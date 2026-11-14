package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.EraseRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the delete slash (2.133.0, the user's: "5칸 정도 뛰어올라서 거대한 참격을 바라보는
 * 방향으로 날리고 참격을 맞은 블록은 픽셀로 산산이 부서지고 거기에 있던 플레이어는 0.5초 동안 글리치로 사라졌다 나타나게";
 * "피해 100 쿨타임 60초", "보는 쪽으로 땅을 따라", "폭 16블록 · 40블록까지", "못 움직이고 맞지도 않음"): one skill of the
 * knife's pipeline whose one blow sends its slash; a leap of five blocks, in the air until it lands; the slash along
 * the ground from where they stood, 16 blocks across, as far as 40, at a steady speed, every stretch of its way come
 * over once; 100 to each, deleted for half a second; its cooldown 60 s from its end.
 */
class EraseRulesTest {
    @Test void itIsOneSkillOfTheKnifesPipelineItsBlowSendingTheSlash(){
        assertEquals(16,ERASE);assertEquals(ERASE,LAST);
        assertTrue(valid(ERASE)&&!valid(ERASE+1));
        assertTrue(skill(ERASE)&&erase(ERASE)&&!erase(KNIVES)&&!erase(SLAM)&&!erase(SHOT));
        assertEquals(1,blows(ERASE),"its one blow sends the slash");assertEquals(EraseRules.SLASH,blowAt(ERASE,0));
        assertEquals(0,damage(ERASE,0),1e-9,"the blow strikes no one: the slash does");
        assertEquals(EraseRules.HOLD,HOLD[ERASE]);assertEquals(EraseRules.LENGTH,LENGTH[ERASE]);
        assertFalse(place(ERASE)>0||last(ERASE)||single(ERASE)||launches(ERASE,0),"no hit of the combo, no one thrown");
        assertEquals(1200,EraseRules.COOLDOWN,"the user's 60 s");assertEquals(1200,cooldown(ERASE));
        assertEquals(1000+EraseRules.LENGTH+1200,EraseRules.ready(1000),"its cooldown from its end");
    }
    @Test void itsMomentsComeInTurn(){
        int[] t={0,EraseRules.LIT,EraseRules.LEAVE,EraseRules.RISEN,EraseRules.SLASH,EraseRules.FALL,EraseRules.LAND,EraseRules.HOLD,EraseRules.LENGTH};
        for(int i=1;i<t.length;i++)assertTrue(t[i]>t[i-1],"in turn: "+i);
        // the slash has gone as far as it goes before it is over (its cooldown is from then)
        assertTrue(EraseRules.SLASH+EraseRules.reached(EraseRules.RANGE)<=EraseRules.LENGTH);
        assertTrue(EraseRules.LENGTH<=40,"two seconds at most");
    }
    @Test void theLeapIsFiveBlocksUpAndBackSmoothly(){
        assertEquals(5,EraseRules.JUMP,1e-9,"the user's 5 blocks");
        assertEquals(0,EraseRules.risen(0),1e-9);assertEquals(0,EraseRules.risen(EraseRules.LEAVE),1e-9);
        assertEquals(1,EraseRules.risen(EraseRules.RISEN),1e-9);assertEquals(1,EraseRules.risen(EraseRules.SLASH),1e-9,"at the top as it slashes");
        assertEquals(0,EraseRules.risen(EraseRules.LAND),1e-9);assertEquals(0,EraseRules.risen(EraseRules.LENGTH),1e-9);
        double last=0;
        for(double a=0;a<=EraseRules.LENGTH;a+=1/3.0){
            double r=EraseRules.risen(a);
            assertTrue(r>=0&&r<=1);
            assertTrue(Math.abs(r-last)*EraseRules.JUMP<1.0,"no jump of the body at "+a+": "+r+" after "+last);
            if(a<=EraseRules.RISEN)assertTrue(r>=last-1e-9,"rising until the top");
            if(a>=EraseRules.FALL)assertTrue(r<=last+1e-9,"falling after it");
            last=r;
        }
        assertFalse(EraseRules.airborne(EraseRules.LEAVE-1));assertTrue(EraseRules.airborne(EraseRules.LEAVE));
        assertTrue(EraseRules.airborne(EraseRules.SLASH));assertFalse(EraseRules.airborne(EraseRules.LAND));
    }
    @Test void theSlashGoesAlongItsWayAtOneSpeedComingOverEveryStretchOnce(){
        assertEquals(40,EraseRules.RANGE,1e-9,"the user's 40 blocks");assertEquals(16,2*EraseRules.HALF,1e-9,"the user's 16 across");
        assertEquals(100,EraseRules.DAMAGE,1e-9,"the user's 100");assertEquals(10,EraseRules.VANISH,"the user's half a second");
        assertTrue(Double.isNaN(EraseRules.front(-1,40)));
        assertEquals(EraseRules.NEAR,EraseRules.front(0,40),1e-9);
        assertEquals(EraseRules.NEAR+EraseRules.SPEED*5,EraseRules.front(5,40),1e-9);
        for(double range:new double[]{EraseRules.NEAR,7.3,23,EraseRules.RANGE}){
            int last=EraseRules.reached(range);
            assertEquals(range,EraseRules.front(last,range),1e-9,"as far as it goes by its last tick");
            double covered=0,at=0;
            for(int t=0;t<=last+3;t++){
                double[] s=EraseRules.sweep(t,range);
                if(s==null){assertTrue(t>0&&EraseRules.front(t-1,range)>=range-1e-9,"none once it is there");continue;}
                assertEquals(at,s[0],1e-9,"from where the last left off");assertTrue(s[1]>=s[0]);
                covered+=s[1]-s[0];at=s[1];
            }
            assertEquals(range,covered,1e-9,"every stretch of its way, from its user's feet on, once");
        }
        assertTrue(EraseRules.SPEED*20>=30,"fast: a second at most to go its way");
    }
    @Test void itStrikesWhatIsInItsBandOnly(){
        // ahead, within its width and its height, as its front comes over 10 to 12
        assertTrue(EraseRules.struck(11,0,0,.3,10,12));
        assertTrue(EraseRules.struck(11,7.9,1,.3,10,12),"to its edge");assertTrue(EraseRules.struck(11,-8.2,1,.3,10,12),"a body half over its edge");
        assertFalse(EraseRules.struck(11,8.5,1,.3,10,12),"past its edge");
        assertFalse(EraseRules.struck(14,0,0,.3,10,12),"not yet come over");assertFalse(EraseRules.struck(8,0,0,.3,10,12),"already passed");
        assertTrue(EraseRules.struck(11,0,EraseRules.HIGH,.3,10,12));assertFalse(EraseRules.struck(11,0,EraseRules.HIGH+.1,.3,10,12),"over it");
        assertTrue(EraseRules.struck(11,0,EraseRules.LOW,.3,10,12));assertFalse(EraseRules.struck(11,0,EraseRules.LOW-.1,.3,10,12),"under it");
        // one standing at its user's feet is in its first sweep (from 0)
        assertTrue(EraseRules.struck(0,0,0,.3,EraseRules.sweep(0,40)[0],EraseRules.sweep(0,40)[1]));
    }
}
