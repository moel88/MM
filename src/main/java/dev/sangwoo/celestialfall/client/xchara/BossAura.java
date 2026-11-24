package dev.sangwoo.celestialfall.client.xchara;

import static dev.sangwoo.celestialfall.client.xchara.GlitchFx.*;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.boss.BossClient;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * What is about X Chara the boss (2.137.0, user request: "몸에 전체적으로 글리치 아우라가 둘러져 있게 만들어주고 밤에도
 * 보랏빛으로 빛이나게 만들어줘 눈에는 붉은색 빛과 아우라를 추가해주고"). Four things, all functions of this client's
 * clock and the boss's own number, so every frame is a new one:
 * <ul>
 * <li>the glitch round its whole body: its own silhouette drawn again behind it, a little bigger, in X Chara's violet
 * and split to pink on one side and cyan on the other, jumping sideways 14 times a second (far, now and then); short
 * bars torn out from its outline; pixels rising round it and going out one by one. Nothing of it lies on the body.</li>
 * <li>a violet light: round the body (the stand aura's layer wraps it too, torn across where the glitch is,
 * StandAuraLayer), and on the ground and walls round it, so it shows by night.</li>
 * <li>what of its skin and outfit shines by itself (BossOutfitFeature draws those, as bright as {@link #shine}).</li>
 * <li>its red eye (the skin's one red eye, the character's right): a red point of light on it, a streak across it, a
 * red flame trailing from its outer corner up and back, and a little red light on what is near.</li>
 * </ul>
 * Stronger as it is angry. Screen effects: none.
 */
public final class BossAura {
    private BossAura(){}
    /** The kind this is about. */
    public static final String KIND="xchara";
    /** How often the glitch changes (steps a second: the weakening's, the pull's). */
    static final float STEP_RATE=14;
    /** The red eye in the head's own space (model pixels; +y down, -z the face, -x the character's right): the skin's red pixel, just before the face. */
    public static final float EYE_X=-1.5f,EYE_Y=-2.2f,EYE_Z=-4.2f;
    /** The colours of its red eye: its core, its glow, the flame's far end. */
    static final int EYE_HOT=0xffd0d8,EYE_RED=0xff1e46,EYE_DEEP=0xc0003a;
    /** The silhouettes behind it: {how far behind the body (blocks), how much bigger}. */
    static final float RIM_BACK=.34f,RIM_SWELL=1.045f;
    /** Its pixels and the bars torn from its outline. */
    static final int PIXELS=30,BARS=7;
    /** The flame from its eye: how many pieces, how long (blocks). */
    static final int FLAME=6;static final float FLAME_LONG=.7f;
    /** How far toward the viewer its eye's light is drawn (blocks): past where the effect layer melts light into the face under it. */
    static final float EYE_NEAR=.5f;

    // ───────────────────────────── the glitch's numbers ─────────────────────────────

    /** The step the glitch is in at {@code clock} seconds. */
    static int step(float clock){return (int)Math.floor(clock*STEP_RATE);}
    /** How hard the glitch is at {@code clock}: mostly a little (0.25..0.5), in fits (about a third of the time, 0.6 s long) all of it. */
    static float fit(float clock,int seed){
        int k=(int)Math.floor(clock/.6f);
        return h(k,0,seed,31)<.34?1:.25f+.25f*(float)h(k,0,seed,32);
    }
    /**
     * One of its silhouettes this step ({@code which}: 0 the violet one, 1 the pink, 2 the cyan): {how far to the
     * camera's right (blocks), how far up, how much bigger}, or null while it does not show. The violet one is nearly
     * always there, round all of it; the other two come and go, one to either side, further in a fit.
     */
    static float[] rim(int which,float clock,int seed,float strength){
        // (the fit as the step began: a silhouette holds through its step)
        int s=step(clock);float fit=fit((s+.01f)/STEP_RATE,seed);
        double show=h(s,which,seed,33);
        if(which==0){
            if(show>.9)return null;
            float jump=h(s,0,seed,34)<.16*fit?(float)(h(s,0,seed,35)-.5)*.3f*fit:0;
            return new float[]{jump,0,RIM_SWELL+.025f*fit*(float)h(s,0,seed,36)};
        }
        if(show>.45+.4*fit*strength)return null;
        float side=which==1?-1:1,far=(.03f+.04f*(float)h(s,which,seed,37))*(1+1.6f*fit*(float)h(s,which,seed,38));
        return new float[]{side*far,(float)(h(s,which,seed,39)-.5)*.05f*fit,1.015f};
    }
    /** The colour of silhouette {@code which}. */
    static int rimColour(int which){return which==0?PURPLE:which==1?PINK:CYAN;}
    /**
     * Pixel {@code j} of those rising round a body {@code height} high and {@code width} wide at {@code clock}: {its
     * height up the body (blocks), how far to the camera's right of its middle, its half size, its colour, how strongly
     * it shows}, or null while it is out. Each is born outside the body's outline (never on it), rises a little, keeps
     * to its grid, blinks at its end and comes again somewhere else.
     */
    static double[] pixel(int j,float clock,int seed,double height,double width){
        float life=(float)(.5+.9*h(j,0,seed,41)),t=clock+(float)h(j,0,seed,42)*life;
        int k=(int)Math.floor(t/life);float age=t-k*life,u=age/life;
        if(h(j,k,seed,43)>.8)return null;
        double side=h(j,k,seed,44)<.5?-1:1,out=width*.5+.06+.42*Math.pow(h(j,k,seed,45),1.6);
        double y=-.05+(height+.35)*h(j,k,seed,46)+(.25+.5*h(j,k,seed,47))*age;
        // (over the head some: the aura closes above it)
        if(h(j,k,seed,48)<.2){out=width*.5*h(j,k,seed,49)*side;side=1;y=height+.05+.3*h(j,k,seed,45)+.4*age;}
        double grid=.0625,x=Math.round(side*out/grid)*grid;y=Math.round(y/grid)*grid;
        double half=h(j,k,seed,50)<.2?.07:.038;
        double c=h(j,k,seed,51);int rgb=c<.36?PURPLE:c<.56?DEEP:c<.72?LILAC:c<.82?INK:c<.92?PINK:CYAN;
        float a=u<.12f?u/.12f:u<.78f?1:((int)Math.floor(t*30+j)%2==0?1:0);
        return new double[]{y,x,half,rgb,a};
    }
    /**
     * Bar {@code j} torn out of the outline this step: {its height up the body, where its middle is to the camera's
     * right, its half length, its half height, its colour}, or null while it does not show. It starts at the body's
     * side and reaches outward.
     */
    static double[] bar(int j,float clock,int seed,double height,double width){
        int s=step(clock);float fit=fit((s+.01f)/STEP_RATE,seed);
        if(h(s,j,seed,61)>.22+.5*fit)return null;
        double side=h(s,j,seed,62)<.5?-1:1,len=.1+.34*h(s,j,seed,63)*(.5+fit),y=height*(.04+.94*h(s,j,seed,64));
        double from=width*.5-.04;
        double c=h(s,j,seed,65);int rgb=c<.45?PURPLE:c<.62?LILAC:c<.78?PINK:c<.9?CYAN:INK;
        return new double[]{y,side*(from+len*.5),len*.5,.018+.03*h(s,j,seed,66),rgb};
    }
    /**
     * How brightly what shines of it shines at {@code clock} (the outfit's and skin's own light, 0..1.25): breathing
     * slowly, dipping for a step now and then as a failing screen does, brighter as it is angry.
     */
    public static float shine(float clock,int seed,float anger){
        float breath=.8f+.12f*MathHelper.sin(clock*2.1f)+.05f*MathHelper.sin(clock*5.3f+1.1f);
        int s=step(clock);float fit=fit(clock,seed);
        float dip=h(s,0,seed,71)<.05+.1*fit?.45f+.3f*(float)h(s,0,seed,72):1;
        return breath*dip*(1+.25f*anger);
    }
    /**
     * Point {@code i} (0 the eye's outer corner, to {@link #FLAME} its end) of the red flame from its eye, in blocks
     * from the eye: {out to the eye's side, up, back off the face}. It streams up and back from the corner, waving.
     */
    static double[] flame(int i,float clock,float anger){
        double u=i/(double)FLAME,len=FLAME_LONG*(.75+.45*anger);
        double wave=Math.sin(clock*9.0-u*5.2)*.035*u+Math.sin(clock*15.5-u*8.7)*.016*u;
        return new double[]{(.03+.3*u)*len/FLAME_LONG+wave*.6,(.02+.55*u*u+.2*u)*len+wave,.02+.22*u*len};
    }

    // ───────────────────────────── whom ─────────────────────────────

    /** Where its eye was drawn (BossOutfitFeature tells every frame it draws the head). */
    private static final class Eye {Vec3d at,side,up,front;long frame;}
    private static final Map<Integer,Eye> EYES=new HashMap<>();
    /** How angry each is taken to be (0..1, eased) and when it was last seen. */
    private static final class State {float anger,on;float seen=Float.NaN;}
    private static final Map<Integer,State> STATES=new HashMap<>();
    private static long frames;
    private static boolean silhouette;
    /** Whether one of its silhouettes is being drawn now (nothing in its hands then: StandHeldItemFeatureMixin). */
    public static boolean silhouette(){return silhouette;}
    private record Shown(int id,Vec3d feet,double height,double width,float clock,int seed,float strength,float anger,Eye eye){}
    private static List<Shown> shown=List.of();

    public static void initialize(){WorldRenderEvents.AFTER_ENTITIES.register(BossAura::render);}
    /** Whether this body is X Chara the boss. */
    public static boolean is(Entity e){
        if(e==null)return false;var i=BossClient.info(e.getUuid());return i!=null&&KIND.equals(i.kind());
    }
    static float clock(float delta){return (StandClient.ticks()+delta)/20f;}
    static int seed(Entity e){return e.getUuid().hashCode()&0xffff;}
    /** How brightly what shines of this boss shines now (BossOutfitFeature). */
    public static float shine(Entity e,float delta){
        var s=STATES.get(e.getId());return shine(clock(delta),seed(e),s==null?0:s.anger)*(e instanceof LivingEntity l&&l.isDead()?Math.max(0,1-l.deathTime/16f):1);
    }
    /**
     * How much of its own light shows where the light on it is {@code block} and {@code sky} (0..15 each) under a sky
     * as bright as {@code brightness} (the client's, 1 by day to 0.2 at midnight): a third of it by day (its body is
     * lit already), all of it in the dark, where it is what shows of it.
     */
    public static float byDark(int block,int sky,float brightness){
        float day=MathHelper.clamp((brightness-.2f)/.8f,0,1),lit=MathHelper.clamp(Math.max(block,sky*(.27f+.73f*day))/15f,0,1);
        return .34f+.66f*(1-lit)*(1-lit*.4f);
    }
    /**
     * The head is being drawn with {@code head} its own space (the model's pixels a sixteenth each): where its red eye
     * is in the world, and which ways its face looks. Not while its silhouette is drawn (that is somewhere else).
     */
    public static void eye(Entity e,MatrixStack head){
        if(FlatBody.drawing())return;
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        var m=head.peek().getPositionMatrix();
        Vector3f o=m.transformPosition(new Vector3f(EYE_X/16,EYE_Y/16,EYE_Z/16)),side=m.transformDirection(new Vector3f(-1,0,0)),up=m.transformDirection(new Vector3f(0,-1,0)),front=m.transformDirection(new Vector3f(0,0,-1));
        // back from the view's frame to the world's (the stack's base turns the world to the view)
        var back=RenderSystem.getInverseViewRotationMatrix();back.transform(o);back.transform(side);back.transform(up);back.transform(front);
        if(side.lengthSquared()<1e-10f||up.lengthSquared()<1e-10f||front.lengthSquared()<1e-10f)return;
        Vec3d at=mc.gameRenderer.getCamera().getPos().add(o.x,o.y,o.z);
        // (a shader pack's shadow pass draws from somewhere else: only where the body is)
        if(at.squaredDistanceTo(e.getLerpedPos(mc.getTickDelta()).add(0,e.getHeight()*.8,0))>6.25)return;
        var x=EYES.computeIfAbsent(e.getId(),k->new Eye());
        side.normalize();up.normalize();front.normalize();
        x.at=at;x.side=new Vec3d(side.x,side.y,side.z);x.up=new Vec3d(up.x,up.y,up.z);x.front=new Vec3d(front.x,front.y,front.z);x.frame=frames;
    }

    /** Every frame before the effects (KagenoRenderer): which bosses show and how; true if any does. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        frames++;
        if(mc.world==null||BossClient.all().isEmpty()){shown=List.of();EYES.clear();STATES.clear();return false;}
        var out=new ArrayList<Shown>();float clock=clock(delta);
        for(var p:mc.world.getPlayers()){
            var info=BossClient.info(p.getUuid());if(info==null||!KIND.equals(info.kind())||p.isInvisible())continue;
            var s=STATES.computeIfAbsent(p.getId(),k->new State());
            float dt=Float.isNaN(s.seen)?0:MathHelper.clamp(clock-s.seen,0,.25f);s.seen=clock;
            s.anger+=((info.angry()?1:0)-s.anger)*Math.min(1,dt*3.5f);s.on=Math.min(1,s.on+dt*2.5f);
            float alive=p.isDead()?Math.max(0,1-p.deathTime/16f):1;
            Vec3d feet=p.getLerpedPos(delta);
            if(feet.squaredDistanceTo(camera)>96*96||alive<=0)continue;
            var eye=EYES.get(p.getId());if(eye!=null&&eye.frame<frames-3)eye=null;
            out.add(new Shown(p.getId(),feet,p.getHeight(),p.getWidth(),clock,seed(p),s.on*alive*(.72f+.28f*s.anger),s.anger,eye));
        }
        if(out.isEmpty()&&frames%200==0){EYES.clear();STATES.clear();}
        shown=out;return !out.isEmpty();
    }
    /** The bosses whose aura shows this frame and how strongly (the stand aura's layer wraps them too). */
    public static void bearers(MinecraftClient mc,java.util.function.BiConsumer<Entity,Float> into){
        if(mc.world==null)return;
        for(var x:shown){var e=mc.world.getEntityById(x.id);if(e!=null&&x.strength>.02f)into.accept(e,Math.min(1,x.strength));}
    }
    /** The blended pass: the bars torn from its outline, its pixels (their own colours, laid over what is behind). */
    public static void alpha(EffectMesh m){
        for(var x:shown){
            Vec3d mid=x.feet.add(0,x.height*.5,0);float near=near(mid,m.camera())*x.strength;if(near<=0)continue;
            Vec3d[] f=facing(m.camera(),mid);
            for(int j=0;j<BARS;j++){
                double[] b=bar(j,x.clock,x.seed,x.height,x.width);if(b==null)continue;
                Vec3d p=x.feet.add(0,b[0],0).add(f[0].multiply(b[1]));
                GlitchFx.bar(m,p,f,b[2]+.012,b[3]+.012,INK,.8f*near);GlitchFx.bar(m,p,f,b[2],b[3],(int)b[4],near);
            }
            for(int j=0;j<PIXELS;j++){
                double[] b=pixel(j,x.clock,x.seed,x.height,x.width);if(b==null||b[4]<=0)continue;
                Vec3d p=x.feet.add(0,b[0],0).add(f[0].multiply(b[1]));float a=(float)b[4]*near;
                square(m,p,f,b[2]+.01,INK,.75f*a);square(m,p,f,b[2],(int)b[3],a);
            }
        }
    }
    /** The light, added: the violet about its body, a glow of its bars and pixels, its red eye and the flame from it. */
    public static void additive(EffectMesh m){
        for(var x:shown){
            Vec3d mid=x.feet.add(0,x.height*.5,0);float near=near(mid,m.camera())*x.strength;if(near<=0)continue;
            Vec3d[] f=facing(m.camera(),mid);
            float pulse=.85f+.15f*MathHelper.sin(x.clock*2.1f),fit=fit(x.clock,x.seed);
            // the violet about it: behind it as the viewer sees it, so its body stands dark against its own light
            Vec3d behind=f[2].multiply(-(x.width*.5+.25));
            m.softDisc(x.feet.add(0,x.height*.72,0).add(behind),1.15,GLOW,.3f*pulse*near);
            m.softDisc(x.feet.add(0,x.height*.3,0).add(behind),1.0,DEEP,.26f*pulse*near);
            m.softDisc(x.feet.add(0,.04,0),.9,GLOW,.16f*near);
            for(int j=0;j<BARS;j++){
                double[] b=bar(j,x.clock,x.seed,x.height,x.width);if(b==null||(int)b[4]==INK)continue;
                GlitchFx.bar(m,x.feet.add(0,b[0],0).add(f[0].multiply(b[1])),f,b[2]*1.2,b[3]*2.6,(int)b[4],.22f*near);
            }
            for(int j=0;j<PIXELS;j++){
                double[] b=pixel(j,x.clock,x.seed,x.height,x.width);if(b==null||b[4]<=0||(int)b[3]==INK)continue;
                m.softDisc(x.feet.add(0,b[0],0).add(f[0].multiply(b[1])),b[2]*2.8,(int)b[3]==LILAC?GLOW:(int)b[3],.2f*(float)b[4]*near);
            }
            eye(m,x,near*(.9f+.1f*fit));
        }
    }
    /** Its red eye: a hot point, a red glow, a streak across it, the flame from its outer corner. Only from before its face. */
    private static void eye(EffectMesh m,Shown x,float a){
        var e=x.eye;if(e==null||a<=0)return;
        Vec3d to=m.camera().subtract(e.at);double far=to.length();if(far<.8)return;
        // (seen from behind the head it is hidden: the light is on its face)
        float facing=smooth((float)(to.normalize().dotProduct(e.front)+.12)/.3f);if(facing<=0)return;
        a*=facing;
        float flicker=.86f+.14f*MathHelper.sin(x.clock*23f)*MathHelper.sin(x.clock*7.7f),k=.8f+.4f*x.anger;
        // (further off it holds its size on the screen a little: a point of light, not a lost pixel)
        double grow=Math.max(1,Math.min(2.6,far/7));
        // drawn EYE_NEAR nearer the viewer along their sight, as much smaller: where it was on the screen, clear of
        // the face (the effect layer melts whatever lies close before a surface)
        double keep=(far-EYE_NEAR)/far;Vec3d cam=m.camera(),eye=cam.add(e.at.subtract(cam).multiply(keep));grow*=keep;
        m.softDisc(eye,.34*k*grow,EYE_RED,.4f*a*flicker);
        m.softDisc(eye,.14*k*grow,EYE_RED,a);
        m.softDisc(eye,.06*grow,EYE_HOT,a);
        Vec3d r=facing(cam,eye)[0];
        m.softLine(eye.subtract(r.multiply(.34*k*grow)),eye.add(r.multiply(.34*k*grow)),.05*grow,EYE_RED,.85f*a*flicker);
        m.softLine(eye.subtract(r.multiply(.16*grow)),eye.add(r.multiply(.16*grow)),.03*grow,EYE_HOT,.8f*a);
        Vec3d back=e.front.multiply(-1),last=null;
        for(int i=0;i<=FLAME;i++){
            double[] p=flame(i,x.clock,x.anger);
            Vec3d at=e.at.add(e.side.multiply(p[0]+.03)).add(e.up.multiply(p[1])).add(back.multiply(p[2]));
            at=cam.add(at.subtract(cam).multiply(keep));
            if(last!=null){
                float u=(i-.5f)/FLAME;
                m.softTaper(last,at,.2*(1-.75*(i-1)/(double)FLAME)*grow,.2*(1-.75*i/(double)FLAME)*grow,u<.5f?EYE_RED:EYE_DEEP,(1-u*.75f)*a);
                if(i<=3)m.softTaper(last,at,.07*grow,.045*grow,EYE_HOT,.8f*a);
            }
            last=at;
        }
    }
    /** Its violet on the world round it (so it shows by night), a little red by its eye. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var x:shown){
            float pulse=.85f+.15f*MathHelper.sin(x.clock*2.1f);
            f.light(x.feet.add(0,x.height*.55,0),GLOW,5.5+1.5*x.anger,(.5f+.25f*x.anger)*pulse*x.strength);
            if(x.eye!=null)f.light(x.eye.at.add(x.eye.front.multiply(.5)),EYE_RED,1.8,.3f*x.strength);
        }
    }

    /**
     * After the bodies: its silhouettes behind it (FlatBody: its own shape, whatever it does, one flat colour each),
     * so only what sticks out past its outline shows. Not with a shader pack's pipeline installed (as the pull's).
     */
    private static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();
        if(mc.world==null||BossClient.all().isEmpty()||!(ctx.consumers() instanceof VertexConsumerProvider.Immediate immediate)||!FxLayer.available())return;
        float delta=ctx.tickDelta(),clock=clock(delta);Vec3d cam=ctx.camera().getPos();boolean drew=false;
        for(AbstractClientPlayerEntity p:mc.world.getPlayers()){
            if(!is(p)||p.isInvisible()||p.isDead())continue;
            var s=STATES.get(p.getId());float strength=s==null?0:s.on*(.72f+.28f*s.anger);if(strength<.5f)continue;
            Vec3d feet=p.getLerpedPos(delta),mid=feet.add(0,p.getHeight()*.5,0);double far=mid.distanceTo(cam);
            if(far>64||far<1.2)continue;
            Vec3d[] f=facing(cam,mid);int seed=seed(p);
            // (behind the body from here, and as much bigger as it is further, so it shows the same size round it)
            Vec3d away=f[2].multiply(-RIM_BACK);float keep=(float)((far+RIM_BACK)/far);
            silhouette=true;
            try{
                for(int which=2;which>=0;which--){
                    float[] r=rim(which,clock,seed,strength);if(r==null)continue;
                    FlatBody.draw(mc,ctx.matrixStack(),immediate,cam,p,feet.add(away).add(f[0].multiply(r[0])).add(0,r[1],0),delta,rimColour(which),r[2]*keep,which==0?.95f:.8f);
                    drew=true;
                }
            }finally{silhouette=false;}
        }
        if(drew)immediate.draw(RenderLayer.getDebugQuads());
    }
    /** For the capture tools and the checks: each boss shown as "strength,anger,eye(1/0)". */
    public static String describe(){
        var b=new StringBuilder();
        for(var x:shown)b.append(b.length()>0?" ":"").append(String.format(java.util.Locale.ROOT,"%.2f,%.2f,%d",x.strength,x.anger,x.eye==null?0:1));
        return b.toString();
    }
}
