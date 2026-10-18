package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T held (remade in 2.38.0): each fire slash is cut by the blade as it leaves at the server's time, swept the way the
 * slash runs and fits the lane the server strikes in; the spin's one ring of fire goes once round with the blade and
 * has closed when the server's spin cut lands.
 */
class ThroughSkillTest {
    /** Where the blade points round from the cast facing (degrees toward the right) and how high (degrees), at {@code t}. */
    static double[] blade(float t){
        var f=ThroughSkill.CLIP.frame(t);var b=R6.bladeOf(f);
        return new double[]{ThroughSkill.turned(t),Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,b.up()))))};
    }
    @Test void eachSlashLeavesAtTheServersTimeAsTheBladePassesInFront(){
        for(int i=0;i<ThroughSkill.SLASH.length;i++){
            assertEquals(KagenoRules.RUSH_SLASHES[i]/20f,ThroughSkill.SLASH[i],1e-6);
            double[] b=blade(ThroughSkill.SLASH[i]);
            assertTrue(Math.abs(b[0])<40,"slash "+i+": the blade points "+b[0]+" degrees round from the front as it leaves");
            assertTrue(Math.abs(b[1])<35,"slash "+i+": the blade is level enough ("+b[1]+" degrees) to throw it ahead");
        }
    }
    @Test void eachSlashRunsTheWayTheBladeSwings(){
        for(int i=0;i<ThroughSkill.SLASH.length;i++){
            float t=ThroughSkill.SLASH[i];double turn=blade(t+.01f)[0]-blade(t-.01f)[0],drop=blade(t+.01f)[1]-blade(t-.01f)[1];
            assertTrue(Math.signum(turn)==Math.signum(ThroughSkill.ARC[i])&&Math.abs(turn)>8,"slash "+i+" sweeps "+ThroughSkill.ARC[i]+" but the blade turns "+turn);
            // The tilted slashes are falling cuts: the plane is tilted down toward the side the blade falls to.
            if(ThroughSkill.ROLL[i]!=0){
                assertTrue(drop<0,"slash "+i+" is a falling cut");
                assertTrue(Math.signum(ThroughSkill.ROLL[i])==-Math.signum(ThroughSkill.ARC[i]),"slash "+i+" leans down toward its head");
            }
        }
    }
    @Test void theSlashesFitTheLaneTheServerStrikesIn(){
        for(int i=0;i<ThroughSkill.SLASH.length;i++){
            for(double r:new double[]{ThroughSkill.R0[i],ThroughSkill.R1[i]}){
                // The body of the slash (within 50 degrees of its middle) is the lane's width give or take a body.
                double across=r*Math.sin(Math.toRadians(50))*Math.cos(Math.toRadians(ThroughSkill.ROLL[i]));
                assertTrue(across<=KagenoRules.RUSH_SLASH_HALF_WIDTH+1,"slash "+i+" reaches "+across+" blocks to the side");
                double tip=Math.abs(r*Math.sin(Math.toRadians(Math.abs(ThroughSkill.ARC[i])/2))*Math.sin(Math.toRadians(ThroughSkill.ROLL[i])));
                assertTrue(ThroughSkill.LIFT[i]+tip<=3.6&&ThroughSkill.LIFT[i]-tip>=-1.6,"slash "+i+" stays within the lane's height");
            }
        }
        assertEquals(KagenoRules.RUSH_RANGE,ThroughSkill.RANGE,1e-9);
        assertEquals(ThroughSkill.SLASH[0]+ThroughSkill.FLIGHT,KagenoRules.RUSH_SLASHES[0]/20.0+(KagenoRules.RUSH_RANGE-1)/(KagenoRules.RUSH_SLASH_SPEED*20),1e-6);
    }
    @Test void theSpinsRingGoesOnceRoundAndHasClosedWhenTheSpinCutLands(){
        assertTrue(KagenoRules.damage(KagenoRules.RUSH,KagenoRules.RUSH_FINISH,0)>0);
        assertEquals(0,ThroughSkill.round(ThroughSkill.SWEEP),1e-6);
        assertTrue(ThroughSkill.CLOSED<=ThroughSkill.CUT,"the ring closes at "+ThroughSkill.CLOSED+", after the cut "+ThroughSkill.CUT);
        assertTrue(ThroughSkill.CLOSED>ThroughSkill.CUT-.08f,"the ring closes at "+ThroughSkill.CLOSED+", long before the cut");
        // The blade keeps turning the same way through the spin.
        for(float t=ThroughSkill.SWEEP;t+.005f<=ThroughSkill.RING_END;t+=.005f)assertTrue(ThroughSkill.turned(t+.005f)>=ThroughSkill.turned(t)-1e-3,"the blade turns back at "+t);
        double total=ThroughSkill.turned(ThroughSkill.RING_END)-ThroughSkill.turned(ThroughSkill.SWEEP);
        assertTrue(total>=360&&total<=600,"the blade turns "+total+" degrees");
    }
    @Test void theDashLeavesWithTheServerAndTheClipCatchesEveryHit(){
        assertEquals(KagenoRules.RUSH_START/20f,ThroughSkill.D,1e-6);
        assertEquals(KagenoRules.RUSH_ARRIVE/20f,ThroughSkill.ARRIVE,1e-6);
        assertEquals(KagenoRules.RUSH_BURST/20f,ThroughSkill.BURST,1e-6);
        assertEquals(KagenoRules.RUSH_FINISH/20f,ThroughSkill.CUT,1e-6);
        assertTrue(ThroughSkill.CLIP.length()<=ThroughSkill.END&&ThroughSkill.END==KagenoRules.DURATION[KagenoRules.RUSH]/20f);
        var stops=ThroughSkill.CLIP.stops();
        assertEquals(2,stops.length);assertEquals(ThroughSkill.ARRIVE,stops[0].sec(),1e-6);assertEquals(ThroughSkill.CUT,stops[1].sec(),1e-6);
        // Leaning into the dash as it leaves and through it.
        for(float t=ThroughSkill.D;t<ThroughSkill.ARRIVE-.07f;t+=.01f)assertTrue(ThroughSkill.CLIP.frame(t).pitch()>=40,"upright in the dash at "+t);
    }
}
