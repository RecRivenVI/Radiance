package com.radiance.audit;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.audit.mixin.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.io.*;

/** Fixed isolated world, normal interaction packets, bounded run. Never judges pixels. */
public final class ChunkLatencyProbe {
    private static final boolean ENABLED="1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_CHUNK_BENCH"));
    private static long start, last, operationAt;
    private static volatile int action;
    private static int observed;
    private static volatile int serverObserved;
    private static boolean reload, stopped;
    private static BlockPos support;
    private static volatile BlockPos target;
    private static volatile long actionTime;
    private static final java.util.concurrent.atomic.AtomicLong diskLoads=new java.util.concurrent.atomic.AtomicLong(), newChunks=new java.util.concurrent.atomic.AtomicLong();
    private static PrintWriter samples, trace;
    private static java.lang.reflect.Method traceMethod;
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    public static void register() {
        if (!ENABLED) return;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
            int request=action; var pos=target;
            if (request==0 || request==serverObserved || pos==null) return;
            var level=event.getServer().overworld();
            if (level.hasChunkAt(pos) && level.getBlockState(pos).isAir()==(request%2==0)) {
                serverObserved=request;
                LOG.info("CHUNK_BENCH server-state operation={} java_ns={} latency_ns={}",request,System.nanoTime(),System.nanoTime()-actionTime);
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkDataEvent.Load event) -> diskLoads.incrementAndGet());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
            if (event.isNewChunk()) newChunks.incrementAndGet();
        });
    }
    public static void poll(Minecraft mc) {
        if(!ENABLED || stopped || mc.level==null || mc.player==null) return;
        long now=System.nanoTime();
        try {
            if(start==0) {
                if(!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))throw new IllegalStateException("Isolated marker required");
                if ("1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_FACE_PROBE"))) {
                    try { Class.forName("com.radiance.client.render.MaterialFaces"); FaceCaptureProbe.verify(); }
                    catch(ClassNotFoundException baseline) {LOG.info("FACE_CAPTURE_PROBE absent in baseline");}
                }
                start=last=now;
                samples=new PrintWriter(Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("chunk-benchmark.csv")));
                trace=new PrintWriter(Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("chunk-update-trace.txt")));
                try { traceMethod=ChunkProxy.class.getMethod("drainUpdateTraceNative"); } catch(NoSuchMethodException missing) {trace.println("This artifact has no native revision/frame tracing API; do not infer update completion from ready count.");}
                samples.println("elapsed_ns,real_frame_interval_ns,process_cpu_ns,ready_sections,disk_deserializations,new_server_chunks,focused,phase");
                mc.options.pauseOnLostFocus=false;
                mc.setScreen(null);
                support=mc.player.blockPosition().offset(2,-1,0);
                for(int y=0;y<5 && mc.level.getBlockState(support).isAir();y++) support=support.below();
                target=support.above();
                if(mc.level.getBlockState(support).isAir() || !mc.level.getBlockState(target).isAir()) throw new IllegalStateException("Benchmark target must have solid support and empty placement cell");
                mc.getSingleplayerServer().execute(()->{
                    var sp=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    if(sp!=null)sp.setGameMode(GameType.CREATIVE);
                });
                LOG.info("CHUNK_BENCH begin target={} player={} view={} sim={} dimensions={}x{}",target,mc.player.position(),mc.options.renderDistance().get(),mc.options.simulationDistance().get(),mc.getWindow().getWidth(),mc.getWindow().getHeight());
            }
            long elapsed=now-start;
            if(traceMethod!=null) {
                long before=System.nanoTime();
                String events=(String)traceMethod.invoke(null);
                long after=System.nanoTime();
                trace.println("JAVA_BRACKET "+before+" "+after);trace.print(events);trace.flush();
            }
            long cpu=((com.sun.management.OperatingSystemMXBean)java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
            samples.println(elapsed+","+(now-last)+","+cpu+","+ChunkProxy.countReadyChunksNative()+","+diskLoads.get()+","+newChunks.get()+","+mc.isWindowActive()+","+(reload?"reload":"idle")); last=now;
            samples.flush();
            if(elapsed>=40_000_000_000L && !reload) {reload=true;mc.levelRenderer.allChanged();LOG.info("CHUNK_BENCH rebuild-all elapsed_ns={}",elapsed);}
            if(action<24 && action==observed && action==serverObserved && elapsed>=12_000_000_000L+(action<12?action*1_500_000_000L:30_000_000_000L+(action-12)*1_500_000_000L)) {
                mc.player.getInventory().selected=0;
                mc.player.getInventory().setItem(0,new ItemStack(Items.STONE));
                mc.gameMode.handleCreativeModeItemAdd(mc.player.getMainHandItem(),36);
                boolean place=action%2==0;
                if (mc.level.getBlockState(target).isAir()!=place) throw new IllegalStateException("Benchmark precondition changed before operation "+action);
                operationAt=actionTime=System.nanoTime();action++;
                if(place) mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,0.5,0),Direction.UP,support,false));
                else mc.gameMode.startDestroyBlock(target,Direction.UP);
                LOG.info("CHUNK_BENCH operation={} kind={} java_ns={} pos={}",action,place?"place":"dig",operationAt,target);
            }
            if(action!=observed && mc.level.getBlockState(target).isAir()==(action%2==0)) {
                observed=action;long readAt=System.nanoTime();LOG.info("CHUNK_BENCH client-state operation={} java_ns={} latency_ns={}",action,readAt,readAt-operationAt);
            }
            if(elapsed>=75_000_000_000L) {stopped=true;samples.close();trace.close();LOG.info("CHUNK_BENCH end actions={} observed={} server_observed={} normal-stop",action,observed,serverObserved);mc.stop();}
        } catch(IOException e){throw new UncheckedIOException(e);} catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}
