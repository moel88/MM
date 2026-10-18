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
        assertEquals(NeonThrustSkill.OPENERS.length+ticks.length*2-1,NeonThrustSkill.THRUSTS.length);
        assertEquals(NeonThrustSkill.THRUSTS.length,NeonThrustSkill.AIM.length);
        assertTrue(NeonThrustSkill.THRUSTS[0]>NeonThrustSkill.BARRAGE,"the barrage opens with a thrust");
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
            // Fully out: the tip is well ahead of where it was drawn in to before (at the start of the barrage, or the last recoil).
            double tipOut=out[0].add(out[1].multiply(NeonThrustSkill.TIP)).z;
            var back=sword(i==0?NeonThrustSkill.BARRAGE:NeonThrustSkill.THRUSTS[i-1]+.06f);
            double tipBack=back[0].add(back[1].multiply(NeonThrustSkill.TIP)).z;
            assertTrue(tipOut>2,"thrust "+i+" tip only "+tipOut+" ahead");
            assertTrue(tipOut-tipBack>.3,"thrust "+i+" moves the tip only "+(tipOut-tipBack));
        }
    }
    /** User request (2.38.2): E is walked and aimed through its thrusts, and its finisher is planted. */
    @Test void walkedThrustsStillGoTheWayTheCasterAimsAndTheFinisherIsPlanted(){
        for(int i=0;i<NeonThrustSkill.THRUSTS.length;i++){
            var f=NeonThrustSkill.walking(NeonThrustSkill.CLIP.frame(NeonThrustSkill.THRUSTS[i]),1);
            var out=KagenoClips.blade(FEET,0,R6.poseOf(f));
            Vec3d want=world(NeonThrustSkill.AIM[i][0],NeonThrustSkill.AIM[i][1]);
            // Walking takes out the lunge and the crouch, not the aim.
            assertTrue(out[1].dotProduct(want)>Math.cos(Math.toRadians(6)),"walked thrust "+i+" points "+out[1]+" not "+want);
            assertEquals(0,f.fw(),1e-6);assertTrue(Math.abs(f.up())<.06,"walked thrust "+i+" crouches "+f.up());
        }
        int[] ticks=KagenoRules.eTicks();int last=ticks[ticks.length-1];
        assertTrue(KagenoRules.eWalking(0)&&KagenoRules.eWalking(last),"walked and aimed up to the last thrust");
        assertTrue(KagenoRules.E_PLANT>last&&!KagenoRules.eWalking(KagenoRules.E_PLANT)&&!KagenoRules.eWalking(KagenoRules.E_FINISH),"the finisher is planted");
        assertTrue(KagenoRules.E_PLANT/20f<=NeonThrustSkill.BACK,"planted before the sword is drawn back");
    }
    @Test void theLungeLevelsTheBladeAtTheTargetSideOn(){
        var set=sword(.3f);
        assertTrue(set[1].z>.99,"the blade points "+set[1]);
        // The sword hand leads: well ahead of the body and on its right (-x).
        assertTrue(set[0].z>.8&&set[0].x<0,"the hand is at "+set[0]);
        var p=R6.poseOf(NeonThrustSkill.CLIP.frame(.3f));
        assertTrue(p.yaw>45,"side-on, turned "+p.yaw);
    }
    @Test void theSwingDrawsBackThenRisesThroughTheFrontAndIsBehindTheHeadWhenTheTargetBursts(){
        var back=sword(NeonThrustSkill.BACK+.05f);
        assertTrue(back[1].z<-.9,"drawn back, the blade points "+back[1]);
        assertTrue(back[0].y<1.3,"at the hip, the hand is at "+back[0].y);
        var low=sword(NeonThrustSkill.LOW);
        assertTrue(low[1].y<-.25&&low[1].z>.8,"low in front, the blade points "+low[1]);
        assertTrue(low[0].add(low[1].multiply(NeonThrustSkill.TIP)).y>0,"the tip stays off the ground");
        var rising=sword(2.665f);
        assertTrue(rising[1].y>.3&&rising[1].z>.5,"rising through the front, the blade points "+rising[1]);
        assertTrue(sword(NeonThrustSkill.UPRIGHT)[1].y>.95,"straight up");
        var cut=sword(NeonThrustSkill.SLASH);
        assertTrue(cut[1].z<-.5&&cut[1].y>.15,"over and behind on the burst, the blade points "+cut[1]);
        assertTrue(sword(NeonThrustSkill.LEVEL)[1].z<-.95,"level behind at the end of the swing");
    }
    @Test void backAtRestBeforeTheInputIsHandedBack(){
        assertTrue(NeonThrustSkill.REST<=KagenoRules.inputEnd(KagenoRules.E)/20f+1e-6);
        assertTrue(NeonThrustSkill.CLIP.length()<=NeonThrustSkill.REST+1e-6);
        assertTrue(sword(NeonThrustSkill.REST)[1].dotProduct(sword(0)[1])>.999);
        assertTrue(new NeonThrustSkill().lifetime()*20>=KagenoRules.DURATION[KagenoRules.E]);
    }
    @Test void manyAfterimageStabsFillTheAirWhileTheThrustsLand(){
        float first=NeonThrustSkill.THRUSTS[0],last=NeonThrustSkill.THRUSTS[NeonThrustSkill.THRUSTS.length-1];
        int fewest=99;
        for(float t=first+.05f;t<=last;t+=.01f)fewest=Math.min(fewest,NeonThrustSkill.ghosts(t).size());
        assertTrue(fewest>=8,"only "+fewest+" afterimages at once");
        // Right on a thrust there are more of them.
        int onThrust=NeonThrustSkill.ghosts(NeonThrustSkill.THRUSTS[4]+.02f).size();
        assertTrue(onThrust>=13,"only "+onThrust+" on a thrust");
        // None before the thrusts; the last ones have faded a quarter of a second after the last thrust.
        assertTrue(NeonThrustSkill.ghosts(first-.1f).isEmpty());
        assertTrue(NeonThrustSkill.ghosts(last+.3f).isEmpty());
    }
    @Test void theStormBlowsFromTheBarrageUntilTheSwordIsDrawnBack(){
        assertEquals(0,NeonThrustSkill.storm(NeonThrustSkill.BARRAGE-.05f),1e-6);
        for(float t=NeonThrustSkill.BARRAGE+.08f;t<2.36f;t+=.01f)assertEquals(1,NeonThrustSkill.storm(t),1e-6,"at "+t);
        assertEquals(0,NeonThrustSkill.storm(NeonThrustSkill.CALM),1e-6);
        assertTrue(NeonThrustSkill.CALM<NeonThrustSkill.BACK);
    }
    @Test void theNeonFlaresUpTheBladeWithTheStepThenStaysLitUntilItFlakesAway(){
        assertEquals(0,NeonThrustSkill.lit(0));assertEquals(0,NeonThrustSkill.glow(0));
        assertTrue(NeonThrustSkill.lit(.06f)>.2f&&NeonThrustSkill.lit(.06f)<.8f);
        assertEquals(1,NeonThrustSkill.lit(NeonThrustSkill.FLARE));
        for(float t=.25f;t<3.1f;t+=.01f)assertTrue(NeonThrustSkill.glow(t)>=.99f,"dark at "+t);
        assertTrue(NeonThrustSkill.glow(2.8f)>1.8f,"charged through the swing");
        assertEquals(0,NeonThrustSkill.glow(3.4f),1e-6);
    }
    @Test void theScreenShocksHardestWhenTheTargetBursts(){
        var e=new NeonThrustSkill();
        assertTrue(e.pulse(NeonThrustSkill.SLASH+.01f)>.7f);
        assertTrue(e.pulse(NeonThrustSkill.SLASH-.05f)<.1f);
        assertTrue(e.shake(NeonThrustSkill.SLASH+.01f)>1f);
        assertNull(e.kick(3.5f));
    }
}
