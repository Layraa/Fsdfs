package com.custommobsforge.custommobsforge.common.fsm;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.List;

/**
 * Адаптер для выполнения узлов дерева поведения в рамках состояния
 */
public class AdapterState extends MobState {
    private List<BehaviorNode> nodes;
    private int currentNodeIndex = 0;
    private long lastExecutionTime = 0;
    private static final long EXECUTION_INTERVAL = 200; // мс

    public AdapterState(String id, String description) {
        super(id, description);
    }

    /**
     * Устанавливает узлы для выполнения
     * @param nodes Список узлов
     */
    public void setNodes(List<BehaviorNode> nodes) {
        this.nodes = nodes;
    }

    @Override
    public void enter(CustomMobEntity entity) {
        super.enter(entity);
        currentNodeIndex = 0;
        lastExecutionTime = 0;
    }

    @Override
    public void update(CustomMobEntity entity) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastExecutionTime < EXECUTION_INTERVAL) {
            return;
        }

        lastExecutionTime = currentTime;

        if (currentNodeIndex >= nodes.size()) {
            // Последовательность завершена, возвращаемся к IDLE
            entity.getStateManager().changeState("idle");
            return;
        }

        BehaviorNode currentNode = nodes.get(currentNodeIndex);

        // Выполняем узел через адаптер
        boolean result = executeNode(entity, currentNode);

        if (result) {
            // Узел успешно выполнен, переходим к следующему
            currentNodeIndex++;
        }
    }

    /**
     * Выполняет узел
     * @param entity Сущность
     * @param node Узел
     * @return true, если узел успешно выполнен
     */
    private boolean executeNode(CustomMobEntity entity, BehaviorNode node) {
        // Адаптер для выполнения узлов
        String nodeType = node.getType().toLowerCase();

        switch (nodeType) {
            case "playanimationnode":
                return executeAnimationNode(entity, node);
            case "timernode":
                return executeTimerNode(entity, node);
            case "follownode":
                return executeFollowNode(entity, node);
            // Другие типы узлов
            default:
                return true;
        }
    }

    /**
     * Выполняет узел анимации
     * @param entity Сущность
     * @param node Узел
     * @return true, если узел успешно выполнен
     */
    private boolean executeAnimationNode(CustomMobEntity entity, BehaviorNode node) {
        String animationId = node.getCustomParameterAsString("animation", "");
        if (animationId.isEmpty()) {
            animationId = node.getAnimationId();
        }

        boolean loop = node.isLoopAnimation();
        float speed = (float) node.getAnimationSpeed();

        if (animationId != null && !animationId.isEmpty()) {
            entity.playAnimationDirect(animationId, loop, speed);
            return true;
        }

        return false;
    }

    /**
     * Выполняет узел таймера
     * @param entity Сущность
     * @param node Узел
     * @return true, если узел успешно выполнен
     */
    private boolean executeTimerNode(CustomMobEntity entity, BehaviorNode node) {
        double duration = node.getCustomParameterAsDouble("duration", 1.0);

        // Проверяем, была ли уже установлена начальная метка времени
        Long startTime = (Long) getStateData().get("timer_" + node.getId());

        if (startTime == null) {
            // Устанавливаем начальную метку времени
            startTime = System.currentTimeMillis();
            getStateData().put("timer_" + node.getId(), startTime);
            return false; // Узел еще не выполнен
        }

        // Проверяем, прошло ли достаточно времени
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        if (elapsedTime >= duration * 1000) {
            // Таймер истек, удаляем метку времени
            getStateData().remove("timer_" + node.getId());
            return true; // Узел выполнен
        }

        return false; // Узел еще не выполнен
    }

    /**
     * Выполняет узел следования
     * @param entity Сущность
     * @param node Узел
     * @return true, если узел успешно выполнен
     */
    private boolean executeFollowNode(CustomMobEntity entity, BehaviorNode node) {
        // Здесь будет реализация узла следования
        // Для примера просто возвращаем true
        return true;
    }
}