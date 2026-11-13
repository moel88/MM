package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.xchara.SlamGround;
import dev.sangwoo.celestialfall.xchara.SlamRules;
import dev.sangwoo.celestialfall.xchara.XCharaRules;
import dev.sangwoo.celestialfall.xchara.XCharaUltimate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.SLAM;

/**
 * X Chara's G with their hack off, the slam, drawn (2.128.0, user request with their video of it, "G - Overwrite Slam":
 * "이번에는 q 비활성화 g스킬 효과음은 영상에 있고 스킬은 영상처럼 검이 q활성화 상태 색깔로 변하고 보라색 네온 빛 원을 소환하고
 * 공중에 올라가서 내가 보는 방향으로 내려찍는 스킬이야"; the server's XCharaAttack.slam, SlamRules). A skill of the knife's
 * pipeline as the cleave and the jab are (KnifeClient's swings, KnifeCombo's passes hand it on here).
 * <ul>
 * <li>The knife: from {@link #LIT} it is the hack's knife, white within its glowing purple rim ({@link #knife}), until
 * it is over ({@link #END}), where it goes out as the hack's does.</li>
 * <li>The circle ({@link #CIRCLE} to END): a great flat disc of purple neon on the ground about where they stood, as far
 * as the slam can come down (SlamRules.RADIUS): on the top of every block of the ground there, cut round at its rim
 * ({@link #disc}); it blinks on and blinks out, as a thing on a screen. From the leap squares of light, boxes of pale
 * pink with a glow about them, rise off it all over ({@link #cube}).</li>
 * <li>The body: every client draws it where the slam's plan has it ({@link #shift}: SlamRules.planned, over where it
 * really stands until the landing has taken it to where it came down), crouched with the knife low, sprung straight
 * up, drifting up there with the knife out, the knife taken up over the head, then thrown forward and down the dive
 * onto it, and landed in a deep crouch with the knife driven into the ground ahead ({@link #CLIP}); it turns to the
 * way it dives ({@link #facing}). Their own camera goes with the body they see ({@link #offset}).</li>
 * <li>Where it comes down: up there their client tells the server, every tick until the dive's word comes, where the
 * middle of its screen points ({@link #tellAim}); and marks for them alone the floor it would come down on, a ring as
 * wide as the landing strikes ({@link #marker}).</li>
 * <li>The landing ({@link #LAND}): a white burst with long white, magenta and dark spikes, magenta bars flickering in
 * it as a broken screen's, rings along the ground, pixels; the floor round it thrown up in a crater's rim and its
 * pieces flung ({@link #solids}), a ripple running out. No screen effect but the shake.</li>
 * </ul>
 * Everything is a function of the skill's own clock, of where it began and of where it came down.
 */
public final class OverwriteSlam {
    private OverwriteSlam(){}
    /** The server's ticks, in seconds. */
    static final float LIT=SlamRules.LIT/20f,CIRCLE=SlamRules.CIRCLE/20f,CROUCH=SlamRules.CROUCH/20f,LEAP=SlamRules.LEAP/20f,RISEN=SlamRules.RISEN/20f,
        DIVE=SlamRules.DIVE/20f,LAND=SlamRules.LAND/20f,FREE=SlamRules.FREE/20f,END=SlamRules.END/20f;
    /** The second spring, in the air (2.128.3: the rise is in two, SlamRules.LEAP2). */
    static final float LEAP2=SlamRules.LEAP2/20f;
    /** How long one is drawn for (seconds): what the landing leaves on the ground is gone by then. */
    public static final float LIFE=LAND+2.7f;

    static float stopped(StandClient.Cast c){return GiantCleave.stopped(c);}
    private static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}
    static int seed(StandClient.Cast c){return (int)(c.start()*31+SLAM*7);}
    private static Vec3d vec(double[] p){return new Vec3d(p[0],p[1],p[2]);}
    private static double[] arr(Vec3d p){return p==null?null:new double[]{p.x,p.y,p.z};}

    // ───────────────────────────── the knife ─────────────────────────────

    /**
     * The knife's look {@code sec} seconds into a slam (stopped short {@code stop} seconds in, NaN: not), as the hack's
     * {on, seconds since it changed}, or null while it is its own black: lit from LIT as the hack comes on, going out
     * as the hack does when it is over or stops short.
     */
    static float[] lit(float sec,float stop){
        if(sec<LIT)return null;
        float out=Float.isNaN(stop)?END:Math.min(END,stop);
        if(out<LIT)return null;
        if(sec<out)return new float[]{1,sec-LIT};
        float t=sec-out;return t<HackLook.OFF?new float[]{0,t}:null;
    }
    /** This player's knife as their slam has it this frame (the hack's look, HackMode.knifeLook), or null if they are in none or it is black. */
    public static HackLook.Frame knife(UUID id,float delta){
        var c=KnifeClient.slamOf(id);if(c==null)return null;
        float[] l=lit(c.age(delta)/20f,stopped(c));if(l==null)return null;
        return new HackLook.Frame(l[0]>0,l[1],HackMode.clock(delta)/20f,(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>17));
    }

    // ───────────────────────────── where the body is ─────────────────────────────

    /** Where the body's feet are {@code age} ticks into slam {@code c} (SlamRules.planned, from where it began). */
    static Vec3d planned(StandClient.Cast c,KnifeClient.Slam info,float age){
        return vec(SlamRules.planned(age,arr(c.origin()),info.rise,new double[]{info.aheadX,info.aheadZ},arr(info.land)));
    }
    /**
     * How much the body is held to the plan {@code age} ticks in: all of it from the leap; let go a little after the
     * landing (the server has taken them to where they came down by then, and they are free soon after).
     */
    static float held(float age){return age<=SlamRules.LEAP?0:1-Ease.smooth(age,SlamRules.LAND+4,SlamRules.LAND+10);}
    /**
     * How far from where slam {@code c}'s user really is their body is drawn this frame (their renderer's offset, BeatdownRenderMixin: their model, name and shadow's place go together): up over
     * where they stood, down the dive, and where it came down until they are really there.
     */
    public static Vec3d shift(StandClient.Cast c,float delta){
        if(c==null||c.combo()!=SLAM||c.skill()!=dev.sangwoo.celestialfall.xchara.XCharaCombo.SKILL)return Vec3d.ZERO;
        var info=KnifeClient.slam(c.id());if(info==null||!Float.isNaN(stopped(c)))return Vec3d.ZERO;
        float age=c.age(delta),w=held(age);if(w<=0)return Vec3d.ZERO;
        var mc=MinecraftClient.getInstance();var owner=mc.world==null?null:mc.world.getPlayerByUuid(c.caster());if(owner==null)return Vec3d.ZERO;
        return planned(c,info,age).subtract(owner.getLerpedPos(delta)).multiply(w);
    }
    /** How far from where this player really is their body is drawn now, in a slam (null: in none, or nowhere else): their own camera goes with it (BotandCamera). */
    public static Vec3d offset(UUID id,float delta){
        var c=KnifeClient.slamOf(id);if(c==null)return null;
        Vec3d s=shift(c,delta);return s.lengthSquared()<1e-8?null:s;
    }
    /** Where slam {@code c}'s body is drawn this frame (its feet): the plan while it is held to it, else where its user is. */
    static Vec3d body(Fx c){return c.pos.add(shift(c.cast,c.delta));}
    /**
     * The way slam {@code c}'s body is drawn facing (degrees): the way it began, turned to the way it dives once this
     * client has heard where it comes down.
     */
    public static float facing(StandClient.Cast c,float delta){
        var info=KnifeClient.slam(c.id());if(info==null||info.land==null||Float.isNaN(info.heard))return c.yaw();
        Vec3d to=info.land.subtract(c.origin());if(to.horizontalLengthSquared()<.25)return c.yaw();
        float way=(float)Math.toDegrees(Math.atan2(-to.x,to.z)),w=Ease.smooth(c.age(delta),info.heard,info.heard+2.5f);
        return c.yaw()+MathHelper.wrapDegrees(way-c.yaw())*w;
    }

    // ───────────────────────────── where it comes down ─────────────────────────────

    /** For the capture tools: a point the local player's slam is aimed at whatever their screen shows (a filming camera is not theirs); null, the screen's middle. */
    public static Vec3d aimAt;
    /** The point the middle of this client's screen aims slam {@code c} at now (SlamGround.aimed, from its camera). */
    static Vec3d aimPoint(MinecraftClient mc,StandClient.Cast c){
        if(aimAt!=null)return aimAt;
        var cam=mc.gameRenderer.getCamera();
        return SlamGround.aimed(mc.world,mc.player,c.origin(),cam.getPos(),Vec3d.fromPolar(cam.getPitch(),cam.getYaw()));
    }
    /** Every tick: up in the air of the local player's own slam, before its dive's word has come, where the middle of the screen points is told to the server. */
    static void tellAim(MinecraftClient mc){
        var c=KnifeClient.slamOf(mc.player.getUuid());if(c==null||!Float.isNaN(stopped(c))||mc.world==null)return;
        var info=KnifeClient.slam(c.id());if(info==null||info.land!=null)return;
        float age=c.age(0);if(age<SlamRules.AIM_FROM-2||age>SlamRules.DIVE+6||!ClientPlayNetworking.canSend(XCharaUltimate.INPUT))return;
        Vec3d at=aimPoint(mc,c);
        var b=PacketByteBufs.create();b.writeByte(XCharaRules.AIM);b.writeDouble(at.x);b.writeDouble(at.y);b.writeDouble(at.z);
        ClientPlayNetworking.send(XCharaUltimate.INPUT,b);
    }
    /** From when in the air their own screen marks where it would come down (seconds). */
    static final float MARK0=LEAP+.25f;
    /**
     * The floor the local player's own slam would come down on now, for its marker: the server's word once the dive
     * has begun, until then what the middle of the screen aims at (found as the server will find it). Null if this is
     * not theirs, or not between the leap and the landing.
     */
    static Vec3d marker(Fx c){
        if(c.mc.player==null||!c.cast.caster().equals(c.mc.player.getUuid())||c.sec<MARK0||c.sec>=LAND||c.sec>=KnifeClient.until(c.cast.id()))return null;
        var info=KnifeClient.slam(c.cast.id());if(info==null)return null;
        if(info.land!=null)return info.land;
        return SlamGround.landing(c.mc.world,c.mc.player,c.cast.origin(),info.rise,aimPoint(c.mc,c.cast));
    }

    // ───────────────────────────── the body ─────────────────────────────

    private static R6.Pose on(double fw,double up,float yaw,float pitch,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf){
        return KnifeCombo.on(0,fw,up,yaw,pitch,0,rArm,blade,lArm,rr,rf,lr,lf);
    }
    /** In the air: the body leant {@code pitch} forward, its limbs and the knife by their ways in the world. */
    private static R6.Pose air(float yaw,float pitch,double[] rArm,double[] blade,double[] lArm,double[] rLeg,double[] lLeg){
        return R6.world(0,0,yaw,pitch,0,rArm,blade,lArm,rLeg,lLeg);
    }
    /** Bowed: {@link #on}, the head going down with the body (not held level over it). */
    private static R6.Pose bowed(double fw,double up,float yaw,float pitch,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf){
        return on(fw,up,yaw,pitch,rArm,blade,lArm,rr,rf,lr,lf).head(yaw,-pitch*.3f,0);
    }
    /**
     * In the air, the knife swung over in one hand: the body leant {@code pitch} forward, the knife's arm by its way
     * from its own shoulder in the body's frame ({right, up, forward}), the knife, the free arm and the legs by their
     * ways in the world; the eyes kept on where they come down ({@code look} degrees down).
     */
    private static R6.Pose wound(float pitch,double[] arm,double[] blade,double[] lArm,double[] rLeg,double[] lLeg,float look){
        R6.Pose p=air(0,pitch,d(60,0),blade,lArm,rLeg,lLeg);
        p.rArm=new R6.Dir(arm[0],arm[1],arm[2]).norm();
        return p.head(0,look-pitch,0);
    }
    /** The knife's arm in the body's frame: up and ahead as the knife comes up, straight over the head, back behind the head and the shoulder, and ahead again into the blow. */
    private static final double[] AHEAD={.08,.48,.36},OVERHEAD={.03,.60,.03},BEHIND={.06,.50,-.30},DOWN_AHEAD={.05,.40,.38};
    /**
     * The clip (the rise and the dive themselves are {@link #shift}'s: the clip is the body about its own feet).
     * 2.128.1, the user of 2.128.0's: "모션도 달라"; then, of what I read of the video again, "정확한 모션은 검을 어깨
     * 뒤까지 올려서 세게 내려치려는 모션으로 올라가는 거임", and "인체 구조상 도끼를 강하게 내려 찍을때 어떤 자세가
     * 나와야할까?"; and of the draft that came of it (both hands on the knife, the back arched): "저 자세에서 허리 그냥
     * 뒤로 안 젖히게 바꾸고 칼도 한 손으로 들어 등 뒤로 넘기는건 똑같아".
     * <ol>
     * <li>standing, the knife held out before them, its point down, as it lights; then bowed deep from the waist, the
     * knees bent, the knife low and level before them (the video's);</li>
     * <li>sprung up, and the wind up: the knife's arm brings it up before them, over the head and back behind the head
     * and the shoulder, the elbow up, in the one hand; the body stays upright (the waist not bent back: the user's),
     * the free arm out ahead of them for balance, the knees bent and the legs left behind; they rise so, their eyes
     * on where they will come down, and hang so at the top, the knife sinking back toward level behind them (never
     * under it: it is held up behind the shoulder, as the video's);</li>
     * <li>the drop: the body stretches up long, the knife swung up from behind to straight over the head (the video's
     * knife standing straight up as they fall, feet first: the chop on its way over), the legs coming down under
     * them;</li>
     * <li>the blow as they land: the trunk folds forward first, the arm comes over and straightens after it, the knife
     * last and fastest; landed deep, bent far over it, the knees bent, the whole weight on the knife in the ground
     * before them; held; and up again.</li>
     * </ol>
     * (2.128.0 crouched on its knees with the knife swept back, rose arched with one knee up and the knife out low,
     * and dived head first with the knife thrust down ahead; 2.128.1's drafts rose spread as a star, then stiff with
     * the knife only cocked, then with both hands on it and the back arched: not gone back to.)
     */
    static final Anim CLIP=Anim.joints(
        k(0,KnifeCombo.STANCE),
        // the knife held out before them, its point down
        k(.14,on(0,-.01,-3,1,d(-36,32),d(-12,20),d(-86,-10),.17,0,-.17,0)),
        k(.30,on(0,-.02,-4,2,d(-32,30),d(-15,16),d(-85,-12),.17,0,-.17,0)),
        // bowed deep from the waist, the knife low and level before them
        k(CROUCH,on(-.04,-.08,-4,14,d(-44,28),d(-9,20),d(-80,-18),.19,.03,-.19,-.03)),
        k(.64,bowed(-.14,-.27,-3,46,d(-62,22),d(3,25),d(-70,-28),.22,.06,-.22,-.04)),
        k(.86,bowed(-.17,-.33,-2,54,d(-66,20),d(5,27),d(-66,-32),.23,.07,-.23,-.05)),
        k(1.04,bowed(-.18,-.37,-2,57,d(-67,20),d(6,28),d(-64,-34),.23,.07,-.23,-.05)),
        // sprung: the body coming up as they push off, the knife coming up before them
        k(LEAP,on(-.04,-.05,-2,30,d(-34,30),d(24,18),d(-52,-40),.20,.03,-.20,-.03)),
        // off the ground: stretched long out of the push, leaning the way they go, the legs left under and behind
        k(LEAP+.07f,wound(16,AHEAD,d(50,8),d(-60,-40),d(-82,150),d(-84,-160),8)),
        k(1.28,wound(10,OVERHEAD,d(82,172),d(-36,-44),d(-76,170),d(-70,-172),18)),
        // the top of the first spring: slowing, the knees coming up under them, the knife gone over behind the shoulder
        k(1.42,wound(5,BEHIND,d(38,174),d(-22,-40),d(-66,150),d(-58,-165),28)),
        k(1.60,wound(3,BEHIND,d(28,171),d(-18,-38),d(-62,140),d(-54,-160),32).at(0,-.02,0)),
        // sinking a little: gathered for the second, the knees drawn up, leaning into it
        k(1.80,wound(8,BEHIND,d(24,170),d(-12,-34),d(-54,130),d(-48,-150),34).at(0,-.04,0)),
        // the second spring: the legs driven down and back, the body stretched and leaning the way they go
        k(1.93,wound(13,BEHIND,d(21,169),d(-48,-42),d(-84,165),d(-80,-170),34)),
        k(2.08,wound(9,BEHIND,d(18,168),d(-40,-40),d(-80,172),d(-76,-175),36)),
        // coming to the top: slowing, the body upright again, the legs swinging in under them
        k(2.36,wound(2,BEHIND,d(13,166),d(-24,-38),d(-72,165),d(-66,-172),38).at(0,.03,0)),
        k(2.62,wound(0,BEHIND,d(9,165),d(-22,-38),d(-70,160),d(-62,-170),40).at(0,-.03,0)),
        k(2.90,wound(-1,BEHIND,d(4,165),d(-18,-36),d(-66,155),d(-60,-168),40).at(0,.02,0)),
        // the dive: the knife swung up from behind to over the head, the body thrown into the way it dives, the legs trailing
        k(3.06,wound(6,OVERHEAD,d(84,172),d(-40,-40),d(-74,170),d(-70,-172),36)),
        k(3.22,wound(20,OVERHEAD,d(80,20),d(-56,-70),d(-66,176),d(-62,-176),30)),
        k(3.35,wound(24,DOWN_AHEAD,d(58,0),d(-52,-90),d(-74,120),d(-72,-130),26)),
        // the blow: folded far over it, the knife in the ground ahead, the whole weight on it
        k(LAND,bowed(.04,-.40,0,38,d(-54,4),d(-24,0),d(-34,-110),.22,.30,-.22,-.26)),
        k(LAND+.07f,bowed(.06,-.47,0,42,d(-56,4),d(-25,0),d(-30,-116),.22,.30,-.22,-.26)),
        k(LAND+.30f,bowed(.05,-.43,0,39,d(-54,4),d(-24,0),d(-34,-110),.22,.30,-.22,-.26)),
        k(FREE,bowed(.05,-.40,0,35,d(-54,5),d(-23,0),d(-36,-108),.22,.30,-.22,-.26)),
        // up, the knife drawn out
        k(FREE+.2f,on(.03,-.18,0,10,d(-64,30),d(-6,0),d(-60,-60),.20,.16,-.20,-.12)),
        k(FREE+.4f,on(.01,-.05,0,3,d(-76,60),d(-1,0),d(-80,-20),.18,.04,-.18,-.03)),
        k(END,KnifeCombo.STANCE)).stops(Anim.stop(LAND,.1,.1)).after(OverwriteSlam::fix);
    /** The ground under the clip's feet: where they stood until the leap, then as far under them as they have risen (and come down again), the ground again as they land. */
    static final IsosangLimbs.Ground GROUND=sec->{
        double a=sec*20;if(a<=SlamRules.LEAP||a>=SlamRules.LAND)return 0;
        // (it falls away from under their feet over the first ticks, not at the leap's own speed: the legs give
        // themselves to the air over a tenth of a second, not in a frame)
        return -SlamRules.RISE*SlamRules.risen(Math.min(a,SlamRules.DIVE))*(1-SlamRules.dived(a))*Ease.smooth((float)a,SlamRules.LEAP,SlamRules.LEAP+4);
    };
    /**
     * How much the legs tuck in the air: all of it wound up over the first spring and while they are still (the knees
     * bent behind them), next to none as the second spring throws the legs down straight, half hanging at the top,
     * next to nothing as they stretch out to drop (the video's legs are straight then), all of it again as the ground
     * comes up under them.
     */
    static double tuck(float sec){
        double thrust=.35,hang=.6,dive=.4;
        double t=1+(thrust-1)*Ease.smooth(sec,LEAP2-.02f,LEAP2+.1f)+(hang-thrust)*Ease.smooth(sec,2.08f,2.36f)+(dive-hang)*Ease.smooth(sec,2.95f,3.12f);
        return t+(1-t)*Ease.smooth(sec,LAND-.1f,LAND);
    }
    private static final Anim.Fix BENT=IsosangLimbs.natural(GROUND,OverwriteSlam::tuck);
    /** A frame of the clip as it is drawn: its elbows and knees bent (IsosangLimbs), the stance's straight limbs at its beginning and its end (as the knife's hits). */
    static R6.Frame fix(float sec,R6.Frame f){
        R6.Frame b=BENT.apply(sec,f);
        float rest=Math.max(1-Ease.smooth(sec,0,KnifeCombo.OUT_OF_REST),Ease.smooth(sec,END-KnifeCombo.INTO_REST,END));
        return rest<=0?b:R6.blend(b,KnifeCombo.AT_REST,Math.min(1,rest));
    }
    /** The fist and the knife's way {@code sec} seconds in, for a body whose feet are at {@code feet} facing {@code yaw}. */
    static Vec3d[] blade(Vec3d feet,float yaw,float sec){return KagenoClips.blade(feet,yaw,R6.poseOf(CLIP.frame(sec)));}

    // ───────────────────────────── the circle ─────────────────────────────

    /** The circle's colours: its neon fill, its rim's darker purple, the glow added over it. */
    static final int FILL=0xb878e4,RIM=0x7426d8,NEON=0xc45cff;
    /** How far over the ground it lies (blocks: clear of the ground's own surface as the effects' depth sees it). */
    static final double OVER=.06;
    /** How wide its rim's line is (blocks). */
    static final double RIM_WIDE=.26;
    /**
     * The circle on the ground about where a slam began: every piece of it (a block's top, cut at the rim: its corners,
     * x y z each), its rim's pieces (two points on the rim and how high the ground is there), and how high the ground
     * is at each block of it.
     */
    record Disc(List<double[]> tiles,List<double[]> rim,Map<Long,Double> tops){
        /** How high the ground of the circle is at (x, z), or NaN where it has none. */
        double top(double x,double z){Double t=tops.get(key(MathHelper.floor(x),MathHelper.floor(z)));return t==null?Double.NaN:t;}
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static final Map<UUID,Disc> DISCS=new LinkedHashMap<>();
    static void clear(){DISCS.clear();}
    /**
     * How high the ground the circle lies on is in the block column (x, z), for a circle begun at height {@code y}: the
     * first block's top from a little over there down to a little under (NaN: none, or a wall stands there).
     */
    static double top(World world,int x,int z,double y){
        var pos=new BlockPos.Mutable(x,MathHelper.floor(y+2.5),z);
        for(int i=0;i<7;i++){
            var shape=world.getBlockState(pos).getCollisionShape(world,pos);
            if(!shape.isEmpty())return i==0?Double.NaN:pos.getY()+shape.getMax(Direction.Axis.Y);
            pos.move(Direction.DOWN);
        }
        return Double.NaN;
    }
    /**
     * A block's top cut by the circle about ({@code cx}, {@code cz}): its corners {x, z} that lie within it (all four,
     * or the square cut along the circle's tangent there), or null if none of it does.
     */
    static double[][] cut(int x,int z,double cx,double cz,double radius){
        double[][] sq={{x,z},{x+1,z},{x+1,z+1},{x,z+1}};int in=0;
        for(var p:sq)if(Math.hypot(p[0]-cx,p[1]-cz)<=radius)in++;
        if(in==4)return sq;
        double mx=x+.5-cx,mz=z+.5-cz,far=Math.hypot(mx,mz);if(far<1e-6)return sq;
        double nx=mx/far,nz=mz/far;
        var out=new ArrayList<double[]>();
        for(int i=0;i<4;i++){
            double[] a=sq[i],b=sq[(i+1)%4];
            double da=(a[0]-cx)*nx+(a[1]-cz)*nz-radius,db=(b[0]-cx)*nx+(b[1]-cz)*nz-radius;
            if(da<=0)out.add(a);
            if(da<0&&db>0||da>0&&db<0){double u=da/(da-db);out.add(new double[]{a[0]+(b[0]-a[0])*u,a[1]+(b[1]-a[1])*u});}
        }
        return out.size()<3?null:out.toArray(new double[0][]);
    }
    /** The circle of slam {@code c} (found once, from the world as it is). */
    static Disc disc(Fx c){
        var d=DISCS.get(c.cast.id());if(d!=null)return d;
        World world=c.mc.world;if(world==null)return null;
        Vec3d from=c.cast.origin();double r=SlamRules.RADIUS;int bx=MathHelper.floor(from.x),bz=MathHelper.floor(from.z),n=(int)Math.ceil(r)+1;
        var tiles=new ArrayList<double[]>();var tops=new HashMap<Long,Double>();
        for(int x=bx-n;x<=bx+n;x++)for(int z=bz-n;z<=bz+n;z++){
            double[][] poly=cut(x,z,from.x,from.z,r);if(poly==null)continue;
            double top=top(world,x,z,from.y);if(Double.isNaN(top))continue;
            tops.put(key(x,z),top);
            double[] t=new double[poly.length*3];
            for(int i=0;i<poly.length;i++){t[i*3]=poly[i][0];t[i*3+1]=top+OVER;t[i*3+2]=poly[i][1];}
            tiles.add(t);
        }
        var rim=new ArrayList<double[]>();int segs=176;
        for(int i=0;i<segs;i++){
            double a=Math.PI*2*i/segs,b=Math.PI*2*(i+1)/segs,m=(a+b)*.5;
            Double top=tops.get(key(MathHelper.floor(from.x+Math.cos(m)*(r-.08)),MathHelper.floor(from.z+Math.sin(m)*(r-.08))));
            if(top!=null)rim.add(new double[]{a,b,top+OVER+.012});
        }
        d=new Disc(tiles,rim,tops);DISCS.put(c.cast.id(),d);
        while(DISCS.size()>8)DISCS.remove(DISCS.keySet().iterator().next());
        return d;
    }
    /**
     * How strongly the circle shows {@code sec} seconds in (0 or 1: it blinks on and blinks out, as a thing on a
     * screen), for a slam stopped short {@code stop} seconds in (NaN: not).
     */
    static float circle(float sec,float stop){
        float end=Float.isNaN(stop)?END:Math.min(END,stop);
        if(sec<CIRCLE||sec>=end)return 0;
        float s=sec-CIRCLE,left=end-sec;
        if(s>=.05f&&s<.09f)return 0;
        if(Float.isNaN(stop)&&left<.09f&&left>=.045f)return 0;
        return 1;
    }
    private static void tile(EffectMesh m,double[] t,int rgb,float alpha){
        int n=t.length/3;Vec3d a=new Vec3d(t[0],t[1],t[2]);
        for(int i=1;i+1<n;i+=2){
            Vec3d b=new Vec3d(t[i*3],t[i*3+1],t[i*3+2]),c2=new Vec3d(t[i*3+3],t[i*3+4],t[i*3+5]);
            Vec3d e=i+2<n?new Vec3d(t[i*3+6],t[i*3+7],t[i*3+8]):c2;
            m.quad(a,b,c2,e,rgb,alpha);
        }
    }
    private static void drawDisc(Fx c,EffectMesh m,boolean light,float on){
        var d=disc(c);if(d==null)return;
        Vec3d from=c.cast.origin();double r=SlamRules.RADIUS;
        // a slow breath of its light
        float breath=.9f+.1f*(float)Math.sin(c.sec*5.2f);
        for(var t:d.tiles())tile(m,t,light?NEON:FILL,light?.05f*on*breath:.82f*on);
        for(var s:d.rim()){
            double ca=Math.cos(s[0]),sa=Math.sin(s[0]),cb=Math.cos(s[1]),sb=Math.sin(s[1]);
            if(light){
                // its glow: brightest on the rim, fading out to either side of it
                double in=r-.9,out=r+.45;
                Vec3d a0=new Vec3d(from.x+ca*in,s[2],from.z+sa*in),b0=new Vec3d(from.x+cb*in,s[2],from.z+sb*in);
                Vec3d a1=new Vec3d(from.x+ca*r,s[2],from.z+sa*r),b1=new Vec3d(from.x+cb*r,s[2],from.z+sb*r);
                Vec3d a2=new Vec3d(from.x+ca*out,s[2],from.z+sa*out),b2=new Vec3d(from.x+cb*out,s[2],from.z+sb*out);
                float g=.5f*on*breath;
                m.vertex(a0,NEON,0);m.vertex(b0,NEON,0);m.vertex(b1,NEON,g);m.vertex(a1,NEON,g);
                m.vertex(a1,NEON,g);m.vertex(b1,NEON,g);m.vertex(b2,NEON,0);m.vertex(a2,NEON,0);
            }else{
                double in=r-RIM_WIDE;
                m.quad(new Vec3d(from.x+ca*in,s[2],from.z+sa*in),new Vec3d(from.x+cb*in,s[2],from.z+sb*in),
                    new Vec3d(from.x+cb*r,s[2],from.z+sb*r),new Vec3d(from.x+ca*r,s[2],from.z+sa*r),RIM,.95f*on);
            }
        }
    }

    // ───────────────────────────── the squares of light ─────────────────────────────

    /** How many squares of light rise off the circle through a slam, and their colours: their tops, their two kinds of side, and the glow about them. */
    static final int CUBES=80;
    static final int CUBE_TOP=0xffffff,CUBE_SIDE=0xfdeaff,CUBE_SHADE=0xf5d2fc,HALO=0xe860ff;
    /**
     * Square {@code j} of a slam {@code sec} seconds in: {how far east and south of where it began, how high over the
     * circle its middle is, its half sizes across, up and along, how far it is turned (radians), how much of it is
     * there (0 to 1)}, or null while it is not. A first flock comes with the leap, the rest all through the rise; each
     * comes out of the circle, rises, slowing a little, and shrinks away; all are gone as the circle goes.
     */
    static double[] cube(int j,int seed,float sec){
        float born=j%5<2?LEAP-.06f+.5f*(float)h(j,seed,2):LEAP+.3f+(LAND-.1f-LEAP)*(float)h(j,seed,2);
        float life=1.3f+1.3f*(float)h(j,seed,3),age=sec-born;
        if(age<0||age>=life)return null;
        double r=1.3+(SlamRules.RADIUS-3.5)*Math.pow(h(j,seed,4),.8),a=Math.PI*2*h(j,seed,5);
        double size=.13+.36*h(j,seed,6)*h(j,seed,7)+(j%9==0?.3:0);
        double hx=size*(.7+.6*h(j,seed,8)),hy=size*(.8+1.1*h(j,seed,9)),hz=size*(.7+.6*h(j,seed,10));
        double v=1.3+2.4*h(j,seed,11),up=.25+hy+v*age*(1-.18*age/life);
        float show=Ease.smooth(age,0,.1f)*(1-Ease.smooth(age,life-.22f,life))*(1-Ease.smooth(sec,END-.3f,END-.04f));
        return new double[]{Math.cos(a)*r,Math.sin(a)*r,up,hx,hy,hz,Math.PI*2*h(j,seed,12)+.6*age*(h(j,seed,13)-.5),show};
    }
    /** How far from the body's way up (from where it stood to as far forward as it goes) no square of light rises (blocks, and half the square). */
    static final double WAY_CLEAR=1.5;
    /** Whether a square at ({@code x}, {@code z}) from where they stood, {@code half} wide, is in the body's way up ({@code aheadX}, {@code aheadZ}: as far forward as it goes). */
    static boolean inTheWay(double x,double z,double aheadX,double aheadZ,double half){
        double l2=aheadX*aheadX+aheadZ*aheadZ,t=l2<1e-9?0:Math.max(0,Math.min(1,(x*aheadX+z*aheadZ)/l2));
        return Math.hypot(x-aheadX*t,z-aheadZ*t)<WAY_CLEAR+half;
    }
    private static void drawCubes(Fx c,EffectMesh m,boolean light,float stop){
        if(c.sec<LEAP-.1f||c.sec>=END)return;
        var d=disc(c);if(d==null)return;
        Vec3d from=c.cast.origin();int seed=seed(c.cast);
        float cutOff=Float.isNaN(stop)?1:1-Ease.smooth(c.sec,stop,stop+.15f);if(cutOff<=0)return;
        var way=info(c);double ax0=way==null?0:way.aheadX,az0=way==null?0:way.aheadZ;
        for(int j=0;j<CUBES;j++){
            var q=cube(j,seed,c.sec);if(q==null)continue;
            // (2.128.4) none where the body rises: they go up the way it goes forward, and a square before it hides it
            if(inTheWay(q[0],q[1],ax0,az0,Math.max(q[3],q[5])))continue;
            double x=from.x+q[0],z=from.z+q[1],top=d.top(x,z);if(Double.isNaN(top))continue;
            Vec3d mid=new Vec3d(x,top+q[2],z);
            float near=GlitchFx.near(mid,c.camera),show=(float)q[7]*cutOff;if(near<=0||show<=.01f)continue;
            double hx=q[3]*show,hy=q[4]*show,hz=q[5]*show;
            if(light){m.softDisc(mid,Math.max(hx,Math.max(hy,hz))*2.8,HALO,.26f*show*near);continue;}
            double cs=Math.cos(q[6]),sn=Math.sin(q[6]);
            Vec3d ax=new Vec3d(cs*hx,0,sn*hx),az=new Vec3d(-sn*hz,0,cs*hz),ay=new Vec3d(0,hy,0);
            Vec3d see=c.camera.subtract(mid);float al=.95f*near;
            // only the faces turned to the camera: three at most, none over another
            face(m,mid,ay,ax,az,see,CUBE_TOP,al);face(m,mid,ay.negate(),ax,az,see,CUBE_SHADE,al);
            face(m,mid,ax,ay,az,see,CUBE_SIDE,al);face(m,mid,ax.negate(),ay,az,see,CUBE_SIDE,al);
            face(m,mid,az,ax,ay,see,CUBE_SHADE,al);face(m,mid,az.negate(),ax,ay,see,CUBE_SHADE,al);
        }
    }
    private static void face(EffectMesh m,Vec3d mid,Vec3d n,Vec3d u,Vec3d v,Vec3d see,int rgb,float alpha){
        if(n.dotProduct(see)<=n.lengthSquared())return;
        Vec3d p=mid.add(n);
        m.quad(p.subtract(u).subtract(v),p.add(u).subtract(v),p.add(u).add(v),p.subtract(u).add(v),rgb,alpha);
    }

    // ───────────────────────────── the landing ─────────────────────────────

    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE;
    /** The landing's magenta, and the dark blue of its dark spikes and of the ground under it. */
    static final int MAGENTA=0xe63cff,NAVY=0x1c1470;
    /** Its burst: how many spikes (white, magenta and dark by turns), how long they last (seconds), how many bars flicker in it and for how long, how many pixels. */
    static final int SPIKES=30,BARS=26,PIXELS=44;
    static final float SPIKE_LIFE=.7f,BAR_LIFE=.85f,BURST_LIFE=1.0f;
    /** The ground: how far out the floor is thrown up in a crater's rim (blocks), how far the ripple runs, how long the floor stays up and when it sinks back (seconds), how many pieces are thrown. */
    static final double CRATER=3.4,RIPPLE=9;
    static final float GROUND_LIFE=2.6f,SINK0=1.2f,SINK1=2.3f;
    static final int CHUNKS=44;
    /** The dark of the ground right under it: how far out (blocks) and when it goes (seconds after the landing). */
    static final double DARK=2.3;
    static final float DARK0=.45f,DARK1=1.0f;
    private static KnifeClient.Slam info(Fx c){return KnifeClient.slam(c.cast.id());}
    /** Where slam {@code c} came down, once it has (it did not stop short before): else null. */
    static Vec3d landed(Fx c){
        if(c.sec<LAND||!KnifeClient.cameTo(c.cast,0))return null;
        var i=info(c);return i==null?null:i.land!=null?i.land:c.cast.origin();
    }
    /** Spike {@code j}: the angle it points at round the camera's view (radians), how long it is (blocks), how wide, and its kind (0 white, 1 magenta, 2 dark). */
    static double[] spike(int j,int seed){
        return new double[]{(j+.1+.8*h(j,seed,31))*Math.PI*2/SPIKES,1.8+3.4*h(j,seed,32)*h(j,seed,33)+(j%3==0?.9:0),.14+.18*h(j,seed,34),j%3};
    }
    /**
     * Bar {@code j} of the burst's flicker {@code s} seconds after the landing: {right, up of the burst's middle as the
     * camera sees it, its half width, its half height, its colour} or null while it is not there. They change place
     * thirty times a second at first and fewer of them are there as it goes.
     */
    static double[] bar(int j,int seed,float s){
        if(s<0||s>=BAR_LIFE)return null;
        int step=(int)Math.floor(s*(s<.3f?30:16));
        if(h(j+step*37,seed,41)>(1-Ease.smooth(s,.25f,BAR_LIFE))*.8)return null;
        double spread=1.1+1.6*Ease.smooth(s,0,.12f);
        double c=h(j+step*53,seed,46);
        return new double[]{(h(j+step*17,seed,42)-.5)*2*spread,(h(j+step*29,seed,43)-.5)*1.4*spread,.22+.6*h(j+step*11,seed,44),.06+.1*h(j+step*13,seed,45),
            c<.55?MAGENTA:c<.75?PURPLE:c<.9?WHITE:DEEP};
    }
    private static void ring(EffectMesh m,Vec3d mid,double radius,double wide,int rgb,float alpha,float outer){
        if(alpha<=0||radius<=0)return;int n=56;
        for(int i=0;i<n;i++){
            double a=Math.PI*2*i/n,b=Math.PI*2*(i+1)/n,in=Math.max(0,radius-wide);
            Vec3d a0=mid.add(Math.cos(a)*in,0,Math.sin(a)*in),b0=mid.add(Math.cos(b)*in,0,Math.sin(b)*in);
            Vec3d a1=mid.add(Math.cos(a)*radius,0,Math.sin(a)*radius),b1=mid.add(Math.cos(b)*radius,0,Math.sin(b)*radius);
            m.vertex(a0,rgb,0);m.vertex(b0,rgb,0);m.vertex(b1,rgb,alpha*outer);m.vertex(a1,rgb,alpha*outer);
        }
    }
    private static void drawLanding(Fx c,EffectMesh m,boolean light){
        Vec3d land=landed(c);if(land==null)return;
        float s=c.sec-LAND;if(s>=GROUND_LIFE)return;
        int seed=seed(c.cast);Vec3d ground=land.add(0,OVER+.03,0);
        // the ground under it: dark, jagged, until the floor has sunk back
        if(!light){
            float dark=Ease.smooth(s,0,.05f)*(1-Ease.smooth(s,DARK0,DARK1));
            if(dark>.01f){
                int n=20;Vec3d last=null;
                for(int i=0;i<=n;i++){
                    double a=Math.PI*2*(i%n)/n,r=DARK*(.6+.4*h(i%n,seed,51));
                    Vec3d p=ground.add(Math.cos(a)*r,0,Math.sin(a)*r);
                    if(last!=null)m.quad(ground,last,p,p,NAVY,.6f*dark);
                    last=p;
                }
            }
        }
        // rings along the ground
        for(int k=0;k<3;k++){
            float t=s-.05f*k;if(t<=0||t>=.6f)continue;
            double r=(k==0?7.5:k==1?11:5)*Ease.out(t,0,.6f);float a=(1-t/.6f);
            if(light)ring(m,ground,r,.8,k==2?WHITE:NEON,.4f*a,1);
            else ring(m,ground,r,.5,k==2?LILAC:PURPLE,.85f*a,1);
        }
        if(s>=BURST_LIFE)return;
        Vec3d at=land.add(0,.9,0);
        float near=GlitchFx.near(at,c.camera);if(near<=0)return;
        var f=GlitchFx.facing(c.camera,at);
        Vec3d mid=at.add(f[2].multiply(.6));
        if(light){
            // the white of it: a core, a star, and its glow
            if(s<.12f)m.softDisc(mid,1.5,WHITE,.8f*Ease.smooth(s,0,.02f)*(1-s/.12f)*near);
            if(s<.2f)m.star(mid,2.6,WHITE,.9f*Ease.smooth(s,0,.02f)*(1-s/.2f)*near);
            m.softDisc(mid,3.6,MAGENTA,.2f*Ease.smooth(s,0,.03f)*(1-Ease.smooth(s,.15f,.6f))*near);
        }
        if(s<SPIKE_LIFE)for(int j=0;j<SPIKES;j++){
            var q=spike(j,seed);Vec3d dir=f[0].multiply(Math.cos(q[0])).add(f[1].multiply(Math.sin(q[0])));
            float life=SPIKE_LIFE*(.6f+.4f*(float)h(j,seed,35));if(s>=life)continue;
            double len=q[1]*Ease.out(s,0,.07f);float fade=Ease.smooth(s,0,.02f)*(1-Ease.smooth(s,life*.35f,life))*near;
            Vec3d a0=mid.add(dir.multiply(.3)),a1=mid.add(dir.multiply(.3+len));int kind=(int)q[3];
            if(light){
                if(kind==0)m.needle(a0,a1,q[2],.22,WHITE,.95f*fade);
                else if(kind==1)m.needle(a0,a1,q[2]*2,.22,MAGENTA,.2f*fade);
            }else{
                if(kind==0)continue;
                m.needle(a0,a1,q[2]*1.5,.22,INK,.85f*fade);
                m.needle(a0,a1,q[2],.22,kind==1?MAGENTA:NAVY,.95f*fade);
            }
        }
        // the bars: a broken screen's
        for(int j=0;j<BARS;j++){
            var b=bar(j,seed,s);if(b==null)continue;
            Vec3d p=mid.add(f[0].multiply(b[0])).add(f[1].multiply(b[1])).add(f[2].multiply(.05));
            if(light)GlitchFx.bar(m,p,f,b[2]*1.3,b[3]*2.2,(int)b[4]==DEEP?PURPLE:(int)b[4],.22f*near);
            else{GlitchFx.bar(m,p,f,b[2]+.03,b[3]+.03,INK,.9f*near);GlitchFx.bar(m,p,f,b[2],b[3],(int)b[4],near);}
        }
        GlitchFx.pixels(m,at,new Vec3d(0,1,0),.9,11,.07,PIXELS,seed,s,light,c.camera);
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** Everything of the slam but the body and the knife, in one pass (the blended bodies, or the light). */
    static void draw(Fx c,EffectMesh m,boolean light){
        float stop=stopped(c.cast),until=KnifeClient.until(c.cast.id());
        float on=circle(c.sec,stop);
        if(on>0)drawDisc(c,m,light,on);
        drawCubes(c,m,light,stop);
        if(c.sec<until){
            int seed=seed(c.cast);Vec3d from=c.cast.origin();
            // the leap: a ring off the ground where they stood, thin lines of light going up after them
            float l=c.sec-LEAP;
            if(l>=0&&l<.45f){
                Vec3d g=from.add(0,OVER+.04,0);
                if(light){
                    ring(m,g,3.2*Ease.out(l,0,.4f),.8,NEON,.7f*(1-l/.45f),1);
                    for(int j=0;j<8;j++){
                        double a=Math.PI*2*(j+h(j,seed,61))/8,r=.5+.5*h(j,seed,62);
                        Vec3d p=from.add(Math.cos(a)*r,0,Math.sin(a)*r);double top=(2.5+3*h(j,seed,63))*Ease.out(l,0,.3f);
                        m.softTaper(p.add(0,top*.3,0),p.add(0,top,0),.12,.02,LILAC,.6f*(1-l/.45f));
                    }
                }else ring(m,g,3.2*Ease.out(l,0,.4f),.3,PURPLE,.8f*(1-l/.45f),1);
            }
            // the second spring, in the air (2.128.3): a smaller ring where their feet were, left behind under them
            float l2=c.sec-LEAP2;
            if(l2>=0&&l2<.4f){
                var first=info(c);Vec3d g=first==null?from.add(0,SlamRules.RISE*SlamRules.FIRST,0):planned(c.cast,first,SlamRules.LEAP2);
                if(light){
                    ring(m,g,2.0*Ease.out(l2,0,.35f),.5,NEON,.6f*(1-l2/.4f),1);
                    for(int j=0;j<6;j++){
                        double a=Math.PI*2*(j+h(j,seed,64))/6,r=.4+.4*h(j,seed,65);
                        Vec3d p=g.add(Math.cos(a)*r,0,Math.sin(a)*r);double top=(1.4+1.6*h(j,seed,66))*Ease.out(l2,0,.3f);
                        m.softTaper(p.add(0,top*.3,0),p.add(0,top,0),.1,.02,LILAC,.5f*(1-l2/.4f));
                    }
                }else ring(m,g,2.0*Ease.out(l2,0,.35f),.2,PURPLE,.7f*(1-l2/.4f),1);
            }
            // the dive: a streak of light down its line behind them
            var i=info(c);
            if(i!=null&&i.land!=null&&c.sec>DIVE&&c.sec<LAND+.25f&&light){
                float fade=1-Ease.smooth(c.sec,LAND,LAND+.25f);
                Vec3d head=planned(c.cast,i,Math.min(c.age,SlamRules.LAND)).add(0,1,0),tail=planned(c.cast,i,SlamRules.DIVE).add(0,1,0);
                if(head.squaredDistanceTo(tail)>.04){
                    m.softTaper(tail,head,.1,.9,NEON,.4f*fade);m.softTaper(tail.add(head.subtract(tail).multiply(.35)),head,.04,.3,WHITE,.6f*fade);
                }
            }
        }
        // where it would come down, for its user alone: a ring as wide as it strikes, a mark in its middle
        Vec3d mark=marker(c);
        if(mark!=null){
            float w=Ease.smooth(c.sec,MARK0,MARK0+.2f)*(.8f+.2f*(float)Math.sin(c.sec*14));
            Vec3d g=mark.add(0,OVER+.05,0);
            if(light){ring(m,g,SlamRules.BLAST,.5,WHITE,.6f*w,1);m.softDisc(g.add(0,.05,0),.5,WHITE,.6f*w);}
            else{ring(m,g,SlamRules.BLAST,.16,WHITE,.95f*w,1);ring(m,g,.32,.32,WHITE,.9f*w,1);}
        }
        drawLanding(c,m,light);
    }
    /**
     * How high the floor block {@code r} blocks from where it came down is heaved {@code s} seconds after the landing
     * (blocks), and how far it is tipped away from there (degrees), as {lift, tilt}: a crater's rim thrown up at once
     * (nothing right under them, most two blocks out), sinking back after; beyond it a ripple running out.
     */
    static double[] heave(double r,float s,double h,double h2){
        if(s<=0||s>=GROUND_LIFE)return new double[]{0,0};
        if(r<CRATER){
            double stand=MathHelper.clamp((r-.9)/.8,0,1);if(stand<=0)return new double[]{0,0};
            double rise=Ease.smooth(s,0,.07f),down=1-Ease.smooth(s,SINK0,SINK1);
            double crest=Math.sin(Math.PI*MathHelper.clamp((r-.6)/(CRATER-.6),0,1));crest*=crest;
            return new double[]{(.12+.75*crest+.25*h)*stand*rise*down,(12+24*h2)*(.4+.6*crest)*rise*down};
        }
        if(r>RIPPLE)return new double[]{0,0};
        double edge=1-Ease.smooth((float)r,(float)RIPPLE-2.5f,(float)RIPPLE);
        return new double[]{FxSolids.wave(r,s,.75,14,1.2)*(.7+.6*h)*edge,0};
    }
    /** One piece of the floor flung by the landing, {@code s} seconds after it: {where it is, its size, how far it has spun (degrees)}, or null once it is gone. */
    static double[] chunk(Vec3d land,int j,int seed,float s){
        float ss=s-.01f*(j%6);if(ss<0||ss>=2.2f)return null;
        double a=Math.PI*2*(j+h(j,seed,71))/CHUNKS*3,r=.8+2.2*h(j,seed,72);
        double size=(.18+.42*h(j,seed,73)*h(j,seed,74))*(1-Ease.smooth(ss,1.7f,2.2f));
        Vec3d out=new Vec3d(Math.cos(a),0,Math.sin(a)),from=land.add(out.multiply(r)).add(0,.2,0);
        Vec3d v=out.multiply(2+7*h(j,seed,75)).add(0,6+9*h(j,seed,76),0);
        Vec3d at=FxSolids.flight(from,v,land.y+size*.5,ss);
        return new double[]{at.x,at.y,at.z,size,(300+500*h(j,seed,77))*Math.min(ss,1.3)};
    }
    /** The floor itself in the landing: its blocks round where it came down drawn heaved up and tipped, a ripple of them running out, and pieces of it flung (drawn only: no block is changed). */
    static void solids(Fx c,FxSolids so){
        Vec3d land=landed(c);if(land==null)return;
        float s=c.sec-LAND;if(s>=GROUND_LIFE)return;
        int seed=seed(c.cast),y=(int)Math.floor(land.y-.5),reach=(int)Math.ceil(RIPPLE+1),bx=MathHelper.floor(land.x),bz=MathHelper.floor(land.z);
        for(int ix=bx-reach;ix<=bx+reach;ix++)for(int iz=bz-reach;iz<=bz+reach;iz++){
            double dx=ix+.5-land.x,dz=iz+.5-land.z,r=Math.hypot(dx,dz);if(r>RIPPLE||r<1e-3)continue;
            var hv=heave(r,s,Ease.hash(ix*7349+iz*911),Ease.hash(ix*131+iz*5923+17));
            if(hv[0]<=.02)continue;
            var pos=new BlockPos(ix,y,iz);var state=so.surface(pos);if(state==null)continue;
            // (tipped away from where it came down: about the level line square to the way out)
            so.lifted(pos,state,hv[0],(float)hv[1],new Vec3d(dz/r,0,-dx/r).multiply(-1));
        }
        var ground=so.ground(land.add(0,-.5,0));if(ground==null)return;
        for(int j=0;j<CHUNKS;j++){
            var q=chunk(land,j,seed,s);if(q==null||q[3]<=.02)continue;
            so.chunk(ground,new Vec3d(q[0],q[1],q[2]),q[3],(float)q[4],(float)(q[4]*.7),(float)(q[4]*.4));
        }
    }
    /** Its light on the world; and little afterglow through the dive (the body's light would leave copies of itself down its line). */
    static void frame(Fx c,FxFrame fr){
        float t=c.sec,stop=stopped(c.cast);
        if(t>=DIVE-.05f&&t<LAND+.12f)fr.afterglow(.06f);
        float on=circle(t,stop);
        if(on>0){
            Vec3d from=c.cast.origin();
            fr.light(from.add(0,1.2,0),0xa040ff,16,.55f*on);
        }
        if(t<KnifeClient.until(c.cast.id())&&t>=LIT&&t<END){
            var hb=blade(body(c),facing(c.cast,c.delta),t);
            fr.light(hb[0].add(hb[1].multiply(1.4)),0x8a2be2,5,.4f);
        }
        Vec3d land=landed(c);
        if(land!=null){
            float s=t-LAND;Vec3d at=land.add(0,1,0);
            if(s<.9f)fr.light(at,0xb04cff,28,2.6f*(1-s/.9f));
            if(s<SINK0+.5f)fr.light(at.add(0,-.5,0),0x8a2be2,9,.5f*(1-Ease.smooth(s,SINK0,SINK0+.5f)));
        }
    }
    /** How hard it shakes the camera: a knock as they leave the ground, hard as it lands (harder on someone). */
    static float shake(StandClient.Cast cast,float sec){
        float leap=sec<KnifeClient.until(cast.id())||KnifeClient.until(cast.id())>LEAP?.35f*KnifeCombo.bump(sec,LEAP,.08f)+.22f*KnifeCombo.bump(sec,LEAP2,.08f):0;
        if(!KnifeClient.cameTo(cast,0))return leap;
        int[] told=KnifeClient.struck(cast.id(),0);
        return leap+(told!=null&&told.length>0?3.6f:3.0f)*KnifeCombo.bump(sec,LAND,.18f);
    }
}
