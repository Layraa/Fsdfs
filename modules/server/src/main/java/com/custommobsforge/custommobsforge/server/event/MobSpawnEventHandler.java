package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.common.data.BehaviorConnection;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.server.ai.BehaviorTreeExecutor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber
public class MobSpawnEventHandler {

    // Добавляем GSON для десериализации
    private static final Gson GSON = new GsonBuilder().create();

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        // Инициализируем менеджер конфигураций при первом событии спавна
        if (event.getLevel() instanceof ServerLevel && !event.getLevel().isClientSide) {
            // Инициализируем менеджер конфигураций
            if (event.getEntity() instanceof LivingEntity) {
                MobConfigManager.init((ServerLevel) event.getLevel());
            }
        }

        // Настраиваем кастомного моба при появлении в мире
        if (event.getEntity() instanceof CustomMobEntity && event.getLevel() instanceof ServerLevel) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();
            ServerLevel level = (ServerLevel) event.getLevel();

            // Расширенное логирование
            System.out.println("MobSpawnEventHandler: CustomMobEntity joined world - ID: " + entity.getId() +
                    ", mobId: " + entity.getMobId() +
                    ", hasData: " + (entity.getMobData() != null));

            // Если у моба уже есть данные, пропускаем
            if (entity.getMobData() != null) {
                System.out.println("MobSpawnEventHandler: Entity already has mob data, skipping configuration");
                return;
            }

            // Получаем ID моба
            String mobId = entity.getMobId();
            if (mobId == null || mobId.isEmpty()) {
                System.out.println("MobSpawnEventHandler: Entity has no mob ID, skipping configuration");
                return;
            }

            // Загружаем данные моба
            MobData mobData = MobConfigManager.loadMobConfig(mobId, level);
            if (mobData == null) {
                System.out.println("MobSpawnEventHandler: Could not load mob data for ID: " + mobId);
                return;
            }

            // Устанавливаем данные
            System.out.println("MobSpawnEventHandler: Setting mob data for entity " + entity.getId() +
                    " with model: " + mobData.getModelPath() +
                    ", texture: " + mobData.getTexturePath());
            entity.setMobData(mobData);

            // Если у моба есть дерево поведения, добавляем соответствующую цель
            if (mobData.getBehaviorTree() != null) {
                System.out.println("MobSpawnEventHandler: Entity " + entity.getId() +
                        " has behavior tree with ID: " + mobData.getBehaviorTree().getId());

                // Проверяем, есть ли узлы в дереве
                if (mobData.getBehaviorTree().getNodes() != null &&
                        !mobData.getBehaviorTree().getNodes().isEmpty()) {
                    System.out.println("MobSpawnEventHandler: Behavior tree has " +
                            mobData.getBehaviorTree().getNodes().size() + " nodes");
                } else {
                    System.out.println("MobSpawnEventHandler: Behavior tree has NO nodes!");
                }

                // Загружаем полное дерево поведения по ID
                BehaviorTree fullTree = loadBehaviorTreeFromServer(mobData.getBehaviorTree().getId(), level);
                if (fullTree != null) {
                    // Проверяем полноту дерева
                    if (fullTree.getNodes() != null && !fullTree.getNodes().isEmpty()) {
                        System.out.println("MobSpawnEventHandler: Loaded full tree with " +
                                fullTree.getNodes().size() + " nodes and " +
                                fullTree.getConnections().size() + " connections");

                        // Выводим все узлы дерева для отладки
                        for (BehaviorNode node : fullTree.getNodes()) {
                            System.out.println("  - Node: " + node.getId() + " (" + node.getType() +
                                    "): " + node.getDescription());

                            // Проверяем параметры узла, особенно для PlayAnimationNode
                            if (node.getType().equalsIgnoreCase("PlayAnimationNode")) {
                                System.out.println("    Animation ID: " + node.getAnimationId());
                                System.out.println("    Animation from parameters: " +
                                        node.getCustomParameterAsString("animation", "NONE"));

                                // Для интереса: какие анимации доступны?
                                if (mobData.getAnimations() != null) {
                                    System.out.println("    Available animations in mob data:");
                                    for (java.util.Map.Entry<String, com.custommobsforge.custommobsforge.common.data.AnimationMapping> entry :
                                            mobData.getAnimations().entrySet()) {
                                        System.out.println("      " + entry.getKey() + " -> " +
                                                entry.getValue().getAnimationName());
                                    }
                                }
                            }
                        }

                        // Выводим все соединения
                        for (BehaviorConnection conn : fullTree.getConnections()) {
                            System.out.println("  - Connection: " + conn.getSourceNodeId() +
                                    " -> " + conn.getTargetNodeId());
                        }
                    } else {
                        System.out.println("MobSpawnEventHandler: Loaded tree has NO nodes!");
                    }

                    // Заменяем пустое дерево на полное
                    mobData.setBehaviorTree(fullTree);
                } else {
                    System.out.println("MobSpawnEventHandler: Failed to load behavior tree from server for ID: " +
                            mobData.getBehaviorTree().getId());
                }

                // Добавляем поведение
                BehaviorTreeExecutor executor = new BehaviorTreeExecutor(entity, mobData.getBehaviorTree());
                entity.goalSelector.addGoal(1, executor);
                System.out.println("MobSpawnEventHandler: Added behavior tree executor for entity " + entity.getId());

                // Проверим, действительно ли добавлен исполнитель
                boolean found = false;
                for (Goal goal : entity.goalSelector.getAvailableGoals()) {
                    if (goal instanceof BehaviorTreeExecutor) {
                        found = true;
                        break;
                    }
                }
                System.out.println("MobSpawnEventHandler: BehaviorTreeExecutor found in goals: " + found);
            }

            // Воспроизводим анимацию появления
            entity.playAnimation("SPAWN");
        }
    }

    /**
     * Загружает полное дерево поведения по ID с сервера
     */
    private static BehaviorTree loadBehaviorTreeFromServer(String treeId, ServerLevel level) {
        try {
            // Путь к файлу дерева поведения
            Path behaviorFile = level.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("custommobsforge").resolve("behaviors").resolve(treeId + ".json");

            if (Files.exists(behaviorFile)) {
                // Читаем содержимое файла
                String json = new String(Files.readAllBytes(behaviorFile), StandardCharsets.UTF_8);

                // Выводим часть содержимого для отладки
                System.out.println("MobSpawnEventHandler: Loaded behavior tree file, length: " +
                        json.length() + " bytes, starting with: " +
                        json.substring(0, Math.min(100, json.length())) + "...");

                // Десериализуем дерево поведения
                return GSON.fromJson(json, BehaviorTree.class);
            } else {
                System.out.println("MobSpawnEventHandler: Behavior tree file not found: " + behaviorFile);
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error loading behavior tree: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Обрабатываем события получения урона для кастомных мобов
        if (event.getEntity() instanceof CustomMobEntity) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();

            // Воспроизводим анимацию получения урона
            entity.playAnimation("HURT");
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Обрабатываем события смерти для кастомных мобов
        if (event.getEntity() instanceof CustomMobEntity) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();

            // Воспроизводим анимацию смерти
            entity.playAnimation("DEATH");
        }
    }
}