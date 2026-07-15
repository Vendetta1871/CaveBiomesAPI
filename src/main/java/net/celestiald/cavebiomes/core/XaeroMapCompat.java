package net.celestiald.cavebiomes.core;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Shared extended-height section scan for Xaero's map and minimap. */
public final class XaeroMapCompat {
    private XaeroMapCompat() {}

    public static int getSectionBasedHeight(Chunk chunk, int startY) {
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        if (sections.length == 0) return -1;

        int start = WorldHeightAPI.sectionIndex(startY);
        start = Math.max(0, Math.min(start, sections.length - 1));
        int height = -1;
        for (int index = start; index < sections.length; index++) {
            if (sections[index] != Chunk.NULL_BLOCK_STORAGE) {
                height = WorldHeightAPI.sectionYBase(index) + 15;
            }
        }
        if (height != -1) return height;

        for (int index = start - 1; index >= 0; index--) {
            if (sections[index] != Chunk.NULL_BLOCK_STORAGE) {
                return WorldHeightAPI.sectionYBase(index) + 15;
            }
        }
        return -1;
    }
}
