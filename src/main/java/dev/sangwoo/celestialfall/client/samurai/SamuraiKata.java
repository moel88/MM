package dev.sangwoo.celestialfall.client.samurai;

import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * The samurai's body through their G (2.144.0, user request: "모션을 R6 만들지말고 관절을 많이 넣어서 모션을 멋있게 만들어
 * 이제 저걸 기반으로 전부 싹 다 갈아엎고 너가 창작해서 멋있게 만들어봐"; 2.143.0 had the video's avatar's one-piece limbs):
 * one clip on its cast's clock, mine from first to last, on the rig's jointed limbs. Its keys are written in the world
 * (the torso's turn, lean and height, the sword arm's and the blade's ways, feet set on the ground where they stand:
 * IsosangLimbs.standingAt) and every frame is then bent at its elbows and knees (IsosangLimbs.natural) and has its hands
 * put where the sword needs them ({@link #hands}: the sword hand on the hilt as the sword lies in its scabbard and as it
 * is slid home, the other hand on the scabbard by its mouth, or under the sword hand on the hilt for the two-handed cuts).
 * <p>
 * Before the dash ({@link #DASH}), facing the one marked: they sink into the draw's crouch, the right foot ahead, the
 * left hand on the scabbard and the right on the hilt at the belly; at the burst ({@link #BURST}) the thumb breaks the
 * sword an inch out. From the dash they are beside the one marked (the clip's facing turns with the cut,
 * SamuraiClient.facing; the one marked stands behind them to the right): they come in turning, the sword drawn in the turn
 * through the one marked (the scabbard pulled back at the hip) and held low out to the right. Then, as the video has it
 * (2.144.1, user: "원본은 그냥 맞추면 검을 2번 돌리고 납도를 하긴해"; 2.144.0 had three two-handed cuts, a leaping whirl
 * and a flick of the blood here, mine: gone), the sword is spun twice and put away: once round flat over the head on the
 * wrist ({@link #SPIN}), the body rising under it; and once more, wider and tilted, down behind through the one marked
 * ({@link #WHIRL}), the body sinking and turning into it; up off that over the right shoulder, the blade laid down to the
 * scabbard's mouth at the left hip and slid slowly home ({@link #NOTO} to {@link #CLICK}) but for its last inch; they
 * stand so, head down, while the four blades cut; and as the world comes back the thumb's width is pushed home
 * ({@link #SNAP}): the pillar of light goes up at that. The left hand is on the scabbard throughout.
 * <p>
 * The scabbard ({@link #sheath}): along the blade, its mouth as far up it as the sword is home ({@link #home}), while the
 * sword is in it or going in; worn at the left hip through the cuts ({@link #along}).
 */
public final class SamuraiKata {
    private SamuraiKata(){}
    /** The clip's moments (seconds of the cast's clock). */
    public static final float BURST=.6f,DASH=1f,ARRIVE=1.2f,SPIN=1.59f,WHIRL=2.25f,NOTO=2.44f,CLICK=2.93f,SNAP=6.8f,LENGTH=7.5f;
    /** Its cuts: {when the blade is through its middle, how big}: the draw, the first spin (across the front), the second (through the one marked). */
    public static final float[][] CUTS={{ARRIVE,1f},{SPIN,.8f},{WHIRL,1.4f}};
    /** The sword along its line from the hand: the guard and the point (blocks; Kageno's sword's, ComboSkill). */
    public static final double GUARD=.24,TIP=1.35;
    /** The scabbard's length and half its width and depth (blocks). */
    public static final double SHEATH=1.2,SHEATH_W=.045,SHEATH_D=.028;
    /** How far home the sword is slid at {@link #CLICK}: all but the last inch, which goes at {@link #SNAP}. */
    public static final float SHORT=.9f;

    // ───────────────────────────── the scabbard and the hands (the torso's space: right, up from the feet, ahead) ─────────────────────────────

    /** The scabbard's mouth and its way, the sword home in it (before the body's middle, running back to the left hip). */
    static final double[] MOUTH_HOME={-.06,1.02,.22},WAY_HOME=unit(-.38,-.25,-.89);
    /** The same as the sword's point is set to it for the sheathing (out at the left hip, the blade coming down to it from over the right shoulder). */
    static final double[] MOUTH_SET={-.42,.8,0},WAY_SET=unit(-.768,-.587,-.256);
    /** The same as it is worn through the cuts (at the left hip, hanging back). */
    static final double[] MOUTH_WORN={-.27,.84,.06},WAY_WORN=unit(-.1,-.3,-.95);
    /** Where on the scabbard the left hand holds it (blocks from its mouth). */
    public static final double HOLD_SCABBARD=.14;

    /** How far the sword is in its scabbard at {@code sec} (0 its point at the mouth, 1 home to the guard). */
    public static float home(float sec){
        if(sec<DASH)return 1-.07f*smooth((sec-(BURST-.02f))/.05f);
        if(sec<NOTO)return (1-.07f)*(1-smooth((sec-DASH)/.1f));
        if(sec<SNAP-.04f)return SHORT*ease((sec-NOTO)/(CLICK-NOTO));
        return SHORT+(1-SHORT)*smooth((sec-(SNAP-.02f))/.035f);
    }
    /** How far the scabbard is on the blade's line at {@code sec} (1), or worn at the hip (0). */
    public static float along(float sec){
        if(sec<DASH)return 1;
        if(sec<NOTO-.2f)return 1-smooth((sec-DASH)/.12f);
        return smooth((sec-(NOTO-.2f))/.12f);
    }
    /** How much the sword hand is put where the scabbard has the hilt at {@code sec} (the sword in it, or going in). */
    static float hilted(float sec){
        if(sec<DASH)return smooth(sec/.14f);
        if(sec<NOTO-.1f)return 1-smooth((sec-DASH)/.07f);
        return smooth((sec-(NOTO-.1f))/.1f);
    }
    /** How much the left hand is on the scabbard at {@code sec}: from the first, and throughout. */
    static float scabbarded(float sec){return smooth(sec/.14f);}

    /** The scabbard with the sword {@code in} of the way home, its mouth and its way where the hands hold it for that: {mouth, way}. */
    static double[][] held(float in){
        float u=smooth(in);
        return new double[][]{mix(MOUTH_SET,MOUTH_HOME,u),unit(mix(WAY_SET,WAY_HOME,u))};
    }
    /** The sword hand in the torso's space for a frame's pose. */
    static double[] swordHand(R6.Pose p){
        double r=R6.armReach(p.rElbow);
        return new double[]{.3125+p.rArm.rt()*r,1.375+p.rArm.up()*r,p.rArm.fw()*r};
    }
    /**
     * The scabbard in the torso's space for a frame's pose at {@code sec}: {mouth, way}. On the blade while the sword is
     * in it; worn at the hip through the cuts; brought from the hip to where the hands hold it for the sheathing before the
     * blade is laid to it (it does not chase a blade still swinging).
     */
    static double[][] scabbard(R6.Pose p,float sec){
        float in=home(sec),w=along(sec);
        if(w<=0)return new double[][]{MOUTH_WORN,WAY_WORN};
        double[][] to;
        if(sec>=DASH&&sec<NOTO-.2f)to=held(1-.07f);      // the draw leaves it where it was held
        else if(sec<DASH||hilted(sec)>=1){
            double[] hand=swordHand(p),blade={p.blade.rt(),p.blade.up(),p.blade.fw()};
            double out=TIP-(TIP-GUARD)*in;
            to=new double[][]{{hand[0]+blade[0]*out,hand[1]+blade[1]*out,hand[2]+blade[2]*out},blade};
        }else to=held(in);
        if(w>=1)return to;
        return new double[][]{mix(MOUTH_WORN,to[0],w),unit(mix(WAY_WORN,to[1],w))};
    }
    /**
     * A bent frame with its hands where the sword needs them at {@code sec}: the sword hand where the scabbard has the
     * hilt, the left hand on the scabbard or on the hilt, each as much as that moment has it (turned to from the frame's
     * own arm).
     */
    static R6.Frame hands(float sec,R6.Frame f){
        float h=hilted(sec);
        if(h>0){
            float in=home(sec);double[][] s=held(in);double out=TIP-(TIP-GUARD)*in;
            var at=IsosangLimbs.swordHandAt(f,s[0][0]-s[1][0]*out,s[0][1]-s[1][1]*out,s[0][2]-s[1][2]*out,new R6.Dir(s[1][0],s[1][1],s[1][2]));
            f=turned(f,at,h,MotionPose.RIGHT_ARM,0,true);
        }
        float on=scabbarded(sec);
        if(on>0){
            double[][] s=scabbard(R6.poseOf(f),sec);
            f=turned(f,IsosangLimbs.leftHandAt(f,s[0][0]+s[1][0]*HOLD_SCABBARD,s[0][1]+s[1][1]*HOLD_SCABBARD,s[0][2]+s[1][2]*HOLD_SCABBARD),on,MotionPose.LEFT_ARM,1,false);
        }
        return f;
    }
    /** Frame {@code a} with one arm turned {@code w} of the way to frame {@code b}'s (its shoulder the short way round, its elbow, and the wrist if asked). */
    static R6.Frame turned(R6.Frame a,R6.Frame b,float w,int part,int hinge,boolean grip){
        if(w<=0)return a;
        MotionPose m=(w>=1?b:a).joints().copy();
        if(w<1){
            m.joint[part]=R6.angles(quat(a.joints().joint[part]).slerp(quat(b.joints().joint[part]),w));
            m.elbow[hinge]=a.joints().elbow[hinge]+(b.joints().elbow[hinge]-a.joints().elbow[hinge])*w;
            // (the rest of b's arms are a's: only this arm differs)
        }
        float g=grip?a.grip()+(b.grip()-a.grip())*Math.min(1,w):a.grip();
        return new R6.Frame(m,g,a.fw(),a.up(),a.rt(),a.yaw(),a.pitch(),a.roll());
    }
    private static Quaternionf quat(float[] e){float k=(float)Math.PI/180;return new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k);}

    // ───────────────────────────── the keys ─────────────────────────────

    private static double[] d(double elevation,double azimuth){return new double[]{elevation,azimuth};}
    /**
     * A key standing on the ground: the root {@code rt} right, {@code fw} ahead and {@code up} over where they stand, the
     * torso turned {@code yaw} to the left, leaning {@code pitch} forward and rolled; the sword arm, the blade and the
     * left arm along world ways ({elevation, azimuth to the right of the clip's facing}); the right foot at
     * ({@code rr}, {@code rf}) and the left at ({@code lr}, {@code lf}) on the ground (right, ahead); the head turned
     * {@code head} to the right of the clip's facing as far as a neck goes, looking {@code down}.
     */
    static R6.Pose on(double rt,double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf,float head,float down){
        R6.Pose p=R6.world(fw,up,yaw,pitch,roll,rArm,blade,lArm,d(-90,0),d(-90,0));
        p.rt=rt;
        p.rLeg=IsosangLimbs.standingAt(p,0,rr,rf);p.lLeg=IsosangLimbs.standingAt(p,1,lr,lf);
        return looking(p,head,down);
    }
    /** A key in the air: as {@link #on}, the legs along world ways. */
    static R6.Pose air(double rt,double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double[] rLeg,double[] lLeg,float head,float down){
        R6.Pose p=R6.world(fw,up,yaw,pitch,roll,rArm,blade,lArm,rLeg,lLeg);
        p.rt=rt;
        return looking(p,head,down);
    }
    private static R6.Pose looking(R6.Pose p,float head,float down){
        float turn=net.minecraft.util.math.MathHelper.wrapDegrees(p.yaw+head);
        return p.head(Math.max(-78,Math.min(78,turn)),down-p.pitch*.8f,-p.roll*.6f);
    }

    // the sword in its scabbard (the hands are put on it: hands), and how the arms lie before they are
    private static final double[] R_HILT=d(-44,-30),B_HILT=d(-18,-150),L_SAYA=d(-60,-34);
    // before the dash, facing the one marked: standing, sunk into the draw's crouch, the burst, coiled, away
    static final R6.Pose STAND=on(0,0,-.06,12,6,0,R_HILT,B_HILT,L_SAYA,.16,.1,-.18,-.06,0,0);
    static final R6.Pose SINK=on(0,.04,-.36,34,24,0,R_HILT,B_HILT,L_SAYA,.2,.46,-.28,-.48,0,2);
    static final R6.Pose DEEP=on(0,.06,-.43,38,30,0,R_HILT,B_HILT,L_SAYA,.2,.46,-.28,-.48,0,6);
    static final R6.Pose BREAK=on(0,.03,-.38,30,21,0,R_HILT,B_HILT,L_SAYA,.2,.46,-.28,-.48,0,-10);
    static final R6.Pose COIL=on(0,.1,-.45,40,33,0,R_HILT,B_HILT,L_SAYA,.2,.46,-.28,-.48,0,8);
    static final R6.Pose AWAY=on(0,.62,-.36,44,50,0,R_HILT,B_HILT,L_SAYA,.2,.74,-.28,-.48,0,14);
    // beside the one marked: in turning to the left on the right foot, the sword drawn in the turn and out to the right
    static final R6.Pose IN=on(0,.6,-.32,-165,26,0,d(-45,135),d(-12,-15),d(-40,60),.1,.22,.2,.92,150,6);
    static final R6.Pose IN_OUT=on(0,.48,-.34,-140,24,3,d(-20,118),d(-5,65),d(-40,40),.12,.24,-.2,.8,120,6);
    static final R6.Pose IN_CUT=on(0,.36,-.36,-108,22,6,d(-8,150),d(0,165),d(-40,10),.14,.26,-.45,.55,90,4);
    static final R6.Pose IN_THROUGH=on(0,.14,-.38,-48,19,6,d(-2,118),d(2,128),d(-42,-50),.2,.34,-.45,-.15,40,4);
    static final R6.Pose DRAWN=on(0,.02,-.38,-8,16,4,d(2,76),d(4,84),d(-44,-90),.26,.42,-.24,-.48,0,4);
    static final R6.Pose DRAWN_HELD=on(0,0,-.36,-4,14,3,d(4,80),d(6,86),d(-44,-90),.26,.42,-.24,-.48,0,2);
    // the first spin: the blade round once flat over the head, on the wrist (front, left, behind, right), the body rising under it
    static final R6.Pose S1_FRONT=on(0,0,-.3,4,10,2,d(34,62),d(9,40),L_SAYA,.26,.42,-.24,-.48,0,0);
    static final R6.Pose S1_LEFT=on(0,.01,-.22,14,4,0,d(60,-5),d(12,-40),L_SAYA,.26,.42,-.24,-.48,0,-4);
    static final R6.Pose S1_BACK=on(0,.02,-.18,20,-1,-2,d(70,-120),d(12,-130),L_SAYA,.26,.42,-.24,-.48,0,-6);
    static final R6.Pose S1_ROUND=on(0,.01,-.18,8,0,0,d(70,145),d(10,140),L_SAYA,.26,.42,-.24,-.48,0,-4);
    static final R6.Pose S1_OUT=on(0,0,-.2,-6,3,2,d(60,88),d(6,86),L_SAYA,.26,.42,-.24,-.48,0,-2);
    // the second: wider and tilted, high before them to the left and low behind through the one marked, the body sinking and turning into it
    static final R6.Pose S2_HELD=on(0,0,-.21,-6,4,2,d(58,86),d(4,84),L_SAYA,.26,.42,-.24,-.48,0,-2);
    static final R6.Pose S2_IN=on(0,.01,-.2,0,2,1,d(57,58),d(12,51),L_SAYA,.26,.42,-.24,-.48,0,-3);
    static final R6.Pose S2_FRONT=on(0,.02,-.17,14,-2,-1,d(60,0),d(24,-12),L_SAYA,.26,.42,-.24,-.48,-4,-6);
    static final R6.Pose S2_LEFT=on(.02,0,-.18,26,0,-3,d(64,-75),d(28,-90),L_SAYA,.26,.42,-.24,-.48,-6,-6);
    static final R6.Pose S2_BACK=on(.06,-.05,-.27,-8,6,0,d(58,178),d(10,-160),L_SAYA,.26,.42,-.24,-.48,20,0);
    static final R6.Pose S2_CUT=on(.12,-.12,-.4,-58,14,4,d(4,127),d(-8,133),L_SAYA,.26,.42,-.24,-.48,70,6);
    static final R6.Pose S2_OUT=on(.1,-.1,-.36,-38,12,4,d(10,86),d(6,76),L_SAYA,.26,.42,-.24,-.48,40,6);
    // up off it over the right shoulder
    static final R6.Pose RAISED=on(.08,-.06,-.24,-6,6,3,d(26,68),d(68,40),L_SAYA,.25,.3,-.22,-.34,0,2);
    // the blade laid to the scabbard's mouth and slid home (the hands are put: hands); standing so; the last inch; done
    static final R6.Pose LAID=on(.06,-.03,-.18,14,6,2,d(30,60),d(-36,-110),L_SAYA,.24,.2,-.2,-.24,-10,8);
    static final R6.Pose SLID=on(.02,0,-.24,12,12,2,R_HILT,B_HILT,L_SAYA,.24,.2,-.2,-.24,-14,14);
    static final R6.Pose STOOD=on(0,0,-.12,8,5,0,R_HILT,B_HILT,L_SAYA,.18,.12,-.18,-.12,0,10);
    static final R6.Pose READY=on(0,0,-.19,10,8,0,R_HILT,B_HILT,L_SAYA,.18,.12,-.18,-.12,0,14);
    static final R6.Pose SNAPPED=on(0,0,-.23,10,10,0,R_HILT,B_HILT,L_SAYA,.18,.12,-.18,-.12,0,16);
    static final R6.Pose DONE=on(0,0,-.05,4,2,0,R_HILT,B_HILT,L_SAYA,.14,.05,-.14,-.05,0,4);

    private static final Anim.Fix BENT=IsosangLimbs.natural(IsosangLimbs.FLOOR);
    public static final Anim CLIP=Anim.of(
        k(0,STAND),
        k(.24,SINK),
        k(.56,DEEP),
        k(.63,BREAK,Anim.Ease.OUT),
        k(.88,COIL),
        k(.98,AWAY,Anim.Ease.IN),
        // the cut: the body beside the one marked from here (the clip's facing turns with it)
        k(DASH,IN,Anim.Ease.CUT),
        k(1.06,IN_OUT,Anim.Ease.LINEAR),
        k(1.13,IN_CUT,Anim.Ease.LINEAR),
        k(1.21,IN_THROUGH,Anim.Ease.LINEAR),
        k(1.31,DRAWN,Anim.Ease.OUT),
        k(1.39,DRAWN_HELD),
        // the sword spun twice (the middle keys at an even turn: no stop on the way round)
        k(1.47,S1_FRONT,Anim.Ease.IN),
        k(1.55,S1_LEFT,Anim.Ease.LINEAR),
        k(1.63,S1_BACK,Anim.Ease.LINEAR),
        k(1.71,S1_ROUND,Anim.Ease.LINEAR),
        k(1.8,S1_OUT,Anim.Ease.OUT),
        k(1.84,S2_HELD),
        k(1.9,S2_IN,Anim.Ease.IN),
        k(2.0,S2_FRONT,Anim.Ease.LINEAR),
        k(2.1,S2_LEFT,Anim.Ease.LINEAR),
        k(2.18,S2_BACK,Anim.Ease.LINEAR),
        k(WHIRL,S2_CUT,Anim.Ease.LINEAR),
        k(2.33,S2_OUT,Anim.Ease.OUT),
        k(2.39,RAISED),
        k(NOTO,LAID),
        k(CLICK,SLID),
        k(3.12,STOOD),
        k(6.64,STOOD),
        k(6.75,READY),
        k(SNAP,SNAPPED,Anim.Ease.OUT),
        k(7.12,DONE),
        k(LENGTH,DONE)).after((sec,f)->hands(sec,BENT.apply(sec,f)));

    /** The body at {@code sec}. */
    public static R6.Pose pose(float sec){return CLIP.pose(Math.max(0,Math.min(LENGTH,sec)));}
    /** The sword hand and the way the blade points in the world at {@code sec}, the body's feet at {@code feet} facing {@code yaw}. */
    public static Vec3d[] blade(Vec3d feet,float yaw,float sec){return KagenoClips.blade(feet,yaw,pose(sec));}
    /** The sword's point in the world at {@code sec}. */
    public static Vec3d tip(Vec3d feet,float yaw,float sec){var hb=blade(feet,yaw,sec);return hb[0].add(hb[1].multiply(TIP));}
    /** The scabbard in the world at {@code sec}: {its mouth, the way it runs from there}, the body's feet at {@code feet} facing {@code yaw}. */
    public static Vec3d[] sheath(Vec3d feet,float yaw,float sec){
        var p=pose(sec);double[][] s=scabbard(p,sec);
        Vec3d mouth=KagenoClips.onTorso(feet,yaw,p,s[0][0],s[0][1],s[0][2]);
        return new Vec3d[]{mouth,KagenoClips.onTorso(feet,yaw,p,s[0][0]+s[1][0],s[0][1]+s[1][1],s[0][2]+s[1][2]).subtract(mouth).normalize()};
    }

    static float smooth(float x){x=x<0?0:x>1?1:x;return x*x*(3-2*x);}
    /** Slow away, slow home. */
    static float ease(float x){x=x<0?0:x>1?1:x;return x*x*x*(x*(x*6-15)+10);}
    private static double[] unit(double... v){double l=Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);return new double[]{v[0]/l,v[1]/l,v[2]/l};}
    private static double[] mix(double[] a,double[] b,double t){return new double[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t};}
}
