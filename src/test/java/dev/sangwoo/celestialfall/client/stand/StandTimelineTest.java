package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import dev.sangwoo.celestialfall.stand.StarPlatinumStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Star Platinum coming out, at rest and going back (2.51.0; its aura 2.52.0): smooth at 60 fps, from any moment to any other. */
class StandTimelineTest {
    static final float FRAME=1/60f;
    private List<String> bones()throws Exception{
        try(var in=getClass().getResourceAsStream("/assets/celestialfall/geo/entity/star_platinum.geo.json")){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    private static float sum(StarPlatinumLook l){float s=0;for(float w:l.weights)s+=w;return s;}
    /** How far two looks are apart in what the eye sees most: where it is, how solid, how big, its stance and its aura. */
    private static float gap(StarPlatinumLook a,StarPlatinumLook b){
        float g=Math.max(Math.abs(a.out-b.out),Math.max(Math.abs(a.alpha-b.alpha),Math.abs(a.scale-b.scale)));
        g=Math.max(g,Math.abs(a.glow-b.glow));
        for(int i=0;i<a.weights.length;i++)g=Math.max(g,Math.abs(a.weights[i]-b.weights[i]));
        return Math.max(g,Math.max(Math.abs(a.wind-b.wind),Math.abs(a.gust-b.gust)));
    }

    @Test void itComesOutStrikesThePoseAndSettlesAtRest()throws Exception{
        var poses=new StarPlatinumPoses();var look=new StarPlatinumLook();var names=bones();
        float[][] last=new float[names.size()][];float[] p=new float[6];float fastest=0,flare=0;var prev=new StarPlatinumLook();
        for(int f=0;f<=(int)(3/FRAME);f++){
            float t=f*FRAME;StandTimeline.summon(t,look);
            assertEquals(1,sum(look),1e-5,"weights at "+t);
            assertTrue(look.alpha>=0&&look.alpha<=1&&look.out>=0&&look.out<=1.0001f,"at "+t);
            if(f>0)assertTrue(gap(prev,look)<.2f,"the look jumps "+gap(prev,look)+" at "+t);
            prev.set(look);flare=Math.max(flare,look.glow);
            for(int b=0;b<names.size();b++){
                poses.pose(names.get(b),look,t,p);
                if(last[b]!=null){
                    // how far the bone really turns (2.54.0: the stances are weighed as turns, so their angles alone
                    // no longer tell it)
                    float d=StandPunchTest.turn(names.get(b),last[b],p);fastest=Math.max(fastest,d);
                    // the burst is quick (a limb crosses the body in a quarter of a second) but never a jump
                    assertTrue(d<20,names.get(b)+" turns "+d+" degrees in a frame at "+t);
                }
                last[b]=p.clone();
            }
        }
        assertTrue(fastest>6,"the burst is a burst: "+fastest);
        // its aura (2.52.0) flares as it bursts into the pose
        assertTrue(flare>1.8f,"the aura flares: "+flare);
        // in its user's body and unseen as Q is pressed; at rest, solid, full size and still once settled
        StandTimeline.summon(0,look);assertEquals(0,look.out,1e-6);assertEquals(0,look.alpha,1e-6);assertEquals(0,look.glow,1e-6);assertEquals(1,look.weights[StarPlatinumStance.SUMMON],1e-6);
        StandTimeline.summon(StandTimeline.EMERGE,look);assertEquals(1,look.out,1e-6);assertEquals(1,look.alpha,1e-6);
        StandTimeline.summon((StandTimeline.POSED+StandTimeline.HOLD)/2,look);assertEquals(1,look.weights[StarPlatinumStance.HERO],1e-6);assertEquals(1,look.wind,1e-6);
        StandTimeline.summon(StandTimeline.SETTLED,look);
        assertEquals(1,look.weights[StarPlatinumStance.IDLE],1e-6);assertEquals(0,look.wind,1e-6);assertEquals(0,look.gust,1e-6);assertEquals(1,look.scale,1e-6);
        assertEquals(1,look.glow,1e-6,"the aura at rest");
    }
    @Test void theBurstGathersSpeedThenSlowsIntoThePose(){
        assertEquals(0,StandTimeline.burst(0),1e-6);assertEquals(1,StandTimeline.burst(1),1e-6);
        float h=1e-3f;
        assertEquals(0,(StandTimeline.burst(h)-StandTimeline.burst(0))/h,.02,"starts still");
        assertEquals(0,(StandTimeline.burst(1)-StandTimeline.burst(1-h))/h,.02,"ends still");
        float best=0,at=0;for(float u=0;u<=1;u+=.001f){float v=(StandTimeline.burst(u+h)-StandTimeline.burst(u))/h;if(v>best){best=v;at=u;}}
        assertEquals(1/3f,at,.02,"fastest a third of the way in");
        for(float u=0;u<1;u+=.01f)assertTrue(StandTimeline.burst(u+.01f)>=StandTimeline.burst(u),"never back");
    }
    @Test void goingBackFromAnyMomentStartsWhereItWas(){
        var from=new StarPlatinumLook();var look=new StarPlatinumLook();var prev=new StarPlatinumLook();
        for(float when=0;when<=2.5f;when+=.05f){
            StandTimeline.summon(when,from);from.lean=7;from.speed=3;
            StandTimeline.dismiss(0,from,look);assertTrue(gap(from,look)<1e-5,"going back at "+when+" jumps");
            prev.set(look);
            for(float t=FRAME;t<=StandTimeline.GONE+FRAME;t+=FRAME){
                StandTimeline.dismiss(t,from,look);assertEquals(1,sum(look),1e-5);
                assertTrue(gap(prev,look)<.2f,"going back from "+when+": a jump at "+t);prev.set(look);
            }
            assertTrue(StandTimeline.gone(StandTimeline.GONE));
            StandTimeline.dismiss(StandTimeline.GONE,from,look);assertEquals(0,look.alpha,1e-6);assertEquals(0,look.out,1e-6);assertEquals(0,look.glow,1e-6);
        }
    }
    @Test void qAgainAndAgainNeverMakesItJump(){
        // out, back at 0.9 s, out again at 1.05 s (in the middle of going back), back at 1.6 s: as the client plays it
        float[] changes={0,.9f,1.05f,1.6f};var from=new StarPlatinumLook();var look=new StarPlatinumLook();var prev=new StarPlatinumLook();
        boolean out=false;float since=-10;boolean hasFrom=false;
        for(int f=0;f<=(int)(2.4f/FRAME);f++){
            float now=f*FRAME;
            for(float c:changes)if(Math.abs(c-now)<FRAME/2){
                if(f>0){StandTimeline.at(out,now-since,hasFrom?from:null,look);from.set(look);hasFrom=true;}
                out=!out;since=now;
            }
            StandTimeline.at(out,now-since,hasFrom?from:null,look);
            if(f>0)assertTrue(gap(prev,look)<.2f,"a jump at "+now+": "+gap(prev,look));
            prev.set(look);
        }
        assertEquals(0,look.alpha,1e-6,"gone at the end");
    }
}
