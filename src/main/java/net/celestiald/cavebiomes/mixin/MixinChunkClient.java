package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Client-only Chunk patches. Kept separate from {@link MixinChunk} and listed
 * under the "client" section of mixins.cavebiomes.json so the common mixin still
 * applies on a dedicated server (these target methods are @SideOnly(CLIENT) and
 * are stripped from the server jar).
 */
@Mixin(Chunk.class)
public abstract class MixinChunkClient {

    @Shadow private int[] heightMap;
    @Shadow private int heightMapMinimum;
    @Shadow private int[] precipitationHeightMap;
    @Shadow private boolean dirty;

    @Shadow public abstract int getTopFilledSegment();
    @Shadow private int getBlockLightOpacity(int x, int y, int z) { return 0; }

    // =========================================================================
    // generateHeightMap (client): l > 0 -> l > minY
    // =========================================================================

    @Overwrite
    protected void generateHeightMap() {
        int topSeg = this.getTopFilledSegment();
        this.heightMapMinimum = Integer.MAX_VALUE;
        int minY = WorldHeightAPI.getMinY();

        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                this.precipitationHeightMap[j + (k << 4)] = -999;
                for (int l = topSeg + 16; l > minY; --l) {
                    if (this.getBlockLightOpacity(j, l - 1, k) != 0) {
                        this.heightMap[k << 4 | j] = l;
                        if (l < this.heightMapMinimum) this.heightMapMinimum = l;
                        break;
                    }
                }
            }
        }
        this.dirty = true;
    }

    // =========================================================================
    // read(PacketBuffer, int, boolean): vanilla builds each section with
    // `new ExtendedBlockStorage(i << 4, ...)` (i = array index). With minY < 0
    // the yBase must be i*16 + minY. The buffer arg already equals i << 4, so we
    // just add minY here instead of overwriting the whole (obscure) method.
    // =========================================================================

    @Redirect(
            method = "read(Lnet/minecraft/network/PacketBuffer;IZ)V",
            at = @At(value = "NEW", target = "net/minecraft/world/chunk/storage/ExtendedBlockStorage"))
    private ExtendedBlockStorage cavebiomes$fixReadSectionYBase(int yArg, boolean storeSkylight) {
        return new ExtendedBlockStorage(yArg + WorldHeightAPI.getMinY(), storeSkylight);
    }
}
