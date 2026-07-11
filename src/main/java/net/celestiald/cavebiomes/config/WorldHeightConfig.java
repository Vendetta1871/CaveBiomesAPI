package net.celestiald.cavebiomes.config;

import net.minecraftforge.common.config.Configuration;
import net.celestiald.cavebiomes.api.WorldHeightAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public final class WorldHeightConfig {

    private static final Logger LOGGER = LogManager.getLogger("CaveBiomesAPI/Config");

    /** Hard cap: chunk sections are packed into a 32-bit mask in SPacketChunkData. */
    public static final int MAX_SECTIONS = 32;

    public static int minY = -64;
    public static int maxY = 320;

    public static void init(File configDir) {
        Configuration cfg = new Configuration(new File(configDir, "cavebiomesapi.cfg"));
        cfg.load();

        minY = cfg.getInt("min_world_y", "world", -64, -256, 0,
                "Minimum world Y coordinate. Must be <= 0 and a multiple of 16.");
        maxY = cfg.getInt("max_world_y", "world", 320, 256, 512,
                "Maximum world Y coordinate. Must be >= 256 and a multiple of 16.");

        // Snap to section boundaries (16 blocks).
        minY = Math.floorDiv(minY, 16) * 16;
        maxY = Math.floorDiv(maxY, 16) * 16;

        if (maxY <= minY) {
            LOGGER.warn("max_world_y ({}) <= min_world_y ({}); resetting to vanilla 0..256.", maxY, minY);
            minY = 0;
            maxY = 256;
        }

        // Section count must fit the 32-bit chunk-data network mask.
        int sections = (maxY - minY) / 16;
        if (sections > MAX_SECTIONS) {
            int clampedMax = minY + MAX_SECTIONS * 16;
            LOGGER.warn("World height {}..{} needs {} sections (> {} supported by the network "
                            + "chunk packet); clamping max_world_y to {}.",
                    minY, maxY, sections, MAX_SECTIONS, clampedMax);
            maxY = clampedMax;
        }

        WorldHeightAPI.configureLocalRange(minY, maxY);
        if (cfg.hasChanged()) cfg.save();
    }
}
