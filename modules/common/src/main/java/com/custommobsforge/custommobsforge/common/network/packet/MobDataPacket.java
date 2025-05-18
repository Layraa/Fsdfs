package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

public class MobDataPacket {
    private MobData mobData;

    public MobDataPacket(MobData mobData) {
        this.mobData = mobData;
    }

    public static void encode(MobDataPacket message, FriendlyByteBuf buffer) {
        message.mobData.writeToBuffer(buffer);
    }

    public static MobDataPacket decode(FriendlyByteBuf buffer) {
        return new MobDataPacket(MobData.readFromBuffer(buffer));
    }

    public static void handle(MobDataPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            // Получаем данные моба
            MobData data = message.mobData;

            if (data != null) {
                System.out.println("MobDataPacket: Received mob data from server for ID: " + data.getId() +
                        ", name: " + data.getName() +
                        ", model: " + data.getModelPath() +
                        ", texture: " + data.getTexturePath());

                // Выводим информацию об анимациях для отладки
                if (data.getAnimations() != null) {
                    System.out.println("MobDataPacket: Animation mappings received:");
                    for (Map.Entry<String, com.custommobsforge.custommobsforge.common.data.AnimationMapping> entry :
                            data.getAnimations().entrySet()) {
                        System.out.println("  " + entry.getKey() + " -> " +
                                entry.getValue().getAnimationName() +
                                " (loop: " + entry.getValue().isLoop() +
                                ", speed: " + entry.getValue().getSpeed() + ")");
                    }
                }

                // Обновляем данные всех существующих мобов с этим ID
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    for (Entity entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof CustomMobEntity) {
                            CustomMobEntity mobEntity = (CustomMobEntity) entity;
                            if (mobEntity.getMobId() != null && mobEntity.getMobId().equals(data.getId())) {
                                System.out.println("MobDataPacket: Updating existing entity " + entity.getId() +
                                        " with mob data for ID: " + data.getId());
                                mobEntity.setMobData(data);
                            }
                        }
                    }
                }
            } else {
                System.err.println("MobDataPacket: Received null mob data from server");
            }
        });

        context.setPacketHandled(true);
    }

    public MobData getMobData() {
        return mobData;
    }
}