package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeonThrustSkillTest {
    static final Vec3d FEET=Vec3d.ZERO;
    static Vec3d[] sword(float sec){return KagenoClips.blade(FEET,0,R6.poseOf(NeonThrustSkill.CLIP.frame(sec)));}
    static Vec3d world(double elevation,double azimuth){
        // Facing +z with yaw 0; azimuth toward the character's right (-x).
        double e=Math.toRadians(elevation),a=Math.toRadians(azimuth);
        return new Vec3d(-Math.cos(e)*Math.sin(a),Math.sin(e),Math.cos(e)*Math.cos(a));
    }
    @Test void everyServerHitLandsOnAThrustAndOneMoreFallsBetweenEachPair(){
        int[] ticks=KagenoRules.eTicks();
        assertEquals(ticks.length*2-1,NeonThrustSkill.THRUSTS.length);
        assertEquals(NeonThrustSkill.THRUSTS.length,NeonThrustSkill.AIM.length);
        int strikes=0;
        for(int i=0;i<NeonThrustSkill.THRUSTS.length;i++){
            if(i>0)assertTrue(NeonThrustSkill.THRUSTS[i]-NeonThrustSkill.THRUSTS[i-1]>=.1f,"thrust "+i+" too soon after the last");
            if(NeonThrustSkill.STRIKES[i]){assertEquals(ticks[strikes]/20f,NeonThrustSkill.THRUSTS[i],1e-6);strikes++;}
        }
        assertEquals(ticks.length,strikes);
        assertEquals(KagenoRules.E_FINISH/20f,NeonThrustSkill.SLASH,1e-6);
    }
    @Test void eachThrustReachesOutAlongItsAim(){
        for(int i=0;i<NeonThrustSkill.THRUSTS.length;i++){
            var out=sword(NeonThrustSkill.THRUSTS[i]);
            Vec3d want=world(NeonThrustSkill.AIM[i][0],NeonThrustSkill.AIM[i][1]);
            assertTrue(out[1].dotProduct(want)>Math.cos(Math.toRadians(3)),"thrust "+i+" points "+out[1]+" not "+want);
            // Fully out: the tip is well ahead of where it was drawn back to before (the stance, or the last recoil).
            double tipOut=out[0].add(out[1].multiply(NeonThrustSkill.TIP)).z;
            var back=sword(i==0?.73f:NeonThrustSkill.THRUSTS[i-1]+.06f);
            double tipBack=back[0].add(back[1].multiply(NeonThrustSkill.TIP)).z;
            assertTrue(tipOut>2,"thrust "+i+" tip only "+tipOut+" ahead");
            assertTrue(tipOut-tipBack>.3,"thrust "+i+" moves the tip only "+(tipOut-tipBack));
        }
    }
    @Test void theRisingSlashCrossesTheTargetOnTheFinisherAndEndsOverTheHead(){
        var cut=sword(NeonThrustSkill.SLASH);
        double elevation=Math.toDegrees(Math.asin(cut[1].y));
        assertTrue(elevation>0&&elevation<35&&cut[1].z>.8,"at the cut the blade points "+cut[1]);
        assertTrue(sword(NeonThrustSkill.SLASH-.05f)[1].y<-.2,"it comes from below");
        assertTrue(sword(2.975f)[1].y>.9,"over the head");
        // The swing sweeps up through the plane the crescent is drawn in, ending past the vertical.
        double before=Math.toDegrees(Math.atan2(sword(2.87f)[1].y,sword(2.87f)[1].z)),after=Math.toDegrees(Math.atan2(sword(2.975f)[1].y,sword(2.975f)[1].z));
        assertTrue(before<-10&&after>75,"swing from "+before+" to "+after);
    }
    @Test void backAtRestBeforeTheInputIsHandedBack(){
        assertTrue(NeonThrustSkill.REST<=KagenoRules.inputEnd(KagenoRules.E)/20f+1e-6);
        assertTrue(NeonThrustSkill.CLIP.length()<=NeonThrustSkill.REST+1e-6);
        assertTrue(sword(NeonThrustSkill.REST)[1].dotProduct(sword(0)[1])>.999);
        assertTrue(new NeonThrustSkill().lifetime()*20>=KagenoRules.DURATION[KagenoRules.E]);
    }
    @Test void theNeonRunsUpTheBladeThenStaysLitUntilItFlakesAway(){
        assertEquals(0,NeonThrustSkill.lit(0));assertEquals(0,NeonThrustSkill.glow(.1f));
        assertTrue(NeonThrustSkill.lit(.29f)>.2f&&NeonThrustSkill.lit(.29f)<.8f);
        assertEquals(1,NeonThrustSkill.lit(NeonThrustSkill.LIT1));
        for(float t=.6f;t<3.1f;t+=.01f)assertTrue(NeonThrustSkill.glow(t)>=.99f,"dark at "+t);
        assertTrue(NeonThrustSkill.glow(NeonThrustSkill.RISE)>1.8f,"charged before the slash");
        assertEquals(0,NeonThrustSkill.glow(3.4f),1e-6);
    }
}
