package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.event.SaveMobDataEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SaveMobDataPacket {
    private final MobData mobData;

    public SaveMobDataPacket(MobData mobData) {
        this.mobData = mobData;
    }

    // Метод для получения данных моба - ЭТОТ МЕТОД ОТСУТСТВОВАЛ
    public MobData getMobData() {
        return mobData;
    }

    public static void encode(SaveMobDataPacket message, FriendlyByteBuf buffer) {
        if (message.mobData != null) {
            message.mobData.writeToBuffer(buffer);
        } else {
            // Запись пустых данных для предотвращения ошибок
            buffer.writeBoolean(false);
        }
    }

    public static SaveMobDataPacket decode(FriendlyByteBuf buffer) {
        try {
            // Проверяем, есть ли данные для чтения
            if (buffer.isReadable()) {
                MobData data = MobData.readFromBuffer(buffer);
                return new SaveMobDataPacket(data);
            } else {
                System.err.println("Error decoding SaveMobDataPacket: Buffer not readable");
                return new SaveMobDataPacket(null);
            }
        } catch (Exception e) {
            System.err.println("Error decoding SaveMobDataPacket: " + e.getMessage());
            e.printStackTrace();
            return new SaveMobDataPacket(null);
        }
    }

    public static void handle(SaveMobDataPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            try {
                ServerPlayer player = context.getSender();
                if (player != null && player.hasPermissions(2)) { // Проверка прав оператора
                    System.out.println("Received SaveMobDataPacket from player: " + player.getName().getString());

                    // Проверим, что данные не null
                    if (message.getMobData() == null) {
                        System.err.println("Error: MobData is null in SaveMobDataPacket");
                        return;
                    }

                    // Публикуем событие с полученными данными
                    MinecraftForge.EVENT_BUS.post(
                            new SaveMobDataEvent(message.getMobData(), player));
                } else {
                    System.err.println("Error: Player is null or doesn't have permissions");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error processing SaveMobDataPacket: " + e.getMessage());
            }
        });

        context.setPacketHandled(true);
    }
}