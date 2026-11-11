package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Where the hack's panel is and how its arm holds it (2.115.0, X Chara's Q). In the held item's frame of a hand in the
 * world (x across the arm towards the holder's right, y off the arm's front: up, over a level forearm; z back up the
 * arm) the panel floats out past the hand to the holder's own side, over the palm and a little beyond the hand, its
 * face turned up and back to its holder; the other hand's is its mirror. From one's own eyes it lies on the off hand's
 * side, its face leaning back to them. The arm that holds it comes up with the hack, its forearm about level, the right
 * arm the left's mirror.
 */
class HackPanelTest {
    static Vector3f at(MatrixStack m,float x,float y,float z){return m.peek().getPositionMatrix().transformPosition(new Vector3f(x,y,z));}
    static Vector3f way(MatrixStack m,float x,float y,float z){return m.peek().getPositionMatrix().transformDirection(new Vector3f(x,y,z)).normalize();}

    @Test void inAHandItFloatsOutPastTheHandOverThePalmItsFaceUpAndBackToItsHolder(){
        for(boolean left:new boolean[]{true,false}){
            var m=new MatrixStack();HackPanel.inHand(m,left,0);
            Vector3f mid=at(m,0,0,0),face=way(m,0,1,0),far=way(m,0,0,-1),outer=way(m,left?-1:1,0,0);
            // (the holder's left is -x in the left hand's frame, their right +x in the right's)
            assertEquals(left?-HackPanel.OUT:HackPanel.OUT,mid.x,1e-5,"out to its holder's side");
            assertEquals(HackPanel.OVER,mid.y,.02,"over the palm");
            assertEquals(-HackPanel.BEYOND,mid.z,1e-5,"a little beyond the hand");
            assertTrue(face.y>.8f,"its face up: "+face.y);
            assertTrue(face.z>.4f,"and back up the arm, to its holder: "+face.z);
            assertTrue(far.y>.4f,"its far edge raised: "+far.y);
            assertTrue(outer.z<-.1f&&Math.abs(outer.y)<.2f,"its outer end swung forward a little: "+outer.z);
            // its inner end by the hand: within its own half width of it
            Vector3f inner=at(m,left?HackPanel.W*.5f:-HackPanel.W*.5f,0,0);
            assertTrue(Math.abs(inner.x)<.12f,"its inner end over the hand: "+inner.x);
        }
        // it bobs a little, no more
        var a=new MatrixStack();HackPanel.inHand(a,true,0);
        for(int i=0;i<200;i++){var b=new MatrixStack();HackPanel.inHand(b,true,i*.05f);assertTrue(at(b,0,0,0).distance(at(a,0,0,0))<.02f);}
    }
    @Test void fromOnesOwnEyesItLeansBackToThem(){
        for(boolean left:new boolean[]{true,false}){
            var m=new MatrixStack();HackPanel.inView(m,left);
            Vector3f face=way(m,0,1,0),far=way(m,0,0,-1),right=way(m,1,0,0);
            assertTrue(face.z>.5f&&face.y>.3f,"its face to the eyes and up: "+face);
            assertTrue(far.y>.5f&&far.z<0,"its far edge up and away");
            assertTrue(right.x>.5f,"its writing left to right");
            assertTrue(left?face.x>0:face.x<0,"turned a little to the middle");
        }
    }
    @Test void itIsFoldedAwayALittleBeforeTheRestOfTheHackIsGone(){
        assertTrue(HackPanel.shown(new HackLook.Frame(true,0,0,1)));
        assertTrue(HackPanel.shown(new HackLook.Frame(false,.2f,0,1)));
        assertFalse(HackPanel.shown(new HackLook.Frame(false,.25f,0,1)));
        assertEquals(0,HackLook.wide(false,.25f),1e-6,"closed by then");
        assertTrue(HackPanel.W>HackPanel.D&&HackPanel.T<.05f,"a wide thin slab");
    }

    static ModelPart arm(){return new ModelPart(java.util.List.of(),java.util.Map.of("elbow",new ModelPart(java.util.List.of(),java.util.Map.of())));}
    @Test void theArmComesUpWithTheHackItsForearmAboutLevel(){
        var l=arm();l.pitch=.2f;l.roll=.03f;
        HackPose.apply(l,true,null,0);assertEquals(.2f,l.pitch,"no hack: as it was");
        HackPose.apply(l,true,new HackLook.Frame(true,0,0,1),0);assertEquals(.2f,l.pitch,1e-6,"not up yet as it comes on");
        HackPose.apply(l,true,new HackLook.Frame(true,99,0,1),0);
        float level=l.pitch+l.getChild("elbow").pitch;
        assertEquals(-Math.PI/2,level,.25,"the forearm about level before them: "+Math.toDegrees(level));
        assertTrue(l.roll<-.1f,"out from the body");
        var r=arm();HackPose.apply(r,false,new HackLook.Frame(true,99,0,1),0);
        assertEquals(-l.roll,r.roll,1e-6,"the right arm its mirror");assertEquals(-l.yaw,r.yaw,1e-6);
        assertEquals(HackPose.ELBOW,r.getChild("elbow").pitch,1e-6);
        // it comes up without a jump, and keeps a quarter of the walk's swing
        float was=Float.NaN;
        for(int i=0;i<=340;i++){
            var a=arm();a.pitch=.4f;HackPose.apply(a,true,new HackLook.Frame(true,i/1000f,0,1),0);
            if(!Float.isNaN(was))assertTrue(Math.abs(a.pitch-was)<.02f,"at "+i);
            was=a.pitch;
        }
        assertEquals(HackPose.PITCH+.1f,was,1e-5);
    }
}
