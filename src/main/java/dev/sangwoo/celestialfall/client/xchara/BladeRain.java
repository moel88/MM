package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.RainRules;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * X Chara's H with their hack off, the rain of knives, its call drawn (2.132.5, user request: "적을 찌르는건 없애고 그냥
 * 비만 떨어지게": no run and no stab now, 2.132.0 to 2.132.4 had them; their user is free once it is called, "자유롭게
 * 움직이고 싸움"; the server's XCharaAttack.rain, RainRules). One skill of the knife's pipeline (KnifeClient's swings,
 * XCharaCombo.RAIN; KnifeCombo hands it on here): the knife lit as the hack lights it ({@link #knife}) and raised
 * straight up to the sky, the free arm out, the head back to it ({@link #CALL}); at RainRules.CALL a beam of light goes up
 * off its point into the sky, where the rain's circle opens ({@link #draw}); held up a moment, then brought down to the
 * stance. The rain itself is RainClient's and RainFx's (a cast of its own, raining where they stood for 30 s).
 * Everything of how it looks is mine. Its sound is one the game has (the user's "있는 소리 재사용", 2.132.0): the slam's
 * knife lighting ({@link #CALL_SOUND}), its burst as the rain is called.
 */
public final class BladeRain {
    private BladeRain(){}
    /** The call's seconds: as the knife lights, as the rain is called, as it is brought down, and how long it is drawn for. */
    static final float LIT_AT=.05f,CALL_AT=RainRules.CALL/20f,DOWN_AT=.62f,END_AT=RainRules.LENGTH/20f;
    public static final float LIFE=END_AT+.6f;

    // ───────────────────────────── the body ─────────────────────────────

    private static R6.Pose on(double rt,double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm){
        return KnifeCombo.on(rt,fw,up,yaw,pitch,roll,rArm,blade,lArm,.20,0,-.20,-.04);
    }
    /** A pose of the call with the head turned up to the knife {@code up} degrees over level (whatever the body's lean). */
    private static R6.Pose looking(R6.Pose p,float up){p.headPitch=-p.pitch-up;return p;}
    /**
     * The call: from the stance the knife comes up before the body (its arm swinging up from down at the side) and on
     * up overhead, straight at the sky, the free arm thrown out low to the other side, the body drawn up a little and
     * leaning back, the head back to the knife; on the call it is thrust up the last of the way (the body up on its
     * toes), held so a moment, then brought down before the body to the stance.
     */
    public static final Anim CALL=call();
    private static Anim call(){
        float end=END_AT;
        Anim.Fix bent=IsosangLimbs.natural(IsosangLimbs.FLOOR);
        return Anim.joints(
            k(0,KnifeCombo.STANCE),
            // gathered: the knife swung up before the body, a little sunk over it
            k(.1f,looking(on(0,.02,-.05,-4,5,0,d(-24,26),d(16,14),d(-62,-34)),4)),
            // rising: the knife up past the shoulder, the body coming up after it
            k(.22f,looking(on(0,0,-.01,-2,-1,0,d(42,16),d(64,10),d(-40,-72)),18)),
            // raised overhead, straight at the sky
            k(.33f,looking(on(0,-.02,.02,0,-5,0,d(77,8),d(85,4),d(-22,-88)),30)),
            // the call: thrust up the last of the way, up on the toes
            k(CALL_AT,looking(on(0,-.02,.05,0,-6,0,d(84,5),d(89,2),d(-16,-92)),33)),
            // held up a moment
            k(.55f,looking(on(0,-.02,.03,0,-5,0,d(81,6),d(87,3),d(-20,-90)),31)),
            // brought down before the body
            k(DOWN_AT+.12f,looking(on(0,.01,-.03,-2,3,0,d(18,20),d(12,12),d(-52,-62)),8)),
            k(.88f,looking(on(0,.01,-.02,-1,1,0,d(-58,20),d(2,6),d(-80,-24)),1)),
            k(end,KnifeCombo.STANCE))
            .stops(Anim.stop(CALL_AT,.03,.05))
            .after((sec,f)->{
                R6.Frame b=bent.apply(sec,f);
                float rest=Math.max(1-Ease.smooth(sec,0,KnifeCombo.OUT_OF_REST),Ease.smooth(sec,end-KnifeCombo.INTO_REST,end));
                return rest<=0?b:R6.blend(b,KnifeCombo.AT_REST,Math.min(1,rest));
            });
    }

    // ───────────────────────────── the knife in the hand ─────────────────────────────

    private static float stopped(StandClient.Cast c){return GiantCleave.stopped(c);}
    /** The knife's look {@code sec} seconds into the call (stopped short {@code stop} seconds in, NaN: not), as the hack's {on, seconds since it changed}, or null while it is its own black: lit as it is raised, out as it is brought down (or as it stops short). */
    static float[] lit(float sec,float stop){
        if(sec<LIT_AT)return null;
        float out=Float.isNaN(stop)?DOWN_AT:Math.min(DOWN_AT,stop);
        if(out<LIT_AT)return null;
        if(sec>=out){float t=sec-out;return t<HackLook.OFF?new float[]{0,t}:null;}
        return new float[]{1,sec-LIT_AT};
    }
    /** This player's knife as their call has it this frame (the hack's look, HackMode.knifeLook), or null if they are in none or it is black. */
    public static HackLook.Frame knife(UUID id,float delta){
        var c=KnifeClient.rainOf(id);if(c==null)return null;
        float[] l=lit(c.age(delta)/20f,stopped(c));if(l==null)return null;
        return new HackLook.Frame(l[0]>0,l[1],HackMode.clock(delta)/20f,(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>17));
    }
    /** Where the knife and its hand are {@code sec} seconds into the call, for its user standing at {@code feet} facing {@code yaw}: {its hand, its way (unit)}. */
    static Vec3d[] knifeAt(Vec3d feet,float yaw,float sec){
        var hb=KagenoClips.blade(feet,yaw,R6.poseOf(CALL.frame(sec)));
        return new Vec3d[]{hb[0],hb[1].normalize()};
    }

    // ───────────────────────────── what the effects' layer adds ─────────────────────────────

    /** The call's light (RainFx.call): purple drawn onto the knife as it is raised, the beam up off its point as the rain is called. */
    static void draw(Fx c,EffectMesh m,boolean light){RainFx.call(c,m,light);}
    /** Its light on the world (RainFx.callFrame). */
    static void frame(Fx c,FxFrame f){RainFx.callFrame(c,f);}
    /** How hard it shakes the camera: a knock as the rain is called. */
    static final float SHAKE_CALL=.32f;
    static float shake(StandClient.Cast cast,float sec){
        if(!KnifeClient.cameTo(cast,0))return 0;
        return SHAKE_CALL*KnifeCombo.bump(sec,CALL_AT,.08f);
    }

    // ───────────────────────────── its sound ─────────────────────────────

    /**
     * Its sound, one the game has (the user's "있는 소리 재사용"): the slam's knife lighting (xchara_slam_lit: a hiss rising
     * for some 0.18 s into a bright burst at 0.19 s and a low knock), started CALL_SOUND_AT ticks in so its burst comes
     * as the rain is called.
     */
    static final String CALL_SOUND="xchara_slam_lit";static final int CALL_SOUND_AT=RainRules.CALL-4;

    /** For the capture tools: this player's latest call as "combo,age,stopped", and of their rain (RainClient), or null. */
    public static String describe(net.minecraft.entity.player.PlayerEntity player,float delta){
        var c=KnifeClient.rainOf(player.getUuid());
        String call=c==null?"-":String.format(java.util.Locale.ROOT,"%d,%.2f,%d",c.combo(),c.age(delta),KnifeClient.until(c.id())==Float.MAX_VALUE?0:1);
        return call+";"+RainClient.describe(player,delta);
    }
}
