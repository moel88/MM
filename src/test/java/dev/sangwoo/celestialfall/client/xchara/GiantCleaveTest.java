package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.combat.MotionPose;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's R with their hack on, the cleave, as drawn (2.121.0; 2.122.0, user request: "덤블링을 돌지말고 그냥 제자리에서
 * 거대한 검을 내려치게 하고 손잡이는 상관없고 칼날 부분을 엄청 키워줘 그리고 내려찍을때 바닥에 박히는 효과를 엄청 강하게
 * 만들어주고"): the panel is pressed, the knife's blade grows huge (its rod a little) and shrinks back, the other hand is
 * on its rod while it is huge, they stay on the ground where they stand, raise it over their head and bring it down, and
 * on the blow the blade lies along the ground ahead with its edge sunk in it; the floor is heaved up along it; nothing
 * of it steps from one frame to the next. (2.123.0, user request: "내려찍는 효과를 강하게 만들어줘 그리고 검이 서서히 커지는
 * 효과 넣어줘": the knife grows slowly, from the press until it is nearly raised, never past its size, with lines running
 * out along it and pixels gathering onto it; and the blow heaves the floor higher, wider and further, twice.)
 */
class GiantCleaveTest {
    static final float NONE=Float.NaN;
    static Vec3d[] blade(float t){return KagenoClips.blade(Vec3d.ZERO,0,R6.poseOf(GiantCleave.CLIP.frame(t)));}
    static Vec3d tip(float t){var hb=blade(t);return hb[0].add(hb[1].multiply(GiantCleave.reach(t)));}

    @Test void itsTimesAreTheServers(){
        assertEquals(CLEAVE_PRESS/20f,GiantCleave.PRESS,1e-6);assertEquals(CLEAVE_GROWN/20f,GiantCleave.GROWN,1e-6);assertEquals(CLEAVE_HELD/20f,GiantCleave.HELD,1e-6);
        assertEquals(CLEAVE_RAISED/20f,GiantCleave.RAISED,1e-6);assertEquals(blowAt(CLEAVE,0)/20f,GiantCleave.BLOW,1e-6);
        assertEquals(LENGTH[CLEAVE]/20f,GiantCleave.CLIP.length(),1e-4,"the clip is as long as the server holds its motion");
        assertTrue(GiantCleave.PRESS<GiantCleave.HELD&&GiantCleave.HELD<GiantCleave.GROWN&&GiantCleave.GROWN<=GiantCleave.RAISED&&GiantCleave.RAISED<GiantCleave.BLOW);
        assertTrue(GiantCleave.LIFE>GiantCleave.BLOW+GiantCleave.SCAR_LIFE-.01f&&GiantCleave.LIFE>GiantCleave.BLOW+GiantCleave.GROUND_LIFE-.01f,"what the blow leaves is drawn until it is gone");
        assertSame(GiantCleave.CLIP,KnifeCombo.clipOf(CLEAVE));assertEquals(GiantCleave.LIFE,KnifeCombo.life(CLEAVE),1e-6);
    }
    @Test void theBladeGrowsHugeAndTheRodALittle(){
        assertEquals(1,GiantCleave.scale(0),1e-6);assertEquals(1,GiantCleave.blade(GiantCleave.PRESS),1e-6,"not before the press");
        assertEquals(GiantCleave.ROD,GiantCleave.scale(GiantCleave.GROWN),1e-4);assertEquals(GiantCleave.BLADE,GiantCleave.blade(GiantCleave.GROWN),1e-4,"grown as both hands hold it");
        assertEquals(GiantCleave.BLADE,GiantCleave.blade(GiantCleave.BLOW+.5f),1e-4,"huge while it is in the ground");
        assertEquals(1,GiantCleave.blade(GiantCleave.LENGTH_S-.15f),1e-4,"its own size before they stand again");assertEquals(1,GiantCleave.scale(GiantCleave.LENGTH_S-.15f),1e-4);
        // (2.122.0: "손잡이는 상관없고 칼날 부분을 엄청 키워줘") the blade far more than the rod: four times what the hand holds, much more than 2.121.0's 2.5
        assertTrue(GiantCleave.ROD*GiantCleave.BLADE>=3.8f,"the blade "+GiantCleave.ROD*GiantCleave.BLADE+" times");
        assertTrue(GiantCleave.ROD<=1.8f&&GiantCleave.BLADE>=2,"the rod only a little: room for both hands");
        assertTrue(GiantCleave.REACH>9&&GiantCleave.DEEP_BLADE>2,"a blade some nine blocks long and over two deep");
        assertEquals(GiantCleave.REACH,GiantCleave.reach(GiantCleave.GROWN),1e-3);assertEquals(KnifeCombo.TIP,GiantCleave.reach(0),1e-6);
        float was=1,most=0;
        for(float t=0;t<2.4f;t+=1/240f){float s=GiantCleave.blade(t);assertTrue(Math.abs(s-was)<.1f,"a step at "+t);was=s;most=Math.max(most,s);}
        assertEquals(GiantCleave.BLADE,most,1e-4,"never past its size");
        // (2.123.0: "검이 서서히 커지는 효과") slowly: over more than half a second, from the press until it is nearly raised, three times as long as 2.122.0's fifth of a second
        assertTrue(GiantCleave.GROW1-GiantCleave.GROW0>=.6f,"it grows for "+(GiantCleave.GROW1-GiantCleave.GROW0)+" s");
        assertTrue(GiantCleave.GROW0>=GiantCleave.PRESS&&GiantCleave.GROW1<=GiantCleave.RAISED,"from the press, grown as it is raised");
        // steadily: it only grows, it is about half grown half way, and no moment of it is much quicker than the rest
        float before=0,quickest=0,mean=1/(GiantCleave.GROW1-GiantCleave.GROW0);
        for(float t=GiantCleave.GROW0;t<=GiantCleave.GROW1+1e-4f;t+=1/240f){
            float g=GiantCleave.grown(t);assertTrue(g>=before-1e-6f,"it shrinks at "+t);
            quickest=Math.max(quickest,(g-before)*240);before=g;
        }
        assertEquals(.5f,GiantCleave.grown((GiantCleave.GROW0+GiantCleave.GROW1)/2),.03f);
        assertTrue(quickest<mean*1.4f,"its quickest "+quickest+" a second, "+mean+" on the whole");
        assertTrue(GiantCleave.grown(GiantCleave.HELD)>.2f&&GiantCleave.grown(GiantCleave.HELD)<.5f,"still small as the other hand takes it: "+GiantCleave.grown(GiantCleave.HELD));
        // as it grows lines run out along it one after another through all of its growing, and pixels gather onto it
        assertTrue(GiantCleave.SCANS>=5&&GiantCleave.SCANS*GiantCleave.SCAN_EVERY>=(GiantCleave.GROW1-GiantCleave.GROW0)*.85f&&GiantCleave.SCANS*GiantCleave.SCAN_EVERY<=GiantCleave.GROW1-GiantCleave.GROW0);
        int motes=0;
        for(int j=0;j<GiantCleave.MOTES;j++){
            double far=2;boolean seen=false;
            for(float t=GiantCleave.GROW0;t<GiantCleave.GROW1+.1f;t+=1/240f){
                var mo=GiantCleave.mote(j,7,t);if(mo==null){if(seen)break;continue;}
                seen=true;assertTrue(mo[0]<=far+1e-9&&mo[0]>=0,"mote "+j+" goes back out");far=mo[0];
            }
            if(seen){motes++;assertTrue(far<.06,"mote "+j+" does not reach the blade: "+far);}
            assertNull(GiantCleave.mote(j,7,GiantCleave.GROW1+.02f),"none after it is grown");
        }
        assertEquals(GiantCleave.MOTES,motes);
        assertEquals(1,GiantCleave.belly(0),1e-9);assertEquals(1,GiantCleave.belly(.5),1e-9);assertTrue(GiantCleave.belly(.95)<.2);
        // stopped short: back at once from what it was
        float cut=.7f;
        assertEquals(GiantCleave.blade(cut),GiantCleave.blade(cut,cut),1e-6);assertEquals(1,GiantCleave.blade(cut+.12f,cut),1e-5);assertEquals(1,GiantCleave.scale(cut+.12f,cut),1e-5);
        // the glow's points go with the blade: about its heel
        double[] heel=HackKnife.grown(0,HackKnife.Y_HEEL,0,2.5f),point=HackKnife.grown(1,HackKnife.Y_TIP,.1,2.5f);
        assertEquals(HackKnife.Y_HEEL,heel[1],1e-9,"the heel stays on the rod's end");
        assertEquals(HackKnife.Y_HEEL+2.5*HackKnife.BLADE,point[1],1e-6);assertEquals(2.5,point[0],1e-9);assertEquals(.25,point[2],1e-9);
    }
    @Test void thePanelIsPressedOnceThenFoldedAwayWhileBothHandsHoldTheKnife(){
        float[] at=GiantCleave.pressAt(GiantCleave.PRESS);
        assertEquals(1,at[0],1e-5,"the arm up");assertEquals(1,at[1],1e-5,"the hand down on it");assertEquals(1,at[2],1e-5,"lit");assertEquals(1,at[3],"violet, not the restore's lime");
        assertEquals(0,GiantCleave.pressAt(0)[0],1e-6);assertEquals(0,GiantCleave.pressAt(GiantCleave.GRIP1)[0],1e-5,"down before the hand is on the knife");
        int downs=0;float was=0;
        for(float t=0;t<1;t+=1/240f){float p=GiantCleave.pressAt(t)[1];if(was<.5f&&p>=.5f)downs++;was=p;}
        assertEquals(1,downs);
        assertEquals(0,GiantCleave.grip(GiantCleave.PRESS),1e-6);assertEquals(1,GiantCleave.grip(GiantCleave.HELD),1e-5);assertEquals(1,GiantCleave.grip(GiantCleave.GROWN),1e-5);
        assertEquals(1,GiantCleave.grip(GiantCleave.BLOW+.4f),1e-5);assertEquals(0,GiantCleave.grip(GiantCleave.LENGTH_S-.2f),1e-5);
        assertTrue(GiantCleave.PANEL_OFF<=GiantCleave.GRIP0+.02f&&GiantCleave.PANEL_OFF>GiantCleave.PRESS+.08f,"after the press, as the hand leaves it");
        assertTrue(GiantCleave.PANEL_ON>=GiantCleave.FREE0&&GiantCleave.PANEL_ON+.25f<=GiantCleave.LENGTH_S,"open again before they stand");
    }
    @Test void bothHandsAreOnTheRodWhileItIsHuge(){
        for(float t=GiantCleave.GRIP1+.01f;t<GiantCleave.FREE0;t+=1/120f){
            var p=R6.poseOf(GiantCleave.CLIP.frame(t));var hb=KagenoClips.blade(Vec3d.ZERO,0,p);
            Vec3d rod=hb[0].subtract(hb[1].multiply(GiantCleave.GRIP_BACK)),left=KagenoClips.freeHand(Vec3d.ZERO,0,p);
            assertTrue(left.distanceTo(rod)<.14,"the other hand "+left.distanceTo(rod)+" off the rod at "+t);
        }
        assertTrue(GiantCleave.GRIP_BACK<KnifeCombo.HEEL*GiantCleave.ROD,"on the rod behind the fist, within its length");
    }
    @Test void theyStayWhereTheyStandRaiseItOverTheirHeadAndBringItDown(){
        // (2.122.0: "덤블링을 돌지말고 그냥 제자리에서") no leap, no turn: on the ground, never once round
        for(float t=0;t<=GiantCleave.LENGTH_S;t+=1/120f){
            R6.Frame f=GiantCleave.CLIP.frame(t);
            assertTrue(f.up()<.12&&f.up()>-.6,"off the ground at "+t+": "+f.up());
            assertTrue(Math.abs(f.pitch())<35,"turned over at "+t+": "+f.pitch());
            assertEquals(0,f.fw(),1e-6);assertEquals(0,f.rt(),1e-6);
        }
        // in both hands it stands upright before them and grows on up; raised, it is over their head and a little back
        assertTrue(blade(GiantCleave.HELD)[1].y>.95,"upright as both hands take it");
        assertTrue(blade(GiantCleave.HELD+.18f)[1].y>.95&&tip(GiantCleave.HELD+.18f).y>tip(GiantCleave.HELD).y+1.5,"growing on up over them");
        var top=blade(GiantCleave.RAISED);
        assertTrue(top[1].y>.85&&top[1].z<-.2,"over their head and back: "+top[1]);
        assertTrue(top[0].y>1.9,"the hands over their head: "+top[0].y);
        // down the front: the blade goes only forward and down from the top to the blow, its point never in the ground before it
        double was=Math.atan2(top[1].y,top[1].z);
        for(float t=GiantCleave.RAISED+.1f;t<GiantCleave.BLOW-.01f;t+=1/240f){
            var hb=blade(t);double a=Math.atan2(hb[1].y,hb[1].z);
            assertTrue(a<=was+.02,"the blade goes back up at "+t);was=Math.min(was,a);
            assertTrue(Math.abs(hb[1].x)<.2,"in the plane they face along");
        }
        for(float t=GiantCleave.GROW0;t<GiantCleave.BLOW-.04f;t+=1/240f)assertTrue(tip(t).y>.5,"the point in the ground at "+t+": "+tip(t).y);
        // quickest as it lands
        double before=tip(GiantCleave.BLOW-.06f).distanceTo(tip(GiantCleave.BLOW-.03f)),early=tip(GiantCleave.RAISED+.08f).distanceTo(tip(GiantCleave.RAISED+.11f));
        assertTrue(before>early*1.5,"it speeds up into the blow: "+early+" then "+before);
    }
    @Test void onTheBlowTheBladeLiesAlongTheGroundWithItsEdgeSunkInIt(){
        Vec3d tip=tip(GiantCleave.BLOW);var hb=blade(GiantCleave.BLOW);
        assertTrue(tip.z>9&&tip.z<reach(CLEAVE,0)+.6,"as far ahead as the server's lane: "+tip.z);
        assertTrue(Math.abs(tip.x)<.5,"along the way they face");
        assertTrue(hb[1].y<0&&hb[1].y>-.1,"level, its point a little down: "+hb[1].y);
        // its spine is over the ground and its edge under it, a block or so: buried, not lying on top and not swallowed
        double spine=hb[0].add(hb[1].multiply(GiantCleave.REACH*.5)).y,edge=spine-GiantCleave.DEEP_BLADE;
        assertTrue(spine>.5&&spine<1.6,"its spine over the ground: "+spine);
        assertTrue(edge<-.6&&edge>-1.8,"its edge sunk in the ground: "+edge);
        // and it stays there until it shrinks
        for(float t=GiantCleave.BLOW;t<GiantCleave.SHRINK0;t+=1/60f){
            var b=blade(t);double sp=b[0].add(b[1].multiply(GiantCleave.REACH*.5)).y;
            assertTrue(sp>.3&&sp<1.6&&Math.abs(b[1].y)<.12,"at "+t+": "+sp);
        }
        // the trench lies under the blade, as long as the lane
        assertTrue(GiantCleave.SCAR1>=reach(CLEAVE,0)&&GiantCleave.SCAR0<1.2);
    }
    @Test void theFloorIsHeavedUpAlongTheBladeAndRipplesOut(){
        // (2.122.0: "내려찍을때 바닥에 박히는 효과를 엄청 강하게") slabs thrown up at once along both sides, tipped away from the blade
        // (2.123.0: "내려찍는 효과를 강하게") over a block high by the blade, three blocks to either side
        double[] near=GiantCleave.heave(4,1.9,.13f,.5,.5),other=GiantCleave.heave(4,-1.9,.13f,.5,.5),far=GiantCleave.heave(4,2.8,.13f,.5,.5),by=GiantCleave.heave(4,.9,.13f,.5,.5);
        assertTrue(near[0]>.9&&near[0]<1.6,"the rim two blocks from the blade: "+near[0]);assertTrue(GiantCleave.SLABS>=3);
        // a crater: low right by the blade, whose spine (a block up) stands clear of it
        assertTrue(by[0]>.15&&by[0]<.6,"by the blade: "+by[0]);assertTrue(GiantCleave.heave(4,0,.13f,.5,.5)[0]<.5);
        assertTrue(near[1]>8&&other[1]<-8,"tipped away to either side");
        assertTrue(far[0]<near[0]&&far[0]>.1,"lower further from it");
        assertEquals(0,GiantCleave.heave(4,1.9,0,.5,.5)[0],1e-9,"not before the blow");
        // they stay up while the blade is in the ground and are back down by the end
        assertTrue(GiantCleave.heave(4,1.9,GiantCleave.SINK0,.5,.5)[0]>.6);
        assertEquals(0,GiantCleave.heave(4,1.9,GiantCleave.SINK1,.5,.5)[0],1e-6);
        assertTrue(GiantCleave.SINK0>=GiantCleave.SHRINK0-GiantCleave.BLOW,"until the blade is out");
        // nothing steps
        double was=0;
        for(float s=0;s<GiantCleave.GROUND_LIFE;s+=1/240f){double l=GiantCleave.heave(4,1.9,s,.5,.5)[0];assertTrue(Math.abs(l-was)<.12,"a slab jumps at "+s);was=l;}
        // further out a ripple runs away from the blade: there later than nearer, gone after; and a second, the blow's echo
        double early=GiantCleave.heave(4,4,.27f,.5,.5)[0],late=GiantCleave.heave(4,8,.27f,.5,.5)[0];
        assertTrue(early>.3&&late<early,"the ripple at 4 blocks first: "+early+", "+late);
        assertTrue(GiantCleave.heave(4,8,.53f,.5,.5)[0]>late);
        assertTrue(GiantCleave.heave(4,4,GiantCleave.AFTER+4/12f,.5,.5)[0]>.15,"the echo's ripple");
        assertEquals(0,GiantCleave.heave(4,4,1.6f,.5,.5)[0],.02);
        assertTrue(GiantCleave.heave(4,11,.74f,.5,.5)[0]>.05,"far out");assertTrue(GiantCleave.RIPPLE>=12);
        assertEquals(0,GiantCleave.heave(4,GiantCleave.RIPPLE+.5,.9f,.5,.5)[0],1e-9);
        // round the blade's far end too, and behind them less; the floor they stand on never moves
        assertTrue(GiantCleave.heave(GiantCleave.SCAR1+4,0,.27f,.5,.5)[0]>.3);
        assertTrue(GiantCleave.heave(-3,3,.3f,.5,.5)[0]<GiantCleave.heave(3,4.6,.3f,.5,.5)[0]);
        for(float s=0;s<GiantCleave.GROUND_LIFE;s+=1/60f){
            assertEquals(0,GiantCleave.heave(0,0,s,.5,.5)[0],1e-9,"under their feet");assertEquals(0,GiantCleave.heave(.6,.5,s,.5,.5)[0],1e-9);assertEquals(0,GiantCleave.heave(-.7,-.6,s,.5,.5)[0],1e-9);
        }
        // nothing of the ripples steps
        for(double x:new double[]{3.5,6,10}){
            double w=0;
            for(float s=0;s<GiantCleave.GROUND_LIFE;s+=1/240f){double l=GiantCleave.heave(4,x,s,.5,.5)[0];assertTrue(Math.abs(l-w)<.08,"the ripple jumps at "+s);w=l;}
        }
        // pieces of it thrown: up from along the trench, down again, never under the ground, gone in two seconds
        Vec3d f=new Vec3d(0,0,1),right=new Vec3d(-1,0,0);double highest=0;
        for(int j=0;j<GiantCleave.CHUNKS;j++){
            double top=0;
            for(float s=0;s<2.8f;s+=1/60f){
                var k=GiantCleave.chunk(Vec3d.ZERO,f,right,j,7,s,0);
                if(s>=2.5f){assertNull(k,"gone");continue;}
                if(k==null)continue;
                assertTrue(k[1]>=-1e-6,"under the ground");assertTrue(k[3]<=.7+1e-9);top=Math.max(top,k[1]);
                assertTrue(k[2]>-3&&k[2]<GiantCleave.SCAR1+6);
            }
            assertTrue(top>1.2,"piece "+j+" thrown up: "+top);highest=Math.max(highest,top);
        }
        assertTrue(highest>5,"the highest of them "+highest+" blocks up");
        // (2.123.0) far more of everything than 2.122.0's 46 pieces, 22 spikes, 22 cracks, three rings
        assertTrue(GiantCleave.CHUNKS>=100&&GiantCleave.SPIKES>=36&&GiantCleave.CRACKS>=32&&GiantCleave.RINGS>=5&&GiantCleave.BARS>=30,"much of it");
        assertTrue(GiantCleave.GROUND_LIFE>2.4f&&GiantCleave.AFTER>.15f&&GiantCleave.AFTER<.4f);
        assertTrue(GiantCleave.width(4)>.5,"a wider trench");
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
            assertTrue(Math.abs(f.up()-was.up())<.03,"the body jumps at "+t+": "+(f.up()-was.up()));
            assertTrue(Math.abs(f.pitch()-was.pitch())<3,"the body tips at "+t);
            for(int i=0;i<2;i++){
                assertTrue(Math.abs(f.joints().elbow[i]-was.joints().elbow[i])<14,"elbow "+i+" at "+t+": "+(f.joints().elbow[i]-was.joints().elbow[i]));
                assertTrue(Math.abs(f.joints().knee[i]-was.joints().knee[i])<14,"knee "+i+" at "+t+": "+(f.joints().knee[i]-was.joints().knee[i]));
            }
            was=f;
        }
    }
}
