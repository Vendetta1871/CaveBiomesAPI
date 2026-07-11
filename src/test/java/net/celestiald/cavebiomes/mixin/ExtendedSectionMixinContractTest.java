package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

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
            int storageIndex = MixinChunk.cavebiomes$entityStorageIndex(sectionY, sectionCount);
            assertEquals(sectionY + 4, storageIndex);
            assertEquals(sectionY, MixinChunk.cavebiomes$trackedEntitySectionY(storageIndex));
        }

        assertEquals(0, MixinChunk.cavebiomes$entityStorageIndex(-5, sectionCount));
        assertEquals(23, MixinChunk.cavebiomes$entityStorageIndex(20, sectionCount));
        assertNotNull(MixinChunk.class.getDeclaredMethod(
                "removeEntityAtIndex", Entity.class, int.class));
    }

    @Test
    public void directChunkWritesRejectCoordinatesOutsideTheFiniteRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
        assertFalse(MixinChunk.cavebiomes$isBlockYInRange(-65));
        assertTrue(MixinChunk.cavebiomes$isBlockYInRange(-64));
        assertTrue(MixinChunk.cavebiomes$isBlockYInRange(319));
        assertFalse(MixinChunk.cavebiomes$isBlockYInRange(320));
    }

    @Test
    public void tileEntityYNormalizationSelectsTheSameBitsAsChunkSectionStorage()
            throws ReflectiveOperationException {
        WorldHeightAPI.configureLocalRange(-64, 320);
        for (int worldY = -64; worldY < 320; ++worldY) {
            int normalizedY = MixinSPacketChunkData.cavebiomes$normalizedTileEntityY(worldY);
            assertEquals(WorldHeightAPI.sectionIndex(worldY), normalizedY >> 4);
        }
        assertEquals(0, MixinSPacketChunkData.cavebiomes$normalizedTileEntityY(-64) >> 4);
        assertEquals(4, MixinSPacketChunkData.cavebiomes$normalizedTileEntityY(0) >> 4);
        assertEquals(16, MixinSPacketChunkData.cavebiomes$normalizedTileEntityY(192) >> 4);
        assertEquals(20, MixinSPacketChunkData.cavebiomes$normalizedTileEntityY(256) >> 4);
        assertEquals(23, MixinSPacketChunkData.cavebiomes$normalizedTileEntityY(319) >> 4);

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
}
