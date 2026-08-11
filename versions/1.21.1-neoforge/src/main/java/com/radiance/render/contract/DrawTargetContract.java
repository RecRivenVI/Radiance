package com.radiance.render.contract;

/**
 * Target decisions use captured facts; querying a renderer or granting permission belongs to
 * adapters.
 */
public final class DrawTargetContract {
    private DrawTargetContract() {}

    public enum GuiRole {
        FOREGROUND(0),
        BACKGROUND_EFFECT(1),
        WORLD_REPLACEMENT(2);

        private final int nativeId;

        GuiRole(int nativeId) {
            this.nativeId = nativeId;
        }

        public int nativeId() {
            return nativeId;
        }
    }

    public enum ScopeKind {
        OUTSIDE,
        WORLD_STAGE,
        WORLD_RASTER,
        GUI,
        CAMERA_OVERLAY,
        DIMENSION_EFFECT,
        UNSUPPORTED_OPENGL
    }

    public enum BufferDrawRoute {
        VULKAN_UI,
        VULKAN_CUSTOM_TARGET,
        VULKAN_WORLD_RASTER,
        REJECT_WORLD_MESH,
        REJECT_UNSCOPED
    }

    public record Scope(ScopeKind kind, String label) {
        public Scope {
            kind = kind == null ? ScopeKind.OUTSIDE : kind;
            label = label == null || label.isBlank() ? kind.name() : label;
        }
    }

    public record BufferDrawDecision(BufferDrawRoute route, Scope scope, String reason) {
        public java.util.Optional<TakeoverSubmission.Domain> domain() {
            return sourceDomain(scope.kind());
        }
    }

    public static java.util.Optional<TakeoverSubmission.Domain> sourceDomain(ScopeKind kind) {
        return switch (kind) {
            case WORLD_STAGE, WORLD_RASTER, DIMENSION_EFFECT ->
                    java.util.Optional.of(TakeoverSubmission.Domain.WORLD);
            case GUI -> java.util.Optional.of(TakeoverSubmission.Domain.GUI);
            case CAMERA_OVERLAY -> java.util.Optional.of(TakeoverSubmission.Domain.SCREEN);
            case OUTSIDE, UNSUPPORTED_OPENGL -> java.util.Optional.empty();
        };
    }

    public static BufferDrawDecision classify(Scope scope, int framebuffer) {
        return switch (scope.kind()) {
            case WORLD_RASTER ->
                    new BufferDrawDecision(
                            BufferDrawRoute.VULKAN_WORLD_RASTER,
                            scope,
                            "explicit world effect after native color and depth resolve");
            case GUI, CAMERA_OVERLAY ->
                    new BufferDrawDecision(BufferDrawRoute.VULKAN_UI, scope, "explicit UI scope");
            case WORLD_STAGE, DIMENSION_EFFECT ->
                    framebuffer != 0
                            ? new BufferDrawDecision(
                                    BufferDrawRoute.VULKAN_CUSTOM_TARGET,
                                    scope,
                                    "explicit Vulkan-backed framebuffer " + framebuffer)
                            : new BufferDrawDecision(
                                    BufferDrawRoute.REJECT_WORLD_MESH,
                                    scope,
                                    "raw BufferUploader draw has no RenderType/world material metadata");
            case UNSUPPORTED_OPENGL ->
                    new BufferDrawDecision(
                            BufferDrawRoute.REJECT_UNSCOPED,
                            scope,
                            "OpenGL scope is unsupported under GLFW_NO_API");
            case OUTSIDE ->
                    new BufferDrawDecision(
                            BufferDrawRoute.REJECT_UNSCOPED,
                            scope,
                            "draw is outside an explicit GUI, camera, or world stage scope");
        };
    }

    public static boolean rasterPreview(ScopeKind kind, boolean explicitPreview) {
        return switch (kind) {
            case GUI, CAMERA_OVERLAY, WORLD_RASTER -> true;
            case WORLD_STAGE, DIMENSION_EFFECT -> false;
            default -> explicitPreview;
        };
    }
}
