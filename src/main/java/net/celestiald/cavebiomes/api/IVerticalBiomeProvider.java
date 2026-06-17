package net.celestiald.cavebiomes.api;

import net.minecraft.world.biome.Biome;

/**
 * Supplies a height-aware (Y) biome override for a column position.
 *
 * <p>Vanilla Minecraft resolves a biome purely from X/Z. Registering a provider via
 * {@link BiomeLayerAPI#register(IVerticalBiomeProvider)} lets a biome depend on Y as well,
 * so two biomes can stack one above another in the same column.</p>
 *
 * <p>Implementations <b>must</b> be idempotent and side-effect-free: {@code getBiome} is
 * called extremely frequently (per block during world rendering) and from multiple threads
 * (render + server). Do no allocation or locking on this path.</p>
 *
 * @author CelestialD
 */
@FunctionalInterface
public interface IVerticalBiomeProvider {

    /**
     * Returns the biome that should apply at this position, or defers.
     *
     * @param x    world block X
     * @param y    world block Y
     * @param z    world block Z
     * @param base the biome resolved so far (vanilla first, then earlier providers in the chain)
     * @return the overriding biome, or {@code base} / {@code null} to leave the running result unchanged
     */
    Biome getBiome(int x, int y, int z, Biome base);
}
