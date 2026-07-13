package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.block.BlockStaticLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Random;

/** Keeps lava fire checks from loading chunks across the configured vertical range. */
@Mixin(BlockStaticLiquid.class)
public abstract class MixinBlockStaticLiquid {

    private static final String BLOCK_POS_ADD =
            "Lnet/minecraft/util/math/BlockPos;add(III)Lnet/minecraft/util/math/BlockPos;";
    private static final String BLOCK_POS_Y =
            "Lnet/minecraft/util/math/BlockPos;getY()I";
    private static final String FIRE_PLACE_EVENT =
            "Lnet/minecraftforge/event/ForgeEventFactory;fireFluidPlaceBlockEvent("
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)"
                    + "Lnet/minecraft/block/state/IBlockState;";

    @Unique
    private static int cavebiomes$relativeToMinimum(int worldY, int dimension) {
        return dimension == 0 ? worldY - WorldHeightAPI.getMinY() : worldY;
    }

    @Unique
    private static int cavebiomes$relativeToMaximum(int worldY, int dimension) {
        return dimension == 0
                ? worldY - WorldHeightAPI.getMaxY() + 256
                : worldY;
    }

    @Redirect(
            method = "updateTick",
            slice = @Slice(
                    from = @At(value = "INVOKE", target = BLOCK_POS_ADD, ordinal = 0),
                    to = @At(value = "INVOKE", target = FIRE_PLACE_EVENT,
                            ordinal = 0, remap = false)),
            at = @At(value = "INVOKE", target = BLOCK_POS_Y, ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$ascendingCandidateFloor(BlockPos candidate, World world,
            BlockPos lavaPos, IBlockState state, Random random) {
        return cavebiomes$relativeToMinimum(
                candidate.getY(), world.provider.getDimension());
    }

    @Redirect(
            method = "updateTick",
            slice = @Slice(
                    from = @At(value = "INVOKE", target = BLOCK_POS_ADD, ordinal = 1),
                    to = @At(value = "INVOKE", target = FIRE_PLACE_EVENT,
                            ordinal = 1, remap = false)),
            at = @At(value = "INVOKE", target = BLOCK_POS_Y, ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$horizontalCandidateFloor(BlockPos candidate, World world,
            BlockPos lavaPos, IBlockState state, Random random) {
        return cavebiomes$relativeToMinimum(
                candidate.getY(), world.provider.getDimension());
    }

    @Redirect(
            method = "updateTick",
            slice = @Slice(
                    from = @At(value = "INVOKE", target = BLOCK_POS_ADD, ordinal = 1),
                    to = @At(value = "INVOKE", target = FIRE_PLACE_EVENT,
                            ordinal = 1, remap = false)),
            at = @At(value = "INVOKE", target = BLOCK_POS_Y, ordinal = 1),
            require = 1,
            allow = 1)
    private int cavebiomes$horizontalCandidateCeiling(BlockPos candidate, World world,
            BlockPos lavaPos, IBlockState state, Random random) {
        return cavebiomes$relativeToMaximum(
                candidate.getY(), world.provider.getDimension());
    }

    @Redirect(
            method = "getCanBlockBurn",
            at = @At(value = "INVOKE", target = BLOCK_POS_Y, ordinal = 0),
            // Fluidlogged API replaces this method with a hook that calls
            // World.isOutsideBuildHeight, which MixinWorld already extends.
            require = 0,
            expect = 0,
            allow = 1)
    private int cavebiomes$flammableCandidateFloor(BlockPos candidate, World world,
            BlockPos checkedPos) {
        return cavebiomes$relativeToMinimum(
                candidate.getY(), world.provider.getDimension());
    }

    @Redirect(
            method = "getCanBlockBurn",
            at = @At(value = "INVOKE", target = BLOCK_POS_Y, ordinal = 1),
            require = 0,
            expect = 0,
            allow = 1)
    private int cavebiomes$flammableCandidateCeiling(BlockPos candidate, World world,
            BlockPos checkedPos) {
        return cavebiomes$relativeToMaximum(
                candidate.getY(), world.provider.getDimension());
    }
}
