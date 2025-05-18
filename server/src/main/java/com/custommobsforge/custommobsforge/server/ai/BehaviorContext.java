package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;

/**
 * Интерфейс для контекста выполнения дерева поведения
 */
public interface BehaviorContext {
    /**
     * Отмечает узел как завершенный
     */
    void completeNode(BehaviorNode node, boolean success);

    /**
     * Отмечает узел как требующий больше времени для выполнения
     */
    void setNodeNeedsMoreTime(BehaviorNode node, boolean needsMoreTime);

    /**
     * Проверяет, требует ли узел больше времени для выполнения
     */
    boolean doesNodeNeedMoreTime(BehaviorNode node);

    /**
     * Получает текущий статус узла
     */
    NodeStatus getNodeStatus(BehaviorNode node);
}