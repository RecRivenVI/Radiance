#ifndef MATERIAL_PALETTE_GLSL
#define MATERIAL_PALETTE_GLSL
layout(std430, buffer_reference, buffer_reference_align=8) readonly buffer MaterialBuffer {
    MaterialVertex vertices[];
};
layout(std430, buffer_reference, buffer_reference_align=8) readonly buffer MaterialDictionary {
    uvec4 header;
    uint indices[];
};
MaterialVertex loadMaterialVertex(uint64_t address, uint index) {
    if ((address & uint64_t(1)) == uint64_t(0)) return MaterialBuffer(address).vertices[index];
    address &= ~uint64_t(1);
    MaterialDictionary dictionary=MaterialDictionary(address);
    return MaterialBuffer(address+uint64_t(dictionary.header.x)*uint64_t(4)).vertices[dictionary.indices[index]];
}
#endif
