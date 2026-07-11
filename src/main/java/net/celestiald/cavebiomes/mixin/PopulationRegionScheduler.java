package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.IExtendedPopulationGenerator;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

/** Loaded-only population scheduling shared by the Chunk mixin and deterministic tests. */
final class PopulationRegionScheduler {
    private PopulationRegionScheduler() {
    }

    static int checkedRadius(int radius) {
        if (radius < 1 || radius > 8) {
            throw new IllegalArgumentException("Population radius must be in [1, 8]: " + radius);
        }
        return radius;
    }

    static boolean populateLoadedRegions(IChunkProvider provider, IChunkGenerator generator,
            int loadedChunkX, int loadedChunkZ) {
        return populateLoadedRegions(loadedChunkX, loadedChunkZ, generator,
                new LoadedChunkAccess<Chunk>() {
                    @Override
                    public Chunk getLoadedChunk(int chunkX, int chunkZ) {
                        return provider.getLoadedChunk(chunkX, chunkZ);
                    }

                    @Override
                    public boolean isTerrainPopulated(Chunk chunk) {
                        return chunk.isTerrainPopulated();
                    }

                    @Override
                    public void populate(Chunk chunk) {
                        ((MixinChunkPopulationInvoker) (Object) chunk)
                                .cavebiomes$populate(generator);
                    }
                });
    }

    static <T> boolean populateLoadedRegions(int loadedChunkX, int loadedChunkZ,
            Object generator, LoadedChunkAccess<T> chunks) {
        if (!(generator instanceof IExtendedPopulationGenerator)) {
            return false;
        }
        int radius = checkedRadius(
                ((IExtendedPopulationGenerator) generator).getPopulationRadius());
        for (int candidateX = loadedChunkX - radius;
                candidateX <= loadedChunkX + radius; ++candidateX) {
            for (int candidateZ = loadedChunkZ - radius;
                    candidateZ <= loadedChunkZ + radius; ++candidateZ) {
                T candidate = chunks.getLoadedChunk(candidateX, candidateZ);
                if (candidate != null && !chunks.isTerrainPopulated(candidate)
                        && isPopulationRegionLoaded(chunks, candidateX, candidateZ, radius)) {
                    chunks.populate(candidate);
                }
            }
        }
        return true;
    }

    private static <T> boolean isPopulationRegionLoaded(LoadedChunkAccess<T> chunks,
            int centerX, int centerZ, int radius) {
        for (int offsetX = -radius; offsetX <= radius; ++offsetX) {
            for (int offsetZ = -radius; offsetZ <= radius; ++offsetZ) {
                if (chunks.getLoadedChunk(centerX + offsetX, centerZ + offsetZ) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    interface LoadedChunkAccess<T> {
        T getLoadedChunk(int chunkX, int chunkZ);

        boolean isTerrainPopulated(T chunk);

        void populate(T chunk);
    }
}
