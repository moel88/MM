package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gold Experience's punches as they are drawn and heard (2.154.0, its basic attack): thrown at full speed, one by one
 * or stopped short at any moment, the stand moves smoothly at 60 fps and is back at its user's shoulder after; each
 * punch loads, holds and lands on the server's tick, fastest as it lands, its fist then out in front where whoever it is
 * for stands; afterimages show only through the strikes; each whoosh is loudest as its fist lands and the fourth's
 * shout's "DA" comes on its blow.
 */
class GoldComboTest {
    static final float FRAME=1/60f;
    static final UUID USER=UUID.randomUUID();
    private static List<String> bones()throws Exception{
        try(var in=GoldComboTest.class.getResourceAsStream("/assets/celestialfall/geo/entity/gold_experience.geo.json")){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    /** Runs the punches heard frame by frame from the stand at rest beside its user; no bone, arm or place jumps. Returns the last frame's time. */
    private float watch(String what,float until,GoldCombo.Punch... punches)throws Exception{return watch(what,until,IDLE,punches);}
    /** The same from the stand held in stance {@code base} (beside its user at rest, or as it is summoned). */
    private float watch(String what,float until,int base,GoldCombo.Punch... punches)throws Exception{
        GoldCombo.put(USER,punches);
        var names=bones();var poses=new GoldExperiencePoses();var look=new GoldExperienceLook();var shape=new GoldRush.Shape();
        float[] scratch=new float[NAMES.size()],q=new float[6];float[][] last=new float[names.size()][];
        float engage=0,ahead=Float.NaN;boolean shown=false;float t=0;
        for(;t<=until;t+=FRAME){
            float delta=GoldRushTest.clock(t);
            look.only(base);look.armL=look.armR=1;shape.clear();
            boolean on=GoldCombo.pose(USER,delta,0,look,shape,scratch);shown|=on;
            if(!on)shape.engage=0;
            float sum=0;for(float w:look.weights){assertTrue(w>=-1e-5,what+" at "+t);sum+=w;}
            assertEquals(1,sum,1e-4,"weights at "+t+" ("+what+")");
            for(int b=0;b<names.size();b++){
                poses.blend(names.get(b),look.weights,t,q);
                if(last[b]!=null)assertTrue(GoldRushTest.turn(last[b],q)<45,names.get(b)+" turns "+GoldRushTest.turn(last[b],q)+" degrees in a frame at "+t+" ("+what+")");
                last[b]=q.clone();
            }
            assertTrue(Math.abs(shape.engage-engage)<.25,"it jumps round at "+t+" ("+what+")");
            // (it dashes on into each blow, fastest as it lands: at most some 18 blocks a second as the fourth lands with
            // the whole body behind it, never a leap)
            if(on&&!Float.isNaN(ahead))assertTrue(Math.abs(shape.ahead-ahead)<.3,"it jumps on or back "+(shape.ahead-ahead)+" blocks at "+t+" ("+what+")");
            engage=shape.engage;ahead=on?shape.ahead:Float.NaN;
        }
        assertTrue(shown,what);
        assertEquals(0,engage,.02,"back at its user's shoulder ("+what+")");
        assertEquals(1,look.weights[base],.02,"at rest again ("+what+")");
        return t;
    }
    static GoldCombo.Punch punch(int combo,long tick,float aim){return GoldCombo.heard(combo,tick,0,aim);}
    @Test void thrownAtFullSpeedTheFourPunchesMoveWithoutAJumpAt60fps()throws Exception{
        // each as soon as it may: 0, 7, 14 and 28 ticks in
        watch("the combo at full speed",4f,punch(1,0,Float.NaN),punch(2,7,Float.NaN),punch(3,14,Float.NaN),punch(4,28,Float.NaN));
        watch("the combo at someone",4f,punch(1,0,2.2f),punch(2,7,2.2f),punch(3,14,2.6f),punch(4,28,2.4f));
        // and slower: each a moment late (the stand stays in front between them)
        watch("the combo slower",5f,punch(1,0,1.8f),punch(2,15,1.8f),punch(3,30,2f),punch(4,55,2f));
        for(int c=1;c<=4;c++)watch("punch "+c+" alone",3f,punch(c,0,2f));
        // thrown as it is still coming out (Q, then at once the attack): from its summoning's own poses
        for(int base:new int[]{HERO,SUMMON,GUARD})watch("the combo from "+NAMES.get(base),4f,base,punch(1,0,Float.NaN),punch(2,7,Float.NaN),punch(3,14,Float.NaN),punch(4,28,Float.NaN));
    }
    @Test void stoppedShortAtAnyMomentItTurnsBackToItsGuardWithoutAJump()throws Exception{
        for(int c=1;c<=4;c++)for(float at:new float[]{0,.05f,.12f,GoldCombo.HIT[c]-.02f,GoldCombo.HIT[c]+.03f,GoldCombo.HIT[c]+.2f}){
            var p=punch(c,0,Float.NaN);p.stopped=at;
            watch("punch "+c+" stopped at "+at,3f,p);
            float[] w=new float[NAMES.size()];GoldCombo.stances(p,at+GoldCombo.BACK,w,1);
            assertEquals(1,w[GUARD],1e-4,"in its guard "+GoldCombo.BACK+" s after it was stopped at "+at);
        }
        // the next one taking over from one stopped short (the rush or another skill came between)
        var first=punch(2,0,Float.NaN);first.stopped=.12f;
        watch("a punch after one stopped short",3f,first,punch(1,4,Float.NaN));
    }
    @Test void eachLoadsHoldsAndLandsOnTheServersTickFastestAsItLands(){
        for(int c=1;c<=4;c++){
            assertEquals(GoldExperienceRules.M1_CONTACT[c]/20f,GoldCombo.HIT[c],1e-6,"punch "+c+" lands on the server's tick");
            float[] w=new float[NAMES.size()];GoldCombo.stances(c,0,w,1);assertEquals(1,w[GUARD],1e-5,"from its guard");
            w=new float[NAMES.size()];GoldCombo.stances(c,GoldCombo.strikeFrom(c),w,1);assertEquals(1,w[GoldCombo.WINDS[c]],1e-5,"loaded and held");
            w=new float[NAMES.size()];GoldCombo.stances(c,GoldCombo.HIT[c],w,1);assertEquals(1,w[GoldCombo.STRIKES[c]],1e-5,"at full stretch as it lands");
            // the hit-stop: it stands still at its blow
            assertEquals(GoldCombo.HIT[c],GoldCombo.warp(c,GoldCombo.HIT[c]+GoldCombo.FREEZE[c]*.5f),1e-6);
            // over before the server's length, back in its guard
            assertTrue(GoldCombo.KEYS[c][GoldCombo.KEYS[c].length-1].at()+GoldCombo.FREEZE[c]<=GoldExperienceRules.M1_LENGTH[c]/20f+1e-4,"punch "+c+" back in its guard before it is over");
            // faster and faster: fastest as it lands
            float prev=0,speed=0;
            for(float t=GoldCombo.strikeFrom(c)+FRAME;t<=GoldCombo.HIT[c]+1e-4;t+=FRAME){
                w=new float[NAMES.size()];GoldCombo.stances(c,t,w,1);float s=w[GoldCombo.STRIKES[c]];
                assertTrue(s-prev>=speed-1e-4,"punch "+c+" gathers speed at "+t);speed=s-prev;prev=s;
            }
        }
        // the hook and the fourth load longer than the jab and the cross; the fourth longest
        assertTrue(GoldCombo.strikeFrom(3)>GoldCombo.strikeFrom(1)&&GoldCombo.strikeFrom(4)>GoldCombo.strikeFrom(3));
    }
    @Test void itsFistLandsWhereWhoeverItIsForStands(){
        // the stand dashes on into each blow so that its fist sinks BITE into the near side of whoever it is for (within bounds)
        for(int c=1;c<=4;c++){
            float fist=GoldCombo.fist(punch(c,0,Float.NaN),GoldCombo.HIT[c])[0];
            for(float aim=1;aim<=4;aim+=.25f){
                var p=punch(c,0,aim);float lunge=GoldCombo.lunge(p);
                assertTrue(lunge>=GoldCombo.LUNGE_MIN[c]-1e-6&&lunge<=GoldCombo.LUNGE_MAX[c]+1e-6,"punch "+c+" dashes "+lunge);
                if(lunge>GoldCombo.LUNGE_MIN[c]+1e-6&&lunge<GoldCombo.LUNGE_MAX[c]-1e-6)
                    assertEquals(aim+GoldCombo.BITE,GoldCombo.AHEAD+lunge+fist,1e-4,"punch "+c+" meets a target "+aim+" off");
            }
            // with no one there it dashes the least
            assertEquals(GoldCombo.LUNGE_MIN[c],GoldCombo.lunge(punch(c,0,Float.NaN)),1e-6);
        }
        // the jab and the hook are the left fist's, the cross and the fourth the right's; the hook crosses the front
        assertFalse(GoldCombo.RIGHT[1]);assertTrue(GoldCombo.RIGHT[2]);assertFalse(GoldCombo.RIGHT[3]);assertTrue(GoldCombo.RIGHT[4]);
        float[] hook=GoldRush.FISTS[HOOK][0],wound=GoldRush.FISTS[HOOK_WIND][0];
        assertTrue(wound[1]<-.35f&&hook[1]>.2f&&wound[0]<hook[0]-.8f,"the hook comes round from out wide on its left ("+wound[1]+", "+wound[0]+" ahead) across to its right ("+hook[1]+", "+hook[0]+" ahead)");
        // the straights at full stretch further out than the hook; every blow about a body's head or chest high
        assertTrue(GoldRush.FISTS[JAB][0][0]>hook[0]&&GoldRush.FISTS[CROSS][1][0]>hook[0]);
        for(int s:new int[]{JAB,CROSS,HOOK,FINISH}){float up=(float)(GoldRush.FISTS[s][s==JAB||s==HOOK?0:1][2]+GoldCombo.RAISE+StandPresence.UP);assertTrue(up>1.2f&&up<1.75f,NAMES.get(s)+" "+up+" blocks up");}
    }
    @Test void afterimagesShowOnlyThroughTheStrikes(){
        for(int c=1;c<=4;c++){
            var p=punch(c,0,Float.NaN);
            assertEquals(0,GoldCombo.ghost(p,GoldCombo.strikeFrom(c)-.05f),1e-6,"not while it loads");
            assertTrue(GoldCombo.ghost(p,GoldCombo.HIT[c])>.99f,"through the strike");
            assertEquals(0,GoldCombo.ghost(p,GoldCombo.HIT[c]+GoldCombo.FREEZE[c]+.12f),1e-6,"gone soon after");
            p.stopped=GoldCombo.HIT[c]-.05f;assertEquals(0,GoldCombo.ghost(p,GoldCombo.HIT[c]),1e-6,"none for one stopped before it landed");
        }
        // only the fourth gathers light in its fist
        for(int c=1;c<4;c++)assertEquals(0,GoldCombo.charge(punch(c,0,Float.NaN),GoldCombo.strikeFrom(c)),1e-6);
        assertTrue(GoldCombo.charge(punch(4,0,Float.NaN),GoldCombo.strikeFrom(4))>.5f);
    }
    @Test void eachWhooshIsLoudestAsItsFistLandsAndTheShoutsDaOnTheFourthsBlow()throws Exception{
        String tool=Files.readString(Path.of("tools/stand/make_gold_sounds.py"));
        for(int c=1;c<=4;c++){
            var cues=GoldCombo.cues(c);
            assertEquals("gold_punch_swing_"+c,cues[0].sound());
            // the tool lays the whoosh's loudest moment where SWING_PEAK says
            var m=Pattern.compile("'gold_punch_swing_"+c+"': dict\\([^\\n]*\\n\\s*L\\('\\d+', [\\d.]+, [\\d.]+, ([\\d.]+),[^\\n]*peak=True").matcher(tool);
            assertTrue(m.find(),"swing "+c+" in the tool");
            assertEquals(GoldCombo.SWING_PEAK[c],Float.parseFloat(m.group(1)),1e-6,"swing "+c+"'s loudest moment");
            assertEquals(GoldCombo.HIT[c],cues[0].at()+GoldCombo.SWING_PEAK[c],1e-6,"swing "+c+" loudest as its fist lands");
            assertTrue(cues[0].at()>=0);
            assertEquals(c==4?2:1,cues.length);
        }
        var shout=GoldCombo.cues(4)[1];
        assertEquals("gold_experience_overdrive",shout.sound());
        assertEquals(GoldCombo.HIT[4],shout.at()+GoldPunch.DA_AT,1e-6,"its DA on the fourth's blow");
        assertTrue(shout.at()>GoldCombo.strikeFrom(4)-.15f,"its \"mu\" through the coil's hold");
        for(int c=1;c<=4;c++)assertEquals("gold_punch_hit_"+c,GoldCombo.blow(c));
    }
}
