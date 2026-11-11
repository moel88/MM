package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.combat.Stagger;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's basic attack, their knife's four hits (2.114.0, user request: "엑스차라 평타 만들자 너가 창작해줘 ... 4타로 끝나고
 * 멋있게"): its blows and its damage, its combo's timing, where each blow reaches, how long each staggers.
 */
class XCharaComboTest {
    @Test void fourHitsTwentyEightInAll(){
        assertEquals(4,DELETE,"it ends on its fourth hit");
        int[] blows={0,1,2,3,2};float sum=0;
        for(int c=CUT;c<=DELETE;c++){
            assertTrue(valid(c));assertEquals(blows[c],blows(c),"hit "+c);
            for(int i=0;i<blows(c);i++){
                assertTrue(damage(c,i)>0);
                if(i>0)assertTrue(blowAt(c,i)>blowAt(c,i-1),"hit "+c+"'s blows in order");
            }
            assertEquals(lastBlow(c),blowAt(c,blows(c)-1));
            sum+=total(c);
        }
        assertEquals(28,sum,1e-4,"as Kageno's four");
        assertEquals(total(DELETE),10,1e-4,"the finisher the hardest");
        assertTrue(total(DELETE)>total(CUT)&&total(CUT)==total(PASTE)&&total(PASTE)==total(FLOOD));
        assertFalse(valid(0));assertFalse(valid(5));assertEquals(0,blows(0));assertEquals(0,blows(5));
    }
    @Test void everyBlowLandsWhileItsHitHoldsThemAndBeforeItIsOver(){
        for(int c=CUT;c<=DELETE;c++){
            assertTrue(lastBlow(c)<HOLD[c],"hit "+c+" holds them through its blows");
            assertTrue(HOLD[c]<=LENGTH[c],"hit "+c+" lets go of them before its motion is over");
            if(c<DELETE){
                // the next may begin only once this one's blows are in, and while it still holds or just as it lets go
                assertTrue(STEP[c]>lastBlow(c),"hit "+c+"'s last blow before the next may start");
                assertTrue(STEP[c]<=LENGTH[c]);
                assertTrue(Math.abs(STEP[c]-HOLD[c])<=1,"hit "+c+" holds them until the next may start");
            }
        }
        // kept at full speed the combo's last blow lands two seconds in (a quick knife)
        long start=0;for(int c=CUT;c<DELETE;c++)start=nextAt(c,start);
        assertEquals(29,start,"the fourth begins 1.45 s in");
        assertTrue(start+lastBlow(DELETE)<=40);
    }
    @Test void theComboGoesOnInOrderAndBreaksWhenTheNextIsLate(){
        assertEquals(PASTE,next(CUT));assertEquals(FLOOD,next(PASTE));assertEquals(DELETE,next(FLOOD));assertEquals(CUT,next(DELETE));
        assertEquals(CUT,next(0),"none yet: the first");
        for(int c=CUT;c<DELETE;c++){
            long may=nextAt(c,100);
            assertEquals(100+STEP[c],may);
            assertEquals(-1,broken(c,100,may),"hit "+c+" in time");
            assertEquals(-1,broken(c,100,may+GRACE),"hit "+c+" just in time");
            // late: it rests from the moment it broke
            assertEquals(may+GRACE+1+REST,broken(c,100,may+GRACE+1));
            assertEquals(may+GRACE+1+REST,broken(c,100,may+GRACE+400),"whenever it is asked after");
        }
        assertEquals(-1,broken(DELETE,100,10_000),"the fourth ended it: nothing to break");
        assertEquals(-1,broken(0,100,10_000));
        // the rest after the fourth runs once its motion is over
        assertEquals(100+LENGTH[DELETE]+REST,rested(100));
    }
    @Test void aHitHoldsItsUserOnlyForItsHold(){
        for(int c=CUT;c<=DELETE;c++){
            assertTrue(holding(c,50,50));assertTrue(holding(c,50,50+HOLD[c]-1));
            assertFalse(holding(c,50,50+HOLD[c]));assertFalse(holding(c,50,49));
        }
        assertFalse(holding(0,50,50));
    }
    @Test void whereEachBlowReaches(){
        // right in front of them, every blow
        for(int c=CUT;c<=DELETE;c++)for(int i=0;i<blows(c);i++){
            if(c==PASTE&&i==1)continue;
            assertTrue(lane(c,i,1.5,0,0,.3),"hit "+c+" blow "+i+" reaches one right in front");
            assertFalse(lane(c,i,-2,0,0,.3),"not one behind");
            assertFalse(lane(c,i,1.5,0,HIGH+.1,.3));assertFalse(lane(c,i,1.5,0,LOW-.1,.3));
        }
        // the cuts are wide, the thrusts narrow and long
        assertTrue(lane(CUT,0,2,2.2,0,.3));assertFalse(lane(FLOOD,0,2,2.2,0,.3));
        assertTrue(lane(FLOOD,2,KNIFE_RANGE,0,0,.3));assertFalse(lane(CUT,0,KNIFE_RANGE,0,0,.3));
        assertFalse(lane(FLOOD,0,KNIFE_RANGE+.4,0,0,.3));
        // the cut pasted again reaches further on, by as much as it stands ahead, and not one standing on its user's feet
        assertEquals(reach(PASTE,0)+PASTE_AHEAD,reach(PASTE,1),1e-9);
        assertTrue(lane(PASTE,1,reach(PASTE,0)+1,0,0,.3));assertFalse(lane(PASTE,0,reach(PASTE,0)+1,0,0,.3));
        assertFalse(lane(PASTE,1,0,0,0,.3));
        // the finisher's two cuts strike the same ground, a little further than the first hit's
        assertEquals(reach(DELETE,0),reach(DELETE,1));assertTrue(reach(DELETE,0)>reach(CUT,0));
        for(int c=CUT;c<=DELETE;c++)for(int i=0;i<blows(c);i++)assertTrue(Math.hypot(reach(c,i),halfWidth(c,i))<=MAX_REACH,"within what is searched");
    }
    @Test void onlyTheFinisherTakesNoGuardAndOnlyItsLastCutThrows(){
        for(int c=CUT;c<DELETE;c++){assertFalse(unblockable(c));for(int i=0;i<blows(c);i++)assertFalse(launches(c,i));}
        assertTrue(unblockable(DELETE));assertFalse(launches(DELETE,0));assertTrue(launches(DELETE,1));
        assertTrue(FLY>0&&FLY_UP>0);
    }
    @Test void aBlowStaggersUntilTheNextCouldLand(){
        int past=Stagger.PAST,last=Stagger.LAST;
        // the next blow of the same hit
        assertEquals(blowAt(PASTE,1)+past,Stagger.xchara(PASTE,0,0,blowAt(PASTE,0)));
        assertEquals(blowAt(FLOOD,2)+past,Stagger.xchara(FLOOD,1,0,blowAt(FLOOD,1)));
        assertEquals(blowAt(DELETE,1)+past,Stagger.xchara(DELETE,0,0,blowAt(DELETE,0)),"the finisher's first cut holds them for its second");
        // a hit's last blow: until the next hit's first, begun as soon as it may
        for(int c=CUT;c<DELETE;c++)
            assertEquals(STEP[c]+blowAt(next(c),0)+past,Stagger.xchara(c,blows(c)-1,0,lastBlow(c)),"hit "+c);
        // so a combo kept at full speed holds them from blow to blow: each stagger outlasts the next blow
        long start=0;
        for(int c=CUT;c<=DELETE;c++){
            for(int i=0;i<blows(c);i++){
                long until=Stagger.xchara(c,i,start,start+blowAt(c,i));
                long nextBlow=i+1<blows(c)?start+blowAt(c,i+1):c<DELETE?nextAt(c,start)+blowAt(c+1,0):-1;
                if(nextBlow>=0)assertTrue(until>nextBlow,"hit "+c+" blow "+i);
                // and no longer than a moment past it: a late or missed blow lets them answer
                if(nextBlow>=0)assertTrue(until-nextBlow<=past);
            }
            if(c<DELETE)start=nextAt(c,start);
        }
        // thrown by the last, they are staggered only a moment
        assertEquals(500+last,Stagger.xchara(DELETE,1,480,500));
        // a blow landing late (the server's tick) never ends its stagger in the past
        assertTrue(Stagger.xchara(CUT,0,0,400)>400);
    }
}
