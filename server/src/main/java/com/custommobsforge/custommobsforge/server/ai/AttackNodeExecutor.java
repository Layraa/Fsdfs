package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AttackNodeExecutor implements NodeExecutor {
    private static final TargetingConditions ATTACK_TARGETING = TargetingConditions.forCombat().range(16.0D);

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры узла
        double damage = node.getCustomParameterAsDouble("damage", 3.0);
        double range = node.getCustomParameterAsDouble("range", 2.0);
        double angle = node.getCustomParameterAsDouble("angle", 60.0);

        // Воспроизводим анимацию атаки
        if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
            entity.setAnimation(node.getAnimationId(), node.isLoopAnimation(), (float) node.getAnimationSpeed());
        } else {
            executor.playAnimation("ATTACK");
        }

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            return true; // На клиенте просто воспроизводим анимацию
        }

        // Ищем цели в указанном радиусе и угле
        List<LivingEntity> targets = findTargetsInCone(entity, range, angle);

        // Если нет целей, возвращаем неудачу
        if (targets.isEmpty()) {
            return false;
        }

        // Атакуем все цели
        boolean anyHit = false;
        for (LivingEntity target : targets) {
            // Наносим урон
            if (target.hurt(entity.level().damageSources().mobAttack(entity), (float) damage)) {
                anyHit = true;
            }
        }

        return anyHit;
    }

    // Метод поиска целей в конусе перед мобом
    private List<LivingEntity> findTargetsInCone(CustomMobEntity attacker, double range, double angle) {
        // Создаем область поиска
        AABB box = attacker.getBoundingBox().inflate(range, range / 2, range);

        // Получаем все живые сущности в этой области
        List<LivingEntity> possibleTargets = attacker.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                target -> ATTACK_TARGETING.test(attacker, target)
        );

        // Фильтруем по углу
        Vec3 lookVec = attacker.getLookAngle();
        double angleRadians = Math.toRadians(angle / 2);
        double cosAngle = Math.cos(angleRadians);

        possibleTargets.removeIf(target -> {
            // Проверяем, находится ли цель перед атакующим в пределах указанного угла
            Vec3 targetVec = target.position().subtract(attacker.position()).normalize();
            double dot = lookVec.dot(targetVec);

            // Если dot product меньше косинуса угла, цель вне конуса
            return dot < cosAngle;
        });

        return possibleTargets;
    }
}