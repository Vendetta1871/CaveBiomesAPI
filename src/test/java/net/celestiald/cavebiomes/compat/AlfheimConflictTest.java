package net.celestiald.cavebiomes.compat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Table tests for the Alfheim startup gate condition. */
public class AlfheimConflictTest {

    @Test
    public void vanillaHeightToleratesAlfheim() {
        assertFalse(AlfheimConflict.conflicts(0, 256, true));
    }

    @Test
    public void deeperFloorConflicts() {
        assertTrue(AlfheimConflict.conflicts(-64, 256, true));
    }

    @Test
    public void raisedCeilingConflicts() {
        assertTrue(AlfheimConflict.conflicts(0, 320, true));
    }

    @Test
    public void defaultExtendedRangeConflicts() {
        assertTrue(AlfheimConflict.conflicts(-64, 320, true));
    }

    @Test
    public void absentAlfheimNeverConflicts() {
        assertFalse(AlfheimConflict.conflicts(-64, 320, false));
        assertFalse(AlfheimConflict.conflicts(0, 256, false));
    }
}
