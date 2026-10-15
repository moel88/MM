package dev.sangwoo.celestialfall;
import dev.sangwoo.celestialfall.menu.*;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.client.menu.*;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MenuTest {
    @Test void firstEntryHasOnlyHamburgerAndExpandedHasExactlyTwoDisabledItems(){for(int[] size:new int[][]{{320,180},{427,240},{854,480},{1920,1080}}){var closed=MenuLayout.dock(size[0],size[1],false);assertEquals(1,closed.size());assertEquals("menu",closed.get(0).id());var expanded=MenuLayout.dock(size[0],size[1],true);assertEquals(7,expanded.size());assertEquals(closed.get(0).rect(),expanded.get(0).rect());assertEquals(Set.of("draw","bag"),new HashSet<>(expanded.stream().filter(b->!b.enabled()).map(MenuLayout.Dock::id).toList()));for(var b:expanded){assertTrue(b.rect().x()>=0&&b.rect().y()>=0);assertTrue(b.rect().x()+b.rect().w()<=size[0]);assertTrue(b.rect().y()+b.rect().h()<=size[1]);for(var other:expanded)if(other!=b)assertFalse(b.rect().contains(other.rect().x()+other.rect().w()/2,other.rect().y()+other.rect().h()/2));}}}
    @Test void responsivePanelStaysInsideLandscapeAndPortraitViewports(){for(int[] size:new int[][]{{320,180},{427,240},{854,480},{480,854}}){var v=MenuLayout.view(size[0],size[1]);assertTrue(v.x()>=7.9&&v.y()>=7.9);assertTrue(v.px(900)<=size[0]-7.9);assertTrue(v.py(500)<=size[1]-7.9);}}
    @Test void earnedTitlesSurviveBothProgressResets(){var s=new MenuProgress(0,-1);assertFalse(s.earned(0));s=s.observe(new ProgressProfile(50,0,0,"none"));assertTrue(s.earned(0));s=s.equip(0).observe(new ProgressProfile(1,1,0,"none"));assertTrue(s.earned(0)&&s.earned(1));assertEquals(0,s.equipped());s=s.observe(new ProgressProfile(1,15,0,"none")).observe(new ProgressProfile(1,0,1,"none"));assertEquals(15,s.unlocked());}
    @Test void unknownAndLockedTitleIdsCannotBeEquipped(){var s=new MenuProgress(1,-1);assertEquals(-1,s.equip(3).equipped());assertEquals(-1,s.equip(100).equipped());assertEquals(-1,new MenuProgress(1,2).equipped());assertEquals(-1,s.equip(0).equip(-1).equipped());}
    @Test void titleStateIsPerPlayerAndPersists(){var s=new MenuState();var id=UUID.randomUUID();s.set(id,new MenuProgress(7,2));var copy=MenuState.read(s.writeNbt(new NbtCompound()));assertEquals(new MenuProgress(7,2),copy.get(id));assertEquals(new MenuProgress(0,-1),copy.get(UUID.randomUUID()));}
    @Test void soundSaveKeepsMobileRendererAndUnknownSettings(@TempDir Path directory)throws Exception{Path p=directory.resolve("celestialfall.json");Files.writeString(p,"{\"postProcessing\":false,\"skyEffect\":false,\"customSetting\":123,\"effectVolume\":0.8}");MenuSettings.writeEffectVolume(p,.25);var o=com.google.gson.JsonParser.parseString(Files.readString(p)).getAsJsonObject();assertFalse(o.get("postProcessing").getAsBoolean());assertFalse(o.get("skyEffect").getAsBoolean());assertEquals(123,o.get("customSetting").getAsInt());assertEquals(.25,o.get("effectVolume").getAsDouble());}
    @Test void invalidSoundConfigIsNotDestroyed(@TempDir Path directory)throws Exception{Path p=directory.resolve("celestialfall.json");Files.writeString(p,"not-json{");assertThrows(RuntimeException.class,()->MenuSettings.writeEffectVolume(p,.25));assertEquals("not-json{",Files.readString(p));}
}
