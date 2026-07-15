package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Moves the piston's hardcoded lower push boundary to the configured minimum Y. */
@Mixin(BlockPistonBase.class)
public abstract class MixinBlockPistonBase {

    @Unique
    private static int cavebiomes$relativeToMinimum(int worldY, World world) {
        return WorldHeightAPI.usesExtendedHeight(world)
                ? worldY - WorldHeightAPI.getMinY() : worldY;
    }

    @Redirect(
            method = "canPush",
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/border/WorldBorder;contains(Lnet/minecraft/util/math/BlockPos;)Z"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/World;getHeight()I", ordinal = 0)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 0),
            require = 1,
            allow = 1)
    private static int cavebiomes$minimumPushY(BlockPos queriedPos, IBlockState state,
            World world, BlockPos blockPos, EnumFacing movement, boolean destroyBlocks,
            EnumFacing pistonFacing) {
        return cavebiomes$relativeToMinimum(queriedPos.getY(), world);
    }

    @Redirect(
            method = "canPush",
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/border/WorldBorder;contains(Lnet/minecraft/util/math/BlockPos;)Z"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/World;getHeight()I", ordinal = 0)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 1),
            require = 1,
            allow = 1)
    private static int cavebiomes$downwardPushY(BlockPos queriedPos, IBlockState state,
            World world, BlockPos blockPos, EnumFacing movement, boolean destroyBlocks,
            EnumFacing pistonFacing) {
        return cavebiomes$relativeToMinimum(queriedPos.getY(), world);
    }
}
