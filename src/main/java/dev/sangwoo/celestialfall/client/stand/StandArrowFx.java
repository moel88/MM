package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.stand.ArrowStabPose.Fate;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.*;

/**
 * The stand arrow's light (2.70.0; on the ten-second stab since 2.71.0), on the effect layer with the stand's punches
 * (KagenoRenderer), in their way: light that adds up and blooms, crisp bursts inked dark round their edges, smoke and
 * dust on fixed planes, nothing over the whole screen (no cutscene: the player's camera and screen are theirs). Gold is
 * the arrow's, violet the stand's, crimson death's.
 * <p>
 * Looked at, raised to the sky and aimed, the arrow's golden head glints and motes of light circle it. Driven in, it
 * leaves a streak of light; as it goes in, a burst at the chest, a ring round the arrow and one running out round the
 * body, sparks thrown out. Each knee going down throws up a little dust. While it is drawn into the body on the knees,
 * light leaks out of the wound in rays, motes of light are drawn into it, and a ring runs out round the body with every
 * beat of the heart, the wound and the arrow glowing brighter and brighter; as everything goes still the rays are drawn
 * back in. Chosen: a burst of gold and violet, a pillar of light from the feet, rings running out over the ground and
 * round the body, sparks of both rising. Rejected: the wound's light turns crimson and gutters out with its rays falling
 * in, black smoke rises from the chest, and dust is thrown up where the body lands. Every effect is a function of the
 * stab's clock.
 */
public final class StandArrowFx {
    /** Crimson for one the arrow rejects, and its dark. */
    static final int CRIMSON=0xd0283c,BLOOD=0x5a0612;
    /**
     * This frame's stabs near the camera: the stab, its clock, the body (as the stab poses it), where the arrow went or
     * will go in (the chest), which way it points into the body, the arrow (its tip, nock and head's base in the world,
     * null once gone), its glow, the feet, which way the body faces, and the chest as the one rejected falls;
     * {@link #near}: 1 for a camera well away from the chest, down to 0 for one at it (the one stabbed seeing from their
     * own eyes): the light that bursts from the chest would fill their screen, so it fades there (no screen effects);
     * and where the knees come down on the ground (right, left: as they rest kneeling).
     */
    record Shown(StandArrowClient.Stab stab,float t,ArrowStabPose.Frame frame,Vec3d chest,Vec3d in,Vec3d tip,Vec3d nock,Vec3d head,Vec3d prevTip,float glow,Vec3d feet,Vec3d way,int seed,float near,Vec3d[] knees){
        Fate fate(){return stab.fate;}
        /** Seconds since its choice showed (below 0 before). */
        float since(){return t-stab.shows();}
    }
    private static final List<Shown> FRAME=new ArrayList<>();
    private static float delta;
    private StandArrowFx(){}

    /** Gathers this frame's stabs (within 96 blocks of the camera); returns whether any show. */
    public static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();delta=tickDelta;
        if(mc.world==null)return false;
        for(var s:StandArrowClient.all().values()){
            if(s.cutShort())continue;
            if(!(mc.world.getEntityById(s.entity) instanceof PlayerEntity p)||!p.getUuid().equals(s.user))continue;
            if(p.getPos().squaredDistanceTo(camera)>96*96)continue;
            float t=s.age(tickDelta);
            if(t<0||t>s.shows()+2.6f&&t>ArrowStabPose.REVEAL+2.6f)continue;
            var f=ArrowStabPose.frame(t,s.fate,s.shows());
            var pose=f.pose();
            Vec3d chest=StandArrowClient.world(p,tickDelta,pose,ArrowStabPose.ENTRY);
            Vec3d in=StandArrowClient.way(p,tickDelta,pose,ArrowStabPose.WAY_IN);
            Vec3d tip=null,nock=null,head=null,prevTip=null;
            var a=f.arrow();
            if(a!=null){
                tip=StandArrowClient.world(p,tickDelta,pose,a.at(ArrowStabPose.TIP_U));
                nock=StandArrowClient.world(p,tickDelta,pose,a.at(ArrowStabPose.NOCK_U));
                head=StandArrowClient.world(p,tickDelta,pose,a.at((ArrowStabPose.HEAD_U+ArrowStabPose.TIP_U)/2));
                if(!a.in()&&t>.04f){
                    // where the tip was a moment ago (the streak behind it as it is driven in)
                    var before=ArrowStabPose.frame(t-.04f,s.fate,s.shows());var b=before.arrow();
                    if(b!=null)prevTip=StandArrowClient.world(p,tickDelta,before.pose(),b.at(ArrowStabPose.TIP_U));
                }
            }
            float yaw=net.minecraft.util.math.MathHelper.lerpAngleDegrees(tickDelta,p.prevBodyYaw,p.bodyYaw);
            float near=Ease.smooth((float)camera.distanceTo(chest),.45f,1.3f);
            Vec3d feet=p.getLerpedPos(tickDelta);
            Vec3d[] knees=new Vec3d[2];
            for(int i=0;i<2;i++){Vec3d k=StandArrowClient.model(p,tickDelta,ArrowStabPose.KNEELING_KNEES[i]);knees[i]=new Vec3d(k.x,feet.y+.02,k.z);}
            FRAME.add(new Shown(s,t,f,chest,in,tip,nock,head,prevTip,f.glow(),feet,way(yaw),(int)(s.start*37+s.user.getLeastSignificantBits()),near,knees));
        }
        return !FRAME.isEmpty();
    }
    public static boolean showing(){return !FRAME.isEmpty();}

    /** A burst's fade: in over a frame or two at {@code at}, out by {@code life} seconds. */
    static float fade(float t,float at,float life){return t<at?0:(float)Math.pow(Math.max(0,1-(t-at)/life),.8)*Ease.smooth(t,at,at+.03f);}
    /** Two ways square to {@code d} (unit). */
    static Vec3d[] square(Vec3d d){
        Vec3d u=d.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-4)u=d.crossProduct(new Vec3d(1,0,0));u=u.normalize();
        return new Vec3d[]{u,u.crossProduct(d).normalize()};
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** Dark ink under the light: the blow's burst, the choice's burst. */
    public static void alpha(EffectMesh m){
        for(var s:FRAME){
            float a=fade(s.t,ArrowStabPose.HIT,.32f)*s.near;
            if(a>0)burst(m,s.chest,.46*(1+.4*Ease.out(s.t,ArrowStabPose.HIT,ArrowStabPose.HIT+.2f)),.34,10,s.seed,INK,INK,.6f*a,.6f*a);
            if(s.fate()==Fate.UNKNOWN)continue;
            float c=fade(s.since(),0,s.fate()==Fate.AWAKEN?.45f:.38f)*s.near;
            if(c>0)burst(m,s.chest,(s.fate()==Fate.AWAKEN?.78:.5)*(1+.35*Ease.out(s.since(),0,.25f)),.32,12,s.seed+7,s.fate()==Fate.DIE?BLOOD:INK,INK,.62f*c,.62f*c);
        }
    }
    public static void additive(EffectMesh m){
        for(var s:FRAME){
            raised(m,s);plunge(m,s);blow(m,s);drawnIn(m,s);
            if(s.fate()==Fate.AWAKEN)chosen(m,s);
            else if(s.fate()==Fate.DIE)rejected(m,s);
        }
    }
    /** Looked at, raised and aimed: its golden head glints and motes of light circle it. */
    static void raised(EffectMesh m,Shown s){
        if(s.head==null||s.t>ArrowStabPose.HIT)return;
        float g=s.glow*(1-Ease.smooth(s.t,ArrowStabPose.LOAD-.02f,ArrowStabPose.LOAD+.12f))*s.near;if(g<.01f)return;
        float tw=.65f+.35f*(float)Math.sin(s.t*11+s.seed);
        m.softDisc(s.head,.26,GOLD,.32f*g);m.softDisc(s.head,.09,WHITE,.4f*g*tw);
        star(m,s.head,.22*(.8+.4*tw),GOLD,.75f*g*tw);
        Vec3d axis=s.tip.subtract(s.nock).normalize();Vec3d[] q=square(axis);
        for(int k=0;k<6;k++){
            double ang=s.t*3.4+k*Math.PI/3,r=.17+.04*Math.sin(s.t*5+k);
            Vec3d p=s.head.add(q[0].multiply(Math.cos(ang)*r)).add(q[1].multiply(Math.sin(ang)*r)).add(axis.multiply(.06*Math.sin(s.t*4+k*1.7)));
            m.softDisc(p,.035,k%2==0?GOLD:WHITE,.8f*g);
        }
    }
    /** A four-point glint turned to the camera (a glint's light, not a thing in the world). */
    static void star(EffectMesh m,Vec3d p,double r,int rgb,float a){
        Vec3d n=m.camera().subtract(p).normalize(),u=n.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(n).normalize();
        for(Vec3d d:new Vec3d[]{u,v,u.negate(),v.negate()}){m.needle(p,p.add(d.multiply(r)),r*.18,0,rgb,a);m.needle(p,p.add(d.multiply(r*.55)),r*.07,0,WHITE,a);}
    }
    /** Driven in: a streak of light behind its tip, brighter the faster it goes. */
    static void plunge(EffectMesh m,Shown s){
        if(s.prevTip==null||s.t<ArrowStabPose.LOAD||s.t>ArrowStabPose.HIT+.02f)return;
        double speed=s.tip.distanceTo(s.prevTip)/.04;if(speed<1.5)return;
        float a=(float)Math.min(1,(speed-1.5)/6)*s.near;if(a<.01f)return;
        m.softTaper(s.prevTip,s.tip,.02,.1,GOLD,.55f*a);m.needle(s.prevTip,s.tip,.035,.85,WHITE,.6f*a);
    }
    /** It goes in: a burst at the chest, a ring round the arrow, one running out round the body, sparks thrown out. */
    static void blow(EffectMesh m,Shown s){
        float a=fade(s.t,ArrowStabPose.HIT,.32f);if(a<=0)return;
        float u=Ease.out(s.t,ArrowStabPose.HIT,ArrowStabPose.HIT+.3f),n=s.near,out=n+(1-n)*u;
        burst(m,s.chest,.46*(1+.4*Ease.out(s.t,ArrowStabPose.HIT,ArrowStabPose.HIT+.2f)),.34,10,s.seed,GOLD,VIOLET,.95f*a*n,.85f*a*n);
        m.softDisc(s.chest,.7,GOLD,.35f*a*n);FxLib.flash(m,s.chest,.42,GOLD,.9f*fade(s.t,ArrowStabPose.HIT,.16f)*n);
        Vec3d[] q=square(s.in);
        FxLib.ringPlane(m,s.chest.subtract(s.in.multiply(.05+.25*u)),q[0],q[1],.1+.85*u,.07,GOLD,.8f*a*n);
        FxLib.ring(m,s.chest,.35+1.9*u,.12,VIOLET,.6f*a*out);
        FxLib.sparks(m,s.chest,16,.85,.06,GOLD,.85f*a*n,s.seed+3,.25f+.75f*u);
    }
    /**
     * Being drawn in: light leaking out of the wound in rays, motes drawn into it, a ring running out round the body
     * with every beat of the heart, the wound and the arrow standing out of it glowing brighter and brighter (on the
     * knees); as everything goes still, the rays drawn back in.
     */
    static void drawnIn(EffectMesh m,Shown s){
        float t=s.t,end=Math.min(s.stab.shows(),ArrowStabPose.REVEAL+.05f);
        if(t<ArrowStabPose.HIT||t>s.stab.shows()+.3f)return;
        float rings=Ease.smooth(t,ArrowStabPose.HIT+.1f,ArrowStabPose.HIT+.35f)*(1-Ease.smooth(t,end,end+.25f)),on=rings*s.near;
        float build=Ease.smooth(t,ArrowStabPose.KNEES-.3f,ArrowStabPose.STILL-.05f),hush=Ease.smooth(t,ArrowStabPose.STILL-.06f,ArrowStabPose.STILL+.06f);
        if(rings<=.01f)return;
        Vec3d out=s.in.negate();Vec3d[] q=square(out);
        // the wound
        float pulse=0;for(float b:ArrowStabPose.BEATS)pulse+=ArrowStabPose.kick(t,b,.12f);
        m.softDisc(s.chest,.2+.18*build+.08*pulse,GOLD,(.35f+.4f*build+.3f*pulse)*on);
        m.softDisc(s.chest,.07+.05*build,WHITE,(.4f+.4f*build)*on);
        // rays leaking out of it, flickering
        for(int k=0;k<9;k++){
            double ang=Ease.hash(s.seed+k*13)*Math.PI*2,spread=.35+.6*Ease.hash(s.seed+k*17);
            Vec3d d=out.add(q[0].multiply(Math.cos(ang)*spread)).add(q[1].multiply(Math.sin(ang)*spread)).normalize();
            float flick=.55f+.45f*(float)Math.sin(t*(13+7*Ease.hash(s.seed+k*19))+k*2.1);
            double len=(.25+.45*Ease.hash(s.seed+k*23))*(.5+.5*build+.35*pulse)*(1-.8*hush);
            m.needle(s.chest.add(d.multiply(.04)),s.chest.add(d.multiply(len)),.05+.03*build,.15,k%3==0?WHITE:GOLD,.75f*on*flick*(.4f+.6f*build)*(1-.6f*hush));
        }
        // motes drawn into the wound
        if(s.near>.5f){FxLib.converge(m,s.chest,1.25,26,t,ArrowStabPose.KNEES,ArrowStabPose.STILL-.08f,GOLD,s.seed+5);FxLib.converge(m,s.chest,.9,16,t,ArrowStabPose.ABSORB-.4f,ArrowStabPose.STILL,GOLD,s.seed+9);}
        // the heart's beats: a ring running out round the body
        for(float b:ArrowStabPose.BEATS){
            float u=(t-b)/.4f;if(u<0||u>=1)continue;
            float a=(float)Math.pow(1-u,.9)*Ease.smooth(u,0,.06f)*rings*(s.near+(1-s.near)*Ease.out(u,0,1));
            FxLib.ring(m,s.chest.add(0,-.1,0),.32+1.25*Ease.out(u,0,1),.08,u<.3f?GOLD:VIOLET,.55f*a*(.6f+.4f*build));
        }
        // the arrow itself, standing out of the chest, glowing as it is drawn in
        if(s.nock!=null){
            float g=s.glow;
            m.softLine(s.chest,s.nock,.16,GOLD,.3f*g*on);m.softLine(s.chest,s.nock,.05,WHITE,.25f*g*on);
        }
    }
    /** Chosen: a burst of gold and violet, a pillar of light, rings over the ground and round the body, sparks rising. */
    static void chosen(EffectMesh m,Shown s){
        float u=s.since();if(u<0||u>2.4f)return;
        float a=fade(u,0,.45f)*s.near,o=Ease.out(u,0,.4f);
        if(a>0){
            burst(m,s.chest,.78*(1+.35*Ease.out(u,0,.25f)),.32,12,s.seed+7,WHITE,VIOLET,.95f*a,.9f*a);
            m.softDisc(s.chest,1.1,VIOLET,.4f*a);m.softDisc(s.chest,.5,GOLD,.5f*a);
            FxLib.flash(m,s.chest,.65,GOLD,fade(u,0,.2f)*s.near);
            FxLib.sparks(m,s.chest,22,1.4,.07,GOLD,.8f*a,s.seed+11,.3f+.7f*o);
        }
        // the pillar of light from the feet, rising and fading
        float p=fade(u,0,.9f)*s.near;
        if(p>0){
            double top=1.2+3.6*Ease.out(u,0,.35f),w=.55*(1-.4*Ease.smooth(u,.2f,.9f));
            Vec3d base=s.feet.add(0,.05,0),up=base.add(0,top,0);
            m.needle(base,up,w*2.4,.22,VIOLET,.32f*p);m.needle(base,up,w*1.3,.22,LILAC,.5f*p);m.needle(base,up,w*.5,.2,WHITE,.7f*p);
        }
        // rings over the ground and round the body
        float r=fade(u,0,.6f);
        if(r>0){
            FxLib.ring(m,s.feet.add(0,.06,0),.4+3.4*Ease.out(u,0,.55f),.2,GOLD,.7f*r);
            FxLib.ring(m,s.feet.add(0,.06,0),.3+2.2*Ease.out(u,.06f,.6f),.12,VIOLET,.6f*r);
            FxLib.ring(m,s.chest,.4+2.6*Ease.out(u,0,.45f),.12,LILAC,.55f*r*(s.near+(1-s.near)*Ease.out(u,0,.45f)));
        }
        // sparks of gold and violet rising off the body
        for(int k=0;k<26;k++){
            float born=.03f+.6f*(float)Ease.hash(s.seed+k*31),life=.7f+.5f*(float)Ease.hash(s.seed+k*37),v=(u-born)/life;
            if(v<0||v>=1)continue;
            double ang=Ease.hash(s.seed+k*41)*Math.PI*2,rad=.25+.35*Ease.hash(s.seed+k*43);
            Vec3d q=s.feet.add(Math.cos(ang)*rad*(1+.4*v),.2+1.6*Ease.hash(s.seed+k*47)+1.4*v,Math.sin(ang)*rad*(1+.4*v));
            float al=(float)Math.sin(Math.PI*v)*(.6f+.4f*(float)Math.sin(u*14+k));
            m.softDisc(q,.05*(1-.4*v),k%3==0?WHITE:k%2==0?GOLD:VIOLET,.85f*al);
        }
    }
    /** Rejected: the wound's light turns crimson and gutters out, its rays falling back in. */
    static void rejected(EffectMesh m,Shown s){
        float u=s.since();if(u<0||u>1.2f||s.near<.01f)return;
        float a=fade(u,0,.38f)*s.near;
        if(a>0){
            burst(m,s.chest,.5*(1+.35*Ease.out(u,0,.25f)),.32,12,s.seed+7,CRIMSON,BLOOD,.8f*a,.7f*a);
            m.softDisc(s.chest,.55,CRIMSON,.35f*a);
        }
        float g=(1-Ease.smooth(u,0,.7f))*s.near;
        if(g>0){
            Vec3d out=s.in.negate();Vec3d[] q=square(out);
            for(int k=0;k<9;k++){
                double ang=Ease.hash(s.seed+k*13)*Math.PI*2,spread=.35+.6*Ease.hash(s.seed+k*17);
                Vec3d d=out.add(q[0].multiply(Math.cos(ang)*spread)).add(q[1].multiply(Math.sin(ang)*spread)).normalize();
                double len=(.25+.45*Ease.hash(s.seed+k*23))*g*g;
                float flick=.5f+.5f*(float)Math.sin(u*31+k*2.3);
                m.needle(s.chest.add(d.multiply(.03)),s.chest.add(d.multiply(len)),.05,.15,k%3==0?CRIMSON:BLOOD,.7f*g*flick);
            }
            m.softDisc(s.chest,.18*g,CRIMSON,.6f*g);
        }
    }
    /** Black smoke rising from the chest of one rejected, and dust thrown up where their body lands (fixed planes). */
    public static void dust(Matrix4f matrix,Vec3d camera){
        if(!FxLayer.available()||!KagenoFire.ready())return;
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        List<Vec3d> smoke=new ArrayList<>(),dust=new ArrayList<>();List<float[]> smokeCells=new ArrayList<>(),dustCells=new ArrayList<>();
        for(var s:FRAME){
            // each knee going down onto the ground (where it rests, kneeling)
            for(int i=0;i<2;i++){
                float at=i==0?ArrowStabPose.KNEE:ArrowStabPose.KNEES;
                if(s.t>at-.02f&&s.t<at+1.1f)FxLib.dustRing(s.knees[i],s.t,at,.06,.5,7,.32,dust,dustCells,s.seed+17+i*7);
            }
            if(s.fate()!=Fate.DIE)continue;
            float u=s.since();if(u<0||u>3)continue;
            for(int k=0;k<9;k++){
                float born=.05f+.9f*(float)Ease.hash(s.seed+k*53),life=1.1f+.5f*(float)Ease.hash(s.seed+k*59),v=(u-born)/life;
                if(v<0||v>=1)continue;
                double drift=(Ease.hash(s.seed+k*61)-.5)*.5;
                smoke.add(s.chest.add(drift*v,.1+.95*v+.05*(k%3),(Ease.hash(s.seed+k*67)-.5)*.5*v));
                smokeCells.add(new float[]{(float)(.13+.3*v),.55f*(float)Math.sin(Math.PI*Math.min(1,v*1.4))*(1-v)*s.near,0,.2f+.5f*v,s.seed+k*71});
            }
            // where the body lands (the chest, as it lies): a ring of dust over the ground
            float landed=ArrowStabPose.LANDS_AT;
            if(u>landed-.02f&&u<landed+1.2f){
                var lying=s.chest;
                double floor=s.feet.y;
                FxLib.dustRing(new Vec3d(lying.x,floor,lying.z),u,landed,.25,1.3,11,.55,dust,dustCells,s.seed+13);
            }
        }
        float age=(float)(world.getTime()+delta);
        if(!smoke.isEmpty())KagenoFire.cells(matrix,camera,smoke,smokeCells,age);
        if(!dust.isEmpty())KagenoFire.dust(matrix,camera,dust,dustCells,age);
    }
    /** The effect layer's lights: the arrow's glow, the blow, the wound and the choice. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            if(s.head!=null&&s.t<ArrowStabPose.HIT)f.light(s.head,0xffd889,2.5,.7f*s.glow);
            float b=fade(s.t,ArrowStabPose.HIT,.35f);if(b>0)f.light(s.chest,0xffe2a0,5,2.2f*b);
            if(s.t>ArrowStabPose.HIT&&s.t<s.stab.shows()+.2f){
                float pulse=0;for(float beat:ArrowStabPose.BEATS)pulse+=ArrowStabPose.kick(s.t,beat,.12f);
                f.light(s.chest,0xffd889,3.5,(.4f+.8f*Ease.smooth(s.t,ArrowStabPose.KNEES-.3f,ArrowStabPose.STILL)+.6f*pulse)*(1-Ease.smooth(s.t,s.stab.shows(),s.stab.shows()+.2f)));
            }
            float u=s.since();
            if(s.fate()==Fate.AWAKEN&&u>=0&&u<1.2f)f.light(s.chest,0xb89cff,7,2.6f*fade(u,0,1.1f));
            if(s.fate()==Fate.DIE&&u>=0&&u<.9f)f.light(s.chest,0xd0283c,3,1.3f*fade(u,0,.8f));
        }
    }
}
