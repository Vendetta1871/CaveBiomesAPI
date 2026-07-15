package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.celestiald.cavebiomes.client.IViewFrustumExt;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends vanilla's render-chunk traversal beyond Y 0..255. Kept separate from
 * the entity-list index fix because OptiFine removes this private helper; an
 * optional traversal overwrite must not disable required entity rendering.
 */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Shadow private ViewFrustum viewFrustum;
    @Shadow private int renderDistanceChunks;
    @Shadow private WorldClient world;

    // =========================================================================
    // setupTerrain() walks the render-chunk visibility graph via this private
    // helper, which prunes any neighbour outside [0,256) and returns null. That
    // confines the flood fill to [0,256): sections above/below are never added
    // to the render list, so only the player's own section renders ("phantom"
    // chunks that fill in only once you enter them). Widen the guard to
    // [minY, maxY). OptiFine replaces this private helper with a different
    // signature, so the injection is intentionally optional there.
    // =========================================================================

    @Inject(
            method = "getRenderChunkOffset(Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/client/renderer/chunk/RenderChunk;"
                    + "Lnet/minecraft/util/EnumFacing;)"
                    + "Lnet/minecraft/client/renderer/chunk/RenderChunk;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void cavebiomes$getRenderChunkOffset(BlockPos playerPos,
            RenderChunk renderChunkBase, EnumFacing facing,
            CallbackInfoReturnable<RenderChunk> cir) {
        BlockPos blockpos = renderChunkBase.getBlockPosOffset16(facing);
        int minimumY = WorldHeightAPI.usesExtendedHeight(this.world)
                ? WorldHeightAPI.getMinY() : 0;
        int maximumY = WorldHeightAPI.usesExtendedHeight(this.world)
                ? WorldHeightAPI.getMaxY() : 256;

        if (MathHelper.abs(playerPos.getX() - blockpos.getX()) > this.renderDistanceChunks * 16) {
            cir.setReturnValue(null);
        } else if (blockpos.getY() >= minimumY && blockpos.getY() < maximumY) {
            cir.setReturnValue(MathHelper.abs(playerPos.getZ() - blockpos.getZ())
                    > this.renderDistanceChunks * 16
                    ? null
                    : ((IViewFrustumExt) (Object) this.viewFrustum)
                            .cavebiomes$getRenderChunk(blockpos));
        } else {
            cir.setReturnValue(null);
        }
    }
}
