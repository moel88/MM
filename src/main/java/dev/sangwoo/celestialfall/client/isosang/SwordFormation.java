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
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * Lee So-sang's H as drawn (2.82.0, user request: "다음은 H스킬이야 엄청 많은 검을 박고 검 위로 올라가 검 위에 올라가 있을때는
 * 움직 일 수 없어 영상이랑 똑같이 구현해줘 이펙트도", and the user's video of it, read frame by frame on a sheet the user
 * answered: some 39 s, the formation strikes all along, only R up there, 60 s cooldown; then "데미지는 처음 한 번 H 박을때
 * 30데미지 그리고 5초마다 5 데미지"). She crouches with her sword low at her right and leaps some three blocks up where
 * she stands, her sword pointing straight down: a thread of lightning runs from its point to the ground, ink darkens the
 * ground all round the formation ahead of her and gathers into its middle, its shrinking edge lit blue (2.82.5), while
 * swords show high in the sky, points down. She spreads her arms to call them; the first is struck into the ground right
 * under her where the lightning ran; out of the middle, where the ink has gathered, a black spike shoots up and bursts
 * into blue light, and she dives down onto the pommel of the sword under her as streams of black water swirl out of the
 * middle, a whirlpool past the formation's edge, thin streaks of pale blue swirling round its black eye (2.82.7, many and
 * thin since 2.82.12), and the rest rain down round its empty middle, each trailing blue lightning (it then spreads out
 * and is gone, lying along the formation's rim as a dark navy smoke and mist a moment, 2.82.8-2.82.12),
 * each struck into the ground with a flower of blue crystal and a splash of ink at its foot. Ink mountains rise round
 * the formation (none behind her, where her camera looks from), white clouds curling over them and wisps of mist
 * drifting low (2.82.1); the swords, dark as they strike, turn silver with a white glow, a gleam running down one now
 * and then; as it stands, a deep navy water lies on its ground with black grain going round in it and strands of pale
 * water, rings open from the swords' feet now and then, dark smoke and black flecks come off their blades, and each
 * stands upright, turning slowly about its length (2.82.13); she stands on that pommel
 * (IsosangClient.drive lifts her, IsosangClient.lift draws the leap and the dive) until the formation ends: every sword
 * flares sky blue over a four-pointed star of light, turns to ink and breaks away, the mountains, the clouds and the blue
 * of the ground going with them, and she drops down.
 * <p>The formation does not lie round her (2.82.4, user request: "가운데에서 점프해서 올라가는게 아니고 H를 쓰면 내가
 * 점프를 하고 그 자리에 검이 하나 있어야해 그러니까 H는 플레이어 기준 중앙으로 나오는게 아니고 외곽부분으로 나와야함"):
 * she stands on its outer ring and its middle lies IsosangRules.FORMATION_STAND_BACK blocks ahead of her, as the server
 * laid it (IsosangClient.formation: its middle, the way she faced; spot: where she stood), each sword where the swing's id
 * seeds it (every client the same). Its swords are thinner than 2.82.1's (THIN, 2.82.4: "검 두께를 지금보다 더 작아지게").
 * Every effect is a function of the swing's clock, so it moves at the render frame rate.
 */
final class SwordFormation {
    private SwordFormation(){}

    // ───────────────────────────── the clock ─────────────────────────────

    static final float LENGTH=IsosangRules.LENGTH[IsosangRules.FORMATION]/20f;
    /** How long a formation that ended early is still drawn after its end (its swords going, the ink they leave drying, IsosangClient.lifeOf). */
    static final float AFTER=2.5f;
    /** The thread of lightning from her sword's point to the ground as she leaps (seconds into H). */
    static final float THREAD0=.22f,THREAD1=.6f;
    /**
     * The ink on the formation's ground, all round its middle (2.82.5, user request: "처음에 잉크가 중앙에 모였다가 점점
     * 확장하고 검이 박히게 만들어야지", and the user's video at a tenth of the speed read again frame by frame; 2.82.6, user
     * request: "잉크 모였다가 퍼지는게 너무 부자연스러운데 이거만 예쁘게 다듬어줘": a liquid sheet drawn by the ink shader,
     * KagenoFire.inkGround, style 13): it soaks into the ground all round the middle in patches as she leaps (POOL0 to
     * POOL1, POOL_WIDE blocks out, she well inside it) and lies there; then it gathers into the middle, its edge easing
     * into motion, sweeping in (GATHER0 to GATHER_FAST, in to some GATHER_MID blocks), lingering as it closes slowly
     * (GATHER_SLOW to GATHER1, toward POOL_IN), and drawn in all the way at the last (COLLAPSE0 to COLLAPSE, as the flash
     * breaks there); once the spike has burst there, a whirlpool: streams of black water swirl out of the middle (SWIRL0
     * to WHIRL1, out to WHIRL_R; its black eye opening to EYE_R from EYE0 to EYE1; the blue of the ground deepening to
     * SWIRL1). (2.82.4 opened a small pool under her and spread it, sliding it out to the formation's middle as it swirled
     * out; 2.82.5 drew a flat disc of black shrinking and fading; 2.82.6 spread the sheet out again to the formation's
     * edge, three thin arms of light turning in it the wrong way round, a lit rim round it like a puddle's.)
     */
    static final float POOL0=.19f,POOL1=.29f,GATHER0=.32f,GATHER_FAST=.5f,GATHER_SLOW=.42f,GATHER1=.64f,COLLAPSE0=.56f,COLLAPSE=IsosangRules.H_PLANT-.055f,SWIRL0=.7f,SWIRL1=1.15f;
    /**
     * How much smaller than 2.82.16 all of it is (2.82.17, user request: "H 범위 8블록으로 줄여", "전체 범위를
     * 줄여달라는거였는대", "전체 all", "산도 같은 비율로 줄임", "12블록으로 늘리는 대신에"): the formation 12 blocks wide, was
     * 14.5, and every length of it out to the mountains, their clouds and the faint mountains behind them with it, but for
     * how high the mountains float (MOUNTAIN_FLOAT) and the empty middle, smaller still (IsosangRules.FORMATION_EMPTY).
     */
    static final double SHRINK=IsosangRules.FORMATION_RADIUS/14.5;
    /** (POOL_WIDE: out past the mountains' feet, as in 2.82.16.) */
    static final double POOL_WIDE=SwordFormation.MOUNTAIN_R+1.1*SHRINK,GATHER_MID=3.4,POOL_IN=2.4;
    /**
     * The whirlpool (2.82.7, user request: "잉크가 모였다가 사라지고 검정색 물줄기가 회오리 처럼 흐르는거임", "이거처럼 이펙트가
     * 크게 나옴" with their still of the video; the video read again a frame at a time and put back on the ground plane):
     * once the gathered ink has gone into the middle and burst there, streams of black water swirl out of it, crescents of
     * light on them, out as wide as the ink first soaked in (WHIRL_R, past the formation's edge and under her camera) in
     * some sixth of a second (SWIRL0 to WHIRL1, slow to start, fastest midway), its middle a black eye no stream crosses
     * (EYE_R, the formation's empty middle: the swords strike round its edge) opening a little later (EYE0 to EYE1). Seen
     * from above its streams curl counterclockwise as they run out, some ten degrees off the round, and all of it turns
     * slowly the same way (the ink shader, style 15: a stream keeps theta + WHIRL_TWIST ln r, it turns WHIRL_SPIN radians
     * a second; theta there grows clockwise seen from above). Its light is a see-through pale blue, no white (2.82.8, user
     * request: "회오리 하늘색 부분 더 더 연하게"): thin streaks, many of them, along its streams, gathering into two broad
     * arms and three thinner ones over a faint blue haze, its water a dark navy (2.82.12, user request: "소용돌이를
     * 포현하는 하늘색 부분이 너무 두껍다고 해야하나 결이 더 많아야 할듯", "그냥 영상 따라서 만드는게 너는 힘든가?";
     * 2.82.7-2.82.11 drew thick crescents). As the swords strike it spreads out (2.82.10, below) and turns into the dark
     * navy smoke at the rim. (EYE_R 5 until 2.82.16; 4 since 2.82.17, the empty middle smaller: the swords striking
     * outside it.)
     */
    static final float WHIRL1=.86f,EYE0=.73f,EYE1=.85f;
    static final double WHIRL_R=POOL_WIDE,EYE_R=4,WHIRL_TWIST=5.2,WHIRL_SPIN=.38;
    /**
     * The whirlpool spreads out and turns into smoke (2.82.10, user request: "퍼진다는 느낌이 안 나잖아 그리고 연기
     * 파란색이여함 연기를 더 진하게 만들고 더 많이 피어오르게 하고 소용돌이가 퍼져서 연기가 되는거처럼 만들어"; 2.82.9 only
     * grew it three tenths as it faded, too little and too quick to read as spreading): a clean front opens in its middle
     * and runs out to the rim (SPREAD0 to SPREAD1, easing in, fast through the middle and slowing as it gets there, front), the black water
     * and its streams squeezed out ahead of it (its edge pushed SPREAD_PUSH further out), the ground clean behind it (the
     * formation's blue ground and the ink at the swords' feet go as it passes them, groundInk). As it runs it turns blue,
     * blue mist lifting off it (frontMist), and what reaches the rim turns into the smoke there (WATER_OUT0 to WATER_OUT1). The ink
     * shader draws the front from the formation's clock as front does.
     */
    static final float SPREAD0=1.0f,SPREAD1=1.33f,WATER_OUT0=1.27f,WATER_OUT1=1.45f;
    static final double SPREAD_PUSH=.06;
    /**
     * The smoke at the rim (2.82.8, user request: "끝으로 퍼지면서 이상한 아우라가 테두리 부분으로 퍼지면서 사라지거든", the
     * video's pale blue smoke round the formation's rim, read on a marked still, then "일단 그렇게 해봐"; 2.82.10: blue,
     * thicker, rising more, the whirlpool spreading out into it): as the spreading whirlpool reaches the formation's rim
     * (RIM_R, outside every sword, just before the mountains' feet) it turns into a band of blue smoke on the ground there
     * (SMOKE_IN to SMOKE_FULL), its wisps streaming round the way the whirlpool turned, a see-through blue mist standing
     * round it (veilLife, shaders: 2.82.11, the user's marked screenshot); it drifts a little further out (to RIM_OUT) and thins away by patches,
     * its thin wisps first (SMOKE_OUT0 to SMOKE_OUT1). Both a dark navy, see-through, giving no light (2.82.12, user
     * request: "더 진하고 어두운 남색", "연기는 눈에 잘 안 띄이게 만들어야함 살짝 투명하고"). The ink shader draws the band
     * in the whirlpool's square (style 15), reading these off the formation's clock.
     */
    static final float SMOKE_IN=1.22f,SMOKE_FULL=1.36f,SMOKE_OUT0=1.64f,SMOKE_OUT1=1.88f;
    static final double RIM_R=SwordFormation.MOUNTAIN_R-.8*SwordFormation.SHRINK,RIM_OUT=SwordFormation.MOUNTAIN_R-.2*SwordFormation.SHRINK;
    /** The mountains rise; the clouds and the mist start coming in (each a little after this, over a second: Cloud.in). */
    static final float RISE0=1.0f,RISE1=1.45f,MIST0=1.2f;
    /** The swords, dark as they strike, turn silver; the crystal flowers at their feet wilt. */
    static final float SILVER0=1.42f,SILVER1=1.8f,WILT0=1.45f,WILT1=1.78f;
    /** As it ends (seconds after its end): the swords flare sky blue (to GLOW), turn to ink (to INKED), and are gone (GONE). */
    static final float GLOW=.08f,INKED=.18f,GONE=.42f;
    /** How long a sword takes to fall from the sky to the ground. */
    static final float FALL=.22f;

    /** When this formation ends (seconds into it): its whole length run, or the server's word that it ended early. */
    static float end(StandClient.Cast c){float e=IsosangClient.formationEnd(c.id());return Float.isNaN(e)?IsosangRules.H_END:Math.min(IsosangRules.H_END,e);}
    /** How far it has gone as it ends: 0 until then; through the flare, the ink and the breaking away (0 to 1). */
    static float going(StandClient.Cast c,float t){return Ease.smooth(t,end(c)+INKED,end(c)+GONE);}
    /**
     * Whether H again took this formation (2.101.0, IsosangClient.takenBy): it ends as H again begins, its swords not
     * flaring and going to ink but pulled out of the ground one by one (SwordFinale draws each from then), the rest of it
     * (its mountains, clouds, water, night) going as at any end.
     */
    static boolean taken(StandClient.Cast c){return IsosangClient.takenBy(c.id())!=null;}
    /** Whether sword {@code b} of this formation has been pulled out by H again by {@code t} seconds into the formation (2.101.0). */
    static boolean pulled(StandClient.Cast c,Blade b,float t){
        var fin=IsosangClient.takenBy(c.id());if(fin==null)return false;
        int k=(b.seed()-IsosangRules.formationSeed(c.id()))/97-1;
        return t-(fin.start()-c.start())/20f>=SwordFinale.pullAt(fin,k);
    }

    // ───────────────────────────── the body ─────────────────────────────
    // (the video: she crouches, her sword low at her right; springs up, her sword turned straight down below her right hand,
    // her free arm out to the left; hangs there; spreads both arms to call the swords down; dives back down (2.82.4: onto
    // the pommel of the sword struck under her, no more to the ground and then up onto a sword) and lands on it, bowing a
    // little, her feet together; stands with her arms spread as they rain; stands there, her sword held as she holds it,
    // shifting a little now and then; steps off as it ends, drops and lands in a crouch, and rises. The body faces the way
    // she faced, as every hit's: toward the formation's middle.)
    static final R6.Pose H_DIP=w(.04,-.12,-4,6,0,d(-55,40),d(0,60),d(-70,-25),22,-158);
    static final R6.Pose H_CROUCH=w(.06,-.32,-10,15,0,d(-32,72),d(-18,122),d(-36,-48),30,-150);
    static final R6.Pose H_SPRING=w(.04,-.04,-4,2,0,d(-58,40),d(-82,18),d(4,-96),d(-60,12),d(-97,-6));
    static final R6.Pose H_AIR=w(.02,.04,-6,-6,0,d(-42,28),d(-89,8),d(16,-100),d(-52,14),d(-102,-8));
    static final R6.Pose H_AIR2=w(.02,.05,-4,-4,0,d(-36,32),d(-86,14),d(22,-102),d(-58,12),d(-98,-10));
    static final R6.Pose H_CALL=w(.03,.05,0,-8,0,d(8,96),d(-24,112),d(12,-98),d(-62,10),d(-100,-10));
    static final R6.Pose H_CALL2=w(.03,.04,0,-6,0,d(12,94),d(-18,108),d(16,-96),d(-66,10),d(-98,-10));
    static final R6.Pose H_DIVE=w(.04,0,-2,4,0,d(-18,64),d(-56,82),d(0,-82),d(-80,6),d(-88,-6));
    /** Down on the pommel (2.82.4): bowing a little into it, her feet together on it, her arms out for balance. */
    static final R6.Pose H_LAND_ON=w(.03,0,-2,8,0,d(-22,64),d(-8,98),d(-12,-76),d(-85,7),d(-87,-7));
    static final R6.Pose H_LAND_ON2=w(.02,0,-1,5,0,d(-8,76),d(4,102),d(-2,-82),d(-87,5),d(-88,-5));
    /** Her arms spread as the swords rain, up on the pommel, her feet together. */
    static final R6.Pose H_CONDUCT_ON=w(.02,0,0,1,0,d(2,86),d(14,100),d(6,-88),d(-88,5),d(-89,-5));
    static final R6.Pose H_CONDUCT_ON2=w(.02,0,0,-1,0,d(6,84),d(18,98),d(10,-86),d(-88,5),d(-89,-5));
    /** Up there: her feet together, her sword as she holds it. */
    static final R6.Pose H_ON=w(0,0,0,3,0,d(-62,14),d(18,6),d(-74,-16),d(-90,3),d(-90,-3));
    /** Standing there, shifting a little now and then. */
    static final R6.Pose H_IDLE=w(0,0,3,1,0,d(-68,6),d(26,4),d(-84,-10),d(-90,3),d(-90,-3));
    /** Stepping off as it ends, dropping, landing in a crouch. */
    static final R6.Pose H_STEP=w(.02,0,0,-2,0,d(-50,30),d(10,20),d(-40,-50),d(-74,8),d(-96,-6));
    static final R6.Pose H_FALL=w(.02,0,0,2,0,d(-30,44),d(-6,40),d(-20,-70),d(-84,6),d(-88,-6));
    static final R6.Pose H_TOUCH=w(.05,-.26,0,12,0,d(-38,54),d(-12,82),d(-36,-56),24,-156);
    static final R6.Pose H_TOUCH2=w(.05,-.22,0,10,0,d(-44,40),d(-4,60),d(-48,-46),24,-156);
    /** How often she shifts as she stands there (seconds), and when she has settled into standing there after landing. */
    static final double SHIFT=3.2,SETTLED=1.65;
    static final Anim CLIP=clip();
    private static Anim clip(){
        List<Anim.Key> keys=new ArrayList<>(List.of(
            k(0,STANCE),
            k(.06,H_DIP),
            k(.14,H_CROUCH,Anim.Ease.OUT),
            k(IsosangRules.H_LEAP,H_SPRING,Anim.Ease.OUT),
            k(.32,H_AIR),
            k(.56,H_AIR2),
            k(.68,H_CALL,Anim.Ease.OUT),
            k(IsosangRules.H_DIVE,H_CALL2),
            k(.84,H_DIVE,Anim.Ease.IN),
            k(IsosangRules.H_LANDED+.01,H_LAND_ON,Anim.Ease.OUT),
            k(.97,H_LAND_ON2),
            k(1.06,H_CONDUCT_ON),
            k(1.16,H_CONDUCT_ON2),
            k(1.4,H_ON),
            k(SETTLED,STANCE)));
        boolean shifted=true;
        for(double t=SETTLED+SHIFT;t<=IsosangRules.H_END-1;t+=SHIFT){keys.add(k(t,shifted?H_IDLE:STANCE));shifted=!shifted;}
        keys.add(k(IsosangRules.H_END,STANCE));
        keys.add(k(IsosangRules.H_DROP,H_STEP));
        keys.add(k(IsosangRules.H_DROP+.25,H_FALL));
        keys.add(k(IsosangRules.H_DOWN,H_TOUCH,Anim.Ease.OUT));
        keys.add(k(IsosangRules.H_DOWN+.1,H_TOUCH2));
        keys.add(k(LENGTH,STANCE));
        return Anim.joints(keys.toArray(new Anim.Key[0])).after(IsosangSlash::grounded);
    }

    // ───────────────────────────── where it lies ─────────────────────────────

    /** The formation's middle on its ground (the server's word), else as far ahead of where she stood as it lies (2.82.4). */
    static Vec3d centre(StandClient.Cast c){
        var f=IsosangClient.formation(c.id());
        return f!=null?f.centre():c.origin().add(Vec3d.fromPolar(0,c.yaw()).multiply(IsosangRules.FORMATION_STAND_BACK));
    }
    /** Where she stands, on the ground (2.82.4: on the formation's outer ring, where she stood as it began): under the sword she stands on. */
    static Vec3d spot(StandClient.Cast c){var f=IsosangClient.formation(c.id());return f==null?c.origin():IsosangClient.spot(f);}
    /** The way she faced as it began. */
    static float yaw(StandClient.Cast c){var f=IsosangClient.formation(c.id());return f==null?c.yaw():f.yaw();}
    /** Her body as drawn: where she is, lifted as the formation's plan has her (IsosangClient.lift). */
    static Vec3d body(Fx c){return c.pos.add(0,IsosangClient.lift(c.cast.caster(),c.delta),0).add(IsosangClient.shift(c.cast.caster(),c.delta));}
    /** Her sword's hand and blade at {@code t} as drawn (on her lifted body). */
    static Vec3d[] blade(Fx c,float t){return KagenoClips.blade(body(c),c.cast.yaw(),R6.poseOf(CLIP.frame(t)));}

    /**
     * A sword of the formation: where it strikes the ground (forward and right of the middle, blocks), how high its pommel
     * stands over it, how far it leans from upright and which way (degrees), its guard's turn, when it strikes the ground,
     * how high over its place it hangs in the sky, when it shows there, how big the flower at its foot is, and its seed.
     */
    record Blade(double fw,double rt,double height,double tilt,double toward,double spin,float plant,double sky,float shows,double flower,int seed){
        /** The one she stands on (it strikes first, right under her). */
        boolean hers(){return plant==IsosangRules.H_PLANT;}
    }
    private static final Map<UUID,Blade[]> LAYOUTS=new LinkedHashMap<>();
    /**
     * The swords of the formation of swing {@code id} (the same on every client: seeded by the id). Where each strikes is
     * the rules' (IsosangRules.formationSwords: spread evenly over all of it but its empty middle, 2.82.16; as the
     * server reckons the sword she stands on); the one she stands on (IsosangRules.standSword, 2.82.4: "H를 쓰면 내가 점프를 하고 그 자리에
     * 검이 하나 있어야해") strikes first, right under her as she hangs in the air, and stands upright, its pommel as high as
     * she stands (its crystal flower as the others', 2.82.5: the spike is out of the middle again); the rest stand
     * upright as it does (2.82.13), a little higher or lower, and rain down after it.
     */
    static Blade[] layout(UUID id){
        var l=LAYOUTS.get(id);if(l!=null)return l;
        int s0=IsosangRules.formationSeed(id);
        var places=IsosangRules.formationSwords(s0);int stand=IsosangRules.standSword();
        List<Blade> out=new ArrayList<>();
        double r0=IsosangRules.FORMATION_EMPTY,r1=IsosangRules.FORMATION_RADIUS-IsosangRules.FORMATION_EDGE;
        for(int k=0;k<places.length;k++){
            int seed=IsosangRules.swordSeed(s0,k);
            // how far out it lies (0 the empty middle's edge, 1 the outermost): the inner ones fall a little first
            double out01=Math.max(0,Math.min(1,(Math.hypot(places[k][0],places[k][1])-r0)/(r1-r0)));
            if(k==stand){
                out.add(new Blade(places[k][0],places[k][1],IsosangRules.FORMATION_STAND,0,0,180*Ease.hash(seed+7),IsosangRules.H_PLANT,10,IsosangRules.H_SKY,.8+.45*Ease.hash(seed+11),seed));
                continue;
            }
            // (2.82.13, user request: "검들이 다 삐 뚫어져있냐 영상은 전부 제대로 박혀있는데": every sword struck straight
            // down, upright; 2.82.1-2.82.12 leaned them 2 to 13 degrees)
            double height=1.25+.5*Ease.hash(seed+4),tilt=0,toward=0,spin=180*Ease.hash(seed+7);
            float plant=IsosangRules.H_RAIN0+(IsosangRules.H_RAIN1-IsosangRules.H_RAIN0)*(float)Math.min(1,.24*out01+.82*Ease.hash(seed+8));
            double sky=7+5*Ease.hash(seed+9);
            // (it shows a little before it falls: at least a tenth of a second of hanging in the sky)
            float shows=IsosangRules.H_SKY+(float)Math.min(plant-FALL-IsosangRules.H_SKY-.1f,.24*Ease.hash(seed+10));
            out.add(new Blade(places[k][0],places[k][1],height,tilt,toward,spin,plant,sky,shows,.8+.45*Ease.hash(seed+11),seed));
        }
        l=out.toArray(new Blade[0]);
        LAYOUTS.put(id,l);while(LAYOUTS.size()>16)LAYOUTS.remove(LAYOUTS.keySet().iterator().next());
        return l;
    }
    /** Where a sword strikes the ground (world). */
    static Vec3d foot(Vec3d centre,float yaw,Blade b){return world(centre,yaw,v(b.fw(),0,b.rt()));}
    /** The way a sword points, from its pommel to its point: down, leaning its tilt toward its way. */
    static Vec3d down(Blade b){
        double t=Math.toRadians(b.tilt()),a=Math.toRadians(b.toward());
        return new Vec3d(Math.sin(t)*Math.cos(a),-Math.cos(t),Math.sin(t)*Math.sin(a)).normalize();
    }
    /** A sword's pommel once it has struck: its height up along it from where it strikes the ground. */
    static Vec3d planted(Vec3d centre,float yaw,Blade b){
        Vec3d d=down(b);return foot(centre,yaw,b).subtract(d.multiply(b.height()/Math.max(.5,-d.y)));
    }
    /** How far over where it strikes a sword is at {@code t} (blocks, 0 once struck): hanging in the sky, then falling, faster and faster. */
    static double above(Blade b,float t){
        float from=b.plant()-FALL;if(t>=b.plant())return 0;
        if(t<from)return b.sky()+.06*Math.sin(t*5+b.seed());
        float u=Ease.unit(t,from,b.plant());return b.sky()*(1-u*u)+.06*Math.sin(from*5+b.seed())*(1-u);
    }
    /**
     * How far a sword's guard has turned at {@code t} (degrees): the swords turn slowly about their own length as the
     * formation stands (2.82.13, user request: "검들이 천천히 회전하네 자연스럽게", the user's video read and confirmed: some
     * six seconds a turn, each at its own time): each from TURN_FROM after it strikes, easing in over TURN_IN, at its own
     * pace (TURN_RATE degrees a second, give or take a fifth) and its own way round; the one she stands on stays still.
     */
    static double spinAt(Blade b,float t){
        if(b.hers())return b.spin();
        double rate=TURN_RATE*(.8+.4*Ease.hash(b.seed()+95))*(Ease.hash(b.seed()+96)<.5?-1:1);
        return b.spin()+rate*eased(t-b.plant()-TURN_FROM,TURN_IN);
    }
    /** How far a turn easing in over {@code in} seconds has gone {@code s} seconds into it (in seconds at full speed): none before, smooth, full speed after. */
    static double eased(double s,double in){
        if(s<=0)return 0;if(s>=in)return s-in*.5;
        double x=s/in;return in*(x*x*x-.5*x*x*x*x);
    }
    static final double TURN_RATE=60;
    static final float TURN_FROM=.5f,TURN_IN=1.2f;
    /** How strongly a sword shows at {@code t} (0 to 1): coming in where it hangs, and going as the formation ends. */
    static float shown(StandClient.Cast c,Blade b,float t){
        // (2.101.0) taken by H again: there until it is pulled out (the finale draws it from then), never inked
        if(taken(c))return pulled(c,b,t)?0:Ease.smooth(t,b.shows(),b.shows()+.08f);
        return Ease.smooth(t,b.shows(),b.shows()+.08f)*(1-Ease.smooth(t,end(c)+INKED+.02f,end(c)+GONE));
    }

    // ───────────────────────────── a sword ─────────────────────────────

    /** The formation's sword (the video: a plain silver longsword), along it from its pommel's top (y 0) to its point, blocks. */
    static final double S_LEN=2.2,S_POMMEL=.09,S_GRIP=.4,S_GUARD=.47,S_W=.072,S_W2=.058,S_T=.022,S_POINT=.34,S_GUARD_W=.27,S_GUARD_T=.042,S_GRIP_R=.03;
    /**
     * Its finer parts (2.82.1, the video's sword looked at again: a groove down its blade, its guard's ends rounded, its
     * grip wound): the fuller's half width and how far down the blade it runs, the guard's knobs' size, and the guard's sweep
     * toward the point at its ends.
     */
    static final double S_FULLER=.017,S_FULLER_END=.7,S_KNOB=.034,S_SWEEP=.035;
    enum Part{BLADE,FULLER,GUARD,KNOB,GEM,GRIP,RING,POMMEL}
    record Face(Vec3d[] v,Vec3d n,Part part){}
    record Edge(Vec3d a,Vec3d b,boolean outline){}
    static final List<Face> FACES=new ArrayList<>();
    static final List<Edge> EDGES=new ArrayList<>();
    /** The middles of its guard's knobs and of its gems (the points their faces turn out from), and the way each gem faces. */
    static final List<Vec3d> KNOBS=new ArrayList<>(),GEMS=new ArrayList<>(),GEM_WAYS=new ArrayList<>();
    static{build();thin();}
    /**
     * How much thinner the formation's swords are than the shape built (2.82.4, user request: "검 두께를 지금보다 더
     * 작아지게"): across and through (their blade's width and thickness, their guard, grip, pommel, knobs and gems), not
     * along; their light's lines are as much finer.
     */
    static final double THIN=.65;
    /** A point of the sword as built, made thinner (across and through, not along). */
    static Vec3d thinned(Vec3d p){return new Vec3d(p.x*THIN,p.y,p.z*THIN);}
    /** Makes the sword as built thinner: its faces (their normals as the shape's are turned), its edges, its knobs and gems. */
    private static void thin(){
        for(int i=0;i<FACES.size();i++){
            var f=FACES.get(i);Vec3d[] v=new Vec3d[f.v().length];for(int k=0;k<v.length;k++)v[k]=thinned(f.v()[k]);
            FACES.set(i,new Face(v,new Vec3d(f.n().x/THIN,f.n().y,f.n().z/THIN).normalize(),f.part()));
        }
        for(int i=0;i<EDGES.size();i++){var e=EDGES.get(i);EDGES.set(i,new Edge(thinned(e.a()),thinned(e.b()),e.outline()));}
        KNOBS.replaceAll(SwordFormation::thinned);GEMS.replaceAll(SwordFormation::thinned);
    }
    private static Vec3d p(double x,double y,double z){return new Vec3d(x,y,z);}
    /** A face, its normal turned to agree with {@code out} (a rough outward way). */
    private static void face(Part part,Vec3d out,Vec3d... v){
        Vec3d n=v[1].subtract(v[0]).crossProduct(v[2].subtract(v[0]));
        if(n.lengthSquared()<1e-14&&v.length>3)n=v[2].subtract(v[0]).crossProduct(v[3].subtract(v[0]));
        n=n.normalize();if(n.dotProduct(out)<0)n=n.negate();
        FACES.add(new Face(v,n,part));
    }
    /** The blade's half width {@code y} along the sword (from its guard to where its point begins), and how far its ridge stands out there. */
    static double bladeWidth(double y){double u=MathHelper.clamp((y-S_GUARD)/(S_LEN-S_POINT-S_GUARD),0,1);return S_W+(S_W2-S_W)*u;}
    static double bladeRidge(double y){double u=MathHelper.clamp((y-S_GUARD)/(S_LEN-S_POINT-S_GUARD),0,1);return S_T*(1-.15*u);}
    /** A point of the blade's edge (its right one, x > 0) {@code y} along the sword, on to its point (as drawn: thinned). */
    static Vec3d edge(double y){
        double y1=S_LEN-S_POINT;
        if(y<=y1)return p(bladeWidth(y)*THIN,y,0);
        return p(S_W2*THIN*(1-MathHelper.clamp((y-y1)/S_POINT,0,1)),Math.min(y,S_LEN),0);
    }
    private static void build(){
        // the blade: a diamond across (its edges at x = ±w, its ridge at z = ±t), narrowing a little to where its point begins
        double y0=S_GUARD,y1=S_LEN-S_POINT,t1=bladeRidge(y1);
        Vec3d e0=p(S_W,y0,0),r0=p(0,y0,S_T),f0=p(-S_W,y0,0),q0=p(0,y0,-S_T);
        Vec3d e1=p(S_W2,y1,0),r1=p(0,y1,t1),f1=p(-S_W2,y1,0),q1=p(0,y1,-t1),tip=p(0,S_LEN,0);
        face(Part.BLADE,p(1,0,1),e0,e1,r1,r0);face(Part.BLADE,p(-1,0,1),r0,r1,f1,f0);
        face(Part.BLADE,p(-1,0,-1),f0,f1,q1,q0);face(Part.BLADE,p(1,0,-1),q0,q1,e1,e0);
        face(Part.BLADE,p(1,.4,1),e1,tip,r1);face(Part.BLADE,p(-1,.4,1),r1,tip,f1);
        face(Part.BLADE,p(-1,.4,-1),f1,tip,q1);face(Part.BLADE,p(1,.4,-1),q1,tip,e1);
        EDGES.add(new Edge(e0,e1,true));EDGES.add(new Edge(e1,tip,true));EDGES.add(new Edge(f0,f1,true));EDGES.add(new Edge(f1,tip,true));
        EDGES.add(new Edge(r0,r1,false));EDGES.add(new Edge(r1,tip,false));EDGES.add(new Edge(q0,q1,false));EDGES.add(new Edge(q1,tip,false));
        // the fuller: a groove down the middle of each broad side from just under the guard, a hair over the blade's faces
        double fy0=S_GUARD+.04,fy1=S_GUARD+S_FULLER_END*(y1-S_GUARD),lift=.0015;
        for(int side=-1;side<=1;side+=2)for(int half=-1;half<=1;half+=2){
            double fw=S_FULLER*half;
            Vec3d a=p(0,fy0,side*(bladeRidge(fy0)+lift)),b=p(fw,fy0,side*(bladeRidge(fy0)*(1-Math.abs(fw)/bladeWidth(fy0))+lift));
            Vec3d c=p(fw,fy1,side*(bladeRidge(fy1)*(1-Math.abs(fw)/bladeWidth(fy1))+lift)),d=p(0,fy1,side*(bladeRidge(fy1)+lift));
            face(Part.FULLER,p(half*.3,0,side),a,b,c,d);
        }
        // the guard: a straight bar across, its ends a little swept toward the point
        double g0=S_GRIP,g1=S_GUARD,gw=S_GUARD_W,gt=S_GUARD_T,sweep=S_SWEEP;
        Vec3d a=p(-gw,g0+sweep,-gt),b=p(gw,g0+sweep,-gt),c=p(gw,g1+sweep,-gt),dd=p(-gw,g1+sweep,-gt);
        Vec3d e=p(-gw,g0+sweep,gt),f=p(gw,g0+sweep,gt),g=p(gw,g1+sweep,gt),h=p(-gw,g1+sweep,gt);
        Vec3d ma=p(0,g0,-gt),mb=p(0,g1,-gt),me=p(0,g0,gt),mf=p(0,g1,gt);
        // (each half its own faces: bent at the middle)
        face(Part.GUARD,p(0,0,-1),a,ma,mb,dd);face(Part.GUARD,p(0,0,-1),ma,b,c,mb);
        face(Part.GUARD,p(0,0,1),e,h,mf,me);face(Part.GUARD,p(0,0,1),me,mf,g,f);
        face(Part.GUARD,p(0,-1,0),a,e,me,ma);face(Part.GUARD,p(0,-1,0),ma,me,f,b);
        face(Part.GUARD,p(0,1,0),dd,mb,mf,h);face(Part.GUARD,p(0,1,0),mb,c,g,mf);
        face(Part.GUARD,p(-1,0,0),a,dd,h,e);face(Part.GUARD,p(1,0,0),b,f,g,c);
        EDGES.add(new Edge(p(-gw,g0+sweep,0),p(0,g0,0),true));EDGES.add(new Edge(p(0,g0,0),p(gw,g0+sweep,0),true));
        EDGES.add(new Edge(p(-gw,g1+sweep,0),p(0,g1,0),true));EDGES.add(new Edge(p(0,g1,0),p(gw,g1+sweep,0),true));
        // the knobs rounding its ends: small eight-faced studs
        for(int side=-1;side<=1;side+=2){
            Vec3d k=p(side*(gw+S_KNOB*.55),(g0+g1)/2+sweep,0);KNOBS.add(k);
            Vec3d[] ax={p(S_KNOB*1.1,0,0),p(0,S_KNOB,0),p(0,0,S_KNOB*.9)};
            for(int sx=-1;sx<=1;sx+=2)for(int sy=-1;sy<=1;sy+=2)for(int sz=-1;sz<=1;sz+=2){
                Vec3d px=k.add(ax[0].multiply(sx)),py=k.add(ax[1].multiply(sy)),pz=k.add(ax[2].multiply(sz));
                face(Part.KNOB,p(sx,sy,sz),px,py,pz);
            }
            EDGES.add(new Edge(k.add(ax[1]),k.add(ax[0].multiply(side)),true));EDGES.add(new Edge(k.add(ax[0].multiply(side)),k.subtract(ax[1]),true));
        }
        // the gems: a low four-sided stone on each face of the guard's middle and of the pommel
        for(int side=-1;side<=1;side+=2){
            gem(p(0,(g0+g1)/2,side*(gt+.002)),.026,.034,.009,side);
            gem(p(0,.047,side*.0575),.019,.025,.007,side);
        }
        // the grip and the pommel: turned, eight sides; the grip wound with three raised bands
        double r=S_GRIP_R,rr=r*1.25;
        double[][] lathe={{0,.034},{.028,.058},{.066,.052},{S_POMMEL,r},
            {.15,r},{.156,rr},{.168,rr},{.174,r},{.236,r},{.242,rr},{.254,rr},{.26,r},{.322,r},{.328,rr},{.34,rr},{.346,r},{S_GRIP,r}};
        Part[] parts={Part.POMMEL,Part.POMMEL,Part.POMMEL,Part.GRIP,Part.RING,Part.RING,Part.RING,Part.GRIP,Part.RING,Part.RING,Part.RING,Part.GRIP,Part.RING,Part.RING,Part.RING,Part.GRIP};
        int sides=8;
        for(int i=0;i+1<lathe.length;i++){
            for(int sd=0;sd<sides;sd++){
                double u0=Math.PI*2*sd/sides,u1=Math.PI*2*(sd+1)/sides,um=(u0+u1)*.5;
                Vec3d p0=p(Math.cos(u0)*lathe[i][1],lathe[i][0],Math.sin(u0)*lathe[i][1]),p1=p(Math.cos(u1)*lathe[i][1],lathe[i][0],Math.sin(u1)*lathe[i][1]);
                Vec3d p2=p(Math.cos(u1)*lathe[i+1][1],lathe[i+1][0],Math.sin(u1)*lathe[i+1][1]),p3=p(Math.cos(u0)*lathe[i+1][1],lathe[i+1][0],Math.sin(u0)*lathe[i+1][1]);
                face(parts[i],p(Math.cos(um),0,Math.sin(um)),p0,p1,p2,p3);
            }
        }
        for(int sd=0;sd<sides;sd++){
            double u0=Math.PI*2*sd/sides,u1=Math.PI*2*(sd+1)/sides;
            face(Part.POMMEL,p(0,-1,0),p(0,0,0),p(Math.cos(u0)*lathe[0][1],0,Math.sin(u0)*lathe[0][1]),p(Math.cos(u1)*lathe[0][1],0,Math.sin(u1)*lathe[0][1]));
        }
        EDGES.add(new Edge(p(0,0,0),p(0,S_GRIP,0),false));
    }
    /** A gem: a low four-sided stone {@code w} across and {@code h} along the sword, standing {@code raise} out from {@code base} on side {@code side} (z). */
    private static void gem(Vec3d base,double w,double h,double raise,int side){
        GEMS.add(base);GEM_WAYS.add(p(0,0,side));
        Vec3d top=base.add(0,0,side*raise),l=base.add(-w,0,0),rt=base.add(w,0,0),up=base.add(0,h,0),dn=base.add(0,-h,0);
        face(Part.GEM,p(-1,1,side*2),top,l,up);face(Part.GEM,p(1,1,side*2),top,up,rt);face(Part.GEM,p(1,-1,side*2),top,rt,dn);face(Part.GEM,p(-1,-1,side*2),top,dn,l);
    }
    /** A sword's frame in the world, pointing along {@code dir} from its pommel, its guard turned {@code spin} degrees: across, along, through. */
    static Vec3d[] frame(Vec3d dir,double spin){
        Vec3d y=dir.normalize(),x0=y.crossProduct(UP);
        if(x0.lengthSquared()<1e-6)x0=new Vec3d(1,0,0);
        x0=x0.normalize();Vec3d x1=y.crossProduct(x0).normalize();double s=Math.toRadians(spin);
        Vec3d x=x0.multiply(Math.cos(s)).add(x1.multiply(Math.sin(s))).normalize(),z=x.crossProduct(y).normalize();
        return new Vec3d[]{x,y,z};
    }
    static Vec3d at(Vec3d pommel,Vec3d[] f,Vec3d p){return pommel.add(f[0].multiply(p.x)).add(f[1].multiply(p.y)).add(f[2].multiply(p.z));}
    private static int colour(Part part){return switch(part){case BLADE->0xd2dbee;case FULLER->0x7d89a6;case GUARD->0xaab5cd;case KNOB->0xc6cfe4;case GEM->0x6aa6ff;case GRIP->0x343c52;case RING->0xb7c1d8;case POMMEL->0xbcc6dc;};}
    /** The light the swords are lit by: from above, a little from the side. */
    static final Vec3d LIGHT=new Vec3d(.35,1,.25).normalize();
    /**
     * A sword's body (the blended pass): every face toward the camera, lit from above and brighter at its rim, darkened
     * toward ink ({@code dark}: as it strikes), flared sky blue ({@code flare}) and inked ({@code ink}) as the formation ends.
     */
    static void sword(EffectMesh m,Vec3d camera,Vec3d pommel,Vec3d[] f,float alpha,float dark,float flare,float ink){
        if(alpha<=.005f)return;
        for(var face:FACES){
            Vec3d n=f[0].multiply(face.n().x).add(f[1].multiply(face.n().y)).add(f[2].multiply(face.n().z));
            Vec3d[] w=new Vec3d[face.v().length];Vec3d c=Vec3d.ZERO;
            for(int i=0;i<w.length;i++){w[i]=at(pommel,f,face.v()[i]);c=c.add(w[i]);}
            c=c.multiply(1.0/w.length);
            Vec3d view=camera.subtract(c);double facing=n.dotProduct(view);if(facing<=0)continue;
            double lit=.6+.4*Math.max(0,n.dotProduct(LIGHT)),rim=1-Math.min(1,facing/Math.max(1e-6,view.length()));
            int col=SpiritSword.scale(colour(face.part()),lit);
            col=SpiritSword.mix(col,0xffffff,.28*rim*rim);
            col=SpiritSword.mix(col,0x070b1c,.9*dark);
            col=SpiritSword.mix(col,0x86ecff,.85*flare);
            col=SpiritSword.mix(col,INK,.95*ink);
            float a=alpha*(face.part()==Part.BLADE?.95f:.97f);
            if(w.length==3)m.triangle(w[0],w[1],w[2],col,a);else m.quad(w[0],w[1],w[2],w[3],col,a);
        }
    }
    /**
     * A sword's light (the light pass): white along its outline with a pale glow round it (the video: the swords stood in
     * the formation glow white at their edges), blue at its edges while it is dark, brighter in the sky, and flaring sky
     * blue as the formation ends; none once it is ink.
     */
    static void swordGlow(EffectMesh m,Vec3d pommel,Vec3d[] f,float alpha,float dark,float sky,float flare,float ink){
        float a=alpha*(1-ink);if(a<=.005f)return;
        int edge=dark>.5f?AZURE:WHITE;float strong=.42f+.18f*sky+.5f*flare;
        for(var e:EDGES){
            Vec3d p=at(pommel,f,e.a()),q=at(pommel,f,e.b());
            if(e.outline()){m.line(p,q,(.014+.01*flare)*THIN,edge,Math.min(1,strong)*a);m.softLine(p,q,(.1+.25*flare)*THIN,dark>.5f?BLUE:PALE,(.13f+.04f*sky+.3f*flare)*a);}
            else m.line(p,q,.009*THIN,PALE,(.2f+.08f*sky)*a*(1-dark));
        }
        Vec3d base=at(pommel,f,p(0,S_GUARD,0)),tip=at(pommel,f,p(0,S_LEN,0)),top=at(pommel,f,p(0,0,0));
        m.softLine(top,tip,.36*THIN,dark>.5f?BLUE:AZURE,(.04f+.03f*sky)*a);
        // its gems glint blue on the side turned to the camera (2.82.1)
        if(dark<.5f)for(int i=0;i<GEMS.size();i++){
            Vec3d g=at(pommel,f,GEMS.get(i)),way=f[0].multiply(GEM_WAYS.get(i).x).add(f[1].multiply(GEM_WAYS.get(i).y)).add(f[2].multiply(GEM_WAYS.get(i).z));
            if(way.dotProduct(m.camera().subtract(g))>0)m.softDisc(g.add(way.multiply(.012*THIN)),.055*THIN,AZURE,(.32f+.2f*sky)*a);
        }
        if(flare>.01f){m.softLine(top,tip,.9*THIN,CYAN,.5f*flare*a);m.line(base,tip,.05*THIN,WHITE,.85f*flare*a);m.softDisc(top,.45*THIN,CYAN,.5f*flare*a);}
    }

    // ───────────────────────────── the ink mountains, the clouds, the mist ─────────────────────────────

    /**
     * An ink mountain round the formation: which way from its middle (radians), how far, how long after the first it
     * rises, its seed; which of the user's paintings it is (ART: 0 the mountain, 1 the peak, 2 the small mountain),
     * how tall (blocks), how strongly it shows (0 to 1) and whether it is drawn mirrored.
     */
    record Mountain(double az,double radius,float delay,int seed,int art,double top,float alpha,boolean flip){
        /** Half its width (blocks along the circle at its radius): as wide as its painting is for its height. */
        double half(){return top*ASPECT[art]/2;}
    }
    private static final Map<UUID,Mountain[]> MOUNTAINS=new LinkedHashMap<>();
    /**
     * How far round from right behind her (seen from the formation's middle, radians) the mist at the rim stands low
     * (2.82.4: she stands on the formation's outer ring and her camera looks at her from behind, from out over the
     * mountains' ring; until 2.82.15 no mountain stood there either, nor a cloud near it).
     */
    static final double MOUNTAIN_GAP=Math.toRadians(50);
    /** The way from the formation's middle to where she stands (radians in the world's x, z), as it faced {@code yaw}. */
    static double behind(float yaw){Vec3d b=Vec3d.fromPolar(0,yaw).negate();return Math.atan2(b.z,b.x);}
    /**
     * The mountains of the formation of swing {@code id}, its user having faced {@code yaw} (2.82.16, user request: "이제 산
     * 갈아엎고 사진이랑 똑같이 만들어봐 인게임에서는 저거랑 똑같은 산 6개가 한 바퀴로 둘러져있음", with their still of the
     * game; then their painting of it: "이거 쓰면 되는데 투명하게 다듬으면 될듯"): six of the same mountain (the user's
     * painting, MOUNTAIN_TEX) all the way round, MOUNTAIN_R blocks out, a sixth of the round apart, each bent round the
     * circle looking at its middle, a little gap between neighbours (as the still's); one of the gaps right behind her
     * (her camera looks in at her from out there, through it).
     */
    static Mountain[] mountains(UUID id,float yaw){
        var l=MOUNTAINS.get(id);if(l!=null)return l;
        int s0=(int)(id.getLeastSignificantBits()*31+id.getMostSignificantBits());
        l=new Mountain[MOUNTAIN_COUNT];double off=behind(yaw)+Math.PI/MOUNTAIN_COUNT;
        for(int i=0;i<MOUNTAIN_COUNT;i++){
            int seed=s0+211*(i+1);
            l[i]=new Mountain(off+Math.PI*2*i/MOUNTAIN_COUNT,MOUNTAIN_R,.14f*(float)Ease.hash(seed+90),seed,0,MOUNTAIN_TOP,MOUNTAIN_ALPHA,false);
        }
        MOUNTAINS.put(id,l);while(MOUNTAINS.size()>16)MOUNTAINS.remove(MOUNTAINS.keySet().iterator().next());
        return l;
    }
    static final int MOUNTAIN_COUNT=6;
    /** How strongly the six show (2.82.16: "큰산 작은산들 ... 더 투명하게": a little see-through, rims and all). */
    static final float MOUNTAIN_ALPHA=.82f;
    /** (2.82.16: where they stood with the formation 12 blocks; they stay there as the swords spread out to their feet.) */
    static final double MOUNTAIN_R=15.4*SHRINK;
    private static final Map<UUID,Mountain[]> FILL=new LinkedHashMap<>();
    /**
     * The user's two other paintings among the six (2.82.16, user request, with the two: "빈 공간에 이거 넣고 그래도 남는
     * 공간에는 이 작은 산들 촘촘하게 끼워넣는데 엄청 투명하게 끼워넣어줘 자세히 봐야 알 정도로"): in each gap between two of
     * them their peak (PEAK_*), standing just behind them a little left of its middle, a little see-through; and all
     * round behind them the small mountain, SMALL_COUNT
     * of it close together (SMALL_*, every other one mirrored), barely there: they show only where the sky is left open
     * above the others. None in the gap right behind her nor, for the small ones, within SMALL_CLEAR of it (her camera
     * looks in from out there: a mountain there would stand before it).
     */
    static Mountain[] fill(UUID id,float yaw){
        var l=FILL.get(id);if(l!=null)return l;
        int s0=(int)(id.getLeastSignificantBits()*37+id.getMostSignificantBits()*3);
        List<Mountain> out=new ArrayList<>();double back=behind(yaw);
        for(int i=1;i<MOUNTAIN_COUNT;i++){
            int seed=s0+307*(i+1);
            out.add(new Mountain(back+Math.PI*2*i/MOUNTAIN_COUNT-PEAK_LEFT/(MOUNTAIN_R+PEAK_BACK),MOUNTAIN_R+PEAK_BACK,.06f+.12f*(float)Ease.hash(seed+2),seed,1,
                PEAK_TOP*(.95+.1*Ease.hash(seed+3)),PEAK_ALPHA,false));
        }
        double span=Math.PI*2-2*SMALL_CLEAR;
        for(int i=0;i<SMALL_COUNT;i++){
            int seed=s0+919*(i+1);
            double az=back+SMALL_CLEAR+span*(i+.5+.5*(Ease.hash(seed+1)-.5))/SMALL_COUNT;
            out.add(new Mountain(az,MOUNTAIN_R+SMALL_BACK0+(SMALL_BACK1-SMALL_BACK0)*((i&1)*.6+.4*Ease.hash(seed+2)),.16f+.2f*(float)Ease.hash(seed+3),seed,2,
                SMALL_TOP0+(SMALL_TOP1-SMALL_TOP0)*Ease.hash(seed+4),SMALL_ALPHA*(float)(.85+.3*Ease.hash(seed+5)),(i&1)==1));
        }
        l=out.toArray(new Mountain[0]);
        FILL.put(id,l);while(FILL.size()>16)FILL.remove(FILL.keySet().iterator().next());
        return l;
    }
    /**
     * The peak in each gap (2.82.16, user request, with a smaller mountain of theirs in place of the tall peak: "너무
     * 이상하다 이정도 크기로 바꾸고 얘도 조금 투명하게 바꿔줘", "너무 정가운데 두지 말고 왼쪽에 더 가깝게 두자", "방금 보낸 사진 말고
     * 이걸로"): how far behind the six it stands, how far left of the gap's middle (as seen from the formation's middle:
     * the way the paintings' left lies), how tall it is (blocks; about as tall as the part of the tall peak they cut out
     * to show its size) and how strongly it shows.
     */
    static final double PEAK_BACK=.4*SHRINK,PEAK_LEFT=1.5*SHRINK,PEAK_TOP=2.0*SHRINK;
    /** (PEAK_ALPHA .58, was .7, and the six at MOUNTAIN_ALPHA: "큰산 작은산들 투명도만 조금 낮추자 거의 희미해서 자세히 봐야하는 산들 제외", then "아니아니 더 투명하게".) */
    static final float PEAK_ALPHA=.58f;
    /** The small mountains: how many, how far behind the six (from, to), how tall (from, to), how strongly they show, how far round from right behind her none stands. */
    static final int SMALL_COUNT=20;
    static final double SMALL_BACK0=1.0*SHRINK,SMALL_BACK1=3.0*SHRINK,SMALL_TOP0=1.6*SHRINK,SMALL_TOP1=2.4*SHRINK,SMALL_CLEAR=Math.toRadians(70);
    static final float SMALL_ALPHA=.15f;
    /** All the mountains of a formation as drawn: the six, the peaks in their gaps, the small ones behind. */
    static List<Mountain> peaks(UUID id,float yaw){
        List<Mountain> l=new ArrayList<>(List.of(mountains(id,yaw)));l.addAll(List.of(fill(id,yaw)));return l;
    }
    /**
     * The mountain: the user's painting (2.82.16: tools/isosang/make_mountain_texture.py crops it, makes its ink body a
     * little see-through and darker, its rims of light kept, and frays its foot away), MOUNTAIN_TOP blocks tall (2.82.16:
     * "산 크기가 너무 커 지금 더 줄이고"; then "산 크기를 조금 더 키우고" made it 4.4, and "산들 크기 다시 줄이자" 3.9 again), as
     * wide as the painting is for that (MOUNTAIN_ASPECT, the texture's width over its
     * height), drawn as a strip of MOUNTAIN_COLS quads bent round the circle.
     */
    static final net.minecraft.util.Identifier MOUNTAIN_TEX=new net.minecraft.util.Identifier("celestialfall","textures/effect/isosang_mountain.png");
    static final double MOUNTAIN_TOP=3.9*SHRINK,MOUNTAIN_ASPECT=1540/518.0,MOUNTAIN_HALF=MOUNTAIN_TOP*MOUNTAIN_ASPECT/2;
    /**
     * The user's paintings: the mountain, the peak and the small mountain (the last two painted on black: the black
     * taken away), all made into textures by make_mountain_texture.py;
     * each one's width over its height (its texture's).
     */
    static final net.minecraft.util.Identifier[] ART={MOUNTAIN_TEX,art("isosang_mountain_peak"),art("isosang_mountain_small")};
    static final double[] ASPECT={MOUNTAIN_ASPECT,804/500.0,964/363.0};
    static net.minecraft.util.Identifier art(String name){return new net.minecraft.util.Identifier("celestialfall","textures/effect/"+name+".png");}
    static final int MOUNTAIN_COLS=48;
    /** How far a mountain is up at {@code t} (0 to 1): rising into place (each a little after the last), sinking away as the formation ends. */
    static float mountainUp(float end,Mountain mt,float t){
        float d=mt.delay();
        return Ease.smooth(t,RISE0+d,RISE1+d+.15f)*(1-Ease.smooth(t,end+GLOW,end+GONE+.2f));
    }
    /** How far a mountain rises into place from (blocks under it). */
    static final double MOUNTAIN_RISE=1.2*SHRINK;
    /**
     * A point of a mountain (fixed in the world, looking at the formation's middle): {@code x} along it round the circle
     * (blocks at its radius), {@code y} up from its floating foot, {@code front} before it (2.82.16, user request: "지금은
     * 원이 아니잖아 둘러진게": bent round the circle, so the six go round it, not as a hexagon of flat faces).
     */
    static Vec3d onMountain(Vec3d centre,Mountain mt,double x,double y,double front){
        double a=mt.az()+x/mt.radius(),r=mt.radius()-front;
        return centre.add(Math.cos(a)*r,MOUNTAIN_FLOAT+y,Math.sin(a)*r);
    }
    /**
     * How high over the ground the mountains float (blocks; 2.82.16, user request: "영상에는 산이 중간에 떠있는데 너가 만든건
     * 왜 바닥에서 부터 산이 있냐?": in the video their feet fray away above the ground, the grass showing through and under
     * them; then "조금 더 낮추고"; then .7, as the user had it, the stream under them .6 high: "지금 0.7칸 떠있다고 했나? 그럼
     * 물줄기 높이는 0.6칸으로"), their feet fraying away in the painting's lowest eighth. 2.5 since 2.82.17 (user request: "H
     * 범위 8블록으로 줄여 그대신 지금 산 간격은 똑같해야 하니까 산의 X값은 그대로 두고 Y값만 올려": the formation 8 blocks
     * wide, the mountains where they were round it, MOUNTAIN_R, only higher; how much higher mine to choose: their feet
     * about level with her eyes as she stands on her sword); the stream under them and the clouds over them go up with them.
     */
    static final double MOUNTAIN_FLOAT=2.5;
    /**
     * How strongly a mountain shows at {@code x} along it (0 to 1), her camera {@code camAz} round the formation from its
     * middle (NaN when her camera is inside the mountains' ring): where it stands between her camera and the formation,
     * just round from where her camera looks in, it thins to let her be seen (OCCLUDED of it), as a camera does in a game.
     */
    static float occlusion(Mountain mt,double x,double camAz){
        if(Double.isNaN(camAz))return 1;
        double off=Math.abs(Math.IEEEremainder(mt.az()+x/mt.radius()-camAz,Math.PI*2));
        return OCCLUDED+(1-OCCLUDED)*Ease.smooth((float)off,(float)OCCLUDE0,(float)OCCLUDE1);
    }
    static final float OCCLUDED=.2f;
    static final double OCCLUDE0=Math.toRadians(4),OCCLUDE1=Math.toRadians(16);
    /** Which way round the formation her camera is from its middle (radians), or NaN when it is inside the mountains' ring (where they never stand before it). */
    static double cameraAz(Vec3d centre,Vec3d camera){
        double dx=camera.x-centre.x,dz=camera.z-centre.z;
        return Math.hypot(dx,dz)<MOUNTAIN_R-.35?Double.NaN:Math.atan2(dz,dx);
    }
    /**
     * The mountains (2.82.16), in the ground pass in draw calls of their own (KagenoFire.inkInterlude): first the haze
     * behind and over them (drawn after them it veiled them), then the mountains (peaks), the farthest from the camera
     * first, each one of the user's paintings on a strip bent round the circle (from its left as seen from the
     * formation's middle, mirrored if it is), rising into place, thinning where it stands before her camera (occlusion).
     */
    static void mountainInk(Fx c){
        float t=c.sec;Vec3d centre=centre(c.cast);double camAz=cameraAz(centre,c.camera);
        var mat=KagenoFire.inkMatrix();
        var buf=net.minecraft.client.render.Tessellator.getInstance().getBuffer();
        cloudRing(c,centre,t,camAz);
        List<Mountain> order=new ArrayList<>();
        float end=end(c.cast);for(var mt:peaks(c.cast.id(),yaw(c.cast)))if(mountainUp(end,mt,t)>.01f)order.add(mt);
        if(order.isEmpty())return;
        order.sort(Comparator.comparingDouble((Mountain mt)->-onMountain(centre,mt,0,mt.top()/2,0).squaredDistanceTo(c.camera)));
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionTexColorProgram);
        for(var mt:order){
            // each in a draw call of its own, the farthest first, its painting bound for it
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,ART[mt.art()]);
            buf.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS,net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR);
            float up=mountainUp(end,mt,t);double lift=-(1-up)*MOUNTAIN_RISE,half=mt.half(),top=mt.top();
            int cols=mt.art()==0?MOUNTAIN_COLS:Math.max(4,(int)Math.ceil(MOUNTAIN_COLS*half/MOUNTAIN_HALF));
            for(int i=0;i<cols;i++){
                float u0=i/(float)cols,u1=(i+1)/(float)cols;
                double x0=(u0-.5)*2*half,x1=(u1-.5)*2*half;
                float a0=mt.alpha()*up*occlusion(mt,x0,camAz),a1=mt.alpha()*up*occlusion(mt,x1,camAz);
                float t0=mt.flip()?1-u0:u0,t1=mt.flip()?1-u1:u1;
                texVertex(buf,mat,onMountain(centre,mt,x0,lift,0),t0,1,a0);
                texVertex(buf,mat,onMountain(centre,mt,x1,lift,0),t1,1,a1);
                texVertex(buf,mat,onMountain(centre,mt,x1,lift+top,0),t1,0,a1);
                texVertex(buf,mat,onMountain(centre,mt,x0,lift+top,0),t0,0,a0);
            }
            net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(buf.end());
        }
    }
    /**
     * The faint clouds over the mountains (2.82.16, user request with their screenshot of the game: "사진에 있는 희미한
     * 구름 보이지? 마지막으로 구름만 저 퀄리티로 만들어줘 저 구름들은 한바퀴로 이어져 있고 천천히 한 바퀴 돌고 있어"): one ring
     * of soft, see-through clouds of mist CLOUD_R out, from CLOUD_LOW blocks up for CLOUD_HIGH (among the mountains and
     * over their summits), joined all the way round, thinner here and there but never gone ("한바퀴 안 둘러져있고"), and
     * turning slowly (the ink shader's mode 17, its clouds,
     * KagenoFire.clouds). Drawn before the mountains (behind them). It comes in after the mountains rise and goes with
     * them; where it runs between her camera and the formation, out near the camera, it thins away (CLOUD_OPEN, within
     * 20 to 45 degrees of her camera's way round).
     * (2.82.16's first draft: fourteen banks of round puffs, HAZE.)
     */
    static void cloudRing(Fx c,Vec3d centre,float t,double camAz){
        float end=end(c.cast);
        float up=Ease.smooth(t,MIST0+.1f,MIST0+1.3f)*(1-Ease.smooth(t,end+GLOW,end+GONE+.3f));
        if(up<=.005f)return;
        int n=CLOUD_SEGS;double[] rr=new double[n+1],hh=new double[n+1];float[] aa=new float[n+1];
        for(int i=0;i<=n;i++){
            double az=Math.PI*2*i/n;rr[i]=CLOUD_R;hh[i]=CLOUD_HIGH;
            float before=Double.isNaN(camAz)?1:Ease.smooth((float)Math.abs(Math.IEEEremainder(az-camAz,Math.PI*2)),(float)CLOUD_OPEN0,(float)CLOUD_OPEN1);
            Vec3d p=centre.add(Math.cos(az)*CLOUD_R,CLOUD_LOW+CLOUD_HIGH/2,Math.sin(az)*CLOUD_R);
            aa[i]=(float)(up*before*camera(p,c.camera));
        }
        KagenoFire.clouds(KagenoFire.inkMatrix(),c.camera,centre.add(0,CLOUD_LOW,0),rr,hh,aa,c.cast.id().hashCode()+71,c.age);
    }
    /** The clouds: how far out (just behind the mountains), from how high up and how tall (blocks), how finely drawn round. */
    static final double CLOUD_R=MOUNTAIN_R+1.0*SHRINK,CLOUD_LOW=MOUNTAIN_FLOAT+.3*SHRINK,CLOUD_HIGH=4.8*SHRINK,CLOUD_OPEN0=Math.toRadians(20),CLOUD_OPEN1=Math.toRadians(45);
    static final int CLOUD_SEGS=160;
    static void texVertex(net.minecraft.client.render.BufferBuilder b,org.joml.Matrix4f m,Vec3d p,float u,float v,float a){
        b.vertex(m,(float)p.x,(float)p.y,(float)p.z).texture(u,v).color(255,255,255,Math.max(0,Math.min(255,Math.round(a*255)))).next();
    }

    /**
     * A cloud of the formation (2.82.2, user request: "구름과 산이 너무 이상하게 생겼어 다시 예쁘게 꾸며주고", the video: soft
     * white clouds among and over the mountains, one end curled; low over the ground, thin wisps of mist; 2.82.1's smooth
     * white tubes looked strange): which way round the formation its curl lies (radians) and how fast it drifts round
     * (radians a second), how far out, how high, how long its band runs from its curl, how thick, how big its curl and how
     * many turns it winds (none for a wisp of mist), which way the curl turns (-1 up over the band, 1 down under it), how
     * high the band arches, when it comes in, how strong it is, its seed, and whether it is a bank of haze over the
     * mountains (2.82.16, user request: "산 위에는 희미한 안개같은 구름이 천천히 돌아가고": the curled clouds are gone).
     */
    record Cloud(double az,double drift,double radius,double up,double length,double thick,double curl,double turns,int dir,double arch,float in,float strength,int seed,boolean haze){
        boolean mist(){return curl<=0;}
    }
    private static final Map<UUID,Cloud[]> CLOUDS=new LinkedHashMap<>();
    /**
     * The clouds of the formation of swing {@code id} (the same on every client), its user having faced {@code yaw}
     * (2.82.16, user request: "산 위에는 희미한 안개같은 구름이 천천히 돌아가고", with their still of the game: soft, faint
     * banks of mist over and behind the mountains; then "구름은 더 많게 산까지 내려 올 정도로"): HAZE banks of faint haze
     * round the mountains, from among their middles up over their summits and behind them, all turning slowly
     * the same way round together (HAZE_TURN radians a second, clockwise seen from above, as the darker water flows), and
     * seven low wisps of mist over the ground. (2.82.2 to 2.82.15: curled clouds over the mountains and two before them.)
     */
    static Cloud[] clouds(UUID id,float yaw){
        var l=CLOUDS.get(id);if(l!=null)return l;
        int s0=(int)(id.getMostSignificantBits()*17+id.getLeastSignificantBits()*7);
        List<Cloud> out=new ArrayList<>();double off=behind(yaw);
        for(int i=0;i<HAZE;i++){
            int seed=s0+131*(i+1);
            out.add(new Cloud(off+Math.PI*2*(i+.5+.3*(Ease.hash(seed+1)-.5))/HAZE,HAZE_TURN,MOUNTAIN_R+.4+2.8*Ease.hash(seed+4),2.6+3.6*Ease.hash(seed+5),
                7+4*Ease.hash(seed+6),2.2+1.0*Ease.hash(seed+7),0,0,1,(Ease.hash(seed+11)-.5)*1.0,
                MIST0+.1f+.6f*(float)Ease.hash(seed+12),1,seed,true));
        }
        for(int i=0;i<7;i++){
            int seed=s0+1601*(i+1);
            out.add(new Cloud(Math.PI*2*(i+.6*Ease.hash(seed+1))/7,(Ease.hash(seed+2)<.5?-1:1)*(.012+.014*Ease.hash(seed+3)),IsosangRules.FORMATION_RADIUS-3.6+4*Ease.hash(seed+4),.35+.65*Ease.hash(seed+5),
                3.5+3*Ease.hash(seed+6),.4+.25*Ease.hash(seed+7),0,0,1,(Ease.hash(seed+11)-.5)*.4,
                MIST0+.6f*(float)Ease.hash(seed+12),.7f,seed,false));
        }
        l=out.toArray(new Cloud[0]);
        CLOUDS.put(id,l);while(CLOUDS.size()>16)CLOUDS.remove(CLOUDS.keySet().iterator().next());
        return l;
    }
    static final int HAZE=0;
    static final double HAZE_TURN=.05;
    /** How strongly a cloud shows at {@code t} (0 to 1): coming in, going with the mountains as the formation ends. */
    static float cloudShown(StandClient.Cast c,Cloud cl,float t){return Ease.smooth(t,cl.in(),cl.in()+1f)*(1-Ease.smooth(t,end(c)+GLOW,end(c)+GONE+.3f));}
    /** How many points of a cloud's path its curl takes (none for a wisp of mist). */
    static int curlPoints(Cloud cl){return cl.curl()>0?Math.max(12,(int)(Math.PI*2*cl.turns()*cl.curl()/.2)):0;}
    /**
     * A cloud's band at {@code t} in its own surface (round the formation at its radius): u along it (blocks round), v up
     * from its height, and its thickness (0 to 1), from the inner end of its curl out to its tail; flowing out from where
     * the curl meets the band as it comes in ({@code shown}), a slow wave running along it and its curl swaying a little.
     */
    static List<double[]> cloudPath(Cloud cl,float t,float shown){
        List<double[]> pts=new ArrayList<>();
        double len=cl.length(),wave=cl.curl()>0?.16:.1,lambda=cl.curl()>0?2.6:2.0;
        int nb=Math.max(8,(int)(len/.22));
        // the curl: from its inner end back out to where it meets the band
        int nc=curlPoints(cl);
        if(nc>0){
            double slope=cl.arch()*Math.PI/len,tl=Math.hypot(1,slope),tx=-1/tl,ty=-slope/tl;
            double nx=-ty*cl.dir(),ny=tx*cl.dir(),r=cl.curl()*(1+.04*Math.sin(t*.6+cl.seed()));
            double cx=nx*r,cy=ny*r,phi0=Math.atan2(-ny,-nx),total=Math.PI*2*cl.turns();
            double sway=.08*Math.sin(t*.35+cl.seed()),cs=Math.cos(sway),sn=Math.sin(sway);
            for(int i=nc;i>0;i--){
                double th=total*i/nc,rr=r*(1-.55*th/total),phi=phi0+cl.dir()*th,x=th/total;
                double px=cx+rr*Math.cos(phi),py=cy+rr*Math.sin(phi);
                pts.add(new double[]{px*cs-py*sn,px*sn+py*cs,(.35+.65*Math.pow(1-x,.8))*cloudIn(shown,x)});
            }
        }
        for(int i=0;i<=nb;i++){
            double u=len*i/nb,x=u/len;
            double v=cl.arch()*Math.sin(Math.PI*x)+wave*Math.sin(Math.PI*2*(u/lambda-t/5.5)+cl.seed())*Math.min(1,u/1.2);
            // (full to six tenths of the way, then rounding off; a little puffed out and drawn in along it, as a cloud is)
            double prof=(cl.haze()?Math.pow(Math.sin(Math.PI*x),.6):x<.6?1:Math.sqrt(Math.max(0,1-Math.pow((x-.6)/.4,2))))*(1+.14*Math.sin(u*2.3+cl.seed()*.7+t*.25)*Math.min(1,u/.8));
            pts.add(new double[]{u,v,prof*(cl.haze()?1:cloudIn(shown,x))});
        }
        return pts;
    }
    /** How much of a cloud has flowed out to {@code x} (0 where its curl meets its band, 1 at either end) as it comes in. */
    static double cloudIn(float shown,double x){return Ease.smooth((float)((shown*1.25-x)/.25),0,1);}
    /** A cloud's colours: its middle, its top and its underside. */
    static final int CLOUD=0xf3f6fb,CLOUD_TOP=0xffffff,CLOUD_UNDER=0xaeb8cc;
    /** How far apart a cloud's puffs are along it (of a puff's radius), and how many sides a puff has. */
    static final double PUFF_STEP=.55;
    static final int PUFF_SEGS=8;
    /**
     * A cloud (the blended pass): soft puffs along its band, big where the band is full and smaller toward its ends and
     * round its curl, uneven in size and spacing, smaller puffs swelling here and there over the band's top so its edge is
     * scalloped as a cloud's;
     * a wisp of mist is a line of flat puffs. Each puff is three soft discs fixed in the world 60° apart round the upright
     * (never turned to the camera, as every smoke and mist here), white over its middle, greyer underneath, fading to
     * nothing at its edge.
     */
    static void cloud(StandClient.Cast cast,EffectMesh m,float t,Vec3d centre,Cloud cl,Vec3d camera){
        float shown=cloudShown(cast,cl,t);if(shown<=.01f)return;
        var path=cloudPath(cl,t,shown);int n=path.size(),curl=curlPoints(cl);
        double az=cl.az()+cl.drift()*t,up=cl.up()+.12*Math.sin(t*.5+cl.seed()),grow=1+.3*Ease.smooth(t,end(cast)+GLOW,end(cast)+GONE+.3f);
        boolean mist=cl.mist(),haze=cl.haze();double aspect=haze?1.4:mist?2.3:1.15,tall=haze?1:mist?.55:1,least=haze?.35:mist?.3:.12;
        float a0=(haze?HAZE_ALPHA:mist?.1f:.14f)*cl.strength()*shown;
        double carry=0;int k=0;
        for(int i=0;i+1<n;i++){
            double[] p0=path.get(i),p1=path.get(i+1);double du=p1[0]-p0[0],dv=p1[1]-p0[1],len=Math.hypot(du,dv);if(len<1e-9)continue;
            while(carry<=len){
                double f=carry/len,u=p0[0]+du*f,v=p0[1]+dv*f,prof=p0[2]+(p1[2]-p0[2])*f;
                double r=cl.thick()*.5*prof*grow*(.72+.5*Ease.hash(cl.seed()*31+k*7));
                if(r>.04){
                    puff(m,centre,cl,az,up,u,v,r*aspect,r*tall,a0,camera);
                    // the band's top swells here and there into a smaller puff (not round the curl, nor its thin ends)
                    if(!mist&&i>=curl&&prof>.5&&Ease.hash(cl.seed()*13+k*5)<.4){
                        double nu=-dv/len,nv=du/len;if(nv<0){nu=-nu;nv=-nv;}
                        double br=r*(.45+.3*Ease.hash(cl.seed()*17+k*3)),lift=r*(.35+.25*Ease.hash(cl.seed()*19+k));
                        puff(m,centre,cl,az,up,u+nu*lift,v+nv*lift,br*aspect,br,a0*.85f,camera);
                    }
                }
                k++;carry+=Math.max(least,r*PUFF_STEP*(.8+.4*Ease.hash(cl.seed()*23+k*11)));
            }
            carry-=len;
        }
    }
    /** A puff of a cloud at {@code u} along its band and {@code v} up: three soft discs {@code ru} across and {@code rv} up, 60° apart round the upright, the first facing the formation's middle. */
    static void puff(EffectMesh m,Vec3d centre,Cloud cl,double az,double up,double u,double v,double ru,double rv,float alpha,Vec3d camera){
        double a=az+u/cl.radius();Vec3d out=new Vec3d(Math.cos(a),0,Math.sin(a)),along=new Vec3d(-out.z,0,out.x);
        Vec3d c=centre.add(out.multiply(cl.radius())).add(0,up+v,0),vv=new Vec3d(0,rv,0);
        // (haze thins away as the camera comes into it: it drifts round over her camera too)
        if(cl.haze())alpha*=Ease.smooth((float)c.distanceTo(camera),HAZE_NEAR0,HAZE_NEAR1);if(alpha<=.002f)return;
        // (each disc thins as it turns edge-on to the camera: seen edge-on a soft disc is a bright bar; 2.82.16)
        Vec3d look=c.subtract(camera);double ll=look.length();look=ll<1e-6?new Vec3d(0,0,1):look.multiply(1/ll);
        for(int s=0;s<3;s++){
            double turn=Math.PI/3*s;Vec3d dir=along.multiply(Math.cos(turn)).add(out.multiply(Math.sin(turn))),uu=dir.multiply(ru);
            float facing=(float)Math.abs(dir.crossProduct(UP).normalize().dotProduct(look));
            disc(m,c,uu,vv,(s==0?alpha:alpha*.72f)*Ease.smooth(facing,.12f,.5f),cl.haze());
        }
    }
    /** A soft disc: full over its inner six tenths, fading to nothing at its rim; white along its top, greyer under it (haze an even pale grey, faint and soft). */
    static void disc(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,float alpha,boolean haze){
        int mid=haze?HAZE_GREY:CLOUD,top=haze?HAZE_GREY:CLOUD_TOP,under=haze?HAZE_UNDER:CLOUD_UNDER;float inner=haze?.6f:.85f;
        for(int i=0;i<PUFF_SEGS;i++){
            double a0=Math.PI*2*i/PUFF_SEGS,a1=Math.PI*2*(i+1)/PUFF_SEGS;
            Vec3d d0=u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))),d1=u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
            Vec3d i0=c.add(d0.multiply(.6)),i1=c.add(d1.multiply(.6)),o0=c.add(d0),o1=c.add(d1);
            int c0=Math.sin(a0)>0?top:under,c1=Math.sin(a1)>0?top:under;
            m.vertex(c,mid,alpha);m.vertex(i0,mid,alpha*inner);m.vertex(i1,mid,alpha*inner);m.vertex(c,mid,alpha);
            m.vertex(i0,mid,alpha*inner);m.vertex(o0,c0,0);m.vertex(o1,c1,0);m.vertex(i1,mid,alpha*inner);
        }
    }
    /** The haze: how strongly each of its puffs lies, its pale grey (a little bluish, darker under it), and how near the camera it thins away to nothing (blocks). */
    static final float HAZE_ALPHA=.19f,HAZE_NEAR0=1.5f,HAZE_NEAR1=4.5f;
    static final int HAZE_GREY=0xc8d0da,HAZE_UNDER=0xa3aebd;
    /**
     * The mountains and the clouds (the blended pass): everything drawn from the farthest from the camera to the nearest,
     * the blended pass keeping no depth of its own.
     */
    static void scenery(Fx c,EffectMesh m,float t){
        Vec3d centre=centre(c.cast);
        record Far(double far,Runnable draw){}
        List<Far> all=new ArrayList<>();
        // (the mountains and the haze behind them are drawn in the ground pass since 2.82.16: mountainInk)
        for(var cl:clouds(c.cast.id(),yaw(c.cast))){
            if(cl.haze()||cloudShown(c.cast,cl,t)<=.01f)continue;
            double a=cl.az()+cl.drift()*t+cl.length()*.3/cl.radius();
            Vec3d mid=centre.add(Math.cos(a)*cl.radius(),cl.up(),Math.sin(a)*cl.radius());
            all.add(new Far(mid.squaredDistanceTo(c.camera),()->cloud(c.cast,m,t,centre,cl,c.camera)));
        }
        all.sort(Comparator.comparingDouble((Far f)->-f.far()));
        for(var f:all)f.draw().run();
    }

    // ───────────────────────────── the ground ─────────────────────────────

    /**
     * Where the ink lies: round the formation's middle, from the first (2.82.5, user request: "검들이 왜 처음에 앞으로
     * 이동하는데 처음부터 플레이어 기준 외곽쪽으로 나오게 해달라고", "왜 플레이어 아래있다가 점점 커지는데": 2.82.4 opened it
     * under her and slid it forward to the middle as it swirled out; now nothing of it moves, it gathers into the middle
     * and swirls out from there).
     */
    static Vec3d poolCentre(StandClient.Cast c){return centre(c);}
    /**
     * How far the ink reaches from the formation's middle as it gathers (blocks): POOL_WIDE as it soaks in and lies there;
     * then its edge eases into motion and sweeps in (the video: to some five blocks in a tenth of a second), lingers as it
     * closes slowly, and is drawn in all the way at the last, faster and faster, gone into the middle as the flash breaks.
     */
    static double gather(float t){
        double sweep=Math.pow(1-Ease.smooth(t,GATHER0,GATHER_FAST),2.4);
        double r=POOL_IN+(GATHER_MID-POOL_IN)*(1-Ease.smooth(t,GATHER_SLOW,GATHER1))+(POOL_WIDE-GATHER_MID)*sweep;
        return r*(1-Ease.in(t,COLLAPSE0,COLLAPSE));
    }
    /** How strongly the gathering ink is drawn (0 to 1): there from POOL0 (it soaks in by patches, soak), gone as it is drawn into the middle. */
    static float gathered(float t){return Ease.smooth(t,POOL0,POOL0+.02f)*(1-Ease.smooth(t,COLLAPSE-.02f,COLLAPSE));}
    /** How far the ink has soaked into the ground as she leaps (0 to 1: in patches all over it at once, never from one place). */
    static float soak(float t){return Ease.smooth(t,POOL0,POOL1);}
    /** How far the whirlpool reaches from the middle once the spike has burst there (blocks; 0 before SWIRL0): slow to start, fastest midway, all the way out at WHIRL1. */
    static double whirl(float t){return t<SWIRL0?0:WHIRL_R*Ease.smooth(t,SWIRL0,WHIRL1);}
    /** How wide its black eye is (blocks): opening after it has started to spread, EYE_R from EYE1. */
    static double eye(float t){return EYE_R*Ease.smooth(t,EYE0,EYE1);}
    /** The ink shader's byte for the eye (style 15): its radius as a share of the whirlpool's, over .6 (0 while there is none). */
    static float eyeByte(float t){double w=whirl(t);return w<=1e-6?0:Math.round(MathHelper.clamp(eye(t)/w/.6,0,1)*255)/255f;}
    /**
     * How far the clean ground behind the spreading whirlpool reaches from the middle (blocks): 0 until SPREAD0, out to the
     * rim at SPREAD1, easing in, fastest a third of the way through (some 45 blocks a second) and slowing as it gets there
     * (as the ink shader draws it).
     */
    static double front(float t){return RIM_R*Math.pow(Ease.smooth(t,SPREAD0,SPREAD1),.7);}
    /** How far the whirlpool's edge is pushed out as it spreads (a share of its radius; as the ink shader draws it). */
    static double pushed(float t){return SPREAD_PUSH*Ease.smooth(t,SPREAD0,SPREAD1);}
    /** How much of the whirlpool is left (1 to 0): all of it as it spreads, gone as what reached the rim turns into the smoke (as the ink shader draws it). */
    static float whirlShown(float t){return 1-Ease.smooth(t,WATER_OUT0,WATER_OUT1);}
    /** Where the smoke lies (blocks from the middle): at the rim, drifting a little further out as it goes (as the ink shader draws it). */
    static double rim(float t){return RIM_R+(RIM_OUT-RIM_R)*Ease.smooth(t,SMOKE_FULL,SMOKE_OUT1);}
    /** How thick the smoke lies (0 to 1): coming as the whirlpool spreads out past it, gone as it thins away (as the ink shader draws it). */
    static float smoke(float t){return Ease.smooth(t,SMOKE_IN,SMOKE_FULL)*(1-Ease.smooth(t,SMOKE_OUT0,SMOKE_OUT1));}
    /**
     * The water lying on the formation's ground once the whirlpool has gone, until the formation ends (2.82.13, user
     * request: "소용돌이가 끝나고 H가 끝날때까지 바닥 지속적으로 흐르는 물 같은 이펙트를 만들어야함 영상 보면 바닥에서 연하게
     * 뭐가 계속 흐르는 거 보여?", then "물이 너무 연하잖아 더 진하게 만들어 엄청 진하게 만드는데 원래 바닥은 보일정도로
     * 조금 연하게", "물 색은 남색", "원본은 그냥 검정색 결이 순환하는데 너가 만든건 소용돌이야"; the video and the build side
     * by side, read and confirmed: the water leaves the middle bare and lies round it, faint, the ground showing well
     * through it; darker patches flow in it, soft with clear edges; strands of pale water only at the far rim; 2.82.14,
     * user request: "산이랑 물이랑 X좌표 붙어있게 해 떨어져있으니까 이상함", "그리고 물은 안 움직이고 검정색만 움직이게 해
     * 영상도 검정색만 순환하잖아"; 2.82.15, user request: "너무 빨리 돌아가고 검정색이 너무 커 영상처럼 지나치게 두껍거나
     * 넓은 검은 면이 되지 않도록 하고, 가늘고 길게 이어지는 형태로 만들어어함 걍 검정색을 강가의 흐름처럼 만들면 되는데"):
     * then "가운데 검정색이 순환", "전체적으로 순환하는게 안 보여? 검정색이?", of the video's darker water marked red: "너가
     * 빨간색으로 표시한 이거 맞음 이게 완전 검정이 아니고 물의 흐름 느낌으로 만든거 같음", and "가운데는 왜 흐름? 아무것도
     * 없는곳인데"): a see-through navy water, still, from round the formation's bare middle out to the mountains' feet
     * (shore); in it (not over the bare middle) long bands of its darker water flowing, see-through (not black), some a
     * block wide, curving round it, the only thing of it that moves: turning slowly clockwise seen from above (FLOW_TURN
     * radians a second, as the video's darker water goes round) and spreading outward as they go (FLOW_OUT blocks a
     * second), each
     * coming and going along its length (the ink shader's FLOW_W and FLOW_OUT); a few thin strands of pale water, still,
     * along the mountains' feet (the ink shader, style 14: the water seeds 8 to 11, the strands 12 to 15; its uv the
     * offset over FLOW_SCALE blocks). How strongly it lies (0 to 1: coming in as the whirlpool's water turns into the
     * smoke, FLOW0 to FLOW1, and going as the formation ends, with the mountains), and the formation's clock the flow
     * runs by, in thousandths of a second in sixteen bits.
     */
    static float flowLife(float t,float end){return Ease.smooth(t,FLOW0,FLOW1)*(1-Ease.smooth(t,end,end+GONE));}
    static int flowClock(float t){return MathHelper.clamp(Math.round(t*1000),0,65535);}
    static final float FLOW0=1.3f,FLOW1=2f;
    /**
     * (FLOW_SCALE: the shader's unit for the water, 11 blocks: its darker water's bands some a block wide, spreading out
     * FLOW_OUT blocks a second, as in 2.82.15; 2.82.16 had it the formation's reach and a block, 15.5, its bands and its
     * bare middle half as big again. Fixed since 2.82.17: the formation 10 blocks wide, its bare middle as in 2.82.15,
     * some 5 to 6.7 blocks out.)
     */
    static final double FLOW_TURN=.2,FLOW_OUT=.5,FLOW_SCALE=11;
    /**
     * Where the water lies round the formation (2.82.14, user request: "산이랑 물이랑 X좌표 붙어있게 해 떨어져있으니까
     * 이상함": it ended a block and more short of the mountains, the ground bare between): its rings at SHORE_STEPS ways
     * round, from WATER_IN blocks out (inside the shader's soft inner edge: the formation's middle is bare, its darker
     * water flowing only in its water, 2.82.15) to the foot of the nearest mountain that way, fading out over EDGE_PAST
     * blocks past it, under the mountain; and the strip of its pale strands along the mountains' feet (STRAND_* blocks
     * inside the edge, there only as far as a mountain stands that way: cover). Since 2.82.17 (user request: "전체 범위를
     * 줄여달라는거였는대", "10블록으로 줄이고") its edge is the formation's, WATER_EDGE, all round, the mountains off beyond
     * it; its strands still broken where the mountains leave a gap. Fixed for a formation, as its mountains are.
     */
    record Shore(double[][] water,double[][] strands,float[] cover){}
    private static final Map<UUID,Shore> SHORES=new LinkedHashMap<>();
    static Shore shore(UUID id,float yaw){
        var s=SHORES.get(id);if(s!=null)return s;
        var ms=mountains(id,yaw);int n=SHORE_STEPS;
        double[] in=new double[n],edge=new double[n],past=new double[n],s0=new double[n],s1=new double[n],s2=new double[n],s3=new double[n];float[] cover=new float[n];
        for(int k=0;k<n;k++){
            double az=Math.PI*2*k/n,e=WATER_EDGE;
            in[k]=WATER_IN;edge[k]=e;past[k]=e+EDGE_PAST;cover[k]=(float)mountainCover(ms,az);
            s0[k]=e-STRAND_FROM;s1[k]=e-STRAND_FULL0;s2[k]=e-STRAND_FULL1;s3[k]=e-STRAND_TO;
        }
        s=new Shore(new double[][]{in,edge,past},new double[][]{s0,s1,s2,s3},cover);
        SHORES.put(id,s);while(SHORES.size()>16)SHORES.remove(SHORES.keySet().iterator().next());
        return s;
    }
    /** How strongly the water lies on each of its rings (to its edge, then fading out past it). */
    static final float[] WATER_RINGS={1,1,0},STRAND_RINGS={0,1,1,0};
    static final int SHORE_STEPS=180;
    /** (WATER_EDGE: the mountains' feet, as since 2.82.14: "산이랑 물이랑 X좌표 붙어있게 해"; the six go all the way round.) */
    static final double WATER_EDGE=MOUNTAIN_R,WATER_IN=3.3,EDGE_PAST=.6*SHRINK,EDGE_FADE=1.5*SHRINK,STRAND_FROM=2.6*SHRINK,STRAND_FULL0=1.7*SHRINK,STRAND_FULL1=.8*SHRINK,STRAND_TO=.2*SHRINK;
    /** How much of the mountain {@code mt} stands the way {@code az} (0 to 1): 1 along it, fading out over EDGE_FADE blocks past its ends; 0 off to its sides. */
    static double along(Mountain mt,double az){
        double off=Math.IEEEremainder(az-mt.az(),Math.PI*2);if(Math.abs(off)>1.2)return 0;
        double over=Math.abs(mt.radius()*off)-mt.half();
        return over<=0?1:1-Ease.smooth((float)over,0,(float)EDGE_FADE);
    }
    /** How much a mountain stands the way {@code az} (0 to 1, the most of any). */
    static double mountainCover(Mountain[] ms,double az){double c=0;for(var mt:ms)c=Math.max(c,along(mt,az));return c;}
    /** The ink shader's byte for the gathering sheet (style 13): how far it has soaked in (0 to 127). */
    static float soakByte(float share){return Math.round(MathHelper.clamp(share,0,1)*127)/255f;}
    /** The formation's clock as the ink shader reads it (seconds over two, 0 to 1). */
    static float poolClock(float t){return MathHelper.clamp(t/2,0,1);}
    /** A ground ring's edge at the angle {@code a} (radians), as a share of its radius: wavering a little, slowly. */
    static double wobble(double a,int seed,float t){return 1+.035*Math.sin(a*5+seed+t*3)+.02*Math.sin(a*11-seed);}
    /**
     * The ink lying on the ground, under everything else of it (2.82.6, the ink shader's ground pass, before the swords,
     * the mountains and the light): the blue of the formation's ground; the ink gathering into its middle; the whirlpool
     * (2.82.7); the still navy water lying on the ground as it stands, out to the mountains' feet, bands of darker water
     * flowing in it (2.82.13 to 2.82.15: style 14, seeds 8 to 15, the formation's clock in the green and
     * blue bytes; drawn in rings out to the mountains, KagenoFire.inkGroundRings). The others each a square of the ink shader
     * (KagenoFire.inkGround) on the ground, its uv the offset over its radius.
     */
    static void ground(Fx c){
        float t=c.sec;Vec3d centre=centre(c.cast);int seed=c.cast.id().hashCode();
        float blue=tint(t);
        if(blue>.005f){double r=RIM_R-.1;KagenoFire.inkGround(centre.add(0,.04,0),r,r,14,seed&7,holeByte(t,r),0,blue);}
        float water=flowLife(t,end(c.cast));
        if(water>.005f){
            var sh=shore(c.cast.id(),yaw(c.cast));int clock=flowClock(t);Vec3d at=centre.add(0,.045,0);
            KagenoFire.inkGroundRings(at,sh.water(),WATER_RINGS,null,FLOW_SCALE,14,8|(seed&3),(clock>>8)/255f,(clock&255)/255f,water);
            KagenoFire.inkGroundRings(at,sh.strands(),STRAND_RINGS,sh.cover(),FLOW_SCALE,14,12|(seed&3),0,0,water);
        }
        double g=gather(t);float a=gathered(t);
        if(a>.005f&&g>.02)KagenoFire.inkGround(centre.add(0,.05,0),g*1.45,g,13,seed,poolClock(t),soakByte(soak(t)),a);
        // (its square big enough for its edge pushed out as it spreads, its streams' tails a little past its radius; drawn
        // until the smoke at its rim has thinned away)
        double w=whirl(t);float o=Ease.smooth(t,SWIRL0,SWIRL0+.02f)*(1-Ease.smooth(t,SMOKE_OUT1-.03f,SMOKE_OUT1+.02f));
        if(o>.005f&&w>.02)KagenoFire.inkGround(centre.add(0,.055,0),w*1.4,w,15,seed+5,poolClock(t),eyeByte(t),o);
        // the night over it (2.99.0), the mountains (2.82.16), then the light on its water, in draw calls of their own,
        // once: in the blended pass, not the light added after it
        if(KagenoFire.crescentGain>.99f){
            boolean night=FormationSky.up(t,end(c.cast)),hills=t>RISE0-.05f&&t<end(c.cast)+GONE+.4f;
            if(night||hills)KagenoFire.inkInterlude(()->{if(night)FormationSky.sky(c);if(hills)mountainInk(c);if(night)FormationSky.water(c);});
        }
    }
    /**
     * The ink left on the ground {@code d} blocks from the formation's middle (0 to 1): gone as the spreading whirlpool's
     * clean front passes it (2.82.9, user request: "바닥 잉크도 퍼질때 동시에 사라지게 하면 안되나?": no blue ground left
     * under the formation after it, nor the ink splashed at the swords' feet; 2.82.10: from the middle out with the front,
     * drying away just behind it).
     */
    static float groundInk(float t,double d){return 1-Ease.smooth((float)front(t),(float)d-1.5f,(float)Math.min(d+2.5,RIM_R));}
    /** The blue of the formation's ground (0 to about .6): coming in as the whirlpool swirls out; its middle cleared by the front (holeByte), gone once it reaches the rim. */
    static float tint(float t){return .58f*Ease.smooth(t,SWIRL0,SWIRL0+.15f)*(1-Ease.smooth(t,SPREAD1-.06f,SPREAD1+.04f));}
    /** The ink shader's byte for the blue ground's cleared middle (style 14): the front's reach as a share of the square's half-width {@code r}, over 1.25. */
    static float holeByte(float t,double r){return Math.round(MathHelper.clamp(front(t)/r/1.25,0,1)*255)/255f;}
    /** A ring of light lying on the ground (radius {@code r}, {@code w} wide, brightest along its middle), wavering a little. */
    static void groundBand(EffectMesh m,Vec3d c,double r,double w,int rgb,float alpha,int seed,float t){
        if(alpha<=.005f||r<=.01)return;int n=Math.max(40,(int)(r*20));
        for(int i=0;i<n;i++){
            double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n,k0=wobble(a0,seed,t),k1=wobble(a1,seed,t);
            Vec3d d0=new Vec3d(Math.cos(a0),0,Math.sin(a0)),d1=new Vec3d(Math.cos(a1),0,Math.sin(a1));
            Vec3d i0=c.add(d0.multiply(Math.max(0,r*k0-w))),i1=c.add(d1.multiply(Math.max(0,r*k1-w))),m0=c.add(d0.multiply(r*k0)),m1=c.add(d1.multiply(r*k1)),o0=c.add(d0.multiply(r*k0+w)),o1=c.add(d1.multiply(r*k1+w));
            m.vertex(i0,rgb,0);m.vertex(i1,rgb,0);m.vertex(m1,rgb,alpha);m.vertex(m0,rgb,alpha);
            m.vertex(m0,rgb,alpha);m.vertex(m1,rgb,alpha);m.vertex(o1,rgb,0);m.vertex(o0,rgb,0);
        }
    }
    /** A disc lying on the ground: {@code rgb} all over it to {@code soft} of its radius, fading to nothing at its rim. */
    static void groundDisc(EffectMesh m,Vec3d c,double r,double soft,int rgb,float alpha){
        if(alpha<=.005f)return;int n=56;
        for(int i=0;i<n;i++){
            double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n;
            Vec3d d0=new Vec3d(Math.cos(a0),0,Math.sin(a0)),d1=new Vec3d(Math.cos(a1),0,Math.sin(a1));
            m.vertex(c,rgb,alpha);m.vertex(c.add(d0.multiply(r*soft)),rgb,alpha);m.vertex(c.add(d1.multiply(r*soft)),rgb,alpha);m.vertex(c,rgb,alpha);
            m.vertex(c.add(d0.multiply(r*soft)),rgb,alpha);m.vertex(c.add(d0.multiply(r)),rgb,0);m.vertex(c.add(d1.multiply(r)),rgb,0);m.vertex(c.add(d1.multiply(r*soft)),rgb,alpha);
        }
    }
    // ───────────────────────────── the passes ─────────────────────────────

    /**
     * The ink: the ink splashed at every sword's foot as it strikes, and as it ends the ink each sword breaks into, where it
     * falls (the ink lying on the formation's ground, gathering and swirling out, is the ground pass's, under the swords).
     */
    static void ink(Fx c){
        float t=c.sec;Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);double floor=centre.y+.02;
        // a splash of ink at every sword's foot as it strikes (the video: black splashes round each one's flower), gone as
        // the spreading whirlpool's clean front passes it (2.82.9, 2.82.10)
        if(t<SPREAD1+.1f)for(var b:layout(c.cast.id())){
            if(t<b.plant())continue;Vec3d f=foot(centre,yaw,b);float left=groundInk(t,Math.hypot(b.fw(),b.rt()));if(left<=.005f)continue;
            GiantSweep.splatFor(new Vec3d(f.x,floor+.003+.001*(b.seed()&7),f.z),1.3+.8*b.flower(),Math.PI*2*Ease.hash(b.seed()+40),1+.3*Ease.hash(b.seed()+41),b.seed()&15,t-b.plant()-.02f,.9f*left);
        }
        // as it ends: ink where the swords stood and where the ink they break into falls (2.101.0: none when H again took
        // its swords)
        float e=end(c.cast)+INKED;
        if(t>=e&&!taken(c.cast))for(var b:layout(c.cast.id())){
            Vec3d f=foot(centre,yaw,b);
            GiantSweep.splatFor(new Vec3d(f.x,floor+.004+.001*(b.seed()&7),f.z),1.5+.9*Ease.hash(b.seed()+43),Math.PI*2*Ease.hash(b.seed()+44),1.1,(b.seed()+3)&15,t-e-.04f,.85f);
        }
        for(var d:drops(c,t)){
            if(d.size()<STAIN_MIN)continue;
            double land=lands(d,floor+.01);if(land<0)continue;
            float age=t-d.born()-(float)land;if(age<0||age>=STAIN_LIFE)continue;
            Vec3d p=flight(d,land),mv=moving(d,land);
            stainAt(new Vec3d(p.x,floor+.001*(d.seed()%7),p.z),d.size()*4.6,Math.atan2(mv.z,mv.x),1+Math.min(1.4,Math.hypot(mv.x,mv.z)*.3),d.seed(),age,.78f);
        }
    }
    /** The ink each sword breaks into as the formation ends: flakes and drops flung off along it, falling. */
    static List<Drop> drops(Fx c,float t){
        List<Drop> out=new ArrayList<>();float e=end(c.cast)+INKED;if(t<e||taken(c.cast))return out;
        Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        for(var b:layout(c.cast.id())){
            Vec3d top=planted(centre,yaw,b),f=foot(centre,yaw,b),out0=f.subtract(centre).multiply(1,0,1);out0=out0.lengthSquared()<1e-4?Vec3d.fromPolar(0,yaw):out0.normalize();
            for(int k=0;k<5;k++){
                int seed=b.seed()*7+k*31+5003;double u=.15+.8*Ease.hash(seed);
                Vec3d at=top.lerp(f,u);double az=Math.PI*2*Ease.hash(seed+1),sp=.8+1.8*Ease.hash(seed+2);
                Vec3d vel=new Vec3d(Math.cos(az),0,Math.sin(az)).multiply(sp).add(out0.multiply(.6)).add(0,.6+1.4*Ease.hash(seed+3),0);
                int kind=k%3==1?1:k%4==3?2:0;
                out.add(new Drop(at,vel,3,3,e+.06f*(float)Ease.hash(seed+4),.6f+.35f*(float)Ease.hash(seed+5),kind==0?.04+.05*Ease.hash(seed+6):.06+.07*Ease.hash(seed+6),seed,kind));
            }
        }
        return out;
    }
    /**
     * The blended pass: the blue of the formation's ground, the ink gathering into its middle, the ink mountains, the clouds
     * and the mist, the black spike out of the middle, the swords (farthest first), the crystal flowers at their feet, and
     * as it ends the ink the swords break into; the ink round whom it strikes.
     */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec;Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        scenery(c,m,t);
        spike(m,centre,t);
        ripples(c,m,t,centre,yaw);
        wisps(c,m,t,centre,yaw);
        drawSwords(c,m,t,centre,yaw);
        flecks(c,m,t,centre,yaw);
        inkLines(c,m,t,centre);
        flowers(c,m,t,centre,yaw,false);
        // (2.93.0) Z: the sword she looks at (hers only) and her flights between them
        SwordStep.alpha(c,m,t,centre,yaw);
        double floor=centre.y+.03;
        for(var d:drops(c,t)){
            float age=t-d.born();if(age<0||age>=d.life())continue;
            double land=lands(d,floor);if(land>=0&&age>=land)continue;
            Vec3d q=flight(d,age),mv=moving(d,age);float u=age/d.life();
            float by=near(q,c.camera);if(by<=0)continue;
            double size=d.size()*(1-.45*u)*Ease.out(age,0,.06f)*(.35+.65*by);float a=.92f*(1-Ease.smooth(u,.6f,1))*by;
            switch(d.kind()){
                case 1->flake(m,q,size,age,1.2+2.0*Ease.hash(d.seed()+9),d.seed(),INK,a);
                case 2->blot(m,q,mv,size,d.seed(),INK,a);
                default->drop(m,q,mv,size,d.seed(),INK,a);
            }
        }
        struck(c,m,t,true);
    }
    /** The swords, drawn from the farthest to the nearest (the blended pass has no depth of its own). */
    static void drawSwords(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        var all=layout(c.cast.id());Integer[] order=new Integer[all.length];Vec3d[] tops=new Vec3d[all.length];
        for(int i=0;i<all.length;i++){order[i]=i;tops[i]=planted(centre,yaw,all[i]).add(0,above(all[i],t),0);}
        Arrays.sort(order,Comparator.comparingDouble((Integer i)->-tops[i].squaredDistanceTo(c.camera)));
        float e=end(c.cast),flare=taken(c.cast)?0:Ease.smooth(t,e,e+.04f)*(1-Ease.smooth(t,e+GLOW,e+INKED)),ink=taken(c.cast)?0:Ease.smooth(t,e+GLOW,e+INKED);
        for(int i:order){
            var b=all[i];float a=shown(c.cast,b,t);if(a<=.005f)continue;
            sword(m,c.camera,tops[i],frame(down(b),spinAt(b,t)),a,dark(b,t),flare,ink);
        }
    }
    // ───────────────────────────── as it stands: rings at the swords' feet, black smoke off their blades ─────────────────────────────

    /** How strongly the formation stands at {@code t} (0 to 1): coming in as its swords turn silver, gone as it ends (its swords flaring). */
    static float standing(StandClient.Cast c,float t){float e=end(c);return Ease.smooth(t,STAND0,STAND0+.4f)*(1-Ease.smooth(t,e,e+GLOW));}
    static final float STAND0=1.6f;
    /**
     * Rings spreading from the swords' feet as from swords standing in water (2.82.13, the user's video read and
     * confirmed: "검 밑동에서 동그란 물결이 퍼짐"): now and then (each sword every RIPPLE_EVERY seconds or so, at its own
     * time) a thin dark ring opens from its foot out to RIPPLE_R blocks over RIPPLE_LIFE seconds, quick at first, wavering
     * a little and fading as it goes; while the formation stands.
     */
    static void ripples(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        float on=standing(c.cast,t);if(on<=.01f)return;
        for(var b:layout(c.cast.id())){
            double u=rippleAge(b,t);if(u<0)continue;
            float a=rippleAlpha(u)*on;if(a<=.004f)continue;
            groundBand(m,foot(centre,yaw,b).add(0,.04,0),rippleRadius(u),.03+.035*u,RIPPLE_INK,a,b.seed(),t);
        }
    }
    /** How far through its ring a sword's foot is at {@code t} (0 to 1), or -1 between rings. */
    static double rippleAge(Blade b,float t){
        double period=RIPPLE_EVERY*(.75+.5*Ease.hash(b.seed()+97)),u=((t+period*Ease.hash(b.seed()+98))%period)/RIPPLE_LIFE;
        return u<1?u:-1;
    }
    /** A ring's radius {@code u} through it (blocks): quick at first, slowing as it spreads. */
    static double rippleRadius(double u){return .18+(RIPPLE_R-.18)*(1-Math.pow(1-u,2.2));}
    /** A ring's darkness {@code u} through it: coming in at once, fading as it spreads. */
    static float rippleAlpha(double u){return (float)(RIPPLE_ALPHA*Ease.smooth((float)u,0,.08f)*Math.pow(Math.max(0,1-u),1.4));}
    static final float RIPPLE_EVERY=3.4f,RIPPLE_LIFE=1.5f,RIPPLE_ALPHA=.4f;
    static final double RIPPLE_R=1.5;
    static final int RIPPLE_INK=0x070c22;
    /** A unit vector square to {@code d}, {@code az} radians round it. */
    static Vec3d perp(Vec3d d,double az){
        Vec3d x0=d.crossProduct(UP);if(x0.lengthSquared()<1e-6)x0=new Vec3d(1,0,0);x0=x0.normalize();
        Vec3d x1=d.crossProduct(x0).normalize();
        return x0.multiply(Math.cos(az)).add(x1.multiply(Math.sin(az)));
    }
    /**
     * Where one of a sword's shed things is in its life at {@code t}: its share of the way through it (0 to 1) and the
     * seed of this one (a new one, somewhere else, as each goes), the {@code k}th of {@code count}, each lasting about
     * {@code life} seconds.
     */
    static double[] shed(int base,int k,int count,float life,float t){
        double period=life*(.85+.3*Ease.hash(base)),s=t+period*(k/(double)count+.3*Ease.hash(base+1));
        double cycle=Math.floor(s/period);return new double[]{s/period-cycle,base+cycle*173};
    }
    /**
     * The dark smoke flowing off the swords' blades (2.82.13, user request: "그리고 검들도 검정 이펙트가 조금씩 흐르고",
     * their video read and confirmed: dark smoke beside the blades): each sword trails WISPS strands of it at a time, each
     * from a place on its blade, streaming up along it and curling away from it as it thins (over WISP_LIFE seconds or
     * so), another following somewhere else as each goes; soft dark ribbons in two crossed planes along the blade, fixed in
     * the world (not turned to the camera); while the formation stands.
     */
    static void wisps(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        float on=standing(c.cast,t);if(on<=.01f)return;
        for(var b:layout(c.cast.id())){
            Vec3d top=planted(centre,yaw,b),d=down(b);
            for(int k=0;k<WISPS;k++){
                double[] life=shed(b.seed()*11+k*37+611,k,WISPS,WISP_LIFE,t);double u=life[0];int seed=(int)life[1];
                float a=wispAlpha(u)*on;if(a<=.004f)continue;
                Vec3d anchor=top.add(d.multiply(S_GUARD+.2+(S_LEN-S_GUARD-.55)*Ease.hash(seed+2)));
                float by=near(anchor,c.camera);if(by<=0)continue;
                Vec3d side=perp(d,Math.PI*2*Ease.hash(seed+3)),across=side.crossProduct(d).normalize();
                double len=.45+.7*u,gap=.03+.12*u,curl=.22*u,rise=.12*u,phase=Math.PI*2*Ease.hash(seed+4);
                int n=7;Vec3d[] pts=new Vec3d[n];double[] w=new double[n];float[] al=new float[n];
                for(int i=0;i<n;i++){
                    double sv=i/(double)(n-1);
                    pts[i]=anchor.subtract(d.multiply(len*sv)).add(side.multiply(gap+curl*sv*sv)).add(across.multiply(.05*Math.sin(5*sv+phase+3*u))).add(0,rise*sv,0);
                    w[i]=(.07+.08*u)*(1-.5*sv);
                    al[i]=a*by*(float)Math.pow(Math.sin(Math.PI*(.08+.84*sv)),.8);
                }
                ribbon(m,pts,w,al,side,INK);ribbon(m,pts,w,al,across,INK);
            }
        }
    }
    /** A strand of smoke's darkness {@code u} through its life: coming in, then thinning away. */
    static float wispAlpha(double u){return (float)(WISP_ALPHA*Ease.smooth((float)u,0,.2f)*(1-Ease.smooth((float)u,.5f,1)));}
    static final int WISPS=2,FLECKS=3;
    static final float WISP_LIFE=1.8f,WISP_ALPHA=.55f,FLECK_LIFE=1.5f;
    /** A soft ribbon along {@code pts}: {@code w} out either way along {@code across}, its colour at {@code al} along its middle, none at its edges. */
    static void ribbon(EffectMesh m,Vec3d[] pts,double[] w,float[] al,Vec3d across,int rgb){
        for(int i=0;i+1<pts.length;i++){
            Vec3d c0=pts[i],c1=pts[i+1],o0=across.multiply(w[i]),o1=across.multiply(w[i+1]);
            m.vertex(c0.subtract(o0),rgb,0);m.vertex(c0,rgb,al[i]);m.vertex(c1,rgb,al[i+1]);m.vertex(c1.subtract(o1),rgb,0);
            m.vertex(c0,rgb,al[i]);m.vertex(c0.add(o0),rgb,0);m.vertex(c1.add(o1),rgb,0);m.vertex(c1,rgb,al[i+1]);
        }
    }
    /**
     * Small black flecks of ink drifting off the swords (2.82.13, the user's video read and confirmed: small black flecks
     * flying round the swords): each sword sheds FLECKS at a time, each from a place on its blade, drifting out from it
     * and up a little, turning over as it goes (the M1's ink flakes, fixed in the world), fading over FLECK_LIFE seconds
     * or so, another following somewhere else; while the formation stands.
     */
    static void flecks(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        float on=standing(c.cast,t);if(on<=.01f)return;
        for(var b:layout(c.cast.id())){
            Vec3d top=planted(centre,yaw,b),d=down(b);
            for(int k=0;k<FLECKS;k++){
                double[] life=shed(b.seed()*13+k*53+977,k,FLECKS,FLECK_LIFE,t);double u=life[0];int seed=(int)life[1];
                float a=(float)(.85*on*Ease.smooth((float)u,0,.12f)*(1-Ease.smooth((float)u,.6f,1)));if(a<=.01f)continue;
                // every third a thin dash flung off faster (the video: long thin black strokes flying past), the rest flakes
                boolean dash=k%3==0;double age=u*FLECK_LIFE,speed=(dash?.7:.25)+(dash?.6:.45)*Ease.hash(seed+4);
                Vec3d start=top.add(d.multiply(S_GUARD+.1+(S_LEN-S_GUARD-.3)*Ease.hash(seed+2)));
                Vec3d away=perp(d,Math.PI*2*Ease.hash(seed+3)),lift=new Vec3d(0,.1+.3*Ease.hash(seed+5),0);
                Vec3d q=start.add(away.multiply(.05+speed*age)).add(lift.multiply(age)).add(0,-.08*age*age,0);
                float by=near(q,c.camera);if(by<=0)continue;
                if(dash)streak(m,q,away.multiply(speed).add(lift).add(0,-.16*age,0),(.12+.12*Ease.hash(seed+6))*(1-.3*u),.016,INK,a*by);
                else flake(m,q,(.04+.035*Ease.hash(seed+6))*(1-.3*u),age,.8+1.4*Ease.hash(seed+7),seed,INK,a*by);
            }
        }
    }
    /**
     * Long thin black lines flying across the whole formation as it stands (2.82.13, the video and the build side by
     * side, read and confirmed: long thin black lines flying all over the formation): INK_LINES at a time, each from
     * somewhere over the formation (out to its edge, half a block to three and a half up), flying straight a little way,
     * fast, thin and pointed at both ends, coming in and going (ink dashes fixed in the world); while the formation stands.
     */
    static void inkLines(Fx c,EffectMesh m,float t,Vec3d centre){
        float on=standing(c.cast,t);if(on<=.01f)return;
        int base=c.cast.id().hashCode()*31+1201;
        for(int k=0;k<INK_LINES;k++){
            double[] life=shed(base+k*71,k,INK_LINES,INK_LINE_LIFE,t);double u=life[0];int seed=(int)life[1];
            float a=(float)(.9*on*Ease.smooth((float)u,0,.15f)*(1-Ease.smooth((float)u,.65f,1)));if(a<=.01f)continue;
            double rr=IsosangRules.FORMATION_RADIUS*Math.sqrt(Ease.hash(seed+1)),az=Math.PI*2*Ease.hash(seed+2);
            double way=Math.PI*2*Ease.hash(seed+3),speed=3+3*Ease.hash(seed+4),age=u*INK_LINE_LIFE;
            Vec3d dir=new Vec3d(Math.cos(way),(Ease.hash(seed+5)-.5)*.5,Math.sin(way)).normalize();
            Vec3d q=centre.add(Math.cos(az)*rr,.5+3*Ease.hash(seed+6),Math.sin(az)*rr).add(dir.multiply(speed*age));
            float by=near(q,c.camera);if(by<=0)continue;
            streak(m,q,dir,(.6+1.2*Ease.hash(seed+7))*(.7+.3*Math.sin(Math.PI*u)),.02,INK,a*by);
        }
    }
    static final int INK_LINES=26;
    static final float INK_LINE_LIFE=.8f;
    /** A thin dash of ink at {@code q}, {@code len} long along the way it moves, pointed at both ends: two crossed planes along it, fixed in the world. */
    static void streak(EffectMesh m,Vec3d q,Vec3d moving,double len,double wid,int rgb,float alpha){
        if(alpha<=.01f||len<=.005)return;
        double speed=moving.length();Vec3d along=speed<1e-4?UP:moving.multiply(1/speed);
        Vec3d s1=perp(along,0),s2=along.crossProduct(s1).normalize(),a=q.subtract(along.multiply(len*.5)),b=q.add(along.multiply(len*.5));
        for(Vec3d s:new Vec3d[]{s1,s2}){
            Vec3d l=q.add(s.multiply(wid)),r=q.subtract(s.multiply(wid));
            m.triangle(a,l,b,rgb,alpha);m.triangle(a,b,r,rgb,alpha);
        }
    }
    /** How dark a sword is at {@code t} (0 to 1): dark as ink as it strikes (the video: black swords over the blue ground), silver again after. */
    static float dark(Blade b,float t){return Ease.smooth(t,b.plant()-.01f,b.plant()+.05f)*(1-Ease.smooth(t,SILVER0,SILVER1));}
    /** How bright a sword is in the sky (0 to 1): white as it hangs and falls, gone dark as it strikes. */
    static float sky(Blade b,float t){return t<b.plant()?1:1-Ease.smooth(t,b.plant(),b.plant()+.06f);}
    /**
     * The black spike shooting up out of the formation's middle where the ink has gathered, as the sword under her strikes
     * (the video: a flash, then a black point up out of the middle where the pool closed and faded, then a burst of blue
     * light; 2.82.4 had it under her, 2.82.5 back in the middle): two crossed blades of ink fixed in the world.
     */
    static void spike(EffectMesh m,Vec3d centre,float t){
        float at=IsosangRules.H_PLANT;if(t<at-.06f||t>=at+.12f)return;
        double h=2.6*Ease.out(t,at-.06f,at-.01f)*(1-Ease.smooth(t,at+.02f,at+.11f)),w=.38*(1-.5*Ease.smooth(t,at,at+.1f));if(h<=.02)return;
        Vec3d top=centre.add(0,h,0);
        for(int s=0;s<2;s++){
            Vec3d side=s==0?new Vec3d(w,0,0):new Vec3d(0,0,w);
            m.triangle(centre.subtract(side).add(0,.02,0),centre.add(side).add(0,.02,0),top,INK,.96f);
        }
    }
    /**
     * The crystal flowers at the swords' feet (the video: a flower of blue crystal petals round each sword as it strikes,
     * pale at their tips, gone as the colour comes back): petals of crystal fixed in the world, opening out of the ground as
     * the sword strikes; their bodies blended ({@code light} false) or their light added.
     */
    static void flowers(Fx c,EffectMesh m,float t,Vec3d centre,float yaw,boolean light){
        if(t>=WILT1)return;
        for(var b:layout(c.cast.id())){
            if(t<b.plant())continue;
            float open=(float)backOut(Ease.unit(t,b.plant(),b.plant()+.12f)),wilt=Ease.smooth(t,WILT0+.06f*(float)Ease.hash(b.seed()+70),WILT1);
            double size=FLOWER*b.flower()*open*(1-.4*wilt);float a=(1-wilt);if(a<=.01f||size<=.01)continue;
            Vec3d f=foot(centre,yaw,b).add(0,.03,0);
            int petals=7;
            for(int j=0;j<petals;j++){
                int seed=b.seed()*13+j*7;
                double az=Math.toRadians(b.spin())+Math.PI*2*(j+.3*(Ease.hash(seed)-.5))/petals,el=Math.toRadians(28+38*Ease.hash(seed+1));
                double len=(.42+.32*Ease.hash(seed+2))*size,wid=(.15+.1*Ease.hash(seed+3))*size;
                Vec3d dir=new Vec3d(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el)),side=new Vec3d(-Math.sin(az),0,Math.cos(az));
                Vec3d tip=f.add(dir.multiply(len)),mid=f.add(dir.multiply(len*.38)),l=mid.add(side.multiply(wid)),r=mid.subtract(side.multiply(wid));
                if(!light){
                    m.vertex(f,DEEP,.9f*a);m.vertex(l,0x4a6cf0,.88f*a);m.vertex(tip,LILAC,.92f*a);m.vertex(tip,LILAC,.92f*a);
                    m.vertex(f,DEEP,.9f*a);m.vertex(tip,LILAC,.92f*a);m.vertex(r,0x3a58d8,.88f*a);m.vertex(r,0x3a58d8,.88f*a);
                }else{
                    m.line(f,tip,.022*size,WHITE,.7f*a);m.line(l,tip,.012*size,PALE,.45f*a);m.line(r,tip,.012*size,PALE,.45f*a);
                }
            }
            if(light){
                m.softDisc(f.add(0,.15,0),.95*size,BLUE,.38f*a);m.softDisc(f.add(0,.2,0),.35*size,PALE,.4f*a);glint8(m,f.add(0,.3*size,0),.3*size*(.6+.4*twinkle(t,b.seed())),WHITE,.65f*a);
                // (2.98.0) sparkles rising off its petals
                for(int j=0;j<6;j++){
                    int seed=b.seed()*17+j*5+1;float born=b.plant()+.05f+.4f*(float)Ease.hash(seed);float u=Ease.unit(t,born,born+.7f);if(u<=0||u>=1)continue;
                    double az=Math.PI*2*Ease.hash(seed+1),r=.3*size+.25*u;
                    Vec3d q=f.add(Math.cos(az)*r,.2+1.3*Ease.out(u,0,1),Math.sin(az)*r);
                    glint8(m,facing(q,c.camera),.09*(1-.5*u),j%2==0?WHITE:PALE,.8f*a*(1-u));
                }
            }
        }
    }
    /** How much bigger the crystal flowers are than 2.97's (2.98.0). */
    static final double FLOWER=1.3;
    /** An ease that overshoots a little before it settles (a flower opening). */
    static double backOut(float u){double s=1.4;double x=u-1;return 1+(s+1)*x*x*x+s*x*x;}
    /**
     * The light: her sword's aura; the thread of lightning from its point to the ground; the pool's blue rim; the swords
     * showing in the sky, their glow, the lightning they trail as they fall and the flash, the shaft of light and the
     * crystal's light as each strikes; the flash and the burst in the middle; the mountains' rims; and the bursts on whom it strikes.
     */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        aura(c,m,t);
        thread(c,m,t,spot(c.cast));
        // (the edge of the ink, lit blue as it gathers and as it swirls out, is the ink shader's: ground)
        burst(m,centre,t);
        swordLight(c,m,t,centre,yaw);
        flowers(c,m,t,centre,yaw,true);
        // (2.98.0) the moonlight and starlight over all of it; (2.99.0) fireflies over its water
        FormationLights.additive(c,m);
        FormationSky.fireflies(c,m);
        // (2.93.0) Z: the sword she looks at (hers only) and her flights between them
        SwordStep.additive(c,m,t,centre,yaw);
        struck(c,m,t,false);
    }
    /**
     * The mist round the rim (2.82.8, user request: "H 끝날때 내가 보내준 사진 보면 연기가 끝부분으로 피어오르다가 사라지는
     * 거 보이지?", "산 중간 부분까지 희미하게 피오르는듯"; 2.82.10: "연기 파란색이여함 연기를 더 진하게 만들고 더 많이
     * 피어오르게"; 2.82.11: "아니 내가 사진에 표시한 애들처럼 나오게 해달라고 연기를", their marked screenshot of the game:
     * a see-through, deep blue mist standing round the formation's rim, hugging the ground in streaks that run round it,
     * rising a little before the mountains' feet. 2.82.10 drew it as opaque billows, lumps, and 2.82.9 as a faint wash of
     * added light, near white; 2.82.12: a dark navy, not to catch the eye, see-through, "더 진하고 어두운 남색", "연기는
     * 눈에 잘 안 띄이게 만들어야함 살짝 투명하고"): how strongly it stands (0 to 1: coming as the whirlpool reaches the rim, gone with the
     * smoke lying there), how high (blocks: a little as it comes, VEIL_HIGH by VEIL_UP, its wisps rising on a little as it
     * goes), and how far it has thinned away (0 to 1: whole, then breaking up by holes, its thin wisps first).
     */
    static float veilLife(float t){return Ease.smooth(t,SMOKE_IN,SMOKE_IN+.12f)*(1-Ease.smooth(t,SMOKE_OUT0-.02f,SMOKE_OUT1));}
    static double veilHeight(float t){return VEIL_HIGH*(.3+.7*Ease.smooth(t,SMOKE_IN,VEIL_UP))+.5*Ease.smooth(t,VEIL_UP,SMOKE_OUT1);}
    static float veilThin(float t){return Ease.smooth(t,SMOKE_OUT0-.1f,SMOKE_OUT1);}
    static final double VEIL_HIGH=3.6*SHRINK;
    static final float VEIL_UP=1.45f;
    /**
     * The mist off the spreading whirlpool (2.82.10, user request: "소용돌이가 퍼져서 연기가 되는거처럼 만들어"): a low ring
     * of the same mist riding on its water just outside the clean front as it runs out (FRONT_MIST_OUT blocks out, up to
     * FRONT_MIST_HIGH), growing as it nears the rim and handing over to the mist there: how strongly (0 to 1) and where.
     */
    static float frontMist(float t){return Ease.smooth(t,SPREAD0+.03f,SPREAD0+.2f)*(1-Ease.smooth(t,SPREAD1-.08f,SPREAD1+.08f));}
    static double frontMistAt(float t){return front(t)+FRONT_MIST_OUT;}
    static double frontMistHeight(float t){return FRONT_MIST_HIGH*(.45+.55*Ease.smooth(t,SPREAD0,SPREAD1));}
    static final double FRONT_MIST_OUT=1.2,FRONT_MIST_HIGH=1.1;
    /** How finely a ring of mist is drawn round, and how far it leans out (blocks a block up). */
    static final int MIST_SEGS=120;
    static final double MIST_LEAN=.25;
    /**
     * How much of the mist's light is added again (0: none, blended only) and how strongly the spreading whirlpool's front
     * lights what is round it (2.82.12, user request: "더 진하고 어두운 남색", "연기는 눈에 잘 안 띄이게 만들어야함 살짝
     * 투명하고": the smoke a dark navy, see-through, giving no light; 2.82.11 added a third of its light again and lit
     * the rim with six blue lights, the front .35).
     */
    static final float MIST_GLOW=0,FRONT_LIGHT=.12f;
    /** How open the rim is to the mist at the angle {@code az} (0 straight behind her, where her camera looks in from, to 1 past MOUNTAIN_GAP, over 30 degrees). */
    static double veilOpen(double az,double back){
        double off=Math.abs(Math.atan2(Math.sin(az-back),Math.cos(az-back)));
        return Ease.smooth((float)off,(float)(MOUNTAIN_GAP-Math.toRadians(15)),(float)(MOUNTAIN_GAP+Math.toRadians(15)));
    }
    /** Gone close to the camera (2 blocks), whole from 5.5 blocks off (mist close by would fill the screen). */
    static double camera(Vec3d p,Vec3d cam){return MathHelper.clamp((p.distanceTo(cam)-2)/3.5,0,1);}
    /**
     * The blue mist (the shader pass): a ring of upright mist fixed in the world (the ink shader's mode 17,
     * KagenoFire.mist: streaks running round the way the whirlpool turned and rising, dense at its foot, wisps at its
     * uneven top, thinning away by holes; a dark navy, see-through, 2.82.12), blended, no light added (MIST_GLOW).
     * Off the spreading whirlpool, low, riding just outside its clean front as it runs out; then round the rim as the
     * whirlpool reaches it, standing to VEIL_HIGH. Low and thin behind her, where her camera looks in from (veilOpen);
     * gone close to the camera.
     */
    static void shaders(Fx c){
        stream(c);
        float t=c.sec;if(t<SPREAD0||t>=SMOKE_OUT1+.02f)return;
        Vec3d mid=poolCentre(c.cast);double back=behind(yaw(c.cast));int seed=c.cast.id().hashCode();
        float fm=frontMist(t);
        if(fm>.005f)mist(c,mid,frontMistAt(t),frontMistHeight(t),.55f*fm,0,seed+11,back);
        float rm=veilLife(t);
        if(rm>.005f)mist(c,mid,rim(t),veilHeight(t),.92f*rm,veilThin(t),seed+29,back);
    }
    /**
     * The stream of water under the mountains (2.82.16, user request with their marked screenshot of the game: "산 아래에
     * 물 줄기 비슷한게 흐르거든? 그거만 만들면 돼 사진까지 보내줄게 이거보고 그대로 물줄기가 흐르게 만들면 돼 산 아래", "산이
     * 떠있는 높이만큼 물줄기 높이를 잡아주면 돼 ... 물줄기 높이는 0.6칸으로"): a ring of running water standing on the ground
     * just before the mountains' feet, STREAM_HIGH blocks high, under them as they float (MOUNTAIN_FLOAT), its streaks
     * running round (the ink shader's mode 17, its stream, KagenoFire.stream; a little of its light added again). It comes
     * in as the mountains rise and goes as they sink; it thins where it stands before her camera, as the mountains do
     * (occlusion), and close to the camera.
     */
    static void stream(Fx c){
        float t=c.sec,end=end(c.cast);
        float up=Ease.smooth(t,RISE0+.2f,RISE1+.6f)*(1-Ease.smooth(t,end+GLOW,end+GONE+.2f));
        if(up<=.005f)return;
        Vec3d mid=centre(c.cast);double camAz=cameraAz(mid,c.camera);
        int n=STREAM_SEGS;double[] rr=new double[n+1],hh=new double[n+1];float[] aa=new float[n+1];
        for(int i=0;i<=n;i++){
            double az=Math.PI*2*i/n;rr[i]=STREAM_R;hh[i]=STREAM_HIGH;
            // (where it runs between her camera and the formation, out near the camera, it is gone: STREAM_OPEN)
            float before=Double.isNaN(camAz)?1:Ease.smooth((float)Math.abs(Math.IEEEremainder(az-camAz,Math.PI*2)),(float)STREAM_OPEN0,(float)STREAM_OPEN1);
            Vec3d p=mid.add(Math.cos(az)*STREAM_R,STREAM_HIGH/2,Math.sin(az)*STREAM_R);
            aa[i]=(float)(up*before*camera(p,c.camera));
        }
        KagenoFire.stream(c.matrix,c.camera,mid,rr,hh,aa,STREAM_HIGH,c.cast.id().hashCode()+53,c.age,STREAM_GLOW,0);
    }
    /** The stream: how far out (just before the mountains' feet), how high (blocks), how finely drawn round, how much of its light is added again. */
    /**
     * (STREAM_HIGH: up to the mountains' frayed feet, "물줄기 높이가 너무 낮잖아": .6 at first, under them with a gap; going
     * up with them as they float higher, 2.82.17, the user's own rule: "산이 떠있는 높이만큼 물줄기 높이를 잡아주면 돼". Its
     * streaks stay as thin as they were and spread over its height: the ink shader is told how high it is.)
     */
    static final double STREAM_R=MOUNTAIN_R-.35*SHRINK,STREAM_HIGH=MOUNTAIN_FLOAT+.45*SHRINK,STREAM_OPEN0=Math.toRadians(35),STREAM_OPEN1=Math.toRadians(65);
    static final int STREAM_SEGS=160;
    static final float STREAM_GLOW=0;
    /** A ring of the mist round {@code mid}: {@code r} blocks out, {@code high} blocks up (low and thin behind her), of strength {@code strength}, thinned away {@code thin}. */
    static void mist(Fx c,Vec3d mid,double r,double high,float strength,float thin,int seed,double back){
        int n=MIST_SEGS;double[] rr=new double[n+1],hh=new double[n+1];float[] aa=new float[n+1];
        for(int i=0;i<=n;i++){
            double az=Math.PI*2*i/n,open=veilOpen(az,back);
            rr[i]=r;hh[i]=high*(.35+.65*open);
            Vec3d p=mid.add(Math.cos(az)*r,hh[i]*.35,Math.sin(az)*r);
            aa[i]=(float)(strength*(.45+.55*open)*camera(p,c.camera));
        }
        KagenoFire.mist(c.matrix,c.camera,mid,rr,hh,aa,MIST_LEAN,thin,seed,c.age,MIST_GLOW,0);
    }
    /** Over the ink: the starlight now and then on a sword as the formation stands. */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec;if(t<SILVER1||t>=end(c.cast))return;
        Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        for(var b:layout(c.cast.id())){
            double period=3+2*Ease.hash(b.seed()+80),phase=period*Ease.hash(b.seed()+81),u=((t+phase)%period)/period;
            if(u>.08)continue;float a=(float)Math.sin(Math.PI*u/.08);
            Vec3d top=planted(centre,yaw,b),q=at(top,frame(down(b),spinAt(b,t)),p(0,S_GUARD+.4*Ease.hash(b.seed()+82),0));
            glint8(m,q,.3,WHITE,.85f*a);glint8(m,q,.55,PALE,.35f*a);
        }
    }
    /** Her own sword's blue aura as she leaps and calls the swords. */
    static void aura(Fx c,EffectMesh m,float t){
        float g=Ease.window(t,.1f,.9f,.06f,.15f);if(g<=.01f)return;
        var hb=blade(c,t);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));
        m.softLine(guard,tip,.55,AURA[1],.16f*g);m.softLine(guard,tip,.22,AURA[1],.5f*g);
        m.line(guard,tip,.07,AURA[2],.9f*g);m.line(guard,tip,.03,AURA[3],.85f*g);
        m.softDisc(tip,.45,AURA[3],.5f*g);
        // (2.98.0) a spark running up it, again and again
        double run=(t*2.4)%1;Vec3d q=guard.lerp(tip,run);glint8(m,facing(q,c.camera),.16*(float)Math.sin(Math.PI*run),WHITE,.8f*g);
    }
    /** The thread of lightning from her sword's point down to the ground as she leaps (the video: a white-blue bolt, flickering). */
    static void thread(Fx c,EffectMesh m,float t,Vec3d spot){
        float a=Ease.smooth(t,THREAD0-.02f,THREAD0+.02f)*(1-Ease.smooth(t,THREAD1-.08f,THREAD1));if(a<=.01f)return;
        var hb=blade(c,t);Vec3d tip=hb[0].add(hb[1].multiply(TIP)),ground=new Vec3d(tip.x,spot.y+.05,tip.z);
        if(tip.y-ground.y<.2)return;
        int seed=c.cast.id().hashCode()+7;
        bolt(m,tip,ground,seed,t,.38,.075,WHITE,AZURE,a);
        // (2.98.0) two forks off it, leaping to the ground round her, each flickering on and off
        for(int k=0;k<2;k++){
            int q=(int)Math.floor(t*15)+k*7;if(Ease.hash(seed+q*29)<.3)continue;
            double u=.35+.35*Ease.hash(seed+q*31+1),az=Math.PI*2*Ease.hash(seed+q*37+2),r=.8+1.1*Ease.hash(seed+q*41+3);
            Vec3d from=tip.lerp(ground,u),to=ground.add(Math.cos(az)*r,0,Math.sin(az)*r);
            bolt(m,from,to,seed+q*53+k,t,.22,.04,PALE,AZURE,.75f*a);
            m.softDisc(to.add(0,.1,0),.4,BLUE,.35f*a);
        }
        m.softDisc(ground.add(0,.1,0),1.3,BLUE,.5f*a);m.softDisc(ground.add(0,.1,0),.4,PALE,.55f*a);m.softDisc(tip,.45,PALE,.6f*a);
        glint8(m,facing(ground.add(0,.15,0),c.camera),.6+.15*Math.sin(t*50),WHITE,.6f*a);
    }
    /**
     * A bolt of lightning from {@code a} to {@code b}: jagged, its kinks drawn anew some thirty times a second (a function of
     * the clock), a white core in a blue glow; light, so turned to the camera.
     */
    static void bolt(EffectMesh m,Vec3d a,Vec3d b,int seed,float t,double jag,double width,int core,int glow,float alpha){
        Vec3d d=b.subtract(a);double len=d.length();if(len<1e-3||alpha<=.005f)return;
        Vec3d s1=d.crossProduct(UP);if(s1.lengthSquared()<1e-6)s1=new Vec3d(1,0,0);s1=s1.normalize();Vec3d s2=d.crossProduct(s1).normalize();
        int n=Math.max(4,Math.min(16,(int)(len*1.6))),q=(int)Math.floor(t*30);
        Vec3d prev=a;
        for(int i=1;i<=n;i++){
            double u=i/(double)n,env=Math.sin(Math.PI*u);
            Vec3d next=i==n?b:a.add(d.multiply(u)).add(s1.multiply((Ease.hash(seed+q*131+i*7)-.5)*jag*2*env)).add(s2.multiply((Ease.hash(seed+q*131+i*7+3)-.5)*jag*2*env));
            m.line(prev,next,width,core,alpha);m.softLine(prev,next,width*7,glow,.35f*alpha);
            prev=next;
        }
    }
    /**
     * The flash before the spike and the burst as it breaks, in the formation's middle where the ink gathered (the video: a
     * cone of white-blue light as the faint pool goes, the black point, then a crown of blue light and a ring racing out).
     */
    static void burst(EffectMesh m,Vec3d centre,float t){
        float at=IsosangRules.H_PLANT,k=FxLib.kick(t,at+.02f,.1f),pre=FxLib.kick(t,at-.055f,.02f)*(1-Ease.smooth(t,at-.035f,at-.02f));
        if(pre>.01f){
            Vec3d top=centre.add(0,2.4,0);
            for(int s=0;s<2;s++){Vec3d side=s==0?new Vec3d(.6,0,0):new Vec3d(0,0,.6);m.triangle(centre.subtract(side).add(0,.03,0),centre.add(side).add(0,.03,0),top,AZURE,.5f*pre);m.triangle(centre.subtract(side.multiply(.35)).add(0,.03,0),centre.add(side.multiply(.35)).add(0,.03,0),top,WHITE,.65f*pre);}
            m.softDisc(centre.add(0,.3,0),1.6,BLUE,.4f*pre);
        }
        if(k<=.01f&&t>at+.4f)return;
        if(k>.01f){
            double h=2.2*Ease.out(t,at,at+.06f);Vec3d top=centre.add(0,h,0);
            for(int s=0;s<2;s++){Vec3d side=s==0?new Vec3d(.75,0,0):new Vec3d(0,0,.75);m.triangle(centre.subtract(side).add(0,.03,0),centre.add(side).add(0,.03,0),top,AZURE,.55f*k);m.triangle(centre.subtract(side.multiply(.4)).add(0,.03,0),centre.add(side.multiply(.4)).add(0,.03,0),top,WHITE,.6f*k);}
            for(int j=0;j<12;j++){
                double az=Math.PI*2*(j+.4*Ease.hash(j*7+1))/12,el=Math.toRadians(48+24*Ease.hash(j*7+2)),len=(1.1+.9*Ease.hash(j*7+3))*Ease.out(t,at,at+.08f);
                Vec3d dir=new Vec3d(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el)),side=new Vec3d(-Math.sin(az),0,Math.cos(az));
                m.needle(centre.add(0,.03,0),centre.add(dir.multiply(len)),side,.22,.35,PALE,.8f*k);
            }
            m.softDisc(centre.add(0,.6,0),4.2,BLUE,.42f*k);m.softDisc(centre.add(0,.5,0),1.6,AZURE,.4f*k);m.softDisc(centre.add(0,.4,0),.7,WHITE,.6f*k);
        }
        // (2.82.6: no ring of its own racing out over the ground: the ink's ripples run out from here)
    }
    /**
     * The swords' light: a flash as each shows in the sky; their glow; the lightning each trails as it falls; the flash,
     * the shaft of light (every fifth one) and the ring at its foot as it strikes.
     */
    static void swordLight(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        float e=end(c.cast),flare=taken(c.cast)?0:Ease.smooth(t,e,e+.04f)*(1-Ease.smooth(t,e+GLOW,e+INKED)),ink=taken(c.cast)?0:Ease.smooth(t,e+GLOW,e+INKED);
        for(var b:layout(c.cast.id())){
            float a=shown(c.cast,b,t);if(a<=.005f)continue;
            Vec3d top=planted(centre,yaw,b).add(0,above(b,t),0),foot=foot(centre,yaw,b);var f=frame(down(b),spinAt(b,t));
            // shows in the sky
            float show=FxLib.kick(t,b.shows()+.02f,.1f);
            if(show>.01f){Vec3d mid=at(top,f,p(0,S_LEN*.45,0));m.star(mid,1.1,PALE,.6f*show);m.star(mid,.45,WHITE,.75f*show);m.softDisc(mid,1.4,AZURE,.32f*show);}
            swordGlow(m,top,f,a,dark(b,t),sky(b,t),flare,ink);
            // the lightning it trails as it falls, from the sky down to its point, gone a moment after it strikes
            float from=b.plant()-FALL,trail=Ease.smooth(t,from,from+.03f)*(1-Ease.smooth(t,b.plant()+.06f,b.plant()+.2f));
            if(trail>.01f){
                Vec3d point=at(top,f,p(0,S_LEN,0)),high=foot.add(0,b.sky()+3,0);
                if(t>=b.plant())point=foot;
                bolt(m,high,point,b.seed(),t,.3,.055,PALE,BLUE,.85f*trail);
                // (2.98.0) and a streak of light down its way
                m.softLine(high,point,.9,BLUE,.22f*trail);m.softLine(high,point,.3,AZURE,.4f*trail);
            }
            // as it strikes: a flash and a ring at its foot, a shaft of light from every third one (2.98.0: every fifth before,
            // all of it smaller: the flash 1.1 blocks, the star 0.8, the ring 0.9)
            float k=FxLib.kick(t,b.plant(),.1f);
            if(k>.01f){
                m.softDisc(foot.add(0,.3,0),2.1,BLUE,.45f*k);m.star(foot.add(0,.35,0),1.5,PALE,.6f*k);m.star(foot.add(0,.35,0),.55,WHITE,.75f*k);
                float u=Ease.unit(t,b.plant(),b.plant()+.25f);if(u<1)groundBand(m,foot.add(0,.06,0),.2+1.9*Ease.out(t,b.plant(),b.plant()+.25f),.1,PALE,.5f*(1-u),b.seed(),t);
            }
            // (2.98.0) sparks flying up and out of its foot as it strikes
            double sk=t-b.plant();
            if(sk>=0&&sk<.4)for(int j=0;j<8;j++){
                int seed=b.seed()*23+j*13;double az=Math.PI*2*Ease.hash(seed),out=2+3.5*Ease.hash(seed+1),up=3+4*Ease.hash(seed+2);
                Vec3d q=FormationLights.spark(foot,az,out,up,sk),q0=FormationLights.spark(foot,az,out,up,Math.max(0,sk-.03));
                m.needle(q0,q,.045,.4,j%3==0?WHITE:j%3==1?PALE:AZURE,.85f*(1-Ease.smooth((float)sk,.2f,.4f)));
            }
            if(Math.floorMod(b.seed(),3)==0){
                float s=Ease.smooth(t,b.plant(),b.plant()+.02f)*(1-Ease.smooth(t,b.plant()+.12f,b.plant()+.5f));
                if(s>.01f){Vec3d up=foot.add(0,18,0);m.line(foot,up,.07,WHITE,.6f*s);m.softLine(foot,up,.9,CYAN,.25f*s);m.softLine(foot,foot.add(0,10,0),2,BLUE,.14f*s);}
            }
            // standing: now and then a gleam runs down its edge from its guard to its point (2.82.1)
            if(t>SILVER1&&flare<=.01f&&ink<=.01f&&a>.5f){
                double period=3.6+2.6*Ease.hash(b.seed()+84),u=((t+period*Ease.hash(b.seed()+85))%period)/GLEAM;
                if(u<1){
                    double y=S_GUARD+.05+(S_LEN-S_GUARD-.12)*Ease.smooth((float)u,0,1);float g=(float)Math.sin(Math.PI*u)*a;
                    Vec3d g0=at(top,f,edge(y-.18)),g1=at(top,f,edge(y+.18)),g2=at(top,f,edge(y));
                    m.softLine(g0,g1,.15*THIN,PALE,.6f*g);m.line(g0,g1,.022*THIN,WHITE,.85f*g);m.softDisc(g2,.18*THIN,WHITE,.4f*g);
                    // (2.98.0) a star at its point as the gleam gets there
                    if(u>.8)glint8(m,facing(at(top,f,p(0,S_LEN,0)),c.camera),.28*Math.sin(Math.PI*(u-.8)/.2),WHITE,.8f*a);
                }
            }
            // as the formation ends: a four-pointed star of light lies on the ground at its foot, a pillar of light up it (the video)
            if(flare>.01f){
                Vec3d g=foot.add(0,.05,0),x=Vec3d.fromPolar(0,(float)spinAt(b,t)),z=new Vec3d(-x.z,0,x.x);
                groundStar(m,g,x,z,2.4,1.7,flare);
                m.softTaper(foot,top.add(0,8,0),1.6,.4,CYAN,.28f*flare);m.line(foot,top.add(0,6,0),.04,WHITE,.5f*flare);
                // (a star at its guard: pale with a small white heart, twenty-eight of them whiting out the picture else)
                Vec3d gd=facing(at(top,f,p(0,S_GUARD,0)),c.camera);glint8(m,gd,.55,PALE,.55f*flare);glint8(m,gd,.22,WHITE,.65f*flare);
            }
        }
    }
    /** How long a gleam takes to run down a standing sword's edge (seconds). */
    static final float GLEAM=.6f;
    /**
     * A four-pointed star of light lying flat on the ground at {@code g}: its long points {@code big} out along {@code x}, its
     * short ones {@code small} out along {@code z}, sharp, white along their middles, a soft pool of light under it.
     */
    static void groundStar(EffectMesh m,Vec3d g,Vec3d x,Vec3d z,double big,double small,float alpha){
        m.needle(g.subtract(x.multiply(big)),g.add(x.multiply(big)),z,.42,.5,CYAN,.6f*alpha);
        m.needle(g.subtract(z.multiply(small)),g.add(z.multiply(small)),x,.36,.5,CYAN,.6f*alpha);
        m.needle(g.subtract(x.multiply(big*.8)),g.add(x.multiply(big*.8)),z,.12,.5,WHITE,.8f*alpha);
        m.needle(g.subtract(z.multiply(small*.8)),g.add(z.multiply(small*.8)),x,.1,.5,WHITE,.8f*alpha);
        groundDisc(m,g.add(0,.01,0),big*.55,.15,CYAN,.35f*alpha);
    }
    /**
     * Bursts on whom the formation struck, as the server told (IsosangClient.formationStruck): blue spikes round a pale heart
     * with a crescent moon, as the sword's hits' ({@code ink} for their ink, in the blended pass).
     */
    static void struck(Fx c,EffectMesh m,float t,boolean ink){
        var world=c.mc.world;if(world==null)return;Vec3d centre=centre(c.cast);
        for(var e:IsosangClient.formationStruck(c.cast.id()).entrySet()){
            float at=e.getKey()/20f;if(t<at-.01f||t>=at+.35f)continue;
            int k=0;
            for(int id:e.getValue()){
                if(!(world.getEntityById(id) instanceof LivingEntity le))continue;
                Vec3d p=chest(c,le),way=p.subtract(centre).multiply(1,0,1);way=way.lengthSquared()<1e-4?c.f:way.normalize();
                double size=e.getKey()==IsosangRules.FORMATION_IMPACT_AT?1.35:.95;
                if(ink)IsosangEmpower.burstInk(c,m,p,at,size,id*13+k);else IsosangEmpower.burst(c,m,p,way,at,size,id*13+k);
                k++;
            }
        }
    }

    // ───────────────────────────── the effect layer, the camera ─────────────────────────────

    static void frame(Fx c,FxFrame f){
        float t=c.sec;Vec3d centre=centre(c.cast);float yaw=yaw(c.cast);
        float th=Ease.smooth(t,THREAD0-.02f,THREAD0+.02f)*(1-Ease.smooth(t,THREAD1-.08f,THREAD1));
        if(th>.01f)f.light(spot(c.cast).add(0,1.5,0),0x9fc8ff,8,.9f*th);
        float pool=Ease.smooth(t,GATHER0,GATHER0+.1f)*(1-Ease.smooth(t,COLLAPSE0,COLLAPSE));
        if(pool>.01f)f.light(poolCentre(c.cast).add(0,.6,0),0x5a8cff,11,.8f*pool);
        // the whirlpool's crescents light the ground round it as far as it reaches, faintly (2.82.8: they are a pale,
        // see-through blue), gone as it turns into the smoke
        float whirl=Ease.smooth(t,SWIRL0,SWIRL0+.06f)*whirlShown(t)*(1-Ease.smooth(t,SPREAD0,SPREAD0+.1f));
        if(whirl>.01f)f.light(poolCentre(c.cast).add(0,.6,0),0x6a9cff,Math.max(6,whirl(t)),.3f*whirl);
        // the spreading whirlpool's front glows a little blue on the ground and the swords as it runs out (2.82.10; 2.82.12
        // fainter: the water it pushes out is a dark blue now). The smoke at the rim gives no light (2.82.12, user request:
        // "연기는 눈에 잘 안 띄이게": a dark navy; 2.82.8-2.82.11 lit the ground and the mountains' feet round it).
        float run=Ease.smooth(t,SPREAD0,SPREAD0+.04f)*(1-Ease.smooth(t,SPREAD1-.04f,SPREAD1+.08f));
        double fr=front(t);
        if(run>.01f&&fr>1){Vec3d m0=poolCentre(c.cast);for(int k=0;k<4;k++){double a=Math.PI*2*k/4+.8;f.light(m0.add(Math.cos(a)*fr,.4,Math.sin(a)*fr),0x4f7dff,4,FRONT_LIGHT*run);}}
        float mid=Math.max(FxLib.kick(t,IsosangRules.H_PLANT+.02f,.16f),.7f*FxLib.kick(t,IsosangRules.H_PLANT-.055f,.02f));if(mid>.01f)f.light(centre.add(0,1,0),0xb8d8ff,12,1.4f*mid);
        for(var b:layout(c.cast.id())){float k=FxLib.kick(t,b.plant(),.14f);if(k>.05f)f.light(foot(centre,yaw,b).add(0,.6,0),0x9fc8ff,9,1.0f*k);}
        float e=end(c.cast),flare=taken(c.cast)?0:Ease.smooth(t,e,e+.04f)*(1-Ease.smooth(t,e+GLOW,e+INKED+.05f));
        if(flare>.01f)f.light(centre.add(0,1,0),0x7fe6ff,20,1.7f*flare);
        // (2.98.0) as she springs, the pillar in the middle, each five-second beat, the ring as it ends
        FormationLights.frame(c,f);
        float stand=Ease.smooth(t,SILVER0,SILVER1)*(1-Ease.smooth(t,e,e+GONE));
        if(stand>.01f)f.light(centre.add(0,2.2,0),0x8fb4ff,12,.25f*stand);
        // fast as she leaps and the swords rain: the afterglow cut short, else they leave copies of themselves on the way;
        // all but none while the ink's lit edge sweeps in and the whirlpool swirls and spreads out (2.82.6: its copies
        // showed as rings beside it; 2.82.10: its front running out)
        f.afterglow(t>=GATHER0-.02f&&t<SPREAD1+.08f?.01f:t>=.14f&&t<1.6f||t>=e-.05f&&t<e+GONE+.1f||t>=IsosangRules.H_DROP&&t<IsosangRules.H_DOWN+.1f?.08f:.3f);
    }
    /** Shake: the burst in the middle, the rain of swords crashing in, and a little as the formation ends (each over two frames). */
    static float shake(StandClient.Cast cast,float sec){
        float e=end(cast);
        return Math.max(Math.max(.7f*bump(sec,IsosangRules.H_PLANT,.1f),.95f*bump(sec,(float)IsosangRules.FORMATION_IMPACT_AT/20,.16f)),taken(cast)?0:.45f*bump(sec,e+.02f,.1f));
    }
    /** How far her own camera draws back for it (blocks): out as she leaps, further as the swords rain to show them all, held a little back while it stands, in again as she drops. */
    static final double PULL_LEAP=2.2,PULL_RAIN=5,PULL_STAND=3.2;
    static double pull(float sec){
        double out=PULL_LEAP*Ease.smooth(sec,.1f,.45f)+(PULL_RAIN-PULL_LEAP)*Ease.smooth(sec,.9f,1.35f)-(PULL_RAIN-PULL_STAND)*Ease.smooth(sec,1.6f,2.6f);
        return out*(1-Ease.smooth(sec,IsosangRules.H_DROP,IsosangRules.H_DOWN+.3f));
    }
}
