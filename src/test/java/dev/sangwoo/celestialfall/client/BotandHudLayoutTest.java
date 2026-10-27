package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.progression.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BotandHudLayoutTest {
    @Test void barsUseRealHealthRemainingGuardAndLiveProgressLabels() {
        var f=BotandHudLayout.frame(74,200,25,new ProgressProfile(50,5,2,"kageno"),false);
        assertEquals(java.util.List.of("74","25","50","[MAX]","5","2"),f.labels().stream().map(BotandHudLayout.Label::text).toList());
        assertEquals(100,BotandHudLayout.guard(0,false));assertEquals(75,BotandHudLayout.guard(25,false));
        assertEquals(0,BotandHudLayout.guard(100,true));
        for(var q:f.quads())for(double p:q.xy())assertTrue(Double.isFinite(p)&&p>=0&&p<=2048);
    }
    @Test void emptyBarsAndSmallScreensStayBounded() {
        for(int width:new int[]{320,390,720,960,1920})assertTrue(BotandHudLayout.width(width)>0&&BotandHudLayout.width(width)<=width-16);
        // 2.73.0 (user request: "HP창 UI 사이즈를 줄이고"): seven tenths of its old size (44% of the width, 240 to 360)
        assertEquals(168,BotandHudLayout.width(480),1e-9);assertEquals(.44*.7*640,BotandHudLayout.width(640),1e-9);assertEquals(252,BotandHudLayout.width(960),1e-9);
        var empty=BotandHudLayout.frame(0,200,0,ProgressProfile.NEW,true);
        assertEquals("0",empty.labels().get(0).text());assertEquals("0",empty.labels().get(1).text());
        assertTrue(empty.labels().stream().anyMatch(x->x.text().equals("BREAK")));
        var full=BotandHudLayout.frame(200,200,100,ProgressProfile.NEW,false);
        assertTrue(full.quads().size()>empty.quads().size());
    }
}
