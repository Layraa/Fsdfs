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

        System.out.println("CustomMobRenderer: Created new renderer instance");
    }

    @Override
    public void render(CustomMobEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // Расширенное логирование для отладки
        if (entity != null) {
            ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            System.out.println("CustomMobRenderer: Rendering entity " + entity.getId() +
                    " of type " + (entityId != null ? entityId.toString() : "unknown") +
                    ", mobId: " + entity.getMobId() +
                    ", hasData: " + (entity.getMobData() != null ? "yes" : "no"));

            if (entity.getMobData() != null) {
                System.out.println("  -> model: " + entity.getMobData().getModelPath());
                System.out.println("  -> texture: " + entity.getMobData().getTexturePath());
                System.out.println("  -> animation: " + entity.getMobData().getAnimationFilePath());
            }
        }

        // Вызываем базовый метод рендеринга
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}