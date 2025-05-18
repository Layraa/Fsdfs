package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.data.MobData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

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
            // Публикуем событие получения данных моба
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.custommobsforge.custommobsforge.common.event.MobDataReceivedEvent(message.mobData)
            );
        });

        context.setPacketHandled(true);
    }

    public MobData getMobData() {
        return mobData;
    }
}