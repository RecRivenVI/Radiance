package io.github.recrivenvi.compliance;

import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "只输出建议词，不生成文件")
public abstract class PrintVocabularyTask extends DefaultTask {
    @Input
    public abstract MapProperty<String, List<String>> getVocabulary();

    @TaskAction
    public void print() {
        Vocabulary vocabulary = new Vocabulary(getVocabulary().get());
        Vocabulary.BUILT_IN.forEach(
                (location, words) -> {
                    String additions =
                            vocabulary.additions(location).isEmpty()
                                    ? ""
                                    : "；项目补充：" + String.join(", ", vocabulary.additions(location));
                    getLogger()
                            .lifecycle("{}: {}{}", location, String.join(", ", words), additions);
                });
    }
}
