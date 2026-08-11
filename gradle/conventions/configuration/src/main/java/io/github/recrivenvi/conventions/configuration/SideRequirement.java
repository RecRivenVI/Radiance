package io.github.recrivenvi.conventions.configuration;

// 产品模组在客户端或服务端的安装要求：必装、无效（装了也不起作用）或可选。
public enum SideRequirement {
    REQUIRED("true"),
    UNUSED("false"),
    OPTIONAL("optional");

    private final String id;

    SideRequirement(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    static SideRequirement of(String id) {
        for (SideRequirement requirement : values())
            if (requirement.id.equals(id)) return requirement;
        return null;
    }
}
