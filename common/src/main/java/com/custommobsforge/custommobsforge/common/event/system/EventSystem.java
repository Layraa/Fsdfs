package com.custommobsforge.custommobsforge.common.event.system;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Система управления событиями
 */
public class EventSystem {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private static final Map<Class<?>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * Регистрирует слушателя для определенного типа события
     */
    /**
     * Регистрирует слушателя для определенного типа события с ID
     */
    public static <T extends Event> void registerListener(Class<T> eventType, EventListener<T> listener, String listenerId) {
        // Оборачиваем слушателя в IdentifiableListener, если он им еще не является
        EventListener<T> identifiableListener = listener;
        if (!(listener instanceof IdentifiableListener)) {
            identifiableListener = new IdentifiableListener<T>() {
                @Override
                public String getId() {
                    return listenerId;
                }

                @Override
                public void onEvent(T event) {
                    listener.onEvent(event);
                }
            };
        }

        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(identifiableListener);
        LOGGER.debug("Registered listener with ID {} for event type: {}", listenerId, eventType.getSimpleName());
    }

    /**
     * Регистрирует слушателя для определенного типа события без ID
     */
    public static <T extends Event> void registerListener(Class<T> eventType, EventListener<T> listener) {
        registerListener(eventType, listener, UUID.randomUUID().toString());
    }

    /**
     * Удаляет слушателя для определенного типа события
     */
    public static <T extends Event> void unregisterListener(Class<T> eventType, EventListener<T> listener) {
        if (listeners.containsKey(eventType)) {
            listeners.get(eventType).remove(listener);
            LOGGER.debug("Unregistered listener for event type: {}", eventType.getSimpleName());
        }
    }

    /**
     * Удаляет слушателя для всех типов событий
     */
    public static void unregisterListener(EventListener<?> listener) {
        for (List<EventListener<?>> listenerList : listeners.values()) {
            listenerList.remove(listener);
        }
        LOGGER.debug("Unregistered listener from all event types");
    }

    /**
     * Генерирует событие и уведомляет всех зарегистрированных слушателей
     */
    @SuppressWarnings("unchecked")
    public static <T extends Event> void fireEvent(T event) {
        List<EventListener<?>> eventListeners = listeners.getOrDefault(event.getClass(), Collections.emptyList());
        LOGGER.debug("Firing event of type {} to {} listeners", event.getClass().getSimpleName(), eventListeners.size());

        for (EventListener<?> listener : eventListeners) {
            try {
                ((EventListener<T>)listener).onEvent(event);
            } catch (Exception e) {
                LOGGER.error("Error in event listener: {}", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Очищает все зарегистрированные слушатели
     */
    public static void clearAllListeners() {
        listeners.clear();
        LOGGER.debug("Cleared all event listeners");
    }

    public static void unregisterListenerById(String listenerId) {
        for (List<EventListener<?>> listenerList : listeners.values()) {
            // Предполагаем, что EventListener имплементирует интерфейс с методом getId()
            listenerList.removeIf(listener -> {
                if (listener instanceof IdentifiableListener) {
                    return ((IdentifiableListener<?>) listener).getId().equals(listenerId);
                }
                return false;
            });
        }
        LOGGER.debug("Unregistered listener with ID: {}", listenerId);
    }
}