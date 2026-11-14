package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
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
import dev.sangwoo.celestialfall.xchara.XChara;
import java.util.ArrayList;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
 * stays in the one stabbed, the body leaning on it, the free hand thrown up over the shoulder: calling the knives.
 * As the last volley lands that hand comes down; the knife is drawn out, goes black, and they step back.</li>
 * <li>The rain ({@link #models}): RainRules.BLADES knives, the knife's own model lit as the hack lights it, half the
 * size it is in the hand, three a tick. Each comes to be out on a shell round the one stabbed (from every side and
 * from over them; none from where its user stands) under blocks of purple, hangs pointing at them a moment, draws
 * back a little and flies in, quicker and quicker, to stand in them a moment before it is gone. What the effects'
 * layer adds ({@link #draw}): a purple glow along each, a streak behind one in flight, a white spark and a few pixels
 * where each goes in, the knife's own burst every few volleys, and as the last lands a ring over the ground and the
 * bigger burst.</li>
 * </ul>
 * Its sounds are ones the game has (the user's: "있는 소리 재사용"; {@link #voices}): the jab's thrust as the knife goes
 * in, the knives shot's coming and strike through the rain. No screen effect but the camera's shake. Everything is a
 * function of the skills' own clocks and what the server told.
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
    /** The knife in them: the body leaning on it over the front foot, the free hand up over the shoulder; {@code push} 0 easing off, 1 bearing in. */
    private static R6.Pose leaning(double push,double[] lArm){
        return on(.02,.62+.05*push,-.355-.015*push,(float)(27+3*push),(float)(23+2*push),-3,d(-10,-22-2*push),d(4,-4),lArm,RR,RF,LR,LF);
    }
    /**
     * The stab and the rain: from the run (whatever stride it is in: KagenoClips takes it up from the frame on screen)
     * the knife comes round the right side to the hip, its point on them, the body side-on over the back foot; then
     * the back leg drives, the hips and the shoulder turn in and the arm goes out last, in on STAB. It is driven a
     * little further and borne on, the free hand thrown up over the left shoulder, until the last volley; that hand
     * comes down with it; the knife is torn out back past the hip and they step away.
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
        // driven further, the free hand coming up
        keys.add(k(STAB_AT+.05f,on(.02,.66,-.37,30,25,-3,d(-9,-24),d(4,-4),d(-4,-120),RR,RF,LR,LF)));
        keys.add(k(STAB_AT+.20f,leaning(1,d(46,-96))));
        keys.add(k(STAB_AT+.36f,leaning(.3,d(72,-76))));
        // borne on through the rain: bearing in and easing off, the hand held up
        float t=STAB_AT+.36f;boolean in=true;
        while(t+.3f<LAST_AT-.1f){t+=.3f;keys.add(k(t,leaning(in?1:.2,in?d(66,-66):d(74,-80))));in=!in;}
        // the last volley: the hand comes down with it, the body bearing in hardest
        keys.add(k(LAST_AT-.08f,leaning(.6,d(64,-62))));
        keys.add(k(LAST_AT+.04f,on(.02,.70,-.38,31,27,-3,d(-9,-25),d(4,-4),d(-22,-40),RR,RF,LR,LF)));
        keys.add(k(OUT_AT,on(.02,.68,-.37,30,26,-3,d(-10,-24),d(4,-4),d(-34,-36),RR,RF,LR,LF)));
        // torn out: the hips back first, the knife after them past the hip
        keys.add(k(OUT_AT+.11f,on(-.03,.34,-.30,-12,12,1,d(-44,60),d(5,6),d(-40,-30),RR,RF,LR,LF)));
        keys.add(k(hold,on(-.03,.14,-.17,-16,5,1,d(-52,100),d(6,30),d(-60,-24),.17,.50,-.21,-.30)));
        keys.add(k(end-.14f,on(-.01,.04,-.07,-5,1,0,d(-76,60),d(3,20),d(-78,-14),.19,.14,-.20,-.10)));
        keys.add(k(end,KnifeCombo.STANCE));
        return Anim.joints(keys.toArray(new Anim.Key[0])).stops(Anim.stop(STAB_AT,.05,.07)).after(rested(end,false));
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

    /** A knife of the rain is this much of the knife in the hand; so its point is this far from its grip (blocks). */
    static final double SIZE=.5,TIP=ShotRules.TIP*SIZE;
    /** A knife comes to be this many ticks before it lands, takes IN to come, and flies the last FLY of them (drawn back BACK blocks just before). */
    static final float BEFORE=7.5f,IN=2.4f,FLY=2.4f;static final double BACK=.35;
    /** One that has landed stands in them this long (ticks), gone over the last GOING of it; one not yet landed as the rain stops short is gone over VANISH. */
    static final float STUCK=6,GOING=2,VANISH=2;
    /** How far out from the one stabbed a knife comes to be (blocks: its point), and how high and low about them (degrees over level). */
    static final double NEAR=4.0,FAR=6.4;static final double LOWEST=4,HIGHEST=68;
    /** No knife comes from within this of where its user stands, seen from the one stabbed, unless from this high over them (degrees). */
    static final double CLEAR=42,OVER=38;
    /** How far past the point it aims at a knife's point goes in (blocks). */
    static final double IN_DEEP=.22;
    private static double h(int k,int seed,int salt){return GlitchFx.h(k,0,seed,salt);}
    /** A seed for this stab's knives (the same on every client: its start). */
    static int seed(StandClient.Cast c){return (int)(c.start()*31+17);}
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
    /** The point of a body knife {@code k} aims at: about its middle, {feet} + this (for a body {@code half} wide and {@code height} high). */
    static Vec3d aim(int k,int seed,double half,double height){
        return new Vec3d((h(k,seed,5)-.5)*half*1.1,height*(.28+.56*h(k,seed,6)),(h(k,seed,7)-.5)*half*1.1);
    }
    /** One knife this frame: its grip (its model's origin), the way it points, how much of it shows, the seconds since it came to be, whether it flies, whether it has landed. */
    public record Pose(Vec3d grip,Vec3d dir,float shown,float lit,boolean flying,boolean in){
        public Vec3d tip(){return grip.add(dir.multiply(TIP*size(shown)));}
    }
    static double size(float shown){return .5+.5*shown;}
    /**
     * Knife {@code k} {@code age} ticks into a stab that stopped short {@code stopped} ticks in (anything past its end
     * if it did not), for one stabbed whose feet are at {@code feet} (so wide and so high), the user {@code user} from
     * them (level). Null while there is nothing of it to draw.
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
        // its point: out on the shell (drawn back a little before it goes), then in, quicker and quicker, to past the point it aims at
        double far=out(k,seed)+BACK*Ease.smooth(t,lands-FLY-1.6f,lands-FLY)+.04*Math.sin(t*.9+k*1.7);
        float u=Math.max(0,Math.min(1,(t-(lands-FLY))/FLY));
        double at=far+(-IN_DEEP-far)*u*u;
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
    private static float until(StandClient.Cast c){float u=KnifeClient.until(c.id());return u==Float.MAX_VALUE?Float.MAX_VALUE:u*20;}
    /** The level way from the one stabbed to the stab's user (where they stab from). */
    private static Vec3d userFrom(StandClient.Cast c,Vec3d feet){
        var way=KnifeClient.rainWay(c.id());Vec3d at=way==null?c.origin():way.to();
        return new Vec3d(at.x-feet.x,0,at.z-feet.z);
    }
    /** How far each knife is rolled about its length (degrees). */
    private static float roll(int k,int seed){return (float)(h(k,seed,8)*360);}

    private static XCharaKnifeRenderer renderer;private static ItemStack stack;
    public static void initialize(){net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(BladeRain::models);}
    /** The knives as the knife's own model, lit as the hack lights it (AFTER_ENTITIES). */
    static void models(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();if(mc.world==null||KnifeClient.SWINGS.isEmpty()||ctx.consumers()==null||!MapClient.enabled())return;
        Vec3d camera=ctx.camera().getPos();var matrices=ctx.matrixStack();float delta=ctx.tickDelta();
        for(var c:KnifeClient.SWINGS.values()){
            if(c.combo()!=RAIN||c.origin().squaredDistanceTo(camera)>128*128)continue;
            var e=stabbed(c);if(e==null)continue;
            float age=c.age(delta),stop=until(c);int seed=seed(c);
            Vec3d feet=e.getLerpedPos(delta),user=userFrom(c,feet);double half=e.getWidth()*.5,height=e.getHeight();
            for(int k=0;k<RainRules.BLADES;k++){
                var p=pose(k,seed,age,stop,feet,half,height,user);if(p==null||p.shown()<=.02f)continue;
                if(renderer==null){renderer=new XCharaKnifeRenderer();stack=new ItemStack(XChara.KNIFE);}
                Vec3d y=p.dir(),s=BladeShot.spine(y,roll(k,seed)),x=s.negate(),z=x.crossProduct(y).normalize();
                float scale=(float)(ShotRules.SCALE*SIZE*size(p.shown()));
                matrices.push();
                matrices.translate(p.grip().x-camera.x,p.grip().y-camera.y,p.grip().z-camera.z);
                matrices.multiply(new Quaternionf().setFromNormalized(new Matrix3f(new Vector3f((float)x.x,(float)x.y,(float)x.z),new Vector3f((float)y.x,(float)y.y,(float)y.z),new Vector3f((float)z.x,(float)z.y,(float)z.z))));
                matrices.scale(scale,scale,scale);
                matrices.translate(-.5,-.51,-.5);
                XCharaKnifeRenderer.hack=new HackLook.Frame(true,p.lit(),age/20f+k*1.3f,seed+k*13);
                try{renderer.render(stack,ModelTransformationMode.NONE,matrices,ctx.consumers(),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);}
                finally{XCharaKnifeRenderer.hack=null;}
                matrices.pop();
            }
        }
    }

    // ───────────────────────────── what the effects' layer adds ─────────────────────────────

    /** The knife's burst on the one stabbed comes with every this many volleys (a smaller one); the last volley's is the big one. */
    static final int BURST_EVERY=4;static final double BURST_SIZE=.6;
    /** Everything of the stab and the rain but the body and the knives' models, in one pass (the blended bodies, or the light). */
    static void draw(Fx c,EffectMesh m,boolean light){
        if(c.cast.combo()!=RAIN)return;
        float age=c.age,sec=c.sec,stop=until(c.cast);int seed=seed(c.cast);
        var e=stabbed(c.cast);
        // the stab: the knife's bigger burst on whom it went into
        float s0=sec-STAB_AT;
        if(s0>=0&&s0<SlashFx.SWIRL_LIFE&&KnifeClient.cameTo(c.cast,0))
            for(var st:KnifeCombo.struck(c,0))SlashFx.burst(m,st.mid(),c.f,HACK_LUNGE,KnifeCombo.seed(c,0)+st.id(),s0,light,c.camera);
        if(e==null)return;
        Vec3d feet=e.getLerpedPos(c.delta),user=userFrom(c.cast,feet),mid=feet.add(0,e.getHeight()*.55,0);double half=e.getWidth()*.5,height=e.getHeight();
        for(int k=0;k<RainRules.BLADES;k++){
            float lands=(float)RainRules.landsAt(k);
            var p=pose(k,seed,age,stop,feet,half,height,user);
            if(p!=null){
                float near=GlitchFx.near(p.grip(),c.camera)*p.shown();Vec3d tip=p.tip();
                if(near>0){
                    if(light){
                        // a soft purple along it, a spark as it comes to be, a streak behind it in flight
                        m.softLine(p.grip().subtract(p.dir().multiply(.15)),tip,.32,GlitchFx.GLOW,.2f*near);
                        if(p.lit()<.12f)m.star(p.grip().add(p.dir().multiply(TIP*.5)),.34*(1-p.lit()/.12f)+.08,GlitchFx.LILAC,.75f*(1-p.lit()/.12f)*near);
                        if(p.flying()){
                            Vec3d tail=tip.subtract(p.dir().multiply(TIP+1.5));
                            m.softTaper(tail,tip,.04,.42,GlitchFx.GLOW,.4f*near);m.softTaper(tail,tip,.015,.1,GlitchFx.WHITE,.65f*near);
                        }
                    }
                    // a block or two of purple breaking over it as it comes (the hack's own coming on)
                    if(p.lit()<.16f){
                        int slot=(int)Math.floor(age/20f*16);var f=GlitchFx.facing(c.camera,p.grip());
                        for(int j=0;j<3;j++){
                            if(GlitchFx.h(j,slot,seed+k,31)>.55)continue;
                            Vec3d at=p.grip().add(p.dir().multiply(TIP*GlitchFx.h(j,slot,seed+k,32))).add(f[0].multiply((GlitchFx.h(j,slot,seed+k,33)-.5)*.3)).add(f[1].multiply((GlitchFx.h(j,slot,seed+k,34)-.5)*.3));
                            double sq=.03+.045*GlitchFx.h(j,slot,seed+k,35);
                            if(light)GlitchFx.square(m,at,f,sq*1.8,GlitchFx.GLOW,.3f*near);else GlitchFx.square(m,at,f,sq,GlitchFx.h(j,slot,seed+k,36)<.6?GlitchFx.PURPLE:GlitchFx.DEEP,.95f*near);
                        }
                    }
                }
            }
            // where it goes in: a white spark, a few pixels thrown back the way it came
            float s=(age-lands)/20f;
            if(s>=0&&s<.3f&&lands<=stop){
                Vec3d way=from(k,seed,user),at=feet.add(aim(k,seed,half,height)).add(way.multiply(half*.6));
                float near=GlitchFx.near(at,c.camera);
                if(light&&s<.09f&&near>0)m.star(at,.3*(1-s/.09f)+.08,GlitchFx.LILAC,.7f*(1-s/.09f)*near);
                GlitchFx.pixels(m,at,way,.7,3.2,.035,2,seed+k*7,s,light,c.camera);
            }
        }
        // the knife's own burst on them every few volleys; the last volley's the big one, with a ring over the ground
        for(int v=0;v<RainRules.VOLLEYS;v++){
            boolean last=v==RainRules.VOLLEYS-1;if(!last&&v%BURST_EVERY!=0)continue;
            float at=RainRules.volleyAt(v),s=(age-at)/20f;
            if(s<0||at>stop)continue;
            if(s<SlashFx.SWIRL_LIFE){
                Vec3d way=from(v*RainRules.PER,seed,user).negate();
                if(last)SlashFx.burst(m,mid,way,HACK_LUNGE,seed+v,s,light,c.camera);
                else SlashFx.burst(m.scaled(SlashFx.burstAt(mid,c.camera),BURST_SIZE),mid,way,HACK_SLASH,seed+v,s,light,c.camera);
            }
            if(last&&s<.5f){
                float fade=1-s/.5f,near=GlitchFx.near(mid,c.camera);
                Vec3d ground=feet.add(0,.06,0),x=new Vec3d(1,0,0),z=new Vec3d(0,0,1);
                if(light&&near>0){
                    m.ring(ground,x,z,.5+3.6*Ease.smooth(s,0,.36f),48,1,.09,GlitchFx.GLOW,.75f*fade*near,0);
                    m.ring(ground,x,z,.3+2.4*Ease.smooth(s,0,.3f),40,1,.04,GlitchFx.WHITE,.6f*fade*near,0);
                }
                GlitchFx.pixels(m,mid,new Vec3d(0,1,0),1,4.5,.05,26,seed+991,s,light,c.camera);
            }
        }
    }
    /** Its light on the world: purple on the one stabbed through the rain, pulsing with the volleys; little afterglow while the knives fly (their light would leave copies of them). */
    static void frame(Fx c,FxFrame f){
        if(c.cast.combo()!=RAIN)return;
        var e=stabbed(c.cast);if(e==null)return;
        float age=c.age,stop=until(c.cast),first=RainRules.FIRST-BEFORE,end=Math.min(RainRules.LAST,stop);
        if(age<first||age>end+10)return;
        Vec3d mid=e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);
        float on=Ease.smooth(age,first,first+4)*(1-Ease.smooth(age,end,end+10));
        double beat=.5+.5*Math.sin(age*Math.PI*2);
        f.light(mid,0x9a3cff,9,(float)(.55+.25*beat)*on);
        float s=(age-RainRules.LAST)/20f;
        if(s>=0&&s<.35f&&RainRules.LAST<=stop)f.light(mid,0xb060ff,14,1.2f*(1-s/.35f));
        if(age<end+STUCK)f.afterglow(.06f);
    }
    /** How hard the stab shakes the camera as it goes in, each volley as it lands, and the last. */
    static final float SHAKE_STAB=.8f,SHAKE_VOLLEY=.16f,SHAKE_LAST=1.3f;
    static float shake(StandClient.Cast cast,float sec){
        if(cast.combo()!=RAIN||!KnifeClient.cameTo(cast,0))return 0;
        int[] told=KnifeClient.struck(cast.id(),0);boolean hit=told!=null&&told.length>0;
        float sum=SHAKE_STAB*(hit?1:.33f)*KnifeCombo.bump(sec,STAB_AT,.07f);
        if(!hit)return sum;
        float stop=until(cast)/20f;
        int near=Math.round(sec*20)-RainRules.FIRST;
        for(int v=Math.max(0,near-6);v<=Math.min(RainRules.VOLLEYS-1,near+1);v++){
            float at=RainRules.volleyAt(v)/20f;if(at>stop)continue;
            sum+=(v==RainRules.VOLLEYS-1?SHAKE_LAST:SHAKE_VOLLEY)*KnifeCombo.bump(sec,at,v==RainRules.VOLLEYS-1?.09f:.04f);
        }
        return sum;
    }

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

    /** For the capture tools: this player's latest run or stab as "combo,age,stopped,shiftx,shifty,shiftz,stabbed,knives shown,knives in", or null. */
    public static String describe(net.minecraft.entity.player.PlayerEntity player,float delta){
        StandClient.Cast best=null;
        for(var c:KnifeClient.SWINGS.values())if(rain(c.combo())&&c.caster().equals(player.getUuid()))best=c;
        if(best==null)return null;
        Vec3d s=shift(best,delta);var e=best.combo()==RAIN?stabbed(best):null;int shown=0,in=0;
        if(e!=null){
            Vec3d feet=e.getLerpedPos(delta),user=userFrom(best,feet);
            for(int k=0;k<RainRules.BLADES;k++){var p=pose(k,seed(best),best.age(delta),until(best),feet,e.getWidth()*.5,e.getHeight(),user);if(p!=null){shown++;if(p.in())in++;}}
        }
        return String.format(java.util.Locale.ROOT,"%d,%.2f,%d,%.2f,%.2f,%.2f,%d,%d,%d",best.combo(),best.age(delta),KnifeClient.until(best.id())==Float.MAX_VALUE?0:1,s.x,s.y,s.z,e==null?-1:e.getId(),shown,in);
    }
}
