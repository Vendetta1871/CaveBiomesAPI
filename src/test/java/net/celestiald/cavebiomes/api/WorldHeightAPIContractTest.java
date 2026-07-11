package net.celestiald.cavebiomes.api;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WorldHeightAPIContractTest {

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void exposesSignedSectionCoordinates() {
        WorldHeightAPI.configureLocalRange(-64, 320);
        assertEquals(24, WorldHeightAPI.getSectionCount());
        assertEquals(-4, WorldHeightAPI.getMinSection());
        assertEquals(0, WorldHeightAPI.sectionIndex(-64));
        assertEquals(4, WorldHeightAPI.sectionIndex(0));
        assertEquals(23, WorldHeightAPI.sectionIndex(319));
        assertEquals(-64, WorldHeightAPI.sectionYBase(0));
        assertEquals(304, WorldHeightAPI.sectionYBase(23));
        assertEquals(0x00ff_ffff, ExtendedChunkAPI.fullSectionMask());
    }

    @Test
    public void serverRangeIsTemporary() {
        WorldHeightAPI.configureLocalRange(0, 256);
        WorldHeightAPI.applyServerRange(-64, 320);
        assertEquals(-64, WorldHeightAPI.getMinY());
        assertEquals(320, WorldHeightAPI.getMaxY());
        WorldHeightAPI.resetToConfiguredRange();
        assertEquals(0, WorldHeightAPI.getMinY());
        assertEquals(256, WorldHeightAPI.getMaxY());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnalignedRanges() {
        WorldHeightAPI.configureLocalRange(-63, 320);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRangesWiderThanThePacketMask() {
        WorldHeightAPI.configureLocalRange(-256, 512);
    }
}
