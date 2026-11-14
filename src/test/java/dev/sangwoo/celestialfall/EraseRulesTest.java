package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.EraseCubes;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the delete slash (2.133.0, the user's: "일단 공중에 떠올라서 참격을 세로로 날리면 바닥에
 * 맞은 부분만 삭제되게 … 한 번 날리고 그냥 바닥에 박히고 삭제되게 … 10 × 10으로 범위를 줄여", "참격이 맞은 땅은 덩어리째
 * 떠오르다가 글리치 폭발되게 그리고 폭발되고 10초 이따가 땅이 다시 돌아오게", "올라가는 덩어리는 10×10×10 정육면체로 하고
 * 터질때까지 그 덩어리 위에 있으면 다시 150데미지 … 떠올라서 터지기까지 시간은 15초 정도 … 총 250데미지"; "피해 100 쿨타임
 * 60초", "십자선이 가리키는 땅"): one skill of the knife's pipeline whose one blow throws its slash; a leap of five
 * blocks; the slash to the ground aimed at, no further than 20 blocks out, landing within a few ticks; ten by ten struck
 * for 100 and deleted for half a second; a cube of that ground ten blocks each way rising and bursting 15 s on, 150 to
 * whoever is on it; the ground back 10 s after; its cooldown 60 s from its end.
 */
class EraseRulesTest {
    @Test void itIsOneSkillOfTheKnifesPipelineItsBlowThrowingTheSlash(){
        assertEquals(16,ERASE);assertEquals(ERASE,LAST);
        assertTrue(valid(ERASE)&&!valid(ERASE+1));
        assertTrue(skill(ERASE)&&erase(ERASE)&&!erase(KNIVES)&&!erase(SLAM)&&!erase(SHOT));
        assertEquals(1,blows(ERASE),"its one blow throws the slash");assertEquals(EraseRules.SLASH,blowAt(ERASE,0));
        assertEquals(0,damage(ERASE,0),1e-9,"the blow strikes no one: the slash does");
        assertEquals(EraseRules.HOLD,HOLD[ERASE]);assertEquals(EraseRules.LENGTH,LENGTH[ERASE]);
        assertFalse(place(ERASE)>0||last(ERASE)||single(ERASE)||launches(ERASE,0),"no hit of the combo, no one thrown");
        assertEquals(1200,EraseRules.COOLDOWN,"the user's 60 s");assertEquals(1200,cooldown(ERASE));
        assertEquals(1000+EraseRules.LENGTH+1200,EraseRules.ready(1000),"its cooldown from its end");
    }
    @Test void itsMomentsComeInTurn(){
        int[] t={0,EraseRules.LIT,EraseRules.LEAVE,EraseRules.RISEN,EraseRules.SLASH,EraseRules.FALL,EraseRules.LAND,EraseRules.HOLD,EraseRules.LENGTH};
        for(int i=1;i<t.length;i++)assertTrue(t[i]>t[i-1],"in turn: "+i);
        assertTrue(EraseRules.AIM_FROM<EraseRules.LEAVE,"its user's client tells where it is aimed from as they leave the ground");
        // the slash lands before they do, however far it flies
        assertTrue(EraseRules.SLASH+EraseRules.FLY_MAX<=EraseRules.LAND);
        assertTrue(EraseRules.LENGTH<=40,"two seconds at most");
    }
    @Test void theLeapIsFiveBlocksUpAndBackSmoothly(){
        assertEquals(5,EraseRules.JUMP,1e-9,"the user's 5 blocks");
        assertEquals(0,EraseRules.risen(0),1e-9);assertEquals(0,EraseRules.risen(EraseRules.LEAVE),1e-9);
        assertEquals(1,EraseRules.risen(EraseRules.RISEN),1e-9);assertEquals(1,EraseRules.risen(EraseRules.SLASH),1e-9,"at the top as it throws");
        assertEquals(0,EraseRules.risen(EraseRules.LAND),1e-9);assertEquals(0,EraseRules.risen(EraseRules.LENGTH),1e-9);
        double last=0;
        for(double a=0;a<=EraseRules.LENGTH;a+=1/3.0){
            double r=EraseRules.risen(a);
            assertTrue(r>=0&&r<=1);
            assertTrue(Math.abs(r-last)*EraseRules.JUMP<1.0,"no jump of the body at "+a+": "+r+" after "+last);
            if(a<=EraseRules.RISEN)assertTrue(r>=last-1e-9,"rising until the top");
            if(a>=EraseRules.FALL)assertTrue(r<=last+1e-9,"falling after it");
            last=r;
        }
        assertFalse(EraseRules.airborne(EraseRules.LEAVE-1));assertTrue(EraseRules.airborne(EraseRules.LEAVE));
        assertTrue(EraseRules.airborne(EraseRules.SLASH));assertFalse(EraseRules.airborne(EraseRules.LAND));
    }
    @Test void itLandsNoFurtherThanTwentyBlocksOutAndFliesThereInAFewTicks(){
        assertEquals(20,EraseRules.REACH,1e-9,"as far out as it lands (what I told the user)");
        double[] near=EraseRules.within(0,0,6,-8);assertEquals(6,near[0],1e-9);assertEquals(-8,near[1],1e-9);
        double[] far=EraseRules.within(10,10,10+30,10+40);
        assertEquals(20,Math.hypot(far[0]-10,far[1]-10),1e-9,"brought in to 20 blocks");
        assertEquals(.6,(far[0]-10)/20,1e-9,"the way it lay kept");assertEquals(.8,(far[1]-10)/20,1e-9);
        assertEquals(EraseRules.FLY_MIN,EraseRules.flight(0));assertEquals(EraseRules.FLY_MIN,EraseRules.flight(3));
        assertEquals(EraseRules.FLY_MAX,EraseRules.flight(400),"never longer than its most");
        int last=0;for(double d=0;d<=30;d+=.5){int f=EraseRules.flight(d);assertTrue(f>=last,"further, no sooner");last=f;}
        assertTrue(EraseRules.flight(Math.hypot(20,6))<=6,"its furthest landing in less than a third of a second");
    }
    @Test void itDeletesTenByTenRoundWhereItLands(){
        assertEquals(10,EraseRules.SIDE,"the user's 10 × 10 × 10");
        assertEquals(100,EraseRules.DAMAGE,1e-9,"the user's 100");assertEquals(150,EraseRules.BURST_DAMAGE,1e-9,"the user's 150");
        assertEquals(250,EraseRules.DAMAGE+EraseRules.BURST_DAMAGE,1e-9,"the user's 250 all told");
        assertEquals(10,EraseRules.VANISH,"the user's half a second");
        // its ground: ten blocks each way, its middle the block corner nearest where it lands
        assertEquals(-5,EraseRules.corner(.2));assertEquals(-4,EraseRules.corner(.6));assertEquals(12-5,EraseRules.corner(12.49));
        int x0=EraseRules.corner(3.7),z0=EraseRules.corner(-8.2);
        assertTrue(Math.abs(x0+EraseRules.SIDE*.5-3.7)<=.5&&Math.abs(z0+EraseRules.SIDE*.5+8.2)<=.5,"round where it lands");
        assertTrue(EraseRules.over(x0,z0,x0+4.7,x0+5.3,z0+4.7,z0+5.3),"one at its middle");
        assertTrue(EraseRules.over(x0,z0,x0-.25,x0+.35,z0+2,z0+2.6),"one half over its edge");
        assertFalse(EraseRules.over(x0,z0,x0-.7,x0-.1,z0+2,z0+2.6),"one beside it");
        assertFalse(EraseRules.over(x0,z0,x0+10.05,x0+10.65,z0+2,z0+2.6),"one past it");
        // whose feet are on its ground, or a jump over it
        assertTrue(EraseRules.struck(0,0));assertTrue(EraseRules.struck(2.5,0),"jumping");assertTrue(EraseRules.struck(-1,0),"on a step down");
        assertFalse(EraseRules.struck(4.5,0),"far over it");assertFalse(EraseRules.struck(-2,0),"under it");
    }
    @Test void theCubeRisesGentlyWhollyOutOfTheGroundAndBurstsFifteenSecondsOn(){
        assertEquals(EraseRules.VANISH,EraseRules.RISE_FROM,"it rises as those it deleted are back");
        assertEquals(300,EraseRules.BURST-EraseRules.RISE_FROM,"the user's 15 s from when it rises");
        assertEquals(200,EraseRules.BACK-EraseRules.BURST,"the user's 10 s: the ground back");
        assertTrue(EraseRules.LIFT>EraseRules.SIDE,"wholly out of the ground, standing on the air");
        assertEquals(0,EraseRules.lift(0),1e-9);assertEquals(0,EraseRules.lift(EraseRules.RISE_FROM),1e-9);
        assertEquals(EraseRules.LIFT,EraseRules.lift(EraseRules.RISE_FROM+EraseRules.RISE_TICKS),1e-9);
        assertEquals(EraseRules.LIFT,EraseRules.lift(EraseRules.BURST-1),1e-9,"held up there until it bursts");
        assertTrue(Double.isNaN(EraseRules.lift(EraseRules.BURST)),"none once it has burst");
        assertFalse(EraseRules.cube(-1));assertTrue(EraseRules.cube(0));assertTrue(EraseRules.cube(EraseRules.BURST-1));assertFalse(EraseRules.cube(EraseRules.BURST));
        // gently: a tick's rise small enough for the clients' cube and the server's to stay together (a tick apart, under the server's quarter block)
        double last=0,most=0;
        for(int t=0;t<EraseRules.BURST;t++){double l=EraseRules.lift(t);assertTrue(l>=last-1e-12,"only up");most=Math.max(most,l-last);last=l;}
        assertTrue(most<.25,"at most "+most+" a tick");
        for(double t=0;t<EraseRules.BURST-.05;t+=.05){
            double a=EraseRules.lift(t),b=EraseRules.lift(t+.05);
            assertTrue(Math.abs(b-a)<.02,"no jump between frames at "+t);
        }
    }
    @Test void whoIsOnItAndWhoStandsOnItAsItRises(){
        assertTrue(EraseRules.on(11,11));assertTrue(EraseRules.on(13.5,11),"a jump up from it");assertTrue(EraseRules.on(10.5,11),"just under its top (a step down at its edge)");
        assertFalse(EraseRules.on(14.5,11),"far over it");assertFalse(EraseRules.on(9,11),"under it");
        assertTrue(EraseRules.standing(11.05,11));assertFalse(EraseRules.standing(11.5,11),"in the air over it");
        // one standing on it is carried up to its top as it rises; one in the air over it is not; one beside it is not
        var c=new EraseCubes.Cube(0,0,64,EraseRules.RISE_FROM+40);
        double prev=c.topAt(c.t()-1),now=c.top();
        assertTrue(now>prev,"it is rising then");
        var box=c.box();assertEquals(now,box.maxY,1e-9);assertEquals(now-EraseRules.SIDE,box.minY,1e-9);
        assertEquals(0,box.minX,1e-9);assertEquals(10,box.maxX,1e-9);
        assertTrue(c.over(new net.minecraft.util.math.Box(4.7,prev,4.7,5.3,prev+1.8,5.3)));
        assertFalse(c.over(new net.minecraft.util.math.Box(-1,prev,4.7,-.4,prev+1.8,5.3)));
        assertNull(new EraseCubes.Cube(0,0,64,EraseRules.BURST).box(),"none once it has burst");
    }
}
