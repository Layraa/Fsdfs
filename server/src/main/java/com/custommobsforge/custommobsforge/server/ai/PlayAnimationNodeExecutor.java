package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.event.system.AnimationCompletedEvent;
import com.custommobsforge.custommobsforge.common.event.system.EventListener;
import com.custommobsforge.custommobsforge.common.event.system.EventSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayAnimationNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    // Карта для отслеживания состояния анимации для каждого узла и сущности
    private static final Map<String, Boolean> animationStarted = new ConcurrentHashMap<>();
    private static final Map<String, Long> animationStartTimes = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> animationCompleted = new ConcurrentHashMap<>();

    // Карта для хранения слушателей событий
    private static final Map<String, EventListener<AnimationCompletedEvent>> eventListeners = new ConcurrentHashMap<>();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        LOGGER.info("PlayAnimationNodeExecutor: Executing node {} of type {} for entity {}",
                node.getId(), node.getType(), entity.getId());

        // Уникальный ключ для узла+сущности
        String nodeKey = entity.getId() + ":" + node.getId();

        // Получаем ID анимации
        String animationId = getAnimationId(node);
        if (animationId == null || animationId.isEmpty()) {
            LOGGER.error("PlayAnimationNodeExecutor: No animation ID found for node {}", node.getId());
            executor.setNodeNeedsMoreTime(false);
            return false;
        }

        // Получаем настройки анимации
        boolean loop = getLoopParameter(node);
        float speed = getSpeedParameter(node);

        // Отключаем автоматические анимации при выполнении этого узла
        entity.setDisableAutoAnimations(true);

        // Если анимация уже завершена, переходим к следующему узлу
        if (animationCompleted.getOrDefault(nodeKey, false)) {
            LOGGER.info("PlayAnimationNodeExecutor: Animation '{}' already completed for node {}, moving to next node",
                    animationId, node.getId());

            // Очищаем состояние
            animationStarted.remove(nodeKey);
            animationStartTimes.remove(nodeKey);
            animationCompleted.remove(nodeKey);

            // Удаляем слушатель событий если был зарегистрирован
            EventListener<AnimationCompletedEvent> listener = eventListeners.remove(nodeKey);
            if (listener != null) {
                EventSystem.unregisterListener(AnimationCompletedEvent.class, listener);
                LOGGER.info("PlayAnimationNodeExecutor: Unregistered event listener for {}", nodeKey);
            }

            // Сообщаем, что узел завершен успешно
            executor.setNodeNeedsMoreTime(false);

            // ВАЖНО: повторно активируем автоматические анимации
            entity.setDisableAutoAnimations(false);

            return true;
        }

        // Для зацикленных анимаций просто запускаем и завершаем узел
        if (loop) {
            if (!animationStarted.getOrDefault(nodeKey, false)) {
                LOGGER.info("PlayAnimationNodeExecutor: Playing looped animation '{}' with speed {} and returning success",
                        animationId, speed);
                entity.playAnimationDirect(animationId, true, speed);
                animationStarted.put(nodeKey, true);

                // Для зацикленной анимации сразу отмечаем как завершенную
                animationCompleted.put(nodeKey, true);
            }

            executor.setNodeNeedsMoreTime(false);

            // ВАЖНО: повторно активируем автоматические анимации
            entity.setDisableAutoAnimations(false);

            return true;
        }

        // Для незацикленных анимаций - более сложная логика
        if (!animationStarted.getOrDefault(nodeKey, false)) {
            // Первое выполнение - запускаем анимацию
            LOGGER.info("PlayAnimationNodeExecutor: First execution for animation '{}', starting it...", animationId);

            // Регистрируем слушатель события завершения анимации
            EventListener<AnimationCompletedEvent> listener = event -> {
                if (event.getEntity().getId() == entity.getId() &&
                        event.getAnimationId().equals(animationId)) {
                    LOGGER.info("PlayAnimationNodeExecutor: Received completion event for animation '{}' on entity {}",
                            animationId, entity.getId());
                    animationCompleted.put(nodeKey, true);

                    // ВАЖНО: повторно активируем автоматические анимации при завершении анимации
                    entity.setDisableAutoAnimations(false);
                }
            };

            EventSystem.registerListener(AnimationCompletedEvent.class, listener);
            eventListeners.put(nodeKey, listener);

            // Запускаем анимацию
            entity.playAnimationDirect(animationId, false, speed);

            // Отмечаем, что анимация запущена и записываем время запуска
            animationStarted.put(nodeKey, true);
            animationStartTimes.put(nodeKey, System.currentTimeMillis());

            // Сообщаем, что узел нуждается в дополнительном времени
            executor.setNodeNeedsMoreTime(true);
            return true;
        } else {
            // Повторное выполнение - проверяем статус анимации
            long currentTime = System.currentTimeMillis();
            long startTime = animationStartTimes.getOrDefault(nodeKey, 0L);
            long duration = entity.estimateAnimationDuration(animationId);

            LOGGER.info("PlayAnimationNodeExecutor: Checking animation '{}' status: elapsed {} ms of {} ms estimated",
                    animationId, (currentTime - startTime), duration);

            // Если прошло достаточно времени, принудительно завершаем анимацию
            if (currentTime - startTime > duration) {
                LOGGER.info("PlayAnimationNodeExecutor: Animation '{}' should be completed by now, forcing completion",
                        animationId);

                // Проверяем, не завершена ли анимация уже
                if (!animationCompleted.getOrDefault(nodeKey, false)) {
                    // Принудительно генерируем событие завершения
                    EventSystem.fireEvent(new AnimationCompletedEvent(animationId, entity));
                }

                // Принудительно отмечаем анимацию как завершенную
                animationCompleted.put(nodeKey, true);

                // ВАЖНО: повторно активируем автоматические анимации
                entity.setDisableAutoAnimations(false);

                // Переходим к следующему узлу немедленно
                executor.setNodeNeedsMoreTime(false);
                return true;
            }

            // Анимация все еще выполняется
            executor.setNodeNeedsMoreTime(true);
            return true;
        }
    }

    // Остальные вспомогательные методы остаются без изменений
    private void logNodeParameters(BehaviorNode node) {
        LOGGER.info("PlayAnimationNodeExecutor: Node parameters:");
        LOGGER.info("  - description: {}", node.getDescription());
        LOGGER.info("  - parameter: {}", node.getParameter());
        LOGGER.info("  - animationId: {}", node.getAnimationId());
        LOGGER.info("  - animationSpeed: {}", node.getAnimationSpeed());
        LOGGER.info("  - loopAnimation: {}", node.isLoopAnimation());

        if (node.getCustomParameters() != null) {
            LOGGER.info("  - Custom parameters:");
            for (Map.Entry<String, Object> entry : node.getCustomParameters().entrySet()) {
                LOGGER.info("    * {} = {}", entry.getKey(), entry.getValue());
            }
        }
    }

    private String getAnimationId(BehaviorNode node) {
        // Поиск ID анимации из разных источников
        String animationId = null;

        // 1. Из customParameters
        animationId = node.getCustomParameterAsString("animation", null);
        if (animationId != null && !animationId.isEmpty()) {
            LOGGER.info("PlayAnimationNodeExecutor: Using animation from customParameter: {}", animationId);
            return animationId;
        }

        // 2. Из параметра узла
        if (node.getParameter() != null && !node.getParameter().isEmpty()) {
            String param = node.getParameter();
            if (param.contains("animation=")) {
                String[] parts = param.split("animation=", 2);
                if (parts.length > 1) {
                    String part = parts[1];
                    if (part.contains(";")) {
                        animationId = part.split(";", 2)[0];
                    } else {
                        animationId = part;
                    }
                    LOGGER.info("PlayAnimationNodeExecutor: Extracted animation value from parameter: {}", animationId);
                    return animationId;
                }
            }
        }

        // 3. Из поля animationId
        if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
            animationId = node.getAnimationId();
            LOGGER.info("PlayAnimationNodeExecutor: Using value from animationId field: {}", animationId);
            return animationId;
        }

        return null;
    }

    private boolean getLoopParameter(BehaviorNode node) {
        boolean loop = node.isLoopAnimation();

        if (node.getParameter() != null && node.getParameter().contains("loop=")) {
            try {
                String param = node.getParameter();
                String[] parts = param.split("loop=", 2);
                if (parts.length > 1) {
                    String value = parts[1];
                    if (value.contains(";")) {
                        value = value.split(";", 2)[0];
                    }
                    loop = Boolean.parseBoolean(value);
                    LOGGER.info("PlayAnimationNodeExecutor: Extracted loop value from parameter: {}", loop);
                }
            } catch (Exception e) {
                LOGGER.error("PlayAnimationNodeExecutor: Error parsing loop parameter: {}", e.getMessage());
            }
        }

        return loop;
    }

    private float getSpeedParameter(BehaviorNode node) {
        float speed = (float) node.getAnimationSpeed();

        if (speed <= 0.001f) {
            if (node.getParameter() != null && node.getParameter().contains("speed=")) {
                try {
                    String param = node.getParameter();
                    String[] parts = param.split("speed=", 2);
                    if (parts.length > 1) {
                        String value = parts[1];
                        if (value.contains(";")) {
                            value = value.split(";", 2)[0];
                        }
                        speed = Float.parseFloat(value);
                        LOGGER.info("PlayAnimationNodeExecutor: Extracted speed value from parameter: {}", speed);
                    }
                } catch (Exception e) {
                    LOGGER.error("PlayAnimationNodeExecutor: Error parsing speed parameter: {}", e.getMessage());
                }
            }

            if (speed <= 0.001f) {
                speed = 1.0f;
                LOGGER.info("PlayAnimationNodeExecutor: Using default speed: {}", speed);
            }
        }

        return speed;
    }

    // Метод для очистки ресурсов узла
    public static void cleanup(int entityId) {
        // Удаляем все записи для этой сущности
        String entityPrefix = entityId + ":";

        // Очищаем карты, удаляя записи для этой сущности
        for (String key : animationStarted.keySet().toArray(new String[0])) {
            if (key.startsWith(entityPrefix)) {
                animationStarted.remove(key);
            }
        }

        for (String key : animationStartTimes.keySet().toArray(new String[0])) {
            if (key.startsWith(entityPrefix)) {
                animationStartTimes.remove(key);
            }
        }

        for (String key : animationCompleted.keySet().toArray(new String[0])) {
            if (key.startsWith(entityPrefix)) {
                animationCompleted.remove(key);
            }
        }

        // Удаляем слушателей событий
        for (String key : eventListeners.keySet().toArray(new String[0])) {
            if (key.startsWith(entityPrefix)) {
                EventListener<AnimationCompletedEvent> listener = eventListeners.remove(key);
                if (listener != null) {
                    EventSystem.unregisterListener(AnimationCompletedEvent.class, listener);
                }
            }
        }

        LOGGER.info("PlayAnimationNodeExecutor: Cleaned up resources for entity {}", entityId);
    }
}