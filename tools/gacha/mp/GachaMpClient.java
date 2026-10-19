package dev.sangwoo.gachamp;

import dev.sangwoo.celestialfall.client.ProgressClient;
import dev.sangwoo.celestialfall.client.gacha.GachaClient;
import dev.sangwoo.celestialfall.client.gacha.GachaScreen;
import dev.sangwoo.celestialfall.gacha.GachaServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;
import java.util.*;

/**
 * Multiplayer check of the gacha (2.39.0), client half (only with -Dbotand.mp.role=puller|other). The puller opens the
 * gacha and presses its real buttons: one pull (an SSR, equipping Kageno), a pull refused while Kageno is on, a ten-pull,
 * then two pull requests sent in one tick. The other player, Kageno equipped, opens the gacha and asks for a pull, which
 * the server refuses. Both log what their client was told and save frames.
 */
public final class GachaMpClient implements ClientModInitializer {
    private static final String ROLE=System.getProperty("botand.mp.role");
    private static final Path OUT=Path.of(System.getProperty("botand.mp.out","mp"));
    private static final String ADDRESS=System.getProperty("botand.mp.address","127.0.0.1:25565");
    private MinecraftClient mc;private int phase,age,step,frames,since;private boolean shown;

    @Override public void onInitializeClient(){
        if(ROLE==null)return;
        ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{tick();}catch(Throwable t){fail(t);}});
    }
    private Path dir(){return OUT.resolve(ROLE);}
    private void note(String line){
        String entry=(mc.world==null?-1:mc.world.getTime())+" ["+age+"] "+line+"\n";
        try{Files.createDirectories(dir());Files.writeString(dir().resolve("log.txt"),entry,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}
    }
    private void fail(Throwable t){var w=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(w));note("ERROR "+w);phase=3;age=0;}
    private void shot(String name){
        var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());frames++;
        try(image){Path p=dir().resolve(String.format(Locale.ROOT,"%04d_%s.png",age,name));Files.createDirectories(p.getParent());image.writeTo(p);}catch(Exception e){note("ERROR shot "+e);}
    }
    private Object call(String method)throws Exception{var m=GachaScreen.class.getDeclaredMethod(method);m.setAccessible(true);return m.invoke(mc.currentScreen);}
    private void press(String shape)throws Exception{
        if(!(mc.currentScreen instanceof GachaScreen s)){note("ERROR no gacha screen for "+shape);return;}
        float[] p=(float[])call(shape);double x=0,y=0;for(int i=0;i<p.length;i+=2){x+=p[i];y+=p[i+1];}x/=p.length/2;y/=p.length/2;
        s.mouseClicked(x,y,0);note("pressed "+shape);
    }
    private String counted(){var c=GachaClient.count;return "SSR in "+c.toSsr()+", SR in "+c.toSr()+", "+c.pulls()+" pulls";}
    private void raw(int n){var b=PacketByteBufs.create();b.writeInt(900000+n);b.writeByte(n);ClientPlayNetworking.send(GachaServer.REQUEST,b);}

    private void tick()throws Exception{
        age++;
        switch(phase){
            case 0->{if(mc.currentScreen!=null&&mc.getOverlay()==null&&age>40){
                var o=mc.options;o.getViewDistance().setValue(5);o.getMaxFps().setValue(30);o.getGuiScale().setValue(2);o.pauseOnLostFocus=false;
                mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                note("connecting "+ADDRESS);ConnectScreen.connect(mc.currentScreen,mc,ServerAddress.parse(ADDRESS),new ServerInfo("mp",ADDRESS,false),false);phase=1;age=0;}}
            case 1->{
                if(age>2400){note("ERROR never ready");phase=3;age=0;return;}
                if(mc.world==null||mc.player==null)return;
                if(mc.currentScreen!=null&&!(mc.currentScreen instanceof GachaScreen))mc.setScreen(null);
                if(mc.world.getPlayers().size()>=2&&age>140){note("ready stand="+ProgressClient.profile.stand());phase=2;age=0;}}
            case 2->{
                mc.onWindowFocusChanged(true);
                if(mc.world==null||mc.player==null){note("ERROR lost connection");phase=3;age=0;return;}
                if(ROLE.equals("puller"))puller();else other();
            }
            case 3->{if(age==20){note("frames "+frames);try{Files.writeString(OUT.resolve(ROLE+".done"),"done");}catch(Exception ignored){}mc.scheduleStop();}}
        }
    }
    /** The puller's script: each step waits for what the last one should have led to. */
    private void puller()throws Exception{
        since++;
        Object stage=mc.currentScreen instanceof GachaScreen?call("stage"):null;
        switch(step){
            case 0->{if(since>20){GachaClient.open();note("opened the gacha");step++;since=0;}}
            case 1->{if(GachaClient.ready&&since>20){note("count "+counted()+" stand="+ProgressClient.profile.stand());shot("main");press("pullOne");step++;since=0;}}
            case 2->{
                if(since%20==0&&"SCENE".equals(String.valueOf(stage)))shot("scene");
                if("RESULT".equals(String.valueOf(stage))){var f=GachaScreen.class.getDeclaredField("grades");f.setAccessible(true);note("single pull: "+f.get(mc.currentScreen)+" notice="+GachaClient.notice+" stand="+ProgressClient.profile.stand()+" count "+counted());shown=false;step++;since=0;}
                if(since>1200){note("ERROR the single pull never showed its result");step=9;}
            }
            case 3->{if(since==30)shot("result");if(since==40){mc.setScreen(null);note("closed; asking for a pull with Kageno on");GachaClient.request(1);step++;since=0;}}
            case 4->{if(!GachaClient.pending&&since>5){note("refused: '"+GachaClient.notice+"' count "+counted());step++;since=0;}}
            case 5->{if("none".equals(ProgressClient.profile.stand())&&since>10){GachaClient.open();note("stand off again; opened the gacha");step++;since=0;}if(since>1200){note("ERROR stand never came off");step=9;}}
            case 6->{if(GachaClient.ready&&!GachaClient.pending&&since>30){note("count "+counted());press("pullTen");step++;since=0;}}
            case 7->{
                if(since%20==0&&"SCENE".equals(String.valueOf(stage)))shot("ten");
                if("RESULT".equals(String.valueOf(stage))){var f=GachaScreen.class.getDeclaredField("grades");f.setAccessible(true);note("ten-pull: "+f.get(mc.currentScreen)+" stand="+ProgressClient.profile.stand()+" count "+counted());step++;since=0;}
                if(since>2400){note("ERROR the ten-pull never showed its result");step=9;}
            }
            case 8->{if(since==40)shot("ten_result");if(since==50){mc.setScreen(null);step++;since=0;}}
            case 9->{if("none".equals(ProgressClient.profile.stand())&&since>40){note("sending two pulls in one tick");raw(1);raw(1);step++;since=0;}if(since>1200){note("ERROR stand never came off for the double request");step=10;since=0;}}
            case 10->{if(since==100){note("done");phase=3;age=0;}}
        }
    }
    private void other()throws Exception{
        since++;
        switch(step){
            case 0->{if(since>40){GachaClient.open();note("opened the gacha with stand="+ProgressClient.profile.stand());step++;since=0;}}
            case 1->{if(GachaClient.ready&&since>30){shot("stand_on");note("count "+counted()+" canPull="+call("canPull"));GachaClient.request(1);step++;since=0;}}
            case 2->{if(!GachaClient.pending&&since>5){note("refused: '"+GachaClient.notice+"' count "+counted());step++;since=0;}}
            case 3->{if(Files.exists(OUT.resolve("puller.done"))||since>9000){note("count at the end "+counted()+" stand="+ProgressClient.profile.stand());phase=3;age=0;}}
        }
    }
}
