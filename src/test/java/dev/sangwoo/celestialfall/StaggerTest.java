package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.combat.*;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A basic attack's blow staggers whom it strikes for a moment only (2.68.0, user request: "평타 맞으면 아주 조금 무력화에
 * 90% 슬로우 ... 평타를 느리게 치거나 빗나갈때 반격도 할 수 있어야하니까 진짜 아주 조금 무력화"): a combo thrown at full
 * speed holds them from blow to blow, a late blow or a miss lets them answer.
 */
class StaggerTest {
    interface Rule{long until(int combo,long start,long now);}
    /** A basic attack thrown at full speed: when each of its four blows starts and lands (1 to 4). */
    record Combo(String name,long[] starts,long[] lands,Rule rule){}

    /** Bare hands under CombatState's own clock, the attack key held (pressed every tick it may be). */
    static Combo bare(){
        CombatState s=new CombatState();long[] starts=new long[5],lands=new long[5];
        for(long now=100;now<200&&lands[4]==0;now++){
            s.tick(now);s.requestAttack(now);
            if(s.attack>0&&starts[s.attack]==0)starts[s.attack]=s.attackStart;
            if(s.hitDue(now))lands[s.attack]=now;
        }
        return new Combo("bare hands",starts,lands,Stagger::bare);
    }
    /** Kageno's M1: each hit starts M1_STEP after the last and lands M1_HIT in. */
    static Combo kageno(){
        long[] starts=new long[5],lands=new long[5];
        for(int k=1;k<=4;k++){starts[k]=100+(k-1)*KagenoRules.M1_STEP;lands[k]=starts[k]+KagenoRules.M1_HIT;}
        return new Combo("Kageno",starts,lands,Stagger::kageno);
    }
    /** Star Platinum's punches: each starts STEP after the last and lands at its CONTACT. */
    static Combo stand(){
        long[] starts=new long[5],lands=new long[5];starts[1]=100;
        for(int k=1;k<=4;k++){if(k>1)starts[k]=starts[k-1]+StarPlatinumRules.STEP[k-1];lands[k]=starts[k]+StarPlatinumRules.CONTACT[k];}
        return new Combo("Star Platinum",starts,lands,Stagger::stand);
    }
    static Combo[] all(){return new Combo[]{bare(),kageno(),stand()};}

    @Test void bareHandsLandAtTheirKnownTicks(){
        var c=bare();
        assertArrayEquals(new long[]{0,103,114,126,140},c.lands(),"the bare-hand combo's contacts");
    }
    @Test void aComboAtFullSpeedHoldsTheTargetFromBlowToBlow(){
        for(var c:all())for(int k=1;k<4;k++){
            long until=c.rule().until(k,c.starts()[k],c.lands()[k]);
            assertEquals(c.lands()[k+1]+Stagger.PAST,until,c.name()+": blow "+k+" staggers them until the next lands, "+Stagger.PAST+" ticks past");
            assertTrue(until>c.lands()[k+1],c.name()+": the next blow lands while they are still staggered");
        }
    }
    @Test void theFourthStaggersOnlyAMomentAsItThrowsThem(){
        for(var c:all())assertEquals(c.lands()[4]+Stagger.LAST,c.rule().until(4,c.starts()[4],c.lands()[4]),c.name());
        assertTrue(Stagger.LAST<=8,"less than half a second");
    }
    @Test void everyStaggerIsShort(){
        // "진짜 아주 조금": never longer than the gap to the next blow and a tenth of a second
        for(var c:all())for(int k=1;k<=4;k++){
            long ticks=c.rule().until(k,c.starts()[k],c.lands()[k])-c.lands()[k];
            assertTrue(ticks>0&&ticks<=16,c.name()+" blow "+k+" staggers "+ticks+" ticks");
        }
    }
    @Test void aBlowThrownLateLeavesTheTargetFreeBeforeItLands(){
        for(var c:all())for(int k=1;k<4;k++){
            long until=c.rule().until(k,c.starts()[k],c.lands()[k]);
            for(int late=Stagger.PAST+1;late<=20;late++){
                long lands=c.lands()[k+1]+late;
                assertTrue(until<lands,c.name()+": blow "+(k+1)+" "+late+" ticks late finds them free");
                assertEquals(late-Stagger.PAST,lands-until,c.name()+": free that long");
            }
        }
    }
    @Test void oneStaggeredCanOnlyWalkSlowlyUntilItEnds(){
        CombatState s=new CombatState();long now=200,until=Stagger.bare(1,197,now);
        s.guard(true,now-1);assertTrue(s.guarding);
        s.stagger(now,until,Stagger.SLOW);
        assertFalse(s.guarding,"the guard is cut off");
        for(long t=now;t<until;t++){
            assertTrue(s.locked(t)&&s.staggered(t),"locked at "+t);assertFalse(s.disabled(t),"not the dazed incapacitation");
            assertTrue(s.slowed(t));assertEquals(.9,s.slow,1e-9,"90% slower");
            assertFalse(s.requestAttack(t),"no attack at "+t);assertFalse(s.dash(t,1,0),"no dash at "+t);s.guard(true,t);assertFalse(s.guarding,"no guard at "+t);
        }
        assertFalse(s.locked(until));assertFalse(s.slowed(until),"its slow ends with it");
        assertTrue(s.guard(true,until),"guards the moment it ends");
    }
    @Test void aMissedBlowLetsTheTargetGuardOrStrikeFirst(){
        // blow 1 lands, blow 2 misses: they are free before blow 3 could land, to guard it or to strike first
        for(var c:all()){
            long free=c.rule().until(1,c.starts()[1],c.lands()[1]),third=c.lands()[3];
            assertTrue(free<third,c.name()+": free before the third blow");
            CombatState d=new CombatState();d.stagger(c.lands()[1],free,Stagger.SLOW);
            assertFalse(d.guard(true,free-1));assertTrue(d.guard(true,free),c.name()+": guards in time");
            d=new CombatState();d.stagger(c.lands()[1],free,Stagger.SLOW);
            assertTrue(d.requestAttack(free),c.name()+": strikes back");
            assertTrue(free+CombatState.WINDUP[1]<third,c.name()+": the answer lands before the third blow");
        }
    }
    @Test void aLongerIncapacitationOrStrongerSlowAlreadyOnStays(){
        CombatState s=new CombatState();
        s.incapacitate(100,60,40,.95);
        s.stagger(110,120,Stagger.SLOW);
        assertTrue(s.disabled(150),"the incapacitation stays");assertEquals(.95,s.slow,1e-9,"the stronger slow stays");
        assertTrue(s.slowed(139));
        // and a stagger never cuts a longer one short
        s=new CombatState();s.stagger(100,130,Stagger.SLOW);s.stagger(105,112,Stagger.SLOW);
        assertTrue(s.staggered(129));assertFalse(s.staggered(130));
    }
}
