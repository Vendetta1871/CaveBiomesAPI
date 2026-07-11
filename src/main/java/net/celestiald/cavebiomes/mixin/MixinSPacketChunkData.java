package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.ExtendedChunkAPI;
import net.minecraft.network.play.server.SPacketChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The "full chunk" sentinel is 65535 (0xFFFF = 16 bits). With more than 16
 * sections that mask can't address sections 16+, and the isFullChunk detection
 * (`mask == 65535`) must agree with the value senders pass. Replace it with the
 * real full mask `(1 << sectionCount) - 1` so the detection still matches the
 * value produced by MixinPlayerChunkMapEntry.
 */
@Mixin(SPacketChunkData.class)
public abstract class MixinSPacketChunkData {

    @ModifyConstant(method = "<init>(Lnet/minecraft/world/chunk/Chunk;I)V",
            constant = @Constant(intValue = 65535))
    private int cavebiomes$fullMask(int original) {
        return ExtendedChunkAPI.fullSectionMask();
    }
}
