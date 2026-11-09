package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The strange space's depths (2.112.1, user request: "이상한 공간 배경도 멋있게 꾸며줘"): all of it behind the two from
 * the side cameras (the abyss under them); its stars over the whole frame of both shots and its ring round the middle
 * between them, in the frame; its embers rising softly, never by a camera; its streaks falling far behind; its spikes
 * coming out of the dark from nothing at their bases.
 */
class SpaceBackdropTest {
    static final Vec3d FROM=new Vec3d(10.5,64,-3.5);
    static final float YAW=37;
    static final Vec3d L=XCharaSpace.left(XCharaSpace.f(YAW)),MID=XCharaSpace.middle(FROM,YAW);
    /** How far toward the side cameras {@code p} is from the middle between the two (minus: behind them). */
    static double toward(double x,double z){return new Vec3d(x-MID.x,0,z-MID.z).dotProduct(L);}

    @Test void itAllLiesBehindTheTwoFromTheSideCameras(){
        for(var s:SpaceBackdrop.farSpikes(FROM,YAW))assertTrue(toward(s[0],s[2])<-30,"a far spike "+toward(s[0],s[2]));
        for(var c:SpaceBackdrop.clouds(FROM,YAW))assertTrue(toward(c[0],c[2])<-55,"a cloud");
        for(var s:SpaceBackdrop.stars(FROM,YAW))assertTrue(toward(s[0],s[2])<-80,"a star");
        var ring=SpaceBackdrop.ringMiddle(FROM,YAW);assertTrue(toward(ring.x,ring.z)<-30,"the ring");
        assertTrue(SpaceBackdrop.abyssMiddle(FROM,YAW).y<FROM.y-25,"the abyss far under them");
    }
    @Test void itsStarsFillBothShotsAndItsRingIsRoundTheMiddleInTheFrame(){
        for(float t:new float[]{WARN+10,SHOT_C+10}){
            var shot=XCharaSpace.shot(FROM,YAW,t);
            boolean[][] cell=new boolean[4][3];
            for(var s:SpaceBackdrop.stars(FROM,YAW)){
                double[] p=XCharaSpaceTest.project(shot,new Vec3d(s[0],s[1],s[2]));
                if(p[2]>0&&Math.abs(p[0])<1&&Math.abs(p[1])<1)cell[(int)((p[0]+1)*2)][(int)((p[1]+1)*1.5)]=true;
            }
            for(int x=0;x<4;x++)for(int y=0;y<3;y++)assertTrue(cell[x][y],"no star in a part of the frame at "+t+": "+x+", "+y);
            Vec3d c=SpaceBackdrop.ringMiddle(FROM,YAW);
            double[] mid=XCharaSpaceTest.project(shot,c),top=XCharaSpaceTest.project(shot,c.add(0,SpaceBackdrop.RING_R,0)),bottom=XCharaSpaceTest.project(shot,c.subtract(0,SpaceBackdrop.RING_R,0));
            assertEquals(0,mid[0],.01,"round the middle between them");
            assertTrue(top[1]<.95&&bottom[1]>-.95&&top[1]>.4,"its top "+top[1]+", its bottom "+bottom[1]);
        }
    }
    @Test void itsEmbersRiseSoftlyAndNeverByACamera(){
        Vec3d b=XCharaSpace.shot(FROM,YAW,WARN).eye(),c=XCharaSpace.shot(FROM,YAW,SHOT_C).eye();
        for(int i=0;i<SpaceBackdrop.EMBERS;i++){
            double[] last=null;int rose=0,fell=0;
            for(float t=WARN;t<WORLD;t+=1/3f){
                double[] e=SpaceBackdrop.ember(FROM,YAW,i,t);
                Vec3d p=new Vec3d(e[0],e[1],e[2]);
                assertTrue(p.distanceTo(b)>4&&p.distanceTo(c)>4,"ember "+i+" by a camera");
                if(last!=null){
                    double moved=p.distanceTo(new Vec3d(last[0],last[1],last[2]));
                    // as it wraps round from the top of its run to the bottom it shows not at all
                    if(moved>.2)assertTrue(e[3]<.02&&last[3]<.05,"ember "+i+" jumps where it shows at "+t+": "+e[3]);
                    else{if(e[1]>last[1])rose++;else fell++;}
                    assertTrue(Math.abs(e[3]-last[3])<.12,"ember "+i+" pops at "+t);
                }
                last=e;
            }
            assertTrue(rose>fell*20,"ember "+i+" rises");
        }
    }
    @Test void itsStreaksFallFarBehind(){
        for(int i=0;i<30;i++){
            double[] k=SpaceBackdrop.streak(FROM,YAW,i);
            assertTrue(k[4]<0,"streak "+i+" falls");
            assertTrue(toward(k[0],k[2])<-40,"streak "+i+" far behind");
            assertTrue(toward(k[0]+k[3]*SpaceBackdrop.STREAK_LIFE,k[2]+k[5]*SpaceBackdrop.STREAK_LIFE)<-40,"and stays there");
        }
    }
    @Test void itsSpikesComeOutOfTheDarkFromNothing(){
        float[] a=SpaceBackdrop.SPIKE_ALPHA;double[] w=SpaceBackdrop.SPIKE_WIDE,at=SpaceBackdrop.SPIKE_AT;
        assertEquals(0,a[0],"nothing at its base (it shows in the frame from far off)");assertEquals(0,a[a.length-1],"nor at its point");
        assertEquals(0,at[0]);assertEquals(1,at[at.length-1]);assertEquals(0,w[w.length-1]);
        for(int i=1;i<w.length;i++){assertTrue(w[i]<w[i-1],"narrowing as it rises");assertTrue(at[i]>at[i-1]);}
    }
}
