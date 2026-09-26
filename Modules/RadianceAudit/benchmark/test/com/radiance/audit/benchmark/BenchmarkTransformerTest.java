package com.radiance.audit.benchmark;

import java.nio.file.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkTransformerTest {
    @TempDir Path temp;
    private byte[] compile(String name,String source)throws Exception {
        Path p=temp.resolve(name.replace('.','/')+".java");Files.createDirectories(p.getParent());Files.writeString(p,source);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","21","-d",temp.toString(),p.toString()));
        return Files.readAllBytes(temp.resolve(name.replace('.','/')+".class"));
    }
    private Class<?> load(String name,byte[] b){return new ClassLoader(getClass().getClassLoader()){Class<?> define(){return defineClass(name,b,0,b.length);}}.define();}
    private Class<?> glfw()throws Exception {
        byte[] b=compile("org.lwjgl.glfw.GLFW","""
            package org.lwjgl.glfw;
            public class GLFW {
                public static int hints, focusHint=-1, showHint=-1, focusCalls, warps, shown, mode;
                public static Object callback;
                public static void glfwWindowHint(int hint,int value) { hints++; if(hint==0x20001)focusHint=value; if(hint==0x2000C)showHint=value; }
                public static void glfwSetWindowAttrib(long w,int a,int v) { showHint=v; }
                public static int glfwGetWindowAttrib(long w,int a) { return 0; }
                public static void glfwGetFramebufferSize(long w,int[] x,int[] y) { x[0]=800;y[0]=600; }
                public static long glfwCreateWindow(int x,int y,CharSequence title,long monitor,long share){return 123;}
                public static void glfwShowWindow(long w){shown++;}
                public static void glfwFocusWindow(long w){focusCalls++;}
                public static void glfwSetCursorPos(long w,double x,double y){warps++;}
                public static void glfwSetInputMode(long w,int m,int value){mode=value;}
                public static int glfwGetKey(long w,int k){return 1;}
                public static int glfwGetMouseButton(long w,int k){return 1;}
                public static void glfwGetCursorPos(long w,double[] x,double[] y){x[0]=77;y[0]=88;}
                public static Object glfwSetKeyCallback(long w,Object cb){Object old=callback; callback=cb; return old;}
                public static void glfwPollEvents(){shown++;}
            }
            """);
        return load("org.lwjgl.glfw.GLFW",BenchmarkTransformer.instrument(b,true));
    }
    @Test void actualTransformedWindowAndInputMethodsDoNotFocusGrabOrDeliverInput()throws Exception {
        Class<?> g=glfw();
        assertEquals(123L,g.getMethod("glfwCreateWindow",int.class,int.class,CharSequence.class,long.class,long.class).invoke(null,800,600,"test",0L,0L));
        assertEquals(0,g.getField("focusHint").get(null));assertEquals(0,g.getField("showHint").get(null));
        g.getMethod("glfwShowWindow",long.class).invoke(null,123L);
        g.getMethod("glfwFocusWindow",long.class).invoke(null,123L);
        g.getMethod("glfwSetCursorPos",long.class,double.class,double.class).invoke(null,123L,1d,2d);
        g.getMethod("glfwSetInputMode",long.class,int.class,int.class).invoke(null,123L,0x33001,0x34003);
        assertEquals(0x34001,g.getField("mode").get(null));
        g.getMethod("glfwSetInputMode",long.class,int.class,int.class).invoke(null,123L,123,7);
        assertEquals(7,g.getField("mode").get(null));
        assertEquals(0,g.getMethod("glfwGetKey",long.class,int.class).invoke(null,123L,1));
        assertEquals(0,g.getMethod("glfwGetMouseButton",long.class,int.class).invoke(null,123L,1));
        double[] x={77},y={88};g.getMethod("glfwGetCursorPos",long.class,double[].class,double[].class).invoke(null,123L,x,y);
        assertEquals(0d,x[0]);assertEquals(0d,y[0]);
        g.getMethod("glfwSetKeyCallback",long.class,Object.class).invoke(null,123L,new Object());
        assertNull(g.getField("callback").get(null));
        assertEquals(0,g.getField("focusCalls").get(null));assertEquals(0,g.getField("warps").get(null));
        g.getMethod("glfwPollEvents").invoke(null);assertEquals(2,g.getField("shown").get(null));
    }
    @Test void fullscreenIsRejectedRatherThanSilentlyChangingBenchmarkConditions()throws Exception {
        Class<?> g=glfw();
        var e=assertThrows(java.lang.reflect.InvocationTargetException.class,()->g.getMethod("glfwCreateWindow",int.class,int.class,CharSequence.class,long.class,long.class).invoke(null,800,600,"test",1L,0L));
        assertInstanceOf(IllegalStateException.class,e.getCause());
    }
    @Test void defaultOffLeavesGlfwUnmodified()throws Exception {
        byte[] b=compile("org.lwjgl.glfw.GLFW","package org.lwjgl.glfw; public class GLFW { public static void glfwFocusWindow(long w){} }");
        assertNull(BenchmarkTransformer.instrument(b,false));
        assertThrows(IllegalArgumentException.class,()->BenchmarkTransformer.instrument(b,true));
    }
    @Test void preservedStackMapFramesExecuteTheActualInstrumentedFrameBody()throws Exception {
        byte[] b=compile("net.minecraft.client.Minecraft","""
            package net.minecraft.client;
            public class Minecraft {
                public Object level=new Object(); private boolean windowActive=false;
                public boolean stopped; public int renders;
                public void runTick(boolean render){ CharSequence s=render?"x":new StringBuilder("xx"); renders+=s.length(); }
                public void stop(){stopped=true;}
            }
            """);
        Class<?> c=load("net.minecraft.client.Minecraft",BenchmarkTransformer.instrument(b,false));
        Object client=c.getConstructor().newInstance();c.getMethod("runTick",boolean.class).invoke(client,true);
        c.getMethod("runTick",boolean.class).invoke(client,false);assertEquals(3,c.getField("renders").get(client));
    }
    public static class FakeAttribute { public String name="quality",value="balanced"; }
    public static class FakeModule { public String name="dlss"; public java.util.List<FakeAttribute> attributeConfigs=java.util.List.of(new FakeAttribute()); }
    public static class FakePipeline {
        public static FakePipeline INSTANCE=new FakePipeline();
        public static String getActivePreset(){return "rt_dlss";}
        public java.util.List<FakeModule> getModules(){return java.util.List.of(new FakeModule());}
    }
    public static class FakeOptions { public static boolean vsync=false; public static int rayBounces=4; }
    @Test void settingsAreReadFromLiveModulesRatherThanRequestedConfig()throws Exception {
        var p=RuntimeHooks.readSettings(FakePipeline.class,FakeOptions.class);
        assertEquals("rt_dlss",p.getProperty("activePreset"));assertEquals("dlss",p.getProperty("activeModules"));
        assertEquals("balanced",p.getProperty("quality"));assertEquals("false",p.getProperty("option.vsync"));
        FakePipeline.INSTANCE.getModules().getFirst().attributeConfigs.getFirst().value="quality";
        assertEquals(4,FakeOptions.rayBounces);
    }
}
