package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.combat.Stagger;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's basic attack, their knife's four swings (2.117.0, user request: "이번에는 Q 껐을때 평타를 만들어줘 이펙트 없이 그냥
 * 휘두르기만해 그대신 모션을 좋게 만들어봐"): one blow each, their damage, the combo's timing, where each reaches, how long
 * each staggers. The same four by every rule whether their hack is on or off (2.118.0, the user's choice: "Q 끈 평타와
 * 같은 동작"): a hit's kind, chosen as it begins, only tells the clients whether to draw its purple cut.
 */
class XCharaComboTest {
    static final int[] KINDS={HACK_SLASH,SLASH};

    @Test void fourSwingsOneBlowEachTwentyEightInAll(){
        assertEquals(4,HACK_LUNGE);assertEquals(HACK_LUNGE+1,SLASH);assertEquals(SLASH+3,LUNGE);
        for(int first:KINDS){
            float sum=0;
            for(int c=first;c<first+4;c++){
                assertTrue(valid(c));assertEquals(1,blows(c),"hit "+c+": one swing, one blow");
                assertEquals(c-first+1,place(c));assertEquals(lastBlow(c),blowAt(c,0));
                assertEquals(first==SLASH,plain(c));
                sum+=total(c);
            }
            assertEquals(28,sum,1e-4,"about Lee So-sang's four");
            assertEquals(10,total(first+3),1e-4,"the lunge the hardest");
            assertTrue(total(first)==6&&total(first+1)==6&&total(first+2)==6);
            // the heavier the swing the longer it takes to land
            assertTrue(blowAt(first,0)<=blowAt(first+1,0)&&blowAt(first+1,0)<blowAt(first+2,0)&&blowAt(first+2,0)<blowAt(first+3,0));
        }
        // (2.121.0: 9 is the cleave, a skill of the pipeline with no place in the combo)
        assertFalse(valid(0));assertTrue(valid(9));assertFalse(valid(10));assertEquals(0,blows(0));assertEquals(0,blows(10));
        assertFalse(plain(0));assertEquals(0,place(0));assertEquals(0,place(9));
    }
    @Test void theCleaveIsASkillOfThePipelineNoHitOfTheCombo(){
        // (2.121.0, user request: "R키는 패널을 누르면 들고 있던 검이 엄청 거대해지고 그걸 양손으로 잡고 덤블링 한 번 돌고 크게
        // 내려찍는 스킬 맞추면 못 움직이는 1초 무력화")
        assertEquals(9,CLEAVE);assertTrue(skill(CLEAVE));assertTrue(valid(CLEAVE));
        for(int hit=HACK_SLASH;hit<=LUNGE;hit++)assertFalse(skill(hit),"hit "+hit);
        assertEquals(0,place(CLEAVE));assertFalse(last(CLEAVE));assertFalse(plain(CLEAVE));
        // the combo starts over after it, with the hack on or off
        assertEquals(HACK_SLASH,next(CLEAVE,false));assertEquals(SLASH,next(CLEAVE,true));
        // one blow, after the press, the growing and the leap, while it holds them; it lets go before its motion is over
        assertEquals(1,blows(CLEAVE));
        assertTrue(CLEAVE_PRESS<CLEAVE_HELD&&CLEAVE_HELD<CLEAVE_GROWN&&CLEAVE_GROWN<=CLEAVE_RAISED&&CLEAVE_RAISED<blowAt(CLEAVE,0));
        // (2.123.0: "검이 서서히 커지는 효과") the knife grows for over half a second from the press, so the blow is at 1.2 s
        assertTrue(CLEAVE_GROWN-CLEAVE_PRESS>=12,"it grows slowly");assertEquals(24,blowAt(CLEAVE,0));
        assertTrue(blowAt(CLEAVE,0)-CLEAVE_RAISED>=5&&blowAt(CLEAVE,0)<=24,"wound, then down: no leap (2.122.0)");
        assertTrue(blowAt(CLEAVE,0)<HOLD[CLEAVE]&&HOLD[CLEAVE]<=LENGTH[CLEAVE]);
        assertTrue(holding(CLEAVE,100,100)&&holding(CLEAVE,100,100+HOLD[CLEAVE]-1));assertFalse(holding(CLEAVE,100,100+HOLD[CLEAVE]));
        // three seconds that they cannot move or act in (the user's: "무력화 시간을 3초로 늘려줘", a second in 2.121.0): the legs stopped wholly
        assertEquals(60,CLEAVE_STUN);assertEquals(1,CLEAVE_SLOW,1e-9);
        // unguardable, no throw, no stagger of the basic attack's (the incapacitation instead)
        assertTrue(unblockable(CLEAVE));assertFalse(launches(CLEAVE,0));
        assertEquals(500,staggerUntil(CLEAVE,0,476,500,2,6));
        // heavier than any hit of the combo
        assertTrue(damage(CLEAVE,0)>damage(LUNGE,0));assertEquals(damage(CLEAVE,0),total(CLEAVE),1e-6);
        // a long lane ahead, as long as the grown knife; nothing behind
        assertTrue(lane(CLEAVE,0,9.5,0,0,0));assertTrue(lane(CLEAVE,0,1,1.4,0,0));assertTrue(lane(CLEAVE,0,3,0,3.4,0));
        assertFalse(lane(CLEAVE,0,9.8,0,0,0));assertFalse(lane(CLEAVE,0,3,1.7,0,0));assertFalse(lane(CLEAVE,0,-.5,0,0,0));assertFalse(lane(CLEAVE,0,3,0,3.8,0));
        assertTrue(reach(CLEAVE,0)>reach(LUNGE,0)&&reach(CLEAVE,0)+2.5<=MAX_REACH+1e-9);
        // its cooldown runs from its end; no combo is open after it and it is no break of one
        assertEquals(100+LENGTH[CLEAVE]+CLEAVE_COOLDOWN,cleaveReady(100));assertEquals(240,CLEAVE_COOLDOWN);
        assertEquals(-1,broken(CLEAVE,100,100000));
    }
    @Test void theHackOnOrOffTheyAreTheSameSwingsByEveryRule(){
        for(int place=1;place<=4;place++){
            int on=hit(place,false),off=hit(place,true);
            assertEquals(place,on);assertEquals(SLASH+place-1,off);
            assertEquals(blowAt(on,0),blowAt(off,0),"place "+place);assertEquals(damage(on,0),damage(off,0));
            assertEquals(STEP[on],STEP[off]);assertEquals(HOLD[on],HOLD[off]);assertEquals(LENGTH[on],LENGTH[off]);
            assertEquals(near(on,0),near(off,0));assertEquals(reach(on,0),reach(off,0));assertEquals(halfWidth(on,0),halfWidth(off,0));
            assertEquals(last(on),last(off));assertEquals(unblockable(on),unblockable(off));assertEquals(launches(on,0),launches(off,0));
            assertEquals(Stagger.xchara(on,0,100,100+blowAt(on,0)),Stagger.xchara(off,0,100,100+blowAt(off,0)));
        }
    }
    @Test void everyBlowLandsWhileItsHitHoldsThemAndBeforeItIsOver(){
        for(int first:KINDS){
            for(int c=first;c<first+4;c++){
                assertTrue(lastBlow(c)<HOLD[c],"hit "+c+" holds them through its blow");
                assertTrue(HOLD[c]<=LENGTH[c],"hit "+c+" lets go of them before its motion is over");
                if(!last(c)){
                    // the next may begin only once this one's blow is in, and just as it lets go
                    assertTrue(STEP[c]>lastBlow(c),"hit "+c+"'s blow before the next may start");
                    assertTrue(STEP[c]<=LENGTH[c]);
                    assertTrue(Math.abs(STEP[c]-HOLD[c])<=1,"hit "+c+" holds them until the next may start");
                }
            }
            // kept at full speed the lunge lands within two seconds (a quick knife)
            long start=0;for(int c=first;c<first+3;c++)start=nextAt(c,start);
            assertEquals(29,start,"the fourth begins 1.45 s in");
            assertTrue(start+lastBlow(first+3)<=40);
        }
    }
    @Test void aHitsKindIsChosenAsItBeginsAndItsPlaceKept(){
        // on in its own kind
        assertEquals(RISE,next(SLASH));assertEquals(CHOP,next(RISE));assertEquals(LUNGE,next(CHOP));assertEquals(SLASH,next(LUNGE));
        assertEquals(HACK_RISE,next(HACK_SLASH));assertEquals(HACK_CHOP,next(HACK_RISE));assertEquals(HACK_LUNGE,next(HACK_CHOP));assertEquals(HACK_SLASH,next(HACK_LUNGE));
        // the hack turned on or off between two hits: the next is the next place of the other kind
        assertEquals(HACK_RISE,next(SLASH,false));assertEquals(HACK_CHOP,next(RISE,false));assertEquals(HACK_LUNGE,next(CHOP,false));assertEquals(HACK_SLASH,next(LUNGE,false));
        assertEquals(RISE,next(HACK_SLASH,true));assertEquals(CHOP,next(HACK_RISE,true));assertEquals(LUNGE,next(HACK_CHOP,true));assertEquals(SLASH,next(HACK_LUNGE,true));
        assertEquals(SLASH,next(0,true),"none yet: the first");assertEquals(HACK_SLASH,next(0,false));assertEquals(HACK_SLASH,next(0));
    }
    @Test void theComboBreaksWhenTheNextIsLateAndRestsAfterTheLunge(){
        for(int first:KINDS){
            for(int c=first;c<first+3;c++){
                long may=nextAt(c,100);
                assertEquals(100+STEP[c],may);
                assertEquals(-1,broken(c,100,may),"hit "+c+" in time");
                assertEquals(-1,broken(c,100,may+GRACE),"hit "+c+" just in time");
                // late: it rests from the moment it broke
                assertEquals(may+GRACE+1+REST,broken(c,100,may+GRACE+1));
                assertEquals(may+GRACE+1+REST,broken(c,100,may+GRACE+400),"whenever it is asked after");
            }
            assertEquals(-1,broken(first+3,100,10_000),"the fourth ended it: nothing to break");
            // the rest after the fourth runs once its motion is over
            assertEquals(100+LENGTH[first+3]+REST,rested(first+3,100));
        }
        assertEquals(-1,broken(0,100,10_000));
    }
    @Test void aHitHoldsItsUserOnlyForItsHold(){
        for(int c=HACK_SLASH;c<=LUNGE;c++){
            assertTrue(holding(c,50,50));assertTrue(holding(c,50,50+HOLD[c]-1));
            assertFalse(holding(c,50,50+HOLD[c]));assertFalse(holding(c,50,49));
        }
        assertFalse(holding(0,50,50));
    }
    @Test void whereEachSwingReaches(){
        for(int c=HACK_SLASH;c<=LUNGE;c++){
            assertTrue(lane(c,0,1.5,0,0,.3),"hit "+c+" reaches one right in front");
            assertFalse(lane(c,0,-2,0,0,.3),"not one behind");
            assertFalse(lane(c,0,1.5,0,HIGH+.1,.3));assertFalse(lane(c,0,1.5,0,LOW-.1,.3));
            assertTrue(Math.hypot(reach(c,0),halfWidth(c,0))<=MAX_REACH,"within what is searched");
        }
        // the two cuts are wide and alike; the chop and the lunge narrow lanes, each further on than the one before
        assertEquals(reach(SLASH,0),reach(RISE,0));assertEquals(halfWidth(SLASH,0),halfWidth(RISE,0));
        assertTrue(lane(SLASH,0,2,2.2,0,.3));assertFalse(lane(CHOP,0,2,2.2,0,.3));assertFalse(lane(LUNGE,0,2,2.2,0,.3));
        assertTrue(reach(CHOP,0)>reach(RISE,0)&&reach(LUNGE,0)>reach(CHOP,0)&&halfWidth(LUNGE,0)<=halfWidth(CHOP,0));
        assertTrue(lane(LUNGE,0,reach(LUNGE,0),0,0,.3));assertFalse(lane(CHOP,0,reach(LUNGE,0)+.4,0,0,.3));
        // nothing is thrown: no swing reaches past the lunge's (until 2.118.0 the hack's third threw knives of light 7 blocks)
        for(int c=HACK_SLASH;c<=LUNGE;c++)assertTrue(reach(c,0)<=reach(LUNGE,0));
        assertTrue(reach(LUNGE,0)<6);
    }
    @Test void onlyTheLungeTakesNoGuardAndThrows(){
        for(int first:KINDS){
            for(int c=first;c<first+3;c++){assertFalse(unblockable(c));assertFalse(launches(c,0));}
            assertTrue(unblockable(first+3));assertTrue(launches(first+3,0));
        }
        assertTrue(FLY>0&&FLY_UP>0);
    }
    @Test void aBlowStaggersUntilTheNextCouldLand(){
        int past=Stagger.PAST,last=Stagger.LAST;
        for(int first:KINDS){
            // a combo kept at full speed holds them from blow to blow: each stagger outlasts the next blow
            long start=0;
            for(int c=first;c<first+4;c++){
                long until=Stagger.xchara(c,0,start,start+blowAt(c,0));
                if(!last(c)){
                    assertEquals(STEP[c]+blowAt(next(c),0)+past,Stagger.xchara(c,0,0,lastBlow(c)),"hit "+c);
                    long nextBlow=nextAt(c,start)+blowAt(c+1,0);
                    assertTrue(until>nextBlow,"hit "+c);
                    // and no longer than a moment past it: a late or missed blow lets them answer
                    assertTrue(until-nextBlow<=past);
                    // the hack turned on or off before the next: the other kind's swing lands as soon
                    assertTrue(until>nextAt(c,start)+blowAt(next(c,!plain(c)),0));
                    start=nextAt(c,start);
                }
            }
            // thrown by the lunge, they are staggered only a moment
            assertEquals(500+last,Stagger.xchara(first+3,0,480,500));
            // a blow landing late (the server's tick) never ends its stagger in the past
            assertTrue(Stagger.xchara(first,0,0,400)>400);
        }
    }
}
