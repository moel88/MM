package dev.sangwoo.celestialfall.client.samurai;

import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.SLASH_SEC;

/**
 * The limbs of the one a samurai's G has marked, while it holds them (2.144.0; a player's or a training dummy's: the
 * rig's jointed limbs; anything else is only turned and lifted, SamuraiClient.turn). One clip on the cast's clock, in
 * their own frame (they face the way its user came from; its user goes by on their left and stands there behind them):
 * startled as its user is suddenly there; thrown open by the draw's cut from their left, then clutching that side;
 * thrown the other way by the whirl's, then sagging, arms hanging, head down; flung wide by the flash and held so,
 * through the grey world; and in the dark, hanging, thrown a different way by each of the four cuts.
 */
public final class SamuraiStruck {
    private SamuraiStruck(){}
    private static double[] d(double el,double az){return new double[]{el,az};}
    private static final double[] NONE=d(0,0);
    // standing
    static final R6.Pose STARTLED=SamuraiKata.on(0,0,0,0,0,0,d(-42,24),NONE,d(-46,-22),.15,.04,-.15,-.06,0,-4);
    static final R6.Pose OPENED=SamuraiKata.on(0,0,0,0,0,0,d(18,74),NONE,d(42,-112),.2,.14,-.22,-.24,-42,-22);
    static final R6.Pose CLUTCHING=SamuraiKata.on(0,0,0,0,0,0,d(-34,-52),NONE,d(-76,-18),.14,.1,-.2,-.12,-18,24);
    static final R6.Pose TURNED=SamuraiKata.on(0,0,0,0,0,0,d(-4,46),NONE,d(8,-62),.2,-.04,-.2,.06,36,-16);
    static final R6.Pose SAGGING=SamuraiKata.on(0,0,0,0,0,0,d(-80,12),NONE,d(-82,-10),.18,.02,-.18,-.06,6,34);
    static final R6.Pose FLUNG=SamuraiKata.on(0,0,0,0,0,0,d(16,122),NONE,d(22,-124),.22,.12,-.22,-.16,0,-30);
    static final R6.Pose FLUNG_HELD=SamuraiKata.on(0,0,0,0,0,0,d(8,116),NONE,d(12,-118),.22,.12,-.22,-.16,4,-24);
    // hanging in the dark
    static final R6.Pose HANGING=SamuraiKata.air(0,0,0,0,0,0,d(-66,34),NONE,d(-70,-30),d(-84,14),d(-86,-12),0,28);
    static final R6.Pose[] THROWN={
        SamuraiKata.air(0,0,0,0,0,0,d(58,84),NONE,d(-28,-142),d(-66,36),d(-78,-150),-40,-20),
        SamuraiKata.air(0,0,0,0,0,0,d(-20,150),NONE,d(64,-70),d(-74,160),d(-60,-30),44,-10),
        SamuraiKata.air(0,0,0,0,0,0,d(40,40),NONE,d(46,-44),d(-58,20),d(-62,-24),0,-40),
        SamuraiKata.air(0,0,0,0,0,0,d(-10,100),NONE,d(-14,-104),d(-50,70),d(-52,-76),-30,30)};

    public static final Anim CLIP=Anim.of(
        k(0,STARTLED),
        k(1.17f,STARTLED),
        k(1.24f,OPENED,Anim.Ease.OUT),
        k(1.7f,CLUTCHING),
        k(2.22f,CLUTCHING),
        k(2.31f,TURNED,Anim.Ease.OUT),
        k(2.8f,SAGGING),
        k(3.2f,SAGGING),
        k(3.31f,FLUNG,Anim.Ease.OUT),
        k(4.2f,FLUNG_HELD),
        k(4.6f,HANGING),
        k(SLASH_SEC[0]-.02f,HANGING),
        k(SLASH_SEC[0]+.07f,THROWN[0],Anim.Ease.OUT),
        k(SLASH_SEC[1]-.02f,THROWN[0]),
        k(SLASH_SEC[1]+.07f,THROWN[1],Anim.Ease.OUT),
        k(SLASH_SEC[2]-.02f,THROWN[1]),
        k(SLASH_SEC[2]+.07f,THROWN[2],Anim.Ease.OUT),
        k(SLASH_SEC[3]-.02f,THROWN[2]),
        k(SLASH_SEC[3]+.07f,THROWN[3],Anim.Ease.OUT),
        k(7f,THROWN[3])).after(IsosangLimbs.natural(IsosangLimbs.FLOOR));

    /** Their limbs at {@code sec} of the cast's clock. */
    public static MotionPose at(float sec){return CLIP.frame(Math.max(0,Math.min(7f,sec))).joints();}
}
