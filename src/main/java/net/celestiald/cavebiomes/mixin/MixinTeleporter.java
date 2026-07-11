package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Extends Overworld portal lookup and creation scans to the configured minimum Y. */
@Mixin(Teleporter.class)
public abstract class MixinTeleporter {

    private static final String ACTUAL_HEIGHT =
            "Lnet/minecraft/world/WorldServer;getActualHeight()I";
    private static final String SET_MUTABLE_POS =
            "Lnet/minecraft/util/math/BlockPos$MutableBlockPos;setPos(III)Lnet/minecraft/util/math/BlockPos$MutableBlockPos;";
    private static final String IS_AIR_BLOCK =
            "Lnet/minecraft/world/WorldServer;isAirBlock(Lnet/minecraft/util/math/BlockPos;)Z";

    @Shadow @Final protected WorldServer world;

    @Unique
    private static int cavebiomes$relativeToPortalFloor(int worldY, int dimension) {
        return dimension == 0 ? worldY - WorldHeightAPI.getMinY() : worldY;
    }

    @Unique
    private static int cavebiomes$portalFloor(int dimension) {
        return dimension == 0 ? WorldHeightAPI.getMinY() : 0;
    }

    @Redirect(
            method = "placeInExistingPortal",
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lnet/minecraft/util/math/BlockPos;add(III)Lnet/minecraft/util/math/BlockPos;",
                            ordinal = 0),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/WorldServer;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;",
                            ordinal = 0)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$existingPortalScanY(BlockPos pos) {
        return cavebiomes$relativeToPortalFloor(pos.getY(), this.world.provider.getDimension());
    }

    @ModifyConstant(
            method = "makePortal",
            slice = @Slice(
                    from = @At(value = "INVOKE", target = ACTUAL_HEIGHT, ordinal = 0),
                    to = @At(value = "INVOKE", target = SET_MUTABLE_POS, ordinal = 0)),
            constant = @Constant(intValue = 0,
                    expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO),
            require = 1,
            allow = 1)
    private int cavebiomes$primaryCandidateFloor(int vanillaFloor) {
        return cavebiomes$portalFloor(this.world.provider.getDimension());
    }

    @ModifyConstant(
            method = "makePortal",
            slice = @Slice(
                    from = @At(value = "INVOKE", target = IS_AIR_BLOCK, ordinal = 0),
                    to = @At(value = "INVOKE", target = IS_AIR_BLOCK, ordinal = 1)),
            constant = @Constant(intValue = 0,
                    expandZeroConditions = Constant.Condition.GREATER_THAN_ZERO),
            require = 1,
            allow = 1)
    private int cavebiomes$primaryDescentFloor(int vanillaFloor) {
        return cavebiomes$portalFloor(this.world.provider.getDimension());
    }

    @ModifyConstant(
            method = "makePortal",
            slice = @Slice(
                    from = @At(value = "INVOKE", target = ACTUAL_HEIGHT, ordinal = 1),
                    to = @At(value = "INVOKE", target = SET_MUTABLE_POS, ordinal = 3)),
            constant = @Constant(intValue = 0,
                    expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO),
            require = 1,
            allow = 1)
    private int cavebiomes$secondaryCandidateFloor(int vanillaFloor) {
        return cavebiomes$portalFloor(this.world.provider.getDimension());
    }

    @ModifyConstant(
            method = "makePortal",
            slice = @Slice(
                    from = @At(value = "INVOKE", target = IS_AIR_BLOCK, ordinal = 3),
                    to = @At(value = "INVOKE", target = IS_AIR_BLOCK, ordinal = 4)),
            constant = @Constant(intValue = 0,
                    expandZeroConditions = Constant.Condition.GREATER_THAN_ZERO),
            require = 1,
            allow = 1)
    private int cavebiomes$secondaryDescentFloor(int vanillaFloor) {
        return cavebiomes$portalFloor(this.world.provider.getDimension());
    }
}
