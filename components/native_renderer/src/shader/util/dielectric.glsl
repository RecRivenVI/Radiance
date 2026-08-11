#ifndef DIELECTRIC_GLSL
#define DIELECTRIC_GLSL
#include "util/disney.glsl"

bool sampleDielectricBranch(LabPBRMat mat, vec3 incident, vec3 normal, float eta,
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
    return directionPdf > 0.0;
}

float dielectricReflectionProbability(float fresnel, float minimumProbability) {
    // Do not sample an impossible component, including transmission under TIR.
    if (fresnel <= 0.0 || fresnel >= 1.0) return clamp(fresnel, 0.0, 1.0);
    float floorProbability = clamp(minimumProbability, 0.0, 0.5);
    return clamp(fresnel, floorProbability, 1.0 - floorProbability);
}

float dielectricBalancedSelection(uvec2 pixel, uint frameSeed) {
    // Each 2x2 footprint contains all four probability quartiles, even after
    // scrambling. Jitter within the stratum preserves a uniform marginal PDF.
    uvec2 p = pixel & 3u;
    uint rank = (((p.x ^ p.y) & 1u) << 3u) | ((p.y & 1u) << 2u) |
        ((p.x ^ p.y) & 2u) | ((p.y & 2u) >> 1u);
    rank ^= frameSeed & 15u;
    // Assemble fixed-point bits before converting, so rounding near 1 cannot
    // move a sample into the next quartile.
    // WorldUBO's uniform_int_distribution supplies 31 random bits, not 32.
    uint bits = (rank << 20u) | ((frameSeed >> 11u) & 0xFFFFFu);
    return float(bits) * (1.0 / 16777216.0);
}

vec3 sampleDielectricWithSelection(LabPBRMat mat, vec3 incident, vec3 normal, float eta,
                                   float minimumProbability, float selection, inout uint seed,
                                   out vec3 direction, out float pdf, out uint lobe) {
    // The same sampled microfacet determines Fresnel and the chosen branch.
    uint facetStart = seed;
    uint facetSeed = facetStart;
    float directionPdf, fresnel;
    vec3 weight;
    bool reflectionValid = sampleDielectricBranch(mat, incident, normal, eta, true,
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
    bool valid = sampleDielectricBranch(mat, incident, normal, eta, false,
        facetStart, direction, weight, directionPdf, fresnel);
    pdf = directionPdf * (1.0 - reflectionProbability);
    lobe = 2u;
    return valid ? weight * directionPdf : vec3(0.0);
}

vec3 sampleDielectric(LabPBRMat mat, vec3 incident, vec3 normal, float eta,
                      inout uint seed, out vec3 direction, out float pdf, out uint lobe) {
    return sampleDielectricWithSelection(mat, incident, normal, eta, 0.0, -1.0, seed, direction, pdf, lobe);
}
#endif
