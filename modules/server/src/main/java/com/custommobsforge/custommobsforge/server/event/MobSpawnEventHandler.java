package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import com.custommobsforge.custommobsforge.common.data.*;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber
public class MobSpawnEventHandler {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
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
        if (event.getEntity() instanceof CustomMobEntity && !event.getLevel().isClientSide) {
            CustomMobEntity entity = (CustomMobEntity) event.getEntity();
            ServerLevel level = (ServerLevel) event.getLevel();

            // Расширенное логирование
            LOGGER.info("!!! MobSpawnEventHandler: CustomMobEntity joined world - ID: {}, mobId: {}, hasData: {}",
                    entity.getId(), entity.getMobId(), (entity.getMobData() != null));

            // Если у моба уже есть данные, пропускаем загрузку данных моба
            if (entity.getMobData() == null) {
                // Получаем ID моба
                String mobId = entity.getMobId();
                if (mobId == null || mobId.isEmpty()) {
                    LOGGER.warn("!!! MobSpawnEventHandler: Entity has no mob ID, skipping configuration");
                    return;
                }

                // Загружаем данные моба
                MobData mobData = MobConfigManager.loadMobConfig(mobId, level);
                if (mobData == null) {
                    LOGGER.error("!!! MobSpawnEventHandler: Could not load mob data for ID: {}", mobId);
                    return;
                }

                // Устанавливаем данные
                LOGGER.info("!!! MobSpawnEventHandler: Setting mob data for entity {} with model: {}, texture: {}",
                        entity.getId(), mobData.getModelPath(), mobData.getTexturePath());
                entity.setMobData(mobData);
            }

            // Проверим наличие дерева поведения и исполнителя независимо от того,
            // были ли данные загружены сейчас или уже существовали
            MobData mobData = entity.getMobData();
            if (mobData == null) {
                LOGGER.error("!!! MobSpawnEventHandler: Entity still has no mob data after attempted load!");
                return;
            }

            // Сначала проверим, есть ли у моба уже исполнитель дерева поведения
            boolean hasExecutor = false;
            for (Goal goal : entity.goalSelector.getAvailableGoals()) {
                if (goal instanceof BehaviorTreeExecutor) {
                    hasExecutor = true;
                    LOGGER.info("!!! MobSpawnEventHandler: Entity already has BehaviorTreeExecutor");
                    break;
                }
            }

            // Если у моба уже есть исполнитель, пропускаем загрузку дерева
            if (hasExecutor) {
                LOGGER.info("!!! MobSpawnEventHandler: Skipping behavior tree setup, entity already has executor");
                return;
            }

            // Если у моба есть дерево поведения, добавляем соответствующую цель
            if (mobData.getBehaviorTree() != null) {
                LOGGER.info("!!! MobSpawnEventHandler: Entity {} has behavior tree with ID: {}",
                        entity.getId(), mobData.getBehaviorTree().getId());

                // Проверяем, есть ли узлы в дереве
                if (mobData.getBehaviorTree().getNodes() == null || mobData.getBehaviorTree().getNodes().isEmpty()) {
                    LOGGER.warn("!!! MobSpawnEventHandler: WARNING - Behavior tree has NO nodes! Trying to load full tree...");
                } else {
                    LOGGER.info("!!! MobSpawnEventHandler: Behavior tree already has {} nodes",
                            mobData.getBehaviorTree().getNodes().size());
                }

                // Загружаем полное дерево поведения по ID
                BehaviorTree fullTree = loadBehaviorTreeFromServer(mobData.getBehaviorTree().getId(), level);
                if (fullTree != null) {
                    // Проверяем полноту дерева
                    if (fullTree.getNodes() != null && !fullTree.getNodes().isEmpty()) {
                        LOGGER.info("!!! MobSpawnEventHandler: Loaded full tree with {} nodes and {} connections",
                                fullTree.getNodes().size(),
                                (fullTree.getConnections() != null ? fullTree.getConnections().size() : 0));

                        // Дамп всех узлов и связей для отладки
                        LOGGER.info("!!! ============ ДЕРЕВО ПОВЕДЕНИЯ УЗЛЫ ============");
                        for (BehaviorNode node : fullTree.getNodes()) {
                            LOGGER.info("!!! Node: ID={}, Type={}, Desc={}, Param={}",
                                    node.getId(), node.getType(), node.getDescription(), node.getParameter());
                        }

                        LOGGER.info("!!! ============ ДЕРЕВО ПОВЕДЕНИЯ СВЯЗИ ============");
                        if (fullTree.getConnections() != null) {
                            for (BehaviorConnection conn : fullTree.getConnections()) {
                                LOGGER.info("!!! Connection: {} -> {}", conn.getSourceNodeId(), conn.getTargetNodeId());
                            }
                        } else {
                            LOGGER.warn("!!! Connections list is NULL!");
                        }

                        // Заменяем дерево на полное
                        mobData.setBehaviorTree(fullTree);
                    } else {
                        LOGGER.warn("!!! MobSpawnEventHandler: WARNING - Loaded tree STILL has NO nodes! Check JSON file structure.");
                    }
                } else {
                    LOGGER.error("!!! MobSpawnEventHandler: Failed to load behavior tree from server for ID: {}",
                            mobData.getBehaviorTree().getId());
                }

                // Создаем исполнителя и добавляем его к мобу ТОЛЬКО если дерево имеет узлы
                if (mobData.getBehaviorTree().getNodes() != null && !mobData.getBehaviorTree().getNodes().isEmpty()) {
                    // Проверим структуру дерева перед добавлением исполнителя
                    BehaviorNode rootNode = mobData.getBehaviorTree().getRootNode();
                    if (rootNode == null) {
                        LOGGER.warn("!!! MobSpawnEventHandler: WARNING - Behavior tree has no root node!");
                    } else {
                        LOGGER.info("!!! MobSpawnEventHandler: Root node is: {} of type {}",
                                rootNode.getId(), rootNode.getType());
                    }

                    BehaviorTreeExecutor executor = new BehaviorTreeExecutor(entity, mobData.getBehaviorTree());
                    entity.goalSelector.addGoal(1, executor);
                    LOGGER.info("!!! MobSpawnEventHandler: Added behavior tree executor for entity {}", entity.getId());
                } else {
                    LOGGER.error("!!! MobSpawnEventHandler: ERROR - Cannot add behavior tree executor because tree has no nodes!");
                }
            } else {
                LOGGER.warn("!!! MobSpawnEventHandler: Entity {} has NO behavior tree defined!", entity.getId());
            }

            // Воспроизводим анимацию появления
            LOGGER.info("!!! MobSpawnEventHandler: Playing SPAWN animation for entity {}", entity.getId());
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

            LOGGER.info("!!! MobSpawnEventHandler: Trying to load behavior tree from path: {}", behaviorFile);

            if (Files.exists(behaviorFile)) {
                // Читаем содержимое файла
                String json = new String(Files.readAllBytes(behaviorFile), StandardCharsets.UTF_8);

                LOGGER.info("!!! MobSpawnEventHandler: Loaded behavior tree JSON, length: {} bytes", json.length());
                LOGGER.info("!!! BehaviorTree JSON content (first 200 chars): {}",
                        json.substring(0, Math.min(200, json.length())));

                try {
                    // Десериализуем дерево поведения
                    BehaviorTree tree = GSON.fromJson(json, BehaviorTree.class);

                    // Проверяем результат десериализации
                    if (tree == null) {
                        LOGGER.error("!!! MobSpawnEventHandler: Deserialization resulted in NULL tree!");
                        return null;
                    }

                    LOGGER.info("!!! MobSpawnEventHandler: Successfully deserialized tree: ID={}, Name={}",
                            tree.getId(), tree.getName());

                    if (tree.getNodes() == null) {
                        LOGGER.warn("!!! MobSpawnEventHandler: WARNING - Deserialized tree has NULL nodes list!");
                    } else if (tree.getNodes().isEmpty()) {
                        LOGGER.warn("!!! MobSpawnEventHandler: WARNING - Deserialized tree has EMPTY nodes list!");
                    } else {
                        LOGGER.info("!!! MobSpawnEventHandler: Deserialized tree has {} nodes", tree.getNodes().size());
                    }

                    return tree;
                } catch (Exception e) {
                    LOGGER.error("!!! ERROR deserializing behavior tree: {}", e.getMessage());
                    e.printStackTrace();

                    // Можно добавить альтернативный способ загрузки, если текущий не работает...
                    return null;
                }
            } else {
                LOGGER.warn("!!! MobSpawnEventHandler: Behavior tree file not found: {}", behaviorFile);

                // Можно добавить поиск файла в других местах, если нужно...
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("!!! Error loading behavior tree: {}", e.getMessage());
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