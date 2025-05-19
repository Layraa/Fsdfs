package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.ai.Blackboard;
import com.custommobsforge.custommobsforge.common.ai.NodeStatus;
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
    private String currentExecutingNodeId;
    private boolean nodeNeedsMoreTime = false;
    private int executionTicks = 0;
    private final int executionInterval = 5;

    // Единый Blackboard для хранения данных между узлами
    private final Blackboard blackboard = new Blackboard();

    // Отслеживание выполнения последовательностей
    private final Map<String, List<BehaviorNode>> pendingSequenceNodes = new HashMap<>();
    private final Map<String, Integer> sequenceNodeIndex = new HashMap<>();
    private final Set<String> currentlyExecutingNodes = new HashSet<>();
    private final Map<String, Long> lastNodeExecutionTime = new HashMap<>();

    // Отслеживание состояния исполнения
    private boolean isExecutingSequence = false;
    private boolean treeCompleted = false;
    private long lastTreeCompletionTime = 0;
    private static final long TREE_RESTART_DELAY = 500;

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
        return tree != null && currentRootNodeId != null;
    }

    @Override
    public void start() {
        treeCompleted = false;
        isExecutingSequence = false;
        pendingSequenceNodes.clear();
        sequenceNodeIndex.clear();
        nodeNeedsMoreTime = false;
        executionTicks = 0;

        // Сбрасываем Blackboard
        blackboard.clear();
    }

    @Override
    public void stop() {
        activeNodeIds.clear();
        pendingSequenceNodes.clear();
        sequenceNodeIndex.clear();
        currentExecutingNodeId = null;
        treeCompleted = false;
        isExecutingSequence = false;
        nodeNeedsMoreTime = false;

        // Очищаем Blackboard
        blackboard.clear();

        currentlyExecutingNodes.clear();
        lastNodeExecutionTime.clear();
    }

    @Override
    public void tick() {
        if (executionTicks == 0) {
            LOGGER.info("BehaviorTreeExecutor: First tick for entity {} with root node: {}",
                    entity.getId(), currentRootNodeId);

            if (tree != null && tree.getNodes() != null) {
                LOGGER.info("Tree nodes ({}): ", tree.getNodes().size());
                for (BehaviorNode node : tree.getNodes()) {
                    LOGGER.info("  - {} ({}): {}", node.getId(), node.getType(), node.getDescription());
                }
            } else {
                LOGGER.error("ERROR: Tree or nodes is null!");
            }

            if (tree != null && tree.getConnections() != null) {
                LOGGER.info("Tree connections ({}): ", tree.getConnections().size());
                for (BehaviorConnection conn : tree.getConnections()) {
                    LOGGER.info("  - {} -> {}", conn.getSourceNodeId(), conn.getTargetNodeId());
                }
            }
        }

        executionTicks++;

        if (executionTicks % executionInterval == 0) {
            LOGGER.info("BehaviorTreeExecutor: Executing tree for entity {} at tick {}",
                    entity.getId(), executionTicks);

            long currentTime = System.currentTimeMillis();
            if (treeCompleted && (currentTime - lastTreeCompletionTime > TREE_RESTART_DELAY)) {
                LOGGER.info("BehaviorTreeExecutor: Restarting tree after completion delay");
                treeCompleted = false;
                isExecutingSequence = false;
                pendingSequenceNodes.clear();
                sequenceNodeIndex.clear();
                nodeNeedsMoreTime = false;

                blackboard.generateNewExecutionId();
            }

            if (treeCompleted) {
                LOGGER.info("BehaviorTreeExecutor: Tree is completed, waiting for restart delay");
                return;
            }

            if (nodeNeedsMoreTime && currentExecutingNodeId != null) {
                BehaviorNode node = tree.getNode(currentExecutingNodeId);
                if (node != null) {
                    LOGGER.info("BehaviorTreeExecutor: Continuing execution of node {} of type {}",
                            node.getId(), node.getType());
                    boolean result = executeNode(node);
                    LOGGER.info("BehaviorTreeExecutor: Node execution result: {}", result);

                    if (!nodeNeedsMoreTime && result && isExecutingSequence) {
                        continuePendingSequence();
                    }
                }
            } else if (isExecutingSequence && !pendingSequenceNodes.isEmpty()) {
                continuePendingSequence();
            } else if (!isExecutingSequence) {
                boolean success = executeTree();
                LOGGER.info("BehaviorTreeExecutor: Tree execution started with result: {}", success);
            }
        }
    }

    // Реализация методов BehaviorContext
    @Override
    public void completeNode(BehaviorNode node, boolean success) {
        String nodeId = node.getId();
        blackboard.setNodeStatus(nodeId, success ? NodeStatus.SUCCESS : NodeStatus.FAILURE);

        EventSystem.fireEvent(new NodeCompletedEvent(node, entity, success));

        LOGGER.info("BehaviorTreeExecutor: Node {} completed with result: {}", nodeId, success);
    }

    @Override
    public void setNodeNeedsMoreTime(BehaviorNode node, boolean needsMoreTime) {
        this.nodeNeedsMoreTime = needsMoreTime;
        if (needsMoreTime) {
            blackboard.setNodeStatus(node.getId(), NodeStatus.RUNNING);
        }
    }

    @Override
    public boolean doesNodeNeedMoreTime(BehaviorNode node) {
        return this.nodeNeedsMoreTime && currentExecutingNodeId != null &&
                currentExecutingNodeId.equals(node.getId());
    }

    @Override
    public NodeStatus getNodeStatus(BehaviorNode node) {
        return blackboard.getNodeStatus(node.getId());
    }

    public void clearNodeStatus(String nodeId) {
        nodeNeedsMoreTime = false;
        blackboard.removeValue(nodeId + ":status");
    }

    private boolean isNodeRecentlyExecuted(String nodeId) {
        long lastExecution = lastNodeExecutionTime.getOrDefault(nodeId, 0L);
        long currentTime = System.currentTimeMillis();

        boolean isRunning = blackboard.getNodeStatus(nodeId) == NodeStatus.RUNNING;
        return currentTime - lastExecution < 100 && !isRunning;
    }

    private void resetNodeStatuses() {
        blackboard.generateNewExecutionId();
        LOGGER.info("BehaviorTreeExecutor: Reset node statuses for new tree execution");
    }

    private boolean executeTree() {
        if (tree == null || currentRootNodeId == null) {
            LOGGER.error("BehaviorTreeExecutor: Cannot execute tree - {} for entity {}",
                    (tree == null ? "tree is null" : "root node ID is null"), entity.getId());
            return false;
        }

        resetNodeStatuses();

        BehaviorNode rootNode = tree.getNode(currentRootNodeId);
        if (rootNode == null) {
            LOGGER.error("BehaviorTreeExecutor: Root node with ID {} not found in tree!", currentRootNodeId);
            return false;
        }

        LOGGER.info("BehaviorTreeExecutor: Executing root node {} of type {}", rootNode.getId(), rootNode.getType());

        pendingSequenceNodes.clear();
        sequenceNodeIndex.clear();
        currentExecutingNodeId = rootNode.getId();
        isExecutingSequence = false;
        treeCompleted = false;

        EventSystem.fireEvent(new NodeStartedEvent(rootNode, entity));

        boolean result = executeNode(rootNode);

        if (result && !pendingSequenceNodes.isEmpty()) {
            isExecutingSequence = true;
            LOGGER.info("BehaviorTreeExecutor: Root node set up a sequence of {} nodes, will execute sequentially",
                    pendingSequenceNodes.values().iterator().next().size());
            return true;
        }

        if (!isExecutingSequence && !nodeNeedsMoreTime) {
            treeCompleted = true;
            lastTreeCompletionTime = System.currentTimeMillis();
            LOGGER.info("BehaviorTreeExecutor: Tree execution completed, no sequences to continue");
        }

        return result;
    }

    private void continuePendingSequence() {
        if (pendingSequenceNodes.isEmpty()) {
            isExecutingSequence = false;
            treeCompleted = true;
            lastTreeCompletionTime = System.currentTimeMillis();
            LOGGER.info("BehaviorTreeExecutor: No pending sequences to continue, tree execution completed");
            return;
        }

        // Проверяем, не был ли узел успешно завершен до того, как мы вернулись в continuePendingSequence
        if (currentExecutingNodeId != null) {
            BehaviorNode currentNode = tree.getNode(currentExecutingNodeId);
            if (currentNode != null) {
                NodeStatus status = getNodeStatus(currentNode);
                if (status == NodeStatus.SUCCESS) {
                    LOGGER.info("BehaviorTreeExecutor: Node {} is now SUCCESS, moving to next node in sequence", currentExecutingNodeId);

                    // Находим последовательность, содержащую этот узел
                    for (Map.Entry<String, List<BehaviorNode>> entry : pendingSequenceNodes.entrySet()) {
                        String sequenceId = entry.getKey();
                        List<BehaviorNode> nodes = entry.getValue();
                        int index = sequenceNodeIndex.getOrDefault(sequenceId, 0);

                        if (index < nodes.size() && nodes.get(index).getId().equals(currentExecutingNodeId)) {
                            // Увеличиваем индекс
                            sequenceNodeIndex.put(sequenceId, index + 1);
                            LOGGER.info("BehaviorTreeExecutor: Updated sequence {} index to {}", sequenceId, index + 1);

                            // Обеспечиваем плавный переход, если есть следующий узел
                            if (index + 1 < nodes.size()) {
                                BehaviorNode nextNode = nodes.get(index + 1);
                                ensureSmoothTransition(currentNode, nextNode);
                            }
                            break; // Важно: выходим из цикла после обновления нужной последовательности
                        }
                    }
                }
            }
        }

        String sequenceId = pendingSequenceNodes.keySet().iterator().next();
        List<BehaviorNode> sequence = pendingSequenceNodes.get(sequenceId);
        int index = sequenceNodeIndex.getOrDefault(sequenceId, 0);

        if (index < 0 || index >= sequence.size()) {
            LOGGER.warn("BehaviorTreeExecutor: Index {} is out of bounds for sequence with {} nodes, resetting to 0",
                    index, sequence.size());

            if (index >= sequence.size()) {
                pendingSequenceNodes.remove(sequenceId);
                sequenceNodeIndex.remove(sequenceId);
                LOGGER.info("BehaviorTreeExecutor: Sequence {} completed successfully", sequenceId);

                if (pendingSequenceNodes.isEmpty()) {
                    isExecutingSequence = false;
                    treeCompleted = true;
                    lastTreeCompletionTime = System.currentTimeMillis();
                    LOGGER.info("BehaviorTreeExecutor: All sequences completed successfully, tree execution finished");
                } else {
                    LOGGER.info("BehaviorTreeExecutor: Moving to next sequence, {} remaining",
                            pendingSequenceNodes.size());
                }
                return;
            } else {
                sequenceNodeIndex.put(sequenceId, 0);
                index = 0;
            }
        }

        BehaviorNode nextNode = sequence.get(index);
        LOGGER.info("BehaviorTreeExecutor: Continuing sequence {}, executing node {} of type {} ({}/{})",
                sequenceId, nextNode.getId(), nextNode.getType(), index+1, sequence.size());

        if (isNodeRecentlyExecuted(nextNode.getId())) {
            LOGGER.warn("BehaviorTreeExecutor: Node {} was recently executed, waiting before next execution",
                    nextNode.getId());
            return;
        }

        currentExecutingNodeId = nextNode.getId();
        NodeStatus status = getNodeStatus(nextNode);

        LOGGER.info("BehaviorTreeExecutor: Node {} current status: {}", nextNode.getId(), status);

        if (status == NodeStatus.RUNNING) {
            LOGGER.info("BehaviorTreeExecutor: Node {} is already running, continuing execution", nextNode.getId());
            boolean result = executeNode(nextNode);
            LOGGER.info("BehaviorTreeExecutor: Node execution result: {}", result);

            if (nodeNeedsMoreTime) {
                LOGGER.info("BehaviorTreeExecutor: Node {} still needs more time", nextNode.getId());
                return;
            }

            if (result) {
                blackboard.setNodeStatus(nextNode.getId(), NodeStatus.SUCCESS);
                LOGGER.info("BehaviorTreeExecutor: Node {} completed with SUCCESS", nextNode.getId());
                sequenceNodeIndex.put(sequenceId, index + 1);
                LOGGER.info("BehaviorTreeExecutor: Moving to next node in sequence, index now {}", index + 1);
            } else {
                blackboard.setNodeStatus(nextNode.getId(), NodeStatus.FAILURE);
                LOGGER.info("BehaviorTreeExecutor: Node {} completed with FAILURE", nextNode.getId());
                pendingSequenceNodes.remove(sequenceId);
                sequenceNodeIndex.remove(sequenceId);
                LOGGER.info("BehaviorTreeExecutor: Sequence {} failed at node {}", sequenceId, nextNode.getId());
            }
            return;
        } else if (status == NodeStatus.SUCCESS) {
            LOGGER.info("BehaviorTreeExecutor: Node {} already completed successfully, moving to next", nextNode.getId());
            sequenceNodeIndex.put(sequenceId, index + 1);
            return;
        } else if (status == NodeStatus.FAILURE) {
            LOGGER.info("BehaviorTreeExecutor: Node {} already failed, terminating sequence", nextNode.getId());
            pendingSequenceNodes.remove(sequenceId);
            sequenceNodeIndex.remove(sequenceId);
            return;
        }

        LOGGER.info("BehaviorTreeExecutor: Starting new node {} execution", nextNode.getId());
        EventSystem.fireEvent(new NodeStartedEvent(nextNode, entity));
        blackboard.setNodeStatus(nextNode.getId(), NodeStatus.RUNNING);

        boolean result = executeNode(nextNode);
        LOGGER.info("BehaviorTreeExecutor: Initial node execution result: {}", result);

        if (nodeNeedsMoreTime) {
            LOGGER.info("BehaviorTreeExecutor: Node needs more time, will continue on next tick");
            return;
        }

        NodeStatus finalStatus = result ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
        blackboard.setNodeStatus(nextNode.getId(), finalStatus);
        LOGGER.info("BehaviorTreeExecutor: Node {} completed with {}", nextNode.getId(), result ? "SUCCESS" : "FAILURE");
        EventSystem.fireEvent(new NodeCompletedEvent(nextNode, entity, result));

        if (result) {
            sequenceNodeIndex.put(sequenceId, index + 1);
            LOGGER.info("BehaviorTreeExecutor: Moving to next node in sequence, index now {}", index + 1);
        } else {
            pendingSequenceNodes.remove(sequenceId);
            sequenceNodeIndex.remove(sequenceId);
            LOGGER.info("BehaviorTreeExecutor: Sequence {} failed at node {}", sequenceId, nextNode.getId());

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

        if (executor == null) {
            LOGGER.error("BehaviorTreeExecutor: No executor found for node type: {}", nodeType);
            LOGGER.info("Available node types: {}", String.join(", ", nodeExecutors.keySet()));
            nodeNeedsMoreTime = false;
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long lastExecution = lastNodeExecutionTime.getOrDefault(nodeId, 0L);

        if (currentTime - lastExecution < 50 && !nodeNeedsMoreTime && currentlyExecutingNodes.contains(nodeId)) {
            LOGGER.warn("BehaviorTreeExecutor: Node {} was just executed {} ms ago, skipping duplicate execution",
                    nodeId, currentTime - lastExecution);
            return true;
        }

        lastNodeExecutionTime.put(nodeId, currentTime);
        currentlyExecutingNodes.add(nodeId);

        LOGGER.info("BehaviorTreeExecutor: Executing node {} of type {} with description: {}",
                nodeId, nodeType, node.getDescription());

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

        nodeNeedsMoreTime = false;

        try {
            boolean result = executor.execute(entity, node, this);
            LOGGER.info("BehaviorTreeExecutor: Node {} execution result: {}, needsMoreTime: {}",
                    nodeId, result, nodeNeedsMoreTime);

            if (!nodeNeedsMoreTime) {
                currentlyExecutingNodes.remove(nodeId);
            }

            if (result && nodeType.equals("sequencenode") && !nodeNeedsMoreTime) {
                List<BehaviorNode> children = getChildNodes(node);
                if (!children.isEmpty()) {
                    pendingSequenceNodes.put(nodeId, children);
                    sequenceNodeIndex.put(nodeId, 0);
                    isExecutingSequence = true;
                    LOGGER.info("BehaviorTreeExecutor: Created pending sequence for node {} with {} children",
                            nodeId, children.size());
                    return true;
                }
            }

            return result;
        } catch (Exception e) {
            LOGGER.error("BehaviorTreeExecutor: Error executing node {}: {}", nodeId, e.getMessage());
            e.printStackTrace();
            currentlyExecutingNodes.remove(nodeId);
            nodeNeedsMoreTime = false;
            return false;
        }
    }

    public void logNodeExecution(String nodeType, String nodeId, String message, boolean isStart) {
        if (isStart) {
            LOGGER.info("BehaviorTreeExecutor: [START] {} node {} for entity {}: {}",
                    nodeType, nodeId, entity.getId(), message);
        } else {
            LOGGER.info("BehaviorTreeExecutor: [END] {} node {} for entity {}: {}",
                    nodeType, nodeId, entity.getId(), message);
        }
    }

    public List<BehaviorNode> getChildNodes(BehaviorNode node) {
        return tree.getChildNodes(node.getId());
    }

    public void playAnimation(String action) {
        entity.playAnimation(action);
    }

    public void setNodeNeedsMoreTime(boolean needsMoreTime) {
        this.nodeNeedsMoreTime = needsMoreTime;
    }

    public boolean getNodeNeedsMoreTime() {
        return this.nodeNeedsMoreTime;
    }

    public boolean isNodeActive(String nodeId) {
        return activeNodeIds.contains(nodeId);
    }

    public void addActiveNode(String nodeId) {
        if (!activeNodeIds.contains(nodeId)) {
            activeNodeIds.add(nodeId);
        }
    }

    public void removeActiveNode(String nodeId) {
        activeNodeIds.remove(nodeId);
    }

    public CustomMobEntity getEntity() {
        return entity;
    }

    public Blackboard getBlackboard() {
        return blackboard;
    }

    /**
     * Обеспечивает плавный переход между узлами
     * @param currentNode Текущий узел
     * @param nextNode Следующий узел
     */
    private void ensureSmoothTransition(BehaviorNode currentNode, BehaviorNode nextNode) {
        if (currentNode == null || nextNode == null) {
            return;
        }

        // Если текущий узел - анимация, а следующий - не анимация,
        // убедимся, что моб не останется без анимации (в T-позе)
        if (currentNode.getType().equalsIgnoreCase("playanimationnode") &&
                !nextNode.getType().equalsIgnoreCase("playanimationnode")) {

            LOGGER.info("BehaviorTreeExecutor: Transitioning from animation node to non-animation node, " +
                    "ensuring IDLE animation to prevent T-pose");
            entity.playAnimation("IDLE");
        }
    }
}