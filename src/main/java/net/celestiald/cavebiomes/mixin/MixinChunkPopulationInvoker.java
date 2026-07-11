package net.celestiald.cavebiomes.mixin;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Calls Chunk's protected single-chunk population method after the required region is loaded. */
@Mixin(Chunk.class)
public interface MixinChunkPopulationInvoker {
    @Invoker("populate")
    void cavebiomes$populate(IChunkGenerator generator);
}
