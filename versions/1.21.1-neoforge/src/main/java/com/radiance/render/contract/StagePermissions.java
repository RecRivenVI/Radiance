package com.radiance.render.contract;

/** Explicit capability registrations. A listener's implementation class grants no permission. */
public final class StagePermissions {
    public enum Producer {
        VEIL_POST_PROCESS,
        SIMULATED_END_SEA
    }

    public enum Target {
        DEFAULT_WORLD,
        OFFSCREEN,
        GUI
    }

    public static final class RasterPermit {
        private final Producer producer;
        private final String reason;

        private RasterPermit(Producer producer, String reason) {
            this.producer = producer;
            this.reason = reason;
        }

        public Producer producer() {
            return producer;
        }

        public String reason() {
            return reason;
        }
    }

    private StagePermissions() {}

    public static RasterPermit resolve(Producer producer, Target target) {
        if (producer == null || target != Target.DEFAULT_WORLD) return null;
        return switch (producer) {
            case VEIL_POST_PROCESS ->
                    new RasterPermit(
                            producer,
                            "Explicit Veil post-processing consumes resolved world color/depth; excludes fixed-buffer geometry");
            case SIMULATED_END_SEA ->
                    new RasterPermit(
                            producer,
                            "Existing explicit non-line world effect retained in stage A; transport equivalence remains undecided");
        };
    }
}
