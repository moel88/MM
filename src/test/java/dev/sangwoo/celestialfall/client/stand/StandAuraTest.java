package dev.sangwoo.celestialfall.client.stand;

import java.util.ArrayList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The light rising off Star Platinum (2.52.0): where it leaves the body, how much of it, and smooth at 60 fps. */
class StandAuraTest {
    static final float FRAME=1/60f;

    @Test void aSparkComesInAndGoesOutSmoothly(){
        for(float life:new float[]{.45f,.8f,1f,1.9f})for(float seed:new float[]{0,.37f,.91f}){
            assertEquals(0,StandAura.brightness(0,life,seed),1e-6,"unseen as it leaves the body");
            assertEquals(0,StandAura.brightness(life,life,seed),1e-6,"unseen as it dies");
            float last=0,best=0;
            for(float age=FRAME;age<=life+FRAME;age+=FRAME){
                float a=StandAura.brightness(age,life,seed);
                assertTrue(a>=0&&a<=1,"brightness "+a);
                assertTrue(Math.abs(a-last)<.2f,"a spark of "+life+" s flickers "+Math.abs(a-last)+" at "+age);
                last=a;best=Math.max(best,a);
            }
            assertTrue(best>.6f,"a spark of "+life+" s shines: "+best);
        }
    }

    @Test void sparksLeaveTheBodyWhereItIsAndRise(){
        var aura=new StandAura(Random.create(7));var feet=new Vec3d(10,64,20);
        // facing south (+z): the hair streams north of it, behind
        for(int t=0;t<5;t++)aura.tick(t,feet,feet,0,1,400);
        assertFalse(aura.isEmpty());
        float unit=StarPlatinumRenderer.SCALE/16;boolean behind=false;
        for(var s:aura.sparks){
            assertTrue(Math.abs(s.x()-10)<14*unit&&Math.abs(s.z()-20)<14*unit,"off the body: "+s);
            assertTrue(s.y()>64&&s.y()<64+57*unit,"off the body: "+s);
            behind|=s.z()<20-4*unit;
            assertTrue(s.at(s.life()).y>s.y()+.2,"rises: "+s);
        }
        assertTrue(behind,"some leave the hair behind it");
    }

    @Test void asManyAsTheRateNeverTooMany(){
        var aura=new StandAura(Random.create(3));var feet=Vec3d.ZERO;
        for(int t=0;t<5;t++)aura.tick(t,feet,feet,0,1,StandAura.RATE);
        // its rate for a quarter of a second, before the shortest has died
        assertEquals((int)(StandAura.RATE*5/20),aura.size());
        var none=new StandAura(Random.create(3));for(int t=0;t<40;t++)none.tick(t,feet,feet,0,1,0);
        assertTrue(none.isEmpty());
        var burst=new StandAura(Random.create(3));for(int t=0;t<40;t++)burst.tick(t,feet,feet,0,1,1e5f);
        assertEquals(StandAura.MOST,burst.size());
    }

    @Test void aMovingStandLeavesATrailThatNeverJumps(){
        // walking at 4.3 blocks a second, bursting into its pose: each spark leaves where the body is between the ticks
        var aura=new StandAura(Random.create(11));
        for(int t=0;t<60;t++){
            var from=new Vec3d(t*.215,64,0);var to=new Vec3d((t+1)*.215,64,0);
            var before=new ArrayList<>(aura.sparks);
            aura.tick(t,from,to,-90,1,StandAura.RATE+StandAura.BURST);
            for(var s:before)if(!aura.sparks.contains(s))
                assertTrue((t-s.born())/20f>=s.life(),"gone while it could still be seen: "+s);
            for(var s:aura.sparks)if(s.born()>=t){
                assertTrue(s.born()<t+1,"born in the tick it leaves the body over");
                double along=(s.born()-t)*.215+t*.215;
                assertEquals(along,s.x(),14*StarPlatinumRenderer.SCALE/16,"where the body was as it left");
            }
        }
        for(var s:aura.sparks){
            Vec3d last=null;
            for(float age=0;age<=s.life();age+=FRAME){
                var p=s.at(age);
                if(last!=null)assertTrue(p.distanceTo(last)<.05,"jumps "+p.distanceTo(last)+" at "+age+": "+s);
                last=p;
            }
        }
    }
}
