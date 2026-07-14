package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PistonAndFallingHeightMixinContractTest {

    private static final String GET_Y =
            "Lnet/minecraft/util/math/BlockPos;getY()I";
    private static final String SET_BLOCK_TO_AIR =
            "Lnet/minecraft/world/World;setBlockToAir(Lnet/minecraft/util/math/BlockPos;)Z";

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void lowerBoundaryAdaptersUseTheConfiguredMinimum() {
        WorldHeightAPI.configureLocalRange(-64, 320);
        for (Class<?> mixin : minimumAdapterMixins()) {
            assertEquals(-1, minimumRelativeY(mixin, -65));
            assertEquals(0, minimumRelativeY(mixin, -64));
            assertEquals(1, minimumRelativeY(mixin, -63));
            assertEquals(64, minimumRelativeY(mixin, 0));
        }

        assertFalse(pistonPositionPassesLowerGuard(-65));
        assertTrue(pistonPositionPassesLowerGuard(-64));
        assertFalse(pistonCanMoveDownFrom(-64));
        assertTrue(pistonCanMoveDownFrom(-63));

        for (Class<?> mixin : fallingBlockMixins()) {
            assertFalse(fallingCheckStarts(mixin, -65));
            assertTrue(fallingCheckStarts(mixin, -64));
            assertFalse(synchronousDescentContinues(mixin, -64));
            assertTrue(synchronousDescentContinues(mixin, -63));
        }
    }

    @Test
    public void fallingEntityLifetimeTracksBothFiniteEdges() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        assertTrue(expiresAfterOneHundredTicks(-65));
        assertTrue(expiresAfterOneHundredTicks(-64));
        assertFalse(expiresAfterOneHundredTicks(-63));
        assertFalse(expiresAfterOneHundredTicks(319));
        assertFalse(expiresAfterOneHundredTicks(320));
        assertTrue(expiresAfterOneHundredTicks(321));
    }

    @Test
    public void adaptersPreserveVanillaZeroToTwoFiftySixBehavior() {
        WorldHeightAPI.configureLocalRange(0, 256);
        for (Class<?> mixin : minimumAdapterMixins()) {
            for (int y : new int[]{-1, 0, 1, 255, 256, 257}) {
                assertEquals(y, minimumRelativeY(mixin, y));
            }
        }
        for (int y : new int[]{-1, 0, 1, 255, 256, 257}) {
            assertEquals(y, maximumRelativeY(y));
        }

        assertFalse(pistonPositionPassesLowerGuard(-1));
        assertTrue(pistonPositionPassesLowerGuard(0));
        assertFalse(pistonCanMoveDownFrom(0));
        assertTrue(pistonCanMoveDownFrom(1));
        assertTrue(expiresAfterOneHundredTicks(0));
        assertFalse(expiresAfterOneHundredTicks(1));
        assertFalse(expiresAfterOneHundredTicks(256));
        assertTrue(expiresAfterOneHundredTicks(257));
    }

    @Test
    public void pistonRedirectsOnlyItsTwoHardcodedLowerReads() throws Exception {
        String borderContains =
                "Lnet/minecraft/world/border/WorldBorder;contains(Lnet/minecraft/util/math/BlockPos;)Z";
        String worldHeight = "Lnet/minecraft/world/World;getHeight()I";
        assertRedirect(MixinBlockPistonBase.class, "cavebiomes$minimumPushY",
                "canPush", 0, "INVOKE", borderContains, new String[0],
                "INVOKE", worldHeight, new String[0]);
        assertRedirect(MixinBlockPistonBase.class, "cavebiomes$downwardPushY",
                "canPush", 1, "INVOKE", borderContains, new String[0],
                "INVOKE", worldHeight, new String[0]);

        assertTrue(Modifier.isStatic(findMethod(MixinBlockPistonBase.class,
                "cavebiomes$minimumPushY").getModifiers()));
        assertTrue(Modifier.isStatic(findMethod(MixinBlockPistonBase.class,
                "cavebiomes$downwardPushY").getModifiers()));
    }

    @Test
    public void blockFallingRedirectsOnlyStartAndSynchronousFloorReads() throws Exception {
        assertFallingBlockRedirects(MixinBlockFalling.class, "checkFallable");
        assertFallingBlockRedirects(MixinBlockDragonEgg.class, "checkFall");
    }

    @Test
    public void entityLifetimeRedirectsOnlyTheHundredTickHeightReads() throws Exception {
        assertRedirect(MixinEntityFallingBlock.class, "cavebiomes$minimumLifetimeY",
                "onUpdate", 0, "CONSTANT", "", new String[]{"intValue=100"},
                "CONSTANT", "", new String[]{"intValue=600"});
        assertRedirect(MixinEntityFallingBlock.class, "cavebiomes$maximumLifetimeY",
                "onUpdate", 1, "CONSTANT", "", new String[]{"intValue=100"},
                "CONSTANT", "", new String[]{"intValue=600"});
    }

    private static void assertFallingBlockRedirects(Class<?> owner, String targetMethod)
            throws Exception {
        assertRedirect(owner, "cavebiomes$minimumStartY", targetMethod, 0,
                "HEAD", "", new String[0],
                "CONSTANT", "", new String[]{"intValue=32"});
        assertRedirect(owner, "cavebiomes$synchronousDescentY", targetMethod, 0,
                "INVOKE", SET_BLOCK_TO_AIR, new String[0],
                "TAIL", "", new String[0]);
        assertRedirect(owner, "cavebiomes$synchronousLandingY", targetMethod, 1,
                "INVOKE", SET_BLOCK_TO_AIR, new String[0],
                "TAIL", "", new String[0]);
    }

    private static void assertRedirect(Class<?> owner, String handlerName,
            String targetMethod, int ordinal, String fromValue, String fromTarget,
            String[] fromArgs, String toValue, String toTarget, String[] toArgs)
            throws Exception {
        Method method = findMethod(owner, handlerName);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        Redirect redirect = method.getAnnotation(Redirect.class);
        assertArrayEquals(new String[]{targetMethod}, redirect.method());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        assertEquals("INVOKE", redirect.at().value());
        assertEquals(GET_Y, redirect.at().target());
        assertEquals(ordinal, redirect.at().ordinal());

        At from = redirect.slice().from();
        assertEquals(fromValue, from.value());
        assertEquals(fromTarget, from.target());
        assertArrayEquals(fromArgs, from.args());
        At to = redirect.slice().to();
        assertEquals(toValue, to.value());
        assertEquals(toTarget, to.target());
        assertArrayEquals(toArgs, to.args());
    }

    private static boolean pistonPositionPassesLowerGuard(int y) {
        return minimumRelativeY(MixinBlockPistonBase.class, y) >= 0;
    }

    private static boolean pistonCanMoveDownFrom(int y) {
        return minimumRelativeY(MixinBlockPistonBase.class, y) != 0;
    }

    private static boolean fallingCheckStarts(Class<?> owner, int y) {
        return minimumRelativeY(owner, y) >= 0;
    }

    private static boolean synchronousDescentContinues(Class<?> owner, int y) {
        return minimumRelativeY(owner, y) > 0;
    }

    private static boolean expiresAfterOneHundredTicks(int y) {
        return minimumRelativeY(MixinEntityFallingBlock.class, y) < 1
                || maximumRelativeY(y) > 256;
    }

    private static int minimumRelativeY(Class<?> owner, int y) {
        return invokeAdapter(owner, "cavebiomes$relativeToMinimum", y);
    }

    private static int maximumRelativeY(int y) {
        return invokeAdapter(MixinEntityFallingBlock.class,
                "cavebiomes$relativeToVanillaMaximum", y);
    }

    private static int invokeAdapter(Class<?> owner, String name, int y) {
        try {
            Method method = owner.getDeclaredMethod(name, int.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Integer) method.invoke(null, y);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<Class<?>> minimumAdapterMixins() {
        return Arrays.<Class<?>>asList(MixinBlockPistonBase.class,
                MixinBlockFalling.class, MixinBlockDragonEgg.class,
                MixinEntityFallingBlock.class);
    }

    private static List<Class<?>> fallingBlockMixins() {
        return Arrays.<Class<?>>asList(MixinBlockFalling.class,
                MixinBlockDragonEgg.class);
    }

    private static Method findMethod(Class<?> owner, String name) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("Missing method " + owner.getName() + "." + name);
    }
}
