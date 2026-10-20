package dev.sangwoo.botandmapmp;

import dev.sangwoo.celestialfall.client.MapClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;
import java.util.*;

/**
 * The map's multiplayer check, client half (only with -Dbotand.mapmp.role=NAME): joins the dedicated server, waits for
 * the town round the spawn to arrive and be built, logs whether the mod's map is on for it and which of the mod's
 * townspeople it can see, and saves a frame looking each way from the spawn (the station square and its stalls, the
 * street down to the crossing and the bridge), the game's HUD showing; then leaves.
 */
public final class MapMpClient implements ClientModInitializer {
    static final String ROLE=System.getProperty("botand.mapmp.role");
    static final Path OUT=Path.of(System.getProperty("botand.mapmp.out","mapmp"));
    static final String ADDRESS=System.getProperty("botand.mapmp.address","127.0.0.1:25565");
    /** Where to look from the spawn: yaw, pitch, name. */
    static final Object[][] VIEWS={{0f,2f,"south"},{-75f,4f,"station"},{180f,4f,"north"}};
    private MinecraftClient mc;private int phase,age,view,settle,built=-1;
    private final StringBuilder log=new StringBuilder();

    @Override public void onInitializeClient(){
        if(ROLE==null)return;
        ClientTickEvents.END_CLIENT_TICK.register(c->{mc=c;try{tick();}catch(Throwable t){note("ERROR "+t);mc.scheduleStop();phase=9;}});
    }
    private void note(String s){
        log.append(s).append('\n');
        try{Path d=OUT.resolve(ROLE);Files.createDirectories(d);Files.writeString(d.resolve("log.txt"),log);}catch(Exception ignored){}
    }
    private void tick()throws Exception{
        age++;
        switch(phase){
            case 0->{if(mc.currentScreen!=null&&mc.getOverlay()==null&&age>40){
                var o=mc.options;o.getViewDistance().setValue(8);o.getMaxFps().setValue(30);o.getGuiScale().setValue(2);o.pauseOnLostFocus=false;
                mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                note("connecting "+ADDRESS);ConnectScreen.connect(mc.currentScreen,mc,ServerAddress.parse(ADDRESS),new ServerInfo("map",ADDRESS,false),false);phase=1;age=0;}}
            case 1->{
                if(age>3000){note("ERROR never in the world");phase=8;age=0;return;}
                if(mc.world==null||mc.player==null)return;
                if(mc.currentScreen!=null){note("screen "+mc.currentScreen.getClass().getSimpleName()+" closed");mc.setScreen(null);}
                if(age<200)return;
                note("in the world at "+fmt()+" map on="+MapClient.enabled()+" players="+mc.world.getPlayers().size());
                List<String> people=new ArrayList<>();
                for(var e:mc.world.getEntities())if(e.hasCustomName()&&!(e instanceof net.minecraft.entity.player.PlayerEntity))people.add(e.getCustomName().getString()+" @"+Math.round(e.getX())+","+Math.round(e.getY())+","+Math.round(e.getZ()));
                note("townspeople in sight: "+people);
                phase=2;age=0;view=0;
            }
            case 2->{ // each view: turn, wait for the chunks in view to be built, save the frame
                if(mc.currentScreen!=null)mc.setScreen(null);
                Object[] v=VIEWS[view];
                mc.player.setYaw((float)v[0]);mc.player.setPitch((float)v[1]);mc.player.setHeadYaw((float)v[0]);
                int count=mc.worldRenderer.getCompletedChunkCount();
                boolean done=mc.worldRenderer.isTerrainRenderComplete()&&count==built;built=count;
                if(age<60||!done){settle=0;return;}
                if(++settle<30)return;
                var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
                Path p=OUT.resolve(ROLE).resolve(v[2]+".png");Files.createDirectories(p.getParent());image.writeTo(p);image.close();
                note("frame "+v[2]+" map on="+MapClient.enabled());
                view++;age=0;settle=0;
                if(view>=VIEWS.length){phase=8;age=0;}
            }
            case 8->{if(age==20){note("done");mc.scheduleStop();phase=9;}}
            default->{}
        }
    }
    private String fmt(){return String.format("%.1f,%.1f,%.1f",mc.player.getX(),mc.player.getY(),mc.player.getZ());}
}
