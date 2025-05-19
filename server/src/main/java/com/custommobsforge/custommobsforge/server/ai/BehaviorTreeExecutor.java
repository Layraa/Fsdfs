package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorConnection;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.event.system.EventSystem;
import com.custommobsforge.custommobsforge.common.event.system.NodeCompletedEvent;
import com.custommobsforge.custommobsforge.common.event.system.NodeStartedEvent;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.PlayBehaviorNodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;

public class BehaviorTreeExecutor extends Goal implements BehaviorContext {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private final CustomMobEntity entity;
    private final BehaviorTree tree;
    private final Map<String, NodeExecutor> nodeExecutors = new HashMap<>();
    private final List<String> activeNodeIds = new ArrayList<>();
    private String currentRootNodeId;
    private String currentExecutingNodeId; // Хранит текущий выполняемый узел
    private boolean nodeNeedsMoreTime = false; // Флаг, указывающий, что узлу нужно больше времени
    private int executionTicks = 0;
    private final int executionInterval = 5; // Обновление каждые 5 тиков (1/4 секунды)

    // Отслеживание статусов узлов
    private final Map<String, NodeStatus> nodeStatuses = new HashMap<>();

    // Отслеживание выполнения последовательностей
    private final Map<String, List<BehaviorNode>> pendingSequenceNodes = new HashMap<>();
    private final Map<String, Integer> sequenceNodeIndex = new HashMap<>();
    private final Set<String> currentlyExecutingNodes = new HashSet<>(); // Отслеживает узлы, которые сейчас выполняются
    private final Map<String, Long> lastNodeExecutionTime = new HashMap<>(); // Время последнего выполнения узла

    // Отслеживание состояния исполнения
    private boolean isExecutingSequence = false;
    private boolean treeCompleted = false;
    private long lastTreeCompletionTime = 0;
    private static final long TREE_RESTART_DELAY = 500; // 500 мс для быстрого рестарта

    // Конструктор
    public BehaviorTreeExecutor(CustomMobEntity entity, BehaviorTree tree) {
        this.entity = entity;
        this.tree = tree;

        LOGGER.info("BehaviorTreeExecutor: Created for entity {} with tree ID: {}",
                entity.getId(), (tree != null ? tree.getId() : "null"));

        if (tree == null) {
            LOGGER.error("BehaviorTreeExecutor: WARNING - Tree is null!");
        } else if (tree.getNodes() == null || tree.getNodes().isEmpty()) {
            LOGGER.error("BehaviorTreeExecutor: WARNING - Tree has no nodes!");
        }

        // Регистрируем все обработчики узлов
        initializeNodeExecutors();

        // Определяем корневой узел
        BehaviorNode rootNode = tree != null ? tree.getRootNode() : null;
        if (rootNode != null) {
            currentRootNodeId = rootNode.getId();
            LOGGER.info("BehaviorTreeExecutor: Root node set to {} of type {}",
                    rootNode.getId(), rootNode.getType());
        } else {
            LOGGER.error("BehaviorTreeExecutor: WARNING - No root node found!");
        }
    }

    // Метод инициализации обработчиков узлов
    private void initializeNodeExecutors() {
        // Базовые узлы
        nodeExecutors.put("sequencenode", new SequenceNodeExecutor());
        nodeExecutors.put("selectornode", new SelectorNodeExecutor());
        nodeExecutors.put("parallelnode", new ParallelNodeExecutor());
        nodeExecutors.put("weightedselectornode", new WeightedSelectorNodeExecutor());

        // Узлы действий
        nodeExecutors.put("attacknode", new AttackNodeExecutor());
        nodeExecutors.put("playanimationnode", new PlayAnimationNodeExecutor());
        nodeExecutors.put("timernode", new TimerNodeExecutor());
        nodeExecutors.put("follownode", new FollowNodeExecutor());
        nodeExecutors.put("fleenode", new FleeNodeExecutor());

        // Узлы событий
        nodeExecutors.put("onspawnnode", new OnSpawnNodeExecutor());
        nodeExecutors.put("ondeathnode", new OnDeathNodeExecutor());
        nodeExecutors.put("ondamagenode", new OnDamageNodeExecutor());

        // Узлы эффектов
        nodeExecutors.put("spawnparticlenode", new SpawnParticleNodeExecutor());
        nodeExecutors.put("displaytitlenode", new DisplayTitleNodeExecutor());
        nodeExecutors.put("playsoundnode", new PlaySoundNodeExecutor());
    }

    // Реализация методов Goal

    @Override
    public boolean canUse() {
        // Этот Goal всегда активен, если есть дерево поведения
        return tree != null && currentRootNodeId != null;
    }

    @Override
    public void start() {
        // Сбрасываем статус дерева при запуске
        treeCompleted = false;
        isExecutingSequence = false;
        pendingSequenceNodes.clear();
        sequenceNodeIndex.clear();
        nodeNeedsMoreTime = false;
        executionTicks = 0;
        nodeStatuses.clear();
    }

    // Также добавьте метод для сброса этих полей
    @Override
    public void stop() {
        // Очищаем активные узлы
        activeNodeIds.clear();
        pendingSequenceNodes.clear();
        sequenceNodeIndex.clear();
        currentExecutingNodeId = null;
        treeCompleted = false;
        isExecutingSequence = false;
        nodeNeedsMoreTime = false;
        nodeStatuses.clear();
        currentlyExecutingNodes.clear();
        lastNodeExecutionTime.clear();
    }

    @Override
    public void tick() {
        // Отладочный вывод при первом тике
        if (executionTicks == 0) {
            LOGGER.info("BehaviorTreeExecutor: First tick for entity {} with root node: {}",
                    entity.getId(), currentRootNodeId);

            // Выводим все узлы дерева для отладки
            if (tree != null && tree.getNodes() != null) {
                LOGGER.info("Tree nodes ({}): ", tree.getNodes().size());
                for (BehaviorNode node : tree.getNodes()) {
                    LOGGER.info("  - {} ({}): {}", node.getId(), node.getType(), node.getDescription());
                }
            } else {
                LOGGER.error("ERROR: Tree or nodes is null!");
            }

            // Выводим все соединения
            if (tree != null && tree.getConnections() != null) {
                LOGGER.info("Tree connections ({}): ", tree.getConnections().size());
                for (BehaviorConnection conn : tree.getConnections()) {
                    LOGGER.info("  - {} -> {}", conn.getSourceNodeId(), conn.getTargetNodeId());
                }
            }
        }

        executionTicks++;

        // Обновляем дерево с заданным интервалом
        if (executionTicks % executionInterval == 0) {
            LOGGER.info("BehaviorTreeExecutor: Executing tree for entity {} at tick {}",
                    entity.getId(), executionTicks);

            // Проверяем, не требуется ли перезапуск дерева после завершения
            long currentTime = System.currentTimeMillis();
            if (treeCompleted && (currentTime - lastTreeCompletionTime > TREE_RESTART_DELAY)) {
                LOGGER.info("BehaviorTreeExecutor: Restarting tree after completion delay");
                treeCompleted = false;
                isExecutingSequence = false;
                pendingSequenceNodes.clear();
                sequenceNodeIndex.clear();
                nodeNeedsMoreTime = false;
                nodeStatuses.clear();
            }

            // Если дерево завершено и не прошло время для перезапуска, пропускаем выполнение
            if (treeCompleted) {
                LOGGER.info("BehaviorTreeExecutor: Tree is completed, waiting for restart delay");
                return;
            }

            // Если у нас есть узел, который просит больше времени на выполнение (TimerNode, например)
            if (nodeNeedsMoreTime && currentExecutingNodeId != null) {
                BehaviorNode node = tree.getNode(currentExecutingNodeId);
                if (node != null) {
                    LOGGER.info("BehaviorTreeExecutor: Continuing execution of node {} of type {}",
                            node.getId(), node.getType());
                    boolean result = executeNode(node);
                    LOGGER.info("BehaviorTreeExecutor: Node execution result: {}", result);

                    // Если узел больше не требует времени, продолжаем выполнение последовательности
                    if (!nodeNeedsMoreTime && result && isExecutingSequence) {
                        continuePendingSequence();
                    }
                }
            } else if (isExecutingSequence && !pendingSequenceNodes.isEmpty()) {
                // Продолжаем выполнение отложенной последовательности
                continuePendingSequence();
            } else if (!isExecutingSequence) {
                // Начинаем новое выполнение дерева только если не выполняется последовательность
                boolean success = executeTree();
                LOGGER.info("BehaviorTreeExecutor: Tree execution started with result: {}", success);
            }
        }
    }

    // Реализация методов BehaviorContext
    @Override
    public void completeNode(BehaviorNode node, boolean success) {
        String nodeId = node.getId();
        NodeStatus oldStatus = nodeStatuses.getOrDefault(nodeId, NodeStatus.READY);
        NodeStatus newStatus = success ? NodeStatus.SUCCESS : NodeStatus.FAILURE;

        // Не меняем статус с RUNNING на тот же статус
        if (oldStatus == newStatus) {
            LOGGER.info("BehaviorTreeExecutor: Not changing node {} status from {} to same status",
                    nodeId, oldStatus);
            return;
        }

        nodeStatuses.put(nodeId, newStatus);

        // Уведомляем о завершении узла только если он действительно меняет статус
        EventSystem.fireEvent(new NodeCompletedEvent(node, entity, success));

        LOGGER.info("BehaviorTreeExecutor: Node {} completed with result: {}, changed status from {} to {}",
                nodeId, success, oldStatus, newStatus);
    }

    @Override
    public void setNodeNeedsMoreTime(BehaviorNode node, boolean needsMoreTime) {
        this.nodeNeedsMoreTime = needsMoreTime;
        if (needsMoreTime) {
            nodeStatuses.put(node.getId(), NodeStatus.RUNNING);
        }
    }

    @Override
    public boolean doesNodeNeedMoreTime(BehaviorNode node) {
        return this.nodeNeedsMoreTime && currentExecutingNodeId != null &&
                currentExecutingNodeId.equals(node.getId());
    }

    @Override
    public NodeStatus getNodeStatus(BehaviorNode node) {
        return nodeStatuses.getOrDefault(node.getId(), NodeStatus.READY);
    }

    public void clearNodeStatus(String nodeId) {
        // Очищаем статус узла, если что-то пошло не так
        // Это поможет избежать застреваний в деревьях поведения
        nodeNeedsMoreTime = false;
        nodeStatuses.remove(nodeId);
    }

    // Добавьте этот метод в BehaviorTreeExecutor для сброса статусов между запусками

    /**
     * Сбрасывает статусы узлов для нового выполнения дерева
     */
    private void resetNodeStatuses() {
        // Очищаем все статусы, кроме RUNNING
        for (String nodeId : new HashSet<>(nodeStatuses.keySet())) {
            NodeStatus status = nodeStatuses.get(nodeId);

            // Оставляем только узлы в процессе выполнения
            if (status != NodeStatus.RUNNING) {
                nodeStatuses.remove(nodeId);
            }
        }

        LOGGER.info("BehaviorTreeExecutor: Reset node statuses for new tree execution");
    }

    private boolean isNodeRecentlyExecuted(String nodeId) {
        long lastExecution = lastNodeExecutionTime.getOrDefault(nodeId, 0L);
        long currentTime = System.currentTimeMillis();

        // Если узел выполнялся менее 100 мс назад, считаем его недавно выполненным
        // Но если узел в статусе RUNNING, всё равно разрешаем его выполнение
        boolean isRunning = nodeStatuses.getOrDefault(nodeId, NodeStatus.READY) == NodeStatus.RUNNING;
        return currentTime - lastExecution < 100 && !isRunning;
    }

    // Модифицируйте метод executeTree
    private boolean executeTree() {
        if (tree == null || currentRootNodeId == null) {
            LOGGER.error("BehaviorTreeExecutor: Cannot execute tree - {} for entity {}",
                    (tree == null ? "tree is null" : "root node ID is null"), entity.getId());
            return false;
        }

        // Сбрасываем статусы узлов перед новым выполнением дерева
        // ВАЖНО: добавлено здесь
        resetNodeStatuses();

        // Получаем корневой узел
        BehaviorNode rootNode = tree.getNode(currentRootNodeId);
        if (rootNode == null) {
            LOGGER.error("BehaviorTreeExecutor: Root node with ID {} not found in tree!", currentRootNodeId);
            return false;
        }

        LOGGER.info("BehaviorTreeExecutor: Executing root node {} of type {}", rootNode.getId(), rootNode.getType());

        // Сбрасываем все отслеживаемые последовательности
        pendingSequenceNodes.clear();
        sequenceNodeIndex.clear();
        currentExecutingNodeId = rootNode.getId();
        isExecutingSequence = false;
        treeCompleted = false;

        // Уведомляем о начале выполнения узла
        EventSystem.fireEvent(new NodeStartedEvent(rootNode, entity));

        // Выполняем корневой узел
        boolean result = executeNode(rootNode);

        // Если корневой узел - SequenceNode, он уже настроил последовательность
        // и вернул true, но дерево еще не завершено
        if (result && !pendingSequenceNodes.isEmpty()) {
            isExecutingSequence = true;
            LOGGER.info("BehaviorTreeExecutor: Root node set up a sequence of {} nodes, will execute sequentially",
                    pendingSequenceNodes.values().iterator().next().size());
            return true;
        }

        // Если корневой узел не создал последовательность или вернул false,
        // дерево считается завершенным
        if (!isExecutingSequence && !nodeNeedsMoreTime) {
            treeCompleted = true;
            lastTreeCompletionTime = System.currentTimeMillis();
            LOGGER.info("BehaviorTreeExecutor: Tree execution completed, no sequences to continue");
        }

        return result;
    }

    // Продолжаем выполнение отложенной последовательности
    private void continuePendingSequence() {
        // Проверяем, есть ли отложенные последовательности
        if (pendingSequenceNodes.isEmpty()) {
            isExecutingSequence = false;
            treeCompleted = true;
            lastTreeCompletionTime = System.currentTimeMillis();
            LOGGER.info("BehaviorTreeExecutor: No pending sequences to continue, tree execution completed");
            return;
        }

        // Берем первую последовательность
        String sequenceId = pendingSequenceNodes.keySet().iterator().next();
        List<BehaviorNode> sequence = pendingSequenceNodes.get(sequenceId);
        int index = sequenceNodeIndex.getOrDefault(sequenceId, 0);

        // КРИТИЧНО: Проверяем, что индекс указывает на существующий узел
        if (index < 0 || index >= sequence.size()) {
            LOGGER.warn("BehaviorTreeExecutor: Index {} is out of bounds for sequence with {} nodes, resetting to 0",
                    index, sequence.size());

            if (index >= sequence.size()) {
                // Если индекс выходит за верхнюю границу, значит последовательность завершена
                pendingSequenceNodes.remove(sequenceId);
                sequenceNodeIndex.remove(sequenceId);
                LOGGER.info("BehaviorTreeExecutor: Sequence {} completed successfully", sequenceId);

                // Проверяем, остались ли еще последовательности
                if (pendingSequenceNodes.isEmpty()) {
                    isExecutingSequence = false;
                    treeCompleted = true;
                    lastTreeCompletionTime = System.currentTimeMillis();
                    LOGGER.info("BehaviorTreeExecutor: All sequences completed successfully, tree execution finished");
                } else {
                    // Если есть еще последовательности, будем выполнять их в следующем тике
                    LOGGER.info("BehaviorTreeExecutor: Moving to next sequence, {} remaining",
                            pendingSequenceNodes.size());
                }
                return;
            } else {
                // Если индекс отрицательный, это ошибка, сбрасываем индекс
                sequenceNodeIndex.put(sequenceId, 0);
                index = 0;
            }
        }

        // Получаем следующий узел для выполнения
        BehaviorNode nextNode = sequence.get(index);
        LOGGER.info("BehaviorTreeExecutor: Continuing sequence {}, executing node {} of type {} ({}/{})",
                sequenceId, nextNode.getId(), nextNode.getType(), index+1, sequence.size());

        if (isNodeRecentlyExecuted(nextNode.getId())) {
            LOGGER.warn("BehaviorTreeExecutor: Node {} was recently executed, waiting before next execution",
                    nextNode.getId());
            return; // Просто пропускаем этот тик и пробуем в следующем
        }

        // Устанавливаем текущий выполняемый узел
        currentExecutingNodeId = nextNode.getId();

        // Получаем статус узла
        NodeStatus status = nodeStatuses.getOrDefault(nextNode.getId(), NodeStatus.READY);

        // ВАЖНО: Логируем текущий статус узла
        LOGGER.info("BehaviorTreeExecutor: Node {} current status: {}", nextNode.getId(), status);

        // Проверяем, не выполняется ли уже узел
        if (status == NodeStatus.RUNNING) {
            LOGGER.info("BehaviorTreeExecutor: Node {} is already running, continuing execution", nextNode.getId());
            boolean result = executeNode(nextNode);
            LOGGER.info("BehaviorTreeExecutor: Node execution result: {}", result);

            // Если узел все еще выполняется, просто ждем
            if (nodeNeedsMoreTime) {
                LOGGER.info("BehaviorTreeExecutor: Node {} still needs more time", nextNode.getId());
                return;
            }

            // Если узел завершился, обновляем его статус
            if (result) {
                nodeStatuses.put(nextNode.getId(), NodeStatus.SUCCESS);

                // ВАЖНО: логируем новый статус
                LOGGER.info("BehaviorTreeExecutor: Node {} completed with SUCCESS", nextNode.getId());

                // Переходим к следующему узлу
                sequenceNodeIndex.put(sequenceId, index + 1);
                LOGGER.info("BehaviorTreeExecutor: Moving to next node in sequence, index now {}", index + 1);
            } else {
                nodeStatuses.put(nextNode.getId(), NodeStatus.FAILURE);

                // ВАЖНО: логируем новый статус
                LOGGER.info("BehaviorTreeExecutor: Node {} completed with FAILURE", nextNode.getId());

                // Если узел завершился неудачно, прерываем последовательность
                pendingSequenceNodes.remove(sequenceId);
                sequenceNodeIndex.remove(sequenceId);
                LOGGER.info("BehaviorTreeExecutor: Sequence {} failed at node {}", sequenceId, nextNode.getId());
            }
            return;
        } else if (status == NodeStatus.SUCCESS) {
            // Узел уже успешно выполнен, переходим к следующему
            LOGGER.info("BehaviorTreeExecutor: Node {} already completed successfully, moving to next", nextNode.getId());
            sequenceNodeIndex.put(sequenceId, index + 1);
            return;
        } else if (status == NodeStatus.FAILURE) {
            // Узел уже завершился неудачно, прерываем последовательность
            LOGGER.info("BehaviorTreeExecutor: Node {} already failed, terminating sequence", nextNode.getId());
            pendingSequenceNodes.remove(sequenceId);
            sequenceNodeIndex.remove(sequenceId);
            return;
        }

        // Узел еще не запускался, начинаем его выполнение
        LOGGER.info("BehaviorTreeExecutor: Starting new node {} execution", nextNode.getId());

        // Уведомляем о начале выполнения узла
        EventSystem.fireEvent(new NodeStartedEvent(nextNode, entity));

        // Устанавливаем статус RUNNING до выполнения
        nodeStatuses.put(nextNode.getId(), NodeStatus.RUNNING);

        // Выполняем узел
        boolean result = executeNode(nextNode);
        LOGGER.info("BehaviorTreeExecutor: Initial node execution result: {}", result);

        // Если узел требует больше времени, оставляем его в статусе RUNNING
        if (nodeNeedsMoreTime) {
            LOGGER.info("BehaviorTreeExecutor: Node needs more time, will continue on next tick");
            // Статус RUNNING уже установлен выше
            return;
        }

        // Устанавливаем финальный статус узла
        nodeStatuses.put(nextNode.getId(), result ? NodeStatus.SUCCESS : NodeStatus.FAILURE);

        // ВАЖНО: логируем финальный статус
        LOGGER.info("BehaviorTreeExecutor: Node {} completed with {}",
                nextNode.getId(), result ? "SUCCESS" : "FAILURE");

        // Уведомляем о завершении узла
        EventSystem.fireEvent(new NodeCompletedEvent(nextNode, entity, result));

        // Обновляем индекс или удаляем последовательность в зависимости от результата
        if (result) {
            sequenceNodeIndex.put(sequenceId, index + 1);
            LOGGER.info("BehaviorTreeExecutor: Moving to next node in sequence, index now {}", index + 1);
        } else {
            // Если узел вернул false, удаляем последовательность
            pendingSequenceNodes.remove(sequenceId);
            sequenceNodeIndex.remove(sequenceId);
            LOGGER.info("BehaviorTreeExecutor: Sequence {} failed at node {}", sequenceId, nextNode.getId());

            // Проверяем, остались ли еще последовательности
            if (pendingSequenceNodes.isEmpty()) {
                isExecutingSequence = false;
                treeCompleted = true;
                lastTreeCompletionTime = System.currentTimeMillis();
                LOGGER.info("BehaviorTreeExecutor: All sequences completed or failed, tree execution finished");
            }
        }
    }

    public boolean executeNode(BehaviorNode node) {
        if (node == null) {
            LOGGER.error("BehaviorTreeExecutor: Node is null, cannot execute");
            nodeNeedsMoreTime = false;
            return false;
        }

        String nodeId = node.getId();
        String nodeType = node.getType().toLowerCase();
        NodeExecutor executor = nodeExecutors.get(nodeType);

        // Если нет обработчика для этого типа узла, прерываем
        if (executor == null) {
            LOGGER.error("BehaviorTreeExecutor: No executor found for node type: {}", nodeType);
            LOGGER.info("Available node types: {}", String.join(", ", nodeExecutors.keySet()));
            nodeNeedsMoreTime = false;
            return false;
        }

        // НОВЫЙ КОД: Защита от повторного выполнения узла в один и тот же тик
        long currentTime = System.currentTimeMillis();
        long lastExecution = lastNodeExecutionTime.getOrDefault(nodeId, 0L);

        // Если узел выполнялся менее 50 мс назад и не требует больше времени, пропускаем выполнение
        if (currentTime - lastExecution < 50 && !nodeNeedsMoreTime && currentlyExecutingNodes.contains(nodeId)) {
            LOGGER.warn("BehaviorTreeExecutor: Node {} was just executed {} ms ago, skipping duplicate execution",
                    nodeId, currentTime - lastExecution);
            return true; // Предполагаем успех, чтобы не нарушать последовательность
        }

        // Запоминаем время выполнения
        lastNodeExecutionTime.put(nodeId, currentTime);

        // Добавляем узел в список выполняющихся
        currentlyExecutingNodes.add(nodeId);

        LOGGER.info("BehaviorTreeExecutor: Executing node {} of type {} with description: {}",
                nodeId, nodeType, node.getDescription());

        // Отправляем пакет о выполнении узла клиентам
        if (entity.level() instanceof ServerLevel) {
            try {
                NetworkManager.INSTANCE.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> this.entity),
                        new PlayBehaviorNodePacket(entity.getId(), nodeId, nodeType)
                );
                LOGGER.info("BehaviorTreeExecutor: Sent PlayBehaviorNodePacket to clients for node {}", nodeId);
            } catch (Exception e) {
                LOGGER.error("BehaviorTreeExecutor: Error sending PlayBehaviorNodePacket: {}", e.getMessage());
            }
        }

        // Сбрасываем флаг перед выполнением
        nodeNeedsMoreTime = false;

        try {
            // Выполняем узел с помощью соответствующего обработчика
            boolean result = executor.execute(entity, node, this);
            LOGGER.info("BehaviorTreeExecutor: Node {} execution result: {}, needsMoreTime: {}",
                    nodeId, result, nodeNeedsMoreTime);

            // Если узел не требует больше времени, удаляем его из списка выполняющихся
            if (!nodeNeedsMoreTime) {
                currentlyExecutingNodes.remove(nodeId);
            }

            // Если это SequenceNode, который вернул true,
            // сохраняем дочерние узлы для последующего выполнения
            if (result && nodeType.equals("sequencenode") && !nodeNeedsMoreTime) {
                List<BehaviorNode> children = getChildNodes(node);
                if (!children.isEmpty()) {
                    pendingSequenceNodes.put(nodeId, children);
                    sequenceNodeIndex.put(nodeId, 0);
                    isExecutingSequence = true;
                    LOGGER.info("BehaviorTreeExecutor: Created pending sequence for node {} with {} children",
                            nodeId, children.size());

                    // В следующем тике начнем выполнение этой последовательности
                    // Не запускаем сразу, чтобы избежать рекурсии
                    return true;
                }
            }

            return result;
        } catch (Exception e) {
            LOGGER.error("BehaviorTreeExecutor: Error executing node {}: {}", nodeId, e.getMessage());
            e.printStackTrace();

            // В случае ошибки удаляем узел из списка выполняющихся
            currentlyExecutingNodes.remove(nodeId);
            nodeNeedsMoreTime = false;
            return false;
        }
    }

    // Метод для логирования выполнения узлов
    public void logNodeExecution(String nodeType, String nodeId, String message, boolean isStart) {
        if (isStart) {
            LOGGER.info("BehaviorTreeExecutor: [START] {} node {} for entity {}: {}",
                    nodeType, nodeId, entity.getId(), message);
        } else {
            LOGGER.info("BehaviorTreeExecutor: [END] {} node {} for entity {}: {}",
                    nodeType, nodeId, entity.getId(), message);
        }
    }

    // Вспомогательные методы для обработчиков узлов

    // Получить список дочерних узлов
    public List<BehaviorNode> getChildNodes(BehaviorNode node) {
        return tree.getChildNodes(node.getId());
    }

    // Выполнить анимацию
    public void playAnimation(String action) {
        entity.playAnimation(action);
    }

    // Установить флаг, что узлу нужно больше времени
    public void setNodeNeedsMoreTime(boolean needsMoreTime) {
        this.nodeNeedsMoreTime = needsMoreTime;
    }

    // Получить значение флага
    public boolean getNodeNeedsMoreTime() {
        return this.nodeNeedsMoreTime;
    }

    // Проверить, активен ли узел
    public boolean isNodeActive(String nodeId) {
        return activeNodeIds.contains(nodeId);
    }

    // Добавить узел в список активных
    public void addActiveNode(String nodeId) {
        if (!activeNodeIds.contains(nodeId)) {
            activeNodeIds.add(nodeId);
        }
    }

    // Удалить узел из списка активных
    public void removeActiveNode(String nodeId) {
        activeNodeIds.remove(nodeId);
    }

    // Getter для entity
    public CustomMobEntity getEntity() {
        return entity;
    }
}