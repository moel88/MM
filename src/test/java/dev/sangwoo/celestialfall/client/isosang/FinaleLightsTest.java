package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static dev.sangwoo.celestialfall.client.isosang.SwordFinaleTest.cast;
import static dev.sangwoo.celestialfall.client.isosang.SwordFinaleTest.run;
import static org.junit.jupiter.api.Assertions.*;

/**
 * H again's moonlight and starlight and its ink (2.101.2, user request: "잉크가 더 많이 튀게 바꿔주고 이제 엄청 멋있고 예쁘게
 * 바꿔줘"): far more ink at every strike, flung more the way each drove in, and all of it gone while it is drawn; the dusk
 * moon in her close shot behind her, off her face and inside the dusk; the petals and stardust shed round her lap and
 * gone soon after; the fan's halo laced between neighbours; a third of the swords shooting pillars; the gleams running
 * whole, each from its hilt; the sparks springing up and falling; the net only between neighbours.
 */
class FinaleLightsTest {
    @Test void farMoreInkIsFlungAtEachStrikeMoreTheWayItDroveInAndAllOfItIsGoneWhileItIsDrawn(){
        assertTrue(SwordFinale.SPLASH>=3.5,"a big splash round each foot");
        int flung=SwordFinale.CLUMPS+SwordFinale.FLAKES+SwordFinale.DROPS,strokes=SwordFinale.SPIKES+SwordFinale.CROWN;
        assertTrue(flung>=24,"far more ink flung off each (2.101.1: 8): "+flung);
        assertTrue(strokes>=12,"strokes of ink shooting up and a crown round each (2.101.1: 2): "+strokes);
        assertTrue(SwordFinale.SPLASH_RING>=6,"smaller splashes flung out round each");
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);
        double along=0;int n=0;
        for(var p:plans){
            double floor=p.foot().y;Vec3d ahead=SwordFinale.drove(p);
            for(int kind=0;kind<3;kind++){
                int count=kind==0?SwordFinale.CLUMPS:kind==1?SwordFinale.FLAKES:SwordFinale.DROPS;
                for(int i=0;i<count;i++){
                    var b=SwordFinale.inkBit(p,kind,i);double l=b.lands(floor);
                    assertTrue(l>.3&&l<SwordFinale.BIT_LIFE-.05,"up a while and down again before it is gone: "+l);
                    Vec3d q=b.at(l).subtract(p.foot());
                    assertTrue(Math.hypot(q.x,q.z)<7,"lands round its foot: "+Math.hypot(q.x,q.z));
                    along+=q.x*ahead.x+q.z*ahead.z;n++;
                    // its splash where it lands dries while it is drawn
                    if(kind!=1)assertTrue(b.born()+l+IsosangSlash.SPLAT_LIFE<=SwordFinale.LIFE+.05,"its splash gone while it is drawn");
                }
            }
            assertTrue(p.plant()+SwordFinale.SPLASH_LIFE<=SwordFinale.LIFE,"the splash round its foot gone while it is drawn");
        }
        assertTrue(along/n>.3,"flung more the way they drove in: "+along/n);
    }
    @Test void theDuskMoonStandsBehindHerInHerCloseShotOffHerFaceInsideTheDusk(){
        var c=cast();var r=run();var fr=SwordFinale.frame(c,r);
        for(float t=SwordFinale.CLOSE0;t<IsosangRules.FINALE_LAUNCH;t+=1/30f){
            var s=SwordFinale.shot(c,r,t);Vec3d moon=SwordFinale.duskMoon(fr,t);
            Vec3d fw=s.look().subtract(s.eye()).normalize(),toMoon=moon.subtract(s.eye()).normalize();
            Vec3d right=fw.crossProduct(new Vec3d(0,1,0)).normalize(),up=right.crossProduct(fw);
            double x=Math.toDegrees(Math.atan2(toMoon.dotProduct(right),toMoon.dotProduct(fw))),y=Math.toDegrees(Math.atan2(toMoon.dotProduct(up),toMoon.dotProduct(fw)));
            double r0=Math.toDegrees(Math.atan(FormationSky.MOON_R/moon.distanceTo(s.eye())));
            assertTrue(toMoon.dotProduct(fw)>0,"ahead of her camera");
            assertTrue(y-r0>2&&y+r0<s.fov()/2-1,"all of it in the frame, over her: "+y+" ±"+r0);
            assertTrue(Math.abs(x)>4&&Math.abs(x)+r0<36,"to one side of her, in the frame: "+x);
            Vec3d head=SwordFinale.head(fr,t);
            double off=Math.toDegrees(Math.acos(Math.min(1,toMoon.dotProduct(head.subtract(s.eye()).normalize()))));
            assertTrue(off-r0>4,"off her face: "+off);
        }
        Vec3d eye=fr.spot().add(0,FormationSky.SKY_EYE,0);
        for(float t=0;t<IsosangRules.FINALE_BACK1;t+=.1f)
            assertTrue(SwordFinale.duskMoon(fr,t).distanceTo(eye)+FormationSky.MOON_R<FormationSky.SKY_R*.9,"in front of the dusk's dome");
        assertEquals(0,SwordFinale.moonLight(IsosangRules.FINALE_FLASH),1e-6,"not there before it flashes in");
        assertEquals(1,SwordFinale.moonLight(1.2f),1e-6,"full by the time she stops in the air");
    }
    @Test void thePetalsAndStardustAreShedRoundHerLapAndGoneSoonAfter(){
        for(int i=0;i<FinaleLights.PETALS;i++){
            float b=FinaleLights.petalBorn(i);
            assertTrue(b>=IsosangRules.FINALE_RIDE&&b<=IsosangRules.FINALE_LAP1,"shed as she rides: "+b);
        }
        assertTrue(IsosangRules.FINALE_LAP1+FinaleLights.PETAL_LIFE1<IsosangRules.FINALE_LAUNCH+.5f,"gone soon after she stops");
        assertTrue(IsosangRules.FINALE_LAP1+FinaleLights.LAP_DUST_LIFE<IsosangRules.FINALE_LAUNCH,"the stardust gone before they fly");
        var c=cast();var r=run();var fr=SwordFinale.frame(c,r);
        // the comet's head rides her sword's point, ahead of her feet
        for(float t=IsosangRules.FINALE_RIDE;t<IsosangRules.FINALE_LAP1;t+=1/30f){
            Vec3d tip=FinaleLights.cometAt(fr,t),feet=SwordFinale.body(fr,t);
            assertTrue(tip.subtract(feet).dotProduct(SwordFinale.heading(fr,t))>.9,"ahead of her along her way");
            assertTrue(Math.abs(tip.y-feet.y)<.5,"at her feet's height");
        }
    }
    @Test void theFansHaloIsLacedBetweenNeighboursAndItsShimmerRunsOutFromTheTop(){
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);
        int[] lace=FinaleLights.halo(plans);
        assertTrue(lace.length>=10,"the two inner rings: "+lace.length);
        for(int i=0;i+1<lace.length;i++){
            double a=SwordFinale.slotAngle(SwordFinale.slot(lace[i])),b=SwordFinale.slotAngle(SwordFinale.slot(lace[i+1]));
            assertTrue(b>=a,"by their angle round the fan");
            double d=FinaleLights.fanPommel(plans[lace[i]],2f).distanceTo(FinaleLights.fanPommel(plans[lace[i+1]],2f));
            assertTrue(d<2.2,"neighbours: "+d);
        }
        for(var p:plans){
            if(p.steed())continue;
            float s0=FinaleLights.shimmer(p);
            assertTrue(s0>=FinaleLights.fanned(p),"its shimmer once it is in the fan");
            assertTrue(s0+FinaleLights.SHIMMER<=SwordFinale.FOLD0,"and run before the fan folds: "+s0);
        }
    }
    @Test void aThirdOrSoOfTheSwordsShootPillarsAndTheirSparksSpringUpAndFall(){
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);
        int n=0;for(var p:plans)if(FinaleLights.pillar(p))n++;
        assertTrue(n>=5&&n<=15,"a third or so: "+n);
        Vec3d o=Vec3d.ZERO,dir=new Vec3d(1,0,0);
        for(double up=3.5;up<=8;up+=1.5){
            double peak=up/12;
            assertTrue(FinaleLights.spark(o,dir,2,up,peak).y>FinaleLights.spark(o,dir,2,up,.05).y,"up");
            assertTrue(FinaleLights.spark(o,dir,2,up,peak+.2).y<FinaleLights.spark(o,dir,2,up,peak).y,"and falling");
        }
    }
    @Test void eachGleamRunsWholeFromItsHiltAndOnlyWhileItStands(){
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);
        for(var p:plans){
            double last=9;int runs=0;
            for(float t=p.plant();t<p.vanish()+.2f;t+=1/240f){
                double u=FinaleLights.gleam(p,t);
                if(t<p.plant()+.12f||t>=p.vanish())assertTrue(u>=1,"none before it settles or once it goes");
                if(u<1&&last>=1){assertTrue(u<.03,"it starts at the hilt: "+u);runs++;}
                if(u<1&&last<1)assertTrue(u-last>0&&u-last<.03,"runs on smoothly: "+last+" -> "+u);
                last=u;
            }
            assertTrue(runs>=1,"at least once, as it settles");
        }
    }
    @Test void theNetIsOnlyBetweenNeighboursAndEverySwordIsInIt(){
        var c=cast();var r=run();var plans=SwordFinale.plans(c,r,null);
        int[][] links=FinaleLights.links(plans);Set<Integer> in=new HashSet<>();Set<Long> seen=new HashSet<>();
        for(int[] l:links){
            assertTrue(l[0]<l[1]);assertTrue(seen.add(l[0]*1000L+l[1]),"each pair once");
            double d=SwordFinale.stuck(plans[l[0]])[0].distanceTo(SwordFinale.stuck(plans[l[1]])[0]);
            assertTrue(d<7,"neighbours: "+d);in.add(l[0]);in.add(l[1]);
        }
        assertEquals(plans.length,in.size(),"every sword's hilt in it");
        for(var p:plans)assertTrue(FinaleLights.going(p,p.vanish()+.12f)<.01f,"each thread gone with its swords");
        assertTrue(IsosangRules.FINALE_GONE1+SwordFinale.FADE<SwordFinale.LIFE,"gone while it is drawn");
        assertTrue(Ease.hash(1)>=0);
    }
}
