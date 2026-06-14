package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

/**
 * Fills the newly-opened sub-zero space so the overworld feels complete:
 * the vanilla bedrock band at Y 0-4 becomes stone, [minY+1 .. 0) is solid stone,
 * and a fresh bedrock floor is generated at the very bottom (Y == minY).
 *
 * Vanilla terrain (0..255) is untouched; everything above 256 stays open sky.
 * Noise-based terrain / caves / ores below 0 are intentionally out of scope here
 * (that belongs to Stage 2 — 3D biomes).
 */
@Mixin(ChunkGeneratorOverworld.class)
public abstract class MixinChunkGeneratorOverworld {

    @Shadow @Final private World world;

    @Inject(method = "generateChunk", at = @At("RETURN"))
    private void cavebiomes$fillBelowZero(int chunkX, int chunkZ, CallbackInfoReturnable<Chunk> cir) {
        int minY = WorldHeightAPI.getMinY();
        if (minY >= 0) {
            return; // vanilla-floor configuration: nothing to add
        }

        Chunk chunk = cir.getReturnValue();
        ExtendedBlockStorage[] arrays = chunk.getBlockStorageArray();
        boolean hasSky = this.world.provider.hasSkyLight();

        IBlockState stone = Blocks.STONE.getDefaultState();
        IBlockState bedrock = Blocks.BEDROCK.getDefaultState();
        Random rand = new Random((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L);

        // 1) Strip the vanilla bedrock band at Y 0..4 (the floor is moving down).
        for (int y = 0; y <= 4; y++) {
            ExtendedBlockStorage s = sectionFor(arrays, y, hasSky);
            int ly = y & 15;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (s.get(x, ly, z).getBlock() == Blocks.BEDROCK) {
                        s.set(x, ly, z, stone);
                    }
                }
            }
        }

        // 2) Solid stone from minY up to 0, with a randomised bedrock floor at the bottom.
        for (int y = minY; y < 0; y++) {
            ExtendedBlockStorage s = sectionFor(arrays, y, hasSky);
            int ly = y & 15;
            boolean inBedrockBand = y <= minY + 4;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    IBlockState state = (inBedrockBand && rand.nextInt(5) >= (y - minY)) ? bedrock : stone;
                    s.set(x, ly, z, state);
                }
            }
        }

        chunk.markDirty();
    }

    @Unique
    private ExtendedBlockStorage sectionFor(ExtendedBlockStorage[] arrays, int y, boolean hasSky) {
        int idx = WorldHeightAPI.sectionIndex(y);
        ExtendedBlockStorage s = arrays[idx];
        if (s == Chunk.NULL_BLOCK_STORAGE) {
            s = new ExtendedBlockStorage(WorldHeightAPI.sectionYBase(idx), hasSky);
            arrays[idx] = s;
        }
        return s;
    }
}
