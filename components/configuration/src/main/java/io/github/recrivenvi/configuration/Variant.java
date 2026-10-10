package io.github.recrivenvi.configuration;

public enum Variant {
    CLIENT("client", true),
    CLIENT_MULTIPLAYER("client-multiplayer", true),
    SERVER("server", false);

    private final String id;
    private final boolean client;

    Variant(String id, boolean client) {
        this.id = id;
        this.client = client;
    }

    public String id() {
        return id;
    }

    public boolean isClient() {
        return client;
    }
}
