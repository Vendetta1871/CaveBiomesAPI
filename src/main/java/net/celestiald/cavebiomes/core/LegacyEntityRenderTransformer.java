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

/** Normalizes direct entity-section reads in known third-party render loops. */
public final class LegacyEntityRenderTransformer implements IClassTransformer {
    private static final String ICHUN_PORTAL = "me.ichun.mods.ichunutil.common.module."
            + "worldportals.client.render.world.RenderGlobalProxy";
    private static final String HBM_LIGHT = "com.hbm.render.LightRenderer";
    private static final String HBM_HELPER = "com.hbm.render.RenderHelper";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String ENTITY_LISTS =
            "()[Lnet/minecraft/util/ClassInheritanceMultiMap;";
    private static final String COMPAT =
            "net/celestiald/cavebiomes/client/ExtendedEntityRenderCompat";
    private static final String COMPAT_DESC = "(Lnet/minecraft/world/chunk/Chunk;I)"
            + "Lnet/minecraft/util/ClassInheritanceMultiMap;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !isTarget(name, transformedName)) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        int patches = 0;
        for (MethodNode method : node.methods) {
            patches += patchEntityListReads(method);
        }
        if (patches != 1) {
            throw new IllegalStateException("Cave Biomes API expected one direct entity-section "
                    + "read in " + transformedName + ", found " + patches);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isTarget(String name, String transformedName) {
        return matches(ICHUN_PORTAL, name, transformedName)
                || matches(HBM_LIGHT, name, transformedName)
                || matches(HBM_HELPER, name, transformedName);
    }

    private static boolean matches(String target, String name, String transformedName) {
        return target.equals(name) || target.equals(transformedName);
    }

    private static int patchEntityListReads(MethodNode method) {
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

            AbstractInsnNode arrayRead = findArrayRead(listsCall);
            AbstractInsnNode division = previousReal(arrayRead);
            AbstractInsnNode divisor = previousReal(division);
            if (!(divisor instanceof IntInsnNode)
                    || divisor.getOpcode() != Opcodes.BIPUSH
                    || ((IntInsnNode) divisor).operand != 16
                    || division.getOpcode() != Opcodes.IDIV
                    || arrayRead.getOpcode() != Opcodes.AALOAD) {
                throw new IllegalStateException("Cave Biomes API found an unexpected direct "
                        + "entity-section read in " + method.name + method.desc);
            }

            method.instructions.remove(listsCall);
            method.instructions.remove(divisor);
            method.instructions.remove(division);
            method.instructions.set(arrayRead, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    COMPAT,
                    "entityListAtBlockY",
                    COMPAT_DESC,
                    false));
            patches++;
        }
        return patches;
    }

    private static AbstractInsnNode findArrayRead(AbstractInsnNode start) {
        AbstractInsnNode instruction = start;
        for (int i = 0; i < 10; i++) {
            instruction = nextReal(instruction);
            if (instruction.getOpcode() == Opcodes.AALOAD) {
                return instruction;
            }
        }
        throw new IllegalStateException("Cave Biomes API found an incomplete entity-section read");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getNext(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) {
                return instruction;
            }
        }
        throw new IllegalStateException("Cave Biomes API found an incomplete entity-section read");
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() >= 0) {
                return instruction;
            }
        }
        throw new IllegalStateException("Cave Biomes API found an incomplete entity-section read");
    }
}
