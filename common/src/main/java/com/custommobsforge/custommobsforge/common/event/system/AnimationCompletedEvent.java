package com.custommobsforge.custommobsforge.common.event.system;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

/**
 * Событие завершения анимации
 */
public class AnimationCompletedEvent extends Event {
    private final String animationId;
    private final CustomMobEntity entity;

    public AnimationCompletedEvent(String animationId, CustomMobEntity entity) {
        super();
        this.animationId = animationId;
        this.entity = entity;
    }

    public String getAnimationId() {
        return animationId;
    }

    public CustomMobEntity getEntity() {
        return entity;
    }
}