package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's terrain delete as its user does it (2.133.0, the user's: "참격 모션 전부 삭제하고 패널을 건들이고"): no leap, no
 * slash; they stand as they stand with the knife and the panel's arm comes up in front of them, its hand tapping the panel
 * as the grounds are marked, the panel lit as it is tapped, their look bowed to it; then the arm goes back out with the
 * panel. Its one sound is the panel's click on the tap.
 */
class ErasePressTest {
    @Test void itIsAPressOfThePanelTappedAsTheGroundsAreMarked(){
        assertSame(ErasePress.CLIP,KnifeCombo.clipOf(ERASE),"the press is the terrain delete's motion");
        assertEquals(EraseRules.LENGTH/20f,ErasePress.CLIP.length(),1e-6);
        assertEquals(ErasePress.LIFE,KnifeCombo.life(ERASE),1e-6);
        float[] start=ErasePress.pressAt(0,Float.NaN);assertEquals(0,start[0],1e-6,"the arm out with the panel as it begins");assertEquals(0,start[1],1e-6);
        float[] tap=ErasePress.pressAt(ErasePress.TAP,Float.NaN);
        assertEquals(1,tap[0],1e-6,"the arm up in front of them");assertEquals(1,tap[1],1e-6,"the hand on the panel as the grounds are marked");
        assertTrue(tap[2]>.9f,"lit as it is tapped");assertEquals(1,tap[3],1e-6,"violet, as the hack's presses but the restore's");
        float[] end=ErasePress.pressAt(EraseRules.LENGTH/20f,Float.NaN);assertEquals(0,end[0],1e-6,"the arm back out by its end");assertEquals(0,end[2],1e-6);
        // no jump of the arm or the hand from frame to frame (a quick tap, as their T's: in four frames); the light flashes in over two
        float[] last=start;
        for(float s=0;s<=ErasePress.LIFE;s+=1/60f){
            float[] p=ErasePress.pressAt(s,Float.NaN);
            for(int k=0;k<2;k++)assertTrue(Math.abs(p[k]-last[k])<.45f,"no jump at "+s+" ("+k+")");
            assertTrue(Math.abs(p[2]-last[2])<.9f,"the light in over a frame or two at "+s);
            last=p;
        }
        // stopped short before the tap: the arm goes back, the panel is not lit
        float[] cut=ErasePress.pressAt(.2f,.05f);assertEquals(0,cut[0],1e-6);assertEquals(0,cut[2],1e-6);
    }
    @Test void itsBodyIsTheStanceButThePanelsArmUpAndTheLookBowedToIt(){
        var rest=KnifeCombo.AT_REST.joints();
        var up=ErasePress.fix(ErasePress.TAP,KnifeCombo.AT_REST).joints();
        for(int j:new int[]{MotionPose.BODY,MotionPose.RIGHT_ARM,MotionPose.RIGHT_LEG,MotionPose.LEFT_LEG})
            assertArrayEquals(rest.joint[j],up.joint[j],1e-4f,"the stance held but for the panel's arm ("+j+")");
        assertEquals(rest.elbow[0],up.elbow[0],1e-4f);
        var idle=new MotionPose();XCharaIdle.arms(idle,true,1,1,0,1,1);
        assertArrayEquals(idle.joint[MotionPose.LEFT_ARM],up.joint[MotionPose.LEFT_ARM],1e-4f,"the panel's arm as their E's and T's press has it");
        assertEquals(idle.elbow[1],up.elbow[1],1e-4f);
        assertEquals(XCharaIdle.LOOK_DOWN,up.joint[MotionPose.HEAD][0],1e-3f,"looking down at the panel");
        var out=ErasePress.fix(0,KnifeCombo.AT_REST).joints();var held=new MotionPose();XCharaIdle.arms(held,true,1,1,0,0,0);
        assertArrayEquals(held.joint[MotionPose.LEFT_ARM],out.joint[MotionPose.LEFT_ARM],1e-4f,"out with the panel as it is held standing");
        assertArrayEquals(rest.joint[MotionPose.HEAD],out.joint[MotionPose.HEAD],1e-4f);
    }
    @Test void itsSoundIsThePanelsClickOnTheTap(){
        assertEquals(1,ErasePress.CUES.length);
        assertEquals("xchara_jab_press",ErasePress.CUES[0][0],"the user's own \"띡\"");
        assertEquals(EraseRules.MARK-1,ErasePress.CUES[0][1],"a tick before the tap: its onset is on it");
    }
}
