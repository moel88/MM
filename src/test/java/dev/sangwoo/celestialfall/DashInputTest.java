package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.combat.DashInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashInputTest {
    @Test void fastMobileTapSurvivesReleaseBeforeNextTickAndDoesNotDoubleSend() {
        var input=new DashInput();input.key(true,true);input.key(false,true);
        assertTrue(input.consume(false,true,true));assertFalse(input.consume(false,false,true));
    }
    @Test void heldOnlyMobileButtonTriggersOnceUntilReleased() {
        var input=new DashInput();assertTrue(input.consume(true,false,true));
        for(int tick=0;tick<100;tick++)assertFalse(input.consume(true,false,true));
        assertFalse(input.consume(false,false,true));assertTrue(input.consume(true,false,true));
    }
    @Test void conflictingBindingStillReceivesRawKeyWithoutAutorepeatDashes() {
        var input=new DashInput();input.key(true,true);assertTrue(input.consume(false,false,true));
        for(int repeat=0;repeat<60;repeat++){input.key(true,true);assertFalse(input.consume(false,true,true));}
        input.key(false,true);input.key(true,true);assertTrue(input.consume(false,false,true));
    }
    @Test void blockedInputIsDiscardedAndDoesNotFireWhenMenuOrStunEnds() {
        var input=new DashInput();input.key(true,false);assertFalse(input.consume(true,true,false));
        assertFalse(input.consume(true,false,true));input.key(false,true);input.consume(false,false,true);
        input.key(true,true);assertFalse(input.consume(true,true,false));assertFalse(input.consume(true,false,true));
        input.clear();assertTrue(input.consume(false,true,true));
    }
}
