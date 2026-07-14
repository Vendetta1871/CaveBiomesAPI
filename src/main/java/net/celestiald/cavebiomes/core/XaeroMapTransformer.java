package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Normalizes Xaero's section-height scan for chunks whose first section is below Y zero. */
public final class XaeroMapTransformer implements IClassTransformer {
    private static final String WORLD_MAP = "xaero.map.MapWriter";
    private static final String MINIMAP = "xaero.common.minimap.write.MinimapWriter";
    private static final String METHOD_DESC = "(Lnet/minecraft/world/chunk/Chunk;I)I";
    private static final String COMPAT = "net/celestiald/cavebiomes/core/XaeroMapCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !isTarget(name, transformedName)) return basicClass;

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        MethodNode method = uniqueHeightMethod(node);
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT,
                "getSectionBasedHeight", METHOD_DESC, false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isTarget(String name, String transformedName) {
        return WORLD_MAP.equals(name) || WORLD_MAP.equals(transformedName)
                || MINIMAP.equals(name) || MINIMAP.equals(transformedName);
    }

    private static MethodNode uniqueHeightMethod(ClassNode node) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if ("getSectionBasedHeight".equals(method.name)
                    && METHOD_DESC.equals(method.desc)) {
                if (result != null) throw failure(node.name, "multiple height methods");
                result = method;
            }
        }
        if (result == null) throw failure(node.name, "no compatible height method");
        return result;
    }

    private static IllegalStateException failure(String target, String detail) {
        return new IllegalStateException("Cave Biomes API Xaero transformer found "
                + detail + " in " + target);
    }
}
