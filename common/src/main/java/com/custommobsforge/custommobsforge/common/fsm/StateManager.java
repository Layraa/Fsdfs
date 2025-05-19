// com.custommobsforge.custommobsforge.common.fsm.StateManager
package com.custommobsforge.custommobsforge.common.fsm;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.event.system.EventSystem;
import com.custommobsforge.custommobsforge.common.event.system.StateChangedEvent;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.StateUpdatePacket;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Менеджер состояний для конечного автомата
 */
public class StateManager {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    private final CustomMobEntity entity;
    private final Map<String, MobState> registeredStates = new HashMap<>();
    private MobState currentState;
    private String initialStateId;

    /**
     * Создает менеджер состояний
     * @param entity Сущность
     */
    public StateManager(CustomMobEntity entity) {
        this.entity = entity;
    }

    /**
     * Регистрирует состояние
     * @param state Состояние
     */
    public void registerState(MobState state) {
        registeredStates.put(state.getId(), state);
    }

    /**
     * Устанавливает начальное состояние
     * @param stateId ID состояния
     */
    public void setInitialState(String stateId) {
        initialStateId = stateId;
        if (currentState == null) {
            changeState(stateId);
        }
    }

    /**
     * Изменяет текущее состояние
     * @param stateId ID нового состояния
     * @return true, если состояние успешно изменено
     */
    public boolean changeState(String stateId) {
        MobState newState = registeredStates.get(stateId);
        if (newState == null) {
            LOGGER.error("StateManager: State with ID {} not found", stateId);
            return false;
        }

        MobState oldState = currentState;

        if (oldState != null) {
            LOGGER.info("StateManager: Changing state from {} to {}",
                    oldState.getId(), newState.getId());
            oldState.exit(entity);
        } else {
            LOGGER.info("StateManager: Setting initial state to {}", newState.getId());
        }

        currentState = newState;
        currentState.enter(entity);

        // Генерируем событие смены состояния
        EventSystem.fireEvent(new StateChangedEvent(oldState, newState, entity));

        // Отправляем пакет об обновлении состояния клиентам
        if (!entity.level().isClientSide) {
            Map<String, Object> stateData = newState.getSerializableData();
            NetworkManager.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                    new StateUpdatePacket(entity.getId(), newState.getId(), stateData)
            );
        }

        return true;
    }

    /**
     * Обновляет текущее состояние
     */
    public void update() {
        if (currentState == null) {
            if (initialStateId != null) {
                changeState(initialStateId);
            } else if (!registeredStates.isEmpty()) {
                // Если начальное состояние не задано, выбираем первое из зарегистрированных
                String firstStateId = registeredStates.keySet().iterator().next();
                changeState(firstStateId);
            }
        }

        if (currentState != null) {
            currentState.update(entity);
        }
    }

    /**
     * Получает текущее состояние
     * @return Текущее состояние
     */
    public MobState getCurrentState() {
        return currentState;
    }

    /**
     * Получает состояние по ID
     * @param stateId ID состояния
     * @return Состояние
     */
    public MobState getState(String stateId) {
        return registeredStates.get(stateId);
    }
}