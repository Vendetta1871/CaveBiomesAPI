package net.celestiald.cavebiomes.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Structural contract tests for {@link NothiriumRenderTransformer}. The
 * fixtures are minimal synthetic copies of Nothirium's 0.4.7-beta bytecode
 * layout: the overload pair in WorldUtil, the early-return guard in
 * RenderChunk.markDirty and the world-bound guard in Direction$1/2.
 */
public class NothiriumRenderTransformerTest implements Opcodes {
    private static final String COMPAT = "net/celestiald/cavebiomes/core/NothiriumRenderCompat";
    private static final String WORLD_UTIL = "meldexun.nothirium.mc.util.WorldUtil";
    private static final String RENDER_CHUNK = "meldexun.nothirium.mc.renderer.chunk.RenderChunk";
    private static final String DIRECTION_1 = "meldexun.nothirium.util.Direction$1";
    private static final String EBS = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";
    private static final String GET_SECTION_DESC = "(Lnet/minecraft/world/World;III)" + EBS;

    private final NothiriumRenderTransformer transformer = new NothiriumRenderTransformer();

    @Test
    public void getSectionDelegatesTheWorldOverloadOnly() {
        byte[] result = transformer.transform(WORLD_UTIL, WORLD_UTIL, worldUtilFixture());
        assertNotEquals(null, result);

        ClassNode node = read(result);
        MethodNode worldOverload = method(node, "getSection", GET_SECTION_DESC);
        assertTrue("first real instruction must be ALOAD 0 (delegate head)",
                worldOverload.instructions.getFirst() instanceof org.objectweb.asm.tree.VarInsnNode);
        MethodInsnNode delegate = null;
        for (AbstractInsnNode instruction : worldOverload.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                delegate = (MethodInsnNode) instruction;
                break;
            }
        }
        assertNotNullCompat(delegate);
        assertEquals(COMPAT, delegate.owner);
        assertEquals("getSection", delegate.name);

        // The convenience overload getSection(int, int, int) must stay untouched:
        // its body forwards to the World overload, not to our compat helper.
        MethodNode convenience = method(node, "getSection", "(III)" + EBS);
        for (AbstractInsnNode instruction : convenience.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                assertNotEquals(COMPAT, ((MethodInsnNode) instruction).owner);
            }
        }
    }

    @Test
    public void markDirtySkipsTheGuardOnlyWhenExtended() {
        byte[] result = transformer.transform(RENDER_CHUNK, RENDER_CHUNK, markDirtyFixture());
        MethodNode method = method(read(result), "markDirty", "()V");

        AbstractInsnNode first = method.instructions.getFirst();
        assertTrue(first instanceof MethodInsnNode);
        assertEquals(COMPAT, ((MethodInsnNode) first).owner);
        assertEquals("useExtendedHeight", ((MethodInsnNode) first).name);

        AbstractInsnNode second = first.getNext();
        assertTrue(second instanceof JumpInsnNode);
        assertEquals(IFNE, ((JumpInsnNode) second).getOpcode());
    }

    @Test
    public void faceCullingSkipsTheWorldBoundGuardOnlyWhenExtended() {
        byte[] result = transformer.transform(DIRECTION_1, DIRECTION_1, faceCulledFixture());
        MethodNode method = method(read(result), "isFaceCulled",
                "(Lmeldexun/nothirium/api/renderer/chunk/IRenderChunk;DDD)Z");

        AbstractInsnNode first = method.instructions.getFirst();
        assertTrue(first instanceof MethodInsnNode);
        assertEquals("useExtendedHeight", ((MethodInsnNode) first).name);
        AbstractInsnNode second = first.getNext();
        assertTrue(second instanceof JumpInsnNode);
        assertEquals(IFNE, ((JumpInsnNode) second).getOpcode());
    }

    @Test
    public void unrelatedClassesPassThroughUnchanged() {
        byte[] fixture = markDirtyFixture();
        byte[] result = transformer.transform("com.example.Other", "com.example.Other", fixture);
        assertEquals(fixture.length, result.length);
    }

    @Test
    public void missingGuardFailsLoudly() {
        ClassWriter writer = newClass(RENDER_CHUNK.replace('.', '/'), "meldexun/nothirium/renderer/chunk/AbstractRenderChunk");
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "markDirty", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitInsn(RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        try {
            transformer.transform(RENDER_CHUNK, RENDER_CHUNK, writer.toByteArray());
            fail("Expected a loud failure when the guard is missing");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Nothirium"));
        }
    }

    private static byte[] worldUtilFixture() {
        ClassWriter writer = newClass(WORLD_UTIL.replace('.', '/'), "java/lang/Object");
        // getSection(int, int, int): forwards to the World overload via Minecraft.getMinecraft().world
        MethodVisitor convenience = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "getSection", "(III)" + EBS, null, null);
        convenience.visitCode();
        convenience.visitInsn(ACONST_NULL);
        convenience.visitVarInsn(ILOAD, 0);
        convenience.visitVarInsn(ILOAD, 1);
        convenience.visitVarInsn(ILOAD, 2);
        convenience.visitMethodInsn(INVOKESTATIC, WORLD_UTIL.replace('.', '/'), "getSection", GET_SECTION_DESC, false);
        convenience.visitInsn(ARETURN);
        convenience.visitMaxs(4, 3);
        convenience.visitEnd();
        // getSection(World, int, int, int): the vanilla [0, 16) guard plus storage lookup
        MethodVisitor worldOverload = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "getSection", GET_SECTION_DESC, null, null);
        worldOverload.visitCode();
        worldOverload.visitVarInsn(ILOAD, 2);
        Label guardPassed = new Label();
        worldOverload.visitJumpInsn(IFLT, guardPassed);
        worldOverload.visitVarInsn(ILOAD, 2);
        worldOverload.visitIntInsn(BIPUSH, 16);
        worldOverload.visitJumpInsn(IF_ICMPLT, guardPassed);
        worldOverload.visitInsn(ACONST_NULL);
        worldOverload.visitInsn(ARETURN);
        worldOverload.visitLabel(guardPassed);
        worldOverload.visitInsn(ACONST_NULL);
        worldOverload.visitInsn(ARETURN);
        worldOverload.visitMaxs(2, 4);
        worldOverload.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] markDirtyFixture() {
        ClassWriter writer = newClass(RENDER_CHUNK.replace('.', '/'), "meldexun/nothirium/renderer/chunk/AbstractRenderChunk");
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "markDirty", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKEVIRTUAL, RENDER_CHUNK.replace('.', '/'), "getSectionY", "()I", false);
        Label afterGuard = new Label();
        method.visitJumpInsn(IFGE, afterGuard);
        method.visitVarInsn(ALOAD, 0);
        method.visitInsn(RETURN);
        method.visitLabel(afterGuard);
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, "meldexun/nothirium/renderer/chunk/AbstractRenderChunk", "markDirty", "()V", false);
        method.visitInsn(RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] faceCulledFixture() {
        ClassWriter writer = newClass(DIRECTION_1.replace('.', '/'), "meldexun/nothirium/util/Direction");
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "isFaceCulled",
                "(Lmeldexun/nothirium/api/renderer/chunk/IRenderChunk;DDD)Z", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKEINTERFACE, "meldexun/nothirium/api/renderer/chunk/IRenderChunk", "getSectionY", "()I", true);
        method.visitIntInsn(BIPUSH, 16);
        Label cameraCheck = new Label();
        method.visitJumpInsn(IF_ICMPLT, cameraCheck);
        method.visitInsn(ICONST_1);
        method.visitInsn(IRETURN);
        method.visitLabel(cameraCheck);
        method.visitInsn(ICONST_0);
        method.visitInsn(IRETURN);
        method.visitMaxs(2, 8);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter newClass(String name, String superName) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, name, null, superName, null);
        return writer;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(ASM5);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return method;
            }
        }
        fail("no method " + name + desc + " in " + node.name);
        return null;
    }

    private static void assertNotNullCompat(MethodInsnNode delegate) {
        if (delegate == null) {
            fail("no delegate call injected");
        }
    }
}
