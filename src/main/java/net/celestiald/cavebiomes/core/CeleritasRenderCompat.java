package net.celestiald.cavebiomes.core;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Runtime helpers called from Celeritas and Actinium classes rewritten by
 * {@link CeleritasRenderTransformer}. Both renderers resolve chunk sections
 * with hardcoded vanilla [0, 16) section bounds; these helpers reroute that
 * logic through the configured world height. Actinium additionally exposes a
 * Depths Update compatibility shim whose storage mapping does not match the
 * linear shift used here, so the transformer prepends early returns that
 * delegate to this class while the configured height is extended.
 */
public final class CeleritasRenderCompat {

    private CeleritasRenderCompat() {}

    /** True when the configured height exceeds the vanilla [0, 256) range. */
    public static boolean useExtendedHeight() {
        return WorldHeightAPI.getMinY() < 0 || WorldHeightAPI.getMaxY() > 256;
    }

    /** Section Y of the lowest stored section (0 for vanilla heights). */
    public static int getMinSection() {
        return WorldHeightAPI.getMinSection();
    }

    /** Exclusive upper section bound (16 for vanilla heights). */
    public static int getMaxSection() {
        return WorldHeightAPI.getMinSection() + WorldHeightAPI.getSectionCount();
    }

    /** Number of chunk sections per column (16 for vanilla heights). */
    public static int getSectionCount() {
        return WorldHeightAPI.getSectionCount();
    }

    /** Lowest build height (0 for vanilla heights). */
    public static int getMinBuildHeight() {
        return WorldHeightAPI.getMinY();
    }

    /** Highest build height (256 for vanilla heights). */
    public static int getMaxBuildHeight() {
        return WorldHeightAPI.getMaxY();
    }

    /**
     * Maps a world section Y to a storage index relative to the configured
     * minimum Y instead of assuming storage section 0 sits at world Y=0.
     * Returns null when the section lies outside the stored range.
     */
    public static ExtendedBlockStorage getSection(Chunk chunk, int sectionY) {
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int index = sectionY - WorldHeightAPI.getMinSection();
        return index >= 0 && index < storage.length ? storage[index] : null;
    }

    /**
     * Replacement for Celeritas' VintageRenderSectionManager#isSectionVisuallyEmpty:
     * identical to the original except the storage lookup maps the section Y
     * through the configured minimum Y.
     */
    public static boolean isSectionVisuallyEmpty(World world, int x, int y, int z) {
        Chunk chunk = world.getChunkFromChunkCoords(x, z);
        if (chunk.isEmpty()) {
            return true;
        }
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int index = y - WorldHeightAPI.getMinSection();
        if (index < 0 || index >= storage.length) {
            return true;
        }
        return storage[index] == Chunk.NULL_BLOCK_STORAGE || storage[index].isEmpty();
    }

    /**
     * Replacement for Actinium's DepthsUpdateCompat#toStorageIndex: maps a
     * world section Y to a linearly shifted storage index, or -1 when the
     * section lies outside the configured height range.
     */
    public static int toStorageIndex(int sectionY) {
        int index = sectionY - WorldHeightAPI.getMinSection();
        return index >= 0 && index < WorldHeightAPI.getSectionCount() ? index : -1;
    }
}
