package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** T (어둠 속의 불꽃). */
class SparkSkillTest {
    /**
     * User request (2.44.0): a cooldown runs once its skill is over. T's cast ends as its last wisps die out (it ran on to
     * 6.2 s with nothing left to see), after its fire has struck and let go of whoever it held.
     */
    @Test void theCastEndsWhenTheFireIsGone(){
        int end=KagenoRules.DURATION[KagenoRules.T];
        assertEquals(new SparkSkill().lifetime()*20,end,.5);
        for(int age=end;age<end+60;age++)assertEquals(0,KagenoRules.damage(KagenoRules.T,age,0),"no fire strikes after "+age);
        assertFalse(KagenoRules.trapped(KagenoRules.T,end-1),"the fire has let go by then");
    }
}
