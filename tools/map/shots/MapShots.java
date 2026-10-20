package dev.sangwoo.botandmap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Films a saved map in the real client (tools/map/shots/run_map_shots.py): opens the world, turns the player into an
 * invisible spectator with the HUD hidden, and for each shot sets the time of day and puts the camera where the shot
 * says, waits until every chunk in view has been built, and saves the frame. A still is one frame; a flight is a
 * camera moving through key positions on a smooth curve, saved at 60 frames a second of its own clock, each frame
 * taken only once the terrain in view is complete. Not shipped in the mod jar.
 *
 * <p>Shots ("," between them): {@code NAME;time=T;at=x/y/z/yaw/pitch[;fov=F]} or
 * {@code NAME;time=T;path=x/y/z/yaw/pitch|...;secs=S[;fov=F]}.
 */
public final class MapShots implements ClientModInitializer {
    static final Path OUT=Path.of(System.getProperty("botand.map.out","map-shots"));
    static final String WORLD=System.getProperty("botand.map.world","");
    static final String LIST=System.getProperty("botand.map.list","");
    static final float SLOW=Float.parseFloat(System.getProperty("botand.map.slow","1"));
    static MapShots instance;
    private MinecraftClient mc;
    private int phase,age,frame,settle;private boolean slowed;
    private final Deque<String> queue=new ArrayDeque<>();
    private String name;private Map<String,String> opts=new HashMap<>();
    private double[][] keys;private double secs;private int frames;
    private double[] pose;
    private final ExecutorService writer=Executors.newSingleThreadExecutor();
    private final List<Future<?>> jobs=new ArrayList<>();
    private final StringBuilder log=new StringBuilder();

    @Override public void onInitializeClient(){
        instance=this;
        for(String s:LIST.split(","))if(!s.isBlank())queue.add(s.trim());
        ClientTickEvents.END_CLIENT_TICK.register(c->{mc=c;try{tick();}catch(Throwable t){fail(t);}});
    }
    private void fail(Throwable t){
        t.printStackTrace();
        try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("error.txt"),t+"\n"+log);}catch(Exception ignored){}
        mc.scheduleStop();phase=99;
    }

    private void tick()throws Exception{
        age++;
        if(phase>=2&&mc.currentScreen!=null&&mc.world!=null)mc.setScreen(null);
        switch(phase){
            case 0->{
                if(mc.currentScreen!=null&&mc.getOverlay()==null&&age>60){
                    Files.createDirectories(OUT);
                    var o=mc.options;o.getViewDistance().setValue(Integer.getInteger("botand.map.view",12));o.getMaxFps().setValue(260);o.getGuiScale().setValue(2);
                    o.hudHidden=true;o.getBobView().setValue(false);mc.onResolutionChanged();
                    mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                    mc.createIntegratedServerLoader().start(new TitleScreen(),WORLD);
                    phase=1;age=0;
                }
            }
            case 1->{
                if(mc.player!=null&&mc.world!=null&&age>40){
                    var server=mc.getServer();
                    server.execute(()->{
                        server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
                        for(var p:server.getPlayerManager().getPlayerList())p.changeGameMode(GameMode.SPECTATOR);
                    });
                    mc.setScreen(null);phase=2;age=0;
                }
            }
            case 2->{ // the next shot
                if(age<20)return;
                if(queue.isEmpty()){for(var j:jobs)j.get();Files.writeString(OUT.resolve("log.txt"),log);mc.scheduleStop();phase=99;return;}
                String[] parts=queue.poll().split(";");name=parts[0];opts.clear();
                for(int i=1;i<parts.length;i++){String[] kv=parts[i].split("=",2);opts.put(kv[0],kv.length>1?kv[1]:"1");}
                long time=Long.parseLong(opts.getOrDefault("time","1000"));
                var server=mc.getServer();server.execute(()->server.getOverworld().setTimeOfDay(time));
                mc.options.getFov().setValue(Integer.parseInt(opts.getOrDefault("fov","70")));
                if(opts.containsKey("path")){
                    String[] k=opts.get("path").split("\\|");keys=new double[k.length][];
                    for(int i=0;i<k.length;i++)keys[i]=Arrays.stream(k[i].split("/")).mapToDouble(Double::parseDouble).toArray();
                    secs=Double.parseDouble(opts.getOrDefault("secs","10"));frames=(int)Math.round(secs*60);
                    if(SLOW>1&&!slowed){var f=MinecraftClient.class.getDeclaredField("renderTickCounter");f.setAccessible(true);var counter=f.get(mc);var t=counter.getClass().getDeclaredField("tickTime");t.setAccessible(true);t.setFloat(counter,50f*SLOW);slowed=true;}
                }else{keys=new double[][]{Arrays.stream(opts.get("at").split("/")).mapToDouble(Double::parseDouble).toArray()};frames=1;}
                frame=0;settle=0;pose=at(0);
                log.append(name).append(" time ").append(time).append(" frames ").append(frames).append('\n');
                phase=3;age=0;
            }
            default->{}
        }
    }

    /** Camera pose (x, y, z, yaw, pitch) at frame k: a Catmull-Rom curve through the key positions. */
    private double[] at(int k){
        if(keys.length==1)return keys[0];
        double u=frames<=1?0:k/(double)(frames-1)*(keys.length-1);
        int i=Math.min(keys.length-2,(int)Math.floor(u));double s=u-i;
        double[] p0=keys[Math.max(0,i-1)],p1=keys[i],p2=keys[i+1],p3=keys[Math.min(keys.length-1,i+2)];
        double[] out=new double[5];
        for(int d=0;d<5;d++){
            double a=p0[d],b=p1[d],c=p2[d],e=p3[d];
            if(d==3){b=a+wrap(b-a);c=b+wrap(c-b);e=c+wrap(e-c);a=b-wrap(b-a);}
            out[d]=0.5*((2*b)+(-a+c)*s+(2*a-5*b+4*c-e)*s*s+(-a+3*b-3*c+e)*s*s*s);
        }
        return out;
    }
    private static double wrap(double d){while(d>180)d-=360;while(d<-180)d+=360;return d;}

    /** Before each frame is drawn: the camera exactly at this frame's pose (no interpolation from the last tick). */
    public static void beforeFrame(){if(instance!=null&&instance.phase==3)instance.place();}
    private void place(){
        var p=mc.player;if(p==null||pose==null)return;
        p.setPosition(pose[0],pose[1],pose[2]);p.prevX=p.lastRenderX=pose[0];p.prevY=p.lastRenderY=pose[1];p.prevZ=p.lastRenderZ=pose[2];
        p.setYaw((float)pose[3]);p.setPitch((float)pose[4]);p.prevYaw=(float)pose[3];p.prevPitch=(float)pose[4];
        p.setHeadYaw((float)pose[3]);p.prevHeadYaw=(float)pose[3];p.renderYaw=p.lastRenderYaw=(float)pose[3];p.renderPitch=p.lastRenderPitch=(float)pose[4];
        p.setVelocity(0,0,0);
    }

    /** After each frame is drawn: keep it once the terrain in view is complete, then move on. */
    public static void afterFrame(){if(instance!=null&&instance.phase==3)try{instance.capture();}catch(Throwable t){instance.fail(t);}}
    private void capture()throws Exception{
        boolean complete=mc.worldRenderer.isTerrainRenderComplete();
        // a still waits for the chunks round the camera to arrive and be built; a flight frame only for its build
        int need=frame==0?Integer.getInteger("botand.map.settle",40):2;
        if(age<(frame==0?60:0)||!complete){settle=0;return;}
        if(++settle<need)return;
        var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
        Path p=frames==1?OUT.resolve(name+".png"):OUT.resolve(name).resolve(String.format("%05d.png",frame));
        jobs.add(writer.submit(()->{try(image){Files.createDirectories(p.getParent());image.writeTo(p);}catch(Exception e){throw new RuntimeException(e);}}));
        frame++;settle=0;
        if(frame>=frames){log.append(name).append(" done\n");phase=2;age=0;pose=null;return;}
        pose=at(frame);
    }
}
