package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.ExtendedChunkAPI;
import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

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
            constant = @Constant(intValue = 65535), require = 1, allow = 1)
    private int cavebiomes$fullMask(int original) {
        return ExtendedChunkAPI.fullSectionMask();
    }

    /**
     * The constructor filters tile entities with {@code 1 << (pos.getY() >> 4)}. Section packet
     * masks use normalized storage-array indices, so make that one Y read relative to minY.
     */
    @Redirect(
            method = "<init>(Lnet/minecraft/world/chunk/Chunk;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;getY()I"),
            require = 1,
            allow = 1)
    private int cavebiomes$normalizeTileEntitySectionY(BlockPos pos) {
        return cavebiomes$normalizedTileEntityY(pos.getY());
    }

    @Unique
    static int cavebiomes$normalizedTileEntityY(int worldY) {
        return worldY - WorldHeightAPI.getMinY();
    }
}
