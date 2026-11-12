package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Where the hack's panel is (2.115.0, X Chara's Q; 2.116.1: as the user's video has it). The arm that holds it hangs
 * straight, out from the body (XCharaIdle.PANEL_OUT): by its hand the panel is a screen, its long side (the way its
 * writing runs) fore and aft, its short side rising out to its holder's side, its face in and up to its holder's eyes;
 * it floats below and outside the hand, a gap between them, the hand over its lower half, a little ahead of the arm;
 * the other hand's is its mirror. From one's own eyes it lies on the off hand's side, its face leaning back to them.
 */
class HackPanelTest {
    static Vector3f at(MatrixStack m,float x,float y,float z){return m.peek().getPositionMatrix().transformPosition(new Vector3f(x,y,z));}
    static Vector3f way(MatrixStack m,float x,float y,float z){return m.peek().getPositionMatrix().transformDirection(new Vector3f(x,y,z)).normalize();}
    /**
     * The held item's frame at the hand of an arm rolled {@code roll} degrees (as the model rolls it), as
     * HeldItemFeatureRenderer has it, from the arm's own frame at its shoulder's pivot (blocks of the model: x to the
     * holder's left, y down, z back).
     */
    static MatrixStack hand(boolean left,float roll){
        var m=new MatrixStack();
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        m.translate((left?-1:1)/16f,.125f,-.625f);
        return m;
    }
    /** The arm's roll as it holds the panel: out from the body (the left arm's outward roll is negative). */
    static float out(boolean left){return left?-XCharaIdle.PANEL_OUT:XCharaIdle.PANEL_OUT;}

    @Test void byTheHandOfTheArmThatHangsOutItIsAScreenTurnedInAndUpToItsHolder(){
        for(boolean left:new boolean[]{true,false}){
            float side=left?1:-1;   // the holder's own side, in the model's x (x to their left, y down, z behind)
            var m=hand(left,out(left));HackPanel.inHand(m,left,0,null);
            Vector3f mid=at(m,0,0,0),face=way(m,0,1,0),top=way(m,0,0,-1),along=way(m,1,0,0);
            // its long side, the way its writing runs, fore and aft: read from that side, from behind its holder to ahead
            assertTrue(along.z*side<-.999f,"its writing fore and aft, as its holder reads it from that side: "+along);
            assertTrue(HackPanel.W>HackPanel.D*1.4f,"and that is its long side");
            // its short side rising out to the side: the top of the writing the outer edge, raised
            assertEquals(0,top.z,1e-4,"its short side square across");
            assertEquals(HackPanel.TILT,Math.toDegrees(Math.atan2(-top.y,top.x*side)),.01,"its outer edge raised: "+top);
            assertTrue(HackPanel.TILT>30&&HackPanel.TILT<50,"some 40 degrees, as the video has it");
            // its face in and up: to its holder's head (the middle of the head from the shoulder's pivot, model px)
            assertTrue(face.x*side<-.5f&&face.y<-.6f,"its face in and up: "+face);
            Vector3f head=new Vector3f(-side*5/16f,-6/16f,0);
            double off=Math.toDegrees(Math.acos(new Vector3f(head).sub(mid).normalize().dot(face)));
            assertTrue(off<15,"turned to its holder's eyes: "+off+" degrees off");
            // by the hand: its middle out past the middle of the arm's end, below it, a little ahead
            Vector3f end=at(hand(left,out(left)),0,-HackPanel.FRONT,0);
            assertEquals(HackPanel.OUT,(mid.x-end.x)*side,1e-4,"out past the hand");
            assertEquals(HackPanel.DOWN,mid.y-end.y,1e-4,"below it");
            assertEquals(HackPanel.AHEAD,end.z-mid.z,1e-4,"a little ahead of the arm");
            assertTrue(HackPanel.OUT>.15f&&HackPanel.OUT<.32f&&HackPanel.DOWN>.04f&&HackPanel.DOWN<.2f&&HackPanel.AHEAD>.05f&&HackPanel.AHEAD<.3f);
        }
        // it bobs a little, no more
        var a=hand(true,out(true));HackPanel.inHand(a,true,0,null);
        for(int i=0;i<200;i++){
            var b=hand(true,out(true));HackPanel.inHand(b,true,i*.05f,null);
            assertTrue(at(b,0,0,0).distance(at(a,0,0,0))<.02f);
            assertTrue(Math.toDegrees(Math.acos(Math.min(1,way(b,0,1,0).dot(way(a,0,1,0)))))<2,"and rocks a little, no more");
        }
    }
    @Test void itFloatsBelowAndOutsideTheHandAGapBetweenThemTheHandOverItsLowerHalf(){
        for(boolean left:new boolean[]{true,false}){
            // in the arm's own frame the arm is a box (model px/16 from the shoulder's pivot), whatever its roll
            float x0=(left?-1:-3)/16f,x1=(left?3:1)/16f,y0=-2/16f,y1=10/16f,z0=-2/16f,z1=2/16f;
            // (the arm standing out with the panel: rolled, the box with it)
            var m=new MatrixStack();
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            m.translate((left?-1:1)/16f,.125f,-.625f);
            Vector3f end=at(m,0,-HackPanel.FRONT,0);
            HackPanel.inHand(m,left,0,null);
            float nearest=9;Vector3f where=null;
            for(int i=0;i<=24;i++)for(int j=0;j<=16;j++)for(int k=0;k<2;k++){
                Vector3f p=at(m,(i/24f-.5f)*HackPanel.W,k==0?0:-HackPanel.T,(j/16f-.5f)*HackPanel.D);
                float dx=Math.max(Math.max(x0-p.x,0),p.x-x1),dy=Math.max(Math.max(y0-p.y,0),p.y-y1),dz=Math.max(Math.max(z0-p.z,0),p.z-z1);
                float d=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                if(d<nearest){nearest=d;where=p;}
            }
            assertTrue(nearest>.1f,"a gap between it and the arm (the video's floats off the hand): "+nearest);
            assertTrue(nearest<.3f,"and close by it: "+nearest);
            assertTrue(where.y>8/16f,"by its hand, not its shoulder: "+where.y);
            // the middle of the arm's end, in the panel's own frame: over its face, over the near half of it (its keys)
            Vector3f over=new Matrix4f(m.peek().getPositionMatrix()).invert().transformPosition(new Vector3f(end));
            assertTrue(over.y>.1f,"the hand above its face: "+over);
            assertTrue(Math.abs(over.x)<HackPanel.W*.5f,"between its two ends");
            assertTrue(over.z>0&&over.z<HackPanel.D*.5f,"over its near half, the keys: "+over);
        }
    }
    @Test void whateverTheArmDoesItGoesWithTheHandAndStandsAsItDid(){
        // a guard's raised forearm, a hit's swing: the panel keeps its own turn in the body's frame, by the hand's end
        var still=hand(true,out(true));HackPanel.inHand(still,true,0,null);
        Vector3f face=way(still,0,1,0),across=way(still,1,0,0);
        Vector3f off=at(still,0,0,0).sub(at(hand(true,out(true)),0,0,0));
        float k=(float)Math.PI/180;
        for(float[] e:new float[][]{{-60,10,-20,-100},{35,-15,-8,-20},{-120,0,-40,0},{0,0,-22,0},{-90,30,0,-45}}){
            // (the arm as the model turns it: its shoulder's turn, then its elbow's about the elbow, 4 px down)
            var turn=new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k).rotateX(e[3]*k);
            var m=new MatrixStack();
            m.multiply(new Quaternionf().rotationZYX(e[2]*k,e[1]*k,e[0]*k));
            m.translate(0,4/16f,0);m.multiply(RotationAxis.POSITIVE_X.rotation(e[3]*k));m.translate(0,-4/16f,0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            m.translate(-1/16f,.125f,-.625f);
            Vector3f handAt=at(m,0,0,0);
            HackPanel.inHand(m,true,0,turn);
            assertTrue(way(m,0,1,0).distance(face)<1e-4f,"its face as it stands: "+way(m,0,1,0)+" for "+java.util.Arrays.toString(e));
            assertTrue(way(m,1,0,0).distance(across)<1e-4f,"its writing fore and aft still");
            assertTrue(at(m,0,0,0).sub(handAt).distance(off)<1e-4f,"as far from the hand's end, the same way");
        }
    }
    @Test void theOtherHandsIsItsMirror(){
        var l=hand(true,out(true));HackPanel.inHand(l,true,0,null);var r=hand(false,out(false));HackPanel.inHand(r,false,0,null);
        for(float[] p:new float[][]{{0,0,0},{HackPanel.W*.5f,0,0},{0,0,HackPanel.D*.5f},{0,.1f,0}}){
            Vector3f a=at(l,p[0],p[1],p[2]),b=at(r,-p[0],p[1],p[2]);
            assertEquals(a.x,-b.x,1e-5);assertEquals(a.y,b.y,1e-5);assertEquals(a.z,b.z,1e-5);
        }
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
}
