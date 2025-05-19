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

        // Проверяем, выполнялся ли узел ранее и сохранялся ли в статусах
        NodeStatus status = executor.getNodeStatus(node);
        if (status == NodeStatus.SUCCESS) {
            LOGGER.info("SequenceNode: Node {} already completed successfully, returning success", node.getId());
            executor.logNodeExecution("SequenceNode", node.getId(), "already successful, returning success", false);
            return true;
        } else if (status == NodeStatus.FAILURE) {
            LOGGER.info("SequenceNode: Node {} already failed, returning failure", node.getId());
            executor.logNodeExecution("SequenceNode", node.getId(), "already failed, returning failure", false);
            return false;
        }

        // Получаем все дочерние узлы
        List<BehaviorNode> children = executor.getChildNodes(node);

        // Если нет дочерних узлов, возвращаем успех
        if (children.isEmpty()) {
            LOGGER.info("SequenceNode: Node {} has no children, returning success", node.getId());
            executor.logNodeExecution("SequenceNode", node.getId(), "no children, returning success", false);
            return true;
        }

        // Выводим список дочерних узлов для отладки
        LOGGER.info("SequenceNode: Node {} has {} children to execute sequentially",
                node.getId(), children.size());

        for (int i = 0; i < children.size(); i++) {
            BehaviorNode child = children.get(i);
            LOGGER.info("SequenceNode: Child {}/{}: {} of type {}",
                    i+1, children.size(), child.getId(), child.getType());
        }

        // Проверяем состояние детей
        boolean allChildrenCompleted = true;
        boolean anyChildFailed = false;

        for (BehaviorNode child : children) {
            NodeStatus childStatus = executor.getNodeStatus(child);

            if (childStatus == NodeStatus.FAILURE) {
                anyChildFailed = true;
                break;
            } else if (childStatus != NodeStatus.SUCCESS) {
                allChildrenCompleted = false;
            }
        }

        if (anyChildFailed) {
            // Если какой-то из детей уже завершился с ошибкой, вся последовательность завершается с ошибкой
            executor.logNodeExecution("SequenceNode", node.getId(),
                    "sequence failed because a child failed", false);
            return false;
        }

        if (allChildrenCompleted) {
            // Если все дети уже успешно выполнены, последовательность успешна
            executor.logNodeExecution("SequenceNode", node.getId(),
                    "all children already completed successfully", false);
            return true;
        }

        // В BehaviorTreeExecutor теперь есть механизм для обработки последовательностей
        executor.logNodeExecution("SequenceNode", node.getId(),
                "registered " + children.size() + " children for sequential execution", false);

        // Возвращаем успех, чтобы BehaviorTreeExecutor знал, что нужно начать выполнение последовательности
        return true;
    }
}