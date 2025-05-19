package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.event.system.EventSystem;
import com.custommobsforge.custommobsforge.server.ai.FollowNodeExecutor;
import com.custommobsforge.custommobsforge.server.ai.OnDamageNodeExecutor;
import com.custommobsforge.custommobsforge.server.ai.PlayAnimationNodeExecutor;
import com.custommobsforge.custommobsforge.server.ai.TimerNodeExecutor;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber
public class EntityCleanupService {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    // Карта для отслеживания зарегистрированных ресурсов
    private static final Map<Integer, Boolean> registeredEntities = new HashMap<>();

    /**
     * Регистрирует сущность для отслеживания и очистки ресурсов
     * @param entity сущность для регистрации
     */
    public static void registerEntity(CustomMobEntity entity) {
        if (!registeredEntities.containsKey(entity.getId())) {
            registeredEntities.put(entity.getId(), true);
            LOGGER.info("EntityCleanupService: Registered entity {} for cleanup", entity.getId());
        }
    }

    /**
     * Очищает ресурсы для указанной сущности
     * @param entity сущность
     */
    public static void cleanup(CustomMobEntity entity) {
        cleanup(entity.getId());
    }

    /**
     * Очищает ресурсы для указанного ID сущности
     * @param entityId ID сущности
     */
    public static void cleanup(int entityId) {
        LOGGER.info("EntityCleanupService: Cleaning up resources for entity {}", entityId);

        // Очищаем ресурсы для узла OnDamageNode
        OnDamageNodeExecutor.cleanup(entityId);

        // Очищаем ресурсы для узла PlayAnimationNode
        PlayAnimationNodeExecutor.cleanup(entityId);

        // Очищаем ресурсы для узла FollowNode
        FollowNodeExecutor.cleanup(entityId);

        // Очищаем ресурсы для узла TimerNode
        TimerNodeExecutor.cleanup(entityId);

        // Удаляем из списка зарегистрированных сущностей
        registeredEntities.remove(entityId);

        LOGGER.info("EntityCleanupService: Resources cleaned up for entity {}", entityId);
    }

    /**
     * Обработчик события удаления сущности из мира
     */
    @SubscribeEvent
    public static void onEntityLeaveWorld(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof CustomMobEntity) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();
            int entityId = entity.getId();

            LOGGER.info("EntityCleanupService: Entity {} leaving world, cleaning up resources", entityId);

            // Очищаем ресурсы
            cleanup(entityId);
        }
    }

    /**
     * Выполняет периодическую очистку неиспользуемых ресурсов
     */
    public static void periodicCleanup() {
        // Можно добавить дополнительную логику очистки,
        // например, удаление устаревших слушателей
    }
}