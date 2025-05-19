package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.event.system.EventSystem;
import com.custommobsforge.custommobsforge.common.event.system.NodeCompletedEvent;
import com.custommobsforge.custommobsforge.common.event.system.NodeStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Адаптер для упрощения работы с узлами поведения и системой событий
 */
public class BehaviorNodeAdapter {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    private final BehaviorNode node;
    private final CustomMobEntity entity;
    private final BehaviorTreeExecutor executor;

    public BehaviorNodeAdapter(BehaviorNode node, CustomMobEntity entity, BehaviorTreeExecutor executor) {
        this.node = node;
        this.entity = entity;
        this.executor = executor;
    }

    /**
     * Уведомляет о начале выполнения узла
     */
    public void notifyStarted() {
        LOGGER.info("BehaviorNodeAdapter: Node {} started for entity {}", node.getId(), entity.getId());
        EventSystem.fireEvent(new NodeStartedEvent(node, entity));
    }

    /**
     * Уведомляет о завершении выполнения узла
     * @param success результат выполнения
     */
    public void notifyCompleted(boolean success) {
        LOGGER.info("BehaviorNodeAdapter: Node {} completed with result {} for entity {}",
                node.getId(), success, entity.getId());
        EventSystem.fireEvent(new NodeCompletedEvent(node, entity, success));
        executor.completeNode(node, success);
    }

    /**
     * Устанавливает флаг необходимости дополнительного времени
     * @param needsMoreTime требуется ли дополнительное время
     */
    public void setNeedsMoreTime(boolean needsMoreTime) {
        executor.setNodeNeedsMoreTime(node, needsMoreTime);
    }

    /**
     * Воспроизводит анимацию для моба
     * @param actionId идентификатор действия
     */
    public void playAnimation(String actionId) {
        entity.playAnimation(actionId);
    }

    /**
     * Напрямую воспроизводит анимацию для моба
     * @param animationId идентификатор анимации
     * @param loop зациклить анимацию
     * @param speed скорость воспроизведения
     */
    public void playAnimationDirect(String animationId, boolean loop, float speed) {
        entity.playAnimationDirect(animationId, loop, speed);
    }

    /**
     * Получает значение параметра как строку
     * @param key ключ параметра
     * @param defaultValue значение по умолчанию
     * @return значение параметра
     */
    public String getStringParameter(String key, String defaultValue) {
        return node.getCustomParameterAsString(key, defaultValue);
    }

    /**
     * Получает значение параметра как double
     * @param key ключ параметра
     * @param defaultValue значение по умолчанию
     * @return значение параметра
     */
    public double getDoubleParameter(String key, double defaultValue) {
        return node.getCustomParameterAsDouble(key, defaultValue);
    }

    /**
     * Получает значение параметра как boolean
     * @param key ключ параметра
     * @param defaultValue значение по умолчанию
     * @return значение параметра
     */
    public boolean getBooleanParameter(String key, boolean defaultValue) {
        return node.getCustomParameterAsBoolean(key, defaultValue);
    }

    /**
     * Получает моба, с которым связан узел
     * @return моб
     */
    public CustomMobEntity getEntity() {
        return entity;
    }

    /**
     * Получает узел поведения
     * @return узел
     */
    public BehaviorNode getNode() {
        return node;
    }

    /**
     * Получает исполнителя дерева поведения
     * @return исполнитель
     */
    public BehaviorTreeExecutor getExecutor() {
        return executor;
    }
}