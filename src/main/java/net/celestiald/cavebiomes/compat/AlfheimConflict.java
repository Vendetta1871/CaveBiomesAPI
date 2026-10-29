package net.celestiald.cavebiomes.compat;

import net.celestiald.cavebiomes.client.AlfheimErrorScreen;
import net.celestiald.cavebiomes.config.WorldHeightConfig;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Arrays;
import java.util.List;

/**
 * Cave Biomes API refuses to start next to Alfheim while the configured world
 * height is extended. Alfheim rewrites Chunk lighting for the vanilla 0..256
 * range: its Chunk#getLightFor overwrite reads chunk sections through raw
 * {@code y >> 4} indices, its relightBlock masks the height map with 255, and
 * its lighting engine encodes queued positions so that Y below zero never
 * enters a queue. Whichever mod wins the Chunk#getLightFor overwrite clash, an
 * extended-height world either crashes on load (ArrayIndexOutOfBoundsException
 * at a negative section index, reached through Fluidlogged API light relight)
 * or silently reads light from sections 64 blocks away from the queried spot.
 * A vanilla-height pack is unaffected, so the refusal only triggers while the
 * configured range leaves 0..256. A client learns about the refusal through
 * FML's custom loading error screen; a dedicated server, which has no screen,
 * through the exception that stops it.
 */
public final class AlfheimConflict {
    public static final String MODID = "alfheim";
    public static final String TITLE =
            "Cave Biomes API cannot run together with Alfheim on extended world height";

    private AlfheimConflict() {
    }

    /** The refusal text, pre-wrapped for the error screen's font. */
    public static List<String> messageLines() {
        return Arrays.asList(
                "Alfheim rewrites Chunk lighting for the vanilla Y 0..256 range.",
                "In a world taller than that it reads chunk sections at wrong",
                "heights and crashes the game on world load",
                "(ArrayIndexOutOfBoundsException: -1).",
                "",
                "Remove Alfheim (alfheim) from the mods folder and start the game",
                "again, or set min_world_y = 0 and max_world_y = 256 in",
                "config/cavebiomesapi.cfg to keep the vanilla world height.");
    }

    /** True when the configured range leaves the 0..256 band Alfheim assumes. */
    public static boolean conflicts(int minY, int maxY, boolean alfheimInstalled) {
        return alfheimInstalled && (minY < 0 || maxY > 256);
    }

    /** Never returns when Alfheim is installed next to an extended world height. */
    public static void haltIfExtendedHeight() {
        if (!conflicts(WorldHeightConfig.minY, WorldHeightConfig.maxY,
                Loader.isModLoaded(MODID))) {
            return;
        }
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            throw new AlfheimErrorScreen();
        }
        throw new LoaderException(TITLE + ". " + String.join(" ", messageLines()));
    }
}
