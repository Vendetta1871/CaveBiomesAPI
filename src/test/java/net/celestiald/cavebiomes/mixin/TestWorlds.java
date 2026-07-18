package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.IExtendedHeightWorldType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.WorldInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * Shared world fixtures for the mixin contract tests. The extended-height
 * contract is world-driven: it applies only to dimension-0 worlds whose world
 * type carries {@link IExtendedHeightWorldType}. The legacy per-dimension test
 * scenarios map onto these fixtures (dimension 0 → extended Overworld, every
 * other dimension → a vanilla-range world).
 */
final class TestWorlds {

    private static final WorldType EXTENDED_TYPE = new ExtendedTestWorldType();
    private static final WorldType VANILLA_TYPE = new WorldType("cb_test_vanilla");

    private TestWorlds() {
    }

    /** World for a legacy test dimension: extended only for the Overworld. */
    static World forDimension(int dimension) {
        return world(dimension, dimension == 0 ? EXTENDED_TYPE : VANILLA_TYPE);
    }

    /** Overworld whose world type opts into the configured extended range. */
    static World extendedOverworld() {
        return world(0, EXTENDED_TYPE);
    }

    /** WorldServer for a legacy test dimension, built without a running server. */
    static WorldServer serverForDimension(int dimension) {
        try {
            WorldServer world = (WorldServer) UnsafeHolder.UNSAFE
                    .allocateInstance(WorldServer.class);
            Field provider = World.class.getField("provider");
            provider.setAccessible(true);
            provider.set(world, provider(dimension));
            Field worldInfo = World.class.getDeclaredField("worldInfo");
            worldInfo.setAccessible(true);
            worldInfo.set(world, info(dimension == 0 ? EXTENDED_TYPE : VANILLA_TYPE));
            return world;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    /** Concrete MixinChunk harness with its shadowed world field installed. */
    static MixinChunk chunkFor(World world) {
        try {
            TestChunk chunk = new TestChunk();
            Field field = MixinChunk.class.getDeclaredField("world");
            field.setAccessible(true);
            field.set(chunk, world);
            return chunk;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    /** Command sender standing in the given world at the block origin. */
    static ICommandSender sender(final World world) {
        return (ICommandSender) Proxy.newProxyInstance(
                ICommandSender.class.getClassLoader(),
                new Class<?>[]{ICommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getEntityWorld")) {
                        return world;
                    }
                    if (method.getName().equals("getPosition")) {
                        return BlockPos.ORIGIN;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static World world(int dimension, WorldType type) {
        return new TestWorld(info(type), provider(dimension));
    }

    private static WorldInfo info(WorldType type) {
        return new WorldInfo(new WorldSettings(0L, GameType.SURVIVAL, true, false, type),
                "cavebiomes-test");
    }

    private static WorldProvider provider(int dimension) {
        TestWorldProvider provider = new TestWorldProvider();
        provider.setDimension(dimension);
        return provider;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    private static final class ExtendedTestWorldType extends WorldType
            implements IExtendedHeightWorldType {
        ExtendedTestWorldType() {
            super("cb_test_extended");
        }
    }

    private static final class TestWorldProvider extends WorldProvider {
        @Override
        public DimensionType getDimensionType() {
            return DimensionType.OVERWORLD;
        }
    }

    private static final class TestWorld extends World {
        TestWorld(WorldInfo info, WorldProvider provider) {
            super(null, info, provider, new Profiler(), false);
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return false;
        }
    }

    /** Installs only the shadowed members the contract tests exercise. */
    private static final class TestChunk extends MixinChunk {
        @Override
        public boolean canSeeSky(BlockPos pos) {
            return false;
        }

        @Override
        public void markDirty() {
        }

        @Override
        protected void propagateSkylightOcclusion(int x, int z) {
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos, Chunk.EnumCreateEntityType createType) {
            return null;
        }

        @Override
        public int getTopFilledSegment() {
            return 0;
        }

        @Override
        protected void populate(IChunkGenerator generator) {
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.getDefaultState();
        }
    }

    private static final class UnsafeHolder {
        private static final sun.misc.Unsafe UNSAFE = create();

        private static sun.misc.Unsafe create() {
            try {
                Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                return (sun.misc.Unsafe) field.get(null);
            } catch (ReflectiveOperationException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }
}
