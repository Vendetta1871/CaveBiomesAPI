package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * World-level height patches. Everything here uses @Inject / @ModifyConstant
 * (which are remapped via the generated refmap) rather than @Overwrite — in this
 * ForgeGradle dev setup @Overwrite member names are not remapped through the
 * refmap, so an @Overwrite of a method whose name differs at runtime silently
 * fails the whole mixin.
 */
@Mixin(value = World.class, priority = 1001)
public abstract class MixinWorld {

    // =========================================================================
    // World height: vanilla World.getHeight()/getActualHeight() hardcode 256.
    // (They live in World, NOT WorldProvider — targeting WorldProvider was the
    // original startup-crash bug.)
    // =========================================================================

    @ModifyConstant(method = "getHeight()I", constant = @Constant(intValue = 256))
    private int fixGetHeight(int original) {
        return WorldHeightAPI.getMaxY();
    }

    // getActualHeight: nether keeps 128, everything else uses configured maxY.
    @ModifyConstant(method = "getActualHeight", constant = @Constant(intValue = 256))
    private int fixGetActualHeight(int original) {
        return WorldHeightAPI.getMaxY();
    }

    // =========================================================================
    // Build-height bounds
    // =========================================================================

    @Inject(method = "isOutsideBuildHeight", at = @At("HEAD"), cancellable = true)
    private void fixOutsideBuildHeight(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(pos.getY() < WorldHeightAPI.getMinY() || pos.getY() >= WorldHeightAPI.getMaxY());
    }

    // isAreaLoaded(int,int,int,int,int,int,boolean): upper Y guard 256 -> maxY.
    @ModifyConstant(method = "isAreaLoaded(IIIIIIZ)Z", constant = @Constant(intValue = 256))
    private int fixAreaLoadedMaxY(int original) {
        return WorldHeightAPI.getMaxY();
    }

    // =========================================================================
    // Lighting bounds.
    //   Upper limit: getLight(...) clamps queries at the vanilla 256 ceiling;
    //   bump it to maxY so blocks up to maxY-1 report real light.
    //   Lower limit: treat anything below minY as unlit (vanilla already returns
    //   0 / remaps for y < 0, so this only matters for negative minY values).
    // =========================================================================

    @ModifyConstant(method = "getLight(Lnet/minecraft/util/math/BlockPos;)I",
            constant = @Constant(intValue = 256))
    private int fixGetLightMaxY(int original) {
        return WorldHeightAPI.getMaxY();
    }

    @ModifyConstant(method = "getLight(Lnet/minecraft/util/math/BlockPos;Z)I",
            constant = @Constant(intValue = 256))
    private int fixGetLightBoolMaxY(int original) {
        return WorldHeightAPI.getMaxY();
    }

    @Inject(method = "getLight(Lnet/minecraft/util/math/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private void fixGetLightClamp(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (pos.getY() < WorldHeightAPI.getMinY()) cir.setReturnValue(0);
    }

    @Inject(method = "getLight(Lnet/minecraft/util/math/BlockPos;Z)I", at = @At("HEAD"), cancellable = true)
    private void fixGetLightBoolClamp(BlockPos pos, boolean checkNeighbors, CallbackInfoReturnable<Integer> cir) {
        if (pos.getY() < WorldHeightAPI.getMinY()) cir.setReturnValue(0);
    }

    // (No lower clamp on getLightFor / getLightFromNeighborsFor: vanilla already
    // handles y < 0 safely, and below-minY light is a cosmetic edge case.)

    // =========================================================================
    // Snow / ice formation Y range (vanilla hardcodes 256)
    // =========================================================================

    @ModifyConstant(method = "canSnowAt", constant = @Constant(intValue = 256))
    private int fixSnowMaxY(int original) {
        return WorldHeightAPI.getMaxY();
    }

    @ModifyConstant(method = "canBlockFreeze(Lnet/minecraft/util/math/BlockPos;Z)Z",
            constant = @Constant(intValue = 256))
    private int fixFreezeMaxY(int original) {
        return WorldHeightAPI.getMaxY();
    }
}
