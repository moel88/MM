package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.CinemaAssets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The gacha poses Bomi with the intro's own rig (2.39.0): the same pivots and turn order as build_animation.py. */
class BomiRigTest {
    @Test void reproducesTheIntrosLookingDownPoseBoneForBone()throws Exception{
        // performance.anim at 12 s: the idle loop's look into the hourglass (build_animation.py pose(12))
        var anim=CinemaAssets.performance();float[] baked=new float[anim.stride];anim.sample(12.0,baked);
        float[][] e=new float[7][];
        e[BomiRig.ROOT]=new float[]{0,305,0};e[BomiRig.BODY]=new float[]{.15f,0,0};e[BomiRig.HEAD]=new float[]{24,15,3.25f};
        e[BomiRig.RIGHT_ARM]=BomiRig.CARRY_RIGHT;e[BomiRig.LEFT_ARM]=BomiRig.CARRY_LEFT;e[BomiRig.RIGHT_LEG]=new float[3];e[BomiRig.LEFT_LEG]=new float[3];
        float[] mine=BomiRig.pose(e,0,0,0);
        float[] offset=null;
        for(int b=0;b<7;b++){
            for(int r=0;r<3;r++)for(int c=0;c<3;c++)assertEquals(baked[b*12+r*4+c],mine[b*12+r*4+c],2e-5,"bone "+b+" turn "+r+","+c);
            float[] d={baked[b*12+3]-mine[b*12+3],baked[b*12+7]-mine[b*12+7],baked[b*12+11]-mine[b*12+11]};
            if(offset==null)offset=d;else for(int k=0;k<3;k++)assertEquals(offset[k],d[k],2e-5,"every bone moves by the root's offset alone");
        }
        assertEquals(.43,offset[0],1e-4,"the intro stands her at x .43");
    }
    @Test void anHourglassCopyIsPlacedWhereItIsAsked(){
        float[] c={0,1.1f,.565f},m=BomiRig.place(c,.78f,180,.68f,1.663f,2.39f);
        float[] at={m[0]*c[0]+m[1]*c[1]+m[2]*c[2]+m[3],m[4]*c[0]+m[5]*c[1]+m[6]*c[2]+m[7],m[8]*c[0]+m[9]*c[1]+m[10]*c[2]+m[11]};
        assertArrayEquals(new float[]{.68f,1.663f,2.39f},at,1e-5f);
        assertEquals(.78f,Math.hypot(m[0],m[8]),1e-5,"scaled, not stretched");
        assertEquals(.78f,m[5],1e-6);
    }
    @Test void seatedSheRestsOnTheCushionWithHerHeadAboveIt(){
        float[][] e=new float[7][3];e[BomiRig.RIGHT_LEG]=new float[]{-72,-9,0};e[BomiRig.LEFT_LEG]=new float[]{-72,9,0};
        float ty=dev.sangwoo.celestialfall.client.title.GachaCinema.SEAT+.03f-BomiRig.HIP;
        float[] p=BomiRig.pose(e,0,ty,-.04f);
        float[] hip=BomiRig.apply(p,BomiRig.RIGHT_LEG,-1.78125f/16,11.25f/16,0),foot=BomiRig.apply(p,BomiRig.RIGHT_LEG,-1.78125f/16,0,0);
        assertEquals(dev.sangwoo.celestialfall.client.title.GachaCinema.SEAT+.03f,hip[1],1e-5,"hips on the cushion");
        assertTrue(foot[2]>.5f&&foot[1]>0,"legs out in front, feet off the floor: "+foot[1]+" "+foot[2]);
    }
}
