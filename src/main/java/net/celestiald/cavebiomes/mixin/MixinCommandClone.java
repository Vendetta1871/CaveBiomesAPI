package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.command.CommandClone;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Extends /clone's source and destination volume guards to the configured finite range. */
@Mixin(CommandClone.class)
public abstract class MixinCommandClone {

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
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/gen/structure/StructureBoundingBox;minY:I",
                    opcode = Opcodes.GETFIELD, ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$sourceMinimumY(StructureBoundingBox bounds) {
        return cavebiomes$relativeToMinimum(bounds.minY);
    }

    @Redirect(
            method = "execute",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=32768"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;")),
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/gen/structure/StructureBoundingBox;maxY:I",
                    opcode = Opcodes.GETFIELD, ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$sourceMaximumY(StructureBoundingBox bounds) {
        return cavebiomes$relativeToVanillaMaximum(bounds.maxY);
    }

    @Redirect(
            method = "execute",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=32768"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;")),
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/gen/structure/StructureBoundingBox;minY:I",
                    opcode = Opcodes.GETFIELD, ordinal = 1),
            require = 1,
            allow = 1)
    private int cavebiomes$destinationMinimumY(StructureBoundingBox bounds) {
        return cavebiomes$relativeToMinimum(bounds.minY);
    }

    @Redirect(
            method = "execute",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "intValue=32768"),
                    to = @At(value = "INVOKE",
                            target = "Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;")),
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/gen/structure/StructureBoundingBox;maxY:I",
                    opcode = Opcodes.GETFIELD, ordinal = 1),
            require = 1,
            allow = 1)
    private int cavebiomes$destinationMaximumY(StructureBoundingBox bounds) {
        return cavebiomes$relativeToVanillaMaximum(bounds.maxY);
    }
}
