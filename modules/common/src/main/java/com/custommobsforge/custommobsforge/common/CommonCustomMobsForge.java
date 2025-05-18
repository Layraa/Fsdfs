package com.custommobsforge.custommobsforge.common;

import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("custommobsforge_common")  // Изменено для соответствия mods.toml
public class CommonCustomMobsForge {
    public static final String MOD_ID = "custommobsforge_common";  // Также обновляем константу

    public CommonCustomMobsForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрируем типы сущностей
        EntityRegistry.ENTITY_TYPES.register(modEventBus);

        // Регистрируем обработчик общей настройки
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Инициализируем менеджер сети
        event.enqueueWork(NetworkManager::registerPackets);
    }
}