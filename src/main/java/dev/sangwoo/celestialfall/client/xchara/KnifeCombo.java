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
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.w;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.client.xchara.GlitchFx.*;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * X Chara's basic attack drawn: the body's motion and the effects of their knife's four hits (2.114.0, user request:
 * "엑스차라 평타 만들자 너가 창작해줘 엑스차라 컨셉은 언더테일에 나오는 차라가 해커의 능력을 사용하는거야 4타로 끝나고
 * 멋있게": mine to make up; XCharaCombo for its timing, KnifeClient for its swings). A knife fighter's quick cuts, facing
 * the front all through (the body only twists with the swing; the arm, the wrist and the free arm do the cutting, the
 * knife trailing the arm the way it goes so its edge leads), each with a hacker's trick:
 * <ol>
 * <li>잘라내기: wound up over the right shoulder, a diagonal cut down across the front to the low left.</li>
 * <li>복사·붙여넣기: wound to the left shoulder, a backhand cut level across the front to the right; then the free hand
 * thrown out ahead and the same cut pasted again further on (a pale ghost of the first).</li>
 * <li>연타: the knife drawn back by the hip and thrust three times, the last a lunge; each thrust throws a knife of
 * white light flying on ahead (where it ends it breaks into pixels) and stamps a popup window on what it strikes, the
 * three piling up as error messages do, then closing one after another.</li>
 * <li>삭제: a step back and the knife raised high, red gathering on it; a diagonal cut down to the low left, the knife
 * carried over, and a second down to the low right. Each cut stamps a red stroke on what it strikes: an X (the mark
 * that closes a window), which folds flat and goes out as an old screen does, and they are sent flying.</li>
 * </ol>
 * Every cut is a band along the knife's own path (as Kageno's are), drawn by the glitch shader (KagenoFire.glitchRibbon:
 * purple light, crisp, torn as corrupted data as it is cut and again as it goes, its colours splitting to red and cyan),
 * with its edge drawn once more as a strip turned to the camera (a band seen edge-on, as from right behind, would be a
 * hairline).
 * Whom a blow strikes: their box flashed as lines round them (a hitbox shown), bars torn across them, square pixels
 * flying off and little 0s and 1s rising (GlitchFx). No screen effects (no impact frame, no flash): everything is in the
 * world; the camera only shakes a little as a blow lands. Every effect is a function of the swing's clock, so it moves
 * at the render frame rate.
 */
public final class KnifeCombo implements Fx.Skill {
    /** How long a swing is drawn for (seconds): the finisher's motion and what its X leaves. */
    public static final float LIFE=2.2f;
    /**
     * The knife's display turns it this far round the wrist (models/item/x_chara_knife.json: its x rotation; none, 2.116.0:
     * the knife is square to the arm that hangs straight, so level ahead); the hand turns it on from there as a hit drives it.
     */
    public static final float DISPLAY_TILT=0;
    /** Blocks along the knife from the fist: its blade's heel and its point (twice its model's, as a hand holds it). */
    static final double HEEL=.37,TIP=2.63;
    /** The body's afterimages' purple. */
    static final int GHOST=0x8a14e8;

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

    // ───────────────────────────── 1 잘라내기 ─────────────────────────────
    // wound up over the right shoulder (twisted right, the weight back), then down across the front to the low left: the
    // arm leads, the knife trails it (to its right and above it), the body untwisting to the left and leaning in

    /** Its end: low at the left, leaning in (where the second hit takes it up). */
    static final R6.Pose H1_END=w(.34,-.26,30,16,-4,d(-44,-74),d(-6,-52),d(-36,-140),8,-166);
    static final Anim H1=Anim.joints(
        k(0,STANCE),
        k(.05,w(-.03,-.05,-20,-4,2,d(32,66),d(68,140),d(-52,-30),40,-150)),
        // wound furthest: the knife up behind the right shoulder
        k(.10,w(-.06,-.10,-30,-6,3,d(48,96),d(58,172),d(-42,-18),45,-150)),
        // the cut, fast out of the wind-up and slowing
        k(.135f,w(.06,-.14,-18,3,1,d(36,62),d(62,124),d(-46,-40),34,-154),Anim.Ease.IN),
        k(.165f,w(.2,-.2,-2,10,-1,d(6,12),d(36,62),d(-50,-72),22,-160),Anim.Ease.LINEAR),
        // the blow: the blade straight ahead across the front, the arm led on to the left
        k(blow(CUT,0),w(.3,-.24,14,14,-3,d(-18,-40),d(7,2),d(-48,-104),12,-164),Anim.Ease.LINEAR),
        k(.23f,w(.33,-.26,26,16,-4,d(-34,-62),d(-2,-30),d(-40,-130),8,-166),Anim.Ease.OUT),
        k(.28f,H1_END),
        k(step(CUT),H1_END),
        k(.56f,w(.16,-.1,10,6,-1,d(-64,-20),d(-2,-14),d(-70,-40),6,-168)),
        k(.7f,STANCE)).stops(Anim.stop(blow(CUT,0),.025,.04)).after(natural(CUT));

    // ───────────────────────────── 2 복사·붙여넣기 ─────────────────────────────
    // from the low left the knife is drawn up to the left shoulder, its point back over it, then cut backhand level
    // across the front to the right (the knife trailing to the arm's left); then the free hand is thrown out ahead, palm
    // at the cut pasted again

    /** Its end: the knife out to the right, the free hand ahead (where the third hit takes it up). */
    static final R6.Pose H2_END=w(.5,-.16,-14,10,2,d(10,100),d(14,70),d(2,-6),20,-160);
    static final Anim H2=Anim.joints(
        k(0,H1_END),
        // drawn up and back to the left shoulder
        k(.04,w(.31,-.22,33,10,-3,d(-6,-82),d(12,-112),d(-48,10),8,-166)),
        k(.08,w(.3,-.18,36,6,-2,d(12,-90),d(24,-152),d(-46,70),8,-166)),
        // the cut, backhand: out of the wind-up, fastest across the front
        k(.11,w(.32,-.18,30,7,-1,d(11,-70),d(20,-128),d(-48,50),9,-166),Anim.Ease.IN),
        k(.13,w(.36,-.19,18,9,1,d(8,-18),d(14,-75),d(-52,14),12,-165),Anim.Ease.LINEAR),
        // the blow: the blade across the middle line three blocks ahead (the fist is out to the right: the arm leads)
        k(blow(PASTE,0),w(.4,-.2,6,10,2,d(5,34),d(8,-17),d(-56,-20),14,-164),Anim.Ease.LINEAR),
        k(.18,w(.44,-.2,-18,10,3,d(7,76),d(10,40),d(-60,-60),18,-162),Anim.Ease.LINEAR),
        k(.22,w(.46,-.18,-30,8,4,d(12,98),d(13,64),d(-58,-80),20,-160),Anim.Ease.OUT),
        // the paste: the free hand drawn in, then thrown out ahead
        k(.26,w(.46,-.16,-26,8,3,d(14,104),d(15,72),d(-24,-30),20,-160)),
        k(blow(PASTE,1),H2_END,Anim.Ease.OUT),
        k(step(PASTE),H2_END),
        k(.6f,w(.24,-.08,-6,4,1,d(-50,50),d(6,30),d(-60,-10),12,-166)),
        k(.75f,STANCE)).stops(Anim.stop(blow(PASTE,0),.02,.04)).after(natural(PASTE));

    // ───────────────────────────── 3 연타 ─────────────────────────────
    // the knife drawn back low by the right hip, its point at the foe, the body twisted right and low; three thrusts
    // (the arm up and out from the hip, the body turning into each), drawn back between them, the last a lunge

    /** Drawn back by the hip. */
    static R6.Pose chamber(double fw,float yaw){return w(fw,-.25,yaw,10,3,d(-52,78),d(5,3),d(-12,-12),28,-152);}
    /** Its end, where the fourth hit takes it up. */
    static final R6.Pose H3_END=w(.56,-.2,8,10,0,d(-30,10),d(8,0),d(-60,-60),12,-168);
    static final Anim H3=Anim.joints(
        k(0,H2_END),
        k(.06,chamber(.24,-32)),
        k(.10,chamber(.2,-36)),
        k(.13,w(.4,-.26,-6,14,0,d(-36,30),d(7,2),d(-40,-80),16,-164),Anim.Ease.IN),
        k(blow(FLOOD,0),w(.54,-.26,14,16,-1,d(-24,-6),d(9,-1),d(-50,-120),12,-167),Anim.Ease.LINEAR),
        k(.20,chamber(.36,-30)),
        k(.23,w(.5,-.26,-2,14,0,d(-34,24),d(11,-3),d(-40,-80),14,-166),Anim.Ease.IN),
        k(blow(FLOOD,1),w(.62,-.26,16,16,-1,d(-18,-12),d(14,-6),d(-50,-120),10,-168),Anim.Ease.LINEAR),
        k(.30,chamber(.44,-32)),
        k(.33,w(.62,-.3,0,18,0,d(-38,26),d(5,2),d(-40,-90),12,-168),Anim.Ease.IN),
        k(blow(FLOOD,2),w(.82,-.32,22,22,-2,d(-22,-8),d(8,-3),d(-46,-140),8,-170),Anim.Ease.LINEAR),
        k(.45,w(.82,-.32,20,21,-2,d(-24,-6),d(7,-2),d(-46,-136),8,-170)),
        k(step(FLOOD),H3_END),
        k(.75f,w(.3,-.1,2,5,0,d(-58,16),d(4,0),d(-74,-24),8,-169)),
        k(.9f,STANCE)).stops(Anim.stop(blow(FLOOD,2),.03,.05)).after(natural(FLOOD));

    // ───────────────────────────── 4 삭제 ─────────────────────────────
    // a step back, the knife raised high behind the right shoulder (held there, sinking: red gathers on it); the first
    // cut down across the front to the low left, a step in; the wrist rolled, the knife carried up over the left
    // shoulder; the second cut down across the front to the low right, another step; held low, then up

    static final R6.Pose H4_HIGH=w(-.14,-.3,-38,-8,5,d(58,116),d(36,-176),d(-16,34),155,-15);
    static final R6.Pose H4_END=w(.8,-.36,-32,20,5,d(-44,76),d(-5,52),d(-36,10),24,-164);
    static final Anim H4=Anim.joints(
        k(0,H3_END),
        k(.08,w(.1,-.12,-20,-4,2,d(30,80),d(60,150),d(-30,20),150,-20)),
        k(.14,w(-.1,-.22,-34,-8,4,d(55,110),d(40,178),d(-20,30),155,-15)),
        k(.26,H4_HIGH),
        k(.29,w(.1,-.3,-22,6,2,d(44,70),d(60,130),d(-30,0),150,-18),Anim.Ease.IN),
        k(.32,w(.3,-.32,0,14,-1,d(8,10),d(38,60),d(-44,-60),120,-20),Anim.Ease.LINEAR),
        // the first blow: the blade straight ahead, the arm led on to the left
        k(blow(DELETE,0),w(.45,-.34,20,18,-4,d(-16,-40),d(9,2),d(-44,-110),90,-24),Anim.Ease.LINEAR),
        k(.385,w(.5,-.34,32,18,-5,d(-36,-66),d(-3,-36),d(-36,-140),80,-28),Anim.Ease.OUT),
        // over: the wrist rolled, the knife carried round the left side and up over the left shoulder
        k(.42,w(.5,-.32,35,13,-4,d(-16,-84),d(10,-112),d(-40,-150),72,-38)),
        k(.455,w(.5,-.28,37,6,-3,d(20,-80),d(44,-140),d(-46,170),64,-58)),
        k(.49,w(.5,-.26,38,2,-3,d(40,-68),d(62,-146),d(-50,160),60,-70)),
        k(.52,w(.6,-.3,20,10,-1,d(24,-36),d(52,-96),d(-50,120),44,-110),Anim.Ease.IN),
        // the second blow: the blade across the middle line three blocks ahead, the arm led on to the right
        k(blow(DELETE,1),w(.72,-.36,-6,18,2,d(-14,30),d(10,-22),d(-44,60),32,-140),Anim.Ease.LINEAR),
        k(.585,w(.78,-.36,-26,20,4,d(-34,62),d(-2,30),d(-40,20),26,-160),Anim.Ease.OUT),
        k(.64,H4_END),
        k(HOLD[DELETE]/20f,H4_END),
        k(1.05f,w(.36,-.14,-10,6,1,d(-62,40),d(-1,20),d(-72,-12),18,-166)),
        k(1.3f,STANCE)).stops(Anim.stop(blow(DELETE,0),.02,.03),Anim.stop(blow(DELETE,1),.05,.07)).after(natural(DELETE));


    // ══════════════════════════════ the plain swings (the hack off, 2.117.0) ══════════════════════════════
    // (user request: "이번에는 Q 껐을때 평타를 만들어줘 이펙트 없이 그냥 휘두르기만해 그대신 모션을 좋게 만들어봐")
    // Nothing is drawn for these but the body and the knife, so the body carries them. Each swing is wound up against
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

    // ───────────────────────────── plain 1: 가로베기 ─────────────────────────────
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

    // ───────────────────────────── plain 2: 올려베기 ─────────────────────────────
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

    // ───────────────────────────── plain 3: 내려찍기 ─────────────────────────────
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

    // ───────────────────────────── plain 4: 찌르기 ─────────────────────────────
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
    static Anim clipOf(int hit){return switch(hit){case PASTE->H2;case FLOOD->H3;case DELETE->H4;case SLASH->P1;case RISE->P2;case CHOP->P3;case LUNGE->P4;default->H1;};}

    @Override public float lifetime(){return LIFE;}
    @Override public boolean layered(){return true;}
    /** Faint: the knife arm trails a copy or two as it cuts, and the body is never hidden under them. */
    @Override public float afterimages(float sec){return .3f;}
    /** None for a plain swing (the hack off, 2.117.0: no effects at all). */
    @Override public float afterimages(Fx c){return plain(c.cast.combo())?0:afterimages(c.sec);}
    @Override public int afterimageColor(){return GHOST;}

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

    // ───────────────────────────── the cuts ─────────────────────────────

    /**
     * One cut: drawn along the knife's own path from {@code from} to {@code to} (seconds into its hit), {@code inner} to
     * {@code outer} blocks along the knife from the fist; eaten from its tail from {@code eat0} to {@code eat1}; its style
     * (KagenoFire.glitchRibbon) and seed.
     */
    record Cut(float from,float to,float eat0,float eat1,double inner,double outer,int style,int seed){}
    static final Cut[] H1_CUTS={new Cut(.12f,.27f,.3f,.52f,.8,2.8,0,3)};
    static final Cut[] H2_CUTS={new Cut(.085f,.225f,.26f,.46f,.8,2.8,0,7)};
    /** The finisher's two: the first stays until the second has crossed it. */
    static final Cut[] H4_CUTS={new Cut(.275f,.395f,.62f,.78f,.7,2.9,1,5),new Cut(.495f,.61f,.68f,.88f,.7,2.9,1,11)};
    static final Cut[] NONE={};
    static Cut[] cuts(int hit){return switch(hit){case CUT->H1_CUTS;case PASTE->H2_CUTS;case DELETE->H4_CUTS;default->NONE;};}
    /** The second hit's cut pasted again: it shows as its blow lands, and is eaten in its turn. */
    static final float PASTE_AT=blow(PASTE,1),PASTE_EAT0=.46f,PASTE_EAT1=.66f;
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
    /**
     * How torn a cut is at {@code t} (0 whole, 1 torn right apart): torn as it is cut and whole as the cut ends, torn
     * apart again as it goes, and now and then a twitch between.
     */
    static float tear(float t,float from,float to,float eat0,float eat1,int seed){
        float in=.95f*(1-Ease.smooth(t,from+.01f,to+.04f)),out=.9f*Ease.smooth(t,eat0-.04f,eat1);
        float twitch=Ease.hash((int)Math.floor(t*20)*31+seed*7)>.8?.5f:0;
        return Math.max(.1f,Math.max(Math.max(in,out),twitch));
    }
    /** How strongly a cut shows in its user's own first person: it passes right before their eyes, and must not blind them. */
    static final float FIRST_PERSON=.4f;
    @Override public void glitch(Fx c){
        // (a plain swing, the hack off, 2.117.0: nothing is drawn but the body and the knife)
        if(plain(c.cast.combo()))return;
        int hit=c.cast.combo();float t=c.sec,until=KnifeClient.until(c.cast.id());Cut[] cuts=cuts(hit);
        float own=c.mc.player!=null&&c.cast.caster().equals(c.mc.player.getUuid())&&c.mc.options.getPerspective().isFirstPerson()?FIRST_PERSON:1;
        for(int k=0;k<cuts.length;k++){
            Cut s=cuts[k];
            if(t<s.from()||t>=s.eat1()||s.from()>=until)continue;
            // (stopped short, the cut ends where the knife was)
            float head=Math.min(Math.min(t,s.to()),until);if(head<=s.from()+.004f)continue;
            var sw=swept(spot(c,k),c.cast.yaw(),hit,s.from(),head,s.inner(),s.outer());
            float eaten=Ease.smooth(t,s.eat0(),s.eat1()),torn=tear(t,s.from(),s.to(),s.eat0(),s.eat1(),s.seed()),shown=own*Ease.smooth(t,s.from(),s.from()+.015f);
            KagenoFire.glitchRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),eaten,torn,shown);
            // its edge turned to the camera: a slash as thick from anywhere (from right behind, the band itself is edge-on)
            var e=edge(sw,c.camera,s.style()==1?EDGE_WIDE*1.25:EDGE_WIDE);
            KagenoFire.glitchRibbon(e.inner(),e.outer(),e.along(),e.n(),s.style(),s.seed()+1,eaten,torn,shown);
        }
        // the second hit's cut pasted again further on: all of it at once, torn as it comes
        if(hit==PASTE&&KnifeClient.cameTo(c.cast,1)&&t>=PASTE_AT-.02f&&t<PASTE_EAT1){
            Cut s=H2_CUTS[0];var sw=pasted(spot(c,0),c.cast.yaw());
            float in=Math.max(.95f*(1-Ease.smooth(t,PASTE_AT,PASTE_AT+.1f)),.9f*Ease.smooth(t,PASTE_EAT0-.04f,PASTE_EAT1));
            float twitch=Ease.hash((int)Math.floor(t*20)*17+5)>.7?.55f:0;
            float eaten=Ease.smooth(t,PASTE_EAT0,PASTE_EAT1),torn=Math.max(.14f,Math.max(in,twitch)),shown=own*Ease.smooth(t,PASTE_AT-.02f,PASTE_AT+.005f);
            KagenoFire.glitchRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),2,s.seed()+2,eaten,torn,shown);
            var e=edge(sw,c.camera,EDGE_WIDE);
            KagenoFire.glitchRibbon(e.inner(),e.outer(),e.along(),e.n(),2,s.seed()+3,eaten,torn,shown);
        }
    }
    /** The second hit's whole cut as it is pasted again: PASTE_AHEAD further on, a little bigger about its middle. */
    static Swept pasted(Vec3d feet,float yaw){
        Cut s=H2_CUTS[0];var sw=swept(feet,yaw,PASTE,s.from(),s.to(),s.inner(),s.outer());
        Vec3d on=Vec3d.fromPolar(0,yaw).multiply(PASTE_AHEAD),mid=sw.outer()[sw.n()/2];
        Vec3d[] in=new Vec3d[sw.n()],out=new Vec3d[sw.n()];
        for(int i=0;i<sw.n();i++){in[i]=mid.add(sw.inner()[i].subtract(mid).multiply(PASTE_SCALE)).add(on);out[i]=mid.add(sw.outer()[i].subtract(mid).multiply(PASTE_SCALE)).add(on);}
        return new Swept(in,out,sw.along(),sw.n());
    }
    static final double PASTE_SCALE=1.08;

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
    /** How big what a blow leaves is: the finisher's last the biggest. */
    static double big(int hit,int i){return launches(hit,i)?1.5:hit==DELETE?1.15:hit==FLOOD?.8:1;}

    /** What each blow leaves on whom it struck, in one pass: their box, the bars torn across them, pixels and digits. */
    private static void impacts(Fx c,EffectMesh m,boolean light){
        int hit=c.cast.combo();float t=c.sec;
        for(int i=0;i<blows(hit);i++){
            float s=t-blow(hit,i);if(s<0||s>=.75f||!KnifeClient.cameTo(c.cast,i))continue;
            int seed=seed(c,i);double big=big(hit,i);Vec3d way=c.f;
            for(var e:struck(c,i)){
                box(m,e.feet(),e.half(),e.height(),s,seed+e.id(),light,c.camera);
                tears(m,e.feet().add(0,e.height()*.5,0),e.half(),e.height(),seed+e.id()*3,s,big,light,c.camera);
                // pixels off them the way the blow went and up; a few 0s and 1s rising
                pixels(m,e.mid(),way.add(0,.35,0).normalize(),.75,5.5*big,.055*big,(int)(14*big),seed+e.id()*5,s,light,c.camera);
                digits(m,e.feet().add(0,e.height()*.6,0),e.half()+.35,e.height()*.5,hit==DELETE?6:3,seed+e.id()*7,s,light,c.camera);
            }
        }
    }

    // ───────────────────────────── the passes ─────────────────────────────

    @Override public void alpha(Fx c,EffectMesh m){
        if(plain(c.cast.combo()))return;
        impacts(c,m,false);
        switch(c.cast.combo()){
            case PASTE->paste(c,m,false);
            case FLOOD->{thrusts(c,m,false);windows(c,m,false);}
            case DELETE->{gather(c,m,false);cross(c,m,false);}
            default->{}
        }
    }
    @Override public void additive(Fx c,EffectMesh m){
        if(plain(c.cast.combo()))return;
        aura(c,m);
        impacts(c,m,true);
        switch(c.cast.combo()){
            case PASTE->paste(c,m,true);
            case FLOOD->{thrusts(c,m,true);windows(c,m,true);}
            case DELETE->{gather(c,m,true);cross(c,m,true);}
            default->{}
        }
    }
    /** How brightly the knife glows at {@code t} of hit {@code hit}: lit while it cuts or thrusts, the finisher's from its wind-up on. */
    static float glow(int hit,float t){
        return switch(hit){
            case CUT->Ease.window(t,.09f,.34f,.04f,.1f);
            case PASTE->Ease.window(t,.06f,.36f,.04f,.1f);
            case FLOOD->Ease.window(t,.08f,.5f,.04f,.12f);
            default->Ease.window(t,.1f,.78f,.06f,.14f);
        };
    }
    /** The knife's glow while it cuts: purple along its edge, a red copy and a cyan one twitching aside (its colours split); the finisher's red. */
    private static void aura(Fx c,EffectMesh m){
        int hit=c.cast.combo();float t=c.sec;if(t>=KnifeClient.until(c.cast.id())+.05f)return;
        float g=glow(hit,t);if(g<=.01f)return;
        var hb=blade(c.pos,c.cast.yaw(),hit,t);Vec3d heel=hb[0].add(hb[1].multiply(HEEL)),tip=hb[0].add(hb[1].multiply(TIP));
        float near=near(tip,c.camera)*near(heel,c.camera);if(near<=0)return;g*=near;
        int rgb=hit==DELETE?RED:PURPLE;
        m.softLine(heel,tip,.9,hit==DELETE?0xc0143c:GLOW,.22f*g);
        m.line(heel,tip,.05,rgb,.6f*g);
        var f=facing(c.camera,tip);
        double split=.05*(Ease.hash((int)Math.floor(t*30)+hit*7)>.5?1:-1);
        m.line(heel.add(f[0].multiply(split)),tip.add(f[0].multiply(split)),.03,RED,.45f*g);
        m.line(heel.subtract(f[0].multiply(split)),tip.subtract(f[0].multiply(split)),.03,CYAN,.45f*g);
        m.softDisc(tip,.16,WHITE,.5f*g);
    }

    // ───────────────────────────── 2: the paste ─────────────────────────────

    /** As the cut is pasted again: a flash at the free hand, a knife of light lying along the copy for a moment, pixels off the copy's head. */
    private static void paste(Fx c,EffectMesh m,boolean light){
        float s=c.sec-PASTE_AT;if(s<-.04f||s>=.6f||!KnifeClient.cameTo(c.cast,1))return;
        Vec3d feet=spot(c,0);float yaw=c.cast.yaw();
        var sw=pasted(feet,yaw);int n=sw.n();
        // the free hand, thrown out: a square flash on it
        if(s<.12f){
            Vec3d hand=KagenoClips.freeHand(c.pos,yaw,R6.poseOf(H2.frame(c.sec)));
            float a=Ease.smooth(s,-.04f,0)*(1-Ease.smooth(s,.04f,.12f))*near(hand,c.camera);
            var f=facing(c.camera,hand);
            if(light){square(m,hand,f,.3,GLOW,.4f*a);square(m,hand,f,.12,WHITE,.7f*a);}
            else GlitchFx.frame(m,hand.add(c.f.multiply(.1)),f[0],f[1],.16+.9*Ease.smooth(s,0,.1f),0,.03,WHITE,.9f*a);
        }
        // a knife of light where the copy's cut ends, along the way it went
        if(s>=0&&s<.1f){
            Vec3d head=sw.outer()[n-1],before=sw.outer()[n-4],aim=head.subtract(before);
            if(aim.lengthSquared()>1e-8){
                aim=aim.normalize();Vec3d at=sw.inner()[n-1].add(head).multiply(.5);
                float a=(1-Ease.smooth(s,.04f,.1f))*near(at,c.camera);
                Vec3d point=head.subtract(sw.inner()[n-1]).normalize();
                if(light){knife(m,at,point,2.2,c.camera,PURPLE,.4f*a);}
                else knife(m,at,point,1.9,c.camera,0xf6f0ff,.85f*a);
            }
        }
        // pixels off the copy as it comes
        if(s>=0)pixels(m,sw.outer()[n/2],c.f.add(0,.3,0).normalize(),.9,4.5,.05,12,seed(c,1)+91,s,light,c.camera);
    }

    // ───────────────────────────── 3: the thrusts ─────────────────────────────

    /** Where each thrust's knife of light flies: up and to the right of the user's feet (its lane). */
    static final double[][] LANE={{1.15,.1},{1.45,-.22},{.98,.2}};
    /** How long a thrust's knife of light is (blocks, its rod's end to its point). */
    static final double KNIFE_LONG=2.3;
    /**
     * How far ahead of its user's feet the knife of light's middle is as it leaves (it comes off the knife itself, along
     * it) and where it ends: its point at the end of the server's reach (XCharaCombo.KNIFE_RANGE).
     */
    static final double THROW_FROM=2.6,THROW_TO=KNIFE_RANGE-KNIFE_LONG*.5;
    /** How long a thrown knife flies (seconds), and how long its trail and what it leaves last after. */
    static final float FLIGHT=.09f,AFTER=.22f;
    /** How far ahead of its user's feet a thrust's knife of light (its middle) is {@code s} seconds after its blow (fast at first, slowing into its end). */
    static double flown(float s){
        double u=Math.max(0,Math.min(1,s/FLIGHT));
        return THROW_FROM+(THROW_TO-THROW_FROM)*(1-(1-u)*(1-u));
    }
    private static void thrusts(Fx c,EffectMesh m,boolean light){
        float t=c.sec;
        for(int i=0;i<blows(FLOOD);i++){
            float s=t-blow(FLOOD,i);if(s<-.03f||s>=FLIGHT+AFTER+PIXEL_LIFE||!KnifeClient.cameTo(c.cast,i))continue;
            Vec3d feet=spot(c,10+i);float yaw=c.cast.yaw();Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
            Vec3d from=at(feet,yaw,THROW_FROM,LANE[i][0],LANE[i][1]),end=at(feet,yaw,THROW_TO,LANE[i][0],LANE[i][1]);
            // the thrust itself: a streak from the fist out past the knife's point, gone in a moment
            if(s<.07f){
                var hb=blade(c.pos,yaw,FLOOD,blow(FLOOD,i));
                float a=Ease.smooth(s,-.03f,0)*(1-Ease.smooth(s,.02f,.07f))*near(hb[0],c.camera);
                Vec3d tip=hb[0].add(hb[1].multiply(TIP+.5));
                if(light){m.needle(hb[0],tip,.34,.75,PURPLE,.5f*a);m.needle(hb[0],tip,.1,.75,WHITE,.9f*a);}
            }
            if(s<0)continue;
            double head=flown(s);Vec3d at=at(feet,yaw,head,LANE[i][0],LANE[i][1]);
            float flying=1-Ease.smooth(s,FLIGHT,FLIGHT+.04f);
            // the knife of light: white, a purple glow, its red and cyan copies aside as it leaves
            if(flying>0){
                float a=flying*near(at,c.camera);
                if(light){knife(m,at,f,KNIFE_LONG*1.2,c.camera,PURPLE,.5f*a);knife(m,at,f,KNIFE_LONG*1.45,c.camera,GLOW,.18f*a);}
                else{
                    var face=facing(c.camera,at);double split=.12*(1-Ease.smooth(s,0,FLIGHT));
                    knife(m,at.add(face[0].multiply(split)),f,KNIFE_LONG,c.camera,RED,.7f*a);knife(m,at.subtract(face[0].multiply(split)),f,KNIFE_LONG,c.camera,CYAN,.7f*a);
                    knife(m,at,f,KNIFE_LONG,c.camera,0xf6f0ff,.97f*a);
                }
            }
            // its trail: a line of light from where it left to where it is, fading from its tail
            float trail=1-Ease.smooth(s,FLIGHT,FLIGHT+AFTER);
            if(trail>0&&light){
                Vec3d tail=from.add(at.subtract(from).multiply(Ease.smooth(s,FLIGHT*.4f,FLIGHT+AFTER*.8f)));
                float a=trail*near(at,c.camera);
                m.needle(tail,at,.26,.9,PURPLE,.45f*a);m.needle(tail,at,.07,.9,WHITE,.8f*a);
            }
            // where it ends it breaks into pixels (deleted), going on a little the way it flew
            float over=s-FLIGHT;
            if(over>=0){
                pixels(m,end,f,.55,6,.06,i==2?16:11,seed(c,i)+57,over,light,c.camera);
                if(light&&over<.1f)square(m,end,facing(c.camera,end),.34*(1+over*4),GLOW,.4f*(1-over/.1f)*near(end,c.camera));
            }
        }
    }
    // ───────────────────────────── 3: the popups ─────────────────────────────

    /** When the popups start closing (the last opened first, WINDOW_STEP apart): a moment after the last thrust. */
    static final float WINDOWS_SHUT=blow(FLOOD,2)+.15f,WINDOW_STEP=.03f;
    /** Half a popup's width and height (blocks): each a little bigger than the one before. */
    static double windowWide(int i){return .8+.07*i;}
    static double windowHigh(int i){return .56+.05*i;}
    /** How long after its blow a thrust's knife of light (its middle) is {@code ahead} blocks ahead of its user (0 nearer than it leaves from; FLIGHT at its end). */
    static float reaches(double ahead){
        double u=Math.max(0,Math.min(1,(ahead-THROW_FROM)/(THROW_TO-THROW_FROM)));
        return (float)(FLIGHT*(1-Math.sqrt(1-u)));
    }
    /**
     * Where thrust {@code i}'s popup is stamped and how far ahead of its user that is: this side of whom it struck (the
     * first of them), or where its knife of light ends; each a step down and to the right of the one before and a
     * little nearer its user (they pile up as error messages do).
     */
    static Vec3d windowAt(Vec3d feet,float yaw,Vec3d struckMid,double struckHalf,int i){
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        Vec3d base=struckMid!=null?struckMid.subtract(f.multiply(struckHalf+.45)):at(feet,yaw,THROW_TO,1.25,0);
        return base.add(right.multiply(-.36+.36*i)).add(0,.26-.22*i,0).subtract(f.multiply(.12*i));
    }
    /**
     * The popups: each thrust stamps one where its knife of light strikes (it snaps open as the knife gets there, shoved
     * aside and its colours split for a moment), the three piling up; a moment after the last thrust they close, the
     * last opened first (each folds flat to a line and breaks into pixels). A swing stopped short closes what it opened.
     */
    private static void windows(Fx c,EffectMesh m,boolean light){
        float t=c.sec,until=KnifeClient.until(c.cast.id());float yaw=c.cast.yaw();
        Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x);
        for(int i=0;i<blows(FLOOD);i++){
            if(!KnifeClient.cameTo(c.cast,i)||t<blow(FLOOD,i))continue;
            Vec3d feet=spot(c,10+i);var all=struck(c,i);
            Vec3d at=windowAt(feet,yaw,all.isEmpty()?null:all.get(0).mid(),all.isEmpty()?0:all.get(0).half(),i);
            float popped=blow(FLOOD,i)+reaches(at.subtract(feet).dotProduct(f));
            float shutAt=Math.min(WINDOWS_SHUT+WINDOW_STEP*(blows(FLOOD)-1-i),Math.max(until,popped)+.04f);
            float s=t-popped,shut=t-shutAt;if(s<=0)continue;
            int seed=seed(c,i)+71;
            if(shut<WINDOW_SHUT)window(m,at,right,windowWide(i),windowHigh(i),s,shut,seed,i==blows(FLOOD)-1?RED:PURPLE,light,c.camera);
            // closed: pixels from where it was
            pixels(m,at,f.multiply(-1).add(0,.25,0).normalize(),.95,4.2,.05,10,seed+5,shut-WINDOW_SHUT*.7f,light,c.camera);
        }
    }

    // ───────────────────────────── 4: red gathering, and the X ─────────────────────────────

    /** The wind-up: red pixels drawn in onto the raised knife, a red glow swelling on it (until the first cut). */
    private static void gather(Fx c,EffectMesh m,boolean light){
        float t=c.sec;if(t<.1f||t>=.3f||t>=KnifeClient.until(c.cast.id()))return;
        var hb=blade(c.pos,c.cast.yaw(),DELETE,t);Vec3d on=hb[0].add(hb[1].multiply((HEEL+TIP)*.5));
        float near=near(on,c.camera);if(near<=0)return;
        float a=Ease.smooth(t,.1f,.16f)*(1-Ease.smooth(t,.27f,.3f))*near;
        var f=facing(c.camera,on);
        if(light){m.softDisc(on,.5+.5*Ease.smooth(t,.1f,.27f),RED,.3f*a);m.softLine(hb[0].add(hb[1].multiply(HEEL)),hb[0].add(hb[1].multiply(TIP)),.7,RED,.35f*a);}
        int seed=(int)(c.cast.start()*17);
        for(int j=0;j<18;j++){
            float born=.1f+.14f*(float)h(j,0,seed,41),age=t-born,life=.09f;if(age<0||age>=life)continue;
            double u=age/life,r=(1.5+.9*h(j,0,seed,42))*(1-u)*(1-u);
            Vec3d any=new Vec3d(h(j,0,seed,43)-.5,h(j,0,seed,44)-.5,h(j,0,seed,45)-.5).normalize();
            Vec3d p=hb[0].add(hb[1].multiply(HEEL+(TIP-HEEL)*h(j,0,seed,46))).add(any.multiply(r));
            if(light)square(m,p,f,.1,RED,.3f*a);
            else{square(m,p,f,.052,INK,.9f*a);square(m,p,f,.04,j%3==0?WHITE:RED,a);}
        }
    }
    /** When the X is whole and holds, when it starts going out, and when the first stroke goes if the second never comes. */
    static final float X_HOLD=.14f,X_CLOSE=blow(DELETE,1)+X_HOLD,X_ALONE=.22f;
    /** Where a stroke of the X is stamped: on whom its cut struck (the first of them), or ahead of its user where the cuts cross. */
    private static Vec3d crossAt(Fx c,int i){
        var all=struck(c,i);
        if(all.isEmpty()&&i==1)all=struck(c,0);
        if(!all.isEmpty())return all.get(0).mid();
        return at(spot(c,20),c.cast.yaw(),2.5,1.25,0);
    }
    /**
     * The X: each cut stamps a red stroke on whom it struck (the first from the high right down to the low left, the
     * second from the high left down to the low right: drawn fast along the cut's way); whole, it holds a moment, folds
     * flat to a white line and the line draws in to a point (GlitchFx.closing), and pixels fly from where it was.
     */
    private static void cross(Fx c,EffectMesh m,boolean light){
        float t=c.sec;boolean both=KnifeClient.cameTo(c.cast,1);
        for(int i=0;i<2;i++){
            float s=t-blow(DELETE,i);if(s<0||!KnifeClient.cameTo(c.cast,i))continue;
            Vec3d at=crossAt(c,i);float near=near(at,c.camera);if(near<=0)continue;
            double big=struck(c,i).isEmpty()&&struck(c,1-i).isEmpty()?.85:1;
            float out=both?t-X_CLOSE:s-X_ALONE;
            double[] z=closing(out);if(z[0]<=0)continue;
            float alpha=near*(both?1:1-Ease.smooth(s,X_ALONE-.06f,X_ALONE));
            stroke(m,at,c.right,i==0?1:-1,Ease.smooth(s,0,X_DRAW),z[0],z[1],big,alpha,light,seed(c,i),t);
            if(light){
                // the stroke's flash as it is drawn, and the white line it folds to
                if(s<.08f)m.softDisc(at,1.1*big,RED,.3f*(1-s/.08f)*near);
                if(out>0&&both&&i==1){
                    double wide=X_HALF*big*Math.sqrt(.5)*z[0];
                    m.needle(at.subtract(c.right.multiply(wide)),at.add(c.right.multiply(wide)),.16,.5,WHITE,.95f*near);
                    m.softDisc(at,.5+.4*z[0],WHITE,.35f*near);
                }
            }
        }
        // gone: pixels from where it was, and a glint
        if(both){
            float s=t-(X_CLOSE+X_FOLD+X_OUT);
            if(s>=0){
                Vec3d at=crossAt(c,1);
                pixels(m,at,c.f.add(0,.2,0).normalize(),1,7,.065,22,seed(c,1)+33,s,light,c.camera);
                if(light&&s<.08f)dev.sangwoo.celestialfall.client.kageno.FxLib.flash(m,at,.7,RED,(1-s/.08f)*near(at,c.camera));
            }
        }
        // sent flying: bars torn across them as they go (a moment more)
        if(both&&!light){
            float s=t-blow(DELETE,1);
            if(s>=TEAR_LIFE&&s<.42f)for(LivingEntity e:c.targets){
                boolean thrown=false;for(var st:struck(c,1))if(st.id()==e.getId())thrown=true;
                if(!thrown||!e.isAlive())continue;
                Vec3d mid=e.getLerpedPos(c.delta).add(0,e.getHeight()*.5,0);
                float cycle=(s-TEAR_LIFE)%.09f;
                tears(m,mid,e.getWidth()*.5,e.getHeight(),seed(c,1)+(int)((s-TEAR_LIFE)/.09f)*13,cycle,.8,false,c.camera);
            }
        }
    }

    // ───────────────────────────── light, shake ─────────────────────────────

    @Override public void frame(Fx c,FxFrame f){
        if(plain(c.cast.combo()))return;
        int hit=c.cast.combo();float t=c.sec;
        // fast cuts: short afterglow, as the effects' other fast slashes (their bands would leave copies of themselves)
        boolean cutting=false;for(Cut s:cuts(hit))cutting|=t>=s.from()-.02f&&t<s.to()+.06f;
        if(cutting||hit==FLOOD&&t<.55f)f.afterglow(.15f);
        float g=glow(hit,t);
        if(g>.01f&&t<KnifeClient.until(c.cast.id())){var hb=blade(c.pos,c.cast.yaw(),hit,t);f.light(hb[0].add(hb[1].multiply(TIP*.7)),hit==DELETE?0xd0204a:0x8a2be2,5,.45f*g);}
        for(int i=0;i<blows(hit);i++){
            float s=t-blow(hit,i);if(s<0||s>=.3f||!KnifeClient.cameTo(c.cast,i))continue;
            for(var e:struck(c,i))f.light(e.mid(),launches(hit,i)?0xff3050:0x9a3cff,launches(hit,i)?10:7,(launches(hit,i)?1.3f:.8f)*(1-s/.3f));
        }
    }
    static float bump(float sec,float at,float tau){return sec<at-.02f?0:Ease.smooth(sec,at-.02f,at+.015f)*(float)Math.exp(-Math.max(0,sec-at-.015f)/tau);}
    /** How hard each blow shakes the camera as it lands on someone (a cut that meets no one, a third of it). */
    static final float[][] SHAKE={{},{.4f},{.3f,.35f},{.18f,.18f,.3f},{.5f,1.1f},{.35f},{.35f},{.55f},{1.0f}};
    @Override public float shake(StandClient.Cast cast,float sec){
        int hit=cast.combo();if(!valid(hit))return 0;float v=0;
        for(int i=0;i<blows(hit);i++){
            if(!KnifeClient.cameTo(cast,i))continue;
            int[] told=KnifeClient.struck(cast.id(),i);
            v=Math.max(v,SHAKE[hit][i]*(told!=null&&told.length>0?1:.33f)*bump(sec,blow(hit,i),.07f));
        }
        return v;
    }
}
