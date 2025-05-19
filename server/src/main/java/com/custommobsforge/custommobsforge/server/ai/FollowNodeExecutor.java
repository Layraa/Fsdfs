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

import java.util.List;

public class FollowNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private static final TargetingConditions FOLLOW_TARGETING = TargetingConditions.forNonCombat().range(32.0D);

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры узла
        double targetDistance = node.getCustomParameterAsDouble("distance", 5.0);
        double speed = node.getCustomParameterAsDouble("speed", 1.0);
        boolean targetPlayerOnly = node.getCustomParameterAsBoolean("targetPlayer", true);

        executor.logNodeExecution("FollowNode", node.getId(),
                "distance=" + targetDistance + ", speed=" + speed + ", targetPlayer=" + targetPlayerOnly, true);

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            executor.logNodeExecution("FollowNode", node.getId(), "skipped on client", false);
            return false; // На клиенте ничего не делаем
        }

        // Уникальный идентификатор для узла+сущности
        String nodeKey = entity.getId() + ":" + node.getId();

        // Отключаем автоматические анимации
        entity.setDisableAutoAnimations(true);

        // Получаем текущую цель из Blackboard или находим новую
        LivingEntity target = (LivingEntity) executor.getBlackboard().getValue(nodeKey + ":target");

        if (target == null || target.isRemoved() || entity.distanceTo(target) > 32.0) {
            target = findTarget(entity, targetPlayerOnly);

            if (target == null) {
                // Нет цели, возвращаем неудачу и включаем автоанимации обратно
                executor.logNodeExecution("FollowNode", node.getId(), "no target found", false);
                entity.setDisableAutoAnimations(false);
                return false;
            }

            // Сохраняем цель в Blackboard
            executor.getBlackboard().setValue(nodeKey + ":target", target);
        }

        // Текущее время для расчётов
        long currentTime = System.currentTimeMillis();
        long lastPathUpdate = executor.getBlackboard().getValue(nodeKey + ":lastPathUpdate", 0L);

        // Обновляем путь с определенным интервалом
        if (currentTime - lastPathUpdate > 500) {
            boolean pathUpdated = updatePath(entity, target, targetDistance, speed);
            executor.getBlackboard().setValue(nodeKey + ":lastPathUpdate", currentTime);

            if (!pathUpdated) {
                // Не удалось обновить путь, возвращаем неудачу
                executor.logNodeExecution("FollowNode", node.getId(), "failed to update path", false);
                entity.setDisableAutoAnimations(false);
                return false;
            }
        }

        // Обновляем анимацию на основе фактического движения
        updateAnimation(entity);

        // Проверяем, находимся ли мы на нужном расстоянии
        double distanceToTarget = entity.distanceTo(target);
        boolean atDesiredDistance = Math.abs(distanceToTarget - targetDistance) < 1.0;

        if (atDesiredDistance) {
            // Если мы на нужном расстоянии, смотрим на цель и возвращаем успех
            entity.getLookControl().setLookAt(target, 30.0f, 30.0f);

            // Воспроизводим IDLE анимацию
            if (!entity.getAnimationAdapter().isPlaying("IDLE")) {
                entity.getAnimationAdapter().playAnimation("IDLE", true, 1.0f);
            }

            executor.logNodeExecution("FollowNode", node.getId(), "reached desired distance", false);
            entity.setDisableAutoAnimations(false);
            return true;
        }

        // Продолжаем следовать, нужно больше времени
        executor.setNodeNeedsMoreTime(true);
        return true;
    }

    /**
     * Обновляет путь к цели
     * @param entity Сущность
     * @param target Цель
     * @param targetDistance Желаемое расстояние до цели
     * @param speed Скорость движения
     * @return true, если удалось обновить путь
     */
    private boolean updatePath(CustomMobEntity entity, LivingEntity target, double targetDistance, double speed) {
        double distanceToTarget = entity.distanceTo(target);
        PathNavigation navigator = entity.getNavigation();

        // Очищаем текущий путь
        navigator.stop();

        // Если мы на нужном расстоянии, просто смотрим на цель
        if (Math.abs(distanceToTarget - targetDistance) < 1.0) {
            entity.getLookControl().setLookAt(target, 30.0f, 30.0f);
            return true;
        }

        // Рассчитываем целевую позицию
        Vec3 targetPos = target.position();
        Vec3 entityPos = entity.position();
        Vec3 direction;

        if (distanceToTarget < targetDistance) {
            // Отходим
            direction = entityPos.subtract(targetPos).normalize();
        } else {
            // Подходим
            direction = targetPos.subtract(entityPos).normalize();
        }

        double distanceToMove = Math.abs(distanceToTarget - targetDistance);
        if (distanceToMove > 10) distanceToMove = 10;

        double targetX = entityPos.x + direction.x * distanceToMove;
        double targetZ = entityPos.z + direction.z * distanceToMove;

        // Устанавливаем путь
        boolean success = navigator.moveTo(targetX, entityPos.y, targetZ, speed);

        if (!success) {
            // Если не удалось установить путь, пробуем прямое перемещение
            entity.getMoveControl().setWantedPosition(targetX, entityPos.y, targetZ, speed);
            return true;
        }

        return true;
    }

    /**
     * Обновляет анимацию моба в зависимости от движения
     * @param entity Сущность
     */
    private void updateAnimation(CustomMobEntity entity) {
        // Проверяем, движется ли моб фактически
        Vec3 velocity = entity.getDeltaMovement();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        if (speed > 0.01) {
            // Моб движется, играем анимацию ходьбы
            if (!entity.getAnimationAdapter().isPlaying("WALK")) {
                entity.getAnimationAdapter().playAnimation("WALK", true, 1.0f);
            }
        } else {
            // Моб стоит, играем анимацию ожидания
            if (!entity.getAnimationAdapter().isPlaying("IDLE")) {
                entity.getAnimationAdapter().playAnimation("IDLE", true, 1.0f);
            }
        }
    }

    /**
     * Находит цель для следования
     * @param entity Сущность
     * @param targetPlayerOnly Следовать только за игроком
     * @return Цель для следования или null, если цель не найдена
     */
    private LivingEntity findTarget(CustomMobEntity entity, boolean targetPlayerOnly) {
        if (targetPlayerOnly) {
            // Ищем ближайшего игрока
            Player nearestPlayer = entity.level().getNearestPlayer(
                    FOLLOW_TARGETING,
                    entity
            );

            return nearestPlayer;
        } else {
            // Ищем любую подходящую сущность
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