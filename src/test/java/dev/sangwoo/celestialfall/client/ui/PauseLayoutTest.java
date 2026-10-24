package dev.sangwoo.celestialfall.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The pause menu (2.45.0): four buttons under a header and the reset question, all on the screen, at any size. */
class PauseLayoutTest {
    private static final int[][] SCREENS={{640,365},{427,240},{960,540},{320,180},{854,480},{240,400}};

    @Test void theHeaderAndFourButtonsStackInsideTheScreen(){
        for(int[] s:SCREENS){
            int w=s[0],h=s[1];var menu=PauseLayout.menu(w,h,4);
            var head=menu.header();
            assertTrue(inside(head,w,h),"header on "+w+"x"+h);
            double y=head.bottom();
            for(var b:menu.buttons()){
                assertTrue(inside(b,w,h),"button on "+w+"x"+h);
                assertTrue(b.y()>=y,"each below the one before");
                assertEquals(head.x(),b.x(),1e-9);assertEquals(head.w(),b.w(),1e-9);
                y=b.bottom();
            }
        }
    }
    @Test void theMouseFindsEachButtonAndNothingBetween(){
        var menu=PauseLayout.menu(640,365,4);
        for(int i=0;i<4;i++){var b=menu.buttons().get(i);assertEquals(i,menu.at(b.x()+b.w()/2,b.y()+b.h()/2));}
        var a=menu.buttons().get(0);var b=menu.buttons().get(1);
        assertEquals(-1,menu.at(a.x()+5,(a.bottom()+b.y())/2),"the gap");
        assertEquals(-1,menu.at(a.x()-1,a.y()+2),"left of it");
        assertEquals(-1,menu.at(10,10));
    }
    @Test void theQuestionSitsInTheMiddleWithResetLeftAndCancelRight(){
        for(int[] s:SCREENS){
            int w=s[0],h=s[1];var d=PauseLayout.dialog(w,h);
            assertTrue(inside(d.box(),w,h),"box on "+w+"x"+h);
            assertEquals(w/2.,d.box().x()+d.box().w()/2,1e-9);assertEquals(h/2.,d.box().y()+d.box().h()/2,1e-9);
            assertTrue(within(d.confirm(),d.box())&&within(d.cancel(),d.box()));
            assertTrue(d.confirm().x()+d.confirm().w()<d.cancel().x(),"reset on the left, cancel on the right");
            assertEquals(d.confirm().w(),d.cancel().w(),1e-9);
            assertEquals(0,d.at(d.confirm().x()+1,d.confirm().y()+1));
            assertEquals(1,d.at(d.cancel().x()+1,d.cancel().y()+1));
            assertEquals(-1,d.at(d.box().x()+2,d.box().y()+2));
        }
    }
    private static boolean inside(PauseLayout.Box b,int w,int h){return b.x()>=0&&b.y()>=0&&b.x()+b.w()<=w&&b.bottom()<=h;}
    private static boolean within(PauseLayout.Box b,PauseLayout.Box o){return b.x()>=o.x()&&b.y()>=o.y()&&b.x()+b.w()<=o.x()+o.w()&&b.bottom()<=o.bottom();}
}
