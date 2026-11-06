#ifndef RECONSTRUCTION_MATERIAL_GLSL
#define RECONSTRUCTION_MATERIAL_GLSL

// Streamline RR's EnvBRDFApprox2 / Ray Tracing Gems chapter 32.
// The guide needs integrated directional reflectance, not only normal-incidence F0.
vec3 reconstructionSpecularReflectance(vec3 f0, float roughness, float noV) {
    noV = clamp(abs(noV), 0.0, 1.0);
    float alpha = clamp(roughness * roughness, 0.0, 1.0);
    vec4 x = vec4(1.0, noV, noV * noV, noV * noV * noV);
    vec4 y = vec4(1.0, alpha, alpha * alpha, alpha * alpha * alpha);
    mat2 m1 = transpose(mat2(0.99044, -1.28514, 1.29678, -0.755907));
    mat3 m2 = transpose(mat3(1.0, 2.92338, 59.4188, 20.3225, -27.0302, 222.592, 121.563, 626.13, 316.627));
    mat2 m3 = transpose(mat2(0.0365463, 3.32707, 9.0632, -9.04756));
    mat3 m4 = transpose(mat3(1.0, 3.59685, -1.36772, 9.04401, -16.3174, 9.22949, 5.56589, 19.7886, -20.2123));
    float bias = dot(m1 * x.xy, y.xy) / max(dot(m2 * x.xyw, y.xyw), 1e-6);
    float scale = dot(m3 * x.xy, y.xy) / max(dot(m4 * x.xzw, y.xyw), 1e-6);
    bias *= clamp(f0.g * 50.0, 0.0, 1.0);
    return clamp(f0 * max(scale, 0.0) + max(bias, 0.0), 0.0, 1.0);
}

// NRD 4.18 material-factor floors keep demodulation bounded. The exact same
// factors must be applied before filtering and after it; raw PT energy is unchanged.
void reconstructionMaterialFactors(vec4 albedoMetallic, vec3 specularReflectance, float roughness, bool containsBackground,
                                   out vec3 diffuseFactor, out vec3 specularFactor) {
    vec3 diffuse = albedoMetallic.rgb * (1.0 - specularReflectance);
    diffuseFactor = mix(vec3(0.02), vec3(1.0), clamp(diffuse, 0.0, 1.0));
    // This signal also contains transmitted/background radiance, not only the
    // reflected BRDF. F0 demodulation would amplify it by up to 50x.
    specularFactor = containsBackground ? vec3(1.0) :
        mix(vec3(0.02), vec3(1.0), specularReflectance * mix(0.1, 1.0, roughness));
}
#endif
