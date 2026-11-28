package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gold Experience's overdrive punch as it is drawn (2.148.0), and the soul it knocks out of a body: the punch smooth at
 * 60 fps thrown to its end and stopped short at any moment, wound up before it is thrown and landing as the server's
 * blow does, fastest as it lands; the soul walking twice as fast as a body, held by what is in its way.
 */
class GoldPunchTest {
    static final float FRAME=1/60f;
    private static List<String> bones()throws Exception{
        try(var in=GoldPunchTest.class.getResourceAsStream("/assets/celestialfall/geo/entity/gold_experience.geo.json")){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    static GoldPunch.Punch punch(float aim){return new GoldPunch.Punch(0,0,0,30,aim,5);}
    /** Runs a punch frame by frame from the stand at rest beside its user; no bone, arm or place jumps. */
    private void watch(GoldPunch.Punch p,String what)throws Exception{
        var names=bones();var poses=new GoldExperiencePoses();var look=new GoldExperienceLook();var shape=new GoldRush.Shape();
        float[] scratch=new float[NAMES.size()],q=new float[6];float[][] last=new float[names.size()][];
        float arm=1,engage=0;boolean shown=false;
        for(float t=0;t<=GoldPunch.LENGTH+GoldPunch.LINGER+GoldPunch.RETURN+.3f;t+=FRAME){
            float delta=GoldRushTest.clock(t);
            look.only(IDLE);look.armL=look.armR=1;
            boolean on=GoldPunch.pose(p,delta,look,shape,scratch);shown|=on;
            if(!on)shape.engage=0;
            float sum=0;for(float w:look.weights){assertTrue(w>=-1e-5,what+" at "+t);sum+=w;}
            assertEquals(1,sum,1e-4,"weights at "+t+" ("+what+")");
            for(int b=0;b<names.size();b++){
                poses.blend(names.get(b),look.weights,t,q);
                if(last[b]!=null)assertTrue(GoldRushTest.turn(last[b],q)<45,names.get(b)+" turns "+GoldRushTest.turn(last[b],q)+" degrees in a frame at "+t+" ("+what+")");
                last[b]=q.clone();
            }
            assertTrue(Math.abs(look.armR-arm)<.05,"its arm jumps in size at "+t+" ("+what+")");
            assertTrue(Math.abs(shape.engage-engage)<.25,"it jumps round at "+t+" ("+what+")");
            arm=look.armR;engage=shape.engage;
        }
        assertTrue(shown,what);
        assertEquals(0,engage,.02,"back at its user's shoulder ("+what+")");assertEquals(1,arm,.01);
        assertEquals(1,look.weights[IDLE],.02,"at rest again ("+what+")");
    }
    @Test void thrownToItsEndItMovesWithoutAJumpAt60fps()throws Exception{
        watch(punch(Float.NaN),"thrown");
        watch(punch(2.4f),"thrown at someone");
    }
    @Test void stoppedShortAtAnyMomentItTurnsBackToItsGuardWithoutAJump()throws Exception{
        for(float at:new float[]{0,.1f,.31f,.44f,.5f,.58f}){
            var p=punch(Float.NaN);p.stopped=at;
            watch(p,"stopped at "+at);
            float[] w=new float[NAMES.size()];GoldPunch.stances(at+GoldPunch.CUT,at,w);
            assertEquals(1,w[GUARD],1e-4,"in its guard "+GoldPunch.CUT+" s after it was stopped at "+at);
        }
    }
    @Test void itWindsUpHoldsAndLandsAsTheServersBlowDoes(){
        assertEquals(GoldExperienceRules.OVER_CONTACT/20f,GoldPunch.CONTACT,1e-6,"its fist lands on the server's tick");
        assertEquals(GoldExperienceRules.OVER_LENGTH/20f,GoldPunch.LENGTH,1e-6);
        float[] w=new float[NAMES.size()];
        GoldPunch.stances(0,Float.NaN,w);assertEquals(1,w[GUARD],1e-5,"from its guard");
        w=new float[NAMES.size()];GoldPunch.stances(GoldPunch.WOUND,Float.NaN,w);assertEquals(1,w[OVER_WIND],1e-5,"wound up");
        w=new float[NAMES.size()];GoldPunch.stances(GoldPunch.LOAD,Float.NaN,w);assertEquals(1,w[OVER_WIND],1e-5,"held there, loaded");
        w=new float[NAMES.size()];GoldPunch.stances(GoldPunch.CONTACT,Float.NaN,w);assertEquals(1,w[OVER_PUNCH],1e-5,"at full stretch as it lands");
        w=new float[NAMES.size()];GoldPunch.stances(GoldPunch.HELD,Float.NaN,w);assertEquals(1,w[OVER_PUNCH],1e-5,"held out");
        w=new float[NAMES.size()];GoldPunch.stances(GoldPunch.GUARDED,Float.NaN,w);assertEquals(1,w[GUARD],1e-5,"back in its guard");
        // faster and faster: fastest as it lands
        float last=0;
        for(float t=GoldPunch.LOAD+FRAME;t<=GoldPunch.CONTACT+1e-4;t+=FRAME){
            float step=GoldPunch.strike(t)-GoldPunch.strike(t-FRAME);
            assertTrue(step>=last-1e-4,"it slows at "+t);last=step;
        }
        // its light gathers as it winds up and is gone as the fist is thrown
        var p=punch(Float.NaN);
        assertEquals(0,GoldPunch.charge(p,0),1e-5);assertTrue(GoldPunch.charge(p,GoldPunch.LOAD)>.95f);assertEquals(0,GoldPunch.charge(p,GoldPunch.CONTACT),1e-5);
    }
    @Test void itsFistGoesFromBehindItsHipToFullStretchAndMeetsWhoeverIsInItsWay(){
        var p=punch(Float.NaN);
        float[] wound=GoldPunch.fist(p,GoldPunch.LOAD),out=GoldPunch.fist(p,GoldPunch.CONTACT);
        assertTrue(wound[0]<.1f,"drawn back behind its shoulder: "+wound[0]);
        assertTrue(out[0]>1.3f,"out at full stretch: "+out[0]);
        assertEquals(GoldPunch.fistReach(),out[0],1e-4);
        assertTrue(out[2]>1.0f&&out[2]<1.7f,"at a body's chest: "+out[2]);
        // it stands so that its fist goes a little into the first one in its way
        for(float aim:new float[]{2.7f,3.1f,3.5f}){
            float place=GoldPunch.place(punch(aim));
            assertEquals(aim+GoldPunch.BITE,place+GoldPunch.fistReach(),1e-4,"its fist into them at "+aim);
        }
        assertEquals(GoldPunch.NEAR,GoldPunch.place(punch(.4f)),1e-6,"never on top of its user");
        assertEquals(GoldPunch.FAR,GoldPunch.place(punch(4.4f)),1e-6,"never far off");
        assertEquals(GoldPunch.AHEAD,GoldPunch.place(punch(Float.NaN)),1e-6);
    }
    /** Flat ground at y=0 and a wall whose near face is at x=5. */
    static Vec3d ground(Vec3d feet,Vec3d want){
        double x=want.x,y=want.y,z=want.z;
        if(feet.y+y<0)y=-feet.y;
        if(feet.x+x>5-SoulClient.HALF)x=Math.max(0,5-SoulClient.HALF-feet.x);
        return new Vec3d(x,y,z);
    }
    @Test void itsShoutsSecondSyllableComesAsTheFistLands(){
        // the user's recording "mu-DA!", cut with its second syllable DA_AT into the file (tools/stand/cut_gold_sounds.py)
        assertEquals(GoldPunch.CONTACT,GoldPunch.SOUND_AT+GoldPunch.DA_AT,1e-6);
        assertEquals(0,Math.round(GoldPunch.SOUND_AT*20)-GoldPunch.SOUND_AT*20,1e-4,"on a tick");
        assertTrue(GoldPunch.SOUND_AT>GoldPunch.WOUND,"as it is thrown, not as it winds up");
        assertEquals("gold_experience_overdrive",GoldPunch.SOUND[0].sound());
    }
    @Test void aSoulWalksTwiceAsFastAsABody(){
        assertEquals(2,GoldExperienceRules.SOUL_SPEED,1e-6);
        // a body's steady walk is 0.2159 blocks a tick (4.317 a second)
        assertEquals(2*.2159,SoulWalk.pace(false),.002);
        var at=new SoulWalk.Step(new Vec3d(0,0,0),Vec3d.ZERO,true);double last=0;
        for(int i=0;i<60;i++){var next=SoulWalk.step(at,1,0,false,false,0,GoldPunchTest::ground);last=next.feet().z-at.feet().z;at=next;assertTrue(at.ground(),"on the ground at "+i);}
        assertEquals(SoulWalk.pace(false),last,.002,"its steady pace, straight ahead (south at yaw 0)");
        assertEquals(0,at.feet().x,1e-6);assertEquals(0,at.feet().y,1e-6);
        // running, as much faster again as a body runs
        at=new SoulWalk.Step(new Vec3d(0,0,0),Vec3d.ZERO,true);
        for(int i=0;i<60;i++){var next=SoulWalk.step(at,1,0,false,true,0,GoldPunchTest::ground);last=next.feet().z-at.feet().z;at=next;}
        assertEquals(SoulWalk.pace(true),last,.003);assertEquals(1.3,SoulWalk.pace(true)/SoulWalk.pace(false),1e-6);
        // the keys let go, it comes to rest
        for(int i=0;i<30;i++)at=SoulWalk.step(at,0,0,false,false,0,GoldPunchTest::ground);
        assertTrue(at.velocity().horizontalLength()<1e-4,"at rest");
    }
    @Test void aSoulSidestepsJumpsFallsAndIsHeldByAWall(){
        // left of one facing south (yaw 0) is east (+x)
        var at=SoulWalk.step(new SoulWalk.Step(Vec3d.ZERO,Vec3d.ZERO,true),0,1,false,false,0,GoldPunchTest::ground);
        assertTrue(at.feet().x>0&&Math.abs(at.feet().z)<1e-9,"left is +x at yaw 0");
        // a jump: up about a block and a quarter, and down again
        at=new SoulWalk.Step(Vec3d.ZERO,Vec3d.ZERO,true);double top=0;int air=0;
        at=SoulWalk.step(at,0,0,true,false,0,GoldPunchTest::ground);
        while(!at.ground()&&air<40){top=Math.max(top,at.feet().y);at=SoulWalk.step(at,0,0,false,false,0,GoldPunchTest::ground);air++;}
        assertEquals(1.25,top,.05,"a body's jump");assertTrue(air>8&&air<16,"in the air "+air+" ticks");assertEquals(0,at.feet().y,1e-9);
        // a wall holds it
        at=new SoulWalk.Step(Vec3d.ZERO,Vec3d.ZERO,true);
        for(int i=0;i<60;i++)at=SoulWalk.step(at,1,0,false,false,-90,GoldPunchTest::ground);
        assertEquals(5-SoulClient.HALF,at.feet().x,1e-6,"up against the wall, not through it");
    }
}
