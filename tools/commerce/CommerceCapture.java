package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.title.BotandTitleScreen;
import dev.sangwoo.celestialfall.client.commerce.*;
import dev.sangwoo.celestialfall.commerce.*;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.map.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;

/** Native client/network/server acceptance test. Never included in the shipped mod. */
public final class CommerceCapture implements ClientModInitializer {
    private static final Path OUT=Path.of(System.getProperty("botand.commerce.proof"));
    private static MinecraftClient mc;private int phase,age;private long started=System.nanoTime(),oldToken;private ArmorStandEntity camera;
    private final StringBuilder results=new StringBuilder();
    private void check(boolean ok,String name){if(!ok)throw new IllegalStateException(name);results.append("PASS ").append(name).append('\n');}
    private void step(){phase++;age=0;System.out.println("COMMERCE QA phase "+phase);}
    private void server(Consumer<ServerPlayerEntity> action){mc.getServer().submit(()->action.accept(mc.getServer().getPlayerManager().getPlayerList().get(0))).join();}
    private void hold(){check(CommerceClient.key(mc.getWindow().getHandle(),GLFW.GLFW_KEY_E,GLFW.GLFW_PRESS),"E captures nearby NPC");}
    private void release(){CommerceClient.key(mc.getWindow().getHandle(),GLFW.GLFW_KEY_E,GLFW.GLFW_RELEASE);}
    private CommerceScreen screen(){return (CommerceScreen)mc.currentScreen;}
    private long nonce()throws Exception{var f=CommerceScreen.class.getDeclaredField("token");f.setAccessible(true);return f.getLong(screen());}
    private void mainClick()throws Exception{var f=CommerceScreen.class.getDeclaredField("primary");f.setAccessible(true);var b=(PressableWidget)f.get(screen());check(b.active,"primary button enabled");screen().mouseClicked(b.getX()+b.getWidth()/2.,b.getY()+b.getHeight()/2.,0);}
    private void view(double x,double y,double z,double tx,double ty,double tz){
        if(camera==null){camera=new ArmorStandEntity(mc.world,0,0,0);camera.setInvisible(true);camera.setNoGravity(true);}
        var d=new Vec3d(tx-x,ty-y,tz-z);float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)Math.toDegrees(-Math.atan2(d.y,Math.hypot(d.x,d.z)));
        camera.refreshPositionAndAngles(x,y-camera.getStandingEyeHeight(),z,yaw,pitch);camera.prevYaw=yaw;camera.prevPitch=pitch;camera.setHeadYaw(yaw);camera.prevHeadYaw=yaw;camera.setBodyYaw(yaw);camera.prevBodyYaw=yaw;
        mc.setCameraEntity(camera);mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);mc.options.getFov().setValue(65);
    }
    private void shot(String name)throws Exception{
        try(var im=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){
            var out=new java.awt.image.BufferedImage(im.getWidth(),im.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){int c=im.getColor(x,y);out.setRGB(x,y,(c&0xff00ff00)|((c&255)<<16)|((c>>>16)&255));}
            var path=OUT.resolve(name+".png");javax.imageio.ImageIO.write(out,"png",path.toFile());try(var f=java.nio.channels.FileChannel.open(path,StandardOpenOption.WRITE)){f.force(true);}check(Files.size(path)>10000,"native screenshot "+name);
        }
    }
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{
        if((System.nanoTime()-started)/1e9>550)throw new IllegalStateException("Timeout phase "+phase);
        age++;
        switch(phase){
        case 0 -> {if(mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-started)/1e9>5){Files.createDirectories(OUT);mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);mc.getToastManager().clear();mc.options.getViewDistance().setValue(8);mc.options.getGuiScale().setValue(2);mc.options.hudHidden=false;mc.options.debugEnabled=false;mc.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);mc.createIntegratedServerLoader().start(new TitleScreen(),System.getProperty("botand.commerce.world"));step();}}
        case 1 -> {if(mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);step();}}
        case 2 -> {if(mc.currentScreen==null&&ProgressClient.ready){check(MapClient.enabled(),"dedicated map recognized");server(p->{p.changeGameMode(GameMode.SURVIVAL);p.getInventory().clear();p.getWorld().getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,p.getServer());p.getServerWorld().setTimeOfDay(1000);p.teleport(p.getServerWorld(),-6.5,81.0625,-116,180,0);ProgressManager.set(p,new ProgressProfile(1,0,0,"kageno",300,95));p.getServerWorld().getChunk(3,-8);});step();}}
        case 3 -> {if(age>100&&CommerceClient.nearest()!=null){view(-10.4,83.1,-114.8,-6.5,82,-118.5);server(p->{check(p.getServerWorld().getEntity(CommerceRegistry.id(true)) instanceof MerchantEntity,"seller auto spawn");check(p.getServerWorld().getEntity(CommerceRegistry.id(false)) instanceof MerchantEntity,"buyer auto spawn");for(boolean b:new boolean[]{true,false}){var n=p.getServerWorld().getEntity(CommerceRegistry.id(b));check(p.getServerWorld().isSpaceEmpty(n,n.getBoundingBox()),"NPC not inside blocks "+b);}});step();}}
        case 4 -> {if(age>45){hold();step();}}
        case 5 -> {if(age==9){shot("Botand_NPC_E_hold");release();step();}}
        case 6 -> {if(age>20){check(mc.currentScreen==null,"short E hold does not open trade");hold();step();}}
        case 7 -> {if(mc.currentScreen instanceof CommerceScreen s&&!s.confirmation){release();step();}}
        case 8 -> {if(age>30){shot("Botand_NPC_Shop");oldToken=nonce();mainClick();step();}}
        case 9 -> {if(age>25){server(p->{var v=ProgressManager.profile(p);check(v.money()==225&&v.level()==2&&v.xp()==5&&CommerceServer.fruitCount(p)==1,"purchase costs 75 and grants 10 XP with level up");CommerceServer.handle(p,CommerceServer.TRADE,0,oldToken);check(ProgressManager.profile(p).equals(v)&&CommerceServer.fruitCount(p)==1,"replayed purchase nonce rejected");});shot("Botand_NPC_Purchase");screen().close();server(p->p.teleport(p.getServerWorld(),55.5,81,-122,180,0));view(58.5,83.1,-120,55.5,82,-124.5);step();}}
        case 10 -> {if(age>60&&CommerceClient.nearest()!=null){hold();step();}}
        case 11 -> {if(mc.currentScreen instanceof CommerceScreen){release();step();}}
        case 12 -> {if(age>30){shot("Botand_NPC_Buyer");mainClick();step();}}
        case 13 -> {if(age>25){server(p->{var v=ProgressManager.profile(p);check(v.money()==275&&v.level()==2&&v.xp()==10&&CommerceServer.fruitCount(p)==0,"sale pays 50 and grants 5 XP");p.getInventory().setStack(0,new ItemStack(CommerceRegistry.ROKAKAKA,2));p.getInventory().selectedSlot=0;p.currentScreenHandler.sendContentUpdates();});screen().close();step();}}
        case 14 -> {if(age>15){check(CommerceClient.clickFruit(),"left click routes fruit use");step();}}
        case 15 -> {if(mc.currentScreen instanceof CommerceScreen s&&s.confirmation&&age>25){shot("Botand_Rokakaka_Confirm");s.close();step();}}
        case 16 -> {if(age>15){server(p->check(CommerceServer.fruitCount(p)==2&&ProgressManager.profile(p).stand().equals("kageno"),"cancel preserves fruit and stand"));CommerceClient.clickFruit();step();}}
        case 17 -> {if(mc.currentScreen instanceof CommerceScreen s&&s.confirmation&&age>15){oldToken=nonce();mainClick();step();}}
        case 18 -> {if(age>25){server(p->{var v=ProgressManager.profile(p);check(v.stand().equals("none")&&v.level()==2&&v.xp()==10&&v.money()==275&&CommerceServer.fruitCount(p)==1,"confirmed use consumes one and resets only stand");check(!ProgressManager.canUseKageno(p),"reset removes Kageno ability access");check(!dev.sangwoo.celestialfall.AtomicManager.cast(p),"atomic rejected after reset");ProgressManager.joined(p);check(ProgressManager.profile(p).stand().equals("none"),"join does not undo intentional reset");CommerceServer.handle(p,CommerceServer.CONFIRM,0,oldToken);check(CommerceServer.fruitCount(p)==1,"replayed reset nonce rejected");CommerceServer.askReset(p,Hand.MAIN_HAND);check(CommerceServer.fruitCount(p)==1,"no stand does not consume fruit");});check(mc.currentScreen==null,"confirmation closes after success");hold();step();}}
        case 19 -> {if(mc.currentScreen instanceof CommerceScreen&&age>20){release();oldToken=nonce();server(p->{var before=ProgressManager.profile(p);p.teleport(p.getServerWorld(),65,81,-111,0,0);CommerceServer.handle(p,CommerceServer.TRADE,0,oldToken);check(ProgressManager.profile(p).equals(before)&&CommerceServer.fruitCount(p)==1,"distance checked again at transaction");});step();}}
        case 20 -> {if(age>25){check(mc.currentScreen==null,"walking away closes trade UI");check(!CommerceClient.key(mc.getWindow().getHandle(),GLFW.GLFW_KEY_E,GLFW.GLFW_PRESS),"E remains inventory away from NPCs");server(p->{p.teleport(p.getServerWorld(),-6.5,81.0625,-116,180,0);ProgressManager.set(p,ProgressManager.profile(p).withMoney(0));});view(-10.4,83.1,-114.8,-6.5,82,-118.5);step();}}
        case 21 -> {if(age>40&&CommerceClient.nearest()!=null){hold();step();}}
        case 22 -> {if(mc.currentScreen instanceof CommerceScreen&&age>20){release();oldToken=nonce();server(p->{var before=ProgressManager.profile(p);CommerceServer.handle(p,CommerceServer.TRADE,0,oldToken);check(ProgressManager.profile(p).equals(before)&&CommerceServer.fruitCount(p)==1,"server refuses insufficient money without reward");});step();}}
        case 23 -> {if(age>20){oldToken=nonce();server(p->{ProgressManager.set(p,ProgressManager.profile(p).withMoney(75));for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(Items.STONE,64));var before=ProgressManager.profile(p);CommerceServer.handle(p,CommerceServer.TRADE,0,oldToken);check(ProgressManager.profile(p).equals(before),"server refuses full inventory without charging");p.getInventory().setStack(0,new ItemStack(CommerceRegistry.ROKAKAKA,2));ProgressManager.set(p,ProgressManager.profile(p).withStand("kageno"));p.currentScreenHandler.sendContentUpdates();});step();}}
        case 24 -> {if(age>20){screen().close();CommerceClient.clickFruit();step();}}
        case 25 -> {if(mc.currentScreen instanceof CommerceScreen s&&s.confirmation&&age>15){oldToken=nonce();server(p->{p.getInventory().setStack(0,ItemStack.EMPTY);CommerceServer.handle(p,CommerceServer.CONFIRM,0,oldToken);check(ProgressManager.profile(p).stand().equals("kageno"),"removing held fruit cancels reset");});step();}}
        case 26 -> {if(age>20){server(p->{var state=ProgressState.get(p.getServer());check(ProgressState.read(state.writeNbt(new net.minecraft.nbt.NbtCompound())).profile(p.getUuid()).equals(ProgressManager.profile(p)),"native progression save round trip");state.markDirty();p.getServerWorld().getPersistentStateManager().save();var seller=(MerchantEntity)p.getServerWorld().getEntity(CommerceRegistry.id(true));check(seller!=null&&seller.getPos().equals(seller.home()),"seller remains anchored");check(p.getServerWorld().getEntitiesByClass(MerchantEntity.class,new Box(-20,75,-140,70,90,-85),e->true).size()==2,"exactly two NPCs without duplicates");MapGate.designate(p.getServerWorld(),false);check(!CommerceServer.eligible(p),"commerce disabled outside authorized map");});step();}}
        case 27 -> {if(age>15){check(!CommerceClient.key(mc.getWindow().getHandle(),GLFW.GLFW_KEY_E,GLFW.GLFW_PRESS),"E restored when map disabled");Files.writeString(OUT.resolve("server-result.txt"),results.toString());Files.writeString(OUT.resolve("client-result.txt"),"PASS 2.16.1: native Minecraft client + integrated server; E hold, commerce, experience, confirmation and rejection checks; 5 native screenshots.\n");mc.setCameraEntity(mc.player);mc.scheduleStop();phase=28;}}
        }
    }catch(Throwable t){t.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("client-error.txt"),"phase="+phase+" "+t+"\n"+results);}catch(Exception ignored){}mc.scheduleStop();phase=28;}});}
}
