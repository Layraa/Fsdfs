package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.common.registry.EntityRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Обработчик регистрации рендереров
 */
public class RenderersRegistrationHandler {

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Регистрируем рендерер для кастомных мобов
        event.registerEntityRenderer(EntityRegistry.CUSTOM_MOB.get(), CustomMobRenderer::new);
    }
}