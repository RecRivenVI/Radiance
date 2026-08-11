import java.util.*;
import org.objectweb.asm.*;

/** Analysis-tool fixture only: emits and parses class bytes; never loads target classes. */
public final class DrawInventoryTest {
    public static void main(String[] args) throws Exception {
        String[] owners={"org/lwjgl/opengl/GL11", "com/mojang/blaze3d/platform/GlStateManager",
            "com/mojang/blaze3d/systems/RenderSystem", "net/minecraft/client/renderer/RenderType",
            "com/mojang/blaze3d/vertex/VertexConsumer", "net/minecraft/client/gui/GuiGraphics",
            "com/mojang/blaze3d/pipeline/RenderTarget", "net/minecraft/client/renderer/ShaderInstance",
            "foundry/veil/api/client/render/shader/ShaderManager"};
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"draw","()V",null,null);
        m.visitCode();Label label=new Label();m.visitLabel(label);m.visitLineNumber(37,label);
        for(String owner:owners)m.visitMethodInsn(Opcodes.INVOKESTATIC,owner,"example","()V",false);
        m.visitFieldInsn(Opcodes.GETSTATIC,owners[3],"STATE","I");m.visitInsn(Opcodes.POP);
        m.visitInvokeDynamicInsn("run","()Ljava/lang/Runnable;",
            new Handle(Opcodes.H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory","()V",false),
            new Handle(Opcodes.H_INVOKESTATIC,owners[0],"glDrawArrays","()V",false));
        m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.RETURN);m.visitMaxs(1,0);m.visitEnd();w.visitEnd();
        byte[] bytes=w.toByteArray();String hash=DrawInventory.hash(bytes);
        DrawInventory.inspect(bytes,"fixture","1","fixture.jar",hash,"Fixture.class");
        if(DrawInventory.sites.size()!=11)throw new AssertionError("Expected 9 invokes + field + method reference");
        Set<Object> layers=new HashSet<>();Set<Object> ids=new HashSet<>();
        for(var row:DrawInventory.sites){
            layers.add(row.get("layer"));ids.add(row.get("id"));
            if(!row.get("translation").equals("UNKNOWN") || !row.get("runtime").equals("static_only"))
                throw new AssertionError("Scanner must not invent equivalence or runtime proof");
            if(!row.get("bytecode_source_line").equals(37))throw new AssertionError("Source line lost");
        }
        if(layers.size()!=9 || ids.size()!=11)throw new AssertionError("Layer or stable identity collision");
        var firstIds=List.copyOf(ids);DrawInventory.sites.clear();
        DrawInventory.inspect(bytes,"fixture","1","fixture.jar",hash,"Fixture.class");
        if(!new HashSet<>(DrawInventory.sites.stream().map(r->r.get("id")).toList()).equals(new HashSet<>(firstIds)))
            throw new AssertionError("IDs are not repeatable");
        if(DrawInventory.risks.stream().noneMatch(r->"invokedynamic_dispatch_unresolved".equals(r.get("risk"))))
            throw new AssertionError("Indirect dispatch limitation lost");
        System.out.println("PASS: 9 API layers, fields, method references, source lines, stable IDs and explicit unknowns");
    }
}
