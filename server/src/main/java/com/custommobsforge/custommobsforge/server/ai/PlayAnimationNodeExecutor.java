package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.ai.NodeStatus;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.event.system.AnimationCompletedEvent;
import com.custommobsforge.custommobsforge.common.event.system.EventListener;
import com.custommobsforge.custommobsforge.common.event.system.EventSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public class PlayAnimationNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        LOGGER.info("PlayAnimationNodeExecutor: Executing node {} of type {} for entity {}",
                node.getId(), node.getType(), entity.getId());

        // Уникальный ключ для узла+сущности
        String nodeKey = entity.getId() + ":" + node.getId();

        // Получаем текущий статус узла из Blackboard
        NodeStatus currentStatus = executor.getBlackboard().getNodeStatus(node.getId());

        // Если узел уже успешно выполнен, сразу возвращаем успех
        if (currentStatus == NodeStatus.SUCCESS) {
            LOGGER.info("PlayAnimationNodeExecutor: Node {} already completed successfully, returning success", node.getId());
            return true;
        }

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

        // Проверяем, завершена ли анимация
        boolean animationCompleted = executor.getBlackboard().getValue(nodeKey + ":completed", false);

        // Если анимация уже завершена, переходим к следующему узлу
        if (animationCompleted) {
            LOGGER.info("PlayAnimationNodeExecutor: Animation '{}' already completed for node {}, moving to next node",
                    animationId, node.getId());

            // Очищаем состояние в Blackboard
            executor.getBlackboard().removeValue(nodeKey + ":started");
            executor.getBlackboard().removeValue(nodeKey + ":completed");
            executor.getBlackboard().removeValue(nodeKey + ":startTime");
            executor.getBlackboard().removeValue(nodeKey + ":listener_id");
            executor.getBlackboard().removeValue(nodeKey + ":idle_played");

            // Удаляем слушатель события, если он был зарегистрирован
            String listenerId = executor.getBlackboard().getValue(nodeKey + ":listener_id", (String)null);
            if (listenerId != null) {
                EventSystem.unregisterListenerById(listenerId);
                LOGGER.info("PlayAnimationNodeExecutor: Unregistered event listener {} for node {}", listenerId, node.getId());
            }

            // Сообщаем, что узел завершен успешно
            executor.completeNode(node, true);
            executor.setNodeNeedsMoreTime(false);

            // ВАЖНО: повторно активируем автоматические анимации
            entity.setDisableAutoAnimations(false);

            return true;
        }

        // Для зацикленных анимаций просто запускаем и завершаем узел
        if (loop) {
            boolean animationStarted = executor.getBlackboard().getValue(nodeKey + ":started", false);
            if (!animationStarted) {
                LOGGER.info("PlayAnimationNodeExecutor: Playing looped animation '{}' with speed {} and returning success",
                        animationId, speed);

                // Используем адаптер анимаций для воспроизведения
                entity.getAnimationAdapter().playAnimation(animationId, true, speed);
                executor.getBlackboard().setValue(nodeKey + ":started", true);

                // Для зацикленной анимации сразу отмечаем как завершенную
                executor.getBlackboard().setValue(nodeKey + ":completed", true);
            }

            // Помечаем узел как выполненный
            executor.completeNode(node, true);
            executor.setNodeNeedsMoreTime(false);

            // Включаем автоматические анимации
            entity.setDisableAutoAnimations(false);

            return true;
        }

        // Для незацикленных анимаций - более сложная логика
        boolean animationStarted = executor.getBlackboard().getValue(nodeKey + ":started", false);
        if (!animationStarted) {
            // Первое выполнение - запускаем анимацию
            LOGGER.info("PlayAnimationNodeExecutor: First execution for animation '{}', starting it...", animationId);

            // Создаем уникальный ID для слушателя
            String listenerId = UUID.randomUUID().toString();

            // Регистрируем слушатель события завершения анимации
            EventListener<AnimationCompletedEvent> listener = event -> {
                if (event.getEntity().getId() == entity.getId() &&
                        event.getAnimationId().equals(animationId)) {
                    LOGGER.info("PlayAnimationNodeExecutor: Received completion event for animation '{}' on entity {}",
                            animationId, entity.getId());

                    // ВАЖНО: Проверяем, не играли ли мы уже IDLE, чтобы избежать рекурсии
                    boolean idlePlayed = executor.getBlackboard().getValue(nodeKey + ":idle_played", false);
                    if (!idlePlayed) {
                        // Отмечаем анимацию как завершенную
                        executor.getBlackboard().setValue(nodeKey + ":completed", true);

                        // Отмечаем, что IDLE уже проигран
                        executor.getBlackboard().setValue(nodeKey + ":idle_played", true);

                        // ВАЖНО: повторно активируем автоматические анимации при завершении анимации
                        entity.setDisableAutoAnimations(false);

                        // ВАЖНО: используем непосредственно AnimationAdapter вместо playAnimation
                        // чтобы не вызвать новое событие завершения
                        entity.getAnimationAdapter().playAnimation("IDLE", true, 1.0f);
                    }
                }
            };

            EventSystem.registerListener(AnimationCompletedEvent.class, listener, listenerId);

            // Сохраняем ID слушателя в Blackboard для последующего удаления
            executor.getBlackboard().setValue(nodeKey + ":listener_id", listenerId);

            // Запускаем анимацию через адаптер анимаций
            entity.getAnimationAdapter().playAnimation(animationId, false, speed);

            // Отмечаем, что анимация запущена и записываем время запуска
            executor.getBlackboard().setValue(nodeKey + ":started", true);
            executor.getBlackboard().setValue(nodeKey + ":startTime", System.currentTimeMillis());
            executor.getBlackboard().setValue(nodeKey + ":idle_played", false);

            // Отмечаем узел как выполняющийся (RUNNING)
            executor.setNodeNeedsMoreTime(true);

            return true;
        } else {
            // Повторное выполнение - проверяем статус анимации
            long currentTime = System.currentTimeMillis();
            long startTime = executor.getBlackboard().getValue(nodeKey + ":startTime", 0L);
            long duration = entity.estimateAnimationDuration(animationId);

            LOGGER.info("PlayAnimationNodeExecutor: Checking animation '{}' status: elapsed {} ms of {} ms estimated",
                    animationId, (currentTime - startTime), duration);

            // Если прошло достаточно времени, принудительно завершаем анимацию (на случай пропуска события завершения)
            if (currentTime - startTime > duration + 100) { // Добавляем 100мс буфера
                LOGGER.info("PlayAnimationNodeExecutor: Animation '{}' should be completed by now, forcing completion",
                        animationId);

                // Проверяем, не завершена ли анимация уже
                if (!executor.getBlackboard().getValue(nodeKey + ":completed", false)) {
                    // Принудительно отмечаем анимацию как завершенную
                    executor.getBlackboard().setValue(nodeKey + ":completed", true);
                    executor.getBlackboard().setValue(nodeKey + ":idle_played", true);

                    // Удаляем слушатель события
                    String listenerId = executor.getBlackboard().getValue(nodeKey + ":listener_id", (String)null);
                    if (listenerId != null) {
                        EventSystem.unregisterListenerById(listenerId);
                    }

                    // ВАЖНО: используем непосредственно AnimationAdapter вместо playAnimation
                    entity.getAnimationAdapter().playAnimation("IDLE", true, 1.0f);
                }

                // Включаем автоматические анимации
                entity.setDisableAutoAnimations(false);

                // Помечаем узел как успешно выполненный
                executor.completeNode(node, true);
                executor.setNodeNeedsMoreTime(false);
                return true;
            }

            // Анимация все еще выполняется
            return true;
        }
    }

    // Вспомогательные методы без изменений
    private String getAnimationId(BehaviorNode node) {
        // Поиск ID анимации из разных источников
        String animationId = null;

        // 1. Из customParameters
        animationId = node.getCustomParameterAsString("animation", null);
        if (animationId != null && !animationId.isEmpty()) {
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
                    return animationId;
                }
            }
        }

        // 3. Из поля animationId
        if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
            animationId = node.getAnimationId();
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
                    }
                } catch (Exception e) {
                    LOGGER.error("PlayAnimationNodeExecutor: Error parsing speed parameter: {}", e.getMessage());
                }
            }

            if (speed <= 0.001f) {
                speed = 1.0f;
            }
        }

        return speed;
    }
}