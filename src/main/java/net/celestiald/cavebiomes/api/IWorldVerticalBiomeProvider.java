package net.celestiald.cavebiomes.api;

import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Supplies a height-aware biome with access to the world whose biome is being resolved.
 *
 * <p>This is the preferred provider contract for seed-dependent or dimension-dependent cave
 * biomes. Implementations are called from both logical sides and may be called from render
 * workers, so they must remain deterministic, side-effect-free, and thread-safe.</p>
 */
@FunctionalInterface
public interface IWorldVerticalBiomeProvider {

    /**
     * Cheap applicability guard for generation and lookup hot paths. Providers
     * which only own selected worlds should override this method.
     */
    default boolean appliesTo(World world) {
        return true;
    }

    /**
     * @param world world owning the queried chunk
     * @param x world block X
     * @param y world block Y
     * @param z world block Z
     * @param base biome resolved by vanilla and earlier providers
     * @return the overriding biome, or {@code base}/{@code null} to defer
     */
    Biome getBiome(World world, int x, int y, int z, Biome base);
}
