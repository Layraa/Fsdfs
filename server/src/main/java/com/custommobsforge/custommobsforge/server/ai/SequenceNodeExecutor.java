package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.ai.NodeStatus;
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

            // Отмечаем последовательность как успешно завершенную
            executor.completeNode(node, true);
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

        // Получаем текущий индекс выполнения последовательности
        int currentIndex = executor.getBlackboard().getValue(node.getId() + ":currentIndex", 0);

        // Если все дочерние узлы уже выполнены, возвращаем успех
        if (currentIndex >= children.size()) {
            LOGGER.info("SequenceNode: All {} children completed successfully", children.size());
            executor.completeNode(node, true);
            executor.logNodeExecution("SequenceNode", node.getId(),
                    "all children already completed successfully", false);
            return true;
        }

        // Получаем текущий узел для выполнения
        BehaviorNode currentChild = children.get(currentIndex);

        // Проверяем статус текущего узла
        NodeStatus childStatus = executor.getNodeStatus(currentChild);

        LOGGER.info("SequenceNode: Executing child {}/{}: {} of type {} (current status: {})",
                currentIndex+1, children.size(), currentChild.getId(), currentChild.getType(), childStatus);

        // Если узел уже успешно выполнен, переходим к следующему
        if (childStatus == NodeStatus.SUCCESS) {
            LOGGER.info("SequenceNode: Child {} already completed successfully, moving to next", currentChild.getId());

            // Увеличиваем индекс и продолжаем выполнение последовательности
            executor.getBlackboard().setValue(node.getId() + ":currentIndex", currentIndex + 1);

            // Рекурсивно вызываем себя, чтобы перейти к следующему узлу
            return execute(entity, node, executor);
        }
        // Если узел завершился неудачно, вся последовательность завершается неудачно
        else if (childStatus == NodeStatus.FAILURE) {
            LOGGER.info("SequenceNode: Child {} failed, sequence fails", currentChild.getId());
            executor.completeNode(node, false);
            return false;
        }

        // Если узел еще не выполнялся или в процессе выполнения, запускаем его
        LOGGER.info("SequenceNode: Executing child node {}", currentChild.getId());

        // В BehaviorTreeExecutor теперь есть механизм для обработки последовательностей
        executor.logNodeExecution("SequenceNode", node.getId(),
                "registered for sequential execution", false);

        // Возвращаем успех, чтобы BehaviorTreeExecutor знал, что нужно начать выполнение последовательности
        return true;
    }
}