package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SurfaceQueryHeightMixinContractTest {

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void overworldScansReachTheConfiguredNegativeRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        assertTrue(usesExtendedRange(MixinChunk.class, 0));
        assertTrue(usesExtendedRange(MixinWorld.class, 0));
        assertEquals(-65, invokeInt(MixinChunk.class,
                "cavebiomes$emptyPrecipitationHeight", new Class<?>[0]));
        assertEquals(-65, precipitationSentinel(-1, 0));

        assertEquals(-49, invokeInt(MixinChunk.class,
                "cavebiomes$precipitationScanStart", new Class<?>[]{int.class}, -64));
        assertEquals(-48, invokeInt(MixinWorld.class,
                "cavebiomes$topSurfaceScanStart", new Class<?>[]{int.class}, -64));
    }

    @Test
    public void scanStartsRespectTheConfiguredExclusiveUpperBound() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        assertEquals(319, invokeInt(MixinChunk.class,
                "cavebiomes$precipitationScanStart", new Class<?>[]{int.class}, 304));
        assertEquals(319, invokeInt(MixinChunk.class,
                "cavebiomes$precipitationScanStart", new Class<?>[]{int.class}, 320));
        assertEquals(320, invokeInt(MixinWorld.class,
                "cavebiomes$topSurfaceScanStart", new Class<?>[]{int.class}, 304));
        assertEquals(320, invokeInt(MixinWorld.class,
                "cavebiomes$topSurfaceScanStart", new Class<?>[]{int.class}, 320));
    }

    @Test
    public void nonOverworldDimensionsAlwaysDelegateToVanilla() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        for (int dimension : new int[]{-1, 1, 7}) {
            assertFalse(usesExtendedRange(MixinChunk.class, dimension));
            assertFalse(usesExtendedRange(MixinWorld.class, dimension));
            assertEquals(-1, precipitationSentinel(-1, dimension));
        }
    }

    @Test
    public void vanillaMinimumAlwaysDelegatesToVanilla() {
        WorldHeightAPI.configureLocalRange(0, 256);

        for (int dimension : new int[]{-1, 0, 1, 7}) {
            assertFalse(usesExtendedRange(MixinChunk.class, dimension));
            assertFalse(usesExtendedRange(MixinWorld.class, dimension));
            assertEquals(-1, precipitationSentinel(-1, dimension));
        }
    }

    @Test
    public void injectionsTargetOnlyTheSurfaceQueryFamily() throws Exception {
        assertHeadInject(MixinChunk.class, "cavebiomes$getPrecipitationHeight",
                "getPrecipitationHeight");
        assertHeadInject(MixinWorld.class, "cavebiomes$getTopSolidOrLiquidBlock",
                "getTopSolidOrLiquidBlock");

        Method handler = MixinWorldServer.class.getDeclaredMethod(
                "cavebiomes$emptyPrecipitationHeight", int.class);
        ModifyConstant modify = handler.getAnnotation(ModifyConstant.class);
        assertNotNull(modify);
        assertArrayEquals(new String[]{"adjustPosToNearbyEntity"}, modify.method());
        assertEquals(1, modify.require());
        assertEquals(1, modify.allow());
        assertEquals(1, modify.constant().length);
        Constant constant = modify.constant()[0];
        assertEquals(-1, constant.intValue());
    }

    @Test
    public void serverSentinelMixinIsRegisteredOnBothLogicalSides() throws IOException {
        String config = readResource("/mixins.cavebiomes.json");
        int mixin = config.indexOf("\"MixinWorldServer\"");
        assertTrue(mixin >= 0);
        assertTrue(mixin < config.indexOf("\"client\""));
    }

    private static void assertHeadInject(Class<?> owner, String handlerName,
            String targetMethod) throws Exception {
        Method handler = owner.getDeclaredMethod(handlerName,
                BlockPos.class, CallbackInfoReturnable.class);
        Inject inject = handler.getAnnotation(Inject.class);
        assertNotNull(inject);
        assertArrayEquals(new String[]{targetMethod}, inject.method());
        assertEquals(1, inject.at().length);
        assertEquals("HEAD", inject.at()[0].value());
        assertTrue(inject.cancellable());
        assertTrue(inject.remap());
        assertEquals(1, inject.require());
        assertEquals(1, inject.allow());
    }

    private static boolean usesExtendedRange(Class<?> owner, int dimension) {
        World world = TestWorlds.forDimension(dimension);
        if (owner == MixinWorld.class) {
            try {
                Method method = MixinWorld.class.getDeclaredMethod(
                        "cavebiomes$usesExtendedSurfaceRange", World.class);
                assertTrue(Modifier.isPrivate(method.getModifiers()));
                assertTrue(Modifier.isStatic(method.getModifiers()));
                method.setAccessible(true);
                return (Boolean) method.invoke(null, world);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
        // MixinChunk folds the same guard into its precipitation handler:
        // the world-driven instance check plus the inline minY < 0 condition.
        return chunkUsesExtendedHeight(world) && WorldHeightAPI.getMinY() < 0;
    }

    private static boolean chunkUsesExtendedHeight(World world) {
        try {
            Method method = MixinChunk.class.getDeclaredMethod(
                    "cavebiomes$usesExtendedHeight");
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertFalse(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Boolean) method.invoke(TestWorlds.chunkFor(world));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int precipitationSentinel(int vanillaSentinel, int dimension) {
        try {
            Method method = MixinWorldServer.class.getDeclaredMethod(
                    "cavebiomes$precipitationSentinelForWorld",
                    int.class, WorldServer.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Integer) method.invoke(null, vanillaSentinel,
                    TestWorlds.serverForDimension(dimension));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int invokeInt(Class<?> owner, String name, Class<?>[] parameters,
            Object... arguments) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Integer) method.invoke(null, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String readResource(String path) throws IOException {
        InputStream input = SurfaceQueryHeightMixinContractTest.class.getResourceAsStream(path);
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
