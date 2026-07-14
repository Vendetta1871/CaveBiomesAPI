package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extends the respawn collision lift to the configured Overworld ceiling. */
@Mixin(PlayerList.class)
public abstract class MixinPlayerList {

    @Unique
    private static double cavebiomes$relativeToConfiguredMaximum(double worldY,
            int dimension) {
        return dimension == 0
                ? worldY - WorldHeightAPI.getMaxY() + 256.0D
                : worldY;
    }

    @Redirect(
            method = "recreatePlayerEntity",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/entity/player/EntityPlayerMP;posY:D",
                    ordinal = 0),
            require = 1,
            allow = 1)
    private double cavebiomes$respawnCollisionCeiling(EntityPlayerMP player) {
        return cavebiomes$relativeToConfiguredMaximum(
                player.posY, player.dimension);
    }
}
