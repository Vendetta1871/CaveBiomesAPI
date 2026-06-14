package net.celestiald.cavebiomes.api;

import net.celestiald.cavebiomes.config.WorldHeightConfig;

/**
 * Public API for querying configured world height.
 * Other mods should use this class to interact with CaveBiomesAPI.
 */
public final class WorldHeightAPI {

    private WorldHeightAPI() {}

    /** Minimum Y coordinate (default 0, can be -64 or lower, always a multiple of 16). */
    public static int getMinY() {
        return WorldHeightConfig.minY;
    }

    /** Maximum Y coordinate (default 256, can be 320 or higher, always a multiple of 16). */
    public static int getMaxY() {
        return WorldHeightConfig.maxY;
    }

    /** Total number of 16-block chunk sections per column. */
    public static int getSectionCount() {
        return (getMaxY() - getMinY()) / 16;
    }

    /**
     * Section index of the lowest section (e.g. -4 when minY == -64).
     * storageArrays[0] corresponds to this section.
     */
    public static int getMinSection() {
        return getMinY() / 16;
    }

    /**
     * Converts a world Y coordinate to a storageArrays index.
     * Section 0 covers [minY, minY+15].
     */
    public static int sectionIndex(int worldY) {
        return (worldY - getMinY()) >> 4;
    }

    /** Returns the Y base (bottom of section) for a given storageArrays index. */
    public static int sectionYBase(int sectionIdx) {
        return sectionIdx * 16 + getMinY();
    }
}
