package com.radiance.audit;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Explicit isolated-world mutations, separate from all performance measurements. */
public final class RigidModelLifecycleProbe {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.rigidLifecycle");
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static long next, started, reloadMax;
    private static int step;
    private static boolean stopped;
    private static UUID target;
    private static CompletableFuture<?> pending;
    private record State(int id,ItemStack item,int rotation,boolean invisible,boolean glowing) {}
    private static volatile State expected;
    private RigidModelLifecycleProbe() {}

    public static void poll(Minecraft mc) {
        if (!ENABLED || !ExperimentAccess.permitted() || stopped || mc.level == null || mc.player == null) return;
        long now=System.nanoTime();
        if (started==0) { started=now; next=now+35_000_000_000L; }
        try {
            if (now-started>190_000_000_000L) throw new IllegalStateException("Rigid lifecycle timed out");
            if (now<next || (pending!=null && !pending.isDone())) return;
            if (pending!=null) { pending.join(); pending=null; }
            if (step>0) {
                State state=expected;
                if(state!=null) {
                    var entity=mc.level.getEntity(state.id());
                    if(!(entity instanceof ItemFrame f) || !ItemStack.matches(f.getItem(),state.item())
                        || f.getRotation()!=state.rotation() || f.isInvisible()!=state.invisible()
                        || f.isCurrentlyGlowing()!=state.glowing())
                        throw new IllegalStateException("Expected item-frame state not delivered to client");
                }
                RigidModelProbe.checkpoint("stage-"+(step-1));
                LOG.info("RIGID_LIFECYCLE completed stage={} cache={}",step-1,cacheIds().size());
            }
            switch(step++) {
                case 0 -> {
                    List<ItemFrame> frames=new ArrayList<>();
                    for(var entity:mc.level.entitiesForRendering()) if(entity instanceof ItemFrame f) frames.add(f);
                    if(frames.size()<2) throw new IllegalStateException("Fixture requires shared item-frame producers");
                    target=frames.getFirst().getUUID();
                    mutate(mc,f -> {f.setItem(new ItemStack(Items.OAK_LEAVES));f.setRotation(3);});
                }
                case 1 -> mutate(mc,f -> {f.setItem(new ItemStack(Items.GLASS));f.setRotation(6);});
                case 2 -> mutate(mc,f -> {var item=new ItemStack(Items.DIAMOND_SWORD);
                    item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);f.setItem(item);f.setGlowingTag(true);});
                case 3 -> mutate(mc,f -> {f.setGlowingTag(false);f.setInvisible(true);f.setItem(ItemStack.EMPTY);});
                case 4 -> mutate(mc,f -> {f.setInvisible(false);f.setItem(new ItemStack(Items.DIAMOND));f.setRotation(1);});
                case 5 -> {
                    reloadMax=cacheIds().stream().mapToLong(Long::longValue).max().orElseThrow();
                    pending=mc.reloadResourcePacks();
                }
                case 6 -> {
                    if(cacheIds().stream().anyMatch(id -> id<=reloadMax))
                        throw new IllegalStateException("Old local model survived resource reload");
                    mutate(mc,f -> {
                        var replacement=new ItemFrame(f.level(),f.getPos(),f.getDirection());
                        replacement.setItem(new ItemStack(Items.OAK_LEAVES));replacement.setRotation(5);
                        f.discard();f.level().addFreshEntity(replacement);target=replacement.getUUID();
                    });
                }
                case 7 -> { mutate(mc,f -> f.setRotation(2)); mc.levelRenderer.allChanged(); }
                case 8 -> {
                    LOG.info("RIGID_LIFECYCLE PASS shared models, rotation, cutout/glass, glint/outline fallback, empty/invisible, reload, replacement, F3+A; not visual approval");
                    stopped=true;mc.stop();
                }
                default -> throw new IllegalStateException("Unknown stage");
            }
            next=now+8_000_000_000L;
        } catch(Throwable error) {
            LOG.error("RIGID_LIFECYCLE FAIL stage={}",step,error);stopped=true;mc.stop();
        }
    }
    private static void mutate(Minecraft mc,java.util.function.Consumer<ItemFrame> action) {
        var server=Objects.requireNonNull(mc.getSingleplayerServer());var dimension=mc.level.dimension();
        pending=CompletableFuture.runAsync(() -> {
            var entity=server.getLevel(dimension).getEntity(target);
            if(!(entity instanceof ItemFrame frame)) throw new IllegalStateException("Fixture item frame missing");
            action.accept(frame);
            var changed=(ItemFrame)server.getLevel(dimension).getEntity(target);
            expected=new State(changed.getId(),changed.getItem().copy(),changed.getRotation(),
                changed.isInvisible(),changed.isCurrentlyGlowing());
        },server);
    }
    private static List<Long> cacheIds() throws ReflectiveOperationException {
        Field cache=Class.forName("com.radiance.client.vertex.RigidModelCapture").getDeclaredField("CACHE");
        cache.setAccessible(true);List<Long> result=new ArrayList<>();
        for(Object entry:(List<?>)cache.get(null)) result.add((Long)RigidModelProbe.field(entry,"id"));
        if(result.isEmpty()) throw new IllegalStateException("Persistent model path not exercised");
        return result;
    }
}
