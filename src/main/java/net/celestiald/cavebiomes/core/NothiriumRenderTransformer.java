package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Makes Nothirium address chunk sections relative to the configured minimum Y.
 * Nothirium replaces the vanilla/OptiFine chunk renderer with its own, so the
 * ViewFrustum/RenderGlobal mixins and the OptiFine transformers never touch the
 * pipeline actually drawing blocks. Its renderer hardcodes the vanilla
 * [0, 16) section range, which drops the {@code -minY} storage shift and shows
 * every section 64 blocks (or generally -minY) above its real position.
 *
 * The hooks mirror Nothirium's own CubicChunks compatibility transformers:
 * delegate section lookup, and skip the world-bound guards when the configured
 * height exceeds the vanilla range. Everything routes through
 * {@link NothiriumRenderCompat} so a Nothirium update only risks one clearly
 * named failure point.
 */
public final class NothiriumRenderTransformer implements IClassTransformer {
    private static final String COMPAT = "net/celestiald/cavebiomes/core/NothiriumRenderCompat";
    private static final String WORLD_UTIL = "meldexun.nothirium.mc.util.WorldUtil";
    private static final String RENDER_CHUNK = "meldexun.nothirium.mc.renderer.chunk.RenderChunk";
    private static final String DIRECTION_DOWN = "meldexun.nothirium.util.Direction$1";
    private static final String DIRECTION_UP = "meldexun.nothirium.util.Direction$2";

    private static final String GET_SECTION_DESC =
            "(Lnet/minecraft/world/World;III)Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        String target = transformedName != null ? transformedName : name;
        if (WORLD_UTIL.equals(target)) {
            return transform(node(basicClass), NothiriumRenderTransformer::rewriteGetSection,
                    "WorldUtil.getSection", GET_SECTION_DESC);
        }
        if (RENDER_CHUNK.equals(target)) {
            return transform(node(basicClass), NothiriumRenderTransformer::rewriteMarkDirty,
                    "RenderChunk.markDirty", "()V");
        }
        if (DIRECTION_DOWN.equals(target) || DIRECTION_UP.equals(target)) {
            return transform(node(basicClass), NothiriumRenderTransformer::rewriteIsFaceCulled,
                    target + ".isFaceCulled", null);
        }
        return basicClass;
    }

    private static ClassNode node(byte[] basicClass) {
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        return node;
    }

    private static byte[] transform(ClassNode node, java.util.function.Consumer<MethodNode> rewrite, String what,
                                    String desc) {
        MethodNode method = uniqueMethod(node, what, desc);
        rewrite.accept(method);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode uniqueMethod(ClassNode node, String what, String desc) {
        String methodName = what.substring(what.lastIndexOf('.') + 1);
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (methodName.equals(method.name) && (desc == null || desc.equals(method.desc))) {
                if (result != null) {
                    throw failure("multiple " + what + " methods");
                }
                result = method;
            }
        }
        if (result == null) {
            throw failure("no " + what + " method");
        }
        return result;
    }

    /** WorldUtil.getSection(World, int, int, int): replace the whole lookup with the height-aware delegate. */
    private static void rewriteGetSection(MethodNode method) {
        if (!method.desc.equals("(Lnet/minecraft/world/World;III)Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;")) {
            throw failure("an unexpected WorldUtil.getSection signature " + method.desc);
        }
        InsnList delegate = new InsnList();
        delegate.add(new VarInsnNode(Opcodes.ALOAD, 0));
        delegate.add(new VarInsnNode(Opcodes.ILOAD, 1));
        delegate.add(new VarInsnNode(Opcodes.ILOAD, 2));
        delegate.add(new VarInsnNode(Opcodes.ILOAD, 3));
        delegate.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "getSection",
                "(Lnet/minecraft/world/World;III)Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;", false));
        delegate.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.insert(delegate);
    }

    /** RenderChunk.markDirty(): when the height is extended, skip the [0, 16) section guard. */
    private static void rewriteMarkDirty(MethodNode method) {
        LabelNode afterGuard = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                for (AbstractInsnNode next = instruction.getNext(); next != null; next = next.getNext()) {
                    if (next instanceof LabelNode) {
                        afterGuard = (LabelNode) next;
                        break;
                    }
                    if (next.getOpcode() >= 0) {
                        break;
                    }
                }
                break;
            }
        }
        if (afterGuard == null) {
            throw failure("no early return guard in RenderChunk.markDirty");
        }
        InsnList skip = new InsnList();
        skip.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "useExtendedHeight", "()Z", false));
        skip.add(new JumpInsnNode(Opcodes.IFNE, afterGuard));
        method.instructions.insert(skip);
    }

    /** Direction$1/$2.isFaceCulled(...): when the height is extended, skip the world-bound cull shortcut. */
    private static void rewriteIsFaceCulled(MethodNode method) {
        JumpInsnNode boundGuard = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof JumpInsnNode && instruction.getOpcode() != Opcodes.GOTO) {
                boundGuard = (JumpInsnNode) instruction;
                break;
            }
        }
        if (boundGuard == null) {
            throw failure("no world-bound guard in Direction.isFaceCulled");
        }
        InsnList skip = new InsnList();
        skip.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "useExtendedHeight", "()Z", false));
        skip.add(new JumpInsnNode(Opcodes.IFNE, boundGuard.label));
        method.instructions.insert(skip);
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException("Cave Biomes API Nothirium transformer found " + detail);
    }
}
