package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.ERASE;

/**
 * X Chara's H with their hack off, the delete slash, its user drawn (2.133.0, user request: "5칸 정도 뛰어올라서 거대한
 * 참격을 바라보는 방향으로 날리고 참격을 맞은 블록은 픽셀로 산산이 부서지고 거기에 있던 플레이어는 0.5초 동안 글리치로
 * 사라졌다 나타나게"; the server's XCharaAttack.erase, EraseRules). A skill of the knife's pipeline (KnifeClient's swings,
 * XCharaCombo.ERASE; KnifeCombo hands it on here); the slash itself, once it has gone, is EraseClient's and EraseFx's (a
 * cast of its own).
 * <ul>
 * <li>The knife: from EraseRules.LIT the hack's knife, white within its glowing purple rim ({@link #knife}), until they
 * have landed and come up ({@link #OUT}), where it goes out as the hack's does.</li>
 * <li>The body ({@link #CLIP}): sunk to spring, sprung straight up (the knife brought up before them and over the
 * head), wound at the top with the knife back behind the head and the shoulder, the free arm out ahead at where it will
 * go; at EraseRules.SLASH the knife chopped over the head and down at the ground ahead, the body folding after it; then
 * down, the legs stretching under them, landed deep, and up. Every client draws the body up there over where it stands
 * ({@link #shift}: EraseRules.risen of how high the server says it leaps); their own camera goes with it ({@link #offset}).</li>
 * <li>The chop: a purple cut along the knife's path ({@link #cut}, the hack's cut); as it comes down, a beam from the
 * knife's point to the ground ahead where the slash rises out of it, and a burst there ({@link #draw}).</li>
 * </ul>
 * Its sounds are ones the game has (KnifeClient's cues for it): the slam's knife lighting and its leap, the knife's own
 * swing, the slam's landing boom and the double's wall breaking as the slash goes. Everything of how it looks is mine;
 * everything is a function of the skill's own clock.
 */
public final class EraseSlash {
    private EraseSlash(){}
    /** The server's ticks, in seconds. */
    static final float LIT=EraseRules.LIT/20f,LEAVE=EraseRules.LEAVE/20f,RISEN=EraseRules.RISEN/20f,SLASH=EraseRules.SLASH/20f,FALL=EraseRules.FALL/20f,
        LAND=EraseRules.LAND/20f,HOLD=EraseRules.HOLD/20f,END=EraseRules.LENGTH/20f;
    /** When the knife goes out (seconds): as they are free, landed and coming up. */
    static final float OUT=HOLD;
    /** How long one is drawn for (seconds): its knife gone out, its landing's ring gone. */
    public static final float LIFE=END+.6f;

    static float stopped(StandClient.Cast c){return GiantCleave.stopped(c);}
    static int seed(StandClient.Cast c){return (int)(c.start()*31+ERASE*7);}
    private static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}

    // ───────────────────────────── the knife ─────────────────────────────

    /**
     * The knife's look {@code sec} seconds in (stopped short {@code stop} seconds in, NaN: not), as the hack's {on,
     * seconds since it changed}, or null while it is its own black: lit from LIT, going out as the hack's does at OUT or as
     * it stops short.
     */
    static float[] lit(float sec,float stop){
        if(sec<LIT)return null;
        float out=Float.isNaN(stop)?OUT:Math.min(OUT,stop);
        if(out<LIT)return null;
        if(sec<out)return new float[]{1,sec-LIT};
        float t=sec-out;return t<HackLook.OFF?new float[]{0,t}:null;
    }
    /** This player's knife as their delete slash has it this frame (the hack's look, HackMode.knifeLook), or null if they are in none or it is black. */
    public static HackLook.Frame knife(UUID id,float delta){
        var c=KnifeClient.eraseOf(id);if(c==null)return null;
        float[] l=lit(c.age(delta)/20f,stopped(c));if(l==null)return null;
        return new HackLook.Frame(l[0]>0,l[1],HackMode.clock(delta)/20f,(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>17));
    }

    // ───────────────────────────── the leap ─────────────────────────────

    /** Stopped short in the air, the body falls back to where it stands over this many ticks. */
    static final float FALLBACK=6;
    /**
     * How high over where they stand cast {@code c}'s body is drawn {@code age} ticks in (blocks): EraseRules.risen of how
     * high the server said it leaps; stopped short in the air, falling back from where it was over FALLBACK ticks.
     */
    static double height(StandClient.Cast c,float age){
        double rise=KnifeClient.eraseRise(c.id());if(!(rise>0))return 0;
        float stop=stopped(c);
        if(Float.isNaN(stop)||age<=stop*20)return rise*EraseRules.risen(age);
        float st=stop*20;double was=rise*EraseRules.risen(st),u=Math.min(1,(age-st)/FALLBACK);
        return was*(1-u*u);
    }
    /** How far from where cast {@code c}'s user really is their body is drawn this frame (their renderer's offset, BeatdownRenderMixin): straight up over where they stand. */
    public static Vec3d shift(StandClient.Cast c,float delta){
        if(c==null||c.combo()!=ERASE||c.skill()!=dev.sangwoo.celestialfall.xchara.XCharaCombo.SKILL)return Vec3d.ZERO;
        double up=height(c,c.age(delta));return up<1e-4?Vec3d.ZERO:new Vec3d(0,up,0);
    }
    /** How far from where this player really is their body is drawn now, in a delete slash (null: in none, or on the ground): their own camera goes with it (BotandCamera). */
    public static Vec3d offset(UUID id,float delta){
        var c=KnifeClient.eraseOf(id);if(c==null)return null;
        Vec3d s=shift(c,delta);return s.lengthSquared()<1e-8?null:s;
    }
    /** Where cast {@code c}'s body is drawn this frame (its feet). */
    static Vec3d body(Fx c){return c.pos.add(shift(c.cast,c.delta));}

    // ───────────────────────────── the body ─────────────────────────────

    private static R6.Pose on(double fw,double up,float yaw,float pitch,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf){
        return KnifeCombo.on(0,fw,up,yaw,pitch,0,rArm,blade,lArm,rr,rf,lr,lf);
    }
    /** {@link #on}, the head going down with the body (not held level over it). */
    private static R6.Pose bowed(double fw,double up,float yaw,float pitch,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf){
        return on(fw,up,yaw,pitch,rArm,blade,lArm,rr,rf,lr,lf).head(yaw,-pitch*.3f,0);
    }
    /**
     * In the air: the body leant {@code pitch} forward, the knife's arm by its way from its own shoulder in the body's frame
     * ({right, up, forward}), the knife, the free arm and the legs by their ways in the world; the eyes {@code look}
     * degrees down.
     */
    private static R6.Pose air(float pitch,double[] arm,double[] blade,double[] lArm,double[] rLeg,double[] lLeg,float look){
        R6.Pose p=R6.world(0,0,0,pitch,0,d(60,0),blade,lArm,rLeg,lLeg);
        p.rArm=new R6.Dir(arm[0],arm[1],arm[2]).norm();
        return p.head(0,look-pitch,0);
    }
    /** The knife's arm in the body's frame: up and ahead, straight over the head, back behind the head and the shoulder (and further), up ahead into the chop, down ahead after it. */
    private static final double[] AHEAD={.08,.48,.36},OVERHEAD={.03,.60,.03},BEHIND={.06,.50,-.30},BEHIND_DEEP={.08,.42,-.40},
        UP_AHEAD={.05,.55,.30},DOWN_AHEAD={.05,.18,.52},LOW_AHEAD={.10,-.12,.55};
    /**
     * The clip (the leap itself is {@link #shift}'s: the clip is the body about its own feet).
     * <ol>
     * <li>the knife lights; they sink, the knife drawn down and back on the right, and crouch deep to spring;</li>
     * <li>sprung: stretched long out of the push, the knife brought up before them and over the head, the legs left under
     * them, the knees coming up as they slow;</li>
     * <li>the top: wound as far as it goes, the knife back behind the head and the shoulder, the free arm out ahead at the
     * ground it will go along, the eyes on it;</li>
     * <li>the chop (SLASH): over the head and down at the ground ahead, the trunk folding forward first, the arm after
     * it, the knife last and fastest; held a moment;</li>
     * <li>the fall: the legs stretching down under them, the knife held low and out; landed deep, the knees taking it,
     * the free arm out for balance; and up.</li>
     * </ol>
     */
    static final Anim CLIP=Anim.joints(
        k(0,KnifeCombo.STANCE),
        // the knife lit, sinking: drawn down and back on the right
        k(.08,on(-.01,-.07,-4,8,d(-44,40),d(-10,36),d(-78,-18),.19,.02,-.19,-.02)),
        // crouched deep to spring
        k(.13,on(-.05,-.28,-3,24,d(-56,34),d(-8,40),d(-58,-30),.22,.05,-.22,-.04)),
        // springing: the body coming up, the knife coming up before them
        k(LEAVE,on(-.02,-.06,-2,12,d(-24,28),d(30,22),d(-40,-44),.20,.03,-.20,-.02)),
        // off the ground, stretched long, rising: the knife up before them, the legs left under them
        k(.28,air(8,AHEAD,d(58,10),d(-56,-42),d(-84,155),d(-86,-165),6)),
        // the knife going up over the head
        k(.40,air(3,OVERHEAD,d(84,172),d(-34,-44),d(-74,160),d(-70,-168),14)),
        // slowing: back behind the head and the shoulder, the knees coming up
        k(.52,air(0,BEHIND,d(34,173),d(-16,-34),d(-62,140),d(-56,-158),22)),
        // the top: wound, the free arm out ahead at where it will go
        k(RISEN,air(-2,BEHIND,d(12,175),d(-30,-6),d(-56,130),d(-50,-150),28)),
        k(.70,air(-3,BEHIND_DEEP,d(2,176),d(-34,-4),d(-54,128),d(-48,-148),30)),
        // the chop: the trunk first, over the head
        k(.75,air(4,OVERHEAD,d(86,176),d(-40,-24),d(-58,135),d(-52,-150),32)),
        k(.775,air(16,UP_AHEAD,d(40,2),d(-48,-56),d(-62,145),d(-56,-155),34)),
        // the blow: down at the ground ahead
        k(SLASH,air(30,DOWN_AHEAD,d(-40,2),d(-52,-82),d(-68,155),d(-62,-160),38)),
        k(.83,air(34,LOW_AHEAD,d(-60,4),d(-50,-92),d(-72,160),d(-66,-162),40)),
        // the fall: the legs stretching down under them, the knife held low and out
        k(.95,air(24,LOW_AHEAD,d(-58,22),d(-36,-90),d(-82,170),d(-80,-172),30)),
        k(1.12,air(14,LOW_AHEAD,d(-52,34),d(-24,-86),d(-86,172),d(-84,-174),22)),
        // landed deep, the knees taking it
        k(LAND,bowed(.03,-.40,0,28,d(-48,26),d(-4,44),d(-30,-96),.24,.12,-.24,-.12)),
        k(LAND+.08f,bowed(.04,-.46,0,32,d(-50,26),d(-2,46),d(-28,-98),.24,.12,-.24,-.12)),
        k(1.40,bowed(.03,-.38,0,26,d(-50,28),d(-1,44),d(-34,-90),.24,.12,-.24,-.12)),
        // up
        k(HOLD+.08f,on(.01,-.16,0,10,d(-62,40),d(-5,22),d(-62,-56),.21,.06,-.21,-.06)),
        k(1.66,on(0,-.04,0,3,d(-74,60),d(-2,4),d(-80,-22),.19,.02,-.19,-.02)),
        k(END,KnifeCombo.STANCE)).stops(Anim.stop(SLASH,.05,.08),Anim.stop(LAND,.08,.12)).after(EraseSlash::fix);
    /** The ground under the clip's feet: where they stood until the leap, then as far under them as they have risen, the ground again as they land. */
    static final IsosangLimbs.Ground GROUND=sec->{
        double a=sec*20;if(a<=EraseRules.LEAVE||a>=EraseRules.LAND)return 0;
        return -EraseRules.JUMP*EraseRules.risen(a)*Ease.smooth((float)a,EraseRules.LEAVE,EraseRules.LEAVE+3);
    };
    /** How much the legs tuck in the air: all of it as they rise, half at the top and through the chop, little as they stretch down to land, all of it again as the ground comes up. */
    static double tuck(float sec){
        double t=1+(.55-1)*Ease.smooth(sec,.6f,.74f)+(.3-.55)*Ease.smooth(sec,.86f,1.0f);
        return t+(1-t)*Ease.smooth(sec,LAND-.08f,LAND);
    }
    private static final Anim.Fix BENT=IsosangLimbs.natural(GROUND,EraseSlash::tuck);
    /** A frame of the clip as it is drawn: its elbows and knees bent (IsosangLimbs), the stance's straight limbs at its beginning and its end. */
    static R6.Frame fix(float sec,R6.Frame f){
        R6.Frame b=BENT.apply(sec,f);
        float rest=Math.max(1-Ease.smooth(sec,0,KnifeCombo.OUT_OF_REST),Ease.smooth(sec,END-KnifeCombo.INTO_REST,END));
        return rest<=0?b:R6.blend(b,KnifeCombo.AT_REST,Math.min(1,rest));
    }
    /** The fist and the knife's way {@code sec} seconds in, for a body whose feet are at {@code feet} facing {@code yaw}. */
    static Vec3d[] blade(Vec3d feet,float yaw,float sec){return KagenoClips.blade(feet,yaw,R6.poseOf(CLIP.frame(sec)));}

    // ───────────────────────────── the chop's cut ─────────────────────────────

    /** The chop's cut: from when along the knife's path, how long its tail is, the band from the fist out (blocks), and when it is eaten and cooled after the blow (seconds). */
    static final float CHOP0=.71f,TAIL=.085f,EAT0=.06f,EAT1=.32f,COOL0=.04f,COOL1=.26f;
    static final double BAND_IN=1.0,BAND_OUT=3.05,EDGE=.7;
    /**
     * The chop's purple cut (the glitch pass, the hack's cut): along the knife's path from behind the head over and down
     * at the ground ahead, where the body was as it chopped (up in the air: still at the top then), and a strip of its
     * edge turned to the camera.
     */
    static void cut(Fx c){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());
        if(t<CHOP0||CHOP0>=stop)return;
        float mark=Math.min(SLASH,stop),head=Math.min(t,mark+.012f),from=Math.max(CHOP0,Math.min(head,mark)-TAIL);
        if(head<=from+.004f||t>=mark+EAT1)return;
        float eaten=Ease.smooth(t,mark+EAT0,mark+EAT1),cool=Ease.smooth(t,mark+COOL0,mark+COOL1),shown=KnifeCombo.own(c)*Ease.smooth(t,CHOP0,CHOP0+.03f);
        // (the body is at the top all through the chop: EraseRules.risen is 1 from RISEN to FALL)
        Vec3d feet=KnifeCombo.spot(c,0).add(0,height(c.cast,EraseRules.SLASH),0);
        var sw=KnifeCombo.swept(feet,c.cast.yaw(),ERASE,from,head,BAND_IN,BAND_OUT);
        KagenoFire.glitchRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),0,10,eaten,cool,shown);
        var e=KnifeCombo.edge(sw,c.camera,EDGE);
        KagenoFire.glitchRibbon(e.inner(),e.outer(),e.along(),e.n(),0,14,eaten,cool,shown);
    }

    // ───────────────────────────── what the effects' layer adds ─────────────────────────────

    static final int NEON=0xc45cff,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE,PURPLE=GlitchFx.PURPLE,INK=GlitchFx.INK,GLOW=GlitchFx.GLOW,DEEP=GlitchFx.DEEP;
    /** Where cast {@code c}'s slash rises out of the ground ahead of where they stood (its first front, a little on). */
    static Vec3d rises(StandClient.Cast c){return c.origin().add(Vec3d.fromPolar(0,c.yaw()).multiply(EraseRules.NEAR+.6));}
    /** The beam of the slash sent: from how long before the blow until how long after (seconds). */
    static final float BEAM0=.02f,BEAM1=.14f;
    /** Everything of the user's part but the cut: the gathering, the leap's ring, the beam and the burst where the slash rises, the landing's ring (bodies laid over what is behind them, or their light). */
    static void draw(Fx c,EffectMesh m,boolean light){
        float t=c.sec,until=KnifeClient.until(c.cast.id());int seed=seed(c.cast);
        Vec3d from=c.cast.origin(),f=Vec3d.fromPolar(0,c.cast.yaw());
        // gathering to spring: light drawn onto the knife
        if(light&&t>=LIT&&t<LEAVE+.06f&&t<until){
            var hb=blade(body(c),c.cast.yaw(),t);Vec3d mid=hb[0].add(hb[1].multiply(1.5));
            float g=Ease.smooth(t,LIT,LEAVE)*(1-Ease.smooth(t,LEAVE,LEAVE+.06f));
            m.softDisc(mid,1.1+.7*g,NEON,.3f*g);
        }
        // the leap: a ring off the ground where they stood, thin lines of light going up after them
        float l=t-LEAVE;
        if(l>=0&&l<.45f&&LEAVE<until){
            Vec3d g=from.add(0,.09,0);float a=1-l/.45f;
            if(light){
                FxLib.ring(m,g,3.4*Ease.out(l,0,.4f),.8,NEON,.6f*a);
                for(int j=0;j<10;j++){
                    double an=Math.PI*2*(j+h(j,seed,61))/10,r=.5+.55*h(j,seed,62);
                    Vec3d p=from.add(Math.cos(an)*r,0,Math.sin(an)*r);double top=(3+3.5*h(j,seed,63))*Ease.out(l,0,.3f);
                    m.softTaper(p.add(0,top*.3,0),p.add(0,top,0),.12,.02,LILAC,.55f*a);
                }
            }else FxLib.ring(m,g,3.4*Ease.out(l,0,.4f),.3,PURPLE,.8f*a);
        }
        // the slash sent: a beam from the knife's point down to where it rises out of the ground ahead, a burst there
        if(KnifeClient.cameTo(c.cast,0)){
            float s=t-SLASH;Vec3d at=rises(c.cast);
            if(s>=-BEAM0&&s<BEAM1){
                var hb=blade(c.pos.add(0,height(c.cast,EraseRules.SLASH),0),c.cast.yaw(),Math.min(t,SLASH));
                Vec3d tip=hb[0].add(hb[1].multiply(2.9));
                float a=Ease.smooth(s,-BEAM0,0)*(1-Ease.smooth(s,.03f,BEAM1));
                if(light){
                    m.softTaper(tip,at.add(0,.2,0),.5,1.4,NEON,.5f*a);
                    m.softTaper(tip,at.add(0,.2,0),.12,.4,WHITE,.85f*a);
                }else m.needle(tip,at.add(0,.2,0),.22,.3,PURPLE,.9f*a);
            }
            if(s>=0&&s<.7f)burst(m,at,f,seed,s,light,c.camera);
        }
        // the landing: a ring at their feet, a few pixels
        float d=t-LAND;
        if(d>=0&&d<.5f&&LAND<until){
            Vec3d g=from.add(0,.09,0);float a=1-d/.5f;
            if(light)FxLib.ring(m,g,2.6*Ease.out(d,0,.45f),.6,NEON,.5f*a);
            else FxLib.ring(m,g,2.6*Ease.out(d,0,.45f),.22,PURPLE,.75f*a);
            GlitchFx.pixels(m,from.add(0,.2,0),new Vec3d(0,1,0),.9,5,.07,14,seed+7,d,light,c.camera);
        }
    }
    /** The burst where the slash rises out of the ground, {@code s} seconds after the blow: a white star and flash, purple spikes up and out across the way, a ring along the ground, pixels thrown up. */
    static void burst(EffectMesh m,Vec3d at,Vec3d f,int seed,float s,boolean light,Vec3d camera){
        Vec3d right=new Vec3d(-f.z,0,f.x),up=new Vec3d(0,1,0);
        float near=GlitchFx.near(at.add(0,1,0),camera);
        if(light&&near>0){
            if(s<.14f)m.softDisc(at.add(0,.8,0),2.4,WHITE,.7f*Ease.smooth(s,0,.02f)*(1-s/.14f)*near);
            if(s<.22f)m.star(at.add(0,1,0),3.2,WHITE,.9f*Ease.smooth(s,0,.02f)*(1-s/.22f)*near);
            m.softDisc(at.add(0,1.2,0),3.0,NEON,.14f*Ease.smooth(s,0,.03f)*(1-Ease.smooth(s,.12f,.6f))*near);
        }
        // rings along the ground: one fast and wide, one narrow and slow
        for(int k=0;k<2;k++){
            float u=s-.04f*k;if(u<=0||u>=.55f)continue;
            double r=(k==0?7:4.5)*Ease.out(u,0,.55f);float a=1-u/.55f;
            if(light)FxLib.ring(m,at.add(0,.08,0),r,.55,k==0?NEON:WHITE,.3f*a);
            else FxLib.ring(m,at.add(0,.08,0),r,.4,k==0?PURPLE:LILAC,.8f*a);
        }
        // spikes up and out across the way (in the plane square to it), white, purple and dark by turns
        if(s<.5f&&near>0)for(int j=0;j<16;j++){
            double an=Math.toRadians(10+160*(j+.5*h(j,seed,71))/16);
            Vec3d dir=right.multiply(Math.cos(an)).add(up.multiply(Math.sin(an))).add(f.multiply(.25*(h(j,seed,72)-.3))).normalize();
            float life=.5f*(.6f+.4f*(float)h(j,seed,73));if(s>=life)continue;
            double len=(2.2+3.6*h(j,seed,74))*Ease.out(s,0,.07f);float fade=Ease.smooth(s,0,.02f)*(1-Ease.smooth(s,life*.35f,life))*near;
            Vec3d a0=at.add(dir.multiply(.3)),a1=at.add(dir.multiply(.3+len));int kind=j%3;
            if(light){if(kind==0)m.needle(a0,a1,.16,.22,WHITE,.9f*fade);else if(kind==1)m.needle(a0,a1,.3,.22,NEON,.2f*fade);}
            else if(kind!=0){m.needle(a0,a1,.24,.22,INK,.85f*fade);m.needle(a0,a1,.16,.22,kind==1?PURPLE:DEEP,.95f*fade);}
        }
        GlitchFx.pixels(m,at.add(0,.4,0),up,.8,11,.08,36,seed+3,s,light,camera);
    }
    /** Its light on the world; and little afterglow through the chop (the knife would leave copies of itself down its path). */
    static void frame(Fx c,FxFrame fr){
        float t=c.sec,until=KnifeClient.until(c.cast.id());
        if(t>=CHOP0&&t<SLASH+.08f)fr.afterglow(.06f);
        if(t<until&&t>=LIT&&t<OUT){
            var hb=blade(body(c),c.cast.yaw(),t);
            fr.light(hb[0].add(hb[1].multiply(1.4)),0x8a2be2,5,.4f);
        }
        if(KnifeClient.cameTo(c.cast,0)){
            float s=t-SLASH;Vec3d at=rises(c.cast).add(0,1,0);
            if(s>=0&&s<.7f)fr.light(at,0xb04cff,24,2.6f*(1-s/.7f));
        }
    }
    /** How hard it shakes the camera: a knock as they leave the ground, hard as the slash goes, a little as they land. */
    static final float SHAKE_LEAP=.3f,SHAKE_SLASH=1.4f,SHAKE_LAND=.45f;
    static float shake(StandClient.Cast cast,float sec){
        float until=KnifeClient.until(cast.id()),v=0;
        if(LEAVE<until)v+=SHAKE_LEAP*KnifeCombo.bump(sec,LEAVE,.08f);
        if(KnifeClient.cameTo(cast,0))v+=SHAKE_SLASH*KnifeCombo.bump(sec,SLASH,.16f);
        if(LAND<until)v+=SHAKE_LAND*KnifeCombo.bump(sec,LAND,.1f);
        return v;
    }

    // ───────────────────────────── its sounds ─────────────────────────────

    /**
     * Its sounds, ones the game has (KnifeClient plays them at their moments): the slam's knife lighting as it starts (its
     * burst as the knife is lit), the slam's leap as they leave the ground, the knife's own swing two ticks before the
     * chop (its "칙" on it), the slam's landing boom and the double's wall breaking (part of it) a tick before the slash
     * goes (their onsets on it).
     */
    static final String LIT_SOUND="xchara_slam_lit",LEAP_SOUND="xchara_slam_leap",SWING_SOUND="xchara_slash",BOOM_SOUND="xchara_slam_land",BREAK_SOUND="xchara_clone_wall";
    static final float BREAK_PART=.7f,SWING_PART=1f;
    /** Its cues: {sound, the tick it starts on, its part}. */
    static final Object[][] CUES={{LIT_SOUND,0,1f},{LEAP_SOUND,EraseRules.LEAVE-1,1f},{SWING_SOUND,EraseRules.SLASH-2,SWING_PART},
        {BOOM_SOUND,EraseRules.SLASH-1,1f},{BREAK_SOUND,EraseRules.SLASH-1,BREAK_PART}};

    /** For the capture tools: this player's latest delete slash as "combo,age,stopped,height", and of its slash (EraseClient), or "-". */
    public static String describe(net.minecraft.entity.player.PlayerEntity player,float delta){
        var c=KnifeClient.eraseOf(player.getUuid());
        String own=c==null?"-":String.format(java.util.Locale.ROOT,"%d,%.2f,%d,%.2f",c.combo(),c.age(delta),KnifeClient.until(c.id())==Float.MAX_VALUE?0:1,height(c,c.age(delta)));
        return own+";"+EraseClient.describe(player,delta);
    }
}
