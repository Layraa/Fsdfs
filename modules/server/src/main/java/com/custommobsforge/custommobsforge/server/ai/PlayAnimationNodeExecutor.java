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
            System.out.println("PlayAnimationNodeExecutor: Getting animation from parameter: " + animationId);
        } else {
            System.out.println("PlayAnimationNodeExecutor: Using animation from node.animationId: " + animationId);
        }

        // Если нет анимации, возвращаем неудачу
        if (animationId.isEmpty()) {
            System.out.println("PlayAnimationNodeExecutor: No animation ID found for node " + node.getId());
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

        // Воспроизводим анимацию НАПРЯМУЮ, без маппинга
        System.out.println("PlayAnimationNodeExecutor: Directly playing animation '" + animationId +
                "' for entity " + entity.getId() +
                " (loop: " + loop + ", speed: " + speed + ")");

        entity.playAnimationDirect(animationId, loop, speed);

        return true;
    }
}