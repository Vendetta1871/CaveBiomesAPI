package net.celestiald.cavebiomes.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MixinBootstrapRequirementTest {
    @Test
    public void missingLaunchLibraryFailsWithAnInstallableDiagnostic() {
        ClassLoader withoutMixin = new ClassLoader(
                MixinBootstrapRequirementTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (name.startsWith("org.spongepowered.asm.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };

        try {
            MixinBootstrapRequirement.initialize(withoutMixin);
            fail("Expected missing MixinBootstrap diagnostic");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("MixinBootstrap 1.1.0"));
            assertTrue(expected.getMessage().contains("client and dedicated-server"));
            assertTrue(expected.getCause() instanceof ClassNotFoundException);
        }
    }

    @Test
    public void corePluginHasNoEagerMixinClassReferences() throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(classBytes(CaveBiomesLoadingPlugin.class)).accept(node, 0);
        boolean sawRequirement = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertFalse("eager Mixin reference " + call.owner,
                        call.owner.startsWith("org/spongepowered/asm/"));
                sawRequirement |= call.owner.equals(
                        "net/celestiald/cavebiomes/core/MixinBootstrapRequirement");
            }
        }
        assertTrue(sawRequirement);
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String path = type.getName().replace('.', '/') + ".class";
        InputStream input = type.getClassLoader().getResourceAsStream(path);
        assertNotNull(path, input);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
