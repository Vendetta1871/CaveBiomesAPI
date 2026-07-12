package net.celestiald.cavebiomes.api;

/**
 * Opts a chunk generator into population only after a square of neighboring chunks is loaded.
 *
 * <p>Vanilla 1.12 populates from a two-by-two area because its decorators are offset toward the
 * positive chunk edges. Generators whose features can cross every edge should return the number
 * of neighboring chunks they may read or write. CaveBiomesAPI then delays population until that
 * complete square is loaded, preventing feature reads from recursively generating chunks.
 * Overlapping centers are populated in a stable local phase order, so load request order cannot
 * reverse two feature passes that may write the same block.</p>
 */
public interface IExtendedPopulationGenerator {
    /** Returns a required horizontal radius in the inclusive range {@code 1..8}. */
    int getPopulationRadius();
}
