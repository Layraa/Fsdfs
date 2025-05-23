package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.server.animation.AnimationDurationCache;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.util.LogHelper;

/**
 * Исполнитель узла воспроизведения анимации (SERVER SIDE ONLY)
 * Запускает анимацию и ждет ее завершения (только для незацикленных)
 */
public class PlayAnimationNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        String nodeId = node.getId();

        LogHelper.info("[PlayAnimationNode] Execute called for node: {} on entity {}", nodeId, entity.getId());

        // Добавим отладку состояния
        LogHelper.info("[PlayAnimationNode] Current node status: {}, isRunning: {}",
                executor.getNodeStatus(nodeId),
                executor.isNodeRunning(nodeId));

        // Проверяем, запущен ли узел ранее
        if (executor.isNodeRunning(nodeId)) {
            LogHelper.debug("[PlayAnimationNode] Node {} is already running, checking completion", nodeId);
            return checkAnimationCompletion(entity, nodeId, executor);
        }

        // Проверяем blackboard напрямую
        Long existingCompletionTime = executor.getBlackboard().getValue(nodeId + ":completion_time", null);
        if (existingCompletionTime != null) {
            LogHelper.warn("[PlayAnimationNode] Found completion time in blackboard but node not marked as running! Time: {}", existingCompletionTime);
            return checkAnimationCompletion(entity, nodeId, executor);
        }

        // Получаем параметры анимации
        String animationId = getAnimationId(node);
        Boolean loopParam = getParameter(node, "loop", null, Boolean.class);
        float speed = getParameter(node, "speed", 1.0f, Float.class);

        LogHelper.info("[PlayAnimationNode] Starting animation: '{}' (loop param: {}, speed: {}) for entity {}",
                animationId, loopParam, speed, entity.getId());

        // Проверка параметров
        if (animationId == null || animationId.isEmpty()) {
            LogHelper.error("[PlayAnimationNode] No animation ID specified for node: {}", nodeId);
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // Получаем реальную информацию об анимации из кэша
        AnimationDurationCache.AnimationInfo animInfo = null;
        if (entity.getMobData() != null && entity.getMobData().getAnimationFilePath() != null) {
            animInfo = AnimationDurationCache.getAnimationInfo(
                    entity.getMobData().getAnimationFilePath(),
                    animationId
            );

            if (animInfo != null) {
                LogHelper.info("[PlayAnimationNode] Found cached animation info for '{}' - duration: {}s, loop: {}",
                        animationId, animInfo.durationSeconds, animInfo.loop);
            } else {
                LogHelper.warn("[PlayAnimationNode] No cached animation info found for '{}'", animationId);
            }
        }

        // Определяем финальные параметры
        boolean finalLoop;
        long duration;

        if (loopParam != null) {
            // Если loop явно указан в параметрах узла - используем его
            finalLoop = loopParam;
            LogHelper.info("[PlayAnimationNode] Using loop parameter from node: {}", finalLoop);
        } else if (animInfo != null) {
            // Иначе берем из файла анимации
            finalLoop = animInfo.loop;
            LogHelper.info("[PlayAnimationNode] Using loop from animation file: {}", finalLoop);
        } else {
            // По умолчанию false
            finalLoop = false;
            LogHelper.info("[PlayAnimationNode] Using default loop: false");
        }

        // Определяем длительность
        if (animInfo != null) {
            duration = animInfo.durationMillis;
        } else {
            // Используем умные значения по умолчанию на основе имени анимации
            duration = estimateAnimationDuration(animationId);
        }

        // Запускаем анимацию через entity
        boolean animationSet = setAnimation(entity, animationId, finalLoop, speed, node);
        if (!animationSet) {
            LogHelper.error("[PlayAnimationNode] Failed to set animation: '{}'", animationId);
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        // ВАЖНО: Для зацикленных анимаций сразу SUCCESS
        if (finalLoop) {
            LogHelper.info("[PlayAnimationNode] Looped animation '{}' started, returning SUCCESS immediately", animationId);
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Для незацикленных анимаций - запоминаем время завершения и ждем
        long adjustedDuration = (long)(duration / speed);
        long completionTime = System.currentTimeMillis() + adjustedDuration;

        LogHelper.info("[PlayAnimationNode] Saving completion time: {} for node: {}",
                completionTime, nodeId);

        executor.getBlackboard().setValue(nodeId + ":completion_time", completionTime);
        executor.getBlackboard().setValue(nodeId + ":animation_name", animationId);

        // Проверим, что сохранилось
        Long savedTime = executor.getBlackboard().getValue(nodeId + ":completion_time", null);
        LogHelper.info("[PlayAnimationNode] Verification - saved time: {}, original: {}",
                savedTime, completionTime);

        executor.setNodeStatus(nodeId, BehaviorTreeExecutor.NodeStatus.RUNNING);

        LogHelper.info("[PlayAnimationNode] Node status after setting RUNNING: {}, isRunning: {}",
                executor.getNodeStatus(nodeId),
                executor.isNodeRunning(nodeId));

        LogHelper.info("[PlayAnimationNode] Non-looped animation '{}' will complete in {} ms",
                animationId, adjustedDuration);

        return BehaviorTreeExecutor.NodeStatus.RUNNING;
    }

    /**
     * Проверяет завершение анимации
     */
    private BehaviorTreeExecutor.NodeStatus checkAnimationCompletion(CustomMobEntity entity, String nodeId, BehaviorTreeExecutor executor) {
        LogHelper.info("[PlayAnimationNode] checkAnimationCompletion for node: {}", nodeId);

        // Попробуем получить напрямую из карты
        LogHelper.info("[PlayAnimationNode] Trying to get completion time for key: '{}'",
                nodeId + ":completion_time");

        // ИСПРАВЛЕНИЕ: Получаем как Object и конвертируем
        Object completionTimeObj = executor.getBlackboard().getValue(nodeId + ":completion_time", null);
        Long completionTime = null;

        if (completionTimeObj instanceof Long) {
            completionTime = (Long) completionTimeObj;
        } else if (completionTimeObj instanceof Number) {
            completionTime = ((Number) completionTimeObj).longValue();
        }

        String animationName = executor.getBlackboard().getStringValue(nodeId + ":animation_name", "unknown");

        LogHelper.info("[PlayAnimationNode] Checking completion for {} - completionTime: {}, currentTime: {}",
                animationName, completionTime, System.currentTimeMillis());

        if (completionTime == null) {
            LogHelper.warn("[PlayAnimationNode] No completion time found for node {}", nodeId);
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime >= completionTime) {
            // Анимация завершена
            executor.getBlackboard().removeValue(nodeId + ":completion_time");
            executor.getBlackboard().removeValue(nodeId + ":animation_name");
            executor.setNodeStatus(nodeId, BehaviorTreeExecutor.NodeStatus.SUCCESS);

            entity.clearTreeAnimation(); // Добавьте этот метод в CustomMobEntity

            LogHelper.info("[PlayAnimationNode] Animation '{}' completed for node {}", animationName, nodeId);
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;
        }

        // Еще ждем
        long remainingTime = completionTime - currentTime;
        LogHelper.debug("[PlayAnimationNode] Animation '{}' still running, {} ms remaining",
                animationName, remainingTime);
        return BehaviorTreeExecutor.NodeStatus.RUNNING;

    }

    /**
     * Оценивает длительность анимации по имени (fallback)
     */
    private long estimateAnimationDuration(String animationId) {
        if (animationId == null || animationId.isEmpty()) {
            return 2000L;
        }

        String lowerAnimId = animationId.toLowerCase();
        long baseDuration;

        if (lowerAnimId.contains("summon") || lowerAnimId.contains("spawn")) {
            baseDuration = 4000L; // 4 секунды
        } else if (lowerAnimId.contains("attack") || lowerAnimId.contains("slash") ||
                lowerAnimId.contains("quickslash") || lowerAnimId.contains("strike")) {
            baseDuration = 1200L; // 1.2 секунды
        } else if (lowerAnimId.contains("death") || lowerAnimId.contains("die")) {
            baseDuration = 3000L; // 3 секунды
        } else if (lowerAnimId.contains("hurt") || lowerAnimId.contains("damage")) {
            baseDuration = 800L;  // 0.8 секунды
        } else if (lowerAnimId.contains("block") || lowerAnimId.contains("defend")) {
            baseDuration = 1500L; // 1.5 секунды
        } else if (lowerAnimId.contains("jump") || lowerAnimId.contains("dodge")) {
            baseDuration = 1000L; // 1 секунда
        } else if (lowerAnimId.contains("stab")) {
            baseDuration = 1500L; // 1.5 секунды для stab
        } else {
            baseDuration = 2000L; // 2 секунды по умолчанию
        }

        LogHelper.info("[PlayAnimationNode] Using estimated duration {} ms for animation '{}'",
                baseDuration, animationId);

        return baseDuration;
    }

    /**
     * Устанавливает анимацию для сущности
     */
    private boolean setAnimation(CustomMobEntity entity, String animationId, boolean loop, float speed, BehaviorNode node) {
        if (entity.getMobData() != null && entity.getMobData().getAnimations() != null) {
            // Проверяем стандартные анимации (IDLE, WALK, DEATH, SPAWN)
            var animationMapping = entity.getMobData().getAnimations().get(animationId.toUpperCase());
            if (animationMapping != null) {
                LogHelper.info("[PlayAnimationNode] Using STANDARD animation mapping: {} -> {}",
                        animationId, animationMapping.getAnimationName());

                // Для стандартных анимаций используем параметры из маппинга, если не переопределены в узле
                boolean finalLoop = node.getCustomParameters().containsKey("loop") ? loop : animationMapping.isLoop();
                float finalSpeed = node.getCustomParameters().containsKey("speed") ? speed : animationMapping.getSpeed();

                entity.setAnimation(animationMapping.getAnimationName(), finalLoop, finalSpeed);
                return true;
            }
        }


        // Используем как кастомную анимацию
        LogHelper.info("[PlayAnimationNode] Using CUSTOM animation: {} (loop: {}, speed: {})",
                animationId, loop, speed);
        entity.setAnimation(animationId, loop, speed);
        return true;

    }

    /**
     * Получает ID анимации из узла
     */
    private String getAnimationId(BehaviorNode node) {
        // Приоритет 1: customParameters
        String animationId = getParameter(node, "animation", null, String.class);
        if (animationId != null && !animationId.isEmpty()) {
            LogHelper.debug("[PlayAnimationNode] Got animation ID from customParameters: {}", animationId);
            return animationId;
        }

        // Приоритет 2: поле animationId
        if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
            LogHelper.debug("[PlayAnimationNode] Got animation ID from animationId field: {}", node.getAnimationId());
            return node.getAnimationId();
        }

        // Приоритет 3: строка parameter
        animationId = parseFromParameterString(node.getParameter(), "animation");
        if (animationId != null) {
            LogHelper.debug("[PlayAnimationNode] Got animation ID from parameter string: {}", animationId);
        }

        return animationId;
    }

}