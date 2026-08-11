#ifndef LOD_LIGHT_GLSL
#define LOD_LIGHT_GLSL
struct LodLightGroup { uint64_t quads; uint first; uint count; ivec4 originLevel; };
layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer LodLightGroups { LodLightGroup values[]; };
layout(std430, buffer_reference, buffer_reference_align = 4) readonly buffer LodLightIndices { uint values[]; };

float lodLightPdf(LodHeader header, LodQuad q, LodModel model, uint level, vec3 direction, float distanceSquared) {
    vec3 p0=lodPosition(q,model,0u,level);
    float area=length(cross(lodPosition(q,model,1u,level)-p0,lodPosition(q,model,2u,level)-p0));
    vec3 normal=lodAxes(lodFace(q)>>1u,vec3(0,0,(lodFace(q)&1u)==0u?-1:1));
    float cosine=dot(normal,-direction);
    if((model.material.z&4u)!=0u)cosine=abs(cosine);
    return cosine>1e-8 && area>0 && header.lightCount>0u ? distanceSquared/(cosine*area*float(header.lightCount)):0;
}
#ifndef CONST_ONLY
float lodEmissionWeight(uint geometry, uint primitive, vec3 point) {
    if(!isLodGeometry(geometry) || mainRay.previousBsdfPdf<=0.0)return 1.0;
    LodHeader header=lodHeader(geometry);
    LodQuad q=LodQuads(materialBufferAddrs.addrs[geometry]).values[primitive/2u];
    LodModel model=LodModels(header.models).values[lodModelId(q)];
    vec3 delta=point-mainRay.lastScatterOrigin;
    float lightPdf=lodLightPdf(header,q,model,uint(instanceAppearances.values[geometry].light.y),normalize(delta),dot(delta,delta));
    float bsdf=mainRay.previousBsdfPdf;
    float scale=max(bsdf,lightPdf);
    float a=bsdf/scale,b=lightPdf/scale;
    return lightPdf>0.0 ? a*a/(a*a+b*b):1.0;
}
#endif
vec3 sampleLodLight(LabPBRMat mat, vec3 point, vec3 normal, vec3 viewDir) {
    if(worldUBO.lodGeometry==0u || mat.transmission>EPS)return vec3(0);
    LodHeader header=lodHeader(worldUBO.lodGeometry-1u);
    if(header.lightCount==0u)return vec3(0);
    uint selected=min(uint(rand(mainRay.seed)*float(header.lightCount)),header.lightCount-1u);
    uint low=0u, high=header.lightGroupCount;
    while(low<high) {uint middle=(low+high)/2u;LodLightGroup g=LodLightGroups(header.lightGroups).values[middle];
        if(g.first+g.count<=selected)low=middle+1u;else high=middle;}
    if(low>=header.lightGroupCount)return vec3(0);
    LodLightGroup group=LodLightGroups(header.lightGroups).values[low];
    uint index=LodLightIndices(header.lightIndices).values[selected];
    LodQuad q=LodQuads(group.quads).values[index];
    LodModel model=LodModels(header.models).values[lodModelId(q)];
    vec2 xy=vec2(rand(mainRay.seed),rand(mainRay.seed));
    vec3 p0=lodPosition(q,model,0u,uint(group.originLevel.w));
    vec3 local=p0+xy.x*(lodPosition(q,model,2u,uint(group.originLevel.w))-p0)+xy.y*(lodPosition(q,model,1u,uint(group.originLevel.w))-p0);
    // Apply exactly the same voxel ownership rule as closest/any-hit near/far coverage.
    if(lodPointCovered(header,group.originLevel,q,local))return vec3(0);
    vec3 samplePoint=vec3(dvec3(group.originLevel.xyz)-worldUBO.cameraPos.xyz)+local;
    vec3 delta=samplePoint-point;float distanceSquared=dot(delta,delta), distance=sqrt(distanceSquared);
    if(distance<.001)return vec3(0);
    vec3 direction=delta/distance;if(dot(normal,direction)<=0)return vec3(0);
    float lightPdf=lodLightPdf(header,q,model,uint(group.originLevel.w),direction,distanceSquared);
    if(lightPdf<=0)return vec3(0);
    MaterialVertex source=lodModelVertex(header,q,0u);
    vec2 uv=mix(lodUv(q,model.faces[lodFace(q)],0u),lodUv(q,model.faces[lodFace(q)],3u),xy)*source.colorLayer.xy;
    vec4 colour=sampleMaterialTexture(source,textures[nonuniformEXT(source.textureID)],materialTextureUv(source,uv),0.0,false);
    if(colour.a<=0)return vec3(0);
    float bsdfPdf;vec3 brdf=DisneyEval(mat,viewDir,normal,direction,bsdfPdf);
    shadowRay.radiance=vec3(0);shadowRay.throughput=vec3(1);shadowRay.queryPadding=0u;shadowRay.pad0=1u;
    traceRayEXT(topLevelAS,0u,receiverQueryMask(queryTransport,worldUBO.isFirstPerson>0,rayReceiveOnlyReceiver(mainRay)),
        0,0,0,offsetQueryOrigin(point,normal,direction),.0001,direction,max(distance-.001,.0001),1);
    float scale=max(lightPdf,bsdfPdf);
    float a=lightPdf/scale,b=bsdfPdf/scale;
    float mis=a*a/max(a*a+b*b,1e-20);
    return mainRay.throughput*shadowRay.radiance*colour.rgb*colour.a*source.albedoEmission*brdf*mis/lightPdf;
}
#endif
