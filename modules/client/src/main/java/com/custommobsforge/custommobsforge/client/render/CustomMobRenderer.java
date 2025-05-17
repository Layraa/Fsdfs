package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import mod.azure.azurelib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Рендерер для кастомных мобов
 */
public class CustomMobRenderer extends GeoEntityRenderer<CustomMobEntity> {

    public CustomMobRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new CustomMobModel());
    }

    @Override
    public void render(CustomMobEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // Можно добавить специальные эффекты рендеринга

        // Вызываем базовый метод рендеринга
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}