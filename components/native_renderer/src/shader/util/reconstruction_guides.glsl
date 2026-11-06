#ifndef RECONSTRUCTION_GUIDES_GLSL
#define RECONSTRUCTION_GUIDES_GLSL

// Unfold a delta path about its actual interfaces. Reflection is exact for
// planes; the rotation-only transmission model follows RTXPT's guide model.
// It intentionally does not claim to model lens curvature or magnification.
mat3 reconstructionReflection(vec3 n) {
    return mat3(1.0) - 2.0 * outerProduct(n, n);
}

mat3 reconstructionRotation(vec3 from, vec3 to) {
    float c = clamp(dot(from, to), -1.0, 1.0);
    if (c < -0.9999) {
        vec3 axis = normalize(cross(from, abs(from.x) < 0.8 ? vec3(1, 0, 0) : vec3(0, 1, 0)));
        return 2.0 * outerProduct(axis, axis) - mat3(1.0);
    }
    vec3 v = cross(from, to);
    mat3 k = mat3(0, v.z, -v.y, -v.z, 0, v.x, v.y, -v.x, 0);
    return mat3(1.0) + k + k * k / max(1.0 + c, 1e-6);
}

void reconstructionUnfold(inout mat3 transform, inout vec3 translation, mat3 local, vec3 pivot) {
    translation += transform * (pivot - local * pivot);
    transform *= local;
}

vec3 reconstructionCameraIncident(vec3 hit, mat4 cameraToWorld, bool orthographic) {
    // Perspective rays converge at the effected eye. Orthographic rays share
    // the view direction regardless of the hit's previous screen position.
    return normalize(orthographic ? -cameraToWorld[2].xyz : hit - cameraToWorld[3].xyz);
}

vec4 reconstructionSkyDirection(mat3 previousTransform, vec3 direction) {
    return vec4(previousTransform * direction, 0.0);
}

vec3 reconstructionPreviousNormal(vec3 normal, vec3 currentEdge, vec3 currentSide,
                                  vec3 previousEdge, vec3 previousSide) {
    vec3 currentCross = cross(currentEdge, currentSide);
    vec3 previousCross = cross(previousEdge, previousSide);
    if (dot(currentCross, currentCross) <= 1e-16 || dot(previousCross, previousCross) <= 1e-16) return normal;
    vec3 cn = normalize(currentCross), pn = normalize(previousCross);
    mat3 currentBasis = mat3(normalize(currentEdge), normalize(cross(cn, currentEdge)), cn);
    mat3 previousBasis = mat3(normalize(previousEdge), normalize(cross(pn, previousEdge)), pn);
    return normalize(previousBasis * transpose(currentBasis) * normal);
}

bool reconstructionGuideValid(vec4 guide) { return dot(guide.xyz, guide.xyz) > 0.5; }

bool reconstructionDeltaSurface(float transmission, float roughness, float metallic, vec3 f0) {
    return roughness <= 0.08 &&
        (transmission > 1e-6 || metallic > 0.99 || max(f0.r, max(f0.g, f0.b)) > 0.95);
}

bool reconstructionMayStoreDeltaSurface(float transmission, float roughness, float metallic, vec3 f0) {
    // Neighboring half values bound image conversion without assuming its rounding mode.
    // Ray generation decides whether the stored material is a delta surface.
    return roughness < 0.08001708984375 &&
        (transmission > 0.00000095367431640625 || metallic > 0.98974609375 ||
         max(f0.r, max(f0.g, f0.b)) > 0.94970703125);
}

// On a completed hit this bit accompanies valid previous position data.
// While a medium contact is pending, the same payload word holds the primitive index.
const uint reconstructionPreviousNormalBit = 2u;

bool reconstructionHasPreviousNormal(uint hitHistory) {
    return (hitHistory & reconstructionPreviousNormalBit) != 0u;
}

// A sampled diffuse/rough endpoint already has the material and history needed
// by reconstruction. Clear dielectric prefixes still require their own unfolding.
bool reconstructionReusableSurface(vec3 normal, float transmission, float roughness) {
    return dot(normal, normal) > 0.5 && (transmission <= 1e-6 || roughness > 0.08);
}

// A layer combines diffuse and specular transport. Remove its resolved
// surface texture before filtering, then restore this same quantized factor.
vec3 reconstructionLayerFactor(vec3 diffuse, vec3 specular) {
    return clamp(diffuse + specular, vec3(0.02), vec3(1.0));
}

vec3 reconstructionStoredFactor(vec4 factor) {
    return factor.a > 0.5 ? max(factor.rgb, vec3(0.02)) : vec3(1.0);
}

bool reconstructionDominantTransmission(float fresnel, bool reflectionValid, bool transmissionValid) {
    return transmissionValid && (!reflectionValid || fresnel < 0.5);
}

vec2 reconstructionLobeWeights(float fresnel, bool reflectionValid, bool transmissionValid) {
    if (!transmissionValid) return vec2(reflectionValid ? 1.0 : 0.0, 0.0);
    if (!reflectionValid) return vec2(0.0, 1.0);
    return vec2(fresnel, 1.0 - fresnel);
}

#endif
