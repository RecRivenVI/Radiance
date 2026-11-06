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

bool reconstructionGuideValid(vec4 guide) { return dot(guide.xyz, guide.xyz) > 0.5; }

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

float reconstructionDominantTransmission(float fresnel, bool transmissionValid) {
    return transmissionValid && fresnel < 0.5 ? 1.0 : 0.0;
}

#endif
