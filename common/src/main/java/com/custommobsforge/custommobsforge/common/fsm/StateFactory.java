package com.custommobsforge.custommobsforge.common.fsm;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.fsm.states.AttackState;
import com.custommobsforge.custommobsforge.common.fsm.states.FollowState;
import com.custommobsforge.custommobsforge.common.fsm.states.IdleState;

/**
 * Фабрика состояний для создания состояний конечного автомата
 */
public class StateFactory {
    /**
     * Создает стандартные состояния для моба
     * @param entity Сущность
     */
    public static void createStandardStates(CustomMobEntity entity) {
        // Создаем основные состояния
        entity.getStateManager().registerState(new IdleState());
        entity.getStateManager().registerState(new FollowState());
        entity.getStateManager().registerState(new AttackState());

        // Устанавливаем начальное состояние
        entity.getStateManager().setInitialState("idle");
    }

    /**
     * Создает состояния из дерева поведения
     * @param entity Сущность
     * @param behaviorTree Дерево поведения
     */
    public static void createStatesFromBehaviorTree(CustomMobEntity entity, BehaviorTree behaviorTree) {
        // Сначала создаем стандартные состояния
        createStandardStates(entity);

        if (behaviorTree == null || behaviorTree.getNodes() == null || behaviorTree.getNodes().isEmpty()) {
            return;
        }

        // Создаем состояния для последовательностей в дереве поведения
        for (BehaviorNode node : behaviorTree.getNodes()) {
            if (node.getType().equalsIgnoreCase("SequenceNode")) {
                AdapterState state = new AdapterState(node.getId(), node.getDescription());
                state.setNodes(behaviorTree.getChildNodes(node.getId()));
                entity.getStateManager().registerState(state);
            }
        }
    }
}