package net.celestiald.cavebiomes.api;

/**
 * Marker for world types which deliberately opt their Overworld into the
 * configured finite-height range.
 *
 * <p>Cave Biomes API keeps its wider chunk storage available process-wide, but
 * only applies extended build, lighting, portal, and entity semantics to world
 * types carrying this marker. Existing and unrelated worlds therefore retain
 * vanilla 0..256 behavior.</p>
 */
public interface IExtendedHeightWorldType {
}
