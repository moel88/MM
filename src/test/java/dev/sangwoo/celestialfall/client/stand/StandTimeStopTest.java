package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.stand.StandRushTest.FRAME;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum's time stop (2.67.0) as a client draws it: the stand's call smooth at 60 fps (it comes round in front,
 * gathers itself, flings itself open as time stops, holds it and goes back), cut short at any moment, after a punch
 * stopped short and with a punch taking over from it as its user strikes in the stopped time; its stances in their
 * order; and the wave spreading and falling back without a jump, its flashes coming in over frames, the stopped world
 * whole once it has spread and its colour back once time moves.
 */
class StandTimeStopTest {
    /** Sets this client's clock (the time stop's, the rush's, the inhale's) to {@code t} seconds; returns the frame's part of a tick. */
    static float clock(float t){long whole=(long)Math.floor(t*20+1e-4);StandTimeStop.ticks=whole;StandRush.ticks=whole;StandInhale.ticks=whole;return Math.max(0,t*20-whole);}
    /** A time stop heard as it is called {@code start} ticks into this client's clock. */
    static StandTimeStop.Stop stop(long start){return new StandTimeStop.Stop(UUID.randomUUID(),start,0,start);}
    /** The server's words as they come: time stops as it is due; it moves again at {@code end} ticks on its clock, or its call is cut short there if {@code cut}. */
    static void words(StandTimeStop.Stop s,long end,boolean cut){
        long in=s.ticksIn();
        if(cut){if(Float.isNaN(s.ended)&&in>=end){s.ended=end/20f;s.cut=true;}return;}
        if(!s.stopped&&in>=TS_WIND){s.stopped=true;s.center=Vec3d.ZERO;}
        if(Float.isNaN(s.ended)&&in>=end)s.ended=end/20f;
    }

    @Test void itGathersItselfOpensAsTimeStopsAndGoesBackWithoutAJumpAt60fps()throws Exception{
        var body=new StandRushTest.Watch(StandInhaleTest.bones());var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        var s=stop(0);float lastEngage=0,lastLunge=0;boolean gathered=false,open=false,front=false;
        for(int f=0;f<=(int)((StandTimeStop.HOLD+StandTimeStop.SETTLE+StandTimeStop.RETURN+.3f)/FRAME);f++){
            float t=f*FRAME,delta=clock(t);words(s,TS_WIND+TS_LENGTH,false);
            var c=StandTimeStop.call(s,0,delta,0,shape);StandRushTest.look(c,look);
            if(c!=null){
                assertTrue(Math.abs(c.engage-lastEngage)<.25f,"comes round in a jump at "+t);lastEngage=c.engage;
                assertTrue(Math.abs(c.lunge-lastLunge)<.03f,"trembles in a jump at "+t);lastLunge=c.lunge;
                if(Math.abs(t-1.2f)<FRAME/2){gathered=c.weights[WORLD_GATHER]>.99f;front=c.engage>.99f;}
                if(Math.abs(t-StandTimeStop.STOP)<FRAME/2)open=c.weights[WORLD]>.99f;
            }
            body.see(look,t,"called");
        }
        assertTrue(front,"in front of its user as it gathers itself");
        assertTrue(gathered,"gathered as its user shouts \"Star Platinum\"");
        assertTrue(open,"wide open as time stops");
        assertNull(StandTimeStop.call(s,0,clock(StandTimeStop.HOLD+StandTimeStop.SETTLE+StandTimeStop.RETURN+.05f),0,shape),"gone back to its place");
        System.out.println("the time stop's fastest turn in a frame: "+body.fastest+" ("+body.where+")");
    }
    @Test void itOpensFasterAndFasterLikeABlow(){
        float[] w=new float[NAMES.size()];
        StandTimeStop.stances(StandTimeStop.OPEN_FROM+(StandTimeStop.OPEN-StandTimeStop.OPEN_FROM)/2,w);
        assertTrue(w[WORLD]<.4f,"less than half open halfway: "+w[WORLD]);
        StandTimeStop.stances(StandTimeStop.OPEN,w);assertEquals(1,w[WORLD],1e-6,"open just before time stops");
        StandTimeStop.stances(.02f,w);assertEquals(1,w[GUARD],1e-6,"from its guard");
        assertTrue(StandTimeStop.OPEN<StandTimeStop.STOP&&StandTimeStop.OPEN_FROM>StandTimeStop.GATHER,"gathered, then open, before time stops");
    }
    @Test void cutShortAtAnyMomentItTurnsBackToItsGuardAndGoesBack()throws Exception{
        var names=StandInhaleTest.bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        for(int cut=1;cut<=TS_WIND;cut+=2){
            var s=stop(0);var body=new StandRushTest.Watch(names);float lastEngage=0;String what="cut short "+cut+" ticks in";
            for(int f=0;f<=(int)((cut/20f+StandTimeStop.SETTLE+StandTimeStop.RETURN+.3f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);words(s,cut,true);
                var c=StandTimeStop.call(s,0,delta,0,shape);StandRushTest.look(c,look);
                if(c!=null){assertTrue(Math.abs(c.engage-lastEngage)<.25f,"goes back in a jump at "+t+" ("+what+")");lastEngage=c.engage;}
                body.see(look,t,what);
            }
            StandTimeStop.call(s,0,clock(cut/20f+StandTimeStop.SETTLE),0,shape);
            assertEquals(1,shape.weights[GUARD],1e-4,"back in its guard ("+what+")");assertTrue(shape.over>=1-1e-4,"turned back ("+what+")");
            assertNull(StandTimeStop.call(s,0,clock(cut/20f+StandTimeStop.SETTLE+StandTimeStop.RETURN+.05f),0,shape),"gone back ("+what+")");
        }
    }
    @Test void itTakesOverFromAPunchStoppedShortAndPunchesInTheStoppedTimeTakeOverFromIt()throws Exception{
        // H as a punch is in flight: the server stops the punch and calls the time stop; time stops, and its user strikes
        // while the stand still holds itself open (a jab, then the combo on), or once it has gone back
        var names=StandInhaleTest.bones();var look=new StarPlatinumLook();var into=new StandPunch.Shape();var call=new StandPunch.Shape();
        for(int combo=1;combo<=4;combo++)for(int at=1;at<=9;at+=2)for(float jabAfter:new float[]{.1f,.6f,1.4f}){
            float hAt=at/20f;long startTick=Math.round(hAt*20);float callAt=startTick/20f,jabAt=callAt+StandTimeStop.STOP+jabAfter;
            var before=new StandPunch.Punch(combo,0,Float.NaN,Float.NaN).stop(hAt);var jab=new StandPunch.Punch(1,0,Float.NaN,Float.NaN);
            var s=stop(startTick);var body=new StandRushTest.Watch(names);float lastEngage=0;
            String what="punch "+combo+" stopped by H at "+hAt+", a jab "+jabAfter+" s after time stops";
            for(int f=0;f<=(int)((jabAt+1.4f)/FRAME);f++){
                float t=f*FRAME,delta=clock(t);
                if(t>=callAt)words(s,TS_WIND+TS_LENGTH,false);
                StandPunch.Shape punch=null;float pt;
                if(t>=jabAt){pt=t-jabAt;if(pt<=StandPunch.length(jab))punch=StandPunch.at(jab,pt,before,t,into);}
                else{pt=t;if(t<=StandPunch.length(before))punch=StandPunch.at(before,t,null,0,into);}
                StandPunch.Shape shown=punch;
                if(t>=callAt){var mine=StandTimeStop.call(s,0,delta,0,call);shown=StandRush.combine(punch,pt,mine,s.age(delta),into);}
                StandRushTest.look(shown,look);
                if(shown!=null){assertTrue(Math.abs(shown.engage-lastEngage)<.25f,"in front in a jump at "+t+" ("+what+")");lastEngage=shown.engage;}
                body.see(look,t,what);
            }
        }
    }
    @Test void itsAuraFlaresAsItOpensAndDiesDownAsTheWaveSpreads(){
        var s=stop(0);StandTimeStop.STOPS.put(s.user,s);
        try{
            float last=0,most=0;
            for(int f=0;f<=(int)(4/FRAME);f++){
                float t=f*FRAME,v=StandTimeStop.flare(s.user,clock(t));
                assertTrue(Math.abs(v-last)<.1f,"flares in a jump at "+t);last=v;most=Math.max(most,v);
            }
            assertEquals(1,StandTimeStop.flare(s.user,clock(StandTimeStop.STOP)),1e-4,"brightest as time stops");
            assertEquals(0,StandTimeStop.flare(s.user,clock(StandTimeStop.STOP+StandTimeStop.SPREAD+.4f)),1e-4,"died down once the wave has spread");
            assertEquals(0,StandTimeStop.flare(s.user,clock(1)),1e-4,"not as it gathers itself");
        }finally{StandTimeStop.STOPS.remove(s.user);}
    }
    @Test void theWaveSpreadsOverTheStoppedWorldAndFallsBackWithoutAJump(){
        var s=stop(0);s.stopped=true;s.center=new Vec3d(0,64,0);
        for(Vec3d eye:new Vec3d[]{new Vec3d(3,66,-4),new Vec3d(30,66,10),new Vec3d(80,70,0)}){
            boolean in=eye.squaredDistanceTo(s.center.add(0,1.1,0))<StandTimeStop.REACH*StandTimeStop.REACH;
            StandTimeStop.Wave last=null;float firstFlash=-1;
            for(int f=0;f<=(int)((MOVE()+StandTimeStop.FALL+.3f)/FRAME);f++){
                float t=f*FRAME;var w=StandTimeStop.wave(s,t,eye);
                if(t<StandTimeStop.STOP-1e-4){assertNull(w,"no wave before time stops ("+t+")");continue;}
                if(t>=MOVE()+StandTimeStop.FALL){assertNull(w,"gone once it has fallen back ("+t+")");continue;}
                assertNotNull(w,"a wave at "+t);
                if(firstFlash<0)firstFlash=w.flash();
                if(last!=null){
                    String at=" at "+t+" (an eye "+(in?"in":"out of")+" the stopped world)";
                    assertTrue(Math.abs(w.radius()-last.radius())<2.6f,"the wave jumps"+at+": "+last.radius()+" to "+w.radius());
                    assertTrue(Math.abs(w.flash()-last.flash())<.2f,"the flash jumps"+at);
                    assertTrue(Math.abs(w.outer()-last.outer())<.2f,"the far world jumps"+at);
                    assertTrue(Math.abs(w.negative()-last.negative())<.4f,"the negative jumps"+at);
                    assertTrue(Math.abs(w.rim()-last.rim())<.4f,"the rim jumps"+at);
                }
                last=w;
            }
            assertTrue(firstFlash<.2f,"the flash comes in over frames: "+firstFlash);
            var held=StandTimeStop.wave(s,StandTimeStop.STOP+StandTimeStop.SPREAD+1,eye);
            assertEquals(StandTimeStop.REACH,held.radius(),1e-4,"spread over all it stopped");
            assertEquals(0,held.negative(),1e-6,"no negative once it has spread");
            assertEquals(in?1:0,held.outer(),1e-6,"the whole world stopped for an eye in it, only what is in it for one out of it");
        }
        assertNull(StandTimeStop.wave(1.5f,Vec3d.ZERO),"no time stop known, no wave");
    }
    @Test void timeStopsAsTheShoutEndsAndMovesAsTheEndEnds()throws Exception{
        // the shout's sound starts a tick on, its boom 38 ticks into it (tools/stand/cut_timestop.py): time stops there
        assertEquals(TS_SOUND+38,TS_WIND,"time stops on the boom");
        assertEquals(TS_WIND/20f,StandTimeStop.STOP,1e-6);
        assertEquals((TS_WIND+TS_LENGTH)/20f,MOVE(),1e-6);
        assertEquals(100,TS_LENGTH,"five seconds");
        // "toki wa ugoki dasu" ends 1.275 s into its sound: started TS_RESUME_LEAD ticks before time moves, within half a tick of it
        assertEquals(1.275,TS_RESUME_LEAD/20.0,.026,"it ends as time moves again");
        double cast=seconds("star_platinum_timestop");
        double end=seconds("star_platinum_timestop_end");
        assertTrue(TS_SOUND/20.0+cast>StandTimeStop.STOP+TS_SETTLE,"the cast sound runs past the wave: "+cast+" s");
        assertTrue(TS_SOUND/20.0+cast<StandTimeStop.MOVE-TS_RESUME_LEAD/20.0,"and is over before \"toki wa ugoki dasu\"");
        assertTrue(end>TS_RESUME_LEAD/20.0,"the end sound rings out a little as time moves: "+end+" s");
        // the wave spreads over the rush of its sound: as long as the rush lasts (it drops away 0.935 s on)
        assertEquals(TS_SETTLE,StandTimeStop.SPREAD,1e-6);
    }
    private static float MOVE(){return StandTimeStop.MOVE;}
    /** How long an Ogg Vorbis sound of the mod's is (seconds): its last page's sample count at its rate (as StarPlatinumRulesTest reads them). */
    static double seconds(String name)throws Exception{
        byte[] b;try(var in=StandTimeStopTest.class.getResourceAsStream("/assets/celestialfall/sounds/"+name+".ogg")){assertNotNull(in,name);b=in.readAllBytes();}
        int rate=-1;long samples=-1;
        for(int i=0;i+27<b.length;i++){
            if(b[i]!='O'||b[i+1]!='g'||b[i+2]!='g'||b[i+3]!='S')continue;
            long granule=0;for(int k=7;k>=0;k--)granule=granule<<8|(b[i+6+k]&255);
            if(granule!=-1)samples=Math.max(samples,granule);
            int segments=b[i+26]&255,body=i+27+segments;
            if(rate<0&&body+16<b.length&&b[body]==1&&b[body+1]=='v')rate=(b[body+12]&255)|(b[body+13]&255)<<8|(b[body+14]&255)<<16|(b[body+15]&255)<<24;
        }
        assertTrue(rate>0&&samples>0,name);
        return samples/(double)rate;
    }
}
