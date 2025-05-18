package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorConnection;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.PlayBehaviorNodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;

public class BehaviorTreeExecutor extends Goal {
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

    // Отслеживание выполнения последовательностей
    private final Map<String, List<BehaviorNode>> pendingSequenceNodes = new HashMap<>();
    private final Map<String, Integer> sequenceNodeIndex = new HashMap<>();

    // Отслеживание состояния исполнения
    private boolean isExecutingSequence = false;
    private boolean treeCompleted = false;
    private long lastTreeCompletionTime = 0;
    private static final long TREE_RESTART_DELAY = 500; // Уменьшено с 1000 до 500 мс для более быстрого рестарта

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
    }

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

    public void clearNodeStatus(String nodeId) {
        // Очищаем статус узла, если что-то пошло не так
        // Это поможет избежать застреваний в деревьях поведения
        nodeNeedsMoreTime = false;
    }

    // Метод выполнения дерева поведения
    private boolean executeTree() {
        if (tree == null || currentRootNodeId == null) {
            LOGGER.error("BehaviorTreeExecutor: Cannot execute tree - {} for entity {}",
                    (tree == null ? "tree is null" : "root node ID is null"), entity.getId());
            return false;
        }

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

        // Если индекс выходит за границы, удаляем последовательность
        if (index >= sequence.size()) {
            pendingSequenceNodes.remove(sequenceId);
            sequenceNodeIndex.remove(sequenceId);
            LOGGER.info("BehaviorTreeExecutor: Sequence {} completed", sequenceId);

            // Проверяем, остались ли еще последовательности
            if (pendingSequenceNodes.isEmpty()) {
                isExecutingSequence = false;
                treeCompleted = true;
                lastTreeCompletionTime = System.currentTimeMillis();
                LOGGER.info("BehaviorTreeExecutor: All sequences completed, tree execution finished");
            } else {
                // Если есть еще последовательности, будем выполнять их в следующем тике
                LOGGER.info("BehaviorTreeExecutor: Moving to next sequence, {} remaining",
                        pendingSequenceNodes.size());
            }
            return;
        }

        // Получаем следующий узел для выполнения
        BehaviorNode nextNode = sequence.get(index);
        LOGGER.info("BehaviorTreeExecutor: Continuing sequence {}, executing node {} of type {}",
                sequenceId, nextNode.getId(), nextNode.getType());

        // Устанавливаем текущий выполняемый узел
        currentExecutingNodeId = nextNode.getId();

        // Выполняем узел
        boolean result = executeNode(nextNode);
        LOGGER.info("BehaviorTreeExecutor: Node execution result: {}", result);

        // Обновляем индекс или удаляем последовательность в зависимости от результата
        if (result) {
            // Если узел требует больше времени, оставляем индекс как есть
            if (!nodeNeedsMoreTime) {
                sequenceNodeIndex.put(sequenceId, index + 1);
                LOGGER.info("BehaviorTreeExecutor: Moving to next node in sequence, index now {}", index + 1);
            }
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

    // Выполнение узла
    public boolean executeNode(BehaviorNode node) {
        if (node == null) {
            LOGGER.error("BehaviorTreeExecutor: Node is null, cannot execute");
            nodeNeedsMoreTime = false;
            return false;
        }

        String nodeType = node.getType().toLowerCase();
        NodeExecutor executor = nodeExecutors.get(nodeType);

        // Если нет обработчика для этого типа узла, прерываем
        if (executor == null) {
            LOGGER.error("BehaviorTreeExecutor: No executor found for node type: {}", nodeType);
            LOGGER.info("Available node types: {}", String.join(", ", nodeExecutors.keySet()));
            nodeNeedsMoreTime = false;
            return false;
        }

        LOGGER.info("BehaviorTreeExecutor: Executing node {} of type {} with description: {}",
                node.getId(), node.getType(), node.getDescription());

        // Отправляем пакет о выполнении узла клиентам
        if (entity.level() instanceof ServerLevel) {
            try {
                NetworkManager.INSTANCE.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                        new PlayBehaviorNodePacket(entity.getId(), node.getId(), node.getType())
                );
                LOGGER.info("BehaviorTreeExecutor: Sent PlayBehaviorNodePacket to clients for node {}", node.getId());
            } catch (Exception e) {
                LOGGER.error("BehaviorTreeExecutor: Error sending PlayBehaviorNodePacket: {}", e.getMessage());
            }
        }

        // Выполняем узел с помощью соответствующего обработчика
        boolean result = executor.execute(entity, node, this);
        LOGGER.info("BehaviorTreeExecutor: Node {} execution result: {}", node.getId(), result);

        // Если это SequenceNode, который вернул true,
        // сохраняем дочерние узлы для последующего выполнения
        if (result && nodeType.equals("sequencenode")) {
            List<BehaviorNode> children = getChildNodes(node);
            if (!children.isEmpty()) {
                pendingSequenceNodes.put(node.getId(), children);
                sequenceNodeIndex.put(node.getId(), 0);
                isExecutingSequence = true;
                LOGGER.info("BehaviorTreeExecutor: Created pending sequence for node {} with {} children",
                        node.getId(), children.size());

                // В следующем тике начнем выполнение этой последовательности
                // Не запускаем сразу, чтобы избежать рекурсии
                return true;
            }
        }

        return result;
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