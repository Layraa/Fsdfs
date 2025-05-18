package com.custommobsforge.custommobsforge.common.event.system;

/**
 * Базовый класс для всех событий в системе
 */
public abstract class Event {
    private final long timestamp;

    public Event() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}