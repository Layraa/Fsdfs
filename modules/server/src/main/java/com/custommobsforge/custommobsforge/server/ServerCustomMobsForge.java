package com.custommobsforge.custommobsforge.server;

import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import com.custommobsforge.custommobsforge.server.commands.ServerCommandRegistrationHandler;
import com.custommobsforge.custommobsforge.server.event.MobSpawnEventHandler;
import com.custommobsforge.custommobsforge.server.event.ServerTickHandler;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

@Mod("custommobsforge_server")
public class ServerCustomMobsForge {

    public ServerCustomMobsForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрируем события жизненного цикла
        modEventBus.addListener(this::serverSetup);
        modEventBus.addListener(this::registerAttributes);

        // Регистрируем обработчики событий
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ServerCommandRegistrationHandler.class);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(MobSpawnEventHandler.class);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ServerTickHandler.class);
    }

    private void serverSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Инициализация серверных компонентов
        });
    }

    @SubscribeEvent
    public void registerAttributes(EntityAttributeCreationEvent event) {
        // Регистрируем атрибуты для нашего моба
        event.put(EntityRegistry.CUSTOM_MOB.get(), CustomMobEntity.createAttributes().build());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Инициализация компонентов при запуске сервера
    }
}