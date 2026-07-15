package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Moves the minecart's independent Overworld void check with the configured minimum Y. */
@Mixin(EntityMinecart.class)
public abstract class MixinEntityMinecart {

    @Unique
    private static double cavebiomes$thresholdForWorld(
            double vanillaThreshold, net.minecraft.world.World world) {
        return WorldHeightAPI.usesExtendedHeight(world)
                ? WorldHeightAPI.getMinY() - 64.0D : vanillaThreshold;
    }

    @ModifyConstant(
            method = "onUpdate",
            constant = @Constant(doubleValue = -64.0D),
            require = 1,
            allow = 1)
    private double cavebiomes$voidThreshold(double vanillaThreshold) {
        Entity entity = (Entity) (Object) this;
        return cavebiomes$thresholdForWorld(vanillaThreshold, entity.world);
    }
}
