package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.RainRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the rain of knives (2.132.5, the user's: "20블록으로 줄이고 떨어지는 칼들도 대폭 줄이고
 * 떨어지는 속도도 줄여 진짜 비 처럼 만들어 그리고 적을 찌르는건 없애고 그냥 비만 떨어지게 비는 30초동안 계속 떨어져"; where it
 * rains "쓴 자리에 고정", its harm "몸에 떨어진 칼마다 1", its user "자유롭게 움직이고 싸움"): no run and no stab, its call a
 * skill of the knife's pipeline; the rain 20 blocks round for 30 seconds, far fewer knives than 2.132.4's, slower,
 * evenly over it, straight down at one speed; a knife meets a body's top, not one under a roof; the same on every machine.
 */
class RainRulesTest {
    @Test void itIsOneSkillOfTheKnifesPipelineItsCallStrikingNoOne(){
        assertEquals(16,RAIN);assertEquals(RAIN,LAST);
        assertTrue(valid(RAIN)&&!valid(RAIN+1));
        assertTrue(skill(RAIN)&&rain(RAIN)&&!rain(KNIVES)&&!rain(CUTTER_DASH)&&!rain(SHOT));
        assertEquals(1,blows(RAIN),"its one blow is the call");assertEquals(RainRules.CALL,blowAt(RAIN,0));
        assertEquals(0,damage(RAIN,0),1e-9,"the call strikes no one: the rain does");
        assertEquals(RainRules.HOLD,HOLD[RAIN]);assertEquals(RainRules.LENGTH,LENGTH[RAIN]);
        assertTrue(RainRules.CALL<RainRules.HOLD&&RainRules.HOLD<RainRules.LENGTH);
        assertTrue(RainRules.LENGTH<=20,"a short call (about a second): then its user is free (\"자유롭게 움직이고 싸움\")");
        assertFalse(place(RAIN)>0||last(RAIN)||single(RAIN)||launches(RAIN,0),"no hit of the combo, no one thrown");
        assertEquals(0,reach(RAIN,0),1e-9);
        assertEquals(1200,RainRules.COOLDOWN,"60 s");assertEquals(1200,cooldown(RAIN));
    }
    @Test void itRainsTwentyBlocksRoundForThirtySecondsItsCooldownFromItsEnd(){
        assertEquals(20,RainRules.ZONE,1e-9,"the user's 20 blocks");
        assertEquals(600,RainRules.POUR,"the user's 30 s of knives coming");
        double first=RainRules.comes(0),last=RainRules.comes(RainRules.BLADES-1);
        assertTrue(first>=RainRules.OPEN&&first<RainRules.OPEN+RainRules.RAMP*.5,"the first soon after the call: "+first);
        assertTrue(last>RainRules.OPEN+RainRules.POUR-RainRules.RAMP*.5&&last<=RainRules.OPEN+RainRules.POUR,"the last at the end of the 30 s: "+last);
        assertEquals(RainRules.OPEN+RainRules.POUR+RainRules.FALL,RainRules.END);
        assertTrue(RainRules.FALL*RainRules.SPEED>=RainRules.SKY+RainRules.SKY_MORE,"what came last has landed by its end");
        assertTrue(RainRules.LAST>RainRules.END);
        assertEquals(1000+RainRules.CALL+RainRules.END+1200,RainRules.ready(1000),"its cooldown from its end");
        assertEquals(1,RainRules.DAMAGE,1e-9,"the user's: 1 for each knife that lands on a body");
    }
    @Test void theKnivesComeOneAfterAnotherComingOnAndDyingAway(){
        double last=-1;
        for(int k=0;k<RainRules.BLADES;k++){
            double c=RainRules.comes(k);
            assertTrue(c>last,"one after another, never two at once: "+k);last=c;
            assertEquals(k+.5,RainRules.count(c),1e-6,"its share of the count");
        }
        // PER a tick at full, fewer as it comes on and dies away
        double mid=RainRules.OPEN+RainRules.POUR/2.0;
        assertEquals(RainRules.PER,RainRules.count(mid+1)-RainRules.count(mid),1e-9);
        assertTrue(RainRules.count(RainRules.OPEN+2)-RainRules.count(RainRules.OPEN+1)<RainRules.PER*.25,"coming on");
        double end=RainRules.OPEN+RainRules.POUR;
        assertTrue(RainRules.count(end)-RainRules.count(end-1)<RainRules.PER*.1,"dying away");
        assertEquals(0,RainRules.count(RainRules.OPEN),1e-9);assertEquals(RainRules.BLADES,RainRules.count(end+5),1e-9);
        // the window holds every knife that may be falling
        for(double t=RainRules.OPEN;t<RainRules.LAST;t+=3.7){
            int[] w=RainRules.window(t,RainRules.LAST-RainRules.POUR);
            for(int k=0;k<RainRules.BLADES;k+=7){
                double c=RainRules.comes(k);
                if(c<=t&&t-c<RainRules.LAST-RainRules.POUR)assertTrue(k>=w[0]&&k<w[1],"knife "+k+" at "+t);
            }
        }
    }
    @Test void farFewerKnivesThanBeforeAndSlower(){
        // in the air at once at full: some 350 (2.132.4 had some 2,270)
        double inAir=RainRules.PER*(RainRules.SKY+RainRules.SKY_MORE*.5)/RainRules.SPEED;
        assertTrue(inAir<500&&inAir>150,"in the air at once: "+inAir);
        assertTrue(RainRules.SPEED<=.6,"slower: 2.132.4's came down at some 1.7 blocks a tick at the last");
        assertTrue(RainRules.SKY/RainRules.SPEED>=17,"near a second to fall: "+RainRules.SKY/RainRules.SPEED+" ticks");
    }
    @Test void theyComeDownEvenlyAllOverIt(){
        int seed=12345;int[] quarter=new int[4];int[] rings=new int[4];
        for(int k=0;k<RainRules.BLADES;k++){
            double[] s=RainRules.spot(k,seed);double r=Math.hypot(s[0],s[1]);
            assertTrue(r<=RainRules.ZONE+1e-9,"within 20 blocks of where it was called");
            quarter[(s[0]>=0?0:1)+(s[1]>=0?0:2)]++;
            rings[Math.min(3,(int)(r*r/(RainRules.ZONE*RainRules.ZONE)*4))]++;
        }
        for(int q:quarter)assertEquals(RainRules.BLADES/4.0,q,RainRules.BLADES*.02,"each quarter as many");
        for(int q:rings)assertEquals(RainRules.BLADES/4.0,q,RainRules.BLADES*.025,"each ring of equal ground as many");
        // and a little apart, one to the next (not in a clump)
        for(int k=1;k<200;k++){
            double[] a=RainRules.spot(k-1,seed),b=RainRules.spot(k,seed);
            assertTrue(Math.hypot(a[0]-b[0],a[1]-b[1])>.5||Math.hypot(a[0],a[1])<2,"one after another, not on top of each other");
        }
        // the same seed, the same rain; another, another
        assertArrayEquals(RainRules.spot(77,seed),RainRules.spot(77,seed));
        assertFalse(java.util.Arrays.equals(RainRules.spot(77,seed),RainRules.spot(77,seed+1)));
    }
    @Test void aKnifeFallsStraightDownAtOneSpeed(){
        int seed=99;
        for(int k:new int[]{0,500,4000,RainRules.BLADES-1}){
            double c=RainRules.comes(k),sky=RainRules.sky(k,seed);
            assertTrue(sky>=RainRules.SKY&&sky<=RainRules.SKY+RainRules.SKY_MORE);
            assertEquals(sky,RainRules.point(k,seed,c-3),1e-9,"as high as it came, before");
            for(double t=c;t<c+30;t+=.25)assertEquals(sky-(t-c)*RainRules.SPEED,RainRules.point(k,seed,t),1e-9,"at one speed");
            assertEquals(c+(sky-2)/RainRules.SPEED,RainRules.reaches(k,seed,2),1e-9);
        }
    }
    @Test void aKnifeMeetsTheTopOfABodyNotOneUnderWhatIsOverThem(){
        // a body from 64 to 65.8, the ground at 64: falling over it from 66.2 to 65.65 this tick, it meets its top
        assertEquals(65.8,RainRules.meets(66.2,65.65,64,65.8,64),1e-9);
        // already in it (they jumped into it): where its point is
        assertEquals(65.2,RainRules.meets(65.2,64.65,64,65.8,64),1e-9);
        // over it still, or past it
        assertTrue(Double.isNaN(RainRules.meets(67,66.45,64,65.8,64)));
        assertTrue(Double.isNaN(RainRules.meets(63.9,63.35,64,65.8,64)));
        // a roof over them at 68: it lands there first
        assertTrue(Double.isNaN(RainRules.meets(66.2,65.65,64,65.8,68)));
        // no ground there (no knife comes)
        assertTrue(Double.isNaN(RainRules.meets(66.2,65.65,64,65.8,Double.NaN)));
        // level: its point within GIRTH of the box
        assertTrue(RainRules.over(10.2,5,10.3,10.9,4.7,5.3)&&!RainRules.over(9.9,5,10.3,10.9,4.7,5.3));
        // standing still in it all through it, a body is struck some ten times on the mean (1 each)
        double times=RainRules.expected(.6,.6);
        assertTrue(times>5&&times<20,"struck on the mean: "+times);
    }
    @Test void theSameOnEveryMachine(){
        // integers only: these never change
        double a=RainRules.h(0,0,0),b=RainRules.h(123,456,7),c=RainRules.h(-5,99999,3);
        for(double v:new double[]{a,b,c})assertTrue(v>=0&&v<1);
        assertEquals(a,RainRules.h(0,0,0));assertNotEquals(b,c);
        assertEquals(RainRules.seed(1000,7,9),RainRules.seed(1000,7,9));assertNotEquals(RainRules.seed(1000,7,9),RainRules.seed(1001,7,9));
        // spread over 0..1
        int[] bins=new int[10];for(int k=0;k<10000;k++)bins[(int)(RainRules.h(k,42,5)*10)]++;
        for(int n:bins)assertEquals(1000,n,120);
    }
}
