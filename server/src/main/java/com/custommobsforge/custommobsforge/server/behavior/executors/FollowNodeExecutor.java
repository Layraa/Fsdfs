package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

/**
 * Исполнитель узла следования за игроком
 */
public class FollowNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры следования
        double followDistance = getParameter(node, "distance", 5.0, Double.class);
        double speed = getParameter(node, "speed", 1.0, Double.class);
        double stopDistance = getParameter(node, "stop_distance", 2.0, Double.class);

        // Находим ближайшего игрока
        Player nearestPlayer = entity.level().getNearestPlayer(
                entity.getX(), entity.getY(), entity.getZ(),
                followDistance, false
        );

        if (nearestPlayer == null) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Вычисляем расстояние до игрока
        double distanceToPlayer = entity.distanceTo(nearestPlayer);

        // Если уже достаточно близко, останавливаемся
        if (distanceToPlayer <= stopDistance) {
            entity.getNavigation().stop();
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Если игрок слишком далеко, прекращаем следование
        if (distanceToPlayer > followDistance) {
            entity.getNavigation().stop();
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Устанавливаем навигацию к игроку
        PathNavigation navigation = entity.getNavigation();

        // Проверяем, нужно ли обновить путь
        String nodeId = node.getId();
        long lastPathUpdate = executor.getBlackboard().getValue(nodeId + ":last_path_update", 0L);
        long currentTime = System.currentTimeMillis();

        // Обновляем путь каждые 500 мс для плавности
        if (currentTime - lastPathUpdate > 500) {
            boolean pathSet = navigation.moveTo(nearestPlayer, speed);
            executor.getBlackboard().setValue(nodeId + ":last_path_update", currentTime);

            if (!pathSet) {
                System.out.println("[FollowNode] Failed to set path to player");
                return BehaviorTreeExecutor.NodeStatus.FAILURE;
            }
        }

        // Проверяем, движется ли моб
        if (navigation.isInProgress()) {
            return BehaviorTreeExecutor.NodeStatus.RUNNING;
        } else {
            // Путь завершен или прерван
            if (distanceToPlayer <= stopDistance) {
                return BehaviorTreeExecutor.NodeStatus.SUCCESS;
            } else {
                return BehaviorTreeExecutor.NodeStatus.RUNNING;
            }
        }
    }
}