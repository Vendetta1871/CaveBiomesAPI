package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * ChunkCache (the read-only region used by chunk rendering AND entity path-finding)
 * returns AIR for any block with y<0 or y>=256. That makes blocks outside [0,256)
 * render as nothing (see-through) and breaks pathfinding there. Widen the bound to
 * [minY, maxY). Common (not @SideOnly), so it also fixes the server-side AI cache.
 *
 * The render-light methods getLightFor/getLightForExt are @SideOnly(CLIENT) (stripped
 * on a dedicated server), so their fix lives in {@link MixinChunkCacheClient}.
 */
@Mixin(ChunkCache.class)
public abstract class MixinChunkCache {

    @Shadow protected int chunkX;
    @Shadow protected int chunkZ;
    @Shadow protected Chunk[][] chunkArray;
    @Shadow protected World world;

    @Overwrite
    public IBlockState getBlockState(BlockPos pos) {
        int minimumY = WorldHeightAPI.usesExtendedHeight(this.world)
                ? WorldHeightAPI.getMinY() : 0;
        int maximumY = WorldHeightAPI.usesExtendedHeight(this.world)
                ? WorldHeightAPI.getMaxY() : 256;
        if (pos.getY() >= minimumY && pos.getY() < maximumY) {
            int i = (pos.getX() >> 4) - this.chunkX;
            int j = (pos.getZ() >> 4) - this.chunkZ;

            if (i >= 0 && i < this.chunkArray.length && j >= 0 && j < this.chunkArray[i].length) {
                Chunk chunk = this.chunkArray[i][j];
                if (chunk != null) {
                    return chunk.getBlockState(pos);
                }
            }
        }
        return Blocks.AIR.getDefaultState();
    }
}
