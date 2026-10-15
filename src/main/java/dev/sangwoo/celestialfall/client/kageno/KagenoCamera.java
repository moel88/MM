package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Cutscene shots (H) and the gameplay camera shake of the Kageno skills. */
public final class KagenoCamera {
    private KagenoCamera(){}
    /**
     * One camera sample, relative to the cast origin and facing: eye and look target as
     * (forward, up, right) in blocks, roll in degrees, vertical FOV in degrees.
     */
    public record Shot(double eyeFw,double eyeUp,double eyeRt,double lookFw,double lookUp,double lookRt,float roll,float fov){
        public static Shot lerp(Shot a,Shot b,double t){
            return new Shot(l(a.eyeFw,b.eyeFw,t),l(a.eyeUp,b.eyeUp,t),l(a.eyeRt,b.eyeRt,t),l(a.lookFw,b.lookFw,t),l(a.lookUp,b.lookUp,t),l(a.lookRt,b.lookRt,t),(float)l(a.roll,b.roll,t),(float)l(a.fov,b.fov,t));
        }
        private static double l(double a,double b,double t){return a+(b-a)*t;}
    }
    private static Shot shot(StandClient.Cast c,float delta){var s=KagenoSkills.of(c.skill());return s==null?null:s.shot(c.age(delta)/20f);}
    public static float blend(StandClient.Cast c,float delta){var s=KagenoSkills.of(c.skill());return s==null?0:s.shotBlend(c.age(delta)/20f);}
    public static double fov(StandClient.Cast c,float delta){var s=shot(c,delta);return s==null?70:s.fov();}
    public static CinematicDirector.Pose pose(StandClient.Cast c,float delta){
        var s=shot(c,delta);var mc=MinecraftClient.getInstance();
        if(s==null){var p=mc.gameRenderer.getCamera();return new CinematicDirector.Pose(p.getPos(),p.getYaw(),p.getPitch(),0);}
        Vec3d f=Vec3d.fromPolar(0,c.yaw()),right=new Vec3d(-f.z,0,f.x);
        Vec3d eye=c.origin().add(f.multiply(s.eyeFw())).add(0,s.eyeUp(),0).add(right.multiply(s.eyeRt()));
        Vec3d look=c.origin().add(f.multiply(s.lookFw())).add(0,s.lookUp(),0).add(right.multiply(s.lookRt()));
        Vec3d d=look.subtract(eye).normalize();
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,d.y))));
        return new CinematicDirector.Pose(eye,yaw,pitch,s.roll());
    }
    /** Hands the last cutscene orientation to the orbit camera when the cutscene cuts back. */
    public static void handoff(StandClient.Cast c){
        var s=KagenoSkills.of(c.skill());if(s==null)return;var shot=s.shot(4.95f);if(shot==null)return;
        Vec3d f=Vec3d.fromPolar(0,c.yaw()),right=new Vec3d(-f.z,0,f.x);
        Vec3d d=f.multiply(shot.lookFw()-shot.eyeFw()).add(right.multiply(shot.lookRt()-shot.eyeRt()));if(d.lengthSquared()<1e-6)return;
        dev.sangwoo.celestialfall.client.BotandCamera.STATE.yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));
        dev.sangwoo.celestialfall.client.BotandCamera.STATE.pitch=10;
    }
    /** Post-process pulse for the anime shader (0 = none). */
    public static float pulse(int skill,float age){var s=KagenoSkills.of(skill);return s==null?0:s.pulse(age/20f);}
    /** Gameplay-camera shake for the local viewer: {yaw, pitch, roll} degrees. */
    public static float[] shake(float delta){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null||!MapClient.enabled()||StandClient.cinematic()!=null)return null;
        float amount=0;float time=mc.world.getTime()+delta;float[] kick=null;
        for(var c:StandClient.CASTS.values()){
            var s=KagenoSkills.of(c.skill());if(s==null)continue;
            double dist=c.origin().distanceTo(mc.player.getPos());float reach=(float)Math.max(0,1-dist/40);
            var k=s.kick(c.age(delta)/20f);
            if(k!=null&&reach>0){if(kick==null)kick=new float[3];for(int i=0;i<3;i++)kick[i]+=k[i]*reach;}
            float v=s.shake(c.age(delta)/20f);if(v<=0)continue;
            amount=Math.max(amount,v*reach);
        }
        if(amount<=0&&kick==null)return null;float strength=(float)CelestialFall.CONFIG.screenShakeStrength;amount*=strength;
        float[] out={amount*(float)(Math.sin(time*2.9)*.7+Math.sin(time*5.3)*.3),amount*(float)(Math.cos(time*3.7)*.7+Math.sin(time*6.1)*.3),amount*.35f*(float)Math.sin(time*4.4)};
        if(kick!=null)for(int i=0;i<3;i++)out[i]+=kick[i]*strength;
        return out;
    }
}
