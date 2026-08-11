package com.radiance.api.render;

import com.radiance.api.render.internal.ScreenEffectsNative;
import com.radiance.render.contract.RenderAbi;
import java.util.List;
import java.util.Objects;

/** Ordered full-screen Vulkan effects. Input 0 is the current image; targets start at 1. */
public final class ScreenEffects {
    private ScreenEffects() {}

    public sealed interface Kernel permits Program, SpirvProgram {
        int nativeId(long generation);
    }

    public enum Program implements Kernel {
        CREEPER(RenderAbi.EFFECT_CREEPER),
        SPIDER(RenderAbi.EFFECT_SPIDER),
        INVERT(RenderAbi.EFFECT_INVERT),
        BLUR(RenderAbi.EFFECT_BLUR),
        SPIDER_H15(RenderAbi.EFFECT_SPIDER_H15),
        SPIDER_V15(RenderAbi.EFFECT_SPIDER_V15),
        SPIDER_H7(RenderAbi.EFFECT_SPIDER_H7),
        SPIDER_V7(RenderAbi.EFFECT_SPIDER_V7);
        private final int id;

        Program(int id) {
            this.id = id;
        }

        public int nativeId(long generation) {
            return id;
        }
    }

    /**
     * A compiled Vulkan fragment program, not OpenGL GLSL translation. The ABI accepts uv at
     * location 0, RGBA at output 0, 1..3 sampler2D inputs at set 0 bindings 1/2/4, and optional
     * 16-byte fragment push constants. Native reflects and rejects other interfaces/capabilities.
     * Re-registers for a new native module; close before replacing a source-owned definition.
     */
    public static final class SpirvProgram implements Kernel, AutoCloseable {
        private final String key;
        private final byte[] spirv;
        private final int inputs;
        private long generation;
        private int handle;
        private boolean closed;

        public SpirvProgram(String key, byte[] spirv, int inputs) {
            if (key == null
                    || key.isBlank()
                    || inputs < 1
                    || inputs > 3
                    || spirv.length < 20
                    || spirv.length % 4 != 0)
                throw new IllegalArgumentException("Invalid compiled screen program");
            this.key = key;
            this.spirv = spirv.clone();
            this.inputs = inputs;
        }

        public int nativeId(long current) {
            if (closed) throw new IllegalStateException("Screen program is closed");
            if (generation != current) {
                handle = ScreenEffectsNative.registerProgram(key, spirv, inputs);
                generation = current;
            }
            return handle;
        }

        public void close() {
            if (!closed && generation != 0) ScreenEffectsNative.releaseProgram(generation, handle);
            closed = true;
        }
    }

    public enum Role {
        WORLD_POST,
        GUI_POST,
        FULLSCREEN_MENU_BLUR
    }

    public record Size(int divisorX, int divisorY) {
        public Size {
            if (divisorX < 1 || divisorY < 1)
                throw new IllegalArgumentException("Invalid effect size");
        }

        public static Size full() {
            return new Size(1, 1);
        }
    }

    public record Pass(
            Kernel program,
            int output,
            List<Integer> inputs,
            RenderResources.Sampling sampling,
            float x,
            float y,
            float z,
            float w) {
        public Pass {
            Objects.requireNonNull(program);
            Objects.requireNonNull(sampling);
            inputs = List.copyOf(inputs);
            if (output < 1
                    || inputs.isEmpty()
                    || inputs.size() > 3
                    || inputs.stream().anyMatch(i -> i < 0)
                    || !Float.isFinite(x)
                    || !Float.isFinite(y)
                    || !Float.isFinite(z)
                    || !Float.isFinite(w))
                throw new IllegalArgumentException("Invalid effect pass");
        }

        public Pass(Kernel program, int output, List<Integer> inputs) {
            this(program, output, inputs, RenderResources.Sampling.LINEAR, 0, 0, 0, 0);
        }
    }

    public record Plan(
            Role role,
            List<Size> targets,
            List<Pass> passes,
            int output,
            RenderResources.Sampling outputSampling) {
        public Plan {
            Objects.requireNonNull(role);
            Objects.requireNonNull(outputSampling);
            targets = List.copyOf(targets);
            passes = List.copyOf(passes);
            if (targets.isEmpty() || targets.size() > 8 || passes.isEmpty() || passes.size() > 32)
                throw new IllegalArgumentException("Effect graph exceeds pass/target budget");
            boolean[] ready = new boolean[targets.size() + 1];
            ready[0] = true;
            for (Pass pass : passes) {
                if (pass.output() >= ready.length)
                    throw new IllegalArgumentException("Unknown effect output");
                for (int input : pass.inputs())
                    if (input >= ready.length || input == pass.output() || !ready[input])
                        throw new IllegalArgumentException(
                                "Effect input has no earlier producer or aliases output");
                ready[pass.output()] = true;
            }
            if (output < 1 || output >= ready.length || !ready[output])
                throw new IllegalArgumentException("Missing effect result");
        }

        public Plan(Role role, List<Size> targets, List<Pass> passes, int output) {
            this(role, targets, passes, output, RenderResources.Sampling.LINEAR);
        }
    }

    public static boolean submit(Plan plan) {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        long generation = ScreenEffectsNative.generation();
        if (generation == 0) return false;
        int[] description = new int[6 + plan.targets().size() * 2 + plan.passes().size() * 8];
        description[0] = RenderAbi.EFFECT_PLAN_VERSION;
        description[1] = plan.role().ordinal();
        description[2] = plan.targets().size();
        description[3] = plan.passes().size();
        description[4] = plan.output();
        description[5] = plan.outputSampling().ordinal();
        int at = 6;
        for (Size size : plan.targets()) {
            description[at++] = size.divisorX();
            description[at++] = size.divisorY();
        }
        float[] parameters = new float[plan.passes().size() * 4];
        int pi = 0;
        for (Pass pass : plan.passes()) {
            description[at++] = pass.program().nativeId(generation);
            description[at++] = pass.output();
            description[at++] = pass.inputs().size();
            description[at++] = pass.sampling().ordinal();
            for (int i = 0; i < 3; i++)
                description[at++] = i < pass.inputs().size() ? pass.inputs().get(i) : 0;
            description[at++] = 0;
            parameters[pi++] = pass.x();
            parameters[pi++] = pass.y();
            parameters[pi++] = pass.z();
            parameters[pi++] = pass.w();
        }
        return ScreenEffectsNative.submit(generation, description, parameters);
    }

    private static final Plan CREEPER = single(Program.CREEPER);
    private static final Plan INVERT = single(Program.INVERT);
    private static final Plan SPIDER =
            new Plan(
                    Role.WORLD_POST,
                    List.of(Size.full(), Size.full(), Size.full()),
                    List.of(
                            new Pass(Program.SPIDER_H15, 1, List.of(0)),
                            new Pass(Program.SPIDER_V15, 2, List.of(1)),
                            new Pass(Program.SPIDER_H7, 1, List.of(0)),
                            new Pass(Program.SPIDER_V7, 3, List.of(1)),
                            new Pass(Program.SPIDER, 1, List.of(0, 2, 3))),
                    1);

    private static Plan single(Program program) {
        return new Plan(
                Role.WORLD_POST,
                List.of(Size.full()),
                List.of(new Pass(program, 1, List.of(0))),
                1);
    }

    public static boolean worldPost(Program program) {
        return submit(
                switch (program) {
                    case CREEPER -> CREEPER;
                    case SPIDER -> SPIDER;
                    case INVERT -> INVERT;
                    default ->
                            throw new IllegalArgumentException(
                                    "Kernel requires an explicit effect plan");
                });
    }

    public static boolean menuBlur(float radius) {
        var passes = new java.util.ArrayList<Pass>();
        int input = 0;
        for (int i = 0; i < 6; i++) {
            int out = (i & 1) == 0 ? 1 : 2;
            passes.add(
                    new Pass(
                            Program.BLUR,
                            out,
                            List.of(input),
                            RenderResources.Sampling.LINEAR,
                            (i & 1) == 0 ? 1 : 0,
                            (i & 1) == 0 ? 0 : 1,
                            radius,
                            1f / (1 << (i / 2))));
            input = out;
        }
        return submit(
                new Plan(
                        Role.FULLSCREEN_MENU_BLUR,
                        List.of(Size.full(), Size.full()),
                        passes,
                        input));
    }
}
