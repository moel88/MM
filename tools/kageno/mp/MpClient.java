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
    /** "beatdown" (2.64.0, MP_ONLY=beatdown): only the beatdown's script, BEATDOWN_SCRIPT (the check that new rules and packets call for). */
    static final String ONLY=System.getProperty("botand.mp.only","");
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
     * Since 2.51.0 the caster then takes Star Platinum as its stand ("/botand progress stand @s star_platinum"), brings it
     * out with Q, walks away with it for two seconds and sends it back with Q: both log how they see each player's stand
     * (out or not as the server tracks it, coming out, at rest or going back, and whether this client drew it).
     * Since 2.54.0 the caster holds the attack with the stand out (3315 to 3378), so Star Platinum punches the watcher
     * standing in front through a whole combo, the fourth throwing it: both log each punch they hear (who, which,
     * whether it landed and on whom, its age on their clock then), when its shout starts on their clock (it should start
     * a tick in, StandAttack.LEAD) and the cooldown list's rest. Since 2.56.0 only the hook and the finisher shout: the
     * jab and the cross are logged as shouting nothing, and the hook's shout as fading when the finisher's comes.
     * Since 2.57.0 the caster first holds E with the stand out (3315 to 3405), so Star Platinum rushes the watcher in
     * front through all its forty blows, walking forward through it (3330 to 3380, 90% slower; the speed window is
     * logged); both log the rush they hear (its start, its voice starting on their clock, every tenth blow and whom it
     * struck, how loud its blows sound, its end), the watcher how many cries of pain it heard from itself, and the
     * cooldown list. The punches follow (3440 to 3503). Since 2.59.0 the caster then throws the heavy punch (R) twice at
     * the watcher, brought back 2.5 blocks in front of it each time ("HOME": /execute ... tp): first unguarded, then
     * with the watcher holding its guard up (F, from when it hears that second heavy punch start): both log each heavy
     * punch they hear, when its voice and its blow start on their clock, how loud its blow sounds once it lands, and
     * whose guard it broke; the watcher logs its own guard (up, broken), the server how far it flew. Since 2.60.0 the
     * second, breaking the watcher's guard, does not throw it but incapacitates it for 2 s: the watcher tries the attack
     * 5 ticks after it lands (refused). Then the watcher takes Kageno ("KAGENO", for skills to try), the caster calls
     * the watcher 4.5 blocks in front of it ("FAR") and uses the
     * Star Finger (T) on it: both log the Star Finger they hear, when its sound starts on their clock and whom it struck;
     * the watcher, struck, tries the attack, the dash (C) and E (all refused: incapacitated), walks forward for 25 ticks
     * (95% slower; its speed is logged) and tries the attack again once the 3 s are over (it swings); it logs its own
     * incapacitation and speed as its client knows them, the server as it keeps them. Since 2.61.0 the caster then turns
     * back (a tap of its guard turns its body to the camera, so the watcher it calls is where it then inhales), calls the
     * watcher 2.5 blocks in front of it again and inhales, holding Y down since 2.62.0 (3922 to 4012): the watcher is
     * caught, drawn in and held in front of the stand while the caster walks on (3942 to 3972), and, held, tries the
     * attack, the dash (C) and E and to walk away (all of it refused: held and incapacitated, 95% slower); Y let up, the
     * inhale ends at once and the watcher, let go, tries the attack 3 ticks after (refused: half a second more) and 14
     * ticks after (it swings). Then the caster inhales again (4060 to 4130) with the watcher holding its guard up (F,
     * for 40 ticks from when it hears that inhale start): the guard holds it off, and once the guard comes down the
     * inhale, still sucking, catches it; and once more, Y let up two ticks after it went down (4150): over at once,
     * before it sucks. Both log each inhale they hear (its start, its sound starting on their clock, whom it caught,
     * the guards seen holding it off, its end), the watcher whether it is held and where it is from the caster, the
     * server the same as it keeps them and every blow on the watcher (none).
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
        "3230 STAND","3270 Q","3315 E+","3330 W+","3380 W-","3405 E-","3440 M1+","3503 M1-",
        "3560 HOME","3575 R","3650 HOME","3665 R","3730 KAGENO","3740 FAR","3755 T","3850 TURN180","3852 W+","3892 W-",
        "3900 TURN0","3902 F+","3906 F-","3912 HOME","3922 Y+","3942 W+","3972 W-","4012 Y-","4045 HOME","4060 Y+","4130 Y-",
        "4145 HOME","4150 Y+","4152 Y-","4175 Q","4225 END"};
    /**
     * Star Platinum's beatdown (2.64.0), on its own (MP_ONLY=beatdown): the caster takes Star Platinum, brings it out and
     * calls the watcher 2.5 blocks in front of it ("HOME"), at full health ("HEAL"), and uses G on it: the whole
     * cutscene (both log the beatdown they hear, its cutscene's sound starting, the camera and the bars, where the one
     * struck is shown; the server the watcher held, its health through it and where it is put down). Then G against the
     * watcher's guard ("GUARD+": the watcher holds F while the file OUT/guard is there): the guard breaks, no cutscene.
     * Then G on nobody (the watcher 8 blocks away, "AWAY"). Then G on the watcher brought down to a quarter of its
     * health ("HURT": /damage, 150 of 200): its blows leave it at one health and the fall kills it as it comes down;
     * the watcher respawns.
     */
    private static final String[] BEATDOWN_SCRIPT={
        "5 STAND","40 Q","90 HOME","100 HEAL","110 G",
        "400 HOME","405 HEAL","410 GUARD+","430 G","455 GUARD-",
        "520 AWAY","535 G",
        "600 HOME","605 HEAL","612 HURT","625 G",
        "935 Q","980 END"};
    /**
     * Star Platinum's time stop (2.67.0), on its own (MP_ONLY=timestop): the caster takes Star Platinum, brings it out,
     * calls the watcher 2.5 blocks in front of it ("HOME") at full health ("HEAL") and stops time (H): while time is
     * stopped it throws a whole combo at the watcher (the fourth throws) and a heavy punch; the watcher, stopped, tries
     * the attack, the dash, to walk and its guard (all held), and logs whether its screen is held still and every sound
     * that played meanwhile (only the time stop's own may). Time moves again: both log the harm that came to whom, the
     * server the watcher's health (one blow of all of it) and how far it flew. Then H again (refused: its cooldown runs).
     */
    private static final String[] TIMESTOP_SCRIPT={
        "5 STAND","40 Q","90 HOME","100 HEAL","110 H",
        "165 M1+","205 M1-","215 R",
        "330 HOME","340 H",
        "400 Q","440 END"};
    private static final String[] STEPS=ONLY.equals("beatdown")?BEATDOWN_SCRIPT:ONLY.equals("timestop")?TIMESTOP_SCRIPT:SCRIPT;
    /** Walking speed windows (script ticks, inclusive) and the lens's FOV windows (script ticks) logged by the caster. */
    private static final int[][] SPEED={{282,312},{330,360},{3340,3375}},LENS={{126,142},{312,340},{3320,3400}};
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
                    for(String step:STEPS){String[] s=step.split(" ");int at=Integer.parseInt(s[0]);if(age==at)act(s[1],true);if(age==at+1&&!s[1].endsWith("+")&&!s[1].endsWith("-"))act(s[1],false);}
                }else{
                    if((age%20==0||!AtomicClient.CASTS.isEmpty()&&age%5==0)&&StandClient.cinematic()==null)face();
                    walk();
                    if(age%20==0&&Files.exists(OUT.resolve("done"))){note("caster done");phase=3;age=0;}
                    if(guardUntil>0&&age>=guardUntil){key(GLFW_KEY_F,false);note("release F (guard)");guardUntil=0;}
                    // 2.64.0: the guard the caster's script asks for (the file OUT/guard), and a respawn once dead
                    boolean guard=Files.exists(OUT.resolve("guard"));
                    if(guard!=guardAsked){guardAsked=guard;key(GLFW_KEY_F,guard);note((guard?"press":"release")+" F (guard, asked by the caster)");}
                    if(!mc.player.isAlive()){if(++deadFor==40){note("dead: respawn requested");mc.player.requestRespawn();}}else deadFor=0;
                    if(age>(ONLY.equals("beatdown")?1600:ONLY.equals("timestop")?1000:4400)){note("ERROR watcher timeout");phase=3;age=0;}
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
        if(step.equals("STAND")){if(down){note("command botand progress stand @s star_platinum");mc.player.networkHandler.sendChatCommand("botand progress stand @s star_platinum");}return;}
        // 2.59.0: the watcher back 2.5 blocks in front of the caster, facing it, for the heavy punch
        // 2.60.0: the watcher takes Kageno (its skills, refused while the Star Finger has it incapacitated)
        if(step.equals("KAGENO")){if(down){String c="botand progress stand Watcher kageno";note("command "+c);mc.player.networkHandler.sendChatCommand(c);}return;}
        if(step.equals("HOME")||step.equals("FAR")||step.equals("AWAY")){if(down){String c="execute as Caster at @s rotated ~ 0 run tp Watcher ^ ^ ^"+(step.equals("FAR")?"4.5":step.equals("AWAY")?"8":"2.5")+" facing entity Caster feet";note("command "+c);mc.player.networkHandler.sendChatCommand(c);}return;}
        // 2.64.0: the watcher at full health, or brought down to a quarter of it; its guard asked for
        if(step.equals("HEAL")||step.equals("HURT")){if(down){String c=step.equals("HEAL")?"effect give Watcher minecraft:instant_health 1 20 true":"damage Watcher 150 minecraft:generic";note("command "+c);mc.player.networkHandler.sendChatCommand(c);}return;}
        if(step.startsWith("GUARD")){if(down){try{if(step.endsWith("+"))Files.writeString(OUT.resolve("guard"),"guard");else Files.deleteIfExists(OUT.resolve("guard"));}catch(Exception e){fail(e);}note("watcher's guard "+(step.endsWith("+")?"asked for":"let down"));}return;}
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
        // 2.51.0: each player's Star Platinum as this client sees it, and every half second while one is drawn how it
        // follows its user (how far it leans into the walk and how fast it goes)
        flag("own_stand",stand(mc.player));if(other!=null)flag("other_stand",stand(other));
        // 2.54.0: Star Platinum's punches heard here, whether each landed and on whom, and when its shout started
        punches();
        // 2.57.0: its rush heard here, and this player's own cries of pain (only the first blow on them cries out)
        rushes();flag("hurt_cries",hurtCries);
        // 2.61.0: its inhale heard here; held by it, the watcher tries everything and to walk away
        inhales();
        // 2.64.0: its beatdown heard here: its cutscene, its camera and bars here, where its one struck is shown
        beats();
        // 2.67.0: its time stop heard here; stopped, the watcher tries everything and its screen is held
        stops();
        boolean fast=mc.player.getVelocity().horizontalLength()>.6;
        if(fast&&!Boolean.TRUE.equals(flags.get("thrown")))note(String.format(Locale.ROOT,"thrown at %.2f blocks a tick",mc.player.getVelocity().horizontalLength()));
        flag("thrown",fast);
        if(age%10==0)for(var p:mc.world.getPlayers()){var look=dev.sangwoo.celestialfall.client.stand.StandPresence.shown(p.getUuid());
            if(look!=null)note(String.format(Locale.ROOT,"stand of %s drawn: out %.2f, solid %.2f, glow %.2f, aura %.2f on %d bodies, lean %.1f deg, speed %.2f blocks/s (%s)",p.getGameProfile().getName(),look.out,look.alpha,look.glow,
                dev.sangwoo.celestialfall.client.stand.StandPresence.aura(p.getUuid()),dev.sangwoo.celestialfall.client.stand.StandAuraLayer.drawnLast(),look.lean,look.speed,dev.sangwoo.celestialfall.client.stand.StandPresence.describe(p.getUuid())));}
        try{var f=dev.sangwoo.celestialfall.client.kageno.HitFeedback.class.getDeclaredField("NUMBERS");f.setAccessible(true);flag("damage_numbers",((Map<?,?>)f.get(null)).size());}catch(ReflectiveOperationException e){flag("damage_numbers","n/a");}
    }
    private final Map<String,int[]> heard=new HashMap<>(),rushHeard=new HashMap<>(),inhaleHeard=new HashMap<>();private int hurtCries,inhalesHeard;private boolean listening;
    /**
     * Each player's inhale heard here (2.61.0; held down since 2.62.0): as it starts, its sound starting (its tick on its
     * clock), each catch (whom), each guard seen holding it off, its end. The watcher holds its guard up for 40 ticks
     * against the second it hears; held, it tries the attack, the dash and E, and walks (W) for 25 ticks, and logs where
     * it is from the caster; let go, it tries the attack 3 ticks after (incapacitated half a second more) and 14 after.
     */
    private void inhales(){
        for(var p:mc.world.getPlayers()){
            var i=dev.sangwoo.celestialfall.client.stand.StandInhale.of(p.getUuid());if(i==null)continue;
            String key=p.getUuid()+"/"+i.start;int[] st=inhaleHeard.get(key);String who=p.getGameProfile().getName()+"'s inhale";
            if(st==null){inhaleHeard.put(key,st=new int[5]);inhalesHeard++;note(String.format(Locale.ROOT,"%s heard (server tick %d, its tick here %d)",who,i.start,i.ticksIn()));
                if(!caster&&inhalesHeard==2){key(GLFW_KEY_F,true);guardUntil=age+40;note("press F (guard) against inhale "+inhalesHeard);}}
            if(st[0]==0&&i.sound()!=null&&mc.getSoundManager().isPlaying(i.sound())){st[0]=1;note(String.format(Locale.ROOT,"%s: its sound started at its tick %d here",who,i.ticksIn()));}
            for(;st[1]<i.caught.size();st[1]++){
                var c=i.caught.get(st[1]);var e=mc.world.getEntityById(c.id);
                note(String.format(Locale.ROOT,"%s caught %s at %.2f s on its clock, heard at its tick %d here",who,e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(c.id),c.at,i.ticksIn()));
                if(!caster&&c.id==mc.player.getId()){tries.put(age+3,"M1");tries.put(age+10,"C");tries.put(age+17,"E");tries.put(age+20,"W+");tries.put(age+45,"W-");heldSince=age;st[3]=1;}
            }
            int seen=(int)i.blocks.stream().filter(b->b.at()*20>lastBlock(key)).count();
            if(seen>0){var last=i.blocks.get(i.blocks.size()-1);blockSeen.put(key,last.at()*20);
                var e=mc.world.getEntityById(last.id());note(String.format(Locale.ROOT,"%s held off by %s's guard at %.2f s on its clock",who,e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(last.id()),last.at()));}
            if(st[2]==0&&!i.on()){st[2]=1;note(String.format(Locale.ROOT,"%s ended at %.2f s on its clock here",who,i.ends()));
                // let go: incapacitated half a second more (the attack refused), then free (it swings)
                if(!caster&&st[3]==1){tries.put(age+3,"M1");tries.put(age+14,"M1");heldSince=-1;}}
        }
        flag("held_by_inhale",dev.sangwoo.celestialfall.client.stand.StandInhale.localHeld());
        // held: where this player is from the caster (ahead along the way it faces, to its right), every 10 ticks
        if(!caster&&heldSince>=0&&(age-heldSince)%10==0&&age-heldSince<=120){
            var c=mc.world.getPlayers().stream().filter(q->q!=mc.player).findFirst().orElse(null);
            if(c!=null){var d=mc.player.getPos().subtract(c.getPos());var f=net.minecraft.util.math.Vec3d.fromPolar(0,c.getHeadYaw());
                note(String.format(Locale.ROOT,"%d ticks after the catch: %.2f blocks ahead of the caster, %.2f to its right, %.2f up; held %s",age-heldSince,d.x*f.x+d.z*f.z,-d.x*f.z+d.z*f.x,d.y,dev.sangwoo.celestialfall.client.stand.StandInhale.localHeld()));}
        }
    }
    private int heldSince=-1;private final Map<String,Float> blockSeen=new HashMap<>();
    /** The clock (ticks) of the last guard seen holding this inhale off (-1 if none yet). */
    private float lastBlock(String key){return blockSeen.getOrDefault(key,-1f);}
    /** Each player's rush heard here (2.57.0): as it starts, its voice starting, every tenth blow (whom it struck, how loud its blows sound) and its end. */
    private void rushes(){
        if(!listening&&mc.getSoundManager()!=null){listening=true;mc.getSoundManager().registerListener((sound,set)->{if(sound.getId().getPath().equals("entity.player.hurt"))hurtCries++;});}
        for(var p:mc.world.getPlayers()){
            var r=dev.sangwoo.celestialfall.client.stand.StandRush.of(p.getUuid());if(r==null)continue;
            String key=p.getUuid()+"/"+r.start;int[] st=rushHeard.get(key);String who=p.getGameProfile().getName()+"'s rush";
            if(st==null){rushHeard.put(key,st=new int[3]);note(String.format(Locale.ROOT,"%s heard (server tick %d, its tick here %d)",who,r.start,r.ticksIn()));}
            if(st[0]==0&&r.voice()!=null&&mc.getSoundManager().isPlaying(r.voice())){st[0]=1;note(String.format(Locale.ROOT,"%s: its voice started at its tick %d here",who,r.ticksIn()));}
            if(r.blows>st[1]){
                var all=r.blowsHeard();var last=all.isEmpty()?null:all.get(all.size()-1);
                if(last!=null&&(r.blows==1||r.blows%10==0)){var names=new ArrayList<String>();
                    for(int id:last.ids()){var e=mc.world.getEntityById(id);names.add(e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(id));}
                    note(String.format(Locale.ROOT,"%s: blow %d heard at its tick %d here, struck %s, its blows sounding at %.2f",who,r.blows,r.ticksIn(),names,r.striking()));}
                st[1]=r.blows;
            }
            if(st[2]==0&&!r.on()){st[2]=1;note(String.format(Locale.ROOT,"%s ended at %.2f s on its clock here, %d blows heard",who,r.ends(),r.blows));}
        }
    }
    /** Each punch of every player heard here, as it is heard, as it lands, and as its shout starts (its tick on the punch's clock). */
    private void punches(){
        for(var p:mc.world.getPlayers())for(var seen:dev.sangwoo.celestialfall.client.stand.StandAttack.punches(p.getUuid())){
            String key=p.getUuid()+"/"+seen.start;int[] state=heard.get(key);
            String who=p.getGameProfile().getName()+"'s punch "+seen.punch.combo();
            if(state==null){heard.put(key,state=new int[5]);note(String.format(Locale.ROOT,"%s heard (server tick %d, its tick here %d)",who,seen.start,seen.ticksIn()));}
            if(state[0]==0&&seen.landed){state[0]=1;var names=new ArrayList<String>();
                for(int id:seen.struck){var e=mc.world.getEntityById(id);names.add(e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(id));}
                note(String.format(Locale.ROOT,"%s landed on %s at its tick %d here",who,names,seen.ticksIn()));}
            if(state[1]==0&&seen.voice()!=null&&mc.getSoundManager().isPlaying(seen.voice())){state[1]=1;note(String.format(Locale.ROOT,"%s: its shout started at its tick %d here",who,seen.ticksIn()));}
            // 2.56.0: the jab and the cross shout nothing; the hook's shout fades as the finisher's comes
            // (the heavy punch and the Star Finger are no punches of the combo: they have sounds of their own, logged below)
            if(state[3]==0&&seen.ticksIn()>=4&&!dev.sangwoo.celestialfall.stand.StarPlatinumRules.skill(seen.punch.combo())&&!dev.sangwoo.celestialfall.stand.StarPlatinumRules.shouts(seen.punch.combo())){state[3]=1;note(who+(seen.voice()==null?": no shout (by the rule)":": a shout, against the rule"));}
            if(state[4]==0&&seen.voice() instanceof dev.sangwoo.celestialfall.client.stand.StandAttack.Shout shout&&shout.fading()){state[4]=1;note(String.format(Locale.ROOT,"%s: its shout fading out at its tick %d here",who,seen.ticksIn()));}
            if(state[2]==0&&!Float.isNaN(seen.punch.stopped())){state[2]=1;note(who+" stopped short");}
            // 2.59.0: the heavy punch: its voice and its blow starting here, the blow's loudness once it lands, whose
            // guard it broke; the watcher holds its guard up against the second it hears
            if(seen.punch.combo()==dev.sangwoo.celestialfall.stand.StarPlatinumRules.HEAVY){
                int[] h=heavyHeard.computeIfAbsent(key,k->new int[4]);
                if(h[0]==0){h[0]=1;heavies++;if(!caster&&heavies==2){key(GLFW_KEY_F,true);guardUntil=age+40;note("press F (guard) against heavy punch "+heavies);}}
                if(h[1]==0&&seen.heavyVoice()!=null&&mc.getSoundManager().isPlaying(seen.heavyVoice())){h[1]=1;note(String.format(Locale.ROOT,"%s: its voice started at its tick %d here",who,seen.ticksIn()));}
                if(h[2]==0&&seen.heavyBlow()!=null&&mc.getSoundManager().isPlaying(seen.heavyBlow())){h[2]=1;note(String.format(Locale.ROOT,"%s: its blow started at its tick %d here",who,seen.ticksIn()));}
                if(h[3]==0&&seen.landed&&seen.ticksIn()>=dev.sangwoo.celestialfall.stand.StarPlatinumRules.CONTACT[seen.punch.combo()]+2){h[3]=1;var broke=new ArrayList<String>();
                    for(int id:seen.broke){var e=mc.world.getEntityById(id);broke.add(e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(id));}
                    note(String.format(Locale.ROOT,"%s: its blow sounding at %.2f at its tick %d here, broke the guard of %s",who,seen.heavyBlow()==null?-1:seen.heavyBlow().loudness(),seen.ticksIn(),broke));
                    // 2.60.0: its guard broken, the watcher is incapacitated: its attack is refused
                    if(!caster&&seen.broke.length>0)tries.put(age+3,"M1");}
            }
            // 2.60.0: the Star Finger: its sound starting here and whom it struck; the watcher, struck, tries everything
            if(seen.punch.combo()==dev.sangwoo.celestialfall.stand.StarPlatinumRules.FINGER){
                int[] h=fingerHeard.computeIfAbsent(key,k->new int[2]);
                if(h[0]==0&&seen.voice()!=null&&mc.getSoundManager().isPlaying(seen.voice())){h[0]=1;note(String.format(Locale.ROOT,"%s: its sound started at its tick %d here",who,seen.ticksIn()));}
                if(h[1]==0&&seen.landed){h[1]=1;
                    boolean me=false;for(int id:seen.struck)me|=id==mc.player.getId();
                    if(!caster&&me){tries.put(age+3,"M1");tries.put(age+10,"C");tries.put(age+17,"E");tries.put(age+20,"W+");tries.put(age+45,"W-");tries.put(age+68,"M1");
                        fingerWalk=new int[]{age+20,age+45};}}
            }
        }
        // this player's own guard as its client knows it (2.59.0: broken by the heavy punch)
        var mine=CombatClient.snapshot(mc.player.getUuid());flag("guard",mine.broken(mc.world.getTime())?"broken":mine.guard()?"up":"down");
        // 2.60.0: incapacitated as its client knows it, and its speed against its base
        var speed=mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        flag("incapacitated",mine.disabled(mc.world.getTime())||mine.broken(mc.world.getTime()));
        flag("speed",String.format(Locale.ROOT,"x%.2f",speed.getValue()/speed.getBaseValue()));
        // the watcher's tries while incapacitated (and once it is over), and how fast it walked
        if(!caster){
            String t=tries.remove(age);
            if(t!=null){note("try "+t+(dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView()?" (stopped in time)":mine.locked(mc.world.getTime())?" (incapacitated)":" (free)"));
                switch(t){case "M1"->{attack(true);release=age+1;}case "C"->{key(GLFW_KEY_C,true);releaseKey=GLFW_KEY_C;release=age+1;}case "E"->{key(GLFW_KEY_E,true);releaseKey=GLFW_KEY_E;release=age+1;}
                    case "W+"->key(GLFW_KEY_W,true);case "W-"->key(GLFW_KEY_W,false);case "F+"->key(GLFW_KEY_F,true);case "F-"->key(GLFW_KEY_F,false);default->{}}
                if(stoppedAt!=null&&(t.equals("W-")||t.equals("F-")))note(String.format(Locale.ROOT,"%.2f blocks from where time stopped it (its guard %s)",mc.player.getPos().distanceTo(stoppedAt),mine.guard()?"up":"down"));}
            if(age==release){attack(false);if(releaseKey!=0)key(releaseKey,false);releaseKey=0;}
            if(fingerWalk!=null){
                var p=mc.player.getPos();
                if(age==fingerWalk[0]){walkFrom=p;walkSteps.clear();}
                else if(age>fingerWalk[0]&&age<=fingerWalk[1]){if(age>fingerWalk[0]+4&&mc.player.isOnGround())walkSteps.add(Math.hypot(p.x-walkFrom.x,p.z-walkFrom.z));walkFrom=p;}
                if(age==fingerWalk[1]){var sorted=new ArrayList<>(walkSteps);sorted.sort(null);double median=sorted.isEmpty()?0:sorted.get(sorted.size()/2);
                    note(String.format(Locale.ROOT,"walking %.2f blocks/s while struck by the Star Finger (median of %d ticks)",median*20,sorted.size()));fingerWalk=null;}
            }
            boolean dashing=mine.dashStart()>mc.world.getTime()-8&&mine.dashStart()<=mc.world.getTime();
            flag("dashing",dashing);
        }
    }
    private final Map<String,int[]> heavyHeard=new HashMap<>(),fingerHeard=new HashMap<>();private int heavies,guardUntil;
    private boolean guardAsked;private int deadFor;private final Map<Object,int[]> beatHeard=new IdentityHashMap<>();
    /** Per beatdown here: its one struck's hurt time last seen, how often their body flashed red and when it last did (its clock). */
    private final Map<Object,float[]> beatFlash=new IdentityHashMap<>();
    /**
     * Each player's beatdown heard here (2.64.0): as it starts (its age here, whom it struck, its damage, where they come
     * down), its cutscene's sound starting (its tick on its clock here: StarPlatinumRules.BEAT_SOUND), where its one
     * struck is shown twice a second (ahead of its user along the blow and up, and where they really are), and its end
     * with how often the one struck's body flashed red here (a blow's hurt time) and when last (its clock: every blow
     * to the last, even those that find them at one health, StarPlatinumBeatdown.blow); and whether this client's
     * camera is the cutscene's, its black bars, the sky-blue glow of this player's body, and (2.65.0) whether this client
     * drew a body glowing sky blue.
     */
    private void beats(){
        for(var p:mc.world.getPlayers()){
            var b=dev.sangwoo.celestialfall.client.stand.StandBeatdown.of(p.getUuid());if(b==null)continue;
            int[] st=beatHeard.computeIfAbsent(b,k->new int[3]);String who=p.getGameProfile().getName()+"'s beatdown";float t=b.age(0);
            var v=mc.world.getEntityById(b.victim);
            if(st[0]==0){st[0]=1;note(String.format(Locale.ROOT,"%s heard at %.2f s on its clock here, on %s, its damage %.1f, they come down %.2f blocks ahead of its user",who,t,
                v instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(b.victim),b.damage,b.land.subtract(b.from).dotProduct(b.f)));}
            if(st[1]==0&&b.voice()!=null&&mc.getSoundManager().isPlaying(b.voice())){st[1]=1;note(String.format(Locale.ROOT,"%s: its cutscene's sound started at its tick %.0f here",who,t*20));}
            if(v!=null&&age%10==0&&!b.ended()){var off=dev.sangwoo.celestialfall.client.stand.StandBeatdown.offset(v,0);
                if(off!=null){var d=v.getPos().add(off).subtract(b.from);note(String.format(Locale.ROOT,"%s at %.2f s: its one struck shown %.2f blocks ahead, %.2f up (really %.2f ahead)",who,t,d.dotProduct(b.f),d.y,v.getPos().subtract(b.from).dotProduct(b.f)));}}
            float[] fl=beatFlash.computeIfAbsent(b,k->new float[]{0,0,Float.NaN});
            if(v instanceof net.minecraft.entity.LivingEntity l){if(l.hurtTime>fl[0]){fl[1]++;fl[2]=t;}fl[0]=l.hurtTime;}
            if(st[2]==0&&b.ended()){st[2]=1;note(String.format(Locale.ROOT,"%s %s at %.2f s on its clock here; its one struck flashed red %.0f times here, last at %.2f s",who,b.wasCut()?"cut short":"over",t,fl[1],fl[2]));}
        }
        flag("beat_camera",dev.sangwoo.celestialfall.client.stand.StandBeatdown.cinematic()!=null);
        float bars=dev.sangwoo.celestialfall.client.stand.StandBeatdown.bars(0);
        flag("beat_bars",bars<=0?"none":bars>=dev.sangwoo.celestialfall.client.stand.StandBeatdown.BAR-1e-4f?"full":"sliding");
        flag("flash",dev.sangwoo.celestialfall.client.CutsceneFlash.local(0)>0);
        // 2.65.0: whether this client drew any body glowing sky blue last frame (its own, or the other player's)
        flag("glowing",dev.sangwoo.celestialfall.client.stand.StandAuraLayer.glowedLast()>0);
    }
    private final Map<Object,int[]> stopHeard=new IdentityHashMap<>();private final Set<String> stillSounds=new LinkedHashSet<>();private boolean stillListening;private int stillTicks;
    private net.minecraft.util.math.Vec3d stoppedAt;
    /**
     * Each player's time stop heard here (2.67.0): as it is called (its tick here), its call's sound starting (its tick
     * on its clock: StarPlatinumRules.TS_SOUND), time stopping (whom it stopped), its end's sound starting, time moving
     * again (the harm that came to each); whether this client draws the other player held still; for the one it
     * stopped, whether its screen is held still and every sound that played meanwhile (only the time stop's own may).
     * Stopped, the watcher tries the attack, the dash, walking and its guard, and logs how far it moved.
     */
    private void stops(){
        if(!stillListening&&mc.getSoundManager()!=null){stillListening=true;
            mc.getSoundManager().registerListener((sound,set)->{if(dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView())stillSounds.add(sound.getId().toString());});}
        for(var p:mc.world.getPlayers()){
            var s=dev.sangwoo.celestialfall.client.stand.StandTimeStop.of(p.getUuid());if(s==null)continue;
            int[] st=stopHeard.computeIfAbsent(s,k->new int[5]);String who=p.getGameProfile().getName()+"'s time stop";
            if(st[0]==0){st[0]=1;note(String.format(Locale.ROOT,"%s heard (server tick %d, its tick here %d)",who,s.start(),s.ticksHere()));}
            if(st[1]==0&&s.voiceSound()!=null&&mc.getSoundManager().isPlaying(s.voiceSound())){st[1]=1;note(String.format(Locale.ROOT,"%s: its call's sound started at its tick %d here",who,s.ticksHere()));}
            if(st[2]==0&&s.timeStopped()){st[2]=1;var names=new ArrayList<String>();
                for(int id:s.whom()){var e=mc.world.getEntityById(id);names.add(e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(id));}
                note(String.format(Locale.ROOT,"%s: time stopped at its tick %d here, stopping %s",who,s.ticksHere(),names));
                if(!caster&&s.whom().contains(mc.player.getId())){tries.put(age+5,"M1");tries.put(age+12,"C");tries.put(age+20,"W+");tries.put(age+40,"W-");tries.put(age+50,"F+");tries.put(age+60,"F-");stoppedAt=mc.player.getPos();}}
            if(st[3]==0&&s.endSound()!=null&&mc.getSoundManager().isPlaying(s.endSound())){st[3]=1;note(String.format(Locale.ROOT,"%s: its end's sound started at its tick %d here",who,s.ticksHere()));}
            if(st[4]==0&&!Float.isNaN(s.endedAt())){st[4]=1;var owed=new ArrayList<String>();
                for(var e:s.owedHarm().entrySet()){var x=mc.world.getEntityById(e.getKey());owed.add(String.format(Locale.ROOT,"%s %.1f",x instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(e.getKey()),e.getValue()));}
                note(String.format(Locale.ROOT,"%s %s at %.2f s on its clock here (its tick %d); the harm owed: %s",who,s.wasCut()?"cut short":"over, time moves again",s.endedAt(),s.ticksHere(),owed));stoppedAt=null;}
        }
        var other=mc.world.getPlayers().stream().filter(q->q!=mc.player).findFirst().orElse(null);
        if(other!=null)flag("other_held_still",dev.sangwoo.celestialfall.client.stand.StandTimeStop.stopped(other));
        boolean view=dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView();
        flag("screen_held_still",view);
        if(view)stillTicks++;
        else if(stillTicks>0){note("while its screen was held ("+stillTicks+" ticks) the sounds that played: "+stillSounds);stillTicks=0;stillSounds.clear();}
    }
    private final Map<Integer,String> tries=new HashMap<>();private int release=-1,releaseKey;private int[] fingerWalk;
    private net.minecraft.util.math.Vec3d walkFrom;private final List<Double> walkSteps=new ArrayList<>();
    /** A player's stand as this client sees it: its id, out or in as the server tracks it, and the stand drawn here. */
    private String stand(net.minecraft.entity.player.PlayerEntity p){
        var h=(dev.sangwoo.celestialfall.stand.StandHolder)p;var st=dev.sangwoo.celestialfall.client.stand.StandPresence.state(p.getUuid());
        String shown=st==null?"none":st[1]==0?"going back":st[0]<dev.sangwoo.celestialfall.client.stand.StandTimeline.SETTLED?"coming out":"at rest";
        return h.botand$getStand()+(h.botand$isStandOut()?" out":" in")+", here "+shown+(dev.sangwoo.celestialfall.client.stand.StandPresence.shown(p.getUuid())!=null?" (drawn)":"");
    }
    private void shot(){
        var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());frames++;
        Path p=dir().resolve(String.format(Locale.ROOT,"%06d.png",mc.world.getTime()));
        writer.submit(()->{try(image){Files.createDirectories(p.getParent());image.writeTo(p);}catch(Exception e){throw new RuntimeException(e);}});
    }
}
