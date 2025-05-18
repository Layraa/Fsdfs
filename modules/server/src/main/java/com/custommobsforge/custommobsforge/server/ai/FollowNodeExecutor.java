package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FollowNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private static final TargetingConditions FOLLOW_TARGETING = TargetingConditions.forNonCombat().range(16.0D);

    // Кэш целей и путей для каждого моба
    private static final Map<Integer, Long> lastMoveTime = new HashMap<>();
    private static final Map<Integer, Long> movementDuration = new HashMap<>();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры узла
        double distance = node.getCustomParameterAsDouble("distance", 5.0);
        double speed = node.getCustomParameterAsDouble("speed", 1.0);
        boolean targetPlayer = node.getCustomParameterAsBoolean("targetPlayer", true);

        executor.logNodeExecution("FollowNode", node.getId(),
                "distance=" + distance + ", speed=" + speed + ", targetPlayer=" + targetPlayer, true);

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            executor.logNodeExecution("FollowNode", node.getId(), "skipped on client", false);
            return false; // На клиенте ничего не делаем
        }

        // Ищем цель для следования
        LivingEntity target = findTarget(entity, targetPlayer);

        // Если нет цели, возвращаем неудачу
        if (target == null) {
            executor.logNodeExecution("FollowNode", node.getId(), "no target found", false);
            return false;
        }

        // Получаем уникальный ID для моба
        int entityId = entity.getId();

        // Вычисляем расстояние до цели
        double distanceToTarget = entity.distanceTo(target);
        LOGGER.info("FollowNode: Entity {} is at distance {} from target, desired distance is {}",
                entityId, distanceToTarget, distance);

        // Проверяем, находится ли моб в движении
        boolean isMoving = false;
        long currentTime = System.currentTimeMillis();

        if (lastMoveTime.containsKey(entityId)) {
            long lastMove = lastMoveTime.get(entityId);
            long duration = movementDuration.getOrDefault(entityId, 1000L);

            // Если с момента начала движения прошло меньше времени чем duration, считаем что моб движется
            if (currentTime - lastMove < duration) {
                isMoving = true;

                // Воспроизводим анимацию ходьбы, если ещё движемся
                executor.playAnimation("WALK");

                LOGGER.info("FollowNode: Entity {} is still moving, animation WALK", entityId);

                // Продолжаем проверку по времени
                executor.setNodeNeedsMoreTime(true);
                executor.logNodeExecution("FollowNode", node.getId(),
                        "still moving, remaining time: " + (duration - (currentTime - lastMove)) + "ms", false);
                return true;
            }
        }

        // Если уже находимся на нужном расстоянии, возвращаем успех
        if (Math.abs(distanceToTarget - distance) < 1.0) {
            // Воспроизводим анимацию ожидания
            executor.playAnimation("IDLE");
            executor.logNodeExecution("FollowNode", node.getId(),
                    "at desired distance " + distanceToTarget, false);
            return true;
        }

        // Получаем навигацию
        PathNavigation navigation = entity.getNavigation();
        if (navigation == null) {
            LOGGER.error("FollowNode: Entity {} has no navigation!", entityId);
            executor.logNodeExecution("FollowNode", node.getId(), "no navigation available", false);
            return false;
        }

        // Вычисляем направление к цели
        Vec3 targetPos = target.position();
        Vec3 entityPos = entity.position();
        Vec3 direction;

        // Направление движения зависит от текущего расстояния
        if (distanceToTarget < distance) {
            // Слишком близко, нужно отойти
            direction = entityPos.subtract(targetPos).normalize();
            LOGGER.info("FollowNode: Entity {} is too close, moving away", entityId);
        } else {
            // Слишком далеко, нужно подойти
            direction = targetPos.subtract(entityPos).normalize();
            LOGGER.info("FollowNode: Entity {} is too far, moving closer", entityId);
        }

        // Вычисляем целевую точку - расстояние умножаем на вектор направления
        double moveDistance = Math.abs(distanceToTarget - distance);
        if (moveDistance > 10) moveDistance = 10; // Ограничиваем дистанцию для лучшего контроля

        // Рассчитываем целевую позицию
        double moveToX = entityPos.x + direction.x * moveDistance;
        double moveToZ = entityPos.z + direction.z * moveDistance;

        // Используем навигацию для движения
        boolean pathSuccess = navigation.moveTo(moveToX, entityPos.y, moveToZ, speed);

        if (pathSuccess) {
            LOGGER.info("FollowNode: Entity {} is moving to [{}, {}, {}] with speed {}",
                    entityId, moveToX, entityPos.y, moveToZ, speed);

            // Воспроизводим анимацию ходьбы
            executor.playAnimation("WALK");

            // Включаем "смотреть на цель" даже при движении
            entity.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // Запоминаем начало движения и его продолжительность (зависит от скорости и расстояния)
            lastMoveTime.put(entityId, currentTime);

            // Рассчитываем приблизительную продолжительность движения на основе расстояния и скорости
            long estimatedDuration = (long)(moveDistance / speed * 1000); // в миллисекундах

            // Ограничиваем минимальную и максимальную продолжительность
            if (estimatedDuration < 500) estimatedDuration = 500;
            if (estimatedDuration > 5000) estimatedDuration = 5000;

            movementDuration.put(entityId, estimatedDuration);

            // Сообщаем, что нужно продолжить выполнение узла в следующем тике
            executor.setNodeNeedsMoreTime(true);

            executor.logNodeExecution("FollowNode", node.getId(),
                    "started moving, estimated duration: " + estimatedDuration + "ms", false);
            return true;
        } else {
            // Если не удалось построить путь, пробуем просто задать направление
            LOGGER.warn("FollowNode: Entity {} failed to create path, trying direct movement", entityId);

            // Используем прямое перемещение через MoveControl
            entity.getMoveControl().setWantedPosition(moveToX, entityPos.y, moveToZ, speed);

            // Воспроизводим анимацию ходьбы в любом случае
            executor.playAnimation("WALK");

            // Смотрим на цель
            entity.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // Запоминаем начало движения
            lastMoveTime.put(entityId, currentTime);

            // Устанавливаем фиксированную продолжительность для прямого перемещения
            movementDuration.put(entityId, 1000L); // 1 секунда

            // Сообщаем, что нужно продолжить выполнение узла в следующем тике
            executor.setNodeNeedsMoreTime(true);

            executor.logNodeExecution("FollowNode", node.getId(),
                    "started direct movement, duration: 1000ms", false);
            return true;
        }
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