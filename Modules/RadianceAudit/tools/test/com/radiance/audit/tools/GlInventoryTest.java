package com.radiance.audit.tools;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;
class GlInventoryTest {
    @TempDir Path temp;
    private byte[] fixture() {
        ClassWriter c=new ClassWriter(0);c.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,"example/Renderer",null,"java/lang/Object",null);
        var a=c.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;",false);a.visit("targets","example.Target");a.visitEnd();
        var m=c.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"render","()V",null,null);m.visitCode();
        m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glDrawArrays","(III)V",false);
        m.visitLdcInsn(new Handle(Opcodes.H_INVOKESTATIC,"org/lwjgl/opengl/GL11","glClear","(I)V",false));m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(3,0);m.visitEnd();
        m=c.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"wrapper","()V",null,null);m.visitCode();
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"example/Renderer","render","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        c.visitEnd();return c.toByteArray();
    }
    private Path jar(String name,byte[] bytes,String entry)throws Exception {
        var p=temp.resolve(name);try(var z=new ZipOutputStream(Files.newOutputStream(p))) {
            z.putNextEntry(new ZipEntry(entry));z.write(bytes);z.closeEntry();
        }return p;
    }
    @Test void scansRealInstructionsAndMethodHandlesWithoutInventingTranslation()throws Exception {
        var p=jar("sample.jar",fixture(),"example/Renderer.class");
        var s=GlInventory.scan(List.of(new GlInventory.Artifact(p.toString(),"mod")));
        assertEquals(2,s.calls().size());assertEquals("DRAW",s.calls().getFirst().kind());
        assertEquals("CLEAR",s.calls().get(1).kind());assertTrue(s.calls().stream().allMatch(x->x.translation().equals("UNKNOWN")));
        assertEquals(1,s.hooks().size());assertTrue(GlInventory.callers(s).contains("example/Renderer#wrapper()V"));
        assertNotEquals(s.calls().getFirst().id(),s.calls().get(1).id());
    }
    @Test void includesNestedJarsAndReportsDuplicateDefinitions()throws Exception {
        var inner=jar("inner.jar",fixture(),"example/Renderer.class");
        var outer=jar("outer.jar",Files.readAllBytes(inner),"META-INF/jarjar/inner.jar");
        var s=GlInventory.scan(List.of(new GlInventory.Artifact(outer.toString(),"mod"),new GlInventory.Artifact(inner.toString(),"library")));
        assertEquals(4,s.calls().size());assertEquals(2,s.classDefinitions().get("example/Renderer"));
        assertTrue(s.calls().getFirst().source().artifact().contains("!META-INF/jarjar/inner.jar"));
    }
    @Test void decisionsNeedEvidenceAndCannotSurviveAnArtifactChange() throws Exception {
        var p=jar("sample.jar",fixture(),"example/Renderer.class");
        var s=GlInventory.scan(List.of(new GlInventory.Artifact(p.toString(),"mod")));
        var id=s.calls().getFirst().id();
        assertThrows(IllegalArgumentException.class, () -> GlInventory.review(s, List.of(
            new GlInventory.Decision(id,"TRANSLATED","PRESERVED",""))));
        assertThrows(IllegalArgumentException.class, () -> GlInventory.review(s, List.of(
            new GlInventory.Decision("old-artifact-id","TRANSLATED","PRESERVED","review.md#case"))));
        var reviewed=GlInventory.review(s,List.of(new GlInventory.Decision(id,"TRANSLATED","UNKNOWN","review.md#case")));
        assertEquals("TRANSLATED",reviewed.calls().getFirst().translation());
        assertEquals("UNKNOWN",reviewed.calls().get(1).translation());
    }
    @Test void malformedInputCannotMasqueradeAsAnEmptyInventory() throws Exception {
        Path p=temp.resolve("invalid.jar");Files.writeString(p,"not a jar");
        assertThrows(java.io.IOException.class,()->GlInventory.scan(List.of(new GlInventory.Artifact(p.toString(),"mod"))));
    }
}
