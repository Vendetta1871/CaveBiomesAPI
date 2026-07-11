package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
            int storageIndex = invokeInt(MixinChunk.class,
                    "cavebiomes$entityStorageIndex", new Class<?>[]{int.class, int.class},
                    sectionY, sectionCount);
            assertEquals(sectionY + 4, storageIndex);
            assertEquals(sectionY, invokeInt(MixinChunk.class,
                    "cavebiomes$trackedEntitySectionY", new Class<?>[]{int.class}, storageIndex));
        }

        assertEquals(0, invokeInt(MixinChunk.class,
                "cavebiomes$entityStorageIndex", new Class<?>[]{int.class, int.class},
                -5, sectionCount));
        assertEquals(23, invokeInt(MixinChunk.class,
                "cavebiomes$entityStorageIndex", new Class<?>[]{int.class, int.class},
                20, sectionCount));
        assertNotNull(MixinChunk.class.getDeclaredMethod(
                "removeEntityAtIndex", Entity.class, int.class));
    }

    @Test
    public void directChunkWritesRejectCoordinatesOutsideTheFiniteRange()
            throws ReflectiveOperationException {
        WorldHeightAPI.configureLocalRange(-64, 320);
        assertFalse(invokeBoolean(MixinChunk.class, "cavebiomes$isBlockYInRange", -65));
        assertTrue(invokeBoolean(MixinChunk.class, "cavebiomes$isBlockYInRange", -64));
        assertTrue(invokeBoolean(MixinChunk.class, "cavebiomes$isBlockYInRange", 319));
        assertFalse(invokeBoolean(MixinChunk.class, "cavebiomes$isBlockYInRange", 320));
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

    private static int normalizedTileSection(int worldY) throws ReflectiveOperationException {
        return invokeInt(MixinSPacketChunkData.class,
                "cavebiomes$normalizedTileEntityY", new Class<?>[]{int.class}, worldY) >> 4;
    }

    private static boolean invokeBoolean(Class<?> owner, String name, int value)
            throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name, int.class);
        assertTrue(name + " must be private for Mixin 0.8", Modifier.isPrivate(method.getModifiers()));
        assertTrue(name + " must remain static", Modifier.isStatic(method.getModifiers()));
        method.setAccessible(true);
        return (Boolean) method.invoke(null, value);
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
