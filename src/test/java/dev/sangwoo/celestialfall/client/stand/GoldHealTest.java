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
 * Gold Experience's healing as it is drawn (2.149.0): the tap's hand and the hold's barrage on its user smooth at 60
 * fps to their end and stopped or let go at any moment; the hand on whom it is for as the server heals them, each fist
 * on its user as the server's blow heals; the leaves winding up about the healed body.
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
    @Test void theHandGoesOutAndBackWithoutAJumpAt60fps()throws Exception{
        float until=GoldHeal.LENGTH+GoldHeal.LINGER+GoldHeal.RETURN+.3f;
        watch(tap(Float.NaN),until,"tapped");
        watch(tap(2.4f),until,"tapped at someone");
        for(float at:new float[]{0,.08f,.2f,.28f}){
            var h=tap(1.5f);h.stopped=at;
            watch(h,until,"stopped at "+at);
            float[] w=new float[NAMES.size()];GoldHeal.stances(at+GoldHeal.CUT,at,w);
            assertEquals(1,w[GUARD],1e-4,"in its guard "+GoldHeal.CUT+" s after it was stopped at "+at);
        }
    }
    @Test void theHandIsOnThemAsTheServerHealsThem(){
        assertEquals(GoldExperienceRules.MEND_CONTACT/20f,GoldHeal.TOUCH,1e-6,"the hand is laid on on the server's tick");
        assertEquals(GoldExperienceRules.MEND_LENGTH/20f,GoldHeal.LENGTH,1e-6);
        float[] w=new float[NAMES.size()];
        GoldHeal.stances(0,Float.NaN,w);assertEquals(1,w[GUARD],1e-5,"from its guard");
        w=new float[NAMES.size()];GoldHeal.stances(GoldHeal.TOUCH,Float.NaN,w);assertEquals(1,w[MEND],1e-5,"laid on");
        w=new float[NAMES.size()];GoldHeal.stances(GoldHeal.STAYS,Float.NaN,w);assertEquals(1,w[MEND],1e-5,"it stays");
        w=new float[NAMES.size()];GoldHeal.stances(GoldHeal.GUARDED,Float.NaN,w);assertEquals(1,w[GUARD],1e-5,"back in its guard");
        // laid on softly: it never speeds up into them (slower and slower over its last third)
        float last=Float.MAX_VALUE;
        for(float t=GoldHeal.TOUCH-.08f;t<=GoldHeal.TOUCH+1e-4;t+=FRAME){
            float step=GoldHeal.reach(t)-GoldHeal.reach(t-FRAME);
            assertTrue(step<=last+1e-4&&step>=-1e-6,"it speeds up at "+t);last=step;
        }
        // the hand is open and out at a body's chest, and it stands so that the hand rests on the first one in its way
        float[] out=GoldHeal.hand(tap(Float.NaN),GoldHeal.TOUCH);
        assertTrue(out[0]>1.1f,"out: "+out[0]);assertTrue(out[2]>1.0f&&out[2]<1.7f,"at a body's chest: "+out[2]);
        for(float aim:new float[]{2.0f,2.8f,3.6f}){
            float place=GoldHeal.place(tap(aim));
            assertEquals(aim+GoldHeal.PRESS,place+GoldHeal.handReach(),1e-4,"its hand on them at "+aim);
        }
        assertEquals(GoldHeal.NEAR,GoldHeal.place(tap(.3f)),1e-6,"never on top of its user");
        assertEquals(GoldHeal.AHEAD,GoldHeal.place(tap(Float.NaN)),1e-6);
    }
    @Test void heldItsFistsFallOnItsUserWithoutAJumpHoweverLongItIsHeld()throws Exception{
        float after=GoldHeal.LINGER+GoldHeal.RETURN+.4f;
        watch(hold(),GoldExperienceRules.SELF_LENGTH/20f+after,"held to its end");
        for(float at:new float[]{.02f,.1f,.33f,1.07f,2.5f,4.99f}){
            var h=hold();h.ended=at;
            watch(h,at+after,"let go at "+at);
        }
    }
    @Test void eachFistIsOnItsUserAsTheServersBlowHeals(){
        // fist k is at full stretch on the tick the server's blow k heals
        for(int k=0;k<GoldExperienceRules.SELF_BLOWS;k++){
            float at=(GoldExperienceRules.SELF_FIRST+GoldExperienceRules.SELF_EVERY*k)/20f;
            assertTrue(GoldExperienceRules.selfBlow(GoldExperienceRules.SELF_FIRST+GoldExperienceRules.SELF_EVERY*k));
            float[] w=new float[NAMES.size()];GoldHeal.barrage(at,5,w,1);
            assertEquals(1,w[GoldRush.fist(k,5)],2e-3,"fist "+k+" out as blow "+k+" heals");
            assertEquals((k&1)==0?1:0,GoldRush.out(w,GoldRush.LEFT),2e-3,"left and right in turn");
        }
        assertEquals(GoldExperienceRules.SELF_BLOWS,GoldExperienceRules.selfBlows(GoldExperienceRules.SELF_LENGTH));
        // it stands at its user's front right, its fists stopping on their body
        assertTrue(GoldHeal.SIDE>20&&GoldHeal.SIDE<80,"at their front right");
        float reach=GoldRush.grown(RUSH_L,0,GoldHeal.ARMS*(1+GoldHeal.STRETCH))[0];
        assertEquals(GoldHeal.BODY-GoldHeal.BITE,GoldHeal.selfPlace()-reach,1e-5,"its fists stop a little into their body");
        assertTrue(GoldHeal.selfPlace()>StandPresence.GAP,"never nearer than a stand keeps");
        // the afterimages of its arms fly only while it lasts
        var h=hold();var look=new GoldExperienceLook();
        GoldRushTest.clock(1f);assertTrue(GoldHeal.phantom(h,0,GoldRushTest.clock(1f),look)>0);
        h.ended=1f;assertEquals(0,GoldHeal.phantom(h,0,GoldRushTest.clock(1.2f),look),1e-6);
        assertEquals(0,GoldHeal.phantom(tap(1),0,GoldRushTest.clock(.2f),look),1e-6,"none for the tap");
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
