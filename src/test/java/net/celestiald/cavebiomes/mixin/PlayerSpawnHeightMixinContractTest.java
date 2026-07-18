package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.player.EntityPlayerMP;
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

public class PlayerSpawnHeightMixinContractTest {

    private static final String PLAYER_Y =
            "Lnet/minecraft/entity/player/EntityPlayerMP;posY:D";

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void overworldInitialSpawnLiftStopsAtConfiguredTopBlock() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        assertTrue(initialSpawnLiftContinues(318.0D, 0));
        assertFalse(initialSpawnLiftContinues(319.0D, 0));
        assertFalse(initialSpawnLiftContinues(320.0D, 0));
    }

    @Test
    public void overworldRespawnLiftCanReachConfiguredMaximum() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        assertTrue(respawnLiftContinues(-64.0D, 0));
        assertTrue(respawnLiftContinues(255.0D, 0));
        assertTrue(respawnLiftContinues(319.0D, 0));
        assertFalse(respawnLiftContinues(320.0D, 0));
    }

    @Test
    public void nonOverworldDimensionsRetainVanillaCollisionCaps() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        for (int dimension : new int[]{-1, 1, 7}) {
            assertTrue(initialSpawnLiftContinues(254.0D, dimension));
            assertFalse(initialSpawnLiftContinues(255.0D, dimension));
            assertTrue(respawnLiftContinues(255.0D, dimension));
            assertFalse(respawnLiftContinues(256.0D, dimension));
        }
    }

    @Test
    public void vanillaRangeIsAnExactNoOpInEveryDimension() {
        WorldHeightAPI.configureLocalRange(0, 256);

        for (Class<?> mixin : new Class<?>[]{MixinEntityPlayerMP.class, MixinPlayerList.class}) {
            for (int dimension : new int[]{-1, 0, 1, 7}) {
                for (double y : new double[]{-64.0D, -1.0D, 0.0D, 254.0D, 255.0D, 256.0D}) {
                    assertEquals(y, relativeY(mixin, y, dimension), 0.0D);
                }
            }
        }
    }

    @Test
    public void redirectsTargetOnlyTheTwoCollisionLoopComparisons() throws Exception {
        assertRedirect(MixinEntityPlayerMP.class,
                "cavebiomes$initialSpawnCollisionCeiling", "<init>");
        assertRedirect(MixinPlayerList.class,
                "cavebiomes$respawnCollisionCeiling", "recreatePlayerEntity");
    }

    @Test
    public void playerSpawnMixinsAreRegisteredOnBothLogicalSides() throws IOException {
        String config = readResource("/mixins.cavebiomes.json");
        int client = config.indexOf("\"client\"");

        int player = config.indexOf("\"MixinEntityPlayerMP\"");
        int playerList = config.indexOf("\"MixinPlayerList\"");
        assertTrue(player >= 0);
        assertTrue(playerList >= 0);
        assertTrue(player < client);
        assertTrue(playerList < client);
    }

    private static boolean initialSpawnLiftContinues(double worldY, int dimension) {
        return relativeY(MixinEntityPlayerMP.class, worldY, dimension) < 255.0D;
    }

    private static boolean respawnLiftContinues(double worldY, int dimension) {
        return relativeY(MixinPlayerList.class, worldY, dimension) < 256.0D;
    }

    private static double relativeY(Class<?> mixin, double worldY, int dimension) {
        try {
            Method method = mixin.getDeclaredMethod(
                    "cavebiomes$relativeToConfiguredMaximum", double.class, World.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Double) method.invoke(null, worldY,
                    TestWorlds.forDimension(dimension));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertRedirect(Class<?> mixin, String name,
            String targetMethod) throws Exception {
        Method method = mixin.getDeclaredMethod(name, EntityPlayerMP.class);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));

        Redirect redirect = method.getAnnotation(Redirect.class);
        assertArrayEquals(new String[]{targetMethod}, redirect.method());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        At at = redirect.at();
        assertEquals("FIELD", at.value());
        assertEquals(PLAYER_Y, at.target());
        assertEquals(0, at.ordinal());
        assertTrue(at.remap());
    }

    private static String readResource(String path) throws IOException {
        InputStream input = PlayerSpawnHeightMixinContractTest.class.getResourceAsStream(path);
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
