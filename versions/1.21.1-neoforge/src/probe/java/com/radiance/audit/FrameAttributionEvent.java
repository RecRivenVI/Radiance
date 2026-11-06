package com.radiance.audit;

import jdk.jfr.*;

/** Connects one measured render interval with JFR samples, pauses and class loading. */
@Name("radiance.RenderFrame")
@Label("Radiance render frame")
@Category("Radiance")
@StackTrace(false)
final class FrameAttributionEvent extends Event {
    long frameToken;
    String mode;

    @Timespan(Timespan.NANOSECONDS)
    long measuredWallNs;

    @Timespan(Timespan.NANOSECONDS)
    long renderThreadCpuNs;
}
