package com.custommobsforge.custommobsforge.server;

import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import com.custommobsforge.custommobsforge.server.commands.ServerCommandRegistrationHandler;
import com.custommobsforge.custommobsforge.server.event.MobSpawnEventHandler;
import com.custommobsforge.custommobsforge.server.event.ServerSaveHandler;
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
    public static final String MOD_ID = "custommobsforge_server";  // Добавляем константу

    public ServerCustomMobsForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        System.out.println("!!!!!!!! ServerCustomMobsForge: Initializing and registering event handlers !!!!!!!!");

        // Явно зарегистрируйте обработчик
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(MobSpawnEventHandler.class);

        // Добавьте проверку после регистрации
        System.out.println("!!!!!!!! ServerCustomMobsForge: MobSpawnEventHandler registered !!!!!!!!");

        // Регистрируем события жизненного цикла
        modEventBus.addListener(this::serverSetup);

        // ПРИМЕЧАНИЕ: Мы удалили регистрацию атрибутов здесь, так как она теперь в общем модуле
        // modEventBus.addListener(this::registerAttributes);

        // Регистрируем обработчики событий
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ServerSaveHandler.class);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ServerCommandRegistrationHandler.class);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(MobSpawnEventHandler.class);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ServerTickHandler.class);
    }

    private void serverSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Инициализация серверных компонентов
            System.out.println("ServerCustomMobsForge: Server components initialized");
        });
    }

    /* УДАЛЕНО: Теперь в общем модуле
    @SubscribeEvent
    public void registerAttributes(EntityAttributeCreationEvent event) {
        // Регистрируем атрибуты для нашего моба
        event.put(EntityRegistry.CUSTOM_MOB.get(), CustomMobEntity.createAttributes().build());
    }
    */

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Инициализация компонентов при запуске сервера
        System.out.println("ServerCustomMobsForge: Server starting");
    }
}