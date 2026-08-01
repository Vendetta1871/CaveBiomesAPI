package net.celestiald.cavebiomes.core;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Runtime helpers called from Nothirium classes rewritten by
 * {@link NothiriumRenderTransformer}. Nothirium resolves section data and
 * world-bound face culling with hardcoded [0, 16) section bounds; these
 * helpers reroute that logic through the configured world height.
 */
public final class NothiriumRenderCompat {

    private NothiriumRenderCompat() {}

    /** True when the configured height exceeds the vanilla [0, 256) range. */
    public static boolean useExtendedHeight() {
        return WorldHeightAPI.getMinY() < 0 || WorldHeightAPI.getMaxY() > 256;
    }

    /**
     * Replacement for meldexun.nothirium.mc.util.WorldUtil#getSection: maps a
     * world section Y to a storage index relative to the configured minimum Y
     * instead of assuming storage section 0 sits at world Y=0.
     */
    public static ExtendedBlockStorage getSection(World world, int sectionX, int sectionY, int sectionZ) {
        int index = sectionY - WorldHeightAPI.getMinSection();
        if (index < 0 || index >= WorldHeightAPI.getSectionCount()) {
            return null;
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(sectionX, sectionZ);
        if (chunk == null) {
            return null;
        }
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        return index < storage.length ? storage[index] : null;
    }
}
