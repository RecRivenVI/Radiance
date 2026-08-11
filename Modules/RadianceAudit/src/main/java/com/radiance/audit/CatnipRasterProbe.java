package com.radiance.audit;

import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Isolated smoke only. Never judges appearance or runs without an explicit environment opt-in. */
public final class CatnipRasterProbe {
    private static final boolean ENABLED="1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_CATNIP_SMOKE"));
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final String CAPTURE_ID=Long.toString(System.currentTimeMillis());
    private static long entered;
    private static int step;
    public static void poll(Minecraft mc) {
        if(!ENABLED || mc.level==null || mc.player==null) return;
        if(!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("Catnip smoke requires isolated instance marker");
        if(entered==0){entered=System.nanoTime();LOG.info("CATNIP_SMOKE world-ready");}
        long seconds=(System.nanoTime()-entered)/1_000_000_000L;
        try {
            if(step==0 && seconds>=4){
                step++;
                var container=ModList.get().getModContainerById("create").orElseThrow();
                var factory=container.getCustomExtension(IConfigScreenFactory.class).orElseThrow();
                mc.setScreen(factory.createScreen(container,mc.screen));
                LOG.info("CATNIP_SMOKE config-open factory screen={}",mc.screen.getClass().getName());
            }else if(step==1 && seconds>=10){
                step++;capture(mc,"catnip-config.png");
                LOG.info("CATNIP_SMOKE config-render-survived screen={}",mc.screen.getClass().getName());
            }else if(step==2 && seconds>=13){
                step++;
                var type=Class.forName("net.createmod.ponder.foundation.ui.PonderUI");
                mc.setScreen((Screen)type.getMethod("of",ResourceLocation.class).invoke(null,
                    ResourceLocation.fromNamespaceAndPath("create","mechanical_piston")));
                if(mc.screen==null)throw new IllegalStateException("Ponder scene absent");
                LOG.info("CATNIP_SMOKE ponder-open");
            }else if(step==3 && seconds>=22){
                step++;capture(mc,"catnip-ponder.png");scroll(mc,true);
            }else if(step==4 && seconds>=32){
                step++;capture(mc,"catnip-ponder-next.png");scroll(mc,false);
            }else if(step==5 && seconds>=42){
                step++;capture(mc,"catnip-ponder-back.png");mc.setScreen(null);
                LOG.info("CATNIP_SMOKE scenes-render-survived");
            }else if(step==6 && seconds>=47){
                step++;LOG.info("CATNIP_SMOKE normal-stop-requested");mc.stop();
            }
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Catnip smoke step "+step,error);}
    }
    private static void capture(Minecraft mc,String name){
        Screenshot.grab(mc.gameDirectory,CAPTURE_ID+"-"+name,mc.getMainRenderTarget(),
            result->LOG.info("CATNIP_SMOKE capture {} {}",name,result.getString()));
    }
    private static void scroll(Minecraft mc,boolean forward)throws ReflectiveOperationException{
        var scroll=mc.screen.getClass().getDeclaredMethod("scroll",boolean.class);
        scroll.setAccessible(true);LOG.info("CATNIP_SMOKE scroll forward={} result={}",forward,scroll.invoke(mc.screen,forward));
    }
}
