package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.title.BotandTitleScreen;
import dev.sangwoo.celestialfall.stand.*;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.map.*;
import dev.sangwoo.celestialfall.combat.CombatManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.network.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.block.Blocks;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.lwjgl.glfw.GLFW.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** One native client receiving genuine server packets, tested as caster AND as affected victim. */
public final class KagenoCapture implements ClientModInitializer {
    private static KagenoCapture instance;
    private final java.util.concurrent.ExecutorService imageWriter=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var thread=new Thread(r,"kageno-proof-writer");thread.setDaemon(true);return thread;});private final java.util.List<java.util.concurrent.Future<?>> imageJobs=new java.util.ArrayList<>();private final java.util.Set<String> shotNames=new java.util.HashSet<>();
    private final java.util.Queue<String> pendingShots=new java.util.ArrayDeque<>();
    public static void afterFrame(){if(instance==null)return;try{instance.traceFrame();while(!instance.pendingShots.isEmpty())instance.saveFrame(instance.pendingShots.remove());}catch(Throwable t){t.printStackTrace();try{Files.writeString(OUT.resolve("client-error.txt"),"frame phase="+instance.phase+" "+t+"\n"+instance.results);}catch(Exception ignored){}try{if(instance.mc.getServer()!=null&&instance.mc.player!=null)instance.server(instance::cleanup);}catch(Exception ignored){}instance.mc.scheduleStop();instance.phase=99;}}
    private void traceFrame(){
        var c=StandClient.cinematic();if(c==null)return;float a=c.age(mc.getTickDelta());long now=System.nanoTime();
        if(c.id().equals(lastH)){check(a+.001f>=lastHAge,"H rendered clock never rewinds");hMaxGap=Math.max(hMaxGap,(now-lastFrameNanos)/1e6);}
        if(!c.id().equals(lastH))phaseTimes.append("KAGENO QA H onset at ").append(java.time.Instant.now()).append(" age ").append(a).append("\n");
        lastH=c.id();lastHAge=a;lastFrameNanos=now;hFrames++;
        var camera=mc.gameRenderer.getCamera();var p=camera.getPos();cameraTrace.append(a).append(',').append(p.x).append(',').append(p.y).append(',').append(p.z).append(',').append(camera.getYaw()).append(',').append(camera.getPitch()).append(',').append(now).append('\n');
    }
    private static final Path OUT=Path.of(System.getProperty("botand.kageno.proof"));
    private MinecraftClient mc;private int phase,age;private long started=System.nanoTime();private float health;private boolean frame;private boolean clockChecked;private UUID lastH;private float lastHAge=-1;private int hFrames;private double hMaxGap;private final StringBuilder cameraTrace=new StringBuilder("age,x,y,z,yaw,pitch,frame_ns\n");private long lastFrameNanos;
    private double walkSpeed;private Vec3d walkStart,atomicOrigin;private final Map<BlockPos,net.minecraft.block.BlockState> terrainBefore=new HashMap<>();
    private void snapshotTerrain(ServerPlayerEntity p){atomicOrigin=p.getPos();terrainBefore.clear();for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++)for(int y=76;y<=82;y++){var at=new BlockPos(x,y,z);terrainBefore.put(at,p.getWorld().getBlockState(at));}}
    private void unchangedTerrain(ServerPlayerEntity p){for(var entry:terrainBefore.entrySet())if(!p.getWorld().getBlockState(entry.getKey()).equals(entry.getValue()))throw new IllegalStateException("G changed block "+entry.getKey());check(true,"G preserves all "+terrainBefore.size()+" sampled terrain blocks");}
    private final List<ServerPlayerEntity> actors=new ArrayList<>();private final StringBuilder results=new StringBuilder();
    private synchronized void check(boolean ok,String name){if(!ok)throw new IllegalStateException(name);results.append("PASS ").append(name).append('\n');}
    private final StringBuilder phaseTimes=new StringBuilder();
    private void step(){jump(phase+1);}
    private void jump(int next){phase=next;age=0;frame=false;String event="KAGENO QA phase "+phase+" at "+java.time.Instant.now();phaseTimes.append(event).append("\n");System.out.println(event);}
    private net.minecraft.client.sound.SoundInstance qTheme;
    private final Set<Integer> basicSeen=new HashSet<>();
    private net.minecraft.client.sound.SoundInstance theme()throws Exception{var f=StandClient.class.getDeclaredField("theme");f.setAccessible(true);return (net.minecraft.client.sound.SoundInstance)f.get(null);}
    private void server(Consumer<ServerPlayerEntity> f){mc.getServer().submit(()->f.accept(mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid()))).join();}
    private void key(int key,boolean down){mc.keyboard.onKey(mc.getWindow().getHandle(),key,0,down?GLFW_PRESS:GLFW_RELEASE,0);}
    private void tap(int key){key(key,true);key(key,false);}
    private void shot(String name){if(shotNames.add(name))pendingShots.add(name);}
    private void finishImages()throws Exception{for(var job:imageJobs)job.get();imageWriter.shutdown();}
    private void saveFrame(String name)throws Exception{
        var failed=AnimePostProcessor.class.getDeclaredField("failed");failed.setAccessible(true);check(!failed.getBoolean(null),"post shader active at "+name);
        var im=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
        imageJobs.add(imageWriter.submit(()->{try(im){
            im.writeTo(OUT.resolve(name+".png"));
            if(name.contains("Monochrome")){int spread=0;for(int y=0;y<im.getHeight();y+=3)for(int x=0;x<im.getWidth();x+=3){int pixel=im.getColor(x,y),r0=pixel&255,g0=pixel>>8&255,b0=pixel>>16&255;spread=Math.max(spread,Math.max(r0,Math.max(g0,b0))-Math.min(r0,Math.min(g0,b0)));}check(spread<=1,"framebuffer is genuinely grayscale "+name);}
            if(name.equals("Kageno_G_Inside_Violet_Shake")){
                int violet=0,total=0;for(int y=0;y<im.getHeight();y+=4)for(int x=0;x<im.getWidth();x+=4){int pixel=im.getColor(x,y),red=pixel&255,green=pixel>>8&255,blue=pixel>>16&255;total++;if(blue>85&&red>10&&blue>red*1.3&&green<65)violet++;}
                check(violet>total*.85,"G actual framebuffer is covered by violet energy inside the blast");
            }
            check(Files.size(OUT.resolve(name+".png"))>10000,"native frame "+name);
        }catch(Exception e){throw new RuntimeException(e);}}));
    }
    private void stand(ServerPlayerEntity p,boolean value){ProgressManager.set(p,new ProgressProfile(1,0,0,value?"kageno":"none",10,0));p.setHealth(p.getMaxHealth());}
    @SuppressWarnings("unchecked") private ServerPlayerEntity actor(ServerPlayerEntity owner,String name,double x,double z){try{
        var server=owner.getServer();var w=owner.getServerWorld();var p=new ServerPlayerEntity(server,w,new GameProfile(UUID.randomUUID(),name));
        p.networkHandler=new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),p);p.changeGameMode(GameMode.SURVIVAL);p.setPosition(x,81,z);p.setYaw(180);p.setHeadYaw(180);p.setOnGround(true);
        var field=ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");field.setAccessible(true);field.setInt(p,0);
        field=net.minecraft.server.PlayerManager.class.getDeclaredField("playerMap");field.setAccessible(true);((Map<UUID,ServerPlayerEntity>)field.get(server.getPlayerManager())).put(p.getUuid(),p);
        server.getPlayerManager().getPlayerList().add(p);
        owner.networkHandler.sendPacket(new PlayerListS2CPacket(EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER,PlayerListS2CPacket.Action.UPDATE_GAME_MODE),List.of(p)));
        w.onPlayerConnected(p);stand(p,false);p.setHealth(200);actors.add(p);return p;
    }catch(Exception e){throw new RuntimeException(e);}}
    private void reset(ServerPlayerEntity p){KagenoManager.clear(p.getServer());AtomicManager.clear(p.getServer());p.teleport(p.getServerWorld(),.5,81,.5,180,0);p.setOnGround(true);p.setHealth(p.getMaxHealth());p.setVelocity(Vec3d.ZERO);for(int i=0;i<actors.size();i++){var a=actors.get(i);a.setHealth(a.getMaxHealth());a.setVelocity(Vec3d.ZERO);a.timeUntilRegen=0;double[][] pos={{.5,-2.5},{3.501,.5},{5.5,.5},{5.501,1.5}};a.setPosition(pos[i][0],81,pos[i][1]);}NoCooldownState.get(p.getServer()).set(p.getUuid(),true);}
    private StandClient.Cast local(int skill){return StandClient.CASTS.values().stream().filter(c->c.skill()==skill).findFirst().orElse(null);}
    @SuppressWarnings("unchecked") private void cleanup(ServerPlayerEntity p){try{KagenoManager.clear(p.getServer());var pm=p.getServer().getPlayerManager();var f=net.minecraft.server.PlayerManager.class.getDeclaredField("playerMap");f.setAccessible(true);var map=(Map<UUID,ServerPlayerEntity>)f.get(pm);for(var a:actors){map.remove(a.getUuid());pm.getPlayerList().remove(a);a.discard();}actors.clear();}catch(Exception e){throw new RuntimeException(e);}}
    public void onInitializeClient(){instance=this;ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{
        if((System.nanoTime()-started)/1e9>350)throw new IllegalStateException("timeout phase "+phase);age++;mc.getToastManager().clear();
        switch(phase){
        case 0 -> {if(mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-started)/1e9>5){Files.createDirectories(OUT);mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);mc.options.getViewDistance().setValue(5);mc.options.getGuiScale().setValue(2);mc.options.getMaxFps().setValue(60);mc.options.hudHidden=false;mc.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.FAST);mc.createIntegratedServerLoader().start(new TitleScreen(),System.getProperty("botand.kageno.world"));step();}}
        case 1 -> {if(mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);step();}}
        case 2 -> {if(mc.currentScreen==null&&ProgressClient.ready){server(p->{p.getServer().setPvpEnabled(true);p.changeGameMode(GameMode.SURVIVAL);p.getServerWorld().setTimeOfDay(1000);p.getWorld().getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,p.getServer());
            for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++){p.getWorld().setBlockState(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.getDefaultState());for(int y=81;y<112;y++)p.getWorld().setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());}
            p.getInventory().clear();p.getInventory().setStack(0,new ItemStack(Items.STONE,17));p.getInventory().selectedSlot=0;p.currentScreenHandler.syncState();stand(p,true);reset(p);
            actor(p,"Near3",.5,-2.5);actor(p,"Outside3",3.501,.5);actor(p,"Edge5",5.5,.5);actor(p,"Outside5",5.501,1.5);
            check(KagenoManager.radiusTargets(p,3).contains(actors.get(0))&&!KagenoManager.radiusTargets(p,3).contains(actors.get(1)),"server 3-block inclusive radius");
            check(KagenoManager.radiusTargets(p,5).contains(actors.get(2))&&!KagenoManager.radiusTargets(p,5).contains(actors.get(3)),"server 5-block inclusive radius");
        });step();}}
        case 3 -> {if(age>40&&StandClient.visible(mc.player)){if(Boolean.getBoolean("botand.kageno.highest")){BotandCamera.STATE.targetDistance=5;BotandCamera.STATE.yaw=180;BotandCamera.STATE.pitch=12;mc.options.hudHidden=true;tap(GLFW_KEY_H);jump(7);}else if(Boolean.getBoolean("botand.kageno.atomic")){BotandCamera.STATE.targetDistance=5;BotandCamera.STATE.yaw=40;BotandCamera.STATE.pitch=12;mc.options.hudHidden=true;server(this::snapshotTerrain);tap(GLFW_KEY_Q);jump(49);}else {BotandCamera.STATE.targetDistance=0;step();}}}
        case 4 -> {if(age>25){shot("Kageno_Sword_FirstPerson");BotandCamera.STATE.targetDistance=5;BotandCamera.STATE.yaw=40;BotandCamera.STATE.pitch=12;step();}}
        case 5 -> {if(age==7)key(GLFW_KEY_F,true);if(age==18){shot("Kageno_F_Guard");check(CombatClient.snapshot(mc.player.getUuid()).guard(),"F guard remains available with arbitrary selected item");check(Math.abs(mc.player.getFovMultiplier()-1)<.0001,"F guard does not zoom the lens");}if(age==20)key(GLFW_KEY_F,false);if(age>28){shot("Kageno_Sword_ThirdPerson");server(p->{NoCooldownState.get(p.getServer()).set(p.getUuid(),false);walkSpeed=p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);walkStart=p.getPos();});mc.options.hudHidden=true;tap(GLFW_KEY_R);step();}}
        case 6 -> {var c=local(R);if(c!=null&&(int)c.age(0)==32)shot("Kageno_R_Frozen_Crosscuts_Monochrome");if(c!=null&&(int)c.age(0)==37)shot("Kageno_R_Release_Burst");if(c!=null&&c.age(0)>=12&&c.age(0)<25&&!frame){check(StandClient.monochrome()&&StandClient.locked(),"caster receives R monochrome and lock");server(p->{
            check(!KagenoManager.frozen(p)&&KagenoManager.frozen(actors.get(0))&&!KagenoManager.frozen(actors.get(1)),"R caster can walk while only radius targets freeze");check(Math.abs(p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)-walkSpeed*.5)<.00001,"R server walk speed is exactly 50 percent");
            var target=actors.get(0);Vec3d before=target.getPos();target.setVelocity(.2,-.4,.2);target.travel(new Vec3d(1,0,1));check(target.getPos().equals(before),"R blocks gravity and travel");target.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.Full(before.x+2,before.y-1,before.z+2,90,30,false));check(target.getPos().equals(before),"R rejects malicious movement packet");
            check(CombatManager.actionLocked(target)&&!KagenoManager.input(p,H),"R blocks attacks and overlapping casts");
        });check(!StandClient.movementLocked(),"R client movement remains enabled while actions are locked");key(GLFW_KEY_W,true);shot("Kageno_R_Caster_Monochrome");frame=true;}
            if(age==40)key(GLFW_KEY_W,false);if(age>65){key(GLFW_KEY_W,false);check(frame,"R captured within freeze interval");server(p->{check(p.getPos().distanceTo(walkStart)>.5,"R caster actually walks during skill");check(Math.abs(p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)-walkSpeed)<.00001,"R restores walking speed after completion");check(Math.abs(actors.get(0).getHealth()-179)<.01,"R deals all five hits total 21");check(actors.get(1).getHealth()==200,"R outside target untouched");check(!KagenoManager.frozen(p)&&!KagenoManager.frozen(actors.get(0)),"R releases every participant");check(!KagenoManager.input(p,R),"R cooldown rejects replay after recovery");reset(p);});tap(GLFW_KEY_H);step();}}
        case 7 -> {var c=local(H);if(c!=null)for(int at:new int[]{10,16,20,27,40,56,59,63,66,69,72,74,78,82,85,89,93,96})if((int)c.age(0)==at)shot("Kageno_H_Pose_"+at);if(c!=null&&c.age(0)>40&&!clockChecked){float before=c.age(0);StandClient.acceptCast(new StandClient.Cast(c.id(),c.caster(),c.dimension(),c.skill(),c.combo(),c.start(),c.start()+3,0,c.origin(),c.yaw(),c.targets()));check(Math.abs(local(H).age(0)-before)<.001,"H delayed snapshot cannot reset animation clock");clockChecked=true;}if(c!=null&&age%16==0&&c.age(0)<116)shot("H_"+String.format("%03d",(int)c.age(0)));if(c!=null&&c.age(0)>=30&&!frame){check(StandClient.cinematic()!=null,"H caster camera starts");
                var audio=Class.forName("dev.sangwoo.celestialfall.client.KagenoAudio");var field=audio.getDeclaredField("PLAYING");field.setAccessible(true);
                var sound=(net.minecraft.client.sound.SoundInstance)((Map<?,?>)field.get(null)).get(c.id());
                check(sound!=null&&mc.getSoundManager().isPlaying(sound)&&sound.getPitch()==1,"H original musical cue plays continuously at 1x");server(p->{var cast=KagenoManager.casts().iterator().next();check(cast.targets.size()==3&&cast.targets.containsKey(actors.get(2)),"H captures all three players through 5-block boundary");});shot("Kageno_H_Sky_Caster");frame=true;}if(c!=null&&c.age(0)>84&&c.age(0)<88)shot("Kageno_H_Slashes_Caster");
            if(age>140){if(Boolean.getBoolean("botand.kageno.highest")){finishImages();Files.writeString(OUT.resolve("phase-times.txt"),phaseTimes);Files.writeString(OUT.resolve("h-camera-frames.csv"),cameraTrace);Files.writeString(OUT.resolve("h-review-result.txt"),results);server(this::cleanup);mc.scheduleStop();phase=99;break;}server(p->{for(int i=0;i<3;i++)check(Math.abs(actors.get(i).getHealth()-168)<.01,"H multi-player damage target "+i);check(actors.get(3).getHealth()==200,"H outside 5 untouched");reset(p);stand(p,false);var caster=actors.get(0);caster.setPosition(.5,81,-1.5);stand(caster,true);NoCooldownState.get(p.getServer()).set(caster.getUuid(),true);check(KagenoManager.input(caster,H),"remote actor H accepted");});step();}}
        case 8 -> {var c=local(H);if(c!=null&&c.age(0)>30&&!frame){check(!StandClient.visible(mc.player)&&StandClient.cinematic()!=null&&StandClient.locked(),"non-Kageno victim receives synchronized H camera and lock");check(c.includes(mc.player.getUuid()),"victim included in server target packet");shot("Kageno_H_Victim_Camera");frame=true;}if(age>140){server(p->{check(Math.abs(p.getHealth()-168)<.01,"H victim receives 32 damage");reset(p);check(KagenoManager.input(actors.get(0),R),"remote actor R accepted");});step();}}
        case 9 -> {var c=local(R);if(c!=null&&c.age(0)>12&&c.age(0)<25&&!frame){check(StandClient.monochrome()&&StandClient.locked(),"non-Kageno victim receives R grayscale");shot("Kageno_R_Victim_Monochrome");frame=true;}if(age>65){check(frame,"victim R captured");server(p->{check(!KagenoManager.frozen(p),"victim R releases");reset(p);stand(actors.get(0),false);stand(p,true);actors.get(0).setPosition(.5,81,-1.5);});step();}}
        case 10 -> {if(age>20){BotandCamera.STATE.targetDistance=9;key(GLFW_KEY_T,true);step();}}
        case 11 -> {if(age%8==0&&local(T)!=null)shot("T_"+String.format("%03d",(int)local(T).age(0)));if(age==1)key(GLFW_KEY_T,false);var c=local(T);if(c!=null&&c.age(0)>30&&!frame){check(mc.currentScreen==null,"T does not open chat");shot("Kageno_T_Ground_Fire");check(local(RUSH)==null,"short T produces only ground fire");frame=true;}if(age>115){check(frame,"tap T activated");server(p->{check(Math.abs(actors.get(0).getHealth()-149)<.01,"T ground fire deals 13 hits total 51");reset(p);actors.get(0).setPosition(.5,81,-1.5);});key(GLFW_KEY_T,true);step();}}
        case 12 -> {var c=local(RUSH);if(c!=null)for(int at:new int[]{8,12,20,30,34,40,46,50,54,60})if((int)c.age(0)==at)shot("Kageno_T_Held_"+at);if(c!=null&&c.age(0)>42&&!frame){shot("Kageno_T_Held_Rush");check(local(T)==null,"held T does not also cast ground fire");frame=true;key(GLFW_KEY_T,false);}if(age>95){check(frame,"held T activated automatically");server(p->{check(p.getPos().distanceTo(new Vec3d(.5,81,.5))>2,"held T actually moves on server");check(Math.abs(actors.get(0).getHealth()-150)<.01,"held T carries target and deals six hits total 50");reset(p);actors.get(1).setPosition(2.5,81,.5);actors.get(2).setPosition(.5,81,2.5);});tap(GLFW_KEY_E);step();}}
        case 13 -> {BotandCamera.STATE.targetDistance=5;var c=local(E);if(c!=null&&c.age(0)<4){BotandCamera.STATE.yaw=40;BotandCamera.STATE.pitch=12;}if(c!=null&&(int)c.age(0)==7)shot("Kageno_E_Windup");if(c!=null&&(int)c.age(0)==10)shot("Kageno_E_Forward_Thrust");if(c!=null&&(int)c.age(0)==52)shot("Kageno_E_Finish");if(c!=null&&c.age(0)>16&&!frame){check(mc.currentScreen==null,"E casts without opening inventory");shot("Kageno_E_Barrage");frame=true;}if(age>85){check(frame,"E activated");server(p->{check(Math.abs(actors.get(0).getHealth()-174)<.01,"E deals eight hits total 26");check(actors.get(1).getHealth()==200&&actors.get(2).getHealth()==200,"E thrust excludes nearby side and rear targets");reset(p);actors.get(0).setPosition(.5,81,-1.5);});tap(GLFW_KEY_Y);step();}}
        case 14 -> {var c=local(Y);if(c!=null&&c.age(0)>22&&!frame){shot("Kageno_Y_Stomp");frame=true;}if(age>65){check(frame,"Y activated");server(p->{check(Math.abs(actors.get(0).getHealth()-184)<.01,"Y deals two hits total 16");reset(p);actors.get(0).setPosition(.5,81,-1.5);});BotandCamera.STATE.yaw=180;mc.options.attackKey.setPressed(true);step();}}
        case 15 -> {var c=StandClient.action(mc.player.getUuid());if(c!=null&&c.skill()==M1){if(basicSeen.add(c.combo())){phaseTimes.append("KAGENO QA M1 ").append(c.combo()).append(" at ").append(java.time.Instant.now()).append(" age ").append(c.age(0)).append("\n");}if(c.age(0)>=5&&c.age(0)<8)shot("Kageno_M1_Stroke_"+c.combo());}if(c!=null&&c.combo()==4&&c.age(0)>7&&!frame){mc.options.attackKey.setPressed(false);shot("Kageno_M1_Fourth_Slash");frame=true;}if(age>78){mc.options.attackKey.setPressed(false);check(frame,"M1 completes four-hit combo");server(p->{check(Math.abs(actors.get(0).getHealth()-172)<.01,"M1 total damage 28");reset(p);});tap(GLFW_KEY_Q);step();}}
        case 16 -> {if(age>25){var s=StandClient.STATES.get(mc.player.getUuid());check(s!=null&&s.until()>mc.world.getTime()&&s.boost()>=.05,"Q overdrive state synchronized");shot("Kageno_Q_Overdrive");server(p->{check(p.getMainHandStack().isOf(Items.STONE)&&p.getMainHandStack().getCount()==17,"all skills preserve selected item");check(!KagenoManager.input(p,Q),"Q cannot stack/restart active music");p.setYaw(180);check(KagenoManager.input(p,M1),"overdrive attack starts");});step();}}
        case 17 -> {if(age>25){var s=StandClient.STATES.get(mc.player.getUuid());check(s!=null&&s.boost()>.05&&!s.marks().isEmpty(),"Q hit grows power and synchronizes shadow mark");server(p->{var target=actors.get(0);float before=target.getHealth();target.timeUntilRegen=0;target.damage(p.getWorld().getDamageSources().generic(),10);check(Math.abs(before-target.getHealth()-11.5)<.01,"shadow mark amplifies damage from other sources exactly once");});tap(GLFW_KEY_G);step();}}
        case 18 -> {if(age>20){server(p->{check(AtomicManager.isCasting(p.getUuid()),"G starts atomic from arbitrary held item");AtomicManager.clear(p.getServer());reset(p);stand(p,false);check(!KagenoManager.input(p,H),"server rejects unowned skill");stand(p,true);check(KagenoManager.input(p,H),"cleanup fixture starts");MapGate.designate(p.getServerWorld(),false);});step();}}
        case 49 -> {if(age>30){qTheme=theme();check(qTheme!=null&&mc.getSoundManager().isPlaying(qTheme),"Q theme is playing before G");check(qTheme.getCategory()==net.minecraft.sound.SoundCategory.RECORDS,"Q theme owns independent RECORDS channel");tap(GLFW_KEY_G);jump(50);}}
        case 50 -> {
            if(age==20){
                var cast=AtomicClient.nearest();var af=cast.getClass().getDeclaredField("audio");af.setAccessible(true);var sequence=af.get(cast);
                var sf=sequence.getClass().getDeclaredField("playing");sf.setAccessible(true);var sound=(net.minecraft.client.sound.SoundInstance)sf.get(sequence);
                check(sound!=null&&sound.getId().getPath().equals("atomic_full")&&mc.getSoundManager().isPlaying(sound),"G original spoken audio actually starts in SoundManager");
                check(sound.getPitch()==1&&sound.getVolume()>0,"G dialogue plays at original speed and nonzero volume");
            }
            if(age==20||age==128||age==240||age==450){check(theme()==qTheme,"Q theme instance is unchanged at G tick "+age);check(mc.getSoundManager().isPlaying(qTheme),"Q theme stays playing at G tick "+age);}
            if(age>0&&age<468&&age%16==0)shot("G_"+String.format("%03d",age));
            if(age==30){check(AtomicClient.cinematic()==null&&!CinematicDirector.isActive(),"G never takes over gameplay camera");check(StandClient.movementLocked(),"G client movement is locked");key(GLFW_KEY_W,true);key(GLFW_KEY_SPACE,true);server(p->{var before=p.getPos();p.setVelocity(.2,.4,.2);p.travel(new Vec3d(1,0,1));check(p.getPos().equals(before),"G blocks server travel");p.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.Full(before.x+2,before.y+1,before.z+2,90,30,false));check(p.getPos().equals(before),"G rejects movement packets during entire cast");});}
            if(age==80){check(AtomicClient.poseCast(mc.player.getUuid())!=null,"G body pose is active without a cinematic camera");shot("Kageno_G_No_Cutscene");shot("Kageno_G_Raised_Sword_Pose");frame=true;}
            if(age==180){key(GLFW_KEY_W,false);key(GLFW_KEY_SPACE,false);check(AtomicClient.cinematic()==null&&!CinematicDirector.isActive(),"G impact preserves ordinary camera");server(p->{check(p.getPos().distanceTo(atomicOrigin)<.001,"G caster cannot move or jump");unchangedTerrain(p);});BotandCamera.STATE.yaw=225;BotandCamera.STATE.pitch=25;}
            if(age==210){
                check(Math.abs(BotandCamera.STATE.yaw-225)<.1,"G preserves the manually selected camera base yaw");
                check(Math.abs(mc.gameRenderer.getCamera().getYaw()-225)<5,"G applies only bounded shake around manual camera rotation");
                check(AtomicImmersion.at(mc.gameRenderer.getCamera().getPos(),mc.getTickDelta()).violet()>.95,"G inside camera receives full violet exposure");
                shot("Kageno_G_Inside_Violet_Shake");shot("Kageno_G_Visual_Crater");
            }
            if(age>490){server(p->{check(!AtomicManager.isCasting(p.getUuid()),"G finishes and releases after cast duration");check(!KagenoManager.frozen(p),"G movement lock releases");unchangedTerrain(p);check(frame,"G normal gameplay view captured");AtomicManager.clear(p.getServer());cleanup(p);});finishImages();Files.writeString(OUT.resolve("phase-times.txt"),phaseTimes);Files.writeString(OUT.resolve("atomic-result.txt"),results.toString());mc.scheduleStop();phase=99;}}
        case 19 -> {if(age>20){check(StandClient.CASTS.isEmpty()&&!StandClient.monochrome()&&StandClient.cinematic()==null,"map exit clears camera monochrome and casts");server(p->{check(KagenoManager.casts().isEmpty(),"map exit clears server locks");MapGate.designate(p.getServerWorld(),true);cleanup(p);});finishImages();Files.writeString(OUT.resolve("phase-times.txt"),phaseTimes);Files.writeString(OUT.resolve("h-camera-frames.csv"),cameraTrace);Files.writeString(OUT.resolve("h-camera-summary.txt"),"rendered_frames="+hFrames+"\nmaximum_frame_gap_ms="+hMaxGap+"\n");check(clockChecked&&hFrames>100,"H camera continuity captured for caster and victim");Files.writeString(OUT.resolve("client-result.txt"),results.toString());mc.scheduleStop();phase=99;}}
        }
    }catch(Throwable t){t.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("client-error.txt"),"phase="+phase+" "+t+"\n"+results);if(mc.getServer()!=null&&mc.player!=null)server(this::cleanup);}catch(Exception ignored){}mc.scheduleStop();phase=99;}});}
}
