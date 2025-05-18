package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.event.RequestMobListEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestMobListPacket {
    public RequestMobListPacket() {
        // Empty constructor
    }

    public static void encode(RequestMobListPacket message, FriendlyByteBuf buffer) {
        // No data to encode
    }

    public static RequestMobListPacket decode(FriendlyByteBuf buffer) {
        return new RequestMobListPacket();
    }

    public static void handle(RequestMobListPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            // Публикуем событие запроса списка мобов
            MinecraftForge.EVENT_BUS.post(new RequestMobListEvent(context.getSender()));
        });

        context.setPacketHandled(true);
    }
}