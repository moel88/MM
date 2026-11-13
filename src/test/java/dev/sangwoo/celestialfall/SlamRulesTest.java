package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.SlamRules;
import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's G with the hack off, the slam (2.128.0, the user's video "G - Overwrite Slam" and words: "검이 q활성화 상태
 * 색깔로 변하고 보라색 네온 빛 원을 소환하고 공중에 올라가서 내가 보는 방향으로 내려찍는 스킬이야"; their answers: the
 * reading "맞음, 이대로", "화면 가운데가 가리키는 땅", "떨어질 수 있는 범위", "피해 50 · 60초 · 가드 불가"): its times are
 * the video's; the body rises straight up, slowing, and dives down the line to where it comes down without a step;
 * it comes down within the circle; its landing strikes round where it came down; and it is a skill of the knife's
 * pipeline as the cleave and the jab are.
 */
class SlamRulesTest {
    @Test void itsTimesAreTheVideos(){
        // what was read of the video and confirmed: the circle 0.35 s after the press, the leap at 1.1 s, the dive at
        // 3.1 s, the landing at 3.4 s, the circle gone at 4.55 s
        assertEquals(.05,SlamRules.LIT/20.0,1e-9);assertEquals(.35,SlamRules.CIRCLE/20.0,1e-9);assertEquals(1.1,SlamRules.LEAP/20.0,1e-9);
        assertEquals(3.1,SlamRules.DIVE/20.0,1e-9);assertEquals(3.4,SlamRules.LAND/20.0,1e-9);assertEquals(4.55,SlamRules.END/20.0,1e-9);
        assertTrue(SlamRules.LIT<SlamRules.CIRCLE&&SlamRules.CIRCLE<SlamRules.CROUCH&&SlamRules.CROUCH<SlamRules.LEAP&&SlamRules.LEAP<SlamRules.RISEN
            &&SlamRules.RISEN<=SlamRules.DIVE&&SlamRules.DIVE<SlamRules.LAND&&SlamRules.LAND<SlamRules.FREE&&SlamRules.FREE<SlamRules.END);
        assertTrue(SlamRules.AIM_FROM>SlamRules.LEAP&&SlamRules.AIM_FROM<SlamRules.DIVE,"where it comes down is told from up in the air, before the dive");
    }
    @Test void theUsersNumbers(){
        assertEquals(50,SlamRules.DAMAGE,0,"피해 50");assertEquals(1200,SlamRules.COOLDOWN,"60초");
        assertEquals(.45,SlamRules.STUN/20.0,1e-9,"the video's stun");
        assertEquals(1000+SlamRules.END+1200,SlamRules.ready(1000),"its cooldown runs from its end");
    }
    @Test void itIsASkillOfTheKnifesPipeline(){
        assertEquals(11,XCharaCombo.SLAM);assertTrue(XCharaCombo.valid(XCharaCombo.SLAM)&&XCharaCombo.skill(XCharaCombo.SLAM));
        assertEquals(1,XCharaCombo.blows(XCharaCombo.SLAM));assertEquals(SlamRules.LAND,XCharaCombo.blowAt(XCharaCombo.SLAM,0),"its one blow is its landing");
        assertEquals(50,XCharaCombo.damage(XCharaCombo.SLAM,0),0);assertEquals(SlamRules.FREE,XCharaCombo.HOLD[XCharaCombo.SLAM]);assertEquals(SlamRules.END,XCharaCombo.LENGTH[XCharaCombo.SLAM]);
        assertTrue(XCharaCombo.unblockable(XCharaCombo.SLAM),"가드 불가");assertFalse(XCharaCombo.launches(XCharaCombo.SLAM,0),"no knockback");
        assertEquals(SlamRules.COOLDOWN,XCharaCombo.cooldown(XCharaCombo.SLAM));
        assertFalse(XCharaCombo.single(XCharaCombo.SLAM),"everyone round where it lands");
        assertEquals(0,XCharaCombo.place(XCharaCombo.SLAM));assertEquals(-1,XCharaCombo.broken(XCharaCombo.SLAM,0,999),"it is no hit of the combo");
        // the other two are as they were
        assertEquals(24,XCharaCombo.blowAt(XCharaCombo.CLEAVE,0));assertEquals(14,XCharaCombo.blowAt(XCharaCombo.JAB,0));
    }
    @Test void theyRiseStraightUpSlowingAndAreHighestAsTheDiveBegins(){
        assertEquals(0,SlamRules.risen(0),0);assertEquals(0,SlamRules.risen(SlamRules.LEAP),0);assertEquals(1,SlamRules.risen(SlamRules.RISEN),1e-9);assertEquals(1,SlamRules.risen(SlamRules.DIVE),1e-9);
        double was=0,speed=Double.MAX_VALUE;
        for(double a=SlamRules.LEAP;a<=SlamRules.RISEN;a+=.25){
            double r=SlamRules.risen(a),v=r-was;assertTrue(v>=-1e-12,"they sink at "+a);
            if(a>SlamRules.LEAP+.25)assertTrue(v<=speed+1e-9,"they speed up at "+a);
            speed=v;was=r;
        }
        // half way up in the first quarter of the climb: off the ground at speed
        assertTrue(SlamRules.risen(SlamRules.LEAP+(SlamRules.RISEN-SlamRules.LEAP)*.25)>=.45);
        assertEquals(4,SlamRules.RISE,0,"the user's four blocks");
    }
    @Test void theBodysWayHasNoStepInIt(){
        double[] from={10,64,-5},land={16,66,3};double[] was=SlamRules.planned(0,from,SlamRules.RISE,land);
        assertArrayEquals(from,was,1e-12);
        for(double a=0;a<=SlamRules.END;a+=1/3.0){
            double[] p=SlamRules.planned(a,from,SlamRules.RISE,land);
            double step=Math.sqrt(Math.pow(p[0]-was[0],2)+Math.pow(p[1]-was[1],2)+Math.pow(p[2]-was[2],2));
            // a third of a tick is a frame at 60 a second: the dive at its fastest is under four blocks of it
            assertTrue(step<3.6,"a step of "+step+" at "+a);was=p;
        }
        // up over where they stood until the dive, where it came down from the landing on
        double[] top=SlamRules.planned(SlamRules.DIVE,from,SlamRules.RISE,land);
        assertEquals(from[0],top[0],1e-12);assertEquals(from[1]+SlamRules.RISE,top[1],1e-9);assertEquals(from[2],top[2],1e-12);
        assertArrayEquals(land,SlamRules.planned(SlamRules.LAND,from,SlamRules.RISE,land),1e-9);assertArrayEquals(land,SlamRules.planned(SlamRules.END,from,SlamRules.RISE,land),1e-9);
        // the dive gathers speed all the way down, and never goes back
        double d=0,v=0;
        for(double a=SlamRules.DIVE;a<=SlamRules.LAND;a+=.25){double n=SlamRules.dived(a);assertTrue(n>=d);if(a>SlamRules.DIVE+.25)assertTrue(n-d>=v-1e-9,"it slows at "+a);v=n-d;d=n;}
        assertEquals(1,SlamRules.dived(SlamRules.LAND),0);
        // not yet told where it comes down: they hang at the top
        assertArrayEquals(top,SlamRules.planned(SlamRules.DIVE+3,from,SlamRules.RISE,null),1e-9);
        // under a roof they rise as far as there is room
        assertEquals(from[1]+3,SlamRules.planned(SlamRules.DIVE,from,3,land)[1],1e-9);
    }
    @Test void itComesDownWithinTheCircle(){
        assertEquals(10,SlamRules.RADIUS,0);
        double[] in=SlamRules.within(0,0,5,-7,.5);assertEquals(5,in[0],0);assertEquals(-7,in[1],0);
        double[] out=SlamRules.within(2,3,2+300,3,.5);assertEquals(2+9.5,out[0],1e-9);assertEquals(3,out[1],1e-9);
        for(double a=0;a<7;a+=.3){double[] p=SlamRules.within(1,1,1+Math.cos(a)*90,1+Math.sin(a)*90,.5);assertEquals(9.5,Math.hypot(p[0]-1,p[1]-1),1e-9);}
        // nonsense is where they stood
        double[] bad=SlamRules.within(4,5,Double.NaN,Double.NaN,.5);assertEquals(4,bad[0],0);assertEquals(5,bad[1],0);
    }
    @Test void theLandingStrikesRoundWhereItCameDown(){
        assertTrue(SlamRules.struck(0,0,0,.3));assertTrue(SlamRules.struck(3.5,0,0,.3));assertTrue(SlamRules.struck(0,0,-3.7,.3));assertTrue(SlamRules.struck(2.5,1,2.5,.3));
        assertFalse(SlamRules.struck(3.9,0,0,.3));assertFalse(SlamRules.struck(2.8,0,2.8,.3));
        assertFalse(SlamRules.struck(0,3.5,0,.3),"not one high over it");assertFalse(SlamRules.struck(0,-2,0,.3),"nor one under the floor");
        // the pipeline asks it the same way whichever way the hit goes
        assertTrue(XCharaCombo.lane(XCharaCombo.SLAM,0,-3,-1,0,.3));assertFalse(XCharaCombo.lane(XCharaCombo.SLAM,0,4.2,0,0,.3));
        // far smaller than the circle: the circle is where it can come down, not whom it strikes
        assertTrue(SlamRules.BLAST*2<SlamRules.RADIUS);
    }
    @Test void nothingHarmsThemInTheAirAndABlowStopsItOnlyBeforeTheLeap(){
        assertFalse(SlamRules.airborne(SlamRules.LEAP-1));assertTrue(SlamRules.airborne(SlamRules.LEAP));assertTrue(SlamRules.airborne(SlamRules.LAND-1));assertFalse(SlamRules.airborne(SlamRules.LAND));
        assertTrue(SlamRules.charging(0)&&SlamRules.charging(SlamRules.LEAP-1));assertFalse(SlamRules.charging(SlamRules.LEAP));
    }
}
