package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The long cooldowns (2.73.0, user request: "카게노 아토믹 쿨타임은 10분으로 하고 게임 나갔다 오거나 재설정하면 쿨타임 다시
 * 10분부터 흐르게 시간정지도 똑같이 바꿔줘 ... 비트다운도 ... 아토믹이랑 똑같은 메커니즘 적용"; 2.74.0: "그냥 죽어서 부활하기만
 * 하면 처음부터 돌게", and as the stand arrow gives a stand): one still running as its player comes back, comes back to
 * life or is given a stand begins again in full, one over stays over, one whose skill is still going keeps its tick, and
 * they are kept with the world.
 */
class LongCooldownsTest {
    private static final int ATOMIC=600*20;

    @Test void oneStillRunningBeginsAgainInFull(){
        long now=50_000;
        assertEquals(now+ATOMIC,LongCooldowns.restart(now+1,now,ATOMIC),"a tick left: ten minutes again");
        assertEquals(now+ATOMIC,LongCooldowns.restart(now+ATOMIC-5,now,ATOMIC),"just begun: ten minutes from now");
        assertEquals(now+StarPlatinumRules.TS_COOLDOWN,LongCooldowns.restart(now+20,now,StarPlatinumRules.TS_COOLDOWN),"the time stop's two and a half minutes");
        assertEquals(now+StarPlatinumRules.BEATDOWN_COOLDOWN,LongCooldowns.restart(now+999,now,StarPlatinumRules.BEATDOWN_COOLDOWN),"the beatdown's two");
    }
    @Test void oneOverStaysOver(){
        long now=50_000;
        assertEquals(0,LongCooldowns.restart(now,now,ATOMIC),"ready this very tick: over");
        assertEquals(0,LongCooldowns.restart(now-3000,now,ATOMIC),"ran out while they were away");
        assertEquals(0,LongCooldowns.restart(0,now,ATOMIC),"never used");
    }
    @Test void oneWhoseSkillIsStillGoingKeepsItsTick(){
        long now=50_000,end=now+300;
        assertEquals(end+ATOMIC,LongCooldowns.restart(end+ATOMIC,now,ATOMIC),"an Atomic still playing out: its ten minutes still begin as it ends");
        assertEquals(now+ATOMIC,LongCooldowns.restart(now+ATOMIC,now,ATOMIC),"one a whole length away is the same either way");
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
