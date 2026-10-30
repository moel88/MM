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
 * Lee So-sang's basic attack as drawn (2.77.0), rebuilt from the ground up after the user's videos of each hit on its own
 * (2.77.2, user request: "완전 달라 전부 갈아엎고 이펙트를 대폭 강화해주고 영상 다시 보고 와", "애초에 4타는 참격을
 * 날리는건데"): the body's four hits and their slashes. Every hit is a swing of its own (its clock the server's,
 * IsosangClient), its blows on the server's ticks (IsosangRules).
 * <ol>
 * <li>참월(斬月): the sword flashing into the hand with a glint, a turn, the sword raised and coiled at the high right,
 * a great diagonal cut down round the left (a crescent wider than they are tall, wrapping round them, a thick stroke of
 * black ink along its outer edge) and a long cut rising straight back up to the high right, held up there.</li>
 * <li>윤월(輪月): a turn into a crouch and a whole spin low over the ground: a wide ring round them that bursts into ink
 * (black brush rings opening in it, ink flung off) and falls to the ground as a ring of ink and splashes.</li>
 * <li>관월(貫月): a lunging thrust (a beam of light far through the foe), a great diagonal cut down from overhead, a cut
 * rising back from the low left, a sweep low across the front (ink on the ground under it) and a wide sweep across at
 * the head; the free hand pointed at the end.</li>
 * <li>낙월(落月): a turn with the sword twirled overhead (a ring of pale light), the free hand pointed at the foe, a
 * crouch, a lunge and a low cut that throws a giant crescent of a slash flying ahead, widening as it goes (IsosangRules'
 * flight: the server strikes as it passes); a ring of ink brushed on the ground round the cut, ink dripping off the
 * slash's back edge and splashed over the ground along its way. Its caster's own camera draws back for it.</li>
 * </ol>
 * The slashes are explicit crescents in the hit's frame ({@link Arc}), each sweeping on in time with the blade, drawn by
 * KagenoFire's ink ribbons (shader mode 16): a vivid blue body glowing inward from a white-hot cutting line, a thick
 * black brush stroke along their outer edge; as they fade the light drains out, the ink spreads, blots open and they
 * break up into drops of ink that fly off and fall. On the effect layer the light burns past white and blooms. Every
 * effect is a function of the swing's clock, so it moves at the render frame rate.
 */
public final class IsosangSlash implements Fx.Skill {
    /** Ink, navy, deep blue, blue, azure, pale blue, white; the twirl's pale cyan. */
    static final int INK=0x04060f,NAVY=0x0b1446,DEEP=0x1530b0,BLUE=0x2a5cff,AZURE=0x58a0ff,PALE=0xbfe2ff,WHITE=0xffffff,CYAN=0x9fe8ff;
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
    /** The sword flashed into the hand, flipped up at the right side; held out up to the right as it glints. */
    static final R6.Pose H1_FLASH=w(.02,-.02,-6,0,0,d(-40,48),d(55,48),d(-82,-12),d(-88,4),d(-88,-4));
    static final R6.Pose H1_GLINT=w(.04,-.03,-10,0,0,d(-20,62),d(48,60),d(-80,-16),d(-86,6),d(-88,-6));
    /** Turned to the right, the sword level at the shoulder pointing at the foe. */
    static final R6.Pose H1_TURN=w(.1,-.06,-48,2,0,d(10,34),d(4,10),d(-60,-60),d(-80,14),d(-84,186));
    /** Raised overhead, pointing back. */
    static final R6.Pose H1_RAISE=w(.16,-.09,-56,-4,0,d(64,40),d(30,160),d(-50,-52),d(-74,16),d(-80,188));
    /** Down in a coil, cocked at the high right, pointing back over the shoulder (a moving hold). */
    static final R6.Pose H1_COIL=w(.26,-.2,-62,10,0,d(40,96),d(24,150),d(-40,-70),d(-62,18),d(-76,190));
    static final R6.Pose H1_LOAD=w(.28,-.21,-64,10,0,d(42,100),d(26,156),d(-40,-72),d(-62,18),d(-76,190));
    /** The great cut: from the high front-right down across the front and on round the low left. */
    static final R6.Pose H1_HIGH=w(.31,-.22,-30,12,0,d(32,58),d(26,62),d(-40,-96),d(-60,16),d(-74,190));
    static final R6.Pose H1_FRONT=w(.34,-.24,-4,15,-1,d(8,18),d(-6,14),d(-38,-120),d(-58,16),d(-72,190));
    static final R6.Pose H1_CUT=w(.36,-.26,18,17,-2,d(-12,-24),d(-16,-40),d(-34,-148),d(-56,16),d(-70,190));
    static final R6.Pose H1_PAST=w(.37,-.27,36,18,-3,d(-22,-58),d(-12,-86),d(-36,-158),d(-55,16),d(-69,190));
    static final R6.Pose H1_LOW=w(.38,-.28,48,19,-3,d(-30,-82),d(-10,-120),d(-38,-162),d(-54,16),d(-68,190));
    /** And straight back up across the front to the high right, the arm thrown up. */
    static final R6.Pose H1_RISE=w(.4,-.2,10,10,0,d(4,4),d(18,10),d(-42,-120),d(-56,16),d(-70,190));
    static final R6.Pose H1_UP=w(.42,-.12,-20,0,0,d(48,48),d(56,52),d(-52,-110),d(-58,16),d(-72,190));
    /** Held up there: where the second hit begins. */
    static final R6.Pose H1_HOLD=w(.4,-.1,-22,-2,0,d(46,50),d(54,54),d(-58,-100),d(-60,16),d(-72,190));
    static final R6.Pose H1_LOWER=w(.24,-.06,-12,2,0,d(-20,60),d(18,66),d(-70,-50),d(-74,12),d(-80,188));
    static final Anim H1=Anim.joints(
        k(0,STANCE),
        k(.03,H1_FLASH),
        k(.07,H1_GLINT),
        k(.13,H1_TURN),
        k(.18,H1_RAISE),
        k(.23,H1_COIL),
        k(.25,H1_LOAD),
        k(.27,H1_HIGH,Anim.Ease.LINEAR),
        k(.285,H1_FRONT,Anim.Ease.LINEAR),
        k(blow(1,0),H1_CUT,Anim.Ease.LINEAR),
        k(.315,H1_PAST,Anim.Ease.LINEAR),
        k(.33,H1_LOW,Anim.Ease.OUT),
        k(.355,H1_RISE,Anim.Ease.LINEAR),
        k(.38,H1_UP,Anim.Ease.OUT),
        k(step(1),H1_HOLD),
        k(.72,H1_LOWER),
        k(.9,STANCE)).stops(Anim.stop(blow(1,0),.03,.05));

    // ───────────────────────────── 2 윤월 ─────────────────────────────
    // Body-space keys while they turn: the turn is the root's yaw, the arm and the blade ride with it.
    /** Turning right, going down, the sword brought down behind on the right. */
    static final R6.Pose H2_TURN=R6.pose().at(.38,-.24,0).body(-90,8,0).head(-6,-6,0).rArm(-34,120).blade(-14,146).lArm(-46,-56).rLeg(-72,24).lLeg(-80,-22);
    /** Crouched, coiled, the blade low out behind on the right. */
    static final R6.Pose H2_LOW=R6.pose().at(.38,-.46,0).body(-150,14,0).head(0,-10,0).rArm(-36,100).blade(-6,116).lArm(-52,-74).rLeg(-66,30).lLeg(-84,-30);
    /** The whole spin back to the left on one foot: the blade held out low at the right, sweeping round over the ground. */
    static R6.Pose spin(float yaw){return R6.pose().at(.38,-.44,0).body(yaw,12,0).head(0,-8,0).rArm(-32,88).blade(-6,94).lArm(-50,-82).rLeg(-68,28).lLeg(-44,-34);}
    /** Out of the spin, rising; the blade snapped out down to the right as the ring bursts into ink. */
    static final R6.Pose H2_RISE=R6.pose().at(.36,-.3,0).body(246,8,0).head(0,-6,0).rArm(-40,74).blade(-10,92).lArm(-60,-60).rLeg(-76,24).lLeg(-82,-24);
    static final R6.Pose H2_FLICK=R6.pose().at(.35,-.16,0).body(258,4,0).head(0,-4,0).rArm(-34,84).blade(-18,104).lArm(-72,-40).rLeg(-82,14).lLeg(-86,-14);
    static final R6.Pose H2_SET=R6.pose().at(.34,-.12,0).body(262,3,0).head(0,-4,0).rArm(-44,70).blade(-18,86).lArm(-74,-34).rLeg(-84,12).lLeg(-86,-12);
    /** Turned back to the front, the sword low at the right: where the third hit begins (yaw: 356, or -4 for the next clip). */
    static R6.Pose h2Back(float yaw){return w(.34,-.08,yaw,4,0,d(-50,30),d(-18,40),d(-72,-20),d(-80,10),d(-86,-8));}
    static final Anim H2=Anim.joints(
        k(0,H1_HOLD),
        k(.05,H2_TURN),
        k(.085,H2_LOW),
        // the spin: an even speed from behind round the right, the front (the blow) and the left, on round behind
        k(.1,spin(-120),Anim.Ease.IN),
        k(.115,spin(-40),Anim.Ease.LINEAR),
        k(.13,spin(40),Anim.Ease.LINEAR),
        k(blow(2,0),spin(120),Anim.Ease.LINEAR),
        k(.165,spin(195),Anim.Ease.LINEAR),
        k(.19,spin(232),Anim.Ease.OUT),
        k(.24,H2_RISE),
        k(blow(2,1),H2_FLICK,Anim.Ease.OUT),
        k(.38,H2_SET),
        k(step(2),h2Back(356)),
        // a whole turn on: the stance faces the same way as the start
        k(.9,STANCE.body(360,0,0))).stops(Anim.stop(blow(2,0),.025,.04),Anim.stop(blow(2,1),.03,.05));

    // ───────────────────────────── 3 관월 ─────────────────────────────
    /** Drawn back side-on: the hand at the right hip, the point level at the foe, the free hand aiming along it. */
    static final R6.Pose H3_DRAW=w(.34,-.24,-40,6,0,d(-48,164),d(2,3),d(-8,-10),d(-60,20),d(-76,190));
    static final R6.Pose H3_COIL=w(.3,-.3,-46,8,0,d(-46,168),d(2,4),d(-6,-12),d(-56,22),d(-74,192));
    /** The thrust: the sword shoulder driven through in a long lunge, the free arm thrown back. */
    static final R6.Pose H3_THRUST=w(.9,-.32,40,12,0,d(-3,-6),d(0,-2),d(-24,-172),d(-50,16),d(-58,192));
    static final R6.Pose H3_HELD=w(.92,-.32,42,12,0,d(-2,-5),d(1,-2),d(-26,-174),d(-50,16),d(-58,192));
    /** Raised overhead and cocked at the high right; the great cut down across the front to the low left. */
    static final R6.Pose H3_OVER=w(.84,-.2,-20,-6,0,d(70,40),d(40,160),d(-50,-80),d(-58,16),d(-70,190));
    static final R6.Pose H3_COCK=w(.84,-.22,-30,0,0,d(50,80),d(50,110),d(-46,-90),d(-58,16),d(-70,190));
    static final R6.Pose H3_DOWN1=w(.85,-.25,-6,10,0,d(20,40),d(8,30),d(-40,-110),d(-56,16),d(-70,190));
    static final R6.Pose H3_DOWN2=w(.86,-.28,20,16,0,d(-14,-20),d(-18,-40),d(-36,-140),d(-56,16),d(-70,190));
    static final R6.Pose H3_LOWL=w(.86,-.3,36,18,0,d(-26,-60),d(-14,-90),d(-36,-150),d(-55,16),d(-69,190));
    static final R6.Pose H3_LOWL2=w(.86,-.3,40,18,0,d(-28,-66),d(-12,-100),d(-38,-152),d(-55,16),d(-69,190));
    /** The cut rising back from the low left to the high front-right. */
    static final R6.Pose H3_UP1=w(.87,-.22,10,10,0,d(4,4),d(16,8),d(-42,-120),d(-56,16),d(-70,190));
    static final R6.Pose H3_UP2=w(.88,-.14,-18,2,0,d(42,46),d(50,50),d(-50,-110),d(-58,16),d(-72,190));
    static final R6.Pose H3_HIGHR=w(.88,-.14,-24,2,0,d(40,60),d(44,70),d(-50,-104),d(-58,16),d(-72,190));
    /** Dropped to the low right, swept low across the front to the left. */
    static final R6.Pose H3_DROPR=w(.88,-.28,-40,12,0,d(-34,110),d(-8,135),d(-40,-90),d(-58,16),d(-72,190));
    static final R6.Pose H3_SWEEPR=w(.89,-.3,-14,14,0,d(-22,60),d(-8,70),d(-38,-120),d(-56,16),d(-70,190));
    static final R6.Pose H3_SWEEP=w(.89,-.3,6,15,0,d(-18,10),d(-8,8),d(-36,-140),d(-56,16),d(-70,190));
    static final R6.Pose H3_SWEEPL=w(.89,-.3,36,15,0,d(-20,-60),d(-6,-80),d(-36,-150),d(-55,16),d(-69,190));
    /** Up the left and the wide sweep across at the head to the right. */
    static final R6.Pose H3_WINDL=w(.88,-.24,46,10,0,d(-6,-96),d(4,-120),d(-40,150),d(-56,16),d(-70,190));
    static final R6.Pose H3_WIDE1=w(.88,-.2,14,6,0,d(14,-30),d(22,-52),d(-40,-160),d(-56,16),d(-70,190));
    static final R6.Pose H3_WIDE2=w(.88,-.18,-6,4,0,d(16,8),d(20,-14),d(-42,-150),d(-56,16),d(-70,190));
    static final R6.Pose H3_WIDE3=w(.88,-.18,-36,4,0,d(10,70),d(10,100),d(-44,-110),d(-58,16),d(-72,190));
    /** Held out to the right, the free hand pointed ahead. */
    static final R6.Pose H3_OUT=w(.8,-.16,-24,6,0,d(-4,80),d(6,92),d(0,-12),d(-58,16),d(-72,190));
    static final Anim H3=Anim.joints(
        k(0,h2Back(-4)),
        k(.12,H3_DRAW),
        k(.22,H3_COIL),
        k(.29,H3_THRUST,Anim.Ease.IN),
        k(blow(3,0),H3_HELD,Anim.Ease.OUT),
        k(.38,H3_OVER),
        k(.44,H3_COCK),
        k(.475,H3_DOWN1,Anim.Ease.LINEAR),
        k(blow(3,1),H3_DOWN2,Anim.Ease.LINEAR),
        k(.54,H3_LOWL,Anim.Ease.OUT),
        k(.64,H3_LOWL2),
        k(.67,H3_UP1,Anim.Ease.LINEAR),
        k(blow(3,2),H3_UP2,Anim.Ease.OUT),
        k(.74,H3_HIGHR),
        k(.77,H3_DROPR),
        k(.79,H3_SWEEPR,Anim.Ease.LINEAR),
        k(blow(3,3),H3_SWEEP,Anim.Ease.LINEAR),
        k(.82,H3_SWEEPL,Anim.Ease.OUT),
        k(.86,H3_WINDL),
        k(.885,H3_WIDE1,Anim.Ease.LINEAR),
        k(blow(3,4),H3_WIDE2,Anim.Ease.LINEAR),
        k(.93,H3_WIDE3,Anim.Ease.OUT),
        k(1.1,H3_OUT),
        k(1.4,STANCE)).stops(Anim.stop(blow(3,0),.035,.03),Anim.stop(blow(3,1),.03,.04),Anim.stop(blow(3,4),.04,.06));

    // ───────────────────────────── 4 낙월 ─────────────────────────────
    /** Turning right, the sword raised overhead. */
    static final R6.Pose H4_UP=w(.1,-.02,-50,-4,0,d(64,-20),d(80,-60),d(-70,30),d(-86,6),d(-88,-6));
    /** The twirl: the arm up over the head, the blade level, turning round it (body-space), the body still turning right. */
    static R6.Pose twirl(double az,float yaw){return R6.pose().at(.12,0,0).body(yaw,-4,0).head(0,-10,0).rArm(80,16).blade(10,az).lArm(-60,-40).rLeg(-88,6).lLeg(-88,-6);}
    /** Aimed: side-on, the free hand pointed at the foe, the sword held level behind at the chest. */
    static final R6.Pose H4_POINT=w(.14,-.16,-70,4,0,d(-20,140),d(0,176),d(6,-4),d(-66,18),d(-82,188));
    static final R6.Pose H4_POINT2=w(.14,-.2,-72,6,0,d(-22,142),d(0,178),d(4,-3),d(-64,18),d(-80,188));
    /** Down in a crouch; the lunge, the blade trailing low behind. */
    static final R6.Pose H4_CROUCH=w(.2,-.5,-62,16,0,d(-40,150),d(-6,172),d(-10,-20),d(-50,20),d(-74,192));
    static final R6.Pose H4_DASH=w(1.0,-.42,-50,24,0,d(-36,146),d(0,170),d(-20,-40),d(-46,16),d(-60,194));
    /** The low cut that throws the slash: from the right behind round the front, low, to the left. */
    static final R6.Pose H4_DRAW=w(1.02,-.4,-30,20,0,d(-18,96),d(-4,120),d(-24,-70),d(-48,16),d(-60,194));
    static final R6.Pose H4_SWING=w(1.03,-.4,-10,19,0,d(-16,50),d(-3,60),d(-24,-90),d(-48,16),d(-60,194));
    static final R6.Pose H4_CUT=w(1.04,-.4,8,18,0,d(-16,6),d(-3,8),d(-26,-110),d(-48,16),d(-60,194));
    static final R6.Pose H4_ON=w(1.05,-.41,40,18,0,d(-18,-64),d(-4,-74),d(-26,-160),d(-48,16),d(-60,194));
    /** Followed through behind the left, low, the free arm thrown back, watching the slash fly. */
    static final R6.Pose H4_END=w(1.05,-.42,62,18,0,d(-28,-100),d(-3,-126),d(-18,150),d(-48,16),d(-62,194));
    static final R6.Pose H4_HOLD=w(1.04,-.4,60,17,0,d(-30,-98),d(-8,-124),d(-20,148),d(-49,16),d(-62,194));
    /** Up on the feet, the sword raised upright at the right side, and lowered. */
    static final R6.Pose H4_RISE=w(.6,-.12,-10,0,0,d(10,70),d(80,70),d(-70,-20),d(-82,12),d(-86,-10));
    static final R6.Pose H4_LOWER=w(.25,-.06,-6,2,0,d(-30,40),d(30,30),d(-80,-12),d(-86,6),d(-88,-6));
    static final Anim H4=Anim.joints(
        k(0,STANCE),
        k(.07,H4_UP),
        // the twirl: a whole turn of the blade over the head, a quarter every 0.05 s, as the body turns on right
        k(.12,twirl(0,-90)),
        k(.17,twirl(90,-110),Anim.Ease.LINEAR),
        k(.22,twirl(180,-126),Anim.Ease.LINEAR),
        k(.27,twirl(270,-136),Anim.Ease.LINEAR),
        k(.32,twirl(360,-142)),
        // aimed, down, the lunge, and the low cut round the front that throws the slash
        k(.42,H4_POINT),
        k(.54,H4_POINT2),
        k(.6,H4_CROUCH),
        k(.66,H4_DASH,Anim.Ease.OUT),
        k(.68,H4_DRAW),
        k(.69,H4_SWING,Anim.Ease.LINEAR),
        k(blow(4,0),H4_CUT,Anim.Ease.LINEAR),
        k(.72,H4_ON,Anim.Ease.LINEAR),
        k(.78,H4_END,Anim.Ease.OUT),
        k(1.1,H4_HOLD),
        // up again unhurried, the sword raised and lowered (the combo is over)
        k(1.35,H4_RISE),
        k(1.55,H4_LOWER),
        k(1.8,STANCE)).stops(Anim.stop(blow(4,0),.07,.12));

    @Override public Anim clip(int combo){return hit(combo);}
    static Anim hit(int combo){return switch(combo){case 2->H2;case 3->H3;case 4->H4;default->H1;};}
    @Override public float lifetime(){return 2.8f;}
    @Override public boolean layered(){return true;}
    @Override public int afterimageColor(){return 0x4a7cff;}

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
    /** A point of the hit's frame (forward, up, right from the feet, blocks) in the world, the hit facing {@code yaw}. */
    static Vec3d world(Vec3d feet,float yaw,Vec3d local){
        Vec3d f=Vec3d.fromPolar(0,yaw),r=new Vec3d(-f.z,0,f.x);
        return feet.add(f.multiply(local.x)).add(0,local.y,0).add(r.multiply(local.z));
    }
    static Vec3d at(Fx c,double fw,double up,double rt){return world(anchor(c),c.cast.yaw(),new Vec3d(fw,up,rt));}
    /** A direction of the hit's frame in the world. */
    static Vec3d dir(float yaw,Vec3d local){return world(Vec3d.ZERO,yaw,local);}
    static Vec3d v(double fw,double up,double rt){return new Vec3d(fw,up,rt);}

    /** What each blow left, held from the first frame at or past it: the caster's feet then, whom it struck and their chests. */
    private record Memo(Vec3d feet,int[] ids,Vec3d[] at){}
    private static final Map<Long,Memo> MEMOS=new LinkedHashMap<>();
    static Memo blowMemo(Fx c,int i){
        long key=c.cast.id().getLeastSignificantBits()*7+i;var m=MEMOS.get(key);if(m!=null)return m;
        var hit=struck(c,i,anchor(c));
        m=new Memo(c.pos,hit.stream().mapToInt(LivingEntity::getId).toArray(),hit.stream().map(e->chest(c,e)).toArray(Vec3d[]::new));
        MEMOS.put(key,m);while(MEMOS.size()>96)MEMOS.remove(MEMOS.keySet().iterator().next());
        return m;
    }
    /**
     * Who blow {@code i} strikes from {@code at}: the server's word once it came, else those in its way as the server
     * picks them (the thrown slash: everyone its flight passes over).
     */
    static List<LivingEntity> struck(Fx c,int i,Vec3d at){
        var world=c.mc.world;if(world==null)return List.of();
        List<LivingEntity> out=new ArrayList<>();
        var told=IsosangClient.struck(c.cast.id(),i);int combo=c.cast.combo();
        // (the thrown slash's word comes tick by tick as it strikes: until it has flown, those it has not told of yet are foreseen)
        boolean final_=combo!=4||c.sec>blow(4,0)+IsosangRules.WAVE_TICKS/20f+.15f;
        if(told!=null&&(told.length>0||final_)){for(int id:told)if(world.getEntityById(id) instanceof LivingEntity e&&e.isAlive())out.add(e);if(final_||combo!=4)return out;}
        UUID caster=c.cast.caster();
        Vec3d f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x);
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(IsosangRules.MAX_REACH+3),e->e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!e.getUuid().equals(caster)&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d o=e.getPos().subtract(at);double fw=o.dotProduct(f),side=o.dotProduct(right),half=e.getWidth()*.5;
            if(out.contains(e))continue;
            if(combo==4?IsosangRules.waveSweeps(0,IsosangRules.WAVE_TICKS,fw,side,o.y,half):IsosangRules.lane(combo,i,fw,side,o.y,half))out.add(e);
            if(out.size()>=6)break;
        }
        return out;
    }
    static Vec3d chest(Fx c,LivingEntity e){return e.getLerpedPos(c.delta).add(0,e.getHeight()*.58,0);}
    /** Where blow {@code i}'s targets are drawn struck: their chests following them up to it, held where it found them after. */
    static List<Vec3d> hits(Fx c,int i,float at){
        if(c.sec<at){List<Vec3d> out=new ArrayList<>();for(var e:struck(c,i,anchor(c)))out.add(chest(c,e));return out;}
        return List.of(blowMemo(c,i).at());
    }
    /** The caster's floor under {@code p} (they stand where they are while a hit holds them). */
    static Vec3d floor(Fx c,Vec3d p){return new Vec3d(p.x,anchor(c).y+.02,p.z);}

    // ───────────────────────────── the slashes ─────────────────────────────

    /**
     * A slash: a crescent swept round {@code centre} in the plane of {@code a} (its angle 0) and {@code b} (90°), between
     * {@code inner} and {@code outer} blocks out, from angle {@code from} to {@code to} (degrees), all in the hit's frame
     * (forward, up, right from its user's feet); its head sweeping on over [t0, t1] (smoothly, or at an even speed for a
     * spin), eaten from the tail over [eat0, eat1] while it turns to ink over [ink0, ink1]. Style: the ink ribbons' (0 a
     * cut, 1 the ring, 6 the long cut rising back).
     */
    record Arc(Vec3d centre,Vec3d a,Vec3d b,double inner,double outer,double from,double to,float t0,float t1,float eat0,float eat1,float ink0,float ink1,int style,int seed,boolean even){}
    /**
     * A plane whose normal leans {@code tilt} degrees from straight up toward {@code toward} (an azimuth, right +): its
     * angle 0 as near forward as the plane goes, 90° to the right of that.
     */
    static Vec3d[] plane(double tilt,double toward){
        double t=Math.toRadians(tilt),z=Math.toRadians(toward);
        Vec3d n=v(Math.sin(t)*Math.cos(z),Math.cos(t),Math.sin(t)*Math.sin(z));
        Vec3d a=v(1,0,0).subtract(n.multiply(n.x)).normalize();
        return new Vec3d[]{a,a.crossProduct(n).normalize()};
    }
    static Arc arc(Vec3d centre,double tilt,double toward,double inner,double outer,double from,double to,float t0,float t1,float eat0,float eat1,float ink0,float ink1,int style,int seed,boolean even){
        var p=plane(tilt,toward);return new Arc(centre,p[0],p[1],inner,outer,from,to,t0,t1,eat0,eat1,ink0,ink1,style,seed,even);
    }
    /**
     * A slash along a chord: its cutting edge from {@code p0} to {@code p1}, bowed {@code sag} blocks out toward
     * {@code bow}, the band {@code width} deep inside it.
     */
    static Arc chord(Vec3d p0,Vec3d p1,Vec3d bow,double sag,double width,float t0,float t1,float eat0,float eat1,float ink0,float ink1,int style,int seed){
        Vec3d d1=p1.subtract(p0);double half=d1.length()/2;d1=d1.normalize();
        Vec3d d2=bow.subtract(d1.multiply(bow.dotProduct(d1))).normalize();
        double r=(half*half+sag*sag)/(2*sag),alpha=Math.toDegrees(Math.asin(Math.min(1,half/r)));
        Vec3d centre=p0.add(p1).multiply(.5).subtract(d2.multiply(r-sag));
        return new Arc(centre,d2,d1,r-width,r,-alpha,alpha,t0,t1,eat0,eat1,ink0,ink1,style,seed,false);
    }
    /**
     * Every hit's slashes. 1: the great cut (its plane rising to the front-right, wrapping round the left) and the long
     * cut rising back. 2: the ring (low over the ground, the spin's even speed). 3: the cut down from overhead, the cut
     * rising back, the low sweep and the wide sweep at the head (the thrust's beam: {@link #beam}). 4: the low cut that
     * throws the slash (the slash itself: {@link #wave}).
     */
    static final Arc[] H1_ARCS={
        arc(v(.35,1.55,.05),22,-135,2.9,4.2,72,-200,.265f,.35f,.42f,.64f,.38f,.6f,0,1,true),
        chord(v(-1.2,.45,-1.9),v(2.6,3.5,2.2),v(.6,.5,-1),.8,1.0,.345f,.39f,.5f,.74f,.46f,.7f,6,2)};
    static final Arc[] H2_ARCS={arc(v(.35,.5,0),3,180,2.6,3.9,214,-146,.1f,.185f,.52f,.86f,.3f,.64f,1,3,true)};
    static final Arc[] H3_ARCS={
        chord(v(1.2,3.7,1.5),v(2.4,.25,-2.5),v(1,.2,.2),1.2,1.3,.445f,.505f,.62f,.86f,.56f,.8f,0,5),
        chord(v(1.8,.3,-2.6),v(3.0,3.5,1.9),v(1,.3,-.3),.9,1.15,.655f,.705f,.78f,.98f,.74f,.95f,0,9),
        arc(v(1.0,.62,0),6,180,2.4,3.7,118,-122,.765f,.815f,.92f,1.15f,.86f,1.1f,0,11,false),
        arc(v(.8,1.55,0),9,-90,2.9,4.4,-132,128,.855f,.92f,1.02f,1.26f,.96f,1.2f,0,13,false)};
    static final Arc[] H4_ARCS={arc(v(1.0,.72,0),0,0,1.5,2.9,125,-115,.675f,.715f,.76f,.92f,.72f,.88f,0,7,false)};
    static Arc[] arcs(int combo){return switch(combo){case 1->H1_ARCS;case 2->H2_ARCS;case 3->H3_ARCS;default->H4_ARCS;};}

    /** A point of slash {@code s} at angle {@code deg}, {@code radius} out (the hit's frame). */
    static Vec3d point(Arc s,double deg,double radius){double t=Math.toRadians(deg);return s.centre().add(s.a().multiply(Math.cos(t)*radius)).add(s.b().multiply(Math.sin(t)*radius));}
    /** How far slash {@code s}'s head has swept on at {@code t} (0 not yet, 1 all the way). */
    static float head(Arc s,float t){float u=Ease.unit(t,s.t0(),s.t1());return s.even()?u:u*u*(3-2*u);}
    /** The band of a slash or a mark: its edges and the fraction along it (0 tail, 1 head). */
    record Swept(Vec3d[] inner,Vec3d[] outer,float[] along,int n){}
    /** Slash {@code s} as drawn at {@code t} by a hit at {@code feet} facing {@code yaw}: its whole path up to its head (null before it starts). */
    static Swept band(Vec3d feet,float yaw,Arc s,float t){
        float h=head(s,t);if(h<=.004f)return null;
        double span=(s.to()-s.from())*h;int n=Math.max(8,(int)Math.ceil(Math.abs(span)/5))+1;
        Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){
            double deg=s.from()+span*i/(n-1);
            in[i]=world(feet,yaw,point(s,deg,s.inner()));out[i]=world(feet,yaw,point(s,deg,s.outer()));along[i]=i/(float)(n-1);
        }
        return new Swept(in,out,along,n);
    }
    /** The band's thickness along it (0..1), as the shader shapes each style. */
    static double profile(int style,double x){
        return switch(style){
            case 0->Math.pow(Math.max(Math.sin(Math.PI*Math.pow(x,1.45)),0),.6);
            case 2->Math.pow(Math.max(Math.sin(Math.PI*x),0),.5);
            case 3->Math.pow(Math.max(Math.sin(Math.PI*x),0),.36);
            case 6->Math.pow(Math.max(Math.sin(Math.PI*Math.pow(x,.62)),0),.55);
            default->.9;
        };
    }
    /** How far across the band its bright line runs (0 the inner edge, 1 the outer), {@code prof} its thickness there. */
    static double lineAt(int style,double prof){return style==0||style==6?1-.29*prof:1-.03*prof;}

    /** Hit 3's thrust: a beam from this far behind the hand to this far ahead of the feet (blocks), lit over these times. */
    static final Vec3d BEAM_FROM=v(-1.6,1.3,.15),BEAM_TO=v(9.5,1.25,.05);
    static final float BEAM_ON=.265f,BEAM_OFF=.56f;
    static float beamLit(float t){return Ease.smooth(t,BEAM_ON,BEAM_ON+.035f)*(1-Ease.smooth(t,BEAM_OFF-.14f,BEAM_OFF));}
    /** The beam's ends at {@code t} (it runs out ahead from the point as the thrust drives in). */
    static Vec3d[] beam(Vec3d feet,float yaw,float t){
        double grow=Ease.out(t,BEAM_ON,BEAM_ON+.04f);
        Vec3d a=world(feet,yaw,BEAM_FROM),b=world(feet,yaw,BEAM_FROM.add(BEAM_TO.subtract(BEAM_FROM).multiply(.15+.85*grow)));
        return new Vec3d[]{a,b};
    }

    /** The thrown slash's height over the ground (its back edge; the leading edge a little higher) and when it is gone. */
    static final double WAVE_HEIGHT=.95,WAVE_LIFT=.14;
    static final float WAVE_INK0=1.0f,WAVE_INK1=1.32f,WAVE_EAT0=1.16f,WAVE_EAT1=1.48f;
    /** Ticks the thrown slash has flown at hit 4's second {@code t} (it stops where its flight ends). */
    static double flown(float t){return Math.max(0,Math.min(IsosangRules.WAVE_TICKS,(t-blow(4,0))*20));}
    /**
     * The thrown slash at hit 4's second {@code t} (null before it is thrown or once gone): a crescent lying level across
     * the way it went, tip to tip, its back edge inner, its leading edge outer, where the server's flight has it
     * (IsosangRules.waveFront, waveHalf, waveCurve), growing out of the low cut over its first frames.
     */
    static Swept wave(Vec3d feet,float yaw,float t){
        float thrown=blow(4,0);if(t<thrown||t>=WAVE_EAT1)return null;
        double ticks=flown(t),grow=Ease.out((float)((t-thrown)*20),0,1.6f);
        double front=IsosangRules.waveFront(ticks),half=IsosangRules.waveHalf(ticks)*(.6+.4*grow),curve=IsosangRules.waveCurve(ticks);
        int n=41;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){
            double u=-1+2.0*i/(n-1),side=half*u,edge=front-curve*u*u,back=edge-IsosangRules.WAVE_DEPTH*(.7+.3*grow);
            out[i]=world(feet,yaw,v(edge,WAVE_HEIGHT+WAVE_LIFT,side));in[i]=world(feet,yaw,v(back,WAVE_HEIGHT,side));along[i]=i/(float)(n-1);
        }
        return new Swept(in,out,along,n);
    }
    /**
     * When the thrown slash reaches a body {@code fw} ahead and {@code side} to the right of where it was thrown (hit 4's
     * second; at the throw for one right in front), or -1 if it never does (IsosangRules.waveReaches).
     */
    static float reaches(double fw,double side,double halfWidth){
        double ticks=IsosangRules.waveReaches(fw,side,1,halfWidth);
        return ticks<0?-1:blow(4,0)+(float)(ticks/20);
    }

    // ───────────────────────────── the ink pass ─────────────────────────────

    /**
     * The ink ribbons (KagenoFire's ink pass): every slash along its arc, drawn on as it sweeps, eaten from the tail as it
     * turns to ink; the thrust's beam; the thrown slash; and the ink on the ground (rings brushed round them, splashes).
     */
    @Override public void ink(Fx c){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        for(var s:arcs(combo)){
            if(t>=s.eat1())continue;
            var sw=band(feet,yaw,s,t);if(sw==null)continue;
            KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),Ease.smooth(t,s.eat0(),s.eat1()),Ease.smooth(t,s.ink0(),s.ink1()),Ease.smooth(t,s.t0(),s.t0()+.015f));
        }
        if(combo==3){
            float lit=beamLit(t);
            if(lit>0){
                var b=beam(feet,yaw,t);Vec3d axis=b[1].subtract(b[0]).normalize(),side=axis.crossProduct(UP).normalize(),up=side.crossProduct(axis).normalize();
                // two crossed bands (level and upright, fixed in the world): a line of light from every side
                strip(b[0],b[1],side,.34,2,15,0,0,lit);strip(b[0],b[1],up,.34,2,15,0,0,lit*.8f);
            }
        }
        if(combo==4){
            var sw=wave(feet,yaw,t);
            if(sw!=null)KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),3,7,Ease.smooth(t,WAVE_EAT0,WAVE_EAT1),Ease.smooth(t,WAVE_INK0,WAVE_INK1),Ease.smooth(t,blow(4,0)-.005f,blow(4,0)+.02f));
        }
        groundInk(c,t,combo,feet,yaw);
    }
    /** A straight band from a to b, {@code width} across along {@code side} (a unit vector). */
    static void strip(Vec3d a,Vec3d b,Vec3d side,double width,int style,int seed,float erase,float ink,float alpha){
        Vec3d h=side.multiply(width*.5);int n=9;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){float u=i/(float)(n-1);Vec3d p=a.add(b.subtract(a).multiply(u));in[i]=p.subtract(h);out[i]=p.add(h);along[i]=u;}
        KagenoFire.inkRibbon(in,out,along,n,style,seed,erase,ink,alpha);
    }
    /** A splash of ink lying on the ground at {@code p}: {@code size} across, stretched {@code stretch} times along {@code angle} (radians). */
    static void splat(Vec3d p,double size,double angle,double stretch,int seed,float dry,float alpha){
        if(alpha<=.01f||size<=.01)return;
        Vec3d a=new Vec3d(Math.cos(angle),0,Math.sin(angle)).multiply(size*stretch*.5),b=new Vec3d(-Math.sin(angle),0,Math.cos(angle)).multiply(size*.5);
        KagenoFire.inkRibbon(new Vec3d[]{p.subtract(a).subtract(b),p.add(a).subtract(b)},new Vec3d[]{p.subtract(a).add(b),p.add(a).add(b)},new float[]{0,1},2,5,seed,dry,0,alpha);
    }
    /**
     * A stroke of ink brushed on the ground round {@code o}: {@code radius} out, {@code width} across, from angle
     * {@code from} to {@code to} (degrees, the hit's frame), brushed on {@code drawn} of the way.
     */
    static void groundRing(Vec3d o,float yaw,double radius,double width,double from,double to,float drawn,int seed,float dry,float alpha){
        if(drawn<=.004f||alpha<=.01f)return;
        double span=(to-from)*drawn;int n=Math.max(8,(int)Math.ceil(Math.abs(span)/5))+1;
        Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){
            double deg=Math.toRadians(from+span*i/(n-1));Vec3d r=dir(yaw,v(Math.cos(deg),0,Math.sin(deg)));
            in[i]=o.add(r.multiply(radius-width*.5));out[i]=o.add(r.multiply(radius+width*.5));along[i]=i/(float)(n-1);
        }
        KagenoFire.inkRibbon(in,out,along,n,4,seed,dry,0,alpha);
    }
    /**
     * Ink left on the ground: hit 2's ring falls as a ring brushed round them and splashes round it; hit 3's low sweep
     * leaves splashes under it; hit 4's cut brushes a ring round it and its thrown slash splashes ink all along its way
     * (each where its back edge passed, streaked the way it flew). All dry away.
     */
    static void groundInk(Fx c,float t,int combo,Vec3d feet,float yaw){
        double floor=feet.y+.02;
        if(combo==2&&t>=blow(2,1)-.02f){
            float at=blow(2,1);
            Vec3d o=world(feet,yaw,v(.35,0,0));o=new Vec3d(o.x,floor,o.z);
            groundRing(o,yaw,3.15,.34,200,-150,Ease.out(t,at-.02f,at+.06f),3,Ease.smooth(t,1.05f,1.6f),.95f*(1-Ease.smooth(t,1.45f,1.7f)));
            groundRing(o,yaw,2.35,.18,40,-250,Ease.out(t,at,at+.08f),8,Ease.smooth(t,.95f,1.4f),.9f*(1-Ease.smooth(t,1.3f,1.5f)));
            for(int k=0;k<20;k++){
                int seed=k*29+311;float born=at+.12f*(float)Ease.hash(seed);if(t<born)continue;
                double az=Math.PI*2*(k+.6*Ease.hash(seed+1))/20,r=3.15+(Ease.hash(seed+2)-.45)*1.4;
                Vec3d p=o.add(dir(yaw,v(Math.cos(az)*r,0,Math.sin(az)*r))).add(0,.002*(k%5),0);
                float dry=Ease.smooth(t,1.1f+.25f*(float)Ease.hash(seed+3),1.6f+.25f*(float)Ease.hash(seed+4));
                splat(p,(.5+.7*Ease.hash(seed+5))*Ease.out(t,born,born+.1f),az+Math.PI/2+(Ease.hash(seed+6)-.5),1.2+1.6*Ease.hash(seed+7),seed&15,dry,.92f*(1-Ease.smooth(dry,.75f,1)));
            }
        }
        if(combo==3&&t>=.8f){
            var s=H3_ARCS[2];
            for(int k=0;k<16;k++){
                int seed=k*31+577;float born=.8f+.1f*(float)Ease.hash(seed);if(t<born)continue;
                double deg=s.from()+(s.to()-s.from())*(.08+.84*Ease.hash(seed+1)),rad=s.inner()+(s.outer()-s.inner())*(.2+.8*Ease.hash(seed+2));
                Vec3d p=world(feet,yaw,point(s,deg,rad));p=new Vec3d(p.x,floor+.002*(k%5),p.z);
                float dry=Ease.smooth(t,1.55f+.2f*(float)Ease.hash(seed+3),2.0f+.2f*(float)Ease.hash(seed+4));
                splat(p,(.45+.6*Ease.hash(seed+5))*Ease.out(t,born,born+.1f),Math.toRadians(deg-yaw)+(Ease.hash(seed+6)-.5),1.3+1.5*Ease.hash(seed+7),seed&15,dry,.9f*(1-Ease.smooth(dry,.75f,1)));
            }
        }
        if(combo==4&&t>=blow(4,0)-.01f){
            float at=blow(4,0);
            Vec3d o=world(feet,yaw,v(1.0,0,0));o=new Vec3d(o.x,floor,o.z);
            groundRing(o,yaw,2.7,.2,150,-165,Ease.out(t,at-.01f,at+.05f),12,Ease.smooth(t,1.4f,1.95f),.95f*(1-Ease.smooth(t,1.8f,2.05f)));
            groundRing(o,yaw,1.9,.12,-30,-250,Ease.out(t,at+.01f,at+.07f),14,Ease.smooth(t,1.25f,1.7f),.85f*(1-Ease.smooth(t,1.6f,1.8f)));
            float flyYaw=yaw;
            for(int k=0;k<38;k++){
                int seed=k*37+733;
                double fw=2+(IsosangRules.WAVE_RANGE-2.5)*Ease.hash(seed),u=(Ease.hash(seed+1)-.5)*2;
                // the slash's back edge passes over it: when its leading edge is DEPTH past it
                float when=reaches(fw+IsosangRules.WAVE_DEPTH*.8,0,0);if(when<0)when=at+.4f;
                double side=u*IsosangRules.waveHalf(Math.min(IsosangRules.WAVE_TICKS,(when-at)*20))*.85;
                float born=when+.03f*(float)Ease.hash(seed+2);if(t<born)continue;
                Vec3d p=world(feet,yaw,v(fw,0,side));p=new Vec3d(p.x,floor+.002*(k%5),p.z);
                float dry=Ease.smooth(t,1.95f+.3f*(float)Ease.hash(seed+3),2.45f+.3f*(float)Ease.hash(seed+4));
                double ang=Math.toRadians(flyYaw+90)+(Ease.hash(seed+5)-.5)*.6;
                splat(p,(.5+.9*Ease.hash(seed+6))*Ease.out(t,born,born+.12f),ang,1.4+2.4*Ease.hash(seed+7),seed&15,dry,.93f*(1-Ease.smooth(dry,.75f,1)));
            }
        }
    }

    // ───────────────────────────── light ─────────────────────────────

    /** Brightness of the blade's aura: lit for the cuts, flaring at each blow; the finisher lights it from the twirl. */
    static float glow(float t,int combo){
        float g=switch(combo){
            case 2->Ease.window(t,.06f,.5f,.04f,.12f);
            case 3->Ease.window(t,.2f,1.0f,.04f,.12f);
            case 4->Ease.window(t,.06f,.8f,.04f,.12f);
            default->Ease.window(t,.02f,.48f,.03f,.12f);
        };
        for(int i=0;i<IsosangRules.blows(combo);i++)g+=.8f*FxLib.kick(t,blow(combo,i),.07f);
        return g;
    }
    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        aura(c,m,t,combo);
        for(var s:arcs(combo)){var sw=band(feet,yaw,s,t);if(sw!=null&&t<s.eat1())edgeGlow(m,sw,s.style(),(1-Ease.smooth(t,s.ink0(),s.ink1()))*(1-Ease.smooth(t,s.eat0(),s.eat1())));}
        sparks(c,m,t,combo,feet,yaw);
        impacts(c,m,t,combo);
        if(combo==1)glint(c,m,t);
        if(combo==2)flare(c,m,t,feet,yaw);
        if(combo==3)beamLight(c,m,t,feet,yaw);
        if(combo==4){twirl(c,m,t);point(c,m,t);dash(c,m,t);waveLight(c,m,t,feet,yaw);}
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        drops(c,m,t,combo,feet,yaw);
        splashes(c,m,t,combo);
        if(combo==4)waveDrops(c,m,t,feet,yaw);
    }
    /** The blade's blue aura while it cuts, brightest as it strikes. */
    static void aura(Fx c,EffectMesh m,float t,int combo){
        float g=glow(t,combo);if(g<=.01f)return;
        var hb=bladeNow(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));float on=Math.min(1,g);
        double w=.06*(1+.5*Math.max(0,Math.min(1,g-1)));float a=Math.min(1.7f,g);
        m.softLine(guard,tip,w*14,AURA[0],a*.14f);m.softLine(guard,tip,w*6,AURA[1],a*.34f);m.softLine(guard,tip,w*2.6,AURA[1],a*.75f);
        m.line(guard,tip,w,AURA[2],a);m.line(guard,tip,w*.45,AURA[3],a);
        m.softDisc(tip,.2+.16*on,AURA[3],.8f*on);
    }
    /**
     * Each slash's light along its cutting line, as wide as the band is thick there: a broad blue haze and a thin white
     * line, so the effect layer blooms round it.
     */
    static void edgeGlow(EffectMesh m,Swept sw,int style,float a){
        if(a<=.01f)return;
        for(int i=0;i+1<sw.n();i++){
            double x=(sw.along()[i]+sw.along()[i+1])*.5,prof=profile(style,x);if(prof<.02)continue;
            double k=lineAt(style,prof);
            Vec3d p0=sw.inner()[i].add(sw.outer()[i].subtract(sw.inner()[i]).multiply(k)),p1=sw.inner()[i+1].add(sw.outer()[i+1].subtract(sw.inner()[i+1]).multiply(k));
            double width=sw.outer()[i].distanceTo(sw.inner()[i])*prof;
            m.softLine(p0,p1,width*1.6,BLUE,a*.22f*(float)prof);
            m.softLine(p0,p1,width*.45,AZURE,a*.45f*(float)prof);
            m.line(p0,p1,.045+.03*prof,WHITE,a*.85f*(float)prof);
        }
    }
    /** Hit 1's glint: the sword flashing into the hand, a white line along it and blue stars at the hand and the point. */
    static void glint(Fx c,EffectMesh m,float t){
        if(t>=.16f)return;
        float a=Ease.smooth(t,0,.025f)*(1-Ease.smooth(t,.08f,.16f));
        var hb=bladeNow(c);Vec3d hand=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));
        m.softLine(hand,tip,.5,AZURE,.35f*a);m.line(hand,tip,.08,WHITE,.9f*a);
        float s=Ease.smooth(t,.02f,.05f)*(1-Ease.smooth(t,.07f,.14f));
        m.softDisc(hand,.6*s+.1,BLUE,.6f*s);m.star(hand,.75*s+.1,PALE,s);m.star(hand,.4*s+.05,WHITE,s);
        m.softDisc(tip,.7*s+.1,BLUE,.6f*s);m.star(tip,.95*s+.1,PALE,s);m.star(tip,.5*s+.05,WHITE,s);
    }
    /**
     * Sparks thrown off every slash's cutting edge as it sweeps: small blue, pale and white motes, born where the edge
     * passed, flying on along the sweep and outwards, slowing and fading in a quarter of a second.
     */
    static void sparks(Fx c,EffectMesh m,float t,int combo,Vec3d feet,float yaw){
        int k0=0;
        for(var s:arcs(combo)){
            k0+=31;if(t<s.t0()||t>=s.t1()+.3f)continue;
            int n=s.style()==1?44:30;
            for(int k=0;k<n;k++){
                float born=s.t0()+(s.t1()-s.t0())*(k+.5f*(float)Ease.hash(k*13+k0))/n,age=t-born,life=.16f+.12f*(float)Ease.hash(k*17+k0);
                if(age<0||age>=life)continue;
                double deg=s.from()+(s.to()-s.from())*head(s,born),r=s.outer()*(.82+.25*Ease.hash(k*19+k0));
                Vec3d at=world(feet,yaw,point(s,deg,r)),next=world(feet,yaw,point(s,deg+Math.signum(s.to()-s.from())*4,r));
                Vec3d way=next.subtract(at);if(way.lengthSquared()<1e-8)continue;way=way.normalize();
                Vec3d out=at.subtract(world(feet,yaw,s.centre()));out=out.lengthSquared()<1e-8?UP:out.normalize();
                double speed=3+3*Ease.hash(k*23+k0),u=age/life;
                Vec3d vel=way.multiply(speed).add(out.multiply(speed*.5*(Ease.hash(k*29+k0)-.1)));
                Vec3d q=at.add(vel.multiply(life*(1-Math.pow(1-u,2))*.5)).add(0,-.6*age*age,0);
                float a=(float)(1-u)*Ease.smooth(age,0,.02f);int col=k%4==0?WHITE:k%4==1?PALE:AZURE;
                m.needle(q.subtract(vel.normalize().multiply(.2*(1-u)+.04)),q,.06,.3,col,a);
                m.softDisc(q,.06*(1-.5*u),col,.8f*a);
            }
        }
    }
    /** When the struck are struck by blow {@code i}: at the blow; the thrown slash as it reaches each (hit 4). */
    static float strikeAt(Fx c,int i,LivingEntity e){
        if(c.cast.combo()!=4)return blow(c.cast.combo(),i);
        Vec3d f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x),o=e.getPos().subtract(anchor(c));
        float at=reaches(o.dotProduct(f),o.dotProduct(right),e.getWidth()*.5);return at<0?blow(4,0):at;
    }
    /** Where the thrown slash struck each one, held from the first frame it reached them. */
    private static final Map<Long,Vec3d> REACHED=new LinkedHashMap<>();
    static Vec3d reachedAt(Fx c,LivingEntity e,float at){
        long key=c.cast.id().getMostSignificantBits()*31+e.getId();var p=REACHED.get(key);if(p!=null)return p;
        p=chest(c,e);if(c.sec<at)return p;
        REACHED.put(key,p);while(REACHED.size()>96)REACHED.remove(REACHED.keySet().iterator().next());
        return p;
    }
    /**
     * Every struck target, at every blow (the thrown slash: as it reaches them): a blue-white flash and star, a ring in
     * the plane across the cut and sparks thrown the way it went through.
     */
    static void impacts(Fx c,EffectMesh m,float t,int combo){
        for(int i=0;i<IsosangRules.blows(combo);i++){
            List<Object[]> where=new ArrayList<>();
            if(combo==4){for(var e:struck(c,0,anchor(c))){float at=strikeAt(c,0,e);where.add(new Object[]{reachedAt(c,e,at),at});}}
            else{float at=blow(combo,i);for(var p:hits(c,i,at))where.add(new Object[]{p,at});}
            for(var w:where){
                Vec3d p=(Vec3d)w[0];float at=(float)w[1];if(t<at-.01f||t>=at+.4f)continue;
                Vec3d way=combo==4?Vec3d.fromPolar(0,c.cast.yaw()):sweep(c,combo,i);
                Vec3d side=way.crossProduct(UP);if(side.lengthSquared()<1e-4)side=c.right;side=side.normalize();Vec3d up=side.crossProduct(way).normalize();
                float u=Ease.unit(t,at,at+.2f),a=Ease.smooth(t,at-.01f,at+.01f);boolean big=combo==4;double s=big?1.5:1.1;
                m.softDisc(p,1.4*s*(1-.4*u)+.25,BLUE,.5f*a*(1-u));m.softDisc(p,.75*s*(1-u)+.12,WHITE,a*(1-u));
                m.star(p,(1.5*s)*(1-.5*u)+.2,PALE,a*(1-u));m.star(p,.8*s*(1-u)+.1,WHITE,a*(1-u));
                FxLib.ringPlane(m,p,side,up,.25+1.2*s*Ease.out(t,at,at+.18f),.22,PALE,.85f*a*(1-Ease.unit(t,at,at+.18f)));
                float sp=a*(1-Ease.smooth(t,at+.08f,at+.32f));
                for(int k=0;k<(big?28:16);k++){
                    double ang=(Ease.hash(k*3+combo*17+i*5)-.5)*2.4,rise=(Ease.hash(k*5+combo*11+i)-.5)*1.6;
                    Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(up.multiply(rise)).normalize();
                    double len=(.7+1.3*Ease.hash(k*7+combo+i*3))*s*Ease.out(t,at,at+.14f);
                    Vec3d q=p.add(dir.multiply(len*.35));
                    m.needle(q,q.add(dir.multiply(len*.7)),.07,.3,k%3==0?WHITE:AZURE,sp);
                }
            }
        }
    }
    /** The way slash {@code i} of a hit moves at its blow (a unit vector; the hit's facing if none). */
    static Vec3d sweep(Fx c,int combo,int i){
        var arcs=arcs(combo);int j=combo==3?i-1:Math.min(i,arcs.length-1);
        if(combo==2||j<0||j>=arcs.length)return Vec3d.fromPolar(0,c.cast.yaw());
        var s=arcs[j];double deg=s.from()+(s.to()-s.from())*.5;
        Vec3d a=world(Vec3d.ZERO,c.cast.yaw(),point(s,deg,s.outer())),b=world(Vec3d.ZERO,c.cast.yaw(),point(s,deg+Math.signum(s.to()-s.from())*3,s.outer()));
        Vec3d w=b.subtract(a);return w.lengthSquared()<1e-8?Vec3d.fromPolar(0,c.cast.yaw()):w.normalize();
    }
    /** Hit 2's burst at its second blow: the ring flares out over the ground, a flash round them, rings racing out. */
    static void flare(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        float at=blow(2,1);if(t<at-.02f||t>=at+.45f)return;
        Vec3d o=world(feet,yaw,v(.35,.5,0));float a=Ease.smooth(t,at-.02f,at+.01f),u=Ease.unit(t,at,at+.35f);
        FxLib.ring(m,o,3.2+1.6*Ease.out(t,at,at+.35f),.5,AZURE,.8f*a*(1-u));
        FxLib.ring(m,o,3.4+.8*Ease.out(t,at,at+.2f),.22,WHITE,.85f*a*(1-Ease.unit(t,at,at+.2f)));
        FxLib.ring(m,o.add(0,-.4,0),2.6+2.4*Ease.out(t,at,at+.3f),.16,PALE,.55f*a*(1-Ease.unit(t,at,at+.3f)));
        m.softDisc(o,3.2*(1-u)+.5,BLUE,.18f*a*(1-u));
    }
    /** Hit 3's beam: its light along the whole line, a glint at its far end, rings at the point running on ahead. */
    static void beamLight(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        float a=beamLit(t);if(a<=0)return;
        var b=beam(feet,yaw,t);Vec3d axis=b[1].subtract(b[0]).normalize(),side=axis.crossProduct(UP).normalize();
        m.softLine(b[0],b[1],2.2,DEEP,.3f*a);m.softLine(b[0],b[1],.8,AZURE,.55f*a);
        m.needle(b[0],b[1],.16,.4,WHITE,.95f*a);
        m.softDisc(b[1],1.1*a,AZURE,.6f*a);m.star(b[1],1.3*a,PALE,a);m.star(b[1],.7*a,WHITE,a);
        var hb=blade(c,3,blow(3,0));Vec3d tip=hb[0].add(hb[1].multiply(TIP));
        for(int k=0;k<5;k++){
            float s=BEAM_ON+.02f*k,u=Ease.unit(t,s,s+.26f);if(t<s||u>=1)continue;
            Vec3d o=tip.add(axis.multiply(.5+1.4*k+1.4*Ease.out(t,s,s+.26f)));
            FxLib.ringPlane(m,o,side,side.crossProduct(axis).normalize(),.3+(.9+.4*k)*Ease.out(t,s,s+.22f),.18,k==0?WHITE:PALE,.85f*(1-u)*Ease.smooth(t,s,s+.015f));
        }
    }
    /** The finisher's twirl: a ring of pale light over the head along the point's path, gone as the aim begins. */
    static void twirl(Fx c,EffectMesh m,float t){
        if(t<.1f||t>=.42f)return;
        float from=Math.max(.1f,t-.16f),head=Math.min(t,.33f);if(head<=from+.004f)return;
        float a=.95f*(1-Ease.smooth(t,.3f,.42f));int n=22;Vec3d prev=null;
        for(int i=0;i<=n;i++){
            float s=from+(head-from)*i/n;var hb=blade(c,4,s);Vec3d p=hb[0].add(hb[1].multiply(TIP*1.08));
            if(prev!=null){float u=i/(float)n;m.softLine(prev,p,.45,CYAN,a*u*.55f);m.line(prev,p,.07,WHITE,a*u);}
            prev=p;
        }
    }
    /** The finisher's aim: a glint at the pointing hand and a blue light gathering on the blade. */
    static void point(Fx c,EffectMesh m,float t){
        if(t<.4f||t>=.62f)return;
        float a=Ease.smooth(t,.4f,.45f)*(1-Ease.smooth(t,.55f,.62f));
        Vec3d hand=c.pos.add(0,1.35,0).add(Vec3d.fromPolar(0,c.cast.yaw()).multiply(.95)).add(c.left.multiply(.25));
        m.star(hand,.45*a+.1,PALE,a);m.softDisc(hand,.35,AZURE,.6f*a);
        var hb=bladeNow(c);Vec3d tip=hb[0].add(hb[1].multiply(TIP));m.softDisc(tip,.5*a,BLUE,.7f*a);m.star(tip,.6*a,WHITE,a);
    }
    /** The finisher's lunge: streaks of air left behind along the way it went. */
    static void dash(Fx c,EffectMesh m,float t){
        if(t<.6f||t>=.8f)return;
        Vec3d f=Vec3d.fromPolar(0,c.cast.yaw());Vec3d side=f.crossProduct(UP).normalize();
        for(int k=0;k<10;k++){
            float born=.6f+.06f*(float)Ease.hash(k*7+3),age=t-born;if(age<0||age>=.18f)continue;
            double off=(Ease.hash(k*11)-.5)*1.4,h=.3+1.3*Ease.hash(k*13),len=1.2+1.4*Ease.hash(k*17);
            Vec3d a=at(c,.2+1.0*Ease.hash(k*5),h,off),b=a.subtract(f.multiply(len));
            m.needle(b,a,side,.06,.85,k%2==0?WHITE:PALE,.7f*(1-age/.18f));
        }
    }
    /** The thrown slash's light: a broad blue haze along its leading edge and a white line, brightest as it is thrown. */
    static void waveLight(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        var sw=wave(feet,yaw,t);if(sw==null)return;
        float lit=(1-Ease.smooth(t,WAVE_INK0,WAVE_INK1))*(1-Ease.smooth(t,WAVE_EAT0,WAVE_EAT1));
        edgeGlow(m,sw,3,1.2f*lit);
        float at=blow(4,0),fl=Ease.smooth(t,at-.01f,at+.01f)*(1-Ease.smooth(t,at+.03f,at+.22f));
        if(fl>0){Vec3d o=world(feet,yaw,v(1.6,.8,0));m.softDisc(o,3.2,BLUE,.35f*fl);m.softDisc(o,1.3,WHITE,.8f*fl);m.star(o,2.6*fl,PALE,fl);}
    }

    // ───────────────────────────── ink in the air ─────────────────────────────

    /**
     * Drops shed by every slash as it turns to ink: dark drops born along its band, flung outwards and on along the sweep,
     * stretched the way they fly, falling and shrinking away (flat in the world, not turned to the camera).
     */
    static void drops(Fx c,EffectMesh m,float t,int combo,Vec3d feet,float yaw){
        int k0=0;double floor=feet.y+.03;
        for(var s:arcs(combo)){
            k0+=53;if(t<s.ink0()||t>=s.ink1()+.8f)continue;
            int n=s.style()==1?56:36;
            for(int k=0;k<n;k++){
                float born=s.ink0()+(s.ink1()-s.ink0())*.65f*(float)Ease.hash(k*7+k0),age=t-born,life=.4f+.35f*(float)Ease.hash(k*11+k0);
                if(age<0||age>=life)continue;
                double deg=s.from()+(s.to()-s.from())*(.05+.9*Ease.hash(k*13+k0)),r=s.inner()+(s.outer()-s.inner())*(.4+.65*Ease.hash(k*17+k0));
                Vec3d at=world(feet,yaw,point(s,deg,r)),radial=at.subtract(world(feet,yaw,s.centre()));radial=radial.lengthSquared()<1e-8?UP:radial.normalize();
                Vec3d next=world(feet,yaw,point(s,deg+Math.signum(s.to()-s.from())*4,r)),way=next.subtract(at);way=way.lengthSquared()<1e-8?c.f:way.normalize();
                double speed=.8+1.6*Ease.hash(k*19+k0);
                Vec3d vel=radial.multiply(speed).add(way.multiply(speed*.7*(Ease.hash(k*23+k0)-.2))).add(0,.2+.6*Ease.hash(k*29+k0),0);
                double drag=1-Math.exp(-age*3.2);Vec3d q=at.add(vel.multiply(drag/3.2)).add(0,-3.4*age*age,0);
                if(q.y<floor)q=new Vec3d(q.x,floor,q.z);
                float u=age/life;double size=(.06+.12*Ease.hash(k*31+k0))*(1-.55*u)*Ease.out(age,0,.06f);
                Vec3d moving=vel.multiply(Math.exp(-age*3.2)).add(0,-6.8*age,0);
                drop(m,q,moving,size,k*37+k0,INK,.92f*(1-Ease.smooth(u,.55f,1)));
            }
        }
    }
    /** Ink splashed from every struck target at every blow (the thrown slash: as it reaches them). */
    static void splashes(Fx c,EffectMesh m,float t,int combo){
        for(int i=0;i<IsosangRules.blows(combo);i++){
            List<Object[]> where=new ArrayList<>();
            if(combo==4){for(var e:struck(c,0,anchor(c))){float at=strikeAt(c,0,e);where.add(new Object[]{reachedAt(c,e,at),at});}}
            else{float at=blow(combo,i);for(var p:hits(c,i,at))where.add(new Object[]{p,at});}
            int b=0;
            for(var w:where){
                Vec3d p=(Vec3d)w[0];float at=(float)w[1];if(t<at||t>=at+.9f)continue;
                Vec3d way=combo==4?Vec3d.fromPolar(0,c.cast.yaw()):sweep(c,combo,i);b++;
                Vec3d side=way.crossProduct(UP);side=side.lengthSquared()<1e-4?c.right:side.normalize();
                for(int k=0;k<(combo==4?22:12);k++){
                    int seed=k*41+i*7+combo*13+b*101;float age=t-at,life=.45f+.3f*(float)Ease.hash(seed);if(age>=life)continue;
                    double ang=(Ease.hash(seed+1)-.5)*2.4,rise=Ease.hash(seed+2)*1.2-.2;
                    Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(0,rise,0).normalize();
                    double speed=1.5+2.4*Ease.hash(seed+3),drag=1-Math.exp(-age*4);
                    Vec3d q=p.add(dir.multiply(speed*drag/4)).add(0,-2.8*age*age,0);float u=age/life;
                    drop(m,q,dir.multiply(speed*Math.exp(-age*4)).add(0,-5.6*age,0),(.07+.11*Ease.hash(seed+4))*(1-.5*u),seed,INK,.92f*(1-Ease.smooth(u,.5f,1)));
                }
            }
        }
    }
    /** Ink dripping off the thrown slash's back edge as it flies and dries, falling to the ground. */
    static void waveDrops(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        float at=blow(4,0);if(t<at+.05f||t>=WAVE_EAT1+.6f)return;
        double floor=feet.y+.03;
        for(int k=0;k<70;k++){
            int seed=k*43+977;float born=at+.08f+(WAVE_EAT1-at-.1f)*(float)Ease.hash(seed),age=t-born,life=.45f+.3f*(float)Ease.hash(seed+1);
            if(age<0||age>=life)continue;
            var sw=wave(feet,yaw,born);if(sw==null)continue;
            double x=(.1+.8*Ease.hash(seed+2))*(sw.n()-1);int i=Math.min(sw.n()-2,(int)x);double f=x-i;
            Vec3d back=sw.inner()[i].add(sw.inner()[i+1].subtract(sw.inner()[i]).multiply(f)),edge=sw.outer()[i].add(sw.outer()[i+1].subtract(sw.outer()[i]).multiply(f));
            Vec3d start=back.add(edge.subtract(back).multiply(.15*Ease.hash(seed+3)));
            Vec3d fly=Vec3d.fromPolar(0,yaw).multiply(1.6*(1-flown(born)/IsosangRules.WAVE_TICKS));
            Vec3d q=start.add(fly.multiply(age)).add(0,-4.6*age*age,0);if(q.y<floor)q=new Vec3d(q.x,floor,q.z);
            float u=age/life;
            drop(m,q,fly.add(0,-9.2*age,0),(.07+.12*Ease.hash(seed+4))*(1-.4*u),seed,INK,.92f*(1-Ease.smooth(u,.6f,1)));
        }
    }
    /**
     * One drop of ink at {@code q}: a round blot stretched the way it moves (in a plane holding that way, tumbling a
     * little, fixed in the world), a thin navy rim round a black middle.
     */
    static void drop(EffectMesh m,Vec3d q,Vec3d moving,double size,int seed,int rgb,float alpha){
        if(alpha<=.01f||size<=.005)return;
        double speed=moving.length();Vec3d along=speed<1e-4?UP:moving.multiply(1/speed);
        double tilt=Ease.hash(seed+8)*Math.PI;
        Vec3d any=Math.abs(along.y)<.9?UP:new Vec3d(1,0,0),s1=along.crossProduct(any).normalize(),s2=along.crossProduct(s1).normalize();
        Vec3d across=s1.multiply(Math.cos(tilt)).add(s2.multiply(Math.sin(tilt)));
        double stretch=1+Math.min(2.2,speed*.35);int sides=8;Vec3d[] p=new Vec3d[sides];
        for(int i=0;i<sides;i++){
            double a=Math.PI*2*i/sides,r=size*(.75+.35*Ease.hash(seed+i*5+1));
            p[i]=q.add(along.multiply(Math.cos(a)*r*stretch)).add(across.multiply(Math.sin(a)*r));
        }
        for(int i=1;i+1<sides;i++)m.triangle(p[0],p[i],p[i+1],rgb,alpha);
    }

    // ───────────────────────────── the effect layer ─────────────────────────────

    /** Lights: the blade's aura, every struck target's flash, the ring's burst, the beam, the thrown slash flying; afterglow cut short for the quick parts. */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        float g=Math.min(1.5f,glow(t,combo));
        if(g>.02f){var hb=bladeNow(c);f.light(hb[0].add(hb[1].multiply(.9)),AURA[1],6,1.2f*g);}
        if(combo==4){for(var e:struck(c,0,feet)){float at=strikeAt(c,0,e),k=FxLib.kick(t,at,.1f);if(k>.01f)f.light(reachedAt(c,e,at),0xcfe4ff,9,3f*k);}}
        else for(int i=0;i<IsosangRules.blows(combo);i++){
            float at=blow(combo,i),hit=FxLib.kick(t,at,.1f);
            if(hit>.01f)for(var p:hits(c,i,at))f.light(p,0xcfe4ff,8,2.5f*hit);
        }
        if(combo==1){float k=FxLib.kick(t,.29f,.12f);if(k>.01f)f.light(world(feet,yaw,v(.4,1.6,-.4)),0x7fb0ff,9,2f*k);}
        if(combo==2){
            float k=FxLib.kick(t,blow(2,0),.14f),fl=FxLib.kick(t,blow(2,1),.16f);
            if(k+fl>.01f)f.light(world(feet,yaw,v(.35,.6,0)),0x8cbcff,10,2.2f*k+2.6f*fl);
        }
        if(combo==3){
            float lit=beamLit(t);if(lit>0){var b=beam(feet,yaw,t);f.light(b[0].add(b[1]).multiply(.5),0x6aa8ff,10,2.6f*lit);}
            for(int i=1;i<5;i++){float k=FxLib.kick(t,blow(3,i),.1f);if(k>.01f)f.light(world(feet,yaw,v(1.4,1.4,0)),0x7fb0ff,8,1.6f*k);}
        }
        if(combo==4){
            var sw=wave(feet,yaw,t);
            if(sw!=null){
                float lit=(1-Ease.smooth(t,WAVE_INK0,WAVE_INK1))*(1-Ease.smooth(t,WAVE_EAT0,WAVE_EAT1));
                Vec3d mid=sw.outer()[sw.n()/2];f.light(mid,0x8cbcff,11,3.2f*lit);
                f.light(sw.outer()[sw.n()/6],0x6aa0ff,7,1.4f*lit);f.light(sw.outer()[sw.n()*5/6],0x6aa0ff,7,1.4f*lit);
            }
            float at=blow(4,0),k=FxLib.kick(t,at,.18f);
            if(k>.01f){
                Vec3d o=world(feet,yaw,v(1.6,.8,0));f.light(o,0xb8d8ff,14,4.4f*k);
                float near=(float)Math.max(0,Math.min(1,(22-c.camera.distanceTo(o))/10));
                if(near>0)f.shaft(o,0xb8d8ff,.3f*near*k);
            }
            // the lunge, the cut and the flight are quick: their afterglow is cut short so they do not smear
            if(t>=.6f&&t<1.12f)f.afterglow(.4f);
        }
        if(combo==1&&t>=.24f&&t<.4f)f.afterglow(.42f);
        if(combo==2&&t>=.09f&&t<.2f)f.afterglow(.45f);
        if(combo==3&&(t>=.28f&&t<.32f||t>=.44f&&t<.52f||t>=.65f&&t<.93f))f.afterglow(.48f);
    }
    /** The air bends along each slash as it sweeps, along the thrown slash as it flies, and in a ring round the ring's burst. */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        for(var s:arcs(combo)){
            var sw=band(feet,yaw,s,t);if(sw==null||t>=s.t1()+.18f)continue;
            d.ribbon(sw.inner(),sw.outer(),sw.n(),.55f*Ease.smooth(t,s.t0(),s.t0()+.03f)*(1-Ease.smooth(t,s.t1(),s.t1()+.18f)));
        }
        if(combo==4){
            var sw=wave(feet,yaw,t);float at=blow(4,0);
            if(sw!=null&&t<at+.5f)d.ribbon(sw.inner(),sw.outer(),sw.n(),.6f*Ease.smooth(t,at,at+.03f)*(1-Ease.smooth(t,at+.3f,at+.5f)));
        }
        if(combo==2){
            float at=blow(2,1);
            if(t>=at-.01f&&t<at+.35f){Vec3d o=world(feet,yaw,v(.35,.5,0));d.ring(o,c.right,Vec3d.fromPolar(0,yaw),3.2+1.6*Ease.out(t,at,at+.35f),.9,.4f*Ease.smooth(t,at-.01f,at+.015f)*(1-Ease.unit(t,at,at+.35f)));}
        }
    }

    // ───────────────────────────── camera ─────────────────────────────

    /** Shake as the blows land, harder for the thrust, the cut down from overhead and the thrown slash; each comes in over two frames. */
    @Override public float shake(StandClient.Cast cast,float sec){
        int combo=cast.combo();float v=0;
        for(int i=0;i<IsosangRules.blows(combo);i++){
            float at=blow(combo,i),amp=combo==4?2f:combo==3?(i==0?.9f:i==1?.8f:i==4?.7f:.45f):combo==2?(i==0?.6f:.8f):.9f;
            v=Math.max(v,amp*bump(sec,at,combo==4?.14f:.08f));
        }
        return v;
    }
    static float bump(float sec,float at,float tau){return sec<at-.02f?0:Ease.smooth(sec,at-.02f,at+.015f)*(float)Math.exp(-Math.max(0,sec-at-.015f)/tau);}
    /** Speed afterimages follow the sword's point and the body (the spin, the cuts, the lunge). */
    @Override public float afterimages(float sec){return -1;}
    /**
     * How far the caster's own camera draws back for the finisher (blocks, as in the user's video: it pulls back over the
     * crouch and the lunge to show the slash flying, holds while it dries into ink and comes back in), 0 for the others.
     */
    public static final double PULL=2.4;
    public static double pull(int combo,float sec){
        if(combo!=4)return 0;
        return PULL*Ease.smooth(sec,.55f,.88f)*(1-Ease.smooth(sec,1.3f,1.8f));
    }
}
