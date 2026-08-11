package com.radiance.audit;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Explicit mutations of a disposable world only; not a performance or visual acceptance run. */
public final class PartModelLifecycleProbe {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.partLifecycle");
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static long started, next, reloadMax;
    private static int step;
    private static boolean stopped;
    private static UUID target;
    private static CompletableFuture<?> pending;
    private static volatile State expected;
    private record State(int id, Rotations head, Rotations body, boolean arms, boolean base,
                         boolean glowing, ItemStack helmet) {
        static State of(ArmorStand a) { return new State(a.getId(),a.getHeadPose(),a.getBodyPose(),
            a.isShowArms(),a.isNoBasePlate(),a.isCurrentlyGlowing(),a.getItemBySlot(EquipmentSlot.HEAD).copy()); }
        boolean matches(ArmorStand a) { return head.equals(a.getHeadPose()) && body.equals(a.getBodyPose())
            && arms==a.isShowArms() && base==a.isNoBasePlate() && glowing==a.isCurrentlyGlowing()
            && ItemStack.matches(helmet,a.getItemBySlot(EquipmentSlot.HEAD)); }
    }
    private PartModelLifecycleProbe() {}
    public static void poll(Minecraft mc) {
        if (!ENABLED || !ExperimentAccess.permitted() || stopped || mc.level==null || mc.player==null) return;
        long now=System.nanoTime();
        if(started==0) {started=now;next=now+35_000_000_000L;}
        try {
            if(now-started>185_000_000_000L)throw new IllegalStateException("Part lifecycle timeout");
            if(now<next || pending!=null && !pending.isDone())return;
            if(pending!=null){pending.join();pending=null;}
            if(expected!=null){
                if(!(mc.level.getEntity(expected.id) instanceof ArmorStand a) || !expected.matches(a))
                    throw new IllegalStateException("Changed part owner not delivered to client");
                // Outline is intentionally the original wrapped-consumer path, not this optimization.
                if(!expected.glowing)PartModelProbe.checkpoint("lifecycle-"+(step-1));
                LOG.info("PART_LIFECYCLE observed stage={} owner={} glowing={} cache={}",
                    step-1,expected.id,expected.glowing,cacheIds().size());
            }
            switch(step++) {
                case 0 -> {
                    var candidates=new ArrayList<ArmorStand>();
                    for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand a && !a.isInvisible())candidates.add(a);
                    var a=candidates.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElseThrow();
                    target=a.getUUID();PartModelProbe.target(a.getId());
                    mutate(mc,e -> {e.setHeadPose(new Rotations(25,45,10));e.setBodyPose(new Rotations(0,-25,0));
                        e.setShowArms(true);e.setNoBasePlate(true);});
                }
                case 1 -> mutate(mc,e -> {e.setHeadPose(new Rotations(-20,-60,30));e.setShowArms(false);
                    e.setNoBasePlate(false);e.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));});
                case 2 -> mutate(mc,e -> e.setGlowingTag(true));
                case 3 -> mutate(mc,e -> {e.setGlowingTag(false);e.setHeadPose(new Rotations(0,70,-25));
                    e.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);});
                case 4 -> {reloadMax=cacheIds().stream().mapToLong(Long::longValue).max().orElseThrow();pending=mc.reloadResourcePacks();}
                case 5 -> {
                    if(cacheIds().stream().anyMatch(id -> id<=reloadMax))throw new IllegalStateException("Part cache crossed resource reload");
                    var server=Objects.requireNonNull(mc.getSingleplayerServer());var dimension=mc.level.dimension();
                    pending=CompletableFuture.runAsync(() -> {
                        var level=server.getLevel(dimension);var old=(ArmorStand)level.getEntity(target);
                        var fresh=new ArmorStand(level,old.getX(),old.getY(),old.getZ());
                        fresh.setHeadPose(new Rotations(15,30,45));fresh.setNoGravity(true);
                        old.discard();level.addFreshEntity(fresh);target=fresh.getUUID();expected=State.of(fresh);
                    },server).thenRunAsync(() -> PartModelProbe.target(expected.id),mc);
                }
                case 6 -> {mc.levelRenderer.allChanged();}
                case 7 -> {
                    // Match PauseScreen's disconnect sequence. disconnect() pumps a nested tick:
                    // stop the probe before entering it, and close the level connection first.
                    stopped=true;
                    mc.level.disconnect();
                    mc.disconnect();
                    for(String type:List.of("PartModelCapture","RigidModelCapture")){
                        Field f=Class.forName("com.radiance.client.vertex."+type).getDeclaredField("CACHE");f.setAccessible(true);
                        if(!((List<?>)f.get(null)).isEmpty())throw new IllegalStateException("Recipe survived disconnect: "+type);
                    }
                    LOG.info("PART_LIFECYCLE PASS pose, visibility flags, equipment, outline fallback state, reload, replacement, F3+A, disconnect retirement; visual and GPU temporal parity remain unapproved");stopped=true;mc.stop();
                }
                default -> throw new IllegalStateException("Part lifecycle stage");
            }
            next=now+8_000_000_000L;
        }catch(Throwable failure){LOG.error("PART_LIFECYCLE FAIL stage={}",step,failure);stopped=true;mc.stop();}
    }
    private static void mutate(Minecraft mc,java.util.function.Consumer<ArmorStand> action){
        var server=Objects.requireNonNull(mc.getSingleplayerServer());var dimension=mc.level.dimension();
        pending=CompletableFuture.runAsync(() -> {var a=(ArmorStand)server.getLevel(dimension).getEntity(target);
            action.accept(a);expected=State.of(a);},server);
    }
    private static List<Long> cacheIds()throws ReflectiveOperationException{
        Field f=Class.forName("com.radiance.client.vertex.PartModelCapture").getDeclaredField("CACHE");f.setAccessible(true);
        var ids=new ArrayList<Long>();for(Object e:(List<?>)f.get(null))ids.add((long)RigidModelProbe.field(e,"id"));
        if(ids.isEmpty())throw new IllegalStateException("Part cache unexercised");return ids;
    }
}
