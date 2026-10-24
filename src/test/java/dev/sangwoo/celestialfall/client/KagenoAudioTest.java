package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Each M1 hit's sound is heard as its blade connects (user report, 2.38.2: it came 0.3-0.45 s late). */
class KagenoAudioTest {
    @Test void eachM1SoundsAttackLandsOnItsHit(){
        assertTrue(KagenoAudio.M1_LEAD>=1,"held back a tick or more, so it starts on the tick grid the cast clock counts on");
        for(int combo=1;combo<=4;combo++){
            int hit=-1;
            for(int age=0;age<=KagenoRules.DURATION[KagenoRules.M1];age++)if(KagenoRules.damage(KagenoRules.M1,age,combo)>0){hit=age;break;}
            assertTrue(hit>0,"hit "+combo+" strikes");
            assertEquals(hit/20f,KagenoAudio.M1_LEAD/20f+KagenoAudio.M1_ATTACK,1/60f,"hit "+combo+"'s attack is heard within a frame of the strike");
        }
    }
}
