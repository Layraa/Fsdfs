package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.List;

public class SequenceNodeExecutor implements NodeExecutor {
    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем все дочерние узлы
        List<BehaviorNode> children = executor.getChildNodes(node);

        // Если нет дочерних узлов, возвращаем успех
        if (children.isEmpty()) {
            return true;
        }

        // Выполняем каждый дочерний узел по порядку
        for (BehaviorNode child : children) {
            // Если хотя бы один дочерний узел не выполнился, прерываем выполнение последовательности
            if (!executor.executeNode(child)) {
                return false;
            }
        }

        // Если все дочерние узлы выполнились успешно, возвращаем успех
        return true;
    }
}