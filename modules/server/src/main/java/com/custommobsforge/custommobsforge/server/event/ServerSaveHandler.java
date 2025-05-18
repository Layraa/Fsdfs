package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.event.SaveMobDataEvent;
import com.custommobsforge.custommobsforge.common.event.SaveBehaviorTreeEvent;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber
public class ServerSaveHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SubscribeEvent
    public static void onSaveMobData(SaveMobDataEvent event) {
        // ДОБАВЛЕНО: Больше логирования
        System.out.println("Server received SaveMobDataEvent for mob: " + event.getMobData().getName() +
                " (ID: " + event.getMobData().getId() + ") from player: " + event.getPlayer().getName().getString());

        ServerLevel level = (ServerLevel) event.getPlayer().level();
        MobData mobData = event.getMobData();

        // Сохраняем данные моба на сервере
        saveMobData(mobData, level);
    }

    @SubscribeEvent
    public static void onSaveBehaviorTree(SaveBehaviorTreeEvent event) {
        // ДОБАВЛЕНО: Больше логирования
        System.out.println("Server received SaveBehaviorTreeEvent for tree: " + event.getBehaviorTree().getName() +
                " (ID: " + event.getBehaviorTree().getId() + ") from player: " + event.getPlayer().getName().getString());

        ServerPlayer player = event.getPlayer();
        ServerLevel level = (ServerLevel) player.level();
        BehaviorTree tree = event.getBehaviorTree();

        // Сохраняем дерево поведения на сервере
        saveBehaviorTree(tree, level);
    }

    // Метод для сохранения данных моба на сервере
    private static void saveMobData(MobData mobData, ServerLevel level) {
        try {
            // ДОБАВЛЕНО: Логирование перед сохранением
            System.out.println("Saving mob data to server for: " + mobData.getName() + " (ID: " + mobData.getId() + ")");

            // Вызываем метод из MobConfigManager для сохранения на сервере
            MobConfigManager.saveMobConfig(mobData, level);

            // ИЗМЕНЕНО: Добавили больше информации в лог
            String worldPath = level.getServer().getWorldPath(LevelResource.ROOT).toString();
            System.out.println("Mob data successfully saved to server at: " + worldPath + "/custommobsforge/mobs/" + mobData.getId() + ".json");
        } catch (Exception e) {
            System.err.println("Error saving mob data on server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Метод для сохранения дерева поведения на сервере
    private static void saveBehaviorTree(BehaviorTree tree, ServerLevel level) {
        try {
            // ДОБАВЛЕНО: Логирование перед сохранением
            System.out.println("Saving behavior tree to server for: " + tree.getName() + " (ID: " + tree.getId() + ")");

            // Здесь нужно добавить метод в MobConfigManager для сохранения дерева
            // Для примера создадим собственную реализацию
            saveBehaviorTreeToServer(tree, level);

            // ИЗМЕНЕНО: Добавили больше информации в лог
            String worldPath = level.getServer().getWorldPath(LevelResource.ROOT).toString();
            System.out.println("Behavior tree successfully saved to server at: " + worldPath + "/custommobsforge/behaviors/" + tree.getId() + ".json");
        } catch (Exception e) {
            System.err.println("Error saving behavior tree on server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Вспомогательный метод для сохранения дерева поведения
    private static void saveBehaviorTreeToServer(BehaviorTree tree, ServerLevel level) {
        try {
            // Создаем директорию для хранения деревьев поведения
            Path behaviorDir = level.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("custommobsforge").resolve("behaviors");

            Files.createDirectories(behaviorDir);

            // Создаем файл с именем, основанным на ID дерева
            Path filePath = behaviorDir.resolve(tree.getId() + ".json");

            // Сериализуем дерево в JSON и сохраняем
            String json = GSON.toJson(tree);
            Files.write(filePath, json.getBytes(StandardCharsets.UTF_8));

            // ДОБАВЛЕНО: Подробное логирование о сохранении файла
            System.out.println("Behavior tree saved to file: " + filePath.toString() + " (Size: " + json.length() + " bytes)");
        } catch (Exception e) {
            System.err.println("Error saving behavior tree file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}