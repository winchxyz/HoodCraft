package com.hoodcraft.client.model;

import com.google.common.collect.ImmutableList;
import com.hoodcraft.HoodCraft;
import com.hoodcraft.entity.CashCat;
import net.minecraft.client.model.AgeableListModel;
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
 * The Cash Cat: vanilla's cat, reposed.
 *
 * <p>The cube layout and every texture offset are vanilla's ocelot mesh, so a texture drawn against
 * the standard 64x32 cat sheet maps straight on. Two things differ.
 *
 * <p>The ears are their own parts rather than two extra boxes on the head cube. Their pivots and UVs
 * are placed so that a rotation of zero reproduces vanilla's geometry exactly - nothing moves until
 * the model asks it to - which buys the droop that carries most of the sad expression.
 *
 * <p>And the default pose is the mascot's: a steeper, more upright sit than vanilla's, front legs
 * straight rather than angled, head hung forward. It holds that pose whenever the cat is miserable
 * and standing still. Feed it gold and it stands up and moves like an ordinary cat until the day
 * runs out.
 */
public class CashCatModel extends AgeableListModel<CashCat> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HoodCraft.id("cash_cat"), "main");

    // --- the mascot sit -----------------------------------------------------
    // Vanilla's sitting cat leans back at 45 degrees. The crying cat sits far more upright, almost
    // hunched forward over its own front legs, which is most of why the pose reads as dejected
    // rather than merely seated.
    private static final float SAD_BODY_X_ROT = 0.62F;
    private static final float SAD_BODY_Y = -5.2F;
    private static final float SAD_BODY_Z = 6.2F;
    private static final float SAD_HEAD_Y = -4.6F;
    private static final float SAD_HEAD_Z = 1.6F;
    /** Chin tucked toward the chest. */
    private static final float SAD_HEAD_X_ROT = 0.30F;
    // Front legs hang vertically from the chest to the floor, close together and a little forward
    // of the body - the pose the mascot is sitting in. The leg box is 10 units long, so a pivot at
    // 14.1 puts the paw on the ground at 24; raising the pivot instead leaves the legs dangling in
    // mid-air, splayed out behind the tilted body.
    private static final float SAD_FRONT_LEG_Y = 14.1F;
    private static final float SAD_FRONT_LEG_Z = -6.0F;
    private static final float SAD_FRONT_LEG_X = 1.0F;
    // Hind legs: folded flat so the thigh lies along the ground beside the body, then swung out
    // into a shallow V so the haunches are visible from the side.
    //
    // Two things matter and both were got wrong first time round. The fold has to be a full right
    // angle, which maps the leg's 6-long axis to point forward and leaves the box hanging 1-3 units
    // under the pivot - so the pivot must sit at 21, putting the underside exactly on the ground at
    // 24. Anything higher leaves the legs floating. And the splay is yRot, not zRot: once the leg
    // has been folded flat, yRot swings it sideways within the ground plane, whereas zRot tips it
    // up into the air.
    private static final float SAD_HIND_LEG_X = 1.8F;
    private static final float SAD_HIND_LEG_Y = 21.0F;
    private static final float SAD_HIND_LEG_Z = 2.0F;
    private static final float SAD_HIND_LEG_FOLD = (float) (-Math.PI / 2);
    private static final float SAD_HIND_LEG_SPLAY = 0.32F;

    /** Ears folded back and splayed outward - the single clearest misery tell. */
    private static final float SAD_EAR_X_ROT = 0.85F;
    private static final float SAD_EAR_Z_ROT = 0.42F;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart leftHindLeg;
    private final ModelPart rightHindLeg;
    private final ModelPart leftFrontLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftEar;
    private final ModelPart rightEar;

    private float sitAmount;

    public CashCatModel(ModelPart root) {
        super(true, 10.0F, 4.0F);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.tail1 = root.getChild("tail1");
        this.tail2 = root.getChild("tail2");
        this.leftHindLeg = root.getChild("left_hind_leg");
        this.rightHindLeg = root.getChild("right_hind_leg");
        this.leftFrontLeg = root.getChild("left_front_leg");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.leftEar = this.head.getChild("left_ear");
        this.rightEar = this.head.getChild("right_ear");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;
        CubeDeformation tailTip = new CubeDeformation(-0.02F);

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .addBox("main", -2.5F, -2.0F, -3.0F, 5.0F, 4.0F, 5.0F, none)
                        .addBox("nose", -1.5F, -0.001F, -4.0F, 3, 2, 2, none, 0, 24),
                PartPose.offset(0.0F, 15.0F, -9.0F));

        // Vanilla draws the ears as boxes at (-2,-3,0) and (1,-3,0) on the head cube. Re-hung here
        // on their own pivots at those boxes' centres, with the same size and UV, so an unrotated
        // ear sits exactly where vanilla's does.
        head.addOrReplaceChild("left_ear",
                CubeListBuilder.create().texOffs(0, 10).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 2.0F, none),
                PartPose.offset(-1.5F, -2.5F, 1.0F));
        head.addOrReplaceChild("right_ear",
                CubeListBuilder.create().texOffs(6, 10).addBox(-0.5F, -0.5F, -1.0F, 1.0F, 1.0F, 2.0F, none),
                PartPose.offset(1.5F, -2.5F, 1.0F));

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(20, 0).addBox(-2.0F, 3.0F, -8.0F, 4.0F, 16.0F, 6.0F, none),
                PartPose.offsetAndRotation(0.0F, 12.0F, -10.0F, (float) (Math.PI / 2), 0.0F, 0.0F));

        root.addOrReplaceChild("tail1",
                CubeListBuilder.create().texOffs(0, 15).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F, none),
                PartPose.offsetAndRotation(0.0F, 15.0F, 8.0F, 0.9F, 0.0F, 0.0F));
        root.addOrReplaceChild("tail2",
                CubeListBuilder.create().texOffs(4, 15).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F, tailTip),
                PartPose.offset(0.0F, 20.0F, 14.0F));

        CubeListBuilder hind = CubeListBuilder.create().texOffs(8, 13).addBox(-1.0F, 0.0F, 1.0F, 2.0F, 6.0F, 2.0F, none);
        root.addOrReplaceChild("left_hind_leg", hind, PartPose.offset(1.1F, 18.0F, 5.0F));
        root.addOrReplaceChild("right_hind_leg", hind, PartPose.offset(-1.1F, 18.0F, 5.0F));

        CubeListBuilder front = CubeListBuilder.create().texOffs(40, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 10.0F, 2.0F, none);
        root.addOrReplaceChild("left_front_leg", front, PartPose.offset(1.2F, 14.1F, -5.0F));
        root.addOrReplaceChild("right_front_leg", front, PartPose.offset(-1.2F, 14.1F, -5.0F));

        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of(this.head);
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(this.body, this.leftHindLeg, this.rightHindLeg,
                this.leftFrontLeg, this.rightFrontLeg, this.tail1, this.tail2);
    }

    @Override
    public void prepareMobModel(CashCat cat, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(cat, limbSwing, limbSwingAmount, partialTick);
        this.resetPose();

        // Driven by the entity, not by the limb swing here: it applies hysteresis so the cat does
        // not bob up and down every time something jostles it, and interpolates so the change reads
        // as sitting down rather than snapping between two poses.
        this.sitAmount = cat.getSitAmount(partialTick);
        if (this.sitAmount > 0.0F) {
            this.applySadSit(this.sitAmount);
        }
    }

    /** Vanilla's standing skeleton, restored every frame before a pose is applied over it. */
    private void resetPose() {
        this.body.setPos(0.0F, 12.0F, -10.0F);
        this.body.xRot = (float) (Math.PI / 2);
        this.head.setPos(0.0F, 15.0F, -9.0F);
        this.head.xRot = 0.0F;
        this.tail1.setPos(0.0F, 15.0F, 8.0F);
        this.tail1.xRot = 0.9F;
        this.tail2.setPos(0.0F, 20.0F, 14.0F);
        this.leftFrontLeg.setPos(1.2F, 14.1F, -5.0F);
        this.rightFrontLeg.setPos(-1.2F, 14.1F, -5.0F);
        this.leftHindLeg.setPos(1.1F, 18.0F, 5.0F);
        this.rightHindLeg.setPos(-1.1F, 18.0F, 5.0F);
        this.leftFrontLeg.xRot = 0.0F;
        this.rightFrontLeg.xRot = 0.0F;
        this.leftHindLeg.xRot = 0.0F;
        this.rightHindLeg.xRot = 0.0F;
        this.leftHindLeg.yRot = 0.0F;
        this.rightHindLeg.yRot = 0.0F;
        this.leftEar.xRot = 0.0F;
        this.leftEar.zRot = 0.0F;
        this.rightEar.xRot = 0.0F;
        this.rightEar.zRot = 0.0F;
    }

    private static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }

    /**
     * Blend the standing skeleton toward the mascot sit.
     *
     * <p>Every value starts from whatever {@link #resetPose()} left behind, so at t=0 this is a
     * no-op and at t=1 it is the full pose; anything between is a real halfway crouch rather than
     * one pose or the other.
     */
    private void applySadSit(float t) {
        this.body.xRot = lerp(t, this.body.xRot, SAD_BODY_X_ROT);
        this.body.y = lerp(t, this.body.y, 12.0F + SAD_BODY_Y);
        this.body.z = lerp(t, this.body.z, -10.0F + SAD_BODY_Z);

        this.head.y = lerp(t, this.head.y, 15.0F + SAD_HEAD_Y);
        this.head.z = lerp(t, this.head.z, -9.0F + SAD_HEAD_Z);

        this.tail1.y = lerp(t, this.tail1.y, 15.0F + 8.0F);
        this.tail1.z = lerp(t, this.tail1.z, 8.0F - 2.0F);
        this.tail1.xRot = lerp(t, this.tail1.xRot, 1.7278761F);
        this.tail2.y = lerp(t, this.tail2.y, 20.0F + 2.0F);
        this.tail2.z = lerp(t, this.tail2.z, 14.0F - 0.8F);
        this.tail2.xRot = lerp(t, this.tail2.xRot, 2.670354F);

        this.leftFrontLeg.x = lerp(t, this.leftFrontLeg.x, SAD_FRONT_LEG_X);
        this.leftFrontLeg.y = lerp(t, this.leftFrontLeg.y, SAD_FRONT_LEG_Y);
        this.leftFrontLeg.z = lerp(t, this.leftFrontLeg.z, SAD_FRONT_LEG_Z);
        this.rightFrontLeg.x = lerp(t, this.rightFrontLeg.x, -SAD_FRONT_LEG_X);
        this.rightFrontLeg.y = lerp(t, this.rightFrontLeg.y, SAD_FRONT_LEG_Y);
        this.rightFrontLeg.z = lerp(t, this.rightFrontLeg.z, SAD_FRONT_LEG_Z);

        this.leftHindLeg.x = lerp(t, this.leftHindLeg.x, SAD_HIND_LEG_X);
        this.leftHindLeg.y = lerp(t, this.leftHindLeg.y, SAD_HIND_LEG_Y);
        this.leftHindLeg.z = lerp(t, this.leftHindLeg.z, SAD_HIND_LEG_Z);
        this.leftHindLeg.xRot = lerp(t, this.leftHindLeg.xRot, SAD_HIND_LEG_FOLD);
        this.leftHindLeg.yRot = lerp(t, this.leftHindLeg.yRot, -SAD_HIND_LEG_SPLAY);

        this.rightHindLeg.x = lerp(t, this.rightHindLeg.x, -SAD_HIND_LEG_X);
        this.rightHindLeg.y = lerp(t, this.rightHindLeg.y, SAD_HIND_LEG_Y);
        this.rightHindLeg.z = lerp(t, this.rightHindLeg.z, SAD_HIND_LEG_Z);
        this.rightHindLeg.xRot = lerp(t, this.rightHindLeg.xRot, SAD_HIND_LEG_FOLD);
        this.rightHindLeg.yRot = lerp(t, this.rightHindLeg.yRot, SAD_HIND_LEG_SPLAY);

        this.leftEar.xRot = lerp(t, this.leftEar.xRot, SAD_EAR_X_ROT);
        this.leftEar.zRot = lerp(t, this.leftEar.zRot, -SAD_EAR_Z_ROT);
        this.rightEar.xRot = lerp(t, this.rightEar.xRot, SAD_EAR_X_ROT);
        this.rightEar.zRot = lerp(t, this.rightEar.zRot, SAD_EAR_Z_ROT);
    }

    @Override
    public void setupAnim(CashCat cat, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.xRot += headPitch * ((float) Math.PI / 180F);
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);

        float standing = 1.0F - this.sitAmount;

        if (this.sitAmount > 0.0F) {
            // Chin down, and a slow breathing sway so a stationary cat is not a statue.
            this.head.xRot += SAD_HEAD_X_ROT * this.sitAmount;
            this.head.y += Mth.cos(ageInTicks * 0.09F) * 0.12F * this.sitAmount;
            this.tail2.xRot += Mth.cos(ageInTicks * 0.06F) * 0.06F * this.sitAmount;
        }

        if (standing <= 0.0F) {
            return;
        }

        // Scaled by how far out of the sit it is, so a cat rising mid-stride does not suddenly
        // start swinging legs that are still folded underneath it.
        float swing = limbSwingAmount * standing;
        this.leftHindLeg.xRot += Mth.cos(limbSwing * 0.6662F) * swing;
        this.rightHindLeg.xRot += Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * swing;
        this.leftFrontLeg.xRot += Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * swing;
        this.rightFrontLeg.xRot += Mth.cos(limbSwing * 0.6662F) * swing;
        this.tail2.xRot += (1.7278761F + ((float) Math.PI / 4F) * Mth.cos(limbSwing) * limbSwingAmount
                - this.tail2.xRot) * standing;

        // Gold has worn off but it is still walking: ears stay a little down.
        if (!cat.isCheeredUp()) {
            this.leftEar.xRot = lerp(standing, this.leftEar.xRot, SAD_EAR_X_ROT * 0.45F);
            this.rightEar.xRot = lerp(standing, this.rightEar.xRot, SAD_EAR_X_ROT * 0.45F);
        }
    }
}
