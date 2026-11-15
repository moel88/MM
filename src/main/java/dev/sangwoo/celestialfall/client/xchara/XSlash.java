package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import dev.sangwoo.celestialfall.xchara.XCharaXSlash;
import dev.sangwoo.celestialfall.xchara.XSlashRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * X Chara's H with their hack off, the great X slash, drawn (2.134.0, user request: "X 대참격으로 날리고 맞은 사람은
 * 글리치에 걸리고 가드 못 하게 엑스차라가 가드에 너무 취약하니까 이런거 하나 넣어주면 좋을듯"; their choices "40+15 ·
 * 50초", "가드를 뚫고 맞음", "8초 · 가드만 못 함", "있는 소리 재사용"; the server's XCharaAttack.xslash and
 * XCharaXSlash, XSlashRules). A skill of the knife's pipeline (KnifeClient's swings; KnifeCombo's passes hand it on
 * here). Mine to make up but for what the user chose of what I recommended: the knife lit for a moment, two great cuts
 * crossing, the X they make thrown ahead, an X left glitching on the ground it went over, bursting a second on.
 * <ul>
 * <li>The knife: from {@link #LIT} it is the hack's knife (white within its purple rim, HackMode.knifeLook, so
 * everywhere it is drawn); as the X is let go it is black again at once: its light has gone into the X.</li>
 * <li>The body ({@link #CLIP}): the knife wound up behind the right shoulder, a forehand cut down from the high right
 * to the low left, turned over and wound up over the left shoulder, a backhand cut down from the high left to the low
 * right, and as it is done the free hand thrust out ahead, palm first, sending the X.</li>
 * <li>The X ({@link #glitch}): each cut leaves a great crescent of X Chara's purple where the knife went (the shader's
 * blade of mode 18, its ink line on its cutting edge, a white core, its inner edge breaking into pixels), the two
 * standing crossed before them; let go, it flies level along the server's way, growing as the server's grows, streaks
 * of light behind it, and breaks into pixels where its way ends.</li>
 * <li>The X on the ground ({@link #draw}): the two diagonals of the way it went over, each a purple scar with a white
 * core and a soft glow as wide as it strikes (XSlashRules.BAND), burned in as the X goes over it, pixels flickering
 * on it, pulsing faster and brighter until it bursts: spikes of purple shot up along it from where its lines cross,
 * flashes, pixels thrown up, a ring.</li>
 * <li>On whom it strikes: the knife's own burst (SlashFx.burst, the lunge's size) as the X is drawn reaching them, a
 * smaller one as the ground bursts under them; and on whom the X glitched, all their time (the server's: the
 * snapshot's sealedUntil for a player, the strike's word for anyone), the weakening's glitch on their body
 * ({@link #prepare}: WeakenClient.body). Their own HUD says "가드 불가" (BotandHud).</li>
 * <li>Its sounds are the game's own, used again (the user's "있는 소리 재사용"): the slam's knife lighting as it starts,
 * the knife's cut at each of the two cuts, the slam's leap as the X goes, the knives shot's strike on each it
 * strikes, the double's glitch as the X on the ground is whole, the pull's burst as it bursts.</li>
 * </ul>
 * No screen effect but small shakes. Everything is a function of the skill's own clock and what the server told.
 */
public final class XSlash {
    private XSlash(){}
    /** When the knife lights, when each cut lands, when the X is let go, when its user is free, and its motion's length (seconds: the server's ticks). */
    static final float LIT=XSlashRules.LIT/20f,CUT1=XSlashRules.CUT1/20f,CUT2=XSlashRules.CUT2/20f,FIRE=XSlashRules.FIRE/20f,HOLD_S=XSlashRules.HOLD/20f,LENGTH_S=XSlashRules.LENGTH/20f;
    /** When each arm of the X is drawn across (seconds): with the knife's cut, from its letting go until just past its blow. */
    static final float ARM1_FROM=.185f,ARM1_TO=.27f,ARM2_FROM=.39f,ARM2_TO=.47f;
    /** The X is gone at the end of its way over this long (seconds); the burst's parts last this long. */
    static final float DISSOLVE=.14f,BURST_LIFE=.55f;
    /** How long one is drawn for (seconds): its X on the ground bursts at the latest at burstAt(RANGE), and what the burst leaves (2.135.0: its embers, thrown pieces and dust, XSlashLights.AFTER). */
    public static final float LIFE=XSlashRules.burstAt(XSlashRules.RANGE)/20f+Math.max(BURST_LIFE,XSlashLights.AFTER)+.15f;
    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE;
    /** Each arm's crescent: how far its cutting edge and its inner edge stand out from the line between its horns at its middle (of that line's length; 2.135.0: a wider band, .13 and .05 until then). */
    static final double OUTER=.155,INNER=.045;
    /** Samples along an arm; how wide its edge shows to the camera (blocks: an X seen from its side is a hairline). */
    static final int ARM_SAMPLES=26;static final double EDGE=.42;
    /** The X on the ground: its scar's half width, its white core's, its glow's (XSlashRules.BAND: as wide as it strikes), and how many samples along each diagonal. */
    static final double SCAR=.5,CORE=.09,GLOW_WIDE=XSlashRules.BAND;static final int GROUND_SAMPLES=32;
    /** The burst: a spike every this far along each diagonal (blocks), and how tall they are at most. */
    static final double SPIKE_EVERY=.7,SPIKE_TALL=3.5;

    // ───────────────────────────── what the server told ─────────────────────────────

    /** One X as this client was told of it: where it went from and which way, how far; whether it is gone early; whom it and its burst struck; the ground under its X; its sounds. */
    static final class Told {
        Vec3d from;float yaw=Float.NaN;double reach=Double.NaN;boolean gone;float goneAt=Float.NaN;
        final List<Hit> hits=new ArrayList<>();
        double[][] ground;
        final List<XCharaClient.Voice> voices=new ArrayList<>();
    }
    /** One struck: whom, by what (the X, or the ground bursting), whether they took it, when it is drawn (seconds into its cast), and their size. */
    record Hit(int entity,int by,boolean took,float at,double half,double height){}
    private static final Map<UUID,Told> TOLD=new HashMap<>();
    /** Whom an X glitched, as this client was told: how many ticks of it were left when it was told, and when that was (this client's ticks); and its seed. */
    record Sealed(UUID id,long left,int since,int seed){
        float left(float delta){return left-(StandClient.ticks()-since)-delta;}
        float on(float delta){return StandClient.ticks()-since+delta;}
    }
    private static final Map<Integer,Sealed> SEALED=new HashMap<>();
    private static Object world;

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaXSlash.EVENT,(mc,handler,b,response)->{
            UUID owner=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();UUID id=b.readUuid();
            if(type==XCharaXSlash.FIRED){
                Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();double reach=b.readDouble();
                if(!finite(from)||!Float.isFinite(yaw)||!Double.isFinite(reach))return;
                mc.execute(()->{
                    if(!here(mc,dim))return;
                    var t=told(id);t.from=from;t.yaw=yaw;t.reach=Math.max(XSlashRules.NEAR+.5,Math.min(XSlashRules.RANGE,reach));
                    fired(mc,owner,id,t);
                });
            }else if(type==XCharaXSlash.STRUCK){
                int by=b.readUnsignedByte(),entity=b.readInt();UUID uuid=b.readUuid();boolean took=b.readBoolean();long now=b.readLong(),until=b.readLong();
                mc.execute(()->{if(here(mc,dim))struck(mc,owner,id,by,entity,uuid,took,now,until);});
            }else if(type==XCharaXSlash.GONE){
                mc.execute(()->{
                    var t=TOLD.get(id);if(t==null)return;
                    var c=KnifeClient.SWINGS.get(id);t.gone=true;t.goneAt=c==null?0:c.age(0)/20f;
                    for(var v:t.voices)v.fade();
                });
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(XSlash::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(XSlash::clear));
    }
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static boolean here(MinecraftClient mc,Identifier dim){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return false;
        if(world!=mc.world){clear();world=mc.world;}
        return true;
    }
    private static Told told(UUID id){return TOLD.computeIfAbsent(id,k->new Told());}
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!TOLD.isEmpty()||!SEALED.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        SEALED.values().removeIf(s->s.left(0)<=0);
    }
    /** Forgets every X no longer among the swings this client draws (KnifeClient). */
    static void keep(java.util.Set<UUID> ids){TOLD.keySet().retainAll(ids);}
    static void clear(){for(var t:TOLD.values())for(var v:t.voices)v.fade();TOLD.clear();SEALED.clear();sealed=List.of();world=null;}

    // ───────────────────────────── the knife ─────────────────────────────

    /**
     * The knife's look {@code sec} seconds in (stopped short {@code stop} seconds in, NaN: not), as the hack's {on,
     * seconds since it changed}, or null while it is its own black: lit from LIT as the hack comes on, black at once
     * as the X goes; one stopped short before that goes out as the hack does.
     */
    static float[] lit(float sec,float stop){
        if(sec<LIT)return null;
        if(!Float.isNaN(stop)&&sec>stop){
            if(stop<LIT||stop>=FIRE)return null;
            float t=sec-stop;return t<HackLook.OFF?new float[]{0,t}:null;
        }
        return sec<FIRE?new float[]{1,sec-LIT}:null;
    }
    /** This player's knife as their great X slash has it this frame (HackMode.knifeLook), or null. */
    public static HackLook.Frame knife(UUID id,float delta){
        var c=KnifeClient.xslashOf(id);if(c==null)return null;
        float[] l=lit(c.age(delta)/20f,KnifeClient.stoppedAt(c));if(l==null)return null;
        return new HackLook.Frame(l[0]>0,l[1],HackMode.clock(delta)/20f,(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>17));
    }

    // ───────────────────────────── the body ─────────────────────────────

    /** The forehand cut's plane: the knife ahead as it lands, going down to the low left. */
    static final KnifeCombo.Swing X1=new KnifeCombo.Swing(d(5,-3),d(-42,-80));
    /** The backhand's: ahead as it lands, going down to the low right. */
    static final KnifeCombo.Swing X2=new KnifeCombo.Swing(d(5,3),d(-42,80));
    private static R6.Pose on(double rt,double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf){
        return KnifeCombo.on(rt,fw,up,yaw,pitch,roll,rArm,blade,lArm,rr,rf,lr,lf);
    }
    /**
     * The clip: out of the stance the left foot slides out ahead and the knife, lit, is wound up behind the right
     * shoulder, the body turned right and sinking; the forehand cut down across them to the low left from the ground up
     * (the hips first, the arm, the knife last); the knife turned over and up past the left side, wound over the left
     * shoulder, the body turned left; the backhand down across them to the low right; and as it is done the free hand
     * thrust out ahead, palm first, the body after it: the X goes. Held, then back into the stance.
     */
    static final Anim CLIP=Anim.joints(
        k(0,KnifeCombo.STANCE),
        k(LIT,on(.03,-.02,-.06,-12,-2,2,d(-30,60),X1.at(-60),d(-56,-16),.20,-.01,-.19,.14)),
        k(.11,on(.06,-.05,-.12,-26,-4,4,X1.at(-88),X1.at(-122),d(-40,-20),.21,-.03,-.20,.33)),
        // wound furthest: the knife up behind the right shoulder, the free hand out ahead
        k(.15,on(.07,-.05,-.15,-32,-5,4,X1.at(-100),X1.at(-140),d(-30,-22),.21,-.03,-.20,.40)),
        // let go: the hips first, then the arm, the knife last
        k(.18,on(.05,.01,-.17,-16,0,2,X1.at(-88),X1.at(-134),d(-36,-44),.21,-.03,-.20,.40)),
        k(.215,on(.01,.10,-.20,4,6,-1,X1.at(-34),X1.at(-86),d(-44,-92),.21,-.03,-.20,.40)),
        // the first cut: the blade ahead across them, going down to the low left
        k(CUT1,on(-.03,.18,-.22,14,10,-3,X1.at(32),X1.at(-6),d(-44,-124),.21,-.03,-.20,.40)),
        k(.29,on(-.05,.22,-.23,24,12,-4,X1.at(52),X1.at(20),d(-40,-140),.21,-.03,-.20,.40)),
        // turned over and up past the left side
        k(.33,on(-.04,.22,-.20,30,10,-4,d(-20,-80),d(20,-110),d(-58,-120),.21,-.03,-.20,.40)),
        // wound over the left shoulder
        k(.36,on(-.02,.20,-.16,32,6,-3,X2.at(-96),X2.at(-136),d(-62,-90),.21,-.03,-.20,.40)),
        // let go again: the hips turn back first
        k(.385,on(0,.22,-.18,20,4,-1,X2.at(-86),X2.at(-132),d(-56,-70),.21,-.03,-.20,.40)),
        k(.415,on(.03,.27,-.21,2,8,1,X2.at(-36),X2.at(-88),d(-46,-50),.21,-.03,-.20,.40)),
        // the second cut: ahead across them, going down to the low right
        k(CUT2,on(.05,.32,-.23,-12,11,3,X2.at(30),X2.at(-6),d(-34,-30),.21,-.03,-.20,.40)),
        // the X sent: the free hand thrust out ahead, palm first, the body leaning after it
        k(FIRE,on(.06,.40,-.22,-20,12,3,X2.at(52),X2.at(22),d(4,-6),.21,-.03,-.20,.40)),
        k(.58,on(.06,.44,-.21,-22,11,3,X2.at(58),X2.at(28),d(6,-4),.21,-.03,-.20,.40)),
        k(HOLD_S,on(.05,.40,-.18,-18,9,2,X2.at(56),X2.at(26),d(-4,-8),.21,-.03,-.20,.40)),
        // drawn back: the front foot comes back
        k(1.0,on(.02,.18,-.08,-6,4,1,d(-60,30),d(-6,10),d(-60,-30),.20,.02,-.19,.20)),
        k(LENGTH_S,KnifeCombo.STANCE)).stops(Anim.stop(CUT1,.03,.05),Anim.stop(CUT2,.03,.05)).after(XSlash::fix);
    private static final Anim.Fix BENT=IsosangLimbs.natural(IsosangLimbs.FLOOR);
    /** A frame of the clip as it is drawn: its elbows and knees bent (IsosangLimbs), the stance's straight limbs at its beginning and its end. */
    static R6.Frame fix(float sec,R6.Frame f){
        R6.Frame b=BENT.apply(sec,f);
        float rest=Math.max(1-Ease.smooth(sec,0,KnifeCombo.OUT_OF_REST),Ease.smooth(sec,LENGTH_S-KnifeCombo.INTO_REST,LENGTH_S));
        return rest<=0?b:R6.blend(b,KnifeCombo.AT_REST,Math.min(1,rest));
    }

    // ───────────────────────────── where it is ─────────────────────────────

    /** Where an X goes from, which way, and how far (what the server told, else where its user stood as it began, the way they faced, and as far as it can). */
    record Way(Vec3d from,Vec3d f,Vec3d right,double reach,Told told){
        Vec3d at(double fw,double side,double up){return from.add(f.multiply(fw)).add(right.multiply(side)).add(0,up,0);}
    }
    static Way way(StandClient.Cast c){
        var t=TOLD.get(c.id());
        Vec3d from=t!=null&&t.from!=null?t.from:c.origin();float yaw=t!=null&&Float.isFinite(t.yaw)?t.yaw:c.yaw();
        double reach=t!=null&&Double.isFinite(t.reach)?t.reach:XSlashRules.RANGE;
        Vec3d f=Vec3d.fromPolar(0,yaw);return new Way(from,f,new Vec3d(-f.z,0,f.x),reach,t);
    }
    /** The second into it the X is gone as far as it went ({@code reach}), and the second its X on the ground bursts. */
    static float flown(double reach){return FIRE+(float)XSlashRules.flight(reach)/20f;}
    static float burst(double reach){return XSlashRules.burstAt(reach)/20f;}
    /**
     * The X {@code sec} seconds in, for one that goes as far as {@code reach} (stopped short {@code stop} seconds in,
     * NaN: not): {how far ahead, half its width, half its height (its middle that high), how strongly it shows, how far
     * it is gone (0 to 1)}, or null if none. Before it goes it stands crossed before them where it was cut; going, as
     * far ahead as the server's and as big; at the end of its way it breaks up.
     */
    static double[] shape(float sec,double reach,float stop){
        if(!Float.isNaN(stop)&&stop<FIRE){
            // stopped short before it went: what was cut of it goes out at once
            if(sec<stop||sec>stop+.1f)return sec<stop?new double[]{XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,1,0}:null;
            return new double[]{XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,1,(sec-stop)/.1f};
        }
        if(sec<FIRE)return new double[]{XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,1,0};
        double head=XSlashRules.head((sec-FIRE)*20),fw=Math.min(head,reach);
        float end=flown(reach),gone=sec<end?0:(sec-end)/DISSOLVE;
        if(gone>=1)return null;
        return new double[]{fw,XSlashRules.half(fw),XSlashRules.tall(fw),1,gone};
    }
    /** How much of arm {@code a} has been cut {@code sec} seconds in (0 to 1). */
    static float cut(int a,float sec){return a==0?Ease.smooth(sec,ARM1_FROM,ARM1_TO):Ease.smooth(sec,ARM2_FROM,ARM2_TO);}
    /**
     * Arm {@code a} of an X {@code fw} ahead, {@code half} wide and {@code tall} high (half of each), drawn as far as
     * {@code upTo} (0 to 1, from where its cut began): a crescent from horn to horn, its cutting edge bowed up and out.
     * The first from the high right to the low left (the forehand's way), the second from the high left to the low right.
     */
    static KnifeCombo.Swept arm(Way w,double fw,double half,double tall,int a,float upTo){
        double sx=a==0?half:-half,sy=2*tall,ex=-sx,ey=0;
        double nx=a==0?-tall:tall,ny=half,nl=Math.hypot(nx,ny);nx/=nl;ny/=nl;
        double len=Math.hypot(ex-sx,ey-sy),outer=OUTER*len,inner=INNER*len;
        int n=Math.max(2,(int)Math.ceil(ARM_SAMPLES*upTo)+1);
        Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){
            double t=upTo*i/(n-1.0),cx=sx+(ex-sx)*t,cy=sy+(ey-sy)*t,s=Math.sin(Math.PI*t);
            in[i]=w.at(fw,cx+nx*inner*s,cy+ny*inner*s);out[i]=w.at(fw,cx+nx*outer*s,cy+ny*outer*s);along[i]=(float)t;
        }
        return new KnifeCombo.Swept(in,out,along,n);
    }
    /** The middle of an X's arm {@code a}, where its cutting edge stands furthest out. */
    static Vec3d armMiddle(Way w,double fw,double half,double tall,int a){
        double nx=a==0?-tall:tall,ny=half,nl=Math.hypot(nx,ny),len=2*Math.hypot(half,tall),b=(OUTER+INNER)*.5*len;
        return w.at(fw,nx/nl*b,tall+ny/nl*b);
    }

    // ───────────────────────────── the X ─────────────────────────────

    static int seed(StandClient.Cast c){return (int)(c.start()*31+17);}
    /** The X's two arms (the effects' pass for X Chara's cuts: the shader's blade, mode 18 style 2), each also turned to the camera along its cutting edge. */
    static void glitch(Fx c){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());if(stop==Float.MAX_VALUE)stop=Float.NaN;
        var w=way(c.cast);if(w.told()!=null&&w.told().gone)return;
        var x=shape(t,w.reach(),stop);if(x==null)return;
        float own=KnifeCombo.own(c),eat=(float)x[4],cool=Ease.smooth(eat,0,1);int seed=seed(c.cast);
        for(int a=0;a<2;a++){
            float p=cut(a,Float.isNaN(stop)?t:Math.min(t,stop));if(p<=.01f)continue;
            float shown=(float)x[3]*own*Ease.smooth(t,a==0?ARM1_FROM:ARM2_FROM,(a==0?ARM1_FROM:ARM2_FROM)+.02f);
            var sw=arm(w,x[0],x[1],x[2],a,p);
            KagenoFire.glitchRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),2,seed+a,eat,cool,shown);
            var e=KnifeCombo.edge(sw,c.camera,EDGE);
            KagenoFire.glitchRibbon(e.inner(),e.outer(),e.along(),e.n(),2,seed+a+4,eat,cool,shown*.85f);
        }
    }
    static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}
    static float in(float s){return s<=0?0:Math.min(1,s/.02f);}
    /** Everything of it but the arms themselves, in one pass (the blended bodies, or the light). */
    static void draw(Fx c,EffectMesh m,boolean light){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());if(stop==Float.MAX_VALUE)stop=Float.NaN;
        var w=way(c.cast);boolean gone=w.told()!=null&&w.told().gone;
        int seed=seed(c.cast);float own=KnifeCombo.own(c);
        if(!gone){
            // (2.135.0) the knife drinking in its light, the hack's ring at their feet, the cuts' sparks, the lock as it is whole
            XSlashLights.gather(c,m,t,stop,seed,own,light);
            XSlashLights.ring(c,m,w,t,stop,seed,own,light);
            XSlashLights.sparks(c,m,w,t,stop,seed,own,light);
            XSlashLights.lock(c,m,w,t,stop,own,light);
            var x0=shape(t,w.reach(),stop);
            if(x0!=null){
                // the slash itself: its bloom and aura, the split of each cut, its head as it is drawn, glints along it, echo blades, spikes
                if(light){
                    XSlashLights.aura(c,m,w,x0,t,stop,own);XSlashLights.bloom(c,m,w,x0,t,stop,own);XSlashLights.split(c,m,w,x0,t,stop,seed,own);
                    XSlashLights.head(c,m,w,x0,t,stop,own);XSlashLights.glints(c,m,w,x0,t,stop,seed,own);
                }
                XSlashLights.echoBlades(c,m,w,x0,t,stop,own,light);
                XSlashLights.spikes(c,m,w,x0,t,stop,seed,own,light);
                if(Float.isNaN(stop)||stop>=FIRE||t<stop)XSlashLights.tear(c,m,w,x0,t,seed,own,light);
            }
            if(light)XSlashLights.tipTrails(c,m,w,t,stop,own);
        }
        // ── as each arm is whole, and as the two cross: a flash where it was cut
        if(light&&!gone&&(Float.isNaN(stop)||stop>=ARM1_TO)){
            for(int a=0;a<2;a++){
                float done=a==0?ARM1_TO:ARM2_TO,s=t-done+.03f;if(s<0||s>.25f||!Float.isNaN(stop)&&stop<done)continue;
                Vec3d mid=armMiddle(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,a);float near=GlitchFx.near(mid,c.camera)*own;
                m.softDisc(mid,.9,GLOW,.32f*in(s)*(1-s/.25f)*near);
                if(s<.12f)m.star(mid,.6,WHITE,.6f*in(s)*(1-s/.12f)*near);
            }
            float s=t-FIRE;
            if(s>=0&&s<.2f&&(Float.isNaN(stop)||stop>=FIRE)){
                Vec3d mid=w.at(XSlashRules.NEAR,0,XSlashRules.TALL0);float near=GlitchFx.near(mid,c.camera)*own;
                m.softDisc(mid,1.6,GLOW,.4f*in(s)*(1-s/.2f)*near);
                if(s<.12f)m.star(mid,1.1,WHITE,.75f*in(s)*(1-s/.12f)*near);
            }
        }
        if(!Float.isNaN(stop)&&stop<FIRE)return;
        if(gone&&t>w.told().goneAt+.1f)return;
        var x=shape(t,w.reach(),stop);
        // ── the X in flight: streaks of light behind it, a soft glow, pixels shed off it, and where its way ends, all of it in pixels
        if(x!=null&&t>=FIRE){
            double fw=x[0],half=x[1],tall=x[2];float fade=(float)(1-x[4]);
            if(light){
                // (2.135.0) echoes of it behind, lightning crawling over its arms
                XSlashLights.echoes(c,m,w,t,stop,seed,own);XSlashLights.arcs(c,m,w,x,t,seed,own);
                Vec3d mid=w.at(fw,0,tall);float near=GlitchFx.near(mid,c.camera);
                m.softDisc(mid,half*.8,GLOW,.16f*fade*near);
                double trail=Math.min(3.2,fw-XSlashRules.NEAR+.4);
                for(int a=0;a<2;a++){
                    var sw=arm(w,fw,half,tall,a,1);
                    for(int i=1;i<sw.n()-1;i+=2){
                        Vec3d p=sw.outer()[i];float k=(float)Math.sin(Math.PI*sw.along()[i]);
                        m.softLine(p,p.subtract(w.f().multiply(trail*(.55+.45*k))),.16+.12*k,GLOW,.34f*k*fade*GlitchFx.near(p,c.camera));
                        if(i%4==1)m.line(p,p.subtract(w.f().multiply(trail*.6*k)),.035,LILAC,.5f*k*fade*GlitchFx.near(p,c.camera));
                    }
                }
            }
            // pixels shed off its arms as it goes (flickering squares left behind it)
            for(int j=0;j<30;j++){
                int a=j%2;double u=h(j,seed,31),back=1.2*h(j,seed,32);
                float life=.18f,born=(float)(FIRE+.4*h(j,seed,33)),s=t-born;if(s<0||s>life)continue;
                double at=Math.min(w.reach(),XSlashRules.head((born-FIRE)*20)),bh=XSlashRules.half(at),bt=XSlashRules.tall(at);
                var sw=arm(w,at,bh,bt,a,1);int i=(int)Math.min(sw.n()-1,Math.round(u*(sw.n()-1)));
                Vec3d p=sw.inner()[i].subtract(w.f().multiply(back+2*s));
                float near=Ease.smooth((float)p.distanceTo(c.camera),1.6f,4.5f);if(near<=0)continue;
                var f=GlitchFx.facing(c.camera,p);double size=.06+.08*h(j,seed,34);
                float blink=(int)Math.floor((t*30)+j)%3==0?.35f:1;int rgb=j%7==3?GlitchFx.CYAN:j%7==5?GlitchFx.PINK:j%3==0?LILAC:PURPLE;
                if(light)GlitchFx.square(m,p,f,size*2,rgb==LILAC||rgb==PURPLE?GLOW:rgb,.25f*near*blink);
                else{GlitchFx.square(m,p,f,size+.012,INK,.85f*near*blink);GlitchFx.square(m,p,f,size,rgb,near*blink);}
            }
        }
        // (2.135.0) as it goes, its wake, its horns grinding the floor, and where its way ends, its shards
        XSlashLights.launch(c,m,w,t,seed,own,light);
        XSlashLights.wakes(c,m,w,t,stop,own,light);
        XSlashLights.hornSparks(c,m,w,t,seed,own,light);
        XSlashLights.end(c,m,w,t,seed,own,light);
        float end=flown(w.reach());
        if(t>=end&&t<end+.6f){
            // where its way ends: all of it in pixels, thrown on ahead
            double fw=w.reach(),half=XSlashRules.half(fw),tall=XSlashRules.tall(fw);float s=t-end;
            for(int a=0;a<2;a++){
                var sw=arm(w,fw,half,tall,a,1);
                for(int i=2;i<sw.n()-2;i+=4)GlitchFx.pixels(m,sw.outer()[i],w.f(),.6,4.5,.06,6,seed+a*97+i,s,light,c.camera);
            }
        }
        // ── the X on the ground
        mark(c,m,w,t,seed,light);
        float since=t-burst(w.reach());
        if((w.told()==null||!w.told().gone)&&since>=0&&since<=XSlashLights.AFTER)XSlashLights.burst(c,m,w,diagonals(w),grounds(c,w),t,seed,light);
        // ── on whom it struck
        if(w.told()!=null)for(var hit:w.told().hits){
            float s=t-hit.at();if(s<0||s>=SlashFx.SWIRL_LIFE)continue;
            var live=c.mc.world==null?null:c.mc.world.getEntityById(hit.entity());
            Vec3d mid=live!=null?live.getLerpedPos(c.delta).add(0,live.getHeight()*.55,0):null;if(mid==null)continue;
            if(hit.by()==XCharaXSlash.BY_X){
                SlashFx.burst(m,mid,w.f(),XCharaCombo.HACK_LUNGE,seed+hit.entity()*7,s,light,c.camera);
                if(hit.took())XSlashLights.struck(c,m,w,mid,hit.half(),hit.height(),s,seed+hit.entity()*13,light);
            }
            else SlashFx.burst(m,mid,new Vec3d(0,1,0),XCharaCombo.HACK_SLASH,seed+hit.entity()*11+3,s,light,c.camera);
        }
    }

    // ───────────────────────────── the X on the ground ─────────────────────────────

    /** The two diagonals of an X that went as far as {@code reach}: each as its two ends, on its user's feet's level. */
    static Vec3d[][] diagonals(Way w){
        var out=new Vec3d[2][];int i=0;
        for(double[] d:XSlashRules.diagonals(w.reach()))out[i++]=new Vec3d[]{w.at(d[0],d[1],0),w.at(d[2],d[3],0)};
        return out;
    }
    /**
     * The top of the ground at ({@code x}, {@code z}) near {@code base} (the first block top within 1.6 of it, going
     * down; none, {@code base}): the X on the ground lies on it.
     */
    static double ground(World world,double x,double z,double base){
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        for(int y=(int)Math.floor(base+1.6);y>=(int)Math.floor(base-1.6);y--){
            var pos=new BlockPos(bx,y,bz);var shape=world.getBlockState(pos).getCollisionShape(world,pos);
            if(shape.isEmpty())continue;
            double top=y+shape.getMax(Direction.Axis.Y);
            if(top<=base+1.6+1e-6)return top;
        }
        return base;
    }
    static double[][] grounds(Fx c,Way w){
        var t=w.told();
        if(t!=null&&t.ground!=null)return t.ground;
        var ds=diagonals(w);var out=new double[2][GROUND_SAMPLES+1];
        for(int a=0;a<2;a++)for(int i=0;i<=GROUND_SAMPLES;i++){
            Vec3d p=ds[a][0].add(ds[a][1].subtract(ds[a][0]).multiply(i/(double)GROUND_SAMPLES));
            out[a][i]=c.mc.world==null?w.from().y:ground(c.mc.world,p.x,p.z,w.from().y);
        }
        if(t!=null&&Double.isFinite(t.reach))t.ground=out;
        return out;
    }
    /**
     * How strongly the X on the ground shows {@code sec} seconds in, for one that went as far as {@code reach}: as
     * it is burned in (a flash where the X has just gone over it, on each point's own clock: {@link #burnt}), whole until
     * it bursts, then going.
     */
    static float markShown(float sec,double reach){
        float b=burst(reach);
        if(sec<FIRE)return 0;
        return sec<b?1:1-Ease.smooth(sec,b+.05f,b+.45f);
    }
    /** How its pulse runs {@code sec} seconds in (0 to 1 over and over): slow as it is whole, faster and faster to its burst. */
    static float pulse(float sec,double reach){
        float from=flown(reach),b=burst(reach);if(sec<from||sec>=b)return 0;
        double s=sec-from;return (float)(.5+.5*Math.sin(Math.PI*2*(2.2*s+5.5*s*s)));
    }
    /** How near its burst it is (0 as it is whole, 1 as it bursts). */
    static float charge(float sec,double reach){float from=flown(reach),b=burst(reach);return Ease.smooth(sec,from,b);}
    /** How hot a point {@code fw} ahead is {@code sec} seconds in: a flash as the X goes over it, fading. */
    static float burnt(float sec,double fw){
        float over=FIRE+(float)XSlashRules.reached(fw)/20f,s=sec-over;
        return s<0?0:(float)Math.exp(-s/.14);
    }
    private static void mark(Fx c,EffectMesh m,Way w,float t,int seed,boolean light){
        float shown=markShown(t,w.reach());if(shown<=0)return;
        if(w.told()!=null&&w.told().gone)return;
        double head=t<FIRE?0:Math.min(w.reach(),XSlashRules.head((t-FIRE)*20));
        if(t>=flown(w.reach()))head=w.reach();
        var g=grounds(c,w);var ds=diagonals(w);var raw=XSlashRules.diagonals(w.reach());
        float bang=burst(w.reach()),charge=charge(t,w.reach()),pulse=pulse(t,w.reach());
        // flickering hard over its last few frames before it bursts
        float flicker=t>bang-.3f&&t<bang?((int)Math.floor(t*30)%2==0?1.25f:.7f):1;
        for(int a=0;a<2;a++){
            double drawn=XSlashRules.drawn(raw[a],head);if(drawn<=0)continue;
            Vec3d a0=ds[a][0],a1=ds[a][1],dir=a1.subtract(a0);
            Vec3d across=new Vec3d(-dir.z,0,dir.x).normalize();
            int last=(int)Math.ceil(GROUND_SAMPLES*drawn);
            for(int i=0;i<last;i++){
                double u0=i/(double)GROUND_SAMPLES,u1=Math.min(drawn,(i+1)/(double)GROUND_SAMPLES);if(u1<=u0)continue;
                Vec3d p0=a0.add(dir.multiply(u0)),p1=a0.add(dir.multiply(u1));
                double y0=lerpGround(g[a],u0)+.035,y1=lerpGround(g[a],u1)+.035;
                p0=new Vec3d(p0.x,y0,p0.z);p1=new Vec3d(p1.x,y1,p1.z);
                double fw=raw[a][0]+(raw[a][2]-raw[a][0])*(u0+u1)*.5;
                float hot=burnt(t,fw),near=GlitchFx.near(p0.add(p1).multiply(.5),c.camera);
                if(near<=0)continue;
                // (the tips of each diagonal thin to a point)
                double taper=Math.min(1,Math.min(u0,1-u1)*12+.25);
                float glow=(.55f+.45f*charge*pulse)*flicker;
                if(light){
                    strip(m,p0,p1,across,GLOW_WIDE*taper,GLOW,.0f,.30f*glow*shown*near);
                    strip(m,p0,p1,across,CORE*taper*(1+hot),WHITE,.85f*(.45f+.55f*hot)*shown*near*glow,.85f*(.45f+.55f*hot)*shown*near*glow);
                    if(hot>.05f)strip(m,p0,p1,across,SCAR*1.6*taper,LILAC,0,.5f*hot*shown*near);
                }else{
                    strip(m,p0,p1,across,(SCAR+.07)*taper,INK,.8f*shown*near,.8f*shown*near);
                    strip(m,p0,p1,across,SCAR*taper,hot>.4f?LILAC:PURPLE,.92f*shown*near,.92f*shown*near);
                    strip(m,p0,p1,across,SCAR*.45*taper,DEEP,.8f*shown*near,.8f*shown*near);
                }
            }
            // pixels flickering on it (flat on the ground, on a grid), more as it nears its burst
            int pixels=(int)(14*drawn);
            for(int j=0;j<pixels;j++){
                int n=(int)Math.floor(t*12+h(j,seed+a,41)*7);
                if(h(n+j*13,seed+a,42)>.35+.45*charge)continue;
                double u=h(j,seed+a,43)*drawn,side=(h(n,seed+a*5+j,44)-.5)*2.2*GLOW_WIDE;
                Vec3d p=a0.add(dir.multiply(u)).add(across.multiply(Math.round(side/.18)*.18));
                p=new Vec3d(p.x,lerpGround(g[a],u)+.05,p.z);
                float near=GlitchFx.near(p,c.camera);if(near<=0)continue;
                double half=.07*(h(n,seed+j,45)<.25?2:1);
                Vec3d ax=across.multiply(half),az=dir.normalize().multiply(half);
                int rgb=h(n,seed+j,46)<.6?PURPLE:h(n,seed+j,47)<.5?LILAC:INK;
                if(light){if(rgb!=INK)m.quad(p.subtract(ax).subtract(az),p.add(ax).subtract(az),p.add(ax).add(az),p.subtract(ax).add(az),GLOW,.3f*shown*near);}
                else m.quad(p.subtract(ax).subtract(az),p.add(ax).subtract(az),p.add(ax).add(az),p.subtract(ax).add(az),rgb,.9f*shown*near);
            }
        }
        // (2.135.0) cracks out from it, data rising off it, its light running in to where its lines cross
        float glowAll=(.55f+.45f*charge*pulse)*flicker;
        XSlashLights.cracks(c,m,w,ds,g,t,shown,glowAll,seed,light);
        XSlashLights.motes(c,m,w,ds,g,t,shown,charge,seed,light);
        if(light)XSlashLights.converge(c,m,w,ds,g,t,seed);
        // ── it bursts: spikes up along it from where its lines cross, flashes, pixels thrown up, a ring
        float s=t-bang;if(s<0||s>=BURST_LIFE)return;
        Vec3d cross=ds[0][0].add(ds[0][1]).multiply(.5);
        double crossY=lerpGround(g[0],.5);cross=new Vec3d(cross.x,crossY,cross.z);
        for(int a=0;a<2;a++){
            Vec3d a0=ds[a][0],a1=ds[a][1],dir=a1.subtract(a0);double len=dir.horizontalLength();
            int spikes=(int)Math.max(2,Math.floor(len/SPIKE_EVERY));
            if(light){
                // the whole line flashes white for a moment
                for(int i=0;i<GROUND_SAMPLES;i++){
                    double u0=i/(double)GROUND_SAMPLES,u1=(i+1)/(double)GROUND_SAMPLES;
                    Vec3d p0=a0.add(dir.multiply(u0)),p1=a0.add(dir.multiply(u1));
                    p0=new Vec3d(p0.x,lerpGround(g[a],u0)+.04,p0.z);p1=new Vec3d(p1.x,lerpGround(g[a],u1)+.04,p1.z);
                    float fl=in(s)*(1-Ease.smooth(s,.04f,.16f));
                    strip(m,p0,p1,new Vec3d(-dir.z,0,dir.x).normalize(),GLOW_WIDE*1.2,WHITE,.0f,.55f*fl*GlitchFx.near(p0,c.camera));
                }
            }
            for(int j=0;j<spikes;j++){
                double u=(j+.5+.6*(h(j,seed+a*31,51)-.5))/spikes;
                Vec3d base=a0.add(dir.multiply(u));base=new Vec3d(base.x,lerpGround(g[a],u),base.z);
                // from where the lines cross outward
                float delay=(float)(Math.abs(u-.5)*2*.12),sj=s-delay;if(sj<0||sj>.42f)continue;
                double tall=SPIKE_TALL*(.5+.5*h(j,seed+a*31,52))*Ease.smooth(sj,0,.06f);
                Vec3d lean=new Vec3d(h(j,seed+a,53)-.5,0,h(j,seed+a,54)-.5).multiply(.5);
                Vec3d top=base.add(lean.multiply(tall/SPIKE_TALL)).add(0,tall,0);
                float fade=in(sj)*(1-Ease.smooth(sj,.12f,.42f))*GlitchFx.near(base.add(0,tall*.5,0),c.camera);if(fade<=0)continue;
                double wide=.22+.12*h(j,seed+a,55);
                if(light){m.needle(base,top,wide*2.4,.25,GLOW,.18f*fade);if(j%2==0)m.needle(base,top,wide*.3,.25,WHITE,.85f*fade);}
                else{m.needle(base,top,wide*1.45,.25,INK,.85f*fade);m.needle(base,top,wide,.25,j%2==0?PURPLE:DEEP,.95f*fade);}
                if(j%3==0)GlitchFx.pixels(m,top,new Vec3d(0,1,0),.5,3.2,.05,4,seed+a*131+j,sj,light,c.camera);
            }
        }
        // the ring where its lines cross, and a flash there
        float r=in(s)*(1-Ease.smooth(s,.15f,.5f)),r2=s<.07f?0:in(s-.07f)*(1-Ease.smooth(s,.2f,.5f));
        if(light){
            FxLib.ringPlane(m,cross.add(0,.06,0),new Vec3d(1,0,0),new Vec3d(0,0,1),.6+6.4*Ease.out(s,0,.42f),.12,WHITE,.6f*r*GlitchFx.near(cross,c.camera));
            FxLib.ringPlane(m,cross.add(0,.06,0),new Vec3d(1,0,0),new Vec3d(0,0,1),.4+4.4*Ease.out(s-.07f,0,.4f),.1,LILAC,.45f*r2*GlitchFx.near(cross,c.camera));
            if(s<.16f){m.softDisc(cross.add(0,1,0),3.2,GLOW,.55f*in(s)*(1-s/.16f));m.star(cross.add(0,1,0),2.1,WHITE,.65f*in(s)*(1-s/.16f));}
        }else{
            DanceBurst.band(m,cross.add(0,.05,0),new Vec3d(1,0,0),new Vec3d(0,0,1),.5+6.1*Ease.out(s,0,.42f),.26,PURPLE,.7f*r);
            DanceBurst.band(m,cross.add(0,.05,0),new Vec3d(1,0,0),new Vec3d(0,0,1),.4+4.2*Ease.out(s-.07f,0,.4f),.18,LILAC,.55f*r2);
        }
    }
    static double lerpGround(double[] g,double u){
        double x=Math.max(0,Math.min(1,u))*GROUND_SAMPLES;int i=(int)Math.floor(x);if(i>=GROUND_SAMPLES)return g[GROUND_SAMPLES];
        return g[i]+(g[i+1]-g[i])*(x-i);
    }
    /** A strip on the ground from {@code a} to {@code b}, {@code half} to either side of it: {@code mid} along its middle line fading to {@code edge} at its sides. */
    static void strip(EffectMesh m,Vec3d a,Vec3d b,Vec3d across,double half,int rgb,float edge,float mid){
        if(Math.max(edge,mid)<=0||half<=0)return;
        Vec3d s=across.multiply(half);
        m.vertex(a,rgb,mid);m.vertex(b,rgb,mid);m.vertex(b.add(s),rgb,edge);m.vertex(a.add(s),rgb,edge);
        m.vertex(a.subtract(s),rgb,edge);m.vertex(b.subtract(s),rgb,edge);m.vertex(b,rgb,mid);m.vertex(a,rgb,mid);
    }

    // ───────────────────────────── light, shake ─────────────────────────────

    /** Its light on the world, and little afterglow while the X flies (its light would leave copies of it behind). */
    static void frame(Fx c,FxFrame fr){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());if(stop==Float.MAX_VALUE)stop=Float.NaN;
        var w=way(c.cast);if(w.told()!=null&&w.told().gone)return;
        if(t>=ARM1_FROM&&t<flown(w.reach())+.1f)fr.afterglow(.05f);
        // (2.135.0) the knife as it drinks in its light; each cut as it lands
        if(t>=LIT&&t<FIRE&&(Float.isNaN(stop)||t<stop)){
            var hb=KnifeCombo.blade(c.pos,c.yaw,XCharaCombo.XSLASH,t);
            fr.light(hb[0].add(hb[1].multiply(1.4)),0x9a3cff,4.5,.3f+.3f*Ease.smooth(t,LIT,XSlashLights.GATHERED));
        }
        for(int a=0;a<2;a++){
            float at=a==0?CUT1:CUT2;if(!Float.isNaN(stop)&&stop<at)continue;
            float bump=KnifeCombo.bump(t,at,.09f);if(bump>.01f)fr.light(armMiddle(w,XSlashRules.NEAR,XSlashRules.HALF0,XSlashRules.TALL0,a),0xb050ff,8,1.1f*bump);
        }
        if(!Float.isNaN(stop)&&stop<FIRE)return;
        var x=shape(t,w.reach(),stop);
        if(x!=null&&t>=FIRE)fr.light(w.at(x[0],0,x[2]),0x9a3cff,11,1.15f*(float)(1-x[4]));
        float go=KnifeCombo.bump(t,FIRE,.1f);if(go>.01f)fr.light(w.at(XSlashRules.NEAR+.4,0,XSlashRules.TALL0),0xc070ff,10,1.4f*go);
        float b=burst(w.reach());
        if(t>=flown(w.reach())&&t<b)fr.light(w.at(w.reach()*.55,0,.6),0x8a14e8,9,.3f+.6f*charge(t,w.reach())*pulse(t,w.reach()));
        float s=t-b;
        if(s>=0&&s<.5f){
            fr.light(w.at((XSlashRules.NEAR+w.reach())*.5,0,1.2),0xc070ff,20,2.2f*(1-s/.5f));
            for(double[] d:XSlashRules.diagonals(w.reach())){
                fr.light(w.at(d[0],d[1],1),0xa040ff,9,1.1f*(1-s/.5f));fr.light(w.at(d[2],d[3],1),0xa040ff,9,1.1f*(1-s/.5f));
            }
        }
        if(s>=0&&s<XSlashLights.AFTER)fr.light(w.at((XSlashRules.NEAR+w.reach())*.5,0,1),0x8a14e8,12,.4f*(1-Ease.smooth(s,.3f,XSlashLights.AFTER)));
    }
    /** Small shakes: at each cut, as the X goes, on whom it strikes, as its X on the ground bursts. */
    static float shake(StandClient.Cast cast,float sec){
        float stop=KnifeClient.until(cast.id());
        // (2.135.0) a little harder throughout: the cuts, the X going, whom it strikes, its burst
        float s=.16f*KnifeCombo.bump(sec,CUT1,.06f)+.16f*KnifeCombo.bump(sec,CUT2,.06f);
        if(stop!=Float.MAX_VALUE&&stop<FIRE)return s;
        var w=way(cast);
        s+=.42f*KnifeCombo.bump(sec,FIRE,.09f);
        if(w.told()!=null)for(var hit:w.told().hits)if(hit.by()==XCharaXSlash.BY_X&&hit.took())s+=.5f*KnifeCombo.bump(sec,hit.at(),.07f);
        if(w.told()==null||!w.told().gone)s+=1.0f*KnifeCombo.bump(sec,burst(w.reach()),.12f);
        return s;
    }

    /** (2.135.0) The ground under its X as it bursts (XSlashLights.solids). */
    static void solids(Fx c,dev.sangwoo.celestialfall.client.vfx.FxSolids so){XSlashLights.solids(c,so,way(c.cast),c.sec);}
    /** How strongly the body leaves purple copies of itself (BodyGhost's silhouettes, 2.135.0): through the cuts and the throw. */
    static float afterimages(float sec){return Ease.smooth(sec,.16f,.2f)*(1-Ease.smooth(sec,.54f,.64f));}
    /** Their colour. */
    static final int AFTERIMAGE=0x9a3cff;
    /** (2.135.0) Dust kicked up as the X goes and as its X on the ground bursts, for every great X slash this client draws (the effect layer's anime dust). */
    public static void dust(org.joml.Matrix4f matrix,Vec3d camera){
        if(TOLD.isEmpty()||!dev.sangwoo.celestialfall.client.vfx.FxLayer.available()||!KagenoFire.ready())return;
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;float delta=mc.getTickDelta();
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        for(var c:KnifeClient.SWINGS.values()){
            if(c.combo()!=XCharaCombo.XSLASH)continue;
            var w=way(c);if(w.told()==null||w.told().gone||w.told().ground==null)continue;
            float stop=KnifeClient.until(c.id());if(stop!=Float.MAX_VALUE&&stop<FIRE)continue;
            if(w.from().squaredDistanceTo(camera)>110*110)continue;
            XSlashLights.dust(w,w.told().ground,c.age(delta)/20f,seed(c),dust,puffs);
        }
        if(!dust.isEmpty())KagenoFire.dust(matrix,camera,dust,puffs,(float)(mc.world.getTime()+delta));
    }

    // ───────────────────────────── sounds ─────────────────────────────

    /**
     * Its own sounds by its clock (each {name, tick, part}), the game's own used again (the user's "있는 소리 재사용"):
     * the slam's knife lighting as it starts, the knife's cut a moment before each of the two cuts lands (its hiss on
     * the blow, KnifeClient.SOUND_BEFORE), the slam's leap as the X goes. What its X does sounds of itself ({@link #fired},
     * {@link #struck}).
     */
    static final Object[][] CUES={{"xchara_slam_lit",0,.8f},{"xchara_slash",XSlashRules.CUT1-KnifeClient.SOUND_BEFORE,1f},{"xchara_slash",XSlashRules.CUT2-KnifeClient.SOUND_BEFORE,1f},{"xchara_slam_leap",XSlashRules.FIRE-1,.9f}};
    /** As the X on the ground is whole, the double's glitch; as it bursts, the pull's; on each struck, the knives shot's strike. */
    static final String WHOLE="xchara_clone",BURST="xchara_pull",HIT=BladeShot.HIT_SOUND;
    static final float WHOLE_PART=.55f;
    /** How loud something of X {@code id} at {@code point} is for this client now: loud for its user; for anyone else from the nearer of it and its user. */
    private static java.util.function.Supplier<Float> loud(MinecraftClient mc,UUID owner,Vec3d point){
        if(mc.player!=null&&mc.player.getUuid().equals(owner))return ()->(float)CelestialFall.CONFIG.effectVolume;
        return ()->{
            var m=MinecraftClient.getInstance();var user=m.world==null?null:m.world.getPlayerByUuid(owner);
            return Math.max(KnifeClient.loudness(m,point),KnifeClient.loudness(m,user==null?point:user.getPos()));
        };
    }
    private static void play(MinecraftClient mc,Told t,String name,java.util.function.Supplier<Float> loud,float part,int wait,StandClient.Cast c,String what){
        var v=XCharaClient.Voice.quieter(CelestialFall.id(name),loud,part);
        if(v.full()<=.01f)return;
        if(wait>0)mc.getSoundManager().play(v,wait);else mc.getSoundManager().play(v);
        t.voices.add(v);
        KnifeClient.heard(String.format(java.util.Locale.ROOT,"%s %s, told %.0f ticks in, played %d later at %.2f",name,what,c==null?-1:c.age(0),Math.max(0,wait),v.full()));
    }
    /** The X went: the sounds of its X on the ground as it is whole and as it bursts, at their moments. */
    private static void fired(MinecraftClient mc,UUID owner,UUID id,Told t){
        var c=KnifeClient.SWINGS.get(id);if(c==null||mc.player==null)return;
        Vec3d f=Vec3d.fromPolar(0,t.yaw),mid=t.from.add(f.multiply((XSlashRules.NEAR+t.reach)*.5));
        int age=(int)Math.floor(c.age(0));
        int whole=(int)Math.ceil(flown(t.reach)*20)-age,bang=XSlashRules.burstAt(t.reach)-age;
        if(whole>-2)play(mc,t,WHOLE,loud(mc,owner,mid),WHOLE_PART,whole,c,"whole");
        if(bang>-2)play(mc,t,BURST,loud(mc,owner,mid),1,bang,c,"burst");
    }
    /** Someone struck by the X (as it is drawn reaching them) or by its burst: the strike's sound and what it leaves on them; and the X's glitch on them. */
    private static void struck(MinecraftClient mc,UUID owner,UUID id,int by,int entity,UUID uuid,boolean took,long now,long until){
        var t=told(id);var c=KnifeClient.SWINGS.get(id);
        var e=mc.world.getEntityById(entity) instanceof LivingEntity l?l:null;
        float at;
        if(by==XCharaXSlash.BY_X){
            double fw=XSlashRules.NEAR;
            if(e!=null&&t.from!=null&&Float.isFinite(t.yaw))fw=e.getPos().subtract(t.from).dotProduct(Vec3d.fromPolar(0,t.yaw))-e.getWidth()*.5;
            double reach=Double.isFinite(t.reach)?t.reach:XSlashRules.RANGE;
            at=FIRE+(float)XSlashRules.reached(Math.max(XSlashRules.NEAR,Math.min(reach,fw)))/20f;
        }else at=burst(Double.isFinite(t.reach)?t.reach:XSlashRules.RANGE);
        t.hits.add(new Hit(entity,by,took,at,e==null?.3:e.getWidth()*.5,e==null?1.8:e.getHeight()));
        if(took&&by==XCharaXSlash.BY_X&&until>now)SEALED.put(entity,new Sealed(uuid,until-now,StandClient.ticks(),entity*7919+(int)(now*31)));
        if(took&&c!=null){
            Vec3d point=e==null?(t.from==null?c.origin():t.from):e.getPos().add(0,e.getHeight()*.55,0);
            int wait=(int)Math.floor(at*20-BladeShot.HIT_SOUND_LEAD-c.age(0));
            play(mc,t,HIT,loud(mc,owner,point),by==XCharaXSlash.BY_X?1:.6f,wait,c,by==XCharaXSlash.BY_X?"x strike":"burst strike");
        }
    }

    // ───────────────────────────── whom it glitched ─────────────────────────────

    /** Ticks of this one's glitch left now, as this client knows (the server's word of the strike; a player's own state too); 0 if none. */
    static float sealedLeft(MinecraftClient mc,Entity e,float delta){
        float left=0;
        var s=SEALED.get(e.getId());if(s!=null&&s.id().equals(e.getUuid()))left=Math.max(0,s.left(delta));
        if(e instanceof PlayerEntity p&&mc.world!=null){
            var snap=CombatClient.snapshot(p.getUuid());
            left=Math.max(left,(float)(snap.sealedUntil()-(mc.world.getTime()+delta)));
        }
        return left;
    }
    /** One glitched to draw this frame. */
    private record Shown(Vec3d feet,double height,double width,float clock,float alpha,int seed,boolean fresh,float on){}
    private static List<Shown> sealed=List.of();
    /** Every frame before the effects: whom the X glitched, each still so. True if any shows. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        if(mc.world==null){sealed=List.of();return false;}
        var out=new ArrayList<Shown>();var seen=new java.util.HashSet<Integer>();
        for(var en:SEALED.entrySet()){
            if(!(mc.world.getEntityById(en.getKey()) instanceof LivingEntity e)||!e.isAlive()||!e.getUuid().equals(en.getValue().id()))continue;
            add(mc,out,e,delta,camera,en.getValue());seen.add(e.getId());
        }
        for(var p:mc.world.getPlayers())if(!seen.contains(p.getId())&&p.isAlive())add(mc,out,p,delta,camera,null);
        sealed=out;
        return !out.isEmpty();
    }
    private static void add(MinecraftClient mc,List<Shown> out,LivingEntity e,float delta,Vec3d camera,Sealed s){
        float left=sealedLeft(mc,e,delta)/20f;if(left<=0)return;
        Vec3d feet=e.getLerpedPos(delta);if(feet.squaredDistanceTo(camera)>96*96)return;
        float on=s==null?99:s.on(delta)/20f,a=WeakenClient.shows(on,left);if(a<=0)return;
        int seed=s!=null?s.seed():(int)(e.getUuid().getLeastSignificantBits()*31+7);
        out.add(new Shown(feet,e.getHeight(),e.getWidth(),on==99?(StandClient.ticks()+delta)/20f:on,a,seed,s!=null&&on<1,on));
    }
    /** The blended pass: the glitch on their bodies. */
    public static void alpha(EffectMesh m){for(var x:sealed)WeakenClient.body(m,x.feet,x.height,x.width,x.clock,x.on,x.alpha,x.seed,x.fresh,false);}
    /** The light, added. */
    public static void additive(EffectMesh m){for(var x:sealed)WeakenClient.body(m,x.feet,x.height,x.width,x.clock,x.on,x.alpha,x.seed,x.fresh,true);}
    /** A little purple light on the world round each of them. */
    public static void frame(FxFrame f,Vec3d camera){for(var x:sealed)f.light(x.feet.add(0,x.height*.5,0),GLOW,2.4,.2f*x.alpha);}

    /** For the capture tools and the checks: the X this client knows of (as "reach:hits") and whom it holds glitched (as "entity:seconds left"). */
    public static String describe(float delta){
        var sb=new StringBuilder();
        for(var t:TOLD.values()){if(sb.length()>0)sb.append(' ');sb.append(String.format(java.util.Locale.ROOT,"x%.1f:%d%s",t.reach,t.hits.size(),t.gone?"g":""));}
        for(var en:SEALED.entrySet()){if(sb.length()>0)sb.append(' ');sb.append(String.format(java.util.Locale.ROOT,"%d:%.1f",en.getKey(),en.getValue().left(delta)/20f));}
        return sb.toString();
    }
}
