package com.hoodcraft.client.renderer;

import com.hoodcraft.client.model.RayModel;
import com.hoodcraft.registry.HCEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;

/**
 * Draws a Ray perched on a player's shoulder.
 *
 * <p>Vanilla's shoulder layer only knows how to draw parrots, so a modded bird riding a shoulder
 * would otherwise be invisible even though the game is tracking it correctly. This layer is added
 * to the player renderers and draws ours the same way.
 */
public class RayOnShoulderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final RayModel model;

    public RayOnShoulderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                                  EntityModelSet models) {
        super(parent);
        this.model = new RayModel(models.bakeLayer(RayModel.LAYER));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        this.renderOnShoulder(poseStack, buffer, packedLight, player,
                limbSwing, limbSwingAmount, netHeadYaw, headPitch, true);
        this.renderOnShoulder(poseStack, buffer, packedLight, player,
                limbSwing, limbSwingAmount, netHeadYaw, headPitch, false);
    }

    private void renderOnShoulder(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                  AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                  float netHeadYaw, float headPitch, boolean leftShoulder) {
        CompoundTag tag = leftShoulder ? player.getShoulderEntityLeft() : player.getShoulderEntityRight();
        EntityType.byString(tag.getString("id"))
                .filter(type -> type == HCEntities.RAY.get())
                .ifPresent(type -> {
                    poseStack.pushPose();
                    poseStack.translate(leftShoulder ? 0.4F : -0.4F, player.isCrouching() ? -1.3F : -1.5F, 0.0F);
                    VertexConsumer consumer =
                            buffer.getBuffer(this.model.renderType(RayRenderer.TEXTURE));
                    this.model.renderOnShoulder(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                            limbSwing, limbSwingAmount, netHeadYaw, headPitch, player.tickCount);
                    poseStack.popPose();
                });
    }
}
