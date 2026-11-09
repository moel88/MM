package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's G's strange space (2.112.0, 2.112.1) as the user's video lays it out: opening straight on the two upside
 * down facing each other at the edges of a side view (2.112.1, user request: "시전자가 칼을 세워드는 저거 없애고 곧바로
 * 서로 거꾸로 마주보는 컷씬부터 시작"; the one marked at the left, its user at the right, the both of them in the middle
 * of the frame's height); the camera pulled back the same way to a sixth of the frame in from its edges, the knives
 * spread evenly over a band round its user (2.112.1: "검들을 골고루 배치해줘"), all of them in the frame and aimed at the
 * one marked; the violet spikes kept off the cameras and from between them and the two, rising from deep under the frame;
 * the pink panels only through the first part of it.
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
        // the side shot: the one marked at the left edge, its user at the right (the video's: at the edges, 125 of 1080 high)
        var b=XCharaSpace.shot(FROM,YAW,WARN+10);
        double[] u=project(b,user),t=project(b,target);
        assertTrue(t[0]<-.85&&t[0]>-1,"the one marked at the left edge: "+t[0]);
        assertTrue(u[0]>.85&&u[0]<1,"its user at the right edge: "+u[0]);
        assertEquals(0,u[1],.02);assertEquals(0,t[1],.02);
        double high=1.8/(2*u[2]*Math.tan(Math.toRadians(b.fov()/2)));
        assertTrue(high>.09&&high<.15,"their height on the frame "+high+" (the video's 0.116)");
        // the pulled-back shot: at a sixth of the frame in from the edges (the video's 0.16 and 0.84 of its width)
        var c=XCharaSpace.shot(FROM,YAW,SHOT_C+10);
        u=project(c,user);t=project(c,target);
        assertEquals(-.68,t[0],.06);assertEquals(.68,u[0],.06);
        // it holds still through each shot, through the two vanishing, until the world is back
        assertEquals(c.eye(),XCharaSpace.shot(FROM,YAW,WORLD-.01f).eye());
        assertEquals(b.eye(),XCharaSpace.shot(FROM,YAW,SHOT_C-.01f).eye());
    }
    @Test void itOpensStraightOnTheTwoUpsideDownFacingEachOther(){
        // its very first frame is the side shot: no shot from behind its user raising the knife (2.112.0's)
        var first=XCharaSpace.shot(FROM,YAW,WARN);
        assertEquals(XCharaSpace.shot(FROM,YAW,WARN+10).eye(),first.eye());
        Vec3d f=Vec3d.fromPolar(0,YAW);
        assertEquals(XCharaSpace.GAP/2,first.eye().subtract(FROM).dotProduct(f),1e-5,"level with the middle between them, not behind its user");
        for(boolean target:new boolean[]{false,true}){
            assertTrue(XCharaSpace.shown(target,WARN),"both in it from its first moment");
            assertFalse(XCharaSpace.shown(target,WARN-.01f));assertFalse(XCharaSpace.shown(target,VANISH),"the two gone at 7.4 s");
        }
        assertTrue(XCharaSpace.inverted(WARN),"upside down from its first moment (the user: 서로 거꾸로 마주 봄)");
        assertFalse(XCharaSpace.inverted(WARN-.01f));assertTrue(XCharaSpace.inverted(VANISH-1));
    }
    @Test void theKnivesAreSpreadEvenlyRoundItsUserAimedAtTheOneMarked(){
        assertEquals(22,XCharaSpace.KNIVES,"the video's 22");
        assertEquals(XCharaSpace.KNIVES,XCharaSpace.SLOTS.length);
        Vec3d f=Vec3d.fromPolar(0,YAW),c=middleOf(FROM),target=middleOf(XCharaSpace.targetFeet(FROM,YAW));
        var shot=XCharaSpace.shot(FROM,YAW,SHOT_C+10);
        double[][] at=new double[XCharaSpace.KNIVES][];
        double fore=-99,aft=99,top=-99,low=99;
        for(int k=0;k<XCharaSpace.KNIVES;k++){
            var kn=XCharaSpace.knife(FROM,YAW,k);
            assertEquals(1,kn[1].dotProduct(target.subtract(kn[0]).normalize()),1e-9,"knife "+k+" points at the one marked");
            double[] p=project(shot,kn[0]);
            assertTrue(Math.abs(p[0])<.97&&Math.abs(p[1])<.97,"knife "+k+" in the frame: "+p[0]+", "+p[1]);
            double ahead=kn[0].subtract(c).dotProduct(f),up=kn[0].y-c.y;
            at[k]=new double[]{ahead,up};
            fore=Math.max(fore,ahead);aft=Math.min(aft,ahead);top=Math.max(top,up);low=Math.min(low,up);
            // none on its user's body (its blade from end to end clear of them)
            double length=XCharaSpace.knifeSize(k)*XCharaSpace.KNIFE_LENGTH;
            for(double x=-.5;x<=.5;x+=.05){
                Vec3d q=kn[0].add(kn[1].multiply(x*length)).subtract(c);
                assertFalse(Math.abs(q.dotProduct(f))<.55&&Math.abs(q.y)<1.15,"knife "+k+" on its user");
            }
        }
        // the video's band: some four blocks ahead and behind, from nine and a half over them to eight under
        assertTrue(fore>3&&aft<-3,"ahead "+fore+", behind "+aft);
        assertTrue(top>8.5&&low<-7.5,"up "+top+", down "+low);
        // evenly: each one's nearest neighbour neither crowding it nor far off
        for(int k=0;k<at.length;k++){
            double near=99;
            for(int j=0;j<at.length;j++)if(j!=k)near=Math.min(near,Math.hypot(at[k][0]-at[j][0],at[k][1]-at[j][1]));
            assertTrue(near>1.9&&near<3.8,"knife "+k+"'s nearest "+near);
        }
        // and no hole in the band (away from its user's body)
        for(double ahead=-3;ahead<=3;ahead+=.25)for(double up=-7.5;up<=8.5;up+=.25){
            if(Math.hypot(ahead,up)<2)continue;
            double near=99;for(var p:at)near=Math.min(near,Math.hypot(p[0]-ahead,p[1]-up));
            assertTrue(near<2.5,"a hole at "+ahead+", "+up+": "+near);
        }
        assertEquals(0,XCharaSpace.knives(SHOT_C-1));assertEquals(1,XCharaSpace.knives(SHOT_C+3));assertEquals(0,XCharaSpace.knives(WORLD));
    }
    @Test void theSpikesKeepOffTheCamerasAndFromBetweenThemAndTheTwo(){
        var spikes=XCharaSpace.spikes(FROM,YAW);
        assertEquals(XCharaSpace.SPIKES,spikes.length);
        for(float t:new float[]{WARN+5,SHOT_C+5}){
            var s=XCharaSpace.shot(FROM,YAW,t);
            for(var sp:spikes){
                assertTrue(Math.hypot(sp[0]-s.eye().x,sp[2]-s.eye().z)>4,"a spike by the camera");
                assertTrue(sp[1]<FROM.y-15.9,"rising from deep under them");
                double tip=sp[1]+sp[3];
                assertTrue(tip>FROM.y-3.01&&tip<FROM.y+12.01,"its point from under their feet to well over them: "+(tip-FROM.y));
            }
        }
        // the same on every client: steady
        var again=XCharaSpace.spikes(FROM,YAW);for(int i=0;i<spikes.length;i++)assertArrayEquals(spikes[i],again[i]);
    }
    @Test void thePanelsFlyThroughTheFirstPartOfItOnly(){
        int seen=0;
        for(int k=0;k<XCharaSpace.PANELS;k++)for(float t=0;t<END;t+=.5f){
            var p=XCharaSpace.panel(FROM,YAW,k,t);
            if(p.shown()<=0)continue;seen++;
            assertTrue(t>=WARN&&t<WARN+XCharaSpace.PANEL_LIFE,"panel "+k+" at "+t);
            assertTrue(project(XCharaSpace.shot(FROM,YAW,t),p.at())[2]>2,"ahead of the camera");
        }
        assertTrue(seen>0);
        // each shows and goes softly
        for(int k=0;k<XCharaSpace.PANELS;k++){float last=0;for(float t=WARN-1;t<WARN+XCharaSpace.PANEL_LIFE+2;t+=1/3f){float s=XCharaSpace.panel(FROM,YAW,k,t).shown();assertTrue(Math.abs(s-last)<.25f,"panel "+k+" at "+t);last=s;}}
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
