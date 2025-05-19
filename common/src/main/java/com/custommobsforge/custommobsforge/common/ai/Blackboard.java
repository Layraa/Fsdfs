// com.custommobsforge.custommobsforge.common.ai.Blackboard
package com.custommobsforge.custommobsforge.common.ai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Глобальное хранилище данных для узлов дерева поведения
 */
public class Blackboard {
    private final Map<String, Object> data = new ConcurrentHashMap<>();
    private String executionId = UUID.randomUUID().toString();

    /**
     * Устанавливает статус узла
     * @param nodeId ID узла
     * @param status Статус узла
     */
    public void setNodeStatus(String nodeId, NodeStatus status) {
        data.put(executionId + ":" + nodeId + ":status", status);
    }

    /**
     * Получает статус узла
     * @param nodeId ID узла
     * @return Статус узла
     */
    public NodeStatus getNodeStatus(String nodeId) {
        return (NodeStatus) data.getOrDefault(
                executionId + ":" + nodeId + ":status", NodeStatus.READY);
    }

    /**
     * Устанавливает значение
     * @param key Ключ
     * @param value Значение
     */
    public void setValue(String key, Object value) {
        data.put(executionId + ":" + key, value);
    }

    /**
     * Получает значение
     * @param key Ключ
     * @return Значение
     */
    public Object getValue(String key) {
        return data.get(executionId + ":" + key);
    }

    /**
     * Получает значение с указанием типа и значения по умолчанию
     * @param key Ключ
     * @param defaultValue Значение по умолчанию
     * @param <T> Тип значения
     * @return Значение
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(String key, T defaultValue) {
        Object value = data.get(executionId + ":" + key);
        if (value != null && defaultValue != null && value.getClass().isAssignableFrom(defaultValue.getClass())) {
            return (T) value;
        }
        return defaultValue;
    }

    /**
     * Удаляет значение
     * @param key Ключ
     */
    public void removeValue(String key) {
        data.remove(executionId + ":" + key);
    }

    /**
     * Очищает все данные текущего выполнения
     */
    public void clear() {
        // Удаляем только данные для текущего executionId
        data.entrySet().removeIf(entry -> entry.getKey().startsWith(executionId + ":"));
    }

    /**
     * Генерирует новый ID выполнения
     * @return ID выполнения
     */
    public String generateNewExecutionId() {
        String newExecutionId = UUID.randomUUID().toString();
        // Удаляем данные для старого ID
        data.entrySet().removeIf(entry -> entry.getKey().startsWith(executionId + ":"));
        // Устанавливаем новый ID
        executionId = newExecutionId;
        return executionId;
    }
}