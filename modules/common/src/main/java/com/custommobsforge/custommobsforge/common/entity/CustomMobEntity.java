package com.custommobsforge.custommobsforge.common.entity;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.data.AnimationMapping;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.AnimationSyncPacket;
import com.custommobsforge.custommobsforge.common.network.packet.MobDataPacket;
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

import java.util.Map;

public class CustomMobEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> MOB_ID =
            SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.STRING);

    private MobData mobData;
    private final AnimatableInstanceCache cache = AzureLibUtil.createInstanceCache(this);
    private String currentAnimation = "";
    private boolean isLoopingAnimation = true;
    private float animationSpeed = 1.0f;

    // Новые поля для управления автоматическими анимациями
    private String lastPlayedAnimation = "";
    private long lastAnimationTime = 0;
    private static final long ANIMATION_COOLDOWN = 500; // 0.5 секунды в миллисекундах

    // Флаг для отслеживания, был ли моб только что создан
    private boolean isNewlySpawned = true;

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

        // Добавляем логирование
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

        // Добавляем логирование
        System.out.println("CustomMobEntity: Set mob data for entity " + this.getId() +
                " with ID " + (mobData != null ? mobData.getId() : "null") +
                ", model: " + (mobData != null ? mobData.getModelPath() : "null") +
                ", texture: " + (mobData != null ? mobData.getTexturePath() : "null"));

        // Обновляем атрибуты на основе данных моба
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

        // Синхронизируем данные с клиентами, если мы на сервере
        syncMobDataWithClient();
    }

    // Новый метод для синхронизации данных моба с клиентами
    private void syncMobDataWithClient() {
        if (!this.level().isClientSide && this.mobData != null) {
            // Отправляем данные всем клиентам в радиусе отслеживания этой сущности
            NetworkManager.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new MobDataPacket(this.mobData)
            );
            System.out.println("CustomMobEntity: Synced mob data for " + this.getMobId() + " to tracking clients");
        }
    }

    // Методы для анимаций
    public void playAnimation(String action) {
        if (mobData != null && mobData.getAnimations() != null) {
            AnimationMapping mapping = mobData.getAnimations().get(action);

            if (mapping != null) {
                String animationId = mapping.getAnimationName();
                boolean loop = mapping.isLoop();
                float speed = mapping.getSpeed();

                System.out.println("CustomMobEntity: Playing animation " + animationId +
                        " for entity " + this.getId() + " (loop: " + loop + ", speed: " + speed + ")");

                // Здесь мы НЕ изменяем ID анимации, а используем его как есть из конфигурации
                // Это обеспечивает полную гибкость и совместимость с любыми названиями анимаций

                this.currentAnimation = animationId;
                this.isLoopingAnimation = loop;
                this.animationSpeed = speed;

                // Обновляем последнюю проигранную анимацию
                this.lastPlayedAnimation = action;
                this.lastAnimationTime = System.currentTimeMillis();

                // Синхронизируем анимацию с клиентами
                if (!this.level().isClientSide) {
                    NetworkManager.INSTANCE.send(
                            PacketDistributor.TRACKING_ENTITY.with(() -> this),
                            new AnimationSyncPacket(this.getId(), animationId, speed, loop)
                    );
                }
            } else {
                System.out.println("CustomMobEntity: No animation mapping found for action " + action +
                        " in entity " + this.getId());

                // Выводим доступные анимации для отладки
                if (mobData.getAnimations() != null) {
                    System.out.println("Available animations:");
                    for (Map.Entry<String, AnimationMapping> entry : mobData.getAnimations().entrySet()) {
                        System.out.println("  " + entry.getKey() + " -> " + entry.getValue().getAnimationName());
                    }
                }
            }
        } else {
            System.out.println("CustomMobEntity: No mob data or animations available for entity " + this.getId());
        }
    }

    public void setAnimation(String animationId, boolean loop, float speed) {
        System.out.println("CustomMobEntity: Setting animation " + animationId +
                " for entity " + this.getId() + " (loop: " + loop + ", speed: " + speed + ")");

        // Используем ID анимации как есть, без модификаций
        this.currentAnimation = animationId;
        this.isLoopingAnimation = loop;
        this.animationSpeed = speed;

        // Обновляем время последней анимации для cooldown
        this.lastAnimationTime = System.currentTimeMillis();
    }

    // Методы GeoEntity для AzureLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<CustomMobEntity> controller = new AnimationController<>(this, "controller", 0, event -> {
            if (this.currentAnimation == null || this.currentAnimation.isEmpty()) {
                System.out.println("CustomMobEntity: No animation set for entity " + this.getId());
                return PlayState.STOP;
            }

            System.out.println("CustomMobEntity: Animation controller processing animation: " + currentAnimation +
                    " (loop: " + isLoopingAnimation + ") for entity " + this.getId());

            // Используем правильный тип LoopType для анимации
            if (isLoopingAnimation) {
                return event.setAndContinue(RawAnimation.begin().then(currentAnimation, Animation.LoopType.LOOP));
            } else {
                return event.setAndContinue(RawAnimation.begin().then(currentAnimation, Animation.LoopType.PLAY_ONCE));
            }
        });

        // Используем setAnimationSpeedHandler с лямбдой
        controller.setAnimationSpeedHandler(animatable -> {
            return (double)this.animationSpeed;
        });

        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // Сохранение/загрузка данных моба в NBT
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

    // Обработка событий
    @Override
    public void tick() {
        super.tick();

        // Проверяем, есть ли данные моба, и если нет - пытаемся синхронизировать
        if (mobData == null && !this.level().isClientSide && this.getMobId() != null && !this.getMobId().isEmpty()) {
            // Это должно происходить на сервере, когда мы загружаем данные моба
            if (this.level() instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel) this.level();
                System.out.println("CustomMobEntity: Attempting to load mob data for ID " + this.getMobId() +
                        " in tick() method");

                // Этот код имитирует поведение MobSpawnEventHandler для мобов, которые были загружены из NBT
                com.custommobsforge.custommobsforge.common.config.MobConfigManager.init(serverLevel);
                MobData mobData = com.custommobsforge.custommobsforge.common.config.MobConfigManager.loadMobConfig(this.getMobId(), serverLevel);

                if (mobData != null) {
                    System.out.println("CustomMobEntity: Successfully loaded mob data in tick() method");
                    this.setMobData(mobData);
                }
            }
        }

        // Если это новый моб и у него есть данные, проигрываем анимацию SPAWN
        if (isNewlySpawned && mobData != null && !this.level().isClientSide) {
            System.out.println("CustomMobEntity: Entity " + this.getId() + " is newly spawned, playing SPAWN animation");
            this.playAnimation("SPAWN");
            isNewlySpawned = false;
            return; // Выходим, чтобы не перезаписать анимацию SPAWN сразу же
        }

        // Автоматическое проигрывание анимаций IDLE и WALK
        if (!this.level().isClientSide && mobData != null &&
                System.currentTimeMillis() - lastAnimationTime > ANIMATION_COOLDOWN &&
                !this.isDeadOrDying() && this.hurtTime <= 0) { // Не проигрываем IDLE/WALK если моб умирает или получает урон

            // Проверяем скорость движения
            double speed = this.getDeltaMovement().horizontalDistance();

            if (speed > 0.01) {
                // Моб движется - проигрываем WALK, если это не текущая анимация
                if (!lastPlayedAnimation.equals("WALK")) {
                    System.out.println("CustomMobEntity: Entity " + this.getId() + " is moving, playing WALK animation");
                    this.playAnimation("WALK");
                }
            } else {
                // Моб стоит - проигрываем IDLE, если это не текущая анимация
                if (!lastPlayedAnimation.equals("IDLE")) {
                    System.out.println("CustomMobEntity: Entity " + this.getId() + " is idle, playing IDLE animation");
                    this.playAnimation("IDLE");
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (super.hurt(source, amount)) {
            // Воспроизводим анимацию получения урона
            System.out.println("CustomMobEntity: Entity " + this.getId() + " hurt, playing HURT animation");
            this.playAnimation("HURT");
            return true;
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        // Воспроизводим анимацию смерти перед вызовом родительского метода
        System.out.println("CustomMobEntity: Entity " + this.getId() + " died, playing DEATH animation");
        this.playAnimation("DEATH");
        super.die(source);
    }
}