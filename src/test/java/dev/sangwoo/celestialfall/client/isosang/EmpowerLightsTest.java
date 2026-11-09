package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E's and the empowered combo's moonlight and starlight (2.103.0): E's moon rising out of the hand and the light drawn
 * into it; a stopped sword's flower opening and fading; sparks only while the blade crackles; the giant sword's stars lit
 * in its shape as it shows and given up to it; its one moon riding its point, rising over where it stops and climbing on
 * as it goes; its stardust left along its way; petals off it while it hangs; motes rising as it goes; a ring where she
 * lands from her leap back.
 */
class EmpowerLightsTest {
    static float blow(int combo,int i){return IsosangRules.blowAt(combo,i)/20f;}

    @Test void esMoonRisesOutOfTheHandAndLightIsDrawnIntoIt(){
        Vec3d hand=new Vec3d(3,65,-2);
        assertEquals(0,EmpowerLights.eMoonShown(0),1e-6);assertTrue(EmpowerLights.eMoonShown(.05f)>.9f,"in E's flash");
        assertEquals(0,EmpowerLights.eMoonShown(EmpowerLights.E_MOON_LIFE),1e-6,"gone within a second");
        assertTrue(EmpowerLights.eMoonAt(hand,0).distanceTo(hand)<.2,"out of the hand");
        assertTrue(EmpowerLights.eMoonAt(hand,.8f).y>hand.y+1.4,"risen over her head");
        for(int k=0;k<EmpowerLights.GATHER;k++){
            double d=EmpowerLights.gatherStart(hand,k).distanceTo(hand);assertTrue(d>=1-1e-9&&d<=1.6+1e-9,"from round the hand: "+d);
            assertTrue(EmpowerLights.gatherFrom(k)+EmpowerLights.GATHER_TIME<=.17f,"in by the time the flash is out");
        }
    }
    @Test void aStoppedSwordsFlowerOpensAndFades(){
        assertEquals(0,EmpowerLights.bloomShown(-.01f),1e-6);assertTrue(EmpowerLights.bloomShown(.05f)>.9f);
        assertEquals(0,EmpowerLights.bloomShown(EmpowerLights.BLOOM_LIFE),1e-6);
        float prev=0;for(float age=0;age<=.3f;age+=.01f){float o=EmpowerLights.bloomOpen(age);assertTrue(o>=prev-1e-6,"opening");prev=o;}
        assertEquals(1,EmpowerLights.bloomOpen(.12f),1e-6,"open in an eighth of a second");
    }
    @Test void sparksSpringOnlyWhileTheBladeCracklesAsSheCrouches(){
        for(int k=0;k<EmpowerLights.CROUCH_SPARKS;k++){
            float born=EmpowerLights.sparkBorn(k);
            assertTrue(born>=.4f&&born+EmpowerLights.sparkLife(k)<=.83f,"with the crouch's lightning (0.38 to 0.8 s): "+born);
            assertTrue(born>blow(IsosangRules.THROWS,2),"after the third throw");
        }
    }
    @Test void theGiantSwordsStarsAreLitInItsShapeAsItShowsAndGivenUpToIt(){
        var c=IsosangEmpowerTest.giantCast();
        var g=IsosangEmpower.giant(c,.06f);assertNotNull(g);
        assertTrue(EmpowerLights.on(g,0,SpiritSword.TIP).distanceTo(g.point())<1e-6,"its point's star at its point");
        assertTrue(EmpowerLights.on(g,0,SpiritSword.POMMEL).distanceTo(g.butt())<1e-6,"its pommel's star at its pommel");
        Vec3d l=EmpowerLights.on(g,SweepLights.STARS[2][0],SweepLights.STARS[2][1]),r=EmpowerLights.on(g,SweepLights.STARS[4][0],SweepLights.STARS[4][1]);
        assertEquals(l.y,r.y,.05,"its guard's points level, as it lies");
        for(int k=0;k<SweepLights.STARS.length;k++){
            assertTrue(EmpowerLights.conLit(k)+.015f<=EmpowerLights.CON_OUT0,"all lit before they are given up: "+k);
            assertEquals(0,EmpowerLights.conShown(k,EmpowerLights.CON_OUT1),1e-6,"given up once it has formed");
            for(int j=0;j<SweepLights.STARS.length;j++)if(SweepLights.STARS[j][1]>SweepLights.STARS[k][1]+.3)
                assertTrue(EmpowerLights.conLit(j)>EmpowerLights.conLit(k),"pommel to point");
        }
        assertTrue(EmpowerLights.CON_OUT1<=blow(IsosangRules.THRUST,0),"gone before it is thrust");
    }
    @Test void itsMoonRidesItsPointThenRisesOverWhereItStops(){
        var c=IsosangEmpowerTest.giantCast();float at=blow(IsosangRules.THRUST,0),arr=IsosangEmpower.arrive(c);
        assertNull(EmpowerLights.giantMoonAt(c,at-.01f));
        assertEquals(0,EmpowerLights.giantMoonShown(c,at-.01f),1e-6);assertTrue(EmpowerLights.giantMoonShown(c,at+.05f)>.9f);
        for(float t=at;t<=arr;t+=.01f)
            assertTrue(EmpowerLights.giantMoonAt(c,t).distanceTo(IsosangEmpower.giant(c,t).point().add(0,.35,0))<1e-6,"riding its point at "+t);
        assertTrue(EmpowerLights.giantMoonAt(c,arr+EmpowerLights.G_MOON_RISE).distanceTo(IsosangEmpower.end(c).add(0,.9,0))<1e-6,"over where it stopped");
        float in=EmpowerLights.inked(c);
        assertEquals(arr+IsosangEmpower.GIANT_HANG,in,1e-6,"the ink comes for it once it has hung");
        Vec3d prev=EmpowerLights.giantMoonAt(c,arr);
        for(float t=arr;t<in+EmpowerLights.G_MOON_OUT1;t+=1/60f){Vec3d p=EmpowerLights.giantMoonAt(c,t);assertTrue(p.distanceTo(prev)<.2,"rising smoothly at "+t);prev=p;}
        assertTrue(EmpowerLights.giantMoonAt(c,in+.8f).y>EmpowerLights.giantMoonAt(c,in).y+.8,"climbing on as the ink swallows it");
        assertTrue(EmpowerLights.giantMoonShown(c,in+.3f)>.9f,"still there as it climbs");
        assertEquals(0,EmpowerLights.giantMoonShown(c,in+EmpowerLights.G_MOON_OUT1),1e-6);
        assertTrue(in+EmpowerLights.G_MOON_OUT1<new IsosangSlash().lifetime(),"gone within the swing's life");
    }
    @Test void itsStardustIsLeftAlongItsWayAndDriftsDown(){
        var c=IsosangEmpowerTest.giantCast();float at=blow(IsosangRules.THRUST,0),arr=IsosangEmpower.arrive(c);
        for(int k=0;k<EmpowerLights.G_TRACE;k++){
            float born=EmpowerLights.gTraceBorn(c,k);assertTrue(born>=at&&born<=arr,"as its point passes");
            Vec3d p=EmpowerLights.gTraceAt(c,k,0);assertTrue(p.distanceTo(IsosangEmpower.giant(c,born).point())<.5,"where its point was");
            assertTrue(EmpowerLights.gTraceAt(c,k,.5f).y<p.y,"drifting down");
        }
    }
    @Test void petalsFallOffItWhileItHangsAndMotesRiseAsItGoes(){
        var c=IsosangEmpowerTest.giantCast();float arr=IsosangEmpower.arrive(c);
        for(int i=0;i<EmpowerLights.G_PETALS;i++){
            float born=EmpowerLights.gPetalBorn(c,i);
            assertTrue(born>=arr&&born<=arr+IsosangEmpower.GIANT_HANG+.1f,"while it hangs");
            assertTrue(EmpowerLights.gPetal(c,i,.6f)[1]<EmpowerLights.gPetal(c,i,0)[1],"falling");
        }
        for(int i=0;i<EmpowerLights.PARTS;i++)assertTrue(EmpowerLights.partAt(c,i,.5f).y>EmpowerLights.partAt(c,i,0).y+.2,"rising");
    }
    @Test void sheLandsFromHerLeapBackWhereItEnds(){
        var c=IsosangEmpowerTest.giantCast();
        assertTrue(IsosangEmpower.leapt(c,EmpowerLights.LAND).distanceTo(IsosangEmpower.leapt(c,EmpowerLights.LAND+.2f))<1e-9,"landed by then");
        assertTrue(IsosangEmpower.leapt(c,EmpowerLights.LAND-.05f).distanceTo(IsosangEmpower.leapt(c,EmpowerLights.LAND))>.05,"still leaping just before");
    }
}
