package net.celestiald.cavebiomes.api;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BiomeLayerAPIContractTest {

    @After
    public void clearProviders() {
        BiomeLayerAPI.clear();
    }

    @Test
    public void chainsLegacyThenWorldAwareProviders() {
        AtomicInteger sequence = new AtomicInteger();
        BiomeLayerAPI.register((IVerticalBiomeProvider)
                (x, y, z, base) -> {
                    assertEquals(0, sequence.getAndIncrement());
                    return base;
                });
        BiomeLayerAPI.register((IWorldVerticalBiomeProvider)
                (world, x, y, z, base) -> {
                    assertEquals(1, sequence.getAndIncrement());
                    return base;
                });

        assertTrue(BiomeLayerAPI.hasProviders());
        assertNull(BiomeLayerAPI.resolve(null, 0, -40, 0, null));
        assertEquals(2, sequence.get());
    }
}
