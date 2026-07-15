package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Makes OptiFine address render sections relative to the configured minimum Y. */
public final class OptiFineRenderChunkTransformer implements IClassTransformer {
    private static final String TARGET = "net.optifine.util.RenderChunkUtils";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        MethodNode method = uniqueCountBlocksMethod(node);
        InsnNode shift = uniqueSectionShift(method);
        AbstractInsnNode shiftAmount = previousRealInstruction(shift);
        if (shiftAmount.getOpcode() != Opcodes.ICONST_4) {
            throw failure("an unexpected render-section shift amount");
        }

        method.instructions.insertBefore(shiftAmount, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/celestiald/cavebiomes/api/WorldHeightAPI",
                "sectionIndex",
                "(I)I",
                false));
        method.instructions.remove(shiftAmount);
        method.instructions.remove(shift);
        guardSectionArrayAccess(method);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode uniqueCountBlocksMethod(ClassNode node) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if ("getCountBlocks".equals(method.name) && method.desc.endsWith(")I")) {
                if (result != null) {
                    throw failure("multiple getCountBlocks methods");
                }
                result = method;
            }
        }
        if (result == null) {
            throw failure("no getCountBlocks method");
        }
        return result;
    }

    private static void guardSectionArrayAccess(MethodNode method) {
        InsnNode arrayLoad = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof InsnNode && instruction.getOpcode() == Opcodes.AALOAD) {
                if (arrayLoad != null) {
                    throw failure("multiple section array reads in getCountBlocks");
                }
                arrayLoad = (InsnNode) instruction;
            }
        }
        if (arrayLoad == null) {
            throw failure("no section array read in getCountBlocks");
        }

        // Stack before AALOAD is [sections, index]. Preserve the array while
        // passing the index and the array's real length through a bounds helper.
        method.instructions.insertBefore(arrayLoad, new InsnNode(Opcodes.DUP2));
        method.instructions.insertBefore(arrayLoad, new InsnNode(Opcodes.POP));
        method.instructions.insertBefore(arrayLoad, new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.insertBefore(arrayLoad, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/celestiald/cavebiomes/core/OptiFineRenderCompat",
                "clampSectionIndex",
                "(II)I",
                false));
    }

    private static InsnNode uniqueSectionShift(MethodNode method) {
        InsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof InsnNode && instruction.getOpcode() == Opcodes.ISHR) {
                if (result != null) {
                    throw failure("multiple integer shifts in getCountBlocks");
                }
                result = (InsnNode) instruction;
            }
        }
        if (result == null) {
            throw failure("no render-section shift in getCountBlocks");
        }
        return result;
    }

    private static AbstractInsnNode previousRealInstruction(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() >= 0) {
                return instruction;
            }
        }
        throw failure("no instruction before render-section shift");
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException("Cave Biomes API OptiFine transformer found " + detail);
    }
}
