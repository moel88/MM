package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * Lee So-sang's H again as drawn (2.101.0, user request: "이번에는 H를 사용한 상태에서 H를 한 번 더 누르면 되는 스킬 음성은
 * 저렇게 3개고 랜덤으로 하나 나오면 돼 스킬은 카메라가 플레이어로 빛추고 H 영역에 있던 모든 검이 전부 플레이어 뒤로 가고 앞으로
 * 꽂는 스킬이야 이걸 쓰면 H가 해제돼", the user's three videos of it read on a sheet and confirmed: "맞음, 이대로"; the cutscene
 * hers alone: "시전자만"; 2.101.1, user request: "좀 많이 달라 영상은 칼을 타고 한바퀴를 돈 다음에 공중에서 검을 전면에 모두
 * 꽂는거야 그리고 내려옴", "그리고 박히는 검들은 더 커야해", "그리고 꽂힐때마다 잉크가 튀어야해", the video read again and
 * confirmed, and their answers: twice as big, growing as they fly, her lap some six blocks round):
 * <ul>
 * <li>a blue flash, a ring of blue light at her feet, and her own camera into its cutscene: the world goes dark under a
 * red dusk ({@link #ground}, IsosangCinema.dusk);</li>
 * <li>she leaps off the pommel, and the sword she stood on is pulled up out of the ground and flies up under her feet
 * ({@link #steedMid}); she rides it round a lap of the sky (IsosangRules.finaleLap), leaning into its turn, a long arc of
 * white and blue light behind her ({@link #trail}); every other sword of her formation is pulled up out of the ground,
 * nearest first, and streams after her swirling round her, swords of light joining them out of bursts of blue
 * ({@link #LIGHT}: as many again, the videos' swarm);</li>
 * <li>she comes back at the camera and stops in the air over where she stood, on her sword; she swings her own sword
 * across in a blue arc ({@link #swing}) and it goes into light; the swords gather behind her back and spread there like
 * wings, points out ({@link #fan}); a white ring bursts round her; she raises two fingers before her face, her eyes
 * alight;</li>
 * <li>her camera cuts round behind her as the swords fly from behind her all at once, long white streaks over her, and
 * come down slanting into the ground in a wide band ahead of her, nearest first, growing to twice their size as they fly
 * ({@link #GROW}), blue crystal flaring up where each strikes and black ink flung up and splashed round it ({@link #ink},
 * {@link #inkBit}); she steps off her sword and comes down onto the ground where she stood, her sword flung down into
 * the band after the rest; the band flashes blue ({@link IsosangRules#FINALE_BURST});</li>
 * <li>her camera comes back to her; the swords stand a moment more and are gone one by one in light.</li>
 * </ul>
 * The formation's own swords are its swords: each is the formation's until it is pulled out (SwordFormation.pulled),
 * this one's from then, from the very place and pose it stood in. Every effect is a function of the swing's clock and
 * of where she stood.
 */
final class SwordFinale {
    private SwordFinale(){}
    /** How long it is drawn (seconds, IsosangClient.life): until the last of its swords' light and ink are gone. */
    static final float LIFE=6.2f;
    /** How long her body takes to turn from her formation's way round to the band's (seconds, IsosangClient.finaleFacing). */
    static final float TURN=.14f;

    // ───────────────────────────── the body ─────────────────────────────
    // (the videos: on the pommel a dip, then up into the air, and down onto her sword come up under her feet; riding it
    // round the sky, crouched, her right foot ahead of her left along it, arms out, leaning into the turn; coming back at
    // the camera she leans back to slow and stops up tall on it, her left knee up; her sword swung across in the air; her
    // right hand up before her face, two fingers raised (no sword in it: hers went into the light after the swing); the hand
    // thrown forward as they fly; watching them come down; off her sword, down onto the ground in a crouch; her sword back in
    // her hand as her camera comes back to her)
    static final R6.Pose F_DIP=w(.03,-.1,-2,6,0,d(-50,40),d(-10,70),d(-62,-30),d(-80,6),d(-86,-6));
    static final R6.Pose F_SPRING=w(.03,0,-2,-4,0,d(-25,62),d(-55,90),d(-25,-62),d(-70,10),d(-100,-6));
    /** Up in the air, coming down onto her sword: her legs gathered under her, her arms out. */
    static final R6.Pose F_TUCK=w(.02,.02,-4,8,6,d(-5,85),d(-25,125),d(0,-82),d(-74,10),d(-74,-8));
    /** Riding: crouched on it, her right foot ahead and her left behind along it, leaning forward and into the turn (her right), her arms out, her sword trailing back. */
    static final R6.Pose F_RIDE=w(.04,-.16,-10,16,18,d(-18,100),d(-24,150),d(6,-76),6,182);
    static final R6.Pose F_RIDE2=w(.04,-.18,-14,18,26,d(-28,118),d(-30,165),d(12,-62),6,180);
    static final R6.Pose F_RIDE3=w(.03,-.15,-8,10,20,d(-14,108),d(-24,158),d(14,-72),4,178);
    static final R6.Pose F_CREST=w(.02,-.12,-4,4,12,d(0,100),d(-14,150),d(10,-92),4,176);
    /** Coming straight back in at the camera: leaning back to slow, her arms out wide. */
    static final R6.Pose F_SWOOP=w(0,-.14,0,-12,2,d(6,112),d(-8,158),d(6,-112),6,174);
    /** Stopped in the air on it (the videos): up tall on her right foot, her left knee up, her arms a little open. */
    static final R6.Pose F_HOVER=w(.01,0,0,-2,0,d(-50,40),d(-30,70),d(-55,-40),d(-88,4),d(-40,-2));
    /**
     * Her sword swung across before her up there (the videos' blue arc): drawn back up at her right, the body turned
     * right; cut across before her to her left, the blade trailing her arm the way it goes (never across its line); followed
     * through, the body turned left.
     */
    static final R6.Pose F_WIND=w(.01,0,-28,4,0,d(22,118),d(16,165),d(12,-40),d(-88,4),d(-52,-2));
    static final R6.Pose F_MID=w(.03,-.01,-6,7,0,d(10,40),d(8,95),d(-5,-80),d(-88,4),d(-58,-2));
    static final R6.Pose F_CUT=w(.04,-.02,24,8,0,d(0,-40),d(2,10),d(-18,-122),d(-88,4),d(-64,-2));
    static final R6.Pose F_FOLLOW=w(.03,-.03,34,6,0,d(-16,-95),d(-20,-55),d(-30,-140),d(-88,4),d(-74,-2));
    static final R6.Pose F_SEAL=w(.01,-.04,4,-2,0,d(30,-50),d(80,-40),d(-55,-50),6,174);
    static final R6.Pose F_SEAL2=w(.01,-.04,5,-3,0,d(33,-48),d(80,-40),d(-57,-52),6,174);
    static final R6.Pose F_SEND=w(.04,-.05,-6,6,0,d(8,8),d(10,6),d(-60,-40),6,174);
    static final R6.Pose F_WATCH=w(.03,-.05,-4,4,0,d(-6,10),d(4,8),d(-62,-40),6,174);
    static final R6.Pose F_WATCH2=w(.02,-.05,-2,2,0,d(-28,14),d(-10,10),d(-68,-30),6,174);
    /** Off her sword: her left foot forward off it; then down, her legs together, her arms a little up. */
    static final R6.Pose F_STEP=w(.04,0,-2,6,0,d(-40,30),d(-20,40),d(-45,-40),d(-88,4),d(-62,-4));
    static final R6.Pose F_DROP=w(.02,0,0,2,0,d(18,78),d(-10,100),d(18,-78),d(-82,8),d(-82,-8));
    static final R6.Pose F_LAND=w(.06,-.28,0,14,0,d(-35,120),d(-45,140),d(-35,-120),22,-158);
    static final R6.Pose F_RISE=w(.02,0,0,-3,0,d(-45,55),d(-60,70),d(-45,-55),d(-90,4),d(-90,-4));
    static final R6.Pose F_LOWER=w(.01,0,0,1,0,d(-65,10),d(25,4),d(-80,-10),d(-90,3),d(-90,-3));
    static final Anim CLIP=Anim.joints(
        k(0,SwordFormation.H_ON),
        k(.08,F_DIP,Anim.Ease.OUT),
        k(FINALE_LEAP,F_SPRING),
        k(.32,F_TUCK,Anim.Ease.OUT),
        k(FINALE_RIDE,F_RIDE),
        k(.66,F_RIDE2),
        k(.86,F_RIDE3),
        k(FINALE_CREST,F_CREST),
        k(1.14,F_SWOOP),
        k(FINALE_LAP1,F_HOVER,Anim.Ease.OUT),
        k(FINALE_SWING0,F_WIND),
        k(1.43,F_MID),
        k(1.48,F_CUT),
        k(FINALE_SWING1,F_FOLLOW,Anim.Ease.OUT),
        k(1.72,F_SEAL,Anim.Ease.OUT),
        k(2.2,F_SEAL2),
        k(2.36,F_SEND,Anim.Ease.OUT),
        k(2.75,F_WATCH),
        k(2.95,F_WATCH2),
        k(3.06,F_STEP),
        k(3.22,F_DROP),
        k(FINALE_LAND,F_LAND,Anim.Ease.OUT),
        k(3.58,F_RISE),
        k(3.85,F_LOWER),
        k(FINALE_LENGTH/20f,STANCE)).after(IsosangSlash::grounded);

    /** Whether {@code e} holds nothing now (her sword gone into the light after she swings it up there, until her camera comes back to her). */
    static boolean emptyHanded(Entity e){
        if(e==null)return false;
        var c=IsosangClient.finaleOf(e.getUuid());if(c==null)return false;
        float t=c.age(MinecraftClient.getInstance().getTickDelta())/20f;
        return t>=HAND_GONE&&t<HAND_BACK;
    }
    /** How far her right hand's two fingers are raised before her face (0 to 1, GGesture.applyFinale): the videos' seal. */
    static float sealWeight(float t){return Ease.window(t,FINALE_SEAL0-.08f,FINALE_SEAL1+.12f,.1f,.12f);}
    /** When her own sword goes into light (as her swing ends, 2.101.1), and comes back to her hand (seconds). */
    static final float HAND_GONE=FINALE_SWING1,HAND_BACK=FINALE_BACK0;

    // ───────────────────────────── where it is ─────────────────────────────

    /**
     * Its frame: where she stands (on the formation's ground), the band's way, its right, how high the pommel she stood on
     * is, the band's way (degrees) and the way her formation had her facing.
     */
    record Frame(Vec3d spot,Vec3d f,Vec3d r,double pommel,float yaw,float formYaw){
        Vec3d at(double fw,double up,double rt){return spot.add(f.multiply(fw)).add(0,up,0).add(r.multiply(rt));}
    }
    static Frame frame(StandClient.Cast c){return frame(c,IsosangClient.finale(c.id()));}
    /** The same for the run {@code run} (the server's word of it: where she stands, the sword she stood on). */
    static Frame frame(StandClient.Cast c,IsosangClient.FinaleRun run){
        Vec3d f=Vec3d.fromPolar(0,c.yaw());
        return new Frame(run==null?c.origin():run.spot(),f,new Vec3d(-f.z,0,f.x),pommel(run),c.yaw(),run==null?c.yaw():run.formYaw());
    }
    /** How high over its ground the pommel she stood on stands (blocks: that sword's, SwordFormation.layout). */
    static double pommel(IsosangClient.FinaleRun run){
        if(run==null)return FORMATION_STAND;
        var l=SwordFormation.layout(run.form());return run.on()<l.length?l[run.on()].height():FORMATION_STAND;
    }
    /** Where her feet are drawn {@code t} seconds in: round her lap of the sky (IsosangRules.finaleLap) and up it (finaleDrawn). */
    static Vec3d body(Frame fr,float t){
        double[] l=finaleLap(t);
        return fr.spot.add(fr.f.multiply(l[0])).add(fr.r.multiply(l[1])).add(0,finaleDrawn(t,fr.pommel),0);
    }
    /** Where her chest is drawn {@code t} seconds in. */
    static Vec3d chest(Frame fr,float t){return body(fr,t).add(0,1.2,0);}
    /**
     * The way her body faces {@code t} seconds in (degrees): from her formation's way round to the band's as she dips on
     * the pommel; then the way she rides round her lap (IsosangRules.finaleHeading: once round to the right), the band's
     * again as she stops in the air.
     */
    static float faces(Frame fr,float t){
        if(t>=FINALE_LEAP)return fr.yaw+(float)finaleHeading(t);
        return fr.formYaw+MathHelper.wrapDegrees(fr.yaw-fr.formYaw)*Ease.smooth(t,0,TURN);
    }
    /** The same as a level way. */
    static Vec3d heading(Frame fr,float t){return Vec3d.fromPolar(0,faces(fr,t));}
    /** The middle of the band its swords come down in (where its sound is heard from for those away from her). */
    static Vec3d bandMiddle(StandClient.Cast c){var fr=frame(c);return fr.at((FINALE_NEAR+FINALE_REACH)/2,0,0);}

    // ───────────────────────────── the swords ─────────────────────────────

    /**
     * How many swords it has: the formation's own (each pulled out of the ground; the one she stood on her sword to ride,
     * {@link #steedMid}), and of light none (2.101.1, user request: "박히는 검들 개수는 줄여줘 28개 정도 박으면 될듯": the
     * formation's 28 come down into the band, hers last. 2.101.0 had as many of light again, joining the swirl out of
     * bursts of blue, 56 in the band).
     */
    static final int OWN=FORMATION_SWORDS,LIGHT=0,SWORDS=OWN+LIGHT;
    /**
     * One sword's way: its own (k &lt; OWN, the formation's sword k: where its pommel stood, its way down, its guard's turn)
     * or of light; whether it is the one she rides; when it is pulled out (or shows out of the light); its swirl after her
     * (how far behind her along her way, how far round her, how high, its angle at the start and its speed round); when it
     * goes to its place in the fan behind her and where that is (its middle and the way it points); where it waits aimed
     * through her once the fan folds (its middle and the way it points: the way it shoots off); when it is launched and
     * where it comes down (the middle of it standing in the ground slanting, grown, and the way it points); when it
     * strikes and when it is gone; its seed.
     */
    record Plan(int k,boolean own,boolean steed,Vec3d planted,Vec3d down,double spin,float start,float join,float lag,double orbitR,double orbitUp,double orbitA,double orbitW,
                float gather,Vec3d fanMid,Vec3d fanDir,Vec3d readyMid,Vec3d aimDir,float launch,Vec3d foot,Vec3d stuckMid,Vec3d stuckDir,float plant,float vanish,int seed){}
    private static final Map<UUID,Plan[]> PLANS=new LinkedHashMap<>();
    static void clear(){PLANS.clear();}
    /** How long a sword pulled out rises straight up out of the ground, and how long it takes to join the swirl (seconds, and a little longer a block it stood from her). */
    static final float RISE=.12f,JOIN=.28f,JOIN_PER=.03f,GATHER=.34f;
    /**
     * The swirl after her (2.101.1, the videos: the swords stream after her round her lap, wheeling round her): each a
     * little behind her along her way (LAG0 to LAG0+LAG seconds), wheeling round it in the upright round across her way.
     */
    static final float LAG0=.03f,LAG=.4f;
    /**
     * The fan behind her back as she stands in the air on her sword (the videos: the swords spread behind her like wings,
     * points out): its hub at her upper back, rings of swords out round it from her left low over the top to her right
     * low, each a little further back than the last, leaning a little back.
     */
    static final double HUB_UP=1.3,HUB_BACK=.5,FAN_FROM=-84,FAN_TO=84,RING0=1.15,RING_STEP=.78;
    static final int RINGS=4,PER_RING=SWORDS/RINGS;
    /**
     * The band's swords (blocks from where she stands): from {@link #BAND_NEAR} out to {@link #BAND_FAR}, {@link #BAND_HALF}
     * either side (inside the band that strikes, IsosangRules.finaleIn), {@link #BURY} blocks of them in the ground as drawn
     * at their size (2.101.1: as much further for them grown, and the nearest further out, so their hilts lean back toward
     * her short of where she comes down).
     */
    static final double BAND_NEAR=5.5,BAND_FAR=15,BAND_HALF=7.6,BURY=.55;
    /**
     * How they strike (2.101.3, user request: "검 박힐때 엄청 빠르게 박혀야 될 거 같아 원본 영상도 엄청 빠르고 매섭게 박히거든",
     * "그리고 위쪽으로 검이 나가서 박히는 느낌이 아니고", "내 몸쪽으로 뚫어서 박히는 느낌임"; 2.101.4: "원본 영상처럼 화면에서 검이
     * 나간다는 느낌으로 만들어줘"; video b again, frame by frame: as her camera cuts behind her the swords wait right by it,
     * big at its left edge from its top to its bottom with every point aimed on into the scene, and shoot one after another
     * as long white streaks out of the screen into the ground ahead past her, some as bright streaks in from past its right
     * edge, in a few frames each; 2.101.5: "왜 가운데는 비고 양끝으로만 나가냐 가운데도 나가게 해줘"): as she sends them
     * ({@link #FOLD0} to {@link #FOLD1}) the fan folds, every sword going to wait round her camera behind her (on its
     * screen, {@link #onScreen}, by the part of the fan it was in, {@link #wing}: her left wing's at its left edge, {@link
     * #EDGE_L0} to EDGE_L0+{@link #EDGE_L} across it, {@link #DEPTH_L0} to DEPTH_L0+{@link #DEPTH_L} blocks deep; her right
     * wing's just past its right edge, {@link #EDGE_R0} to EDGE_R0+{@link #EDGE_R}, {@link #DEPTH_R0} to DEPTH_R0+{@link
     * #DEPTH_R} deep; the fan's middle, within {@link #MID_WING} degrees of straight up, along the middle of its bottom and
     * its top, {@link #MID_X0} to MID_X0+{@link #MID_X} across it as they stood across the fan, those striking the band's
     * near half along its bottom ({@link #MID_LOW0} up it, {@link #DEPTH_B0} to DEPTH_B0+{@link #DEPTH_B} deep) and its far
     * half along its top ({@link #MID_TOP0} up it, {@link #DEPTH_T0} to DEPTH_T0+{@link #DEPTH_T} deep), clear of her and
     * the band between; those striking near the band low on it, far high), its point aimed at where it strikes. Each is drawn back a little ({@link #AIM} seconds before it is launched, {@link
     * #DRAW} blocks), then shoots off that way out of the screen into the scene, faster and faster (its way to the power
     * {@link #STRIKE_EASE}), its point dipping into the ground a little steeper than it came (up to {@link #STEEPER} degrees
     * more, at most {@link #SLANT_MAX} below level, never so much that it would go up: {@link #steeper}), {@link #FLIGHT}
     * to FLIGHT+{@link #FLIGHT_SPREAD} seconds into the ground ({@link #STEED_FLIGHT} hers, from under her feet as she
     * steps off it); it drives {@link #DRIVE} blocks further in than it stands and springs back over {@link #SETTLE}
     * seconds. The swords strike when they did (the server's blows), each launched just before.
     */
    static final float FOLD0=2.27f,FOLD1=2.46f;
    static final double MID_WING=35,EDGE_L0=-1.12,EDGE_L=.55,DEPTH_L0=2.4,DEPTH_L=2.6,EDGE_R0=1.06,EDGE_R=.22,DEPTH_R0=2.4,DEPTH_R=1.2;
    static final double MID_X0=-.45,MID_X=1.1,MID_JITTER=.08,MID_LOW0=-.8,MID_LOW=.16,DEPTH_B0=2.9,DEPTH_B=.8,MID_TOP0=.6,MID_TOP=.2,DEPTH_T0=3,DEPTH_T=1.3;
    /** Which part of the fan a slot at {@code phi} radians round it is in: her left wing, its middle (within {@link #MID_WING} degrees of straight up), her right wing. */
    enum Wing{LEFT,MIDDLE,RIGHT}
    static Wing wing(double phi){double a=Math.toDegrees(phi);return a<-MID_WING?Wing.LEFT:a>MID_WING?Wing.RIGHT:Wing.MIDDLE;}
    static final double STEEPER=18,SLANT_MAX=62,SLANT_JITTER=6;
    static final float FLIGHT=.07f,FLIGHT_SPREAD=.04f,AIM=.1f,STEED_FLIGHT=.11f,SETTLE=.12f;
    static final double DRAW=.35,DRIVE=.3,STRIKE_EASE=1.7;
    /**
     * How big the swords grow as they fly down into the band (2.101.1, user request: "그리고 박히는 검들은 더 커야해", their
     * choice: twice as big, growing as they fly; then "박히는 검들 1.5배로 더 키우고": half as big again, three times the
     * formation's): their size from the fan (1) to standing in the ground.
     */
    static final double GROW=3;
    /** Each sword's slot in the fan: its own swords on the even rings' slots, any of light between them (none now: one each). */
    static int slot(int j){return LIGHT==0?j:j<OWN?2*j:2*(j-OWN)+1;}
    /** A slot's angle round the fan (radians, 0 straight up, + to her right) and ring. */
    static double slotAngle(int s){int q=s%RINGS,i=s/RINGS;return Math.toRadians(FAN_FROM+(FAN_TO-FAN_FROM)*(i+(q%2==1?.5:0))/(PER_RING-.5));}
    static int ring(int s){return s%RINGS;}
    /** The ways of all of its swords (computed once for the swing: where its formation's swords stood, where the band's ground is). */
    static Plan[] plans(StandClient.Cast c){
        var p=PLANS.get(c.id());if(p!=null)return p;
        var run=IsosangClient.finale(c.id());if(run==null)return null;
        p=plans(c,run,MinecraftClient.getInstance());
        PLANS.put(c.id(),p);while(PLANS.size()>16)PLANS.remove(PLANS.keySet().iterator().next());
        return p;
    }
    /** The same for the run {@code run}, the band's ground looked for in {@code mc}'s world (level with where she stands with none). */
    static Plan[] plans(StandClient.Cast c,IsosangClient.FinaleRun run,MinecraftClient mc){
        Frame fr=frame(c,run);
        var layout=SwordFormation.layout(run.form());
        float formT=(c.start()-run.formStart())/20f;
        // the formation's own swords pulled out nearest her first (the one she stood on as she leaps off it)
        Integer[] near=new Integer[OWN];
        for(int k=0;k<OWN;k++)near[k]=k;
        Arrays.sort(near,Comparator.comparingDouble((Integer k)->SwordFormation.foot(run.centre(),run.formYaw(),layout[k]).squaredDistanceTo(fr.spot)));
        float[] pulls=new float[OWN];
        for(int r=0;r<OWN;r++){int k=near[r];pulls[k]=k==run.on()?FINALE_LEAP+.02f:PULL0+PULL_SPREAD*r/(OWN-1f)+.01f*(float)Ease.hash(k*31+5);}
        Vec3d hub=fr.at(-HUB_BACK,FINALE_HOVER+HUB_UP,0);
        Plan[] out=new Plan[SWORDS];
        for(int j=0;j<SWORDS;j++){
            int seed=j*977+run.form().hashCode()*31+13;boolean own=j<OWN,steed=own&&j==run.on();
            Vec3d planted=null,down=new Vec3d(0,-1,0);double spin=360*Ease.hash(seed+1);float start,join=JOIN;
            if(own){
                var b=layout[j];planted=SwordFormation.planted(run.centre(),run.formYaw(),b);down=SwordFormation.down(b);
                spin=SwordFormation.spinAt(b,formT+pulls[j]);start=pulls[j];
                // (the farther it stood from her, the longer it takes to fly up into the swirl after her)
                join=JOIN+JOIN_PER*(float)SwordFormation.foot(run.centre(),run.formYaw(),b).distanceTo(fr.spot);
            }else start=BORN0+BORN_SPREAD*(j-OWN)/(LIGHT-1f)+.03f*(float)Ease.hash(seed+2);
            float lag=LAG0+LAG*(float)Ease.hash(seed+15);
            double orbitR=own?1.8+1.8*Ease.hash(seed+3):2.2+2*Ease.hash(seed+3),orbitUp=-.8+1.8*Ease.hash(seed+4);
            double orbitA=Math.PI*2*Ease.hash(seed+5),orbitW=(4+2*Ease.hash(seed+6))*(Ease.hash(seed+16)<.5?-1:1);
            // (it goes on into the fan once it has joined the swirl: never off its way into it)
            float gather=Math.max(GATHER0+GATHER_SPREAD*(float)Ease.hash(seed+7),start+(own?RISE+join+.04f:.15f));
            int s=slot(j);double phi=slotAngle(s),rho=RING0+RING_STEP*ring(s);
            Vec3d radial=fr.r.multiply(Math.sin(phi)).add(0,Math.cos(phi),0);
            Vec3d fanDir=radial.subtract(fr.f.multiply(.2)).normalize();
            Vec3d fanPommel=hub.add(radial.multiply(rho)).subtract(fr.f.multiply(.12+.16*rho));
            Vec3d fanMid=fanPommel.add(fanDir.multiply(SwordFormation.S_LEN/2));
            // where it comes down: across the band as it stood across the fan, its ring how far out (hers, the one she
            // rides, in the near middle of it, flung there last as she steps off it)
            double side,fw;
            if(steed){side=(Ease.hash(seed+9)-.5)*.8;fw=BAND_NEAR+.5;}
            else{side=BAND_HALF*Math.sin(phi)*(.8+.2*Ease.hash(seed+8))+(Ease.hash(seed+9)-.5)*.9;fw=BAND_NEAR+(BAND_FAR-BAND_NEAR)*((ring(s)+.15+.7*Ease.hash(seed+10))/RINGS);}
            Vec3d foot=ground(mc,fr.at(fw,0,side),fr.spot.y);
            float plant,launch;
            if(steed){launch=FINALE_DESCEND+.06f;plant=launch+STEED_FLIGHT;gather=launch;}
            else{
                plant=Math.min(FINALE_PLANT1,FINALE_PLANT0+(FINALE_PLANT1-FINALE_PLANT0-.04f)*(float)((fw-BAND_NEAR)/(BAND_FAR-BAND_NEAR))+.04f*(float)Ease.hash(seed+12));
                launch=plant-FLIGHT-FLIGHT_SPREAD*(float)Ease.hash(seed+13);
            }
            // (2.101.3) where it waits as the fan folds, behind her back, aimed at where it strikes past her body (hers under
            // her feet as she steps off it); it shoots off that way and dips into the ground a little steeper, standing
            // along the way it comes in
            Vec3d readyMid;
            if(steed)readyMid=rideMid(fr,FINALE_DESCEND);
            else{
                // (2.101.4) round her camera behind her: her left wing's at its left edge (in it, big, as the video's), her
                // right wing's just past its right edge (out of it until they shoot in, as the video's bright streaks from
                // its right); (2.101.5) her wings' middle in the middle of it, along its bottom and its top (clear of her
                // and the band between); those striking near the band low on it, far high
                double h1=Ease.hash(seed+21),h2=Ease.hash(seed+22),h3=Ease.hash(seed+23);
                readyMid=switch(wing(phi)){
                    case RIGHT->onScreen(fr,EDGE_R0+EDGE_R*h1,-.55+.37*ring(s)+.15*(h2-.5),DEPTH_R0+DEPTH_R*h3);
                    case LEFT->onScreen(fr,EDGE_L0+EDGE_L*h1,-.8+.53*ring(s)+.2*(h2-.5),DEPTH_L0+DEPTH_L*h3);
                    case MIDDLE->{
                        double across=MID_X0+MID_X*(Math.toDegrees(phi)+MID_WING)/(2*MID_WING)+MID_JITTER*(h1-.5);
                        boolean high=ring(s)>=RINGS/2;
                        yield high?onScreen(fr,across,MID_TOP0+MID_TOP*(ring(s)-RINGS/2)+MID_JITTER*(h2-.5),DEPTH_T0+DEPTH_T*h3)
                            :onScreen(fr,across,MID_LOW0+MID_LOW*ring(s)+MID_JITTER*(h2-.5),DEPTH_B0+DEPTH_B*h3);
                    }
                };
            }
            Vec3d aimDir=foot.subtract(readyMid).normalize();
            Vec3d stuckDir=steeper(readyMid,aimDir,foot,STEEPER+SLANT_JITTER*(2*Ease.hash(seed+20)-1));
            Vec3d point=foot.add(stuckDir.multiply(BURY*GROW)),stuckMid=point.subtract(stuckDir.multiply(SwordFormation.S_LEN*GROW/2));
            float vanish=FINALE_GONE0+(FINALE_GONE1-FINALE_GONE0-.2f)*(float)Ease.hash(seed+14);
            out[j]=new Plan(j,own,steed,planted,down,spin,start,join,lag,orbitR,orbitUp,orbitA,orbitW,gather,fanMid,fanDir,readyMid,aimDir,launch,foot,stuckMid,stuckDir,plant,vanish,seed);
        }
        return out;
    }
    /** When the formation's swords are pulled out (seconds: the nearest as she leaps, the farthest by PULL0+PULL_SPREAD), and when the swords of light show; when they gather behind her. */
    static final float PULL0=.24f,PULL_SPREAD=.42f,BORN0=.34f,BORN_SPREAD=.5f,GATHER0=1.24f,GATHER_SPREAD=.3f;
    /** When the formation's sword {@code k} is pulled out of the ground by H again {@code fin} (seconds into it; never for none). */
    static float pullAt(StandClient.Cast fin,int k){
        var p=plans(fin);return p==null||k<0||k>=OWN?Float.MAX_VALUE:p[k].start();
    }
    /** The ground under {@code at} (looked for 3 blocks over it to 4 under it in the client's world), else {@code at} at {@code floor}. */
    static Vec3d ground(MinecraftClient mc,Vec3d at,double floor){
        Vec3d level=new Vec3d(at.x,floor,at.z);
        if(mc==null||mc.world==null)return level;
        var hit=mc.world.raycast(new RaycastContext(level.add(0,3,0),level.add(0,-4,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        return hit.getType()==HitResult.Type.MISS?level:hit.getPos();
    }

    /** A sword as it is drawn: its middle, the way it points (pommel to point), its guard's turn, how strongly it shows, whether it flies (its light streaks behind it), how big it is. */
    record Blade(Vec3d mid,Vec3d dir,double spin,float alpha,boolean flying,double scale){
        Vec3d pommel(){return mid.subtract(dir.multiply(SwordFormation.S_LEN*scale/2));}
        Vec3d point(){return mid.add(dir.multiply(SwordFormation.S_LEN*scale/2));}
    }
    /** Where sword {@code p} streams after her {@code t} seconds in (its middle): a little behind her along her way, wheeling round it. */
    static Vec3d orbit(Frame fr,Plan p,float t){
        float at=Math.max(0,t-p.lag());
        Vec3d c=chest(fr,at),h=heading(fr,at),side=new Vec3d(-h.z,0,h.x);
        double a=p.orbitA()+p.orbitW()*t;
        return c.add(side.multiply(Math.cos(a)*p.orbitR())).add(0,.75*Math.sin(a)*p.orbitR()+p.orbitUp(),0);
    }
    /** How fast it goes there (blocks a second). */
    static Vec3d orbitVel(Frame fr,Plan p,float t){return orbit(fr,p,t+.005f).subtract(orbit(fr,p,t-.005f)).multiply(100);}
    /**
     * The way it points as it streams after her: the way she went where it is behind her (point first after her, as a
     * shoal: her way round, up as she climbs and down as she comes down); round the way it wheels as she leaps and as she
     * stops, turning from one to the other over a moment. (Its own motion, her way and its wheel together, would turn it
     * round in a frame wherever the two nearly cancel.)
     */
    static Vec3d swirl(Frame fr,Plan p,float t){
        float at=Math.max(0,t-p.lag());
        Vec3d h=heading(fr,at),side=new Vec3d(-h.z,0,h.x);double a=p.orbitA()+p.orbitW()*t;
        Vec3d wheel=side.multiply(-Math.sin(a)).add(0,.75*Math.cos(a),0).multiply(Math.signum(p.orbitW())).normalize();
        float w=Ease.smooth(at,FINALE_LEAP,FINALE_LEAP+SURE)*(1-Ease.smooth(at,FINALE_LAP1-.2f,FINALE_LAP1-.02f));
        if(w<=0)return wheel;
        // (her way: along her heading, never straight up or down, so never right against its wheel)
        Vec3d v=chest(fr,at+.005f).subtract(chest(fr,at-.005f)).multiply(100);
        Vec3d way=h.multiply(Math.max(SURE_ON,v.x*h.x+v.z*h.z)).add(0,v.y,0).normalize();
        return slerp(wheel,way,w);
    }
    /** How long the swords after her take to turn from their wheel to her way as she leaps (seconds), and the least of her way along her heading they take (blocks a second). */
    static final float SURE=.12f;
    static final double SURE_ON=4;
    /**
     * Her sword under her feet as she rides {@code t} seconds in (its middle): along the way she goes, a little ahead of
     * her, its flat just under her soles.
     */
    static Vec3d rideMid(Frame fr,float t){return body(fr,t).add(heading(fr,t).multiply(STEED_AHEAD)).add(0,-STEED_UNDER,0);}
    /** The way it points: her way, its point a little up as she climbs and down as she comes down (half her climb, at most STEED_PITCH degrees). */
    static Vec3d rideDir(Frame fr,float t){
        double vy=(finaleDrawn(t+.01,fr.pommel)-finaleDrawn(t-.01,fr.pommel))/.02;
        double[] a=finaleLap(t-.01),b=finaleLap(t+.01);double vh=Math.hypot(b[0]-a[0],b[1]-a[1])/.02;
        double pitch=MathHelper.clamp(.5*Math.atan2(vy,Math.max(4,vh)),-Math.toRadians(STEED_PITCH),Math.toRadians(STEED_PITCH));
        return heading(fr,t).multiply(Math.cos(pitch)).add(0,Math.sin(pitch),0).normalize();
    }
    static final double STEED_AHEAD=.12,STEED_UNDER=.04,STEED_PITCH=8;
    /**
     * The sword she stood on {@code t} seconds in as she rides it (its middle, 2.101.1, the videos: "뛰어오른 그녀가 서 있던
     * 검을 발밑에 두고 올라탐"): pulled up out of the ground as she leaps off it, rising, and up after her to come under her
     * feet as she comes down onto it (FINALE_RIDE); under them from then until she steps off it.
     */
    static Vec3d steedMid(Frame fr,Plan p,float t){
        // as she steps off it (2.101.3): it stays where she left it, aiming, drawn back a little
        if(t>=FINALE_DESCEND)return p.readyMid().lerp(drawn(fr,p),Ease.smooth(t,FINALE_DESCEND,p.launch()));
        if(t>=FINALE_RIDE)return rideMid(fr,t);
        Vec3d stood=p.planted().add(p.down().multiply(SwordFormation.S_LEN/2));
        Vec3d up=stood.add(0,RISE_UP*Ease.out(t,p.start(),p.start()+RISE),0);
        return up.lerp(rideMid(fr,t),steedIn(p,t));
    }
    /** How far up under her feet it has come (0 to 1). */
    static float steedIn(Plan p,float t){return Ease.smooth(t,p.start()+RISE*.4f,FINALE_RIDE);}
    /**
     * How big sword {@code p} is {@code t} seconds in (1 until it is launched, growing to GROW as it flies: the user's
     * "날아가며 커짐"; with the way it has come, so it is not already big where it waited behind her: 2.101.3's first preview
     * grew it by time, and as each was slowest at the start one swelled up white right behind her back).
     */
    static double scale(Plan p,float t){return t<p.launch()?1:scaleAt(along(p,t));}
    /** When it is drawn back to shoot (hers as she steps off it; never before the fan has folded). */
    static float aimFrom(Plan p){return p.steed()?FINALE_DESCEND:Math.max(FOLD1,p.launch()-AIM);}
    /** Where its middle is drawn back to as it is launched: from where it waits aimed through her (hers from under her feet as she steps off), back along the way it shoots. */
    static Vec3d drawn(Frame fr,Plan p){return p.readyMid().subtract(p.aimDir().multiply(DRAW));}
    /** How far along its flight it is {@code t} seconds in (0 as it is launched to 1 as it strikes: faster and faster). */
    static double along(Plan p,float t){return Math.pow(Ease.unit(t,p.launch(),p.plant()),STRIKE_EASE);}
    /** How big it is {@code u} of the way along its flight (1 to GROW, with the way it has come). */
    static double scaleAt(double u){return 1+(GROW-1)*u*u*(3-2*u);}
    /**
     * A point on her camera's screen behind her as the swords wait there ({@code x} and {@code y} from -1 to 1 across and
     * up it, -1 its left and bottom edges, 16:9; {@code depth} blocks in front of it): where her behind shot stands as the
     * fan has folded.
     */
    static Vec3d onScreen(Frame fr,double x,double y,double depth){
        float u=Ease.unit(FOLD1,FINALE_LAUNCH,FINALE_BACK1);
        Vec3d eye=fr.at(BEHIND[0]+.8*u,BEHIND[1]+.3*u,BEHIND[2]),fw=fr.at(BEHIND_LOOK[0],BEHIND_LOOK[1],BEHIND_LOOK[2]).subtract(eye).normalize();
        Vec3d right=fw.crossProduct(UP).normalize(),up=right.crossProduct(fw);
        double v=Math.tan(Math.toRadians(FOV_BEHIND/2)),h=v*16/9.0;
        return eye.add(fw.multiply(depth)).add(right.multiply(x*depth*h)).add(up.multiply(y*depth*v));
    }
    /** The way a sword pointed {@code d} stands {@code slant} radians below level (its point on the way {@code d} goes). */
    static Vec3d standing(Vec3d d,double slant){Vec3d level=new Vec3d(d.x,0,d.z).normalize();return level.multiply(Math.cos(slant)).add(0,-Math.sin(slant),0);}
    /**
     * The way a sword waiting at {@code mid}, aimed {@code aim} at {@code foot}, stands once it has struck: up to {@code
     * more} degrees steeper than it came (at most SLANT_MAX below level), but never so steep that its flight would go up
     * anywhere (its point or its middle): as steep as that allows, else straight in the way it came.
     */
    static Vec3d steeper(Vec3d mid,Vec3d aim,Vec3d foot,double more){
        double came=Math.toDegrees(Math.asin(MathHelper.clamp(-aim.y,-1,1)));
        Vec3d from=mid.add(aim.multiply(SwordFormation.S_LEN/2-DRAW));
        for(double s=Math.min(SLANT_MAX,came+Math.max(0,more));s>came;s-=1){
            Vec3d d=standing(aim,Math.toRadians(s));
            if(neverUp(from,aim,foot.add(d.multiply(BURY*GROW+DRIVE)),d))return d;
        }
        return aim;
    }
    /** Whether a flight from {@code a} going {@code da} to {@code b} going {@code db} never goes up, its point nor its middle. */
    static boolean neverUp(Vec3d a,Vec3d da,Vec3d b,Vec3d db){
        double l=a.distanceTo(b),last=1e9;
        for(int i=0;i<=60;i++){
            double u=i/60.0;
            Vec3d v=hermiteWay(a,da,b,db,l,u);if(v.y>-.02*l)return false;
            double y=hermite(a,da,b,db,l,u).y+v.normalize().y*-SwordFormation.S_LEN*scaleAt(u)/2;
            if(y>last+1e-9)return false;last=y;
        }
        return true;
    }
    static Vec3d hermite(Vec3d a,Vec3d da,Vec3d b,Vec3d db,double l,double u){
        double u2=u*u,u3=u2*u;
        return a.multiply(2*u3-3*u2+1).add(da.multiply(l*(u3-2*u2+u))).add(b.multiply(-2*u3+3*u2)).add(db.multiply(l*(u3-u2)));
    }
    static Vec3d hermiteWay(Vec3d a,Vec3d da,Vec3d b,Vec3d db,double l,double u){
        double u2=u*u;
        return a.multiply(6*u2-6*u).add(da.multiply(l*(3*u2-4*u+1))).add(b.multiply(-6*u2+6*u)).add(db.multiply(l*(3*u2-2*u)));
    }
    /** Where its point shoots off from (drawn back, as it is launched) and where it is driven in to (its point deepest in the ground). */
    static Vec3d tipFrom(Frame fr,Plan p){return drawn(fr,p).add(p.aimDir().multiply(SwordFormation.S_LEN/2));}
    static Vec3d tipTo(Plan p){return p.foot().add(p.stuckDir().multiply(BURY*GROW+DRIVE));}
    /**
     * Its point {@code u} of the way along its flight: shot off from where it was drawn back the way it was aimed, out of
     * her camera's screen, dipping to the way it stands as it drives in (a cubic from the one to the other, each way as long
     * as the flight: straight when the two are the same).
     */
    static Vec3d flightTip(Frame fr,Plan p,double u){Vec3d a=tipFrom(fr,p),b=tipTo(p);return hermite(a,p.aimDir(),b,p.stuckDir(),a.distanceTo(b),u);}
    /** The way it goes {@code u} of the way along its flight (the way it points as it flies). */
    static Vec3d flightDir(Frame fr,Plan p,double u){
        Vec3d a=tipFrom(fr,p),b=tipTo(p),v=hermiteWay(a,p.aimDir(),b,p.stuckDir(),a.distanceTo(b),u);
        return v.lengthSquared()<1e-12?p.stuckDir():v.normalize();
    }
    /** Its middle {@code u} of the way along its flight (behind its point the way it goes, as big as it has grown). */
    static Vec3d flight(Frame fr,Plan p,double u){return flightTip(fr,p,u).subtract(flightDir(fr,p,u).multiply(SwordFormation.S_LEN*scaleAt(u)/2));}
    /** Where it waits aimed through her {@code t} seconds in, breathing a little until it is drawn back to shoot. */
    static Vec3d ready(Plan p,float t){
        double s=p.seed(),k=1-Ease.smooth(t,aimFrom(p),p.launch());
        return p.readyMid().add(0,.04*k*Math.sin(t*2.7+s),0).add(p.aimDir().multiply(.03*k*Math.sin(t*3.3+s*.7)));
    }
    /** Its middle {@code t} seconds in (null when it is not drawn), before the way it points is found from how it moves. */
    static Vec3d mid(Frame fr,Plan p,float t){
        if(t<p.start()||t>=p.vanish()+FADE)return null;
        // stuck: driven a little further in than it stands, springing back
        if(t>=p.plant())return p.stuckMid().add(p.stuckDir().multiply(DRIVE*(1-Ease.out(t,p.plant(),p.plant()+SETTLE))));
        // (2.101.3) shot off past her body, faster and faster, dipping into the ground, never up (the video: long streaks)
        if(t>=p.launch())return flight(fr,p,along(p,t));
        if(p.steed())return steedMid(fr,p,t);
        float fanned=p.gather()+GATHER;
        if(t>=FOLD0){
            // folded out of the fan to wait behind her aimed through her, then drawn back a little to shoot
            float u=Ease.smooth(t,FOLD0,FOLD1);
            Vec3d at=u<=0?fanAt(p,t):u>=1?ready(p,t):bezier(fanAt(p,t),fanAt(p,t).subtract(p.fanDir().multiply(.5)),p.readyMid().subtract(p.aimDir().multiply(.6)),ready(p,t),u);
            return at.subtract(p.aimDir().multiply(DRAW*Ease.smooth(t,aimFrom(p),p.launch())));
        }
        if(t>=fanned)return fanAt(p,t);
        if(t>=p.gather()){
            float u=Ease.smooth(t,p.gather(),fanned);
            // (on along its way after her, not straight off it; out into its place point first: it comes in along the
            // way it will point)
            Vec3d a=orbit(fr,p,p.gather()),v=orbitVel(fr,p,p.gather());
            return bezier(a,a.add(v.multiply(.06)),p.fanMid().subtract(p.fanDir().multiply(1.2)),fanAt(p,fanned),u);
        }
        if(!p.own())return orbit(fr,p,t);
        Vec3d stood=p.planted().add(p.down().multiply(SwordFormation.S_LEN/2)),top=stood.add(0,RISE_UP,0);
        if(t<p.start()+RISE)return stood.add(0,RISE_UP*Ease.out(t,p.start(),p.start()+RISE),0);
        float v=Ease.smooth(t,p.start()+RISE,p.start()+RISE+p.join());
        if(v>=1)return orbit(fr,p,t);
        // (up out of the ground and after her into the swirl: it comes in along the way it will go)
        Vec3d o=orbit(fr,p,t),go=orbitVel(fr,p,t);
        return bezier(top,top.add(0,2.2,0).add(o.subtract(top).multiply(1,0,1).multiply(.25)),o.subtract(go.multiply(.08)),o,v);
    }
    /**
     * How high a sword pulled out rises straight up before it flies after her (blocks).
     */
    static final double RISE_UP=1.5;
    /** How long a sword takes to go (seconds). */
    static final float FADE=.22f;
    /** Its place in the fan, breathing a little. */
    static Vec3d fanAt(Plan p,float t){
        double s=p.seed();return p.fanMid().add(0,.05*Math.sin(t*2.3+s),0).add(p.fanDir().multiply(.04*Math.sin(t*3.1+s*.7)));
    }
    static Vec3d bezier(Vec3d a,Vec3d b,Vec3d c,Vec3d d,double u){
        double v=1-u;return a.multiply(v*v*v).add(b.multiply(3*v*v*u)).add(c.multiply(3*v*u*u)).add(d.multiply(u*u*u));
    }
    /** Its guard's turn {@code t} seconds in: spinning slowly as it flies after her until it is launched; hers turned flat as it comes under her feet. */
    static double spinAt(Plan p,float t){
        if(p.steed())return p.spin()*(1-steedIn(p,t));
        return p.spin()+SPIN*Math.max(0,Math.min(t,p.launch())-p.start()-RISE);
    }
    /** Sword {@code p} {@code t} seconds in (null when it is not drawn). */
    static Blade blade(Frame fr,Plan p,float t){
        Vec3d m=mid(fr,p,t);if(m==null)return null;
        float alpha=(p.own()?1:Ease.smooth(t,p.start(),p.start()+.1f))*(1-Ease.smooth(t,p.vanish(),p.vanish()+FADE));
        double spin=spinAt(p,t),sc=scale(p,t);
        // stuck: slanting into the ground, grown, quivering a moment as it strikes
        if(t>=p.plant()){
            float s=t-p.plant();double q=Math.toRadians(QUIVER)*Math.sin(s*QUIVER_RATE)*Math.exp(-s/QUIVER_DECAY);
            Vec3d side=p.stuckDir().crossProduct(UP);side=side.lengthSquared()<1e-6?new Vec3d(1,0,0):side.normalize();
            Vec3d d=p.stuckDir().multiply(Math.cos(q)).add(side.crossProduct(p.stuckDir()).multiply(Math.sin(q))).normalize();
            Vec3d point=m.add(p.stuckDir().multiply(SwordFormation.S_LEN*GROW/2));
            return new Blade(point.subtract(d.multiply(SwordFormation.S_LEN*GROW/2)),d,spin,alpha,false,GROW);
        }
        // hers: pointing down as it stood, turned up under her feet along her way as it comes, her way while she rides
        if(p.steed()){
            Vec3d d;
            if(t>=p.launch())d=flightDir(fr,p,along(p,t));
            else if(t>=FINALE_DESCEND)d=slerp(rideDir(fr,FINALE_DESCEND),p.aimDir(),Ease.smooth(t,FINALE_DESCEND,p.launch()));
            else if(t>=FINALE_RIDE)d=rideDir(fr,t);
            else if(t<p.start()+RISE*.4f)d=p.down();
            else d=slerp(p.down(),rideDir(fr,t),steedIn(p,t));
            return new Blade(m,d,spin,alpha,t>=p.launch(),sc);
        }
        // pulled out: pointing down as it stood, until it turns up as it rises
        if(p.own()&&t<p.start()+RISE*.5f)return new Blade(m,p.down(),spin,alpha,false,1);
        // in the fan: pointing out, as the wings' feathers; as the fan folds, turned to point through her the way it shoots
        float fanned=p.gather()+GATHER;
        if(t>=fanned&&t<p.launch())return new Blade(m,slerp(p.fanDir(),p.aimDir(),Ease.smooth(t,FOLD0,FOLD1)),spin,alpha,false,sc);
        // flying: launched, the way it goes along its flight (shot off past her, dipping in); streaming after her, the way it goes;
        // pulled out, turned up from pointing down as it rises, then after her into the swirl; gathering, turned from its
        // way after her out to the way it points in the fan (each turned smoothly, never flipping round where its way
        // bends tight)
        Vec3d d;
        if(t>=p.launch())d=flightDir(fr,p,along(p,t));
        else if(t>=p.gather())d=slerp(swirl(fr,p,p.gather()),p.fanDir(),Ease.smooth(t,p.gather(),fanned));
        else if(p.own()&&t<p.start()+RISE+p.join()){
            Vec3d upward=slerp(p.down(),UP,Ease.smooth(t,p.start()+RISE*.5f,p.start()+RISE+.14f));
            d=slerp(upward,swirl(fr,p,t),Ease.smooth(t,p.start()+RISE+p.join()*.3f,p.start()+RISE+p.join()));
        }else d=swirl(fr,p,t);
        return new Blade(m,d,spin,alpha,true,sc);
    }
    /** How fast it spins as it streams after her (degrees a second), and how it quivers as it strikes (degrees, its rate in radians a second, how fast it dies). */
    static final double SPIN=140,QUIVER=9,QUIVER_RATE=52,QUIVER_DECAY=.09;
    static final Vec3d UP=new Vec3d(0,1,0);
    static Vec3d slerp(Vec3d a,Vec3d b,double u){
        if(u<=0)return a;if(u>=1)return b;
        double dot=MathHelper.clamp(a.dotProduct(b),-1,1),th=Math.acos(dot);
        if(th<1e-4)return b;
        if(Math.PI-th<1e-3){Vec3d n=a.crossProduct(UP);n=n.lengthSquared()<1e-6?a.crossProduct(new Vec3d(1,0,0)):n;n=n.normalize();return a.multiply(Math.cos(th*u)).add(n.multiply(Math.sin(th*u))).normalize();}
        double s=Math.sin(th);return a.multiply(Math.sin((1-u)*th)/s).add(b.multiply(Math.sin(u*th)/s)).normalize();
    }
    /** Every sword drawn {@code t} seconds in (null for one not drawn then). */
    static Blade[] blades(StandClient.Cast c,float t){
        var plans=plans(c);if(plans==null)return new Blade[0];
        Frame fr=frame(c);float end=IsosangClient.finaleEnd(c.id());
        Blade[] out=new Blade[plans.length];
        for(int j=0;j<plans.length;j++){
            var p=plans[j];
            // called off: what has not come down yet goes now
            if(!Float.isNaN(end)&&t>=end&&p.plant()>end){var b=blade(fr,p,Math.min(t,end));if(b!=null&&t<end+.2f)out[j]=new Blade(b.mid(),b.dir(),b.spin(),b.alpha()*(1-Ease.smooth(t,end,end+.2f)),false,b.scale());continue;}
            out[j]=blade(fr,p,t);
        }
        return out;
    }
    /** Whether its blows are still to come or came before it was called off: the band's burst only for one not called off before it. */
    static boolean comes(StandClient.Cast c,float at){float end=IsosangClient.finaleEnd(c.id());return Float.isNaN(end)||at<=end;}
    /** A sword standing in the band: its pommel and its point (grown). */
    static Vec3d[] stuck(Plan p){
        Vec3d h=p.stuckDir().multiply(SwordFormation.S_LEN*GROW/2);
        return new Vec3d[]{p.stuckMid().subtract(h),p.stuckMid().add(h)};
    }

    // ───────────────────────────── the ink ─────────────────────────────

    /**
     * The ink as each strikes (2.101.1, user request: "그리고 꽂힐때마다 잉크가 튀어야해"; the video: black ink flying up among
     * the blue crystal where each comes down, the ground black round it; 2.101.2, user request: "잉크가 더 많이 튀게
     * 바꿔주고": far more of it): a big splash of ink round its foot ({@link #SPLASH} across, lasting {@link #SPLASH_LIFE}),
     * a long one thrown on ahead the way it drove in and a short one back ({@link #THROWN}), {@link #SPLASH_RING} smaller
     * ones flung out round it, {@link #SPIKES} tall strokes of ink shooting up out of it and a crown of {@link #CROWN}
     * short ones flung out low round it, each eaten from below, and pieces of ink flung up and out, more of them the way it
     * drove in ({@link #CLUMPS} clumps, {@link #FLAKES} flakes, {@link #DROPS} drops, falling, each clump and every drop
     * of any size splashing where it lands).
     */
    static final double SPLASH=3.6;
    static final int SPLASH_RING=6,SPIKES=4,CROWN=8,CLUMPS=8,FLAKES=10,DROPS=14;
    static final float SPLASH_LIFE=2f;
    /** How long a piece of ink flung off flies before it is gone (seconds: they are up and down again by then), the last of which it fades. */
    static final float BIT_LIFE=1.45f,BIT_FADE=.25f;
    /** The splashes thrown on ahead the way it drove in and back (of SPLASH across; how far out of its foot; how many times as long as wide). */
    static final double[] THROWN={.62,1.6,2.4, .38,-.95,1.9};
    /** How much more of the ink flung off goes the way it drove in. */
    static final double AHEAD=.45;
    /** The way sword {@code p} drove in, level (the way its slant leans: away from her). */
    static Vec3d drove(Plan p){
        Vec3d d=new Vec3d(p.stuckDir().x,0,p.stuckDir().z);
        return d.lengthSquared()<1e-6?new Vec3d(1,0,0):d.normalize();
    }
    /** Piece {@code i} of kind {@code kind} (0 clump, 1 flake, 2 drop) flung off as sword {@code p} strikes. */
    static SwordCage.Bit inkBit(Plan p,int kind,int i){
        int seed=p.seed()*31+kind*1009+i*67+5;
        double a=Math.PI*2*Ease.hash(seed);Vec3d out=new Vec3d(Math.cos(a),0,Math.sin(a)).add(drove(p).multiply(AHEAD)).normalize();
        Vec3d from=p.foot().add(out.multiply(.15+.4*Ease.hash(seed+1))).add(0,.1+.4*Ease.hash(seed+2),0);
        double speed=kind==0?2+1.8*Ease.hash(seed+3):kind==1?1.8+2.2*Ease.hash(seed+3):2.6+2.6*Ease.hash(seed+3);
        double rise=kind==0?4+3*Ease.hash(seed+4):kind==1?4.4+3*Ease.hash(seed+4):4.6+3*Ease.hash(seed+4);
        double size=kind==0?.28+.24*Ease.hash(seed+5):kind==1?.18+.16*Ease.hash(seed+5):.07+.07*Ease.hash(seed+5);
        float born=p.plant()+.04f*(float)Ease.hash(seed+6);
        return new SwordCage.Bit(kind,from,out.multiply(speed).add(0,rise,0),born,size,1.5+3*Ease.hash(seed+7),seed);
    }
    /** A splash of ink {@code age} seconds after it struck (spreading in a blink, black a while, breaking up and gone by SPLASH_LIFE). */
    static void splash(Vec3d p,double size,double angle,double stretch,int seed,float age,float alpha){
        if(age<0||age>=SPLASH_LIFE)return;
        float dry=Ease.smooth(age,.5f*SPLASH_LIFE,SPLASH_LIFE);
        splat(p,size*(.55+.45*Ease.out(age,0,.07f)),angle,stretch,seed&15,dry,alpha*(1-Ease.smooth(dry,.35f,1)));
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** The blended pass: the swords' bodies (the farthest first), the crystal flaring up where each strikes, the ink flung off. */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec;var all=blades(c.cast,t);var plans=plans(c.cast);if(plans==null)return;
        Integer[] order=new Integer[all.length];for(int i=0;i<all.length;i++)order[i]=i;
        Arrays.sort(order,Comparator.comparingDouble((Integer i)->all[i]==null?0:-all[i].mid().squaredDistanceTo(c.camera)));
        for(int i:order){
            var b=all[i];if(b==null||b.alpha()<=.005f)continue;
            var p=plans[i];float flare=flare(p,b,t,false),a=b.alpha()*(p.own()?1:.88f)*clearOf(b,c.camera);if(a<=.005f)continue;
            SwordFormation.sword(m,c.camera,b.pommel(),SwordFormation.frame(b.dir(),b.spin()),b.scale(),a,0,flare,0);
        }
        for(var p:plans)if(comes(c.cast,p.plant()))crystal(m,p,t,false);
        // the ink flung up as each strikes, falling (a clump breaks smaller as it flies)
        for(var p:plans){
            if(t<p.plant()||t>=p.plant()+BIT_LIFE+.05f||!comes(c.cast,p.plant()))continue;
            double floor=p.foot().y;
            for(int kind=0;kind<3;kind++){
                int n=kind==0?CLUMPS:kind==1?FLAKES:DROPS;
                for(int i=0;i<n;i++){
                    var b=inkBit(p,kind,i);double s=t-b.born();if(s<0||s>=BIT_LIFE)continue;
                    double l=b.lands(floor);if(s>=l)continue;
                    Vec3d q=b.at(s);float a=(1-Ease.smooth((float)s,BIT_LIFE-BIT_FADE,BIT_LIFE))*Ease.smooth((float)s,0,.03f)*near(q,c.camera);
                    double size=b.size()*(kind==0?1-.35*Ease.smooth((float)s,.2f,.7f):1);
                    if(kind==0)blot(m,q,b.moving(s),size,b.seed(),INK,.95f*a);
                    else if(kind==1)flake(m,q,size,s,b.spin(),b.seed(),INK,.95f*a);
                    else drop(m,q,b.moving(s),size,b.seed(),INK,.95f*a);
                }
            }
        }
        FinaleLights.alpha(c,m);
    }
    /**
     * The light under the ink: the flash and the ring at her feet as it begins; the streak up under her as she leaps; each
     * sword's glow and the white light it streaks behind it as it flies; each sword of light's burst as it shows; the arc of
     * light behind her as she rides round; her sword swung across in a blue arc, and into light; the white ring round her;
     * her eyes and her two fingers alight; as each strikes, a flash and a ring at its foot and the crystal's light; a ring
     * at her feet as she comes down; the band's burst; the swords going in light.
     */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;Frame fr=frame(c.cast);var plans=plans(c.cast);if(plans==null)return;
        var all=blades(c.cast,t);
        // as it begins: a flash of blue, a ring of light at her feet on the pommel
        Vec3d feet0=fr.spot.add(0,fr.pommel+.05,0);
        float flash=FxLib.kick(t,FINALE_FLASH,.12f);
        if(flash>.01f){m.softDisc(feet0.add(0,.9,0),3.2,BLUE,.5f*flash);m.softDisc(feet0.add(0,.9,0),1.1,PALE,.45f*flash);glint8(m,facing(feet0.add(0,1,0),c.camera),1.4*flash,WHITE,.7f*flash);}
        if(t>=FINALE_FLASH-.01f&&t<FINALE_FLASH+.45f){
            float u=Ease.unit(t,FINALE_FLASH,FINALE_FLASH+.45f);
            FxLib.ring(m,feet0,.3+3.2*Ease.out(t,FINALE_FLASH,FINALE_FLASH+.35f),.16,AZURE,.75f*(1-u));
            FxLib.ring(m,feet0,.2+2*Ease.out(t,FINALE_FLASH,FINALE_FLASH+.25f),.05,WHITE,.7f*(1-u));
        }
        // her own sword into light after her swing, and back as her camera comes back to her
        for(float at:new float[]{HAND_GONE,HAND_BACK}){
            float k=FxLib.kick(t,at,.1f);if(k<=.01f)continue;
            Vec3d h=hand(fr,at);if(h==null)continue;
            Vec3d o=toward(h,c.camera,.1);m.softDisc(o,.45,AZURE,.4f*k);glint8(m,o,.28*k,WHITE,.8f*k);
        }
        // the streak of light up under her as she leaps, until her sword is under her feet
        float up=Ease.window(t,FINALE_LEAP,FINALE_RIDE+.1f,.04f,.15f);
        if(up>.01f){Vec3d top=body(fr,t);m.softTaper(feet0,top,.9,.25,BLUE,.32f*up);m.line(feet0,top,.05,PALE,.6f*up);}
        // the swords: their glow, the light they streak behind them as they fly, the burst each of light shows out of
        for(int j=0;j<all.length;j++){
            var b=all[j];var p=plans[j];if(b==null)continue;
            float flare=flare(p,b,t,true),near=clearOf(b,c.camera);
            // (its outline lit less than the formation's under its night: against the dusk the fan was a mass of white)
            SwordFormation.swordGlow(m,b.pommel(),SwordFormation.frame(b.dir(),b.spin()),b.scale(),b.alpha()*near,0,GLOW_SKY,flare,0);
            if(near>.01f&&(b.flying()||t>=p.launch()&&t<p.plant()+STREAK_FLY))streak(m,fr,p,t,new Blade(b.mid(),b.dir(),b.spin(),b.alpha()*near,true,b.scale()));
            if(!p.own()){
                float k=FxLib.kick(t,p.start(),.09f);
                if(k>.01f){Vec3d o=toward(b.mid(),c.camera,.2);m.softDisc(o,.8,BLUE,.3f*k);glint8(m,o,.28*k,PALE,.6f*k);}
            }else{
                // pulled out: a flash of blue at its foot
                float k=FxLib.kick(t,p.start(),.12f);
                if(k>.01f){Vec3d g=p.planted().add(p.down().multiply(SwordFormation.S_LEN));m.softDisc(g.add(0,.3,0),1.2,BLUE,.35f*k);FxLib.ring(m,g.add(0,.05,0),.2+1.2*Ease.out(t,p.start(),p.start()+.25f),.06,PALE,.6f*k);}
            }
        }
        // the arc of light behind her as she rides round the sky
        trail(m,fr,t);
        // her sword swung across in the air
        swing(c,m,fr,t);
        // the white ring round her as she stands in the air (the videos' burst round her)
        ringBurst(c,m,fr,t);
        // her eyes and her two fingers alight
        seal(c,m,fr,t);
        // launched: a flash behind her as they go
        float go=FxLib.kick(t,FINALE_LAUNCH,.14f);
        if(go>.01f){Vec3d o=fr.at(-HUB_BACK-.4,FINALE_HOVER+HUB_UP+1.2,0);m.softDisc(o,3.2,BLUE,.22f*go);m.softDisc(o,1.1,PALE,.2f*go);}
        // each striking: a flash and a ring at its foot, the crystal's light
        for(var p:plans){
            if(!comes(c.cast,p.plant()))continue;
            float k=FxLib.kick(t,p.plant(),.1f);
            if(k>.01f){
                Vec3d g=p.foot();m.softDisc(g.add(0,.45,0),2.4,BLUE,.42f*k);m.star(g.add(0,.5,0),1.3,PALE,.5f*k);
                float u=Ease.unit(t,p.plant(),p.plant()+.3f);if(u<1)FxLib.ring(m,g.add(0,.05,0),.3+2.2*Ease.out(t,p.plant(),p.plant()+.28f),.1,AZURE,.6f*(1-u));
            }
            crystal(m,p,t,true);
        }
        landing(c,m,fr,t);
        burst(c,m,fr,t);
        // going: lit along its blade a moment, a scatter of motes off it
        for(int j=0;j<plans.length;j++){
            var p=plans[j];if(t<p.vanish()-.02f||t>=p.vanish()+.6f||!comes(c.cast,p.plant()))continue;
            Vec3d[] ends=stuck(p);Vec3d pommel=ends[0],point=ends[1];
            float g=Ease.window(t,p.vanish()-.02f,p.vanish()+FADE,.04f,.1f);
            if(g>.01f){m.softLine(pommel,point,.45,BLUE,.3f*g);m.softLine(pommel,point,.18,PALE,.45f*g);}
            for(int s=0;s<8;s++){
                int seed=p.seed()*7+s*13;float born=p.vanish()+.12f*(float)Ease.hash(seed);float u=Ease.unit(t,born,born+.45f);if(u<=0||u>=1)continue;
                Vec3d q=pommel.lerp(point,.1+.8*Ease.hash(seed+1)).add((Ease.hash(seed+2)-.5)*.6*u,1.6*u,(Ease.hash(seed+3)-.5)*.6*u);
                glint8(m,facing(q,c.camera),.22*(1-u),s%3==0?WHITE:s%3==1?PALE:CYAN,.8f*(1-u));
            }
        }
        FinaleLights.additive(c,m);
    }
    /**
     * How strongly sword {@code b} flares with light (SwordFormation.sword's flare: a broad glow along it): its own swords
     * a little as they fly, the swords of light as they fly, hers glowing as she rides it; standing in the fan a little,
     * and standing in the ground hardly (a broad glow round a standing sword looked like a pale slab, not a sword).
     */
    static float flare(Plan p,Blade b,float t,boolean light){
        if(p.steed()&&t<p.launch())return t<FINALE_RIDE?(light?.2f:.12f):(light?.3f:.2f);
        // (2.101.1: streaming after her they are many and near her: barely lit, else a white mass hides her)
        if(b.flying()&&t<p.launch())return p.own()?(light?.04f:.02f):(light?.08f:.1f);
        if(b.flying())return p.own()?(light?.2f:.14f):(light?.26f:.3f);
        if(t>=p.plant())return p.own()?0:.05f;
        return p.own()?0:.08f;
    }
    /**
     * How much of sword {@code b} shows by the camera (2.101.1's previews: a sword flying right by her close camera filled
     * the frame white): all of it a block and a half off, none within half a block.
     */
    static float clearOf(Blade b,Vec3d camera){return Ease.smooth((float)toSegment(camera,b.pommel(),b.point()),.5f,1.5f);}
    /** How far {@code p} is from the segment from {@code a} to {@code b}. */
    static double toSegment(Vec3d p,Vec3d a,Vec3d b){
        Vec3d ab=b.subtract(a);double l=ab.lengthSquared();double u=l<1e-9?0:MathHelper.clamp(p.subtract(a).dotProduct(ab)/l,0,1);
        return p.distanceTo(a.add(ab.multiply(u)));
    }
    /**
     * The white light a flying sword streaks behind it: back along its way a few hundredths of a second as it streams after
     * her; launched (2.101.3), a long streak from where it shot from to its point, shrinking into the ground over STREAK_FLY
     * once it strikes (the video's long white streaks).
     */
    static void streak(EffectMesh m,Frame fr,Plan p,float t,Blade b){
        boolean launched=t>=p.launch();
        float a=b.alpha()*(launched?.9f*(1-Ease.smooth(t,p.plant(),p.plant()+STREAK_FLY)):.32f);if(a<=.01f)return;
        if(launched){
            // (2.101.3) along its flight: a long streak from where it shot from to its point, shrinking into the ground
            float h=Math.min(t,p.plant()),s0=Math.max(p.launch(),t-STREAK_FLY);if(h-s0<.002f)return;
            double w=scale(p,h);int n=6;Vec3d prev=tip(fr,p,s0);
            for(int i=1;i<=n;i++){
                Vec3d q=tip(fr,p,s0+(h-s0)*i/n);float k=(float)i/n;
                m.softTaper(prev,q,.32*w*(k-1f/n),.32*w*k,BLUE,.32f*a);m.softTaper(prev,q,.12*w*(k-1f/n),.12*w*k,PALE,.6f*a);
                if(k>.35f)m.line(prev,q,.03*w,WHITE,.85f*a);
                prev=q;
            }
            return;
        }
        Vec3d head=b.point(),tail=null;
        for(float s=STREAK_SWIRL;s>.005f;s*=.5f){
            float at=Math.max(p.start(),t-s);Vec3d m0=mid(fr,p,at);
            if(m0!=null){tail=m0.add(b.dir().multiply(SwordFormation.S_LEN*scale(p,at)/2));break;}
        }
        if(tail==null||tail.squaredDistanceTo(head)<.01)return;
        double w=b.scale()*.7;
        m.softTaper(tail,head,0,.32*w,BLUE,.32f*a);m.softTaper(tail,head,0,.12*w,AZURE,.6f*a);m.line(tail.lerp(head,.35),head,.03*w,PALE,.85f*a);
    }
    /** A launched sword's point {@code t} seconds in (along its flight). */
    static Vec3d tip(Frame fr,Plan p,float t){return flightTip(fr,p,along(p,Math.min(t,p.plant())));}
    static final float STREAK_SWIRL=.045f,STREAK_FLY=.11f;
    /** How strongly its swords' outlines are lit (SwordFormation.swordGlow's sky: 1 the formation's own under its night). */
    static final float GLOW_SKY=.5f;
    /** Her pose {@code t} seconds in. */
    static R6.Pose pose(float t){return R6.poseOf(CLIP.frame(t));}
    /** Her right hand (where her sword is) and the way her sword points at {@code t} as drawn: round her lap and up it. */
    static Vec3d[] sword(Frame fr,float t){return KagenoClips.blade(body(fr,t),faces(fr,t),pose(t));}
    static Vec3d hand(Frame fr,float t){return sword(fr,t)[0];}
    /** Her head's middle at {@code t} as drawn. */
    static Vec3d head(Frame fr,float t){return KagenoClips.head(body(fr,t),faces(fr,t),pose(t));}
    /**
     * The arc of white and blue light behind her as she rides round the sky (2.101.1, the videos: "흰·파란 빛의 큰 호"):
     * back along her way under her feet some TRAIL seconds, broad and bright at her and thin and faint behind; drawn into her
     * as she stops.
     */
    static void trail(EffectMesh m,Frame fr,float t){
        if(t<FINALE_RIDE-.02f||t>=FINALE_LAP1+TRAIL+.02f)return;
        float head=Math.min(t,FINALE_LAP1),tail=Math.max(FINALE_RIDE-.06f,t-TRAIL);if(head-tail<.01f)return;
        float fade=Ease.smooth(t,FINALE_RIDE-.02f,FINALE_RIDE+.06f)*(1-Ease.smooth(t,FINALE_LAP1-.05f,FINALE_LAP1+TRAIL));
        int n=30;Vec3d prev=null;
        for(int i=0;i<=n;i++){
            float s=tail+(head-tail)*i/n;Vec3d q=body(fr,s).add(0,-.03,0);
            if(prev!=null){
                double k=i/(double)n,w=.3+.7*k;float a=(float)(k*k)*fade;
                m.softLine(prev,q,1.1*w,BLUE,.3f*a);m.softLine(prev,q,.42*w,AZURE,.45f*a);m.line(prev,q,.06*w,k>.6?WHITE:PALE,.75f*a);
            }
            prev=q;
        }
        if(prev!=null&&fade>.01f){m.softDisc(prev,.7,AZURE,.3f*fade);glint8(m,prev,.3,WHITE,.6f*fade);}
    }
    static final float TRAIL=.32f;
    /**
     * Her sword swung across in the air (2.101.1, the videos: "공중에서 칼을 휘둘러 파란 호"): a crescent of blue light where
     * her blade has gone from where it was drawn back to where it is, broad and bright at its head, white along its edge,
     * fading behind it and gone soon after.
     */
    static void swing(Fx c,EffectMesh m,Frame fr,float t){
        if(t<FINALE_SWING0||t>=FINALE_SWING1+SWING_FADE)return;
        float head=Math.min(t,FINALE_SWING1);if(head-FINALE_SWING0<.005f)return;
        int n=24;Vec3d[] in=new Vec3d[n+1],out=new Vec3d[n+1];float[] at=new float[n+1];
        for(int i=0;i<=n;i++){
            float s=FINALE_SWING0+(head-FINALE_SWING0)*i/n;at[i]=s;
            Vec3d[] b=sword(fr,s);in[i]=b[0].add(b[1].multiply(GUARD+.6*(TIP-GUARD)));out[i]=b[0].add(b[1].multiply(TIP+.3));
        }
        // (2.101.1's first preview: the whole sweep filled in, close before the camera, washed the frame white)
        for(int i=0;i<n;i++){
            float age=t-at[i+1];float a=(1-Ease.smooth(age,.03f,SWING_FADE))*(float)Math.pow((i+1)/(double)n,.6);
            // (none of it right before the camera: her blade swept at her close camera and its arc crossed the frame as a band)
            a*=Ease.smooth((float)Math.min(out[i].distanceTo(c.camera),out[i+1].distanceTo(c.camera)),1.2f,2.4f);if(a<=.01f)continue;
            // (an arc along where her point went: the band between it and her blade read as a white slab close before the camera)
            m.softLine(out[i],out[i+1],.3,BLUE,.22f*a);
            m.softLine(out[i],out[i+1],.12,AZURE,.38f*a);
            m.line(out[i],out[i+1],.04,WHITE,.7f*a);
        }
        if(t<FINALE_SWING1+.06f){float k=1-Ease.smooth(t,FINALE_SWING1,FINALE_SWING1+.06f);glint8(m,out[n],.18*k+.06,WHITE,.7f*k);}
    }
    static final float SWING_FADE=.28f;
    /** The white ring that bursts round her up there (a ring turned to the camera, soft and wide, with a flash), and a flat ring round her feet. */
    static void ringBurst(Fx c,EffectMesh m,Frame fr,float t){
        float at=FINALE_RING;if(t<at-.02f||t>=at+.45f)return;
        float a=Ease.smooth(t,at-.02f,at+.01f),u=Ease.unit(t,at,at+.4f);
        Vec3d feet=body(fr,t),o=feet.add(0,1.15,0);
        double r=.6+2.6*Ease.out(t,at,at+.24f);
        SwordCage.ringToCamera(m,o,c.camera,r,.6,PALE,.24f*a*(1-u));
        SwordCage.ringToCamera(m,o,c.camera,r*.96,.16,WHITE,.45f*a*(1-u));
        m.softDisc(toward(o,c.camera,.3),1.6,AZURE,.14f*a*(1-u)*(1-u));
        FxLib.ring(m,feet.add(0,.02,0),.4+4.5*Ease.out(t,at,at+.35f),.18,AZURE,.5f*a*(1-u));
    }
    /** Her eyes alight and a star at her two raised fingers while she holds them before her face (the videos' glowing eyes). */
    static void seal(Fx c,EffectMesh m,Frame fr,float t){
        float k=Ease.window(t,FINALE_SEAL0,FINALE_SEAL1+.12f,.08f,.12f);if(k<=.01f)return;
        // (small and on her: a star on her face at a close camera fills the frame and hides her)
        for(int s=-1;s<=1;s+=2){
            Vec3d eye=toward(onBody(fr,t,EYE_RT*s,EYE_UP,EYE_FW),c.camera,.04);
            m.softDisc(eye,.075,AZURE,.55f*k);m.softDisc(eye,.03,WHITE,.7f*k);
            if(s<0)glint8(m,eye,.1+.02*Math.sin(t*28),PALE,.75f*k);
        }
        var tip=GGesture.FINALE_TIP;
        Vec3d at=toward(onBody(fr,t,-tip.x/16,(24-tip.y)/16,-tip.z/16),c.camera,.05);
        m.softDisc(at,.2,AZURE,.3f*k);glint8(m,at,.085+.012*Math.sin(t*31),WHITE,.8f*k);
    }
    /** Where her eyes are on her face (blocks as the model is built: either side of its middle, over her feet, ahead). */
    static final double EYE_RT=.125,EYE_UP=1.72,EYE_FW=.255,MODEL_SCALE=.9375;
    /** A point on her torso {@code t} seconds in as she is drawn (the model drawn 0.9375 the size it is built). */
    static Vec3d onBody(Frame fr,float t,double rt,double up,double fw){
        Vec3d feet=body(fr,t);
        Vec3d q=KagenoClips.onTorso(feet,faces(fr,t),pose(t),rt,up,fw);
        return feet.add(q.subtract(feet).multiply(MODEL_SCALE));
    }
    /** {@code p} moved {@code by} blocks toward the camera (over what it lies on). */
    static Vec3d toward(Vec3d p,Vec3d camera,double by){Vec3d d=camera.subtract(p);double l=d.length();return l<1e-4?p:p.add(d.multiply(Math.min(by,l*.5)/l));}
    /** As she comes down onto the ground: a ring of blue light out round her feet and a soft flash. */
    static void landing(Fx c,EffectMesh m,Frame fr,float t){
        float at=FINALE_LAND;if(t<at-.02f||t>=at+.45f||!comes(c.cast,at))return;
        float u=Ease.unit(t,at,at+.42f),k=FxLib.kick(t,at,.12f);
        Vec3d g=fr.spot.add(0,.05,0);
        FxLib.ring(m,g,.3+2.6*Ease.out(t,at,at+.3f),.12,AZURE,.6f*(1-u));
        FxLib.ring(m,g,.2+1.6*Ease.out(t,at,at+.22f),.04,WHITE,.6f*(1-u));
        if(k>.01f)m.softDisc(g.add(0,.4,0),1.6,BLUE,.3f*k);
    }
    /**
     * The crystal flaring up where a sword strikes (the videos' blue crystal fire): sharp shards of blue crystal standing
     * up round its foot, each two crossing diamonds fixed in the world (fire is not turned to the camera), shooting up as it
     * strikes and breaking away; the blended pass draws their bodies, the light pass their glow and white hearts (2.101.1:
     * a third bigger, for the swords grown).
     */
    static void crystal(EffectMesh m,Plan p,float t,boolean light){
        float s=t-p.plant();if(s<0||s>=CRYSTAL)return;
        float fade=1-Ease.smooth(s,.2f,CRYSTAL);if(fade<=.01f)return;
        for(int i=0;i<SHARDS;i++){
            int seed=p.seed()*13+i*29;
            // (each its own moment, up in a few hundredths of a second, sharp: the videos' jagged blue crystal)
            float born=.035f*(float)Ease.hash(seed+7),grow=Ease.out(s,born,born+.05f);if(grow<=.01f)continue;
            Vec3d way=new Vec3d((Ease.hash(seed)-.5)*1.3,1,(Ease.hash(seed+1)-.5)*1.3).normalize();
            double len=CRYSTAL_SIZE*(1.1+1.7*Ease.hash(seed+2))*(i==0?1.25:1)*grow*(1+.18*s),w=CRYSTAL_SIZE*(.07+.09*Ease.hash(seed+3));
            Vec3d base=p.foot().add((Ease.hash(seed+4)-.5)*.7,-.05,(Ease.hash(seed+5)-.5)*.7).add(way.multiply(.35*s));
            Vec3d side=way.crossProduct(UP);side=side.lengthSquared()<1e-6?new Vec3d(1,0,0):side.normalize();
            double turn=Math.PI*Ease.hash(seed+6);side=side.multiply(Math.cos(turn)).add(way.crossProduct(side).multiply(Math.sin(turn))).normalize();
            Vec3d side2=way.crossProduct(side).normalize();
            Vec3d tip=base.add(way.multiply(len)),waist=base.add(way.multiply(len*(.2+.15*Ease.hash(seed+8))));
            if(!light){
                // a deep blue edge round a vivid blue body, on two crossing planes
                for(Vec3d sd:new Vec3d[]{side,side2}){
                    m.quad(base,waist.add(sd.multiply(w*1.35)),tip.add(way.multiply(.06)),waist.subtract(sd.multiply(w*1.35)),0x1b3ccf,.8f*fade);
                    m.quad(base,waist.add(sd.multiply(w)),tip,waist.subtract(sd.multiply(w)),i%3==0?0x8fd2ff:0x3f7dff,.85f*fade);
                }
            }else{
                m.softLine(base,tip,w*2.2,BLUE,.24f*fade);m.line(waist,tip,w*.28,WHITE,.85f*fade);m.line(base,waist,w*.4,PALE,.6f*fade);
                if(i==0)m.softDisc(base.add(0,.5,0),1.3,AZURE,.22f*fade);
            }
        }
    }
    static final int SHARDS=7;
    static final float CRYSTAL=.56f;
    static final double CRYSTAL_SIZE=1.6;
    /**
     * The band's burst as the last come down (FINALE_BURST, the videos' great blue flash): the ground of the band alight in
     * blue, a ring racing out along it, every sword lit up along its blade with a spike of light up out of its foot, and
     * stars rising all over it.
     */
    static void burst(Fx c,EffectMesh m,Frame fr,float t){
        float at=FINALE_BURST;if(t<at-.02f||t>=at+.9f||!comes(c.cast,at))return;
        float a=Ease.smooth(t,at-.02f,at+.01f),u=Ease.unit(t,at,at+.6f),fade=1-Ease.smooth(t,at+.08f,at+.7f);
        for(int i=0;i<5;i++){Vec3d o=fr.at(BAND_NEAR+(BAND_FAR-BAND_NEAR)*(i+.5)/5,.3,0);m.softDisc(o,7.5,BLUE,.22f*a*fade);m.softDisc(o,3.2,AZURE,.1f*a*fade);}
        Vec3d mid=fr.at((BAND_NEAR+BAND_FAR)/2,.06,0);
        FxLib.ring(m,mid,1+16*Ease.out(t,at,at+.5f),.5,AZURE,.6f*a*(1-u));
        FxLib.ring(m,mid,.6+11*Ease.out(t,at,at+.35f),.12,WHITE,.65f*a*(1-u));
        var plans=plans(c.cast);if(plans==null)return;
        float spike=a*(1-Ease.smooth(t,at+.05f,at+.4f));
        for(var p:plans){
            Vec3d[] ends=stuck(p);
            m.softLine(ends[0],ends[1],.9,CYAN,.35f*a*fade);
            if(spike>.01f)m.needle(p.foot(),p.foot().add(0,3+3.5*Ease.hash(p.seed()+40),0),.16,.4,Ease.hash(p.seed()+41)<.3?WHITE:PALE,.7f*spike);
        }
        for(int s=0;s<STARS;s++){
            int seed=s*53+911;float born=at+.02f+.18f*(float)Ease.hash(seed),v=Ease.unit(t,born,born+.7f);if(v<=0||v>=1)continue;
            Vec3d q=fr.at(BAND_NEAR+(BAND_FAR-BAND_NEAR)*Ease.hash(seed+1),.2+2.4*Ease.out(v,0,1),(Ease.hash(seed+2)-.5)*2*BAND_HALF);
            glint8(m,facing(q,c.camera),(.22+.2*Ease.hash(seed+3))*(1-.5*v),s%3==0?WHITE:s%3==1?PALE:CYAN,.85f*(1-v)*twinkle(t,seed));
        }
    }
    static final int STARS=48;
    /** Over the ink: a star at each sword's foot as it strikes, and a few stars on the swords standing in the band. */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec;var plans=plans(c.cast);if(plans==null)return;
        for(var p:plans){
            if(!comes(c.cast,p.plant()))continue;
            float k=FxLib.kick(t,p.plant(),.08f);
            if(k>.01f)glint8(m,facing(p.foot().add(0,.3,0),c.camera),1.2*k+.15,WHITE,.85f*k);
            if(t>p.plant()+.1f&&t<p.vanish()){
                Vec3d q=p.stuckMid().add(p.stuckDir().multiply(GROW*(-.3+.6*Ease.hash(p.seed()+60))));
                glint8(m,q,.18,Ease.hash(p.seed()+61)<.5?WHITE:PALE,.7f*twinkle(t,p.seed()));
            }
        }
        FinaleLights.overlay(c,m);
    }
    /**
     * The ink as each strikes (2.101.1: "그리고 꽂힐때마다 잉크가 튀어야해"; 2.101.2: "잉크가 더 많이 튀게"): the ground black
     * round its foot (a big splash, a long one thrown on ahead the way it drove in and a short one back, smaller ones flung
     * out round it a moment later), tall strokes of ink shooting up out of it and a crown of short ones flung out low round
     * it, eaten from below, and a splash where each clump and each drop flung off comes down.
     */
    static void ink(Fx c){
        float t=c.sec;var plans=plans(c.cast);if(plans==null)return;
        for(var p:plans){
            if(t<p.plant()||!comes(c.cast,p.plant()))continue;
            float age=t-p.plant();Vec3d g=p.foot();double floor=g.y;
            Vec3d fw=drove(p);double way=Math.atan2(fw.z,fw.x);
            splash(new Vec3d(g.x,floor+.004+.001*(p.seed()&7),g.z),SPLASH*(.85+.35*Ease.hash(p.seed()+70)),Math.PI*2*Ease.hash(p.seed()+71),1.25,p.seed(),age-.01f,.94f);
            for(int i=0;i<2;i++){
                double size=THROWN[3*i],out=THROWN[3*i+1],stretch=THROWN[3*i+2];Vec3d q=g.add(fw.multiply(out*(.85+.3*Ease.hash(p.seed()+72+i))));
                splash(new Vec3d(q.x,floor+.0045+.0005*i,q.z),SPLASH*size,way+(Ease.hash(p.seed()+74+i)-.5)*.3,stretch,p.seed()+3+2*i,age-.03f-.02f*i,.9f);
            }
            for(int i=0;i<SPLASH_RING;i++){
                int seed=p.seed()*11+i*37+2;double a=Math.PI*2*(i+.6*Ease.hash(seed))/SPLASH_RING,r=1.3+2.3*Ease.hash(seed+1);
                // (more of them, and farther, the way it drove in)
                Vec3d q=g.add(Math.cos(a)*r,0,Math.sin(a)*r).add(fw.multiply(.5*r*Math.max(0,Math.cos(a-way))));
                splash(new Vec3d(q.x,floor+.002+.0004*i,q.z),.55+.75*Ease.hash(seed+2),a,1.2+.8*Ease.hash(seed+3),seed,age-.04f-.12f*(float)Ease.hash(seed+4),.88f);
            }
            // where the flung clumps and drops come down
            for(int kind=0;kind<3;kind+=2){
                int n=kind==0?CLUMPS:DROPS;
                for(int i=0;i<n;i++){
                    var b=inkBit(p,kind,i);double l=b.lands(floor);if(l>BIT_LIFE)continue;
                    Vec3d q=b.at(l);
                    splatAt(new Vec3d(q.x,floor+.003+.0005*(i%6)+(kind==0?.002:0),q.z),b.size()*(kind==0?3:4.5),Math.PI*2*Ease.hash(b.seed()+9),1.3,b.seed(),(float)(t-b.born()-l),kind==0?.85f:.8f);
                }
            }
            if(age>=.6f)continue;
            // the tall strokes of ink shooting up out of it, twice crossed, eaten from below
            for(int i=0;i<SPIKES;i++){
                int seed=p.seed()*17+i*101+3;double a=Math.PI*2*(i+.5*Ease.hash(seed))/SPIKES;
                Vec3d out=new Vec3d(Math.cos(a),0,Math.sin(a)).add(fw.multiply(.35)).normalize();
                spike(g,floor,out,2.4+2*Ease.hash(seed+1),.42+.24*Ease.hash(seed+2),.2+.3*Ease.hash(seed+3),.15,seed,age,.01f,.07f,.18f,.52f);
            }
            // the crown: short strokes flung out low all round it
            for(int i=0;i<CROWN;i++){
                int seed=p.seed()*19+i*89+7;double a=Math.PI*2*(i+.4*Ease.hash(seed))/CROWN;
                Vec3d out=new Vec3d(Math.cos(a),0,Math.sin(a));
                spike(g,floor,out,1+.8*Ease.hash(seed+1),.22+.12*Ease.hash(seed+2),1+.6*Ease.hash(seed+3),.35+.25*Ease.hash(seed+4),seed,age,.0f,.05f,.14f,.44f);
            }
            // a ring of ink racing out on the ground round it (a brush ring, eaten as it goes)
            inkRing(g,floor+.006,p.seed(),age);
        }
    }
    /** The ring of ink racing out round a sword's foot {@code age} seconds after it struck: out to RING_OUT blocks in a blink, thinning, eaten from 0.3 s. */
    static void inkRing(Vec3d g,double y,int seed,float age){
        float drawn=Ease.out(age,0,.28f),eaten=Ease.smooth(age,.3f,.6f);if(drawn<=.01f||eaten>=1)return;
        double r=.4+(RING_OUT-.4)*drawn,w=.34-.2*drawn;int n=33;
        Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];double turn=Math.PI*2*Ease.hash(seed+77);
        for(int i=0;i<n;i++){
            double a=turn+Math.PI*2*i/(n-1),rr=r*(1+.07*Math.sin(3*a+seed));
            in[i]=new Vec3d(g.x+Math.cos(a)*(rr-w/2),y,g.z+Math.sin(a)*(rr-w/2));out[i]=new Vec3d(g.x+Math.cos(a)*(rr+w/2),y,g.z+Math.sin(a)*(rr+w/2));along[i]=i/(float)(n-1);
        }
        dev.sangwoo.celestialfall.client.KagenoFire.inkRibbon(in,out,along,n,4,seed&15,eaten,0,.9f);
    }
    static final double RING_OUT=3.4;
    /**
     * A stroke of ink shooting up out of {@code g} {@code age} seconds after it struck: from {@code from} blocks out along
     * {@code out} up {@code h} high, leaning out {@code lean} of its height, {@code w} wide, twice crossed; up between
     * {@code rise0} and {@code rise1}, eaten from below between {@code eat0} and {@code eat1}.
     */
    static void spike(Vec3d g,double floor,Vec3d out,double h,double w,double lean,double from,int seed,float age,float rise0,float rise1,float eat0,float eat1){
        float rise=Ease.out(age,rise0,rise1),eaten=Ease.smooth(age,eat0,eat1);if(rise<=0||eaten>=1)return;
        Vec3d foot=new Vec3d(g.x,floor,g.z).add(out.multiply(from));
        Vec3d top=foot.add(0,h,0).add(out.multiply(lean*h)),to=foot.add(top.subtract(foot).multiply(rise));
        IsosangSlash.strip(foot,to,new Vec3d(-out.z,0,out.x),w,4,seed&15,eaten,0,.95f);
        IsosangSlash.strip(foot,to,out,w*.85,4,(seed+5)&15,eaten,0,.95f);
    }
    /**
     * Lights: the flash as it begins, round her as she rides round with the swords after her, the fan, as each strikes, as
     * she comes down, the band's burst; the afterglow cut short while they swirl and fly (else they leave copies of
     * themselves on the way); a deep blue impact frame as the band bursts (a few frames, whole within 40 blocks, gone by 70).
     */
    static void frame(Fx c,FxFrame f){
        float t=c.sec;Frame fr=frame(c.cast);var plans=plans(c.cast);if(plans==null)return;
        float flash=FxLib.kick(t,FINALE_FLASH,.15f);if(flash>.01f)f.light(fr.spot.add(0,fr.pommel+1,0),0x9fc8ff,10,1.4f*flash);
        float swirl=Ease.window(t,FINALE_LEAP,GATHER0+GATHER+.2f,.1f,.25f);if(swirl>.01f)f.light(chest(fr,t),0x8cbcff,10,.8f*swirl);
        float fan=Ease.window(t,GATHER0+GATHER,FINALE_LAUNCH+.1f,.1f,.1f);if(fan>.01f)f.light(fr.at(-HUB_BACK,FINALE_HOVER+HUB_UP+1,0),0x8cbcff,8,.6f*fan);
        for(var p:plans){float k=comes(c.cast,p.plant())?FxLib.kick(t,p.plant(),.14f):0;if(k>.05f)f.light(p.foot().add(0,.8,0),0x9fd4ff,8,.9f*k);}
        if(comes(c.cast,FINALE_LAND)){float k=FxLib.kick(t,FINALE_LAND,.12f);if(k>.01f)f.light(fr.spot.add(0,.8,0),0x9fc8ff,6,.6f*k);}
        if(comes(c.cast,FINALE_BURST)){
            float b=FxLib.kick(t,FINALE_BURST,.3f);if(b>.01f)f.light(fr.at((BAND_NEAR+BAND_FAR)/2,1.5,0),0xa8d8ff,26,1.8f*b);
            float imp=GGrand.impact(t,FINALE_BURST);
            float near=(float)Math.max(0,Math.min(1,(70-c.camera.distanceTo(fr.at(8,1,0)))/30));
            if(imp>0&&near>0)f.impact(.7f*near*imp,0,SwordCage.CAGE_LOOK);
        }
        if(t>=FINALE_LAUNCH-.05f&&t<FINALE_PLANT1+.1f)f.afterglow(.02f);
        else if(t>=FINALE_LEAP&&t<GATHER0+GATHER+.1f||t>=FINALE_SWING0&&t<FINALE_SWING1+.1f)f.afterglow(.05f);
        FinaleLights.frame(c,f);
    }
    /**
     * A jolt as the swords strike: a small one for every sword as it slams in (2.101.3: a rumble while they come down), the
     * blows a little more, harder as the band bursts; each over two frames.
     */
    static float shake(StandClient.Cast cast,float sec){
        float v=0;
        for(int i=0;i<blows(FINALE);i++)if(comes(cast,blowAt(FINALE,i)/20f))v=Math.max(v,(i==blows(FINALE)-1?.85f:.4f)*bump(sec,blowAt(FINALE,i)/20f,.1f));
        var plans=plans(cast);
        if(plans!=null)for(var p:plans)if(comes(cast,p.plant()))v=Math.max(v,PLANT_JOLT*bump(sec,p.plant(),.08f));
        return v;
    }
    static final float PLANT_JOLT=.24f;

    // ───────────────────────────── her cutscene ─────────────────────────────

    /**
     * Its dusk (her cutscene's): the sky a red dusk and the ground round her darkened, coming in as it flashes and going as
     * her camera comes back to her; only on her own client, in her cutscene (IsosangCinema.dusk darkens the fog with it).
     */
    static void ground(Fx c){
        var own=cinematic();if(own==null||!own.id().equals(c.cast.id()))return;
        float k=dusk(c.cast,c.sec);if(k<=.005f)return;
        Frame fr=frame(c.cast);
        // (in draw calls of their own, once: in the blended pass, as the formation's night is, KagenoFire.inkInterlude)
        if(dev.sangwoo.celestialfall.client.KagenoFire.crescentGain>.99f)dev.sangwoo.celestialfall.client.KagenoFire.inkInterlude(()->{
            float blue=wash(c.sec),shine=k*moonLight(c.sec);Vec3d moon=duskMoon(fr,c.sec);
            FormationSky.draw(c,false,FormationSky.SOFT,1,m->{duskDome(m,fr.spot,k,blue);if(shine>.005f)moonDisc(m,fr,moon,shine);});
            // (2.101.2) the crescent moon in the dusk behind her, as the formation's in its night (FormationSky.moonLight)
            if(shine>.005f)FormationSky.draw(c,true,FormationSky.SOFT,FormationSky.MOON_GAIN,m->FormationSky.moonLight(c,m,moon,MOON_SHINE*shine));
            FormationSky.draw(c,false,.35f,1,m->veil(m,fr.spot,k));
        });
    }
    /**
     * The crescent moon in her cutscene's dusk (2.101.2, "엄청 멋있고 예쁘게": her sword's moon, as in the formation's
     * night): behind her as she stops in the air (DUSK_MOON blocks back, up, and to her left: some 14° over her close
     * camera's eye and 12° right of her, framed between the fan's swords spread behind her), rising a little through it;
     * the formation's moon (FormationSky.MOON_R round, lit low on its left as the camera sees it, its halo and its star).
     */
    static Vec3d duskMoon(Frame fr,float t){
        return fr.at(DUSK_MOON[0],DUSK_MOON[1]-DUSK_MOON_RISE*(1-Ease.smooth(t,FINALE_FLASH,FINALE_BACK0)),DUSK_MOON[2]);
    }
    static final double[] DUSK_MOON={-37,14.7,-8.5};
    static final double DUSK_MOON_RISE=2;
    /** How bright it is (0 to 1): coming after the blue flash, going with the dusk (SwordFinale.dusk does that). */
    static float moonLight(float t){return Ease.smooth(t,FINALE_FLASH+.08f,FINALE_FLASH+.7f);}
    static final float MOON_SHINE=.9f;
    /** Its dark disc over the dusk (the dusk's own colour there a little darker: its unlit part ashen). */
    static void moonDisc(EffectMesh m,Frame fr,Vec3d moon,float shine){
        Vec3d e=fr.spot.add(0,FormationSky.SKY_EYE,0),d=moon.subtract(e).normalize();
        double el=Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));
        FormationSky.disc(m,moon,FormationSky.MOON_R*1.005,FormationSky.scale(duskRgb(el),.8),Math.min(1,.96f*shine),.03);
    }
    /** How deep its dusk is {@code t} seconds in (0 to 1). */
    static float dusk(StandClient.Cast c,float t){return Ease.smooth(t,FINALE_FLASH,FINALE_FLASH+.12f)*(1-Ease.smooth(t,FINALE_BACK0,FINALE_BACK1));}
    /** The dusk's colours by elevation (degrees from its eye, a little over her): dark under the horizon, red low, orange, a deep red-brown overhead (the videos' burning sky). */
    static final double[] DUSK_EL={-16,-2,2,8,18,40,65,90};
    static final int[] DUSK_RGB={0x04060c,0x080a14,0x1e1208,0x341d08,0x3c2208,0x2c1808,0x1a100a,0x0e0a0e};
    /** The blue it flashes in with (the videos' blue flash over the world as the cutscene begins), how long it takes to go. */
    static final int WASH_RGB=0x1a2c78;
    static float wash(float t){return Ease.smooth(t,FINALE_FLASH-.02f,FINALE_FLASH+.04f)*(1-Ease.smooth(t,FINALE_FLASH+.08f,FINALE_FLASH+.55f));}
    static int duskRgb(double el){
        if(el<=DUSK_EL[0])return DUSK_RGB[0];
        for(int i=1;i<DUSK_EL.length;i++)if(el<=DUSK_EL[i]){double u=(el-DUSK_EL[i-1])/(DUSK_EL[i]-DUSK_EL[i-1]);return FormationSky.mix(DUSK_RGB[i-1],DUSK_RGB[i],u*u*(3-2*u));}
        return DUSK_RGB[DUSK_RGB.length-1];
    }
    static void duskDome(EffectMesh m,Vec3d spot,float k,float blue){
        Vec3d e=spot.add(0,FormationSky.SKY_EYE,0);int n=64;double[] rings=FormationSky.RINGS;double r=FormationSky.SKY_R*.9;
        for(int j=0;j+1<rings.length;j++){
            for(int i=0;i<n;i++){
                double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n;
                Vec3d p00=e.add(FormationSky.dir(a0,rings[j]).multiply(r)),p01=e.add(FormationSky.dir(a1,rings[j]).multiply(r));
                Vec3d p11=e.add(FormationSky.dir(a1,rings[j+1]).multiply(r)),p10=e.add(FormationSky.dir(a0,rings[j+1]).multiply(r));
                int c0=FormationSky.mix(duskRgb(rings[j]),WASH_RGB,blue),c1=FormationSky.mix(duskRgb(rings[j+1]),WASH_RGB,blue);float al=.96f*k;
                m.vertex(p00,c0,al);m.vertex(p01,c0,al);m.vertex(p11,c1,al);m.vertex(p10,c1,al);
            }
        }
    }
    /** The ground darkened round her (out to {@link #VEIL_R} blocks, softly to nothing at its edge). */
    static void veil(EffectMesh m,Vec3d spot,float k){
        int n=64,rings=6;Vec3d o=spot.add(0,.03,0);
        for(int j=0;j<rings;j++){
            double r0=VEIL_R*j/rings,r1=VEIL_R*(j+1)/rings;float a0=VEIL_ALPHA*k*(float)(1-Math.pow(j/(double)rings,2)),a1=VEIL_ALPHA*k*(float)(1-Math.pow((j+1)/(double)rings,2));
            for(int i=0;i<n;i++){
                double t0=Math.PI*2*i/n,t1=Math.PI*2*(i+1)/n;
                m.vertex(o.add(Math.cos(t0)*r0,0,Math.sin(t0)*r0),VEIL_RGB,a0);m.vertex(o.add(Math.cos(t1)*r0,0,Math.sin(t1)*r0),VEIL_RGB,a0);
                m.vertex(o.add(Math.cos(t1)*r1,0,Math.sin(t1)*r1),VEIL_RGB,a1);m.vertex(o.add(Math.cos(t0)*r1,0,Math.sin(t0)*r1),VEIL_RGB,a1);
            }
        }
    }
    static final double VEIL_R=36;
    static final float VEIL_ALPHA=.6f;
    static final int VEIL_RGB=0x04070e;
    /** The local player's own H again whose cutscene is on now (their camera's setting allowing), or null. */
    static StandClient.Cast cinematic(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||!mc.player.isAlive()||!CelestialFall.CONFIG.cinematicCamera)return null;
        var c=IsosangClient.ownFinale();if(c==null)return null;
        float t=c.age(mc.getTickDelta())/20f;return t>=FINALE_FLASH&&t<FINALE_BACK1?c:null;
    }
    /** A shot: where the camera stands, what it looks at, its field of view (degrees). */
    record Shot(Vec3d eye,Vec3d look,double fov){}
    /**
     * Her camera's shots {@code t} seconds in (the videos; 2.101.1, read again: "카메라는 아래에서 그녀를 따라 돎"): low before
     * where she stood and a little to her left, wide, following her up off the pommel and round her lap of the sky as she
     * rides it, rising to her as she comes back at it and stops in the air before it; in close before her up there as she
     * swings her sword, the swords spread behind her and she raises her fingers; cut round behind her at her right, the
     * band ahead and below, she at the left in the air, as they fly and come down and she comes down after them.
     */
    static Shot shot(StandClient.Cast c,float t){return shot(c,IsosangClient.finale(c.id()),t);}
    static Shot shot(StandClient.Cast c,IsosangClient.FinaleRun run,float t){
        Frame fr=frame(c,run);
        if(t<FINALE_LAP1)return lap(fr,t);
        if(t<CLOSE0){
            float u=Ease.smooth(t,FINALE_LAP1,CLOSE0);Shot a=lap(fr,FINALE_LAP1);
            return new Shot(a.eye().lerp(fr.at(CLOSE[0],FINALE_HOVER+CLOSE[1],CLOSE[2]),u),a.look().lerp(fr.at(0,FINALE_HOVER+CLOSE_LOOK,0),u),a.fov()+(FOV_CLOSE-a.fov())*u);
        }
        if(t<FINALE_LAUNCH){
            float u=Ease.unit(t,CLOSE0,FINALE_LAUNCH);
            return new Shot(fr.at(CLOSE[0]-CLOSE_IN*u,FINALE_HOVER+CLOSE[1],CLOSE[2]),fr.at(0,FINALE_HOVER+CLOSE_LOOK,0),FOV_CLOSE);
        }
        float u=Ease.unit(t,FINALE_LAUNCH,FINALE_BACK1);
        return new Shot(fr.at(BEHIND[0]+.8*u,BEHIND[1]+.3*u,BEHIND[2]),fr.at(BEHIND_LOOK[0],BEHIND_LOOK[1],BEHIND_LOOK[2]),FOV_BEHIND);
    }
    /** The lap's shot {@code t} seconds in: where it stands (eased from its start to its end), looking at her a moment late. */
    static Shot lap(Frame fr,float t){
        float u=Ease.smooth(t,FINALE_FLASH,FINALE_LAP1);
        Vec3d eye=fr.at(LAP0[0]+(LAP1[0]-LAP0[0])*u,LAP0[1]+(LAP1[1]-LAP0[1])*u,LAP0[2]+(LAP1[2]-LAP0[2])*u);
        // (it drifts after her a little round her lap, a moment late: following her from below, not left far behind)
        double[] l=finaleLap(Math.max(0,t-DRIFT_LAG));eye=eye.add(fr.f.multiply(l[0]*DRIFT)).add(fr.r.multiply(l[1]*DRIFT));
        Vec3d look=chest(fr,Math.max(0,t-LOOK_LAG)).add(0,.3,0);
        return new Shot(eye,look,FOV_LAP+(FOV_HOVER-FOV_LAP)*Ease.smooth(t,FINALE_CREST,FINALE_LAP1));
    }
    /** The lap's shot's place as it begins and as she stops before it (forward, up, right of where she stands), and how late it looks at her (seconds). */
    static final double[] LAP0={4.4,1.1,-1.6},LAP1={4.4,3.0,-.6};
    static final float LOOK_LAG=.07f,DRIFT_LAG=.12f;
    /** How much of her way round the lap's shot drifts after her. */
    static final double DRIFT=.3;
    /** In close before her up there (over her feet as she stands in the air: FINALE_HOVER), where it looks (her chest, her head and the fan's foot in frame), how far it comes in. */
    static final double[] CLOSE={2.95,1.5,.14};
    static final double CLOSE_LOOK=1.5,CLOSE_IN=.3;
    /**
     * Behind her at her right (she at the left of it, in the air), and what it looks at ahead (the band, a little to her
     * left). (2.101.3: further back and up, looking higher, so each sword is seen shooting past her into the band: 2.101.2's
     * was low and near, the fan over the frame. The swords wait round it, {@link #onScreen}.)
     */
    static final double[] BEHIND={-7.5,4.8,4.2},BEHIND_LOOK={8,2.6,-.9};
    static final double FOV_LAP=60,FOV_HOVER=54,FOV_CLOSE=50,FOV_BEHIND=74;
    static final float CLOSE0=1.5f;
    /** Her camera in its cutscene: the shot, short of a block between her and it; going back to the game's camera as it ends. */
    static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){
        var c=cinematic();if(c==null)return null;
        float t=c.age(delta)/20f;Shot s=shot(c,t);Frame fr=frame(c);
        Vec3d eye=clear(chest(fr,Math.min(t,FINALE_LAND)),s.eye());Vec3d d=s.look().subtract(eye);if(d.lengthSquared()<1e-6)d=fr.f;
        d=d.normalize();
        float sy=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),sp=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));
        float back=Ease.smooth(t,FINALE_BACK0,FINALE_BACK1);
        if(back<=0)return new CinematicDirector.Pose(eye,sy,sp,0);
        return new CinematicDirector.Pose(eye.lerp(vanilla,back),sy+MathHelper.wrapDegrees(yaw-sy)*back,sp+(pitch-sp)*back,0);
    }
    static double fov(double vanilla,float delta){
        var c=cinematic();if(c==null)return vanilla;
        float t=c.age(delta)/20f;double f=shot(c,t).fov();
        return f+(vanilla-f)*Ease.smooth(t,FINALE_BACK0,FINALE_BACK1);
    }
    /** How deep her cutscene's dusk is for the local player now (0 to 1: the fog darkens with it). */
    static float duskNow(float delta){var c=cinematic();return c==null?0:dusk(c,c.age(delta)/20f);}
    /** The camera at {@code cam} pulled in toward {@code from} short of a block between them. */
    static Vec3d clear(Vec3d from,Vec3d cam){return SwordCage.clear(from,cam);}
}
