package com.radiance.client.proxy.world;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Coalesces packet-local light passes into the existing pre-render light publication. */
public final class ClientLightUpdates {
    // Only the client thread enters this scope; the logical server keeps its own light passes.
    private static int packetDepth;

    private ClientLightUpdates() {}

    public static void receive(Runnable packet) {
        packetDepth++;
        try {
            packet.run();
        } finally {
            packetDepth--;
        }
    }

    static boolean receiving() {
        return packetDepth != 0;
    }

    public static boolean defer(LevelLightEngine engine) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.isSameThread()
                && minecraft.level != null
                && minecraft.level.getLightEngine() == engine
                && receiving();
    }
}
