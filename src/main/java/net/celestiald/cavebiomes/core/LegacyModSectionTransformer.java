package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Normalizes direct chunk-section indexing in known 1.12 mod implementations. */
public final class LegacyModSectionTransformer implements IClassTransformer {
    private static final String WARP_DRIVE = "cr0s.warpdrive.FastSetBlockState";
    private static final String WARP_METHOD = "chunk_setBlockState";
    private static final String WARP_DESC = "(Lnet/minecraft/world/chunk/Chunk;"
            + "Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/state/IBlockState;)"
            + "Lnet/minecraft/block/state/IBlockState;";

    private static final String ASTEROIDS = "micdoodle8.mods.galacticraft.planets."
            + "asteroids.world.gen.ChunkProviderAsteroids";
    private static final String ASTEROIDS_METHOD = "generateSkylightMap";
    private static final String ASTEROIDS_DESC =
            "(Lnet/minecraft/world/chunk/Chunk;II)V";
    private static final String STORAGE =
            "net/minecraft/world/chunk/storage/ExtendedBlockStorage";
    private static final String HEIGHT_API =
            "net/celestiald/cavebiomes/api/WorldHeightAPI";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        String target = target(name, transformedName);
        if (target == null) return basicClass;

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        if (WARP_DRIVE.equals(target)) patchWarpDrive(node);
        else patchAsteroids(node);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String target(String name, String transformedName) {
        if (WARP_DRIVE.equals(name) || WARP_DRIVE.equals(transformedName)) {
            return WARP_DRIVE;
        }
        if (ASTEROIDS.equals(name) || ASTEROIDS.equals(transformedName)) {
            return ASTEROIDS;
        }
        return null;
    }

    private static void patchWarpDrive(ClassNode node) {
        MethodNode method = uniqueMethod(node, WARP_METHOD, WARP_DESC);
        AbstractInsnNode readShift = null;
        AbstractInsnNode writeShift = null;
        int arrayVariable = -1;
        int yVariable = -1;

        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shiftAmount = previousReal(instruction);
            AbstractInsnNode yLoad = previousReal(shiftAmount);
            AbstractInsnNode arrayLoad = previousReal(yLoad);
            if (shiftAmount.getOpcode() != Opcodes.ICONST_4
                    || !(yLoad instanceof VarInsnNode)
                    || yLoad.getOpcode() != Opcodes.ILOAD
                    || !(arrayLoad instanceof VarInsnNode)
                    || arrayLoad.getOpcode() != Opcodes.ALOAD) {
                continue;
            }

            AbstractInsnNode next = nextReal(instruction);
            boolean readsArray = next.getOpcode() == Opcodes.AALOAD;
            boolean writesArray = next instanceof VarInsnNode
                    && next.getOpcode() == Opcodes.ALOAD
                    && nextReal(next).getOpcode() == Opcodes.AASTORE;
            if (!readsArray && !writesArray) continue;

            int candidateArray = ((VarInsnNode) arrayLoad).var;
            int candidateY = ((VarInsnNode) yLoad).var;
            if ((arrayVariable != -1 && arrayVariable != candidateArray)
                    || (yVariable != -1 && yVariable != candidateY)) {
                throw failure(WARP_DRIVE, "inconsistent section-index locals");
            }
            arrayVariable = candidateArray;
            yVariable = candidateY;
            if (readsArray) {
                if (readShift != null) throw failure(WARP_DRIVE, "multiple raw section reads");
                readShift = instruction;
            } else {
                if (writeShift != null) throw failure(WARP_DRIVE, "multiple raw section writes");
                writeShift = instruction;
            }
        }

        if (readShift == null || writeShift == null) {
            throw failure(WARP_DRIVE, "expected one raw section read and write");
        }
        replaceSectionShift(method, readShift);
        replaceSectionShift(method, writeShift);
    }

    private static void replaceSectionShift(MethodNode method, AbstractInsnNode shift) {
        AbstractInsnNode shiftAmount = previousReal(shift);
        method.instructions.set(shiftAmount, heightCall("sectionIndex"));
        method.instructions.remove(shift);
    }

    private static void patchAsteroids(ClassNode node) {
        MethodNode method = uniqueMethod(node, ASTEROIDS_METHOD, ASTEROIDS_DESC);
        MethodInsnNode storageConstructor = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESPECIAL
                        && STORAGE.equals(call.owner)
                        && "<init>".equals(call.name) && "(IZ)V".equals(call.desc)) {
                    if (storageConstructor != null) {
                        throw failure(ASTEROIDS, "multiple section constructors");
                    }
                    storageConstructor = call;
                }
            }
        }
        if (storageConstructor == null) {
            throw failure(ASTEROIDS, "no section constructor");
        }

        AbstractInsnNode skylightLoad = previousReal(storageConstructor);
        AbstractInsnNode yShift = previousReal(skylightLoad);
        AbstractInsnNode shiftAmount = previousReal(yShift);
        AbstractInsnNode indexLoad = previousReal(shiftAmount);
        if (!(skylightLoad instanceof VarInsnNode)
                || skylightLoad.getOpcode() != Opcodes.ILOAD
                || yShift.getOpcode() != Opcodes.ISHL
                || shiftAmount.getOpcode() != Opcodes.ICONST_4
                || !(indexLoad instanceof VarInsnNode)
                || indexLoad.getOpcode() != Opcodes.ILOAD) {
            throw failure(ASTEROIDS, "unexpected section y-base expression");
        }
        int indexVariable = ((VarInsnNode) indexLoad).var;

        JumpInsnNode loopComparison = null;
        AbstractInsnNode loopLimit = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof JumpInsnNode)
                    || instruction.getOpcode() != Opcodes.IF_ICMPGE) continue;
            AbstractInsnNode candidateLimit = previousReal(instruction);
            AbstractInsnNode candidateIndex = previousReal(candidateLimit);
            if (candidateLimit instanceof IntInsnNode
                    && candidateLimit.getOpcode() == Opcodes.BIPUSH
                    && ((IntInsnNode) candidateLimit).operand == 16
                    && candidateIndex instanceof VarInsnNode
                    && candidateIndex.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) candidateIndex).var == indexVariable) {
                if (loopComparison != null) {
                    throw failure(ASTEROIDS, "multiple vanilla section-loop limits");
                }
                loopComparison = (JumpInsnNode) instruction;
                loopLimit = candidateLimit;
            }
        }
        if (loopComparison == null) {
            throw failure(ASTEROIDS, "no vanilla section-loop limit");
        }

        VarInsnNode loopInitialize = null;
        AbstractInsnNode loopStart = null;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != loopComparison; instruction = instruction.getNext()) {
            if (instruction instanceof VarInsnNode
                    && instruction.getOpcode() == Opcodes.ISTORE
                    && ((VarInsnNode) instruction).var == indexVariable
                    && previousReal(instruction).getOpcode() == Opcodes.ICONST_0) {
                if (loopInitialize != null) {
                    throw failure(ASTEROIDS, "multiple vanilla section-loop starts");
                }
                loopInitialize = (VarInsnNode) instruction;
                loopStart = previousReal(instruction);
            }
        }
        if (loopInitialize == null) {
            throw failure(ASTEROIDS, "no vanilla section-loop start");
        }

        int increments = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof IincInsnNode
                    && ((IincInsnNode) instruction).var == indexVariable
                    && ((IincInsnNode) instruction).incr == 1) {
                increments++;
            }
        }
        if (increments < 1) {
            throw failure(ASTEROIDS, "section loop does not increment its index");
        }

        method.instructions.insert(loopStart, heightCall("sectionIndex"));
        IntInsnNode worldYLimit = new IntInsnNode(Opcodes.SIPUSH, 256);
        method.instructions.set(loopLimit, worldYLimit);
        method.instructions.insert(worldYLimit, heightCall("sectionIndex"));
        method.instructions.set(shiftAmount, heightCall("sectionYBase"));
        method.instructions.remove(yShift);
    }

    private static MethodNode uniqueMethod(ClassNode node, String name, String desc) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                if (result != null) throw failure(node.name, "multiple " + name + desc);
                result = method;
            }
        }
        if (result == null) throw failure(node.name, "no " + name + desc);
        return result;
    }

    private static MethodInsnNode heightCall(String method) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HEIGHT_API,
                method, "(I)I", false);
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() >= 0) return instruction;
        }
        throw failure("bytecode", "no preceding instruction");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getNext(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) return instruction;
        }
        throw failure("bytecode", "no following instruction");
    }

    private static IllegalStateException failure(String target, String detail) {
        return new IllegalStateException("Cave Biomes API compatibility transformer found "
                + detail + " in " + target);
    }
}
