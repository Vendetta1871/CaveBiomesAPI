package net.celestiald.cavebiomes.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Repairs known 1.12 mods that allocate entity data keys against the wrong entity class. */
public final class LegacyDataParameterOwnerTransformer implements IClassTransformer {
    private static final Patch[] PATCHES = {
            new Patch("drzhark.mocreatures.entity.MoCEntityMob",
                    "net/minecraft/entity/EntityCreature",
                    "drzhark/mocreatures/entity/MoCEntityMob", 4),
            new Patch("erebus.entity.EntityJumpingSpider",
                    "erebus/entity/EntityScytodes",
                    "erebus/entity/EntityJumpingSpider", 1),
            new Patch("net.daveyx0.multimob.entity.EntityMMFlyingMob",
                    "net/daveyx0/multimob/entity/EntityMMBird",
                    "net/daveyx0/multimob/entity/EntityMMFlyingMob", 1),
            new Patch("com.lycanitesmobs.core.entity.AgeableCreatureEntity",
                    "com/lycanitesmobs/core/entity/BaseCreatureEntity",
                    "com/lycanitesmobs/core/entity/AgeableCreatureEntity", 2),
            new Patch("com.lycanitesmobs.core.entity.TameableCreatureEntity",
                    "com/lycanitesmobs/core/entity/BaseCreatureEntity",
                    "com/lycanitesmobs/core/entity/TameableCreatureEntity", 4)
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        Patch patch = findPatch(name, transformedName);
        if (patch == null) {
            return basicClass;
        }

        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(basicClass).accept(node, 0);
        MethodNode initializer = null;
        for (MethodNode method : node.methods) {
            if ("<clinit>".equals(method.name) && "()V".equals(method.desc)) {
                if (initializer != null) {
                    throw failure(patch, "multiple static initializers");
                }
                initializer = method;
            }
        }
        if (initializer == null) {
            throw failure(patch, "no static initializer");
        }

        int replaced = 0;
        for (AbstractInsnNode instruction : initializer.instructions.toArray()) {
            if (instruction instanceof LdcInsnNode) {
                LdcInsnNode constant = (LdcInsnNode) instruction;
                if (constant.cst instanceof Type
                        && patch.badOwner.equals(((Type) constant.cst).getInternalName())) {
                    constant.cst = Type.getObjectType(patch.correctOwner);
                    replaced++;
                }
            }
        }
        if (replaced != patch.expectedCount) {
            throw failure(patch, "expected " + patch.expectedCount
                    + " wrong owner literals but found " + replaced);
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static Patch findPatch(String name, String transformedName) {
        for (Patch patch : PATCHES) {
            if (patch.className.equals(name) || patch.className.equals(transformedName)) {
                return patch;
            }
        }
        return null;
    }

    private static IllegalStateException failure(Patch patch, String detail) {
        return new IllegalStateException("Cave Biomes API data-parameter compatibility patch for "
                + patch.className + " found " + detail);
    }

    private static final class Patch {
        private final String className;
        private final String badOwner;
        private final String correctOwner;
        private final int expectedCount;

        private Patch(String className, String badOwner, String correctOwner,
                int expectedCount) {
            this.className = className;
            this.badOwner = badOwner;
            this.correctOwner = correctOwner;
            this.expectedCount = expectedCount;
        }
    }
}
