package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PortalHeightMixinContractTest {

    private static final String GET_Y =
            "Lnet/minecraft/util/math/BlockPos;getY()I";
    private static final String GET_BLOCK_STATE_WORLD =
            "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;";
    private static final String GET_BLOCK_STATE_SERVER =
            "Lnet/minecraft/world/WorldServer;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;";
    private static final String ADD =
            "Lnet/minecraft/util/math/BlockPos;add(III)Lnet/minecraft/util/math/BlockPos;";
    private static final String ACTUAL_HEIGHT =
            "Lnet/minecraft/world/WorldServer;getActualHeight()I";
    private static final String SET_MUTABLE_POS =
            "Lnet/minecraft/util/math/BlockPos$MutableBlockPos;setPos(III)Lnet/minecraft/util/math/BlockPos$MutableBlockPos;";
    private static final String IS_AIR_BLOCK =
            "Lnet/minecraft/world/WorldServer;isAirBlock(Lnet/minecraft/util/math/BlockPos;)Z";

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void portalFloorAdaptersUseTheConfiguredMinimumOnlyInTheOverworld() {
        WorldHeightAPI.configureLocalRange(-64, 320);

        for (Class<?> owner : new Class<?>[]{MixinBlockPortalSize.class, MixinTeleporter.class}) {
            assertEquals(-1, relativeY(owner, -65, 0));
            assertEquals(0, relativeY(owner, -64, 0));
            assertEquals(1, relativeY(owner, -63, 0));
            assertEquals(64, relativeY(owner, 0, 0));

            for (int dimension : new int[]{-1, 1, 7}) {
                assertEquals(-65, relativeY(owner, -65, dimension));
                assertEquals(0, relativeY(owner, 0, dimension));
                assertEquals(320, relativeY(owner, 320, dimension));
            }
        }

        assertEquals(-64, portalFloor(0));
        assertEquals(0, portalFloor(-1));
        assertEquals(0, portalFloor(1));
        assertEquals(0, portalFloor(7));
    }

    @Test
    public void vanillaRangePreservesEveryPortalFloorComparison() {
        WorldHeightAPI.configureLocalRange(0, 256);

        for (Class<?> owner : new Class<?>[]{MixinBlockPortalSize.class, MixinTeleporter.class}) {
            for (int dimension : new int[]{-1, 0, 1, 7}) {
                for (int y : new int[]{-1, 0, 1, 255, 256}) {
                    assertEquals(y, relativeY(owner, y, dimension));
                }
            }
        }

        for (int dimension : new int[]{-1, 0, 1, 7}) {
            assertEquals(0, portalFloor(dimension));
        }
    }

    @Test
    public void frameDiscoveryRedirectsOnlyTheConstructorFloorRead() throws Exception {
        Method method = findMethod(MixinBlockPortalSize.class, "cavebiomes$frameDescentY");
        Redirect redirect = method.getAnnotation(Redirect.class);

        assertArrayEquals(new String[]{"<init>"}, redirect.method());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        assertAt(redirect.at(), "INVOKE", GET_Y, 0, new String[0]);
        assertSlice(redirect.slice(), "CONSTANT", "", -1, new String[]{"intValue=21"},
                "INVOKE", GET_BLOCK_STATE_WORLD, 0, new String[0]);
    }

    @Test
    public void existingPortalSearchRedirectsOnlyItsTopDownFloorRead() throws Exception {
        Method method = findMethod(MixinTeleporter.class, "cavebiomes$existingPortalScanY");
        Redirect redirect = method.getAnnotation(Redirect.class);

        assertArrayEquals(new String[]{"placeInExistingPortal"}, redirect.method());
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        assertAt(redirect.at(), "INVOKE", GET_Y, 0, new String[0]);
        assertSlice(redirect.slice(), "INVOKE", ADD, 0, new String[0],
                "INVOKE", GET_BLOCK_STATE_SERVER, 0, new String[0]);
    }

    @Test
    public void portalCreationPatchesBothCandidateAndDescentLoopsPrecisely() throws Exception {
        assertFloorConstant("cavebiomes$primaryCandidateFloor",
                Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO,
                "INVOKE", ACTUAL_HEIGHT, 0,
                "INVOKE", SET_MUTABLE_POS, 0);
        assertFloorConstant("cavebiomes$primaryDescentFloor",
                Constant.Condition.GREATER_THAN_ZERO,
                "INVOKE", IS_AIR_BLOCK, 0,
                "INVOKE", IS_AIR_BLOCK, 1);
        assertFloorConstant("cavebiomes$secondaryCandidateFloor",
                Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO,
                "INVOKE", ACTUAL_HEIGHT, 1,
                "INVOKE", SET_MUTABLE_POS, 3);
        assertFloorConstant("cavebiomes$secondaryDescentFloor",
                Constant.Condition.GREATER_THAN_ZERO,
                "INVOKE", IS_AIR_BLOCK, 3,
                "INVOKE", IS_AIR_BLOCK, 4);
    }

    @Test
    public void portalMixinsAreRegisteredForBothLogicalSides() throws IOException {
        String config = readResource("/mixins.cavebiomes.json");
        assertTrue(config.contains("\"MixinBlockPortalSize\""));
        assertTrue(config.contains("\"MixinTeleporter\""));
        assertTrue(config.indexOf("\"MixinBlockPortalSize\"") < config.indexOf("\"client\""));
        assertTrue(config.indexOf("\"MixinTeleporter\"") < config.indexOf("\"client\""));
    }

    private static void assertFloorConstant(String handlerName, Constant.Condition condition,
            String fromValue, String fromTarget, int fromOrdinal,
            String toValue, String toTarget, int toOrdinal) throws Exception {
        Method method = findMethod(MixinTeleporter.class, handlerName);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        ModifyConstant modify = method.getAnnotation(ModifyConstant.class);

        assertArrayEquals(new String[]{"makePortal"}, modify.method());
        assertEquals(1, modify.require());
        assertEquals(1, modify.allow());
        assertEquals(1, modify.constant().length);
        assertEquals(0, modify.constant()[0].intValue());
        assertArrayEquals(new Constant.Condition[]{condition},
                modify.constant()[0].expandZeroConditions());
        assertEquals(1, modify.slice().length);
        assertSlice(modify.slice()[0], fromValue, fromTarget, fromOrdinal, new String[0],
                toValue, toTarget, toOrdinal, new String[0]);
    }

    private static void assertSlice(Slice slice,
            String fromValue, String fromTarget, int fromOrdinal, String[] fromArgs,
            String toValue, String toTarget, int toOrdinal, String[] toArgs) {
        assertAt(slice.from(), fromValue, fromTarget, fromOrdinal, fromArgs);
        assertAt(slice.to(), toValue, toTarget, toOrdinal, toArgs);
    }

    private static void assertAt(At at, String value, String target, int ordinal, String[] args) {
        assertEquals(value, at.value());
        assertEquals(target, at.target());
        assertEquals(ordinal, at.ordinal());
        assertArrayEquals(args, at.args());
    }

    private static int relativeY(Class<?> owner, int y, int dimension) {
        try {
            boolean serverSide = owner == MixinTeleporter.class;
            Method method = owner.getDeclaredMethod(
                    "cavebiomes$relativeToPortalFloor", int.class,
                    serverSide ? WorldServer.class : World.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            Object world = serverSide
                    ? TestWorlds.serverForDimension(dimension)
                    : TestWorlds.forDimension(dimension);
            return (Integer) method.invoke(null, y, world);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int portalFloor(int dimension) {
        try {
            Method method = MixinTeleporter.class.getDeclaredMethod(
                    "cavebiomes$portalFloor", WorldServer.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
            method.setAccessible(true);
            return (Integer) method.invoke(null,
                    TestWorlds.serverForDimension(dimension));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Method findMethod(Class<?> owner, String name) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("Missing method " + owner.getName() + "." + name);
    }

    private static String readResource(String path) throws IOException {
        InputStream input = PortalHeightMixinContractTest.class.getResourceAsStream(path);
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
