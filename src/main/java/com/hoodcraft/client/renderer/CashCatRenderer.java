package com.hoodcraft.client.renderer;

import com.hoodcraft.HoodCraft;
import com.hoodcraft.client.model.CashCatModel;
import com.hoodcraft.entity.CashCat;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Two sheets: with a collar and without. The expression lives in the pose and the tear particles
 * rather than the texture, so cheering a cat up does not swap what it is wearing.
 */
public class CashCatRenderer extends MobRenderer<CashCat, CashCatModel> {

    private static final ResourceLocation STRAY = HoodCraft.id("textures/entity/cash_cat.png");
    private static final ResourceLocation TAMED = HoodCraft.id("textures/entity/cash_cat_tamed.png");

    public CashCatRenderer(EntityRendererProvider.Context context) {
        super(context, new CashCatModel(context.bakeLayer(CashCatModel.LAYER)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(CashCat cat) {
        return cat.isTame() ? TAMED : STRAY;
    }
}
