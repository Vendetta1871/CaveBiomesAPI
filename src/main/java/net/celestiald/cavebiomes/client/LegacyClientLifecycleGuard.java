package net.celestiald.cavebiomes.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Null-safe hand access for legacy input handlers that can run during world teardown. */
public final class LegacyClientLifecycleGuard {
    private LegacyClientLifecycleGuard() {
    }

    public static ItemStack mainHand(EntityPlayer player) {
        return player == null ? ItemStack.EMPTY : player.getHeldItemMainhand();
    }

    public static ItemStack offHand(EntityPlayer player) {
        return player == null ? ItemStack.EMPTY : player.getHeldItemOffhand();
    }
}
