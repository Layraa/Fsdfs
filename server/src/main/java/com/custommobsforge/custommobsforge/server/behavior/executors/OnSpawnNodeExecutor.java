package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Исполнитель узла события появления
 * Активируется при появлении моба в мире
 */
public class OnSpawnNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        // Проверяем, выполнялся ли уже этот узел
        boolean alreadyExecuted = executor.getBlackboard().getBooleanValue(nodeId + ":executed", false);

        if (alreadyExecuted) {
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Получаем параметры
        double delay = getParameter(node, "delay", 0.0, Double.class); // секунды
        String animationId = getParameter(node, "animation", null, String.class);

        long currentTime = System.currentTimeMillis();

        // Проверяем задержку
        if (delay > 0) {
            Long startTime = executor.getBlackboard().getValue(nodeId + ":start_time", null);

            if (startTime == null) {
                executor.getBlackboard().setValue(nodeId + ":start_time", currentTime);
                System.out.println("[OnSpawnNode] Started spawn delay: " + delay + " seconds");
                return BehaviorTreeExecutor.NodeStatus.RUNNING;
            }

            long elapsedTime = currentTime - startTime;
            if (elapsedTime < (delay * 1000)) {
                return BehaviorTreeExecutor.NodeStatus.RUNNING;
            }
        }

        // Выполняем действия при спавне
        System.out.println("[OnSpawnNode] Executing spawn actions for entity " + entity.getId());

        // Воспроизводим анимацию если указана
        if (animationId != null && !animationId.isEmpty()) {
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

        // Выполняем дочерние узлы если есть
        var children = executor.getChildNodes(node);
        if (!children.isEmpty()) {
            for (BehaviorNode child : children) {
                executor.executeNode(child);
            }
        }

        // Помечаем как выполненный
        executor.getBlackboard().setValue(nodeId + ":executed", true);

        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }
}