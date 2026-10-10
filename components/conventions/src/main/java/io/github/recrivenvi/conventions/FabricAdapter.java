package io.github.recrivenvi.conventions;

import io.github.recrivenvi.configuration.TargetFacts;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

final class FabricAdapter {
    private FabricAdapter() {}

    static void prepare(Project project, TargetFacts facts, String plugin, SourceSet probe) {
        if (!project.getPluginManager().hasPlugin(plugin))
            throw new GradleException(
                    "versions/" + facts.name() + "/build.gradle.kts 必须应用 " + plugin);
        LoomGradleExtensionAPI loom =
                project.getExtensions().getByType(LoomGradleExtensionAPI.class);
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add("minecraft", "com.mojang:minecraft:" + facts.minecraftVersion());
        String loader = "net.fabricmc:fabric-loader:" + facts.loaderVersion();
        if (facts.remapped()) {
            dependencies.add(
                    "mappings",
                    facts.mappings().equals("yarn")
                            ? "net.fabricmc:yarn:" + facts.properties().get("yarn_version") + ":v2"
                            : loom.officialMojangMappings());
            dependencies.add("modImplementation", loader);
        } else dependencies.add("implementation", loader);
        loom.splitEnvironmentSourceSets();
        // 探针与客户端源码集一样可以调用客户端代码；服务端运行时由加载器跳过它的客户端入口。
        if (probe != null) {
            SourceSet client = client(project);
            probe.setCompileClasspath(
                    probe.getCompileClasspath()
                            .plus(client.getOutput())
                            .plus(client.getCompileClasspath()));
            probe.setRuntimeClasspath(
                    probe.getRuntimeClasspath()
                            .plus(client.getOutput())
                            .plus(client.getRuntimeClasspath()));
        }
    }

    static void configure(TargetContext context) {
        var project = context.project();
        LoomGradleExtensionAPI loom =
                project.getExtensions().getByType(LoomGradleExtensionAPI.class);
        SourceSet client = client(project);
        loom.getMods()
                .create(
                        context.modId(),
                        mod -> {
                            mod.sourceSet(context.main());
                            mod.sourceSet(client);
                            mod.configuration(context.products());
                        });
        // 加载器只把类路径上的目录当作模组，探针只出现在验证运行的类路径上。
        if (context.probe() != null)
            loom.getMods().create(context.probeModId(), mod -> mod.sourceSet(context.probe()));
        loom.getRuns()
                .configureEach(
                        settings -> {
                            if (!context.managed(settings.getName()))
                                settings.getRunDirectory()
                                        .set(context.buildRun(settings.getName()));
                        });
        for (InstanceRun run : context.runs()) configure(context, run, loom);
        for (InstanceRun run : context.validations()) {
            RunConfigSettings settings = configure(context, run, loom);
            if (context.probe() != null) settings.getSourceSet().set(context.probe().getName());
            settings.getSystemProperties().putAll(run.properties());
            settings.getGenerateRunConfig().set(false);
        }
    }

    private static RunConfigSettings configure(
            TargetContext context, InstanceRun run, LoomGradleExtensionAPI loom) {
        RunConfigSettings settings = loom.getRuns().maybeCreate(run.name());
        if (run.client()) settings.client();
        else settings.server();
        settings.getRunDirectory().set(context.directory(run));
        settings.getJvmArguments().addAll(run.launch().jvmArguments());
        settings.getProgramArguments().addAll(run.launch().gameArguments());
        return settings;
    }

    private static SourceSet client(Project project) {
        return project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName("client");
    }
}
