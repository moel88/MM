package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
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
 * Gold Experience's healing as it is drawn (2.149.0): one punch with the fist left in, the tap's and the hold's smooth
 * at 60 fps to their end and stopped or let go at any moment; the fist in as the server heals; held, in until it is
 * let go; the leaves winding up about the healed body.
 */
class GoldHealTest {
    static final float FRAME=1/60f;
    private static List<String> bones()throws Exception{
        try(var in=GoldHealTest.class.getResourceAsStream("/assets/celestialfall/geo/entity/gold_experience.geo.json")){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    static GoldHeal.Heal tap(float aim){return new GoldHeal.Heal(false,0,0,0,30,aim,5);}
    static GoldHeal.Heal hold(){return new GoldHeal.Heal(true,0,0,0,30,Float.NaN,5);}
    /** Runs a healing frame by frame from the stand at rest beside its user; no bone, arm or place jumps, and it is at rest again after. */
    private void watch(GoldHeal.Heal h,float until,String what)throws Exception{
        var names=bones();var poses=new GoldExperiencePoses();var look=new GoldExperienceLook();var shape=new GoldRush.Shape();
        float[] scratch=new float[NAMES.size()],q=new float[6];float[][] last=new float[names.size()][];
        float armL=1,armR=1,engage=0;boolean shown=false;
        for(float t=0;t<=until;t+=FRAME){
            float delta=GoldRushTest.clock(t);
            look.only(IDLE);look.armL=look.armR=1;
            boolean on=GoldHeal.pose(h,delta,look,shape,scratch,30);shown|=on;
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
            armL=look.armL;armR=look.armR;engage=shape.engage;
        }
        assertTrue(shown,what);
        assertEquals(0,engage,.02,"back at its user's shoulder ("+what+")");assertEquals(1,armR,.01);assertEquals(1,armL,.01);
        assertEquals(1,look.weights[IDLE],.02,"at rest again ("+what+")");
    }
    @Test void tappedTheFistGoesInAndOutWithoutAJumpAt60fps()throws Exception{
        float until=GoldHeal.LENGTH+GoldHeal.LINGER+GoldHeal.RETURN+.3f;
        watch(tap(Float.NaN),until,"tapped");
        watch(tap(2.4f),until,"tapped at someone");
        for(float at:new float[]{0,.08f,.2f,.28f}){
            var h=tap(1.5f);h.stopped=at;
            watch(h,until,"stopped at "+at);
            float[] w=new float[NAMES.size()];GoldHeal.stances(h,at+GoldHeal.CUT,w);
            assertEquals(1,w[GUARD],1e-4,"in its guard "+GoldHeal.CUT+" s after it was stopped at "+at);
        }
    }
    @Test void itIsOnePunchDrivenInAsTheServerHeals(){
        assertEquals(GoldExperienceRules.MEND_CONTACT/20f,GoldHeal.TOUCH,1e-6,"the fist is in on the server's tick");
        assertEquals(GoldExperienceRules.SELF_FIRST/20f,GoldHeal.TOUCH,1e-6,"and in its user as their first healing comes");
        assertEquals(GoldExperienceRules.MEND_LENGTH/20f,GoldHeal.LENGTH,1e-6);
        var h=tap(Float.NaN);float[] w=new float[NAMES.size()];
        GoldHeal.stances(h,0,w);assertEquals(1,w[GUARD],1e-5,"from its guard");
        w=new float[NAMES.size()];GoldHeal.stances(h,GoldHeal.TOUCH,w);assertEquals(1,w[MEND],1e-5,"driven in");
        w=new float[NAMES.size()];GoldHeal.stances(h,GoldHeal.STAYS,w);assertEquals(1,w[MEND],1e-5,"left in a moment");
        w=new float[NAMES.size()];GoldHeal.stances(h,GoldHeal.GUARDED,w);assertEquals(1,w[GUARD],1e-5,"back in its guard");
        // a punch: faster and faster, fastest as it goes in
        float last=0;
        for(float t=GoldHeal.OUT_FROM+FRAME;t<=GoldHeal.TOUCH+1e-4;t+=FRAME){
            float step=GoldHeal.reach(t)-GoldHeal.reach(t-FRAME);
            assertTrue(step>=last-1e-4,"it slows at "+t);last=step;
        }
        // the fist is out at a body's chest, and it stands so that the fist is well into the first one in its way
        float[] out=GoldHeal.fist(h,GoldHeal.TOUCH);
        assertTrue(out[0]>1.2f,"out: "+out[0]);assertTrue(out[2]>1.0f&&out[2]<1.7f,"at a body's chest: "+out[2]);
        assertEquals(GoldHeal.fistReach(),out[0],1e-4);
        for(float aim:new float[]{2.0f,2.8f,3.6f}){
            float place=GoldHeal.place(tap(aim));
            assertEquals(aim+GoldHeal.PRESS,place+GoldHeal.fistReach(),1e-4,"its fist into them at "+aim);
        }
        assertTrue(GoldHeal.PRESS>=.15f,"driven in, not laid on");
        assertEquals(GoldHeal.NEAR,GoldHeal.place(tap(.3f)),1e-6,"never on top of its user");
        assertEquals(GoldHeal.AHEAD,GoldHeal.place(tap(Float.NaN)),1e-6);
    }
    @Test void heldTheFistStaysInItsUserWithoutAJumpHoweverLongItIsHeld()throws Exception{
        float after=GoldHeal.CUT+GoldHeal.LINGER+GoldHeal.RETURN+.4f;
        watch(hold(),GoldExperienceRules.SELF_LENGTH/20f+after,"held to its end");
        for(float at:new float[]{.02f,.1f,.2f,.33f,1.07f,2.5f,4.99f}){
            var h=hold();h.ended=at;
            watch(h,at+after,"let go at "+at);
        }
    }
    @Test void heldItIsOneFistLeftInNotABarrage(){
        var h=hold();
        // in from the first healing to the last, never drawn back between
        for(float t=GoldHeal.TOUCH;t<=GoldExperienceRules.SELF_LAST/20f+1e-4;t+=FRAME){
            float[] w=new float[NAMES.size()];GoldHeal.stances(h,t,w);
            assertEquals(1,w[MEND],1e-4,"the fist is in at "+t);
        }
        for(int s:GoldRush.LEFT){float[] w=new float[NAMES.size()];GoldHeal.stances(h,1f,w);assertEquals(0,w[s],1e-6,"no fist of the rush");}
        // let go, it is drawn out to the guard
        h.ended=2f;float[] w=new float[NAMES.size()];GoldHeal.stances(h,2f+GoldHeal.CUT,w);assertEquals(1,w[GUARD],1e-4);
        // it stands at its user's front right with its knuckles well inside their body, pressing a little with each healing
        assertTrue(GoldHeal.SIDE>20&&GoldHeal.SIDE<80,"at their front right");
        assertEquals(GoldHeal.DEPTH,GoldHeal.selfPlace()-GoldHeal.fistReach(),1e-5);assertTrue(GoldHeal.DEPTH<.13f,"inside a player's body (half as deep as .125)");
        assertTrue(GoldHeal.selfPlace()>StandPresence.GAP,"never nearer than a stand keeps");
        var held=hold();float every=GoldExperienceRules.SELF_EVERY/20f,most=0,prev=0;
        for(float t=0;t<1.2f;t+=FRAME/4){
            float p=GoldHeal.press(held,t);assertTrue(p>=0&&p<=GoldHeal.PUSH+1e-6);most=Math.max(most,p);
            assertTrue(Math.abs(p-prev)<GoldHeal.PUSH*.25f,"its press jumps at "+t);prev=p;
        }
        assertTrue(most>GoldHeal.PUSH*.9f,"it presses");
        assertEquals(0,GoldHeal.press(held,GoldHeal.TOUCH-.01f),1e-6,"not before it is in");
        assertTrue(GoldHeal.press(held,GoldHeal.TOUCH+every+.02f)>GoldHeal.PUSH*.9f,"with each healing");
        // its light is on while the fist is in
        assertEquals(0,GoldHeal.on(held,GoldHeal.TOUCH-.05f),1e-6);assertEquals(1,GoldHeal.on(held,1f),1e-6);
        held.ended=1f;assertEquals(0,GoldHeal.on(held,1.2f),1e-6);
    }
    @Test void theLeavesWindUpAboutTheHealedBodyAndAreGone(){
        for(int k=0;k<GoldHealFx.LEAVES_N;k++){
            double[] first=GoldHealFx.leaf(k,7,0,.6),last=GoldHealFx.leaf(k,7,.999f,.6),prev=null;
            assertEquals(0,first[4],1e-6,"none as it begins");assertTrue(last[4]<.02,"gone at its end");
            assertTrue(last[1]>first[1]+.6,"it rises: "+first[1]+" to "+last[1]);
            for(float u=0;u<1;u+=.01f){
                double[] l=GoldHealFx.leaf(k,7,u,.6);
                assertTrue(l[2]>=.5,"clear of the body");
                if(prev!=null){
                    assertTrue(l[1]>=prev[1]-1e-9,"it never sinks");
                    assertTrue(Math.abs(l[0]-prev[0])<.2&&Math.abs(l[3]-prev[3])<.2&&Math.abs(l[4]-prev[4])<.15,"no jump at "+u);
                }
                prev=l;
            }
        }
    }
}
