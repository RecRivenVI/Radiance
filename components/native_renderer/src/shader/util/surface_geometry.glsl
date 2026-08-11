#ifndef SURFACE_GEOMETRY_GLSL
#define SURFACE_GEOMETRY_GLSL
#include "util/height_map.glsl"

vec3 mappedNormalForView(vec3 normal, vec3 geometricNormal, vec3 viewDir) {
    float geometricCos = dot(geometricNormal, viewDir);
    if (geometricCos <= 0.0) return geometricNormal;

    vec3 reflection = reflect(-viewDir, normal);
    float reflectedCos = dot(reflection, geometricNormal);
    float band = 0.1 * geometricCos;
    float minimumCos = min(1e-4, 0.5 * geometricCos);
    if (reflectedCos >= max(band, minimumCos) && dot(normal, viewDir) > 0.0 &&
        dot(normal, geometricNormal) > 0.0) return normal;

    // A C1 positive continuation at the geometric horizon. Correct the normal
    // via the reflection half-vector; neither a sampled ray nor its PDF is edited.
    float liftedCos = reflectedCos < band ? band * band / (2.0 * band - reflectedCos) : reflectedCos;
    liftedCos = clamp(max(liftedCos, minimumCos), 0.0, 1.0);
    vec3 alongSurface = reflection - geometricNormal * reflectedCos;
    if (dot(alongSurface, alongSurface) <= 1e-12)
        alongSurface = normal - geometricNormal * dot(normal, geometricNormal);
    if (dot(alongSurface, alongSurface) <= 1e-12) return geometricNormal;
    vec3 correctedReflection = normalize(alongSurface) * sqrt(max(1.0 - liftedCos * liftedCos, 0.0)) +
                               geometricNormal * liftedCos;
    vec3 bisector = viewDir + correctedReflection;
    if (dot(bisector, bisector) <= 1e-12) return geometricNormal;
    vec3 corrected = normalize(bisector);
    // At vanishing projected area, float cancellation must not turn the BSDF
    // tangent or point the ideal reflection into the actual surface.
    if (dot(corrected, viewDir) <= 0.0 || dot(corrected, geometricNormal) <= 0.0 ||
        dot(reflect(-viewDir, corrected), geometricNormal) <= 0.0) return geometricNormal;
    return corrected;
}

vec3 applyNormalMapToBasis(vec3 matNormal,
                           vec3 tangent,
                           vec3 bitangent,
                           vec3 basisNormal,
                           vec3 geometricNormal,
                           vec3 viewDir) {
    if (any(isnan(matNormal)) || any(isinf(matNormal))) { return geometricNormal; }

    vec3 correctedLocalNormal = matNormal;
    correctedLocalNormal.y = -correctedLocalNormal.y;

    vec3 normal = normalizeF(tangent * correctedLocalNormal.x + bitangent * correctedLocalNormal.y +
                                 basisNormal * correctedLocalNormal.z,
                             geometricNormal);
    return mappedNormalForView(normal, geometricNormal, viewDir);
}

vec3 basePlaneWorldPosAtUv(vec2 uv,
                           vec2 referenceUv,
                           vec3 referenceWorldPos,
                           vec3 dPduWorld,
                           vec3 dPdvWorld) {
    vec2 uvOffset = uv - referenceUv;
    return referenceWorldPos + dPduWorld * uvOffset.x + dPdvWorld * uvOffset.y;
}
#endif
