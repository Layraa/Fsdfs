package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.List;

public class SelectorNodeExecutor implements NodeExecutor {
    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем все дочерние узлы
        List<BehaviorNode> children = executor.getChildNodes(node);

        // Если нет дочерних узлов, возвращаем неудачу
        if (children.isEmpty()) {
            return false;
        }

        // Выполняем каждый дочерний узел по порядку
        for (BehaviorNode child : children) {
            // Если хотя бы один дочерний узел выполнился успешно, прерываем выполнение селектора
            if (executor.executeNode(child)) {
                return true;
            }
        }

        // Если ни один дочерний узел не выполнился успешно, возвращаем неудачу
        return false;
    }
}