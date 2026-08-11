package com.radiance.client.shader;

public record ShaderField(String name, String fieldName, Kind kind, int componentCount,
                          int offset, int size, int samplerSlot, int arrayLength, boolean isArray) {

    public ShaderField(String name, String fieldName, Kind kind, int componentCount,
        int offset, int size, int samplerSlot) {
        this(name, fieldName, kind, componentCount, offset, size, samplerSlot, 1, false);
    }

    public ShaderField(String name, String fieldName, Kind kind, int componentCount,
        int offset, int size, int samplerSlot, int arrayLength) {
        this(name, fieldName, kind, componentCount, offset, size, samplerSlot, arrayLength,
            arrayLength > 1);
    }

    public ShaderField {
        if (arrayLength < 1) {
            throw new IllegalArgumentException("Shader field array length must be positive");
        }
        if (!isArray && arrayLength != 1) {
            throw new IllegalArgumentException("Scalar shader field cannot have multiple elements");
        }
    }

    public enum Kind {
        INT,
        UINT,
        BOOL,
        FLOAT,
        MATRIX,
        SAMPLER
    }

    public boolean isSampler() {
        return kind == Kind.SAMPLER;
    }

    public String glslType() {
        return switch (kind) {
            case INT -> switch (componentCount) {
                case 1 -> "int";
                case 2 -> "ivec2";
                case 3 -> "ivec3";
                case 4 -> "ivec4";
                default -> throw new IllegalStateException(
                    "Unsupported int vector size: " + componentCount);
            };
            case UINT -> switch (componentCount) {
                case 1 -> "uint";
                case 2 -> "uvec2";
                case 3 -> "uvec3";
                case 4 -> "uvec4";
                default -> throw new IllegalStateException(
                    "Unsupported uint vector size: " + componentCount);
            };
            case BOOL -> switch (componentCount) {
                case 1 -> "bool";
                case 2 -> "bvec2";
                case 3 -> "bvec3";
                case 4 -> "bvec4";
                default -> throw new IllegalStateException(
                    "Unsupported bool vector size: " + componentCount);
            };
            case FLOAT -> switch (componentCount) {
                case 1 -> "float";
                case 2 -> "vec2";
                case 3 -> "vec3";
                case 4 -> "vec4";
                default -> throw new IllegalStateException(
                    "Unsupported float vector size: " + componentCount);
            };
            case MATRIX -> switch (componentCount) {
                case 2 -> "mat2";
                case 3 -> "mat3";
                case 4 -> "mat4";
                default -> throw new IllegalStateException(
                    "Unsupported matrix size: " + componentCount);
            };
            case SAMPLER -> "uint";
        };
    }
}
