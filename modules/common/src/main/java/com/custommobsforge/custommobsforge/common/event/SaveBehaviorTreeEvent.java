package com.custommobsforge.custommobsforge.common.event;

import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class SaveBehaviorTreeEvent extends Event {
    private final BehaviorTree behaviorTree;
    private final ServerPlayer player;

    public SaveBehaviorTreeEvent(BehaviorTree behaviorTree, ServerPlayer player) {
        this.behaviorTree = behaviorTree;
        this.player = player;
    }

    public BehaviorTree getBehaviorTree() {
        return behaviorTree;
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}