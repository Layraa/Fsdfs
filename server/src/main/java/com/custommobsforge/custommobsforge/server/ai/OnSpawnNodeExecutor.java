package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.HashSet;
import java.util.Set;

public class OnSpawnNodeExecutor implements NodeExecutor {
    // Set для отслеживания узлов, которые уже были активированы
    private static final Set<String> activatedNodes = new HashSet<>();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Уникальный идентификатор для данного узла и сущности
        String nodeEntityId = entity.getId() + ":" + node.getId();

        // Проверяем, был ли узел уже активирован
        if (activatedNodes.contains(nodeEntityId)) {
            return false;
        }

        // Получаем параметры
        boolean delayEnabled = node.getCustomParameterAsBoolean("delayEnabled", false);
        double delay = node.getCustomParameterAsDouble("delay", 0.0);

        // Если задержка включена, используем узел таймера
        if (delayEnabled && delay > 0) {
            // Создаем временный узел таймера
            BehaviorNode timerNode = new BehaviorNode("TimerNode", "Spawn Delay");
            timerNode.setCustomParameter("duration", delay);
            timerNode.setCustomParameter("repeat", false);

            NodeExecutor timerExecutor = new TimerNodeExecutor();
            if (!timerExecutor.execute(entity, timerNode, executor)) {
                return false; // Таймер еще работает
            }
        }

        // Помечаем узел как активированный
        activatedNodes.add(nodeEntityId);

        // Выполняем дочерние узлы
        boolean anyChildSucceeded = false;
        for (BehaviorNode child : executor.getChildNodes(node)) {
            if (executor.executeNode(child)) {
                anyChildSucceeded = true;
            }
        }

        return anyChildSucceeded;
    }
}