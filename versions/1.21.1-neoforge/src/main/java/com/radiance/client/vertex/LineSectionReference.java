package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.PoseStack;

/** An authored extrusion reference is separate from the source line's normal/tangent. */
public interface LineSectionReference {
    void sectionReference(PoseStack.Pose pose, float x, float y, float z);
}
