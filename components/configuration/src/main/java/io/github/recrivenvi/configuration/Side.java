package io.github.recrivenvi.configuration;

public enum Side {
    BOTH("both"),
    CLIENT("client"),
    SERVER("server");

    private final String id;

    Side(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Side of(String id) {
        for (Side side : values()) if (side.id.equals(id)) return side;
        throw new IllegalArgumentException("未知的运行端：" + id + "；应为 both、client 或 server");
    }
}
