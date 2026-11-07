package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T's moonlight and starlight (2.100.0, user request: "이번에는 T스킬도 엄청 예쁘게 꾸며줘"): the stars and sparks on its
 * slashes never at their ends nor where they cross before the foe (2.96.3: gathered there they read as one star), the
 * sparks born as each slash's head passes, the swords' stardust shed as they fly, the stars on its dash's light off the
 * foe's end, the petals shed along her way falling, and all of it gone before T stops being drawn.
 */
class LungeLightsTest {
    @Test void theSlashesStarsAreNeverAtTheirEndsNorWhereTheyCross(){
        for(int j=0;j<IsosangRules.LUNGE_SLASHES;j++)for(double u:LungeLights.slashStarsU(j)){
            assertTrue(u>=.1-1e-9&&u<=.9+1e-9,"off its ends: "+u);
            assertTrue(u<=.4+1e-9||u>=.6-1e-9,"off its middle: "+u);
        }
        // and spread over both halves
        double[] u=LungeLights.slashStarsU(0);int low=0;for(double v:u)if(v<.5)low++;
        assertTrue(low>=2&&u.length-low>=2);
    }
    @Test void theSlashesSparksSpringOffThemAsTheyAreDrawnNeverAtTheirEndsNorWhereTheyCross(){
        for(int j=0;j<IsosangRules.LUNGE_SLASHES;j++){
            int low=0;
            for(int i=0;i<LungeLights.SLASH_SPARKS;i++){
                double[] s=LungeLights.slashSpark(j,i);
                assertTrue(s[0]>=.08-1e-9&&s[0]<=.92+1e-9,"off its ends: "+s[0]);
                assertTrue(s[0]<=.38+1e-9||s[0]>=.62-1e-9,"off its middle: "+s[0]);
                assertTrue(Math.abs(Math.abs(s[1])-1)<1e-9,"off one edge or the other");
                // as its head passes there: SwordLunge.drawn's ease reaches it then
                float at=IsosangRules.lungeSlash(j),born=at+(float)s[2];
                assertEquals(s[0],SwordLunge.drawn(j,born),.01,"born as its head passes");
                assertTrue(born+LungeLights.SPARK_LIFE1<=SwordLunge.LIFE,"gone before T stops being drawn");
                if(s[0]<.5)low++;
            }
            assertTrue(low>=3&&LungeLights.SLASH_SPARKS-low>=3,"on both halves");
        }
        // they spring out across it and drift down, slowing
        var b=new SwordLunge.Blade(new Vec3d(0,66,0),new Vec3d(6,64,2),SwordLunge.WIDTH);
        Vec3d side=LungeLights.across(b,new Vec3d(-1,0,6));
        assertEquals(0,side.dotProduct(b.way()),1e-9,"across the blade");
        double[] s=LungeLights.slashSpark(0,0);
        Vec3d p0=LungeLights.sparkAt(b,side,s,41,0),p1=LungeLights.sparkAt(b,side,s,41,.2f),p2=LungeLights.sparkAt(b,side,s,41,.55f);
        assertTrue(p0.distanceTo(b.point(s[0]))<=b.width()*.5+1e-9,"on its edge at first");
        assertTrue(p1.subtract(p0).dotProduct(side)*s[1]>.1,"out across it");
        assertTrue(p2.distanceTo(p1)<p1.distanceTo(p0)+.2,"slowing");
        assertTrue(p2.y<LungeLights.sparkAt(b,side,s,41,.35f).y,"drifting down at last");
    }
    @Test void theSwordsStardustIsShedAsTheyFlyAndGoneBeforeTStopsBeingDrawn(){
        for(int i=0;i<IsosangRules.VOLLEY_SWORDS;i++)for(int j=0;j<LungeLights.SWORD_DUST;j++){
            float born=LungeLights.swordDustBorn(i,j);
            assertTrue(born>=IsosangRules.volleyThrown(IsosangRules.LUNGE,i)&&born<IsosangRules.volleyArrives(IsosangRules.LUNGE,i),"as it flies");
            assertTrue(born+LungeLights.SWORD_DUST_LIFE<=SwordLunge.LIFE,"gone before T stops being drawn");
        }
    }
    @Test void theDashStarsRideItsBodyOffTheFoesEnd(){
        for(int i=0;i<LungeLights.DASH_STARS;i++){
            double[] s=LungeLights.dashStar(i);
            assertTrue(s[0]>=LungeLights.DASH_FROM&&s[0]<=LungeLights.DASH_TO,"along it, off the foe: "+s[0]);
            assertTrue(Math.abs(s[1])<=.72+1e-9,"inside its width: "+s[1]);
        }
        for(int i=0;i<LungeLights.RIM_STARS;i++){double[] s=LungeLights.rimStar(i);assertTrue(s[0]>=.15&&s[0]<=.8+1e-9);}
    }
    @Test void thePetalsAreShedAlongHerWayAndFall(){
        Vec3d from=new Vec3d(0,65,0),to=new Vec3d(12,64,3),side=new Vec3d(-.24,0,.97).normalize();
        for(int i=0;i<LungeLights.PETALS;i++){
            double[] p0=LungeLights.petal(from,to,side,i,0),p1=LungeLights.petal(from,to,side,i,1);
            Vec3d a=new Vec3d(p0[0],p0[1],p0[2]);
            // shed near her way: within its span and a little to its sides
            Vec3d d=to.subtract(from);double f=a.subtract(from).dotProduct(d)/d.lengthSquared();
            assertTrue(f>-.05&&f<1.05,"along her way: "+f);
            assertTrue(p1[1]<p0[1]-.15,"falling: "+p0[1]+" to "+p1[1]);
            float shed=LungeLights.shed(i);
            assertTrue(shed>=SwordLunge.light()-1e-6&&shed<=SwordLunge.light()+SwordLunge.RACE+1e-6,"as her dash's light passes");
            assertTrue(shed+LungeLights.PETAL_LIFE1<=SwordLunge.LIFE,"gone before T stops being drawn");
        }
    }
    @Test void allOfItIsGoneBeforeTStopsBeingDrawn(){
        // the slashes' dust, the last shed as the last slash is eaten
        for(int j=0;j<IsosangRules.LUNGE_SLASHES;j++){
            float last=IsosangRules.lungeSlash(j)+SwordLunge.EAT1;
            assertTrue(last+LungeLights.dustLife(last)<=SwordLunge.LIFE+1e-4);
        }
        float hop=IsosangRules.LUNGE_HOP/20f;
        assertTrue(hop+SwordStep.flight()+LungeLights.DUST_LIFE<=SwordLunge.LIFE,"the stardust of her flight on");
        assertTrue(IsosangRules.blowAt(IsosangRules.LUNGE,0)/20f+.9f<=SwordLunge.LIFE,"the stars off the foe");
    }
}
