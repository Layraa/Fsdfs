package com.custommobsforge.custommobsforge.common.event;

import com.custommobsforge.custommobsforge.common.data.MobData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class SaveMobDataEvent extends Event {
    private final MobData mobData;
    private final ServerPlayer player;

    public SaveMobDataEvent(MobData mobData, ServerPlayer player) {
        this.mobData = mobData;
        this.player = player;
    }

    public MobData getMobData() {
        return mobData;
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}