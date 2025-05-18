package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class RequestMobDataPacket {
    private String mobId;

    public RequestMobDataPacket(String mobId) {
        this.mobId = mobId;
    }

    public static void encode(RequestMobDataPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.mobId);
    }

    public static RequestMobDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestMobDataPacket(buffer.readUtf());
    }

    public static void handle(RequestMobDataPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerLevel level = (ServerLevel) player.level();

                // Улучшенное логирование
                System.out.println("RequestMobDataPacket: Received request for mob data with ID: " + message.mobId +
                        " from player: " + player.getName().getString());

                // Загрузка данных моба
                MobData mobData = MobConfigManager.loadMobConfig(message.mobId, level);

                if (mobData != null) {
                    System.out.println("RequestMobDataPacket: Found mob data for ID: " + message.mobId +
                            ", name: " + mobData.getName() +
                            ", model: " + mobData.getModelPath() +
                            ", texture: " + mobData.getTexturePath());

                    // Отправляем данные клиенту
                    NetworkManager.INSTANCE.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new MobDataPacket(mobData)
                    );
                } else {
                    System.out.println("RequestMobDataPacket: Mob data not found for ID: " + message.mobId);
                }
            }
        });

        context.setPacketHandled(true);
    }
}