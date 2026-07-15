package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Normalizes direct chunk-section access in legacy placement and retrogen paths. */
public final class LegacyPlacementSectionTransformer implements IClassTransformer {
    private static final String DIMENSIONAL_DOORS = "org.dimdev.ddutils.schem.Schematic";
    private static final String DIMENSIONAL_DOORS_METHOD = "setBlocks";
    private static final String DIMENSIONAL_DOORS_DESC =
            "(Lnet/minecraft/world/World;III)V";

    private static final String LUCKY_BLOCK = "mod.lucky.drop.func.DropFuncBlock";
    private static final String LUCKY_SET_BLOCK = "setBlock";
    private static final String LUCKY_SET_BLOCK_DESC = "(Lnet/minecraft/world/World;"
            + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/nbt/NBTTagCompound;Z)V";
    private static final String LUCKY_SET_TILE = "setTileEntity";
    private static final String LUCKY_SET_TILE_DESC = "(Lnet/minecraft/world/World;"
            + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/nbt/NBTTagCompound;)V";

    private static final String EXTRA_UTILS_QUARRY =
            "com.rwtema.extrautils2.quarry.TileQuarryConvoluted";
    private static final String EXTRA_UTILS_QUARRY_METHOD = "findDiggableBlockInChunk";
    private static final String EXTRA_UTILS_QUARRY_DESC = "(Lcom/rwtema/extrautils2/quarry/"
            + "TileQuarryConvoluted$Digger;III)Z";
    private static final String EXTRA_UTILS_RETROGEN =
            "com.rwtema.extrautils2.worldgen.SingleChunkGen";
    private static final String EXTRA_UTILS_RETROGEN_METHOD = "setBlockState";
    private static final String EXTRA_UTILS_RETROGEN_DESC =
            "(Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/state/IBlockState;)V";

    private static final String NTM_RADIATION = "com.hbm.handler.RadiationSystemNT";
    private static final String NTM_REBUILD = "rebuildChunkPockets";
    private static final String NTM_REBUILD_DESC =
            "(Lnet/minecraft/world/chunk/Chunk;I)V";

    private static final String BLOCK_POS = "net/minecraft/util/math/BlockPos";
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
        if (DIMENSIONAL_DOORS.equals(target)) patchDimensionalDoors(node);
        else if (LUCKY_BLOCK.equals(target)) patchLuckyBlock(node);
        else if (EXTRA_UTILS_QUARRY.equals(target)) patchExtraUtilsQuarry(node);
        else if (EXTRA_UTILS_RETROGEN.equals(target)) patchExtraUtilsRetrogen(node);
        else patchNtmRadiation(node);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String target(String name, String transformedName) {
        if (DIMENSIONAL_DOORS.equals(name) || DIMENSIONAL_DOORS.equals(transformedName)) {
            return DIMENSIONAL_DOORS;
        }
        if (LUCKY_BLOCK.equals(name) || LUCKY_BLOCK.equals(transformedName)) {
            return LUCKY_BLOCK;
        }
        if (EXTRA_UTILS_QUARRY.equals(name) || EXTRA_UTILS_QUARRY.equals(transformedName)) {
            return EXTRA_UTILS_QUARRY;
        }
        if (EXTRA_UTILS_RETROGEN.equals(name) || EXTRA_UTILS_RETROGEN.equals(transformedName)) {
            return EXTRA_UTILS_RETROGEN;
        }
        if (NTM_RADIATION.equals(name) || NTM_RADIATION.equals(transformedName)) {
            return NTM_RADIATION;
        }
        return null;
    }

    private static void patchDimensionalDoors(ClassNode node) {
        MethodNode method = uniqueMethod(node, DIMENSIONAL_DOORS_METHOD,
                DIMENSIONAL_DOORS_DESC);
        int arrayLocal = dimensionalDoorsStorageArrayLocal(method);
        AbstractInsnNode readShift = null;
        AbstractInsnNode writeShift = null;
        int sectionOffsetLocal = -1;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shiftAmount = previousReal(instruction);
            AbstractInsnNode originYLoad = previousReal(shiftAmount);
            AbstractInsnNode arrayLoad = previousReal(originYLoad);
            AbstractInsnNode sliceLoad = nextReal(instruction);
            AbstractInsnNode addition = nextReal(sliceLoad);
            if (shiftAmount.getOpcode() != Opcodes.ICONST_4
                    || !(originYLoad instanceof VarInsnNode)
                    || originYLoad.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) originYLoad).var != 3
                    || !(arrayLoad instanceof VarInsnNode)
                    || arrayLoad.getOpcode() != Opcodes.ALOAD
                    || ((VarInsnNode) arrayLoad).var != arrayLocal
                    || !(sliceLoad instanceof VarInsnNode)
                    || sliceLoad.getOpcode() != Opcodes.ILOAD
                    || addition.getOpcode() != Opcodes.IADD) {
                continue;
            }

            int candidateOffsetLocal = ((VarInsnNode) sliceLoad).var;
            if (sectionOffsetLocal >= 0 && sectionOffsetLocal != candidateOffsetLocal) {
                throw failure(DIMENSIONAL_DOORS,
                        "inconsistent chunk-section offset locals");
            }
            sectionOffsetLocal = candidateOffsetLocal;

            AbstractInsnNode afterAddition = nextReal(addition);
            if (afterAddition.getOpcode() == Opcodes.AALOAD) {
                if (readShift != null) {
                    throw failure(DIMENSIONAL_DOORS, "multiple chunk-section reads");
                }
                readShift = instruction;
            } else if (afterAddition instanceof VarInsnNode
                    && afterAddition.getOpcode() == Opcodes.ALOAD
                    && nextReal(afterAddition).getOpcode() == Opcodes.AASTORE) {
                if (writeShift != null) {
                    throw failure(DIMENSIONAL_DOORS, "multiple chunk-section writes");
                }
                writeShift = instruction;
            }
        }
        if (readShift == null || writeShift == null) {
            throw failure(DIMENSIONAL_DOORS, "expected one chunk-section read and write");
        }
        validateDimensionalDoorsConstructorBase(method, sectionOffsetLocal);
        replaceSectionShift(method, readShift, "sectionIndex");
        replaceSectionShift(method, writeShift, "sectionIndex");
    }

    private static int dimensionalDoorsStorageArrayLocal(MethodNode method) {
        VarInsnNode result = null;
        int getters = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)
                    || !isStorageGetter((MethodInsnNode) instruction)) continue;
            getters++;
            AbstractInsnNode store = nextReal(instruction);
            if (!(store instanceof VarInsnNode) || store.getOpcode() != Opcodes.ASTORE) {
                throw failure(DIMENSIONAL_DOORS, "unexpected chunk storage-array store");
            }
            if (result != null) {
                throw failure(DIMENSIONAL_DOORS, "multiple chunk storage arrays");
            }
            result = (VarInsnNode) store;
        }
        if (getters != 1 || result == null) {
            throw failure(DIMENSIONAL_DOORS, "expected one chunk storage array");
        }
        return result.var;
    }

    private static void validateDimensionalDoorsConstructorBase(MethodNode method,
            int sectionOffsetLocal) {
        int bases = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR
                    || previousReal(instruction).getOpcode() != Opcodes.ICONST_4) continue;
            AbstractInsnNode yLoad = previousReal(previousReal(instruction));
            AbstractInsnNode offsetLoad = nextReal(instruction);
            AbstractInsnNode addition = nextReal(offsetLoad);
            AbstractInsnNode shiftAmount = nextReal(addition);
            AbstractInsnNode shift = nextReal(shiftAmount);
            if (yLoad instanceof VarInsnNode
                    && yLoad.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) yLoad).var == 3
                    && offsetLoad instanceof VarInsnNode
                    && offsetLoad.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) offsetLoad).var == sectionOffsetLocal
                    && addition.getOpcode() == Opcodes.IADD
                    && shiftAmount.getOpcode() == Opcodes.ICONST_4
                    && shift.getOpcode() == Opcodes.ISHL) {
                bases++;
            }
        }
        if (bases != 1) {
            throw failure(DIMENSIONAL_DOORS,
                    "expected one preserved absolute section constructor base");
        }
    }

    private static void patchLuckyBlock(ClassNode node) {
        patchLuckyMethod(uniqueMethod(node, LUCKY_SET_BLOCK, LUCKY_SET_BLOCK_DESC));
        patchLuckyMethod(uniqueMethod(node, LUCKY_SET_TILE, LUCKY_SET_TILE_DESC));
    }

    private static void patchLuckyMethod(MethodNode method) {
        AbstractInsnNode readShift = null;
        AbstractInsnNode writeShift = null;
        AbstractInsnNode constructorBaseShift = null;
        int getters = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode
                    && isStorageGetter((MethodInsnNode) instruction)) {
                getters++;
                AbstractInsnNode posLoad = nextReal(instruction);
                AbstractInsnNode getY = nextReal(posLoad);
                AbstractInsnNode shiftAmount = nextReal(getY);
                AbstractInsnNode shift = nextReal(shiftAmount);
                if (!(posLoad instanceof VarInsnNode)
                        || posLoad.getOpcode() != Opcodes.ALOAD
                        || ((VarInsnNode) posLoad).var != 2
                        || !(getY instanceof MethodInsnNode)
                        || !isBlockPosYGetter((MethodInsnNode) getY)
                        || shiftAmount.getOpcode() != Opcodes.ICONST_4
                        || shift.getOpcode() != Opcodes.ISHR) {
                    throw failure(LUCKY_BLOCK, "unexpected block-position section lookup");
                }
                AbstractInsnNode afterShift = nextReal(shift);
                if (afterShift.getOpcode() == Opcodes.AALOAD) {
                    if (readShift != null) {
                        throw failure(LUCKY_BLOCK, "multiple chunk-section reads in "
                                + method.name + method.desc);
                    }
                    readShift = shift;
                } else if (afterShift instanceof TypeInsnNode
                        && afterShift.getOpcode() == Opcodes.NEW
                        && STORAGE.equals(((TypeInsnNode) afterShift).desc)) {
                    if (writeShift != null) {
                        throw failure(LUCKY_BLOCK, "multiple chunk-section writes in "
                                + method.name + method.desc);
                    }
                    writeShift = shift;
                } else {
                    throw failure(LUCKY_BLOCK, "unknown chunk-section access in "
                            + method.name + method.desc);
                }
            }
            if (instruction.getOpcode() == Opcodes.ISHR
                    && previousReal(instruction).getOpcode() == Opcodes.ICONST_4
                    && nextReal(instruction).getOpcode() == Opcodes.ICONST_4
                    && nextReal(nextReal(instruction)).getOpcode() == Opcodes.ISHL) {
                AbstractInsnNode getY = previousReal(previousReal(instruction));
                if (getY instanceof MethodInsnNode
                        && isBlockPosYGetter((MethodInsnNode) getY)) {
                    if (constructorBaseShift != null) {
                        throw failure(LUCKY_BLOCK, "multiple section constructor bases in "
                                + method.name + method.desc);
                    }
                    constructorBaseShift = instruction;
                }
            }
        }
        if (getters != 2 || readShift == null || writeShift == null
                || constructorBaseShift == null) {
            throw failure(LUCKY_BLOCK, "expected two indexes and one preserved constructor base in "
                    + method.name + method.desc);
        }
        replaceSectionShift(method, readShift, "sectionIndex");
        replaceSectionShift(method, writeShift, "sectionIndex");
    }

    private static void patchExtraUtilsQuarry(ClassNode node) {
        MethodNode method = uniqueMethod(node, EXTRA_UTILS_QUARRY_METHOD,
                EXTRA_UTILS_QUARRY_DESC);
        AbstractInsnNode rawSectionShift = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shiftAmount = previousReal(instruction);
            AbstractInsnNode yLoad = previousReal(shiftAmount);
            AbstractInsnNode arrayLoad = previousReal(yLoad);
            if (shiftAmount.getOpcode() == Opcodes.ICONST_4
                    && yLoad instanceof VarInsnNode
                    && yLoad.getOpcode() == Opcodes.ILOAD
                    && ((VarInsnNode) yLoad).var == 2
                    && arrayLoad instanceof VarInsnNode
                    && arrayLoad.getOpcode() == Opcodes.ALOAD
                    && ((VarInsnNode) arrayLoad).var == 7
                    && nextReal(instruction).getOpcode() == Opcodes.AALOAD) {
                if (rawSectionShift != null) {
                    throw failure(EXTRA_UTILS_QUARRY, "multiple quarry section lookups");
                }
                rawSectionShift = instruction;
            }
        }
        if (rawSectionShift == null) {
            throw failure(EXTRA_UTILS_QUARRY, "no quarry section lookup");
        }
        replaceSectionShift(method, rawSectionShift, "sectionIndex");
    }

    private static void patchExtraUtilsRetrogen(ClassNode node) {
        MethodNode method = uniqueMethod(node, EXTRA_UTILS_RETROGEN_METHOD,
                EXTRA_UTILS_RETROGEN_DESC);
        AbstractInsnNode readShift = null;
        AbstractInsnNode writeShift = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shiftAmount = previousReal(instruction);
            AbstractInsnNode yLoad = previousReal(shiftAmount);
            AbstractInsnNode arrayLoad = previousReal(yLoad);
            if (shiftAmount.getOpcode() != Opcodes.ICONST_4
                    || !(yLoad instanceof VarInsnNode)
                    || yLoad.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) yLoad).var != 5
                    || !(arrayLoad instanceof VarInsnNode)
                    || arrayLoad.getOpcode() != Opcodes.ALOAD
                    || ((VarInsnNode) arrayLoad).var != 8) {
                continue;
            }
            AbstractInsnNode afterShift = nextReal(instruction);
            if (afterShift.getOpcode() == Opcodes.AALOAD) {
                if (readShift != null) {
                    throw failure(EXTRA_UTILS_RETROGEN, "multiple section reads");
                }
                readShift = instruction;
            } else if (afterShift instanceof TypeInsnNode
                    && afterShift.getOpcode() == Opcodes.NEW
                    && STORAGE.equals(((TypeInsnNode) afterShift).desc)) {
                if (writeShift != null) {
                    throw failure(EXTRA_UTILS_RETROGEN, "multiple section writes");
                }
                writeShift = instruction;
            }
        }
        MethodInsnNode constructor = uniqueStorageConstructor(method, EXTRA_UTILS_RETROGEN);
        AbstractInsnNode storeSkyLight = previousReal(constructor);
        AbstractInsnNode mask = previousReal(storeSkyLight);
        AbstractInsnNode maskAmount = previousReal(mask);
        AbstractInsnNode yLoad = previousReal(maskAmount);
        if (storeSkyLight.getOpcode() != Opcodes.ICONST_1
                || mask.getOpcode() != Opcodes.IAND
                || !(maskAmount instanceof IntInsnNode)
                || maskAmount.getOpcode() != Opcodes.BIPUSH
                || ((IntInsnNode) maskAmount).operand != -16
                || !(yLoad instanceof VarInsnNode)
                || yLoad.getOpcode() != Opcodes.ILOAD
                || ((VarInsnNode) yLoad).var != 5) {
            throw failure(EXTRA_UTILS_RETROGEN, "unexpected preserved section constructor base");
        }
        if (readShift == null || writeShift == null) {
            throw failure(EXTRA_UTILS_RETROGEN, "expected one section read and write");
        }
        replaceSectionShift(method, readShift, "sectionIndex");
        replaceSectionShift(method, writeShift, "sectionIndex");
    }

    private static void patchNtmRadiation(ClassNode node) {
        MethodNode method = uniqueMethod(node, NTM_REBUILD, NTM_REBUILD_DESC);
        MethodInsnNode getter = null;
        VarInsnNode absoluteSectionLoad = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)
                    || !isStorageGetter((MethodInsnNode) instruction)) continue;
            if (getter != null) {
                throw failure(NTM_RADIATION, "multiple Minecraft section reads");
            }
            getter = (MethodInsnNode) instruction;
            AbstractInsnNode indexLoad = nextReal(instruction);
            if (!(indexLoad instanceof VarInsnNode)
                    || indexLoad.getOpcode() != Opcodes.ILOAD
                    || ((VarInsnNode) indexLoad).var != 1
                    || nextReal(indexLoad).getOpcode() != Opcodes.AALOAD) {
                throw failure(NTM_RADIATION, "unexpected Minecraft section read");
            }
            absoluteSectionLoad = (VarInsnNode) indexLoad;
        }
        if (getter == null || absoluteSectionLoad == null) {
            throw failure(NTM_RADIATION, "no Minecraft section read");
        }
        method.instructions.insert(absoluteSectionLoad,
                heightCall("sectionIndexFromSectionY"));
    }

    private static MethodInsnNode uniqueStorageConstructor(MethodNode method, String target) {
        MethodInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKESPECIAL
                    && STORAGE.equals(call.owner)
                    && "<init>".equals(call.name) && "(IZ)V".equals(call.desc)) {
                if (result != null) throw failure(target, "multiple section constructors");
                result = call;
            }
        }
        if (result == null) throw failure(target, "no section constructor");
        return result;
    }

    private static boolean isStorageGetter(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && CHUNK.equals(call.owner)
                && STORAGE_ARRAY_DESC.equals(call.desc)
                && ("getBlockStorageArray".equals(call.name)
                    || "func_76587_i".equals(call.name));
    }

    private static boolean isBlockPosYGetter(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && BLOCK_POS.equals(call.owner)
                && "()I".equals(call.desc)
                && ("getY".equals(call.name) || "func_177956_o".equals(call.name));
    }

    private static void replaceSectionShift(MethodNode method,
            AbstractInsnNode shift, String apiMethod) {
        AbstractInsnNode shiftAmount = previousReal(shift);
        method.instructions.set(shiftAmount, heightCall(apiMethod));
        method.instructions.remove(shift);
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
        return new IllegalStateException("Cave Biomes API placement transformer found "
                + detail + " in " + target);
    }
}
