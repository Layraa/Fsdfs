package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class StateUpdatePacket {
    private final int entityId;
    private final String stateId;
    private final Map<String, Object> stateData;

    public StateUpdatePacket(int entityId, String stateId, Map<String, Object> stateData) {
        this.entityId = entityId;
        this.stateId = stateId;
        this.stateData = stateData != null ? stateData : new HashMap<>();
    }

    public static void encode(StateUpdatePacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.entityId);
        buffer.writeUtf(message.stateId);

        // Сериализуем данные состояния как строки
        Map<String, String> stringData = new HashMap<>();
        for (Map.Entry<String, Object> entry : message.stateData.entrySet()) {
            stringData.put(entry.getKey(), entry.getValue().toString());
        }

        buffer.writeInt(stringData.size());
        for (Map.Entry<String, String> entry : stringData.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public static StateUpdatePacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readInt();
        String stateId = buffer.readUtf();

        // Десериализуем данные состояния
        int dataSize = buffer.readInt();
        Map<String, Object> stateData = new HashMap<>();

        for (int i = 0; i < dataSize; i++) {
            String key = buffer.readUtf();
            String value = buffer.readUtf();
            stateData.put(key, value);
        }

        return new StateUpdatePacket(entityId, stateId, stateData);
    }

    public static void handle(StateUpdatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(message.entityId);
                if (entity instanceof CustomMobEntity) {
                    CustomMobEntity mobEntity = (CustomMobEntity) entity;

                    // Изменяем состояние с данными
                    mobEntity.getStateManager().changeState(message.stateId);

                    // Применяем данные состояния
                    if (mobEntity.getStateManager().getCurrentState() != null) {
                        mobEntity.getStateManager().getCurrentState().applyData(message.stateData);
                    }
                }
            }
        });

        context.setPacketHandled(true);
    }

    public int getEntityId() {
        return entityId;
    }

    public String getStateId() {
        return stateId;
    }

    public Map<String, Object> getStateData() {
        return stateData;
    }
}