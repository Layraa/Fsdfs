package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;

public class OnDeathNodeExecutor implements NodeExecutor {
    private static final Random random = new Random();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры
        boolean dropItemsEnabled = node.getCustomParameterAsBoolean("dropItemsEnabled", false);
        boolean showDeathMessage = node.getCustomParameterAsBoolean("showDeathMessage", false);

        // Воспроизводим анимацию смерти
        if (node.getAnimationId() != null && !node.getAnimationId().isEmpty()) {
            entity.setAnimation(node.getAnimationId(), node.isLoopAnimation(), (float) node.getAnimationSpeed());
        } else {
            executor.playAnimation("DEATH");
        }

        // Выпадение предметов при смерти
        if (dropItemsEnabled && entity.level() instanceof ServerLevel) {
            dropItems(entity);
        }

        // Отображение сообщения о смерти
        if (showDeathMessage && entity.level() instanceof ServerLevel) {
            // Это будет обрабатываться на более высоком уровне через обработчик событий
        }

        // Выполняем дочерние узлы
        for (BehaviorNode child : executor.getChildNodes(node)) {
            executor.executeNode(child);
        }

        return true;
    }

    // Метод для выпадения предметов при смерти
    private void dropItems(CustomMobEntity entity) {
        // В реальном приложении здесь может быть более сложная логика выпадения предметов
        // на основе конфигурации моба

        // Для примера, выпадение случайного предмета
        if (random.nextFloat() < 0.5f) {
            ItemStack itemStack = new ItemStack(Items.BONE, 1 + random.nextInt(2));
            ItemEntity itemEntity = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), itemStack);
            entity.level().addFreshEntity(itemEntity);
        }
    }
}