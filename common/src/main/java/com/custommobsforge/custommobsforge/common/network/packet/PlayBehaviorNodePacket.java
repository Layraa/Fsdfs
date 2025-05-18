package com.custommobsforge.custommobsforge.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayBehaviorNodePacket {
    private int entityId;
    private String nodeId;
    private String nodeType;

    public PlayBehaviorNodePacket(int entityId, String nodeId, String nodeType) {
        this.entityId = entityId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
    }

    public static void encode(PlayBehaviorNodePacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.entityId);
        buffer.writeUtf(message.nodeId);
        buffer.writeUtf(message.nodeType);
    }

    public static PlayBehaviorNodePacket decode(FriendlyByteBuf buffer) {
        return new PlayBehaviorNodePacket(
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readUtf()
        );
    }

    public static void handle(PlayBehaviorNodePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            // На клиенте мы отображаем выполнение узла поведения
            // Это будет реализовано в клиентском модуле

            // com.custommobsforge.custommobsforge.client.behavior.BehaviorVisualizer.showNodeExecution(message);
        });

        context.setPacketHandled(true);
    }

    public int getEntityId() {
        return entityId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }
}