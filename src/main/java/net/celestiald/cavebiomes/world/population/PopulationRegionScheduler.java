package net.celestiald.cavebiomes.world.population;

import net.celestiald.cavebiomes.api.IExtendedPopulationGenerator;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

/** Loaded-only population scheduling shared by the Chunk mixin and deterministic tests. */
public final class PopulationRegionScheduler {
    private static final ThreadLocal<Boolean> PREPARING_DETACHED_REGION =
            new ThreadLocal<Boolean>();

    private PopulationRegionScheduler() {
    }

    static int checkedRadius(int radius) {
        if (radius < 1 || radius > 8) {
            throw new IllegalArgumentException("Population radius must be in [1, 8]: " + radius);
        }
        return radius;
    }

    public static boolean populateLoadedRegions(IChunkProvider provider, IChunkGenerator generator,
            int loadedChunkX, int loadedChunkZ) {
        prepareDetachedRegion(provider, generator, loadedChunkX, loadedChunkZ);
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
                        ((ExtendedChunkPopulationAccess) (Object) chunk)
                                .cavebiomes$populate(generator);
                    }
                });
    }

    /**
     * Some pregenerators insert generated chunks directly into the provider and call
     * {@link Chunk#populate(IChunkProvider, IChunkGenerator)} before {@link Chunk#onLoad()}.
     * Their vanilla two-by-two preparation is not enough for an extended generator's symmetric
     * population region. Complete only that detached invocation's declared region; ordinary
     * loaded chunks retain the loaded-only scheduling contract above.
     */
    private static void prepareDetachedRegion(IChunkProvider provider, IChunkGenerator generator,
            int chunkX, int chunkZ) {
        if (!(generator instanceof IExtendedPopulationGenerator)
                || Boolean.TRUE.equals(PREPARING_DETACHED_REGION.get())) {
            return;
        }
        Chunk invokingChunk = provider.getLoadedChunk(chunkX, chunkZ);
        if (invokingChunk == null || invokingChunk.isLoaded()) {
            return;
        }
        int radius = checkedRadius(
                ((IExtendedPopulationGenerator) generator).getPopulationRadius());
        PREPARING_DETACHED_REGION.set(Boolean.TRUE);
        try {
            for (int offsetX = -radius; offsetX <= radius; ++offsetX) {
                for (int offsetZ = -radius; offsetZ <= radius; ++offsetZ) {
                    int neighborX = chunkX + offsetX;
                    int neighborZ = chunkZ + offsetZ;
                    if (provider.getLoadedChunk(neighborX, neighborZ) == null) {
                        provider.provideChunk(neighborX, neighborZ);
                    }
                }
            }
        } finally {
            PREPARING_DETACHED_REGION.remove();
        }
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
