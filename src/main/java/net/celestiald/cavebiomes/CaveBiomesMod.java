package net.celestiald.cavebiomes;

import net.celestiald.cavebiomes.config.WorldHeightConfig;
import net.celestiald.cavebiomes.network.WorldHeightNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

import java.util.Map;

@Mod(modid = CaveBiomesMod.MODID, name = CaveBiomesMod.NAME, version = CaveBiomesMod.VERSION,
        acceptedMinecraftVersions = "1.12.2")
public class CaveBiomesMod {

    public static final String MODID   = "cavebiomesapi";
    public static final String NAME    = "Cave Biomes API";
    public static final String VERSION = "1.1.0";

    @Mod.Instance
    public static CaveBiomesMod INSTANCE;

    private static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        WorldHeightConfig.init(event.getModConfigurationDirectory());
        WorldHeightNetwork.initialize();
        logger.info("World height: Y {} to {}", WorldHeightConfig.minY, WorldHeightConfig.maxY);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        logger.info("Cave Biomes API initialized.");
    }

    @NetworkCheckHandler
    public boolean checkRemoteVersion(Map<String, String> remoteVersions, Side remoteSide) {
        return VERSION.equals(remoteVersions.get(MODID));
    }
}
