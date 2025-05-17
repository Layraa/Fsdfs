package com.custommobsforge.custommobsforge.client.render;

import com.custommobsforge.custommobsforge.client.cache.MobDataCache;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import mod.azure.azurelib.model.DefaultedEntityGeoModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Модель для кастомных мобов
 */
public class CustomMobModel extends DefaultedEntityGeoModel<CustomMobEntity> {

    public CustomMobModel() {
        super(new ResourceLocation("custommobsforge", "geo/default.geo.json"));
    }

    @Override
    public ResourceLocation getModelResource(CustomMobEntity entity) {
        // Получаем данные моба
        MobData mobData = getMobData(entity);

        if (mobData != null && mobData.getModelPath() != null) {
            return convertPathToResourceLocation(mobData.getModelPath());
        }

        // Возвращаем модель по умолчанию, если нет данных
        return new ResourceLocation("custommobsforge", "geo/default.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CustomMobEntity entity) {
        // Получаем данные моба
        MobData mobData = getMobData(entity);

        if (mobData != null && mobData.getTexturePath() != null) {
            return convertPathToResourceLocation(mobData.getTexturePath());
        }

        // Возвращаем текстуру по умолчанию, если нет данных
        return new ResourceLocation("custommobsforge", "textures/entity/default.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CustomMobEntity entity) {
        // Получаем данные моба
        MobData mobData = getMobData(entity);

        if (mobData != null && mobData.getAnimationFilePath() != null) {
            return convertPathToResourceLocation(mobData.getAnimationFilePath());
        }

        // Если нет определенного пути к анимации, пробуем создать из пути к модели
        if (mobData != null && mobData.getModelPath() != null) {
            String modelPath = mobData.getModelPath();
            String animationPath = modelPath.replace("geo", "animations").replace(".geo.json", ".animation.json");
            return convertPathToResourceLocation(animationPath);
        }

        // Возвращаем анимацию по умолчанию, если нет данных
        return new ResourceLocation("custommobsforge", "animations/default.animation.json");
    }

    @Override
    public RenderType getRenderType(CustomMobEntity entity, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    // Вспомогательные методы

    private MobData getMobData(CustomMobEntity entity) {
        // Сначала пробуем получить из самой сущности
        MobData mobData = entity.getMobData();

        // Если нет данных, пробуем получить из кэша по ID
        if (mobData == null && entity.getMobId() != null && !entity.getMobId().isEmpty()) {
            mobData = MobDataCache.getMobData(entity.getMobId());
        }

        return mobData;
    }

    private ResourceLocation convertPathToResourceLocation(String path) {
        // Преобразуем путь в ResourceLocation
        if (path.startsWith("assets/")) {
            path = path.substring(7);
        }

        String[] parts = path.split("/", 2);
        if (parts.length == 2) {
            return new ResourceLocation(parts[0], parts[1]);
        }

        // Если путь некорректный, возвращаем путь по умолчанию
        return new ResourceLocation("custommobsforge", "geo/default.geo.json");
    }
}