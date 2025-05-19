package com.custommobsforge.custommobsforge.common.event.system;

/**
 * Интерфейс для слушателей событий с ID
 */
public interface IdentifiableListener<T extends Event> extends EventListener<T> {
    /**
     * Получить ID слушателя
     */
    String getId();
}
