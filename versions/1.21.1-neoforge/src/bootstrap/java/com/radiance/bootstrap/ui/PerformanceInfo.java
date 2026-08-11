/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.radiance.bootstrap.ui;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

final class PerformanceInfo {
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private long previousTime;
    private long previousCpuTime;

    PerformanceInfo() {
        osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        memoryBean = ManagementFactory.getMemoryMXBean();
    }

    LoadingScene.PerformanceSnapshot update() {
        final MemoryUsage heapusage = memoryBean.getHeapMemoryUsage();
        float memory = (float) heapusage.getUsed() / heapusage.getMax();
        // Windows' load counters can block the loading render thread. Process time
        // is a cheap monotonic counter; sample its delta over the existing UI interval.
        long now = System.nanoTime();
        long cpuTime = osBean.getProcessCpuTime();
        double cpuLoad =
                previousTime != 0 && cpuTime >= previousCpuTime && previousCpuTime >= 0
                        ? (double) (cpuTime - previousCpuTime)
                                / (now - previousTime)
                                / osBean.getAvailableProcessors()
                        : -1;
        previousTime = now;
        previousCpuTime = cpuTime;
        String cpuText =
                cpuLoad < 0
                        ? "Process CPU: --"
                        : String.format("Process CPU: %.1f%%", cpuLoad * 100);
        String text =
                String.format(
                        "Memory: %d/%d MB (%.1f%%)  %s",
                        heapusage.getUsed() >> 20,
                        heapusage.getMax() >> 20,
                        memory * 100.0,
                        cpuText);
        return new LoadingScene.PerformanceSnapshot(memory, text);
    }
}
