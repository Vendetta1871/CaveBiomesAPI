package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Keeps client chunk and block updates safe across disconnects and the configured Y range. */
public final class ClientBlockChangeGuardTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraft.client.network.NetHandlerPlayClient";
    private static final String MCP_BLOCK_METHOD = "handleBlockChange";
    private static final String SRG_BLOCK_METHOD = "func_147234_a";
    private static final String BLOCK_HANDLER_DESC =
            "(Lnet/minecraft/network/play/server/SPacketBlockChange;)V";
    private static final String MCP_MULTI_METHOD = "handleMultiBlockChange";
    private static final String SRG_MULTI_METHOD = "func_147287_a";
    private static final String MULTI_HANDLER_DESC =
            "(Lnet/minecraft/network/play/server/SPacketMultiBlockChange;)V";
    private static final String MCP_CHUNK_METHOD = "handleChunkData";
    private static final String SRG_CHUNK_METHOD = "func_147263_a";
    private static final String CHUNK_HANDLER_DESC =
            "(Lnet/minecraft/network/play/server/SPacketChunkData;)V";
    private static final String WORLD = "net/minecraft/client/multiplayer/WorldClient";
    private static final String BLOCK_CHANGE_DESC =
            "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z";
    private static final String HOOK =
            "net/celestiald/cavebiomes/client/ClientBlockChangeGuard";
    private static final String HOOK_DESC = "(L" + WORLD
            + ";Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z";
    private static final String HEIGHT_API =
            "net/celestiald/cavebiomes/api/WorldHeightAPI";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        replaceBlockChangeCall(uniqueHandler(node, MCP_BLOCK_METHOD, SRG_BLOCK_METHOD,
                BLOCK_HANDLER_DESC));
        replaceBlockChangeCall(uniqueHandler(node, MCP_MULTI_METHOD, SRG_MULTI_METHOD,
                MULTI_HANDLER_DESC));
        patchChunkRenderRange(uniqueHandler(node, MCP_CHUNK_METHOD, SRG_CHUNK_METHOD,
                CHUNK_HANDLER_DESC));

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void replaceBlockChangeCall(MethodNode method) {
        MethodInsnNode blockChange = uniqueBlockChangeCall(method);
        blockChange.setOpcode(Opcodes.INVOKESTATIC);
        blockChange.owner = HOOK;
        blockChange.name = "apply";
        blockChange.desc = HOOK_DESC;
        blockChange.itf = false;
    }

    private static void patchChunkRenderRange(MethodNode method) {
        MethodInsnNode renderCall = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && WORLD.equals(call.owner)
                        && "(IIIIII)V".equals(call.desc)
                        && ("markBlockRangeForRenderUpdate".equals(call.name)
                            || "func_147458_c".equals(call.name))) {
                    if (renderCall != null) {
                        throw failure("multiple chunk render-range calls");
                    }
                    renderCall = call;
                }
            }
        }
        if (renderCall == null) {
            throw failure("no chunk render-range call");
        }

        AbstractInsnNode rangeStart = renderCall.getPrevious();
        while (rangeStart != null) {
            if (rangeStart instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) rangeStart;
                if (field.getOpcode() == Opcodes.GETFIELD
                        && ("L" + WORLD + ";").equals(field.desc)) {
                    break;
                }
            }
            rangeStart = rangeStart.getPrevious();
        }
        if (rangeStart == null) {
            throw failure("no world load before chunk render-range call");
        }

        AbstractInsnNode minimum = null;
        AbstractInsnNode maximum = null;
        for (AbstractInsnNode instruction = rangeStart.getNext();
                instruction != renderCall; instruction = instruction.getNext()) {
            if (instruction.getOpcode() == Opcodes.ICONST_0) {
                if (minimum != null) throw failure("multiple minimum-Y constants in render range");
                minimum = instruction;
            } else if (instruction instanceof IntInsnNode
                    && instruction.getOpcode() == Opcodes.SIPUSH
                    && ((IntInsnNode) instruction).operand == 256) {
                if (maximum != null) throw failure("multiple maximum-Y constants in render range");
                maximum = instruction;
            }
        }
        if (minimum == null || maximum == null) {
            throw failure("unexpected vanilla chunk render range");
        }
        method.instructions.set(minimum, heightCall("getMinY"));
        method.instructions.set(maximum, heightCall("getMaxY"));
    }

    private static MethodInsnNode heightCall(String method) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HEIGHT_API, method, "()I", false);
    }

    private static MethodNode uniqueHandler(ClassNode node, String mcpName, String srgName,
            String descriptor) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (descriptor.equals(method.desc)
                    && (mcpName.equals(method.name) || srgName.equals(method.name))) {
                if (result != null) {
                    throw failure("multiple matching " + mcpName + " handlers");
                }
                result = method;
            }
        }
        if (result == null) {
            throw failure("no matching " + mcpName + " handler");
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
