package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adapts legacy dimension transitions that are relative to vanilla's Y=0 floor. */
public final class LegacyDimensionBoundaryTransformer implements IClassTransformer {
    private static final String WARPDRIVE_HANDLER = "cr0s.warpdrive.event.LivingHandler";
    private static final String WARPDRIVE_UPDATE = "onLivingUpdate";
    private static final String WARPDRIVE_UPDATE_DESC =
            "(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingUpdateEvent;)V";

    private static final String ENTITY_LIVING = "net/minecraft/entity/EntityLivingBase";
    private static final String WORLD_DESC = "Lnet/minecraft/world/World;";
    private static final String HEIGHT_API =
            "net/celestiald/cavebiomes/api/WorldHeightAPI";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || (!WARPDRIVE_HANDLER.equals(name)
                && !WARPDRIVE_HANDLER.equals(transformedName))) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        patchWarpDrive(node);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void patchWarpDrive(ClassNode node) {
        MethodNode method = uniqueMethod(node, WARPDRIVE_UPDATE, WARPDRIVE_UPDATE_DESC);
        FieldInsnNode worldField = findWorldField(method);
        LdcInsnNode boundary = null;
        int entityLocal = -1;
        boolean findsChildDimension = false;

        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("cr0s/warpdrive/data/CelestialObjectManager".equals(call.owner)
                        && "getClosestChild".equals(call.name)) {
                    findsChildDimension = true;
                }
            }
            if (!(instruction instanceof LdcInsnNode)
                    || !Double.valueOf(-10.0D).equals(((LdcInsnNode) instruction).cst)) {
                continue;
            }

            AbstractInsnNode positionRead = previousReal(instruction);
            AbstractInsnNode entityLoad = previousReal(positionRead);
            if (!(positionRead instanceof FieldInsnNode)
                    || positionRead.getOpcode() != Opcodes.GETFIELD
                    || !ENTITY_LIVING.equals(((FieldInsnNode) positionRead).owner)
                    || !"D".equals(((FieldInsnNode) positionRead).desc)
                    || !("posY".equals(((FieldInsnNode) positionRead).name)
                        || "field_70163_u".equals(((FieldInsnNode) positionRead).name))
                    || !(entityLoad instanceof VarInsnNode)
                    || entityLoad.getOpcode() != Opcodes.ALOAD
                    || nextReal(instruction).getOpcode() != Opcodes.DCMPG
                    || nextReal(nextReal(instruction)).getOpcode() != Opcodes.IFGE) {
                continue;
            }
            if (boundary != null) {
                throw failure("multiple lower dimension boundaries");
            }
            boundary = (LdcInsnNode) instruction;
            entityLocal = ((VarInsnNode) entityLoad).var;
        }

        if (!findsChildDimension) {
            throw failure("no child-dimension transition");
        }
        if (boundary == null || entityLocal < 0) {
            throw failure("no Y=-10 lower dimension boundary");
        }

        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(Opcodes.ALOAD, entityLocal));
        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, worldField.owner,
                worldField.name, worldField.desc));
        replacement.add(new LdcInsnNode(-10.0D));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HEIGHT_API,
                "offsetFromVanillaFloor", "(" + WORLD_DESC + "D)D", false));
        method.instructions.insertBefore(boundary, replacement);
        method.instructions.remove(boundary);
    }

    private static FieldInsnNode findWorldField(MethodNode method) {
        FieldInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode)
                    || instruction.getOpcode() != Opcodes.GETFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (!ENTITY_LIVING.equals(field.owner) || !WORLD_DESC.equals(field.desc)
                    || !("world".equals(field.name) || "field_70170_p".equals(field.name))) {
                continue;
            }
            if (result != null && (!result.owner.equals(field.owner)
                    || !result.name.equals(field.name) || !result.desc.equals(field.desc))) {
                throw failure("inconsistent entity world fields");
            }
            result = field;
        }
        if (result == null) throw failure("no entity world field");
        return result;
    }

    private static MethodNode uniqueMethod(ClassNode node, String name, String desc) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (!name.equals(method.name) || !desc.equals(method.desc)) continue;
            if (result != null) throw failure("multiple " + name + desc + " methods");
            result = method;
        }
        if (result == null) throw failure("no " + name + desc + " method");
        return result;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() >= 0) return instruction;
        }
        throw failure("no preceding bytecode instruction");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getNext(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) return instruction;
        }
        throw failure("no following bytecode instruction");
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException("Cave Biomes API dimension-boundary transformer found "
                + detail + " in " + WARPDRIVE_HANDLER);
    }
}
