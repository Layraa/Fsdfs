package com.custommobsforge.custommobsforge.server.behavior;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.behavior.executors.*;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Основной исполнитель дерева поведения для CustomMobEntity (SERVER SIDE ONLY)
 */
public class BehaviorTreeExecutor extends Goal {

    // Статусы выполнения узлов
    public enum NodeStatus {
        SUCCESS,    // Узел выполнен успешно
        FAILURE,    // Узел завершился неудачей
        RUNNING     // Узел выполняется
    }

    private final CustomMobEntity entity;
    private final BehaviorTree tree;
    private final Blackboard blackboard;
    private final Map<String, NodeExecutor> nodeExecutors;
    private final Map<String, NodeStatus> nodeStatuses;
    private final Set<String> runningNodes;

    // Интервал обновления для оптимизации (тики)
    private int updateInterval = 3; // Обновление каждые 3 тика для плавности
    private int tickCounter = 0;

    // Флаги состояния
    private boolean treeInitialized = false;

    public BehaviorTreeExecutor(CustomMobEntity entity, BehaviorTree tree) {
        this.entity = entity;
        this.tree = tree;
        this.blackboard = new Blackboard();
        this.nodeExecutors = new HashMap<>();
        this.nodeStatuses = new ConcurrentHashMap<>();
        this.runningNodes = ConcurrentHashMap.newKeySet(); // Thread-safe set

        // Регистрируем исполнители всех типов узлов
        registerNodeExecutors();

        // Устанавливаем флаги для AI цели
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /**
     * Регистрирует исполнители для всех типов узлов
     */
    private void registerNodeExecutors() {
        // Основные узлы управления потоком
        nodeExecutors.put("sequencenode", new SequenceNodeExecutor());
        nodeExecutors.put("selectornode", new SelectorNodeExecutor());
        nodeExecutors.put("parallelnode", new ParallelNodeExecutor());
        nodeExecutors.put("weightedselectornode", new WeightedSelectorNodeExecutor());

        // Узлы действий
        nodeExecutors.put("playanimationnode", new PlayAnimationNodeExecutor());
        nodeExecutors.put("attacknode", new AttackNodeExecutor());
        nodeExecutors.put("follownode", new FollowNodeExecutor());
        nodeExecutors.put("fleenode", new FleeNodeExecutor());
        nodeExecutors.put("timernode", new TimerNodeExecutor());

        // Узлы событий
        nodeExecutors.put("onspawnnode", new OnSpawnNodeExecutor());
        nodeExecutors.put("ondeathnode", new OnDeathNodeExecutor());
        nodeExecutors.put("ondamagenode", new OnDamageNodeExecutor());

        // Узлы эффектов
        nodeExecutors.put("spawnparticlenode", new SpawnParticleNodeExecutor());
        nodeExecutors.put("displaytitlenode", new DisplayTitleNodeExecutor());
        nodeExecutors.put("playsoundnode", new PlaySoundNodeExecutor());
        nodeExecutors.put("scriptnode", new ScriptNodeExecutor());

        System.out.println("[BehaviorTreeExecutor] Registered " + nodeExecutors.size() + " node executors");
    }

    @Override
    public boolean canUse() {
        boolean canUse = tree != null && tree.getRootNode() != null && !entity.isDeadOrDying();
        LogHelper.debug("BehaviorTreeExecutor canUse: {} (tree: {}, rootNode: {}, dead: {})",
                canUse, tree != null, tree != null ? tree.getRootNode() != null : false, entity.isDeadOrDying());
        return canUse;
    }

    @Override
    public void start() {
        LogHelper.info("🚀 [BehaviorTreeExecutor] STARTING execution for entity {}", entity.getId());
        LogHelper.info("   Tree: {}", (tree != null ? tree.getName() : "null"));
        LogHelper.info("   Root node: {}", (tree != null && tree.getRootNode() != null ? tree.getRootNode().getType() : "null"));

        // Очищаем предыдущее состояние
        nodeStatuses.clear();
        runningNodes.clear();
        blackboard.clear();

        // Инициализируем дерево
        initializeTree();
        treeInitialized = true;
        tickCounter = 0;

        LogHelper.info("✅ [BehaviorTreeExecutor] Initialization completed for entity {}", entity.getId());
    }

    @Override
    public void tick() {
        // Логируем первые несколько тиков
        if (tickCounter < 5) {
            LogHelper.info("🔄 [BehaviorTreeExecutor] Tick #{} for entity {}", tickCounter, entity.getId());
        }

        // Обновляем с заданным интервалом
        if (++tickCounter % updateInterval != 0) {
            return;
        }

        if (!treeInitialized || tree == null) {
            LogHelper.warn("[BehaviorTreeExecutor] Tree not initialized or null for entity {}", entity.getId());
            return;
        }

        try {
            // Получаем корневой узел и выполняем его
            BehaviorNode rootNode = tree.getRootNode();
            if (rootNode != null) {
                if (tickCounter <= updateInterval * 3) { // Логируем первые 3 выполнения
                    LogHelper.info("[BehaviorTreeExecutor] Executing root node {} for entity {}",
                            rootNode.getType(), entity.getId());
                }

                NodeStatus result = executeNode(rootNode);

                if (tickCounter <= updateInterval * 3) {
                    LogHelper.info("[BehaviorTreeExecutor] Root node result: {} for entity {}", result, entity.getId());
                }

                // Если корневой узел завершился (не RUNNING), логируем
                if (result != NodeStatus.RUNNING) {
                    LogHelper.info("[BehaviorTreeExecutor] Root node completed with status: {} for entity {}",
                            result, entity.getId());
                }
            } else {
                LogHelper.error("[BehaviorTreeExecutor] Root node is null for entity {}", entity.getId());
            }

        } catch (Exception e) {
            LogHelper.error("[BehaviorTreeExecutor] Error during execution for entity {}: {}",
                    entity.getId(), e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        System.out.println("[BehaviorTreeExecutor] Stopping execution for entity " + entity.getId());

        // Останавливаем все выполняющиеся узлы
        for (String nodeId : new HashSet<>(runningNodes)) {
            stopNode(nodeId);
        }

        runningNodes.clear();
        treeInitialized = false;
    }

    /**
     * Выполняет указанный узел
     */
    public NodeStatus executeNode(BehaviorNode node) {
        if (node == null) {
            LogHelper.warn("[BehaviorTreeExecutor] Trying to execute null node");
            return NodeStatus.FAILURE;
        }

        String nodeId = node.getId();
        String nodeType = node.getType().toLowerCase();

        LogHelper.debug("[BehaviorTreeExecutor] Executing node {} of type {}", nodeId, nodeType);

        // Получаем исполнителя для типа узла
        NodeExecutor executor = nodeExecutors.get(nodeType);
        if (executor == null) {
            LogHelper.error("[BehaviorTreeExecutor] No executor found for node type: {}", nodeType);
            return NodeStatus.FAILURE;
        }

        try {
            // Выполняем узел
            NodeStatus result = executor.execute(entity, node, this);

            LogHelper.debug("[BehaviorTreeExecutor] Node {} result: {}", nodeId, result);

            // Обновляем статус
            nodeStatuses.put(nodeId, result);

            // Управляем списком выполняющихся узлов
            if (result == NodeStatus.RUNNING) {
                runningNodes.add(nodeId);
            } else {
                runningNodes.remove(nodeId);
            }

            return result;

        } catch (Exception e) {
            LogHelper.error("[BehaviorTreeExecutor] Error executing node {} of type {}: {}",
                    nodeId, nodeType, e.getMessage());
            e.printStackTrace();
            return NodeStatus.FAILURE;
        }
    }

    /**
     * Останавливает выполнение узла
     */
    public void stopNode(String nodeId) {
        runningNodes.remove(nodeId);
        nodeStatuses.remove(nodeId);
        System.out.println("[BehaviorTreeExecutor] Stopped node: " + nodeId);
    }

    /**
     * Инициализирует дерево поведения
     */
    private void initializeTree() {
        // Устанавливаем начальные значения в blackboard
        blackboard.setValue("entity_id", entity.getId());
        blackboard.setValue("spawn_time", System.currentTimeMillis());
        blackboard.setValue("tree_id", tree.getId());
        blackboard.setValue("spawn_triggered", true);

        LogHelper.info("[BehaviorTreeExecutor] Tree initialized for entity {} - {}", entity.getId(), tree.getName());
    }

    // Геттеры для исполнителей узлов
    public Blackboard getBlackboard() {
        return blackboard;
    }

    public NodeStatus getNodeStatus(String nodeId) {
        return nodeStatuses.getOrDefault(nodeId, null);
    }

    public void setNodeStatus(String nodeId, NodeStatus status) {
        nodeStatuses.put(nodeId, status);

        if (status == NodeStatus.RUNNING) {
            runningNodes.add(nodeId);
        } else {
            runningNodes.remove(nodeId);
        }
    }

    public boolean isNodeRunning(String nodeId) {
        // Проверяем как по набору runningNodes, так и по статусу
        return runningNodes.contains(nodeId) ||
                (nodeStatuses.get(nodeId) == NodeStatus.RUNNING);
    }

    public List<BehaviorNode> getChildNodes(BehaviorNode node) {
        return tree.getChildNodes(node.getId());
    }

    public CustomMobEntity getEntity() {
        return entity;
    }

    public BehaviorTree getTree() {
        return tree;
    }
}