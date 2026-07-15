package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keeps WorldServer's empty-precipitation sentinel aligned with the scan floor. */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer {

    @Unique
    private static int cavebiomes$precipitationSentinelForWorld(
            int vanillaSentinel, WorldServer world) {
        return WorldHeightAPI.usesExtendedHeight(world) && WorldHeightAPI.getMinY() < 0
                ? WorldHeightAPI.getMinY() - 1
                : vanillaSentinel;
    }

    @ModifyConstant(
            method = "adjustPosToNearbyEntity",
            constant = @Constant(intValue = -1),
            require = 1,
            allow = 1)
    private int cavebiomes$emptyPrecipitationHeight(int vanillaSentinel) {
        WorldServer world = (WorldServer) (Object) this;
        return cavebiomes$precipitationSentinelForWorld(vanillaSentinel, world);
    }
}
