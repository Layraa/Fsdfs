package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

public interface NodeExecutor {
    /**
     * Выполняет узел дерева поведения
     *
     * @param entity Сущность, выполняющая дерево поведения
     * @param node Узел для выполнения
     * @param executor Исполнитель дерева поведения
     * @return true, если узел успешно выполнен, false в противном случае
     */
    boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor);
}