package com.custommobsforge.custommobsforge.common.animations;

/**
 * Интерфейс адаптера анимаций
 */
public interface AnimationAdapter {
    /**
     * Воспроизводит анимацию
     * @param name Имя анимации
     * @param loop Зациклить анимацию
     * @param speed Скорость анимации
     */
    void playAnimation(String name, boolean loop, float speed);

    /**
     * Проверяет, воспроизводится ли анимация
     * @param name Имя анимации
     * @return true, если анимация воспроизводится
     */
    boolean isPlaying(String name);

    /**
     * Обновляет адаптер анимаций
     */
    void update();
}