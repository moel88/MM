package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum's rush (2.57.0): its barrage smooth at 60 fps held to the end and let go at any moment, its afterimages
 * too, taking over from a punch and a punch taking over from it, its fists left and right in turn at every height, its
 * place on whoever is in its way, and its two sounds.
 */
class StandRushTest {
    static final float FRAME=1/60f;
    private static List<String> bones()throws Exception{
        try(var in=StandRushTest.class.getResourceAsStream("/assets/celestialfall/geo/entity/star_platinum.geo.json")){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    /** Sets this client's clock to {@code t} seconds; returns the frame's part of a tick. */
    static float clock(float t){long whole=(long)Math.floor(t*20+1e-4);StandRush.ticks=whole;return Math.max(0,t*20-whole);}
    /** The look drawn: at rest, taken over by the rush as far as the stand has come round (as StandPresence puts it on). */
    static void look(StandPunch.Shape s,StarPlatinumLook look){
        look.only(StarPlatinumStance.IDLE);if(s==null)return;
        if(s.engage>0)s.onto(look);
        look.wind=.35f*s.engage;look.gust=s.gust;
    }
    /** Follows the drawn body frame by frame: no bone turns 40 degrees or more in a frame (the angle between the two whole turns). */
    static final class Watch {
        final StarPlatinumPoses poses=new StarPlatinumPoses();final List<String> names;final float[][] last;final float[] p=new float[6];
        float fastest;String where="";
        Watch(List<String> names){this.names=names;last=new float[names.size()][];}
        void see(StarPlatinumLook look,float t,String what){
            float sum=0,under=0;for(int i=0;i<look.weights.length;i++){sum+=look.weights[i];under+=look.under[i];}
            assertEquals(1,sum,1e-4,"weights at "+t+" ("+what+")");if(look.over<1)assertEquals(1,under,1e-4,"the pose under at "+t+" ("+what+")");
            for(int b=0;b<names.size();b++){
                poses.pose(names.get(b),look,t,p);
                if(last[b]!=null){
                    float d=StandPunchTest.turn(names.get(b),last[b],p);
                    if(d>fastest){fastest=d;where=names.get(b)+" at "+t;}
                    assertTrue(d<40,names.get(b)+" turns "+d+" degrees in a frame at "+t+" ("+what+")");
                }
                last[b]=p.clone();
            }
        }
    }

    @Test void heldToTheEndItsBarrageMovesWithoutAJumpAt60fps()throws Exception{
        var names=bones();var body=new Watch(names);var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        var r=new StandRush.Rush(0,0,0,48611);
        float lastEngage=0;boolean front=false;
        for(int f=0;f<=(int)((r.ends()+StandRush.LINGER+StandRush.RETURN+.2f)/FRAME);f++){
            float t=f*FRAME,delta=clock(t);
            var s=StandRush.rush(r,delta,0,shape);look(s,look);
            if(s!=null){
                assertTrue(Math.abs(s.engage-lastEngage)<.25f,"comes round in a jump at "+t);lastEngage=s.engage;
                if(t>=StarPlatinumRules.RUSH_FIRST/20f&&t<=r.ends())front|=s.engage>.95f;
            }
            body.see(look,t,"held");
        }
        assertTrue(front,"in front as its blows land");
        assertTrue(body.fastest>15,"its fists are fast: "+body.fastest+" ("+body.where+")");
        // the afterimages' fists, each on its own clock and in its own order
        for(int i=0;i<StandRush.phantoms();i++){
            var w=new Watch(names);var ghost=new StarPlatinumLook();
            for(int f=0;f<=(int)(r.ends()/FRAME);f++){
                float t=f*FRAME;ghost.only(IDLE);java.util.Arrays.fill(ghost.weights,0);
                StandRush.barrage(t-StandRush.phantomLag(i),StandRush.phantomSeed(r,i),ghost.weights,1);
                w.see(ghost,t,"afterimage "+i);
            }
        }
    }
    @Test void letGoAtAnyMomentItsFistsGoBackToTheGuardAndItGoesBack()throws Exception{
        var names=bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        for(int stop:new int[]{1,2,3,4,5,7,10,17,33,50,66,80,83}){
            var r=new StandRush.Rush(0,0,0,977+stop);var body=new Watch(names);float lastEngage=0;
            for(int f=0;f<=(int)((stop/20f+StandRush.SETTLE+StandRush.LINGER+StandRush.RETURN+.3f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);
                // the word that it ended comes as its tick begins, and it ends there on this client's clock (StandRush.event)
                if(r.on()&&StandRush.ticks>=stop)r.ended=Math.min(r.age(0),StarPlatinumRules.RUSH_LENGTH/20f);
                var s=StandRush.rush(r,delta,0,shape);look(s,look);
                if(s!=null){assertTrue(Math.abs(s.engage-lastEngage)<.25f,"goes back in a jump at "+t+" (let go at "+stop+")");lastEngage=s.engage;}
                body.see(look,t,"let go at tick "+stop);
            }
            assertFalse(r.on());assertEquals(stop/20f,r.ends(),1e-6);
            StandRush.rush(r,clock(r.ends()+StandRush.SETTLE),0,shape);
            assertEquals(1,shape.weights[GUARD],1e-4,"back in its guard once let go at "+stop);
            assertNull(StandRush.rush(r,clock(r.ends()+StandRush.LINGER+StandRush.RETURN+.05f),0,shape),"gone back to its place once let go at "+stop);
            assertEquals(0,StandRush.phantomAlpha(r,0,r.ends()+.1f),1e-6,"no afterimages once it is over");
        }
    }
    @Test void itTakesOverFromAPunchAndAPunchTakesOverFromIt()throws Exception{
        var names=bones();var look=new StarPlatinumLook();var into=new StandPunch.Shape();var rush=new StandPunch.Shape();
        // a punch in flight when E goes down: the server stops it there and starts the rush once the stand has turned
        // back to its guard (StarPlatinumRules.STOP_RETURN); the rush let go a while later, and a jab asked for soon
        // after, which starts once the rush's fists are back in the guard (RUSH_SETTLE): every punch, E down at every
        // moment of it, the jab at every moment of the rush's way back
        for(int combo=1;combo<=4;combo++)for(int at=1;at<=12;at++){
            float eAt=at/20f,rushAt=eAt+StarPlatinumRules.STOP_RETURN/20f,letGo=rushAt+.15f+.05f*(at%5),jabAt=letGo+Math.max(StarPlatinumRules.RUSH_SETTLE,at%7)/20f;
            var before=new StandPunch.Punch(combo,0,Float.NaN,Float.NaN).stop(eAt);var jab=new StandPunch.Punch(1,0,Float.NaN,Float.NaN);
            int startTick=Math.round(rushAt*20);var r=new StandRush.Rush(startTick,0,startTick,515+at);
            var body=new Watch(names);float lastEngage=0;String what="punch "+combo+", E at "+eAt+", the rush at "+rushAt+", let go at "+letGo+", a jab at "+jabAt;
            for(int f=0;f<=(int)((jabAt+1.3f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);
                if(r.on()&&StandRush.ticks>=Math.round(letGo*20))r.ended=r.age(0);
                // the punches' shape as StandAttack makes it (the newest, and the one before while it takes over)
                StandPunch.Shape punch=null;float pt;
                if(t>=jabAt){pt=t-jabAt;if(pt<=StandPunch.length(jab))punch=StandPunch.at(jab,pt,before,t,into);}
                else{pt=t;if(t<=StandPunch.length(before))punch=StandPunch.at(before,t,null,0,into);}
                var s=t<rushAt?punch:StandRush.combine(punch,pt,StandRush.rush(r,delta,0,rush),r.age(delta),into);
                look(s,look);
                if(s!=null){assertTrue(Math.abs(s.engage-lastEngage)<.25f,"in front in a jump at "+t+" ("+what+")");lastEngage=s.engage;}
                body.see(look,t,what);
            }
        }
    }
    @Test void heardLateTogetherItStillTakesOverFromThePunchWithoutAJump()throws Exception{
        // a lagging connection: the word that the punch stopped and the word that the rush started come in the same tick
        var names=bones();var look=new StarPlatinumLook();var into=new StandPunch.Shape();var rush=new StandPunch.Shape();
        for(int combo=1;combo<=4;combo++)for(int at=1;at<=12;at++){
            float rushAt=at/20f;
            var before=new StandPunch.Punch(combo,0,Float.NaN,Float.NaN).stop(rushAt);
            var r=new StandRush.Rush(at,0,at,733+at);var body=new Watch(names);
            for(int f=0;f<=(int)((rushAt+1.2f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);
                StandPunch.Shape punch=t<=StandPunch.length(before)?StandPunch.at(before,t,null,0,into):null;
                var s=t<rushAt?punch:StandRush.combine(punch,t,StandRush.rush(r,delta,0,rush),r.age(delta),into);
                look(s,look);body.see(look,t,"punch "+combo+" stopped by the rush at "+rushAt+", heard together");
            }
        }
    }
    @Test void itsFistsGoLeftAndRightInTurnAtEveryHeight(){
        int[] heights=new int[3];
        for(int k=0;k<4000;k++){
            int stance=StandRush.fist(k,7177);
            int[] side=(k&1)==0?StandRush.LEFT:StandRush.RIGHT;int at=-1;
            for(int i=0;i<3;i++)if(side[i]==stance)at=i;
            assertTrue(at>=0,"fist "+k+" with the "+((k&1)==0?"left":"right"));
            heights[at]++;
        }
        assertEquals(.5,heights[0]/4000.0,.05,"the middle most of all");
        assertEquals(.25,heights[1]/4000.0,.05,"high");assertEquals(.25,heights[2]/4000.0,.05,"low");
        // and every fist strikes out from where the one before left it, at full stretch as it lands
        var w=new float[NAMES.size()];
        for(int k=0;k<20;k++){
            java.util.Arrays.fill(w,0);StandRush.barrage((k+1)*StandRush.HALF-1e-5f,7177,w,1);
            assertEquals(1,w[StandRush.fist(k,7177)],1e-3,"fist "+k+" at full stretch as it lands");
        }
        java.util.Arrays.fill(w,0);StandRush.barrage(0,7177,w,1);assertEquals(1,w[GUARD],1e-6,"from the guard");
    }
    @Test void itStandsWhereItsFistsSinkIntoWhoeverIsInItsWay(){
        float reach=StandRush.fistReach();
        assertTrue(reach>1&&reach<1.4f,"its fists reach "+reach+" blocks ahead of it");
        // someone whose near side is 2.2 blocks ahead: the fists, at full stretch, sink BITE into them
        float place=Math.max(StandRush.NEAR,Math.min(StandRush.FAR,2.2f+StandRush.BITE-reach));
        assertEquals(2.2f+StandRush.BITE,place+reach,1e-5);
        assertTrue(StandRush.NEAR>.7f,"never inside its user");
        assertTrue(StandRush.FAR>=StarPlatinumRules.RUSH_REACH+StandRush.BITE-reach,"it reaches whoever its blows can strike");
        assertTrue(StandPunch.burst(StarPlatinumRules.RUSH_FIRST/20f/StandRush.RISE)>.95f,"round in front as the first blow lands");
    }
    @Test void itsBlowsSoundOnlyWhileItStrikesAndBothFadeWhenLetGo(){
        var r=new StandRush.Rush(0,0,0,1);
        var voice=new StandRush.Sound(new net.minecraft.util.Identifier("celestialfall","star_platinum_rush"),r,false,.9f);
        var blows=new StandRush.Sound(new net.minecraft.util.Identifier("celestialfall","star_platinum_rush_hits"),r,true,.9f);
        assertEquals(0,blows.loudness(),1e-6,"the blows silent until it strikes someone");
        r.strike=1;voice.tick();blows.tick();assertEquals(.9f,voice.loudness(),1e-6);assertEquals(.9f,blows.loudness(),1e-6);
        r.strike=0;voice.tick();blows.tick();assertEquals(.9f,voice.loudness(),1e-6,"the voice whoever it strikes");assertEquals(0,blows.loudness(),1e-6);
        r.strike=1;voice.tick();voice.fade();blows.fade();
        float lv=voice.loudness();
        for(int i=0;i<StandRush.FADE;i++){
            assertFalse(voice.isDone(),"still fading");
            voice.tick();blows.tick();assertTrue(voice.loudness()<lv,"quieter every tick");lv=voice.loudness();
        }
        assertEquals(0,voice.loudness(),1e-6);assertTrue(voice.isDone()&&blows.isDone(),"gone once faded");
    }
}
