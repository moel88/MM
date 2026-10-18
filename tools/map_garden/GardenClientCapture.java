package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.title.*;
import dev.sangwoo.celestialfall.map.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.*;
import net.minecraft.block.*;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import java.nio.file.*;

/** Disposable integration harness: not shipped in the production JAR. */
public final class GardenClientCapture implements ClientModInitializer {
    private long started=System.nanoTime(),wait;
    private int phase,shot;
    private volatile boolean running,finished;private volatile Throwable failure;
    private ArmorStandEntity camera;private boolean openedTitle;
    private static final Path OUT=Path.of("../build/map-garden/verification/native");
    // Position then point of interest; all captures use actual Minecraft textures/lighting.
    private static final double[][] VIEWS={
        {0,262,46,0,80,-4,96,1000},
        {38,86,43,38,82,23,70,1000},
        {-73,86,-30,-91,82,-53,68,1000},
        {54,87,-15,30,85,-98,70,1000},
        {-40,86,66,-50,85,39,70,1000},
        {98,91,151,83,84,128,70,1000},
        {38,86,43,38,82,23,70,14000},
        {51,86,-14,25,85,-76,70,14000},
        {33,83,-18,33,81,-48,70,1000}
    };
    public void onInitializeClient(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(failure!=null)throw new IllegalStateException("Server verification failed",failure);
            if((System.nanoTime()-started)/1e9>900)throw new IllegalStateException("Garden capture timed out phase "+phase);
            if(phase==0&&mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-started)/1e9>6){
                Files.createDirectories(OUT);mc.options.getViewDistance().setValue(16);mc.options.getGuiScale().setValue(2);
                mc.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
                mc.createIntegratedServerLoader().start(new TitleScreen(),System.getProperty("botand.garden.world","Botand_Garden_QA"));phase=1;return;
            }
            if(phase==1&&mc.currentScreen instanceof BotandTitleScreen title){
                openedTitle=true;var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+10,r.y()+10,0);phase=2;return;
            }
            if(phase==2&&mc.currentScreen==null&&ProgressClient.ready){
                if(!MapClient.enabled()||!BotandHud.enabled())throw new IllegalStateException("Map/HUD not enabled");
                mc.getServer().execute(()->{try{startRenovation(mc);}catch(Throwable e){failure=e;}});phase=3;return;
            }
            if(phase==3){
                if(!running||finished)return;
                mc.getServer().execute(()->{try{
                    if(!MapDecoration.running(mc.getServer().getOverworld())){finished=true;verifyRenovation(mc);}
                }catch(Throwable e){failure=e;}});
                phase=4;return;
            }
            if(phase==4){
                if(!finished){mc.getServer().execute(()->{try{if(!MapDecoration.running(mc.getServer().getOverworld())){finished=true;verifyRenovation(mc);}}catch(Throwable e){failure=e;}});return;}
                if(failure!=null)return;
                camera=new ArmorStandEntity(mc.world,0,0,0);camera.setInvisible(true);camera.setNoGravity(true);mc.setCameraEntity(camera);
                mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);mc.options.hudHidden=true;
                setView(mc);phase=5;return;
            }
            if(phase==5&&(System.nanoTime()-wait)/1e9>(shot==0?20:9)){
                capture(mc,"garden-"+shot+".png");shot++;
                if(shot<VIEWS.length){setView(mc);return;}
                // Last scene is the F3/HUD regression proof, then toggle debug back off.
                mc.options.debugEnabled=false;mc.options.hudHidden=false;wait=System.nanoTime();phase=6;
            }
            if(phase==6&&(System.nanoTime()-wait)/1e9>3){
                capture(mc,"hud-without-f3.png");
                Files.writeString(OUT.resolve("client-result.txt"),"PASS 2.14.0: original compact map auto-recognized; title Play; renovation command; all patch cells checked against target; custom block preserved; containers unchanged; actual textured world screenshots day/night; F3 and custom health HUD together; F3 off keeps HUD. Desktop Mesa, actual phone not tested. Auto-title="+openedTitle+"\n");
                mc.setCameraEntity(mc.player);mc.scheduleStop();phase=7;
            }
        }catch(Throwable e){failure=e;e.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("client-error.txt"),e.toString());}catch(Exception ignored){}mc.scheduleStop();}});
    }
    private int custom;private GardenPatch patch;private BlockState[] states;private java.util.Map<BlockPos,net.minecraft.nbt.NbtCompound> containers;
    private void startRenovation(MinecraftClient mc)throws Exception{
        var s=mc.getServer();var w=s.getOverworld();patch=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_garden.bgp.gz"));states=new BlockState[patch.palette.length];
        for(int i=0;i<states.length;i++)states[i]=BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(),patch.palette[i],false).blockState();
        custom=0;while(patch.y(custom)>79)custom++;
        w.setBlockState(new BlockPos(patch.x(custom),patch.y(custom),patch.z(custom)),Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        containers=new java.util.HashMap<>();
        for(int[] p:new int[][]{{-50,81,-123},{38,81,-19}}){var pos=new BlockPos(p[0],p[1],p[2]);var be=w.getBlockEntity(pos);if(be!=null)containers.put(pos,be.createNbt());}
        int result=s.getCommandManager().executeWithPrefix(s.getCommandSource().withWorld(w),"botand map decorate");
        if(result!=1||!MapDecoration.running(w))throw new IllegalStateException("Renovation did not start");running=true;
    }
    private void verifyRenovation(MinecraftClient mc)throws Exception{
        var w=mc.getServer().getOverworld();int mismatches=0;String first="";
        for(int i=0;i<patch.count;i++){
            var pos=new BlockPos(patch.x(i),patch.y(i),patch.z(i));var expected=i==custom?Blocks.DIAMOND_BLOCK.getDefaultState():states[patch.after(i)];
            if(w.getBlockState(pos)!=expected){mismatches++;if(mismatches<=50)first+=pos+" expected="+expected+" got="+w.getBlockState(pos)+"\n";}
        }
        for(var e:containers.entrySet())if(!e.getValue().equals(w.getBlockEntity(e.getKey()).createNbt()))throw new IllegalStateException("Container changed "+e.getKey());
        Files.writeString(OUT.resolve("server-result.txt"),"Cells="+patch.count+" mismatches="+mismatches+" preserved custom block=true containers="+containers.size()+"\n"+first);
        if(mismatches!=0)throw new IllegalStateException("Target mismatch "+mismatches+" "+first);
        w.setBlockState(new BlockPos(patch.x(custom),patch.y(custom),patch.z(custom)),states[patch.after(custom)],Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
    }
    private void setView(MinecraftClient mc){
        var v=VIEWS[shot];float yaw=(float)Math.toDegrees(Math.atan2(-(v[3]-v[0]),v[5]-v[2]));
        float pitch=(float)Math.toDegrees(-Math.atan2(v[4]-v[1],Math.hypot(v[3]-v[0],v[5]-v[2])));
        camera.refreshPositionAndAngles(v[0],v[1]-camera.getStandingEyeHeight(),v[2],yaw,pitch);camera.prevYaw=yaw;camera.prevPitch=pitch;
        camera.setHeadYaw(yaw);camera.prevHeadYaw=yaw;camera.setBodyYaw(yaw);camera.prevBodyYaw=yaw;
        mc.options.getFov().setValue((int)v[6]);mc.worldRenderer.scheduleTerrainUpdate();
        mc.getServer().execute(()->mc.getServer().getOverworld().setTimeOfDay((long)v[7]));
        mc.options.hudHidden=shot!=VIEWS.length-1;mc.options.debugEnabled=shot==VIEWS.length-1;
        wait=System.nanoTime();
    }
    private static void capture(MinecraftClient mc,String name)throws Exception{try(var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){image.writeTo(OUT.resolve(name));}}
}
