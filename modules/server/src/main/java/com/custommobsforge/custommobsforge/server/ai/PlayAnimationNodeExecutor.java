package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

public class PlayAnimationNodeExecutor implements NodeExecutor {
    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры анимации
        String animationId = node.getAnimationId();
        if (animationId == null || animationId.isEmpty()) {
            animationId = node.getCustomParameterAsString("animation", "");
        }

        // Если нет анимации, возвращаем неудачу
        if (animationId.isEmpty()) {
            return false;
        }

        // Получаем настройки воспроизведения
        boolean loop = node.isLoopAnimation();
        if (!node.getAnimationId().isEmpty()) {
            loop = node.getCustomParameterAsBoolean("loop", node.isLoopAnimation());
        }

        float speed = (float) node.getAnimationSpeed();
        if (node.getAnimationSpeed() == 0) {
            speed = (float) node.getCustomParameterAsDouble("speed", 1.0);
        }

        // Воспроизводим анимацию
        entity.setAnimation(animationId, loop, speed);

        return true;
    }
}