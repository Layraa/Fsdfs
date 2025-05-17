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

            // Если у моба уже есть данные, пропускаем
            if (entity.getMobData() != null) {
                return;
            }

            // Получаем ID моба
            String mobId = entity.getMobId();
            if (mobId == null || mobId.isEmpty()) {
                return;
            }

            // Загружаем данные моба
            MobData mobData = MobConfigManager.loadMobConfig(mobId, level);
            if (mobData == null) {
                return;
            }

            // Устанавливаем данные
            entity.setMobData(mobData);

            // Если у моба есть дерево поведения, добавляем соответствующую цель
            if (mobData.getBehaviorTree() != null) {
                entity.goalSelector.addGoal(1, new BehaviorTreeExecutor(entity, mobData.getBehaviorTree()));
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