package io.github.recrivenvi.compliance;

public enum Rule {
    S01("S-01", Level.FAIL, "AGENTS.md 声明的规范版本与检查器一致"),
    S02("S-02", Level.FAIL, "AGENTS.md 中可检查的规则与检查器一一对应"),
    S03("S-03", Level.FAIL, "AGENTS.md 的建议词表与检查器内置词表一致"),
    S04("S-04", Level.FAIL, "CLAUDE.md 只导入两份规范"),
    S05("S-05", Level.FAIL, "项目规范与项目事实、Target 事实一致"),
    S06("S-06", Level.FAIL, "模板文件与当前规范版本一致"),
    S07("S-07", Level.FAIL, "两份规范合计不超过 32 KiB"),
    G06("G-06", Level.FAIL, "被忽略的文件不进入版本控制"),
    G09("G-09", Level.FAIL, "仓库不跟踪构建二进制"),
    L01("L-01", Level.FAIL, "根目录只有允许的条目"),
    L02("L-02", Level.FAIL, "versions/ 中只有已登记的 Target"),
    L03("L-03", Level.FAIL, "Target 目录只有允许的条目与源码集"),
    L04("L-04", Level.FAIL, "组件按职责命名"),
    L05("L-05", Level.FAIL, ".gitignore 包含必需的规则"),
    L07("L-07", Level.FAIL, "Java 源码位于 mod_group 对应的包"),
    L08("L-08", Level.FAIL, "组件是被包含进构建的 Gradle 构建"),
    L09("L-09", Level.FAIL, "第三方许可原文按来源与许可命名"),
    L10("L-10", Level.FAIL, ".github 中只有已知的 GitHub 文件"),
    T02("T-02", Level.FAIL, "Target 构建脚本不写版本号"),
    T07("T-07", Level.WARN, "多个 Target 的产品源码中相同且不引用 Minecraft 的 Java 源码移入组件"),
    P01("P-01", Level.FAIL, "元数据占位符只写在双引号字符串内"),
    P03("P-03", Level.FAIL, "每个 Target 只有本加载器的元数据文件"),
    P04("P-04", Level.FAIL, "加载器元数据从占位符取值"),
    P05("P-05", Level.FAIL, "资源路径符合资源位置规则"),
    P06("P-06", Level.WARN, "派生项目不沿用模板的模组 ID 与 Java 包"),
    P07("P-07", Level.WARN, "模板名称只出现在模板文件、NOTICE 与 licenses/ 中"),
    I04("I-04", Level.FAIL, "instances/ 中只有已登记的 Target 与实例"),
    V01("V-01", Level.FAIL, "验证目录符合命名规则"),
    V02("V-02", Level.WARN, "验证类型取自建议词"),
    V03("V-03", Level.FAIL, "验证目录只有允许的条目"),
    V04("V-04", Level.FAIL, "验证目录中没有源码、脚本或可执行文件"),
    V08("V-08", Level.FAIL, "validation.toml 写明工具组件与 Target"),
    V09("V-09", Level.FAIL, "validation.md 写明目标、命令与通过标准"),
    D01("D-01", Level.FAIL, "documents/ 中只有七个分类"),
    D02("D-02", Level.FAIL, "文档文件名符合命名规则"),
    D03("D-03", Level.WARN, "文档类型取自建议词"),
    D05("D-05", Level.FAIL, "Markdown 相对链接有效"),
    D06("D-06", Level.FAIL, "结构化文档包含固定小节"),
    D07("D-07", Level.WARN, "玩家文档不出现仓库路径与构建命令"),
    D08("D-08", Level.FAIL, "文档图片按编号命名并按顺序引用"),
    D12("D-12", Level.FAIL, "README 互相链接并列出已登记的 Target"),
    D13("D-13", Level.FAIL, "译文有原文且小节数量相同"),
    D14("D-14", Level.FAIL, "更新日志遵循 Keep a Changelog"),
    D15("D-15", Level.FAIL, "玩家文档开头写出支持的 Minecraft 版本"),
    D16("D-16", Level.FAIL, "研究记录开头写出调查日期"),
    C01("C-01", Level.FAIL, "文本文件为不带 BOM 的 UTF-8"),
    C02("C-02", Level.FAIL, "文本文件使用 LF，批处理文件使用 CRLF"),
    C03("C-03", Level.FAIL, "文本文件以一个换行结束"),
    C04("C-04", Level.FAIL, "文本文件没有行尾空白与制表符"),
    C06("C-06", Level.FAIL, "Gradle Wrapper 已校验且可执行");

    private final String id;
    private final Level level;
    private final String title;

    Rule(String id, Level level, String title) {
        this.id = id;
        this.level = level;
        this.title = title;
    }

    public String id() {
        return id;
    }

    public Level level() {
        return level;
    }

    public String title() {
        return title;
    }

    public enum Level {
        FAIL("失败"),
        WARN("警告");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
