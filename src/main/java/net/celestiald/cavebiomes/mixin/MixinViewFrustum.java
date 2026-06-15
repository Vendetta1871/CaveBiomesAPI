package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

/**
 * Client render grid extension. Vanilla {@link ViewFrustum} hardcodes the
 * vertical render-chunk grid to 16 rows starting at Y=0, so anything outside
 * [0,256) is never rendered. Make the grid cover Y [minY, maxY): height =
 * sectionCount, origin shifted by minY, and block-Y -> row-index shifted by
 * minSection. @SideOnly(CLIENT) — listed under "client" in the mixin config.
 */
@Mixin(ViewFrustum.class)
public abstract class MixinViewFrustum {

    @Shadow protected int countChunksY;
    @Shadow protected int countChunksX;
    @Shadow protected int countChunksZ;
    @Shadow public RenderChunk[] renderChunks;

    // Grid height: 16 -> configured section count (e.g. 24 for -64..320).
    @ModifyConstant(method = "setCountChunksXYZ", constant = @Constant(intValue = 16))
    private int cavebiomes$gridHeight(int original) {
        return WorldHeightAPI.getSectionCount();
    }

    // Both createRenderChunks (initial) and updateChunkPositions place rows at
    // y = row*16 assuming the grid starts at Y=0; shift the origin down to minY.
    @Redirect(
            method = {"createRenderChunks", "updateChunkPositions"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/RenderChunk;setPosition(III)V"))
    private void cavebiomes$shiftRowY(RenderChunk renderChunk, int x, int y, int z) {
        renderChunk.setPosition(x, y + WorldHeightAPI.getMinY(), z);
    }

    @Nullable
    @Overwrite
    protected RenderChunk getRenderChunk(BlockPos pos) {
        int i = MathHelper.intFloorDiv(pos.getX(), 16);
        int j = MathHelper.intFloorDiv(pos.getY(), 16) - WorldHeightAPI.getMinSection();
        int k = MathHelper.intFloorDiv(pos.getZ(), 16);

        if (j >= 0 && j < this.countChunksY) {
            i = i % this.countChunksX;
            if (i < 0) i += this.countChunksX;
            k = k % this.countChunksZ;
            if (k < 0) k += this.countChunksZ;
            int l = (k * this.countChunksY + j) * this.countChunksX + i;
            return this.renderChunks[l];
        }
        return null;
    }

    @Overwrite
    public void markBlocksForUpdate(int x1, int y1, int z1, int x2, int y2, int z2, boolean updateImmediately) {
        int i  = MathHelper.intFloorDiv(x1, 16);
        int j  = MathHelper.intFloorDiv(y1, 16) - WorldHeightAPI.getMinSection();
        int k  = MathHelper.intFloorDiv(z1, 16);
        int l  = MathHelper.intFloorDiv(x2, 16);
        int i1 = MathHelper.intFloorDiv(y2, 16) - WorldHeightAPI.getMinSection();
        int j1 = MathHelper.intFloorDiv(z2, 16);

        for (int k1 = i; k1 <= l; ++k1) {
            int l1 = k1 % this.countChunksX;
            if (l1 < 0) l1 += this.countChunksX;

            for (int i2 = j; i2 <= i1; ++i2) {
                // Y is not toroidal: it is already offset into [0, countChunksY).
                if (i2 < 0 || i2 >= this.countChunksY) continue;

                for (int k2 = k; k2 <= j1; ++k2) {
                    int l2 = k2 % this.countChunksZ;
                    if (l2 < 0) l2 += this.countChunksZ;

                    int i3 = (l2 * this.countChunksY + i2) * this.countChunksX + l1;
                    this.renderChunks[i3].setNeedsUpdate(updateImmediately);
                }
            }
        }
    }
}
