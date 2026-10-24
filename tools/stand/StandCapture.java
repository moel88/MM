package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.title.BotandTitleScreen;
import dev.sangwoo.celestialfall.client.commerce.*;
import dev.sangwoo.celestialfall.commerce.*;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.stand.*;
import dev.sangwoo.celestialfall.map.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.*;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import java.nio.file.*;
import java.util.function.Consumer;

/** Isolated native client/integrated-server acceptance harness. */
public final class StandCapture implements ClientModInitializer {
    private static final Path OUT=Path.of(System.getProperty("botand.stand.proof"));
    private static final boolean RESUME=Boolean.getBoolean("botand.stand.resume");
    private static MinecraftClient mc;private int phase,age;private long started=System.nanoTime();
    private final StringBuilder results=new StringBuilder();
    private void check(boolean ok,String name){if(!ok)throw new IllegalStateException(name);results.append("PASS ").append(name).append('\n');}
    private void step(){phase++;age=0;System.out.println("STAND QA phase "+phase);}
    private void server(Consumer<ServerPlayerEntity> action){mc.getServer().submit(()->action.accept(mc.getServer().getPlayerManager().getPlayerList().get(0))).join();}
    private void command(ServerPlayerEntity p,String cmd){check(p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(4),cmd)>0,"command "+cmd);}
    private void select(ServerPlayerEntity p,int slot){p.getInventory().selectedSlot=slot;p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(slot));p.currentScreenHandler.sendContentUpdates();}
    private void pressR(){check(mc.options.useKey.matchesKey(org.lwjgl.glfw.GLFW.GLFW_KEY_R,0),"R bound to item/stand use");KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(mc.options.useKey));}
    private void shot(String name)throws Exception{
        try(var im=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){im.writeTo(OUT.resolve(name+".png"));check(Files.size(OUT.resolve(name+".png"))>10000,"native frame "+name);}
    }
    private void confirmFruit()throws Exception{
        var screen=(CommerceScreen)mc.currentScreen;
        var f=CommerceScreen.class.getDeclaredField("primary");f.setAccessible(true);
        ((net.minecraft.client.gui.widget.PressableWidget)f.get(screen)).onPress();
    }
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{
        if((System.nanoTime()-started)/1e9>300)throw new IllegalStateException("Timeout phase "+phase);
        age++;mc.getToastManager().clear();
        switch(phase){
        case 0 -> {if(mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-started)/1e9>5){Files.createDirectories(OUT);mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);mc.options.getViewDistance().setValue(4);mc.options.getGuiScale().setValue(2);mc.options.getMaxFps().setValue(60);mc.options.hudHidden=false;mc.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);mc.createIntegratedServerLoader().start(new TitleScreen(),System.getProperty("botand.stand.world"));step();}}
        case 1 -> {if(mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);step();}}
        case 2 -> {if(mc.currentScreen==null&&ProgressClient.ready){
            if(RESUME){phase=40;age=0;break;}
            server(p->{p.changeGameMode(GameMode.SURVIVAL);p.getInventory().clear();p.getServerWorld().setTimeOfDay(1000);
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){p.getWorld().setBlockState(new BlockPos(x,90,z),Blocks.SMOOTH_STONE.getDefaultState());for(int y=91;y<140;y++)p.getWorld().setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());}
                p.teleport(p.getServerWorld(),.5,91,.5,180,0);p.setOnGround(true);ProgressManager.set(p,new ProgressProfile(18,2,1,"none",777,31));
                p.getInventory().setStack(0,new ItemStack(CelestialFall.ATOMIC_SWORD));select(p,0);ProgressManager.joined(p);
                check(!ProgressManager.canUseKageno(p)&&!StandManager.kageno(p),"owning sword does not auto-equip on join");check(!AtomicManager.cast(p),"unassigned sword cannot cast");
                p.getInventory().clear();p.getInventory().setStack(1,new ItemStack(Items.STONE,17));p.getInventory().setStack(2,new ItemStack(CommerceRegistry.ROKAKAKA,2));p.getInventory().setStack(3,new ItemStack(Items.DIAMOND,5));
                select(p,0);command(p,"botand progress stand @s kageno");check(StandManager.kageno(p),"command updates tracked stand immediately");check(p.getMainHandStack().isEmpty(),"equip leaves empty inventory slot unchanged");});step();}}
        case 3 -> {if(age>35&&StandClient.visible(mc.player)){check(mc.player.getMainHandStack().isEmpty(),"empty client slot stays empty");check(!CombatHands.enabled(mc.player),"fists do not replace stand sword");BotandCamera.STATE.targetDistance=0;step();}}
        case 4 -> {if(age>35){shot("Stand_FirstPerson_Empty");pressR();step();}}
        case 5 -> {if(age>10){server(p->{check(AtomicManager.isCasting(p.getUuid()),"R casts with empty hands");check(!StandManager.useSkill(p),"cooldown blocks repeated skill");AtomicManager.clear(p.getServer());p.setMainArm(Arm.LEFT);select(p,1);});step();}}
        case 6 -> {if(age>25&&mc.player.getMainHandStack().isOf(Items.STONE)){check(StandClient.visible(mc.player),"stone selection keeps sword");shot("Stand_FirstPerson_Stone");pressR();step();}}
        case 7 -> {if(age>10){server(p->{check(AtomicManager.isCasting(p.getUuid()),"R casts while stone selected");check(p.getMainHandStack().isOf(Items.STONE)&&p.getMainHandStack().getCount()==17,"skill does not consume or replace stone");AtomicManager.clear(p.getServer());p.setMainArm(Arm.RIGHT);select(p,2);});step();}}
        case 8 -> {if(age>30&&mc.player.getMainHandStack().isOf(CommerceRegistry.ROKAKAKA)){BotandCamera.STATE.targetDistance=5;BotandCamera.STATE.yaw=90;BotandCamera.STATE.pitch=12;step();}}
        case 9 -> {if(age>35){check(StandClient.visible(mc.player),"fruit selection keeps sword");shot("Stand_ThirdPerson_Fruit");pressR();step();}}
        case 10 -> {if(age>10){server(p->{check(AtomicManager.isCasting(p.getUuid()),"R casts with fruit selected");check(p.getMainHandStack().getCount()==2,"R does not consume fruit");AtomicManager.clear(p.getServer());});step();}}
        case 11 -> {if(age>30){check(CommerceClient.clickFruit(),"fruit left click remains available");step();}}
        case 12 -> {if(mc.currentScreen instanceof CommerceScreen s&&s.confirmation){confirmFruit();step();}}
        case 13 -> {if(age>25&&mc.currentScreen==null&&!StandClient.visible(mc.player)){server(p->{check(ProgressManager.profile(p).stand().equals("none"),"fruit resets stand");check(p.getMainHandStack().getCount()==1,"fruit consumes exactly one");check(!StandManager.useSkill(p),"removed stand rejects skill packet path");check(ProgressManager.profile(p).money()==777&&ProgressManager.profile(p).level()==18,"stand changes preserve growth and money");});shot("Stand_Removed");server(p->{command(p,"botand progress stand @s kageno");select(p,3);});step();}}
        case 14 -> {if(age>25&&StandClient.visible(mc.player)){server(p->{MapGate.designate(p.getServerWorld(),false);check(!StandManager.kageno(p)&&!StandManager.useSkill(p),"disabled map rejects stand skill");});step();}}
        case 15 -> {if(age>25){check(!StandClient.visible(mc.player),"disabled map clears client sword");server(p->MapGate.designate(p.getServerWorld(),true));step();}}
        case 16 -> {if(mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);}if(age>25&&mc.currentScreen==null&&StandClient.visible(mc.player)){server(p->{var before=ProgressManager.profile(p);check(ProgressState.read(ProgressState.get(p.getServer()).writeNbt(new NbtCompound())).profile(p.getUuid()).equals(before),"stand and progression NBT round trip");check(p.getInventory().getStack(0).isEmpty()&&p.getInventory().getStack(1).getCount()==17&&p.getInventory().getStack(2).getCount()==1&&p.getInventory().getStack(3).getCount()==5,"all selected items survive stand lifecycle");p.getWorld().getGameRules().get(net.minecraft.world.GameRules.KEEP_INVENTORY).set(true,p.getServer());p.kill();});step();}}
        case 17 -> {if(age>25&&!mc.player.isAlive()){mc.player.requestRespawn();step();}}
        case 18 -> {if(age>35&&mc.player.isAlive()&&StandClient.visible(mc.player)){server(p->{check(StandManager.kageno(p),"stand restored on respawn");check(p.getInventory().getStack(3).isOf(Items.DIAMOND),"respawn does not inject a sword");p.getServerWorld().getPersistentStateManager().save();});Files.writeString(OUT.resolve("client-result.txt"),results.toString());mc.scheduleStop();phase=99;}}
        case 40 -> {if(age==30){BotandCamera.STATE.targetDistance=4;BotandCamera.STATE.yaw=mc.player.getYaw()+90;BotandCamera.STATE.pitch=5;}if(age>70){shot("Stand_ThirdPerson_Side");check(StandClient.visible(mc.player),"sword restored after process restart");server(p->{check(StandManager.kageno(p)&&ProgressManager.canUseKageno(p),"server stand restored after process restart");check(p.getInventory().getStack(3).isOf(Items.DIAMOND)&&p.getInventory().getStack(3).getCount()==5,"inventory item preserved after restart");check(ProgressManager.profile(p).money()==777&&ProgressManager.profile(p).level()==18,"progress preserved after restart");});Files.writeString(OUT.resolve("restart-result.txt"),results.toString());mc.scheduleStop();phase=99;}}
        }
    }catch(Throwable t){t.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(RESUME?"restart-error.txt":"client-error.txt"),"phase="+phase+" "+t+"\n"+results);}catch(Exception ignored){}mc.scheduleStop();phase=99;}});}
}
