#ifndef MATERIAL_FACES_GLSL
#define MATERIAL_FACES_GLSL
#include "common/render_abi.hpp"
const uint materialCullBackBit = FACE_BACK_BIT;
const uint materialCullFrontBit = FACE_FRONT_BIT;
const uint materialClockwiseBit = FACE_CLOCKWISE_BIT;
const uint materialHardwareFrontFlipBit = FACE_HARDWARE_FRONT_FLIP_BIT;
uint effectiveMaterialFaces(uint flags) {
    uint faces = flags & (materialCullBackBit | materialCullFrontBit);
    if ((flags & materialClockwiseBit) == 0u) return faces;
    return ((faces & materialCullBackBit) != 0u ? materialCullFrontBit : 0u)
        | ((faces & materialCullFrontBit) != 0u ? materialCullBackBit : 0u);
}
// gl_HitKind includes mirror correction and, for uniform FRONT models, the
// backend's canonical-facing flip. Preserve source sidedness instead of deleting it.
bool acceptsMaterialFace(uint flags, bool ccwFront) {
    bool sourceFront = ccwFront != ((flags & materialHardwareFrontFlipBit) != 0u);
    bool front = sourceFront != ((flags & materialClockwiseBit) != 0u);
    return (flags & (front ? materialCullFrontBit : materialCullBackBit)) == 0u;
}
// Authored thin-sheet ownership is separate from the world sidedness policy.
// Unpaired geometry is bilateral; exact reverse twins retain one member per side.
bool acceptsWorldTriangleFace(uint flags, bool ccwFront, uint primitivePolicy, bool bilateral) {
    if (!bilateral) return (flags & FACE_MEDIUM_BOUNDARY_BIT) != 0u || acceptsMaterialFace(flags, ccwFront);
    if ((primitivePolicy & 1u) == 0u) return true;
    uint face = effectiveMaterialFaces(flags);
    if (face == 0u || face == (materialCullBackBit | materialCullFrontBit))
        flags = (flags & ~(materialCullBackBit | materialCullFrontBit)) | materialCullBackBit;
    return acceptsMaterialFace(flags, ccwFront);
}
bool acceptsQueryTriangleFace(uint flags, bool ccwFront, uint primitivePolicy, bool directImage) {
    return (!directImage || (flags & FACE_MEDIUM_BOUNDARY_BIT) != 0u || acceptsMaterialFace(flags, ccwFront)) &&
        acceptsWorldTriangleFace(flags, ccwFront, primitivePolicy, true);
}
// Direct imaging uses source facing; transport is bilateral. Accepted surfaces
// retain the material's original alpha/transmission, never an added null layer.
#endif
