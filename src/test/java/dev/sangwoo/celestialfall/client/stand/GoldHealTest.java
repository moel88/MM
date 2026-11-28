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
 * Gold Experience's healing as it is drawn (2.149.0, 2.153.0): one punch, the tap's and the hold's smooth at 60 fps to
 * their end and stopped at any moment; the fist in as the server heals (the hold all at once, then out as the tap's);
 * the leaves winding up about the healed body; the sounds on their moments.
 */
class GoldHealTest {
    static final float FRAME=1/60f;
    /** The bones of a skin's skeleton (2.156.0: each skin its own, GoldSkin). */
    private static List<String> bones(GoldSkin skin)throws Exception{
        try(var in=GoldHealTest.class.getResourceAsStream(skin.geo())){
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
        // (2.156.0) in every skin, each with its own skeleton, stances and fists
        for(var skin:GoldSkin.values()){h.skin=skin;watch(skin,h,until,what);}
        h.skin=GoldSkin.ORIGINAL;
    }
    private void watch(GoldSkin skin,GoldHeal.Heal h,float until,String what)throws Exception{
        what=skin+": "+what;
        var names=bones(skin);var poses=new GoldExperiencePoses(skin);var look=new GoldExperienceLook();var shape=new GoldRush.Shape();
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
        assertEquals(GoldExperienceRules.SELF_FIRST/20f,GoldHeal.TOUCH,1e-6,"and in its user as they are healed");
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
        // the fist is out at a body's chest, and it stands so that the fist is well into the first one in its way (2.156.0: in every skin)
        for(var skin:GoldSkin.values()){
            var s=tap(Float.NaN);s.skin=skin;
            float[] out=GoldHeal.fist(s,GoldHeal.TOUCH);
            // (2.155.0: the body the user sent has shorter arms than the box-built one, which reaches past 1.2)
            assertTrue(out[0]>(skin==GoldSkin.ORIGINAL?1.2f:1.05f),skin+": out: "+out[0]);assertTrue(out[2]>1.0f&&out[2]<1.7f,skin+": at a body's chest: "+out[2]);
            assertEquals(GoldHeal.fistReach(skin),out[0],1e-4);
            for(float aim:new float[]{2.0f,2.8f,3.6f}){
                var a=tap(aim);a.skin=skin;
                assertEquals(aim+GoldHeal.PRESS,GoldHeal.place(a)+GoldHeal.fistReach(skin),1e-4,skin+": its fist into them at "+aim);
            }
        }
        assertTrue(GoldHeal.PRESS>=.15f,"driven in, not laid on");
        assertEquals(GoldHeal.NEAR,GoldHeal.place(tap(.3f)),1e-6,"never on top of its user");
        assertEquals(GoldHeal.AHEAD,GoldHeal.place(tap(Float.NaN)),1e-6);
    }
    @Test void heldTheFistGoesIntoItsUserAndOutWithoutAJumpAt60fps()throws Exception{
        float until=GoldHeal.LENGTH+GoldHeal.LINGER+GoldHeal.RETURN+.3f;
        watch(hold(),until,"held");
        // stopped short at any moment: a blow on its user, before the fist is in or after
        for(float at:new float[]{0,.08f,.2f,.28f,.31f,.45f,.55f}){
            var h=hold();h.stopped=at;
            watch(h,until,"held, stopped at "+at);
            float[] w=new float[NAMES.size()];GoldHeal.stances(h,Math.min(at,GoldHeal.STAYS)+GoldHeal.CUT,w);
            assertEquals(1,w[GUARD],1e-4,"in its guard "+GoldHeal.CUT+" s after it was stopped at "+at);
        }
    }
    @Test void heldItIsOnePunchIntoItsUserOutAgainAsTheTaps(){
        var h=hold();var tap=tap(Float.NaN);
        // the same punch as the tap's, on the same moments (2.153.0: all of it at once, never left in until Y is let go)
        for(float t=0;t<=GoldHeal.LENGTH+.5f;t+=FRAME){
            float[] a=new float[NAMES.size()],b=new float[NAMES.size()];GoldHeal.stances(h,t,a);GoldHeal.stances(tap,t,b);
            assertArrayEquals(b,a,1e-6f,"as the tap's at "+t);
        }
        float[] w=new float[NAMES.size()];GoldHeal.stances(h,GoldHeal.TOUCH,w);assertEquals(1,w[MEND],1e-5,"in as they are healed");
        w=new float[NAMES.size()];GoldHeal.stances(h,GoldHeal.GUARDED,w);assertEquals(1,w[GUARD],1e-5,"out again, in its guard");
        for(int s:GoldRush.LEFT){float[] v=new float[NAMES.size()];GoldHeal.stances(h,.4f,v);assertEquals(0,v[s],1e-6,"no fist of the rush");}
        // it stands at its user's front right with its knuckles well inside their body
        assertTrue(GoldHeal.SIDE>20&&GoldHeal.SIDE<80,"at their front right");
        assertTrue(GoldHeal.DEPTH<.13f,"inside a player's body (half as deep as .125)");
        for(var skin:GoldSkin.values()){
            assertEquals(GoldHeal.DEPTH,GoldHeal.selfPlace(skin)-GoldHeal.fistReach(skin),1e-5);
            assertTrue(GoldHeal.selfPlace(skin)>StandPresence.GAP,skin+": never nearer than a stand keeps");
        }
        // its light is on while the fist is in, and out as it is drawn out
        assertEquals(0,GoldHeal.on(h,GoldHeal.TOUCH-.05f),1e-6);assertEquals(1,GoldHeal.on(h,.4f),1e-6);assertEquals(0,GoldHeal.on(h,GoldHeal.STAYS+.12f),1e-6);
    }
    @Test void theirSoundsComeAsTheFistGoesIn(){
        // the tap's from the moment the fist is in; the hold's two ticks before, its first sparkle 0.12 s into it
        assertEquals(1,GoldHeal.TAP_SOUND.length);assertEquals(GoldHeal.TOUCH,GoldHeal.TAP_SOUND[0].at(),1e-6);
        assertEquals("gold_experience_mend",GoldHeal.TAP_SOUND[0].sound());
        assertEquals(1,GoldHeal.HOLD_SOUND.length);assertEquals("gold_experience_self_heal",GoldHeal.HOLD_SOUND[0].sound());
        assertEquals(GoldHeal.TOUCH,GoldHeal.HOLD_SOUND[0].at()+.12f,.03f,"its first sparkle as the fist goes in");
        for(var c:new GoldSounds.Cue[]{GoldHeal.TAP_SOUND[0],GoldHeal.HOLD_SOUND[0]})assertEquals(0,Math.round(c.at()*20)-c.at()*20,1e-4,"on a tick: "+c.at());
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
