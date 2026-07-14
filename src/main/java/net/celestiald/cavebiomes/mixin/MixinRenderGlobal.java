package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.celestiald.cavebiomes.client.IViewFrustumExt;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

/**
 * renderEntities() picks a chunk's per-section entity list with
 * {@code chunk.getEntityLists()[renderChunk.getPosition().getY() / 16]}.
 * With a negative grid origin (minY < 0) that render-chunk Y is negative, so the
 * index becomes negative -> ArrayIndexOutOfBoundsException. getY() is called
 * exactly once in renderEntities (for this index), so redirect it to the
 * minY-shifted value, mapping the row into [0, sectionCount). Clamp the result
 * because OptiFine and entity-render hooks can retain a render chunk for one
 * frame while the view frustum is being rebuilt or torn down.
 */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Shadow private ViewFrustum viewFrustum;
    @Shadow private int renderDistanceChunks;

    @Redirect(method = "renderEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;getY()I"))
    private int cavebiomes$entityListRowY(BlockPos pos) {
        int shiftedY = pos.getY() - WorldHeightAPI.getMinY();
        int highestShiftedY = WorldHeightAPI.getMaxY() - WorldHeightAPI.getMinY() - 1;
        return MathHelper.clamp(shiftedY, 0, highestShiftedY);
    }

    // =========================================================================
    // setupTerrain() walks the render-chunk visibility graph via this private
    // helper, which prunes any neighbour outside [0,256) and returns null. That
    // confines the flood fill to [0,256): sections above/below are never added
    // to the render list, so only the player's own section renders ("phantom"
    // chunks that fill in only once you enter them). Widen the guard to
    // [minY, maxY). @Overwrite (not @ModifyConstant): the SpongePowered AP does
    // not emit a refmap entry for this method name, so a string-based injector
    // would silently fail in a reobfuscated build — whereas an @Overwrite is
    // reobfuscated by symbol, the same approach used in MixinViewFrustum.
    // =========================================================================

    @Nullable
    @Overwrite
    private RenderChunk getRenderChunkOffset(BlockPos playerPos, RenderChunk renderChunkBase, EnumFacing facing) {
        BlockPos blockpos = renderChunkBase.getBlockPosOffset16(facing);

        if (MathHelper.abs(playerPos.getX() - blockpos.getX()) > this.renderDistanceChunks * 16) {
            return null;
        } else if (blockpos.getY() >= WorldHeightAPI.getMinY() && blockpos.getY() < WorldHeightAPI.getMaxY()) {
            return MathHelper.abs(playerPos.getZ() - blockpos.getZ()) > this.renderDistanceChunks * 16
                    ? null : ((IViewFrustumExt) (Object) this.viewFrustum).cavebiomes$getRenderChunk(blockpos);
        } else {
            return null;
        }
    }
}
