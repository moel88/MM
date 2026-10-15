package dev.sangwoo.botandverify;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.title.BotandTitleScreen;
import dev.sangwoo.celestialfall.client.loot.*;
import dev.sangwoo.celestialfall.client.commerce.CommerceClient;
import dev.sangwoo.celestialfall.loot.*;
import dev.sangwoo.celestialfall.commerce.*;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.map.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import java.nio.file.*;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;

/** Native game acceptance harness; never packaged in the production JAR. */
public final class LootCapture implements ClientModInitializer {
    private static final Path OUT=Path.of(System.getProperty("botand.loot.proof"));
    private static final boolean RESUME=Boolean.getBoolean("botand.loot.resume");
    private static final int SLOT=7;
    private static MinecraftClient mc;private int phase,age;private long started=System.nanoTime(),cycle;private ArmorStandEntity camera;
    private final StringBuilder results=new StringBuilder();
    private void check(boolean ok,String name){if(!ok)throw new IllegalStateException(name);results.append("PASS ").append(name).append('\n');}
    private void step(){phase++;age=0;System.out.println("LOOT QA phase "+phase);}
    private void server(Consumer<ServerPlayerEntity> action){mc.getServer().submit(()->action.accept(mc.getServer().getPlayerManager().getPlayerList().get(0))).join();}
    private void hold(){check(FieldLootClient.key(mc.getWindow().getHandle(),GLFW.GLFW_KEY_E,GLFW.GLFW_PRESS),"E captured by field loot");}
    private void release(){FieldLootClient.key(mc.getWindow().getHandle(),GLFW.GLFW_KEY_E,GLFW.GLFW_RELEASE);}
    private FieldLootEntity clientLoot(){return mc.world.getEntitiesByClass(FieldLootEntity.class,new Box(15,79,-10,20,84,-4),e->e.slot()==SLOT).stream().findFirst().orElse(null);}
    private FieldLootEntity loot(ServerPlayerEntity p){return (FieldLootEntity)p.getServerWorld().getEntity(LootPositions.id(SLOT));}
    private void force(ServerPlayerEntity p,boolean chest,int drops){
        var w=p.getServerWorld();var state=LootState.get(w);var old=state.slot(SLOT);var entity=w.getEntity(LootPositions.id(SLOT));if(entity!=null)entity.discard();
        state.put(SLOT,new LootState.Slot(chest,drops,old==null?0:old.cycle()+1,-1,-1));
    }
    private void home(ServerPlayerEntity p){p.teleport(p.getServerWorld(),17.5,81,-9.0,0,0);}
    private void fill(ServerPlayerEntity p){for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(Items.STONE,64));}
    private void sync(ServerPlayerEntity p){p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();}
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
        age++;mc.getToastManager().clear();
        switch(phase){
        case 0 -> {if(mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-started)/1e9>5){Files.createDirectories(OUT);mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);mc.getToastManager().clear();mc.options.getViewDistance().setValue(8);mc.options.getGuiScale().setValue(2);mc.options.hudHidden=false;mc.options.debugEnabled=false;mc.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);mc.createIntegratedServerLoader().start(new TitleScreen(),System.getProperty("botand.loot.world"));step();}}
        case 1 -> {if(mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);step();}}
        case 2 -> {if(mc.currentScreen==null&&ProgressClient.ready){check(MapClient.enabled(),"dedicated city enabled");
            if(RESUME){phase=40;age=0;break;}
            server(p->{p.changeGameMode(GameMode.SURVIVAL);p.getInventory().clear();p.getWorld().getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,p.getServer());p.getServerWorld().setTimeOfDay(1000);home(p);ProgressManager.set(p,new ProgressProfile(1,0,0,"none",100,0));for(var pos:LootPositions.ALL)p.getServerWorld().setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);});step();}}
        case 3 -> {if(age>110){server(p->{var w=p.getServerWorld();for(int i=0;i<LootPositions.ALL.size();i++){check(LootState.get(w).slot(i)!=null,"field marker migrated "+i);check(w.getBlockState(LootPositions.ALL.get(i)).isAir(),"original chest replaced "+i);check(w.getEntity(LootPositions.id(i)) instanceof FieldLootEntity,"one field entity exists "+i);}check(w.getBlockState(new BlockPos(38,81,-19)).isOf(Blocks.CHEST),"arena equipment chest preserved");force(p,false,3);p.setInvisible(true);});view(20.1,82.65,-10,17.5,81.5,-6.5);mc.options.getFov().setValue(52);step();}}
        case 4 -> {if(age>35&&FieldLootClient.preferred()!=null){shot("Botand_Field_Box");hold();step();}}
        case 5 -> {if(age==9){release();step();}}
        case 6 -> {if(age>18){server(p->check(LootState.get(p.getServerWorld()).slot(SLOT).available()&&ProgressManager.profile(p).money()==100,"short hold gives no reward"));hold();step();}}
        case 7 -> {if(clientLoot()!=null&&clientLoot().opened()){release();step();}}
        case 8 -> {if(age==10){shot("Botand_Field_Box_Open");server(p->{var state=LootState.get(p.getServerWorld());var slot=state.slot(SLOT);check(ProgressManager.profile(p).money()==110&&ProgressManager.profile(p).xp()==10,"box pays 10 money and 10 XP");check(CommerceServer.fruitCount(p)==1&&LootServer.keySlot(p)>=0,"box awards independently rolled fruit and key");check(slot.respawnAt()-slot.openedAt()>=100&&slot.respawnAt()-slot.openedAt()<=3600,"respawn chosen within three minutes");check(!LootServer.claim(p,loot(p)),"repeat claim rejected");cycle=slot.cycle();});step();}}
        case 9 -> {if(age>65){server(p->{check(loot(p)==null,"opened box vanishes after animation");var state=LootState.get(p.getServerWorld());var v=state.slot(SLOT);check(!v.available(),"cooldown retains claimed state");state.put(SLOT,new LootState.Slot(v.treasure(),v.drops(),v.cycle(),p.getServerWorld().getTime()+60,v.openedAt()));});step();}}
        case 10 -> {if(age>65&&clientLoot()!=null&&!clientLoot().opened()){server(p->{var slot=LootState.get(p.getServerWorld()).slot(SLOT);check(slot.available()&&slot.cycle()==cycle+1,"scheduled respawn advances once");check(ProgressManager.profile(p).money()==110,"respawn gives no free reward");force(p,true,1);p.getInventory().clear();sync(p);});step();}}
        case 11 -> {if(age>30&&clientLoot()!=null&&clientLoot().treasure()&&!clientLoot().opened()){shot("Botand_Field_Chest_Locked");hold();step();}}
        case 12 -> {if(age>30){release();server(p->{check(LootState.get(p.getServerWorld()).slot(SLOT).available()&&ProgressManager.profile(p).money()==110,"locked chest refuses missing key");fill(p);p.getInventory().setStack(40,new ItemStack(LootRegistry.CHEST_KEY,2));sync(p);});hold();step();}}
        case 13 -> {if(age>35){release();server(p->{check(LootState.get(p.getServerWorld()).slot(SLOT).available()&&p.getOffHandStack().getCount()==2&&ProgressManager.profile(p).money()==110,"full inventory preserves chest key and rewards");p.getInventory().clear();fill(p);p.getInventory().setStack(0,new ItemStack(LootRegistry.CHEST_KEY,1));sync(p);});hold();step();}}
        case 14 -> {if(clientLoot()!=null&&clientLoot().opened()){release();step();}}
        case 15 -> {if(age==14){shot("Botand_Field_Chest_Open");server(p->{check(ProgressManager.profile(p).money()==125&&ProgressManager.profile(p).xp()==25,"treasure pays fifty percent extra");check(LootServer.keySlot(p)<0&&CommerceServer.fruitCount(p)==1,"one key consumed and exactly one fruit guaranteed");check(p.getInventory().getStack(0).isOf(CommerceRegistry.ROKAKAKA),"last key frees slot for guaranteed reward");check(!LootServer.claim(p,loot(p)),"treasure cannot be claimed twice");});step();}}
        case 16 -> {if(age>65){server(p->{force(p,false,1);p.getInventory().clear();sync(p);});step();}}
        case 17 -> {if(age>30&&FieldLootClient.preferred()!=null){hold();step();}}
        case 18 -> {if(age==8){server(p->p.teleport(p.getServerWorld(),37,81,-9,0,0));step();}}
        case 19 -> {if(age>25){release();server(p->{check(LootState.get(p.getServerWorld()).slot(SLOT).available()&&ProgressManager.profile(p).money()==125,"leaving reach cancels hold");check(!LootServer.claim(p,loot(p)),"server rejects remote direct claim");home(p);var pos=new BlockPos(17,82,-8);var before=p.getWorld().getBlockState(pos);p.getWorld().setBlockState(pos,Blocks.STONE.getDefaultState());check(!LootServer.near(p,loot(p)),"wall blocks line of sight");check(!LootServer.claim(p,loot(p)),"wall blocks rewards");p.getWorld().setBlockState(pos,before);LootServer.handle(p,LootServer.START,loot(p).getId());});step();}}
        case 20 -> {if(age>32){server(p->{check(LootState.get(p.getServerWorld()).slot(SLOT).available()&&ProgressManager.profile(p).money()==125,"missing hold pulses expires request");check(!loot(p).damage(p.getWorld().getDamageSources().generic(),1000),"skills cannot break the loot entity");var state=LootState.get(p.getServerWorld());check(LootState.read(state.writeNbt(new NbtCompound())).slot(SLOT).equals(state.slot(SLOT)),"loot state NBT round trip");});step();}}
        case 21 -> {if(age>15){server(p->{var w=p.getServerWorld();var pos=new BlockPos(20,81,-8);var before=w.getBlockState(pos);w.setBlockState(pos,Blocks.CHEST.getDefaultState());var chest=(net.minecraft.block.entity.ChestBlockEntity)w.getBlockEntity(pos);chest.setStack(0,new ItemStack(Items.DIAMOND,11));check(!LootWorld.migrate(w,pos)&&chest.getStack(0).getCount()==11,"custom stored items preserved during migration");w.removeBlockEntity(pos);w.setBlockState(pos,before);MapGate.designate(w,false);check(!LootServer.near(p,loot(p)),"loot disabled outside authorized map");MapGate.designate(w,true);});step();}}
        case 22 -> {if(age>30){server(p->{var w=p.getServerWorld();force(p,true,1);p.getInventory().clear();p.getInventory().setStack(0,new ItemStack(LootRegistry.CHEST_KEY,3));sync(p);var state=LootState.get(w);var v=state.slot(SLOT);state.put(SLOT,v.claimed(w.getTime(),3000));var slot=state.slot(SLOT);var props=new java.util.Properties();props.setProperty("cycle",""+slot.cycle());props.setProperty("due",""+slot.respawnAt());props.setProperty("opened",""+slot.openedAt());props.setProperty("time",""+w.getTime());try(var writer=Files.newBufferedWriter(OUT.resolve("restart.properties"))){props.store(writer,"Native restart fixture");}catch(Exception ex){throw new RuntimeException(ex);}state.markDirty();w.getPersistentStateManager().save();});Files.writeString(OUT.resolve("server-result.txt"),results.toString());Files.writeString(OUT.resolve("client-result.txt"),"PASS 2.17.0: native field loot opening, keys, rewards, migration, cooldown and rejected requests; four actual screenshots.\n");mc.setCameraEntity(mc.player);mc.scheduleStop();phase=99;}}
        case 40 -> {if(age>100){var props=new java.util.Properties();try(var reader=Files.newBufferedReader(OUT.resolve("restart.properties"))){props.load(reader);}server(p->{var state=LootState.get(p.getServerWorld());var s=state.slot(SLOT);check(s!=null&&!s.available()&&s.treasure(),"claimed treasure persists after process restart");check(s.cycle()==Long.parseLong(props.getProperty("cycle"))&&s.respawnAt()==Long.parseLong(props.getProperty("due"))&&s.openedAt()==Long.parseLong(props.getProperty("opened")),"restart preserves exact deadline and cycle");check(loot(p)==null,"restart does not respawn claimed chest early");check(ProgressManager.profile(p).money()==125&&ProgressManager.profile(p).xp()==25,"loot progression persists after restart");check(p.getInventory().getStack(0).isOf(LootRegistry.CHEST_KEY)&&p.getInventory().getStack(0).getCount()==3,"keys persist after restart");});Files.writeString(OUT.resolve("restart-result.txt"),results.toString());mc.setCameraEntity(mc.player);mc.scheduleStop();phase=99;}}
        }
    }catch(Throwable t){t.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(RESUME?"restart-error.txt":"client-error.txt"),"phase="+phase+" "+t+"\n"+results);}catch(Exception ignored){}mc.scheduleStop();phase=99;}});}
}
