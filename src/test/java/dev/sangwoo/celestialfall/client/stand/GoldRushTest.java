package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import dev.sangwoo.celestialfall.stand.GoldExperienceStance;
import dev.sangwoo.celestialfall.stand.StarPlatinumRush;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gold Experience's muda rush as it is drawn (2.147.0): its barrage smooth at 60 fps held to the end and let go at any
 * moment, its fists left and right in turn at every height and a little slower than Star Platinum's, its arms growing
 * and coming back without a jump, the afterimages of its arms, and where its fists are.
 */
class GoldRushTest {
    static final float FRAME=1/60f;
    private static List<String> bones()throws Exception{
        try(var in=GoldRushTest.class.getResourceAsStream("/assets/celestialfall/geo/entity/gold_experience.geo.json")){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    /** Sets this client's clock to {@code t} seconds; returns the frame's part of a tick. */
    static float clock(float t){long whole=(long)Math.floor(t*20+1e-4);StandRush.ticks=whole;return Math.max(0,t*20-whole);}
    static StandRush.Rush rush(int seed){var r=new StandRush.Rush(0,0,0,seed);r.kind=StarPlatinumRush.GOLD;return r;}
    /** The angle (degrees) between two turns given as the poses' angles. */
    static double turn(float[] a,float[] b){
        double[] p=StarPlatinumPoses.quat(a[0],a[1],a[2],new double[4]),q=StarPlatinumPoses.quat(b[0],b[1],b[2],new double[4]);
        double d=Math.abs(p[0]*q[0]+p[1]*q[1]+p[2]*q[2]+p[3]*q[3]);
        return Math.toDegrees(2*Math.acos(Math.min(1,d)));
    }
    /** Runs a rush frame by frame from the stand at rest beside its user; no bone, arm or place jumps. */
    private void watch(StandRush.Rush r,float until,String what)throws Exception{
        var names=bones();var poses=new GoldExperiencePoses();var look=new GoldExperienceLook();var shape=new GoldRush.Shape();
        float[] scratch=new float[NAMES.size()],p=new float[6];float[][] last=new float[names.size()][];
        float armL=1,armR=1,engage=0;boolean shown=false;
        for(float t=0;t<=until;t+=FRAME){
            float delta=clock(t);
            look.only(IDLE);look.armL=look.armR=1;
            boolean on=GoldRush.pose(r,delta,look,shape,scratch);shown|=on;
            if(!on){shape.engage=0;}
            float sum=0;for(float w:look.weights){assertTrue(w>=-1e-5,what+" at "+t);sum+=w;}
            assertEquals(1,sum,1e-4,"weights at "+t+" ("+what+")");
            for(int b=0;b<names.size();b++){
                poses.blend(names.get(b),look.weights,t,p);
                if(last[b]!=null)assertTrue(turn(last[b],p)<45,names.get(b)+" turns "+turn(last[b],p)+" degrees in a frame at "+t+" ("+what+")");
                last[b]=p.clone();
            }
            assertTrue(look.armL>=1-1e-4&&look.armL<=GoldRush.ARMS*(1+GoldRush.STRETCH)+1e-4,"the left arm's size "+look.armL);
            assertTrue(Math.abs(look.armL-armL)<.08&&Math.abs(look.armR-armR)<.08,"an arm jumps in size at "+t+" ("+what+")");
            assertTrue(Math.abs(shape.engage-engage)<.25,"it jumps round at "+t+" ("+what+"): "+engage+" to "+shape.engage);
            armL=look.armL;armR=look.armR;engage=shape.engage;
        }
        assertTrue(shown,what);
        assertEquals(0,engage,.02,"back at its user's shoulder ("+what+")");assertEquals(1,armL,.01);
        assertEquals(1,look.weights[IDLE],.02,"at rest again ("+what+")");
    }
    @Test void heldToTheEndItsBarrageMovesWithoutAJumpAt60fps()throws Exception{
        var r=rush(11);
        assertEquals(GoldExperienceRules.RUSH_LENGTH/20f,r.ends(),1e-6,"its own length");
        watch(r,r.ends()+GoldRush.LINGER+GoldRush.RETURN+.3f,"held");
        // in front of its user through it
        clock(1f);var shape=new GoldRush.Shape();var look=new GoldExperienceLook().only(IDLE);
        assertTrue(GoldRush.pose(r,0,look,shape,new float[NAMES.size()]));
        assertEquals(1,shape.engage,1e-3);assertEquals(0,look.weights[IDLE],1e-3,"all in its rush's stances");
    }
    @Test void letGoAtAnyMomentItsFistsGoBackWithoutAJump()throws Exception{
        for(float end:new float[]{.05f,.2f,.33f,.71f,1.48f,2.9f}){
            var r=rush(7);r.ended=end;
            watch(r,end+GoldRush.LINGER+GoldRush.RETURN+.3f,"let go at "+end);
        }
        // let go before it began: it never shows
        var r=rush(7);r.ended=0;
        assertFalse(GoldRush.pose(r,0,new GoldExperienceLook().only(IDLE),new GoldRush.Shape(),new float[NAMES.size()]));
    }
    @Test void itsFistsGoLeftAndRightInTurnAtEveryHeightALittleSlowerThanStarPlatinums(){
        assertTrue(GoldRush.HALF>StandRush.HALF&&GoldRush.HALF<StandRush.HALF*1.2f,"a little slower");
        int[] seen=new int[NAMES.size()];
        for(int k=0;k<200;k++){
            int f=GoldRush.fist(k,5);seen[f]++;
            boolean left=f==RUSH_L||f==RUSH_L_HI||f==RUSH_L_LO;
            assertEquals((k&1)==0,left,"fist "+k);
        }
        for(int s:new int[]{RUSH_L,RUSH_L_HI,RUSH_L_LO,RUSH_R,RUSH_R_HI,RUSH_R_LO})assertTrue(seen[s]>8,NAMES.get(s)+" thrown "+seen[s]+" times");
        assertTrue(seen[RUSH_L]>seen[RUSH_L_HI]&&seen[RUSH_L]>seen[RUSH_L_LO],"the middle most often");
        // at the top of each fist's time its stance is all there is: the fist stops dead at full stretch
        float[] w=new float[NAMES.size()];GoldRush.barrage(GoldRush.HALF*3-1e-4f,5,w,1);
        assertEquals(1,w[GoldRush.fist(2,5)],1e-2);
        java.util.Arrays.fill(w,0);GoldRush.barrage(0,5,w,1);assertEquals(1,w[GUARD],1e-6,"from the guard");
    }
    @Test void theAfterimagesOfItsArmsFlyOnlyThroughTheBarrage(){
        var r=rush(3);var look=new GoldExperienceLook();
        clock(.05f);assertEquals(0,GoldRush.phantom(r,0,0,look),1e-6,"not before it has come round");
        float most=0;
        for(float t=.4f;t<3;t+=.013f){
            float delta=clock(t);
            for(int i=0;i<GoldRush.PHANTOMS.length;i++){
                float a=GoldRush.phantom(r,i,delta,look);most=Math.max(most,a);
                assertTrue(a>0&&a<=.6f,"afterimage "+i+" at "+t+": "+a);
                assertTrue(look.showL>=0&&look.showL<=1&&look.showR>=0&&look.showR<=1);
                // one arm strikes out as the other is drawn back: never both shown in full
                assertTrue(look.showL+look.showR<=1.001f);
                float sum=0;for(float w:look.weights)sum+=w;assertEquals(1,sum,1e-4);
            }
        }
        assertTrue(most>.3f);
        clock(r.ends()+.2f);for(int i=0;i<GoldRush.PHANTOMS.length;i++)assertEquals(0,GoldRush.phantom(r,i,0,look),1e-6,"gone as it ends");
        assertTrue(GoldRush.GREEN<GoldRush.RED&&GoldRush.BLUE<GoldRush.GREEN,"golden");
    }
    @Test void itsFistsAreWhereItsStancesPutThem(){
        for(int s:new int[]{RUSH_L,RUSH_L_HI,RUSH_L_LO}){
            float[] out=GoldRush.FISTS[s][0],back=GoldRush.FISTS[s][1];
            assertTrue(out[0]>.85f,NAMES.get(s)+": the left fist out in front, "+out[0]);
            assertTrue(back[0]<out[0]-.4f,NAMES.get(s)+": the right one drawn back");
            // the mirror on the other side
            float[] other=GoldRush.FISTS[s+3][1];
            assertEquals(out[0],other[0],1e-3);assertEquals(-out[1],other[1],1e-3);assertEquals(out[2],other[2],1e-3);
        }
        assertTrue(GoldRush.FISTS[RUSH_L_HI][0][2]>GoldRush.FISTS[RUSH_L][0][2]+.1f&&GoldRush.FISTS[RUSH_L][0][2]>GoldRush.FISTS[RUSH_L_LO][0][2]+.1f,"high, middle and low");
        // bigger arms reach further, out from the shoulder
        float[] grown=GoldRush.grown(RUSH_L,0,GoldRush.ARMS*(1+GoldRush.STRETCH));
        assertTrue(grown[0]>GoldRush.FISTS[RUSH_L][0][0]+.05f);assertEquals(grown[0],GoldRush.fistReach(),1e-6);
        assertTrue(GoldRush.fistReach()>1&&GoldRush.fistReach()<1.9f,"its reach: "+GoldRush.fistReach());
        assertEquals(GoldExperienceStance.NAMES.size(),GoldRush.FISTS.length);
    }
}
