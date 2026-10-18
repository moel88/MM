package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * H "최강": a 4.97 s scripted cutscene shown to the caster and everyone caught within 5 blocks.
 * Shot 1 worm's-eye launch (the hero rockets up and back, somersaults head-down), shot 2 inverted
 * close-up from the side, shot 3 one take: straight up at the hero (the sky rolls 175°, a four-point
 * star flare, ten magenta zigzag dashes = ten hits), a whip-tilt down to the street, the hero slides
 * in and skids into a three-point crouch (jolt, dolly-zoom, Dutch tilt), shot 4 a two-frame side
 * insert, shot 5 the hand insert and pull-back to the raised-sword stance, a burst of slash lines
 * (hit 11), the downswing and a horizontal beam with a dark crescent along the ground (hit 12).
 * Hard cut back to gameplay at 4.972 s; the beam line and the ground crescent linger.
 * Seconds after the cast; cast frame = (forward, up, right) of the caster; 1 stud = 0.36 blocks.
 */
final class HighestSkill implements Fx.Skill {
    static final double STUD=.36;
    static final float SHOT2=.635f,SHOT3=1.486f,FLARE=1.619f,WHIP=2.570f,STREET=2.687f,LAND=2.737f,SHOT4=3.487f,SHOT5=3.521f,
        BURST=3.838f,DOWN=4.239f,BEAM=4.606f,END=4.972f;
    /** The ten zigzag dashes (one hit each), then the burst and the beam. */
    static final float[] ZIG={2.167f,2.268f,2.334f,2.418f,2.534f,2.668f,2.701f,2.818f,2.885f,2.985f};
    /** Apex of the leap, in the cast frame (up and back, away from the shot-1 camera). */
    static final double APEX_FW=-14,APEX_UP=28;
    /** Street landing spot (shot 3c): the hero slides in ≈4 studs in front of the street camera. */
    static final double LAND_FW=-1.70,RETREAT=5.0,BRAKE_FW=LAND_FW-RETREAT;
    static final float BRAKE=3.20f,DRIVE=3.22f;

    // ───────────────────────────── poses ─────────────────────────────
    static final R6.Pose K1=pose().rArm(-70,90).lArm(-70,-90).blade(-10,90);
    static final R6.Pose K2=pose().rArm(-8,90).lArm(-8,-90).blade(0,90);
    static final R6.Pose K3=pose().body(0,-180,0).rArm(-72,90).lArm(-72,-90).blade(-85,0);
    static final R6.Pose K4=pose().body(0,-180,0).rArm(-85,12).lArm(-85,-12).blade(-82,0).lLeg(0,-90);
    /** Feet braced above the body, hips drawn in before the downward push. */
    static final R6.Pose BRACE=pose().body(0,-155,-8).head(0,-12,0)
        .rLeg(-48,12).lLeg(-58,-18).rArm(-55,75).lArm(-65,-55).blade(-70,100);
    static final R6.Pose PUSH=pose().body(0,-205,-5).head(0,8,0)
        .rLeg(-88,5).lLeg(-85,-6).rArm(-8,95).lArm(-55,-50).blade(5,100);
    static final R6.Pose K7=pose().head(0,-5,0).lArm(25,-10).rArm(-20,60).blade(-30,70);
    // Every ground pose keeps the cast heading; the camera never drives the body or head yaw.
    static final R6.Pose K8=pose().head(0,0,0).rArm(78,-30).blade(78,-150).lArm(-78,-5);
    static final R6.Pose K10=pose().rArm(-42,75).lArm(-90,0).blade(-37,75).rLeg(-90,0).lLeg(-90,0);

    /** Convert a world-space limb direction into the pitched torso's local space. */
    private static R6.Dir unpitch(double right,double up,double forward,double pitch){
        double a=Math.toRadians(pitch),c=Math.cos(a),s=Math.sin(a);
        return new R6.Dir(right,up*c+forward*s,-up*s+forward*c).norm();
    }
    /** Low sliding stance. Both boots skim the floor; the free hand drags on it while braking. */
    static R6.Pose streetPose(double forward,double height,float pitch,boolean braking){
        double a=Math.toRadians(pitch),c=Math.cos(a);
        var p=pose().at(forward,height,0).body(0,pitch,0).head(0,-pitch+24,0)
            .rArm(-15,150).blade(10,170).lArm(-12,-22);
        p.rArm=unpitch(.94,.08,-.33,pitch);
        p.blade=unpitch(.91,-.10,-.40,pitch);
        double hip=height+1.05-.30*c,legUp=(.045-hip)/.75;
        double legFw=Math.sqrt(Math.max(0,1-legUp*legUp-.06*.06));
        p.lLeg=unpitch(-.06,legUp,legFw,pitch);
        p.rLeg=unpitch(.06,legUp,-legFw,pitch);
        if(braking){
            double shoulder=height+1.05+.325*c,handUp=(.07-shoulder)/.625;
            double handFw=-Math.sqrt(Math.max(0,1-handUp*handUp-.10*.10));
            p.lArm=unpitch(-.10,handUp,handFw,pitch);
        }
        return p;
    }
    /** Leap curve: fast start, strong ease-out, apex at ≈0.5 s. */
    static double leap(float t){double u=Math.min(1,t/.5);return 1-Math.pow(1-u,2.4);}
    static final Anim CLIP;
    static {
        List<Key> keys=new ArrayList<>();
        keys.add(k(0,KagenoClips.IDLE));
        keys.add(k(.017,K1,Anim.Ease.LINEAR));
        // Launch: T-pose snapped in 2-3 frames, root on the leap curve; the somersault 0.28-0.53 s.
        for(int frame=2;frame<=38;frame++){
            float t=frame/60f;
            double s=leap(t);R6.Pose p;
            if(t<.28f)p=K2.blade(t<.2f?0:38,90);
            else if(t<.53f){float u=(t-.28f)/.25f;p=R6.Pose.lerp(K2.blade(38,90),K3,u*u*(3-2*u));}
            else p=K3;
            keys.add(k(t,p.at(APEX_FW*s,APEX_UP*s,0),Anim.Ease.LINEAR));
        }
        // Side-on ceiling plant: the splayed leg joins the other foot, hips compress, then
        // both legs extend and the root leaves the contact height before the sky cut.
        keys.add(k(.72,K4.at(APEX_FW-.05,APEX_UP+.05,0)));
        keys.add(k(.94,K4.lLeg(-85,-8).rLeg(-85,5).at(APEX_FW-.10,APEX_UP+.12,0)));
        keys.add(k(1.10,BRACE.body(0,-168,-5).at(APEX_FW-.18,APEX_UP+.28,0)));
        keys.add(k(1.30,BRACE.at(APEX_FW-.22,APEX_UP+.47,0),Anim.Ease.OUT));
        keys.add(k(1.385,BRACE.at(APEX_FW-.22,APEX_UP+.47,0),Anim.Ease.HOLD));
        keys.add(k(1.445,PUSH.at(APEX_FW-.12,APEX_UP-.12,0),Anim.Ease.IN));
        keys.add(k(SHOT3,PUSH.body(0,-238,-5).at(APEX_FW+.30,APEX_UP-1.50,0),Anim.Ease.IN));
        keys.add(k(FLARE,PUSH.body(0,-265,0).at(APEX_FW+.75,APEX_UP-3.5,0),Anim.Ease.LINEAR));
        keys.add(k(LAND-.01,PUSH.body(0,-265,0).at(APEX_FW+.75,APEX_UP-3.5,0),Anim.Ease.HOLD));
        // One continuous ground path: retreat with the left palm on the floor, brake,
        // release the hand, then immediately drive forward into the sword preparation.
        // The torso rides more upright than a dive (43-46°), so the slide reads as a crouched skid, not lying prone.
        keys.add(k(LAND,streetPose(LAND_FW,-.65,44,true),Anim.Ease.CUT));
        keys.add(k(2.82,streetPose(LAND_FW-1.40,-.67,46,true),Anim.Ease.LINEAR));
        keys.add(k(2.96,streetPose(LAND_FW-3.40,-.66,45,true),Anim.Ease.LINEAR));
        keys.add(k(3.10,streetPose(LAND_FW-4.70,-.64,43,true),Anim.Ease.LINEAR));
        keys.add(k(BRAKE,streetPose(BRAKE_FW,-.63,42,true),Anim.Ease.OUT));
        keys.add(k(DRIVE,streetPose(BRAKE_FW,-.61,41,true),Anim.Ease.HOLD));
        keys.add(k(3.28,streetPose(-5.80,-.50,38,false).lArm(-20,-20),Anim.Ease.IN));
        keys.add(k(3.36,streetPose(-3.40,-.40,35,false).lArm(8,-18),Anim.Ease.LINEAR));
        keys.add(k(3.43,streetPose(-1.10,-.25,30,false).lArm(30,-12),Anim.Ease.LINEAR));
        keys.add(k(3.47,streetPose(0,-.12,18,false).lArm(52,-12).rArm(-30,95),Anim.Ease.LINEAR));
        keys.add(k(SHOT4,K7.at(0,-.05,0).body(0,12,0).head(0,-12,0).rArm(-40,80)));
        keys.add(k(3.54,K7.rArm(-10,40)));
        keys.add(k(3.58,K7.rArm(40,-10).blade(50,-20),Anim.Ease.LINEAR));
        // The raised sword is held alive: the arm winds a little higher and the head drops as the light gathers.
        keys.add(k(3.66,K8.rArm(76,-31).blade(75,-148),Anim.Ease.OUT));
        keys.add(k(3.90,K8.rArm(79,-30).blade(79,-151).head(0,4,0)));
        keys.add(k(4.10,K8.rArm(82,-29).blade(83,-153).head(0,8,0).lArm(-74,-8)));
        keys.add(k(DOWN,K8.rArm(84,-28).blade(85,-154).head(0,10,0).lArm(-72,-10),Anim.Ease.OUT));
        keys.add(k(DOWN+.025f,pose().rArm(20,80).blade(10,85).head(0,12,0).rLeg(-90,0).lLeg(-90,0),Anim.Ease.LINEAR));
        // The cut carries the arm past its stop, then it settles and breathes until the hard cut.
        keys.add(k(DOWN+.05f,K10.rArm(-48,74).blade(-44,73).head(0,12,0),Anim.Ease.LINEAR));
        keys.add(k(DOWN+.09f,K10.rArm(-52,72).blade(-49,71).head(0,14,0),Anim.Ease.OUT));
        keys.add(k(DOWN+.24f,K10.head(0,8,0)));
        keys.add(k(4.72f,K10.rArm(-40,75).blade(-35,75).head(0,6,0)));
        keys.add(k(END,K10.rArm(-43,75).blade(-38,75).head(0,7,0)));
        keys.add(k(END+.45f,KagenoClips.IDLE));
        CLIP=Anim.joints(keys.toArray(Key[]::new));
    }
    @Override public Anim clip(int combo){return CLIP;}
    /** The hero is a dot under the flare and along the zigzag. */
    @Override public boolean hidden(float sec){return sec>=FLARE&&sec<LAND;}
    @Override public float lifetime(){return END+1.45f;}

    // ───────────────────────────── camera ─────────────────────────────
    /** Root position of the hero (cast frame) at {@code sec}. */
    static R6.Pose root(float sec){return CLIP.pose(sec);}
    private static KagenoCamera.Shot look(double ex,double ey,double ez,double lx,double ly,double lz,float roll,float fov){return new KagenoCamera.Shot(ex,ey,ez,lx,ly,lz,roll,fov);}
    @Override public KagenoCamera.Shot shot(float t){
        if(t<0||t>=END)return null;
        if(t<SHOT2){
            // Worm's-eye ≈6.5 studs in front at ankle height; tilts up after him with a little lag, slight push.
            var r=root(Math.max(0,t-.05f));double fov=70-8*Ease.out(t,0,.25f)-2*Ease.smooth(t,.25f,SHOT2);
            return look(1.10,.15,2.70,r.fw,r.up+1.0,r.rt,0,(float)fov);
        }
        if(t<SHOT3){
            // Right-rear profile, not an axial view of the inverted torso. Keep the eye at
            // the contact height so compression and the subsequent push are visible.
            var r=root(Math.max(SHOT2,t-.025f));float settle=Ease.out(t,SHOT2,.9f);
            return look(APEX_FW-.50,APEX_UP+1.14,2.38-.15*settle,
                r.fw,r.up+1.02,r.rt,3+4*settle,70);
        }
        double gx=APEX_FW-.25,gz=0;
        if(t<WHIP){
            // Straight up at the hero; the image rotates 175° CCW (yaw of an up-looking camera), quad-out.
            float u=Math.min(1,(t-SHOT3)/1.12f);double th=Math.toRadians(5+175*(1-(1-u)*(1-u)));
            return look(gx,.6,gz,gx+Math.cos(th)*.02,30,gz+Math.sin(th)*.02,-37,70);
        }
        if(t<STREET){
            // Retain the downward whip and tilted horizon through the empty street frame.
            // Approach the same side as the mirrored ground sequence.
            float u=Ease.out(t,WHIP,STREET);double a=Math.toRadians(90*u);
            var end=streetShot(STREET);
            return look(gx+(end.eyeFw()-gx)*u,.6+(end.eyeUp()-.6)*u,end.eyeRt()*u,
                gx+(end.lookFw()-gx)*Math.sin(a),end.lookUp()+Math.cos(a)*10,0,
                -37+(end.roll()+37)*u,70+(end.fov()-70)*u);
        }
        if(t<SHOT4)return streetShot(t);
        if(t<SHOT5){
            // Oblique close-up. Head/body heading stays fixed in the cast frame.
            return look(.95,1.30,.75,0,1.36,0,-12,70);
        }
        double d=1.05+1.65*Ease.out(t,3.605f,3.82f),az=Math.toRadians(38);
        float roll=-6*(1-Ease.out(t,3.605f,3.82f));
        if(t>=4.289f&&t<4.34f)roll+=(float)(1.3*Math.sin(Math.PI*Ease.unit(t,4.289f,4.34f)));
        else if(t>=4.372f&&t<4.42f)roll-=(float)(1.0*Math.sin(Math.PI*Ease.unit(t,4.372f,4.42f)));
        // Reverse only the viewing side: preserve the 38-degree angle, lens and pull-back.
        return look(Math.cos(az)*d,1.08,Math.sin(az)*d,0,1.08,0,roll,70);
    }
    /** Time, eye forward/up/right, target height, roll, FOV, target lead. */
    private static final double[][] STREET_CAMERA={
        {DRIVE,.06-.55*RETREAT,.95,2.60+.16*RETREAT,.80,-6,50,1},
        {3.36,-1.05,.85,2.65,.87,-9,52,0},
        {3.43,.65,1.0,1.85,1.05,-12,60,0},
        {SHOT4,.95,1.30,.75,1.36,-12,70,0}
    };
    private static KagenoCamera.Shot streetShot(float t){
        var r=root(Math.max(LAND,t));
        if(t<=DRIVE){
            // A root value of -5 alone is not enough: the previous dolly-zoom made
            // the retreat grow toward the viewer. Keep the lens fixed and move the
            // camera only 55% as far as the body. Depth must increase until braking.
            double back=LAND_FW-r.fw;
            float roll=t<LAND?-9+6*Ease.smooth(t,STREET,LAND):-3-3*Ease.smooth(t,LAND,DRIVE);
            return look(.06-.55*back,.95,2.60+.16*back,
                r.fw+.20*back,.80,0,roll,50);
        }
        int i=1;while(i<STREET_CAMERA.length-1&&t>STREET_CAMERA[i][0])i++;
        var a=STREET_CAMERA[i-1];var b=STREET_CAMERA[i];
        float u=Ease.smooth(t,(float)a[0],(float)b[0]);double[] v=new double[8];
        for(int n=1;n<8;n++)v[n]=a[n]+(b[n]-a[n])*u;
        return look(v[1],v[2],v[3],r.fw+v[7],v[4],0,(float)v[5],(float)v[6]);
    }
    @Override public float shotBlend(float sec){return sec>=0&&sec<END?1:0;}

    // ───────────────────────────── effects ─────────────────────────────
    /** Zigzag polyline in the sky (cast frame): starts at the hero, ten sharp segments descending. */
    static Vec3d[] zigzag(Fx c){
        int seed=(int)c.cast.id().getLeastSignificantBits();Vec3d[] pts=new Vec3d[11];
        double x=APEX_FW-.25,y=APEX_UP+1.2,z=0,dir=Ease.hash(seed)*Math.PI*2;
        pts[0]=c.at0(x,y,z);
        for(int i=1;i<=10;i++){
            double len=14+16*Ease.hash(seed+i*7);
            // Sharp acute turns: the path doubles back by 135-155°.
            if(i>1)dir+=Math.toRadians((Ease.hash(seed+i*11)>.5?1:-1)*(135+20*Ease.hash(seed+i*13)));
            x+=Math.cos(dir)*len;z+=Math.sin(dir)*len;y-=1.6+.6*Ease.hash(seed+i*17);
            // Keep the pattern centred over the landing area.
            x=APEX_FW+(x-APEX_FW)*.8;z*=.8;
            pts[i]=c.at0(x,y,z);
        }
        return pts;
    }
    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;
        launch(c,m,t);
        flare(c,m,t,true);
        zig(c,m,t,true);
        skid(c,m,t);
        gather(c,m,t);
        burst(c,m,t,true);
        beam(c,m,t,true);
        sparkles(c,m,t);
    }
    /** The launch: a flash and a shock ring over the street where he kicks off, and a pink-violet trail up after him. */
    static void launch(Fx c,EffectMesh m,float t){
        if(t>=.6f)return;
        Vec3d o=c.at0(0,.05,0);float u=Ease.unit(t,0,.45f),k=FxLib.kick(t,.01f,.12f);
        m.softDisc(o.add(0,.4,0),1.8*k+.2,0xf0c8ff,.8f*k);
        FxLib.ring(m,o,.3+4.2*Ease.out(t,0,.4f),.5,0xe0b8ff,.85f*(1-u));
        FxLib.ring(m,o,.2+2.4*Ease.out(t,0,.3f),.3,0xff7af0,.7f*(1-Ease.unit(t,0,.3f)));
        if(t<.5f){var r=root(t);Vec3d hero=c.at0(r.fw,r.up+.9,r.rt);float a=(1-Ease.smooth(t,.25f,.5f));
            m.softTaper(o.add(0,.3,0),hero,.05,.55,0xb070f0,.55f*a);m.softTaper(o.add(0,.3,0),hero,.02,.2,0xfff0ff,.8f*a);}
    }
    /** Sparks thrown off the street where the free hand and the boots drag through the skid. */
    static void skid(Fx c,EffectMesh m,float t){
        if(t<LAND||t>=BRAKE+.35f)return;
        int last=(int)Math.floor((t-LAND)*120);
        for(int k=Math.max(0,last-30);k<=last;k++){
            float born=LAND+k/120f,age=t-born,life=.16f+.12f*(float)Ease.hash(k*7+901);if(born>BRAKE||age<0||age>=life)continue;
            var r=root(born);Vec3d at=c.at0(r.fw+(Ease.hash(k*3+902)-.5)*.3,.06,Ease.hash(k*5+903)>.5?-.38:.12);
            Vec3d v=c.vec(1.4+1.8*Ease.hash(k*11+904),.8+1.6*Ease.hash(k*13+905),(Ease.hash(k*17+906)-.5)*1.6);
            Vec3d q=at.add(v.multiply(age)).add(0,-2*age*age,0);float a=1-age/life;
            m.needle(q.subtract(v.normalize().multiply(.18)),q,.04,.3,k%3==0?0xffffff:0xffb0f0,a);
        }
    }
    /** As the burst flickers round him, motes of light are drawn into the raised sword and a violet glow swells round him. */
    static void gather(Fx c,EffectMesh m,float t){
        if(t<BURST-.1f||t>=DOWN+.05f)return;
        var hb=KagenoClips.blade(c.origin,c.cast.yaw(),CLIP.pose(t));Vec3d tip=hb[0].add(hb[1].multiply(1.1));
        FxLib.converge(m,tip,2.4,22,t,BURST-.1f,DOWN,0xd8b8ff,951);
        float swell=Ease.smooth(t,BURST,DOWN)*(1-Ease.smooth(t,DOWN,DOWN+.05f));
        m.softDisc(tip,.35+.4*swell,0xf4e6ff,.7f*swell);m.softDisc(c.at0(0,1.1,0),1.6+.4*swell,0x6a3cd6,.22f*swell);
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;
        flare(c,m,t,false);
        zig(c,m,t,false);
        burst(c,m,t,false);
        smear(c,m,t);
        crescent(c,m,t);
        beam(c,m,t,false);
    }
    /** The finisher follows the cast direction, independently of the side camera. */
    static Vec3d fin(Fx c,double fw,double up,double rt){return c.at0(fw,up,rt);}
    /** Camera screen axes (right, up), including the cutscene roll. */
    static Vec3d[] screen(Fx c){
        var cam=c.mc.gameRenderer.getCamera();var f=cam.getHorizontalPlane();var u=cam.getVerticalPlane();
        Vec3d fw=new Vec3d(f.x(),f.y(),f.z()),up=new Vec3d(u.x(),u.y(),u.z());
        return new Vec3d[]{fw.crossProduct(up).normalize(),up.normalize()};
    }
    /** V1: four-point star flare locked to the screen axes at the hero, sized in screen fractions. */
    static void flare(Fx c,EffectMesh m,float t,boolean additive){
        if(t<FLARE||t>=2.40f)return;
        var r=root(t);Vec3d o=c.at0(r.fw,r.up+1.05,r.rt);double d=Math.max(1,o.distanceTo(c.camera));
        double hs=2*d*Math.tan(Math.toRadians(35));
        float grow=Ease.out(t,FLARE,1.83f),shrink=1-Ease.smooth(t,2.23f,2.40f);
        double flick=1+.08*Math.sin(t*61)*Math.sin(t*23);
        double vert=.5*hs*grow*shrink*flick,horiz=.44*hs*grow*(.6+.4*shrink)*flick,core=.03*hs*grow*shrink;
        Vec3d[] ax=screen(c);Vec3d right=ax[0],up=ax[1];
        float a=Math.min(1,grow*1.3f)*(.35f+.65f*shrink);
        if(!additive){
            // Pink-violet rays read over any sky: drawn alpha-blended, white only near the core.
            m.softDisc(o,core*3.4,0xfcc8fd,a*.55f);m.softDisc(o,core*2.0,0xe278f5,a*.55f);
            for(int k=0;k<4;k++){
                Vec3d dir=k==0?up:k==1?up.negate():k==2?right:right.negate();double len=k<2?vert:horiz;
                m.needle(o,o.add(dir.multiply(len)),core*.6,.02,0x8c6af8,a*.9f);
                m.needle(o,o.add(dir.multiply(len*.55)),core*1.0,.02,0xb26ef5,a*.95f);
                m.needle(o,o.add(dir.multiply(len*.3)),core*1.5,.02,0xd96ef9,a);
            }
            return;
        }
        for(int k=0;k<4;k++){Vec3d dir=k==0?up:k==1?up.negate():k==2?right:right.negate();double len=k<2?vert:horiz;m.needle(o,o.add(dir.multiply(len*.14)),core*.35,.02,0xfcf6fd,a*.9f);}
        m.softDisc(o,core*1.15,0xffffff,a);m.softDisc(o,core*.7,0xffffff,a);
        // A ring of light racing out from the star as it ignites, and a slower second one.
        float r1=Ease.unit(t,FLARE,FLARE+.35f),r2=Ease.unit(t,FLARE+.12f,FLARE+.6f);
        if(r1<1)FxLib.ringPlane(m,o,right,up,core*(2+14*Ease.out(t,FLARE,FLARE+.35f)),core*1.2,0xf0a8f8,.8f*(1-r1));
        if(r2>0&&r2<1)FxLib.ringPlane(m,o,right,up,core*(2+20*Ease.out(t,FLARE+.12f,FLARE+.6f)),core*.9,0xc890fa,.5f*(1-r2));
    }
    /** V2: flat neon magenta tubes, one segment per dash; held, then fading as the street shot ends. */
    static void zig(Fx c,EffectMesh m,float t,boolean additive){
        if(t<ZIG[0]||t>=SHOT4)return;
        Vec3d[] p=zigzag(c);float fade=1-Ease.smooth(t,3.2f,SHOT4);
        for(int i=0;i<10;i++){
            if(t<ZIG[i])break;Vec3d a=p[i],b=p[i+1];
            // Solid magenta body with a pale hot streak (alpha-blended so the bloom keeps it magenta), faint violet halo.
            if(additive){
                m.softLine(a,b,3.2,0x9c97fa,.16f*fade);m.line(a,b,.35,0xffd8ff,.35f*fade);
                // A flash at the turn where each dash lands (its hit).
                float k=FxLib.kick(t,ZIG[i],.07f);if(k>.01f){m.softDisc(b,4.5*k+1,0xff9cf6,.7f*k);m.softDisc(b,1.8*k+.5,0xffffff,.9f*k);}
            }
            else{m.softLine(a,b,3.0,0xc19cfe,.45f*fade);m.line(a,b,1.6,0xe842f5,fade);m.line(a,b,.32,0xfebbfe,.9f*fade);}
        }
    }
    /** Intensity envelope of the slash-line burst (peak ≈3.88 s, pulsing, gone ≈4.42 s). */
    static float burstLevel(float t){
        if(t<BURST||t>=4.42f)return 0;
        if(t<3.88f)return Ease.out(t,BURST,3.88f);
        return (.55f+.15f*(float)Math.sin(t*38))*(1-Ease.smooth(t,4.2f,4.42f))+.45f*(1-Ease.smooth(t,3.88f,3.98f));
    }
    /** V4: slash streaks and four-point sparkles, mostly behind the hero, on his right and above. */
    static void burst(Fx c,EffectMesh m,float t,boolean additive){
        float level=burstLevel(t);if(level<=0)return;
        Vec3d[] ax=screen(c);Vec3d right=ax[0],up=ax[1];int seed=(int)c.cast.id().getMostSignificantBits();
        int last=(int)Math.floor((t-BURST)*600);
        for(int k=Math.max(0,last-60);k<=last;k++){
            float born=BURST+k/600f,life=.033f+.05f*(float)Ease.hash(seed+k*3),u=(t-born)/life;if(u<0||u>=1)continue;
            if(Ease.hash(seed+k*5)>burstLevel(born)*.95f)continue;
            Vec3d centre=c.at0(-.2-1.3*Ease.hash(seed+k*7),.8+2.7*Ease.hash(seed+k*11),-.5+3*Ease.hash(seed+k*13));
            double kind=Ease.hash(seed+k*17),ang=kind<.55?Math.toRadians(-35+(Ease.hash(seed+k*19)-.5)*30):kind<.9?Math.toRadians((Ease.hash(seed+k*19)-.5)*20):Math.toRadians(90);
            Vec3d dir=right.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang)));
            double len=(2.5+4.5*Ease.hash(seed+k*23)),w=.05+.15*Ease.hash(seed+k*29);
            Vec3d a=centre.subtract(dir.multiply(len*.5)),b=centre.add(dir.multiply(len*.5));
            float al=(float)Math.sin(Math.PI*Math.min(1,u*1.4));
            // Violet edges in the alpha pass keep the streaks coloured over a bright sky; the white core is additive.
            if(additive){m.needle(a,b,w*1.2,.5,0xd7b1f8,al*.8f);m.needle(a,b,w*.45,.5,0xffffff,al);}
            else{m.needle(a,b,w*2.8,.5,0x6a3cd6,al*.55f);m.needle(a,b,w*1.7,.5,0x9d6cf0,al*.6f);}
        }
        if(!additive)return;
        int stars=(int)Math.floor((t-BURST)*40);
        for(int k=Math.max(0,stars-4);k<=stars;k++){
            float born=BURST+k/40f,life=.05f+.05f*(float)Ease.hash(seed+k*31),u=(t-born)/life;if(u<0||u>=1)continue;
            Vec3d p=c.at0(-.3-1.5*Ease.hash(seed+k*37),.5+3*Ease.hash(seed+k*41),-1+4.5*Ease.hash(seed+k*43));
            double s=.14+.46*Ease.hash(seed+k*47);float al=level*(float)Math.sin(Math.PI*u);
            m.softDisc(p,s*.6,0x7561e6,al*.6f);
            for(int q=0;q<4;q++){Vec3d dir=q<2?up.multiply(q==0?1:-1):right.multiply(q==2?1:-1);m.needle(p,p.add(dir.multiply(s)),s*.18,.02,0xffffff,al);}
        }
        // A big translucent violet smear right of the hero for two frames.
        if(t>=3.965f&&t<4.0f)m.softDisc(c.at0(0,1.2,-1),.95,0x7462e0,.45f);
    }
    /** V6: horizontal white-violet beam behind the torso along the lateral axis, far to his right. */
    static void beam(Fx c,EffectMesh m,float t,boolean additive){
        if(t<BEAM||t>=END+.4f)return;
        Vec3d a=fin(c,-.2,1.25,-3*STUD),b=fin(c,-.2,1.25,45*STUD);
        float u=t-BEAM;
        // A broad white mass for a frame or two, then a thin glowing core inside a soft violet halo.
        double w=u<.05f?.55:u<.08f?.55-.38*Ease.unit(t,BEAM+.05f,BEAM+.08f):.17-.06*Ease.smooth(t,BEAM+.1f,END);
        float al=1-Ease.smooth(t,END,END+.4f);
        if(!additive){
            m.softLine(a,b,w*6,u<.2f?0xb6a8f1:0x8f6cf2,al*.45f);
            m.softLine(a.add(0,w*.6,0),b.add(0,w*.6,0),w*2.5,0x5946bb,al*.3f);
            return;
        }
        // The first frames' white mass widens out to his right.
        if(u<.08f)m.softTaper(a,b,.2,3.4,0xfdfdfa,.9f*(1-Ease.unit(t,BEAM+.05f,BEAM+.08f)));
        m.softLine(a,b,w*3.2,0xc8a0ff,al*.35f);m.softLine(a,b,w*2.2,0xf6e9fa,al*.8f);m.line(a,b,w*.6,0xfdfdfa,al);
        // A ring where it leaves him, and sparks racing out along it.
        if(u<.3f)FxLib.ringPlane(m,fin(c,-.2,1.25,.4),c.f,FxLib.UP,.3+2.6*Ease.out(t,BEAM,BEAM+.3f),.25,0xf0e0ff,.8f*(1-Ease.unit(t,BEAM,BEAM+.3f)));
        for(int k=0;k<24;k++){
            float born=BEAM+.012f*k,age=t-born;if(age<0||age>=.35f)continue;
            double x=(1+18*age*(1+Ease.hash(k*3+961)))*1.0,y=1.25+(Ease.hash(k*5+962)-.5)*.5+.6*age,z=(Ease.hash(k*7+963)-.5)*.4;
            Vec3d q=fin(c,-.2+z,y,x);float sa=al*(1-age/.35f);
            m.needle(q.subtract(c.right.multiply(.5)),q,.05,.3,k%3==0?0xffffff:0xd8c0ff,sa);
        }
        // Short white vertical plume at his right for three frames.
        if(t>=4.65f&&t<4.72f){Vec3d p=fin(c,-.15,1.25,.9);m.needle(p.add(0,-.15,0),p.add(0,2.6,0),.32,.3,0xffffff,.9f);}
    }
    /**
     * Fire-shader cells on fixed planes (they stay put as the cutscene camera moves): V8's pink-violet glowing puff
     * round the sword fist and the violet haze along the beam; on the effect layer the dust thrown up as the hero
     * lands and skids, and along the ground crescent as the beam fires.
     */
    @Override public void shaders(Fx c){
        float t=c.sec;List<Vec3d> pts=new ArrayList<>();List<float[]> cells=new ArrayList<>();
        if(t>=4.65f&&t<4.92f){
            var hb=KagenoClips.blade(c.origin,c.cast.yaw(),CLIP.pose(t));float a=1-Ease.smooth(t,4.7f,4.92f);
            for(int k=0;k<3;k++){pts.add(hb[0].add(0,.08*k,0));cells.add(new float[]{(float)(.42-.1*k),a*(.4f+.15f*k),.45f+.15f*k,.3f,k*17+201});}
        }
        if(t>=4.67f&&t<5.3f){
            float haze=Ease.smooth(t,4.65f,4.72f)*(1-Ease.smooth(t,4.9f,5.3f));
            for(int k=0;k<12;k++){pts.add(fin(c,-.25,1.2+.2*(Ease.hash(k*5+1)-.5)+.2*(t-4.67f),.8+5.5*k/12.0));cells.add(new float[]{(float)(.32+.14*Ease.hash(k*3)),.26f*haze,.4f,.4f,k*23+211});}
        }
        if(!pts.isEmpty())KagenoFire.cells(c.matrix,c.camera,pts,cells,c.age);
        if(!FxLayer.available()||t>=END+1.2f)return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        FxLib.dustRing(c.at0(0,0,0),t,.02f,.3,2.6,10,.9,dust,puffs,211);
        FxLib.dustRing(c.at0(LAND_FW,0,0),t,LAND,.3,3.0,12,.95,dust,puffs,221);
        for(int i=0;i<9;i++){
            float born=LAND+.055f*i,s=t-born,life=.5f+.2f*(float)Ease.hash(i*3+231);if(s<0||s>=life)continue;float u=s/life;
            double fw=LAND_FW+(BRAKE_FW-LAND_FW)*Math.min(1,(born-LAND)/(BRAKE-LAND));
            dust.add(c.at0(fw,.2+.3*u,(Ease.hash(i*5+232)-.5)*.8));puffs.add(new float[]{(float)(.2+.22*Ease.out(u,0,.5f)),.6f*Ease.smooth(s,0,.04f)*(1-Ease.smooth(u,.45f,1)),.5f*(1-u),u,i*31+233});
        }
        for(int i=0;i<16;i++){
            float s=t-BEAM-.015f*i,life=.7f+.3f*(float)Ease.hash(i*3+241);if(s<0||s>=life)continue;float u=s/life;
            dust.add(fin(c,.2+.3*u,.25+.5*u,1+i*.4));puffs.add(new float[]{(float)(.35+.35*Ease.out(u,0,.5f)),.75f*Ease.smooth(s,0,.04f)*(1-Ease.smooth(u,.5f,1)),.7f*(1-u),u,i*29+243});
        }
        if(!dust.isEmpty())KagenoFire.dust(c.matrix,c.camera,dust,puffs,c.age);
    }
    /** V9: small white / pale-blue petals drifting around the beam on his right. */
    static void sparkles(Fx c,EffectMesh m,float t){
        if(t<4.67f||t>=5.47f)return;
        // Violet haze and glittering dots along the beam on his right.
        float haze=Ease.smooth(t,4.65f,4.72f)*(1-Ease.smooth(t,4.9f,5.3f));
        for(int k=0;k<90;k++){
            double rt=.5+6*Ease.hash(k*7+1),up=.4+2*Ease.hash(k*9+2),drift=(t-4.67f)*.25;
            float a=haze*(float)Math.abs(Math.sin(t*9+k*1.7));
            m.softDisc(fin(c,-.25+(Ease.hash(k*13)-.5)*.4,up+drift,rt),.035+.035*Ease.hash(k*11),k%3==0?0xe2e5fb:0xb8a0ff,a);
        }
        for(int k=0;k<36;k++){
            double rt=.4+5.5*Ease.hash(k*3+5),up=.5+1.8*Ease.hash(k*5+6),drift=(t-4.67f)*(.2+.3*Ease.hash(k*7+7));
            Vec3d p=fin(c,-.4+.6*Ease.hash(k*11+8),up+drift*.5,rt+drift);
            float a=(float)Math.sin(Math.PI*Ease.unit(t,4.67f+.15f*(float)Ease.hash(k*13),5.47f));
            m.star(p,.04+.05*Ease.hash(k*17),0xe2e5fb,a);
        }
    }
    /** V5: brief translucent dark-blue ribbon along the downswing arc. */
    static void smear(Fx c,EffectMesh m,float t){
        if(t<4.255f||t>=4.30f)return;
        float a=.4f*(1-Ease.smooth(t,4.28f,4.30f));Vec3d s=fin(c,0,1.4,.35);
        for(int i=0;i<12;i++){
            double a0=Math.toRadians(85-122*i/12.0),a1=Math.toRadians(85-122*(i+1)/12.0);
            Vec3d d0=fin(c,.15,Math.sin(a0),Math.cos(a0)).subtract(c.origin),d1=fin(c,.15,Math.sin(a1),Math.cos(a1)).subtract(c.origin);
            m.quad(s.add(d0.multiply(.6)),s.add(d1.multiply(.6)),s.add(d1.multiply(1.75)),s.add(d0.multiply(1.75)),0x3c5aa0,a);
        }
    }
    /**
     * V7: dark scythe crescent on his right: the straight upper edge runs from far out along the beam
     * down to the sword tip at his feet, the lower edge sags to the ground. It stays after the cut as
     * the violet line on the ground and fades out linearly over ≈1.4 s.
     */
    static void crescent(Fx c,EffectMesh m,float t){
        if(t<BEAM||t>=END+1.4f)return;
        float a=t<END?.85f:.85f*(1-Ease.unit(t,END,END+1.4f));
        Vec3d near=fin(c,.3,.05,1.0),far=fin(c,-.4,1.25,14*STUD+1.0);
        int n=24;
        for(int i=0;i<n;i++){
            double s0=i/(double)n,s1=(i+1)/(double)n;
            Vec3d u0=near.lerp(far,s0),u1=near.lerp(far,s1);
            // Scythe profile: thickest a third of the way out, the lower edge a deep concave curve.
            double th0=2.6*STUD*Math.pow(Math.sin(Math.PI*s0),.75)*(1-.45*s0),th1=2.6*STUD*Math.pow(Math.sin(Math.PI*s1),.75)*(1-.45*s1);
            // Sagging toward the ground and slightly outward (forward of the beam line).
            Vec3d l0=u0.add(fin(c,.25*th0,-th0*1.6,0).subtract(c.origin)),l1=u1.add(fin(c,.25*th1,-th1*1.6,0).subtract(c.origin));
            l0=new Vec3d(l0.x,Math.max(c.origin.y+.02,l0.y),l0.z);l1=new Vec3d(l1.x,Math.max(c.origin.y+.02,l1.y),l1.z);
            Vec3d m0=u0.lerp(l0,.45),m1=u1.lerp(l1,.45);
            // Near-black navy blade, violet-blue only along the inner edge, then a crisp black-indigo outer rim.
            m.vertex(u0,0x262c94,a);m.vertex(u1,0x262c94,a);m.vertex(m1,0x0d1150,a);m.vertex(m0,0x0d1150,a);
            m.vertex(m0,0x0d1150,a);m.vertex(m1,0x0d1150,a);m.vertex(l1,0x04061e,a);m.vertex(l0,0x04061e,a);
            Vec3d o0=l0.add(0,-.03,0),o1=l1.add(0,-.03,0);
            m.vertex(l0,0x04011c,a);m.vertex(l1,0x04011c,a);m.vertex(o1,0x000001,a);m.vertex(o0,0x000001,a);
        }
        // Lavender inner edge where it meets the beam.
        m.line(near,far,.05,0x818afd,a*.8f);
        // Violet glow rising off the blade for half a second after the beam.
        float glow=Ease.smooth(t,BEAM+.05f,BEAM+.12f)*(1-Ease.smooth(t,4.95f,5.45f));
        if(glow>0)for(int k=0;k<12;k++){
            double s=(k+.5)/12;Vec3d p=near.lerp(far,s).add(0,.1+.3*Ease.hash(k*7+3),0);
            m.softDisc(p,.55+.45*Math.sin(Math.PI*s),s<.3?0xb848e0:k%2==0?0x7a3ce0:0x9a4cf0,.5f*glow);
        }
    }
    // ───────────────────────────── effect layer ─────────────────────────────

    @Override public boolean layered(){return true;}
    /**
     * Lights: the star flare at the hero in the sky, every magenta zigzag dash lighting the street below as it is
     * drawn, the landing's flash, the slash-line burst, then the beam: a blinding line of light along the street
     * with the impact frame, a colour split and a light shaft. Decals: skid marks burned along the slide, and the
     * dark crescent the beam cuts along the ground, glowing violet as it fires and cooling.
     */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;
        if(t<.45f)f.light(c.at0(0,.6,0),0xf0c8ff,9,2.6f*FxLib.kick(t,.01f,.12f));
        if(t>=FLARE&&t<2.40f){var r=root(t);float grow=Ease.out(t,FLARE,1.83f),shrink=1-Ease.smooth(t,2.23f,2.40f);f.light(c.at0(r.fw,r.up+1.05,r.rt),0xe278f5,14,2.2f*grow*shrink);}
        if(t>=ZIG[0]&&t<SHOT4){
            Vec3d[] p=zigzag(c);float fade=1-Ease.smooth(t,3.2f,SHOT4);
            for(int i=0;i<10;i++){if(t<ZIG[i])break;f.light(p[i].add(p[i+1]).multiply(.5),0xe842f5,13,(.9f+2.4f*FxLib.kick(t,ZIG[i],.08f))*fade);}
        }
        if(t>=LAND-.01f&&t<LAND+.5f)f.light(c.at0(LAND_FW,.6,0),0xc8b8ff,9,2.8f*FxLib.kick(t,LAND,.12f));
        float burst=burstLevel(t);
        if(burst>0)f.light(c.at0(-.4,1.6,1),0xd7b1f8,8,1.6f*burst);
        if(t>=BURST&&t<DOWN+.05f){var hb=KagenoClips.blade(c.origin,c.cast.yaw(),CLIP.pose(t));f.light(hb[0].add(hb[1].multiply(1.1)),0xe0c8ff,5,1.4f*Ease.smooth(t,BURST,DOWN)*(1-Ease.smooth(t,DOWN,DOWN+.05f)));}
        if(t>=BEAM-.01f&&t<END+.4f){
            float k=FxLib.kick(t,BEAM,.2f),linger=Ease.smooth(t,BEAM,BEAM+.05f)*(1-Ease.smooth(t,END,END+.4f));
            for(int i=0;i<4;i++)f.light(fin(c,-.2,1.25,-3*STUD+i*4.2),0xf0e4ff,8,3.4f*k+.7f*linger);
            Vec3d hero=fin(c,0,1.2,0);float near=FxLib.near(c,hero);
            if(near>0){f.impact(near*FxLib.impact(t,BEAM),FxLib.impactMode(t,BEAM));f.chroma(near*.01f*FxLib.kick(t,BEAM,.12f));f.shaft(hero,0xe8dcff,near*(.55f*k+.1f*linger));}
        }
        if(t>=LAND){
            float a=Ease.smooth(t,LAND,LAND+.05f)*(1-Ease.smooth(t,END+.6f,END+1.2f));double grow=Ease.out(t,LAND,BRAKE);
            double mid=LAND_FW-(LAND_FW-BRAKE_FW)*.5*grow,half=(LAND_FW-BRAKE_FW)*.5*grow+.2;
            for(int s=-1;s<=1;s+=2)f.decal(c.at0(mid,0,s*.18),c.f,half,.16,.25f*(1-Ease.smooth(t,LAND,BRAKE+.3f)),.8f*a,41.3f+s);
        }
        if(t>=BEAM){
            float heat=1-Ease.smooth(t,BEAM+.1f,END+.8f),a=Ease.smooth(t,BEAM,BEAM+.04f)*(1-Ease.smooth(t,END+.8f,END+1.4f));
            f.decal(fin(c,.15,0,2.7),c.right,2.6*Ease.out(t,BEAM,BEAM+.08f)+.2,.7,heat,a,47.9f);
        }
    }
    /** The air bends along each zigzag dash as it is drawn, in the landing's shock and along the beam. */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;
        if(t<.4f)d.ring(c.at0(0,.05,0),c.right,c.f,.3+4.5*Ease.out(t,0,.4f),1.2,.8f*Ease.smooth(t,0,.02f)*(1-Ease.unit(t,0,.4f)));
        if(t>=ZIG[0]&&t<SHOT4){Vec3d[] p=zigzag(c);for(int i=0;i<10;i++){if(t<ZIG[i])break;float k=1-Ease.smooth(t-ZIG[i],.05f,.25f);if(k>0)d.band(p[i],p[i+1],FxLib.UP,2.2,.6f*k);}}
        if(t>=LAND-.01f&&t<LAND+.4f){float in=Ease.smooth(t,LAND-.01f,LAND+.015f);d.ring(c.at0(LAND_FW,.05,0),c.right,c.f,.4+4.5*Ease.out(t,LAND,LAND+.4f),1.2,.8f*in*(1-Ease.unit(t,LAND,LAND+.4f)));}
        if(t>=BEAM-.01f&&t<END+.2f){
            float in=Ease.smooth(t,BEAM-.01f,BEAM+.015f),k=1-Ease.smooth(t,BEAM+.05f,END+.2f);
            d.band(fin(c,-.2,1.25,-3*STUD),fin(c,-.2,1.25,45*STUD),FxLib.UP,1.6*(1-.5*Ease.smooth(t,BEAM,BEAM+.2f)),1f*in*k);
            if(t<BEAM+.35f)d.ring(fin(c,-.2,1.25,0),c.f,FxLib.UP,.5+4*Ease.out(t,BEAM,BEAM+.35f),1,.7f*in*(1-Ease.unit(t,BEAM,BEAM+.35f)));
        }
    }
    /** Chunks of the street thrown up where the hero kicks off, where he lands and along the ground crescent as the beam fires. */
    @Override public void solids(Fx c,FxSolids s){
        float t=c.sec;
        if(t<1.2f)FxLib.chunks(s,c.at0(0,0,0),t,10,1651,2.4,7,.17,.8f);
        if(t>=LAND&&t<LAND+1.2f)FxLib.chunks(s,c.at0(LAND_FW,0,0),t-LAND,16,1701,3,6,.17,.8f);
        if(t>=BEAM&&t<BEAM+1.4f)for(int k=0;k<4;k++)FxLib.chunks(s,fin(c,.15,0,1.2+1.5*k),t-BEAM-.02f*k,6,1801+k*17,1.4,6,.16,.8f);
    }
}
