package com.radiance.audit;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Isolated 40-second camera translation/rotation fixture, not a gameplay or visual verdict. */
final class FixedSceneRouteProbe {
    private static final boolean ENABLED = "1".equals(ExperimentAccess.getenv("RADIANCE_FIXED_SCENE_ROUTE"));
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long entered;
    private static int previous = -1, skipped;
    private static boolean finished;
    private static double x,y,z;
    private static float yaw,pitch;
    private static CompletableFuture<Void> pending;
    static boolean poll(Minecraft mc) {
        if (!ENABLED) return false;
        if (finished || mc.level == null || mc.player == null) return true;
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("Fixed route requires an isolated instance");
        long now=System.nanoTime();
        try {
            if (entered==0) {
                entered=now; x=mc.player.getX();y=mc.player.getY();z=mc.player.getZ();
                yaw=mc.player.getYRot();pitch=mc.player.getXRot();
                LOG.info("FIXED_ROUTE ready x={} y={} z={} yaw={} pitch={}",x,y,z,yaw,pitch);
            }
            if (pending!=null) { if(!pending.isDone()) return true; pending.join(); pending=null; }
            double elapsed=(now-entered)/1e9-45;
            if(elapsed<0) return true;
            int step=Math.min(200,(int)(elapsed*5));
            if(step==previous) return true;
            skipped+=Math.max(0,step-previous-1); previous=step;
            double phase=step/200.0;
            double dx=8*Math.sin(Math.PI*phase);
            float turn=(float)(35*Math.sin(2*Math.PI*phase));
            if(step==200) {dx=0;turn=0;}
            var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
            if(server==null) throw new IllegalStateException("Fixed route requires an integrated server");
            double targetX=x+dx;float targetYaw=yaw+turn;
            pending=CompletableFuture.runAsync(() -> {
                var player=server.getPlayerList().getPlayer(id);
                if(player==null) throw new IllegalStateException("Route player disconnected");
                player.connection.teleport(targetX,y,z,targetYaw,pitch);
            },server);
            if(step%25==0) LOG.info("FIXED_ROUTE target step={} x={} y={} z={} yaw={}",step,targetX,y,z,targetYaw);
            if(step==200) {
                pending.whenComplete((ignored,error)-> {
                    if(error!=null) LOG.error("FIXED_ROUTE FAIL final move",error);
                    else LOG.info("FIXED_ROUTE PASS steps=201 skipped={} duration=40s; camera fixture only",skipped);
                });
                finished=true;
            }
        } catch(Exception | LinkageError failure) {
            finished=true;LOG.error("FIXED_ROUTE FAIL; stop without retry",failure);mc.stop();
        }
        return true;
    }
}
