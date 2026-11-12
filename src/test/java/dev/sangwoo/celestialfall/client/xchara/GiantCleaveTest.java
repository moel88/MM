package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's R with their hack on, the cleave, as drawn (2.121.0, user request: "R키는 패널을 누르면 들고 있던 검이 엄청
 * 거대해지고 그걸 양손으로 잡고 덤블링 한 번 돌고 크게 내려찍는 스킬"): the panel is pressed, the knife grows huge and
 * shrinks back, the other hand is on its rod while it is huge, the body turns one whole somersault high enough that the
 * knife's point never meets the ground until the blow, and on the blow the knife lies along the ground ahead; nothing
 * of it steps from one frame to the next.
 */
class GiantCleaveTest {
    static final float NONE=Float.NaN;
    static Vec3d[] blade(float t){return KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(GiantCleave.CLIP.frame(t)));}
    static Vec3d tip(float t){var hb=blade(t);return hb[0].add(hb[1].multiply(KnifeCombo.TIP*GiantCleave.scale(t)));}

    @Test void itsTimesAreTheServers(){
        assertEquals(CLEAVE_PRESS/20f,GiantCleave.PRESS,1e-6);assertEquals(CLEAVE_GROWN/20f,GiantCleave.GROWN,1e-6);
        assertEquals(CLEAVE_LEAP/20f,GiantCleave.LEAP,1e-6);assertEquals(blowAt(CLEAVE,0)/20f,GiantCleave.BLOW,1e-6);
        assertEquals(LENGTH[CLEAVE]/20f,GiantCleave.CLIP.length(),1e-4,"the clip is as long as the server holds its motion");
        assertTrue(GiantCleave.PRESS<GiantCleave.GROWN&&GiantCleave.GROWN<GiantCleave.LEAP&&GiantCleave.LEAP<GiantCleave.BLOW);
        assertTrue(GiantCleave.LIFE>GiantCleave.LENGTH_S,"what the blow leaves is drawn a little longer");
        assertSame(GiantCleave.CLIP,KnifeCombo.clipOf(CLEAVE));assertEquals(GiantCleave.LIFE,KnifeCombo.life(CLEAVE),1e-6);
    }
    @Test void theKnifeGrowsHugeAfterThePressAndShrinksBackAfterTheBlow(){
        assertEquals(1,GiantCleave.scale(0),1e-6);assertEquals(1,GiantCleave.scale(GiantCleave.PRESS),1e-6,"not before the press");
        assertEquals(GiantCleave.SCALE,GiantCleave.scale(GiantCleave.GROWN),1e-4,"grown as both hands hold it");
        assertEquals(GiantCleave.SCALE,GiantCleave.scale(GiantCleave.BLOW),1e-4);assertEquals(GiantCleave.SCALE,GiantCleave.scale(GiantCleave.BLOW+.4f),1e-4,"huge a moment after the blow");
        assertEquals(1,GiantCleave.scale(GiantCleave.LENGTH_S-.2f),1e-4,"its own size before they stand again");
        assertTrue(GiantCleave.SCALE>=2.2f&&KnifeCombo.TIP*GiantCleave.SCALE>6,"huge: over six blocks from the fist");
        float was=1,most=0;
        for(float t=0;t<2.2f;t+=1/240f){float s=GiantCleave.scale(t);assertTrue(Math.abs(s-was)<.09f,"a step at "+t);was=s;most=Math.max(most,s);}
        assertTrue(most>GiantCleave.SCALE*1.02f&&most<GiantCleave.SCALE*1.2f,"it swells a little over its size on the way: "+most);
        // stopped short: back at once from what it was
        float cut=.9f;
        assertEquals(GiantCleave.scale(cut),GiantCleave.scale(cut,cut),1e-6);assertEquals(1,GiantCleave.scale(cut+.12f,cut),1e-5);
        assertEquals(GiantCleave.scale(.5f),GiantCleave.scale(.5f,NONE),1e-6);
    }
    @Test void thePanelIsPressedOnceThenFoldedAwayWhileBothHandsHoldTheKnife(){
        float[] at=GiantCleave.pressAt(GiantCleave.PRESS);
        assertEquals(1,at[0],1e-5,"the arm up");assertEquals(1,at[1],1e-5,"the hand down on it");assertEquals(1,at[2],1e-5,"lit");assertEquals(1,at[3],"violet, not the restore's lime");
        assertEquals(0,GiantCleave.pressAt(0)[0],1e-6);assertEquals(0,GiantCleave.pressAt(GiantCleave.GRIP1)[0],1e-5,"down before the hand is on the knife");
        int downs=0;float was=0;
        for(float t=0;t<1;t+=1/240f){float p=GiantCleave.pressAt(t)[1];if(was<.5f&&p>=.5f)downs++;was=p;}
        assertEquals(1,downs);
        // the other hand: on the rod from the knife's growing until after the blow
        assertEquals(0,GiantCleave.grip(GiantCleave.PRESS),1e-6);assertEquals(1,GiantCleave.grip(GiantCleave.GROWN),1e-5);
        assertEquals(1,GiantCleave.grip(GiantCleave.BLOW+.3f),1e-5);assertEquals(0,GiantCleave.grip(GiantCleave.LENGTH_S-.2f),1e-5);
        // the panel is away while the hand is on the knife, and back before the end
        assertTrue(GiantCleave.PANEL_OFF<=GiantCleave.GRIP0+.02f&&GiantCleave.PANEL_OFF>GiantCleave.PRESS+.08f,"after the press, as the hand leaves it");
        assertTrue(GiantCleave.PANEL_ON>=GiantCleave.FREE0&&GiantCleave.PANEL_ON+.3f<=GiantCleave.LENGTH_S,"open again before they stand");
    }
    @Test void bothHandsAreOnTheRodWhileItIsHuge(){
        for(float t=GiantCleave.GRIP1+.01f;t<GiantCleave.FREE0;t+=1/120f){
            var p=R6.poseOf(GiantCleave.CLIP.frame(t));var hb=KagenoClips.blade(Vec3d.ZERO,0,p);
            Vec3d rod=hb[0].subtract(hb[1].multiply(GiantCleave.GRIP_BACK)),left=KagenoClips.freeHand(Vec3d.ZERO,0,p);
            assertTrue(left.distanceTo(rod)<.13,"the other hand "+left.distanceTo(rod)+" off the rod at "+t);
        }
        assertTrue(GiantCleave.GRIP_BACK<KnifeCombo.HEEL*GiantCleave.SCALE,"on the rod behind the fist, within its length");
    }
    @Test void oneWholeSomersaultHighEnoughThatTheKnifeClearsTheGround(){
        // the turn: once round forward, between the leap and the blow
        double turned=0;float was=GiantCleave.CLIP.frame(GiantCleave.LEAP).pitch();
        for(float t=GiantCleave.LEAP;t<=GiantCleave.BLOW+1e-4f;t+=1/240f){
            float p=GiantCleave.CLIP.frame(t).pitch();float d=MathHelper.wrapDegrees(p-was);
            assertTrue(Math.abs(d)<9,"the turn steps at "+t+": "+d);
            turned+=d;was=p;
        }
        assertEquals(360,turned,30,"one whole turn forward: "+turned);
        // the leap: high, and back down as the blow lands
        double apex=0;for(float t=0;t<2.1f;t+=1/120f)apex=Math.max(apex,GiantCleave.raised(t));
        assertTrue(apex>6.5&&apex<8,"the leap "+apex);
        assertEquals(0,GiantCleave.raised(GiantCleave.BLOW),1e-6,"landed on the blow");assertEquals(0,GiantCleave.raised(GiantCleave.LEAP),1e-6);
        // the knife's point goes once round in the air: straight down under them at some moment, never in the ground
        boolean down=false,back=false,over=false;
        for(float t=GiantCleave.LEAP+.06f;t<GiantCleave.BLOW-.03f;t+=1/240f){
            var hb=blade(t);Vec3d tip=tip(t);
            assertTrue(tip.y>.3,"the point in the ground at "+t+": "+tip.y);
            if(hb[1].y<-.97)down=true;if(down&&hb[1].z<-.9)back=true;if(back&&hb[1].y>.97)over=true;
        }
        assertTrue(down&&back&&over,"down ahead, under, up behind and over the top");
    }
    @Test void onTheBlowTheKnifeLiesAlongTheGroundAhead(){
        Vec3d tip=tip(GiantCleave.BLOW);var hb=blade(GiantCleave.BLOW);
        assertTrue(tip.y>-.3&&tip.y<.12,"its point on the ground: "+tip.y);
        assertTrue(tip.z>6&&tip.z<reach(CLEAVE,0)+.4,"as far ahead as the server's lane: "+tip.z);
        assertTrue(Math.abs(tip.x)<.45,"along the way they face");
        assertTrue(hb[1].y<0&&hb[1].y>-.15,"nearly level");
        // it came down from over their head: a tenth of a second before, it points well up
        assertTrue(blade(GiantCleave.BLOW-.12f)[1].y>.4);
        // and stays down until it shrinks
        for(float t=GiantCleave.BLOW;t<GiantCleave.SHRINK0-.12f;t+=1/60f)assertTrue(tip(t).y<.2&&tip(t).y>-.5,"at "+t+": "+tip(t).y);
    }
    @Test void itBeginsAndEndsAsTheyStandAndNothingSteps(){
        R6.Frame first=GiantCleave.CLIP.frame(0),last=GiantCleave.CLIP.frame(GiantCleave.LENGTH_S);
        MotionPose idle=new MotionPose();XCharaIdle.arms(idle,true,1,1,0,0,0);
        for(R6.Frame f:new R6.Frame[]{first,last}){
            for(int i:new int[]{MotionPose.RIGHT_ARM,MotionPose.RIGHT_LEG,MotionPose.LEFT_LEG})assertArrayEquals(KnifeCombo.AT_REST.joints().joint[i],f.joints().joint[i],.6f,"limb "+i);
            assertArrayEquals(idle.joint[MotionPose.LEFT_ARM],f.joints().joint[MotionPose.LEFT_ARM],.6f,"the panel's arm as it holds the panel");
            assertEquals(0,f.pitch(),.5f);assertEquals(0,f.up(),1e-3);
            assertEquals(0,f.joints().knee[0],.6f);assertEquals(0,f.joints().elbow[1],.6f);
        }
        R6.Frame was=first;
        for(float t=1/240f;t<=GiantCleave.LENGTH_S;t+=1/240f){
            R6.Frame f=GiantCleave.CLIP.frame(t);
            assertTrue(Math.abs(f.up()-was.up())<.26,"the body jumps at "+t+": "+(f.up()-was.up()));
            for(int i=0;i<2;i++){
                assertTrue(Math.abs(f.joints().elbow[i]-was.joints().elbow[i])<14,"elbow "+i+" at "+t+": "+(f.joints().elbow[i]-was.joints().elbow[i]));
                // (the push-off of the leap is the quickest a knee goes: a leg nearly straight is a few degrees of knee for
                // every hundredth of a block, and the legs shoot straight as the feet leave the ground)
                // (and as they touch down again, a moment before the blow)
                boolean off=t>GiantCleave.LEAP&&t<GiantCleave.LEAP+.1f||t>GiantCleave.BLOW-.06f&&t<GiantCleave.BLOW+.01f;
                assertTrue(Math.abs(f.joints().knee[i]-was.joints().knee[i])<(off?40:14),"knee "+i+" at "+t+": "+(f.joints().knee[i]-was.joints().knee[i]));
            }
            was=f;
        }
        // the camera goes with the leap and comes back
        assertTrue(GiantCleave.SCAR1>=reach(CLEAVE,0)&&GiantCleave.SCAR0<1.5,"the scar lies under the knife, as long as the lane");
    }
}
