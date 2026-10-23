package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;

/** A dragon reuses its neck part at different poses; each occurrence keeps a separate identity. */
public final class VanillaDragonRequests {
    private VanillaDragonRequests() {}

    public interface PoseSource {
        List<VanillaModelPose.Part> radiance$parts(PoseStack pose, int color);
    }
}
