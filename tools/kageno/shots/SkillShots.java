package dev.sangwoo.botandshots;

import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.stand.*;
import dev.sangwoo.celestialfall.training.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.lwjgl.glfw.GLFW.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/**
 * Visual QA only: creates a flat world, casts each Kageno skill on the integrated server
 * and saves every rendered frame together with the cast clock. Not shipped in the mod jar.
 * -Dbotand.shots.out=DIR  -Dbotand.shots.list=M1,E,R,...  -Dbotand.shots.slow=N
 */
public final class SkillShots implements ClientModInitializer {
    static SkillShots instance;
    static final Path OUT=Path.of(System.getProperty("botand.shots.out","shots"));
    static final int SLOW=Integer.getInteger("botand.shots.slow",1);
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"shot-writer");t.setDaemon(true);return t;});
    private final List<Future<?>> jobs=new ArrayList<>();
    private MinecraftClient mc;private int phase,age,shotIndex;private long started=System.nanoTime();
    private final Deque<String> queue=new ArrayDeque<>(Arrays.asList(System.getProperty("botand.shots.list","F,M1,E,R,T,RUSH,Y,Q,H,G").split(",")));
    private String current;private Map<String,String> opts=new HashMap<>();private long castTick;private int dummyId=-1;private final StringBuilder log=new StringBuilder();
    private boolean slowed;
    private float camYaw=Float.parseFloat(System.getProperty("botand.shots.yaw","145")),camPitch=12,camDist=Float.parseFloat(System.getProperty("botand.shots.dist","6.5"));

    public static void afterFrame(){if(instance!=null)try{instance.frame();}catch(Throwable t){instance.fail(t);}}
    private void fail(Throwable t){t.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("error.txt"),phase+" "+current+" "+t+"\n"+log);}catch(Exception ignored){}mc.scheduleStop();phase=99;}
    private void server(Consumer<ServerPlayerEntity> f){mc.getServer().submit(()->f.accept(mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid()))).join();}
    private void key(int key,boolean down){mc.keyboard.onKey(mc.getWindow().getHandle(),key,0,down?GLFW_PRESS:GLFW_RELEASE,0);}
    /** The client's own clock of the newest local cast (falls back to the trigger tick). */
    private float clock(){
        float delta=mc.getTickDelta();
        if("Q".equals(current))return (mc.world.getTime()-castTick)+delta;
        if("G".equals(current)){for(var c:AtomicClient.CASTS.values())if(c.caster.equals(mc.player.getUuid()))return c.age(delta);}
        else{StandClient.Cast best=null;for(var c:StandClient.CASTS.values())if(c.caster().equals(mc.player.getUuid())&&(best==null||c.start()>best.start()))best=c;if(best!=null&&mc.world.getTime()-castTick<400)return best.age(delta);}
        return (mc.world.getTime()-castTick)+delta;
    }
    private int skill(String name){return switch(name){case "M1"->M1;case "E"->E;case "R"->R;case "T"->T;case "RUSH"->RUSH;case "Y"->Y;case "Q"->Q;case "H"->H;case "G"->G;default->-1;};}
    private int length(String name){if(opts.containsKey("len"))return (int)opt("len",60);return switch(name){case "F"->50;case "M1"->75;case "Q"->140;case "P"->390;case "G"->560;case "T"->110;case "RUSH"->90;default->DURATION[skill(name)]+20;};}

    private void frame()throws Exception{
        if(phase!=4||current==null)return;
        float t=clock();if(t<-2||t>length(current))return;
        var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
        String name=String.format(Locale.ROOT,"%s/%05d_%07.2f.png",current,shotIndex++,t);
        jobs.add(writer.submit(()->{try(image){Path p=OUT.resolve(name);Files.createDirectories(p.getParent());image.writeTo(p);}catch(Exception e){throw new RuntimeException(e);}}));
    }
    private void setup(ServerPlayerEntity p){
        var w=p.getServerWorld();MapGate.designate(w,true);
        w.setTimeOfDay(6000);w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,w.getServer());w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,w.getServer());
        w.setWeather(999999,0,false,false);p.getServer().setPvpEnabled(true);p.changeGameMode(GameMode.SURVIVAL);
        int y=p.getBlockY()-1;
        for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++){w.setBlockState(new BlockPos(x,y,z),((x>>2)+(z>>2)&1)==0?Blocks.LIGHT_GRAY_CONCRETE.getDefaultState():Blocks.POLISHED_ANDESITE.getDefaultState());}
        for(int x=-14;x<=14;x+=7){for(int h=0;h<12;h++)w.setBlockState(new BlockPos(x,y+1+h,-26),Blocks.LIGHT_BLUE_TERRACOTTA.getDefaultState());}
        ProgressManager.set(p,new ProgressProfile(1,0,0,"kageno",10,0));NoCooldownState.get(p.getServer()).set(p.getUuid(),true);
        p.getInventory().clear();p.currentScreenHandler.syncState();
        var dummy=TrainingDummies.TYPES.values().iterator().next().create(w);dummy.refreshPositionAndAngles(.5,y+1,3.5,180,0);w.spawnEntity(dummy);dummyId=dummy.getId();
        log.append("ground ").append(y).append('\n');
    }
    private void reset(ServerPlayerEntity p){
        KagenoManager.clear(p.getServer());AtomicManager.clear(p.getServer());
        int y=p.getServerWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,0,0);
        float yaw=facing(opt("face",0));
        p.networkHandler.requestTeleport(.5,y,.5,yaw,0);p.setYaw(yaw);p.setHeadYaw(yaw);p.setBodyYaw(yaw);p.setHealth(p.getMaxHealth());p.setVelocity(0,0,0);
        var d=p.getServerWorld().getEntityById(dummyId);
        if(!(d instanceof net.minecraft.entity.LivingEntity)||!d.isAlive()){
            var dummy=TrainingDummies.TYPES.values().iterator().next().create(p.getServerWorld());dummy.refreshPositionAndAngles(.5,y,3.5,180,0);p.getServerWorld().spawnEntity(dummy);dummyId=dummy.getId();d=dummy;
        }
        if(d instanceof net.minecraft.entity.LivingEntity l){
            var f=net.minecraft.util.math.Vec3d.fromPolar(0,yaw);var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
            boolean show=opt("dummy",1)>0;var at=new net.minecraft.util.math.Vec3d(.5,y,.5).add(f.multiply(opt("dfw",3))).add(right.multiply(opt("drt",0)));
            // Hidden dummies wait far to the side (below the floor they would fall out of the world).
            l.setHealth(l.getMaxHealth());l.refreshPositionAndAngles(show?at.x:at.x+70,y,at.z,yaw+180,0);l.setVelocity(0,0,0);
        }
    }
    private float opt(String key,float fallback){var v=opts.get(key);return v==null?fallback:Float.parseFloat(v);}
    /** Camera looks along camyaw (default north); the character's facing is given relative to it. */
    private float camLook(){return opt("camyaw",180);}
    private float facing(float face){return camLook()+180+face;}
    private void camera(){float d=opt("dist",camDist);BotandCamera.STATE.yaw=camLook();BotandCamera.STATE.pitch=opt("pitch",camPitch);BotandCamera.STATE.targetDistance=d;BotandCamera.STATE.distance=d;BotandCamera.STATE.collisionDistance=d;}
    private void face(ServerPlayerEntity p,float face){float y=facing(face);p.setYaw(y);p.setHeadYaw(y);p.setBodyYaw(y);p.networkHandler.requestTeleport(p.getX(),p.getY(),p.getZ(),y,0);}

    @Override public void onInitializeClient(){
        instance=this;
        ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{tick();}catch(Throwable t){fail(t);}});
    }
    private void tick()throws Exception{
        age++;if((System.nanoTime()-started)/1e9>1500*SLOW)throw new IllegalStateException("timeout");
        if(mc.getToastManager()!=null)mc.getToastManager().clear();
        if(phase>=1&&phase<99&&mc.world!=null&&mc.currentScreen instanceof dev.sangwoo.celestialfall.client.title.BotandTitleScreen)mc.setScreen(null);
        if(SLOW>1&&!slowed&&phase>=3){var f=MinecraftClient.class.getDeclaredField("renderTickCounter");f.setAccessible(true);var counter=f.get(mc);var t=counter.getClass().getDeclaredField("tickTime");t.setAccessible(true);t.setFloat(counter,50f*SLOW);slowed=true;}
        switch(phase){
            case 0->{if(mc.currentScreen!=null&&mc.getOverlay()==null&&age>100){
                Files.createDirectories(OUT);var o=mc.options;o.getViewDistance().setValue(6);o.getMaxFps().setValue(260);o.getGuiScale().setValue(2);o.hudHidden=true;o.getBobView().setValue(false);
                mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                var info=new LevelInfo("ShotsFlat"+System.nanoTime(),GameMode.CREATIVE,false,Difficulty.EASY,true,new GameRules(),DataConfiguration.SAFE_MODE);
                mc.createIntegratedServerLoader().createAndStart(info.getLevelName(),info,new GeneratorOptions(1,false,false),r->r.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder());
                phase=1;age=0;}}
            case 1->{
                if(mc.currentScreen instanceof dev.sangwoo.celestialfall.client.title.BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);}
                if(mc.player!=null&&mc.world!=null&&mc.currentScreen==null&&age>60){server(this::setup);phase=2;age=0;}}
            case 2->{if(age>60&&StandClient.visible(mc.player)){phase=3;age=0;}else if(age>400)throw new IllegalStateException("stand never visible, map="+MapClient.enabled());}
            case 3->{ // next skill
                if(age==1){if(queue.isEmpty()){for(var j:jobs)j.get();Files.writeString(OUT.resolve("log.txt"),log);mc.scheduleStop();phase=99;return;}String entry=queue.poll();String[] parts=entry.split(";");current=parts[0];opts.clear();for(int i=1;i<parts.length;i++){String[] kv=parts[i].split("=",2);opts.put(kv[0],kv.length>1?kv[1]:"1");}server(this::reset);}
                if(age==2)camera();
                if(age>=30){camera();castTick=mc.world.getTime()+1;shotIndex=0;trigger(current);phase=4;age=0;}}
            case 4->{
                if(current.equals("M1")){
                    String[] at=opts.getOrDefault("hits","0/13/26/39").split("[,/]");String[] faces=opts.getOrDefault("faces","").split("[,/]");
                    for(int i=1;i<at.length;i++)if(age==Integer.parseInt(at[i].trim())){final int hit=i;server(p->{if(faces.length>hit&&!faces[hit].isBlank())face(p,Float.parseFloat(faces[hit]));KagenoManager.input(p,M1);});}
                }
                if(current.equals("RUSH")&&age<14)server(p->KagenoManager.input(p,KagenoRules.PULSE));
                // Follow-up inputs during the shot, "then=M1@60/E@90".
                if(opts.containsKey("then"))for(String step:opts.get("then").split("/")){String[] sa=step.split("@");if(sa.length==2&&age==Integer.parseInt(sa[1].trim())){int id=skill(sa[0].trim());server(p->KagenoManager.input(p,id));}}
                if(current.equals("F")&&age==40)key(GLFW_KEY_F,false);
                if(!current.equals("H"))camera();
                if(age>length(current)+4){log.append(current).append(" frames ").append(shotIndex).append('\n');if(current.equals("Q"))server(p->{});phase=3;age=0;}}
        }
    }
    private void trigger(String name){
        log.append(name).append(" at ").append(mc.world.getTime()).append('\n');
        switch(name){
            case "F"->key(GLFW_KEY_F,true);
            case "RUSH"->server(p->KagenoManager.input(p,T_DOWN));
            case "T"->server(p->{KagenoManager.input(p,T_DOWN);KagenoManager.input(p,T_UP);});
            case "P"->server(p->{if(!KagenoManager.input(p,POSE))log.append("  rejected P\n");});
            default->server(p->{if(!KagenoManager.input(p,skill(name)))log.append("  rejected ").append(name).append('\n');});
        }
    }
}
