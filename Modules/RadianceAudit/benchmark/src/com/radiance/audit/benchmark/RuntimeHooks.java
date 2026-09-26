package com.radiance.audit.benchmark;

import java.lang.management.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Loader-neutral common metrics. No fork JNI, GL context, focus fabrication or GPU waits. */
public final class RuntimeHooks {
    private static final int LIMIT=200_000;
    private static final long[][] samples=new long[LIMIT][];
    private static final ThreadMXBean threads=ManagementFactory.getThreadMXBean();
    private static volatile int count;
    private static long started,worldStart,frameStart,cpuStart,lastStart;
    private static long warmup,sampleDuration,startupTimeout;
    private static Field level,focused;
    private static Method stop;
    private static Method windowAttribute;
    private static Method framebufferSize;
    private static final int[] width=new int[1],height=new int[1];
    private static long window;
    private static int windowsCreated;
    private static boolean bound,stopRequested,inFrame,flushed;
    private static String error="",adapter="unbound";
    private static Path output;
    private static final List<String> transformed=Collections.synchronizedList(new ArrayList<>());
    private RuntimeHooks() {}

    public static void initialize() throws Exception {
        String version=System.getProperty("radiance.audit.benchmark.version","");
        if(!Set.of("1.21.1-neoforge","1.21.4-fabric").contains(version))
            throw new IllegalArgumentException("Specify radiance.audit.benchmark.version=1.21.1-neoforge or 1.21.4-fabric");
        warmup=seconds("radiance.audit.benchmark.warmupSeconds",30);
        sampleDuration=seconds("radiance.audit.benchmark.sampleSeconds",30);
        startupTimeout=seconds("radiance.audit.benchmark.startupSeconds",180);
        if(threads.isThreadCpuTimeSupported() && !threads.isThreadCpuTimeEnabled())threads.setThreadCpuTimeEnabled(true);
        output=Path.of("radiance-audit/benchmark/"+System.currentTimeMillis());Files.createDirectories(output);
        Files.writeString(output.resolve("CONFIG.txt"),"version="+version+"\nunattended="+Boolean.getBoolean("radiance.audit.unattended")
            +"\nwarmupNs="+warmup+"\nsampleNs="+sampleDuration+"\nnativeGpuTimings=unavailable\ngeneratedFrames=unavailable\n");
        started=System.nanoTime();
        Runtime.getRuntime().addShutdownHook(new Thread(RuntimeHooks::flush,"Radiance-Audit-benchmark-save"));
    }
    private static long seconds(String key,int fallback) {
        int value=Integer.getInteger(key,fallback);
        if(value<1 || value>3600)throw new IllegalArgumentException(key+" must be 1..3600");
        return value*1_000_000_000L;
    }
    public static void transformed(String name) { transformed.add(name); }
    public static void windowCreated(long handle,Class<?> glfw) {
        try {window=handle;windowsCreated++;windowAttribute=glfw.getMethod("glfwGetWindowAttrib",long.class,int.class);
            framebufferSize=glfw.getMethod("glfwGetFramebufferSize",long.class,int[].class,int[].class);}
        catch(ReflectiveOperationException e){fail(e);}
    }
    public static int cursorMode(int mode,int value) { return mode==0x00033001?0x00034001:value; }
    public static void neutralCursor(Object x,Object y) {
        for(Object target:new Object[]{x,y}) {
            if(target instanceof java.nio.DoubleBuffer b)b.put(b.position(),0d);
            else if(target instanceof double[] a)a[0]=0d;
        }
    }
    public static void requireWindowed(long monitor) {
        if(monitor!=0)throw new IllegalStateException("Unattended benchmarks require a windowed configuration; fullscreen was not silently changed");
    }
    private static Field field(Class<?> type,String name)throws ReflectiveOperationException {
        var f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static void bind(Object client)throws ReflectiveOperationException {
        boolean intermediary=client.getClass().getName().equals("net.minecraft.class_310");
        adapter=intermediary?"fabric-1.21.4-intermediary":"neoforge-1.21.1-mojmap";
        level=field(client.getClass(),intermediary?"field_1687":"level");
        focused=field(client.getClass(),intermediary?"field_1695":"windowActive");
        stop=client.getClass().getDeclaredMethod(intermediary?"method_1592":"stop");stop.setAccessible(true);bound=true;
    }
    public static void begin(Object client,boolean render) {
        if(!render)return;
        try {
            if(!bound)bind(client);
            inFrame=true;frameStart=System.nanoTime();cpuStart=threads.isThreadCpuTimeSupported()?threads.getCurrentThreadCpuTime():-1;
        } catch(ReflectiveOperationException|RuntimeException ex) { fail(ex); }
    }
    public static void end(Object client,boolean render) {
        if(!render || !bound)return;
        try {
            long now=System.nanoTime();boolean world=level.get(client)!=null;
            if(world && worldStart==0) {
                worldStart=frameStart;
                if(output!=null)captureSettings(client,"world-start");
            }
            long elapsed=worldStart==0?0:frameStart-worldStart;
            if(world && elapsed>=warmup && elapsed<warmup+sampleDuration && count<LIMIT) {
                long cpu=cpuStart<0?-1:threads.getCurrentThreadCpuTime()-cpuStart;
                int actualFocus=windowAttribute==null?-1:(int)windowAttribute.invoke(null,window,0x00020001);
                int minimized=windowAttribute==null?-1:(int)windowAttribute.invoke(null,window,0x00020002);
                if(framebufferSize!=null)framebufferSize.invoke(null,window,width,height);
                samples[count]=new long[]{frameStart-started,now-frameStart,lastStart==0?0:frameStart-lastStart,cpu,focused.getBoolean(client)?1:0,actualFocus,width[0],height[0],minimized};
                count++;
            }
            lastStart=frameStart;inFrame=false;
            if(!stopRequested && (count>=LIMIT || elapsed>=warmup+sampleDuration || (worldStart==0 && now-started>startupTimeout))) {
                if(worldStart==0)error="WORLD_NOT_ENTERED";
                if(world && output!=null)captureSettings(client,"sample-end");
                stopRequested=true;stop.invoke(client); // Original client shutdown saves integrated server.
            }
        } catch(ReflectiveOperationException|RuntimeException ex) { fail(ex); }
    }
    private static void fail(Exception e) {
        if(error.isEmpty()){error=e.toString();System.err.println("[Radiance Audit/benchmark] observer invalid: "+error);}
    }
    /** Java-selected active modules/attributes, not a claim of SDK evaluate success. */
    private static void captureSettings(Object client,String phase) {
        try {
            ClassLoader loader=client.getClass().getClassLoader();
            Properties values=readSettings(Class.forName("com.radiance.client.pipeline.Pipeline",false,loader),
                    Class.forName("com.radiance.client.option.Options",false,loader));
            try(var w=Files.newBufferedWriter(output.resolve(phase+"-settings.properties"))){values.store(w,"Observed live Java pipeline and options; no setters invoked");}
        }catch(Exception e){fail(e);}
    }
    public static Properties readSettings(Class<?> pipeline,Class<?> options)throws ReflectiveOperationException {
        Properties p=new Properties();
        p.setProperty("activePreset",String.valueOf(pipeline.getMethod("getActivePreset").invoke(null)));
        Object instance=pipeline.getField("INSTANCE").get(null);
        var modules=(List<?>)pipeline.getMethod("getModules").invoke(instance);
        List<String> names=new ArrayList<>();
        for(Object m:modules) {
            String name=String.valueOf(m.getClass().getField("name").get(m));names.add(name);
            var attributes=(List<?>)m.getClass().getField("attributeConfigs").get(m);
            if(attributes!=null)for(Object a:attributes)
                p.setProperty(String.valueOf(a.getClass().getField("name").get(a)),String.valueOf(a.getClass().getField("value").get(a)));
        }
        p.setProperty("activeModules",String.join(",",names));
        for(Field f:options.getFields())if(Modifier.isStatic(f.getModifiers()) && (f.getType().isPrimitive() || f.getType()==String.class))
            p.setProperty("option."+f.getName(),String.valueOf(f.get(null)));
        return p;
    }
    public static synchronized void flush() {
        if(flushed || output==null)return;flushed=true;
        try {
            try(var w=Files.newBufferedWriter(output.resolve("frames.csv"))) {
                w.write("start_ns,frame_wall_ns,start_interval_ns,render_thread_cpu_ns,minecraft_cached_focused,glfw_actual_focused,framebuffer_width,framebuffer_height,iconified\n");
                int n=count;for(int i=0;i<n;i++){long[] row=samples[i];for(int j=0;j<row.length;j++){if(j>0)w.write(",");w.write(Long.toString(row[j]));}w.write("\n");}
            }
            Files.writeString(output.resolve("STATUS.txt"),"adapter="+adapter+"\nframes="+count+"\nworldEntered="+(worldStart!=0)
                +"\nnormalStopRequested="+stopRequested+"\nincompleteFrame="+inFrame+"\nwindowsCreated="+windowsCreated+"\nerror="+error+"\n"
                +"This observer does not prove native close, world save, visible output, or generated-frame delivery. Inspect client logs separately.\n");
            Files.write(output.resolve("transformed.txt"),List.copyOf(transformed));
        } catch(Exception e){System.err.println("[Radiance Audit/benchmark] cannot write evidence: "+e);}
    }
}
