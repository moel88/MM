package dev.sangwoo.celestialfall.client.samurai;

import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The samurai's body through their G (2.143.0): every key stands on the ground, the clip flows (but across its one cut,
 * the dash), the blade does what the video's does at its moments, and the scabbard is where the sword needs it.
 */
class SamuraiKataTest {
    static final Vec3d FEET=new Vec3d(3.5,70,-8.5);
    static final float YAW=120;

    @Test void everyKeyStandsOnTheGround(){
        float[] times=SamuraiKata.CLIP.times();
        for(int i=0;i<times.length;i++){
            double[] feet=dev.sangwoo.celestialfall.client.isosang.IsosangSlash.feet(SamuraiKata.CLIP.key(i));
            assertEquals(0,Math.min(feet[0],feet[1]),1e-3,"key at "+times[i]+": its lower foot on the ground");
            assertTrue(Math.max(feet[0],feet[1])<.2,"key at "+times[i]+": the other foot near it: "+Math.max(feet[0],feet[1]));
        }
        // and between its keys no foot is far under it
        for(float s=0;s<SamuraiKata.LENGTH;s+=1/60f){
            double[] feet=dev.sangwoo.celestialfall.client.isosang.IsosangSlash.feet(SamuraiKata.CLIP.frame(s));
            assertEquals(0,Math.min(feet[0],feet[1]),1e-3,"at "+s+": its lower foot on the ground");
        }
        assertEquals(SamuraiKata.LENGTH,SamuraiKata.CLIP.length(),1e-6);
    }
    @Test void theClipFlowsButAcrossTheDash(){
        Vec3d[] last=null;
        for(float s=0;s<SamuraiKata.LENGTH;s+=1/60f){
            var hb=SamuraiKata.blade(FEET,YAW,s);
            Vec3d tip=hb[0].add(hb[1].multiply(SamuraiKata.TIP));
            // (the dash is a cut: the body is elsewhere, turned round)
            boolean cut=s<=SamuraiKata.DASH+1/60f&&s+1/60f>=SamuraiKata.DASH-1/60f;
            if(last!=null&&!cut){
                assertTrue(hb[0].distanceTo(last[0])<.5,"the hand in one frame at "+s+": "+hb[0].distanceTo(last[0]));
                assertTrue(tip.distanceTo(last[1])<1.75,"the point in one frame at "+s+": "+tip.distanceTo(last[1]));
            }
            last=new Vec3d[]{hb[0],tip};
        }
    }
    @Test void theBladeDoesWhatTheVideosDoes(){
        // before the dash: at the left hip, pointing back (the sheathed sword)
        assertTrue(SamuraiKata.blade(FEET,YAW,.3f)[1].dotProduct(Vec3d.fromPolar(0,YAW))<-.5,"pointing behind them");
        // the flourish: fast (its crescents show); the sword laid over the shoulder: still, its hand high, its point down at the other side
        double fastest=0;
        for(float s=1.3f;s<2.3f;s+=1/60f){
            var a=SamuraiKata.blade(FEET,YAW,s);var b=SamuraiKata.blade(FEET,YAW,s+1/60f);
            fastest=Math.max(fastest,a[0].add(a[1].multiply(SamuraiKata.TIP)).distanceTo(b[0].add(b[1].multiply(SamuraiKata.TIP)))*60);
        }
        assertTrue(fastest>25,"the flourish's fastest: "+fastest+" blocks a second");
        for(float s=2.45f;s<2.82f;s+=.05f){
            var a=SamuraiKata.blade(FEET,YAW,s);var b=SamuraiKata.blade(FEET,YAW,s+1/60f);
            assertTrue(a[0].add(a[1].multiply(SamuraiKata.TIP)).distanceTo(b[0].add(b[1].multiply(SamuraiKata.TIP)))*60<1,"held still at "+s);
            assertTrue(a[0].y>FEET.y+1.5,"the hand high at "+s+": "+(a[0].y-FEET.y));
            assertTrue(a[1].y<-.3,"the point down at "+s);
            // toward their left (the frame's right in the close shot: they face it)
            Vec3d f=Vec3d.fromPolar(0,YAW),left=new Vec3d(f.z,0,-f.x);
            assertTrue(a[1].dotProduct(left)>.5,"the point to their left at "+s);
        }
        // at the end: lowered again, upright
        var done=SamuraiKata.pose(5f);
        assertEquals(0,done.yaw,1e-3);assertEquals(0,done.pitch,1e-3);
    }
    @Test void theScabbardIsWhereTheSwordNeedsIt(){
        // sheathed: along the blade, its mouth at the guard
        for(float s:new float[]{0,.5f,.99f,3.0f,3.3f,6f}){
            var hb=SamuraiKata.blade(FEET,YAW,s);var sh=SamuraiKata.sheath(FEET,YAW,s);
            assertEquals(1,SamuraiKata.home(s),1e-6);assertEquals(1,SamuraiKata.along(s),1e-6);
            assertTrue(sh[0].distanceTo(hb[0].add(hb[1].multiply(SamuraiKata.GUARD)))<1e-6,"its mouth at the guard at "+s);
            assertTrue(sh[1].dotProduct(hb[1])>.999999);
        }
        // the whole blade within it
        assertTrue(SamuraiKata.SHEATH>=SamuraiKata.TIP-SamuraiKata.GUARD);
        // through the flourish: at the hip, hanging back and down, the sword out of it
        for(float s=1.2f;s<2.2f;s+=.1f){
            assertEquals(0,SamuraiKata.along(s),1e-6);assertEquals(0,SamuraiKata.home(s),1e-6);
            var sh=SamuraiKata.sheath(FEET,YAW,s);var p=SamuraiKata.pose(s);
            Vec3d hip=KagenoClips.onTorso(FEET,YAW,p,-.27,.8,.06);
            assertTrue(sh[0].distanceTo(hip)<1e-6);
            assertTrue(sh[1].y<.5,"hanging back, not up, at "+s+": "+sh[1].y);
            assertTrue(sh[1].dotProduct(Vec3d.fromPolar(0,YAW))<0,"behind them at "+s);
        }
        // the sword laid at its mouth: the mouth at the blade's point, the scabbard on beyond it
        for(float s=2.4f;s<2.86f;s+=.05f){
            var hb=SamuraiKata.blade(FEET,YAW,s);var sh=SamuraiKata.sheath(FEET,YAW,s);
            assertEquals(1,SamuraiKata.along(s),1e-6);assertEquals(0,SamuraiKata.home(s),1e-6);
            assertTrue(sh[0].distanceTo(hb[0].add(hb[1].multiply(SamuraiKata.TIP)))<1e-6,"its mouth at the point at "+s);
        }
        // home by the click, and never further than home
        assertEquals(1,SamuraiKata.home(SamuraiKata.CLICK+.04f),1e-6);
        float last=0;
        for(float s=2.4f;s<3.1f;s+=1/120f){float h=SamuraiKata.home(s);assertTrue(h>=last-1e-6&&h<=1);last=h;}
        // nothing jumps as it comes to the blade's line and leaves it
        Vec3d before=null;
        for(float s=1.01f;s<3.2f;s+=1/120f){
            Vec3d mouth=SamuraiKata.sheath(FEET,YAW,s)[0];
            if(before!=null)assertTrue(mouth.distanceTo(before)<.3,"the mouth in one frame at "+s+": "+mouth.distanceTo(before));
            before=mouth;
        }
    }
    @Test void theHeadTurnsNoFurtherThanANeckDoes(){
        float[] times=SamuraiKata.CLIP.times();
        for(int i=0;i<times.length;i++){
            R6.Pose p=R6.poseOf(SamuraiKata.CLIP.key(i));
            assertTrue(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(p.headYaw))<=70,"key at "+times[i]+": the head turned "+p.headYaw);
        }
    }
}
