package net.celestiald.cavebiomes.world.population;

import net.celestiald.cavebiomes.api.IExtendedPopulationGenerator;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Loaded-only population scheduling shared by the Chunk mixin and deterministic tests. */
public final class PopulationRegionScheduler {
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

    static <T> boolean populateLoadedRegions(int loadedChunkX, int loadedChunkZ,
            Object generator, LoadedChunkAccess<T> chunks) {
        if (!(generator instanceof IExtendedPopulationGenerator)) {
            return false;
        }
        int radius = checkedRadius(
                ((IExtendedPopulationGenerator) generator).getPopulationRadius());
        ArrayDeque<Coordinate> triggers = new ArrayDeque<Coordinate>();
        triggers.add(new Coordinate(loadedChunkX, loadedChunkZ));
        while (!triggers.isEmpty()) {
            Coordinate trigger = triggers.removeFirst();
            for (Candidate<T> candidate : readyCandidates(
                    chunks, trigger.x, trigger.z, radius)) {
                if (chunks.isTerrainPopulated(candidate.chunk)
                        || !lowerPhasesPopulated(chunks,
                            candidate.x, candidate.z, radius)) {
                    continue;
                }
                chunks.populate(candidate.chunk);
                // Completing one phase may unblock an already-loaded neighboring center even
                // when that center is outside the last chunk-load scan.
                triggers.addLast(new Coordinate(candidate.x, candidate.z));
            }
        }
        return true;
    }

    private static <T> List<Candidate<T>> readyCandidates(LoadedChunkAccess<T> chunks,
            int triggerX, int triggerZ, int radius) {
        List<Candidate<T>> candidates = new ArrayList<Candidate<T>>();
        for (int candidateX = triggerX - radius;
                candidateX <= triggerX + radius; ++candidateX) {
            for (int candidateZ = triggerZ - radius;
                    candidateZ <= triggerZ + radius; ++candidateZ) {
                T candidate = chunks.getLoadedChunk(candidateX, candidateZ);
                if (candidate != null && !chunks.isTerrainPopulated(candidate)
                        && isPopulationRegionLoaded(chunks, candidateX, candidateZ, radius)) {
                    candidates.add(new Candidate<T>(candidateX, candidateZ, candidate,
                        stablePhase(candidateX, candidateZ, radius)));
                }
            }
        }
        Collections.sort(candidates, new Comparator<Candidate<T>>() {
            @Override
            public int compare(Candidate<T> left, Candidate<T> right) {
                int phase = Integer.compare(left.phase, right.phase);
                if (phase != 0) {
                    return phase;
                }
                int x = Integer.compare(left.x, right.x);
                return x != 0 ? x : Integer.compare(left.z, right.z);
            }
        });
        return candidates;
    }

    private static <T> boolean lowerPhasesPopulated(LoadedChunkAccess<T> chunks,
            int centerX, int centerZ, int radius) {
        int phase = stablePhase(centerX, centerZ, radius);
        for (int offsetX = -radius; offsetX <= radius; ++offsetX) {
            for (int offsetZ = -radius; offsetZ <= radius; ++offsetZ) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }
                int neighborX = centerX + offsetX;
                int neighborZ = centerZ + offsetZ;
                if (stablePhase(neighborX, neighborZ, radius) >= phase) {
                    continue;
                }
                T neighbor = chunks.getLoadedChunk(neighborX, neighborZ);
                if (neighbor == null || !chunks.isTerrainPopulated(neighbor)) {
                    return false;
                }
            }
        }
        return true;
    }

    static int stablePhase(int chunkX, int chunkZ, int radius) {
        // Centers with the same phase are more than two writable radii apart, so their feature
        // regions cannot intersect. The finite phase order avoids load-order races without
        // imposing an unbounded coordinate ordering on the world.
        int period = checkedRadius(radius) * 2 + 1;
        return Math.floorMod(chunkX, period) * period + Math.floorMod(chunkZ, period);
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

    private static final class Coordinate {
        private final int x;
        private final int z;

        private Coordinate(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final class Candidate<T> {
        private final int x;
        private final int z;
        private final T chunk;
        private final int phase;

        private Candidate(int x, int z, T chunk, int phase) {
            this.x = x;
            this.z = z;
            this.chunk = chunk;
            this.phase = phase;
        }
    }
}
