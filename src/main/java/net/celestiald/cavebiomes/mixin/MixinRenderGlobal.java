package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * renderEntities() picks a chunk's per-section entity list with
 * {@code chunk.getEntityLists()[renderChunk.getPosition().getY() / 16]}.
 * With a negative grid origin (minY < 0) that render-chunk Y is negative, so the
 * index becomes negative -> ArrayIndexOutOfBoundsException. getY() is called
 * exactly once in renderEntities (for this index), so redirect it to the
 * minY-shifted value, mapping the row into [0, sectionCount).
 */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Redirect(method = "renderEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;getY()I"))
    private int cavebiomes$entityListRowY(BlockPos pos) {
        return pos.getY() - WorldHeightAPI.getMinY();
    }
}
