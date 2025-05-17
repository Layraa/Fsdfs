package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import java.util.List;

public class DisplayTitleNodeExecutor implements NodeExecutor {
    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры
        String titleText = node.getCustomParameterAsString("titleText", "");
        String subtitleText = node.getCustomParameterAsString("subtitleText", "");
        double displayTime = node.getCustomParameterAsDouble("displayTime", 3.0);
        String color = node.getCustomParameterAsString("color", "white");

        // Если оба текста пустые, ничего не делаем
        if (titleText.isEmpty() && subtitleText.isEmpty()) {
            return false;
        }

        // Проверяем, является ли мир серверным
        if (!(entity.level() instanceof ServerLevel)) {
            return true; // На клиенте ничего не делаем
        }

        // Находим игроков поблизости
        List<ServerPlayer> players = ((ServerLevel) entity.level()).getPlayers(player ->
                player.distanceTo(entity) < 32.0
        );

        // Отправляем заголовок игрокам
        for (ServerPlayer player : players) {
            sendTitle(player, titleText, subtitleText, (int) (displayTime * 20));
        }

        return true;
    }

    // Метод для отправки заголовка игроку (обновлено для 1.20.1)
    private void sendTitle(ServerPlayer player, String title, String subtitle, int duration) {
        Component titleComponent = Component.literal(title);
        Component subtitleComponent = Component.literal(subtitle);

        // В Minecraft 1.20.1 изменился API для отправки заголовков
        // Используем новые классы пакетов
        if (!title.isEmpty()) {
            player.connection.send(new ClientboundSetTitleTextPacket(titleComponent));
        }

        if (!subtitle.isEmpty()) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitleComponent));
        }

        // Настраиваем анимацию (fadeIn, stay, fadeOut)
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, duration, 10));
    }
}