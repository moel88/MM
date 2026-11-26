package dev.sangwoo.celestialfall;

import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.samurai.SamuraiRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The samurai's G's timing and numbers (2.143.0): its moments are the user's video's (its first frame the press, 20
 * ticks a second), its blows add up to its damage, and the one marked is held from the dash until they are thrown up.
 */
class SamuraiRulesTest {
    @Test void itsMomentsAreTheVideos(){
        assertEquals(.6,BURST/20.0,1e-9);assertEquals(1.0,DASH/20.0,1e-9);assertEquals(1.2,ARRIVE/20.0,1e-9);
        assertEquals(2.93,SHEATHE/20.0,.03);assertEquals(3.2,INK/20.0,1e-9);assertEquals(3.4,GREY/20.0,1e-9);assertEquals(4.2,VOID/20.0,1e-9);
        for(int k=0;k<4;k++)assertEquals(SLASH_SEC[k],SLASHES[k]/20.0,.03,"cut "+k);
        assertEquals(6.77,WORLD/20.0,.03);assertEquals(7.5,END/20.0,1e-9);
        assertTrue(BURST<DASH&&DASH<ARRIVE&&ARRIVE<SHEATHE&&SHEATHE<INK&&INK<GREY&&GREY<VOID&&VOID<SLASHES[0]&&SLASHES[3]<WORLD&&WORLD<LAUNCH&&LAUNCH<END);
    }
    @Test void itsBlowsAddUpToItsDamage(){
        float sum=0;long last=-1;
        for(int k=0;k<BLOWS.length;k++){
            assertTrue(BLOWS[k]>last,"blows in order");last=BLOWS[k];
            assertEquals(k,blowIndex(BLOWS[k]));
            assertTrue(blowDamage(k)>0);sum+=blowDamage(k);
        }
        assertEquals(DAMAGE,sum,1e-3);
        assertEquals(-1,blowIndex(DASH));assertEquals(0,blowDamage(-1));assertEquals(0,blowDamage(BLOWS.length));
        assertEquals(0,blowsBy(ARRIVE-1));assertEquals(1,blowsBy(ARRIVE));assertEquals(BLOWS.length,blowsBy(END));
        // the last is the heaviest, as the one marked is thrown up
        assertEquals(LAUNCH,BLOWS[BLOWS.length-1]);
        for(int k=0;k<BLOWS.length-1;k++)assertTrue(blowDamage(k)<blowDamage(BLOWS.length-1));
    }
    @Test void whoIsHeldWhen(){
        assertTrue(warning(0)&&warning(DASH-1));assertFalse(warning(DASH));
        assertFalse(held(DASH-1));assertTrue(held(DASH)&&held(LAUNCH-1));assertFalse(held(LAUNCH));
        assertTrue(userHeld(0)&&userHeld(END-1));assertFalse(userHeld(END));
        assertEquals(END+COOLDOWN,ready(END));assertEquals(3600,COOLDOWN);
    }
}
