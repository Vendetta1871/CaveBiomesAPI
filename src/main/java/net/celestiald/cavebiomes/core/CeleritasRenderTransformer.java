package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Makes Celeritas (and its shader-enabled fork Actinium) address chunk
 * sections relative to the configured minimum Y. Both replace the vanilla
 * chunk renderer with their own, so the ViewFrustum/RenderGlobal mixins and
 * the OptiFine/Nothirium transformers never touch the pipeline actually
 * drawing blocks. Celeritas hardcodes the vanilla [0, 16) section range when
 * creating its section manager and indexes {@code Chunk.getBlockStorageArray()}
 * by world section Y, which drops the {@code -minY} storage shift and shows
 * every section 64 blocks (or generally -minY) above its real position.
 *
 * Actinium already routes its section lookups through a Depths Update
 * compatibility shim whose storage mapping does not match the linear shift
 * used here; the transformer prepends early returns to that shim so the
 * configured height wins while it is extended, leaving the Depths Update
 * logic untouched otherwise.
 *
 * Everything routes through {@link CeleritasRenderCompat} so a renderer update
 * only risks one clearly named failure point.
 */
public final class CeleritasRenderTransformer implements IClassTransformer {
    private static final String COMPAT = "net/celestiald/cavebiomes/core/CeleritasRenderCompat";

    private static final String SECTION_MANAGER = "org.taumc.celeritas.impl.render.terrain.VintageRenderSectionManager";
    private static final String WORLD_SLICE = "org.taumc.celeritas.impl.world.WorldSlice";
    private static final String CLONED_SECTION = "org.taumc.celeritas.impl.world.cloned.ClonedChunkSection";
    private static final String WORLD_RENDERER = "org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer";

    private static final String DEPTHS_UPDATE_COMPAT = "com.dhj.actinium.compat.depthsupdate.DepthsUpdateCompat";
    private static final String ACTINIUM_RENDERER = "com.dhj.actinium.render.terrain.ActiniumWorldRenderer";

    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String EBS = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";
    private static final String STORAGE_ARRAY_DESC = "()[" + EBS;
    private static final String GET_SECTION_DESC = "(L" + CHUNK + ";I)" + EBS;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        String target = transformedName != null ? transformedName : name;
        switch (target) {
            case SECTION_MANAGER:
                return transform(node(basicClass), CeleritasRenderTransformer::rewriteSectionManager,
                        SECTION_MANAGER, "Celeritas");
            case WORLD_SLICE:
                return transform(node(basicClass), CeleritasRenderTransformer::rewritePrepare,
                        "WorldSlice.prepare", "Celeritas");
            case CLONED_SECTION:
                return transform(node(basicClass), CeleritasRenderTransformer::rewriteGetChunkSection,
                        "ClonedChunkSection.getChunkSection", "Celeritas");
            case WORLD_RENDERER:
                return transform(node(basicClass), CeleritasRenderTransformer::rewriteBuildHeights,
                        WORLD_RENDERER, "Celeritas");
            case DEPTHS_UPDATE_COMPAT:
                return transform(node(basicClass), CeleritasRenderTransformer::rewriteDepthsUpdateCompat,
                        DEPTHS_UPDATE_COMPAT, "Actinium");
            case ACTINIUM_RENDERER:
                return transform(node(basicClass), CeleritasRenderTransformer::rewriteActiniumRenderer,
                        ACTINIUM_RENDERER, "Actinium");
            default:
                return basicClass;
        }
    }

    private static ClassNode node(byte[] basicClass) {
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        return node;
    }

    private static byte[] transform(ClassNode node, java.util.function.Consumer<ClassNode> rewrite, String what,
                                    String renderer) {
        rewrite.accept(node);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode uniqueMethod(ClassNode node, String methodName, String renderer) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (methodName.equals(method.name)) {
                if (result != null) {
                    throw failure(renderer, "multiple " + node.name + "." + methodName + " methods");
                }
                result = method;
            }
        }
        if (result == null) {
            throw failure(renderer, "no " + node.name + "." + methodName + " method");
        }
        return result;
    }

    /**
     * VintageRenderSectionManager: create(...) passes hardcoded 0/16 section bounds to the
     * constructor, and isSectionVisuallyEmpty(...) indexes the storage array by world section Y.
     */
    private static void rewriteSectionManager(ClassNode node) {
        rewriteCreate(uniqueMethod(node, "create", "Celeritas"));
        rewriteIsSectionVisuallyEmpty(node, uniqueMethod(node, "isSectionVisuallyEmpty", "Celeritas"));
    }

    /** create(...): replace the iconst_0 / bipush 16 section bounds with the height-aware values. */
    private static void rewriteCreate(MethodNode method) {
        InsnNode zero = null;
        IntInsnNode sixteen = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.ICONST_0) {
                if (zero != null) {
                    throw failure("Celeritas", "multiple iconst_0 constants in VintageRenderSectionManager.create");
                }
                zero = (InsnNode) instruction;
            } else if (instruction.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) instruction).operand == 16) {
                if (sixteen != null) {
                    throw failure("Celeritas", "multiple bipush 16 constants in VintageRenderSectionManager.create");
                }
                sixteen = (IntInsnNode) instruction;
            }
        }
        if (zero == null || sixteen == null) {
            throw failure("Celeritas", "no 0/16 section bounds in VintageRenderSectionManager.create");
        }
        method.instructions.set(zero, new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "getMinSection", "()I", false));
        method.instructions.set(sixteen, new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "getMaxSection", "()I", false));
    }

    /** isSectionVisuallyEmpty(int, int, int): replace the whole lookup with the height-aware delegate. */
    private static void rewriteIsSectionVisuallyEmpty(ClassNode node, MethodNode method) {
        if (!method.desc.equals("(III)Z")) {
            throw failure("Celeritas", "an unexpected isSectionVisuallyEmpty signature " + method.desc);
        }
        FieldNode world = null;
        for (FieldNode field : node.fields) {
            if ("world".equals(field.name)) {
                world = field;
                break;
            }
        }
        if (world == null) {
            throw failure("Celeritas", "no world field in VintageRenderSectionManager");
        }
        InsnList delegate = new InsnList();
        delegate.add(new VarInsnNode(Opcodes.ALOAD, 0));
        delegate.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD, node.name, "world", world.desc));
        delegate.add(new VarInsnNode(Opcodes.ILOAD, 1));
        delegate.add(new VarInsnNode(Opcodes.ILOAD, 2));
        delegate.add(new VarInsnNode(Opcodes.ILOAD, 3));
        delegate.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "isSectionVisuallyEmpty",
                "(Lnet/minecraft/world/World;III)Z", false));
        delegate.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.insert(delegate);
    }

    /**
     * WorldSlice.prepare(...): reroute the direct {@code storageArray[origin.y()]} lookup through
     * the height-aware delegate, keeping the chunk reference and the section Y on the stack.
     */
    private static void rewritePrepare(ClassNode node) {
        MethodNode method = uniqueMethod(node, "prepare", "Celeritas");
        MethodInsnNode storageCall = null;
        AbstractInsnNode arrayLoad = null;
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int i = 0; i < instructions.length; i++) {
            AbstractInsnNode instruction = instructions[i];
            if (instruction instanceof MethodInsnNode && CHUNK.equals(((MethodInsnNode) instruction).owner)
                    && STORAGE_ARRAY_DESC.equals(((MethodInsnNode) instruction).desc)) {
                if (storageCall != null) {
                    throw failure("Celeritas", "multiple storage array lookups in WorldSlice.prepare");
                }
                storageCall = (MethodInsnNode) instruction;
            }
            if (instruction.getOpcode() == Opcodes.AALOAD && i > 0
                    && instructions[i - 1] instanceof MethodInsnNode
                    && "y".equals(((MethodInsnNode) instructions[i - 1]).name)
                    && "()I".equals(((MethodInsnNode) instructions[i - 1]).desc)) {
                if (arrayLoad != null) {
                    throw failure("Celeritas", "multiple section array loads in WorldSlice.prepare");
                }
                arrayLoad = instruction;
            }
        }
        if (storageCall == null || arrayLoad == null) {
            throw failure("Celeritas", "no storage array section lookup in WorldSlice.prepare");
        }
        method.instructions.remove(storageCall);
        method.instructions.set(arrayLoad, new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "getSection",
                GET_SECTION_DESC, false));
    }

    /** ClonedChunkSection.getChunkSection(Chunk, int): replace the whole lookup with the delegate. */
    private static void rewriteGetChunkSection(ClassNode node) {
        MethodNode method = uniqueMethod(node, "getChunkSection", "Celeritas");
        if (!method.desc.equals(GET_SECTION_DESC)) {
            throw failure("Celeritas", "an unexpected getChunkSection signature " + method.desc);
        }
        InsnList delegate = new InsnList();
        delegate.add(new VarInsnNode(Opcodes.ALOAD, 0));
        delegate.add(new VarInsnNode(Opcodes.ILOAD, 1));
        delegate.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "getSection", GET_SECTION_DESC, false));
        delegate.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.insert(delegate);
    }

    /** CeleritasWorldRenderer: the entity culling bounds hardcode the vanilla height range. */
    private static void rewriteBuildHeights(ClassNode node) {
        replaceBody(uniqueMethod(node, "getMinimumBuildHeight", "Celeritas"), "getMinBuildHeight", "Celeritas");
        replaceBody(uniqueMethod(node, "getMaximumBuildHeight", "Celeritas"), "getMaxBuildHeight", "Celeritas");
    }

    /** getMinimum/MaximumBuildHeight(): replace the whole body with the height-aware value. */
    private static void replaceBody(MethodNode method, String helper, String renderer) {
        if (!method.desc.equals("()I")) {
            throw failure(renderer, "an unexpected " + method.name + " signature " + method.desc);
        }
        InsnList delegate = new InsnList();
        delegate.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, helper, "()I", false));
        delegate.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.insert(delegate);
    }

    /**
     * Actinium DepthsUpdateCompat: prepend an early return delegating to the configured height
     * while it is extended; the original Depths Update logic runs unchanged otherwise.
     */
    private static void rewriteDepthsUpdateCompat(ClassNode node) {
        prependExtendedReturn(uniqueMethod(node, "getMinSection", "Actinium"), "getMinSection", -1);
        prependExtendedReturn(uniqueMethod(node, "getMaxSection", "Actinium"), "getMaxSection", -1);
        prependExtendedReturn(uniqueMethod(node, "getSectionCount", "Actinium"), "getSectionCount", -1);
        prependExtendedReturn(uniqueMethod(node, "getMinBuildHeight", "Actinium"), "getMinBuildHeight", -1);
        prependExtendedReturn(uniqueMethod(node, "toStorageIndex", "Actinium"), "toStorageIndex", 1);
    }

    /** ActiniumWorldRenderer.getMaximumBuildHeight(): prepend the extended-height early return. */
    private static void rewriteActiniumRenderer(ClassNode node) {
        prependExtendedReturn(uniqueMethod(node, "getMaximumBuildHeight", "Actinium"), "getMaxBuildHeight", -1);
    }

    /**
     * Prepends {@code if (CeleritasRenderCompat.useExtendedHeight()) return helper(...);} to the
     * method. {@code argumentVar} is the local holding the helper's single int argument, or -1
     * when the helper takes none.
     */
    private static void prependExtendedReturn(MethodNode method, String helper, int argumentVar) {
        InsnList prepend = new InsnList();
        LabelNode originalBody = new LabelNode();
        prepend.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, "useExtendedHeight", "()Z", false));
        prepend.add(new JumpInsnNode(Opcodes.IFEQ, originalBody));
        if (argumentVar >= 0) {
            prepend.add(new VarInsnNode(Opcodes.ILOAD, argumentVar));
        }
        prepend.add(new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, helper,
                argumentVar >= 0 ? "(I)I" : "()I", false));
        prepend.add(new InsnNode(Opcodes.IRETURN));
        prepend.add(originalBody);
        method.instructions.insert(prepend);
    }

    private static IllegalStateException failure(String renderer, String detail) {
        return new IllegalStateException("Cave Biomes API " + renderer + " transformer found " + detail);
    }
}
