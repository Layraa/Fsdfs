package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

public class PlaySoundNodeExecutor implements NodeExecutor {
    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры
        String soundId = node.getCustomParameterAsString("soundId", "entity.generic.hurt");
        double volume = node.getCustomParameterAsDouble("volume", 1.0);
        double pitch = node.getCustomParameterAsDouble("pitch", 1.0);
        double radius = node.getCustomParameterAsDouble("radius", 16.0);

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            return true; // На клиенте ничего не делаем
        }

        // Получаем звуковое событие
        SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));

        // Если звук не найден, используем звук по умолчанию
        if (soundEvent == null) {
            soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.hurt"));
        }

        // Воспроизводим звук
        if (soundEvent != null) {
            entity.level().playSound(
                    null, // Игрок (null означает всем в радиусе)
                    entity.getX(), entity.getY(), entity.getZ(), // Позиция
                    soundEvent, // Звук
                    SoundSource.NEUTRAL, // Источник звука
                    (float) volume, // Громкость
                    (float) pitch // Высота
            );
        }

        return true;
    }
}