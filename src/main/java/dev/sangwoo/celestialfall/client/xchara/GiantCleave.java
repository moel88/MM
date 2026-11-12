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
 * 거대해지고 그걸 양손으로 잡고 덤블링 한 번 돌고 크게 내려찍는 스킬 맞추면 못 움직이는 1초 무력화"; 2.122.0, user request:
 * "덤블링을 돌지말고 그냥 제자리에서 거대한 검을 내려치게 하고 손잡이는 상관없고 칼날 부분을 엄청 키워줘 그리고 내려찍을때
 * 바닥에 박히는 효과를 엄청 강하게 만들어주고 무력화 시간을 3초로 늘려줘"; the server's XCharaAttack.cleave,
 * XCharaCombo.CLEAVE). It is a skill of the knife's pipeline (KnifeClient's swings, KnifeCombo's passes hand it on
 * here), so it has a hit's clock, its body clip, its news of whom it struck. There was no video of it: the motion and
 * the effects are mine to the user's words.
 * <ul>
 * <li>The press ({@link #PRESS}): the panel's arm comes up and its hand taps the panel once, lit violet
 * ({@link #pressAt}; the arm as their Y's press has it, XCharaIdle).</li>
 * <li>The knife grows: all of it a little ({@link #scale}, to {@link #ROD} times, so both hands have room on its rod)
 * and its blade much more on its own ({@link #blade}, {@link #BLADE} times that: XCharaKnifeRenderer.blade), a blade
 * some nine blocks long and over two deep. The knife's arm brings it up in front of them as it grows; the panel folds
 * away ({@link #panel}) and the other hand takes its rod behind the first ({@link #grip}: that arm set by where the rod
 * is, IsosangLimbs.leftHandAt).</li>
 * <li>The blow, where they stand (no leap, no somersault: 2.121.0's are gone): both hands raise it over their head and
 * a little back, the body arched under it ({@link #RAISED}), and bring it down ahead onto the ground ({@link #BLOW},
 * the server's), the hips first, the knife last. It bites into the ground along its whole length, its edge sunk a
 * block deep, and they hold it there, buried, until it shrinks back; the other hand lets go and the panel opens
 * again.</li>
 * </ul>
 * Their own camera stands off to their side and back for it and looks up at the blade ({@link #cameraPull},
 * {@link #cameraShoulder}, {@link #camera}). The effects are the hack's purple, drawn in the world with the knife's
 * passes: pixels thrown off the knife as it grows and as it shrinks, the band behind the blade as it comes down, and
 * the blow on the ground, made as strong as the user asked: the floor itself heaved up in slabs along both sides of the
 * blade and rippling out from it, and pieces of it thrown high ({@link #solids}: the real blocks drawn, none changed); a
 * long black trench under the blade, cracks running out from it, rings over the ground, tall spikes, pixels, a white
 * flash, and the knife's burst on whom it struck. No screen effect but the shake. Everything is a function of the
 * skill's own clock.
 */
public final class GiantCleave {
    private GiantCleave(){}
    static final int HIT=CLEAVE;
    /** When the panel is pressed, when the knife is grown and in both hands, when it is raised over their head, when the blow lands (seconds: the server's ticks), and its motion's length. */
    static final float PRESS=CLEAVE_PRESS/20f,GROWN=CLEAVE_GROWN/20f,RAISED=CLEAVE_RAISED/20f,BLOW=blowAt(CLEAVE,0)/20f,LENGTH_S=LENGTH[CLEAVE]/20f;
    /** How long one is drawn for (seconds): what the blow leaves on the ground is gone by then. */
    public static final float LIFE=BLOW+2.4f;
    /** How many times its size the whole knife grows to (its rod: room for both hands), how many times that its blade grows on its own, and how far they swell over that on the way. */
    public static final float ROD=1.6f,BLADE=2.5f,OVER=.4f;
    /** The knife grows from here to here; it shrinks back from here to here (seconds). */
    static final float GROW0=PRESS+.02f,GROW1=GROWN-.03f,SHRINK0=BLOW+.6f,SHRINK1=BLOW+.8f;
    /** The other hand comes onto the rod from here to here and lets go from here to here; it holds the rod this far behind the first (blocks). */
    static final float GRIP0=.32f,GRIP1=.43f,FREE0=BLOW+.6f,FREE1=BLOW+.74f;
    static final double GRIP_BACK=.22;
    /** The panel folds away as the other hand leaves it and opens again as the hand comes back (seconds). */
    static final float PANEL_OFF=.31f,PANEL_ON=BLOW+.7f;
    /** The grown knife from the fist: to its blade's heel, to its point (blocks), and how deep its blade is, spine to edge. */
    static final double HEEL=KnifeCombo.HEEL*ROD,REACH=HEEL+(KnifeCombo.TIP-KnifeCombo.HEEL)*ROD*BLADE,DEEP_BLADE=.55*ROD*BLADE;

    // ───────────────────────────── the knife's size ─────────────────────────────

    /** How far grown the knife is {@code sec} seconds in (0 its own size, 1 grown; a little over on the way): just after the press, back after the blow. */
    static float grown(float sec){
        float u=MathHelper.clamp((sec-GROW0)/(GROW1-GROW0),0,1);
        // out past its size and back onto it
        return (u*u*(3-2*u)+OVER*(float)Math.sin(Math.PI*u)*u)*(1-Ease.smooth(sec,SHRINK0,SHRINK1));
    }
    /** The same for one stopped short {@code stopped} seconds in (NaN: not): back to its size at once from what it was. */
    static float grown(float sec,float stopped){
        if(Float.isNaN(stopped)||sec<=stopped)return grown(sec);
        return grown(stopped)*(1-Ease.smooth(sec,stopped,stopped+.12f));
    }
    /** How many times its size the whole knife is then (its rod with it). */
    public static float scale(float sec){return 1+(ROD-1)*grown(sec);}
    public static float scale(float sec,float stopped){return 1+(ROD-1)*grown(sec,stopped);}
    /** How many times that its blade is on its own then. */
    public static float blade(float sec){return 1+(BLADE-1)*grown(sec);}
    public static float blade(float sec,float stopped){return 1+(BLADE-1)*grown(sec,stopped);}
    /** From the fist to the knife's point then (blocks). */
    static double reach(float sec){double rod=scale(sec);return KnifeCombo.HEEL*rod+(KnifeCombo.TIP-KnifeCombo.HEEL)*rod*blade(sec);}
    /** The knife's size in this player's hand now (1 if they are in no cleave). */
    public static float scale(UUID id,float delta){
        var c=KnifeClient.cleaveOf(id);if(c==null)return 1;
        return scale(c.age(delta)/20f,stopped(c));
    }
    /** Its blade's own size in this player's hand now (1 if they are in no cleave). */
    public static float blade(UUID id,float delta){
        var c=KnifeClient.cleaveOf(id);if(c==null)return 1;
        return blade(c.age(delta)/20f,stopped(c));
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

    /** The body leant {@code pitch} forward (degrees), its arm written in its own space, its knife by its way in the world (its elevation, ahead: over 90 it leans back over their head). */
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
    private static final double[] KNIFE_ARM=d(-76,90);
    /**
     * The clip: they stand where they are through all of it (2.122.0: no leap, no somersault), the left foot ahead and
     * the right behind as the knife goes up, the weight going back under it and coming forward and down with the blow.
     */
    static final Anim CLIP=Anim.joints(
        k(0,KnifeCombo.STANCE),
        // the press: they look down at the panel
        k(.10,plant(-.02,3,KNIFE_ARM,0,24,.17,0,-.17,0)),
        k(PRESS,plant(-.03,4,KNIFE_ARM,0,30,.17,0,-.17,0)),
        // the knife brought up in front as it grows, their eyes going up it
        k(.30,plant(-.02,-2,d(-30,26),58,-14,.18,-.03,-.18,.05)),
        k(.38,plant(-.02,-4,d(-24,-12),74,-22,.19,-.08,-.19,.12)),
        // grown: upright before them in both hands
        k(GROWN,plant(-.03,-3,d(-18,-38),84,-26,.20,-.14,-.20,.20)),
        // raised: up over their head and a little back, the body arched under it, the weight on the back foot
        k(.56,plant(.0,-8,d(28,-34),96,-24,.20,-.18,-.20,.24)),
        k(.66,plant(.03,-13,d(56,-26),108,-20,.20,-.20,-.20,.26)),
        k(RAISED,plant(.04,-15,d(62,-24),113,-18,.20,-.20,-.20,.26)),
        k(.76,plant(.04,-15,d(63,-24),115,-16,.20,-.20,-.20,.26)),
        // let go: the hips and the body first, the arms after them, the knife last
        k(.82,plant(.0,-6,d(58,-26),108,-6,.20,-.20,-.20,.26)),
        k(.89,plant(-.10,8,d(34,-32),80,6,.20,-.20,-.20,.28)),
        k(.95,plant(-.18,16,d(4,-38),36,12,.20,-.22,-.20,.30)),
        // the blow: the knife along the ground ahead, its edge sunk in it
        k(BLOW,plant(-.22,20,d(-14,-42),-1.5,16,.21,-.24,-.20,.32)),
        k(BLOW+.07f,plant(-.30,24,d(-18,-42),-2,18,.21,-.24,-.20,.32)),
        // held there, buried
        k(BLOW+.30f,plant(-.27,22,d(-16,-42),-1.8,14,.21,-.24,-.20,.32)),
        k(BLOW+.58f,plant(-.26,21,d(-16,-42),-1.8,12,.21,-.24,-.20,.32)),
        // up again: the knife its own size, the hands apart
        k(BLOW+.74f,plant(-.16,10,d(-48,-10),2,6,.19,-.12,-.19,.16)),
        k(BLOW+.88f,plant(-.04,3,d(-70,60),1,0,.17,-.03,-.17,.04)),
        k(LENGTH_S,KnifeCombo.STANCE)).stops(Anim.stop(BLOW,.1,.1)).after(GiantCleave::fix);

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

    private static StandClient.Cast own(){
        var mc=net.minecraft.client.MinecraftClient.getInstance();if(mc.player==null)return null;
        var c=KnifeClient.cleaveOf(mc.player.getUuid());return c==null||!Float.isNaN(stopped(c))?null:c;
    }
    /** How much of the camera's own framing of it is there {@code sec} seconds in: from the knife's growing until a while after the blow. */
    static float framed(float sec){return Ease.smooth(sec,GROW0,GROWN+.1f)*(1-Ease.smooth(sec,BLOW+.5f,BLOW+.95f));}
    /** How far the local player's own camera draws back for the grown knife and the blow (blocks). */
    public static double cameraPull(float delta){var c=own();return c==null?0:6.5*framed(c.age(delta)/20f);}
    /**
     * How far it stands out to their right (blocks, as the shift lock's shoulder): the knife comes down in the plane
     * they face along, which the camera right behind them is in (it would be a line); from over to the side the blade
     * is seen whole.
     */
    public static double cameraShoulder(float delta){var c=own();return c==null?0:4.6*framed(c.age(delta)/20f);}
    /**
     * The local player's camera through their cleave: turned from the way they look onto it (a point a little ahead of
     * them, as high as the raised blade's middle while it is up and down with it), so the whole blade and where it
     * lands stay in the frame from where the camera stands out to their side; back to their own look after the blow.
     */
    public static dev.sangwoo.celestialfall.client.CinematicDirector.Pose camera(dev.sangwoo.celestialfall.client.CinematicDirector.Pose game,float delta){
        var c=own();var mc=net.minecraft.client.MinecraftClient.getInstance();if(c==null||game==null||mc.player==null)return game;
        float sec=c.age(delta)/20f,w=framed(sec);if(w<=1e-4f)return game;
        Vec3d f=Vec3d.fromPolar(0,c.yaw());
        double high=1.4+3.4*Ease.smooth(sec,GROW0,GROWN)*(1-Ease.smooth(sec,RAISED+.1f,BLOW+.05f));
        Vec3d v=mc.player.getLerpedPos(delta).add(f.multiply(3.4)).add(0,high,0).subtract(game.position());
        float yaw=(float)Math.toDegrees(Math.atan2(-v.x,v.z)),pitch=(float)-Math.toDegrees(Math.atan2(v.y,Math.hypot(v.x,v.z)));
        return new dev.sangwoo.celestialfall.client.CinematicDirector.Pose(game.position(),game.yaw()+MathHelper.wrapDegrees(yaw-game.yaw())*w,game.pitch()+(pitch-game.pitch())*w,game.roll());
    }

    // ───────────────────────────── the effects ─────────────────────────────

    static final int INK=DanceBurst.INK,DEEP=DanceBurst.DEEP,PURPLE=DanceBurst.PURPLE,GLOW=DanceBurst.GLOW,LILAC=0xe2b8ff,WHITE=0xffffff;
    /** The band behind the blade as it comes down: from here, this long a tail (seconds), these parts of the knife (blocks from the fist), its edge this wide to the camera. */
    static final float CHOP0=.79f,TAIL=.14f,COOL0=.06f,COOL1=.4f,EAT0=.12f,EAT1=.5f;
    static final double BAND_IN=REACH*.42,BAND_OUT=REACH*.99,EDGE=.8;
    /** The trench the blow leaves along the ground under the blade: from here to here ahead of its user (blocks), this long (seconds: while the blade is in it, then closing). */
    static final double SCAR0=.7,SCAR1=REACH+.5;static final float SCAR_LIFE=2.3f;
    /** The floor heaved up along the blade and rippling out from it, and its pieces thrown: this long (seconds), the slabs within this of the blade (blocks), the ripple out to this. */
    static final float GROUND_LIFE=2.2f,SINK0=.75f,SINK1=1.6f;
    static final double SLABS=1.9,RIPPLE=7.5;
    static final int SPIKES=22,PIXELS=48,CRACKS=22,CHUNKS=46;

    static Vec3d[] blade(Vec3d feet,float yaw,float sec){return KnifeCombo.blade(feet,yaw,HIT,sec);}
    private static boolean landed(Fx c){return c.sec>=BLOW&&KnifeClient.cameTo(c.cast,0);}

    /** The band behind the blade as it comes down (the effects' pass for X Chara's cuts). */
    static void cut(Fx c){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());
        if(t<CHOP0||CHOP0>=stop)return;
        float mark=Math.min(BLOW,stop),head=Math.min(t,mark+.012f),from=Math.max(CHOP0,Math.min(head,mark)-TAIL);
        if(head<=from+.004f||t>=mark+EAT1)return;
        float eaten=Ease.smooth(t,mark+EAT0,mark+EAT1),cool=Ease.smooth(t,mark+COOL0,mark+COOL1),shown=KnifeCombo.own(c)*Ease.smooth(t,CHOP0,CHOP0+.04f);
        var sw=KnifeCombo.swept(KnifeCombo.spot(c,0),c.cast.yaw(),HIT,from,head,BAND_IN,BAND_OUT);
        KagenoFire.glitchRibbon(sw.inner(),sw.outer(),sw.along(),sw.n(),0,9,eaten,cool,shown);
        var e=KnifeCombo.edge(sw,c.camera,EDGE);
        KagenoFire.glitchRibbon(e.inner(),e.outer(),e.along(),e.n(),0,13,eaten,cool,shown);
    }
    private static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}

    /** Everything but the band and the floor, in one pass: bodies laid over what is behind them, or their light. */
    static void draw(Fx c,EffectMesh m,boolean light){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());int seed=KnifeCombo.seed(c,0);
        Vec3d feet=KnifeCombo.spot(c,0);Vec3d f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x),up=new Vec3d(0,1,0);
        float own=KnifeCombo.own(c);
        // the press: a blink on the knife
        if(t>=PRESS&&t<PRESS+.3f&&PRESS<stop){
            float s=t-PRESS;var hb=blade(c.pos,c.cast.yaw(),Math.min(t,stop));
            if(light){
                m.softDisc(hb[0],.5+1.6*s,GLOW,.3f*(1-s/.3f)*own);
                m.star(hb[0].add(hb[1].multiply(KnifeCombo.TIP*.5)),.5,WHITE,.7f*(1-Ease.smooth(s,0,.12f))*own);
            }
        }
        // growing (and shrinking back): pixels thrown off all along the blade
        for(int pass=0;pass<2;pass++){
            float from=pass==0?GROW0:SHRINK0;if(t<from||t>=from+.6f||from>=stop)continue;
            float s=t-from;var hb=blade(c.pos,c.cast.yaw(),Math.min(Math.min(t,from+.25f),stop));
            double len=pass==0?reach(Math.min(t,GROW1)):REACH;
            for(int j=0;j<12;j++){
                Vec3d at=hb[0].add(hb[1].multiply(len*(.1+.9*(j+.5)/12)));
                Vec3d way=up.multiply(.4+h(j,seed,31+pass)).add(right.multiply(h(j,seed,37+pass)*2-1)).add(hb[1].multiply(.6*(pass==0?1:-1))).normalize();
                GlitchFx.pixels(m,at,way,.9,3.0,.1,3,seed+j*17+pass*101,s-.015f*j,light,c.camera);
            }
            if(light&&s<.3f)m.needle(hb[0],hb[0].add(hb[1].multiply(len)),.4,.5,pass==0?WHITE:LILAC,.5f*(1-s/.3f)*own);
        }
        // raised: a glint running up the blade to its point
        if(light&&t>=RAISED-.1f&&t<RAISED+.14f&&RAISED<stop){
            float s=t-(RAISED-.1f);var hb=blade(c.pos,c.cast.yaw(),t);
            Vec3d at=hb[0].add(hb[1].multiply(REACH*Ease.smooth(s,0,.16f)));
            m.star(at,1.1,WHITE,.8f*Math.min(1,s/.03f)*(1-Ease.smooth(s,.14f,.24f))*own);
        }
        if(!landed(c))return;
        float s=t-BLOW;
        // the trench along the ground under the blade: black, a purple lip, a white seam as it is cut; closing from its near end once the blade is out
        if(s<SCAR_LIFE){
            float open=Ease.smooth(s,0,.04f),gone=Ease.smooth(s,SINK0+.2f,SCAR_LIFE);
            double a0=SCAR0+(SCAR1-SCAR0)*gone*.9,a1=SCAR0+(SCAR1-SCAR0)*open;int n=18;
            for(int i=0;i<n;i++){
                double u0=i/(double)n,u1=(i+1)/(double)n,z0=a0+(a1-a0)*u0,z1=a0+(a1-a0)*u1;if(z1<=z0)continue;
                double w0=width(z0),w1=width(z1);float fade=1-gone;
                Vec3d p0=feet.add(f.multiply(z0)).add(0,.035,0),p1=feet.add(f.multiply(z1)).add(0,.035,0);
                if(light){
                    quad(m,p0.add(0,.014,0),p1.add(0,.014,0),right,w0*.14,w1*.14,WHITE,.9f*fade*(1-Ease.smooth(s,.12f,.7f)));
                    quad(m,p0.add(0,.01,0),p1.add(0,.01,0),right,w0*1.7,w1*1.7,GLOW,.2f*fade*(1-.6f*Ease.smooth(s,.2f,1.2f)));
                }else{
                    quad(m,p0,p1,right,w0*1.5+.08,w1*1.5+.08,DEEP,.9f*fade);
                    quad(m,p0.add(0,.004,0),p1.add(0,.004,0),right,w0*1.2+.04,w1*1.2+.04,PURPLE,.95f*fade*(1-.5f*Ease.smooth(s,.2f,1f)));
                    quad(m,p0.add(0,.008,0),p1.add(0,.008,0),right,w0,w1,INK,.96f*fade);
                }
            }
        }
        // cracks running out from the trench over the ground: ink, lit purple a moment
        if(s<SCAR_LIFE)for(int j=0;j<CRACKS;j++){
            double z=SCAR0+(SCAR1-SCAR0)*(j+.5+.7*(h(j,seed,41)-.5))/CRACKS,side=j%2==0?1:-1;
            float ss=s-(float)(.02*h(j,seed,43));if(ss<0)continue;
            double turn=Math.toRadians(side*(90+50*(h(j,seed,45)-.5))),len=(1.8+3.2*h(j,seed,47))*Ease.smooth(ss,0,.09f);
            Vec3d at=feet.add(f.multiply(z)).add(right.multiply(side*width(z)*.8)).add(0,.045,0);
            float fade=1-Ease.smooth(s,SINK0+.3f,SCAR_LIFE);
            for(int k=0;k<3;k++){
                // (each crack: three legs, turning a little at each)
                Vec3d way=f.multiply(Math.cos(turn)).add(right.multiply(Math.sin(turn)));
                Vec3d b=at.add(way.multiply(len*(k==0?.42:k==1?.33:.25))),across=new Vec3d(-way.z,0,way.x);
                double wide=.16*(1-.28*k);
                if(light)m.needle(at.add(0,.006,0),b.add(0,.006,0),across,wide*.5,.2,PURPLE,.7f*fade*(1-Ease.smooth(ss,.1f,.7f)));
                else m.needle(at,b,across,wide,.2,INK,.92f*fade);
                at=b;turn+=Math.toRadians(70*(h(j*3+k,seed,49)-.5));
            }
        }
        // three rings out over the ground from under the blade's middle
        Vec3d mid=feet.add(f.multiply((SCAR0+SCAR1)/2)).add(0,.05,0);
        for(int k=0;k<3;k++){
            float rs=s-.05f*k,life=.45f+.12f*k;if(rs<0||rs>=life)continue;
            double r=1.2+(5.2+3.2*k)*Ease.smooth(rs,0,life*.9f);float a=Math.min(1,rs/.02f)*(1-Ease.smooth(rs,life*.35f,life));
            if(light)FxLib.ring(m,mid,r,.08,WHITE,.5f*a);else DanceBurst.band(m,mid,new Vec3d(1,0,0),new Vec3d(0,0,1),r,.34-.08*k,k==1?DEEP:PURPLE,.78f*a);
        }
        // tall spikes thrown up along the trench, to either side
        if(s<.36f)for(int j=0;j<SPIKES;j++){
            double z=SCAR0+(SCAR1-SCAR0)*(j+.5+.6*(h(j,seed,3)-.5))/SPIKES,side=j%2==0?1:-1;
            float ss=s-(float)(.03*z/SCAR1);if(ss<0)continue;
            Vec3d at=feet.add(f.multiply(z)).add(right.multiply(side*.2)).add(0,.05,0);
            Vec3d way=up.multiply(.9).add(right.multiply(side*(.3+.6*h(j,seed,5)))).add(f.multiply(.3*(h(j,seed,7)-.3))).normalize();
            double len=(2.0+3.4*h(j,seed,9))*Ease.smooth(ss,0,.05f);float fade=Math.min(1,ss/.015f)*(1-Ease.smooth(ss,.14f,.33f));
            Vec3d a=at.add(way.multiply(.1)),b=at.add(way.multiply(.1+len));double wide=.3+.16*h(j,seed,11);
            if(light){m.needle(a,b,wide*2,.25,GLOW,.14f*fade);if(j%3==0)m.needle(a,b,wide*.3,.25,WHITE,.8f*fade);}
            else{m.needle(a,b,wide*1.4,.25,INK,.85f*fade);m.needle(a,b,wide,.25,j%2==0?PURPLE:DEEP,.95f*fade);}
        }
        // pixels thrown up along it
        if(s<GlitchFx.PIXEL_LIFE+.3f)for(int j=0;j<8;j++){
            Vec3d at=feet.add(f.multiply(SCAR0+(SCAR1-SCAR0)*(j+.5)/8)).add(0,.1,0);
            GlitchFx.pixels(m,at,up,1.6,6.0,.12,PIXELS/8,seed+j*29,s-.02f*j,light,c.camera);
        }
        // the white of the blow along the blade
        if(light&&s<.18f)for(int k=0;k<3;k++){
            Vec3d at=feet.add(f.multiply(SCAR0+(SCAR1-SCAR0)*(.2+.3*k))).add(0,.7,0);
            m.softDisc(at,2.6,WHITE,.3f*(1-s/.18f)*own);m.star(at,3.0-.5*k,WHITE,.6f*(1-s/.16f)*own);
        }
        // whom it struck: the knife's burst on them, as its finisher's
        if(s<SlashFx.SWIRL_LIFE)for(var e:KnifeCombo.struck(c,0))SlashFx.burst(m,e.mid(),f,HACK_LUNGE,seed+e.id(),s,light,c.camera);
    }
    /** The trench's half width at {@code z} blocks ahead: a blade's, widest a third along, to a point at its far end. */
    static double width(double z){
        double u=MathHelper.clamp((z-SCAR0)/(SCAR1-SCAR0),0,1);
        return .42*Math.pow(Math.sin(Math.PI*Math.pow(u,.6)),.7)+.03;
    }
    private static void quad(EffectMesh m,Vec3d a,Vec3d b,Vec3d side,double wa,double wb,int rgb,float alpha){
        if(alpha<=.003f)return;
        m.quad(a.subtract(side.multiply(wa)),b.subtract(side.multiply(wb)),b.add(side.multiply(wb)),a.add(side.multiply(wa)),rgb,alpha);
    }

    // ───────────────────────────── the floor ─────────────────────────────

    /**
     * How high the floor block whose middle is {@code z} blocks ahead of the user and {@code x} to their right is heaved
     * {@code s} seconds after the blow (blocks), and how far it is tipped away from the blade (degrees, to the side it
     * is on), as {lift, tilt}. Along the blade ({@link #SLABS} to either side of it) the floor is thrown up in slabs at
     * once, highest by the blade, and stays up while the blade is in the ground, sinking back after ({@link #SINK0} to
     * {@link #SINK1}); further out a ripple runs away from the blade ({@link #RIPPLE}). {@code h} and {@code h2} (0 to
     * 1) are the block's own.
     */
    static double[] heave(double z,double x,float s,double h,double h2){
        if(s<=0||s>=GROUND_LIFE)return new double[]{0,0};
        double d=Math.abs(x);
        if(z>=.4&&z<=SCAR1+.3&&d<SLABS){
            double rise=Ease.smooth(s,(float)(.03*z/SCAR1),(float)(.03*z/SCAR1)+.06f),stay=1-.22*Ease.smooth(s,.12f,.5f),down=1-Ease.smooth(s,SINK0,SINK1);
            double near=1-d/SLABS,lift=(.22+.5*near+.22*h)*(d<.5?.4:1);
            return new double[]{lift*rise*stay*down,Math.signum(x)*(12+20*h2)*near*rise*down};
        }
        if(z<-1.5||z>SCAR1+2.5||d>RIPPLE)return new double[]{0,0};
        return new double[]{dev.sangwoo.celestialfall.client.vfx.FxSolids.wave(d,s,.5,15,1.1)*(.7+.6*h),0};
    }
    /** One piece of the floor thrown by the blow, {@code s} seconds after it, the ground at {@code ground}: {where it is, its size, how far it has spun (degrees)}, or null once it is gone. */
    static double[] chunk(Vec3d feet,Vec3d f,Vec3d right,int j,int seed,float s,double ground){
        float ss=s-.012f*(j%6);if(ss<0||ss>=1.9f)return null;
        double z=SCAR0+(SCAR1-SCAR0)*h(j,seed,61),side=j%2==0?1:-1;
        double size=(.18+.32*h(j,seed,63))*(1-Ease.smooth(ss,1.4f,1.9f));
        Vec3d from=feet.add(f.multiply(z)).add(right.multiply(side*.35)).add(0,.2,0);
        Vec3d v=right.multiply(side*(1.2+5.5*h(j,seed,65))).add(0,6.5+7.5*h(j,seed,67),0).add(f.multiply(3.5*(h(j,seed,69)-.45)));
        Vec3d at=dev.sangwoo.celestialfall.client.vfx.FxSolids.flight(from,v,ground+size*.5,ss);
        return new double[]{at.x,at.y,at.z,size,(300+500*h(j,seed,71))*Math.min(ss,1.1)};
    }
    /** The floor itself in the blow: its blocks along the blade drawn heaved up and tipped, a ripple of them running out, and pieces of it thrown (drawn only: no block is changed). */
    static void solids(Fx c,dev.sangwoo.celestialfall.client.vfx.FxSolids so){
        if(!landed(c))return;
        float s=c.sec-BLOW;if(s>=GROUND_LIFE)return;
        Vec3d feet=KnifeCombo.spot(c,0),f=Vec3d.fromPolar(0,c.cast.yaw()),right=new Vec3d(-f.z,0,f.x);int seed=KnifeCombo.seed(c,0);
        int y=(int)Math.floor(feet.y-.5),reach=(int)Math.ceil(SCAR1+RIPPLE+2);
        int bx=(int)Math.floor(feet.x),bz=(int)Math.floor(feet.z);
        for(int ix=bx-reach;ix<=bx+reach;ix++)for(int iz=bz-reach;iz<=bz+reach;iz++){
            Vec3d d=new Vec3d(ix+.5-feet.x,0,iz+.5-feet.z);
            double z=d.dotProduct(f),x=d.dotProduct(right);
            if(z<-1.5||z>SCAR1+2.5||Math.abs(x)>RIPPLE)continue;
            var hv=heave(z,x,s,Ease.hash(ix*7349+iz*911),Ease.hash(ix*131+iz*5923+17));
            if(hv[0]<=.02)continue;
            var pos=new net.minecraft.util.math.BlockPos(ix,y,iz);var state=so.surface(pos);if(state==null)continue;
            so.lifted(pos,state,hv[0],(float)hv[1],f);
        }
        var ground=so.ground(feet.add(f.multiply(2)).add(0,-.5,0));if(ground==null)return;
        for(int j=0;j<CHUNKS;j++){
            var k=chunk(feet,f,right,j,seed,s,feet.y);if(k==null||k[3]<=.02)continue;
            so.chunk(ground,new Vec3d(k[0],k[1],k[2]),k[3],(float)k[4],(float)(k[4]*.7),(float)(k[4]*.4));
        }
    }

    /** The light on the world; and little afterglow while the blade comes down (its band would leave copies of itself). */
    static void frame(Fx c,FxFrame fr){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());
        if(t>=CHOP0&&t<BLOW+.1f)fr.afterglow(.06f);
        if(t<stop){
            float g=Ease.window(t,GROW0,SHRINK1,.1f,.15f);
            if(g>.01f){var hb=blade(c.pos,c.cast.yaw(),t);fr.light(hb[0].add(hb[1].multiply(reach(t)*.5)),0x8a2be2,9,.55f*g);}
        }
        if(landed(c)){
            float s=t-BLOW;Vec3d f=Vec3d.fromPolar(0,c.cast.yaw());
            if(s<.6f)fr.light(KnifeCombo.spot(c,0).add(f.multiply(5)).add(0,1,0),0x9a3cff,20,1.8f*(1-s/.6f));
            if(s<.3f)for(var e:KnifeCombo.struck(c,0))fr.light(e.mid(),0x9a3cff,8,1f*(1-s/.3f));
        }
    }
    /** How hard it shakes the camera: a touch as the knife grows, very hard as it lands (harder on someone). */
    static float shake(StandClient.Cast cast,float sec){
        float grow=.25f*KnifeCombo.bump(sec,GROW1,.08f);
        if(!KnifeClient.cameTo(cast,0))return grow;
        int[] told=KnifeClient.struck(cast.id(),0);
        return grow+(told!=null&&told.length>0?3.2f:2.6f)*KnifeCombo.bump(sec,BLOW,.16f);
    }
}
