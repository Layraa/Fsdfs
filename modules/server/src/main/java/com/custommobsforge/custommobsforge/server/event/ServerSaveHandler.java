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
        ServerLevel level = (ServerLevel) event.getPlayer().level();
        MobData mobData = event.getMobData();

        // Сохраняем данные моба на сервере
        saveMobData(mobData, level);
    }

    @SubscribeEvent
    public static void onSaveBehaviorTree(SaveBehaviorTreeEvent event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel level = (ServerLevel) player.level(); // ← исправлено здесь
        BehaviorTree tree = event.getBehaviorTree();

        // Сохраняем дерево поведения на сервере
        saveBehaviorTree(tree, level);
    }

    // Метод для сохранения данных моба на сервере
    private static void saveMobData(MobData mobData, ServerLevel level) {
        try {
            // Вызываем метод из MobConfigManager для сохранения на сервере
            MobConfigManager.saveMobConfig(mobData, level);

            System.out.println("Mob data saved to server: " + mobData.getName());
        } catch (Exception e) {
            System.err.println("Error saving mob data on server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Метод для сохранения дерева поведения на сервере
    private static void saveBehaviorTree(BehaviorTree tree, ServerLevel level) {
        try {
            // Здесь нужно добавить метод в MobConfigManager для сохранения дерева
            // Для примера создадим собственную реализацию
            saveBehaviorTreeToServer(tree, level);

            System.out.println("Behavior tree saved to server: " + tree.getName());
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

            System.out.println("Behavior tree saved to: " + filePath);
        } catch (Exception e) {
            System.err.println("Error saving behavior tree file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}