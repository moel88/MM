package dev.sangwoo.botandmp;

import dev.sangwoo.celestialfall.AtomicManager;
import dev.sangwoo.celestialfall.AtomicTimeline;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.progression.*;
import dev.sangwoo.celestialfall.stand.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributes;
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
 * Kageno cast is running. During G, still husks stand 4, 12, 20 and 26 blocks from the caster and the
 * cast age of the first hit on each is logged (the damage column opens with the pillar), with the ages the
 * column holds each one (75 % slower) and lets it go; they have enough health to outlast every hit. The 20-block husk is moved out of the column at age 300,
 * and the watcher (Botand health, healed every tick) stands just outside the open wall from 270 to 300 (the wall
 * shake) and is moved into it at 320 and out again at 360, to log how soon the hold takes and lets go; the
 * watcher's client measures its walking speed outside and inside. Not shipped in the mod jar.
 */
public final class MpServer implements ModInitializer {
    static final Path OUT=Path.of(System.getProperty("botand.mp.out","mp"));
    private final Map<UUID,int[]> live=new HashMap<>();private final Map<UUID,net.minecraft.util.math.Vec3d> logged=new HashMap<>();
    private int ground=Integer.MIN_VALUE;private boolean atomic,idle;
    private long atomicStart;private final Map<UUID,double[]> targets=new LinkedHashMap<>();
    private final Map<UUID,Long> holdLogged=new HashMap<>();

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
        if(AtomicManager.hasActive()&&!atomic){atomic=true;atomicStart=server.getOverworld().getTime();note(server,"atomic start");outside(server);targets(server);}
        else if(!AtomicManager.hasActive()&&atomic){atomic=false;note(server,"atomic end");clearTargets(server);holdLogged.clear();}
        if(atomic)watchTargets(server);
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
    /** Still husks (no AI, sun-proof) in a line away from the watcher, at known distances from the caster. */
    private void targets(MinecraftServer server){
        var w=server.getOverworld();
        for(double d:new double[]{4,12,20,26}){
            var husk=net.minecraft.entity.EntityType.HUSK.create(w);if(husk==null)continue;
            husk.refreshPositionAndAngles(.5-d,ground,.5,90,0);husk.setAiDisabled(true);husk.setPersistent();husk.setSilent(true);
            // Enough health to outlast every hit, so the hold can be watched until it lets go.
            husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(4000);husk.setHealth(4000);
            w.spawnEntity(husk);targets.put(husk.getUuid(),new double[]{d,-1,husk.getHealth(),-1});
        }
        note(server,"targets "+targets.size());
    }
    private void watchTargets(MinecraftServer server){
        var w=server.getOverworld();long age=w.getTime()-atomicStart;
        for(var e:targets.entrySet()){
            var t=e.getValue();var mob=w.getEntity(e.getKey());
            if(t[1]<0){
                float health=mob instanceof net.minecraft.entity.LivingEntity l&&l.isAlive()?l.getHealth():0;
                if(health<t[2]){t[1]=age;note(server,String.format(java.util.Locale.ROOT,"target %.0f blocks first hit at cast age %d",t[0],age));}
            }
            if(mob instanceof net.minecraft.entity.LivingEntity l)hold(server,String.format(java.util.Locale.ROOT,"target %.0f blocks",t[0]),l,age);
            // The 20-block husk leaves the column once it is fully open.
            if(t[0]==20&&age==300&&mob!=null){mob.refreshPositionAndAngles(.5-40,ground,.5,90,0);note(server,"target 20 blocks moved out to 40 blocks at cast age "+age);}
        }
        for(var p:server.getPlayerManager().getPlayerList()){
            if(caster(p))continue;
            // Just outside the fully open wall (3 blocks out) for the wall shake, then back out before stepping in.
            if(age==270){p.setVelocity(0,0,0);p.networkHandler.requestTeleport(.5,ground,31.5,180,0);note(server,"watcher moved next to the wall (31 blocks) at cast age "+age);}
            if(age==300){int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,64);p.setVelocity(0,0,0);p.networkHandler.requestTeleport(.5,y,64.5,180,0);note(server,"watcher moved out (64 blocks) at cast age "+age);}
            if(age==320){
                p.setVelocity(0,0,0);p.networkHandler.requestTeleport(.5,ground,10.5,180,0);note(server,"watcher moved into the column (10 blocks) at cast age "+age);
            }
            if(age==360){
                int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,64);
                p.setVelocity(0,0,0);p.networkHandler.requestTeleport(.5,y,64.5,180,0);note(server,"watcher moved out (64 blocks) at cast age "+age);
            }
            hold(server,"watcher",p,age);
        }
        if(age==AtomicTimeline.BEAM_END+1){
            int left=0;
            for(var id:targets.keySet())if(w.getEntity(id) instanceof net.minecraft.entity.LivingEntity l&&held(l))left++;
            for(var p:server.getPlayerManager().getPlayerList())if(held(p))left++;
            note(server,"held after the beam: "+left);
        }
    }
    private static boolean held(net.minecraft.entity.LivingEntity e){
        var speed=e.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        return speed!=null&&speed.getModifier(AtomicTimeline.HIT_SLOW_ID)!=null;
    }
    /** Logs each change of an entity's G hold with its speed against its base. */
    private void hold(MinecraftServer server,String name,net.minecraft.entity.LivingEntity e,long age){
        boolean now=held(e);Long since=holdLogged.get(e.getUuid());
        if(now==(since!=null))return;
        if(now)holdLogged.put(e.getUuid(),age);else holdLogged.remove(e.getUuid());
        var speed=e.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        note(server,String.format(java.util.Locale.ROOT,"%s %s at cast age %d (speed attribute x%.2f)",name,now?"held":"released",age,speed.getValue()/speed.getBaseValue()));
    }
    private void clearTargets(MinecraftServer server){
        var w=server.getOverworld();
        for(var e:targets.entrySet()){
            if(e.getValue()[1]<0)note(server,String.format(java.util.Locale.ROOT,"target %.0f blocks never hit",e.getValue()[0]));
            var mob=w.getEntity(e.getKey());if(mob!=null)mob.discard();
        }
        targets.clear();
    }
    private void restore(MinecraftServer server){
        if(!idle||ground==Integer.MIN_VALUE||server.getTicks()%10!=0)return;
        for(var p:server.getPlayerManager().getPlayerList()){boolean c=caster(p);if(p.isOnGround()&&p.squaredDistanceTo(.5,ground,c?.5:3)>1.2*1.2)home(p);}
    }
}
