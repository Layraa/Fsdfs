package com.custommobsforge.custommobsforge.common.fsm.states;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.fsm.MobState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Состояние следования за целью
 */
public class FollowState extends MobState {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private static final TargetingConditions FOLLOW_TARGETING = TargetingConditions.forNonCombat().range(32.0D);

    private LivingEntity target;
    private double desiredDistance;
    private long lastPathUpdate;
    private static final long PATH_UPDATE_INTERVAL = 500; // мс

    public FollowState() {
        super("follow", "Follow State");
    }

    @Override
    public void enter(CustomMobEntity entity) {
        super.enter(entity);
        LOGGER.info("FollowState: Entity {} entered follow state", entity.getId());
        desiredDistance = entity.getMobData().getAttribute("followDistance", 5.0f);
        lastPathUpdate = 0;
        target = null;
    }

    @Override
    public void update(CustomMobEntity entity) {
        // Находим цель при необходимости
        if (target == null || target.isRemoved() || !isValidTarget(target)) {
            target = findTarget(entity);
            if (target == null) {
                // Нет цели, возвращаемся в состояние IDLE
                LOGGER.info("FollowState: No target found, returning to idle state");
                entity.getStateManager().changeState("idle");
                return;
            }
        }

        long currentTime = System.currentTimeMillis();

        // Обновляем путь с интервалом
        if (currentTime - lastPathUpdate > PATH_UPDATE_INTERVAL) {
            updatePath(entity, target);
            lastPathUpdate = currentTime;
        }

        // Управляем анимацией на основе фактического движения
        updateAnimation(entity);
    }

    @Override
    public void exit(CustomMobEntity entity) {
        LOGGER.info("FollowState: Entity {} exited follow state", entity.getId());
        super.exit(entity);
    }

    /**
     * Обновляет путь к цели
     * @param entity Сущность
     * @param target Цель
     */
    private void updatePath(CustomMobEntity entity, LivingEntity target) {
        double distanceToTarget = entity.distanceTo(target);
        PathNavigation navigator = entity.getNavigation();

        // Очищаем текущий путь
        navigator.stop();

        // Если мы на нужном расстоянии, просто смотрим на цель
        if (Math.abs(distanceToTarget - desiredDistance) < 1.0) {
            entity.getLookControl().setLookAt(target, 30.0f, 30.0f);
            return;
        }

        // Рассчитываем целевую позицию
        Vec3 targetPos = target.position();
        Vec3 entityPos = entity.position();
        Vec3 direction;

        if (distanceToTarget < desiredDistance) {
            // Отходим
            direction = entityPos.subtract(targetPos).normalize();
        } else {
            // Подходим
            direction = targetPos.subtract(entityPos).normalize();
        }

        double distanceToMove = Math.abs(distanceToTarget - desiredDistance);
        if (distanceToMove > 10) distanceToMove = 10;

        double targetX = entityPos.x + direction.x * distanceToMove;
        double targetZ = entityPos.z + direction.z * distanceToMove;

        // Устанавливаем путь
        navigator.moveTo(targetX, entityPos.y, targetZ, 1.0);
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
     * @return Цель или null, если цель не найдена
     */
    private LivingEntity findTarget(CustomMobEntity entity) {
        // По умолчанию следуем за ближайшим игроком
        return entity.level().getNearestPlayer(
                FOLLOW_TARGETING,
                entity
        );
    }

    /**
     * Проверяет, является ли цель валидной
     * @param target Цель
     * @return true, если цель валидна
     */
    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive();
    }
}