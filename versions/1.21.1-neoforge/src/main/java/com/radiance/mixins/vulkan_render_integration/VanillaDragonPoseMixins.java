package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.adapter.vanilla.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.*;

/** Original dragon pose equations, separated mechanically from its immediate part rendering. */
@Mixin(EnderDragonRenderer.DragonModel.class)
public abstract class VanillaDragonPoseMixins implements VanillaDragonRequests.PoseSource {
    @Shadow @Final private ModelPart head;
    @Shadow @Final private ModelPart neck;
    @Shadow @Final private ModelPart jaw;
    @Shadow @Final private ModelPart body;
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart leftWingTip;
    @Shadow @Final private ModelPart leftFrontLeg;
    @Shadow @Final private ModelPart leftFrontLegTip;
    @Shadow @Final private ModelPart leftFrontFoot;
    @Shadow @Final private ModelPart leftRearLeg;
    @Shadow @Final private ModelPart leftRearLegTip;
    @Shadow @Final private ModelPart leftRearFoot;
    @Shadow @Final private ModelPart rightWing;
    @Shadow @Final private ModelPart rightWingTip;
    @Shadow @Final private ModelPart rightFrontLeg;
    @Shadow @Final private ModelPart rightFrontLegTip;
    @Shadow @Final private ModelPart rightFrontFoot;
    @Shadow @Final private ModelPart rightRearLeg;
    @Shadow @Final private ModelPart rightRearLegTip;
    @Shadow @Final private ModelPart rightRearFoot;
    @Shadow private EnderDragon entity;
    @Shadow private float a;

    public List<VanillaModelPose.Part> radiance$parts(PoseStack p_114281_, int p_350827_) {
        var result = new ArrayList<VanillaModelPose.Part>();

        p_114281_.pushPose();
        float f = Mth.lerp(this.a, this.entity.oFlapTime, this.entity.flapTime);
        this.jaw.xRot = (float) (Math.sin((double) (f * (float) (Math.PI * 2))) + 1.0) * 0.2F;
        float f1 = (float) (Math.sin((double) (f * (float) (Math.PI * 2) - 1.0F)) + 1.0);
        f1 = (f1 * f1 + f1 * 2.0F) * 0.05F;
        p_114281_.translate(0.0F, f1 - 2.0F, -3.0F);
        p_114281_.mulPose(Axis.XP.rotationDegrees(f1 * 2.0F));
        float f2 = 0.0F;
        float f3 = 20.0F;
        float f4 = -12.0F;
        float f5 = 1.5F;
        double[] adouble = this.entity.getLatencyPos(6, this.a);
        float f6 =
                Mth.wrapDegrees(
                        (float)
                                (this.entity.getLatencyPos(5, this.a)[0]
                                        - this.entity.getLatencyPos(10, this.a)[0]));
        float f7 =
                Mth.wrapDegrees(
                        (float) (this.entity.getLatencyPos(5, this.a)[0] + (double) (f6 / 2.0F)));
        float f8 = f * (float) (Math.PI * 2);

        for (int i = 0; i < 5; i++) {
            double[] adouble1 = this.entity.getLatencyPos(5 - i, this.a);
            float f9 = (float) Math.cos((double) ((float) i * 0.45F + f8)) * 0.15F;
            this.neck.yRot =
                    Mth.wrapDegrees((float) (adouble1[0] - adouble[0]))
                            * (float) (Math.PI / 180.0)
                            * 1.5F;
            this.neck.xRot =
                    f9
                            + this.entity.getHeadPartYOffset(i, adouble, adouble1)
                                    * (float) (Math.PI / 180.0)
                                    * 1.5F
                                    * 5.0F;
            this.neck.zRot =
                    -Mth.wrapDegrees((float) (adouble1[0] - (double) f7))
                            * (float) (Math.PI / 180.0)
                            * 1.5F;
            this.neck.y = f3;
            this.neck.z = f4;
            this.neck.x = f2;
            f3 += Mth.sin(this.neck.xRot) * 10.0F;
            f4 -= Mth.cos(this.neck.yRot) * Mth.cos(this.neck.xRot) * 10.0F;
            f2 -= Mth.sin(this.neck.yRot) * Mth.cos(this.neck.xRot) * 10.0F;
            result.addAll(VanillaModelPose.extract(this.neck, p_114281_, p_350827_));
        }

        this.head.y = f3;
        this.head.z = f4;
        this.head.x = f2;
        double[] adouble2 = this.entity.getLatencyPos(0, this.a);
        this.head.yRot =
                Mth.wrapDegrees((float) (adouble2[0] - adouble[0])) * (float) (Math.PI / 180.0);
        this.head.xRot =
                Mth.wrapDegrees(this.entity.getHeadPartYOffset(6, adouble, adouble2))
                        * (float) (Math.PI / 180.0)
                        * 1.5F
                        * 5.0F;
        this.head.zRot =
                -Mth.wrapDegrees((float) (adouble2[0] - (double) f7)) * (float) (Math.PI / 180.0);
        result.addAll(VanillaModelPose.extract(this.head, p_114281_, p_350827_));
        p_114281_.pushPose();
        p_114281_.translate(0.0F, 1.0F, 0.0F);
        p_114281_.mulPose(Axis.ZP.rotationDegrees(-f6 * 1.5F));
        p_114281_.translate(0.0F, -1.0F, 0.0F);
        this.body.zRot = 0.0F;
        result.addAll(VanillaModelPose.extract(this.body, p_114281_, p_350827_));
        float f10 = f * (float) (Math.PI * 2);
        this.leftWing.xRot = 0.125F - (float) Math.cos((double) f10) * 0.2F;
        this.leftWing.yRot = -0.25F;
        this.leftWing.zRot = -((float) (Math.sin((double) f10) + 0.125)) * 0.8F;
        this.leftWingTip.zRot = (float) (Math.sin((double) (f10 + 2.0F)) + 0.5) * 0.75F;
        this.rightWing.xRot = this.leftWing.xRot;
        this.rightWing.yRot = -this.leftWing.yRot;
        this.rightWing.zRot = -this.leftWing.zRot;
        this.rightWingTip.zRot = -this.leftWingTip.zRot;
        radiance$side(
                p_114281_,
                result,
                f1,
                this.leftWing,
                this.leftFrontLeg,
                this.leftFrontLegTip,
                this.leftFrontFoot,
                this.leftRearLeg,
                this.leftRearLegTip,
                this.leftRearFoot,
                p_350827_);
        radiance$side(
                p_114281_,
                result,
                f1,
                this.rightWing,
                this.rightFrontLeg,
                this.rightFrontLegTip,
                this.rightFrontFoot,
                this.rightRearLeg,
                this.rightRearLegTip,
                this.rightRearFoot,
                p_350827_);
        p_114281_.popPose();
        float f11 = -Mth.sin(f * (float) (Math.PI * 2)) * 0.0F;
        f8 = f * (float) (Math.PI * 2);
        f3 = 10.0F;
        f4 = 60.0F;
        f2 = 0.0F;
        adouble = this.entity.getLatencyPos(11, this.a);

        for (int j = 0; j < 12; j++) {
            adouble2 = this.entity.getLatencyPos(12 + j, this.a);
            f11 += Mth.sin((float) j * 0.45F + f8) * 0.05F;
            this.neck.yRot =
                    (Mth.wrapDegrees((float) (adouble2[0] - adouble[0])) * 1.5F + 180.0F)
                            * (float) (Math.PI / 180.0);
            this.neck.xRot =
                    f11
                            + (float) (adouble2[1] - adouble[1])
                                    * (float) (Math.PI / 180.0)
                                    * 1.5F
                                    * 5.0F;
            this.neck.zRot =
                    Mth.wrapDegrees((float) (adouble2[0] - (double) f7))
                            * (float) (Math.PI / 180.0)
                            * 1.5F;
            this.neck.y = f3;
            this.neck.z = f4;
            this.neck.x = f2;
            f3 += Mth.sin(this.neck.xRot) * 10.0F;
            f4 -= Mth.cos(this.neck.yRot) * Mth.cos(this.neck.xRot) * 10.0F;
            f2 -= Mth.sin(this.neck.yRot) * Mth.cos(this.neck.xRot) * 10.0F;
            result.addAll(VanillaModelPose.extract(this.neck, p_114281_, p_350827_));
        }

        p_114281_.popPose();
        return result;
    }

    @Unique
    private void radiance$side(
            PoseStack p_173978_,
            List<VanillaModelPose.Part> result,
            float p_173982_,
            ModelPart p_173983_,
            ModelPart p_173984_,
            ModelPart p_173985_,
            ModelPart p_173986_,
            ModelPart p_173987_,
            ModelPart p_173988_,
            ModelPart p_173989_,
            int p_350327_) {

        p_173987_.xRot = 1.0F + p_173982_ * 0.1F;
        p_173988_.xRot = 0.5F + p_173982_ * 0.1F;
        p_173989_.xRot = 0.75F + p_173982_ * 0.1F;
        p_173984_.xRot = 1.3F + p_173982_ * 0.1F;
        p_173985_.xRot = -0.5F - p_173982_ * 0.1F;
        p_173986_.xRot = 0.75F + p_173982_ * 0.1F;
        result.addAll(VanillaModelPose.extract(p_173983_, p_173978_, p_350327_));
        result.addAll(VanillaModelPose.extract(p_173984_, p_173978_, p_350327_));
        result.addAll(VanillaModelPose.extract(p_173987_, p_173978_, p_350327_));
    }
}
