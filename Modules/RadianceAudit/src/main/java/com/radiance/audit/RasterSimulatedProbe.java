package com.radiance.audit;

import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Explicit isolated fixture: original producers, no visual pass judgement. */
public final class RasterSimulatedProbe {
    private static final boolean ENABLED = "1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_RASTER_SIMULATED_SMOKE"));
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final String ID = Long.toString(System.currentTimeMillis());
    private static long start;
    private static int step;
    private static Object sublevel, handler;
    private static boolean assemblyRequested;
    public static void poll(Minecraft mc) {
        if (!ENABLED || mc.level == null || mc.player == null) return;
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("Raster fixture requires isolated marker");
        if (start == 0) { start = System.nanoTime(); mc.options.pauseOnLostFocus = false; LOG.info("RASTER_SIM world-ready"); }
        long seconds = (System.nanoTime()-start)/1_000_000_000L;
        try {
            if (step == 0 && seconds >= 5) {
                Object container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer")
                    .getMethod("getContainer", Level.class).invoke(null, mc.level);
                Collection<?> levels = (Collection<?>)call(container, "getAllSubLevels");
                if (levels.isEmpty()) {
                    if (!assemblyRequested) {
                        assemblyRequested=true;
                        BlockPos block = mc.player.blockPosition().offset(3, 3, 0);
                        mc.getSingleplayerServer().execute(() -> {
                            try {
                                var level = mc.getSingleplayerServer().overworld();
                                if (!level.getBlockState(block).isAir()) throw new IllegalStateException("Fixture location occupied");
                                level.setBlock(block, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                                var result = Class.forName("dev.simulated_team.simulated.util.SimAssemblyHelper")
                                    .getMethod("assembleFromSingleBlock", Level.class, BlockPos.class, BlockPos.class, boolean.class, boolean.class)
                                    .invoke(null, level, block, block, true, true);
                                LOG.info("RASTER_SIM fixture assembled {}", result);
                            } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                        });
                    }
                    if(seconds>25)throw new IllegalStateException("Fixture sublevel not delivered");
                    return;
                }
                sublevel=levels.iterator().next();
                handler=Class.forName("dev.simulated_team.simulated.SimulatedClient").getField("PHYSICS_STAFF_CLIENT_HANDLER").get(null);
                UUID uuid=(UUID)call(sublevel,"getUniqueId");
                invoke(handler,"setLocks",mc.level.dimension(),List.of(uuid));
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("simulated","creative_physics_staff")));
                if(stack.isEmpty())throw new IllegalStateException("Staff item missing");
                mc.player.getInventory().selected=0;mc.player.getInventory().setItem(0,stack);
                mc.gameMode.handleCreativeModeItemAdd(stack,36);
                step++;LOG.info("RASTER_SIM staff fixture uuid={} count={}",uuid,levels.size());
            }
            if(handler!=null && step<3) {
                Object bounds=call(call(sublevel,"getPlot"),"getBoundingBox");
                // The original beam producer expects a plot-local target and transforms it
                // through that sublevel's interpolated pose while rendering.
                double x=(((Number)call(bounds,"minX")).doubleValue()+((Number)call(bounds,"maxX")).doubleValue()+1)*.5;
                double y=(((Number)call(bounds,"minY")).doubleValue()+((Number)call(bounds,"maxY")).doubleValue()+1)*.5;
                double z=(((Number)call(bounds,"minZ")).doubleValue()+((Number)call(bounds,"maxZ")).doubleValue()+1)*.5;
                invoke(handler,"updateBeam",mc.level,mc.player.getUUID(),mc.player.position(),new Vec3(x,y,z));
            }
            if(step==1 && seconds>=12){ step++;capture(mc,"staff"); }
            else if(step==2 && seconds>=16){
                step++;
                var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("simulated","contraption_diagram"));
                Object entity=type.create(mc.level);
                if(entity==null || !entity.getClass().getSimpleName().equals("DiagramEntity"))throw new IllegalStateException("Diagram entity missing");
                var configClass=Class.forName("dev.simulated_team.simulated.content.entities.diagram.DiagramConfig");
                Object config=configClass.getMethod("makeDefault",entity.getClass()).invoke(null,entity);
                invokeStatic("dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen","open",entity,config,sublevel);
                LOG.info("RASTER_SIM diagram-open {}",mc.screen.getClass().getName());
            }else if(step==3 && seconds>=26){ step++;capture(mc,"diagram");
                var q=(org.joml.Quaternionf)mc.screen.getClass().getField("LOCAL_ORIENTATION").get(null);q.rotateY(.6f).rotateX(.3f);
            }else if(step==4 && seconds>=32){ step++;capture(mc,"diagram-rotated");
                mc.options.guiScale().set(2);mc.resizeDisplay();LOG.info("RASTER_SIM GUI scale=2");
            }else if(step==5 && seconds>=39){ step++;capture(mc,"diagram-scale2");mc.screen.onClose();
                var container=net.neoforged.fml.ModList.get().getModContainerById("create").orElseThrow();
                mc.setScreen(container.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class).orElseThrow().createScreen(container,mc.screen));
            }else if(step==6 && seconds>=45){ step++;capture(mc,"create-config");
                mc.setScreen((Screen)Class.forName("net.createmod.ponder.foundation.ui.PonderUI").getMethod("of",ResourceLocation.class)
                    .invoke(null,ResourceLocation.fromNamespaceAndPath("create","mechanical_piston")));
                LOG.info("RASTER_SIM original Ponder opened");
            }else if(step==7 && seconds>=55){step++;capture(mc,"ponder");scroll(mc,true);}
            else if(step==8 && seconds>=64){step++;capture(mc,"ponder-next");scroll(mc,false);}
            else if(step==9 && seconds>=73){step++;capture(mc,"ponder-back");mc.screen.onClose();}
            else if(step==10 && seconds>=79){step++;LOG.info("RASTER_SIM normal-stop-requested");mc.stop();}
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Raster Simulated fixture step "+step,ex);}
    }
    private static Object call(Object target,String name)throws ReflectiveOperationException{return target.getClass().getMethod(name).invoke(target);}
    private static Object invoke(Object target,String name,Object... args)throws ReflectiveOperationException{
        return invokeMethod(target.getClass(),target,name,args);
    }
    private static Object invokeStatic(String type,String name,Object...args)throws ReflectiveOperationException{
        return invokeMethod(Class.forName(type),null,name,args);
    }
    private static Object invokeMethod(Class<?> type,Object target,String name,Object[] args)throws ReflectiveOperationException{
        for(var m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            boolean fit=true;var types=m.getParameterTypes();for(int i=0;i<args.length;i++)if(args[i]!=null&&!types[i].isInstance(args[i])){fit=false;break;}
            if(fit)return m.invoke(target,args);
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,ID+"-"+name+".png",mc.getMainRenderTarget(),c->LOG.info("RASTER_SIM capture {} {}",name,c.getString()));}
    private static void scroll(Minecraft mc,boolean forward)throws ReflectiveOperationException{var m=mc.screen.getClass().getDeclaredMethod("scroll",boolean.class);m.setAccessible(true);LOG.info("RASTER_SIM scroll {} {}",forward,m.invoke(mc.screen,forward));}
}
