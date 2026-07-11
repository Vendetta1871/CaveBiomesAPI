package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Translates the falling entity's 100-tick cleanup window to the finite range. */
@Mixin(EntityFallingBlock.class)
public abstract class MixinEntityFallingBlock {

    @Unique
    private static int cavebiomes$relativeToMinimum(int worldY) {
        return worldY - WorldHeightAPI.getMinY();
    }

    @Unique
    private static int cavebiomes$relativeToVanillaMaximum(int worldY) {
        return worldY - WorldHeightAPI.getMaxY() + 256;
    }

    @Redirect(
            method = "onUpdate",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=100"),
                    to = @At(value = "CONSTANT", args = "intValue=600")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$minimumLifetimeY(BlockPos pos) {
        return cavebiomes$relativeToMinimum(pos.getY());
    }

    @Redirect(
            method = "onUpdate",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=100"),
                    to = @At(value = "CONSTANT", args = "intValue=600")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 1),
            require = 1,
            allow = 1)
    private int cavebiomes$maximumLifetimeY(BlockPos pos) {
        return cavebiomes$relativeToVanillaMaximum(pos.getY());
    }
}
