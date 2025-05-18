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
    private int executionTicks = 0;
    private final int executionInterval = 5; // Обновление каждые 5 тиков (1/4 секунды)

    // Конструктор
    public BehaviorTreeExecutor(CustomMobEntity entity, BehaviorTree tree) {

        this.entity = entity;
        this.tree = tree;

        System.out.println("BehaviorTreeExecutor: Created for entity " + entity.getId() +
                " with tree ID: " + (tree != null ? tree.getId() : "null"));

        if (tree == null) {
            System.out.println("BehaviorTreeExecutor: WARNING - Tree is null!");
        } else if (tree.getNodes() == null || tree.getNodes().isEmpty()) {
            System.out.println("BehaviorTreeExecutor: WARNING - Tree has no nodes!");
        }

        // Регистрируем все обработчики узлов
        initializeNodeExecutors();

        // Определяем корневой узел
        BehaviorNode rootNode = tree != null ? tree.getRootNode() : null;
        if (rootNode != null) {
            currentRootNodeId = rootNode.getId();
            System.out.println("BehaviorTreeExecutor: Root node set to " + rootNode.getId() +
                    " of type " + rootNode.getType());
        } else {
            System.out.println("BehaviorTreeExecutor: WARNING - No root node found!");
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
        // Ничего не делаем при запуске
    }

    @Override
    public void stop() {
        // Очищаем активные узлы
        activeNodeIds.clear();
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
            boolean success = executeTree();
            LOGGER.info("BehaviorTreeExecutor: Tree execution result: {}", success);
        }
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

        // Выполняем корневой узел
        return executeNode(rootNode);
    }

    // Рекурсивное выполнение узла и его потомков
    public boolean executeNode(BehaviorNode node) {
        if (node == null) {
            LOGGER.error("BehaviorTreeExecutor: Node is null, cannot execute");
            return false;
        }

        String nodeType = node.getType().toLowerCase();
        NodeExecutor executor = nodeExecutors.get(nodeType);

        // Если нет обработчика для этого типа узла, прерываем
        if (executor == null) {
            LOGGER.error("BehaviorTreeExecutor: No executor found for node type: {}", nodeType);
            LOGGER.info("Available node types: {}", String.join(", ", nodeExecutors.keySet()));
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

        return result;
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