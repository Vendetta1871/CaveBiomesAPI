package net.celestiald.cavebiomes.world.population;

import net.minecraft.world.gen.IChunkGenerator;

/** Internal bridge for invoking a chunk's protected population method. */
public interface ExtendedChunkPopulationAccess {
    void cavebiomes$populate(IChunkGenerator generator);
}
