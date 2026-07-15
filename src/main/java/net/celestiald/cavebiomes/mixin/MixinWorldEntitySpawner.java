package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.World;
import net.minecraft.world.WorldEntitySpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Random;

/** Includes negative Overworld sections in vanilla's initial natural-spawn Y selection. */
@Mixin(WorldEntitySpawner.class)
public abstract class MixinWorldEntitySpawner {

    @Unique
    private static int cavebiomes$sampleSpawnY(Random random, int vanillaUpperBound,
            World world) {
        int minimumY = WorldHeightAPI.usesExtendedHeight(world)
                ? WorldHeightAPI.getMinY() : 0;
        return minimumY + random.nextInt(vanillaUpperBound - minimumY);
    }

    @Redirect(
            method = "getRandomChunkPosition",
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/Chunk;getHeight(Lnet/minecraft/util/math/BlockPos;)I"),
                    to = @At("TAIL")),
            at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I",
                    ordinal = 0, remap = false),
            require = 1,
            allow = 1)
    private static int cavebiomes$selectSpawnY(Random random, int vanillaUpperBound,
            World world, int chunkX, int chunkZ) {
        return cavebiomes$sampleSpawnY(random, vanillaUpperBound, world);
    }
}
