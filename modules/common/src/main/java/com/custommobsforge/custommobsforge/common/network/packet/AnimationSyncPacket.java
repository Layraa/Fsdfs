package com.custommobsforge.custommobsforge.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AnimationSyncPacket {
    private int entityId;
    private String animationId;
    private float animationSpeed;
    private boolean loop;

    public AnimationSyncPacket(int entityId, String animationId, float animationSpeed, boolean loop) {
        this.entityId = entityId;
        this.animationId = animationId;
        this.animationSpeed = animationSpeed;
        this.loop = loop;
    }

    public static void encode(AnimationSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.entityId);
        buffer.writeUtf(message.animationId);
        buffer.writeFloat(message.animationSpeed);
        buffer.writeBoolean(message.loop);
    }

    public static AnimationSyncPacket decode(FriendlyByteBuf buffer) {
        return new AnimationSyncPacket(
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readBoolean()
        );
    }

    public static void handle(AnimationSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            // На клиенте мы находим сущность и устанавливаем анимацию
            // Это будет реализовано в клиентском модуле

            // Здесь будет вызов клиентского кода для обработки анимации
            // com.custommobsforge.custommobsforge.client.animation.AnimationHandler.handleAnimationSync(message);
        });

        context.setPacketHandled(true);
    }

    public int getEntityId() {
        return entityId;
    }

    public String getAnimationId() {
        return animationId;
    }

    public float getAnimationSpeed() {
        return animationSpeed;
    }

    public boolean isLoop() {
        return loop;
    }
}