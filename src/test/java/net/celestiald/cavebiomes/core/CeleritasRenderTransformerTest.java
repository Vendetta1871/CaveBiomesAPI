package net.celestiald.cavebiomes.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Structural contract tests for {@link CeleritasRenderTransformer}. The
 * fixtures are minimal synthetic copies of the bytecode layout found in the
 * Celeritas 2.4.0-dev (forge-mc12.2) and Actinium alpha-0.0.6 release jars:
 * the hardcoded 0/16 section bounds in VintageRenderSectionManager.create,
 * the direct storage-array lookup in WorldSlice.prepare, the guarded lookup
 * in ClonedChunkSection.getChunkSection and Actinium's DepthsUpdateCompat
 * shim methods.
 */
public class CeleritasRenderTransformerTest implements Opcodes {
    private static final String COMPAT = "net/celestiald/cavebiomes/core/CeleritasRenderCompat";
    private static final String SECTION_MANAGER = "org.taumc.celeritas.impl.render.terrain.VintageRenderSectionManager";
    private static final String WORLD_SLICE = "org.taumc.celeritas.impl.world.WorldSlice";
    private static final String CLONED_SECTION = "org.taumc.celeritas.impl.world.cloned.ClonedChunkSection";
    private static final String WORLD_RENDERER = "org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer";
    private static final String DEPTHS_UPDATE_COMPAT = "com.dhj.actinium.compat.depthsupdate.DepthsUpdateCompat";
    private static final String ACTINIUM_RENDERER = "com.dhj.actinium.render.terrain.ActiniumWorldRenderer";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String EBS = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";
    private static final String GET_SECTION_DESC = "(L" + CHUNK + ";I)" + EBS;

    private final CeleritasRenderTransformer transformer = new CeleritasRenderTransformer();

    @Test
    public void createBoundsDelegateToCompat() {
        ClassNode node = read(transformer.transform(SECTION_MANAGER, SECTION_MANAGER, sectionManagerFixture()));
        MethodNode create = method(node, "create");

        boolean minFound = false;
        boolean maxFound = false;
        for (AbstractInsnNode instruction : create.instructions.toArray()) {
            if (instruction.getOpcode() == ICONST_0) {
                fail("hardcoded 0 section bound survived in create");
            }
            if (instruction instanceof IntInsnNode
                    && instruction.getOpcode() == BIPUSH && ((IntInsnNode) instruction).operand == 16) {
                fail("hardcoded 16 section bound survived in create");
            }
            if (instruction instanceof MethodInsnNode && COMPAT.equals(((MethodInsnNode) instruction).owner)) {
                if ("getMinSection".equals(((MethodInsnNode) instruction).name)) {
                    minFound = true;
                }
                if ("getMaxSection".equals(((MethodInsnNode) instruction).name)) {
                    maxFound = true;
                    assertTrue("getMaxSection must come after getMinSection", minFound);
                }
            }
        }
        assertTrue("no getMinSection delegate injected", minFound);
        assertTrue("no getMaxSection delegate injected", maxFound);
    }

    @Test
    public void isSectionVisuallyEmptyDelegates() {
        ClassNode node = read(transformer.transform(SECTION_MANAGER, SECTION_MANAGER, sectionManagerFixture()));
        MethodNode method = method(node, "isSectionVisuallyEmpty");

        AbstractInsnNode first = method.instructions.getFirst();
        assertTrue(first instanceof org.objectweb.asm.tree.VarInsnNode);
        AbstractInsnNode second = first.getNext();
        assertTrue(second instanceof FieldInsnNode);
        assertEquals("world", ((FieldInsnNode) second).name);
        MethodInsnNode delegate = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                delegate = (MethodInsnNode) instruction;
                break;
            }
        }
        assertNotNullCompat(delegate);
        assertEquals(COMPAT, delegate.owner);
        assertEquals("isSectionVisuallyEmpty", delegate.name);
    }

    @Test
    public void prepareReroutesTheStorageLookup() {
        ClassNode node = read(transformer.transform(WORLD_SLICE, WORLD_SLICE, worldSliceFixture()));
        MethodNode prepare = method(node, "prepare");

        boolean delegateFound = false;
        for (AbstractInsnNode instruction : prepare.instructions.toArray()) {
            if (instruction.getOpcode() == AALOAD) {
                fail("direct storage array load survived in prepare");
            }
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (CHUNK.equals(call.owner) && ("()[" + EBS).equals(call.desc)) {
                    fail("direct getBlockStorageArray call survived in prepare");
                }
                if (COMPAT.equals(call.owner)) {
                    delegateFound = true;
                    assertEquals("getSection", call.name);
                    assertEquals(GET_SECTION_DESC, call.desc);
                }
            }
        }
        assertTrue("no getSection delegate injected", delegateFound);
    }

    @Test
    public void getChunkSectionDelegates() {
        ClassNode node = read(transformer.transform(CLONED_SECTION, CLONED_SECTION, clonedSectionFixture()));
        MethodNode method = method(node, "getChunkSection");

        AbstractInsnNode first = method.instructions.getFirst();
        assertTrue(first instanceof org.objectweb.asm.tree.VarInsnNode);
        MethodInsnNode delegate = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                delegate = (MethodInsnNode) instruction;
                break;
            }
        }
        assertNotNullCompat(delegate);
        assertEquals(COMPAT, delegate.owner);
        assertEquals("getSection", delegate.name);
        assertEquals(GET_SECTION_DESC, delegate.desc);
    }

    @Test
    public void celeritasBuildHeightsDelegate() {
        ClassNode node = read(transformer.transform(WORLD_RENDERER, WORLD_RENDERER, celeritasRendererFixture()));

        assertHeadDelegate(method(node, "getMinimumBuildHeight"), "getMinBuildHeight");
        assertHeadDelegate(method(node, "getMaximumBuildHeight"), "getMaxBuildHeight");
    }

    @Test
    public void actiniumShimMethodsAreGatedOnExtendedHeight() {
        ClassNode node = read(transformer.transform(DEPTHS_UPDATE_COMPAT, DEPTHS_UPDATE_COMPAT, depthsUpdateCompatFixture()));

        assertGatedHead(method(node, "getMinSection"), "getMinSection");
        assertGatedHead(method(node, "getMaxSection"), "getMaxSection");
        assertGatedHead(method(node, "getSectionCount"), "getSectionCount");
        assertGatedHead(method(node, "getMinBuildHeight"), "getMinBuildHeight");

        MethodNode toStorageIndex = method(node, "toStorageIndex");
        assertGatedHead(toStorageIndex, "toStorageIndex");
        // The gated return must forward the sectionY argument (local 1) to the helper.
        AbstractInsnNode jump = toStorageIndex.instructions.getFirst().getNext();
        AbstractInsnNode load = jump.getNext();
        assertTrue(load instanceof org.objectweb.asm.tree.VarInsnNode);
        assertEquals(ILOAD, load.getOpcode());
        assertEquals(1, ((org.objectweb.asm.tree.VarInsnNode) load).var);
    }

    @Test
    public void actiniumMaxBuildHeightIsGated() {
        ClassNode node = read(transformer.transform(ACTINIUM_RENDERER, ACTINIUM_RENDERER, actiniumRendererFixture()));
        assertGatedHead(method(node, "getMaximumBuildHeight"), "getMaxBuildHeight");
    }

    @Test
    public void unrelatedClassesPassThroughUnchanged() {
        byte[] fixture = celeritasRendererFixture();
        byte[] result = transformer.transform("com.example.Other", "com.example.Other", fixture);
        assertEquals(fixture.length, result.length);
    }

    @Test
    public void missingCreateBoundsFailLoudly() {
        ClassWriter writer = newClass(SECTION_MANAGER.replace('.', '/'));
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "create", "()V", null, null);
        method.visitCode();
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        // isSectionVisuallyEmpty is required too, but the create failure must fire first.
        try {
            transformer.transform(SECTION_MANAGER, SECTION_MANAGER, writer.toByteArray());
            fail("Expected a loud failure when the create bounds are missing");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Celeritas"));
        }
    }

    @Test
    public void missingPrepareLookupFailsLoudly() {
        ClassWriter writer = newClass(WORLD_SLICE.replace('.', '/'));
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "prepare", "()V", null, null);
        method.visitCode();
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        try {
            transformer.transform(WORLD_SLICE, WORLD_SLICE, writer.toByteArray());
            fail("Expected a loud failure when the storage lookup is missing");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Celeritas"));
        }
    }

    @Test
    public void missingActiniumShimMethodFailsLoudly() {
        ClassWriter writer = newClass(DEPTHS_UPDATE_COMPAT.replace('.', '/'));
        writer.visitEnd();
        try {
            transformer.transform(DEPTHS_UPDATE_COMPAT, DEPTHS_UPDATE_COMPAT, writer.toByteArray());
            fail("Expected a loud failure when the shim methods are missing");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Actinium"));
        }
    }

    private static void assertHeadDelegate(MethodNode method, String helper) {
        AbstractInsnNode first = method.instructions.getFirst();
        assertTrue(first instanceof MethodInsnNode);
        assertEquals(COMPAT, ((MethodInsnNode) first).owner);
        assertEquals(helper, ((MethodInsnNode) first).name);
        assertEquals(IRETURN, first.getNext().getOpcode());
    }

    private static void assertGatedHead(MethodNode method, String helper) {
        AbstractInsnNode first = method.instructions.getFirst();
        assertTrue(first instanceof MethodInsnNode);
        assertEquals(COMPAT, ((MethodInsnNode) first).owner);
        assertEquals("useExtendedHeight", ((MethodInsnNode) first).name);

        AbstractInsnNode second = first.getNext();
        assertTrue(second instanceof JumpInsnNode);
        assertEquals(IFEQ, second.getOpcode());

        MethodInsnNode delegate = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && helper.equals(((MethodInsnNode) instruction).name)) {
                delegate = (MethodInsnNode) instruction;
                break;
            }
        }
        assertNotNullCompat(delegate);
        assertEquals(COMPAT, delegate.owner);
    }

    private static byte[] sectionManagerFixture() {
        String self = SECTION_MANAGER.replace('.', '/');
        ClassWriter writer = newClass(self);
        writer.visitField(ACC_PRIVATE | ACC_FINAL, "world", "Lnet/minecraft/client/multiplayer/WorldClient;",
                null, null).visitEnd();
        // <init>(int, int): stands in for the section-bounded constructor
        MethodVisitor init = writer.visitMethod(ACC_PUBLIC, "<init>", "(II)V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(1, 3);
        init.visitEnd();
        // create(...): hardcoded 0/16 bounds, like the release jar
        MethodVisitor create = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "create",
                "(Lnet/minecraft/client/multiplayer/WorldClient;)L" + self + ";", null, null);
        create.visitCode();
        create.visitTypeInsn(NEW, self);
        create.visitInsn(DUP);
        create.visitInsn(ICONST_0);
        create.visitIntInsn(BIPUSH, 16);
        create.visitMethodInsn(INVOKESPECIAL, self, "<init>", "(II)V", false);
        create.visitInsn(ARETURN);
        create.visitMaxs(4, 1);
        create.visitEnd();
        // isSectionVisuallyEmpty(int, int, int): the vanilla [0, length) guard plus storage lookup
        MethodVisitor empty = writer.visitMethod(ACC_PROTECTED, "isSectionVisuallyEmpty", "(III)Z", null, null);
        empty.visitCode();
        empty.visitVarInsn(ALOAD, 0);
        empty.visitFieldInsn(GETFIELD, self, "world", "Lnet/minecraft/client/multiplayer/WorldClient;");
        empty.visitVarInsn(ILOAD, 1);
        empty.visitVarInsn(ILOAD, 3);
        empty.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/client/multiplayer/WorldClient",
                "func_72964_e", "(II)L" + CHUNK + ";", false);
        empty.visitVarInsn(ASTORE, 4);
        empty.visitVarInsn(ALOAD, 4);
        empty.visitMethodInsn(INVOKEVIRTUAL, CHUNK, "func_76621_g", "()Z", false);
        Label notEmpty = new Label();
        empty.visitJumpInsn(IFEQ, notEmpty);
        empty.visitInsn(ICONST_1);
        empty.visitInsn(IRETURN);
        empty.visitLabel(notEmpty);
        empty.visitInsn(ICONST_0);
        empty.visitInsn(IRETURN);
        empty.visitMaxs(4, 5);
        empty.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] worldSliceFixture() {
        String self = WORLD_SLICE.replace('.', '/');
        ClassWriter writer = newClass(self);
        // prepare(Chunk, SectionPos): the direct storageArray[origin.y()] lookup from the release jar
        MethodVisitor prepare = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "prepare",
                "(L" + CHUNK + ";Lorg/embeddedt/embeddium/impl/util/position/SectionPos;)" + EBS, null, null);
        prepare.visitCode();
        prepare.visitVarInsn(ALOAD, 0);
        prepare.visitMethodInsn(INVOKEVIRTUAL, CHUNK, "func_76587_i", "()[" + EBS, false);
        prepare.visitVarInsn(ALOAD, 1);
        prepare.visitMethodInsn(INVOKEVIRTUAL, "org/embeddedt/embeddium/impl/util/position/SectionPos",
                "y", "()I", false);
        prepare.visitInsn(AALOAD);
        prepare.visitInsn(ARETURN);
        prepare.visitMaxs(2, 2);
        prepare.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] clonedSectionFixture() {
        String self = CLONED_SECTION.replace('.', '/');
        ClassWriter writer = newClass(self);
        // getChunkSection(Chunk, int): the guarded storageArray[y] lookup from the release jar
        MethodVisitor method = writer.visitMethod(ACC_PRIVATE | ACC_STATIC, "getChunkSection",
                GET_SECTION_DESC, null, null);
        method.visitCode();
        method.visitInsn(ACONST_NULL);
        method.visitVarInsn(ASTORE, 2);
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKEVIRTUAL, CHUNK, "func_76587_i", "()[" + EBS, false);
        method.visitVarInsn(ASTORE, 3);
        method.visitVarInsn(ILOAD, 1);
        Label done = new Label();
        method.visitJumpInsn(IFLT, done);
        method.visitVarInsn(ILOAD, 1);
        method.visitVarInsn(ALOAD, 3);
        method.visitInsn(ARRAYLENGTH);
        method.visitJumpInsn(IF_ICMPGE, done);
        method.visitVarInsn(ALOAD, 3);
        method.visitVarInsn(ILOAD, 1);
        method.visitInsn(AALOAD);
        method.visitVarInsn(ASTORE, 2);
        method.visitLabel(done);
        method.visitVarInsn(ALOAD, 2);
        method.visitInsn(ARETURN);
        method.visitMaxs(2, 4);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] celeritasRendererFixture() {
        String self = WORLD_RENDERER.replace('.', '/');
        ClassWriter writer = newClass(self);
        MethodVisitor min = writer.visitMethod(ACC_PUBLIC, "getMinimumBuildHeight", "()I", null, null);
        min.visitCode();
        min.visitInsn(ICONST_0);
        min.visitInsn(IRETURN);
        min.visitMaxs(1, 1);
        min.visitEnd();
        MethodVisitor max = writer.visitMethod(ACC_PUBLIC, "getMaximumBuildHeight", "()I", null, null);
        max.visitCode();
        max.visitIntInsn(SIPUSH, 256);
        max.visitInsn(IRETURN);
        max.visitMaxs(1, 1);
        max.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] depthsUpdateCompatFixture() {
        String self = DEPTHS_UPDATE_COMPAT.replace('.', '/');
        ClassWriter writer = newClass(self);
        String worldDesc = "(Lnet/minecraft/world/World;)I";
        constantIntMethod(writer, "getMinSection", worldDesc, 0);
        constantIntMethod(writer, "getMaxSection", worldDesc, 16);
        constantIntMethod(writer, "getSectionCount", worldDesc, 16);
        constantIntMethod(writer, "getMinBuildHeight", worldDesc, 0);
        // toStorageIndex(World, int): vanilla identity mapping
        MethodVisitor toIndex = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "toStorageIndex",
                "(Lnet/minecraft/world/World;I)I", null, null);
        toIndex.visitCode();
        toIndex.visitVarInsn(ILOAD, 1);
        Label inRange = new Label();
        toIndex.visitJumpInsn(IFGE, inRange);
        toIndex.visitInsn(ICONST_M1);
        toIndex.visitInsn(IRETURN);
        toIndex.visitLabel(inRange);
        toIndex.visitVarInsn(ILOAD, 1);
        toIndex.visitInsn(IRETURN);
        toIndex.visitMaxs(1, 2);
        toIndex.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] actiniumRendererFixture() {
        String self = ACTINIUM_RENDERER.replace('.', '/');
        ClassWriter writer = newClass(self);
        MethodVisitor max = writer.visitMethod(ACC_PUBLIC, "getMaximumBuildHeight", "()I", null, null);
        max.visitCode();
        max.visitIntInsn(SIPUSH, 256);
        max.visitInsn(IRETURN);
        max.visitMaxs(1, 1);
        max.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void constantIntMethod(ClassWriter writer, String name, String desc, int value) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, name, desc, null, null);
        method.visitCode();
        if (value == 0) {
            method.visitInsn(ICONST_0);
        } else {
            method.visitIntInsn(BIPUSH, value);
        }
        method.visitInsn(IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }

    private static ClassWriter newClass(String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, name, null, "java/lang/Object", null);
        return writer;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(ASM5);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name)) {
                return method;
            }
        }
        fail("no method " + name + " in " + node.name);
        return null;
    }

    private static void assertNotNullCompat(MethodInsnNode delegate) {
        if (delegate == null) {
            fail("no delegate call injected");
        }
    }
}
