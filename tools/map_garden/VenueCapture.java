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
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Disposable native integration mod; excluded from the production JAR. */
public final class VenueCapture implements ClientModInitializer {
    private static final Path OUT=Path.of(System.getProperty("botand.venue.proof"));
    private static final String[] NAMES={"shop-exterior","shop-interior","cafe-exterior","cafe-interior","cafe-evening"};
    private static final double[][] VIEWS={
        {-4,88,-78,-6,87,-100,70,1000},
        {-5,84,-97,-6,83,-116,80,1000},
        {-32,89,62,-51,87,44,63,1000},
        {-50,83,47,-55,83,42,85,1000},
        {-33,86,56,-51,86,44,65,13000}
    };
    private GardenPatch patch;private BlockState[] states;private BlockPos custom,container;
    private final Map<BlockPos,net.minecraft.nbt.NbtCompound> containers=new HashMap<>();
    private volatile Throwable failure;private volatile boolean verified,restarted;
    private long started=System.nanoTime(),wait;private int phase,shot;private ArmorStandEntity camera;
    public void onInitializeClient(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try{
            Files.createDirectories(OUT);
            patch=GardenPatch.read(getClass().getResourceAsStream("/data/celestialfall/map/azure_venues.bgp.gz"));
            states=new BlockState[patch.palette.length];
            for(int i=0;i<states.length;i++)states[i]=BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(),patch.palette[i],false).blockState();
            var w=server.getOverworld();custom=new BlockPos(patch.x(0),patch.y(0),patch.z(0));
            w.setBlockState(custom,Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            container=new BlockPos(patch.x(1),patch.y(1),patch.z(1));
            w.setBlockState(container,Blocks.CHEST.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            Set<Long> chunks=new HashSet<>();
            for(int i=0;i<patch.count;i++)if(chunks.add(ChunkPos.toLong(patch.x(i)>>4,patch.z(i)>>4)))w.getChunk(patch.x(i)>>4,patch.z(i)>>4);
            for(var pos:List.of(container,new BlockPos(-50,81,-123),new BlockPos(38,81,-19)))containers.put(pos,w.getBlockEntity(pos).createNbt());
            w.setTimeOfDay(1000);w.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
        }catch(Throwable t){failure=t;}});
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(failure!=null)throw new IllegalStateException("Venue verification failed",failure);
            if((System.nanoTime()-started)/1e9>500)throw new IllegalStateException("Timeout phase "+phase);
            if(phase==0&&mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-started)/1e9>5){
                Files.createDirectories(OUT);mc.options.getViewDistance().setValue(14);mc.options.getGuiScale().setValue(2);
                mc.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
                mc.createIntegratedServerLoader().start(new TitleScreen(),System.getProperty("botand.garden.world"));phase=1;return;
            }
            if(phase==1&&mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);phase=2;return;}
            if(phase==2&&mc.currentScreen==null&&ProgressClient.ready){
                if(!MapClient.enabled())throw new IllegalStateException("Map not recognized");wait=System.nanoTime();phase=3;
            }
            if(phase==3&&!verified&&(System.nanoTime()-wait)/1e9>2){
                wait=System.nanoTime();mc.getServer().execute(()->{try{if(VenueRenovation.remaining(mc.getServer())==0)verify(mc.getServer());}catch(Throwable t){failure=t;}});
            }
            if(phase==3&&verified){
                camera=new ArmorStandEntity(mc.world,0,0,0);camera.setInvisible(true);camera.setNoGravity(true);mc.setCameraEntity(camera);
                mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);mc.options.hudHidden=true;mc.options.debugEnabled=false;
                setView(mc);phase=4;
            }
            if(phase==4&&(System.nanoTime()-wait)/1e9>9){
                try(var im=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){im.writeTo(OUT.resolve(NAMES[shot]+".png"));}
                if(++shot<VIEWS.length){setView(mc);return;}
                Files.writeString(OUT.resolve("client-result.txt"),"PASS 2.15.0: existing 2.14.1 world automatic venue renovation; all "+patch.count+" cells checked; custom block and 3 containers preserved; rerun idempotent; five native screenshots.\n");
                mc.setCameraEntity(mc.player);mc.scheduleStop();phase=5;
            }
        }catch(Throwable t){failure=t;t.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("client-error.txt"),t.toString());}catch(Exception ignored){}mc.scheduleStop();}});
    }
    private void setView(MinecraftClient mc){
        var v=VIEWS[shot];Vec3d eye=new Vec3d(v[0],v[1],v[2]),d=new Vec3d(v[3],v[4],v[5]).subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)Math.toDegrees(-Math.atan2(d.y,Math.hypot(d.x,d.z)));
        camera.refreshPositionAndAngles(eye.x,eye.y-camera.getStandingEyeHeight(),eye.z,yaw,pitch);
        camera.prevYaw=yaw;camera.prevPitch=pitch;camera.setHeadYaw(yaw);camera.prevHeadYaw=yaw;camera.setBodyYaw(yaw);camera.prevBodyYaw=yaw;
        mc.options.getFov().setValue((int)v[6]);mc.getServer().execute(()->mc.getServer().getOverworld().setTimeOfDay((long)v[7]));wait=System.nanoTime();
    }
    private void verify(MinecraftServer server)throws Exception{
        var w=server.getOverworld();int bad=0;String first="";
        for(int i=0;i<patch.count;i++){
            var p=new BlockPos(patch.x(i),patch.y(i),patch.z(i));var expected=p.equals(custom)?Blocks.DIAMOND_BLOCK.getDefaultState():p.equals(container)?Blocks.CHEST.getDefaultState():states[patch.after(i)];
            if(w.getBlockState(p)!=expected){bad++;if(bad<9)first+=p+"="+w.getBlockState(p)+" expected="+expected+"; ";}
        }
        if(bad!=0)throw new IllegalStateException(bad+" mismatches: "+first);
        for(var e:containers.entrySet())if(!e.getValue().equals(w.getBlockEntity(e.getKey()).createNbt()))throw new IllegalStateException("Container changed");
        for(int z=43;z<=45;z++)for(int x=-48;x<=-45;x++)for(int y=81;y<=83;y++)if(!w.getBlockState(new BlockPos(x,y,z)).isAir())throw new IllegalStateException("Cafe entry blocked");
        for(int x=-7;x<=-5;x++)for(int z=-94;z<=-89;z++)for(int y=81;y<=83;y++)if(!w.getBlockState(new BlockPos(x,y,z)).isAir())throw new IllegalStateException("Shop entry blocked");
        if(!restarted){
            if(server.getCommandManager().executeWithPrefix(server.getCommandSource().withWorld(w),"botand map venues")!=1)throw new IllegalStateException("Command failed");
            restarted=true;return;
        }
        w.getPersistentStateManager().save();
        Files.writeString(OUT.resolve("server-result.txt"),"PASS cells="+patch.count+" mismatches=0 custom=preserved containers=3 entrances=clear auto_update=true manual_command=true\n");verified=true;
    }
}
