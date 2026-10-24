package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.title.*;
import dev.sangwoo.celestialfall.map.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.*;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Disposable integration mod, never included in the production JAR. */
public final class FloorRepairCapture implements ClientModInitializer {
    private static final Path OUT=Path.of(System.getProperty("botand.floor.proof"));
    private static final BlockPos PARK=new BlockPos(38,80,43);
    private GardenPatch patch;private BlockPos custom;private final Map<BlockPos,net.minecraft.nbt.NbtCompound> containers=new HashMap<>();
    private volatile Throwable failure;private volatile boolean verified,restarted;
    private long started=System.nanoTime(),wait;private int phase;private ArmorStandEntity camera;
    public void onInitializeClient(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try{
            patch=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_garden_floor_fix.bgp.gz"));
            var w=server.getOverworld();custom=new BlockPos(patch.x(0),80,patch.z(0));
            if(!w.getBlockState(custom).isOf(Blocks.QUARTZ_BRICKS)||!w.getBlockState(PARK).isOf(Blocks.QUARTZ_BRICKS))throw new IllegalStateException("Wrong 2.14.0 fixtures");
            w.setBlockState(custom,Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            // The harness visits every affected chunk; production repair never forces loads.
            Set<Long> chunks=new HashSet<>();
            for(int i=0;i<patch.count;i++)if(chunks.add(ChunkPos.toLong(patch.x(i)>>4,patch.z(i)>>4)))w.getChunk(patch.x(i)>>4,patch.z(i)>>4);
            for(int[] p:new int[][]{{-50,81,-123},{38,81,-19}}){var pos=new BlockPos(p[0],p[1],p[2]);containers.put(pos,w.getBlockEntity(pos).createNbt());}
        }catch(Throwable t){failure=t;}});
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(failure!=null)throw new IllegalStateException("Floor verification failed",failure);
            if((System.nanoTime()-started)/1e9>500)throw new IllegalStateException("Timeout phase "+phase);
            if(phase==0&&mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-started)/1e9>5){
                Files.createDirectories(OUT);mc.options.getViewDistance().setValue(16);mc.options.getGuiScale().setValue(2);
                mc.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
                mc.createIntegratedServerLoader().start(new TitleScreen(),System.getProperty("botand.garden.world"));phase=1;return;
            }
            if(phase==1&&mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);phase=2;return;}
            if(phase==2&&mc.currentScreen==null&&ProgressClient.ready){
                if(!MapClient.enabled())throw new IllegalStateException("Map not recognized");wait=System.nanoTime();phase=3;
            }
            if(phase==3&&(System.nanoTime()-wait)/1e9>20){
                mc.getServer().execute(()->{try{verify(mc.getServer());}catch(Throwable t){failure=t;}});phase=4;return;
            }
            if(phase==4&&!verified&&(System.nanoTime()-wait)/1e9>1){
                wait=System.nanoTime();mc.getServer().execute(()->{try{if(FloorRepair.remaining(mc.getServer())==0)verify(mc.getServer());}catch(Throwable t){failure=t;}});
            }
            if(phase==4&&verified){
                camera=new ArmorStandEntity(mc.world,0,0,0);camera.setInvisible(true);camera.setNoGravity(true);mc.setCameraEntity(camera);
                mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);mc.options.hudHidden=true;
                Vec3d eye=new Vec3d(39,84,-16),target=new Vec3d(28,80,-23),d=target.subtract(eye);
                float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)Math.toDegrees(-Math.atan2(d.y,Math.hypot(d.x,d.z)));
                camera.refreshPositionAndAngles(eye.x,eye.y-camera.getStandingEyeHeight(),eye.z,yaw,pitch);
                camera.prevYaw=yaw;camera.prevPitch=pitch;camera.setHeadYaw(yaw);camera.prevHeadYaw=yaw;camera.setBodyYaw(yaw);camera.prevBodyYaw=yaw;
                mc.options.getFov().setValue(70);mc.options.debugEnabled=false;wait=System.nanoTime();phase=5;
            }
            if(phase==5&&(System.nanoTime()-wait)/1e9>6){
                try(var im=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){im.writeTo(OUT.resolve("floor-fixed.png"));}
                Files.writeString(OUT.resolve("client-result.txt"),"PASS 2.14.1: actual 2.14.0 world automatic repair; all "+patch.count+" coordinates checked; user block and 2 containers preserved; intentional park quartz unchanged; rerun idempotent; native screenshot.\n");
                mc.setCameraEntity(mc.player);mc.scheduleStop();phase=6;
            }
        }catch(Throwable t){failure=t;t.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("client-error.txt"),t.toString());}catch(Exception ignored){}mc.scheduleStop();}});
    }
    private void verify(MinecraftServer server)throws Exception{
        var w=server.getOverworld();int bad=0;String first="";
        for(int i=0;i<patch.count;i++){
            var p=new BlockPos(patch.x(i),80,patch.z(i));var expected=p.equals(custom)?Blocks.DIAMOND_BLOCK:Blocks.POLISHED_ANDESITE;
            if(!w.getBlockState(p).isOf(expected)){bad++;if(bad<6)first+=p+"="+w.getBlockState(p)+"; ";}
        }
        if(bad!=0)throw new IllegalStateException(bad+" mismatches; pending="+FloorRepair.remaining(server)+" "+first);
        if(!w.getBlockState(PARK).isOf(Blocks.QUARTZ_BRICKS))throw new IllegalStateException("Park changed");
        for(var e:containers.entrySet())if(!e.getValue().equals(w.getBlockEntity(e.getKey()).createNbt()))throw new IllegalStateException("Container changed");
        if(!restarted){
            int result=server.getCommandManager().executeWithPrefix(server.getCommandSource().withWorld(w),"botand map fixfloor");
            if(result!=1)throw new IllegalStateException("Repair command failed");restarted=true;return;
        }
        Files.writeString(OUT.resolve("server-result.txt"),"PASS cells="+patch.count+" mismatches=0 custom=preserved containers=2 park_quartz=preserved auto_repair=true manual_command=true\n");verified=true;
    }
}
