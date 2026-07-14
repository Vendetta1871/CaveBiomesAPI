package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import org.junit.After;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommandVolumeHeightMixinContractTest {

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void adaptersImplementConfiguredInclusiveExclusiveBounds() {
        WorldHeightAPI.configureLocalRange(-64, 320);
        for (Class<?> mixin : commandMixins()) {
            assertOutside(mixin, -65);
            assertInside(mixin, -64);
            assertInside(mixin, 0);
            assertInside(mixin, 255);
            assertInside(mixin, 319);
            assertOutside(mixin, 320);
        }
    }

    @Test
    public void adaptersAreIdentityFunctionsForVanillaRange() {
        WorldHeightAPI.configureLocalRange(0, 256);
        for (Class<?> mixin : commandMixins()) {
            for (int y : new int[]{-1, 0, 1, 255, 256}) {
                assertEquals(y, invokeAdapter(mixin, "cavebiomes$relativeToMinimum", y));
                assertEquals(y, invokeAdapter(mixin,
                        "cavebiomes$relativeToVanillaMaximum", y));
            }
            assertOutside(mixin, -1);
            assertInside(mixin, 0);
            assertInside(mixin, 255);
            assertOutside(mixin, 256);
        }
    }

    @Test
    public void fillRedirectsOnlyTheTwoLegacyGuardReads() throws ReflectiveOperationException {
        assertRedirect(MixinCommandFill.class, "cavebiomes$minimumVolumeY",
                "Lnet/minecraft/util/math/BlockPos;getY()I", "INVOKE", -1, 0, 32768);
        assertRedirect(MixinCommandFill.class, "cavebiomes$maximumVolumeY",
                "Lnet/minecraft/util/math/BlockPos;getY()I", "INVOKE", -1, 1, 32768);
    }

    @Test
    public void cloneAndCompareRedirectOnlyTheirInitialBoundingBoxGuards()
            throws ReflectiveOperationException {
        for (Class<?> mixin : Arrays.<Class<?>>asList(MixinCommandClone.class,
                MixinCommandCompare.class)) {
            int sliceConstant = mixin == MixinCommandClone.class ? 32768 : 524288;
            assertRedirect(mixin, "cavebiomes$sourceMinimumY",
                    "Lnet/minecraft/world/gen/structure/StructureBoundingBox;minY:I",
                    "FIELD", Opcodes.GETFIELD, 0, sliceConstant);
            assertRedirect(mixin, "cavebiomes$sourceMaximumY",
                    "Lnet/minecraft/world/gen/structure/StructureBoundingBox;maxY:I",
                    "FIELD", Opcodes.GETFIELD, 0, sliceConstant);
            assertRedirect(mixin, "cavebiomes$destinationMinimumY",
                    "Lnet/minecraft/world/gen/structure/StructureBoundingBox;minY:I",
                    "FIELD", Opcodes.GETFIELD, 1, sliceConstant);
            assertRedirect(mixin, "cavebiomes$destinationMaximumY",
                    "Lnet/minecraft/world/gen/structure/StructureBoundingBox;maxY:I",
                    "FIELD", Opcodes.GETFIELD, 1, sliceConstant);
        }
    }

    private static List<Class<?>> commandMixins() {
        return Arrays.<Class<?>>asList(MixinCommandFill.class, MixinCommandClone.class,
                MixinCommandCompare.class);
    }

    private static void assertInside(Class<?> mixin, int y) {
        assertTrue(invokeAdapter(mixin, "cavebiomes$relativeToMinimum", y) >= 0);
        assertTrue(invokeAdapter(mixin, "cavebiomes$relativeToVanillaMaximum", y) < 256);
    }

    private static void assertOutside(Class<?> mixin, int y) {
        assertTrue(invokeAdapter(mixin, "cavebiomes$relativeToMinimum", y) < 0
                || invokeAdapter(mixin, "cavebiomes$relativeToVanillaMaximum", y) >= 256);
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

    private static void assertRedirect(Class<?> owner, String methodName, String target,
            String atValue, int opcode, int ordinal, int sliceConstant)
            throws ReflectiveOperationException {
        Method method = findMethod(owner, methodName);
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        Redirect redirect = method.getAnnotation(Redirect.class);
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
        assertEquals(atValue, redirect.at().value());
        assertEquals(target, redirect.at().target());
        assertEquals(opcode, redirect.at().opcode());
        assertEquals(ordinal, redirect.at().ordinal());
        assertEquals("CONSTANT", redirect.slice().from().value());
        assertEquals(Arrays.asList("intValue=" + sliceConstant),
                Arrays.asList(redirect.slice().from().args()));
        assertEquals("INVOKE", redirect.slice().to().value());
        assertEquals("Lnet/minecraft/command/ICommandSender;getEntityWorld()Lnet/minecraft/world/World;",
                redirect.slice().to().target());
    }

    private static Method findMethod(Class<?> owner, String name) {
        List<Method> methods = Arrays.asList(owner.getDeclaredMethods());
        for (Method method : methods) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("Missing method " + owner.getName() + "." + name);
    }
}
