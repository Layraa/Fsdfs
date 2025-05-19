package com.custommobsforge.custommobsforge.common.entity;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.data.AnimationMapping;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.AnimationSyncPacket;
import com.custommobsforge.custommobsforge.common.network.packet.MobDataPacket;
import com.custommobsforge.custommobsforge.common.event.system.AnimationCompletedEvent;
import com.custommobsforge.custommobsforge.common.event.system.AnimationStartedEvent;
import com.custommobsforge.custommobsforge.common.event.system.EventSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import mod.azure.azurelib.animatable.GeoEntity;
import mod.azure.azurelib.core.animatable.instance.AnimatableInstanceCache;
import mod.azure.azurelib.core.animation.AnimatableManager;
import mod.azure.azurelib.core.animation.AnimationController;
import mod.azure.azurelib.core.animation.RawAnimation;
import mod.azure.azurelib.core.animation.Animation;
import mod.azure.azurelib.core.object.PlayState;
import mod.azure.azurelib.util.AzureLibUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CustomMobEntity extends PathfinderMob implements GeoEntity {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private static final EntityDataAccessor<String> MOB_ID =
            SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.STRING);

    private MobData mobData;
    private final AnimatableInstanceCache cache = AzureLibUtil.createInstanceCache(this);
    private String currentAnimation = "";
    private boolean isLoopingAnimation = true;
    private float animationSpeed = 1.0f;

    // Поле для отслеживания времени запуска анимаций
    private final Map<String, Long> animationStartTimes = new ConcurrentHashMap<>();

    // Карта для хранения коллбэков для анимаций
    private final Map<String, Consumer<String>> animationCallbacks = new ConcurrentHashMap<>();

    // Поля для управления автоматическими анимациями
    private String lastPlayedAnimation = "";
    private long lastAnimationTime = 0;
    private static final long ANIMATION_COOLDOWN = 500; // 0.5 секунды в миллисекундах

    // Флаг для отслеживания, был ли моб только что создан
    private boolean isNewlySpawned = true;

    // Набор приоритетных анимаций, которые не должны прерываться
    private static final Set<String> PRIORITY_ANIMATIONS = new HashSet<>(Arrays.asList(
            "ATTACK", "HURT", "DEATH", "SPECIAL_1", "SPECIAL_2"));

    // Флаг для отключения автоматических анимаций
    private boolean disableAutoAnimations = false;

    public CustomMobEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        System.out.println("CustomMobEntity: Created new entity instance");
    }

    // Статический строитель атрибутов
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D, 0.0F));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MOB_ID, "");
    }

    public void setMobId(String mobId) {
        this.entityData.set(MOB_ID, mobId);
        System.out.println("CustomMobEntity: Set mob ID to " + mobId + " for entity " + this.getId());
    }

    public String getMobId() {
        return this.entityData.get(MOB_ID);
    }

    public MobData getMobData() {
        return mobData;
    }

    public void setMobData(MobData mobData) {
        this.mobData = mobData;
        System.out.println("CustomMobEntity: Set mob data for entity " + this.getId() +
                " with ID " + (mobData != null ? mobData.getId() : "null") +
                ", model: " + (mobData != null ? mobData.getModelPath() : "null") +
                ", texture: " + (mobData != null ? mobData.getTexturePath() : "null"));

        if (mobData != null && mobData.getAttributes() != null) {
            if (mobData.getAttributes().containsKey("maxHealth")) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(mobData.getAttributes().get("maxHealth"));
                this.setHealth(this.getMaxHealth());
            }
            if (mobData.getAttributes().containsKey("movementSpeed")) {
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(mobData.getAttributes().get("movementSpeed"));
            }
            if (mobData.getAttributes().containsKey("attackDamage")) {
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(mobData.getAttributes().get("attackDamage"));
            }
            if (mobData.getAttributes().containsKey("armor")) {
                this.getAttribute(Attributes.ARMOR).setBaseValue(mobData.getAttributes().get("armor"));
            }
            if (mobData.getAttributes().containsKey("knockbackResistance")) {
                this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(mobData.getAttributes().get("knockbackResistance"));
            }
        }
        syncMobDataWithClient();
    }

    private void syncMobDataWithClient() {
        if (!this.level().isClientSide && this.mobData != null) {
            NetworkManager.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new MobDataPacket(this.mobData)
            );
            System.out.println("CustomMobEntity: Synced mob data for " + this.getMobId() + " to tracking clients");
        }
    }

    /**
     * Проигрывает анимацию по действию
     */
    public void playAnimation(String action) {
        if (mobData != null && mobData.getAnimations() != null) {
            AnimationMapping mapping = mobData.getAnimations().get(action);

            if (mapping != null) {
                String animationId = mapping.getAnimationName();
                boolean loop = mapping.isLoop();
                float speed = mapping.getSpeed();

                LOGGER.info("CustomMobEntity: Playing animation {} for entity {} (loop: {}, speed: {})",
                        animationId, this.getId(), loop, speed);

                this.currentAnimation = animationId;
                this.isLoopingAnimation = loop;
                this.animationSpeed = speed;

                this.lastPlayedAnimation = action;
                this.lastAnimationTime = System.currentTimeMillis();

                animationStartTimes.put(animationId, System.currentTimeMillis());

                EventSystem.fireEvent(new AnimationStartedEvent(animationId, this));

                if (!this.level().isClientSide) {
                    NetworkManager.INSTANCE.send(
                            PacketDistributor.TRACKING_ENTITY.with(() -> this),
                            new AnimationSyncPacket(this.getId(), animationId, speed, loop)
                    );
                }
            } else {
                LOGGER.warn("CustomMobEntity: No animation mapping found for action {} in entity {}",
                        action, this.getId());

                if (mobData.getAnimations() != null) {
                    LOGGER.info("Available animations:");
                    for (Map.Entry<String, AnimationMapping> entry : mobData.getAnimations().entrySet()) {
                        LOGGER.info("  {} -> {}", entry.getKey(), entry.getValue().getAnimationName());
                    }
                }
            }
        } else {
            LOGGER.warn("CustomMobEntity: No mob data or animations available for entity {}", this.getId());
        }
    }

    /**
     * Прямое воспроизведение анимации по имени, без использования маппинга
     */
    public void playAnimationDirect(String animationName, boolean loop, float speed) {
        LOGGER.info("CustomMobEntity: Directly playing animation '{}' for entity {} (loop: {}, speed: {})",
                animationName, this.getId(), loop, speed);

        // Если анимация пустая, просто выходим
        if (animationName == null || animationName.isEmpty()) {
            LOGGER.warn("CustomMobEntity: Attempted to play empty animation for entity {}", this.getId());
            return;
        }

        // Записываем время запуска
        animationStartTimes.put(animationName, System.currentTimeMillis());

        // Генерируем событие начала анимации
        EventSystem.fireEvent(new AnimationStartedEvent(animationName, this));

        boolean canInterrupt = true;

        // Проверяем, можно ли прерывать текущую анимацию
        if (!this.currentAnimation.isEmpty() && !this.currentAnimation.equals(animationName)) {
            if (!isLoopingAnimation && System.currentTimeMillis() - lastAnimationTime < 1000) {
                LOGGER.info("CustomMobEntity: Checking if can interrupt current animation '{}'", currentAnimation);

                // Проверяем, является ли текущая анимация приоритетной
                boolean isCurrentPriority = false;
                boolean isNewPriority = false;

                if (mobData != null && mobData.getAnimations() != null) {
                    for (Map.Entry<String, AnimationMapping> entry : mobData.getAnimations().entrySet()) {
                        // Проверяем текущую анимацию
                        if (PRIORITY_ANIMATIONS.contains(entry.getKey()) &&
                                entry.getValue().getAnimationName().equals(currentAnimation)) {
                            isCurrentPriority = true;
                        }

                        // Проверяем новую анимацию
                        if (PRIORITY_ANIMATIONS.contains(entry.getKey()) &&
                                entry.getValue().getAnimationName().equals(animationName)) {
                            isNewPriority = true;
                        }
                    }
                }

                // Не прерываем приоритетную анимацию не-приоритетной
                if (isCurrentPriority && !isNewPriority) {
                    LOGGER.info("Not interrupting priority animation {} with non-priority {}",
                            currentAnimation, animationName);
                    canInterrupt = false;
                }
            }
        }

        if (canInterrupt) {
            if (!this.currentAnimation.isEmpty() && !this.currentAnimation.equals(animationName)) {
                LOGGER.info("CustomMobEntity: Resetting previous animation '{}' before playing new one",
                        this.currentAnimation);

                if (!isLoopingAnimation) {
                    EventSystem.fireEvent(new AnimationCompletedEvent(this.currentAnimation, this));
                }
            }

            this.currentAnimation = animationName;
            this.isLoopingAnimation = loop;
            this.animationSpeed = speed;

            this.lastAnimationTime = System.currentTimeMillis();
            this.lastPlayedAnimation = animationName;

            // Важно: После завершения незацикленной анимации мы должны автоматически
            // вернуться к анимации IDLE
            if (!loop) {
                // Расчет времени, когда мы вернемся к IDLE
                long returnToIdleTime = System.currentTimeMillis() + estimateAnimationDuration(animationName) + 100;

                // Планируем переход к IDLE после завершения анимации
                new Thread(() -> {
                    try {
                        Thread.sleep(returnToIdleTime - System.currentTimeMillis());

                        // Проверяем, не запущена ли уже другая анимация
                        if (this.currentAnimation.equals(animationName) && !this.level().isClientSide && !this.isRemoved()) {
                            LOGGER.info("CustomMobEntity: Animation '{}' finished, returning to IDLE", animationName);
                            this.playAnimation("IDLE");
                        }
                    } catch (InterruptedException e) {
                        // Игнорируем
                    }
                }).start();
            }

            forceAnimationRefresh();

            if (!this.level().isClientSide) {
                try {
                    NetworkManager.INSTANCE.send(
                            PacketDistributor.TRACKING_ENTITY.with(() -> this),
                            new AnimationSyncPacket(this.getId(), animationName, speed, loop)
                    );
                    LOGGER.info("CustomMobEntity: Animation sync packet sent to tracking clients for {} on entity {}",
                            animationName, this.getId());
                } catch (Exception e) {
                    LOGGER.error("CustomMobEntity: ERROR sending animation sync packet: {}", e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    // Добавьте этот метод в класс CustomMobEntity

    /**
     * Переходит к анимации IDLE, если не воспроизводится приоритетная анимация
     */
    public void transitionToIdleAnimation() {
        if (currentAnimation.isEmpty() || isLoopingAnimation) {
            if (!lastPlayedAnimation.equals("IDLE")) {
                LOGGER.info("CustomMobEntity: Transitioning to IDLE animation for entity {}", this.getId());
                this.playAnimation("IDLE");
            }
        } else {
            // Проверяем, не является ли текущая анимация приоритетной
            boolean isCurrentPriority = false;

            if (mobData != null && mobData.getAnimations() != null) {
                for (Map.Entry<String, AnimationMapping> entry : mobData.getAnimations().entrySet()) {
                    if (PRIORITY_ANIMATIONS.contains(entry.getKey()) &&
                            entry.getValue().getAnimationName().equals(currentAnimation)) {
                        isCurrentPriority = true;
                        break;
                    }
                }
            }

            if (!isCurrentPriority) {
                LOGGER.info("CustomMobEntity: Current animation '{}' is not priority, transitioning to IDLE",
                        currentAnimation);
                this.playAnimation("IDLE");
            }
        }
    }

    /**
     * Принудительно обновляет контроллеры анимации
     */
    private void forceAnimationRefresh() {
        if (this.cache != null) {
            AnimatableManager<CustomMobEntity> manager = this.cache.getManagerForId(this.getId());
            if (manager != null) {
                Map<String, AnimationController<CustomMobEntity>> controllers = manager.getAnimationControllers();
                if (controllers != null) {
                    for (AnimationController<CustomMobEntity> controller : controllers.values()) {
                        controller.forceAnimationReset();
                    }
                }
            }
        }
    }

    public void setAnimation(String animationId, boolean loop, float speed) {
        System.out.println("CustomMobEntity: Setting animation " + animationId +
                " for entity " + this.getId() + " (loop: " + loop + ", speed: " + speed + ")");

        if (mobData != null && mobData.getAnimations() != null) {
            boolean found = false;
            for (Map.Entry<String, AnimationMapping> entry : mobData.getAnimations().entrySet()) {
                if (entry.getValue().getAnimationName().equals(animationId)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.println("CustomMobEntity: WARNING - Animation '" + animationId +
                        "' not found in mob data animations! Available animations:");
                for (Map.Entry<String, AnimationMapping> entry : mobData.getAnimations().entrySet()) {
                    System.out.println("  " + entry.getKey() + " -> " + entry.getValue().getAnimationName());
                }
            }
        }

        this.currentAnimation = animationId;
        this.isLoopingAnimation = loop;
        this.animationSpeed = speed;

        animationStartTimes.put(animationId, System.currentTimeMillis());

        this.lastAnimationTime = System.currentTimeMillis();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<CustomMobEntity> controller = new AnimationController<>(this, "controller", 0, event -> {
            if (this.currentAnimation == null || this.currentAnimation.isEmpty()) {
                return PlayState.STOP;
            }

            if (executionTicks % 100 == 0) {
                LOGGER.debug("CustomMobEntity: Animation controller processing animation: {} (loop: {}) for entity {}",
                        currentAnimation, isLoopingAnimation, this.getId());
            }

            Animation.LoopType loopType = isLoopingAnimation ? Animation.LoopType.LOOP : Animation.LoopType.PLAY_ONCE;

            try {
                RawAnimation animation = RawAnimation.begin().then(currentAnimation, loopType);

                if (!isLoopingAnimation) {
                    if (event.getController().hasAnimationFinished()) {
                        LOGGER.info("CustomMobEntity: Animation controller reports animation '{}' finished for entity {}",
                                currentAnimation, this.getId());

                        EventSystem.fireEvent(new AnimationCompletedEvent(currentAnimation, this));
                    }
                }

                return event.setAndContinue(animation);
            } catch (Exception e) {
                LOGGER.error("Error in animation controller for entity {}: {}", this.getId(), e.getMessage());
                return PlayState.STOP;
            }
        });

        controller.setAnimationSpeedHandler(animatable -> (double) this.animationSpeed);

        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        String mobId = this.getMobId();
        compound.putString("MobId", mobId);
        System.out.println("CustomMobEntity: Saving mob ID " + mobId + " to NBT for entity " + this.getId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        String mobId = compound.getString("MobId");
        this.setMobId(mobId);
        System.out.println("CustomMobEntity: Loaded mob ID " + mobId + " from NBT for entity " + this.getId());
    }

    private int executionTicks = 0;

    // Добавьте следующие методы в класс CustomMobEntity

    private void updateAnimations() {
        // Обновляем статус незацикленных анимаций
        if (!this.currentAnimation.isEmpty() && !this.isLoopingAnimation) {
            long startTime = getAnimationStartTime(this.currentAnimation);
            long currentTime = System.currentTimeMillis();
            long duration = estimateAnimationDuration(this.currentAnimation);

            if (startTime > 0 && currentTime - startTime > duration) {
                LOGGER.info("CustomMobEntity: Animation '{}' completed naturally after {} ms (duration estimate: {} ms)",
                        this.currentAnimation, currentTime - startTime, duration);

                // Сохраняем имя завершенной анимации
                String completedAnimation = this.currentAnimation;

                // Очищаем ссылку на текущую анимацию перед генерацией события
                this.currentAnimation = "";

                // Запускаем событие завершения анимации
                EventSystem.fireEvent(new AnimationCompletedEvent(completedAnimation, this));

                // Уведомляем коллбэки
                notifyAnimationCallbacks(completedAnimation);

                // Удаляем информацию о времени запуска
                animationStartTimes.remove(completedAnimation);

                LOGGER.info("CustomMobEntity: Reset current animation from '{}' to empty", completedAnimation);

                // Если не включена блокировка автоанимаций, возвращаемся к IDLE
                if (!disableAutoAnimations) {
                    // Небольшая задержка перед переходом к IDLE
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Игнорируем
                    }

                    LOGGER.info("CustomMobEntity: Transitioning to IDLE after animation completion");
                    playAnimation("IDLE");
                }
            }
        }
    }

    /**
     * Обновленный метод tick() с вызовом updateAnimations()
     */
    @Override
    public void tick() {
        super.tick();
        executionTicks++;

        // Загрузка данных моба при необходимости
        if (mobData == null && !this.level().isClientSide && this.getMobId() != null && !this.getMobId().isEmpty()) {
            if (this.level() instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel) this.level();
                LOGGER.info("CustomMobEntity: Attempting to load mob data for ID " + this.getMobId() +
                        " in tick() method");

                com.custommobsforge.custommobsforge.common.config.MobConfigManager.init(serverLevel);
                MobData mobData = com.custommobsforge.custommobsforge.common.config.MobConfigManager.loadMobConfig(this.getMobId(), serverLevel);

                if (mobData != null) {
                    LOGGER.info("CustomMobEntity: Successfully loaded mob data in tick() method");
                    this.setMobData(mobData);
                }
            }
        }

        // Специальная обработка для только что созданного моба
        if (isNewlySpawned && mobData != null && !this.level().isClientSide) {
            LOGGER.info("CustomMobEntity: Entity " + this.getId() + " is newly spawned, playing SPAWN animation");
            this.playAnimation("SPAWN");
            isNewlySpawned = false;
            // Устанавливаем флаг блокировки автоматических анимаций на короткое время
            this.disableAutoAnimations = true;
            return;
        }

        // Обновляем статус анимаций
        updateAnimations();

        // Обработка автоматических анимаций
        if (!disableAutoAnimations && !this.level().isClientSide && mobData != null &&
                System.currentTimeMillis() - lastAnimationTime > ANIMATION_COOLDOWN &&
                !this.isDeadOrDying() && this.hurtTime <= 0 && this.currentAnimation.isEmpty()) {

            double speed = this.getDeltaMovement().horizontalDistance();
            if (speed > 0.01) {
                if (!lastPlayedAnimation.equals("WALK")) {
                    LOGGER.info("CustomMobEntity: Entity " + this.getId() + " is moving, playing WALK animation");
                    this.playAnimation("WALK");
                }
            } else {
                if (!lastPlayedAnimation.equals("IDLE")) {
                    LOGGER.info("CustomMobEntity: Entity " + this.getId() + " is idle, playing IDLE animation");
                    this.playAnimation("IDLE");
                }
            }
        }
    }

    // Вызывает все зарегистрированные колбэки для анимации
    private void notifyAnimationCallbacks(String animationId) {
        // Проходим по всем зарегистрированным коллбэкам
        animationCallbacks.forEach((callbackId, callback) -> {
            callback.accept(animationId);
        });
    }

    // Метод для отключения автоматических анимаций (IDLE, WALK)
    public void setDisableAutoAnimations(boolean disable) {
        this.disableAutoAnimations = disable;
    }

    private void updateAnimationStatuses() {
        long currentTime = System.currentTimeMillis();

        if (!isLoopingAnimation && currentAnimation != null && !currentAnimation.isEmpty()) {
            long startTime = animationStartTimes.getOrDefault(currentAnimation, 0L);
            if (startTime > 0) {
                long duration = estimateAnimationDuration(currentAnimation);

                if (currentTime - startTime > duration) {
                    LOGGER.info("CustomMobEntity: Animation '{}' has completed after {} ms (duration: {} ms)",
                            currentAnimation, currentTime - startTime, duration);

                    EventSystem.fireEvent(new AnimationCompletedEvent(currentAnimation, this));

                    animationStartTimes.remove(currentAnimation);
                }
            }
        }
    }

    public long estimateAnimationDuration(String animationId) {
        long baseDuration = 2000L;
        if (animationId.contains("summon") || animationId.contains("spawn")) {
            baseDuration = 3000L;
        } else if (animationId.contains("attack")) {
            baseDuration = 1000L;
        } else if (animationId.contains("death")) {
            baseDuration = 3500L;
        } else if (animationId.contains("hurt")) {
            baseDuration = 800L;
        }
        return (long)(baseDuration / this.animationSpeed);
    }

    public long getAnimationStartTime(String animationId) {
        return animationStartTimes.getOrDefault(animationId, 0L);
    }

    // Регистрирует коллбэк для анимации
    public void registerAnimationCallback(String callbackId, Consumer<String> callback) {
        animationCallbacks.put(callbackId, callback);
        LOGGER.info("CustomMobEntity: Registered animation callback with ID {} for entity {}", callbackId, this.getId());
    }

    // Удаляет коллбэк
    public void removeAnimationCallback(String callbackId) {
        if (animationCallbacks.remove(callbackId) != null) {
            LOGGER.info("CustomMobEntity: Removed animation callback with ID {} for entity {}", callbackId, this.getId());
        }
    }

    // Проверяет, завершилась ли анимация
    public boolean hasAnimationCompleted(String animationId) {
        long startTime = getAnimationStartTime(animationId);
        if (startTime == 0) {
            return false;
        }

        long duration = estimateAnimationDuration(animationId);
        return System.currentTimeMillis() - startTime > duration;
    }

    // Принудительно завершает анимацию
    public void forceCompleteAnimation(String animationId) {
        if (animationId.equals(currentAnimation)) {
            LOGGER.info("CustomMobEntity: Forcing completion of animation {} for entity {}", animationId, this.getId());
            EventSystem.fireEvent(new AnimationCompletedEvent(animationId, this));

            // Уведомляем коллбэки
            notifyAnimationCallbacks(animationId);

            if (animationId.equals(currentAnimation)) {
                // Если текущая анимация все еще та же, сбрасываем ее
                this.currentAnimation = "";
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (super.hurt(source, amount)) {
            System.out.println("CustomMobEntity: Entity " + this.getId() + " hurt, playing HURT animation");
            this.playAnimation("HURT");
            return true;
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        System.out.println("CustomMobEntity: Entity " + this.getId() + " died, playing DEATH animation");
        this.playAnimation("DEATH");
        super.die(source);
    }
}