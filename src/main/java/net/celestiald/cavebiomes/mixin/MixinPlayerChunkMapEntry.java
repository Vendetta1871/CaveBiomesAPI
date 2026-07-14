package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.celestiald.cavebiomes.api.ExtendedChunkAPI;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import javax.annotation.Nullable;

/**
 * Fixes the chunk-update path for extended height:
 *  - full-chunk sends use the real section mask (not 16-bit 0xFFFF),
 *  - the per-tick block-change batch tracks the FULL Y (vanilla packs Y into the
 *    low 8 bits of a short, capping it at 0..255 and producing phantom blocks at
 *    `y & 255` for any change outside that range),
 *  - the section bit uses the storage index si(y), not y>>4,
 *  - multi-block batches resend whole sections, because SPacketMultiBlockChange's
 *    wire format also packs Y in 8 bits and cannot carry Y outside 0..255.
 */
@Mixin(PlayerChunkMapEntry.class)
public abstract class MixinPlayerChunkMapEntry {

    @Shadow @Final private PlayerChunkMap playerChunkMap;
    @Shadow @Final private ChunkPos pos;
    @Shadow private Chunk chunk;
    @Shadow private int changes;
    @Shadow private int changedSectionFilter;
    @Shadow private boolean sentToPlayers;

    @Shadow public abstract void sendPacket(Packet<?> packetIn);
    @Shadow private void sendBlockEntity(@Nullable TileEntity be) {}

    // Full Y-range replacement for the vanilla short[] changedBlocks: pack
    // x:4 (28-31) | z:4 (24-27) | (y - minY):24 (0-23).
    @Unique private final int[] cavebiomes$changed = new int[64];

    @ModifyConstant(method = "sendToPlayers", constant = @Constant(intValue = 65535))
    private int cavebiomes$fullMaskA(int original) {
        return ExtendedChunkAPI.fullSectionMask();
    }

    @ModifyConstant(method = "sendToPlayer", constant = @Constant(intValue = 65535))
    private int cavebiomes$fullMaskB(int original) {
        return ExtendedChunkAPI.fullSectionMask();
    }

    @Overwrite
    public void blockChanged(int x, int y, int z) {
        if (this.sentToPlayers) {
            if (this.changes == 0) {
                this.playerChunkMap.entryChanged((PlayerChunkMapEntry) (Object) this);
            }

            this.changedSectionFilter |= 1 << WorldHeightAPI.sectionIndex(y);

            if (this.changes < 64) {
                int packed = (x & 15) << 28 | (z & 15) << 24 | ((y - WorldHeightAPI.getMinY()) & 0xFFFFFF);
                for (int i = 0; i < this.changes; ++i) {
                    if (this.cavebiomes$changed[i] == packed) {
                        return;
                    }
                }
                this.cavebiomes$changed[this.changes++] = packed;
            }
        }
    }

    @Overwrite
    public void update() {
        if (this.sentToPlayers && this.chunk != null) {
            if (this.changes != 0) {
                WorldServer world = this.playerChunkMap.getWorldServer();

                if (this.changes == 1) {
                    int packed = this.cavebiomes$changed[0];
                    int wx = ((packed >>> 28) & 15) + this.pos.x * 16;
                    int wy = (packed & 0xFFFFFF) + WorldHeightAPI.getMinY();
                    int wz = ((packed >>> 24) & 15) + this.pos.z * 16;
                    BlockPos blockpos = new BlockPos(wx, wy, wz);

                    this.sendPacket(new SPacketBlockChange(world, blockpos));
                    if (world.getBlockState(blockpos).getBlock().hasTileEntity()) {
                        this.sendBlockEntity(world.getTileEntity(blockpos));
                    }
                } else if (this.changes < this.cavebiomes$changed.length
                        && this.cavebiomes$allChangesFitVanillaHeight()) {
                    // Keep vanilla's compact batch for surface updates. This matters in active
                    // modded bases where resending whole sections every tick floods the client.
                    short[] changedBlocks = new short[this.changes];
                    for (int i = 0; i < this.changes; ++i) {
                        int packed = this.cavebiomes$changed[i];
                        int x = (packed >>> 28) & 15;
                        int y = (packed & 0xFFFFFF) + WorldHeightAPI.getMinY();
                        int z = (packed >>> 24) & 15;
                        changedBlocks[i] = (short) (x << 12 | z << 8 | y);
                    }
                    this.sendPacket(new SPacketMultiBlockChange(this.changes, changedBlocks, this.chunk));
                    for (int i = 0; i < this.changes; ++i) {
                        int packed = this.cavebiomes$changed[i];
                        int x = ((packed >>> 28) & 15) + this.pos.x * 16;
                        int y = (packed & 0xFFFFFF) + WorldHeightAPI.getMinY();
                        int z = ((packed >>> 24) & 15) + this.pos.z * 16;
                        BlockPos blockpos = new BlockPos(x, y, z);
                        IBlockState state = world.getBlockState(blockpos);
                        if (state.getBlock().hasTileEntity(state)) {
                            this.sendBlockEntity(world.getTileEntity(blockpos));
                        }
                    }
                } else {
                    // Extended-height updates and saturated batches cannot use the vanilla
                    // packet's eight-bit Y field, so resend only their affected sections.
                    this.sendPacket(new SPacketChunkData(this.chunk, this.changedSectionFilter));
                }

                this.changes = 0;
                this.changedSectionFilter = 0;
            }
        }
    }

    @Unique
    private boolean cavebiomes$allChangesFitVanillaHeight() {
        for (int i = 0; i < this.changes; ++i) {
            int y = (this.cavebiomes$changed[i] & 0xFFFFFF) + WorldHeightAPI.getMinY();
            if (y < 0 || y > 255) {
                return false;
            }
        }
        return true;
    }
}
