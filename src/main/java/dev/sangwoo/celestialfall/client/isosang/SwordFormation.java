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
    static final double POOL_WIDE=14.5,GATHER_MID=3.4,POOL_IN=2.4;
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
     * navy smoke at the rim.
     */
    static final float WHIRL1=.86f,EYE0=.73f,EYE1=.85f;
    static final double WHIRL_R=POOL_WIDE,EYE_R=5,WHIRL_TWIST=5.2,WHIRL_SPIN=.38;
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
     * (RIM_R, outside every sword and before the mountains' feet) it turns into a band of blue smoke on the ground there
     * (SMOKE_IN to SMOKE_FULL), its wisps streaming round the way the whirlpool turned, a see-through blue mist standing
     * round it (veilLife, shaders: 2.82.11, the user's marked screenshot); it drifts a little further out (to RIM_OUT) and thins away by patches,
     * its thin wisps first (SMOKE_OUT0 to SMOKE_OUT1). Both a dark navy, see-through, giving no light (2.82.12, user
     * request: "더 진하고 어두운 남색", "연기는 눈에 잘 안 띄이게 만들어야함 살짝 투명하고"). The ink shader draws the band
     * in the whirlpool's square (style 15), reading these off the formation's clock.
     */
    static final float SMOKE_IN=1.22f,SMOKE_FULL=1.36f,SMOKE_OUT0=1.64f,SMOKE_OUT1=1.88f;
    static final double RIM_R=11.2,RIM_OUT=12.2;
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
    static Vec3d body(Fx c){return c.pos.add(0,IsosangClient.lift(c.cast.caster(),c.delta),0);}
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
     * the rules' (IsosangRules.formationSwords: rings round an empty middle, 2.82.2 to 2.82.4, as the server reckons the
     * sword she stands on); the one she stands on (IsosangRules.standSword, 2.82.4: "H를 쓰면 내가 점프를 하고 그 자리에
     * 검이 하나 있어야해") strikes first, right under her as she hangs in the air, and stands upright, its pommel as high as
     * she stands (its crystal flower as the others', 2.82.5: the spike is out of the middle again); the rest stand
     * upright as it does (2.82.13), a little higher or lower, and rain down after it.
     */
    static Blade[] layout(UUID id){
        var l=LAYOUTS.get(id);if(l!=null)return l;
        int s0=IsosangRules.formationSeed(id);
        var places=IsosangRules.formationSwords(s0);int stand=IsosangRules.standSword();
        List<Blade> out=new ArrayList<>();
        for(int k=0,r=0,inRing=0;k<places.length;k++,inRing++){
            if(inRing==IsosangRules.FORMATION_RING_N[r]){r++;inRing=0;}
            int seed=IsosangRules.swordSeed(s0,k);
            if(k==stand){
                out.add(new Blade(places[k][0],places[k][1],IsosangRules.FORMATION_STAND,0,0,180*Ease.hash(seed+7),IsosangRules.H_PLANT,10,IsosangRules.H_SKY,.8+.45*Ease.hash(seed+11),seed));
                continue;
            }
            // (2.82.13, user request: "검들이 다 삐 뚫어져있냐 영상은 전부 제대로 박혀있는데": every sword struck straight
            // down, upright; 2.82.1-2.82.12 leaned them 2 to 13 degrees)
            double height=1.25+.5*Ease.hash(seed+4),tilt=0,toward=0,spin=180*Ease.hash(seed+7);
            float plant=IsosangRules.H_RAIN0+(IsosangRules.H_RAIN1-IsosangRules.H_RAIN0)*(float)Math.min(1,.12*r+.82*Ease.hash(seed+8));
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
    static float shown(StandClient.Cast c,Blade b,float t){return Ease.smooth(t,b.shows(),b.shows()+.08f)*(1-Ease.smooth(t,end(c)+INKED+.02f,end(c)+GONE));}

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
     * A mass of an ink mountain (2.82.2, user request: "구름과 산이 너무 이상하게 생겼어 다시 예쁘게 꾸며주고", the video
     * looked at again: each mountain a broad mass rising to a round summit, its flanks steep under the summit and spreading
     * out toward its feet with a shoulder or two on them, near black, a light blue rim along its upper outline thick under
     * the summit and thinning down the flanks, ridges running down its face from the summit and the shoulders in strokes of
     * the same light; 2.82.1's narrow stepped pinnacles looked strange): its outline from its left foot up over its summit
     * and down to its right foot (x along its mountain from the mountain's middle, y up, blocks; x only grows along it), the
     * summit's index in it, how far before its mountain's face it stands (toward the formation's middle, blocks), how long
     * after its mountain it rises (seconds), how far down each flank its rim runs (0 to 1 of the flank), its ridges (each a
     * line of points from its top down), the flank each falls on (-1 left, 1 right), how broad each is (of a rim's), its seed.
     */
    record Peak(double[] xs,double[] ys,int top,double front,float delay,double rimL,double rimR,double[][][] ridges,int[] ridgeSide,double[] ridgeWidth,int seed){
        double height(){return ys[top];}
        /** The outline's height {@code x} along the mountain (0 off it). */
        double at(double x){return outlineAt(xs,ys,x);}
    }
    /** An ink mountain round the formation: which way from its middle (radians), how far, its masses from the back one to the front one, how long after the first it rises, its seed. */
    record Mountain(double az,double radius,Peak[] peaks,float delay,int seed){}
    /** The height of an outline {@code x} along it (0 off it). */
    static double outlineAt(double[] xs,double[] ys,double x){
        if(x<=xs[0]||x>=xs[xs.length-1])return 0;
        int i=1;while(i<xs.length-1&&xs[i]<x)i++;
        double f=(x-xs[i-1])/Math.max(1e-9,xs[i]-xs[i-1]);return ys[i-1]+(ys[i]-ys[i-1])*f;
    }
    private static final Map<UUID,Mountain[]> MOUNTAINS=new LinkedHashMap<>();
    /**
     * How far round from right behind her (seen from the formation's middle, radians) no mountain stands and no cloud
     * hangs (2.82.4: she stands on the formation's outer ring and her camera looks at her from behind, from out over the
     * mountains' ring; a mountain or a cloud there would stand between them). The clouds keep further off: they drift.
     */
    static final double MOUNTAIN_GAP=Math.toRadians(50),CLOUD_GAP=Math.toRadians(70);
    /** The way from the formation's middle to where she stands (radians in the world's x, z), as it faced {@code yaw}. */
    static double behind(float yaw){Vec3d b=Vec3d.fromPolar(0,yaw).negate();return Math.atan2(b.z,b.x);}
    /**
     * The mountains of the formation of swing {@code id} (the same on every client), its user having faced {@code yaw}:
     * six round it, a little apart, each a broad mass with a lower one beside it a little before it and one or two
     * foothills before them; none behind her (2.82.4, MOUNTAIN_GAP).
     */
    static Mountain[] mountains(UUID id,float yaw){
        var l=MOUNTAINS.get(id);if(l!=null)return l;
        int s0=(int)(id.getLeastSignificantBits()*31+id.getMostSignificantBits());
        int n=6;l=new Mountain[n];double off=behind(yaw)+MOUNTAIN_GAP,span=Math.PI*2-2*MOUNTAIN_GAP;
        for(int i=0;i<n;i++){
            int seed=s0+211*(i+1);
            double az=off+span*(i+.5+.3*(Ease.hash(seed+1)-.5))/n,radius=IsosangRules.FORMATION_RADIUS+2.6+1.6*Ease.hash(seed+2);
            double tall=4.4+2.0*Ease.hash(seed+4),side=Ease.hash(seed+6)<.5?-1:1;int count=3+(Ease.hash(seed+5)<.5?1:0);
            // from the back to the front: where along the mountain and how tall (of the tall one's height), how broad (half its foot, of its own height), how far before the face, how late it rises
            double[][] plan={{(Ease.hash(seed+7)-.5)*.4,1,.7+.25*Ease.hash(seed+8),0,0},
                {side*(.55+.2*Ease.hash(seed+9)),.55+.2*Ease.hash(seed+10),.75+.25*Ease.hash(seed+11),.14,.06},
                {-side*(.3+.25*Ease.hash(seed+12)),.28+.12*Ease.hash(seed+13),.95+.35*Ease.hash(seed+14),.32,.1},
                {side*(.9+.2*Ease.hash(seed+15)),.2+.1*Ease.hash(seed+16),.95+.35*Ease.hash(seed+17),.46,.13}};
            Peak[] peaks=new Peak[count];
            for(int j=0;j<count;j++){
                double h=tall*plan[j][1],half=h*plan[j][2];
                peaks[j]=massOf(plan[j][0]*tall,h,half*(.88+.24*Ease.hash(seed+30+j)),half*(.88+.24*Ease.hash(seed+40+j)),plan[j][3],(float)plan[j][4],seed+1000*(j+1));
            }
            l[i]=new Mountain(az,radius,peaks,.14f*(float)Ease.hash(seed+90),seed);
        }
        MOUNTAINS.put(id,l);while(MOUNTAINS.size()>16)MOUNTAINS.remove(MOUNTAINS.keySet().iterator().next());
        return l;
    }
    /** How many points down each flank of a mass. */
    static final int FLANK=24;
    /** A flank of a mass, from its summit down to its foot: its points, where its shoulders are (of the way down it), how concave it is. */
    record Flank(double[][] pts,double[] shoulders,double bend){}
    /**
     * A flank ({@code dir} -1 to the left, 1 to the right): round under the summit, steep there and spreading out toward
     * its foot (concave), with one or two shoulders swelling out of it, a little uneven.
     */
    static Flank flank(double x0,double h,double half,int dir,int seed){
        double bend=1.3+.45*Ease.hash(seed),c=.07,norm=Math.sqrt(1+c*c)-c;
        int humps=Ease.hash(seed+1)<.55?1:2;double[] hu=new double[humps],hw=new double[humps],hh=new double[humps];
        for(int k=0;k<humps;k++){
            hu[k]=humps==1?.38+.25*Ease.hash(seed+2):k==0?.28+.14*Ease.hash(seed+2):.56+.16*Ease.hash(seed+3);
            hw[k]=.07+.06*Ease.hash(seed+4+k);hh[k]=.05+.07*Ease.hash(seed+6+k);
        }
        double[][] pts=new double[FLANK+1][];
        for(int i=0;i<=FLANK;i++){
            double u=i/(double)FLANK,ue=(Math.sqrt(u*u+c*c)-c)/norm,y=h*Math.pow(1-ue,bend);
            for(int k=0;k<humps;k++){double z=(u-hu[k])/hw[k];if(Math.abs(z)<1){double cz=Math.cos(z*Math.PI/2);y+=h*hh[k]*cz*cz;}}
            y+=h*.016*(.6*Math.sin(u*19+seed)+.4*Math.sin(u*41+seed*1.7))*4*u*(1-u);
            pts[i]=new double[]{x0+dir*half*u,i==0?h:i==FLANK?0:MathHelper.clamp(y,0,h*.985)};
        }
        double[] tops=new double[humps];for(int k=0;k<humps;k++)tops[k]=hu[k];
        return new Flank(pts,tops,bend);
    }
    /**
     * A mass: its summit {@code x} along its mountain and {@code h} up, its foot {@code halfL} out to the left and {@code halfR}
     * to the right; its ridges: one from the summit down each flank into its face, steeper than the flank, and one down
     * from each shoulder, all kept inside its outline.
     */
    static Peak massOf(double x,double h,double halfL,double halfR,double front,float delay,int seed){
        Flank left=flank(x,h,halfL,-1,seed+10),right=flank(x,h,halfR,1,seed+50);
        int n=left.pts().length+right.pts().length-1,k=0;double[] xs=new double[n],ys=new double[n];
        for(int i=left.pts().length-1;i>=0;i--){xs[k]=left.pts()[i][0];ys[k]=left.pts()[i][1];k++;}
        for(int i=1;i<right.pts().length;i++){xs[k]=right.pts()[i][0];ys[k]=right.pts()[i][1];k++;}
        List<double[][]> ridges=new ArrayList<>();List<Integer> sides=new ArrayList<>();List<Double> widths=new ArrayList<>();
        for(int dir=-1;dir<=1;dir+=2){
            Flank fl=dir<0?left:right;double half=dir<0?halfL:halfR;int rs=seed+(dir<0?60:80);
            double ue=.2+.16*Ease.hash(rs),ye=h*Math.max(.18,(1-fl.bend()*ue)*.82);
            double[] a={x+dir*half*.03,h*.975},e={x+dir*half*ue,ye},mid={(a[0]+e[0])/2+dir*half*.035,(a[1]+e[1])/2};
            ridges.add(within(xs,ys,new double[][]{a,mid,e}));sides.add(dir);widths.add(.62);
            for(int s=0;s<fl.shoulders().length;s++){
                double su=fl.shoulders()[s],sx=x+dir*half*su,sy=outlineAt(xs,ys,sx);if(sy<.6)continue;
                double len=sy*(.42+.16*Ease.hash(rs+5+s)),ex=sx+dir*half*(.05+.06*Ease.hash(rs+7+s)),ey=sy-len;
                ridges.add(within(xs,ys,new double[][]{{sx,sy*.97},{(sx+ex)/2+dir*.04,(sy+ey)/2},{ex,ey}}));sides.add(dir);widths.add(.48);
            }
        }
        boolean rightLonger=Ease.hash(seed+3)<.6;double longer=.72+.2*Ease.hash(seed+4),shorter=.45+.2*Ease.hash(seed+5);
        return new Peak(xs,ys,left.pts().length-1,front,delay,rightLonger?shorter:longer,rightLonger?longer:shorter,
            ridges.toArray(new double[0][][]),sides.stream().mapToInt(Integer::intValue).toArray(),widths.stream().mapToDouble(Double::doubleValue).toArray(),seed);
    }
    /** A ridge's points kept inside an outline: each a little under it (and over the ground). */
    static double[][] within(double[] xs,double[] ys,double[][] pts){
        double[][] out=new double[pts.length][];
        for(int i=0;i<pts.length;i++){double top=outlineAt(xs,ys,pts[i][0]);out[i]=new double[]{pts[i][0],MathHelper.clamp(pts[i][1],0,Math.max(0,top-.06-.02*i))};}
        return out;
    }
    /** How tall a mass stands at {@code t} (0 to 1): brushed up out of the ground (each mountain a little after the last, its front masses after its back one), sinking as the formation ends. */
    static float risen(StandClient.Cast c,Mountain mt,Peak p,float t){
        float d=mt.delay()+p.delay();
        return Ease.out(t,RISE0+d,RISE1+d)*(1-Ease.smooth(t,end(c)+GLOW,end(c)+GONE+.2f));
    }
    /** How far down from its summit a mass's rims are drawn at {@code t} (0 to 1): the brush runs down as it rises. */
    static float drawn(Mountain mt,Peak p,float t){float d=mt.delay()+p.delay();return Ease.smooth(t,RISE0+d+.06f,RISE1+d+.32f);}
    /** A point of a mountain's face (fixed in the world, looking at the formation's middle): {@code x} along it, {@code y} up, {@code front} before it. */
    static Vec3d onMountain(Vec3d centre,Mountain mt,double x,double y,double front){
        Vec3d out=new Vec3d(Math.cos(mt.az()),0,Math.sin(mt.az())),along=new Vec3d(-out.z,0,out.x);
        return centre.add(out.multiply(mt.radius()-front)).add(along.multiply(x)).add(0,y,0);
    }
    /** The ink of a mass (the blended pass): near black under its outline, washing out a little toward its foot. */
    static void peakBody(EffectMesh m,Vec3d centre,Mountain mt,Peak p,float h,float a){
        for(int k=0;k+1<p.xs().length;k++){
            double x0=p.xs()[k],x1=p.xs()[k+1],y0=p.ys()[k]*h,y1=p.ys()[k+1]*h;
            if(y0<.01&&y1<.01)continue;
            Vec3d t0=onMountain(centre,mt,x0,y0,p.front()),t1=onMountain(centre,mt,x1,y1,p.front());
            Vec3d m0=onMountain(centre,mt,x0,y0*.35,p.front()),m1=onMountain(centre,mt,x1,y1*.35,p.front());
            Vec3d b0=onMountain(centre,mt,x0,-.08,p.front()),b1=onMountain(centre,mt,x1,-.08,p.front());
            m.vertex(t0,0x04060d,.95f*a);m.vertex(t1,0x04060d,.95f*a);m.vertex(m1,0x070b1d,.9f*a);m.vertex(m0,0x070b1d,.9f*a);
            m.vertex(m0,0x070b1d,.9f*a);m.vertex(m1,0x070b1d,.9f*a);m.vertex(b1,0x111a38,.5f*a);m.vertex(b0,0x111a38,.5f*a);
        }
    }
    /** A mass's rims of light down both flanks from its summit, the one further than the other, and its ridges: paint (the blended pass) or their glow ({@code glow}, added). */
    static void peakRims(EffectMesh m,Vec3d centre,Mountain mt,Peak p,float h,float drawn,float a,boolean glow){
        if(drawn<=.005f)return;
        double w0=(.06+.02*p.height())*Math.sqrt(h);
        for(int dir=-1;dir<=1;dir+=2){
            List<double[]> pts=new ArrayList<>();
            for(int i=p.top();i>=0&&i<p.xs().length;i+=dir)pts.add(new double[]{p.xs()[i],p.ys()[i]*h});
            brushStroke(m,centre,mt,p.front()+.01,pts,dir<0,w0,(dir<0?p.rimL():p.rimR())*drawn,p.seed()+(dir<0?7:13),a,glow);
        }
        for(int r=0;r<p.ridges().length;r++){
            List<double[]> pts=new ArrayList<>();for(var q:p.ridges()[r])pts.add(new double[]{q[0],q[1]*h});
            brushStroke(m,centre,mt,p.front()+.015,pts,p.ridgeSide()[r]<0,w0*p.ridgeWidth()[r],Ease.smooth(drawn,.3f+.12f*(r%3),1),p.seed()+29+r*11,.85f*a,glow);
        }
    }
    /** Where along a polyline {@code d} is (by the lengths {@code len} along it). */
    static double[] strokeAt(List<double[]> pts,double[] len,double d){
        int n=pts.size();if(d<=0)return pts.get(0);if(d>=len[n-1])return pts.get(n-1);
        int i=1;while(i<n-1&&len[i]<d)i++;
        double f=(d-len[i-1])/Math.max(1e-9,len[i]-len[i-1]);double[] a=pts.get(i-1),b=pts.get(i);
        return new double[]{a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f};
    }
    /** A smooth ragged wobble (0 to 1) along a stroke. */
    static double ragged(double x){return .5+.5*(.6*Math.sin(x)+.4*Math.sin(2.7*x+1.3));}
    /** The light blue paint of the mountains' rims: pale at their edge, light blue into the mountain, and the blue it washes into. */
    static final int RIM_EDGE=0xe6edff,RIM_BODY=0x8aa0ff,RIM_WASH=0x3c55d6,RIM_GLOW=0x4d6dff;
    /**
     * A brush stroke on a mountain's face along {@code pts} (x along it, y up) from its first point: its outer edge on them,
     * its paint {@code w0} wide into the mountain (to the left of the way it runs when {@code left}), landing a little
     * thinner, thinning to nothing at its far end, ragged as a dry brush, {@code reach} of it drawn (0 to 1). Paint of light
     * (the blended pass: pale at its edge, light blue into the mountain, washing into it), or ({@code glow}) its halo of
     * light, added: soft to both sides, a little brighter under the summit.
     */
    static void brushStroke(EffectMesh m,Vec3d centre,Mountain mt,double front,List<double[]> pts,boolean left,double w0,double reach,int seed,float a,boolean glow){
        int n=pts.size();if(n<2||reach<=.005||a<=.005f)return;
        double[] len=new double[n];
        for(int i=1;i<n;i++)len[i]=len[i-1]+Math.hypot(pts.get(i)[0]-pts.get(i-1)[0],pts.get(i)[1]-pts.get(i-1)[1]);
        double total=len[n-1],end=total*Math.min(1,reach);if(end<.02)return;
        int steps=Math.max(2,(int)Math.ceil(end/.09));
        Vec3d[] prev=null;float pw=0;
        for(int s=0;s<=steps;s++){
            double d=end*s/steps;
            double[] q=strokeAt(pts,len,d),q0=strokeAt(pts,len,Math.max(0,d-.07)),q1=strokeAt(pts,len,Math.min(total,d+.07));
            double tx=q1[0]-q0[0],ty=q1[1]-q0[1],tl=Math.hypot(tx,ty);if(tl<1e-9){tx=0;ty=-1;tl=1;}tx/=tl;ty/=tl;
            double nx=left?-ty:ty,ny=left?tx:-tx,u=d/total;
            double w=w0*(.55+.45*Ease.smooth((float)d,0,.18f))*Math.pow(Math.max(0,1-u),.75)*(.78+.44*ragged(d*6.5+seed*.37));
            float k=(float)(w/w0);
            Vec3d[] now=glow
                ?new Vec3d[]{onMountain(centre,mt,q[0]-nx*.18,q[1]-ny*.18,front),onMountain(centre,mt,q[0],q[1],front),onMountain(centre,mt,q[0]+nx*.24,q[1]+ny*.24,front),
                    onMountain(centre,mt,q[0]-nx*.07,q[1]-ny*.07,front),onMountain(centre,mt,q[0]+nx*w*1.1,q[1]+ny*w*1.1,front)}
                :new Vec3d[]{onMountain(centre,mt,q[0]-nx*.012,q[1]-ny*.012,front),onMountain(centre,mt,q[0]+nx*w,q[1]+ny*w,front),onMountain(centre,mt,q[0]+nx*w*1.9,q[1]+ny*w*1.9,front)};
            if(prev!=null){
                if(glow){
                    float g0=.12f*a*pw,g1=.12f*a*k,s0=.16f*a*pw*pw,s1=.16f*a*k*k;
                    m.vertex(prev[0],RIM_GLOW,0);m.vertex(now[0],RIM_GLOW,0);m.vertex(now[1],RIM_GLOW,g1);m.vertex(prev[1],RIM_GLOW,g0);
                    m.vertex(prev[1],RIM_GLOW,g0);m.vertex(now[1],RIM_GLOW,g1);m.vertex(now[2],RIM_GLOW,0);m.vertex(prev[2],RIM_GLOW,0);
                    m.vertex(prev[3],PALE,0);m.vertex(now[3],PALE,0);m.vertex(now[1],PALE,s1);m.vertex(prev[1],PALE,s0);
                    m.vertex(prev[1],PALE,s0);m.vertex(now[1],PALE,s1);m.vertex(now[4],PALE,0);m.vertex(prev[4],PALE,0);
                }else{
                    float e0=.95f*a*Math.min(1,pw*3),e1=.95f*a*Math.min(1,k*3),b0=.72f*a*Math.min(1,pw*3),b1=.72f*a*Math.min(1,k*3);
                    m.vertex(prev[0],RIM_EDGE,e0);m.vertex(now[0],RIM_EDGE,e1);m.vertex(now[1],RIM_BODY,b1);m.vertex(prev[1],RIM_BODY,b0);
                    m.vertex(prev[1],RIM_BODY,b0);m.vertex(now[1],RIM_BODY,b1);m.vertex(now[2],RIM_WASH,0);m.vertex(prev[2],RIM_WASH,0);
                }
            }
            prev=now;pw=k;
        }
    }
    /** A mountain (the blended pass): its masses one over another, the farthest from the camera first, each its ink and its rims. */
    static void mountain(Fx c,EffectMesh m,float t,Vec3d centre,Mountain mt){
        Vec3d out=new Vec3d(Math.cos(mt.az()),0,Math.sin(mt.az()));
        boolean inside=c.camera.subtract(centre).dotProduct(out)<mt.radius()-.35;
        int n=mt.peaks().length;
        for(int j=0;j<n;j++){
            var p=mt.peaks()[inside?j:n-1-j];
            float h=risen(c.cast,mt,p,t);if(h<=.01f)continue;float a=Math.min(1,h*1.6f);
            peakBody(m,centre,mt,p,h,a);
            peakRims(m,centre,mt,p,h,drawn(mt,p,t),a,false);
        }
    }
    /** The mountains' light (the light pass): the soft glow of their rims and ridges. */
    static void mountainLight(Fx c,EffectMesh m,float t){
        Vec3d centre=centre(c.cast);
        for(var mt:mountains(c.cast.id(),yaw(c.cast)))for(var p:mt.peaks()){
            float h=risen(c.cast,mt,p,t);if(h<=.01f)continue;
            peakRims(m,centre,mt,p,h,drawn(mt,p,t),Math.min(1,h*1.4f),true);
        }
    }

    /**
     * A cloud of the formation (2.82.2, user request: "구름과 산이 너무 이상하게 생겼어 다시 예쁘게 꾸며주고", the video: soft
     * white clouds among and over the mountains, one end curled; low over the ground, thin wisps of mist; 2.82.1's smooth
     * white tubes looked strange): which way round the formation its curl lies (radians) and how fast it drifts round
     * (radians a second), how far out, how high, how long its band runs from its curl, how thick, how big its curl and how
     * many turns it winds (none for a wisp of mist), which way the curl turns (-1 up over the band, 1 down under it), how
     * high the band arches, when it comes in, how strong it is, its seed.
     */
    record Cloud(double az,double drift,double radius,double up,double length,double thick,double curl,double turns,int dir,double arch,float in,float strength,int seed){
        boolean mist(){return curl<=0;}
    }
    private static final Map<UUID,Cloud[]> CLOUDS=new LinkedHashMap<>();
    /**
     * The clouds of the formation of swing {@code id} (the same on every client), its user having faced {@code yaw}: six
     * over and behind the mountains, two among them before their feet (none of these behind her, 2.82.4: CLOUD_GAP), and
     * seven low wisps of mist over the ground.
     */
    static Cloud[] clouds(UUID id,float yaw){
        var l=CLOUDS.get(id);if(l!=null)return l;
        int s0=(int)(id.getMostSignificantBits()*17+id.getLeastSignificantBits()*7);
        List<Cloud> out=new ArrayList<>();double off=behind(yaw)+CLOUD_GAP,span=Math.PI*2-2*CLOUD_GAP;
        for(int i=0;i<6;i++){
            int seed=s0+131*(i+1);
            out.add(new Cloud(off+span*(i+.25+.5*Ease.hash(seed+1))/6,(Ease.hash(seed+2)<.5?-1:1)*(.006+.008*Ease.hash(seed+3)),IsosangRules.FORMATION_RADIUS+4.2+3.0*Ease.hash(seed+4),4.6+2.2*Ease.hash(seed+5),
                5+4*Ease.hash(seed+6),1.2+.5*Ease.hash(seed+7),1.1+.5*Ease.hash(seed+8),.75+.3*Ease.hash(seed+9),Ease.hash(seed+10)<.72?-1:1,(Ease.hash(seed+11)-.5)*1.2,
                MIST0+.05f+.5f*(float)Ease.hash(seed+12),1,seed));
        }
        for(int i=0;i<2;i++){
            int seed=s0+977*(i+1);
            out.add(new Cloud(off+span*(i+.3+.4*Ease.hash(seed+1))/2,(Ease.hash(seed+2)<.5?-1:1)*(.008+.008*Ease.hash(seed+3)),IsosangRules.FORMATION_RADIUS+1.2+1.2*Ease.hash(seed+4),2.4+1.2*Ease.hash(seed+5),
                4+3*Ease.hash(seed+6),.8+.35*Ease.hash(seed+7),.8+.35*Ease.hash(seed+8),.7+.3*Ease.hash(seed+9),Ease.hash(seed+10)<.6?-1:1,(Ease.hash(seed+11)-.5)*.9,
                MIST0+.25f+.4f*(float)Ease.hash(seed+12),.85f,seed));
        }
        for(int i=0;i<7;i++){
            int seed=s0+1601*(i+1);
            out.add(new Cloud(Math.PI*2*(i+.6*Ease.hash(seed+1))/7,(Ease.hash(seed+2)<.5?-1:1)*(.012+.014*Ease.hash(seed+3)),IsosangRules.FORMATION_RADIUS-3.6+4*Ease.hash(seed+4),.35+.65*Ease.hash(seed+5),
                3.5+3*Ease.hash(seed+6),.4+.25*Ease.hash(seed+7),0,0,1,(Ease.hash(seed+11)-.5)*.4,
                MIST0+.6f*(float)Ease.hash(seed+12),.7f,seed));
        }
        l=out.toArray(new Cloud[0]);
        CLOUDS.put(id,l);while(CLOUDS.size()>16)CLOUDS.remove(CLOUDS.keySet().iterator().next());
        return l;
    }
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
            double prof=(x<.6?1:Math.sqrt(Math.max(0,1-Math.pow((x-.6)/.4,2))))*(1+.14*Math.sin(u*2.3+cl.seed()*.7+t*.25)*Math.min(1,u/.8));
            pts.add(new double[]{u,v,prof*cloudIn(shown,x)});
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
    static void cloud(StandClient.Cast cast,EffectMesh m,float t,Vec3d centre,Cloud cl){
        float shown=cloudShown(cast,cl,t);if(shown<=.01f)return;
        var path=cloudPath(cl,t,shown);int n=path.size(),curl=curlPoints(cl);
        double az=cl.az()+cl.drift()*t,up=cl.up()+.12*Math.sin(t*.5+cl.seed()),grow=1+.3*Ease.smooth(t,end(cast)+GLOW,end(cast)+GONE+.3f);
        boolean mist=cl.mist();double aspect=mist?2.3:1.15,tall=mist?.55:1,least=mist?.3:.12;
        float a0=(mist?.1f:.14f)*cl.strength()*shown;
        double carry=0;int k=0;
        for(int i=0;i+1<n;i++){
            double[] p0=path.get(i),p1=path.get(i+1);double du=p1[0]-p0[0],dv=p1[1]-p0[1],len=Math.hypot(du,dv);if(len<1e-9)continue;
            while(carry<=len){
                double f=carry/len,u=p0[0]+du*f,v=p0[1]+dv*f,prof=p0[2]+(p1[2]-p0[2])*f;
                double r=cl.thick()*.5*prof*grow*(.72+.5*Ease.hash(cl.seed()*31+k*7));
                if(r>.04){
                    puff(m,centre,cl,az,up,u,v,r*aspect,r*tall,a0);
                    // the band's top swells here and there into a smaller puff (not round the curl, nor its thin ends)
                    if(!mist&&i>=curl&&prof>.5&&Ease.hash(cl.seed()*13+k*5)<.4){
                        double nu=-dv/len,nv=du/len;if(nv<0){nu=-nu;nv=-nv;}
                        double br=r*(.45+.3*Ease.hash(cl.seed()*17+k*3)),lift=r*(.35+.25*Ease.hash(cl.seed()*19+k));
                        puff(m,centre,cl,az,up,u+nu*lift,v+nv*lift,br*aspect,br,a0*.85f);
                    }
                }
                k++;carry+=Math.max(least,r*PUFF_STEP*(.8+.4*Ease.hash(cl.seed()*23+k*11)));
            }
            carry-=len;
        }
    }
    /** A puff of a cloud at {@code u} along its band and {@code v} up: three soft discs {@code ru} across and {@code rv} up, 60° apart round the upright, the first facing the formation's middle. */
    static void puff(EffectMesh m,Vec3d centre,Cloud cl,double az,double up,double u,double v,double ru,double rv,float alpha){
        double a=az+u/cl.radius();Vec3d out=new Vec3d(Math.cos(a),0,Math.sin(a)),along=new Vec3d(-out.z,0,out.x);
        Vec3d c=centre.add(out.multiply(cl.radius())).add(0,up+v,0),vv=new Vec3d(0,rv,0);
        for(int s=0;s<3;s++){
            double turn=Math.PI/3*s;Vec3d uu=along.multiply(Math.cos(turn)).add(out.multiply(Math.sin(turn))).multiply(ru);
            disc(m,c,uu,vv,s==0?alpha:alpha*.72f);
        }
    }
    /** A soft disc: full over its inner six tenths, fading to nothing at its rim; white along its top, greyer under it. */
    static void disc(EffectMesh m,Vec3d c,Vec3d u,Vec3d v,float alpha){
        for(int i=0;i<PUFF_SEGS;i++){
            double a0=Math.PI*2*i/PUFF_SEGS,a1=Math.PI*2*(i+1)/PUFF_SEGS;
            Vec3d d0=u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))),d1=u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
            Vec3d i0=c.add(d0.multiply(.6)),i1=c.add(d1.multiply(.6)),o0=c.add(d0),o1=c.add(d1);
            int c0=Math.sin(a0)>0?CLOUD_TOP:CLOUD_UNDER,c1=Math.sin(a1)>0?CLOUD_TOP:CLOUD_UNDER;
            m.vertex(c,CLOUD,alpha);m.vertex(i0,CLOUD,alpha*.85f);m.vertex(i1,CLOUD,alpha*.85f);m.vertex(c,CLOUD,alpha);
            m.vertex(i0,CLOUD,alpha*.85f);m.vertex(o0,c0,0);m.vertex(o1,c1,0);m.vertex(i1,CLOUD,alpha*.85f);
        }
    }
    /**
     * The mountains and the clouds (the blended pass): everything drawn from the farthest from the camera to the nearest,
     * the blended pass keeping no depth of its own.
     */
    static void scenery(Fx c,EffectMesh m,float t){
        Vec3d centre=centre(c.cast);
        record Far(double far,Runnable draw){}
        List<Far> all=new ArrayList<>();
        for(var mt:mountains(c.cast.id(),yaw(c.cast))){
            boolean any=false;for(var p:mt.peaks())any|=risen(c.cast,mt,p,t)>.01f;
            if(any)all.add(new Far(onMountain(centre,mt,0,2,0).squaredDistanceTo(c.camera),()->mountain(c,m,t,centre,mt)));
        }
        for(var cl:clouds(c.cast.id(),yaw(c.cast))){
            if(cloudShown(c.cast,cl,t)<=.01f)continue;
            double a=cl.az()+cl.drift()*t+cl.length()*.3/cl.radius();
            Vec3d mid=centre.add(Math.cos(a)*cl.radius(),cl.up(),Math.sin(a)*cl.radius());
            all.add(new Far(mid.squaredDistanceTo(c.camera),()->cloud(c.cast,m,t,centre,cl)));
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
     * a see-through navy water, still, from round the formation's bare middle out to the mountains' feet (shore); in it
     * thin, long dark streaks drifting slowly along still, gently winding lanes round the middle as a river carries
     * things, the only thing of it that moves: counterclockwise seen from above (as the whirlpool turned), FLOW_SPEED
     * blocks a second in the middle of its channel and FLOW_BANK of that at its banks (the ink shader's riverTurn, its
     * RIVER_V and RIVER_BANK); a few thin strands of pale water, still, along the mountains' feet (the ink shader, style
     * 14: the water seeds 8 to 11, the strands 12 to 15; its uv the offset over FLOW_SCALE blocks). How strongly it lies
     * (0 to 1: coming in as the whirlpool's water turns into the smoke, FLOW0 to FLOW1, and going as the formation ends,
     * with the mountains), and the formation's clock the river runs by, in thousandths of a second in sixteen bits (each
     * lane of it turns at its own pace, so it is told the time, not a turn).
     */
    static float flowLife(float t,float end){return Ease.smooth(t,FLOW0,FLOW1)*(1-Ease.smooth(t,end,end+GONE));}
    static int flowClock(float t){return MathHelper.clamp(Math.round(t*1000),0,65535);}
    static final float FLOW0=1.3f,FLOW1=2f;
    static final double FLOW_SPEED=.9,FLOW_BANK=.45,FLOW_SCALE=IsosangRules.FORMATION_RADIUS+1;
    /**
     * Where the water lies round the formation (2.82.14, user request: "산이랑 물이랑 X좌표 붙어있게 해 떨어져있으니까
     * 이상함": it ended a block and more short of the mountains, the ground bare between): its rings at SHORE_STEPS ways
     * round, from WATER_IN blocks out (inside the shader's soft inner edge) to the foot of the nearest mountain that way
     * (waterEdge), fading out over EDGE_PAST blocks past it, under the mountain; and the strip of its pale strands along
     * the mountains' feet (STRAND_* blocks inside the edge, there only as far as a mountain stands that way: cover).
     * Fixed for a formation, as its mountains are.
     */
    record Shore(double[][] water,double[][] strands,float[] cover){}
    private static final Map<UUID,Shore> SHORES=new LinkedHashMap<>();
    static Shore shore(UUID id,float yaw){
        var s=SHORES.get(id);if(s!=null)return s;
        var ms=mountains(id,yaw);int n=SHORE_STEPS;
        double[] in=new double[n],edge=new double[n],past=new double[n],s0=new double[n],s1=new double[n],s2=new double[n],s3=new double[n];float[] cover=new float[n];
        for(int k=0;k<n;k++){
            double az=Math.PI*2*k/n,e=waterEdge(ms,az);
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
    static final double WATER_IN=3.9,EDGE_PAST=.6,EDGE_FADE=1.5,STRAND_FROM=2.6,STRAND_FULL0=1.7,STRAND_FULL1=.8,STRAND_TO=.2;
    /**
     * How far out the water runs the way {@code az} (radians in the world's x, z) from the formation's middle: to the
     * face of the nearest mountain that way (a mountain's face counts along its masses, fading out over EDGE_FADE blocks
     * past their feet; the masses before it stand in the water), else as far as the farthest mountain's face. Smooth all
     * round (the least of the faces that way, each eased toward the farthest as it fades).
     */
    static double waterEdge(Mountain[] ms,double az){
        double far=0;for(var mt:ms)far=Math.max(far,mt.radius());
        double d=far;
        for(var mt:ms){
            double w=along(mt,az);if(w<=0)continue;
            double s=mt.radius()/Math.cos(Math.IEEEremainder(az-mt.az(),Math.PI*2));
            d=Math.min(d,far+Math.min(0,s-far)*w);
        }
        return d;
    }
    /** How much of the mountain {@code mt} stands the way {@code az} (0 to 1): 1 along its masses, fading out over EDGE_FADE blocks past their feet; 0 off to its sides. */
    static double along(Mountain mt,double az){
        double off=Math.IEEEremainder(az-mt.az(),Math.PI*2);if(Math.cos(off)<.25)return 0;
        double x=mt.radius()*Math.tan(off),lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;
        for(var p:mt.peaks()){lo=Math.min(lo,p.xs()[0]);hi=Math.max(hi,p.xs()[p.xs().length-1]);}
        double over=Math.max(lo-x,x-hi);
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
     * (2.82.7); the still navy water lying on the ground as it stands, out to the mountains' feet, thin dark streaks
     * drifting in it as a river's (2.82.13 to 2.82.15: style 14, seeds 8 to 15, the formation's clock in the green and
     * blue bytes; drawn in rings out to the mountains, KagenoFire.inkGroundRings). The others each a square of the ink shader
     * (KagenoFire.inkGround) on the ground, its uv the offset over its radius.
     */
    static void ground(Fx c){
        float t=c.sec;Vec3d centre=centre(c.cast);int seed=c.cast.id().hashCode();
        float blue=tint(t);
        if(blue>.005f){double r=IsosangRules.FORMATION_RADIUS+.6;KagenoFire.inkGround(centre.add(0,.04,0),r,r,14,seed&7,holeByte(t,r),0,blue);}
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
    }
    /**
     * The ink left on the ground {@code d} blocks from the formation's middle (0 to 1): gone as the spreading whirlpool's
     * clean front passes it (2.82.9, user request: "바닥 잉크도 퍼질때 동시에 사라지게 하면 안되나?": no blue ground left
     * under the formation after it, nor the ink splashed at the swords' feet; 2.82.10: from the middle out with the front,
     * drying away just behind it).
     */
    static float groundInk(float t,double d){return 1-Ease.smooth((float)front(t),(float)d-1f,(float)d+2f);}
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
        // as it ends: ink where the swords stood and where the ink they break into falls
        float e=end(c.cast)+INKED;
        if(t>=e)for(var b:layout(c.cast.id())){
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
        List<Drop> out=new ArrayList<>();float e=end(c.cast)+INKED;if(t<e)return out;
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
        float e=end(c.cast),flare=Ease.smooth(t,e,e+.04f)*(1-Ease.smooth(t,e+GLOW,e+INKED)),ink=Ease.smooth(t,e+GLOW,e+INKED);
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
            double size=b.flower()*open*(1-.4*wilt);float a=(1-wilt);if(a<=.01f||size<=.01)continue;
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
            if(light){m.softDisc(f.add(0,.15,0),.55*size,BLUE,.35f*a);glint8(m,f.add(0,.3*size,0),.18*size*(.6+.4*twinkle(t,b.seed())),WHITE,.6f*a);}
        }
    }
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
        mountainLight(c,m,t);
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
    static final double VEIL_HIGH=3.6;
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
    static final int MIST_SEGS=96;
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
        float t=c.sec;if(t<SPREAD0||t>=SMOKE_OUT1+.02f)return;
        Vec3d mid=poolCentre(c.cast);double back=behind(yaw(c.cast));int seed=c.cast.id().hashCode();
        float fm=frontMist(t);
        if(fm>.005f)mist(c,mid,frontMistAt(t),frontMistHeight(t),.55f*fm,0,seed+11,back);
        float rm=veilLife(t);
        if(rm>.005f)mist(c,mid,rim(t),veilHeight(t),.92f*rm,veilThin(t),seed+29,back);
    }
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
            double period=4+3*Ease.hash(b.seed()+80),phase=period*Ease.hash(b.seed()+81),u=((t+phase)%period)/period;
            if(u>.07)continue;float a=(float)Math.sin(Math.PI*u/.07);
            Vec3d top=planted(centre,yaw,b),q=at(top,frame(down(b),spinAt(b,t)),p(0,S_GUARD+.4*Ease.hash(b.seed()+82),0));
            glint8(m,q,.2,WHITE,.8f*a);
        }
    }
    /** Her own sword's blue aura as she leaps and calls the swords. */
    static void aura(Fx c,EffectMesh m,float t){
        float g=Ease.window(t,.1f,.9f,.06f,.15f);if(g<=.01f)return;
        var hb=blade(c,t);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));
        m.softLine(guard,tip,.3,AURA[1],.12f*g);m.softLine(guard,tip,.14,AURA[1],.45f*g);
        m.line(guard,tip,.06,AURA[2],.85f*g);m.line(guard,tip,.027,AURA[3],.8f*g);
        m.softDisc(tip,.2,AURA[3],.5f*g);
    }
    /** The thread of lightning from her sword's point down to the ground as she leaps (the video: a white-blue bolt, flickering). */
    static void thread(Fx c,EffectMesh m,float t,Vec3d spot){
        float a=Ease.smooth(t,THREAD0-.02f,THREAD0+.02f)*(1-Ease.smooth(t,THREAD1-.08f,THREAD1));if(a<=.01f)return;
        var hb=blade(c,t);Vec3d tip=hb[0].add(hb[1].multiply(TIP)),ground=new Vec3d(tip.x,spot.y+.05,tip.z);
        if(tip.y-ground.y<.2)return;
        bolt(m,tip,ground,c.cast.id().hashCode()+7,t,.35,.05,WHITE,AZURE,a);
        m.softDisc(ground.add(0,.1,0),.7,BLUE,.45f*a);m.softDisc(tip,.25,PALE,.6f*a);
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
            m.softDisc(centre.add(0,.6,0),2.4,BLUE,.42f*k);m.softDisc(centre.add(0,.4,0),.8,WHITE,.6f*k);
        }
        // (2.82.6: no ring of its own racing out over the ground: the ink's ripples run out from here)
    }
    /**
     * The swords' light: a flash as each shows in the sky; their glow; the lightning each trails as it falls; the flash,
     * the shaft of light (every fifth one) and the ring at its foot as it strikes.
     */
    static void swordLight(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        float e=end(c.cast),flare=Ease.smooth(t,e,e+.04f)*(1-Ease.smooth(t,e+GLOW,e+INKED)),ink=Ease.smooth(t,e+GLOW,e+INKED);
        for(var b:layout(c.cast.id())){
            float a=shown(c.cast,b,t);if(a<=.005f)continue;
            Vec3d top=planted(centre,yaw,b).add(0,above(b,t),0),foot=foot(centre,yaw,b);var f=frame(down(b),spinAt(b,t));
            // shows in the sky
            float show=FxLib.kick(t,b.shows()+.02f,.1f);
            if(show>.01f){Vec3d mid=at(top,f,p(0,S_LEN*.45,0));m.star(mid,.5,WHITE,.7f*show);m.softDisc(mid,.6,AZURE,.3f*show);}
            swordGlow(m,top,f,a,dark(b,t),sky(b,t),flare,ink);
            // the lightning it trails as it falls, from the sky down to its point, gone a moment after it strikes
            float from=b.plant()-FALL,trail=Ease.smooth(t,from,from+.03f)*(1-Ease.smooth(t,b.plant()+.06f,b.plant()+.2f));
            if(trail>.01f){
                Vec3d point=at(top,f,p(0,S_LEN,0)),high=foot.add(0,b.sky()+3,0);
                if(t>=b.plant())point=foot;
                bolt(m,high,point,b.seed(),t,.3,.034,PALE,BLUE,.8f*trail);
            }
            // as it strikes: a flash and a ring at its foot, a shaft of light from every fifth one
            float k=FxLib.kick(t,b.plant(),.1f);
            if(k>.01f){
                m.softDisc(foot.add(0,.3,0),1.1,BLUE,.45f*k);m.star(foot.add(0,.35,0),.8,WHITE,.7f*k);
                float u=Ease.unit(t,b.plant(),b.plant()+.18f);if(u<1)groundBand(m,foot.add(0,.06,0),.15+.7*Ease.out(t,b.plant(),b.plant()+.18f),.08,PALE,.45f*(1-u),b.seed(),t);
            }
            if(Math.floorMod(b.seed(),5)==0){
                float s=Ease.smooth(t,b.plant(),b.plant()+.02f)*(1-Ease.smooth(t,b.plant()+.1f,b.plant()+.4f));
                if(s>.01f){Vec3d up=foot.add(0,14,0);m.line(foot,up,.06,WHITE,.55f*s);m.softLine(foot,up,.45,CYAN,.25f*s);}
            }
            // standing: now and then a gleam runs down its edge from its guard to its point (2.82.1)
            if(t>SILVER1&&flare<=.01f&&ink<=.01f&&a>.5f){
                double period=5.5+3.5*Ease.hash(b.seed()+84),u=((t+period*Ease.hash(b.seed()+85))%period)/GLEAM;
                if(u<1){
                    double y=S_GUARD+.05+(S_LEN-S_GUARD-.12)*Ease.smooth((float)u,0,1);float g=(float)Math.sin(Math.PI*u)*a;
                    Vec3d g0=at(top,f,edge(y-.15)),g1=at(top,f,edge(y+.15)),g2=at(top,f,edge(y));
                    m.softLine(g0,g1,.09*THIN,PALE,.5f*g);m.line(g0,g1,.016*THIN,WHITE,.75f*g);m.softDisc(g2,.12*THIN,WHITE,.35f*g);
                }
            }
            // as the formation ends: a four-pointed star of light lies on the ground at its foot, a pillar of light up it (the video)
            if(flare>.01f){
                Vec3d g=foot.add(0,.05,0),x=Vec3d.fromPolar(0,(float)spinAt(b,t)),z=new Vec3d(-x.z,0,x.x);
                groundStar(m,g,x,z,1.6,1.15,flare);
                m.softTaper(foot,top.add(0,1.2,0),.95,.25,CYAN,.38f*flare);
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
                double size=e.getKey()==IsosangRules.FORMATION_IMPACT_AT?.95:.7;
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
        for(var b:layout(c.cast.id())){float k=FxLib.kick(t,b.plant(),.12f);if(k>.05f)f.light(foot(centre,yaw,b).add(0,.6,0),0x9fc8ff,6,.7f*k);}
        float e=end(c.cast),flare=Ease.smooth(t,e,e+.04f)*(1-Ease.smooth(t,e+GLOW,e+INKED+.05f));
        if(flare>.01f)f.light(centre.add(0,1,0),0x7fe6ff,14,1.3f*flare);
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
        return Math.max(Math.max(.7f*bump(sec,IsosangRules.H_PLANT,.1f),.95f*bump(sec,(float)IsosangRules.FORMATION_IMPACT_AT/20,.16f)),.45f*bump(sec,e+.02f,.1f));
    }
    /** How far her own camera draws back for it (blocks): out as she leaps, further as the swords rain to show them all, held a little back while it stands, in again as she drops. */
    static final double PULL_LEAP=2.2,PULL_RAIN=5,PULL_STAND=3.2;
    static double pull(float sec){
        double out=PULL_LEAP*Ease.smooth(sec,.1f,.45f)+(PULL_RAIN-PULL_LEAP)*Ease.smooth(sec,.9f,1.35f)-(PULL_RAIN-PULL_STAND)*Ease.smooth(sec,1.6f,2.6f);
        return out*(1-Ease.smooth(sec,IsosangRules.H_DROP,IsosangRules.H_DOWN+.3f));
    }
}
