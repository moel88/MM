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
                int boxes=l.rows()+(l.more()?1:0);
                double bottom=l.rowTop(boxes-1)+l.row();
                assertTrue(bottom<=h*(PlayerPanel.MARGIN+PlayerPanel.TALL)+1e-9,"not taller than its share: "+w+"x"+h+" "+players);
                assertEquals(players>l.rows(),l.more(),"the rest are counted in a last row");
                assertTrue(l.rows()>=1);
            }
        }
        var wide=PlayerPanel.layout(640,365,3,1000);
        assertEquals(640*.45,wide.width(),1e-9,"a very long name cannot make it wider than 45% of the screen");
    }
}
