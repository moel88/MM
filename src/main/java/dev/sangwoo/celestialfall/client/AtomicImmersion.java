package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.atomic.AtomicLook;
import dev.sangwoo.celestialfall.client.atomic.AtomicPillar;
import dev.sangwoo.celestialfall.client.atomic.AtomicPillarShape;
import dev.sangwoo.celestialfall.client.atomic.AtomicStage;
import net.minecraft.util.math.Vec3d;

/**
 * G's screen treatment (chant haze, impact frames, blast colour grade) and its camera quake for
 * the local viewer. Additive to the player camera; never owns it or changes input.
 */
public final class AtomicImmersion {
    public record Sample(AtomicLook.Grade grade,float yaw,float pitch,float roll) {
        public boolean active() { return grade.tint()>0||grade.frame()>0||grade.mix()>0||yaw!=0||pitch!=0||roll!=0; }
        /** The same sample without the blast colour grade (haze, impact frames and quake stay). */
        public Sample withoutGrade() {
            return grade.mix()==0?this:new Sample(new AtomicLook.Grade(grade.tint(),grade.frame(),grade.morph(),0,grade.lo(),grade.hi()),yaw,pitch,roll);
        }
        /** Adds the jolt of the air shock reaching an outside viewer (amplitude in degrees). */
        public Sample withJolt(float amplitude,long step) {
            if(amplitude<=0)return this;
            return new Sample(grade,yaw+amplitude*AtomicLook.jitter(step,0),pitch+amplitude*.8f*AtomicLook.jitter(step,1),roll+amplitude*.5f*AtomicLook.jitter(step,2));
        }
        /**
         * Adds the shake right next to the pillar's wall (amplitude in degrees): a hard jitter on every frame step over a
         * rolling heave, on all three axes.
         */
        public Sample withWall(float amplitude,float sec) {
            if(amplitude<=0)return this;
            long step=(long)Math.floor(sec*45);float heave=sec*7f;
            return new Sample(grade,yaw+amplitude*(.6f*AtomicLook.jitter(step,5)+.5f*rumble(heave,6)),
                pitch+amplitude*(.75f*AtomicLook.jitter(step,6)+.55f*rumble(heave,7)),roll+amplitude*(.45f*AtomicLook.jitter(step,7)+.35f*rumble(heave*.8f,8)));
        }
        /** Adds the earthquake felt near the pillar from outside (amplitude in degrees): a rolling sway under a fast tremble, mostly vertical. */
        public Sample withQuake(float amplitude,float sec) {
            if(amplitude<=0)return this;
            float sway=sec*5.5f,tremble=sec*23f;
            return new Sample(grade,yaw+amplitude*(.5f*rumble(sway,0)+.3f*rumble(tremble,3)),
                pitch+amplitude*(.65f*rumble(sway,1)+.45f*rumble(tremble,4)),roll+amplitude*.35f*rumble(sway*.7f,2));
        }
    }
    public static final Sample NONE=new Sample(AtomicLook.Grade.NONE,0,0,0);
    private AtomicImmersion(){}
    /** Smooth zero-mean noise over time (one random value per unit of t, eased between them), roughly unit size. */
    static float rumble(float t,int axis) {
        long i=(long)Math.floor(t);float f=t-i;f=f*f*(3-2*f);
        float a=AtomicLook.jitter(i,axis+10),b=AtomicLook.jitter(i+1,axis+10);
        return a+(b-a)*f;
    }
    /**
     * The part of the outside quake not already carried by the blast shake close to the pillar, so the two
     * together shake about as hard as the stronger one (degrees).
     */
    static float quakeBeyondShake(float sec,double distance,double radius,float shake) {
        float quake=AtomicStage.quake(sec,distance,radius)*shake,existing=AtomicLook.shake(sec)*reach(distance,radius)*shake;
        return (float)Math.sqrt(Math.max(0,quake*quake-existing*existing));
    }
    /**
     * Distance from a cast's axis that the wall shake goes by: the local player's when the camera is their own view
     * (first person or the third-person camera behind them), else the camera's.
     */
    public static double wallDistance(Vec3d camera,Vec3d origin,float delta){
        var p=net.minecraft.client.MinecraftClient.getInstance().player;
        double own=AtomicPillarShape.OWN_CAMERA;
        Vec3d at=p!=null&&p.getEyePos().squaredDistanceTo(camera)<own*own?p.getLerpedPos(delta):camera;
        return Math.hypot(at.x-origin.x,at.z-origin.z);
    }
    /** Full strength within 1.6 blast radii of the cast, gone at 4 radii. */
    public static float reach(double distance,double radius) {
        return (float)Math.max(0,Math.min(1,(radius*4-distance)/(radius*2.4)));
    }
    public static Sample sample(float sec,double distance,double radius,double shakeStrength) {
        float reach=reach(distance,radius);if(reach<=0||!(sec>=0))return NONE;
        var g=AtomicLook.grade(sec,(long)Math.floor(sec*50));
        // Impact frames are all-or-nothing; the haze and grade thin out with distance.
        var grade=new AtomicLook.Grade(g.tint()*reach,reach>=.5f?g.frame():0,g.morph(),g.mix()*reach,g.lo(),g.hi());
        float amp=AtomicLook.shake(sec)*reach*(float)Math.max(0,Math.min(2,shakeStrength));
        if(amp<=0)return new Sample(grade,0,0,0);
        long step=(long)Math.floor(sec*45);
        return new Sample(grade,amp*AtomicLook.jitter(step,0),amp*AtomicLook.jitter(step,1),amp*.3f*AtomicLook.jitter(step,2));
    }
    public static Sample at(Vec3d camera,float delta){
        Sample result=NONE;double best=Double.MAX_VALUE;
        var mc=net.minecraft.client.MinecraftClient.getInstance();
        if(mc.world==null)return NONE;
        for(var cast:AtomicClient.CASTS.values()){
            if(!cast.dimension.equals(mc.world.getRegistryKey().getValue()))continue;
            double d=Math.hypot(camera.x-cast.origin.x,camera.z-cast.origin.z);
            if(d>=best)continue;
            float sec=cast.age(delta)/20f;
            var value=sample(sec,d,cast.radius,CelestialFall.CONFIG.screenShakeStrength);
            if(AtomicPillar.available()&&!AtomicPillar.interior(cast,camera,sec)){
                // The violet grade belongs to the hit view; outside the damage column the pillar shows its own colours,
                // the ground near the pillar quakes with every surge, the air shock jolts the camera when it arrives,
                // even beyond the quake's reach, and right next to the wall the screen shakes hard.
                float shake=(float)Math.max(0,Math.min(2,CelestialFall.CONFIG.screenShakeStrength));
                float jolt=AtomicStage.jolt(sec,camera.distanceTo(cast.origin),cast.radius)*shake;
                // Within 8 blocks of the pillar's wall (measured from the player in their own view) the screen shakes violently.
                double r=AtomicPillarShape.radius(AtomicStage.visual(sec),cast.radius);
                float wall=AtomicStage.wallShake(sec,wallDistance(camera,cast.origin,delta),r,cast.radius)*shake;
                value=(value==NONE?new Sample(AtomicLook.Grade.NONE,0,0,0):value.withoutGrade()).withJolt(jolt,(long)Math.floor(sec*45))
                    .withQuake(quakeBeyondShake(sec,d,cast.radius,shake),sec).withWall(wall,sec);
                if(!value.active())value=NONE;
            }
            if(value!=NONE){result=value;best=d;}
        }
        return result;
    }
}
