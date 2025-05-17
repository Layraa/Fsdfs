package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FollowNodeExecutor implements NodeExecutor {
    private static final TargetingConditions FOLLOW_TARGETING = TargetingConditions.forNonCombat().range(16.0D);

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры узла
        double distance = node.getCustomParameterAsDouble("distance", 5.0);
        double speed = node.getCustomParameterAsDouble("speed", 1.0);
        boolean targetPlayer = node.getCustomParameterAsBoolean("targetPlayer", true);

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            return false; // На клиенте ничего не делаем
        }

        // Ищем цель для следования
        LivingEntity target = findTarget(entity, targetPlayer);

        // Если нет цели, возвращаем неудачу
        if (target == null) {
            return false;
        }

        // Вычисляем расстояние до цели
        double distanceToTarget = entity.distanceTo(target);

        // Если уже находимся на нужном расстоянии, возвращаем успех
        if (Math.abs(distanceToTarget - distance) < 1.0) {
            // Воспроизводим анимацию ходьбы
            executor.playAnimation("IDLE");
            return true;
        }

        // Вычисляем направление к цели
        Vec3 targetPos = target.position();
        Vec3 entityPos = entity.position();
        Vec3 direction = targetPos.subtract(entityPos).normalize();

        // Если нужно отойти от цели (слишком близко)
        if (distanceToTarget < distance) {
            direction = direction.scale(-1);
        }

        // Перемещаем моба в нужном направлении
        entity.getMoveControl().setWantedPosition(
                entityPos.x + direction.x * speed,
                entityPos.y,
                entityPos.z + direction.z * speed,
                speed
        );

        // Воспроизводим анимацию ходьбы
        executor.playAnimation("WALK");

        return true;
    }

    // Метод поиска цели
    private LivingEntity findTarget(CustomMobEntity entity, boolean targetPlayer) {
        if (targetPlayer) {
            // Ищем ближайшего игрока
            Player nearestPlayer = entity.level().getNearestPlayer(
                    FOLLOW_TARGETING,
                    entity
            );

            return nearestPlayer;
        } else {
            // Ищем любую живую сущность (можно настроить под конкретные типы)
            List<LivingEntity> nearbyEntities = entity.level().getEntitiesOfClass(
                    LivingEntity.class,
                    entity.getBoundingBox().inflate(16.0),
                    target -> FOLLOW_TARGETING.test(entity, target) && !(target instanceof CustomMobEntity)
            );

            if (!nearbyEntities.isEmpty()) {
                return nearbyEntities.get(0);
            }
        }

        return null;
    }
}