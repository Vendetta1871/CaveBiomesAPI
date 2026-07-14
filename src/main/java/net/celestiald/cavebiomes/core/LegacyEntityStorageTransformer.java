package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Normalizes direct entity-list storage indices in known third-party code. */
public final class LegacyEntityStorageTransformer implements IClassTransformer {
    private static final String AUTO_ROCKET =
            "micdoodle8.mods.galacticraft.api.prefab.entity.EntityAutoRocket";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String ENTITY_LISTS =
            "()[Lnet/minecraft/util/ClassInheritanceMultiMap;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null
                || !(AUTO_ROCKET.equals(name) || AUTO_ROCKET.equals(transformedName))) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        int patches = 0;
        for (MethodNode method : node.methods) {
            if (("onUpdate".equals(method.name) || "func_70071_h_".equals(method.name))
                    && "()V".equals(method.desc)) {
                patches += patchRocketSectionIndex(method);
            }
        }
        if (patches != 1) {
            throw new IllegalStateException("Cave Biomes API expected one Galacticraft rocket "
                    + "entity-section read, found " + patches);
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
                    "net/celestiald/cavebiomes/api/WorldHeightAPI",
                    "sectionIndexFromSectionY",
                    "(I)I",
                    false));
            patches++;
        }
        return patches;
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
