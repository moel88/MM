package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The basic attack's moonlight and starlight (2.103.0): each cut's stardust left where its head passes, drifting down;
 * a small moon where each cut stops, never ahead of her where the struck stand; petals shed as each cut breaks up and
 * falling; hit 2's halo running round her the way its cuts went as they close the ring; hit 4's moon riding the thrown
 * slash and setting as it goes to ink; the empowered blade crackling only through its cuts; all of it gone within the
 * swing's life.
 */
class SlashLightsTest {
    @Test void passesIsWhenTheHeadReachesItsPlace(){
        for(int combo=1;combo<=4;combo++)for(var s:IsosangSlash.arcs(combo))for(double f=.05;f<.96;f+=.05){
            float t=SlashLights.passes(s,f);
            assertTrue(t>=s.t0()&&t<=s.t1());
            assertEquals(f,IsosangSlash.head(s,t),1e-3,"hit "+combo+" at "+f);
        }
    }
    @Test void eachCutLeavesItsStardustAlongItsEdgeAsItsHeadPassesAndItDriftsDown(){
        for(int combo=1;combo<=4;combo++)for(var s:IsosangSlash.arcs(combo)){
            int n=SlashLights.traceCount(s,false),left=0;
            assertTrue(n>=8&&n<=12,"a few, not a cloud: "+n);
            assertTrue(SlashLights.traceCount(s,true)>n,"more in the empowered combo");
            for(int k=0;k<n;k++){
                if(!SlashLights.traced(s,k,n))continue;left++;
                float born=SlashLights.traceBorn(s,k,n);double f=SlashLights.traceFrac(s,k,n);
                assertEquals(f,IsosangSlash.head(s,born),1e-3,"left as the head passes its place");
                Vec3d at=SlashLights.traceLocal(s,k,n,0),edge=IsosangSlash.point(s,s.from()+(s.to()-s.from())*f,s.outer());
                assertTrue(at.distanceTo(edge)<.4,"off the cutting edge where it passed: "+at.distanceTo(edge));
                assertTrue(SlashLights.traceLocal(s,k,n,.5f).y<at.y-.1,"drifting down");
            }
            assertTrue(left>=5,"hit "+combo+" leaves its trace: "+left);
        }
    }
    @Test void aSmallMoonShowsWhereEachCutStopsNeverAheadWhereTheStruckStand(){
        for(int combo=1;combo<=3;combo++)for(var s:IsosangSlash.arcs(combo)){
            assertEquals(0,SlashLights.moonShown(s,s.t1()-.03f),1e-6,"not before its point stops");
            assertTrue(SlashLights.moonShown(s,s.t1()+.05f)>.9f,"as it stops");
            assertEquals(0,SlashLights.moonShown(s,s.t1()+SlashLights.MOON_LIFE),1e-6,"gone within half a second");
            Vec3d p=SlashLights.moonLocal(s,0),end=IsosangSlash.point(s,s.to(),s.outer());
            assertTrue(p.distanceTo(end)<.3||p.y==.3,"just out from where its point stopped: "+p+" "+end);
            assertTrue(p.y>=.3-1e-9,"off the ground");
            assertTrue(SlashLights.moonLocal(s,.4f).y>=p.y,"rising a little");
            assertFalse(p.x>1.2&&Math.abs(p.z)<1.2,"hit "+combo+": not ahead of her, over whom it strikes: "+p);
            // its horns back the way its point came: the way it moves as it stops runs along its sweep
            Vec3d way=SlashLights.endWay(s),r=end.subtract(s.centre()).normalize();
            assertEquals(1,way.length(),1e-6);assertTrue(Math.abs(way.dotProduct(r))<.1,"along its edge");
        }
    }
    @Test void petalsAreShedAsEachCutBreaksUpAndTumbleDown(){
        for(int combo=1;combo<=3;combo++)for(var s:IsosangSlash.arcs(combo)){
            int n=SlashLights.petalCount(s);assertTrue(n>=3&&n<=5,"a few: "+n);
            for(int i=0;i<n;i++){
                float born=SlashLights.petalBorn(s,i);assertTrue(born>=s.eat0()-1e-6&&born<=s.eat1()+1e-6,"as it is eaten away");
                double[] a=SlashLights.petal(s,i,0),b=SlashLights.petal(s,i,.5f);
                assertTrue(b[1]<a[1]||a[1]<=.03+1e-9,"falling: hit "+combo);
                for(float age=0;age<SlashLights.PETAL_LIFE1;age+=.05f)assertTrue(SlashLights.petal(s,i,age)[1]>=.03-1e-9,"on the ground at the lowest");
            }
        }
    }
    @Test void hit2sHaloRunsRoundHerTheWayItsCutsWentAsTheyCloseTheRing(){
        float closes=IsosangSlash.H2_ARCS[1].t1();
        for(int k=0;k<SlashLights.HALO;k++){
            assertEquals(0,SlashLights.haloShown(k,closes-.005f),1e-6,"not before the ring is closed");
            assertTrue(SlashLights.haloLit(k)<closes+SlashLights.HALO_RUN+1e-6);
            if(k>0){
                assertTrue(SlashLights.haloLit(k)>SlashLights.haloLit(k-1),"one after another");
                assertTrue(SlashLights.haloDeg(k)<SlashLights.haloDeg(k-1),"anticlockwise seen from above, as both cuts");
            }
            Vec3d p=SlashLights.haloLocal(k);
            assertEquals(SlashLights.HALO_R,Math.hypot(p.x-SlashLights.HALO_C.x,p.z-SlashLights.HALO_C.z),1e-9,"on the ring round her");
            assertTrue(SlashLights.haloLit(k)+SlashLights.haloLife(k)<1,"gone within a second");
        }
        // the first lights by where the front cut stopped
        assertTrue(Math.abs(SlashLights.haloDeg(0)-IsosangSlash.H2_ARCS[1].to())<=360.0/SlashLights.HALO);
    }
    @Test void hit4sMoonRidesTheThrownSlashThenSetsAsItGoesToInk(){
        Vec3d feet=new Vec3d(0,64,0);float at=IsosangSlash.blow(4,0);
        assertEquals(0,SlashLights.waveMoonShown(at-.01f),1e-6);
        assertTrue(SlashLights.waveMoonShown(at+.05f)>.9f,"with the throw");
        for(float t=at+.005f;t<SlashLights.SET0;t+=.02f){
            var sw=IsosangSlash.wave(feet,0,t);Vec3d p=SlashLights.waveMoonAt(feet,0,t);
            assertTrue(p.distanceTo(sw.outer()[sw.n()/2].add(0,.3,0))<1e-9,"riding its leading edge at "+t);
        }
        Vec3d top=SlashLights.waveMoonAt(feet,0,SlashLights.SET0),prev=top;
        for(float t=SlashLights.SET0;t<=SlashLights.SET1;t+=1/60f){
            Vec3d p=SlashLights.waveMoonAt(feet,0,t);
            assertEquals(top.x,p.x,1e-9);assertEquals(top.z,p.z,1e-9);
            assertTrue(p.y<=prev.y+1e-9,"sinking");assertTrue(prev.y-p.y<.08,"smoothly");prev=p;
        }
        assertEquals(top.y-SlashLights.SET_DROP,SlashLights.waveMoonAt(feet,0,SlashLights.SET1).y,1e-6,"set under where it stopped");
        assertTrue(SlashLights.waveMoonAt(feet,0,SlashLights.SET1).y>feet.y,"not into the ground");
        assertEquals(0,SlashLights.waveMoonShown(SlashLights.SET1),1e-6,"gone as it sets");
    }
    @Test void theEmpoweredBladeCracklesOnlyThroughItsCuts(){
        assertTrue(SlashLights.charge(2,.2f)>.9f&&SlashLights.charge(3,.6f)>.9f,"through the cuts");
        assertEquals(0,SlashLights.charge(2,.5f),1e-6,"done once hit 2's cuts are");
        assertEquals(0,SlashLights.charge(3,1.15f),1e-6,"done once hit 3's last cut is");
        for(int combo=2;combo<=3;combo++)for(float t=0;t<1.4f;t+=1/60f)
            assertTrue(Math.abs(SlashLights.charge(combo,t+1/60f)-SlashLights.charge(combo,t))<.25f,"no jump at "+t);
        for(int seed=0;seed<200;seed++){double o=Math.abs(SlashLights.boltOff(seed));assertTrue(o>=.07&&o<=.12,"just off the blade's edges");}
    }
    @Test void allOfItIsGoneWithinItsSwingsLife(){
        float life=new IsosangSlash().lifetime();
        assertTrue(SlashLights.last()<life-.3f,"well within: "+SlashLights.last()+" of "+life);
        assertTrue(SlashLights.last()>IsosangRules.LENGTH[3]/20f-.5f,"and the longest hit's dust outlasts its cuts");
    }
}
