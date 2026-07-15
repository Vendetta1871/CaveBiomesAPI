package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/** Keeps OptiFine's chunk visibility scan in normalized section coordinates. */
public final class OptiFineChunkVisibilityTransformer implements IClassTransformer {
    private static final String TARGET = "net.optifine.render.ChunkVisibility";
    private static final String HEIGHT_API = "net/celestiald/cavebiomes/api/WorldHeightAPI";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        MethodNode method = uniqueMethod(node, "getMaxChunkY");
        List<InsnNode> shifts = integerShifts(method, Opcodes.ISHR);
        if (shifts.size() != 4) {
            throw failure("expected four section shifts in getMaxChunkY, found " + shifts.size());
        }

        replaceShift(method, shifts.get(1), "sectionIndex");
        replaceShift(method, shifts.get(3), "sectionIndex");

        List<InsnNode> leftShifts = integerShifts(method, Opcodes.ISHL);
        if (leftShifts.size() != 1) {
            throw failure("expected one result shift in getMaxChunkY, found " + leftShifts.size());
        }
        replaceShift(method, leftShifts.get(0), "sectionYBase");

        replaceUniqueSectionCount(method);
        replaceUniqueSectionCount(uniqueMethod(node, "<clinit>"));

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode uniqueMethod(ClassNode node, String name) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (name.equals(method.name)) {
                if (result != null) {
                    throw failure("multiple " + name + " methods");
                }
                result = method;
            }
        }
        if (result == null) {
            throw failure("no " + name + " method");
        }
        return result;
    }

    private static List<InsnNode> integerShifts(MethodNode method, int opcode) {
        List<InsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof InsnNode && instruction.getOpcode() == opcode) {
                result.add((InsnNode) instruction);
            }
        }
        return result;
    }

    private static void replaceShift(MethodNode method, InsnNode shift, String apiMethod) {
        AbstractInsnNode shiftAmount = previousRealInstruction(shift);
        if (shiftAmount.getOpcode() != Opcodes.ICONST_4) {
            throw failure("an unexpected shift amount before " + apiMethod);
        }
        method.instructions.insertBefore(shiftAmount, new MethodInsnNode(
                Opcodes.INVOKESTATIC, HEIGHT_API, apiMethod, "(I)I", false));
        method.instructions.remove(shiftAmount);
        method.instructions.remove(shift);
    }

    private static void replaceUniqueSectionCount(MethodNode method) {
        IntInsnNode constant = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof IntInsnNode
                    && instruction.getOpcode() == Opcodes.BIPUSH
                    && ((IntInsnNode) instruction).operand == 16) {
                if (constant != null) {
                    throw failure("multiple section-count constants in " + method.name);
                }
                constant = (IntInsnNode) instruction;
            }
        }
        if (constant == null) {
            throw failure("no section-count constant in " + method.name);
        }
        method.instructions.set(constant, new MethodInsnNode(
                Opcodes.INVOKESTATIC, HEIGHT_API, "getSectionCount", "()I", false));
    }

    private static AbstractInsnNode previousRealInstruction(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() >= 0) {
                return instruction;
            }
        }
        throw failure("no instruction before section shift");
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException("Cave Biomes API OptiFine visibility transformer found " + detail);
    }
}
