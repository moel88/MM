package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.ShotRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's Y with their hack off, the four knives shot (2.130.0, the user's words and choices: "q활성화 상태의 검을 4개를
 * 적에게 날리는 스킬이야", "합 32 (8씩) · 15초", "가드로 막힘", "보는 방향으로 날아감"): four knives, 8 each, a guard takes
 * them, 15 s from its end; they come one after another, are let go one after another, and each flies straight at the
 * point it is sent at from where it hung.
 */
class ShotRulesTest {
    @Test void fourKnivesEightEachAGuardTakesFifteenSecondsFromItsEnd(){
        assertTrue(valid(SHOT)&&skill(SHOT));assertEquals(0,place(SHOT),"no hit of the combo");assertEquals(SHOT,LAST);assertFalse(valid(SHOT+1));
        assertEquals(4,ShotRules.BLADES);assertEquals(4,blows(SHOT),"its blows are its knives let go");
        assertEquals(32,total(SHOT),1e-6,"the user's 32 in all");
        for(int k=0;k<4;k++){assertEquals(8,damage(SHOT,k),1e-6);assertEquals(ShotRules.FIRE[k],blowAt(SHOT,k));}
        assertFalse(unblockable(SHOT),"the user's: a guard takes them");
        for(int k=0;k<4;k++)assertFalse(launches(SHOT,k),"no knockback");
        assertFalse(cutter(SHOT));assertFalse(single(SHOT));
        assertEquals(300,ShotRules.COOLDOWN,"the user's 15 s");assertEquals(ShotRules.COOLDOWN,cooldown(SHOT));
        assertEquals(100+ShotRules.LENGTH+300,ShotRules.ready(100),"from its end");
        assertEquals(ShotRules.HOLD,HOLD[SHOT]);assertEquals(ShotRules.LENGTH,LENGTH[SHOT]);
        assertTrue(ShotRules.HOLD>ShotRules.FIRE[3],"held until the last has gone");assertTrue(ShotRules.LENGTH>=ShotRules.HOLD);
        // (the cleave, the jab, the slam and the cuts are as they were)
        assertTrue(unblockable(CLEAVE));assertTrue(unblockable(JAB));assertFalse(unblockable(CUTTER));assertTrue(launches(CUTTER,2));
    }
    @Test void theyComeOneAfterAnotherAndGoOneAfterAnother(){
        for(int k=0;k<4;k++){
            assertTrue(ShotRules.FIRE[k]>ShotRules.APPEAR[k]+4,"each hangs a while before it goes");
            if(k>0){
                assertEquals(3,ShotRules.APPEAR[k]-ShotRules.APPEAR[k-1],"0.15 s apart");assertEquals(3,ShotRules.FIRE[k]-ShotRules.FIRE[k-1]);
            }
            assertFalse(ShotRules.there(k,ShotRules.APPEAR[k]-.01));assertTrue(ShotRules.there(k,ShotRules.APPEAR[k]));
            assertFalse(ShotRules.gone(k,ShotRules.FIRE[k]-.01));assertTrue(ShotRules.gone(k,ShotRules.FIRE[k]));
        }
        assertTrue(ShotRules.APPEAR[3]<=ShotRules.FIRE[0],"all four are there as the first goes");
    }
    @Test void theyHangAboutWhereTheirUserStoodFacing(){
        double[] feet={10,64,-5};
        // facing +z (yaw 0): ahead is +z, the right is -x
        double[] a=ShotRules.spot(feet,0,0);
        assertEquals(10-ShotRules.SPOTS[0][2],a[0],1e-9);assertEquals(64+ShotRules.SPOTS[0][1],a[1],1e-9);assertEquals(-5+ShotRules.SPOTS[0][0],a[2],1e-9);
        // facing -x (yaw 90): ahead is -x, the right is -z
        double[] b=ShotRules.spot(feet,90,2);
        assertEquals(10-ShotRules.SPOTS[2][0],b[0],1e-9);assertEquals(-5-ShotRules.SPOTS[2][2],b[2],1e-9);
        boolean over=false,low=false,left=false,right=false;
        for(double[] s:ShotRules.SPOTS){
            assertTrue(s[0]<0,"every grip behind the line of their chest: the blade reaches forward of it");
            assertTrue(s[0]+ShotRules.TIP<3,"none hangs far out ahead");
            assertTrue(s[0]>-1.6&&s[0]+ShotRules.TIP>1,"its blade's middle beside or over them, not behind (the game's camera is there)");
            over|=s[1]>2.4;low|=s[1]<1;left|=s[2]<0;right|=s[2]>0;
            assertTrue(Math.hypot(s[2],Math.max(0,Math.min(1.8,s[1]))-s[1])>.3,"clear of their body's middle line");
        }
        assertTrue(over&&low&&left&&right,"over the head, low, and to both sides");
    }
    @Test void eachFliesStraightAtThePointItIsSentAt(){
        double[] feet={0,64,0},eye={0,65.62,0},want={.5,65,12};
        for(int k=0;k<4;k++){
            double[] s=ShotRules.spot(feet,0,k),w=ShotRules.way(s,eye,want);
            assertEquals(1,Math.sqrt(w[0]*w[0]+w[1]*w[1]+w[2]*w[2]),1e-9);
            // the point lies on its way
            double t=(want[0]-s[0])*w[0]+(want[1]-s[1])*w[1]+(want[2]-s[2])*w[2];
            for(int i=0;i<3;i++)assertEquals(want[i],s[i]+w[i]*t,1e-6,"knife "+k+" meets the point");
            assertTrue(t>0&&w[2]>.9,"forward");
        }
        // a point right before their eyes: taken as far out along their look as NEAR, so no knife turns back on them
        double[] close={0,65.62,.4};
        for(int k=0;k<4;k++){
            double[] s=ShotRules.spot(feet,0,k),w=ShotRules.way(s,eye,close);
            assertTrue(w[2]>.5,"knife "+k+" still goes forward");
            double t=(0-s[0])*w[0]+(65.62-s[1])*w[1]+(ShotRules.NEAR-s[2])*w[2];
            assertEquals(ShotRules.NEAR,s[2]+w[2]*t,1e-6);
        }
        // nothing told: level, the way they faced
        double[] h=ShotRules.hangs(90);assertEquals(-1,h[0],1e-9);assertEquals(0,h[1],1e-9);assertEquals(0,h[2],1e-9);
    }
    @Test void itsPointGoesFortyBlocksASecondAndTheServerLooksATickAhead(){
        assertEquals(ShotRules.TIP,ShotRules.head(0),1e-9,"as it goes its point is where it hung");
        assertEquals(ShotRules.TIP,ShotRules.head(-3),1e-9);
        assertEquals(2,ShotRules.head(1)-ShotRules.head(0),1e-9);assertEquals(2,ShotRules.SPEED,1e-9);
        assertEquals(0,ShotRules.swept(0)[0],1e-9,"as it goes: everything from its grip on");
        for(int n=0;n<12;n++){
            double[] a=ShotRules.swept(n),b=ShotRules.swept(n+1);
            assertEquals(a[1],b[0],1e-9,"no gap between one tick's stretch and the next");
            assertEquals(ShotRules.head(n+1),a[1],1e-9,"a tick ahead of where its point is drawn");
        }
        for(double far:new double[]{ShotRules.TIP,5,12.5,24})assertEquals(far,ShotRules.head(ShotRules.reached(far)),1e-9);
        assertEquals(0,ShotRules.reached(1),1e-9,"nearer than its point: at once");
        assertTrue(ShotRules.TIP>2.4&&ShotRules.TIP<2.9,"the knife the size it is in the hand: its point some 2.6 blocks ahead of its grip");
        assertTrue(ShotRules.over(3)<=ShotRules.FIRE[3]+ShotRules.RANGE/ShotRules.SPEED);
        assertTrue(ShotRules.STAGGER>3,"a strike staggers past when the next could come");
    }
}
