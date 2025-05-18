package com.custommobsforge.custommobsforge.common.event.system;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Событие завершения выполнения узла
 */
public class NodeCompletedEvent extends Event {
    private final BehaviorNode node;
    private final CustomMobEntity entity;
    private final boolean success;

    public NodeCompletedEvent(BehaviorNode node, CustomMobEntity entity, boolean success) {
        super();
        this.node = node;
        this.entity = entity;
        this.success = success;
    }

    public BehaviorNode getNode() {
        return node;
    }

    public CustomMobEntity getEntity() {
        return entity;
    }

    public boolean isSuccess() {
        return success;
    }
}