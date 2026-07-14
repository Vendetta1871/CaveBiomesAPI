package net.celestiald.cavebiomes.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;

/** Drops block updates that were already queued when the client world was closed. */
public final class ClientBlockChangeGuard {
    private ClientBlockChangeGuard() {}

    public static boolean apply(WorldClient world, BlockPos pos, IBlockState state) {
        return world != null && world.invalidateRegionAndSetBlock(pos, state);
    }
}
