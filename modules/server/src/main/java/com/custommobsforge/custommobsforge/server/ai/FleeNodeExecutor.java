package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FleeNodeExecutor implements NodeExecutor {
    private static final TargetingConditions FLEE_TARGETING = TargetingConditions.forNonCombat().range(16.0D);

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры узла
        double distance = node.getCustomParameterAsDouble("distance", 10.0);
        double speed = node.getCustomParameterAsDouble("speed", 1.2);
        boolean targetPlayer = node.getCustomParameterAsBoolean("targetPlayer", true);

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            return false; // На клиенте ничего не делаем
        }

        // Ищем цель для бегства
        LivingEntity threat = findThreat(entity, targetPlayer);

        // Если нет угрозы, возвращаем неудачу
        if (threat == null) {
            return false;
        }

        // Вычисляем расстояние до угрозы
        double distanceToThreat = entity.distanceTo(threat);

        // Если угроза слишком далеко, нет необходимости убегать
        if (distanceToThreat > distance) {
            // Воспроизводим анимацию ожидания
            executor.playAnimation("IDLE");
            return true;
        }

        // Вычисляем направление от угрозы (противоположное направлению к угрозе)
        Vec3 threatPos = threat.position();
        Vec3 entityPos = entity.position();
        Vec3 direction = entityPos.subtract(threatPos).normalize();

        // Перемещаем моба в направлении от угрозы
        entity.getMoveControl().setWantedPosition(
                entityPos.x + direction.x * speed * 2,
                entityPos.y,
                entityPos.z + direction.z * speed * 2,
                speed
        );

        // Воспроизводим анимацию бега
        if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
            entity.setAnimation(node.getAnimationId(), node.isLoopAnimation(), (float) node.getAnimationSpeed());
        } else {
            executor.playAnimation("RUN");
        }

        return true;
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