package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class PlayAnimationNodeExecutor implements NodeExecutor {
    private static final Logger LOGGER = LogManager.getLogger("CustomMobsForge");

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        LOGGER.info("PlayAnimationNodeExecutor: Executing node {} of type {} for entity {}",
                node.getId(), node.getType(), entity.getId());

        // Вывод всех параметров узла
        LOGGER.info("PlayAnimationNodeExecutor: Node parameters:");
        LOGGER.info("  - description: {}", node.getDescription());
        LOGGER.info("  - parameter: {}", node.getParameter());
        LOGGER.info("  - animationId: {}", node.getAnimationId());
        LOGGER.info("  - animationSpeed: {}", node.getAnimationSpeed());
        LOGGER.info("  - loopAnimation: {}", node.isLoopAnimation());

        if (node.getCustomParameters() != null) {
            LOGGER.info("  - Custom parameters:");
            for (Map.Entry<String, Object> entry : node.getCustomParameters().entrySet()) {
                LOGGER.info("    * {} = {}", entry.getKey(), entry.getValue());
            }
        }

        // Получаем параметры анимации
        String animationId = null;

        // 1. Проверим параметр animation в customParameters
        animationId = node.getCustomParameterAsString("animation", null);
        if (animationId != null && !animationId.isEmpty()) {
            LOGGER.info("PlayAnimationNodeExecutor: Using animation from customParameter: {}", animationId);
        }
        // 2. Проверим параметр в обычном параметре узла
        else if (node.getParameter() != null && !node.getParameter().isEmpty()) {
            String param = node.getParameter();
            LOGGER.info("PlayAnimationNodeExecutor: Parsing parameter: {}", param);

            if (param.contains("animation=")) {
                String[] parts = param.split("animation=", 2);
                if (parts.length > 1) {
                    String part = parts[1];
                    // Если есть другие параметры после, отделенные точкой с запятой
                    if (part.contains(";")) {
                        animationId = part.split(";", 2)[0];
                    } else {
                        animationId = part;
                    }
                    LOGGER.info("PlayAnimationNodeExecutor: Extracted animation value from parameter: {}", animationId);
                }
            }
        }
        // 3. В последнюю очередь проверим поле animationId
        if ((animationId == null || animationId.isEmpty()) && node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
            animationId = node.getAnimationId();
            LOGGER.info("PlayAnimationNodeExecutor: Using value from animationId field: {}", animationId);
        }

        // Если нет анимации, возвращаем неудачу
        if (animationId == null || animationId.isEmpty()) {
            LOGGER.error("PlayAnimationNodeExecutor: ERROR - No animation ID found for node {}", node.getId());
            return false;
        }

        // Получаем настройки воспроизведения
        boolean loop = node.isLoopAnimation();
        // Проверим параметр узла
        if (node.getParameter() != null && node.getParameter().contains("loop=")) {
            try {
                String param = node.getParameter();
                String[] parts = param.split("loop=", 2);
                if (parts.length > 1) {
                    String value = parts[1];
                    if (value.contains(";")) {
                        value = value.split(";", 2)[0];
                    }
                    loop = Boolean.parseBoolean(value);
                    LOGGER.info("PlayAnimationNodeExecutor: Extracted loop value from parameter: {}", loop);
                }
            } catch (Exception e) {
                LOGGER.error("PlayAnimationNodeExecutor: Error parsing loop parameter: {}", e.getMessage());
            }
        }

        float speed = (float) node.getAnimationSpeed();
        if (speed <= 0.001f) {
            // Проверим параметр скорости
            if (node.getParameter() != null && node.getParameter().contains("speed=")) {
                try {
                    String param = node.getParameter();
                    String[] parts = param.split("speed=", 2);
                    if (parts.length > 1) {
                        String value = parts[1];
                        if (value.contains(";")) {
                            value = value.split(";", 2)[0];
                        }
                        speed = Float.parseFloat(value);
                        LOGGER.info("PlayAnimationNodeExecutor: Extracted speed value from parameter: {}", speed);
                    }
                } catch (Exception e) {
                    LOGGER.error("PlayAnimationNodeExecutor: Error parsing speed parameter: {}", e.getMessage());
                }
            }

            // Если скорость все еще не задана, используем значение по умолчанию
            if (speed <= 0.001f) {
                speed = 1.0f;
                LOGGER.info("PlayAnimationNodeExecutor: Using default speed: {}", speed);
            }
        }

        // Воспроизводим анимацию НАПРЯМУЮ, без маппинга
        LOGGER.info("PlayAnimationNodeExecutor: PLAYING ANIMATION '{}' for entity {} (loop: {}, speed: {})",
                animationId, entity.getId(), loop, speed);

        // Вызываем метод непосредственно для проигрывания анимации
        entity.playAnimationDirect(animationId, loop, speed);

        return true;
    }
}