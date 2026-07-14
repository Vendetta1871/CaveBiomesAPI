package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
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
    private static final String ASMODEUS_LIGHT =
            "asmodeuscore.core.utils.worldengine.additions.WE_ChunkSmartLight";
    private static final String ASMODEUS_LIGHT_INTERNAL =
            "asmodeuscore/core/utils/worldengine/additions/WE_ChunkSmartLight";
    private static final String ASMODEUS_METHOD_MCP = "generateSkylightMap";
    private static final String ASMODEUS_METHOD_SRG = "func_76603_b";
    private static final String ASMODEUS_DESC = "()V";

    private static final String MO_CREATURES = "drzhark.mocreatures.MoCDespawner";
    private static final String MO_CREATURES_METHOD = "getLightFromNeighbors";
    private static final String MO_CREATURES_DESC =
            "(Lnet/minecraft/world/chunk/Chunk;III)I";

    private static final String AE_CACHED_PLANE = "appeng.spatial.CachedPlane";
    private static final String AE_CACHED_PLANE_INTERNAL = "appeng/spatial/CachedPlane";
    private static final String AE_COLUMN = "appeng.spatial.CachedPlane$Column";
    private static final String AE_COLUMN_CONSTRUCTOR = "(Lappeng/spatial/CachedPlane;"
            + "Lnet/minecraft/world/chunk/Chunk;IIII)V";
    private static final String AE_BLOCK_DATA =
            "Lappeng/spatial/CachedPlane$BlockStorageData;";
    private static final String AE_BLOCK_DATA_METHOD = "(I" + AE_BLOCK_DATA + ")V";
    private static final String AE_PLANE_CONSTRUCTOR =
            "(Lnet/minecraft/world/World;IIIIII)V";

    private static final String MR_TJP_WORLD = "mrtjp.core.world.WorldLib$";
    private static final String MR_TJP_METHOD = "uncheckedSetBlock";
    private static final String MR_TJP_DESC = "(Lnet/minecraft/world/World;"
            + "Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/state/IBlockState;)V";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String STORAGE =
            "net/minecraft/world/chunk/storage/ExtendedBlockStorage";
    private static final String STORAGE_ARRAY_DESC = "()[L" + STORAGE + ";";
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
        else if (ASTEROIDS.equals(target)) patchAsteroids(node);
        else if (ASMODEUS_LIGHT.equals(target)) patchAsmodeusLight(node);
        else if (MO_CREATURES.equals(target)) patchMoCreatures(node);
        else if (AE_CACHED_PLANE.equals(target)) patchAeCachedPlane(node);
        else if (AE_COLUMN.equals(target)) patchAeColumn(node);
        else patchMrTjp(node);

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
        if (ASMODEUS_LIGHT.equals(name) || ASMODEUS_LIGHT.equals(transformedName)) {
            return ASMODEUS_LIGHT;
        }
        if (MO_CREATURES.equals(name) || MO_CREATURES.equals(transformedName)) {
            return MO_CREATURES;
        }
        if (AE_CACHED_PLANE.equals(name) || AE_CACHED_PLANE.equals(transformedName)) {
            return AE_CACHED_PLANE;
        }
        if (AE_COLUMN.equals(name) || AE_COLUMN.equals(transformedName)) {
            return AE_COLUMN;
        }
        if (MR_TJP_WORLD.equals(name) || MR_TJP_WORLD.equals(transformedName)) {
            return MR_TJP_WORLD;
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
        patchSingleSectionRead(method, ASTEROIDS, CHUNK, 13);
    }

    private static void patchAsmodeusLight(ClassNode node) {
        MethodNode method = uniqueMethod(node, ASMODEUS_METHOD_MCP,
                ASMODEUS_METHOD_SRG, ASMODEUS_DESC);
        patchSingleSectionRead(method, ASMODEUS_LIGHT,
                ASMODEUS_LIGHT_INTERNAL, 5);
    }

    private static void patchMoCreatures(ClassNode node) {
        MethodNode method = uniqueMethod(node, MO_CREATURES_METHOD, MO_CREATURES_DESC);
        patchSingleSectionRead(method, MO_CREATURES, CHUNK, 2);
    }

    private static void patchAeCachedPlane(ClassNode node) {
        MethodNode constructor = uniqueMethod(node, "<init>", AE_PLANE_CONSTRUCTOR);
        AbstractInsnNode verticalShift = null;
        for (AbstractInsnNode instruction : constructor.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHL) continue;
            AbstractInsnNode sectionSum = previousReal(instruction);
            AbstractInsnNode loopLoad = previousReal(sectionSum);
            AbstractInsnNode minimumLoad = previousReal(loopLoad);
            AbstractInsnNode one = previousReal(minimumLoad);
            AbstractInsnNode verticalGet = previousReal(one);
            AbstractInsnNode duplicate = previousReal(verticalGet);
            AbstractInsnNode thisLoad = previousReal(duplicate);
            AbstractInsnNode or = nextReal(instruction);
            AbstractInsnNode verticalPut = nextReal(or);
            if (sectionSum.getOpcode() != Opcodes.IADD
                    || !(loopLoad instanceof VarInsnNode)
                    || loopLoad.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) loopLoad).var != 16
                    || !(minimumLoad instanceof VarInsnNode)
                    || minimumLoad.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) minimumLoad).var != 10
                    || one.getOpcode() != Opcodes.ICONST_1
                    || !isVerticalBitsField(verticalGet, Opcodes.GETFIELD)
                    || duplicate.getOpcode() != Opcodes.DUP
                    || !(thisLoad instanceof VarInsnNode)
                    || thisLoad.getOpcode() != Opcodes.ALOAD
                    || ((VarInsnNode) thisLoad).var != 0
                    || or.getOpcode() != Opcodes.IOR
                    || !isVerticalBitsField(verticalPut, Opcodes.PUTFIELD)) {
                continue;
            }
            if (verticalShift != null) {
                throw failure(AE_CACHED_PLANE, "multiple vertical packet-mask shifts");
            }
            verticalShift = instruction;
        }
        if (verticalShift == null) {
            throw failure(AE_CACHED_PLANE, "expected one vertical packet-mask shift");
        }
        constructor.instructions.insert(previousReal(verticalShift),
                heightCall("sectionIndexFromSectionY"));
    }

    private static boolean isVerticalBitsField(AbstractInsnNode instruction, int opcode) {
        if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != opcode) {
            return false;
        }
        FieldInsnNode field = (FieldInsnNode) instruction;
        return AE_CACHED_PLANE_INTERNAL.equals(field.owner)
                && "verticalBits".equals(field.name) && "I".equals(field.desc);
    }

    private static void patchAeColumn(ClassNode node) {
        MethodNode constructor = uniqueMethod(node, "<init>", AE_COLUMN_CONSTRUCTOR);
        int storageVariable = storageArrayLocal(constructor, AE_COLUMN + ".<init>");
        if (storageVariable != 7) {
            throw failure(AE_COLUMN, "unexpected constructor storage-array local " + storageVariable);
        }

        int sectionYVariable = aeColumnSectionYLocal(constructor);
        MethodInsnNode storageConstructor = uniqueStorageConstructor(constructor, AE_COLUMN);
        AbstractInsnNode storageNew = previousStorageNew(storageConstructor, AE_COLUMN);
        AbstractInsnNode afterConstructor = nextReal(storageConstructor);
        if (afterConstructor.getOpcode() != Opcodes.DUP_X2
                || nextReal(afterConstructor).getOpcode() != Opcodes.AASTORE) {
            throw failure(AE_COLUMN, "unexpected constructor storage write");
        }

        VarInsnNode readIndex = null;
        VarInsnNode writeIndex = null;
        for (AbstractInsnNode instruction : constructor.instructions.toArray()) {
            if (!(instruction instanceof VarInsnNode)
                    || instruction.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) instruction).var != sectionYVariable) {
                continue;
            }
            AbstractInsnNode arrayLoad = previousReal(instruction);
            if (!(arrayLoad instanceof VarInsnNode)
                    || arrayLoad.getOpcode() != Opcodes.ALOAD
                    || ((VarInsnNode) arrayLoad).var != storageVariable) {
                continue;
            }
            AbstractInsnNode next = nextReal(instruction);
            if (next.getOpcode() == Opcodes.AALOAD) {
                if (readIndex != null) throw failure(AE_COLUMN, "multiple constructor section reads");
                readIndex = (VarInsnNode) instruction;
            } else if (next == storageNew) {
                if (writeIndex != null) throw failure(AE_COLUMN, "multiple constructor section writes");
                writeIndex = (VarInsnNode) instruction;
            }
        }
        if (readIndex == null || writeIndex == null) {
            throw failure(AE_COLUMN, "expected one constructor section read and write");
        }

        AbstractInsnNode yBaseShift = null;
        for (AbstractInsnNode instruction : constructor.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHL) continue;
            AbstractInsnNode amount = previousReal(instruction);
            AbstractInsnNode sectionY = previousReal(amount);
            if (amount.getOpcode() == Opcodes.ICONST_4
                    && sectionY instanceof VarInsnNode
                    && sectionY.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) sectionY).var == sectionYVariable
                    && isBetween(storageNew, instruction, storageConstructor)) {
                if (yBaseShift != null) throw failure(AE_COLUMN, "multiple section y-base shifts");
                yBaseShift = instruction;
            }
        }
        if (yBaseShift == null) throw failure(AE_COLUMN, "expected one section y-base shift");

        constructor.instructions.insert(readIndex, heightCall("sectionIndexFromSectionY"));
        constructor.instructions.insert(writeIndex, heightCall("sectionIndexFromSectionY"));
        AbstractInsnNode shiftAmount = previousReal(yBaseShift);
        constructor.instructions.set(shiftAmount, heightCall("sectionIndexFromSectionY"));
        constructor.instructions.set(yBaseShift, heightCall("sectionYBase"));

        patchAeColumnWorldYMethod(node, "setBlockIDWithMetadata", AE_BLOCK_DATA_METHOD, 3);
        patchAeColumnWorldYMethod(node, "fillData", AE_BLOCK_DATA_METHOD, 3);
        patchAeColumnWorldYMethod(node, "doNotSkip", "(I)Z", 2);
    }

    private static int aeColumnSectionYLocal(MethodNode constructor) {
        VarInsnNode result = null;
        for (AbstractInsnNode instruction : constructor.instructions.toArray()) {
            if (!(instruction instanceof VarInsnNode)
                    || instruction.getOpcode() != Opcodes.ISTORE) continue;
            AbstractInsnNode sum = previousReal(instruction);
            AbstractInsnNode minimum = previousReal(sum);
            AbstractInsnNode loop = previousReal(minimum);
            if (sum.getOpcode() == Opcodes.IADD
                    && minimum instanceof VarInsnNode
                    && minimum.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) minimum).var == 5
                    && loop instanceof VarInsnNode
                    && loop.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) loop).var == 8) {
                if (result != null) throw failure(AE_COLUMN, "multiple section-Y locals");
                result = (VarInsnNode) instruction;
            }
        }
        if (result == null) throw failure(AE_COLUMN, "no section-Y local");
        if (result.var != 9) throw failure(AE_COLUMN, "unexpected section-Y local " + result.var);
        return result.var;
    }

    private static void patchAeColumnWorldYMethod(ClassNode node, String name,
            String desc, int expectedStorageVariable) {
        MethodNode method = uniqueMethod(node, name, desc);
        String target = AE_COLUMN + "." + name;
        int storageVariable = storageArrayLocal(method, target);
        if (storageVariable != expectedStorageVariable) {
            throw failure(target, "unexpected storage-array local " + storageVariable);
        }
        patchSingleLocalArrayRead(method, target, storageVariable, 1);
    }

    private static void patchMrTjp(ClassNode node) {
        MethodNode method = uniqueMethod(node, MR_TJP_METHOD, MR_TJP_DESC);
        int storageVariable = storageArrayLocal(method, MR_TJP_WORLD + "." + MR_TJP_METHOD);
        if (storageVariable != 5) {
            throw failure(MR_TJP_WORLD, "unexpected storage-array local " + storageVariable);
        }

        MethodInsnNode storageConstructor = uniqueStorageConstructor(method, MR_TJP_WORLD);
        AbstractInsnNode storageNew = previousStorageNew(storageConstructor, MR_TJP_WORLD);
        if (nextReal(storageConstructor).getOpcode() != Opcodes.AASTORE) {
            throw failure(MR_TJP_WORLD, "unexpected storage write after constructor");
        }

        int reads = 0;
        int writes = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shiftAmount = previousReal(instruction);
            AbstractInsnNode yLoad = previousReal(shiftAmount);
            AbstractInsnNode arrayLoad = previousReal(yLoad);
            if (shiftAmount.getOpcode() != Opcodes.ICONST_4
                    || !(yLoad instanceof VarInsnNode)
                    || yLoad.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) yLoad).var != 7
                    || !(arrayLoad instanceof VarInsnNode)
                    || arrayLoad.getOpcode() != Opcodes.ALOAD
                    || ((VarInsnNode) arrayLoad).var != storageVariable) {
                continue;
            }
            AbstractInsnNode next = nextReal(instruction);
            if (next.getOpcode() == Opcodes.AALOAD) reads++;
            else if (next == storageNew) writes++;
            else throw failure(MR_TJP_WORLD, "unexpected section-array operation");
            replaceSectionShift(method, instruction);
        }
        if (reads != 3 || writes != 1) {
            throw failure(MR_TJP_WORLD, "expected three section reads and one write, found "
                    + reads + " reads and " + writes + " writes");
        }
    }

    private static void patchSingleSectionRead(MethodNode method, String target,
            String getterOwner, int yVariable) {
        AbstractInsnNode sectionShift = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shiftAmount = previousReal(instruction);
            AbstractInsnNode yLoad = previousReal(shiftAmount);
            AbstractInsnNode getterInstruction = previousReal(yLoad);
            AbstractInsnNode arrayRead = nextReal(instruction);
            if (shiftAmount.getOpcode() != Opcodes.ICONST_4
                    || !(yLoad instanceof VarInsnNode)
                    || yLoad.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) yLoad).var != yVariable
                    || !(getterInstruction instanceof MethodInsnNode)
                    || arrayRead.getOpcode() != Opcodes.AALOAD) {
                continue;
            }

            MethodInsnNode getter = (MethodInsnNode) getterInstruction;
            if (getter.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !getterOwner.equals(getter.owner)
                    || !STORAGE_ARRAY_DESC.equals(getter.desc)
                    || !("getBlockStorageArray".equals(getter.name)
                        || "func_76587_i".equals(getter.name))) {
                continue;
            }
            if (sectionShift != null) {
                throw failure(target, "multiple raw section reads");
            }
            sectionShift = instruction;
        }
        if (sectionShift == null) {
            throw failure(target, "expected one raw section read");
        }
        replaceSectionShift(method, sectionShift);
    }

    private static void patchSingleLocalArrayRead(MethodNode method, String target,
            int storageVariable, int yVariable) {
        AbstractInsnNode sectionShift = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shiftAmount = previousReal(instruction);
            AbstractInsnNode yLoad = previousReal(shiftAmount);
            AbstractInsnNode arrayLoad = previousReal(yLoad);
            if (shiftAmount.getOpcode() == Opcodes.ICONST_4
                    && yLoad instanceof VarInsnNode
                    && yLoad.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) yLoad).var == yVariable
                    && arrayLoad instanceof VarInsnNode
                    && arrayLoad.getOpcode() == Opcodes.ALOAD
                    && ((VarInsnNode) arrayLoad).var == storageVariable
                    && nextReal(instruction).getOpcode() == Opcodes.AALOAD) {
                if (sectionShift != null) throw failure(target, "multiple raw section reads");
                sectionShift = instruction;
            }
        }
        if (sectionShift == null) throw failure(target, "expected one raw section read");
        replaceSectionShift(method, sectionShift);
    }

    private static int storageArrayLocal(MethodNode method, String target) {
        VarInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !CHUNK.equals(call.owner)
                    || !STORAGE_ARRAY_DESC.equals(call.desc)
                    || !("getBlockStorageArray".equals(call.name)
                        || "func_76587_i".equals(call.name))) {
                continue;
            }
            AbstractInsnNode store = nextReal(call);
            if (!(store instanceof VarInsnNode) || store.getOpcode() != Opcodes.ASTORE) {
                throw failure(target, "storage getter is not stored in a local");
            }
            if (result != null) throw failure(target, "multiple storage-array locals");
            result = (VarInsnNode) store;
        }
        if (result == null) throw failure(target, "no storage-array local");
        return result.var;
    }

    private static MethodInsnNode uniqueStorageConstructor(MethodNode method, String target) {
        MethodInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKESPECIAL
                    && STORAGE.equals(call.owner)
                    && "<init>".equals(call.name) && "(IZ)V".equals(call.desc)) {
                if (result != null) throw failure(target, "multiple storage constructors");
                result = call;
            }
        }
        if (result == null) throw failure(target, "no storage constructor");
        return result;
    }

    private static AbstractInsnNode previousStorageNew(MethodInsnNode constructor,
            String target) {
        TypeInsnNode result = null;
        for (AbstractInsnNode instruction = constructor.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction instanceof TypeInsnNode
                    && instruction.getOpcode() == Opcodes.NEW
                    && STORAGE.equals(((TypeInsnNode) instruction).desc)) {
                if (result != null) throw failure(target, "multiple storage allocations");
                result = (TypeInsnNode) instruction;
            }
        }
        if (result == null || nextReal(result).getOpcode() != Opcodes.DUP) {
            throw failure(target, "unexpected storage allocation");
        }
        return result;
    }

    private static boolean isBetween(AbstractInsnNode start, AbstractInsnNode candidate,
            AbstractInsnNode end) {
        for (AbstractInsnNode instruction = start.getNext(); instruction != null && instruction != end;
                instruction = instruction.getNext()) {
            if (instruction == candidate) return true;
        }
        return false;
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

    private static MethodNode uniqueMethod(ClassNode node, String mcpName,
            String srgName, String desc) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (desc.equals(method.desc)
                    && (mcpName.equals(method.name) || srgName.equals(method.name))) {
                if (result != null) {
                    throw failure(node.name, "multiple " + mcpName + "/" + srgName + desc);
                }
                result = method;
            }
        }
        if (result == null) {
            throw failure(node.name, "no " + mcpName + "/" + srgName + desc);
        }
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
