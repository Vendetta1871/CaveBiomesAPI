package net.celestiald.cavebiomes.network;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Owns the CaveBiomesAPI range-sync channel and connection lifecycle. */
public final class WorldHeightNetwork {
    private static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("cavebiomes:height");
    private static final WorldHeightNetwork INSTANCE = new WorldHeightNetwork();
    private static boolean initialized;
    private volatile boolean resetClientRangeWhenWorldCloses;

    private WorldHeightNetwork() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        CHANNEL.registerMessage(WorldHeightMessage.Handler.class,
                WorldHeightMessage.class, 0, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        initialized = true;
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            CHANNEL.sendTo(new WorldHeightMessage(
                    WorldHeightAPI.getMinY(), WorldHeightAPI.getMaxY()),
                    (EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void clientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        resetClientRangeWhenWorldCloses = false;
        WorldHeightAPI.resetToConfiguredRange();
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void clientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        resetClientRangeWhenWorldCloses = true;
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && resetClientRangeWhenWorldCloses
                && Minecraft.getMinecraft().world == null) {
            resetClientRangeWhenWorldCloses = false;
            WorldHeightAPI.resetToConfiguredRange();
        }
    }
}
