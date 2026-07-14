package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.command.CommandFill;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Extends /fill's own volume guard after CommandBase has parsed the coordinates. */
@Mixin(CommandFill.class)
public abstract class MixinCommandFill {

    @Unique
    private static int cavebiomes$relativeToMinimum(int worldY) {
        return worldY - WorldHeightAPI.getMinY();
    }

    @Unique
    private static int cavebiomes$relativeToVanillaMaximum(int worldY) {
        return worldY - WorldHeightAPI.getMaxY() + 256;
    }

    @Redirect(
            method = "execute",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=32768"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$minimumVolumeY(BlockPos pos) {
        return cavebiomes$relativeToMinimum(pos.getY());
    }

    @Redirect(
            method = "execute",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=32768"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I", ordinal = 1),
            require = 1,
            allow = 1)
    private int cavebiomes$maximumVolumeY(BlockPos pos) {
        return cavebiomes$relativeToVanillaMaximum(pos.getY());
    }
}
