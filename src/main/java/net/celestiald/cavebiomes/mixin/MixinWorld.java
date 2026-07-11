package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Shadow public abstract boolean isBlockLoaded(BlockPos pos);
    @Shadow public abstract Chunk getChunkFromBlockCoords(BlockPos pos);
    @Shadow public abstract void notifyLightSet(BlockPos pos);
    @Shadow protected abstract boolean isChunkLoaded(int x, int z, boolean allowEmpty);

    // =========================================================================
    // Build-height bounds
    // =========================================================================

    @Inject(method = "isOutsideBuildHeight", at = @At("HEAD"), cancellable = true)
    private void fixOutsideBuildHeight(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(pos.getY() < WorldHeightAPI.getMinY() || pos.getY() >= WorldHeightAPI.getMaxY());
    }

    // Reimplement the small private bound/loaded-chunk loop. Using expandZeroConditions here also
    // rewrites vanilla's boolean false return constants and can turn an unloaded area into true.
    @Inject(method = "isAreaLoaded(IIIIIIZ)Z", at = @At("HEAD"), cancellable = true)
    private void cavebiomes$isAreaLoaded(int fromX, int fromY, int fromZ,
            int toX, int toY, int toZ, boolean allowEmpty,
            CallbackInfoReturnable<Boolean> cir) {
        if (toY < WorldHeightAPI.getMinY() || fromY >= WorldHeightAPI.getMaxY()) {
            cir.setReturnValue(false);
            return;
        }
        int minimumChunkX = fromX >> 4;
        int maximumChunkX = toX >> 4;
        int minimumChunkZ = fromZ >> 4;
        int maximumChunkZ = toZ >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; ++chunkX) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; ++chunkZ) {
                if (!this.isChunkLoaded(chunkX, chunkZ, allowEmpty)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
        cir.setReturnValue(true);
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

    // getLightFor: vanilla clamps Y<0 to Y=0 and rejects Y>=256 via isValid(pos).
    // Both prevent the BFS in checkLightFor from reading correct stored values at
    // extended-range positions, breaking light propagation outside [0,256).
    @Inject(method = "getLightFor", at = @At("HEAD"), cancellable = true)
    private void fixGetLightFor(EnumSkyBlock type, BlockPos pos,
                                 CallbackInfoReturnable<Integer> cir) {
        int y = pos.getY();
        if (y >= 0 && y < 256) return;
        if (y < WorldHeightAPI.getMinY() || y >= WorldHeightAPI.getMaxY()) {
            cir.setReturnValue(type.defaultLightValue);
            return;
        }
        if (!this.isBlockLoaded(pos)) { cir.setReturnValue(type.defaultLightValue); return; }
        cir.setReturnValue(this.getChunkFromBlockCoords(pos).getLightFor(type, pos));
    }

    // setLightFor: vanilla guards with isValid(pos) → isInvalid() → true for Y<0 or Y>=256.
    // The BFS never writes light values outside [0,256), so chunk arrays stay at 0 (darkness).
    @Inject(method = "setLightFor", at = @At("HEAD"), cancellable = true)
    private void fixSetLightFor(EnumSkyBlock type, BlockPos pos, int lightValue, CallbackInfo ci) {
        int y = pos.getY();
        if (y >= 0 && y < 256) return;
        if (y >= WorldHeightAPI.getMinY() && y < WorldHeightAPI.getMaxY()
                && this.isBlockLoaded(pos)) {
            this.getChunkFromBlockCoords(pos).setLightFor(type, pos, lightValue);
            this.notifyLightSet(pos);
        }
        ci.cancel();
    }

    // getLightFromNeighborsFor: same Y<0 clamp + isValid guard as getLightFor.
    // Used by World.getCombinedLight for entity lighting in extended Y range.
    @Inject(method = "getLightFromNeighborsFor", at = @At("HEAD"), cancellable = true)
    private void fixGetLightFromNeighborsFor(EnumSkyBlock type, BlockPos pos,
                                              CallbackInfoReturnable<Integer> cir) {
        int y = pos.getY();
        if (y >= 0 && y < 256) return;
        if (y < WorldHeightAPI.getMinY() || y >= WorldHeightAPI.getMaxY()) {
            cir.setReturnValue(type.defaultLightValue);
            return;
        }
        if (!this.isBlockLoaded(pos)) { cir.setReturnValue(type.defaultLightValue); return; }
        cir.setReturnValue(this.getChunkFromBlockCoords(pos).getLightFor(type, pos));
    }

    // =========================================================================
    // Snow / ice formation Y range (vanilla hardcodes 256)
    // =========================================================================

    @ModifyConstant(method = "canSnowAtBody", remap = false,
            constant = @Constant(intValue = 256))
    private int fixSnowMaxY(int original) {
        return WorldHeightAPI.getMaxY();
    }

    @ModifyConstant(method = "canBlockFreezeBody", remap = false,
            constant = @Constant(intValue = 256))
    private int fixFreezeMaxY(int original) {
        return WorldHeightAPI.getMaxY();
    }
}
