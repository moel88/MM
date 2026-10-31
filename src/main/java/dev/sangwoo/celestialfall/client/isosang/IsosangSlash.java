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
 * <li>관월(貫月) (2.79.2 draft): four cuts: down from her left across the front to her right; in the same place the
 * other way, rising back to her left; the mirror of the first, crossing the second in an X; and, once those are gone, the
 * biggest, nearly level, rising from her left across the front round to her right.</li>
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

    // ───────────────────────────── facing the front ─────────────────────────────
    // (2.79.3, user request "캐릭터가 개 발광을 하는데 정면만 보고 때리게 하면 안되나? 그대신 손이나 팔을 잘 써서 자연스럽게
    // 하기": every hit faces the front. The body no longer spins round (hit 1 turned a whole turn, hit 2 two, hit 3 five):
    // it only twists with the swing, at most 30° either way, the head stays on the front (R6.world), and the sword arm, the
    // wrist and the free arm do the cutting. The slashes, their timing, the blows and the sounds are as they were: the
    // crescents are set in the hit's frame, not on the blade. Through each cut the blade trails the arm the way it goes
    // (to the arm's left in a cut to the right, to its right in a cut to the left), far back as it is wound and less as it
    // comes through, so it never crosses the arm's line, where R6 would roll the arm over in a frame.)

    // ───────────────────────────── 1 참월 ─────────────────────────────
    // 1: the sword flashed into the hand low at the left, raised up the right and overhead, wound back over the left
    // shoulder in a crouch (its point behind them), then swung round in front of them: down by the left side, low across
    // the front (the blow) and up to end high at the right, as the crescent sweeps from behind round the left and the
    // front to the right.
    /** The sword flashed into the hand, held out low to the left, glinting. */
    static final R6.Pose H1_FLASH=w(.02,-.02,8,0,0,d(-40,-30),d(-20,-45),d(-80,12),-20,-160);
    static final R6.Pose H1_GLINT=w(.04,-.04,10,0,1,d(-28,-36),d(-12,-52),d(-78,14),-22,-160);
    /** Raised up to the right, then overhead. */
    static final R6.Pose H1_TURN=w(.1,-.08,-14,4,-2,d(20,50),d(40,56),d(-60,-50),30,-156);
    static final R6.Pose H1_RAISE=w(.16,-.05,-6,-6,-2,d(70,20),d(60,-20),d(-50,-60),28,-158);
    /** Down in a coil, twisted a little to the left, the sword wound back over the left shoulder, its point behind them. */
    static final R6.Pose H1_COIL=w(.24,-.3,24,12,4,d(30,-98),d(12,-158),d(-30,80),-14,-170);
    static final R6.Pose H1_LOAD=w(.26,-.32,26,13,4,d(28,-102),d(10,-162),d(-30,84),-14,-170);
    /** Held up at the right, facing the front: where the second hit begins. */
    static final R6.Pose H1_HOLD=w(.42,-.1,-14,-2,0,d(42,64),d(56,48),d(-58,-100),22,-166);
    static final R6.Pose H1_LOWER=w(.24,-.05,-8,2,0,d(-20,60),d(18,40),d(-70,-50),12,-170);
    static final Anim H1=Anim.joints(
        k(0,STANCE),
        k(.03,H1_FLASH),
        k(.07,H1_GLINT),
        k(.1,H1_TURN),
        k(.14,H1_RAISE),
        k(.17,H1_COIL),
        k(.19,H1_LOAD),
        // wound furthest, crouched, the point behind the left shoulder
        k(.23f,w(.3,-.32,28,12,4,d(26,-104),d(12,-166),d(-34,86),-10,-172)),
        // the cut, fast out of the coil and slowing: the arm sweeps the blade from behind the left shoulder down by the
        // left side, low across the front (the blow) and up to the right, the wrist bringing it through, the body
        // untwisting from 28° left to 16° right, the free arm swinging back the other way
        k(.25,w(.33,-.3,22,11,3,d(14,-86),d(0,-150),d(-36,70),-12,-172),Anim.Ease.IN),
        k(.263f,w(.34,-.31,12,11,2,d(0,-56),d(-16,-104),d(-40,36),-16,-171),Anim.Ease.LINEAR),
        k(.28f,w(.35,-.31,0,11,0,d(-4,-6),d(-12,-40),d(-44,-12),-20,-170),Anim.Ease.LINEAR),
        k(blow(1,0),w(.36,-.3,-10,10,-2,d(10,40),d(20,12),d(-46,-50),-22,-168),Anim.Ease.LINEAR),
        k(.32f,w(.38,-.26,-16,6,-4,d(30,80),d(48,58),d(-50,-80),-22,-166),Anim.Ease.OUT),
        k(.38f,H1_HOLD),
        k(step(1),H1_HOLD),
        k(.72,H1_LOWER),
        k(.9,STANCE)).stops(Anim.stop(blow(1,0),.03,.05)).after(IsosangSlash::grounded);

    // ───────────────────────────── 2 윤월 ─────────────────────────────
    // (2.79.2 draft, the user's videos from above and from the front, "처음에 뒤로 베고 앞으로 가서 베는거야 그래서 원처럼
    // 보이는거임": two cuts to the left, anticlockwise seen from above (the other way from hit 1): first one behind them at
    // the hip, then, a moment on, one in front of them low over the ground; together they look like a ring, and the one
    // behind goes first. 2.79.3, facing the front: wound low behind the right, the arm sweeps the blade from the
    // front-right across the front to the left at the hip; on the right foot, the left knee up, it is lifted over the head
    // and dropped low behind the right; then the arm sweeps it low round from behind the right across the front to the
    // left.)
    /** Wound up to the right before the first cut, crouching, the sword drawn back low behind on the right. */
    static final R6.Pose H2_WIND=w(.38,-.24,-22,10,-4,d(-22,124),d(-10,150),d(-44,-40),70,-50);
    /** The moment between the cuts (the video's crane): on the right foot, the left knee drawn up. */
    static R6.Pose crane2(double up,float yaw,double[] arm,double[] blade,double[] lArm){
        R6.Pose p=w(.36,up,yaw,6,0,arm,blade,lArm,12,Double.NaN);
        p.lLeg=R6.local(p,-30,-24);
        return p;
    }
    /** Out of the front cut, rising, the sword raised across before them at the left. */
    static final R6.Pose H2_RISE2=w(.36,-.1,12,4,0,d(30,-56),d(46,-36),d(-60,-80),14,-14);
    /** Back to the front, the sword low at the right: where the third hit begins (yaw 4, as the next clip's first key). */
    static R6.Pose h2Back(float yaw){return w(.34,-.08,yaw,4,0,d(-50,30),d(-18,40),d(-72,-20),20,-160);}
    static final Anim H2=Anim.joints(
        k(0,H1_HOLD),
        k(.05,H2_WIND),
        // the cut behind them, at the hip: the arm sweeps the blade from the front-right across the front to the left,
        // the body untwisting from the right to the left, the free arm swinging the other way
        k(.08,w(.36,-.3,-20,10,-3,d(-12,40),d(-8,66),d(-40,-60),24,-160),Anim.Ease.IN),
        k(.093,w(.36,-.32,-6,10,0,d(-4,-30),d(-4,6),d(-38,-20),24,-160),Anim.Ease.LINEAR),
        k(.106,w(.36,-.32,10,10,2,d(0,-72),d(-6,-40),d(-34,30),24,-160),Anim.Ease.LINEAR),
        k(.12,w(.36,-.32,20,10,4,d(4,-86),d(-12,-62),d(-32,50),24,-160),Anim.Ease.LINEAR),
        k(.135,w(.36,-.3,24,10,4,d(4,-90),d(-16,-68),d(-30,60),24,-160),Anim.Ease.OUT),
        // the moment between (the crane): on the right foot, the left knee up; the sword lifted over the head and
        // dropped low behind the right, ready to sweep round
        k(blow(2,0),crane2(-.2,22,d(2,-92),d(-20,-72),d(-24,50))),
        k(.18,crane2(-.12,4,d(58,-20),d(30,-150),d(10,-40))),
        k(.21,crane2(-.2,-20,d(-18,112),d(-14,150),d(36,-56))),
        // the cut in front of them, low over the ground: the arm sweeps the blade from behind the right round the right
        // and across the front to the left, the body untwisting from the right to the left
        k(.235,w(.36,-.42,-22,12,-3,d(-22,118),d(-18,160),d(-30,-70),24,-150),Anim.Ease.IN),
        k(.25,w(.36,-.44,-12,12,-1,d(-26,58),d(-20,92),d(-30,-40),24,-150),Anim.Ease.LINEAR),
        k(.265,w(.36,-.44,0,12,1,d(-28,-16),d(-20,18),d(-32,0),24,-150),Anim.Ease.LINEAR),
        k(.28,w(.36,-.42,12,12,3,d(-28,-70),d(-18,-40),d(-34,40),24,-150),Anim.Ease.LINEAR),
        k(blow(2,1),w(.36,-.38,16,10,3,d(-26,-84),d(-16,-60),d(-36,50),24,-150),Anim.Ease.OUT),
        k(.38,H2_RISE2),
        k(step(2),h2Back(4)),
        k(.9,STANCE)).stops(Anim.stop(blow(2,0),.025,.04),Anim.stop(blow(2,1),.03,.05)).after(IsosangSlash::grounded);

    // ───────────────────────────── 3 관월 ─────────────────────────────
    // (2.79.2 draft, the user's videos from above and the front at a tenth of the speed, and the user's own words: "첫번째
    // 검격이 오른쪽에서 아래로 내리고 돌아서 두번째 검격이 왼쪽으로 위로 올려 이게 조금 오래 남아서 세번째 검격이랑 교차해서
    // X자", "2번째가 애초에 첫번째랑 베는 위치는 같은데 베는 방향이 다름", "너가 읽은게 맞는 거 같아"): four cuts. 1: down
    // from her left, high (the front video's right), across the front to her right, low, on a plane leaning down to her
    // right; 2: in the same place the other way, rising from her right across the front to her left, lasting a while; 3:
    // the mirror of the first (high at her right), down across the front to her left, crossing the second in an X; 4:
    // once the second and third are gone, the biggest, nearly level, rising from her left across the front round to her
    // right, lasting longest. One blow on each cut as it passes the front (0.1, 0.35, 0.55, 0.85 s; "4번으로 바꿔"). 2.79.3,
    // facing the front: the arm and the wrist swing each cut across the front, the body twisting with them; between the
    // second and the third the sword is lifted over the head from the left shoulder to the right.
    static final Anim H3=Anim.joints(
        k(0,h2Back(4)),
        // wound up to the left, the sword drawn up over the left shoulder
        k(.05,w(.4,-.24,24,10,-3,d(40,-70),d(28,-150),d(-40,60),24,-160)),
        // 1: down from her left, high, across the front to her right, low
        k(.08,w(.4,-.3,22,10,-3,d(36,-72),d(24,-152),d(-40,62),24,-160),Anim.Ease.IN),
        k(.093,w(.4,-.32,12,10,-1,d(20,-40),d(10,-96),d(-42,30),24,-160),Anim.Ease.LINEAR),
        k(.106,w(.4,-.33,-2,10,1,d(-4,20),d(-6,-20),d(-44,-20),24,-160),Anim.Ease.LINEAR),
        k(.12,w(.4,-.33,-14,10,3,d(-22,70),d(-14,44),d(-44,-60),24,-160),Anim.Ease.OUT),
        // through, the sword low at the right; it rolls over the arm (raised past it) and is cocked to go back
        k(.2,w(.4,-.3,-18,10,3,d(-28,84),d(-12,66),d(-44,-70),24,-160)),
        k(.26,w(.4,-.31,-18,10,3,d(-28,88),d(10,88),d(-44,-70),24,-160)),
        k(.31,w(.4,-.32,-18,10,3,d(-26,90),d(-12,112),d(-44,-70),24,-160)),
        // 2: the same place the other way: rising from her right across the front (the blow) to her left, high
        k(.325f,w(.4,-.32,-17,10,3,d(-24,84),d(-10,106),d(-43,-68),24,-160)),
        k(.345f,w(.4,-.33,-12,10,2,d(-16,56),d(-6,76),d(-42,-50),24,-160),Anim.Ease.IN),
        k(blow(3,1),w(.4,-.34,-8,10,1,d(-12,36),d(-4,44),d(-41,-36),24,-160),Anim.Ease.LINEAR),
        k(.36f,w(.4,-.33,0,10,0,d(-2,-6),d(2,4),d(-40,-6),24,-160),Anim.Ease.LINEAR),
        k(.37f,w(.4,-.31,8,10,-1,d(12,-44),d(10,-30),d(-40,20),24,-160),Anim.Ease.LINEAR),
        k(.38f,w(.4,-.29,16,10,-2,d(24,-70),d(18,-52),d(-40,42),24,-160),Anim.Ease.LINEAR),
        k(.395f,w(.4,-.28,22,10,-3,d(34,-86),d(22,-64),d(-40,56),24,-160),Anim.Ease.OUT),
        // the sword lifted over the head from the left shoulder to the right, the body turning back to the right
        k(.47,w(.4,-.26,4,8,0,d(64,-10),d(36,-120),d(-30,10),24,-160)),
        k(.51,w(.4,-.28,-20,10,2,d(44,76),d(24,150),d(-40,-40),24,-160)),
        // 3: the mirror of the first: down from her right, high, across the front (the blow) to her left, low
        k(.53,w(.4,-.3,-20,10,2,d(36,62),d(20,126),d(-41,-36),24,-160),Anim.Ease.IN),
        k(.545,w(.4,-.31,-14,10,1,d(24,30),d(14,80),d(-42,-24),24,-160),Anim.Ease.LINEAR),
        k(blow(3,2),w(.4,-.32,-10,10,0,d(18,14),d(10,46),d(-42,-14),24,-160),Anim.Ease.LINEAR),
        k(.56,w(.4,-.33,0,10,-1,d(4,-26),d(-4,6),d(-42,16),24,-160),Anim.Ease.LINEAR),
        k(.57,w(.4,-.33,12,10,-2,d(-14,-66),d(-12,-36),d(-42,46),24,-160),Anim.Ease.LINEAR),
        k(.585,w(.4,-.33,20,10,-3,d(-22,-86),d(-16,-60),d(-42,60),24,-160),Anim.Ease.OUT),
        // down low behind the X, the sword drawn back low at her left, wound for the last
        k(.66,w(.4,-.34,24,10,-3,d(-20,-86),d(-8,-118),d(-40,64),24,-160)),
        k(.78,w(.4,-.34,26,10,-3,d(-18,-88),d(-6,-124),d(-40,66),24,-160)),
        // 4: the biggest, nearly level: from her left across the front (the blow) round to her right
        k(.82,w(.4,-.34,25,10,-3,d(-16,-86),d(-8,-118),d(-40,64),24,-160)),
        k(.84,w(.4,-.33,20,10,-2,d(-12,-64),d(-8,-96),d(-41,50),24,-160),Anim.Ease.IN),
        k(blow(3,3),w(.4,-.32,8,10,0,d(-6,-14),d(-4,-44),d(-42,20),24,-160),Anim.Ease.LINEAR),
        k(.86,w(.4,-.3,-2,10,1,d(-2,24),d(-2,-6),d(-44,-10),24,-160),Anim.Ease.LINEAR),
        k(.875,w(.4,-.29,-10,10,2,d(0,64),d(2,34),d(-45,-36),24,-160),Anim.Ease.LINEAR),
        k(.89,w(.4,-.28,-16,10,3,d(2,96),d(4,66),d(-46,-58),24,-160),Anim.Ease.LINEAR),
        k(.905,w(.4,-.28,-20,10,4,d(4,110),d(6,86),d(-46,-70),24,-160),Anim.Ease.OUT),
        k(1.02,w(.38,-.24,-18,8,3,d(6,104),d(10,88),d(-50,-64),22,-162)),
        // (2.79.2: back in the stance as the finisher may begin, 1.25 s, the user's video of the whole combo)
        k(step(3),STANCE)).stops(Anim.stop(blow(3,0),.025,.03),Anim.stop(blow(3,1),.025,.03),Anim.stop(blow(3,2),.025,.03),Anim.stop(blow(3,3),.035,.05)).after(IsosangSlash::grounded);

    // ───────────────────────────── 4 낙월 ─────────────────────────────
    /** The twirl: the arm up over the head, the blade level, turning round it (body-space), the head on the front. */
    static R6.Pose twirl(double az,float yaw){return planted(R6.pose().at(.12,-.03,0).body(yaw,-4,0).head(yaw,-10,0).rArm(80,16).blade(10,az).lArm(-60,-40).rLeg(-88,6).lLeg(-88,-6),30,-150);}
    /** Aimed: turned a little to the right, the free hand pointed at the foe, the sword held level behind at the chest. */
    static final R6.Pose H4_POINT=w(.14,-.2,-28,4,-2,d(-20,140),d(0,176),d(6,-4),26,-160);
    static final R6.Pose H4_POINT2=w(.14,-.22,-30,6,-2,d(-22,142),d(0,178),d(4,-3),26,-160);
    /** Down in a crouch; the lunge, the blade trailing low behind. */
    static final R6.Pose H4_CROUCH=w(.2,-.34,-26,20,-3,d(-40,150),d(-6,172),d(-10,-20),24,-164);
    static final R6.Pose H4_DASH=w(1.03,-.34,-22,28,0,d(-36,146),d(0,170),d(-20,-40),18,-172);
    /** The low cut that throws the slash: from the right behind round the front, low, to the left. */
    static final R6.Pose H4_DRAW=w(1.06,-.34,-16,22,0,d(-18,96),d(-4,120),d(-24,-70),18,-172);
    static final R6.Pose H4_SWING=w(1.07,-.34,-6,21,2,d(-16,40),d(-3,64),d(-24,-90),18,-172);
    static final R6.Pose H4_CUT=w(1.08,-.34,6,20,4,d(-16,-14),d(-3,12),d(-26,-110),18,-172);
    static final R6.Pose H4_ON=w(1.09,-.35,20,20,6,d(-18,-74),d(-4,-48),d(-26,-160),18,-172);
    /** Followed through to the left, low, the free arm thrown back, watching the slash fly. */
    static final R6.Pose H4_END=w(1.09,-.36,28,20,7,d(-28,-92),d(-3,-70),d(-18,150),18,-172);
    static final R6.Pose H4_HOLD=w(1.08,-.35,26,19,6,d(-30,-90),d(-8,-68),d(-20,148),18,-172);
    /** Up on the feet, the sword raised upright at the right side, and lowered. */
    static final R6.Pose H4_RISE=w(.66,-.1,-10,0,0,d(10,70),d(80,70),d(-70,-20),16,-164);
    static final R6.Pose H4_LOWER=w(.28,-.05,-6,2,0,d(-30,40),d(30,30),d(-80,-12),10,-170);
    /**
     * The twirl's keys: the blade's way round over the head in the hit's frame ({@code W}, right +) and the body's turn
     * (left +); the blade's angle off the torso's front is their sum. (2.79.2, the user's video of the whole combo at half
     * speed: the sword goes round to the left, anticlockwise seen from above, right, front, left, behind, in about 0.13 s,
     * and is held back behind for the aim; until 2.79.2 it went round the other way, to the right, over 0.2 s. 2.79.3: the
     * body only turns a little to the right with it, facing the front.)
     */
    static R6.Pose twirlAt(double w,float yaw){return twirl(w+yaw,yaw);}
    static final Anim H4=Anim.joints(
        k(0,STANCE),
        // the twirl: the blade swung up behind at the right and round over the head to the left, a quarter of a turn
        // every 0.03 s, ending held back behind, as the body turns a little to the right
        k(.05,twirlAt(150,-6)),
        k(.08,twirlAt(62,-10),Anim.Ease.LINEAR),
        k(.11,twirlAt(-28,-14),Anim.Ease.LINEAR),
        k(.14,twirlAt(-118,-18),Anim.Ease.LINEAR),
        k(.18,twirlAt(-190,-22)),
        // aimed, down, the lunge, and the low cut round the front that throws the slash (2.79.2: 0.2 s sooner, the
        // video of the whole combo)
        k(.24,H4_POINT),
        k(.34,H4_POINT2),
        k(.385,H4_CROUCH),
        k(.455,H4_DASH,Anim.Ease.OUT),
        k(.475,H4_DRAW),
        k(.49,H4_SWING,Anim.Ease.LINEAR),
        k(blow(4,0),H4_CUT,Anim.Ease.LINEAR),
        k(.52,H4_ON,Anim.Ease.LINEAR),
        k(.58,H4_END,Anim.Ease.OUT),
        k(.9,H4_HOLD),
        // up again unhurried, the sword raised and lowered (the combo is over)
        k(1.15,H4_RISE),
        k(1.35,H4_LOWER),
        k(1.6,STANCE)).stops(Anim.stop(blow(4,0),.07,.12)).after(IsosangSlash::grounded);

    @Override public Anim clip(int combo){return hit(combo);}
    /** Hit {@code combo}'s motion (2.80.0: the empowered first and fourth, IsosangEmpower). */
    static Anim hit(int combo){return switch(combo){case 2->H2;case 3->H3;case 4->H4;case IsosangRules.THROWS->IsosangEmpower.THROWS;case IsosangRules.THRUST->IsosangEmpower.THRUST;default->H1;};}
    /** Whether this swing is drawn by IsosangEmpower (2.80.0): E's flash (0) and the empowered first and fourth. */
    static boolean empowered(int combo){return combo==0||combo==IsosangRules.THROWS||combo==IsosangRules.THRUST;}
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
     * round the right, low over the ground (2.79.2 draft: two cuts to the left, behind then in front). 3 (2.79.2 draft):
     * four cuts, down and back up in the same place, the mirror crossing it, the biggest nearly level. 4: the low cut that throws the slash (the slash
     * itself: {@link #wave}).
     */
    static final Arc[] H1_ARCS={
        // (2.79.2 draft, the user's marks: a crescent, not a circle round them: its thin tip behind, thin along the left,
        // its thickest belly ahead, its bright pointed tip at the right; standing out ahead of them; high at the right,
        // low at the left; at the height of the face)
        arc(v(1.0,1.45,0),25,-90,1.3,2.6,180,450,.25f,.32f,.74f,.9f,.27f,.45f,9,1,2)};
    // (2.79.2 draft) 2: the cut behind them, drawn first at the hip, its plane a little low behind on the right (the video
    // from the front sees it nearly edge on), from the front-right across the front, round the left and behind to the
    // back-right, thickest behind on the left; then the cut in front of them, low over the ground, from behind round the
    // right, across the front to the left, thickest ahead, white while fresh (style 10). Both anticlockwise seen from above.
    static final Arc[] H2_ARCS={
        arc(v(0,.7,0),10,150,1.9,3.15,60,-215,.08f,.12f,.36f,.52f,.13f,.32f,10,3,2),
        arc(v(.3,.15,0),0,0,1.8,2.6,165,-110,.235f,.28f,.52f,.7f,.3f,.5f,10,5,2)};
    // (2.79.2 draft) 3: the first down from her left (high) across the front to her right (low), thick at its start; the
    // second in the same place rising back from her right to her left, thick at its end; the third the mirror of the
    // first (high at her right), thick at its start, crossing the second in an X; the fourth the biggest, nearly level,
    // rising from her left across the front round to her right. The first two lean down to her right (toward 90), the
    // third down to her left, the fourth a little down to her left.
    static final Arc[] H3_ARCS={
        arc(v(.2,1.5,-.2),32,90,1.4,2.7,-150,100,.08f,.12f,.3f,.45f,.2f,.34f,11,15,2),
        arc(v(.2,1.5,-.2),32,90,1.5,2.8,100,-150,.345f,.395f,.72f,.86f,.55f,.75f,9,5,2),
        arc(v(.2,1.55,.2),30,-60,1.6,3.0,150,-100,.54f,.58f,.76f,.88f,.68f,.82f,11,11,2),
        arc(v(.4,1.1,0),10,-90,2.0,3.6,-120,150,.84f,.9f,1.2f,1.38f,1.05f,1.25f,9,9,2)};
    // (2.79.2 draft, the user's videos: the low cut that throws the slash shows only as it is swung; the thrown slash itself
    // carries on, and no second arc lingers round her)
    static final Arc[] H4_ARCS={arc(v(1.0,.72,0),0,0,1.5,2.9,125,-115,.475f,.515f,.52f,.58f,.5f,.56f,0,7,0)};
    static final Arc[] NONE={};
    static Arc[] arcs(int combo){return switch(combo){case 1->H1_ARCS;case 2->H2_ARCS;case 3->H3_ARCS;case 4->H4_ARCS;default->NONE;};}
    /**
     * The slash blow {@code i} of hit {@code combo} lands with (an index into {@link #arcs}). Hit 3 (2.79.2): one blow on
     * each of its four cuts, as it passes the front.
     */
    static int arcOf(int combo,int i){
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
            // hit 1's great crescent (2.79.1): thin only where it began, thick from its middle to its rounded head
            case 8->{double h=Ease.smooth((float)x,.86f,1);yield (.06+.94*Ease.smooth((float)x,0,.5f))*(1-h*h);}
            // hit 1's crescent draft (2.79.2): pointed at both tips, thin along its start, thickest at its belly (70% along)
            case 9,10->Math.pow(Math.max(Math.sin(Math.PI*Math.pow(x,1.94)),0),1.6);
            // hit 3's cuts down (2.79.2 draft): the same crescent thickest near its start
            case 11->Math.pow(Math.max(Math.sin(Math.PI*Math.pow(1-x,1.94)),0),1.6);
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
        if(style==1||style==10){double spread=1-(1-ink)*(1-ink);return .9-.6*spread;}
        return inkFront(prof,ink);
    }
    /** How far in from the outer edge the white-hot rim reaches (0..1 of the band), as the shader has it: the ring's broad while fresh. */
    static double whiteEnd(int style,float ink){
        return style==1||style==10?.6-.52*Ease.smooth(ink,0,.12f):.24-.18*Ease.smooth(ink,0,.3f);
    }

    /**
     * The thrown slash's heights over the ground: its back edge low, its leading edge high (2.78.0, the user's videos: a
     * broad band from above and a tall one from the front, so it leans back from its leading edge, about 35°); and when
     * it turns to ink and is gone (it lingers where its flight ends and dries there).
     */
    static final double WAVE_BACK=.55,WAVE_FRONT=1.05,WAVE_LOOK_DEPTH=1.0;
    // (2.79.2 draft, the user's videos: it dries from the inside a quarter of a second after the throw, is nearly gone by
    // 0.45 s and quite gone by 0.52 s)
    static final float WAVE_INK0=blow(4,0)+.25f,WAVE_INK1=blow(4,0)+.4f,WAVE_EAT0=blow(4,0)+.36f,WAVE_EAT1=blow(4,0)+.52f;
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
     * air, every slash along its arc (drawn on as it sweeps, eaten from the tail as it turns to ink), the thrown slash and the ink thrown up out of whom it strikes.
     */
    @Override public void ink(Fx c){
        if(empowered(c.cast.combo())){IsosangEmpower.ink(c);return;}
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        // the ground's stains first (under everything), the loops of ink in the air, then the slashes
        groundInk(c,t,combo,feet,yaw);
        for(var l:loops(combo))loop(feet,yaw,l,t);
        for(var s:arcs(combo)){
            if(t>=s.eat1())continue;
            var sw=band(feet,yaw,s,t);if(sw==null)continue;
            KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),Ease.smooth(t,s.eat0(),s.eat1()),inks(s,sw,t),Ease.smooth(t,s.t0(),s.t0()+.015f)*shown(sw,c.camera));
        }
        if(combo==4){
            trail(feet,yaw,t);
            var sw=wave(feet,yaw,t);
            if(sw!=null)KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),3,7,Ease.smooth(t,WAVE_EAT0,WAVE_EAT1),Ease.smooth(t,WAVE_INK0,WAVE_INK1),Ease.smooth(t,blow(4,0)-.005f,blow(4,0)+.02f));
            inkUp(c,t);
        }
    }
    /**
     * The thrown slash's trail (2.79.2 draft, the user's videos from above, behind and the front): a wide stroke of dark
     * blue on the ground from just ahead of its user to under the middle of the hanging slash, longest in its middle,
     * streaked the way it flew, turning to ink with the slash.
     */
    static void trail(Vec3d feet,float yaw,float t){
        float at=blow(4,0);if(t<at||t>=WAVE_EAT1)return;
        double ticks=flown(t),grow=Ease.out((float)((t-at)*20),0,1.6f);
        double far=IsosangRules.waveFront(ticks)-IsosangRules.WAVE_DEPTH*WAVE_LOOK_DEPTH*(.7+.3*grow)+.35,near=.45,half=1.0;
        if(far<near+.2)return;
        double floor=.03;int n=9;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){
            double u=-1+2.0*i/(n-1),side=half*u;
            in[i]=world(feet,yaw,v(near,floor,side*.45));out[i]=world(feet,yaw,v(far,floor,side));along[i]=i/(float)(n-1);
        }
        // (2.79.2, the video of the whole combo: deep blue as it is laid, not light; black as it dries)
        KagenoFire.inkRibbon(in,out,along,n,3,11,Ease.smooth(t,WAVE_EAT0,WAVE_EAT1),.25f+.75f*Ease.smooth(t,WAVE_INK0-.05f,WAVE_INK1),.85f*Ease.smooth(t,at+.02f,at+.08f));
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
     * A splash of ink on the ground at {@code p} (the ink pass's style 12; 2.79.2, the user's video of the whole combo at
     * half speed: big see-through black splashes with sharp edges, arms flung out, holes and drops round them, where the
     * soft blots were too faint and grey): {@code size} across, turned {@code angle} (radians), stretched {@code stretch}
     * times along it, dried {@code dry} (0 wet, 1 gone).
     */
    static void splat(Vec3d p,double size,double angle,double stretch,int seed,float dry,float alpha){
        if(alpha<=.01f||size<=.01||dry>=1)return;
        Vec3d a=new Vec3d(Math.cos(angle),0,Math.sin(angle)).multiply(size*stretch*.5),b=new Vec3d(-Math.sin(angle),0,Math.cos(angle)).multiply(size*.5);
        KagenoFire.inkRibbon(new Vec3d[]{p.subtract(a).subtract(b),p.add(a).subtract(b)},new Vec3d[]{p.subtract(a).add(b),p.add(a).add(b)},new float[]{0,1},2,12,seed,dry,0,alpha);
    }
    /**
     * How long a splash lasts (2.79.2, the video of the whole combo: it spreads in a blink, stays dark a good while, and
     * breaks up and fades from about half a second, gone by 1.2 s).
     */
    static final float SPLAT_LIFE=1.2f;
    /** A splash {@code age} seconds after the ink landed (nothing before it lands or once it has dried). */
    static void splatAt(Vec3d p,double size,double angle,double stretch,int seed,float age,float alpha){
        if(age<0||age>=SPLAT_LIFE)return;
        float dry=Ease.smooth(age,.45f*SPLAT_LIFE,SPLAT_LIFE);
        splat(p,size*(.55+.45*Ease.out(age,0,.07f)),angle,stretch,seed&15,dry,alpha*(1-Ease.smooth(dry,.35f,1)));
    }
    /**
     * Splashes of ink flung onto the ground round slash {@code s} as it turns to ink (2.79.2, the video of the whole
     * combo): {@code n} of them from its angle {@code from} to {@code to} (its own degrees), out from its middle to past its
     * outer edge, each landing as the ink reached it, {@code delay} after the blade, {@code size} across.
     */
    static void splats(Vec3d feet,float yaw,Arc s,double from,double to,int n,float delay,double size,float alpha,int seed0,float t){
        double floor=feet.y+.02;
        for(int k=0;k<n;k++){
            int seed=k*43+seed0;double f=(k+.5+.5*(Ease.hash(seed)-.5))/n,deg=from+(to-from)*f;
            double along=(deg-s.from())/(s.to()-s.from());if(along<0||along>1)continue;
            float born=s.t0()+(s.t1()-s.t0())*(float)along+delay+.12f*(float)Ease.hash(seed+1);
            if(t<born)continue;
            double rad=s.inner()+(s.outer()-s.inner())*(.4+.9*Ease.hash(seed+2));
            Vec3d q=point(s,deg,rad),p=world(feet,yaw,v(q.x,0,q.z));
            splatAt(new Vec3d(p.x,floor+.002*(k%5),p.z),size*(.7+.6*Ease.hash(seed+3)),Math.PI*2*Ease.hash(seed+4),1+.5*Ease.hash(seed+5),seed,t-born,alpha);
        }
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
    // (2.79.2 draft, the user's videos: the thin ring of ink is drawn round them at the waist, close to the body, as the
    // slash is thrown, about twice her height across, and stays a little under half a second; 2.78.0 had it on the ground)
    static final Loop[] H4_LOOPS={new Loop(v(0,.9,0),0,0,2.1,1,.08,-200,160,.49f,.52f,.8f,.98f,6)};
    /** Hit 2's (2.79.2 draft): the thin line of ink the point leaves round them at the waist (the videos' black circle inside the cuts). */
    static final Loop[] H2_LOOPS={new Loop(v(0,.95,0),10,150,1.5,1,.05,20,-400,.2f,.27f,.42f,.55f,4)};
    static Loop[] loops(int combo){return combo==4?H4_LOOPS:combo==2?H2_LOOPS:new Loop[0];}
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
            // (2.79.2 draft) the ground inside darkens for a moment under the front cut (the videos), then dries
            var b=H2_ARCS[1];float dark=t-(b.t0()+.02f);
            if(dark>=0&&dark<.45f){
                Vec3d o=world(feet,yaw,v(b.centre().x,0,0));float dry=Ease.smooth(dark,.15f,.45f);
                stain(new Vec3d(o.x,floor-.002,o.z),4.6*(.7+.3*Ease.out(dark,0,.12f)),Math.toRadians(-yaw),1,9,dry,.42f*(1-Ease.smooth(dry,.6f,1)));
            }
            // (2.79.2, the video of the whole combo) splashes of ink flung out round the ring as its cuts dry: the cut
            // behind at her left and behind, the cut in front at her right and ahead
            splats(feet,yaw,H2_ARCS[0],-20,-230,5,.18f,2.4,.85f,521,t);
            splats(feet,yaw,H2_ARCS[1],140,-60,4,.16f,2.2,.85f,547,t);
        }
        if(combo==3){
            // (2.79.2 draft) under the low ends of the cuts: the first two at her right, the third at her left, the fourth
            // all along the front (the video from above: the ground spattered with ink by the end)
            pool(feet,yaw,H3_ARCS[0],40,100,4,.08f,1.2,.8f,577,t);
            pool(feet,yaw,H3_ARCS[2],-40,-100,4,.06f,1.3,.85f,601,t);
            pool(feet,yaw,H3_ARCS[3],-60,120,8,.06f,1.6,.85f,631,t);
            // (2.79.2, the video of the whole combo: by the X and the great fourth cut the ground round her is splashed
            // black all over, the splashes big and sharp-edged, lasting into the finisher)
            splats(feet,yaw,H3_ARCS[0],-120,90,4,.14f,2.6,.85f,661,t);
            splats(feet,yaw,H3_ARCS[1],80,-130,4,.2f,2.6,.85f,683,t);
            splats(feet,yaw,H3_ARCS[2],130,-90,5,.12f,2.8,.88f,709,t);
            splats(feet,yaw,H3_ARCS[3],-110,140,7,.1f,3.2,.88f,739,t);
        }
        if(combo==4){
            float at=blow(4,0);
            // (2.79.2 draft, the user's video from above: the ground along its trail and under it is left black with ink)
            for(int k=0;k<40;k++){
                int seed=k*37+733;
                double fw=.8+(IsosangRules.WAVE_RANGE-1.2)*Ease.hash(seed),u=(Ease.hash(seed+1)-.5)*2*(.35+.65*Ease.smooth((float)(fw/IsosangRules.WAVE_RANGE),.2f,1f));
                // the slash's back edge passes over it: when its leading edge is DEPTH past it (2.79.2 draft: where it
                // hangs, its trail soaks into the ground as it dries)
                float when=reaches(Math.min(IsosangRules.WAVE_RANGE-.05,fw+IsosangRules.WAVE_DEPTH*.8),0,0);if(when<0)when=at+.15f;when=Math.max(when,WAVE_INK0-.1f);
                double side=u*IsosangRules.waveHalf(Math.min(IsosangRules.WAVE_TICKS,(when-at)*20))*.8;
                Vec3d p=world(feet,yaw,v(fw,0,side));
                stainAt(new Vec3d(p.x,floor+.003*(k%4),p.z),1.0+1.2*Ease.hash(seed+6),Math.toRadians(yaw+90)+(Ease.hash(seed+5)-.5)*.6,1.5+1.5*Ease.hash(seed+7),seed,t-when-.04f*(float)Ease.hash(seed+2),.88f);
                // (2.79.2, the video of the whole combo: the trail's ground is splashed black, sharp-edged, a good while)
                if(k%3==0)splatAt(new Vec3d(p.x,floor+.004+.002*(k%5),p.z),1.9+1.6*Ease.hash(seed+8),Math.PI*2*Ease.hash(seed+9),1.2+.6*Ease.hash(seed+10),seed,t-when-.06f*(float)Ease.hash(seed+3),.85f);
            }
            // the ground inside the ring of ink darkens while it lasts (the video of the whole combo)
            var ring=H4_LOOPS[0];float dk=t-ring.d0();
            if(dk>=0&&dk<.6f){float dry=Ease.smooth(dk,.3f,.6f);stain(new Vec3d(feet.x,floor-.002,feet.z),ring.radius()*2.1*(.75+.25*Ease.out(dk,0,.08f)),Math.toRadians(-yaw),1,13,dry,.4f*(1-Ease.smooth(dry,.6f,1)));}
            // round whom it strikes, where it struck them
            int b=0;
            for(var e:struck(c,0,anchor(c))){
                float hit=strikeAt(c,0,e);b++;Vec3d o=reachedAt(c,e,hit);
                for(int k=0;k<3;k++){
                    int seed=b*97+k*13;double az=Math.PI*2*Ease.hash(seed),r=.35*k;
                    stainAt(new Vec3d(o.x+Math.cos(az)*r,floor+.004*k,o.z+Math.sin(az)*r),1.3+.9*Ease.hash(seed+1),az,1.2+.6*Ease.hash(seed+2),seed,t-hit-.1f-.06f*k,.9f);
                    splatAt(new Vec3d(o.x+Math.cos(az)*r*1.6,floor+.006+.002*k,o.z+Math.sin(az)*r*1.6),2.0+1.2*Ease.hash(seed+3),az+1,1.1+.5*Ease.hash(seed+4),seed+5,t-hit-.08f-.05f*k,.88f);
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
            // (2.79.2: the bigger drops splash where they land, the video of the whole combo's spatters)
            if(d.size()>.08)splatAt(new Vec3d(p.x,floor+.003+.001*(d.seed()%5),p.z),d.size()*9,Math.atan2(mv.z,mv.x),1+Math.min(1.2,Math.hypot(mv.x,mv.z)*.25),d.seed()+3,age,.8f);
        }
    }

    // ───────────────────────────── light ─────────────────────────────

    /** Brightness of the blade's aura: lit for the cuts, flaring at each blow; the finisher lights it from the twirl. */
    static float glow(float t,int combo){
        float g=switch(combo){
            case 2->Ease.window(t,.06f,.36f,.04f,.12f);
            case 3->Ease.window(t,.06f,.95f,.04f,.12f);
            case 4->Ease.window(t,.04f,.6f,.04f,.12f);
            default->Ease.window(t,.02f,.48f,.03f,.12f);
        };
        for(int i=0;i<IsosangRules.blows(combo);i++)g+=.8f*FxLib.kick(t,blow(combo,i),.07f);
        return g;
    }
    @Override public void additive(Fx c,EffectMesh m){
        if(empowered(c.cast.combo())){IsosangEmpower.additive(c,m);return;}
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        aura(c,m,t,combo);
        for(var s:arcs(combo)){var sw=band(feet,yaw,s,t);if(sw!=null&&t<s.eat1())edgeGlow(m,sw,s.style(),edgeLit(s,t),inks(s,sw,t));}
        if(combo<4)for(var s:arcs(combo))sweep(m,feet,yaw,s,t);
        sparks(c,m,t,combo,feet,yaw);
        spray(m,t,combo,feet,yaw);
        impacts(c,m,t,combo);
        if(combo==1)glint(c,m,t);
        if(combo==2)flare(c,m,t,feet,yaw);
        if(combo==4){twirl(c,m,t);point(c,m,t);dash(c,m,t);waveLight(c,m,t,feet,yaw);}
    }
    /**
     * The light the blade leaves across what it swept (2.79.2, the user's video of the whole combo: a pale see-through fan
     * inside each cut as it is drawn, from close round them out to the cut, gone within a tenth of a second; not a curtain
     * from the ground, not a fan of spokes): the last 0.05 s of its sweep, brightest at its head and out at the cut.
     */
    static void sweep(EffectMesh m,Vec3d feet,float yaw,Arc s,float t){
        if(t<s.t0()||t>=s.t1()+.1f)return;
        float fade=1-Ease.smooth(t,s.t1(),s.t1()+.1f);if(fade<=.01f)return;
        double d1=s.from()+(s.to()-s.from())*head(s,t),d0=s.from()+(s.to()-s.from())*head(s,t-.05f);
        if(Math.abs(d1-d0)<1)return;
        int n=Math.max(4,(int)Math.ceil(Math.abs(d1-d0)/6));double r0=s.inner()*.3,r1=s.inner()+(s.outer()-s.inner())*.15;
        for(int i=0;i<n;i++){
            double a=d0+(d1-d0)*i/n,b=d0+(d1-d0)*(i+1)/n;float ua=i/(float)n,ub=(i+1)/(float)n;
            Vec3d ia=world(feet,yaw,point(s,a,r0)),oa=world(feet,yaw,point(s,a,r1)),ib=world(feet,yaw,point(s,b,r0)),ob=world(feet,yaw,point(s,b,r1));
            m.vertex(ia,PALE,0);m.vertex(ib,PALE,0);m.vertex(ob,CYAN,.28f*fade*ub*ub);m.vertex(oa,CYAN,.28f*fade*ua*ua);
        }
    }
    @Override public void alpha(Fx c,EffectMesh m){
        if(empowered(c.cast.combo())){IsosangEmpower.alpha(c,m);return;}
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        inkBursts(c,m,t,combo);
        airInk(c,m,t,combo,feet,yaw);
    }
    /**
     * Over the ink ribbons (2.77.5, user request "마지막으로 이펙트 더 예쁘게 꾸며줘": the moon's light and its stars, as the
     * sword has them and as the hits are named): the starlight on each slash, a glint racing at each one's head, the ring's
     * burst of stars, the stars along the thrust, and the moon riding the thrown slash with its trail of stardust.
     */
    @Override public void overlay(Fx c,EffectMesh m){
        if(empowered(c.cast.combo())){IsosangEmpower.overlay(c,m);return;}
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        for(var s:arcs(combo))for(var st:stars(s,t)){
            Vec3d p=world(feet,yaw,point(s,st.deg(),st.radius()));
            int col=st.night()?(st.seed()%3==0?LILAC:PALE):(st.seed()%3==0?WHITE:st.seed()%3==1?PALE:LILAC);
            glint8(m,p,st.size(),col,st.alpha());
        }
        heads(m,t,combo,feet,yaw);
        if(combo==4)waveStars(m,t,feet,yaw);
    }
    /**
     * The blade's blue aura while it cuts, brightest as it strikes: kept close round the blade (2.77.4, user request "지금
     * 평타 너무 가시성이 안 좋은데": a wide haze round it clouded them).
     */
    static void aura(Fx c,EffectMesh m,float t,int combo){
        // (2.80.0) brighter in the empowered combo (E): its second and third look as they do, the blade lit more
        float g=glow(t,combo)*(IsosangClient.empowered(c.cast.id())?1.4f:1);if(g<=.01f)return;
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
     * Hit 2's front cut (2.79.2 draft, the videos): as it is drawn a wide sheet of white light spreads over the ground
     * inside it, strongest ahead of them, gone within a fifth of a second.
     */
    static void flare(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        var b=H2_ARCS[1];float t0=b.t0();if(t<t0||t>=t0+.3f)return;
        float a=Ease.smooth(t,t0,t0+.03f)*(1-Ease.smooth(t,t0+.08f,t0+.3f)),h=head(b,t);
        int n=18;Vec3d[] o=new Vec3d[n+1],mid=new Vec3d[n+1],in=new Vec3d[n+1];float[] wgt=new float[n+1];
        for(int i=0;i<=n;i++){
            double deg=b.from()+(b.to()-b.from())*h*i/n;
            Vec3d q0=point(b,deg,b.inner()+.25),q1=point(b,deg,b.inner()-.45),q2=point(b,deg,Math.max(.4,b.inner()-1.2));
            o[i]=world(feet,yaw,v(q0.x,.05,q0.z));mid[i]=world(feet,yaw,v(q1.x,.05,q1.z));in[i]=world(feet,yaw,v(q2.x,.05,q2.z));
            wgt[i]=(float)(.35+.65*Math.max(0,Math.cos(Math.toRadians(deg))));
        }
        for(int i=0;i<n;i++){
            float w=a*(wgt[i]+wgt[i+1])*.5f;
            m.triangle(o[i],o[i+1],mid[i+1],PALE,.34f*w);m.triangle(o[i],mid[i+1],mid[i],PALE,.34f*w);
            m.triangle(mid[i],mid[i+1],in[i+1],AZURE,.14f*w);m.triangle(mid[i],in[i+1],in[i],AZURE,.14f*w);
        }
    }
    /**
     * The finisher's twirl: a broad band of pale, see-through light over the head along the point's path, left hanging a
     * fifth of a second after the blade (2.78.0, the user's video from above: a wide faint crescent, not a thin bright
     * line, still there as they aim).
     */
    static void twirl(Fx c,EffectMesh m,float t){
        if(t<.04f||t>=.42f)return;
        float from=Math.max(.04f,t-.13f),head=Math.min(t,.19f);if(head<=from+.004f)return;
        float a=.9f*(1-Ease.smooth(t,.28f,.42f));int n=24;Vec3d prev=null;
        for(int i=0;i<=n;i++){
            float s=from+(head-from)*i/n;var hb=blade(c,4,s);Vec3d p=hb[0].add(hb[1].multiply(TIP*1.06));
            if(prev!=null){float u=i/(float)n;m.softLine(prev,p,.8,CYAN,a*u*.3f);m.softLine(prev,p,.35,PALE,a*u*.35f);m.line(prev,p,.1,PALE,a*u*.45f);}
            prev=p;
        }
    }
    /** The finisher's aim: a faint blue light along the blade held back (2.78.0: no stars at the hand or the point; the video has none). */
    static void point(Fx c,EffectMesh m,float t){
        if(t<.2f||t>=.44f)return;
        float a=Ease.smooth(t,.2f,.26f)*(1-Ease.smooth(t,.38f,.44f));
        var hb=bladeNow(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));
        m.softLine(guard,tip,.3,AZURE,.3f*a);m.softDisc(tip,.16,PALE,.5f*a);
    }
    /** The finisher's lunge: streaks of air left behind along the way it went. */
    static void dash(Fx c,EffectMesh m,float t){
        if(t<.42f||t>=.62f)return;
        Vec3d f=Vec3d.fromPolar(0,c.cast.yaw());Vec3d side=f.crossProduct(UP).normalize();
        for(int k=0;k<6;k++){
            float born=.42f+.06f*(float)Ease.hash(k*7+3),age=t-born;if(age<0||age>=.18f)continue;
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
     * Lights: the blade's aura, every struck target's flash, each cut's flash, the thrown slash flying;
     * afterglow cut short for the quick parts. Kept low (2.77.4: they washed the floor round them white) and no shaft of
     * light as the slash is thrown (it stood over them as a column).
     */
    @Override public void frame(Fx c,FxFrame f){
        if(empowered(c.cast.combo())){IsosangEmpower.frame(c,f);return;}
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        float g=Math.min(1.3f,glow(t,combo));
        if(g>.02f){var hb=bladeNow(c);f.light(hb[0].add(hb[1].multiply(.9)),AURA[1],4,.4f*g);}
        for(var w:strikes(c,combo)){float k=FxLib.kick(t,w.when(),.08f);if(k>.01f)f.light(w.at(),0xcfe4ff,combo==4?6:5,(combo==4?1.1f:.85f)*k);}
        if(combo==1){float k=FxLib.kick(t,.25f,.12f);if(k>.01f)f.light(world(feet,yaw,v(.4,1.6,-.4)),0x7fb0ff,7,.7f*k);}
        if(combo==2){
            float ka=FxLib.kick(t,H2_ARCS[0].t1(),.14f),kb=FxLib.kick(t,H2_ARCS[1].t0()+.02f,.16f);
            if(ka>.01f)f.light(world(feet,yaw,v(0,.8,0)),0x8cbcff,7,.6f*ka);
            if(kb>.01f)f.light(world(feet,yaw,v(1.2,.3,0)),0xb8d8ff,8,.9f*kb);
        }
        if(combo==3){
            for(var s:H3_ARCS){float k=FxLib.kick(t,s.t0()+.02f,.12f);if(k>.01f)f.light(world(feet,yaw,point(s,(s.from()+s.to())*.5,(s.inner()+s.outer())*.5)),0x7fb0ff,7,.6f*k);}
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
        // (2.79.0-1: hit 1's quick wind into the spin leaves no fan of blade copies; 2.79.2: nor hit 2's two quick cuts, nor
        // hit 4's twirl, the video of the whole combo has no spokes)
        if(t<IsosangRules.LENGTH[combo]/20f)f.afterglow(combo==2&&t>=.06f&&t<.32f?.12f:combo==1&&t<.34f?.1f:combo==4&&t<.24f?.08f:.3f);
    }
    /** The air bends a little along each slash as it sweeps, along the thrown slash as it flies, and in a ring round the ring's burst. */
    @Override public void distort(Fx c,FxDistort d){
        if(empowered(c.cast.combo())){IsosangEmpower.distort(c,d);return;}
        float t=c.sec;int combo=c.cast.combo();Vec3d feet=anchor(c);float yaw=c.cast.yaw();
        for(var s:arcs(combo)){
            var sw=band(feet,yaw,s,t);if(sw==null||t>=s.t1()+.18f)continue;
            d.ribbon(sw.inner(),sw.outer(),sw.n(),.25f*Ease.smooth(t,s.t0(),s.t0()+.03f)*(1-Ease.smooth(t,s.t1(),s.t1()+.18f)));
        }
        if(combo==4){
            var sw=wave(feet,yaw,t);float at=blow(4,0);
            if(sw!=null&&t<at+.5f)d.ribbon(sw.inner(),sw.outer(),sw.n(),.3f*Ease.smooth(t,at,at+.03f)*(1-Ease.smooth(t,at+.3f,at+.5f)));
        }
    }

    // ───────────────────────────── camera ─────────────────────────────

    /** Shake as the blows land, harder for the thrust, the cut down from overhead and the thrown slash; each comes in over two frames. */
    @Override public float shake(StandClient.Cast cast,float sec){
        if(empowered(cast.combo()))return IsosangEmpower.shake(cast,sec);
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
        // (2.80.0) the empowered fourth's giant sword: drawn back for it too
        if(combo==IsosangRules.THRUST)return IsosangEmpower.pull(sec);
        if(combo!=4)return 0;
        return PULL*Ease.smooth(sec,.35f,.68f)*(1-Ease.smooth(sec,1.2f,1.6f));
    }
}
