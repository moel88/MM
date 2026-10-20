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
    /**
     * Since 2.38.2: E is walked and aimed (the camera turns 50 degrees right as it starts, W held through its thrusts), R
     * is walked away from the watcher through it and after it ends (its slowdown must end with it), T held's camera
     * turns 39 degrees right after the first slash, and "DUMMY" runs /botand dummy atomic 30 (an Atomic with no player
     * caster, 30 blocks ahead). Since 2.43.0 the caster dashes (C) after the lone M1 at 2420, whose combo then breaks: the
     * log shows both in the cooldown list. Since 2.45.0 the caster sends the player list away with Tab and back, opens
     * the pause menu (Esc), asks for a reset (R) and confirms it (Enter): the server kills it and both clients' own logs
     * show the death message in chat; then it respawns. Both log the player list's names and whether it is away.
     */
    private static final String[] SCRIPT={
        "5 M1","18 M1","31 M1","44 M1",
        "90 F+","130 F-",
        "170 E","172 TURN50","174 W+","214 W-",
        "270 R","271 TURN180","272 W+","362 W-",
        "370 T","510 T+","527 TURN39","530 T-",
        "650 Y","770 Q","830 H","980 G",
        "1480 P","1700 P",
        "1760 P","1900 E",
        "2000 P","2420 M1",
        "2445 C",
        "2460 DUMMY",
        "3070 TAB","3100 TAB","3120 ESC","3135 R","3150 ENTER","3185 RESPAWN",
        "3240 END"};
    /** Walking speed windows (script ticks, inclusive) and the lens's FOV windows (script ticks) logged by the caster. */
    private static final int[][] SPEED={{282,312},{330,360}},LENS={{126,142},{312,340}};
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mp-writer");t.setDaemon(true);return t;});
    private final Map<String,Object> flags=new HashMap<>();
    private final Map<UUID,long[]> casts=new HashMap<>();private net.minecraft.util.math.Vec3d lastPos;
    private MinecraftClient mc;private int phase,age,frames;private boolean caster;private final Set<Integer> held=new HashSet<>();
    private float quakePeak,overwhelmPeak,lightPeak,wallPeak;private net.minecraft.util.math.Vec3d walkLast;private long walkStart=-1;
    private final List<Double> steps=new ArrayList<>();
    private net.minecraft.util.math.Vec3d speedLast;private final List<Double> speedSteps=new ArrayList<>();private float lensMin=9,lensMax=0;

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
    /** The watcher looks a little up at G's pillar from outside; otherwise both look slightly down at each other. */
    private void face(){if(mc.player==null)return;BotandCamera.STATE.yaw=caster?0:180;BotandCamera.STATE.pitch=!caster&&!AtomicClient.CASTS.isEmpty()?-8:12;}

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
                if(mc.currentScreen!=null){flag("screen",mc.currentScreen.getClass().getName());if(!interfaceStep())mc.setScreen(null);}
                observe();
                if(age%4==0)shot();
                if(caster){
                    if(age%40==0&&held.isEmpty())face();
                    measure();
                    for(String step:SCRIPT){String[] s=step.split(" ");int at=Integer.parseInt(s[0]);if(age==at)act(s[1],true);if(age==at+1&&!s[1].endsWith("+")&&!s[1].endsWith("-"))act(s[1],false);}
                }else{
                    if((age%20==0||!AtomicClient.CASTS.isEmpty()&&age%5==0)&&StandClient.cinematic()==null)face();
                    walk();
                    if(age%20==0&&Files.exists(OUT.resolve("done"))){note("caster done");phase=3;age=0;}
                    if(age>4000){note("ERROR watcher timeout");phase=3;age=0;}
                }
                if(mc.world==null||mc.player==null){note("ERROR lost connection");phase=3;age=0;}
            }
            case 3->{if(age==20){writer.shutdown();writer.awaitTermination(60,TimeUnit.SECONDS);note("frames "+frames);mc.scheduleStop();}}
        }
    }
    /**
     * During G the watcher walks forward for 30 ticks outside the column (cast age 280) and again inside it (325; the
     * server moves it in at 320 and out at 360) on the real movement path, and logs its walking speed: the median
     * distance per tick on the ground once it is up to speed, leaving out the ticks after a hit (knockback).
     */
    private void walk(){
        var cast=AtomicClient.CASTS.values().stream().findFirst().orElse(null);if(cast==null||mc.player==null)return;
        long a=mc.world.getTime()-cast.start;var p=mc.player.getPos();
        if(walkStart<0&&(a==280||a==325)){walkStart=a;walkLast=p;steps.clear();key(GLFW_KEY_W,true);return;}
        if(walkStart<0)return;
        if(a-walkStart>=6&&mc.player.isOnGround()&&mc.player.hurtTime==0)steps.add(Math.hypot(p.x-walkLast.x,p.z-walkLast.z));
        walkLast=p;
        if(a>=walkStart+30){
            key(GLFW_KEY_W,false);steps.sort(null);double median=steps.isEmpty()?0:steps.get(steps.size()/2);
            note(String.format(Locale.ROOT,"walking %.2f blocks/s on the ground at cast age %d (median of %d ticks, %s)",median*20,a,steps.size(),AtomicClient.held(mc.player)?"held":"free"));
            walkStart=-1;
        }
    }
    /** The caster's walking speed in each SPEED window (median per tick on the ground) and the lens's FOV multiplier range in each LENS window. */
    private void measure(){
        var p=mc.player.getPos();
        for(int[] w:SPEED){
            if(age==w[0]){speedLast=p;speedSteps.clear();}
            else if(age>w[0]&&age<=w[1]){if(mc.player.isOnGround())speedSteps.add(Math.hypot(p.x-speedLast.x,p.z-speedLast.z));speedLast=p;}
            if(age==w[1]){var sorted=new ArrayList<>(speedSteps);sorted.sort(null);double median=sorted.isEmpty()?0:sorted.get(sorted.size()/2);
                note(String.format(Locale.ROOT,"walking %.2f blocks/s from script tick %d to %d (median of %d ticks)",median*20,w[0],w[1],sorted.size()));}
        }
        for(int[] w:LENS){
            if(age>=w[0]&&age<=w[1]){float f=mc.player.getFovMultiplier();lensMin=Math.min(lensMin,f);lensMax=Math.max(lensMax,f);}
            if(age==w[1]){note(String.format(Locale.ROOT,"lens FOV multiplier %.3f to %.3f from script tick %d to %d",lensMin,lensMax,w[0],w[1]));lensMin=9;lensMax=0;}
        }
    }
    /** The 2.45.0 steps keep the pause menu and the death screen up (the rest of the script closes every screen). */
    private boolean interfaceStep(){return caster&&age>=3060&&(mc.currentScreen instanceof dev.sangwoo.celestialfall.client.ui.BotandPauseScreen||mc.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen);}
    private void act(String step,boolean down){
        if(step.equals("END")){if(down){note("script end");try{Files.writeString(OUT.resolve("done"),"done");}catch(Exception ignored){}phase=3;age=0;}return;}
        if(step.startsWith("TURN")){if(down){BotandCamera.STATE.yaw=(caster?0:180)+Float.parseFloat(step.substring(4));note("camera turned to "+BotandCamera.STATE.yaw);}return;}
        if(step.equals("RESPAWN")){if(down){note("respawn requested (alive="+mc.player.isAlive()+")");mc.player.requestRespawn();}return;}
        if(step.equals("DUMMY")){if(down){note("command botand dummy atomic 30");mc.player.networkHandler.sendChatCommand("botand dummy atomic 30");}return;}
        String k=step.replaceAll("[+-]$","");boolean press=step.endsWith("+")||!step.endsWith("-")&&down;
        if(down&&press&&!k.equals("W"))face();
        if(down||!step.endsWith("+"))note((press?"press ":"release ")+k);
        switch(k){
            case "M1"->attack(press);
            case "F"->key(GLFW_KEY_F,press);
            case "E"->key(GLFW_KEY_E,press);case "R"->key(GLFW_KEY_R,press);case "T"->key(GLFW_KEY_T,press);case "Y"->key(GLFW_KEY_Y,press);
            case "Q"->key(GLFW_KEY_Q,press);case "G"->key(GLFW_KEY_G,press);case "H"->key(GLFW_KEY_H,press);case "P"->key(GLFW_KEY_P,press);
            case "W"->key(GLFW_KEY_W,press);case "C"->key(GLFW_KEY_C,press);
            case "TAB"->key(GLFW_KEY_TAB,press);case "ESC"->key(GLFW_KEY_ESCAPE,press);case "ENTER"->key(GLFW_KEY_ENTER,press);
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
        // 2.42.0: the cooldown list (top first) and whether the danger banner is up for this player
        flag("cooldowns",CooldownHud.shown());
        flag("danger_banner",AtomicClient.CASTS.values().stream().anyMatch(c->c.caster.equals(mc.player.getUuid())&&c.age(0)<dev.sangwoo.celestialfall.AtomicTimeline.END));
        // G seen from here: the strongest camera quake and Veil screen treatment while it lasts, logged when it ends.
        if(!AtomicClient.CASTS.isEmpty()){
            var s=dev.sangwoo.celestialfall.client.AtomicImmersion.at(mc.gameRenderer.getCamera().getPos(),0);
            quakePeak=Math.max(quakePeak,Math.max(Math.abs(s.yaw()),Math.abs(s.pitch())));
            overwhelmPeak=Math.max(overwhelmPeak,dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm.strength());
            lightPeak=Math.max(lightPeak,dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm.radiance());
            // The wall shake this camera gets (degrees, with the shake setting) from the nearest cast.
            for(var c:AtomicClient.CASTS.values()){
                var cam=mc.gameRenderer.getCamera().getPos();float sec=c.age(0)/20f;
                double r=dev.sangwoo.celestialfall.client.atomic.AtomicPillarShape.radius(dev.sangwoo.celestialfall.client.atomic.AtomicStage.visual(sec),c.radius);
                if(!dev.sangwoo.celestialfall.client.atomic.AtomicPillar.interior(c,cam,sec))
                    wallPeak=Math.max(wallPeak,dev.sangwoo.celestialfall.client.atomic.AtomicStage.wallShake(sec,dev.sangwoo.celestialfall.client.AtomicImmersion.wallDistance(cam,c.origin,0),r,c.radius)
                        *(float)dev.sangwoo.celestialfall.CelestialFall.CONFIG.screenShakeStrength);
            }
            flag("veil_overwhelm_active",dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm.active());
        }else if(quakePeak>0||overwhelmPeak>0||lightPeak>0){
            note(String.format(Locale.ROOT,"G seen: quake peak %.2f deg, wall shake amplitude peak %.2f deg, overwhelm peak %.2f, light peak %.2f, veil frames %d",quakePeak,wallPeak,overwhelmPeak,lightPeak,
                dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm.renderedFrames()));
            quakePeak=0;overwhelmPeak=0;lightPeak=0;wallPeak=0;
        }
        flag("atomic",AtomicClient.CASTS.size());flag("overdrive",StandClient.OVERDRIVE.size());flag("hits",StandClient.hitCount);
        flag("health",Math.round(mc.player.getHealth()));
        // 2.45.0: the player list (its names, and whether Tab sent it away), the pause menu and its reset question, deaths and chat
        flag("player_list",mc.player.networkHandler.getListedPlayerListEntries().stream().map(e->e.getProfile().getName()).sorted().toList());
        flag("player_list_away",dev.sangwoo.celestialfall.client.ui.PlayerPanel.hidden());
        flag("pause_menu",mc.currentScreen instanceof dev.sangwoo.celestialfall.client.ui.BotandPauseScreen);
        flag("reset_question",mc.currentScreen instanceof dev.sangwoo.celestialfall.client.ui.BotandPauseScreen s&&s.asking());
        flag("alive",mc.player.isAlive());
        // the death message itself is in each client's own log as a [CHAT] line (logs/ROLE-latest.log)
        // G's hold on this player: its speed against its base, and that the slowdown is not taken for an optical zoom.
        var speed=mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        flag("atomic_hold",AtomicClient.held(mc.player)?String.format(Locale.ROOT,"speed x%.2f fov x%.2f",speed.getValue()/speed.getBaseValue(),mc.player.getFovMultiplier()):"off");
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
