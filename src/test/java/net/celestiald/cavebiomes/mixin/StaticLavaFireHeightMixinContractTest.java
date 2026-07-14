package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StaticLavaFireHeightMixinContractTest {

    private static final String BLOCK_POS_ADD =
            "Lnet/minecraft/util/math/BlockPos;add(III)Lnet/minecraft/util/math/BlockPos;";
    private static final String BLOCK_POS_Y =
            "Lnet/minecraft/util/math/BlockPos;getY()I";
    private static final String FIRE_PLACE_EVENT =
            "Lnet/minecraftforge/event/ForgeEventFactory;fireFluidPlaceBlockEvent("
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)"
                    + "Lnet/minecraft/block/state/IBlockState;";

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void overworldLoadGuardCoversTheConfiguredFiniteRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        assertFalse(insideLoadGuard(-65, 0));
        assertTrue(insideLoadGuard(-64, 0));
        assertTrue(insideLoadGuard(-1, 0));
        assertTrue(insideLoadGuard(0, 0));
        assertTrue(insideLoadGuard(255, 0));
        assertTrue(insideLoadGuard(319, 0));
        assertFalse(insideLoadGuard(320, 0));
    }

    @Test
    public void nonOverworldDimensionsRetainVanillaFireGuards() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        for (int dimension : new int[]{-1, 1, 7}) {
            assertFalse(insideLoadGuard(-1, dimension));
            assertTrue(insideLoadGuard(0, dimension));
            assertTrue(insideLoadGuard(255, dimension));
            assertFalse(insideLoadGuard(256, dimension));
        }
    }

    @Test
    public void vanillaRangeIsAnExactNoOpInEveryDimension() {
        WorldHeightAPI.configureLocalRange(0, 256);

        for (int dimension : new int[]{-1, 0, 1, 7}) {
            for (int y : new int[]{-65, -1, 0, 1, 255, 256, 320}) {
                assertEquals(y, relativeToMinimum(y, dimension));
                assertEquals(y, relativeToMaximum(y, dimension));
            }
        }
    }

    @Test
    public void heightTranslationDoesNotConsumeLavaRandomness() {
        WorldHeightAPI.configureLocalRange(-64, 320);
        Random control = new Random(123456789L);
        Random observed = new Random(123456789L);

        for (int y = -65; y <= 320; ++y) {
            relativeToMinimum(y, 0);
            relativeToMaximum(y, 0);
        }
        for (int i = 0; i < 32; ++i) {
            assertEquals(control.nextInt(), observed.nextInt());
        }
    }

    @Test
    public void redirectsCoverOnlyTheFiveVerticalGuardReads() throws Exception {
        assertUpdateRedirect("cavebiomes$ascendingCandidateFloor", 0, 0, 0);
        assertUpdateRedirect("cavebiomes$horizontalCandidateFloor", 1, 1, 0);
        assertUpdateRedirect("cavebiomes$horizontalCandidateCeiling", 1, 1, 1);
        assertBurnRedirect("cavebiomes$flammableCandidateFloor", 0);
        assertBurnRedirect("cavebiomes$flammableCandidateCeiling", 1);
    }

    @Test
    public void staticLiquidMixinIsRegisteredOnBothLogicalSides() throws IOException {
        String config = readResource("/mixins.cavebiomes.json");
        int mixin = config.indexOf("\"MixinBlockStaticLiquid\"");
        assertTrue(mixin >= 0);
        assertTrue(mixin < config.indexOf("\"client\""));
    }

    private static boolean insideLoadGuard(int worldY, int dimension) {
        return relativeToMinimum(worldY, dimension) >= 0
                && relativeToMaximum(worldY, dimension) < 256;
    }

    private static int relativeToMinimum(int worldY, int dimension) {
        return invokeTranslation("cavebiomes$relativeToMinimum", worldY, dimension);
    }

    private static int relativeToMaximum(int worldY, int dimension) {
        return invokeTranslation("cavebiomes$relativeToMaximum", worldY, dimension);
    }

    private static int invokeTranslation(String name, int worldY, int dimension) {
        try {
            Method method = MixinBlockStaticLiquid.class.getDeclaredMethod(
                    name, int.class, int.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Integer) method.invoke(null, worldY, dimension);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertUpdateRedirect(String name, int addOrdinal,
            int eventOrdinal, int yOrdinal) throws Exception {
        Method method = MixinBlockStaticLiquid.class.getDeclaredMethod(name,
                BlockPos.class, World.class, BlockPos.class,
                IBlockState.class, Random.class);
        assertPrivateInstanceHandler(method);
        Redirect redirect = method.getAnnotation(Redirect.class);
        assertRedirect(redirect, "updateTick", yOrdinal);

        Slice slice = redirect.slice();
        assertAt(slice.from(), "INVOKE", BLOCK_POS_ADD, addOrdinal);
        assertAt(slice.to(), "INVOKE", FIRE_PLACE_EVENT, eventOrdinal);
        assertFalse(slice.to().remap());
    }

    private static void assertBurnRedirect(String name, int yOrdinal) throws Exception {
        Method method = MixinBlockStaticLiquid.class.getDeclaredMethod(name,
                BlockPos.class, World.class, BlockPos.class);
        assertPrivateInstanceHandler(method);
        Redirect redirect = method.getAnnotation(Redirect.class);
        assertRedirect(redirect, "getCanBlockBurn", yOrdinal);
        assertAt(redirect.slice().from(), "HEAD", "", -1);
        assertAt(redirect.slice().to(), "TAIL", "", -1);
    }

    private static void assertPrivateInstanceHandler(Method method) {
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
    }

    private static void assertRedirect(Redirect redirect, String method,
            int yOrdinal) {
        assertArrayEquals(new String[]{method}, redirect.method());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        assertAt(redirect.at(), "INVOKE", BLOCK_POS_Y, yOrdinal);
        assertTrue(redirect.at().remap());
    }

    private static void assertAt(At at, String value, String target, int ordinal) {
        assertEquals(value, at.value());
        assertEquals(target, at.target());
        assertEquals(ordinal, at.ordinal());
    }

    private static String readResource(String path) throws IOException {
        InputStream input = StaticLavaFireHeightMixinContractTest.class.getResourceAsStream(path);
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
