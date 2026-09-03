package com.hoodcraft.client.renderer;

import com.hoodcraft.HoodCraft;
import com.hoodcraft.client.model.RayModel;
import com.hoodcraft.entity.Ray;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RayRenderer extends MobRenderer<Ray, RayModel> {

    public static final ResourceLocation TEXTURE = HoodCraft.id("textures/entity/ray.png");

    public RayRenderer(EntityRendererProvider.Context context) {
        super(context, new RayModel(context.bakeLayer(RayModel.LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(Ray entity) {
        return TEXTURE;
    }
}
