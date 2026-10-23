package com.radiance.mixins.vulkan_render_integration;

import com.radiance.compatibility.vanilla.VanillaModelPose;
import java.util.List;
import net.minecraft.client.model.LlamaModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.*;

@Mixin(LlamaModel.class)
public abstract class LlamaRootPoseMixins implements VanillaModelPose.DefinedRoots {
    @Shadow @Final private ModelPart head;
    @Shadow @Final private ModelPart body;
    @Shadow @Final private ModelPart rightHindLeg;
    @Shadow @Final private ModelPart leftHindLeg;
    @Shadow @Final private ModelPart rightFrontLeg;
    @Shadow @Final private ModelPart leftFrontLeg;
    @Shadow @Final private ModelPart rightChest;
    @Shadow @Final private ModelPart leftChest;
    @Unique private List<VanillaModelPose.Group> radiance$adult, radiance$young;

    public List<VanillaModelPose.Group> radiance$rootGroups(boolean young) {
        if (radiance$adult == null) {
            radiance$adult =
                    List.of(
                            VanillaModelPose.Group.ordinary(
                                    head,
                                    body,
                                    rightHindLeg,
                                    leftHindLeg,
                                    rightFrontLeg,
                                    leftFrontLeg,
                                    rightChest,
                                    leftChest));
            radiance$young =
                    List.of(
                            new VanillaModelPose.Group(
                                    List.of(head),
                                    .71428573F,
                                    .64935064F,
                                    .7936508F,
                                    0,
                                    1.3125F,
                                    .22F),
                            new VanillaModelPose.Group(
                                    List.of(body), .625F, .45454544F, .45454544F, 0, 2.0625F, 0),
                            new VanillaModelPose.Group(
                                    List.of(
                                            rightHindLeg,
                                            leftHindLeg,
                                            rightFrontLeg,
                                            leftFrontLeg,
                                            rightChest,
                                            leftChest),
                                    .45454544F,
                                    .41322312F,
                                    .45454544F,
                                    0,
                                    2.0625F,
                                    0));
        }
        return young ? radiance$young : radiance$adult;
    }
}
