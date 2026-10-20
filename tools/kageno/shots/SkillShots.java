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
 *
 * <p>Actors for G: "act=NAME:DIST:YAW:SPEED:START+..." puts a husk DIST blocks from the cast spot towards YAW
 * (Minecraft yaw: 0 south, 90 west, 180 north, 270 east). From START ("impact", "held" = once G's column holds it,
 * or a cast age in ticks) it runs straight away from the spot at SPEED blocks per second on the ground, through the
 * game's own movement (so G's hold slows it), stopping 60 blocks out. "track=NAME:MODE:DIST:UP:SIDE[:FOV]" films it
 * with a camera that follows it: MODE "ahead" (DIST blocks further out and SIDE to the side, looking back at it with the
 * pillar behind), "front" (the same, with G's hit view going by the actor's spot), "side" (DIST blocks to its side and
 * SIDE behind) or "behind" (DIST blocks behind it like a player's own third-person camera; G's hit view going by the
 * actor's spot). Every tick of a shot with actors goes to
 * NAME/actors.csv. Fixed and following cameras are onlookers: G's hit view goes by where they are (SkillShotsObserverMixin).
 * "shake=1" gives those cameras the Atomic camera shake a player standing there would get (quake, jolt, the shake
 * next to the pillar's wall) and logs every frame of G to NAME/shake.csv: cast age in ticks, blocks from the
 * camera to the pillar's wall (negative inside), the wall shake amplitude and the applied yaw and pitch in degrees.
 *
 * <p>"orbit=R/UP/DEGPS[/START/FW/RT/CY]" films with a camera circling the cast: R blocks from a centre FW forward and RT
 * right of the cast spot and CY up, UP above the ground, turning DEGPS degrees per second of the cast clock from START
 * (0 behind the caster, 90 to the caster's right). It shows effects from changing angles (3D shapes stay put in the world).
 *
 * <p>"walk=N" holds W (the camera-relative walk) from N ticks before the cast until the shot ends: R's walking cast
 * with "face=180" (camera behind, so W walks the way the caster faces).
 *
 * <p>"GDUMMY" runs "/botand dummy atomic" (2.38.2): a dummy "adist" blocks ahead (60 by default) casts Atomic and the
 * player's own camera watches it from outside; its clock is that cast's.
 *
 * <p>Every shot also logs each client tick to NAME/motion.csv (cast clock in ticks, the player's position, the lens's
 * target and shown FOV multipliers), and an M1 shot logs to NAME/sound.csv the cast clock at which each hit's sound
 * starts playing in the game's sound system.
 *
 * <p>"hud=1" films with the game's HUD shown (2.42.0: the cooldown list and the danger banner); "cool=1" gives the
 * shot real cooldowns (the capture otherwise casts without them, so the list would show nothing for Atomic). "HUD" casts
 * nothing itself: its "then=" steps cast one skill after another (T as "T@n/RUSH@n+1", a press and a release) and its
 * clock is the shot's own; every step that the game refuses is logged. T held needs the key's pulses as the client sends
 * them while it is down ("T@n/PULSE@n+3/PULSE@n+6/PULSE@n+9": T held begins 8 ticks after the press). "DASH@n" presses
 * the dash key (C) for a tick. Every cast of the player's (with its combo hit), every dash and every change of the
 * cooldown list's boxes ("list [newest, ...] at TICK") go to the log.
 *
 * <p>"BASE" films the plain body instead of a skill: a jump at 0.5 s and 2 s (airborne pose, landing dip), a blow
 * at 3.5 s and 4.25 s (flinch), then an M1 at 5 s (the crossfade into a skill and out of it). Its clock is the
 * shot's own (seconds from the start).
 */
public final class SkillShots implements ClientModInitializer {
    static SkillShots instance;
    static final Path OUT=Path.of(System.getProperty("botand.shots.out","shots"));
    static final int SLOW=Integer.getInteger("botand.shots.slow",1);
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"shot-writer");t.setDaemon(true);return t;});
    private final List<Future<?>> jobs=new ArrayList<>();
    private MinecraftClient mc;private int phase,age,shotIndex;private long started=System.nanoTime();
    private final Deque<String> queue=new ArrayDeque<>(Arrays.asList(System.getProperty("botand.shots.list","F,M1,E,R,T,RUSH,Y,Q,H,G").split(",")));
    private String current,folder;private Map<String,String> opts=new HashMap<>();private long castTick;private int dummyId=-1;private final StringBuilder log=new StringBuilder();
    private final Map<String,Integer> repeats=new HashMap<>();
    private boolean slowed;
    private float camYaw=Float.parseFloat(System.getProperty("botand.shots.yaw","145")),camPitch=12,camDist=Float.parseFloat(System.getProperty("botand.shots.dist","6.5"));
    private net.minecraft.util.math.Vec3d castPos=net.minecraft.util.math.Vec3d.ZERO;private static volatile double[] far,orbit;private float castYaw;
    private record Actor(String name,double dist,float yaw,double speed,String start){
        net.minecraft.util.math.Vec3d dir(){double r=Math.toRadians(yaw);return new net.minecraft.util.math.Vec3d(-Math.sin(r),0,Math.cos(r));}
    }
    private final List<Actor> actors=new ArrayList<>();
    private static final Map<String,Integer> ACTOR_IDS=new ConcurrentHashMap<>();private static final Map<String,Actor> ACTORS=new ConcurrentHashMap<>();
    private static volatile String[] track;private static volatile boolean shake;private final StringBuilder shakeLog=new StringBuilder();
    private volatile boolean filming;private volatile long serverStart=-1;private volatile net.minecraft.util.math.Vec3d spot=net.minecraft.util.math.Vec3d.ZERO;
    private final Set<String> moving=new HashSet<>();private final StringBuilder actorLog=new StringBuilder();
    private final StringBuilder motionLog=new StringBuilder(),soundLog=new StringBuilder();private final Set<UUID> sounded=new HashSet<>();

    /** Fixed far camera of the current entry ("far=ex/ey/ez/tx/ty/tz[/fov]" in blocks from the cast spot), or null. */
    public static dev.sangwoo.celestialfall.client.CinematicDirector.Pose farPose(){
        var tracked=trackPose();if(tracked!=null)return tracked;
        var circling=orbitPose();if(circling!=null)return circling;
        double[] f=far;var self=instance;if(f==null||f.length<6||self==null||self.phase<3)return null;
        var o=self.castPos;var eye=o.add(f[0],f[1],f[2]);var d=o.add(f[3],f[4],f[5]).subtract(eye).normalize();
        return new dev.sangwoo.celestialfall.client.CinematicDirector.Pose(eye,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.asin(d.y)),0);
    }
    /** Whether the onlooker camera of this entry shakes like a player standing there ("shake=1"). */
    public static boolean shake(){var self=instance;return shake&&self!=null&&self.phase>=3;}
    public static double farFov(){
        String[] t=track;if(t!=null&&instance!=null&&instance.phase>=3)return t.length>5?Double.parseDouble(t[5]):70;
        if(orbit!=null&&instance!=null&&instance.phase>=3)return 70;
        double[] f=far;return f==null||instance==null||instance.phase<3?0:f.length>6?f[6]:60;
    }
    /**
     * Where G's hit view is decided for the onlooker camera: the tracked actor's own spot when the camera stands for that
     * actor's view ("behind", "front": the screen shows the hit view exactly while the actor is held), otherwise null
     * (the camera's spot).
     */
    public static net.minecraft.util.math.Vec3d viewer(){
        String[] t=track;var self=instance;if(t==null||self==null||self.phase<3||!(t[1].equals("behind")||t[1].equals("front")))return null;
        var mc=MinecraftClient.getInstance();Integer id=ACTOR_IDS.get(t[0]);var e=id==null||mc.world==null?null:mc.world.getEntityById(id);
        return e==null?null:e.getLerpedPos(mc.getTickDelta());
    }
    /** The camera circling the cast ("orbit=..."), at its angle on this frame of the cast clock. */
    private static dev.sangwoo.celestialfall.client.CinematicDirector.Pose orbitPose(){
        double[] o=orbit;var self=instance;if(o==null||o.length<3||self==null||self.phase<3)return null;
        double r=o[0],up=o[1],speed=o[2],start=o.length>3?o[3]:0,fw=o.length>4?o[4]:0,rt=o.length>5?o[5]:0,cy=o.length>6?o[6]:1;
        var f=net.minecraft.util.math.Vec3d.fromPolar(0,self.castYaw);var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
        var centre=self.castPos.add(f.multiply(fw)).add(right.multiply(rt)).add(0,cy,0);
        double a=Math.toRadians(start+speed*Math.max(0,self.phase==4?self.clock():0)/20);
        var eye=centre.add(f.multiply(-Math.cos(a)*r)).add(right.multiply(Math.sin(a)*r)).add(0,up-cy,0);
        var v=centre.subtract(eye).normalize();
        return new dev.sangwoo.celestialfall.client.CinematicDirector.Pose(eye,(float)Math.toDegrees(Math.atan2(-v.x,v.z)),(float)-Math.toDegrees(Math.asin(v.y)),0);
    }
    /** The camera following an actor ("track=..."), from its interpolated position on this frame. */
    private static dev.sangwoo.celestialfall.client.CinematicDirector.Pose trackPose(){
        String[] t=track;var self=instance;if(t==null||self==null||self.phase<3)return null;
        var mc=MinecraftClient.getInstance();Integer id=ACTOR_IDS.get(t[0]);Actor a=ACTORS.get(t[0]);
        var e=id==null||mc.world==null?null:mc.world.getEntityById(id);if(e==null||a==null)return null;
        var p=e.getLerpedPos(mc.getTickDelta());var dir=a.dir();var side=new net.minecraft.util.math.Vec3d(-dir.z,0,dir.x);
        double d=Double.parseDouble(t[2]),up=Double.parseDouble(t[3]),s=Double.parseDouble(t[4]);
        net.minecraft.util.math.Vec3d eye,look;
        if(t[1].equals("side")){eye=p.add(side.multiply(d)).add(dir.multiply(-s)).add(0,up,0);look=p.add(dir.multiply(1.5)).add(0,1.1,0);}
        else if(t[1].equals("behind")){eye=p.add(dir.multiply(-d)).add(side.multiply(s)).add(0,up,0);look=p.add(dir.multiply(3)).add(0,.9,0);}
        else{eye=p.add(dir.multiply(d)).add(side.multiply(s)).add(0,up,0);look=p.add(0,1.1,0);}
        var v=look.subtract(eye).normalize();
        return new dev.sangwoo.celestialfall.client.CinematicDirector.Pose(eye,(float)Math.toDegrees(Math.atan2(-v.x,v.z)),(float)-Math.toDegrees(Math.asin(v.y)),0);
    }

    public static void afterFrame(){if(instance!=null)try{instance.frame();}catch(Throwable t){instance.fail(t);}}
    private void fail(Throwable t){t.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("error.txt"),phase+" "+current+" "+t+"\n"+log);}catch(Exception ignored){}mc.scheduleStop();phase=99;}
    private void server(Consumer<ServerPlayerEntity> f){mc.getServer().submit(()->f.accept(mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid()))).join();}
    private void key(int key,boolean down){mc.keyboard.onKey(mc.getWindow().getHandle(),key,0,down?GLFW_PRESS:GLFW_RELEASE,0);}
    /** The client's own clock of the newest local cast (falls back to the trigger tick). */
    private float clock(){
        float delta=mc.getTickDelta();
        if("Q".equals(current)||"M1".equals(current)||"BASE".equals(current)||"HUD".equals(current))return (mc.world.getTime()-castTick)+delta;
        if("G".equals(current)){for(var c:AtomicClient.CASTS.values())if(c.caster.equals(mc.player.getUuid()))return c.age(delta);}
        else if("GDUMMY".equals(current)){for(var c:AtomicClient.CASTS.values())return c.age(delta);}
        else{StandClient.Cast best=null;for(var c:StandClient.CASTS.values())if(c.caster().equals(mc.player.getUuid())&&(best==null||c.start()>best.start()))best=c;if(best!=null&&mc.world.getTime()-castTick<400)return best.age(delta);}
        return (mc.world.getTime()-castTick)+delta;
    }
    private int skill(String name){return switch(name){case "M1"->M1;case "E"->E;case "R"->R;case "T"->T;case "RUSH"->RUSH;case "Y"->Y;case "Q"->Q;case "H"->H;case "G"->G;case "PULSE"->PULSE;default->-1;};}
    private int length(String name){if(opts.containsKey("len"))return (int)opt("len",60);return switch(name){case "BASE"->150;case "HUD"->400;case "F"->50;case "M1"->75;case "Q"->140;case "P"->390;case "G"->560;case "T"->110;case "RUSH"->90;case "GDUMMY"->560;default->DURATION[skill(name)]+20;};}
    private final Set<UUID> castsLogged=new HashSet<>();private long dashLogged=-1000;private List<String> listLogged=List.of();
    /** One row of motion.csv for this client tick, and for M1 the hits whose sound has just started (sound.csv). */
    private void logTick()throws Exception{
        // every cast of the player's (with its combo hit) and every dash, as the client hears of them (the HUD shots' captions)
        for(var c:StandClient.CASTS.values())if(c.caster().equals(mc.player.getUuid())&&castsLogged.add(c.id()))
            log.append("  cast ").append(c.skill()).append(" combo ").append(c.combo()).append(" at ").append(c.start()).append('\n');
        long dash=CombatClient.snapshot(mc.player.getUuid()).dashStart();
        if(dash!=dashLogged&&dash>0){if(dashLogged!=-1000||mc.world.getTime()-dash<40)log.append("  dash at ").append(dash).append('\n');dashLogged=dash;}
        var shown=CooldownHud.shown();if(!shown.equals(listLogged)){log.append("  list ").append(shown).append(" at ").append(mc.world.getTime()).append('\n');listLogged=shown;}
        var gr=mc.gameRenderer;var f=net.minecraft.client.render.GameRenderer.class.getDeclaredField("fovMultiplier");f.setAccessible(true);
        var p=mc.player;motionLog.append(String.format(Locale.ROOT,"%.3f,%.4f,%.4f,%.4f,%.2f,%.4f,%.4f%n",clock()-mc.getTickDelta(),p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getFovMultiplier(),f.getFloat(gr)));
        if(!"M1".equals(current))return;
        var field=Class.forName("dev.sangwoo.celestialfall.client.KagenoAudio").getDeclaredField("PLAYING");field.setAccessible(true);
        for(var e:((Map<UUID,net.minecraft.client.sound.SoundInstance>)field.get(null)).entrySet()){
            if(sounded.contains(e.getKey())||!mc.getSoundManager().isPlaying(e.getValue()))continue;
            var c=StandClient.CASTS.get(e.getKey());if(c==null)continue;sounded.add(e.getKey());
            soundLog.append(String.format(Locale.ROOT,"%d,%.3f,%.3f%n",c.combo(),c.age(0),clock()-mc.getTickDelta()));
        }
    }

    private void frame()throws Exception{
        if(phase!=4||current==null)return;
        float t=clock();if(t<-2||t>length(current))return;
        if(shake&&"G".equals(current))logShake(t);
        var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
        String name=String.format(Locale.ROOT,"%s/%05d_%07.2f.png",folder,shotIndex++,t);
        jobs.add(writer.submit(()->{try(image){Path p=OUT.resolve(name);Files.createDirectories(p.getParent());image.writeTo(p);}catch(Exception e){throw new RuntimeException(e);}}));
    }
    /** One row of shake.csv for this frame (t: cast age in ticks). */
    private void logShake(float t){
        var cam=mc.gameRenderer.getCamera().getPos();float delta=mc.getTickDelta();
        for(var c:AtomicClient.CASTS.values()){
            if(!c.caster.equals(mc.player.getUuid()))continue;
            float sec=c.age(delta)/20f;double d=Math.hypot(cam.x-c.origin.x,cam.z-c.origin.z);
            double r=dev.sangwoo.celestialfall.client.atomic.AtomicPillarShape.radius(dev.sangwoo.celestialfall.client.atomic.AtomicStage.visual(sec),c.radius);
            float wall=dev.sangwoo.celestialfall.client.atomic.AtomicStage.wallShake(sec,AtomicImmersion.wallDistance(cam,c.origin,delta),r,c.radius)*(float)CelestialFall.CONFIG.screenShakeStrength;
            var s=AtomicImmersion.at(cam,delta);
            shakeLog.append(String.format(Locale.ROOT,"%.2f,%.3f,%.3f,%.3f,%.3f%n",t,d-r,wall,s.yaw(),s.pitch()));
        }
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
        NoCooldownState.get(p.getServer()).set(p.getUuid(),!opts.containsKey("cool"));
        p.getItemCooldownManager().remove(CelestialFall.ATOMIC_SWORD);
        int y=p.getServerWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,0,0);
        float yaw=facing(opt("face",0));
        p.networkHandler.requestTeleport(.5,y,.5,yaw,0);p.setYaw(yaw);p.setHeadYaw(yaw);p.setBodyYaw(yaw);p.setHealth(p.getMaxHealth());p.setVelocity(0,0,0);
        var d=p.getServerWorld().getEntityById(dummyId);
        if(!(d instanceof net.minecraft.entity.LivingEntity)||!d.isAlive()){
            var dummy=TrainingDummies.TYPES.values().iterator().next().create(p.getServerWorld());dummy.refreshPositionAndAngles(.5,y,3.5,180,0);p.getServerWorld().spawnEntity(dummy);dummyId=dummy.getId();d=dummy;
        }
        actors(p.getServerWorld(),y);
        if(d instanceof net.minecraft.entity.LivingEntity l){
            var f=net.minecraft.util.math.Vec3d.fromPolar(0,yaw);var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
            boolean show=opt("dummy",1)>0;var at=new net.minecraft.util.math.Vec3d(.5,y,.5).add(f.multiply(opt("dfw",3))).add(right.multiply(opt("drt",0)));
            // Hidden dummies wait far to the side (below the floor they would fall out of the world).
            l.setHealth(l.getMaxHealth());l.refreshPositionAndAngles(show?at.x:at.x+70,y,at.z,yaw+180,0);l.setVelocity(0,0,0);
        }
    }
    /** Removes the last shot's actors and spawns this one's: still husks with no goals and plenty of health. */
    private void actors(net.minecraft.server.world.ServerWorld w,int y){
        for(int id:ACTOR_IDS.values()){var old=w.getEntityById(id);if(old!=null)old.discard();}
        ACTOR_IDS.clear();ACTORS.clear();moving.clear();
        for(var a:actors){
            var husk=net.minecraft.entity.EntityType.HUSK.create(w);if(husk==null)continue;
            var at=new net.minecraft.util.math.Vec3d(.5,y,.5).add(a.dir().multiply(a.dist));
            husk.refreshPositionAndAngles(at.x,y,at.z,a.yaw,0);husk.setHeadYaw(a.yaw);husk.setBodyYaw(a.yaw);
            husk.setPersistent();husk.setSilent(true);
            try{for(String f:new String[]{"goalSelector","targetSelector"}){var field=net.minecraft.entity.mob.MobEntity.class.getDeclaredField(f);field.setAccessible(true);
                ((net.minecraft.entity.ai.goal.GoalSelector)field.get(husk)).clear(g->true);}}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            husk.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(4000);husk.setHealth(4000);
            // A mob's ground speed is 0.98 s^2 / (1 - 0.6 * 0.91) blocks a tick for movement speed s (MoveControl at speed 1).
            husk.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(Math.sqrt(a.speed/20*(1-.6*.91)/.98));
            w.spawnEntity(husk);ACTOR_IDS.put(a.name,husk.getId());ACTORS.put(a.name,a);
        }
    }
    /** Server tick of a shot with actors: start and steer them, and log where they are, whether G holds them and its edge. */
    private void actorTick(net.minecraft.server.MinecraftServer server){
        if(!filming||actors.isEmpty()||serverStart<0)return;
        var w=server.getOverworld();long age=w.getTime()-serverStart;
        double edge=dev.sangwoo.celestialfall.AtomicTimeline.holdRadius(age,CelestialFall.CONFIG.atomicVisualRadius);
        for(var a:actors){
            Integer id=ACTOR_IDS.get(a.name);var e=id==null?null:w.getEntityById(id);
            if(!(e instanceof net.minecraft.entity.mob.MobEntity mob))continue;
            var speed=mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
            boolean held=speed.getModifier(dev.sangwoo.celestialfall.AtomicTimeline.HIT_SLOW_ID)!=null;
            double out=Math.hypot(mob.getX()-spot.x,mob.getZ()-spot.z);
            boolean go=moving.contains(a.name)||switch(a.start){case "impact"->age>=dev.sangwoo.celestialfall.AtomicTimeline.IMPACT;case "held"->held;default->age>=Long.parseLong(a.start);};
            if(go&&out<60){
                moving.add(a.name);var target=spot.add(a.dir().multiply(400));
                mob.getMoveControl().moveTo(target.x,mob.getY(),target.z,1);
            }
            actorLog.append(String.format(Locale.ROOT,"%d,%s,%.3f,%.3f,%.3f,%d,%.4f,%.3f%n",age,a.name,mob.getX(),mob.getZ(),out,held?1:0,speed.getValue()/speed.getBaseValue(),edge));
        }
    }
    private float opt(String key,float fallback){var v=opts.get(key);return v==null?fallback:Float.parseFloat(v);}
    /** Camera looks along camyaw (default north); the character's facing is given relative to it. */
    private float camLook(){return opt("camyaw",180);}
    private float facing(float face){return camLook()+180+face;}
    /**
     * "turn=AGE:DEG/AGE:DEG/...": the player turns the camera during the cast (cast clock ticks, degrees clockwise from
     * the shot's camera yaw), straight between the keys and held after the last; as a player would, so the game turns
     * the caster to it (T held's aim).
     */
    private float turn(){
        if(!opts.containsKey("turn")||phase<4)return 0;
        float age=clock(),prev=0,from=0;boolean first=true;
        for(String key:opts.get("turn").split("/")){
            String[] v=key.split(":");float at=Float.parseFloat(v[0].trim()),deg=Float.parseFloat(v[1].trim());
            if(first&&age<=at)return deg;
            if(!first&&age<=at)return prev+(deg-prev)*Math.max(0,Math.min(1,(age-from)/Math.max(1e-3f,at-from)));
            first=false;prev=deg;from=at;
        }
        return prev;
    }
    private void camera(){float d=opt("dist",camDist);BotandCamera.STATE.yaw=camLook()+turn();BotandCamera.STATE.pitch=opt("pitch",camPitch);BotandCamera.STATE.targetDistance=d;BotandCamera.STATE.distance=d;BotandCamera.STATE.collisionDistance=d;}
    private void face(ServerPlayerEntity p,float face){float y=facing(face);p.setYaw(y);p.setHeadYaw(y);p.setBodyYaw(y);p.networkHandler.requestTeleport(p.getX(),p.getY(),p.getZ(),y,0);}

    @Override public void onInitializeClient(){
        instance=this;
        ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{tick();}catch(Throwable t){fail(t);}});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{try{actorTick(server);}catch(Throwable t){if(mc!=null)mc.execute(()->fail(t));}});
    }
    private void tick()throws Exception{
        age++;if((System.nanoTime()-started)/1e9>1500*SLOW)throw new IllegalStateException("timeout");
        if(mc.getToastManager()!=null)mc.getToastManager().clear();
        if(phase>=1&&phase<99&&mc.world!=null&&mc.currentScreen instanceof dev.sangwoo.celestialfall.client.title.BotandTitleScreen)mc.setScreen(null);
        if(SLOW>1&&!slowed&&phase>=3){var f=MinecraftClient.class.getDeclaredField("renderTickCounter");f.setAccessible(true);var counter=f.get(mc);var t=counter.getClass().getDeclaredField("tickTime");t.setAccessible(true);t.setFloat(counter,50f*SLOW);slowed=true;}
        switch(phase){
            case 0->{if(mc.currentScreen!=null&&mc.getOverlay()==null&&age>100){
                Files.createDirectories(OUT);var o=mc.options;o.getViewDistance().setValue(6);o.getMaxFps().setValue(260);o.getGuiScale().setValue(2);mc.onResolutionChanged();o.hudHidden=true;o.getBobView().setValue(false);
                mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                var info=new LevelInfo("ShotsFlat"+System.nanoTime(),GameMode.CREATIVE,false,Difficulty.EASY,true,new GameRules(),DataConfiguration.SAFE_MODE);
                mc.createIntegratedServerLoader().createAndStart(info.getLevelName(),info,new GeneratorOptions(1,false,false),r->r.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder());
                phase=1;age=0;}}
            case 1->{
                if(mc.currentScreen instanceof dev.sangwoo.celestialfall.client.title.BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);}
                if(mc.player!=null&&mc.world!=null&&mc.currentScreen==null&&age>60){server(this::setup);phase=2;age=0;}}
            case 2->{if(age>60&&StandClient.visible(mc.player)){phase=3;age=0;}else if(age>400)throw new IllegalStateException("stand never visible, map="+MapClient.enabled());}
            case 3->{ // next skill
                if(age==1){if(queue.isEmpty()){for(var j:jobs)j.get();Files.writeString(OUT.resolve("log.txt"),log);mc.scheduleStop();phase=99;return;}String entry=queue.poll();String[] parts=entry.split(";");current=parts[0];int n=repeats.merge(current,1,Integer::sum);folder=n==1?current:current+"."+n;opts.clear();for(int i=1;i<parts.length;i++){String[] kv=parts[i].split("=",2);opts.put(kv[0],kv.length>1?kv[1]:"1");}
                    far=null;if(opts.containsKey("far")){String[] v=opts.get("far").split("[/,]");double[] f=new double[v.length];for(int i=0;i<v.length;i++)f[i]=Double.parseDouble(v[i].trim());far=f;}
                    orbit=null;if(opts.containsKey("orbit")){String[] v=opts.get("orbit").split("[/,]");double[] f=new double[v.length];for(int i=0;i<v.length;i++)f[i]=Double.parseDouble(v[i].trim());orbit=f;}
                    actors.clear();if(opts.containsKey("act"))for(String spec:opts.get("act").split("\\+")){String[] v=spec.split(":");actors.add(new Actor(v[0],Double.parseDouble(v[1]),Float.parseFloat(v[2]),Double.parseDouble(v[3]),v[4]));}
                    track=opts.containsKey("track")?opts.get("track").split(":"):null;shake=opts.containsKey("shake")&&!"0".equals(opts.get("shake"));
                    mc.options.hudHidden=!opts.containsKey("hud")||"0".equals(opts.get("hud"));
                    server(this::reset);
                    int view=(int)opt("view",Integer.getInteger("botand.shots.view",6));if(mc.options.getViewDistance().getValue()!=view){mc.options.getViewDistance().setValue(view);mc.worldRenderer.reload();}}
                if(age==2)camera();
                if(age==2&&(opts.containsKey("far")||opts.containsKey("orbit"))){castPos=mc.player.getPos();castYaw=mc.player.getYaw();}
                if(age==2)camera();
                // "walk=N": the player walks (W held, camera-relative) from N ticks before the cast to the end of the shot.
                int lead=opts.containsKey("far")||opts.containsKey("track")||opts.containsKey("orbit")?90:30;
                if(opts.containsKey("walk")&&age==Math.max(3,lead-(int)opt("walk",10)))key(GLFW_KEY_W,true);
                if(age>=lead){camera();castPos=mc.player.getPos();castYaw=mc.player.getYaw();castTick=mc.world.getTime()+1;shotIndex=0;serverStart=-1;actorLog.setLength(0);shakeLog.setLength(0);motionLog.setLength(0);soundLog.setLength(0);sounded.clear();filming=true;trigger(current);phase=4;age=0;}}
            case 4->{
                logTick();
                if(current.equals("M1")){
                    String[] at=opts.getOrDefault("hits","0/13/26/39").split("[,/]");String[] faces=opts.getOrDefault("faces","").split("[,/]");
                    for(int i=1;i<at.length;i++)if(age==Integer.parseInt(at[i].trim())){final int hit=i;server(p->{if(faces.length>hit&&!faces[hit].isBlank())face(p,Float.parseFloat(faces[hit]));KagenoManager.input(p,M1);});}
                }
                if(current.equals("RUSH")&&age<14)server(p->KagenoManager.input(p,KagenoRules.PULSE));
                if(current.equals("BASE")){
                    if((age==10||age==40)&&mc.player.isOnGround())mc.player.jump();
                    if(age==70||age==85)server(p->p.damage(p.getDamageSources().generic(),1));
                    if(age==100)server(p->KagenoManager.input(p,M1));
                }
                // Follow-up inputs during the shot, "then=M1@60/E@90".
                if(opts.containsKey("then"))for(String step:opts.get("then").split("/")){String[] sa=step.split("@");if(sa.length!=2)continue;int at=Integer.parseInt(sa[1].trim());String what=sa[0].trim();
                    // "DASH@n": the dash key (C) is pressed for a tick, through the client's own input
                    if(what.equals("DASH")){if(age==at)key(GLFW_KEY_C,true);if(age==at+1)key(GLFW_KEY_C,false);continue;}
                    if(age==at){int id=skill(what);server(p->{boolean ok=KagenoManager.input(p,id);log.append("  ").append(what).append(ok?" at ":" refused at ").append(p.getWorld().getTime()).append('\n');});}}
                if(current.equals("F")&&age==40)key(GLFW_KEY_F,false);
                if(!current.equals("H"))camera();
                if(Math.min(age,clock())>length(current)+4||age>length(current)*3+40){log.append(folder).append(" frames ").append(shotIndex).append('\n');
                    if(opts.containsKey("walk"))key(GLFW_KEY_W,false);
                    filming=false;if(!actors.isEmpty()){var rows="age,name,x,z,out,held,speed_attr,edge\n"+actorLog;var path=OUT.resolve(folder).resolve("actors.csv");Files.createDirectories(path.getParent());Files.writeString(path,rows);}
                    if(shakeLog.length()>0){var path=OUT.resolve(folder).resolve("shake.csv");Files.createDirectories(path.getParent());Files.writeString(path,"t,gap,wall,yaw,pitch\n"+shakeLog);}
                    {var path=OUT.resolve(folder).resolve("motion.csv");Files.createDirectories(path.getParent());Files.writeString(path,"t,x,y,z,yaw,fov_target,fov\n"+motionLog);}
                    if(soundLog.length()>0){var path=OUT.resolve(folder).resolve("sound.csv");Files.writeString(path,"combo,cast_age,shot_clock\n"+soundLog);}if(current.equals("Q"))server(p->{});phase=3;age=0;}}
        }
    }
    private void trigger(String name){
        log.append(name).append(" at ").append(mc.world.getTime()).append('\n');
        switch(name){
            case "F"->key(GLFW_KEY_F,true);
            case "RUSH"->server(p->KagenoManager.input(p,T_DOWN));
            case "T"->server(p->{KagenoManager.input(p,T_DOWN);KagenoManager.input(p,T_UP);});
            case "P"->server(p->{if(!KagenoManager.input(p,POSE))log.append("  rejected P\n");});
            case "BASE","HUD"->{}
            case "GDUMMY"->server(p->{int n=p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource(),"botand dummy atomic "+(int)opt("adist",60));if(n<=0)log.append("  rejected GDUMMY\n");});
            default->server(p->{long now=p.getWorld().getTime();var at=p.getPos();
                if(!KagenoManager.input(p,skill(name)))log.append("  rejected ").append(name).append('\n');else{spot=at;serverStart=now;}});
        }
    }
}
