package dev.sangwoo.celestialfall.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The danger banner's place (2.42.0): where the user's screenshot has it when there is room, and never over the HP art on
 * any screen, however narrow (the art then reaches the middle of the screen).
 */
class DangerBannerTest {
    /** The widest line's width at full size, as Botand Hud Serif sets "목숨이 위태롭습니다!" (about 9.1 ems). */
    private static float widest(int h){return 9.1f*DangerBanner.SIZE[1]*h;}

    @Test void whereTheScreenshotHasItWhenThereIsRoom(){
        // 1920×1080 at GUI scale 2 and 1280×730 at GUI scale 1: the art stays left of the banner
        for(int[] s:new int[][]{{960,540},{1280,730}}){
            var p=DangerBanner.place(s[0],s[1],widest(s[1]),true);
            assertEquals(1,p.scale(),1e-6);assertEquals(p.top(),p.at(),1e-6);
            assertEquals(.182f*s[1],p.middle(0,s[1]),1e-3,"the first line a fifth of the way down");
        }
        var free=DangerBanner.place(480,270,widest(270),false);
        assertEquals(free.top(),free.at(),1e-6,"without the art it is never moved");
    }
    @Test void neverOverTheHpArt(){
        int above=0,below=0;
        for(int w=320;w<=1700;w+=9)for(int h=240;h<=960;h+=13){
            if(w<h*1.1||w>h*2.6)continue;
            float wide=widest(h);var p=DangerBanner.place(w,h,wide,true);
            float half=DangerBanner.half(wide,h)*p.scale(),cx=w/2f;
            float top=p.at(),bottom=p.at()+(DangerBanner.bottom(h)-DangerBanner.top(h))*p.scale();
            assertTrue(top>=DangerBanner.MARGIN-1e-3&&bottom<=h,"on the screen at "+w+"x"+h);
            assertTrue(p.scale()>=DangerBanner.SMALLEST,"never made tiny at "+w+"x"+h);
            // the art: its portrait at the left from the top, its bars, bow and lantern from clearAbove, down to the money line
            double right=BotandHudLayout.right(w),portrait=BotandHudLayout.portraitRight(w),clear=BotandHudLayout.clearAbove(w),end=BotandHudLayout.bottom(w);
            boolean barsHit=cx+half>portrait&&cx-half<right&&bottom>clear&&top<end;
            boolean portraitHit=cx-half<portrait&&bottom>8&&top<end;
            assertFalse(barsHit||portraitHit,"over the HP art at "+w+"x"+h+": "+p);
            if(p.at()<p.top())above++;if(p.at()>p.top())below++;
        }
        assertTrue(above>0,"narrow screens put it above the art's bars");
    }
    @Test void aboveTheBarsOnTheUsualNarrowScreens(){
        // 1920×1080 at GUI scale 3 or 4, 1280×730 at 2: the art reaches the middle, there is room above its bars
        for(int[] s:new int[][]{{640,360},{480,270},{640,365}}){
            var p=DangerBanner.place(s[0],s[1],widest(s[1]),true);
            assertTrue(p.at()<p.top()&&p.scale()>.85f,"above the bars, nearly full size at "+s[0]+"x"+s[1]+": "+p);
        }
    }
}
