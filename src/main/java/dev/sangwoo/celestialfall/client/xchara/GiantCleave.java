package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.combat.MotionPose;
import java.util.UUID;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * X Chara's R with their hack on, the cleave, drawn (2.121.0, user request: "R키는 패널을 누르면 들고 있던 검이 엄청
 * 거대해지고 그걸 양손으로 잡고 덤블링 한 번 돌고 크게 내려찍는 스킬 맞추면 못 움직이는 1초 무력화"; the server's
 * XCharaAttack.cleave, XCharaCombo.CLEAVE). It is a skill of the knife's pipeline (KnifeClient's swings, KnifeCombo's
 * passes hand it on here), so it has a hit's clock, its body clip, its news of whom it struck. There was no video of
 * it: the motion and the effects are mine to the user's words.
 * <ul>
 * <li>The press ({@link #PRESS}): the panel's arm comes up and its hand taps the panel once, lit violet
 * ({@link #pressAt}; the arm as their E's press has it, XCharaIdle).</li>
 * <li>The knife grows ({@link #scale}: to {@link #SCALE} times what the hand holds, a little over and back, some 7.5
 * blocks of it): the knife's arm brings it up in front of them as it grows; grown, its weight tips its point down to
 * the ground ahead, the panel folds away ({@link #panel}) and the other hand takes its rod behind the first
 * ({@link #grip}: that arm set by where the rod is, IsosangLimbs.leftHandAt).</li>
 * <li>The somersault ({@link #LEAP} to {@link #BLOW}): they sink, leap high (the knife is long: the leap clears its
 * whole turn off the ground) and turn once forward, tucked, the knife rigid in their hands, so its point draws one
 * whole wheel in the air: down ahead of them, under, up behind and over the top.</li>
 * <li>The blow ({@link #BLOW}, the server's): out of the turn they drop and the knife comes down from over their head
 * onto the ground ahead as they land, its length along the way they face. They hold it there a moment, the knife
 * shrinks back, the other hand lets go and the panel opens again.</li>
 * </ul>
 * The body is drawn up over where its user stands (the frame's root; the user stays on the ground), its own camera
 * rising and drawing back with it ({@link #cameraLift}, {@link #cameraPull}, {@link #cameraShoulder}). The effects are the hack's purple, drawn
 * in the world with the knife's passes: pixels thrown off the knife as it grows and as it shrinks, a ring under the
 * leap, the wheel's band behind the knife's point, and on the blow a scar along the ground under the knife, two rings
 * running out over the ground, spikes and pixels thrown up along it, and the knife's burst on whom it struck. No screen
 * effect but the shake. Everything is a function of the skill's own clock.
 */
public final class GiantCleave {
    private GiantCleave(){}
    static final int HIT=CLEAVE;
    /** When the panel is pressed, when the knife is grown and in both hands, when they leave the ground, when the blow lands (seconds: the server's ticks), and its motion's length. */
    static final float PRESS=CLEAVE_PRESS/20f,GROWN=CLEAVE_GROWN/20f,LEAP=CLEAVE_LEAP/20f,BLOW=blowAt(CLEAVE,0)/20f,LENGTH_S=LENGTH[CLEAVE]/20f;
    /** How long one is drawn for (seconds): what the blow leaves on the ground is gone by then. */
    public static final float LIFE=LENGTH_S+.9f;
    /** How many times the knife the hand holds it grows to, and how far it swells over that on the way. */
    public static final float SCALE=2.5f,OVER=.4f;
    /** The knife grows from here to here; it shrinks back from here to here (seconds). */
    static final float GROW0=PRESS+.02f,GROW1=GROWN-.03f,SHRINK0=BLOW+.46f,SHRINK1=BLOW+.68f;
    /** The other hand comes onto the rod from here to here and lets go from here to here; it holds the rod this far behind the first (blocks). */
    static final float GRIP0=.32f,GRIP1=.43f,FREE0=BLOW+.47f,FREE1=BLOW+.62f;
    static final double GRIP_BACK=.22;
    /** The panel folds away as the other hand leaves it and opens again as the hand comes back (seconds). */
    static final float PANEL_OFF=.31f,PANEL_ON=BLOW+.58f;
    /** The grown knife from the fist to its point (blocks). */
    static final double REACH=KnifeCombo.TIP*SCALE;

    // ───────────────────────────── the knife's size ─────────────────────────────

    /** How many times its size the knife is {@code sec} seconds in: grown just after the press, a little over and back; shrunk back after the blow. */
    public static float scale(float sec){
        float u=MathHelper.clamp((sec-GROW0)/(GROW1-GROW0),0,1);
        // out past its size and back onto it (the swell largest two thirds of the way)
        float grown=u*u*(3-2*u)+OVER*(float)Math.sin(Math.PI*u)*u;
        return 1+(SCALE-1)*grown*(1-Ease.smooth(sec,SHRINK0,SHRINK1));
    }
    /** The same for one stopped short {@code stopped} seconds in (NaN: not): back to its size at once from what it was. */
    public static float scale(float sec,float stopped){
        if(Float.isNaN(stopped)||sec<=stopped)return scale(sec);
        return 1+(scale(stopped)-1)*(1-Ease.smooth(sec,stopped,stopped+.12f));
    }
    /** The knife's size in this player's hand now (1 if they are in no cleave). */
    public static float scale(UUID id,float delta){
        var c=KnifeClient.cleaveOf(id);if(c==null)return 1;
        return scale(c.age(delta)/20f,stopped(c));
    }
    static float stopped(StandClient.Cast c){float u=KnifeClient.until(c.id());return u==Float.MAX_VALUE?Float.NaN:u;}

    // ───────────────────────────── the panel, the other hand ─────────────────────────────

    /** The press of the panel {@code sec} seconds in as {lift, tap, lit, 1} (HackPanel.press; the last marks it violet, not E's lime). */
    static float[] pressAt(float sec){
        float lift=Ease.smooth(sec,0,.14f)*(1-Ease.smooth(sec,PRESS+.07f,PRESS+.16f));
        float tap=Ease.smooth(sec,PRESS-.06f,PRESS)*(1-Ease.smooth(sec,PRESS+.02f,PRESS+.09f));
        float lit=Ease.smooth(sec,PRESS-.02f,PRESS)*(1-Ease.smooth(sec,PRESS+.08f,PRESS+.3f));
        return new float[]{lift,tap,lit,1};
    }
    /** This player's press of the panel for their cleave this frame, or null. */
    public static float[] press(UUID id,float delta){
        var c=KnifeClient.cleaveOf(id);if(c==null||!Float.isNaN(stopped(c)))return null;
        float sec=c.age(delta)/20f;return sec<PANEL_OFF+.3f?pressAt(sec):null;
    }
    /** How much the other hand is on the knife's rod {@code sec} seconds in (0 to 1). */
    static float grip(float sec){return Ease.smooth(sec,GRIP0,GRIP1)*(1-Ease.smooth(sec,FREE0,FREE1));}
    /**
     * The panel through this player's cleave: null while it is as their hack has it; else {whether it is opening (1) or
     * folding away (0), seconds since}: folded away while both hands hold the knife (HackMode.panelLook).
     */
    public static float[] panel(UUID id,float delta){
        var c=KnifeClient.cleaveOf(id);if(c==null)return null;
        float sec=c.age(delta)/20f,stop=stopped(c);
        if(sec<PANEL_OFF)return null;
        float on=Float.isNaN(stop)?PANEL_ON:Math.min(PANEL_ON,stop);
        return sec<on?new float[]{0,sec-PANEL_OFF}:new float[]{1,sec-on};
    }

    // ───────────────────────────── the body ─────────────────────────────

    /** The body turned {@code pitch} forward (degrees: 360 one whole somersault), its arm and legs written in its own space, its knife by its way in the world (its elevation, ahead). */
    private static R6.Pose body(double up,float pitch,double[] rArm,double blade,float head){
        R6.Pose p=R6.pose().at(0,up,0).body(0,pitch,0).head(0,head,0);
        p.rArm=R6.dir(rArm[0],rArm[1]);p.blade=R6.dir(blade+pitch,0);p.lArm=R6.dir(-80,-8);
        return p;
    }
    /** Standing: the feet on the ground where they are put (right of, ahead of where their user stands). */
    static R6.Pose plant(double up,float pitch,double[] rArm,double blade,float head,double rr,double rf,double lr,double lf){
        R6.Pose p=body(up,pitch,rArm,blade,head);
        p.rLeg=IsosangLimbs.standingAt(p,0,rr,rf);p.lLeg=IsosangLimbs.standingAt(p,1,lr,lf);
        return p;
    }
    /** In the air: the legs as the body has them. */
    static R6.Pose air(double up,float pitch,double[] rArm,double blade,float head,double[] rLeg,double[] lLeg){
        R6.Pose p=body(up,pitch,rArm,blade,head);
        p.rLeg=R6.dir(rLeg[0],rLeg[1]);p.lLeg=R6.dir(lLeg[0],lLeg[1]);
        return p;
    }
    /** The knife's way in the body through the somersault (its elevation over the body's front: rigid in the hands). */
    static final double HELD=34;
    /** How high the leap carries them (blocks: the body is drawn this far up; the knife's whole turn clears the ground). */
    static final double APEX=7.1;
    private static final double[] FRONT=d(-33,-38),KNIFE_ARM=d(-76,90);
    private static final R6.Pose END=KnifeCombo.STANCE.copy().body(0,360,0);
    /**
     * The clip. Its somersault is the root's pitch going once round (R6 keeps whole turns), about the body's middle;
     * the knife is rigid in the hands through it, so its point goes round with the body.
     */
    static final Anim CLIP=Anim.joints(
        k(0,KnifeCombo.STANCE),
        // the press: they look down at the panel
        k(.10,plant(-.02,3,KNIFE_ARM,0,24,.17,0,-.17,0)),
        k(PRESS,plant(-.03,4,KNIFE_ARM,0,30,.17,0,-.17,0)),
        // the knife brought up in front as it grows, their eyes on it
        k(.30,plant(-.02,-3,d(-30,26),52,-10,.18,-.02,-.18,.04)),
        k(.38,plant(-.03,-6,d(-28,-10),40,-12,.20,-.06,-.20,.10)),
        // grown: its weight tips its point down, both hands on it
        k(GROWN,plant(-.08,5,FRONT,8,4,.22,-.10,-.22,.18)),
        // sunk for the leap, then up out of the legs (they leave the ground just after LEAP)
        k(.50,plant(-.38,17,d(-46,-44),-4.2,14,.22,-.10,-.22,.18)),
        k(LEAP,plant(-.23,14,d(-40,-42),-5.5,10,.20,-.06,-.20,.12)),
        k(.595,plant(-.01,6,d(-36,-40),-4,8,.15,.02,-.15,.02)),
        // up: the legs trail, the knife heaved up after them
        k(.625,air(.5,8,d(-34,-38),-2,6,d(-88,2),d(-86,-2))),
        k(.68,air(3.0,-5,d(-14,-36),28,-6,d(-80,178),d(-74,178))),
        k(.77,air(6.2,-8,d(2,-35),HELD+8,0,d(-60,0),d(-70,0))),
        // the somersault, tucked, at the top of the leap
        k(.83,air(6.95,60,d(2,-35),HELD-60,22,d(-28,4),d(-38,-4))),
        k(.89,air(APEX,135,d(2,-35),HELD-135,26,d(-22,4),d(-30,-4))),
        k(.95,air(6.95,215,d(2,-35),HELD-215,26,d(-22,4),d(-30,-4))),
        // out of it: the knife over the top, the legs let down
        k(1.02,air(6.0,300,d(14,-34),HELD-300+6,18,d(-45,2),d(-55,-2))),
        k(1.10,air(3.0,338,d(4,-35),48,8,d(-68,0),d(-76,0))),
        k(1.15,air(.7,358,d(-22,-40),17,6,d(-84,2),d(-86,-2))),
        // the feet touch a moment before the blow and the legs take the landing through it
        k(1.175,air(.02,364,d(-34,-42),6,8,d(-88,2),d(-88,-2))),
        // the blow: landed low, the knife along the ground ahead
        k(BLOW,plant(-.26,370,d(-46,-44),-5.6,14,.24,-.28,-.22,.36)),
        k(BLOW+.08f,plant(-.50,376,d(-54,-44),-4.0,16,.24,-.28,-.22,.36)),
        k(BLOW+.35f,plant(-.44,371,d(-52,-44),-4.4,12,.24,-.28,-.22,.36)),
        // up again: the knife its own size, the hands apart
        k(BLOW+.50f,plant(-.22,366,d(-40,-30),4,6,.20,-.14,-.20,.20)),
        k(BLOW+.70f,plant(-.05,361,d(-70,60),2,0,.17,-.02,-.17,.04)),
        k(LENGTH_S,END)).stops(Anim.stop(BLOW,.07,.08)).after(GiantCleave::fix);

    private static final Anim.Fix BENT=IsosangLimbs.natural(IsosangLimbs.FLOOR);
    /**
     * A frame of the clip as it is drawn: its elbows and knees bent (IsosangLimbs), its turn kept within one round, the
     * stance's straight limbs at its beginning and its end (as the knife's hits), and the other arm: at first the
     * panel's (as it holds the panel, up for the press and its tap), then on the knife's rod behind the first hand
     * ({@link #grip}), then the panel's again.
     */
    static R6.Frame fix(float sec,R6.Frame f){
        R6.Frame b=BENT.apply(sec,f);
        // (the same turn, within one round: the end is the stance, not the stance once round)
        b=new R6.Frame(b.joints(),b.grip(),b.fw(),b.up(),b.rt(),b.yaw(),MathHelper.wrapDegrees(b.pitch()),b.roll());
        float rest=Math.max(1-Ease.smooth(sec,0,KnifeCombo.OUT_OF_REST),Ease.smooth(sec,LENGTH_S-KnifeCombo.INTO_REST,LENGTH_S));
        if(rest>0)b=R6.blend(b,KnifeCombo.AT_REST,Math.min(1,rest));
        // the panel's arm: out from the body with the panel, up for the press
        float[] press=pressAt(sec);MotionPose idle=new MotionPose();
        XCharaIdle.arms(idle,true,1,1,0,press[0],press[1]);
        MotionPose m=b.joints().copy();
        m.joint[MotionPose.LEFT_ARM]=idle.joint[MotionPose.LEFT_ARM].clone();m.elbow[1]=idle.elbow[1];
        R6.Frame held=new R6.Frame(m,b.grip(),b.fw(),b.up(),b.rt(),b.yaw(),b.pitch(),b.roll());
        float g=grip(sec);if(g<=0)return held;
        // on the rod: behind the knife's hand, along the knife
        R6.Pose p=R6.poseOf(b);double r=R6.armReach(p.rElbow);
        double x=.3125+p.rArm.rt()*r-p.blade.rt()*GRIP_BACK,y=1.375+p.rArm.up()*r-p.blade.up()*GRIP_BACK,z=p.rArm.fw()*r-p.blade.fw()*GRIP_BACK;
        R6.Frame both=IsosangLimbs.leftHandAt(b,x,y,z);
        return g>=1?both:R6.blend(held,both,g);
    }

    // ───────────────────────────── the camera ─────────────────────────────

    /** How far the body is drawn up over its user {@code sec} seconds in (blocks). */
    static double raised(float sec){return Math.max(0,CLIP.frame(sec).up());}
    private static StandClient.Cast own(){
        var mc=net.minecraft.client.MinecraftClient.getInstance();if(mc.player==null)return null;
        var c=KnifeClient.cleaveOf(mc.player.getUuid());return c==null||!Float.isNaN(stopped(c))?null:c;
    }
    /** How far the local player's own camera rises with their leap now (blocks: most of the way with the body). */
    public static double cameraLift(float delta){var c=own();return c==null?0:.55*raised(c.age(delta)/20f);}
    /** How much of the camera's own framing of it is there {@code sec} seconds in: from the knife's growing until a little after the blow. */
    static float framed(float sec){return Ease.smooth(sec,GROW0,LEAP+.1f)*(1-Ease.smooth(sec,BLOW+.35f,BLOW+.8f));}
    /** How far it draws back for the wheel and the blow (blocks). */
    public static double cameraPull(float delta){var c=own();return c==null?0:4.5*framed(c.age(delta)/20f);}
    /**
     * How far it stands out to their right (blocks, as the shift lock's shoulder): the knife turns in the plane they
     * face along, which the camera right behind them is in (the wheel would be a line, and the knife would swing back
     * through the camera); from over to the side the wheel is seen as a wheel.
     */
    public static double cameraShoulder(float delta){var c=own();return c==null?0:4.4*framed(c.age(delta)/20f);}

    /**
     * The local player's camera through their cleave: turned from the way they look onto it (a point over the ground a
     * little ahead of them, rising with most of their leap), so the body up in the air and the knife's wheel stay in the
     * frame from where the camera stands out to their side; back to their own look after the blow.
     */
    public static dev.sangwoo.celestialfall.client.CinematicDirector.Pose camera(dev.sangwoo.celestialfall.client.CinematicDirector.Pose game,float delta){
        var c=own();var mc=net.minecraft.client.MinecraftClient.getInstance();if(c==null||game==null||mc.player==null)return game;
        float sec=c.age(delta)/20f,w=framed(sec);if(w<=1e-4f)return game;
        Vec3d f=Vec3d.fromPolar(0,c.yaw());
        Vec3d v=mc.player.getLerpedPos(delta).add(f.multiply(2.6)).add(0,1.3+.8*raised(sec),0).subtract(game.position());
        float yaw=(float)Math.toDegrees(Math.atan2(-v.x,v.z)),pitch=(float)-Math.toDegrees(Math.atan2(v.y,Math.hypot(v.x,v.z)));
        return new dev.sangwoo.celestialfall.client.CinematicDirector.Pose(game.position(),game.yaw()+MathHelper.wrapDegrees(yaw-game.yaw())*w,game.pitch()+(pitch-game.pitch())*w,game.roll());
    }

    // ───────────────────────────── the effects ─────────────────────────────

    static final int INK=DanceBurst.INK,DEEP=DanceBurst.DEEP,PURPLE=DanceBurst.PURPLE,GLOW=DanceBurst.GLOW,LILAC=0xe2b8ff,WHITE=0xffffff;
    /** The wheel's band behind the knife's point: from the leap, this long a tail (seconds), these parts of the knife (blocks from the fist), its edge this wide to the camera. */
    static final float WHEEL0=LEAP+.04f,TAIL=.15f,COOL0=.05f,COOL1=.34f,EAT0=.1f,EAT1=.42f;
    static final double BAND_IN=REACH*.56,BAND_OUT=REACH*.99,EDGE=.7;
    /** The scar the blow leaves along the ground: from here to here ahead of its user (blocks), this long (seconds). */
    static final double SCAR0=.9,SCAR1=7.3;static final float SCAR_LIFE=1.1f;
    static final int SPIKES=12,PIXELS=30;

    static Vec3d[] blade(Vec3d feet,float yaw,float sec){return KnifeCombo.blade(feet,yaw,HIT,sec);}
    /** The clip's time for the effects: no further than where one stopped short. */
    private static float until(Fx c){return Math.min(c.sec,KnifeClient.until(c.cast.id()));}
    private static boolean landed(Fx c){return c.sec>=BLOW&&KnifeClient.cameTo(c.cast,0);}

    /** The wheel's band (the effects' pass for X Chara's cuts). */
    static void cut(Fx c){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());
        if(t<WHEEL0||WHEEL0>=stop)return;
        float mark=Math.min(BLOW,stop),head=Math.min(t,mark+.012f),from=Math.max(WHEEL0,Math.min(head,mark)-TAIL);
        if(head<=from+.004f||t>=mark+EAT1)return;
        float eaten=Ease.smooth(t,mark+EAT0,mark+EAT1),cool=Ease.smooth(t,mark+COOL0,mark+COOL1),shown=KnifeCombo.own(c)*Ease.smooth(t,WHEEL0,WHEEL0+.04f);
        var sw=KnifeCombo.swept(KnifeCombo.spot(c,0),c.cast.yaw(),HIT,from,head,BAND_IN,BAND_OUT);
        KagenoFire.glitchRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),0,9,eaten,cool,shown);
        var e=KnifeCombo.edge(sw,c.camera,EDGE);
        KagenoFire.glitchRibbon(e.inner(),e.outer(),e.along(),e.n(),0,13,eaten,cool,shown);
    }
    private static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}

    /** Everything but the band, in one pass: bodies laid over what is behind them, or their light. */
    static void draw(Fx c,EffectMesh m,boolean light){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());int seed=KnifeCombo.seed(c,0);
        Vec3d feet=KnifeCombo.spot(c,0);Vec3d f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x),up=new Vec3d(0,1,0);
        float own=KnifeCombo.own(c);
        // the press: a ring off the panel's hand, a blink on the knife
        if(t>=PRESS&&t<PRESS+.3f&&PRESS<stop){
            float s=t-PRESS;var hb=blade(c.pos,c.cast.yaw(),Math.min(t,stop));
            if(light){
                m.softDisc(hb[0],.5+1.6*s,GLOW,.3f*(1-s/.3f)*own);
                m.star(hb[0].add(hb[1].multiply(KnifeCombo.TIP*.5)),.5,WHITE,.7f*(1-Ease.smooth(s,0,.12f))*own);
            }
        }
        // growing (and shrinking back): pixels thrown off along the knife
        for(int pass=0;pass<2;pass++){
            float from=pass==0?GROW0:SHRINK0;if(t<from||t>=from+.6f||from>=stop)continue;
            float s=t-from;var hb=blade(c.pos,c.cast.yaw(),Math.min(Math.min(t,from+.25f),stop));
            double len=KnifeCombo.TIP*(pass==0?scale(Math.min(t,GROW1)):SCALE);
            for(int j=0;j<8;j++){
                Vec3d at=hb[0].add(hb[1].multiply(len*(.12+.88*(j+.5)/8)));
                Vec3d way=up.multiply(.5+h(j,seed,31+pass)).add(right.multiply(h(j,seed,37+pass)*2-1)).add(hb[1].multiply(.6*(pass==0?1:-1))).normalize();
                GlitchFx.pixels(m,at,way,.9,2.6,.085,3,seed+j*17+pass*101,s-.02f*j,light,c.camera);
            }
            if(light&&s<.28f)m.needle(hb[0],hb[0].add(hb[1].multiply(len)),.22,.5,pass==0?WHITE:LILAC,.5f*(1-s/.28f)*own);
        }
        // the leap: a ring out over the ground under them, pixels kicked up
        if(t>=LEAP&&t<LEAP+.4f&&LEAP<stop){
            float s=t-LEAP;double r=.5+2.4*Ease.smooth(s,0,.32f);float a=(1-Ease.smooth(s,.12f,.4f))*Math.min(1,s/.03f);
            Vec3d g=feet.add(0,.04,0);
            if(light)FxLib.ring(m,g,r,.05,WHITE,.4f*a);else DanceBurst.band(m,g,new Vec3d(1,0,0),new Vec3d(0,0,1),r,.16,PURPLE,.7f*a);
            GlitchFx.pixels(m,g,up,1.2,3.2,.08,10,seed+7,s,light,c.camera);
        }
        if(!landed(c))return;
        float s=t-BLOW;
        // the scar along the ground under the knife: ink, purple, a white seam; eaten from its near end
        if(s<SCAR_LIFE){
            float open=Ease.smooth(s,0,.05f),gone=Ease.smooth(s,.45f,SCAR_LIFE);
            double a0=SCAR0+(SCAR1-SCAR0)*gone*.9,a1=SCAR0+(SCAR1-SCAR0)*open;int n=14;
            for(int i=0;i<n;i++){
                double u0=i/(double)n,u1=(i+1)/(double)n,z0=a0+(a1-a0)*u0,z1=a0+(a1-a0)*u1;if(z1<=z0)continue;
                double w0=width(z0),w1=width(z1);float fade=1-gone;
                Vec3d p0=feet.add(f.multiply(z0)).add(0,.035,0),p1=feet.add(f.multiply(z1)).add(0,.035,0);
                if(light){
                    quad(m,p0.add(0,.012,0),p1.add(0,.012,0),right,w0*.16,w1*.16,WHITE,.85f*fade*(1-Ease.smooth(s,.1f,.6f)));
                    quad(m,p0.add(0,.008,0),p1.add(0,.008,0),right,w0*1.5,w1*1.5,GLOW,.16f*fade);
                }else{
                    quad(m,p0,p1,right,w0*1.25+.05,w1*1.25+.05,INK,.85f*fade);
                    quad(m,p0.add(0,.004,0),p1.add(0,.004,0),right,w0,w1,DEEP,.95f*fade);
                    quad(m,p0.add(0,.008,0),p1.add(0,.008,0),right,w0*.55,w1*.55,PURPLE,.95f*fade);
                }
            }
        }
        // two rings out over the ground from under the knife's middle
        Vec3d mid=feet.add(f.multiply((SCAR0+SCAR1)/2)).add(0,.05,0);
        for(int k=0;k<2;k++){
            float rs=s-.05f*k,life=.42f+.1f*k;if(rs<0||rs>=life)continue;
            double r=1.0+(4.4+2.2*k)*Ease.smooth(rs,0,life*.9f);float a=Math.min(1,rs/.02f)*(1-Ease.smooth(rs,life*.35f,life));
            if(light)FxLib.ring(m,mid,r,.06,WHITE,.45f*a);else DanceBurst.band(m,mid,new Vec3d(1,0,0),new Vec3d(0,0,1),r,.24-.06*k,k==0?PURPLE:DEEP,.75f*a);
        }
        // spikes thrown up along the scar, to either side
        if(s<.3f)for(int j=0;j<SPIKES;j++){
            double z=SCAR0+(SCAR1-SCAR0)*(j+.5+.6*(h(j,seed,3)-.5))/SPIKES,side=j%2==0?1:-1;
            float ss=s-(float)(.035*z/SCAR1);if(ss<0)continue;
            Vec3d at=feet.add(f.multiply(z)).add(right.multiply(side*.15)).add(0,.05,0);
            Vec3d way=up.multiply(.9).add(right.multiply(side*(.35+.5*h(j,seed,5)))).add(f.multiply(.3*(h(j,seed,7)-.3))).normalize();
            double len=(1.3+1.7*h(j,seed,9))*Ease.smooth(ss,0,.05f);float fade=Math.min(1,ss/.015f)*(1-Ease.smooth(ss,.12f,.27f));
            Vec3d a=at.add(way.multiply(.1)),b=at.add(way.multiply(.1+len));double wide=.2+.1*h(j,seed,11);
            if(light){m.needle(a,b,wide*2,.25,GLOW,.14f*fade);if(j%3==0)m.needle(a,b,wide*.3,.25,WHITE,.8f*fade);}
            else{m.needle(a,b,wide*1.4,.25,INK,.85f*fade);m.needle(a,b,wide,.25,j%2==0?PURPLE:DEEP,.95f*fade);}
        }
        // pixels thrown up along it
        if(s<GlitchFx.PIXEL_LIFE+.2f)for(int j=0;j<6;j++){
            Vec3d at=feet.add(f.multiply(SCAR0+(SCAR1-SCAR0)*(j+.5)/6)).add(0,.1,0);
            GlitchFx.pixels(m,at,up,1.4,4.6,.1,PIXELS/6,seed+j*29,s-.02f*j,light,c.camera);
        }
        // the light of the blow where the knife's middle came down
        if(light&&s<.16f){m.softDisc(mid.add(0,.5,0),2.2,WHITE,.35f*(1-s/.16f)*own);m.star(mid.add(0,.5,0),2.6,WHITE,.6f*(1-s/.14f)*own);}
        // whom it struck: the knife's burst on them, as its finisher's
        if(s<SlashFx.SWIRL_LIFE)for(var e:KnifeCombo.struck(c,0))SlashFx.burst(m,e.mid(),f,HACK_LUNGE,seed+e.id(),s,light,c.camera);
    }
    /** The scar's half width at {@code z} blocks ahead: a blade's, widest a third along, to a point at its far end. */
    static double width(double z){
        double u=MathHelper.clamp((z-SCAR0)/(SCAR1-SCAR0),0,1);
        return .34*Math.pow(Math.sin(Math.PI*Math.pow(u,.6)),.7)+.02;
    }
    private static void quad(EffectMesh m,Vec3d a,Vec3d b,Vec3d side,double wa,double wb,int rgb,float alpha){
        if(alpha<=.003f)return;
        m.quad(a.subtract(side.multiply(wa)),b.subtract(side.multiply(wb)),b.add(side.multiply(wb)),a.add(side.multiply(wa)),rgb,alpha);
    }

    /** The light on the world; and little afterglow while the wheel turns (its band would leave copies of itself). */
    static void frame(Fx c,FxFrame fr){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());
        if(t>=WHEEL0&&t<BLOW+.1f)fr.afterglow(.06f);
        if(t<stop){
            float g=Ease.window(t,GROW0,SHRINK1,.1f,.15f);
            if(g>.01f){var hb=blade(c.pos,c.cast.yaw(),t);fr.light(hb[0].add(hb[1].multiply(KnifeCombo.TIP*scale(t)*.5)),0x8a2be2,7,.5f*g);}
        }
        if(landed(c)){
            float s=t-BLOW;Vec3d f=Vec3d.fromPolar(0,c.cast.yaw());
            if(s<.5f)fr.light(KnifeCombo.spot(c,0).add(f.multiply(4)).add(0,1,0),0x9a3cff,14,1.3f*(1-s/.5f));
            if(s<.3f)for(var e:KnifeCombo.struck(c,0))fr.light(e.mid(),0x9a3cff,8,1f*(1-s/.3f));
        }
    }
    /** How hard it shakes the camera: a touch as the knife grows, hard as it lands (harder on someone). */
    static float shake(StandClient.Cast cast,float sec){
        float grow=.2f*KnifeCombo.bump(sec,GROW1,.08f);
        if(!KnifeClient.cameTo(cast,0))return grow;
        int[] told=KnifeClient.struck(cast.id(),0);
        return grow+(told!=null&&told.length>0?2.0f:1.4f)*KnifeCombo.bump(sec,BLOW,.11f);
    }
}
