package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.List;

public class ParallelNodeExecutor implements NodeExecutor {
    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем все дочерние узлы
        List<BehaviorNode> children = executor.getChildNodes(node);

        // Если нет дочерних узлов, возвращаем успех
        if (children.isEmpty()) {
            return true;
        }

        // Параметр прерывания при неудаче
        boolean abortOnFailure = node.getCustomParameterAsBoolean("abortOnFailure", true);

        boolean allSuccess = true;

        // Выполняем все дочерние узлы
        for (BehaviorNode child : children) {
            boolean success = executor.executeNode(child);

            // Если узел не выполнился успешно
            if (!success) {
                allSuccess = false;

                // Если настроено прерывание при неудаче, прекращаем выполнение
                if (abortOnFailure) {
                    return false;
                }
            }
        }

        // Возвращаем общий результат выполнения
        return allSuccess;
    }
}