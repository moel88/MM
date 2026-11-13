package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.combat.Stagger;
import dev.sangwoo.celestialfall.xchara.CutterRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's E with their hack off, the three cuts (2.129.0, the user's words and choices: "앞으로 짧게 이동하면서 3번 베는
 * 스킬이야 적이 바로 앞에 있으면 이동하지 않고 3번 베기만 해", "그냥 4타처럼 넉백이 있는거임", "합 24 (8씩)", "12초", "가드로
 * 막힘"): three cuts a quarter of a second apart, 8 each, guarded, staggering as a basic attack's, the third throwing;
 * a short way forward first unless someone is right ahead, stopping short of whoever is in the way; 12 s from its end.
 */
class CutterRulesTest {
    @Test void threeCutsAQuarterOfASecondApartEightEach(){
        for(int c:new int[]{CUTTER,CUTTER_DASH}){
            assertTrue(valid(c)&&skill(c)&&cutter(c));assertEquals(0,place(c),"no hit of the combo");
            assertEquals(3,blows(c));assertEquals(24,total(c),1e-6,"the user's 24 in all");
            for(int i=0;i<3;i++){assertEquals(8,damage(c,i),1e-6);if(i>0)assertEquals(5,blowAt(c,i)-blowAt(c,i-1),"the video's quarter of a second");}
            assertFalse(unblockable(c),"the user's: a guard takes them");
            assertFalse(launches(c,0));assertFalse(launches(c,1));assertTrue(launches(c,2),"the third throws, as the fourth swing does");
            assertFalse(single(c),"everyone in their way");
            assertEquals(CutterRules.COOLDOWN,cooldown(c));assertEquals(240,CutterRules.COOLDOWN,"the user's 12 s");
            assertEquals(100+LENGTH[c]+240,CutterRules.ready(c,100),"from its end");
            assertTrue(HOLD[c]>lastBlow(c)&&LENGTH[c]>HOLD[c]);
            assertEquals(-1,broken(c,0,1000),"it opens no combo");
            // its reach: a lane ahead, wide across the front
            assertTrue(lane(c,0,3,0,0,.3)&&lane(c,2,3.8,1.5,1,.3));assertFalse(lane(c,0,4.2,0,0,.3));assertFalse(lane(c,0,2,2.5,0,.3));assertFalse(lane(c,0,2,0,3.2,.3));
        }
        assertFalse(cutter(SLAM)||cutter(JAB)||cutter(CLEAVE)||cutter(LUNGE));
        assertTrue(unblockable(CLEAVE)&&unblockable(JAB)&&unblockable(SLAM)&&unblockable(LUNGE)&&!unblockable(SLASH),"the others as they were");
        assertTrue(launches(LUNGE,0)&&!launches(CLEAVE,0)&&!launches(JAB,0));
        assertEquals(13,CUTTER_DASH);assertFalse(valid(LAST+1));
        // the one that goes forward first is the same, LEAD ticks later
        for(int i=0;i<3;i++)assertEquals(blowAt(CUTTER,i)+CutterRules.LEAD,blowAt(CUTTER_DASH,i));
        assertEquals(HOLD[CUTTER]+CutterRules.LEAD,HOLD[CUTTER_DASH]);assertEquals(LENGTH[CUTTER]+CutterRules.LEAD,LENGTH[CUTTER_DASH]);
        assertTrue(blowAt(CUTTER_DASH,0)>CutterRules.DASH_TO,"they are there before the first cut lands");
    }
    @Test void eachCutStaggersUntilTheNextAndTheLastAsTheLungeDoes(){
        for(int c:new int[]{CUTTER,CUTTER_DASH}){
            long start=1000;
            for(int i=0;i<2;i++){
                long now=start+blowAt(c,i),until=Stagger.xchara(c,i,start,now);
                assertEquals(start+blowAt(c,i+1)+Stagger.PAST,until,"held until the next cut and a little past");
            }
            long now=start+blowAt(c,2);
            assertEquals(now+Stagger.LAST,Stagger.xchara(c,2,start,now),"the last as the lunge's");
        }
        assertEquals(500,staggerUntil(CLEAVE,0,0,500,2,6),"the cleave staggers no one, as before");
    }
    @Test void theyGoForwardOnlyWithNoOneRightAhead(){
        double none=Double.NaN;
        assertEquals(CutterRules.DASH,CutterRules.dash(none,99),1e-9,"no one: all the way");assertEquals(3,CutterRules.DASH,1e-9);
        // ("적이 바로 앞에 있으면 이동하지 않고 3번 베기만 해")
        for(double near:new double[]{0,.5,1.5,CutterRules.CLOSE})assertEquals(0,CutterRules.dash(near,99),1e-9,"right ahead at "+near);
        // further off: up to them, stopping short so the cuts' own reach has them
        for(double far=CutterRules.CLOSE+.5;far<=7;far+=.25){
            double d=CutterRules.dash(far,99);
            assertTrue(d>0&&d<=CutterRules.DASH+1e-9,"at "+far);
            assertTrue(far-d>=CutterRules.KEEP-1e-9,"they stop short of them at "+far);
            if(far-CutterRules.KEEP<=CutterRules.DASH)assertTrue(far-d<CutterRules.REACH[1],"and have them within reach at "+far);
        }
        assertEquals(3,CutterRules.dash(6.5,99),1e-9);assertEquals(2.3,CutterRules.dash(4,99),1e-9);
        // within the reach of the cuts as they stand, someone is right ahead: they never go forward past whom they could cut
        assertTrue(CutterRules.CLOSE<CutterRules.REACH[1]&&CutterRules.KEEP<CutterRules.CLOSE);
        // a wall: as far as it lets them; hardly any way at all is none
        assertEquals(1.2,CutterRules.dash(none,1.2),1e-9);assertEquals(0,CutterRules.dash(none,.3),1e-9);assertEquals(0,CutterRules.dash(none,-1),1e-9);
        assertEquals(1,CutterRules.dash(6,1),1e-9);
    }
    @Test void theWayForwardIsQuickOffTheMarkAndNeverSteps(){
        assertEquals(0,CutterRules.gone(0),1e-9);assertEquals(1,CutterRules.gone(CutterRules.DASH_TO),1e-9);assertEquals(1,CutterRules.gone(40),1e-9);assertEquals(0,CutterRules.gone(-3),1e-9);
        assertTrue(CutterRules.gone(CutterRules.DASH_TO*.5)>.7,"most of the way in its first half");
        double was=0;
        for(double a=0;a<=CutterRules.DASH_TO+2;a+=1/12.0){double g=CutterRules.gone(a);assertTrue(g>=was-1e-12);assertTrue(g-was<.03,"a step at "+a);was=g;}
        double[] from={10,64,-5},to={10,65,-2};
        assertArrayEquals(from,CutterRules.planned(0,from,to),1e-9);assertArrayEquals(to,CutterRules.planned(CutterRules.DASH_TO,from,to),1e-9);assertArrayEquals(to,CutterRules.planned(30,from,to),1e-9);
        double[] last=from;
        for(double a=0;a<=CutterRules.DASH_TO;a+=1/12.0){
            double[] p=CutterRules.planned(a,from,to);
            assertTrue(Math.abs(p[2]-last[2])<.1&&Math.abs(p[1]-last[1])<.06,"it steps at "+a);
            assertTrue(p[1]>=64-1e-9&&p[1]<=65+1e-9&&p[2]>=-5-1e-9&&p[2]<=-2+1e-9);last=p;
        }
        // three blocks in three tenths of a second: a short way, as the user's "짧게"
        assertTrue(CutterRules.DASH_TO<=8&&CutterRules.DASH<=4);
    }
}
