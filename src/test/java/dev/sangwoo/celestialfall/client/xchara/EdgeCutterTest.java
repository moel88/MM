package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.xchara.CutterRules;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's E with their hack off, the three cuts, as drawn (2.129.0, the user's video and words: "앞으로 짧게 이동하면서
 * 3번 베는 스킬이야 적이 바로 앞에 있으면 이동하지 않고 3번 베기만 해"): the clips are as long as the server's, begin and end
 * in the stance, and nothing of them steps; each cut has its blade ahead across the middle as the server's blow lands
 * and is quickest there; they go one way, back, and down across; the one that goes forward first leans far over its
 * stride with the knife trailing, and its cuts are the same, later.
 */
class EdgeCutterTest {
    static Vec3d[] blade(Anim clip,float t){return KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(clip.frame(t)));}
    static Vec3d tip(Anim clip,float t){var hb=blade(clip,t);return hb[0].add(hb[1].multiply(KnifeCombo.TIP));}
    static double rt(Vec3d v){return -v.x;}

    @Test void theClipsAreTheServers(){
        assertEquals(LENGTH[CUTTER]/20f,EdgeCutter.CLIP.length(),1e-4);assertEquals(LENGTH[CUTTER_DASH]/20f,EdgeCutter.CLIP_DASH.length(),1e-4);
        assertSame(EdgeCutter.CLIP,KnifeCombo.clipOf(CUTTER));assertSame(EdgeCutter.CLIP_DASH,KnifeCombo.clipOf(CUTTER_DASH));
        assertSame(CorruptionJab.CLIP,KnifeCombo.clipOf(JAB),"the jab is still the jab");assertSame(GiantCleave.CLIP,KnifeCombo.clipOf(CLEAVE));assertSame(OverwriteSlam.CLIP,KnifeCombo.clipOf(SLAM));
        assertEquals(EdgeCutter.LIFE,KnifeCombo.life(CUTTER),1e-6);assertEquals(EdgeCutter.LIFE,KnifeCombo.life(CUTTER_DASH),1e-6);
        assertTrue(EdgeCutter.LIFE>=lastBlow(CUTTER_DASH)/20f+SlashFx.SWIRL_LIFE,"what the last cut leaves is drawn until it is gone");
        assertEquals(CutterRules.LEAD/20f,EdgeCutter.LEAD,1e-6);
        // its sound starts so that its first rush is on the first cut
        assertEquals(blowAt(CUTTER,0)-KnifeClient.CUTTER_SOUND_BEFORE,KnifeClient.soundAt(CUTTER));assertEquals(blowAt(CUTTER_DASH,0)-KnifeClient.CUTTER_SOUND_BEFORE,KnifeClient.soundAt(CUTTER_DASH));
        assertEquals(blowAt(JAB,0)-KnifeClient.JAB_SOUND_BEFORE,KnifeClient.soundAt(JAB));assertEquals(0,KnifeClient.soundAt(CLEAVE));
    }
    @Test void theyBeginAndEndInTheStanceAndNothingSteps(){
        for(Anim clip:new Anim[]{EdgeCutter.CLIP,EdgeCutter.CLIP_DASH}){
            R6.Frame rest=KnifeCombo.AT_REST,a=clip.frame(0),z=clip.frame(clip.length());
            for(int j=0;j<rest.joints().joint.length;j++)for(int k=0;k<3;k++){
                assertEquals(rest.joints().joint[j][k],a.joints().joint[j][k],.5,"joint "+j+" as it begins");
                assertEquals(rest.joints().joint[j][k],z.joints().joint[j][k],.5,"joint "+j+" as it ends");
            }
            assertEquals(0,z.fw(),1e-3);assertEquals(0,z.up(),1e-3);
            Vec3d fist=blade(clip,0)[0],tip=tip(clip,0);
            for(float t=1/480f;t<=clip.length();t+=1/480f){
                Vec3d f=blade(clip,t)[0],p=tip(clip,t);
                assertTrue(f.distanceTo(fist)<.13,"the fist steps "+f.distanceTo(fist)+" at "+t);
                assertTrue(p.distanceTo(tip)<.5,"the knife's point steps "+p.distanceTo(tip)+" at "+t);
                assertTrue(p.y>.12,"its point in the ground at "+t+": "+p.y);
                fist=f;tip=p;
            }
            for(float t=0;t<=clip.length();t+=1/60f)assertTrue(clip.frame(t).up()<=.05,"they leave the ground at "+t);
        }
    }
    @Test void eachCutHasItsBladeAheadAcrossTheMiddleAsItLandsAndIsQuickestThere(){
        for(int c:new int[]{CUTTER,CUTTER_DASH}){
            Anim clip=KnifeCombo.clipOf(c);
            for(int i=0;i<3;i++){
                float at=blowAt(c,i)/20f;
                Vec3d way=blade(clip,at)[1],tip=tip(clip,at);
                assertTrue(way.z>.9,"cut "+i+": the blade points ahead as it lands: "+way);
                assertTrue(Math.abs(tip.x)<.9&&tip.z>2.4&&tip.y>.5&&tip.y<2.2,"cut "+i+": its point before them: "+tip);
                // the twentieth of a second into the blow covers more than the one that wound it
                double into=tip(clip,at).distanceTo(tip(clip,at-.05f));
                assertTrue(into>1.6,"cut "+i+" comes in at "+into);
            }
        }
    }
    @Test void theyGoOneWayBackAndDownAcross(){
        Anim clip=EdgeCutter.CLIP;float b1=EdgeCutter.B1,b2=EdgeCutter.B2,b3=EdgeCutter.B3;
        // the first: from their right to their left, level
        Vec3d a=tip(clip,b1-.03f),z=tip(clip,b1+.03f);
        assertTrue(rt(a)>rt(z)+.8,"the first goes to the left: "+rt(a)+" to "+rt(z));assertTrue(Math.abs(a.y-z.y)<.7,"about level: "+a.y+" to "+z.y);
        // the second: back to their right, rising
        a=tip(clip,b2-.03f);z=tip(clip,b2+.03f);
        assertTrue(rt(z)>rt(a)+.8,"the second goes back to the right");assertTrue(z.y>a.y+.15,"rising: "+a.y+" to "+z.y);
        // the third: to the left again and down, from over their shoulder; the body drops into it and steps
        a=tip(clip,b3-.03f);z=tip(clip,b3+.03f);
        assertTrue(rt(a)>rt(z)+.6,"the third goes to the left");assertTrue(a.y>z.y+.5,"down: "+a.y+" to "+z.y);
        assertTrue(tip(clip,b3-.09f).y>2.3,"wound up over the shoulder: "+tip(clip,b3-.09f).y);
        assertTrue(clip.frame(b3).up()<clip.frame(b2).up()-.1,"the body drops into the last");
        assertTrue(clip.frame(b3).fw()>clip.frame(b1).fw()+.3,"and has gone in over a step");
        // the body turns with each: right of the front as a cut is wound, left of it after (the other way for the backhand)
        assertTrue(clip.frame(b1-.07f).yaw()<-20&&clip.frame(b1+.04f).yaw()>20);
        assertTrue(clip.frame(b2+.04f).yaw()<-15);
        assertTrue(clip.frame(b3+.05f).yaw()>20);
    }
    @Test void theOneThatGoesForwardLeansFarOverItsStrideWithTheKnifeTrailingAndCutsTheSameLater(){
        Anim dash=EdgeCutter.CLIP_DASH,still=EdgeCutter.CLIP;
        // through the going: the body thrown forward (the video's is nearly level), the knife behind them and up
        for(float t=.10f;t<=.20f;t+=1/60f){
            assertTrue(dash.frame(t).pitch()>35,"leaning "+dash.frame(t).pitch()+" at "+t);
            Vec3d way=blade(dash,t)[1];
            assertTrue(way.z<-.6&&way.y>.1,"the knife trails behind and up at "+t+": "+way);
        }
        // upright again as they cut
        assertTrue(dash.frame(EdgeCutter.LEAD+EdgeCutter.B1).pitch()<20);
        // from the first cut on it is the cut where they stand, LEAD later
        for(float t=EdgeCutter.B1-.02f;t<=still.length();t+=1/30f){
            Vec3d a=tip(still,t),b=tip(dash,t+EdgeCutter.LEAD);
            assertTrue(a.distanceTo(b)<.02,"the same cut at "+t+": "+a+" and "+b);
        }
        // the body is drawn along the way while it is held to the plan, and let go once the server has them there
        assertEquals(1,EdgeCutter.held(0),1e-6);assertEquals(1,EdgeCutter.held(CutterRules.DASH_TO+5),1e-6);assertEquals(0,EdgeCutter.held(CutterRules.DASH_TO+10),1e-6);
        assertTrue(CutterRules.DASH_TO+10<=blowAt(CUTTER_DASH,2),"let go before the last cut");
    }
    @Test void aCutShakesALittleAndTheLastTheMost(){
        assertTrue(EdgeCutter.SHAKE[2]>EdgeCutter.SHAKE[0]&&EdgeCutter.SHAKE[0]==EdgeCutter.SHAKE[1]&&EdgeCutter.SHAKE[2]<=1.0f);
    }
}
