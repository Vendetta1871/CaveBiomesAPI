package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adapts Dynmap's 1.12 Forge bridge to finite worlds whose minimum Y is below zero. */
public final class DynmapTransformer implements IClassTransformer {
    private static final String SNAPSHOT = "org.dynmap.forge_1_12_2.ChunkSnapshot";
    private static final String CHUNK_CACHE = "org.dynmap.forge_1_12_2.ForgeMapChunkCache";
    private static final String ITERATOR = CHUNK_CACHE + "$OurMapIterator";
    private static final String FORGE_WORLD = "org.dynmap.forge_1_12_2.ForgeWorld";
    private static final String HEIGHT_API = "net/celestiald/cavebiomes/api/WorldHeightAPI";
    private static final String COMPAT = "net/celestiald/cavebiomes/core/DynmapCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        String target = target(name, transformedName);
        if (target == null) return basicClass;

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        if (SNAPSHOT.equals(target)) transformSnapshot(node);
        else if (CHUNK_CACHE.equals(target)) transformChunkCache(node);
        else if (ITERATOR.equals(target)) transformIterator(node);
        else transformForgeWorld(node);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String target(String name, String transformedName) {
        for (String candidate : new String[]{SNAPSHOT, CHUNK_CACHE, ITERATOR, FORGE_WORLD}) {
            if (candidate.equals(name) || candidate.equals(transformedName)) return candidate;
        }
        return null;
    }

    private static void transformSnapshot(ClassNode node) {
        int constructorCounts = 0;
        int savedSection = 0;
        for (MethodNode method : node.methods) {
            if ("<init>".equals(method.name)) {
                constructorCounts += replaceSectionCountDivision(method);
                if ("(Lnet/minecraft/nbt/NBTTagCompound;I)V".equals(method.desc)) {
                    savedSection += normalizeSavedSection(method);
                }
            }
        }
        if (constructorCounts != 2 || savedSection != 1) {
            throw failure("ChunkSnapshot", "expected two section counts and one saved-section read, found "
                    + constructorCounts + " and " + savedSection);
        }
        replaceAccessorIndex(uniqueMethod(node, "getBlockType", "(III)Lorg/dynmap/renderer/DynmapBlockState;"));
        replaceAccessorIndex(uniqueMethod(node, "getBlockSkyLight", "(III)I"));
        replaceAccessorIndex(uniqueMethod(node, "getBlockEmittedLight", "(III)I"));
    }

    private static int replaceSectionCountDivision(MethodNode method) {
        int replaced = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.IDIV) continue;
            AbstractInsnNode divisor = previousReal(instruction);
            AbstractInsnNode dividend = previousReal(divisor);
            if (isIntConstant(divisor, 16) && dividend instanceof VarInsnNode
                    && dividend.getOpcode() == Opcodes.ILOAD) {
                method.instructions.set(dividend, call(HEIGHT_API, "getSectionCount", "()I"));
                method.instructions.remove(divisor);
                method.instructions.remove(instruction);
                replaced++;
            }
        }
        return replaced;
    }

    private static int normalizeSavedSection(MethodNode method) {
        int replaced = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ("net/minecraft/nbt/NBTTagCompound".equals(call.owner)
                    && ("getByte".equals(call.name) || "func_74771_c".equals(call.name))
                    && "(Ljava/lang/String;)B".equals(call.desc)) {
                method.instructions.insert(call, call(COMPAT, "safeSectionIndexFromSectionY", "(I)I"));
                replaced++;
            }
        }
        return replaced;
    }

    private static void replaceAccessorIndex(MethodNode method) {
        int replaced = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shift = previousReal(instruction);
            AbstractInsnNode value = previousReal(shift);
            if (shift.getOpcode() == Opcodes.ICONST_4 && value instanceof VarInsnNode
                    && value.getOpcode() == Opcodes.ILOAD && ((VarInsnNode) value).var == 2) {
                method.instructions.insertBefore(shift,
                        call(COMPAT, "safeSectionIndex", "(I)I"));
                method.instructions.remove(shift);
                method.instructions.remove(instruction);
                replaced++;
            }
        }
        if (replaced != 1) {
            throw failure("ChunkSnapshot." + method.name,
                    "expected one block-Y section shift, found " + replaced);
        }
    }

    private static void transformChunkCache(ClassNode node) {
        MethodNode setChunks = uniqueMethod(node, "setChunks",
                "(Lorg/dynmap/forge_1_12_2/ForgeWorld;Ljava/util/List;)V");
        int counts = replaceWorldHeightShift(setChunks);
        MethodNode empty = uniqueMethod(node, "isEmptySection", "(III)Z");
        empty.instructions.insert(new VarInsnNode(Opcodes.ISTORE, 2));
        empty.instructions.insert(call(COMPAT, "safeSectionIndexFromSectionY", "(I)I"));
        empty.instructions.insert(new VarInsnNode(Opcodes.ILOAD, 2));
        if (counts != 1) {
            throw failure("ForgeMapChunkCache", "expected one section-count shift, found " + counts);
        }
    }

    private static int replaceWorldHeightShift(MethodNode method) {
        int replaced = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shift = previousReal(instruction);
            AbstractInsnNode field = previousReal(shift);
            if (shift.getOpcode() == Opcodes.ICONST_4 && field instanceof FieldInsnNode
                    && "worldheight".equals(((FieldInsnNode) field).name)) {
                AbstractInsnNode object = previousReal(field);
                method.instructions.set(object, call(HEIGHT_API, "getSectionCount", "()I"));
                method.instructions.remove(field);
                method.instructions.remove(shift);
                method.instructions.remove(instruction);
                replaced++;
            }
        }
        return replaced;
    }

    private static void transformIterator(ClassNode node) {
        MethodNode constructor = uniqueMethod(node, "<init>",
                "(Lorg/dynmap/forge_1_12_2/ForgeMapChunkCache;III)V");
        int zeroMinimums = 0;
        for (AbstractInsnNode instruction : constructor.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.PUTFIELD && instruction instanceof FieldInsnNode
                    && "ymin".equals(((FieldInsnNode) instruction).name)) {
                AbstractInsnNode zero = previousReal(instruction);
                if (zero.getOpcode() != Opcodes.ICONST_0) {
                    throw failure("OurMapIterator", "unexpected ymin initializer");
                }
                constructor.instructions.set(zero, call(HEIGHT_API, "getMinY", "()I"));
                zeroMinimums++;
            }
        }
        replaceSingleZeroBound(uniqueMethod(node, "initialize", "(III)V"), Opcodes.IFLT, Opcodes.IF_ICMPLT);
        replaceSingleZeroBound(uniqueMethod(node, "setY", "(I)V"), Opcodes.IFLT, Opcodes.IF_ICMPLT);
        replaceSingleZeroBound(uniqueMethod(node, "getBlockTypeAt",
                "(Lorg/dynmap/utils/BlockStep;)Lorg/dynmap/renderer/DynmapBlockState;"),
                Opcodes.IFLE, Opcodes.IF_ICMPLE);
        replaceYDecrementBound(uniqueMethod(node, "stepPosition", "(Lorg/dynmap/utils/BlockStep;)V"));
        normalizeBlockKey(uniqueMethod(node, "getBlockKey", "()J"));
        if (zeroMinimums != 1) {
            throw failure("OurMapIterator", "expected one ymin initializer, found " + zeroMinimums);
        }
    }

    private static void replaceSingleZeroBound(MethodNode method, int oldOpcode, int newOpcode) {
        JumpInsnNode match = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof JumpInsnNode && instruction.getOpcode() == oldOpcode) {
                if (match != null) throw failure("OurMapIterator." + method.name, "multiple minimum-Y bounds");
                match = (JumpInsnNode) instruction;
            }
        }
        if (match == null) throw failure("OurMapIterator." + method.name, "no minimum-Y bound");
        method.instructions.insertBefore(match, call(HEIGHT_API, "getMinY", "()I"));
        match.setOpcode(newOpcode);
    }

    private static void replaceYDecrementBound(MethodNode method) {
        int replaced = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof JumpInsnNode) || instruction.getOpcode() != Opcodes.IFGE) continue;
            AbstractInsnNode field = previousReal(instruction);
            if (field instanceof FieldInsnNode && field.getOpcode() == Opcodes.GETFIELD
                    && "y".equals(((FieldInsnNode) field).name)) {
                method.instructions.insertBefore(instruction, call(HEIGHT_API, "getMinY", "()I"));
                ((JumpInsnNode) instruction).setOpcode(Opcodes.IF_ICMPGE);
                replaced++;
            }
        }
        if (replaced != 1) {
            throw failure("OurMapIterator.stepPosition",
                    "expected one descending minimum-Y bound, found " + replaced);
        }
    }

    private static void normalizeBlockKey(MethodNode method) {
        int stride = 0;
        int offset = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.IMUL) {
                AbstractInsnNode worldHeight = previousReal(instruction);
                if (isField(worldHeight, Opcodes.GETFIELD, "worldheight")) {
                    method.instructions.insertBefore(instruction, loadIteratorField("ymin"));
                    method.instructions.insertBefore(instruction, new InsnNode(Opcodes.ISUB));
                    stride++;
                }
            } else if (instruction.getOpcode() == Opcodes.IADD) {
                AbstractInsnNode y = previousReal(instruction);
                if (isField(y, Opcodes.GETFIELD, "y")) {
                    method.instructions.insertBefore(instruction, loadIteratorField("ymin"));
                    method.instructions.insertBefore(instruction, new InsnNode(Opcodes.ISUB));
                    offset++;
                }
            }
        }
        if (stride != 1 || offset != 1) {
            throw failure("OurMapIterator.getBlockKey", "expected one height stride and Y offset, found "
                    + stride + " and " + offset);
        }
    }

    private static InsnList loadIteratorField(String name) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "org/dynmap/forge_1_12_2/ForgeMapChunkCache$OurMapIterator", name, "I"));
        return instructions;
    }

    private static boolean isField(AbstractInsnNode instruction, int opcode, String name) {
        return instruction instanceof FieldInsnNode && instruction.getOpcode() == opcode
                && name.equals(((FieldInsnNode) instruction).name);
    }

    private static void transformForgeWorld(ClassNode node) {
        MethodNode constructor = uniqueMethod(node, "<init>", "(Ljava/lang/String;IIZZLjava/lang/String;)V");
        int replaced = 0;
        for (AbstractInsnNode instruction : constructor.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKESPECIAL
                    && "org/dynmap/DynmapWorld".equals(call.owner)
                    && "<init>".equals(call.name) && "(Ljava/lang/String;II)V".equals(call.desc)) {
                constructor.instructions.insertBefore(call, call(HEIGHT_API, "getMinY", "()I"));
                call.desc = "(Ljava/lang/String;III)V";
                replaced++;
            }
        }
        if (replaced != 1) {
            throw failure("ForgeWorld", "expected one DynmapWorld constructor call, found " + replaced);
        }

        MethodNode loaded = uniqueMethod(node, "setWorldLoaded", "(Lnet/minecraft/world/World;)V");
        InsnList update = new InsnList();
        update.add(new VarInsnNode(Opcodes.ALOAD, 0));
        update.add(new VarInsnNode(Opcodes.ALOAD, 1));
        update.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World",
                "func_72800_K", "()I", false));
        update.add(call(HEIGHT_API, "getMinY", "()I"));
        update.add(new VarInsnNode(Opcodes.ALOAD, 1));
        update.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World",
                "func_181545_F", "()I", false));
        update.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "org/dynmap/DynmapWorld",
                "updateWorldHeights", "(III)V", false));
        loaded.instructions.insert(update);
    }

    private static MethodNode uniqueMethod(ClassNode node, String name, String desc) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                if (result != null) throw failure(node.name, "multiple " + name + desc + " methods");
                result = method;
            }
        }
        if (result == null) throw failure(node.name, "no " + name + desc + " method");
        return result;
    }

    private static MethodInsnNode call(String owner, String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, false);
    }

    private static boolean isIntConstant(AbstractInsnNode instruction, int value) {
        if (instruction instanceof IntInsnNode) return ((IntInsnNode) instruction).operand == value;
        return value >= -1 && value <= 5 && instruction.getOpcode() == Opcodes.ICONST_0 + value;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() >= 0) return instruction;
        }
        throw failure("bytecode", "no preceding instruction");
    }

    private static IllegalStateException failure(String target, String detail) {
        return new IllegalStateException("Cave Biomes API Dynmap transformer found " + detail + " in " + target);
    }
}
