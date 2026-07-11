package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityVoidHeightMixinContractTest {

    private static final double VANILLA_THRESHOLD = -64.0D;

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void overworldKeepsTheVanillaSixtyFourBlockSafetyMargin() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        for (Class<?> mixin : voidMixins()) {
            assertEquals(-128.0D, threshold(mixin, 0), 0.0D);
            assertFalse(isOutOfWorld(mixin, -128.0D, 0));
            assertTrue(isOutOfWorld(mixin, -128.0001D, 0));
        }

        WorldHeightAPI.configureLocalRange(-256, 256);
        for (Class<?> mixin : voidMixins()) {
            assertEquals(-320.0D, threshold(mixin, 0), 0.0D);
        }
    }

    @Test
    public void nonOverworldDimensionsRetainTheVanillaThreshold() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        for (Class<?> mixin : voidMixins()) {
            for (int dimension : new int[]{-1, 1, 7}) {
                assertEquals(VANILLA_THRESHOLD, threshold(mixin, dimension), 0.0D);
                assertFalse(isOutOfWorld(mixin, -64.0D, dimension));
                assertTrue(isOutOfWorld(mixin, -64.0001D, dimension));
            }
        }
    }

    @Test
    public void vanillaMinimumPreservesEveryDimensionExactly() {
        WorldHeightAPI.configureLocalRange(0, 256);

        for (Class<?> mixin : voidMixins()) {
            for (int dimension : new int[]{-1, 0, 1, 7}) {
                double actual = threshold(mixin, dimension);
                assertEquals(Double.doubleToLongBits(VANILLA_THRESHOLD),
                        Double.doubleToLongBits(actual));
            }
        }
    }

    @Test
    public void baseEntityAndMinecartPatchOnlyTheirOwnVoidConstant() throws Exception {
        assertVoidConstant(MixinEntity.class, "onEntityUpdate");
        assertVoidConstant(MixinEntityMinecart.class, "onUpdate");
    }

    @Test
    public void bothVoidMixinsAreRegisteredOnBothLogicalSides() throws IOException {
        String config = readResource("/mixins.cavebiomes.json");
        int clientSection = config.indexOf("\"client\"");
        for (String mixin : new String[]{"MixinEntity", "MixinEntityMinecart"}) {
            int index = config.indexOf("\"" + mixin + "\"");
            assertTrue(index >= 0);
            assertTrue(index < clientSection);
        }
    }

    private static void assertVoidConstant(Class<?> owner, String targetMethod) throws Exception {
        Method handler = owner.getDeclaredMethod(
                "cavebiomes$voidThreshold", double.class);
        assertTrue(Modifier.isPrivate(handler.getModifiers()));
        assertFalse(Modifier.isStatic(handler.getModifiers()));

        ModifyConstant modify = handler.getAnnotation(ModifyConstant.class);
        assertArrayEquals(new String[]{targetMethod}, modify.method());
        assertEquals(1, modify.require());
        assertEquals(1, modify.allow());
        assertEquals(1, modify.constant().length);
        Constant constant = modify.constant()[0];
        assertEquals(VANILLA_THRESHOLD, constant.doubleValue(), 0.0D);
    }

    private static boolean isOutOfWorld(Class<?> mixin, double entityY, int dimension) {
        return entityY < threshold(mixin, dimension);
    }

    private static double threshold(Class<?> owner, int dimension) {
        try {
            Method method = owner.getDeclaredMethod(
                    "cavebiomes$thresholdForDimension", double.class, int.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Double) method.invoke(null, VANILLA_THRESHOLD, dimension);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Class<?>[] voidMixins() {
        return new Class<?>[]{MixinEntity.class, MixinEntityMinecart.class};
    }

    private static String readResource(String path) throws IOException {
        InputStream input = EntityVoidHeightMixinContractTest.class.getResourceAsStream(path);
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
