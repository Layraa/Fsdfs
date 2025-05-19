package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TimerNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    // Используем ConcurrentHashMap для многопоточной безопасности
    private static final Map<String, Long> timerStartTimes = new ConcurrentHashMap<>();
    private static final Map<String, Long> timerDurations = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> timerCompleted = new ConcurrentHashMap<>();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры таймера с проверкой ошибок
        double duration = 1.0; // Значение по умолчанию
        boolean repeat = false;

        try {
            duration = node.getCustomParameterAsDouble("duration", 1.0);
            repeat = node.getCustomParameterAsBoolean("repeat", false);
        } catch (Exception e) {
            LOGGER.error("TimerNodeExecutor: Error parsing parameters: {}", e.getMessage());
        }

        // Выведем параметры для отладки
        executor.logNodeExecution("TimerNode", node.getId(),
                "duration=" + duration + ", repeat=" + repeat, true);

        // Проверим raw параметр для более надежного парсинга
        if (node.getParameter() != null && !node.getParameter().isEmpty()) {
            LOGGER.info("TimerNodeExecutor: Raw parameter = {}", node.getParameter());

            // Тщательное извлечение параметра duration
            try {
                String param = node.getParameter();
                if (param.contains("duration=")) {
                    String[] parts = param.split("duration=", 2);
                    if (parts.length > 1) {
                        String value = parts[1];
                        if (value.contains(";")) {
                            value = value.split(";", 2)[0];
                        }
                        double parsedDuration = Double.parseDouble(value);
                        LOGGER.info("TimerNodeExecutor: Parsed duration = {} from parameter", parsedDuration);
                        duration = parsedDuration;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("TimerNodeExecutor: Error parsing duration: {}", e.getMessage());
            }

            // Тщательное извлечение параметра repeat
            try {
                String param = node.getParameter();
                if (param.contains("repeat=")) {
                    String[] parts = param.split("repeat=", 2);
                    if (parts.length > 1) {
                        String value = parts[1];
                        if (value.contains(";")) {
                            value = value.split(";", 2)[0];
                        }
                        boolean parsedRepeat = Boolean.parseBoolean(value);
                        LOGGER.info("TimerNodeExecutor: Parsed repeat = {} from parameter", parsedRepeat);
                        repeat = parsedRepeat;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("TimerNodeExecutor: Error parsing repeat: {}", e.getMessage());
            }
        }

        // Идентификатор таймера (уникальный для каждого узла и каждой сущности)
        String timerId = entity.getId() + ":" + node.getId();

        // Проверяем, завершился ли таймер ранее
        if (timerCompleted.getOrDefault(timerId, false) && !repeat) {
            LOGGER.info("TimerNodeExecutor: Timer for {} already completed, returning success", timerId);

            // Сбрасываем статус для следующего запуска
            timerCompleted.remove(timerId);

            // Сообщаем, что узел завершил выполнение
            executor.completeNode(node, true);
            executor.setNodeNeedsMoreTime(false);

            return true;
        }

        // Текущее время в миллисекундах
        long currentTime = System.currentTimeMillis();

        // Если таймер не запущен, запускаем его
        if (!timerStartTimes.containsKey(timerId)) {
            LOGGER.info("TimerNodeExecutor: Starting timer for {}", timerId);
            timerStartTimes.put(timerId, currentTime);

            // Сохраняем продолжительность в миллисекундах
            long durationMs = Math.max(100, (long) (duration * 1000));
            timerDurations.put(timerId, durationMs);

            // Сбрасываем статус завершения
            timerCompleted.remove(timerId);

            // Сообщаем исполнителю, что нужно больше времени
            executor.setNodeNeedsMoreTime(true);

            executor.logNodeExecution("TimerNode", node.getId(),
                    "timer started, will run for " + durationMs + "ms", false);
            return true; // Возвращаем true, чтобы последовательность продолжалась
        }

        // Проверяем, истекло ли время
        long startTime = timerStartTimes.get(timerId);
        long durationMs = timerDurations.getOrDefault(timerId, Math.max(100, (long)(duration * 1000)));
        long elapsed = currentTime - startTime;

        LOGGER.info("TimerNodeExecutor: Checking timer for {}, elapsed {} ms out of {}",
                timerId, elapsed, durationMs);

        if (elapsed >= durationMs) {
            // Если таймер настроен на повторение, перезапускаем его
            if (repeat) {
                LOGGER.info("TimerNodeExecutor: Timer expired, restarting (repeat=true)");
                timerStartTimes.put(timerId, currentTime);
                // Все еще нужно время
                executor.setNodeNeedsMoreTime(true);
            } else {
                // Иначе, удаляем таймер
                LOGGER.info("TimerNodeExecutor: Timer expired, not restarting (repeat=false)");
                timerStartTimes.remove(timerId);
                timerDurations.remove(timerId);

                // Отмечаем таймер как завершенный
                timerCompleted.put(timerId, true);

                // Больше не нужно времени
                executor.setNodeNeedsMoreTime(false);

                // Явно сообщаем об успешном завершении узла
                executor.completeNode(node, true);
            }

            // Выполняем дочерние узлы, если есть
            List<BehaviorNode> children = executor.getChildNodes(node);
            if (!children.isEmpty()) {
                LOGGER.info("TimerNodeExecutor: Executing child nodes after timer expired");
                for (BehaviorNode child : children) {
                    LOGGER.info("TimerNodeExecutor: Executing child node {} after timer expired", child.getId());
                    executor.executeNode(child);
                }
            }

            executor.logNodeExecution("TimerNode", node.getId(), "completed", false);
            return true;
        }

        // Таймер еще работает
        LOGGER.info("TimerNodeExecutor: Timer not yet expired, continuing");
        executor.setNodeNeedsMoreTime(true);
        return true; // Важно! Возвращаем true, чтобы последовательность продолжалась
    }

    /**
     * Очищает таймеры для указанной сущности
     */
    public static void cleanup(int entityId) {
        // Удаляем все записи для этой сущности
        String entityPrefix = entityId + ":";

        // Очищаем карты, удаляя записи для этой сущности
        for (String key : timerStartTimes.keySet().toArray(new String[0])) {
            if (key.startsWith(entityPrefix)) {
                timerStartTimes.remove(key);
            }
        }

        for (String key : timerDurations.keySet().toArray(new String[0])) {
            if (key.startsWith(entityPrefix)) {
                timerDurations.remove(key);
            }
        }

        for (String key : timerCompleted.keySet().toArray(new String[0])) {
            if (key.startsWith(entityPrefix)) {
                timerCompleted.remove(key);
            }
        }

        LOGGER.info("TimerNodeExecutor: Cleaned up resources for entity {}", entityId);
    }
}