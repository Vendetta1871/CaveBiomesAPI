package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.World;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NaturalSpawnHeightMixinContractTest {

    private static final String CHUNK_HEIGHT =
            "Lnet/minecraft/world/chunk/Chunk;getHeight(Lnet/minecraft/util/math/BlockPos;)I";
    private static final String NEXT_INT = "Ljava/util/Random;nextInt(I)I";

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void overworldSelectionIncludesEveryYFromMinimumToVanillaUpperBound() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        RecordingRandom minimum = new RecordingRandom(0);
        assertEquals(-64, sample(minimum, 80, 0));
        assertEquals(144, minimum.lastBound);

        RecordingRandom maximum = new RecordingRandom(143);
        assertEquals(79, sample(maximum, 80, 0));
        assertEquals(144, maximum.lastBound);
    }

    @Test
    public void nonOverworldDimensionsRetainVanillaSelection() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        for (int dimension : new int[]{-1, 1, 7}) {
            RecordingRandom minimum = new RecordingRandom(0);
            assertEquals(0, sample(minimum, 80, dimension));
            assertEquals(80, minimum.lastBound);

            RecordingRandom maximum = new RecordingRandom(79);
            assertEquals(79, sample(maximum, 80, dimension));
            assertEquals(80, maximum.lastBound);
        }
    }

    @Test
    public void vanillaRangePreservesTheExactRandomContract() {
        WorldHeightAPI.configureLocalRange(0, 256);

        for (int dimension : new int[]{-1, 0, 1, 7}) {
            RecordingRandom random = new RecordingRandom(31);
            assertEquals(31, sample(random, 80, dimension));
            assertEquals(80, random.lastBound);
            assertEquals(1, random.calls);
        }
    }

    @Test
    public void redirectTargetsOnlyThePostHeightSpawnYDraw() throws Exception {
        Method method = MixinWorldEntitySpawner.class.getDeclaredMethod(
                "cavebiomes$selectSpawnY", Random.class, int.class,
                net.minecraft.world.World.class, int.class, int.class);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));

        Redirect redirect = method.getAnnotation(Redirect.class);
        assertArrayEquals(new String[]{"getRandomChunkPosition"}, redirect.method());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        assertAt(redirect.at(), "INVOKE", NEXT_INT, 0);
        assertFalse(redirect.at().remap());

        Slice slice = redirect.slice();
        assertAt(slice.from(), "INVOKE", CHUNK_HEIGHT, -1);
        assertAt(slice.to(), "TAIL", "", -1);
    }

    @Test
    public void naturalSpawnMixinIsRegisteredOnBothLogicalSides() throws IOException {
        String config = readResource("/mixins.cavebiomes.json");
        int mixin = config.indexOf("\"MixinWorldEntitySpawner\"");
        assertTrue(mixin >= 0);
        assertTrue(mixin < config.indexOf("\"client\""));
    }

    private static int sample(Random random, int vanillaUpperBound, int dimension) {
        try {
            Method method = MixinWorldEntitySpawner.class.getDeclaredMethod(
                    "cavebiomes$sampleSpawnY", Random.class, int.class, World.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Integer) method.invoke(null, random, vanillaUpperBound,
                    TestWorlds.forDimension(dimension));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertAt(At at, String value, String target, int ordinal) {
        assertEquals(value, at.value());
        assertEquals(target, at.target());
        assertEquals(ordinal, at.ordinal());
    }

    private static String readResource(String path) throws IOException {
        InputStream input = NaturalSpawnHeightMixinContractTest.class.getResourceAsStream(path);
        if (input == null) {
            throw new AssertionError("Missing resource " + path);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private static final class RecordingRandom extends Random {
        private final int result;
        private int lastBound;
        private int calls;

        private RecordingRandom(int result) {
            this.result = result;
        }

        @Override
        public int nextInt(int bound) {
            this.lastBound = bound;
            ++this.calls;
            if (this.result < 0 || this.result >= bound) {
                throw new AssertionError("Result " + this.result + " outside bound " + bound);
            }
            return this.result;
        }
    }
}
