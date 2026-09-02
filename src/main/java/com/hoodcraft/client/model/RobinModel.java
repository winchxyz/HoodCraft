package com.hoodcraft.client.model;

import com.hoodcraft.HoodCraft;
import com.hoodcraft.entity.Robin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Robin's model.
 *
 * <p>The cube layout is ported from Alex's Mobs' Blue Jay (GPL-3.0), rebuilt against vanilla's
 * {@link LayerDefinition} so the mod carries no runtime dependency on Citadel. The animation is
 * written fresh in the vanilla parrot's idiom, since a Robin is meant to read as a parrot.
 *
 * <p>Every part rests at a non-zero angle, so each frame restores the rest pose first and animates
 * as a delta on top of it. Overwriting the rotations outright - as vanilla's own bird models do,
 * since theirs rest at zero - would flatten the bird's posture.
 */
public class RobinModel extends HierarchicalModel<Robin> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(HoodCraft.id("robin"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart crest;
    private final ModelPart tail;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    // Rest pose, captured once so each frame can animate relative to it.
    private final float bodyRestX;
    private final float headRestX;
    private final float crestRestX;
    private final float legRestX;

    public RobinModel(ModelPart root) {
        this.root = root;
        // The mesh root is the bare container the layer bakes into; "root" is the bird's own pivot
        // at floor level, which everything else hangs from. Body is a grandchild, not a child.
        ModelPart pivot = root.getChild("root");
        this.body = pivot.getChild("body");
        this.head = this.body.getChild("head");
        this.crest = this.head.getChild("crest");
        this.tail = this.body.getChild("tail");
        this.leftWing = this.body.getChild("left_wing");
        this.rightWing = this.body.getChild("right_wing");
        this.leftLeg = this.body.getChild("left_leg");
        this.rightLeg = this.body.getChild("right_leg");

        this.bodyRestX = this.body.xRot;
        this.headRestX = this.head.xRot;
        this.crestRestX = this.crest.xRot;
        this.legRestX = this.leftLeg.xRot;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();

        PartDefinition root = parts.addOrReplaceChild("root",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -4.0F, -4.0F, 4.0F, 4.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, -3.2F, 0.0F, -0.1309F, 0.0F, 0.0F));

        body.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(26, 10).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(1.5F, 0.0F, 1.0F, 0.1309F, 0.0F, 0.0F));

        body.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(26, 10).mirror().addBox(-1.5F, 0.0F, -2.0F, 3.0F, 3.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(-1.5F, 0.0F, 1.0F, 0.1309F, 0.0F, 0.0F));

        body.addOrReplaceChild("tail",
                CubeListBuilder.create()
                        .texOffs(0, 22).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 1.0F, 7.0F),
                PartPose.offset(0.0F, -3.0F, 3.0F));

        body.addOrReplaceChild("left_wing",
                CubeListBuilder.create()
                        .texOffs(15, 14).addBox(0.0F, -1.0F, -1.0F, 1.0F, 3.0F, 8.0F),
                PartPose.offset(2.0F, -3.0F, -2.0F));

        body.addOrReplaceChild("right_wing",
                CubeListBuilder.create()
                        .texOffs(15, 14).mirror().addBox(-1.0F, -1.0F, -1.0F, 1.0F, 3.0F, 8.0F).mirror(false),
                PartPose.offset(-2.0F, -3.0F, -2.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(23, 0).addBox(-2.5F, -3.0F, -3.0F, 5.0F, 4.0F, 5.0F)
                        .texOffs(26, 16).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 2.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, -4.0F, 0.2182F, 0.0F, 0.0F));

        head.addOrReplaceChild("crest",
                CubeListBuilder.create()
                        .texOffs(0, 12).addBox(-2.5F, -1.0F, 0.0F, 5.0F, 3.0F, 6.0F, new CubeDeformation(-0.01F)),
                PartPose.offsetAndRotation(0.0F, -2.0F, -1.0F, 0.3491F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(Robin entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.resetPose();

        this.head.xRot = this.headRestX + headPitch * Mth.DEG_TO_RAD;
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;

        if (entity.isOrderedToSit()) {
            this.animateSitting();
            return;
        }

        // Wing flap: interpolated from the entity so it stays in step with actual flight.
        float partialTick = ageInTicks - entity.tickCount;
        float flapAmount = Mth.clamp(
                Mth.lerp(partialTick, entity.oFlapSpeed, entity.flapSpeed), 0.0F, 1.0F);
        float wingBeat = Mth.cos(ageInTicks * 0.9F) * flapAmount;

        // At rest the wings are folded flat against the body; in the air they spread and beat.
        float spread = 0.35F * flapAmount;
        this.leftWing.zRot = -spread - wingBeat * 0.9F;
        this.rightWing.zRot = spread + wingBeat * 0.9F;
        this.leftWing.xRot = -wingBeat * 0.25F;
        this.rightWing.xRot = -wingBeat * 0.25F;

        // Flying tucks the legs up and pitches the body forward.
        this.body.xRot = this.bodyRestX + flapAmount * 0.17F;
        this.leftLeg.xRot = this.legRestX + flapAmount * 0.70F;
        this.rightLeg.xRot = this.legRestX + flapAmount * 0.70F;
        this.tail.xRot = -flapAmount * 0.10F + Mth.cos(ageInTicks * 0.1F) * 0.05F;

        // On the ground it walks; the legs stride and the whole bird bobs.
        float grounded = 1.0F - flapAmount;
        if (grounded > 0.0F) {
            this.leftLeg.xRot += Mth.cos(limbSwing * 0.95F) * 1.1F * limbSwingAmount * grounded;
            this.rightLeg.xRot += Mth.cos(limbSwing * 0.95F + Mth.PI) * 1.1F * limbSwingAmount * grounded;
            this.body.xRot += Mth.cos(limbSwing * 1.90F) * 0.08F * limbSwingAmount * grounded;
            this.tail.xRot += Mth.cos(limbSwing * 0.95F) * 0.30F * limbSwingAmount * grounded;
        }

        // The crest never sits quite still.
        this.crest.xRot = this.crestRestX + Mth.cos(ageInTicks * 0.1F) * 0.10F;
    }

    /** A perched bird: legs folded under it, wings shut, crest raised. */
    private void animateSitting() {
        this.body.y += 1.5F;
        this.leftLeg.xRot = this.legRestX + 1.4F;
        this.rightLeg.xRot = this.legRestX + 1.4F;
        this.leftWing.zRot = 0.0F;
        this.rightWing.zRot = 0.0F;
        this.crest.xRot = this.crestRestX + 0.15F;
    }

    /**
     * Pose and draw the bird as a shoulder passenger.
     *
     * <p>A shoulder-riding bird exists only as an NBT tag on the player, not as a live entity, so
     * there is nothing to hand {@link #setupAnim}. This poses it as a perched bird instead.
     */
    public void renderOnShoulder(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                                 float limbSwing, float limbSwingAmount,
                                 float netHeadYaw, float headPitch, int tickCount) {
        this.resetPose();
        this.head.xRot = this.headRestX + headPitch * Mth.DEG_TO_RAD;
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.leftLeg.xRot += Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.rightLeg.xRot += Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.4F * limbSwingAmount;
        this.crest.xRot = this.crestRestX + Mth.cos(tickCount * 0.1F) * 0.10F;
        this.root.render(poseStack, buffer, packedLight, packedOverlay);
    }

    /** Restore every animated part to the pose baked into the layer definition. */
    private void resetPose() {
        this.body.xRot = this.bodyRestX;
        this.body.y = -3.2F;
        this.head.xRot = this.headRestX;
        this.head.yRot = 0.0F;
        this.head.zRot = 0.0F;
        this.crest.xRot = this.crestRestX;
        this.tail.xRot = 0.0F;
        this.leftWing.setRotation(0.0F, 0.0F, 0.0F);
        this.rightWing.setRotation(0.0F, 0.0F, 0.0F);
        this.leftLeg.xRot = this.legRestX;
        this.rightLeg.xRot = this.legRestX;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int packedLight, int packedOverlay, int color) {
        if (!this.young) {
            super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
            return;
        }

        // Chicks are half-size with an oversized head, the same proportions Alex's Mobs used.
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.translate(0.0F, 1.5F, 0.0F);
        this.head.xScale = 1.35F;
        this.head.yScale = 1.35F;
        this.head.zScale = 1.35F;
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
        this.head.xScale = 1.0F;
        this.head.yScale = 1.0F;
        this.head.zScale = 1.0F;
        poseStack.popPose();
    }
}
