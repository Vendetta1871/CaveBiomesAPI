# CaveBiomesAPI

**CaveBiomesAPI** is a core/library mod for Minecraft 1.12.2 designed specifically for mod developers. It significantly expands the vanilla world generation capabilities by introducing vertical (3D) biomes and custom world height limits.

If you've ever wanted to create massive underground ecosystems with different biomes stacked beneath the surface, or high mountains scrapping the sky, this API is for you!

By default the mod is fully transparent — no registrations means byte-for-byte vanilla behavior. Everything is opt-in.

## Features

* **Vertical Biomes (3D Biomes):** Allows developers to generate completely different biomes at varying Y-levels (heights) in the same column. You can easily stack different biomes one under another (e.g., a lush surface biome with a glowing mushroom cave biome directly underneath).
* **World Height Configuration:** Gives modders the ability to easily configure and extend the world height limits in 1.12.2.
* **Developer-Friendly:** Provides a clean and accessible API to integrate complex underground world generation into your own mods.

## Requirements

* Minecraft 1.12.2 / Forge 14.23.5.2864
* **[MixinBootstrap](https://www.curseforge.com/minecraft/mc-mods/mixinbootstrap)** (runtime dependency)

---

## Adding as a Dependency

### Step 1 — Configure your mod's `build.gradle`

```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    // compile-time access to the API; not bundled in your output jar
    deobfCompile 'com.github.Vendetta1871:CaveBiomesAPI:1.1.0'
}
```

### Step 2 — Declare the dependency in `mcmod.info`

```json
"dependencies": ["cavebiomesapi"]
```

This tells Forge to load CaveBiomesAPI before your mod.

At runtime, drop both `cavebiomesapi-1.1.0.jar` and your mod jar into the `mods/` folder.

---

## API Reference

### World Height API

World height is configured by the **player/server admin** in `config/cavebiomesapi.cfg`:

```ini
general {
    minY = -64   # default: 0
    maxY = 320   # default: 256
}
```

Read the active values in code (available after `FMLPreInitializationEvent`):

```java
import net.celestiald.cavebiomes.api.WorldHeightAPI;

int minY = WorldHeightAPI.getMinY();
int maxY = WorldHeightAPI.getMaxY();
```

The API is **read-only** — height is a world property and cannot be changed at runtime.

---

### Vertical Biome API

Lets you stack different biomes by Y coordinate in the same X/Z column. Covers both **biome identity** (what `World.getBiome` returns, including tints and F3 display) and **terrain generation** (cave surfaces are coated with the resolved biome's surface block, 1.18-style).

#### Register a provider

```java
import net.celestiald.cavebiomes.api.BiomeLayerAPI;
import net.minecraft.init.Biomes;

// In your mod's FMLInitializationEvent handler:
BiomeLayerAPI.register((x, y, z, base) -> y < 30 ? Biomes.MESA : base);
```

Seed- or dimension-dependent providers should use the world-aware overload:

```java
BiomeLayerAPI.register((IWorldVerticalBiomeProvider) (world, x, y, z, base) ->
        world.provider.getDimension() == 0 && y < 30 ? Biomes.MESA : base);
```

### Extended chunk generation

Generators which fill the configured range directly should use `ExtendedChunkAPI`. It validates
the active range, creates signed-Y `ExtendedBlockStorage` sections, performs generation-time
section writes, and exposes the exact full-section packet mask.

```java
ExtendedChunkAPI.requireRange("examplemod", -64, 320);
ExtendedChunkAPI.setBlockState(chunk, localX, worldY, localZ, state,
        world.provider.hasSkyLight());
```

The server synchronizes its active range to multiplayer clients before chunk streaming. A client
restores its local configured range on disconnect.

`IVerticalBiomeProvider` is a `@FunctionalInterface`:

```java
package net.celestiald.cavebiomes.api;
import net.minecraft.world.biome.Biome;

@FunctionalInterface
public interface IVerticalBiomeProvider {
    /**
     * @param x, y, z  world coordinates of the queried block
     * @param base      biome resolved so far (vanilla biome, or the result of a prior provider)
     * @return the overriding biome, or {@code base} / {@code null} to defer to the next provider
     */
    Biome getBiome(int x, int y, int z, Biome base);
}
```

#### Chaining multiple providers

Multiple providers chain in registration order — each receives the result of the previous one:

```java
// Below Y=0 → Nether Wastes
BiomeLayerAPI.register((x, y, z, base) -> y < 0 ? Biomes.HELL : base);

// Y 0..30 → Mesa
BiomeLayerAPI.register((x, y, z, base) -> (y >= 0 && y < 30) ? Biomes.MESA : base);

// Above Y=200 → Ice Plains
BiomeLayerAPI.register((x, y, z, base) -> y >= 200 ? Biomes.ICE_PLAINS : base);
```

#### Managing providers

```java
IVerticalBiomeProvider p = (x, y, z, base) -> ...;

BiomeLayerAPI.register(p);    // add
BiomeLayerAPI.unregister(p);  // remove one (keep a reference if you need to remove it later)
BiomeLayerAPI.clear();        // remove all
```

#### What changes with a registered provider

| System | Effect |
|---|---|
| `World.getBiome` / `ChunkCache.getBiome` | Returns the Y-resolved biome |
| F3 biome display, fog/sky tint, grass/foliage color | Driven by the resolved biome |
| Cave surface decoration | Exposed stone faces (floor, walls, ceiling) inside the Y-band are replaced with the resolved biome's surface block |
| Bulk rock between caves, ores, liquids, bedrock | Unchanged |
| `Chunk.blockBiomeArray` (saved to disk) | Not modified — the override is applied at read time, storage stays vanilla |

#### Rules for providers

* **Pure and side-effect-free** — `resolve` is called per block during rendering on multiple threads.
* **Idempotent** — the same (x, y, z, base) must always return the same result.
* Return `base` or `null` to pass through; return a different `Biome` instance to override.

---

## How it works (for the curious)

* **MixinChunkBiome** — `@Inject` at `RETURN` of `Chunk.getBiome(BlockPos, BiomeProvider)`. Single hook covers both `World.getBiome` (loaded chunk) and `ChunkCache.getBiome` (rendering / mob AI).
* **MixinChunkGeneratorBiome** — `@Inject` at `generateChunk` `RETURN`. After caves are carved, scans cave-air blocks and replaces adjacent STONE faces with the resolved biome's `topBlock`. Direct `ExtendedBlockStorage` writes, no lighting recalculation needed (both stone and surface blocks are opaque full cubes).
* With **no providers registered** both hooks return immediately — generation is byte-for-byte vanilla.

---

## License

[GNU General Public License v3.0 (GPL-3.0)](LICENSE.txt)
