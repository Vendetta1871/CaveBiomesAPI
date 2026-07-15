package net.celestiald.cavebiomes.client;

import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Rejects metadata entries whose serializer does not match the receiving entity field. */
public final class ClientEntityMetadataGuard {
    private static final Logger LOGGER = LogManager.getLogger("CaveBiomes/EntityMetadata");
    private static final Set<String> REPORTED_MISMATCHES = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());

    private ClientEntityMetadataGuard() {
    }

    public static List<EntityDataManager.DataEntry<?>> filter(Entity entity,
            Map<Integer, EntityDataManager.DataEntry<?>> local,
            List<EntityDataManager.DataEntry<?>> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return incoming;
        }

        List<EntityDataManager.DataEntry<?>> accepted = incoming;

        for (int index = 0; index < incoming.size(); index++) {
            EntityDataManager.DataEntry<?> source = incoming.get(index);
            EntityDataManager.DataEntry<?> target = local.get(source.getKey().getId());
            if (target == null || serializersMatch(target, source)) {
                if (accepted != incoming) {
                    accepted.add(source);
                }
                continue;
            }

            if (accepted == incoming) {
                accepted = new ArrayList<EntityDataManager.DataEntry<?>>(incoming.size() - 1);
                accepted.addAll(incoming.subList(0, index));
            }
            reportMismatch(entity, target, source);
        }
        return accepted;
    }

    private static boolean serializersMatch(EntityDataManager.DataEntry<?> target,
            EntityDataManager.DataEntry<?> source) {
        return DataSerializers.getSerializerId(target.getKey().getSerializer())
                == DataSerializers.getSerializerId(source.getKey().getSerializer());
    }

    private static void reportMismatch(Entity entity,
            EntityDataManager.DataEntry<?> target,
            EntityDataManager.DataEntry<?> source) {
        int dataId = source.getKey().getId();
        int expected = DataSerializers.getSerializerId(target.getKey().getSerializer());
        int received = DataSerializers.getSerializerId(source.getKey().getSerializer());
        String entityClass = entity == null ? "unknown" : entity.getClass().getName();
        String signature = entityClass + ':' + dataId + ':' + expected + ':' + received;
        if (REPORTED_MISMATCHES.add(signature)) {
            LOGGER.error("Discarded incompatible synced entity data for {} (entity {}, data id {}): "
                            + "expected serializer {}, received {}. This indicates a client/server "
                            + "data-parameter mismatch or stale entity packet.",
                    entityClass, entity == null ? -1 : entity.getEntityId(), dataId,
                    expected, received);
        }
    }
}
