package dev.sangwoo.celestialfall.client.samurai;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * The samurai's body through their G (2.143.0, from the user's video, its seconds): one clip on its cast's clock, R6
 * keys with one-piece limbs as the video's avatar has them. Before the dash ({@link #DASH}) they stand low over the
 * sheathed sword, facing the one marked; from it they stand beside them, their back to them (the clip's facing turns
 * with the cut, SamuraiClient.facing): the cut made in passing held low, the flourish (up to the left, over the head,
 * across to the right, up, a twist, low round the front, up again), the sword laid over the shoulder with its point at
 * the scabbard's mouth ({@link #NOTO}), pushed home ({@link #CLICK}), and lowered to the hip. Every frame stands on the
 * ground (its root is set so its lower foot does: {@link #stood}).
 * <p>
 * The scabbard ({@link #sheath}): along the blade, its mouth at the guard, while the sword is in it; left at the hip as
 * the sword is drawn and through the flourish; brought to the blade's point for the sword to be put back, and up the blade
 * to the guard as it goes home.
 */
public final class SamuraiKata {
    private SamuraiKata(){}
    /** The clip's moments (seconds of the cast's clock; the video's). */
    public static final float BURST=.6f,DASH=1f,ARRIVE=1.2f,NOTO=2.4f,CLICK=2.93f,DOWN=3.17f,LENGTH=7.5f;
    /** The sword along its line from the hand: the guard and the point (blocks; Kageno's sword's, ComboSkill). */
    public static final double GUARD=.24,TIP=1.35;
    /** The scabbard's length and half its width and depth (blocks). */
    public static final double SHEATH=1.2,SHEATH_W=.045,SHEATH_D=.028;

    /** A leg stance in the world: {elevation, azimuth toward the right of the facing}. */
    private static final double[] R_WIDE={-51,68},L_WIDE={-51,-104},R_EASY={-80,60},L_EASY={-80,-100};

    /**
     * A key written in the world (R6.world's way: the torso turned yaw to the left, leaning pitch forward and rolled, the
     * limbs and the blade along world directions), its head turned {@code head} to the right of the facing and looking
     * {@code look} down, its root as high as stands its lower foot on the ground.
     */
    static R6.Pose key(double fw,double rt,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double[] rLeg,double[] lLeg,float head,float look){
        R6.Pose p=R6.pose().at(fw,0,rt).body(yaw,pitch,roll);
        p.rArm=R6.local(p,rArm[0],rArm[1]);p.blade=R6.local(p,blade[0],blade[1]);p.lArm=R6.local(p,lArm[0],lArm[1]);
        p.rLeg=R6.local(p,rLeg[0],rLeg[1]);p.lLeg=R6.local(p,lLeg[0],lLeg[1]);
        // the head toward {head, look} in the world: the torso's turn taken back out of it
        p=p.head(yaw+head,look-pitch*.8f,-roll*.6f);
        double[] feet=dev.sangwoo.celestialfall.client.isosang.IsosangSlash.feet(R6.solve(p));
        return p.at(fw,-Math.min(feet[0],feet[1]),rt);
    }
    private static double[] d(double elevation,double azimuth){return new double[]{elevation,azimuth};}

    // before the dash: low over the sheathed sword, the right hand on its hilt at the left hip
    static final R6.Pose IAI=key(0,0,34,22,0,d(-36,-48),d(-16,-156),d(-58,-38),d(-58,24),d(-56,-150),0,6);
    static final R6.Pose IAI_LOW=key(0,0,38,27,0,d(-34,-50),d(-14,-158),d(-56,-40),d(-54,24),d(-52,-150),0,10);
    static final R6.Pose IAI_BURST=key(0,0,30,15,0,d(-38,-46),d(-20,-154),d(-60,-36),d(-60,24),d(-58,-150),0,-4);
    static final R6.Pose IAI_GO=key(.15,0,40,34,0,d(-30,-52),d(-10,-160),d(-52,-42),d(-50,24),d(-48,-152),0,14);
    // beside the one marked, the back to them: the cut made in passing, held low out to the right
    static final R6.Pose ARRIVE_IN=key(.55,0,-38,34,6,d(-14,62),d(-12,92),d(-40,-72),d(-44,66),d(-48,-98),0,12);
    static final R6.Pose ARRIVED=key(0,0,-30,30,6,d(-22,70),d(-18,100),d(-45,-70),d(-46,68),d(-50,-96),0,10);
    static final R6.Pose BACK_LOW=key(0,0,-10,26,2,d(-40,40),d(-30,75),d(-48,-64),R_WIDE,L_WIDE,0,8);
    static final R6.Pose WIND=key(0,0,-36,22,4,d(-50,62),d(-44,122),d(-42,-60),R_WIDE,L_WIDE,10,6);
    // the flourish
    static final R6.Pose UP_LEFT=key(0,0,45,-6,-6,d(58,-35),d(55,-60),d(-30,-112),R_WIDE,L_WIDE,-12,-6);
    static final R6.Pose OVER=key(0,0,72,-4,-2,d(72,20),d(12,150),d(-36,-104),R_WIDE,L_WIDE,-32,-2);
    static final R6.Pose ACROSS=key(0,0,10,6,0,d(18,-15),d(22,-80),d(-44,-90),R_WIDE,L_WIDE,0,2);
    static final R6.Pose RIGHT=key(0,0,-48,12,4,d(-4,82),d(-22,98),d(-38,-84),R_WIDE,L_WIDE,14,4);
    static final R6.Pose UP_RIGHT=key(0,0,-28,-8,2,d(60,72),d(66,92),d(-40,-90),R_WIDE,L_WIDE,8,-6);
    static final R6.Pose TWIST=key(0,0,86,4,-4,d(38,-62),d(44,-60),d(-34,-120),R_WIDE,L_WIDE,-50,0);
    static final R6.Pose LOW_LEFT=key(0,0,40,16,-2,d(-22,-100),d(-12,-95),d(-40,-110),R_WIDE,L_WIDE,-10,6);
    static final R6.Pose LOW=key(0,0,0,22,0,d(-42,0),d(-28,15),d(-44,-96),R_WIDE,L_WIDE,0,10);
    static final R6.Pose RIGHT_LOW=key(0,0,-40,14,4,d(-8,88),d(-14,108),d(-40,-86),R_WIDE,L_WIDE,12,6);
    static final R6.Pose RISE=key(0,0,-12,-4,2,d(70,62),d(84,20),d(-44,-84),R_WIDE,L_WIDE,4,-4);
    // the sword laid over the shoulder, its point down at the scabbard's mouth by the left hand; pushed home; lowered
    static final R6.Pose NOTO_IN=key(0,0,12,2,4,d(54,54),d(-28,-110),d(-42,-78),R_WIDE,L_WIDE,0,4);
    static final R6.Pose NOTO_HOLD=key(0,0,12,2,4,d(50,50),d(-33,-104),d(-42,-78),R_WIDE,L_WIDE,0,6);
    static final R6.Pose NOTO_END=key(0,0,13,3,4,d(49,49),d(-34,-102),d(-42,-78),R_WIDE,L_WIDE,0,8);
    static final R6.Pose HOME=key(0,0,12,4,4,d(40,40),d(-36,-100),d(-44,-76),R_WIDE,L_WIDE,0,10);
    static final R6.Pose DONE=key(0,0,0,0,0,d(-40,-52),d(-22,-160),d(-70,-30),R_EASY,L_EASY,0,4);

    public static final Anim CLIP=Anim.of(
        k(0,IAI),
        k(.5,IAI_LOW),
        k(BURST,IAI_BURST,Anim.Ease.OUT),
        k(.88,IAI_BURST),
        k(.97,IAI_GO,Anim.Ease.IN),
        // the cut: the body beside the one marked from here (the clip's facing turns with it)
        k(DASH,ARRIVE_IN,Anim.Ease.CUT),
        k(ARRIVE,ARRIVED,Anim.Ease.OUT),
        k(1.3,BACK_LOW),
        k(1.4,WIND),
        k(1.53,UP_LEFT),
        k(1.64,OVER),
        k(1.7,ACROSS),
        k(1.78,RIGHT),
        k(1.85,UP_RIGHT),
        k(1.93,TWIST),
        k(1.99,LOW_LEFT),
        k(2.05,LOW),
        k(2.11,RIGHT_LOW),
        k(2.2,RISE),
        k(2.3,NOTO_IN),
        k(NOTO,NOTO_HOLD),
        k(2.86,NOTO_END),
        k(CLICK,HOME,Anim.Ease.OUT),
        k(3.0,HOME),
        k(DOWN,DONE),
        k(LENGTH,DONE)).after(SamuraiKata::stood);
    /** A frame with its root as high as stands its lower foot on the ground (between keys the legs, turning with the torso, would sink one a little or lift both). */
    static R6.Frame stood(R6.Frame f){
        double[] feet=dev.sangwoo.celestialfall.client.isosang.IsosangSlash.feet(f);double low=Math.min(feet[0],feet[1]);
        return Math.abs(low)<1e-6?f:new R6.Frame(f.joints(),f.grip(),f.fw(),f.up()-low,f.rt(),f.yaw(),f.pitch(),f.roll());
    }

    /** The body at {@code sec}. */
    public static R6.Pose pose(float sec){return CLIP.pose(Math.max(0,Math.min(LENGTH,sec)));}
    /** The sword hand and the way the blade points in the world at {@code sec}, the body's feet at {@code feet} facing {@code yaw}. */
    public static Vec3d[] blade(Vec3d feet,float yaw,float sec){return KagenoClips.blade(feet,yaw,pose(sec));}

    /** How far the sword is in its scabbard at {@code sec} (0 its point at the mouth, 1 home to the guard). */
    public static float home(float sec){
        if(sec<DASH)return 1;
        if(sec<NOTO)return 1-smooth((sec-DASH)/.08f);
        return smooth((sec-2.87f)/.09f);
    }
    /** How far the scabbard is on the blade's line at {@code sec} (1), or left at the hip (0). */
    public static float along(float sec){
        if(sec<DASH)return 1;
        if(sec<2.2f)return 1-smooth((sec-DASH)/.12f);
        return smooth((sec-2.22f)/.14f);
    }
    /**
     * The scabbard at {@code sec}: {its mouth, the way it runs from there}, the body's feet at {@code feet} facing
     * {@code yaw}. On the blade's line its mouth is as far up the blade as the sword is home; at the hip it hangs from
     * the left side of the belt, pointing back and down.
     */
    public static Vec3d[] sheath(Vec3d feet,float yaw,float sec){
        var p=pose(sec);var hb=KagenoClips.blade(feet,yaw,p);
        float in=home(sec),w=along(sec);
        Vec3d onBlade=hb[0].add(hb[1].multiply(TIP-(TIP-GUARD)*in));
        if(w>=1)return new Vec3d[]{onBlade,hb[1]};
        Vec3d hip=KagenoClips.onTorso(feet,yaw,p,-.27,.8,.06);
        Vec3d back=KagenoClips.onTorso(feet,yaw,p,-.36,.52,-.94).subtract(hip).normalize();
        if(w<=0)return new Vec3d[]{hip,back};
        Vec3d way=back.multiply(1-w).add(hb[1].multiply(w));
        way=way.lengthSquared()<1e-6?hb[1]:way.normalize();
        return new Vec3d[]{hip.lerp(onBlade,w),way};
    }
    static float smooth(float x){x=x<0?0:x>1?1:x;return x*x*(3-2*x);}
}
