package io.github.recrivenvi.configuration;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "报告构建配置阶段已校验的配置")
public abstract class VerifyConfigurationTask extends DefaultTask {
    @Internal
    public abstract Property<RepositoryConfiguration> getConfiguration();

    @TaskAction
    public void verify() {
        RepositoryConfiguration configuration = getConfiguration().get();
        getLogger()
                .lifecycle(
                        "配置校验通过：{} 个 Target，{} 项验证，EULA {}",
                        configuration.getTargetNames().size(),
                        configuration.validations().size(),
                        configuration.isEulaAccepted() ? "已接受" : "未接受");
    }
}
