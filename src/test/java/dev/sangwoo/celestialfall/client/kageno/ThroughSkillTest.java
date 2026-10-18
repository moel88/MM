package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T held (remade in 2.38.0): each fire slash is cut by the blade as it leaves at the server's time, swept the way the
 * slash runs and fits the lane the server strikes in; the spin's one ring of fire goes once round with the blade and
 * has closed when the server's spin cut lands; the fire the left hand summons is the size the user asked for.
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
        Vec3d f=new Vec3d(0,0,1),right=new Vec3d(-1,0,0);
        for(int i=0;i<ThroughSkill.SLASH.length;i++){
            var plane=ThroughSkill.slashPlane(f,right,i);
            assertEquals(0,plane[0].dotProduct(plane[1]),1e-9,"slash "+i+"'s plane is square");
            for(double r:new double[]{ThroughSkill.R0[i],ThroughSkill.R1[i]}){
                double half=Math.abs(ThroughSkill.ARC[i])/2;
                for(double deg=-half;deg<=half;deg+=5){
                    Vec3d o=ThroughSkill.arcOffset(plane,r,deg);double side=o.dotProduct(right),height=ThroughSkill.LIFT[i]+o.y;
                    // The body of the slash (within 50 degrees of its middle) is the lane's width give or take a body.
                    if(Math.abs(deg)<=50)assertTrue(Math.abs(side)<=KagenoRules.RUSH_SLASH_HALF_WIDTH+1,"slash "+i+" reaches "+side+" blocks to the side");
                    // Nowhere far into the floor, and no higher than a body standing at the top of the lane.
                    assertTrue(height>=-1&&height<=5.3,"slash "+i+" at "+deg+" degrees is "+height+" blocks up");
                    // The arc bows forward: no part of it is far ahead of its middle (the rolled ones lean a little).
                    assertTrue(o.dotProduct(f)<=.25,"slash "+i+" at "+deg+" degrees is "+o.dotProduct(f)+" blocks ahead of its middle");
                }
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
    @Test void theSummonedFireIsHalfAsTallAgainAsAPlayerAndBurnsAbout2s(){
        // User request: half as tall again as a player (1.8 blocks) and twice the first one's width (1.4 blocks).
        assertEquals(1.8*1.5,ThroughSkill.FRONT_HEIGHT,1e-9);
        assertEquals(1.4*2,ThroughSkill.FRONT_WIDTH,1e-9);
        // It has shot up to its full height by the first slash and is gone about 2 s after the hand summons it.
        assertEquals(ThroughSkill.FRONT_HEIGHT,ThroughSkill.frontHeight(ThroughSkill.SLASH[0]),1e-6);
        assertTrue(ThroughSkill.front(.3f)>.99f);
        assertTrue(ThroughSkill.FRONT_END>=1.8f&&ThroughSkill.FRONT_END<=2.3f);
        assertEquals(0,ThroughSkill.front(ThroughSkill.FRONT_END),0);
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
