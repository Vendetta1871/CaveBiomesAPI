package net.celestiald.cavebiomes.api;

import net.minecraft.world.biome.Biome;

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
     * server threads. CopyOnWriteArrayList gives lock-free, allocation-free reads.
     */
    private static final List<IVerticalBiomeProvider> PROVIDERS = new CopyOnWriteArrayList<>();

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

    /** Removes a previously registered provider. */
    public static void unregister(IVerticalBiomeProvider provider) {
        PROVIDERS.remove(provider);
        hasProviders = !PROVIDERS.isEmpty();
    }

    /** Removes all providers, returning the API to its inert (vanilla) state. */
    public static void clear() {
        PROVIDERS.clear();
        hasProviders = false;
    }

    /** Whether any provider is registered. Cheap guard for generation-time fast paths. */
    public static boolean hasProviders() {
        return hasProviders;
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
        // Index loop over the CopyOnWriteArrayList snapshot: no iterator allocation.
        for (int i = 0; i < PROVIDERS.size(); i++) {
            Biome r = PROVIDERS.get(i).getBiome(x, y, z, result);
            if (r != null) {
                result = r;
            }
        }
        return result;
    }
}
