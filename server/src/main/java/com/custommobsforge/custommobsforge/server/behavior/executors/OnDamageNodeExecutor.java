package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Исполнитель узла события получения урона
 * Активируется когда моб получает урон
 */
public class OnDamageNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        // Получаем параметры
        double minDamage = getParameter(node, "min_damage", 0.0, Double.class);
        boolean playerOnly = getParameter(node, "player_only", false, Boolean.class);
        String animationId = getParameter(node, "animation", null, String.class);

        // Проверяем, произошло ли событие урона
        boolean damageTriggered = executor.getBlackboard().getBooleanValue("damage_triggered", false);
        double lastDamageAmount = executor.getBlackboard().getDoubleValue("last_damage_amount", 0.0);
        boolean lastDamageFromPlayer = executor.getBlackboard().getBooleanValue("last_damage_from_player", false);

        if (!damageTriggered) {
            // Регистрируем узел как обработчик события урона
            executor.getBlackboard().setValue("damage_handler_node", nodeId);
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Проверяем условия активации
        if (lastDamageAmount < minDamage) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        if (playerOnly && !lastDamageFromPlayer) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Выполняем действия при получении урона
        System.out.println("[OnDamageNode] Executing damage response for entity " + entity.getId() +
                " (damage: " + lastDamageAmount + ")");

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

        // Сбрасываем флаг события урона
        executor.getBlackboard().setValue("damage_triggered", false);

        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }
}