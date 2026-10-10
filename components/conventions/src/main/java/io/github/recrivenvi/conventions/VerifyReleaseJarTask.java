package io.github.recrivenvi.conventions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "核对已构建的发行 JAR，不生成输出")
public abstract class VerifyReleaseJarTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getJar();

    // 开启源码 JAR 时核对其中的许可文件。
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSourcesJar();

    @Input
    public abstract Property<String> getMetadata();

    @Input
    public abstract Property<String> getProbeId();

    // 根目录的 LICENSE、NOTICE 等文件，存在的那些应出现在 META-INF/ 中。
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getLicenseFiles();

    // licenses/ 中的第三方许可原文，应出现在 META-INF/licenses/ 中；目录可以不存在。
    @Internal
    public abstract DirectoryProperty getLicenses();

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getProducts();

    // components {} 中登记的 product 组件，与其中实际提供了 JAR 的组件。
    @Input
    public abstract ListProperty<String> getRegisteredProducts();

    @Input
    public abstract ListProperty<String> getPackagedProducts();

    // probe 源码集的输出目录；除元数据文件名外，其中的文件不应出现在发行 JAR 中。
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getProbe();

    // 加载器元数据的文件名；探针与产品都有这些文件，由元数据的内容区分。
    @Input
    public abstract ListProperty<String> getMetadataFiles();

    @TaskAction
    public void verify() throws IOException {
        List<String> licenseEntries = new ArrayList<>();
        for (File file : getLicenseFiles())
            if (file.isFile()) licenseEntries.add("META-INF/" + file.getName());
        Path licenses = getLicenses().get().getAsFile().toPath();
        for (Path file : files(licenses))
            licenseEntries.add(
                    "META-INF/licenses/" + licenses.relativize(file).toString().replace('\\', '/'));
        List<String> required = new ArrayList<>(licenseEntries);
        for (File product : getProducts())
            try (ZipFile zip = new ZipFile(product)) {
                zip.stream()
                        .map(ZipEntry::getName)
                        .filter(ReleaseJar::merged)
                        .forEach(required::add);
            }
        List<String> forbidden = new ArrayList<>();
        for (File directory : getProbe()) {
            Path root = directory.toPath();
            for (Path file : files(root)) {
                String entry = root.relativize(file).toString().replace('\\', '/');
                if (!getMetadataFiles().get().contains(entry)) forbidden.add(entry);
            }
        }
        File jar = getJar().get().getAsFile();
        List<String> problems =
                ReleaseJar.unpackaged(getRegisteredProducts().get(), getPackagedProducts().get());
        problems.addAll(
                ReleaseJar.problems(
                        jar.toPath(),
                        getMetadata().get(),
                        getProbeId().get(),
                        required,
                        forbidden));
        if (getSourcesJar().isPresent()) {
            File sources = getSourcesJar().get().getAsFile();
            for (String problem :
                    ReleaseJar.problems(
                            sources.toPath(), null, getProbeId().get(), licenseEntries, List.of()))
                problems.add(sources.getName() + " " + problem);
        }
        if (!problems.isEmpty())
            throw new GradleException(jar.getName() + "：\n" + String.join("\n", problems));
        getLogger().lifecycle("{}：元数据、{} 个必需条目与探针排除均符合", jar.getName(), required.size());
    }

    private static List<Path> files(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }
}
