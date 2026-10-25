package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.*;

/**
 * The light of Star Platinum's inhale (2.61.0), drawn with its punches' on the effect layer (KagenoRenderer), in their
 * colours and their way: light that adds up and blooms, crisp bursts inked dark round their edges, and none of it over
 * the whole screen (the stand's rule since 2.56.0: no shake, flash or impact frame). The air in front of the stand comes
 * in down a cone to its mouth (under its scarf: it has no mouth): streaks of air spiral in from beyond its reach,
 * faster and thinner the nearer they come, rings of air close in along the cone, dust on the ground in front of it is
 * dragged in, and its mouth glows faintly. As it catches someone the air folds in on them in a burst, then swirls round
 * them and streams off them into its mouth for as long as it holds them; a guard that takes it flashes blue and throws
 * its sparks aside. As it ends it lets the air go in a puff. Every effect is a function of the inhale's clock.
 */
public final class StandInhaleFx {
    /** The cone the air comes in down: from this far ahead of its user (blocks), this high over their feet there (a chest's height), this wide. */
    static final double FAR=3.7,FAR_UP=1.05,WIDTH=1.45;
    /** How many streaks of air come in at once, and how long the catch's burst and the air let go last (seconds). */
    static final int STREAKS=34;
    static final float CATCH=.36f,RELEASE=.4f;
    /**
     * This frame's inhales near the camera: the inhale, its clock and end, how hard it sucks, where the stand stands,
     * its mouth, and the cone (its way in from the far end to the mouth, its length, and two ways across it).
     */
    record Shown(UUID user,StandInhale.Inhale inhale,float t,float end,float suck,StandPresence.Body body,Vec3d mouth,Vec3d axis,double length,Vec3d across,Vec3d up,Vec3d feet,Vec3d way,int seed){}
    private static final List<Shown> FRAME=new ArrayList<>();
    private static float delta;
    private StandInhaleFx(){}

    /** Gathers this frame's inhales (within 96 blocks of the camera, their stand drawn); returns whether any show. */
    public static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();delta=tickDelta;
        if(mc.world==null)return false;
        for(var e:StandInhale.all().entrySet()){
            var i=e.getValue();var user=mc.world.getPlayerByUuid(e.getKey());
            if(user==null||user.getPos().squaredDistanceTo(camera)>96*96)continue;
            float t=i.age(tickDelta),end=i.ends();
            if(t<0||t>end+1f)continue;
            var body=StandPresence.body(e.getKey());if(body==null)continue;
            Vec3d feet=user.getLerpedPos(tickDelta),way=way(i.yaw(tickDelta));
            Vec3d mouth=on(StandInhale.mouth(t,end),body);
            Vec3d far=feet.add(way.multiply(FAR)).add(0,FAR_UP,0),in=mouth.subtract(far);
            double length=Math.max(.5,in.length());Vec3d axis=in.multiply(1/length);
            Vec3d across=right(way),up=across.crossProduct(axis).normalize();
            if(up.y<0)up=up.negate();
            FRAME.add(new Shown(e.getKey(),i,t,end,StandInhale.suction(t,end),body,mouth,axis,length,across,up,feet,way,(int)(i.start*31+e.getKey().getLeastSignificantBits())));
        }
        return !FRAME.isEmpty();
    }
    public static boolean showing(){return !FRAME.isEmpty();}

    // ───────────────────────────── where things are ─────────────────────────────

    /**
     * A point {@code f} of the way down the cone from its far end (0) to the mouth (1), on a spiral: {@code rad} out
     * from its middle at the far end (closing in as it goes), turned {@code ang} radians round it and on by {@code spin} as it comes in.
     */
    static Vec3d cone(Shown s,double f,double rad,double ang,double spin){
        double along=s.length*(1-f),r=rad*Math.pow(Math.max(0,1-f),1.15)+.02,a=ang+spin*f;
        return s.mouth.subtract(s.axis.multiply(along)).add(s.across.multiply(Math.cos(a)*r)).add(s.up.multiply(Math.sin(a)*r));
    }
    /** The chest of one it caught or whose guard took it, this frame (null if this client does not see them). */
    static Vec3d chest(int id){
        var world=MinecraftClient.getInstance().world;if(world==null)return null;
        return world.getEntityById(id) instanceof LivingEntity l?l.getLerpedPos(delta).add(0,l.getHeight()*.6,0):null;
    }
    static double width(int id){var world=MinecraftClient.getInstance().world;return world!=null&&world.getEntityById(id) instanceof LivingEntity l?l.getWidth()*.5:.3;}
    /** How far through the catch's burst it is (0 to 1; outside it, below 0 or past 1). */
    static float catchU(Shown s){return Float.isNaN(s.inhale.caughtAt)?-1:(s.t-s.inhale.caughtAt)/CATCH;}
    /** How strongly the air swirls round and streams off whom it holds now (0 to 1): from its catch until it ends. */
    static float holding(Shown s){
        if(Float.isNaN(s.inhale.caughtAt))return 0;
        return Ease.smooth(s.t-s.inhale.caughtAt,0,.12f)*(1-Ease.smooth(s.t-s.end,0,.12f));
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** Dark ink under the light: the catch's bursts on whom it caught, and the ring of the air let go. */
    public static void alpha(EffectMesh m){
        for(var s:FRAME){
            float u=catchU(s);
            if(u>=0&&u<1)for(var c:s.inhale.caught){
                var p=chest(c.id());if(p==null)continue;
                float a=(float)Math.pow(1-u,.8)*Ease.smooth(u,0,.06f);
                burst(m,p,.42*(1-.25*u)*1.14,.36,9,s.seed+c.id()*7,INK,INK,.62f*a,.62f*a);
            }
            float r=(s.t-s.end)/RELEASE;
            if(r>=0&&r<1&&!s.inhale.on()){
                Vec3d c=s.mouth.subtract(s.axis.multiply(.12+.9*Ease.out(r,0,1)));
                FxLib.ringPlane(m,c,s.across,s.up,.18+.85*Ease.out(r,0,1),.09,INK,.45f*(1-r));
            }
        }
    }
    public static void additive(EffectMesh m){
        for(var s:FRAME){
            if(s.suck>.002f){funnel(m,s);streaks(m,s);rings(m,s);mouth(m,s);}
            caught(m,s);blocked(m,s);release(m,s);
        }
    }
    /**
     * The funnel the air comes in down: faint bands of air along its sides, wound round it and turning slowly, wide out
     * in front and narrowing into the mouth, so that the way the air goes reads from any side.
     */
    static void funnel(EffectMesh m,Shown s){
        for(int k=0;k<6;k++){
            double ang=k*Math.PI/3+s.t*1.6;
            Vec3d a=cone(s,0,WIDTH*.95,ang,2.2),b=cone(s,.45,WIDTH*.95,ang,2.2),c=cone(s,.8,WIDTH*.95,ang,2.2),d=cone(s,.96,WIDTH*.95,ang,2.2);
            float al=s.suck*(.75f+.25f*(float)Math.sin(s.t*5+k*1.7));
            m.softTaper(a,b,.26,.16,VIOLET,.12f*al);m.softTaper(b,c,.16,.09,LILAC,.15f*al);m.softTaper(c,d,.09,.04,LILAC,.18f*al);
        }
    }
    /**
     * The streaks of air coming in down the funnel: each its own beat, spiralling in from beyond the inhale's reach and
     * faster the nearer the mouth (a thin white core on some), into the mouth itself.
     */
    static void streaks(EffectMesh m,Shown s){
        for(int k=0;k<STREAKS;k++){
            float period=.3f+.2f*(float)Ease.hash(s.seed+k*7),phase=(float)Ease.hash(s.seed+k*11)*period;
            float u=((s.t+phase)%period)/period,f=(float)Math.pow(u,1.6);
            float a=s.suck*(float)Math.sin(Math.PI*Math.min(1,u*1.08))*(1-Ease.smooth(f,.9f,.99f))*.95f;
            if(a<.01f)continue;
            double rad=WIDTH*(.3+.7*Ease.hash(s.seed+k*13)),ang=Ease.hash(s.seed+k*17)*Math.PI*2,spin=2.3+.8*Ease.hash(s.seed+k*19);
            // the streak's tail, a little way back along its spiral (longer as it speeds in)
            double tail=.14+.28*f;
            int c=k%4==0?WHITE:k%3==0?LILAC:k%2==0?BLUE:VIOLET;double w=(.065+.035*Ease.hash(s.seed+k*23))*(1-.35*f);
            Vec3d q0=cone(s,Math.max(0,f-tail),rad,ang,spin),q1=cone(s,Math.max(0,f-tail*.5),rad,ang,spin),q2=cone(s,f,rad,ang,spin);
            m.softTaper(q0,q1,0,w*.6,c,a*.8f);m.softTaper(q1,q2,w*.6,w,c,a);
            if(c==WHITE||c==LILAC)m.needle(q1,q2,w*.4,.8,WHITE,a*(c==WHITE?.85f:.45f));
        }
    }
    /** Rings of air closing in down the cone to the mouth, one after another. */
    static void rings(EffectMesh m,Shown s){
        for(int k=0;k<3;k++){
            float period=.52f,u=((s.t+k*period/3)%period)/period,f=(float)Math.pow(u,1.35);
            Vec3d c=s.mouth.subtract(s.axis.multiply(s.length*(1-f)*.94));
            double r=WIDTH*.72*(1-f)+.06;
            float a=s.suck*.5f*(float)Math.sin(Math.PI*u);
            FxLib.ringPlane(m,c,s.across,s.up,r,.06+.05*(1-f),k==1?LILAC:VIOLET,a);
        }
    }
    /** A faint glow where the air goes in under its scarf (small: it must not cover its face). */
    static void mouth(EffectMesh m,Shown s){
        float flicker=.85f+.15f*(float)Math.sin(s.t*31+s.seed);
        Vec3d p=s.mouth.subtract(s.axis.multiply(.08));
        m.softDisc(p,.34,VIOLET,.4f*s.suck*flicker);m.softDisc(p,.11,WHITE,.42f*s.suck*flicker);
    }
    /**
     * Whom it caught: the air folds in on each as the catch comes (a burst at their chest, rings closing in round them,
     * motes drawn into them), then swirls round them and streams off them into its mouth for as long as it holds them.
     */
    static void caught(EffectMesh m,Shown s){
        float u=catchU(s),hold=holding(s);
        for(var c:s.inhale.caught){
            var p=chest(c.id());if(p==null)continue;
            int seed=s.seed+c.id()*7;
            if(u>=0&&u<1){
                float a=(float)Math.pow(1-u,.8)*Ease.smooth(u,0,.06f);
                burst(m,p,.42*(1-.25*u),.36,9,seed,LILAC,VIOLET,.85f*a,.75f*a);
                m.softDisc(p,.5,VIOLET,.3f*a);m.softDisc(p,.14,WHITE,.5f*a*(1-u));
                // rings closing in on them: round them flat, and square to the way the air goes
                FxLib.ring(m,p.subtract(0,.25,0),.25+.95*(1-Ease.out(u,0,1)),.07,LILAC,.7f*a);
                FxLib.ringPlane(m,p,s.across,s.up,.2+1.05*(1-Ease.out(u,0,1)),.08,VIOLET,.6f*a);
            }
            FxLib.converge(m,p,1.4,12,s.t,s.inhale.caughtAt-.12f,s.inhale.caughtAt+.1f,LILAC,seed);
            if(hold<=.01f)continue;
            float sh=hold*s.suck;
            double w=width(c.id())+.22;
            // the air swirling round them, rising
            for(int k=0;k<6;k++){
                float rise=(float)((s.t*.85+k/6.0+Ease.hash(seed+k))%1.0);
                double ang=s.t*6.5+k*Math.PI*2/6,h=-.75+1.5*rise,r=w*(1+.12*Math.sin(s.t*3+k));
                Vec3d a0=p.add(Math.cos(ang-.55)*r,h-.06,Math.sin(ang-.55)*r),a1=p.add(Math.cos(ang-.25)*r,h-.03,Math.sin(ang-.25)*r),a2=p.add(Math.cos(ang)*r,h,Math.sin(ang)*r);
                float al=.45f*sh*(float)Math.sin(Math.PI*rise);
                int col=k%3==0?LILAC:k%2==0?BLUE:VIOLET;
                m.softTaper(a0,a1,0,.05,col,al*.8f);m.softTaper(a1,a2,.05,.07,col,al);
            }
            // the air streaming off them into its mouth
            Vec3d to=s.mouth.subtract(p);double d=to.length();if(d<.05)continue;
            Vec3d dir=to.multiply(1/d),side=dir.crossProduct(new Vec3d(0,1,0));side=side.lengthSquared()<1e-4?s.across:side.normalize();
            for(int k=0;k<7;k++){
                float v=(float)((s.t*2.1+k/7.0+Ease.hash(seed+k*3)*.3)%1.0);
                double wob=Math.sin(v*Math.PI*2+k)*.12*(1-v);
                Vec3d q=p.add(dir.multiply(d*v)).add(side.multiply(wob)).add(0,Math.cos(v*5+k)*.08*(1-v),0);
                float al=.55f*sh*(float)Math.sin(Math.PI*v)*(1-Ease.smooth(v,.8f,1f));
                m.needle(q,q.add(dir.multiply(.22+.12*v)),.05,.75,k%4==0?WHITE:k%2==0?LILAC:VIOLET,al);
            }
        }
    }
    /** Whose guard took it: a flash of blue on their guard and its sparks thrown aside, as the rush's on a guard. */
    static void blocked(EffectMesh m,Shown s){
        float u=catchU(s);if(u<0||u>=1)return;
        float a=(float)Math.pow(1-u,.8)*Ease.smooth(u,0,.06f);
        for(int id:s.inhale.blocked){
            var c=chest(id);if(c==null)continue;
            Vec3d go=c.subtract(s.feet).multiply(1,0,1);go=go.lengthSquared()<1e-6?s.way:go.normalize();
            Vec3d at=c.subtract(go.multiply(width(id)*.9)),side=right(go),up=up(go,side);int seed=s.seed+id*11;
            burst(m,at,.34,.4,9,seed,GUARD_LIGHT,GUARD,.75f*a,.6f*a);
            FxLib.ringPlane(m,at,side,up,.2+.7*Ease.out(u,0,1),.06,GUARD,.7f*a*(1-u));
            for(int k=0;k<8;k++){
                double ang=(Ease.hash(seed+k*5)-.5)*2.6;
                Vec3d d=side.multiply(Math.sin(ang)*(k%2==0?1:-1)).add(up.multiply(Math.cos(ang)*.6)).subtract(go.multiply(.3)).normalize();
                Vec3d q=at.add(d.multiply(.15+.7*Ease.out(u,0,1)));
                m.needle(q,q.add(d.multiply(.24)),.045,.4,k%2==0?WHITE:GUARD_LIGHT,.8f*a*(1-u));
            }
        }
    }
    /** As it ends: the air it held let go in a puff out of its mouth, a ring running out and streaks thrown on ahead. */
    static void release(EffectMesh m,Shown s){
        float r=(s.t-s.end)/RELEASE;if(r<0||r>=1||s.inhale.on())return;
        float a=(1-r)*Ease.smooth(r,0,.05f);
        Vec3d out=s.axis.negate(),c=s.mouth.add(out.multiply(.12+.9*Ease.out(r,0,1)));
        FxLib.ringPlane(m,c,s.across,s.up,.18+.85*Ease.out(r,0,1),.08,LILAC,.6f*a);
        m.softDisc(s.mouth.add(out.multiply(.2)),.35*(1+r),VIOLET,.25f*a);
        for(int k=0;k<9;k++){
            double ang=Ease.hash(s.seed+k*29)*Math.PI*2,spread=.25+.35*Ease.hash(s.seed+k*31);
            Vec3d d=out.add(s.across.multiply(Math.cos(ang)*spread)).add(s.up.multiply(Math.sin(ang)*spread)).normalize();
            Vec3d q=s.mouth.add(d.multiply(.15+1.3*Ease.out(r,0,1)));
            m.needle(q,q.add(d.multiply(.3*(1-r)+.05)),.05,.35,k%3==0?WHITE:k%2==0?LILAC:VIOLET,.65f*a);
        }
    }
    /** Dust on the ground in front of it dragged in toward the stand while it sucks (the effect layer's anime dust). */
    public static void dust(Matrix4f matrix,Vec3d camera){
        if(!FxLayer.available()||!KagenoFire.ready())return;
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        for(var s:FRAME){
            if(s.suck<.05f)continue;
            var user=world.getPlayerByUuid(s.user);if(user==null||!user.isOnGround())continue;
            for(int k=0;k<10;k++){
                float period=.75f+.3f*(float)Ease.hash(s.seed+k*41),u=(float)(((s.t+Ease.hash(s.seed+k*43)*period)%period)/period);
                double ahead=2.2+1.7*Ease.hash(s.seed+k*47),side=(Ease.hash(s.seed+k*53)-.5)*2.6,f=Math.pow(u,1.6);
                Vec3d from=s.feet.add(s.way.multiply(ahead)).add(right(s.way).multiply(side)),to=s.feet.add(s.way.multiply(1.2)).add(right(s.way).multiply(side*.2));
                dust.add(from.lerp(to,f).add(0,.18+.32*u,0));
                puffs.add(new float[]{(float)(.42*(1-.45*u)),.55f*s.suck*(float)Math.sin(Math.PI*u),.3f,u,s.seed+k*37});
            }
        }
        if(!dust.isEmpty())KagenoFire.dust(matrix,camera,dust,puffs,(float)(world.getTime()+delta));
    }
    /** The effect layer: its mouth lights the world faintly while it sucks, whom it holds glow with the air round them, and the catch flares on them. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            if(s.suck>.01f)f.light(s.mouth,0xc8b8ff,3.5,1.1f*s.suck);
            float hold=holding(s)*s.suck,u=catchU(s);
            for(var c:s.inhale.caught){
                var p=chest(c.id());if(p==null)continue;
                if(hold>.01f)f.light(p,0xd8ccff,2.6,.6f*hold);
                if(u>=0&&u<1)f.light(p,0xe0d4ff,4,1.6f*(1-u));
            }
            if(u>=0&&u<1)for(int id:s.inhale.blocked){var p=chest(id);if(p!=null)f.light(p,0xb8c8ff,4,1.4f*(1-u));}
        }
    }
}
