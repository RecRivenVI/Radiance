package com.radiance.audit.benchmark;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class BenchmarkTransformer implements ClassFileTransformer {
    private static final String HOOK="com/radiance/audit/benchmark/RuntimeHooks";
    private static final String GLFW="org/lwjgl/glfw/GLFW";
    private final Instrumentation instrumentation;
    private final Module hooks;
    public BenchmarkTransformer(Instrumentation instrumentation,Module hooks) { this.instrumentation=instrumentation;this.hooks=hooks; }
    @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> redef,ProtectionDomain domain,byte[] bytes) {
        boolean settings=name!=null && (name.equals("com/radiance/client/pipeline/Pipeline") || name.equals("com/radiance/client/option/Options"));
        if(name==null || !settings && !name.equals(GLFW) && !name.equals("net/minecraft/client/Minecraft") && !name.equals("net/minecraft/class_310"))return null;
        try {
            if(module.isNamed()) {
                Map<String,Set<Module>> opened=new HashMap<>();
                if(!name.equals(GLFW))opened.put(name.substring(0,name.lastIndexOf('/')).replace('/','.'),Set.of(hooks));
                if(settings)for(String p:Set.of("com.radiance.client.pipeline","com.radiance.client.pipeline.config","com.radiance.client.option"))
                    if(module.getPackages().contains(p))opened.put(p,Set.of(hooks));
                instrumentation.redefineModule(module,Set.of(hooks),Map.of(),opened,Set.of(),Map.of());
            }
            if(settings)return null; // Read-only inspection access, no renderer instruction changes.
            byte[] changed=instrument(bytes,Boolean.getBoolean("radiance.audit.unattended"));
            if(changed!=null)RuntimeHooks.transformed(name);
            return changed;
        } catch(Exception e) {
            System.err.println("[Radiance Audit/benchmark] cannot safely instrument "+name+": "+e);
            // Invalid class bytes prevent window creation if isolation cannot be installed.
            return new byte[0];
        }
    }
    private static MethodInsnNode call(String owner,String name,String desc) {return new MethodInsnNode(Opcodes.INVOKESTATIC,owner,name,desc,false);}
    private static void integer(InsnList code,int value){code.add(new LdcInsnNode(value));}
    public static byte[] instrument(byte[] bytes,boolean unattended) {
        ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);boolean changed=false;int frameHooks=0,creates=0;
        for(MethodNode m:c.methods) {
            if((m.access&Opcodes.ACC_NATIVE)!=0)continue;
            if((c.name.equals("net/minecraft/client/Minecraft") && m.name.equals("runTick") || c.name.equals("net/minecraft/class_310") && m.name.equals("method_1523")) && m.desc.equals("(Z)V")) {
                InsnList head=new InsnList();head.add(new VarInsnNode(Opcodes.ALOAD,0));head.add(new VarInsnNode(Opcodes.ILOAD,1));head.add(call(HOOK,"begin","(Ljava/lang/Object;Z)V"));m.instructions.insert(head);
                for(var i:m.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN) {
                    InsnList end=new InsnList();end.add(new VarInsnNode(Opcodes.ALOAD,0));end.add(new VarInsnNode(Opcodes.ILOAD,1));end.add(call(HOOK,"end","(Ljava/lang/Object;Z)V"));m.instructions.insertBefore(i,end);
                }
                changed=true;frameHooks++;
            }
            if(!unattended || !c.name.equals(GLFW))continue;
            InsnList head=new InsnList();
            if(m.name.equals("glfwCreateWindow")) {
                for(int hint:new int[]{0x00020001,0x0002000C}) {integer(head,hint);integer(head,0);head.add(call(GLFW,"glfwWindowHint","(II)V"));}
                // Fullscreen activation cannot satisfy the unattended contract.
                Type[] args=Type.getArgumentTypes(m.desc);int slot=0;
                for(int n=0;n<args.length;n++){if(n==3){head.add(new VarInsnNode(Opcodes.LLOAD,slot));head.add(call(HOOK,"requireWindowed","(J)V"));}slot+=args[n].getSize();}
                for(var i:m.instructions.toArray())if(i.getOpcode()==Opcodes.LRETURN) {
                    InsnList notify=new InsnList();notify.add(new InsnNode(Opcodes.DUP2));notify.add(new LdcInsnNode(Type.getObjectType(GLFW)));
                    notify.add(call(HOOK,"windowCreated","(JLjava/lang/Class;)V"));m.instructions.insertBefore(i,notify);
                }
                creates++;
            } else if(m.name.equals("glfwShowWindow")) {
                head.add(new VarInsnNode(Opcodes.LLOAD,0));integer(head,0x0002000C);integer(head,0);head.add(call(GLFW,"glfwSetWindowAttrib","(JII)V"));
            } else if(m.name.equals("glfwSetInputMode")) {
                // Only the cursor mode is replaced; sticky modes retain their normal semantics.
                head.add(new VarInsnNode(Opcodes.ILOAD,2));head.add(new VarInsnNode(Opcodes.ILOAD,3));head.add(call(HOOK,"cursorMode","(II)I"));head.add(new VarInsnNode(Opcodes.ISTORE,3));
            } else if(m.name.equals("glfwSetWindowMonitor")) {
                head.add(new VarInsnNode(Opcodes.LLOAD,2));head.add(call(HOOK,"requireWindowed","(J)V"));
            } else if(Set.of("glfwFocusWindow","glfwSetCursorPos").contains(m.name)) {
                clear(m);head.add(new InsnNode(Opcodes.RETURN));
            } else if(m.name.equals("glfwGetCursorPos") && (m.desc.equals("(J[D[D)V") || m.desc.equals("(JLjava/nio/DoubleBuffer;Ljava/nio/DoubleBuffer;)V"))) {
                clear(m);head.add(new VarInsnNode(Opcodes.ALOAD,2));head.add(new VarInsnNode(Opcodes.ALOAD,3));
                head.add(call(HOOK,"neutralCursor","(Ljava/lang/Object;Ljava/lang/Object;)V"));head.add(new InsnNode(Opcodes.RETURN));
            } else if(Set.of("glfwSetKeyCallback","glfwSetCharCallback","glfwSetCharModsCallback","glfwSetMouseButtonCallback","glfwSetCursorPosCallback","glfwSetCursorEnterCallback","glfwSetScrollCallback","glfwSetDropCallback").contains(m.name)) {
                head.add(new InsnNode(Opcodes.ACONST_NULL));head.add(new VarInsnNode(Opcodes.ASTORE,2));
            } else if(Set.of("glfwGetKey","glfwGetMouseButton").contains(m.name)) {
                clear(m);head.add(new InsnNode(Opcodes.ICONST_0));head.add(new InsnNode(Opcodes.IRETURN));
            }
            if(head.size()>0){m.instructions.insert(head);changed=true;}
        }
        if(c.name.equals(GLFW) && unattended && creates==0)throw new IllegalArgumentException("No GLFW window creation entry");
        if(!c.name.equals(GLFW) && frameHooks!=1)throw new IllegalArgumentException("Expected exactly one mapped Minecraft frame entry");
        if(!changed)return null;
        // Preserve the real game's stack map types; never guess its class hierarchy as Object.
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(w);return w.toByteArray();
    }
    private static void clear(MethodNode m) {
        m.instructions.clear();m.tryCatchBlocks.clear();
        if(m.localVariables!=null)m.localVariables.clear();
        m.visibleLocalVariableAnnotations=null;m.invisibleLocalVariableAnnotations=null;
    }
}
