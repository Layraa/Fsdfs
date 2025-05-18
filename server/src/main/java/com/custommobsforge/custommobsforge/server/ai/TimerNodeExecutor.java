package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TimerNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    // Кэш для отслеживания времени активации узлов
    private static final Map<String, Long> timerStartTimes = new HashMap<>();
    private static final Map<String, Long> timerDurations = new HashMap<>();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры таймера
        double duration = node.getCustomParameterAsDouble("duration", 1.0);
        boolean repeat = node.getCustomParameterAsBoolean("repeat", false);

        // Выведем параметры для отладки
        executor.logNodeExecution("TimerNode", node.getId(),
                "duration=" + duration + ", repeat=" + repeat, true);

        // Также проверим raw параметр
        if (node.getParameter() != null && !node.getParameter().isEmpty()) {
            LOGGER.info("TimerNodeExecutor: Raw parameter = {}", node.getParameter());

            // Попробуем извлечь параметры вручную
            String param = node.getParameter();
            if (param.contains("duration=")) {
                try {
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
                } catch (Exception e) {
                    LOGGER.error("TimerNodeExecutor: Error parsing duration: {}", e.getMessage());
                }
            }

            if (param.contains("repeat=")) {
                try {
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
                } catch (Exception e) {
                    LOGGER.error("TimerNodeExecutor: Error parsing repeat: {}", e.getMessage());
                }
            }
        }

        // Идентификатор таймера (уникальный для каждого узла и каждой сущности)
        String timerId = entity.getId() + ":" + node.getId();

        // Текущее время в миллисекундах (не тиках!)
        long currentTime = System.currentTimeMillis();

        // Если таймер не запущен, запускаем его
        if (!timerStartTimes.containsKey(timerId)) {
            LOGGER.info("TimerNodeExecutor: Starting timer for {}", timerId);
            timerStartTimes.put(timerId, currentTime);

            // Сохраняем продолжительность в миллисекундах
            long durationMs = (long) (duration * 1000);
            timerDurations.put(timerId, durationMs);

            // Сообщаем исполнителю, что нужно больше времени
            executor.setNodeNeedsMoreTime(true);

            executor.logNodeExecution("TimerNode", node.getId(),
                    "timer started, will run for " + durationMs + "ms", false);
            return true; // Возвращаем true, чтобы последовательность продолжалась
        }

        // Проверяем, истекло ли время
        long startTime = timerStartTimes.get(timerId);
        long durationMs = timerDurations.getOrDefault(timerId, (long)(duration * 1000));
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
                // Больше не нужно времени
                executor.setNodeNeedsMoreTime(false);
            }

            // Выполняем дочерние узлы, если есть
            List<BehaviorNode> children = executor.getChildNodes(node);
            if (!children.isEmpty()) {
                LOGGER.info("TimerNodeExecutor: Executing child node after timer expired");
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
}