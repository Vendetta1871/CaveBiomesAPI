package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.tileentity.TileEntityBeacon;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

/** Extends Overworld beacon beam and base scans across the configured height. */
@Mixin(TileEntityBeacon.class)
public abstract class MixinTileEntityBeacon {

    private static final String IS_COMPLETE =
            "Lnet/minecraft/tileentity/TileEntityBeacon;isComplete:Z";
    private static final String GET_BLOCK_STATE =
            "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;";

    @Unique
    private static int cavebiomes$beamCeilingForDimension(int vanillaCeiling,
            int dimension) {
        return dimension == 0 ? WorldHeightAPI.getMaxY() : vanillaCeiling;
    }

    @Unique
    private static int cavebiomes$baseFloorForDimension(int vanillaFloor,
            int dimension) {
        return dimension == 0 ? WorldHeightAPI.getMinY() : vanillaFloor;
    }

    @ModifyConstant(
            method = "updateSegmentColors",
            constant = @Constant(intValue = 256),
            require = 1,
            allow = 1)
    private int cavebiomes$beamCeiling(int vanillaCeiling) {
        return cavebiomes$beamCeilingForDimension(
                vanillaCeiling, cavebiomes$dimension());
    }

    @ModifyConstant(
            method = "updateSegmentColors",
            slice = @Slice(
                    from = @At(value = "FIELD", target = IS_COMPLETE,
                            opcode = Opcodes.GETFIELD, ordinal = 0),
                    to = @At(value = "INVOKE", target = GET_BLOCK_STATE, ordinal = 1)),
            constant = @Constant(intValue = 0,
                    expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO),
            require = 1,
            allow = 1)
    private int cavebiomes$baseFloor(int vanillaFloor) {
        return cavebiomes$baseFloorForDimension(
                vanillaFloor, cavebiomes$dimension());
    }

    @Unique
    private int cavebiomes$dimension() {
        return ((TileEntityBeacon) (Object) this).getWorld().provider.getDimension();
    }
}
