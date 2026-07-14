package net.celestiald.cavebiomes.api;

import net.celestiald.cavebiomes.config.WorldHeightConfig;

/**
 * Public API for querying configured world height.
 * Other mods should use this class to interact with CaveBiomesAPI.
 */
public final class WorldHeightAPI {

    private static volatile int minimumY = -64;
    private static volatile int maximumY = 320;
    private static volatile int configuredMinimumY = -64;
    private static volatile int configuredMaximumY = 320;

    private WorldHeightAPI() {}

    /** Minimum Y coordinate (default 0, can be -64 or lower, always a multiple of 16). */
    public static int getMinY() {
        return minimumY;
    }

    /** Maximum Y coordinate (default 256, can be 320 or higher, always a multiple of 16). */
    public static int getMaxY() {
        return maximumY;
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

    /** Converts an absolute section Y coordinate to a storageArrays index. */
    public static int sectionIndexFromSectionY(int sectionY) {
        return sectionY - getMinSection();
    }

    /** Returns the Y base (bottom of section) for a given storageArrays index. */
    public static int sectionYBase(int sectionIdx) {
        return sectionIdx * 16 + getMinY();
    }

    /** Applies the local configuration and records it as the disconnect fallback. */
    public static synchronized void configureLocalRange(int minY, int maxY) {
        validateRange(minY, maxY);
        configuredMinimumY = minY;
        configuredMaximumY = maxY;
        minimumY = minY;
        maximumY = maxY;
    }

    /** Applies the authoritative range received from a multiplayer server. */
    public static synchronized void applyServerRange(int minY, int maxY) {
        validateRange(minY, maxY);
        minimumY = minY;
        maximumY = maxY;
    }

    /** Restores the client-side local range after leaving a server. */
    public static synchronized void resetToConfiguredRange() {
        minimumY = configuredMinimumY;
        maximumY = configuredMaximumY;
    }

    private static void validateRange(int minY, int maxY) {
        if ((minY & 15) != 0 || (maxY & 15) != 0 || minY > 0 || maxY < 256
                || maxY <= minY) {
            throw new IllegalArgumentException("Invalid finite world range " + minY + ".." + maxY);
        }
        int sections = (maxY - minY) / 16;
        if (sections > WorldHeightConfig.MAX_SECTIONS) {
            throw new IllegalArgumentException("Finite world range needs " + sections
                    + " sections; maximum is " + WorldHeightConfig.MAX_SECTIONS);
        }
    }
}
