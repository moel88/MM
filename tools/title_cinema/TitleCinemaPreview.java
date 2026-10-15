package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.client.title.*;
import java.util.*;
import java.nio.file.*;
public final class TitleCinemaPreview {
 public static void main(String[] args)throws Exception{
  var shots=new LinkedHashMap<String,Object>();String[] names={"desktop","mobile","portrait","credits","leave","servers","motion_off"};
  int[][] sizes={{1536,864},{1280,720},{720,1280},{1536,864},{1536,864},{1536,864},{1536,864}};
  for(int n=0;n<names.length;n++){
   var l=TitleLayout.of(sizes[n][0],sizes[n][1]);var m=new TitleMotion();boolean animated=n!=6;
   for(int j=0;j<120;j++)m.step(1/60.0,animated,0,0,Set.of("play"));
   var s=TitleArt.scene(l,sizes[n][0],sizes[n][1],new TitleArt.State("BOMI",true,animated,n==3?"credits":n==4?"leave":n==5?"servers":"","",m));
   shots.put(names[n],Map.of("width",sizes[n][0],"height",sizes[n][1],"layout",l,"scene",s));
  }
  Files.writeString(Path.of("build/title-cinema/ui-scenes.json"),new com.google.gson.Gson().toJson(shots));
 }
}
