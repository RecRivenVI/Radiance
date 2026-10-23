package com.radiance.mixins.vulkan_render_integration;

import com.radiance.compatibility.vanilla.VanillaModelPose;
import java.util.List;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.*;

@Mixin(RabbitModel.class)
public abstract class RabbitRootPoseMixins implements VanillaModelPose.DefinedRoots {
    @Shadow @Final private ModelPart leftRearFoot, rightRearFoot, leftHaunch, rightHaunch, body;
    @Shadow @Final
    private ModelPart leftFrontLeg, rightFrontLeg, head, rightEar, leftEar, tail, nose;
    @Unique private List<VanillaModelPose.Group> radiance$adult, radiance$young;

    public List<VanillaModelPose.Group> radiance$rootGroups(boolean young) {
        if (radiance$adult == null) {
            radiance$adult =
                    List.of(
                            new VanillaModelPose.Group(
                                    List.of(
                                            leftRearFoot,
                                            rightRearFoot,
                                            leftHaunch,
                                            rightHaunch,
                                            body,
                                            leftFrontLeg,
                                            rightFrontLeg,
                                            head,
                                            rightEar,
                                            leftEar,
                                            tail,
                                            nose),
                                    .6F,
                                    .6F,
                                    .6F,
                                    0,
                                    1,
                                    0));
            radiance$young =
                    List.of(
                            new VanillaModelPose.Group(
                                    List.of(head, leftEar, rightEar, nose),
                                    .56666666F,
                                    .56666666F,
                                    .56666666F,
                                    0,
                                    1.375F,
                                    .125F),
                            new VanillaModelPose.Group(
                                    List.of(
                                            leftRearFoot,
                                            rightRearFoot,
                                            leftHaunch,
                                            rightHaunch,
                                            body,
                                            leftFrontLeg,
                                            rightFrontLeg,
                                            tail),
                                    .4F,
                                    .4F,
                                    .4F,
                                    0,
                                    2.25F,
                                    0));
        }
        return young ? radiance$young : radiance$adult;
    }
}
