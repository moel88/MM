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
 * <p>"UI" (2.45.0) films the interface on its own clock (client ticks from the shot's start, which run on while the
 * pause menu pauses the game) and casts nothing; its "then=" steps are keys and the mouse: "KEY_TAB@n" (also ESCAPE,
 * R, ENTER, UP, DOWN) taps a key through the real keyboard path, "MOVE_B0@n" glides the pointer over 9 ticks to the
 * pause menu's first button (B0-B3; D0 and D1 the reset question's buttons, RESPAWN the death screen's first button,
 * DONE the options screen's done button, OFF clear of them all) and "CLICK@n" clicks the left button where it is,
 * through Minecraft's own mouse handlers. The screenshots do not hold the system pointer, so while a screen is up the
 * shot draws one (an arrow, with a ring as it clicks) where the game's mouse is; it is the capture's mark, not the
 * game's. The log gets every screen opened, the reset question, the player list sent away or back, deaths, respawns
 * and chat lines. -Dbotand.shots.lang=ko_kr switches the game's language first.
 *
 * <p>"METEOR" and "SHOP" (2.49.0) film the meteors and the merchants on a real map: -Dbotand.shots.world=FOLDER opens
 * that world from the saves (run_shots.py copies SHOTS_WORLD there) instead of making a flat one, and nothing is built
 * into it. "at=x/y/z/yaw/pitch" puts the player there (world coordinates; the spawn if left out) and "time=T" sets the
 * time of day. Their clock is the shot's own (client ticks from its start). Steps: "FALL@n" drops a meteor on
 * "fall=x/z" (the ground there) coming in from "from=YAW" (the way a player would face to look back up its way),
 * "FALLB@n" another on "fallb=x/z", "TP@n" moves the player to "tp=x/y/z/yaw/pitch" ("TPB@n" to "tpb=..."),
 * "CLEARMETEOR@n" crumbles every meteor, "GIVE_PICKAXE@n", "GIVE_FRAGMENTS@n" (three) and "GIVE_STICK@n" put things in
 * the inventory, "KEYDOWN_E@n" and "KEYUP_E@n" hold E (talking to a merchant takes a second), "MOVE_C1@n" glides the
 * pointer to the open screen's second button (C0 is its first) and "CLICK@n" clicks. "aimrock=1" keeps the player
 * looking at the nearest rock (mining with the pickaxe: a click strikes the rock aimed at), "holds=1/0/..." scripts the
 * meteors' shatter rolls in order (2.49.1: 1 holds together after a crystal, 0 shatters; real rolls after the script, so
 * a film run and its sound run break the same way), and "farfrom=A;farto=B"
 * shows the fixed camera ("far=") only from shot tick A to B (the game's own camera before and after). The log gets each rock's
 * fragments and blows as they change, the action bar's messages, the screens and chat, and what the inventory holds.
 * -Dbotand.shots.audio=true (run_shots.py with SHOTS_RECORD=1) plays a marker tone before each entry and logs every
 * sound the game starts and each tick's time to NAME/sounds.csv and NAME/ticks.csv, for tools/meteor/shots/mux.py.
 *
 * <p>"STAND" (2.50.0) films Star Platinum's figure: the player goes invisible and stand-less, the training dummy away, and
 * the figure stands "sfw" blocks (3) ahead of where the player faces, facing back ("syaw" turns it that many degrees
 * more), in "stance=idle" or "stance=hero". Its clock is the shot's own (client ticks from its start); film it with
 * "orbit=" centred on it (FW = sfw) and "fov=" for that camera. "STANCE_HERO@n" and "STANCE_IDLE@n" change its stance.
 *
 * <p>"SUMMON" (2.51.0) films Star Platinum as a stand: the player is seen and has it as their stand, the training dummy
 * away, and it starts in. Its clock is the shot's own; "KEY_Q@n" taps Q through the real keyboard path (out, and back),
 * "KEYDOWN_W@n"/"KEYUP_W@n" (also A, S, D) walk. Each tick goes to NAME/stand.csv: the shot clock, whether the server has it
 * out, the seconds since it last came out or went back, and the look it was drawn with (how far out, how solid, its
 * size, the stances, the wind, the gust, the lean, the speed and, since 2.52.0, its glow; since 2.53.0 how many bodies
 * the last frame wrapped in the aura); the log gets each time it comes out or goes back.
 * "time=T" sets the time of day for the entry (2.52.0: a glow shows best at night). Its punches (2.54.0): "ATTACKDOWN@n"
 * and "ATTACKUP@n" hold the attack (the left button) down and let it go through Minecraft's own mouse handler, "dummy=1"
 * (with "dfw"/"drt") puts the training dummy in front to punch and "DUMMYBACK@n" brings it back in front, whole, after
 * the finisher threw it. stand.csv gets the last punch (its number, its age in seconds, whether it landed and on how
 * many, its whole ticks in: the shot clock t less those is when it started), how far round in front the stand is and
 * each punch stance's share; the log gets each punch seen and landed. Its rush (2.57.0): "KEYDOWN_E@n" and "KEYUP_E@n"
 * hold E through the real keyboard path; stand.csv gets the rush (its age in seconds, the blows heard, how many the last
 * struck, whether it is on, how loud its blows sound), the training dummy's health, the player's speed and each rush
 * stance's share, and the log each time it comes on and goes off and each blow heard. Its heavy punch (2.59.0):
 * "KEY_R@n" taps R through the real keyboard path, and "dguard=1" makes the training dummy one that holds its guard
 * up; stand.csv gets the heavy punch's two stances, the dummy's guard (up, broken) and where it is (its flight), and
 * the log the moment its guard breaks. Its Star Finger (2.60.0): "KEY_T@n" taps T through the real keyboard path,
 * and "act=" husks walk off from their START (ticks of the shot clock, through the game's movement: the Star Finger
 * slows them) and go to NAME/actors.csv;
 * stand.csv gets its two stances, how far its fingers are open and out, how long they were drawn and how far ahead of
 * the player's feet their tips reach, and whether the dummy is incapacitated and its speed; the log how far the
 * fingers went each time and when the dummy is incapacitated and free again. Its inhale (2.61.0; held down since
 * 2.62.0): "KEYDOWN_Y@n" and "KEYUP_Y@n" hold Y down and let it up through the real keyboard path ("KEY_Y@n" taps it);
 * stand.csv gets its stances (and the strain), the inhale (its age, whether it is on, whether it holds anyone now, how
 * many guards were seen holding it off lately, how hard it sucks) and where the dummy is from the player (ahead, to
 * their right), and the log when it comes on, when it catches someone and lets go, when a guard holds it off and when
 * it goes off. Its beatdown (2.64.0): "KEY_G@n" taps G through the real keyboard path; with no "far=", "orbit=" or
 * "track=" the game's own camera films it, so its cutscene's shots and black bars are the game's as its user sees them.
 * stand.csv gets its cutscene (its age in seconds, whether it ended and was cut short, how high the bars are), whether
 * its camera has the player, how strongly the player's body flashes sky blue, its two stances (the collar, the
 * uppercut) and where the one struck is shown from the player (ahead, to the right, up), and since 2.65.0 its blow's
 * two stances (drawn back, striking), the heavy punch's two (its last blow) and how many bodies glow sky blue; the log
 * when its cutscene starts and ends, and each change of the dummy's health. Its time stop (2.67.0): "KEY_H@n" taps H
 * through the real keyboard path; to film the one it stops, "FAKE@n" brings in a second Star Platinum user (a fake
 * player on the server, its stand out, "cfw" blocks ahead and "crt" to the right, facing the player), "FAKEH@n" has it
 * stop time by the real rules (the player stands stopped in it, their screen held), "FAKEWALK@n" walks it up to "fto"
 * blocks in front of the player ("frt" to their right) over "fwt" ticks (to film a walking player the player's own time
 * stop stops mid-stride, end the walk before time stops: the walk moves it whatever holds it), "HURT@n" deals the player "hurt" damage from it through the game's
 * own blow path and "FLING@n" throws the player "fly" blocks a tick away from it ("flyup" up) through the time stop's
 * hold, as a punch's throw is (the fake has no stand attacks of its own: the server runs those for listed players
 * only), and "UNFAKE@n" takes it away. stand.csv gets the time stop (its age, whether time is stopped, when it moved
 * again, cut short, whether this client's screen is held still, its wave: how far it has spread, the far world's
 * grade, the flash, the negative, since 2.68.0 how brightly its bubble shows, its cracks and the impact frame), the
 * call's two stances, the player's health and whether the dummy and the player are held; camera.csv each frame's camera
 * (where, its yaw and pitch, the game camera's distance and clear distance, the field of view); the log when it is
 * called, stops time, moves again, the screen held and let go, and each change of the player's health. The basic
 * attack's stagger (2.68.0): "dattack=1" makes the training dummy one that throws basic-attack combos at the player;
 * stand.csv gets whether the player can only walk (their combat state locked), whether they are staggered, whether the
 * dummy is staggered (the server's own state) and its blow, and the log each time the player or the dummy is staggered
 * and free again.
 *
 * <p>"BASE" films the plain body instead of a skill: a jump at 0.5 s and 2 s (airborne pose, landing dip), a blow
 * at 3.5 s and 4.25 s (flinch), then an M1 at 5 s (the crossfade into a skill and out of it). Its clock is the
 * shot's own (seconds from the start).
 */
public final class SkillShots implements ClientModInitializer {
    static SkillShots instance;
    static final Path OUT=Path.of(System.getProperty("botand.shots.out","shots"));
    static final int SLOW=Integer.getInteger("botand.shots.slow",1);
    static final String LANG=System.getProperty("botand.shots.lang");
    /** A world in the saves to open instead of a new flat one (the meteor and shop shots). */
    static final String WORLD=System.getProperty("botand.shots.world");
    static final boolean AUDIO=Boolean.getBoolean("botand.shots.audio");
    private final StringBuilder soundRows=new StringBuilder(),tickRows=new StringBuilder();private long markerNanos=-1;
    private String overlayLogged="",rocksLogged="",itemsLogged="";
    /** The UI shot's pointer: where it glides from and to (window pixels) and when, and when it last clicked (shot ticks). */
    private double[] glideFrom,glideTo;private double glideAt=-1e9,clickAt=-1e9;
    static final double GLIDE=9;
    private String screenLogged="";private int chatLogged=-1;private boolean aliveLogged=true,hiddenLogged,askingLogged,pausedLogged,worldClosed;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"shot-writer");t.setDaemon(true);return t;});
    private final List<Future<?>> jobs=new ArrayList<>();
    private MinecraftClient mc;private int phase,age,shotIndex;private long started=System.nanoTime();
    private final Deque<String> queue=new ArrayDeque<>(Arrays.asList(System.getProperty("botand.shots.list","F,M1,E,R,T,RUSH,Y,Q,H,G").split(",")));
    private String current,folder;private Map<String,String> opts=new HashMap<>();private long castTick;private int dummyId=-1;private final StringBuilder log=new StringBuilder();
    private final Map<String,Integer> repeats=new HashMap<>();
    private boolean slowed,listening;
    private float camYaw=Float.parseFloat(System.getProperty("botand.shots.yaw","145")),camPitch=12,camDist=Float.parseFloat(System.getProperty("botand.shots.dist","6.5"));
    private net.minecraft.util.math.Vec3d castPos=net.minecraft.util.math.Vec3d.ZERO;private static volatile double[] far,orbit;private float castYaw;
    private record Actor(String name,double dist,float yaw,double speed,String start){
        net.minecraft.util.math.Vec3d dir(){double r=Math.toRadians(yaw);return new net.minecraft.util.math.Vec3d(-Math.sin(r),0,Math.cos(r));}
    }
    private final List<Actor> actors=new ArrayList<>();
    private static final Map<String,Integer> ACTOR_IDS=new ConcurrentHashMap<>();private static final Map<String,Actor> ACTORS=new ConcurrentHashMap<>();
    private static volatile String[] track;private static volatile boolean shake;private final StringBuilder shakeLog=new StringBuilder();
    /** "holds=1/0/..." (2.49.1): the entry's meteor shatter rolls in order, 1 holds together and 0 shatters (SkillShotsMeteorMixin). */
    private static final Deque<Boolean> HOLDS=new ConcurrentLinkedDeque<>();
    public static Boolean nextHold(){return HOLDS.poll();}
    private volatile boolean filming;private volatile long serverStart=-1;private volatile net.minecraft.util.math.Vec3d spot=net.minecraft.util.math.Vec3d.ZERO;
    private final Set<String> moving=new HashSet<>();private final StringBuilder actorLog=new StringBuilder();
    private final StringBuilder motionLog=new StringBuilder(),soundLog=new StringBuilder();private final Set<UUID> sounded=new HashSet<>();

    /** Fixed far camera of the current entry ("far=ex/ey/ez/tx/ty/tz[/fov]" in blocks from the cast spot), or null. */
    public static dev.sangwoo.celestialfall.client.CinematicDirector.Pose farPose(){
        var fight=bossPose();if(fight!=null)return fight;
        var tracked=trackPose();if(tracked!=null)return tracked;
        var circling=orbitPose();if(circling!=null)return circling;
        double[] f=far;var self=instance;if(f==null||f.length<6||self==null||self.phase<3)return null;
        // "farfrom=A;farto=B": the fixed camera only for those shot ticks (the game's camera before and after)
        if(self.opts.containsKey("farfrom")&&(self.phase<4||self.age<self.opt("farfrom",0)||self.age>=self.opt("farto",1e9f)))return null;
        var o=self.castPos;var eye=o.add(f[0],f[1],f[2]);var d=o.add(f[3],f[4],f[5]).subtract(eye).normalize();
        return new dev.sangwoo.celestialfall.client.CinematicDirector.Pose(eye,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.asin(d.y)),0);
    }
    /** Whether the onlooker camera of this entry shakes like a player standing there ("shake=1"). */
    public static boolean shake(){var self=instance;return shake&&self!=null&&self.phase>=3;}
    public static double farFov(){
        if(instance!=null&&instance.phase>=3&&instance.opts.containsKey("bcam")){String[] v=instance.opts.get("bcam").split("[/,]");return v.length>2?Double.parseDouble(v[2]):60;}
        String[] t=track;if(t!=null&&instance!=null&&instance.phase>=3)return t.length>5?Double.parseDouble(t[5]):70;
        if(orbit!=null&&instance!=null&&instance.phase>=3)return instance.opt("fov",70);
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
    private static net.minecraft.util.math.Vec3d bossEye,bossLook,bossSide;private static double bossClock=-1;
    /**
     * "bcam=D/UP/FOV" (2.69.0): a camera beside the fight, following the player and the boss together: from the side
     * of the line between them, D blocks out (more as they part), UP blocks up, looking at the middle; it follows
     * smoothly (a fifth of a second) on the shot's own clock and keeps to the side it began on. Once the boss is gone it
     * stays where it was, looking where it looked.
     */
    private static dev.sangwoo.celestialfall.client.CinematicDirector.Pose bossPose(){
        var self=instance;if(self==null||self.phase<3||!self.opts.containsKey("bcam"))return null;
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null)return null;
        String[] v=self.opts.get("bcam").split("[/,]");double d=Double.parseDouble(v[0]),up=v.length>1?Double.parseDouble(v[1]):2,dt;
        double t=self.phase==4?self.clock():0;
        if(bossClock<0||t<bossClock){dt=-1;bossEye=null;bossSide=null;}else dt=t-bossClock;
        bossClock=t;
        float delta=mc.getTickDelta();var me=mc.player.getLerpedPos(delta);net.minecraft.util.math.Vec3d boss=null;
        for(var info:dev.sangwoo.celestialfall.client.boss.BossClient.all()){var e=mc.world.getPlayerByUuid(info.id());if(e!=null){boss=e.getLerpedPos(delta);break;}}
        if(boss!=null){
            var mid=me.add(boss).multiply(.5);var line=boss.subtract(me).multiply(1,0,1);double apart=line.length();
            var side=apart<1e-3?(bossSide==null?new net.minecraft.util.math.Vec3d(1,0,0):bossSide):new net.minecraft.util.math.Vec3d(-line.z,0,line.x).normalize();
            if(bossSide==null){var f=net.minecraft.util.math.Vec3d.fromPolar(0,self.castYaw);var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);if(side.dotProduct(right)<0)side=side.multiply(-1);}
            else if(side.dotProduct(bossSide)<0)side=side.multiply(-1);
            bossSide=side;
            var eye=mid.add(side.multiply(d+apart*.55)).add(0,up,0);var look=mid.add(0,1,0);
            if(bossEye==null||dt<0){bossEye=eye;bossLook=look;}
            else{double k=1-Math.exp(-dt*.25);bossEye=bossEye.lerp(eye,k);bossLook=bossLook.lerp(look,k);}
        }
        if(bossEye==null)return null;
        var w=bossLook.subtract(bossEye).normalize();
        return new dev.sangwoo.celestialfall.client.CinematicDirector.Pose(bossEye,(float)Math.toDegrees(Math.atan2(-w.x,w.z)),(float)-Math.toDegrees(Math.asin(w.y)),0);
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
        if("UI".equals(current)||mapShot()||"STAND".equals(current)||"SUMMON".equals(current))return age+delta;
        if("Q".equals(current)||"M1".equals(current)||"BASE".equals(current)||"HUD".equals(current))return (mc.world.getTime()-castTick)+delta;
        if("G".equals(current)){for(var c:AtomicClient.CASTS.values())if(c.caster.equals(mc.player.getUuid()))return c.age(delta);}
        else if("GDUMMY".equals(current)){for(var c:AtomicClient.CASTS.values())return c.age(delta);}
        else{StandClient.Cast best=null;for(var c:StandClient.CASTS.values())if(c.caster().equals(mc.player.getUuid())&&(best==null||c.start()>best.start()))best=c;if(best!=null&&mc.world.getTime()-castTick<400)return best.age(delta);}
        return (mc.world.getTime()-castTick)+delta;
    }
    private int skill(String name){return switch(name){case "M1"->M1;case "E"->E;case "R"->R;case "T"->T;case "RUSH"->RUSH;case "Y"->Y;case "Q"->Q;case "H"->H;case "G"->G;case "PULSE"->PULSE;default->-1;};}
    private boolean mapShot(){return "METEOR".equals(current)||"SHOP".equals(current);}
    private int length(String name){if(opts.containsKey("len"))return (int)opt("len",60);return switch(name){case "METEOR"->300;case "SHOP"->400;case "BASE"->150;case "HUD"->400;case "UI"->300;case "F"->50;case "M1"->75;case "Q"->140;case "P"->390;case "G"->560;case "T"->110;case "RUSH"->90;case "GDUMMY"->560;case "STAND"->240;case "SUMMON"->300;default->DURATION[skill(name)]+20;};}
    private final Set<UUID> castsLogged=new HashSet<>();private long dashLogged=-1000;private List<String> listLogged=List.of();
    /** One row of motion.csv for this client tick, and for M1 the hits whose sound has just started (sound.csv). */
    private void logTick()throws Exception{
        if("UI".equals(current)||mapShot())logInterface();
        if(mapShot()&&mc.player!=null&&mc.world!=null)logMeteors();
        if(AUDIO&&phase==4)tickRows.append(String.format(Locale.ROOT,"%.3f,%d%n",clock()-mc.getTickDelta(),System.nanoTime()));
        // the UI shot may leave the world ("나가기"): nothing below has a player then
        if(mc.player==null||mc.world==null)return;
        // every cast of the player's (with its combo hit) and every dash, as the client hears of them (the HUD shots' captions)
        for(var c:StandClient.CASTS.values())if(c.caster().equals(mc.player.getUuid())&&castsLogged.add(c.id()))
            log.append("  cast ").append(c.skill()).append(" combo ").append(c.combo()).append(" at ").append(c.start()).append('\n');
        long dash=CombatClient.snapshot(mc.player.getUuid()).dashStart();
        if(dash!=dashLogged&&dash>0){if(dashLogged!=-1000||mc.world.getTime()-dash<40)log.append("  dash at ").append(dash).append('\n');dashLogged=dash;}
        var shown=CooldownHud.shown();if(!shown.equals(listLogged)){log.append("  list ").append(shown).append(" at ").append(mc.world.getTime()).append('\n');listLogged=shown;}
        var gr=mc.gameRenderer;var f=net.minecraft.client.render.GameRenderer.class.getDeclaredField("fovMultiplier");f.setAccessible(true);
        if("SUMMON".equals(current))logStand();
        if("SUMMON".equals(current))logBoss();
        var p=mc.player;motionLog.append(String.format(Locale.ROOT,"%.3f,%.4f,%.4f,%.4f,%.2f,%.4f,%.4f%n",clock()-mc.getTickDelta(),p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getFovMultiplier(),f.getFloat(gr)));
        if(!"M1".equals(current))return;
        var field=Class.forName("dev.sangwoo.celestialfall.client.KagenoAudio").getDeclaredField("PLAYING");field.setAccessible(true);
        for(var e:((Map<UUID,net.minecraft.client.sound.SoundInstance>)field.get(null)).entrySet()){
            if(sounded.contains(e.getKey())||!mc.getSoundManager().isPlaying(e.getValue()))continue;
            var c=StandClient.CASTS.get(e.getKey());if(c==null)continue;sounded.add(e.getKey());
            soundLog.append(String.format(Locale.ROOT,"%d,%.3f,%.3f%n",c.combo(),c.age(0),clock()-mc.getTickDelta()));
        }
    }

    /** The meteor and shop shots' log: each rock's fragments and blows, the action bar, what the inventory holds. */
    private void logMeteors()throws Exception{
        var rocks=new StringBuilder();
        for(var e:mc.world.getEntities())if(e instanceof dev.sangwoo.celestialfall.meteor.MeteorEntity m)
            rocks.append(String.format(Locale.ROOT," #%d(%.0f,%.0f,%.0f) crystals %d blows %d",m.slot(),m.getX(),m.getY(),m.getZ(),m.fragments(),m.hits()));
        if(!rocks.toString().equals(rocksLogged)){log.append("  rocks").append(rocks.length()==0?" none":rocks).append(" at ").append(age).append('\n');rocksLogged=rocks.toString();}
        var f=net.minecraft.client.gui.hud.InGameHud.class.getDeclaredField("overlayMessage");f.setAccessible(true);
        var t=(net.minecraft.text.Text)f.get(mc.inGameHud);String overlay=t==null?"":t.getString();
        if(!overlay.equals(overlayLogged)){if(!overlay.isEmpty())log.append("  action bar \"").append(overlay).append("\" at ").append(age).append('\n');overlayLogged=overlay;}
        var inv=mc.player.getInventory();int pick=0,frag=0,arrow=0,stick=0,fruit=0;
        for(int i=0;i<inv.size();i++){var st=inv.getStack(i);
            if(st.isOf(dev.sangwoo.celestialfall.meteor.MeteorRegistry.PICKAXE))pick+=st.getCount();if(st.isOf(dev.sangwoo.celestialfall.meteor.MeteorRegistry.FRAGMENT))frag+=st.getCount();
            if(st.isOf(dev.sangwoo.celestialfall.meteor.MeteorRegistry.STAND_ARROW))arrow+=st.getCount();if(st.isOf(net.minecraft.item.Items.STICK))stick+=st.getCount();
            if(st.isOf(dev.sangwoo.celestialfall.commerce.CommerceRegistry.ROKAKAKA))fruit+=st.getCount();}
        String items=String.format(Locale.ROOT,"pickaxe %d, fragments %d, sticks %d, stand arrows %d, rokakaka %d, money %d",pick,frag,stick,arrow,fruit,ProgressClient.profile.money());
        if(!items.equals(itemsLogged)){log.append("  holds ").append(items).append(" at ").append(age).append('\n');itemsLogged=items;}
        if(opts.containsKey("aimrock"))aimRock();
    }
    /** Turns the player to the middle of the nearest rock (and tells the server), as a player aiming at it would. */
    private void aimRock(){
        dev.sangwoo.celestialfall.meteor.MeteorEntity best=null;double bestD=1e9;
        for(var e:mc.world.getEntities())if(e instanceof dev.sangwoo.celestialfall.meteor.MeteorEntity m){double d=m.squaredDistanceTo(mc.player);if(d<bestD){bestD=d;best=m;}}
        if(best==null)return;
        var d=best.getPos().add(0,.55,0).subtract(mc.player.getEyePos());
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalLength()));
        mc.player.setYaw(yaw);mc.player.setPitch(pitch);mc.player.setHeadYaw(yaw);mc.player.setBodyYaw(yaw);
        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(yaw,pitch,mc.player.isOnGround()));
    }
    private boolean ui(){return ("UI".equals(current)||mapShot())&&phase==4;}
    /** A UI step's target in GUI units (see the class notes). */
    private double[] target(String name){
        int w=mc.getWindow().getScaledWidth(),h=mc.getWindow().getScaledHeight();
        if(name.matches("B[0-3]")){var b=dev.sangwoo.celestialfall.client.ui.PauseLayout.menu(w,h,4).buttons().get(name.charAt(1)-'0');return new double[]{b.x()+b.w()*.42,b.y()+b.h()*.55};}
        if(name.equals("D0")||name.equals("D1")){var d=dev.sangwoo.celestialfall.client.ui.PauseLayout.dialog(w,h);var b=name.equals("D0")?d.confirm():d.cancel();return new double[]{b.x()+b.w()*.5,b.y()+b.h()*.55};}
        if(name.equals("RESPAWN")||name.equals("DONE")){
            var s=mc.currentScreen;if(s==null)throw new IllegalStateException("no screen for "+name);
            for(var c:s.children())if(c instanceof net.minecraft.client.gui.widget.ButtonWidget b&&(name.equals("RESPAWN")||b.getMessage().getString().equals(net.minecraft.screen.ScreenTexts.DONE.getString())))
                return new double[]{b.getX()+b.getWidth()*.5,b.getY()+b.getHeight()*.55};
            throw new IllegalStateException("no button for "+name+" on "+s.getClass().getSimpleName());
        }
        if(name.matches("C[0-9]+")){
            var sc=mc.currentScreen;if(sc==null)throw new IllegalStateException("no screen for "+name);
            int want=Integer.parseInt(name.substring(1)),n=0;
            for(var c:sc.children())if(c instanceof net.minecraft.client.gui.widget.ClickableWidget b&&n++==want)return new double[]{b.getX()+b.getWidth()*.5,b.getY()+b.getHeight()*.55};
            throw new IllegalStateException("no widget "+name+" on "+sc.getClass().getSimpleName());
        }
        if(name.equals("OFF"))return new double[]{w*.2,h*.8};
        throw new IllegalArgumentException(name);
    }
    /** Moves the game's mouse along the current glide for this frame (only while a screen is up: in play it would turn the camera). */
    private void glide(){
        if(!ui()||glideTo==null||mc.currentScreen==null)return;
        double k=Math.max(0,Math.min(1,(clock()-glideAt)/GLIDE));k=k*k*(3-2*k);
        double x=glideFrom[0]+(glideTo[0]-glideFrom[0])*k,y=glideFrom[1]+(glideTo[1]-glideFrom[1])*k;
        if(Math.abs(x-mc.mouse.getX())>1e-6||Math.abs(y-mc.mouse.getY())>1e-6){
            glfwSetCursorPos(mc.getWindow().getHandle(),x,y);
            try{var m=net.minecraft.client.Mouse.class.getDeclaredMethod("onCursorPos",long.class,double.class,double.class);m.setAccessible(true);m.invoke(mc.mouse,mc.getWindow().getHandle(),x,y);}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        if(k>=1)glideTo=null;
    }
    private void button(boolean down){
        try{var m=net.minecraft.client.Mouse.class.getDeclaredMethod("onMouseButton",long.class,int.class,int.class,int.class);m.setAccessible(true);
            m.invoke(mc.mouse,mc.getWindow().getHandle(),GLFW_MOUSE_BUTTON_LEFT,down?GLFW_PRESS:GLFW_RELEASE,0);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    /** The capture's pointer mark: an arrow about 20 pixels tall where the game's mouse is, and a ring spreading as it clicks. */
    private void pointer(net.minecraft.client.gui.DrawContext d){
        if(!ui())return;
        double scale=mc.getWindow().getScaleFactor();float x=(float)(mc.mouse.getX()/scale),y=(float)(mc.mouse.getY()/scale),u=(float)(20/scale/17);
        float[] base={0,0,10.8f,10.8f,5.8f,10.8f,8.4f,16,6.2f,17,3.6f,11.6f,0,15};
        float[] arrow=new float[base.length];for(int i=0;i<base.length;i+=2){arrow[i]=x+base[i]*u;arrow[i+1]=y+base[i+1]*u;}
        d.getMatrices().push();d.getMatrices().translate(0,0,400);
        var m=d.getMatrices().peek().getPositionMatrix();
        double since=clock()-clickAt;
        if(since>=0&&since<7){float k=(float)(since/7);var b=dev.sangwoo.celestialfall.client.gacha.GachaArt.begin(d,false);
            dev.sangwoo.celestialfall.client.gacha.GachaArt.stroke(b,m,dev.sangwoo.celestialfall.client.gacha.GachaArt.circle(x,y,(3+11*k)*u*1.6f,32),1.2f*u*1.6f,dev.sangwoo.celestialfall.client.gacha.GachaArt.alpha(0xffffff,200*(1-k)));
            dev.sangwoo.celestialfall.client.gacha.GachaArt.end(b);}
        var b=dev.sangwoo.celestialfall.client.gacha.GachaArt.begin(d,false);
        dev.sangwoo.celestialfall.client.gacha.GachaArt.smooth(b,m,arrow,0xffffffff,0xffffffff);
        dev.sangwoo.celestialfall.client.gacha.GachaArt.stroke(b,m,arrow,1.1f*u,0xff000000);
        dev.sangwoo.celestialfall.client.gacha.GachaArt.end(b);
        d.getMatrices().pop();
    }
    /**
     * A second player for the boss's shots (2.69.0, "MATE@n"): a server player nobody plays, with a handler that sends
     * nothing; its body moves, falls and is thrown as a player's does (its own player tick, as the boss's), and it is
     * kept from dying under "mfloor" health.
     */
    static final class Mate extends ServerPlayerEntity {
        float floor;
        Mate(net.minecraft.server.world.ServerWorld w,com.mojang.authlib.GameProfile profile){
            super(w.getServer(),w,profile);
            new net.minecraft.server.network.ServerPlayNetworkHandler(w.getServer(),new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),this){
                @Override public void sendPacket(net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callbacks){}
                @Override public void disconnect(net.minecraft.text.Text reason){}
            };
        }
        @Override public void tick(){super.tick();if(!isRemoved()){playerTick();if(isAlive()&&getHealth()<floor)setHealth(floor);}}
    }
    private volatile Mate mate;
    private final StringBuilder bossLog=new StringBuilder();private String bossDoing="";
    private final Set<String> tried=new HashSet<>();private final Map<Integer,Integer> tryRelease=new HashMap<>();
    /**
     * SUMMON with a boss (2.69.0): this tick's row of boss.csv, read on the server: his health, his anger, what he does,
     * whom he fights, how far from the player, where, which way he faces, whether his stand is out, his guard and dash,
     * the player's health and whether they are held, and the boxes he left; the log gets what he does each time it changes.
     */
    private void logBoss(){
        float t=clock()-mc.getTickDelta();
        server(p->{
            var b=dev.sangwoo.celestialfall.boss.Bosses.live(dev.sangwoo.celestialfall.boss.BossKind.JOTARO);
            if(opt("pfull",0)>0&&p.getHealth()<70)p.setHealth(p.getMaxHealth());
            int boxes=p.getServerWorld().getEntitiesByClass(dev.sangwoo.celestialfall.loot.FieldLootEntity.class,p.getBoundingBox().expand(40),e->e.loose()).size();
            if(b==null){if(bossLog.length()>0||boxes>0)bossLog.append(String.format(Locale.ROOT,"%.3f,,,,,,,,,,,,%.1f,%d,%d,%s%n",t,p.getHealth(),dev.sangwoo.celestialfall.combat.CombatManager.incapacitated(p)?1:0,boxes,mate==null?"":String.format(Locale.ROOT,"%.1f",mate.getHealth())));return;}
            var br=b.brain();var s=dev.sangwoo.celestialfall.combat.CombatManager.peek(b);long now=b.getWorld().getTime();
            String doing=br.doing();var target=br.target();
            if(!doing.equals(bossDoing)){log.append("  boss ").append(doing.isEmpty()?"-":doing).append(" at ").append(String.format(Locale.ROOT,"%.1f",t)).append('\n');bossDoing=doing;}
            bossLog.append(String.format(Locale.ROOT,"%.3f,%.1f,%d,%s,%s,%.2f,%.2f,%.2f,%.1f,%d,%d,%d,%.1f,%d,%d,%s%n",t,b.getHealth(),br.angry()?1:0,doing.replace(',',' '),target==null?"":target.getName().getString(),
                Math.sqrt(b.squaredDistanceTo(p)),b.getX(),b.getZ(),b.getYaw(),((StandHolder)(Object)b).botand$isStandOut()?1:0,s!=null&&s.guarding?1:0,s!=null&&s.dashing(now)?1:0,
                p.getHealth(),dev.sangwoo.celestialfall.combat.CombatManager.incapacitated(p)?1:0,boxes,mate==null?"":String.format(Locale.ROOT,"%.1f",mate.getHealth())));
        });
    }
    private final StringBuilder standLog=new StringBuilder();private boolean standOutLogged,dummyBroken;
    /** SUMMON: this tick's row of stand.csv, and a line in the log each time the stand comes out or goes back. */
    private void logStand(){
        boolean out=((StandHolder)mc.player).botand$isStandOut();
        if(out!=standOutLogged){log.append("  stand ").append(out?"out":"in").append(" at ").append(age).append('\n');standOutLogged=out;}
        var state=dev.sangwoo.celestialfall.client.stand.StandPresence.state(mc.player.getUuid());
        var l=dev.sangwoo.celestialfall.client.stand.StandPresence.shown(mc.player.getUuid());
        standLog.append(String.format(Locale.ROOT,"%.3f,%d,%.3f,%d",clock()-mc.getTickDelta(),out?1:0,state==null?-1:state[0],state==null?-1:(int)state[1]));
        int aura=dev.sangwoo.celestialfall.client.stand.StandAuraLayer.drawnLast();
        if(l==null)standLog.append(",,,,,,,,,,,,").append(aura);
        else standLog.append(String.format(Locale.ROOT,",%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.3f,%.4f,%d",l.out,l.alpha,l.scale,l.weights[0],l.weights[1],l.weights[2],l.wind,l.gust,l.lean,l.speed,l.glow,aura));
        // 2.54.0: the punches: the last one's number, its age (seconds), whether it landed and on how many, how far the
        // stand is round in front, and the share of each punch stance in the look drawn (guard, then each punch loaded and thrown)
        var punch=dev.sangwoo.celestialfall.client.stand.StandAttack.describe(mc.player,mc.getTickDelta());
        var shape=dev.sangwoo.celestialfall.client.stand.StandAttack.shape(mc.player.getUuid(),mc.getTickDelta(),0,new dev.sangwoo.celestialfall.client.stand.StandPunch.Shape());
        standLog.append(',').append(punch==null?",,,,":punch).append(',').append(shape==null?"":String.format(Locale.ROOT,"%.4f",shape.engage));
        for(int i=3;i<12;i++)standLog.append(',').append(l==null?"":String.format(Locale.ROOT,"%.4f",l.weights[i]));
        // 2.57.0: the rush: its age (seconds), the blows heard, how many the last struck, whether it is on and how loud its
        // blows sound; the training dummy's health and the player's speed; and the share of each rush stance in the look
        var rush=dev.sangwoo.celestialfall.client.stand.StandRush.describe(mc.player,mc.getTickDelta());
        standLog.append(',').append(rush==null?",,,,":rush);
        var dummy=mc.world.getEntityById(dummyId);
        standLog.append(',').append(dummy instanceof net.minecraft.entity.LivingEntity le?String.format(Locale.ROOT,"%.2f",le.getHealth()):"");
        standLog.append(',').append(String.format(Locale.ROOT,"%.5f",mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED)));
        for(int i=12;i<18;i++)standLog.append(',').append(l==null?"":String.format(Locale.ROOT,"%.4f",l.weights[i]));
        // 2.59.0: the heavy punch's two stances; the training dummy's guard (up, broken) and where it is (its flight)
        for(int i=18;i<20;i++)standLog.append(',').append(l==null?"":String.format(Locale.ROOT,"%.4f",l.weights[i]));
        if(dummy instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity t){
            boolean broken=t.brokenUntil()>mc.world.getTime();
            standLog.append(String.format(Locale.ROOT,",%d,%d,%.3f,%.3f,%.3f",t.guarding()?1:0,broken?1:0,t.getX(),t.getY(),t.getZ()));
            if(broken&&!dummyBroken)log.append("  dummy guard broken at ").append(age).append('\n');
            dummyBroken=broken;
        }else standLog.append(",,,,,");
        // 2.60.0: the Star Finger's two stances, how far its fingers are open and out, how long they were drawn (blocks)
        // and how far ahead of the player's feet their tips reach; the training dummy incapacitated, and its speed
        for(int i=20;i<22;i++)standLog.append(',').append(l==null?"":String.format(Locale.ROOT,"%.4f",l.weights[i]));
        standLog.append(',').append(l==null?",":String.format(Locale.ROOT,"%.4f,%.4f",l.fingerOpen,l.fingerExt));
        var finger=dev.sangwoo.celestialfall.client.stand.StarPlatinumRenderer.finger(mc.player.getUuid());
        if(finger==null)standLog.append(",,");
        else{
            var ahead=finger.tip().subtract(mc.player.getLerpedPos(mc.getTickDelta()));var f=net.minecraft.util.math.Vec3d.fromPolar(0,mc.player.getYaw());
            double len=finger.root().distanceTo(finger.tip()),reach=ahead.x*f.x+ahead.z*f.z;
            standLog.append(String.format(Locale.ROOT,",%.3f,%.3f",len,reach));
            if(len>fingerLongest+.05){fingerLongest=len;fingerLogged=true;}
            else if(fingerLogged&&len<fingerLongest-.3){log.append(String.format(Locale.ROOT,"  fingers out %.2f blocks (tips %.2f blocks ahead) by %d%n",fingerLongest,fingerReach,age));fingerLogged=false;}
            if(len>=fingerLongest-.001)fingerReach=reach;
        }
        if(finger==null&&fingerLogged){log.append(String.format(Locale.ROOT,"  fingers out %.2f blocks (tips %.2f blocks ahead) by %d%n",fingerLongest,fingerReach,age));fingerLogged=false;}
        if(finger==null)fingerLongest=0;
        if(dummy instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity t){
            boolean disabled=t.disabledUntil()>mc.world.getTime();
            standLog.append(String.format(Locale.ROOT,",%d,%.5f",disabled?1:0,t.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED)));
            if(disabled!=dummyDisabled)log.append("  dummy ").append(disabled?"incapacitated":"free again").append(" at ").append(age).append('\n');
            dummyDisabled=disabled;
        }else standLog.append(",,");
        // 2.61.0: the inhale's stances (2.62.0: and the strain it shudders with); the inhale (its age in seconds, whether
        // it is on, whether it holds anyone now, how many guards were seen holding it off lately, how hard it sucks);
        // where the training dummy is from the player (ahead along their facing, to their right: its drag)
        for(int i=22;i<25;i++)standLog.append(',').append(l==null?"":String.format(Locale.ROOT,"%.4f",l.weights[i]));
        var inhale=dev.sangwoo.celestialfall.client.stand.StandInhale.describe(mc.player,mc.getTickDelta());
        standLog.append(',').append(inhale==null?",,,,":inhale);
        if(dummy!=null){
            var d=dummy.getLerpedPos(mc.getTickDelta()).subtract(mc.player.getLerpedPos(mc.getTickDelta()));var f=net.minecraft.util.math.Vec3d.fromPolar(0,mc.player.getYaw());
            standLog.append(String.format(Locale.ROOT,",%.3f,%.3f",d.x*f.x+d.z*f.z,-d.x*f.z+d.z*f.x));
        }else standLog.append(",,");
        // 2.64.0: the beatdown: its cutscene (its age in seconds, whether it ended, whether it was cut short, how high the
        // black bars are), whether its camera has the player, how strongly the player's body flashes sky blue, its two
        // stances (gripping the collar, the uppercut), and where the one struck is shown from the player (ahead along the
        // blow, to its right, up: the cutscene's body, not where it really is)
        var beat=dev.sangwoo.celestialfall.client.stand.StandBeatdown.describe(mc.player,mc.getTickDelta());
        standLog.append(',').append(beat==null?",,,":beat);
        standLog.append(',').append(dev.sangwoo.celestialfall.client.stand.StandBeatdown.cinematic()!=null?1:0);
        standLog.append(String.format(Locale.ROOT,",%.3f",dev.sangwoo.celestialfall.client.CutsceneFlash.local(mc.getTickDelta())));
        for(int i=25;i<27;i++)standLog.append(',').append(l==null?"":String.format(Locale.ROOT,"%.4f",l.weights[i]));
        var shown=dummy==null?null:dev.sangwoo.celestialfall.client.stand.StandBeatdown.offset(dummy,mc.getTickDelta());
        if(shown!=null){
            var d=dummy.getLerpedPos(mc.getTickDelta()).add(shown).subtract(mc.player.getLerpedPos(mc.getTickDelta()));var f=net.minecraft.util.math.Vec3d.fromPolar(0,mc.player.getYaw());
            standLog.append(String.format(Locale.ROOT,",%.3f,%.3f,%.3f",d.x*f.x+d.z*f.z,-d.x*f.z+d.z*f.x,d.y));
        }else standLog.append(",,,");
        // 2.65.0: the beatdown's own blow's two stances (drawn back, striking), its heavy punch's (the last blow's), and
        // how many bodies the last frame made glow sky blue (StandAuraLayer)
        for(int i:new int[]{27,28,18,19})standLog.append(',').append(l==null?"":String.format(Locale.ROOT,"%.4f",l.weights[i]));
        standLog.append(',').append(dev.sangwoo.celestialfall.client.stand.StandAuraLayer.glowedLast());
        // 2.66.0: whether the one struck is left undrawn this frame (the cutscene's shots from their own eyes)
        standLog.append(',').append(dummy!=null&&dev.sangwoo.celestialfall.client.stand.StandBeatdown.hidden(dummy)?1:0);
        // 2.67.0: the time stop (its age, whether time is stopped, when it moved again, cut short, whether this client's
        // screen is held, its wave: how far it has spread, the far world's grade, the flash, the negative, since 2.68.0
        // how brightly its bubble shows, its cracks and the impact frame), the call's two
        // stances, the player's health, whether this client holds the dummy stopped and the player stopped
        var stop=dev.sangwoo.celestialfall.client.stand.StandTimeStop.describe(mc.getTickDelta(),mc.gameRenderer.getCamera().getPos());
        standLog.append(',').append(stop==null?",,,,,,,,,,,":stop);
        for(int i=29;i<31;i++)standLog.append(',').append(l==null?"":String.format(Locale.ROOT,"%.4f",l.weights[i]));
        standLog.append(String.format(Locale.ROOT,",%.2f,%d,%d",mc.player.getHealth(),dummy!=null&&!Float.isNaN(dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillDelta(dummy))?1:0,
            dev.sangwoo.celestialfall.client.stand.StandTimeStop.holds(mc.player)?1:0));
        // 2.68.0: whether the player can only walk (their combat state locked: staggered by a basic attack's blow, among
        // others), staggered as the server last said, and the training dummy staggered (the server's own state) and its blow
        var snap=dev.sangwoo.celestialfall.client.CombatClient.snapshot(mc.player.getUuid());double clock=mc.world.getTime()+mc.getTickDelta();
        boolean staggered=snap.staggered(clock);
        boolean dummyStaggered=false;int dummyStrike=0;
        if(dummy!=null&&mc.getServer()!=null){var sd=mc.getServer().getOverworld().getEntity(dummy.getUuid());
            if(sd instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity t){dummyStaggered=t.combatState().staggered(mc.getServer().getOverworld().getTime());dummyStrike=t.combatState().attack;}}
        standLog.append(String.format(Locale.ROOT,",%d,%d,%d,%d",dev.sangwoo.celestialfall.client.CombatClient.actionLocked()?1:0,staggered?1:0,dummyStaggered?1:0,dummyStrike));
        if(staggered!=playerStaggered){log.append("  player ").append(staggered?"staggered":"free again").append(" at ").append(age).append(String.format(Locale.ROOT," (until %d)%n",snap.staggerUntil()));playerStaggered=staggered;}
        if(dummyStaggered!=dummyStaggeredLogged){log.append("  dummy ").append(dummyStaggered?"staggered":"free of its stagger").append(" at ").append(age).append('\n');dummyStaggeredLogged=dummyStaggered;}
        standLog.append('\n');
        if(stop!=null){
            String[] v=stop.split(",");String phase=v[3].equals("1")?"cut short":v[1].equals("1")?"stopped":!v[2].equals("-1.000")?"moving":"called";
            if(!phase.equals(stopLogged)){log.append("  time stop ").append(phase).append(" at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));stopLogged=phase;}
            boolean still=v[4].equals("1");
            if(still!=stillLogged){log.append("  screen ").append(still?"held still":"moving again").append(" at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));stillLogged=still;}
        }
        if(Math.abs(mc.player.getHealth()-playerHealth)>.05f){
            if(playerHealth>=0)log.append(String.format(Locale.ROOT,"  player health %.1f -> %.1f at %d%n",playerHealth,mc.player.getHealth(),age));
            playerHealth=mc.player.getHealth();
        }
        if(beat!=null){
            String[] v=beat.split(",");boolean ended=v[1].equals("1");
            if(!beatOn){log.append("  beatdown cutscene from ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));beatOn=true;}
            if(ended&&!beatEnded){log.append("  beatdown ").append(v[2].equals("1")?"cut short":"over").append(" at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));beatEnded=true;}
        }else{beatOn=false;beatEnded=false;}
        if(dummy instanceof net.minecraft.entity.LivingEntity le&&Math.abs(le.getHealth()-dummyHealth)>.05f){
            if(dummyHealth>=0)log.append(String.format(Locale.ROOT,"  dummy health %.1f -> %.1f at %d%n",dummyHealth,le.getHealth(),age));
            dummyHealth=le.getHealth();
        }
        if(inhale!=null){
            String[] v=inhale.split(",");boolean on=v[1].equals("1");String holds=v[2];int guards=Integer.parseInt(v[3]);
            if(on&&!inhaleOn)log.append("  inhale on at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));
            if(!holds.equals(inhaleCaught))log.append(holds.equals("1")?"  inhale caught someone at ":"  inhale holds no one at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));
            if(guards>inhaleGuards)log.append("  a guard held the inhale off at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));
            if(!on&&inhaleOn)log.append("  inhale off at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));
            inhaleOn=on;inhaleCaught=holds;inhaleGuards=guards;
        }else{inhaleOn=false;inhaleCaught="0";inhaleGuards=0;}
        if(rush!=null){
            String[] v=rush.split(",");boolean on=v[3].equals("1");int blows=Integer.parseInt(v[1]);
            if(on&&!rushOn)log.append("  rush on at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));
            if(blows>rushBlows)log.append("  rush blow ").append(blows).append(" struck ").append(v[2]).append(" at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[0]));
            if(!on&&rushOn)log.append("  rush off at ").append(age).append(String.format(Locale.ROOT," (its age %s s, %d blows heard)%n",v[0],blows));
            rushOn=on;rushBlows=blows;
        }else{rushOn=false;rushBlows=0;}
        if(punch!=null){
            String[] v=punch.split(",");String key=v[0]+"/"+v[2];
            if(!key.equals(punchLogged)){
                if(!v[0].equals(punchLogged.split("/")[0])||punchLogged.isEmpty()||v[2].equals("0"))log.append("  punch ").append(v[0]).append(" seen at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[1]));
                if(v[2].equals("1"))log.append("  punch ").append(v[0]).append(" landed on ").append(v[3]).append(" at ").append(age).append(String.format(Locale.ROOT," (its age %s s)%n",v[1]));
                punchLogged=key;
            }
        }
    }
    private String punchLogged="";private boolean rushOn;private int rushBlows;private final StringBuilder cameraLog=new StringBuilder();private String worldPass=",,,,,,,";
    private boolean beatOn,beatEnded;private float dummyHealth=-1;
    /** 2.67.0: the fake Star Platinum user of the shot (server side), and what the log has said of the time stop and the player's health. */
    private net.minecraft.server.network.ServerPlayerEntity fakePlayer;private String stopLogged="";private boolean stillLogged;private float playerHealth=-1;
    private boolean playerStaggered,dummyStaggeredLogged;
    private double fingerLongest,fingerReach;private boolean fingerLogged,dummyDisabled;
    private boolean inhaleOn;private String inhaleCaught="0";private int inhaleGuards;
    /** The UI shot's log: screens opened, the reset question, the player list, deaths and respawns, chat lines. */
    private void logInterface()throws Exception{
        String screen=mc.currentScreen==null?"-":mc.currentScreen.getClass().getSimpleName();
        if(!screen.equals(screenLogged)){log.append("  screen ").append(screen).append(" at ").append(age).append('\n');screenLogged=screen;}
        if(mc.player==null||mc.world==null){if(!worldClosed){log.append("  world closed at ").append(age).append('\n');worldClosed=true;}return;}
        boolean paused=mc.isPaused();if(paused!=pausedLogged){log.append("  paused ").append(paused).append(" at ").append(age).append('\n');pausedLogged=paused;}
        boolean asking=mc.currentScreen instanceof dev.sangwoo.celestialfall.client.ui.BotandPauseScreen s&&s.asking();
        if(asking!=askingLogged){log.append("  question ").append(asking?"up":"down").append(" at ").append(age).append('\n');askingLogged=asking;}
        boolean hidden=dev.sangwoo.celestialfall.client.ui.PlayerPanel.hidden();
        if(hidden!=hiddenLogged){log.append("  player list ").append(hidden?"sent away":"back").append(" at ").append(age).append('\n');hiddenLogged=hidden;}
        boolean alive=mc.player.isAlive();
        if(alive!=aliveLogged){log.append("  player ").append(alive?"respawned":"died").append(" at ").append(age).append(String.format(Locale.ROOT," (health %.1f)",mc.player.getHealth())).append('\n');aliveLogged=alive;}
        var f=net.minecraft.client.gui.hud.ChatHud.class.getDeclaredField("messages");f.setAccessible(true);
        var lines=(List<net.minecraft.client.gui.hud.ChatHudLine>)f.get(mc.inGameHud.getChatHud());
        if(chatLogged<0)chatLogged=lines.size();
        // newest first
        for(int i=lines.size()-chatLogged-1;i>=0;i--)log.append("  chat \"").append(lines.get(i).content().getString()).append("\" at ").append(age).append('\n');
        chatLogged=lines.size();
    }
    private void frame()throws Exception{
        if(phase!=4||current==null)return;
        float t=clock();if(t<-2||t>length(current))return;
        if(shake&&"G".equals(current))logShake(t);
        // 2.67.0: where the camera was for each frame of a stand shot (and its field of view), to camera.csv
        if("SUMMON".equals(current)){var cam=mc.gameRenderer.getCamera();var pos=cam.getPos();
            var m=net.minecraft.client.render.GameRenderer.class.getDeclaredMethod("getFov",net.minecraft.client.render.Camera.class,float.class,boolean.class);m.setAccessible(true);
            cameraLog.append(String.format(Locale.ROOT,"%.2f,%.3f,%.3f,%.3f,%.2f,%.2f,%.3f,%.3f,%.2f,%s%n",t,pos.x,pos.y,pos.z,cam.getYaw(),cam.getPitch(),BotandCamera.STATE.distance,BotandCamera.STATE.collisionDistance,
                (double)m.invoke(mc.gameRenderer,cam,mc.getTickDelta(),true),worldPass));}
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
        if(WORLD!=null){
            // a real map: nothing is built into it; noon, clear, survival, money enough for the pickaxe
            w.setTimeOfDay(6000);w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,w.getServer());w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,w.getServer());
            w.setWeather(999999,0,false,false);p.getServer().setPvpEnabled(true);p.changeGameMode(GameMode.SURVIVAL);
            ProgressManager.set(p,new ProgressProfile(1,0,0,"kageno",6000,0));NoCooldownState.get(p.getServer()).set(p.getUuid(),true);
            p.getInventory().clear();p.currentScreenHandler.syncState();dummyId=-1;
            log.append("map ").append(WORLD).append(" spawn ").append(w.getSpawnPos().toShortString()).append('\n');
            return;
        }
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
        if(WORLD!=null){
            var w=p.getServerWorld();
            // each entry starts with no meteors: the last one's rocks go without a sound or a crumble
            var meteors=dev.sangwoo.celestialfall.meteor.MeteorState.get(w);
            for(var m:meteors.all()){meteors.clear(m.slot());var rock=w.getEntity(m.uuid());if(rock!=null)rock.discard();}
            // and the map's own minute roll a full minute away (no meteor of its own drops into an entry)
            meteors.nextRoll(w.getTime()+dev.sangwoo.celestialfall.meteor.MeteorRules.ROLL_TICKS);
            if(opts.containsKey("time"))w.setTimeOfDay((long)opt("time",6000));
            double[] at;var sp=w.getSpawnPos();
            if(opts.containsKey("at")){String[] v=opts.get("at").split("[/,]");at=new double[5];for(int i=0;i<v.length;i++)at[i]=Double.parseDouble(v[i].trim());}
            else at=new double[]{sp.getX()+.5,sp.getY(),sp.getZ()+.5,180,0};
            p.networkHandler.requestTeleport(at[0],at[1],at[2],(float)at[3],(float)at[4]);p.setYaw((float)at[3]);p.setHeadYaw((float)at[3]);p.setBodyYaw((float)at[3]);p.setPitch((float)at[4]);
            p.setHealth(p.getMaxHealth());p.setVelocity(0,0,0);
            return;
        }
        NoCooldownState.get(p.getServer()).set(p.getUuid(),!opts.containsKey("cool"));
        p.getItemCooldownManager().remove(CelestialFall.ATOMIC_SWORD);
        // the figure shots: no player in the picture (and no stand of theirs), last entry's figures gone
        for(var f:p.getServerWorld().getEntitiesByClass(dev.sangwoo.celestialfall.stand.StarPlatinumEntity.class,p.getBoundingBox().expand(64),e->true))f.discard();
        boolean figure="STAND".equals(current);p.setInvisible(figure);
        // the stand of the entry: none for the figure, Star Platinum (in) for SUMMON, Kageno for the rest
        String stand=figure?"none":"SUMMON".equals(current)?"star_platinum":"kageno";
        if(!stand.equals(ProgressManager.profile(p).stand()))ProgressManager.set(p,ProgressManager.profile(p).withStand(stand));
        ((StandHolder)p).botand$setStandOut(false);
        // "time=T" (2.52.0): the time of day for this entry on the flat world too (the stand's aura at night)
        if(opts.containsKey("time"))p.getServerWorld().setTimeOfDay((long)opt("time",6000));
        int y=p.getServerWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,0,0);
        float yaw=facing(opt("face",0));
        p.networkHandler.requestTeleport(.5,y,.5,yaw,0);p.setYaw(yaw);p.setHeadYaw(yaw);p.setBodyYaw(yaw);p.setHealth(p.getMaxHealth());p.setVelocity(0,0,0);
        var d=p.getServerWorld().getEntityById(dummyId);
        // "dguard=1" (2.59.0): the training dummy that holds its guard up (for the heavy punch breaking it)
        // "dattack=1" (2.68.0): the training dummy that throws basic-attack combos at the player (its blows stagger them)
        var kind=opt("dguard",0)>0?dev.sangwoo.celestialfall.training.DummyKind.GUARD:opt("dattack",0)>0?dev.sangwoo.celestialfall.training.DummyKind.ATTACK:dev.sangwoo.celestialfall.training.DummyKind.IDLE;
        if(d instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity t&&t.kind()!=kind){d.discard();d=null;}
        if(!(d instanceof net.minecraft.entity.LivingEntity)||!d.isAlive()){
            var dummy=TrainingDummies.TYPES.get(kind).create(p.getServerWorld());dummy.refreshPositionAndAngles(.5,y,3.5,180,0);p.getServerWorld().spawnEntity(dummy);dummyId=dummy.getId();d=dummy;
        }
        actors(p.getServerWorld(),y);
        if(d instanceof net.minecraft.entity.LivingEntity l){
            var f=net.minecraft.util.math.Vec3d.fromPolar(0,yaw);var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
            boolean show=opt("dummy","STAND".equals(current)||"SUMMON".equals(current)?0:1)>0;var at=new net.minecraft.util.math.Vec3d(.5,y,.5).add(f.multiply(opt("dfw",3))).add(right.multiply(opt("drt",0)));
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
    private void camera(){float d=opt("dist",camDist);BotandCamera.STATE.yaw=camLook()+turn();BotandCamera.STATE.pitch=opt("pitch",camPitch);BotandCamera.STATE.targetDistance=d;BotandCamera.STATE.distance=d;BotandCamera.STATE.collisionDistance=d;
        // "aimrock=1": with a rock within reach the camera looks at it from over the player's shoulder (a click aims where the camera looks)
        if(opts.containsKey("aimrock")&&phase==4&&mc.player!=null)for(var e:mc.world.getEntities())if(e instanceof dev.sangwoo.celestialfall.meteor.MeteorEntity m&&m.squaredDistanceTo(mc.player)<49){
            var v=m.getPos().add(0,.55,0).subtract(mc.player.getEyePos());
            BotandCamera.STATE.yaw=(float)Math.toDegrees(Math.atan2(-v.x,v.z))+opt("rockyaw",0);BotandCamera.STATE.pitch=(float)-Math.toDegrees(Math.atan2(v.y,v.horizontalLength()))+opt("rockpitch",6);break;}
        // "bface=1" (2.69.0): the player keeps their camera (and so their blows) on the boss, as a player fighting it does,
        // from tick "bfrom" (0); "bchase=D" walks them at it while it is further than D blocks. Film it with a far= camera.
        if(opts.containsKey("bface")&&phase==4&&mc.player!=null&&age>=opt("bfrom",0)){
            net.minecraft.entity.player.PlayerEntity boss=null;
            for(var info:dev.sangwoo.celestialfall.client.boss.BossClient.all()){var e=mc.world.getPlayerByUuid(info.id());if(e!=null&&e.isAlive()){boss=e;break;}}
            if(boss!=null){
                var v=boss.getPos().add(0,1.2,0).subtract(mc.player.getEyePos());
                BotandCamera.STATE.yaw=(float)Math.toDegrees(Math.atan2(-v.x,v.z));BotandCamera.STATE.pitch=(float)-Math.toDegrees(Math.atan2(v.y,v.horizontalLength()));
                if(opts.containsKey("bchase"))mc.options.forwardKey.setPressed(v.horizontalLength()>opt("bchase",3));
            }else if(opts.containsKey("bchase"))mc.options.forwardKey.setPressed(false);
        }
    }
    private void face(ServerPlayerEntity p,float face){float y=facing(face);p.setYaw(y);p.setHeadYaw(y);p.setBodyYaw(y);p.networkHandler.requestTeleport(p.getX(),p.getY(),p.getZ(),y,0);}

    @Override public void onInitializeClient(){
        instance=this;
        ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{tick();}catch(Throwable t){fail(t);}});
        // 2.67.0: the world pass's projection and view of each frame (camera.csv), and the viewport and framebuffer then
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(c->{var pr=c.projectionMatrix();var v=c.matrixStack().peek().getPositionMatrix();
            int[] vp=new int[4];org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,vp);
            worldPass=String.format(Locale.ROOT,"%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d",pr.m00(),pr.m11(),v.m11(),v.m12(),v.m21(),vp[2],vp[3],
                org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)==MinecraftClient.getInstance().getFramebuffer().fbo?1:0);});
        // the UI shot's pointer: moved before a screen draws, drawn after it (Fabric makes a screen's events anew each
        // time it is set up, coming back from the options screen included, so they are registered every time)
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.beforeRender(screen).register((s,d,x,y,delta)->{try{glide();}catch(Throwable t){fail(t);}});
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s,d,x,y,delta)->pointer(d));
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{try{actorTick(server);}catch(Throwable t){if(mc!=null)mc.execute(()->fail(t));}});
        // every sound the game starts, with the shot's clock and the time (to lay the recording under the film)
        if(AUDIO)ClientTickEvents.START_CLIENT_TICK.register(client->{if(client.getSoundManager()!=null&&!listening){listening=true;client.getSoundManager().registerListener((sound,set)->{
            if(phase==4)soundRows.append(String.format(Locale.ROOT,"%.3f,%d,%s%n",clock(),System.nanoTime(),sound.getId()));});}});
    }
    private void tick()throws Exception{
        age++;if((System.nanoTime()-started)/1e9>1500*SLOW)throw new IllegalStateException("timeout");
        if(mc.getToastManager()!=null)mc.getToastManager().clear();
        if(phase>=1&&phase<99&&mc.world!=null&&mc.currentScreen instanceof dev.sangwoo.celestialfall.client.title.BotandTitleScreen)mc.setScreen(null);
        if(SLOW>1&&!slowed&&phase>=3){var f=MinecraftClient.class.getDeclaredField("renderTickCounter");f.setAccessible(true);var counter=f.get(mc);var t=counter.getClass().getDeclaredField("tickTime");t.setAccessible(true);t.setFloat(counter,50f*SLOW);slowed=true;}
        switch(phase){
            case 0->{if(mc.currentScreen!=null&&mc.getOverlay()==null&&age>100){
                if(LANG!=null&&!LANG.equals(mc.options.language)){mc.options.language=LANG;mc.getLanguageManager().setLanguage(LANG);mc.reloadResources();age=0;return;}
                Files.createDirectories(OUT);var o=mc.options;
                // the recording run: no music over the game's own sounds
                if(AUDIO){o.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MUSIC).setValue(0.0);o.getSoundVolumeOption(net.minecraft.sound.SoundCategory.RECORDS).setValue(0.0);}
                o.getViewDistance().setValue(6);o.getMaxFps().setValue(260);o.getGuiScale().setValue(2);mc.onResolutionChanged();o.hudHidden=true;o.getBobView().setValue(false);
                mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                if(WORLD!=null)mc.createIntegratedServerLoader().start(new net.minecraft.client.gui.screen.TitleScreen(),WORLD);
                else{
                var info=new LevelInfo("ShotsFlat"+System.nanoTime(),GameMode.CREATIVE,false,Difficulty.EASY,true,new GameRules(),DataConfiguration.SAFE_MODE);
                mc.createIntegratedServerLoader().createAndStart(info.getLevelName(),info,new GeneratorOptions(1,false,false),r->r.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder());
                }
                phase=1;age=0;}}
            case 1->{
                if(mc.currentScreen instanceof dev.sangwoo.celestialfall.client.title.BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);}
                if(mc.player!=null&&mc.world!=null&&mc.currentScreen==null&&age>60){server(this::setup);phase=2;age=0;}}
            case 2->{if(WORLD!=null&&!mc.isWindowFocused()){log.append("window focus given (was ").append(mc.isWindowFocused()).append(")\n");mc.onWindowFocusChanged(true);}
                if(age>60&&StandClient.visible(mc.player)){phase=3;age=0;}else if(age>400)throw new IllegalStateException("stand never visible, map="+MapClient.enabled());}
            case 3->{ // next skill
                if(age==1){if(queue.isEmpty()){for(var j:jobs)j.get();Files.writeString(OUT.resolve("log.txt"),log);mc.scheduleStop();phase=99;return;}String entry=queue.poll();String[] parts=entry.split(";");current=parts[0];int n=repeats.merge(current,1,Integer::sum);folder=n==1?current:current+"."+n;opts.clear();for(int i=1;i<parts.length;i++){String[] kv=parts[i].split("=",2);opts.put(kv[0],kv.length>1?kv[1]:"1");}
                    far=null;if(opts.containsKey("far")){String[] v=opts.get("far").split("[/,]");double[] f=new double[v.length];for(int i=0;i<v.length;i++)f[i]=Double.parseDouble(v[i].trim());far=f;}
                    orbit=null;if(opts.containsKey("orbit")){String[] v=opts.get("orbit").split("[/,]");double[] f=new double[v.length];for(int i=0;i<v.length;i++)f[i]=Double.parseDouble(v[i].trim());orbit=f;}
                    actors.clear();if(opts.containsKey("act"))for(String spec:opts.get("act").split("\\+")){String[] v=spec.split(":");actors.add(new Actor(v[0],Double.parseDouble(v[1]),Float.parseFloat(v[2]),Double.parseDouble(v[3]),v[4]));}
                    track=opts.containsKey("track")?opts.get("track").split(":"):null;shake=opts.containsKey("shake")&&!"0".equals(opts.get("shake"));
                    HOLDS.clear();if(opts.containsKey("holds"))for(String v:opts.get("holds").split("/"))HOLDS.add(!"0".equals(v.trim()));
                    mc.options.hudHidden=!opts.containsKey("hud")||"0".equals(opts.get("hud"));
                    server(this::reset);
                    // and none of the last entry's falls, smoke or debris on this client
                    if(WORLD!=null){var clear=Class.forName("dev.sangwoo.celestialfall.client.meteor.MeteorFx").getDeclaredMethod("clear");clear.setAccessible(true);clear.invoke(null);}
                    int view=(int)opt("view",Integer.getInteger("botand.shots.view",6));if(mc.options.getViewDistance().getValue()!=view){mc.options.getViewDistance().setValue(view);mc.worldRenderer.reload();}}
                if(age==2)camera();
                if(age==2&&(opts.containsKey("far")||opts.containsKey("orbit")||opts.containsKey("bcam"))){castPos=mc.player.getPos();castYaw=mc.player.getYaw();}
                if(age==2)camera();
                // "walk=N": the player walks (W held, camera-relative) from N ticks before the cast to the end of the shot.
                int lead=opts.containsKey("far")||opts.containsKey("track")||opts.containsKey("orbit")||opts.containsKey("bcam")?90:30;
                if(opts.containsKey("walk")&&age==Math.max(3,lead-(int)opt("walk",10)))key(GLFW_KEY_W,true);
                if(AUDIO&&age==Math.max(3,lead-20)){markerNanos=System.nanoTime();mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),2f,1f));soundRows.setLength(0);tickRows.setLength(0);}
                if(age>=lead){camera();castPos=mc.player.getPos();castYaw=mc.player.getYaw();castTick=mc.world.getTime()+1;shotIndex=0;serverStart=-1;actorLog.setLength(0);shakeLog.setLength(0);motionLog.setLength(0);soundLog.setLength(0);sounded.clear();tried.clear();tryRelease.clear();filming=true;trigger(current);phase=4;age=0;}}
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
                    // the UI shot's keys and mouse
                    // "TRY_R@n" (also T, E, M1; 2.69.0): from tick n, the key at the first tick the player is free to act
                    // (CombatClient.actionLocked) with the boss within its reach (R 3.8, T 2.4 to 4.8, E 3, M1 3.2 blocks),
                    // as a player waits for an opening; E is held "tryhold" ticks (40), the attack 6; it gives up after 120
                    if(what.startsWith("TRY_")){String k=what.substring(4),id=what+"@"+at;
                        if(age>=at&&!tried.contains(id)){
                            net.minecraft.entity.player.PlayerEntity b=null;
                            for(var info:dev.sangwoo.celestialfall.client.boss.BossClient.all()){var e=mc.world.getPlayerByUuid(info.id());if(e!=null&&e.isAlive()){b=e;break;}}
                            double d=b==null?99:b.distanceTo(mc.player),near=k.equals("T")?2.4:0,reach=switch(k){case "R"->3.8;case "T"->4.8;case "E"->3.0;default->3.2;};
                            if(b!=null&&!CombatClient.actionLocked()&&d>=near&&d<=reach){
                                tried.add(id);log.append(String.format(Locale.ROOT,"  try %s pressed at %d (asked from %d, the boss %.2f blocks away)%n",k,age,at,d));
                                switch(k){case "R"->{key(GLFW_KEY_R,true);key(GLFW_KEY_R,false);}case "T"->{key(GLFW_KEY_T,true);key(GLFW_KEY_T,false);}
                                    case "E"->{key(GLFW_KEY_E,true);tryRelease.put(GLFW_KEY_E,age+(int)opt("tryhold",40));}
                                    default->{button(true);tryRelease.put(-1,age+6);}}
                            }else if(age>=at+120){tried.add(id);log.append("  try ").append(k).append(" gave up at ").append(age).append('\n');}
                        }
                        continue;}
                    if(what.startsWith("KEY_")){int code=switch(what.substring(4)){case "TAB"->GLFW_KEY_TAB;case "ESCAPE"->GLFW_KEY_ESCAPE;case "R"->GLFW_KEY_R;case "ENTER"->GLFW_KEY_ENTER;case "UP"->GLFW_KEY_UP;case "DOWN"->GLFW_KEY_DOWN;case "Q"->GLFW_KEY_Q;case "T"->GLFW_KEY_T;case "Y"->GLFW_KEY_Y;case "G"->GLFW_KEY_G;case "H"->GLFW_KEY_H;default->throw new IllegalArgumentException(what);};
                        if(age==at){log.append("  key ").append(what.substring(4)).append(" at ").append(age).append('\n');key(code,true);}if(age==at+1)key(code,false);continue;}
                    if(what.startsWith("MOVE_")){if(age==at){glideTo=null;double[] g=target(what.substring(5));double k=mc.getWindow().getScaleFactor();glideFrom=new double[]{mc.mouse.getX(),mc.mouse.getY()};glideTo=new double[]{g[0]*k,g[1]*k};glideAt=age;log.append("  move ").append(what.substring(5)).append(" at ").append(age).append('\n');}continue;}
                    if(what.equals("TP")||what.equals("TPB")){if(age==at)server(p->{String[] v=opts.get(what.equals("TP")?"tp":"tpb").split("[/,]");double[] d=new double[5];for(int i=0;i<v.length;i++)d[i]=Double.parseDouble(v[i].trim());
                            p.networkHandler.requestTeleport(d[0],d[1],d[2],(float)d[3],(float)d[4]);p.setYaw((float)d[3]);p.setHeadYaw((float)d[3]);p.setBodyYaw((float)d[3]);log.append("  teleported at ").append(age).append('\n');});continue;}
                    if(what.equals("FALL")||what.equals("FALLB")){if(age==at)server(p->{
                            String[] v=opts.get(what.equals("FALL")?"fall":"fallb").split("[/,]");var w=p.getServerWorld();
                            int slot=dev.sangwoo.celestialfall.meteor.MeteorState.get(w).firstEmpty();
                            var m=slot<0?null:dev.sangwoo.celestialfall.meteor.MeteorManager.launch(w,slot,new net.minecraft.util.math.Vec3d(Double.parseDouble(v[0]),w.getTopY(),Double.parseDouble(v[1])),opts.containsKey("from")?opt("from",0):Double.NaN);
                            log.append("  fall ").append(m==null?"refused":String.format(Locale.ROOT,"#%d to (%.1f, %.1f, %.1f) from (%.0f, %.0f, %.0f) at tick %d",m.slot(),m.x(),m.y(),m.z(),m.fromX(),m.fromY(),m.fromZ(),age)).append('\n');});continue;}
                    if(what.equals("CLEARMETEOR")){if(age==at)server(p->{int n=p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(),"botand meteor clear");log.append("  meteors cleared (").append(n).append(") at ").append(age).append('\n');});continue;}
                    if(what.startsWith("GIVE_")){if(age==at)server(p->{
                            var stack=switch(what.substring(5)){case "PICKAXE"->new net.minecraft.item.ItemStack(dev.sangwoo.celestialfall.meteor.MeteorRegistry.PICKAXE);case "FRAGMENTS"->new net.minecraft.item.ItemStack(dev.sangwoo.celestialfall.meteor.MeteorRegistry.FRAGMENT,3);case "STICK"->new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK);default->throw new IllegalArgumentException(what);};
                            p.getInventory().offerOrDrop(stack);p.currentScreenHandler.sendContentUpdates();log.append("  given ").append(what.substring(5)).append(" at ").append(age).append('\n');});continue;}
                    if(what.startsWith("KEYDOWN_")||what.startsWith("KEYUP_")){boolean down=what.startsWith("KEYDOWN_");int code=switch(what.substring(what.indexOf('_')+1)){case "E"->GLFW_KEY_E;case "W"->GLFW_KEY_W;case "A"->GLFW_KEY_A;case "S"->GLFW_KEY_S;case "D"->GLFW_KEY_D;case "Y"->GLFW_KEY_Y;default->throw new IllegalArgumentException(what);};
                        if(age==at){log.append("  key ").append(what).append(" at ").append(age).append('\n');key(code,down);}continue;}
                    if(what.startsWith("STANCE_")){if(age==at)server(p->{int stance=dev.sangwoo.celestialfall.stand.StarPlatinumStance.NAMES.indexOf(what.substring(7).toLowerCase(Locale.ROOT));
                            for(var f:p.getServerWorld().getEntitiesByClass(dev.sangwoo.celestialfall.stand.StarPlatinumEntity.class,p.getBoundingBox().expand(64),e->true))f.setStance(stance);
                            log.append("  stance ").append(what.substring(7)).append(" at ").append(age).append('\n');});continue;}
                    if(what.equals("CLICK")){if(age==at){log.append("  click at ").append(age).append(" on ").append(mc.currentScreen==null?"-":mc.currentScreen.getClass().getSimpleName()).append('\n');clickAt=age;button(true);}if(age==at+1)button(false);continue;}
                    // 2.54.0: the attack (the left button) held down and let go, through Minecraft's own mouse handler
                    if(what.equals("ATTACKDOWN")||what.equals("ATTACKUP")){if(age==at){log.append("  attack ").append(what.equals("ATTACKDOWN")?"down":"up").append(" at ").append(age).append('\n');button(what.equals("ATTACKDOWN"));}continue;}
                    // 2.67.0: a second Star Platinum user (a server-side fake player, its stand out) "cfw" blocks ahead of the
                    // player (and "crt" to their right), facing them: "FAKE@n" brings it in, "FAKEH@n" makes it stop time (H, its
                    // real rules: the player stands stopped in it), "HURT@n" deals the player "hurt" (4) through the game's own
                    // blow path from it (kept until time moves again), "UNFAKE@n" takes it away
                    if(what.equals("FAKE")){if(age==at)server(p->{
                            var w=p.getServerWorld();var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SP_User");
                            var fake=net.fabricmc.fabric.api.entity.FakePlayer.get(w,profile);
                            var f=net.minecraft.util.math.Vec3d.fromPolar(0,p.getYaw());var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
                            var to=p.getPos().add(f.multiply(opt("cfw",6))).add(right.multiply(opt("crt",0)));float yaw=p.getYaw()+180;
                            fake.refreshPositionAndAngles(to.x,p.getY(),to.z,yaw,0);fake.setHeadYaw(yaw);fake.setBodyYaw(yaw);
                            ((StandHolder)fake).botand$setStand("star_platinum");((StandHolder)fake).botand$setStandOut(true);
                            p.networkHandler.sendPacket(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(List.of(fake)));
                            boolean in=w.spawnEntity(fake);fakePlayer=fake;
                            log.append("  fake Star Platinum user ").append(in?"in":"refused").append(String.format(Locale.ROOT," at (%.2f, %.2f, %.2f) facing %.0f, tick %d%n",to.x,to.y,to.z,yaw,age));});continue;}
                    if(what.equals("FAKEH")){if(age==at)server(p->{boolean ok=fakePlayer!=null&&dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.request(fakePlayer);log.append("  fake H ").append(ok?"at ":"refused at ").append(age).append('\n');});continue;}
                    // "FAKEWALK@n": the fake user walks up to "fto" blocks (1.6) in front of the player and "frt" (0) to their right
                    // over "fwt" ticks (30), facing the way it walks; "FAKER@n": it throws its heavy punch (R, its real rules)
                    if(what.equals("FAKEWALK")){int n=(int)opt("fwt",30);if(age>=at&&age<at+n){int left=at+n-age;server(p->{var fake=fakePlayer;if(fake==null)return;
                            var f=net.minecraft.util.math.Vec3d.fromPolar(0,p.getYaw());var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
                            var to=p.getPos().add(f.multiply(opt("fto",1.6f))).add(right.multiply(opt("frt",0)));var next=fake.getPos().lerp(to,1.0/left);
                            var way=next.subtract(fake.getPos());if(way.horizontalLengthSquared()<1e-6)way=p.getPos().subtract(next);
                            float yaw=(float)Math.toDegrees(Math.atan2(way.z,way.x))-90;
                            fake.refreshPositionAndAngles(next.x,p.getY(),next.z,yaw,0);fake.setHeadYaw(yaw);fake.setBodyYaw(yaw);
                            if(left==1)log.append(String.format(Locale.ROOT,"  fake walked up to (%.2f, %.2f) by %d%n",next.x,next.z,age));});}continue;}
                    if(what.equals("FAKER")){if(age==at)server(p->{boolean ok=fakePlayer!=null&&dev.sangwoo.celestialfall.stand.StarPlatinumAttack.heavy(fakePlayer);log.append("  fake R ").append(ok?"at ":"refused at ").append(age).append('\n');});continue;}
                    if(what.equals("HURT")){if(age==at)server(p->{float amount=(float)opt("hurt",4);
                            boolean ok=dev.sangwoo.celestialfall.combat.CombatManager.damageWithoutKnockback(p,fakePlayer==null?p.getDamageSources().generic():p.getDamageSources().playerAttack(fakePlayer),amount);
                            log.append(String.format(Locale.ROOT,"  hurt %.1f %s at %d (health %.1f)%n",amount,ok?"landed":"refused",age,p.getHealth()));});continue;}
                    // "FLING@n": the player sent flying "fly" (2) blocks a tick away from the fake user and "flyup" (0.6) up, as a
                    // punch's throw is (held by a time stop that holds the player until time moves again)
                    if(what.equals("FLING")){if(age==at)server(p->{var from=fakePlayer==null?p.getPos().add(0,0,-1):fakePlayer.getPos();var away=p.getPos().subtract(from).multiply(1,0,1).normalize();
                            var v=away.multiply(opt("fly",2)).add(0,opt("flyup",.6f),0);boolean kept=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.deferLaunch(p,v);
                            if(!kept){p.setVelocity(v);p.velocityModified=true;}
                            log.append(String.format(Locale.ROOT,"  fling (%.2f, %.2f, %.2f) %s at %d%n",v.x,v.y,v.z,kept?"kept for when time moves":"now",age));});continue;}
                    // 2.69.0: the boss. "BOSS@n" brings Kujo Jotaro in "bfw" blocks (7) ahead of the player ("brt" to their right),
                    // facing them, neutral, with "bhp" health (his whole); "BOSSANGRY@n" angers him with the player as a blow of
                    // theirs would; "BOSSHP@n" sets his health to "bhp2"; "BOSSKILL@n" fells him with a blow of the player's (his
                    // reward, his boxes); "UNBOSS@n" takes him off. "pfull=1" keeps the player's health up (whole again under 70).
                    if(what.equals("BOSS")){if(age==at)server(p->{
                            var w=p.getServerWorld();var f=net.minecraft.util.math.Vec3d.fromPolar(0,p.getYaw());var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
                            var to=p.getPos().add(f.multiply(opt("bfw",7))).add(right.multiply(opt("brt",0)));
                            var b=dev.sangwoo.celestialfall.boss.Bosses.spawn(w,dev.sangwoo.celestialfall.boss.BossKind.JOTARO,new net.minecraft.util.math.Vec3d(to.x,p.getY(),to.z),p.getYaw()+180,(float)opt("bhp",0));
                            log.append(String.format(Locale.ROOT,"  boss %s at (%.2f, %.2f) tick %d, health %.0f%n",b==null?"refused":"in",to.x,to.z,age,b==null?0:b.getHealth()));});continue;}
                    if(what.equals("BOSSANGRY")){if(age==at)server(p->{var b=dev.sangwoo.celestialfall.boss.Bosses.live(dev.sangwoo.celestialfall.boss.BossKind.JOTARO);if(b!=null)dev.sangwoo.celestialfall.boss.Bosses.provoke(b,p);log.append("  boss angered at ").append(age).append('\n');});continue;}
                    if(what.equals("BOSSHP")){if(age==at)server(p->{var b=dev.sangwoo.celestialfall.boss.Bosses.live(dev.sangwoo.celestialfall.boss.BossKind.JOTARO);if(b!=null)b.setHealth((float)opt("bhp2",2400));log.append("  boss health set at ").append(age).append('\n');});continue;}
                    if(what.equals("BOSSKILL")){if(age==at)server(p->{var b=dev.sangwoo.celestialfall.boss.Bosses.live(dev.sangwoo.celestialfall.boss.BossKind.JOTARO);
                            if(b!=null){b.timeUntilRegen=0;boolean ok=b.damage(p.getDamageSources().playerAttack(p),b.getHealth()+50);log.append("  boss felled ").append(ok?"":"(refused) ").append("at ").append(age).append(String.format(Locale.ROOT," (player money %d)%n",ProgressManager.profile(p).money()));}});continue;}
                    if(what.equals("UNBOSS")){if(age==at)server(p->{var b=dev.sangwoo.celestialfall.boss.Bosses.live(dev.sangwoo.celestialfall.boss.BossKind.JOTARO);if(b!=null)dev.sangwoo.celestialfall.boss.Bosses.remove(b);log.append("  boss off at ").append(age).append('\n');});continue;}
                    // "MATE@n" (2.69.0): a second player on the server, "mfw" blocks (5) ahead of the player and "mrt" (3) to their
                    // right, facing the boss, with "mhp" health (60): a body hurt, staggered and thrown as a player's is, that
                    // plays nothing (its client is nobody's); never under "mfloor" (20). "MATEHP@n" sets its health to "mhp2",
                    // "PHP@n" the player's to "php"; "UNMATE@n" takes it off.
                    if(what.equals("MATE")){if(age==at)server(p->{
                            var w=p.getServerWorld();var m=new Mate(w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Partner"));
                            var f=net.minecraft.util.math.Vec3d.fromPolar(0,p.getYaw());var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
                            var to=p.getPos().add(f.multiply(opt("mfw",5))).add(right.multiply(opt("mrt",3)));float yaw=p.getYaw()+180;
                            m.refreshPositionAndAngles(to.x,p.getY(),to.z,yaw,0);m.setHeadYaw(yaw);m.setBodyYaw(yaw);
                            ProgressManager.set(m,ProgressManager.profile(m).withStand("none"));
                            for(var q:p.getServer().getPlayerManager().getPlayerList())q.networkHandler.sendPacket(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(List.of(m)));
                            m.changeGameMode(GameMode.SURVIVAL);boolean in=w.spawnEntity(m);m.setHealth((float)opt("mhp",60));m.floor=(float)opt("mfloor",20);mate=m;
                            log.append("  mate ").append(in?"in":"refused").append(String.format(Locale.ROOT," at (%.2f, %.2f) health %.0f, tick %d%n",to.x,to.z,m.getHealth(),age));});continue;}
                    if(what.equals("MATEHP")){if(age==at)server(p->{if(mate!=null){mate.setHealth((float)opt("mhp2",30));log.append(String.format(Locale.ROOT,"  mate health set to %.0f at %d%n",mate.getHealth(),age));}});continue;}
                    // "TOBOX@n" (2.69.0): the player steps up to the nearest box a boss left (1.3 blocks from it, facing it), to
                    // open it with E held ("KEYDOWN_E@n"/"KEYUP_E@n")
                    if(what.equals("TOBOX")){if(age==at)server(p->{
                            var boxes=p.getServerWorld().getEntitiesByClass(dev.sangwoo.celestialfall.loot.FieldLootEntity.class,p.getBoundingBox().expand(24),e->e.loose()&&!e.opened());
                            var box=boxes.stream().min(java.util.Comparator.comparingDouble(e->e.squaredDistanceTo(p))).orElse(null);
                            if(box==null){log.append("  no box to step up to at ").append(age).append('\n');return;}
                            var away=p.getPos().subtract(box.getPos()).multiply(1,0,1);if(away.lengthSquared()<1e-4)away=new net.minecraft.util.math.Vec3d(0,0,1);away=away.normalize();
                            var to=box.getPos().add(away.multiply(1.3));float yaw=(float)Math.toDegrees(Math.atan2(-(box.getX()-to.x),box.getZ()-to.z));
                            p.networkHandler.requestTeleport(to.x,box.getY(),to.z,yaw,25);p.setYaw(yaw);p.setHeadYaw(yaw);p.setBodyYaw(yaw);
                            log.append(String.format(Locale.ROOT,"  stepped up to the box at (%.2f, %.2f) at %d%n",box.getX(),box.getZ(),age));});continue;}
                    if(what.equals("PHP")){if(age==at)server(p->{p.setHealth((float)opt("php",40));log.append(String.format(Locale.ROOT,"  player health set to %.0f at %d%n",p.getHealth(),age));});continue;}
                    if(what.equals("UNMATE")){if(age==at)server(p->{if(mate!=null){for(var q:p.getServer().getPlayerManager().getPlayerList())q.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(List.of(mate.getUuid())));mate.discard();mate=null;log.append("  mate gone at ").append(age).append('\n');}});continue;}
                    if(what.equals("UNFAKE")){if(age==at)server(p->{if(fakePlayer!=null){p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(List.of(fakePlayer.getUuid())));fakePlayer.discard();fakePlayer=null;log.append("  fake gone at ").append(age).append('\n');}});continue;}
                    // the training dummy back in front of the player (after the finisher threw it), whole again
                    if(what.equals("DUMMYBACK")){if(age==at)server(p->{var d=p.getServerWorld().getEntityById(dummyId);if(d instanceof net.minecraft.entity.LivingEntity l){
                            var f=net.minecraft.util.math.Vec3d.fromPolar(0,p.getYaw());var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);var to=p.getPos().add(f.multiply(opt("dfw",3))).add(right.multiply(opt("drt",0)));
                            l.setHealth(l.getMaxHealth());l.setVelocity(0,0,0);l.refreshPositionAndAngles(to.x,p.getY(),to.z,p.getYaw()+180,0);
                            // (2.68.0: an attacking dummy starts its first combo 20 ticks after it is back, not before)
                            if(l instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity t&&t.kind()==dev.sangwoo.celestialfall.training.DummyKind.ATTACK)t.resetTraining();
                            log.append("  dummy back at ").append(age).append('\n');}});continue;}
                    if(age==at){int id=skill(what);server(p->{boolean ok=KagenoManager.input(p,id);log.append("  ").append(what).append(ok?" at ":" refused at ").append(p.getWorld().getTime()).append('\n');});}}
                if(current.equals("F")&&age==40)key(GLFW_KEY_F,false);
                for(var it=tryRelease.entrySet().iterator();it.hasNext();){var e=it.next();if(age>=e.getValue()){if(e.getKey()<0)button(false);else key(e.getKey(),false);it.remove();}}
                if(!current.equals("H"))camera();
                if(Math.min(age,clock())>length(current)+4||age>length(current)*3+40){log.append(folder).append(" frames ").append(shotIndex).append('\n');
                    if(opts.containsKey("walk"))key(GLFW_KEY_W,false);
                    filming=false;if(!actors.isEmpty()){var rows="age,name,x,z,out,held,speed_attr,edge\n"+actorLog;var path=OUT.resolve(folder).resolve("actors.csv");Files.createDirectories(path.getParent());Files.writeString(path,rows);}
                    if(shakeLog.length()>0){var path=OUT.resolve(folder).resolve("shake.csv");Files.createDirectories(path.getParent());Files.writeString(path,"t,gap,wall,yaw,pitch\n"+shakeLog);}
                    {var path=OUT.resolve(folder).resolve("motion.csv");Files.createDirectories(path.getParent());Files.writeString(path,"t,x,y,z,yaw,fov_target,fov\n"+motionLog);}
                    if(bossLog.length()>0){Files.writeString(OUT.resolve(folder).resolve("boss.csv"),"t,health,angry,doing,target,distance,x,z,yaw,stand_out,guard,dash,player_health,player_held,boxes,mate_health\n"+bossLog);bossLog.setLength(0);bossDoing="";}
                    if(standLog.length()>0){Files.writeString(OUT.resolve(folder).resolve("stand.csv"),"t,server_out,since,out_flag,out,alpha,scale,idle,hero,summon,wind,gust,lean,speed,glow,aura,combo,punch_age,landed,struck,punch_ticks,engage,guard,wind_l,punch_l,wind_r,punch_r,wind_hook,hook,wind_big,punch_big,"
                        +"rush_age,rush_blows,rush_struck,rush_on,rush_strike,dummy_health,speed_attr,rush_l,rush_l_hi,rush_l_lo,rush_r,rush_r_hi,rush_r_lo,"
                        +"wind_heavy,punch_heavy,dummy_guard,dummy_broken,dummy_x,dummy_y,dummy_z,wind_finger,star_finger,finger_open,finger_ext,finger_len,finger_reach,dummy_disabled,dummy_speed,"
                        +"inhale,suction,strain,inhale_age,inhale_on,inhale_caught,inhale_blocked,inhale_suck,dummy_ahead,dummy_side,"
                        +"beat_age,beat_ended,beat_cut,beat_bars,beat_camera,flash,beat_grab,beat_upper,shown_ahead,shown_side,shown_up,beat_wind,beat_strike,wind_heavy,punch_heavy,glowed,beat_hidden,"
                        +"ts_age,ts_stopped,ts_ended,ts_cut,ts_still,ts_radius,ts_outer,ts_flash,ts_negative,ts_shell,ts_crack,ts_impact,world_gather,world,player_health,dummy_stopped,player_held,player_locked,player_staggered,dummy_staggered,dummy_strike\n"+standLog);
                        standLog.setLength(0);punchLogged="";rushOn=false;rushBlows=0;fingerLongest=0;fingerLogged=dummyDisabled=false;inhaleOn=false;inhaleCaught="0";inhaleGuards=0;beatOn=beatEnded=false;dummyHealth=-1;
                        stopLogged="";stillLogged=false;playerHealth=-1;playerStaggered=dummyStaggeredLogged=false;
                        Files.writeString(OUT.resolve(folder).resolve("camera.csv"),"t,x,y,z,yaw,pitch,distance,clear,fov,proj_x,proj_y,view_11,view_12,view_21,viewport_w,viewport_h,main_bound\n"+cameraLog);cameraLog.setLength(0);}
                    if(AUDIO){Files.writeString(OUT.resolve(folder).resolve("sounds.csv"),"clock,nanos,sound\n"+soundRows);Files.writeString(OUT.resolve(folder).resolve("ticks.csv"),"clock,nanos\nmarker,"+markerNanos+"\n"+tickRows);}
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
            case "BASE","HUD","UI","METEOR","SHOP"->{}
            // 2.60.0: a stand shot's actors walk from their START (shot ticks), for the Star Finger's slow
            case "SUMMON"->{if(!actors.isEmpty())server(p->{spot=p.getPos();serverStart=p.getWorld().getTime();});}
            case "STAND"->server(p->{
                var w=p.getServerWorld();var figure=dev.sangwoo.celestialfall.stand.StarPlatinum.TYPE.create(w);
                var at=p.getPos().add(net.minecraft.util.math.Vec3d.fromPolar(0,p.getYaw()).multiply(opt("sfw",3)));float yaw=p.getYaw()+180+opt("syaw",0);
                figure.refreshPositionAndAngles(at.x,at.y,at.z,yaw,0);figure.setHeadYaw(yaw);figure.setBodyYaw(yaw);
                figure.setStance(Math.max(0,dev.sangwoo.celestialfall.stand.StarPlatinumStance.NAMES.indexOf(opts.getOrDefault("stance","idle"))));
                log.append("  figure ").append(w.spawnEntity(figure)?"stands":"refused").append(String.format(Locale.ROOT," at (%.2f, %.2f, %.2f) facing %.0f%n",at.x,at.y,at.z,yaw));});
            case "GDUMMY"->server(p->{int n=p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource(),"botand dummy atomic "+(int)opt("adist",60));if(n<=0)log.append("  rejected GDUMMY\n");});
            default->server(p->{long now=p.getWorld().getTime();var at=p.getPos();
                if(!KagenoManager.input(p,skill(name)))log.append("  rejected ").append(name).append('\n');else{spot=at;serverStart=now;}});
        }
    }
}
