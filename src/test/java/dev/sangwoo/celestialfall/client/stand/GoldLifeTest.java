package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gold Experience's T as it is drawn (2.151.0): its punch smooth at 60 fps (stopped short at any moment too), on the
 * server's ticks; the stand playing no part in the frog (2.151.1), the item turning of itself as the light gathers into
 * it; the item becoming the frog and the frog the item again without a blank or a jump; the part flying off turning into
 * the fly without a blank; one knocked down falling on their back and getting up without a jump.
 */
class GoldLifeTest {
    static final float FRAME=1/60f;
    /** The bones of a skin's skeleton (2.156.0: each skin its own, GoldSkin). */
    private static List<String> bones(GoldSkin skin)throws Exception{
        try(var in=GoldLifeTest.class.getResourceAsStream(skin.geo())){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    static GoldLife.Life frog(){return new GoldLife.Life(true,0,0,0,30,Float.NaN,5,null);}
    static GoldLife.Life punch(float aim){return new GoldLife.Life(false,0,0,0,30,aim,5,null);}
    /** Runs it frame by frame from the stand at rest beside its user; no bone, arm or place jumps, and it is at rest again after. */
    private void watch(GoldLife.Life l,float until,String what)throws Exception{
        // (2.156.0) in every skin, each with its own skeleton, stances and fists
        for(var skin:GoldSkin.values()){l.skin=skin;watch(skin,l,until,what);}
        l.skin=GoldSkin.ORIGINAL;
    }
    private void watch(GoldSkin skin,GoldLife.Life l,float until,String what)throws Exception{
        what=skin+": "+what;
        var names=bones(skin);var poses=new GoldExperiencePoses(skin);var look=new GoldExperienceLook();var shape=new GoldRush.Shape();
        float[] scratch=new float[NAMES.size()],q=new float[6];float[][] last=new float[names.size()][];
        float armL=1,armR=1,engage=0,side=Float.NaN,ahead=Float.NaN,raise=Float.NaN;boolean shown=false;
        for(float t=0;t<=until;t+=FRAME){
            float delta=GoldRushTest.clock(t);
            look.only(IDLE);look.armL=look.armR=1;
            boolean on=GoldLife.pose(l,delta,look,shape,scratch);shown|=on;
            if(!on)shape.engage=0;
            float sum=0;for(float w:look.weights){assertTrue(w>=-1e-5,what+" at "+t);sum+=w;}
            assertEquals(1,sum,1e-4,"weights at "+t+" ("+what+")");
            for(int b=0;b<names.size();b++){
                poses.blend(names.get(b),look.weights,t,q);
                if(last[b]!=null)assertTrue(GoldRushTest.turn(last[b],q)<45,names.get(b)+" turns "+GoldRushTest.turn(last[b],q)+" degrees in a frame at "+t+" ("+what+")");
                last[b]=q.clone();
            }
            assertTrue(Math.abs(look.armR-armR)<.05&&Math.abs(look.armL-armL)<.05,"an arm jumps in size at "+t+" ("+what+")");
            assertTrue(Math.abs(shape.engage-engage)<.25,"it jumps round at "+t+" ("+what+")");
            if(on&&!Float.isNaN(side)){
                assertEquals(side,shape.side,1e-3,"where it stands is fixed by its user's place ("+what+")");
                assertEquals(ahead,shape.ahead,1e-3);assertEquals(raise,shape.raise,1e-3);
            }
            if(on){side=shape.side;ahead=shape.ahead;raise=shape.raise;}
            armL=look.armL;armR=look.armR;engage=shape.engage;
        }
        assertTrue(shown,what);
        assertEquals(0,engage,.02,"back at its user's shoulder ("+what+")");assertEquals(1,armR,.01);assertEquals(1,armL,.01);
        assertEquals(1,look.weights[IDLE],.02,"at rest again ("+what+")");
    }
    @Test void itsPunchIsSmoothAt60fps()throws Exception{
        float until=GoldLife.PUNCH_GUARDED+GoldLife.LINGER+GoldLife.RETURN+.3f;
        watch(punch(Float.NaN),until,"a punch at no one");
        watch(punch(1.6f),until,"a punch at someone");
        for(float at:new float[]{0,.05f,.15f,.25f,.35f}){
            var l=punch(1.6f);l.stopped=at;
            watch(l,until,"stopped at "+at);
            float[] w=new float[NAMES.size()];GoldLife.stances(l,at+GoldLife.CUT,w);
            assertEquals(1,w[GUARD],1e-4,"in its guard "+GoldLife.CUT+" s after it was stopped at "+at);
        }
    }
    @Test void theStandPlaysNoPartInTheFrogTheItemTurnsOfItself(){
        // (2.151.1, user request: "아이템 개구리로 바꿀때는 스탠드가 아이템 안 때려도 돼 손에 들고 있는게 자동으로 개구리로 변하게")
        var l=frog();var look=new GoldExperienceLook();var shape=new GoldRush.Shape();float[] scratch=new float[NAMES.size()];
        for(float t=0;t<=GoldLife.over(l)+.5f;t+=FRAME){
            look.only(IDLE);
            assertFalse(GoldLife.pose(l,GoldRushTest.clock(t),look,shape,scratch),"the stand comes for the item at "+t);
            assertEquals(1,look.weights[IDLE],1e-6,"the stand's look untouched at "+t);
            float[] w=new float[NAMES.size()];GoldLife.stances(l,t,w);
            for(float x:w)assertEquals(0,x,1e-9,"a stance of the stand's at "+t);
            assertEquals(0,GoldLife.glow(l,t),1e-9,"the stand's fist glows at "+t);
        }
        // the light gathers into the item from as the hand comes up, faster and faster, full as it turns, then is gone
        assertEquals(GoldExperienceRules.FROG_BORN/20f,GoldLife.BORN,1e-6,"it turns as the server's frog is born");
        assertTrue(GoldLife.GATHER_FROM<GoldLife.RAISED,"it starts gathering as the hand comes up");
        assertEquals(0,GoldLife.gathered(l,GoldLife.GATHER_FROM),1e-6);
        assertEquals(1,GoldLife.gathered(l,GoldLife.BORN),1e-6,"full as it turns");
        assertEquals(0,GoldLife.gathered(l,GoldLife.BORN+GoldLife.GATHER_GONE),1e-6,"gone into its flash");
        float last=0,step=0;
        for(float t=GoldLife.GATHER_FROM;t<=GoldLife.BORN+1e-4;t+=FRAME){
            float g=GoldLife.gathered(l,t);assertTrue(g-last>=step-1e-4,"it slows at "+t);step=g-last;last=g;
        }
        for(float t=0,prev=0;t<=GoldLife.BORN+GoldLife.GATHER_GONE+.05f;t+=FRAME){float g=GoldLife.gathered(l,t);assertTrue(Math.abs(g-prev)<.3,"a jump at "+t);prev=g;}
        // ended before it turned, no light gathers after
        var e=frog();e.early=.2f;assertEquals(0,GoldLife.gathered(e,.25f),1e-6);
        // the punch's light stays its own
        assertTrue(GoldLife.glow(punch(Float.NaN),.2f)>0);assertEquals(0,GoldLife.gathered(punch(Float.NaN),.2f),1e-6);
    }
    @Test void thePunchLandsOnTheServersTick(){
        assertEquals(GoldExperienceRules.FLY_CONTACT/20f,GoldLife.CONTACT,1e-6,"the punch lands on the server's tick");
        float[] w;
        // the punch: wound up, then faster and faster, fastest as it lands
        var p=punch(Float.NaN);
        w=new float[NAMES.size()];GoldLife.stances(p,GoldLife.CONTACT,w);assertEquals(1,w[OVER_PUNCH],1e-5,"at full stretch as it lands");
        float last=0;
        for(float t=GoldLife.LOAD+FRAME;t<=GoldLife.CONTACT+1e-4;t+=FRAME){
            float step=GoldLife.strike(t)-GoldLife.strike(t-FRAME);
            assertTrue(step>=last-1e-4,"it slows at "+t);last=step;
        }
        // its fist BITE into the one it lands on, wherever they stand (within reach; 2.156.0: in every skin)
        for(var skin:GoldSkin.values()){
            float reach=GoldRush.grown(skin,OVER_PUNCH,1,GoldLife.ARMS)[0];
            for(float aim:new float[]{GoldLife.NEAR+reach-GoldLife.BITE+.05f,2.8f,skin.far+reach-GoldLife.BITE-.05f}){
                var l=punch(aim);l.skin=skin;
                assertEquals(aim+GoldLife.BITE,GoldLife.place(l)+reach,1e-4,skin+": its fist into them at "+aim);
            }
            var near=punch(.2f);near.skin=skin;var far=punch(9);far.skin=skin;
            assertEquals(GoldLife.NEAR,GoldLife.place(near),1e-6,"never on top of its user");
            assertEquals(skin.far,GoldLife.place(far),1e-6,"nor far from them");
        }
    }
    @Test void theItemBecomesTheFrogAndTheFrogTheItemWithoutABlankOrAJump(){
        var l=frog();
        assertEquals(1,GoldLife.itemShown(l,0),1e-6,"the item in the hand held up");
        assertEquals(0,GoldLife.frogShown(l,GoldLife.BORN),1e-6,"no frog before it turns");
        assertEquals(1,GoldLife.frogShown(l,GoldLife.MORPHED),1e-6,"the frog, whole");
        assertEquals(0,GoldLife.itemShown(l,GoldLife.MORPHED),1e-6,"the item gone into it");
        assertEquals(0,GoldLife.seat(l,GoldLife.BORN)[0],1e-6,"born in the hand");
        assertEquals(1,GoldLife.seat(l,GoldLife.HOP_TO+.01f)[0],1e-6,"on the shoulder");
        assertEquals(1,GoldLife.seat(l,5)[0],1e-6,"sits there");
        assertEquals(0,GoldLife.seat(l,GoldLife.HOPPED_BACK+.01f)[0],1e-6,"back in the hand at the end");
        assertEquals(0,GoldLife.frogShown(l,GoldLife.END),1e-3,"the frog gone into the item as the server gives it back");
        assertEquals(1,GoldLife.itemShown(l,GoldLife.END),1e-3,"the item in the hand again");
        assertEquals(1,GoldLife.raised(l,GoldLife.BORN),1e-6,"the hand up as it turns");
        assertEquals(1,GoldLife.raised(l,GoldLife.END),1e-6,"and as the item comes back");
        assertEquals(0,GoldLife.raised(l,5),1e-6,"down in between");
        float item=1,frog=0,where=0,raised=0;
        for(float t=0;t<=GoldLife.LOWERED_AGAIN+.1f;t+=FRAME){
            float i=GoldLife.itemShown(l,t),f=GoldLife.frogShown(l,t),w=GoldLife.seat(l,t)[0],r=GoldLife.raised(l,t);
            // (the frog pops out of the item in a tenth of a second, a little too big for a moment: seven frames)
            // (as the hand is down again the item drawn in it gives way to the one the server gave back: a swap, not a jump)
            boolean swap=t>GoldLife.LOWERED_AGAIN-.06f;
            assertTrue((swap||Math.abs(i-item)<.3)&&Math.abs(f-frog)<.3,"a jump at "+t+": item "+item+" -> "+i+", frog "+frog+" -> "+f);
            assertTrue(Math.abs(w-where)<.15&&Math.abs(r-raised)<.15,"a jump at "+t);
            if(t>GoldLife.RAISED&&t<GoldLife.LOWERED_AGAIN-.05f)assertTrue(Math.max(i,f)>.3f,"neither item nor frog at "+t);
            item=i;frog=f;where=w;raised=r;
        }
        // ended early it bursts and is gone
        var e=frog();e.early=3;
        assertEquals(1,GoldLife.frogShown(e,3),1e-6);assertEquals(0,GoldLife.frogShown(e,3+GoldLife.BURST),1e-6);
        assertEquals(3+GoldLife.BURST,GoldLife.over(e),1e-6);
    }
    @Test void thePartFlyingOffTurnsIntoTheFlyWithoutABlank(){
        float part=1,fly=0;
        for(double age=0;age<GoldExperienceRules.FLY_FORMS;age+=1/3.0){
            float p=GoldLife.partShown(age/GoldExperienceRules.FLY_FORMS),f=GoldLife.flyShown(age);
            assertTrue(Math.max(p,f)>.3f,"nothing at "+age);
            assertTrue(Math.abs(p-part)<.25&&Math.abs(f-fly)<.25,"a jump at "+age);
            part=p;fly=f;
        }
        assertEquals(1,GoldLife.flyShown(GoldExperienceRules.FLY_FORMS),1e-6);
        assertEquals(1,GoldLife.flyShown(GoldExperienceRules.FLY_FORMS+100),1e-6);
        assertEquals(0,GoldLife.flyShown(GoldExperienceRules.FLY_GONE),1e-6,"gone into the part's place");
    }
    @Test void knockedDownTheyFallOnTheirBackAndGetUpWithoutAJump(){
        assertEquals(GoldExperienceRules.FLY_DOWN/20f,GoldLife.DOWN,1e-6,"down a second");
        assertEquals(0,GoldLife.fallen(0),1e-6);assertEquals(1,GoldLife.fallen(GoldLife.FALLEN),1e-6,"on their back");
        assertEquals(1,GoldLife.fallen(GoldLife.GETS_UP),1e-6);assertEquals(0,GoldLife.fallen(GoldLife.DOWN),1e-6,"up again as it is over");
        float last=0;MotionPose before=null;
        for(float t=0;t<=GoldLife.DOWN;t+=FRAME){
            float k=GoldLife.fallen(t);assertTrue(Math.abs(k-last)<.15,"it tips over in a jump at "+t);last=k;
            var pose=GoldLife.DOWN_BODY.frame(t).joints();
            if(before!=null)for(int j=0;j<pose.joint.length;j++)for(int a=0;a<3;a++)
                assertTrue(Math.abs(pose.joint[j][a]-before.joint[j][a])<30,"joint "+j+" jumps at "+t);
            before=pose.copy();
        }
    }
}
