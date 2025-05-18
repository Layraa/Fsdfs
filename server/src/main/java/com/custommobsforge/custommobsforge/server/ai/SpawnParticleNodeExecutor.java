package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class SpawnParticleNodeExecutor implements NodeExecutor {
    // Сопоставление названий частиц и типов частиц
    // Используем ParticleOptions вместо SimpleParticleType для более общего использования
    private static final Map<String, ParticleOptions> PARTICLE_TYPES = new HashMap<>();

    static {
        PARTICLE_TYPES.put("smoke", ParticleTypes.SMOKE);
        PARTICLE_TYPES.put("flame", ParticleTypes.FLAME);
        PARTICLE_TYPES.put("heart", ParticleTypes.HEART);
        PARTICLE_TYPES.put("water", ParticleTypes.DRIPPING_WATER);
        PARTICLE_TYPES.put("lava", ParticleTypes.DRIPPING_LAVA);
        PARTICLE_TYPES.put("snow", ParticleTypes.SNOWFLAKE);
        PARTICLE_TYPES.put("explosion", ParticleTypes.EXPLOSION);
        PARTICLE_TYPES.put("portal", ParticleTypes.PORTAL);
        // Для частиц DUST нужно использовать DustParticleOptions с указанием цвета и размера
        PARTICLE_TYPES.put("redstone", new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 1.0F));
        PARTICLE_TYPES.put("slime", ParticleTypes.ITEM_SLIME);
        PARTICLE_TYPES.put("magic", ParticleTypes.ENCHANT);
    }

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры
        String particleType = node.getCustomParameterAsString("particleType", "smoke");
        double radius = node.getCustomParameterAsDouble("radius", 1.0);
        int count = (int) node.getCustomParameterAsDouble("count", 10);

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            return true; // На клиенте ничего не делаем
        }

        // Получаем тип частиц
        ParticleOptions particleOptions = PARTICLE_TYPES.getOrDefault(particleType.toLowerCase(),
                ParticleTypes.SMOKE);

        // Спавним частицы
        spawnParticles((ServerLevel) entity.level(), entity, particleOptions, radius, count);

        return true;
    }

    // Метод для спавна частиц
    private void spawnParticles(ServerLevel level, CustomMobEntity entity, ParticleOptions particleType, double radius, int count) {
        // Спавним частицы вокруг моба
        for (int i = 0; i < count; i++) {
            double offsetX = (Math.random() - 0.5) * 2 * radius;
            double offsetY = Math.random() * radius;
            double offsetZ = (Math.random() - 0.5) * 2 * radius;

            level.sendParticles(
                    particleType,
                    entity.getX() + offsetX,
                    entity.getY() + offsetY,
                    entity.getZ() + offsetZ,
                    1,  // количество частиц
                    0, 0, 0,  // скорость
                    0  // доп. данные
            );
        }
    }
}