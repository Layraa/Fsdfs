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

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры таймера
        double duration = node.getCustomParameterAsDouble("duration", 1.0);
        boolean repeat = node.getCustomParameterAsBoolean("repeat", false);

        // Выведем параметры для отладки
        LOGGER.info("TimerNodeExecutor: Parameters - duration={}, repeat={} for node {}",
                duration, repeat, node.getId());

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

        // Текущее время
        long currentTime = entity.level().getGameTime();

        // Если таймер не запущен, запускаем его
        if (!timerStartTimes.containsKey(timerId)) {
            LOGGER.info("TimerNodeExecutor: Starting timer for {}", timerId);
            timerStartTimes.put(timerId, currentTime);
            return false; // Сразу возвращаем неудачу, чтобы дать таймеру время работать
        }

        // Проверяем, истекло ли время
        long startTime = timerStartTimes.get(timerId);
        long elapsed = currentTime - startTime;
        long durationTicks = (long) (duration * 20); // Секунды в тики

        LOGGER.info("TimerNodeExecutor: Checking timer for {}, elapsed {} ticks out of {}",
                timerId, elapsed, durationTicks);

        if (elapsed >= durationTicks) {
            // Если таймер настроен на повторение, перезапускаем его
            if (repeat) {
                LOGGER.info("TimerNodeExecutor: Timer expired, restarting (repeat=true)");
                timerStartTimes.put(timerId, currentTime);
            } else {
                // Иначе, удаляем таймер
                LOGGER.info("TimerNodeExecutor: Timer expired, not restarting (repeat=false)");
                timerStartTimes.remove(timerId);
            }

            // Выполняем дочерние узлы
            LOGGER.info("TimerNodeExecutor: Timer expired, executing child nodes");
            boolean anyChildSucceeded = false;
            List<BehaviorNode> children = executor.getChildNodes(node);
            if (children.isEmpty()) {
                LOGGER.warn("TimerNodeExecutor: No child nodes to execute!");
            }

            for (BehaviorNode child : children) {
                LOGGER.info("TimerNodeExecutor: Executing child node {} of type {}",
                        child.getId(), child.getType());
                if (executor.executeNode(child)) {
                    anyChildSucceeded = true;
                    LOGGER.info("TimerNodeExecutor: Child node executed successfully");
                } else {
                    LOGGER.warn("TimerNodeExecutor: Child node NOT executed successfully");
                }
            }

            return anyChildSucceeded;
        }

        LOGGER.info("TimerNodeExecutor: Timer not yet expired, returning false");
        return false; // Таймер еще работает
    }
}