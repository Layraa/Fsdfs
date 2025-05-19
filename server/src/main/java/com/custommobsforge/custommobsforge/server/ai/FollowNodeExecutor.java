package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FollowNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private static final TargetingConditions FOLLOW_TARGETING = TargetingConditions.forNonCombat().range(32.0D);

    // Кэш состояний для каждого моба
    private static final Map<Integer, FollowState> mobStates = new ConcurrentHashMap<>();

    // Состояние для отслеживания прогресса выполнения узла
    private static class FollowState {
        public LivingEntity target; // Текущая цель
        public Vec3 lastPosition = Vec3.ZERO; // Последняя позиция моба
        public long lastPositionUpdateTime = 0; // Время последнего обновления позиции
        public long movementStartTime = 0; // Время начала движения
        public long estimatedDuration = 0; // Оценочная продолжительность движения
        public boolean isWalking = false; // Флаг того, что моб сейчас идёт
        public boolean wasAtDesiredDistance = false; // Флаг того, что моб уже был на нужном расстоянии
        public int stuckCounter = 0; // Счётчик "застреваний"
        public String animationState = "NONE"; // Текущее состояние анимации
        public double targetDistance = 0; // Целевое расстояние
        public int completionCount = 0; // Счётчик успешных завершений (для защиты от бесконечного цикла)

        // Конструктор
        public FollowState() {
            reset();
        }

        // Сброс состояния
        public void reset() {
            target = null;
            lastPosition = Vec3.ZERO;
            lastPositionUpdateTime = 0;
            movementStartTime = 0;
            estimatedDuration = 0;
            isWalking = false;
            wasAtDesiredDistance = false;
            stuckCounter = 0;
            animationState = "NONE";
        }
    }

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры узла
        double targetDistance = node.getCustomParameterAsDouble("distance", 5.0);
        double speed = node.getCustomParameterAsDouble("speed", 1.0);
        boolean targetPlayerOnly = node.getCustomParameterAsBoolean("targetPlayer", true);

        executor.logNodeExecution("FollowNode", node.getId(),
                "distance=" + targetDistance + ", speed=" + speed + ", targetPlayer=" + targetPlayerOnly, true);

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            executor.logNodeExecution("FollowNode", node.getId(), "skipped on client", false);
            return false; // На клиенте ничего не делаем
        }

        // Получаем уникальный ID для моба
        int entityId = entity.getId();

        // Получаем или создаём состояние для этого моба
        FollowState state = mobStates.computeIfAbsent(entityId, id -> new FollowState());

        // Сохраняем целевое расстояние
        state.targetDistance = targetDistance;

        // Отключаем автоматические анимации, чтобы не было конфликтов
        entity.setDisableAutoAnimations(true);

        // Текущее время для расчётов
        long currentTime = System.currentTimeMillis();

        // Если это первый запуск, инициализируем позицию
        if (state.lastPosition.equals(Vec3.ZERO)) {
            state.lastPosition = entity.position();
            state.lastPositionUpdateTime = currentTime;
        }

        // Проверяем достижение лимита успешных завершений
        if (state.completionCount >= 5) {
            LOGGER.info("FollowNode: Entity {} has reached completion limit ({}), finishing node",
                    entityId, state.completionCount);

            // Восстанавливаем автоматические анимации
            entity.setDisableAutoAnimations(false);

            // Очищаем состояние
            state.reset();

            // Сообщаем, что мы закончили
            executor.setNodeNeedsMoreTime(false);
            return true;
        }

        // Проверяем, закончилось ли время движения
        if (state.isWalking && state.movementStartTime > 0) {
            if (currentTime - state.movementStartTime > state.estimatedDuration) {
                LOGGER.info("FollowNode: Entity {} movement time elapsed, checking position", entityId);
                state.isWalking = false;
            }
        }

        // Обновляем информацию о движении
        if (currentTime - state.lastPositionUpdateTime > 200) { // Каждые 200 мс
            Vec3 currentPos = entity.position();
            double distanceMoved = state.lastPosition.distanceTo(currentPos);

            // Проверяем, движется ли моб
            if (state.isWalking && distanceMoved < 0.05) {
                state.stuckCounter++;
                LOGGER.info("FollowNode: Entity {} might be stuck (moved only {} blocks), stuck counter: {}",
                        entityId, distanceMoved, state.stuckCounter);

                // Если моб застрял несколько раз подряд, пытаемся его освободить
                if (state.stuckCounter >= 3) {
                    LOGGER.warn("FollowNode: Entity {} is stuck, trying to free it", entityId);

                    // Останавливаем текущий путь
                    entity.getNavigation().stop();

                    // Небольшой "прыжок" для преодоления мелких препятствий
                    entity.setDeltaMovement(entity.getDeltaMovement().add(0, 0.2, 0));

                    // Задержка перед новой попыткой
                    state.stuckCounter = 0;
                    state.isWalking = false;
                }
            } else {
                state.stuckCounter = 0;
            }

            // Обновляем последнюю позицию
            state.lastPosition = currentPos;
            state.lastPositionUpdateTime = currentTime;
        }

        // Поиск или проверка текущей цели
        LivingEntity target = state.target;

        if (target == null || target.isRemoved() || entity.distanceTo(target) > 32.0) {
            target = findTarget(entity, targetPlayerOnly);

            if (target == null) {
                executor.logNodeExecution("FollowNode", node.getId(), "no target found", false);

                // Воспроизводим анимацию IDLE, так как нет цели для следования
                if (!state.animationState.equals("IDLE")) {
                    executor.playAnimation("IDLE");
                    state.animationState = "IDLE";
                }

                // Увеличиваем счётчик завершений
                state.completionCount++;

                // Если несколько раз подряд нет цели, завершаем узел
                if (state.completionCount >= 3) {
                    executor.setNodeNeedsMoreTime(false);
                    entity.setDisableAutoAnimations(false);
                    state.reset();
                    return true;
                }

                // Продолжаем выполнение узла, но с задержкой
                executor.setNodeNeedsMoreTime(true);
                return true;
            }

            // Обновляем цель
            state.target = target;
        }

        // Вычисляем расстояние до цели
        double distanceToTarget = entity.distanceTo(target);
        LOGGER.info("FollowNode: Entity {} is at distance {} from target, desired distance is {}",
                entityId, distanceToTarget, targetDistance);

        // Если уже находимся на нужном расстоянии (с погрешностью), считаем это успехом
        if (Math.abs(distanceToTarget - targetDistance) < 1.0) {
            LOGGER.info("FollowNode: Entity {} is at desired distance", entityId);

            // Воспроизводим анимацию ожидания
            if (!state.animationState.equals("IDLE")) {
                executor.playAnimation("IDLE");
                state.animationState = "IDLE";
            }

            // Если мы уже были на нужном расстоянии, увеличиваем счётчик завершений
            if (state.wasAtDesiredDistance) {
                state.completionCount++;

                // Если мы на нужном расстоянии несколько тиков подряд, считаем узел завершённым
                if (state.completionCount >= 3) {
                    LOGGER.info("FollowNode: Entity {} has been at desired distance for {} ticks, completing node",
                            entityId, state.completionCount);

                    // Восстанавливаем автоматические анимации
                    entity.setDisableAutoAnimations(false);

                    // Очищаем состояние
                    state.reset();

                    // Сообщаем, что мы закончили
                    executor.setNodeNeedsMoreTime(false);
                    return true;
                }
            } else {
                state.wasAtDesiredDistance = true;
                state.completionCount = 1;
            }

            // Продолжаем выполнение узла, но с проверкой в следующем тике
            executor.setNodeNeedsMoreTime(true);
            return true;
        } else {
            // Сбрасываем флаг, так как мы не на нужном расстоянии
            state.wasAtDesiredDistance = false;
            state.completionCount = 0;
        }

        // Получаем навигацию
        PathNavigation navigation = entity.getNavigation();
        if (navigation == null) {
            LOGGER.error("FollowNode: Entity {} has no navigation!", entityId);
            executor.logNodeExecution("FollowNode", node.getId(), "no navigation available", false);
            executor.setNodeNeedsMoreTime(false);
            entity.setDisableAutoAnimations(false);
            state.reset();
            return false;
        }

        // Если моб уже идёт, не пытаемся установить новый путь
        if (state.isWalking && currentTime - state.movementStartTime < state.estimatedDuration) {
            // Просто обновляем анимацию ходьбы
            if (!state.animationState.equals("WALK")) {
                executor.playAnimation("WALK");
                state.animationState = "WALK";
            }

            // Продолжаем смотреть на цель
            entity.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // Продолжаем выполнение узла
            executor.setNodeNeedsMoreTime(true);
            return true;
        }

        // Вычисляем направление к цели
        Vec3 targetPos = target.position();
        Vec3 entityPos = entity.position();
        Vec3 direction;

        // Направление движения зависит от текущего расстояния
        if (distanceToTarget < targetDistance) {
            // Слишком близко, нужно отойти
            direction = entityPos.subtract(targetPos).normalize();
            LOGGER.info("FollowNode: Entity {} is too close, moving away", entityId);
        } else {
            // Слишком далеко, нужно подойти
            direction = targetPos.subtract(entityPos).normalize();
            LOGGER.info("FollowNode: Entity {} is too far, moving closer", entityId);
        }

        // Вычисляем целевую точку
        double moveDistance = Math.abs(distanceToTarget - targetDistance);
        if (moveDistance > 10) moveDistance = 10; // Ограничиваем дистанцию для лучшего контроля

        // Останавливаем текущий путь
        navigation.stop();

        // Рассчитываем целевую позицию
        double moveToX, moveToZ;
        if (distanceToTarget < targetDistance) {
            // Если мы слишком близко, отходим в противоположном направлении
            moveToX = entityPos.x + direction.x * moveDistance;
            moveToZ = entityPos.z + direction.z * moveDistance;
        } else {
            // Если мы слишком далеко, используем вектор к цели
            // и останавливаемся на нужном расстоянии от неё
            Vec3 dirToTarget = targetPos.subtract(entityPos).normalize();
            double distanceToMove = distanceToTarget - targetDistance;
            moveToX = entityPos.x + dirToTarget.x * distanceToMove;
            moveToZ = entityPos.z + dirToTarget.z * distanceToMove;
        }

        // Используем навигацию для движения
        boolean pathSuccess = navigation.moveTo(moveToX, entityPos.y, moveToZ, speed);

        if (pathSuccess) {
            LOGGER.info("FollowNode: Entity {} is moving to [{}, {}, {}] with speed {}",
                    entityId, moveToX, entityPos.y, moveToZ, speed);

            // Воспроизводим анимацию ходьбы
            executor.playAnimation("WALK");
            state.animationState = "WALK";
            state.isWalking = true;

            // Включаем "смотреть на цель" даже при движении
            entity.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // Запоминаем начало движения
            state.movementStartTime = currentTime;

            // Рассчитываем приблизительную продолжительность движения
            long estimatedDuration = (long)(moveDistance / speed * 1000);
            if (estimatedDuration < 500) estimatedDuration = 500;
            if (estimatedDuration > 5000) estimatedDuration = 5000;
            state.estimatedDuration = estimatedDuration;

            // Продолжаем выполнение узла
            executor.setNodeNeedsMoreTime(true);
            return true;
        } else {
            // Если не удалось построить путь, пробуем прямое движение
            LOGGER.warn("FollowNode: Entity {} failed to create path, trying direct movement", entityId);

            // Используем прямое перемещение через MoveControl
            entity.getMoveControl().setWantedPosition(moveToX, entityPos.y, moveToZ, speed);

            // Воспроизводим анимацию ходьбы
            executor.playAnimation("WALK");
            state.animationState = "WALK";
            state.isWalking = true;

            // Смотрим на цель
            entity.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // Запоминаем начало движения
            state.movementStartTime = currentTime;
            state.estimatedDuration = 1000L; // 1 секунда для прямого движения

            // Продолжаем выполнение узла
            executor.setNodeNeedsMoreTime(true);
            return true;
        }
    }

    // Метод поиска цели
    private LivingEntity findTarget(CustomMobEntity entity, boolean targetPlayer) {
        if (targetPlayer) {
            // Ищем ближайшего игрока
            Player nearestPlayer = entity.level().getNearestPlayer(
                    FOLLOW_TARGETING,
                    entity
            );

            return nearestPlayer;
        } else {
            // Ищем любую живую сущность
            List<LivingEntity> nearbyEntities = entity.level().getEntitiesOfClass(
                    LivingEntity.class,
                    entity.getBoundingBox().inflate(16.0),
                    target -> FOLLOW_TARGETING.test(entity, target) && !(target instanceof CustomMobEntity)
            );

            if (!nearbyEntities.isEmpty()) {
                return nearbyEntities.get(0);
            }
        }

        return null;
    }

    // Метод для очистки ресурсов при удалении моба
    public static void cleanup(int entityId) {
        mobStates.remove(entityId);
        LOGGER.info("FollowNode: Cleaned up resources for entity {}", entityId);
    }
}