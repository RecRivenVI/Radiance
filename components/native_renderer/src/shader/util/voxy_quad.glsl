#ifndef LOD_QUAD_GLSL
#define LOD_QUAD_GLSL
// Historical resource path retained for custom packs. Content is the platform's canonical ABI.
struct LodQuad {uint position;uint extent;uint materialFace;uint tint;};
struct LodFace {vec4 bounds;vec4 plane;};
struct LodModel {LodFace faces[6];uvec4 material;uvec4 surface;};
uint lodFace(LodQuad q){return (q.materialFace>>24)&7u;}
uint lodModelId(LodQuad q){return q.materialFace&0xffffffu;}
vec3 lodBase(LodQuad q){return vec3(q.position&1023u,(q.position>>10)&1023u,(q.position>>20)&1023u);}
vec2 lodExtent(LodQuad q){return vec2(q.extent&65535u,q.extent>>16);}
vec3 lodAxes(uint axis,vec3 p){return axis==0u?p.xzy:(axis==1u?p:p.zxy);}
vec2 lodUv(LodQuad q,LodFace f,uint corner){return f.bounds.xy+(f.bounds.zw+lodExtent(q)-vec2(1))*vec2((corner>>1)&1u,corner&1u);}
vec3 lodPosition(LodQuad q,LodModel model,uint corner,uint level){uint face=lodFace(q);return (lodBase(q)+lodAxes(face>>1,vec3(lodUv(q,model.faces[face],corner),model.faces[face].plane.x)))*float(1u<<level);}
#endif
