package net.celestiald.cavebiomes.mixin;

import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WeatherHeightMixinContractTest {

    @Test
    public void snowAndFreezeReplaceOnlyTheExtendedRangePath() throws Exception {
        assertHeadCancellation("cavebiomes$canSnowAtBody");
        assertHeadCancellation("cavebiomes$canBlockFreezeBody");
    }

    private static void assertHeadCancellation(String name) throws Exception {
        Method handler = MixinWorld.class.getDeclaredMethod(
                name, BlockPos.class, boolean.class, CallbackInfoReturnable.class);
        Inject inject = handler.getAnnotation(Inject.class);
        assertNotNull(inject);
        assertEquals(1, inject.at().length);
        assertEquals("HEAD", inject.at()[0].value());
        assertTrue(inject.cancellable());
        assertFalse(inject.remap());
        assertArrayEquals(new String[]{name.contains("Snow")
                        ? "canSnowAtBody"
                        : "canBlockFreezeBody"},
                inject.method());
    }
}
