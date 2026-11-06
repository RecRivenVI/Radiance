#ifndef DIELECTRIC_GLSL
#define DIELECTRIC_GLSL
#include "util/disney.glsl"

bool dielectricDirectionOnSide(vec3 outgoing, vec3 incidentFacingGeometry, bool reflection) {
    float side = dot(outgoing, incidentFacingGeometry);
    return reflection ? side > 1e-7 : side < -1e-7;
}

bool sampleDielectricBranch(LabPBRMat mat, vec3 incident, vec3 normal, vec3 geometricNormal, float eta,
                            bool reflection, inout uint seed, out vec3 direction,
                            out vec3 weight, out float directionPdf, out float fresnel) {
    vec3 tangent, bitangent;
    Onb(normal, tangent, bitangent);
    vec3 v = ToLocal(tangent, bitangent, normal, -incident);
    float alpha = max(mat.roughness * mat.roughness, 1e-4);
    vec3 h = SampleGGXVNDF(v, alpha, alpha, rand(seed), rand(seed));
    float vDotH = max(dot(v, h), 1e-6);
    fresnel = DielectricFresnel(vDotH, eta);
    vec3 l = reflection ? reflect(-v, h) : refract(-v, h, eta);
    weight = vec3(0.0);
    direction = vec3(0.0);
    directionPdf = 0.0;
    if (dot(l, l) <= 1e-8 || (reflection ? l.z <= 0.0 : l.z >= 0.0)) return false;
    l = normalize(l);
    float pdfH = GTR2Aniso(h.z, h.x, h.y, alpha, alpha) *
        SmithGAniso(abs(v.z), v.x, v.y, alpha, alpha) * vDotH / max(abs(v.z), 1e-6);
    float masking = SmithGAniso(abs(l.z), l.x, l.y, alpha, alpha);
    if (reflection) {
        directionPdf = pdfH / (4.0 * vDotH);
        weight = vec3(fresnel * masking);
    } else {
        float denominator = dot(l, h) + eta * dot(v, h);
        directionPdf = pdfH * abs(dot(l, h)) / max(denominator * denominator, 1e-10);
        weight = vec3((1.0 - fresnel) * masking * eta * eta);
    }
    direction = normalize(ToWorld(tangent, bitangent, normal, l));
    // Null samples outside the actual geometric hemisphere are not relabelled
    // as the other lobe: that would use the wrong eta and medium history.
    if (!dielectricDirectionOnSide(direction, geometricNormal, reflection)) {
        weight = vec3(0); directionPdf = 0.0; return false;
    }
    return directionPdf > 0.0;
}

float dielectricReflectionProbability(float fresnel, float minimumProbability) {
    // Do not sample an impossible component, including transmission under TIR.
    if (fresnel <= 0.0 || fresnel >= 1.0) return clamp(fresnel, 0.0, 1.0);
    float floorProbability = clamp(minimumProbability, 0.0, 0.5);
    return clamp(fresnel, floorProbability, 1.0 - floorProbability);
}

float dielectricBalancedSelection(uvec2 pixel, uint frameIndex, uint samplingSeed) {
    // Every aligned 2x2 has all four quartiles, so every sliding 3x3 contains
    // both lobes at probabilities in [1/4,3/4]. Independent cell permutations
    // remove the screen-wide 4x4 repetition without giving up that guarantee.
    uint cellKey = xxhash32(uvec3(pixel >> 1u, samplingSeed));
    uint cycle = frameIndex >> 2u;
    uint permutationSeed = cellKey ^ (cycle * 0x9E3779B9u);
    uint permutationWord = pcg(permutationSeed);
    uint permutation = permutationWord % 6u;
    uint x = pixel.x & 1u, y = pixel.y & 1u;
    uint first = permutation < 2u ? x : permutation < 4u ? y : x ^ y;
    uint second = permutation == 0u || permutation == 5u ? y :
        permutation == 1u || permutation == 3u ? x ^ y : x;
    uint rank = ((first << 1u) | second) ^ ((permutationWord >> 8u) & 3u);
    rank = (rank + (frameIndex & 3u)) & 3u;
    // Each pixel visits every quartile once per four-frame group. The fine
    // value is a digitally scrambled base-2 sequence over groups, not a shared
    // per-frame jitter. Work in 24-bit fixed point to stay strictly below 1.
    uint local = x | (y << 1u);
    uint fine = bitfieldReverse(cycle ^ (cellKey + local * 0x85EBCA6Bu)) >> 10u;
    uint bits = (rank << 22u) | fine;
    return float(bits) * (1.0 / 16777216.0);
}

vec3 sampleDielectricWithSelection(LabPBRMat mat, vec3 incident, vec3 normal, vec3 geometricNormal, float eta,
                                   float minimumProbability, float selection, inout uint seed,
                                   out vec3 direction, out float pdf, out uint lobe) {
    // The same sampled microfacet determines Fresnel and the chosen branch.
    uint facetStart = seed;
    uint facetSeed = facetStart;
    float directionPdf, fresnel;
    vec3 weight;
    bool reflectionValid = sampleDielectricBranch(mat, incident, normal, geometricNormal, eta, true,
        facetSeed, direction, weight, directionPdf, fresnel);
    seed = facetSeed;
    float reflectionProbability = dielectricReflectionProbability(fresnel, minimumProbability);
    float randomSelection = rand(seed);
    bool reflection = (selection < 0.0 ? randomSelection : selection) < reflectionProbability;
    if (reflection) {
        pdf = directionPdf * reflectionProbability;
        lobe = 1u;
        return reflectionValid ? weight * directionPdf : vec3(0.0);
    }
    bool valid = sampleDielectricBranch(mat, incident, normal, geometricNormal, eta, false,
        facetStart, direction, weight, directionPdf, fresnel);
    pdf = directionPdf * (1.0 - reflectionProbability);
    lobe = 2u;
    return valid ? weight * directionPdf : vec3(0.0);
}

vec3 sampleDielectric(LabPBRMat mat, vec3 incident, vec3 normal, vec3 geometricNormal, float eta,
                      inout uint seed, out vec3 direction, out float pdf, out uint lobe) {
    return sampleDielectricWithSelection(mat, incident, normal, geometricNormal, eta, 0.0, -1.0, seed, direction, pdf, lobe);
}
bool sampleDielectricBranch(LabPBRMat mat, vec3 incident, vec3 normal, float eta,
    bool reflection, inout uint seed, out vec3 direction, out vec3 weight, out float pdf, out float fresnel) {
    return sampleDielectricBranch(mat, incident, normal, normal, eta, reflection, seed, direction, weight, pdf, fresnel);
}
vec3 sampleDielectricWithSelection(LabPBRMat mat, vec3 incident, vec3 normal, float eta,
    float floorProbability, float selection, inout uint seed, out vec3 direction, out float pdf, out uint lobe) {
    return sampleDielectricWithSelection(mat, incident, normal, normal, eta, floorProbability, selection, seed, direction, pdf, lobe);
}
vec3 sampleDielectric(LabPBRMat mat, vec3 incident, vec3 normal, float eta,
    inout uint seed, out vec3 direction, out float pdf, out uint lobe) {
    return sampleDielectric(mat, incident, normal, normal, eta, seed, direction, pdf, lobe);
}
#endif
