package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.world.population.ExtendedChunkPopulationAccess;
import net.celestiald.cavebiomes.world.population.PopulationRegionScheduler;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import org.junit.Test;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PopulationRegionMixinContractTest {
    @Test
    public void schedulerHelperIsOutsideTheReservedMixinPackage() {
        assertEquals("net.celestiald.cavebiomes.world.population",
                PopulationRegionScheduler.class.getPackage().getName());
    }

    @Test
    public void radiusContractRejectsUnboundedPopulationRegions() throws Exception {
        Method method = PopulationRegionScheduler.class.getDeclaredMethod(
                "checkedRadius", int.class);
        method.setAccessible(true);
        assertEquals(1, method.invoke(null, 1));
        assertEquals(8, method.invoke(null, 8));
        assertRejected(method, 0);
        assertRejected(method, 9);
    }

    @Test
    public void chunkPopulationIsInterceptedBeforeVanillaReadinessChecks() throws Exception {
        Method handler = MixinChunk.class.getDeclaredMethod(
                "cavebiomes$populateLoadedRegion", IChunkProvider.class,
                IChunkGenerator.class, CallbackInfo.class);
        Inject inject = handler.getAnnotation(Inject.class);
        assertNotNull(inject);
        assertArrayEquals(new String[]{
                "populate(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/gen/IChunkGenerator;)V"
        }, inject.method());
        assertEquals("HEAD", inject.at()[0].value());
        // The hook only schedules loaded detached regions; the vanilla
        // lifecycle deliberately continues (no cancellation).
        assertFalse(inject.cancellable());
        assertEquals(1, inject.require());
        assertEquals(1, inject.allow());
    }

    @Test
    public void protectedPopulationBridgeUsesOnlyANormalPackageInterface() throws Exception {
        assertTrue(ExtendedChunkPopulationAccess.class.isAssignableFrom(MixinChunk.class));
        Method method = MixinChunk.class.getDeclaredMethod(
                "cavebiomes$populate", IChunkGenerator.class);
        assertNotNull(method.getAnnotation(Unique.class));

        Method shadow = MixinChunk.class.getDeclaredMethod(
                "populate", IChunkGenerator.class);
        assertNotNull(shadow.getAnnotation(Shadow.class));

        String config = readResource("/mixins.cavebiomes.json");
        assertFalse(config.contains("MixinChunkPopulationInvoker"));
    }

    private static void assertRejected(Method method, int radius) throws Exception {
        try {
            method.invoke(null, radius);
            fail("Expected radius " + radius + " to be rejected");
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IllegalArgumentException);
        }
    }

    private static String readResource(String path) throws IOException {
        InputStream input = PopulationRegionMixinContractTest.class.getResourceAsStream(path);
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
}
