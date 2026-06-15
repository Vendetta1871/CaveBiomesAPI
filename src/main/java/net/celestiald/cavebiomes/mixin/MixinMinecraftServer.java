package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NetHandlerPlayServer rejects block placement above MinecraftServer.getBuildLimit()
 * (the server.properties max-build-height, default 256) with the red "Height limit
 * for building is N blocks" message — independent of World.isOutsideBuildHeight.
 * Raise the limit to the configured maxY so building up to maxY-1 is allowed.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Inject(method = "getBuildLimit", at = @At("HEAD"), cancellable = true)
    private void cavebiomes$buildLimit(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(WorldHeightAPI.getMaxY());
    }
}
