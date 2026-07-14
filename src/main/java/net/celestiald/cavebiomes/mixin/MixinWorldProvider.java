package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends the provider methods to which Forge 1.12 delegates World height queries. */
@Mixin(WorldProvider.class)
public abstract class MixinWorldProvider {

    @Shadow protected boolean nether;

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true, remap = false)
    private void cavebiomes$getHeight(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(WorldHeightAPI.getMaxY());
    }

    @Inject(method = "getActualHeight", at = @At("HEAD"), cancellable = true, remap = false)
    private void cavebiomes$getActualHeight(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.nether ? 128 : WorldHeightAPI.getMaxY());
    }
}
