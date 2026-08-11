package com.radiance.compatibility.simulated;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.render.RenderCaptureContract;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.simulated_team.simulated.util.SimpleSubLevelGroupRenderer;
import com.radiance.compatibility.veil.VeilAdapter;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.world.phys.AABB;
import org.lwjgl.system.MemoryUtil;

/** Original Simulated draw/post/composition with scoped Vulkan resources, never a second renderer. */
public final class SimulatedDiagramCompatibility {
    private static final Map<Object, Resources> RESOURCES = new IdentityHashMap<>();
    private static Context active;
    private SimulatedDiagramCompatibility() {}

    public static boolean isRenderingDiagram() { return active != null; }
    public static float logicalWidth() { return active == null ? 0 : active.width; }
    public static float logicalHeight() { return active == null ? 0 : active.height; }

    public static <T> T framebuffer(int width, int height) {
        var window = Minecraft.getInstance().getWindow();
        return VeilAdapter.framebufferBuilder(
            DiagramPresentation.pixels(width, window.getWidth(), window.getGuiScaledWidth()),
            DiagramPresentation.pixels(height, window.getHeight(), window.getGuiScaledHeight()));
    }

    public static void draw(ClientSubLevel root, Object fbo, float width, float height,
        Runnable original) {
        var resources = RESOURCES.computeIfAbsent(fbo, ignored -> new Resources());
        var selected = Collections.newSetFromMap(new IdentityHashMap<RenderSection, Boolean>());
        for (var subLevel : SimpleSubLevelGroupRenderer.getRenderedChain(root))
            if (subLevel.getRenderData() instanceof VanillaChunkedSubLevelRenderData data)
                selected.addAll(data.allRenderSections());
        resources.meshes.retain(selected);
        // Flush before rebinding: callers may still have paper/GUI vertices pending.
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        Context previous = active;
        var recovery = new SimulatedRenderRecovery();
        SimulatedFramebufferRecovery.capture(recovery);
        recovery.add(() -> active = previous);
        active = new Context(resources, width, height);
        try (var scope = RenderCaptureContract.enter(RenderCaptureContract.ScopeKind.GUI,
            "simulated-diagram")) {
            recovery.run(() -> resources.lightmaps.run(original));
        }
    }

    public static VertexBuffer buffer(RenderSection section, RenderType layer,
        ClientSubLevel subLevel, double x, double y, double z) {
        if (active == null) return section.getBuffer(layer);
        return active.resources.meshes.buffer(section, layer, subLevel.getLevel());
    }

    public static void release(Object fbo, Runnable original) {
        Resources resources = RESOURCES.remove(fbo);
        var recovery = new SimulatedRenderRecovery();
        recovery.add(original);
        recovery.run(() -> { if (resources != null) resources.close(); });
    }

    /** One readback per initial decoration pass, with all queries using the final post alpha. */
    public static final class Coverage implements AutoCloseable {
        private final ByteBuffer pixels;
        private final int width, height, logicalWidth, logicalHeight;
        public Coverage(Object target, int logicalWidth, int logicalHeight) {
            this.width = VeilAdapter.framebufferWidth(target); this.height = VeilAdapter.framebufferHeight(target);
            this.logicalWidth = logicalWidth; this.logicalHeight = logicalHeight;
            pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
            var recovery = new SimulatedRenderRecovery();
            SimulatedFramebufferRecovery.capture(recovery);
            try {
                recovery.run(() -> {
                    VeilAdapter.bindFramebufferRead(target);
                    FramebufferProxy.readPixels(0, 0, width, height, 0x1908, 0x1401,
                        MemoryUtil.memAddress(pixels));
                });
            } catch (RuntimeException | Error failure) { MemoryUtil.memFree(pixels); throw failure; }
        }
        public boolean occupied(AABB box) {
            int x = (int) box.minX;
            int y = (int) (logicalHeight - box.maxY);
            return DiagramPresentation.occupied(pixels, width, height, logicalWidth, logicalHeight,
                x, y, (int) box.maxX - x, (int) (logicalHeight - box.minY) - y);
        }
        @Override public void close() { MemoryUtil.memFree(pixels); }
    }

    private record Context(Resources resources, float width, float height) {}
    private static final class Resources implements AutoCloseable {
        final DiagramLightmaps lightmaps = new DiagramLightmaps();
        final DiagramSectionMeshes meshes = new DiagramSectionMeshes();
        @Override public void close() {
            var recovery = new SimulatedRenderRecovery();
            recovery.add(lightmaps::close); recovery.add(meshes::close); recovery.run(() -> {});
        }
    }
}
