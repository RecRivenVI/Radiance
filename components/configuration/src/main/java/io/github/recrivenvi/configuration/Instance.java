package io.github.recrivenvi.configuration;

import java.io.Serializable;

public record Instance(String id, Variant base) implements Serializable {
    public boolean isClient() {
        return base.isClient();
    }

    public boolean isServer() {
        return base == Variant.SERVER;
    }

    boolean builtIn() {
        return id.equals(base.id());
    }
}
