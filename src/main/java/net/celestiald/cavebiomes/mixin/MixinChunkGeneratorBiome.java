package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.BiomeLayerAPI;
import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stage 2 — 1.18-style cave-biome decoration. After a chunk is generated (caves already
 * carved), this coats <b>exposed</b> stone surfaces — cave floors, walls and ceilings, plus
 * the main surface where a band reaches it — with the height-resolved biome's surface block,
 * within each registered vertical biome's Y-band. Solid rock between caves stays stone, just
 * like real deserts are stone underground.
 *
 * <p>Runs at {@code generateChunk} RETURN on the finished {@link Chunk}, using direct
 * {@link ExtendedBlockStorage} writes with no lighting churn since stone and surface blocks are
 * opaque full cubes.</p>
 *
 * <p>Inert by default: if no providers are registered the inject returns immediately, leaving
 * generation byte-for-byte vanilla.</p>
 *
 * <p>Why not {@code genTerrainBlocks}: vanilla's {@code generateBiomeTerrain} only decorates
 * the sea-level surface and fills everything deeper with stone/gravel regardless of biome, so
 * swapping the biome there is invisible for a sub-surface boundary. This post-pass is the only
 * thing that makes an underground biome visible.</p>
 *
 * @author CelestialD
 */
@Mixin(ChunkGeneratorOverworld.class)
public abstract class MixinChunkGeneratorBiome {

    @Shadow @Final private World world;

    @Inject(method = "generateChunk", at = @At("RETURN"))
    private void cavebiomes$decorateCaveBiomes(int cx, int cz, CallbackInfoReturnable<Chunk> cir) {
        if (!BiomeLayerAPI.hasProviders(this.world)) {
            return; // inert: default = vanilla
        }

        Chunk chunk = cir.getReturnValue();
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int baseX = cx << 4;
        int baseZ = cz << 4;
        // Ask the provider for Biome objects instead of reading Chunk's legacy
        // byte array. Roughly Enough IDs replaces that array with an inert
        // compatibility view when biome registry IDs exceed 255.
        Biome[] baseBiomes = this.world.getBiomeProvider().getBiomes(
                null, baseX, baseZ, 16, 16);

        boolean extended = WorldHeightAPI.usesExtendedHeight(this.world);
        int minY = extended ? WorldHeightAPI.getMinY() : 0;
        int maxY = extended ? WorldHeightAPI.getMaxY() : 256;
        // Bound the scan to the solid+near-surface band; everything above is open sky.
        int topBound = Math.min(maxY, chunk.getTopFilledSegment() + 16);

        boolean changed = false;

        // Walk cave space (air) and decorate its stone neighbours — proportional to cave
        // surface area, not to rock volume.
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = minY; y < topBound; y++) {
                    if (cavebiomes$block(storage, lx, y, lz).getBlock() != Blocks.AIR) {
                        continue;
                    }
                    // Six faces of this air block. Vertical neighbours are always in-column;
                    // horizontal ones only when they stay inside this chunk (skip at edges to
                    // avoid forcing adjacent-chunk generation — leaves harmless border seams).
                    changed |= cavebiomes$decorate(storage, baseBiomes, baseX, baseZ, lx, y + 1, lz, minY, maxY);
                    changed |= cavebiomes$decorate(storage, baseBiomes, baseX, baseZ, lx, y - 1, lz, minY, maxY);
                    if (lx + 1 < 16) changed |= cavebiomes$decorate(storage, baseBiomes, baseX, baseZ, lx + 1, y, lz, minY, maxY);
                    if (lx - 1 >= 0) changed |= cavebiomes$decorate(storage, baseBiomes, baseX, baseZ, lx - 1, y, lz, minY, maxY);
                    if (lz + 1 < 16) changed |= cavebiomes$decorate(storage, baseBiomes, baseX, baseZ, lx, y, lz + 1, minY, maxY);
                    if (lz - 1 >= 0) changed |= cavebiomes$decorate(storage, baseBiomes, baseX, baseZ, lx, y, lz - 1, minY, maxY);
                }
            }
        }

        if (changed) {
            chunk.markDirty();
        }
    }

    /** Reads a block straight from the section arrays (cheaper than Chunk.getBlockState). */
    @Unique
    private IBlockState cavebiomes$block(ExtendedBlockStorage[] storage, int lx, int y, int lz) {
        int idx = WorldHeightAPI.sectionIndex(y);
        if (idx < 0 || idx >= storage.length) {
            return Blocks.AIR.getDefaultState();
        }
        ExtendedBlockStorage s = storage[idx];
        if (s == Chunk.NULL_BLOCK_STORAGE) {
            return Blocks.AIR.getDefaultState();
        }
        return s.get(lx, y & 15, lz);
    }

    /**
     * If the neighbour at (nx, ny, nz) is plain stone and a registered provider overrides the
     * biome there, replaces it with that biome's surface block. Returns whether it changed.
     */
    @Unique
    private boolean cavebiomes$decorate(ExtendedBlockStorage[] storage, Biome[] baseBiomes,
                                        int baseX, int baseZ, int nx, int ny, int nz,
                                        int minY, int maxY) {
        if (ny < minY || ny >= maxY) {
            return false;
        }
        if (cavebiomes$block(storage, nx, ny, nz).getBlock() != Blocks.STONE) {
            return false;
        }
        Biome base = baseBiomes[nz << 4 | nx];
        if (base == null) {
            return false;
        }
        Biome resolved = BiomeLayerAPI.resolve(this.world,
                baseX + nx, ny, baseZ + nz, base);
        if (resolved == base) {
            return false;
        }
        int idx = WorldHeightAPI.sectionIndex(ny);
        ExtendedBlockStorage s = storage[idx];
        if (s == Chunk.NULL_BLOCK_STORAGE) {
            return false; // can't happen for a stone block, but stay safe
        }
        s.set(nx, ny & 15, nz, resolved.topBlock);
        return true;
    }
}
