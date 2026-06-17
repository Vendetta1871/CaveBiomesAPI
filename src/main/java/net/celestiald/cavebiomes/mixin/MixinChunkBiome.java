package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.BiomeLayerAPI;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stage 2 — applies registered vertical (Y-aware) biome overrides on top of vanilla's 2D
 * (X/Z-only) biome lookup.
 *
 * <p>This is the single runtime hook needed: both {@code World.getBiome} (on a loaded chunk)
 * and {@code ChunkCache.getBiome} (rendering + entity AI) funnel through
 * {@code Chunk.getBiome(BlockPos, BiomeProvider)}. We deliberately do <b>not</b> also hook
 * {@code BiomeProvider.getBiome}: {@code Chunk.getBiome} calls the provider internally on a
 * cache miss, so resolving in both places would apply overrides twice.</p>
 *
 * <p>The vanilla biome is what gets cached into the chunk's 2D {@code blockBiomeArray}; the
 * vertical override is applied fresh on every read using the real Y, so chunk storage / NBT /
 * network stay untouched. With no providers registered {@link BiomeLayerAPI#resolve} returns
 * the base biome unchanged, making this injection a pure pass-through (vanilla behavior).</p>
 *
 * @author CelestialD
 */
@Mixin(Chunk.class)
public abstract class MixinChunkBiome {

    @Inject(
            method = "getBiome(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/biome/BiomeProvider;)Lnet/minecraft/world/biome/Biome;",
            at = @At("RETURN"),
            cancellable = true)
    private void cavebiomes$applyVerticalBiome(BlockPos pos, BiomeProvider provider,
                                               CallbackInfoReturnable<Biome> cir) {
        Biome base = cir.getReturnValue();
        Biome resolved = BiomeLayerAPI.resolve(pos.getX(), pos.getY(), pos.getZ(), base);
        if (resolved != base) {
            cir.setReturnValue(resolved);
        }
    }
}
