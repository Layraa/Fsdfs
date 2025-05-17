package com.custommobsforge.custommobsforge.client.handler;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.network.packet.AnimationSyncPacket;
import com.custommobsforge.custommobsforge.common.network.packet.PlayBehaviorNodePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationHandler {
    private static final Map<Integer, Long> activeNodeEffects = new HashMap<>();
    private static final long EFFECT_DURATION = 1000; // 1 секунда в миллисекундах

    public static void handleAnimationSync(AnimationSyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level.getEntity(packet.getEntityId());

        if (entity instanceof CustomMobEntity) {
            CustomMobEntity mobEntity = (CustomMobEntity) entity;
            mobEntity.setAnimation(packet.getAnimationId(), packet.isLoop(), packet.getAnimationSpeed());
        }
    }

    public static void handleNodeExecution(PlayBehaviorNodePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level.getEntity(packet.getEntityId());

        if (entity instanceof CustomMobEntity) {
            // Сохраняем время активации эффекта
            activeNodeEffects.put(packet.getEntityId(), System.currentTimeMillis());

            // В зависимости от типа узла, показываем разные частицы
            ParticleOptions particle = getParticleForNodeType(packet.getNodeType());
            if (particle != null) {
                // Спавним частицы вокруг моба
                spawnParticlesAroundEntity(entity, particle);
            }
        }
    }

    private static ParticleOptions getParticleForNodeType(String nodeType) {
        switch (nodeType.toLowerCase()) {
            case "attacknode":
                return ParticleTypes.SWEEP_ATTACK;
            case "playanimationnode":
                return ParticleTypes.NOTE;
            case "timernode":
                return ParticleTypes.SMOKE;
            case "follownode":
                return ParticleTypes.HEART;
            case "fleenode":
                return ParticleTypes.CLOUD;
            default:
                return null;
        }
    }

    private static void spawnParticlesAroundEntity(Entity entity, ParticleOptions particle) {
        ParticleEngine particleEngine = Minecraft.getInstance().particleEngine;

        // Спавним частицы вокруг моба
        for (int i = 0; i < 10; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetY = Math.random() * 2;
            double offsetZ = (Math.random() - 0.5) * 2;

            particleEngine.createParticle(
                    particle,
                    entity.getX() + offsetX,
                    entity.getY() + offsetY,
                    entity.getZ() + offsetZ,
                    0, 0, 0
            );
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Обновляем эффекты узлов
            long currentTime = System.currentTimeMillis();
            activeNodeEffects.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > EFFECT_DURATION);
        }
    }
}