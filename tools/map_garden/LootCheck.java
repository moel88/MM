package dev.sangwoo.botandverify;

import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.client.title.*;
import dev.sangwoo.celestialfall.loot.*;
import dev.sangwoo.celestialfall.map.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

/**
 * Disposable native check on a fresh copy of the dedicated map (excluded from the production JAR): the
 * map is recognised, each of the eleven field chests turns into a box or treasure chest at exactly its
 * position with its fixed id and saved slot, a removed box comes back once without duplicates, and the
 * arena supplies chest is left alone. Writes loot-result.txt and one real screenshot per position.
 */
public final class LootCheck implements ClientModInitializer {
    private static final Path OUT=Path.of(System.getProperty("botand.loot.proof"));
    private static final BlockPos ARENA_CHEST=new BlockPos(38,81,-19);
    private final List<String> lines=new ArrayList<>();
    private volatile Throwable failure;private volatile String serverCheck;
    private long started=System.nanoTime(),wait;private int phase,slot;private boolean asked;

    @Override public void onInitializeClient(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(failure!=null)throw new IllegalStateException("Loot check failed",failure);
            if((System.nanoTime()-started)/1e9>900)throw new IllegalStateException("Timeout phase "+phase+" slot "+slot);
            if(phase==0&&mc.currentScreen!=null&&mc.getOverlay()==null&&(System.nanoTime()-started)/1e9>5){
                Files.createDirectories(OUT);mc.options.getViewDistance().setValue(8);mc.options.getGuiScale().setValue(2);
                mc.createIntegratedServerLoader().start(new TitleScreen(),System.getProperty("botand.loot.world"));phase=1;return;
            }
            if(phase==1&&mc.currentScreen instanceof BotandTitleScreen title){var r=title.layout().screen(title.layout().actions().get(0).rect());title.mouseClicked(r.x()+8,r.y()+8,0);phase=2;return;}
            if(phase==2&&mc.currentScreen==null&&ProgressClient.ready&&mc.player!=null){
                boolean city=MapGate.isCity(mc.getServer()),on=MapClient.enabled();
                note("map recognised="+city+" client enabled="+on);
                if(!city||!on)throw new IllegalStateException("Dedicated map not recognised");
                mc.getServer().execute(()->{var w=mc.getServer().getOverworld();w.setTimeOfDay(6000);
                    w.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,mc.getServer());});
                mc.options.hudHidden=true;phase=3;go(mc);return;
            }
            if(phase>=3)look();
            if(phase==3){
                long waited=(System.nanoTime()-wait)/1_000_000;
                if(!asked&&waited>6000){asked=true;serverCheck=null;int i=slot;mc.getServer().execute(()->{try{serverCheck=check(mc.getServer().getOverworld(),i);}catch(Throwable t){failure=t;}});}
                if(asked&&serverCheck!=null&&waited>7000){
                    note(serverCheck);
                    try(var im=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){im.writeTo(OUT.resolve(String.format(Locale.ROOT,"box-%02d.png",slot+1)));}
                    if(++slot<LootPositions.ALL.size()){go(mc);return;}
                    phase=4;slot=0;wait=System.nanoTime();asked=false;
                    // Back to the first position to remove its box and watch it come back.
                    teleport(mc,0);
                }
                return;
            }
            if(phase==4){
                long waited=(System.nanoTime()-wait)/1_000_000;
                if(!asked&&waited>5000){asked=true;mc.getServer().execute(()->{try{
                    var w=mc.getServer().getOverworld();var e=w.getEntity(LootPositions.id(0));
                    note("removed slot 1 box present="+(e!=null));if(e!=null)e.discard();
                }catch(Throwable t){failure=t;}});wait=System.nanoTime();phase=5;asked=false;}
                return;
            }
            if(phase==5&&(System.nanoTime()-wait)/1e9>5&&!asked){
                asked=true;mc.getServer().execute(()->{try{
                    var w=mc.getServer().getOverworld();
                    long copies=w.getEntitiesByClass(FieldLootEntity.class,new net.minecraft.util.math.Box(LootPositions.ALL.get(0)).expand(4),e->true).size();
                    var e=w.getEntity(LootPositions.id(0));
                    note("slot 1 after removal: back="+(e!=null)+" copies="+copies+" same id="+(e!=null&&e.getUuid().equals(LootPositions.id(0))));
                    var chest=w.getBlockEntity(ARENA_CHEST);
                    int stacks=0;if(chest instanceof ChestBlockEntity c)for(int i=0;i<c.size();i++)if(!c.getStack(i).isEmpty())stacks++;
                    note("arena supplies chest kept="+(w.getBlockState(ARENA_CHEST).isOf(Blocks.CHEST))+" stacks="+stacks);
                    int saved=0;var state=LootState.get(w);for(int i=0;i<LootPositions.ALL.size();i++)if(state.slot(i)!=null)saved++;
                    note("saved slots="+saved);
                    serverCheck="done";
                }catch(Throwable t){failure=t;}});
                return;
            }
            if(phase==5&&"done".equals(serverCheck)){
                try(var im=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){im.writeTo(OUT.resolve("box-01-restored.png"));}
                Files.write(OUT.resolve("loot-result.txt"),lines);mc.scheduleStop();phase=6;
            }
        }catch(Throwable t){failure=t;t.printStackTrace();try{Files.createDirectories(OUT);lines.add("ERROR "+t);Files.write(OUT.resolve("loot-error.txt"),lines);}catch(Exception ignored){}mc.scheduleStop();}});
    }
    private void note(String line){synchronized(lines){lines.add(line);}}
    private void go(MinecraftClient mc){asked=false;wait=System.nanoTime();teleport(mc,slot);}
    /** Botand's orbit camera in first person, looking north and down, so the player's own model does not hide the box. */
    private static void look(){
        var s=BotandCamera.STATE;s.targetDistance=s.distance=s.collisionDistance=0;s.yaw=180;s.pitch=25;
    }
    /** Stand three blocks south of the position looking north and down at it (first person, so nothing hides it). */
    private static void teleport(MinecraftClient mc,int i){
        var pos=LootPositions.ALL.get(i);
        mc.getServer().execute(()->{var p=mc.getServer().getPlayerManager().getPlayerList().get(0);
            p.teleport(mc.getServer().getOverworld(),pos.getX()+.5,pos.getY(),pos.getZ()+3.5,180,25);});
    }
    private static String check(ServerWorld w,int i){
        var pos=LootPositions.ALL.get(i);var e=w.getEntity(LootPositions.id(i));var slot=LootState.get(w).slot(i);
        String kind=e instanceof FieldLootEntity f?(f.treasure()?"treasure chest":"box"):"none";
        boolean exact=e!=null&&e.getBlockPos().equals(pos);
        return String.format(Locale.ROOT,"slot %d at %d %d %d: block=%s entity=%s exact=%s saved=%s",i+1,pos.getX(),pos.getY(),pos.getZ(),
            net.minecraft.registry.Registries.BLOCK.getId(w.getBlockState(pos).getBlock()).getPath(),kind,exact,slot==null?"none":(slot.treasure()?"treasure":"box")+" cycle "+slot.cycle());
    }
}
