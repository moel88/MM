package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.mixin.client.SoundSystemAccess;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;

/**
 * Star Platinum's time stop as this client sees it (2.67.0, user request: "이제 H시간 정지를 만들자 시간 정지 시간은 5초고
 * 시간 정지를 하면 당한 사람은 시전자도 멈춰보이고 전부 멈춰보이고 풀려야 다시 보여 ... 소리 보내줄게 하나는 시전할때 소리
 * 하나는 끝날때 소리"; its rules are the server's: stand/StarPlatinumTimeStop, StarPlatinumRules). H calls it
 * (stand_timestop_v1). Every client within 128 blocks of its user hears of it (stand_timestop_event_v1) and keeps its
 * clock (the server's age of it when told and this client's ticks since, as the punches'). Its user shouts
 * "Star Platinum: The World!" (the user's first recording, a tick on), held where they stand, while their stand comes
 * round in front of them and gathers itself, then flings itself open as the shout ends; there time stops (the boom of
 * its sound, TS_WIND): a wave spreads from where its user stood over everything within TS_RANGE as the rush of its sound
 * runs (TS_SETTLE), a band of the negative at its front, and leaves the world it stopped a cold violet-blue monochrome
 * (TimeStopView). What it stopped stands still: this client neither ticks nor moves them, and draws them (and their
 * stands) as they were on the last frame before it heard (their frame's moment held). Whom it stopped see nothing move:
 * their screen holds the frame of the moment it stopped, the wave spreading over it, their look held, and they hear
 * nothing but its own sound until time moves again. Then "toki wa ugoki dasu" (the user's second recording) ends as
 * time moves (TS_RESUME_LEAD), the wave falls back to where it began and the colour returns (and all that befell the
 * stopped comes to them at once: the server's to deal).
 */
public final class StandTimeStop {
    /** Seconds from H: time stops (the boom of its sound); time moves again (unless the server says it did sooner). */
    public static final float STOP=TS_WIND/20f,MOVE=(TS_WIND+TS_LENGTH)/20f;
    /**
     * The stand's part (seconds from H): it comes round in front of its user (RISE), gathers itself (from GATHER_FROM, all
     * gathered by GATHER), flings itself open (from OPEN_FROM, wide open by OPEN, just before time stops), holds that open
     * while the wave spreads (to HOLD), settles back into its guard (SETTLE) and goes back to its place (RETURN).
     */
    static final float RISE=.18f,GATHER_FROM=.06f,GATHER=.45f,OPEN_FROM=1.58f,OPEN=STOP-.04f,HOLD=STOP+TS_SETTLE,SETTLE=.5f,RETURN=.4f;
    /** Where it stands in front of its user (blocks ahead) and how much higher than at rest. */
    static final float AHEAD=1f,RAISE=.3f;
    /** How hard it trembles as it gathers itself (blocks, at the most just before it opens). */
    static final float TREMBLE=.035f;
    /** The wave: seconds it takes to spread over the stopped world (the rush of its sound), and to fall back as time moves. */
    static final float SPREAD=TS_SETTLE,FALL=.5f;
    /** How far the stopped world reaches (blocks from where its user stood). */
    static final float REACH=(float)TS_RANGE;

    /** A time stop as this client heard of it: its user, when H was pressed and its clock, and what the server has said since. */
    public static final class Stop {
        public final UUID user;final long start,serverAge,received;
        /** Where its user stood as time stopped, and whom it stopped (entity ids). */
        Vec3d center;boolean stopped;final Set<Integer> held=new HashSet<>();
        /** When time moved again (seconds on its clock; NaN until the server says), whether its call was cut short, when it is ready again. */
        float ended=Float.NaN;boolean cut;long ready;
        /** The harm each one it stopped was owed as time moved again (entity id, harm), as the server said. */
        final Map<Integer,Float> owed=new HashMap<>();
        /** Its two sounds, once played. */
        StandAttack.Shout voice,end;
        /** The way its user faced as they called it (its clock's 12 o'clock, StandTimeStopFx; NaN until it is drawn). */
        float clockYaw=Float.NaN;
        Stop(UUID user,long start,long serverAge,long received){this.user=user;this.start=start;this.serverAge=serverAge;this.received=received;}
        /** Whole ticks on its clock since H (this client's tick counts it in as it begins). */
        long ticksIn(){return Math.max(0,serverAge+ticks-received);}
        /** Seconds on its clock since H, this frame. */
        public float age(float delta){return (serverAge+ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        /** When time moves again (seconds on its clock): the server's word once it has come, else when it is due. */
        public float moves(){return Float.isNaN(ended)?MOVE:ended;}
        /** Whether time is stopped now (the server said so and has not said it moves again). */
        public boolean stopping(){return stopped&&Float.isNaN(ended);}
        /** Whether its user is calling it now (not cut short, time not stopped yet). */
        boolean calling(float delta){return !stopped&&Float.isNaN(ended)&&age(delta)<STOP+.5f;}
        /** For the multiplayer check (tools/kageno/mp): when H was pressed (the server's tick), its clock here in whole ticks, whether time stopped, whom it stopped, and its sounds. */
        public long start(){return start;}
        public long ticksHere(){return ticksIn();}
        public boolean timeStopped(){return stopped;}
        public float endedAt(){return ended;}
        public boolean wasCut(){return cut;}
        public Set<Integer> whom(){return held;}
        public Map<Integer,Float> owedHarm(){return owed;}
        public SoundInstance voiceSound(){return voice;}
        public SoundInstance endSound(){return end;}
    }
    /** The time stop this client knows of for this user (null if none). */
    public static Stop of(UUID user){return STOPS.get(user);}
    static final Map<UUID,Stop> STOPS=new HashMap<>();
    /** Whom the time stops hold stopped now (entity ids), and the tick's share of the frame each is held at. */
    static final Map<Integer,Float> STILL=new HashMap<>();
    static long ticks;
    private static Object world;private static boolean heldH;
    /** The tick's share the last frame was drawn at: one stopped from now on is held there. */
    private static float lastDelta=1;
    /** Whether this client's player stands stopped (its screen held, its sound hushed), and the sounds it hushed. */
    private static boolean frozen;
    private static final List<SoundInstance> HUSHED=new ArrayList<>();
    private StandTimeStop(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(StarPlatinumTimeStop.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong();boolean cut=b.readBoolean();long ready=b.readLong();
            Vec3d center=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
            int n=b.readVarInt();if(n<0||n>4096)return;
            int[] ids=new int[n];float[] harm=new float[n];
            for(int i=0;i<n;i++){ids[i]=b.readInt();harm[i]=b.readFloat();}
            if(now<start||now-start>TS_WIND+TS_LENGTH+200||!Double.isFinite(center.x+center.y+center.z))return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                if(world!=mc.world){clear();world=mc.world;}
                hear(mc,user,type,start,now,cut,ready,center,ids,harm);
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(StandTimeStop::tick);
        WorldRenderEvents.START.register(c->lastDelta=c.tickDelta());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(StandTimeStop::clear));
    }
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var s:STOPS.values()){
            if(s.voice!=null&&!s.voice.isDone())mc.getSoundManager().stop(s.voice);
            if(s.end!=null&&!s.end.isDone())mc.getSoundManager().stop(s.end);
        }
        STOPS.clear();STILL.clear();world=null;
        thaw();
    }

    // ───────────────────────────── what the server says ─────────────────────────────

    private static void hear(MinecraftClient mc,UUID user,int type,long start,long now,boolean cut,long ready,Vec3d center,int[] ids,float[] harm){
        var s=STOPS.get(user);
        if(s==null||s.start!=start){
            // (a time stop heard of first as time stops, or after: its clock from there)
            if(s!=null)quiet(mc,s);
            s=new Stop(user,start,now-start,ticks);STOPS.put(user,s);
            if(type==StarPlatinumTimeStop.CALL)call(mc,s);
        }
        s.ready=ready;
        if(type==StarPlatinumTimeStop.STOP&&!s.stopped){
            s.stopped=true;s.center=center;
            for(int id:ids)s.held.add(id);
            // whom it stopped stand still from now: as the last frame drew them
            for(int id:ids)STILL.put(id,lastDelta);
            if(mc.player!=null&&s.held.contains(mc.player.getId()))freeze(mc);
        }else if(type==StarPlatinumTimeStop.END&&Float.isNaN(s.ended)){
            s.ended=Math.max(0,now-start)/20f;s.cut=cut;
            for(int i=0;i<ids.length;i++)s.owed.put(ids[i],harm[i]);
            for(int id:s.held)STILL.remove(id);
            // cut short as its user called it: the shout dies away; time moved early (its user gone): so does its end
            if(cut&&s.voice!=null)s.voice.fade();
            if(!cut&&s.end!=null&&s.ended<MOVE-.1f)s.end.fade();
            if(frozen&&mc.player!=null&&s.held.contains(mc.player.getId()))thaw();
        }
    }
    /** It is called: its shout, a tick on (none if heard too late to come on its beat). */
    private static void call(MinecraftClient mc,Stop s){
        if(s.serverAge>TS_SOUND+2)return;
        float volume=volume(mc,s);if(volume<.01f)return;
        s.voice=new StandAttack.Shout(CelestialFall.id("star_platinum_timestop"),volume);
        int delay=(int)(TS_SOUND-s.serverAge);
        if(delay>0)mc.getSoundManager().play(s.voice,delay);else mc.getSoundManager().play(s.voice);
    }
    /**
     * How loud its sounds are for this client: in full for its user and anyone within the reach of the stopped world,
     * fading out to nothing as far again beyond it.
     */
    private static float volume(MinecraftClient mc,Stop s){
        if(mc.player==null)return 0;
        if(mc.player.getUuid().equals(s.user))return (float)CelestialFall.CONFIG.effectVolume;
        Vec3d from=s.center;
        if(from==null){var u=mc.world==null?null:mc.world.getPlayerByUuid(s.user);if(u==null)return 0;from=u.getPos();}
        double far=from.distanceTo(mc.player.getPos());
        return (float)(CelestialFall.CONFIG.effectVolume*MathHelper.clamp(1-(far-REACH)/REACH,0,1));
    }
    private static void quiet(MinecraftClient mc,Stop s){
        if(s.voice!=null)s.voice.fade();
        if(s.end!=null)s.end.fade();
        for(int id:s.held)STILL.remove(id);
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!STOPS.isEmpty()||world!=null||frozen)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        for(var s:STOPS.values()){
            // "toki wa ugoki dasu": it ends as time moves again
            if(s.stopping()&&s.end==null&&s.ticksIn()>=TS_WIND+TS_LENGTH-TS_RESUME_LEAD&&s.ticksIn()<=TS_WIND+TS_LENGTH-TS_RESUME_LEAD+2){
                float volume=volume(mc,s);
                if(volume>.01f){s.end=new StandAttack.Shout(CelestialFall.id("star_platinum_timestop_end"),volume);mc.getSoundManager().play(s.end);}
            }
            // (the server's word that time moves again lost: it moves anyway, a little after it was due)
            if(s.stopping()&&s.age(0)>MOVE+2){s.ended=s.age(0);for(int id:s.held)STILL.remove(id);if(frozen&&mc.player!=null&&s.held.contains(mc.player.getId()))thaw();}
        }
        // (one that is over is kept until it is ready again: the cooldown list reads that from it, ready(); dropped 3 s
        // after time moved, its box went with it, 57 s early, as the multiplayer check found)
        long time=mc.world.getTime();
        STOPS.values().removeIf(s->(Float.isNaN(s.ended)&&!s.stopped&&s.age(0)>STOP+3||!Float.isNaN(s.ended)&&s.age(0)>s.ended+3)&&s.ready<=time);
        // still hushed: every new sound but its own is kept out (TimeStopSoundMixin); one let back in by the game's own
        // pause menu is hushed again
        if(frozen)hush(mc);
    }

    // ───────────────────────────── whom it stopped ─────────────────────────────

    /** This client's player is stopped: their screen holds this frame (TimeStopView), their look stays, their sound hushes. */
    private static void freeze(MinecraftClient mc){
        if(frozen)return;
        frozen=true;TimeStopView.holdStill();hush(mc);
    }
    /** Time moves again for this client's player: their screen goes on, the hushed sounds go on where they stopped. */
    private static void thaw(){
        if(!frozen)return;
        frozen=false;TimeStopView.letGo();
        var mc=MinecraftClient.getInstance();
        var sources=((SoundSystemAccess)((dev.sangwoo.celestialfall.mixin.client.SoundManagerAccess)mc.getSoundManager()).botand$system()).botand$sources();
        for(var sound:HUSHED){var source=sources.get(sound);if(source!=null)source.run(net.minecraft.client.sound.Source::resume);}
        HUSHED.clear();
    }
    /** Pauses every sound playing but the time stop's own (and keeps them, to go on with as time moves again). */
    private static void hush(MinecraftClient mc){
        var sources=((SoundSystemAccess)((dev.sangwoo.celestialfall.mixin.client.SoundManagerAccess)mc.getSoundManager()).botand$system()).botand$sources();
        for(var e:sources.entrySet()){
            var sound=e.getKey();if(own(sound))continue;
            e.getValue().run(net.minecraft.client.sound.Source::pause);
            if(!HUSHED.contains(sound))HUSHED.add(sound);
        }
    }
    /** Whether this is one of the time stops' own sounds (the only ones whom it stopped hear). */
    static boolean own(SoundInstance sound){
        for(var s:STOPS.values())if(sound==s.voice||sound==s.end)return true;
        return false;
    }
    /** Whether {@code sound} is kept out: this client's player stands stopped and it is not the time stop's own. */
    public static boolean hushed(SoundInstance sound){return frozen&&!own(sound);}
    /** Whether this client's player stands stopped now: their screen held still (TimeStopView), their look and keys held. */
    public static boolean stillView(){return frozen;}
    /**
     * Whether {@code e} is held where it stands by a time stop, as this client sees it (the client's half of
     * KagenoManager.frozen): its user calling it, or stopped in it.
     */
    public static boolean holds(LivingEntity e){
        if(STILL.containsKey(e.getId()))return true;
        var s=STOPS.get(e.getUuid());return s!=null&&s.calling(0);
    }
    /**
     * The tick's share of the frame to draw {@code e} at while it stands stopped (NaN if it does not): it is drawn as on
     * the last frame before this client heard it was. This client's own player is not (their screen is held instead).
     */
    public static float stillDelta(Entity e){
        if(STILL.isEmpty())return Float.NaN;
        var mc=MinecraftClient.getInstance();if(e==mc.player)return Float.NaN;
        Float d=STILL.get(e.getId());return d==null?Float.NaN:d;
    }
    /**
     * Whether {@code e} stands stopped, as this client sees it: a player's stand stands still with them, and a body holds
     * the pose it was drawn in (MotionPlayerModel, TrainingDummyModel).
     */
    public static boolean stopped(Entity e){return STILL.containsKey(e.getId());}
    /**
     * For the capture tools' logs: the newest time stop this client knows of, as "age,stopped,ended,cut,still,radius,
     * outer,flash,negative,shell,crack,impact" (its age in seconds, whether time is stopped, when it moved again or was
     * cut short (seconds, -1 if not yet), whether this client's screen is held still, and its wave for a camera at
     * {@code eye}), or null.
     */
    public static String describe(float delta,Vec3d eye){
        Stop s=null;for(var x:STOPS.values())if(s==null||x.start>s.start)s=x;
        if(s==null)return null;
        var w=s.cut?null:wave(s,feet(MinecraftClient.getInstance(),s,delta),s.age(delta),eye);
        return String.format(java.util.Locale.ROOT,"%.3f,%d,%.3f,%d,%d,%.2f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",s.age(delta),s.stopping()?1:0,Float.isNaN(s.ended)?-1:s.ended,s.cut?1:0,frozen?1:0,
            w==null?0:w.radius(),w==null?0:w.outer(),w==null?0:w.flash(),w==null?0:w.negative(),w==null?0:w.shell(),w==null?0:w.crack(),w==null?0:w.impact());
    }
    /** The tick this player's time stop is ready again (0 if this client has not seen them use it). */
    public static long ready(UUID id){var s=STOPS.get(id);return s==null||s.calling(0)||s.stopping()?0:s.ready;}

    // ───────────────────────────── H ─────────────────────────────

    /** H for Star Platinum's user: calls the time stop (the server decides whether it may). */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(key!=GLFW.GLFW_KEY_H||window!=mc.getWindow().getHandle())return false;
        if(action==GLFW.GLFW_RELEASE){if(!heldH)return false;heldH=false;return true;}
        if(heldH)return true;
        if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.starPlatinum(mc.player))return false;
        heldH=true;
        if(StandAttack.mine(mc)&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(StarPlatinumTimeStop.INPUT)){
            BotandCamera.prepareInteraction();
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
            ClientPlayNetworking.send(StarPlatinumTimeStop.INPUT,PacketByteBufs.empty());
        }
        return true;
    }

    // ───────────────────────────── the stand's part ─────────────────────────────

    private static final StandPunch.Shape CALL_SHAPE=new StandPunch.Shape();
    /**
     * The stand's shape this frame through its user's punches, rush, inhale and time stop together, {@code back} seconds
     * before this frame (null if none shows): the newest leads, taking over from the others (StandRush.combine).
     */
    public static StandPunch.Shape shape(PlayerEntity user,float delta,float back,StandPunch.Shape into){
        var base=StandInhale.shape(user,delta,back,into);
        var s=STOPS.get(user.getUuid());if(s==null)return base;
        var mine=call(s,MathHelper.lerpAngleDegrees(delta,user.prevHeadYaw,user.headYaw),delta,back,CALL_SHAPE);if(mine==null)return base;
        float baseAge=StandRush.newest(user.getUuid(),delta,back);
        var inhale=StandInhale.of(user.getUuid());if(inhale!=null)baseAge=Math.min(baseAge,inhale.age(delta)-back);
        return StandRush.combine(base,baseAge,mine,s.age(delta)-back,into);
    }
    /**
     * The stand's shape through time stop {@code s}, {@code back} seconds before this frame (null once its part is
     * over): its stances, how far it has come round in front, the way it faces ({@code yaw}, its user's), how it trembles
     * as it gathers itself, the gale of its opening and its afterimages.
     */
    static StandPunch.Shape call(Stop s,float yaw,float delta,float back,StandPunch.Shape into){
        float t=s.age(delta)-back;if(t<0)return null;
        // cut short as it was called: it lets go there, back to its guard and its place
        float end=s.cut&&!Float.isNaN(s.ended)?Math.min(s.ended,HOLD):HOLD;
        if(t>end+SETTLE+RETURN)return null;
        into.clear();
        float at=Math.min(t,end);
        stances(at,into.weights);
        if(t>end){
            // over: it turns from where it was back to its guard (turned, not weighed in)
            System.arraycopy(into.weights,0,into.under,0,into.under.length);java.util.Arrays.fill(into.weights,0);
            into.weights[GUARD]=1;into.over=StandPunch.smooth((t-end)/SETTLE);
        }
        into.engage=StandPunch.burst(t/RISE)*(1-StandPunch.smooth((t-end-SETTLE)/RETURN));
        into.yaw=yaw;
        into.ahead=AHEAD;into.side=0;into.raise=RAISE;
        // it trembles with the power it gathers, the more the nearer it is to opening
        float shake=TREMBLE*StandPunch.smooth((at-GATHER_FROM)/(OPEN_FROM-GATHER_FROM))*(1-StandPunch.smooth((at-OPEN_FROM)/(OPEN-OPEN_FROM)));
        into.lunge=shake*(float)(Math.sin(t*45.9)*.6+Math.sin(t*25.8+1.3)*.4);
        // a breeze as it gathers itself, a gale as it opens, dying away as the wave spreads
        float open=StandPunch.smooth((at-OPEN_FROM)/(OPEN-OPEN_FROM));
        into.gust=(.45f+1.9f*open*(1-StandPunch.smooth((t-STOP)/SPREAD)))*(t>end?1-StandPunch.smooth((t-end)/SETTLE):1);
        // afterimages as it flings itself open
        into.ghost=.55f*(float)Math.sin(Math.PI*MathHelper.clamp((t-OPEN_FROM)/(STOP+.12f-OPEN_FROM),0,1));
        return into;
    }
    /** Its stances {@code t} seconds into its part: from its guard it gathers itself, then flings itself open (faster and faster, as a blow). */
    static void stances(float t,float[] w){
        java.util.Arrays.fill(w,0);
        float g=StandPunch.smooth((t-GATHER_FROM)/(GATHER-GATHER_FROM));
        float o=(float)Math.pow(MathHelper.clamp((t-OPEN_FROM)/(OPEN-OPEN_FROM),0,1),1.6);
        w[GUARD]=1-g;w[WORLD_GATHER]=g*(1-o);w[WORLD]=o;
    }
    /**
     * How brightly this player's stand glows over its usual light as time stops (0 to 1: the aura flares as it opens and
     * dies down as the wave spreads), 0 if it is not stopping time.
     */
    public static float flare(UUID owner,float delta){
        var s=STOPS.get(owner);if(s==null||s.cut)return 0;
        float t=s.age(delta);
        return StandPunch.smooth((t-OPEN_FROM)/(STOP-OPEN_FROM))*(1-StandPunch.smooth((t-STOP)/(SPREAD+.3f)));
    }

    // ───────────────────────────── the wave ─────────────────────────────

    /** Seconds before time stops that space is drawn in to its user (a bubble closing on them), and how far out it starts (blocks). */
    static final float IMPLODE=.32f,IMPLODE_R=7f;
    /** How long the stopped world's cracks spread before it breaks as time moves (seconds: "toki wa ugoki dasu" until it ends). */
    static final float CRACK=TS_RESUME_LEAD/20f;
    /** How far the world seen through the bubble of the stopped world takes on its look (0 to 1). */
    static final float GLASS=.55f;
    /** How brightly the bubble's surface shows while time is stopped (it shows in full as the wave spreads). */
    static final float SHELL=.6f;

    /**
     * The wave and the stopped world this frame, for a camera at {@code eye} (TimeStopView, shaders/timestop): the time
     * stop it shows (the nearest that shows), where its middle is, how far the wave has spread (the radius of the bubble
     * of the stopped world), the negative behind its front (how deep, which way, how strong), the glow of its front on
     * the world, how far the world in it and the world seen through it are graded, the flash, and since 2.68.0 how
     * brightly the bubble's surface shows, its cracks as time is about to move, the impact frame as time stops, how much
     * the world bends at its front (out as it spreads, in as it closes), its clock (seconds) and the vignette of an eye in
     * it (null if none shows).
     */
    public record Wave(Vec3d center,float radius,float band,float side,float negative,float rim,float grade,float outer,float flash,
                       float shell,float crack,float impact,float refract,float time,float vignette){
        /** The same wave with this impact frame (TimeStopView draws the impact over the finished frame, the rest under the effects). */
        public Wave impact(float to){return new Wave(center,radius,band,side,negative,rim,grade,outer,flash,shell,crack,to,refract,time,vignette);}
        /** The impact frame alone: nothing of the wave but it. */
        public Wave impactOnly(){return new Wave(center,0,1,1,0,0,0,0,0,0,0,impact,0,time,0);}
    }
    public static Wave wave(float delta,Vec3d eye){
        Wave best=null;double near=Double.MAX_VALUE;
        var mc=MinecraftClient.getInstance();
        for(var s:STOPS.values()){
            if(s.cut)continue;
            Vec3d feet=feet(mc,s,delta);
            var w=wave(s,feet,s.age(delta),eye);if(w==null)continue;
            double d=w.center.squaredDistanceTo(eye);
            if(d<near){near=d;best=w;}
        }
        return best;
    }
    /**
     * Where time stop {@code s} began: where its user stood as time stopped (the server's word), or before it has
     * stopped, where its user stands now (held there as they call it; null if this client does not see them).
     */
    static Vec3d feet(MinecraftClient mc,Stop s,float delta){
        if(s.center!=null)return s.center;
        if(mc==null||mc.world==null)return null;
        var user=mc.world.getPlayerByUuid(s.user);return user==null?null:user.getLerpedPos(delta);
    }
    /**
     * Time stop {@code s}'s wave {@code t} seconds on its clock, its middle over {@code feet}, for a camera at {@code eye}
     * (null if none shows). Before time stops (2.68.0) space is drawn in to its user: a bubble closes on them from
     * IMPLODE_R blocks, the world bending in at its front, its inside turning negative. As time stops, an impact frame
     * (the world in negative, its shapes dark on light) for a few frames, and the wave spreads from its user fast and then
     * slower, the bubble's surface bright, the whole of the world it has passed over negative at first, the negative
     * narrowing to its front, the world bending out at its front. Stopped: the stopped world, the bubble's surface faint
     * and the world seen through it half graded. As "toki wa ugoki dasu" runs, cracks spread over the bubble; as time
     * moves it breaks (its shards, StandTimeStopFx) and the colour returns from its edge in, the negative falling back
     * with it.
     */
    static Wave wave(Stop s,Vec3d feet,float t,Vec3d eye){
        if(feet==null)return null;
        Vec3d c=feet.add(0,1.1,0);
        double far=eye.distanceTo(c);boolean in=far<REACH;
        // (the impact frame and the flash for an eye out of the stopped world: weaker, none from three times its reach)
        float outside=in?1:.6f*(float)MathHelper.clamp(1-(far-REACH)/(2*REACH),0,1);
        float e=t-STOP,moves=s.moves();
        if(e<-IMPLODE)return null;
        if(!s.stopped||e<0){
            // space drawn in to its user (or, the server's word that time stopped not here yet, held closed)
            if(!s.stopped&&e>.6f||!Float.isNaN(s.ended))return null;
            float u=Math.min(1,(e+IMPLODE)/IMPLODE);
            float r=IMPLODE_R*(1-(float)Math.pow(u,1.7))+.05f;
            float on=StandPunch.smooth(u/.3f);
            // (its front's glow and its surface go out as it closes on its user: it would light their body up)
            float close=1-StandPunch.smooth((u-.7f)/.3f);
            return new Wave(c,r,2.5f,1,.85f*on,.9f*on*close,0,0,0,.6f*on*close,0,0,-.9f*on*close,t,in?.25f*u:0);
        }
        if(t>=moves){
            // time moves again: the bubble breaks and the colour returns from its edge in, the negative falling back
            float u=(t-moves)/FALL;if(u>=1)return null;
            float r=REACH*(1-(float)Math.pow(u,1.5));
            // (the negative and the flash come in over a couple of frames, as every flash does; the bubble's surface
            // goes out over five, as its shards fly)
            float in2=StandPunch.smooth(u/.15f),broke=1-StandPunch.smooth(u/.18f);
            return new Wave(c,r,6,-1,.8f*(1-u)*in2,.22f+(1.1f*(1-u*u)-.22f)*in2,1,GLASS*(1-StandPunch.smooth(u/.35f)),.3f*in2*(1-StandPunch.smooth(u/.3f)),
                (SHELL+.35f)*broke,broke,.55f*in2*(1-StandPunch.smooth((u-.1f)/.25f))*outside,-.7f*(1-u)*in2,t,in?.35f*(1-StandPunch.smooth(u/.6f)):0);
        }
        if(e<SPREAD){
            // time stops: the wave spreads from its user, fast and then slower; the world it has passed over negative
            // at first, the negative narrowing to its front; the world seen through the bubble taking on its look as
            // it reaches its edge; a flash and an impact frame as it starts
            float u=e/SPREAD;
            float r=REACH*(1-(float)Math.pow(1-u,2.4));
            float flash=.35f*StandPunch.smooth(e/.05f)*(1-StandPunch.smooth(e/.22f))*(in?1:.65f*outside);
            float impact=StandPunch.smooth(e/.035f)*(1-StandPunch.smooth((e-.05f)/.09f))*outside;
            // (its front's glow comes in as it leaves its user: at first the front is on them, and would light their
            // whole body up for a frame)
            float rim=(1.3f*(1-StandPunch.smooth((u-.75f)/.25f))+.22f)*StandPunch.smooth(e/.1f);
            float band=Math.max(1.5f,r*(.95f-.75f*u));
            // (the bubble comes in over five frames as it leaves its user)
            float shell=(SHELL+(1-SHELL)*(1-StandPunch.smooth((u-.6f)/.4f)))*StandPunch.smooth(e/.09f);
            float refract=StandPunch.smooth(e/.08f)*(1-StandPunch.smooth((u-.3f)/.6f))+.15f*StandPunch.smooth((u-.3f)/.6f);
            return new Wave(c,r,band,1,1-StandPunch.smooth((u-.55f)/.45f),rim,1,GLASS*StandPunch.smooth((u-.6f)/.4f),flash,
                shell,0,impact,refract,t,in?.25f+.1f*StandPunch.smooth(u/.5f):0);
        }
        // stopped: the stopped world, the bubble's surface faint, the world seen through it half graded; its cracks
        // spreading as time is about to move
        float lead=moves-t,crack=lead<CRACK?(float)Math.pow(1-lead/CRACK,1.5):0;
        return new Wave(c,REACH,0,1,0,.22f+.3f*crack,1,GLASS,0,SHELL+.35f*crack,crack,0,.15f,t,in?.35f:0);
    }
}
