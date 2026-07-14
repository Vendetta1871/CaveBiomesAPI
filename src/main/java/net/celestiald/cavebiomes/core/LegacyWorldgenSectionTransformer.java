package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Normalizes fixed-height section indexes in legacy dimension generators. */
public final class LegacyWorldgenSectionTransformer implements IClassTransformer {
    private static final String GALAXY_SPACE = "galaxyspace.systems.SolarSystem.planets."
            + "kuiperbelt.dimension.ChunkProviderKuiperBelt";
    private static final String GALAXY_METHOD = "generateSkylightMap";
    private static final String GALAXY_DESC = "(Lnet/minecraft/world/chunk/Chunk;II)V";

    private static final String TWILIGHT_FOREST =
            "twilightforest.world.ChunkGeneratorTFBase";
    private static final String TWILIGHT_METHOD = "fillChunk";
    private static final String TWILIGHT_DESC = "(Lnet/minecraft/world/chunk/Chunk;"
            + "Lnet/minecraft/world/chunk/ChunkPrimer;)V";

    private static final String MYSTCRAFT =
            "com.xcompwiz.mystcraft.world.profiling.ChunkProfiler";
    private static final String MYSTCRAFT_METHOD = "profileChunk";
    private static final String MYSTCRAFT_DESC = "(Lnet/minecraft/world/chunk/Chunk;"
            + "Lcom/xcompwiz/mystcraft/world/profiling/ChunkProfiler$ChunkProfileData;"
            + "Ljava/util/Map;)V";

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
        if (GALAXY_SPACE.equals(target)) patchGalaxySpace(node);
        else if (TWILIGHT_FOREST.equals(target)) patchTwilightForest(node);
        else patchMystcraft(node);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String target(String name, String transformedName) {
        if (GALAXY_SPACE.equals(name) || GALAXY_SPACE.equals(transformedName)) {
            return GALAXY_SPACE;
        }
        if (TWILIGHT_FOREST.equals(name) || TWILIGHT_FOREST.equals(transformedName)) {
            return TWILIGHT_FOREST;
        }
        if (MYSTCRAFT.equals(name) || MYSTCRAFT.equals(transformedName)) {
            return MYSTCRAFT;
        }
        return null;
    }

    private static void patchGalaxySpace(ClassNode node) {
        MethodNode method = uniqueMethod(node, GALAXY_METHOD, GALAXY_DESC);
        MethodInsnNode constructor = uniqueStorageConstructor(method, GALAXY_SPACE);
        AbstractInsnNode skyLightLoad = previousReal(constructor);
        AbstractInsnNode yShift = previousReal(skyLightLoad);
        AbstractInsnNode shiftAmount = previousReal(yShift);
        AbstractInsnNode logicalIndexLoad = previousReal(shiftAmount);
        if (!(skyLightLoad instanceof VarInsnNode)
                || skyLightLoad.getOpcode() != Opcodes.ILOAD
                || yShift.getOpcode() != Opcodes.ISHL
                || shiftAmount.getOpcode() != Opcodes.ICONST_4
                || !(logicalIndexLoad instanceof VarInsnNode)
                || logicalIndexLoad.getOpcode() != Opcodes.ILOAD) {
            throw failure(GALAXY_SPACE, "unexpected section constructor y base");
        }
        int logicalIndexVariable = ((VarInsnNode) logicalIndexLoad).var;

        VarInsnNode logicalReadIndex = null;
        VarInsnNode logicalWriteIndex = null;
        AbstractInsnNode lightSectionShift = null;
        int getters = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)
                    || !isStorageGetter((MethodInsnNode) instruction)) continue;
            getters++;
            AbstractInsnNode indexLoad = nextReal(instruction);
            if (!(indexLoad instanceof VarInsnNode)
                    || indexLoad.getOpcode() != Opcodes.ILOAD) {
                throw failure(GALAXY_SPACE, "storage getter without an integer index");
            }
            AbstractInsnNode afterIndex = nextReal(indexLoad);
            int indexVariable = ((VarInsnNode) indexLoad).var;
            if (indexVariable == logicalIndexVariable
                    && afterIndex.getOpcode() == Opcodes.AALOAD) {
                if (logicalReadIndex != null) {
                    throw failure(GALAXY_SPACE, "multiple logical section reads");
                }
                logicalReadIndex = (VarInsnNode) indexLoad;
            } else if (indexVariable == logicalIndexVariable
                    && afterIndex instanceof TypeInsnNode
                    && afterIndex.getOpcode() == Opcodes.NEW
                    && STORAGE.equals(((TypeInsnNode) afterIndex).desc)) {
                if (logicalWriteIndex != null) {
                    throw failure(GALAXY_SPACE, "multiple logical section writes");
                }
                logicalWriteIndex = (VarInsnNode) indexLoad;
            } else if (afterIndex.getOpcode() == Opcodes.ICONST_4) {
                AbstractInsnNode candidateShift = nextReal(afterIndex);
                if (candidateShift.getOpcode() != Opcodes.ISHR
                        || nextReal(candidateShift).getOpcode() != Opcodes.AALOAD) {
                    throw failure(GALAXY_SPACE, "unexpected light section lookup");
                }
                if (lightSectionShift != null) {
                    throw failure(GALAXY_SPACE, "multiple light section lookups");
                }
                lightSectionShift = candidateShift;
            } else {
                throw failure(GALAXY_SPACE, "unknown chunk-section access shape");
            }
        }
        if (getters != 3 || logicalReadIndex == null || logicalWriteIndex == null
                || lightSectionShift == null) {
            throw failure(GALAXY_SPACE, "expected two generator and one light section access");
        }

        method.instructions.insert(logicalReadIndex,
                heightCall("sectionIndexFromSectionY"));
        method.instructions.insert(logicalWriteIndex,
                heightCall("sectionIndexFromSectionY"));
        replaceSectionShift(method, lightSectionShift, "sectionIndex");
    }

    private static void patchTwilightForest(ClassNode node) {
        MethodNode method = uniqueMethod(node, TWILIGHT_METHOD, TWILIGHT_DESC);
        MethodInsnNode constructor = uniqueStorageConstructor(method, TWILIGHT_FOREST);
        AbstractInsnNode skyLightLoad = previousReal(constructor);
        AbstractInsnNode yShift = previousReal(skyLightLoad);
        AbstractInsnNode shiftAmount = previousReal(yShift);
        AbstractInsnNode indexLoad = previousReal(shiftAmount);
        if (!(skyLightLoad instanceof VarInsnNode)
                || skyLightLoad.getOpcode() != Opcodes.ILOAD
                || yShift.getOpcode() != Opcodes.ISHL
                || shiftAmount.getOpcode() != Opcodes.ICONST_4
                || !(indexLoad instanceof VarInsnNode)
                || indexLoad.getOpcode() != Opcodes.ILOAD) {
            throw failure(TWILIGHT_FOREST, "unexpected section constructor y base");
        }
        int sectionIndexVariable = ((VarInsnNode) indexLoad).var;
        AbstractInsnNode rawSectionShift = uniqueStoredSectionShift(method,
                TWILIGHT_FOREST, sectionIndexVariable);

        replaceSectionShift(method, rawSectionShift, "sectionIndex");
        method.instructions.set(shiftAmount, heightCall("sectionYBase"));
        method.instructions.remove(yShift);
    }

    private static void patchMystcraft(ClassNode node) {
        MethodNode method = uniqueMethod(node, MYSTCRAFT_METHOD, MYSTCRAFT_DESC);
        MethodInsnNode getter = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode
                    && isStorageGetter((MethodInsnNode) instruction)) {
                if (getter != null) {
                    throw failure(MYSTCRAFT, "multiple chunk-section getters");
                }
                getter = (MethodInsnNode) instruction;
            }
        }
        if (getter == null || !(nextReal(getter) instanceof VarInsnNode)
                || nextReal(getter).getOpcode() != Opcodes.ASTORE) {
            throw failure(MYSTCRAFT, "unexpected chunk-section getter");
        }

        AbstractInsnNode rawSectionShift = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shiftAmount = previousReal(instruction);
            AbstractInsnNode yLoad = previousReal(shiftAmount);
            AbstractInsnNode indexStore = nextReal(instruction);
            if (shiftAmount.getOpcode() == Opcodes.ICONST_4
                    && yLoad instanceof VarInsnNode
                    && yLoad.getOpcode() == Opcodes.ILOAD
                    && indexStore instanceof VarInsnNode
                    && indexStore.getOpcode() == Opcodes.ISTORE) {
                if (rawSectionShift != null) {
                    throw failure(MYSTCRAFT, "multiple stored section shifts");
                }
                rawSectionShift = instruction;
            }
        }
        if (rawSectionShift == null) {
            throw failure(MYSTCRAFT, "no stored raw section shift");
        }
        replaceSectionShift(method, rawSectionShift, "sectionIndex");
    }

    private static AbstractInsnNode uniqueStoredSectionShift(MethodNode method,
            String target, int sectionIndexVariable) {
        AbstractInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.ISHR) continue;
            AbstractInsnNode shiftAmount = previousReal(instruction);
            AbstractInsnNode yLoad = previousReal(shiftAmount);
            AbstractInsnNode indexStore = nextReal(instruction);
            if (shiftAmount.getOpcode() != Opcodes.ICONST_4
                    || !(yLoad instanceof VarInsnNode)
                    || yLoad.getOpcode() != Opcodes.ILOAD
                    || !(indexStore instanceof VarInsnNode)
                    || indexStore.getOpcode() != Opcodes.ISTORE
                    || ((VarInsnNode) indexStore).var != sectionIndexVariable) {
                continue;
            }
            if (result != null) throw failure(target, "multiple stored section shifts");
            result = instruction;
        }
        if (result == null) throw failure(target, "no stored raw section shift");
        return result;
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
        return new IllegalStateException("Cave Biomes API worldgen transformer found "
                + detail + " in " + target);
    }
}
