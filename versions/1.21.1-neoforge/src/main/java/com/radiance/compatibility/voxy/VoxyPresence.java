package com.radiance.compatibility.voxy;

/** Does not link Voxy types when the optional mod is absent. */
public final class VoxyPresence {
    public static final boolean PRESENT =
            VoxyPresence.class
                            .getClassLoader()
                            .getResource("me/cortex/voxy/client/VoxyClient.class")
                    != null;

    private VoxyPresence() {}
}
