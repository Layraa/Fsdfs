package com.custommobsforge.custommobsforge.common.network.packet;

import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SaveBehaviorTreePacket {
    private BehaviorTree behaviorTree;

    public SaveBehaviorTreePacket(BehaviorTree behaviorTree) {
        this.behaviorTree = behaviorTree;
    }

    public static void encode(SaveBehaviorTreePacket message, FriendlyByteBuf buffer) {
        message.behaviorTree.writeToBuffer(buffer);
    }

    public static SaveBehaviorTreePacket decode(FriendlyByteBuf buffer) {
        BehaviorTree tree = BehaviorTree.readFromBuffer(buffer);
        return new SaveBehaviorTreePacket(tree);
    }

    public static void handle(SaveBehaviorTreePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.hasPermissions(2)) { // Проверка прав оператора
                // Вызов обработчика пакета из серверного модуля через событие
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new com.custommobsforge.custommobsforge.common.event.SaveBehaviorTreeEvent(
                                message.behaviorTree, player));
            }
        });

        context.setPacketHandled(true);
    }

    public BehaviorTree getBehaviorTree() {
        return behaviorTree;
    }
}