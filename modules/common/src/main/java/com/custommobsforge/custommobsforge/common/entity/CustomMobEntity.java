package com.custommobsforge.custommobsforge.common.entity;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.data.AnimationMapping;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.AnimationSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob; // Изменено с Mob на PathfinderMob
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

public class CustomMobEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> MOB_ID =
            SynchedEntityData.defineId(CustomMobEntity.class, EntityDataSerializers.STRING);

    private MobData mobData;
    private final AnimatableInstanceCache cache = AzureLibUtil.createInstanceCache(this);
    private String currentAnimation = "animation.idle";
    private boolean isLoopingAnimation = true;
    private float animationSpeed = 1.0f;

    public CustomMobEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
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

        // Специфические цели будут добавляться в серверном модуле
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MOB_ID, "");
    }

    public void setMobId(String mobId) {
        this.entityData.set(MOB_ID, mobId);
    }

    public String getMobId() {
        return this.entityData.get(MOB_ID);
    }

    public MobData getMobData() {
        return mobData;
    }

    public void setMobData(MobData mobData) {
        this.mobData = mobData;

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
    }

    // Методы для анимаций
    public void playAnimation(String action) {
        if (mobData != null && mobData.getAnimations() != null) {
            AnimationMapping mapping = mobData.getAnimations().get(action);

            if (mapping != null) {
                String animationId = mapping.getAnimationName();
                boolean loop = mapping.isLoop();
                float speed = mapping.getSpeed();

                this.currentAnimation = animationId;
                this.isLoopingAnimation = loop;
                this.animationSpeed = speed;

                // Синхронизируем анимацию с клиентами
                if (!this.level().isClientSide) {
                    NetworkManager.INSTANCE.send(
                            PacketDistributor.TRACKING_ENTITY.with(() -> this),
                            new AnimationSyncPacket(this.getId(), animationId, speed, loop)
                    );
                }
            }
        }
    }

    public void setAnimation(String animationId, boolean loop, float speed) {
        this.currentAnimation = animationId;
        this.isLoopingAnimation = loop;
        this.animationSpeed = speed;
    }

    // Методы GeoEntity для AzureLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<CustomMobEntity> controller = new AnimationController<>(this, "controller", 0, event -> {
            // Используем правильный тип LoopType для анимации
            if (isLoopingAnimation) {
                return event.setAndContinue(RawAnimation.begin().then(currentAnimation, Animation.LoopType.LOOP));
            } else {
                return event.setAndContinue(RawAnimation.begin().then(currentAnimation, Animation.LoopType.PLAY_ONCE));
            }
        });

        // Используем setAnimationSpeedHandler с лямбдой
        controller.setAnimationSpeedHandler(animatable -> (double)this.animationSpeed);

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
        compound.putString("MobId", this.getMobId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        String mobId = compound.getString("MobId");
        this.setMobId(mobId);
    }

    // Обработка событий
    @Override
    public void tick() {
        super.tick();

        // Логика обновления моба будет добавлена в серверном модуле
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (super.hurt(source, amount)) {
            // Воспроизводим анимацию получения урона
            this.playAnimation("HURT");
            return true;
        }
        return false;
    }
}