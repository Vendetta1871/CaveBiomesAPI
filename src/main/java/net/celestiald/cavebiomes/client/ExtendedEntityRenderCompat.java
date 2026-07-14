package net.celestiald.cavebiomes.client;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.world.chunk.Chunk;

/** Safe entity-section access for renderers that bypass Chunk's query methods. */
public final class ExtendedEntityRenderCompat {
    private static final ClassInheritanceMultiMap<Entity> EMPTY =
            new ClassInheritanceMultiMap<>(Entity.class);

    private ExtendedEntityRenderCompat() {}

    public static ClassInheritanceMultiMap<Entity> entityListAtBlockY(
            Chunk chunk, int worldY) {
        ClassInheritanceMultiMap<Entity>[] lists = chunk.getEntityLists();
        int index = WorldHeightAPI.sectionIndex(worldY);
        if (index < 0 || index >= lists.length || lists[index] == null) {
            return EMPTY;
        }
        return lists[index];
    }
}
