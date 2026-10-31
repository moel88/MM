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
import dev.sangwoo.celestialfall.client.stand.StandPunchFx;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
 * Remade once more from the user's video from above (2.78.0): every sweep goes round to the right (clockwise seen from
 * above), the bands thin at their tails and thick and inked at their heads, bigger, white while fresh, blue as they age
 * and thinning to a line, pools of ink under their low parts.
 * <ol>
 * <li>참월(斬月): the sword flashing into the hand low at the left with a glint, raised and coiled back over the left
 * shoulder, a whole spin to the right cutting a crescent from the front-left across the front, round the right and behind
 * to the back-left (leaning up to the front-right, thin at its tail and thick with ink at its end), a thin cut rising
 * back from the low left to the high right, held up there.</li>
 * <li>윤월(輪月): a wind-up to the left, a crouch and a whole spin to the right low over the ground: a wide ring round
 * them from the front, white as it is drawn, then blue, the ground inside it darkening and a pool of ink gathering under
 * its first quarter as it bursts into ink and thins away.</li>
 * <li>관월(貫月): a flurry of spins to the right: a ring round them leaning up to the front, the long cut rising forward
 * out of it (a beam of light climbing far through the foe), the sword drawn back across to the left, a second ring, a
 * third (crossing the beam in an X), a thin level arc at the chest and a fourth ring, the biggest; each ring ending thick
 * and inked at the back-left, the ground there black with ink by the end; the sword held out to the left at the end.</li>
 * <li>낙월(落月): a turn with the sword twirled overhead (a broad band of pale light left hanging), the free hand pointed
 * at the foe, a crouch, a lunge and a low cut that throws a giant crescent of a slash flying ahead, leaning back from its
 * leading edge, widening as it drifts out (IsosangRules' flight: the server strikes as it passes); a ring of ink on the
 * ground round the cut, rays of light fanning out behind the slash, ink dripping off its back edge and thrown up out of
 * whom it strikes. Its caster's own camera draws back for it.</li>
 * </ol>
 * The slashes are explicit crescents in the hit's frame ({@link Arc}), each sweeping on in time with the blade, drawn by
 * KagenoFire's ink ribbons (shader mode 16) as the user's videos show them (2.77.3: "원본은 바깥쪽이 밝은 파랑이고 안쪽이
 * 검정이야"): a white-hot cutting edge outermost in bright blue light, the band deepening inward through royal blue to
 * black brush ink where it is thick; as they dry the ink spreads out, the band thins toward its rim, blots open and they
 * break up into flakes and drops of ink that fly off and fall, staining the ground softly for a moment, and motes of
 * light. On the effect layer the light burns past white and blooms. Since 2.77.5 they are decorated with the moon's light
 * and its stars, as the sword is and as the hits are named: starlight on the slashes (over the ink, {@link #overlay}), a
 * glint racing at each one's head, a crescent moon at the heart of every blow's burst, moon dust as they break up, a
 * gleam on the ink, and a moon riding the thrown slash. Every effect is a function of the swing's clock, so it moves at
 * the render frame rate.
 */
public final class IsosangSlash implements Fx.Skill {
    /** Ink, navy, deep blue, blue, azure, pale blue, white; the twirl's pale cyan. */
    static final int INK=0x04060f,NAVY=0x0b1446,DEEP=0x1530b0,BLUE=0x2a5cff,AZURE=0x58a0ff,PALE=0xbfe2ff,WHITE=0xffffff,CYAN=0x9fe8ff;
    /** The starlight's lilac (the sword's lightning), moonlight, and the gleam of it on a drop of ink (2.77.5). */
    static final int LILAC=0xd6caff,MOON=0xf4f1ff,SHEEN=0x22409a;
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
    /** The player model's hips: this far to each side of the torso's pivot and this far below it; a leg's length. */
    static final double HIP_SIDE=.12,HIP_DOWN=.3,LEG=.75;
    /**
     * The pose with its feet on the ground (2.77.3: the legs are rigid, so a body brought down went into the ground with
     * them): each leg reaching from its hip out along the ground toward {@code rAz} and {@code lAz} (degrees in the hit's
     * frame, right +; NaN keeps that leg as it is) just as far as it must for the foot to touch the ground, straight down
     * when the hip stands as high as a leg is long. A crouch is a wide stance, a lunge a long one.
     */
    static R6.Pose planted(R6.Pose p,double rAz,double lAz){
        float k=(float)Math.PI/180;
        var q=new Quaternionf().rotationAxis(p.yaw*k,0,1,0).rotateAxis(p.pitch*k,1,0,0).rotateAxis(p.roll*k,0,0,1);
        R6.Pose o=p.copy();
        if(!Double.isNaN(rAz))o.rLeg=reach(q,p,-HIP_SIDE,rAz);
        if(!Double.isNaN(lAz))o.lLeg=reach(q,p,HIP_SIDE,lAz);
        return o;
    }
    /** A leg from its hip ({@code xLeft} left of the pivot) to where it touches the ground toward {@code az}, in the torso's frame. */
    private static R6.Dir reach(Quaternionf q,R6.Pose p,double xLeft,double az){
        // (KagenoClips' frame with the facing along +z: the character's left is +x)
        var hip=q.transform(new Vector3f((float)xLeft,(float)-HIP_DOWN,0)).add((float)-p.rt,(float)(p.up+dev.sangwoo.celestialfall.client.kageno.KagenoClips.PIVOT),(float)p.fw);
        double h=Math.max(0,hip.y),out=Math.sqrt(Math.max(0,LEG*LEG-h*h)),a=Math.toRadians(az);
        var leg=new Vector3f((float)(-Math.sin(a)*out),(float)-Math.min(h,LEG),(float)(Math.cos(a)*out));
        q.conjugate(new Quaternionf()).transform(leg);
        return new R6.Dir(-leg.x,leg.y,leg.z).norm();
    }
    /**
     * A frame of a hit with its feet kept out of the ground: its keys plant them ({@link #planted}), but in between, the
     * legs turning from one key's way to the next's while the body moves would put a foot into the ground for a moment;
     * such a leg reaches out along the ground the way it points (a leg hanging straight down: out to its own side), just
     * far enough to stand on it, the more the deeper the foot would have gone (all the way from 3 cm), so the leg never
     * jumps. A raised leg is left as it is.
     */
    static R6.Frame grounded(R6.Frame f){
        var p=R6.poseOf(f);float k=(float)Math.PI/180;
        var q=new Quaternionf().rotationAxis(p.yaw*k,0,1,0).rotateAxis(p.pitch*k,1,0,0).rotateAxis(p.roll*k,0,0,1);
        R6.Dir r=ground(q,p,-HIP_SIDE,p.rLeg,1),l=ground(q,p,HIP_SIDE,p.lLeg,-1);
        return r==p.rLeg&&l==p.lLeg?f:R6.legs(f,r,l);
    }
    static final double SINK=.03;
    private static R6.Dir ground(Quaternionf q,R6.Pose p,double xLeft,R6.Dir leg,int side){
        var hip=q.transform(new Vector3f((float)xLeft,(float)-HIP_DOWN,0)).add((float)-p.rt,(float)(p.up+dev.sangwoo.celestialfall.client.kageno.KagenoClips.PIVOT),(float)p.fw);
        var w=q.transform(new Vector3f((float)-leg.rt(),(float)leg.up(),(float)leg.fw()));
        double depth=-(hip.y+LEG*w.y);if(depth<=0)return leg;
        // (right +: the world's -x; a little out to its own side so a leg hanging straight down has a way to go)
        R6.Dir planted=reach(q,p,xLeft,Math.toDegrees(Math.atan2(-w.x+.15*side,w.z)));
        double u=Math.min(1,depth/SINK),b=u*u*(3-2*u);
        return new R6.Dir(leg.rt()+(planted.rt()-leg.rt())*b,leg.up()+(planted.up()-leg.up())*b,leg.fw()+(planted.fw()-leg.fw())*b).norm();
    }
    /** How high each foot (right, left) of frame {@code f} stands over the ground (blocks; below it, negative). */
    static double[] feet(R6.Frame f){
        var p=R6.poseOf(f);float k=(float)Math.PI/180;
        var q=new Quaternionf().rotationAxis(p.yaw*k,0,1,0).rotateAxis(p.pitch*k,1,0,0).rotateAxis(p.roll*k,0,0,1);
        double[] out=new double[2];int i=0;
        for(var leg:new R6.Dir[]{p.rLeg,p.lLeg}){
            double xLeft=i==0?-HIP_SIDE:HIP_SIDE;
            var hip=q.transform(new Vector3f((float)xLeft,(float)-HIP_DOWN,0)).add(0,(float)(p.up+dev.sangwoo.celestialfall.client.kageno.KagenoClips.PIVOT),0);
            out[i++]=hip.y+LEG*q.transform(new Vector3f((float)-leg.rt(),(float)leg.up(),(float)leg.fw())).y;
        }
        return out;
    }
    /** {@link #w}, its feet planted toward {@code rAz} and {@code lAz} ({@link #planted}). */
    static R6.Pose w(double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double rAz,double lAz){
        return planted(R6.world(fw,up,yaw,pitch,roll,rArm,blade,lArm,d(-90,0),d(-90,0)),rAz,lAz);
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
    // (2.79.0, the user's videos from above and from behind: the great cut is a whole spin to the right on a steeply
    // leaning plane, low by the ground behind the left and high above a target ahead to the right; its crescent sweeps
    // clockwise from the front-right round the right, behind and the left, and rises from the ground up across the front
    // to end thick and inked high at the front-right. 2.78.0 ended it low behind the left and added a second thin cut
    // rising back; the videos have one cut.)
    /** The sword flashed into the hand, held out low to the left, glinting. */
    static final R6.Pose H1_FLASH=w(.02,-.02,8,0,0,d(-40,-30),d(-20,-45),d(-80,12),-20,-160);
    static final R6.Pose H1_GLINT=w(.04,-.04,10,0,1,d(-28,-36),d(-12,-52),d(-78,14),-22,-160);
    /** Raised up to the right, then overhead. */
    static final R6.Pose H1_TURN=w(.1,-.08,-30,4,-2,d(20,50),d(40,56),d(-60,-50),30,-156);
    static final R6.Pose H1_RAISE=w(.16,-.05,-10,-6,-2,d(70,20),d(60,-20),d(-50,-60),28,-158);
    /** Down in a coil, turned to the left, the sword wound back over the left shoulder (a moving hold). */
    static final R6.Pose H1_COIL=w(.24,-.3,50,14,4,d(30,-110),d(10,-150),d(-30,80),-20,-170);
    static final R6.Pose H1_LOAD=w(.26,-.32,55,15,4,d(28,-116),d(8,-156),d(-30,84),-20,-170);
    /**
     * The spin cut (body-space keys: the turn is the root's yaw, to the right): the blade held across the body to the
     * left, high as it passes the front-right and low behind, so its point goes round them in a crescent that leans
     * down to the back-left.
     */
    static R6.Pose cut1(float yaw,double elev,double up){return planted(R6.pose().at(.36,up,0).body(yaw,10,-4).head(0,-8,0).rArm(elev*.7-8,-5).blade(elev,20).lArm(-30,-110).rLeg(-70,20).lLeg(-30,-30),24,Double.NaN);}
    /** Held up there (a whole turn on, 22° to the right): where the second hit begins. */
    static final R6.Pose H1_HOLD=w(.42,-.1,-382,-2,0,d(46,50),d(54,54),d(-58,-100),22,-166);
    static final R6.Pose H1_LOWER=w(.24,-.05,-372,2,0,d(-20,60),d(18,66),d(-70,-50),12,-170);
    static final Anim H1=Anim.joints(
        k(0,STANCE),
        k(.03,H1_FLASH),
        k(.07,H1_GLINT),
        k(.1,H1_TURN),
        k(.14,H1_RAISE),
        k(.17,H1_COIL),
        k(.19,H1_LOAD),
        // crouched, turning into the spin, the sword brought up on end (the video's 0.85 s)
        k(.23f,cut1(25,70,-.32)),
        // the spin cut (2.79.0), fast out of the coil and slowing, the blade following the leaning plane: high at the
        // front-right, down behind the right, by the ground behind the left, and rising up across the front (the blow)
        // to end high at the front-right (the yaw is 20° left of the arc's angle: the blade leads the body by 20°)
        k(.25,cut1(-10,43,-.3),Anim.Ease.IN),
        k(.265f,cut1(-152,-8,-.31),Anim.Ease.LINEAR),
        k(.272f,cut1(-206,-15,-.32),Anim.Ease.LINEAR),
        k(.28f,cut1(-259,-8,-.32),Anim.Ease.LINEAR),
        k(blow(1,0),cut1(-350,48,-.3),Anim.Ease.LINEAR),
        k(.32f,cut1(-380,55,-.25),Anim.Ease.OUT),
        k(.38f,H1_HOLD),
        k(step(1),H1_HOLD),
        k(.72,H1_LOWER),
        k(.9,STANCE.body(-360,0,0))).stops(Anim.stop(blow(1,0),.03,.05)).after(IsosangSlash::grounded);

    // ───────────────────────────── 2 윤월 ─────────────────────────────
    // Body-space keys while they turn: the turn is the root's yaw, the arm and the blade ride with it. (2.78.0, the user's
    // video from above: the spin is to the right, the ring drawn clockwise from the front; it went round to the left
    // until 2.77.7.) The yaws carry on from hit 1's whole turn (H1_HOLD: -382).
    /** Winding up to the left, going down, the sword trailing behind on the right. */
    static final R6.Pose H2_TURN=planted(R6.pose().at(.38,-.18,0).body(-322,8,4).head(6,-6,0).rArm(-34,100).blade(-14,120).lArm(-46,-56).rLeg(-72,24).lLeg(-80,-22),150,-40);
    /** Crouched, coiled to the left, the blade low out at the right-front. */
    static final R6.Pose H2_LOW=planted(R6.pose().at(.38,-.28,0).body(-272,16,6).head(0,-10,0).rArm(-36,100).blade(-6,116).lArm(-52,-74).rLeg(-66,30).lLeg(-84,-30),160,-30);
    /** The whole spin to the right on the right foot, the left knee drawn up: the blade held out low at the right, sweeping round over the ground. */
    static R6.Pose spin(float yaw){return planted(R6.pose().at(.38,-.3,0).body(yaw,14,0).head(0,-8,0).rArm(-32,88).blade(-6,94).lArm(-38,-96).rLeg(-68,28).lLeg(-22,-26),165,Double.NaN);}
    /** Out of the spin, rising; the blade snapped out down to the right as the ring bursts into ink. */
    static final R6.Pose H2_RISE=planted(R6.pose().at(.36,-.2,0).body(-628,8,0).head(0,-6,0).rArm(-40,74).blade(-10,92).lArm(-60,-60).rLeg(-76,24).lLeg(-82,-24),165,-20);
    static final R6.Pose H2_FLICK=planted(R6.pose().at(.35,-.14,0).body(-640,6,0).head(0,-4,0).rArm(-34,84).blade(-18,104).lArm(-72,-40).rLeg(-82,14).lLeg(-86,-14),160,-20);
    static final R6.Pose H2_SET=planted(R6.pose().at(.34,-.1,0).body(-646,3,0).head(0,-4,0).rArm(-44,70).blade(-18,86).lArm(-74,-34).rLeg(-84,12).lLeg(-86,-12),150,-20);
    /** Turned back to the front, the sword low at the right: where the third hit begins (yaw: -716, or 4 for the next clip). */
    static R6.Pose h2Back(float yaw){return w(.34,-.08,yaw,4,0,d(-50,30),d(-18,40),d(-72,-20),20,-160);}
    static final Anim H2=Anim.joints(
        k(0,H1_HOLD),
        k(.05,H2_TURN),
        k(.085,H2_LOW),
        // the spin: an even speed to the right from the front, round the right, behind (the blow) and the left, to the front
        k(.1,spin(-262),Anim.Ease.IN),
        k(.115,spin(-342),Anim.Ease.LINEAR),
        k(.13,spin(-422),Anim.Ease.LINEAR),
        k(blow(2,0),spin(-502),Anim.Ease.LINEAR),
        k(.165,spin(-577),Anim.Ease.LINEAR),
        k(.19,spin(-614),Anim.Ease.OUT),
        k(.24,H2_RISE),
        k(blow(2,1),H2_FLICK,Anim.Ease.OUT),
        k(.38,H2_SET),
        k(step(2),h2Back(-716)),
        // two whole turns on: the stance faces the same way as the start
        k(.9,STANCE.body(-720,0,0))).stops(Anim.stop(blow(2,0),.025,.04),Anim.stop(blow(2,1),.03,.05)).after(IsosangSlash::grounded);

    // ───────────────────────────── 3 관월 ─────────────────────────────
    // (2.78.0, the user's video from above: hit 3 is a flurry of spins to the right. A ring round them first, leaning up to
    // the front; out of it the long cut rising forward, low behind to high ahead, that pierces the foe (the beam); the
    // sword drawn back across to the left; a second ring, a third, a thin wide arc level at the chest and a fourth, the
    // biggest, each ending thick and inked at the back-left; all clockwise seen from above. The yaws run on to the right
    // through five whole turns. 2.77.7 had one ring to the left, a cut down, an X and one rising cut.)
    /** Wound up to the left, the sword drawn low across to the left-front. */
    static final R6.Pose H3_WIND1=w(.4,-.22,44,10,2,d(-30,-70),d(-24,-96),d(-20,60),-10,-168);
    /**
     * A ring sweep (body-space keys: the turn is the root's yaw, to the right): the blade held across the body to the
     * left, its elevation following its ring's lean, so its point goes round them with the crescent.
     */
    static R6.Pose whirl3(float yaw,double elev,double up){return planted(R6.pose().at(.5,up,0).body(yaw,8,-3).head(0,-8,0).rArm(elev*.7-6,-20).blade(elev,-32).lArm(-34,-120).rLeg(-72,22).lLeg(-30,-28),22,Double.NaN);}
    /** The fourth ring's sweep: the blade out at the right-front. */
    static R6.Pose whirl4(float yaw,double elev,double up){return planted(R6.pose().at(.5,up,0).body(yaw,10,-4).head(0,-8,0).rArm(elev*.7-6,50).blade(elev,60).lArm(-36,-110).rLeg(-74,22).lLeg(-32,-26),22,Double.NaN);}
    /** The thin level arc: the blade held out level at the right, at the chest. */
    static R6.Pose level3(float yaw,double elev){return planted(R6.pose().at(.5,-.26,0).body(yaw,8,-2).head(0,-6,0).rArm(elev*.7-4,-10).blade(elev,-20).lArm(-40,-100).rLeg(-78,18).lLeg(-40,-24),20,Double.NaN);}
    /**
     * The long cut rising forward out of the first ring (the beam): from behind, low, the sword shoulder driven through in
     * a long lunge, the free arm thrown back, the point ahead and climbing with the beam.
     */
    static final R6.Pose H3_BEAM1=w(.9,-.26,-250,12,2,d(-14,160),d(-10,170),d(-30,-60),20,-166);
    static final R6.Pose H3_THRUST=w(1.0,-.34,-318,18,3,d(5,-6),d(12,-2),d(-24,-172),12,-174);
    static final R6.Pose H3_HELD=w(1.02,-.34,-320,18,3,d(6,-5),d(13,-2),d(-26,-174),12,-174);
    /** The sword drawn back level across to the left before the second ring. */
    static final R6.Pose H3_DRAW2=w(.9,-.3,-290,10,-2,d(-10,-100),d(0,-110),d(-10,30),16,-168);
    /** Between the rings: the sword drawn high across to the left (the third), and held level at the right (the fourth). */
    static final R6.Pose H3_WIND3=w(.6,-.26,-690,10,-2,d(10,-80),d(20,-100),d(-20,40),14,-170);
    /** The end: the sword held out to the left, the free hand pointed ahead (as the video leaves it). */
    static final R6.Pose H3_OUT=w(.84,-.14,-1760,6,0,d(-8,-80),d(0,-100),d(-10,20),18,-166);
    static final Anim H3=Anim.joints(
        k(0,h2Back(4)),
        k(.10,H3_WIND1),
        // the first ring, fast out of the wind-up and slowing: the front-left, the right-front, the right, the right behind
        k(.18,whirl3(28,22,-.3),Anim.Ease.IN),
        k(.195,whirl3(-64,30,-.31),Anim.Ease.LINEAR),
        k(.21,whirl3(-129,14,-.32),Anim.Ease.LINEAR),
        k(.24,whirl3(-182,-4,-.32),Anim.Ease.OUT),
        // the long cut rising forward: from low behind up and through in a lunge (the blow), the point climbing
        k(.27,H3_BEAM1),
        k(blow(3,0),H3_THRUST,Anim.Ease.IN),
        k(.33,H3_HELD,Anim.Ease.OUT),
        k(.38,H3_DRAW2),
        // the second ring: the front-left, the front (the blow), the right, behind, the back-left
        k(.49,whirl3(-362,-4,-.3),Anim.Ease.IN),
        k(.505,whirl3(-432,10,-.3),Anim.Ease.LINEAR),
        k(.525,whirl3(-522,7,-.31),Anim.Ease.LINEAR),
        k(.54,whirl3(-582,2,-.32),Anim.Ease.LINEAR),
        k(.56,whirl3(-642,-6,-.32),Anim.Ease.OUT),
        k(.62,H3_WIND3),
        // the third ring, leaning up to the front-left
        k(.69,whirl3(-712,20,-.28),Anim.Ease.IN),
        k(.705,whirl3(-772,10,-.3),Anim.Ease.LINEAR),
        k(.725,whirl3(-872,-9,-.31),Anim.Ease.LINEAR),
        k(.745,whirl3(-952,-8,-.31),Anim.Ease.LINEAR),
        k(.76,whirl3(-1002,8,-.3),Anim.Ease.OUT),
        // the thin level arc at the chest: from the left, across the front (the blow), to the right behind
        k(.79,level3(-1010,8),Anim.Ease.IN),
        k(.805,level3(-1080,7),Anim.Ease.LINEAR),
        k(.83,level3(-1220,6),Anim.Ease.OUT),
        // the fourth ring, the biggest, leaning up to the front-right: the front (the blow), the right, behind, the back-left
        k(.89,whirl4(-1360,12,-.32),Anim.Ease.IN),
        k(.905,whirl4(-1425,16,-.33),Anim.Ease.LINEAR),
        k(.925,whirl4(-1515,0,-.34),Anim.Ease.LINEAR),
        k(.945,whirl4(-1590,-12,-.34),Anim.Ease.LINEAR),
        k(.97,whirl4(-1640,-10,-.34),Anim.Ease.OUT),
        k(1.05,H3_OUT),
        k(1.4,STANCE.body(-1800,0,0))).stops(Anim.stop(blow(3,0),.035,.03),Anim.stop(blow(3,1),.03,.04),Anim.stop(blow(3,4),.04,.06)).after(IsosangSlash::grounded);

    // ───────────────────────────── 4 낙월 ─────────────────────────────
    /** Turning right, the sword raised overhead. */
    static final R6.Pose H4_UP=w(.1,-.03,-52,-4,0,d(64,-20),d(80,-60),d(-70,30),30,-150);
    /** The twirl: the arm up over the head, the blade level, turning round it (body-space), the body still turning right. */
    static R6.Pose twirl(double az,float yaw){return planted(R6.pose().at(.12,-.03,0).body(yaw,-4,0).head(0,-10,0).rArm(80,16).blade(10,az).lArm(-60,-40).rLeg(-88,6).lLeg(-88,-6),30,-150);}
    /** Aimed: side-on, the free hand pointed at the foe, the sword held level behind at the chest. */
    static final R6.Pose H4_POINT=w(.14,-.2,-72,4,-2,d(-20,140),d(0,176),d(6,-4),26,-160);
    static final R6.Pose H4_POINT2=w(.14,-.22,-74,6,-2,d(-22,142),d(0,178),d(4,-3),26,-160);
    /** Down in a crouch; the lunge, the blade trailing low behind. */
    static final R6.Pose H4_CROUCH=w(.2,-.34,-62,20,-3,d(-40,150),d(-6,172),d(-10,-20),24,-164);
    static final R6.Pose H4_DASH=w(1.03,-.34,-50,28,0,d(-36,146),d(0,170),d(-20,-40),18,-172);
    /** The low cut that throws the slash: from the right behind round the front, low, to the left. */
    static final R6.Pose H4_DRAW=w(1.06,-.34,-30,22,0,d(-18,96),d(-4,120),d(-24,-70),18,-172);
    static final R6.Pose H4_SWING=w(1.07,-.34,-8,21,2,d(-16,50),d(-3,60),d(-24,-90),18,-172);
    static final R6.Pose H4_CUT=w(1.08,-.34,12,20,4,d(-16,6),d(-3,8),d(-26,-110),18,-172);
    static final R6.Pose H4_ON=w(1.09,-.35,44,20,6,d(-18,-64),d(-4,-74),d(-26,-160),18,-172);
    /** Followed through behind the left, low, the free arm thrown back, watching the slash fly. */
    static final R6.Pose H4_END=w(1.09,-.36,66,20,7,d(-28,-100),d(-3,-126),d(-18,150),18,-172);
    static final R6.Pose H4_HOLD=w(1.08,-.35,64,19,6,d(-30,-98),d(-8,-124),d(-20,148),18,-172);
    /** Up on the feet, the sword raised upright at the right side, and lowered. */
    static final R6.Pose H4_RISE=w(.66,-.1,-10,0,0,d(10,70),d(80,70),d(-70,-20),16,-164);
    static final R6.Pose H4_LOWER=w(.28,-.05,-6,2,0,d(-30,40),d(30,30),d(-80,-12),10,-170);
    static final Anim H4=Anim.joints(
        k(0,STANCE),
        k(.07,H4_UP),
        // the twirl: a whole turn of the blade over the head, a quarter every 0.05 s, as the body turns on right
        k(.12,twirl(0,-90)),
        k(.17,twirl(90,-110),Anim.Ease.LINEAR),
        k(.22,twirl(180,-126),Anim.Ease.LINEAR),
        k(.27,twirl(270,-136),Anim.Ease.LINEAR),
        k(.32,twirl(360,-142)),
        // aimed, down, the lunge, and the low cut round the front that throws the slash (2.78.0: a little quicker to the
        // aim and the crouch, as the video from above has them)
        k(.4,H4_POINT),
        k(.52,H4_POINT2),
        k(.58,H4_CROUCH),
        k(.65,H4_DASH,Anim.Ease.OUT),
        k(.675,H4_DRAW),
        k(.69,H4_SWING,Anim.Ease.LINEAR),
        k(blow(4,0),H4_CUT,Anim.Ease.LINEAR),
        k(.72,H4_ON,Anim.Ease.LINEAR),
        k(.78,H4_END,Anim.Ease.OUT),
        k(1.1,H4_HOLD),
        // up again unhurried, the sword raised and lowered (the combo is over)
        k(1.35,H4_RISE),
        k(1.55,H4_LOWER),
        k(1.8,STANCE)).stops(Anim.stop(blow(4,0),.07,.12)).after(IsosangSlash::grounded);

    @Override public Anim clip(int combo){return hit(combo);}
    static Anim hit(int combo){return switch(combo){case 2->H2;case 3->H3;case 4->H4;default->H1;};}
    @Override public float lifetime(){return 4.2f;}
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
     * (forward, up, right from its user's feet); its head sweeping on over [t0, t1] ({@code pace}: 0 smoothly, 1 at an
     * even speed for a spin, 2 fast at first and slowing, as the video's spin cuts: 2.78.0), eaten from the tail over
     * [eat0, eat1] while it turns to ink over [ink0, ink1]. Style: the ink ribbons' (0 a spin cut, thin at its tail and
     * thick at its head; 1 the ring; 6 the thin cut rising back; 7 a thin bright arc).
     */
    record Arc(Vec3d centre,Vec3d a,Vec3d b,double inner,double outer,double from,double to,float t0,float t1,float eat0,float eat1,float ink0,float ink1,int style,int seed,int pace){}
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
    static Arc arc(Vec3d centre,double tilt,double toward,double inner,double outer,double from,double to,float t0,float t1,float eat0,float eat1,float ink0,float ink1,int style,int seed,int pace){
        var p=plane(tilt,toward);return new Arc(centre,p[0],p[1],inner,outer,from,to,t0,t1,eat0,eat1,ink0,ink1,style,seed,pace);
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
        return new Arc(centre,d2,d1,r-width,r,-alpha,alpha,t0,t1,eat0,eat1,ink0,ink1,style,seed,0);
    }
    /**
     * Every hit's slashes (2.78.0, the user's video from above; angle 0 ahead, 90 to the right, so a rising angle is a
     * sweep clockwise seen from above, to the right). 1: the spin cut, from the front-left across the front, round the
     * right and behind to the back-left, leaning up to the front-right (thin at its tail, thick and inked at its head), and
     * the thin cut rising back from the low left behind to the high right ahead. 2: the ring, a whole turn from the front
     * round the right, low over the ground. 3: a ring leaning up to the front, from the front-left round the right to
     * behind; the long cut rising forward ({@link #beam}); a second ring (leaning a little to the right), a third (up to
     * the front-left), a thin level arc at the chest from the left round the front, and a fourth, the biggest (up to the
     * front-right); the rings all end thick and inked at the back-left. 4: the low cut that throws the slash (the slash
     * itself: {@link #wave}).
     */
    static final Arc[] H1_ARCS={
        arc(v(.2,2.0,0),27,-135,2.9,4.3,30,400,.25f,.32f,.72f,.86f,.4f,.62f,0,1,2)};
    static final Arc[] H2_ARCS={arc(v(.35,.5,0),3,180,2.3,3.1,-60,300,.1f,.19f,.6f,.9f,.12f,.36f,1,3,1)};
    static final Arc[] H3_ARCS={
        arc(v(.5,1.5,.5),20,180,1.7,2.5,-90,120,.18f,.24f,.42f,.62f,.22f,.36f,0,15,2),
        arc(v(.4,1.3,.3),12,-90,2.0,2.8,-30,250,.49f,.56f,.78f,1.0f,.54f,.7f,0,5,2),
        arc(v(.4,1.45,0),20,135,2.2,3.0,-40,250,.69f,.76f,1.0f,1.22f,.74f,.9f,0,11,2),
        arc(v(-.2,1.35,-.7),4,0,2.55,2.8,-80,120,.79f,.83f,.95f,1.1f,.86f,.95f,7,3,2),
        arc(v(.3,1.45,0),18,-135,2.6,3.6,-20,260,.89f,.97f,1.28f,1.55f,.95f,1.15f,0,9,2)};
    static final Arc[] H4_ARCS={arc(v(1.0,.72,0),0,0,1.5,2.9,125,-115,.675f,.715f,.88f,1.04f,.69f,.83f,0,7,0)};
    static Arc[] arcs(int combo){return switch(combo){case 1->H1_ARCS;case 2->H2_ARCS;case 3->H3_ARCS;default->H4_ARCS;};}
    /**
     * The slash blow {@code i} of hit {@code combo} lands with (an index into {@link #arcs}; -1 for hit 3's long rising
     * cut, whose beam it is). Hit 3: the first ring has no blow of its own; the second ring, the third, the thin arc and
     * the fourth ring take the other four.
     */
    static int arcOf(int combo,int i){
        if(combo==3)return i==0?-1:i;
        return Math.min(i,arcs(combo).length-1);
    }

    /** A point of slash {@code s} at angle {@code deg}, {@code radius} out (the hit's frame). */
    static Vec3d point(Arc s,double deg,double radius){double t=Math.toRadians(deg);return s.centre().add(s.a().multiply(Math.cos(t)*radius)).add(s.b().multiply(Math.sin(t)*radius));}
    /** How far slash {@code s}'s head has swept on at {@code t} (0 not yet, 1 all the way). */
    static float head(Arc s,float t){float u=Ease.unit(t,s.t0(),s.t1());return switch(s.pace()){case 1->u;case 2->1-(1-u)*(1-u);default->u*u*(3-2*u);};}
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
    /**
     * How far each point of slash {@code s}'s band has turned to ink at {@code t}: each part as long after its blade swept
     * it as the slash's own ink takes (the videos': its tail goes to ink first while its head still shines).
     */
    static float[] inks(Arc s,Swept sw,float t){
        float h=head(s,t),delay=s.ink0()-s.t0(),dur=s.ink1()-s.ink0();float[] out=new float[sw.n()];
        for(int i=0;i<sw.n();i++){float swept=s.t0()+(s.t1()-s.t0())*sw.along()[i]*h;out[i]=Ease.smooth(t,swept+delay,swept+delay+dur);}
        return out;
    }
    /**
     * The band's thickness along it (0..1), as the shader shapes each style (2.78.0, the video: a spin cut is a thin line
     * at its tail, where the blade began, and thickens all the way to its rounded head, where it stopped).
     */
    static double profile(int style,double x){
        return switch(style){
            case 0->{double h=Ease.smooth((float)x,.86f,1);yield (.08+.92*Math.pow(x,1.3))*(1-h*h);}
            case 2->Math.pow(Math.max(Math.sin(Math.PI*x),0),.5);
            case 3->Math.pow(Math.max(Math.sin(Math.PI*x),0),.36);
            case 6->Math.pow(Math.max(Math.sin(Math.PI*Math.pow(x,.62)),0),.55);
            case 7->Math.pow(Math.max(Math.sin(Math.PI*x),0),.3);
            default->.9;
        };
    }
    /** How far across the band its bright line runs (0 the inner edge, 1 the outer): along the cutting edge, outermost. */
    static double lineAt(int style,double prof){return 1-.05*prof;}
    /**
     * Where the ink begins across a band {@code prof} thick that has turned {@code ink} far to ink (0 the outer edge, 1 the
     * inner), as the shader draws it (2.78.0, the video: the ink fills the thick parts and barely touches the thin ones,
     * and spreads outward as it ages).
     */
    static double inkFront(double prof,double ink){
        double spread=1-(1-ink)*(1-ink);
        return Math.max(.2,.92-.55*Ease.smooth((float)prof,.15f,.9f)-.25*spread);
    }
    /** The same for a slash of {@code style}: the ring (1) is light all through until it dries, as the shader has it. */
    static double inkFront(int style,double prof,double ink){
        if(style==1){double spread=1-(1-ink)*(1-ink);return .9-.6*spread;}
        return inkFront(prof,ink);
    }
    /** How far in from the outer edge the white-hot rim reaches (0..1 of the band), as the shader has it: the ring's broad while fresh. */
    static double whiteEnd(int style,float ink){
        return style==1?.6-.52*Ease.smooth(ink,0,.12f):.24-.18*Ease.smooth(ink,0,.3f);
    }

    /**
     * Hit 3's thrust: a beam from this far behind the hand to this far ahead of the feet (blocks), lit over these times
     * (2.77.7, the user's videos: it climbs from low behind them to high ahead, through the foe's chest, and shines on
     * while the cut down crosses it in an X).
     */
    static final Vec3d BEAM_FROM=v(-2.4,.55,.15),BEAM_TO=v(6.5,2.6,.05);
    static final float BEAM_ON=.265f,BEAM_OFF=.92f;
    static float beamLit(float t){return Ease.smooth(t,BEAM_ON,BEAM_ON+.035f)*(1-Ease.smooth(t,BEAM_OFF-.18f,BEAM_OFF));}
    /** The beam's ends at {@code t} (it runs out ahead from the point as the thrust drives in). */
    static Vec3d[] beam(Vec3d feet,float yaw,float t){
        double grow=Ease.out(t,BEAM_ON,BEAM_ON+.04f);
        Vec3d a=world(feet,yaw,BEAM_FROM),b=world(feet,yaw,BEAM_FROM.add(BEAM_TO.subtract(BEAM_FROM).multiply(.15+.85*grow)));
        return new Vec3d[]{a,b};
    }

    /**
     * The thrown slash's heights over the ground: its back edge low, its leading edge high (2.78.0, the user's videos: a
     * broad band from above and a tall one from the front, so it leans back from its leading edge, about 35°); and when
     * it turns to ink and is gone (it lingers where its flight ends and dries there).
     */
    static final double WAVE_BACK=.55,WAVE_FRONT=1.05,WAVE_LOOK_DEPTH=1.0;
    static final float WAVE_INK0=1.25f,WAVE_INK1=1.65f,WAVE_EAT0=1.75f,WAVE_EAT1=2.15f;
    /** Ticks the thrown slash has flown at hit 4's second {@code t} (it stops where its flight ends). */
    static double flown(float t){return Math.max(0,Math.min(IsosangRules.WAVE_TICKS,(t-blow(4,0))*20));}
    /**
     * The thrown slash at hit 4's second {@code t} (null before it is thrown or once gone): a crescent across the way it
     * went, tip to tip, its back edge inner and low, its leading edge outer and high, where the server's flight has it
     * (IsosangRules.waveFront, waveHalf, waveCurve), growing out of the low cut over its first frames.
     */
    static Swept wave(Vec3d feet,float yaw,float t){
        float thrown=blow(4,0);if(t<thrown||t>=WAVE_EAT1)return null;
        double ticks=flown(t),grow=Ease.out((float)((t-thrown)*20),0,1.6f);
        double front=IsosangRules.waveFront(ticks),half=IsosangRules.waveHalf(ticks)*(.6+.4*grow),curve=IsosangRules.waveCurve(ticks);
        int n=41;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){
            double u=-1+2.0*i/(n-1),side=half*u,edge=front-curve*u*u,back=edge-IsosangRules.WAVE_DEPTH*WAVE_LOOK_DEPTH*(.7+.3*grow);
            out[i]=world(feet,yaw,v(edge,WAVE_BACK+(WAVE_FRONT-WAVE_BACK)*(.6+.4*grow),side));in[i]=world(feet,yaw,v(back,WAVE_BACK,side));along[i]=i/(float)(n-1);
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
     * The ink ribbons (KagenoFire's ink pass): the ink soaked into the ground, the strokes of ink looping round them in the
     * air, every slash along its arc (drawn on as it sweeps, eaten from the tail as it turns to ink), the thrust's beam,
     * the thrown slash and the ink thrown up out of whom it strikes.
     */
    @Override public void ink(Fx c){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        // the ground's stains first (under everything), the loops of ink in the air, then the slashes
        groundInk(c,t,combo,feet,yaw);
        for(var l:loops(combo))loop(feet,yaw,l,t);
        for(var s:arcs(combo)){
            if(t>=s.eat1())continue;
            var sw=band(feet,yaw,s,t);if(sw==null)continue;
            KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),Ease.smooth(t,s.eat0(),s.eat1()),inks(s,sw,t),Ease.smooth(t,s.t0(),s.t0()+.015f)*shown(sw,c.camera));
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
            inkUp(c,t);
        }
    }
    /** A straight band from a to b, {@code width} across along {@code side} (a unit vector). */
    static void strip(Vec3d a,Vec3d b,Vec3d side,double width,int style,int seed,float erase,float ink,float alpha){
        Vec3d h=side.multiply(width*.5);int n=9;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){float u=i/(float)(n-1);Vec3d p=a.add(b.subtract(a).multiply(u));in[i]=p.subtract(h);out[i]=p.add(h);along[i]=u;}
        KagenoFire.inkRibbon(in,out,along,n,style,seed,erase,ink,alpha);
    }
    /**
     * A stain of ink soaked into the ground at {@code p} (the ink pass's style 5: a soft blot the ground shows through,
     * darker in its middle): {@code size} across, stretched {@code stretch} times along {@code angle} (radians), drying
     * away over {@code dry} (0 wet, 1 gone).
     */
    static void stain(Vec3d p,double size,double angle,double stretch,int seed,float dry,float alpha){
        if(alpha<=.01f||size<=.01||dry>=1)return;
        Vec3d a=new Vec3d(Math.cos(angle),0,Math.sin(angle)).multiply(size*stretch*.5),b=new Vec3d(-Math.sin(angle),0,Math.cos(angle)).multiply(size*.5);
        KagenoFire.inkRibbon(new Vec3d[]{p.subtract(a).subtract(b),p.add(a).subtract(b)},new Vec3d[]{p.subtract(a).add(b),p.add(a).add(b)},new float[]{0,1},2,5,seed,dry,0,alpha);
    }
    /**
     * How long a stain lasts (seconds; the videos': the ink soaks in where it lands and is gone within a second, no hard
     * edge to it): it spreads out over its first tenth of a second and dries away, shrinking and fading, from 0.35 s
     * (2.77.6: larger and darker, so the ink reads as ink).
     */
    static final float STAIN_LIFE=.95f;
    /**
     * How long a pool of ink soaked into the ground under a slash lasts (2.78.0, the video from above: the big dark pools
     * under the ring and the low sweeps show about a tenth of a second after the blade and are gone in half a second).
     */
    static final float POOL_LIFE=.5f;
    /** The smallest drop that leaves a stain where it lands (blocks; 2.77.4: every drop's stain dirtied the ground round them). */
    static final double STAIN_MIN=.05;
    static float dried(float age){return dried(age,STAIN_LIFE);}
    static float dried(float age,float life){return Ease.smooth(age,.37f*life,life);}
    /** A stain {@code age} seconds after the ink landed (nothing before it lands or once it has dried). */
    static void stainAt(Vec3d p,double size,double angle,double stretch,int seed,float age,float alpha){stainAt(p,size,angle,stretch,seed,age,alpha,STAIN_LIFE);}
    /** The same, lasting {@code life} seconds. */
    static void stainAt(Vec3d p,double size,double angle,double stretch,int seed,float age,float alpha,float life){
        if(age<0||age>=life)return;
        float dry=dried(age,life);
        stain(p,size*(.5+.5*Ease.out(age,0,.1f)),angle,stretch,seed&15,dry,alpha*(1-Ease.smooth(dry,.6f,1)));
    }
    /**
     * A pool of ink under slash {@code s} (2.78.0): {@code n} soft stains along its band from angle {@code from} to
     * {@code to} (its own degrees), each born as the blade passed over it and {@code delay} on, {@code size} across,
     * lasting {@link #POOL_LIFE}; their alpha {@code alpha}.
     */
    static void pool(Vec3d feet,float yaw,Arc s,double from,double to,int n,float delay,double size,float alpha,int seed0,float t){
        double floor=feet.y+.02;
        for(int k=0;k<n;k++){
            int seed=k*41+seed0;double f=(k+.5+.4*(Ease.hash(seed)-.5))/n,deg=from+(to-from)*f;
            double along=(deg-s.from())/(s.to()-s.from());if(along<0||along>1)continue;
            float born=s.t0()+(s.t1()-s.t0())*(float)along+delay+.04f*(float)Ease.hash(seed+1);
            double rad=s.inner()+(s.outer()-s.inner())*(.3+.6*Ease.hash(seed+2));
            Vec3d q=point(s,deg,rad),p=world(feet,yaw,v(q.x,0,q.z));
            stainAt(new Vec3d(p.x,floor+.003*(k%4),p.z),size*(.8+.4*Ease.hash(seed+3)),Math.toRadians(-yaw)+Math.toRadians(deg)+Math.PI/2+(Ease.hash(seed+4)-.5)*.6,1.4+.9*Ease.hash(seed+5),seed,t-born,alpha,POOL_LIFE);
        }
    }
    /**
     * A stroke of ink looping round them in the air (the videos' brush strokes: thin, thick and thin, drawn on fast and
     * eaten away from its tail): round {@code centre} (the hit's frame) in a plane leaning {@code tilt} degrees toward
     * {@code toward}, {@code radius} out ({@code squash} times as far across), {@code width} thick, from angle
     * {@code from} to {@code to}, drawn on over [d0, d1] and eaten over [e0, e1].
     */
    record Loop(Vec3d centre,double tilt,double toward,double radius,double squash,double width,double from,double to,float d0,float d1,float e0,float e1,int seed){}
    /**
     * Hit 4's one: a thin ring of ink drawn on the ground round them as the slash is thrown (2.78.0, the user's videos: a
     * brush ring on the ground where the cut was made, kept a third of a second). The strokes looping in the air round hit
     * 2's ring and hit 4's cut (2.77.2–2.77.7) are gone: the video from above has none.
     */
    static final Loop[] H4_LOOPS={new Loop(v(.5,.05,0),0,0,1.6,1,.09,-200,160,.7f,.75f,1.0f,1.35f,6)};
    static Loop[] loops(int combo){return combo==4?H4_LOOPS:new Loop[0];}
    static void loop(Vec3d feet,float yaw,Loop l,float t){
        float drawn=Ease.out(t,l.d0(),l.d1()),erase=Ease.smooth(t,l.e0(),l.e1());
        if(drawn<=.004f||erase>=1)return;
        var pl=plane(l.tilt(),l.toward());
        double span=(l.to()-l.from())*drawn;int n=Math.max(10,(int)Math.ceil(Math.abs(span)/4))+1;
        Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){
            double deg=Math.toRadians(l.from()+span*i/(n-1)),r=l.radius()*(1+.06*Math.sin(deg*2+l.seed()));
            Vec3d dir=pl[0].multiply(Math.cos(deg)).add(pl[1].multiply(Math.sin(deg)*l.squash()));
            in[i]=world(feet,yaw,l.centre().add(dir.multiply(r-l.width()*.5)));out[i]=world(feet,yaw,l.centre().add(dir.multiply(r+l.width()*.5)));along[i]=i/(float)(n-1);
        }
        KagenoFire.inkRibbon(in,out,along,n,4,l.seed(),erase,0,.95f*(1-Ease.smooth(t,l.e1()-.1f,l.e1())));
    }
    /**
     * Ink thrown up out of everyone the thrown slash strikes (the video: black strokes bursting up off them as it cuts
     * through): strokes drawn up out of them in a blink, leaning the way it flew, eaten away from below.
     */
    static void inkUp(Fx c,float t){
        Vec3d f=Vec3d.fromPolar(0,c.cast.yaw()),side=new Vec3d(-f.z,0,f.x);double floor=anchor(c).y+.05;int b=0;
        for(var e:struck(c,0,anchor(c))){
            float at=strikeAt(c,0,e);b++;if(t<at||t>=at+.8f)continue;
            Vec3d o=reachedAt(c,e,at);
            for(int k=0;k<7;k++){
                int seed=k*47+b*131;float t0=at+.05f*(float)Ease.hash(seed),u=Ease.out(t,t0,t0+.13f);if(u<=0)continue;
                double h=1.5+1.7*Ease.hash(seed+1),lean=(Ease.hash(seed+2)-.3)*.9,off=(Ease.hash(seed+3)-.5)*1.3;
                Vec3d base=new Vec3d(o.x,floor,o.z).add(side.multiply(off)).add(f.multiply((Ease.hash(seed+4)-.5)*.7));
                Vec3d top=base.add(0,h,0).add(f.multiply(lean*h*.5)).add(side.multiply(off*.5));
                strip(base,base.add(top.subtract(base).multiply(u)),side,.2+.24*Ease.hash(seed+5),4,seed&15,Ease.smooth(t,t0+.3f,t0+.68f),0,.95f);
            }
        }
    }
    /**
     * Ink soaked into the ground for a moment (the videos': soft and see-through, gone within a second): where hit 2's ring
     * bursts and its ink falls round them, under hit 3's low sweep, along hit 4's thrown slash as its back edge passes over
     * and round whom it strikes; and wherever a drop of ink lands.
     */
    static void groundInk(Fx c,float t,int combo,Vec3d feet,float yaw){
        double floor=feet.y+.02;
        if(combo==1){
            // (2.79.0: no pool under the spin cut: it skims the ground behind the left and ends high in the air)
        }
        if(combo==2){
            // the ground inside the ring darkens as it dries, and a pool of ink gathers under the first quarter it drew
            // (2.78.0, the video from above: the ring's black crescent on the ground, right and behind, lasting on while
            // the rest thins to a line); then the ink it bursts into splashes round it
            var ring=H2_ARCS[0];float dark=t-(ring.t1()+.1f);
            if(dark>=0&&dark<POOL_LIFE+.1f){
                Vec3d o=world(feet,yaw,v(.35,0,0));float dry=Ease.smooth(dark,.2f,POOL_LIFE+.1f);
                stain(new Vec3d(o.x,floor-.002,o.z),6.0*(.6+.4*Ease.out(dark,0,.15f)),Math.toRadians(-yaw),1,9,dry,.34f*(1-Ease.smooth(dry,.6f,1)));
            }
            pool(feet,yaw,ring,40,200,9,.18f,1.5,.9f,211,t);
            float at=blow(2,1);
            for(int k=0;k<12;k++){
                int seed=k*29+311;float born=at+.1f+.14f*(float)Ease.hash(seed);
                double az=Math.PI*2*(k+.6*Ease.hash(seed+1))/12,r=2.8+1.4*Ease.hash(seed+2);
                Vec3d p=world(feet,yaw,v(.35+Math.cos(az)*r,0,Math.sin(az)*r));
                stainAt(new Vec3d(p.x,floor+.003*(k%4),p.z),.8+.8*Ease.hash(seed+5),Math.toRadians(-yaw)+az+Math.PI/2+(Ease.hash(seed+6)-.5),1.3+1.2*Ease.hash(seed+7),seed,t-born,.88f);
            }
        }
        if(combo==3){
            // under each ring's thick end behind the left (the second, third and fourth: 2.78.0, the video from above has
            // the ground there black with ink by the end), the fourth's the widest
            pool(feet,yaw,H3_ARCS[1],170,250,4,.06f,1.2,.8f,577,t);
            pool(feet,yaw,H3_ARCS[2],165,250,5,.06f,1.3,.85f,601,t);
            pool(feet,yaw,H3_ARCS[4],150,260,8,.06f,1.7,.9f,631,t);
        }
        if(combo==4){
            float at=blow(4,0);
            for(int k=0;k<16;k++){
                int seed=k*37+733;
                double fw=2+(IsosangRules.WAVE_RANGE-2.5)*Ease.hash(seed),u=(Ease.hash(seed+1)-.5)*2;
                // the slash's back edge passes over it: when its leading edge is DEPTH past it
                float when=reaches(fw+IsosangRules.WAVE_DEPTH*.8,0,0);if(when<0)when=at+.4f;
                double side=u*IsosangRules.waveHalf(Math.min(IsosangRules.WAVE_TICKS,(when-at)*20))*.8;
                Vec3d p=world(feet,yaw,v(fw,0,side));
                stainAt(new Vec3d(p.x,floor+.003*(k%4),p.z),.75+.75*Ease.hash(seed+6),Math.toRadians(yaw+90)+(Ease.hash(seed+5)-.5)*.6,1.5+1.5*Ease.hash(seed+7),seed,t-when-.04f*(float)Ease.hash(seed+2),.85f);
            }
            // round whom it strikes, where it struck them
            int b=0;
            for(var e:struck(c,0,anchor(c))){
                float hit=strikeAt(c,0,e);b++;Vec3d o=reachedAt(c,e,hit);
                for(int k=0;k<3;k++){
                    int seed=b*97+k*13;double az=Math.PI*2*Ease.hash(seed),r=.35*k;
                    stainAt(new Vec3d(o.x+Math.cos(az)*r,floor+.004*k,o.z+Math.sin(az)*r),1.3+.9*Ease.hash(seed+1),az,1.2+.6*Ease.hash(seed+2),seed,t-hit-.1f-.06f*k,.9f);
                }
            }
        }
        // every drop that has landed (the small ones soak in unseen)
        for(var d:dropsOf(c,t,combo,feet,yaw)){
            if(d.size()<STAIN_MIN)continue;
            double land=lands(d,feet.y+.03);if(land<0)continue;
            float age=t-d.born()-(float)land;if(age<0||age>=STAIN_LIFE)continue;
            Vec3d p=flight(d,land),mv=moving(d,land);
            stainAt(new Vec3d(p.x,floor+.001*(d.seed()%7),p.z),d.size()*(d.kind()==2?4.2:4.6),Math.atan2(mv.z,mv.x),1+Math.min(1.4,Math.hypot(mv.x,mv.z)*.3),d.seed(),age,.78f);
        }
    }

    // ───────────────────────────── light ─────────────────────────────

    /** Brightness of the blade's aura: lit for the cuts, flaring at each blow; the finisher lights it from the twirl. */
    static float glow(float t,int combo){
        float g=switch(combo){
            case 2->Ease.window(t,.06f,.36f,.04f,.12f);
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
        for(var s:arcs(combo)){var sw=band(feet,yaw,s,t);if(sw!=null&&t<s.eat1())edgeGlow(m,sw,s.style(),edgeLit(s,t),inks(s,sw,t));}
        sparks(c,m,t,combo,feet,yaw);
        spray(m,t,combo,feet,yaw);
        impacts(c,m,t,combo);
        if(combo==1)glint(c,m,t);
        if(combo==2)flare(c,m,t,feet,yaw);
        if(combo==3)beamLight(c,m,t,feet,yaw);
        if(combo==4){twirl(c,m,t);point(c,m,t);dash(c,m,t);waveLight(c,m,t,feet,yaw);}
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        inkBursts(c,m,t,combo);
        if(combo==2)inkRing(m,t,feet,yaw);
        airInk(c,m,t,combo,feet,yaw);
    }
    /** Hit 2's burst: a ring of ink running out under its white ring, a little wider, so the light is drawn in ink (2.77.6). */
    static void inkRing(EffectMesh m,float t,Vec3d feet,float yaw){
        float at=blow(2,1);if(t<at-.02f||t>=at+.4f)return;
        Vec3d o=world(feet,yaw,v(.35,.49,0));float a=Ease.smooth(t,at-.02f,at+.01f)*(1-Ease.unit(t,at,at+.3f));
        FxLib.ring(m,o,2.85+1.0*Ease.out(t,at,at+.22f),.14,INK,.7f*a);
    }
    /**
     * Over the ink ribbons (2.77.5, user request "마지막으로 이펙트 더 예쁘게 꾸며줘": the moon's light and its stars, as the
     * sword has them and as the hits are named): the starlight on each slash, a glint racing at each one's head, the ring's
     * burst of stars, the stars along the thrust, and the moon riding the thrown slash with its trail of stardust.
     */
    @Override public void overlay(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        for(var s:arcs(combo))for(var st:stars(s,t)){
            Vec3d p=world(feet,yaw,point(s,st.deg(),st.radius()));
            int col=st.night()?(st.seed()%3==0?LILAC:PALE):(st.seed()%3==0?WHITE:st.seed()%3==1?PALE:LILAC);
            glint8(m,p,st.size(),col,st.alpha());
        }
        heads(m,t,combo,feet,yaw);
        if(combo==2)ringStars(m,t,feet,yaw);
        if(combo==3)beamStars(m,t,feet,yaw);
        if(combo==4)waveStars(m,t,feet,yaw);
    }
    /**
     * The blade's blue aura while it cuts, brightest as it strikes: kept close round the blade (2.77.4, user request "지금
     * 평타 너무 가시성이 안 좋은데": a wide haze round it clouded them).
     */
    static void aura(Fx c,EffectMesh m,float t,int combo){
        float g=glow(t,combo);if(g<=.01f)return;
        var hb=bladeNow(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));float on=Math.min(1,g);
        double w=.06*(1+.4*Math.max(0,Math.min(1,g-1)));float a=Math.min(1.1f,g);
        m.softLine(guard,tip,w*5,AURA[1],a*.12f);m.softLine(guard,tip,w*2.4,AURA[1],a*.45f);
        m.line(guard,tip,w,AURA[2],a*.85f);m.line(guard,tip,w*.45,AURA[3],a*.8f);
        m.softDisc(tip,.12+.08*on,AURA[3],.5f*on);
    }
    /**
     * Each slash's light along its cutting edge (outermost), as wide as the band is thick there: a deep blue haze, a blue
     * glow, an azure one and a white line, so the effect layer blooms round the edge as the videos' slashes do. The glow
     * hugs the edge (2.77.4: a haze three times the band wide blurred the crescent's shape and washed out what was behind it).
     * The glow faces the camera, so where the band is seen edge on it fades to a fifth ({@link #faceOn}): else a slash seen
     * edge on, a thin line itself, stood up in the air as a pillar of light.
     */
    static void edgeGlow(EffectMesh m,Swept sw,int style,float a){edgeGlow(m,sw,style,a,null);}
    /** The same, each part's light dimmed as that part turns to ink ({@code ink} per point of the band; 2.78.0, the video: the white rim is gone from a part within a tenth of a second of the blade). */
    static void edgeGlow(EffectMesh m,Swept sw,int style,float a,float[] ink){
        if(a<=.01f)return;
        for(int i=0;i+1<sw.n();i++){
            double x=(sw.along()[i]+sw.along()[i+1])*.5,prof=profile(style,x);if(prof<.02)continue;
            double k=lineAt(style,prof);
            Vec3d p0=sw.inner()[i].add(sw.outer()[i].subtract(sw.inner()[i]).multiply(k)),p1=sw.inner()[i+1].add(sw.outer()[i+1].subtract(sw.inner()[i+1]).multiply(k));
            float dim=ink==null?1:1-.8f*(ink[i]+ink[i+1])*.5f;
            // (2.78.0: the glow is a rim, no wider than on a band 0.6 blocks thick, so a thick head stays dark inside)
            double width=Math.min(.6,sw.outer()[i].distanceTo(sw.inner()[i])*prof);float f=a*(float)Math.min(1,prof*1.6)*dim,face=faceOn(sw,i,m.camera()),glow=f*(.2f+.8f*face);
            m.softLine(p0,p1,width*1.2,DEEP,.08f*glow);
            m.softLine(p0,p1,width*.55,BLUE,.26f*glow);
            m.softLine(p0,p1,width*.22,AZURE,.45f*glow);
            m.line(p0,p1,.05+.03*Math.min(1,prof*1.6),WHITE,.9f*f*(.4f+.6f*face));
        }
    }
    /** The sine of how far out of band {@code sw}'s plane at its segment {@code i} the view from {@code camera} is (0 edge on, 1 face on). */
    static double outOfPlane(Swept sw,int i,Vec3d camera){
        Vec3d n=sw.outer()[i].subtract(sw.inner()[i]).crossProduct(sw.inner()[i+1].subtract(sw.inner()[i]));
        Vec3d view=camera.subtract(sw.inner()[i].add(sw.outer()[i]).multiply(.5));
        double nl=n.length(),vl=view.length();return nl<1e-9||vl<1e-6?1:Math.abs(n.dotProduct(view)/(nl*vl));
    }
    /** How face on band {@code sw} is seen from {@code camera} at its segment {@code i}: 0 edge on, 1 once the view is 24° or more out of its plane. */
    static float faceOn(Swept sw,int i,Vec3d camera){return Ease.smooth((float)outOfPlane(sw,i,camera),.08f,.4f);}
    /**
     * How much of a slash shows from {@code camera}: all of it, unless it is seen edge on all along (no part of it more than
     * about 9° out of its plane), then down to a third. Seen edge on, a flat slash is a sliver with all its light squeezed
     * into it: a bright line standing up in the air (2.77.4: hit 3's diagonal cuts, from the side). Not the thrown slash:
     * whom it flies at sees it edge on, coming.
     */
    static float shown(Swept sw,Vec3d camera){
        double most=0;for(int i=0;i+1<sw.n();i++)most=Math.max(most,outOfPlane(sw,i,camera));
        return .35f+.65f*Ease.smooth((float)most,.03f,.15f);
    }
    /**
     * How bright slash {@code s}'s edge is at {@code t}: flaring as it sweeps, its white edge staying on (thinner) while
     * the ink spreads in behind it (as the videos' do), gone as it is eaten away.
     */
    static float edgeLit(Arc s,float t){
        float flare=1+.35f*(float)Math.exp(-Math.max(0,t-s.t1())/.05f)*Ease.smooth(t,s.t0(),s.t0()+.02f);
        return flare*(1-.3f*Ease.smooth(t,s.ink0(),s.ink1()))*(1-Ease.smooth(t,s.eat0(),s.eat1()));
    }
    /**
     * A star on slash {@code s} (2.77.5): at angle {@code deg} and {@code radius} out (the hit's frame), {@code size}
     * across, shining {@code alpha}; in the band's ink ({@code night}) or in its light.
     */
    record Star(double deg,double radius,double size,float alpha,boolean night,int seed){}
    /**
     * The starlight on slash {@code s} at {@code t} (the sword's stars; its name is the moon's trace): small glints in the
     * band's light right behind the blade, gone as the ink spreads over them, and once the ink has turned, a few faint
     * stars in it, like stars in a night sky. Each is fixed on the band where the blade passed (where the shader has its
     * light or its ink: {@link #inks}' clock, the shader's ink front and white edge), twinkling, and eaten away with it.
     */
    static List<Star> stars(Arc s,float t){
        List<Star> out=new ArrayList<>();
        if(t<s.t0()||t>=s.eat1())return out;
        float h=head(s,t),delay=s.ink0()-s.t0(),dur=s.ink1()-s.ink0();double eaten=Ease.smooth(t,s.eat0(),s.eat1())*1.1-.05;
        int n=s.style()==1?30:20;
        for(int k=0;k<n;k++){
            int seed=k*61+s.seed()*977;double f=.04+.92*Ease.hash(seed);
            if(f>h-.02)continue;
            double x=f/h,prof=profile(s.style(),x);if(x<eaten+.1||prof<.15)continue;
            float swept=s.t0()+(s.t1()-s.t0())*(float)f,ink=Ease.smooth(t,swept+delay,swept+delay+dur);
            double inkAt=inkFront(s.style(),prof,ink),whiteEnd=whiteEnd(s.style(),ink),d;float a;
            boolean night=k%2==1;
            if(night){if(ink<.4f)continue;d=inkAt+.1+(.86-inkAt-.1)*Ease.hash(seed+1);if(d>.86)continue;a=.8f*Ease.smooth(ink,.4f,.75f);}
            else{double room=inkAt-whiteEnd-.15;if(room<.02)continue;d=whiteEnd+.06+room*Ease.hash(seed+1);a=1-Ease.smooth(ink,.55f,.85f);}
            a*=twinkle(t,seed)*Ease.smooth((float)x,(float)eaten+.1f,(float)eaten+.2f)*Ease.smooth(t,swept,swept+.03f);
            if(a<=.01f)continue;
            out.add(new Star(s.from()+(s.to()-s.from())*f,s.inner()+(s.outer()-s.inner())*(1-d*prof),night?.08+.05*Ease.hash(seed+2):.1+.08*Ease.hash(seed+2),a,night,seed));
        }
        return out;
    }
    /** A star's twinkle at {@code t} (0.3..1): two to four times a second, each its own way. */
    static float twinkle(float t,int seed){
        float w=(float)Math.sin(2*Math.PI*(t*(2+2*Ease.hash(seed+5))+Ease.hash(seed+6)));return .3f+.7f*w*w;
    }
    /** The head of each slash while it sweeps (where the blade is): a bright glint racing along its cutting edge, gone as it stops. */
    static void heads(EffectMesh m,float t,int combo,Vec3d feet,float yaw){
        for(var s:arcs(combo)){
            if(t<s.t0()||t>=s.t1()+.08f)continue;
            float a=Ease.smooth(t,s.t0(),s.t0()+.015f)*(1-Ease.smooth(t,s.t1()+.01f,s.t1()+.08f));
            Vec3d p=world(feet,yaw,point(s,s.from()+(s.to()-s.from())*head(s,t),s.outer()));
            glint8(m,p,.3,WHITE,a);m.softDisc(p,.24,AZURE,.3f*a);
        }
    }
    /** Hit 2's burst: stars riding out on its white ring, twinkling, gone with it (in place of streaks of light). */
    static void ringStars(EffectMesh m,float t,Vec3d feet,float yaw){
        float at=blow(2,1);if(t<at||t>=at+.36f)return;
        Vec3d o=world(feet,yaw,v(.35,.5,0));double r=2.75+1.0*Ease.out(t,at,at+.22f);float fade=1-Ease.unit(t,at,at+.36f);
        for(int k=0;k<12;k++){
            int seed=k*53+211;double az=Math.PI*2*(k+.4*Ease.hash(seed))/12;
            Vec3d p=o.add(dir(yaw,v(Math.cos(az)*r,.15*(Ease.hash(seed+1)-.5),Math.sin(az)*r)));
            float a=fade*twinkle(t,seed)*Ease.smooth(t,at+.008f*(k%4),at+.008f*(k%4)+.02f);
            glint8(m,p,.1+.07*Ease.hash(seed+2),k%3==2?LILAC:k%3==1?PALE:WHITE,a);
        }
    }
    /** Hit 3's thrust: stars lighting one after another along the beam as it pierces, twinkling while it shines. */
    static void beamStars(EffectMesh m,float t,Vec3d feet,float yaw){
        float lit=beamLit(t);if(lit<=0)return;
        var b=beam(feet,yaw,t);
        for(int k=0;k<6;k++){
            int seed=k*37+409;float on=BEAM_ON+.012f+.022f*k;if(t<on)continue;
            Vec3d p=b[0].add(b[1].subtract(b[0]).multiply(.22+.13*k)).add(0,.1*(Ease.hash(seed)-.5),0);
            glint8(m,p,.12+.06*Ease.hash(seed+1),k%2==0?WHITE:LILAC,lit*Ease.smooth(t,on,on+.02f)*twinkle(t,seed));
        }
    }
    /**
     * Hit 4's thrown slash (落月, the falling moon): a crescent moon riding its front as it flies, and stardust left behind
     * along its path, drifting up and twinkling as it fades.
     */
    static void waveStars(EffectMesh m,float t,Vec3d feet,float yaw){
        float at=blow(4,0),fly=IsosangRules.WAVE_TICKS/20f;
        var sw=wave(feet,yaw,t);
        if(sw!=null&&t<at+fly+.1f){
            float a=Ease.smooth(t,at,at+.03f)*(1-Ease.smooth(t,at+fly-.05f,at+fly+.1f));
            Vec3d front=sw.outer()[sw.n()/2].add(0,.3,0);
            crescentMoon(m,front,.3,Math.PI*.62,MOON,.85f*a);glint8(m,front,.2,WHITE,.6f*a);m.softDisc(front,.45,AZURE,.25f*a);
        }
        for(int k=0;k<28;k++){
            int seed=k*41+1307;float born=at+.03f+(fly-.04f)*(k+(float)Ease.hash(seed))/28,age=t-born,life=.35f+.25f*(float)Ease.hash(seed+1);
            if(age<0||age>=life)continue;
            var swb=wave(feet,yaw,born);if(swb==null)continue;
            double x=(.08+.84*Ease.hash(seed+2))*(swb.n()-1);int i=Math.min(swb.n()-2,(int)x);double fr=x-i;
            Vec3d p=swb.outer()[i].add(swb.outer()[i+1].subtract(swb.outer()[i]).multiply(fr)).add(0,.05+.35*Ease.hash(seed+3)+.25*age,0);
            float u=age/life;
            glint8(m,p,.08+.06*Ease.hash(seed+4),k%3==0?WHITE:k%3==1?PALE:LILAC,(1-u)*Ease.smooth(age,0,.03f)*twinkle(t,seed));
        }
    }
    /** An eight-pointed glint at {@code p}, facing the camera (a glint, so it may turn to it): a four-pointed star and a shorter one turned 45°. */
    static void glint8(EffectMesh m,Vec3d p,double r,int rgb,float alpha){
        if(alpha<=.01f||r<=.005)return;
        m.star(p,r,rgb,alpha);
        Vec3d[] uv=cameraPlane(m,p);if(uv==null)return;
        Vec3d d1=uv[0].add(uv[1]).multiply(.3536*r),d2=uv[0].subtract(uv[1]).multiply(.3536*r);
        m.line(p.subtract(d1),p.add(d1),r*.09,rgb,.8f*alpha);m.line(p.subtract(d2),p.add(d2),r*.09,rgb,.8f*alpha);
        m.softDisc(p,r*.3,rgb,.5f*alpha);
    }
    /**
     * A crescent moon of light at {@code p}, facing the camera (a glint): its outer edge a circle {@code r} round, its
     * inner edge bulging in to leave a lune widest (0.55 of {@code r}) at its back, its horns opening toward {@code turn}
     * (radians in the camera's plane, 0 its right).
     */
    static void crescentMoon(EffectMesh m,Vec3d p,double r,double turn,int rgb,float alpha){
        if(alpha<=.01f||r<=.005)return;
        Vec3d[] uv=cameraPlane(m,p);if(uv==null)return;
        int n=16;double span=Math.toRadians(125);Vec3d po=null,pi=null;
        for(int i=0;i<=n;i++){
            double th=-span+2*span*i/n,w=.55*r*Math.cos(Math.PI/2*th/span),ang=turn+Math.PI+th;
            Vec3d d=uv[0].multiply(Math.cos(ang)).add(uv[1].multiply(Math.sin(ang))),o=p.add(d.multiply(r)),in=p.add(d.multiply(r-w));
            if(po!=null){m.triangle(po,o,in,rgb,alpha);m.triangle(po,in,pi,rgb,alpha);}
            po=o;pi=in;
        }
    }
    /** The camera's right and up at {@code p} (a plane facing it), or null at the camera itself. */
    static Vec3d[] cameraPlane(EffectMesh m,Vec3d p){
        Vec3d normal=m.camera().subtract(p);if(normal.lengthSquared()<1e-6)return null;normal=normal.normalize();
        Vec3d u=normal.crossProduct(UP);if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();
        return new Vec3d[]{u,u.crossProduct(normal).normalize()};
    }
    /**
     * Motes of light sprayed off each slash's bright edge as it breaks up (the videos': the edge going to pale spray as it
     * goes), from where the edge is being eaten (its tail on): since 2.77.5 moon dust, white, pale and lilac, half of them
     * little stars, drifting out and up and then settling slowly, swaying, twinkling as they fade.
     */
    static void spray(EffectMesh m,float t,int combo,Vec3d feet,float yaw){
        int k0=0;
        for(var s:arcs(combo)){
            k0+=67;if(t<s.eat0()||t>=s.eat1()+.8f)continue;
            Vec3d centre=world(feet,yaw,s.centre());int n=s.style()==1?18:12;
            for(int k=0;k<n;k++){
                int seed=k*71+k0;float born=s.eat0()+(s.eat1()-s.eat0())*(float)Ease.hash(seed),age=t-born,life=.5f+.3f*(float)Ease.hash(seed+1);
                if(age<0||age>=life)continue;
                double x=Math.max(0,Math.min(1,Ease.smooth(born,s.eat0(),s.eat1())*1.05+.08*(Ease.hash(seed+2)-.5)));
                Vec3d at=world(feet,yaw,point(s,s.from()+(s.to()-s.from())*x,s.outer()-(s.outer()-s.inner())*.1*Ease.hash(seed+3)));
                Vec3d out=at.subtract(centre);out=out.lengthSquared()<1e-8?UP:out.normalize();
                Vec3d vel=out.multiply(.4+.8*Ease.hash(seed+4)).add(0,.4+.4*Ease.hash(seed+5),0),side=out.crossProduct(UP);
                side=side.lengthSquared()<1e-8?Vec3d.ZERO:side.normalize();
                Vec3d q=at.add(vel.multiply((1-Math.exp(-age*3.5))/3.5)).add(0,-.3*age*age,0).add(side.multiply(.06*Math.sin(age*9+seed)));
                float u=age/life,a=(float)(1-u)*Ease.smooth(age,0,.04f)*twinkle(t,seed);int col=k%3==0?WHITE:k%3==1?PALE:LILAC;
                if(k%2==0)m.star(q,.06+.05*(1-u),col,a);else m.softDisc(q,.035+.03*(1-u),col,.8f*a);
            }
        }
    }
    /** Hit 1's glint: the sword flashing into the hand, a white line along it and blue stars at the hand and the point. */
    static void glint(Fx c,EffectMesh m,float t){
        if(t>=.16f)return;
        float a=Ease.smooth(t,0,.025f)*(1-Ease.smooth(t,.08f,.16f));
        var hb=bladeNow(c);Vec3d hand=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));
        m.softLine(hand,tip,.3,AZURE,.3f*a);m.line(hand,tip,.07,WHITE,.9f*a);
        float s=Ease.smooth(t,.02f,.05f)*(1-Ease.smooth(t,.07f,.14f));
        m.softDisc(hand,.35*s+.06,BLUE,.5f*s);m.star(hand,.45*s+.06,PALE,s);m.star(hand,.24*s+.03,WHITE,s);
        m.softDisc(tip,.4*s+.06,BLUE,.5f*s);m.star(tip,.55*s+.06,PALE,s);m.star(tip,.3*s+.03,WHITE,s);
    }
    /**
     * Sparks thrown off every slash's cutting edge as it sweeps: small blue, pale and white motes, born where the edge
     * passed, flying on along the sweep and outwards, slowing and fading in a quarter of a second.
     */
    static void sparks(Fx c,EffectMesh m,float t,int combo,Vec3d feet,float yaw){
        int k0=0;
        for(var s:arcs(combo)){
            k0+=31;if(t<s.t0()||t>=s.t1()+.3f)continue;
            int n=s.style()==1?20:14;
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
    /** One target struck by blow {@code blow} of the hit (the {@code nth} it struck), drawn struck {@code at}, {@code when} seconds in. */
    record Struck(Vec3d at,float when,int blow,int nth){}
    /** Every target every blow strikes (the thrown slash: each as it reaches them). */
    static List<Struck> strikes(Fx c,int combo){
        List<Struck> out=new ArrayList<>();
        for(int i=0;i<IsosangRules.blows(combo);i++){
            int n=0;
            if(combo==4){for(var e:struck(c,0,anchor(c))){float at=strikeAt(c,0,e);out.add(new Struck(reachedAt(c,e,at),at,i,n++));}}
            else{float at=blow(combo,i);for(var p:hits(c,i,at))out.add(new Struck(p,at,i,n++));}
        }
        return out;
    }
    /** How long an impact's burst lasts (seconds); how big it is (blocks: the finisher's biggest, hit 3's quick cuts smaller). */
    static final float BURST=.2f;
    static final double[] BURST_SIZE={0,.6,.55,.5,.78};
    static double burstSize(int combo,Struck w,float t){return (.62+.38*Ease.out(t,w.when(),w.when()+.06f))*BURST_SIZE[combo]*(1-.15*Ease.unit(t,w.when(),w.when()+BURST));}
    static float burstAlpha(Struck w,float t){return Ease.smooth(t,w.when()-.006f,w.when()+.012f)*(float)Math.pow(1-Ease.unit(t,w.when(),w.when()+BURST),.7);}
    /**
     * Where a burst on a target is drawn: on the near side of them (its centre is inside them, so they would hide half of
     * it), 0.75 blocks out (2.77.5: at 0.35 it sat at their skin, where the effect layer melts what touches the world, and
     * its crescent was all but gone), at most half way to the camera.
     */
    static Vec3d facing(Vec3d p,Vec3d camera){Vec3d d=camera.subtract(p);double l=d.length();return l<.8?p:p.add(d.multiply(Math.min(.75,.5*l)/l));}
    /** How much of a burst on a target shows: none to a camera inside them (the struck's own first person), all from 1.4 blocks. */
    static float seen(Vec3d p,Vec3d camera){return (float)Math.max(0,Math.min(1,(camera.distanceTo(p)-.7)/.7));}
    /**
     * Every struck target, at every blow (the thrown slash: as it reaches them): a small sharp burst, blue spikes round a
     * pale heart with an ink edge (drawn under it, {@link #inkBursts}), a crescent moon at its heart (2.77.5), a thin quick
     * ring across the cut and a few short sparks the way it went through. Small and crisp (2.77.4, user request "지금 평타 너무 가시성이 안 좋은데":
     * the big white flashes, the cross of light and the wide rings hid whom they struck, and the slashes behind them).
     */
    static void impacts(Fx c,EffectMesh m,float t,int combo){
        for(var w:strikes(c,combo)){
            Vec3d p=w.at();float at=w.when();int i=w.blow();if(t<at-.01f||t>=at+.3f)continue;
            Vec3d way=combo==4?Vec3d.fromPolar(0,c.cast.yaw()):sweep(c,combo,i);
            Vec3d side=way.crossProduct(UP);if(side.lengthSquared()<1e-4)side=c.right;side=side.normalize();Vec3d up=side.crossProduct(way).normalize();
            float u=Ease.unit(t,at,at+.16f),a=Ease.smooth(t,at-.01f,at+.01f);boolean big=combo==4;double s=big?1.35:1;
            float vis=seen(p,c.camera),ba=burstAlpha(w,t)*vis,flash=a*(1-u)*vis;double br=burstSize(combo,w,t);
            Vec3d near=facing(p,c.camera);
            if(ba>0){StandPunchFx.burst(m,near,br,.36,9,combo*31+i*7+w.nth()*13,AZURE,BLUE,.85f*ba,.32f*ba);crescentMoon(m,near,br*.56,Math.PI*2*Ease.hash(combo*31+i*7+w.nth()*13+19)+.8*(t-at),MOON,.9f*ba);}
            if(flash>0){m.softDisc(near,.7*s,BLUE,.28f*flash);m.softDisc(near,.16*s*(1-u)+.04,WHITE,.7f*flash);}
            // a thin quick ring across the cut
            FxLib.ringPlane(m,p,side,up,.15+.85*s*Ease.out(t,at,at+.15f),.07,WHITE,.7f*a*(1-Ease.unit(t,at,at+.15f)));
            float sp=a*(1-Ease.smooth(t,at+.05f,at+.2f));
            for(int k=0;k<(big?16:10);k++){
                double ang=(Ease.hash(k*3+combo*17+i*5)-.5)*2.4,rise=(Ease.hash(k*5+combo*11+i)-.5)*1.6;
                Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(up.multiply(rise)).normalize();
                double len=(.35+.6*Ease.hash(k*7+combo+i*3))*s*Ease.out(t,at,at+.12f);
                Vec3d q=p.add(dir.multiply(br*.8+len*.3));
                m.needle(q,q.add(dir.multiply(len*.6)),.05,.3,k%4==0?WHITE:k%4==1?PALE:k%4==2?LILAC:AZURE,sp);
            }
        }
    }
    /** The ink edge under every impact's burst (a little bigger, so the burst reads against a bright sky or the struck). */
    static void inkBursts(Fx c,EffectMesh m,float t,int combo){
        for(var w:strikes(c,combo)){
            float ba=burstAlpha(w,t)*seen(w.at(),c.camera);if(t<w.when()-.01f||ba<=0)continue;
            StandPunchFx.burst(m,facing(w.at(),c.camera),burstSize(combo,w,t)*1.22,.36,9,combo*31+w.blow()*7+w.nth()*13,INK,INK,.75f*ba,.75f*ba);
        }
    }
    /** The way slash {@code i} of a hit moves at its blow (a unit vector; the hit's facing if none). */
    static Vec3d sweep(Fx c,int combo,int i){
        var arcs=arcs(combo);int j=arcOf(combo,i);
        if(combo==2||j<0||j>=arcs.length)return Vec3d.fromPolar(0,c.cast.yaw());
        var s=arcs[j];double deg=s.from()+(s.to()-s.from())*.5;
        Vec3d a=world(Vec3d.ZERO,c.cast.yaw(),point(s,deg,s.outer())),b=world(Vec3d.ZERO,c.cast.yaw(),point(s,deg+Math.signum(s.to()-s.from())*3,s.outer()));
        Vec3d w=b.subtract(a);return w.lengthSquared()<1e-8?Vec3d.fromPolar(0,c.cast.yaw()):w.normalize();
    }
    /**
     * Hit 2's burst at its second blow (as the ring cracks into ink): a thin white ring and a narrow blue one running out
     * over the ground from the ring (2.77.4: no wide flash round the ring, and the rings narrow, so the ring cracking into
     * ink shows through), with stars riding out on the white one ({@link #ringStars}, 2.77.5, in place of streaks of light).
     */
    static void flare(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        float at=blow(2,1);if(t<at-.02f||t>=at+.4f)return;
        Vec3d o=world(feet,yaw,v(.35,.5,0));float a=Ease.smooth(t,at-.02f,at+.01f);
        FxLib.ring(m,o,2.75+1.0*Ease.out(t,at,at+.22f),.08,WHITE,.85f*a*(1-Ease.unit(t,at,at+.22f)));
        FxLib.ring(m,o,2.6+1.8*Ease.out(t,at,at+.36f),.18,AZURE,.3f*a*(1-Ease.unit(t,at,at+.36f)));
    }
    /**
     * Hit 3's beam: its light along the whole line and a glint at its far end (2.77.7: no rings of air running on along
     * it; the videos have none, and the whirl's ring goes round them instead).
     */
    static void beamLight(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        float a=beamLit(t);if(a<=0)return;
        var b=beam(feet,yaw,t);
        // a sheet of pale light standing from the ground up to the line (2.78.0, the video from above: a translucent wedge
        // widening ahead; from the front, a blade of light taller as it goes)
        int n=10;Vec3d prevG=null,prevL=null;double floor=feet.y+.03;
        for(int i=0;i<=n;i++){
            Vec3d l=b[0].add(b[1].subtract(b[0]).multiply(i/(double)n)),g=new Vec3d(l.x,floor,l.z);
            if(prevL!=null){m.triangle(prevG,g,l,AZURE,.11f*a);m.triangle(prevG,l,prevL,AZURE,.11f*a);Vec3d pm=prevG.add(prevL.subtract(prevG).multiply(.55)),cm=g.add(l.subtract(g).multiply(.55));m.triangle(pm,cm,l,PALE,.1f*a);m.triangle(pm,l,prevL,PALE,.1f*a);}
            prevG=g;prevL=l;
        }
        m.softLine(b[0],b[1],.8,DEEP,.2f*a);m.softLine(b[0],b[1],.35,AZURE,.5f*a);
        m.needle(b[0],b[1],.1,.4,WHITE,.95f*a);
        m.softDisc(b[1],.5*a,AZURE,.5f*a);m.star(b[1],.6*a,PALE,a);m.star(b[1],.35*a,WHITE,a);
    }
    /**
     * The finisher's twirl: a broad band of pale, see-through light over the head along the point's path, left hanging a
     * fifth of a second after the blade (2.78.0, the user's video from above: a wide faint crescent, not a thin bright
     * line, still there as they aim).
     */
    static void twirl(Fx c,EffectMesh m,float t){
        if(t<.1f||t>=.58f)return;
        float from=Math.max(.1f,t-.13f),head=Math.min(t,.33f);if(head<=from+.004f)return;
        float a=.9f*(1-Ease.smooth(t,.42f,.58f));int n=24;Vec3d prev=null;
        for(int i=0;i<=n;i++){
            float s=from+(head-from)*i/n;var hb=blade(c,4,s);Vec3d p=hb[0].add(hb[1].multiply(TIP*1.06));
            if(prev!=null){float u=i/(float)n;m.softLine(prev,p,.8,CYAN,a*u*.3f);m.softLine(prev,p,.35,PALE,a*u*.35f);m.line(prev,p,.1,PALE,a*u*.45f);}
            prev=p;
        }
    }
    /** The finisher's aim: a faint blue light along the blade held back (2.78.0: no stars at the hand or the point; the video has none). */
    static void point(Fx c,EffectMesh m,float t){
        if(t<.38f||t>=.62f)return;
        float a=Ease.smooth(t,.38f,.44f)*(1-Ease.smooth(t,.56f,.62f));
        var hb=bladeNow(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));
        m.softLine(guard,tip,.3,AZURE,.3f*a);m.softDisc(tip,.16,PALE,.5f*a);
    }
    /** The finisher's lunge: streaks of air left behind along the way it went. */
    static void dash(Fx c,EffectMesh m,float t){
        if(t<.6f||t>=.8f)return;
        Vec3d f=Vec3d.fromPolar(0,c.cast.yaw());Vec3d side=f.crossProduct(UP).normalize();
        for(int k=0;k<6;k++){
            float born=.6f+.06f*(float)Ease.hash(k*7+3),age=t-born;if(age<0||age>=.18f)continue;
            double off=(Ease.hash(k*11)-.5)*1.4,h=.3+1.3*Ease.hash(k*13),len=1.0+1.2*Ease.hash(k*17);
            Vec3d a=at(c,.2+1.0*Ease.hash(k*5),h,off),b=a.subtract(f.multiply(len));
            m.needle(b,a,side,.05,.85,k%2==0?WHITE:PALE,.5f*(1-age/.18f));
        }
    }
    /** The thrown slash's light: a blue glow along its leading edge and a white line, brightest as it is thrown; a small flash as it is thrown. */
    static void waveLight(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        var sw=wave(feet,yaw,t);if(sw==null)return;
        float lit=(1-.5f*Ease.smooth(t,WAVE_INK0,WAVE_INK1))*(1-Ease.smooth(t,WAVE_EAT0,WAVE_EAT1));
        edgeGlow(m,sw,3,1.1f*lit);
        // streaks of light along its body the way it flies, riding on it
        Vec3d f=Vec3d.fromPolar(0,yaw);float fast=lit*(1-Ease.smooth(t,blow(4,0)+.3f,blow(4,0)+.6f));
        if(fast>.01f)for(int k=0;k<12;k++){
            double x=(.06+.88*Ease.hash(k*13+5))*(sw.n()-1);int i=Math.min(sw.n()-2,(int)x);double fr=x-i;
            Vec3d edge=sw.outer()[i].add(sw.outer()[i+1].subtract(sw.outer()[i]).multiply(fr)),back=sw.inner()[i].add(sw.inner()[i+1].subtract(sw.inner()[i]).multiply(fr));
            Vec3d a=edge.add(back.subtract(edge).multiply(.12)),b=a.subtract(f.multiply(edge.distanceTo(back)*(.6+.9*Ease.hash(k*17+5))));
            m.needle(b,a,.05,.4,k%3==0?WHITE:PALE,.4f*fast);
        }
        // long streaks of light fanning out from the cut to the slash's back edge as it flies off (2.78.0, the video from
        // above: rays from where it was thrown, out across the ground behind it, for a third of a second)
        float rays=lit*Ease.smooth(t,blow(4,0)+.02f,blow(4,0)+.06f)*(1-Ease.smooth(t,blow(4,0)+.2f,blow(4,0)+.5f));
        if(rays>.01f){
            Vec3d from=world(feet,yaw,v(1.1,.7,0));
            for(int k=0;k<10;k++){
                double x=(.08+.84*Ease.hash(k*19+41))*(sw.n()-1);int i=Math.min(sw.n()-2,(int)x);double fr=x-i;
                Vec3d back=sw.inner()[i].add(sw.inner()[i+1].subtract(sw.inner()[i]).multiply(fr));
                Vec3d a=from.add(back.subtract(from).multiply(.2+.1*Ease.hash(k*23+41))),b=from.add(back.subtract(from).multiply(.98));
                m.needle(a,b,.06,.45,k%3==0?WHITE:k%3==1?PALE:AZURE,.35f*rays);
            }
        }
        float at=blow(4,0),fl=Ease.smooth(t,at-.01f,at+.01f)*(1-Ease.smooth(t,at+.03f,at+.22f));
        if(fl>0){Vec3d o=world(feet,yaw,v(1.6,.8,0));m.softDisc(o,1.4,BLUE,.3f*fl);m.softDisc(o,.6,WHITE,.7f*fl);m.star(o,1.2*fl,PALE,fl);m.star(o,.6*fl,WHITE,fl);}
    }

    // ───────────────────────────── ink in the air ─────────────────────────────

    /**
     * A drop of ink flung at {@code born} from {@code at} at {@code vel}, slowed by the air ({@code drag} a second) and
     * falling (it drops {@code fall}·age² blocks), {@code size} across; {@code kind} 0 a round drop, 1 a flake (a pointed
     * petal, tumbling), 2 a blot (bigger and lumpier: a slash breaking up). It shows for {@code life} seconds, shrinking
     * and fading, unless it lands first: then it stains the ground where it landed ({@link #groundInk}).
     */
    record Drop(Vec3d at,Vec3d vel,double drag,double fall,float born,float life,double size,int seed,int kind){}
    /** The longest a drop shows in the air (the cast's lifetime holds it and the stain after it). */
    static final float DROP_LIFE=1.05f;
    static Vec3d flight(Drop d,double age){return d.at().add(d.vel().multiply((1-Math.exp(-age*d.drag()))/d.drag())).add(0,-d.fall()*age*age,0);}
    static Vec3d moving(Drop d,double age){return d.vel().multiply(Math.exp(-age*d.drag())).add(0,-2*d.fall()*age,0);}
    /** Seconds after it was flung that a drop reaches {@code floor} (-1: not while it shows). */
    static double lands(Drop d,double floor){
        if(d.at().y<=floor)return 0;
        if(flight(d,d.life()).y>floor)return -1;
        double lo=0,hi=d.life();
        for(int i=0;i<16;i++){double mid=(lo+hi)*.5;if(flight(d,mid).y>floor)lo=mid;else hi=mid;}
        return hi;
    }
    /**
     * Every drop of ink a hit throws: shed by each slash as it turns to ink (drops and flakes flung outward and on along the
     * sweep) and burst off it as it breaks up (blots and flakes, from where it is being eaten); splashed from every struck
     * target at every blow (the thrown slash: as it reaches them); dripping off the thrown slash's back edge as it flies.
     */
    static List<Drop> dropsOf(Fx c,float t,int combo,Vec3d feet,float yaw){
        List<Drop> out=new ArrayList<>();int k0=0;
        for(var s:arcs(combo)){
            k0+=53;if(t<s.ink0())continue;
            Vec3d centre=world(feet,yaw,s.centre());double sign=Math.signum(s.to()-s.from());
            int n=s.style()==1?30:20;
            for(int k=0;k<n;k++){
                float born=s.ink0()+(s.ink1()-s.ink0())*.7f*(float)Ease.hash(k*7+k0);if(t<born)continue;
                double deg=s.from()+(s.to()-s.from())*(.05+.9*Ease.hash(k*13+k0)),r=s.inner()+(s.outer()-s.inner())*(.15+.8*Ease.hash(k*17+k0));
                Vec3d at=world(feet,yaw,point(s,deg,r)),radial=at.subtract(centre);radial=radial.lengthSquared()<1e-8?UP:radial.normalize();
                Vec3d way=world(feet,yaw,point(s,deg+sign*4,r)).subtract(at);way=way.lengthSquared()<1e-8?c.f:way.normalize();
                double speed=.8+1.8*Ease.hash(k*19+k0);
                Vec3d vel=radial.multiply(speed).add(way.multiply(speed*.7*(Ease.hash(k*23+k0)-.2))).add(0,.2+.6*Ease.hash(k*29+k0),0);
                int kind=k%3==2?1:0;
                out.add(new Drop(at,vel,3.2,3.4,born,.6f+.45f*(float)Ease.hash(k*11+k0),kind==1?.06+.07*Ease.hash(k*31+k0):.04+.06*Ease.hash(k*31+k0),k*37+k0,kind));
            }
            // breaking up: blots and flakes burst off the band where it is being eaten, from its tail on
            if(t>=s.eat0())for(int k=0;k<(s.style()==1?14:9);k++){
                int seed=k*59+k0+7;float born=s.eat0()+(s.eat1()-s.eat0())*.6f*(float)Ease.hash(seed);if(t<born)continue;
                double x=Math.min(1,Ease.smooth(born,s.eat0(),s.eat1())*.85+.1*Ease.hash(seed+1));
                double deg=s.from()+(s.to()-s.from())*x,r=s.inner()+(s.outer()-s.inner())*(.2+.6*Ease.hash(seed+2));
                Vec3d at=world(feet,yaw,point(s,deg,r)),radial=at.subtract(centre);radial=radial.lengthSquared()<1e-8?UP:radial.normalize();
                Vec3d way=world(feet,yaw,point(s,deg+sign*4,r)).subtract(at);way=way.lengthSquared()<1e-8?c.f:way.normalize();
                double speed=1.8+2.4*Ease.hash(seed+3);
                Vec3d vel=radial.multiply(speed).add(way.multiply(speed*.5)).add(0,.6+.8*Ease.hash(seed+4),0);
                int kind=k%2==0?2:1;
                out.add(new Drop(at,vel,2.6,2.6,born,.7f+.35f*(float)Ease.hash(seed+6),kind==2?.09+.09*Ease.hash(seed+5):.06+.07*Ease.hash(seed+5),seed,kind));
            }
        }
        // splashed from the struck
        for(var w:strikes(c,combo)){
            Vec3d p=w.at();float at=w.when();int i=w.blow(),b=w.nth()+1;if(t<at)continue;
            Vec3d way=combo==4?Vec3d.fromPolar(0,c.cast.yaw()):sweep(c,combo,i);
            Vec3d side=way.crossProduct(UP);side=side.lengthSquared()<1e-4?c.right:side.normalize();
            for(int k=0;k<(combo==4?20:12);k++){
                int seed=k*41+i*7+combo*13+b*101;
                double ang=(Ease.hash(seed+1)-.5)*2.6,rise=Ease.hash(seed+2)*1.3-.2;
                Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(0,rise,0).normalize();
                int kind=k%3==1?1:k%7==3?2:0;
                out.add(new Drop(p,dir.multiply(1.6+2.8*Ease.hash(seed+3)),4,2.8,at,.6f+.4f*(float)Ease.hash(seed),kind==0?.045+.065*Ease.hash(seed+4):.065+.08*Ease.hash(seed+4),seed,kind));
            }
        }
        // flung off the beam's far end (2.77.7, the videos': it bursts into ink where it ends)
        if(combo==3&&t>=BEAM_ON+.14f){
            Vec3d from=world(feet,yaw,BEAM_FROM),end=world(feet,yaw,BEAM_TO),f=end.subtract(from).normalize();
            for(int k=0;k<12;k++){
                int seed=k*47+1301;float born=BEAM_ON+.14f+.12f*(float)Ease.hash(seed);if(t<born)continue;
                Vec3d dir=f.multiply(.7).add(new Vec3d(Ease.hash(seed+1)-.5,Ease.hash(seed+2)*.9,Ease.hash(seed+3)-.5).multiply(1.6)).normalize();
                int kind=k%3==0?2:1;
                out.add(new Drop(end.subtract(f.multiply(.8*Ease.hash(seed+4))),dir.multiply(1.6+2.2*Ease.hash(seed+5)),2.6,2.4,born,.7f+.3f*(float)Ease.hash(seed+6),kind==2?.09+.09*Ease.hash(seed+7):.06+.07*Ease.hash(seed+7),seed,kind));
            }
        }
        // dripping off the thrown slash's back edge
        if(combo==4){
            float at=blow(4,0);
            for(int k=0;k<56;k++){
                int seed=k*43+977;float born=at+.08f+(WAVE_EAT1-at-.1f)*(float)Ease.hash(seed);if(t<born)continue;
                var sw=wave(feet,yaw,born);if(sw==null)continue;
                double x=(.1+.8*Ease.hash(seed+2))*(sw.n()-1);int i=Math.min(sw.n()-2,(int)x);double f=x-i;
                Vec3d back=sw.inner()[i].add(sw.inner()[i+1].subtract(sw.inner()[i]).multiply(f)),edge=sw.outer()[i].add(sw.outer()[i+1].subtract(sw.outer()[i]).multiply(f));
                Vec3d start=back.add(edge.subtract(back).multiply(.15*Ease.hash(seed+3)));
                Vec3d fly=Vec3d.fromPolar(0,yaw).multiply(1.6*(1-flown(born)/IsosangRules.WAVE_TICKS));
                int kind=k%4==3?1:0;
                out.add(new Drop(start,fly,.05,4.6,born,.55f+.35f*(float)Ease.hash(seed+1),kind==1?.07+.07*Ease.hash(seed+4):.045+.07*Ease.hash(seed+4),seed,kind));
            }
        }
        return out;
    }
    /** The drops in the air, each until it lands (its stain is drawn in the ink pass) or fades. */
    static void airInk(Fx c,EffectMesh m,float t,int combo,Vec3d feet,float yaw){
        double floor=feet.y+.03;
        for(var d:dropsOf(c,t,combo,feet,yaw)){
            float age=t-d.born();if(age<0||age>=d.life())continue;
            double land=lands(d,floor);if(land>=0&&age>=land)continue;
            Vec3d q=flight(d,age),mv=moving(d,age);float u=age/d.life();
            float by=near(q,c.camera);if(by<=0)continue;
            double size=d.size()*(1-.45*u)*Ease.out(age,0,.06f)*(.35+.65*by);float a=.94f*(1-Ease.smooth(u,.6f,1))*by;
            switch(d.kind()){
                case 1->flake(m,q,size,age,1.2+2.0*Ease.hash(d.seed()+9),d.seed(),INK,a);
                case 2->blot(m,q,mv,size,d.seed(),INK,a);
                default->drop(m,q,mv,size,d.seed(),INK,a);
            }
        }
    }
    /**
     * Flying ink close to the camera shrinks and fades out, from 2.6 blocks to nothing at 1 (2.77.6: by the eye a drop
     * covered the screen like a slab of rock, half faded a pale ghost of one, and the struck saw what splashed from their
     * own face).
     */
    static float near(Vec3d q,Vec3d camera){return Ease.smooth((float)camera.distanceTo(q),1f,2.6f);}
    /**
     * One drop of ink at {@code q}: a round blot stretched the way it moves (in a plane holding that way, tumbling a
     * little, fixed in the world).
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
        gleam(m,q,p,1,3,size,alpha);
    }
    /**
     * Moonlight gleaming on a drop of ink (2.77.5): a rim of dark blue sheen along one side of its outline {@code p} round
     * its middle {@code q} ({@code count} edges from point {@code from}, thinning to nothing at both ends) and a speck of
     * light on that rim, so the ink reads as wet (in its plane, over it). A spot in its middle read as an eye.
     */
    static void gleam(EffectMesh m,Vec3d q,Vec3d[] p,int from,int count,double size,float alpha){
        if(size<.03||alpha<=.02f)return;
        for(int k=0;k<count;k++){
            Vec3d a=p[(from+k)%p.length],b=p[(from+k+1)%p.length];
            double wa=.32*Math.sin(Math.PI*k/count),wb=.32*Math.sin(Math.PI*(k+1)/count);
            Vec3d ai=a.add(q.subtract(a).multiply(wa)),bi=b.add(q.subtract(b).multiply(wb));
            m.triangle(a,b,bi,SHEEN,.6f*alpha);m.triangle(a,bi,ai,SHEEN,.6f*alpha);
        }
        Vec3d mid=p[(from+count/2)%p.length],speck=mid.add(q.subtract(mid).multiply(.16));double r=size*.07;
        Vec3d u=mid.subtract(q);u=u.lengthSquared()<1e-12?UP:u.normalize();Vec3d w=p[(from+count/2+1)%p.length].subtract(mid);
        w=w.subtract(u.multiply(w.dotProduct(u)));w=w.lengthSquared()<1e-12?UP:w.normalize();
        m.triangle(speck.add(u.multiply(r)),speck.add(w.multiply(r*1.6)),speck.subtract(u.multiply(r)),WHITE,.5f*alpha);
        m.triangle(speck.add(u.multiply(r)),speck.subtract(u.multiply(r)),speck.subtract(w.multiply(r*1.6)),WHITE,.5f*alpha);
    }
    /**
     * A flake of ink at {@code q}: a pointed petal {@code size} long, turning over about its own length ({@code spin}
     * turns a second) so it shows face on, then edge on (fixed in the world, not turned to the camera).
     */
    static void flake(EffectMesh m,Vec3d q,double size,double age,double spin,int seed,int rgb,float alpha){
        if(alpha<=.01f||size<=.005)return;
        double th=Ease.hash(seed+11)*Math.PI*2,z=2*Ease.hash(seed+12)-1,rr=Math.sqrt(Math.max(0,1-z*z));
        Vec3d along=new Vec3d(rr*Math.cos(th),z,rr*Math.sin(th));
        Vec3d any=Math.abs(along.y)<.9?UP:new Vec3d(1,0,0),u=along.crossProduct(any).normalize(),w=along.crossProduct(u).normalize();
        double a=Math.PI*2*(spin*age+Ease.hash(seed+13));
        Vec3d across=u.multiply(Math.cos(a)).add(w.multiply(Math.sin(a)));
        double len=size,wid=size*(.28+.12*Ease.hash(seed+14));
        double[][] shape={{1,0},{.35,.6},{-.3,.5},{-.62,0},{-.3,-.5},{.35,-.6}};
        Vec3d[] p=new Vec3d[shape.length];
        for(int i=0;i<shape.length;i++)p[i]=q.add(along.multiply(shape[i][0]*len)).add(across.multiply(shape[i][1]*wid));
        for(int i=1;i+1<p.length;i++)m.triangle(p[0],p[i],p[i+1],rgb,alpha);
    }
    /** A blot of ink at {@code q}: a lumpy round splash, stretched the way it moves (fixed in the world). */
    static void blot(EffectMesh m,Vec3d q,Vec3d moving,double size,int seed,int rgb,float alpha){
        if(alpha<=.01f||size<=.005)return;
        double speed=moving.length();Vec3d along=speed<1e-4?UP:moving.multiply(1/speed);
        double tilt=Ease.hash(seed+8)*Math.PI;
        Vec3d any=Math.abs(along.y)<.9?UP:new Vec3d(1,0,0),s1=along.crossProduct(any).normalize(),s2=along.crossProduct(s1).normalize();
        Vec3d across=s1.multiply(Math.cos(tilt)).add(s2.multiply(Math.sin(tilt)));
        double stretch=1+Math.min(1.6,speed*.25);int sides=12;Vec3d[] p=new Vec3d[sides];
        for(int i=0;i<sides;i++){
            double a=Math.PI*2*i/sides,r=size*(.72+.28*Ease.hash(seed+i*5+1))*(i%4==0?1+.3*Ease.hash(seed+i*7+2):1);
            p[i]=q.add(along.multiply(Math.cos(a)*r*stretch)).add(across.multiply(Math.sin(a)*r));
        }
        for(int i=0;i<sides;i++)m.triangle(q,p[i],p[(i+1)%sides],rgb,alpha);
        gleam(m,q,p,2,4,size,alpha);
    }

    // ───────────────────────────── the effect layer ─────────────────────────────

    /**
     * Lights: the blade's aura, every struck target's flash, the ring's burst, the beam, the thrown slash flying;
     * afterglow cut short for the quick parts. Kept low (2.77.4: they washed the floor round them white) and no shaft of
     * light as the slash is thrown (it stood over them as a column).
     */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        float g=Math.min(1.3f,glow(t,combo));
        if(g>.02f){var hb=bladeNow(c);f.light(hb[0].add(hb[1].multiply(.9)),AURA[1],4,.4f*g);}
        for(var w:strikes(c,combo)){float k=FxLib.kick(t,w.when(),.08f);if(k>.01f)f.light(w.at(),0xcfe4ff,combo==4?6:5,(combo==4?1.1f:.85f)*k);}
        if(combo==1){float k=FxLib.kick(t,.25f,.12f);if(k>.01f)f.light(world(feet,yaw,v(.4,1.6,-.4)),0x7fb0ff,7,.7f*k);}
        if(combo==2){
            float k=FxLib.kick(t,blow(2,0),.14f),fl=FxLib.kick(t,blow(2,1),.16f);
            if(k+fl>.01f)f.light(world(feet,yaw,v(.35,.6,0)),0x8cbcff,8,.75f*k+.9f*fl);
        }
        if(combo==3){
            float lit=beamLit(t);if(lit>0){var b=beam(feet,yaw,t);f.light(b[0].add(b[1]).multiply(.5),0x6aa8ff,8,.9f*lit);}
            for(int i=1;i<5;i++){float k=FxLib.kick(t,blow(3,i),.1f);if(k>.01f)f.light(world(feet,yaw,v(1.4,1.4,0)),0x7fb0ff,6,.55f*k);}
        }
        if(combo==4){
            var sw=wave(feet,yaw,t);
            if(sw!=null){
                float lit=(1-Ease.smooth(t,WAVE_INK0,WAVE_INK1))*(1-Ease.smooth(t,WAVE_EAT0,WAVE_EAT1));
                Vec3d mid=sw.outer()[sw.n()/2];f.light(mid,0x8cbcff,9,1.15f*lit);
                f.light(sw.outer()[sw.n()/6],0x6aa0ff,5,.5f*lit);f.light(sw.outer()[sw.n()*5/6],0x6aa0ff,5,.5f*lit);
            }
            float at=blow(4,0),k=FxLib.kick(t,at,.16f);
            if(k>.01f)f.light(world(feet,yaw,v(1.6,.8,0)),0xb8d8ff,9,1.4f*k);
        }
        // the whole swing is quick (the cuts, the spin, the lunge, the flight and the blade's moves between them): the
        // afterglow is cut short throughout, shortest in the spin, else the blade's bright line leaves a fan of copies of
        // itself round them like spokes (2.77.4 cut it for the cuts alone; 2.77.5 for the recoveries between them too)
        if(t<IsosangRules.LENGTH[combo]/20f)f.afterglow(combo==2&&t>=.06f&&t<.24f?.25f:combo==1&&t>=.14f&&t<.34f?.1f:.3f);// (2.79.0: hit 1's quick wind into the spin)
    }
    /** The air bends a little along each slash as it sweeps, along the thrown slash as it flies, and in a ring round the ring's burst. */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        for(var s:arcs(combo)){
            var sw=band(feet,yaw,s,t);if(sw==null||t>=s.t1()+.18f)continue;
            d.ribbon(sw.inner(),sw.outer(),sw.n(),.25f*Ease.smooth(t,s.t0(),s.t0()+.03f)*(1-Ease.smooth(t,s.t1(),s.t1()+.18f)));
        }
        if(combo==4){
            var sw=wave(feet,yaw,t);float at=blow(4,0);
            if(sw!=null&&t<at+.5f)d.ribbon(sw.inner(),sw.outer(),sw.n(),.3f*Ease.smooth(t,at,at+.03f)*(1-Ease.smooth(t,at+.3f,at+.5f)));
        }
        if(combo==2){
            float at=blow(2,1);
            if(t>=at-.01f&&t<at+.35f){Vec3d o=world(feet,yaw,v(.35,.5,0));d.ring(o,c.right,Vec3d.fromPolar(0,yaw),2.7+1.4*Ease.out(t,at,at+.35f),.9,.2f*Ease.smooth(t,at-.01f,at+.015f)*(1-Ease.unit(t,at,at+.35f)));}
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
    /** Speed afterimages of the body (the spin, the cuts, the lunge), faint so they do not cloud it (2.77.4). */
    @Override public float afterimages(float sec){return .25f;}
    /**
     * How far the caster's own camera draws back for the finisher (blocks, as in the user's video: it pulls back over the
     * crouch and the lunge to show the slash flying, holds while it dries into ink and comes back in), 0 for the others.
     */
    public static final double PULL=2.4;
    public static double pull(int combo,float sec){
        if(combo!=4)return 0;
        return PULL*Ease.smooth(sec,.55f,.88f)*(1-Ease.smooth(sec,1.4f,1.8f));
    }
}
