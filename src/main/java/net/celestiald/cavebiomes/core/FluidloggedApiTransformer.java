package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Keeps Fluidlogged API's chunk capability distinct across extended-height Y pages.
 *
 * @author CelestialD
 */
public final class FluidloggedApiTransformer implements IClassTransformer {
    private static final String CAPABILITY =
            "git.jbredwards.fluidlogged_api.mod.common.capability.FluidStateCapabilityVanilla";
    private static final String MESSAGE =
            "git.jbredwards.fluidlogged_api.mod.common.message.SMessageSyncFluidStates";
    private static final String EVENT_HANDLER =
            "git.jbredwards.fluidlogged_api.mod.common.EventHandler";
    private static final String CONTAINER_INTERNAL =
            "git/jbredwards/fluidlogged_api/api/capability/IFluidStateContainer";
    private static final String COMPAT =
            "net/celestiald/cavebiomes/core/FluidloggedApiCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        String target = target(name, transformedName);
        if (target == null) return basicClass;

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        if (CAPABILITY.equals(target)) transformCapability(node);
        else if (MESSAGE.equals(target)) transformMessage(node);
        else transformEventHandler(node);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String target(String name, String transformedName) {
        for (String candidate : new String[]{CAPABILITY, MESSAGE, EVENT_HANDLER}) {
            if (candidate.equals(name) || candidate.equals(transformedName)) return candidate;
        }
        return null;
    }

    private static void transformCapability(ClassNode node) {
        replaceContainerLookup(uniqueMethod(node, "getContainer",
                "(I)Lgit/jbredwards/fluidlogged_api/api/capability/IFluidStateContainer;"));
        replaceYDecoder(uniqueMethod(node, "deserializeY", "(C)I"));

        MethodNode serialize = uniqueMethod(node, "serializeNBT", "()Lnet/minecraft/nbt/NBTBase;");
        serialize.name = "cavebiomes$serializePageNBT";
        MethodNode deserialize = uniqueMethod(node, "deserializeNBT", "(Lnet/minecraft/nbt/NBTBase;)V");
        deserialize.name = "cavebiomes$deserializePageNBT";

        MethodNode serializeWrapper = new MethodNode(Opcodes.ACC_PUBLIC, "serializeNBT",
                "()Lnet/minecraft/nbt/NBTBase;", null, null);
        serializeWrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        serializeWrapper.instructions.add(call("serializeCapability",
                "(Ljava/lang/Object;)Lnet/minecraft/nbt/NBTBase;"));
        serializeWrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(serializeWrapper);

        MethodNode deserializeWrapper = new MethodNode(Opcodes.ACC_PUBLIC, "deserializeNBT",
                "(Lnet/minecraft/nbt/NBTBase;)V", null, null);
        deserializeWrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        deserializeWrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        deserializeWrapper.instructions.add(call("deserializeCapability",
                "(Ljava/lang/Object;Lnet/minecraft/nbt/NBTBase;)V"));
        deserializeWrapper.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(deserializeWrapper);
    }

    private static void replaceContainerLookup(MethodNode method) {
        clear(method);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(call("getContainer", "(Ljava/lang/Object;I)Ljava/lang/Object;"));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, CONTAINER_INTERNAL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
    }

    private static void replaceYDecoder(MethodNode method) {
        clear(method);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(call("deserializeY", "(Ljava/lang/Object;I)I"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
    }

    private static void transformMessage(ClassNode node) {
        MethodNode constructor = uniqueMethod(node, "<init>",
                "(IIILgit/jbredwards/fluidlogged_api/api/capability/IFluidStateCapability;)V");
        int replaced = 0;
        for (AbstractInsnNode instruction : constructor.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEINTERFACE
                    || !"getContainer".equals(call.name)
                    || !"(I)Lgit/jbredwards/fluidlogged_api/api/capability/IFluidStateContainer;"
                    .equals(call.desc)) {
                continue;
            }

            AbstractInsnNode field = previousReal(call);
            AbstractInsnNode owner = previousReal(field);
            if (!(field instanceof FieldInsnNode) || field.getOpcode() != Opcodes.GETFIELD
                    || !"indexY".equals(((FieldInsnNode) field).name)
                    || owner.getOpcode() != Opcodes.ALOAD
                    || ((VarInsnNode) owner).var != 0) {
                throw failure("SMessageSyncFluidStates", "unexpected container Y source");
            }
            constructor.instructions.set(owner, new VarInsnNode(Opcodes.ILOAD, 2));
            constructor.instructions.remove(field);
            replaced++;
        }
        if (replaced != 1) {
            throw failure("SMessageSyncFluidStates", "expected one container Y source, found " + replaced);
        }
    }

    private static void transformEventHandler(ClassNode node) {
        MethodNode syncChunk = uniqueMethod(node, "syncChunk",
                "(Lnet/minecraftforge/event/world/ChunkWatchEvent$Watch;)V");
        int returns = 0;
        for (AbstractInsnNode instruction : syncChunk.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                syncChunk.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, 0));
                syncChunk.instructions.insertBefore(instruction, call("syncExtendedPages",
                        "(Lnet/minecraftforge/event/world/ChunkWatchEvent$Watch;)V"));
                returns++;
            }
        }
        if (returns != 1) {
            throw failure("EventHandler.syncChunk", "expected one return, found " + returns);
        }
    }

    private static void clear(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
    }

    private static MethodNode uniqueMethod(ClassNode node, String name, String desc) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                if (result != null) throw failure(node.name, "multiple " + name + desc + " methods");
                result = method;
            }
        }
        if (result == null) throw failure(node.name, "no " + name + desc + " method");
        return result;
    }

    private static MethodInsnNode call(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, COMPAT, name, desc, false);
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode start) {
        for (AbstractInsnNode instruction = start.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() >= 0) return instruction;
        }
        throw failure("bytecode", "no preceding instruction");
    }

    private static IllegalStateException failure(String target, String detail) {
        return new IllegalStateException("Cave Biomes API Fluidlogged API transformer found "
                + detail + " in " + target);
    }
}
