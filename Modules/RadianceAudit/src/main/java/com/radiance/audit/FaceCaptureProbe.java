package com.radiance.audit;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.render.MaterialFaces;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;
/** Verifies an actual transformed third-party-style callback with NO OpenGL context. */
public final class FaceCaptureProbe extends RenderType {
    private FaceCaptureProbe(String name,Runnable setup) {
        super(name,DefaultVertexFormat.POSITION,VertexFormat.Mode.QUADS,256,false,false,setup,()->{});
    }
    public static void verify() {
        var before=MaterialFaces.state();
        RenderType back=new FaceCaptureProbe("face_probe_back",()->{
            GL11.glEnable(GL11.GL_CULL_FACE);GL11.glCullFace(GL11.GL_BACK);GL11.glFrontFace(GL11.GL_CCW);
        });
        RenderType front=new FaceCaptureProbe("face_probe_front",()->{
            GL11.glEnable(GL11.GL_CULL_FACE);GL11.glCullFace(GL11.GL_FRONT);GL11.glFrontFace(GL11.GL_CW);
        });
        RenderType both=new FaceCaptureProbe("face_probe_disabled",()->GL11.glDisable(GL11.GL_CULL_FACE));
        if(MaterialFaces.capture(back)!=MaterialFaces.flags(true,GL11.GL_BACK,GL11.GL_CCW)
            || MaterialFaces.capture(front)!=MaterialFaces.flags(true,GL11.GL_FRONT,GL11.GL_CW)
            || MaterialFaces.capture(both)!=0 || !before.equals(MaterialFaces.state()))
            throw new IllegalStateException("Actual RenderType/direct GL face-state capture failed");
        org.slf4j.LoggerFactory.getLogger("RadianceAudit").info("FACE_CAPTURE_PROBE PASS real RenderType callbacks/direct GL translation/state restore without a context");
    }
}
