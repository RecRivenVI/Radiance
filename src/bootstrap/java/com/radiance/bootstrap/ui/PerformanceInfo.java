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

    PerformanceInfo() {
        osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        memoryBean = ManagementFactory.getMemoryMXBean();
    }

    LoadingScene.PerformanceSnapshot update() {
        final MemoryUsage heapusage = memoryBean.getHeapMemoryUsage();
        float memory = (float) heapusage.getUsed() / heapusage.getMax();
        var cpuLoad = osBean.getProcessCpuLoad();
        String cpuText;
        if (cpuLoad == -1) {
            cpuText = String.format("*CPU: %.1f%%", osBean.getCpuLoad() * 100f);
        } else {
            cpuText = String.format("CPU: %.1f%%", cpuLoad * 100f);
        }
        String text = String.format("Memory: %d/%d MB (%.1f%%)  %s", heapusage.getUsed() >> 20, heapusage.getMax() >> 20, memory * 100.0, cpuText);
        return new LoadingScene.PerformanceSnapshot(memory, text);
    }
}
