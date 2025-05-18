package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerTickHandler {
    // Кэш для оптимизации поведения мобов
    private static final Map<Integer, Long> lastBehaviorUpdate = new HashMap<>();
    private static final int UPDATE_INTERVAL = 10; // Обновление каждые 10 тиков

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Пропускаем обработку, если это не фаза END
            return;
        }

        // Оптимизируем обработку поведения мобов
        updateMobBehaviors();
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END || !(event.level instanceof ServerLevel)) {
            // Пропускаем обработку, если это не фаза END или не серверный мир
            return;
        }

        ServerLevel level = (ServerLevel) event.level;

        // Обрабатываем события для всех кастомных мобов в мире
        // Это можно использовать для дополнительных системных эффектов
    }

    private static void updateMobBehaviors() {
        // Очищаем устаревшие записи
        List<Integer> entitiesToRemove = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<Integer, Long> entry : lastBehaviorUpdate.entrySet()) {
            // Если запись старше 5 минут, удаляем (моб, вероятно, больше не существует)
            if (currentTime - entry.getValue() > 300000) {
                entitiesToRemove.add(entry.getKey());
            }
        }

        for (Integer entityId : entitiesToRemove) {
            lastBehaviorUpdate.remove(entityId);
        }
    }
}