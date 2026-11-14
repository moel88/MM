package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.KnivesRules;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's T with their hack off, the three small knives thrown (2.131.0, the user's words and choices: "다른손으로 작은
 * 나이프 3개를 던지는 기술이야", "합 12 (4씩) · 5초", "평타처럼 짧은 경직만", "가드 불가"): three knives, 4 each, no guard
 * takes them, 5 s from its end; they leave the hand one after another, quickly, and each flies straight and fast from
 * the hand that threw it.
 */
class KnivesRulesTest {
    @Test void threeKnivesFourEachNoGuardTakesFiveSecondsFromItsEnd(){
        assertTrue(valid(KNIVES)&&skill(KNIVES));assertEquals(0,place(KNIVES),"no hit of the combo");assertEquals(15,KNIVES);assertFalse(valid(LAST+1));
        assertEquals(15,KNIVES);assertEquals(14,SHOT,"the knives shot keep their number");
        assertEquals(3,KnivesRules.KNIVES);assertEquals(3,blows(KNIVES),"its blows are its knives leaving the hand");
        assertEquals(12,total(KNIVES),1e-6,"the user's 12 in all");
        for(int k=0;k<3;k++){assertEquals(4,damage(KNIVES,k),1e-6);assertEquals(KnivesRules.FIRE[k],blowAt(KNIVES,k));}
        assertTrue(unblockable(KNIVES),"the user's: no guard takes them");
        assertFalse(unblockable(SHOT),"a guard still takes the knives shot");
        for(int k=0;k<3;k++)assertFalse(launches(KNIVES,k),"no knockback");
        assertFalse(cutter(KNIVES));assertFalse(single(KNIVES));
        assertEquals(100,KnivesRules.COOLDOWN,"the user's 5 s");assertEquals(KnivesRules.COOLDOWN,cooldown(KNIVES));
        assertEquals(100+KnivesRules.LENGTH+100,KnivesRules.ready(100),"from its end");
        assertEquals(KnivesRules.HOLD,HOLD[KNIVES]);assertEquals(KnivesRules.LENGTH,LENGTH[KNIVES]);assertEquals(0,STEP[KNIVES]);
        assertTrue(KnivesRules.HOLD>KnivesRules.FIRE[2],"held until the last has gone");assertTrue(KnivesRules.LENGTH>=KnivesRules.HOLD);
        assertTrue(KnivesRules.LENGTH<=20,"a quick, simple skill (the video's is over in two thirds of a second)");
        // a basic attack's short stagger: until the next knife could come and a little, no incapacitation
        assertTrue(KnivesRules.STAGGER>KnivesRules.FIRE[1]-KnivesRules.FIRE[0]&&KnivesRules.STAGGER<=8);assertTrue(KnivesRules.STAGGER_LAST<=8);
        // (the other skills are as they were)
        assertEquals(ShotRules.COOLDOWN,cooldown(SHOT));assertTrue(unblockable(CLEAVE));assertTrue(unblockable(JAB));assertFalse(unblockable(CUTTER));
    }
    @Test void theyAreInTheHandThenLeaveItOneAfterAnother(){
        assertTrue(KnivesRules.APPEAR<KnivesRules.RAISED&&KnivesRules.RAISED<KnivesRules.FIRE[0],"they come to be, the hand is raised, then thrown");
        assertEquals(6,KnivesRules.FIRE[0],"the first a third of a second after the press (the video's)");
        for(int k=1;k<3;k++)assertEquals(2,KnivesRules.FIRE[k]-KnivesRules.FIRE[k-1],"a tenth of a second apart (the video's strikes)");
        assertFalse(KnivesRules.there(KnivesRules.APPEAR-.01));assertTrue(KnivesRules.there(KnivesRules.APPEAR));
        for(int k=0;k<3;k++){assertFalse(KnivesRules.gone(k,KnivesRules.FIRE[k]-.01));assertTrue(KnivesRules.gone(k,KnivesRules.FIRE[k]));}
    }
    @Test void aKnifeFliesFastAndTheServerLooksATickAhead(){
        assertTrue(KnivesRules.SPEED>=2*ShotRules.SPEED,"far faster than the knives shot (the video's cross five blocks between two frames)");
        assertEquals(KnivesRules.TIP,KnivesRules.head(0),1e-9);assertEquals(KnivesRules.TIP,KnivesRules.head(-3),1e-9);
        assertEquals(KnivesRules.TIP+KnivesRules.SPEED*2.5,KnivesRules.head(2.5),1e-9);
        double[] first=KnivesRules.swept(0);assertEquals(0,first[0],1e-9,"as it leaves: everything from its tail on");
        for(int n=0;n<6;n++){
            double[] a=KnivesRules.swept(n),b=KnivesRules.swept(n+1);
            assertEquals(a[1],b[0],1e-9,"no gap between a tick's stretch and the next's");
            assertEquals(KnivesRules.head(n+1),a[1],1e-9,"a tick ahead of where its point is drawn");
        }
        for(double far:new double[]{.3,KnivesRules.TIP,3,9.5,KnivesRules.RANGE})assertEquals(Math.max(far,KnivesRules.TIP),KnivesRules.head(KnivesRules.reached(far)),1e-9);
        assertEquals(KnivesRules.FIRE[2]+KnivesRules.reached(KnivesRules.RANGE),KnivesRules.over(2),1e-9);
        assertTrue(KnivesRules.reached(KnivesRules.RANGE)<=5,"across its whole range in a quarter of a second");
        assertTrue(KnivesRules.TIP<1,"a small knife");assertTrue(KnivesRules.TIP<ShotRules.TIP/3);
    }
    @Test void theyLeaveFromTheHandThatHoldsNoKnifeAheadOfTheBody(){
        double[] feet={10,64,-5};
        for(int k=0;k<3;k++){
            double[] s=KnivesRules.SPOTS[k];
            assertTrue(s[0]>.6&&s[0]<1.4,"ahead of them (the arm thrown out)");assertTrue(s[1]>.6&&s[1]<1.5,"between the hip and the shoulder");
            assertTrue(s[2]<0&&s[2]>-.7,"on the left: the knife is in the right hand");
            // facing south (yaw 0): ahead is +z, their right is -x
            double[] at=KnivesRules.spot(feet,0,k);
            assertEquals(feet[0]-s[2],at[0],1e-9);assertEquals(feet[1]+s[1],at[1],1e-9);assertEquals(feet[2]+s[0],at[2],1e-9);
            // facing west (yaw 90): ahead is -x, their right is -z
            double[] west=KnivesRules.spot(feet,90,k);
            assertEquals(feet[0]-s[0],west[0],1e-9);assertEquals(feet[2]-s[2],west[2],1e-9);
            // straight at what it is sent at
            double[] want={feet[0]+.4,feet[1]+1.1,feet[2]+7},w=ShotRules.way(at,new double[]{feet[0],feet[1]+1.62,feet[2]},want);
            double far=Math.sqrt(Math.pow(want[0]-at[0],2)+Math.pow(want[1]-at[1],2)+Math.pow(want[2]-at[2],2));
            for(int i=0;i<3;i++)assertEquals(want[i],at[i]+w[i]*far,1e-9);
        }
        assertTrue(KnivesRules.SPOTS[2][1]<KnivesRules.SPOTS[0][1],"the hand sweeps on down as they leave");
    }
}
