package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.progression.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
public final class BotandHudPreview {
 public static void main(String[] args)throws Exception {
  Path out=Path.of("build/botand-hud");Files.createDirectories(out);
  var list=new ArrayList<Object>();
  list.add(Map.of("caption","NEW PLAYER  /  HP 200  /  GUARD 100", "frame",BotandHudLayout.frame(200,200,100,ProgressProfile.NEW,false)));
  list.add(Map.of("caption","IN COMBAT  /  HP 74  /  GUARD 25", "frame",BotandHudLayout.frame(74,200,25,new ProgressProfile(23,5,2,"kageno"),false)));
  list.add(Map.of("caption","LEVEL 50 [MAX]  /  REBIRTH READY", "frame",BotandHudLayout.frame(200,200,100,new ProgressProfile(50,5,2,"kageno"),false)));
  list.add(Map.of("caption","AFTER REBIRTH  /  LEVEL 1  /  REBIRTH 6", "frame",BotandHudLayout.frame(200,200,100,new ProgressProfile(1,6,2,"kageno"),false)));
  list.add(Map.of("caption","GUARD BREAK  /  FIFTEEN REBIRTHS", "frame",BotandHudLayout.frame(38,200,0,new ProgressProfile(1,15,2,"kageno"),true)));
  list.add(Map.of("caption","AFTER TRANSCENDENCE  /  0 REBIRTHS  /  3", "frame",BotandHudLayout.frame(200,200,100,new ProgressProfile(1,0,3,"kageno"),false)));
  Files.writeString(out.resolve("layout.json"),new GsonBuilder().setPrettyPrinting().create().toJson(list));
 }
}
