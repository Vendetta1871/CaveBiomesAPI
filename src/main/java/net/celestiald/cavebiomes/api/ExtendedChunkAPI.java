package net.celestiald.cavebiomes.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Stable helpers for generators which fill sections outside vanilla's Y=0..255 range. */
public final class ExtendedChunkAPI {

    private ExtendedChunkAPI() {}

    /** Fails clearly when a content mod requires a different finite height contract. */
    public static void requireRange(String owner, int minimumY, int maximumY) {
        if (WorldHeightAPI.getMinY() != minimumY || WorldHeightAPI.getMaxY() != maximumY) {
            throw new IllegalStateException(owner + " requires world height " + minimumY + ".."
                    + maximumY + ", but CaveBiomesAPI is configured for "
                    + WorldHeightAPI.getMinY() + ".." + WorldHeightAPI.getMaxY());
        }
    }

    /** Returns the storage section containing {@code worldY}, or {@code null} when absent. */
    public static ExtendedBlockStorage getSection(Chunk chunk, int worldY) {
        int index = checkedSectionIndex(worldY);
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        requireCompatibleArray(sections);
        return sections[index];
    }

    /** Returns an existing section or creates one with the correct signed Y base. */
    public static ExtendedBlockStorage getOrCreateSection(Chunk chunk, int worldY,
            boolean storeSkyLight) {
        int index = checkedSectionIndex(worldY);
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        requireCompatibleArray(sections);
        ExtendedBlockStorage section = sections[index];
        if (section == Chunk.NULL_BLOCK_STORAGE) {
            section = new ExtendedBlockStorage(WorldHeightAPI.sectionYBase(index), storeSkyLight);
            sections[index] = section;
        }
        return section;
    }

    /** Direct generation-time block write without neighbor updates or recursive chunk loads. */
    public static void setBlockState(Chunk chunk, int localX, int worldY, int localZ,
            IBlockState state, boolean storeSkyLight) {
        if ((localX & ~15) != 0 || (localZ & ~15) != 0) {
            throw new IllegalArgumentException("Local coordinates must be in 0..15");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
        getOrCreateSection(chunk, worldY, storeSkyLight)
                .set(localX, worldY & 15, localZ, state);
    }

    /** Network/full-chunk section mask for the active finite range. */
    public static int fullSectionMask() {
        int count = WorldHeightAPI.getSectionCount();
        return count == Integer.SIZE ? -1 : (1 << count) - 1;
    }

    private static int checkedSectionIndex(int worldY) {
        if (worldY < WorldHeightAPI.getMinY() || worldY >= WorldHeightAPI.getMaxY()) {
            throw new IndexOutOfBoundsException("Y " + worldY + " is outside "
                    + WorldHeightAPI.getMinY() + ".." + WorldHeightAPI.getMaxY());
        }
        return WorldHeightAPI.sectionIndex(worldY);
    }

    private static void requireCompatibleArray(ExtendedBlockStorage[] sections) {
        if (sections.length != WorldHeightAPI.getSectionCount()) {
            throw new IllegalStateException("Chunk has " + sections.length
                    + " sections, expected " + WorldHeightAPI.getSectionCount());
        }
    }
}
