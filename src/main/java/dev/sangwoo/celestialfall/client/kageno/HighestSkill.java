package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * H "최강": a 4.97 s scripted cutscene shown to the caster and everyone caught within 5 blocks.
 * Shot 1 worm's-eye launch (the hero rockets up and back, somersaults head-down), shot 2 inverted
 * close-up from behind, shot 3 one take: straight up at the hero (the sky rolls 175°, a four-point
 * star flare, ten magenta zigzag dashes = ten hits), a whip-tilt down to the street, the hero slides
 * in and skids into a three-point crouch (jolt, dolly-zoom, Dutch tilt), shot 4 a two-frame face
 * close-up, shot 5 the hand insert and pull-back to the raised-sword stance, a burst of slash lines
 * (hit 11), the downswing and a horizontal beam with a dark crescent along the ground (hit 12).
 * Hard cut back to gameplay at 4.972 s; the beam line and the ground crescent linger.
 * Seconds after the cast; cast frame = (forward, up, right) of the caster; 1 stud = 0.36 blocks.
 */
final class HighestSkill implements Fx.Skill {
    static final double STUD=.36;
    static final float SHOT2=.635f,SHOT3=1.486f,FLARE=1.619f,WHIP=2.570f,STREET=2.653f,LAND=2.737f,SHOT4=3.487f,SHOT5=3.521f,
        BURST=3.838f,DOWN=4.239f,BEAM=4.606f,END=4.972f;
    /** The ten zigzag dashes (one hit each), then the burst and the beam. */
    static final float[] ZIG={2.167f,2.268f,2.334f,2.418f,2.534f,2.668f,2.701f,2.818f,2.885f,2.985f};
    /** Apex of the leap, in the cast frame (up and back, away from the shot-1 camera). */
    static final double APEX_FW=-14,APEX_UP=28;
    /** Street landing spot (shot 3c): the hero slides in ≈4 studs in front of the street camera. */
    static final double STREET_CAM=-14.0,LAND_FW=STREET_CAM-6.5*STUD;

    // ───────────────────────────── poses ─────────────────────────────
    static final R6.Pose K1=pose().rArm(-70,90).lArm(-70,-90).blade(-10,90);
    static final R6.Pose K2=pose().rArm(-8,90).lArm(-8,-90).blade(0,90);
    static final R6.Pose K3=pose().body(0,-180,0).rArm(-72,90).lArm(-72,-90).blade(-85,0);
    static final R6.Pose K4=pose().body(0,-180,0).rArm(-85,12).lArm(-85,-12).blade(-82,0).lLeg(0,-90);
    static final R6.Pose K5=pose().body(0,55,0).head(0,-40,0).rArm(-20,150).blade(10,160).lArm(55,-20).lLeg(10,0).rLeg(-85,180);
    static final R6.Pose K6=pose().body(0,40,0).head(0,-35,0).lArm(15,-10).rArm(-15,110).blade(0,120).rLeg(-50,140).lLeg(-5,-15);
    static final R6.Pose K7=pose().head(0,-5,0).lArm(25,-10).rArm(-20,60).blade(-30,70);
    /** Shot-5 camera azimuth: he stands turned 35° away from it, then squares up to it at the downswing. */
    static final float SQUARE=35;
    static final R6.Pose K8=pose().head(SQUARE,0,0).rArm(78,-30).blade(78,-150).lArm(-78,-5);
    static final R6.Pose K10=pose().at(0,-.05,0).body(-SQUARE,0,0).rArm(-42,75).lArm(-42,-75).blade(-37,75).rLeg(-72,90).lLeg(-72,-90);
    /** Leap curve: fast start, strong ease-out, apex at ≈0.5 s. */
    static double leap(float t){double u=Math.min(1,t/.5);return 1-Math.pow(1-u,2.4);}
    static final Anim CLIP;
    static {
        List<Key> keys=new ArrayList<>();
        keys.add(k(0,KagenoClips.IDLE));
        keys.add(k(.017,K1,Anim.Ease.SNAP));
        // Launch: T-pose snapped in 2-3 frames, root on the leap curve; the somersault 0.28-0.53 s.
        for(float t=.05f;t<=SHOT2+.001f;t+=.05f){
            double s=leap(t);R6.Pose p;
            if(t<.28f)p=K2.blade(t<.2f?0:38,90);
            else if(t<.53f){float u=(t-.28f)/.25f;p=R6.Pose.lerp(K2.blade(38,90),K3,u*u*(3-2*u));}
            else p=K3;
            keys.add(k(t,p.at(APEX_FW*s,APEX_UP*s,0),t<.06f?Anim.Ease.SNAP:Anim.Ease.LINEAR));
        }
        // Inverted hover: one leg out sideways, swinging up to straight; slow drift.
        keys.add(k(.72,K4.at(APEX_FW-.05,APEX_UP+.05,0)));
        keys.add(k(.97,K4.lLeg(-88,-5).at(APEX_FW-.15,APEX_UP+.15,0)));
        keys.add(k(SHOT3,K4.lLeg(-88,-5).at(APEX_FW-.25,APEX_UP+.25,0)));
        keys.add(k(LAND-.01,K4.lLeg(-88,-5).at(APEX_FW-.25,APEX_UP+.25,0),Anim.Ease.HOLD));
        // Street: lunge-slide, skid back (≈1.5 studs, drifting to camera-left), forward (≈3 studs), crouch.
        keys.add(k(LAND,K5.at(LAND_FW,-.75,0),Anim.Ease.SNAP));
        keys.add(k(2.87,K5.at(LAND_FW-1.5*STUD,-.72,1*STUD)));
        keys.add(k(2.94,K6.at(LAND_FW-.8*STUD,-.55,.8*STUD)));
        keys.add(k(3.07,K6.at(LAND_FW+1.5*STUD,-.55,.6*STUD),Anim.Ease.OUT));
        keys.add(k(SHOT4-.01,K6.at(LAND_FW+1.5*STUD,-.55,.6*STUD),Anim.Ease.HOLD));
        // Hard cut: back at the cast origin, rising from the crouch with the left arm up near the face.
        keys.add(k(SHOT4,K7.at(0,-.25,0).body(0,15,0).rArm(-40,80),Anim.Ease.SNAP));
        keys.add(k(3.54,K7.rArm(-10,40)));
        keys.add(k(3.58,K7.rArm(40,-10).blade(50,-20),Anim.Ease.LINEAR));
        keys.add(k(3.66,K8,Anim.Ease.OUT));
        keys.add(k(DOWN,K8,Anim.Ease.HOLD));
        keys.add(k(DOWN+.025f,pose().body(-SQUARE*.5f,0,0).rArm(20,80).blade(10,85).rLeg(-80,90).lLeg(-80,-90),Anim.Ease.LINEAR));
        keys.add(k(DOWN+.05f,K10,Anim.Ease.LINEAR));
        keys.add(k(END,K10,Anim.Ease.HOLD));
        keys.add(k(END+.45f,KagenoClips.IDLE));
        CLIP=Anim.of(keys.toArray(Key[]::new));
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
            return look(6.5*STUD,.15,0,r.fw,r.up+1.0,r.rt,0,(float)fov);
        }
        if(t<SHOT3){
            // Behind the inverted hero at his altitude: settle (tilt up, push in, roll) then a slow roll drift.
            var r=root(t);float settle=Ease.out(t,SHOT2,.9f);
            double back=1.45-.18*settle;
            return look(r.fw+back,r.up+1.05+.15-.25*settle,r.rt-.25,r.fw,r.up+1.05+.05,r.rt+.15,3*settle+2.5f*Ease.smooth(t,.9f,SHOT3),70);
        }
        double gx=APEX_FW-.25,gz=0;
        if(t<WHIP){
            // Straight up at the hero; the image rotates 175° CCW (yaw of an up-looking camera), quad-out.
            float u=Math.min(1,(t-SHOT3)/1.12f);double th=Math.toRadians(5+175*(1-(1-u)*(1-u)));
            return look(gx,.6,gz,gx+Math.cos(th)*.02,30,gz+Math.sin(th)*.02,-37,70);
        }
        if(t<STREET){
            // Whip-tilt down in 6 frames with the roll correction, ending level along the street (−forward).
            float u=Ease.smooth(t,WHIP,STREET);double a=Math.toRadians(90*u);
            double lx=gx-Math.sin(a)*10-.02,ly=.6+Math.cos(a)*10;
            return look(gx,.6+.3*u,gz,lx,ly,gz,-37*(1-u),70);
        }
        if(t<SHOT4){
            // Street camera ≈0.5 H high: pan right, pan left after the hero, push-in, 3-frame jolt
            // (zoom punch, −9° roll), then a dolly-zoom (70 → 45°) backing off to hold him, Dutch drift.
            float pan=(float)(14*Ease.smooth(t,STREET,2.72f)-29*Ease.out(t,2.75f,2.87f));
            double camFw=STREET_CAM+.12*Ease.smooth(t,2.75f,2.87f)-.25*Ease.smooth(t,2.87f,2.99f);
            float fov=70;double roll=0;
            fov-=10*Ease.out(t,3.004f,3.054f);roll-=9*Ease.out(t,3.004f,3.054f);
            float dz=Ease.smooth(t,3.07f,SHOT4-.02f);fov-=15*dz;camFw+=.4*dz;roll-=8*Ease.smooth(t,3.27f,SHOT4);
            double ph=Math.toRadians(pan);
            double lx=camFw-Math.cos(ph)*6,lz=-Math.sin(ph)*6;
            return look(camFw,.9,0,lx,.55,lz,(float)roll,fov);
        }
        if(t<SHOT5){
            // Face close-up ≈2.2 studs in front, slightly below eye level.
            return look(.85,1.3,0,0,1.55,0,0,70);
        }
        // Shot 5: front-right (azimuth ≈35°) at chest height; starts ≈2.4 studs away (the hand and tsuba
        // sweep past the lens), dollies back to ≈6.5 studs, then locked with a tiny roll shake at the downswing.
        double az=Math.toRadians(35),d=2.4*STUD+(6.5-2.4)*STUD*Ease.out(t,3.605f,3.82f);
        float roll=0;
        if(t>=4.289f&&t<4.34f)roll=(float)(1.3*Math.sin(Math.PI*Ease.unit(t,4.289f,4.34f)));
        else if(t>=4.372f&&t<4.42f)roll=(float)(-1.0*Math.sin(Math.PI*Ease.unit(t,4.372f,4.42f)));
        return look(Math.cos(az)*d,1.08,Math.sin(az)*d,0,1.08,0,roll,70);
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
        flare(c,m,t,true);
        zig(c,m,t,true);
        burst(c,m,t);
        beam(c,m,t);
        handSmoke(c,m,t);
        sparkles(c,m,t);
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;
        flare(c,m,t,false);
        zig(c,m,t,false);
        smear(c,m,t);
        crescent(c,m,t);
    }
    /** Point in the finisher frame: the cast frame turned to face the shot-5 camera (forward, up, right). */
    static Vec3d fin(Fx c,double fw,double up,double rt){
        double a=Math.toRadians(SQUARE);Vec3d f=c.f.multiply(Math.cos(a)).add(c.right.multiply(Math.sin(a))),r=c.right.multiply(Math.cos(a)).subtract(c.f.multiply(Math.sin(a)));
        return c.origin.add(f.multiply(fw)).add(0,up,0).add(r.multiply(rt));
    }
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
    }
    /** V2: flat neon magenta tubes, one segment per dash; held, then fading as the street shot ends. */
    static void zig(Fx c,EffectMesh m,float t,boolean additive){
        if(t<ZIG[0]||t>=SHOT4)return;
        Vec3d[] p=zigzag(c);float fade=1-Ease.smooth(t,3.2f,SHOT4);
        for(int i=0;i<10;i++){
            if(t<ZIG[i])break;Vec3d a=p[i],b=p[i+1];
            if(additive){m.line(a,b,2.6,0x9c97fa,.1f*fade);m.line(a,b,1.6,0xc19cfe,.18f*fade);m.line(a,b,.22,0xfebbfe,.8f*fade);}
            else{m.line(a,b,1.2,0xe6a7fc,.55f*fade);m.line(a,b,.8,0xf77ffa,fade);}
        }
    }
    /** Intensity envelope of the slash-line burst (peak ≈3.88 s, pulsing, gone ≈4.42 s). */
    static float burstLevel(float t){
        if(t<BURST||t>=4.42f)return 0;
        if(t<3.88f)return Ease.out(t,BURST,3.88f);
        return (.55f+.15f*(float)Math.sin(t*38))*(1-Ease.smooth(t,4.2f,4.42f))+.45f*(1-Ease.smooth(t,3.88f,3.98f));
    }
    /** V4: slash streaks and four-point sparkles, mostly behind the hero, on his right and above. */
    static void burst(Fx c,EffectMesh m,float t){
        float level=burstLevel(t);if(level<=0)return;
        Vec3d[] ax=screen(c);Vec3d right=ax[0],up=ax[1];int seed=(int)c.cast.id().getMostSignificantBits();
        int last=(int)Math.floor((t-BURST)*480);
        for(int k=Math.max(0,last-44);k<=last;k++){
            float born=BURST+k/480f,life=.033f+.05f*(float)Ease.hash(seed+k*3),u=(t-born)/life;if(u<0||u>=1)continue;
            if(Ease.hash(seed+k*5)>burstLevel(born)*.95f)continue;
            Vec3d centre=c.at0(-.2-1.3*Ease.hash(seed+k*7),.8+2.7*Ease.hash(seed+k*11),-.5+3*Ease.hash(seed+k*13));
            double kind=Ease.hash(seed+k*17),ang=kind<.55?Math.toRadians(-35+(Ease.hash(seed+k*19)-.5)*30):kind<.9?Math.toRadians((Ease.hash(seed+k*19)-.5)*20):Math.toRadians(90);
            Vec3d dir=right.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang)));
            double len=(2+4*Ease.hash(seed+k*23)),w=.04+.13*Ease.hash(seed+k*29);
            Vec3d a=centre.subtract(dir.multiply(len*.5)),b=centre.add(dir.multiply(len*.5));
            float al=(float)Math.sin(Math.PI*Math.min(1,u*1.4));
            m.needle(a,b,w*2.2,.5,0x7462e0,al*.5f);m.needle(a,b,w*1.3,.5,0xd7b1f8,al*.85f);m.needle(a,b,w*.5,.5,0xffffff,al);
        }
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
    static void beam(Fx c,EffectMesh m,float t){
        if(t<BEAM||t>=END+.4f)return;
        Vec3d a=fin(c,-.2,1.25,-3*STUD),b=fin(c,-.2,1.25,45*STUD);
        float u=t-BEAM;
        double w=u<.05f?.42:u<.08f?.42-.28*Ease.unit(t,BEAM+.05f,BEAM+.08f):.14-.06*Ease.smooth(t,BEAM+.1f,END);
        float al=1-Ease.smooth(t,END,END+.4f);
        Vec3d upOff=new Vec3d(0,w*.38,0);
        m.line(a.add(upOff),b.add(upOff),w*.9,0x5946bb,al*.45f);m.line(a.add(upOff.multiply(.6)),b.add(upOff.multiply(.6)),w*.7,0xb495f4,al*.7f);
        m.line(a.subtract(upOff),b.subtract(upOff),w*.9,0x7976a3,al*.35f);m.line(a.subtract(upOff.multiply(.6)),b.subtract(upOff.multiply(.6)),w*.7,0xf6e9fa,al*.7f);
        m.line(a,b,w*.55,0xfdfdfa,al);
        if(u>=.04f)m.line(a,b,w*1.7,u<.2f?0xb6a8f1:0x8f6cf2,al*.3f);
        // Short white vertical plume at his right for three frames.
        if(t>=4.65f&&t<4.70f){Vec3d p=fin(c,-.15,1.25,.9);m.needle(p.add(0,-.15,0),p.add(0,.9,0),.3,.4,0xffffff,.8f);}
    }
    /** V8: pink-violet glowing puff around the sword fist. */
    static void handSmoke(Fx c,EffectMesh m,float t){
        if(t<4.65f||t>=4.92f)return;
        var hb=KagenoClips.blade(c.origin,c.cast.yaw(),CLIP.pose(t));float a=1-Ease.smooth(t,4.7f,4.92f);
        m.softDisc(hb[0],.75,0x4428ea,a*.35f);m.softDisc(hb[0],.5,0xaa60fe,a*.5f);m.softDisc(hb[0],.3,0xe99df7,a*.8f);
    }
    /** V9: small white / pale-blue petals drifting around the beam on his right. */
    static void sparkles(Fx c,EffectMesh m,float t){
        if(t<4.67f||t>=5.47f)return;
        // Violet haze and glittering dots along the beam on his right.
        float haze=Ease.smooth(t,4.65f,4.72f)*(1-Ease.smooth(t,4.9f,5.3f));
        for(int k=0;k<8;k++)m.softDisc(fin(c,-.25,1.2+.2*(Ease.hash(k*5+1)-.5),.8+5.5*k/8.0),.45+.2*Ease.hash(k*3),0x6a4bd0,.3f*haze);
        for(int k=0;k<40;k++){
            double rt=.6+6*Ease.hash(k*7+1),up=1.25+(Ease.hash(k*9+2)-.5)*.9,drift=(t-4.67f)*.25;
            float a=haze*(float)Math.abs(Math.sin(t*9+k*1.7));
            m.softDisc(fin(c,-.25+(Ease.hash(k*13)-.5)*.4,up+drift,rt),.035+.035*Ease.hash(k*11),k%3==0?0xe2e5fb:0xb8a0ff,a);
        }
        for(int k=0;k<22;k++){
            double rt=.4+5*Ease.hash(k*3+5),up=.7+1.2*Ease.hash(k*5+6),drift=(t-4.67f)*(.2+.3*Ease.hash(k*7+7));
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
            double th0=2.4*STUD*4*s0*(1-s0)*(1-.3*s0),th1=2.4*STUD*4*s1*(1-s1)*(1-.3*s1);
            // Sagging toward the ground and slightly outward (forward of the beam line).
            Vec3d l0=u0.add(fin(c,.25*th0,-th0*1.6,0).subtract(c.origin)),l1=u1.add(fin(c,.25*th1,-th1*1.6,0).subtract(c.origin));
            l0=new Vec3d(l0.x,Math.max(c.origin.y+.02,l0.y),l0.z);l1=new Vec3d(l1.x,Math.max(c.origin.y+.02,l1.y),l1.z);
            Vec3d m0=u0.lerp(l0,.45),m1=u1.lerp(l1,.45);
            m.vertex(u0,0x818afd,a);m.vertex(u1,0x818afd,a);m.vertex(m1,0x2229b8,a);m.vertex(m0,0x2229b8,a);
            m.vertex(m0,0x2229b8,a);m.vertex(m1,0x2229b8,a);m.vertex(l1,0x0d1c6b,a);m.vertex(l0,0x0d1c6b,a);
            // Crisp black-indigo outer rim.
            Vec3d o0=l0.add(0,-.03,0),o1=l1.add(0,-.03,0);
            m.vertex(l0,0x04011c,a);m.vertex(l1,0x04011c,a);m.vertex(o1,0x000001,a);m.vertex(o0,0x000001,a);
        }
    }
}
