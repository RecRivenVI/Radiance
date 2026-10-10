package io.github.recrivenvi.conventions;

import io.github.recrivenvi.configuration.RepositoryConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "就地准备长期保留的运行目录")
public abstract class PrepareInstanceTask extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getDirectory();

    @Input
    public abstract Property<Boolean> getServer();

    @Input
    public abstract Property<Boolean> getEulaAccepted();

    @Input
    public abstract ListProperty<String> getMods();

    @Internal
    public abstract Property<RepositoryConfiguration> getConfiguration();

    @TaskAction
    public void prepare() throws IOException {
        Path directory = getDirectory().get().getAsFile().toPath();
        Files.createDirectories(directory);
        mods(directory);
        if (!getServer().get()) {
            // 新实例首次启动时，无障碍引导会挡在标题界面前等待点击；之后仍可在游戏设置中打开。
            Path options = directory.resolve("options.txt");
            if (Files.notExists(options))
                Files.writeString(options, "onboardAccessibility:false\n");
            return;
        }
        Path properties = directory.resolve("server.properties");
        // 开发用的玩家是离线账号；26.3 起新服务端默认开启白名单，会拒绝 client-multiplayer 实例。
        if (Files.notExists(properties))
            Files.writeString(properties, "online-mode=false\nwhite-list=false\n");
        if (getEulaAccepted().get())
            Files.writeString(directory.resolve("eula.txt"), "eula=true\n");
    }

    private void mods(Path directory) throws IOException {
        Map<String, Path> inputs = new LinkedHashMap<>();
        for (String input : getMods().get())
            inputs.put(input, getConfiguration().get().input(input).toPath());
        for (String name : InstanceMods.sync(directory, inputs))
            getLogger().warn("mods/{} 在放入后被改动过，保留它，不再随实例设置 mods 更新或删除", name);
    }
}
