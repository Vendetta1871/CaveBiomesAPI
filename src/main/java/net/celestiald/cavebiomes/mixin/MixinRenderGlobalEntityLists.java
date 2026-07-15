package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps RenderGlobal's per-section entity lookup inside the extended chunk array. */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobalEntityLists {

    @Redirect(
            method = "renderEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I"),
            require = 1,
            allow = 1)
    private int cavebiomes$entityListRowY(BlockPos pos) {
        int shiftedY = pos.getY() - WorldHeightAPI.getMinY();
        int highestShiftedY = WorldHeightAPI.getMaxY() - WorldHeightAPI.getMinY() - 1;
        return MathHelper.clamp(shiftedY, 0, highestShiftedY);
    }
}
