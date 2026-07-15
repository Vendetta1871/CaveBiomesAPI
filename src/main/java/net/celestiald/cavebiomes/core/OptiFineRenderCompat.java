package net.celestiald.cavebiomes.core;

/** Runtime helpers used by narrowly scoped OptiFine bytecode patches. */
public final class OptiFineRenderCompat {
    private OptiFineRenderCompat() {}

    public static int clampSectionIndex(int index, int sectionCount) {
        if (sectionCount <= 1) {
            return 0;
        }
        return Math.max(0, Math.min(index, sectionCount - 1));
    }
}
