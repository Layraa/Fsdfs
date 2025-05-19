package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OnDamageNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    // Кэш для отслеживания последнего срабатывания узла
    private static final Map<String, Long> lastActivationTimes = new HashMap<>();
    private static final long COOLDOWN = 20; // Кулдаун в тиках

    // Карта для отслеживания активных узлов обработки урона для каждого моба
    private static final Map<Integer, Map<String, BehaviorNode>> damageHandlerNodes = new ConcurrentHashMap<>();
    private static final Map<Integer, BehaviorTreeExecutor> entityExecutors = new ConcurrentHashMap<>();

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

        // Регистрируем узел для обработки событий урона
        registerDamageHandler(entity.getId(), node.getId(), node, executor);

        // Воспроизводим анимацию получения урона, если она указана
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

    /**
     * Регистрирует узел для обработки событий урона
     */
    private void registerDamageHandler(int entityId, String nodeId, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Добавляем узел в карту обработчиков для этого моба
        Map<String, BehaviorNode> handlers = damageHandlerNodes.computeIfAbsent(entityId, id -> new ConcurrentHashMap<>());
        handlers.put(nodeId, node);

        // Запоминаем исполнитель для этого моба
        entityExecutors.put(entityId, executor);

        LOGGER.info("OnDamageNodeExecutor: Registered damage handler node {} for entity {}", nodeId, entityId);
    }

    /**
     * Метод для обработки события урона
     * Вызывается из MobSpawnEventHandler
     */
    public static void handleDamageEvent(CustomMobEntity entity, float amount, boolean isPlayerSource) {
        int entityId = entity.getId();

        // Проверяем, есть ли обработчики для этого моба
        Map<String, BehaviorNode> handlers = damageHandlerNodes.get(entityId);
        if (handlers == null || handlers.isEmpty()) {
            return;
        }

        BehaviorTreeExecutor executor = entityExecutors.get(entityId);
        if (executor == null) {
            return;
        }

        LOGGER.info("OnDamageNodeExecutor: Processing damage {} for entity {} (from player: {})",
                amount, entityId, isPlayerSource);

        // Обрабатываем все зарегистрированные узлы для этого моба
        for (Map.Entry<String, BehaviorNode> entry : handlers.entrySet()) {
            String nodeId = entry.getKey();
            BehaviorNode node = entry.getValue();

            // Получаем параметры узла
            double minDamage = node.getCustomParameterAsDouble("minDamage", 0.0);
            boolean reactToPlayerOnly = node.getCustomParameterAsBoolean("reactToPlayerOnly", true);

            // Проверяем условия
            if (amount < minDamage) {
                LOGGER.info("OnDamageNodeExecutor: Damage {} is below threshold {} for node {}",
                        amount, minDamage, nodeId);
                continue;
            }

            if (reactToPlayerOnly && !isPlayerSource) {
                LOGGER.info("OnDamageNodeExecutor: Damage not from player, ignoring for node {} (reactToPlayerOnly=true)", nodeId);
                continue;
            }

            // Обновляем время последней активации
            String nodeEntityId = entityId + ":" + nodeId;
            lastActivationTimes.put(nodeEntityId, entity.level().getGameTime());

            // Воспроизводим анимацию получения урона
            if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
                entity.setAnimation(node.getAnimationId(), node.isLoopAnimation(), (float) node.getAnimationSpeed());
            } else {
                entity.playAnimation("HURT");
            }

            // Выполняем дочерние узлы
            LOGGER.info("OnDamageNodeExecutor: Executing child nodes for node {}", nodeId);
            for (BehaviorNode child : executor.getChildNodes(node)) {
                executor.executeNode(child);
            }
        }
    }

    /**
     * Метод для очистки обработчиков при удалении моба
     */
    public static void cleanup(int entityId) {
        damageHandlerNodes.remove(entityId);
        entityExecutors.remove(entityId);
        LOGGER.info("OnDamageNodeExecutor: Cleaned up damage handlers for entity {}", entityId);
    }
}