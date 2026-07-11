package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.block.BlockPortal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Allows Overworld portal frames to be discovered down to the configured minimum Y. */
@Mixin(BlockPortal.Size.class)
public abstract class MixinBlockPortalSize {

    @Shadow @Final private World world;

    @Unique
    private static int cavebiomes$relativeToPortalFloor(int worldY, int dimension) {
        return dimension == 0 ? worldY - WorldHeightAPI.getMinY() : worldY;
    }

    @Redirect(
            method = "<init>",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=21"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;",
                            ordinal = 0)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$frameDescentY(BlockPos pos) {
        return cavebiomes$relativeToPortalFloor(pos.getY(), this.world.provider.getDimension());
    }
}
