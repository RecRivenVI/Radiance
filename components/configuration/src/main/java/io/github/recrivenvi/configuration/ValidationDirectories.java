package io.github.recrivenvi.configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

// 配置缓存每次构建都重新计算值来源，新增或删除验证目录时配置随之失效。
public abstract class ValidationDirectories
        implements ValueSource<List<String>, ValidationDirectories.Parameters> {
    public interface Parameters extends ValueSourceParameters {
        Property<File> getDirectory();
    }

    @Override
    public List<String> obtain() {
        File[] directories = getParameters().getDirectory().get().listFiles(File::isDirectory);
        List<String> names = new ArrayList<>();
        if (directories != null)
            for (File directory : directories)
                if (new File(directory, Validation.FILE).isFile()) names.add(directory.getName());
        names.sort(null);
        return names;
    }
}
