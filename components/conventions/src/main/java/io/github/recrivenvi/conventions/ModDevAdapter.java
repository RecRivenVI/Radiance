package io.github.recrivenvi.conventions;

import java.util.List;
import net.neoforged.moddevgradle.dsl.ModDevExtension;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension;
import org.gradle.api.artifacts.Dependency;

final class ModDevAdapter {
    private ModDevAdapter() {}

    static void neoForge(TargetContext context) {
        NeoForgeExtension neoForge =
                context.project().getExtensions().getByType(NeoForgeExtension.class);
        neoForge.enable(
                settings -> {
                    settings.setVersion(context.facts().loaderVersion());
                    settings.setDisableRecompilation(true);
                });
        configure(context, neoForge);
    }

    static void legacyForge(TargetContext context) {
        LegacyForgeExtension forge =
                context.project().getExtensions().getByType(LegacyForgeExtension.class);
        forge.enable(
                settings -> {
                    settings.setForgeVersion(
                            context.facts().minecraftVersion()
                                    + "-"
                                    + context.facts().loaderVersion());
                    settings.setDisableRecompilation(true);
                });
        configure(context, forge);
    }

    private static void configure(TargetContext context, ModDevExtension extension) {
        if (extension.getVersionCapabilities().legacyClasspath())
            for (Dependency component : context.products().getDependencies())
                context.project()
                        .getDependencies()
                        .add("additionalRuntimeClasspath", component.copy());
        extension.getMods().create(context.modId(), mod -> mod.sourceSet(context.main()));
        ModModel probe =
                context.probe() == null
                        ? null
                        : extension
                                .getMods()
                                .create(
                                        context.probeModId(),
                                        mod -> mod.sourceSet(context.probe()));
        extension
                .getRuns()
                .configureEach(
                        model -> {
                            if (!context.managed(model.getName()))
                                model.getGameDirectory().set(context.buildRun(model.getName()));
                            // 只有验证运行加载探针；其他运行默认加载除探针以外的模组。
                            if (probe != null && !context.validation(model.getName()))
                                model.getLoadedMods()
                                        .convention(
                                                context.project()
                                                        .provider(() -> without(extension, probe)));
                        });
        for (InstanceRun run : context.runs())
            extension.getRuns().create(run.name(), model -> configure(context, run, model));
        for (InstanceRun run : context.validations())
            extension
                    .getRuns()
                    .create(
                            run.name(),
                            model -> {
                                configure(context, run, model);
                                if (context.probe() != null)
                                    model.getSourceSet().set(context.probe());
                                model.getSystemProperties().putAll(run.properties());
                                model.disableIdeRun();
                            });
    }

    private static List<ModModel> without(ModDevExtension extension, ModModel probe) {
        return extension.getMods().stream().filter(mod -> mod != probe).toList();
    }

    private static void configure(TargetContext context, InstanceRun run, RunModel model) {
        if (run.client()) model.client();
        else model.server();
        model.getGameDirectory().set(context.directory(run));
        model.getJvmArguments().addAll(run.launch().jvmArguments());
        model.getProgramArguments().addAll(run.launch().gameArguments());
        model.taskBefore(context.project().getTasks().named(run.prepareTaskName()));
    }
}
