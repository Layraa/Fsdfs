package com.custommobsforge.custommobsforge.common.event.system;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Событие начала выполнения узла
 */
public class NodeStartedEvent extends Event {
    private final BehaviorNode node;
    private final CustomMobEntity entity;

    public NodeStartedEvent(BehaviorNode node, CustomMobEntity entity) {
        super();
        this.node = node;
        this.entity = entity;
    }

    public BehaviorNode getNode() {
        return node;
    }

    public CustomMobEntity getEntity() {
        return entity;
    }
}