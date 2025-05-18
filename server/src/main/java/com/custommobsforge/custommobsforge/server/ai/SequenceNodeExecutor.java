package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SequenceNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        executor.logNodeExecution("SequenceNode", node.getId(), "starting sequence", true);

        // Получаем все дочерние узлы
        List<BehaviorNode> children = executor.getChildNodes(node);

        // Если нет дочерних узлов, возвращаем успех
        if (children.isEmpty()) {
            LOGGER.info("SequenceNode: Node {} has no children, returning success", node.getId());
            executor.logNodeExecution("SequenceNode", node.getId(), "no children, returning success", false);
            return true;
        }

        // В BehaviorTreeExecutor теперь есть механизм для обработки последовательностей
        LOGGER.info("SequenceNode: Node {} has {} children to execute sequentially",
                node.getId(), children.size());

        // Выводим список дочерних узлов для отладки
        for (int i = 0; i < children.size(); i++) {
            BehaviorNode child = children.get(i);
            LOGGER.info("SequenceNode: Child {}/{}: {} of type {}",
                    i+1, children.size(), child.getId(), child.getType());
        }

        executor.logNodeExecution("SequenceNode", node.getId(),
                "registered " + children.size() + " children for sequential execution", false);

        // Возвращаем успех, чтобы BehaviorTreeExecutor знал, что нужно начать выполнение последовательности
        return true;
    }
}