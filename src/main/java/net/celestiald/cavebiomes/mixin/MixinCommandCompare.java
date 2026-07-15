package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.command.CommandCompare;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Extends /testforblocks' source and destination guards to the configured finite range. */
@Mixin(CommandCompare.class)
public abstract class MixinCommandCompare {

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
                    from = @At(value = "CONSTANT", args = "intValue=524288"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;")),
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/gen/structure/StructureBoundingBox;minY:I",
                    opcode = Opcodes.GETFIELD, ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$sourceMinimumY(StructureBoundingBox bounds, MinecraftServer server,
            ICommandSender sender, String[] args) {
        return cavebiomes$relativeToMinimum(bounds.minY, sender);
    }

    @Redirect(
            method = "execute",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=524288"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;")),
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/gen/structure/StructureBoundingBox;maxY:I",
                    opcode = Opcodes.GETFIELD, ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$sourceMaximumY(StructureBoundingBox bounds, MinecraftServer server,
            ICommandSender sender, String[] args) {
        return cavebiomes$relativeToVanillaMaximum(bounds.maxY, sender);
    }

    @Redirect(
            method = "execute",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=524288"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;")),
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/gen/structure/StructureBoundingBox;minY:I",
                    opcode = Opcodes.GETFIELD, ordinal = 1),
            require = 1,
            allow = 1)
    private int cavebiomes$destinationMinimumY(StructureBoundingBox bounds,
            MinecraftServer server, ICommandSender sender, String[] args) {
        return cavebiomes$relativeToMinimum(bounds.minY, sender);
    }

    @Redirect(
            method = "execute",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=524288"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;")),
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/gen/structure/StructureBoundingBox;maxY:I",
                    opcode = Opcodes.GETFIELD, ordinal = 1),
            require = 1,
            allow = 1)
    private int cavebiomes$destinationMaximumY(StructureBoundingBox bounds,
            MinecraftServer server, ICommandSender sender, String[] args) {
        return cavebiomes$relativeToVanillaMaximum(bounds.maxY, sender);
    }
}
