package com.custommobsforge.custommobsforge.client;

import com.custommobsforge.custommobsforge.client.cache.MobDataCache;
import com.custommobsforge.custommobsforge.client.commands.CommandRegistrationHandler;
import com.custommobsforge.custommobsforge.client.gui.MobCreatorGUI;
import com.custommobsforge.custommobsforge.client.handler.AnimationHandler;
import com.custommobsforge.custommobsforge.client.render.RenderersRegistrationHandler;
import com.custommobsforge.custommobsforge.common.CommonCustomMobsForge;
import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import javafx.application.Platform;

@Mod("custommobsforge_client")
@Mod.EventBusSubscriber(modid = CommonCustomMobsForge.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CustomMobsForgeClient {
    public static final String MOD_ID = "custommobsforge_client";
    private static boolean isGuiLaunched = false;

    public CustomMobsForgeClient() {
        // Регистрация обработчиков событий
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(CommandRegistrationHandler.class);
        MinecraftForge.EVENT_BUS.register(AnimationHandler.class);

        // Инициализация JavaFX
        try {
            Platform.startup(() -> {});
            Platform.setImplicitExit(false);
        } catch (Exception e) {
            System.err.println("Failed to initialize JavaFX: " + e.getMessage());
        }
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // Инициализация клиентского кэша
        MobDataCache.init();
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        RenderersRegistrationHandler.registerEntityRenderers(event);
    }
}