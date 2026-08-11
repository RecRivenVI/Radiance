package com.radiance.audit;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Optional probe contributors; registration never enables an experiment. */
public final class ProbeSources {
    private static final CopyOnWriteArrayList<Consumer<Minecraft>> MENU =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<Minecraft>> WORLD =
            new CopyOnWriteArrayList<>();

    private ProbeSources() {}

    public static void register(Consumer<Minecraft> menu, Consumer<Minecraft> world) {
        MENU.add(menu);
        WORLD.add(world);
    }

    public static void menu(Minecraft mc) {
        MENU.forEach(c -> c.accept(mc));
    }

    public static void world(Minecraft mc) {
        WORLD.forEach(c -> c.accept(mc));
    }
}
