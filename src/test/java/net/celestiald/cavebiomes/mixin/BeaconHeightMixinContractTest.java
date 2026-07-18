package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.World;
import org.junit.After;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

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

public class BeaconHeightMixinContractTest {

    private static final String IS_COMPLETE =
            "Lnet/minecraft/tileentity/TileEntityBeacon;isComplete:Z";
    private static final String GET_BLOCK_STATE =
            "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;";

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void overworldBeaconScansUseTheConfiguredBounds() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        assertEquals(320, beamCeiling(256, 0));
        assertEquals(-64, baseFloor(0, 0));
        assertTrue(319 < beamCeiling(256, 0));
        assertTrue(-64 >= baseFloor(0, 0));
    }

    @Test
    public void configuredBoundsRemainExclusiveAboveAndInclusiveBelow() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        int ceiling = beamCeiling(256, 0);
        int floor = baseFloor(0, 0);
        assertTrue(319 < ceiling);
        assertFalse(320 < ceiling);
        assertTrue(-64 >= floor);
        assertFalse(-65 >= floor);
    }

    @Test
    public void nonOverworldDimensionsRetainVanillaBounds() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        for (int dimension : new int[]{-1, 1, 7}) {
            assertEquals(256, beamCeiling(256, dimension));
            assertEquals(0, baseFloor(0, dimension));
        }
    }

    @Test
    public void vanillaRangeIsAnExactNoOp() {
        WorldHeightAPI.configureLocalRange(0, 256);

        for (int dimension : new int[]{-1, 0, 1, 7}) {
            assertEquals(256, beamCeiling(256, dimension));
            assertEquals(0, baseFloor(0, dimension));
        }
    }

    @Test
    public void injectionsTargetOnlyTheTwoBeaconScanComparisons() throws Exception {
        Method ceiling = MixinTileEntityBeacon.class.getDeclaredMethod(
                "cavebiomes$beamCeiling", int.class);
        assertPrivateInstanceHandler(ceiling);
        ModifyConstant ceilingModify = ceiling.getAnnotation(ModifyConstant.class);
        assertModifyContract(ceilingModify);
        assertEquals(1, ceilingModify.constant().length);
        assertEquals(256, ceilingModify.constant()[0].intValue());

        Method floor = MixinTileEntityBeacon.class.getDeclaredMethod(
                "cavebiomes$baseFloor", int.class);
        assertPrivateInstanceHandler(floor);
        ModifyConstant floorModify = floor.getAnnotation(ModifyConstant.class);
        assertModifyContract(floorModify);
        assertEquals(1, floorModify.constant().length);
        assertEquals(0, floorModify.constant()[0].intValue());
        assertArrayEquals(
                new Constant.Condition[]{Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO},
                floorModify.constant()[0].expandZeroConditions());
        assertEquals(1, floorModify.slice().length);
        Slice slice = floorModify.slice()[0];
        assertAt(slice.from(), "FIELD", IS_COMPLETE, 0);
        assertEquals(Opcodes.GETFIELD, slice.from().opcode());
        assertAt(slice.to(), "INVOKE", GET_BLOCK_STATE, 1);
    }

    @Test
    public void beaconMixinIsRegisteredOnBothLogicalSides() throws IOException {
        String config = readResource("/mixins.cavebiomes.json");
        int mixin = config.indexOf("\"MixinTileEntityBeacon\"");
        assertTrue(mixin >= 0);
        assertTrue(mixin < config.indexOf("\"client\""));
    }

    private static void assertPrivateInstanceHandler(Method method) {
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
    }

    private static void assertModifyContract(ModifyConstant modify) {
        assertArrayEquals(new String[]{"updateSegmentColors"}, modify.method());
        assertEquals(1, modify.require());
        assertEquals(1, modify.allow());
    }

    private static void assertAt(At at, String value, String target, int ordinal) {
        assertEquals(value, at.value());
        assertEquals(target, at.target());
        assertEquals(ordinal, at.ordinal());
    }

    private static int beamCeiling(int vanillaCeiling, int dimension) {
        return invokeHelper("cavebiomes$beamCeilingForWorld",
                vanillaCeiling, dimension);
    }

    private static int baseFloor(int vanillaFloor, int dimension) {
        return invokeHelper("cavebiomes$baseFloorForWorld", vanillaFloor, dimension);
    }

    private static int invokeHelper(String name, int vanillaValue, int dimension) {
        try {
            Method method = MixinTileEntityBeacon.class.getDeclaredMethod(
                    name, int.class, World.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Integer) method.invoke(null, vanillaValue,
                    TestWorlds.forDimension(dimension));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String readResource(String path) throws IOException {
        InputStream input = BeaconHeightMixinContractTest.class.getResourceAsStream(path);
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
