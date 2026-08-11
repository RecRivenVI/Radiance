#include "util/layered_texture.glsl"
#define LAYERED_LIGHT_PDF_ONLY
#include "util/layered_light.glsl"
#include "util/previous_scene_position.glsl"
void main() {
    uint geometry=getGeometryBufferIndex(gl_InstanceCustomIndexEXT,gl_GeometryIndexEXT);
    uint i0,i1,i2;PositionVertex p0,p1,p2;MaterialVertex m0,m1,m2;
    loadTriangle(geometry,gl_PrimitiveID,i0,i1,i2,p0,p1,p2,m0,m1,m2);
    vec3 bary=vec3(1-attribs.x-attribs.y,attribs);
    vec3 position=gl_WorldRayOriginEXT+gl_WorldRayDirectionEXT*gl_HitTEXT;
    LayeredWorldMaterial material=mapping.programs[m0.textureID];
    vec4 tint=(m0.colorLayer*bary.x+m1.colorLayer*bary.y+m2.colorLayer*bary.z)*sourceDrawColour(geometry);
    vec4 colour=evaluateLayeredPlane(material,position,worldUBO.cameraPos.xyz,tint,worldUBO.gameTime);
    rayPrepareOpaque(mainRay,gl_HitTEXT);
    float lightPdf=(material.settings.w&2u)!=0u && mapping.programInfo.x>0u ?
        layeredEmitterPdf(material,mainRay.lastScatterOrigin,position):0;
    float bsdfPdf=mainRay.previousBsdfPdf;
    float mis=bsdfPdf>0 && lightPdf>0 ? bsdfPdf*bsdfPdf/(bsdfPdf*bsdfPdf+lightPdf*lightPdf):1;
    mainRay.radiance+=mainRay.throughput*colour.rgb*colour.a*material.surface.y/float(max(material.settings.y,1u))*mis;
    if((material.settings.w&1u)==0u)mainRay.throughput*=1-colour.a;
    mainRay.normal=normalize(mat3(gl_WorldToObject3x4EXT)*cross(p1.pos-p0.pos,p2.pos-p0.pos));
    if(dot(mainRay.normal,-mainRay.direction)<0)mainRay.normal=-mainRay.normal;
    mainRay.hitT=gl_HitTEXT;
    rayStoreMaterial(mainRay,vec4(colour.rgb,colour.a),vec3(0),1,0,0,1,0);
    mainRay.hasPrevScenePos=loadPreviousScenePos(geometry,gl_PrimitiveID,bary,mainRay.prevScenePos)?1u:0u;
    mainRay.coneWidth+=gl_HitTEXT*mainRay.coneSpread;
    mainRay.origin=offsetQueryOrigin(position,mainRay.normal,mainRay.direction);
    raySetNoisy(mainRay,false);
    raySetContinue(mainRay,true);raySetStop(mainRay,false);
}
