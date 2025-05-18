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

    // ИЗМЕНЕНО: Используем более правильный ID модели по умолчанию
    public CustomMobModel() {
        super(new ResourceLocation("custommobsforge", "geo/custom_mob.geo.json"));
        System.out.println("CustomMobModel: Created new model instance with default geo model");
    }

    @Override
    public ResourceLocation getModelResource(CustomMobEntity entity) {
        System.out.println("CustomMobModel: Getting model resource for entity " +
                (entity != null ? entity.getId() + ", mobId: " + entity.getMobId() : "null"));

        // Получаем данные моба
        MobData mobData = getMobData(entity);

        if (mobData != null && mobData.getModelPath() != null) {
            // ИЗМЕНЕНО: Улучшенный метод преобразования путей ресурсов
            ResourceLocation modelLocation = pathToResourceLocation(mobData.getModelPath());
            System.out.println("CustomMobModel: Using model: " + modelLocation);
            return modelLocation;
        }

        // Возвращаем модель по умолчанию, если нет данных
        System.out.println("CustomMobModel: Using default model");
        return new ResourceLocation("custommobsforge", "geo/custom_mob.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CustomMobEntity entity) {
        System.out.println("CustomMobModel: Getting texture resource for entity " +
                (entity != null ? entity.getId() : "null"));

        // Получаем данные моба
        MobData mobData = getMobData(entity);

        if (mobData != null && mobData.getTexturePath() != null) {
            // ИЗМЕНЕНО: Улучшенный метод преобразования путей ресурсов
            ResourceLocation textureLocation = pathToResourceLocation(mobData.getTexturePath());
            System.out.println("CustomMobModel: Using texture: " + textureLocation);
            return textureLocation;
        }

        // Возвращаем текстуру по умолчанию, если нет данных
        System.out.println("CustomMobModel: Using default texture");
        return new ResourceLocation("custommobsforge", "textures/entity/custom_mob.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CustomMobEntity entity) {
        System.out.println("CustomMobModel: Getting animation resource for entity " +
                (entity != null ? entity.getId() : "null"));

        // Получаем данные моба
        MobData mobData = getMobData(entity);

        if (mobData != null && mobData.getAnimationFilePath() != null) {
            // ИЗМЕНЕНО: Улучшенный метод преобразования путей ресурсов
            ResourceLocation animationLocation = pathToResourceLocation(mobData.getAnimationFilePath());
            System.out.println("CustomMobModel: Using animation file: " + animationLocation);
            return animationLocation;
        }

        // Если нет определенного пути к анимации, пробуем создать из пути к модели
        if (mobData != null && mobData.getModelPath() != null) {
            String modelPath = mobData.getModelPath();
            String animationPath = modelPath.replace("geo", "animations").replace(".geo.json", ".animation.json");
            ResourceLocation animationLocation = pathToResourceLocation(animationPath);
            System.out.println("CustomMobModel: Using derived animation file: " + animationLocation);
            return animationLocation;
        }

        // Возвращаем анимацию по умолчанию, если нет данных
        System.out.println("CustomMobModel: Using default animation");
        return new ResourceLocation("custommobsforge", "animations/custom_mob.animation.json");
    }

    @Override
    public RenderType getRenderType(CustomMobEntity entity, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    // Вспомогательные методы

    private MobData getMobData(CustomMobEntity entity) {
        if (entity == null) {
            System.out.println("CustomMobModel: Entity is null, cannot get mob data");
            return null;
        }

        // Сначала пробуем получить из самой сущности
        MobData mobData = entity.getMobData();

        // Если нет данных, пробуем получить из кэша по ID
        if (mobData == null && entity.getMobId() != null && !entity.getMobId().isEmpty()) {
            System.out.println("CustomMobModel: Getting mob data from cache for ID: " + entity.getMobId());
            mobData = MobDataCache.getMobData(entity.getMobId());
        } else if (mobData != null) {
            System.out.println("CustomMobModel: Using mob data from entity");
        }

        if (mobData == null) {
            System.out.println("CustomMobModel: No mob data found for entity " + entity.getId());
        }

        return mobData;
    }

    // ИЗМЕНЕНО: Полностью переписанный метод для корректного преобразования путей ресурсов
    private ResourceLocation pathToResourceLocation(String path) {
        System.out.println("CustomMobModel: Converting path to ResourceLocation: " + path);

        // Разделитель для элементов пути
        String[] parts;

        if (path.startsWith("assets/")) {
            // Формат "assets/modid/path/to/resource.ext"
            // Обрезаем префикс "assets/"
            path = path.substring(7);

            // Теперь разделяем на modid и путь
            int firstSlash = path.indexOf('/');
            if (firstSlash != -1) {
                String modid = path.substring(0, firstSlash);
                String resourcePath = path.substring(firstSlash + 1);

                System.out.println("CustomMobModel: Converted to ResourceLocation: " + modid + ":" + resourcePath);
                return new ResourceLocation(modid, resourcePath);
            }
        } else if (path.contains(":")) {
            // Формат "modid:path/to/resource.ext"
            parts = path.split(":", 2);
            if (parts.length == 2) {
                System.out.println("CustomMobModel: Using provided ResourceLocation: " + parts[0] + ":" + parts[1]);
                return new ResourceLocation(parts[0], parts[1]);
            }
        }

        // Если путь не соответствует ожидаемому формату, предполагаем, что это
        // относительный путь в пространстве имен нашего мода
        System.out.println("CustomMobModel: Falling back to default namespace with path: " + path);
        return new ResourceLocation("custommobsforge", path);
    }

    private boolean resourceExists(ResourceLocation resourceLocation) {
        try {
            // Проверяем, существует ли ресурс
            net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(resourceLocation).isPresent();
            return true;
        } catch (Exception e) {
            System.out.println("Resource not found: " + resourceLocation);
            return false;
        }
    }
}