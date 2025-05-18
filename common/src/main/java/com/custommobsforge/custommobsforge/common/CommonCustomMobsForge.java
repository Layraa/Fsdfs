package com.custommobsforge.custommobsforge.common;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

        // ДОБАВЛЕНО: Регистрируем обработчик атрибутов
        modEventBus.addListener(this::registerAttributes);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Инициализируем менеджер сети
        event.enqueueWork(NetworkManager::registerPackets);
    }

    // ДОБАВЛЕНО: Метод для регистрации атрибутов (раньше был только в серверном модуле)
    @SubscribeEvent
    public void registerAttributes(EntityAttributeCreationEvent event) {
        // Регистрируем атрибуты для нашего моба
        event.put(EntityRegistry.CUSTOM_MOB.get(), CustomMobEntity.createAttributes().build());
        System.out.println("Registered attributes for custom_mob entity in COMMON module");
    }
}