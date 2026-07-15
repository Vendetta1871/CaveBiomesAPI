package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extends the initial player collision lift to the configured Overworld ceiling. */
@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP {

    @Unique
    private static double cavebiomes$relativeToConfiguredMaximum(double worldY,
            net.minecraft.world.World world) {
        return WorldHeightAPI.usesExtendedHeight(world)
                ? worldY - WorldHeightAPI.getMaxY() + 256.0D
                : worldY;
    }

    @Redirect(
            method = "<init>",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/entity/player/EntityPlayerMP;posY:D",
                    ordinal = 0),
            require = 1,
            allow = 1)
    private double cavebiomes$initialSpawnCollisionCeiling(EntityPlayerMP player) {
        return cavebiomes$relativeToConfiguredMaximum(player.posY, player.world);
    }
}
