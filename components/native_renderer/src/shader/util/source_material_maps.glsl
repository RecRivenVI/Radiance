#ifndef SOURCE_MATERIAL_MAPS_GLSL
#define SOURCE_MATERIAL_MAPS_GLSL
// Green low bits are runtime presence metadata, independent of authored map values.
// An explicitly supplied all-black specular map remains an authored rough surface.
TextureMapEntry sourceMaterialMaps(TextureMapEntry maps, vec2 uv, float lod) {
    if (maps.flag >= 0) {
        uint presence = uint(round(sampleTexture(textures[nonuniformEXT(maps.flag)], uv, ceil(lod), false).g * 255.0));
        if ((presence & 1u) == 0u) maps.specular = -1;
        if ((presence & 2u) == 0u) maps.normal = -1;
    }
    return maps;
}
#endif
