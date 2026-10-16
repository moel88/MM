package dev.sangwoo.botandmp;

import dev.sangwoo.celestialfall.AtomicManager;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.stand.*;
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
 * Multiplayer smoke test, dedicated-server half (only with -Dbotand.mp.server=true): a flat lit stage,
 * "Caster" gets Kageno without cooldowns, "Watcher" stands 2.5 blocks in front of it (64 blocks out,
 * outside the damage column, while G runs). Both are kept alive and put back on their marks whenever no
 * Kageno cast is running. Not shipped in the mod jar.
 */
public final class MpServer implements ModInitializer {
    static final Path OUT=Path.of(System.getProperty("botand.mp.out","mp"));
    private final Map<UUID,int[]> live=new HashMap<>();private final Map<UUID,net.minecraft.util.math.Vec3d> logged=new HashMap<>();
    private int ground=Integer.MIN_VALUE;private boolean atomic,idle;

    @Override public void onInitialize(){
        if(!Boolean.getBoolean("botand.mp.server"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(this::started);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->server.execute(()->joined(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->note(server,"left "+handler.player.getGameProfile().getName()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        // Marks are restored before the network tick so vanilla's movement check sees the new spot.
        ServerTickEvents.START_SERVER_TICK.register(this::restore);
    }
    private void note(MinecraftServer server,String line){
        try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("server.txt"),server.getOverworld().getTime()+" "+line+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}
    }
    private void started(MinecraftServer server){
        var w=server.getOverworld();MapGate.designate(w,true);
        w.setTimeOfDay(6000);w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
        w.setWeather(999999,0,false,false);server.setPvpEnabled(true);
        ground=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
        for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++)w.setBlockState(new BlockPos(x,ground-1,z),((x>>2)+(z>>2)&1)==0?Blocks.LIGHT_GRAY_CONCRETE.getDefaultState():Blocks.POLISHED_ANDESITE.getDefaultState());
        note(server,"started ground="+ground+" map="+MapGate.enabled(w)+" dedicated="+server.isDedicated());
    }
    private static boolean caster(ServerPlayerEntity p){return p.getGameProfile().getName().equals("Caster");}
    private void joined(ServerPlayerEntity p){
        p.changeGameMode(GameMode.SURVIVAL);
        if(caster(p)){ProgressManager.set(p,new ProgressProfile(1,0,0,"kageno",10,0));NoCooldownState.get(p.getServer()).set(p.getUuid(),true);}
        home(p);
        note(p.getServer(),"joined "+p.getGameProfile().getName()+" kageno="+StandManager.kageno(p));
    }
    private void home(ServerPlayerEntity p){boolean c=caster(p);p.setVelocity(0,0,0);p.networkHandler.requestTeleport(.5,ground,c?.5:3,c?0:180,0);}
    private void tick(MinecraftServer server){
        boolean busy=false;var seen=new HashSet<UUID>();
        for(var c:KagenoManager.casts()){
            seen.add(c.id);int[] info=live.get(c.id);
            if(info==null){live.put(c.id,info=new int[]{c.skill,0});note(server,"cast "+c.skill+" by "+c.player.getGameProfile().getName());}
            info[1]=Math.max(info[1],c.targets.size());
            if(c.age()<(c.skill==KagenoRules.Q?30:KagenoRules.DURATION[c.skill]))busy=true;
        }
        for(var it=live.entrySet().iterator();it.hasNext();){var e=it.next();if(!seen.contains(e.getKey())){note(server,"end "+e.getValue()[0]+" targets="+e.getValue()[1]);it.remove();}}
        if(AtomicManager.hasActive()&&!atomic){atomic=true;note(server,"atomic start");outside(server);}else if(!AtomicManager.hasActive()&&atomic){atomic=false;note(server,"atomic end");}
        busy|=atomic;
        for(var p:server.getPlayerManager().getPlayerList()){
            var last=logged.get(p.getUuid());
            if(last==null||last.squaredDistanceTo(p.getPos())>1){logged.put(p.getUuid(),p.getPos());note(server,String.format(java.util.Locale.ROOT,"pos %s %.2f %.2f %.2f",p.getGameProfile().getName(),p.getX(),p.getY(),p.getZ()));}
            if(p.isAlive()&&p.getHealth()<p.getMaxHealth())p.setHealth(p.getMaxHealth());
        }
        idle=!busy;
    }
    /** During G the watcher stands 64 blocks out (outside the 28-block damage column), facing the caster. */
    private void outside(MinecraftServer server){
        var w=server.getOverworld();int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,64);
        for(var p:server.getPlayerManager().getPlayerList())if(!caster(p)){p.setVelocity(0,0,0);p.networkHandler.requestTeleport(.5,y,64.5,180,0);note(server,"watcher outside y="+y);}
    }
    private void restore(MinecraftServer server){
        if(!idle||ground==Integer.MIN_VALUE||server.getTicks()%10!=0)return;
        for(var p:server.getPlayerManager().getPlayerList()){boolean c=caster(p);if(p.isOnGround()&&p.squaredDistanceTo(.5,ground,c?.5:3)>1.2*1.2)home(p);}
    }
}
