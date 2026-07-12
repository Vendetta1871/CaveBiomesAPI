package net.celestiald.cavebiomes.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Loads the external launch library with a useful diagnostic when it is missing or incompatible. */
final class MixinBootstrapRequirement {
    private static final String BOOTSTRAP = "org.spongepowered.asm.launch.MixinBootstrap";
    private static final String MIXINS = "org.spongepowered.asm.mixin.Mixins";
    private static final String CONFIGURATION = "mixins.cavebiomes.json";

    private MixinBootstrapRequirement() {
    }

    static void initialize(ClassLoader loader) {
        try {
            Class<?> bootstrap = Class.forName(BOOTSTRAP, true, loader);
            invoke(bootstrap.getMethod("init"), null);
            Class<?> mixins = Class.forName(MIXINS, true, loader);
            invoke(mixins.getMethod("addConfiguration", String.class),
                    null, CONFIGURATION);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError exception) {
            throw missing(exception);
        }
    }

    private static void invoke(Method method, Object target, Object... arguments) {
        try {
            method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw missing(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("MixinBootstrap initialization failed", cause);
        }
    }

    private static IllegalStateException missing(Throwable cause) {
        return new IllegalStateException("CaveBiomesAPI requires MixinBootstrap 1.1.0. "
                + "Install its jar in the client and dedicated-server mods directories before "
                + "starting Forge 1.12.2.", cause);
    }
}
