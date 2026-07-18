package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ExtendedSectionMixinContractTest {

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void entityTrackingKeepsSignedSectionCoordinatesAndNormalizesArrayAccess()
            throws ReflectiveOperationException {
        WorldHeightAPI.configureLocalRange(-64, 320);
        int sectionCount = WorldHeightAPI.getSectionCount();

        for (int sectionY = -4; sectionY <= 19; ++sectionY) {
            int storageIndex = invokeChunkInt(
                    "cavebiomes$entityStorageIndex", sectionY, sectionCount);
            assertEquals(sectionY + 4, storageIndex);
            assertEquals(sectionY, invokeInt(MixinChunk.class,
                    "cavebiomes$trackedEntitySectionY", new Class<?>[]{int.class}, storageIndex));
        }

        assertEquals(0, invokeChunkInt(
                "cavebiomes$entityStorageIndex", -5, sectionCount));
        assertEquals(23, invokeChunkInt(
                "cavebiomes$entityStorageIndex", 20, sectionCount));
        assertNotNull(MixinChunk.class.getDeclaredMethod(
                "removeEntityAtIndex", Entity.class, int.class));
    }

    @Test
    public void directChunkWritesRejectCoordinatesOutsideTheFiniteRange()
            throws ReflectiveOperationException {
        WorldHeightAPI.configureLocalRange(-64, 320);
        assertFalse(invokeChunkBoolean("cavebiomes$isBlockYInRange", -65));
        assertTrue(invokeChunkBoolean("cavebiomes$isBlockYInRange", -64));
        assertTrue(invokeChunkBoolean("cavebiomes$isBlockYInRange", 319));
        assertFalse(invokeChunkBoolean("cavebiomes$isBlockYInRange", 320));
    }

    @Test
    public void tileEntityYNormalizationSelectsTheSameBitsAsChunkSectionStorage()
            throws ReflectiveOperationException {
        WorldHeightAPI.configureLocalRange(-64, 320);
        for (int worldY = -64; worldY < 320; ++worldY) {
            int normalizedY = invokeInt(MixinSPacketChunkData.class,
                    "cavebiomes$normalizedTileEntityY", new Class<?>[]{int.class}, worldY);
            assertEquals(WorldHeightAPI.sectionIndex(worldY), normalizedY >> 4);
        }
        assertEquals(0, normalizedTileSection(-64));
        assertEquals(4, normalizedTileSection(0));
        assertEquals(16, normalizedTileSection(192));
        assertEquals(20, normalizedTileSection(256));
        assertEquals(23, normalizedTileSection(319));

        Method handler = MixinSPacketChunkData.class.getDeclaredMethod(
                "cavebiomes$normalizeTileEntitySectionY", BlockPos.class);
        Redirect redirect = handler.getAnnotation(Redirect.class);
        assertNotNull(redirect);
        assertArrayEquals(new String[]{"<init>(Lnet/minecraft/world/chunk/Chunk;I)V"},
                redirect.method());
        assertEquals("INVOKE", redirect.at().value());
        assertEquals("Lnet/minecraft/util/math/BlockPos;getY()I", redirect.at().target());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
    }

    @Test
    public void relightQueueCoversEveryConfiguredSectionColumnExactlyOnce()
            throws ReflectiveOperationException {
        WorldHeightAPI.configureLocalRange(-64, 320);
        int sectionCount = WorldHeightAPI.getSectionCount();
        int queueLimit = invokeInt(MixinChunk.class, "cavebiomes$relightQueueLimit",
                new Class<?>[]{int.class}, sectionCount);
        assertEquals(24 * 16 * 16, queueLimit);

        Set<Integer> visited = new HashSet<>();
        for (int queueIndex = 0; queueIndex < queueLimit; ++queueIndex) {
            int section = invokeInt(MixinChunk.class, "cavebiomes$relightSectionIndex",
                    new Class<?>[]{int.class, int.class}, queueIndex, sectionCount);
            int localX = invokeInt(MixinChunk.class, "cavebiomes$relightLocalX",
                    new Class<?>[]{int.class, int.class}, queueIndex, sectionCount);
            int localZ = invokeInt(MixinChunk.class, "cavebiomes$relightLocalZ",
                    new Class<?>[]{int.class, int.class}, queueIndex, sectionCount);

            assertTrue(section >= 0 && section < sectionCount);
            assertTrue(localX >= 0 && localX < 16);
            assertTrue(localZ >= 0 && localZ < 16);
            assertTrue(visited.add((section << 8) | (localX << 4) | localZ));
        }
        assertEquals(queueLimit, visited.size());

        assertRelightCoordinate(0, sectionCount, 0, 0, 0);
        assertRelightCoordinate(23, sectionCount, 23, 0, 0);
        assertRelightCoordinate(24, sectionCount, 0, 1, 0);
        assertRelightCoordinate(sectionCount * 16, sectionCount, 0, 0, 1);
        assertRelightCoordinate(queueLimit - 1, sectionCount, 23, 15, 15);
    }

    @Test
    public void relightWorldYAndLightScanUseConfiguredMinimum()
            throws ReflectiveOperationException {
        WorldHeightAPI.configureLocalRange(-64, 320);
        assertEquals(-64, invokeInt(MixinChunk.class, "cavebiomes$relightWorldY",
                new Class<?>[]{int.class, int.class}, 0, 0));
        assertEquals(-49, invokeInt(MixinChunk.class, "cavebiomes$relightWorldY",
                new Class<?>[]{int.class, int.class}, 0, 15));
        assertEquals(0, invokeInt(MixinChunk.class, "cavebiomes$relightWorldY",
                new Class<?>[]{int.class, int.class}, 4, 0));
        assertEquals(319, invokeInt(MixinChunk.class, "cavebiomes$relightWorldY",
                new Class<?>[]{int.class, int.class}, 23, 15));

        assertFalse(invokeChunkBoolean("cavebiomes$isAboveMinimumBuildHeight", -64));
        assertTrue(invokeChunkBoolean("cavebiomes$isAboveMinimumBuildHeight", -63));

        WorldHeightAPI.configureLocalRange(0, 256);
        assertFalse(invokeChunkBoolean("cavebiomes$isAboveMinimumBuildHeight", 0));
        assertTrue(invokeChunkBoolean("cavebiomes$isAboveMinimumBuildHeight", 1));
        assertEquals(4096, invokeInt(MixinChunk.class, "cavebiomes$relightQueueLimit",
                new Class<?>[]{int.class}, 16));
    }

    private static void assertRelightCoordinate(int queueIndex, int sectionCount,
            int expectedSection, int expectedX, int expectedZ)
            throws ReflectiveOperationException {
        assertEquals(expectedSection, invokeInt(MixinChunk.class,
                "cavebiomes$relightSectionIndex", new Class<?>[]{int.class, int.class},
                queueIndex, sectionCount));
        assertEquals(expectedX, invokeInt(MixinChunk.class,
                "cavebiomes$relightLocalX", new Class<?>[]{int.class, int.class},
                queueIndex, sectionCount));
        assertEquals(expectedZ, invokeInt(MixinChunk.class,
                "cavebiomes$relightLocalZ", new Class<?>[]{int.class, int.class},
                queueIndex, sectionCount));
    }

    private static int normalizedTileSection(int worldY) throws ReflectiveOperationException {
        return invokeInt(MixinSPacketChunkData.class,
                "cavebiomes$normalizedTileEntityY", new Class<?>[]{int.class}, worldY) >> 4;
    }

    private static boolean invokeChunkBoolean(String name, int value)
            throws ReflectiveOperationException {
        Method method = MixinChunk.class.getDeclaredMethod(name, int.class);
        assertTrue(name + " must be private for Mixin 0.8",
                Modifier.isPrivate(method.getModifiers()));
        assertFalse(name + " is a world-driven per-chunk instance check",
                Modifier.isStatic(method.getModifiers()));
        method.setAccessible(true);
        return (Boolean) method.invoke(
                TestWorlds.chunkFor(TestWorlds.extendedOverworld()), value);
    }

    private static int invokeChunkInt(String name, int first, int second)
            throws ReflectiveOperationException {
        Method method = MixinChunk.class.getDeclaredMethod(name, int.class, int.class);
        assertTrue(name + " must be private for Mixin 0.8",
                Modifier.isPrivate(method.getModifiers()));
        assertFalse(name + " is a world-driven per-chunk instance check",
                Modifier.isStatic(method.getModifiers()));
        method.setAccessible(true);
        return (Integer) method.invoke(
                TestWorlds.chunkFor(TestWorlds.extendedOverworld()), first, second);
    }

    private static int invokeInt(Class<?> owner, String name, Class<?>[] parameterTypes,
            Object... arguments) throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        assertTrue(name + " must be private for Mixin 0.8", Modifier.isPrivate(method.getModifiers()));
        assertTrue(name + " must remain static", Modifier.isStatic(method.getModifiers()));
        method.setAccessible(true);
        return (Integer) method.invoke(null, arguments);
    }
}
