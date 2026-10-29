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
        for(Vec3d eye:new Vec3d[]{new Vec3d(3,66,-4),new Vec3d(15,66,6),new Vec3d(30,66,10),new Vec3d(80,70,0)}){
            boolean in=eye.squaredDistanceTo(s.center.add(0,1.1,0))<StandTimeStop.REACH*StandTimeStop.REACH;
            StandTimeStop.Wave last=null;float firstFlash=-1,firstRim=-1,firstImpact=-1,mostImpact=0,mostCrack=0;
            for(int f=0;f<=(int)((MOVE()+StandTimeStop.FALL+.3f)/FRAME);f++){
                float t=f*FRAME;var w=StandTimeStop.wave(s,s.center,t,eye);
                if(t<StandTimeStop.STOP-StandTimeStop.IMPLODE-1e-4){assertNull(w,"no wave before space is drawn in ("+t+")");continue;}
                if(t>=MOVE()+StandTimeStop.FALL){assertNull(w,"gone once it has fallen back ("+t+")");continue;}
                assertNotNull(w,"a wave at "+t);
                if(t>=StandTimeStop.STOP&&firstFlash<0){firstFlash=w.flash();firstRim=w.rim();firstImpact=w.impact();}
                mostImpact=Math.max(mostImpact,w.impact());mostCrack=Math.max(mostCrack,w.crack());
                if(last!=null){
                    String at=" at "+t+" (an eye "+(in?"in":"out of")+" the stopped world)";
                    assertTrue(Math.abs(w.radius()-last.radius())<1.2f,"the wave jumps"+at+": "+last.radius()+" to "+w.radius());
                    assertTrue(Math.abs(w.flash()-last.flash())<.2f,"the flash jumps"+at);
                    assertTrue(Math.abs(w.outer()-last.outer())<.2f,"the far world jumps"+at);
                    assertTrue(Math.abs(w.negative()-last.negative())<.4f,"the negative jumps"+at);
                    assertTrue(Math.abs(w.rim()-last.rim())<.4f,"the rim jumps"+at);
                    assertTrue(Math.abs(w.shell()-last.shell())<.3f,"the bubble jumps"+at+": "+last.shell()+" to "+w.shell());
                    assertTrue(Math.abs(w.crack()-last.crack())<.35f,"its cracks jump"+at);
                    assertTrue(Math.abs(w.impact()-last.impact())<.55f,"the impact frame jumps"+at);
                    assertTrue(Math.abs(w.refract()-last.refract())<.4f,"the bend jumps"+at);
                    assertTrue(Math.abs(w.vignette()-last.vignette())<.1f,"the vignette jumps"+at);
                }
                last=w;
            }
            assertTrue(firstFlash<.2f,"the flash comes in over frames: "+firstFlash);
            // (its front starts on its user: its glow comes in over frames, or it lights their whole body up for one)
            assertTrue(firstRim<.4f,"the front's glow comes in over frames: "+firstRim);
            assertTrue(firstImpact<.55f,"the impact frame comes in over frames: "+firstImpact);
            double far=eye.distanceTo(s.center.add(0,1.1,0));
            if(far<StandTimeStop.REACH*1.5)assertTrue(mostImpact>.5f,"an impact frame as time stops ("+far+" blocks away)");
            if(far>=StandTimeStop.REACH*3)assertEquals(0,mostImpact,1e-6,"no impact frame three times its reach away");
            assertEquals(1,mostCrack,1e-4,"cracked all over as it breaks");
            var held=StandTimeStop.wave(s,s.center,StandTimeStop.STOP+StandTimeStop.SPREAD+1,eye);
            assertEquals(StandTimeStop.REACH,held.radius(),1e-4,"spread over all it stopped");
            assertEquals(0,held.negative(),1e-6,"no negative once it has spread");
            assertEquals(0,held.crack(),1e-6,"no cracks while time is stopped");
            assertEquals(0,held.impact(),1e-6,"no impact frame while time is stopped");
            assertEquals(StandTimeStop.GLASS,held.outer(),1e-6,"what is seen through the bubble half stopped, from in it or out of it");
            assertEquals(StandTimeStop.SHELL,held.shell(),1e-6,"the bubble shows, fainter, while time is stopped");assertTrue(StandTimeStop.SHELL<.8f);
            assertEquals(in?.35f:0,held.vignette(),1e-6,"a vignette for an eye in it only");
            var cracking=StandTimeStop.wave(s,s.center,MOVE()-.1f,eye);
            assertTrue(cracking.crack()>.8f&&cracking.shell()>held.shell(),"cracked and brighter just before time moves");
            assertEquals(0,StandTimeStop.wave(s,s.center,MOVE()-StandTimeStop.CRACK-.05f,eye).crack(),1e-6,"its cracks start as \"toki wa ugoki dasu\" does");
        }
        assertNull(StandTimeStop.wave(1.5f,Vec3d.ZERO),"no time stop known, no wave");
    }
    @Test void spaceIsDrawnInToItsUserJustBeforeTimeStops(){
        // (before time stops: the server has not yet said so, its user's feet where they stand)
        var s=stop(0);Vec3d feet=new Vec3d(0,64,0),eye=new Vec3d(4,66,0);
        var first=StandTimeStop.wave(s,feet,StandTimeStop.STOP-StandTimeStop.IMPLODE+1e-3f,eye);
        assertNotNull(first);assertTrue(first.radius()>StandTimeStop.IMPLODE_R-.2f,"it starts wide: "+first.radius());
        assertTrue(first.shell()<.1f&&first.rim()<.1f&&first.negative()<.1f,"coming in over frames");
        float last=first.radius();
        for(float t=StandTimeStop.STOP-StandTimeStop.IMPLODE+FRAME;t<StandTimeStop.STOP;t+=FRAME){
            var w=StandTimeStop.wave(s,feet,t,eye);if(w==null)continue;
            assertTrue(w.radius()<=last+1e-4,"it closes in at "+t);last=w.radius();
            assertTrue(w.refract()<=0,"the world bends in as it closes");assertEquals(0,w.grade(),1e-6,"nothing stopped yet");
        }
        assertTrue(last<.3f,"closed on its user as time stops: "+last);
        var closed=StandTimeStop.wave(s,feet,StandTimeStop.STOP-1e-3f,eye);
        assertTrue(closed.rim()<.05f&&closed.shell()<.05f,"its glow gone as it closes on its user (it would light them up)");
        // the server's word late: held closed, then the wave spreads; cut short as it was called: nothing
        assertTrue(StandTimeStop.wave(s,feet,StandTimeStop.STOP+.1f,eye).radius()<.3f,"held closed until the server says time stopped");
        assertNull(StandTimeStop.wave(s,feet,StandTimeStop.STOP+.7f,eye),"none if it never says so");
        assertNull(StandTimeStop.wave(s,null,StandTimeStop.STOP-.1f,eye),"none where its user is not seen");
    }
    @Test void theClockSpinsFasterAndFasterAndStopsDeadAsTimeStops(){
        var s=stop(0);s.stopped=true;s.center=Vec3d.ZERO;
        var shown=new StandTimeStopFx.Shown(s,0,Vec3d.ZERO,null,7,StandTimeStop.MOVE,0);
        double last=StandTimeStopFx.minute(shown,0),lastSpeed=0;
        for(float t=FRAME;t<=StandTimeStop.STOP;t+=FRAME){
            double a=StandTimeStopFx.minute(shown,t),speed=(a-last)/FRAME;
            assertTrue(speed>=lastSpeed-1e-6,"faster and faster at "+t);lastSpeed=speed;last=a;
        }
        assertTrue(lastSpeed/(2*Math.PI)>3,"spinning fast as time stops: "+lastSpeed/(2*Math.PI)+" turns a second");
        double stopped=StandTimeStopFx.minute(shown,StandTimeStop.STOP);
        for(float t=StandTimeStop.STOP+.25f;t<StandTimeStop.MOVE-StandTimeStop.CRACK;t+=.1f)
            assertEquals(stopped,StandTimeStopFx.minute(shown,t),1e-3,"still while time is stopped ("+t+")");
        assertNotEquals(stopped,StandTimeStopFx.minute(shown,StandTimeStop.MOVE-.2f),"trembling as time is about to move");
        assertTrue(StandTimeStopFx.minute(shown,StandTimeStop.MOVE+.5f)>stopped+1,"running on once it moves");
        // never a jump between frames (only as fast as it turns)
        last=StandTimeStopFx.minute(shown,0);
        for(float t=FRAME;t<StandTimeStop.MOVE+1.5f;t+=FRAME){double a=StandTimeStopFx.minute(shown,t);assertTrue(Math.abs(a-last)<Math.toRadians(30),"the hand jumps at "+t);last=a;}
    }
    @Test void whatTheWavePassesOverHangsWhereItWasUntilTimeMoves(){
        // a mote is lit as the front reaches it: when the wave's radius reaches its distance
        for(double d=0;d<=StandTimeStop.REACH;d+=.5){
            float at=StandTimeStopFx.reaches(d);
            assertEquals(d,StandTimeStopFx.spread(at),.05,"the front reaches "+d+" blocks at "+at);
        }
        assertEquals(StandTimeStop.STOP,StandTimeStopFx.reaches(0),1e-6);assertEquals(StandTimeStop.STOP+StandTimeStop.SPREAD,StandTimeStopFx.reaches(StandTimeStop.REACH),1e-4);
        var s=stop(0);s.stopped=true;s.center=Vec3d.ZERO;
        var shown=new StandTimeStopFx.Shown(s,0,Vec3d.ZERO,null,7,StandTimeStop.MOVE,0);
        int in=0;
        for(int k=0;k<StandTimeStopFx.MOTES;k++){var p=StandTimeStopFx.mote(shown,k);if(p==null)continue;in++;
            assertTrue(p.y>.2&&p.distanceTo(new Vec3d(0,1.1,0))<StandTimeStop.REACH,"in the stopped world, above the ground");}
        assertTrue(in>StandTimeStopFx.MOTES*.8,"most of the motes in it: "+in);
    }
    @Test void itsBubbleBreaksIntoShardsFlungOutWithoutAJump(){
        var s=stop(0);s.stopped=true;s.center=Vec3d.ZERO;
        var shown=new StandTimeStopFx.Shown(s,0,Vec3d.ZERO,null,7,StandTimeStop.MOVE,0);Vec3d c=new Vec3d(0,1.1,0);
        for(int k=0;k<StandTimeStopFx.SHARDS;k++){
            Vec3d[] home=StandTimeStopFx.shard(shown,k,0,false);
            Vec3d mid=home[0].add(home[1]).add(home[2]).multiply(1/3.0);
            assertEquals(StandTimeStop.REACH,mid.distanceTo(c),.6,"shard "+k+" begins on the bubble");
            assertTrue(mid.y>-.2,"above the ground");
            Vec3d last=mid,early=null;
            for(float age=FRAME;age<1.3f;age+=FRAME){
                Vec3d[] q=StandTimeStopFx.shard(shown,k,age,false);Vec3d m=q[0].add(q[1]).add(q[2]).multiply(1/3.0);
                assertTrue(m.distanceTo(last)<.25,"shard "+k+" jumps at "+age);last=m;
                if(early==null&&age>=.3f)early=m;
            }
            assertTrue(early.distanceTo(c)>StandTimeStop.REACH+.3,"flung out ("+k+")");
            assertTrue(last.y<early.y,"and falling ("+k+")");
        }
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
    @Test void whatMovesInTheStoppedTimeKeepsItsColoursUntilItStops(){
        // (2.75.1) its user all through it; once time has stopped, whom it did not stop: a Star Platinum's user until their
        // two seconds run out (then theirs go over a few frames, at once on their own still), anyone it passed by until
        // the colour has come back; whom it stopped never, nor as time moves again (theirs come back with the world's)
        StandTimeStop.STOPS.clear();StandTimeStop.STILL.clear();StandTimeStop.DRAINING.clear();
        var s=stop(0);StandTimeStop.STOPS.put(s.user,s);
        UUID nobody=UUID.randomUUID();int user=1,stopped=2,late=3,free=4;
        float lateAt=(TS_WIND+TS_RESIST)/20f,end=StandTimeStop.MOVE+StandTimeStop.FALL;
        float last=1;boolean drained=false,released=false,shown=false;
        try{
            for(int f=0;f<=(int)((end+.3f)/FRAME);f++){
                float t=f*FRAME;
                // the server's word that the two seconds are up, heard before the frame's ticks (as the client runs its tasks)
                if(t>=lateAt&&!s.held.contains(late)){StandTimeStop.lastDelta=clock(t-FRAME);StandTimeStop.holdLate(s,late);}
                float delta=clock(t);
                boolean was=s.stopped;words(s,TS_WIND+TS_LENGTH,false);
                if(s.stopped&&!was){s.held.add(stopped);StandTimeStop.STILL.put(stopped,delta);}
                if(!Float.isNaN(s.ended)&&!released){StandTimeStop.release(s);released=true;}
                float u=StandTimeStop.colour(s.user,user,delta,false),st=StandTimeStop.colour(nobody,stopped,delta,false);
                float l=StandTimeStop.colour(nobody,late,delta,false),fr=StandTimeStop.colour(nobody,free,delta,false);
                boolean wave=StandTimeStop.showing(delta);shown|=wave;
                // (not on the very frame a boundary falls on: which side it is is rounding's)
                boolean edge=Math.abs(t-end)<.002f||Math.abs(t-(StandTimeStop.STOP-StandTimeStop.IMPLODE))<.002f;
                if(!edge){
                    assertEquals(t>=StandTimeStop.STOP-StandTimeStop.IMPLODE&&t<end,wave,"the wave shows at "+t);
                    assertEquals(t<end?1:0,u,"its user keeps theirs while it shows, at "+t);
                }
                assertEquals(0,st,"whom it stopped take its look at "+t);
                if(t<StandTimeStop.STOP-1e-4){assertEquals(0,l,"time not stopped yet at "+t);assertEquals(0,fr,"time not stopped yet at "+t);continue;}
                if(!edge)assertEquals(t<end?1:0,fr,"whom it passed by keep theirs until the colour comes back, at "+t);
                if(t<lateAt-1e-4){assertEquals(1,l,"a Star Platinum's user keeps theirs for their two seconds, at "+t);continue;}
                // stopped late: theirs go over a few frames, without a jump, and at once on their own still
                if(Float.isNaN(s.ended))assertEquals(0,StandTimeStop.colour(nobody,late,delta,true),"gone on the still at "+t);
                assertTrue(l<=last+1e-6&&last-l<.3f,"theirs go without a jump at "+t+": "+last+" to "+l);last=l;
                if(t>=lateAt+StandTimeStop.DRAIN+FRAME)assertEquals(0,l,"and are gone at "+t);
                drained|=l==0;
            }
        }finally{StandTimeStop.STOPS.clear();StandTimeStop.STILL.clear();StandTimeStop.DRAINING.clear();}
        assertTrue(drained&&released&&shown);
        assertEquals(TS_RESIST,40,"two seconds");
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
