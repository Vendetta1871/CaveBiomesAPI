package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.command.CommandFill;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
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
    private static int cavebiomes$relativeToMinimum(int worldY, ICommandSender sender) {
        return WorldHeightAPI.usesExtendedHeight(sender.getEntityWorld())
                ? worldY - WorldHeightAPI.getMinY() : worldY;
    }

    @Unique
    private static int cavebiomes$relativeToVanillaMaximum(int worldY, ICommandSender sender) {
        return WorldHeightAPI.usesExtendedHeight(sender.getEntityWorld())
                ? worldY - WorldHeightAPI.getMaxY() + 256 : worldY;
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
    private int cavebiomes$minimumVolumeY(BlockPos pos, MinecraftServer server,
            ICommandSender sender, String[] args) {
        return cavebiomes$relativeToMinimum(pos.getY(), sender);
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
    private int cavebiomes$maximumVolumeY(BlockPos pos, MinecraftServer server,
            ICommandSender sender, String[] args) {
        return cavebiomes$relativeToVanillaMaximum(pos.getY(), sender);
    }
}
