package com.radiance.audit.benchmark;

import java.lang.instrument.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;

/** Startup-only companion: runs before SERVICE and GAME can create a window. */
public final class AgentBootstrap {
    private static JarFile hooksJar;
    private static URLClassLoader transformerLoader;

    public static void premain(String ignored, Instrumentation instrumentation) throws Exception {
        if (!Boolean.getBoolean("radiance.audit.benchmark")) return;
        Path cwd=Path.of(System.getProperty("user.dir")).toRealPath();
        if (!Boolean.getBoolean("radiance.audit.experiments") || !Files.isRegularFile(cwd.resolve(".radiance-audit-test-instance")))
            throw new IllegalStateException("Benchmark requires explicit experiments and an isolated-instance marker");
        Path path=extract(cwd,"audit-benchmark-hooks.jar");
        hooksJar=new JarFile(path.toFile());instrumentation.appendToBootstrapClassLoaderSearch(hooksJar);
        Class<?> hooks=Class.forName("com.radiance.audit.benchmark.RuntimeHooks",true,null);
        hooks.getMethod("initialize").invoke(null);
        // The transformer and ASM are nested resources, not entries on the game's classpath.
        Path transformerPath=extract(cwd,"audit-benchmark-transformer.jar");
        transformerLoader=new URLClassLoader(new URL[]{transformerPath.toUri().toURL()},ClassLoader.getPlatformClassLoader());
        var transformer=(ClassFileTransformer)Class.forName("com.radiance.audit.benchmark.BenchmarkTransformer",true,transformerLoader)
            .getConstructor(Instrumentation.class,Module.class).newInstance(instrumentation,hooks.getModule());
        instrumentation.addTransformer(transformer,false);
        System.setProperty("radiance.audit.benchmark.active","true");
        System.err.println("[Radiance Audit/benchmark] startup companion active; unattended="+Boolean.getBoolean("radiance.audit.unattended"));
    }
    private static Path extract(Path cwd,String name)throws Exception {
        byte[] bytes;
        try(var stream=AgentBootstrap.class.getResourceAsStream("/"+name)) {
            if(stream==null)throw new IllegalStateException("Missing benchmark hooks");
            bytes=stream.readAllBytes();
        }
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Path directory=cwd.resolve("radiance-audit/benchmark-bootstrap/"+hash);Files.createDirectories(directory);
        Path path=directory.resolve(name);
        if(!Files.exists(path))Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
        if(!Arrays.equals(bytes,Files.readAllBytes(path)))throw new IllegalStateException("Extracted hooks identity mismatch");
        return path;
    }
}
