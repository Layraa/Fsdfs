package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FleeNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private static final TargetingConditions FLEE_TARGETING = TargetingConditions.forNonCombat().range(16.0D);

    // Кэш для отслеживания движения
    private static final Map<Integer, Long> lastMoveTime = new HashMap<>();
    private static final Map<Integer, Long> movementDuration = new HashMap<>();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры узла
        double distance = node.getCustomParameterAsDouble("distance", 10.0);
        double speed = node.getCustomParameterAsDouble("speed", 1.2);
        boolean targetPlayer = node.getCustomParameterAsBoolean("targetPlayer", true);

        executor.logNodeExecution("FleeNode", node.getId(),
                "distance=" + distance + ", speed=" + speed + ", targetPlayer=" + targetPlayer, true);

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            executor.logNodeExecution("FleeNode", node.getId(), "skipped on client", false);
            return false; // На клиенте ничего не делаем
        }

        // Ищем цель для бегства
        LivingEntity threat = findThreat(entity, targetPlayer);

        // Если нет угрозы, возвращаем неудачу
        if (threat == null) {
            executor.logNodeExecution("FleeNode", node.getId(), "no threat found", false);
            return false;
        }

        // Получаем уникальный ID для моба
        int entityId = entity.getId();

        // Вычисляем расстояние до угрозы
        double distanceToThreat = entity.distanceTo(threat);
        LOGGER.info("FleeNode: Entity {} is at distance {} from threat, safe distance is {}",
                entityId, distanceToThreat, distance);

        // Проверяем, находится ли моб в движении
        boolean isMoving = false;
        long currentTime = System.currentTimeMillis();

        if (lastMoveTime.containsKey(entityId)) {
            long lastMove = lastMoveTime.get(entityId);
            long duration = movementDuration.getOrDefault(entityId, 1000L);

            // Если с момента начала движения прошло меньше времени чем duration, считаем что моб движется
            if (currentTime - lastMove < duration) {
                isMoving = true;

                // Воспроизводим анимацию бега, если ещё движемся
                if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
                    entity.setAnimation(node.getAnimationId(), node.isLoopAnimation(), (float) node.getAnimationSpeed());
                } else {
                    executor.playAnimation("RUN");
                }

                LOGGER.info("FleeNode: Entity {} is still moving, animation RUN", entityId);

                // Продолжаем проверку по времени
                executor.setNodeNeedsMoreTime(true);
                executor.logNodeExecution("FleeNode", node.getId(),
                        "still fleeing, remaining time: " + (duration - (currentTime - lastMove)) + "ms", false);
                return true;
            }
        }

        // Если угроза слишком далеко, нет необходимости убегать
        if (distanceToThreat > distance) {
            // Воспроизводим анимацию ожидания
            executor.playAnimation("IDLE");
            executor.logNodeExecution("FleeNode", node.getId(),
                    "already at safe distance: " + distanceToThreat, false);
            return true;
        }

        // Получаем навигацию
        PathNavigation navigation = entity.getNavigation();
        if (navigation == null) {
            LOGGER.error("FleeNode: Entity {} has no navigation!", entityId);
            executor.logNodeExecution("FleeNode", node.getId(), "no navigation available", false);
            return false;
        }

        // Вычисляем направление от угрозы (противоположное направлению к угрозе)
        Vec3 threatPos = threat.position();
        Vec3 entityPos = entity.position();
        Vec3 direction = entityPos.subtract(threatPos).normalize();

        // Рассчитываем целевую позицию для бегства
        double moveDistance = distance - distanceToThreat;
        if (moveDistance < 5) moveDistance = 5; // Минимальное расстояние для бегства
        if (moveDistance > 15) moveDistance = 15; // Максимальное расстояние для бегства

        double moveToX = entityPos.x + direction.x * moveDistance;
        double moveToZ = entityPos.z + direction.z * moveDistance;

        // ВАЖНОЕ ИЗМЕНЕНИЕ: При бегстве используем умеренную скорость для более реалистичного движения
        double adjustedSpeed = speed;
        if (adjustedSpeed > 1.5) adjustedSpeed = 1.5; // Ограничиваем максимальную скорость

        // Используем навигацию для движения
        boolean pathSuccess = navigation.moveTo(moveToX, entityPos.y, moveToZ, adjustedSpeed);

        if (pathSuccess) {
            LOGGER.info("FleeNode: Entity {} is fleeing to [{}, {}, {}] with speed {}",
                    entityId, moveToX, entityPos.y, moveToZ, adjustedSpeed);

            // Воспроизводим анимацию бега
            if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
                entity.setAnimation(node.getAnimationId(), node.isLoopAnimation(), (float) node.getAnimationSpeed());
            } else {
                executor.playAnimation("RUN");
            }

            // Повернуться лицом в направлении бегства (противоположно угрозе)
            double lookX = entityPos.x + direction.x * 10;
            double lookZ = entityPos.z + direction.z * 10;
            entity.getLookControl().setLookAt(lookX, entityPos.y, lookZ, 30.0F, 30.0F);

            // Запоминаем начало движения и его продолжительность
            lastMoveTime.put(entityId, currentTime);

            // Рассчитываем приблизительную продолжительность движения на основе расстояния и скорости
            long estimatedDuration = (long)(moveDistance / adjustedSpeed * 1000); // в миллисекундах

            // Ограничиваем минимальную и максимальную продолжительность
            if (estimatedDuration < 500) estimatedDuration = 500;
            if (estimatedDuration > 5000) estimatedDuration = 5000;

            movementDuration.put(entityId, estimatedDuration);

            // Сообщаем, что нужно продолжить выполнение узла в следующем тике
            executor.setNodeNeedsMoreTime(true);

            executor.logNodeExecution("FleeNode", node.getId(),
                    "started fleeing, estimated duration: " + estimatedDuration + "ms", false);
            return true;
        } else {
            // Если не удалось построить путь, пробуем просто задать направление
            LOGGER.warn("FleeNode: Entity {} failed to create path, trying direct movement", entityId);

            // Используем прямое перемещение
            entity.getMoveControl().setWantedPosition(moveToX, entityPos.y, moveToZ, adjustedSpeed);

            // Воспроизводим анимацию бега в любом случае
            if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
                entity.setAnimation(node.getAnimationId(), node.isLoopAnimation(), (float) node.getAnimationSpeed());
            } else {
                executor.playAnimation("RUN");
            }

            // Запоминаем начало движения
            lastMoveTime.put(entityId, currentTime);

            // Устанавливаем фиксированную продолжительность для прямого перемещения
            movementDuration.put(entityId, 1000L); // 1 секунда

            // Сообщаем, что нужно продолжить выполнение узла в следующем тике
            executor.setNodeNeedsMoreTime(true);

            executor.logNodeExecution("FleeNode", node.getId(),
                    "started direct fleeing, duration: 1000ms", false);
            return true;
        }
    }

    // Метод поиска угрозы
    private LivingEntity findThreat(CustomMobEntity entity, boolean targetPlayer) {
        if (targetPlayer) {
            // Ищем ближайшего игрока
            Player nearestPlayer = entity.level().getNearestPlayer(
                    FLEE_TARGETING,
                    entity
            );

            return nearestPlayer;
        } else {
            // Ищем любую подходящую сущность
            List<LivingEntity> nearbyEntities = entity.level().getEntitiesOfClass(
                    LivingEntity.class,
                    entity.getBoundingBox().inflate(16.0),
                    target -> FLEE_TARGETING.test(entity, target) && !(target instanceof CustomMobEntity)
            );

            if (!nearbyEntities.isEmpty()) {
                return nearbyEntities.get(0);
            }
        }

        return null;
    }
}