package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Guards legacy client input handlers that dereference the player during disconnect teardown. */
public final class LegacyClientLifecycleTransformer implements IClassTransformer {
    private static final String TARGET =
            "electroblob.wizardry.client.WizardryControlHandler";
    private static final String METHOD = "getWandInUse";
    private static final String METHOD_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String HELPER =
            "net/celestiald/cavebiomes/client/LegacyClientLifecycleGuard";
    private static final String HELPER_DESC =
            "(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        MethodNode method = uniqueMethod(node);
        replaceHandRead(method, "getHeldItemMainhand", "func_184614_ca", "mainHand");
        replaceHandRead(method, "getHeldItemOffhand", "func_184592_cb", "offHand");

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode uniqueMethod(ClassNode node) {
        MethodNode result = null;
        for (MethodNode method : node.methods) {
            if (METHOD.equals(method.name) && METHOD_DESC.equals(method.desc)) {
                if (result != null) {
                    throw failure("multiple getWandInUse methods");
                }
                result = method;
            }
        }
        if (result == null) {
            throw failure("no getWandInUse(EntityPlayer) method");
        }
        return result;
    }

    private static void replaceHandRead(MethodNode method, String mcpName, String srgName,
            String helperName) {
        MethodInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && PLAYER.equals(call.owner)
                    && "()Lnet/minecraft/item/ItemStack;".equals(call.desc)
                    && (mcpName.equals(call.name) || srgName.equals(call.name))) {
                if (result != null) {
                    throw failure("multiple " + mcpName + " calls");
                }
                result = call;
            }
        }
        if (result == null) {
            throw failure("no " + mcpName + " call");
        }
        result.setOpcode(Opcodes.INVOKESTATIC);
        result.owner = HELPER;
        result.name = helperName;
        result.desc = HELPER_DESC;
        result.itf = false;
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException("Cave Biomes API Wizardry lifecycle guard found " + detail);
    }
}
