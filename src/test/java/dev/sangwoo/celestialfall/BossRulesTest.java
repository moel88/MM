package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.boss.BossKind;
import dev.sangwoo.celestialfall.boss.BossRules;
import dev.sangwoo.celestialfall.boss.BossRules.Candidate;
import dev.sangwoo.celestialfall.boss.BossThreats;
import dev.sangwoo.celestialfall.combat.CombatState;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import java.util.List;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What every boss does and what Kujo Jotaro has of his own (2.69.0, user request: "공격 우선 순위는 HP가 낮은 플레이어
 * 우선 ... 어그로가 한 번 끌리면 30블록을 벗어나지 않는이상 30블록 이내에 있는 모든 플레이어를 HP가 낮은 순서대로 공격함",
 * "보스 체력은 5000", "피가 2500이 되면 시간정지를 사용 할 수 있어 죠타로의 시간정지 쿨타임은 2분 비트다운은 1분30초
 * 나머지는 똑같아 죠타로를 잡으면 5000원을 주고 죠타로가 죽은 자리 기준으로 원형으로 6개의 박스", "1시간 30분마다").
 */
class BossRulesTest {
    static Candidate<String> c(String who,float health,double distance){return new Candidate<>(who,health,distance);}

    @Test void theLeastHealthWithinThirtyBlocksIsFoughtFirst(){
        var order=BossRules.order(List.of(c("full",20,4),c("low",6,25),c("mid",12,2),c("far",1,30.5),c("dead",0,3)));
        assertEquals(List.of("low","mid","full"),order,"by health, the lowest first; whoever is past 30 blocks or dead is not fought");
        assertEquals(30,BossRules.RANGE,"its range is 30 blocks");
        assertEquals(List.of("near","far"),BossRules.order(List.of(c("far",8,20),c("near",8,3))),"with the same health, the nearer first");
        assertEquals(List.of("edge"),BossRules.order(List.of(c("edge",8,30))),"one exactly 30 blocks away is still within it");
    }
    @Test void itDoesNotTurnFromOneToAnotherOnEveryBlow(){
        var both=List.of(c("a",14,3),c("b",12,6));
        assertEquals("a",BossRules.choose(both,"a",true,4),"in the middle of a combo, a target with a little more health is kept");
        assertEquals("b",BossRules.choose(both,"a",false,4),"between moves it turns to the one with less health");
        assertEquals("b",BossRules.choose(List.of(c("a",14,3),c("b",6,6)),"a",true,4),"one with clearly less health is gone after at once");
        assertEquals("b",BossRules.choose(List.of(c("a",14,31),c("b",12,6)),"a",true,4),"one who left its range is let go");
        assertEquals("b",BossRules.choose(List.of(c("b",12,6)),"a",true,4),"one no longer there is let go");
        assertNull(BossRules.choose(List.of(c("a",14,40)),"a",true,4),"nobody within its range: nobody");
    }
    @Test void sixBoxesStandEvenlyRoundWhereItFell(){
        var ring=BossRules.ring(10,-20,6,BossRules.BOX_RING,37);
        assertEquals(6,ring.length);
        for(int i=0;i<6;i++){
            double dx=ring[i][0]-10,dz=ring[i][1]+20;
            assertEquals(BossRules.BOX_RING,Math.hypot(dx,dz),1e-9,"box "+i+" on the ring");
            var next=ring[(i+1)%6];
            assertEquals(2*BossRules.BOX_RING*Math.sin(Math.PI/6),Math.hypot(next[0]-ring[i][0],next[1]-ring[i][1]),1e-9,"boxes "+i+" and the next evenly apart (60 degrees)");
        }
        var f=Vec3d.fromPolar(0,37);
        // (the game's own sine is a table: a little off)
        assertEquals(10+f.x*BossRules.BOX_RING,ring[0][0],1e-3,"the first toward the way it faced");
        assertEquals(-20+f.z*BossRules.BOX_RING,ring[0][1],1e-3,"the first toward the way it faced");
        assertEquals(6,BossKind.JOTARO.boxes,"Jotaro leaves six");
        assertEquals(6000,BossRules.BOX_LIFE,"a box it left goes after five minutes unopened");
    }
    @Test void itComesEveryHourAndAHalfFromItsLastComing(){
        long every=BossKind.JOTARO.every;
        assertEquals(90*60*20,every,"an hour and a half of ticks");
        assertEquals(1000+every,BossRules.nextAfter(1000,every,1000),"at its coming, the next is a period on");
        assertEquals(1000+every,BossRules.nextAfter(1000,every,1000+every-1));
        assertEquals(1000+2*every,BossRules.nextAfter(1000,every,1000+every),"the period keeps its own beat");
        assertEquals(1000+4*every,BossRules.nextAfter(1000,every,1000+3*every+5),"a server down for hours picks the beat up again");
        assertEquals(5000,BossRules.nextAfter(5000,every,10),"not yet due: as it is");
        assertThrows(IllegalArgumentException.class,()->BossRules.nextAfter(0,0,5));
    }
    @Test void itIsWholeAgainEvenlyAfterItLetsGo(){
        float h=1200,max=BossKind.JOTARO.health;int ticks=0;
        while(h<max){h+=BossRules.heal(h,max);ticks++;assertTrue(ticks<=BossRules.HEAL_TICKS,"whole within "+BossRules.HEAL_TICKS+" ticks");}
        assertEquals(max,h,1e-3f);
        assertTrue(ticks<=BossRules.HEAL_TICKS);
        assertEquals(0,BossRules.heal(max,max),"whole: nothing more");
    }
    @Test void jotaroHasHisOwnHealthTimeStopAndCooldowns(){
        var j=BossKind.JOTARO;
        assertEquals(5000,j.health,"5000 health");
        assertFalse(j.mayStopTime(2500.5f),"no time stop above 2500");
        assertTrue(j.mayStopTime(2500),"his time stop at 2500");
        assertTrue(j.mayStopTime(40));
        assertEquals(2*60*20,j.cooldown(BossRules.TIME_STOP,StarPlatinumRules.TS_COOLDOWN),"time stop: two minutes");
        assertEquals(90*20,j.cooldown(BossRules.BEATDOWN,StarPlatinumRules.BEATDOWN_COOLDOWN),"beatdown: a minute and a half");
        assertEquals(StarPlatinumRules.HEAVY_COOLDOWN,j.cooldown("heavy",StarPlatinumRules.HEAVY_COOLDOWN),"the rest as a player's");
        assertEquals(StarPlatinumRules.RUSH_COOLDOWN,j.cooldown("rush",StarPlatinumRules.RUSH_COOLDOWN));
        assertTrue(j.cooldown(BossRules.TIME_STOP,StarPlatinumRules.TS_COOLDOWN)>StarPlatinumRules.TS_COOLDOWN,"longer than a player's");
        assertTrue(j.cooldown(BossRules.BEATDOWN,StarPlatinumRules.BEATDOWN_COOLDOWN)>StarPlatinumRules.BEATDOWN_COOLDOWN,"longer than a player's");
        assertEquals(5000,j.reward,"5000 won to whoever fells him");
        assertEquals("star_platinum",j.stand);
        assertTrue(j.profile.length()<=16&&j.profile.chars().allMatch(ch->ch<128),"a player name the game takes");
        assertEquals("쿠죠 죠타로가",j.subject(),"its name takes 가 (it ends in a vowel)");
        assertEquals("쿠죠 죠타로를",j.object());
        assertEquals("디오 브란도가",BossKind.particle("디오 브란도","이","가"));
        assertEquals("폴나레프를",BossKind.particle("폴나레프","을","를"));
        assertEquals("압둘이",BossKind.particle("압둘","이","가"),"ends in a consonant: 이");
        assertEquals("DIO이(가)",BossKind.particle("DIO","이","가"),"not Hangul: both");
        assertSame(j,BossKind.byId("JOTARO"));
        assertNull(BossKind.byId("dio"));
    }
    @Test void itShrugsOffStaggersOnlyAfterBeingHeldDownLong(){
        var poise=new BossRules.Poise();
        // held now and then (a slow attacker): never shrugs
        for(long t=0;t<400;t++){poise.tick(t,t%3==0);assertFalse(poise.shrugs(t),"held a third of the time: staggers hold it ("+t+")");}
        // held without a break: after POISE_HELD ticks it shrugs for POISE_TICKS, then can be held again
        var p=new BossRules.Poise();long shrugAt=-1;
        for(long t=1000;t<1100;t++){p.tick(t,true);if(p.shrugs(t)){shrugAt=t;break;}}
        assertEquals(1000+BossRules.POISE_HELD-1,shrugAt,"it shrugs once it has been held POISE_HELD ticks");
        for(long t=shrugAt;t<shrugAt+BossRules.POISE_TICKS;t++){p.tick(t,true);assertTrue(p.shrugs(t),"for POISE_TICKS ("+t+")");}
        long after=shrugAt+BossRules.POISE_TICKS;p.tick(after,true);
        assertFalse(p.shrugs(after),"then staggers hold it again");
        // the same tick told twice counts once; a gap counts as free
        var q=new BossRules.Poise();
        for(long t=0;t<BossRules.POISE_HELD-1;t++){q.tick(t,true);q.tick(t,true);}
        assertFalse(q.shrugs(BossRules.POISE_HELD-2));
        q.tick(BossRules.POISE_HELD+200,true);
        assertFalse(q.shrugs(BossRules.POISE_HELD+200),"a long gap forgets what held it before");
    }
    @Test void aBlowsGroundIsWhereItStrikes(){
        // a lane 0.2 to 3.6 ahead of a player at the origin facing south (yaw 0, +z), 1.15 to either side
        var lane=BossThreats.Zone.lane(Vec3d.ZERO,0,.2,3.6,1.15,-1.2,2.8);
        assertTrue(lane.covers(0,0,2,.3),"straight ahead");
        assertTrue(lane.covers(1.3,0,2,.3),"a body 0.3 across reaching into its side");
        assertFalse(lane.covers(1.6,0,2,.3),"past its side");
        assertFalse(lane.covers(0,0,-1,.3),"behind");
        assertFalse(lane.covers(0,0,4.2,.3),"past its reach");
        assertFalse(lane.covers(0,3.5,2,.3),"above it");
        var east=BossThreats.Zone.lane(Vec3d.ZERO,-90,.2,3.6,1.15,-1.2,2.8);
        assertTrue(east.covers(2,0,0,.3),"yaw -90 strikes east");
        assertFalse(east.covers(0,0,2,.3));
        // the right side of a lane: a player facing south has the west (-x) on the right
        var right=new BossThreats.Zone(0,0,0,0,0,3,.5,2,-1,2,0);
        assertTrue(right.covers(-1,0,1,0),"right of the way is -x facing south");
        assertFalse(right.covers(1,0,1,0));
        var ring=BossThreats.Zone.ring(new Vec3d(5,0,5),20,-64,64);
        assertTrue(ring.covers(5,0,24.8,.3));
        assertFalse(ring.covers(5,0,25.5,.3));
    }
    @Test void aDashGetsItOutOfTheWayOnlyIfStartedInTime(){
        assertEquals(0,BossThreats.dashDistance(0),1e-9);
        double whole=0;for(int i=0;i<CombatState.DASH_TICKS;i++)whole+=CombatState.dashSpeed(i);
        assertEquals(whole,BossThreats.dashDistance(CombatState.DASH_TICKS),1e-9,"the whole dash");
        assertEquals(whole,BossThreats.dashDistance(100),1e-9,"no further than the whole dash");
        assertTrue(BossThreats.dashDistance(2)>1.8,"two ticks of dash carry a body out of a punch's lane (1.15 to the side)");
        assertTrue(BossRules.REACTION<StarPlatinumRules.CONTACT[StarPlatinumRules.HEAVY],"a heavy punch can be seen in time");
        assertTrue(BossRules.REACTION>=3,"no quicker than a quick player");
    }
}
