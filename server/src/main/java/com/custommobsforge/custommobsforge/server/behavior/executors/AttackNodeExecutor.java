package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Исполнитель узла атаки (SERVER SIDE ONLY)
 */
public class AttackNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        // Получаем параметры атаки
        double damage = getParameter(node, "damage", 3.0, Double.class);
        double range = getParameter(node, "range", 3.0, Double.class);
        double angle = getParameter(node, "angle", 90.0, Double.class); // Угол атаки в градусах
        String animationId = getParameter(node, "animation", null, String.class);
        boolean playAnimation = animationId != null && !animationId.isEmpty();

        System.out.println("[AttackNode] Executing attack - damage: " + damage +
                ", range: " + range + ", angle: " + angle + " for entity " + entity.getId());

        // Проверяем кулдаун атаки
        long lastAttackTime = executor.getBlackboard().getValue(nodeId + ":last_attack", 0L);
        long attackCooldown = getParameter(node, "cooldown", 1000L, Long.class); // мс
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAttackTime < attackCooldown) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Находим цели в радиусе атаки
        List<LivingEntity> targets = findTargetsInRange(entity, range, angle);

        if (targets.isEmpty()) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Воспроизводим анимацию атаки если указана
        if (playAnimation) {
            if (entity.getMobData() != null && entity.getMobData().getAnimations() != null) {
                var animationMapping = entity.getMobData().getAnimations().get(animationId);
                if (animationMapping != null) {
                    entity.setAnimation(animationMapping.getAnimationName(),
                            animationMapping.isLoop(),
                            animationMapping.getSpeed());
                } else {
                    entity.setAnimation(animationId, false, 1.0f);
                }
            }
        }

        // Наносим урон всем целям
        for (LivingEntity target : targets) {
            target.hurt(entity.damageSources().mobAttack(entity), (float) damage);
            System.out.println("[AttackNode] Dealt " + damage + " damage to " + target.getName().getString());
        }

        // Обновляем время последней атаки
        executor.getBlackboard().setValue(nodeId + ":last_attack", currentTime);

        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }

    /**
     * Находит цели в радиусе и угле атаки
     */
    private List<LivingEntity> findTargetsInRange(CustomMobEntity entity, double range, double angle) {
        // Создаем область поиска
        AABB searchArea = entity.getBoundingBox().inflate(range);

        // Получаем всех живых сущностей в области
        List<LivingEntity> potentialTargets = entity.level().getEntitiesOfClass(
                LivingEntity.class,
                searchArea,
                target -> target != entity && target.isAlive() &&
                        (target instanceof Player || isValidTarget(target))
        );

        // Фильтруем по углу атаки
        return potentialTargets.stream()
                .filter(target -> isInAttackAngle(entity, target, angle))
                .toList();
    }

    /**
     * Проверяет, является ли цель валидной для атаки
     */
    private boolean isValidTarget(LivingEntity target) {
        // Можно добавить дополнительную логику
        return true;
    }

    /**
     * Проверяет, находится ли цель в угле атаки
     */
    private boolean isInAttackAngle(CustomMobEntity entity, LivingEntity target, double maxAngle) {
        if (maxAngle >= 360.0) {
            return true; // Атака во всех направлениях
        }

        // Вычисляем вектор направления взгляда моба
        double lookX = Math.sin(Math.toRadians(-entity.getYRot()));
        double lookZ = Math.cos(Math.toRadians(-entity.getYRot()));

        // Вычисляем вектор к цели
        double deltaX = target.getX() - entity.getX();
        double deltaZ = target.getZ() - entity.getZ();

        // Нормализуем вектор к цели
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (distance == 0) return true;

        deltaX /= distance;
        deltaZ /= distance;

        // Вычисляем угол между векторами
        double dotProduct = lookX * deltaX + lookZ * deltaZ;
        double angleRadians = Math.acos(Math.max(-1.0, Math.min(1.0, dotProduct)));
        double angleDegrees = Math.toDegrees(angleRadians);

        return angleDegrees <= maxAngle / 2.0;
    }
}