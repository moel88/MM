package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import java.util.List;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.stand.StandRushTest.FRAME;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum's inhale (2.61.0): smooth at 60 fps through its breath, its suction and its hold, ended at any moment,
 * taking over from a punch stopped short, from the rush and a punch taking over from it; its stances in their order,
 * its mouth where the air goes in, and its sound fading out as it ends.
 */
class StandInhaleTest {
    /** Sets this client's clock (the inhale's, the rush's) to {@code t} seconds; returns the frame's part of a tick. */
    static float clock(float t){long whole=(long)Math.floor(t*20+1e-4);StandInhale.ticks=whole;StandRush.ticks=whole;return Math.max(0,t*20-whole);}
    /** An inhale heard as it starts {@code start} ticks into this client's clock. */
    static StandInhale.Inhale inhale(long start){return new StandInhale.Inhale(start,0,start);}
    /** Its catch heard as it comes (whom it caught: one, or no one). */
    static void caught(StandInhale.Inhale i,boolean anyone){
        i.caughtAt=Math.max(INHALE_CATCH/20f,i.age(0));
        i.caught=anyone?new StandInhale.Caught[]{new StandInhale.Caught(7,0,Vec3d.ZERO)}:new StandInhale.Caught[0];
    }
    /** The server's words as they come on this client's clock: its catch, and its end at tick {@code end} on its clock (or as its hold or its catch of no one runs out). */
    static void words(StandInhale.Inhale i,boolean anyone,long end){
        long in=i.ticksIn();
        if(Float.isNaN(i.caughtAt)&&in>=INHALE_CATCH&&end>INHALE_CATCH)caught(i,anyone);
        long over=Math.min(end,anyone?INHALE_LENGTH:INHALE_CATCH+INHALE_WHIFF);
        if(i.on()&&in>=over)i.ended=Math.min(Math.max(0,i.age(0)),i.ends());
    }

    @Test void heldToTheEndItBreathesInSucksAndHoldsWithoutAJumpAt60fps()throws Exception{
        var body=new StandRushTest.Watch(bones());var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        var i=inhale(0);float lastEngage=0;boolean front=false;
        for(int f=0;f<=(int)((INHALE_LENGTH/20f+StandInhale.LINGER+StandInhale.RETURN+.2f)/FRAME);f++){
            float t=f*FRAME,delta=clock(t);words(i,true,Long.MAX_VALUE);
            var s=StandInhale.inhale(i,delta,0,shape);StandRushTest.look(s,look);
            if(s!=null){
                assertTrue(Math.abs(s.engage-lastEngage)<.25f,"comes round in a jump at "+t);lastEngage=s.engage;
                if(Math.abs(t-INHALE_CATCH/20f)<FRAME)front=s.engage>.95f;
            }
            body.see(look,t,"held to the end");
        }
        assertTrue(front,"in front of its user as it catches");
        assertFalse(i.on());assertEquals(INHALE_LENGTH/20f,i.ends(),1e-6,"over as its hold runs out");
        System.out.println("the inhale's fastest turn in a frame: "+body.fastest+" ("+body.where+")");
    }
    @Test void endedAtAnyMomentItTurnsBackToTheGuardAndGoesBack()throws Exception{
        var names=bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        for(boolean anyone:new boolean[]{true,false})for(int stop:new int[]{1,2,4,6,7,8,9,10,12,16,23,24,40,60,90,107,108}){
            if(!anyone&&stop>INHALE_CATCH+INHALE_WHIFF)continue;
            var i=inhale(0);var body=new StandRushTest.Watch(names);float lastEngage=0;String what=(anyone?"caught someone":"caught no one")+", ended at "+stop;
            for(int f=0;f<=(int)((stop/20f+StandInhale.SETTLE+StandInhale.LINGER+StandInhale.RETURN+.3f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);words(i,anyone,stop);
                var s=StandInhale.inhale(i,delta,0,shape);StandRushTest.look(s,look);
                if(s!=null){assertTrue(Math.abs(s.engage-lastEngage)<.25f,"goes back in a jump at "+t+" ("+what+")");lastEngage=s.engage;}
                body.see(look,t,what);
            }
            assertFalse(i.on(),what);assertEquals(stop/20f,i.ends(),1e-6,what);
            StandInhale.inhale(i,clock(i.ends()+StandInhale.SETTLE),0,shape);
            assertEquals(1,shape.weights[GUARD],1e-4,"back in its guard ("+what+")");assertTrue(shape.over>=1-1e-4,"turned back ("+what+")");
            assertNull(StandInhale.inhale(i,clock(i.ends()+StandInhale.LINGER+StandInhale.RETURN+.05f),0,shape),"gone back to its place ("+what+")");
        }
    }
    @Test void itTakesOverFromAPunchStoppedShortAndAPunchTakesOverFromIt()throws Exception{
        // Y as a punch is in flight: the server stops the punch there and starts the inhale once the stand has turned back
        // to its guard (StarPlatinumRules.STOP_RETURN); the inhale ends a while later, and a jab asked for after it
        var names=bones();var look=new StarPlatinumLook();var into=new StandPunch.Shape();
        for(int combo=1;combo<=4;combo++)for(int at=1;at<=12;at++)for(boolean anyone:new boolean[]{true,false}){
            float yAt=at/20f;long startTick=Math.round(yAt*20)+STOP_RETURN;float inhaleAt=startTick/20f;
            int end=anyone?20+7*at:INHALE_CATCH+INHALE_WHIFF;float jabAt=inhaleAt+end/20f+(at%4)/20f;
            var before=new StandPunch.Punch(combo,0,Float.NaN,Float.NaN).stop(yAt);var jab=new StandPunch.Punch(1,0,Float.NaN,Float.NaN);
            var i=inhale(startTick);var body=new StandRushTest.Watch(names);float lastEngage=0;
            String what="punch "+combo+" stopped by Y at "+yAt+", the inhale at "+inhaleAt+" ("+(anyone?"caught someone":"caught no one")+", over "+end+" ticks in), a jab at "+jabAt;
            for(int f=0;f<=(int)((jabAt+1.3f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);
                if(t>=inhaleAt)words(i,anyone,end);
                StandPunch.Shape punch=null;float pt;
                if(t>=jabAt){pt=t-jabAt;if(pt<=StandPunch.length(jab))punch=StandPunch.at(jab,pt,before,t,into);}
                else{pt=t;if(t<=StandPunch.length(before))punch=StandPunch.at(before,t,null,0,into);}
                var s=t<inhaleAt?punch:StandInhale.over(punch,pt,i,delta,0,into);
                StandRushTest.look(s,look);
                if(s!=null){assertTrue(Math.abs(s.engage-lastEngage)<.25f,"in front in a jump at "+t+" ("+what+")");lastEngage=s.engage;}
                body.see(look,t,what);
            }
        }
    }
    @Test void itTakesOverFromTheRushOnceItsFistsAreBackInTheGuard()throws Exception{
        // Y just after the rush is let go: the inhale starts once the rush's fists are back in the guard (RUSH_SETTLE)
        var names=bones();var look=new StarPlatinumLook();var into=new StandPunch.Shape();var rushShape=new StandPunch.Shape();
        for(int stop:new int[]{3,9,20,41,60,83})for(int late=0;late<=6;late+=2){
            var r=new StandRush.Rush(0,0,0,311+stop);long startTick=stop+RUSH_SETTLE+late;var i=inhale(startTick);
            var body=new StandRushTest.Watch(names);String what="the rush let go "+stop+" ticks in, the inhale "+late+" ticks after its fists are back";
            for(int f=0;f<=(int)((startTick/20f+INHALE_LENGTH/20f+1)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);
                if(r.on()&&StandRush.ticks>=stop)r.ended=r.age(0);
                if(t>=startTick/20f)words(i,true,Long.MAX_VALUE);
                var base=StandRush.rush(r,delta,0,rushShape);
                var s=t<startTick/20f?base:StandInhale.over(base==null?null:into.set(base),r.age(delta),i,delta,0,into);
                StandRushTest.look(s,look);body.see(look,t,what);
            }
        }
    }
    @Test void itDrawsItsBreathInThenBendsIntoTheSuctionAndHeaves(){
        var w=new float[NAMES.size()];
        java.util.Arrays.fill(w,0);StandInhale.stances(0,w,1);assertEquals(1,w[GUARD],1e-6,"from the guard");
        java.util.Arrays.fill(w,0);StandInhale.stances(StandInhale.DRAW_FROM,w,1);assertEquals(1,w[GUARD],1e-6,"its guard until it is round in front");
        assertTrue(StandPunch.burst(StandInhale.DRAW_FROM/StandInhale.RISE)>.98f,"round in front before it draws its breath");
        java.util.Arrays.fill(w,0);StandInhale.stances(StandInhale.DRAW,w,1);assertEquals(1,w[INHALE],1e-6,"its breath drawn in");
        java.util.Arrays.fill(w,0);StandInhale.stances(StandInhale.BEND_TO,w,1);assertEquals(1,w[SUCTION],1e-6,"bent into the suction");
        assertTrue(StandInhale.BEND_FROM<INHALE_CATCH/20f&&StandInhale.BEND_TO>INHALE_CATCH/20f,"it bends into the suction as it catches");
        float least=1;
        for(float t=StandInhale.BEND_TO;t<INHALE_LENGTH/20f;t+=.01f){java.util.Arrays.fill(w,0);StandInhale.stances(t,w,1);least=Math.min(least,w[SUCTION]);}
        assertEquals(1-StandInhale.HEAVE,least,.01,"it heaves through the hold");
        // how hard it sucks: a little as it draws its breath, full from the catch until it ends, nothing after
        assertEquals(0,StandInhale.suction(0,5),1e-6);
        assertTrue(StandInhale.suction(.25f,5)>.1f&&StandInhale.suction(.25f,5)<.4f,"the air begins to move");
        assertEquals(1,StandInhale.suction(INHALE_CATCH/20f+.02f,5),1e-3,"full as it catches");
        assertEquals(0,StandInhale.suction(5.2f,5),1e-6,"none once it ends");
    }
    @Test void itsMouthIsInFrontOfItsHeadWhereTheAirGoesIn(){
        // (under its scarf: it has no mouth; blocks ahead, to its right and up from its feet)
        float[] guard=StandInhale.MOUTH[GUARD],breath=StandInhale.MOUTH[INHALE],suction=StandInhale.MOUTH[SUCTION];
        assertTrue(suction[2]>1.3f&&suction[2]<1.9f,"at a head's height: "+suction[2]);
        assertTrue(suction[0]>.4f,"in front of it as it sucks: "+suction[0]);
        assertTrue(breath[0]<suction[0]-.4f,"thrown back as it draws its breath: "+breath[0]+" against "+suction[0]);
        assertTrue(breath[2]>suction[2],"and higher");
        assertEquals(0,suction[1],.05,"in the middle");
        // whom it holds are just in front of it: past its mouth, not inside the stand
        assertTrue(INHALE_AHEAD-StandInhale.AHEAD-suction[0]>.2f,"room for a body between its mouth and whom it holds");
        // it moves on with its head, no faster than its lunge into the suction (about 0.9 blocks in a fifth of a second)
        float[] last=StandInhale.mouth(0,5);double fastest=0;
        for(float t=FRAME;t<6;t+=FRAME){
            float[] m=StandInhale.mouth(t,5);double d=Math.sqrt(Math.pow(m[0]-last[0],2)+Math.pow(m[1]-last[1],2)+Math.pow(m[2]-last[2],2));
            assertTrue(d<.14,"its mouth jumps "+d+" blocks at "+t);last=m;fastest=Math.max(fastest,d);
        }
        System.out.println("its mouth's fastest move in a frame: "+fastest+" blocks");
    }
    @Test void itsSoundFadesOutAsItEndsAndOneOverBeforeItBeganNeverPlays(){
        var playing=new StandInhale.Sound(new Identifier("celestialfall","star_platinum_inhale"),.9f);
        playing.tick();assertEquals(.9f,playing.loudness(),1e-6);playing.fade();
        float lv=playing.loudness();
        for(int k=0;k<StandInhale.FADE;k++){
            assertTrue(playing.canPlay(),"still fading out, not cut off ("+k+")");assertFalse(playing.isDone());
            playing.tick();assertTrue(playing.loudness()<lv,"quieter every tick");lv=playing.loudness();
        }
        assertEquals(0,playing.loudness(),1e-6);assertTrue(playing.isDone());
        var early=new StandInhale.Sound(new Identifier("celestialfall","star_platinum_inhale"),.9f);
        early.fade();assertFalse(early.canPlay(),"over before it began: it never plays");
    }
    static List<String> bones()throws Exception{
        try(var in=StandInhaleTest.class.getResourceAsStream("/assets/celestialfall/geo/entity/star_platinum.geo.json")){
            var list=new java.util.ArrayList<String>();
            for(var b:com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    @Test void whomItHoldsAreDrawnInSmoothlyAndKeptBeforeItAsItsUserWalks(){
        // its user at the origin facing south (+z): whom it caught 3.3 blocks ahead, a little to the side
        double[] at={.6,3.3};double[] last=at.clone();double fastest=0;
        for(int k=1;k<=INHALE_PULL+40;k++){
            // its user walks on at half a walk (about a tenth of a block a tick) and turns a little
            double ux=0,uz=k>INHALE_PULL?(k-INHALE_PULL)*.11:0;float yaw=k>INHALE_PULL?(k-INHALE_PULL)*1.5f:0;
            double[] a=inhaleAnchor(ux,uz,yaw,0);
            double u=inhalePull(k/(double)INHALE_PULL);
            double[] p={at[0]+(a[0]-at[0])*u,at[1]+(a[1]-at[1])*u};
            if(k>=INHALE_PULL)p=a;
            double step=Math.hypot(p[0]-last[0],p[1]-last[1]);fastest=Math.max(fastest,step);
            assertTrue(step<StandInhale.STEP,"no fling: "+step+" blocks in tick "+k);
            last=p;
        }
        assertTrue(fastest<.5,"drawn in at most half a block a tick: "+fastest);
        double[] a=inhaleAnchor(0,0,0,0);assertEquals(0,a[0],1e-9);assertEquals(INHALE_AHEAD,a[1],1e-9,"held in front of its user");
    }
}
