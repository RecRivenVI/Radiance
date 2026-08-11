package com.radiance.client.shader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit multi-binding vertex layout for fixed external shader consumers. */
public record CustomVertexLayout(List<Binding> bindings, List<Attribute> attributes) {

    public static final int BYTE = 0;
    public static final int UNSIGNED_BYTE = 1;
    public static final int SHORT = 2;
    public static final int UNSIGNED_SHORT = 3;
    public static final int INT = 4;
    public static final int UNSIGNED_INT = 5;
    public static final int FLOAT = 6;

    public static final CustomVertexLayout SABLE_FANCY = new CustomVertexLayout(
        List.of(new Binding(0, 8, false), new Binding(1, 8, true)),
        List.of(
            new Attribute(0, 0, 3, BYTE, false, false, 0),
            new Attribute(1, 0, 3, BYTE, false, false, 4),
            new Attribute(2, 1, 2, UNSIGNED_INT, false, true, 0)));

    public CustomVertexLayout {
        bindings = List.copyOf(bindings);
        attributes = List.copyOf(attributes);
        if (bindings.isEmpty() || attributes.isEmpty()) {
            throw new IllegalArgumentException("Custom vertex layout cannot be empty");
        }
        Set<Integer> bindingIds = new HashSet<>();
        for (Binding binding : bindings) {
            if (!bindingIds.add(binding.binding())) {
                throw new IllegalArgumentException("Duplicate vertex binding " + binding.binding());
            }
        }
        Set<Integer> locations = new HashSet<>();
        for (Attribute attribute : attributes) {
            if (!bindingIds.contains(attribute.binding())) {
                throw new IllegalArgumentException("Unknown attribute binding " + attribute.binding());
            }
            if (!locations.add(attribute.location())) {
                throw new IllegalArgumentException("Duplicate attribute location " + attribute.location());
            }
        }
    }

    public int[] bindingData() {
        int[] data = new int[bindings.size() * 3];
        for (int i = 0; i < bindings.size(); i++) {
            Binding binding = bindings.get(i);
            data[i * 3] = binding.binding();
            data[i * 3 + 1] = binding.stride();
            data[i * 3 + 2] = binding.perInstance() ? 1 : 0;
        }
        return data;
    }

    public int[] attributeData() {
        int[] data = new int[attributes.size() * 7];
        for (int i = 0; i < attributes.size(); i++) {
            Attribute attribute = attributes.get(i);
            data[i * 7] = attribute.location();
            data[i * 7 + 1] = attribute.binding();
            data[i * 7 + 2] = attribute.componentCount();
            data[i * 7 + 3] = attribute.componentType();
            data[i * 7 + 4] = attribute.normalized() ? 1 : 0;
            data[i * 7 + 5] = attribute.integer() ? 1 : 0;
            data[i * 7 + 6] = attribute.offset();
        }
        return data;
    }

    public record Binding(int binding, int stride, boolean perInstance) {
        public Binding {
            if (binding < 0 || stride <= 0) {
                throw new IllegalArgumentException("Invalid custom vertex binding");
            }
        }
    }

    public record Attribute(int location, int binding, int componentCount, int componentType,
                            boolean normalized, boolean integer, int offset) {
        public Attribute {
            if (location < 0 || binding < 0 || componentCount < 1 || componentCount > 4
                || componentType < BYTE || componentType > FLOAT || offset < 0) {
                throw new IllegalArgumentException("Invalid custom vertex attribute");
            }
            if (integer && normalized) {
                throw new IllegalArgumentException("Integer attributes cannot be normalized");
            }
        }
    }
}
