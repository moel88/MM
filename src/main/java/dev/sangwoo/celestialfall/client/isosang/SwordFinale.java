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
 * nearest first, and streams after her swirling round her;</li>
 * <li>she comes back at the camera and stops in the air over where she stood, on her sword; she swings her own sword
 * across in a blue arc ({@link #swing}) and it goes into light; the swords gather behind her back and spread there like
 * wings, points out ({@link #fan}); a white ring bursts round her; she raises two fingers before her face, her eyes
 * alight;</li>
 * <li>her camera cuts round behind her, at a slant (2.101.9: {@link #BEHIND}), the swords waiting whole in a wave at the
 * left of its frame and a few more over the rest of it aimed at her foe, swords of light among them ({@link #LIGHT}:
 * more swords, 2.101.9), and they are fired at once, at random and fast, long white streaks across the frame coming down
 * slanting into the ground in a wide band ahead of her, growing to three times their size as they fly ({@link #GROW}),
 * blue crystal flaring up where each strikes and black ink flung up and splashed round it ({@link #ink}, {@link
 * #inkBit}), those of light bursting into light as they strike; she steps off her sword and comes down onto the ground
 * where she stood, her sword flung down into the band after the rest; the band flashes blue ({@link
 * IsosangRules#FINALE_BURST});</li>
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
     * {@link #steedMid}), and {@link #LIGHT} of light (2.101.9, user request: "검들은 더 많게"): they show in the wave as
     * the fan folds and burst into light as they strike, so that only the formation's 28 stand in the band (2.101.1, user
     * request: "박히는 검들 개수는 줄여줘 28개 정도 박으면 될듯"; 2.101.0 had as many of light as its own, joining the swirl
     * out of bursts of blue, 56 standing in the band).
     */
    static final int OWN=FORMATION_SWORDS,LIGHT=40,SWORDS=OWN+LIGHT;
    /**
     * One sword's way: its own (k &lt; OWN, the formation's sword k: where its pommel stood, its way down, its guard's turn)
     * or of light; whether it is the one she rides; when it is pulled out (or shows out of the light); its swirl after her
     * (how far behind her along her way, how far round her, how high, its angle at the start and its speed round); when it
     * goes to its place in the fan behind her and where that is (its middle and the way it points; one of light none: its
     * place in the wave); where it waits in the wave as her camera cuts behind her (its middle and the way it points: the
     * way it shoots off); when it is launched and where it comes down (the middle of it standing in the ground slanting,
     * grown, and the way it points); when it strikes and when it is gone; its seed; its guard's turn as it waits there
     * (its flat to her camera: {@link #flatTo}); when it shows there (as her camera cuts, or a moment after the last of
     * its place was fired: {@link #RELOAD}) and which place it is ({@link #slotSpots}).
     */
    record Plan(int k,boolean own,boolean steed,Vec3d planted,Vec3d down,double spin,float start,float join,float lag,double orbitR,double orbitUp,double orbitA,double orbitW,
                float gather,Vec3d fanMid,Vec3d fanDir,Vec3d readyMid,Vec3d aimDir,float launch,Vec3d foot,Vec3d stuckMid,Vec3d stuckDir,float plant,float vanish,int seed,
                double face,float appear,int slot){}
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
    static final int RINGS=4,PER_RING=OWN/RINGS;
    /**
     * The band's swords (blocks from where she stands): from {@link #BAND_NEAR} out to {@link #BAND_FAR}, {@link #BAND_HALF}
     * either side (inside the band that strikes, IsosangRules.finaleIn), {@link #BURY} blocks of them in the ground as drawn
     * at their size (2.101.1: as much further for them grown, and the nearest further out, so their hilts lean back toward
     * her short of where she comes down).
     */
    static final double BAND_NEAR=5.5,BAND_FAR=15,BAND_HALF=7.6,BURY=.55;
    /**
     * How they strike (2.101.3, user request: "검 박힐때 엄청 빠르게 박혀야 될 거 같아 원본 영상도 엄청 빠르고 매섭게 박히거든",
     * "그리고 위쪽으로 검이 나가서 박히는 느낌이 아니고"; 2.101.8: "이제 검을 연사하는 거 처럼 빨리 쏘면 돼"; 2.101.9, user
     * request: "그냥 검들 바로 쏘게 바꿔 지금 조금 기다리고 쏘는데 그냥 바로 쏴 카메라 뒤로 이동하자마자 그리고 순서대로 나가는거
     * 같은데 무작위로 빠르게 쏘게 검들은 더 많게"; 2.101.8 fired them nearest first from 2.43 s): as she sends them
     * ({@link #FOLD0} to {@link #FOLD1}, before her camera cuts behind her) the fan folds, every sword going to its place
     * in the wave they wait in ({@link #waveSpots}), the swords of light showing there among them; from the cut
     * (FINALE_LAUNCH) they are fired at random, one every {@link #FIRE_STEP} seconds (a little either way, {@link
     * #FIRE_JITTER} of it): each drawn back a little ({@link #AIM} seconds before it is fired, {@link #DRAW} blocks), then
     * shot off the way it points, faster and faster (its way to the power {@link #STRIKE_EASE}), bending in to where it
     * strikes, its point dipping into the ground a little steeper than it came (up to {@link #STEEPER} degrees more, at
     * most {@link #SLANT_MAX} below level, never so much that it would go up: {@link #steeper}), {@link #FLIGHT} to
     * FLIGHT+{@link #FLIGHT_SPREAD} seconds into the ground ({@link #STEED_FLIGHT} hers, from under her feet as she steps
     * off it, last); it drives {@link #DRIVE} blocks further in than it stands and springs back over {@link #SETTLE}
     * seconds. The server's blows fall as they come down (IsosangRules.BLOWS).
     */
    static final float FOLD0=2.16f,FOLD1=2.3f;
    static final float FIRE_STEP=.006f,FIRE_JITTER=.35f;
    /**
     * Where they wait as her camera cuts behind her (2.101.9, user request: "카메라는 검의 진행 방향을 정면에서 바라보지
     * 말고, 진행 방향의 측후방에서 비스듬히 바라보는 3/4 측면 시점으로 설정해", "검의 끝부분만 보이는 정면 시점이 아니라 검날의
     * 전체 길이와 옆면이 드러나는 구도로", "가까운 검은 크고 길게, 멀리 있는 검은 작게", "여러 검이 화면의 서로 다른 높이와 깊이에",
     * then "검이 너무 모여있어 내가 보낸 사진처럼 조금만 모여있게", "그냥 저 각도에서 방금 내가 표시한 범위에서 골고루 나오게
     * 바꿔줘" and their answers: under the red line, all of it, every sword aimed at her foe, about 20 at a time, each place
     * filled again by a sword of light as its sword is fired, 67 in all; 2.101.8's points only, out of her camera's
     * screen, are gone, and the first preview's wall of them at the left of the frame): whole swords, their flats to her
     * camera ({@link #flatTo}), in {@link #WAVE_SLOTS} places spread evenly over her frame under the user's line
     * ({@link #RED_LINE}, {@link #SLOT_LINE} under it: Lloyd relaxation over it), off her and off her foe and wherever a
     * sword aimed at her foe would show as less than {@link #MIN_LEN} of the frame's height; each as deep as keeps it
     * {@link #SLOT_UP} blocks off the ground, some way between {@link #SLOT_NEAR} and {@link #SLOT_FAR}; each aimed
     * straight at her foe ({@link #foe}, at least {@link #AIM_DOWN} down) and striking where that line meets the ground
     * on through it (no further than {@link #FOE_SPREAD} from it, in the band). As her camera cuts behind her every place
     * holds one of her own; from then they are fired at random, one every FIRE_STEP, each from one of the places whose
     * sword has waited there {@link #RELOAD_WAIT} at least, and {@link #RELOAD} after each is fired the next shows in its
     * place (a little aside, {@link #SLOT_JITTER}), showing over {@link #RELOAD_IN}: the rest of her own (gone into light
     * as the fan folds) and those of light, at random.
     */
    static final int WAVE_SLOTS=20;
    static final double SLOT_LINE=.1,SLOT_NEAR=2.2,SLOT_FAR=5,SLOT_UP=2,SLOT_JITTER=.03,MIN_LEN=.07,AIM_DOWN=.08,FACE_JITTER=14,PLACE_GAP=.45,SWORD_GAP=.15;
    static final float RELOAD=.015f;
    /**
     * How they come out through her camera's screen (2.101.9, user: "검이 화면을 뚫고 나오지 않음"; 2.101.8: "아니 카메라를
     * 뚫고 나와야한다니까?"): each shows behind her behind camera's screen, {@link #SCREEN} blocks in front of its lens (square
     * to the way it looks), its point at it, and comes out through it into its place along the line from the lens through
     * that over {@link #EMERGE} (fast, slowing into its place: {@link #outOf}), its part still behind the screen not drawn
     * (faint over {@link #FADE_IN} from it); her own as her camera cuts behind her (one after another over its first
     * {@link #EMERGE_SPREAD}), each of the rest RELOAD after the last of its place was fired; each fired once it has
     * waited {@link #WAIT_OUT} in its place, the first {@link #FIRE0} (as soon as one has come out).
     */
    static final double SCREEN=.9,FADE_IN=.12;
    static final float EMERGE=.06f,EMERGE_SPREAD=.05f,WAIT_OUT=.015f,FIRE0=FINALE_LAUNCH+EMERGE+WAIT_OUT;
    /**
     * The line the user drew on a still of her behind camera's shot (2.101.8, tools/isosang/finale_layout.py traced it:
     * -1 to 1 across and up the screen, 16:9): the swords wait under it (2.101.8: "빨간선 아래에 전부 골고루 배치하면 돼").
     */
    static final double[][] RED_LINE={{-1.000,0.896},{-0.979,0.889},{-0.896,0.804},{-0.771,0.711},{-0.604,0.604},{-0.438,0.496},{-0.271,0.407},{-0.104,0.337},{0.062,0.233},{0.229,0.119},{0.396,0.041},{0.562,-0.048},{0.729,-0.163},{0.854,-0.215},{1.000,-0.252}};
    /** The height of the user's line at {@code x} across her behind camera's screen. */
    static double redLine(double x){
        for(int i=1;i<RED_LINE.length;i++)if(x<=RED_LINE[i][0]){double[] a=RED_LINE[i-1],b=RED_LINE[i];return a[1]+(b[1]-a[1])*(x-a[0])/(b[0]-a[0]);}
        return RED_LINE[RED_LINE.length-1][1];
    }
    /** How far her body is clear of the waiting swords on her behind camera's screen (half her width, blocks; how far on its screen). */
    static final double HER_HALF=.45,HER_CLEAR=.03;
    /** How long one of light stands struck before it bursts into light, and how long it takes to go. */
    static final float LIGHT_STAY=.04f,LIGHT_FADE=.12f;
    /** How far a waiting sword breathes on and back along its way, and up and down (blocks). */
    static final double BREATHE=.03,BOB=.025;
    /** How far back from its point a sword's guard is (the root of its blade: its guard's ends swept toward the point). */
    static final double HILT=SwordFormation.S_LEN-SwordFormation.S_GUARD-SwordFormation.S_SWEEP;
    static final double STEEPER=18,SLANT_MAX=62,SLANT_JITTER=6;
    static final float FLIGHT=.07f,FLIGHT_SPREAD=.04f,AIM=.07f,STEED_FLIGHT=.11f,SETTLE=.12f;
    static final double DRAW=.2,DRIVE=.3,STRIKE_EASE=1.7;
    /**
     * How big the swords grow as they fly down into the band (2.101.1, user request: "그리고 박히는 검들은 더 커야해", their
     * choice: twice as big, growing as they fly; then "박히는 검들 1.5배로 더 키우고": half as big again, three times the
     * formation's): their size from the fan (1) to standing in the ground.
     */
    static final double GROW=3;
    /** Each of its own swords' slot in the fan (one each; those of light are never in it). */
    static int slot(int j){return j;}
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
        int salt=run.form().hashCode()*31+13,her=run.on()>=0&&run.on()<OWN?run.on():-1;
        // (2.101.9) the places they wait in as her camera cuts behind her, each holding one of her own then; fired at
        // random from then ("무작위로 빠르게 쏘게"), each place filled again a moment after its sword is fired, by the rest
        // of her own and those of light at random, until all are fired
        Vec3d flow=flow(fr);
        int n=SWORDS-(her<0?0:1),slots=Math.min(WAVE_SLOTS,OWN-(her<0?0:1));
        double[][] spot=slotSpots(fr,slots,salt,flow);
        var owns=new java.util.ArrayList<Integer>();for(int j=0;j<OWN;j++)if(j!=her)owns.add(j);
        owns.sort(Comparator.comparingDouble((Integer j)->Ease.hash(j*977+salt+40)));
        var after=new java.util.ArrayList<Integer>(owns.subList(slots,owns.size()));for(int j=OWN;j<SWORDS;j++)after.add(j);
        after.sort(Comparator.comparingDouble((Integer j)->Ease.hash(j*977+salt+42)));
        float[] launches=new float[SWORDS],appears=new float[SWORDS],readyAt=new float[slots];int[] slotOf=new int[SWORDS],nth=new int[SWORDS],held=new int[slots],count=new int[slots];
        Arrays.fill(slotOf,-1);
        for(int k=0;k<slots;k++){held[k]=owns.get(k);appears[held[k]]=FOLD1+(k==0?0:EMERGE_SPREAD*(float)Ease.hash(salt*17+k*5));readyAt[k]=appears[held[k]]+EMERGE+WAIT_OUT;slotOf[held[k]]=k;}
        int made=slots,next=0;
        for(int i=0;i<n;i++){
            float t=FIRE0+FIRE_STEP*(i+(i==0?0:FIRE_JITTER*(2*(float)Ease.hash(salt*7+i)-1)));
            // one of the places whose sword has waited long enough, at random among those fired the fewest times (else the
            // one soonest ready, waited for)
            int k=-1;double pick=1e9;
            for(int q=0;q<slots;q++)if(held[q]>=0&&readyAt[q]<=t){double h=count[q]+.9*Ease.hash(salt*13+i*7+q*3);if(h<pick){pick=h;k=q;}}
            if(k<0){float soon=Float.MAX_VALUE;for(int q=0;q<slots;q++)if(held[q]>=0&&readyAt[q]<soon){soon=readyAt[q];k=q;}t=Math.max(t,soon);}
            launches[held[k]]=t;
            if(made<n){int j=after.get(next++);held[k]=j;appears[j]=t+RELOAD;readyAt[k]=t+RELOAD+EMERGE+WAIT_OUT;slotOf[j]=k;nth[j]=++count[k];made++;}
            else held[k]=-1;
        }
        Vec3d eye=folded(fr)[0];double[] box=herBox(fr);
        // (2.101.9: "적을 바라보지 않게하자") each in its place (a little aside of it when it fills it again), all pointing
        // much the same way (FLOW: the photo's slant), each turned a little to where it strikes: an even lattice over the
        // band, each the one nearest the way they all point from where it waits (assign)
        Vec3d[] mids=new Vec3d[SWORDS];var waved=new java.util.ArrayList<Integer>();
        for(int j=0;j<SWORDS;j++){
            if(j==her)continue;waved.add(j);double[] at=spot[slotOf[j]];int seed=j*977+salt;
            if(nth[j]>0){
                double[] a={at[0]+SLOT_JITTER*(2*Ease.hash(seed+30)-1),at[1]+SLOT_JITTER*(2*Ease.hash(seed+31)-1),at[2]*(1+.05*(2*Ease.hash(seed+32)-1))};
                Vec3d m=onScreen(fr,a[0],a[1],a[2]);boolean clear=shows(fr,box,m,flow);
                for(int q=0;q<slots&&clear;q++)if(q!=slotOf[j])clear=gap(m,flow,onScreen(fr,spot[q][0],spot[q][1],spot[q][2]),flow)>=PLACE_GAP-.1;
                if(clear)mids[j]=m;
            }
            if(mids[j]==null)mids[j]=onScreen(fr,at[0],at[1],at[2]);
        }
        double[][] marks=bandMarks(waved.size(),salt);Vec3d[] marked=new Vec3d[marks.length],from=new Vec3d[waved.size()];
        for(int i=0;i<marks.length;i++)marked[i]=ground(mc,fr.at(marks[i][0],0,marks[i][1]),fr.spot.y);
        for(int i=0;i<from.length;i++)from[i]=mids[waved.get(i)];
        int[] markOf=assign(from,marked,flow);Vec3d[] feet=new Vec3d[SWORDS],aims=new Vec3d[SWORDS];
        for(int i=0;i<from.length;i++){
            int j=waved.get(i);feet[j]=marked[markOf[i]];aims[j]=aimed(flow,feet[j].subtract(mids[j]).normalize());
            // (turned to where it strikes it would not show whole in her frame, or would cross her: the way they all point)
            if(!shows(fr,box,mids[j],aims[j]))aims[j]=aimed(flow,flow);
        }
        // (turned, any two waiting at once must not be in one another: each so turned the way they all point instead)
        for(int a:waved)for(int b:waved){
            if(a>=b||launches[a]<=appears[b]||launches[b]<=appears[a]||gap(mids[a],aims[a],mids[b],aims[b])>=SWORD_GAP)continue;
            aims[a]=aimed(flow,flow);aims[b]=aimed(flow,flow);
        }
        Plan[] out=new Plan[SWORDS];
        for(int j=0;j<SWORDS;j++){
            int seed=j*977+salt;boolean own=j<OWN,steed=j==her;
            Vec3d planted=null,down=new Vec3d(0,-1,0);double spin=360*Ease.hash(seed+1);float start,join=JOIN;
            if(own){
                var b=layout[j];planted=SwordFormation.planted(run.centre(),run.formYaw(),b);down=SwordFormation.down(b);
                spin=SwordFormation.spinAt(b,formT+pulls[j]);start=pulls[j];
                // (the farther it stood from her, the longer it takes to fly up into the swirl after her)
                join=JOIN+JOIN_PER*(float)SwordFormation.foot(run.centre(),run.formYaw(),b).distanceTo(fr.spot);
            }else start=appears[j];
            float lag=LAG0+LAG*(float)Ease.hash(seed+15);
            double orbitR=1.8+1.8*Ease.hash(seed+3),orbitUp=-.8+1.8*Ease.hash(seed+4);
            double orbitA=Math.PI*2*Ease.hash(seed+5),orbitW=(4+2*Ease.hash(seed+6))*(Ease.hash(seed+16)<.5?-1:1);
            // (it goes on into the fan once it has joined the swirl: never off its way into it)
            float gather=own?Math.max(GATHER0+GATHER_SPREAD*(float)Ease.hash(seed+7),start+RISE+join+.04f):start;
            Vec3d foot,readyMid,aimDir;float plant,launch,appear;
            if(steed){
                // hers, the one she rides: in the near middle of the band, flung there last as she steps off it
                foot=ground(mc,fr.at(BAND_NEAR+.5,0,(Ease.hash(seed+9)-.5)*.8),fr.spot.y);
                readyMid=rideMid(fr,FINALE_DESCEND);aimDir=foot.subtract(readyMid).normalize();
                launch=FINALE_DESCEND+.06f;plant=launch+STEED_FLIGHT;gather=launch;appear=FINALE_RIDE;
            }else{
                readyMid=mids[j];aimDir=aims[j];foot=feet[j];
                launch=launches[j];appear=appears[j];
                plant=launch+FLIGHT+FLIGHT_SPREAD*(float)Ease.hash(seed+13);
            }
            // its place in the fan behind her (her own; one of light is never in it)
            Vec3d fanMid=readyMid,fanDir=aimDir;
            if(own){
                int s=slot(j);double phi=slotAngle(s),rho=RING0+RING_STEP*ring(s);
                Vec3d radial=fr.r.multiply(Math.sin(phi)).add(0,Math.cos(phi),0);
                fanDir=radial.subtract(fr.f.multiply(.2)).normalize();
                Vec3d fanPommel=hub.add(radial.multiply(rho)).subtract(fr.f.multiply(.12+.16*rho));
                fanMid=fanPommel.add(fanDir.multiply(SwordFormation.S_LEN/2));
            }
            // it shoots off the way it points, bends in to where it strikes and dips into the ground a little steeper
            Vec3d stuckDir=steeper(readyMid,aimDir,foot,STEEPER+SLANT_JITTER*(2*Ease.hash(seed+20)-1));
            Vec3d point=foot.add(stuckDir.multiply(BURY*GROW)),stuckMid=point.subtract(stuckDir.multiply(SwordFormation.S_LEN*GROW/2));
            // (2.101.9) one of light bursts into light a moment after it strikes; her own stand until the end
            float vanish=own?FINALE_GONE0+(FINALE_GONE1-FINALE_GONE0-.2f)*(float)Ease.hash(seed+14):plant+LIGHT_STAY;
            double face=steed?0:flatTo(aimDir,readyMid,eye)+FACE_JITTER*(2*Ease.hash(seed+24)-1);
            out[j]=new Plan(j,own,steed,planted,down,spin,start,join,lag,orbitR,orbitUp,orbitA,orbitW,gather,fanMid,fanDir,readyMid,aimDir,launch,foot,stuckMid,stuckDir,plant,vanish,seed,face,appear,steed?-1:slotOf[j]);
        }
        return out;
    }
    /**
     * The way they all point (2.101.9, user request: "적을 바라보지 않게하자", the other of the two ways shown them: all the
     * same way, as in their photo, from the left of the frame on into it to the right): through {@link #FLOW} on her
     * behind camera's screen as it is before it is rolled, on to her right, away and down.
     */
    static Vec3d flow(Frame fr){
        Vec3d[] b=behind(fr,FOLD1);Vec3d eye=b[0],fw=b[1].subtract(eye).normalize(),right=fw.crossProduct(UP).normalize(),up=right.crossProduct(fw);
        double v=Math.tan(Math.toRadians(FOV_BEHIND/2)),h=v*16/9.0;
        return fw.add(right.multiply(FLOW[0]*h)).add(up.multiply(FLOW[1]*v)).normalize();
    }
    static final double[] FLOW={1.15,-.35};
    /** How far each is turned from the way they all point to where it strikes (of the angle between). */
    static final double TO_MARK=.25;
    /** The way a sword points: {@code flow} turned TO_MARK of the way to {@code to} (where it strikes), at least AIM_DOWN down. */
    static Vec3d aimed(Vec3d flow,Vec3d to){
        Vec3d d=slerp(flow,to,TO_MARK);
        if(d.y>-AIM_DOWN){Vec3d level=new Vec3d(d.x,0,d.z).normalize();d=level.multiply(Math.sqrt(1-AIM_DOWN*AIM_DOWN)).add(0,-AIM_DOWN,0);}
        return d;
    }
    /** {@code n} places over the band, evenly (a golden lattice: how far into it and how far aside, blocks). */
    static double[][] bandMarks(int n,int salt){
        double g=(Math.sqrt(5)-1)/2,off=Ease.hash(salt+5);double[][] out=new double[n][];
        for(int i=0;i<n;i++){double a=(i+.5)/n,b=(i*g+off)%1;out[i]=new double[]{BAND_NEAR+(BAND_FAR-BAND_NEAR)*a,(2*b-1)*BAND_HALF*.92};}
        return out;
    }
    /**
     * Which of {@code to} each of {@code from} strikes: each a different one, all of them as near the way {@code flow} as
     * may be (the sum of the squared turns off it least: each in turn its nearest left, the hardest first, then any two
     * swapped that turn less so).
     */
    static int[] assign(Vec3d[] from,Vec3d[] to,Vec3d flow){
        int n=from.length;double[][] cost=new double[n][n];
        for(int i=0;i<n;i++)for(int j=0;j<n;j++){double a=Math.acos(MathHelper.clamp(to[j].subtract(from[i]).normalize().dotProduct(flow),-1,1));cost[i][j]=a*a;}
        int[] m=new int[n];boolean[] used=new boolean[n];double[] best=new double[n];Integer[] order=new Integer[n];
        for(int i=0;i<n;i++){order[i]=i;best[i]=1e9;for(int j=0;j<n;j++)best[i]=Math.min(best[i],cost[i][j]);}
        Arrays.sort(order,Comparator.comparingDouble(i->-best[i]));
        for(int i:order){int bj=-1;for(int j=0;j<n;j++)if(!used[j]&&(bj<0||cost[i][j]<cost[i][bj]))bj=j;m[i]=bj;used[bj]=true;}
        for(int pass=0;pass<8;pass++){
            boolean better=false;
            for(int a=0;a<n;a++)for(int b=a+1;b<n;b++)if(cost[a][m[b]]+cost[b][m[a]]<cost[a][m[a]]+cost[b][m[b]]-1e-12){int x=m[a];m[a]=m[b];m[b]=x;better=true;}
            if(!better)break;
        }
        return m;
    }
    /**
     * Its {@code n} places on her behind camera's screen as it cuts behind her ({@link #onScreen}'s: across, up, and how
     * deep each sword's middle is), spread evenly over where one may wait (under the user's line, off her, whole in the
     * frame and showing as a blade, pointing {@code flow}: {@link #shows}), by Lloyd relaxation from places picked at random.
     */
    static double[][] slotSpots(Frame fr,int n,int salt,Vec3d flow){
        double[] box=herBox(fr);
        var grid=new java.util.ArrayList<double[]>();
        for(int gy=0;gy<40;gy++)for(int gx=0;gx<64;gx++){
            double x=-.94+1.88*(gx+.5)/64,y=-.88+1.8*(gy+.5)/40;
            if(y>redLine(x)-SLOT_LINE)continue;
            Vec3d m=onScreen(fr,x,y,slotDepth(fr,x,y,.7));
            if(shows(fr,box,m,flow))grid.add(new double[]{x,y});
        }
        double[][] c=new double[n][];
        for(int k=0;k<n;k++)c[k]=grid.get((int)(Ease.hash(salt*29+k*13)*grid.size())%grid.size()).clone();
        for(int it=0;it<40;it++){
            double[][] sum=new double[n][3];
            for(double[] g:grid){int b=0;double bd=1e9;for(int k=0;k<n;k++){double d=sq((g[0]-c[k][0])*16/9.0)+sq(g[1]-c[k][1]);if(d<bd){bd=d;b=k;}}sum[b][0]+=g[0];sum[b][1]+=g[1];sum[b][2]++;}
            for(int k=0;k<n;k++)if(sum[k][2]>0){c[k][0]=sum[k][0]/sum[k][2];c[k][1]=sum[k][1]/sum[k][2];}
        }
        double[][] out=new double[n][];Vec3d[] mids=new Vec3d[n];
        for(int k=0;k<n;k++){
            // (snapped to the nearest where one may wait, in case its cell's middle is not, and its sword clear of those
            // before it by PLACE_GAP: one in front of another on her screen may be in it)
            final double[] ck=c[k];
            var near=new java.util.ArrayList<>(grid);near.sort(Comparator.comparingDouble(g->sq((g[0]-ck[0])*16/9.0)+sq(g[1]-ck[1])));
            for(int i=0;i<near.size()&&out[k]==null;i++){
                double[] g=near.get(i);
                for(double u:new double[]{.45+.55*Ease.hash(salt*31+k*7),.7,.2}){
                    double z=slotDepth(fr,g[0],g[1],u);Vec3d m=onScreen(fr,g[0],g[1],z);
                    if(!shows(fr,box,m,flow))continue;
                    boolean clear=true;for(int q=0;q<k&&clear;q++)clear=gap(m,flow,mids[q],flow)>=PLACE_GAP;
                    if(clear){out[k]=new double[]{g[0],g[1],z};mids[k]=m;break;}
                }
            }
            if(out[k]==null){double[] g=near.get(0);double z=slotDepth(fr,g[0],g[1],.7);out[k]=new double[]{g[0],g[1],z};mids[k]=onScreen(fr,g[0],g[1],z);}
        }
        return out;
    }
    /** How far apart two swords are (their middles {@code a} and {@code b}, pointing {@code da} and {@code db}): the least of their blades' distances. */
    static double gap(Vec3d a,Vec3d da,Vec3d b,Vec3d db){
        double d=1e9;Vec3d a0=a.subtract(da.multiply(SwordFormation.S_LEN/2)),b0=b.subtract(db.multiply(SwordFormation.S_LEN/2));
        for(int u=0;u<=8;u++)for(int v=0;v<=8;v++)d=Math.min(d,a0.add(da.multiply(SwordFormation.S_LEN*u/8.0)).distanceTo(b0.add(db.multiply(SwordFormation.S_LEN*v/8.0))));
        return d;
    }
    static double sq(double v){return v*v;}
    /** How deep a sword's middle at {@code x}, {@code y} on her screen waits: {@code u} of the way from SLOT_NEAR to as deep as keeps it SLOT_UP off the ground (SLOT_FAR at most). */
    static double slotDepth(Frame fr,double x,double y,double u){
        double most=SLOT_FAR;
        for(int s=0;s<=40;s++){double z=SLOT_NEAR+(SLOT_FAR-SLOT_NEAR)*s/40.0;if(onScreen(fr,x,y,z).y-fr.spot.y<SLOT_UP){most=Math.max(SLOT_NEAR,z-(SLOT_FAR-SLOT_NEAR)/40.0);break;}}
        return SLOT_NEAR+(most-SLOT_NEAR)*u;
    }
    /**
     * Whether a sword whose middle is at {@code mid}, pointing {@code d}, may wait there on her behind camera's screen: in
     * front of it, its blade whole in its frame and under the user's line, showing as MIN_LEN of the frame's height at
     * least, clear of her ({@code box}).
     */
    static boolean shows(Frame fr,double[] box,Vec3d mid,Vec3d d){
        Vec3d pommel=mid.subtract(d.multiply(SwordFormation.S_LEN/2));
        double[] a=onScreen(fr,pommel),g=onScreen(fr,pommel.add(d.multiply(SwordFormation.S_GUARD))),b=onScreen(fr,mid.add(d.multiply(SwordFormation.S_LEN/2)));
        if(a[2]<.5||b[2]<.5)return false;
        for(double[] q:new double[][]{g,b})if(Math.abs(q[0])>.98||Math.abs(q[1])>.96||q[1]>redLine(q[0]))return false;
        if(Math.hypot((b[0]-a[0])*16/9.0,b[1]-a[1])/2<MIN_LEN)return false;
        return !crosses(fr,box,pommel,d);
    }
    /** Her body on her behind camera's screen as it cuts behind her: its left, right, bottom and top. */
    static double[] herBox(Frame fr){
        Vec3d feet=body(fr,FOLD1);double[] a=onScreen(fr,feet),b=onScreen(fr,feet.add(0,1.9,0));
        double w=HER_HALF/(a[2]*Math.tan(Math.toRadians(FOV_BEHIND/2))*16/9.0);
        return new double[]{Math.min(a[0],b[0])-w,Math.max(a[0],b[0])+w,a[1],b[1]};
    }
    /** Whether a sword at {@code pommel} pointing {@code d} crosses her ({@code box}) on her behind camera's screen. */
    static boolean crosses(Frame fr,double[] box,Vec3d pommel,Vec3d d){
        for(int i=0;i<=16;i++){
            double[] q=onScreen(fr,pommel.add(d.multiply(SwordFormation.S_LEN*i/16.0)));
            if(q[2]>.05&&q[0]>box[0]-HER_CLEAR&&q[0]<box[1]+HER_CLEAR&&q[1]>box[2]-HER_CLEAR&&q[1]<box[3]+HER_CLEAR)return true;
        }
        return false;
    }
    /**
     * The guard's turn (SwordFormation.frame's spin, degrees) that shows the flat of a sword at {@code mid} pointing
     * {@code dir} to {@code eye} (2.101.9: "검날의 전체 길이와 옆면이 드러나는 구도로").
     */
    static double flatTo(Vec3d dir,Vec3d mid,Vec3d eye){
        Vec3d y=dir.normalize(),x0=y.crossProduct(UP);if(x0.lengthSquared()<1e-6)x0=new Vec3d(1,0,0);x0=x0.normalize();
        Vec3d x1=y.crossProduct(x0).normalize(),v=eye.subtract(mid);v=v.subtract(y.multiply(v.dotProduct(y)));
        if(v.lengthSquared()<1e-9)return 0;
        Vec3d x=y.crossProduct(v.normalize());
        return Math.toDegrees(Math.atan2(x.dotProduct(x1),x.dotProduct(x0)));
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
    record Blade(Vec3d mid,Vec3d dir,double spin,float alpha,boolean flying,double scale,double from){
        Blade(Vec3d mid,Vec3d dir,double spin,float alpha,boolean flying,double scale){this(mid,dir,spin,alpha,flying,scale,0);}
        /** The same, still behind her camera's screen as far as {@code from} along it (2.101.9: coming out through it). */
        Blade cut(double from){return new Blade(mid,dir,spin,alpha,flying,scale,from);}
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
    /** When it is drawn back to shoot (hers as she steps off it; never before the fan has folded: those fired at the cut not at all). */
    static float aimFrom(Plan p){return p.steed()?FINALE_DESCEND:Math.max(Math.max(FOLD1,p.appear()+EMERGE),p.launch()-AIM);}
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
        Vec3d[] c=folded(fr);double v=Math.tan(Math.toRadians(FOV_BEHIND/2)),h=v*16/9.0;
        return c[0].add(c[1].multiply(depth)).add(c[2].multiply(x*depth*h)).add(c[3].multiply(y*depth*v));
    }
    /** Where {@code q} is on that screen ({@link #onScreen}'s: -1 to 1 across and up it) and how deep in front of it. */
    static double[] onScreen(Frame fr,Vec3d q){
        Vec3d[] c=folded(fr);double v=Math.tan(Math.toRadians(FOV_BEHIND/2)),h=v*16/9.0;
        Vec3d d=q.subtract(c[0]);double z=d.dotProduct(c[1]);
        return new double[]{d.dotProduct(c[2])/z/h,d.dotProduct(c[3])/z/v,z};
    }
    /** Her camera behind her as the fan has folded (as it cuts behind her): its eye, the way it looks, its right and its up. */
    static Vec3d[] folded(Frame fr){
        Vec3d[] b=behind(fr,FOLD1);Vec3d eye=b[0],fw=b[1].subtract(eye).normalize();
        Vec3d right=fw.crossProduct(UP).normalize(),up=right.crossProduct(fw);
        // (rolled as her camera is: its frame turned clockwise for a roll below nought)
        double th=Math.toRadians(-rollAt(FOLD1));
        return new Vec3d[]{eye,fw,right.multiply(Math.cos(th)).add(up.multiply(Math.sin(th))),up.multiply(Math.cos(th)).subtract(right.multiply(Math.sin(th)))};
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
    /**
     * Where a sword of its places is {@code t} seconds in until it is fired: behind her camera's screen until it shows,
     * coming out through it into its place, waiting there breathing, drawn back a little to shoot.
     */
    static Vec3d waiting(Frame fr,Plan p,float t){
        if(t<p.appear())return outOf(fr,p,0);
        if(t<p.appear()+EMERGE)return outOf(fr,p,(t-p.appear())/EMERGE);
        return ready(fr,p,t).subtract(p.aimDir().multiply(DRAW*Ease.smooth(t,aimFrom(p),p.launch())));
    }
    /**
     * Its middle {@code u} of the way out through her camera's screen (0 behind it, its point at it, to 1 in its place):
     * along the line from the lens through its place, fast and slowing into it.
     */
    static Vec3d outOf(Frame fr,Plan p,double u){
        Vec3d[] c=folded(fr);Vec3d eye=c[0],to=p.readyMid().subtract(eye);
        double depth=to.dotProduct(c[1]),l0=(SCREEN-.02-p.aimDir().dotProduct(c[1])*SwordFormation.S_LEN/2)/depth;
        return eye.add(to.multiply(l0+(1-l0)*(1-(1-u)*(1-u))));
    }
    /** Whether it is one of her own that fills one of its places only later (gone into light as the fan folds). */
    static boolean refills(Plan p){return p.own()&&!p.steed()&&p.appear()>FOLD1+EMERGE_SPREAD+1e-4f;}
    /**
     * How far along sword {@code p} (its own length from its pommel, before it is scaled) it is still behind her camera's
     * screen {@code t} seconds in, at {@code pommel}, pointing {@code dir}, {@code scale} times its size (hers never).
     */
    static double cut(Frame fr,Plan p,Vec3d pommel,Vec3d dir,double scale,float t){
        if(p.steed()||t<FOLD0)return 0;
        Vec3d n=folded(fr)[1];double dn=dir.dotProduct(n);if(dn<.2)return 0;
        Vec3d screen=behind(fr,Math.max(t,FOLD1))[0].add(n.multiply(SCREEN));
        return MathHelper.clamp(screen.subtract(pommel).dotProduct(n)/dn/scale,0,SwordFormation.S_LEN);
    }
    /** Where it waits in its place {@code t} seconds in, breathing a little until it is drawn back to shoot. */
    static Vec3d ready(Frame fr,Plan p,float t){
        double s=p.seed(),k=1-Ease.smooth(t,aimFrom(p),p.launch());
        return p.readyMid().add(p.aimDir().multiply(BREATHE*k*Math.sin(t*3.3+s*.7))).add(0,BOB*k*Math.sin(t*2.6+s*1.3),0);
    }
    /**
     * How much of it shows {@code t} seconds in as it comes: one of light from as it shows in its place (over RELOAD_IN);
     * her own all of it, but one that fills a place again later going into light as the fan folds and back as it shows.
     */
    static float showing(Plan p,float t){
        if(!p.own())return Ease.smooth(t,p.appear(),p.appear()+.03f);
        if(!refills(p))return 1;
        return t<p.appear()?1-Ease.smooth(t,FOLD0,FOLD1):Ease.smooth(t,p.appear(),p.appear()+.03f);
    }
    /** How long it takes to go (her own a while, one of light in a moment as it bursts into light). */
    static float fade(Plan p){return p.own()?FADE:LIGHT_FADE;}
    /** Its middle {@code t} seconds in (null when it is not drawn), before the way it points is found from how it moves. */
    static Vec3d mid(Frame fr,Plan p,float t){
        if(t<p.start()||t>=p.vanish()+fade(p))return null;
        // stuck: driven a little further in than it stands, springing back
        if(t>=p.plant())return p.stuckMid().add(p.stuckDir().multiply(DRIVE*(1-Ease.out(t,p.plant(),p.plant()+SETTLE))));
        // (2.101.3) shot off past her body, faster and faster, dipping into the ground, never up (the video: long streaks)
        if(t>=p.launch())return flight(fr,p,along(p,t));
        if(p.steed())return steedMid(fr,p,t);
        // (2.101.9) one of light: out through her camera's screen into its place from as it shows, until it is fired
        if(!p.own())return waiting(fr,p,t);
        // her own that fill a place again later: into light where it is in the fan as the fan folds; nowhere until it shows
        if(refills(p)&&t>=FOLD0){
            if(t<FOLD1)return fanAt(p,t);
            if(t<p.appear())return null;
            return waiting(fr,p,t);
        }
        float fanned=p.gather()+GATHER;
        if(t>=FOLD0){
            // folded out of the fan to behind her camera's screen as it cuts behind her, then out through it into its place
            if(t>=FOLD1)return waiting(fr,p,t);
            float u=Ease.smooth(t,FOLD0,FOLD1);Vec3d in=outOf(fr,p,0);
            return u<=0?fanAt(p,t):bezier(fanAt(p,t),fanAt(p,t).subtract(p.fanDir().multiply(.5)),in.subtract(p.aimDir().multiply(.6)),in,u);
        }
        if(t>=fanned)return fanAt(p,t);
        if(t>=p.gather()){
            float u=Ease.smooth(t,p.gather(),fanned);
            // (on along its way after her, not straight off it; out into its place point first: it comes in along the
            // way it will point)
            Vec3d a=orbit(fr,p,p.gather()),v=orbitVel(fr,p,p.gather());
            return bezier(a,a.add(v.multiply(.06)),p.fanMid().subtract(p.fanDir().multiply(1.2)),fanAt(p,fanned),u);
        }
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
    /**
     * Its guard's turn {@code t} seconds in: spinning slowly as it flies after her and in the fan, turned as the fan folds
     * to show its flat to her camera behind her ({@link #flatTo}: 2.101.9) and so as it flies; one of light so from the
     * first; hers turned flat as it comes under her feet.
     */
    static double spinAt(Plan p,float t){
        if(p.steed())return p.spin()*(1-steedIn(p,t));
        if(!p.own())return p.face();
        double s=p.spin()+SPIN*Math.max(0,Math.min(t,FOLD0)-p.start()-RISE);
        if(refills(p))return t<p.appear()?s:p.face();
        return t<=FOLD0?s:s+MathHelper.wrapDegrees(p.face()-s)*Ease.smooth(t,FOLD0,FOLD1);
    }
    /** Sword {@code p} {@code t} seconds in (null when it is not drawn). */
    static Blade blade(Frame fr,Plan p,float t){
        Vec3d m=mid(fr,p,t);if(m==null)return null;
        float alpha=showing(p,t)*(1-Ease.smooth(t,p.vanish(),p.vanish()+fade(p)));
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
        // one of light, waiting in its place: pointing the way it shoots; her own filling a place again, a feather going
        // into light as the fan folds, then the same
        if(!p.own()&&t<p.launch())return new Blade(m,p.aimDir(),spin,alpha,false,1).cut(cut(fr,p,m.subtract(p.aimDir().multiply(SwordFormation.S_LEN/2)),p.aimDir(),1,t));
        if(refills(p)&&t>=FOLD0&&t<p.launch()){
            Vec3d d=t<FOLD1?p.fanDir():p.aimDir();
            return new Blade(m,d,spin,alpha,false,1).cut(t<FOLD1?0:cut(fr,p,m.subtract(d.multiply(SwordFormation.S_LEN/2)),d,1,t));
        }
        // pulled out: pointing down as it stood, until it turns up as it rises
        if(p.own()&&t<p.start()+RISE*.5f)return new Blade(m,p.down(),spin,alpha,false,1);
        // in the fan: pointing out, as the wings' feathers; as the fan folds, turned to point the way it shoots, waiting
        float fanned=p.gather()+GATHER;
        if(t>=fanned&&t<p.launch()){var w=new Blade(m,slerp(p.fanDir(),p.aimDir(),Ease.smooth(t,FOLD0,FOLD1)),spin,alpha,false,sc);return w.cut(cut(fr,p,w.pommel(),w.dir(),sc,t));}
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
            if(!Float.isNaN(end)&&t>=end&&p.plant()>end){var b=blade(fr,p,Math.min(t,end));if(b!=null&&t<end+.2f)out[j]=new Blade(b.mid(),b.dir(),b.spin(),b.alpha()*(1-Ease.smooth(t,end,end+.2f)),false,b.scale(),b.from());continue;}
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
            var p=plans[i];float flare=flare(p,b,t,false),a=b.alpha()*(p.own()?1:.88f)*clearOf(c,p,b,t);if(a<=.005f)continue;
            SwordFormation.sword(m,c.camera,b.pommel(),SwordFormation.frame(b.dir(),b.spin()),b.scale(),a,0,flare,0,b.from(),b.from()>0?FADE_IN:0);
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
            float flare=flare(p,b,t,true),near=clearOf(c,p,b,t);
            // (its outline lit less than the formation's under its night: against the dusk the fan was a mass of white)
            SwordFormation.swordGlow(m,b.pommel(),SwordFormation.frame(b.dir(),b.spin()),b.scale(),b.alpha()*near,0,GLOW_SKY,flare,0,b.from(),b.from()>0?FADE_IN:0);
            if(near>.01f&&(b.flying()||t>=p.launch()&&t<p.plant()+STREAK_FLY))streak(m,fr,p,t,new Blade(b.mid(),b.dir(),b.spin(),b.alpha()*near,true,b.scale()));
            // (2.101.9: "속도감이 느껴지게") fired: fading copies of it a moment behind it along its flight
            if(!p.steed()&&t>=p.launch()&&t<p.plant()&&near>.01f)for(int g=1;g<=GHOSTS;g++){
                float at=t-g*GHOST_STEP;if(at<p.launch())break;
                double u=along(p,at),gs=scaleAt(u);Vec3d gd=flightDir(fr,p,u),gm=flight(fr,p,u);
                SwordFormation.swordGlow(m,gm.subtract(gd.multiply(SwordFormation.S_LEN*gs/2)),SwordFormation.frame(gd,b.spin()),gs,b.alpha()*near*GHOST_ALPHA/g,0,GLOW_SKY,.25f,0);
            }
            if(!p.steed()&&t>=p.appear()&&t<p.appear()+.2f){
                // (2.101.9) breaking out through her camera's screen: a little light and a ring where its point comes through
                float k=FxLib.kick(t,p.appear()+.01f,.08f);
                if(k>.01f){
                    Vec3d o=outOf(fr,p,0).add(p.aimDir().multiply(SwordFormation.S_LEN/2));float u=Ease.unit(t,p.appear(),p.appear()+.12f);
                    m.softDisc(o,.07,BLUE,.35f*k);glint8(m,o,.06*k,PALE,.7f*k);
                    if(u<1)FxLib.ring(m,o,.02+.12*Ease.out(t,p.appear(),p.appear()+.1f),.008,PALE,.6f*(1-u));
                }
            }
            if(refills(p)&&t<FOLD1){
                // going into light in the fan as it folds
                float k=FxLib.kick(t,FOLD0+.07f,.1f);
                if(k>.01f){Vec3d o=toward(b.mid(),c.camera,.2);m.softDisc(o,.9,BLUE,.3f*k);glint8(m,o,.3*k,PALE,.6f*k);}
            }
            if(p.own()){
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
        // going: lit along its blade a moment, a scatter of motes off it (one of light a moment after it strikes: it
        // bursts into light)
        for(int j=0;j<plans.length;j++){
            var p=plans[j];if(t<p.vanish()-.02f||t>=p.vanish()+.6f||!comes(c.cast,p.plant()))continue;
            Vec3d[] ends=stuck(p);Vec3d pommel=ends[0],point=ends[1];
            float g=Ease.window(t,p.vanish()-.02f,p.vanish()+fade(p),.04f,.1f);
            if(!p.own()){float k=FxLib.kick(t,p.vanish(),.12f);if(k>.01f){Vec3d o=toward(pommel.lerp(point,.45),c.camera,.3);m.softDisc(o,1.4,BLUE,.3f*k);glint8(m,o,.5*k,PALE,.55f*k);}}
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
    /**
     * The same for sword {@code p} drawn {@code t} seconds in to {@code c}'s camera: all of it to her own cutscene's camera
     * once it is behind her (2.101.7), where each waits laid out by it with only its blade in its frame, its hilt by the
     * lens and out of the frame, and shoots off away from it.
     */
    static float clearOf(Fx c,Plan p,Blade b,float t){
        if(!p.steed()&&t>=FINALE_LAUNCH){var own=cinematic();if(own!=null&&own.id().equals(c.cast.id()))return 1;}
        return clearOf(b,c.camera);
    }
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
    /** How many fading copies follow a sword fired into the band, how far behind each the next is (seconds along its flight), and how strongly the first shows. */
    static final int GHOSTS=3;
    static final float GHOST_STEP=.012f,GHOST_ALPHA=.45f;
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
        // (2.101.9) each shot lighting round where it was fired, a moment: the rapid fire flickering
        for(var p:plans){if(p.steed()||!comes(c.cast,p.plant()))continue;float k=FxLib.kick(t,p.launch(),.06f);if(k>.05f)f.light(tipFrom(fr,p),0xb4dcff,5,.55f*k);}
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
        Vec3d[] b=behind(fr,t);
        return new Shot(b[0],b[1],FOV_BEHIND);
    }
    /**
     * Her camera behind her {@code t} seconds in (its eye and what it looks at): at BEHIND looking at BEHIND_LOOK, coming
     * on a little (BEHIND_ON forward and BEHIND_UP up over the shot), and tilted down after her as she comes down (what it
     * looks at going down BEHIND_TILT times as far as she does: turned, not moved, so that the swords still waiting by it
     * stay as they were in its frame, their hilts out of it and their points in it, and she in it as she lands).
     */
    static Vec3d[] behind(Frame fr,float t){
        float u=Ease.unit(t,FINALE_LAUNCH,FINALE_BACK1);
        double down=BEHIND_TILT*(chest(fr,FINALE_DESCEND).y-chest(fr,Math.max(t,FINALE_DESCEND)).y);
        Vec3d on=fr.f.multiply(BEHIND_ON*u).add(0,BEHIND_UP*u,0);
        return new Vec3d[]{fr.at(BEHIND[0],BEHIND[1],BEHIND[2]).add(on),fr.at(BEHIND_LOOK[0],BEHIND_LOOK[1]-down,BEHIND_LOOK[2]).add(on)};
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
     * was low and near, the fan over the frame. 2.101.7, user request: "아니 플레이어랑 카메라가 너무 멀리있잖아": in toward
     * her along the way from her to 2.101.3's, 0.58 as far, turned the same way: she where she was in its frame, as big
     * again and two thirds, as in the video. 2.101.9, user request: "카메라는 검의 진행 방향을 정면에서 바라보지 말고, 진행
     * 방향의 측후방에서 비스듬히 바라보는 3/4 측면 시점으로 설정해", "카메라를 검의 진행 방향과 평행하게 두거나 완전히 수직으로
     * 두지 말 것", "그리고 카메라를 살짝 비스듬이 배치해": turned 17 degrees more to her left and moved round to her right to
     * keep her where she was in its frame, so that the swords waiting at its left slant across it on away from it, some
     * 30 to 65 degrees off the way it looks; rolled {@link #BEHIND_ROLL} degrees, the frame turned 7 degrees clockwise:
     * the user's choice "7° 기울임" of a still beside the level one.) How far it comes on forward and up over
     * the shot, and how many times as far as she comes down what it looks at goes down after her.
     */
    static final double[] BEHIND={-3.7,4.64,3.9},BEHIND_LOOK={11.15,2.44,-6.2};
    static final float BEHIND_ROLL=-7;
    /** A roll for her behind camera tried in a film before it goes in (SkillShots "hroll"), else null. */
    static Float tunedRoll;
    static final double BEHIND_ON=.46,BEHIND_UP=.17,BEHIND_TILT=1.6;
    static final double FOV_LAP=60,FOV_HOVER=54,FOV_CLOSE=50,FOV_BEHIND=74;
    static final float CLOSE0=1.5f;
    /** Her camera in its cutscene: the shot, short of a block between her and it; going back to the game's camera as it ends. */
    static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){
        var c=cinematic();if(c==null)return null;
        float t=c.age(delta)/20f;Shot s=shot(c,t);Frame fr=frame(c);
        Vec3d eye=clear(chest(fr,Math.min(t,FINALE_LAND)),s.eye());Vec3d d=s.look().subtract(eye);if(d.lengthSquared()<1e-6)d=fr.f;
        d=d.normalize();
        float sy=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),sp=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));
        float back=Ease.smooth(t,FINALE_BACK0,FINALE_BACK1),roll=rollAt(t);
        if(back<=0)return new CinematicDirector.Pose(eye,sy,sp,roll);
        return new CinematicDirector.Pose(eye.lerp(vanilla,back),sy+MathHelper.wrapDegrees(yaw-sy)*back,sp+(pitch-sp)*back,roll);
    }
    /** How far her camera is rolled {@code t} seconds in (degrees: behind her BEHIND_ROLL, none again as it comes back to her). */
    static float rollAt(float t){return t<FINALE_LAUNCH?0:(tunedRoll!=null?tunedRoll:BEHIND_ROLL)*(1-Ease.smooth(t,FINALE_BACK0,FINALE_BACK1));}
    /** The same for the local player's own H again's cutscene now (IsosangCinema.roll: the view and the billboards alike). */
    static float roll(float delta){var c=cinematic();return c==null?0:rollAt(c.age(delta)/20f);}
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
