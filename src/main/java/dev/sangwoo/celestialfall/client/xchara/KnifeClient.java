package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.xchara.XCharaAttack;
import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * X Chara's basic attack as this client knows it (2.114.0; the server's XCharaAttack, XCharaCombo for its timing). The
 * attack key on the map is X Chara's player's knife: it asks the server for a hit ({@link #punch}; held down it keeps
 * asking, the server keeps a press made a little early for the next hit). The server tells when a hit starts, whom each
 * of its blows struck, when the attack rests and when a hit stops short; each hit is a swing ({@link #SWINGS}, a cast of
 * its own skill drawn with the effects: KnifeCombo through KagenoRenderer) whose clock (the server's age as it was told,
 * this client's ticks since and the frame's part of a tick) drives the body's motion ({@link #animated}, KagenoClips) and
 * everything drawn. A swing stopped short lets go of the body at once and draws nothing of the blows it never came to
 * ({@link #until}).
 * <p>
 * Its sound is the user's own (2.118.0: "이걸 보탠드 엑스 차라 평타 소리로 옮겨 Q활성화 비활성화 전부 똑같게"; taken out of
 * their video from under its music, tools/xchara/extract_slash.py): one sound, xchara_slash, for every swing with the
 * hack on or off, a hiss as the knife cuts and a tail falling after it (their "피슝"). It starts SOUND_BEFORE ticks
 * before the swing's blow, as the knife is let go, so the hiss is at its fullest as the blow lands and the tail falls
 * through the follow-through; a blow that lands on someone has no sound of its own (the video's has none). Loud for
 * its user, fading out to CARRY blocks for everyone else; a hit stopped short fades out over a few ticks. (Until
 * 2.118.0 the sounds were made up: each hit's own and a blow's as it landed.)
 */
public final class KnifeClient {
    /** Every hit this client knows of, by its id: a cast of the knife's skill (XCharaCombo.SKILL), its combo the hit's number. */
    public static final Map<UUID,StandClient.Cast> SWINGS=new LinkedHashMap<>();
    /** Whom each blow of each swing struck, by the server's word (the entities' ids; null until it is told). */
    private static final Map<UUID,int[][]> STRUCK=new HashMap<>();
    /** The swings that stopped short, and how far into each (ticks) it did. */
    private static final Map<UUID,Float> STOPPED=new HashMap<>();
    /** When each player's attack is ready again after its rest (the server's world time), as told. */
    private static final Map<UUID,Long> READY=new HashMap<>();
    /** (2.121.0) When each player's cleave (R with the hack on) is ready again, as told. */
    private static final Map<UUID,Long> CLEAVE_READY=new HashMap<>();
    /** (2.124.0) When each player's jab (R with the hack off) is ready again, as told. */
    private static final Map<UUID,Long> JAB_READY=new HashMap<>();
    /** (2.128.0) When each player's slam (G with the hack off) is ready again, as told. */
    private static final Map<UUID,Long> SLAM_READY=new HashMap<>();
    /**
     * (2.128.0) A slam as this client was told of it: how high its user rises over where they stood (blocks), where it
     * comes down (null until its dive begins) and how far into it (ticks, on its cast's clock) this client heard that.
     */
    public static final class Slam {
        public final float rise;public Vec3d land;public float heard=Float.NaN;
        /** (2.128.4) How far forward of where they stood the top of its rise is ({x,z}). */
        public final float aheadX,aheadZ;
        Slam(float rise,float aheadX,float aheadZ){this.rise=rise;this.aheadX=aheadX;this.aheadZ=aheadZ;}
    }
    private static final Map<UUID,Slam> SLAMS=new HashMap<>();
    /** (2.129.0) When each player's three cuts (E with the hack off) are ready again, as told. */
    private static final Map<UUID,Long> CUTTER_READY=new HashMap<>();
    /** (2.129.0) Where each of the three cuts that goes forward first ends, by its id, as told as it began. */
    private static final Map<UUID,Vec3d> DASHES=new HashMap<>();
    /** Where the three cuts {@code id} go forward to (null: they cut where they stand, or none). */
    public static Vec3d dash(UUID id){return DASHES.get(id);}
    /**
     * (2.129.0) The three cuts' sound, the user's own out of their video (tools/xchara/extract_cutter.py): one sound of
     * three cuts a quarter of a second apart, started CUTTER_SOUND_BEFORE ticks before the first cut lands (its first
     * onset is that far into its file).
     */
    static final String CUTTER_SOUND="xchara_cutter";static final int CUTTER_SOUND_BEFORE=1;
    /** (2.130.0) When each player's knives shot (Y with the hack off) are ready again, as told. */
    private static final Map<UUID,Long> SHOT_READY=new HashMap<>();
    /** (2.130.0) The tick this player's knives shot are ready again, as the server told it (0 if none). */
    public static long shotReady(UUID id){return SHOT_READY.getOrDefault(id,0L);}
    /** (2.131.0) When each player's small knives (T with the hack off) are ready again, as told. */
    private static final Map<UUID,Long> KNIVES_READY=new HashMap<>();
    /** (2.131.0) The tick this player's small knives are ready again, as the server told it (0 if none). */
    public static long knivesReady(UUID id){return KNIVES_READY.getOrDefault(id,0L);}
    /** (2.132.0) When each player's rain of knives (H with the hack off) is ready again, as told. */
    private static final Map<UUID,Long> RAIN_READY=new HashMap<>();
    /** (2.132.0) The tick this player's rain of knives is ready again, as the server told it (0 if none). */
    public static long rainReady(UUID id){return RAIN_READY.getOrDefault(id,0L);}
    /** (2.132.0) The way of a run or a stab of the rain of knives as told as it began: the run's from where it began to where it ends with no one met; the stab's from where the run had its user to where they stab from. */
    public record Way(Vec3d from,Vec3d to){}
    private static final Map<UUID,Way> WAYS=new HashMap<>();
    public static Way rainWay(UUID id){return WAYS.get(id);}
    /** (2.132.0) The run each stab of the rain of knives took over from, by the stab's id (none: it began with no run). */
    private static final Map<UUID,StandClient.Cast> RAN=new HashMap<>();
    public static StandClient.Cast ranBefore(UUID id){return RAN.get(id);}
    /** (2.132.0) This player's latest run or stab of the rain of knives this client still draws (in flight or stopped short; a little past its end, while its knife goes out), or null. */
    public static StandClient.Cast rainOf(UUID id){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.caster().equals(id)&&XCharaCombo.rain(c.combo())&&c.age(0)<XCharaCombo.LENGTH[c.combo()]+10)best=c;
        return best;
    }
    /** What this client was told of slam {@code id} (null: none). */
    public static Slam slam(UUID id){return SLAMS.get(id);}
    /**
     * (2.128.0) The slam's three sounds, the user's own out of their video (tools/xchara/extract_slam.py): the knife's
     * lighting as it starts, the leap's as they leave the ground, and the landing's, each started SLAM_SOUND_BEFORE
     * ticks before its moment (its onset is that far into its file).
     */
    static final String SLAM_LIT_SOUND="xchara_slam_lit",SLAM_LEAP_SOUND="xchara_slam_leap",SLAM_LAND_SOUND="xchara_slam_land";static final int SLAM_SOUND_BEFORE=1;
    /** How loud the leap's sound is at the second spring, of what it is at the first (the video's second rush is some 6 dB under its first). */
    static final float SLAM_LEAP2_PART=.5f;
    /** The cleave's sound (its own, played as it starts). */
    static final String CLEAVE_SOUND="xchara_r";
    /**
     * (2.124.0) The jab's two sounds, the user's own out of their video (tools/xchara/extract_jab.py): the press's
     * ("띡") as it starts, and the thrust's, started JAB_SOUND_BEFORE ticks before the blow so that its onset (that far
     * into its file) is on the blow.
     */
    static final String JAB_PRESS_SOUND="xchara_jab_press",JAB_SOUND="xchara_jab";static final int JAB_SOUND_BEFORE=1;
    /** A swing told of later than this many ticks after it began is not drawn (it is nearly over). */
    static final int LATE=12;
    /** Each swing's sound as it plays (to fade it out if the swing stops short). */
    private static final Map<UUID,XCharaClient.Voice> VOICES=new HashMap<>();
    /** (2.128.0) A slam's other sounds as they play or wait to (its leap's and its landing's: faded out, or never begun, if it stops short). */
    private static final Map<UUID,List<XCharaClient.Voice>> MORE_VOICES=new HashMap<>();
    /** Every swing's sound; it starts this many ticks before the swing's blow lands; how far it carries (blocks). */
    static final String SOUND="xchara_slash";static final int SOUND_BEFORE=2;static final double CARRY=40;
    /** The tick into hit {@code combo} its sound starts on. */
    static int soundAt(int combo){return XCharaCombo.cutter(combo)?XCharaCombo.blowAt(combo,0)-CUTTER_SOUND_BEFORE:combo==XCharaCombo.JAB?XCharaCombo.blowAt(combo,0)-JAB_SOUND_BEFORE:XCharaCombo.skill(combo)?0:Math.max(0,XCharaCombo.blowAt(combo,0)-SOUND_BEFORE);}
    /** For the capture tools: the sounds this client started, newest last (each "name hit/blow, ticks into its swing"). */
    public static final List<String> HEARD=new ArrayList<>();
    static void heard(String line){HEARD.add(line);while(HEARD.size()>64)HEARD.remove(0);}
    private static int ticks,lastAsk=-100;
    private static Object world;
    private KnifeClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaAttack.EVENT,(mc,handler,b,response)->{
            UUID caster=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();UUID id=b.readUuid();int combo=b.readUnsignedByte(),blow=b.readByte();
            long start=b.readLong(),serverNow=b.readLong();Vec3d at=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();long ready=b.readLong();
            int n=b.readVarInt();if(n<0||n>512||type>XCharaAttack.AIMED||combo>XCharaCombo.LAST||!Float.isFinite(yaw))return;
            var struck=new ArrayList<StandClient.Target>();
            for(int i=0;i<n;i++)struck.add(new StandClient.Target(b.readUuid(),b.readInt(),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble())));
            // (2.121.0) when the cleave is ready again (a 2.120 server sends none)
            long cleave=b.readableBytes()>=8?b.readLong():0;
            // (2.124.0) and the jab (a 2.123 server sends none)
            long jab=b.readableBytes()>=8?b.readLong():0;
            // (2.128.0) and the slam; and of one in flight how high it rises and where it comes down (a 2.127 server sends none)
            long slam=b.readableBytes()>=8?b.readLong():0;
            float rise=b.readableBytes()>=5?b.readFloat():0;
            // (2.128.4) and how far forward the top of its rise is
            float aheadX=b.readableBytes()>=9?b.readFloat():0,aheadZ=b.readableBytes()>=5?b.readFloat():0;
            Vec3d land=b.readableBytes()>=1&&b.readBoolean()&&b.readableBytes()>=24?new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()):null;
            // (2.129.0) and the three cuts (a 2.128 server sends none); of one going forward first, where that ends is in the slam's place
            long cutter=b.readableBytes()>=8?b.readLong():0;
            // (2.130.0) and the knives shot (a 2.129 server sends none)
            long shot=b.readableBytes()>=8?b.readLong():0;
            // (2.131.0) and the small knives thrown (a 2.130 server sends none)
            long knives=b.readableBytes()>=8?b.readLong():0;
            // (2.132.0) and the rain of knives (a 2.131 server sends none); of one in flight where its way begins (where it ends is in the slam's place)
            long rain=b.readableBytes()>=8?b.readLong():0;
            Vec3d began=b.readableBytes()>=1&&b.readBoolean()&&b.readableBytes()>=24?new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()):null;
            mc.execute(()->{CLEAVE_READY.put(caster,cleave);JAB_READY.put(caster,jab);SLAM_READY.put(caster,slam);CUTTER_READY.put(caster,cutter);SHOT_READY.put(caster,shot);KNIVES_READY.put(caster,knives);RAIN_READY.put(caster,rain);});
            if(XCharaCombo.rain(combo)&&type==XCharaAttack.HIT&&land!=null&&began!=null&&finite(land)&&finite(began)&&began.squaredDistanceTo(at)<=24*24&&land.squaredDistanceTo(began)<=16*16)
                mc.execute(()->WAYS.put(id,new Way(began,land)));
            if(combo==XCharaCombo.CUTTER_DASH&&type==XCharaAttack.HIT&&land!=null&&Double.isFinite(land.x)&&Double.isFinite(land.y)&&Double.isFinite(land.z)
                &&land.squaredDistanceTo(at)<=(dev.sangwoo.celestialfall.xchara.CutterRules.DASH+4)*(dev.sangwoo.celestialfall.xchara.CutterRules.DASH+4))mc.execute(()->DASHES.put(id,land));
            if(combo==XCharaCombo.SLAM)mc.execute(()->{
                if(type==XCharaAttack.HIT&&Float.isFinite(rise)){
                    float far=(float)Math.hypot(aheadX,aheadZ),most=(float)dev.sangwoo.celestialfall.xchara.SlamRules.FORWARD,k=Float.isFinite(far)&&far>most?most/far:Float.isFinite(far)?1:0;
                    SLAMS.put(id,new Slam(Math.max(0,Math.min((float)dev.sangwoo.celestialfall.xchara.SlamRules.RISE,rise)),aheadX*k,aheadZ*k));
                }
                var info=SLAMS.get(id);var cast=SWINGS.get(id);
                if(info!=null&&info.land==null&&land!=null&&Double.isFinite(land.x)&&Double.isFinite(land.y)&&Double.isFinite(land.z)){
                    info.land=land;info.heard=cast==null?serverNow-start:cast.age(0);
                }
            });
            mc.execute(()->accept(mc,caster,dim,type,id,combo,blow,start,serverNow,at,yaw,ready,struck));
        });
        ClientTickEvents.START_CLIENT_TICK.register(KnifeClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(KnifeClient::clear));
    }
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static void accept(MinecraftClient mc,UUID caster,Identifier dim,int type,UUID id,int combo,int blow,long start,long serverNow,Vec3d at,float yaw,long ready,List<StandClient.Target> struck){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
        if(world!=mc.world){clear();world=mc.world;}
        READY.put(caster,ready);
        if(type==XCharaAttack.HIT&&XCharaCombo.valid(combo)){
            if(serverNow-start>LATE)return;
            var cast=new StandClient.Cast(id,caster,dim,XCharaCombo.SKILL,combo,start,serverNow,StandClient.ticks(),at,yaw,List.of());
            KagenoClips.begin(cast);
            // (2.132.0) the rain's stab takes over from its run: the run lets go of the body here (the stab begins from
            // the frame of it on screen, KagenoClips.begin) and draws nothing more
            if(combo==XCharaCombo.RAIN)for(var c:SWINGS.values())if(c.caster().equals(caster)&&c.combo()==XCharaCombo.RUSH&&!STOPPED.containsKey(c.id())){STOPPED.put(c.id(),Math.max(2,c.age(0)));RAN.put(id,c);}
            SWINGS.put(id,cast);STRUCK.put(id,new int[XCharaCombo.blows(combo)][]);
            voice(mc,cast);
        }else if(type==XCharaAttack.LANDED){
            var cast=SWINGS.get(id);var told=STRUCK.get(id);
            if(cast==null||told==null||blow<0||blow>=told.length)return;
            told[blow]=struck.stream().mapToInt(StandClient.Target::entity).toArray();
            // (2.124.0) the jab marks whom it struck: the X on their face while they are held
            if(cast.combo()==XCharaCombo.JAB)CorruptionJab.mark(cast,struck);
            // (2.132.0) the rain's stab is in someone: the rain's sounds, at their moments from now
            if(cast.combo()==XCharaCombo.RAIN&&blow==0&&!struck.isEmpty())rainVoices(mc,cast);
            if(!struck.isEmpty()){
                var all=new ArrayList<>(cast.targets());for(var t:struck)if(all.stream().noneMatch(o->o.entity()==t.entity()))all.add(t);
                SWINGS.put(id,new StandClient.Cast(cast.id(),cast.caster(),cast.dimension(),cast.skill(),cast.combo(),cast.start(),cast.serverNow(),cast.received(),cast.origin(),cast.yaw(),List.copyOf(all)));
            }
        }else if(type==XCharaAttack.STOPPED){
            // the swing in flight lets go of the body, and none of the blows it never came to is drawn; one stopped before
            // anything of it was to be seen is gone
            float age=Math.max(0,serverNow-start);
            for(var c:SWINGS.values())if(c.caster().equals(caster)&&!STOPPED.containsKey(c.id())&&animating(c)){
                STOPPED.put(c.id(),c.id().equals(id)?age:c.age(0));
                var v=VOICES.get(c.id());if(v!=null)v.fade();
                var more=MORE_VOICES.get(c.id());if(more!=null)for(var w:more)w.fade();
            }
            SWINGS.values().removeIf(c->STOPPED.containsKey(c.id())&&STOPPED.get(c.id())<2);
        }
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(world!=null||!SWINGS.isEmpty())clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        var dim=mc.world.getRegistryKey().getValue();
        SWINGS.values().removeIf(c->!c.dimension().equals(dim)||c.age(0)>=KnifeCombo.life(c.combo())*20||c.age(0)<0||gone(mc,c));
        STRUCK.keySet().retainAll(SWINGS.keySet());STOPPED.keySet().retainAll(SWINGS.keySet());VOICES.keySet().retainAll(SWINGS.keySet());
        SLAMS.keySet().retainAll(SWINGS.keySet());DASHES.keySet().retainAll(SWINGS.keySet());WAYS.keySet().retainAll(SWINGS.keySet());RAN.keySet().retainAll(SWINGS.keySet());MORE_VOICES.keySet().retainAll(SWINGS.keySet());BladeShot.keep(SWINGS.keySet());
        // (2.128.0) up in the air of their own slam: where the middle of the screen points, told every tick until the dive's word comes
        if(mc.player!=null)OverwriteSlam.tellAim(mc);
        // (2.130.0) and while their own knives shot are not all gone: each goes at where they look as it is let go
        if(mc.player!=null)BladeShot.tellAim(mc);
        // held down, the attack key keeps asking (the server keeps a press made a little early for the next hit)
        if(mc.player!=null&&mc.currentScreen==null&&mc.options.attackKey.isPressed()&&attacks(mc.player))punch();
    }
    /** A swing whose caster is dead or X Chara no more. */
    private static boolean gone(MinecraftClient mc,StandClient.Cast c){
        var p=mc.world.getPlayerByUuid(c.caster());return p!=null&&(!p.isAlive()||!StandManager.xChara(p));
    }
    public static void clear(){
        for(var v:VOICES.values())v.fade();
        for(var l:MORE_VOICES.values())for(var v:l)v.fade();
        SWINGS.clear();STRUCK.clear();STOPPED.clear();READY.clear();CLEAVE_READY.clear();JAB_READY.clear();SLAM_READY.clear();SLAMS.clear();CUTTER_READY.clear();SHOT_READY.clear();KNIVES_READY.clear();RAIN_READY.clear();WAYS.clear();RAN.clear();DASHES.clear();VOICES.clear();MORE_VOICES.clear();BladeShot.clear();KnifeCombo.clear();CorruptionJab.clear();OverwriteSlam.clear();world=null;
    }

    // ───────────────────────────── sounds ─────────────────────────────

    /** How loud something at {@code from} is for this client now (before the game's volume settings): fading out to CARRY blocks. */
    static float loudness(MinecraftClient mc,Vec3d from){
        if(mc.player==null)return 0;
        return (float)(dev.sangwoo.celestialfall.CelestialFall.CONFIG.effectVolume*Math.max(0,1-mc.player.getPos().distanceTo(from)/CARRY));
    }
    /** The swing's sound: SOUND_BEFORE ticks before its blow, loud for its user, for everyone else from where its user is. */
    private static void voice(MinecraftClient mc,StandClient.Cast cast){
        if(mc.world==null||mc.player==null)return;
        UUID caster=cast.caster();Vec3d origin=cast.origin();
        java.util.function.Supplier<Float> loud=caster.equals(mc.player.getUuid())?()->(float)dev.sangwoo.celestialfall.CelestialFall.CONFIG.effectVolume:()->{
            var m=MinecraftClient.getInstance();var user=m.world==null?null:m.world.getPlayerByUuid(caster);
            return loudness(m,user==null?origin:user.getPos());
        };
        boolean jab=cast.combo()==XCharaCombo.JAB;
        if(cast.combo()==XCharaCombo.SLAM){
            // (2.128.0) the slam: the knife's lighting now, the leap's and the landing's at their moments (one told of
            // after a moment has none of that moment's sound)
            int age=(int)Math.floor(cast.age(0));var later=new ArrayList<XCharaClient.Voice>();
            // (2.128.3: the rise is in two; the leap's sound again at the second spring, at SLAM_LEAP2_PART of it: the
            // video has a quieter rush there)
            Object[][] cues={{SLAM_LIT_SOUND,0,1f},{SLAM_LEAP_SOUND,dev.sangwoo.celestialfall.xchara.SlamRules.LEAP-SLAM_SOUND_BEFORE,1f},
                {SLAM_LEAP_SOUND,dev.sangwoo.celestialfall.xchara.SlamRules.LEAP2-SLAM_SOUND_BEFORE,SLAM_LEAP2_PART},{SLAM_LAND_SOUND,dev.sangwoo.celestialfall.xchara.SlamRules.LAND-SLAM_SOUND_BEFORE,1f}};
            for(var cue:cues){
                int wait=(Integer)cue[1]-age;if(wait<-2)continue;
                var v=XCharaClient.Voice.quieter(dev.sangwoo.celestialfall.CelestialFall.id((String)cue[0]),loud,(Float)cue[2]);
                if(wait>0)mc.getSoundManager().play(v,wait);else mc.getSoundManager().play(v);
                later.add(v);
                heard(String.format(java.util.Locale.ROOT,"%s hit %d, told %.0f ticks in, played %d later at %.2f",cue[0],cast.combo(),cast.age(0),Math.max(0,wait),v.full()));
            }
            MORE_VOICES.put(cast.id(),later);
            return;
        }
        if(cast.combo()==XCharaCombo.SHOT){
            // (2.130.0) the knives shot: each knife's coming to be has the "!!!"'s own sound (the user's video: the very
            // sound), at its moment (one told of after a moment has none of that moment's sound); a knife's strike is
            // BladeShot's, as it lands
            int age=(int)Math.floor(cast.age(0));var later=new ArrayList<XCharaClient.Voice>();
            for(int k=0;k<dev.sangwoo.celestialfall.xchara.ShotRules.BLADES;k++){
                int wait=dev.sangwoo.celestialfall.xchara.ShotRules.APPEAR[k]-age;if(wait<-2)continue;
                var v=XCharaClient.Voice.quieter(dev.sangwoo.celestialfall.CelestialFall.id(BladeShot.SOUND),loud,BladeShot.SOUND_PART);
                if(wait>0)mc.getSoundManager().play(v,wait);else mc.getSoundManager().play(v);
                later.add(v);
                heard(String.format(java.util.Locale.ROOT,"%s knife %d, told %.0f ticks in, played %d later at %.2f",BladeShot.SOUND,k,cast.age(0),Math.max(0,wait),v.full()));
            }
            MORE_VOICES.put(cast.id(),later);
            return;
        }
        // (2.132.0) the rain of knives: its run has no sound; its stab has the jab's thrust as the knife goes in (the
        // rain's own come once it is in someone: rainVoices)
        if(cast.combo()==XCharaCombo.RUSH)return;
        if(cast.combo()==XCharaCombo.RAIN){
            int wait=dev.sangwoo.celestialfall.xchara.RainRules.STAB-1-(int)Math.floor(cast.age(0));if(wait<-2)return;
            var v=new XCharaClient.Voice(dev.sangwoo.celestialfall.CelestialFall.id(BladeRain.STAB_SOUND),loud);
            if(v.full()<=.01f)return;
            if(wait>0)mc.getSoundManager().play(v,wait);else mc.getSoundManager().play(v);
            VOICES.put(cast.id(),v);
            heard(String.format(java.util.Locale.ROOT,"%s hit %d, told %.0f ticks in, played %d later at %.2f",BladeRain.STAB_SOUND,cast.combo(),cast.age(0),Math.max(0,wait),v.full()));
            return;
        }
        String sound=cast.combo()==XCharaCombo.KNIVES?CorruptionKnives.SOUND:XCharaCombo.cutter(cast.combo())?CUTTER_SOUND:jab?JAB_SOUND:XCharaCombo.skill(cast.combo())?CLEAVE_SOUND:SOUND;
        var v=new XCharaClient.Voice(dev.sangwoo.celestialfall.CelestialFall.id(sound),loud);
        if(v.full()<=.01f)return;
        // (2.124.0) the jab's press, as it starts (told of late, not at all: it is over)
        if(jab&&cast.age(0)<3){
            mc.getSoundManager().play(new XCharaClient.Voice(dev.sangwoo.celestialfall.CelestialFall.id(JAB_PRESS_SOUND),loud));
            heard(String.format(java.util.Locale.ROOT,"%s hit %d, told %.0f ticks in, played 0 later at %.2f",JAB_PRESS_SOUND,cast.combo(),cast.age(0),v.full()));
        }
        // told of after it was to start: at once (a tick late is not heard as late; later, LATE has dropped the swing)
        int wait=soundAt(cast.combo())-(int)Math.floor(cast.age(0));
        if(wait>0)mc.getSoundManager().play(v,wait);else mc.getSoundManager().play(v);
        VOICES.put(cast.id(),v);
        heard(String.format(java.util.Locale.ROOT,"%s hit %d, told %.0f ticks in, played %d later at %.2f",sound,cast.combo(),cast.age(0),Math.max(0,wait),v.full()));
    }
    /** (2.132.0) The rain's sounds (BladeRain.cues), each at its moment from now: for its user as loud as they are, for everyone else from where its user is. One whose moment is past is not played. */
    private static void rainVoices(MinecraftClient mc,StandClient.Cast cast){
        if(mc.world==null||mc.player==null)return;
        UUID caster=cast.caster();Vec3d origin=cast.origin();
        java.util.function.Supplier<Float> loud=caster.equals(mc.player.getUuid())?()->(float)dev.sangwoo.celestialfall.CelestialFall.CONFIG.effectVolume:()->{
            var m=MinecraftClient.getInstance();var user=m.world==null?null:m.world.getPlayerByUuid(caster);
            return loudness(m,user==null?origin:user.getPos());
        };
        int age=(int)Math.floor(cast.age(0));var later=MORE_VOICES.computeIfAbsent(cast.id(),x->new ArrayList<>());
        for(var cue:BladeRain.cues()){
            int wait=(Integer)cue[1]-age;if(wait<-1)continue;
            var v=XCharaClient.Voice.quieter(dev.sangwoo.celestialfall.CelestialFall.id((String)cue[0]),loud,(Float)cue[2]);
            if(v.full()<=.005f)continue;
            if(wait>0)mc.getSoundManager().play(v,wait);else mc.getSoundManager().play(v);
            later.add(v);
            heard(String.format(java.util.Locale.ROOT,"%s rain, told %.0f ticks in, played %d later at %.2f",cue[0],cast.age(0),Math.max(0,wait),v.full()));
        }
    }
    /** Whether this swing's body clip still plays (it is the body's motion until then, unless it stopped short). */
    static boolean animating(StandClient.Cast c){return c.age(0)<KnifeCombo.clipOf(c.combo()).length()*20+1;}
    /** The latest swing of this player whose body clip still plays and did not stop short (KagenoClips.animated), or null. */
    public static StandClient.Cast animated(UUID id){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.caster().equals(id)&&!STOPPED.containsKey(c.id())&&animating(c))best=c;
        return best;
    }
    /** How far into swing {@code id} (seconds) its blows are drawn: all of it, or until it stopped short. */
    public static float until(UUID id){var s=STOPPED.get(id);return s==null?Float.MAX_VALUE:s/20f;}
    /** Whether swing {@code id} came to its blow {@code i}: it did not stop short before it. */
    static boolean cameTo(StandClient.Cast c,int i){return XCharaCombo.blowAt(c.combo(),i)/20f<=until(c.id())+1e-4f;}
    /** Whom blow {@code i} of swing {@code id} struck, by the server's word (null until it is told). */
    static int[] struck(UUID id,int i){var t=STRUCK.get(id);return t==null||i<0||i>=t.length?null:t[i];}
    /**
     * Whom blow {@code i} of swing {@code c} struck, for the effects: the server's word once it is told; until then
     * (a tick or two) whoever stands in the blow's way on this client, as the server finds them (XCharaCombo.lane; one
     * with a guard up is struck by none of the first three hits).
     */
    static List<LivingEntity> struckBy(MinecraftClient mc,StandClient.Cast c,int i){
        var out=new ArrayList<LivingEntity>();if(mc.world==null)return out;
        int[] told=struck(c.id(),i);
        if(told!=null){for(int id:told)if(mc.world.getEntityById(id) instanceof LivingEntity e)out.add(e);return out;}
        var user=mc.world.getPlayerByUuid(c.caster());Vec3d at=user==null?c.origin():user.getPos();
        // (2.128.0) the slam strikes round where it comes down, not ahead of where its user stands
        if(c.combo()==XCharaCombo.SLAM){var info=SLAMS.get(c.id());if(info==null||info.land==null)return out;at=info.land;}
        // (2.129.0) the three cuts after a going forward are struck from where that ended (their user is drawn there, and is there a tick or so on)
        if(c.combo()==XCharaCombo.CUTTER_DASH){Vec3d to=DASHES.get(c.id());if(to!=null)at=to;}
        Vec3d f=Vec3d.fromPolar(0,c.yaw()),right=new Vec3d(-f.z,0,f.x);
        for(var e:mc.world.getEntitiesByClass(LivingEntity.class,new net.minecraft.util.math.Box(at,at).expand(XCharaCombo.MAX_REACH+3),
            e->e!=user&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            // (one holding a guard up takes the first three hits on it: struck by none of them)
            if(!XCharaCombo.unblockable(c.combo())&&guarding(e,mc.world.getTime()))continue;
            if(XCharaCombo.lane(c.combo(),i,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5))out.add(e);
        }
        return out;
    }
    /** Whether this one holds a guard up now, as this client knows it (a player's combat state, a training dummy's). */
    private static boolean guarding(net.minecraft.entity.Entity e,long now){
        if(e instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity d)return d.guarding();
        if(e instanceof PlayerEntity p){var state=CombatClient.snapshot(p.getUuid());return state.guard()&&!state.broken(now);}
        return false;
    }
    /** The tick this player's attack is ready again after its rest, as the server told it (0 if none). */
    public static long ready(UUID id){return READY.getOrDefault(id,0L);}
    /** (2.121.0) The tick this player's cleave is ready again, as the server told it (0 if none). */
    public static long cleaveReady(UUID id){return CLEAVE_READY.getOrDefault(id,0L);}
    /** (2.121.0) This player's latest cleave this client still draws (in flight or stopped short), or null: GiantCleave's knife's size, press and panel. */
    public static StandClient.Cast cleaveOf(UUID id){return skillOf(id,XCharaCombo.CLEAVE);}
    /** (2.124.0) This player's latest jab this client still draws (in flight or stopped short), or null: CorruptionJab's knife's look and its turn in the hand. */
    public static StandClient.Cast jabOf(UUID id){return skillOf(id,XCharaCombo.JAB);}
    private static StandClient.Cast skillOf(UUID id,int combo){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.caster().equals(id)&&c.combo()==combo&&c.age(0)<XCharaCombo.LENGTH[combo]+2)best=c;
        return best;
    }
    /** (2.128.0) This player's latest slam this client still draws (in flight or stopped short; a little past its end, while its knife goes out), or null. */
    public static StandClient.Cast slamOf(UUID id){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.caster().equals(id)&&c.combo()==XCharaCombo.SLAM&&c.age(0)<XCharaCombo.LENGTH[XCharaCombo.SLAM]+10)best=c;
        return best;
    }
    /** (2.129.0) This player's latest three cuts this client still draws (in flight or stopped short), of either kind, or null. */
    public static StandClient.Cast cutterOf(UUID id){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.caster().equals(id)&&XCharaCombo.cutter(c.combo())&&c.age(0)<XCharaCombo.LENGTH[c.combo()]+2)best=c;
        return best;
    }
    /** (2.129.0) The tick this player's three cuts are ready again, as the server told it (0 if none). */
    public static long cutterReady(UUID id){return CUTTER_READY.getOrDefault(id,0L);}
    /** (2.128.0) The tick this player's slam is ready again, as the server told it (0 if none). */
    public static long slamReady(UUID id){return SLAM_READY.getOrDefault(id,0L);}
    /** (2.124.0) The tick this player's jab is ready again, as the server told it (0 if none). */
    public static long jabReady(UUID id){return JAB_READY.getOrDefault(id,0L);}
    /** Forgets this player's rest (their character is not X Chara now: the server forgot it too, StandManager). */
    public static void forget(UUID id){READY.remove(id);CLEAVE_READY.remove(id);JAB_READY.remove(id);SLAM_READY.remove(id);CUTTER_READY.remove(id);SHOT_READY.remove(id);KNIVES_READY.remove(id);RAIN_READY.remove(id);}
    /**
     * Whether a hit holds the local player where they stand now (XCharaCombo.HOLD): their keys do not walk them. The
     * server holds them as long (XCharaAttack.busy).
     */
    public static boolean movementLocked(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled())return false;
        for(var c:SWINGS.values())if(c.caster().equals(mc.player.getUuid())&&!STOPPED.containsKey(c.id())&&c.age(0)<XCharaCombo.HOLD[c.combo()])return true;
        return false;
    }
    /** Whether this player's attack key swings X Chara's knife now: theirs, on the map, holding neither the meteor pickaxe nor a fruit. */
    public static boolean attacks(PlayerEntity p){
        return p!=null&&MapClient.enabled()&&StandManager.xChara(p)&&!dev.sangwoo.celestialfall.client.commerce.CommerceClient.fruitInHand()
            &&!dev.sangwoo.celestialfall.client.meteor.MeteorClient.pickaxeInHand();
    }
    /** The attack key: asks the server for a hit (at most every other tick). Returns whether the knife takes the key (nothing else happens). */
    public static boolean punch(){
        var mc=MinecraftClient.getInstance();if(!attacks(mc.player))return false;
        if(CombatClient.combatInput()&&ticks-lastAsk>=2&&ClientPlayNetworking.canSend(XCharaAttack.INPUT)){
            BotandCamera.prepareInteraction();
            ClientPlayNetworking.send(XCharaAttack.INPUT,PacketByteBufs.create());lastAsk=ticks;
        }
        return true;
    }
    /** For the capture tools: this player's latest swing as "hit,age,stopped", or null. */
    public static String describe(PlayerEntity p,float delta){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.caster().equals(p.getUuid()))best=c;
        return best==null?null:String.format(java.util.Locale.ROOT,"%d,%.2f,%d",best.combo(),best.age(delta),STOPPED.containsKey(best.id())?1:0);
    }
}
