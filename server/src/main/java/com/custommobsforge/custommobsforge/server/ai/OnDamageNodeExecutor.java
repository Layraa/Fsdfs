package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.HashMap;
import java.util.Map;

public class OnDamageNodeExecutor implements NodeExecutor {
    // Кэш для отслеживания последнего срабатывания узла
    private static final Map<String, Long> lastActivationTimes = new HashMap<>();
    private static final long COOLDOWN = 20; // Кулдаун в тиках

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры
        double minDamage = node.getCustomParameterAsDouble("minDamage", 0.0);
        boolean reactToPlayerOnly = node.getCustomParameterAsBoolean("reactToPlayerOnly", true);

        // Уникальный идентификатор для данного узла и сущности
        String nodeEntityId = entity.getId() + ":" + node.getId();

        // Проверяем кулдаун
        long currentTime = entity.level().getGameTime();
        if (lastActivationTimes.containsKey(nodeEntityId)) {
            long lastActivation = lastActivationTimes.get(nodeEntityId);
            if (currentTime - lastActivation < COOLDOWN) {
                return false; // Кулдаун еще не истек
            }
        }

        // Воспроизводим анимацию получения урона
        if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
            entity.setAnimation(node.getAnimationId(), node.isLoopAnimation(), (float) node.getAnimationSpeed());
        } else {
            executor.playAnimation("HURT");
        }

        // Обновляем время последнего срабатывания
        lastActivationTimes.put(nodeEntityId, currentTime);

        // Выполняем дочерние узлы
        boolean anyChildSucceeded = false;
        for (BehaviorNode child : executor.getChildNodes(node)) {
            if (executor.executeNode(child)) {
                anyChildSucceeded = true;
            }
        }

        return anyChildSucceeded;
    }
}