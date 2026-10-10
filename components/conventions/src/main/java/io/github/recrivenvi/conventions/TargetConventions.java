package io.github.recrivenvi.conventions;

import io.github.recrivenvi.configuration.Instance;
import io.github.recrivenvi.configuration.RepositoryConfiguration;
import io.github.recrivenvi.configuration.TargetFacts;
import io.github.recrivenvi.configuration.Validation;
import io.github.recrivenvi.configuration.VerifyInputsTask;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.language.jvm.tasks.ProcessResources;

public abstract class TargetConventions implements Plugin<Project> {
    private static final Map<String, String> LOADER_PLUGINS =
            Map.of(
                    "neoforge", "net.neoforged.moddev",
                    "forge", "net.neoforged.moddev.legacyforge",
                    "fabric", "net.fabricmc.fabric-loom");
    private static final String REMAP_PLUGIN = "net.fabricmc.fabric-loom-remap";
    private static final List<String> LICENSE_FILES =
            List.of("LICENSE", "LICENSE.md", "LICENSE.txt", "NOTICE", "COPYING", "COPYING.LESSER");
    private static final Map<String, String> LOADER_METADATA =
            Map.of(
                    "neoforge", "META-INF/neoforge.mods.toml",
                    "forge", "META-INF/mods.toml",
                    "fabric", "fabric.mod.json");
    private static final List<String> METADATA_FILES =
            List.of(
                    "META-INF/mods.toml",
                    "META-INF/neoforge.mods.toml",
                    "fabric.mod.json",
                    "pack.mcmeta");

    @Override
    public void apply(Project project) {
        RepositoryConfiguration repository =
                project.getGradle().getExtensions().getByType(RepositoryConfiguration.class);
        String target = project.getName();
        TargetFacts facts = repository.target(target);
        String modId = repository.getModId();
        Map<String, String> metadata = Metadata.of(repository, target);

        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply("com.diffplug.spotless");
        project.setGroup(repository.getProjectFacts().get("mod_group"));
        project.setVersion(repository.getProjectFacts().get("mod_version"));
        project.getExtensions()
                .getByType(BasePluginExtension.class)
                .getArchivesName()
                .set(modId + "-" + target);
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(facts.javaVersion()));
        project.getTasks()
                .withType(JavaCompile.class)
                .configureEach(
                        task -> {
                            task.getOptions().setEncoding("UTF-8");
                            task.getOptions().getRelease().set(facts.javaVersion());
                        });

        TargetExtension extension =
                project.getExtensions()
                        .create(
                                "target",
                                TargetExtension.class,
                                target,
                                repository.metadata(target),
                                repository);
        // Loom 在配置阶段解析类路径，所以缺失的输入先解析为空，
        // 由这个任务在编译之前说明缺少什么。
        TaskProvider<VerifyInputsTask> inputs =
                project.getTasks()
                        .register(
                                "verifyInputs",
                                VerifyInputsTask.class,
                                task -> {
                                    task.setGroup("verification");
                                    task.setDescription("核对这个 Target 用到的本地输入。");
                                    task.getConfiguration().set(repository);
                                    task.getNames().set(project.provider(extension::inputs));
                                });
        project.getTasks()
                .withType(JavaCompile.class)
                .configureEach(task -> task.dependsOn(inputs));

        SourceSet main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        expand(project, main, metadata);
        licenses(project, main);
        SourceSet probe = probe(project, facts, modId, java, main, metadata);
        // 监听 net.fabricmc.fabric-loom 本身会让 Loom 误以为游戏被混淆并要求映射表；
        // 它内部的 fabric-loom 插件在扩展准备好之后才完成应用。
        if (facts.loader().equals("fabric"))
            project.getPluginManager()
                    .withPlugin(
                            "fabric-loom",
                            plugin ->
                                    FabricAdapter.prepare(
                                            project,
                                            facts,
                                            facts.remapped()
                                                    ? REMAP_PLUGIN
                                                    : LOADER_PLUGINS.get("fabric"),
                                            probe));
        else {
            main.getResources().srcDir("src/generated/resources");
            main.getResources().exclude(".cache/**");
        }
        List<String> productNames =
                project.getGradle().getExtensions().getByType(ComponentRegistry.class).products();
        Configuration products = embed(project, productNames);

        verifyRelease(project, facts, modId, main, products, productNames, probe);

        List<InstanceRun> runs = new ArrayList<>();
        for (Instance instance : repository.instances()) {
            InstanceRun run =
                    new InstanceRun(
                            Names.run(instance.id()),
                            instance.isClient(),
                            repository.launchSettings(target, instance),
                            Map.of());
            runs.add(run);
            registerPrepare(project, repository, run, instance);
        }
        List<InstanceRun> validations = new ArrayList<>();
        for (Validation validation : repository.validations()) {
            if (!validation.targets().contains(target)) continue;
            for (Instance role : validation.roles()) {
                InstanceRun run =
                        new InstanceRun(
                                Names.validation(validation.camelName(), role.id()),
                                role.isClient(),
                                repository.launchSettings(validation, target, role),
                                properties(repository, validation, target, role));
                validations.add(run);
                registerPrepare(project, repository, run, role);
            }
        }

        TargetContext context =
                new TargetContext(project, facts, modId, main, probe, products, runs, validations);
        project.afterEvaluate(
                evaluated -> {
                    String plugin =
                            facts.remapped() ? REMAP_PLUGIN : LOADER_PLUGINS.get(facts.loader());
                    if (!evaluated.getPluginManager().hasPlugin(plugin))
                        throw new GradleException(
                                "versions/" + target + "/build.gradle.kts 必须应用 " + plugin);
                    switch (facts.loader()) {
                        case "neoforge" -> ModDevAdapter.neoForge(context);
                        case "forge" -> ModDevAdapter.legacyForge(context);
                        default -> FabricAdapter.configure(context);
                    }
                });
        Formatting.target(project);
    }

    // 主 JAR 从处理后的资源取得许可文件；源码 JAR 只打包源码目录，需要单独加入。
    private static void licenses(Project project, SourceSet main) {
        Project root = project.getRootProject();
        project.getTasks()
                .named(main.getProcessResourcesTaskName(), ProcessResources.class)
                .configure(task -> licenses(task, root));
        project.getTasks()
                .withType(Jar.class)
                .configureEach(
                        jar -> {
                            if (jar.getName().equals(main.getSourcesJarTaskName()))
                                licenses(jar, root);
                        });
    }

    private static void licenses(AbstractCopyTask task, Project root) {
        task.from(root.files(LICENSE_FILES), spec -> spec.into("META-INF"));
        task.from(root.file("licenses"), spec -> spec.into("META-INF/licenses"));
    }

    private static void verifyRelease(
            Project project,
            TargetFacts facts,
            String modId,
            SourceSet main,
            Configuration products,
            List<String> productNames,
            SourceSet probe) {
        Project root = project.getRootProject();
        String jar = "libs/" + modId + "-" + facts.name() + "-" + project.getVersion();
        project.getTasks()
                .register(
                        "verifyRelease",
                        VerifyReleaseJarTask.class,
                        task -> {
                            task.setGroup("verification");
                            task.setDescription("构建发行 JAR，核对其中的元数据、许可文件与 product 组件，并确认没有探针。");
                            task.dependsOn(BasePlugin.ASSEMBLE_TASK_NAME);
                            task.getJar()
                                    .set(
                                            project.getLayout()
                                                    .getBuildDirectory()
                                                    .file(jar + ".jar"));
                            task.getMetadata().set(LOADER_METADATA.get(facts.loader()));
                            task.getProbeId().set(Names.probe(modId));
                            task.getLicenseFiles().from(root.files(LICENSE_FILES));
                            task.getLicenses().set(root.file("licenses"));
                            task.getProducts().from(products);
                            task.getRegisteredProducts().set(productNames);
                            task.getPackagedProducts()
                                    .set(
                                            products.getIncoming()
                                                    .getArtifacts()
                                                    .getResolvedArtifacts()
                                                    .map(TargetConventions::owners));
                            if (probe != null) task.getProbe().from(probe.getOutput());
                            task.getMetadataFiles().set(METADATA_FILES);
                            // 任务在脚本求值之后才被配置，此时已知道脚本是否开启了源码 JAR。
                            if (project.getTasks()
                                    .getNames()
                                    .contains(main.getSourcesJarTaskName()))
                                task.getSourcesJar()
                                        .set(
                                                project.getLayout()
                                                        .getBuildDirectory()
                                                        .file(jar + "-sources.jar"));
                        });
    }

    // 探针是独立的模组 <mod_id>_probe：可以调用产品代码，只在验证运行中加载，不进入任何产品构件。
    private static SourceSet probe(
            Project project,
            TargetFacts facts,
            String modId,
            JavaPluginExtension java,
            SourceSet main,
            Map<String, String> metadata) {
        if (!project.file("src/probe").isDirectory()) return null;
        // 加载器把探针当作独立的模组，产品需要的元数据文件它也需要；1.20.1 Forge 缺少 pack.mcmeta 时会停在警告界面。
        List<String> missing = new ArrayList<>();
        for (String file : METADATA_FILES)
            if ((file.equals(LOADER_METADATA.get(facts.loader()))
                            || project.file("src/main/resources/" + file).isFile())
                    && !project.file("src/probe/resources/" + file).isFile()) missing.add(file);
        if (!missing.isEmpty())
            throw new GradleException(
                    "versions/"
                            + facts.name()
                            + "/src/probe/resources/ 缺少 "
                            + String.join("、", missing)
                            + "；探针是独立的模组 "
                            + Names.probe(modId)
                            + "，需要与产品相同的元数据文件");
        SourceSet probe = java.getSourceSets().create("probe");
        probe.setCompileClasspath(
                probe.getCompileClasspath()
                        .plus(main.getOutput())
                        .plus(main.getCompileClasspath()));
        probe.setRuntimeClasspath(
                probe.getRuntimeClasspath()
                        .plus(main.getOutput())
                        .plus(main.getRuntimeClasspath()));
        expand(project, probe, metadata);
        project.getTasks()
                .named(JavaBasePlugin.CHECK_TASK_NAME)
                .configure(task -> task.dependsOn(probe.getClassesTaskName()));
        return probe;
    }

    private static Map<String, String> properties(
            RepositoryConfiguration repository,
            Validation validation,
            String target,
            Instance role) {
        File directory = validation.directory(repository.getRootDirectory());
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("validation.name", validation.name());
        properties.put("validation.target", target);
        properties.put("validation.role", role.id());
        properties.put("validation.task", path(new File(directory, "task")));
        properties.put("validation.result", path(new File(directory, "result")));
        return properties;
    }

    private static String path(File file) {
        return file.getAbsolutePath().replace(File.separatorChar, '/');
    }

    // 提供了 JAR 的组件；只应用 base 的组件没有可用的变体，Gradle 退回到空的 default 配置。
    private static List<String> owners(Set<ResolvedArtifactResult> artifacts) {
        Set<String> names = new TreeSet<>();
        for (ResolvedArtifactResult artifact : artifacts)
            if (artifact.getVariant().getOwner() instanceof ProjectComponentIdentifier project)
                names.add(project.getProjectName());
        return List.copyOf(names);
    }

    private static Configuration embed(Project project, List<String> components) {
        ObjectFactory objects = project.getObjects();
        Configuration products =
                project.getConfigurations()
                        .create(
                                "productComponents",
                                configuration -> {
                                    configuration.setDescription("合并进模组 JAR 的 product 组件。");
                                    configuration.setCanBeConsumed(false);
                                    configuration.setCanBeResolved(true);
                                    configuration.setTransitive(false);
                                    configuration.attributes(
                                            attributes -> {
                                                attributes.attribute(
                                                        Usage.USAGE_ATTRIBUTE,
                                                        objects.named(
                                                                Usage.class, Usage.JAVA_RUNTIME));
                                                attributes.attribute(
                                                        Category.CATEGORY_ATTRIBUTE,
                                                        objects.named(
                                                                Category.class, Category.LIBRARY));
                                                attributes.attribute(
                                                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                                        objects.named(
                                                                LibraryElements.class,
                                                                LibraryElements.JAR));
                                            });
                                });
        for (String component : components) {
            project.getDependencies()
                    .add(
                            JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                            ComponentRegistry.module(component));
            project.getDependencies().add(products.getName(), ComponentRegistry.module(component));
        }
        ArchiveOperations archives = objects.newInstance(Archives.class).getArchives();
        project.getTasks()
                .named(JavaPlugin.JAR_TASK_NAME, Jar.class)
                .configure(
                        jar -> {
                            jar.from(
                                    products.getElements().map(files -> trees(archives, files)),
                                    spec ->
                                            spec.exclude(
                                                    ReleaseJar.MANIFEST,
                                                    "**/" + ReleaseJar.MODULE_INFO));
                            // Gradle 按压缩包整体跟踪 zipTree，脚本给 jar 加的包含与排除规则不会让它重新打包，
                            // 所以把这些规则也列为输入。
                            jar.getInputs()
                                    .property(
                                            "productPatterns",
                                            project.provider(() -> patterns(jar)));
                        });
        return products;
    }

    private static List<List<String>> patterns(Jar jar) {
        return List.of(
                List.copyOf(new TreeSet<>(jar.getIncludes())),
                List.copyOf(new TreeSet<>(jar.getExcludes())));
    }

    private static List<FileTree> trees(ArchiveOperations archives, Set<FileSystemLocation> files) {
        List<FileTree> trees = new ArrayList<>();
        for (FileSystemLocation file : files) trees.add(archives.zipTree(file.getAsFile()));
        return trees;
    }

    public abstract static class Archives {
        @Inject
        public abstract ArchiveOperations getArchives();
    }

    private static void expand(Project project, SourceSet sourceSet, Map<String, String> metadata) {
        TaskProvider<Sync> templates =
                project.getTasks()
                        .register(
                                sourceSet.getTaskName("generate", "templates"),
                                Sync.class,
                                sync -> {
                                    sync.getInputs().properties(metadata);
                                    sync.from("src/" + sourceSet.getName() + "/templates");
                                    sync.into(
                                            project.getLayout()
                                                    .getBuildDirectory()
                                                    .dir(
                                                            "generated/sources/templates/"
                                                                    + sourceSet.getName()));
                                    sync.expand(metadata);
                                });
        sourceSet.getJava().srcDir(templates);
        project.getTasks()
                .named(sourceSet.getProcessResourcesTaskName(), ProcessResources.class)
                .configure(
                        task -> {
                            task.getInputs().properties(metadata);
                            task.filesMatching(METADATA_FILES, details -> details.expand(metadata));
                        });
    }

    private static void registerPrepare(
            Project project,
            RepositoryConfiguration repository,
            InstanceRun run,
            Instance instance) {
        project.getTasks()
                .register(
                        run.prepareTaskName(),
                        PrepareInstanceTask.class,
                        task -> {
                            task.setDescription("准备 " + run.name() + " 实例。");
                            task.getDirectory().set(run.launch().directory());
                            task.getServer().set(instance.isServer());
                            task.getEulaAccepted().set(repository.isEulaAccepted());
                            task.getMods().set(run.launch().mods());
                            task.getConfiguration().set(repository);
                        });
        project.getTasks()
                .withType(JavaExec.class)
                .matching(task -> task.getName().equals(run.taskName()))
                .configureEach(
                        task -> {
                            task.dependsOn(run.prepareTaskName());
                            if (instance.isServer()) task.setStandardInput(System.in);
                        });
    }
}
