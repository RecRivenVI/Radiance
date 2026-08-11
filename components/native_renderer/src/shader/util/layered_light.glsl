#ifndef LAYERED_LIGHT_GLSL
#define LAYERED_LIGHT_GLSL
#include "util/layered_texture.glsl"
float layeredEmitterPdf(LayeredWorldMaterial material,vec3 receiver,vec3 point) {
    vec3 delta=point-receiver;float d2=dot(delta,delta);
    float cosine=abs(delta.y)/sqrt(max(d2,1e-20));
    float area=4*material.origin.w*material.origin.w;
    return cosine>1e-8 && area>0 ? d2/(cosine*area*float(material.settings.y)*float(mapping.programInfo.x)):0;
}
#ifndef LAYERED_LIGHT_PDF_ONLY
vec3 sampleLayeredWorldLight(LabPBRMat mat,vec3 point,vec3 normal,vec3 viewDir) {
    if(mapping.programInfo.x==0u || mat.transmission>EPS)return vec3(0);
    uint selected=min(uint(rand(mainRay.seed)*float(mapping.programInfo.x)),mapping.programInfo.x-1u);
    LayeredWorldMaterial material=mapping.programs[mapping.programEmitters[selected]];
    uint layer=min(uint(rand(mainRay.seed)*float(material.settings.y)),material.settings.y-1u);
    vec3 samplePoint=layeredOriginScene(material,worldUBO.cameraPos.xyz)+vec3((rand(mainRay.seed)*2-1)*material.origin.w,
        float(layer)*material.surface.x,(rand(mainRay.seed)*2-1)*material.origin.w);
    vec3 receiver=point,delta=samplePoint-receiver;
    float distance=length(delta);if(distance<.001)return vec3(0);
    vec3 direction=delta/distance;
    if(dot(normal,direction)<=0)return vec3(0);
    vec4 tint=vec4(1);
    if(int(material.settings.z)>=0) {
        ivec2 size=textureSize(textures[nonuniformEXT(material.settings.z)],0);
        tint=texelFetch(textures[nonuniformEXT(material.settings.z)],ivec2(int(layer)%size.x,0),0);
    }
    vec4 light=evaluateLayeredPlane(material,samplePoint,worldUBO.cameraPos.xyz,tint,worldUBO.gameTime);
    float lightPdf=layeredEmitterPdf(material,receiver,samplePoint);
    if(lightPdf<=0 || light.a<=0)return vec3(0);
    float bsdfPdf;vec3 brdf=DisneyEval(mat,viewDir,normal,direction,bsdfPdf);
    shadowRay.radiance=vec3(0);shadowRay.throughput=vec3(1);shadowRay.queryPadding=0u;shadowRay.pad0=1u;
    traceRayEXT(topLevelAS,0u,receiverQueryMask(queryTransport,worldUBO.isFirstPerson>0,rayReceiveOnlyReceiver(mainRay)),
        0,0,0,offsetQueryOrigin(point,normal,direction),.0001,direction,max(distance-.001,.0001),1);
    float mis=lightPdf*lightPdf/max(lightPdf*lightPdf+bsdfPdf*bsdfPdf,1e-20);
    return mainRay.throughput*shadowRay.radiance*light.rgb*light.a*material.surface.y/
        float(material.settings.y)*brdf*mis/lightPdf;
}
#endif
#endif
