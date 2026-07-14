package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Prevents queued block changes from touching a client world after disconnect. */
public final class ClientBlockChangeGuardTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraft.client.network.NetHandlerPlayClient";
    private static final String MCP_METHOD = "handleBlockChange";
    private static final String SRG_METHOD = "func_147234_a";
    private static final String HANDLER_DESC =
            "(Lnet/minecraft/network/play/server/SPacketBlockChange;)V";
    private static final String WORLD = "net/minecraft/client/multiplayer/WorldClient";
    private static final String BLOCK_CHANGE_DESC =
            "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z";
    private static final String HOOK =
            "net/celestiald/cavebiomes/client/ClientBlockChangeGuard";
    private static final String HOOK_DESC = "(L" + WORLD
            + ";Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        MethodNode method = uniqueHandler(node);
        MethodInsnNode blockChange = uniqueBlockChangeCall(method);
        blockChange.setOpcode(Opcodes.INVOKESTATIC);
        blockChange.owner = HOOK;
        blockChange.name = "apply";
        blockChange.desc = HOOK_DESC;
        blockChange.itf = false;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode uniqueHandler(ClassNode node) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (HANDLER_DESC.equals(method.desc)
                    && (MCP_METHOD.equals(method.name) || SRG_METHOD.equals(method.name))) {
                if (result != null) {
                    throw failure("multiple matching block-change handlers");
                }
                result = method;
            }
        }
        if (result == null) {
            throw failure("no matching block-change handler");
        }
        return result;
    }

    private static MethodInsnNode uniqueBlockChangeCall(MethodNode method) {
        MethodInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && WORLD.equals(call.owner)
                        && BLOCK_CHANGE_DESC.equals(call.desc)
                        && ("invalidateRegionAndSetBlock".equals(call.name)
                            || "func_180503_b".equals(call.name))) {
                    if (result != null) {
                        throw failure("multiple world block-change calls");
                    }
                    result = call;
                }
            }
        }
        if (result == null) {
            throw failure("no world block-change call");
        }
        return result;
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException("Cave Biomes API client block-change transformer found " + detail);
    }
}
