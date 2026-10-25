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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Star Platinum's punches (2.54.0): smooth at 60 fps through a mashed combo, landing on their contacts, the stand coming
 * round and going back. 2.59.0: the heavy punch (R), alone, taking over from a punch stopped short at any moment, and
 * stopped short itself.
 */
class StandPunchTest {
    static final float FRAME=1/60f;
    /** A mashed combo: each punch as soon as it may (seconds). */
    static final float[] STARTS={0,StarPlatinumRules.STEP[1]/20f,(StarPlatinumRules.STEP[1]+StarPlatinumRules.STEP[2])/20f,(StarPlatinumRules.STEP[1]+StarPlatinumRules.STEP[2]+StarPlatinumRules.STEP[3])/20f};
    private List<String> bones()throws Exception{
        try(var in=getClass().getResourceAsStream("/assets/celestialfall/geo/entity/star_platinum.geo.json")){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    static StandPunch.Punch punch(int k){return new StandPunch.Punch(k,0,Float.NaN,Float.NaN);}
    /** How far (degrees) a bone turns from one pose to another: the angle between the two whole turns, its own turn in the model included. */
    static float turn(String bone,float[] a,float[] b){
        var rest=StarPlatinumPoses.rests().getOrDefault(bone,new float[6]);
        double[] qa=StarPlatinumPoses.quat(rest[0]+a[0],rest[1]+a[1],rest[2]+a[2],new double[4]),qb=StarPlatinumPoses.quat(rest[0]+b[0],rest[1]+b[1],rest[2]+b[2],new double[4]);
        double dot=Math.abs(qa[0]*qb[0]+qa[1]*qb[1]+qa[2]*qb[2]+qa[3]*qb[3]);
        return (float)Math.toDegrees(2*Math.acos(Math.min(1,dot)));
    }
    /** The shape at {@code t} seconds into a combo whose punches start at {@code starts}, as the client takes the newest and the one before. */
    static StandPunch.Shape at(float t,float[] starts,StandPunch.Shape into){
        int n=-1;for(int i=0;i<starts.length;i++)if(t>=starts[i])n=i;
        if(n<0)return null;
        return StandPunch.at(punch(n+1),t-starts[n],n>0?punch(n):null,n>0?t-starts[n-1]:0,into);
    }
    /** The look drawn: at rest, taken over by the punches as far as the stand has come round (as StandPresence puts them on). */
    static void look(StandPunch.Shape s,StarPlatinumLook look){
        look.only(StarPlatinumStance.IDLE);if(s==null)return;
        if(s.engage>0)s.onto(look);
        look.wind=.35f*s.engage;look.gust=s.gust;
    }

    @Test void aMashedComboMovesWithoutAJumpAt60fps()throws Exception{
        var poses=new StarPlatinumPoses();var names=bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        float[][] last=new float[names.size()][];float[] p=new float[6];float fastest=0;String where="";
        float lastEngage=0,lastYaw=0;
        for(int f=0;f<=(int)(5.5f/FRAME);f++){
            float t=f*FRAME;var s=at(t,STARTS,shape);look(s,look);
            float sum=0,under=0;for(int i=0;i<look.weights.length;i++){sum+=look.weights[i];under+=look.under[i];}
            assertEquals(1,sum,1e-4,"weights at "+t);if(look.over<1)assertEquals(1,under,1e-4,"the pose under at "+t);
            if(s!=null){
                assertTrue(Math.abs(s.engage-lastEngage)<.25f,"comes round in a jump at "+t);
                lastEngage=s.engage;
            }
            for(int b=0;b<names.size();b++){
                poses.pose(names.get(b),look,t,p);
                if(last[b]!=null){
                    float d=turn(names.get(b),last[b],p);
                    if(d>fastest){fastest=d;where=names.get(b)+" at "+t;}
                    // a punch is fast (2.55.0: an elbow snapping straight through some 140 degrees in a tenth of a
                    // second, at its fastest in the frame it lands, which five afterimages smear), but nothing ever jumps
                    assertTrue(d<40,names.get(b)+" turns "+d+" degrees in a frame at "+t);
                }
                last[b]=p.clone();
            }
        }
        assertTrue(fastest>25,"the punches are fast: "+fastest+" ("+where+")");
    }
    @Test void aComboAtAnyPaceMovesWithoutAJump()throws Exception{
        // the next punch late by up to its grace (StarPlatinumRules.GRACE): each takes over from wherever the last one is
        var poses=new StarPlatinumPoses();var names=bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        int[][] lates={{1,1,1},{2,3,4},{4,0,8},{6,9,2},{10,13,17},{0,6,0},{3,3,12}};
        for(int[] late:lates){
            float[] starts=new float[4];int tick=0;
            for(int k=1;k<4;k++){tick+=StarPlatinumRules.STEP[k]+late[k-1];starts[k]=tick/20f;}
            float[][] last=new float[names.size()][];float[] p=new float[6];
            for(int f=0;f<=(int)((starts[3]+2.6f)/FRAME);f++){
                float t=f*FRAME;look(at(t,starts,shape),look);
                for(int b=0;b<names.size();b++){
                    poses.pose(names.get(b),look,t,p);
                    if(last[b]!=null){float d=turn(names.get(b),last[b],p);assertTrue(d<40,names.get(b)+" turns "+d+" degrees in a frame at "+t+" (late "+java.util.Arrays.toString(late)+")");}
                    last[b]=p.clone();
                }
            }
        }
    }
    @Test void aPunchStoppedShortAtAnyMomentGoesBackToItsGuardWithoutAJump()throws Exception{
        // its user struck, or E asking for the rush (2.57.0): at any moment of any punch, the one before it taking over too
        var poses=new StarPlatinumPoses();var names=bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        for(int combo=1;combo<=4;combo++)for(int stop=1;stop<=StarPlatinumRules.LENGTH[combo];stop++)for(boolean after:new boolean[]{false,true}){
            float at=stop/20f;var now=punch(combo).stop(at);
            // with the one before it still taking over (the next punch came as soon as it might), or none before it
            var before=after&&combo>1?punch(combo-1):null;float lead=before==null?0:StarPlatinumRules.STEP[combo-1]/20f;
            float[][] last=new float[names.size()][];float[] p=new float[6];
            for(int f=0;f<=(int)((at+.6f)/FRAME);f++){
                float t=f*FRAME;
                var s=StandPunch.at(t<at?punch(combo):now,t,before,t+lead,shape);look(s,look);
                for(int b=0;b<names.size();b++){
                    poses.pose(names.get(b),look,t,p);
                    if(last[b]!=null){float d=turn(names.get(b),last[b],p);assertTrue(d<40,names.get(b)+" turns "+d+" degrees in a frame at "+t+" (punch "+combo+" stopped at "+at+(before==null?"":", after punch "+(combo-1))+")");}
                    last[b]=p.clone();
                }
            }
            StandPunch.at(now,at+StarPlatinumRules.STOP_RETURN/20f,before,at+StarPlatinumRules.STOP_RETURN/20f+lead,shape);
            assertEquals(1,shape.weights[StarPlatinumStance.GUARD],1e-6);assertEquals(1,shape.over,1e-6,"back in its guard "+StarPlatinumRules.STOP_RETURN+" ticks after it stopped");
        }
    }
    @Test void aShoutFadesOutWhenTheNextComes(){
        var shout=new StandAttack.Shout(new net.minecraft.util.Identifier("celestialfall","star_platinum_ora_3"),.8f);
        shout.tick();assertEquals(.8f,shout.loudness(),1e-6,"full until told to fade");assertFalse(shout.isDone());
        shout.fade();
        float last=shout.loudness();
        for(int i=0;i<StandAttack.FADE;i++){
            assertFalse(shout.isDone(),"still fading");
            shout.tick();assertTrue(shout.loudness()<last,"quieter every tick");last=shout.loudness();
        }
        assertEquals(0,shout.loudness(),1e-6);assertTrue(shout.isDone(),"gone once faded");
        shout.fade();assertTrue(shout.isDone(),"a second fade changes nothing");
    }
    @Test void everyPunchLandsOnItsContactAndHoldsStillThere(){
        var shape=new StandPunch.Shape();
        for(int k=1;k<=StarPlatinumRules.HEAVY;k++){
            float hit=StandPunch.HIT[k];
            assertEquals(StarPlatinumRules.CONTACT[k]/20f,hit,1e-6);
            StandPunch.at(punch(k),hit,null,0,shape);
            assertEquals(1,shape.weights[StandPunch.STRIKES[k]],1e-5,"punch "+k+" thrown at its contact");
            // the hit-stop: still for its frames
            var held=StandPunch.at(punch(k),hit+StandPunch.FREEZE[k]*.9f,null,0,new StandPunch.Shape());
            for(int i=0;i<shape.weights.length;i++)assertEquals(shape.weights[i],held.weights[i],1e-5,"punch "+k+" still through its hit-stop");
            // still loading a moment before: the strike is quickest as it lands
            StandPunch.at(punch(k),hit-.05f,null,0,shape);
            assertTrue(shape.weights[StandPunch.STRIKES[k]]<.6f,"punch "+k+" lands at the end of its strike: "+shape.weights[StandPunch.STRIKES[k]]);
            // and back in the guard once it is over, if no next one comes (2.56.0: a next one may come before, and take over)
            StandPunch.at(punch(k),StarPlatinumRules.LENGTH[k]/20f,null,0,shape);assertEquals(1,shape.weights[StarPlatinumStance.GUARD],1e-4,"punch "+k+" back in its guard");
        }
    }
    @Test void itComesRoundToTheFrontAndGoesBackOnceThePunchesStop(){
        var shape=new StandPunch.Shape();
        assertEquals(0,at(0,STARTS,shape).engage,1e-6,"at its user's shoulder as the first punch starts");
        assertTrue(StandPunch.RISE<StandPunch.HIT[1],"in front before the first lands");
        assertEquals(1,at(StandPunch.RISE,STARTS,shape).engage,1e-6,"in front before the first lands");
        for(float t=StandPunch.RISE;t<STARTS[3]+1.2f;t+=.05f)assertEquals(1,at(t,STARTS,shape).engage,1e-6,"in front through the combo at "+t);
        float end=STARTS[3]+StandPunch.length(punch(4));
        assertEquals(0,at(end,STARTS,shape).engage,1e-4,"back at its shoulder once it is over");
        // a punch stopped short (its user struck) goes back to the guard and the stand goes back soon after
        var stopped=punch(2).stop(.15f);
        StandPunch.at(stopped,.15f+.25f,null,0,shape);assertEquals(1,shape.weights[StarPlatinumStance.GUARD],1e-4);
        assertEquals(0,StandPunch.at(stopped,StandPunch.length(stopped)-.2f,null,0,shape).engage,1e-3);
        assertEquals(0,StandPunch.ghost(stopped,StandPunch.HIT[2]),1e-6,"no afterimages for a punch that never landed");
    }
    @Test void itPunchesFromRightInFrontOfItsUser(){
        var shape=new StandPunch.Shape();
        for(int k=1;k<=StarPlatinumRules.HEAVY;k++){
            // on the punch's line, right in front of its user, as each blow lands
            StandPunch.at(punch(k),StandPunch.HIT[k],null,0,shape);
            assertEquals(0,shape.side,1e-6,"punch "+k+" on its user's line");
            assertEquals(StandPunch.AHEAD,shape.ahead,1e-6);
            // nobody there: it still dashes on into the blow, the least way, and the blow shoves it on a little more
            assertEquals(StandPunch.LUNGE_MIN[k]+StandPunch.drive(punch(k),StandPunch.HIT[k]),shape.lunge,1e-4,"punch "+k+" dashes on as it lands");
            assertTrue(StandPunch.drive(punch(k),StandPunch.HIT[k]+.02f)>.6f*StandPunch.DRIVE[k],"punch "+k+" shoves it on through the blow");
            assertEquals(0,StandPunch.drive(punch(k),StandPunch.HIT[k]+.5f),.01f,"the shove is soon gone");
            float[] fist=StandPunch.fist(k);
            assertTrue(fist[0]>.7f&&fist[2]>1.2f&&fist[2]<1.9f,"punch "+k+" reaches ahead at head height: "+java.util.Arrays.toString(fist));
            // somebody a little way off: it dashes on until its fist sinks into them, never less nor more than its bounds
            for(float aim:new float[]{.8f,1.6f,2.2f,2.7f,3.3f,4f}){
                float lunge=StandPunch.lunge(new StandPunch.Punch(k,0,aim,Float.NaN));
                assertTrue(lunge>=StandPunch.LUNGE_MIN[k]-1e-6&&lunge<=StandPunch.LUNGE_MAX[k]+1e-6,"punch "+k+" dashes "+lunge);
                if(lunge>StandPunch.LUNGE_MIN[k]+1e-6&&lunge<StandPunch.LUNGE_MAX[k]-1e-6)
                    assertEquals(aim+StandPunch.BITE,StandPunch.AHEAD+lunge+fist[0],1e-4,"punch "+k+" meets a target "+aim+" off");
            }
            // it stays right in front of its user: never more than a couple of blocks on, even at its farthest dash
            assertTrue(StandPunch.AHEAD+StandPunch.LUNGE_MAX[k]<2.3f);
        }
        // back in place once a punch is over (the last of its shove dying away)
        StandPunch.at(punch(1),StarPlatinumRules.LENGTH[1]/20f,null,0,shape);
        assertEquals(0,shape.lunge,5e-3);
    }

    // ---- R, the heavy punch (2.59.0) ----

    static final int HEAVY=StarPlatinumRules.HEAVY;
    /** Steps a look through {@code seconds} at 60 fps, the shape at each frame from {@code shapes}; returns the fastest turn of any bone in a frame, failing on any of 40 degrees or more. */
    float sweep(float seconds,java.util.function.BiFunction<Float,StandPunch.Shape,StandPunch.Shape> shapes,String what)throws Exception{
        var poses=new StarPlatinumPoses();var names=bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        float[][] last=new float[names.size()][];float[] p=new float[6];float fastest=0;
        for(int f=0;f<=(int)(seconds/FRAME);f++){
            float t=f*FRAME;look(shapes.apply(t,shape),look);
            for(int b=0;b<names.size();b++){
                poses.pose(names.get(b),look,t,p);
                if(last[b]!=null){float d=turn(names.get(b),last[b],p);fastest=Math.max(fastest,d);assertTrue(d<40,names.get(b)+" turns "+d+" degrees in a frame at "+t+" ("+what+")");}
                last[b]=p.clone();
            }
        }
        return fastest;
    }
    @Test void theHeavyPunchMovesWithoutAJumpAt60fps()throws Exception{
        // alone, from the stand at rest beside its user; then a jab as soon as it may (a punch after it starts the combo again)
        float jab=StarPlatinumRules.STEP[HEAVY]/20f;
        float fastest=sweep(jab+2.2f,(t,into)->t<jab?StandPunch.at(punch(HEAVY),t,null,0,into):StandPunch.at(punch(1),t-jab,punch(HEAVY),t,into),"the heavy punch, then a jab");
        assertTrue(fastest>25,"it strikes fast: "+fastest);
        // it coils long and holds the coil before it strikes
        var shape=new StandPunch.Shape();
        StandPunch.at(punch(HEAVY),.4f,null,0,shape);assertEquals(1,shape.weights[StarPlatinumStance.WIND_HEAVY],1e-4,"coiled");
        StandPunch.at(punch(HEAVY),StandPunch.HIT[HEAVY],null,0,shape);assertEquals(1,shape.weights[StarPlatinumStance.PUNCH_HEAVY],1e-4,"thrown as it lands");
        assertTrue(StandPunch.FREEZE[HEAVY]>StandPunch.FREEZE[4],"the longest hit-stop");
        assertTrue(StandPunch.GUST[HEAVY]>StandPunch.GUST[4]&&StandPunch.DRIVE[HEAVY]>StandPunch.DRIVE[4],"the strongest gust and shove");
    }
    @Test void theHeavyPunchTakesOverFromAPunchStoppedShortAtAnyMoment()throws Exception{
        // R in the middle of a mashed combo: the punch in flight stops short and turns back to its guard, and the heavy
        // punch starts from there, STOP_RETURN ticks on (StarPlatinumAttack.beginHeavy); the client shows the newest punch
        // begun and the one before it (StandAttack.shape)
        float ret=StarPlatinumRules.STOP_RETURN/20f;
        for(int combo=1;combo<=4;combo++)for(int stop=1;stop<=StarPlatinumRules.LENGTH[combo];stop++){
            final int k=combo;float sk=STARTS[k-1],at=sk+stop/20f,heavy=at+ret;var stopped=punch(k).stop(stop/20f);
            var before=k>1?punch(k-1):null;float sb=k>1?STARTS[k-2]:0;
            sweep(heavy+StarPlatinumRules.LENGTH[HEAVY]/20f+.8f,(t,into)->{
                if(t>=heavy)return StandPunch.at(punch(HEAVY),t-heavy,stopped,t-sk,into);
                if(t>=sk)return StandPunch.at(t<at?punch(k):stopped,t-sk,before,t-sb,into);
                return at(t,STARTS,into);
            },"punch "+k+" stopped "+stop+" ticks in for the heavy punch");
        }
    }
    @Test void theHeavyPunchStoppedShortAtAnyMomentGoesBackToItsGuard()throws Exception{
        for(int stop=1;stop<=StarPlatinumRules.LENGTH[HEAVY];stop++){
            float at=stop/20f;var now=punch(HEAVY).stop(at);
            sweep(at+.8f,(t,into)->StandPunch.at(t<at?punch(HEAVY):now,t,null,0,into),"the heavy punch stopped "+stop+" ticks in");
            var shape=StandPunch.at(now,at+StarPlatinumRules.STOP_RETURN/20f,null,0,new StandPunch.Shape());
            assertEquals(1,shape.weights[StarPlatinumStance.GUARD],1e-6);assertEquals(1,shape.over,1e-6,"back in its guard");
        }
    }
    @Test void theHeavyPunchsBlowSoundsOnlyIfItStrikes(){
        var voice=new StandAttack.Heavy(new net.minecraft.util.Identifier("celestialfall","star_platinum_heavy"),false,1f);
        var blow=new StandAttack.Heavy(new net.minecraft.util.Identifier("celestialfall","star_platinum_heavy_hit"),true,1f);
        voice.tick();blow.tick();
        assertEquals(1,voice.loudness(),1e-6,"the voice whoever it strikes");assertEquals(0,blow.loudness(),1e-6,"the blow silent until it is known to strike");
        assertTrue(blow.shouldAlwaysPlay(),"the blow starts though silent, to come in as the fist lands");assertFalse(voice.shouldAlwaysPlay());
        blow.strike(true);blow.tick();assertEquals(1,blow.loudness(),1e-6,"in at once as the fist lands");
        // the server says it struck no one after all: out over FADE ticks, still playing as it goes
        blow.strike(false);float last=blow.loudness();
        for(int i=0;i<StandAttack.FADE;i++){blow.tick();assertTrue(blow.loudness()<last,"quieter every tick");last=blow.loudness();assertTrue(blow.canPlay());}
        assertEquals(0,blow.loudness(),1e-6);
        // stopped short as it plays: it fades out (the game would cut a sound off on a tick it cannot play)
        voice.fade();
        for(int i=0;i<StandAttack.FADE;i++){assertTrue(voice.canPlay(),"fading, not cut off");assertFalse(voice.isDone());voice.tick();}
        assertTrue(voice.isDone(),"gone once faded");
        // stopped short before its sound began: it never plays
        var early=new StandAttack.Heavy(new net.minecraft.util.Identifier("celestialfall","star_platinum_heavy"),false,1f);
        early.fade();assertFalse(early.canPlay(),"never begins");
    }
}
