package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.block.BlockFalling;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Extends falling-block start and synchronous landing checks below vanilla Y 0. */
@Mixin(BlockFalling.class)
public abstract class MixinBlockFalling {

    @Unique
    private static int cavebiomes$relativeToMinimum(int worldY) {
        return worldY - WorldHeightAPI.getMinY();
    }

    @Redirect(
            method = "checkFallable",
            slice = @Slice(
                    from = @At("HEAD"),
                    to = @At(value = "CONSTANT", args = "intValue=32")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$minimumStartY(BlockPos pos) {
        return cavebiomes$relativeToMinimum(pos.getY());
    }

    @Redirect(
            method = "checkFallable",
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/World;setBlockToAir(Lnet/minecraft/util/math/BlockPos;)Z"),
                    to = @At("TAIL")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$synchronousDescentY(BlockPos pos) {
        return cavebiomes$relativeToMinimum(pos.getY());
    }

    @Redirect(
            method = "checkFallable",
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/World;setBlockToAir(Lnet/minecraft/util/math/BlockPos;)Z"),
                    to = @At("TAIL")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 1),
            require = 1,
            allow = 1)
    private int cavebiomes$synchronousLandingY(BlockPos pos) {
        return cavebiomes$relativeToMinimum(pos.getY());
    }
}
