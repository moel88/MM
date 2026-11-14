package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.RainRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * X Chara's H with their hack off, the rain of knives, drawn (2.132.0, user request, no video: "h를 쓰면 달려가다가 앞에
 * 적이 있으면 적을 찌르고 수많은 q활성화 검들이 사방에서 날아와서 적을 맞추는 스킬", "검들이 쏟아지는 수준으로 많이
 * 날아와야해 그리고 달려간다음에 검이 다 쏟아질때까지 적을 찌르고 있는거야"; the server's XCharaAttack.rain, RainRules).
 * Two skills of the knife's pipeline that follow one another (KnifeClient's swings; KnifeCombo's passes hand them on
 * here): XCharaCombo.RUSH the run, XCharaCombo.RAIN the stab and the rain. All of how it looks is mine.
 * <ul>
 * <li>The run ({@link #RUN}): thrown forward off the back foot, the body far over its strides, the knife trailing
 * behind and up, the free arm pumping; drawn along RainRules.ran from where it began ({@link #shift}: the server keeps
 * them where they began, as the three cuts' going forward). With no one met it brakes onto the front foot and
 * stands.</li>
 * <li>The stab ({@link #STAB}): taken up from the run as it is on screen; the knife brought round to the hip as the
 * last of the way is covered (RainRules.lunged, from where the run had them to where the server said), then driven in
 * from the ground up, the whole body behind it. It lights as the hack lights it as it goes in ({@link #knife}) and
 * stays in the one stabbed. Then (2.132.1, user requests: "버틸때 양손으로 버티고 정자세로 서서 버텨", "서서 버티는데
 * 버티고 있다는 자세를 만들어줘 힘 줘서 버티고 있다는") they come up onto it and bear on it standing, both hands on its rod
 * ({@link #bracing}, {@link #gripped}), leaning into it and straining (bearing in and easing off by turns, shaking:
 * {@link #strain}); the body turns to the one it goes into ({@link #facing}). As the last volley lands they bear in
 * hardest; the other hand lets go, the knife is drawn out, goes black, and they step back.</li>
 * <li>The rain (2.132.3, user requests: "칼이 너무 빠르게 떨어져", "그냥 저 칼이 다시 플레이어를 향하게 바꾸자", answered
 * "2.132.0처럼 사방에서"; 2.132.1 had them fall straight down like rain, 2.132.2 over 15 blocks round): RainRules.BLADES
 * knives lit as the hack lights them, {@link #SIZE} the size they are in the hand (2.132.2: "검 크기도 조금만 더
 * 키우자"), RainRules.PER a tick, coming to be in the air all round the one stabbed ({@link #from}, {@link #out}: none
 * from where its user stands unless from high), aimed at them, drawn back a little and flying in quicker and quicker
 * ({@link #pose}), each standing in them a moment and gone. They are drawn flat by the effects' layer (RainFx: the
 * knife's own model is too many boxes for hundreds at once), with all the rest of what it adds ({@link #draw}), and the
 * screen inside the rain.</li>
 * </ul>
 * Its sounds are ones the game has (the user's: "있는 소리 재사용"; {@link #cues}): the jab's thrust as the knife goes
 * in, the knives shot's coming and strike through the rain. Everything is a function of the skills' own clocks and
 * what the server told.
 */
public final class BladeRain {
    private BladeRain(){}
    /** How long the run and the stab are drawn for (seconds): what the last volley leaves is gone by then. */
    public static final float RUSH_LIFE=RainRules.RUSH_LENGTH/20f+.1f,LIFE=RainRules.LENGTH/20f+.5f;
    static final float STAB_AT=RainRules.STAB/20f,LAST_AT=RainRules.LAST/20f,OUT_AT=RainRules.WITHDRAW/20f;

    // ───────────────────────────── the body ─────────────────────────────

    private static R6.Pose on(double rt,double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf){
        return KnifeCombo.on(rt,fw,up,yaw,pitch,roll,rArm,blade,lArm,rr,rf,lr,lf);
    }
    /** A stride of the run: one foot on the ground under the body (the right if {@code right}, {@code under} ahead of where they are), the other knee swung through ahead. */
    private static R6.Pose passing(boolean right,double up,float pitch,double[] lArm,double under){
        R6.Pose p=on(0,.27,up,-11,pitch,0,d(-23,165),d(35,176),lArm,.17,right?under:.2,-.18,right?.2:under);
        if(right)p.lLeg=R6.local(p,-42,0);else p.rLeg=R6.local(p,-42,0);
        return p;
    }
    /** The run's strides: left foot out ahead and the right behind, and the other way. */
    private static R6.Pose strideA(){return on(0,.26,-.30,-12,54,0,d(-24,164),d(34,176),d(-12,-6),.17,-.64,-.18,.56);}
    private static R6.Pose strideB(){return on(0,.28,-.31,-10,56,0,d(-22,166),d(36,176),d(-40,-150),.17,.58,-.18,-.62);}
    private static Anim.Fix rested(float end,boolean fromRest){
        Anim.Fix bent=IsosangLimbs.natural(IsosangLimbs.FLOOR);
        return (sec,f)->{
            R6.Frame b=bent.apply(sec,f);
            float rest=Math.max(fromRest?1-Ease.smooth(sec,0,KnifeCombo.OUT_OF_REST):0,Ease.smooth(sec,end-KnifeCombo.INTO_REST,end));
            return rest<=0?b:R6.blend(b,KnifeCombo.AT_REST,Math.min(1,rest));
        };
    }
    /**
     * The run: off the back foot into four long strides (a foot down every eighth of a second, the other knee swung
     * through between), the knife swung out round the right side to trail behind and up; with no one met, braked onto
     * the front foot and stood up.
     */
    static final Anim RUN=run();
    private static Anim run(){
        float to=RainRules.RUN_TO/20f,end=RainRules.RUSH_LENGTH/20f;
        return Anim.joints(
            k(0,KnifeCombo.STANCE),
            // loaded: sunk over the back foot, the knife starting round the right side
            k(.05f,on(0,.07,-.11,-6,15,0,d(-70,60),d(4,50),d(-60,-10),.17,-.16,-.18,.20)),
            k(.11f,on(0,.19,-.23,-10,40,0,d(-42,128),d(18,138),d(-32,-8),.17,-.48,-.18,.44)),
            k(.17f,strideA()),
            k(.2325f,passing(false,-.27,55,d(-26,-70),.02)),
            k(.295f,strideB()),
            k(.3575f,passing(true,-.27,55,d(-26,-70),.02)),
            k(.42f,strideA()),
            k(.4825f,passing(false,-.27,55,d(-26,-70),.02)),
            k(.545f,strideB()),
            k(.6075f,passing(true,-.28,50,d(-30,-60),.04)),
            // braking: the left foot thrown out ahead, the body coming up and back over it
            k(to,on(.02,.10,-.24,-18,26,2,d(-36,132),d(16,150),d(-30,-14),.19,-.22,-.20,.46)),
            k(to+.10f,on(.02,-.02,-.17,-12,6,1,d(-54,96),d(8,70),d(-56,-16),.20,-.12,-.20,.36)),
            k(end-.12f,on(.01,-.02,-.07,-4,0,0,d(-76,60),d(3,24),d(-78,-14),.20,-.03,-.20,.14)),
            k(end,KnifeCombo.STANCE)).after(rested(end,true));
    }
    /** The feet as the stab stands: the right out ahead under the knife, the left behind. */
    private static final double RR=.15,RF=.92,LR=-.22,LF=-.34;
    /**
     * The feet as they bear on it (2.132.1, user requests: "버틸때 양손으로 버티고 정자세로 서서 버텨", then "서서 버티는데
     * 버티고 있다는 자세를 만들어줘 힘 줘서 버티고 있다는"): standing braced, a little wider than at rest, the right
     * ahead under the knife (its knee bent over it), the left back behind the hips to push from (its leg near straight):
     * {right foot right, ahead, left foot right, ahead}.
     */
    static final double BR=.24,BF=.80,BL=-.27,BB=.06;
    /**
     * The knife in them, borne on with both hands and straining (2.132.1): standing, the knees a little bent and set,
     * the body square to them and leaning into the knife, the head down, both hands on its rod low before the body (the
     * other behind the knife's, {@link #gripped}), the knife level into them; {@code push} 0 easing off a little, 1
     * bearing in hard (leaning further in and sinking a little, the arms driving out). The strain's shaking is
     * {@link #shaking}.
     */
    static R6.Pose bracing(double push){
        R6.Pose p=on(0,.50+.05*push,-.10-.025*push,(float)(1+1.5*push),(float)(12+4*push),0,d(-34+4*push,-30),d(-3,0),d(-38,28),BR,BF,BL,BB);
        // (the head down at them: this far under level, whatever the body's lean)
        p.headPitch=-p.pitch+(float)(13+3*push);
        return p;
    }
    /**
     * The stab and the rain: from the run (whatever stride it is in: KagenoClips takes it up from the frame on screen)
     * the knife comes round the right side to the hip, its point on them, the body side-on over the back foot; then
     * the back leg drives, the hips and the shoulder turn in and the arm goes out last, in on STAB. It is driven a
     * little further, the free arm thrown back; then (2.132.1) they come up onto it: the back foot swung through and set
     * down beside the front, the body rising square to them, the other hand coming onto the knife's rod; and they bear
     * on it so, standing at attention with both hands on it, until the last volley. Then the other hand lets go, the
     * knife is torn out back past the hip and they step away.
     */
    static final Anim STAB=stab();
    private static Anim stab(){
        float end=RainRules.LENGTH/20f,hold=RainRules.HOLD/20f;
        var keys=new ArrayList<Anim.Key>();
        keys.add(k(0,strideA()));
        // the last strides: the knife round to the hip, the body turning side-on and sinking back
        keys.add(k(.07f,on(-.02,.20,-.31,-22,34,1,d(-34,150),d(26,128),d(-8,-12),.16,.10,-.21,.54)));
        keys.add(k(.14f,on(-.05,.13,-.31,-32,18,2,d(-48,140),d(12,56),d(-2,-12),.16,.30,-.21,.30)));
        keys.add(k(.21f,on(-.08,.08,-.32,-40,10,3,d(-50,150),d(7,-7),d(4,-8),RR,.40,LR,LF)));
        // a breath wound; then let go from the ground up: the hips first, the arm after them
        keys.add(k(.245f,on(-.08,.07,-.33,-41,10,3,d(-50,152),d(7,-7),d(5,-8),RR,.40,LR,LF)));
        keys.add(k(.275f,on(-.05,.18,-.33,-27,12,1,d(-54,128),d(7,-7),d(-2,-20),RR,.62,LR,LF)));
        keys.add(k(.315f,on(-.01,.38,-.35,-2,18,-2,d(-42,40),d(6,-6),d(-12,-92),RR,.82,LR,LF)));
        // in: the whole body out behind the knife
        keys.add(k(STAB_AT,on(.01,.54,-.36,19,22,-3,d(-16,-10),d(5,-5),d(-16,-136),RR,RF,LR,LF)));
        // driven further, the free arm thrown back
        keys.add(k(STAB_AT+.05f,on(.02,.66,-.37,30,25,-3,d(-9,-24),d(4,-4),d(-6,-112),RR,RF,LR,LF)));
        // (2.132.1) braced on it: the back foot brought up along the ground to push from (the front drawn back a little
        // under them), the body turning square to them and leaning in, the other hand coming round onto the rod
        keys.add(k(STAB_AT+.13f,on(.01,.62,-.26,17,19,-2,d(-24,-27),d(1,-2),d(-20,-78),RR,.86,LR,-.12)));
        keys.add(k(STAB_AT+.21f,on(0,.55,-.12,6,15,-1,d(-33,-29),d(-2,0),d(-34,-6),BR,.82,BL,.03)));
        keys.add(k(STAB_AT+.29f,bracing(1)));
        // borne on through the rain, straining: easing off a little and bearing in hard by turns
        float t=STAB_AT+.29f;boolean in=false;
        while(t+.3f<LAST_AT-.1f){t+=.3f;keys.add(k(t,bracing(in?1:.35)));in=!in;}
        // the last volley: bearing in hardest
        keys.add(k(LAST_AT-.08f,bracing(.4)));
        keys.add(k(LAST_AT+.04f,bracing(1.6)));
        keys.add(k(OUT_AT,bracing(1)));
        // torn out: the other hand off it, the hips back first, the knife after them past the hip, the right foot stepping back
        keys.add(k(OUT_AT+.11f,on(-.03,.36,-.24,-12,10,1,d(-44,60),d(5,6),d(-40,-30),.19,.16,BL,BB)));
        keys.add(k(hold,on(-.03,.18,-.14,-14,4,1,d(-52,100),d(6,30),d(-60,-24),.18,.06,-.20,.22)));
        keys.add(k(end-.14f,on(-.01,.04,-.06,-5,1,0,d(-76,60),d(3,20),d(-78,-14),.19,.02,-.20,.10)));
        keys.add(k(end,KnifeCombo.STANCE));
        return Anim.joints(keys.toArray(new Anim.Key[0])).stops(Anim.stop(STAB_AT,.05,.07)).after(gripped(rested(end,false)));
    }
    /** How much of the other hand is on the knife's rod {@code sec} seconds into the stab (2.132.1): coming onto it as they come up onto it, off it as it is torn out. */
    static float both(float sec){return Ease.smooth(sec,STAB_AT+.05f,STAB_AT+.27f)*(1-Ease.smooth(sec,OUT_AT+.01f,OUT_AT+.09f));}
    /**
     * A frame of the stab as it is drawn with the other hand on the knife's rod ({@link #both}): behind the knife's hand
     * along the knife (GiantCleave.GRIP_BACK, as the grown knife's grip), that arm set by where the rod is
     * (IsosangLimbs.leftHandAt).
     */
    /**
     * How much they shake with the strain {@code sec} seconds into the stab (0 to 1): as they bear on it with both hands,
     * harder as they bear in (the keys' pushes, every 0.3 seconds) and with the last volley, gone as it is torn out.
     */
    static float strain(float sec){
        float on=both(sec);if(on<=0)return 0;
        double since=(sec-(STAB_AT+.29f))/.6;double push=.5+.5*Math.cos(2*Math.PI*since);
        return on*(float)(.65+.35*push)*(1+.5f*(float)KnifeCombo.bump(sec,LAST_AT,.12f));
    }
    /** Their body shaking with the strain {@code sec} seconds into the stab: small and quick, never the same twice (a few beats, none a whole number of the others), as {fw, up, rt, yaw, pitch, roll} to add to the frame. */
    static double[] shaking(float sec){
        float st=strain(sec);if(st<=0)return null;
        double w=sec*2*Math.PI;
        return new double[]{.005*st*Math.sin(w*9.3),.006*st*Math.sin(w*11.7+1),.004*st*Math.sin(w*8.1+2),
            .9*st*Math.sin(w*10.3+.5),1.3*st*Math.sin(w*12.1+.2),.8*st*Math.sin(w*13.7+1.3)};
    }
    private static Anim.Fix gripped(Anim.Fix inner){
        return (sec,f)->{
            R6.Frame b=inner.apply(sec,f);
            float g=both(sec);if(g<=0)return b;
            double[] sh=shaking(sec);
            if(sh!=null)b=new R6.Frame(b.joints(),b.grip(),b.fw()+sh[0],b.up()+sh[1],b.rt()+sh[2],(float)(b.yaw()+sh[3]),(float)(b.pitch()+sh[4]),(float)(b.roll()+sh[5]));
            R6.Pose p=R6.poseOf(b);double r=R6.armReach(p.rElbow),l=R6.armReach(p.lElbow);
            double x=.3125+p.rArm.rt()*r-p.blade.rt()*GiantCleave.GRIP_BACK,y=1.375+p.rArm.up()*r-p.blade.up()*GiantCleave.GRIP_BACK,z=p.rArm.fw()*r-p.blade.fw()*GiantCleave.GRIP_BACK;
            if(g>=1)return IsosangLimbs.leftHandAt(b,x,y,z);
            // (on its way there: the hand along the straight line from where the key has it to the rod, so it goes
            // there and does not swing round)
            double hx=-.3125+p.lArm.rt()*l,hy=1.375+p.lArm.up()*l,hz=p.lArm.fw()*l;
            R6.Frame two=IsosangLimbs.leftHandAt(b,hx+(x-hx)*g,hy+(y-hy)*g,hz+(z-hz)*g);
            return R6.blend(b,two,Ease.smooth(g,0,.35f));
        };
    }
    static Anim clipOf(int combo){return combo==RUSH?RUN:STAB;}

    // ───────────────────────────── where the body is ─────────────────────────────

    /** Where the run's user's feet are drawn {@code age} ticks in, from {@code from} toward {@code to}. */
    static Vec3d ran(Vec3d from,Vec3d to,float age){
        double far=Math.hypot(to.x-from.x,to.z-from.z);if(far<1e-6)return from;
        double g=RainRules.ran(age,far)/far;
        return new Vec3d(from.x+(to.x-from.x)*g,from.y+(to.y-from.y)*g,from.z+(to.z-from.z)*g);
    }
    /** Where the stab's user's feet are drawn {@code age} ticks in, from where the run had them to where they stab from. */
    static Vec3d lunged(Vec3d from,Vec3d to,float age){
        double g=RainRules.lunged(age);
        return new Vec3d(from.x+(to.x-from.x)*g,from.y+(to.y-from.y)*g,from.z+(to.z-from.z)*g);
    }
    /** How much the body is held to the plan {@code age} ticks in, the server taking them there at {@code there}: all of it until then; let go a little after (another client has seen them come there by then). */
    static float held(float age,int there){return 1-Ease.smooth(age,there+5,there+10);}
    /** Where the user of {@code c} (a run or a stab) is drawn {@code age} ticks in by its plan, or null with none told. */
    static Vec3d planned(StandClient.Cast c,float age){
        var way=KnifeClient.rainWay(c.id());if(way==null)return null;
        if(c.combo()==RUSH)return ran(way.from(),way.to(),age);
        Vec3d at=lunged(way.from(),way.to(),age);
        // (the stab is heard of a tick or so after the run was caught: the body goes on along the run's own way and
        // comes over to the stab's in its first ticks, where it would jump that tick's way ahead)
        float w=Ease.smooth(age,TAKES0,TAKES1);
        if(w<1){
            var run=KnifeClient.ranBefore(c.id());var ran=run==null?null:KnifeClient.rainWay(run.id());
            if(ran!=null)at=ran(ran.from(),ran.to(),run.age(0)-c.age(0)+age).lerp(at,w);
        }
        return at;
    }
    /** Over which ticks of the stab the body comes over from the run's way to the stab's. */
    static final float TAKES0=.5f,TAKES1=3.2f;
    /**
     * How far from where the user really is their body is drawn this frame (their renderer's offset, BeatdownRenderMixin:
     * their model, name and shadow's place go together): along the run, then the stab's last way, and where that ends
     * until they are really there. None for one that stopped short (a run the stab took over from is one).
     */
    public static Vec3d shift(StandClient.Cast c,float delta){
        if(c==null||!rain(c.combo())||c.skill()!=SKILL||KnifeClient.until(c.id())!=Float.MAX_VALUE)return Vec3d.ZERO;
        float age=c.age(delta),w=held(age,c.combo()==RUSH?RainRules.RUN_TO:RainRules.ARRIVE);if(w<=0)return Vec3d.ZERO;
        Vec3d at=planned(c,age);if(at==null)return Vec3d.ZERO;
        var mc=MinecraftClient.getInstance();var owner=mc.world==null?null:mc.world.getPlayerByUuid(c.caster());if(owner==null)return Vec3d.ZERO;
        return at.subtract(owner.getLerpedPos(delta)).multiply(w);
    }
    /** The stab's body turns to the one it goes into at most this far from the run's way (degrees), over this many ticks from when this client knows whom (as the knife comes round to the hip). */
    static final float TURN=40,TURN_TICKS=4;
    /** Whom a stab goes into as this client knows it ({@code id}, -1: no one), and from when (ticks into it) its body turns to them from what turn ({@code from}, degrees left of the run's way). */
    private record Aim(int id,float since,float from){}
    private static final java.util.Map<UUID,Aim> AIMS=new java.util.LinkedHashMap<>();
    /** The one in the stab's way from where it is made (the server's rule, XCharaCombo.lane: the nearest ahead), as this client finds them; -1: no one. */
    static int inTheWay(StandClient.Cast c){
        var mc=MinecraftClient.getInstance();var way=KnifeClient.rainWay(c.id());if(mc.world==null||way==null)return -1;
        Vec3d at=way.to(),f=Vec3d.fromPolar(0,c.yaw()),right=new Vec3d(-f.z,0,f.x);var user=mc.world.getPlayerByUuid(c.caster());
        int best=-1;double near=Double.MAX_VALUE;
        for(var e:mc.world.getEntitiesByClass(LivingEntity.class,new net.minecraft.util.math.Box(at,at).expand(RainRules.REACH[1]+2),
            e->e!=user&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);double ahead=d.dotProduct(f);
            if(!dev.sangwoo.celestialfall.xchara.XCharaCombo.lane(RAIN,0,ahead,d.dotProduct(right),d.y,e.getWidth()*.5))continue;
            if(ahead<near){near=ahead;best=e.getId();}
        }
        return best;
    }
    /** How far the stab's body is turned from the run's way {@code age} ticks in, as {@code a} has it (degrees, left +): to the one it goes into, at most TURN. */
    private static float turnOf(StandClient.Cast c,Aim a,float age,float delta){
        float want=0;
        var mc=MinecraftClient.getInstance();var way=KnifeClient.rainWay(c.id());
        if(a.id()>=0&&way!=null&&mc.world!=null&&mc.world.getEntityById(a.id()) instanceof LivingEntity e){
            Vec3d to=e.getLerpedPos(delta).subtract(way.to());
            if(to.horizontalLengthSquared()>=.25)want=net.minecraft.util.math.MathHelper.clamp(net.minecraft.util.math.MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-to.x,to.z))-c.yaw()),-TURN,TURN);
        }
        return a.from()+(want-a.from())*Ease.smooth(age,a.since(),a.since()+TURN_TICKS);
    }
    /**
     * The way the stab's body is drawn facing (degrees, 2.132.1: they stand square to the one they bear on): the run's,
     * turned to the one the knife goes into (at most TURN either way): as this client finds them in its way from its
     * first frame (as the knife comes round to the hip), then as the server says (the knife is in a tick or so before
     * that is heard; if it names another, or no one, the body turns on to them from where it was).
     */
    public static float facing(StandClient.Cast c,float delta){
        if(c.combo()!=RAIN)return c.yaw();
        float age=c.age(delta);Aim a=AIMS.get(c.id());
        int[] told=KnifeClient.struck(c.id(),0);
        if(told!=null){
            int id=told.length==0?-1:told[0];
            if(a==null||a.id()!=id){a=new Aim(id,a==null?0:age,a==null?0:turnOf(c,a,age,delta));AIMS.put(c.id(),a);}
        }else if(a==null){a=new Aim(inTheWay(c),0,0);AIMS.put(c.id(),a);}
        while(AIMS.size()>64)AIMS.remove(AIMS.keySet().iterator().next());
        return c.yaw()+turnOf(c,a,age,delta);
    }
    /** How far from where this player really is their body is drawn now, in their rain of knives (null: in none, or nowhere else): their own camera goes with it (BotandCamera). */
    public static Vec3d offset(UUID id,float delta){
        var c=KnifeClient.rainOf(id);if(c==null)return null;
        Vec3d s=shift(c,delta);return s.lengthSquared()<1e-8?null:s;
    }

    // ───────────────────────────── the knife in the hand ─────────────────────────────

    private static float stopped(StandClient.Cast c){return GiantCleave.stopped(c);}
    /** The knife's look {@code sec} seconds into the stab (stopped short {@code stop} seconds in, NaN: not), as the hack's {on, seconds since it changed}, or null while it is its own black: lit as it goes in, out as it is drawn out (or as it stops short). */
    static float[] lit(float sec,float stop){
        if(sec<STAB_AT)return null;
        float out=Float.isNaN(stop)?OUT_AT:Math.min(OUT_AT,stop);
        if(out<STAB_AT)return null;
        if(sec>=out){float t=sec-out;return t<HackLook.OFF?new float[]{0,t}:null;}
        return new float[]{1,sec-STAB_AT};
    }
    /** This player's knife as their stab has it this frame (the hack's look, HackMode.knifeLook), or null if they are in none or it is black. */
    public static HackLook.Frame knife(UUID id,float delta){
        var c=KnifeClient.rainOf(id);if(c==null||c.combo()!=RAIN)return null;
        float[] l=lit(c.age(delta)/20f,stopped(c));if(l==null)return null;
        return new HackLook.Frame(l[0]>0,l[1],HackMode.clock(delta)/20f,(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>17));
    }

    // ───────────────────────────── the knives of the rain ─────────────────────────────
    // (2.132.3, user requests: "칼이 너무 빠르게 떨어져", then "그냥 저 칼이 다시 플레이어를 향하게 바꾸자", asked and
    // answered "2.132.0처럼 사방에서": not raining straight down out of the sky over the wide ground any more (2.132.1,
    // 2.132.2) but as 2.132.0's: coming to be in the air all round the one stabbed, aimed at them, and flying in. Slower
    // than 2.132.0's, which darted in over an eighth of a second; still drawn flat (RainFx.blade), a little bigger, many)

    /** A knife of the rain is this much of the knife in the hand (2.132.2: .5 to .6, "검 크기도 조금만 더 키우자"); so its point is this far from its grip (blocks). */
    static final double SIZE=.6,TIP=ShotRules.TIP*SIZE;
    /**
     * A knife comes to be this many ticks before it lands, taking IN of them to come, and flies the last FLY of them
     * (2.132.3, "칼이 너무 빠르게 떨어져": 2.132.0's flew the last 2.4, darting in; these come on from standing and are
     * at most some 1.7 blocks a tick, as they go in).
     */
    static final float BEFORE=7.5f,IN=1.5f,FLY=6f;
    /** How it comes on as it flies: so much of the way gone {@code u} of the way through its flight, u to this power (quicker and quicker, from standing). */
    static final double PACE=1.5;
    /** One that has landed stands in them this long (ticks), gone over the last GOING of it; one not yet landed as the rain stops short is gone over VANISH. */
    static final float STUCK=3,GOING=1.5f,VANISH=2;
    /** How far out from the point it aims at a knife's point comes to be (blocks), and how high and low about them (degrees over level). */
    static final double NEAR=4.0,FAR=6.4;static final double LOWEST=4,HIGHEST=68;
    /** No knife comes from within this of where its user stands, seen from the one stabbed, unless from this high over them (degrees). */
    static final double CLEAR=42,OVER=38;
    /** How far past the point it aims at a knife's point goes in (blocks). */
    static final double IN_DEEP=.22;
    private static double h(int k,int seed,int salt){return GlitchFx.h(k,0,seed,salt);}
    /** A seed for this stab's knives (the same on every client: its start). */
    static int seed(StandClient.Cast c){return (int)(c.start()*31+17);}
    /** Whether knife {@code k} is its volley's own (one of each volley, as many as the server's blows): it goes in with more. */
    static boolean onThem(int k){return k%RainRules.PER==0;}
    /**
     * The way knife {@code k} comes from, seen from the one stabbed (a unit vector, out from them): round them by the
     * golden angle from one to the next (so each comes from far off the last), as high as chance has it between LOWEST
     * and HIGHEST; one that would come from where its user stands ({@code user}: the level way from the one stabbed to
     * them) comes from over them instead.
     */
    static Vec3d from(int k,int seed,Vec3d user){
        double az=k*2.39996323+h(k,seed,1)*.6,lo=Math.sin(Math.toRadians(LOWEST)),hi=Math.sin(Math.toRadians(HIGHEST));
        double sin=lo+(hi-lo)*h(k,seed,2),cos=Math.sqrt(1-sin*sin);
        double x=Math.cos(az),z=Math.sin(az);
        if(user!=null&&user.horizontalLengthSquared()>1e-8){
            Vec3d u=new Vec3d(user.x,0,user.z).normalize();
            if(x*u.x+z*u.z>Math.cos(Math.toRadians(CLEAR))&&sin<Math.sin(Math.toRadians(OVER))){
                sin=Math.sin(Math.toRadians(OVER+(HIGHEST-OVER)*h(k,seed,3)));cos=Math.sqrt(1-sin*sin);
            }
        }
        return new Vec3d(x*cos,sin,z*cos);
    }
    /** How far out knife {@code k}'s point comes to be (blocks). */
    static double out(int k,int seed){return NEAR+(FAR-NEAR)*h(k,seed,4);}
    /** The point of the one stabbed knife {@code k} aims at: about their middle, {feet} + this (for a body {@code half} wide and {@code height} high). */
    static Vec3d aim(int k,int seed,double half,double height){
        return new Vec3d((h(k,seed,5)-.5)*half*1.1,height*(.28+.56*h(k,seed,6)),(h(k,seed,7)-.5)*half*1.1);
    }
    /** One knife this frame: its grip (its rod's middle), the way it points, how much of it shows, the seconds since it came to be, whether it flies, whether it has landed. */
    public record Pose(Vec3d grip,Vec3d dir,float shown,float lit,boolean flying,boolean in){
        public Vec3d tip(){return grip.add(dir.multiply(TIP*size(shown)));}
    }
    static double size(float shown){return .5+.5*shown;}
    /** How much of the way in a knife has come {@code u} of the way through its flight (0 to 1): quicker and quicker, from standing. */
    static double flown(double u){return Math.pow(Math.max(0,Math.min(1,u)),PACE);}
    /**
     * Knife {@code k} {@code age} ticks into a stab that stopped short {@code stopped} ticks in (anything past its end
     * if it did not), for one stabbed whose feet are at {@code feet} (so wide and so high), the user {@code user} from
     * them (level): coming to be out in the air, aimed at them, flying in quicker and quicker from standing, its point
     * past where it aims, a moment shivering in them. Null while there is nothing of it to draw.
     */
    public static Pose pose(int k,int seed,float age,float stopped,Vec3d feet,double half,double height,Vec3d user){
        float lands=(float)RainRules.landsAt(k),comes=lands-BEFORE;
        if(age<comes||comes>=stopped)return null;
        boolean cut=lands>stopped;
        // (one not landed as the rain stops short hangs where it was then, and is gone)
        float t=cut?Math.min(age,stopped):age;
        if(cut&&age>=stopped+VANISH||!cut&&age>=lands+STUCK)return null;
        float shown=Ease.smooth(t,comes,comes+IN)*(cut?1-Ease.smooth(age,stopped,stopped+VANISH):1-Ease.smooth(age,lands+STUCK-GOING,lands+STUCK));
        if(shown<=0)return null;
        Vec3d way=from(k,seed,user),target=feet.add(aim(k,seed,half,height)),dir=way.negate();
        // its point: out in the air, then in, quicker and quicker from standing, to past the point it aims at
        double far=out(k,seed),u=(t-(lands-FLY))/FLY;
        double at=far+(-IN_DEEP-far)*flown(u);
        boolean in=t>=lands;
        // (in them it shivers a moment)
        if(in){double s=(t-lands)/20.0;dir=dir.add(way.crossProduct(new Vec3d(0,1,0)).multiply(.05*Math.exp(-s/.06)*Math.sin(s*90))).normalize();}
        Vec3d tip=target.add(way.multiply(at));
        return new Pose(tip.subtract(dir.multiply(TIP*size(shown))),dir,shown,(t-comes)/20f,u>0&&!in,in);
    }
    /** The one the stab's knife is in, as this client was told (null: no one yet, or they are not here). */
    static LivingEntity stabbed(StandClient.Cast c){
        int[] told=KnifeClient.struck(c.id(),0);if(told==null||told.length==0)return null;
        var mc=MinecraftClient.getInstance();
        return mc.world!=null&&mc.world.getEntityById(told[0]) instanceof LivingEntity e?e:null;
    }
    /** When this one stopped short (ticks into it), or Float.MAX_VALUE: it did not. */
    static float until(StandClient.Cast c){float u=KnifeClient.until(c.id());return u==Float.MAX_VALUE?Float.MAX_VALUE:u*20;}
    /** The level way from the one stabbed to the stab's user (where they stab from). */
    static Vec3d userFrom(StandClient.Cast c,Vec3d feet){
        var way=KnifeClient.rainWay(c.id());Vec3d at=way==null?c.origin():way.to();
        return new Vec3d(at.x-feet.x,0,at.z-feet.z);
    }
    /** How far each knife is turned about its length (degrees: which way its spine faces). */
    static float roll(int k,int seed){return (float)(h(k,seed,8)*360);}

    // ───────────────────────────── what the effects' layer adds ─────────────────────────────

    /** Everything of the run, the stab and the rain but the body and the knives' models, in one pass (the blended bodies, or the light): RainFx (2.132.1, made much stronger). */
    static void draw(Fx c,EffectMesh m,boolean light){RainFx.draw(c,m,light);}
    /** Its light on the world, and less afterglow where things go fast (RainFx). */
    static void frame(Fx c,FxFrame f){RainFx.frame(c,f);}
    /** How hard it shakes the camera (RainFx). */
    static float shake(StandClient.Cast cast,float sec){return RainFx.shake(cast,sec);}

    // ───────────────────────────── its sounds ─────────────────────────────

    /**
     * Its sounds, ones the game has (the user's: "있는 소리 재사용"): as the knife goes in, the jab's thrust
     * (xchara_jab, its onset a tick into its file); through the rain, the knives shot's coming (xchara_shot_appear)
     * every COME_EVERY ticks from as the first comes to be, and their strike (xchara_shot_hit, its onset a tick into
     * its file) with every STRIKE_EVERY-th volley, each at a part of its loudness (many of them sound one over
     * another), the last volley's at LAST_PART.
     */
    static final String STAB_SOUND="xchara_jab",COME_SOUND="xchara_shot_appear",STRIKE_SOUND="xchara_shot_hit";
    static final int COME_EVERY=3,STRIKE_EVERY=2;static final float COME_PART=.3f,STRIKE_PART=.25f,LAST_PART=.8f;
    /** The rain's sounds as {name, the tick into the stab it starts on, the part of its loudness}: what is played once the knife is in someone. */
    static java.util.List<Object[]> cues(){
        var out=new ArrayList<Object[]>();
        for(int t=Math.round(RainRules.FIRST-BEFORE);t<=RainRules.LAST-BEFORE;t+=COME_EVERY)out.add(new Object[]{COME_SOUND,t,COME_PART});
        for(int v=0;v<RainRules.VOLLEYS;v++){
            boolean last=v==RainRules.VOLLEYS-1;
            if(last||v%STRIKE_EVERY==0&&v<RainRules.VOLLEYS-2)out.add(new Object[]{STRIKE_SOUND,RainRules.volleyAt(v)-1,last?LAST_PART:STRIKE_PART});
        }
        return out;
    }

    /** For the capture tools: this player's latest run or stab as "combo,age,stopped,shiftx,shifty,shiftz,stabbed,knives shown,knives in,how much they are inside the rain (its screen)", or null. */
    public static String describe(net.minecraft.entity.player.PlayerEntity player,float delta){
        StandClient.Cast best=null;
        for(var c:KnifeClient.SWINGS.values())if(rain(c.combo())&&c.caster().equals(player.getUuid()))best=c;
        if(best==null)return null;
        Vec3d s=shift(best,delta);var e=best.combo()==RAIN?stabbed(best):null;int shown=0,in=0;float inside=0;
        if(e!=null){
            Vec3d feet=e.getLerpedPos(delta),user=userFrom(best,feet);
            inside=RainFx.inside(player.getLerpedPos(delta),feet,best.age(delta),until(best));
            for(int k=0;k<RainRules.BLADES;k++){
                var p=pose(k,seed(best),best.age(delta),until(best),feet,e.getWidth()*.5,e.getHeight(),user);if(p!=null){shown++;if(p.in())in++;}
            }
        }
        return String.format(java.util.Locale.ROOT,"%d,%.2f,%d,%.2f,%.2f,%.2f,%d,%d,%d,%.2f",best.combo(),best.age(delta),KnifeClient.until(best.id())==Float.MAX_VALUE?0:1,s.x,s.y,s.z,e==null?-1:e.getId(),shown,in,inside);
    }
}
