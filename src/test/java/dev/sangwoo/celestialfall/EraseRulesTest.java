package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.EraseCubes;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack on, the terrain delete (2.133.0, the user's: "지금 H를 q활성화 H를 바꾸고 기존 H는 삭제해 그리고
 * 띄우는 걸 4개로 하자 참격 모션 전부 삭제하고 패널을 건들이고 하나는 바라보는 방향에 있는 10 × 10 × 10 블록 위에 거대한 !!!가
 * 뜨게하고 나머지는 근처 플레이어 위치에 !!!가 각각 3개 뜨게하고 공중으로 뜨게 근처 플레이어가 없으면 그냥 랜덤한 곳에 !!!
 * 뜨고 올라가게"; they chose no hit as it begins, only the burst's 150, "!!!" a second, nearby within 20 blocks; and before:
 * "올라가는 덩어리는 10×10×10 정육면체로 하고 터질때까지 그 덩어리 위에 있으면 다시 150데미지 … 떠올라서 터지기까지 시간은
 * 15초 정도", "폭발되고 10초 이따가 땅이 다시 돌아오게", "쿨타임 60초"): one skill of the knife's pipeline whose one blow
 * is the press of the panel; four grounds ten by ten, one where they look and the others under the nearest ones within 20
 * blocks or at random, none overlapping; each rising as a cube a second on and bursting 15 s after it rises, 150 to
 * whoever is on it; the ground back 10 s after; its cooldown 60 s from its end.
 */
class EraseRulesTest {
    @Test void itIsOneSkillOfTheKnifesPipelineItsBlowThePress(){
        assertEquals(16,ERASE);assertEquals(ERASE,LAST);
        assertTrue(valid(ERASE)&&!valid(ERASE+1));
        assertFalse(valid(9),"the cleave's 9 is gone (the user's: \"기존 H는 삭제해\")");
        assertTrue(skill(ERASE)&&erase(ERASE)&&!erase(KNIVES)&&!erase(SLAM)&&!erase(SHOT));
        assertEquals(1,blows(ERASE),"its one blow is the press");assertEquals(EraseRules.MARK,blowAt(ERASE,0));
        assertEquals(0,damage(ERASE,0),1e-9,"the press strikes no one (the user's choice: only the burst's 150)");
        assertEquals(EraseRules.HOLD,HOLD[ERASE]);assertEquals(EraseRules.LENGTH,LENGTH[ERASE]);
        assertFalse(place(ERASE)>0||last(ERASE)||single(ERASE)||launches(ERASE,0),"no hit of the combo, no one thrown");
        assertEquals(1200,EraseRules.COOLDOWN,"the user's 60 s");assertEquals(1200,cooldown(ERASE));
        assertEquals(1000+EraseRules.LENGTH+1200,EraseRules.ready(1000),"its cooldown from its end");
    }
    @Test void itsMomentsComeInTurn(){
        assertTrue(0<EraseRules.MARK&&EraseRules.MARK<EraseRules.HOLD&&EraseRules.HOLD<=EraseRules.LENGTH);
        assertTrue(EraseRules.LENGTH<=20,"a press: a second at most");
        assertEquals(20,EraseRules.WARN,"the user's \"!!!\" a second");
        assertEquals(EraseRules.WARN,EraseRules.RISE_FROM,"it rises as the \"!!!\" goes");
    }
    @Test void fourGroundsOneWhereTheyLookTheOthersNearOrAtRandom(){
        assertEquals(4,EraseRules.CUBES,"the user's four");assertEquals(3,EraseRules.NEAR_ONES,"the user's three near");
        assertEquals(20,EraseRules.NEAR,1e-9,"the user's 20 blocks");assertEquals(20,EraseRules.REACH,1e-9);
        assertEquals(10,EraseRules.SIDE,"the user's 10 × 10 × 10");
        assertTrue(EraseRules.LOOK!=EraseRules.PLAYER&&EraseRules.PLAYER!=EraseRules.RANDOM&&EraseRules.LOOK!=EraseRules.RANDOM);
        // where they look: brought in to 20 blocks, the way it lay kept
        double[] near=EraseRules.within(0,0,6,-8);assertEquals(6,near[0],1e-9);assertEquals(-8,near[1],1e-9);
        double[] far=EraseRules.within(10,10,10+30,10+40);
        assertEquals(20,Math.hypot(far[0]-10,far[1]-10),1e-9);assertEquals(.6,(far[0]-10)/20,1e-9);
        // a random place: within NEAR of them, all over the round
        double most=0;boolean[] quarter=new boolean[4];
        for(int i=0;i<40;i++)for(int j=0;j<40;j++){
            double[] p=EraseRules.random(5,7,i/39.0,j/39.0);double r=Math.hypot(p[0]-5,p[1]-7);
            assertTrue(r<=EraseRules.NEAR+1e-9);most=Math.max(most,r);
            if(r>1)quarter[(p[0]>5?1:0)+(p[1]>7?2:0)]=true;
        }
        assertEquals(EraseRules.NEAR,most,1e-6);for(boolean q:quarter)assertTrue(q,"every way round");
        assertEquals(0,Math.hypot(EraseRules.random(5,7,0,.3)[0]-5,EraseRules.random(5,7,0,.3)[1]-7),1e-9);
    }
    @Test void theGroundUnderOneNearHasThemOnItAndOverlapsNoneMarked(){
        // its middle the block corner nearest them
        assertEquals(-5,EraseRules.corner(.2));assertEquals(-4,EraseRules.corner(.6));assertEquals(12-5,EraseRules.corner(12.49));
        List<int[]> taken=new ArrayList<>();
        int[] a=EraseRules.around(3.7,-8.2,taken);
        assertArrayEquals(new int[]{EraseRules.corner(3.7),EraseRules.corner(-8.2)},a,"round them when nothing is in the way");
        assertTrue(EraseRules.holds(a[0],a[1],3.7,-8.2));
        taken.add(a);
        assertNull(EraseRules.around(5.1,-7.9,taken),"one on a ground marked already is lifted by it: none of their own");
        // one beside a marked ground: theirs is shifted off it, still under them
        int[] b=EraseRules.around(a[0]+11.5,a[1]+4.5,taken);
        assertNotNull(b);assertTrue(EraseRules.holds(b[0],b[1],a[0]+11.5,a[1]+4.5),"still under them");
        assertTrue(EraseRules.free(b[0],b[1],taken),"overlapping none marked");
        assertTrue(b[0]!=EraseRules.corner(a[0]+11.5)||b[1]!=EraseRules.corner(a[1]+4.5),"shifted from round them");
        // the nearest shift: no other free one with them on it is nearer round them
        int cx=EraseRules.corner(a[0]+11.5),cz=EraseRules.corner(a[1]+4.5),d=(b[0]-cx)*(b[0]-cx)+(b[1]-cz)*(b[1]-cz);
        for(int x0=b[0]-12;x0<b[0]+12;x0++)for(int z0=b[1]-12;z0<b[1]+12;z0++)
            if(EraseRules.holds(x0,z0,a[0]+11.5,a[1]+4.5)&&EraseRules.free(x0,z0,taken))assertTrue((x0-cx)*(x0-cx)+(z0-cz)*(z0-cz)>=d);
        // boxed in on both sides: none
        List<int[]> walls=new ArrayList<>();walls.add(new int[]{0,0});walls.add(new int[]{11,0});
        assertNull(EraseRules.around(10.5,5,walls),"no ten by ten fits between them with them on it");
        // overlap: by the corners
        assertTrue(EraseRules.overlap(0,0,9,9));assertFalse(EraseRules.overlap(0,0,10,0));assertFalse(EraseRules.overlap(0,0,0,-10));
        // whose box is over a ground
        int x0=EraseRules.corner(3.7),z0=EraseRules.corner(-8.2);
        assertTrue(EraseRules.over(x0,z0,x0-.25,x0+.35,z0+2,z0+2.6),"one half over its edge");
        assertFalse(EraseRules.over(x0,z0,x0-.7,x0-.1,z0+2,z0+2.6),"one beside it");
    }
    @Test void theCubeRisesGentlyAfterTheWarningAndBurstsFifteenSecondsOn(){
        assertEquals(300,EraseRules.BURST-EraseRules.RISE_FROM,"the user's 15 s from when it rises");
        assertEquals(200,EraseRules.BACK-EraseRules.BURST,"the user's 10 s: the ground back");
        assertEquals(150,EraseRules.BURST_DAMAGE,1e-9,"the user's 150");
        assertTrue(EraseRules.LIFT>EraseRules.SIDE,"wholly out of the ground, standing on the air");
        assertEquals(0,EraseRules.lift(0),1e-9);assertEquals(0,EraseRules.lift(EraseRules.RISE_FROM),1e-9,"still in the ground through the warning");
        assertEquals(EraseRules.LIFT,EraseRules.lift(EraseRules.RISE_FROM+EraseRules.RISE_TICKS),1e-9);
        assertEquals(EraseRules.LIFT,EraseRules.lift(EraseRules.BURST-1),1e-9,"held up there until it bursts");
        assertTrue(Double.isNaN(EraseRules.lift(EraseRules.BURST)),"none once it has burst");
        assertFalse(EraseRules.cube(-1));assertTrue(EraseRules.cube(0));assertTrue(EraseRules.cube(EraseRules.BURST-1));assertFalse(EraseRules.cube(EraseRules.BURST));
        double last=0,most=0;
        for(int t=0;t<EraseRules.BURST;t++){double l=EraseRules.lift(t);assertTrue(l>=last-1e-12,"only up");most=Math.max(most,l-last);last=l;}
        assertTrue(most<.25,"gently: at most "+most+" a tick (the server's quarter block)");
        for(double t=0;t<EraseRules.BURST-.05;t+=.05){
            double a=EraseRules.lift(t),b=EraseRules.lift(t+.05);
            assertTrue(Math.abs(b-a)<.02,"no jump between frames at "+t);
        }
    }
    @Test void whoIsOnItAndWhoStandsOnItAsItRises(){
        assertTrue(EraseRules.on(11,11));assertTrue(EraseRules.on(13.5,11),"a jump up from it");assertTrue(EraseRules.on(10.5,11),"just under its top (a step down at its edge)");
        assertFalse(EraseRules.on(14.5,11),"far over it");assertFalse(EraseRules.on(9,11),"under it");
        assertTrue(EraseRules.standing(11.05,11));assertFalse(EraseRules.standing(11.5,11),"in the air over it");
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
