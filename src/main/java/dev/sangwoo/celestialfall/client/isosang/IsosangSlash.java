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
 * 들어있어 영상에"): the body's four hits after the user's video and their slashes. Every hit is a swing of its own (its
 * clock the server's, IsosangClient), its blows on the server's ticks (IsosangRules).
 * <ol>
 * <li>참월(斬月): the sword raised upright, levelled at the foe with a step in, cocked over the right shoulder and brought
 * down across the front to the left hip: a great diagonal crescent.</li>
 * <li>윤월(輪月): the blade held out across to the left and a whole turn to the right on the spot: a ring all round,
 * which falls to the ground as a ring of ink.</li>
 * <li>관월(貫月): drawn back side-on and driven straight through ahead, a beam of light running on from the point; pulled
 * back with a twist, two cuts crossing in an X and a rising sweep.</li>
 * <li>낙월(落月): the sword twirled over the head, the free hand pointed at the foe, a lunge and a great level cut across
 * the front that bursts into ink: a ring of it on the ground and a spray of blots.</li>
 * </ol>
 * The slashes are blue light outlined in ink (KagenoFire's ink ribbons: a white cutting edge, a vivid blue body, deep blue
 * to ink on the inner side), drawn along the blade's own path; as they fade they turn to ink, break up and shed blots
 * that fly off and fall. Every effect is a function of the swing's clock, so it moves at the render frame rate.
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

    /**
     * How far ahead each hit's body stands (its root, blocks) as the next may begin (IsosangRules.STEP): the next hit's
     * clip begins there, so a combo kept at full speed hands over without the body sliding back between hits; the
     * body comes back to the feet as the combo's last clip settles.
     */
    static final double CARRY1=.38,CARRY2=.28,CARRY3=.555;

    // ───────────────────────────── 1 참월 ─────────────────────────────
    /** The sword raised upright before the chest. */
    static final R6.Pose H1_UP=w(.02,-.02,-6,0,0,d(-18,26),d(84,14),d(-78,-18),d(-86,6),d(-88,-6));
    /** Levelled at the foe as they step in, low. */
    static final R6.Pose H1_AIM=w(.2,-.12,-20,8,0,d(-6,24),d(3,3),d(-58,-42),d(-66,16),d(-80,186));
    /** Cocked over the right shoulder, pointing back and up, the hips turned right. */
    static final R6.Pose H1_COCK=w(.26,-.06,-30,-4,0,d(64,30),d(28,164),d(-42,-62),d(-64,16),d(-76,188));
    static final R6.Pose H1_LOAD=w(.27,-.05,-33,-6,0,d(68,34),d(26,172),d(-40,-66),d(-64,16),d(-76,188));
    /** Coming over the right shoulder into the front. */
    static final R6.Pose H1_OVER=w(.3,-.1,-12,4,0,d(50,44),d(44,70),d(-36,-90),d(-62,16),d(-74,188));
    /** The cut: down across the front to the left, a deep step in. */
    static final R6.Pose H1_CUT=w(.36,-.2,22,16,-2,d(0,-14),d(-18,-40),d(-30,-148),d(-56,16),d(-70,190));
    /** Followed through low behind the left. */
    static final R6.Pose H1_END=w(.38,-.22,36,18,-3,d(-28,-40),d(-26,-74),d(-36,-160),d(-54,16),d(-68,190));
    static final Anim H1=Anim.joints(
        k(0,STANCE),
        k(.06,H1_UP),
        k(.13,H1_AIM),
        k(.2,H1_COCK),
        // the coil keeps winding (a moving hold); then the blade comes over the shoulder and down across the front
        k(.225,H1_LOAD),
        k(.26,H1_OVER,Anim.Ease.LINEAR),
        k(blow(1,0),H1_CUT,Anim.Ease.LINEAR),
        k(.37,H1_END,Anim.Ease.OUT),
        k(.52,w(.37,-.21,33,17,-3,d(-30,-38),d(-28,-72),d(-38,-156),d(-55,16),d(-69,190))),
        k(.8,STANCE)).stops(Anim.stop(blow(1,0),.05,.09));

    // ───────────────────────────── 2 윤월 ─────────────────────────────
    // Body-space keys: the whole turn (to the right) is the root's yaw; the arm, held across to the left, rides with it.
    /** Wound up: the hips turned left, the sword arm across the body and the blade out to the left behind, low. */
    static final R6.Pose H2_WIND=R6.pose().at(.34,-.16,0).body(46,12,0).head(-30,-8,0).rArm(-22,-78).blade(-6,-122).lArm(-52,62).rLeg(-76,30).lLeg(-80,-26);
    /** Turning: the blade held out level to the left of the body, which turns under it. */
    static R6.Pose spin(double fw,float yaw){return R6.pose().at(fw,-.14,0).body(yaw,9,0).head(-12,-6,0).rArm(-8,-58).blade(2,-94).lArm(-40,120).rLeg(-78,28).lLeg(-78,-28);}
    /** Round again, a little past the front, the blade coming down to rest out at the left. */
    static final R6.Pose H2_END=R6.pose().at(.26,-.1,0).body(-372,6,0).head(8,-6,0).rArm(-34,-42).blade(-26,-72).lArm(-62,40).rLeg(-80,20).lLeg(-82,-20);
    static final Anim H2=Anim.joints(
        k(0,STANCE.at(CARRY1,0,0)),
        k(.1,H2_WIND),
        // the turn: an even speed from the wind-up round the front (the blow) and on round behind
        k(.17,spin(.32,4),Anim.Ease.IN),
        k(.235,spin(.31,-46),Anim.Ease.LINEAR),
        k(blow(2,0),spin(.3,-94),Anim.Ease.LINEAR),
        k(.36,spin(.3,-184),Anim.Ease.LINEAR),
        k(.42,spin(.29,-274),Anim.Ease.LINEAR),
        k(.48,spin(.28,-346),Anim.Ease.LINEAR),
        k(.58,H2_END,Anim.Ease.OUT),
        // a whole turn on: the stance faces the same way as the start
        k(.8,STANCE.body(-360,0,0))).stops(Anim.stop(blow(2,0),.03,.06));

    // ───────────────────────────── 3 관월 ─────────────────────────────
    /** Drawn back side-on: the hand at the right hip, the point level at the foe, the free hand aiming along it. */
    static final R6.Pose H3_DRAW=w(.1,-.2,-40,6,0,d(-48,164),d(2,3),d(-8,-10),d(-60,20),d(-76,190));
    static final R6.Pose H3_LOAD=w(.04,-.26,-48,4,0,d(-46,172),d(3,2),d(-4,-6),d(-60,22),d(-76,192));
    /** The thrust: the sword shoulder driven through in a long lunge, the free arm thrown back. */
    static final R6.Pose H3_THRUST=w(.62,-.27,42,12,0,d(-3,-6),d(0,-2),d(-24,-172),d(-50,16),d(-58,192));
    static final R6.Pose H3_HELD=w(.68,-.28,46,13,0,d(-2,-5),d(1,-2),d(-26,-174),d(-50,16),d(-58,192));
    /** Pulled back with a twist, the sword going up to the right for the first cut of the cross. */
    static final R6.Pose H3_TWIST=w(.52,-.18,-8,6,0,d(36,42),d(50,58),d(-40,-120),d(-56,16),d(-66,190));
    /** The cross's first cut passing the front, down to the left; through low behind the left. */
    static final R6.Pose H3_X1=w(.54,-.22,14,12,-1,d(4,-4),d(-14,-26),d(-34,-140),d(-54,16),d(-68,190));
    static final R6.Pose H3_X1_END=w(.56,-.24,26,16,-2,d(-22,-30),d(-24,-58),d(-34,-150),d(-54,16),d(-68,190));
    /** Brought round up the left for the second. */
    static final R6.Pose H3_UPLEFT=w(.54,-.16,18,4,0,d(48,-26),d(52,-62),d(-40,-110),d(-56,16),d(-68,190));
    /** The second cut passing the front, down to the right across the first; through low behind the right. */
    static final R6.Pose H3_X2=w(.56,-.22,-12,12,1,d(4,8),d(-14,28),d(-34,-110),d(-54,16),d(-68,190));
    static final R6.Pose H3_X2_END=w(.58,-.24,-24,16,2,d(-20,34),d(-24,60),d(-34,-100),d(-54,16),d(-68,190));
    /** Coiled low to the right behind for the sweep. */
    static final R6.Pose H3_COIL=w(.5,-.26,-38,10,0,d(-30,96),d(-16,124),d(-36,-60),d(-56,16),d(-70,190));
    /** The sweep passing the front, rising; on up to high over the left shoulder. */
    static final R6.Pose H3_SWEEP=w(.54,-.18,4,6,0,d(14,10),d(18,-6),d(-30,-110),d(-58,16),d(-70,190));
    static final R6.Pose H3_HIGH=w(.56,-.12,30,-2,0,d(50,-32),d(56,-56),d(-30,-140),d(-58,16),d(-70,190));
    static final Anim H3=Anim.joints(
        k(0,STANCE.at(CARRY2,0,0)),
        k(.12,H3_DRAW),
        k(.19,H3_LOAD),
        k(blow(3,0),H3_THRUST,Anim.Ease.IN),
        // held out along the thrust while the beam runs on, then pulled back up to the right
        k(.31,H3_HELD,Anim.Ease.OUT),
        k(.385,H3_TWIST),
        k(blow(3,1),H3_X1,Anim.Ease.LINEAR),
        k(.475,H3_X1_END,Anim.Ease.OUT),
        k(.515,H3_UPLEFT),
        k(blow(3,2),H3_X2,Anim.Ease.LINEAR),
        k(.575,H3_X2_END,Anim.Ease.OUT),
        k(.64,H3_COIL),
        k(blow(3,3),H3_SWEEP,Anim.Ease.LINEAR),
        k(.76,H3_HIGH,Anim.Ease.OUT),
        k(.86,w(.55,-.11,32,-4,0,d(54,-34),d(60,-58),d(-30,-142),d(-60,16),d(-70,190))),
        k(1.1,STANCE)).stops(Anim.stop(blow(3,0),.035,.03),Anim.stop(blow(3,3),.04,.06));

    // ───────────────────────────── 4 낙월 ─────────────────────────────
    /** The sword raised over the head for the twirl, the blade pointing out level. */
    static R6.Pose twirl(double az){return w(.45,0,0,-4,0,d(80,16),d(12,az),d(-70,-30),d(-88,6),d(-88,-6));}
    /** Aimed: the free hand pointed at the foe, the sword drawn low behind on the right, the hips turned right, low. */
    static final R6.Pose H4_POINT=w(.3,-.2,-34,6,0,d(-52,150),d(-10,164),d(6,-4),d(-64,18),d(-80,188));
    static final R6.Pose H4_CROUCH=w(.36,-.3,-38,10,0,d(-50,152),d(-8,166),d(4,-2),d(-58,18),d(-76,190));
    /** The lunge: the body thrown forward, the blade trailing back. */
    static final R6.Pose H4_DASH=w(.82,-.26,-40,22,0,d(-40,150),d(-2,170),d(-10,-30),d(-48,16),d(-62,194));
    /** The great level cut: from the right behind round the front to the left. */
    static final R6.Pose H4_DRAW=w(.86,-.24,-22,18,0,d(-6,78),d(2,122),d(-20,-70),d(-50,16),d(-62,194));
    static final R6.Pose H4_SWEEP=w(.88,-.24,4,16,0,d(0,40),d(4,56),d(-24,-110),d(-50,16),d(-62,194));
    static final R6.Pose H4_CUT=w(.9,-.25,36,16,0,d(0,-14),d(2,-10),d(-26,-150),d(-50,16),d(-62,194));
    static final R6.Pose H4_ON=w(.92,-.26,60,17,0,d(-6,-62),d(-2,-82),d(-24,-170),d(-50,16),d(-62,194));
    /** Followed through behind the left, low, the free arm thrown back: the finishing pose. */
    static final R6.Pose H4_END=w(.92,-.3,72,18,0,d(-26,-100),d(-20,-140),d(-18,150),d(-48,16),d(-62,194));
    static final Anim H4=Anim.joints(
        k(0,STANCE.at(CARRY3,0,0)),
        k(.05,w(.5,0,0,-4,0,d(70,14),d(84,10),d(-72,-26),d(-88,6),d(-88,-6))),
        // the twirl: a whole turn of the blade over the head, a quarter every 0.035 s
        k(.085,twirl(0)),
        k(.12,twirl(90),Anim.Ease.LINEAR),
        k(.155,twirl(180),Anim.Ease.LINEAR),
        k(.19,twirl(270),Anim.Ease.LINEAR),
        k(.225,twirl(360)),
        k(.29,H4_POINT),
        k(.36,H4_CROUCH),
        k(.42,H4_DASH,Anim.Ease.OUT),
        k(.455,H4_DRAW),
        k(.475,H4_SWEEP,Anim.Ease.LINEAR),
        k(blow(4,0),H4_CUT,Anim.Ease.LINEAR),
        k(.53,H4_ON,Anim.Ease.LINEAR),
        k(.62,H4_END,Anim.Ease.OUT),
        k(.92,w(.91,-.29,70,17,0,d(-28,-98),d(-22,-138),d(-20,148),d(-49,16),d(-62,194))),
        // held, then back on the feet unhurried: the lunge's step taken back (the combo is over)
        k(1.05,w(.88,-.27,66,15,0,d(-32,-92),d(-26,-130),d(-26,140),d(-52,16),d(-64,192))),
        k(1.5,STANCE)).stops(Anim.stop(blow(4,0),.07,.12));

    @Override public Anim clip(int combo){return hit(combo);}
    static Anim hit(int combo){return switch(combo){case 2->H2;case 3->H3;case 4->H4;default->H1;};}
    @Override public float lifetime(){return 1.9f;}
    @Override public boolean layered(){return true;}
    @Override public int afterimageColor(){return 0x5a8cff;}

    // ───────────────────────────── where things are ─────────────────────────────

    /** Sword hand and blade direction at clip time {@code sec} of hit {@code combo}, on the caster as drawn. */
    static Vec3d[] blade(Fx c,int combo,float sec){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(hit(combo).frame(sec)));}
    static Vec3d[] bladeNow(Fx c){return blade(c,c.cast.combo(),c.sec);}
    /** The same, standing at {@code feet} facing {@code yaw} (for the tests). */
    static Vec3d[] blade(Vec3d feet,float yaw,int combo,float sec){return KagenoClips.blade(feet,yaw,R6.poseOf(hit(combo).frame(sec)));}

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
    /** The caster's floor (under the cast's start; they stand where they are while a hit holds them). */
    static Vec3d floor(Fx c,Vec3d p){return new Vec3d(p.x,c.pos.y+.02,p.z);}

    // ───────────────────────────── the slashes ─────────────────────────────

    /**
     * A slash swept by the blade: drawn along its path from clip time {@code from}, its head riding the blade until
     * {@code to}, eaten from the tail over [eat0, eat1] while it turns to ink over [ink0, ink1]; its edges lie
     * {@code inner}..{@code outer} blocks along the blade from the hand (a little past the point). Styles of the ink
     * ribbons: 0 a cut (widest at its head), 1 a ring (even), 3 the finisher's (thicker).
     */
    record Slash(float from,float to,float eat0,float eat1,float ink0,float ink1,int style,double inner,double outer,int seed){}
    static final Slash[] NONE={};
    static Slash[] slashes(int combo){
        return switch(combo){
            case 1->new Slash[]{new Slash(.245f,.37f,.34f,.62f,.36f,.66f,0,1.7,2.55,1)};
            case 2->new Slash[]{new Slash(.17f,.5f,.44f,.8f,.46f,.84f,1,1.75,2.25,3)};
            case 3->new Slash[]{new Slash(.4f,.48f,.52f,.74f,.52f,.78f,0,1.7,2.45,5),new Slash(.52f,.58f,.62f,.84f,.62f,.88f,0,1.7,2.45,9),
                new Slash(.655f,.765f,.8f,1.04f,.8f,1.08f,0,1.75,2.6,11)};
            default->new Slash[]{new Slash(.445f,.575f,.64f,.98f,.6f,1.04f,3,1.8,3.25,7)};
        };
    }
    static final int SAMPLES=40;
    /** The band the blade sweeps between clip times {@code from} and {@code head}: edges and the fraction along the tip's path. */
    record Swept(Vec3d[] inner,Vec3d[] outer,float[] along,int n){}
    static Swept swept(Fx c,int combo,float from,float head,double inner,double outer){return swept(c.pos,c.cast.yaw(),combo,from,head,inner,outer);}
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

    /** Hit 3's thrust: the beam runs on from the point, out to this far (blocks), lit over these clip times. */
    static final double BEAM=5.6;
    static final float BEAM_ON=.235f,BEAM_OFF=.44f;
    static float beamLit(float t){return Ease.smooth(t,BEAM_ON,BEAM_ON+.03f)*(1-Ease.smooth(t,BEAM_OFF-.12f,BEAM_OFF));}
    /** The thrust's line: from behind the hand to the beam's end, along the blade at the blow (its stretch out grows with the thrust). */
    static Vec3d[] beam(Vec3d feet,float yaw,float t){
        var hb=blade(feet,yaw,3,blow(3,0));double grow=Ease.out(t,BEAM_ON,BEAM_ON+.05f);
        return new Vec3d[]{hb[0].subtract(hb[1].multiply(.4)),hb[0].add(hb[1].multiply(TIP+(BEAM-TIP)*grow)),hb[1]};
    }
    /** Where the finisher bursts into ink on the ground: under the first one it struck, else where its cut met the front. */
    static Vec3d finishCentre(Fx c){
        var m=blowMemo(c,0);Vec3d f=Vec3d.fromPolar(0,c.cast.yaw());
        Vec3d at=m.at().length>0?new Vec3d(m.at()[0].x,m.feet().y,m.at()[0].z):m.feet().add(f.multiply(2.6));
        return new Vec3d(at.x,m.feet().y+.03,at.z);
    }

    /**
     * The ink ribbons (KagenoFire's ink pass): every slash along the blade's own path, drawn on as it sweeps, eaten from
     * the tail as it turns to ink; the turn's ring falls to the ground as a ring of ink; the thrust's beam; the finisher's
     * ring of ink on the ground.
     */
    @Override public void ink(Fx c){
        float t=c.sec;int combo=c.cast.combo();
        for(var s:slashes(combo)){
            var sw=now(c,s);if(sw==null)continue;
            float a=Ease.smooth(t,s.from(),s.from()+.02f);
            KagenoFire.inkRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),Ease.smooth(t,s.eat0(),s.eat1()),inked(s,t),a);
        }
        if(combo==2&&t>=.4f&&t<1.25f){
            // the ring falls round their feet as a thin ring of ink and dries there, breaking up
            Vec3d o=floor(c,c.pos);double r=2.0+.12*Ease.out(t,.4f,.7f);
            ring(o,r-.13,r+.13,4,13,Ease.smooth(t,.95f,1.25f),.25f+.6f*Ease.smooth(t,.45f,1.2f),.9f*Ease.smooth(t,.4f,.5f));
        }
        if(combo==3){
            float lit=beamLit(t);
            if(lit>0){var b=beam(c.pos,c.cast.yaw(),t);beamRibbon(c,b[0],b[1],.2,2,15,Ease.smooth(t,BEAM_OFF-.14f,BEAM_OFF),Ease.smooth(t,.3f,BEAM_OFF),lit);}
        }
        if(combo==4&&t>=blow(4,0)-.01f&&t<1.5f){
            // the ink spreading out over the ground from where the cut met the front, a ring racing out and drying
            float at=blow(4,0);Vec3d o=finishCentre(c);double r=.5+1.7*Ease.out(t,at,at+.3f);
            ring(o,r-.18,r+.18,4,7,Ease.smooth(t,1.1f,1.5f),.2f+.65f*Ease.smooth(t,at+.05f,1.4f),.9f*Ease.smooth(t,at-.01f,at+.02f));
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
            case 2->Ease.window(t,.1f,.56f,.05f,.1f);
            case 3->Ease.window(t,.1f,.8f,.05f,.12f);
            case 4->Ease.window(t,.04f,.7f,.04f,.12f);
            default->Ease.window(t,.12f,.46f,.05f,.1f);
        };
        for(int i=0;i<IsosangRules.blows(combo);i++)g+=.7f*FxLib.kick(t,blow(combo,i),.07f);
        return g;
    }
    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        aura(c,m,t,combo);
        sparks(c,m,t,combo);
        impacts(c,m,t,combo);
        if(combo==3)beamLight(c,m,t);
        if(combo==4){twirl(c,m,t);point(c,m,t);burst(c,m,t,true);}
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        blots(c,m,t,combo);
        splashes(c,m,t,combo);
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
                m.softDisc(p,(big?2.4:1.35)*(1-.4*u)+.25,BLUE,.55f*a*(1-u));m.softDisc(p,(big?.85:.5)*(1-u)+.12,WHITE,a*(1-u));
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
    /** Hit 3's beam: its light along the line, rings at the point and where it meets whom it struck. */
    static void beamLight(Fx c,EffectMesh m,float t){
        float a=beamLit(t);if(a<=0)return;
        var b=beam(c.pos,c.cast.yaw(),t);
        m.softLine(b[0],b[1],1.5,DEEP,.32f*a);m.softLine(b[0],b[1],.5,AZURE,.5f*a);
        m.needle(b[0],b[1].subtract(b[2].multiply(.4)),.12,.25,WHITE,.9f*a);
        var hb=blade(c,3,blow(3,0));Vec3d tip=hb[0].add(hb[1].multiply(TIP));float at=blow(3,0);
        FxLib.ringPlane(m,tip,c.right,UP,.2+.8*Ease.out(t,at-.02f,at+.1f),.14,WHITE,.8f*a);
        for(int k=0;k<3;k++){
            float s=at+.03f*k,u=Ease.unit(t,s,s+.26f);if(t<s||u>=1)continue;
            Vec3d o=tip.add(b[2].multiply(.6+1.3*k+1.2*Ease.out(t,s,s+.26f)));
            FxLib.ringPlane(m,o,c.right,UP,.3+(.8+.4*k)*Ease.out(t,s,s+.2f),.16,k==0?WHITE:PALE,.8f*(1-u)*Ease.smooth(t,s,s+.015f));
        }
    }
    /** The finisher's twirl: a pale ring of light over the head along the point's path, gone as the aim begins. */
    static void twirl(Fx c,EffectMesh m,float t){
        if(t<.07f||t>=.34f)return;
        float from=Math.max(.07f,t-.12f),head=Math.min(t,.225f);if(head<=from+.004f)return;
        float a=.85f*(1-Ease.smooth(t,.24f,.34f));int n=18;Vec3d prev=null;
        for(int i=0;i<=n;i++){
            float s=from+(head-from)*i/n;var hb=blade(c,4,s);Vec3d p=hb[0].add(hb[1].multiply(TIP*.95));
            if(prev!=null){float u=i/(float)n;m.softLine(prev,p,.22,AZURE,a*u*.6f);m.line(prev,p,.035,PALE,a*u);}
            prev=p;
        }
    }
    /** The finisher's aim: a glint at the pointing hand. */
    static void point(Fx c,EffectMesh m,float t){
        if(t<.24f||t>=.42f)return;
        float a=Ease.smooth(t,.24f,.28f)*(1-Ease.smooth(t,.36f,.42f));
        Vec3d hand=c.pos.add(0,1.35,0).add(c.f.multiply(.75)).add(c.left.multiply(.3));
        m.star(hand,.35*a+.1,PALE,a);m.softDisc(hand,.3,AZURE,.6f*a);
    }
    /**
     * The finisher bursting into ink as it lands: a flash and a shock ring over the ground where the cut met the front
     * (light), and a dark ring racing out with the ink under it (blended).
     */
    static void burst(Fx c,EffectMesh m,float t,boolean additive){
        float at=blow(4,0);if(t<at-.01f||t>=at+.7f)return;
        Vec3d o=finishCentre(c);float a=Ease.smooth(t,at-.01f,at+.01f),u=Ease.unit(t,at,at+.45f);
        if(!additive){
            FxLib.ring(m,o.add(0,.02,0),.5+3*Ease.out(t,at,at+.5f),.3,INK,.5f*a*(1-u));
            return;
        }
        m.softDisc(o.add(0,1,0),2.2*(1-.5*u)+.3,BLUE,.45f*a*(1-u));
        FxLib.ring(m,o.add(0,.05,0),.4+3.4*Ease.out(t,at,at+.4f),.32,AZURE,.65f*a*(1-u));
        FxLib.ring(m,o.add(0,.04,0),.3+2.2*Ease.out(t,at,at+.3f),.2,PALE,.55f*a*(1-Ease.unit(t,at,at+.3f)));
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
                float u=age/life;double size=(.07+.13*Ease.hash(k*31+k0))*(1-.6*u)*Ease.out(age,0,.06f);
                flake(m,q,size,k*37+k0,age,INK,.9f*(1-Ease.smooth(u,.55f,1)));
            }
        }
    }
    /** Ink splashed from every struck target at every blow, and the finisher's spray of blots out of the ground ring. */
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
            Vec3d o=finishCentre(c);
            for(int k=0;k<44;k++){
                int seed=k*43+977;float age=t-at-.04f*(float)Ease.hash(seed),life=.6f+.4f*(float)Ease.hash(seed+1);if(age<0||age>=life)continue;
                double az=Ease.hash(seed+2)*Math.PI*2,r=.8+2.2*Ease.hash(seed+3);
                Vec3d out=new Vec3d(Math.cos(az),0,Math.sin(az));
                Vec3d start=o.add(out.multiply(r)).add(0,.1,0),v=out.multiply(1+1.6*Ease.hash(seed+4)).add(0,1+1.6*Ease.hash(seed+5),0);
                double drag=1-Math.exp(-age*2.6);Vec3d q=start.add(v.multiply(drag/2.6)).add(0,-3.2*age*age,0);float u=age/life;
                if(q.y<o.y-.05)continue;
                flake(m,q,(.08+.16*Ease.hash(seed+6))*(1-.5*u)*Ease.out(age,0,.08f),seed,age,INK,.92f*(1-Ease.smooth(u,.6f,1)));
            }
        }
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
        if(combo==3){float lit=beamLit(t);if(lit>0){var b=beam(c.pos,c.cast.yaw(),t);f.light(b[0].add(b[1]).multiply(.5),0x6aa8ff,8,2.2f*lit);}}
        if(combo==4){
            float at=blow(4,0);
            if(t>=at-.01f){
                Vec3d o=finishCentre(c);float near=(float)Math.max(0,Math.min(1,(22-c.camera.distanceTo(o))/10));
                f.light(o.add(0,1,0),0x9cc8ff,12,4f*FxLib.kick(t,at,.18f));
                if(near>0)f.shaft(o.add(0,.6,0),0xb8d8ff,.35f*near*FxLib.kick(t,at,.18f));
            }
            // the lunge and the great cut are quick: their afterglow is cut short so they do not smear (FxFrame.afterglow)
            if(t>=.38f&&t<.6f)f.afterglow(.4f);
        }
        if(combo==2&&t>=.15f&&t<.52f)f.afterglow(.45f);
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
            if(t>=at-.01f&&t<at+.4f){Vec3d o=finishCentre(c).add(0,.9,0);float in=Ease.smooth(t,at-.01f,at+.015f);d.ring(o,c.right,c.f,.5+5*Ease.out(t,at,at+.4f),1.4,.8f*in*(1-Ease.unit(t,at,at+.4f)));}
        }
    }

    // ───────────────────────────── camera ─────────────────────────────

    /** Shake as the blows land, a little harder for the thrust and the finisher; each comes in over two frames. */
    @Override public float shake(StandClient.Cast cast,float sec){
        int combo=cast.combo();float v=0;
        for(int i=0;i<IsosangRules.blows(combo);i++){
            float at=blow(combo,i),amp=combo==4?1.4f:combo==3?(i==0?.8f:.45f):.5f;
            v=Math.max(v,amp*bump(sec,at,combo==4?.12f:.08f));
        }
        return v;
    }
    static float bump(float sec,float at,float tau){return sec<at-.02f?0:Ease.smooth(sec,at-.02f,at+.015f)*(float)Math.exp(-Math.max(0,sec-at-.015f)/tau);}
    /** Speed afterimages for the fast parts: the turn, the cross, the lunge and the great cut. */
    @Override public float afterimages(float sec){return -1;}
}
