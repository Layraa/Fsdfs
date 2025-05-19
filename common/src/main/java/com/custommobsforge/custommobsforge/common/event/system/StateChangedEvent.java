package com.custommobsforge.custommobsforge.common.event.system;

import com.custommobsforge.custommobsforge.common.fsm.MobState;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Событие смены состояния
 */
public class StateChangedEvent extends Event {
    private final MobState oldState;
    private final MobState newState;
    private final CustomMobEntity entity;

    public StateChangedEvent(MobState oldState, MobState newState, CustomMobEntity entity) {
        super();
        this.oldState = oldState;
        this.newState = newState;
        this.entity = entity;
    }

    public MobState getOldState() {
        return oldState;
    }

    public MobState getNewState() {
        return newState;
    }

    public CustomMobEntity getEntity() {
        return entity;
    }
}