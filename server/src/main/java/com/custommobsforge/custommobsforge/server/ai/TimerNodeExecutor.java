package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.ai.NodeStatus;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class TimerNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

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

        // Проверим status узла
        NodeStatus currentStatus = executor.getNodeStatus(node);
        if (currentStatus == NodeStatus.SUCCESS) {
            LOGGER.info("TimerNodeExecutor: Timer for node {} already completed, returning success", node.getId());
            return true;
        }

        // Проверим raw параметр для более надежного парсинга
        if (node.getParameter() != null && !node.getParameter().isEmpty()) {
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
                        repeat = parsedRepeat;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("TimerNodeExecutor: Error parsing repeat: {}", e.getMessage());
            }
        }

        // Идентификатор таймера (уникальный для каждого узла и каждой сущности)
        String timerId = entity.getId() + ":" + node.getId();

        // Проверяем, завершен ли таймер уже
        boolean timerCompleted = executor.getBlackboard().getValue(timerId + ":completed", false);
        if (timerCompleted && !repeat) {
            LOGGER.info("TimerNodeExecutor: Timer for {} already completed, returning success", timerId);

            // Сбрасываем статус для следующего запуска
            executor.getBlackboard().removeValue(timerId + ":completed");
            executor.getBlackboard().removeValue(timerId + ":started");
            executor.getBlackboard().removeValue(timerId + ":startTime");
            executor.getBlackboard().removeValue(timerId + ":duration");

            // Отмечаем узел как выполненный
            executor.completeNode(node, true);
            executor.setNodeNeedsMoreTime(false);

            return true;
        }

        // Текущее время в миллисекундах
        long currentTime = System.currentTimeMillis();

        // Если таймер не запущен, запускаем его
        if (!executor.getBlackboard().getValue(timerId + ":started", false)) {
            LOGGER.info("TimerNodeExecutor: Starting timer for {}", timerId);
            executor.getBlackboard().setValue(timerId + ":started", true);
            executor.getBlackboard().setValue(timerId + ":startTime", currentTime);

            // Сохраняем продолжительность в миллисекундах
            long durationMs = Math.max(100, (long) (duration * 1000));
            executor.getBlackboard().setValue(timerId + ":duration", durationMs);

            // Сбрасываем статус завершения
            executor.getBlackboard().removeValue(timerId + ":completed");

            // Отмечаем узел как выполняющийся (RUNNING)
            executor.setNodeNeedsMoreTime(true);

            // Логируем запуск таймера
            executor.logNodeExecution("TimerNode", node.getId(),
                    "timer started, will run for " + durationMs + "ms", false);
            return true; // Возвращаем true, чтобы последовательность продолжалась
        }

        // Проверяем, истекло ли время
        long startTime = executor.getBlackboard().getValue(timerId + ":startTime", 0L);
        long durationMs = executor.getBlackboard().getValue(timerId + ":duration", Math.max(100, (long)(duration * 1000)));
        long elapsed = currentTime - startTime;

        LOGGER.info("TimerNodeExecutor: Checking timer for {}, elapsed {} ms out of {}",
                timerId, elapsed, durationMs);

        if (elapsed >= durationMs) {
            // Если таймер настроен на повторение, перезапускаем его
            if (repeat) {
                LOGGER.info("TimerNodeExecutor: Timer expired, restarting (repeat=true)");
                executor.getBlackboard().setValue(timerId + ":startTime", currentTime);
                // Все еще нужно время
                executor.setNodeNeedsMoreTime(true);
            } else {
                // Иначе, удаляем таймер
                LOGGER.info("TimerNodeExecutor: Timer expired, not restarting (repeat=false)");
                executor.getBlackboard().removeValue(timerId + ":started");
                executor.getBlackboard().removeValue(timerId + ":startTime");
                executor.getBlackboard().removeValue(timerId + ":duration");

                // Отмечаем таймер как завершенный
                executor.getBlackboard().setValue(timerId + ":completed", true);

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

            // Логируем завершение таймера
            executor.logNodeExecution("TimerNode", node.getId(), "completed", false);
            return true;
        } else {
            // Проверяем, не осталось ли слишком мало времени
            // Если таймер почти завершен (осталось меньше интервала выполнения), форсируем завершение
            // Это поможет избежать проблем с застреванием узлов
            if (durationMs - elapsed < 250) { // если осталось меньше 250мс
                LOGGER.info("TimerNodeExecutor: Timer almost expired (remaining {}ms), forcing completion", durationMs - elapsed);

                // Имитируем истечение таймера
                if (repeat) {
                    LOGGER.info("TimerNodeExecutor: Timer expired, restarting (repeat=true)");
                    executor.getBlackboard().setValue(timerId + ":startTime", currentTime);
                    executor.setNodeNeedsMoreTime(true);
                } else {
                    LOGGER.info("TimerNodeExecutor: Timer expired, not restarting (repeat=false)");
                    executor.getBlackboard().removeValue(timerId + ":started");
                    executor.getBlackboard().removeValue(timerId + ":startTime");
                    executor.getBlackboard().removeValue(timerId + ":duration");
                    executor.getBlackboard().setValue(timerId + ":completed", true);
                    executor.setNodeNeedsMoreTime(false);
                    executor.completeNode(node, true);
                }

                return true;
            }

            // Таймер еще работает
            LOGGER.info("TimerNodeExecutor: Timer not yet expired, continuing");
            executor.setNodeNeedsMoreTime(true);
            return true; // Важно! Возвращаем true, чтобы последовательность продолжалась
        }
    }
}