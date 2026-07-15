package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Normalizes vertical entity-query ranges in known third-party fast paths. */
public final class LegacyEntityQueryTransformer implements IClassTransformer {
    private static final String UNIVERSAL_TWEAKS =
            "mod.acgaming.universaltweaks.util.UTEntityAABBUtil";
    private static final String ENDER_IO_MAGNET =
            "crazypants.enderio.base.item.magnet.MagnetController";
    private static final String ENDER_IO_VACUUM =
            "crazypants.enderio.machines.machine.vacuum.chest.TileVacuumChest";
    private static final String QUERY_DESC = "(Lnet/minecraft/world/World;"
            + "Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;";
    private static final String UT_QUERY_DESC = "(Lnet/minecraft/world/chunk/Chunk;"
            + "Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;"
            + "Ljava/util/List;Lcom/google/common/base/Predicate;D)V";
    private static final String AABB = "net/minecraft/util/math/AxisAlignedBB";
    private static final String MATH_HELPER = "net/minecraft/util/math/MathHelper";
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
            if (UNIVERSAL_TWEAKS.equals(target)
                    && "getEntitiesWithinAABBForEntity".equals(method.name)
                    && UT_QUERY_DESC.equals(method.desc)) {
                patches += patchFloorSections(method);
            } else if ((ENDER_IO_MAGNET.equals(target) || ENDER_IO_VACUUM.equals(target))
                    && "selectEntitiesWithinAABB".equals(method.name)
                    && QUERY_DESC.equals(method.desc)) {
                patches += ENDER_IO_MAGNET.equals(target)
                        ? patchShiftSections(method) : patchFloorSections(method);
            }
        }
        if (patches != 2) {
            throw new IllegalStateException("Cave Biomes API expected two vertical entity-query "
                    + "section calculations in " + target + ", found " + patches);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String target(String name, String transformedName) {
        if (matches(UNIVERSAL_TWEAKS, name, transformedName)) {
            return UNIVERSAL_TWEAKS;
        }
        if (matches(ENDER_IO_MAGNET, name, transformedName)) {
            return ENDER_IO_MAGNET;
        }
        if (matches(ENDER_IO_VACUUM, name, transformedName)) {
            return ENDER_IO_VACUUM;
        }
        return null;
    }

    private static boolean matches(String target, String name, String transformedName) {
        return target.equals(name) || target.equals(transformedName);
    }

    private static int patchFloorSections(MethodNode method) {
        int patches = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!isVerticalAabbField(instruction)) {
                continue;
            }
            AbstractInsnNode floor = findFloorBeforeNextField(instruction);
            method.instructions.insert(floor, minimumSectionOffset());
            patches++;
        }
        return patches;
    }

    private static int patchShiftSections(MethodNode method) {
        int patches = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!isVerticalAabbField(instruction)) {
                continue;
            }
            AbstractInsnNode d2i = nextReal(instruction);
            AbstractInsnNode four = nextReal(d2i);
            AbstractInsnNode shift = nextReal(four);
            if (d2i.getOpcode() != Opcodes.D2I
                    || four.getOpcode() != Opcodes.ICONST_4
                    || shift.getOpcode() != Opcodes.ISHR) {
                throw unexpected(method);
            }
            method.instructions.insert(shift, minimumSectionOffset());
            patches++;
        }
        return patches;
    }

    private static AbstractInsnNode findFloorBeforeNextField(AbstractInsnNode start) {
        AbstractInsnNode instruction = start;
        for (int i = 0; i < 8; i++) {
            instruction = nextReal(instruction);
            if (instruction instanceof FieldInsnNode) {
                break;
            }
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                        && MATH_HELPER.equals(call.owner)
                        && "(D)I".equals(call.desc)
                        && ("floor".equals(call.name) || "func_76128_c".equals(call.name))) {
                    return call;
                }
            }
        }
        throw new IllegalStateException("Cave Biomes API found an unexpected vertical "
                + "entity-query section calculation");
    }

    private static boolean isVerticalAabbField(AbstractInsnNode instruction) {
        if (!(instruction instanceof FieldInsnNode)) {
            return false;
        }
        FieldInsnNode field = (FieldInsnNode) instruction;
        return field.getOpcode() == Opcodes.GETFIELD
                && AABB.equals(field.owner)
                && "D".equals(field.desc)
                && ("minY".equals(field.name) || "maxY".equals(field.name)
                        || "field_72338_b".equals(field.name)
                        || "field_72337_e".equals(field.name));
    }

    private static InsnList minimumSectionOffset() {
        InsnList offset = new InsnList();
        offset.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HEIGHT_API,
                "getMinSection",
                "()I",
                false));
        offset.add(new InsnNode(Opcodes.ISUB));
        return offset;
    }

    private static IllegalStateException unexpected(MethodNode method) {
        return new IllegalStateException("Cave Biomes API found an unexpected vertical "
                + "entity-query section calculation in " + method.name + method.desc);
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getNext(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) {
                return instruction;
            }
        }
        throw new IllegalStateException("Cave Biomes API found an incomplete vertical "
                + "entity-query section calculation");
    }
}
