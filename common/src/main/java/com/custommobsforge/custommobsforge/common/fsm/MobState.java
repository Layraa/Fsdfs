package com.custommobsforge.custommobsforge.common.fsm;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * Базовый класс состояния для конечного автомата
 */
public abstract class MobState {
    private String id;
    private String description;
    private Map<String, Object> stateData = new HashMap<>();

    public MobState() {
        // Пустой конструктор для сериализации
    }

    public MobState(String id, String description) {
        this.id = id;
        this.description = description;
    }

    /**
     * Вызывается при входе в состояние
     * @param entity Сущность
     */
    public void enter(CustomMobEntity entity) {
        // Переопределяется в подклассах
    }

    /**
     * Вызывается при выходе из состояния
     * @param entity Сущность
     */
    public void exit(CustomMobEntity entity) {
        // Переопределяется в подклассах
    }

    /**
     * Обновление состояния
     * @param entity Сущность
     */
    public abstract void update(CustomMobEntity entity);

    /**
     * Получение ID состояния
     * @return ID состояния
     */
    public String getId() {
        return id;
    }

    /**
     * Установка ID состояния
     * @param id ID состояния
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Получение описания состояния
     * @return Описание состояния
     */
    public String getDescription() {
        return description;
    }

    /**
     * Установка описания состояния
     * @param description Описание состояния
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Получение данных состояния
     * @return Данные состояния
     */
    public Map<String, Object> getStateData() {
        return stateData;
    }

    /**
     * Установка данных состояния
     * @param stateData Данные состояния
     */
    public void setStateData(Map<String, Object> stateData) {
        this.stateData = stateData;
    }

    /**
     * Получение сериализуемых данных состояния
     * @return Сериализуемые данные состояния
     */
    public Map<String, Object> getSerializableData() {
        return new HashMap<>(stateData);
    }

    /**
     * Применение данных состояния
     * @param data Данные состояния
     */
    public void applyData(Map<String, Object> data) {
        if (data != null) {
            stateData.putAll(data);
        }
    }

    /**
     * Сериализация состояния
     * @param buffer Буфер для записи
     */
    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUtf(id != null ? id : "");
        buffer.writeUtf(description != null ? description : "");

        // Сериализуем данные состояния
        Map<String, String> stringData = new HashMap<>();
        for (Map.Entry<String, Object> entry : stateData.entrySet()) {
            stringData.put(entry.getKey(), entry.getValue().toString());
        }

        buffer.writeInt(stringData.size());
        for (Map.Entry<String, String> entry : stringData.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    /**
     * Десериализация состояния
     * @param buffer Буфер для чтения
     * @return Состояние
     */
    public static MobState readFromBuffer(FriendlyByteBuf buffer) {
        // Этот метод должен быть переопределен в фабрике состояний
        throw new UnsupportedOperationException("This method should be overridden in state factory");
    }
}