package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends the Y coordinate accepted by commands which use CommandBase.parseBlockPos. */
@Mixin(CommandBase.class)
public abstract class MixinCommandBase {

    @Inject(method = "parseBlockPos", at = @At("HEAD"), cancellable = true)
    private static void cavebiomes$parseBlockPos(ICommandSender sender, String[] args,
            int startIndex, boolean centerBlock,
            CallbackInfoReturnable<BlockPos> cir) throws NumberInvalidException {
        BlockPos origin = sender.getPosition();
        cir.setReturnValue(new BlockPos(
                CommandBase.parseDouble(origin.getX(), args[startIndex],
                        -30000000, 30000000, centerBlock),
                CommandBase.parseDouble(origin.getY(), args[startIndex + 1],
                        WorldHeightAPI.getMinY(), WorldHeightAPI.getMaxY() - 1, false),
                CommandBase.parseDouble(origin.getZ(), args[startIndex + 2],
                        -30000000, 30000000, centerBlock)));
    }
}
