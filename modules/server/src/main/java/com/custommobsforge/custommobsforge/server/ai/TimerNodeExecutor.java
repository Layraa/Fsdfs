package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.HashMap;
import java.util.Map;

public class TimerNodeExecutor implements NodeExecutor {
    // Кэш для отслеживания времени активации узлов
    private static final Map<String, Long> timerStartTimes = new HashMap<>();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры таймера
        double duration = node.getCustomParameterAsDouble("duration", 1.0);
        boolean repeat = node.getCustomParameterAsBoolean("repeat", false);

        // Идентификатор таймера (уникальный для каждого узла и каждой сущности)
        String timerId = entity.getId() + ":" + node.getId();

        // Текущее время
        long currentTime = entity.level().getGameTime();

        // Если таймер не запущен, запускаем его
        if (!timerStartTimes.containsKey(timerId)) {
            timerStartTimes.put(timerId, currentTime);
            return false; // Сразу возвращаем неудачу, чтобы дать таймеру время работать
        }

        // Проверяем, истекло ли время
        long startTime = timerStartTimes.get(timerId);
        long elapsed = currentTime - startTime;
        long durationTicks = (long) (duration * 20); // Секунды в тики

        if (elapsed >= durationTicks) {
            // Если таймер настроен на повторение, перезапускаем его
            if (repeat) {
                timerStartTimes.put(timerId, currentTime);
            } else {
                // Иначе, удаляем таймер
                timerStartTimes.remove(timerId);
            }

            return true; // Таймер истек, возвращаем успех
        }

        return false; // Таймер еще работает
    }
}