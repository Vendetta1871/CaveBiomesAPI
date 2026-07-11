package net.celestiald.cavebiomes.world.population;

import net.celestiald.cavebiomes.api.IExtendedPopulationGenerator;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PopulationRegionSchedulerTest {
    private static final ExtendedGenerator RADIUS_ONE = new ExtendedGenerator(1);

    @Test
    public void everyMissingNeighborBlocksTheTarget() {
        for (Coordinate missing : square(-1, 1)) {
            if (missing.x == 0 && missing.z == 0) {
                continue;
            }
            FakeChunks chunks = new FakeChunks();
            chunks.loadAll(square(-1, 1));
            chunks.unload(missing.x, missing.z);

            assertTrue(schedule(0, 0, RADIUS_ONE, chunks));
            assertFalse("missing " + missing, chunks.wasPopulated(0, 0));
        }

        FakeChunks complete = new FakeChunks();
        complete.loadAll(square(-1, 1));
        assertTrue(schedule(0, 0, RADIUS_ONE, complete));
        assertTrue(complete.wasPopulated(0, 0));
    }

    @Test
    public void westAndNorthLoadingLastReconsiderTheTarget() {
        for (Coordinate last : Arrays.asList(new Coordinate(-1, 0),
                new Coordinate(0, -1))) {
            FakeChunks chunks = new FakeChunks();
            chunks.loadAll(square(-1, 1));
            chunks.unload(last.x, last.z);
            assertTrue(schedule(0, 0, RADIUS_ONE, chunks));
            assertFalse(chunks.wasPopulated(0, 0));

            chunks.load(last.x, last.z);
            assertTrue(schedule(last.x, last.z, RADIUS_ONE, chunks));
            assertTrue("last loaded " + last, chunks.wasPopulated(0, 0));
        }
    }

    @Test
    public void loadOrderDoesNotChangeTheReadySet() {
        List<Coordinate> rowMajor = square(-2, 2);
        List<Coordinate> reverse = new ArrayList<Coordinate>(rowMajor);
        Collections.reverse(reverse);
        List<Coordinate> outsideIn = Arrays.asList(
                new Coordinate(-2, -2), new Coordinate(-1, -2), new Coordinate(0, -2),
                new Coordinate(1, -2), new Coordinate(2, -2), new Coordinate(2, -1),
                new Coordinate(2, 0), new Coordinate(2, 1), new Coordinate(2, 2),
                new Coordinate(1, 2), new Coordinate(0, 2), new Coordinate(-1, 2),
                new Coordinate(-2, 2), new Coordinate(-2, 1), new Coordinate(-2, 0),
                new Coordinate(-2, -1), new Coordinate(-1, -1), new Coordinate(0, -1),
                new Coordinate(1, -1), new Coordinate(1, 0), new Coordinate(1, 1),
                new Coordinate(0, 1), new Coordinate(-1, 1), new Coordinate(-1, 0),
                new Coordinate(0, 0));
        Set<String> expected = keys(square(-1, 1));

        for (List<Coordinate> order : Arrays.asList(rowMajor, reverse, outsideIn)) {
            FakeChunks chunks = populateInOrder(order);
            assertEquals(expected, chunks.populatedKeys());
            assertEquals("each ready chunk must populate once", expected.size(),
                    chunks.populateCalls);
        }
    }

    @Test
    public void schedulerOnlyQueriesAlreadyLoadedChunks() {
        LoadedOnlyProvider provider = new LoadedOnlyProvider();
        ExtendedChunkGenerator generator = new ExtendedChunkGenerator();

        assertTrue(PopulationRegionScheduler.populateLoadedRegions(provider, generator, 0, 0));
        assertTrue(provider.loadedLookups > 0);
        assertEquals(0, provider.provideCalls);
        assertEquals(0, generator.generationCalls);
        assertEquals(0, generator.populationCalls);
    }

    @Test
    public void nonOptInGeneratorsRemainOnTheVanillaPath() {
        LoadedOnlyProvider provider = new LoadedOnlyProvider();
        StubChunkGenerator generator = new StubChunkGenerator();

        assertFalse(PopulationRegionScheduler.populateLoadedRegions(provider, generator, 0, 0));
        assertEquals(0, provider.loadedLookups);
        assertEquals(0, provider.provideCalls);
        assertEquals(0, generator.generationCalls);
        assertEquals(0, generator.populationCalls);
    }

    private static boolean schedule(int loadedX, int loadedZ, Object generator,
            FakeChunks chunks) {
        return PopulationRegionScheduler.populateLoadedRegions(
                loadedX, loadedZ, generator, chunks);
    }

    private static FakeChunks populateInOrder(List<Coordinate> order) {
        FakeChunks chunks = new FakeChunks();
        for (Coordinate coordinate : order) {
            chunks.load(coordinate.x, coordinate.z);
            assertTrue(schedule(coordinate.x, coordinate.z, RADIUS_ONE, chunks));
        }
        return chunks;
    }

    private static List<Coordinate> square(int minimum, int maximum) {
        List<Coordinate> result = new ArrayList<Coordinate>();
        for (int x = minimum; x <= maximum; ++x) {
            for (int z = minimum; z <= maximum; ++z) {
                result.add(new Coordinate(x, z));
            }
        }
        return result;
    }

    private static Set<String> keys(List<Coordinate> coordinates) {
        Set<String> result = new LinkedHashSet<String>();
        for (Coordinate coordinate : coordinates) {
            result.add(coordinate.key());
        }
        return result;
    }

    private static final class ExtendedGenerator implements IExtendedPopulationGenerator {
        private final int radius;

        private ExtendedGenerator(int radius) {
            this.radius = radius;
        }

        @Override
        public int getPopulationRadius() {
            return radius;
        }
    }

    private static final class FakeChunks
            implements PopulationRegionScheduler.LoadedChunkAccess<FakeChunk> {
        private final Map<String, FakeChunk> loaded = new LinkedHashMap<String, FakeChunk>();
        private final Set<String> populated = new LinkedHashSet<String>();
        private int loadedLookups;
        private int populateCalls;

        private void load(int x, int z) {
            loaded.put(key(x, z), new FakeChunk(x, z));
        }

        private void loadAll(List<Coordinate> coordinates) {
            for (Coordinate coordinate : coordinates) {
                load(coordinate.x, coordinate.z);
            }
        }

        private void unload(int x, int z) {
            loaded.remove(key(x, z));
        }

        private boolean wasPopulated(int x, int z) {
            return populated.contains(key(x, z));
        }

        private Set<String> populatedKeys() {
            return new LinkedHashSet<String>(populated);
        }

        @Override
        public FakeChunk getLoadedChunk(int chunkX, int chunkZ) {
            ++loadedLookups;
            return loaded.get(key(chunkX, chunkZ));
        }

        @Override
        public boolean isTerrainPopulated(FakeChunk chunk) {
            return chunk.populated;
        }

        @Override
        public void populate(FakeChunk chunk) {
            ++populateCalls;
            chunk.populated = true;
            populated.add(key(chunk.x, chunk.z));
        }
    }

    private static final class LoadedOnlyProvider implements IChunkProvider {
        private int loadedLookups;
        private int provideCalls;

        @Override
        public Chunk getLoadedChunk(int chunkX, int chunkZ) {
            ++loadedLookups;
            return null;
        }

        @Override
        public Chunk provideChunk(int chunkX, int chunkZ) {
            ++provideCalls;
            return null;
        }

        @Override
        public boolean tick() {
            return false;
        }

        @Override
        public String makeString() {
            return "loaded-only test provider";
        }

        @Override
        public boolean isChunkGeneratedAt(int chunkX, int chunkZ) {
            return false;
        }
    }

    private static final class ExtendedChunkGenerator extends StubChunkGenerator
            implements IExtendedPopulationGenerator {
        @Override
        public int getPopulationRadius() {
            return 1;
        }
    }

    private static class StubChunkGenerator implements IChunkGenerator {
        int generationCalls;
        int populationCalls;

        @Override
        public Chunk generateChunk(int chunkX, int chunkZ) {
            ++generationCalls;
            return null;
        }

        @Override
        public void populate(int chunkX, int chunkZ) {
            ++populationCalls;
        }

        @Override
        public boolean generateStructures(Chunk chunk, int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType type,
                BlockPos position) {
            return Collections.emptyList();
        }

        @Override
        public BlockPos getNearestStructurePos(World world, String name, BlockPos position,
                boolean findUnexplored) {
            return null;
        }

        @Override
        public void recreateStructures(Chunk chunk, int chunkX, int chunkZ) {
        }

        @Override
        public boolean isInsideStructure(World world, String name, BlockPos position) {
            return false;
        }
    }

    private static final class FakeChunk {
        private final int x;
        private final int z;
        private boolean populated;

        private FakeChunk(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final class Coordinate {
        private final int x;
        private final int z;

        private Coordinate(int x, int z) {
            this.x = x;
            this.z = z;
        }

        private String key() {
            return PopulationRegionSchedulerTest.key(x, z);
        }

        @Override
        public String toString() {
            return key();
        }
    }

    private static String key(int x, int z) {
        return x + "," + z;
    }
}
