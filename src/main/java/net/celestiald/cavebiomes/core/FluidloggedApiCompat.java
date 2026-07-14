package net.celestiald.cavebiomes.core;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.event.world.ChunkWatchEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * Runtime-only bridge for Fluidlogged API 3.x; it has no hard dependency on that mod.
 *
 * @author CelestialD
 */
public final class FluidloggedApiCompat {
    private static final String PAGE_LIST = "cavebiomesPages";
    private static final String PAGE_NUMBER = "page";
    private static final String PAGE_DATA = "data";
    private static final String SERIALIZE_PAGE = "cavebiomes$serializePageNBT";
    private static final String DESERIALIZE_PAGE = "cavebiomes$deserializePageNBT";

    // Values never reference their weak owner key, so unloaded chunks remain collectable.
    private static final Map<Object, Map<Integer, Object>> PAGES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Integer> PAGE_NUMBERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FluidloggedApiCompat() {}

    /** Returns an independent 256-block container for the requested world-Y page. */
    public static Object getContainer(Object owner, int worldY) {
        int page = Math.floorDiv(worldY, 256);
        if (page == 0) return owner;

        synchronized (PAGES) {
            Map<Integer, Object> pages = PAGES.get(owner);
            if (pages == null) {
                pages = new HashMap<>();
                PAGES.put(owner, pages);
            }
            Object container = pages.get(page);
            if (container == null) {
                container = newPage(owner);
                pages.put(page, container);
                PAGE_NUMBERS.put(container, page);
            }
            return container;
        }
    }

    /** Restores the page base which Fluidlogged API's char position cannot encode itself. */
    public static int deserializeY(Object container, int serializedPos) {
        Integer page = PAGE_NUMBERS.get(container);
        return (page == null ? 0 : page * 256) + ((serializedPos & 0xFFFF) >>> 8);
    }

    /** Persists the vanilla page unchanged unless an extended page actually contains fluid. */
    public static NBTBase serializeCapability(Object owner) {
        NBTBase vanilla = serializePage(owner);
        Map<Integer, Object> snapshot = pageSnapshot(owner);
        boolean hasExtendedData = false;
        for (Object page : snapshot.values()) {
            if (!isEmpty(page)) {
                hasExtendedData = true;
                break;
            }
        }
        if (!hasExtendedData) return vanilla;

        NBTTagList pages = new NBTTagList();
        appendPage(pages, 0, vanilla);
        for (Map.Entry<Integer, Object> entry : snapshot.entrySet()) {
            if (!isEmpty(entry.getValue())) {
                appendPage(pages, entry.getKey(), serializePage(entry.getValue()));
            }
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setTag(PAGE_LIST, pages);
        return root;
    }

    /** Reads both Fluidlogged API's original page-zero data and the extended page envelope. */
    public static void deserializeCapability(Object owner, NBTBase serialized) {
        if (!(serialized instanceof NBTTagCompound)
                || !((NBTTagCompound) serialized).hasKey(PAGE_LIST, 9)) {
            clear(owner);
            deserializePage(owner, serialized);
            return;
        }

        clear(owner);
        NBTTagList pages = ((NBTTagCompound) serialized).getTagList(PAGE_LIST, 10);
        for (int i = 0; i < pages.tagCount(); i++) {
            NBTTagCompound page = pages.getCompoundTagAt(i);
            int pageNumber = page.getInteger(PAGE_NUMBER);
            Object container = pageNumber == 0 ? owner : getContainer(owner, pageNumber * 256);
            deserializePage(container, page.getTag(PAGE_DATA));
        }
    }

    /** Adds non-empty below-zero and above-255 pages after Fluidlogged API's normal page-zero sync. */
    public static void syncExtendedPages(ChunkWatchEvent.Watch event) {
        Chunk chunk = event.getChunkInstance();
        if (chunk == null || isCubicWorld(chunk)) return;

        Object capability = invoke(chunk, "getFluidStateCapability");
        if (capability == null) return;

        int minimumPage = Math.floorDiv(WorldHeightAPI.getMinY(), 256);
        int maximumPage = Math.floorDiv(WorldHeightAPI.getMaxY() - 1, 256);
        for (Map.Entry<Integer, Object> entry : pageSnapshot(capability).entrySet()) {
            int page = entry.getKey();
            if (page < minimumPage || page > maximumPage
                    || isEmpty(entry.getValue())) continue;
            sendPage(chunk, event, capability, page * 256);
        }
    }

    private static Object newPage(Object owner) {
        try {
            Field offsetX = owner.getClass().getDeclaredField("offsetX");
            Field offsetZ = owner.getClass().getDeclaredField("offsetZ");
            offsetX.setAccessible(true);
            offsetZ.setAccessible(true);
            Constructor<?> constructor = owner.getClass().getConstructor(int.class, int.class);
            return constructor.newInstance(offsetX.getInt(owner) >> 4, offsetZ.getInt(owner) >> 4);
        } catch (ReflectiveOperationException e) {
            throw failure("create a vertical fluid-state page", e);
        }
    }

    private static Map<Integer, Object> pageSnapshot(Object owner) {
        synchronized (PAGES) {
            Map<Integer, Object> pages = PAGES.get(owner);
            return pages == null ? Collections.emptyMap() : new TreeMap<>(pages);
        }
    }

    private static NBTBase serializePage(Object page) {
        return (NBTBase) invoke(page, SERIALIZE_PAGE);
    }

    private static void deserializePage(Object page, NBTBase serialized) {
        invoke(page, DESERIALIZE_PAGE, NBTBase.class, serialized);
    }

    private static boolean isEmpty(Object page) {
        Object positions = invoke(page, "getSerializedPositions");
        return positions instanceof Set && ((Set<?>) positions).isEmpty();
    }

    private static void appendPage(NBTTagList pages, int pageNumber, NBTBase data) {
        NBTTagCompound page = new NBTTagCompound();
        page.setInteger(PAGE_NUMBER, pageNumber);
        page.setTag(PAGE_DATA, data);
        pages.appendTag(page);
    }

    private static void clear(Object owner) {
        invoke(owner, "clearFluidStates");
        synchronized (PAGES) {
            Map<Integer, Object> pages = PAGES.remove(owner);
            if (pages != null) {
                for (Object page : pages.values()) PAGE_NUMBERS.remove(page);
            }
        }
    }

    private static boolean isCubicWorld(Chunk chunk) {
        try {
            ClassLoader loader = FluidloggedApiCompat.class.getClassLoader();
            Class<?> api = Class.forName("git.jbredwards.fluidlogged_api.mod.FluidloggedAPI", false, loader);
            if (!api.getField("isCubicChunks").getBoolean(null)) return false;
            Class<?> hooks = Class.forName(
                    "git.jbredwards.fluidlogged_api.mod.asm.plugins.vanilla.world.PluginChunk$CCHooks",
                    false, loader);
            return (Boolean) hooks.getMethod("isCubicWorld", net.minecraft.world.World.class)
                    .invoke(null, chunk.getWorld());
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException e) {
            throw failure("check Fluidlogged API's CubicChunks mode", e);
        }
    }

    private static void sendPage(Chunk chunk, ChunkWatchEvent.Watch event,
            Object capability, int pageBase) {
        try {
            ClassLoader loader = FluidloggedApiCompat.class.getClassLoader();
            Class<?> capabilityType = Class.forName(
                    "git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability", false, loader);
            Class<?> messageType = Class.forName(
                    "git.jbredwards.fluidlogged_api.mod.common.message.SMessageSyncFluidStates", false, loader);
            Constructor<?> constructor = messageType.getConstructor(
                    int.class, int.class, int.class, capabilityType);
            IMessage message = (IMessage) constructor.newInstance(
                    chunk.x, pageBase, chunk.z, capability);

            Class<?> api = Class.forName("git.jbredwards.fluidlogged_api.mod.FluidloggedAPI", false, loader);
            SimpleNetworkWrapper wrapper = (SimpleNetworkWrapper) api.getField("WRAPPER").get(null);
            wrapper.sendTo(message, event.getPlayer());
        } catch (ReflectiveOperationException e) {
            throw failure("sync an extended fluid-state page", e);
        }
    }

    private static Object invoke(Object target, String name) {
        return invoke(target, name, new Class<?>[0], new Object[0]);
    }

    private static Object invoke(Object target, String name, Class<?> parameter, Object argument) {
        return invoke(target, name, new Class<?>[]{parameter}, new Object[]{argument});
    }

    private static Object invoke(Object target, String name, Class<?>[] parameters, Object[] arguments) {
        try {
            return target.getClass().getMethod(name, parameters).invoke(target, arguments);
        } catch (ReflectiveOperationException e) {
            throw failure("invoke Fluidlogged API " + name, e);
        }
    }

    private static IllegalStateException failure(String action, ReflectiveOperationException error) {
        Throwable cause = error instanceof InvocationTargetException && error.getCause() != null
                ? error.getCause() : error;
        return new IllegalStateException("Cave Biomes API could not " + action, cause);
    }
}
