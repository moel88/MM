package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.combat.Stagger;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lee So-sang's basic attack (2.77.0, rebuilt after the user's videos of each hit in 2.77.2): four hits, their blows in
 * order, no knockback anywhere, the combo's rhythm and rest, and the finisher's slash thrown flying: it strikes whoever
 * its band passes over on its way, with no gap between one tick and the next, and no one behind, beside or beyond it.
 */
class IsosangRulesTest {
    @Test void everyHitsBlowsLandInOrderWhileItHoldsItsUser(){
        // the great cut; the ring and its burst; hit 3's four cuts (2.79.2); the thrown slash
        assertArrayEquals(new int[]{1,2,4,1},new int[]{blows(1),blows(2),blows(3),blows(4)});
        for(int combo=1;combo<=4;combo++){
            for(int i=0;i<blows(combo);i++){
                assertTrue(blowAt(combo,i)>0&&blowAt(combo,i)<LENGTH[combo],"hit "+combo+" blow "+i);
                if(i>0)assertTrue(blowAt(combo,i)>blowAt(combo,i-1),"hit "+combo+" blows in order");
                assertTrue(damage(combo,i)>0);
            }
            assertTrue(lastBlow(combo)<HOLD[combo],"hit "+combo+" holds its user until its last blow has landed");
            assertTrue(HOLD[combo]<=LENGTH[combo]);
            if(combo<4)assertTrue(STEP[combo]>lastBlow(combo),"the next hit waits for hit "+combo+"'s last blow");
            if(combo<3)assertTrue(STEP[combo]<LENGTH[combo],"hit "+combo+" is still moving as the next may take over");
        }
        // (after the third the next waits a moment in the stance, as the video of the whole combo shows)
        assertTrue(STEP[3]>=LENGTH[3]);
        assertTrue(LENGTH[4]/20.0>blowAt(4,0)/20.0+WAVE_TICKS/20.0,"the finisher stands until its slash has flown");
        assertEquals(29,total(1)+total(2)+total(3)+total(4),1e-4,"the whole combo, as Kageno's");
        assertEquals(6,total(2),1e-4);assertEquals(7,total(3),1e-4);
        assertTrue(unblockable(4)&&!unblockable(1)&&!unblockable(2)&&!unblockable(3),"only the finisher takes no guard");
    }
    @Test void thePaceIsTheVideos(){
        // each hit's video gives its blows; the video of the whole combo how soon the next may follow
        // (2.79.2, the user's video of the whole combo at half speed: the finisher 1.25 s after the third, not 1.5 s)
        assertArrayEquals(new int[]{11,10,25},new int[]{STEP[1],STEP[2],STEP[3]});
        assertEquals(6,blowAt(1,0));assertEquals(3,blowAt(2,0));assertEquals(6,blowAt(2,1));
        assertArrayEquals(new int[]{2,7,11,17},new int[]{blowAt(3,0),blowAt(3,1),blowAt(3,2),blowAt(3,3)});
        // (2.79.2, the same video: thrown 0.44 s after the twirl begins, half a second in; it was 0.7 s)
        assertEquals(10,blowAt(4,0),"the slash is thrown after the twirl, the aim, the crouch and the lunge");
    }
    @Test void theComboBreaksWhenTheNextHitIsLateAndRestsAfterwards(){
        assertEquals(2,next(1));assertEquals(4,next(3));assertEquals(1,next(4));assertEquals(1,next(0));
        long start=100;
        assertEquals(-1,broken(1,start,start+STEP[1]+GRACE),"still on time");
        assertEquals(start+STEP[1]+GRACE+1+REST,broken(1,start,start+STEP[1]+GRACE+1));
        assertEquals(-1,broken(4,start,start+500),"the fourth hit ends the combo itself");
        assertEquals(start+LENGTH[4]+REST,rested(start),"the rest runs once the fourth hit is over");
        assertTrue(holding(1,start,start)&&!holding(1,start,start+HOLD[1]));
    }
    @Test void everyBlowStaggersUntilTheNextCouldLandAndTheFinisherABitLonger(){
        long start=0;
        // hit 3's blows hold whom they strike from one to the next, and hit 2's ring until it bursts
        for(int i=0;i<3;i++){long now=start+blowAt(3,i);assertEquals(start+blowAt(3,i+1)+Stagger.PAST,Stagger.isosang(3,i,start,now));}
        assertEquals(start+blowAt(2,1)+Stagger.PAST,Stagger.isosang(2,0,start,start+blowAt(2,0)));
        // the last blow of a hit holds them until the next hit's first could land, started as soon as it may
        assertEquals(start+STEP[1]+blowAt(2,0)+Stagger.PAST,Stagger.isosang(1,0,start,start+blowAt(1,0)));
        assertEquals(start+STEP[2]+blowAt(3,0)+Stagger.PAST,Stagger.isosang(2,1,start,start+blowAt(2,1)));
        assertEquals(start+STEP[3]+blowAt(4,0)+Stagger.PAST,Stagger.isosang(3,3,start,start+blowAt(3,3)));
        // the thrown slash throws no one: it staggers them a moment where they stand, whenever it reaches them
        assertEquals(start+blowAt(4,0)+3+Stagger.LAST,Stagger.isosang(4,0,start,start+blowAt(4,0)+3));
        // a late blow (the time stop letting time move again) never staggers into the past
        assertEquals(500+Stagger.PAST,Stagger.isosang(1,0,start,500));
    }
    @Test void eachBlowReachesItsOwnWay(){
        // the great cut: ahead and wide to both sides, not far behind nor overhead
        assertTrue(lane(1,0,2,3,1,.3));assertFalse(lane(1,0,-1.5,0,1,.3));assertFalse(lane(1,0,2,0,3.5,.3));
        // the ring: all round, both blows
        for(int i=0;i<2;i++)for(int a=0;a<360;a+=30){double r=SPIN_RADIUS-.2,x=Math.cos(Math.toRadians(a))*r,y=Math.sin(Math.toRadians(a))*r;assertTrue(lane(2,i,x,y,.5,.3),"the ring at "+a);}
        assertFalse(lane(2,0,SPIN_RADIUS+1,0,.5,.3));
        // hit 3's four cuts (2.79.2): each a wide sweep across the front, the fourth the biggest
        for(int i=0;i<3;i++){assertTrue(lane(3,i,2,2.8,1,.3),"cut "+i);assertFalse(lane(3,i,4.8,0,1,.3),"cut "+i);assertFalse(lane(3,i,-1.5,0,1,.3),"cut "+i);}
        assertTrue(lane(3,3,4,3.8,1,.3));assertFalse(lane(3,3,5,0,1,.3));assertFalse(lane(3,3,2,4.5,1,.3));
    }
    @Test void theThrownSlashFliesOutWideningAndSlowing(){
        assertEquals(WAVE_START,waveFront(0),1e-9);assertEquals(WAVE_RANGE,waveFront(WAVE_TICKS),1e-9);assertEquals(WAVE_RANGE,waveFront(WAVE_TICKS+5),1e-9);
        assertEquals(WAVE_HALF_START,waveHalf(0),1e-9);assertEquals(WAVE_HALF_END,waveHalf(WAVE_TICKS),1e-9);
        double was=waveFront(0),speed=Double.MAX_VALUE;
        for(int k=1;k<=WAVE_TICKS;k++){
            double now=waveFront(k);assertTrue(now>was,"it flies on");
            assertTrue(now-was<speed+1e-9,"fast at first, slowing");speed=now-was;was=now;
            assertTrue(waveHalf(k)>waveHalf(k-1),"it widens as it flies");
        }
        assertTrue(waveFront(1)-waveFront(0)>.6,"it leaves fast: "+(waveFront(1)-waveFront(0)));
        // 2.79.2 draft (the user's videos of hit 4): it flies only a moment and hangs there, near
        assertEquals(WAVE_RANGE,waveFront(4),1e-9,"stopped within a fifth of a second");assertTrue(WAVE_RANGE>8&&WAVE_RANGE<12,"about 10 blocks: "+WAVE_RANGE);
        // its tips trail behind its middle
        assertTrue(waveEdge(4,waveHalf(4))<waveEdge(4,0)-1);
        assertTrue(waveFlying(0)&&waveFlying(WAVE_TICKS)&&!waveFlying(WAVE_TICKS+1)&&!waveFlying(-1));
    }
    /** The first tick of the flight whose sweep takes a body there (the throw itself is tick 0), or -1. */
    static int struckAt(double fw,double side,double half){
        for(int k=0;k<=WAVE_TICKS;k++)if(waveSweeps(Math.max(0,k-1),k,fw,side,1,half))return k;
        return -1;
    }
    @Test void theThrownSlashStrikesEveryoneItPassesOverAndNoOneElse(){
        // all along its way, in the middle and out to the sides, no one slips between one tick and the next
        for(double fw=-.4;fw<=WAVE_RANGE-.1;fw+=.1)assertTrue(struckAt(fw,0,.3)>=0,"missed straight ahead at "+fw);
        for(double fw=0;fw<=WAVE_RANGE-WAVE_CURVE_END-.2;fw+=.25)for(double side=-2;side<=2;side+=.5)
            assertTrue(struckAt(fw,side,.3)>=0,"missed at "+fw+", "+side);
        // right in front as it is thrown: at once; further on: later
        assertEquals(0,struckAt(.5,0,.3));assertTrue(struckAt(8.5,0,.3)>struckAt(2.5,0,.3),"further on, later: "+struckAt(8.5,0,.3)+" "+struckAt(2.5,0,.3));
        // not behind them, not beyond its range, not out past its tips, not over its head or under its feet
        assertEquals(-1,struckAt(-2,0,.3));assertEquals(-1,struckAt(WAVE_RANGE+1,0,.3));
        assertEquals(-1,struckAt(1.5,WAVE_HALF_END+1,.3));assertTrue(struckAt(1.5,3.4,.3)>struckAt(1.5,0,.3),"a wide point beside them only once the band has widened over it: "+struckAt(1.5,3.4,.3)+" "+struckAt(1.5,0,.3));
        assertFalse(waveSweeps(0,WAVE_TICKS,5,0,HIGH+.5,.3));assertFalse(waveSweeps(0,WAVE_TICKS,5,0,LOW-.5,.3));
        // a big body is reached sooner than a small one
        assertTrue(struckAt(9.5,0,1.2)<=struckAt(9.5,0,.3));
        // a boss reads it as a lane as long and wide as its flight
        assertEquals(WAVE_RANGE,reach(4,0),1e-9);assertEquals(WAVE_HALF_END,halfWidth(4,0),1e-9);
    }

    // ───────────────────────────── E (2.80.0) ─────────────────────────────

    @Test void theEmpoweredComboIsTheFirstAndFourthChangedAndHalfAsHardAgain(){
        // (user request: "1타와 4타만 바뀌고 2타 3타는 모션이 같아", "데미지 50% 더 강하고")
        assertEquals(THROWS,kind(1,true));assertEquals(2,kind(2,true));assertEquals(3,kind(3,true));assertEquals(THRUST,kind(4,true));
        for(int at=1;at<=4;at++)assertEquals(at,kind(at,false));
        assertEquals(1,position(THROWS));assertEquals(4,position(THRUST));
        assertEquals(2,next(THROWS,true));assertEquals(3,next(2,true));assertEquals(THRUST,next(3,true));assertEquals(4,next(3,false));
        float plain=0,empowered=0;
        for(int at=1;at<=4;at++){plain+=total(kind(at,false),false);empowered+=total(kind(at,true),true);}
        assertEquals(29,plain,1e-4);assertEquals(29*EMPOWER,empowered,1e-4,"the whole combo half as hard again");
        assertEquals(total(1)*EMPOWER,total(THROWS),1e-4,"the three swords together");
        assertEquals(total(4)*EMPOWER,total(THRUST),1e-4,"the thrust");
        assertEquals(damage(2,0)*EMPOWER,damage(2,0,true),1e-6);assertEquals(damage(3,3)*EMPOWER,damage(3,3,true),1e-6);
        assertEquals(damage(2,0),damage(2,0,false),1e-6);
        assertEquals(300,E_COOLDOWN,"15 s");
    }
    @Test void theEmpoweredHitsKeepTheCombosRules(){
        for(int combo:new int[]{THROWS,THRUST}){
            for(int i=0;i<blows(combo);i++){assertTrue(blowAt(combo,i)>0&&blowAt(combo,i)<LENGTH[combo]);if(i>0)assertTrue(blowAt(combo,i)>blowAt(combo,i-1));}
            assertTrue(lastBlow(combo)<HOLD[combo]&&HOLD[combo]<=LENGTH[combo]);
        }
        assertEquals(3,blows(THROWS),"three swords");assertEquals(1,blows(THRUST));
        assertTrue(STEP[THROWS]>lastBlow(THROWS)&&STEP[THROWS]<LENGTH[THROWS],"the second may follow once the swords are thrown");
        long start=100;
        assertEquals(-1,broken(THROWS,start,start+STEP[THROWS]+GRACE));assertEquals(start+STEP[THROWS]+GRACE+1+REST,broken(THROWS,start,start+STEP[THROWS]+GRACE+1),"a late second breaks it");
        assertEquals(-1,broken(THRUST,start,start+500),"the thrust ends the combo itself");
        assertEquals(start+LENGTH[THRUST]+REST,rested(THRUST,start));
        assertTrue(holding(THRUST,start,start)&&!holding(THRUST,start,start+HOLD[THRUST]));
        assertTrue(unblockable(THRUST)&&!unblockable(THROWS),"the fourth of either kind takes no guard");
        // a thrown sword holds whom it strikes until the next could reach them; the third's last until the thrust could land
        assertEquals(start+blowAt(THROWS,1)+SWORD_LAG+Stagger.PAST,Stagger.isosang(THROWS,0,start,start+blowAt(THROWS,0)+1,true));
        assertEquals(start+STEP[THROWS]+blowAt(2,0)+Stagger.PAST,Stagger.isosang(THROWS,2,start,start+blowAt(THROWS,2)+1,true));
        assertEquals(start+STEP[3]+blowAt(THRUST,0)+GIANT_LAG+Stagger.PAST,Stagger.isosang(3,3,start,start+blowAt(3,3),true));
        // (2.80.1) the giant sword reaches a body a block and a half in front of where they stood before the leap within GIANT_LAG
        assertTrue(giantReaches(backstep(blowAt(THRUST,0))+1.5,.3)<=GIANT_LAG,"the third's stagger holds until the giant sword can land");
        assertEquals(start+blowAt(THRUST,0)+Stagger.LAST,Stagger.isosang(THRUST,0,start,start+blowAt(THRUST,0),true));
    }
    @Test void aThrownSwordFliesStraightOnToItsEnd(){
        assertEquals(0,swordFlown(0,SWORD_RANGE),1e-9);assertEquals(SWORD_SPEED,swordFlown(1,SWORD_RANGE),1e-9);
        assertEquals(SWORD_RANGE,swordFlown(100,SWORD_RANGE),1e-9);assertEquals(4,swordFlown(100,4),1e-9,"stopped by a block in its way");
        assertTrue(swordFlying(0,SWORD_RANGE)&&!swordFlying(-1,SWORD_RANGE)&&!swordFlying(100,SWORD_RANGE));
        assertTrue(SWORD_RANGE>WAVE_RANGE,"further than the finisher's slash");
        assertEquals(3,SWORD_FROM.length);
        assertTrue(SWORD_FROM[0][2]>0&&SWORD_FROM[1][2]>0&&SWORD_FROM[2][2]<0,"low at the right, at the right hand, at the left");
        assertTrue(SWORD_FROM[0][1]<SWORD_FROM[1][1],"the first low");
    }
    @Test void theFourthLeapsFiveBlocksBackAndThrowsTheGiantSwordFlyingOn(){
        // (2.80.2, user request: "2칸 뒤로 움직이게 바꾸고"; 5 blocks in 2.80.1) a leap 2 blocks straight back, fast at first and slowing to a stop
        assertEquals(2,BACKSTEP,1e-9);
        assertEquals(0,backstep(0),1e-9);assertEquals(0,backstep(BACKSTEP_FROM),1e-9);assertEquals(BACKSTEP,backstep(BACKSTEP_TO),1e-9);assertEquals(BACKSTEP,backstep(30),1e-9);
        for(double k=0;k<BACKSTEP_TO+1;k+=.05)assertTrue(backstep(k+.05)>=backstep(k)-1e-12,"straight back, never forward");
        assertTrue(backstep(BACKSTEP_FROM+1)-backstep(BACKSTEP_FROM)>backstep(BACKSTEP_TO)-backstep(BACKSTEP_TO-1),"fast at first, slowing");
        for(double k=BACKSTEP_FROM;k<BACKSTEP_TO;k+=1)assertTrue(backstep(k+1)-backstep(k)<4,"a tick's leap within what a player may move: "+k);
        assertEquals(1.5,backstep(BACKSTEP_TO,1.5),1e-9,"no further than the blocks behind let it");assertEquals(0,backstep(BACKSTEP_TO,-1),1e-9);
        // ("검 안 날리는 거 같은데? … 파란색 검이 날아가는 거 안 보여?") the giant sword thrown as the leap ends, flying on point first
        assertTrue(blowAt(THRUST,0)>BACKSTEP_FROM&&blowAt(THRUST,0)<=BACKSTEP_TO,"thrown as the leap ends");
        assertEquals(GIANT_START,giantHead(0,GIANT_RANGE),1e-9);assertEquals(GIANT_START+GIANT_SPEED,giantHead(1,GIANT_RANGE),1e-9);
        assertEquals(GIANT_RANGE,giantHead(100,GIANT_RANGE),1e-9);assertEquals(5,giantHead(100,5),1e-9,"stopped by a block in its way");
        assertEquals(GIANT_START,giantHead(5,1),1e-9,"a block right in front: it stops where it was thrown");
        assertEquals((GIANT_RANGE-GIANT_START)/GIANT_SPEED,giantArrives(GIANT_RANGE),1e-9);
        // (2.80.4, user request: "검 아주 조금만 더 느리게 날아가도 될듯") 40 blocks a second, some 0.13 s from where it shows to where it
        // stops (it was 60 and 0.09 s); a little slower, still a thrust
        assertEquals(40,GIANT_SPEED*20,1e-9);
        double flight=giantArrives(GIANT_RANGE)/20;assertTrue(flight>.12&&flight<.15,"its flight: "+flight+" s");
        assertTrue(giantFlying(0,GIANT_RANGE)&&giantFlying(Math.ceil(giantArrives(GIANT_RANGE)),GIANT_RANGE)&&!giantFlying(Math.ceil(giantArrives(GIANT_RANGE))+1,GIANT_RANGE)&&!giantFlying(-1,GIANT_RANGE));
        assertTrue(GIANT_RANGE>BACKSTEP+3,"it reaches past where they stood before the leap, to whom they fought: "+GIANT_RANGE);
        // its way: from right in front of them to its point as it goes, as wide as the giant sword; not behind, not beyond, not off to the side
        assertTrue(giantOver(0,GIANT_RANGE,2,0,1,.3),"right in front at once");assertFalse(giantOver(0,GIANT_RANGE,6,0,1,.3),"not yet so far");
        assertTrue(giantOver(giantReaches(6,.3)+1e-9,GIANT_RANGE,6,0,1,.3),"reached as its point passes");assertFalse(giantOver(giantReaches(6,.3)-.1,GIANT_RANGE,6,0,1,.3));
        assertTrue(giantOver(100,GIANT_RANGE,GIANT_RANGE,0,1,.3));assertFalse(giantOver(100,GIANT_RANGE,GIANT_RANGE+1,0,1,.3),"not beyond where it stops");
        assertFalse(giantOver(100,GIANT_RANGE,-1.5,0,1,.3),"not behind them");assertFalse(giantOver(100,GIANT_RANGE,4,GIANT_HALF_WIDTH+.6,1,.3),"not off to the side");
        assertTrue(giantOver(100,GIANT_RANGE,4,GIANT_HALF_WIDTH,1,.3));assertFalse(giantOver(100,GIANT_RANGE,4,0,HIGH+.5,.3),"not far over it");
        assertEquals(GIANT_RANGE,reach(THRUST,0),1e-9);assertEquals(GIANT_HALF_WIDTH,halfWidth(THRUST,0),1e-9);
        assertTrue(MAX_REACH>=GIANT_RANGE+GIANT_HALF_WIDTH,"looked for as far as it flies");
        assertTrue(THRUST_FLY>0&&THRUST_FLY_UP>0,"it throws whom it strikes (user request: 4타에 맞으면 넉백)");
    }

    @Test void rSweepsTheGiantSwordLevelRoundItsPommelFromTheRightAcrossTheFrontToTheLeft(){
        // (2.81.0, user request: "R스킬 거대한 검을 소환해서 전방을 크게 베는거야 … 맞아도 넉백 없어 데미지 20이고"; the user's choices:
        // a 20 s cooldown, no guard takes it)
        assertEquals(20,SWEEP_DAMAGE,1e-9);assertEquals(20,R_COOLDOWN/20.0,1e-9);assertTrue(unblockable(SWEEP),"no guard takes it");
        assertTrue(drawn(SWEEP)&&!valid(SWEEP),"a swing the clients draw, not a hit of the combo");
        assertEquals(1,blows(SWEEP));assertEquals(SWEEP_DAMAGE,damage(SWEEP,0),1e-9);
        for(int[] table:new int[][]{STEP,HOLD,LENGTH})assertTrue(table.length>SWEEP,"every table has R's place");assertTrue(HOLD[SWEEP]<LENGTH[SWEEP]);
        // its way round (the video from above): held out to the right and back, round across the front, to a stop behind the left
        assertEquals(SWEEP_FROM,sweepAngle(0),1e-9);assertEquals(SWEEP_FROM,sweepAngle(SWEEP_START),1e-9);
        assertTrue(SWEEP_FROM>90&&SWEEP_FROM<135,"it begins out to the right and a little back: "+SWEEP_FROM);
        assertTrue(SWEEP_TO<-90&&SWEEP_TO>-120,"it stops out to the left and a little back: "+SWEEP_TO);
        assertEquals(0,sweepAngle(SWEEP_FRONT),1e-9);assertEquals(SWEEP_TO,sweepAngle(SWEEP_END),1e-9);assertEquals(SWEEP_TO,sweepAngle(5),1e-9);
        assertTrue(SWEEP_FRONT>.55&&SWEEP_FRONT<.65,"across the front at some 0.6 s: "+SWEEP_FRONT);
        double last=SWEEP_FROM,fastest=0;
        for(double t=0;t<=1.2;t+=1/600.0){
            double a=sweepAngle(t);assertTrue(a<=last+1e-9,"round one way only at "+t);
            fastest=Math.max(fastest,(last-a)*600);last=a;
        }
        assertTrue(fastest>900&&fastest<2500,"fast, but no jump: "+fastest+" deg/s");
        assertEquals(0,sweepDone(SWEEP_START),1e-9);assertEquals(1,sweepDone(SWEEP_END),1e-9);
        // tick by tick, the server's sweep passes every angle from where it begins to where it stops, once
        double covered=0;
        for(int k=1;k<=LENGTH[SWEEP];k++){double a0=sweepAngle((k-1)/20.0),a1=sweepAngle(k/20.0);assertTrue(a1<=a0);covered+=a0-a1;}
        assertEquals(SWEEP_FROM-SWEEP_TO,covered,1e-9);
        assertTrue(blowAt(SWEEP,0)/20.0>=SWEEP_START&&(blowAt(SWEEP,0)-1)/20.0<SWEEP_START,"its first strike on the first tick it turns");
        // whom it passes: within its reach of the pommel, in the angles it swept that tick, not behind, not far over it
        double fw=SWEEP_PIVOT_FW+4;
        assertFalse(swept(sweepAngle(.5),sweepAngle(.55),fw,0,1,.3),"not before it gets round to the front");
        assertTrue(swept(sweepAngle(.55),sweepAngle(.6),fw,0,1,.3),"struck as it crosses the front");
        assertFalse(swept(sweepAngle(.6),sweepAngle(.65),fw,0,1,.3),"once");
        assertTrue(swept(sweepAngle(.45),sweepAngle(.5),SWEEP_PIVOT_FW,4,1,.3),"out to the right, early");
        assertTrue(swept(sweepAngle(.6),sweepAngle(.7),SWEEP_PIVOT_FW,-4,1,.3),"out to the left, late");
        assertFalse(swept(SWEEP_FROM,SWEEP_TO,SWEEP_PIVOT_FW-4,0,1,.3),"never behind it");
        assertFalse(swept(SWEEP_FROM,SWEEP_TO,SWEEP_PIVOT_FW+SWEEP_REACH+.6,0,1,.3),"never past its point");
        assertTrue(swept(SWEEP_FROM,SWEEP_TO,SWEEP_PIVOT_FW+SWEEP_REACH+.2,0,1,.3),"a body its point just touches");
        assertFalse(swept(SWEEP_FROM,SWEEP_TO,fw,0,SWEEP_HIGH+.5,.3),"not far over it");assertTrue(swept(SWEEP_FROM,SWEEP_TO,fw,0,3,.3),"it is up in the air: a body on a ledge under it");
        assertTrue(swept(SWEEP_FROM,sweepAngle(.5),SWEEP_PIVOT_FW+.5,0,0,.3),"one right under its pommel as it begins");
        assertEquals(SWEEP_FRONT,sweepReaches(fw,0,1,0),.01,"the front reached as it crosses it");
        assertTrue(sweepReaches(SWEEP_PIVOT_FW,3,1,.3)<sweepReaches(fw,0,1,.3)&&sweepReaches(fw,0,1,.3)<sweepReaches(SWEEP_PIVOT_FW,-3,1,.3),"the right, then the front, then the left");
        assertEquals(-1,sweepReaches(SWEEP_PIVOT_FW-4,0,1,.3),1e-9);
        // (the user's choice, "적에게로 옮겨 감", and their word: "검은 10블록 이내에 내가 쳐다보고 있는 적까지 닿을 수 있어 앞에 적이 없다면
        // 내 바로 앞을 베어내") with a foe in the crosshair it stands out toward them so the blade passes them; never back from right
        // in front of its user, never past a foe SWEEP_AIM blocks off (2.81.1, the user's word: "사거리를 30블록으로 늘리자 그러니까
        // 바라보는 적이 30블록 이내에 있으면 검이 적한테 이동해서 밴다는 뜻임")
        assertEquals(0,sweepShift(0),1e-9);assertEquals(0,sweepShift(SWEEP_PIVOT_FW+SWEEP_MEET),1e-9);
        for(double d=SWEEP_PIVOT_FW+SWEEP_MEET;d<=SWEEP_AIM;d+=.5)assertEquals(SWEEP_PIVOT_FW+SWEEP_MEET,d-sweepShift(d),1e-9,"the blade meets a foe "+d+" off");
        assertEquals(SWEEP_AIM-SWEEP_PIVOT_FW-SWEEP_MEET,sweepShift(45),1e-9);
        assertEquals(23.7,sweepShift(30),1e-9);assertEquals(13.7,sweepShift(20),1e-9);assertEquals(3.7,sweepShift(10),1e-9);
        for(double d=0;d<=SWEEP_AIM;d+=.25){double from=d-sweepShift(d);assertTrue(sweepReaches(from,0,0,.3)>=0,"a foe in the crosshair "+d+" blocks off is struck");}
        assertEquals(30,SWEEP_AIM,1e-9);
        // (2.81.1, the user's word: "그냥 r은 원래처럼 앞만 베고 R홀드를 해야지 보고 있는 적에게 검이 순간이동 되게 만들자") R held is
        // the key held down as long as Kageno's T held is
        assertEquals(dev.sangwoo.celestialfall.stand.KagenoRules.HOLD,R_HOLD_TICKS);assertEquals(.4,R_HOLD_TICKS/20.0,1e-9);
        assertTrue(SWEEP_REACH>5&&SWEEP_PIVOT_UP>1.8,"a giant sword, up in the air: "+SWEEP_REACH);
    }
    @Test void hLaysTheSwordFormationStrikingAsTheSwordsComeDownAndEveryFiveSecondsAfter(){
        // (2.82.0, the user's word: "데미지는 처음 한 번 H 박을때 30데미지 그리고 5초마다 5 데미지")
        assertTrue(formationStrikes(FORMATION_IMPACT_AT),"as the swords come down");
        assertEquals(30,formationDamage(FORMATION_IMPACT_AT),1e-6);
        assertEquals(100,FORMATION_EVERY,"every five seconds");
        for(long a=FORMATION_IMPACT_AT+FORMATION_EVERY;a<H_END*20;a+=FORMATION_EVERY){assertTrue(formationStrikes(a),"again at "+a);assertEquals(5,formationDamage(a),1e-6);}
        int strikes=0;for(long a=0;a<LENGTH[FORMATION];a++)if(formationStrikes(a))strikes++;
        assertEquals(8,strikes,"once as the swords come down and seven times after, over its 40 s");
        assertEquals(65,formationTotal(),1e-6);
        assertFalse(formationStrikes(FORMATION_IMPACT_AT-1));assertFalse(formationStrikes(FORMATION_IMPACT_AT+1));
        assertTrue(FORMATION_IMPACT_AT/20f>=H_RAIN0&&FORMATION_IMPACT_AT/20f<=H_RAIN1,"it strikes as the swords rain down");
        // (the user's answers: some 39 s as in the video, 60 s cooldown, only R up there)
        assertEquals(826,LENGTH[FORMATION]);assertEquals(LENGTH[FORMATION],HOLD[FORMATION],"it holds them all along");
        assertEquals(1200,H_COOLDOWN);
        assertTrue(H_END-H_LANDED>38&&H_END-H_LANDED<40,"stood on the sword some 39 s: "+(H_END-H_LANDED));
        assertTrue(drawn(FORMATION));assertEquals(0,blows(FORMATION),"its strikes are its own, not a hit's blows");
        // who is in it: out to its radius, a little under and well over its ground
        assertTrue(formationOver(FORMATION_RADIUS,0,0,0));assertTrue(formationOver(0,FORMATION_RADIUS+.3,1,.3));
        assertFalse(formationOver(FORMATION_RADIUS+.5,0,0,.3));assertFalse(formationOver(0,0,FORMATION_HIGH+.1,.3));assertFalse(formationOver(0,0,FORMATION_LOW-.1,.3));
        assertTrue(formationOver(3,4,4.5,.3),"one jumping over it");
    }
    @Test void herBodyLeapsUpAndComesDownOntoTheSwordUnderHerWithoutAJump(){
        // her real height and her drawn one: continuous, and the same once she stands on the pommel
        double h=1e-4;
        for(double t=0;t<LENGTH[FORMATION]/20.0+.5;t+=.001){
            assertEquals(planned(t),planned(t+h),.015,"the drawn body jumps at "+t);
            assertEquals(raised(t),raised(t+h),.012,"the real body jumps at "+t);
            assertTrue(planned(t)>=-1e-9&&raised(t)>=-1e-9);
            if(t>=H_LANDED&&t<H_DOWN)assertEquals(raised(t),planned(t),1e-9,"drawn where she is at "+t);
            if(t<H_LEAP||t>=H_DOWN)assertEquals(0,raised(t),1e-9,"on the ground at "+t);
            // (2.82.4) up from her leap to her drop: she never comes down to the ground between, she comes down onto the pommel
            if(t>=H_PEAK&&t<H_DROP){assertEquals(FORMATION_STAND,raised(t),1e-9,"held as high as the pommel at "+t);assertTrue(planned(t)>=FORMATION_STAND-1e-9,"under the pommel at "+t);}
        }
        assertEquals(H_LEAP_HEIGHT,planned(H_PEAK),1e-9,"some three blocks up as she hangs");
        assertEquals(FORMATION_STAND,raised(10),1e-9,"on the pommel, her own height up");
        assertTrue(planned(H_DIVE)>FORMATION_STAND+1,"she comes down onto the pommel from well over it");
        assertEquals(FORMATION_STAND,planned(H_LANDED),1e-9,"down on the pommel as she lands");
        assertTrue(H_PLANT<H_DIVE,"the sword struck under her before she comes down onto it");
        assertTrue(onSword(H_LEAP)&&onSword(40)&&!onSword(H_DOWN)&&!onSword(H_LEAP-.01));
        assertTrue(H_DOWN<=LENGTH[FORMATION]/20.0,"down before its motion is over");
    }
    @Test void sheStandsOnTheSwordWhereSheLeptTheFormationAheadOfHerSwordsAllOverIt(){
        // (2.82.4, "H를 쓰면 내가 점프를 하고 그 자리에 검이 하나 있어야해 그러니까 H는 플레이어 기준 중앙으로 나오는게 아니고
        // 외곽부분으로 나와야함": the sword she stands on is where she leapt, on the formation's outer ring, its middle ahead
        // of her; the server holds her there and every client draws that sword there: one reckoning)
        assertTrue(FORMATION_STAND_BACK>FORMATION_EMPTY-FORMATION_JITTER&&FORMATION_RADIUS-FORMATION_STAND_BACK>3,"among the swords, the formation reaching on behind her");
        assertArrayEquals(new double[]{-FORMATION_STAND_BACK,0},standAt(),1e-12);
        var rnd=new java.util.Random(7);
        for(int n=0;n<300;n++){
            var id=new java.util.UUID(rnd.nextLong(),rnd.nextLong());int s0=formationSeed(id);
            assertEquals(s0,formationSeed(new java.util.UUID(id.getMostSignificantBits(),id.getLeastSignificantBits())));
            var swords=formationSwords(s0);int k=standSword();
            assertEquals(FORMATION_SWORDS,swords.length);
            assertArrayEquals(standAt(),swords[k],1e-12,"right behind the middle");
            // no other sword right by her
            for(int j=0;j<swords.length;j++)if(j!=k)assertTrue(Math.hypot(swords[j][0]-swords[k][0],swords[j][1]-swords[k][1])>1.2,"a sword by hers");
            // (2.82.16, "검이 중앙 제외 모든 범위에 박혀 있게 하자 지금 너무 앞부분에 쏠려있어") all over the formation but its
            // empty middle: none in it nor past the edge; some in every eighth of the round, and round and behind her too
            int behindHer=0;int[] eighths=new int[8];double near=1e9;
            for(int j=0;j<swords.length;j++){
                double r=Math.hypot(swords[j][0],swords[j][1]);
                assertTrue(r>=FORMATION_EMPTY-FORMATION_JITTER-1e-9&&r<=FORMATION_RADIUS-FORMATION_EDGE+FORMATION_JITTER+1e-9,"out of its bounds: "+r);
                eighths[(int)Math.floor((Math.atan2(swords[j][1],swords[j][0])+Math.PI)/(Math.PI/4))%8]++;
                if(swords[j][0]<-FORMATION_STAND_BACK)behindHer++;
                for(int i=0;i<j;i++)near=Math.min(near,Math.hypot(swords[j][0]-swords[i][0],swords[j][1]-swords[i][1]));
            }
            for(int e:eighths)assertTrue(e>=2,"swords all round: "+java.util.Arrays.toString(eighths));
            assertTrue(behindHer>=1,"some behind her");
            assertTrue(near>1.2,"none right by another: "+near);
            // the outermost are out near the edge, the innermost round the empty middle
            double far=0,in=1e9;for(var w:swords){double r=Math.hypot(w[0],w[1]);far=Math.max(far,r);in=Math.min(in,r);}
            assertTrue(far>FORMATION_RADIUS-FORMATION_EDGE-.8&&in<FORMATION_EMPTY+.8,"from "+in+" to "+far);
        }
    }
    @Test void zMovesHerUpToThreeTimesWithinTenSecondsOfTheFirstThenItsTwentySecondsRun(){
        // (2.93.0) "z스킬을 누르면 10초 동안 최대 3번 쳐다보는 검으로 이동 할 수 있어 영상 처럼 10초 안에 3번 다 사용하는 순간
        // 즉시 재사용 쿨타임 20초가 흐르고 또는 3번 다 사용 안 해도 10초가 지나면 재사용 쿨타임 20초가 흐르게"; one press, one
        // move; the ten seconds from the first (the user's answers)
        assertEquals(3,Z_USES);assertEquals(10*20,Z_WINDOW);assertEquals(20*20,Z_COOLDOWN);
        var s=ZState.FRESH;assertTrue(s.usable(0)&&s.usable(5000));assertEquals(3,s.left(5000));
        // all three in the window: its cooldown runs from the third at once
        s=zUse(s,1000,false);assertNotNull(s);assertEquals(1000,s.first());assertEquals(1,s.uses());
        assertTrue(s.open(1100)&&s.usable(1100));assertEquals(2,s.left(1100));
        s=zUse(s,1100,false);assertNotNull(s);assertEquals(1000,s.first(),"the window from the first move");assertEquals(1,s.left(1101));
        s=zUse(s,1150,false);assertNotNull(s);assertEquals(3,s.uses());
        assertEquals(1150+Z_COOLDOWN,s.ready(),"twenty seconds from the third move, at once");
        assertFalse(s.open(1151)||s.usable(1151));assertEquals(0,s.left(1151));
        assertNull(zUse(s,1151,false),"none while it cools down");assertNull(zUse(s,1150+Z_COOLDOWN-1,false));
        var again=zUse(s,1150+Z_COOLDOWN,false);assertNotNull(again,"ready again");
        assertEquals(1150+Z_COOLDOWN,again.first(),"a new window");assertEquals(1,again.uses());
        // fewer than three: its cooldown runs from the window's end
        for(int used=1;used<=2;used++){
            var t=ZState.FRESH;for(int i=0;i<used;i++)t=zUse(t,2000+60*i,false);
            assertEquals(2000+Z_WINDOW+Z_COOLDOWN,t.ready(),used+" used: from the window's end");
            assertTrue(t.usable(2000+Z_WINDOW-1),"the window still open");assertEquals(Z_USES-used,t.left(2000+Z_WINDOW-1));
            assertFalse(t.usable(2000+Z_WINDOW),"the ten seconds over: none left");assertNull(zUse(t,2000+Z_WINDOW,false));
            assertNull(zUse(t,2000+Z_WINDOW+Z_COOLDOWN-1,false));assertNotNull(zUse(t,2000+Z_WINDOW+Z_COOLDOWN,false));
        }
        // the cooldowns off for its user (/botand nocooldown): ready again at once, a move on every press
        var f=ZState.FRESH;for(int i=0;i<8;i++){f=zUse(f,4000+i,true);assertNotNull(f);assertEquals(0,f.ready());assertTrue(f.usable(4001+i));}
    }
    @Test void zMovesHerOnceEverySwordHasStruckAndLandsHerBeforeTheSwordsFlare(){
        assertTrue(Z_FROM>=H_RAIN1+.2,"every sword struck (the last strikes by H_RAIN1)");
        assertFalse(zTime(Z_FROM-.01));assertTrue(zTime(Z_FROM));assertTrue(zTime(30));
        assertTrue(zTime(H_END-Z_TRAVEL/20.0-.01));assertFalse(zTime(H_END-Z_TRAVEL/20.0),"she would still fly as the swords flare");
        assertTrue(Z_TRAVEL/20.0>.15&&Z_TRAVEL/20.0<=.25,"some 0.2 s, as the video's: "+Z_TRAVEL/20.0);
    }
    @Test void hsBasicAttackFansSixSwordsAndEachStrikesOnceEveryThreeSeconds(){
        // (2.95.0) "등 뒤에 검 6개가 나와서 바라보고 있는 상대에게 날아감", the user's answers: 2 a sword, every 3 s
        assertEquals(6,VOLLEY_SWORDS);assertEquals(VOLLEY_SWORDS,blows(VOLLEY));
        for(int i=0;i<VOLLEY_SWORDS;i++)assertEquals(2f,damage(VOLLEY,i),1e-6f,"2 a sword");
        assertEquals(12f,total(VOLLEY),1e-6f);
        assertTrue(drawn(VOLLEY),"its swords are drawn");assertFalse(valid(VOLLEY),"not one of the combo's hits");assertFalse(unblockable(VOLLEY),"a guard takes it");
        // shown one after another (the video: 0.17 s for all six), all of them before the first is thrown
        for(int i=1;i<VOLLEY_SWORDS;i++){assertTrue(volleyShows(i)>volleyShows(i-1));assertTrue(volleyThrown(i)>volleyThrown(i-1));assertTrue(blowAt(VOLLEY,i)>=blowAt(VOLLEY,i-1));}
        assertEquals(.17,volleyShows(5)-volleyShows(0),.01,"0.17 s from the first to the last");
        assertTrue(volleyShows(5)<volleyThrown(0),"the fan is whole before the first is thrown");
        for(int i=0;i<VOLLEY_SWORDS;i++){
            float flight=volleyArrives(i)-volleyThrown(i);
            assertTrue(flight>.45f&&flight<.7f,"a wheel round the air of about half a second: "+flight);
            assertTrue(volleyStrikes(i)>volleyArrives(i),"it sticks in, then strikes");
            // as the video's blows are heard and shown: 1.0 to 1.25 s in
            assertTrue(volleyStrikes(i)>=1f&&volleyStrikes(i)<=1.26f,"strikes at "+volleyStrikes(i));
            assertTrue(blowAt(VOLLEY,i)<VOLLEY_LENGTH,"over by its end");
        }
        assertTrue(VOLLEY_HOLD/20f>=volleyThrown(5),"no Z or R until the last is thrown");
        // one every 3 s from one's start to the next's (its cooldown from its end); with the cooldowns off, once it is over
        assertEquals(60,volleyReady(1000,false)-1000);assertEquals(VOLLEY_LENGTH,volleyReady(1000,true)-1000);
        // on the sword once every sword stands, over before the swords flare
        assertFalse(volleyTime(Z_FROM-.01));assertTrue(volleyTime(Z_FROM));assertTrue(volleyTime(30));
        assertTrue(volleyTime(H_END-VOLLEY_LENGTH/20.0));assertFalse(volleyTime(H_END-VOLLEY_LENGTH/20.0+.01));
        // a basic attack's stagger: until the next sword strikes and a little past; the last a moment
        long start=500;
        for(int i=0;i<VOLLEY_SWORDS-1;i++){long now=start+blowAt(VOLLEY,i);assertEquals(start+blowAt(VOLLEY,i+1)+Stagger.PAST,Stagger.isosang(VOLLEY,i,start,now,false));}
        long last=start+blowAt(VOLLEY,5);assertEquals(last+Stagger.LAST,Stagger.isosang(VOLLEY,5,start,last,false));
    }
    @Test void hsBasicAttackReachesTheWholeFormationAndThreeBlocksMore(){
        // (2.95.2) "평타 사거리는 H 범위 전체에서 3블록 정도 더": the formation's 12 blocks and 3 more, all round
        assertEquals(15,FORMATION_RADIUS+VOLLEY_BEYOND,1e-9);
        assertTrue(volleyIn(0,0,0,.3,0),"its middle");assertTrue(volleyIn(FORMATION_RADIUS,0,0,.3,0),"the formation's edge");
        assertTrue(volleyIn(0,FORMATION_RADIUS+2.9,0,0,0),"3 blocks past it");assertTrue(volleyIn(10.5,10.5,0,.3,0),"3 blocks past it anyway round");
        assertFalse(volleyIn(0,15.4,0,.3,0),"no further");assertFalse(volleyIn(11,11,0,.3,0));
        assertTrue(volleyIn(15.2,0,0,.3,0),"a body reaching into it");
        assertTrue(volleyIn(0,5,FORMATION_HIGH+3,.3,0));assertFalse(volleyIn(0,5,FORMATION_HIGH+3.1,.3,0),"over it");
        assertTrue(volleyIn(0,5,FORMATION_LOW-3,.3,0));assertFalse(volleyIn(0,5,FORMATION_LOW-3.1,.3,0),"under it");
        // within the formation's own strike all of it is (and more)
        for(double dx=-14;dx<=14;dx+=.5)for(double dz=-14;dz<=14;dz+=.5)for(double dy=-1;dy<=5;dy+=1)if(formationOver(dx,dz,dy,.3))assertTrue(volleyIn(dx,dz,dy,.3,0));
        // the server's slack and how far it follows the foe
        assertTrue(volleyIn(0,15.8,0,.3,1));assertFalse(volleyIn(0,16.4,0,.3,1));
        assertTrue(volleyIn(0,24.5,0,.3,VOLLEY_LEASH));assertFalse(volleyIn(0,25.5,0,.3,VOLLEY_LEASH));
        // her eyes are in it wherever on the formation she stands (its swords, at the height of their pommels and her eyes)
        for(var sw:formationSwords(42))assertTrue(volleyIn(sw[0],sw[1],1.75+1.62,0,0),"her eyes on a sword");
        // the crosshair: within it, as it met
        double[] in=volleyPoint(0,3.3,-5.6,2,0,8);assertArrayEquals(new double[]{2,0,8,0},in,1e-9);
        // beyond its edge: where the line from her eyes goes out of it
        double[] edge=volleyPoint(0,3.3,-5.6,0,0,30);
        assertEquals(1,edge[3],1e-9);assertEquals(15,Math.hypot(edge[0],edge[2]),1e-9);assertEquals(0,edge[0],1e-9);
        assertEquals(3.3-3.3*(15+5.6)/(30+5.6),edge[1],1e-9,"on the line to it");
        // over its top: where the line goes out through it
        double[] top=volleyPoint(0,3.3,-5.6,0,30,10);assertEquals(FORMATION_HIGH+3,top[1],1e-9);assertTrue(Math.hypot(top[0],top[2])<15);
        // under its floor
        double[] floor=volleyPoint(0,3.3,-5.6,0,-30,0);assertEquals(FORMATION_LOW-3,floor[1],1e-9);
        // any way round, never further than its edge
        for(int k=0;k<72;k++){double a=Math.toRadians(k*5);double[] q=volleyPoint(3,3.3,-4,40*Math.sin(a),-1,40*Math.cos(a));
            assertEquals(15,Math.hypot(q[0],q[2]),1e-9,"brought in to its edge");
            // on the line from her eyes to it
            double[] d={q[0]-3,q[1]-3.3,q[2]+4},t={40*Math.sin(a)-3,-1-3.3,40*Math.cos(a)+4};double l=Math.sqrt(d[0]*d[0]+d[1]*d[1]+d[2]*d[2]),m=Math.sqrt(t[0]*t[0]+t[1]*t[1]+t[2]*t[2]);
            assertEquals(1,(d[0]*t[0]+d[1]*t[1]+d[2]*t[2])/(l*m),1e-9);}
        // her eyes outside it (none on the formation's swords): the nearest point of it
        double[] near=volleyPoint(0,3,-20,0,0,-30);assertEquals(15,Math.hypot(near[0],near[2]),1e-9);assertEquals(1,near[3],1e-9);
    }
    @Test void tDashesThroughTheFoeThenItsSwordsAndThreeSlashesCome(){
        // (2.96.0) "T를 누르면 평타 6검이 날아가는 동시에 빠르게 바라보는 상대에게 돌진 베어내고 적에게 3 참격으로 베이는 동시에
        // 다른 검으로 넘어가는 스킬", the user's answers: 30 in all (the dash 6, each sword 2, each slash 4), 15 s
        assertEquals(10,blows(LUNGE));assertTrue(drawn(LUNGE));assertFalse(valid(LUNGE),"not one of the combo's hits");assertFalse(unblockable(LUNGE),"a guard takes it");
        assertEquals(30f,total(LUNGE),1e-6f);
        int dash=0,swords=0,slashes=0;
        for(int i=0;i<blows(LUNGE);i++){
            if(i>0)assertTrue(blowAt(LUNGE,i)>=blowAt(LUNGE,i-1),"in order");
            switch(lungeKind(i)){
                case 0->{dash++;assertEquals(6f,damage(LUNGE,i),1e-6f);assertTrue(blowAt(LUNGE,i)>LUNGE_GO&&blowAt(LUNGE,i)<LUNGE_LAND,"as she passes, on her dash");}
                case 1->{swords++;assertEquals(2f,damage(LUNGE,i),1e-6f);assertEquals(blowAt(LUNGE,i)/20f,volleyStrikes(LUNGE,lungeNth(i)),1e-6f,"each sword on its blow");}
                default->{slashes++;assertEquals(4f,damage(LUNGE,i),1e-6f);assertEquals(blowAt(LUNGE,i)/20f,lungeSlash(lungeNth(i)),1e-6f);}
            }
        }
        assertEquals(1,dash);assertEquals(VOLLEY_SWORDS,swords);assertEquals(LUNGE_SLASHES,slashes);
        // the video: the fan as she goes, the swords coming down 0.55 to 0.8 s as she has flown on, the slashes among the last
        for(int i=0;i<VOLLEY_SWORDS;i++){
            assertTrue(volleyShows(LUNGE,i)<volleyThrown(LUNGE,i),"shown, then thrown");
            assertTrue(volleyThrown(LUNGE,i)>=LUNGE_GO/20f-.01f,"thrown as she sets off");
            assertTrue(volleyArrives(LUNGE,i)>volleyThrown(LUNGE,i)+.2f,"a flight round the air");
            assertTrue(volleyStrikes(LUNGE,i)>volleyArrives(LUNGE,i));
            assertTrue(volleyStrikes(LUNGE,i)>=.55f&&volleyStrikes(LUNGE,i)<=.8f,"strikes at "+volleyStrikes(LUNGE,i));
        }
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);assertTrue(at>=(LUNGE_HOP+Z_TRAVEL)/20f-.001f,"she has flown on by then");assertTrue(at<LUNGE_LENGTH/20f);
            if(j>0)assertTrue(at>lungeSlash(j-1));
        }
        // a basic attack's stagger until the next blow, the last a moment
        long start=900;
        for(int i=0;i+1<blows(LUNGE);i++){long now=start+blowAt(LUNGE,i);assertEquals(Math.max(now,start+blowAt(LUNGE,i+1))+Stagger.PAST,Stagger.isosang(LUNGE,i,start,now,false));}
        // 15 s from its end; held for all of it
        assertEquals(LUNGE_LENGTH+300,lungeReady(1000,false)-1000);assertEquals(LUNGE_LENGTH,lungeReady(1000,true)-1000);
        assertEquals(LUNGE_LENGTH,HOLD[LUNGE]);assertEquals(LUNGE_LENGTH,LENGTH[LUNGE]);assertEquals(1.25,LUNGE_LENGTH/20.0,1e-9,"the video: settled 1.25 s in");
        // once every sword stands, and over (on the far sword) before they flare
        assertFalse(lungeTime(Z_FROM-.01));assertTrue(lungeTime(Z_FROM));assertTrue(lungeTime(H_END-LUNGE_LENGTH/20.0));assertFalse(lungeTime(H_END-LUNGE_LENGTH/20.0+.01));
    }
    @Test void tsWayRunsOnUnbrokenFromHerSwordToTheFoeOntoTheFarSword(){
        // (2.96.0) the formation holds her along it (the server's Formation.spotAt, the client's spot and raised): on the
        // sword she stood on, along the dash to where she lands in front of the foe (2.96.2), there, over Z's flight onto the far sword
        double fx=2,fz=-5,px=14,pz=7,tx=-9,tz=4,from=1.6,to=1.4;
        double[] prev=null;double ph=Double.NaN;
        for(double a=0;a<=LUNGE_LENGTH;a+=.05){
            double[] q=lungeSpot(a,fx,fz,px,pz,tx,tz);double h=lungeHeight(a,from,to);
            if(prev!=null){
                // never a jump: a twentieth of a tick moves her well under a block (the dash and the flight on are quick, not broken)
                assertTrue(Math.hypot(q[0]-prev[0],q[1]-prev[1])<1,"a jump at "+a);
                assertTrue(Math.abs(h-ph)<.5,"its height jumps at "+a);
            }
            prev=q;ph=h;
            assertTrue(h>=-1e-9,"never under the ground");
        }
        assertArrayEquals(new double[]{fx,fz},lungeSpot(0,fx,fz,px,pz,tx,tz),1e-9);assertEquals(from,lungeHeight(0,from,to),1e-9,"on her pommel as it begins");
        assertArrayEquals(new double[]{px,pz},lungeSpot(LUNGE_LAND,fx,fz,px,pz,tx,tz),1e-9);assertEquals(0,lungeHeight(LUNGE_LAND,from,to),1e-9,"down in front of the foe");
        assertArrayEquals(new double[]{px,pz},lungeSpot(LUNGE_HOP,fx,fz,px,pz,tx,tz),1e-9);
        assertArrayEquals(new double[]{tx,tz},lungeSpot(LUNGE_HOP+Z_TRAVEL,fx,fz,px,pz,tx,tz),1e-9);assertEquals(to,lungeHeight(LUNGE_HOP+Z_TRAVEL,from,to),1e-9,"on the far pommel");
        assertEquals(to,lungeHeight(LUNGE_LENGTH,from,to),1e-9);
        // out of sight in the light of her dash and as she flies on (2.96.3: seen as she sets off, trailing a few violet
        // afterimages, hidden from as its light comes until just before she is down in front of the foe; seen there and after)
        assertFalse(lungeHidden(LUNGE_GO-.1));assertFalse(lungeHidden(LUNGE_GO+.5));
        assertTrue(lungeHidden(LUNGE_GO+LUNGE_LIGHT+.05));assertTrue(lungeHidden(LUNGE_LAND-LUNGE_SEEN-.05));
        assertFalse(lungeHidden(LUNGE_LAND-LUNGE_SEEN+.05));assertFalse(lungeHidden(LUNGE_LAND+.5));
        assertEquals(.2,(LUNGE_GO+LUNGE_LIGHT)/20.0,1e-9,"its light comes at the video's 0.2 s");
        assertTrue((LUNGE_LAND-LUNGE_SEEN)/20.0>=.32,"hidden while the light covers her (the video: to some 0.32 s)");
        assertTrue(lungeHidden(LUNGE_HOP+1));assertFalse(lungeHidden(LUNGE_HOP+Z_TRAVEL+.1));
        // each tick's step of the longest dash (from her sword across the formation to a foe at the reach's far edge)
        // under the 8 blocks a frame the camera takes for a teleport (BotandCamera)
        double longest=FORMATION_RADIUS+VOLLEY_BEYOND+FORMATION_RADIUS;
        assertTrue(longest/(LUNGE_LAND-LUNGE_GO)<8,"a tick's step: "+longest/(LUNGE_LAND-LUNGE_GO));
    }
    @Test void tLandsLowJustInFrontOfTheFoeAtItsSide(){
        // (2.96.2, the user's video read again and confirmed: "상대 바로 앞 옆 (영상처럼)") LUNGE_FRONT short of the foe on
        // the way she came, LUNGE_SIDE to her right; never past it, and never behind where she set out
        for(int k=0;k<16;k++){
            double a=Math.PI*2*k/16,wx=Math.cos(a),wz=Math.sin(a),gx=3,gz=-4;
            for(double reach:new double[]{.3,1,1.5,6,20}){
                double[] p=lungeLanding(gx,gz,wx,wz,reach);double dx=p[0]-gx,dz=p[1]-gz;
                double front=-(dx*wx+dz*wz),side=dx*-wz+dz*wx;
                assertEquals(LUNGE_SIDE,side,1e-9,"to her right");
                assertTrue(front>=-1e-9,"never past the foe");
                assertTrue(front<=Math.max(0,reach-.5)+1e-9,"never behind where she set out");
                if(reach>=LUNGE_FRONT+.5)assertEquals(LUNGE_FRONT,front,1e-9);
            }
        }
        // her right as she dashes: dashing south (+z) her right is west (-x)
        double[] p=lungeLanding(0,0,0,1,10);assertEquals(-LUNGE_SIDE,p[0],1e-9);assertEquals(-LUNGE_FRONT,p[1],1e-9);
        assertTrue(LUNGE_FRONT>=1&&LUNGE_FRONT<=2&&LUNGE_SIDE>=.5&&LUNGE_SIDE<=1.5,"just in front of it, at its side");
    }
    @Test void tFliesOnToAnySwordOnTheOtherSideOfHers(){
        // (2.96.1, user request: "스킬을 사용한 검에 반대편 검 아무대나 이동으로 바꿔") every formation, from every sword:
        // only swords on the other side of its middle (more than a right angle round from hers), never her own, about
        // half of them to pick from, each picked by some roll
        for(int seed=0;seed<40;seed++){
            double[][] sw=formationSwords(seed*977);
            for(int from=0;from<sw.length;from++){
                int[] far=oppositeSwords(sw,from);
                assertTrue(far.length>=sw.length/4,"few to pick from: "+far.length+" from sword "+from);
                for(int k:far){assertNotEquals(from,k);assertTrue(sw[k][0]*sw[from][0]+sw[k][1]*sw[from][1]<0,"sword "+k+" is on her side of the middle");}
                java.util.Set<Integer> picked=new java.util.HashSet<>();
                for(int roll=0;roll<far.length*3;roll++){int k=oppositeSword(sw,from,roll);picked.add(k);assertTrue(java.util.Arrays.stream(far).anyMatch(x->x==k));}
                assertEquals(far.length,picked.size(),"every one of them can be picked");
            }
            // from where she stands as the formation begins (right behind its middle): the half ahead of the middle
            for(int k:oppositeSwords(sw,standSword()))assertTrue(sw[k][0]>0,"ahead of the middle");
        }
        assertEquals(2,oppositeSword(new double[][]{{1,0},{2,0},{-1,0}},0,7),"the one on the other side");
    }
    @Test void hsBasicAttacksSoundIsTheVideosRunningPastItsLastBlow()throws Exception{
        // (2.95.0) the user's video's own sound (cut_volley_sound.py): its swords' rush, and their burst on the blows, run
        // on past the last until it is back down to the formation's sound
        double v=StarPlatinumRulesTest.seconds("isosang_volley");
        assertTrue(v>volleyStrikes(VOLLEY_SWORDS-1)+.3&&v<2.2,"its sound: "+v+" s");
        // (2.96.0) T's, from the user's video of it: its rush, its crack, the swords' blows and the slashes, on past them
        double t=StarPlatinumRulesTest.seconds("isosang_t");
        assertTrue(t>lungeSlash(LUNGE_SLASHES-1)+.4&&t<2.2,"its sound: "+t+" s");
    }
    @Test void hsDodgeHoldsFromItsStartAndMovesHerToAnyOtherSwordAtRandom(){
        // (2.94.0) "H를 쓰고 단 1회 맞은 데미지 무시하고 랜덤한 검으로 이동": from H's start (a blow before the swords stand is kept
        // from her at once, and she moves as they do), until a move would land too late, as Z's
        assertTrue(dodgeTime(0),"from H's start");assertTrue(dodgeTime(.3));assertTrue(dodgeTime(Z_FROM));assertTrue(dodgeTime(30));
        assertFalse(dodgeTime(-.05),"not before it");
        assertTrue(dodgeTime(H_END-Z_TRAVEL/20.0-.01));assertFalse(dodgeTime(H_END-Z_TRAVEL/20.0),"she would still fly as the swords flare");
        for(double t=Z_FROM;t<H_END;t+=.05)assertEquals(zTime(t),dodgeTime(t),"its move as Z's at "+t);
        // any sword but the one she stands on, each as likely: every roll a sword, every other sword one roll
        int n=FORMATION_SWORDS;
        for(int on=0;on<n;on++){
            var seen=new java.util.HashSet<Integer>();
            for(int roll=0;roll<n-1;roll++){int k=dodgeSword(on,roll,n);assertNotEquals(on,k,"never the one she stands on");assertTrue(k>=0&&k<n);assertTrue(seen.add(k),"one roll each");}
            assertEquals(n-1,seen.size(),"every other sword");
        }
        // a roll out of its range still lands on one of them
        assertNotEquals(5,dodgeSword(5,n+7,n));assertNotEquals(5,dodgeSword(5,-3,n));
    }
    @Test void herFlightRunsSmoothlyFromOnePommelToTheOther()throws Exception{
        assertEquals(0,zAlong(0),1e-12);assertEquals(1,zAlong(1),1e-12);assertEquals(0,zArc(0),1e-12);assertEquals(0,zArc(1),1e-12);
        double prev=0;
        for(int i=1;i<=1000;i++){
            double u=i/1000.0,a=zAlong(u);assertTrue(a>=prev-1e-12,"onward at "+u);assertTrue(a-prev<.005,"no jump at "+u);prev=a;
            assertTrue(zArc(u)>=0&&zArc(u)<=Z_ARC+1e-9);
        }
        assertTrue(zAlong(.25)>.4,"quick from the start: "+zAlong(.25));
        assertTrue((zAlong(1)-zAlong(.98))/.02<.1,"easing onto the pommel");
        // the sound of it is the user's video's, about a second
        double z=StarPlatinumRulesTest.seconds("isosang_z");assertTrue(z>.8&&z<1.3,"Z's sound: "+z+" s");
    }
    @Test void hsSoundRunsFromItsStartToTheFormationsEndAndItsEndsGoesOn()throws Exception{
        // (2.82.18) "처음부터 끝날때까지 소리가 나야함 영상처럼": H's sound is its recording right through to H_END (2.82.17
        // and before: its first 4.2 s), and the end's (from H_END to the video's end) takes over where it stops
        double cast=StarPlatinumRulesTest.seconds("isosang_h"),end=StarPlatinumRulesTest.seconds("isosang_h_end");
        assertEquals(H_END,cast,.02,"H's sound runs to the formation's end");
        assertTrue(end>1&&end<2,"the end's goes on from there: "+end+" s");
        // streamed (40 s is too long to keep whole in memory), the end's loaded whole as before
        String json;try(var in=IsosangRulesTest.class.getResourceAsStream("/assets/celestialfall/sounds.json")){assertNotNull(in);json=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        var sounds=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertTrue(sounds.getAsJsonObject("isosang_h").getAsJsonArray("sounds").get(0).getAsJsonObject().get("stream").getAsBoolean(),"H's sound streams");
    }
    @Test void gStrikesEveryoneWithinThirtyBlocksAsTheSwordLands()throws Exception{
        // (2.83.0) "피해 100 쿨 5분", "범위는 30블록": once, as its sword strikes (3.1 s), holding her through its cutscene
        assertEquals(100,G_DAMAGE,1e-6);assertEquals(30,G_RANGE,1e-9);
        assertEquals(6000,G_COOLDOWN,"five minutes");
        assertEquals(62,G_IMPACT);assertTrue(ultimateStrikes(62)&&!ultimateStrikes(61)&&!ultimateStrikes(63));
        assertTrue(G_HOLD>G_IMPACT&&G_HOLD/20f<5,"she is held through the cutscene, under five seconds");
        assertEquals(G_HOLD,HOLD[ULTIMATE]);assertEquals(G_HOLD,LENGTH[ULTIMATE]);assertTrue(drawn(ULTIMATE));assertEquals(0,blows(ULTIMATE));
        // within 30 blocks of her middle: a body 29.5 blocks out level is struck, 30.6 is not; above and below too
        assertTrue(ultimateReaches(29.5,0,0,.3,1.8));assertFalse(ultimateReaches(30.6,0,0,.3,1.8));
        assertTrue(ultimateReaches(0,25,0,.3,1.8));assertFalse(ultimateReaches(0,31.2,0,.3,1.8));
        assertTrue(ultimateReaches(0,-29,0,.3,1.8));assertFalse(ultimateReaches(0,-32,0,.3,1.8));
        assertTrue(ultimateReaches(21,0,21,.3,1.8),"diagonally within");assertFalse(ultimateReaches(22,0,22,.3,1.8));
        // two recordings, each as long as it goes on in the video (the screen dark to silence again)
        assertEquals(2,G_VARIANTS);
        for(int i=1;i<=G_VARIANTS;i++){double len=StarPlatinumRulesTest.seconds("isosang_g_"+i);assertEquals(7.28,len,.05,"its recording "+i);}
        // the times: the face, the sky, the fall, the strike, the game back, in order
        assertTrue(G_GLOW<G_FACE&&G_FACE<G_SKY&&G_SKY<G_FALL&&G_FALL<G_STRIKE&&G_STRIKE<G_BACK&&G_BACK<G_GAME&&G_GAME<G_ERUPT&&G_ERUPT<G_CLEAR);
        assertEquals(G_IMPACT/20f,G_STRIKE,1e-6);
    }
}
