package com.custommobsforge.custommobsforge.common.event.system;

/**
 * Интерфейс для слушателей событий
 */
@FunctionalInterface
public interface EventListener<T extends Event> {
    void onEvent(T event);
}