package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Client-only ChunkCache light patches. Chunk rebuild samples lighting through
 * RegionRenderCache (extends ChunkCache); vanilla getLightFor / getLightForExt
 * guard with {@code pos.getY() >= 0 && pos.getY() < 256} and otherwise return
 * {@code type.defaultLightValue} (= 15 for SKY), so every block outside [0,256)
 * is reported fully sky-lit -> the extended regions render at max brightness.
 * Widen the guard to [minY, maxY).
 *
 * Kept separate from {@link MixinChunkCache} and listed under the "client"
 * section of mixins.cavebiomes.json: both methods are @SideOnly(CLIENT) and are
 * stripped from the dedicated-server jar, so a common mixin would fail to apply
 * there. getBlockState (used by server AI pathfinding too) stays in the common
 * mixin. Uses @Overwrite — the SpongePowered AP emits no refmap entry for these
 * method names, so a string-based injector would silently fail when reobfuscated.
 */
@Mixin(ChunkCache.class)
public abstract class MixinChunkCacheClient {

    @Shadow protected int chunkX;
    @Shadow protected int chunkZ;
    @Shadow protected Chunk[][] chunkArray;
    @Shadow protected World world;

    @Shadow public abstract IBlockState getBlockState(BlockPos pos);
    @Shadow private boolean withinBounds(int x, int z) { return false; }

    @Overwrite
    private int getLightForExt(EnumSkyBlock type, BlockPos pos) {
        int minimumY = WorldHeightAPI.usesExtendedHeight(this.world)
                ? WorldHeightAPI.getMinY() : 0;
        int maximumY = WorldHeightAPI.usesExtendedHeight(this.world)
                ? WorldHeightAPI.getMaxY() : 256;
        if (type == EnumSkyBlock.SKY && !this.world.provider.hasSkyLight()) {
            return 0;
        } else if (pos.getY() >= minimumY && pos.getY() < maximumY) {
            if (this.getBlockState(pos).useNeighborBrightness()) {
                int l = 0;
                for (EnumFacing enumfacing : EnumFacing.values()) {
                    int k = this.getLightFor(type, pos.offset(enumfacing));
                    if (k > l) l = k;
                    if (l >= 15) return l;
                }
                return l;
            } else {
                int i = (pos.getX() >> 4) - this.chunkX;
                int j = (pos.getZ() >> 4) - this.chunkZ;
                if (!this.withinBounds(i, j)) return type.defaultLightValue;
                return this.chunkArray[i][j].getLightFor(type, pos);
            }
        } else {
            return type.defaultLightValue;
        }
    }

    @Overwrite
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        int minimumY = WorldHeightAPI.usesExtendedHeight(this.world)
                ? WorldHeightAPI.getMinY() : 0;
        int maximumY = WorldHeightAPI.usesExtendedHeight(this.world)
                ? WorldHeightAPI.getMaxY() : 256;
        if (pos.getY() >= minimumY && pos.getY() < maximumY) {
            int i = (pos.getX() >> 4) - this.chunkX;
            int j = (pos.getZ() >> 4) - this.chunkZ;
            if (!this.withinBounds(i, j)) return type.defaultLightValue;
            return this.chunkArray[i][j].getLightFor(type, pos);
        } else {
            return type.defaultLightValue;
        }
    }
}
