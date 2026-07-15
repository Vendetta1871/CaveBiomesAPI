package net.celestiald.cavebiomes.api;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.World;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Public API for vertical (height-bounded) biomes — Stage 2 of CaveBiomesAPI.
 *
 * <p>Other mods register an {@link IVerticalBiomeProvider} to make biome lookup depend on Y,
 * so two different biomes can occupy the same X/Z column at different heights. With no
 * providers registered the API is completely inert: {@link #resolve} returns the vanilla
 * biome unchanged, so default behavior is byte-for-byte vanilla.</p>
 *
 * <p>Providers form an ordered chain (registration order). Each receives the running result
 * (vanilla first, then earlier providers) and may return a new biome to apply or {@code null}
 * / the same {@code base} to defer; the last non-null override wins.</p>
 *
 * @author CelestialD
 */
public final class BiomeLayerAPI {

    private BiomeLayerAPI() {}

    /**
     * Registration is rare; iteration is hot and happens concurrently on the render and
     * server threads. CopyOnWriteArrayList gives lock-free snapshot reads.
     */
    private static final List<IVerticalBiomeProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<IWorldVerticalBiomeProvider> WORLD_PROVIDERS =
            new CopyOnWriteArrayList<>();

    /**
     * Hot-path guard. A single volatile read lets {@link #resolve} short-circuit to the
     * vanilla value when nothing is registered, keeping the default path ~free and ensuring
     * the mixin hooks behave as pure pass-throughs.
     */
    private static volatile boolean hasProviders = false;

    /** Registers a vertical biome provider. Order of registration is the chain order. */
    public static void register(IVerticalBiomeProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        PROVIDERS.add(provider);
        hasProviders = true;
    }

    /** Registers a world-aware provider for seed- or dimension-dependent biome layers. */
    public static void register(IWorldVerticalBiomeProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        WORLD_PROVIDERS.add(provider);
        hasProviders = true;
    }

    /** Removes a previously registered provider. */
    public static void unregister(IVerticalBiomeProvider provider) {
        PROVIDERS.remove(provider);
        refreshGuard();
    }

    /** Removes a previously registered world-aware provider. */
    public static void unregister(IWorldVerticalBiomeProvider provider) {
        WORLD_PROVIDERS.remove(provider);
        refreshGuard();
    }

    /** Removes all providers, returning the API to its inert (vanilla) state. */
    public static void clear() {
        PROVIDERS.clear();
        WORLD_PROVIDERS.clear();
        hasProviders = false;
    }

    /** Whether any provider is registered. Cheap guard for generation-time fast paths. */
    public static boolean hasProviders() {
        return hasProviders;
    }

    /** Whether at least one registered provider applies to this world. */
    public static boolean hasProviders(World world) {
        if (!hasProviders) {
            return false;
        }
        if (!PROVIDERS.isEmpty()) {
            return true;
        }
        for (IWorldVerticalBiomeProvider provider : WORLD_PROVIDERS) {
            if (provider.appliesTo(world)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the biome at a world position, applying registered vertical overrides on top
     * of the vanilla (X/Z) biome.
     *
     * @param x    world block X
     * @param y    world block Y
     * @param z    world block Z
     * @param base the vanilla biome for this column
     * @return the height-resolved biome (equal to {@code base} when nothing overrides)
     */
    public static Biome resolve(int x, int y, int z, Biome base) {
        if (!hasProviders) {
            return base;
        }
        Biome result = base;
        // CopyOnWriteArrayList iterators retain one stable snapshot even if a
        // provider is concurrently unregistered from another thread.
        for (IVerticalBiomeProvider provider : PROVIDERS) {
            Biome r = provider.getBiome(x, y, z, result);
            if (r != null) {
                result = r;
            }
        }
        return result;
    }

    /** Resolves both legacy coordinate-only providers and world-aware providers. */
    public static Biome resolve(World world, int x, int y, int z, Biome base) {
        if (!hasProviders) {
            return base;
        }
        Biome result = base;
        for (IVerticalBiomeProvider provider : PROVIDERS) {
            Biome resolved = provider.getBiome(x, y, z, result);
            if (resolved != null) {
                result = resolved;
            }
        }
        for (IWorldVerticalBiomeProvider provider : WORLD_PROVIDERS) {
            if (!provider.appliesTo(world)) {
                continue;
            }
            Biome resolved = provider.getBiome(world, x, y, z, result);
            if (resolved != null) {
                result = resolved;
            }
        }
        return result;
    }

    private static void refreshGuard() {
        hasProviders = !PROVIDERS.isEmpty() || !WORLD_PROVIDERS.isEmpty();
    }
}
