package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.LongCooldowns;
import dev.sangwoo.celestialfall.progression.StandStats;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Whose cooldowns the list shows (2.74.2, user request: "걍 다른 스탠드를 끼면 다른 스탠드 쿨타임은 사라진다 이게
 * 힘든가? 서로 스탠드가 다른데 왜 흐르는건데"): only those of the stand the player has now, and the dash, which is
 * everyone's; none of a stand's to one who has none (after a Rokakaka).
 */
class CooldownHudTest {
    private static final int[] STAR={CooldownHud.PUNCHES,CooldownHud.BARRAGE,CooldownHud.HEAVY,CooldownHud.FINGER,CooldownHud.INHALE,CooldownHud.BEATDOWN,CooldownHud.TIME_STOP};

    @Test void kagenosAreShownOnlyToKageno(){
        for(int slot=KagenoRules.M1;slot<=KagenoRules.P;slot++){
            assertTrue(CooldownHud.shows(slot,"kageno"),KagenoRules.NAMES[slot]);
            assertFalse(CooldownHud.shows(slot,"star_platinum"),KagenoRules.NAMES[slot]+" is not Star Platinum's");
            assertFalse(CooldownHud.shows(slot,"none"),KagenoRules.NAMES[slot]+" is gone with the stand");
        }
    }
    @Test void starPlatinumsAreShownOnlyToStarPlatinum(){
        for(int slot:STAR){
            assertTrue(CooldownHud.shows(slot,"star_platinum"),"slot "+slot);
            assertFalse(CooldownHud.shows(slot,"kageno"),"slot "+slot+" is not Kageno's");
            assertFalse(CooldownHud.shows(slot,"none"),"slot "+slot+" is gone with the stand");
        }
    }
    @Test void theDashIsEveryones(){
        for(String stand:StandStats.ids())assertTrue(CooldownHud.shows(CooldownHud.DASH,stand),stand);
    }
    @Test void aLongCooldownIsShownToTheStandItIsKeptFor(){
        // the server keeps each long cooldown for one stand (LongCooldowns.Skill.stand): the list shows it to that stand only
        assertTrue(CooldownHud.shows(KagenoRules.G,LongCooldowns.Skill.ATOMIC.stand));
        assertTrue(CooldownHud.shows(CooldownHud.TIME_STOP,LongCooldowns.Skill.TIME_STOP.stand));
        assertTrue(CooldownHud.shows(CooldownHud.BEATDOWN,LongCooldowns.Skill.BEATDOWN.stand));
        for(var skill:LongCooldowns.Skill.values())for(String stand:StandStats.ids()){
            int slot=skill==LongCooldowns.Skill.ATOMIC?KagenoRules.G:skill==LongCooldowns.Skill.TIME_STOP?CooldownHud.TIME_STOP:CooldownHud.BEATDOWN;
            assertEquals(stand.equals(skill.stand),CooldownHud.shows(slot,stand),skill+" for "+stand);
        }
    }
}
