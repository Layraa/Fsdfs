package com.custommobsforge.custommobsforge.client.gui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Сервис для загрузки и управления моделями
 */
public class ModelLoaderService {
    // Кэш загруженных моделей
    private Map<String, ModelInfo> modelCache = new HashMap<>();

    // Пути для поиска
    private static final String[] SEARCH_PATHS = {
            "src/main/resources/assets/custommobsforge/",
            "resources/assets/custommobsforge/",
            "assets/custommobsforge/",
            "./assets/custommobsforge/",
            "../assets/custommobsforge/"
    };

    /**
     * Получить список всех доступных моделей
     */
    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();

        // Поиск файлов .geo.json во всех SEARCH_PATHS
        for (String basePath : SEARCH_PATHS) {
            String geoPath = basePath + "geo/";
            File directory = new File(geoPath);

            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles((dir, name) -> name.endsWith(".geo.json"));
                if (files != null) {
                    System.out.println("Found " + files.length + " model files in " + geoPath);
                    for (File file : files) {
                        // Формируем путь, который соответствует формату "assets/custommobsforge/geo/filename.geo.json"
                        String path = "assets/custommobsforge/geo/" + file.getName();
                        models.add(path);
                        System.out.println("Added model: " + path);
                    }
                    if (files.length > 0) {
                        break; // Если нашли файлы, прекращаем поиск в других директориях
                    }
                }
            } else {
                System.out.println("Directory does not exist or is not a directory: " + geoPath);
            }
        }

        // Если не нашли файлы, добавляем заглушки (файлы из вашего скриншота)
        if (models.isEmpty()) {
            System.out.println("No models found, adding default examples");
            models.add("assets/custommobsforge/geo/custom_mob.geo.json");
            models.add("assets/custommobsforge/geo/god.geo.json");
            models.add("assets/custommobsforge/geo/overlord.geo.json");
        }

        return models;
    }

    /**
     * Получить список текстур для указанной модели
     */
    public List<String> getTexturesForModel(String modelPath) {
        List<String> textures = new ArrayList<>();

        try {
            // Извлекаем базовое имя модели
            String baseName = extractModelBaseName(modelPath);
            System.out.println("Looking for textures for model base name: " + baseName);

            // Проверяем все пути для поиска текстур
            for (String basePath : SEARCH_PATHS) {
                String texturePath = basePath + "textures/entity/";
                File directory = new File(texturePath);

                if (directory.exists() && directory.isDirectory()) {
                    // Ищем все PNG файлы
                    File[] files = directory.listFiles((dir, name) -> name.endsWith(".png"));
                    if (files != null) {
                        System.out.println("Found " + files.length + " texture files in " + texturePath);
                        for (File file : files) {
                            // Формируем путь в формате "assets/custommobsforge/textures/entity/filename.png"
                            String path = "assets/custommobsforge/textures/entity/" + file.getName();
                            textures.add(path);
                            System.out.println("Added texture: " + path);
                        }
                        if (files.length > 0) {
                            break; // Если нашли файлы, прекращаем поиск в других директориях
                        }
                    }
                } else {
                    System.out.println("Directory does not exist or is not a directory: " + texturePath);
                }
            }

            // Если не нашли текстуры, добавляем заглушки на основе имени модели
            if (textures.isEmpty()) {
                System.out.println("No textures found, adding default examples for " + baseName);
                textures.add("assets/custommobsforge/textures/entity/" + baseName + ".png");
            }

        } catch (Exception e) {
            System.err.println("Error getting textures for model: " + e.getMessage());
            e.printStackTrace();
        }

        return textures;
    }

    /**
     * Извлечь базовое имя модели из пути
     */
    private String extractModelBaseName(String modelPath) {
        // Получаем только имя файла из пути
        String fileName = modelPath;
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }

        // Удаляем расширение и дополнительные части
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.indexOf("."));
        }

        if (fileName.contains("_geo")) {
            fileName = fileName.replace("_geo", "");
        }

        if (fileName.contains(".geo")) {
            fileName = fileName.replace(".geo", "");
        }

        return fileName;
    }

    /**
     * Загрузить модель
     */
    public ModelInfo loadModel(String modelPath) {
        if (modelCache.containsKey(modelPath)) {
            return modelCache.get(modelPath);
        }

        try {
            // Создаем объект с информацией о модели
            ModelInfo modelInfo = new ModelInfo();
            modelInfo.setPath(modelPath);
            modelInfo.setName(extractModelBaseName(modelPath));

            // Получаем и устанавливаем доступные текстуры
            modelInfo.setAvailableTextures(getTexturesForModel(modelPath));

            // Кэшируем модель
            modelCache.put(modelPath, modelInfo);

            return modelInfo;
        } catch (Exception e) {
            System.err.println("Error loading model: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Класс для хранения информации о модели
     */
    public static class ModelInfo {
        private String path;
        private String name;
        private List<String> availableTextures = new ArrayList<>();

        // Геттеры и сеттеры
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getAvailableTextures() { return availableTextures; }
        public void setAvailableTextures(List<String> availableTextures) { this.availableTextures = availableTextures; }
    }
}