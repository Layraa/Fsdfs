package com.custommobsforge.custommobsforge.common.network;

import com.custommobsforge.custommobsforge.common.CommonCustomMobsForge;
import com.custommobsforge.custommobsforge.common.network.packet.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkManager {
    private static final String PROTOCOL_VERSION = "1";

    public static final ResourceLocation CHANNEL_NAME =
            new ResourceLocation(CommonCustomMobsForge.MOD_ID, "main_channel");

    public static SimpleChannel INSTANCE;

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void registerPackets() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        // Существующие пакеты
        INSTANCE.registerMessage(nextId(), SpawnMobPacket.class,
                SpawnMobPacket::encode,
                SpawnMobPacket::decode,
                SpawnMobPacket::handle);

        INSTANCE.registerMessage(nextId(), RequestMobDataPacket.class,
                RequestMobDataPacket::encode,
                RequestMobDataPacket::decode,
                RequestMobDataPacket::handle);

        INSTANCE.registerMessage(nextId(), MobDataPacket.class,
                MobDataPacket::encode,
                MobDataPacket::decode,
                MobDataPacket::handle);

        INSTANCE.registerMessage(nextId(), AnimationSyncPacket.class,
                AnimationSyncPacket::encode,
                AnimationSyncPacket::decode,
                AnimationSyncPacket::handle);

        INSTANCE.registerMessage(nextId(), PlayBehaviorNodePacket.class,
                PlayBehaviorNodePacket::encode,
                PlayBehaviorNodePacket::decode,
                PlayBehaviorNodePacket::handle);

        INSTANCE.registerMessage(nextId(), RequestMobListPacket.class,
                RequestMobListPacket::encode,
                RequestMobListPacket::decode,
                RequestMobListPacket::handle);

        // Зарегистрировать новые пакеты
        INSTANCE.registerMessage(nextId(), SaveMobDataPacket.class,
                SaveMobDataPacket::encode,
                SaveMobDataPacket::decode,
                SaveMobDataPacket::handle);

        INSTANCE.registerMessage(nextId(), SaveBehaviorTreePacket.class,
                SaveBehaviorTreePacket::encode,
                SaveBehaviorTreePacket::decode,
                SaveBehaviorTreePacket::handle);

        // Новый пакет для синхронизации состояний
        INSTANCE.registerMessage(nextId(), StateUpdatePacket.class,
                StateUpdatePacket::encode,
                StateUpdatePacket::decode,
                StateUpdatePacket::handle);
    }
}