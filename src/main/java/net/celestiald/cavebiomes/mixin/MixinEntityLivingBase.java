package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extends the shared chorus-fruit/enderman landing scan below vanilla Y=0. */
@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase {

    @Unique
    private static int cavebiomes$relativeToTeleportFloor(int worldY, int dimension) {
        return dimension == 0 ? worldY - WorldHeightAPI.getMinY() : worldY;
    }

    @Redirect(
            method = "attemptTeleport",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I",
                    ordinal = 0),
            require = 1,
            allow = 1)
    private int cavebiomes$teleportLandingFloor(BlockPos pos) {
        EntityLivingBase entity = (EntityLivingBase) (Object) this;
        return cavebiomes$relativeToTeleportFloor(
                pos.getY(), entity.world.provider.getDimension());
    }
}
