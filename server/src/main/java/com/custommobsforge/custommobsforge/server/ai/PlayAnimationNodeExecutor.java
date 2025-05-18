package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayAnimationNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    // Карта для узлов и их анимаций
    private static final Map<String, String> nodeAnimations = new HashMap<>();

    // Карта для ID коллбэков
    private static final Map<String, String> callbackIds = new HashMap<>();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        LOGGER.info("PlayAnimationNodeExecutor: Executing node {} of type {} for entity {}",
                node.getId(), node.getType(), entity.getId());

        // Логирование параметров узла
        logNodeParameters(node);

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

        // Уникальный ключ для узла+сущности
        String nodeKey = entity.getId() + ":" + node.getId();

        // Сохраняем анимацию для этого узла
        nodeAnimations.put(nodeKey, animationId);


        // Проверяем, зациклена ли анимация
            if (loop) {
                // Зацикленные анимации просто запускаем и сразу возвращаем успех
                LOGGER.info("PlayAnimationNodeExecutor: Playing looped animation '{}' and returning success", animationId);
                entity.playAnimationDirect(animationId, true, speed);
                executor.setNodeNeedsMoreTime(false);
                return true;
            }

            // Проверяем, запускали ли мы уже эту анимацию для этого узла
            if (!callbackIds.containsKey(nodeKey)) {
                // Первый запуск - регистрируем коллбэк и запускаем анимацию
                String callbackId = UUID.randomUUID().toString();
                callbackIds.put(nodeKey, callbackId);

                // Регистрируем коллбэк для отслеживания завершения анимации
                registerAnimationCallback(entity, callbackId, animationId);

                // Запускаем анимацию
                LOGGER.info("PlayAnimationNodeExecutor: First execution, playing animation '{}'", animationId);

                // НОВОЕ: задержка перед запуском новой анимации
                try {
                    Thread.sleep(50); // 50мс задержка, чтобы AzureLib успел обработать предыдущие анимации
                } catch (InterruptedException e) {
                    // Игнорируем прерывание
                }

                entity.playAnimationDirect(animationId, false, speed);

                // Сообщаем, что нам нужно больше времени
                executor.setNodeNeedsMoreTime(true);
                return true;
            } else {
                // Последующие вызовы - проверяем, завершилась ли анимация
                if (entity.hasAnimationCompleted(animationId)) {
                    // Анимация завершилась - очищаем данные и двигаемся дальше
                    LOGGER.info("PlayAnimationNodeExecutor: Animation '{}' has completed", animationId);

                    // Удаляем коллбэк
                    String callbackId = callbackIds.remove(nodeKey);
                    entity.removeAnimationCallback(callbackId);

                    // Удаляем запись об анимации
                    nodeAnimations.remove(nodeKey);

                    // Сообщаем, что нам больше не нужно времени
                    executor.setNodeNeedsMoreTime(false);
                    return true;
                } else {
                    // Анимация еще не завершилась - продолжаем ждать
                    LOGGER.info("PlayAnimationNodeExecutor: Animation '{}' still in progress", animationId);

                    // Проверяем, прошло ли достаточно времени с начала анимации
                    long startTime = entity.getAnimationStartTime(animationId);
                    long currentTime = System.currentTimeMillis();
                    long duration = entity.estimateAnimationDuration(animationId);

                    if (currentTime - startTime > duration * 1.5) {
                        // Анимация, вероятно, зависла - принудительно завершаем ее
                        LOGGER.warn("PlayAnimationNodeExecutor: Animation '{}' seems to be stuck, forcing completion", animationId);
                        entity.forceCompleteAnimation(animationId);

                        // Удаляем коллбэк
                        String callbackId = callbackIds.remove(nodeKey);
                        entity.removeAnimationCallback(callbackId);

                        // Сообщаем, что нам больше не нужно времени
                        executor.setNodeNeedsMoreTime(false);
                        return true;
                    }

                    executor.setNodeNeedsMoreTime(true);
                    return true;
                }
            }
        }

    // Вспомогательные методы

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

    private void registerAnimationCallback(CustomMobEntity entity, String callbackId, String animationId) {
        // Регистрируем коллбэк для уведомления о завершении анимации
        final String finalAnimationId = animationId;

        entity.registerAnimationCallback(callbackId, completedAnimId -> {
            if (completedAnimId.equals(finalAnimationId)) {
                LOGGER.info("PlayAnimationNodeExecutor: Animation callback triggered for '{}'", finalAnimationId);
            }
        });

        LOGGER.info("PlayAnimationNodeExecutor: Registered callback {} for animation '{}'", callbackId, animationId);
    }
}