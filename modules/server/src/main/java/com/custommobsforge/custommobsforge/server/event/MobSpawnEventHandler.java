package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.ai.BehaviorTreeExecutor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class MobSpawnEventHandler {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        // Инициализируем менеджер конфигураций при первом событии спавна
        if (event.getLevel() instanceof ServerLevel && !event.getLevel().isClientSide) {
            // Инициализируем менеджер конфигураций
            if (event.getEntity() instanceof LivingEntity) {
                MobConfigManager.init((ServerLevel) event.getLevel());
            }
        }

        // Настраиваем кастомного моба при появлении в мире
        if (event.getEntity() instanceof CustomMobEntity && event.getLevel() instanceof ServerLevel) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();
            ServerLevel level = (ServerLevel) event.getLevel();

            // Расширенное логирование
            System.out.println("MobSpawnEventHandler: CustomMobEntity joined world - ID: " + entity.getId() +
                    ", mobId: " + entity.getMobId() +
                    ", hasData: " + (entity.getMobData() != null));

            // Если у моба уже есть данные, пропускаем
            if (entity.getMobData() != null) {
                System.out.println("MobSpawnEventHandler: Entity already has mob data, skipping configuration");
                return;
            }

            // Получаем ID моба
            String mobId = entity.getMobId();
            if (mobId == null || mobId.isEmpty()) {
                System.out.println("MobSpawnEventHandler: Entity has no mob ID, skipping configuration");
                return;
            }

            // Загружаем данные моба
            MobData mobData = MobConfigManager.loadMobConfig(mobId, level);
            if (mobData == null) {
                System.out.println("MobSpawnEventHandler: Could not load mob data for ID: " + mobId);
                return;
            }

            // Устанавливаем данные
            System.out.println("MobSpawnEventHandler: Setting mob data for entity " + entity.getId() +
                    " with model: " + mobData.getModelPath() +
                    ", texture: " + mobData.getTexturePath());
            entity.setMobData(mobData);

            // Если у моба есть дерево поведения, добавляем соответствующую цель
            if (mobData.getBehaviorTree() != null) {
                entity.goalSelector.addGoal(1, new BehaviorTreeExecutor(entity, mobData.getBehaviorTree()));
                System.out.println("MobSpawnEventHandler: Added behavior tree executor for entity " + entity.getId());
            }

            // Воспроизводим анимацию появления
            entity.playAnimation("SPAWN");
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Обрабатываем события получения урона для кастомных мобов
        if (event.getEntity() instanceof CustomMobEntity) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();

            // Воспроизводим анимацию получения урона
            entity.playAnimation("HURT");
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Обрабатываем события смерти для кастомных мобов
        if (event.getEntity() instanceof CustomMobEntity) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();

            // Воспроизводим анимацию смерти
            entity.playAnimation("DEATH");
        }
    }
}