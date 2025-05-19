package com.custommobsforge.custommobsforge.common.fsm.states;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.fsm.MobState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Состояние атаки для моба
 */
public class AttackState extends MobState {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private static final TargetingConditions ATTACK_TARGETING = TargetingConditions.forCombat().range(16.0D);

    private LivingEntity target;
    private long attackCooldown = 0;
    private static final long ATTACK_COOLDOWN_TIME = 2000; // 2 секунды
    private static final double ATTACK_RANGE = 2.0;

    public AttackState() {
        super("attack", "Attack State");
    }

    @Override
    public void enter(CustomMobEntity entity) {
        super.enter(entity);
        LOGGER.info("AttackState: Entity {} entered attack state", entity.getId());
        attackCooldown = 0;
    }

    @Override
    public void update(CustomMobEntity entity) {
        // Находим цель при необходимости
        if (target == null || target.isRemoved() || !isValidTarget(target)) {
            target = findTarget(entity);
            if (target == null) {
                // Нет цели, возвращаемся в состояние IDLE
                LOGGER.info("AttackState: No target found, returning to idle state");
                entity.getStateManager().changeState("idle");
                return;
            }
        }

        // Проверяем расстояние до цели
        double distanceToTarget = entity.distanceTo(target);

        // Если расстояние слишком большое, переходим в состояние следования
        if (distanceToTarget > ATTACK_RANGE * 1.5) {
            LOGGER.info("AttackState: Target too far ({}), changing to follow state", distanceToTarget);
            entity.getStateManager().changeState("follow");
            return;
        }

        // Смотрим на цель
        entity.getLookControl().setLookAt(target, 30.0f, 30.0f);

        // Проверяем, можем ли атаковать
        long currentTime = System.currentTimeMillis();
        if (currentTime - attackCooldown > ATTACK_COOLDOWN_TIME) {
            // Можем атаковать
            if (distanceToTarget <= ATTACK_RANGE) {
                performAttack(entity, target);
                attackCooldown = currentTime;
            }
        }
    }

    @Override
    public void exit(CustomMobEntity entity) {
        LOGGER.info("AttackState: Entity {} exited attack state", entity.getId());
        super.exit(entity);
    }

    /**
     * Выполняет атаку
     * @param entity Сущность
     * @param target Цель
     */
    private void performAttack(CustomMobEntity entity, LivingEntity target) {
        LOGGER.info("AttackState: Entity {} attacking {}", entity.getId(), target.getId());

        // Воспроизводим анимацию атаки
        entity.getAnimationAdapter().playAnimation("ATTACK", false, 1.0f);

        // Наносим урон
        float damage = entity.getMobData().getAttribute("attackDamage", 3.0f);
        target.hurt(entity.level().damageSources().mobAttack(entity), damage);
    }

    /**
     * Находит цель для атаки
     * @param entity Сущность
     * @return Цель для атаки или null, если цель не найдена
     */
    private LivingEntity findTarget(CustomMobEntity entity) {
        // По умолчанию атакуем ближайшего игрока
        return entity.level().getNearestPlayer(
                ATTACK_TARGETING,
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