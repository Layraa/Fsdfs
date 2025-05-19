package com.custommobsforge.custommobsforge.common.fsm.states;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.fsm.MobState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Состояние ожидания для моба
 */
public class IdleState extends MobState {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");
    private long lastPlayerCheckTime = 0;
    private static final long PLAYER_CHECK_INTERVAL = 1000; // 1 секунда

    public IdleState() {
        super("idle", "Idle State");
    }

    @Override
    public void enter(CustomMobEntity entity) {
        super.enter(entity);
        LOGGER.info("IdleState: Entity {} entered idle state", entity.getId());
        entity.getAnimationAdapter().playAnimation("IDLE", true, 1.0f);
    }

    @Override
    public void update(CustomMobEntity entity) {
        // Проверяем, не пора ли искать игрока
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPlayerCheckTime > PLAYER_CHECK_INTERVAL) {
            lastPlayerCheckTime = currentTime;

            // Проверяем, есть ли поблизости игрок
            Player nearestPlayer = findNearestPlayer(entity);
            if (nearestPlayer != null) {
                // Если игрок найден, переходим в состояние следования
                double distance = entity.distanceTo(nearestPlayer);
                if (distance < 16.0) {
                    LOGGER.info("IdleState: Found player at distance {}, changing to follow state", distance);
                    entity.getStateManager().changeState("follow");
                    return;
                }
            }
        }
    }

    @Override
    public void exit(CustomMobEntity entity) {
        LOGGER.info("IdleState: Entity {} exited idle state", entity.getId());
        super.exit(entity);
    }

    /**
     * Находит ближайшего игрока
     * @param entity Сущность
     * @return Ближайший игрок или null, если игрок не найден
     */
    private Player findNearestPlayer(CustomMobEntity entity) {
        return entity.level().getNearestPlayer(
                TargetingConditions.forNonCombat().range(16.0D),
                entity
        );
    }
}