package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.xchara.XSlashRules;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's H with their hack off, the great X slash (2.134.0, user request: "X 대참격으로 날리고 맞은 사람은 글리치에
 * 걸리고 가드 못 하게 엑스차라가 가드에 너무 취약하니까 이런거 하나 넣어주면 좋을듯"; their choices "40+15 · 50초",
 * "가드를 뚫고 맞음", "8초 · 가드만 못 함"): a skill of the knife's pipeline whose one blow lets the X go; it flies level
 * the way they faced, growing, striking everyone it goes over once (no guard takes it) and glitching them; the ground it
 * went over keeps an X (its way's diagonals) that bursts a second after it is gone on whoever stands on it.
 */
class XSlashRulesTest {
    @Test void itIsOneSkillOfTheKnifesPipelineItsBlowTheXLetGo(){
        assertEquals(17,XSLASH);assertEquals(XSLASH,LAST);
        assertTrue(valid(XSLASH)&&skill(XSLASH)&&!valid(XSLASH+1));
        assertFalse(erase(XSLASH)||cutter(XSLASH)||single(XSLASH)||plain(XSLASH));
        assertEquals(0,place(XSLASH),"no hit of the combo");
        assertEquals(1,blows(XSLASH),"its one blow lets the X go");assertEquals(XSlashRules.FIRE,blowAt(XSLASH,0));
        assertEquals(0,damage(XSLASH,0),1e-9,"the blow itself strikes no one: its X does (XCharaXSlash)");
        assertEquals(XSlashRules.HOLD,HOLD[XSLASH]);assertEquals(XSlashRules.LENGTH,LENGTH[XSLASH]);
        assertTrue(unblockable(XSLASH),"no guard takes it (the user's \"가드를 뚫고 맞음\")");assertFalse(launches(XSLASH,0),"no knockback");
        assertEquals(40,XSlashRules.DAMAGE,1e-9);assertEquals(15,XSlashRules.BURST_DAMAGE,1e-9,"the user's 40+15");
        assertEquals(160,XSlashRules.SEAL,"the user's 8 s with no guard");
        assertEquals(1000,XSlashRules.COOLDOWN,"the user's 50 s");assertEquals(1000,cooldown(XSLASH));
        assertEquals(500+XSlashRules.LENGTH+1000,XSlashRules.ready(500),"its cooldown from its end");
    }
    @Test void itsMomentsComeInTurn(){
        assertTrue(0<XSlashRules.LIT&&XSlashRules.LIT<XSlashRules.CUT1&&XSlashRules.CUT1<XSlashRules.CUT2&&XSlashRules.CUT2<XSlashRules.FIRE);
        assertTrue(XSlashRules.FIRE<XSlashRules.HOLD&&XSlashRules.HOLD<=XSlashRules.LENGTH);
        assertTrue(XSlashRules.FIRE-XSlashRules.CUT2<=2,"thrown as the second cut is done");
        assertEquals(20,XSlashRules.WAIT,"the user's \"1초 뒤\"");
    }
    @Test void theXFliesLevelAndGrowsAsItGoes(){
        assertEquals(XSlashRules.NEAR,XSlashRules.head(0),1e-9);assertEquals(XSlashRules.NEAR,XSlashRules.head(-3),1e-9,"where it stands before it goes");
        assertEquals(XSlashRules.NEAR+XSlashRules.SPEED*3,XSlashRules.head(3),1e-9);
        assertEquals((XSlashRules.RANGE-XSlashRules.NEAR)/XSlashRules.SPEED,XSlashRules.flight(XSlashRules.RANGE),1e-9);
        assertEquals(XSlashRules.flight(XSlashRules.RANGE),XSlashRules.flight(99),1e-9,"no further than its range");
        for(double fw=XSlashRules.NEAR;fw<XSlashRules.RANGE;fw+=.25)assertEquals(fw,XSlashRules.head(XSlashRules.reached(fw)),1e-9);
        // growing: from its size where it was cut to its full size, never shrinking, over GROW blocks
        assertEquals(XSlashRules.HALF0,XSlashRules.half(XSlashRules.NEAR),1e-9);assertEquals(XSlashRules.HALF,XSlashRules.half(XSlashRules.NEAR+XSlashRules.GROW),1e-9);
        assertEquals(XSlashRules.HALF,XSlashRules.half(XSlashRules.RANGE),1e-9);
        assertEquals(XSlashRules.TALL0,XSlashRules.tall(XSlashRules.NEAR),1e-9);assertEquals(XSlashRules.TALL,XSlashRules.tall(XSlashRules.RANGE),1e-9);
        double last=0,lastTall=0;
        for(double fw=0;fw<=XSlashRules.RANGE;fw+=.05){double h=XSlashRules.half(fw),t=XSlashRules.tall(fw);assertTrue(h>=last-1e-12&&t>=lastTall-1e-12);last=h;lastTall=t;}
        assertEquals(10,2*XSlashRules.HALF,1e-9,"about ten blocks wide (what I recommended)");
        // looked over a tick ahead of where it is drawn, with no gap between ticks, from its user's feet as it goes
        assertEquals(0,XSlashRules.swept(0)[0],1e-9);
        for(int n=0;n<12;n++){
            double[] s=XSlashRules.swept(n);assertEquals(XSlashRules.head(n+1),s[1],1e-9,"a tick ahead");
            if(n>0)assertEquals(XSlashRules.swept(n-1)[1],s[0],1e-9,"no gap");
        }
    }
    @Test void whomItGoesOver(){
        // its way between 7 and 9 blocks ahead, grown there to its full width
        assertTrue(XSlashRules.NEAR+XSlashRules.GROW<7);
        assertTrue(XSlashRules.inWay(8,0,0,.3,7,9),"in the middle");
        assertTrue(XSlashRules.inWay(8,XSlashRules.HALF+.25,0,.3,7,9),"at its edge, half in");
        assertFalse(XSlashRules.inWay(8,XSlashRules.HALF+.4,0,.3,7,9),"beside it");
        assertFalse(XSlashRules.inWay(10,0,0,.3,7,9),"not yet come to");assertTrue(XSlashRules.inWay(9.25,0,0,.3,7,9),"half in ahead");
        assertTrue(XSlashRules.inWay(8,0,XSlashRules.HIGH-.01,.3,7,9),"a jump high");assertFalse(XSlashRules.inWay(8,0,XSlashRules.HIGH+.1,.3,7,9),"over it");
        assertFalse(XSlashRules.inWay(8,0,XSlashRules.LOW-.1,.3,7,9),"under it");
        // narrower near them, where it was cut
        assertTrue(XSlashRules.inWay(XSlashRules.NEAR,XSlashRules.HALF0,0,.3,0,XSlashRules.NEAR+.1));
        assertFalse(XSlashRules.inWay(XSlashRules.NEAR,XSlashRules.HALF0+1,0,.3,0,XSlashRules.NEAR+.1));
    }
    @Test void theXOnTheGroundIsItsWaysTwoDiagonals(){
        double reach=XSlashRules.RANGE;double[][] d=XSlashRules.diagonals(reach);
        assertArrayEquals(new double[]{XSlashRules.NEAR,-XSlashRules.HALF,reach,XSlashRules.HALF},d[0],1e-9,"near on the left to far on the right");
        assertArrayEquals(new double[]{XSlashRules.NEAR,XSlashRules.HALF,reach,-XSlashRules.HALF},d[1],1e-9,"near on the right to far on the left");
        double mid=(XSlashRules.NEAR+reach)/2;
        assertEquals(0,XSlashRules.fromMark(mid,0,reach),1e-9,"where they cross");
        assertEquals(0,XSlashRules.fromMark(XSlashRules.NEAR,XSlashRules.HALF,reach),1e-9,"its corners");
        assertTrue(XSlashRules.onMark(mid,0,0,.3,reach));
        assertTrue(XSlashRules.onMark(mid,XSlashRules.BAND,0,.3,reach),"within its band");
        assertFalse(XSlashRules.onMark(XSlashRules.NEAR+.5,0,0,.3,reach),"in the gap of the X near them");
        assertFalse(XSlashRules.onMark(mid,0,XSlashRules.MARK_HIGH+.2,.3,reach),"high over it");
        assertFalse(XSlashRules.onMark(reach+XSlashRules.BAND+1,XSlashRules.HALF,0,.3,reach),"past its end");
        // a wall stopped it short: its X as far as it went
        double[][] short_=XSlashRules.diagonals(6);assertEquals(6,short_[0][2],1e-9);
        assertFalse(XSlashRules.onMark(12,0,0,.3,6),"none past where it stopped");
        // drawn on the ground as the X goes over it, whole once it is as far as it went
        double last=-1;
        for(double head=0;head<=reach+1;head+=.25){double k=XSlashRules.drawn(d[0],head);assertTrue(k>=last-1e-12&&k>=0&&k<=1);last=k;}
        assertEquals(0,XSlashRules.drawn(d[0],XSlashRules.NEAR),1e-9);assertEquals(1,XSlashRules.drawn(d[0],reach),1e-9);
        // it bursts a second after the X is gone as far as it went
        assertEquals(XSlashRules.FIRE+(int)Math.ceil(XSlashRules.flight(reach))+XSlashRules.WAIT,XSlashRules.burstAt(reach));
        assertTrue(XSlashRules.burstAt(6)<XSlashRules.burstAt(reach),"sooner for one a wall stopped");
        assertTrue(XSlashRules.burstAt(reach)-(XSlashRules.FIRE+XSlashRules.flight(reach))>=XSlashRules.WAIT,"a full second after it is whole");
    }
}
