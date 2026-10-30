package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * Lee So-sang's basic attack as drawn (2.77.0, user request: "이제 평타부터 만들자 영상 보내줄게 1타 2타 3타 4타 다
 * 들어있어 영상에"), remade after the user's video of each hit (2.77.1, user request: "타수 별로 다시 영상 보내줄게 제대로
 * 참고해"): the body's four hits and their slashes. Every hit is a swing of its own (its clock the server's,
 * IsosangClient), its blows on the server's ticks (IsosangRules).
 * <ol>
 * <li>참월(斬月): the sword flipped upright at the right side (a glint along it) and held out to the right, a step in
 * with it cocked level at the shoulder, a great diagonal cut down across the front to the left (a wide tilted crescent,
 * its head thick with ink), a cut rising straight back up to the right (a long narrow streak) and the sword held out
 * there.</li>
 * <li>윤월(輪月): from there the sword flipped upright, a half turn to the right down into a crouch, and a whole turn back
 * to the left with the blade low over the ground: a wide ring round them, which flares as the sword is flicked, falls to
 * the ground as a ring of ink and dries there into blots; turned back to the front.</li>
 * <li>관월(貫月): drawn back side-on and driven through in a lunge, a beam of light along the whole line (behind the hand
 * and far ahead); the sword drawn back low to the right, a long cut rising across to the high left, a quick cut back
 * down, a great cut rising up the right side over the top, and a sweep low across the front from the left that ends
 * held out to the right, leaving ink on the ground.</li>
 * <li>낙월(落月): a turn to the right with the sword twirled overhead (a ring of light), the free hand pointed at the foe
 * with the sword drawn low behind, a deep crouch, a lunge and a giant level cut low across the front: a huge crescent
 * that spreads into ink over the ground. The caster's own camera draws back for it.</li>
 * </ol>
 * The slashes are blue light outlined in ink (KagenoFire's ink ribbons: a white cutting edge, a vivid blue body, deep blue
 * to ink on the inner side), drawn along the blade's own path well past its point; as they fade they turn to ink, break
 * up and shed blots that fly off and fall, and the low ones leave blots of ink on the ground. Every effect is a function
 * of the swing's clock, so it moves at the render frame rate.
 */
public final class IsosangSlash implements Fx.Skill {
    /** Ink, navy, deep blue, blue, azure, pale blue, white. */
    static final int INK=0x05081a,NAVY=0x0c1650,DEEP=0x1a2fa0,BLUE=0x2f62ff,AZURE=0x5aa2ff,PALE=0xb8dcff,WHITE=0xffffff;
    /** The blade's aura while it cuts (haze, glow, body, core). */
    static final int[] AURA={0x10308c,0x3a78ff,0x9fd0ff,0xf4fbff};
    /** Blocks along the blade from the hand: the guard and the point (the sword as drawn in the hand, 0.9 of its model). */
    static final double GUARD=.27,TIP=1.67;
    static final Vec3d UP=new Vec3d(0,1,0);

    /** The second into hit {@code combo} its blow {@code i} lands on (the server's tick, IsosangRules). */
    static float blow(int combo,int i){return IsosangRules.blowAt(combo,i)/20f;}
    /** The second into hit {@code combo} the next may begin (IsosangRules.STEP): the next clip starts from this pose. */
    static float step(int combo){return IsosangRules.STEP[combo]/20f;}
    static double[] d(double elevation,double azimuth){return new double[]{elevation,azimuth};}
    /** A pose written in the world (R6.world): root forward and up, the torso's turn (left +), lean and roll, then the limbs and the blade. */
    static R6.Pose w(double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double[] rLeg,double[] lLeg){
        return R6.world(fw,up,yaw,pitch,roll,rArm,blade,lArm,rLeg,lLeg);
    }
    /**
     * Their stance between hits, as the body holds the sword when it is not swinging (MotionPlayerModel's "isosang" arm,
     * the sword as the game holds an item): the arm a little forward, the blade forward and up a third of the way.
     */
    static final R6.Pose STANCE=w(0,0,0,0,0,d(-71,2),d(31,0),d(-86,-8),d(-90,3),d(-90,-3));
    /** The stance solved, for a swing to start from (KagenoClips.begin). */
    public static R6.Frame stance(){return R6.solve(STANCE);}
    /**
     * The game draws the sword in the hand 12° further round the wrist than the reach of R6's grip (its display turns it
     * 12° about the hand's x, models/item/isosang_sword.json): the hand turns it back by this much while a swing drives it.
     */
    public static final float DISPLAY_TILT=12;

    // ───────────────────────────── 1 참월 ─────────────────────────────
    /** The sword flipped upright at the right side, the hand by the hip. */
    static final R6.Pose H1_RAISE=w(.02,-.02,-8,0,0,d(-58,40),d(82,30),d(-80,-14),d(-88,4),d(-88,-4));
    /** Rising and tilting out to the right as it glints. */
    static final R6.Pose H1_GLINT=w(.05,-.03,-12,0,0,d(-26,68),d(58,64),d(-78,-20),d(-86,6),d(-88,-6));
    /** Held out up to the right, the step beginning. */
    static final R6.Pose H1_REACH=w(.14,-.06,-18,4,0,d(8,80),d(34,86),d(-70,-30),d(-76,14),d(-84,186));
    /** Stepped in, the hips turned right, the sword cocked level at the shoulder pointing back. */
    static final R6.Pose H1_COCK=w(.3,-.12,-40,6,0,d(34,112),d(16,166),d(-46,-66),d(-62,18),d(-76,190));
    static final R6.Pose H1_LOAD=w(.32,-.13,-45,6,0,d(38,118),d(18,174),d(-44,-70),d(-62,18),d(-76,190));
    /** Coming round the right into the front. */
    static final R6.Pose H1_OVER=w(.34,-.16,-22,8,0,d(26,62),d(14,82),d(-40,-92),d(-60,16),d(-74,190));
    /** Across the front to the right, level, on the way down. */
    static final R6.Pose H1_ACROSS=w(.35,-.19,-4,12,-1,d(10,28),d(-2,28),d(-36,-118),d(-58,16),d(-72,190));
    /** The cut: down across the front to the left, a deep step in. */
    static final R6.Pose H1_CUT=w(.36,-.22,18,16,-2,d(-6,-10),d(-15,-34),d(-32,-146),d(-56,16),d(-70,190));
    /** Through low to the left, held there a moment. */
    static final R6.Pose H1_DOWN=w(.38,-.26,34,20,-3,d(-30,-48),d(-14,-84),d(-38,-160),d(-54,16),d(-68,190));
    static final R6.Pose H1_LOW=w(.38,-.27,33,20,-3,d(-32,-50),d(-12,-88),d(-38,-158),d(-54,16),d(-68,190));
    /** The cut rising back across the front to the right. */
    static final R6.Pose H1_RISE=w(.38,-.2,4,12,0,d(0,20),d(8,30),d(-40,-120),d(-56,16),d(-70,190));
    /** Held out to the right at the chest: where the second hit begins. */
    static final R6.Pose H1_OUT=w(.38,-.16,-16,8,0,d(2,86),d(10,96),d(-44,-100),d(-58,16),d(-72,190));
    static final R6.Pose H1_HOLD=w(.37,-.15,-14,7,0,d(-3,88),d(5,99),d(-46,-96),d(-58,16),d(-72,190));
    static final R6.Pose H1_SETTLE=w(.26,-.1,-10,5,0,d(-24,66),d(14,70),d(-62,-56),d(-68,14),d(-80,188));
    static final Anim H1=Anim.joints(
        k(0,STANCE),
        k(.05,H1_RAISE),
        k(.085,H1_GLINT),
        k(.13,H1_REACH),
        k(.2,H1_COCK),
        // the coil keeps winding (a moving hold); then the blade comes round the right and down across the front
        k(.235,H1_LOAD),
        k(.268,H1_OVER,Anim.Ease.LINEAR),
        k(.284,H1_ACROSS,Anim.Ease.LINEAR),
        k(blow(1,0),H1_CUT,Anim.Ease.LINEAR),
        k(.345,H1_DOWN,Anim.Ease.OUT),
        k(.385,H1_LOW),
        // and straight back up to the right
        k(.425,H1_RISE,Anim.Ease.LINEAR),
        k(.46,H1_OUT,Anim.Ease.OUT),
        k(step(1),H1_HOLD),
        k(.85,H1_SETTLE),
        k(1.2,STANCE)).stops(Anim.stop(blow(1,0),.05,.09));

    // ───────────────────────────── 2 윤월 ─────────────────────────────
    /** The sword flipped upright out at the right. */
    static final R6.Pose H2_FLIP=w(.36,-.12,-14,4,0,d(36,84),d(86,80),d(-50,-90),d(-60,16),d(-72,190));
    // Body-space keys while they turn: the turn is the root's yaw, the arm and the blade ride with it.
    /** Turning right, going down, the sword swung down behind. */
    static final R6.Pose H2_TURN=R6.pose().at(.36,-.24,0).body(-80,10,0).head(-8,-6,0).rArm(-34,118).blade(-14,146).lArm(-44,-56).rLeg(-72,24).lLeg(-80,-22);
    /** Half round, crouched low, the blade low out behind the right. */
    static final R6.Pose H2_LOW=R6.pose().at(.36,-.46,0).body(-180,14,0).head(0,-10,0).rArm(-36,100).blade(-6,122).lArm(-52,-74).rLeg(-68,30).lLeg(-84,-30);
    /** The whole turn back to the left: the blade held out low at the right, sweeping round over the ground. */
    static R6.Pose spin(float yaw){return R6.pose().at(.36,-.46,0).body(yaw,12,0).head(0,-8,0).rArm(-32,86).blade(-6,94).lArm(-50,-80).rLeg(-68,30).lLeg(-84,-30);}
    /** Round past the back, rising, the blade coming down to the right. */
    static final R6.Pose H2_RISE=R6.pose().at(.35,-.3,0).body(212,8,0).head(0,-6,0).rArm(-40,72).blade(-10,92).lArm(-60,-60).rLeg(-76,24).lLeg(-84,-24);
    static final R6.Pose H2_STAND=R6.pose().at(.34,-.14,0).body(214,4,0).head(0,-4,0).rArm(-48,50).blade(-16,64).lArm(-72,-40).rLeg(-82,14).lLeg(-86,-14);
    /** The flick that makes the ring flare: the blade snapped out down to the right. */
    static final R6.Pose H2_FLICK=R6.pose().at(.34,-.12,0).body(210,4,0).head(0,-4,0).rArm(-34,84).blade(-18,104).lArm(-72,-40).rLeg(-82,14).lLeg(-86,-14);
    static final R6.Pose H2_SET=R6.pose().at(.34,-.12,0).body(216,3,0).head(0,-4,0).rArm(-44,70).blade(-18,86).lArm(-74,-34).rLeg(-84,12).lLeg(-86,-12);
    /** Turned back to the front, the sword low at the right: where the third hit begins (yaw: 356, or -4 for the next clip). */
    static R6.Pose h2Back(float yaw){return w(.34,-.08,yaw,4,0,d(-50,30),d(-18,40),d(-72,-20),d(-80,10),d(-86,-8));}
    static final Anim H2=Anim.joints(
        k(0,H1_HOLD),
        k(.04,H2_FLIP),
        k(.1,H2_TURN),
        k(.17,H2_LOW),
        // the turn back: an even speed from behind round the front (the blow) and on round behind
        k(.19,spin(-132),Anim.Ease.IN),
        k(.21,spin(-56),Anim.Ease.LINEAR),
        k(.23,spin(16),Anim.Ease.LINEAR),
        k(blow(2,0),spin(90),Anim.Ease.LINEAR),
        k(.27,spin(164),Anim.Ease.LINEAR),
        k(.3,spin(200),Anim.Ease.OUT),
        k(.36,H2_RISE),
        k(.41,H2_STAND),
        k(blow(2,1),H2_FLICK,Anim.Ease.OUT),
        k(.5,H2_SET),
        k(step(2),h2Back(356)),
        // a whole turn on: the stance faces the same way as the start
        k(1.0,STANCE.body(360,0,0))).stops(Anim.stop(blow(2,0),.03,.06),Anim.stop(blow(2,1),.03,.05));

    // ───────────────────────────── 3 관월 ─────────────────────────────
    /** Drawn back side-on: the hand at the right hip, the point level at the foe, the free hand aiming along it. */
    static final R6.Pose H3_DRAW=w(.34,-.22,-40,6,0,d(-48,164),d(2,3),d(-8,-10),d(-60,20),d(-76,190));
    /** The thrust: the sword shoulder driven through in a long lunge, the free arm thrown back. */
    static final R6.Pose H3_THRUST=w(.86,-.3,40,12,0,d(-3,-6),d(0,-2),d(-24,-172),d(-50,16),d(-58,192));
    static final R6.Pose H3_HELD=w(.9,-.31,44,13,0,d(-2,-5),d(1,-2),d(-26,-174),d(-50,16),d(-58,192));
    /** Drawn back low to the right. */
    static final R6.Pose H3_PULL=w(.74,-.24,-8,8,0,d(-40,72),d(-16,112),d(-40,-92),d(-58,16),d(-70,190));
    static final R6.Pose H3_LOWR=w(.7,-.26,-24,10,0,d(-40,82),d(-8,124),d(-42,-80),d(-58,16),d(-72,190));
    static final R6.Pose H3_LOWR2=w(.7,-.27,-28,10,0,d(-42,86),d(-8,130),d(-42,-78),d(-58,16),d(-72,190));
    /** The long cut rising across the front to the high left; on up there. */
    static final R6.Pose H3_RISE=w(.72,-.18,16,4,0,d(8,-6),d(16,-26),d(-40,-130),d(-56,16),d(-70,190));
    static final R6.Pose H3_RISE_END=w(.73,-.13,28,0,0,d(42,-40),d(48,-70),d(-38,-150),d(-56,16),d(-70,190));
    /** The quick cut back down across the front to the low right; through behind the right. */
    static final R6.Pose H3_QUICK=w(.73,-.18,-6,12,1,d(0,14),d(-12,30),d(-36,-110),d(-56,16),d(-70,190));
    static final R6.Pose H3_QUICK_END=w(.73,-.23,-20,14,1,d(-28,56),d(-10,96),d(-36,-96),d(-56,16),d(-70,190));
    /** Low behind the right, then the great cut rising up the right side and over the top. */
    static final R6.Pose H3_ARC_LOW=w(.72,-.26,-30,12,0,d(-42,112),d(-8,150),d(-36,-90),d(-58,16),d(-72,190));
    static final R6.Pose H3_ARC=w(.72,-.18,-14,0,0,d(10,52),d(30,40),d(-40,-110),d(-58,16),d(-72,190));
    static final R6.Pose H3_ARC_TOP=w(.72,-.1,4,-6,0,d(60,10),d(70,-10),d(-50,-120),d(-62,14),d(-74,188));
    /** Brought down to the low left, coiled for the sweep. */
    static final R6.Pose H3_WIND=w(.73,-.28,40,10,0,d(-30,-100),d(-6,-132),d(-40,150),d(-56,16),d(-70,190));
    static final R6.Pose H3_WIND2=w(.73,-.3,48,12,0,d(-32,-108),d(-8,-142),d(-40,156),d(-56,16),d(-70,190));
    /** The sweep low across the front from the left, rising on to the right and held out there. */
    static final R6.Pose H3_SWEEP=w(.74,-.3,0,14,0,d(-16,-4),d(-10,8),d(-36,-150),d(-56,16),d(-70,190));
    static final R6.Pose H3_SWEEP_END=w(.74,-.25,-30,10,0,d(0,70),d(10,96),d(-40,-110),d(-58,16),d(-72,190));
    static final R6.Pose H3_OUT=w(.72,-.2,-20,8,0,d(-4,84),d(4,96),d(-44,-100),d(-58,16),d(-72,190));
    /** On the way back to the stance: where the finisher begins. */
    static final R6.Pose H3_BACK=w(.56,-.14,-14,6,0,d(-18,74),d(10,84),d(-56,-70),d(-62,14),d(-76,188));
    static final Anim H3=Anim.joints(
        k(0,h2Back(-4)),
        k(.06,H3_DRAW),
        k(.12,H3_THRUST,Anim.Ease.IN),
        // held out along the thrust while the beam runs through, then drawn back low to the right
        k(blow(3,0),H3_HELD,Anim.Ease.OUT),
        k(.26,H3_PULL),
        k(.37,H3_LOWR),
        k(.41,H3_LOWR2),
        k(blow(3,1),H3_RISE,Anim.Ease.LINEAR),
        k(.49,H3_RISE_END,Anim.Ease.OUT),
        k(blow(3,2),H3_QUICK,Anim.Ease.LINEAR),
        k(.585,H3_QUICK_END),
        k(.61,H3_ARC_LOW),
        k(blow(3,3),H3_ARC,Anim.Ease.LINEAR),
        k(.7,H3_ARC_TOP,Anim.Ease.OUT),
        k(.8,H3_WIND),
        k(.89,H3_WIND2),
        k(blow(3,4),H3_SWEEP,Anim.Ease.LINEAR),
        k(1.02,H3_SWEEP_END,Anim.Ease.OUT),
        k(1.3,H3_OUT),
        k(step(3),H3_BACK),
        k(1.7,STANCE)).stops(Anim.stop(blow(3,0),.035,.03),Anim.stop(blow(3,4),.04,.06));

    // ───────────────────────────── 4 낙월 ─────────────────────────────
    /** Turning right, the sword raised overhead. */
    static final R6.Pose H4_UP=w(.54,-.04,-60,-4,0,d(64,-20),d(80,-60),d(-70,30),d(-86,6),d(-88,-6));
    /** The twirl: the arm up over the head, the blade level, turning round it (body-space), the body still turning right. */
    static R6.Pose twirl(double az,float yaw){return R6.pose().at(.52,0,0).body(yaw,-4,0).head(0,-10,0).rArm(80,16).blade(10,az).lArm(-60,-40).rLeg(-88,6).lLeg(-88,-6);}
    /** Aimed: turned back side-on, the free hand pointed at the foe, the sword drawn low behind on the right. */
    static final R6.Pose H4_POINT=w(.5,-.2,-34,6,0,d(-52,150),d(-10,164),d(6,-4),d(-64,18),d(-80,188));
    static final R6.Pose H4_POINT2=w(.5,-.24,-36,8,0,d(-50,152),d(-10,166),d(5,-3),d(-62,18),d(-78,188));
    /** Down in a deep crouch, the sword stretched back low. */
    static final R6.Pose H4_CROUCH=w(.54,-.48,-40,14,0,d(-46,156),d(-6,170),d(0,-4),d(-50,20),d(-74,192));
    /** The lunge: the body thrown forward, the blade trailing back. */
    static final R6.Pose H4_DASH=w(.96,-.38,-42,22,0,d(-40,150),d(0,172),d(-10,-30),d(-46,16),d(-60,194));
    /** The giant level cut: from the right behind round the front, low, to the left. */
    static final R6.Pose H4_DRAW=w(.98,-.34,-24,18,0,d(-16,80),d(-2,112),d(-20,-70),d(-48,16),d(-60,194));
    static final R6.Pose H4_SWING=w(.99,-.34,-8,17,0,d(-16,46),d(-3,62),d(-22,-90),d(-48,16),d(-60,194));
    static final R6.Pose H4_CUT=w(1.0,-.34,6,16,0,d(-18,8),d(-4,12),d(-24,-110),d(-48,16),d(-60,194));
    static final R6.Pose H4_ON=w(1.01,-.35,40,16,0,d(-18,-64),d(-4,-74),d(-26,-160),d(-48,16),d(-60,194));
    /** Followed through behind the left, low, the free arm thrown back: the finishing pose. */
    static final R6.Pose H4_END=w(1.01,-.38,64,18,0,d(-28,-100),d(-10,-126),d(-18,150),d(-48,16),d(-62,194));
    static final R6.Pose H4_HOLD=w(1.0,-.37,62,17,0,d(-30,-98),d(-12,-124),d(-20,148),d(-49,16),d(-62,194));
    static final R6.Pose H4_RISE=w(.88,-.28,54,13,0,d(-34,-90),d(-14,-118),d(-28,138),d(-54,16),d(-66,192));
    static final Anim H4=Anim.joints(
        k(0,H3_BACK),
        k(.05,H4_UP),
        // the twirl: a whole turn of the blade over the head, a quarter every 0.03 s, as the body turns on right
        k(.08,twirl(0,-100)),
        k(.11,twirl(90,-120),Anim.Ease.LINEAR),
        k(.14,twirl(180,-136),Anim.Ease.LINEAR),
        k(.17,twirl(270,-146),Anim.Ease.LINEAR),
        k(.2,twirl(360,-150)),
        // turned back and aimed, down, the lunge, and the cut round the front
        k(.28,H4_POINT),
        k(.36,H4_POINT2),
        k(.43,H4_CROUCH),
        k(.49,H4_DASH,Anim.Ease.OUT),
        k(.52,H4_DRAW),
        k(.535,H4_SWING,Anim.Ease.LINEAR),
        k(blow(4,0),H4_CUT,Anim.Ease.LINEAR),
        k(.6,H4_ON,Anim.Ease.LINEAR),
        k(.68,H4_END,Anim.Ease.OUT),
        k(.95,H4_HOLD),
        // held, then back on the feet unhurried: the lunge's step taken back (the combo is over)
        k(1.1,H4_RISE),
        k(1.6,STANCE)).stops(Anim.stop(blow(4,0),.07,.12));

    @Override public Anim clip(int combo){return hit(combo);}
    static Anim hit(int combo){return switch(combo){case 2->H2;case 3->H3;case 4->H4;default->H1;};}
    @Override public float lifetime(){return 2.4f;}
    @Override public boolean layered(){return true;}
    @Override public int afterimageColor(){return 0x5a8cff;}

    // ───────────────────────────── where things are ─────────────────────────────

    /** Sword hand and blade direction at clip time {@code sec} of hit {@code combo}, on the caster as drawn. */
    static Vec3d[] blade(Fx c,int combo,float sec){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(hit(combo).frame(sec)));}
    static Vec3d[] bladeNow(Fx c){return blade(c,c.cast.combo(),c.sec);}
    /** The same, standing at {@code feet} facing {@code yaw} (for the tests). */
    static Vec3d[] blade(Vec3d feet,float yaw,int combo,float sec){return KagenoClips.blade(feet,yaw,R6.poseOf(hit(combo).frame(sec)));}

    /**
     * Where a swing's slashes and ink stay: its caster's feet while the hit holds them there, then where they were as it
     * let go (they may walk off; what the hit left stays where it was made).
     */
    private static final Map<UUID,Vec3d> ANCHORS=new LinkedHashMap<>();
    static Vec3d anchor(Fx c){
        if(c.sec<IsosangRules.HOLD[c.cast.combo()]/20f)return c.pos;
        var a=ANCHORS.get(c.cast.id());if(a!=null)return a;
        ANCHORS.put(c.cast.id(),c.pos);while(ANCHORS.size()>64)ANCHORS.remove(ANCHORS.keySet().iterator().next());
        return c.pos;
    }
    /** What each blow left, held from the first frame at or past it: the caster's feet then, whom it struck and their chests. */
    private record Memo(Vec3d feet,int[] ids,Vec3d[] at){}
    private static final Map<Long,Memo> MEMOS=new LinkedHashMap<>();
    static Memo blowMemo(Fx c,int i){
        long key=c.cast.id().getLeastSignificantBits()*7+i;var m=MEMOS.get(key);if(m!=null)return m;
        var hit=struck(c,i,c.pos);
        m=new Memo(c.pos,hit.stream().mapToInt(LivingEntity::getId).toArray(),hit.stream().map(e->chest(c,e)).toArray(Vec3d[]::new));
        MEMOS.put(key,m);while(MEMOS.size()>64)MEMOS.remove(MEMOS.keySet().iterator().next());
        return m;
    }
    /** Who blow {@code i} strikes from {@code at}: the server's word once it came, else those in its way as the server picks them. */
    static List<LivingEntity> struck(Fx c,int i,Vec3d at){
        var world=c.mc.world;if(world==null)return List.of();
        List<LivingEntity> out=new ArrayList<>();
        var told=IsosangClient.struck(c.cast.id(),i);
        if(told!=null){for(int id:told)if(world.getEntityById(id) instanceof LivingEntity e&&e.isAlive())out.add(e);return out;}
        UUID caster=c.cast.caster();int combo=c.cast.combo();
        Vec3d f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x);
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(IsosangRules.FINISH_REACH+3),e->e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!e.getUuid().equals(caster)&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d o=e.getPos().subtract(at);
            if(IsosangRules.lane(combo,i,o.dotProduct(f),o.dotProduct(right),o.y,e.getWidth()*.5))out.add(e);
            if(out.size()>=4)break;
        }
        return out;
    }
    static Vec3d chest(Fx c,LivingEntity e){return e.getLerpedPos(c.delta).add(0,e.getHeight()*.58,0);}
    /** Where blow {@code i}'s targets are drawn struck: their chests following them up to it, held where it found them after. */
    static List<Vec3d> hits(Fx c,int i,float at){
        if(c.sec<at){List<Vec3d> out=new ArrayList<>();for(var e:struck(c,i,c.pos))out.add(chest(c,e));return out;}
        return List.of(blowMemo(c,i).at());
    }
    /** The caster's floor under {@code p} (they stand where they are while a hit holds them). */
    static Vec3d floor(Fx c,Vec3d p){return new Vec3d(p.x,anchor(c).y+.02,p.z);}

    // ───────────────────────────── the slashes ─────────────────────────────

    /**
     * A slash swept by the blade: drawn along its path from clip time {@code from}, its head riding the blade until
     * {@code to}, eaten from the tail over [eat0, eat1] while it turns to ink over [ink0, ink1]; its edges lie
     * {@code inner}..{@code outer} blocks along the blade from the hand (well past the point: the light flies on past
     * the steel). Styles of the ink ribbons: 0 a cut (widest at its head), 1 a ring (even), 3 the great ones (fuller).
     */
    record Slash(float from,float to,float eat0,float eat1,float ink0,float ink1,int style,double inner,double outer,int seed){}
    static Slash[] slashes(int combo){
        return switch(combo){
            // the great diagonal and the cut rising back
            case 1->new Slash[]{new Slash(.262f,.325f,.4f,.7f,.42f,.74f,0,2.3,3.1,1),new Slash(.395f,.465f,.5f,.74f,.48f,.76f,0,2.3,2.9,2)};
            // the ring round them, low over the ground
            case 2->new Slash[]{new Slash(.17f,.32f,.44f,.8f,.42f,.84f,1,2.3,3.3,3)};
            // the rising cut, the quick cut, the great cut up the right side and the low sweep
            case 3->new Slash[]{new Slash(.41f,.49f,.56f,.8f,.55f,.84f,0,2.2,2.9,5),new Slash(.505f,.585f,.64f,.86f,.64f,.9f,0,2.1,2.6,9),
                new Slash(.6f,.71f,.78f,1.02f,.78f,1.06f,3,2.1,3.0,11),new Slash(.88f,1.02f,1.08f,1.4f,1.06f,1.44f,0,2.3,3.3,13)};
            // the giant crescent
            default->new Slash[]{new Slash(.515f,.61f,.82f,1.28f,.78f,1.24f,3,2.6,5.6,7)};
        };
    }
    static final int SAMPLES=40;
    /** The band the blade sweeps between clip times {@code from} and {@code head}: edges and the fraction along the tip's path. */
    record Swept(Vec3d[] inner,Vec3d[] outer,float[] along,int n){}
    static Swept swept(Fx c,int combo,float from,float head,double inner,double outer){return swept(anchor(c),c.cast.yaw(),combo,from,head,inner,outer);}
    static Swept swept(Vec3d feet,float yaw,int combo,float from,float head,double inner,double outer){
        int n=SAMPLES+1;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];double len=0;
        for(int i=0;i<n;i++){
            var hb=blade(feet,yaw,combo,from+(head-from)*i/(n-1));
            in[i]=hb[0].add(hb[1].multiply(inner));out[i]=hb[0].add(hb[1].multiply(outer));
            if(i>0)len+=out[i].distanceTo(out[i-1]);along[i]=(float)len;
        }
        for(int i=0;i<n;i++)along[i]=len>1e-6?(float)(along[i]/len):i/(float)(n-1);
        return new Swept(in,out,along,n);
    }
    /** Slash {@code s} of this swing as drawn now (null outside its life): its whole path up to the blade's place. */
    static Swept now(Fx c,Slash s){
        float t=c.sec;if(t<s.from()||t>=s.eat1())return null;
        float head=Math.min(t,s.to());if(head<=s.from()+.004f)return null;
        return swept(c,c.cast.combo(),s.from(),head,s.inner(),s.outer());
    }
    /** How far slash {@code s} has turned to ink at {@code t} (0 fresh light, 1 all ink). */
    static float inked(Slash s,float t){return Ease.smooth(t,s.ink0(),s.ink1());}

    /** Hit 3's thrust: a beam along the whole line, from this far behind the hand to this far ahead of it (blocks), lit over these clip times. */
    static final double BEAM_BACK=2.2,BEAM=6.2;
    static final float BEAM_ON=.1f,BEAM_OFF=.36f;
    static float beamLit(float t){return Ease.smooth(t,BEAM_ON,BEAM_ON+.03f)*(1-Ease.smooth(t,BEAM_OFF-.12f,BEAM_OFF));}
    /** The thrust's line along the blade at the blow (it runs out ahead as the thrust drives in). */
    static Vec3d[] beam(Vec3d feet,float yaw,float t){
        var hb=blade(feet,yaw,3,blow(3,0));double grow=Ease.out(t,BEAM_ON,BEAM_ON+.05f);
        return new Vec3d[]{hb[0].subtract(hb[1].multiply(BEAM_BACK*(.4+.6*grow))),hb[0].add(hb[1].multiply(TIP+(BEAM-TIP)*grow)),hb[1]};
    }
    /** Where the finisher strikes on the ground: under the first one it struck, else where its cut met the front. */
    static Vec3d finishCentre(Fx c){
        var m=blowMemo(c,0);Vec3d f=Vec3d.fromPolar(0,c.cast.yaw());
        Vec3d at=m.at().length>0?new Vec3d(m.at()[0].x,m.feet().y,m.at()[0].z):m.feet().add(f.multiply(3.2));
        return new Vec3d(at.x,m.feet().y+.03,at.z);
    }
    /** Hit 2's ring on the ground round its caster: its radius (blocks), out where the ring went round. */
    static final double RING=3.0;

    /**
     * The ink ribbons (KagenoFire's ink pass): every slash along the blade's own path, drawn on as it sweeps, eaten from
     * the tail as it turns to ink; the turn's ring falls to the ground as a ring of ink; the thrust's beam.
     */
    @Override public void ink(Fx c){
        float t=c.sec;int combo=c.cast.combo();
        for(var s:slashes(combo)){
            var sw=now(c,s);if(sw==null)continue;
            float a=Ease.smooth(t,s.from(),s.from()+.02f);
            KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),Ease.smooth(t,s.eat0(),s.eat1()),inked(s,t),a);
        }
        if(combo==2&&t>=.4f&&t<1.4f){
            // the ring falls round their feet as a ring of ink and dries there, breaking up
            Vec3d o=floor(c,anchor(c));double r=RING+.1*Ease.out(t,.4f,.7f);
            ring(o,r-.26,r+.26,4,13,Ease.smooth(t,1.0f,1.4f),.3f+.6f*Ease.smooth(t,.45f,1.15f),.92f*Ease.smooth(t,.4f,.5f));
        }
        if(combo==3){
            float lit=beamLit(t);
            if(lit>0){var b=beam(anchor(c),c.cast.yaw(),t);beamRibbon(c,b[0],b[1],.26,2,15,Ease.smooth(t,BEAM_OFF-.14f,BEAM_OFF),Ease.smooth(t,.22f,BEAM_OFF),lit);}
        }
    }
    /** A flat ring of ink on the ground round {@code o}, from radius r0 to r1. */
    static void ring(Vec3d o,double r0,double r1,int style,int seed,float erase,float ink,float alpha){
        int n=SAMPLES+1;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){
            double a=Math.PI*2*i/(n-1);Vec3d r=new Vec3d(Math.cos(a),0,Math.sin(a));
            in[i]=o.add(r.multiply(r0));out[i]=o.add(r.multiply(r1));along[i]=i/(float)(n-1);
        }
        KagenoFire.inkRibbon(in,out,along,n,style,seed,erase,ink,alpha);
    }
    /** A straight band from a to b, its width (blocks) turned to face the camera about its own line. */
    static void beamRibbon(Fx c,Vec3d a,Vec3d b,double width,int style,int seed,float erase,float ink,float alpha){
        Vec3d axis=b.subtract(a).normalize(),toCam=c.camera.subtract(a.add(b).multiply(.5)),side=axis.crossProduct(toCam);
        if(side.lengthSquared()<1e-6)side=axis.crossProduct(UP);side=side.normalize().multiply(width*.5);
        int n=9;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){float u=i/(float)(n-1);Vec3d p=a.add(b.subtract(a).multiply(u));in[i]=p.subtract(side);out[i]=p.add(side);along[i]=u;}
        KagenoFire.inkRibbon(in,out,along,n,style,seed,erase,ink,alpha);
    }

    // ───────────────────────────── light ─────────────────────────────

    /** Brightness of the blade's aura: lit for the cuts, flaring at each blow; the finisher lights it from the twirl. */
    static float glow(float t,int combo){
        float g=switch(combo){
            case 2->Ease.window(t,.12f,.5f,.05f,.1f);
            case 3->Ease.window(t,.06f,1.08f,.04f,.14f);
            case 4->Ease.window(t,.04f,.72f,.04f,.12f);
            default->Ease.window(t,.2f,.5f,.04f,.1f);
        };
        for(int i=0;i<IsosangRules.blows(combo);i++)g+=.7f*FxLib.kick(t,blow(combo,i),.07f);
        return g;
    }
    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        aura(c,m,t,combo);
        sparks(c,m,t,combo);
        impacts(c,m,t,combo);
        if(combo==1)glint(c,m,t);
        if(combo==2)flare(c,m,t);
        if(combo==3)beamLight(c,m,t);
        if(combo==4){twirl(c,m,t);point(c,m,t);burst(c,m,t,true);}
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        blots(c,m,t,combo);
        splashes(c,m,t,combo);
        groundInk(c,m,t,combo);
        if(combo==4)burst(c,m,t,false);
    }
    /** The blade's blue aura while it cuts, brightest as it strikes. */
    static void aura(Fx c,EffectMesh m,float t,int combo){
        float g=glow(t,combo);if(g<=.01f)return;
        var hb=bladeNow(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));float on=Math.min(1,g);
        double w=.05*(1+.5*Math.max(0,Math.min(1,g-1)));float a=Math.min(1.6f,g);
        m.softLine(guard,tip,w*12,AURA[0],a*.12f);m.softLine(guard,tip,w*5.5,AURA[1],a*.3f);m.softLine(guard,tip,w*2.4,AURA[1],a*.7f);
        m.line(guard,tip,w,AURA[2],a);m.line(guard,tip,w*.45,AURA[3],a);
        m.softDisc(tip,.16+.12*on,AURA[3],.75f*on);
    }
    /** Hit 1's glint: a blue star running up the blade as it is flipped upright and tilted out. */
    static final float GLINT=.085f;
    static void glint(Fx c,EffectMesh m,float t){
        if(t<GLINT-.04f||t>=GLINT+.1f)return;
        float a=Ease.smooth(t,GLINT-.04f,GLINT)*(1-Ease.smooth(t,GLINT+.03f,GLINT+.1f));
        var hb=bladeNow(c);Vec3d p=hb[0].add(hb[1].multiply(GUARD+(TIP-GUARD)*(.45+.4*Ease.smooth(t,GLINT-.04f,GLINT+.08f))));
        m.softDisc(p,.55*a+.1,BLUE,.5f*a);m.star(p,.42*a+.06,PALE,a);m.star(p,.22*a+.03,WHITE,a);
    }
    /**
     * Sparks thrown off the edge of every slash as it sweeps: small blue, pale and white motes, born where the edge passed,
     * flying on along the sweep and outwards, slowing and fading in a quarter of a second.
     */
    static void sparks(Fx c,EffectMesh m,float t,int combo){
        int k0=0;
        for(var s:slashes(combo)){
            k0+=31;if(t<s.from()||t>=s.to()+.26f)continue;
            var sw=swept(c,combo,s.from(),s.to(),s.inner(),s.outer());int n=s.style()==3?30:18;
            for(int k=0;k<n;k++){
                float born=s.from()+(s.to()-s.from())*(k+.5f*(float)Ease.hash(k*13+k0))/n,age=t-born,life=.14f+.1f*(float)Ease.hash(k*17+k0);
                if(age<0||age>=life)continue;
                double x=(born-s.from())/(s.to()-s.from())*(sw.n()-1);int i=Math.min(sw.n()-2,(int)x);double f=x-i;
                Vec3d edge=sw.outer()[i].add(sw.outer()[i+1].subtract(sw.outer()[i]).multiply(f)),root=sw.inner()[i].add(sw.inner()[i+1].subtract(sw.inner()[i]).multiply(f));
                Vec3d way=sw.outer()[i+1].subtract(sw.outer()[i]);if(way.lengthSquared()<1e-8)continue;way=way.normalize();
                Vec3d out=edge.subtract(root);out=out.lengthSquared()<1e-8?UP:out.normalize();
                double reach=.75+.35*Ease.hash(k*19+k0),speed=2.6+2.4*Ease.hash(k*23+k0),u=age/life;
                Vec3d at=root.add(edge.subtract(root).multiply(reach)),v=way.multiply(speed).add(out.multiply(speed*.45*(Ease.hash(k*29+k0)-.2)));
                Vec3d q=at.add(v.multiply(life*(1-Math.pow(1-u,2))*.5)).add(0,-.6*age*age,0);
                float a=(float)(1-u)*Ease.smooth(age,0,.02f);int col=k%4==0?WHITE:k%4==1?PALE:AZURE;
                m.needle(q.subtract(v.normalize().multiply(.14*(1-u)+.03)),q,.05,.3,col,a);
                m.softDisc(q,.045*(1-.5*u),col,.8f*a);
            }
        }
    }
    /** Every struck target, at every blow: a blue-white burst, a ring and sparks thrown the way the blade went through. */
    static void impacts(Fx c,EffectMesh m,float t,int combo){
        for(int i=0;i<IsosangRules.blows(combo);i++){
            float at=blow(combo,i);if(t<at-.01f||t>=at+.36f)continue;
            Vec3d way=sweep(c,combo,at);
            Vec3d side=way.crossProduct(UP);if(side.lengthSquared()<1e-4)side=c.right;side=side.normalize();Vec3d up=side.crossProduct(way).normalize();
            float u=Ease.unit(t,at,at+.18f),a=Ease.smooth(t,at-.01f,at+.01f);boolean big=combo==4;
            for(var p:hits(c,i,at)){
                m.softDisc(p,(big?1.5:1.1)*(1-.4*u)+.2,BLUE,.45f*a*(1-u));m.softDisc(p,(big?.85:.5)*(1-u)+.12,WHITE,a*(1-u));
                FxLib.ringPlane(m,p,side,up,.2+(big?1.7:.9)*Ease.out(t,at,at+.16f),big?.22:.16,PALE,.8f*a*(1-Ease.unit(t,at,at+.16f)));
                float s=a*(1-Ease.smooth(t,at+.08f,at+.3f));
                for(int k=0;k<(big?24:12);k++){
                    double ang=(Ease.hash(k*3+combo*17+i*5)-.5)*2.2,rise=(Ease.hash(k*5+combo*11+i)-.5)*1.6;
                    Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(up.multiply(rise)).normalize();
                    double len=(.6+1.1*Ease.hash(k*7+combo+i*3))*(big?1.5:1)*Ease.out(t,at,at+.14f);
                    Vec3d q=p.add(dir.multiply(len*.35));
                    m.needle(q,q.add(dir.multiply(len*.65)),.06,.3,k%3==0?WHITE:AZURE,s);
                }
            }
        }
    }
    /** The way the blade's point moves at clip time {@code at} (a unit vector; the swing's facing if it stands still). */
    static Vec3d sweep(Fx c,int combo,float at){
        var a=blade(c,combo,at-.012f);var b=blade(c,combo,at+.012f);
        Vec3d w=b[0].add(b[1].multiply(TIP)).subtract(a[0].add(a[1].multiply(TIP)));
        return w.lengthSquared()<1e-6?c.f:w.normalize();
    }
    /** Hit 2's second blow: the ring flares out over the ground as the sword is flicked, a flash round them and a ring racing out. */
    static void flare(Fx c,EffectMesh m,float t){
        float at=blow(2,1);if(t<at-.02f||t>=at+.4f)return;
        Vec3d o=floor(c,anchor(c)).add(0,.08,0);float a=Ease.smooth(t,at-.02f,at+.01f),u=Ease.unit(t,at,at+.3f);
        FxLib.ring(m,o,RING-.2+1.1*Ease.out(t,at,at+.3f),.34,AZURE,.75f*a*(1-u));
        FxLib.ring(m,o,RING+.4*Ease.out(t,at,at+.2f),.18,WHITE,.7f*a*(1-Ease.unit(t,at,at+.2f)));
        FxLib.ring(m,o.add(0,.3,0),RING*.85+.6*Ease.out(t,at,at+.25f),.12,PALE,.5f*a*(1-Ease.unit(t,at,at+.25f)));
    }
    /** Hit 3's beam: its light along the whole line, rings at the point and running on ahead of it. */
    static void beamLight(Fx c,EffectMesh m,float t){
        float a=beamLit(t);if(a<=0)return;
        var b=beam(anchor(c),c.cast.yaw(),t);
        m.softLine(b[0],b[1],1.8,DEEP,.32f*a);m.softLine(b[0],b[1],.6,AZURE,.55f*a);
        m.needle(b[0],b[1].subtract(b[2].multiply(.4)),.14,.25,WHITE,.95f*a);
        var hb=blade(c,3,blow(3,0));Vec3d tip=hb[0].add(hb[1].multiply(TIP));float at=blow(3,0);
        FxLib.ringPlane(m,tip,c.right,UP,.2+.8*Ease.out(t,at-.05f,at+.08f),.14,WHITE,.8f*a);
        m.star(b[1],.6*a,PALE,.8f*a);
        for(int k=0;k<4;k++){
            float s=BEAM_ON+.025f*k,u=Ease.unit(t,s,s+.24f);if(t<s||u>=1)continue;
            Vec3d o=tip.add(b[2].multiply(.6+1.2*k+1.2*Ease.out(t,s,s+.24f)));
            FxLib.ringPlane(m,o,c.right,UP,.3+(.8+.4*k)*Ease.out(t,s,s+.2f),.16,k==0?WHITE:PALE,.8f*(1-u)*Ease.smooth(t,s,s+.015f));
        }
    }
    /** The finisher's twirl: a ring of light over the head along the point's path, gone as the aim begins. */
    static void twirl(Fx c,EffectMesh m,float t){
        if(t<.06f||t>=.32f)return;
        float from=Math.max(.06f,t-.13f),head=Math.min(t,.2f);if(head<=from+.004f)return;
        float a=.9f*(1-Ease.smooth(t,.22f,.32f));int n=18;Vec3d prev=null;
        for(int i=0;i<=n;i++){
            float s=from+(head-from)*i/n;var hb=blade(c,4,s);Vec3d p=hb[0].add(hb[1].multiply(TIP*1.05));
            if(prev!=null){float u=i/(float)n;m.softLine(prev,p,.3,AZURE,a*u*.6f);m.line(prev,p,.05,PALE,a*u);}
            prev=p;
        }
    }
    /** The finisher's aim: a glint at the pointing hand. */
    static void point(Fx c,EffectMesh m,float t){
        if(t<.25f||t>=.45f)return;
        float a=Ease.smooth(t,.25f,.29f)*(1-Ease.smooth(t,.38f,.45f));
        Vec3d hand=c.pos.add(0,1.35,0).add(c.f.multiply(.95)).add(c.left.multiply(.25));
        m.star(hand,.35*a+.1,PALE,a);m.softDisc(hand,.3,AZURE,.6f*a);
    }
    /**
     * The finisher landing: a flash and shock rings over the ground where the cut met the front (light), and a dark
     * ring racing out with the ink under it (blended).
     */
    static void burst(Fx c,EffectMesh m,float t,boolean additive){
        float at=blow(4,0);if(t<at-.01f||t>=at+.7f)return;
        Vec3d o=finishCentre(c);float a=Ease.smooth(t,at-.01f,at+.01f),u=Ease.unit(t,at,at+.45f);
        if(!additive){
            FxLib.ring(m,o.add(0,.02,0),.6+3.6*Ease.out(t,at,at+.5f),.34,INK,.5f*a*(1-u));
            return;
        }
        m.softDisc(o.add(0,1,0),1.5*(1-.5*u)+.2,BLUE,.3f*a*(1-u));
        FxLib.ring(m,o.add(0,.05,0),.4+4.2*Ease.out(t,at,at+.4f),.34,AZURE,.65f*a*(1-u));
        FxLib.ring(m,o.add(0,.04,0),.3+2.6*Ease.out(t,at,at+.3f),.2,PALE,.55f*a*(1-Ease.unit(t,at,at+.3f)));
    }

    // ───────────────────────────── ink ─────────────────────────────

    /**
     * Blots shed by every slash as it turns to ink: dark flakes born along its band, flung outwards and on along the
     * sweep, tumbling, falling and shrinking away (flat flakes in the world, not turned to the camera).
     */
    static void blots(Fx c,EffectMesh m,float t,int combo){
        int k0=0;
        for(var s:slashes(combo)){
            k0+=53;if(t<s.ink0()||t>=s.ink1()+.75f)continue;
            var sw=swept(c,combo,s.from(),s.to(),s.inner(),s.outer());int n=s.style()==3?40:s.style()==1?36:22;
            for(int k=0;k<n;k++){
                float born=s.ink0()+(s.ink1()-s.ink0())*.6f*(float)Ease.hash(k*7+k0),age=t-born,life=.38f+.34f*(float)Ease.hash(k*11+k0);
                if(age<0||age>=life)continue;
                float along=(float)Ease.hash(k*13+k0);double x=along*(sw.n()-1);int i=Math.min(sw.n()-2,(int)x);double f=x-i;
                double across=.35+.6*Ease.hash(k*17+k0);
                Vec3d in=sw.inner()[i].add(sw.inner()[i+1].subtract(sw.inner()[i]).multiply(f)),out=sw.outer()[i].add(sw.outer()[i+1].subtract(sw.outer()[i]).multiply(f));
                Vec3d at=in.add(out.subtract(in).multiply(across)),radial=out.subtract(in);radial=radial.lengthSquared()<1e-8?UP:radial.normalize();
                Vec3d way=sw.outer()[i+1].subtract(sw.outer()[i]);way=way.lengthSquared()<1e-8?c.f:way.normalize();
                double speed=.5+1.1*Ease.hash(k*19+k0);
                Vec3d v=radial.multiply(speed).add(way.multiply(speed*.6*(Ease.hash(k*23+k0)-.3))).add(0,.1+.5*Ease.hash(k*29+k0),0);
                double drag=1-Math.exp(-age*3.5);Vec3d q=at.add(v.multiply(drag/3.5)).add(0,-3.2*age*age,0);
                double floor=anchor(c).y+.03;if(q.y<floor)q=new Vec3d(q.x,floor,q.z);
                float u=age/life;double size=(.07+.13*Ease.hash(k*31+k0))*(1-.6*u)*Ease.out(age,0,.06f);
                flake(m,q,size,k*37+k0,age,INK,.9f*(1-Ease.smooth(u,.55f,1)));
            }
        }
    }
    /** Ink splashed from every struck target at every blow, and the finisher's spray of blots up out of the ground. */
    static void splashes(Fx c,EffectMesh m,float t,int combo){
        for(int i=0;i<IsosangRules.blows(combo);i++){
            float at=blow(combo,i);if(t<at||t>=at+.8f)continue;
            Vec3d way=sweep(c,combo,at);int b=0;
            for(var p:hits(c,i,at)){
                b++;
                for(int k=0;k<(combo==4?18:10);k++){
                    int seed=k*41+i*7+combo*13+b*101;float age=t-at,life=.4f+.3f*(float)Ease.hash(seed);if(age>=life)continue;
                    double ang=(Ease.hash(seed+1)-.5)*2.4,rise=Ease.hash(seed+2)*1.2-.2;
                    Vec3d side=way.crossProduct(UP);side=side.lengthSquared()<1e-4?c.right:side.normalize();
                    Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(0,rise,0).normalize();
                    double speed=1.2+2*Ease.hash(seed+3),drag=1-Math.exp(-age*4);
                    Vec3d q=p.add(dir.multiply(speed*drag/4)).add(0,-2.6*age*age,0);float u=age/life;
                    flake(m,q,(.06+.1*Ease.hash(seed+4))*(1-.5*u),seed,age,INK,.9f*(1-Ease.smooth(u,.5f,1)));
                }
            }
        }
        if(combo==4){
            float at=blow(4,0);if(t<at||t>=at+1.1f)return;
            var s=slashes(4)[0];var sw=swept(c,4,s.from(),s.to(),s.inner(),s.outer());double floor=anchor(c).y+.03;
            for(int k=0;k<56;k++){
                int seed=k*43+977;float age=t-at-.08f*(float)Ease.hash(seed),life=.6f+.4f*(float)Ease.hash(seed+1);if(age<0||age>=life)continue;
                double x=Ease.hash(seed+2)*(sw.n()-1);int i=Math.min(sw.n()-2,(int)x);double f=x-i,across=Ease.hash(seed+3);
                Vec3d in=sw.inner()[i].add(sw.inner()[i+1].subtract(sw.inner()[i]).multiply(f)),out=sw.outer()[i].add(sw.outer()[i+1].subtract(sw.outer()[i]).multiply(f));
                Vec3d start=in.add(out.subtract(in).multiply(across));start=new Vec3d(start.x,floor+.1,start.z);
                Vec3d radial=out.subtract(in).multiply(1,0,1);radial=radial.lengthSquared()<1e-8?c.f:radial.normalize();
                Vec3d v=radial.multiply(.6+1.4*Ease.hash(seed+4)).add(0,1.2+1.8*Ease.hash(seed+5),0);
                double drag=1-Math.exp(-age*2.6);Vec3d q=start.add(v.multiply(drag/2.6)).add(0,-3.2*age*age,0);float u=age/life;
                if(q.y<floor-.02)continue;
                flake(m,q,(.08+.16*Ease.hash(seed+6))*(1-.5*u)*Ease.out(age,0,.08f),seed,age,INK,.92f*(1-Ease.smooth(u,.6f,1)));
            }
        }
    }
    /**
     * Ink left on the ground: blots spreading under the low slashes as they turn to ink (the ring's, the low sweep's and
     * the giant crescent's), drying and fading out (flat on the ground, a navy rim round a black middle).
     */
    static void groundInk(Fx c,EffectMesh m,float t,int combo){
        double floor=anchor(c).y+.025;
        if(combo==2){
            Vec3d o=anchor(c);
            for(int k=0;k<26;k++){
                int seed=k*29+311;float born=.55f+.35f*(float)Ease.hash(seed);if(t<born)continue;
                double az=Math.PI*2*(k+.6*Ease.hash(seed+1))/26,r=RING+(Ease.hash(seed+2)-.5)*.9;
                Vec3d p=new Vec3d(o.x+Math.cos(az)*r,floor,o.z+Math.sin(az)*r);
                float a=.85f*(1-Ease.smooth(t,1.15f+.15f*(float)Ease.hash(seed+3),1.5f+.2f*(float)Ease.hash(seed+4)));
                blot(m,p,(.18+.3*Ease.hash(seed+5))*Ease.out(t,born,born+.12f),seed,a);
            }
        }
        if(combo==3)inkUnder(c,m,t,slashes(3)[3],floor,22,577,1.1f,1.35f,1.6f,1.95f,.16,.42);
        if(combo==4)inkUnder(c,m,t,slashes(4)[0],floor,48,733,.86f,1.2f,1.65f,2.2f,.25,.72);
    }
    /**
     * Blots on the ground under slash {@code s}'s band (projected straight down), each appearing at a time in
     * [born0, born1] and fading out over a time in [fade0, fade1].
     */
    static void inkUnder(Fx c,EffectMesh m,float t,Slash s,double floor,int n,int seed0,float born0,float born1,float fade0,float fade1,double small,double big){
        if(t<born0)return;
        var sw=swept(c,c.cast.combo(),s.from(),s.to(),s.inner(),s.outer());
        for(int k=0;k<n;k++){
            int seed=k*31+seed0;float born=born0+(born1-born0)*(float)Ease.hash(seed);if(t<born)continue;
            double x=Ease.hash(seed+1)*(sw.n()-1);int i=Math.min(sw.n()-2,(int)x);double f=x-i,across=.15+.85*Ease.hash(seed+2);
            Vec3d in=sw.inner()[i].add(sw.inner()[i+1].subtract(sw.inner()[i]).multiply(f)),out=sw.outer()[i].add(sw.outer()[i+1].subtract(sw.outer()[i]).multiply(f));
            Vec3d p=in.add(out.subtract(in).multiply(across));
            float fade=fade0+(fade1-fade0)*(float)Ease.hash(seed+3),a=.88f*(1-Ease.smooth(t,fade-.3f,fade));
            blot(m,new Vec3d(p.x,floor,p.z),(small+(big-small)*Ease.hash(seed+4))*Ease.out(t,born,born+.14f),seed,a);
        }
    }
    /** One blot of ink lying on the ground at {@code p}: an uneven round patch, a navy rim round a black middle, with a droplet or two beside it. */
    static void blot(EffectMesh m,Vec3d p,double size,int seed,float alpha){
        if(alpha<=.01f||size<=.01)return;
        patch(m,p,size*1.12,seed,NAVY,.5f*alpha);patch(m,p.add(0,.002,0),size,seed,INK,alpha);
        for(int j=0;j<2;j++){
            double az=Ease.hash(seed+40+j)*Math.PI*2,r=size*(1.25+.6*Ease.hash(seed+50+j));
            patch(m,p.add(Math.cos(az)*r,.003,Math.sin(az)*r),size*(.16+.14*Ease.hash(seed+60+j)),seed+70+j,INK,alpha);
        }
    }
    static void patch(EffectMesh m,Vec3d p,double size,int seed,int rgb,float alpha){
        int sides=10;Vec3d[] q=new Vec3d[sides];
        for(int i=0;i<sides;i++){
            double a=Math.PI*2*i/sides,r=size*(.7+.45*Ease.hash(seed+i*7+3));
            q[i]=p.add(Math.cos(a)*r,0,Math.sin(a)*r);
        }
        for(int i=1;i+1<sides;i++)m.triangle(p,q[i],q[i+1],rgb,alpha);
        m.triangle(p,q[sides-1],q[0],rgb,alpha);m.triangle(p,q[0],q[1],rgb,alpha);
    }
    /**
     * One flat flake of ink at {@code q}: an irregular six-sided blot in a plane of its own that tumbles as it flies
     * (a seeded tilt and spin), with a thin navy rim round a black middle.
     */
    static void flake(EffectMesh m,Vec3d q,double size,int seed,float age,int rgb,float alpha){
        if(alpha<=.01f||size<=.005)return;
        double spin=(Ease.hash(seed+7)-.5)*14*age,tilt=Ease.hash(seed+8)*Math.PI,az=Ease.hash(seed+9)*Math.PI*2;
        Vec3d n=new Vec3d(Math.sin(tilt+spin)*Math.cos(az),Math.cos(tilt+spin),Math.sin(tilt+spin)*Math.sin(az)).normalize();
        Vec3d u=n.crossProduct(Math.abs(n.y)<.9?UP:new Vec3d(1,0,0)).normalize(),v=n.crossProduct(u).normalize();
        int sides=6;Vec3d[] p=new Vec3d[sides];
        for(int i=0;i<sides;i++){
            double a=Math.PI*2*i/sides+.4*Ease.hash(seed+i*3),r=size*(.55+.6*Ease.hash(seed+i*5+1));
            p[i]=q.add(u.multiply(Math.cos(a)*r)).add(v.multiply(Math.sin(a)*r));
        }
        for(int i=1;i+1<sides;i++)m.triangle(p[0],p[i],p[i+1],rgb,alpha);
    }

    // ───────────────────────────── the effect layer ─────────────────────────────

    /** Lights: the blade's aura as it cuts, a blue-white flash on every struck target, the beam lighting the way ahead, the finisher's burst. */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;int combo=c.cast.combo();
        float g=Math.min(1.4f,glow(t,combo));
        if(g>.02f){var hb=bladeNow(c);f.light(hb[0].add(hb[1].multiply(.9)),AURA[1],5.5,1.1f*g);}
        for(int i=0;i<IsosangRules.blows(combo);i++){
            float at=blow(combo,i),hit=FxLib.kick(t,at,.1f);
            if(hit>.01f)for(var p:hits(c,i,at))f.light(p,0xcfe4ff,combo==4?9:7,(combo==4?3f:2.2f)*hit);
        }
        if(combo==2){float fl=FxLib.kick(t,blow(2,1),.14f);if(fl>.01f)f.light(anchor(c).add(0,.5,0),0x8cbcff,9,2.2f*fl);}
        if(combo==3){float lit=beamLit(t);if(lit>0){var b=beam(anchor(c),c.cast.yaw(),t);f.light(b[0].add(b[1]).multiply(.5),0x6aa8ff,9,2.4f*lit);}}
        if(combo==4){
            float at=blow(4,0);
            if(t>=at-.01f){
                Vec3d o=finishCentre(c);float near=(float)Math.max(0,Math.min(1,(22-c.camera.distanceTo(o))/10));
                f.light(o.add(0,1,0),0x9cc8ff,14,4.2f*FxLib.kick(t,at,.2f));
                if(near>0)f.shaft(o.add(0,.6,0),0xb8d8ff,.35f*near*FxLib.kick(t,at,.18f));
            }
            // the lunge and the great cut are quick: their afterglow is cut short so they do not smear (FxFrame.afterglow)
            if(t>=.44f&&t<.66f)f.afterglow(.4f);
        }
        if(combo==2&&t>=.16f&&t<.34f)f.afterglow(.45f);
        if(combo==3&&t>=.08f&&t<.2f)f.afterglow(.5f);
    }
    /** The air bends along each slash as it sweeps, and in rings round the finisher's blow. */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;int combo=c.cast.combo();
        for(var s:slashes(combo)){
            var sw=now(c,s);if(sw==null)continue;
            d.ribbon(sw.inner(),sw.outer(),sw.n(),.55f*Ease.smooth(t,s.from(),s.from()+.03f)*(1-Ease.smooth(t,s.to(),s.eat1())));
        }
        if(combo==4){
            float at=blow(4,0);
            if(t>=at-.01f&&t<at+.45f){Vec3d o=finishCentre(c).add(0,.9,0);float in=Ease.smooth(t,at-.01f,at+.015f);d.ring(o,c.right,c.f,.5+6*Ease.out(t,at,at+.45f),1.6,.8f*in*(1-Ease.unit(t,at,at+.45f)));}
        }
    }

    // ───────────────────────────── camera ─────────────────────────────

    /** Shake as the blows land, a little harder for the thrust and the finisher; each comes in over two frames. */
    @Override public float shake(StandClient.Cast cast,float sec){
        int combo=cast.combo();float v=0;
        for(int i=0;i<IsosangRules.blows(combo);i++){
            float at=blow(combo,i),amp=combo==4?1.5f:combo==3?(i==0?.8f:i==4?.6f:.4f):combo==2?.55f:.6f;
            v=Math.max(v,amp*bump(sec,at,combo==4?.12f:.08f));
        }
        return v;
    }
    static float bump(float sec,float at,float tau){return sec<at-.02f?0:Ease.smooth(sec,at-.02f,at+.015f)*(float)Math.exp(-Math.max(0,sec-at-.015f)/tau);}
    /** Speed afterimages for the fast parts: the turn, the cuts, the lunge and the great cut. */
    @Override public float afterimages(float sec){return -1;}
    /**
     * How far the caster's own camera draws back for the finisher (blocks, as in the user's video: it pulls back over the
     * crouch and the lunge to show the giant cut, holds while it spreads into ink and comes back in), 0 for the others.
     */
    public static final double PULL=1.6;
    public static double pull(int combo,float sec){
        if(combo!=4)return 0;
        return PULL*Ease.smooth(sec,.38f,.62f)*(1-Ease.smooth(sec,1.05f,1.6f));
    }
}
