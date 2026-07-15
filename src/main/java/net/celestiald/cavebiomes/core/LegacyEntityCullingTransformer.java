package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Normalizes entity-section indices used by third-party render culling patches. */
public final class LegacyEntityCullingTransformer implements IClassTransformer {
    private static final String FERMIUM = "mirror.normalasm.patches.RenderGlobalPatch";
    private static final String SLEDGEHAMMER =
            "io.github.lxgaming.sledgehammer.mixin.core.client.renderer.RenderGlobalMixin";
    private static final String BOUNDS_DESC = "(Lnet/minecraft/client/renderer/chunk/"
            + "RenderChunk;)Lnet/minecraft/util/math/AxisAlignedBB;";
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
        int sectionPatches = 0;
        int boundPatches = 0;
        for (MethodNode method : node.methods) {
            if (isTargetMethod(target, method)) {
                sectionPatches += patchSectionDivision(method);
                if (SLEDGEHAMMER.equals(target)) {
                    boundPatches += patchSledgehammerBound(method);
                }
            }
        }
        int expectedBounds = SLEDGEHAMMER.equals(target) ? 1 : 0;
        if (sectionPatches != 1 || boundPatches != expectedBounds) {
            throw new IllegalStateException("Cave Biomes API expected one entity-culling section "
                    + "calculation and " + expectedBounds + " bounds in " + target
                    + ", found " + sectionPatches + " and " + boundPatches);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isTargetMethod(String target, MethodNode method) {
        String expectedName = FERMIUM.equals(target)
                ? "getCorrectBoundingBox" : "getBoundingBoxForChunk";
        return expectedName.equals(method.name) && BOUNDS_DESC.equals(method.desc);
    }

    private static int patchSectionDivision(MethodNode method) {
        int patches = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.IDIV) {
                continue;
            }
            AbstractInsnNode divisor = previousReal(instruction);
            if (!(divisor instanceof IntInsnNode)
                    || divisor.getOpcode() != Opcodes.BIPUSH
                    || ((IntInsnNode) divisor).operand != 16) {
                continue;
            }
            method.instructions.remove(divisor);
            method.instructions.set(instruction, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HEIGHT_API,
                    "sectionIndex",
                    "(I)I",
                    false));
            patches++;
        }
        return patches;
    }

    private static int patchSledgehammerBound(MethodNode method) {
        int patches = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof IntInsnNode)
                    || instruction.getOpcode() != Opcodes.BIPUSH
                    || ((IntInsnNode) instruction).operand != 15) {
                continue;
            }
            InsnList arrayMaximum = new InsnList();
            arrayMaximum.add(new VarInsnNode(Opcodes.ALOAD, 3));
            arrayMaximum.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    CHUNK,
                    "func_177429_s",
                    ENTITY_LISTS,
                    false));
            arrayMaximum.add(new InsnNode(Opcodes.ARRAYLENGTH));
            arrayMaximum.add(new InsnNode(Opcodes.ICONST_1));
            arrayMaximum.add(new InsnNode(Opcodes.ISUB));
            method.instructions.insertBefore(instruction, arrayMaximum);
            method.instructions.remove(instruction);
            patches++;
        }
        return patches;
    }

    private static String target(String name, String transformedName) {
        if (matches(FERMIUM, name, transformedName)) {
            return FERMIUM;
        }
        if (matches(SLEDGEHAMMER, name, transformedName)) {
            return SLEDGEHAMMER;
        }
        return null;
    }

    private static boolean matches(String target, String name, String transformedName) {
        return target.equals(name) || target.equals(transformedName);
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() >= 0) {
                return instruction;
            }
        }
        throw new IllegalStateException("Cave Biomes API found an incomplete entity-culling "
                + "section calculation");
    }
}
