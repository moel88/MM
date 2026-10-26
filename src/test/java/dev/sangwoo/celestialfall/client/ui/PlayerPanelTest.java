package dev.sangwoo.celestialfall.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The player list (2.45.0): shown at the top right on joining, Tab slides it off to the right and Tab again brings it
 * back, from wherever it is, with nothing jumping from one 60 fps frame to the next; and it fits the screen.
 */
class PlayerPanelTest {
    private static final double FRAME=20/60.;

    @Test void itShowsOnJoiningAndStaysUntilTab(){
        var m=new PlayerPanelMotion();m.reset();
        for(double t=0;t<400;t+=7.3)assertEquals(0,m.progress(t),1e-12);
        assertFalse(m.hidden());
    }
    @Test void tabSlidesItOutInASteadyTimeAndTabAgainBringsItBack(){
        var m=new PlayerPanelMotion();m.reset();
        m.toggle(100);
        assertTrue(m.hidden());
        assertEquals(0,m.progress(100),1e-12);
        assertEquals(.5,m.progress(100+PlayerPanelMotion.SLIDE/2),1e-12);
        assertEquals(1,m.progress(100+PlayerPanelMotion.SLIDE),1e-12);
        assertEquals(1,m.progress(500),1e-12,"gone, it stays gone");
        m.toggle(500);
        assertEquals(1,m.progress(500),1e-12);
        assertEquals(0,m.progress(500+PlayerPanelMotion.SLIDE),1e-12);
        assertEquals(0,m.progress(900),1e-12);
    }
    @Test void tabHalfwayTurnsItRoundFromWhereItIs(){
        var m=new PlayerPanelMotion();m.reset();
        m.toggle(10);double at=10+PlayerPanelMotion.SLIDE*.3,before=m.progress(at);
        m.toggle(at);
        assertEquals(before,m.progress(at),1e-12,"no jump when it turns");
        assertTrue(m.progress(at+1)<before,"and it comes back");
        assertEquals(0,m.progress(at+PlayerPanelMotion.SLIDE*.3),1e-9,"in the time it had gone out");
    }
    @Test void theHeaderLeavesFirstAndTheLastRowComesBackFirst(){
        int n=6;
        double p=.3;
        for(int i=1;i<n;i++)assertTrue(PlayerPanelMotion.box(p,i-1,n)>=PlayerPanelMotion.box(p,i,n),"box "+i);
        assertEquals(0,PlayerPanelMotion.box(0,0,n),1e-12);
        for(int i=0;i<n;i++)assertEquals(1,PlayerPanelMotion.box(1,i,n),1e-12,"every box is out at the end");
    }
    @Test void noBoxJumpsBetweenFramesThroughTabsAtAnyMoment(){
        var m=new PlayerPanelMotion();m.reset();
        int n=9;
        // Tab pressed at awkward times: mid-slide, just after a turn, after it settled
        double[] tabs={20.4,24.1,25.0,31.7,60.2,63.9,64.2,90};
        int next=0;double[] last=new double[n];
        double maxStep=1.5*(1+PlayerPanelMotion.STAGGER*(n-1))/PlayerPanelMotion.SLIDE*FRAME+1e-9;
        for(double t=0;t<140;t+=FRAME){
            while(next<tabs.length&&tabs[next]<=t)m.toggle(tabs[next++]);
            double p=m.progress(t);
            assertTrue(p>=0&&p<=1);
            for(int i=0;i<n;i++){
                double out=PlayerPanelMotion.box(p,i,n);
                if(t>0)assertTrue(Math.abs(out-last[i])<=maxStep,"box "+i+" jumps at "+t+": "+last[i]+" -> "+out);
                last[i]=out;
            }
        }
    }
    @Test void tabTurnsItOncePerPressEvenWhenHeld(){
        // ticks of (down, presses since the last tick): pressed and held with the key's repeats, released, tapped within a tick
        boolean[] down={true,true,true,true,false,false,false,true,false};
        int[] presses={1,0,3,4,0,0,1,1,0};
        boolean held=false;int turns=0;
        for(int i=0;i<down.length;i++){if(PlayerPanel.turns(held,down[i],presses[i]))turns++;held=down[i];}
        assertEquals(3,turns,"the press, the tap and the next press; the repeats and the releases do nothing");
    }
    @Test void itSitsAtTheTopRightAndFitsTheScreen(){
        int[][] screens={{640,365},{427,240},{960,540},{320,180},{854,480}};
        for(int[] s:screens){
            int w=s[0],h=s[1];
            for(int players:new int[]{1,4,12,40}){
                var l=PlayerPanel.layout(w,h,players,60);
                double margin=PlayerPanel.MARGIN*h;
                assertEquals(w-margin,l.x()+l.width(),1e-9,"right edge");
                assertEquals(margin,l.y(),1e-9,"top edge");
                assertTrue(l.x()>w*.5,"it keeps to the right half");
                assertTrue(l.bottom()<=h*(PlayerPanel.MARGIN+PlayerPanel.TALL)+1e-9,"not taller than its share: "+w+"x"+h+" "+players);
                assertTrue(l.rows()>=1&&l.rows()<=PlayerPanelScroll.SHOWN,"five rows at most (2.73.0)");
                assertEquals(Math.min(players,PlayerPanelScroll.SHOWN),l.rows(),"as many as there are, up to five, on these screens");
                assertEquals(players>l.rows(),l.scrolls(),"the rest are scrolled to");
            }
        }
        var wide=PlayerPanel.layout(640,365,3,1000);
        assertEquals(640*.45,wide.width(),1e-9,"a very long name cannot make it wider than 45% of the screen");
        // 2.73.0: wider than it is high, row by row ("가로폭은 너무 작고 세로 폭이 너무 커")
        var l=PlayerPanel.layout(960,540,3,40);
        assertTrue(l.width()/l.row()>10,"a row is over ten times as wide as it is high");
    }

    // 2.73.0 (user request: "최대 5명만 보이고 나머지는 스크롤 내려야 보이게")
    @Test void theWheelScrollsARowANotchOnlyWhenThereAreMoreThanFive(){
        int shown=PlayerPanelScroll.SHOWN;var s=new PlayerPanelScroll();
        assertFalse(s.wheel(-1,5,shown,0),"five or fewer: the wheel is the camera's");
        assertFalse(s.wheel(-1,1,shown,0));
        assertTrue(s.wheel(-1,8,shown,10),"down a notch");assertEquals(1,s.target());
        assertTrue(s.wheel(-2,8,shown,20));assertEquals(3,s.target(),"two notches, two rows: the last three show");
        assertTrue(s.wheel(-1,8,shown,30),"at the end it still takes the wheel (no zooming under the list)");assertEquals(3,s.target(),"never past the last row");
        assertTrue(s.wheel(5,8,shown,40));assertEquals(0,s.target(),"never above the first");
        // a trackpad's fractions add up to rows
        assertTrue(s.wheel(-.4,8,shown,50));assertEquals(0,s.target());
        assertTrue(s.wheel(-.4,8,shown,51));assertEquals(0,s.target());
        assertTrue(s.wheel(-.4,8,shown,52));assertEquals(1,s.target());
    }
    @Test void itEasesToTheRowWithoutJumpsAndKeepsInTheListAsPlayersLeave(){
        int shown=PlayerPanelScroll.SHOWN;var s=new PlayerPanelScroll();
        // notches at awkward times: one after another before the last has eased
        double[] notches={10,10.4,11.1,30,30.5,31,31.2};double[] dir={-1,-1,-1,1,-1,1,1};
        int next=0;double last=s.first(0),most=0;
        double step=1.5*3/PlayerPanelScroll.TICKS*FRAME+1e-9;
        for(double t=0;t<60;t+=FRAME){
            while(next<notches.length&&notches[next]<=t){s.wheel(dir[next],12,shown,notches[next]);next++;}
            double f=s.first(t);
            assertTrue(Math.abs(f-last)<=step,"no jump at "+t+": "+last+" -> "+f);
            assertTrue(f>=-1e-9&&f<=PlayerPanelScroll.most(12,shown)+1e-9);
            most=Math.max(most,f);last=f;
        }
        assertEquals(s.target(),s.first(60),1e-9,"it settles on the row");
        assertTrue(most>2.5,"it went down");
        // players leave: it keeps within the list, easing back
        s.wheel(-9,12,shown,100);assertEquals(7,s.target());
        s.fit(9,shown,200);assertEquals(4,s.target());
        assertEquals(7,s.first(200),1e-9,"from where it was");
        assertEquals(4,s.first(200+PlayerPanelScroll.TICKS),1e-9);
        s.fit(3,shown,300);assertEquals(0,s.target(),"five or fewer: back to the top");
    }
}
