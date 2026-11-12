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
import dev.sangwoo.celestialfall.combat.MotionPose;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * X Chara's R with their hack off, the jab, drawn (2.124.0, user request with their video of it: "이번에는 q비활성화 r
 * 만들자 효과음은 띡 소리 나고 찌르는 효과음이고 스킬은 영상 처럼 검이 잠시 q활성화 상태의 빛나는 검으로 변하고 적을 찔러
 * 적의 얼굴에 플레이어 화면을 따라오는 보라색 X자가 생기고 적은 8초동안 아무것도 못하고 가만히 서 있어야해"; what I read
 * of the video they confirmed with one change, "다 맞는데 적을 찌르면 칼은 곧바로 검은 칼로"; the server's
 * XCharaAttack.jab, XCharaCombo.JAB). A skill of the knife's pipeline as the cleave is (KnifeClient's swings,
 * KnifeCombo's passes hand it on here).
 * <ul>
 * <li>The knife: from {@link #LIT} it is the hack's knife, white within its glowing purple rim, coming on as the hack's
 * does ({@link #knife}: HackMode.knifeLook, so everywhere the knife is drawn, and the hack's light and pixels about
 * it); the moment the blow lands it is black again, at once (the user's word).</li>
 * <li>The turn ({@link #TWIRL0} to {@link #TWIRL1}): the video turns the knife once round in the hand. Theirs is a
 * short one; ours is three blocks long and would go through the ground turned by a hand at the hip, so the arm goes
 * up ahead and the knife turns once on the raised fist like a propeller, about the forearm ({@link #spin}: the knife
 * turned in the hand as it is drawn, the arm still), its low side ahead of them and clear of the ground, its high side
 * back over their head.</li>
 * <li>The thrust: the knife drawn back low past the right hip with its point on the foe ({@link #DRAWN}), then the
 * lunge at their face ({@link #BLOW}, the server's), the body behind it, as the fourth swing's but shorter and
 * higher.</li>
 * <li>On whom it strikes: a white burst of long rays on their face and a few purple spikes ({@link #draw}); and the
 * X ({@link #mark}): white within purple, of square pixels, before their face and turned to whoever looks (the
 * user's "플레이어 화면을 따라오는"), as long as they stand there unable to do anything (the server's JAB_STUN, eight
 * seconds). The X is the mark of it: the stars of an incapacitation are not drawn over them, and they stand as they
 * are, not swaying ({@link #marked}).</li>
 * </ul>
 * No screen effect but a small shake. Everything is a function of the skill's own clock, or of the mark's.
 */
public final class CorruptionJab {
    private CorruptionJab(){}
    /** When the knife lights, when its turn begins and is over, when it is drawn back, when the blow lands (seconds: the server's ticks), and its motion's length. */
    static final float LIT=JAB_LIT/20f,TWIRL0=JAB_TWIRL/20f,TWIRL1=JAB_TWIRLED/20f,DRAWN=JAB_DRAWN/20f,BLOW=blowAt(JAB,0)/20f,LENGTH_S=LENGTH[JAB]/20f;
    /** How long one is drawn for (seconds): what the blow leaves on their face is gone by then. */
    public static final float LIFE=LENGTH_S+.3f;

    // ───────────────────────────── the knife ─────────────────────────────

    static float stopped(StandClient.Cast c){return GiantCleave.stopped(c);}
    /**
     * The knife's look {@code sec} seconds into a jab (stopped short {@code stop} seconds in, NaN: not), as the hack's
     * {on, seconds since it changed}, or null while it is its own black: lit from LIT as the hack comes on, black at
     * once as the blow lands; one stopped short before that goes out as the hack does.
     */
    static float[] lit(float sec,float stop){
        if(sec<LIT)return null;
        if(!Float.isNaN(stop)&&sec>stop){
            if(stop<LIT||stop>=BLOW)return null;
            float t=sec-stop;return t<HackLook.OFF?new float[]{0,t}:null;
        }
        return sec<BLOW?new float[]{1,sec-LIT}:null;
    }
    /** This player's knife as their jab has it this frame (the hack's look, HackMode.knifeLook), or null if they are in none or it is black. */
    public static HackLook.Frame knife(UUID id,float delta){
        var c=KnifeClient.jabOf(id);if(c==null)return null;
        float[] l=lit(c.age(delta)/20f,stopped(c));if(l==null)return null;
        return new HackLook.Frame(l[0]>0,l[1],HackMode.clock(delta)/20f,(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>17));
    }
    /** How far round the knife has turned in the hand {@code sec} seconds in (degrees): once round, quickest in the middle of it. */
    static float spinAt(float sec){
        float u=MathHelper.clamp((sec-TWIRL0)/(TWIRL1-TWIRL0),0,1);
        return SPIN*360*(.35f*u+.65f*u*u*(3-2*u));
    }
    /** Which way round it turns about the forearm: its point from up over their head to their right, down ahead of them, to their left and up again (the video's, seen from behind: up, right, down, left). */
    static final float SPIN=-1;
    /** The knife's turn in this player's hand now (degrees about the forearm; 0 if they are in no jab): StandHeldItemFeatureMixin. */
    public static float spin(UUID id,float delta){
        var c=KnifeClient.jabOf(id);if(c==null||!Float.isNaN(stopped(c)))return 0;
        float s=spinAt(c.age(delta)/20f);return Math.abs(s)>=360?0:s;
    }
    /** The knife's way in body space for a frame of the clip with the knife turned {@code spin} degrees in the hand (R6.bladeOf with the turn about the forearm before the wrist's). */
    static R6.Dir spun(R6.Frame f,float spin){
        float[] e=f.joints().joint[MotionPose.RIGHT_ARM];float k=(float)Math.PI/180;
        var q=new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k).rotateX(f.joints().elbow[0]*k).rotateX(-90*k).rotateY(180*k).rotateZ(spin*k).rotateX(f.grip()*k).rotateY(-90*k);
        var v=q.transform(new Vector3f(0,1,0));
        return new R6.Dir(-v.x,-v.y,-v.z);
    }
    /** The fist and the knife's way {@code sec} seconds in, for a user standing at {@code feet} facing {@code yaw} (the knife as it is turned in the hand). */
    static Vec3d[] blade(Vec3d feet,float yaw,float sec){
        R6.Frame f=CLIP.frame(sec);R6.Pose p=R6.poseOf(f);
        float s=spinAt(sec);if(Math.abs(s)>0&&Math.abs(s)<360)p.blade=spun(f,s);
        return KagenoClips.blade(feet,yaw,p);
    }

    // ───────────────────────────── the body ─────────────────────────────

    /** The arm raised for the turn (elevation, azimuth right of the front); the knife is square to it, its point up and back over their head (as an arm raises a blade it holds square to itself). */
    static final double ARM_UP=56,ARM_RIGHT=16;
    private static R6.Pose on(double rt,double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double rr,double rf,double lr,double lf){
        return KnifeCombo.on(rt,fw,up,yaw,pitch,roll,rArm,blade,lArm,rr,rf,lr,lf);
    }
    /**
     * The clip: out of the stance the arm goes up ahead with the knife as it lights (the turn is the knife's own, in the
     * hand: {@link #spin}; the arm only rides it a little); then down past the right side, the body turning side-on and
     * sinking onto the back leg, the knife drawn back by the hip with its point on the foe's face and the free hand out
     * ahead; the lunge, from the ground up, the front foot shooting out to land on the blow; held out, and back.
     */
    static final Anim CLIP=Anim.joints(
        k(0,KnifeCombo.STANCE),
        // the arm swings up ahead, the knife square to it all the way: its point goes up and over
        k(.07,on(0,0,-.03,-3,2,0,d(-24,22),d(66,22),d(-84,-14),.17,0,-.17,0)),
        // raised: the knife's point up and back over their head, about to turn
        k(TWIRL0,on(-.02,-.02,.0,-8,-4,0,d(ARM_UP,ARM_RIGHT),d(ARM_UP+90,ARM_RIGHT),d(-68,-30),.18,.04,-.18,-.06)),
        k(.30,on(-.02,-.03,.02,-10,-6,0,d(ARM_UP+3,ARM_RIGHT-2),d(ARM_UP+93,ARM_RIGHT-2),d(-62,-36),.18,.06,-.18,-.08)),
        k(TWIRL1,on(-.03,-.03,0,-12,-5,0,d(ARM_UP,ARM_RIGHT+2),d(ARM_UP+90,ARM_RIGHT+2),d(-64,-34),.18,.08,-.18,-.10)),
        // down past the right side and back to the hip, the knife coming over and down onto the foe, the body turning
        // side-on and sinking
        k(.52,on(-.05,-.05,-.12,-24,2,2,d(-8,96),d(62,0),d(-34,-26),.18,.14,-.20,-.18)),
        k(DRAWN,on(-.08,-.08,-.24,-38,7,3,d(-50,146),d(10,-6),d(2,-10),.18,.22,-.22,-.28)),
        // held a moment, the point on their face
        k(.635,on(-.08,-.10,-.26,-40,7,3,d(-48,150),d(11,-6),d(4,-8),.18,.22,-.22,-.28)),
        // the lunge
        k(.665,on(-.04,.14,-.27,-14,12,0,d(-40,70),d(11,-6),d(-8,-70),.17,.50,-.22,-.26)),
        // the blow: the whole body out behind the knife, still going
        k(BLOW,on(.01,.48,-.27,18,18,-3,d(2,-8),d(11,-5),d(-16,-138),.16,.86,-.22,.02)),
        // furthest, then settled
        k(.74,on(.02,.60,-.28,26,20,-3,d(6,-14),d(10,-5),d(-18,-134),.16,.94,-.22,.10)),
        k(.84,on(.02,.58,-.27,24,19,-3,d(5,-13),d(10,-5),d(-22,-134),.16,.94,-.22,.10)),
        k(HOLD[JAB]/20f,on(.02,.56,-.26,22,18,-2,d(3,-10),d(9,-5),d(-28,-136),.16,.94,-.22,.10)),
        // drawn back and up: the front foot steps back
        k(1.04,on(.01,.30,-.14,8,8,-1,d(-44,34),d(4,-4),d(-60,-70),.17,.42,-.20,.06)),
        k(1.12,on(0,.10,-.05,0,3,0,d(-70,70),d(1,0),d(-80,-20),.17,.10,-.18,.02)),
        k(LENGTH_S,KnifeCombo.STANCE)).stops(Anim.stop(BLOW,.06,.08)).after(CorruptionJab::fix);

    private static final Anim.Fix BENT=IsosangLimbs.natural(IsosangLimbs.FLOOR);
    /** A frame of the clip as it is drawn: its elbows and knees bent (IsosangLimbs), the stance's straight limbs at its beginning and its end (as the knife's hits). */
    static R6.Frame fix(float sec,R6.Frame f){
        R6.Frame b=BENT.apply(sec,f);
        float rest=Math.max(1-Ease.smooth(sec,0,KnifeCombo.OUT_OF_REST),Ease.smooth(sec,LENGTH_S-KnifeCombo.INTO_REST,LENGTH_S));
        return rest<=0?b:R6.blend(b,KnifeCombo.AT_REST,Math.min(1,rest));
    }

    // ───────────────────────────── the effects of the thrust ─────────────────────────────

    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE;
    /** The glint in the hand as the knife turns (the video's, yellow). */
    static final int GLINT=0xfff0a0;
    /** The burst on their face: how many long white rays and for how long (seconds), how many purple spikes and for how long, how many pixels. */
    static final int RAYS=10,SPIKES=5,PIXELS=14;
    static final float RAY_LIFE=.18f,SPIKE_LIFE=.26f,BURST_LIFE=.6f;
    /** How far toward the camera from their face the burst and the X lie (blocks beyond half their width: before the face, not in it). */
    static final double FRONT=.16;
    static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}
    static float in(float s){return s<=0?0:Math.min(1,s/.02f);}
    /** The middle of this one's face: their eyes' height over their feet. */
    static Vec3d face(Vec3d feet,double eyes){return feet.add(0,eyes+.04,0);}
    /** Where something before a face lies for a camera at {@code camera}: toward it, beyond half their width. */
    static Vec3d before(Vec3d face,double half,Vec3d camera){
        Vec3d to=camera.subtract(face);double far=to.length();
        return far<1e-6?face:face.add(to.multiply(Math.min(half+FRONT,far*.5)/far));
    }
    /** Ray {@code j} of the burst: the angle it points at round the camera's view (radians) and how long it is (blocks): long and uneven, as the video's. */
    static double[] ray(int j,int seed){return new double[]{(j+.15+.7*h(j,seed,1))*Math.PI*2/RAYS,.9+1.4*h(j,seed,2)*h(j,seed,3)+.5*(j%2)};}
    private static double eyes(Fx c,KnifeCombo.Struck e){
        var live=c.mc.world==null?null:c.mc.world.getEntityById(e.id());
        return live!=null?live.getStandingEyeHeight():e.height()*.9;
    }
    private static boolean landed(Fx c){return c.sec>=BLOW&&KnifeClient.cameTo(c.cast,0);}
    /** The tip's path as the knife turns: its last twentieth of a second, a thin arc of light behind it. */
    private static void trail(Fx c,EffectMesh m,float t){
        if(t<TWIRL0||t>TWIRL1+.05f)return;
        float w=Ease.window(t,TWIRL0,TWIRL1+.05f,.05f,.06f);if(w<=.01f)return;
        Vec3d last=null;int n=10;
        for(int i=0;i<=n;i++){
            float at=Math.max(TWIRL0,Math.min(TWIRL1,t-.055f*(1-i/(float)n)));
            var hb=blade(c.pos,c.cast.yaw(),at);Vec3d p=hb[0].add(hb[1].multiply(KnifeCombo.TIP));
            if(last!=null&&last.squaredDistanceTo(p)>1e-6){float a=w*(i/(float)n);m.softLine(last,p,.16,GLOW,.3f*a);m.line(last,p,.035,LILAC,.5f*a);}
            last=p;
        }
    }
    /** Everything of the thrust but the knife itself, in one pass (the blended bodies, or the light): the glint in the hand and the tip's arc as it turns, and the burst on the face of whom it struck. */
    static void draw(Fx c,EffectMesh m,boolean light){
        float t=c.sec,stop=KnifeClient.until(c.cast.id());float own=KnifeCombo.own(c);
        if(t<stop&&light){
            // the glint in the hand as the knife turns
            float g=Ease.window(t,TWIRL0-.04f,TWIRL1,.05f,.1f);
            if(g>.01f){
                var hb=blade(c.pos,c.cast.yaw(),t);float near=GlitchFx.near(hb[0],c.camera)*own;
                float tw=.7f+.3f*(float)Math.sin(t*70);
                m.star(hb[0],.26*tw,GLINT,.8f*g*near);m.softDisc(hb[0],.2,GLINT,.3f*g*near);
            }
            trail(c,m,t);
        }
        if(!landed(c))return;
        float s=t-BLOW;if(s>=BURST_LIFE)return;
        var struck=KnifeCombo.struck(c,0);
        if(struck.isEmpty()){
            // it met no one: a spark at its point
            if(light&&s<.12f){
                var hb=blade(KnifeCombo.spot(c,0),c.cast.yaw(),BLOW);Vec3d tip=hb[0].add(hb[1].multiply(KnifeCombo.TIP));
                m.star(tip,.4,WHITE,.7f*in(s)*(1-s/.12f)*GlitchFx.near(tip,c.camera)*own);
            }
            return;
        }
        for(var e:struck){
            int seed=KnifeCombo.seed(c,0)+e.id();
            Vec3d at=before(face(e.feet(),eyes(c,e)),e.half(),c.camera);
            float near=GlitchFx.near(at,c.camera);if(near<=0)continue;
            var f=GlitchFx.facing(c.camera,at);
            if(light){
                // the white burst: a core, a star, long thin rays
                if(s<.1f)m.softDisc(at,.34,WHITE,.85f*in(s)*(1-s/.1f)*near);
                if(s<.16f)m.star(at,.95,WHITE,.9f*in(s)*(1-s/.16f)*near);
                if(s<RAY_LIFE)for(int j=0;j<RAYS;j++){
                    var r=ray(j,seed);Vec3d dir=f[0].multiply(Math.cos(r[0])).add(f[1].multiply(Math.sin(r[0])));
                    m.needle(at.add(dir.multiply(.12)),at.add(dir.multiply(.12+r[1]*Ease.smooth(s,0,.045f))),.05,.3,WHITE,.9f*in(s)*(1-s/RAY_LIFE)*near);
                }
                m.softDisc(at,.85,GLOW,.2f*in(s)*(1-Ease.smooth(s,.1f,.4f))*near);
            }
            // the purple spikes after it
            float sp=s-.04f;
            if(sp>=0&&sp<SPIKE_LIFE)for(int j=0;j<SPIKES;j++){
                double a=(j+.2+.6*h(j,seed,5))*Math.PI*2/SPIKES+h(0,seed,6)*Math.PI*2;
                Vec3d dir=f[0].multiply(Math.cos(a)).add(f[1].multiply(Math.sin(a)));
                double len=(.6+.55*h(j,seed,8))*Ease.smooth(sp,0,.05f),wide=.1+.05*h(j,seed,9);
                float fade=in(sp)*(1-Ease.smooth(sp,.1f,SPIKE_LIFE))*near;
                Vec3d a0=at.add(dir.multiply(.2)),a1=at.add(dir.multiply(.2+len));
                if(light){m.needle(a0,a1,wide*2.2,.25,GLOW,.14f*fade);if(j%2==0)m.needle(a0,a1,wide*.3,.25,WHITE,.8f*fade);}
                else{m.needle(a0,a1,wide*1.45,.25,INK,.85f*fade);m.needle(a0,a1,wide,.25,j%2==0?PURPLE:DEEP,.95f*fade);}
            }
            GlitchFx.pixels(m,at,f[2],.92,3.2,.035,PIXELS,seed,s,light,c.camera);
        }
    }
    /** Its light on the world, and little afterglow while the knife turns and is thrust (its light would leave copies of itself like spokes). */
    static void frame(Fx c,FxFrame fr){
        float t=c.sec;
        if(t>=TWIRL0-.05f&&t<BLOW+.1f)fr.afterglow(.1f);
        if(!landed(c))return;
        float s=t-BLOW;
        if(s<.3f)for(var e:KnifeCombo.struck(c,0))fr.light(face(e.feet(),e.height()*.9),0x9a3cff,7,1f*(1-s/.3f));
    }
    /** A small shake as it lands on someone (a third of it on no one). */
    static float shake(StandClient.Cast cast,float sec){
        if(!KnifeClient.cameTo(cast,0))return 0;
        int[] told=KnifeClient.struck(cast.id(),0);
        return .9f*(told!=null&&told.length>0?1:.33f)*KnifeCombo.bump(sec,BLOW,.07f);
    }

    // ───────────────────────────── the X ─────────────────────────────

    /** The X's pixels: how many across and down, and what each is (0 nothing, 1 its purple outline, 2 its white). */
    static final int ACROSS=17,DOWN=11;
    static final byte[][] CELLS=cells();
    /** How wide the X is (blocks: wider than a head, as the video's), and so how big a pixel of it is. */
    static final double WIDE=.9,CELL=WIDE/ACROSS;
    private static double toStroke(double x,double y,double x0,double y0,double x1,double y1){
        double dx=x1-x0,dy=y1-y0,u=Math.max(0,Math.min(1,((x-x0)*dx+(y-y0)*dy)/(dx*dx+dy*dy)));
        return Math.hypot(x-x0-dx*u,y-y0-dy*u);
    }
    private static byte[][] cells(){
        var out=new byte[DOWN][ACROSS];
        for(int y=0;y<DOWN;y++)for(int x=0;x<ACROSS;x++){
            // the two strokes, corner to corner within the outline's margin (a pixel is as wide as it is high)
            double near=Math.min(toStroke(x,y,2,2,ACROSS-3,DOWN-3),toStroke(x,y,2,DOWN-3,ACROSS-3,2));
            out[y][x]=(byte)(near<=1.1?2:near<=2.2?1:0);
        }
        return out;
    }
    /**
     * One marked: which entity, how far past its blow the jab's own clock was at this client's tick {@code received}
     * (ticks; before it, negative), and its seed. The X runs on the jab's clock (its cast's, as this client drew its
     * thrust), so it comes on the frame the knife is drawn landing: the server's word of the blow comes a tick before.
     */
    record Mark(int entity,long past,long received,int seed){
        /** Ticks since the blow landed as this client drew it, at its tick {@code ticks} and {@code delta} of the next. */
        float age(long ticks,float delta){return past+(ticks-received)+delta;}
        float age(float delta){return age(StandClient.ticks(),delta);}
    }
    private static final Map<Integer,Mark> MARKS=new LinkedHashMap<>();
    /** The X comes in over this long and goes out over this long (seconds). */
    static final float X_IN=.14f,X_OUT=.3f;
    /** The jab's blow landed on these (the server's word): each has the X on their face for as long as they are held (JAB_STUN). */
    static void mark(StandClient.Cast cast,List<StandClient.Target> struck){
        long past=cast.serverNow()-cast.start()-blowAt(JAB,0);
        for(var t:struck){
            MARKS.put(t.entity(),new Mark(t.entity(),past,cast.received(),(int)(cast.start()*31+t.entity()*7)));
            while(MARKS.size()>64)MARKS.remove(MARKS.keySet().iterator().next());
        }
    }
    static void clear(){MARKS.clear();shown=List.of();}
    /** Whether this entity has the X on them now: no stars turn over them and they do not sway (the X is the mark of it). */
    public static boolean marked(int entity){return !MARKS.isEmpty()&&marked(entity,StandClient.ticks());}
    static boolean marked(int entity,long ticks){
        var k=MARKS.get(entity);return k!=null&&k.age(ticks,0)<JAB_STUN;
    }
    /** How big the X is {@code s} seconds after it came and {@code left} seconds before it goes (1 its size): popped in from bigger, shrinking out. */
    static float size(float s,float left){return (1+.9f*(1-Ease.smooth(s,0,X_IN)))*(.55f+.45f*Ease.smooth(left,0,X_OUT));}
    /** How strongly it shows then (0 or 1: it blinks in and blinks out, as a thing on a screen). */
    static float shows(float s,float left,int seed){
        if(s<0||left<=0)return 0;
        if(s<X_IN)return (int)Math.floor(s*50)%2==0?1:0;
        if(left<X_OUT)return (int)Math.floor(left*28)%2==0?1:0;
        return 1;
    }
    /** How far round it is tipped then (degrees): rocking a little, as the video's. */
    static float tip(float clock,int seed){
        return 5f*(float)Math.sin(clock*Math.PI*2*.55+seed*.7)+2.2f*(float)Math.sin(clock*Math.PI*2*1.7+seed*1.3);
    }
    /** Every so often its rows slip sideways a moment: how many pixels row {@code row} is out then. */
    static int slip(float clock,int row,int seed){
        double every=1.25,turn=clock/every+h(0,seed,21);int n=(int)Math.floor(turn);
        if((turn-n)*every>=.09||h(n,seed,22)<.25)return 0;
        double r=h(row+n*17,seed,23);return r<.3?-1:r>.7?1:0;
    }
    /** An X to draw this frame: where its middle is, the camera's right and up there tipped as it rocks, a pixel's size, how strongly it shows, its clock and its seed. */
    record Shown(Vec3d at,Vec3d right,Vec3d up,double cell,float alpha,float clock,int seed){}
    private static List<Shown> shown=List.of();
    /** Every frame before the effects: the marks still held, each before its one's face toward this camera. True if any shows. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        if(MARKS.isEmpty()||mc.world==null){shown=List.of();return false;}
        var out=new ArrayList<Shown>();
        for(var it=MARKS.values().iterator();it.hasNext();){
            var k=it.next();float age=k.age(delta);
            if(age>=JAB_STUN+2){it.remove();continue;}
            if(!(mc.world.getEntityById(k.entity()) instanceof LivingEntity e)||!e.isAlive()){if(age>2)it.remove();continue;}
            // (their own eyes are behind it in the first person: not before them)
            if(e==mc.getCameraEntity()&&!mc.gameRenderer.getCamera().isThirdPerson())continue;
            float s=age/20f,left=(JAB_STUN-age)/20f;
            float a=shows(s,left,k.seed());if(a<=0)continue;
            Vec3d face=face(e.getLerpedPos(delta),e.getStandingEyeHeight());
            if(face.squaredDistanceTo(camera)>96*96)continue;
            Vec3d at=before(face,e.getWidth()*.5,camera);
            float near=GlitchFx.near(at,camera);if(near<=0)continue;
            var f=GlitchFx.facing(camera,at);float clock=age/20f;
            double turn=Math.toRadians(tip(clock,k.seed())),cs=Math.cos(turn),sn=Math.sin(turn);
            Vec3d right=f[0].multiply(cs).add(f[1].multiply(sn)),up=f[1].multiply(cs).subtract(f[0].multiply(sn));
            out.add(new Shown(at.add(0,.015*Math.sin(clock*Math.PI*2*.8),0),right,up,CELL*size(s,left),a*near,clock,k.seed()));
        }
        shown=out;
        return !out.isEmpty();
    }
    private static void cell(EffectMesh m,Shown x,double cx,double cy,double half,int rgb,float alpha){
        Vec3d p=x.at.add(x.right.multiply(cx*x.cell)).add(x.up.multiply(cy*x.cell));
        Vec3d a=x.right.multiply(half*x.cell),b=x.up.multiply(half*x.cell);
        m.quad(p.subtract(a).subtract(b),p.add(a).subtract(b),p.add(a).add(b),p.subtract(a).add(b),rgb,alpha);
    }
    /** The blended pass: the X's pixels, its purple outline and its white. */
    public static void alpha(EffectMesh m){for(var x:shown)alpha(m,x);}
    /** One X's pixels (2.125.0: the X on the face of X Chara's double is the same, CloneFling). */
    static void alpha(EffectMesh m,Shown x){
        for(int y=0;y<DOWN;y++){
            int slip=slip(x.clock,y,x.seed);
            for(int i=0;i<ACROSS;i++){
                int kind=CELLS[y][i];if(kind==0)continue;
                // (a hair over a pixel wide: no seams between them)
                cell(m,x,i+slip-(ACROSS-1)*.5,(DOWN-1)*.5-y,.52,kind==2?WHITE:PURPLE,x.alpha);
            }
        }
    }
    /** The light, added: its purple glow, and a little of its white's own. */
    public static void additive(EffectMesh m){for(var x:shown)additive(m,x);}
    static void additive(EffectMesh m,Shown x){
        float breath=.85f+.15f*(float)Math.sin(x.clock*4.2f);
        m.softDisc(x.at,x.cell*ACROSS*.75,GLOW,.26f*x.alpha*breath);
        for(int y=0;y<DOWN;y++){
            int slip=slip(x.clock,y,x.seed);
            for(int i=0;i<ACROSS;i++)if(CELLS[y][i]==2)cell(m,x,i+slip-(ACROSS-1)*.5,(DOWN-1)*.5-y,.5,LILAC,.22f*x.alpha);
        }
    }
    /** A little purple light on the world round each X. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var x:shown)f.light(x.at,GLOW,2.2,.3f*x.alpha);
    }
    /** For the capture tools: the marks this client holds, as "entity:seconds on". */
    public static String describe(float delta){return MARKS.isEmpty()?"":describe(StandClient.ticks(),delta);}
    static String describe(long ticks,float delta){
        var sb=new StringBuilder();
        for(var k:MARKS.values()){if(sb.length()>0)sb.append(' ');sb.append(String.format(java.util.Locale.ROOT,"%d:%.2f",k.entity(),k.age(ticks,delta)/20f));}
        return sb.toString();
    }
}
