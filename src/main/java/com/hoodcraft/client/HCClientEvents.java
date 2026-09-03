package com.hoodcraft.client;

import com.hoodcraft.HoodCraft;
import com.hoodcraft.client.model.CashCatModel;
import com.hoodcraft.client.model.RobinModel;
import com.hoodcraft.client.renderer.CashCatRenderer;
import com.hoodcraft.client.renderer.RobinOnShoulderLayer;
import com.hoodcraft.client.renderer.RobinRenderer;
import com.hoodcraft.registry.HCEntities;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = HoodCraft.MODID, value = Dist.CLIENT)
public final class HCClientEvents {

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RobinModel.LAYER, RobinModel::createBodyLayer);
        event.registerLayerDefinition(CashCatModel.LAYER, CashCatModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HCEntities.ROBIN.get(), RobinRenderer::new);
        event.registerEntityRenderer(HCEntities.CASH_CAT.get(), CashCatRenderer::new);
    }

    @SubscribeEvent
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new RobinOnShoulderLayer(renderer, event.getEntityModels()));
            }
        }
    }

    private HCClientEvents() {
    }
}
