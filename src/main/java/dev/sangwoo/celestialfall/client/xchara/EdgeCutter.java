package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.CutterRules;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * X Chara's E with their hack off, the three cuts, drawn (2.129.0, user request with their video of it, Roblox's "E -
 * Edge Cutter": "이번에는 q비활성화 e스킬 효과음은 영상에 2번 재생되고 스킬은 영상처럼 앞으로 짧게 이동하면서 3번 베는
 * 스킬이야 적이 바로 앞에 있으면 이동하지 않고 3번 베기만 해"; the server's XCharaAttack.cutter, CutterRules). A skill of
 * the knife's pipeline as the cleave, the jab and the slam are (KnifeClient's swings; KnifeCombo's passes hand it on
 * here), in two kinds: XCharaCombo.CUTTER cut where they stand, XCharaCombo.CUTTER_DASH after a short way forward.
 * <ul>
 * <li>The going forward (the video's: the body thrown far forward over its stride, the knife trailing behind and up):
 * drawn, not walked ({@link #shift}: every client draws the body along CutterRules.planned from where it began to
 * where the server said it ends, while the server keeps them where they began and takes them there as it is over).
 * Their own camera goes with the body they see (BotandCamera).</li>
 * <li>The three cuts ({@link #CLIP}, {@link #CLIP_DASH}: the same, CutterRules.LEAD later): a level forehand across
 * the front to the left, the backhand back to the right and rising, and from up there a heavy cut down across to the
 * low left, the body dropping into it over a step: the one that throws whom it strikes. Each is wound against the way
 * it goes as the one before it is carried on, the body leading the arm and the arm the knife. The video shows three
 * cuts a quarter of a second apart; which cuts they are is mine.</li>
 * <li>On whom a cut strikes: the knife's own burst (SlashFx.burst: the white star and rays, the purple swirl and
 * spikes; the video's), the third's the bigger one. Nothing follows the knife (the video's knife stays black and
 * draws no band).</li>
 * </ul>
 * No screen effect but a small shake. Everything is a function of the skill's own clock.
 */
public final class EdgeCutter {
    private EdgeCutter(){}
    /** The dashing kind's cuts are this much later (seconds), and its going forward is over by then. */
    static final float LEAD=CutterRules.LEAD/20f,THERE=CutterRules.DASH_TO/20f;
    /** When each cut lands where they stand (seconds: the server's ticks). */
    static final float B1=blowAt(CUTTER,0)/20f,B2=blowAt(CUTTER,1)/20f,B3=blowAt(CUTTER,2)/20f;
    /** How long one is drawn for (seconds): what its last cut leaves is gone by then. */
    public static final float LIFE=LENGTH[CUTTER_DASH]/20f+.4f;

    // ───────────────────────────── the body ─────────────────────────────

    private static R6.Pose on(double rt,double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf){
        return KnifeCombo.on(rt,fw,up,yaw,pitch,roll,rArm,blade,lArm,rr,rf,lr,lf);
    }
    /** The first cut's plane: level across the front at the chest, dipping a little to the left (as the first swing's). */
    static final KnifeCombo.Swing S1=new KnifeCombo.Swing(d(-1,-2),d(-6,-92));
    /** The second's: back across the front to the right, rising. */
    static final KnifeCombo.Swing S2=new KnifeCombo.Swing(d(4,2),d(14,92));
    /** The third's: down across the front from the high right to the low left. */
    static final KnifeCombo.Swing S3=new KnifeCombo.Swing(d(2,-3),d(-46,-92));
    /**
     * The three cuts' keys from the first's winding on, {@code at} seconds later than where they stand ({@code fw}
     * blocks further forward in the frame: none; the frame goes with the body).
     */
    private static void cuts(ArrayList<Anim.Key> keys,float at){
        // ── 1: wound behind the right shoulder (the body turned right, the left foot out ahead), then level across to the left
        keys.add(k(at+B1-.085,on(.06,-.04,-.13,-34,-3,4,S1.at(-96),S1.at(-136),d(-36,-22),.21,-.03,-.20,.34)));
        keys.add(k(at+B1-.06,on(.04,.02,-.15,-18,1,2,S1.at(-84),S1.at(-130),d(-40,-44),.21,-.03,-.20,.34)));
        keys.add(k(at+B1-.03,on(.01,.10,-.17,2,6,-1,S1.at(-32),S1.at(-84),d(-46,-92),.21,-.03,-.20,.34)));
        keys.add(k(at+B1,on(-.03,.17,-.19,18,10,-3,S1.at(34),S1.at(-6),d(-46,-124),.21,-.03,-.20,.34)));
        keys.add(k(at+B1+.035,on(-.05,.21,-.20,28,12,-4,S1.at(66),S1.at(40),d(-42,-144),.21,-.03,-.20,.34)));
        // carried on past to the left, and on round behind the left side: where the backhand is wound (the knife keeps
        // level over the ground all the way round)
        keys.add(k(at+B1+.07,on(-.06,.23,-.21,33,13,-5,S1.at(88),d(2,-84),d(-38,-150),.21,-.01,-.20,.34)));
        keys.add(k(at+B1+.11,on(-.07,.24,-.22,36,13,-5,d(-8,-96),d(0,-116),d(-36,-150),.20,.06,-.20,.34)));
        // ── 2: let go, the hips first; back across to the right, rising, the right foot coming up under the body
        keys.add(k(at+B2-.065,on(-.05,.27,-.21,22,10,-3,S2.at(-84),d(-2,-112),d(-40,-128),.19,.20,-.20,.34)));
        keys.add(k(at+B2-.03,on(-.02,.31,-.18,4,6,0,S2.at(-34),S2.at(-84),d(-48,-100),.18,.30,-.20,.34)));
        keys.add(k(at+B2,on(.02,.35,-.15,-12,2,2,S2.at(32),S2.at(-6),d(-56,-74),.18,.34,-.20,.34)));
        keys.add(k(at+B2+.035,on(.04,.37,-.12,-22,-2,3,S2.at(64),S2.at(38),d(-60,-58),.18,.34,-.20,.34)));
        // carried on up to the high right and over: where the last is wound (the knife behind the right shoulder, the body tall)
        keys.add(k(at+B2+.10,on(.06,.38,-.07,-32,-7,3,S3.at(-92),S3.at(-138),d(-22,-28),.18,.34,-.20,.36)));
        keys.add(k(at+B3-.085,on(.06,.39,-.06,-34,-8,3,S3.at(-94),S3.at(-142),d(-12,-20),.18,.36,-.20,.36)));
        // ── 3: the heavy one: the body drops and turns over a step of the right foot, the arm after it, the knife last
        keys.add(k(at+B3-.055,on(.04,.45,-.13,-16,3,1,S3.at(-74),S3.at(-130),d(-22,-44),.17,.58,-.20,.36)));
        keys.add(k(at+B3-.025,on(.01,.55,-.22,4,12,-1,S3.at(-26),S3.at(-82),d(-34,-96),.16,.84,-.20,.36)));
        keys.add(k(at+B3,on(-.02,.64,-.29,20,19,-3,S3.at(30),S3.at(-8),d(-40,-134),.16,.92,-.21,.36)));
        // the cut levels off over the ground and is drawn on across to the left hip (the hand leading it there), deep
        // over the front foot; held; brought back
        keys.add(k(at+B3+.04,on(-.04,.69,-.33,30,23,-4,d(-38,-36),d(-3,-12),d(-38,-150),.16,.92,-.21,.36)));
        keys.add(k(at+B3+.12,on(-.05,.72,-.36,36,25,-4,d(-33,-66),d(-8,-36),d(-34,-156),.16,.92,-.21,.36)));
        keys.add(k(at+HOLD[CUTTER]/20f,on(-.05,.70,-.34,33,23,-4,d(-32,-60),d(-7,-30),d(-36,-152),.16,.92,-.21,.36)));
        keys.add(k(at+LENGTH[CUTTER]/20f-.2,on(-.02,.36,-.15,12,10,-1,d(-62,-20),d(-3,-12),d(-66,-70),.18,.30,-.20,.20)));
        keys.add(k(at+LENGTH[CUTTER]/20f,KnifeCombo.STANCE));
    }
    private static Anim clip(boolean dash){
        var keys=new ArrayList<Anim.Key>();float at=dash?LEAD:0;
        keys.add(k(0,KnifeCombo.STANCE));
        if(dash){
            // thrown forward off the back foot: the knife swung out round the right side to trail behind and up, the body
            // far over its stride, the free hand reaching ahead (the video's)
            keys.add(k(.04,on(0,.08,-.09,-6,14,0,d(-70,60),d(4,50),d(-60,-10),.17,-.14,-.18,.22)));
            keys.add(k(.08,on(0,.18,-.20,-10,36,0,d(-50,120),d(14,120),d(-36,-8),.17,-.42,-.18,.40)));
            keys.add(k(.12,on(0,.26,-.30,-12,56,0,d(-26,164),d(34,176),d(-10,-6),.17,-.66,-.18,.54)));
            // the stride changes: the right foot through
            keys.add(k(.20,on(0,.28,-.32,-10,60,0,d(-20,166),d(36,176),d(-26,-30),.17,.54,-.18,-.56)));
            // braking onto the left foot as they come there, the knife swung out round the right side for the first cut
            keys.add(k(.27,on(.03,.06,-.18,-24,20,2,d(-40,120),S1.at(-150),d(-34,-14),.20,-.12,-.20,.34)));
        }else keys.add(k(.05,on(.03,-.02,-.07,-16,-1,2,d(-44,76),S1.at(-70),d(-56,-18),.20,-.01,-.19,.14)));
        cuts(keys,at);
        int hit=dash?CUTTER_DASH:CUTTER;
        Anim.Fix bent=IsosangLimbs.natural(IsosangLimbs.FLOOR);float end=LENGTH[hit]/20f;
        return Anim.joints(keys.toArray(new Anim.Key[0]))
            .stops(Anim.stop(at+B1,.02,.035),Anim.stop(at+B2,.02,.035),Anim.stop(at+B3,.05,.07))
            .after((sec,f)->{
                R6.Frame b=bent.apply(sec,f);
                float rest=Math.max(1-Ease.smooth(sec,0,KnifeCombo.OUT_OF_REST),Ease.smooth(sec,end-KnifeCombo.INTO_REST,end));
                return rest<=0?b:R6.blend(b,KnifeCombo.AT_REST,Math.min(1,rest));
            });
    }
    /** The clips: cut where they stand, and after the going forward. */
    static final Anim CLIP=clip(false),CLIP_DASH=clip(true);
    static Anim clipOf(int combo){return combo==CUTTER_DASH?CLIP_DASH:CLIP;}

    // ───────────────────────────── where the body is ─────────────────────────────

    private static double[] arr(Vec3d v){return new double[]{v.x,v.y,v.z};}
    /** Where the body's feet are {@code age} ticks into dashing cut {@code c}, going to {@code to}. */
    static Vec3d planned(StandClient.Cast c,Vec3d to,float age){
        double[] p=CutterRules.planned(age,arr(c.origin()),arr(to));return new Vec3d(p[0],p[1],p[2]);
    }
    /** How much the body is held to the plan {@code age} ticks in: all of it through the going; let go a little after (the server has taken them there by then). */
    static float held(float age){return 1-Ease.smooth(age,CutterRules.DASH_TO+3,CutterRules.DASH_TO+8);}
    /**
     * How far from where the cuts' user really is their body is drawn this frame (their renderer's offset,
     * BeatdownRenderMixin: their model, name and shadow's place go together): along the way forward, and where it ends
     * until they are really there. None for one that cuts where they stand, or that stopped short.
     */
    public static Vec3d shift(StandClient.Cast c,float delta){
        if(c==null||c.combo()!=CUTTER_DASH||c.skill()!=SKILL)return Vec3d.ZERO;
        Vec3d to=KnifeClient.dash(c.id());if(to==null||KnifeClient.until(c.id())!=Float.MAX_VALUE)return Vec3d.ZERO;
        float age=c.age(delta),w=held(age);if(w<=0)return Vec3d.ZERO;
        var mc=MinecraftClient.getInstance();var owner=mc.world==null?null:mc.world.getPlayerByUuid(c.caster());if(owner==null)return Vec3d.ZERO;
        return planned(c,to,age).subtract(owner.getLerpedPos(delta)).multiply(w);
    }
    /** How far from where this player really is their body is drawn now, in their three cuts (null: in none, or nowhere else): their own camera goes with it (BotandCamera). */
    public static Vec3d offset(UUID id,float delta){
        var c=KnifeClient.cutterOf(id);if(c==null)return null;
        Vec3d s=shift(c,delta);return s.lengthSquared()<1e-8?null:s;
    }

    // ───────────────────────────── what a cut leaves ─────────────────────────────

    /** Everything of the cuts but the body and the knife, in one pass (the blended bodies, or the light): the burst on whom each struck. */
    static void draw(Fx c,EffectMesh m,boolean light){
        int hit=c.cast.combo();
        for(int i=0;i<blows(hit);i++){
            float s=c.sec-blowAt(hit,i)/20f;
            if(s<0||s>=SlashFx.SWIRL_LIFE||!KnifeClient.cameTo(c.cast,i))continue;
            for(var e:KnifeCombo.struck(c,i))SlashFx.burst(m,e.mid(),c.f,i==2?HACK_LUNGE:HACK_SLASH,KnifeCombo.seed(c,i)+e.id(),s,light,c.camera);
        }
    }
    /** Its light on the world: a little purple on whom a cut struck. */
    static void frame(Fx c,FxFrame f){
        int hit=c.cast.combo();
        for(int i=0;i<blows(hit);i++){
            float s=c.sec-blowAt(hit,i)/20f;
            if(s<0||s>=.3f||!KnifeClient.cameTo(c.cast,i))continue;
            for(var e:KnifeCombo.struck(c,i))f.light(e.mid(),0x9a3cff,i==2?8:6,(i==2?1f:.65f)*(1-s/.3f));
        }
    }
    /** How hard each cut shakes the camera as it lands on someone (a cut that meets no one, a third of it). */
    static final float[] SHAKE={.35f,.35f,.9f};
    static float shake(StandClient.Cast cast,float sec){
        int hit=cast.combo();float sum=0;
        for(int i=0;i<blows(hit);i++){
            if(!KnifeClient.cameTo(cast,i))continue;
            int[] told=KnifeClient.struck(cast.id(),i);
            sum+=SHAKE[i]*(told!=null&&told.length>0?1:.33f)*KnifeCombo.bump(sec,blowAt(hit,i)/20f,.07f);
        }
        return sum;
    }
}
