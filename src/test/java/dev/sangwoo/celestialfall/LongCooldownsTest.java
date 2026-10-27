package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.gacha.GachaRules;
import dev.sangwoo.celestialfall.progression.StandStats;
import dev.sangwoo.celestialfall.stand.StandArrowRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The long cooldowns (2.73.0, user request: "카게노 아토믹 쿨타임은 10분으로 하고 게임 나갔다 오거나 재설정하면 쿨타임 다시
 * 10분부터 흐르게 시간정지도 똑같이 바꿔줘 ... 비트다운도 ... 아토믹이랑 똑같은 메커니즘 적용"; 2.74.0: "그냥 죽어서 부활하기만
 * 하면 처음부터 돌게", and as the stand arrow gives a stand; 2.74.1: "그냥 사용하는 스탠드의 긴 쿨타임 애들만 처음부터 다시
 * 흐르게 하면 돼"). Since 2.74.3 ("그냥 단순하게 스탠드를 새로 얻거나 죽으면 쿨타임 다시 처음부터 돌게하면 되는데"): as a
 * player comes back, comes back to life or is given a stand, every long cooldown of the stand they have begins in full,
 * whatever it was (running, over, never used), but for one whose skill is still going (it keeps its later tick); one
 * of a skill they do not have (another stand's, or the time stop before they are awakened) is gone. They are kept with
 * the world.
 */
class LongCooldownsTest {
    private static final int ATOMIC=600*20,TS=StarPlatinumRules.TS_COOLDOWN,BD=StarPlatinumRules.BEATDOWN_COOLDOWN;

    @Test void theirsBeginInFullWhateverTheyWere(){
        long now=50_000;
        for(int length:new int[]{ATOMIC,TS,BD}){
            assertEquals(now+length,LongCooldowns.restart(now+1,now,length,true),length+": a tick left");
            assertEquals(now+length,LongCooldowns.restart(now+length-5,now,length,true),length+": just begun");
            assertEquals(now+length,LongCooldowns.restart(now,now,length,true),length+": ready this very tick");
            assertEquals(now+length,LongCooldowns.restart(now-3000,now,length,true),length+": ran out while they were away");
            assertEquals(now+length,LongCooldowns.restart(0,now,length,true),length+": never used");
        }
        assertEquals(now+ATOMIC,LongCooldowns.restart(0,now,ATOMIC,true),"Atomic: ten minutes");
        assertEquals(now+3000,LongCooldowns.restart(0,now,TS,true),"the time stop: two and a half minutes");
        assertEquals(now+2400,LongCooldowns.restart(0,now,BD,true),"the beatdown: two");
    }
    @Test void oneWhoseSkillIsStillGoingKeepsItsTick(){
        long now=50_000,end=now+300;
        assertEquals(end+ATOMIC,LongCooldowns.restart(end+ATOMIC,now,ATOMIC,true),"an Atomic still playing out: its ten minutes still begin as it ends");
        assertEquals(now+ATOMIC,LongCooldowns.restart(now+ATOMIC,now,ATOMIC,true),"one a whole length away is the same either way");
    }
    @Test void oneNotTheirsIsGone(){
        long now=50_000;
        for(long ready:new long[]{0,now-5,now,now+1,now+ATOMIC-5,now+300+ATOMIC})
            assertEquals(0,LongCooldowns.restart(ready,now,ATOMIC,false),"ready at "+ready+": gone");
    }
    @Test void whatEachStandHas(){
        var atomic=LongCooldowns.Skill.ATOMIC;var timeStop=LongCooldowns.Skill.TIME_STOP;var beatdown=LongCooldowns.Skill.BEATDOWN;
        for(boolean awake:new boolean[]{false,true}){
            assertTrue(LongCooldowns.has(atomic,"kageno",awake),"Kageno's Atomic");
            assertFalse(LongCooldowns.has(timeStop,"kageno",awake),"not Kageno's: the time stop");
            assertFalse(LongCooldowns.has(beatdown,"kageno",awake),"not Kageno's: the beatdown");
            assertFalse(LongCooldowns.has(atomic,"star_platinum",awake),"not Star Platinum's: Atomic");
            assertTrue(LongCooldowns.has(beatdown,"star_platinum",awake),"Star Platinum's beatdown, awakened or not");
            for(var s:LongCooldowns.Skill.values())assertFalse(LongCooldowns.has(s,"none",awake),s+": none without a stand");
        }
        assertTrue(LongCooldowns.has(timeStop,"star_platinum",true),"the awakened's time stop");
        assertFalse(LongCooldowns.has(timeStop,"star_platinum",false),"no time stop before the awakening (H is no key, 2.72.0)");
        for(var s:LongCooldowns.Skill.values())assertTrue(LongCooldowns.has(s,s.stand,true),s+": its stand's, awakened");
    }
    @Test void eachIsItsStands(){
        assertEquals("kageno",LongCooldowns.Skill.ATOMIC.stand);
        assertEquals("star_platinum",LongCooldowns.Skill.TIME_STOP.stand);assertEquals("star_platinum",LongCooldowns.Skill.BEATDOWN.stand);
        for(var s:LongCooldowns.Skill.values())assertTrue(StandStats.known(s.stand)&&!s.stand.equals("none"),s+": a stand's");
        assertEquals(GachaRules.SSR_STAND,LongCooldowns.Skill.ATOMIC.stand,"the gacha's stand begins Atomic again");
        assertTrue(StandArrowRules.STANDS.stream().anyMatch(a->a.stand().equals(LongCooldowns.Skill.TIME_STOP.stand)),"the arrow's stand begins the time stop and the beatdown again");
    }
    @Test void theLedgerKeepsEachPlayersSkillsApart(){
        var a=UUID.randomUUID();var b=UUID.randomUUID();var state=new LongCooldowns();
        assertEquals(0,state.ready(a,LongCooldowns.Skill.ATOMIC),"nothing kept at first");
        assertFalse(state.put(a,LongCooldowns.Skill.ATOMIC,0),"none for none changes nothing");
        assertFalse(state.isDirty());
        assertTrue(state.put(a,LongCooldowns.Skill.ATOMIC,12_345));
        assertTrue(state.put(a,LongCooldowns.Skill.BEATDOWN,7_000));
        assertTrue(state.put(b,LongCooldowns.Skill.TIME_STOP,9_000));
        assertFalse(state.put(b,LongCooldowns.Skill.TIME_STOP,9_000),"the same again changes nothing");
        assertTrue(state.isDirty());
        assertEquals(12_345,state.ready(a,LongCooldowns.Skill.ATOMIC));assertEquals(0,state.ready(a,LongCooldowns.Skill.TIME_STOP));
        assertEquals(7_000,state.ready(a,LongCooldowns.Skill.BEATDOWN));assertEquals(9_000,state.ready(b,LongCooldowns.Skill.TIME_STOP));
        state.put(b,LongCooldowns.Skill.TIME_STOP,0);
        assertFalse(state.players().contains(b),"a player with none left is dropped");
    }
    @Test void theyAreKeptWithTheWorld(){
        var a=UUID.randomUUID();var b=UUID.randomUUID();var state=new LongCooldowns();
        state.put(a,LongCooldowns.Skill.ATOMIC,123_456_789L);state.put(a,LongCooldowns.Skill.TIME_STOP,99);state.put(b,LongCooldowns.Skill.BEATDOWN,5);
        var restored=LongCooldowns.read(state.writeNbt(new NbtCompound()));
        assertEquals(123_456_789L,restored.ready(a,LongCooldowns.Skill.ATOMIC));
        assertEquals(99,restored.ready(a,LongCooldowns.Skill.TIME_STOP));assertEquals(0,restored.ready(a,LongCooldowns.Skill.BEATDOWN));
        assertEquals(5,restored.ready(b,LongCooldowns.Skill.BEATDOWN));
        assertEquals(0,LongCooldowns.read(new NbtCompound()).ready(a,LongCooldowns.Skill.ATOMIC),"a world from before 2.73.0 has none");
        var damaged=new NbtCompound();var list=new net.minecraft.nbt.NbtList();var row=new NbtCompound();row.putLong("Atomic",77);list.add(row);damaged.put("Players",list);
        assertTrue(LongCooldowns.read(damaged).players().isEmpty(),"a row without a player is skipped");
    }
}
