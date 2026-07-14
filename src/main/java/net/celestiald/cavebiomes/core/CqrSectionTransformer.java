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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Normalizes CQR's direct chunk-section access without widening its legacy scan bands. */
public final class CqrSectionTransformer implements IClassTransformer {
    private static final String BLOCK_PLACING =
            "team.cqr.cqrepoured.util.BlockPlacingHelper";
    private static final String BLOCK_POS = "team.cqr.cqrepoured.util.BlockPosUtil";
    private static final String BLOCK_LIGHT = "team.cqr.cqrepoured.world.structure.generation."
            + "generation.util.BlockLightUtil";
    private static final String SKY_LIGHT = "team.cqr.cqrepoured.world.structure.generation."
            + "generation.util.SkyLightUtil";

    private static final String SECTION_PLACEMENT_DESC = "(Lnet/minecraft/world/World;III"
            + "Lteam/cqr/cqrepoured/world/structure/generation/generation/GeneratableDungeon;"
            + "Lteam/cqr/cqrepoured/util/BlockPlacingHelper$IBlockInfo;)Z";
    private static final String BLOCK_POS_PLACEMENT_DESC = "(Lnet/minecraft/world/World;"
            + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;"
            + "Lnet/minecraft/tileentity/TileEntity;I"
            + "Lteam/cqr/cqrepoured/world/structure/generation/generation/GeneratableDungeon;)Z";
    private static final String FOR_EACH_DESC = "(Lnet/minecraft/world/World;IIIIIIZZ"
            + "Lteam/cqr/cqrepoured/util/BlockPosUtil$BlockInfoConsumer;)V";
    private static final String BLOCK_LIGHT_DESC =
            "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V";
    private static final String SKY_LIGHT_DESC = "(Lnet/minecraft/world/World;"
            + "Lteam/cqr/cqrepoured/world/structure/generation/generation/ChunkInfo;)V";

    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String STORAGE =
            "net/minecraft/world/chunk/storage/ExtendedBlockStorage";
    private static final String STORAGE_ARRAY_DESC = "()[L" + STORAGE + ";";
    private static final String BLOCK_POS_INTERNAL = "net/minecraft/util/math/BlockPos";
    private static final String MUTABLE_POS_INTERNAL =
            "net/minecraft/util/math/BlockPos$MutableBlockPos";
    private static final String HEIGHT_API =
            "net/celestiald/cavebiomes/api/WorldHeightAPI";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        String target = target(name, transformedName);
        if (target == null) return basicClass;

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        if (BLOCK_PLACING.equals(target)) patchBlockPlacing(node);
        else if (BLOCK_POS.equals(target)) patchBlockPosUtil(node);
        else if (BLOCK_LIGHT.equals(target)) patchBlockLight(node);
        else patchSkyLight(node);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String target(String name, String transformedName) {
        for (String candidate : new String[]{BLOCK_PLACING, BLOCK_POS, BLOCK_LIGHT, SKY_LIGHT}) {
            if (candidate.equals(name) || candidate.equals(transformedName)) return candidate;
        }
        return null;
    }

    private static void patchBlockPlacing(ClassNode node) {
        patchSectionPlacement(uniqueMethod(node, "setBlockStates", SECTION_PLACEMENT_DESC));
        patchBlockPosPlacement(uniqueMethod(node, "setBlockState", BLOCK_POS_PLACEMENT_DESC));
    }

    private static void patchSectionPlacement(MethodNode method) {
        VarInsnNode readIndex = null;
        VarInsnNode valueWriteIndex = null;
        VarInsnNode nullWriteIndex = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof VarInsnNode)
                    || instruction.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) instruction).var != 2
                    || !isStorageGetter(previousReal(instruction))) {
                continue;
            }
            AbstractInsnNode next = nextReal(instruction);
            if (next.getOpcode() == Opcodes.AALOAD) {
                if (readIndex != null) throw failure(BLOCK_PLACING, "multiple section reads");
                readIndex = (VarInsnNode) instruction;
            } else if (next instanceof VarInsnNode && next.getOpcode() == Opcodes.ALOAD
                    && ((VarInsnNode) next).var == 7
                    && nextReal(next).getOpcode() == Opcodes.AASTORE) {
                if (valueWriteIndex != null) {
                    throw failure(BLOCK_PLACING, "multiple section value writes");
                }
                valueWriteIndex = (VarInsnNode) instruction;
            } else if (next.getOpcode() == Opcodes.ACONST_NULL
                    && nextReal(next).getOpcode() == Opcodes.AASTORE) {
                if (nullWriteIndex != null) {
                    throw failure(BLOCK_PLACING, "multiple section cleanup writes");
                }
                nullWriteIndex = (VarInsnNode) instruction;
            } else {
                throw failure(BLOCK_PLACING, "unexpected absolute section-Y array operation");
            }
        }
        if (readIndex == null || valueWriteIndex == null || nullWriteIndex == null) {
            throw failure(BLOCK_PLACING, "expected one section read and two section writes");
        }
        validateSectionYBase(method, BLOCK_PLACING + ".setBlockStates", 2);
        method.instructions.insert(readIndex, heightCall("sectionIndexFromSectionY"));
        method.instructions.insert(valueWriteIndex, heightCall("sectionIndexFromSectionY"));
        method.instructions.insert(nullWriteIndex, heightCall("sectionIndexFromSectionY"));
    }

    private static void patchBlockPosPlacement(MethodNode method) {
        AbstractInsnNode readShift = null;
        AbstractInsnNode writeShift = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode amount = previousReal(instruction);
            AbstractInsnNode getY = previousReal(amount);
            AbstractInsnNode posLoad = previousReal(getY);
            AbstractInsnNode getter = previousReal(posLoad);
            if (amount.getOpcode() != Opcodes.ICONST_4
                    || !isGetY(getY, BLOCK_POS_INTERNAL)
                    || !(posLoad instanceof VarInsnNode)
                    || posLoad.getOpcode() != Opcodes.ALOAD
                    || ((VarInsnNode) posLoad).var != 1
                    || !isStorageGetter(getter)) {
                continue;
            }
            AbstractInsnNode next = nextReal(instruction);
            if (next.getOpcode() == Opcodes.AALOAD) {
                if (readShift != null) throw failure(BLOCK_PLACING, "multiple BlockPos reads");
                readShift = instruction;
            } else if (next instanceof VarInsnNode && next.getOpcode() == Opcodes.ALOAD
                    && ((VarInsnNode) next).var == 7
                    && nextReal(next).getOpcode() == Opcodes.AASTORE) {
                if (writeShift != null) throw failure(BLOCK_PLACING, "multiple BlockPos writes");
                writeShift = instruction;
            } else {
                throw failure(BLOCK_PLACING, "unexpected BlockPos section-array operation");
            }
        }
        if (readShift == null || writeShift == null) {
            throw failure(BLOCK_PLACING, "expected one BlockPos section read and write");
        }
        validateBlockYBase(method, BLOCK_PLACING + ".setBlockState", BLOCK_POS_INTERNAL, 1);
        replaceWorldYShift(method, readShift);
        replaceWorldYShift(method, writeShift);
    }

    private static void patchBlockPosUtil(ClassNode node) {
        MethodNode method = uniqueMethod(node, "forEach", FOR_EACH_DESC);
        validateLegacyClamp(method, 2, 0, "max");
        validateLegacyClamp(method, 5, 255, "min");
        int storageVariable = storageArrayLocal(method, BLOCK_POS + ".forEach");
        if (storageVariable != 20) {
            throw failure(BLOCK_POS, "unexpected storage-array local " + storageVariable);
        }

        VarInsnNode sectionIndex = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof VarInsnNode)
                    || instruction.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) instruction).var != 21) continue;
            AbstractInsnNode arrayLoad = previousReal(instruction);
            if (arrayLoad instanceof VarInsnNode && arrayLoad.getOpcode() == Opcodes.ALOAD
                    && ((VarInsnNode) arrayLoad).var == storageVariable
                    && nextReal(instruction).getOpcode() == Opcodes.AALOAD) {
                if (sectionIndex != null) throw failure(BLOCK_POS, "multiple section-loop reads");
                sectionIndex = (VarInsnNode) instruction;
            }
        }
        if (sectionIndex == null) throw failure(BLOCK_POS, "no section-loop read");
        validateSectionLoop(method, BLOCK_POS, 11, 14, 21, 1);
        if (countSectionYReconstruction(method, 21) != 2) {
            throw failure(BLOCK_POS, "unexpected world-Y reconstruction count");
        }
        method.instructions.insert(sectionIndex, heightCall("sectionIndexFromSectionY"));
    }

    private static void patchBlockLight(ClassNode node) {
        MethodNode method = uniqueMethod(node, "relightBlock", BLOCK_LIGHT_DESC);
        AbstractInsnNode readShift = null;
        AbstractInsnNode writeShift = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode amount = previousReal(instruction);
            AbstractInsnNode getY = previousReal(amount);
            AbstractInsnNode mutable = previousReal(getY);
            AbstractInsnNode getter = previousReal(mutable);
            if (amount.getOpcode() != Opcodes.ICONST_4
                    || !isGetY(getY, MUTABLE_POS_INTERNAL)
                    || !isMutableField(mutable, BLOCK_LIGHT)
                    || !isStorageGetter(getter)) {
                continue;
            }
            AbstractInsnNode next = nextReal(instruction);
            if (next.getOpcode() == Opcodes.AALOAD) {
                if (readShift != null) throw failure(BLOCK_LIGHT, "multiple section reads");
                readShift = instruction;
            } else if (next instanceof VarInsnNode && next.getOpcode() == Opcodes.ALOAD
                    && ((VarInsnNode) next).var == 13
                    && nextReal(next).getOpcode() == Opcodes.AASTORE) {
                if (writeShift != null) throw failure(BLOCK_LIGHT, "multiple section writes");
                writeShift = instruction;
            } else {
                throw failure(BLOCK_LIGHT, "unexpected section-array operation");
            }
        }
        if (readShift == null || writeShift == null) {
            throw failure(BLOCK_LIGHT, "expected one section read and write");
        }
        validateBlockYBase(method, BLOCK_LIGHT + ".relightBlock", MUTABLE_POS_INTERNAL, -1);
        replaceWorldYShift(method, readShift);
        replaceWorldYShift(method, writeShift);
    }

    private static void patchSkyLight(ClassNode node) {
        MethodNode method = uniqueMethod(node, "checkSkyLight", SKY_LIGHT_DESC);
        VarInsnNode index = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof VarInsnNode)
                    || instruction.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) instruction).var != 3
                    || !isStorageGetter(previousReal(instruction))
                    || nextReal(instruction).getOpcode() != Opcodes.AALOAD) {
                continue;
            }
            if (index != null) throw failure(SKY_LIGHT, "multiple section-loop reads");
            index = (VarInsnNode) instruction;
        }
        if (index == null) throw failure(SKY_LIGHT, "no section-loop read");
        validateTopMarkedLoop(method);
        if (countSectionYReconstruction(method, 3) != 2) {
            throw failure(SKY_LIGHT, "unexpected world-Y reconstruction count");
        }
        method.instructions.insert(index, heightCall("sectionIndexFromSectionY"));
    }

    private static void validateSectionYBase(MethodNode method, String target, int sectionVariable) {
        MethodInsnNode constructor = uniqueStorageConstructor(method, target);
        TypeInsnNode allocation = uniqueStorageAllocation(method, constructor, target);
        int matches = 0;
        for (AbstractInsnNode instruction = allocation; instruction != constructor;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.ISHL) continue;
            AbstractInsnNode amount = previousReal(instruction);
            AbstractInsnNode sectionY = previousReal(amount);
            if (amount.getOpcode() == Opcodes.ICONST_4
                    && sectionY instanceof VarInsnNode
                    && sectionY.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) sectionY).var == sectionVariable) {
                matches++;
            }
        }
        if (matches != 1) throw failure(target, "expected one preserved sectionY << 4 base");
    }

    private static void validateBlockYBase(MethodNode method, String target,
            String getYOwner, int posVariable) {
        MethodInsnNode constructor = uniqueStorageConstructor(method, target);
        TypeInsnNode allocation = uniqueStorageAllocation(method, constructor, target);
        int matches = 0;
        for (AbstractInsnNode instruction = allocation; instruction != constructor;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode amount = previousReal(instruction);
            AbstractInsnNode getY = previousReal(amount);
            AbstractInsnNode source = previousReal(getY);
            AbstractInsnNode leftAmount = nextReal(instruction);
            AbstractInsnNode leftShift = nextReal(leftAmount);
            boolean sourceMatches = posVariable < 0
                    ? isMutableField(source, BLOCK_LIGHT)
                    : source instanceof VarInsnNode && source.getOpcode() == Opcodes.ALOAD
                        && ((VarInsnNode) source).var == posVariable;
            if (amount.getOpcode() == Opcodes.ICONST_4
                    && isGetY(getY, getYOwner) && sourceMatches
                    && leftAmount.getOpcode() == Opcodes.ICONST_4
                    && leftShift.getOpcode() == Opcodes.ISHL) {
                matches++;
            }
        }
        if (matches != 1) throw failure(target, "expected one preserved posY >> 4 << 4 base");
    }

    private static void validateLegacyClamp(MethodNode method, int variable,
            int constant, String mathMethod) {
        int matches = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !"java/lang/Math".equals(call.owner)
                    || !mathMethod.equals(call.name) || !"(II)I".equals(call.desc)) {
                continue;
            }
            AbstractInsnNode value = previousReal(call);
            AbstractInsnNode load = previousReal(value);
            AbstractInsnNode store = nextReal(call);
            boolean constantMatches = constant == 0
                    ? value.getOpcode() == Opcodes.ICONST_0
                    : value instanceof IntInsnNode && value.getOpcode() == Opcodes.SIPUSH
                        && ((IntInsnNode) value).operand == constant;
            if (constantMatches
                    && load instanceof VarInsnNode && load.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) load).var == variable
                    && store instanceof VarInsnNode && store.getOpcode() == Opcodes.ISTORE
                    && ((VarInsnNode) store).var == variable) {
                matches++;
            }
        }
        if (matches != 1) {
            throw failure(BLOCK_POS, "expected legacy Y clamp for local " + variable);
        }
    }

    private static void validateSectionLoop(MethodNode method, String target,
            int minimumVariable, int maximumVariable, int loopVariable, int increment) {
        int starts = 0;
        int increments = 0;
        int limits = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof VarInsnNode && instruction.getOpcode() == Opcodes.ISTORE
                    && ((VarInsnNode) instruction).var == loopVariable) {
                AbstractInsnNode minimum = previousReal(instruction);
                if (minimum instanceof VarInsnNode && minimum.getOpcode() == Opcodes.ILOAD
                        && ((VarInsnNode) minimum).var == minimumVariable) starts++;
            } else if (instruction instanceof IincInsnNode
                    && ((IincInsnNode) instruction).var == loopVariable
                    && ((IincInsnNode) instruction).incr == increment) {
                increments++;
            } else if (instruction.getOpcode() == Opcodes.IF_ICMPGT) {
                AbstractInsnNode maximum = previousReal(instruction);
                AbstractInsnNode loop = previousReal(maximum);
                if (maximum instanceof VarInsnNode && maximum.getOpcode() == Opcodes.ILOAD
                        && ((VarInsnNode) maximum).var == maximumVariable
                        && loop instanceof VarInsnNode && loop.getOpcode() == Opcodes.ILOAD
                        && ((VarInsnNode) loop).var == loopVariable) limits++;
            }
        }
        if (starts != 1 || increments != 1 || limits != 1) {
            throw failure(target, "unexpected absolute section-loop shape");
        }
    }

    private static void validateTopMarkedLoop(MethodNode method) {
        int starts = 0;
        int decrements = 0;
        int limits = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof VarInsnNode && instruction.getOpcode() == Opcodes.ISTORE
                    && ((VarInsnNode) instruction).var == 3) {
                AbstractInsnNode callInstruction = previousReal(instruction);
                if (callInstruction instanceof MethodInsnNode
                        && "topMarked".equals(((MethodInsnNode) callInstruction).name)
                        && "()I".equals(((MethodInsnNode) callInstruction).desc)) starts++;
            } else if (instruction instanceof IincInsnNode
                    && ((IincInsnNode) instruction).var == 3
                    && ((IincInsnNode) instruction).incr == -1) {
                decrements++;
            } else if (instruction.getOpcode() == Opcodes.IFLT) {
                AbstractInsnNode load = previousReal(instruction);
                if (load instanceof VarInsnNode && load.getOpcode() == Opcodes.ILOAD
                        && ((VarInsnNode) load).var == 3) limits++;
            }
        }
        if (starts != 1 || decrements != 1 || limits != 1) {
            throw failure(SKY_LIGHT, "unexpected legacy top-marked loop");
        }
    }

    private static int countSectionYReconstruction(MethodNode method, int sectionVariable) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHL) continue;
            AbstractInsnNode amount = previousReal(instruction);
            AbstractInsnNode section = previousReal(amount);
            if (amount.getOpcode() == Opcodes.ICONST_4
                    && section instanceof VarInsnNode && section.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) section).var == sectionVariable) count++;
        }
        return count;
    }

    private static int storageArrayLocal(MethodNode method, String target) {
        VarInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!isStorageGetter(instruction)) continue;
            AbstractInsnNode store = nextReal(instruction);
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
            if (call.getOpcode() == Opcodes.INVOKESPECIAL && STORAGE.equals(call.owner)
                    && "<init>".equals(call.name) && "(IZ)V".equals(call.desc)) {
                if (result != null) throw failure(target, "multiple storage constructors");
                result = call;
            }
        }
        if (result == null) throw failure(target, "no storage constructor");
        return result;
    }

    private static TypeInsnNode uniqueStorageAllocation(MethodNode method,
            MethodInsnNode constructor, String target) {
        TypeInsnNode result = null;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != constructor; instruction = instruction.getNext()) {
            if (instruction instanceof TypeInsnNode && instruction.getOpcode() == Opcodes.NEW
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

    private static boolean isStorageGetter(AbstractInsnNode instruction) {
        if (!(instruction instanceof MethodInsnNode)) return false;
        MethodInsnNode call = (MethodInsnNode) instruction;
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && CHUNK.equals(call.owner)
                && STORAGE_ARRAY_DESC.equals(call.desc)
                && ("getBlockStorageArray".equals(call.name)
                    || "func_76587_i".equals(call.name));
    }

    private static boolean isGetY(AbstractInsnNode instruction, String owner) {
        if (!(instruction instanceof MethodInsnNode)) return false;
        MethodInsnNode call = (MethodInsnNode) instruction;
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && owner.equals(call.owner)
                && "()I".equals(call.desc)
                && ("getY".equals(call.name) || "func_177956_o".equals(call.name));
    }

    private static boolean isMutableField(AbstractInsnNode instruction, String owner) {
        if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != Opcodes.GETSTATIC) {
            return false;
        }
        FieldInsnNode field = (FieldInsnNode) instruction;
        return owner.replace('.', '/').equals(field.owner) && "MUTABLE1".equals(field.name)
                && ("L" + MUTABLE_POS_INTERNAL + ";").equals(field.desc);
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

    private static void replaceWorldYShift(MethodNode method, AbstractInsnNode shift) {
        AbstractInsnNode amount = previousReal(shift);
        method.instructions.set(amount, heightCall("sectionIndex"));
        method.instructions.remove(shift);
    }

    private static MethodInsnNode heightCall(String method) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HEIGHT_API, method, "(I)I", false);
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
        return new IllegalStateException("Cave Biomes API CQR transformer found "
                + detail + " in " + target);
    }
}
