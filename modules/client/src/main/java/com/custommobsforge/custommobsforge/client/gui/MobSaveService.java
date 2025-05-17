package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.client.gui.AnimationConfig;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.json.*;

/**
 * Сервис для сохранения и загрузки конфигураций мобов
 */
public class MobSaveService {
    // Директория для сохранения конфигураций мобов
    private static final String MOBS_DIRECTORY = "config/custommobsforge/mobs/";

    // Директория для сохранения деревьев поведения
    private static final String BEHAVIORS_DIRECTORY = "config/custommobsforge/behaviors/";

    /**
     * Сохранить конфигурацию моба
     */
    /**
     * Сохранить конфигурацию моба вместе с деревом поведения в один файл
     */
    /**
     * Сохранить конфигурацию моба
     */
    /**
     * Сохранить конфигурацию моба
     */
    /**
     * Сохранить конфигурацию моба
     */
    /**
     * Сохранить конфигурацию моба
     */
    public boolean saveMob(MobConfig mobConfig) {
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
                // Находим дерево поведения по ID
                BehaviorTreeConfig treeConfig = loadBehaviorTreeById(mobConfig.getBehaviorTreeId());

                if (treeConfig != null) {
                    // Создаем объект дерева поведения
                    JSONObject behaviorTree = new JSONObject();
                    behaviorTree.put("id", treeConfig.getId().toString());
                    behaviorTree.put("name", treeConfig.getName());

                    // Узлы дерева
                    JSONArray nodesArray = new JSONArray();
                    for (NodeData node : treeConfig.getNodes()) {
                        JSONObject nodeObj = new JSONObject();
                        nodeObj.put("id", node.getId());
                        nodeObj.put("type", node.getType());
                        nodeObj.put("description", node.getDescription());

                        if (node.getParameter() != null && !node.getParameter().isEmpty()) {
                            nodeObj.put("parameter", node.getParameter());
                        }

                        nodeObj.put("x", node.getX());
                        nodeObj.put("y", node.getY());
                        nodeObj.put("isExpanded", node.isExpanded());

                        // Добавляем информацию об анимации, если она есть
                        if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
                            nodeObj.put("animationId", node.getAnimationId());
                            nodeObj.put("animationSpeed", node.getAnimationSpeed());
                            nodeObj.put("loopAnimation", node.isLoopAnimation());
                        }

                        nodesArray.put(nodeObj);
                    }
                    behaviorTree.put("nodes", nodesArray);

                    // Соединения между узлами
                    JSONArray connectionsArray = new JSONArray();
                    for (ConnectionData connection : treeConfig.getConnections()) {
                        JSONObject connObj = new JSONObject();
                        connObj.put("source", connection.getSourceNodeId());
                        connObj.put("target", connection.getTargetNodeId());
                        connectionsArray.put(connObj);
                    }
                    behaviorTree.put("connections", connectionsArray);

                    // Добавляем дерево поведения в конец
                    orderedData.put("behaviorTree", behaviorTree);
                }
            }

            // Создаем JSON объект из упорядоченной карты
            JSONObject jsonMob = new JSONObject(orderedData);

            // Сохраняем в файл
            String mobFilePath = mobDirPath + mobConfig.getName().toLowerCase().replace(" ", "_") + ".json";
            try (FileWriter writer = new FileWriter(mobFilePath)) {
                writer.write(jsonMob.toString(2));
            }

            System.out.println("Mob configuration saved to: " + mobFilePath);
            return true;
        } catch (Exception e) {
            System.err.println("Error saving mob configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Загрузить конфигурацию дерева поведения по ID
     */
    private BehaviorTreeConfig loadBehaviorTreeById(UUID treeId) {
        try {
            File behaviorsDir = new File(BEHAVIORS_DIRECTORY);
            if (!behaviorsDir.exists() || !behaviorsDir.isDirectory()) {
                return null;
            }

            File[] behaviorFiles = behaviorsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (behaviorFiles == null) {
                return null;
            }

            for (File file : behaviorFiles) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()));
                    JSONObject json = new JSONObject(content);

                    if (json.has("id") && json.getString("id").equals(treeId.toString())) {
                        // Нашли нужное дерево, загружаем его
                        BehaviorTreeConfig config = new BehaviorTreeConfig();
                        config.setId(UUID.fromString(json.getString("id")));

                        if (json.has("name")) {
                            config.setName(json.getString("name"));
                        }

                        if (json.has("mobId")) {
                            config.setMobId(UUID.fromString(json.getString("mobId")));
                        }

                        if (json.has("mobName")) {
                            config.setMobName(json.getString("mobName"));
                        }

                        // Загружаем узлы
                        List<NodeData> nodes = new ArrayList<>();
                        if (json.has("nodes")) {
                            JSONArray nodesArray = json.getJSONArray("nodes");
                            for (int i = 0; i < nodesArray.length(); i++) {
                                JSONObject nodeObj = nodesArray.getJSONObject(i);
                                NodeData node = new NodeData();
                                node.setId(nodeObj.getString("id"));
                                node.setType(nodeObj.getString("type"));
                                node.setDescription(nodeObj.getString("description"));

                                if (nodeObj.has("parameter")) {
                                    node.setParameter(nodeObj.getString("parameter"));
                                }

                                node.setX(nodeObj.getDouble("x"));
                                node.setY(nodeObj.getDouble("y"));
                                node.setExpanded(nodeObj.getBoolean("isExpanded"));

                                // Загружаем параметры анимации, если они есть
                                if (nodeObj.has("animationId")) {
                                    node.setAnimationId(nodeObj.getString("animationId"));
                                    node.setAnimationSpeed(nodeObj.getDouble("animationSpeed"));
                                    node.setLoopAnimation(nodeObj.getBoolean("loopAnimation"));
                                }

                                nodes.add(node);
                            }
                        }
                        config.setNodes(nodes);

                        // Загружаем соединения
                        List<ConnectionData> connections = new ArrayList<>();
                        if (json.has("connections")) {
                            JSONArray connectionsArray = json.getJSONArray("connections");
                            for (int i = 0; i < connectionsArray.length(); i++) {
                                JSONObject connObj = connectionsArray.getJSONObject(i);
                                ConnectionData connection = new ConnectionData();
                                connection.setSourceNodeId(connObj.getString("source"));
                                connection.setTargetNodeId(connObj.getString("target"));
                                connections.add(connection);
                            }
                        }
                        config.setConnections(connections);

                        return config;
                    }
                } catch (Exception e) {
                    // Пропускаем файл при ошибке чтения
                    System.err.println("Error reading behavior tree file " + file.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error searching for behavior tree: " + e.getMessage());
        }

        return null;
    }

    /**
     * Загрузить конфигурацию моба вместе с деревом поведения
     */
    public MobConfig loadMobWithBehaviorTree(String mobId) {
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
            String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));
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

            // Загружаем настройки спавна
            if (jsonMob.has("spawnSettings")) {
                JSONObject spawnSettings = jsonMob.getJSONObject("spawnSettings");
                mobConfig.setCanSpawnDay(spawnSettings.getBoolean("canSpawnDay"));
                mobConfig.setCanSpawnNight(spawnSettings.getBoolean("canSpawnNight"));

                JSONArray biomes = spawnSettings.getJSONArray("biomes");
                List<String> spawnBiomes = new ArrayList<>();
                for (int i = 0; i < biomes.length(); i++) {
                    spawnBiomes.add(biomes.getString(i));
                }
                mobConfig.setSpawnBiomes(spawnBiomes);
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

                // Здесь можно дополнительно загрузить дерево поведения если нужно
            } else if (jsonMob.has("behaviorTreeId")) {
                mobConfig.setBehaviorTreeId(UUID.fromString(jsonMob.getString("behaviorTreeId")));
            }

            return mobConfig;
        } catch (Exception e) {
            System.err.println("Error loading mob configuration: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Вспомогательный метод для поиска дерева поведения по id моба
    private BehaviorTreeConfig findBehaviorTreeByMobId(UUID mobId) {
        try {
            File behaviorsDir = new File(BEHAVIORS_DIRECTORY);

            if (!behaviorsDir.exists() || !behaviorsDir.isDirectory()) {
                return null;
            }

            File[] behaviorFiles = behaviorsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (behaviorFiles == null) {
                return null;
            }

            for (File file : behaviorFiles) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()));
                    JSONObject json = new JSONObject(content);

                    if (json.has("mobId") && json.getString("mobId").equals(mobId.toString())) {
                        return loadBehaviorTree(file.getName().replace(".json", ""));
                    }
                } catch (Exception e) {
                    // Пропускаем файл при ошибке чтения
                    continue;
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding behavior tree for mob: " + e.getMessage());
        }

        return null;
    }

    // Выделяем копирование дерева поведения в отдельный метод для лучшей организации
    private void copyBehaviorTreeToMobDirectory(MobConfig mobConfig, String mobDirPath) {
        if (mobConfig.getBehaviorTreeId() == null) {
            return;
        }

        try {
            // Находим файл дерева поведения в общей директории
            String treePath = BEHAVIORS_DIRECTORY + mobConfig.getName().toLowerCase().replace(" ", "_") + "_behavior.json";
            File treeFile = new File(treePath);

            if (treeFile.exists()) {
                // Читаем содержимое
                String treeContent = new String(Files.readAllBytes(treeFile.toPath()));

                // Сохраняем копию в директорию моба с именем, соответствующим имени моба
                String mobTreePath = mobDirPath + mobConfig.getName().toLowerCase().replace(" ", "_") + "_behavior.json";
                Files.write(Paths.get(mobTreePath), treeContent.getBytes());

                System.out.println("Behavior tree copied to mob directory: " + mobTreePath);
            }
        } catch (Exception e) {
            System.err.println("Failed to copy behavior tree to mob directory: " + e.getMessage());
        }
    }

    /**
     * Загрузить конфигурацию моба
     */
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
            String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));
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
     * Удалить конфигурацию моба
     */
    public boolean deleteMob(UUID mobId) {
        try {
            List<MobConfig> mobs = getAllMobs();

            for (MobConfig mob : mobs) {
                if (mob.getId().equals(mobId)) {
                    String fileName = mob.getName().toLowerCase().replace(" ", "_") + ".json";
                    String filePath = MOBS_DIRECTORY + fileName;

                    Files.deleteIfExists(Paths.get(filePath));
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            System.err.println("Error deleting mob: " + e.getMessage());
            return false;
        }
    }

    /**
     * Создать необходимые директории
     */
    private void createDirectories() throws IOException {
        Files.createDirectories(Paths.get(MOBS_DIRECTORY));
        Files.createDirectories(Paths.get(BEHAVIORS_DIRECTORY));
    }

    // В методе saveBehaviorTree(BehaviorTreeConfig treeConfig)
    public boolean saveBehaviorTree(BehaviorTreeConfig treeConfig) {
        try {
            // Создаем директорию, если она не существует
            createDirectories();

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

            System.out.println("Behavior tree saved to: " + filePath);
            return true;
        } catch (Exception e) {
            System.err.println("Error saving behavior tree: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // В методе loadBehaviorTree(String treeId)
    public BehaviorTreeConfig loadBehaviorTree(String treeId) {
        try {
            // Конвертируем ID в имя файла
            String fileName = treeId.replace(" ", "_").toLowerCase() + ".json";
            String filePath = BEHAVIORS_DIRECTORY + fileName;

            // Проверяем существование файла
            if (!Files.exists(Paths.get(filePath))) {
                return null;
            }

            // Читаем JSON файл
            String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));
            JSONObject jsonTree = new JSONObject(jsonContent);

            // Создаем объект конфигурации дерева
            BehaviorTreeConfig treeConfig = new BehaviorTreeConfig();
            treeConfig.setId(UUID.fromString(jsonTree.getString("id")));
            treeConfig.setName(jsonTree.getString("name"));

            // Загружаем узлы
            List<NodeData> nodes = new ArrayList<>();
            JSONArray nodesArray = jsonTree.getJSONArray("nodes");

            for (int i = 0; i < nodesArray.length(); i++) {
                JSONObject jsonNode = nodesArray.getJSONObject(i);

                NodeData node = new NodeData();
                node.setId(jsonNode.getString("id"));
                node.setType(jsonNode.getString("type"));
                node.setX(jsonNode.getDouble("x"));
                node.setY(jsonNode.getDouble("y"));
                node.setDescription(jsonNode.getString("description"));

                if (jsonNode.has("parameter")) {
                    node.setParameter(jsonNode.getString("parameter"));
                }

                node.setExpanded(jsonNode.getBoolean("isExpanded"));

                // Загружаем параметры анимации, если они есть
                if (jsonNode.has("animationId")) {
                    node.setAnimationId(jsonNode.getString("animationId"));
                    node.setAnimationSpeed(jsonNode.getDouble("animationSpeed"));
                    node.setLoopAnimation(jsonNode.getBoolean("loopAnimation"));
                }

                nodes.add(node);
            }
            treeConfig.setNodes(nodes);

            // Загружаем соединения
            List<ConnectionData> connections = new ArrayList<>();
            JSONArray connectionsArray = jsonTree.getJSONArray("connections");

            for (int i = 0; i < connectionsArray.length(); i++) {
                JSONObject jsonConnection = connectionsArray.getJSONObject(i);

                ConnectionData connection = new ConnectionData();
                connection.setSourceNodeId(jsonConnection.getString("source"));
                connection.setTargetNodeId(jsonConnection.getString("target"));

                connections.add(connection);
            }
            treeConfig.setConnections(connections);

            return treeConfig;
        } catch (Exception e) {
            System.err.println("Error loading behavior tree: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}