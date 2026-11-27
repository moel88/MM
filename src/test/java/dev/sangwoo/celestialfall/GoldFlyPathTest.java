package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.stand.GoldFlyPath;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Where Gold Experience's fly is (2.151.0, T: "앞으로 알짱거리면서 파리 자기자신을 때리게 유도"): smooth at 60 fps all its
 * life, the part flying off from where it was struck to the fly's place, the fly before its owner's face, in the way of
 * their blows straight ahead most of the time but not all of it, lagging their turns, and back into the part's place.
 */
class GoldFlyPathTest {
    static final double EYES=1.62,HEIGHT=1.8;
    static double[] at(int seed,int part,double age,float heading,double[] from){return GoldFlyPath.at(seed,part,age,0,64,0,heading,EYES,HEIGHT,from);}
    static double dist(double[] a,double[] b){return Math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1])+(a[2]-b[2])*(a[2]-b[2]));}

    @Test void itNeverJumpsAt60fpsFromTheBlowToItsReturn(){
        for(int seed:new int[]{0,7,-123456,987654321})for(int part=0;part<PARTS;part++){
            double[] from=GoldFlyPath.world(0,64,0,0,GoldFlyPath.socket(part,HEIGHT)),last=null;
            for(double age=0;age<=FLY_GONE+2;age+=1/3.0){
                double[] p=at(seed,part,age,0,from);
                if(last!=null)assertTrue(dist(last,p)<.25,"it jumps "+dist(last,p)+" blocks in a frame at "+age+" (seed "+seed+", part "+part+")");
                last=p;
            }
            assertEquals(0,dist(at(seed,part,0,0,from),from),1e-9,"the part flies off from where it was struck off");
            assertEquals(0,dist(at(seed,part,FLY_GONE,0,from),from),1e-9,"and the fly is back in the part's place at the end");
        }
    }
    @Test void itHoversBeforeTheirFaceInTheWayOfTheirBlowsMostOfTheTimeButNotAll(){
        int[] seeds={1,2,3,4,5,6,7,8};
        long inTheWay=0,all=0;
        for(int seed:seeds)for(double age=FLY_FORMS;age<FLY_FORMS+FLY_LASTS;age+=.5){
            // facing south (+z): its right is west (-x)
            double[] p=at(seed,HEAD,age,0,new double[]{0,65,0});
            double ahead=p[2],right=-p[0],up=p[1]-64;
            assertTrue(ahead>.6&&ahead<1.9,"ahead of them: "+ahead+" at "+age);
            assertTrue(Math.abs(right)<1.8,"to the side: "+right);
            assertTrue(up>EYES-.7&&up<EYES+.45,"about their face: "+up);
            all++;if(Math.abs(right)<1.3)inTheWay++;
        }
        double share=inTheWay/(double)all;
        assertTrue(share>.55,"in the way of a blow straight ahead "+share+" of the time");
        assertTrue(share<.92,"but not all the time: "+share);
    }
    @Test void itLagsTheirTurns(){
        float h=0;for(int k=0;k<6;k++)h=GoldFlyPath.follow(h,90);
        assertEquals(90*(1-Math.exp(-1)),h,1,"two thirds of the way round in its follow time");
        for(int k=0;k<34;k++)h=GoldFlyPath.follow(h,90);
        assertEquals(90,h,1,"round in two seconds");
        // the short way round
        assertTrue(GoldFlyPath.follow(170,-170)>170,"170 to -170 goes on past 180");
    }
    @Test void thePartsAreWhereTheyAreOnABody(){
        double[] head=GoldFlyPath.socket(HEAD,HEIGHT),ra=GoldFlyPath.socket(RIGHT_ARM,HEIGHT),la=GoldFlyPath.socket(LEFT_ARM,HEIGHT),rl=GoldFlyPath.socket(RIGHT_LEG,HEIGHT);
        assertTrue(head[2]>ra[2]&&ra[2]>rl[2],"head over arms over legs");
        assertTrue(ra[1]>0&&la[1]<0,"the right arm on the right");
        assertEquals(head[2]/2,GoldFlyPath.socket(HEAD,HEIGHT/2)[2],1e-9,"by the body's height");
    }
}
