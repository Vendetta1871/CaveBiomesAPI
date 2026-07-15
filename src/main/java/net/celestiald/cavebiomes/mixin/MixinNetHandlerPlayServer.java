package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the extended placement ceiling only in an opted-in Overworld. */
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {

    @Shadow public EntityPlayerMP player;

    @Redirect(
            method = "processPlayerDigging",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I"),
            require = 1,
            allow = 1)
    private int cavebiomes$diggingBuildLimit(MinecraftServer server) {
        return cavebiomes$buildLimit(server);
    }

    @Redirect(
            method = "processTryUseItemOnBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I"),
            require = 3,
            allow = 3)
    private int cavebiomes$placementBuildLimit(MinecraftServer server) {
        return cavebiomes$buildLimit(server);
    }

    @Unique
    private int cavebiomes$buildLimit(MinecraftServer server) {
        return WorldHeightAPI.usesExtendedHeight(this.player.getServerWorld())
                ? WorldHeightAPI.getMaxY()
                : server.getBuildLimit();
    }
}
