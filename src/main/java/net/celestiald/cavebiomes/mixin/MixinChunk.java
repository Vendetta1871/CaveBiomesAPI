package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.celestiald.cavebiomes.api.IWrappedWorldType;
import net.celestiald.cavebiomes.world.population.ExtendedChunkPopulationAccess;
import net.celestiald.cavebiomes.world.population.PopulationRegionScheduler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkGeneratorDebug;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

// Apply below the default priority so other mods can inject into the extended
// implementations merged here, as Moving Elevators does for setBlockState.
@Mixin(value = Chunk.class, priority = 999)
public abstract class MixinChunk implements ExtendedChunkPopulationAccess {

    // ---- Shadowed fields ----
    @Shadow @Final @Mutable private ExtendedBlockStorage[] storageArrays;
    @Shadow @Final @Mutable private ClassInheritanceMultiMap<Entity>[] entityLists;
    @Shadow @Final private World world;
    @Shadow private int[] heightMap;
    @Shadow private int heightMapMinimum;
    @Shadow private int[] precipitationHeightMap;
    @Shadow private boolean dirty;
    @Shadow private int queuedLightChecks;
    // NB: must NOT be `public final int x = 0` — javac folds a final field with a
    // constant initializer into the literal 0, so `this.x`/`this.z` would compile
    // to 0 instead of reading the real chunk coords (killed entities, broke
    // lighting world-coords). @Shadow @Final (no Java initializer) reads the field.
    @Shadow @Final public int x;
    @Shadow @Final public int z;
    @Shadow private boolean hasEntities;

    // ---- Shadowed methods (NOT being overwritten in this Mixin) ----
    @Shadow public abstract boolean canSeeSky(BlockPos pos);
    @Shadow public abstract void markDirty();
    @Shadow protected abstract void propagateSkylightOcclusion(int x, int z);
    @Shadow @Nullable public abstract TileEntity getTileEntity(BlockPos pos, Chunk.EnumCreateEntityType createType);
    @Shadow public abstract int getTopFilledSegment();
    @Shadow protected abstract void populate(IChunkGenerator generator);
    // getBlockState(BlockPos) delegates to getBlockState(int,int,int) — keep shadow for external calls
    @Shadow public abstract IBlockState getBlockState(BlockPos pos);

    // Private target-class helpers (shadow with stub body required for private access)
    @Shadow private void updateSkylightNeighborHeight(int x, int z, int startY, int endY) {}
    @Shadow private int getBlockLightOpacity(int x, int y, int z) { return 0; }

    // Own logger for addEntity warning
    @Unique private static final Logger CAVEBIOMES_CHUNK_LOGGER = LogManager.getLogger("CaveBiomes/Chunk");

    @Override
    @Unique
    public void cavebiomes$populate(IChunkGenerator generator) {
        populate(generator);
    }

    // ---- API helpers ----
    @Unique private int si(int y)    { return (y - WorldHeightAPI.getMinY()) >> 4; }
    @Unique private int yBase(int i) { return i * 16 + WorldHeightAPI.getMinY(); }

    @Unique
    private boolean cavebiomes$isBlockYInRange(int blockY) {
        return blockY >= cavebiomes$minimumBuildY() && blockY < cavebiomes$maximumBuildY();
    }

    @Unique
    private int cavebiomes$entityStorageIndex(int sectionY, int storageSectionCount) {
        int minimumSection = cavebiomes$usesExtendedHeight()
                ? WorldHeightAPI.getMinSection() : 0;
        int maximumSection = cavebiomes$usesExtendedHeight()
                ? WorldHeightAPI.getMinSection() + storageSectionCount - 1 : 15;
        int clampedSection = Math.max(minimumSection, Math.min(sectionY, maximumSection));
        return clampedSection - WorldHeightAPI.getMinSection();
    }

    @Unique
    private static int cavebiomes$trackedEntitySectionY(int storageIndex) {
        return storageIndex + WorldHeightAPI.getMinSection();
    }

    @Unique
    private static int cavebiomes$relightQueueLimit(int sectionCount) {
        return sectionCount * 16 * 16;
    }

    @Unique
    private static int cavebiomes$relightSectionIndex(int queueIndex, int sectionCount) {
        return queueIndex % sectionCount;
    }

    @Unique
    private static int cavebiomes$relightLocalX(int queueIndex, int sectionCount) {
        return queueIndex / sectionCount % 16;
    }

    @Unique
    private static int cavebiomes$relightLocalZ(int queueIndex, int sectionCount) {
        return queueIndex / (sectionCount * 16);
    }

    @Unique
    private static int cavebiomes$relightWorldY(int sectionIndex, int localY) {
        return WorldHeightAPI.sectionYBase(sectionIndex) + localY;
    }

    @Unique
    private boolean cavebiomes$isAboveMinimumBuildHeight(int worldY) {
        return worldY > cavebiomes$minimumBuildY();
    }

    @Unique
    private boolean cavebiomes$usesExtendedHeight() {
        return WorldHeightAPI.usesExtendedHeight(this.world);
    }

    @Unique
    private int cavebiomes$minimumBuildY() {
        return cavebiomes$usesExtendedHeight() ? WorldHeightAPI.getMinY() : 0;
    }

    @Unique
    private int cavebiomes$maximumBuildY() {
        return cavebiomes$usesExtendedHeight() ? WorldHeightAPI.getMaxY() : 256;
    }

    @Unique
    private static int cavebiomes$precipitationScanStart(int topFilledSegment) {
        return Math.min(topFilledSegment + 15, WorldHeightAPI.getMaxY() - 1);
    }

    @Unique
    private static int cavebiomes$emptyPrecipitationHeight() {
        return WorldHeightAPI.getMinY() - 1;
    }

    // =========================================================================
    // Extended population region: vanilla's two-by-two readiness check is only sufficient for
    // decorators offset toward +X/+Z. Opt-in generators can require a symmetric loaded square,
    // preventing feature reads at the loaded edge from recursively generating and populating an
    // unbounded chain of chunks.
    // =========================================================================

    @Inject(
            method = "populate(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/gen/IChunkGenerator;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void cavebiomes$populateLoadedRegion(IChunkProvider provider,
            IChunkGenerator generator, CallbackInfo ci) {
        if (PopulationRegionScheduler.populateLoadedRegions(
                provider, generator, this.x, this.z)) {
            ci.cancel();
        }
    }

    // =========================================================================
    // Constructor 1: resize storageArrays and entityLists to configured count
    // =========================================================================

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void resizeArrays(World worldIn, int x, int z, CallbackInfo ci) {
        int count = WorldHeightAPI.getSectionCount();
        if (count != 16) {
            this.storageArrays = new ExtendedBlockStorage[count];
            this.entityLists = (ClassInheritanceMultiMap[]) new ClassInheritanceMultiMap[count];
            for (int i = 0; i < count; i++) {
                this.entityLists[i] = new ClassInheritanceMultiMap<>(Entity.class);
            }
        }
        // Vanilla initializes this to 4096 (= 16 sections * 16 X * 16 Z), meaning
        // the incremental relight sweep is complete until resetRelightChecks() runs.
        // Preserve that state for the configured number of sections.
        int semanticSectionCount = WorldHeightAPI.usesExtendedHeight(worldIn) ? count : 16;
        this.queuedLightChecks = cavebiomes$relightQueueLimit(semanticSectionCount);
    }

    // =========================================================================
    // Constructor 2: shift primer-generated sections to correct indices.
    //
    // Vanilla fills storageArrays[0..15] from ChunkPrimer (Y 0..255).
    // With minY < 0 (e.g. -64), vanilla terrain must live at indices 4..19.
    // The yBase of each storage is unchanged because:
    //   old[i].yBase = i*16  and  sectionYBase(i - minSection) = i*16 - minSection*16 + minY = i*16  ✓
    // =========================================================================

    @Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/ChunkPrimer;II)V",
            at = @At("RETURN"))
    private void fixPrimerShift(World worldIn, ChunkPrimer primer, int x, int z, CallbackInfo ci) {
        int minSection = WorldHeightAPI.getMinSection();
        if (minSection != 0) {
            ExtendedBlockStorage[] old = this.storageArrays;
            this.storageArrays = new ExtendedBlockStorage[old.length];
            for (int i = 0; i < 16 && i < old.length; i++) {
                if (old[i] != null) {
                    int idx = i - minSection;
                    if (idx >= 0 && idx < this.storageArrays.length) {
                        this.storageArrays[idx] = old[i];
                    }
                }
            }
        }
    }

    // =========================================================================
    // getBlockState(int,int,int): replace y >= 0 && y >> 4 check with si(y)
    // =========================================================================

    @Overwrite
    public IBlockState getBlockState(final int x, final int y, final int z) {
        WorldType worldType = this.world.getWorldType();
        if (worldType instanceof IWrappedWorldType) {
            worldType = ((IWrappedWorldType) worldType).getBaseWorldType();
        }
        if (worldType == WorldType.DEBUG_ALL_BLOCK_STATES) {
            IBlockState iblockstate = null;
            if (y == 60) iblockstate = Blocks.BARRIER.getDefaultState();
            if (y == 70) iblockstate = ChunkGeneratorDebug.getBlockStateFor(x, z);
            return iblockstate == null ? Blocks.AIR.getDefaultState() : iblockstate;
        }
        if (!cavebiomes$isBlockYInRange(y)) {
            return Blocks.AIR.getDefaultState();
        }
        try {
            int idx = si(y);
            if (idx >= 0 && idx < this.storageArrays.length) {
                ExtendedBlockStorage storage = this.storageArrays[idx];
                if (storage != Chunk.NULL_BLOCK_STORAGE) {
                    return storage.get(x & 15, y & 15, z & 15);
                }
            }
            return Blocks.AIR.getDefaultState();
        } catch (Throwable t) {
            // NB: no anonymous ICrashReportDetail here — an anonymous inner class
            // inside a Mixin produces a "MixinChunk$1 ... cannot be referenced
            // directly" error at runtime. addCrashSection is eager and avoids it.
            CrashReport report = CrashReport.makeCrashReport(t, "Getting block state");
            CrashReportCategory cat = report.makeCategory("Block being got");
            cat.addCrashSection("Location", CrashReportCategory.getCoordinateInfo(x, y, z));
            throw new ReportedException(report);
        }
    }

    // =========================================================================
    // Precipitation surface: vanilla stops its lazy column scan above Y=0.
    // Keep that implementation untouched outside the extended Overworld, while
    // shifting both its lower bound and empty-column sentinel with minY.
    // =========================================================================

    @Inject(method = "getPrecipitationHeight", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private void cavebiomes$getPrecipitationHeight(BlockPos pos,
            CallbackInfoReturnable<BlockPos> cir) {
        if (!cavebiomes$usesExtendedHeight() || WorldHeightAPI.getMinY() >= 0) {
            return;
        }

        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        int column = localX | localZ << 4;
        int height = this.precipitationHeightMap[column];

        if (height == -999) {
            int minY = WorldHeightAPI.getMinY();
            BlockPos cursor = new BlockPos(pos.getX(),
                    cavebiomes$precipitationScanStart(this.getTopFilledSegment()), pos.getZ());
            height = cavebiomes$emptyPrecipitationHeight();

            while (cursor.getY() > minY) {
                IBlockState state = this.getBlockState(cursor);
                Material material = state.getMaterial();
                if (material.blocksMovement() || material.isLiquid()) {
                    height = cursor.getY() + 1;
                    break;
                }
                cursor = cursor.down();
            }

            this.precipitationHeightMap[column] = height;
        }

        cir.setReturnValue(new BlockPos(pos.getX(), height, pos.getZ()));
    }

    // =========================================================================
    // setBlockState: fix j >> 4 index and yBase for new ExtendedBlockStorage
    // =========================================================================

    @Overwrite
    @Nullable
    public IBlockState setBlockState(BlockPos pos, IBlockState state) {
        int i  = pos.getX() & 15;
        int j  = pos.getY();
        int k  = pos.getZ() & 15;
        if (!cavebiomes$isBlockYInRange(j)) {
            return null;
        }
        int l  = k << 4 | i;

        if (j >= this.precipitationHeightMap[l] - 1) {
            this.precipitationHeightMap[l] = -999;
        }

        int i1 = this.heightMap[l];
        IBlockState iblockstate = this.getBlockState(pos);
        if (iblockstate == state) return null;

        Block block  = state.getBlock();
        Block block1 = iblockstate.getBlock();
        int k1 = iblockstate.getLightOpacity(this.world, pos);

        int sectionIdx = si(j);
        ExtendedBlockStorage extendedblockstorage = (sectionIdx >= 0 && sectionIdx < this.storageArrays.length)
                ? this.storageArrays[sectionIdx] : null;
        boolean flag = false;

        if (extendedblockstorage == Chunk.NULL_BLOCK_STORAGE) {
            if (block == Blocks.AIR) return null;
            extendedblockstorage = new ExtendedBlockStorage(yBase(sectionIdx), this.world.provider.hasSkyLight());
            this.storageArrays[sectionIdx] = extendedblockstorage;
            flag = j >= i1;
        }

        extendedblockstorage.set(i, j & 15, k, state);

        if (!this.world.isRemote) {
            if (block1 != block) block1.breakBlock(this.world, pos, iblockstate);
            TileEntity te = this.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
            if (te != null && te.shouldRefresh(this.world, pos, iblockstate, state))
                this.world.removeTileEntity(pos);
        } else if (block1.hasTileEntity(iblockstate)) {
            TileEntity te = this.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
            if (te != null && te.shouldRefresh(this.world, pos, iblockstate, state))
                this.world.removeTileEntity(pos);
        }

        if (extendedblockstorage.get(i, j & 15, k).getBlock() != block) return null;

        if (flag) {
            this.generateSkylightMap();
        } else {
            int j1 = state.getLightOpacity(this.world, pos);
            if (j1 > 0) {
                if (j >= i1) this.relightBlock(i, j + 1, k);
            } else if (j == i1 - 1) {
                this.relightBlock(i, j, k);
            }
            if (j1 != k1 && (j1 < k1
                    || this.getLightFor(EnumSkyBlock.SKY, pos) > 0
                    || this.getLightFor(EnumSkyBlock.BLOCK, pos) > 0)) {
                this.propagateSkylightOcclusion(i, k);
            }
        }

        if (!this.world.isRemote && block1 != block
                && (!this.world.captureBlockSnapshots || block.hasTileEntity(state))) {
            block.onBlockAdded(this.world, pos, state);
        }

        if (block.hasTileEntity(state)) {
            TileEntity tileentity1 = this.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
            if (tileentity1 == null) {
                tileentity1 = block.createTileEntity(this.world, state);
                this.world.setTileEntity(pos, tileentity1);
            }
            if (tileentity1 != null) tileentity1.updateContainingBlockInfo();
        }

        this.dirty = true;
        return iblockstate;
    }

    // =========================================================================
    // getLightFor / setLightFor / getLightSubtracted: fix j >> 4 index
    // =========================================================================

    @Overwrite
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        if (!cavebiomes$isBlockYInRange(j)) {
            return type.defaultLightValue;
        }
        int k = pos.getZ() & 15;
        int idx = si(j);
        ExtendedBlockStorage storage = (idx >= 0 && idx < this.storageArrays.length)
                ? this.storageArrays[idx] : null;
        if (storage == Chunk.NULL_BLOCK_STORAGE) {
            return this.canSeeSky(pos) ? type.defaultLightValue : 0;
        }
        if (type == EnumSkyBlock.SKY) {
            return !this.world.provider.hasSkyLight() ? 0 : storage.getSkyLight(i, j & 15, k);
        }
        return type == EnumSkyBlock.BLOCK ? storage.getBlockLight(i, j & 15, k) : type.defaultLightValue;
    }

    @Overwrite
    public void setLightFor(EnumSkyBlock type, BlockPos pos, int value) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        if (!cavebiomes$isBlockYInRange(j)) {
            return;
        }
        int k = pos.getZ() & 15;
        int idx = si(j);
        if (idx < 0 || idx >= this.storageArrays.length) return;
        ExtendedBlockStorage storage = this.storageArrays[idx];
        if (storage == Chunk.NULL_BLOCK_STORAGE) {
            storage = new ExtendedBlockStorage(yBase(idx), this.world.provider.hasSkyLight());
            this.storageArrays[idx] = storage;
            this.generateSkylightMap();
        }
        this.dirty = true;
        if (type == EnumSkyBlock.SKY) {
            if (this.world.provider.hasSkyLight()) storage.setSkyLight(i, j & 15, k, value);
        } else if (type == EnumSkyBlock.BLOCK) {
            storage.setBlockLight(i, j & 15, k, value);
        }
    }

    @Overwrite
    public int getLightSubtracted(BlockPos pos, int amount) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        if (!cavebiomes$isBlockYInRange(j)) {
            return 0;
        }
        int k = pos.getZ() & 15;
        int idx = si(j);
        ExtendedBlockStorage storage = (idx >= 0 && idx < this.storageArrays.length)
                ? this.storageArrays[idx] : null;
        if (storage == Chunk.NULL_BLOCK_STORAGE) {
            return this.world.provider.hasSkyLight() && amount < EnumSkyBlock.SKY.defaultLightValue
                    ? EnumSkyBlock.SKY.defaultLightValue - amount : 0;
        }
        int l  = !this.world.provider.hasSkyLight() ? 0 : storage.getSkyLight(i, j & 15, k);
        l -= amount;
        int i1 = storage.getBlockLight(i, j & 15, k);
        if (i1 > l) l = i1;
        return l;
    }

    // =========================================================================
    // relightBlock: fix j > 0 → j > minY and storageArrays indices
    // =========================================================================

    @Overwrite
    private void relightBlock(int x, int y, int z) {
        // No '& 255' mask: heightMap is int[] and must hold values above 255.
        int i = this.heightMap[z << 4 | x];
        int j = i;
        if (y > i) j = y;

        int minY = cavebiomes$minimumBuildY();
        while (j > minY && this.getBlockLightOpacity(x, j - 1, z) == 0) --j;

        if (j != i) {
            this.world.markBlocksDirtyVertical(x + this.x * 16, z + this.z * 16, j, i);
            this.heightMap[z << 4 | x] = j;
            int worldX = this.x * 16 + x;
            int worldZ = this.z * 16 + z;

            if (this.world.provider.hasSkyLight()) {
                if (j < i) {
                    for (int j1 = j; j1 < i; ++j1) {
                        int idx = si(j1);
                        if (idx >= 0 && idx < this.storageArrays.length) {
                            ExtendedBlockStorage s = this.storageArrays[idx];
                            if (s != Chunk.NULL_BLOCK_STORAGE) {
                                s.setSkyLight(x, j1 & 15, z, 15);
                                this.world.notifyLightSet(new BlockPos((this.x << 4) + x, j1, (this.z << 4) + z));
                            }
                        }
                    }
                } else {
                    for (int i1 = i; i1 < j; ++i1) {
                        int idx = si(i1);
                        if (idx >= 0 && idx < this.storageArrays.length) {
                            ExtendedBlockStorage s = this.storageArrays[idx];
                            if (s != Chunk.NULL_BLOCK_STORAGE) {
                                s.setSkyLight(x, i1 & 15, z, 0);
                                this.world.notifyLightSet(new BlockPos((this.x << 4) + x, i1, (this.z << 4) + z));
                            }
                        }
                    }
                }

                int k1 = 15;
                while (j > minY && k1 > 0) {
                    --j;
                    int i2 = this.getBlockLightOpacity(x, j, z);
                    if (i2 == 0) i2 = 1;
                    k1 -= i2;
                    if (k1 < 0) k1 = 0;
                    int idx = si(j);
                    if (idx >= 0 && idx < this.storageArrays.length) {
                        ExtendedBlockStorage s = this.storageArrays[idx];
                        if (s != Chunk.NULL_BLOCK_STORAGE) s.setSkyLight(x, j & 15, z, k1);
                    }
                }
            }

            int l1 = this.heightMap[z << 4 | x];
            int j2 = i, k2 = l1;
            if (l1 < i) { j2 = l1; k2 = i; }
            if (l1 < this.heightMapMinimum) this.heightMapMinimum = l1;

            if (this.world.provider.hasSkyLight()) {
                for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
                    this.updateSkylightNeighborHeight(worldX + facing.getFrontOffsetX(),
                            worldZ + facing.getFrontOffsetZ(), j2, k2);
                }
                this.updateSkylightNeighborHeight(worldX, worldZ, j2, k2);
            }

            this.dirty = true;
        }
    }

    // =========================================================================
    // generateSkylightMap: fix l > 0 → l > minY and i1 <= 0 → i1 <= minY
    // =========================================================================

    @Overwrite
    public void generateSkylightMap() {
        int topSeg = Math.min(this.getTopFilledSegment(), cavebiomes$maximumBuildY() - 16);
        this.heightMapMinimum = Integer.MAX_VALUE;
        int minY = cavebiomes$minimumBuildY();

        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                this.precipitationHeightMap[j + (k << 4)] = -999;

                for (int l = topSeg + 16; l > minY; --l) {
                    if (this.getBlockLightOpacity(j, l - 1, k) != 0) {
                        this.heightMap[k << 4 | j] = l;
                        if (l < this.heightMapMinimum) this.heightMapMinimum = l;
                        break;
                    }
                }

                if (this.world.provider.hasSkyLight()) {
                    int k1 = 15;
                    int i1 = topSeg + 16 - 1;
                    while (true) {
                        int j1 = this.getBlockLightOpacity(j, i1, k);
                        if (j1 == 0 && k1 != 15) j1 = 1;
                        k1 -= j1;
                        if (k1 > 0) {
                            int idx = si(i1);
                            if (idx >= 0 && idx < this.storageArrays.length) {
                                ExtendedBlockStorage s = this.storageArrays[idx];
                                if (s != Chunk.NULL_BLOCK_STORAGE) {
                                    s.setSkyLight(j, i1 & 15, k, k1);
                                    this.world.notifyLightSet(
                                            new BlockPos((this.x << 4) + j, i1, (this.z << 4) + k));
                                }
                            }
                        }
                        --i1;
                        if (i1 <= minY || k1 <= 0) break;
                    }
                }
            }
        }
        this.dirty = true;
    }

    // generateHeightMap() is @SideOnly(CLIENT) in vanilla — its override lives
    // in MixinChunkClient so this common Mixin still applies on a dedicated server.

    // =========================================================================
    // Incremental relight sweep: vanilla encodes 16 sections in a 4096-entry
    // queue and anchors their world positions at Y=0. Decode against the actual
    // storage-array length and minY so every configured section is visited once.
    // =========================================================================

    /**
     * @author CelestialD
     * @reason Decode incremental relight work against the configured finite section range.
     */
    @Overwrite
    public void enqueueRelightChecks() {
        boolean extended = cavebiomes$usesExtendedHeight();
        int sectionCount = extended ? this.storageArrays.length : 16;
        int firstSectionIndex = extended ? 0 : -WorldHeightAPI.getMinSection();
        int queueLimit = cavebiomes$relightQueueLimit(sectionCount);
        if (this.queuedLightChecks >= queueLimit) {
            return;
        }

        for (int iteration = 0; iteration < 8; ++iteration) {
            if (this.queuedLightChecks >= queueLimit) {
                return;
            }

            int queueIndex = this.queuedLightChecks++;
            int sectionIndex = firstSectionIndex
                    + cavebiomes$relightSectionIndex(queueIndex, sectionCount);
            int localX = cavebiomes$relightLocalX(queueIndex, sectionCount);
            int localZ = cavebiomes$relightLocalZ(queueIndex, sectionCount);
            ExtendedBlockStorage section = this.storageArrays[sectionIndex];

            for (int localY = 0; localY < 16; ++localY) {
                int worldY = cavebiomes$relightWorldY(sectionIndex, localY);
                BlockPos pos = new BlockPos((this.x << 4) + localX, worldY,
                        (this.z << 4) + localZ);
                boolean boundary = localY == 0 || localY == 15
                        || localX == 0 || localX == 15 || localZ == 0 || localZ == 15;
                IBlockState state = section == Chunk.NULL_BLOCK_STORAGE
                        ? Blocks.AIR.getDefaultState() : section.get(localX, localY, localZ);

                if ((section == Chunk.NULL_BLOCK_STORAGE && boundary)
                        || (section != Chunk.NULL_BLOCK_STORAGE
                        && state.getBlock().isAir(state, this.world, pos))) {
                    for (EnumFacing facing : EnumFacing.values()) {
                        BlockPos neighbor = pos.offset(facing);
                        IBlockState neighborState = this.world.getBlockState(neighbor);
                        if (neighborState.getLightValue(this.world, neighbor) > 0) {
                            this.world.checkLight(neighbor);
                        }
                    }
                    this.world.checkLight(pos);
                }
            }
        }
    }

    /**
     * Checks one horizontal column while populating chunk light. Vanilla stops
     * both downward passes at Y=0; the finite world must stop at minY instead.
     *
     * @author CelestialD
     * @reason Populate skylight and emitted light through the configured minimum Y.
     */
    @Overwrite
    private boolean checkLight(int localX, int localZ) {
        int topSegment = Math.min(this.getTopFilledSegment(),
                cavebiomes$maximumBuildY() - 16);
        boolean foundOpaque = false;
        boolean reachedOpaqueBelowSea = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                (this.x << 4) + localX, cavebiomes$minimumBuildY(),
                (this.z << 4) + localZ);

        for (int worldY = topSegment + 16 - 1;
                worldY > this.world.getSeaLevel()
                        || (cavebiomes$isAboveMinimumBuildHeight(worldY)
                        && !reachedOpaqueBelowSea);
                --worldY) {
            pos.setPos(pos.getX(), worldY, pos.getZ());
            int opacity = this.getBlockLightOpacity(localX, worldY, localZ);
            if (opacity == 255 && worldY < this.world.getSeaLevel()) {
                reachedOpaqueBelowSea = true;
            }

            if (!foundOpaque && opacity > 0) {
                foundOpaque = true;
            } else if (foundOpaque && opacity == 0 && !this.world.checkLight(pos)) {
                return false;
            }
        }

        for (int worldY = pos.getY(); cavebiomes$isAboveMinimumBuildHeight(worldY);
                --worldY) {
            pos.setPos(pos.getX(), worldY, pos.getZ());
            IBlockState state = this.getBlockState(pos);
            if (state.getLightValue(this.world, pos) > 0) {
                this.world.checkLight(pos);
            }
        }

        return true;
    }

    // =========================================================================
    // isEmptyBetween (func_76606_c): vanilla clamps to [0,256) and indexes
    // storageArrays[y>>4]; switch to [minY,maxY) and si(y).
    // =========================================================================

    @Overwrite
    public boolean isEmptyBetween(int startY, int endY) {
        int minimumY = cavebiomes$minimumBuildY();
        int maximumY = cavebiomes$maximumBuildY();
        if (startY < minimumY) startY = minimumY;
        if (endY >= maximumY) endY = maximumY - 1;

        for (int y = startY; y <= endY; y += 16) {
            int idx = si(y);
            if (idx < 0 || idx >= this.storageArrays.length) continue;
            ExtendedBlockStorage storage = this.storageArrays[idx];
            if (storage != Chunk.NULL_BLOCK_STORAGE && !storage.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // Entity.chunkCoordY remains the raw signed section coordinate used by
    // World.updateEntityWithOptionalForce. Only accesses to entityLists use the normalized index.
    // Keeping those two coordinate systems distinct prevents every entity from being removed and
    // re-added on every tick when minSection is non-zero.
    // =========================================================================

    @Overwrite
    public void addEntity(Entity entityIn) {
        this.hasEntities = true;
        int i = MathHelper.floor(entityIn.posX / 16.0D);
        int j = MathHelper.floor(entityIn.posZ / 16.0D);

        if (i != this.x || j != this.z) {
            CAVEBIOMES_CHUNK_LOGGER.warn("Wrong location! ({}, {}) should be ({}, {}), {}",
                    i, j, this.x, this.z, entityIn);
            entityIn.setDead();
        }

        int sectionY = MathHelper.floor(entityIn.posY / 16.0D);
        int sectionIndex = cavebiomes$entityStorageIndex(sectionY, this.entityLists.length);

        MinecraftForge.EVENT_BUS.post(new EntityEvent.EnteringChunk(
                entityIn, this.x, this.z, entityIn.chunkCoordX, entityIn.chunkCoordZ));
        entityIn.addedToChunk = true;
        entityIn.chunkCoordX = this.x;
        entityIn.chunkCoordY = cavebiomes$trackedEntitySectionY(sectionIndex);
        entityIn.chunkCoordZ = this.z;
        this.entityLists[sectionIndex].add(entityIn);
        this.markDirty();
    }

    /**
     * Converts Entity.chunkCoordY's signed section coordinate back to an array index.
     *
     * @author CelestialD
     * @reason Extended entity arrays are offset by minSection while the Entity field must retain
     *         the raw section coordinate compared by World.updateEntityWithOptionalForce.
     */
    @Overwrite
    public void removeEntityAtIndex(Entity entityIn, int sectionY) {
        int sectionIndex = cavebiomes$entityStorageIndex(sectionY, this.entityLists.length);
        this.entityLists[sectionIndex].remove(entityIn);
        this.markDirty();
    }

    // =========================================================================
    // Entity AABB queries index entityLists by floor(y/16) (clamped to [0,len)).
    // addEntity stores entities at floor(y/16) - minSection, so the lookups must
    // apply the same offset or entities below 0 / above 255 are never found
    // (e.g. dropped items can't be picked up). Both query methods compute their
    // section bounds with MathHelper.floor(double); shift every such result down
    // by minSection here.
    // =========================================================================

    @Redirect(
            method = {
                    "getEntitiesWithinAABBForEntity",
                    "getEntitiesOfTypeWithinAABB"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;floor(D)I"))
    private int cavebiomes$entitySectionIndex(double value) {
        return cavebiomes$entityStorageIndex(
                MathHelper.floor(value), this.entityLists.length);
    }
}
