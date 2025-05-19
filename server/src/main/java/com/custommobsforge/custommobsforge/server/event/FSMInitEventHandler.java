package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.fsm.StateFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber
public class FSMInitEventHandler {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof CustomMobEntity && !event.getLevel().isClientSide) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();

            LOGGER.info("FSMInitEventHandler: Initializing FSM for entity {}", entity.getId());

            // Убедимся, что данные моба загружены
            if (entity.getMobData() == null) {
                // Получаем ID моба
                String mobId = entity.getMobId();
                if (mobId != null && !mobId.isEmpty()) {
                    // Загружаем данные моба
                    MobData mobData = MobConfigManager.loadMobConfig(
                            mobId, (ServerLevel) event.getLevel());

                    if (mobData != null) {
                        entity.setMobData(mobData);
                    }
                }
            }

            // Если у моба есть данные и дерево поведения, инициализируем состояния
            if (entity.getMobData() != null) {
                if (entity.getMobData().getBehaviorTree() != null) {
                    LOGGER.info("FSMInitEventHandler: Creating states from behavior tree for entity {}",
                            entity.getId());
                    StateFactory.createStatesFromBehaviorTree(
                            entity, entity.getMobData().getBehaviorTree());
                } else {
                    LOGGER.info("FSMInitEventHandler: Creating standard states for entity {}",
                            entity.getId());
                    StateFactory.createStandardStates(entity);
                }
            }
        }
    }
}