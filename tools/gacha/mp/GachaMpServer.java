package dev.sangwoo.gachamp;

import dev.sangwoo.celestialfall.gacha.GachaRules;
import dev.sangwoo.celestialfall.gacha.GachaState;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.progression.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import java.nio.file.*;
import java.util.*;

/**
 * Multiplayer check of the gacha (2.39.0), dedicated-server half (only with -Dbotand.mp.server=true). "Puller" joins
 * with no stand and a count that makes the first pull an SSR; once that equips Kageno the stand is taken off again and
 * the count set so a ten-pull has an SR on its 4th pull and an SSR on its 6th; after that the stand comes off once more
 * for two pulls sent in one tick (the second must be refused). "Other" joins with Kageno equipped (pulls refused). Every
 * change of a player's count or stand is logged to server.txt. Not shipped in the mod jar.
 */
public final class GachaMpServer implements ModInitializer {
    static final Path OUT=Path.of(System.getProperty("botand.mp.out","mp"));
    private final Map<UUID,String> seen=new HashMap<>();
    private int stage,wait,mark;

    @Override public void onInitialize(){
        if(!Boolean.getBoolean("botand.mp.server"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(this::started);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->server.execute(()->joined(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->note(server,"left "+handler.player.getGameProfile().getName()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }
    private void note(MinecraftServer server,String line){
        try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("server.txt"),server.getOverworld().getTime()+" "+line+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}
    }
    private void started(MinecraftServer server){
        var w=server.getOverworld();MapGate.designate(w,true);
        w.setTimeOfDay(6000);w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
        w.setWeather(999999,0,false,false);
        int ground=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)w.setBlockState(new BlockPos(x,ground-1,z),((x>>2)+(z>>2)&1)==0?Blocks.LIGHT_GRAY_CONCRETE.getDefaultState():Blocks.POLISHED_ANDESITE.getDefaultState());
        note(server,"started map="+MapGate.enabled(w)+" dedicated="+server.isDedicated());
    }
    private static boolean puller(ServerPlayerEntity p){return p.getGameProfile().getName().equals("Puller");}
    private void count(ServerPlayerEntity p,int sinceSsr,int sinceSr){var s=GachaState.get(p.getServer());s.set(p.getUuid(),new GachaRules.Count(sinceSsr,sinceSr,s.get(p.getUuid()).pulls()));}
    private void joined(ServerPlayerEntity p){
        p.changeGameMode(GameMode.SURVIVAL);
        if(puller(p)){ProgressManager.set(p,ProgressManager.profile(p).withStand("none"));count(p,99,0);}
        else ProgressManager.set(p,ProgressManager.profile(p).withStand("kageno"));
        note(p.getServer(),"joined "+p.getGameProfile().getName()+" stand="+ProgressManager.profile(p).stand());
    }
    private void tick(MinecraftServer server){
        for(var p:server.getPlayerManager().getPlayerList()){
            var c=GachaState.get(server).get(p.getUuid());String now="stand="+ProgressManager.profile(p).stand()+" count: SSR in "+c.toSsr()+", SR in "+c.toSr()+", "+c.pulls()+" pulls";
            if(!now.equals(seen.put(p.getUuid(),now)))note(server,p.getGameProfile().getName()+" "+now);
            if(!puller(p))continue;
            boolean kageno=ProgressManager.profile(p).stand().equals("kageno");
            // after the SSR single pull (and the client's scene, results and a pull refused while Kageno is on):
            // stand off, a ten-pull with an SR on the 4th and an SSR on the 6th
            if(stage==0&&kageno&&c.pulls()>=1&&++wait>240){ProgressManager.set(p,ProgressManager.profile(p).withStand("none"));count(p,94,6);note(server,"Puller: stand off, count set for the ten-pull");stage=1;wait=0;mark=c.pulls();}
            // after the ten-pull and its scene (about 12 s since 2.40.0) and its results: stand off again for the double request
            if(stage==1&&c.pulls()>=mark+10&&++wait>520){ProgressManager.set(p,ProgressManager.profile(p).withStand("none"));count(p,0,0);note(server,"Puller: stand off for the double request");stage=2;wait=0;}
        }
    }
}
