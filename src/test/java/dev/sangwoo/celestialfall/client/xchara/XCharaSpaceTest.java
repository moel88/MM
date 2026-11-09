package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's G's strange space (2.112.0) as the user's video lays it out: its user raising the knife upright, seen from
 * behind; the two upside down facing each other at the edges of a side view (the one marked at the left, its user at the
 * right, the both of them in the middle of the frame's height); the camera pulled back the same way to a sixth of the
 * frame in from its edges, the knives in a crescent round its user, all of them in the frame and aimed at the one marked;
 * the violet spikes kept off the cameras and from between them and the two; the pink panels only through the second shot.
 */
class XCharaSpaceTest {
    static final Vec3d FROM=new Vec3d(10.5,64,-3.5);
    static final float YAW=37;
    /** {@code p} on the frame of {@code s} (16:9): x and y from -1 to 1 across it, and how far ahead of the eye it is. */
    static double[] project(XCharaSpace.Shot s,Vec3d p){
        Vec3d fwd=s.look().subtract(s.eye()).normalize(),right=fwd.crossProduct(new Vec3d(0,1,0)).normalize(),up=right.crossProduct(fwd);
        Vec3d d=p.subtract(s.eye());double z=d.dotProduct(fwd);
        double tanV=Math.tan(Math.toRadians(s.fov()/2)),tanH=tanV*16/9;
        return new double[]{d.dotProduct(right)/z/tanH,d.dotProduct(up)/z/tanV,z};
    }
    static Vec3d middleOf(Vec3d feet){return feet.add(0,XCharaSpace.MIDDLE,0);}

    @Test void theSideShotsHaveTheTwoAtTheVideosPlaces(){
        Vec3d user=middleOf(FROM),target=middleOf(XCharaSpace.targetFeet(FROM,YAW));
        // the second shot: the one marked at the left edge, its user at the right (the video's: at the edges, 125 of 1080 high)
        var b=XCharaSpace.shot(FROM,YAW,SHOT_B+10);
        double[] u=project(b,user),t=project(b,target);
        assertTrue(t[0]<-.85&&t[0]>-1,"the one marked at the left edge: "+t[0]);
        assertTrue(u[0]>.85&&u[0]<1,"its user at the right edge: "+u[0]);
        assertEquals(0,u[1],.02);assertEquals(0,t[1],.02);
        double high=1.8/(2*u[2]*Math.tan(Math.toRadians(b.fov()/2)));
        assertTrue(high>.09&&high<.15,"their height on the frame "+high+" (the video's 0.116)");
        // the third: pulled back, at a sixth of the frame in from the edges (the video's 0.16 and 0.84 of its width)
        var c=XCharaSpace.shot(FROM,YAW,SHOT_C+10);
        u=project(c,user);t=project(c,target);
        assertEquals(-.68,t[0],.06);assertEquals(.68,u[0],.06);
        // it holds still through each shot, through the two vanishing, until the world is back
        assertEquals(c.eye(),XCharaSpace.shot(FROM,YAW,WORLD-.01f).eye());
        assertEquals(b.eye(),XCharaSpace.shot(FROM,YAW,SHOT_C-.01f).eye());
    }
    @Test void theFirstShotIsFromBehindItsUserLookingUpAtTheKnifeAndPushesInSmoothly(){
        Vec3d f=Vec3d.fromPolar(0,YAW);
        var a0=XCharaSpace.shot(FROM,YAW,WARN);var a1=XCharaSpace.shot(FROM,YAW,SHOT_B-.01f);
        assertTrue(a0.eye().subtract(FROM).dotProduct(f)<-1,"behind its user");
        assertTrue(a0.look().y>a0.eye().y,"looking up");
        // its user's head (1.6 up) in the frame, and the knife raised over it (2.4 up, half a block ahead)
        for(var s:new XCharaSpace.Shot[]{a0,a1}){
            double[] head=project(s,FROM.add(0,1.6,0)),knife=project(s,FROM.add(f.multiply(.6)).add(0,2.4,0));
            for(double[] p:new double[][]{head,knife})assertTrue(Math.abs(p[0])<.95&&Math.abs(p[1])<.95&&p[2]>0,p[0]+", "+p[1]);
        }
        Vec3d last=a0.eye();
        for(float t=WARN;t<SHOT_B;t+=1/3f){var s=XCharaSpace.shot(FROM,YAW,t);assertTrue(s.eye().distanceTo(last)<.06,"no jump at "+t);last=s.eye();}
    }
    @Test void whoIsInTheFrameAndUpsideDown(){
        assertTrue(XCharaSpace.shown(false,WARN));assertFalse(XCharaSpace.shown(true,WARN),"the one marked not in the first shot");
        assertTrue(XCharaSpace.shown(true,SHOT_B));assertFalse(XCharaSpace.shown(true,VANISH));assertFalse(XCharaSpace.shown(false,VANISH),"the two gone at 7.4 s");
        assertFalse(XCharaSpace.inverted(SHOT_B-.01f));assertTrue(XCharaSpace.inverted(SHOT_B));assertTrue(XCharaSpace.inverted(VANISH-1),"upside down until they vanish (the user: 서로 거꾸로 마주 봄)");
    }
    @Test void theKnifeIsRaisedThroughTheFirstShotOnly(){
        assertEquals(0,XCharaSpace.raise(WARN-1));assertEquals(1,XCharaSpace.raise(WARN+4),1e-6);assertEquals(1,XCharaSpace.raise(SHOT_B),1e-6);
        assertEquals(0,XCharaSpace.raise(SHOT_B+3),1e-6);
        float last=0;for(float t=WARN-2;t<SHOT_B+5;t+=1/3f){float r=XCharaSpace.raise(t);assertTrue(Math.abs(r-last)<.2f,"smooth at "+t);last=r;}
    }
    @Test void theKnivesLieInACrescentRoundItsUserAimedAtTheOneMarked(){
        Vec3d f=Vec3d.fromPolar(0,YAW),target=middleOf(XCharaSpace.targetFeet(FROM,YAW));
        var c=XCharaSpace.shot(FROM,YAW,SHOT_C+10);
        double topAhead=-99,midAhead=-99;
        for(int k=0;k<XCharaSpace.KNIVES;k++){
            var kn=XCharaSpace.knife(FROM,YAW,k);
            assertEquals(1,kn[1].dotProduct(target.subtract(kn[0]).normalize()),1e-9,"knife "+k+" points at the one marked");
            double[] p=project(c,kn[0]);
            assertTrue(Math.abs(p[0])<.97&&Math.abs(p[1])<.97,"knife "+k+" in the frame: "+p[0]+", "+p[1]);
            double ahead=kn[0].subtract(FROM).dotProduct(f);
            if(k==0||k==XCharaSpace.KNIVES-1)topAhead=Math.max(topAhead,ahead);
            if(k==XCharaSpace.KNIVES/2)midAhead=ahead;
        }
        assertTrue(midAhead>.5,"its middle just ahead of its user: "+midAhead);
        assertTrue(topAhead<-3,"its horns far behind: "+topAhead);
        assertEquals(22,XCharaSpace.KNIVES,"the video's 22");
        assertEquals(0,XCharaSpace.knives(SHOT_C-1));assertEquals(1,XCharaSpace.knives(SHOT_C+3));assertEquals(0,XCharaSpace.knives(WORLD));
    }
    @Test void theSpikesKeepOffTheCamerasAndFromBetweenThemAndTheTwo(){
        var spikes=XCharaSpace.spikes(FROM,YAW);
        assertEquals(XCharaSpace.SPIKES,spikes.length);
        for(float t:new float[]{SHOT_B+5,SHOT_C+5}){
            var s=XCharaSpace.shot(FROM,YAW,t);
            for(var sp:spikes){
                assertTrue(Math.hypot(sp[0]-s.eye().x,sp[2]-s.eye().z)>4,"a spike by the camera");
                assertTrue(sp[1]<FROM.y-2.9,"rising from below them");
            }
        }
        // the same on every client: steady
        var again=XCharaSpace.spikes(FROM,YAW);for(int i=0;i<spikes.length;i++)assertArrayEquals(spikes[i],again[i]);
    }
    @Test void thePanelsFlyThroughTheSecondShotOnly(){
        int seen=0;
        for(int k=0;k<XCharaSpace.PANELS;k++)for(float t=0;t<END;t+=.5f){
            var p=XCharaSpace.panel(FROM,YAW,k,t);
            if(p.shown()<=0)continue;seen++;
            assertTrue(t>=SHOT_B&&t<SHOT_B+XCharaSpace.PANEL_LIFE,"panel "+k+" at "+t);
            assertTrue(project(XCharaSpace.shot(FROM,YAW,t),p.at())[2]>2,"ahead of the camera");
        }
        assertTrue(seen>0);
        // each shows and goes softly
        for(int k=0;k<XCharaSpace.PANELS;k++){float last=0;for(float t=SHOT_B-1;t<SHOT_B+XCharaSpace.PANEL_LIFE+2;t+=1/3f){float s=XCharaSpace.panel(FROM,YAW,k,t).shown();assertTrue(Math.abs(s-last)<.25f,"panel "+k+" at "+t);last=s;}}
    }
    @Test void theKnifesShapeIsTheModels(){
        assertEquals(1,SwordDance.depth(0),1e-9);assertEquals(1,SwordDance.depth(.2),1e-9);assertEquals(0,SwordDance.depth(1),1e-9);
        double last=1;for(double u=0;u<=1;u+=.01){double d=SwordDance.depth(u);assertTrue(d<=last+1e-9);last=d;}
        // make_knife.py's measured profile, within 0.02
        double[][] profile={{.22,1.0},{.30,.98},{.37,.94},{.45,.912},{.52,.88},{.595,.80},{.67,.74},{.74,.63},{.82,.51},{.89,.36},{.93,.26},{.967,.12},{.985,.06}};
        for(var p:profile)assertEquals(p[1],SwordDance.depth(p[0]),.025,"at "+p[0]);
        assertEquals(4.14,SwordDance.ASPECT,.01);
    }
}
