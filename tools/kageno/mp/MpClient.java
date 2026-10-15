package dev.sangwoo.botandmp;

import dev.sangwoo.celestialfall.client.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Multiplayer smoke test, client half (only with -Dbotand.mp.role=caster|watcher). The caster joins
 * the dedicated server and plays every Kageno key through the real keyboard path on a fixed script;
 * the watcher stands in front of it. Both log what their client sees and save a frame every 4 ticks.
 */
public final class MpClient implements ClientModInitializer {
    private static final String ROLE=System.getProperty("botand.mp.role");
    private static final Path OUT=Path.of(System.getProperty("botand.mp.out","mp"));
    private static final String ADDRESS=System.getProperty("botand.mp.address","127.0.0.1:25565");
    private static final String[] SKILLS={"M1","E","R","T","T+","Y","Q","H","G","P"};
    /** Script tick, then a key: a bare name taps it, "+"/"-" hold and release. */
    private static final String[] SCRIPT={
        "5 M1","18 M1","31 M1","44 M1",
        "90 F+","130 F-",
        "170 E","270 R","370 T","510 T+","530 T-",
        "650 Y","770 Q","830 H","980 G",
        "1480 P","1700 P",
        "1760 P","1900 E",
        "2000 P","2420 M1",
        "2480 END"};
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mp-writer");t.setDaemon(true);return t;});
    private final Map<String,Object> flags=new HashMap<>();
    private final Map<UUID,long[]> casts=new HashMap<>();private net.minecraft.util.math.Vec3d lastPos;
    private MinecraftClient mc;private int phase,age,frames;private boolean caster;private final Set<Integer> held=new HashSet<>();

    @Override public void onInitializeClient(){
        if(ROLE==null)return;caster=ROLE.equals("caster");
        ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{tick();}catch(Throwable t){fail(t);}});
    }
    private Path dir(){return OUT.resolve(ROLE);}
    private void note(String line){
        String entry=(mc.world==null?-1:mc.world.getTime())+" ["+age+"] "+line+"\n";
        try{Files.createDirectories(dir());Files.writeString(dir().resolve("log.txt"),entry,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}
    }
    private void fail(Throwable t){
        var w=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(w));note("ERROR "+w);
    }
    private void flag(String name,Object value){if(!Objects.equals(flags.put(name,value),value))note(name+" = "+value);}
    private void key(int key,boolean down){mc.keyboard.onKey(mc.getWindow().getHandle(),key,0,down?GLFW_PRESS:GLFW_RELEASE,0);if(down)held.add(key);else held.remove(key);}
    private void attack(boolean down){var k=InputUtil.Type.MOUSE.createFromCode(GLFW_MOUSE_BUTTON_LEFT);KeyBinding.setKeyPressed(k,down);if(down)KeyBinding.onKeyPressed(k);}
    private String name(UUID id){var p=mc.world.getPlayerByUuid(id);return p==null?id.toString().substring(0,8):p.getGameProfile().getName();}
    private void face(){if(mc.player==null)return;BotandCamera.STATE.yaw=caster?0:180;BotandCamera.STATE.pitch=12;}

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
                if(mc.currentScreen!=null){flag("screen",mc.currentScreen.getClass().getName());mc.setScreen(null);}
                boolean ready=mc.world.getPlayers().size()>=2&&age>100&&(!caster||StandClient.visible(mc.player));
                if(ready){note("ready players="+mc.world.getPlayers().size()+" kageno="+StandClient.visible(mc.player));face();phase=2;age=0;}}
            case 2->{
                mc.onWindowFocusChanged(true);
                if(mc.currentScreen!=null){flag("screen",mc.currentScreen.getClass().getName());mc.setScreen(null);}
                observe();
                if(age%4==0)shot();
                if(caster){
                    if(age%40==0&&held.isEmpty())face();
                    for(String step:SCRIPT){String[] s=step.split(" ");int at=Integer.parseInt(s[0]);if(age==at)act(s[1],true);if(age==at+1&&!s[1].endsWith("+")&&!s[1].endsWith("-"))act(s[1],false);}
                }else{
                    if(age%20==0&&StandClient.cinematic()==null)face();
                    if(age%20==0&&Files.exists(OUT.resolve("done"))){note("caster done");phase=3;age=0;}
                    if(age>4000){note("ERROR watcher timeout");phase=3;age=0;}
                }
                if(mc.world==null||mc.player==null){note("ERROR lost connection");phase=3;age=0;}
            }
            case 3->{if(age==20){writer.shutdown();writer.awaitTermination(60,TimeUnit.SECONDS);note("frames "+frames);mc.scheduleStop();}}
        }
    }
    private void act(String step,boolean down){
        if(step.equals("END")){if(down){note("script end");try{Files.writeString(OUT.resolve("done"),"done");}catch(Exception ignored){}phase=3;age=0;}return;}
        String k=step.replaceAll("[+-]$","");boolean press=step.endsWith("+")||!step.endsWith("-")&&down;
        if(down)face();
        if(down||!step.endsWith("+"))note((press?"press ":"release ")+k);
        switch(k){
            case "M1"->attack(press);
            case "F"->key(GLFW_KEY_F,press);
            case "E"->key(GLFW_KEY_E,press);case "R"->key(GLFW_KEY_R,press);case "T"->key(GLFW_KEY_T,press);case "Y"->key(GLFW_KEY_Y,press);
            case "Q"->key(GLFW_KEY_Q,press);case "G"->key(GLFW_KEY_G,press);case "H"->key(GLFW_KEY_H,press);case "P"->key(GLFW_KEY_P,press);
            default->note("ERROR unknown step "+step);
        }
    }
    private void observe(){
        long now=mc.world.getTime();
        for(var c:StandClient.CASTS.values())if(!casts.containsKey(c.id())){
            casts.put(c.id(),new long[]{c.skill(),c.start()});
            note("cast "+(c.skill()<SKILLS.length?SKILLS[c.skill()]:c.skill())+" combo="+c.combo()+" by "+name(c.caster())+" age="+String.format(Locale.ROOT,"%.1f",c.age(0))+" targets="+c.targets().size());
        }
        for(var it=casts.entrySet().iterator();it.hasNext();){var e=it.next();if(!StandClient.CASTS.containsKey(e.getKey())){note("end "+SKILLS[(int)e.getValue()[0]]+" age~"+(now-e.getValue()[1]));it.remove();}}
        flag("cinematic",StandClient.cinematic()!=null);flag("mono",StandClient.monochrome());flag("frozen",StandClient.frozen(mc.player));flag("locked",StandClient.locked());
        flag("atomic",AtomicClient.CASTS.size());flag("overdrive",StandClient.OVERDRIVE.size());flag("hits",StandClient.hitCount);
        flag("health",Math.round(mc.player.getHealth()));
        if(lastPos==null||lastPos.squaredDistanceTo(mc.player.getPos())>1){lastPos=mc.player.getPos();note(String.format(Locale.ROOT,"selfpos %.2f %.2f %.2f",lastPos.x,lastPos.y,lastPos.z));}
        var other=mc.world.getPlayers().stream().filter(p->p!=mc.player).findFirst().orElse(null);
        if(other!=null){flag("other_marked",StandClient.shadowMarked(other));flag("other_outlined",StandClient.shadowOutlined(other));}
        try{var f=dev.sangwoo.celestialfall.client.kageno.HitFeedback.class.getDeclaredField("NUMBERS");f.setAccessible(true);flag("damage_numbers",((Map<?,?>)f.get(null)).size());}catch(ReflectiveOperationException e){flag("damage_numbers","n/a");}
    }
    private void shot(){
        var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());frames++;
        Path p=dir().resolve(String.format(Locale.ROOT,"%06d.png",mc.world.getTime()));
        writer.submit(()->{try(image){Files.createDirectories(p.getParent());image.writeTo(p);}catch(Exception e){throw new RuntimeException(e);}});
    }
}
