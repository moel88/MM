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
 * and the watcher (Botand health, healed every tick) stands still just outside the open wall from 250 to 276 (the
 * wall shake) and is moved into it at 320 and out again at 360, to log how soon the hold takes and lets go; the
 * watcher's client measures its walking speed outside and inside. Since 2.38.2 it also logs E walked and aimed (how far
 * the caster moved and turned, the planted way, and every hit on the watcher), when R's slowdown leaves the caster, the
 * way each T held slash was thrown, and an Atomic cast by the dummy (/botand dummy atomic): the watcher stands inside
 * its column, and every player hurt or held by it is logged (there should be none), as is the dummy going when it ends.
 * Not shipped in the mod jar.
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
        if(caster(p)){ProgressManager.set(p,new ProgressProfile(1,0,0,"kageno",10,0));NoCooldownState.get(p.getServer()).set(p.getUuid(),true);p.getServer().getPlayerManager().addToOperators(p.getGameProfile());}
        home(p);
        note(p.getServer(),"joined "+p.getGameProfile().getName()+" kageno="+StandManager.kageno(p));
    }
    private void home(ServerPlayerEntity p){boolean c=caster(p);p.setVelocity(0,0,0);p.networkHandler.requestTeleport(.5,ground,c?.5:3,c?0:180,0);}
    private void tick(MinecraftServer server){
        boolean busy=false;var seen=new HashSet<UUID>();
        for(var c:KagenoManager.casts()){
            seen.add(c.id);int[] info=live.get(c.id);
            if(info==null){live.put(c.id,info=new int[]{c.skill,0});note(server,"cast "+c.skill+" by "+c.player.getGameProfile().getName());
                // T held: the watcher stands on the line 12 blocks ahead, for the fire slashes to strike and the dash to
                // come to, and a still husk stands behind it 18 blocks ahead (a slash ends on the first one it reaches).
                if(c.skill==KagenoRules.RUSH){
                    var f=net.minecraft.util.math.Vec3d.fromPolar(0,c.yaw);
                    for(var p:server.getPlayerManager().getPlayerList())if(!caster(p)){
                        var at=c.origin.add(f.multiply(12));
                        p.setVelocity(0,0,0);p.networkHandler.requestTeleport(at.x,ground,at.z,c.yaw+180,0);note(server,"watcher moved onto the T+ line (12 blocks ahead)");}
                    var husk=net.minecraft.entity.EntityType.HUSK.create(server.getOverworld());
                    if(husk!=null){var at=c.origin.add(f.multiply(18));husk.refreshPositionAndAngles(at.x,ground,at.z,c.yaw+180,0);husk.setAiDisabled(true);husk.setPersistent();husk.setSilent(true);
                        husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(4000);husk.setHealth(4000);server.getOverworld().spawnEntity(husk);behind=husk;note(server,"husk behind the watcher (18 blocks ahead)");}
                }}
            if(c.skill==KagenoRules.RUSH)rush(server,c);
            if(c.skill==KagenoRules.E)thrusts(server,c);
            if(c.skill==KagenoRules.R)walkingR(server,c);
            info[1]=Math.max(info[1],c.targets.size());
            if(c.age()<(c.skill==KagenoRules.Q?30:KagenoRules.DURATION[c.skill]))busy=true;
        }
        for(var it=live.entrySet().iterator();it.hasNext();){var e=it.next();if(!seen.contains(e.getKey())){note(server,"end "+e.getValue()[0]+" targets="+e.getValue()[1]);it.remove();}}
        if(AtomicManager.hasActive()&&!atomic){
            atomic=true;atomicStart=server.getOverworld().getTime();dummy=atomicDummy(server);
            if(dummy!=null){note(server,String.format(java.util.Locale.ROOT,"atomic start, cast by the dummy at %.1f %.1f %.1f",dummy.getX(),dummy.getY(),dummy.getZ()));inside(server);}
            else{note(server,"atomic start");outside(server);targets(server);}
        }
        else if(!AtomicManager.hasActive()&&atomic){atomic=false;note(server,"atomic end");if(dummy!=null)dummyGone=server.getOverworld().getTime()+2;else clearTargets(server);holdLogged.clear();}
        if(atomic&&dummy==null){watchTargets(server);for(var p:server.getPlayerManager().getPlayerList())if(hurtNow(p))note(server,"player "+p.getGameProfile().getName()+" hurt during the caster's Atomic at cast age "+(server.getOverworld().getTime()-atomicStart));}
        if(atomic&&dummy!=null)watchDummy(server);
        if(dummyGone>0&&server.getOverworld().getTime()>=dummyGone){
            int left=0;for(var e:server.getOverworld().iterateEntities())if(e.getCommandTags().contains(dev.sangwoo.celestialfall.training.TrainingDummies.ATOMIC_TAG))left++;
            note(server,"atomic dummies left after the cast: "+left);dummyGone=0;dummy=null;
        }
        busy|=atomic;
        punched(server);
        for(var p:server.getPlayerManager().getPlayerList()){
            var last=logged.get(p.getUuid());
            if(last==null||last.squaredDistanceTo(p.getPos())>1){logged.put(p.getUuid(),p.getPos());note(server,String.format(java.util.Locale.ROOT,"pos %s %.2f %.2f %.2f",p.getGameProfile().getName(),p.getX(),p.getY(),p.getZ()));}
            if(p.isAlive()&&p.getHealth()<p.getMaxHealth())p.setHealth(p.getMaxHealth());
        }
        idle=!busy;
    }
    private final Set<UUID> struck=new HashSet<>();private net.minecraft.entity.mob.HuskEntity behind;
    private net.minecraft.entity.Entity dummy;private long dummyGone;private final Map<UUID,Integer> hurtSeen=new HashMap<>();
    /**
     * Whether the player was hurt since the last server tick: a blow raises its hurt time back to 10, whichever way this
     * tick and the skill's damage are ordered (the entity tick counts it down by one before this runs).
     */
    private boolean hurtNow(ServerPlayerEntity p){Integer before=hurtSeen.put(p.getUuid(),p.hurtTime);return p.hurtTime>=9&&(before==null||p.hurtTime>before);}
    /** The Atomic dummy casting now, if the cast that just started is its. */
    private static net.minecraft.entity.Entity atomicDummy(MinecraftServer server){
        for(var e:server.getOverworld().iterateEntities())if(e.getCommandTags().contains(dev.sangwoo.celestialfall.training.TrainingDummies.ATOMIC_TAG)&&AtomicManager.active(e.getUuid()))return e;
        return null;
    }
    /** For the dummy's Atomic the watcher stands 6 blocks from the dummy, well inside the column it opens. */
    private void inside(MinecraftServer server){
        for(var p:server.getPlayerManager().getPlayerList())if(!caster(p)){var at=dummy.getPos().add(0,0,6);p.setVelocity(0,0,0);p.networkHandler.requestTeleport(at.x,dummy.getY(),at.z,180,0);note(server,"watcher moved into the dummy's column (6 blocks from it)");}
    }
    /** Every player the dummy's Atomic hurts or holds (none should be) and when its damage column has opened past the watcher. */
    private void watchDummy(MinecraftServer server){
        long age=server.getOverworld().getTime()-atomicStart;
        for(var p:server.getPlayerManager().getPlayerList()){
            if(hurtNow(p))note(server,"player "+p.getGameProfile().getName()+" hurt during the dummy's Atomic at cast age "+age);
            hold(server,"player "+p.getGameProfile().getName(),p,age);
        }
        if(age==AtomicTimeline.BEAM_END)note(server,String.format(java.util.Locale.ROOT,"dummy's Atomic at the end of its beam: damage radius reached %.1f blocks, watcher %.1f blocks from the dummy",
            AtomicTimeline.damageRadius(AtomicTimeline.BEAM_END-1,dev.sangwoo.celestialfall.CelestialFall.CONFIG.atomicVisualRadius),
            server.getPlayerManager().getPlayerList().stream().filter(q->!caster(q)).mapToDouble(q->q.distanceTo(dummy)).findFirst().orElse(-1)));
    }
    /** E walked and aimed: where the caster went and turned by the plant, the planted way, and each hit on the watcher. */
    private void thrusts(MinecraftServer server,KagenoManager.Cast c){
        int age=c.age();var f=net.minecraft.util.math.Vec3d.fromPolar(0,c.yaw);
        for(var p:server.getPlayerManager().getPlayerList())if(!caster(p)&&hurtNow(p))note(server,String.format(java.util.Locale.ROOT,"watcher hit by E at cast age %d (caster facing %.0f degrees from the cast facing)",age,net.minecraft.util.math.MathHelper.wrapDegrees(c.player.getYaw()-c.yaw)));
        if(age==KagenoRules.E_PLANT+1){
            var d=c.player.getPos().subtract(c.origin);
            note(server,String.format(java.util.Locale.ROOT,"E planted: caster walked %.2f blocks (%.2f ahead, %.2f to the right) and faces %.0f degrees right of the cast facing",
                d.horizontalLength(),d.dotProduct(f),d.dotProduct(new net.minecraft.util.math.Vec3d(-f.z,0,f.x)),net.minecraft.util.math.MathHelper.wrapDegrees(c.player.getYaw()-c.yaw)));
        }
        if(age==KagenoRules.E_FINISH+1){
            var d=c.player.getPos().subtract(c.origin);
            note(server,String.format(java.util.Locale.ROOT,"E finisher: caster %.2f blocks from the cast spot, planted way %.0f degrees right of the cast facing",d.horizontalLength(),net.minecraft.util.math.MathHelper.wrapDegrees(planted(c)-c.yaw)));
        }
    }
    /** The way E's finisher was planted (the cast's first aim), read the way the CAST packet carries it. */
    private static float planted(KagenoManager.Cast c){
        try{var f=KagenoManager.Cast.class.getDeclaredField("aims");f.setAccessible(true);return ((float[])f.get(c))[0];}catch(ReflectiveOperationException e){return Float.NaN;}
    }
    /** R's slowdown on its caster around the end of R's input window (2.38.2: it ends there, not with the effects). */
    private void walkingR(MinecraftServer server,KagenoManager.Cast c){
        int age=c.age();if(age!=KagenoRules.inputEnd(KagenoRules.R)-1&&age!=KagenoRules.inputEnd(KagenoRules.R)+1&&age!=KagenoRules.DURATION[KagenoRules.R]-2)return;
        var speed=c.player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        note(server,String.format(java.util.Locale.ROOT,"R at cast age %d: caster slowdown %s, speed attribute x%.2f, sprinting %s",age,speed.getModifier(KagenoManager.R_SLOW)!=null?"on":"off",speed.getValue()/speed.getBaseValue(),c.player.isSprinting()));
    }
    /** T held: when the watcher is first held (a fire slash struck it), where the caster arrives, and the watcher thrown by the spin cut. */
    private void rush(MinecraftServer server,KagenoManager.Cast c){
        int age=c.age();
        for(var p:server.getPlayerManager().getPlayerList()){
            if(caster(p))continue;
            if(c.targets.containsKey(p)&&struck.add(c.id))note(server,"watcher struck and held by T+ at cast age "+age+String.format(java.util.Locale.ROOT," (%.2f blocks ahead of the cast spot)",p.getPos().subtract(c.origin).dotProduct(net.minecraft.util.math.Vec3d.fromPolar(0,c.yaw))));
            if(age==KagenoRules.RUSH_BURST+1)note(server,String.format(java.util.Locale.ROOT,"T+ after the arrival: caster %.2f blocks up the line, %.2f blocks from the watcher",c.player.getPos().subtract(c.origin).dotProduct(net.minecraft.util.math.Vec3d.fromPolar(0,c.yaw)),c.player.getPos().distanceTo(p.getPos())));
            if(age==KagenoRules.RUSH_FINISH+6)note(server,String.format(java.util.Locale.ROOT,"T+ after the spin cut: watcher %.2f blocks from the caster",c.player.getPos().distanceTo(p.getPos())));
        }
        if(age==KagenoRules.RUSH_START){
            try{var f=KagenoManager.Cast.class.getDeclaredField("aims");f.setAccessible(true);float[] a=(float[])f.get(c);
                note(server,String.format(java.util.Locale.ROOT,"T+ slash ways (degrees right of the cast facing): %.0f, %.0f, %.0f",
                    net.minecraft.util.math.MathHelper.wrapDegrees(a[0]-c.yaw),net.minecraft.util.math.MathHelper.wrapDegrees(a[1]-c.yaw),net.minecraft.util.math.MathHelper.wrapDegrees(a[2]-c.yaw)));
            }catch(ReflectiveOperationException e){note(server,"T+ slash ways: "+e);}
        }
        if(age==KagenoRules.DURATION[KagenoRules.RUSH]-2&&behind!=null){
            note(server,String.format(java.util.Locale.ROOT,"T+ husk behind the watcher: health %.0f of %.0f",behind.getHealth(),behind.getMaxHealth()));behind.discard();behind=null;}
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
            // Standing still just outside the fully open wall (3 blocks out) for the wall shake, back out before its walk at 280.
            if(age==250){p.setVelocity(0,0,0);p.networkHandler.requestTeleport(.5,ground,31.5,180,0);note(server,"watcher moved next to the wall (31 blocks) at cast age "+age);}
            if(age==276){int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,64);p.setVelocity(0,0,0);p.networkHandler.requestTeleport(.5,y,64.5,180,0);note(server,"watcher moved out (64 blocks) at cast age "+age);}
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
        // a player with Star Platinum out walks with it (2.51.0): left where they are; and a player Star Platinum's
        // punches threw (2.54.0) is left where it came down
        for(var p:server.getPlayerManager().getPlayerList()){boolean c=caster(p);if(p.isOnGround()&&!dev.sangwoo.celestialfall.stand.StandSummon.out(p)&&!thrownLately(p,server)&&p.squaredDistanceTo(.5,ground,c?.5:3)>1.2*1.2)home(p);}
    }
    private final Map<UUID,Long> thrown=new HashMap<>();private final Map<UUID,Long> flying=new HashMap<>();private int rushBlows;private float rushDamage;
    private boolean thrownLately(ServerPlayerEntity p,MinecraftServer server){Long at=thrown.get(p.getUuid());return at!=null&&server.getOverworld().getTime()-at<200;}
    /**
     * Star Platinum's punches (2.54.0) on the watcher while the caster has it out: every hit (the damage, how fast it
     * leaves, how far from the caster) and, for one that throws it, where it comes down.
     */
    private void punched(MinecraftServer server){
        var caster=server.getPlayerManager().getPlayerList().stream().filter(MpServer::caster).findFirst().orElse(null);
        if(caster==null||!dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punches(caster))return;
        long now=server.getOverworld().getTime();
        for(var p:server.getPlayerManager().getPlayerList()){
            if(caster(p))continue;
            if(hurtNow(p)){
                double speed=p.getVelocity().horizontalLength();
                // 2.57.0: a blow of the rush (counted; every tenth logged), or a punch
                if(dev.sangwoo.celestialfall.stand.StarPlatinumRush.rushing(caster)){rushBlows++;rushDamage+=p.getMaxHealth()-p.getHealth();
                    if(rushBlows==1||rushBlows%10==0)note(server,String.format(java.util.Locale.ROOT,"watcher struck by the rush: blow %d, %.1f damage so far, leaving at %.2f blocks a tick, %.2f blocks from the caster",rushBlows,rushDamage,speed,p.distanceTo(caster)));
                    continue;}
                note(server,String.format(java.util.Locale.ROOT,"watcher punched by Star Platinum: %.1f damage, leaving at %.2f blocks a tick, %.2f blocks from the caster",p.getMaxHealth()-p.getHealth(),speed,p.distanceTo(caster)));
                if(speed>.8){thrown.put(p.getUuid(),now);flying.put(p.getUuid(),now);}
            }
            Long since=flying.get(p.getUuid());
            if(since!=null&&now-since>2&&p.isOnGround()){flying.remove(p.getUuid());
                note(server,String.format(java.util.Locale.ROOT,"watcher came down %d ticks after the throw, %.2f blocks from the caster",now-since,p.distanceTo(caster)));}
        }
    }
}
