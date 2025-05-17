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

import java.util.*;

public class BehaviorTreeExecutor extends Goal {
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

        // Регистрируем все обработчики узлов
        initializeNodeExecutors();

        // Определяем корневой узел
        BehaviorNode rootNode = tree.getRootNode();
        if (rootNode != null) {
            currentRootNodeId = rootNode.getId();
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
        executionTicks++;

        // Обновляем дерево с заданным интервалом
        if (executionTicks % executionInterval == 0) {
            executeTree();
        }
    }

    // Метод выполнения дерева поведения
    private void executeTree() {
        if (tree == null || currentRootNodeId == null) {
            return;
        }

        // Получаем корневой узел
        BehaviorNode rootNode = tree.getNode(currentRootNodeId);
        if (rootNode == null) {
            return;
        }

        // Выполняем корневой узел
        executeNode(rootNode);
    }

    // Рекурсивное выполнение узла и его потомков
    public boolean executeNode(BehaviorNode node) {
        if (node == null) {
            return false;
        }

        String nodeType = node.getType().toLowerCase();
        NodeExecutor executor = nodeExecutors.get(nodeType);

        // Если нет обработчика для этого типа узла, прерываем
        if (executor == null) {
            return false;
        }

        // Отправляем пакет о выполнении узла клиентам
        if (entity.level() instanceof ServerLevel) {
            NetworkManager.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                    new PlayBehaviorNodePacket(entity.getId(), node.getId(), node.getType())
            );
        }

        // Выполняем узел с помощью соответствующего обработчика
        return executor.execute(entity, node, this);
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