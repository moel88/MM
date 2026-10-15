package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.client.title.*;
import java.util.*;
import java.nio.file.*;
public final class TitlePreview {
    public static void main(String[] args)throws Exception{
        var shots=new LinkedHashMap<String,Object>();
        String[] names={"desktop","mobile","portrait","credits","leave","servers","motion_off"};
        int[][] sizes={{960,443},{780,360},{360,640},{960,540},{960,540},{960,540},{960,540}};
        for(int n=0;n<names.length;n++){
            var l=TitleLayout.of(sizes[n][0],sizes[n][1]);var m=new TitleMotion();boolean animated=n!=6;
            for(int j=0;j<120;j++)m.step(1/60.0,animated,.3,-.2,Set.of(n==0?"play":""));
            var s=TitleArt.scene(l,sizes[n][0],sizes[n][1],new TitleArt.State("SO DI",true,animated,n==3?"credits":n==4?"leave":n==5?"servers":"","",m));
            shots.put(names[n],Map.of("width",sizes[n][0],"height",sizes[n][1],"layout",l,"scene",s));
        }
        Path out=Path.of("build/botand-title");Files.createDirectories(out);Files.writeString(out.resolve("scenes.json"),new com.google.gson.GsonBuilder().create().toJson(shots));
    }
}
