package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Prevents concurrent connection flushes from dereferencing a drained queue entry. */
public final class NetworkQueueRaceTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraft.network.NetworkManager";
    private static final String MCP_METHOD = "flushOutboundQueue";
    private static final String SRG_METHOD = "func_150733_h";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        MethodNode method = uniqueMethod(node);

        MethodInsnNode isEmpty = uniqueQueueCall(method, "isEmpty", "()Z");
        JumpInsnNode exitWhenEmpty = nextJump(isEmpty);
        if (exitWhenEmpty.getOpcode() != Opcodes.IFNE) {
            throw failure("an unexpected queue-empty branch");
        }

        MethodInsnNode poll = uniqueQueueCall(method, "poll", "()Ljava/lang/Object;");
        VarInsnNode tupleStore = nextStore(poll);
        method.instructions.insert(tupleStore, new JumpInsnNode(Opcodes.IFNULL, exitWhenEmpty.label));
        method.instructions.insert(tupleStore, new VarInsnNode(Opcodes.ALOAD, tupleStore.var));

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode uniqueMethod(ClassNode node) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if ("()V".equals(method.desc)
                    && (MCP_METHOD.equals(method.name) || SRG_METHOD.equals(method.name))) {
                if (result != null) {
                    throw failure("multiple matching flush methods");
                }
                result = method;
            }
        }
        if (result == null) {
            throw failure("no matching flush method");
        }
        return result;
    }

    private static MethodInsnNode uniqueQueueCall(MethodNode method, String name, String desc) {
        MethodInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "java/util/Queue".equals(call.owner)
                        && name.equals(call.name)
                        && desc.equals(call.desc)) {
                    if (result != null) {
                        throw failure("multiple Queue." + name + " calls");
                    }
                    result = call;
                }
            }
        }
        if (result == null) {
            throw failure("no Queue." + name + " call");
        }
        return result;
    }

    private static JumpInsnNode nextJump(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getNext(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof JumpInsnNode) {
                return (JumpInsnNode) instruction;
            }
            if (instruction.getOpcode() >= 0) {
                throw failure("an unexpected instruction after Queue.isEmpty");
            }
        }
        throw failure("no queue-empty branch");
    }

    private static VarInsnNode nextStore(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getNext(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof VarInsnNode && instruction.getOpcode() == Opcodes.ASTORE) {
                return (VarInsnNode) instruction;
            }
            int opcode = instruction.getOpcode();
            if (opcode >= 0 && opcode != Opcodes.CHECKCAST) {
                throw failure("an unexpected instruction after Queue.poll");
            }
        }
        throw failure("no queue tuple store");
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException("Cave Biomes API network queue transformer found " + detail);
    }
}
