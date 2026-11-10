package dev.sangwoo.celestialfall.client.xchara;

import java.util.Arrays;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's G's blows in the world (2.112.1, user request: "컷씬이 끝나고 적에게 박히는 검무 이펙트를 영상처럼 엄청 대폭
 * 상향해줘"; 2.112.2: "검무 박힐때 이펙트 더 크게 하고 진한 보라색으로 만들어줘"; 2.112.3: "검무 이펙트가 너무 크고
 * 이펙트를 빛나는 보라 빛나는 흰색 검정 이렇게 섞어서 만들어봐 영상처럼"): each a flower of light as the video's, facing the
 * camera, its heart behind the one marked from it so their body stays in front; its petals shooting out evenly round it,
 * the video's size again (some four blocks either way: 2.112.2's six were too big) and wider than tall, the
 * last blow's the biggest; the video's glowing purple, glowing white and black; every blow its own; each petal growing out
 * of nothing, then fading, gone within its life.
 */
class DanceBurstTest {
    static final Vec3d MID=new Vec3d(4.5,65.2,-7.5),CAM=MID.add(-6,1.6,-8);
    static final int SEED=12345;

    @Test void itsHeartIsBehindThemFromTheCameraAndThinsInTheirOwnView(){
        var fr=DanceBurst.frame(MID,CAM);
        Vec3d n=CAM.subtract(MID).normalize();
        assertEquals(-DanceBurst.BEHIND,fr.o().subtract(MID).dotProduct(n),1e-9);
        assertEquals(0,fr.o().subtract(MID).crossProduct(n).length(),1e-9,"straight behind them");
        assertEquals(1,fr.near(),1e-6);
        assertEquals(0,fr.u().dotProduct(n),1e-9);assertEquals(0,fr.v().dotProduct(n),1e-9);assertTrue(fr.v().y>0,"up the frame is up");
        // their own first person: its heart at their middle, a third of it
        var own=DanceBurst.frame(MID,MID.add(0,.7,0));
        assertEquals(MID,own.o());assertEquals(.3,own.near(),1e-6);
    }
    @Test void itsPetalsShootOutEvenlyRoundAsWideAsTheVideosFlower(){
        var fr=DanceBurst.frame(MID,CAM);
        double sum=0,sumUp=0,lastAcross=0;
        for(int k=0;k<HITS;k++){
            boolean last=k==HITS-1;int count=DanceBurst.petalCount(last);double big=DanceBurst.big(last);
            double across=0,up=0;double[] angles=new double[count];
            for(int j=0;j<count;j++){
                // grown full, every one of them
                var p=DanceBurst.petal(fr,k,j,SEED,2.6f,big,count);
                assertNotNull(p,"blow "+k+" petal "+j);
                Vec3d tip=p.b().subtract(fr.o());
                across=Math.max(across,Math.abs(tip.dotProduct(fr.u())));up=Math.max(up,Math.abs(tip.dotProduct(fr.v())));
                angles[j]=Math.atan2(tip.dotProduct(fr.v())/DanceBurst.SQUASH,tip.dotProduct(fr.u()));
            }
            assertTrue(across>2.4*big&&across<6*big,"blow "+k+": "+across+" across");
            // evenly round: no gap between them wider than twice their share
            Arrays.sort(angles);double gap=angles[0]+2*Math.PI-angles[count-1];
            for(int j=1;j<count;j++)gap=Math.max(gap,angles[j]-angles[j-1]);
            assertTrue(gap<2*2*Math.PI/count+1e-9,"blow "+k+": a gap of "+Math.toDegrees(gap)+" degrees");
            if(last)lastAcross=across;else{sum+=across;sumUp+=up;}
        }
        double mean=sum/(HITS-1),meanUp=sumUp/(HITS-1);
        assertTrue(meanUp*1.15<mean,"wider than tall (the video's): "+mean+" across, "+meanUp+" up");
        assertTrue(mean>3.5*DanceBurst.SCALE&&mean<5*DanceBurst.SCALE,"its scale on 2.112.1's four blocks either way: "+mean);
        // the video's: all of them together some 3.4 times the one marked's height across, its flowers a little behind them
        assertTrue(mean>3&&mean<4.4,"some four blocks either way, the video's (2.112.2's six too big): "+mean);
        assertTrue(lastAcross>1.3*mean,"the last blow the biggest: "+lastAcross+" against "+mean);
    }
    @Test void itsColoursAreTheVideosGlowingPurpleGlowingWhiteAndBlack(){
        // the petals and their glow: a glowing violet (more blue than red, little green, its hue between violet and
        // magenta, bright: 2.112.2's deep purple was dark), lighter down the middle by the heart
        for(int rgb:new int[]{DanceBurst.AXIS_BASE,DanceBurst.AXIS_TIP,DanceBurst.RIM_BASE,DanceBurst.RIM_TIP,DanceBurst.GLOW}){
            int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
            assertTrue(b>r&&r>g&&g<r/2.0,"purple: "+Integer.toHexString(rgb));
            double hue=240+60.0*(r-g)/(b-g);
            assertTrue(hue>265&&hue<290,"violet to magenta: "+Integer.toHexString(rgb)+" "+hue);
            assertTrue(b>=0xd0,"glowing, not dark: "+Integer.toHexString(rgb));
        }
        assertTrue(luma(DanceBurst.AXIS_BASE)>luma(DanceBurst.RIM_BASE)&&luma(DanceBurst.HOT)>luma(DanceBurst.AXIS_BASE),"hot and light down the middle by the heart");
        // its glowing white, and its black
        assertEquals(0xffffff,DanceBurst.WHITE);
        assertTrue(luma(DanceBurst.INK)<12,"black: "+Integer.toHexString(DanceBurst.INK));
        assertTrue(DanceBurst.big(false)<1.5&&DanceBurst.big(false)>=.8&&DanceBurst.big(true)>DanceBurst.big(false),"smaller than 2.112.2's, the last the biggest");
    }
    static double luma(int rgb){return .2126*(rgb>>16&255)+.7152*(rgb>>8&255)+.0722*(rgb&255);}
    @Test void everyBlowIsItsOwn(){
        var fr=DanceBurst.frame(MID,CAM);
        for(int k=0;k+1<HITS-1;k++){
            Vec3d a=DanceBurst.petal(fr,k,0,SEED,2.6f,1,8).b(),b=DanceBurst.petal(fr,k+1,0,SEED,2.6f,1,8).b();
            assertTrue(a.distanceTo(b)>.05,"blows "+k+" and "+(k+1)+" alike");
        }
        // and the same on every client
        assertEquals(DanceBurst.petal(fr,7,3,SEED,1.2f,1,8),DanceBurst.petal(fr,7,3,SEED,1.2f,1,8));
    }
    @Test void eachPetalGrowsOutOfNothingThenFadesAndIsGoneInItsTime(){
        var fr=DanceBurst.frame(MID,CAM);
        for(int k=0;k<HITS;k++)for(int j=0;j<8;j++){
            double lastReach=0;float lastFade=0;boolean seen=false;
            for(float s=0;s<DanceBurst.LIFE+1;s+=1/6f){
                var p=DanceBurst.petal(fr,k,j,SEED,s,1,8);
                if(p==null){assertFalse(seen&&s<2,"blow "+k+" petal "+j+" gone too soon at "+s);lastFade=0;continue;}
                double reach=p.b().distanceTo(p.a());
                if(!seen){
                    assertTrue(reach<1.6,"it starts from (near) nothing: "+reach);
                    assertTrue(p.fade()<.6f,"and comes in over a frame: "+p.fade());
                }
                seen=true;
                // it grows for its first ticks, it does not shrink in a jump after
                if(s<1.5f)assertTrue(reach>=lastReach-1e-9,"blow "+k+" petal "+j+" shrinking at "+s);
                assertTrue(Math.abs(p.fade()-lastFade)<.6f,"blow "+k+" petal "+j+" jumps at "+s);
                lastReach=reach;lastFade=p.fade();
            }
            assertTrue(seen);
            assertNull(DanceBurst.petal(fr,k,j,SEED,DanceBurst.LIFE,1,8),"gone within its life");
        }
    }
}
