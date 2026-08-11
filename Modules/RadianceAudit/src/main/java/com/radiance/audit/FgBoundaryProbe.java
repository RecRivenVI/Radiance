package com.radiance.audit;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.Files;

/** Explicit isolated probe. No appearance assertions and no driver fault injection. */
public final class FgBoundaryProbe {
    private static final boolean ENABLED = "1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_FG_BOUNDARIES"));
    private static final boolean MENU_ONLY = "menu".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_FG_BOUNDARIES"));
    private static final boolean WORLD_PROBE = "world-probe".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_FG_BOUNDARIES"));
    private static final int WORLD_PROBE_SECONDS = Math.clamp(
        Integer.parseInt(com.radiance.audit.ExperimentAccess.getenv().getOrDefault("RADIANCE_WORLD_PROBE_SECONDS", "60")), 1, 60);
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long entered;
    private static int step;
    private static boolean extraEffects;
    private static final ResourceLocation CROSSHAIR = ResourceLocation.withDefaultNamespace("hud/crosshair");
    public static void register() { if (ENABLED) NeoForge.EVENT_BUS.addListener(FgBoundaryProbe::afterScreen); }
    public static boolean poll(Minecraft mc) {
        if (WORLD_PROBE) {
            if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
                throw new IllegalStateException("World probe requires an isolated acceptance marker");
            if (mc.level != null && mc.player != null) {
                if (entered == 0) {
                    entered = System.nanoTime(); mc.options.pauseOnLostFocus = false;
                    LOG.info("GPU_PROBE world-ready pid={} dimension={} limitSeconds={}", ProcessHandle.current().pid(), mc.level.dimension().location(), WORLD_PROBE_SECONDS);
                }
                long seconds = (System.nanoTime()-entered)/1_000_000_000L;
                if ((seconds >= WORLD_PROBE_SECONDS || Files.isRegularFile(mc.gameDirectory.toPath().resolve("gpu-probe-stop.request"))) && step++ == 0) {
                    LOG.info("GPU_PROBE normal-stop elapsedSeconds={}", seconds); mc.stop();
                }
            }
            return true;
        }
        if (MENU_ONLY) {
            if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
                throw new IllegalStateException("Menu probe requires an isolated acceptance marker");
            if (mc.level != null) throw new IllegalStateException("Menu probe must not enter a world");
            if (mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                if (entered == 0) {
                    entered = System.nanoTime();
                    LOG.info("FG_BOUNDARY menu-ready pid={}", ProcessHandle.current().pid());
                }
                if ((System.nanoTime()-entered)/1_000_000_000L >= 25 && step++ == 0) {
                    LOG.info("FG_BOUNDARY menu-normal-stop"); mc.stop();
                }
            }
            return true;
        }
        if (!ENABLED) return false;
        if (mc.level == null || mc.player == null) return true;
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("FG probe requires isolated acceptance marker");
        if (entered == 0) {
            entered=System.nanoTime(); mc.options.pauseOnLostFocus=false;
            mc.options.menuBackgroundBlurriness().set(5);
            LOG.info("FG_BOUNDARY world-ready; idle pause disabled in isolated process");
        }
        long seconds=(System.nanoTime()-entered)/1_000_000_000L;
        try {
            if (step==0 && seconds>=8) { step++; capture(mc,"world-crosshair"); }
            else if (step==1 && seconds>=16) { step++;mc.setScreen(new PauseScreen(true)); }
            else if (step==2 && seconds>=21) { step++;capture(mc,"vanilla-pause-after-HUD"); }
            else if (step==3 && seconds>=27) { step++;extraEffects=true; }
            else if (step==4 && seconds>=32) { step++;capture(mc,"panel-text-invert-blur-later-UI"); }
            else if (step==5 && seconds>=40) {
                step++; var type=Class.forName("net.createmod.ponder.foundation.ui.PonderUI");
                mc.setScreen((Screen)type.getMethod("of",ResourceLocation.class).invoke(null,
                    ResourceLocation.fromNamespaceAndPath("create","mechanical_piston")));
            } else if (step==6 && seconds>=49) { step++;capture(mc,"ponder-panel-blur-invert"); }
            else if (step==7 && seconds>=59) {
                step++;var method=mc.screen.getClass().getDeclaredMethod("scroll",boolean.class);
                method.setAccessible(true);LOG.info("FG_BOUNDARY transition={}",method.invoke(mc.screen,true));
            } else if (step==8 && seconds>=64) { step++;capture(mc,"transition-effects"); }
            else if (step==9 && seconds>=72) { step++;extraEffects=false;mc.setScreen(null); }
            else if (step==10 && seconds>=79) { step++;capture(mc,"returned-world-crosshair"); }
            else if (step==11 && seconds>=88) { step++;LOG.info("FG_BOUNDARY normal-stop");mc.stop(); }
        } catch (ReflectiveOperationException | java.io.IOException e) { throw new IllegalStateException("FG probe step "+step,e); }
        return true;
    }
    private static void capture(Minecraft mc,String label) throws java.io.IOException {
        Files.writeString(mc.gameDirectory.toPath().resolve("radiance-fg-capture.request"),label);
        LOG.info("FG_BOUNDARY capture={} screen={}",label,mc.screen==null ? "world" : mc.screen.getClass().getName());
    }
    private static void inverse(net.minecraft.client.gui.GuiGraphics g, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
            GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        g.blitSprite(CROSSHAIR,x,y,45,45);
        RenderSystem.defaultBlendFunc();
    }
    private static void afterScreen(ScreenEvent.Render.Post event) {
        if (!extraEffects) return;
        var mc=Minecraft.getInstance();var g=event.getGuiGraphics();
        g.fill(20,30,240,110,0x804070A0);
        g.drawString(mc.font,"Before: fractional text + panel",25,40,0xA0FFFFFF,false);
        g.flush(); inverse(g,100,60);
        // Actual GameRenderer -> ScreenEffectCoordinator -> native postBlur path.
        RenderSystem.disableDepthTest();mc.gameRenderer.processBlurEffect(event.getPartialTick());
        inverse(g,250,60);
        g.fill(20,130,240,175,0x60703050);
        g.drawString(mc.font,"After: sharp text + panel",25,140,0xA0FFFFFF,false);g.flush();
    }
}
