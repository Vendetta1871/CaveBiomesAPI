package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import org.junit.After;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CommandHeightMixinContractTest {

    @After
    public void restoreDefaultRange() {
        WorldHeightAPI.configureLocalRange(-64, 320);
    }

    @Test
    public void parseBlockPosAcceptsBothFiniteHeightBoundaries() throws Exception {
        WorldHeightAPI.configureLocalRange(-64, 320);
        Method handler = handler();

        assertEquals(new BlockPos(1, -64, 3), invoke(handler, new String[]{"1", "-64", "3"}));
        assertEquals(new BlockPos(1, 319, 3), invoke(handler, new String[]{"1", "319", "3"}));
    }

    @Test
    public void injectionReplacesTheSharedVanillaParserAtMethodEntry() throws Exception {
        Inject inject = handler().getAnnotation(Inject.class);
        assertNotNull(inject);
        assertArrayEquals(new String[]{"parseBlockPos"}, inject.method());
        assertEquals(1, inject.at().length);
        assertEquals("HEAD", inject.at()[0].value());
        assertTrue(inject.cancellable());
    }

    private static Method handler() throws NoSuchMethodException {
        Method handler = MixinCommandBase.class.getDeclaredMethod(
                "cavebiomes$parseBlockPos", ICommandSender.class, String[].class,
                int.class, boolean.class, CallbackInfoReturnable.class);
        handler.setAccessible(true);
        return handler;
    }

    private static BlockPos invoke(Method handler, String[] coordinates) throws Exception {
        ICommandSender sender = (ICommandSender) Proxy.newProxyInstance(
                ICommandSender.class.getClassLoader(),
                new Class<?>[]{ICommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPosition")) {
                        return BlockPos.ORIGIN;
                    }
                    if (method.getName().equals("getEntityWorld")) {
                        return TestWorlds.extendedOverworld();
                    }
                    return defaultValue(method.getReturnType());
                });
        CallbackInfoReturnable<BlockPos> callback =
                new CallbackInfoReturnable<>("parseBlockPos", true);
        handler.invoke(null, sender, coordinates, 0, false, callback);
        return callback.getReturnValue();
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }
}
