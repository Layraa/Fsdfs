package com.custommobsforge.custommobsforge.common.animations;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.event.system.AnimationCompletedEvent;
import com.custommobsforge.custommobsforge.common.event.system.AnimationStartedEvent;
import com.custommobsforge.custommobsforge.common.event.system.EventSystem;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.AnimationSyncPacket;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Реализация адаптера анимаций
 */
public class AnimationAdapterImpl implements AnimationAdapter {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    private final CustomMobEntity entity;
    private final Queue<AnimationRequest> animationQueue = new ConcurrentLinkedQueue<>();
    private AnimationRequest currentAnimation;
    private final Map<String, Long> animationStartTimes = new ConcurrentHashMap<>();

    public AnimationAdapterImpl(CustomMobEntity entity) {
        this.entity = entity;
    }

    @Override
    public void playAnimation(String name, boolean loop, float speed) {
        AnimationRequest request = new AnimationRequest(name, loop, speed);
        if (currentAnimation == null || !currentAnimation.isPriority()) {
            startAnimation(request);
        } else {
            animationQueue.add(request);
        }
    }

    @Override
    public boolean isPlaying(String name) {
        return currentAnimation != null && currentAnimation.getName().equals(name);
    }

    @Override
    public void update() {
        // Проверяем завершение текущей анимации
        if (currentAnimation != null && !currentAnimation.isLoop()) {
            long startTime = animationStartTimes.getOrDefault(currentAnimation.getName(), 0L);
            long duration = estimateDuration(currentAnimation.getName());

            if (System.currentTimeMillis() - startTime > duration) {
                // Анимация завершена
                fireAnimationEndEvent(currentAnimation.getName());
                currentAnimation = null;

                // Запускаем следующую анимацию из очереди, если есть
                if (!animationQueue.isEmpty()) {
                    startAnimation(animationQueue.poll());
                }
            }
        }
    }

    /**
     * Запускает анимацию
     * @param request Запрос анимации
     */
    private void startAnimation(AnimationRequest request) {
        if (currentAnimation != null) {
            fireAnimationEndEvent(currentAnimation.getName());
        }

        currentAnimation = request;
        animationStartTimes.put(request.getName(), System.currentTimeMillis());

        // Интеграция с AzureLib
        entity.animationSpeed = request.getSpeed();
        entity.currentAnimation = request.getName();
        entity.looping = request.isLoop();

        // Отправка сетевого пакета
        if (!entity.level().isClientSide) {
            NetworkManager.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                    new AnimationSyncPacket(entity.getId(), request.getName(), request.getSpeed(), request.isLoop())
            );
        }

        // Генерация события
        fireAnimationStartEvent(request.getName());
    }

    /**
     * Оценивает продолжительность анимации
     * @param animationName Имя анимации
     * @return Продолжительность в миллисекундах
     */
    private long estimateDuration(String animationName) {
        // Эта логика должна быть согласована с методом estimateAnimationDuration в CustomMobEntity
        return entity.estimateAnimationDuration(animationName);
    }

    /**
     * Генерирует событие начала анимации
     * @param animationName Имя анимации
     */
    private void fireAnimationStartEvent(String animationName) {
        EventSystem.fireEvent(new AnimationStartedEvent(animationName, entity));
    }

    /**
     * Генерирует событие окончания анимации
     * @param animationName Имя анимации
     */
    private void fireAnimationEndEvent(String animationName) {
        // Очень важно: проверяем, не IDLE ли это анимация, чтобы избежать рекурсии
        if (!animationName.equalsIgnoreCase("IDLE")) {
            EventSystem.fireEvent(new AnimationCompletedEvent(animationName, entity));
        } else {
            // Для IDLE не генерируем события, чтобы избежать рекурсии
            LOGGER.debug("AnimationAdapterImpl: Skipping event generation for IDLE animation to avoid recursion");
        }
    }

    /**
     * Класс запроса анимации
     */
    private static class AnimationRequest {
        private final String name;
        private final boolean loop;
        private final float speed;
        private final boolean priority;

        public AnimationRequest(String name, boolean loop, float speed) {
            this.name = name;
            this.loop = loop;
            this.speed = speed;

            // Определяем, является ли анимация приоритетной
            this.priority = name.toLowerCase().contains("attack") ||
                    name.toLowerCase().contains("hurt") ||
                    name.toLowerCase().contains("death");
        }

        public String getName() {
            return name;
        }

        public boolean isLoop() {
            return loop;
        }

        public float getSpeed() {
            return speed;
        }

        public boolean isPriority() {
            return priority;
        }
    }
}