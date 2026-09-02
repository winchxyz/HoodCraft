package com.hoodcraft.client.renderer;

import com.hoodcraft.HoodCraft;
import com.hoodcraft.client.model.RobinModel;
import com.hoodcraft.entity.Robin;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RobinRenderer extends MobRenderer<Robin, RobinModel> {

    public static final ResourceLocation TEXTURE = HoodCraft.id("textures/entity/robin.png");

    public RobinRenderer(EntityRendererProvider.Context context) {
        super(context, new RobinModel(context.bakeLayer(RobinModel.LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(Robin entity) {
        return TEXTURE;
    }
}
