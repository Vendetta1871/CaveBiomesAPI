package net.celestiald.cavebiomes.core;

import net.celestiald.cavebiomes.api.WorldHeightAPI;

/** Small runtime helpers used by the optional Dynmap transformer. */
public final class DynmapCompat {
    private DynmapCompat() {}

    /** Returns Dynmap's trailing empty-section sentinel for an out-of-range block Y. */
    public static int safeSectionIndex(int worldY) {
        return safeIndex(WorldHeightAPI.sectionIndex(worldY));
    }

    /** Returns Dynmap's trailing empty-section sentinel for an out-of-range section Y. */
    public static int safeSectionIndexFromSectionY(int sectionY) {
        return safeIndex(WorldHeightAPI.sectionIndexFromSectionY(sectionY));
    }

    private static int safeIndex(int index) {
        int sectionCount = WorldHeightAPI.getSectionCount();
        return index >= 0 && index < sectionCount ? index : sectionCount;
    }
}
