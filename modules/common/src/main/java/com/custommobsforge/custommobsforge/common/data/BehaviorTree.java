package com.custommobsforge.custommobsforge.common.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BehaviorTree {
    private String id;
    private String name;
    private List<BehaviorNode> nodes = new ArrayList<>();
    private List<BehaviorConnection> connections = new ArrayList<>();

    // Конструкторы
    public BehaviorTree() {
        // Пустой конструктор для Gson
        this.id = UUID.randomUUID().toString();
    }

    public BehaviorTree(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<BehaviorNode> getNodes() { return nodes; }
    public void setNodes(List<BehaviorNode> nodes) { this.nodes = nodes; }

    public List<BehaviorConnection> getConnections() { return connections; }
    public void setConnections(List<BehaviorConnection> connections) { this.connections = connections; }

    // Методы для работы с деревом
    public void addNode(BehaviorNode node) {
        nodes.add(node);
    }

    public void removeNode(String nodeId) {
        nodes.removeIf(node -> node.getId().equals(nodeId));
        // Также удаляем все связи с этим узлом
        connections.removeIf(conn ->
                conn.getSourceNodeId().equals(nodeId) || conn.getTargetNodeId().equals(nodeId));
    }

    public void addConnection(BehaviorConnection connection) {
        connections.add(connection);
    }

    public void removeConnection(String sourceId, String targetId) {
        connections.removeIf(conn ->
                conn.getSourceNodeId().equals(sourceId) && conn.getTargetNodeId().equals(targetId));
    }

    public BehaviorNode getNode(String nodeId) {
        for (BehaviorNode node : nodes) {
            if (node.getId().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    public List<BehaviorNode> getChildNodes(String nodeId) {
        List<BehaviorNode> children = new ArrayList<>();

        for (BehaviorConnection conn : connections) {
            if (conn.getSourceNodeId().equals(nodeId)) {
                BehaviorNode targetNode = getNode(conn.getTargetNodeId());
                if (targetNode != null) {
                    children.add(targetNode);
                }
            }
        }

        return children;
    }

    public BehaviorNode getRootNode() {
        // Находим корневой узел (не имеет входящих соединений)
        for (BehaviorNode node : nodes) {
            boolean isTargetNode = false;
            for (BehaviorConnection conn : connections) {
                if (conn.getTargetNodeId().equals(node.getId())) {
                    isTargetNode = true;
                    break;
                }
            }

            if (!isTargetNode) {
                return node;
            }
        }

        // Если нет явного корня, берем первый узел
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    // Методы сериализации
    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUtf(id);
        buffer.writeUtf(name != null ? name : "");

        // Записываем узлы
        buffer.writeInt(nodes.size());
        for (BehaviorNode node : nodes) {
            node.writeToBuffer(buffer);
        }

        // Записываем соединения
        buffer.writeInt(connections.size());
        for (BehaviorConnection conn : connections) {
            conn.writeToBuffer(buffer);
        }
    }

    public static BehaviorTree readFromBuffer(FriendlyByteBuf buffer) {
        BehaviorTree tree = new BehaviorTree();
        tree.id = buffer.readUtf();
        tree.name = buffer.readUtf();

        // Читаем узлы
        int nodeCount = buffer.readInt();
        for (int i = 0; i < nodeCount; i++) {
            tree.nodes.add(BehaviorNode.readFromBuffer(buffer));
        }

        // Читаем соединения
        int connCount = buffer.readInt();
        for (int i = 0; i < connCount; i++) {
            tree.connections.add(BehaviorConnection.readFromBuffer(buffer));
        }

        return tree;
    }
}