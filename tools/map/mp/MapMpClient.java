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
 * townspeople it can see, and saves a frame looking each way from the spawn in the middle of the town hall's square
 * (south to the merchants' stalls and the bridge, west to the park, north up the shopping street to the shrine's hill),
 * the game's HUD showing. Then the meteor check (2.49.0): it says "meteor-ready" in the chat, turns east to where the
 * server drops a meteor, logs the fall as this client draws it (from its own fall list), the rock it then sees (with its
 * fragments and blows as they change) and every status the rock sends (a blow, a fragment off, mined out); holding the
 * meteor pickaxe beside a rock it mines it as a player would (aiming at it and clicking every seven ticks), and it logs
 * every swing it sees another player make and whether it shows them holding the pickaxe in both hands (2.49.1), and every
 * meteor sound it plays (2.49.2: the fall's recording, the landing, the blows). It saves
 * a frame just after the landing and one after the rock is gone; then leaves.
 */
public final class MapMpClient implements ClientModInitializer {
    static final String ROLE=System.getProperty("botand.mapmp.role");
    static final Path OUT=Path.of(System.getProperty("botand.mapmp.out","mapmp"));
    static final String ADDRESS=System.getProperty("botand.mapmp.address","127.0.0.1:25565");
    /** Where to look from the spawn: yaw, pitch, name. */
    static final Object[][] VIEWS={{0f,2f,"south"},{90f,4f,"west"},{180f,4f,"north"}};
    private MinecraftClient mc;private int phase,age,view,settle,built=-1;
    private int fallsSeen=-1,rockAge=-1,goneAge=-1,lastSwing=-100;private String rockSeen="";private boolean wrapped,impactShot,listening;
    private final StringBuilder log=new StringBuilder();
    private final java.util.Map<java.util.UUID,Boolean> swinging=new java.util.HashMap<>(),twoHanded=new java.util.HashMap<>();

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
                if(!listening){listening=true;
                    // 2.49.2: every meteor sound this client plays (the fall's recording and the landing among them), from
                    // the moment it is in the world: a fall can reach it before its meteor check begins
                    mc.getSoundManager().registerListener((sound,set)->{var id=sound.getId();if(id.getNamespace().equals("celestialfall")&&id.getPath().startsWith("meteor_"))
                        note(String.format("plays %s from %.0f,%.0f,%.0f at %d of phase %d",id.getPath(),sound.getX(),sound.getY(),sound.getZ(),age,phase));});}
                if(mc.currentScreen!=null){note("screen "+mc.currentScreen.getClass().getSimpleName()+" closed");mc.setScreen(null);}
                if(age<200)return;
                note("in the world at "+fmt()+" map on="+MapClient.enabled()+" players="+mc.world.getPlayers().size()+" wires="+dev.sangwoo.celestialfall.client.town.WireRenderer.count()+" backdrop="+dev.sangwoo.celestialfall.client.town.BackdropRenderer.count()+" sea="+dev.sangwoo.celestialfall.client.town.BackdropRenderer.seaLevel());
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
                if(view>=VIEWS.length){phase=3;age=0;mc.player.networkHandler.sendChatMessage("meteor-ready");note("said meteor-ready");}
            }
            case 3->meteor();
            case 8->{if(age==20){note("done");mc.scheduleStop();phase=9;}}
            default->{}
        }
    }
    private String fmt(){return String.format("%.1f,%.1f,%.1f",mc.player.getX(),mc.player.getY(),mc.player.getZ());}
    /** The meteor check, each tick (see the class notes). */
    private void meteor()throws Exception{
        if(mc.currentScreen!=null)mc.setScreen(null);
        if(!wrapped){wrapped=true;var mine=dev.sangwoo.celestialfall.meteor.MeteorEntity.EVENTS;
            dev.sangwoo.celestialfall.meteor.MeteorEntity.EVENTS=(e,st)->{note("rock status "+switch(st){case 70->"blow";case 71->"fragment off";case 72->"fragment off, rock gone (mined out or shattered)";default->"crumbled";}+" (crystals "+e.fragments()+" blows "+e.hits()+") at "+age);mine.accept(e,st);};}
        var f=Class.forName("dev.sangwoo.celestialfall.client.meteor.MeteorFx").getDeclaredField("FALLS");f.setAccessible(true);
        int falls=((List<?>)f.get(null)).size();
        if(falls!=fallsSeen){note("falls this client draws: "+falls+" at "+age);fallsSeen=falls;}
        dev.sangwoo.celestialfall.meteor.MeteorEntity rock=null;
        // the nearest (the map may drop others of its own meanwhile)
        for(var e:mc.world.getEntities())if(e instanceof dev.sangwoo.celestialfall.meteor.MeteorEntity m&&(rock==null||m.squaredDistanceTo(mc.player)<rock.squaredDistanceTo(mc.player)))rock=m;
        String seen=rock==null?"none":String.format("rock at %.1f,%.1f,%.1f fragments %d blows %d",rock.getX(),rock.getY(),rock.getZ(),rock.fragments(),rock.hits());
        if(!seen.equals(rockSeen)){note("sees "+seen+" at "+age);rockSeen=seen;}
        // 2.49.1: the other players' swings as this client sees them, and whether it shows them in the two-handed hold
        for(var other:mc.world.getPlayers()){
            if(other==mc.player)continue;
            boolean two=dev.sangwoo.celestialfall.client.meteor.MiningPose.active(other,0);
            if(!Boolean.valueOf(two).equals(twoHanded.get(other.getUuid()))){twoHanded.put(other.getUuid(),two);note("sees "+other.getName().getString()+(two?" holding the pickaxe in both hands":" without the two-handed hold")+" at "+age);}
            if(other.handSwinging&&!Boolean.TRUE.equals(swinging.get(other.getUuid())))note("sees "+other.getName().getString()+" swing at "+age);
            swinging.put(other.getUuid(),other.handSwinging);
        }
        if(rock!=null&&rockAge<0)rockAge=age;
        if(rock==null&&rockAge>=0&&goneAge<0)goneAge=age;
        // look east to where it comes down until it is there, then at the rock
        var look=rock!=null?rock.getPos().add(0,.6,0):new net.minecraft.util.math.Vec3d(mc.player.getX()+14,mc.player.getEyeY()+14,mc.player.getZ());
        var d=look.subtract(mc.player.getEyePos());
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalLength()));
        mc.player.setYaw(yaw);mc.player.setPitch(pitch);mc.player.setHeadYaw(yaw);
        if(rock!=null&&mc.player.getMainHandStack().isOf(dev.sangwoo.celestialfall.meteor.MeteorRegistry.PICKAXE)&&rock.squaredDistanceTo(mc.player)<36&&age-lastSwing>=7){
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(yaw,pitch,mc.player.isOnGround()));
            mc.gameRenderer.updateTargetedEntity(1);
            lastSwing=age;boolean took=dev.sangwoo.celestialfall.client.meteor.MeteorClient.attack();
            note("swing at "+age+(took?"":" (not taken)"));
        }
        if(rockAge>=0&&!impactShot&&age-rockAge>=12){impactShot=true;shot("impact");}
        if(goneAge>=0&&age-goneAge==30){shot("after");note("rock gone; done watching");phase=8;age=0;}
        if(age>2400){note("meteor check timed out");phase=8;age=0;}
    }
    private void shot(String name)throws Exception{
        var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
        Path p=OUT.resolve(ROLE).resolve(name+".png");Files.createDirectories(p.getParent());image.writeTo(p);image.close();
        note("frame "+name);
    }
}
