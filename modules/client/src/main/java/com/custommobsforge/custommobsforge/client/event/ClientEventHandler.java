package com.custommobsforge.custommobsforge.client.event;

import com.custommobsforge.custommobsforge.client.gui.MobCreatorGUI;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.event.MobDataReceivedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "custommobsforge_client", bus = Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onMobDataReceived(MobDataReceivedEvent event) {
        // Получаем данные моба
        com.custommobsforge.custommobsforge.common.data.MobData data = event.getMobData();

        if (data != null) {
            System.out.println("ClientEventHandler: Received mob data from server for ID: " + data.getId() +
                    ", name: " + data.getName() +
                    ", model: " + data.getModelPath() +
                    ", texture: " + data.getTexturePath());

            // Добавляем в клиентский кэш
            MobCreatorGUI gui = MobCreatorGUI.getInstance();
            if (gui != null) {
                gui.getMobSaveService().updateCache(data);
            }

            // Обновляем данные всех существующих мобов с этим ID
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof CustomMobEntity) {
                        CustomMobEntity mobEntity = (CustomMobEntity) entity;
                        if (mobEntity.getMobId() != null && mobEntity.getMobId().equals(data.getId())) {
                            System.out.println("ClientEventHandler: Updating existing entity " + entity.getId() +
                                    " with mob data for ID: " + data.getId());
                            mobEntity.setMobData(data);
                        }
                    }
                }
            }
        } else {
            System.err.println("ClientEventHandler: Received null mob data from server");
        }
    }
}