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
 * DONE the options screen's done button, LIST the player list's rows (2.73.0; in play the cursor is free on the map, so
 * the pointer glides and is drawn there too), OFF clear of them all), "WHEEL_DOWN@n"/"WHEEL_UP@n" (2.73.0) turn the
 * mouse wheel a notch where the pointer is, "MATES@n" brings "mates" (9) more players onto the server with names of
 * their own (in a row 40 blocks behind the player) and "CLICK@n" clicks the left button where it is,
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
 * <p>"slamfollow=1" (2.128.1): the "orbit=" camera goes with the slam's body as it is drawn, up its rise and down its dive
 * (to see the body's motion from near all the way).
 *
 * <p>X Chara's three cuts (2.129.0, E with the hack off: "stand=x_chara" and no KEY_Q): "KEY_E@n" starts them; the log
 * gets the server's ("cutter +N: server ...": which kind, where the going forward ends, the cuts landed, where the player
 * really is, the dummy's health and how far it was thrown) and how far from there this client draws the body, tick by tick.
 *
 * <p>X Chara's slam (2.128.0, G with the hack off: "stand=x_chara" and no KEY_Q): "KEY_G@n" starts it; "slamaim=FW[_RT[_UP]]"
 * aims it at that point from where the player stands (blocks ahead, to the right and up: OverwriteSlam.aimAt) instead of
 * at what the middle of the screen points at (a filming camera's middle is not the player's); the log gets the server's
 * slam ("slam +N: server ...": how high it rises, where it comes down, where the player really is, the dummy's health)
 * and how far from there this client draws the body, at a few ticks of it.
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
 * 2.62.0): "KEYDOWN_Y@n" and "KEYUP_Y@n" hold Y down and let it up through the real keyboard path ("KEY_Y@n" taps it;
 * "KEYDOWN_R@n"/"KEYUP_R@n", 2.81.1, hold Lee So-sang's R down for R held);
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
 * only), and "UNFAKE@n" takes it away. (2.92.0) "fstand=isosang" makes the fake a Lee So-sang user ("fname" its name)
 * and "FAKEG@n" has it cast G by the real rules, to film one of the others in its blast's ground: the log gets when the
 * player's camera goes into its cutscene and out of it, and every half second how loud it sounds for them (no one is
 * struck: the server forgets a fake's swing the next tick). stand.csv gets the time stop (its age, whether time is stopped, when it moved
 * again, cut short, whether this client's screen is held still, its wave: how far it has spread, the far world's
 * grade, the flash, the negative, since 2.68.0 how brightly its bubble shows, its cracks and the impact frame), the
 * call's two stances, the player's health and whether the dummy and the player are held; camera.csv each frame's camera
 * (where, its yaw and pitch, the game camera's distance and clear distance, the field of view); the log when it is
 * called, stops time, moves again, the screen held and let go, and each change of the player's health. The basic
 * attack's stagger (2.68.0): "dattack=1" makes the training dummy one that throws basic-attack combos at the player;
 * stand.csv gets whether the player can only walk (their combat state locked), whether they are staggered, whether the
 * dummy is staggered (the server's own state) and its blow, and the log each time the player or the dummy is staggered
 * and free again. The stand arrow (2.70.0): "stand=none" starts the entry with no stand, "GIVE_ARROW@n" puts a stand
 * arrow in the player's main hand, "RIG_DEATH@n" and "RIG_STAR@n" decide what the next arrow will do (StandArrow.rigged:
 * the death, or Star Platinum), and "KEY_R@n" uses it (R is the use key on the map); stand.csv gets the stab (its age
 * in seconds, what it chose: 0 not yet, 1 a stand, 2 death, how brightly the arrow shines, how far down the body is, how
 * far the arrow has been drawn in, whether it was cut short), the player's stand and how many arrows they hold, and
 * the log when the stab starts, what it chose and when, when the player dies and when their stand comes out.
 * "clearchat=1" empties the chat as the entry starts (the last entries' lines, such as a warm-up's awakening, gone).
 * Star Platinum's call (2.72.0): "AWAKEN@n" runs "/botand progress awaken @s" as the player (its answer in their chat;
 * Star Platinum's time stop is the awakened's) and "UNAWAKEN@n" takes it back; stand.csv gets whether the player is
 * awakened (their client's word, the server's), the call sound playing ("star_platinum_summon" as the stand comes out at
 * Q, "star_platinum_dismiss" as it goes back) and the ticks since H was tapped; the log when each call sound starts, when
 * the awakening comes and goes, and the command's answer.
 * The long cooldowns begun again (2.74.0): "LONGCD@n" puts on the player, with "cool=1", the Atomic's, the time stop's
 * and the beatdown's cooldowns as if each had been running a while, "lcd=A/T/B" seconds still to run (240/60/40; 0 for
 * none): the server's own ledgers and the packet its client is told, as a use long ago leaves them; "NOSTAND@n" takes the
 * player's stand off them ("/botand progress stand @s none" as the player). The log gets each time the client is told
 * them (each one's seconds left and length) and each death and respawn. "pointer=1" lets MOVE_ and CLICK work (and the
 * pointer show) in a shot other than UI while a screen is up, such as the death screen's 부활 ("MOVE_RESPAWN@n"), and
 * since 2.74.1 in play too while the cursor is free. The gacha (2.74.1): "SSRNEXT@n" makes the player's next pull an SSR
 * (their count at 99 since one, as "/botand gacha set" does); "MOVE_DOCK@n" points at the HUD's menu button (≡),
 * "MOVE_DRAW@n" at the open menu's 뽑기, and on the gacha screen "MOVE_G1@n" at 1회 뽑기, "MOVE_GSKIP@n" at 건너뛰기,
 * "MOVE_GOK@n" at the results' 확인 and "MOVE_GX@n" at its X; "KEY_M@n" is the menu key. The log gets the gacha's
 * answers (its notice) and the stand the client knows the player has. A Rokakaka (2.74.2): "GIVE_FRUIT@n" puts one in
 * the main hand, "ATTACKDOWN@n"/"ATTACKUP@n" click with it (its ask opens the confirmation), "MOVE_C2@n" points at the
 * confirmation's 사용하고 초기화 and "CLICK@n" eats it ("stand=kageno" starts a SUMMON entry as Kageno). Lee So-sang's
 * sword (2.77.0): "stand=isosang" and "ATTACKDOWN@n"/"ATTACKUP@n"; the log gets each hit seen and whom its blows struck,
 * NAME/isosang.csv each hit and the tick it began on (tools/isosang/shots/mux_m1.py lays the game's sound by it).
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
    private static final java.util.Map<String,Float> ACTOR_HEALTH=new java.util.HashMap<>();
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
        // ("slamfollow=1", 2.128.1) the camera goes with the body of X Chara's slam as it is drawn (up its rise, down its dive)
        if(self.opts.containsKey("slamfollow")){var mc=MinecraftClient.getInstance();
            if(mc.player!=null){
                // (from where the player really is: the landing takes them to where they came down, and the camera stays with them)
                centre=centre.add(mc.player.getLerpedPos(mc.getTickDelta()).subtract(self.castPos));
                var off=dev.sangwoo.celestialfall.client.xchara.OverwriteSlam.offset(mc.player.getUuid(),mc.getTickDelta());
                if(off!=null)centre=centre.add(off);}}
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
    private net.minecraft.util.math.Vec3d slamFrom;
    private net.minecraft.util.math.Vec3d cutFrom,cutDummy;
    private net.minecraft.util.math.Vec3d shotFrom;
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
    private final Set<UUID> castsLogged=new HashSet<>();private long dashLogged=-1000;private List<String> listLogged=List.of();private int hitsLogged;private String glowLogged="";
    /** One row of motion.csv for this client tick, and for M1 the hits whose sound has just started (sound.csv). */
    private void logTick()throws Exception{
        if("UI".equals(current)||mapShot()||"SUMMON".equals(current)&&("none".equals(opts.get("stand"))||opts.containsKey("cool")))logInterface();
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
        // (2.108.0) the hit count, as this client hears it (every character's)
        if(dev.sangwoo.celestialfall.client.HitCounterHud.count!=hitsLogged){hitsLogged=dev.sangwoo.celestialfall.client.HitCounterHud.count;log.append(String.format(Locale.ROOT,"  hits %d at %d (world %d)%n",hitsLogged,age,mc.world.getTime()));}
        // (2.109.0) a skill with a cutscene's glow: the server's (from its key until it is cast or cut short, which skill)
        // and this client's lock, and the speed the player walks at meanwhile (a fifth of their own)
        {var server=mc.getServer();var sp=server==null?null:server.getPlayerManager().getPlayer(mc.player.getUuid());
            int skill=sp==null?-1:dev.sangwoo.celestialfall.combat.CutsceneGlow.skill(sp);
            String g=skill+"/"+dev.sangwoo.celestialfall.client.CutsceneFlash.localGlowing();
            if(!g.equals(glowLogged)){glowLogged=g;log.append(String.format(Locale.ROOT,"  glow %s at %d (world %d): server %s, client %s, speed %.4f, flash %.2f%n",skill<0?"none":new String[]{"Kageno H","beatdown","Lee So-sang G"}[skill],age,mc.world.getTime(),
                skill>=0,dev.sangwoo.celestialfall.client.CutsceneFlash.localGlowing(),mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED),dev.sangwoo.celestialfall.client.CutsceneFlash.local(mc.getTickDelta())));}}
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
    /**
     * Whether the pointer moves and is drawn: UI and map shots; "pointer=1" (2.74.0) in another while a screen is up (the
     * death screen's 부활), and since 2.74.1 in play too while the cursor is free (the HUD's menu button).
     */
    private boolean ui(){return ("UI".equals(current)||mapShot()||opts.containsKey("pointer")&&mc!=null&&(mc.currentScreen!=null||!mc.mouse.isCursorLocked()))&&phase==4;}
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
        // 2.73.0: the player list's rows (where the wheel scrolls it), as drawn on the last frame
        if(name.equals("LIST")){var a=dev.sangwoo.celestialfall.client.ui.PlayerPanel.area();if(a==null)throw new IllegalStateException("no player list to point at");return new double[]{a[0]+(a[2]-a[0])*.55,a[1]+(a[3]-a[1])*.62};}
        // 2.74.1: the HUD's menu button (≡), the open menu's 뽑기, and the gacha screen's 1회 뽑기, 건너뛰기, 확인 and X
        if(name.equals("DOCK")||name.equals("DRAW")){
            for(var dk:dev.sangwoo.celestialfall.client.menu.MenuLayout.dock(w,h,name.equals("DRAW")))
                if(name.equals("DOCK")||dk.id().equals("draw")){var r=dk.rect();return new double[]{r.x()+r.w()*.5,r.y()+r.h()*.55};}
            throw new IllegalStateException("no "+name+" in the dock");
        }
        if(name.matches("G1|GSKIP|GOK|GX")){
            if(!(mc.currentScreen instanceof dev.sangwoo.celestialfall.client.gacha.GachaScreen s))throw new IllegalStateException("no gacha screen for "+name);
            float[] q;
            try{
                if(name.equals("GOK")){var m=Class.forName("dev.sangwoo.celestialfall.client.gacha.GachaResults").getDeclaredMethod("okButton",dev.sangwoo.celestialfall.client.gacha.GachaScreen.class);m.setAccessible(true);q=(float[])m.invoke(null,s);}
                else{var m=dev.sangwoo.celestialfall.client.gacha.GachaScreen.class.getDeclaredMethod(name.equals("G1")?"pullOne":name.equals("GSKIP")?"skipButton":"closeBox");m.setAccessible(true);q=(float[])m.invoke(s);}
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            double x=0,y=0;for(int i=0;i<q.length;i+=2){x+=q[i];y+=q[i+1];}
            return new double[]{x/(q.length/2),y/(q.length/2)};
        }
        if(name.equals("OFF"))return new double[]{w*.2,h*.8};
        throw new IllegalArgumentException(name);
    }
    /** Moves the game's mouse along the current glide for this frame (only while a screen is up: in play it would turn the camera). */
    private void glide(){
        // (2.73.0: in play too while the cursor is free, as it is on the map, to point at the player list)
        if(!ui()||glideTo==null||mc.currentScreen==null&&mc.mouse.isCursorLocked())return;
        double k=Math.max(0,Math.min(1,(clock()-glideAt)/GLIDE));k=k*k*(3-2*k);
        double x=glideFrom[0]+(glideTo[0]-glideFrom[0])*k,y=glideFrom[1]+(glideTo[1]-glideFrom[1])*k;
        if(Math.abs(x-mc.mouse.getX())>1e-6||Math.abs(y-mc.mouse.getY())>1e-6){
            glfwSetCursorPos(mc.getWindow().getHandle(),x,y);
            try{var m=net.minecraft.client.Mouse.class.getDeclaredMethod("onCursorPos",long.class,double.class,double.class);m.setAccessible(true);m.invoke(mc.mouse,mc.getWindow().getHandle(),x,y);}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        if(k>=1)glideTo=null;
    }
    private void button(boolean down){button(GLFW_MOUSE_BUTTON_LEFT,down);}
    private void button(int which,boolean down){
        try{var m=net.minecraft.client.Mouse.class.getDeclaredMethod("onMouseButton",long.class,int.class,int.class,int.class);m.setAccessible(true);
            m.invoke(mc.mouse,mc.getWindow().getHandle(),which,down?GLFW_PRESS:GLFW_RELEASE,0);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    /**
     * (2.107.0) "SWEEP@n": the mouse goes "sweep" pixels right (1200; "sweepy" down, 0) over "sweept" shot ticks (40),
     * eased, a little each frame through Minecraft's own cursor handler, as a hand on the mouse would move it: with the
     * right button held (or the cursor captured) the camera turns, 0.15° a pixel at the default sensitivity.
     */
    private void sweep(){
        if(sweepAt<0||mc==null||phase!=4)return;
        double k=Math.max(0,Math.min(1,(clock()-sweepAt)/opt("sweept",40)));k=k*k*(3-2*k);
        double x=opt("sweep",1200)*k,y=opt("sweepy",0)*k,dx=x-sweepDone,dy=y-sweepDoneY;
        if(Math.abs(dx)>1e-6||Math.abs(dy)>1e-6){
            try{var m=net.minecraft.client.Mouse.class.getDeclaredMethod("onCursorPos",long.class,double.class,double.class);m.setAccessible(true);m.invoke(mc.mouse,mc.getWindow().getHandle(),mc.mouse.getX()+dx,mc.mouse.getY()+dy);}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            sweepDone=x;sweepDoneY=y;
        }
        if(k>=1){sweepAt=-1;log.append(String.format(Locale.ROOT,"  sweep over at %d (camera yaw %.1f, pitch %.1f, distance %.2f)%n",age,BotandCamera.STATE.yaw,BotandCamera.STATE.pitch,BotandCamera.STATE.distance));}
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
    private volatile Mate mate;private final List<Mate> mates=new java.util.concurrent.CopyOnWriteArrayList<>();
    /** SLEEP: the tick the player lay down in bed (-1: not in bed). */
    private volatile float inBedAt=-1;
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
            // "pfloor=N": the player's health kept at N or more (a low-health player the boss goes after, not killed)
            if(opt("pfloor",0)>0&&p.isAlive()&&p.getHealth()<opt("pfloor",0))p.setHealth((float)opt("pfloor",0));
            int boxes=p.getServerWorld().getEntitiesByClass(dev.sangwoo.celestialfall.loot.FieldLootEntity.class,p.getBoundingBox().expand(40),e->e.loose()).size();
            if(inBedAt>=0&&!p.isSleeping()){
                log.append(String.format(Locale.ROOT,"  woken at %.1f (%.0f ticks in bed): the night slept through, time of day %d%n",t,t-inBedAt,p.getServerWorld().getTimeOfDay()));inBedAt=-1;
            }
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
    /** 2.72.0: the call sound last logged, whether the client was last logged awakened, the tick H was last tapped. */
    private String callLogged="";private boolean awakeLogged;private long lastH=-1;
    /** (2.93.0) "ZAIM<deg>@n": the sword of the player's own formation the camera turns onto (-1 once Z is pressed), from when, from where; and where it holds once it has. */
    private int zaimSword=-1,zaimAt=-1;private float zaimYaw0,zaimPitch0,zaimYaw,zaimPitch;private boolean zaimHeld;
    /** (2.95.0) "DAIM@n" / "PAIM<deg>@n": the camera turned onto the training dummy (following it) or onto a spot of the ground (held), from when and from where. */
    private boolean daimHeld;private net.minecraft.util.math.Vec3d paimPoint;private int daimAt=-1;private float daimYaw0,daimPitch0;
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
        // 2.70.0: the stand arrow: the stab (its age, what it chose, the arrow's glow, how far down the body is, how far the
        // arrow has been drawn in, cut short), the player's stand (the server's) and how many arrows the main hand holds
        var stab=dev.sangwoo.celestialfall.client.stand.StandArrowClient.of(mc.player.getUuid());
        if(stab==null)standLog.append(",,,,,,");
        else{
            float st=stab.age(mc.getTickDelta());var sf=dev.sangwoo.celestialfall.client.stand.ArrowStabPose.frame(st,stab.fate,stab.shows());
            standLog.append(String.format(Locale.ROOT,",%.3f,%d,%.3f,%.3f,%.3f,%d",st,stab.fate.ordinal(),sf.glow(),sf.fallen(),sf.arrow()==null?1f:sf.arrow().sink(),stab.cutShort()?1:0));
            String phase=stab.cutShort()?"cut short":stab.fate==dev.sangwoo.celestialfall.client.stand.ArrowStabPose.Fate.UNKNOWN?"driven in":"chose "+stab.outcome;
            if(!phase.equals(arrowLogged)){log.append("  stand arrow ").append(phase).append(" at ").append(age).append(String.format(Locale.ROOT," (its age %.2f s)%n",st));arrowLogged=phase;}
        }
        String serverStand="";
        if(mc.getServer()!=null){var sp=mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid());if(sp!=null)serverStand=dev.sangwoo.celestialfall.progression.ProgressManager.profile(sp).stand();}
        int arrows=mc.player.getMainHandStack().isOf(dev.sangwoo.celestialfall.meteor.MeteorRegistry.STAND_ARROW)?mc.player.getMainHandStack().getCount():0;
        standLog.append(',').append(serverStand).append(',').append(arrows);
        // 2.72.0: whether the player is awakened (their client's word, the server's), the call sound playing (the call
        // as it comes out, the sound of it going as it goes back) and ticks since H was tapped (-1: not yet)
        boolean awake=dev.sangwoo.celestialfall.client.ProgressClient.awakened;String serverAwake="";
        if(mc.getServer()!=null){var sp=mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid());if(sp!=null)serverAwake=ProgressManager.awakened(sp)?"1":"0";}
        var call=dev.sangwoo.celestialfall.client.stand.StandCall.playing(mc.player.getUuid());
        String callName=call==null?"":call.getId().getPath();
        standLog.append(',').append(awake?1:0).append(',').append(serverAwake).append(',').append(callName).append(',').append(lastH<0?-1:age-lastH);
        if(!callName.equals(callLogged)){if(!callName.isEmpty())log.append("  call sound ").append(callName).append(" at ").append(age).append('\n');callLogged=callName;}
        if(awake!=awakeLogged){log.append("  ").append(awake?"awakened":"not awakened").append(" (client) at ").append(age).append('\n');awakeLogged=awake;}
        if(!mc.player.isAlive()&&!playerDeadLogged){log.append("  player died at ").append(age).append('\n');playerDeadLogged=true;}
        if(mc.player.isAlive())playerDeadLogged=false;
        // 2.75.0: Star Platinum's users resisting another's stopped time (the server's own state): whether the player and
        // the fake user resist one now or stand stopped in one, and where the fake user is
        String playerTs=",",fakeTs=",,,";
        if(mc.getServer()!=null){
            var sp=mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid());var fake=fakePlayer;
            if(sp!=null){
                boolean resisting=StarPlatinumTimeStop.resisting(sp),stopped=StarPlatinumTimeStop.stopped(sp);
                playerTs=(resisting?1:0)+","+(stopped?1:0);
                if(resisting&&!resistLogged[0])log.append("  player resists the time stop at ").append(age).append(String.format(Locale.ROOT," (at %.2f, %.2f)%n",sp.getX(),sp.getZ()));
                if(stopped&&!resistLogged[1])log.append("  player stopped in it at ").append(age).append(String.format(Locale.ROOT," (at %.2f, %.2f)%n",sp.getX(),sp.getZ()));
                resistLogged[0]=resisting;resistLogged[1]=stopped;
            }
            if(fake!=null&&!fake.isRemoved()){
                boolean resisting=StarPlatinumTimeStop.resisting(fake),stopped=StarPlatinumTimeStop.stopped(fake);
                fakeTs=(resisting?1:0)+","+(stopped?1:0)+String.format(Locale.ROOT,",%.3f,%.3f",fake.getX(),fake.getZ());
                if(resisting&&!resistLogged[2])log.append("  fake user resists the time stop at ").append(age).append(String.format(Locale.ROOT," (at %.2f, %.2f)%n",fake.getX(),fake.getZ()));
                if(stopped&&!resistLogged[3])log.append("  fake user stopped in it at ").append(age).append(String.format(Locale.ROOT," (at %.2f, %.2f)%n",fake.getX(),fake.getZ()));
                resistLogged[2]=resisting;resistLogged[3]=stopped;
            }
        }
        standLog.append(',').append(playerTs).append(',').append(fakeTs);
        // 2.75.1: what the stopped world keeps in colour, as this client draws it: how many bodies the last frame kept
        // (players and the stands drawn with them), whether it laid the auras back, and how fully the player's colours
        // and the fake user's are kept
        int kept=dev.sangwoo.celestialfall.client.stand.TimeStopView.keptLast();
        var fakeHere=fakePlayer==null?null:mc.world.getPlayerByUuid(fakePlayer.getUuid());
        float tsDelta=mc.getTickDelta();
        standLog.append(String.format(Locale.ROOT,",%d,%d,%.3f,%s",kept,dev.sangwoo.celestialfall.client.stand.TimeStopView.aurasLast()?1:0,
            dev.sangwoo.celestialfall.client.stand.StandTimeStop.colour(mc.player,tsDelta,false),
            fakeHere==null?"":String.format(Locale.ROOT,"%.3f",dev.sangwoo.celestialfall.client.stand.StandTimeStop.colour(fakeHere,tsDelta,false))));
        if(kept!=keptLogged){log.append("  stopped world keeps ").append(kept).append(" bodies in colour at ").append(age).append('\n');keptLogged=kept;}
        // 2.112.0: X Chara's G: its age (ticks), whether its strange space came, whether it ended and how (XCharaUltimate's
        // event), whether this client's camera is in its strange space; a line in the log as each of them changes
        var dance=dev.sangwoo.celestialfall.client.xchara.XCharaClient.describe(mc.player,mc.getTickDelta());
        boolean inSpace=dev.sangwoo.celestialfall.client.xchara.XCharaClient.cinematic()!=null;
        standLog.append(',').append(dance==null?",,,":dance).append(',').append(inSpace?1:0);
        String danceKey=dance==null?"":dance.substring(dance.indexOf(',')+1)+","+(inSpace?1:0);
        if(!danceKey.equals(danceLogged)){if(dance!=null)log.append("  x chara G (space,ended,how,camera) ").append(danceKey).append(" at ").append(age).append(String.format(Locale.ROOT," (its age %s)%n",dance.substring(0,dance.indexOf(','))));danceLogged=danceKey;}
        // 2.114.0: X Chara's basic attack: the player's latest swing (which hit, its age in ticks, whether it stopped
        // short); a line in the log as each hit is first seen
        var knife=dev.sangwoo.celestialfall.client.xchara.KnifeClient.describe(mc.player,mc.getTickDelta());
        standLog.append(',').append(knife==null?",,":knife);
        String knifeKey=knife==null?"":knife.substring(0,knife.indexOf(','))+"/"+knife.substring(knife.lastIndexOf(',')+1)+"/"+(knife.length()>0?Math.round(age-Float.parseFloat(knife.split(",")[1])):0);
        if(!knifeKey.equals(knifeLogged)){if(knife!=null)log.append("  knife hit ").append(knife.split(",")[0]).append(knife.endsWith(",1")?" stopped short":"").append(" at ").append(age).append(String.format(Locale.ROOT," (its age %s)%n",knife.split(",")[1]));knifeLogged=knifeKey;}
        // 2.115.0: X Chara's hack (Q): whether it is on and the seconds since it changed; a line in the log as it changes
        var hack=dev.sangwoo.celestialfall.client.xchara.HackMode.describe(mc.player,mc.getTickDelta());
        standLog.append(',').append(hack==null?",":hack);
        String hackKey=hack==null?"":hack.substring(0,hack.indexOf(','));
        if(!hackKey.equals(hackLogged)){if(hack!=null&&!hackLogged.isEmpty())log.append("  hack ").append(hackKey.equals("1")?"on":"off").append(" at ").append(age).append('\n');hackLogged=hackKey;}
        // 2.119.0: X Chara's E (the restore): its age in ticks, whether its heal landed, whether it stopped short, and the
        // player's health as this client knows it; a line in the log as it starts, heals and stops
        var heal=dev.sangwoo.celestialfall.client.xchara.HealClient.describe(mc.player.getUuid(),mc.getTickDelta());
        standLog.append(',').append(heal==null?",,":heal).append(',').append(String.format(Locale.ROOT,"%.1f",mc.player.getHealth()));
        String healKey=heal==null?"":heal.substring(heal.indexOf(',')+1);
        if(!healKey.equals(healLogged)){if(heal!=null)log.append("  heal ").append(healKey.equals("0,0")?"started":healKey.startsWith("1")?"landed":"stopped short").append(" at ").append(age).append(String.format(Locale.ROOT," (its age %s, health %.1f)%n",heal.split(",")[0],mc.player.getHealth()));healLogged=healKey;}
        // 2.124.0: X Chara's jab: who has its X on their face ("entity:seconds on"); a line in the log as one is marked and as none is
        String marks=dev.sangwoo.celestialfall.client.xchara.CorruptionJab.describe(mc.getTickDelta());
        boolean marked=!marks.isEmpty()&&java.util.Arrays.stream(marks.split(" ")).anyMatch(k->dev.sangwoo.celestialfall.client.xchara.CorruptionJab.marked(Integer.parseInt(k.substring(0,k.indexOf(':')))));
        if(marked!=marksLogged){log.append(marked?"  jab's X on ":"  jab's X gone ").append(marks).append(" at ").append(age).append('\n');marksLogged=marked;}
        // 2.125.0: X Chara's double (R with the hack on): its age, whether the one marked was seized, whether they went
        // into the wall, whether it ended and how; a line in the log as each changes
        var doubled=dev.sangwoo.celestialfall.client.xchara.CloneClient.describe(mc.player,mc.getTickDelta());
        String doubledKey=doubled==null?"":doubled.substring(doubled.indexOf(',')+1);
        standLog.append(',').append(doubled==null?",,,,":doubled);
        if(!doubledKey.equals(doubledLogged)){if(doubled!=null)log.append("  double (seized,in the wall,ended,how) ").append(doubledKey).append(" at ").append(age).append(String.format(Locale.ROOT," (its age %s)%n",doubled.substring(0,doubled.indexOf(','))));doubledLogged=doubledKey;}
        // 2.126.0: X Chara's weakening (T with the hack on): whom this client holds weakened and its casts; a line in the
        // log as who is weakened or a cast's state changes (the seconds left are left out of what is compared)
        {String weak=dev.sangwoo.celestialfall.client.xchara.WeakenClient.describe(mc.getTickDelta());
         String weakKey=weak.replaceAll(":[0-9.]+","").replaceAll("@[0-9.]+","");
         if(!weakKey.equals(weakLogged)){log.append("  weakening (entity:seconds left, cast@age,how) [").append(weak).append("] at ").append(age).append('\n');weakLogged=weakKey;}}
        // 2.127.0: X Chara's pull (Y with the hack on): this client's casts; a line in the log as a cast's state changes
        {String pull=dev.sangwoo.celestialfall.client.xchara.PullClient.describe(mc.getTickDelta());
         String pullKey=pull.replaceAll("@[0-9.]+,(-?[0-9]+),-?[0-9.]+,","@,$1,");
         if(!pullKey.equals(pullLogged)){log.append("  pull (cast@age,how,seconds since the pull,drawn where it is) [").append(pull).append("] at ").append(age).append('\n');pullLogged=pullKey;}}
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
        // Lee So-sang's sword (2.77.0): each of the player's hits seen, and whom its blows struck as they are told
        var me=MinecraftClient.getInstance().player;
        // (2.92.0) and the fake user's, so a G it casts is in NAME/isosang.csv for its sound (mux_g.py)
        if(me!=null)for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(!c.caster().equals(me.getUuid())&&(fakePlayer==null||!c.caster().equals(fakePlayer.getUuid())))continue;
            if(swingsLogged.add(c.id())){
                long began=age-(long)Math.floor(c.age(0));
                log.append("  isosang hit ").append(c.combo()).append(" seen at ").append(age).append(" (began on tick ").append(began).append(")\n");
                // (2.83.0) G: the recording it plays
                var ult=dev.sangwoo.celestialfall.client.isosang.IsosangClient.ultimate(c.id());
                if(ult!=null)log.append("  G plays recording ").append(ult.variant()+1).append(", its sword strikes ").append(String.format(Locale.ROOT,"%.1f %.1f %.1f",ult.strike().x,ult.strike().y,ult.strike().z)).append('\n');
                // (2.81.0) R: where its sweep stands against the player (out toward the foe in the crosshair, or where they are)
                var sw=dev.sangwoo.celestialfall.client.isosang.IsosangClient.sweep(c.id());
                if(sw!=null){var f=net.minecraft.util.math.Vec3d.fromPolar(0,sw.yaw());var d=sw.at().subtract(me.getPos());
                    log.append(String.format(Locale.ROOT,"  isosang R stands %.2f ahead %.2f up of the player, facing %.1f (the player %.1f), aimed at %d%n",d.dotProduct(f),d.y,sw.yaw(),me.getYaw(),sw.target()));}
                swingRows.append(c.combo()).append(',').append(began).append('\n');
            }
            int n=c.targets().size();Integer was=swingTargets.put(c.id(),n);
            if(was==null?n>0:n>was)log.append("  isosang hit ").append(c.combo()).append(" struck ").append(n).append(" at ").append(age).append(String.format(Locale.ROOT," (its age %.2f s)%n",c.age(0)/20f));
        }
        // (2.92.0) another's G: when the player's camera goes into its cutscene and out of it (and how far into it),
        // and every half second how loud its sound is for the player and where they stand
        if(me!=null){
            var g=dev.sangwoo.celestialfall.client.isosang.GArenaClient.cinematic();
            var other=g!=null&&!g.caster().equals(me.getUuid())?g:null;
            if((other==null)!=(othersG==null)){
                var c=other!=null?other:othersG;
                log.append("  others' G cutscene ").append(other!=null?"on":"off").append(" at ").append(age)
                    .append(String.format(Locale.ROOT," (its age %.3f s, the player at %.1f %.1f %.1f)%n",c.age(MinecraftClient.getInstance().getTickDelta())/20f,me.getX(),me.getY(),me.getZ()));
            }
            othersG=other;
            if(age%10==0)for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
                var u=dev.sangwoo.celestialfall.client.isosang.IsosangClient.ultimate(c.id());
                if(c.combo()!=dev.sangwoo.celestialfall.isosang.IsosangRules.ULTIMATE||c.caster().equals(me.getUuid())||u==null||c.age(0)/20f>8)continue;
                log.append(String.format(Locale.ROOT,"  others' G heard at %.3f at %d (its age %.2f s, the player at %.1f %.1f %.1f, %.1f blocks from where it strikes)%n",
                    dev.sangwoo.celestialfall.client.isosang.IsosangClient.ultimateLoudness(MinecraftClient.getInstance(),c.id(),u.strike()),age,c.age(0)/20f,me.getX(),me.getY(),me.getZ(),me.getPos().distanceTo(u.strike())));
            }
        }
    }
    private dev.sangwoo.celestialfall.client.StandClient.Cast othersG;
    private final java.util.Set<java.util.UUID> swingsLogged=new java.util.HashSet<>();private final java.util.Map<java.util.UUID,Integer> swingTargets=new java.util.HashMap<>();
    /** 2.77.0: each of the player's sword hits (its number and the tick of the shot's clock it began on), for NAME/isosang.csv. */
    private final StringBuilder swingRows=new StringBuilder();
    private String punchLogged="";private boolean rushOn;private int rushBlows;private final StringBuilder cameraLog=new StringBuilder();private String worldPass=",,,,,,,";
    private boolean beatOn,beatEnded;private float dummyHealth=-1;
    /** 2.67.0: the fake Star Platinum user of the shot (server side), and what the log has said of the time stop and the player's health. */
    private volatile UUID fakeId;
    private net.minecraft.server.network.ServerPlayerEntity fakePlayer;private String stopLogged="";private boolean stillLogged;private int keptLogged;private float playerHealth=-1;private float fakeRunYaw;
    private boolean playerStaggered,dummyStaggeredLogged,playerDeadLogged;
    /** 2.112.0: X Chara's G's state last logged. */
    private String danceLogged="";
    /** (2.114.0) X Chara's basic attack's swing last logged. */
    private String knifeLogged="";
    private String hackLogged="";
    private String healLogged="";
    /** (2.125.0) the double's state as last logged. */
    private String doubledLogged="";
    private String weakLogged="";
    private String pullLogged="";
    /** (2.124.0) whether the jab's X was on someone as last logged. */
    private boolean marksLogged;
    /** (2.75.0) last logged: the player resisting a time stop, stopped in one; the fake user the same. */
    private final boolean[] resistLogged=new boolean[4];
    private String arrowLogged="";
    private double fingerLongest,fingerReach;private boolean fingerLogged,dummyDisabled;
    private boolean inhaleOn;private String inhaleCaught="0";private int inhaleGuards;
    /** The UI shot's log: screens opened, the reset question, the player list, deaths and respawns, chat lines. */
    private String panelLogged="",longLogged="",gachaLogged="";
    private void logInterface()throws Exception{
        String screen=mc.currentScreen==null?"-":mc.currentScreen.getClass().getSimpleName();
        if(!screen.equals(screenLogged)){log.append("  screen ").append(screen).append(" at ").append(age).append('\n');screenLogged=screen;}
        if(mc.player==null||mc.world==null){if(!worldClosed){log.append("  world closed at ").append(age).append('\n');worldClosed=true;}return;}
        boolean paused=mc.isPaused();if(paused!=pausedLogged){log.append("  paused ").append(paused).append(" at ").append(age).append('\n');pausedLogged=paused;}
        boolean asking=mc.currentScreen instanceof dev.sangwoo.celestialfall.client.ui.BotandPauseScreen s&&s.asking();
        if(asking!=askingLogged){log.append("  question ").append(asking?"up":"down").append(" at ").append(age).append('\n');askingLogged=asking;}
        // 2.73.0: the player list's names and the row it scrolls to
        var listed=mc.player.networkHandler.getListedPlayerListEntries().size();int row=(int)dev.sangwoo.celestialfall.client.ui.PlayerPanel.scroll()[1];
        String listNow=listed+" listed, from row "+row;
        if(!listNow.equals(panelLogged)){log.append("  player list ").append(listNow).append(" at ").append(age).append('\n');panelLogged=listNow;}
        boolean hidden=dev.sangwoo.celestialfall.client.ui.PlayerPanel.hidden();
        if(hidden!=hiddenLogged){log.append("  player list ").append(hidden?"sent away":"back").append(" at ").append(age).append('\n');hiddenLogged=hidden;}
        boolean alive=mc.player.isAlive();
        if(alive!=aliveLogged){log.append("  player ").append(alive?"respawned":"died").append(" at ").append(age).append(String.format(Locale.ROOT," (health %.1f)",mc.player.getHealth())).append('\n');aliveLogged=alive;}
        // 2.74.0: the long cooldowns this client is told, whenever any ready tick changes (seconds left of its length)
        if(dev.sangwoo.celestialfall.client.LongCooldownsClient.known()){
            long now=mc.world.getTime();var key=new StringBuilder();var line=new StringBuilder();
            for(var sk:dev.sangwoo.celestialfall.LongCooldowns.Skill.values()){long r=dev.sangwoo.celestialfall.client.LongCooldownsClient.ready(sk);key.append(r).append(',');
                line.append(String.format(Locale.ROOT," %s %s of %.0f s;",sk.name().toLowerCase(Locale.ROOT),r>now?String.format(Locale.ROOT,"%.1f s left",(r-now)/20.):"none",dev.sangwoo.celestialfall.client.LongCooldownsClient.length(sk)/20.));}
            if(!key.toString().equals(longLogged)){log.append("  long cooldowns told:").append(line).append(" at ").append(age).append('\n');longLogged=key.toString();}
        }
        // 2.74.1: the gacha's answers (its notice) and the stand the player's client knows they have
        {String notice=dev.sangwoo.celestialfall.client.gacha.GachaClient.notice,stand=dev.sangwoo.celestialfall.client.ProgressClient.profile.stand();
            if(!(notice+"|"+stand).equals(gachaLogged)){log.append("  gacha notice \"").append(notice).append("\", stand ").append(stand).append(" at ").append(age).append('\n');gachaLogged=notice+"|"+stand;}
        }
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
        // (2.82.0) "save=FROM-TO/FROM-TO": only the frames within these stretches of the entry's clock (ticks) are kept,
        // for a long skill (Lee So-sang's H, 41 s) whose start and end are filmed and not its middle
        if(opts.containsKey("save")){boolean keep=false;for(String w:opts.get("save").split("/")){String[] v=w.split("-");if(t>=Float.parseFloat(v[0])&&t<=Float.parseFloat(v[1]))keep=true;}if(!keep)return;}
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
        // "stand=none" (2.70.0): no stand (the stand arrow is only for one without)
        if(opts.containsKey("stand"))stand=opts.get("stand");
        if(!stand.equals(ProgressManager.profile(p).stand()))ProgressManager.set(p,ProgressManager.profile(p).withStand(stand));
        ((StandHolder)p).botand$setStandOut(false);
        // (2.115.0) and X Chara's hack off: every entry starts without it
        ((StandHolder)p).botand$setHacking(false);
        // "time=T" (2.52.0): the time of day for this entry on the flat world too (the stand's aura at night)
        if(opts.containsKey("time"))p.getServerWorld().setTimeOfDay((long)opt("time",6000));
        int y=p.getServerWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,0,0);
        float yaw=facing(opt("face",0));
        p.networkHandler.requestTeleport(.5,y,.5,yaw,0);p.setYaw(yaw);p.setHeadYaw(yaw);p.setBodyYaw(yaw);p.setHealth(p.getMaxHealth());p.setVelocity(0,0,0);
        // "php=N" (2.119.0): the player starts the shot at N health (for X Chara's E, which heals them)
        if(opt("php",0)>0)p.setHealth((float)opt("php",0));
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
        ACTOR_IDS.clear();ACTORS.clear();moving.clear();ACTOR_HEALTH.clear();
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
            actorLog.append(String.format(Locale.ROOT,"%d,%s,%.3f,%.3f,%.3f,%d,%.4f,%.3f,%.1f%n",age,a.name,mob.getX(),mob.getZ(),out,held?1:0,speed.getValue()/speed.getBaseValue(),edge,mob.getHealth()));
            // (2.82.2) each change of an actor's health, and how far out it stands then (a skill's reach measured)
            Float was=ACTOR_HEALTH.put(a.name,mob.getHealth());
            if(was!=null&&Math.abs(was-mob.getHealth())>.01f)log.append(String.format(Locale.ROOT,"  actor %s health %.1f -> %.1f at %d (%.2f out)%n",a.name,was,mob.getHealth(),age,out));
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
    /** (2.96.1) Whether the camera's way is left as the game leaves it (after KEY_T), not set back to the entry's each tick. */
    private boolean camKept;
    /** (2.107.0) Whether the camera is the game's alone from "FREECAM@n" on: the shot sets neither its way nor its distance, so the real mouse moves it. */
    private boolean camFree;
    /** (2.107.0) "SWEEP@n": the shot tick the mouse began to sweep (-1 for none) and how far it has gone (pixels). */
    private float sweepAt=-1;private double sweepDone,sweepDoneY;
    private void camera(){if(camFree&&phase==4)return;float d=opt("dist",camDist);if(!camKept||phase!=4){BotandCamera.STATE.yaw=camLook()+turn();BotandCamera.STATE.pitch=opt("pitch",camPitch);}BotandCamera.STATE.targetDistance=d;BotandCamera.STATE.distance=d;BotandCamera.STATE.collisionDistance=d;
        // "aimrock=1": with a rock within reach the camera looks at it from over the player's shoulder (a click aims where the camera looks)
        if(opts.containsKey("aimrock")&&phase==4&&mc.player!=null)for(var e:mc.world.getEntities())if(e instanceof dev.sangwoo.celestialfall.meteor.MeteorEntity m&&m.squaredDistanceTo(mc.player)<49){
            var v=m.getPos().add(0,.55,0).subtract(mc.player.getEyePos());
            BotandCamera.STATE.yaw=(float)Math.toDegrees(Math.atan2(-v.x,v.z))+opt("rockyaw",0);BotandCamera.STATE.pitch=(float)-Math.toDegrees(Math.atan2(v.y,v.horizontalLength()))+opt("rockpitch",6);break;}
        // "ZAIM<deg>@n" (2.93.0): onto the sword picked, eased in over 8 ticks; held where it is after Z
        if(zaimHeld&&phase==4&&mc.player!=null){
            var mids=dev.sangwoo.celestialfall.client.isosang.SwordStep.middles();
            if(zaimSword>=0&&mids!=null&&zaimSword<mids.length){float[] a=aimAt(mids[zaimSword]);float u=Math.min(1,(age-zaimAt)/8f);u=u*u*(3-2*u);
                zaimYaw=zaimYaw0+net.minecraft.util.math.MathHelper.wrapDegrees(a[0]-zaimYaw0)*u;zaimPitch=zaimPitch0+(a[1]-zaimPitch0)*u;}
            BotandCamera.STATE.yaw=zaimYaw;BotandCamera.STATE.pitch=zaimPitch;
        }
        // "DAIM@n" (2.95.0, Lee So-sang's H basic attack, 회풍만검): onto the training dummy's chest, eased in over 8 ticks and
        // following it; "PAIM<deg>@n" onto the spot of the ground picked then, held there
        if((daimHeld||paimPoint!=null)&&phase==4&&mc.player!=null){
            net.minecraft.util.math.Vec3d to=paimPoint;
            if(daimHeld){var dm=mc.world.getEntityById(dummyId);to=dm==null?null:dm.getPos().add(0,dm.getHeight()*.55,0);}
            if(to!=null){float[] a=aimAt(to);float u=Math.min(1,(age-daimAt)/8f);u=u*u*(3-2*u);
                BotandCamera.STATE.yaw=daimYaw0+net.minecraft.util.math.MathHelper.wrapDegrees(a[0]-daimYaw0)*u;BotandCamera.STATE.pitch=daimPitch0+(a[1]-daimPitch0)*u;}
        }
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
    /** (2.93.0) The sword of the player's own formation nearest {@code deg} degrees clockwise of where the camera looks, 3.5 to 10 blocks from them, not the one they stand on (-1 for none). */
    private int pickSword(float deg){
        var mids=dev.sangwoo.celestialfall.client.isosang.SwordStep.middles();if(mids==null)return -1;int on=dev.sangwoo.celestialfall.client.isosang.SwordStep.perchNow(),best=-1;double bestD=1e9;
        float want=BotandCamera.STATE.yaw+deg;var at=mc.player.getPos();
        for(int k=0;k<mids.length;k++){if(k==on)continue;double dx=mids[k].x-at.x,dz=mids[k].z-at.z,far=Math.hypot(dx,dz);if(far<3.5||far>10)continue;
            double off=Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx,dz))-want));if(off<bestD){bestD=off;best=k;}}
        return best;
    }
    /** (2.93.0) The camera's yaw and pitch for its middle to fall on {@code target} (the shoulder camera's offset and H's pull taken in, a few rounds). */
    private float[] aimAt(net.minecraft.util.math.Vec3d target){
        float yaw=BotandCamera.STATE.yaw,pitch=BotandCamera.STATE.pitch,keepYaw=yaw,keepPitch=pitch;
        var eye=mc.player.getPos().add(0,mc.player.getStandingEyeHeight()+.8*Math.max(0,dev.sangwoo.celestialfall.client.isosang.IsosangClient.lift(mc.player.getUuid(),1)),0);
        double pull=dev.sangwoo.celestialfall.client.isosang.IsosangClient.cameraPull(1);
        for(int i=0;i<8;i++){
            BotandCamera.STATE.yaw=yaw;BotandCamera.STATE.pitch=pitch;var o=BotandCamera.STATE.offset(BotandCamera.STATE.distance,BotandCamera.shoulder(1));
            var off=new net.minecraft.util.math.Vec3d(o.x(),o.y(),o.z());var cam=eye.add(off).add(off.lengthSquared()>1e-6?off.normalize().multiply(pull):net.minecraft.util.math.Vec3d.ZERO);
            var d=target.subtract(cam);yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
        }
        BotandCamera.STATE.yaw=keepYaw;BotandCamera.STATE.pitch=keepPitch;
        return new float[]{yaw,pitch};
    }
    private void face(ServerPlayerEntity p,float face){float y=facing(face);p.setYaw(y);p.setHeadYaw(y);p.setBodyYaw(y);p.networkHandler.requestTeleport(p.getX(),p.getY(),p.getZ(),y,0);}

    @Override public void onInitializeClient(){
        instance=this;
        ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{tick();}catch(Throwable t){fail(t);}});
        // 2.67.0: the world pass's projection and view of each frame (camera.csv), and the viewport and framebuffer then
        // (2.107.0) "SWEEP@n": the mouse moved a little each frame (the camera turns with it from the next frame)
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(c->{try{sweep();}catch(Throwable t){fail(t);}});
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(c->{var pr=c.projectionMatrix();var v=c.matrixStack().peek().getPositionMatrix();
            int[] vp=new int[4];org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,vp);
            worldPass=String.format(Locale.ROOT,"%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d",pr.m00(),pr.m11(),v.m11(),v.m12(),v.m21(),vp[2],vp[3],
                org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)==MinecraftClient.getInstance().getFramebuffer().fbo?1:0);});
        // the UI shot's pointer: moved before a screen draws, drawn after it (Fabric makes a screen's events anew each
        // time it is set up, coming back from the options screen included, so they are registered every time)
        // 2.73.0: in play (no screen up, the cursor free on the map) the pointer glides and is drawn over the HUD
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((d,delta)->{if(mc!=null&&mc.currentScreen==null&&ui()&&!mc.mouse.isCursorLocked()){try{glide();}catch(Throwable t){fail(t);}pointer(d);}});
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.beforeRender(screen).register((s,d,x,y,delta)->{try{glide();}catch(Throwable t){fail(t);}});
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s,d,x,y,delta)->pointer(d));
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{try{actorTick(server);}catch(Throwable t){if(mc!=null)mc.execute(()->fail(t));}});
        // every sound the game starts, with the shot's clock and the time (to lay the recording under the film)
        if(AUDIO)ClientTickEvents.START_CLIENT_TICK.register(client->{if(client.getSoundManager()!=null&&!listening){listening=true;client.getSoundManager().registerListener((sound,set)->{
            if(phase==4)soundRows.append(String.format(Locale.ROOT,"%.3f,%d,%s%n",clock(),System.nanoTime(),sound.getId()));});}});
    }
    private int slowNow=SLOW;
    private void setSlow(int n)throws Exception{
        slowNow=n;System.setProperty("botand.shots.slow",Integer.toString(n));
        var f=MinecraftClient.class.getDeclaredField("renderTickCounter");f.setAccessible(true);var counter=f.get(mc);
        var t=counter.getClass().getDeclaredField("tickTime");t.setAccessible(true);t.setFloat(counter,50f*n);
        log.append("  slow ").append(n).append(" at ").append(age).append('\n');
    }
    private void tick()throws Exception{
        age++;if((System.nanoTime()-started)/1e9>1500*SLOW)throw new IllegalStateException("timeout");
        if(mc.getToastManager()!=null)mc.getToastManager().clear();
        if(phase>=1&&phase<99&&mc.world!=null&&mc.currentScreen instanceof dev.sangwoo.celestialfall.client.title.BotandTitleScreen)mc.setScreen(null);
        if(SLOW>1&&!slowed&&phase>=3){var f=MinecraftClient.class.getDeclaredField("renderTickCounter");f.setAccessible(true);var counter=f.get(mc);var t=counter.getClass().getDeclaredField("tickTime");t.setAccessible(true);t.setFloat(counter,50f*SLOW);slowed=true;}
        // 2.87.0 slows=AGE:N/AGE:N/...: from that tick of the entry on, the game runs N times slower (a heavy stretch slower,
        // the rest of the entry faster: Lee So-sang's G's clouds and strike took 6 to 8 s a frame in the software renderer).
        // The server follows at once (SkillShotsServerMixin reads the property every tick). Back to SLOW as an entry starts.
        if(phase==4&&age==1&&slowNow!=SLOW)setSlow(SLOW);
        if(phase==4&&opts.containsKey("slows"))for(String w:opts.get("slows").split("/")){String[] v=w.split(":");if(age==Integer.parseInt(v[0].trim()))setSlow(Integer.parseInt(v[1].trim()));}
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
                    // (2.83.0) "cine=0": the player's own cutscenes off (CelestialFall.CONFIG.cinematicCamera), so their G is seen
                    // as everyone else sees it; "gvar=1|2": the recording (and faces) their next G plays (IsosangAttack.riggedVariant)
                    CelestialFall.CONFIG.cinematicCamera=!"0".equals(opts.get("cine"));
                    if(opts.containsKey("gvar")){int gv=Integer.parseInt(opts.get("gvar"))-1;server(p->dev.sangwoo.celestialfall.isosang.IsosangAttack.riggedVariant=gv);log.append("  next G rigged to recording ").append(gv+1).append('\n');}
                    // (2.101.0) "hvar=1|2|3": the recording their next H again plays (IsosangAttack.riggedFinale)
                    if(opts.containsKey("hvar")){int hv=Integer.parseInt(opts.get("hvar"))-1;server(p->dev.sangwoo.celestialfall.isosang.IsosangAttack.riggedFinale=hv);log.append("  next H again rigged to recording ").append(hv+1).append('\n');}
                    // (2.87.3) "gfit=SCALE/LEAN/RIGHT/DOWN/YAW/PAN/DROP": G's sky-shot figure fitted so in this entry (GScene.tuned), to try a fit before it goes in
                    {double[] fit=null;if(opts.containsKey("gfit")){String[] v=opts.get("gfit").split("/");fit=new double[7];for(int i=0;i<7;i++)fit[i]=i<v.length?Double.parseDouble(v[i].trim()):0;log.append("  G sky figure fitted ").append(opts.get("gfit")).append('\n');}
                     var tuned=Class.forName("dev.sangwoo.celestialfall.client.isosang.GScene").getDeclaredField("tuned");tuned.setAccessible(true);tuned.set(null,fit);}
                    // (2.87.5) "gblade=ROUND/LEFT/RIGHT": G's sky-shot blade widened so in this entry (GScene.tunedBlade), to try a shape before it goes in
                    {double[] shape=null;if(opts.containsKey("gblade")){String[] v=opts.get("gblade").split("/");shape=new double[3];for(int i=0;i<3;i++)shape[i]=i<v.length?Double.parseDouble(v[i].trim()):1;log.append("  G sky blade shaped ").append(opts.get("gblade")).append('\n');}
                     var tunedBlade=Class.forName("dev.sangwoo.celestialfall.client.isosang.GScene").getDeclaredField("tunedBlade");tunedBlade.setAccessible(true);tunedBlade.set(null,shape);}
                    // (2.101.9) "hroll=DEG": H again's behind camera rolled so in this entry (SwordFinale.tunedRoll), to try a roll before it goes in
                    {Float roll=opts.containsKey("hroll")?Float.valueOf(opts.get("hroll")):null;if(roll!=null)log.append("  H again's behind camera rolled ").append(roll).append('\n');
                     var tunedRoll=Class.forName("dev.sangwoo.celestialfall.client.isosang.SwordFinale").getDeclaredField("tunedRoll");tunedRoll.setAccessible(true);tunedRoll.set(null,roll);}
                    // (2.101.9) "hcam=EF/EU/ER/LF/LU/LR": H again's behind camera's eye and what it looks at (blocks ahead of her, up, to her right) so in this entry (SwordFinale.tunedBehind)
                    {double[] cam=null;if(opts.containsKey("hcam")){String[] v=opts.get("hcam").split("/");cam=new double[6];for(int i=0;i<6;i++)cam[i]=Double.parseDouble(v[i].trim());log.append("  H again's behind camera at ").append(opts.get("hcam")).append('\n');}
                     var tunedBehind=Class.forName("dev.sangwoo.celestialfall.client.isosang.SwordFinale").getDeclaredField("tunedBehind");tunedBehind.setAccessible(true);tunedBehind.set(null,cam);}
                    // (2.116.1) "panelfit=TILT/OUT/DOWN/AHEAD[/WIDE/DEEP]": X Chara's hack panel placed so by their hand in this entry (HackPanel.tuned), to try a placing before it goes in
                    {float[] fit=null;if(opts.containsKey("panelfit")){String[] v=opts.get("panelfit").split("/");fit=new float[6];for(int i=0;i<6;i++)fit[i]=i<v.length?Float.parseFloat(v[i].trim()):1;log.append("  hack panel placed ").append(opts.get("panelfit")).append('\n');}
                     var tunedPanel=Class.forName("dev.sangwoo.celestialfall.client.xchara.HackPanel").getDeclaredField("tuned");tunedPanel.setAccessible(true);tunedPanel.set(null,fit);}
                    // "clearchat=1" (2.70.0): the chat emptied (none of the last entries' lines in this one's picture)
                    if(opts.containsKey("clearchat")&&!"0".equals(opts.get("clearchat")))mc.inGameHud.getChatHud().clear(false);
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
                    // "ZAIM<deg>@n" (2.93.0, Lee So-sang's Z): from tick n the camera turns over 8 ticks onto a sword of the player's
                    // own formation, the one nearest <deg> degrees clockwise of where it looks then, 3.5 to 10 blocks from them, and
                    // follows it until Z is pressed; it holds where it is after that
                    if(what.startsWith("ZAIM")){if(age==at){zaimSword=pickSword(Float.parseFloat(what.substring(4)));zaimAt=age;camKept=false;zaimYaw0=BotandCamera.STATE.yaw;zaimPitch0=BotandCamera.STATE.pitch;zaimYaw=zaimYaw0;zaimPitch=zaimPitch0;zaimHeld=true;
                            log.append(String.format(Locale.ROOT,"  aim at sword %d (on %d) at %d%n",zaimSword,dev.sangwoo.celestialfall.client.isosang.SwordStep.perchNow(),age));}continue;}
                    // (2.107.0) "FREECAM@n": the camera left to the game from tick n (the real mouse turns and zooms it); "RDOWN@n"
                    // and "RUP@n" press and let go of the right mouse button, and "SWEEP@n" moves the mouse, both through
                    // Minecraft's own mouse handlers (dead, waiting to come back, only the view moves: DeathView)
                    // (2.107.0) "WHERE@n": where the player is as their client and the server have them (the body's way, the
                    // hotbar slot, health, the screen up, how many bodies this client holds, whether the server keeps the dead
                    // body in the world)
                    if(what.equals("WHERE")){if(age==at){var q=mc.player;
                            log.append(String.format(Locale.ROOT,"  where at %d: client %.2f %.2f %.2f, body yaw %.1f, slot %d, health %.1f, screen %s, cursor %s, %d entities seen %s (theirs %s, removed %s, death time %d, pose %s), death view %s%n",age,q.getX(),q.getY(),q.getZ(),q.getYaw(),q.getInventory().selectedSlot,q.getHealth(),
                                mc.currentScreen==null?"-":mc.currentScreen.getClass().getSimpleName(),mc.mouse.isCursorLocked()?"captured":"free",java.util.stream.StreamSupport.stream(mc.world.getEntities().spliterator(),false).count(),
                                java.util.stream.StreamSupport.stream(mc.world.getEntities().spliterator(),false).map(e->e==q?"me":net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).getPath()).sorted().toList(),
                                mc.world.getEntityById(q.getId())==q,q.getRemovalReason(),q.deathTime,q.getPose(),dev.sangwoo.celestialfall.client.DeathView.active()));
                            final int when=age;server(p->log.append(String.format(Locale.ROOT,"  where at %d: server %.2f %.2f %.2f, body yaw %.1f, slot %d, health %.1f, removed %s, in the world's players %s%n",when,p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getInventory().selectedSlot,p.getHealth(),p.isRemoved(),p.getServerWorld().getPlayers().contains(p))));}continue;}
                    if(what.equals("FREECAM")){if(age==at){camFree=true;log.append(String.format(Locale.ROOT,"  camera free at %d (yaw %.1f, pitch %.1f, distance %.2f)%n",age,BotandCamera.STATE.yaw,BotandCamera.STATE.pitch,BotandCamera.STATE.distance));}continue;}
                    if(what.equals("RDOWN")||what.equals("RUP")){if(age==at){button(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT,what.equals("RDOWN"));log.append("  right button ").append(what.equals("RDOWN")?"down":"up").append(" at ").append(age).append(mc.mouse.isCursorLocked()?" (cursor captured)":" (cursor free)").append('\n');}continue;}
                    if(what.equals("SWEEP")){if(age==at){sweepAt=age;sweepDone=sweepDoneY=0;log.append(String.format(Locale.ROOT,"  sweep at %d (camera yaw %.1f, cursor %s)%n",age,BotandCamera.STATE.yaw,mc.mouse.isCursorLocked()?"captured":"free"));}continue;}
                    if(what.startsWith("KEY_")){int code=switch(what.substring(4)){case "TAB"->GLFW_KEY_TAB;case "ESCAPE"->GLFW_KEY_ESCAPE;case "R"->GLFW_KEY_R;case "ENTER"->GLFW_KEY_ENTER;case "UP"->GLFW_KEY_UP;case "DOWN"->GLFW_KEY_DOWN;case "Q"->GLFW_KEY_Q;case "T"->GLFW_KEY_T;case "Y"->GLFW_KEY_Y;case "G"->GLFW_KEY_G;case "H"->GLFW_KEY_H;case "2"->GLFW_KEY_2;case "M"->GLFW_KEY_M;case "E"->GLFW_KEY_E;case "F5"->GLFW_KEY_F5;case "SPACE"->GLFW_KEY_SPACE;case "Z"->GLFW_KEY_Z;default->throw new IllegalArgumentException(what);};
                        // (2.93.0) Z: which sword it aims at as it is pressed (the client's own reckoning), and the camera stops following it
                        if(what.equals("KEY_Z")&&age==at){log.append(String.format(Locale.ROOT,"  Z aims at sword %d (on %d, Z left %d) at %d%n",dev.sangwoo.celestialfall.client.isosang.SwordStep.aimedForFilm(),dev.sangwoo.celestialfall.client.isosang.SwordStep.perchNow(),
                            dev.sangwoo.celestialfall.client.isosang.IsosangClient.zState(mc.player.getUuid()).left(mc.world.getTime()),age));zaimSword=-1;}
                        // and 8 ticks on, where the server has them (the sword it holds them under, how far off it they are, how high) and Z's state
                        if(what.equals("KEY_Z")&&age==at+8)server(p->{var f=dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(p);var z=dev.sangwoo.celestialfall.isosang.IsosangAttack.zNow(p);long now=p.getWorld().getTime();
                            log.append(f==null?"  Z after: no formation\n":String.format(Locale.ROOT,"  Z after %d: %.2f blocks off where it holds them, %.2f up; Z first %d (%d ago), used %d, ready in %d ticks%n",age,Math.hypot(p.getX()-f.holds().x,p.getZ()-f.holds().z),p.getY()-f.centre().y,z.first(),now-z.first(),z.uses(),Math.max(0,z.ready()-now)));});
                        // (2.96.0) T on H's sword: whom and where the client's crosshair picks as it is pressed, whether the server
                        // began it (its foe, where she lands, the swords she flies from and to), and by its end what it struck,
                        // where the server has her and the dummy's health
                        if(what.equals("KEY_T")&&age==at){var aim=dev.sangwoo.celestialfall.client.isosang.IsosangClient.lungeAimNow(mc);
                            if(aim!=null)log.append(String.format(Locale.ROOT,"  T aims at %s (%.2f, %.2f, %.2f) at %d%n",aim.target()<0?"no one":aim.target()==dummyId?"the dummy":"entity "+aim.target(),aim.point().x,aim.point().y,aim.point().z,age));
                            // and the camera let go from DAIM/PAIM: T turns it itself (onto its foe as she lands beyond it); (2.96.1) and
                            // from then on left as the game leaves it, as a player's whose mouse is still (not set back each tick to the
                            // entry's way: that snapped it round as T let go of it), until another DAIM/PAIM/ZAIM
                            if(daimHeld||paimPoint!=null)log.append("  camera let go at ").append(age).append('\n');daimHeld=false;paimPoint=null;camKept=true;}
                        if(what.equals("KEY_T")&&(age==at+3||age==at+28))server(p->{var l=dev.sangwoo.celestialfall.isosang.IsosangAttack.lungeNow(p);var d=p.getServerWorld().getEntityById(dummyId);
                            var f=dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(p);if(f==null)return;
                            long now=p.getWorld().getTime();float hp=d instanceof net.minecraft.entity.LivingEntity e?e.getHealth():Float.NaN;
                            log.append(String.format(Locale.ROOT,"  T %s at %d: %s, dummy health %.1f, on sword %d, %.2f blocks off where it holds them, %.2f up, ready in %d%n",age==at+3?"begun":"after",age,
                                l==null?"none under way":String.format(Locale.ROOT,"begun %d ticks ago on %s at (%.2f, %.2f, %.2f), lands at (%.2f, %.2f, %.2f), sword %d to %d, %d struck",now-l.start(),l.target()<0?"no one":l.target()==dummyId?"the dummy":"entity "+l.target(),
                                    l.at().x,l.at().y,l.at().z,l.past().x,l.past().y,l.past().z,l.from(),l.to(),l.blows()),
                                hp,dev.sangwoo.celestialfall.isosang.IsosangAttack.standsOn(p),Math.hypot(p.getX()-f.holds().x,p.getZ()-f.holds().z),p.getY()-f.centre().y,
                                Math.max(0,dev.sangwoo.celestialfall.isosang.IsosangAttack.tReady(p)-now)));});
                        // (2.97.0) Y on H's sword: whom and where the crosshair picks as it is pressed (and the camera let go from
                        // DAIM/PAIM as for T: Y's shot and its turn onto the foe take it), whether the server began it (its foe,
                        // its ring), and by its end what it struck, the dummy's health and until when it was caged
                        if(what.equals("KEY_Y")&&age==at){var aim=dev.sangwoo.celestialfall.client.isosang.IsosangClient.cageAimNow(mc);
                            if(aim!=null)log.append(String.format(Locale.ROOT,"  Y aims at %s (%.2f, %.2f, %.2f) at %d%n",aim.target()<0?"no one":aim.target()==dummyId?"the dummy":"entity "+aim.target(),aim.point().x,aim.point().y,aim.point().z,age));
                            if(daimHeld||paimPoint!=null)log.append("  camera let go at ").append(age).append('\n');daimHeld=false;paimPoint=null;camKept=true;}
                        // (2.128.0) X Chara's slam (G with the hack off): by the server, the one in flight (how high it rises, where
                        // it comes down), where the player is from where they began, the dummy's health and how long it is held; and
                        // how far from where they are this client draws them
                        if(what.equals("KEY_G")&&age==at){slamFrom=mc.player.getPos();
                            if(opts.containsKey("slamaim")){String[] v=opts.get("slamaim").split("[_x]");var f=net.minecraft.util.math.Vec3d.fromPolar(0,mc.player.getYaw());var r=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
                                dev.sangwoo.celestialfall.client.xchara.OverwriteSlam.aimAt=mc.player.getPos().add(f.multiply(Double.parseDouble(v[0]))).add(r.multiply(v.length>1?Double.parseDouble(v[1]):0)).add(0,v.length>2?Double.parseDouble(v[2]):0,0);
                                log.append("  slam aimed at ").append(dev.sangwoo.celestialfall.client.xchara.OverwriteSlam.aimAt).append('\n');}
                            else dev.sangwoo.celestialfall.client.xchara.OverwriteSlam.aimAt=null;}
                        if(what.equals("KEY_G")&&slamFrom!=null&&(age==at+2||age==at+12||age==at+30||age==at+50||age==at+64||age==at+67||age==at+69||age==at+72||age==at+78||age==at+86||age==at+96)){final var from=slamFrom;final int a0=at;
                            server(p->{if(!dev.sangwoo.celestialfall.stand.StandManager.xChara(p))return;Object[] d=dev.sangwoo.celestialfall.xchara.XCharaAttack.describeSlam(p);var dm=p.getServerWorld().getEntityById(dummyId);
                                long now=p.getWorld().getTime();float hp=dm instanceof net.minecraft.entity.LivingEntity e?e.getHealth():Float.NaN;
                                long off=dm instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity t?Math.max(0,t.disabledUntil()-now):-1;
                                log.append(String.format(Locale.ROOT,"  slam +%d: server %s, player (%.2f, %.2f, %.2f) from the start, airborne %s, ready in %d, dummy hp %.1f held %d%n",age-a0,
                                    d==null?"none":String.format(Locale.ROOT,"age %d rise %.2f land %s blows %d",now-(Long)d[0],(Double)d[1],d[2]==null?"-":String.format(Locale.ROOT,"(%.2f, %.2f, %.2f)",((net.minecraft.util.math.Vec3d)d[2]).x-from.x,((net.minecraft.util.math.Vec3d)d[2]).y-from.y,((net.minecraft.util.math.Vec3d)d[2]).z-from.z),(Integer)d[3]),
                                    p.getX()-from.x,p.getY()-from.y,p.getZ()-from.z,dev.sangwoo.celestialfall.xchara.XCharaAttack.airborne(p),Math.max(0,dev.sangwoo.celestialfall.xchara.XCharaAttack.slamReady(p)-now),hp,off));});
                            var sh=dev.sangwoo.celestialfall.client.xchara.OverwriteSlam.offset(mc.player.getUuid(),0);
                            log.append(String.format(Locale.ROOT,"  slam +%d: client draws the body %s from where it is, knife %s, sounds %s%n",age-a0,sh==null?"nowhere else":String.format(Locale.ROOT,"(%.2f, %.2f, %.2f)",sh.x,sh.y,sh.z),
                                dev.sangwoo.celestialfall.client.xchara.HackMode.knifeLook(mc.player.getUuid(),0)==null?"black":"lit",String.join(" | ",dev.sangwoo.celestialfall.client.xchara.KnifeClient.HEARD.stream().filter(x->x.contains("slam")).toList())));}
                        // (2.129.0) X Chara's three cuts (E with the hack off): by the server, the one in flight (which kind, where its
                        // going forward ends, how many cuts landed), where the player is from where they began, the dummy's health,
                        // how far it is from where it stood, and how long it is staggered; and how far from where they are this
                        // client draws them
                        if(what.equals("KEY_E")&&age==at){cutFrom=mc.player.getPos();var dm=mc.world.getEntityById(dummyId);cutDummy=dm==null?null:dm.getPos();}
                        if(what.equals("KEY_E")&&cutFrom!=null&&dev.sangwoo.celestialfall.stand.StandManager.xChara(mc.player)&&age>at&&age<=at+40&&(age-at<=14||(age-at)%4==0)){final var from=cutFrom;final var stood=cutDummy;final int a0=at;
                            server(p->{if(!dev.sangwoo.celestialfall.stand.StandManager.xChara(p))return;Object[] d=dev.sangwoo.celestialfall.xchara.XCharaAttack.describeCutter(p);var dm=p.getServerWorld().getEntityById(dummyId);
                                long now=p.getWorld().getTime();float hp=dm instanceof net.minecraft.entity.LivingEntity e?e.getHealth():Float.NaN;
                                log.append(String.format(Locale.ROOT,"  cutter +%d: server %s, player (%.2f, %.2f, %.2f) from the start, ready in %d, dummy hp %.1f, %.2f from where it stood%n",age-a0,
                                    d==null?"none":String.format(Locale.ROOT,"age %d kind %d to %s blows %d",now-(Long)d[0],(Integer)d[1],d[2]==null?"-":String.format(Locale.ROOT,"(%.2f, %.2f, %.2f)",((net.minecraft.util.math.Vec3d)d[2]).x-from.x,((net.minecraft.util.math.Vec3d)d[2]).y-from.y,((net.minecraft.util.math.Vec3d)d[2]).z-from.z),(Integer)d[3]),
                                    p.getX()-from.x,p.getY()-from.y,p.getZ()-from.z,Math.max(0,dev.sangwoo.celestialfall.xchara.XCharaAttack.cutterReady(p)-now),hp,dm==null||stood==null?Double.NaN:dm.getPos().distanceTo(stood)));});
                            var sh=dev.sangwoo.celestialfall.client.xchara.EdgeCutter.offset(mc.player.getUuid(),0);
                            log.append(String.format(Locale.ROOT,"  cutter +%d: client draws the body %s from where it is, swing %s, sounds %s%n",age-a0,sh==null?"nowhere else":String.format(Locale.ROOT,"(%.2f, %.2f, %.2f)",sh.x,sh.y,sh.z),
                                dev.sangwoo.celestialfall.client.xchara.KnifeClient.describe(mc.player,0),String.join(" | ",dev.sangwoo.celestialfall.client.xchara.KnifeClient.HEARD.stream().filter(x->x.contains("cutter")).toList())));}
                        // (2.130.0) X Chara's knives shot (Y with the hack off): "shotaim=FW[_RT[_UP]]" sends them at that point from
                        // where the player stands ("shotaim=dummy": at the dummy's chest; a filming camera's middle is not the
                        // player's); by the server, the skill in flight (the knives let go, the last aim it was told), how many
                        // knives fly, the dummy's health and how long it is staggered; and each knife as this client draws it
                        if(what.equals("KEY_Y")&&age==at&&dev.sangwoo.celestialfall.stand.StandManager.xChara(mc.player)){shotFrom=mc.player.getPos();
                            if(opts.containsKey("shotaim")){String o=opts.get("shotaim");var f=net.minecraft.util.math.Vec3d.fromPolar(0,mc.player.getYaw());var r=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
                                if(o.equals("dummy")){var dm=mc.world.getEntityById(dummyId);dev.sangwoo.celestialfall.client.xchara.BladeShot.aimAt=dm==null?null:dm.getPos().add(0,dm.getHeight()*.55,0);}
                                else{String[] v=o.split("[_x]");dev.sangwoo.celestialfall.client.xchara.BladeShot.aimAt=mc.player.getPos().add(f.multiply(Double.parseDouble(v[0]))).add(r.multiply(v.length>1?Double.parseDouble(v[1]):0)).add(0,v.length>2?Double.parseDouble(v[2]):0,0);}
                                log.append("  knives aimed at ").append(dev.sangwoo.celestialfall.client.xchara.BladeShot.aimAt).append('\n');}
                            else dev.sangwoo.celestialfall.client.xchara.BladeShot.aimAt=null;}
                        if(what.equals("KEY_Y")&&shotFrom!=null&&dev.sangwoo.celestialfall.stand.StandManager.xChara(mc.player)&&!dev.sangwoo.celestialfall.client.xchara.HackMode.on(mc.player)&&age>at&&age<=at+44&&(age-at<=30||(age-at)%4==0)){final int a0=at;
                            server(p->{if(!dev.sangwoo.celestialfall.stand.StandManager.xChara(p))return;Object[] d=dev.sangwoo.celestialfall.xchara.XCharaAttack.describeShot(p);var dm=p.getServerWorld().getEntityById(dummyId);
                                long now=p.getWorld().getTime();float hp=dm instanceof net.minecraft.entity.LivingEntity e?e.getHealth():Float.NaN;
                                log.append(String.format(Locale.ROOT,"  shot +%d: server %s, %d knives flying, ready in %d, dummy hp %.1f%n",age-a0,
                                    d==null?"none":String.format(Locale.ROOT,"age %d let go %d aim %s",now-(Long)d[0],(Integer)d[1],d[2]==null?"-":d[2].toString()),
                                    dev.sangwoo.celestialfall.xchara.XCharaShot.flying(p),Math.max(0,dev.sangwoo.celestialfall.xchara.XCharaAttack.shotReady(p)-now),hp));});
                            StringBuilder ks=new StringBuilder();for(int k=0;k<4;k++)ks.append(" [").append(dev.sangwoo.celestialfall.client.xchara.BladeShot.describe(mc.player,k,0)).append(']');
                            log.append(String.format(Locale.ROOT,"  shot +%d: client knives (shown,flying,head,grip xyz,struck,far,took)%s, sounds %s%n",age-a0,ks,
                                String.join(" | ",dev.sangwoo.celestialfall.client.xchara.KnifeClient.HEARD.stream().filter(x->x.contains("knife")).toList())));}
                        // (2.127.0) X Chara's pull: by the server, how the last one went, where from and to, and where the dummy is
                        // from the player now, its health and whether anything holds it
                        if(what.equals("KEY_Y")&&(age==at+2||age==at+8||age==at+14))server(p->{double[] l=dev.sangwoo.celestialfall.xchara.XCharaPull.last(p);var d=p.getServerWorld().getEntityById(dummyId);
                            if(!dev.sangwoo.celestialfall.stand.StandManager.xChara(p))return;
                            long now=p.getWorld().getTime();float hp=d instanceof net.minecraft.entity.LivingEntity e?e.getHealth():Float.NaN;
                            long off=d instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dm?Math.max(0,dm.disabledUntil()-now):-1;
                            log.append(String.format(Locale.ROOT,"  Y (pull) at %d (+%d): %s; %s; dummy %s, health %.1f, disabled %d more ticks, ready in %d%n",age,age-at,
                                java.util.Arrays.toString(dev.sangwoo.celestialfall.xchara.XCharaPull.describe(p)),
                                l==null?"none yet":String.format(Locale.ROOT,"last: how %d, %d ticks ago, from (%.2f, %.2f, %.2f) to (%.2f, %.2f, %.2f)",(int)l[0],now-(long)l[1],l[2],l[3],l[4],l[5],l[6],l[7]),
                                d==null?"gone":String.format(Locale.ROOT,"at (%.2f, %.2f, %.2f), %.2f from the player, yaw %.0f",d.getX(),d.getY(),d.getZ(),Math.hypot(d.getX()-p.getX(),d.getZ()-p.getZ()),d.getYaw()),hp,off,
                                Math.max(0,dev.sangwoo.celestialfall.xchara.XCharaPull.ready(p)-now)));});
                        if(what.equals("KEY_Y")&&(age==at+3||age==at+12||age==at+34))server(p->{var y=dev.sangwoo.celestialfall.isosang.IsosangAttack.cageNow(p);var d=p.getServerWorld().getEntityById(dummyId);
                            var f=dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(p);if(f==null)return;
                            long now=p.getWorld().getTime();float hp=d instanceof net.minecraft.entity.LivingEntity e?e.getHealth():Float.NaN;
                            long caged=d instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity dm?Math.max(0,dm.disabledUntil()-now):-1;
                            log.append(String.format(Locale.ROOT,"  Y %s at %d: %s, dummy health %.1f, caged %d more ticks, on sword %d, %.2f up, ready in %d%n",age==at+3?"begun":"after",age,
                                y==null?"none under way":String.format(Locale.ROOT,"begun %d ticks ago on %s, ring at (%.2f, %.2f, %.2f) (%.2f from the dummy), %d struck",now-y.start(),y.target()<0?"no one":y.target()==dummyId?"the dummy":"entity "+y.target(),
                                    y.at().x,y.at().y,y.at().z,d==null?-1:Math.hypot(d.getX()-y.at().x,d.getZ()-y.at().z),y.blows()),
                                hp,caged,dev.sangwoo.celestialfall.isosang.IsosangAttack.standsOn(p),p.getY()-f.centre().y,
                                Math.max(0,dev.sangwoo.celestialfall.isosang.IsosangAttack.yReady(p)-now)));});
                        // (2.101.0) H again on her formation: whether the server began its finale (where she stands, its band's way,
                        // its blows so far), the dummy's health, how high she is over where she stands, when H is ready again
                        if(what.equals("KEY_H")&&(age==at+3||age==at+30||age==at+60||age==at+80))server(p->{var u=dev.sangwoo.celestialfall.isosang.IsosangAttack.finaleNow(p);if(u==null)return;
                            var d=p.getServerWorld().getEntityById(dummyId);long now=p.getWorld().getTime();float hp=d instanceof net.minecraft.entity.LivingEntity e?e.getHealth():Float.NaN;
                            var fw=net.minecraft.util.math.Vec3d.fromPolar(0,u.yaw());double dfw=d==null?Double.NaN:d.getPos().subtract(u.spot()).dotProduct(fw);
                            log.append(String.format(Locale.ROOT,"  H again at %d: begun %d ticks ago, she at (%.2f, %.2f, %.2f) %.2f up, %.2f off it, way %.1f, %d struck, dummy %.2f ahead, health %.1f, formation %s, H ready in %d%n",age,now-u.start(),
                                u.spot().x,u.spot().y,u.spot().z,p.getY()-u.spot().y,Math.hypot(p.getX()-u.spot().x,p.getZ()-u.spot().z),u.yaw(),u.blows(),dfw,hp,
                                dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(p)==null?"over":"standing",Math.max(0,dev.sangwoo.celestialfall.isosang.IsosangAttack.hReady(p)-now)));});
                        if(age==at){log.append("  key ").append(what.substring(4)).append(" at ").append(age).append('\n');key(code,true);if(what.equals("KEY_H"))lastH=age;
                            // 2.70.0: R with the stand arrow in hand: what the server sees as it is pressed (why it might refuse)
                            if(what.equals("KEY_R")&&mc.player.getMainHandStack().isOf(dev.sangwoo.celestialfall.meteor.MeteorRegistry.STAND_ARROW))server(p->log.append(String.format(Locale.ROOT,
                                "  arrow use at %d: stand %s, can change %s, on ground %s (client %s), map %s, use key %s%n",age,ProgressManager.profile(p).stand(),ProgressManager.canChange(p),p.isOnGround(),mc.player.isOnGround(),
                                dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()),mc.options.useKey.getBoundKeyTranslationKey())));}
                        if(age==at+1)key(code,false);continue;}
                    if(what.startsWith("MOVE_")){if(age==at){glideTo=null;double[] g=target(what.substring(5));double k=mc.getWindow().getScaleFactor();glideFrom=new double[]{mc.mouse.getX(),mc.mouse.getY()};glideTo=new double[]{g[0]*k,g[1]*k};glideAt=age;log.append("  move ").append(what.substring(5)).append(" at ").append(age).append('\n');}continue;}
                    if(what.equals("TP")||what.equals("TPB")){if(age==at)server(p->{String[] v=opts.get(what.equals("TP")?"tp":"tpb").split("[/,]");double[] d=new double[5];for(int i=0;i<v.length;i++)d[i]=Double.parseDouble(v[i].trim());
                            p.networkHandler.requestTeleport(d[0],d[1],d[2],(float)d[3],(float)d[4]);p.setYaw((float)d[3]);p.setHeadYaw((float)d[3]);p.setBodyYaw((float)d[3]);log.append("  teleported at ").append(age).append('\n');});continue;}
                    if(what.equals("FALL")||what.equals("FALLB")){if(age==at)server(p->{
                            String[] v=opts.get(what.equals("FALL")?"fall":"fallb").split("[/,]");var w=p.getServerWorld();
                            int slot=dev.sangwoo.celestialfall.meteor.MeteorState.get(w).firstEmpty();
                            var m=slot<0?null:dev.sangwoo.celestialfall.meteor.MeteorManager.launch(w,slot,new net.minecraft.util.math.Vec3d(Double.parseDouble(v[0]),w.getTopY(),Double.parseDouble(v[1])),opts.containsKey("from")?opt("from",0):Double.NaN);
                            log.append("  fall ").append(m==null?"refused":String.format(Locale.ROOT,"#%d to (%.1f, %.1f, %.1f) from (%.0f, %.0f, %.0f) at tick %d",m.slot(),m.x(),m.y(),m.z(),m.fromX(),m.fromY(),m.fromZ(),age)).append('\n');});continue;}
                    if(what.equals("CLEARMETEOR")){if(age==at)server(p->{int n=p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(),"botand meteor clear");log.append("  meteors cleared (").append(n).append(") at ").append(age).append('\n');});continue;}
                    // 2.74.2: a Rokakaka in the main hand (a left click with it asks to reset the stand: MOVE_C2 is the
                    // confirmation's 사용하고 초기화)
                    if(what.equals("GIVE_FRUIT")){if(age==at)server(p->{
                            p.getInventory().setStack(p.getInventory().selectedSlot,new net.minecraft.item.ItemStack(dev.sangwoo.celestialfall.commerce.CommerceRegistry.ROKAKAKA));
                            p.currentScreenHandler.sendContentUpdates();log.append("  given FRUIT (main hand) at ").append(age).append('\n');});continue;}
                    if(what.equals("GIVE_ARROW")){if(age==at)server(p->{
                            p.getInventory().setStack(p.getInventory().selectedSlot,new net.minecraft.item.ItemStack(dev.sangwoo.celestialfall.meteor.MeteorRegistry.STAND_ARROW));
                            p.currentScreenHandler.sendContentUpdates();log.append("  given ARROW (main hand) at ").append(age).append('\n');});continue;}
                    if(what.equals("AWAKEN")||what.equals("UNAWAKEN")){if(age==at)server(p->{
                            // 2.72.0: the operator's command, as the player (its answer goes to their chat)
                            int n=p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource(),"botand progress awaken @s"+(what.equals("AWAKEN")?"":" off"));
                            log.append("  ").append(what.equals("AWAKEN")?"awakened":"awakening taken back").append(" by the command at ").append(age).append(n>0?"":" (refused)").append(" (server: ")
                                .append(ProgressManager.awakened(p)).append(")\n");});continue;}
                    if(what.equals("LONGCD")){if(age==at)server(p->{
                            String[] v=opts.getOrDefault("lcd","240/60/40").split("/");long now=p.getWorld().getTime();var server=p.getServer();
                            var skills=dev.sangwoo.celestialfall.LongCooldowns.Skill.values();
                            for(int i=0;i<skills.length;i++){long ready=Math.round(Double.parseDouble(v[i].trim())*20);ready=ready>0?now+ready:0;
                                switch(skills[i]){case ATOMIC->dev.sangwoo.celestialfall.AtomicManager.restore(p,ready,now);case TIME_STOP->dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.restore(p,ready);case BEATDOWN->dev.sangwoo.celestialfall.stand.StarPlatinumAttack.restoreBeatdown(p,ready);}
                                dev.sangwoo.celestialfall.LongCooldowns.note(server,p.getUuid(),skills[i],ready);}
                            log.append("  long cooldowns put on at ").append(age).append(" (").append(opts.getOrDefault("lcd","240/60/40")).append(" s left)\n");});continue;}
                    // 2.74.1: the player's next gacha pull made an SSR (their count at 99 pulls since one, as the operator's
                    // "/botand gacha set" makes it: the banner then says SSR 확정까지 1회)
                    if(what.equals("SSRNEXT")){if(age==at)server(p->{
                            var g=dev.sangwoo.celestialfall.gacha.GachaState.get(p.getServer());var c=g.get(p.getUuid());
                            g.set(p.getUuid(),new dev.sangwoo.celestialfall.gacha.GachaRules.Count(dev.sangwoo.celestialfall.gacha.GachaRules.SSR_PITY-1,c.sinceSr(),c.pulls()));
                            log.append("  next gacha pull made an SSR at ").append(age).append('\n');});continue;}
                    if(what.equals("NOSTAND")){if(age==at)server(p->{
                            int n=p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource(),"botand progress stand @s none");
                            log.append("  stand taken off at ").append(age).append(n>0?"":" (refused)").append(" (server: ").append(ProgressManager.profile(p).stand()).append(")\n");});continue;}
                    if(what.equals("RIG_DEATH")||what.equals("RIG_STAR")){if(age==at)server(p->{
                            dev.sangwoo.celestialfall.stand.StandArrow.rigged=what.equals("RIG_DEATH")?dev.sangwoo.celestialfall.stand.StandArrowRules.DIE:"star_platinum";
                            log.append("  next arrow rigged to ").append(dev.sangwoo.celestialfall.stand.StandArrow.rigged).append(" at ").append(age).append('\n');});continue;}
                    if(what.startsWith("GIVE_")){if(age==at)server(p->{
                            var stack=switch(what.substring(5)){case "PICKAXE"->new net.minecraft.item.ItemStack(dev.sangwoo.celestialfall.meteor.MeteorRegistry.PICKAXE);case "FRAGMENTS"->new net.minecraft.item.ItemStack(dev.sangwoo.celestialfall.meteor.MeteorRegistry.FRAGMENT,3);case "STICK"->new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK);default->throw new IllegalArgumentException(what);};
                            p.getInventory().offerOrDrop(stack);p.currentScreenHandler.sendContentUpdates();log.append("  given ").append(what.substring(5)).append(" at ").append(age).append('\n');});continue;}
                    if(what.startsWith("KEYDOWN_")||what.startsWith("KEYUP_")){boolean down=what.startsWith("KEYDOWN_");int code=switch(what.substring(what.indexOf('_')+1)){case "E"->GLFW_KEY_E;case "W"->GLFW_KEY_W;case "A"->GLFW_KEY_A;case "S"->GLFW_KEY_S;case "D"->GLFW_KEY_D;case "Y"->GLFW_KEY_Y;case "R"->GLFW_KEY_R;case "SPACE"->GLFW_KEY_SPACE;case "F"->GLFW_KEY_F;default->throw new IllegalArgumentException(what);};
                        if(age==at){log.append("  key ").append(what).append(" at ").append(age).append('\n');key(code,down);}continue;}
                    if(what.startsWith("STANCE_")){if(age==at)server(p->{int stance=dev.sangwoo.celestialfall.stand.StarPlatinumStance.NAMES.indexOf(what.substring(7).toLowerCase(Locale.ROOT));
                            for(var f:p.getServerWorld().getEntitiesByClass(dev.sangwoo.celestialfall.stand.StarPlatinumEntity.class,p.getBoundingBox().expand(64),e->true))f.setStance(stance);
                            log.append("  stance ").append(what.substring(7)).append(" at ").append(age).append('\n');});continue;}
                    // "WHEEL_DOWN@n"/"WHEEL_UP@n" (2.73.0): a notch of the mouse wheel where the pointer is, through
                    // Minecraft's own mouse handler (over the player list it scrolls the list)
                    if(what.equals("WHEEL_DOWN")||what.equals("WHEEL_UP")){if(age==at){
                            double zoom=BotandCamera.STATE.targetDistance;
                            try{var m=net.minecraft.client.Mouse.class.getDeclaredMethod("onMouseScroll",long.class,double.class,double.class);m.setAccessible(true);
                                m.invoke(mc.mouse,mc.getWindow().getHandle(),0d,what.equals("WHEEL_UP")?1d:-1d);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                            var sc=dev.sangwoo.celestialfall.client.ui.PlayerPanel.scroll();
                            // (2.107.0) and the camera's zoom (where it goes), which the wheel sets in play and, dead, waiting to come back
                            log.append("  wheel ").append(what.equals("WHEEL_UP")?"up":"down").append(" at ").append(age).append(String.format(Locale.ROOT,": the list goes to row %d, the camera's zoom %.2f -> %.2f%n",(int)sc[1],zoom,BotandCamera.STATE.targetDistance));}continue;}
                    if(what.equals("CLICK")){if(age==at){log.append("  click at ").append(age).append(" on ").append(mc.currentScreen==null?"-":mc.currentScreen.getClass().getSimpleName()).append('\n');clickAt=age;button(true);}if(age==at+1)button(false);continue;}
                    // 2.54.0: the attack (the left button) held down and let go, through Minecraft's own mouse handler
                    if(what.equals("ATTACKDOWN")||what.equals("ATTACKUP")){if(age==at){log.append("  attack ").append(what.equals("ATTACKDOWN")?"down":"up").append(" at ").append(age).append('\n');button(what.equals("ATTACKDOWN"));}
                        // (2.95.0) on H's sword: whom and where the client's crosshair picks for 회풍만검 as it is pressed,
                        // whether the server began one (whom it comes down on), and what its swords struck by its end
                        if(what.equals("ATTACKDOWN")){
                            if(age==at){var aim=dev.sangwoo.celestialfall.client.isosang.IsosangClient.aimNow(mc);
                                if(aim!=null)log.append(String.format(Locale.ROOT,"  volley aims at %s (%.2f, %.2f, %.2f) at %d%n",aim.target()<0?"no one":aim.target()==dummyId?"the dummy":"entity "+aim.target(),aim.point().x,aim.point().y,aim.point().z,age));}
                            if(age==at+3||age==at+27)server(p->{var v=dev.sangwoo.celestialfall.isosang.IsosangAttack.volleyNow(p);var d=p.getServerWorld().getEntityById(dummyId);
                                if(v==null&&age==at+27&&!(d instanceof net.minecraft.entity.LivingEntity))return;
                                long now=p.getWorld().getTime();float hp=d instanceof net.minecraft.entity.LivingEntity l?l.getHealth():Float.NaN;
                                if(age==at+3&&dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(p)==null)return;
                                log.append(String.format(Locale.ROOT,"  volley %s at %d: %s, dummy health %.1f, ready in %d%n",age==at+3?"begun":"after",age,
                                    v==null?"none under way":String.format(Locale.ROOT,"begun %d ticks ago on %s at (%.2f, %.2f, %.2f), %d struck",now-v.start(),v.target()<0?"no one":v.target()==dummyId?"the dummy":"entity "+v.target(),v.at().x,v.at().y,v.at().z,v.blows()),
                                    hp,Math.max(0,dev.sangwoo.celestialfall.isosang.IsosangAttack.vReady(p)-now)));});
                        }
                        continue;}
                    // "DAIM@n" / "PAIM<deg>@n" (2.95.0): the camera onto the training dummy (following it), or onto the spot of the
                    // ground "paimd" (10) blocks from the player <deg> degrees clockwise of where the camera looks then (held)
                    if(what.equals("DAIM")||what.startsWith("PAIM")){if(age==at){
                            daimAt=age;daimYaw0=BotandCamera.STATE.yaw;daimPitch0=BotandCamera.STATE.pitch;zaimHeld=false;daimHeld=what.equals("DAIM");paimPoint=null;camKept=false;
                            if(!daimHeld){var f=net.minecraft.util.math.Vec3d.fromPolar(0,BotandCamera.STATE.yaw+Float.parseFloat(what.substring(4)));var at2=mc.player.getPos().add(f.multiply(opt("paimd",10)));
                                int gy=mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,(int)Math.floor(at2.x),(int)Math.floor(at2.z));paimPoint=new net.minecraft.util.math.Vec3d(at2.x,gy+.05,at2.z);}
                            log.append(String.format(Locale.ROOT,"  camera onto %s at %d%n",daimHeld?"the dummy":String.format(Locale.ROOT,"the ground at (%.2f, %.2f, %.2f)",paimPoint.x,paimPoint.y,paimPoint.z),age));}continue;}
                    // 2.67.0: a second Star Platinum user (a server-side fake player, its stand out) "cfw" blocks ahead of the
                    // player (and "crt" to their right), facing them: "FAKE@n" brings it in, "FAKEH@n" makes it stop time (H, its
                    // real rules: the player stands stopped in it), "HURT@n" deals the player "hurt" (4) through the game's own
                    // blow path from it (kept until time moves again), "UNFAKE@n" takes it away
                    // (2.92.0) "fstand=isosang" makes it a Lee So-sang user instead (its stand id; "fname" its name, SP_User),
                    // and "FAKEG@n" has it cast G by the real rules (IsosangAttack.ultimate): the player is one of the
                    // others in its blast's ground. The server forgets a fake player's swing the next tick (its tick looks
                    // players up in the player list), so its G strikes no one: the player's client plays it out alone.
                    if(what.equals("FAKE")){if(age==at)server(p->{
                            var w=p.getServerWorld();var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),opts.getOrDefault("fname","SP_User"));
                            String fstand=opts.getOrDefault("fstand","star_platinum");
                            var fake=net.fabricmc.fabric.api.entity.FakePlayer.get(w,profile);
                            var f=net.minecraft.util.math.Vec3d.fromPolar(0,p.getYaw());var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
                            var to=p.getPos().add(f.multiply(opt("cfw",6))).add(right.multiply(opt("crt",0)));float yaw=p.getYaw()+180;
                            fake.refreshPositionAndAngles(to.x,p.getY(),to.z,yaw,0);fake.setHeadYaw(yaw);fake.setBodyYaw(yaw);
                            ((StandHolder)fake).botand$setStand(fstand);((StandHolder)fake).botand$setStandOut(fstand.equals("star_platinum"));
                            p.networkHandler.sendPacket(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(List.of(fake)));
                            boolean in=w.spawnEntity(fake);fakePlayer=fake;
                            log.append("  fake ").append(fstand).append(" user ").append(in?"in":"refused").append(String.format(Locale.ROOT," at (%.2f, %.2f, %.2f) facing %.0f, tick %d%n",to.x,to.y,to.z,yaw,age));});continue;}
                    if(what.equals("FAKEG")){if(age==at)server(p->{boolean ok=fakePlayer!=null&&dev.sangwoo.celestialfall.isosang.IsosangAttack.ultimate(fakePlayer);log.append("  fake G ").append(ok?"at ":"refused at ").append(age).append('\n');});continue;}
                    // (2.99.1) "FAKEFORM@n": the fake Lee So-sang user turns the way the player faces and casts H by the real
                    // rules (IsosangAttack.formation): the player is someone else, outside it or walking into it (H's night is
                    // its caster's and whoever stands in it). The server forgets a fake's swing the next tick (as its G), so
                    // the player's client plays the formation out alone and nothing strikes. From then on, every 5 ticks, the
                    // log gets how far the player's feet are from its middle and how much of its night their screen shows.
                    if(what.equals("FAKEFORM")){
                        if(age==at)server(p->{var fake=fakePlayer;boolean ok=false;
                            if(fake!=null){float yaw=p.getYaw();fake.refreshPositionAndAngles(fake.getX(),fake.getY(),fake.getZ(),yaw,0);fake.setHeadYaw(yaw);fake.setBodyYaw(yaw);
                                fakeId=fake.getUuid();ok=dev.sangwoo.celestialfall.isosang.IsosangAttack.formation(fake);}
                            log.append("  fake H (formation) ").append(ok?"at ":"refused at ").append(age).append('\n');});
                        if(age>at&&(age-at)%5==0&&fakeId!=null){var swing=dev.sangwoo.celestialfall.client.isosang.IsosangClient.formationOf(fakeId);
                            var form=swing==null?null:dev.sangwoo.celestialfall.client.isosang.IsosangClient.formation(swing.id());
                            if(form!=null)log.append(String.format(Locale.ROOT,"  passer-by %.2f blocks from its middle at %d (H %.2f s), its night shown %.2f%n",
                                Math.hypot(mc.player.getX()-form.centre().x,mc.player.getZ()-form.centre().z),age,swing.age(0)/20f,dev.sangwoo.celestialfall.client.isosang.IsosangClient.nightShown(swing.id())));}
                        continue;}
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
                    // "FAKERUN@n" (2.75.0): from shot tick n for "frn" ticks (60) the fake user walks at a player's pace, "fspd"
                    // (4.317) blocks a second, the way "fdir" degrees (90) round from the player's facing as it starts (90:
                    // across in front of them, to their right), facing that way: a step a tick, and none while a time stop
                    // holds it (KagenoManager.frozen, as a player's own client is held), so it walks on through the two
                    // seconds it resists another's stopped time and stops as they run out, as a player would
                    if(what.equals("FAKERUN")){int n=(int)opt("frn",60);if(age>=at&&age<at+n){boolean first=age==at;server(p->{var fake=fakePlayer;if(fake==null)return;
                            if(first){fakeRunYaw=p.getYaw()+(float)opt("fdir",90);log.append("  fake user walks off at ").append(age).append('\n');}
                            if(KagenoManager.frozen(fake))return;
                            var stride=net.minecraft.util.math.Vec3d.fromPolar(0,fakeRunYaw).multiply(opt("fspd",4.317f)/20);
                            fake.refreshPositionAndAngles(fake.getX()+stride.x,fake.getY(),fake.getZ()+stride.z,fakeRunYaw,0);fake.setHeadYaw(fakeRunYaw);fake.setBodyYaw(fakeRunYaw);});}continue;}
                    // (2.94.0) and with Lee So-sang's H under way, which sword she stands on as it comes and 8 ticks on (H's dodge
                    // moves her onto another at random as the first blow is kept from her), and how far she stands off where the
                    // server holds her then
                    if(what.equals("HURT")){if(age==at)server(p->{float amount=(float)opt("hurt",4);int on=dev.sangwoo.celestialfall.isosang.IsosangAttack.standsOn(p);
                            boolean ok=dev.sangwoo.celestialfall.combat.CombatManager.damageWithoutKnockback(p,fakePlayer==null?p.getDamageSources().generic():p.getDamageSources().playerAttack(fakePlayer),amount);
                            log.append(String.format(Locale.ROOT,"  hurt %.1f %s at %d (health %.1f)%s%n",amount,ok?"landed":"refused",age,p.getHealth(),on<0?"":String.format(Locale.ROOT,", on sword %d, H's dodge %s",on,dev.sangwoo.celestialfall.isosang.IsosangAttack.dodged(p)?"used":"unused")));});
                        if(age==at+8)server(p->{var f=dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(p);if(f==null)return;
                            log.append(String.format(Locale.ROOT,"  hurt after %d: on sword %d, %.2f blocks off where it holds them, %.2f up (health %.1f)%n",age,dev.sangwoo.celestialfall.isosang.IsosangAttack.standsOn(p),Math.hypot(p.getX()-f.holds().x,p.getZ()-f.holds().z),p.getY()-f.centre().y,p.getHealth()));});
                        continue;}
                    // "FLING@n": the player sent flying "fly" (2) blocks a tick away from the fake user and "flyup" (0.6) up, as a
                    // punch's throw is (held by a time stop that holds the player until time moves again)
                    if(what.equals("FLING")){if(age==at)server(p->{var from=fakePlayer==null?p.getPos().add(0,0,-1):fakePlayer.getPos();var away=p.getPos().subtract(from).multiply(1,0,1).normalize();
                            var v=away.multiply(opt("fly",2)).add(0,opt("flyup",.6f),0);boolean kept=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.deferLaunch(p,v);
                            if(!kept){p.setVelocity(v);p.velocityModified=true;}
                            log.append(String.format(Locale.ROOT,"  fling (%.2f, %.2f, %.2f) %s at %d%n",v.x,v.y,v.z,kept?"kept for when time moves":"now",age));});continue;}
                    // 2.69.0: the boss. "BOSS@n" brings Kujo Jotaro in "bfw" blocks (7) ahead of the player ("brt" to their right),
                    // facing them, neutral, with "bhp" health (his whole); "BOSSANGRY@n" angers him with the player as a blow of
                    // theirs would; "BOSSHP@n" sets his health to "bhp2" (after "BOSSANGRY": a calm boss heals where he stands, 50
                    // a tick, so "bhp" under his whole only lasts until he is angered); "BOSSKILL@n" fells him with a blow of the
                    // player's (his reward, his boxes); "UNBOSS@n" takes him off. "pfull=1" keeps the player's health up (whole
                    // again under 70).
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
                    // "MATES@n" (2.73.0): "mates" (9) more players on the server with names of their own, in a row 40
                    // blocks behind the player (small in the distance), for the player list to fill past its five rows;
                    // "UNMATES@n" takes them off
                    if(what.equals("MATES")){if(age==at)server(p->{
                            String[] names={"Sakura_77","minsu","tow","Bomi","joshhhh","DarkBlue","Steve99","ORA_ORA","yare_yare","Hourglass","Kujo","Azure"};
                            var w=p.getServerWorld();var f=net.minecraft.util.math.Vec3d.fromPolar(0,p.getYaw());var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);
                            int n=(int)opt("mates",9);
                            for(int i=0;i<n;i++){
                                var m=new Mate(w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),names[i%names.length]));
                                var to=p.getPos().add(f.multiply(-40)).add(right.multiply((i-n/2.)*2));m.refreshPositionAndAngles(to.x,p.getY(),to.z,p.getYaw(),0);
                                ProgressManager.set(m,ProgressManager.profile(m).withStand("none"));
                                for(var q:p.getServer().getPlayerManager().getPlayerList())q.networkHandler.sendPacket(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(List.of(m)));
                                m.changeGameMode(GameMode.SURVIVAL);w.spawnEntity(m);m.floor=20;mates.add(m);
                            }
                            log.append("  ").append(n).append(" players in at ").append(age).append('\n');});continue;}
                    if(what.equals("UNMATES")){if(age==at)server(p->{for(var m:mates){for(var q:p.getServer().getPlayerManager().getPlayerList())q.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(List.of(m.getUuid())));m.discard();}
                            log.append("  ").append(mates.size()).append(" players gone at ").append(age).append('\n');mates.clear();});continue;}
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
                    // "SLEEP@n" (2.69.0): night, the day's cycle on, a bed under the player and the player in it; the log tells
                    // how many players the world counts (the boss among them) and when the night is slept through (the game
                    // wakes everyone in bed then): a boss must not keep it from being slept through (BossSleepMixin)
                    if(what.equals("SLEEP")){if(age==at)server(p->{
                            var w=p.getServerWorld();w.setTimeOfDay(18000);w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(true,w.getServer());
                            var dir=p.getHorizontalFacing();var foot=p.getBlockPos();
                            var bed=net.minecraft.block.Blocks.RED_BED.getDefaultState().with(net.minecraft.block.BedBlock.FACING,dir);
                            w.setBlockState(foot.offset(dir),bed.with(net.minecraft.block.BedBlock.PART,net.minecraft.block.enums.BedPart.HEAD));
                            w.setBlockState(foot,bed.with(net.minecraft.block.BedBlock.PART,net.minecraft.block.enums.BedPart.FOOT));
                            var r=p.trySleep(foot);if(r.right().isPresent())inBedAt=age;
                            log.append("  in bed at ").append(age).append(": ").append(r.left().map(Object::toString).orElse("asleep"))
                                .append(", the world counts ").append(w.getPlayers().size()).append(" players (")
                                .append(w.getPlayers().stream().filter(q->q instanceof dev.sangwoo.celestialfall.boss.BossPlayer).count()).append(" a boss)\n");});continue;}
                    if(what.equals("PHP")){if(age==at)server(p->{p.setHealth((float)opt("php",40));log.append(String.format(Locale.ROOT,"  player health set to %.0f at %d%n",p.getHealth(),age));});continue;}
                    if(what.equals("UNMATE")){if(age==at)server(p->{if(mate!=null){for(var q:p.getServer().getPlayerManager().getPlayerList())q.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(List.of(mate.getUuid())));mate.discard();mate=null;log.append("  mate gone at ").append(age).append('\n');}});continue;}
                    if(what.equals("UNFAKE")){if(age==at)server(p->{if(fakePlayer!=null){p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket(List.of(fakePlayer.getUuid())));fakePlayer.discard();fakePlayer=null;log.append("  fake gone at ").append(age).append('\n');}});continue;}
                    // (2.110.0) "CHAT@n": the chat opened with "chat" (a command, e.g. "/botand vanilla") typed in it, as a
                    // player types it (KEY_ENTER sends it); the log gets the map as this client has it and the player's game
                    // mode, as it opens and each 10 ticks after for 30 ticks
                    if(what.equals("CHAT")){
                        if(age==at){mc.setScreen(new net.minecraft.client.gui.screen.ChatScreen(opts.getOrDefault("chat","")));log.append("  chat opened with \"").append(opts.getOrDefault("chat","")).append("\" at ").append(age).append('\n');}
                        if(age>=at&&age<=at+40&&(age-at)%10==0){var gm=mc.interactionManager==null?null:mc.interactionManager.getCurrentGameMode();
                            log.append(String.format(Locale.ROOT,"  at %d: map %s, game mode %s, vanilla %s, inventory key %s, perspective %s%n",age,dev.sangwoo.celestialfall.client.MapClient.enabled(),gm,
                                dev.sangwoo.celestialfall.map.VanillaMode.on(mc.player),mc.options.inventoryKey.getBoundKeyTranslationKey(),mc.options.getPerspective()));}
                        continue;}
                    // (2.109.0) "DUMMYTO@n": the training dummy put "dto" blocks (1.8) in front of the player (and "drt" to their
                    // right), facing them, as it is: an attacking one (dattack=1) left out of its reach till then throws its
                    // first jab at once (it lands WINDUP[1] ticks on), as a blow comes at one glowing before a skill with a cutscene
                    if(what.equals("DUMMYTO")){if(age==at)server(p->{var d=p.getServerWorld().getEntityById(dummyId);if(d instanceof net.minecraft.entity.LivingEntity l){
                            var f=net.minecraft.util.math.Vec3d.fromPolar(0,p.getYaw());var right=new net.minecraft.util.math.Vec3d(-f.z,0,f.x);var to=p.getPos().add(f.multiply(opt("dto",1.8f))).add(right.multiply(opt("drt",0)));
                            l.setVelocity(0,0,0);l.refreshPositionAndAngles(to.x,p.getY(),to.z,p.getYaw()+180,0);l.setHeadYaw(p.getYaw()+180);l.setBodyYaw(p.getYaw()+180);
                            log.append(String.format(Locale.ROOT,"  dummy to %.2f blocks in front at %d (world %d)%n",opt("dto",1.8f),age,p.getWorld().getTime()));}});continue;}
                    // the training dummy back in front of the player (after the finisher threw it), whole again
                    // (2.113.0) "GAUGE@n": Lee So-sang's H gauge full (as /botand gauge), so H may be used at once
                    if(what.equals("GAUGE")){if(age==at){server(p->dev.sangwoo.celestialfall.isosang.IsosangAttack.setGauge(p,dev.sangwoo.celestialfall.isosang.IsosangRules.GAUGE_FULL));log.append("  gauge full at ").append(age).append('\n');}continue;}
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
                    filming=false;if(!actors.isEmpty()){var rows="age,name,x,z,out,held,speed_attr,edge,health\n"+actorLog;var path=OUT.resolve(folder).resolve("actors.csv");Files.createDirectories(path.getParent());Files.writeString(path,rows);}
                    if(shakeLog.length()>0){var path=OUT.resolve(folder).resolve("shake.csv");Files.createDirectories(path.getParent());Files.writeString(path,"t,gap,wall,yaw,pitch\n"+shakeLog);}
                    {var path=OUT.resolve(folder).resolve("motion.csv");Files.createDirectories(path.getParent());Files.writeString(path,"t,x,y,z,yaw,fov_target,fov\n"+motionLog);}
                    if(bossLog.length()>0){Files.writeString(OUT.resolve(folder).resolve("boss.csv"),"t,health,angry,doing,target,distance,x,z,yaw,stand_out,guard,dash,player_health,player_held,boxes,mate_health\n"+bossLog);bossLog.setLength(0);bossDoing="";}
                    if(standLog.length()>0){Files.writeString(OUT.resolve(folder).resolve("stand.csv"),"t,server_out,since,out_flag,out,alpha,scale,idle,hero,summon,wind,gust,lean,speed,glow,aura,combo,punch_age,landed,struck,punch_ticks,engage,guard,wind_l,punch_l,wind_r,punch_r,wind_hook,hook,wind_big,punch_big,"
                        +"rush_age,rush_blows,rush_struck,rush_on,rush_strike,dummy_health,speed_attr,rush_l,rush_l_hi,rush_l_lo,rush_r,rush_r_hi,rush_r_lo,"
                        +"wind_heavy,punch_heavy,dummy_guard,dummy_broken,dummy_x,dummy_y,dummy_z,wind_finger,star_finger,finger_open,finger_ext,finger_len,finger_reach,dummy_disabled,dummy_speed,"
                        +"inhale,suction,strain,inhale_age,inhale_on,inhale_caught,inhale_blocked,inhale_suck,dummy_ahead,dummy_side,"
                        +"beat_age,beat_ended,beat_cut,beat_bars,beat_camera,flash,beat_grab,beat_upper,shown_ahead,shown_side,shown_up,beat_wind,beat_strike,wind_heavy,punch_heavy,glowed,beat_hidden,"
                        +"ts_age,ts_stopped,ts_ended,ts_cut,ts_still,ts_radius,ts_outer,ts_flash,ts_negative,ts_shell,ts_crack,ts_impact,world_gather,world,player_health,dummy_stopped,player_held,player_locked,player_staggered,dummy_staggered,dummy_strike,arrow_age,arrow_fate,arrow_glow,arrow_fallen,arrow_sink,arrow_cut,server_stand,arrows,awake,server_awake,call,h_ago,"
                        +"player_resisting,player_stopped,fake_resisting,fake_stopped,fake_x,fake_z,ts_kept,ts_auras,player_colour,fake_colour,xc_age,xc_space,xc_ended,xc_how,xc_camera,kc_hit,kc_age,kc_stopped,hk_on,hk_t,he_age,he_healed,he_stopped,player_health,cl_age,cl_seized,cl_struck,cl_ended,cl_how\n"+standLog);
                        standLog.setLength(0);punchLogged="";rushOn=false;rushBlows=0;fingerLongest=0;fingerLogged=dummyDisabled=false;inhaleOn=false;inhaleCaught="0";inhaleGuards=0;beatOn=beatEnded=false;dummyHealth=-1;
                        hackLogged="";healLogged="";weakLogged="";pullLogged="";marksLogged=false;stopLogged="";stillLogged=false;keptLogged=0;playerHealth=-1;playerStaggered=dummyStaggeredLogged=false;arrowLogged="";playerDeadLogged=false;callLogged="";lastH=-1;zaimHeld=false;zaimSword=-1;daimHeld=false;paimPoint=null;camKept=false;camFree=false;sweepAt=-1;danceLogged="";knifeLogged="";
                        Files.writeString(OUT.resolve(folder).resolve("camera.csv"),"t,x,y,z,yaw,pitch,distance,clear,fov,proj_x,proj_y,view_11,view_12,view_21,viewport_w,viewport_h,main_bound\n"+cameraLog);cameraLog.setLength(0);}
                    if(AUDIO){Files.writeString(OUT.resolve(folder).resolve("sounds.csv"),"clock,nanos,sound\n"+soundRows);Files.writeString(OUT.resolve(folder).resolve("ticks.csv"),"clock,nanos\nmarker,"+markerNanos+"\n"+tickRows);}
                    if(swingRows.length()>0){Files.writeString(OUT.resolve(folder).resolve("isosang.csv"),"combo,start\n"+swingRows);swingRows.setLength(0);}
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
