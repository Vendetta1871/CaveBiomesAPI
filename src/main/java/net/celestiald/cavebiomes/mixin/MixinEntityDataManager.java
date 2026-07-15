package net.celestiald.cavebiomes.mixin;

import net.celestiald.cavebiomes.client.ClientEntityMetadataGuard;
import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Map;

/** Validates every client metadata application, including Forge mod-entity spawns. */
@Mixin(EntityDataManager.class)
public abstract class MixinEntityDataManager {
    @Shadow @Final private Entity entity;
    @Shadow @Final private Map<Integer, EntityDataManager.DataEntry<?>> entries;

    @ModifyVariable(method = "setEntryValues(Ljava/util/List;)V", at = @At("HEAD"),
            argsOnly = true, require = 1)
    private List<EntityDataManager.DataEntry<?>> cavebiomes$filterMetadata(
            List<EntityDataManager.DataEntry<?>> incoming) {
        return ClientEntityMetadataGuard.filter(entity, entries, incoming);
    }
}
