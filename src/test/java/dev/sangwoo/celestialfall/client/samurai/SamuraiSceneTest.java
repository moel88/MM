package dev.sangwoo.celestialfall.client.samurai;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The samurai's G's cutscene as the user's video lays it out (2.143.0): its parts in the video's order, the close shot
 * with its user at one side of the frame and the one marked behind them at the other, the side shots with the one marked
 * in the middle (its user beyond them at the frame's edge), the black ground's horizon at their feet, and the four blades
 * about them, each on a line through their body, cutting in the video's order.
 */
class SamuraiSceneTest {
    static final Vec3d HOLD=new Vec3d(10.5,64,-3.5);
    static final float YAW=37;
    static final Vec3d F=Vec3d.fromPolar(0,YAW),R=SamuraiScene.right(F);
    static final Vec3d SPOT=HOLD.subtract(F.multiply(SHORT)).add(R.multiply(ASIDE));
    /** {@code p} on the frame of {@code s} (16:9): x and y from -1 to 1 across it, and how far ahead of the eye it is. */
    static double[] project(SamuraiScene.Shot s,Vec3d p){
        Vec3d fwd=s.look().subtract(s.eye()).normalize(),right=fwd.crossProduct(new Vec3d(0,1,0)).normalize(),up=right.crossProduct(fwd);
        Vec3d d=p.subtract(s.eye());double z=d.dotProduct(fwd);
        double tanV=Math.tan(Math.toRadians(s.fov()/2)),tanH=tanV*16/9;
        return new double[]{d.dotProduct(right)/z/tanH,d.dotProduct(up)/z/tanV,z};
    }

    @Test void itsPartsComeInTheVideosOrder(){
        SamuraiScene.Part last=SamuraiScene.Part.BEFORE;
        for(float s=0;s<8;s+=1/120f){
            var p=SamuraiScene.part(s);
            assertTrue(p.ordinal()>=last.ordinal(),"parts never go back: "+p+" after "+last+" at "+s);last=p;
        }
        assertEquals(SamuraiScene.Part.AFTER,last);
        assertEquals(SamuraiScene.Part.BEFORE,SamuraiScene.part(.99f));
        assertEquals(SamuraiScene.Part.DASH_WHITE,SamuraiScene.part(1.01f));
        assertEquals(SamuraiScene.Part.DASH_BURST,SamuraiScene.part(1.15f));
        assertEquals(SamuraiScene.Part.CLOSE_UP,SamuraiScene.part(2.5f));
        assertEquals(SamuraiScene.Part.CUT_LINE,SamuraiScene.part(3.21f));
        assertEquals(SamuraiScene.Part.CUT_WASH,SamuraiScene.part(3.25f));
        assertEquals(SamuraiScene.Part.CUT_BODIES,SamuraiScene.part(3.28f));assertEquals(SamuraiScene.Part.CUT_BODIES,SamuraiScene.part(3.31f));
        assertEquals(SamuraiScene.Part.CUT_SIGN,SamuraiScene.part(3.35f));
        assertEquals(SamuraiScene.Part.GREY_WORLD,SamuraiScene.part(3.8f));
        assertEquals(SamuraiScene.Part.RING_WHITE,SamuraiScene.part(4.21f));assertEquals(SamuraiScene.Part.RING_BLACK,SamuraiScene.part(4.25f));
        assertEquals(SamuraiScene.Part.VOID_GROUND,SamuraiScene.part(5f));assertEquals(SamuraiScene.Part.VOID_GROUND,SamuraiScene.part(6.6f));
        assertEquals(SamuraiScene.Part.OUT_GREY,SamuraiScene.part(6.7f));assertEquals(SamuraiScene.Part.OUT_WHITE,SamuraiScene.part(6.75f));
        // the world is there in the close shot and the grey one, and nowhere else between the dash and its return
        for(var p:SamuraiScene.Part.values()){
            boolean world=p==SamuraiScene.Part.BEFORE||p==SamuraiScene.Part.CLOSE_UP||p==SamuraiScene.Part.GREY_WORLD||p==SamuraiScene.Part.AFTER;
            assertEquals(!world,SamuraiScene.flat(p),p.name());
        }
        assertFalse(SamuraiScene.shown(.99f));assertTrue(SamuraiScene.shown(1f)&&SamuraiScene.shown(6.7f));assertFalse(SamuraiScene.shown(SamuraiScene.WORLD));
        // the server's ticks fall in the parts they belong to
        assertEquals(SamuraiScene.Part.CLOSE_UP,SamuraiScene.part(ARRIVE/20f));assertEquals(SamuraiScene.Part.CLOSE_UP,SamuraiScene.part(SHEATHE/20f));
        for(float at:SLASH_SEC)assertEquals(SamuraiScene.Part.VOID_GROUND,SamuraiScene.part(at));
        assertEquals(SamuraiScene.Part.AFTER,SamuraiScene.part(LAUNCH/20f));
    }
    @Test void theFlatFramesAreBlackAndWhiteAsTheVideos(){
        // black bodies on white, then white bodies on black; the one marked white on the black ground
        assertEquals(1,SamuraiScene.ground(SamuraiScene.Part.CUT_WASH));assertEquals(0,SamuraiScene.body(SamuraiScene.Part.CUT_WASH,true));assertEquals(0,SamuraiScene.body(SamuraiScene.Part.CUT_WASH,false));
        assertEquals(0,SamuraiScene.ground(SamuraiScene.Part.CUT_BODIES));assertEquals(1,SamuraiScene.body(SamuraiScene.Part.CUT_BODIES,true));
        assertEquals(1,SamuraiScene.ground(SamuraiScene.Part.RING_WHITE));assertEquals(1,SamuraiScene.body(SamuraiScene.Part.RING_WHITE,true));
        assertEquals(0,SamuraiScene.ground(SamuraiScene.Part.RING_BLACK));assertEquals(0,SamuraiScene.body(SamuraiScene.Part.RING_BLACK,true));
        assertEquals(0,SamuraiScene.ground(SamuraiScene.Part.VOID_GROUND));assertEquals(1,SamuraiScene.body(SamuraiScene.Part.VOID_GROUND,true));
        // its user is in none of the frames about the one marked alone
        for(var p:new SamuraiScene.Part[]{SamuraiScene.Part.RING_WHITE,SamuraiScene.Part.RING_BLACK,SamuraiScene.Part.VOID_GROUND})assertTrue(SamuraiScene.body(p,false)<0,p.name());
        assertTrue(SamuraiScene.DISC_WHITE<SamuraiScene.DISC_BLACK);
    }
    @Test void theCloseShotHasItsUserAtOneSideAndTheOneMarkedBehindAtTheOther(){
        for(float s:new float[]{1.25f,2.2f,3.1f}){
            var shot=SamuraiScene.shot(SPOT,HOLD,F,1,s);
            double[] u=project(shot,SPOT.add(0,.9,0)),t=project(shot,HOLD.add(0,.9,0));
            // the camera looks the way its user came: its user (who stands to the right of that way) at the right, nearer
            assertTrue(u[0]>.15&&u[0]<.6,"its user right of the middle: "+u[0]+" at "+s);
            assertTrue(t[0]<-.2&&t[0]>-.85,"the one marked left of it: "+t[0]+" at "+s);
            assertTrue(u[2]<t[2],"its user in front");
            // low: the eye under their middles
            assertTrue(shot.eye().y<HOLD.y+.8);
            double high=1.8/(2*u[2]*Math.tan(Math.toRadians(shot.fov()/2)));
            assertTrue(high>.5&&high<.75,"its user's height on the frame "+high+" (the video's some 0.6)");
        }
        // it pushes in
        assertTrue(SamuraiScene.shot(SPOT,HOLD,F,1,3.1f).eye().distanceTo(SPOT)<SamuraiScene.shot(SPOT,HOLD,F,1,1.3f).eye().distanceTo(SPOT));
        // mirrored when its user stands at the other side
        Vec3d other=HOLD.subtract(F.multiply(SHORT)).subtract(R.multiply(ASIDE));
        assertTrue(project(SamuraiScene.shot(other,HOLD,F,1,2f),other.add(0,.9,0))[0]<-.15);
    }
    @Test void theWideShotsHaveTheOneMarkedInTheMiddle(){
        for(int side:new int[]{1,-1}){
            Vec3d spot=HOLD.subtract(F.multiply(SHORT)).add(R.multiply(ASIDE*side));
            var grey=SamuraiScene.shot(spot,HOLD,F,side,3.6f);
            double[] t=project(grey,HOLD.add(0,.9,0));
            assertEquals(0,t[0],.02);
            double high=1.8/(2*t[2]*Math.tan(Math.toRadians(grey.fov()/2)));
            assertTrue(high>.24&&high<.31,"their height on the grey frame "+high+" (the video's 0.27)");
            // its user beyond them, at the frame's edge on their own side
            Vec3d user=SamuraiScene.userFeet(spot,HOLD,F,side,3.6f).add(0,.9,0);
            double[] u=project(grey,user);
            assertTrue(u[0]*side>.8&&u[0]*side<1.02,"its user at the frame's edge: "+u[0]);
            // the black ground: further off, its horizon at their feet, their middle at the frame's
            var black=SamuraiScene.shot(spot,HOLD,F,side,5f);
            double[] feet=project(black,HOLD),mid=project(black,HOLD.add(0,.9,0));
            double horizon=project(black,HOLD.add(SamuraiScene.sideLook(F,side).multiply(5000)))[1];
            assertEquals(horizon,feet[1],.05,"the horizon at their feet");
            assertEquals(0,mid[1],.05);
            high=1.8/(2*mid[2]*Math.tan(Math.toRadians(black.fov()/2)));
            assertTrue(high>.18&&high<.23,"their height on the black ground "+high+" (the video's 0.2)");
            // the wide shots look the way its user came (at the one marked's front), the frame's right to the right of it
            Vec3d look=grey.look().subtract(grey.eye());
            assertTrue(new Vec3d(look.x,0,look.z).normalize().dotProduct(F)>.999);
            assertEquals(F,SamuraiScene.sideLook(F,side));
            assertEquals(side,SamuraiScene.sideRight(F,side).dotProduct(R),1e-6);
        }
        assertFalse(SamuraiScene.away(3.19f));assertTrue(SamuraiScene.away(3.2f)&&SamuraiScene.away(6.7f));assertFalse(SamuraiScene.away(SamuraiScene.WORLD));
        assertEquals(SPOT,SamuraiScene.userFeet(SPOT,HOLD,F,1,2f));
    }
    @Test void theFourBladesWaitAboutThemAndCutThroughTheirBody(){
        var shot=SamuraiScene.shot(SPOT,HOLD,F,1,5f);
        for(int k=0;k<4;k++){
            double[] b=SamuraiScene.BLADES[k];
            assertEquals(1,Math.hypot(b[2],b[3]),1e-9,"blade "+k+" points a unit way");
            // its point off their body, in the frame
            Vec3d tip=SamuraiScene.plane(HOLD,F,1,b[0],b[1]);
            double[] p=project(shot,tip);
            assertTrue(Math.abs(p[0])<.9&&Math.abs(p[1])<.95,"blade "+k+"'s point in the frame: "+p[0]+", "+p[1]);
            assertTrue(Math.hypot(b[0],b[1]-.9)>1.6,"blade "+k+"'s point off their body");
            // its line goes through their body (within their height, within a third of a block of their middle line)
            double along=-b[0]*b[2]+(.9-b[1])*b[3],x=b[0]+b[2]*along,y=b[1]+b[3]*along;
            assertTrue(along>0,"blade "+k+" points at them");
            assertTrue(Math.abs(x)<.35&&y>0&&y<1.8,"blade "+k+"'s line through their body: "+x+", "+y);
            assertTrue(SamuraiScene.waits(k,SLASH_SEC[k]-.01f));assertFalse(SamuraiScene.waits(k,SLASH_SEC[k]));assertFalse(SamuraiScene.waits(k,4.1f));
            assertEquals(0,SamuraiScene.cut(k,SLASH_SEC[k]-.01f));
            assertTrue(SamuraiScene.cut(k,SLASH_SEC[k])>.8,"a broad band as it goes through");
            assertTrue(SamuraiScene.cut(k,SLASH_SEC[k]+.3f)<.06&&SamuraiScene.cut(k,SLASH_SEC[k]+.3f)>.03,"then the thin line it leaves");
        }
        // the video's: from the left level, from the upper right, from the upper left, from under them
        assertTrue(SamuraiScene.BLADES[0][0]<0&&SamuraiScene.BLADES[0][3]==0);
        assertTrue(SamuraiScene.BLADES[1][0]>0&&SamuraiScene.BLADES[1][1]>1.8&&SamuraiScene.BLADES[1][3]<0);
        assertTrue(SamuraiScene.BLADES[2][0]<0&&SamuraiScene.BLADES[2][1]>1.8&&SamuraiScene.BLADES[2][3]<0);
        assertTrue(SamuraiScene.BLADES[3][1]<0&&SamuraiScene.BLADES[3][3]==1);
    }
    @Test void theOneMarkedIsBentOverThenThrownAboutByTheCuts(){
        assertArrayEquals(new float[]{0,0,0},SamuraiScene.reel(3.1f));
        float[] bent=SamuraiScene.reel(5f);
        assertTrue(bent[0]>12&&bent[0]<24,"leaning over: "+bent[0]);assertEquals(0,bent[1]);
        assertTrue(bent[2]>15&&bent[2]<30,"bent forward: "+bent[2]);
        float last=bent[0];float lift=0;
        for(int k=0;k<4;k++){
            float[] r=SamuraiScene.reel(SLASH_SEC[k]+.12f);
            assertTrue(Math.abs(r[0]-last)>30,"cut "+k+" throws them: "+last+" to "+r[0]);
            assertTrue(r[1]>lift,"and lifts them");last=r[0];lift=r[1];
        }
        assertTrue(lift<.6);
        // thrown one way, then the other, then over
        assertTrue(SamuraiScene.reel(SLASH_SEC[0]+.12f)[0]<0&&SamuraiScene.reel(SLASH_SEC[1]+.12f)[0]>0&&SamuraiScene.reel(SLASH_SEC[3]+.12f)[0]<-60);
        // nothing jumps: a frame of sixty moves them less than half a cut's turn
        for(float s=3f;s<6.7f;s+=1/60f){
            float[] a=SamuraiScene.reel(s),b=SamuraiScene.reel(s+1/60f);
            assertTrue(Math.abs(b[0]-a[0])<40,"turn in one frame at "+s+": "+(b[0]-a[0]));
        }
    }
    @Test void theRingClosesOnThem(){
        assertEquals(0,SamuraiScene.ring(3.5f));assertEquals(0,SamuraiScene.ring(4.2f));
        double last=9;
        for(float s=3.6f;s<4.19f;s+=.05f){double r=SamuraiScene.ring(s);assertTrue(r<last&&r>1.7,"closing: "+r);last=r;}
    }
}
