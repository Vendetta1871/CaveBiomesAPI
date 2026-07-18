package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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

public class LivingTeleportHeightMixinContractTest {

    private static final String BLOCK_POS_Y =
            "Lnet/minecraft/util/math/BlockPos;getY()I";

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void overworldLandingScanReachesTheConfiguredMinimum() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        assertFalse(canDescend(-64, 0));
        assertTrue(canDescend(-63, 0));
        assertTrue(canDescend(-20, 0));
        assertEquals(0, relativeY(-64, 0));
        assertEquals(1, relativeY(-63, 0));
        assertEquals(44, relativeY(-20, 0));
    }

    @Test
    public void nonOverworldDimensionsRetainTheVanillaFloor() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        for (int dimension : new int[]{-1, 1, 7}) {
            assertEquals(-20, relativeY(-20, dimension));
            assertEquals(0, relativeY(0, dimension));
            assertEquals(1, relativeY(1, dimension));
            assertFalse(canDescend(-20, dimension));
            assertFalse(canDescend(0, dimension));
            assertTrue(canDescend(1, dimension));
        }
    }

    @Test
    public void vanillaMinimumPreservesEveryDimensionExactly() {
        WorldHeightAPI.configureLocalRange(0, 256);

        for (int dimension : new int[]{-1, 0, 1, 7}) {
            for (int y : new int[]{-1, 0, 1, 255, 256}) {
                assertEquals(y, relativeY(y, dimension));
            }
        }
    }

    @Test
    public void redirectTargetsOnlyTheTeleportLandingLoop() throws Exception {
        Method handler = MixinEntityLivingBase.class.getDeclaredMethod(
                "cavebiomes$teleportLandingFloor", BlockPos.class);
        assertTrue(Modifier.isPrivate(handler.getModifiers()));
        assertFalse(Modifier.isStatic(handler.getModifiers()));

        Redirect redirect = handler.getAnnotation(Redirect.class);
        assertArrayEquals(new String[]{"attemptTeleport"}, redirect.method());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        At at = redirect.at();
        assertEquals("INVOKE", at.value());
        assertEquals(BLOCK_POS_Y, at.target());
        assertEquals(0, at.ordinal());
        assertTrue(at.remap());
    }

    @Test
    public void teleportMixinIsRegisteredOnBothLogicalSides() throws IOException {
        String config = readResource("/mixins.cavebiomes.json");
        int mixin = config.indexOf("\"MixinEntityLivingBase\"");
        assertTrue(mixin >= 0);
        assertTrue(mixin < config.indexOf("\"client\""));
    }

    private static boolean canDescend(int worldY, int dimension) {
        return relativeY(worldY, dimension) > 0;
    }

    private static int relativeY(int worldY, int dimension) {
        try {
            Method method = MixinEntityLivingBase.class.getDeclaredMethod(
                    "cavebiomes$relativeToTeleportFloor", int.class, World.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Integer) method.invoke(null, worldY,
                    TestWorlds.forDimension(dimension));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String readResource(String path) throws IOException {
        InputStream input = LivingTeleportHeightMixinContractTest.class.getResourceAsStream(path);
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
