package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.client.title.*;
import dev.sangwoo.celestialfall.client.menu.MenuLayout.Rect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TitleTest {
    @Test void allHitTargetsFitAndDoNotOverlapAcrossMobileDesktopAndPortrait(){
        for(int[] size:new int[][]{{320,180},{427,240},{780,360},{960,540},{480,270},{360,640},{320,480},{240,320},{1920,800}}){
            var l=TitleLayout.of(size[0],size[1]);var actions=l.actions();assertEquals(3,actions.size());
            for(var a:actions){Rect r=l.screen(a.rect());assertTrue(r.x()>=0&&r.y()>=0);assertTrue(r.x()+r.w()<=size[0]+.1);assertTrue(r.y()+r.h()<=size[1]+.1);assertTrue(r.h()>=19.9);
                for(var b:actions)if(a!=b){Rect o=l.screen(b.rect());assertFalse(r.x()<o.x()+o.w()&&r.x()+r.w()>o.x()&&r.y()<o.y()+o.h()&&r.y()+r.h()>o.y(),a.id()+" vs "+b.id());}
            }
            for(String modal:List.of("credits","leave","servers"))for(var a:l.modal(modal)){var r=l.screen(a.rect());assertTrue(r.x()>=0&&r.y()>=0&&r.x()+r.w()<=size[0]&&r.y()+r.h()<=size[1]);}
        }
    }
    @Test void normalAndReducedMotionNeverChangeHitAreas(){var a=TitleLayout.of(780,360);var m=new TitleMotion();for(int i=0;i<120;i++)m.step(1/60.0,true,1,-1,Set.of("play"));assertEquals(a.actions(),TitleLayout.of(780,360).actions());assertEquals(0,m.time(false));assertEquals(0,m.mx(false));assertEquals(0,m.my(false));assertEquals(1,m.intro(false));}
    @Test void menuLeavesTheWorldVisibleAndUsesOnlyATransparentLogo(){
        for(int[] size:new int[][]{{960,540},{2080,540},{720,1100},{320,180}}){
            var l=TitleLayout.of(size[0],size[1]);
            var scene=TitleArt.scene(l,size[0],size[1],new TitleArt.State("test",true,true,"","",new TitleMotion()));
            var pictures=scene.commands.stream().filter(c->c.kind().equals("image")).toList();
            assertEquals(1,pictures.size());assertEquals("logo",pictures.get(0).asset());
            assertTrue(l.logo().w()<l.width()/1.2);
            var r=l.screen(l.logo());assertTrue(r.x()>=0&&r.y()>=0&&r.x()+r.w()<=size[0]&&r.y()+r.h()<=size[1],"Logo must not be cover-cropped");
            for(var c:scene.commands)if(c.kind().equals("shape"))assertTrue((c.top()>>>24)<200,"No opaque background rectangle");
        }
    }
    @Test void transitionsAreSingleShotBoundedAndFrameRateIndependent(){var a=new TitleMotion();var b=new TitleMotion();for(int i=0;i<60;i++)a.step(1/60.0,true,1,-1,Set.of("play"));for(int i=0;i<30;i++)b.step(1/30.0,true,1,-1,Set.of("play"));assertEquals(a.hover("play"),b.hover("play"),1e-8);assertTrue(a.startExit());assertFalse(a.startExit());assertFalse(a.exitReady(true));assertTrue(a.exitReady(false));for(int i=0;i<20;i++)a.step(.01,true,0,0,Set.of());assertTrue(a.exitReady(true));assertEquals(1,a.exitAlpha(true));a.reset();assertFalse(a.leaving());}
    @Test void allDrawCommandsAreFiniteAndMissingMultiplayerHasHonestCopy(){
        var l=TitleLayout.of(427,240);var m=new TitleMotion();m.step(.05,true,1,1,Set.of("servers"));var s=TitleArt.scene(l,427,240,new TitleArt.State("SO DI",false,true,"credits","",m));
        assertTrue(s.commands.stream().anyMatch(c->c.text().contains("현재 계정")));
        for(var c:s.commands)for(double v:c.points())assertTrue(Double.isFinite(v));
        assertEquals(1,s.commands.stream().filter(c->c.kind().equals("image")).count());
    }
    @Test void titlePreferencesMergeUnknownFieldsAndNeverTouchWorldGraphics(@TempDir Path dir)throws Exception{
        var world=dir.resolve("celestialfall.json");Files.writeString(world,"{\"postProcessing\":false,\"skyEffect\":false}");var p=dir.resolve("botand-title.json");Files.writeString(p,"{\"custom\":123,\"motion\":true}");TitlePreferences.write(p,false);
        var json=com.google.gson.JsonParser.parseString(Files.readString(p)).getAsJsonObject();assertFalse(json.get("motion").getAsBoolean());assertEquals(123,json.get("custom").getAsInt());assertEquals("{\"postProcessing\":false,\"skyEffect\":false}",Files.readString(world));
        Files.writeString(p,"broken{");assertThrows(RuntimeException.class,()->TitlePreferences.write(p,true));assertEquals("broken{",Files.readString(p));
    }
}
