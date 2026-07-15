package net.celestiald.cavebiomes.api;

import net.celestiald.cavebiomes.config.WorldHeightConfig;
import net.minecraft.world.World;

/**
 * Public API for querying configured world height.
 * Other mods should use this class to interact with CaveBiomesAPI.
 */
public final class WorldHeightAPI {

    private static volatile Range activeRange = new Range(-64, 320);
    private static volatile Range configuredRange = activeRange;
    private static volatile boolean rangeFrozen;

    private WorldHeightAPI() {}

    /** Minimum Y coordinate (default 0, can be -64 or lower, always a multiple of 16). */
    public static int getMinY() {
        return activeRange.minimumY;
    }

    /** Maximum Y coordinate (default 256, can be 320 or higher, always a multiple of 16). */
    public static int getMaxY() {
        return activeRange.maximumY;
    }

    /** Total number of 16-block chunk sections per column. */
    public static int getSectionCount() {
        return activeRange.sectionCount;
    }

    /**
     * Section index of the lowest section (e.g. -4 when minY == -64).
     * storageArrays[0] corresponds to this section.
     */
    public static int getMinSection() {
        return activeRange.minimumSection;
    }

    /**
     * Returns whether this world deliberately opted into extended-height
     * semantics. Chunk storage remains wide for every world because the array
     * layout is process-wide, but only marked Overworld types use the extended
     * build range.
     */
    public static boolean usesExtendedHeight(World world) {
        return world != null
                && world.provider != null
                && world.provider.getDimension() == 0
                && world.getWorldType() instanceof IExtendedHeightWorldType;
    }

    /**
     * Moves a vanilla lower-world boundary by the active minimum Y.
     *
     * <p>Legacy dimension and void handlers commonly express their lower
     * boundary relative to vanilla's Y=0 floor. For example, a transition at
     * Y=-10 should occur at Y=-74 in an extended Overworld whose minimum is
     * Y=-64. Worlds without extended-height semantics keep the supplied
     * boundary unchanged.</p>
     */
    public static double offsetFromVanillaFloor(World world, double vanillaBoundary) {
        return usesExtendedHeight(world) ? vanillaBoundary + activeRange.minimumY
                : vanillaBoundary;
    }

    /**
     * Converts a world Y coordinate to a storageArrays index.
     * Section 0 covers [minY, minY+15].
     */
    public static int sectionIndex(int worldY) {
        return (worldY - activeRange.minimumY) >> 4;
    }

    /** Converts an absolute section Y coordinate to a storageArrays index. */
    public static int sectionIndexFromSectionY(int sectionY) {
        return sectionY - activeRange.minimumSection;
    }

    /** Returns the Y base (bottom of section) for a given storageArrays index. */
    public static int sectionYBase(int sectionIdx) {
        return sectionIdx * 16 + activeRange.minimumY;
    }

    /** Returns whether both bounds match one atomic snapshot of the active range. */
    public static boolean isActiveRange(int minY, int maxY) {
        Range range = activeRange;
        return range.minimumY == minY && range.maximumY == maxY;
    }

    /** Applies the local configuration and records it as the disconnect fallback. */
    public static synchronized void configureLocalRange(int minY, int maxY) {
        validateRange(minY, maxY);
        Range range = new Range(minY, maxY);
        requireMutableOrSame(range, "configure the local world height");
        configuredRange = range;
        activeRange = range;
    }

    /** Applies the authoritative range received from a multiplayer server. */
    public static synchronized void applyServerRange(int minY, int maxY) {
        validateRange(minY, maxY);
        Range range = new Range(minY, maxY);
        requireMutableOrSame(range, "apply a server world height");
        activeRange = range;
    }

    /** Restores the client-side local range after leaving a server. */
    public static synchronized void resetToConfiguredRange() {
        requireMutableOrSame(configuredRange, "reset the local world height");
        activeRange = configuredRange;
    }

    /** Locks the configured range before any world-dependent arrays can be allocated. */
    public static synchronized void freezeRange() {
        rangeFrozen = true;
    }

    private static void requireMutableOrSame(Range requested, String action) {
        Range active = activeRange;
        if (rangeFrozen && (active.minimumY != requested.minimumY
                || active.maximumY != requested.maximumY)) {
            throw new IllegalStateException("Cannot " + action + " after Cave Biomes API startup; "
                    + "the active range is " + active.minimumY + ".." + active.maximumY);
        }
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

    private static final class Range {
        private final int minimumY;
        private final int maximumY;
        private final int minimumSection;
        private final int sectionCount;

        private Range(int minimumY, int maximumY) {
            this.minimumY = minimumY;
            this.maximumY = maximumY;
            this.minimumSection = minimumY / 16;
            this.sectionCount = (maximumY - minimumY) / 16;
        }
    }
}
