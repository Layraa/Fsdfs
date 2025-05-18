package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.common.data.AnimationMapping;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.data.BehaviorConnection;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.SaveMobDataPacket;
import com.custommobsforge.custommobsforge.common.network.packet.SaveBehaviorTreePacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class MobSaveService {
    // Локальные директории для кэширования (не изменяются)
    private static final String MOBS_DIRECTORY = "config/custommobsforge/mobs/";
    private static final String BEHAVIORS_DIRECTORY = "config/custommobsforge/behaviors/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Сохраняет конфигурацию моба
     */
    public boolean saveMob(MobConfig mobConfig) {
        try {
            // 1. Сохраняем локальную копию для кэширования
            saveLocalMobCopy(mobConfig);

            // 2. Конвертируем в MobData для отправки на сервер
            MobData mobData = convertToMobData(mobConfig);

            // 3. Отправляем пакет на сервер
            NetworkManager.INSTANCE.sendToServer(new SaveMobDataPacket(mobData));

            System.out.println("Sent mob data to server: " + mobConfig.getName());
            return true;
        } catch (Exception e) {
            System.err.println("Error saving mob configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Сохраняет конфигурацию дерева поведения
     */
    public boolean saveBehaviorTree(BehaviorTreeConfig treeConfig) {
        try {
            // 1. Сохраняем локальную копию для кэширования
            saveLocalBehaviorTreeCopy(treeConfig);

            // 2. Конвертируем в BehaviorTree для отправки на сервер
            BehaviorTree behaviorTree = convertToBehaviorTree(treeConfig);

            // 3. Отправляем пакет на сервер
            NetworkManager.INSTANCE.sendToServer(new SaveBehaviorTreePacket(behaviorTree));

            System.out.println("Sent behavior tree to server: " + treeConfig.getName());
            return true;
        } catch (Exception e) {
            System.err.println("Error saving behavior tree: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Конвертирует MobConfig в MobData для отправки на сервер
     */
    private MobData convertToMobData(MobConfig config) {
        MobData data = new MobData(config.getId().toString(), config.getName());
        data.setModelPath(config.getModelPath());
        data.setTexturePath(config.getTexturePath());
        data.setAnimationFilePath(config.getAnimationFilePath());

        // Конвертация атрибутов
        Map<String, Float> attributes = new HashMap<>();
        for (Map.Entry<String, Double> entry : config.getAttributes().entrySet()) {
            attributes.put(entry.getKey(), entry.getValue().floatValue());
        }
        data.setAttributes(attributes);

        // Конвертация анимаций
        Map<String, AnimationConfig> clientAnimations = config.getAnimationMappings();
        if (clientAnimations != null) {
            for (Map.Entry<String, AnimationConfig> entry : clientAnimations.entrySet()) {
                AnimationConfig clientConfig = entry.getValue();
                data.addAnimation(
                        entry.getKey(),
                        clientConfig.getAnimationName(),
                        clientConfig.isLoop(),
                        clientConfig.getSpeed()
                );
            }
        }

        // Если у моба есть дерево поведения, привязываем его ID
        if (config.getBehaviorTreeId() != null) {
            // Создаем пустое дерево поведения с таким же ID
            BehaviorTree tree = new BehaviorTree();
            tree.setId(config.getBehaviorTreeId().toString());
            tree.setName(config.getName() + "_behavior");
            data.setBehaviorTree(tree);
        }

        return data;
    }

    /**
     * Конвертирует BehaviorTreeConfig в BehaviorTree для отправки на сервер
     */
    private BehaviorTree convertToBehaviorTree(BehaviorTreeConfig config) {
        BehaviorTree tree = new BehaviorTree();
        tree.setId(config.getId().toString());
        tree.setName(config.getName());

        // Устанавливаем ID связанного моба, если есть
        if (config.getMobId() != null) {
            // В BehaviorTree пока нет прямого поля для mobId,
            // поэтому можем использовать имя для хранения информации о мобе
            tree.setName(config.getName() + " (Mob: " + config.getMobName() + ")");
        }

        // Конвертация узлов
        List<BehaviorNode> nodes = new ArrayList<>();
        for (NodeData nodeData : config.getNodes()) {
            BehaviorNode node = new BehaviorNode(
                    nodeData.getType(),
                    nodeData.getDescription()
            );

            // Устанавливаем ID узла, если есть
            if (nodeData.getId() != null) {
                node.setId(nodeData.getId());
            }

            node.setParameter(nodeData.getParameter());
            node.setX(nodeData.getX());
            node.setY(nodeData.getY());
            node.setExpanded(nodeData.isExpanded());

            // Копируем параметры анимации
            if (nodeData.getAnimationId() != null && !nodeData.getAnimationId().isEmpty()) {
                node.setAnimationId(nodeData.getAnimationId());
                node.setAnimationSpeed(nodeData.getAnimationSpeed());
                node.setLoopAnimation(nodeData.isLoopAnimation());
            }

            // Добавляем пользовательские параметры, если есть
            if (nodeData.getParameter() != null && !nodeData.getParameter().isEmpty()) {
                String[] params = nodeData.getParameter().split(";");
                for (String param : params) {
                    if (param.contains("=")) {
                        String[] keyValue = param.split("=", 2);
                        if (keyValue.length == 2) {
                            node.setCustomParameter(keyValue[0], keyValue[1]);
                        }
                    }
                }
            }

            nodes.add(node);
        }
        tree.setNodes(nodes);

        // Конвертация соединений
        List<BehaviorConnection> connections = new ArrayList<>();
        for (ConnectionData connData : config.getConnections()) {
            BehaviorConnection conn = new BehaviorConnection(
                    connData.getSourceNodeId(),
                    connData.getTargetNodeId()
            );

            connections.add(conn);
        }
        tree.setConnections(connections);

        return tree;
    }

    /**
     * Сохраняет локальную копию конфигурации моба для кэширования
     */
    private void saveLocalMobCopy(MobConfig mobConfig) {
        try {
            // Создание директории для конкретного моба
            String mobDirName = mobConfig.getName().toLowerCase().replace(" ", "_");
            String mobDirPath = MOBS_DIRECTORY + mobDirName + "/";
            Files.createDirectories(Paths.get(mobDirPath));

            // Создаем обычный HashMap для сохранения в нужном порядке
            Map<String, Object> orderedData = new LinkedHashMap<>();

            // 1. Имя моба
            orderedData.put("name", mobConfig.getName());

            // 2. Пути к ресурсам
            orderedData.put("model", mobConfig.getModelPath());
            orderedData.put("texture", mobConfig.getTexturePath());

            // Путь к файлу анимации - убедимся что он сохраняется
            if (mobConfig.getAnimationFilePath() != null && !mobConfig.getAnimationFilePath().isEmpty()) {
                orderedData.put("animationFile", mobConfig.getAnimationFilePath());
                System.out.println("Saving animation file path: " + mobConfig.getAnimationFilePath());
            }

            // 3. Анимации
            JSONArray animations = new JSONArray();
            for (Map.Entry<String, AnimationConfig> entry : mobConfig.getAnimationMappings().entrySet()) {
                JSONObject animation = new JSONObject();
                animation.put("action", entry.getKey());
                animation.put("animation", entry.getValue().getAnimationName());
                animation.put("loop", entry.getValue().isLoop());
                animation.put("speed", entry.getValue().getSpeed());

                if (entry.getValue().getDescription() != null && !entry.getValue().getDescription().isEmpty()) {
                    animation.put("description", entry.getValue().getDescription());
                }

                animations.put(animation);
            }
            orderedData.put("animations", animations);

            // 4. Атрибуты
            JSONObject attributes = new JSONObject();
            for (Map.Entry<String, Double> entry : mobConfig.getAttributes().entrySet()) {
                attributes.put(entry.getKey(), entry.getValue());
            }
            orderedData.put("attributes", attributes);

            // 5. ID
            orderedData.put("id", mobConfig.getId().toString());

            // 6. Дерево поведения (в самом конце)
            if (mobConfig.getBehaviorTreeId() != null) {
                orderedData.put("behaviorTreeId", mobConfig.getBehaviorTreeId().toString());
            }

            // Создаем JSON объект из упорядоченной карты
            JSONObject jsonMob = new JSONObject(orderedData);

            // Сохраняем в файл
            String mobFilePath = mobDirPath + mobConfig.getName().toLowerCase().replace(" ", "_") + ".json";
            try (FileWriter writer = new FileWriter(mobFilePath)) {
                writer.write(jsonMob.toString(2));
            }

            System.out.println("Mob configuration cached locally: " + mobFilePath);
        } catch (Exception e) {
            System.err.println("Error creating local cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Сохраняет локальную копию дерева поведения для кэширования
     */
    private void saveLocalBehaviorTreeCopy(BehaviorTreeConfig treeConfig) {
        try {
            // Создаем директорию, если она не существует
            Files.createDirectories(Paths.get(BEHAVIORS_DIRECTORY));

            // Создаем JSON объект с данными дерева поведения
            JSONObject jsonTree = new JSONObject();
            jsonTree.put("id", treeConfig.getId().toString());
            jsonTree.put("name", treeConfig.getName());

            // Добавляем информацию о связанном мобе (если есть)
            if (treeConfig.getMobId() != null) {
                jsonTree.put("mobId", treeConfig.getMobId().toString());
            }
            if (treeConfig.getMobName() != null) {
                jsonTree.put("mobName", treeConfig.getMobName());
            }

            // Добавляем узлы
            JSONArray nodesArray = new JSONArray();
            for (NodeData node : treeConfig.getNodes()) {
                JSONObject jsonNode = new JSONObject();
                jsonNode.put("id", node.getId());
                jsonNode.put("type", node.getType());
                jsonNode.put("x", node.getX());
                jsonNode.put("y", node.getY());
                jsonNode.put("description", node.getDescription());

                if (node.getParameter() != null && !node.getParameter().isEmpty()) {
                    jsonNode.put("parameter", node.getParameter());
                }

                jsonNode.put("isExpanded", node.isExpanded());

                // Добавляем информацию об анимации, если она есть
                if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
                    jsonNode.put("animationId", node.getAnimationId());
                    jsonNode.put("animationSpeed", node.getAnimationSpeed());
                    jsonNode.put("loopAnimation", node.isLoopAnimation());
                }

                nodesArray.put(jsonNode);
            }
            jsonTree.put("nodes", nodesArray);

            // Добавляем соединения
            JSONArray connectionsArray = new JSONArray();
            for (ConnectionData connection : treeConfig.getConnections()) {
                JSONObject jsonConnection = new JSONObject();
                jsonConnection.put("source", connection.getSourceNodeId());
                jsonConnection.put("target", connection.getTargetNodeId());

                connectionsArray.put(jsonConnection);
            }
            jsonTree.put("connections", connectionsArray);

            // Определяем имя файла на основе имени дерева или связанного моба
            String fileName;
            if (treeConfig.getMobName() != null && !treeConfig.getMobName().isEmpty()) {
                fileName = treeConfig.getMobName().toLowerCase().replace(" ", "_") + "_behavior.json";
            } else {
                fileName = treeConfig.getName().toLowerCase().replace(" ", "_") + ".json";
            }

            // Сохраняем в файл
            String filePath = BEHAVIORS_DIRECTORY + fileName;
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(jsonTree.toString(2)); // Красивый вывод с отступами
            }

            System.out.println("Behavior tree cached locally: " + filePath);
        } catch (Exception e) {
            System.err.println("Error saving local behavior tree: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Получить список всех сохраненных мобов
     */
    public List<MobConfig> getAllMobs() {
        try {
            createDirectories();

            List<MobConfig> mobs = new ArrayList<>();
            File mobsDir = new File(MOBS_DIRECTORY);

            if (mobsDir.exists() && mobsDir.isDirectory()) {
                File[] mobFiles = mobsDir.listFiles((dir, name) -> name.endsWith(".json"));

                if (mobFiles != null) {
                    for (File file : mobFiles) {
                        try {
                            // Извлекаем ID из имени файла
                            String mobId = file.getName().replace(".json", "");
                            MobConfig mobConfig = loadMob(mobId);

                            if (mobConfig != null) {
                                mobs.add(mobConfig);
                            }
                        } catch (Exception e) {
                            System.err.println("Error loading mob file " + file.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }

            return mobs;
        } catch (Exception e) {
            System.err.println("Error getting all mobs: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Загрузить конфигурацию моба
     */
    public MobConfig loadMob(String mobId) {
        try {
            // Конвертируем ID в имя файла
            String fileName = mobId.replace(" ", "_").toLowerCase() + ".json";
            String mobDirPath = MOBS_DIRECTORY + mobId.replace(" ", "_").toLowerCase() + "/";
            String filePath = mobDirPath + fileName;

            // Проверяем существование файла
            if (!Files.exists(Paths.get(filePath))) {
                // Пробуем старый формат
                filePath = MOBS_DIRECTORY + fileName;
                if (!Files.exists(Paths.get(filePath))) {
                    return null;
                }
            }

            // Читаем JSON файл
            String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
            JSONObject jsonMob = new JSONObject(jsonContent);

            // Создаем объект конфигурации
            MobConfig mobConfig = new MobConfig();
            mobConfig.setId(UUID.fromString(jsonMob.getString("id")));
            mobConfig.setName(jsonMob.getString("name"));
            mobConfig.setModelPath(jsonMob.getString("model"));
            mobConfig.setTexturePath(jsonMob.getString("texture"));

            // Загружаем путь к файлу анимации
            if (jsonMob.has("animationFile")) {
                mobConfig.setAnimationFilePath(jsonMob.getString("animationFile"));
            }

            // Загружаем атрибуты
            JSONObject attributes = jsonMob.getJSONObject("attributes");
            for (String key : attributes.keySet()) {
                mobConfig.getAttributes().put(key, attributes.getDouble(key));
            }

            // Загружаем анимации
            if (jsonMob.has("animations")) {
                JSONArray animations = jsonMob.getJSONArray("animations");
                Map<String, AnimationConfig> animationMappings = new HashMap<>();

                for (int i = 0; i < animations.length(); i++) {
                    JSONObject animation = animations.getJSONObject(i);
                    String action = animation.getString("action");
                    String animationName = animation.getString("animation");
                    boolean loop = animation.getBoolean("loop");
                    float speed = (float) animation.getDouble("speed");

                    AnimationConfig config = new AnimationConfig(
                            animationName, loop, speed);

                    if (animation.has("description")) {
                        config.setDescription(animation.getString("description"));
                    }

                    animationMappings.put(action, config);
                }

                mobConfig.setAnimationMappings(animationMappings);
            }

            // Загружаем информацию о дереве поведения
            if (jsonMob.has("behaviorTree")) {
                JSONObject behaviorTree = jsonMob.getJSONObject("behaviorTree");
                UUID behaviorTreeId = UUID.fromString(behaviorTree.getString("id"));
                mobConfig.setBehaviorTreeId(behaviorTreeId);
            } else if (jsonMob.has("behaviorTreeId")) {
                // Для обратной совместимости со старым форматом
                mobConfig.setBehaviorTreeId(UUID.fromString(jsonMob.getString("behaviorTreeId")));
            }

            return mobConfig;
        } catch (Exception e) {
            System.err.println("Error loading mob configuration: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Создать необходимые директории
     */
    private void createDirectories() throws IOException {
        Files.createDirectories(Paths.get(MOBS_DIRECTORY));
        Files.createDirectories(Paths.get(BEHAVIORS_DIRECTORY));
    }
}