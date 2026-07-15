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

/** Normalizes direct entity-list storage indices in known third-party code. */
public final class LegacyEntityStorageTransformer implements IClassTransformer {
    private static final String AUTO_ROCKET =
            "micdoodle8.mods.galacticraft.api.prefab.entity.EntityAutoRocket";
    private static final String BETWEENLANDS_SPAWNER =
            "thebetweenlands.common.world.biome.spawning.AreaMobSpawner";
    private static final String BETWEENLANDS_POPULATE_DESC = "(Lnet/minecraft/world/World;"
            + "Lnet/minecraft/util/math/ChunkPos;ZZZZIIIIF)I";
    private static final String COMPACT_MACHINE_PREVIEW =
            "org.dave.compactmachines3.gui.machine.widgets.WidgetMachinePreview";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String ENTITY_LISTS =
            "()[Lnet/minecraft/util/ClassInheritanceMultiMap;";
    private static final String HEIGHT_API =
            "net/celestiald/cavebiomes/api/WorldHeightAPI";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        String target = target(name, transformedName);
        if (basicClass == null || target == null) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        int patches = 0;
        for (MethodNode method : node.methods) {
            if (AUTO_ROCKET.equals(target)
                    && ("onUpdate".equals(method.name) || "func_70071_h_".equals(method.name))
                    && "()V".equals(method.desc)) {
                patches += patchRocketSectionIndex(method);
            } else if (BETWEENLANDS_SPAWNER.equals(target)
                    && "populateChunk".equals(method.name)
                    && BETWEENLANDS_POPULATE_DESC.equals(method.desc)) {
                patches += patchBetweenlandsSectionIndex(method);
            } else if (COMPACT_MACHINE_PREVIEW.equals(target)
                    && "renderEntities".equals(method.name)
                    && "()V".equals(method.desc)) {
                patches += patchCompactMachineSectionIndex(method);
            }
        }
        if (patches != 1) {
            throw new IllegalStateException("Cave Biomes API expected one entity-section read "
                    + "in " + target + ", found " + patches);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static int patchRocketSectionIndex(MethodNode method) {
        int patches = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode listsCall = (MethodInsnNode) instruction;
            if (listsCall.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !CHUNK.equals(listsCall.owner)
                    || !ENTITY_LISTS.equals(listsCall.desc)) {
                continue;
            }

            AbstractInsnNode ownerLoad = nextReal(listsCall);
            AbstractInsnNode sectionRead = nextReal(ownerLoad);
            AbstractInsnNode arrayRead = nextReal(sectionRead);
            if (!(sectionRead instanceof FieldInsnNode)
                    || sectionRead.getOpcode() != Opcodes.GETFIELD
                    || !"I".equals(((FieldInsnNode) sectionRead).desc)
                    || !("chunkCoordY".equals(((FieldInsnNode) sectionRead).name)
                            || "field_70162_ai".equals(((FieldInsnNode) sectionRead).name))
                    || arrayRead.getOpcode() != Opcodes.AALOAD) {
                throw new IllegalStateException("Cave Biomes API found an unexpected Galacticraft "
                        + "rocket entity-section read");
            }

            method.instructions.insertBefore(arrayRead, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HEIGHT_API,
                    "sectionIndexFromSectionY",
                    "(I)I",
                    false));
            patches++;
        }
        return patches;
    }

    private static int patchBetweenlandsSectionIndex(MethodNode method) {
        int patches = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode getY = (MethodInsnNode) instruction;
            if (getY.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"net/minecraft/util/math/BlockPos".equals(getY.owner)
                    || !"()I".equals(getY.desc)
                    || !("getY".equals(getY.name) || "func_177956_o".equals(getY.name))) {
                continue;
            }
            AbstractInsnNode divisor = nextReal(getY);
            AbstractInsnNode division = nextReal(divisor);
            if (!(divisor instanceof IntInsnNode)
                    || divisor.getOpcode() != Opcodes.BIPUSH
                    || ((IntInsnNode) divisor).operand != 16
                    || division.getOpcode() != Opcodes.IDIV) {
                continue;
            }
            method.instructions.remove(divisor);
            method.instructions.set(division, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HEIGHT_API,
                    "sectionIndex",
                    "(I)I",
                    false));
            patches++;
        }
        return patches;
    }

    private static int patchCompactMachineSectionIndex(MethodNode method) {
        int patches = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode listsCall = (MethodInsnNode) instruction;
            if (listsCall.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !CHUNK.equals(listsCall.owner)
                    || !ENTITY_LISTS.equals(listsCall.desc)) {
                continue;
            }
            AbstractInsnNode section = nextReal(listsCall);
            AbstractInsnNode arrayRead = nextReal(section);
            if (section.getOpcode() != Opcodes.ICONST_2
                    || arrayRead.getOpcode() != Opcodes.AALOAD) {
                throw new IllegalStateException("Cave Biomes API found an unexpected Compact "
                        + "Machines preview entity-section read");
            }
            method.instructions.insert(section, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HEIGHT_API,
                    "sectionIndexFromSectionY",
                    "(I)I",
                    false));
            patches++;
        }
        return patches;
    }

    private static String target(String name, String transformedName) {
        if (matches(AUTO_ROCKET, name, transformedName)) {
            return AUTO_ROCKET;
        }
        if (matches(BETWEENLANDS_SPAWNER, name, transformedName)) {
            return BETWEENLANDS_SPAWNER;
        }
        if (matches(COMPACT_MACHINE_PREVIEW, name, transformedName)) {
            return COMPACT_MACHINE_PREVIEW;
        }
        return null;
    }

    private static boolean matches(String target, String name, String transformedName) {
        return target.equals(name) || target.equals(transformedName);
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getNext(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) {
                return instruction;
            }
        }
        throw new IllegalStateException("Cave Biomes API found an incomplete Galacticraft "
                + "rocket entity-section read");
    }
}
