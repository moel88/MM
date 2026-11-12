package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.w;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * X Chara's basic attack drawn: the body's motion through its four swings (2.117.0, user request: "이번에는 Q 껐을때 평타를
 * 만들어줘 이펙트 없이 그냥 휘두르기만해 그대신 모션을 좋게 만들어봐": mine to make up; XCharaCombo for its timing, KnifeClient
 * for its swings) and, for a swing begun with their hack on (Q), its purple cut and what it leaves on whom it strikes
 * (SlashFx, 2.118.0, user request: "Q활성화 평타 이펙트를 전체적으로 싹 다 갈아엎고 평타 소리에 맞춰서 새로 만들어줘 보라색
 * 느낌으로 이펙트는 원래꺼보다는 약하고 적당하게"; the swings are the same with the hack on or off, the user's choice).
 * A swing begun with the hack off draws nothing but the body and the knife.
 * <p>
 * Until 2.118.0 the hack's four hits were a hacker's tricks of their own (2.114.0: glitching cuts, a cut pasted again,
 * knives of light and popups, a red X), all gone: their motions, their effects and the glitch shader's styles.
 * <p>
 * No screen effects (no impact frame, no flash): everything is in the world; the camera only shakes a little as a blow
 * lands. Every effect is a function of the swing's clock, so it moves at the render frame rate.
 */
public final class KnifeCombo implements Fx.Skill {
    /** How long a swing is drawn for (seconds): the lunge's motion, the longest (what a cut leaves is gone well before). */
    public static final float LIFE=1.5f;
    /**
     * The knife's display turns it this far round the wrist (models/item/x_chara_knife.json: its x rotation; none, 2.116.0:
     * the knife is square to the arm that hangs straight, so level ahead); the hand turns it on from there as a hit drives it.
     */
    public static final float DISPLAY_TILT=0;
    /** Blocks along the knife from the fist: its blade's heel and its point (twice its model's, as a hand holds it). */
    static final double HEEL=.37,TIP=2.63;

    /** The second into hit {@code hit} its blow {@code i} lands on (the server's tick). */
    static float blow(int hit,int i){return blowAt(hit,i)/20f;}
    /** The second into hit {@code hit} the next may begin. */
    static float step(int hit){return STEP[hit]/20f;}

    // ───────────────────────────── the stance ─────────────────────────────

    /**
     * How the game holds the knife between hits, as a frame (MotionPlayerModel's "xchara" arms, XCharaIdle, 2.116.0: the
     * arm straight down, a little out from the body; the knife as its display turns it).
     */
    static R6.Frame hold(){
        MotionPose m=new MotionPose();
        m.joint[MotionPose.RIGHT_ARM]=new float[]{0,0,XCharaIdle.KNIFE_OUT};
        return new R6.Frame(m,DISPLAY_TILT,0,0,0,0,0,0);
    }
    /**
     * Their stance between hits: as they stand at rest (XCharaIdle: at attention, the knife's arm straight and a little
     * out, the knife level ahead with its spine up, the other arm straight against the body, the legs straight and a
     * little apart).
     */
    static final R6.Pose STANCE=holding();
    private static R6.Pose holding(){
        var held=R6.poseOf(hold());
        R6.Pose s=R6.world(0,0,0,0,0,d(-90,0),d(0,0),d(-90,0),d(-90+XCharaIdle.LEGS_OUT,90),d(-90+XCharaIdle.LEGS_OUT,-90));
        s.rArm=held.rArm;s.blade=held.blade;return s;
    }
    /** The stance as the model stands it: every limb straight (what a hit begins from and ends in: {@link #natural}). */
    static final R6.Frame AT_REST=R6.solve(STANCE);
    /** The stance solved, for a hit to start from (KagenoClips.begin). */
    public static R6.Frame stance(){return new R6.Frame(AT_REST.joints().copy(),AT_REST.grip(),0,0,0,0,0,0);}
    /** How long the first hit takes out of the stance's straight limbs into its own bent ones, and every hit takes back into them at its end (seconds). */
    static final float OUT_OF_REST=.05f,INTO_REST=.15f;
    /**
     * The fix of hit {@code hit}'s frames: their limbs bent as a person's are (IsosangLimbs: the elbows and knees of the
     * keys' one-piece limbs), and, where the hit stands in the stance (the first hit's beginning, every hit's end), the
     * limbs straight as they stand at rest (2.116.0: at attention), eased into and out of.
     */
    static Anim.Fix natural(int hit){
        Anim.Fix bent=IsosangLimbs.natural(IsosangLimbs.FLOOR);float end=LENGTH[hit]/20f;
        return (sec,f)->{
            R6.Frame b=bent.apply(sec,f);
            float w=Math.max(place(hit)==1?1-Ease.smooth(sec,0,OUT_OF_REST):0,Ease.smooth(sec,end-INTO_REST,end));
            return w<=0?b:R6.blend(b,AT_REST,Math.min(1,w));
        };
    }


    // ══════════════════════════════ the four swings (2.117.0) ══════════════════════════════
    // (user request: "이번에는 Q 껐을때 평타를 만들어줘 이펙트 없이 그냥 휘두르기만해 그대신 모션을 좋게 만들어봐")
    // With the hack off nothing is drawn for these but the body and the knife, so the body carries them (with it on a
    // purple cut follows the knife, SlashFx: the body is the same). Each swing is wound up against
    // the way it will go and let go from the ground up: the hips and the body turn and go forward first, the arm after
    // them, the knife last, trailing the arm so that its edge leads; it is quickest as it lands, stopped a moment on the
    // blow, carried on past it and slowly brought back a little (never frozen), where the next swing is already wound.
    // The arm and the knife keep to one plane through a swing (Swing), so the knife's point draws a clean arc; the feet
    // stand where they are put while the body moves over them (on), and step: one slides out as the first swing is
    // wound, the other steps through with the second, the back one is gathered up under the third and shot back as it
    // drops, the front one shoots out with the lunge. The free arm goes against the knife's all through; the head keeps
    // to the front.

    /**
     * A swing's plane: the way the knife points as it lands and the way it is travelling there. {@link #at} is a way in
     * that plane, so many degrees round from the first toward the second (behind the blow, negative). An arm and a knife
     * both in the plane, the knife some way behind the arm, swing with the knife's edge leading the whole way (the hand
     * holds it with its edge on the arm's side of it).
     */
    static final class Swing {
        private final double[] a,w;
        Swing(double[] ahead,double[] way){
            a=vec(ahead);double[] t=vec(way);double dot=a[0]*t[0]+a[1]*t[1]+a[2]*t[2];
            for(int i=0;i<3;i++)t[i]-=a[i]*dot;
            double len=Math.sqrt(t[0]*t[0]+t[1]*t[1]+t[2]*t[2]);
            w=new double[]{t[0]/len,t[1]/len,t[2]/len};
        }
        double[] at(double turn){
            double c=Math.cos(Math.toRadians(turn)),s=Math.sin(Math.toRadians(turn));
            double x=a[0]*c+w[0]*s,y=a[1]*c+w[1]*s,z=a[2]*c+w[2]*s;
            return d(Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,y)))),Math.toDegrees(Math.atan2(x,z)));
        }
        /** A way of the world (elevation, azimuth right of the front) as right, up, ahead. */
        private static double[] vec(double[] way){
            double e=Math.toRadians(way[0]),z=Math.toRadians(way[1]);
            return new double[]{Math.cos(e)*Math.sin(z),Math.sin(e),Math.cos(e)*Math.cos(z)};
        }
    }
    /**
     * A pose written in the world as {@link dev.sangwoo.celestialfall.client.isosang.IsosangSlash#w} writes one, with
     * the body also {@code rt} to the right (its weight over one foot or the other), standing on its feet where they are
     * put: the right foot on the ground ({@code rr} right of, {@code rf} ahead of where their user stands), the left at
     * ({@code lr}, {@code lf}) (IsosangLimbs.standingAt: the knees bent to them as the body comes down). Keys that keep a
     * foot's place keep that foot still while the body moves over it; a foot whose place changes between two keys
     * slides there along the ground (a low step).
     */
    static R6.Pose on(double rt,double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf){
        R6.Pose p=R6.world(fw,up,yaw,pitch,roll,rArm,blade,lArm,d(-90,0),d(-90,0));
        p.rt=rt;
        p.rLeg=IsosangLimbs.standingAt(p,0,rr,rf);p.lLeg=IsosangLimbs.standingAt(p,1,lr,lf);
        return p;
    }

    // ───────────────────────────── 1: 가로베기 ─────────────────────────────
    // the weight back onto the right leg, the left foot sliding out ahead, the knife swung out round the right side to
    // behind the shoulder (the body turned right, sinking); then level across the front at chest height to the left,
    // the body turning and driving forward over the front foot

    /** Its plane: level across the front, dipping a little to the left. */
    static final Swing S1=new Swing(d(-1,-2),d(-8,-92));
    /** Its end: the knife out to the left, the body turned after it over the front foot (where the second takes it up). */
    static final R6.Pose P1_END=on(-.07,.30,-.24,34,13,-4,S1.at(78),S1.at(55),d(-38,-150),.21,-.03,-.20,.40);
    static final Anim P1=Anim.joints(
        k(0,STANCE),
        k(.05,on(.03,-.03,-.07,-15,-2,2,d(-42,74),S1.at(-58),d(-58,-18),.20,-.01,-.19,.12)),
        k(.09,on(.06,-.06,-.13,-32,-4,4,S1.at(-90),S1.at(-120),d(-42,-20),.21,-.03,-.20,.33)),
        // wound furthest: the knife level behind the right shoulder, the free hand out ahead
        k(.115,on(.07,-.06,-.15,-37,-5,4,S1.at(-97),S1.at(-137),d(-34,-22),.21,-.03,-.20,.40)),
        // let go: the hips first (the body turns and goes forward while the arm has hardly moved), then the arm, the knife last
        k(.14f,on(.05,.01,-.17,-20,0,2,S1.at(-88),S1.at(-133),d(-38,-42),.21,-.03,-.20,.40)),
        k(.17f,on(.01,.12,-.20,2,7,-1,S1.at(-36),S1.at(-88),d(-46,-90),.21,-.03,-.20,.40)),
        // the blow: the blade straight ahead across the middle, the arm led on to the left
        k(blow(SLASH,0),on(-.03,.22,-.23,18,11,-3,S1.at(34),S1.at(-6),d(-46,-124),.21,-.03,-.20,.40)),
        k(.23f,on(-.06,.27,-.24,29,13,-4,S1.at(66),S1.at(38),d(-42,-144),.21,-.03,-.20,.40)),
        // carried on past, then brought back a little
        k(.30f,on(-.08,.31,-.25,38,14,-5,S1.at(87),S1.at(64),d(-36,-154),.21,-.03,-.20,.40)),
        k(step(SLASH),P1_END),
        k(.58f,on(-.03,.13,-.10,12,5,-1,d(-62,-30),d(-3,-20),d(-72,-60),.20,-.01,-.19,.14)),
        k(.75f,STANCE)).stops(Anim.stop(blow(SLASH,0),.025,.04)).after(natural(SLASH));

    // ───────────────────────────── 2: 올려베기 ─────────────────────────────
    // the wrist turns the knife over and lets it down behind the left hip (the body sinking over the front foot), then
    // it is ripped up backhand across the front to the high right: the right foot steps through and lands on the blow,
    // the body rising with the cut and opening to the right, the knife ending out to the right and up, its flat to
    // whoever looks from behind

    /** Its end: the knife up and out to the right, the chest open (where the third takes it up). */
    static final R6.Pose P2_END=on(.06,.58,-.12,-28,-4,3,d(60,104),d(34,92),d(-56,-48),.16,.84,-.20,.40);
    static final Anim P2=Anim.joints(
        k(0,P1_END),
        // the knife turned over the arm, down and back behind the left hip
        k(.04,on(-.08,.31,-.27,36,14,-5,d(-30,-80),d(-2,-86),d(-36,-146),.21,-.03,-.20,.40)),
        k(.075,on(-.09,.33,-.30,38,15,-5,d(-36,-66),d(-7,-120),d(-32,-138),.20,.12,-.20,.40)),
        k(.105,on(-.09,.34,-.31,38,16,-5,d(-36,-60),d(-8,-136),d(-30,-134),.19,.30,-.20,.40)),
        // let go: up out of the legs, the hips turning first
        k(.135f,on(-.07,.37,-.29,26,13,-3,d(-33,-52),d(-9,-129),d(-36,-122),.18,.52,-.20,.40)),
        k(.17f,on(-.02,.44,-.24,7,9,0,d(-10,-14),d(-10,-80),d(-48,-100),.17,.76,-.20,.40)),
        // the blow: the blade ahead across the middle at the chest, the arm led on up to the right
        k(blow(RISE,0),on(.02,.51,-.19,-9,4,2,d(28,26),d(-6,-15),d(-56,-76),.16,.84,-.20,.40)),
        k(.23f,on(.05,.55,-.15,-21,-1,3,d(50,62),d(10,22),d(-60,-60),.16,.84,-.20,.40)),
        k(.30f,on(.07,.58,-.11,-32,-5,3,d(63,100),d(30,84),d(-58,-50),.16,.84,-.20,.40)),
        k(step(RISE),P2_END),
        k(.62f,on(.03,.28,-.07,-10,-1,1,d(-28,84),d(20,50),d(-72,-30),.19,.10,-.20,.38)),
        k(.8f,STANCE)).stops(Anim.stop(blow(RISE,0),.025,.04)).after(natural(RISE));

    // ───────────────────────────── 3: 내려찍기 ─────────────────────────────
    // the knife swings on round the outside of the shoulder to behind it, the arm going up, the body rising tall under
    // it and arching back with the back foot gathered up; then down from the high right through the middle, levelling
    // off over the ground and drawn on across to the left hip, the body dropping into a deep stance as the back foot
    // shoots back

    /** Its plane: down through the middle, from the high right to the low left (a little off upright). */
    static final Swing S3=new Swing(d(8,-5),d(-68,-90));
    /** Its end: deep over the front foot, the knife low at the left (where the finisher takes it up). */
    static final R6.Pose P3_END=on(-.04,.74,-.38,29,27,-4,d(-32,-60),d(-7,-28),d(-36,-150),.16,.84,-.22,.22);
    static final Anim P3=Anim.joints(
        k(0,P2_END),
        k(.05,on(.06,.59,-.10,-31,-6,3,d(70,96),d(36,116),d(-40,-40),.16,.84,-.20,.44)),
        k(.10,on(.07,.61,-.07,-34,-8,3,d(72,88),d(34,140),d(-22,-28),.16,.84,-.19,.50)),
        // the top: the blade back behind the shoulder, the body tall, the free hand up ahead
        k(.16,on(.07,.62,-.05,-35,-10,3,S3.at(-86),S3.at(-141),d(-4,-14),.16,.84,-.18,.56)),
        k(.21,on(.07,.62,-.06,-36,-10,3,S3.at(-84),S3.at(-146),d(4,-10),.16,.84,-.18,.56)),
        // let go: the body drops and turns, the arm after it, the knife last
        k(.245f,on(.05,.65,-.13,-16,2,1,S3.at(-62),S3.at(-128),d(-10,-36),.16,.84,-.19,.46)),
        k(.275f,on(.02,.69,-.21,2,11,-1,S3.at(-24),S3.at(-80),d(-26,-90),.16,.84,-.21,.32)),
        // the blow: the blade down through the middle at the chest
        k(blow(CHOP,0),on(-.01,.72,-.29,17,19,-3,S3.at(22),S3.at(-12),d(-36,-132),.16,.84,-.22,.22)),
        // the cut levels off over the ground and is drawn on across to the left hip (the hand leading it there)
        k(.33f,on(-.03,.74,-.36,26,25,-4,d(-38,-36),d(-2,-10),d(-38,-148),.16,.84,-.22,.22)),
        k(.40f,on(-.05,.75,-.40,32,28,-4,d(-33,-66),d(-9,-34),d(-34,-156),.16,.84,-.22,.22)),
        k(step(CHOP),P3_END),
        k(.78f,on(-.02,.36,-.15,10,10,-1,d(-66,-6),d(-3,-8),d(-66,-70),.19,.20,-.20,.10)),
        k(.95f,STANCE)).stops(Anim.stop(blow(CHOP,0),.05,.07)).after(natural(CHOP));

    // ───────────────────────────── 4: 찌르기 ─────────────────────────────
    // the weight drawn back onto the back leg, the body turned side-on, the knife pulled back low past the right hip
    // with its point kept on the foe and the free hand out ahead; held; then the lunge: the back leg drives, the hips
    // and the shoulder turn in, the arm goes out last (the hand turning over as it goes, the blade ending canted),
    // the front foot shooting out to land on the blow and the back foot dragged after it, the free arm thrown back
    // and out to the left; driven on a little past the blow, held out, then drawn back and up, stepping back

    static final R6.Pose P4_BACK=on(-.08,.50,-.30,-38,8,3,d(-52,146),d(8,-8),d(2,-10),.16,.80,-.22,.22);
    static final Anim P4=Anim.joints(
        k(0,P3_END),
        k(.10,on(-.06,.60,-.34,-14,16,0,d(-72,30),d(1,-17),d(-20,-40),.16,.84,-.22,.22)),
        k(.20,P4_BACK),
        // held, sinking a little more
        k(.27,on(-.09,.47,-.32,-40,7,3,d(-50,150),d(8,-8),d(4,-8),.16,.80,-.22,.22)),
        // the lunge
        k(.31f,on(-.06,.58,-.33,-28,10,1,d(-54,128),d(8,-8),d(-2,-20),.15,.94,-.22,.23)),
        k(.36f,on(-.02,.86,-.35,0,18,-2,d(-44,44),d(7,-7),d(-14,-96),.14,1.24,-.22,.38)),
        // the blow: the whole body out behind the knife, still going
        k(blow(LUNGE,0),on(.01,1.07,-.36,20,22,-3,d(-18,-10),d(6,-6),d(-16,-138),.14,1.42,-.22,.56)),
        // furthest, then settled
        k(.44f,on(.02,1.24,-.37,31,25,-3,d(-9,-26),d(5,-5),d(-18,-132),.14,1.50,-.22,.68)),
        k(.56f,on(.02,1.20,-.36,28,23,-3,d(-10,-25),d(5,-5),d(-22,-134),.14,1.50,-.22,.68)),
        k(HOLD[LUNGE]/20f,on(.02,1.18,-.35,26,22,-2,d(-13,-20),d(5,-5),d(-28,-136),.14,1.50,-.22,.68)),
        // drawn back and up: the front foot steps back past the other, then that one
        k(.88f,on(.01,.84,-.24,12,14,-1,d(-40,30),d(4,-4),d(-52,-90),.16,.96,-.22,.68)),
        k(1.0f,on(0,.44,-.14,2,8,0,d(-58,52),d(2,-2),d(-66,-50),.19,.06,-.21,.66)),
        k(1.13f,on(0,.14,-.05,-2,3,0,d(-70,70),d(1,0),d(-80,-20),.19,0,-.20,.28)),
        k(1.25f,STANCE)).stops(Anim.stop(blow(LUNGE,0),.06,.08)).after(natural(LUNGE));

    @Override public Anim clip(int combo){return clipOf(combo);}
    /** The swing at hit {@code hit}'s place in the combo: the same with the hack on or off. */
    static Anim clipOf(int hit){if(hit==JAB)return CorruptionJab.CLIP;if(skill(hit))return GiantCleave.CLIP;return switch(place(hit)){case 2->P2;case 3->P3;case 4->P4;default->P1;};}

    /** (2.121.0) The cleave is drawn longer than a swing (what its blow leaves on the ground). */
    @Override public float lifetime(){return Math.max(LIFE,GiantCleave.LIFE);}
    /** How long hit {@code combo} is drawn for (seconds). */
    static float life(int combo){return combo==JAB?CorruptionJab.LIFE:skill(combo)?GiantCleave.LIFE:LIFE;}
    @Override public boolean layered(){return true;}
    /** None: the body is never copied behind itself (the hack's cut is the knife's own path, SlashFx). */
    @Override public float afterimages(float sec){return 0;}

    // ───────────────────────────── where things are ─────────────────────────────

    /** The fist and the knife's way {@code sec} into hit {@code hit}, for a user standing at {@code feet} facing {@code yaw}. */
    static Vec3d[] blade(Vec3d feet,float yaw,int hit,float sec){return KagenoClips.blade(feet,yaw,R6.poseOf(clipOf(hit).frame(sec)));}
    /** A point of the hit's frame: ahead, up and to the right of {@code feet} facing {@code yaw}. */
    static Vec3d at(Vec3d feet,float yaw,double fw,double up,double rt){
        Vec3d f=Vec3d.fromPolar(0,yaw);return feet.add(f.multiply(fw)).add(0,up,0).add(new Vec3d(-f.z,0,f.x).multiply(rt));
    }
    /**
     * Where its user stood as part {@code key} of swing {@code c} was first drawn: what it leaves stays there (a cut's
     * band does not slide after them as they walk on).
     */
    private static final Map<String,Vec3d> SPOTS=new LinkedHashMap<>();
    static Vec3d spot(Fx c,int key){
        String id=c.cast.id()+":"+key;Vec3d p=SPOTS.get(id);
        if(p==null){p=c.pos;SPOTS.put(id,p);while(SPOTS.size()>256)SPOTS.remove(SPOTS.keySet().iterator().next());}
        return p;
    }
    static void clear(){SPOTS.clear();MEMOS.clear();}

    // ───────────────────────────── a band along the knife's path ─────────────────────────────

    static final int SAMPLES=36;
    /** A band along a path: its inner and outer edges and how far along each point is (0 the tail, 1 the head). */
    record Swept(Vec3d[] inner,Vec3d[] outer,float[] along,int n){}
    /** The band the knife sweeps in hit {@code hit} from {@code from} to {@code head} seconds, its user at {@code feet} facing {@code yaw}. */
    static Swept swept(Vec3d feet,float yaw,int hit,float from,float head,double inner,double outer){
        int n=SAMPLES+1;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];double len=0;
        for(int i=0;i<n;i++){
            var hb=blade(feet,yaw,hit,from+(head-from)*i/(n-1));
            in[i]=hb[0].add(hb[1].multiply(inner));out[i]=hb[0].add(hb[1].multiply(outer));
            if(i>0)len+=out[i].distanceTo(out[i-1]);along[i]=(float)len;
        }
        for(int i=0;i<n;i++)along[i]=len>1e-6?(float)(along[i]/len):i/(float)(n-1);
        return new Swept(in,out,along,n);
    }
    /** How wide a cut's edge shows to the camera (blocks); the finisher's a quarter wider. */
    static final double EDGE_WIDE=.55;
    /**
     * A cut's edge as a strip turned to the camera: along the path of the band's outer edge (the knife's point),
     * {@code width} wide to one side of it (the band's, as far as the camera's view lets it; the same side all along
     * it, so it never twists). A band seen edge-on is a hairline (from right behind its user every cut across the front
     * is: its blade points away from them as it lands); with this strip a cut is a slash as thick from anywhere.
     */
    static Swept edge(Swept sw,Vec3d camera,double width){
        int n=sw.n();Vec3d[] side=new Vec3d[n];Vec3d last=null;double vote=0;
        for(int i=0;i<n;i++){
            Vec3d along=sw.outer()[Math.min(n-1,i+1)].subtract(sw.outer()[Math.max(0,i-1)]);
            Vec3d v=along.crossProduct(camera.subtract(sw.outer()[i]));
            if(v.lengthSquared()>=1e-10){
                v=v.normalize();
                // where the path turns back on itself as the camera sees it (it heads straight to or from the camera
                // there) the cross product changes sign: the strip keeps to the side it was on
                if(last!=null&&v.dotProduct(last)<0)v=v.negate();
                last=v;
            }
            side[i]=last;
            if(last!=null)vote+=last.dotProduct(sw.inner()[i].subtract(sw.outer()[i]));
        }
        if(last==null)return sw;
        Vec3d first=null;for(int i=0;i<n&&first==null;i++)first=side[i];
        Vec3d[] in=new Vec3d[n];double sign=vote<0?-width:width;
        for(int i=0;i<n;i++)in[i]=sw.outer()[i].add((side[i]==null?first:side[i]).multiply(sign));
        return new Swept(in,sw.outer(),sw.along(),n);
    }
    // ───────────────────────────── whom a blow struck ─────────────────────────────

    /** One struck by a blow, as they stood then: their feet, half their width and their height. */
    record Struck(int id,Vec3d feet,double half,double height){
        Vec3d mid(){return feet.add(0,height*.55,0);}
    }
    private record Memo(boolean told,List<Struck> all){}
    private static final Map<String,Memo> MEMOS=new LinkedHashMap<>();
    /**
     * Whom blow {@code i} of this swing struck, where they stood as it landed: found as it lands (whoever stands in its way
     * here, until the server's word comes a tick or two on) and kept, so what a blow leaves on them stays where it was.
     */
    static List<Struck> struck(Fx c,int i){
        String key=c.cast.id()+":"+i;Memo m=MEMOS.get(key);
        boolean told=KnifeClient.struck(c.cast.id(),i)!=null;
        if(m==null||told&&!m.told()){
            var all=new ArrayList<Struck>();
            for(var e:KnifeClient.struckBy(c.mc,c.cast,i)){
                // (one found before the server said: kept where they were found)
                Struck was=null;if(m!=null)for(var s:m.all())if(s.id()==e.getId())was=s;
                all.add(was!=null?was:new Struck(e.getId(),e.getLerpedPos(c.delta),e.getWidth()*.5,e.getHeight()));
            }
            m=new Memo(told,all);MEMOS.put(key,m);while(MEMOS.size()>256)MEMOS.remove(MEMOS.keySet().iterator().next());
        }
        return m.all();
    }
    /** A seed for what blow {@code i} of this swing leaves (the same on every client: the swing's start). */
    static int seed(Fx c,int i){return (int)(c.cast.start()*31+c.cast.combo()*7+i*13);}
    // ───────────────────────────── the passes ─────────────────────────────

    /** How strongly a cut shows in its user's own first person: it passes right before their eyes, and must not blind them. */
    static final float FIRST_PERSON=.4f;
    static float own(Fx c){return c.mc.player!=null&&c.cast.caster().equals(c.mc.player.getUuid())&&c.mc.options.getPerspective().isFirstPerson()?FIRST_PERSON:1;}
    /** The cut of a swing begun with the hack on (the effects' pass for X Chara's cuts: KagenoFire.glitchRibbon, the shader's mode 18). */
    @Override public void glitch(Fx c){
        // (a plain swing, the hack off, 2.117.0: nothing is drawn but the body and the knife)
        if(c.cast.combo()==JAB)return;
        if(skill(c.cast.combo())){GiantCleave.cut(c);return;}
        if(plain(c.cast.combo()))return;
        SlashFx.cut(c);
    }
    /** (2.122.0) The cleave's ground: the floor heaved up along the knife and its pieces thrown. */
    @Override public void solids(Fx c,dev.sangwoo.celestialfall.client.vfx.FxSolids s){if(c.cast.combo()==CLEAVE)GiantCleave.solids(c,s);}
    @Override public void alpha(Fx c,EffectMesh m){
        if(c.cast.combo()==JAB){CorruptionJab.draw(c,m,false);return;}
        if(skill(c.cast.combo())){GiantCleave.draw(c,m,false);return;}
        if(plain(c.cast.combo()))return;
        SlashFx.draw(c,m,false);
    }
    @Override public void additive(Fx c,EffectMesh m){
        if(c.cast.combo()==JAB){CorruptionJab.draw(c,m,true);return;}
        if(skill(c.cast.combo())){GiantCleave.draw(c,m,true);return;}
        if(plain(c.cast.combo()))return;
        SlashFx.draw(c,m,true);
    }

    // ───────────────────────────── light, shake ─────────────────────────────

    @Override public void frame(Fx c,FxFrame f){
        if(c.cast.combo()==JAB){CorruptionJab.frame(c,f);return;}
        if(skill(c.cast.combo())){GiantCleave.frame(c,f);return;}
        if(plain(c.cast.combo()))return;
        SlashFx.frame(c,f);
    }

    static float bump(float sec,float at,float tau){return sec<at-.02f?0:Ease.smooth(sec,at-.02f,at+.015f)*(float)Math.exp(-Math.max(0,sec-at-.015f)/tau);}
    /** How hard each swing's blow shakes the camera as it lands on someone, by its place in the combo (a cut that meets no one, a third of it). */
    static final float[] SHAKE={0,.35f,.35f,.55f,1.0f};
    @Override public float shake(StandClient.Cast cast,float sec){
        int hit=cast.combo();if(hit==JAB)return CorruptionJab.shake(cast,sec);
        if(skill(hit))return GiantCleave.shake(cast,sec);
        if(!valid(hit)||!KnifeClient.cameTo(cast,0))return 0;
        int[] told=KnifeClient.struck(cast.id(),0);
        return SHAKE[place(hit)]*(told!=null&&told.length>0?1:.33f)*bump(sec,blow(hit,0),.07f);
    }
}
