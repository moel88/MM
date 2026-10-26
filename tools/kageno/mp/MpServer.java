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
    private boolean bossPlaced;
    /**
     * 2.69.0, on the first tick (once the mod's own start has brought back a boss that was in play): Kujo Jotaro stands
     * 8 blocks behind the caster's mark before either client joins (each must be shown him as they come in), neutral;
     * a server started again finds him where the last one left him, with the health he had (MP_RESTART).
     */
    private void placeBoss(MinecraftServer server){
        bossPlaced=true;var w=server.getOverworld();
        var b=dev.sangwoo.celestialfall.boss.Bosses.live(dev.sangwoo.celestialfall.boss.BossKind.JOTARO);
        String how="brought back by the mod";
        if(b==null){how="new";b=dev.sangwoo.celestialfall.boss.Bosses.spawn(w,dev.sangwoo.celestialfall.boss.BossKind.JOTARO,new net.minecraft.util.math.Vec3d(.5,ground,-7.5),0,dev.sangwoo.celestialfall.boss.BossKind.JOTARO.health);}
        note(server,b==null?"ERROR no boss":String.format(java.util.Locale.ROOT,"boss %s (%s) at %.1f %.1f %.1f health %.0f (in the player list: %s)",b.getUuid(),how,b.getX(),b.getY(),b.getZ(),b.getHealth(),server.getPlayerManager().getPlayerList().contains(b)));
    }
    private static boolean caster(ServerPlayerEntity p){return p.getGameProfile().getName().equals("Caster");}
    private void joined(ServerPlayerEntity p){
        p.changeGameMode(GameMode.SURVIVAL);
        // (2.67.0: the time stop's own check keeps the caster's cooldowns, to see a second H refused)
        if(caster(p)){ProgressManager.set(p,new ProgressProfile(1,0,0,ARROW_ONLY?"none":"kageno",10,0));NoCooldownState.get(p.getServer()).set(p.getUuid(),!TIMESTOP_ONLY);p.getServer().getPlayerManager().addToOperators(p.getGameProfile());}
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
        if(STAGGER_ONLY)staggers(server);
        if(BOSS_ONLY&&!bossPlaced)placeBoss(server);
        if(BOSS_ONLY)boss(server);
        if(ARROW_ONLY)arrow(server);
        for(var p:server.getPlayerManager().getPlayerList()){
            var last=logged.get(p.getUuid());
            if(last==null||last.squaredDistanceTo(p.getPos())>1){logged.put(p.getUuid(),p.getPos());note(server,String.format(java.util.Locale.ROOT,"pos %s %.2f %.2f %.2f",p.getGameProfile().getName(),p.getX(),p.getY(),p.getZ()));}
            // (the beatdown's own check, 2.64.0: its damage is the point, so nobody is healed but by the script's HEAL)
            if(p.isAlive()&&p.getHealth()<p.getMaxHealth()&&!BEATDOWN_ONLY&&!TIMESTOP_ONLY&&!ARROW_ONLY&&(!BOSS_ONLY||caster(p)))p.setHealth(p.getMaxHealth());
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
        if(!idle||ground==Integer.MIN_VALUE||server.getTicks()%10!=0||BOSS_ONLY||ARROW_ONLY)return;
        // a player with Star Platinum out walks with it (2.51.0): left where they are; and a player Star Platinum's
        // punches threw (2.54.0) is left where it came down
        // (2.60.0: and while the caster has Star Platinum out, the watcher stays where the script puts it: 4.5 blocks
        // in front of the caster for the Star Finger)
        boolean standOut=server.getPlayerManager().getPlayerList().stream().anyMatch(q->caster(q)&&dev.sangwoo.celestialfall.stand.StandSummon.out(q));
        for(var p:server.getPlayerManager().getPlayerList()){boolean c=caster(p);if(!c&&standOut)continue;
            if(p.isOnGround()&&!dev.sangwoo.celestialfall.stand.StandSummon.out(p)&&!thrownLately(p,server)&&p.squaredDistanceTo(.5,ground,c?.5:3)>1.2*1.2)home(p);}
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
                if(speed>.8){thrown.put(p.getUuid(),now);flying.put(p.getUuid(),now);from.put(p.getUuid(),p.getPos());}
            }
            Long since=flying.get(p.getUuid());
            if(since!=null&&now-since>2&&p.isOnGround()){flying.remove(p.getUuid());var start=from.remove(p.getUuid());
                note(server,String.format(java.util.Locale.ROOT,"watcher came down %d ticks after the throw, %.2f blocks from the caster, %.2f blocks from where it was struck",now-since,p.distanceTo(caster),start==null?-1:Math.hypot(p.getX()-start.x,p.getZ()-start.z)));}
            // 2.59.0: the watcher's guard as the server keeps it (the heavy punch breaks it)
            boolean up=dev.sangwoo.celestialfall.combat.CombatManager.guarding(p);
            if(up!=Boolean.TRUE.equals(guardUp.put(p.getUuid(),up)))note(server,"watcher guard "+(up?"up":"down"));
            // 2.60.0: the watcher incapacitated as the server keeps it (the broken guard, the Star Finger), its speed
            boolean off=dev.sangwoo.celestialfall.combat.CombatManager.incapacitated(p);
            var speed=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if(off!=Boolean.TRUE.equals(disabled.put(p.getUuid(),off)))note(server,String.format(java.util.Locale.ROOT,"watcher %s (speed attribute x%.2f, %.2f blocks from the caster)",off?"incapacitated":"free again",speed.getValue()/speed.getBaseValue(),p.distanceTo(caster)));
            String sp=String.format(java.util.Locale.ROOT,"x%.2f",speed.getValue()/speed.getBaseValue());
            if(!sp.equals(speeds.put(p.getUuid(),sp)))note(server,"watcher speed attribute "+sp);
            // 2.61.0: the watcher held by the caster's inhale as the server keeps it, and where it is from the caster
            // 2.62.0: the caster's inhale on and off as the server keeps it (held down: off the tick Y comes up)
            boolean on=dev.sangwoo.celestialfall.stand.StarPlatinumInhale.inhaling(caster);
            if(on!=Boolean.TRUE.equals(inhaling.put(caster.getUuid(),on)))note(server,"caster's inhale "+(on?"on":"off")+" at tick "+now);
            boolean held=dev.sangwoo.celestialfall.stand.StarPlatinumInhale.held(p);
            if(held!=Boolean.TRUE.equals(inhaled.put(p.getUuid(),held))){note(server,"watcher "+(held?"caught by the inhale":"let go by the inhale")+" "+where(p,caster));if(held)heldAt=now;}
            if(held&&(now-heldAt)%20==0)note(server,"watcher held "+(now-heldAt)+" ticks: "+where(p,caster));
            // 2.64.0: the watcher in the caster's beatdown's cutscene as the server keeps it, its health through it, its
            // death (the fall kills it as it comes down) and where it is put down
            boolean in=dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown.involved(p);
            if(in!=Boolean.TRUE.equals(beaten.put(p.getUuid(),in)))note(server,String.format(java.util.Locale.ROOT,"watcher %s at tick %d, health %.1f %s",in?"in the beatdown's cutscene":"out of the beatdown's cutscene",now,p.getHealth(),where(p,caster)));
            Float before=healths.put(p.getUuid(),p.getHealth());
            if(before!=null&&Math.abs(before-p.getHealth())>.01f&&(in||BEATDOWN_ONLY||TIMESTOP_ONLY))note(server,String.format(java.util.Locale.ROOT,"watcher health %.1f -> %.1f at tick %d%s",before,p.getHealth(),now,in?" (in the cutscene)":""));
            // 2.67.0: the watcher stopped in the caster's stopped time as the server keeps it, and the caster's time stop
            boolean stopped=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopped(p);
            if(stopped!=Boolean.TRUE.equals(timeStopped.put(p.getUuid(),stopped)))note(server,String.format(java.util.Locale.ROOT,"watcher %s at tick %d, health %.1f %s",stopped?"stopped in time":"moving again",now,p.getHealth(),where(p,caster)));
            boolean calling=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.active(caster);
            if(calling!=Boolean.TRUE.equals(timeStopping.put(caster.getUuid(),calling)))note(server,"caster's time stop "+(calling?"called":"over")+" at tick "+now+", ready again at "+dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.ready(caster));
            // 2.72.0: the caster's awakening as the server keeps it (the time stop is the awakened's), and its stand out
            // or in and whether at its call
            boolean awake=ProgressManager.awakened(caster);
            if(awake!=Boolean.TRUE.equals(awakenings.put(caster.getUuid(),awake)))note(server,"caster "+(awake?"awakened":"not awakened")+" at tick "+now);
            var ch=(dev.sangwoo.celestialfall.stand.StandHolder)caster;String standNow=(ch.botand$isStandOut()?"out":"in")+(ch.botand$standCalled()?" (called)":" (not called)");
            if(!standNow.equals(standStates.put(caster.getUuid(),standNow)))note(server,"caster's stand "+standNow+" at tick "+now);
            boolean alive=p.isAlive();
            if(alive!=!Boolean.FALSE.equals(lives.put(p.getUuid(),alive)))note(server,"watcher "+(alive?"alive again":"died")+" at tick "+now+" "+where(p,caster));
        }
    }
    /** Only the beatdown's script (2.64.0, MP_ONLY=beatdown). */
    static final boolean BEATDOWN_ONLY="beatdown".equals(System.getProperty("botand.mp.only"));
    /** Only the time stop's script (2.67.0, MP_ONLY=timestop). */
    static final boolean TIMESTOP_ONLY="timestop".equals(System.getProperty("botand.mp.only"));
    /** Only the stagger's script (2.68.0, MP_ONLY=stagger). */
    static final boolean STAGGER_ONLY="stagger".equals(System.getProperty("botand.mp.only"));
    /** Only the boss's script (2.69.0, MP_ONLY=boss). */
    static final boolean BOSS_ONLY="boss".equals(System.getProperty("botand.mp.only"));
    /** Only the stand arrow's script (2.70.0, MP_ONLY=arrow). */
    static final boolean ARROW_ONLY="arrow".equals(System.getProperty("botand.mp.only"));
    private boolean arrowHeld,arrowAlive=true;private String arrowStand="";private int arrowCount=-1;private boolean arrowOut;private net.minecraft.util.math.Vec3d arrowFrom;
    /**
     * The stand arrow (2.70.0): the next arrow's choice as the caster's script asks for it (the file OUT/rig), and the
     * caster: held by an arrow, its stand (and whether it is out), the arrows in its main hand, alive or dead, and any
     * move while held (there should be none: the server holds them where they stand).
     */
    private void arrow(MinecraftServer server){
        try{var rig=OUT.resolve("rig");if(Files.exists(rig)){String v=Files.readString(rig).trim();Files.delete(rig);dev.sangwoo.celestialfall.stand.StandArrow.rigged=v;note(server,"next arrow rigged to "+v);}}
        catch(Exception e){note(server,"ERROR rig "+e);}
        for(var p:server.getPlayerManager().getPlayerList()){
            if(!caster(p))continue;
            boolean held=dev.sangwoo.celestialfall.stand.StandArrow.stabbing(p);
            if(held!=arrowHeld){arrowHeld=held;note(server,String.format(java.util.Locale.ROOT,"caster %s at %.2f %.2f %.2f",held?"held by the arrow":"free of the arrow",p.getX(),p.getY(),p.getZ()));arrowFrom=held?p.getPos():null;}
            if(held&&arrowFrom!=null&&p.getPos().distanceTo(arrowFrom)>.05){note(server,String.format(java.util.Locale.ROOT,"ERROR caster moved %.2f blocks while held",p.getPos().distanceTo(arrowFrom)));arrowFrom=p.getPos();}
            String stand=ProgressManager.profile(p).stand();boolean out=((dev.sangwoo.celestialfall.stand.StandHolder)p).botand$isStandOut();
            if(!stand.equals(arrowStand)||out!=arrowOut){arrowStand=stand;arrowOut=out;note(server,"caster stand "+stand+(out?" (out)":""));}
            int count=p.getMainHandStack().isOf(dev.sangwoo.celestialfall.meteor.MeteorRegistry.STAND_ARROW)?p.getMainHandStack().getCount():0;
            if(count!=arrowCount){arrowCount=count;note(server,"caster holds "+count+" stand arrows");}
            if(p.isAlive()!=arrowAlive){arrowAlive=p.isAlive();note(server,"caster "+(arrowAlive?"alive":"dead"));}
        }
    }
    private String bossSeen="";private float bossHealth=-1;private final Map<UUID,Float> lastHealth=new HashMap<>();private final Map<UUID,Long> money=new HashMap<>();
    private int boxesSeen=-1;private net.minecraft.util.math.Vec3d bossFell;private boolean bossStopping;private final Map<UUID,Boolean> bossStopped=new HashMap<>();
    /**
     * Kujo Jotaro (2.69.0) as the server keeps him: in play or not and where, angry or not, whom he goes after and what
     * he does (each change), his health (each blow), his time stop, both players' health (each change: whom he struck),
     * stopped in his time or not, alive, their money (the reward), the boxes he left (how many, how far from where he
     * fell) and the watcher opening one (the caster's script asks with the file OUT/openbox).
     */
    private void boss(MinecraftServer server){
        long now=server.getOverworld().getTime();var w=server.getOverworld();
        var b=dev.sangwoo.celestialfall.boss.Bosses.live(dev.sangwoo.celestialfall.boss.BossKind.JOTARO);
        String seen;
        if(b==null)seen="none";
        else{var t=b.brain().target();seen=String.format(java.util.Locale.ROOT,"%s angry=%s target=%s doing=%s",b.getUuid().toString().substring(0,8),b.brain().angry(),t==null?"-":t.getName().getString(),b.brain().doing().isEmpty()?"-":b.brain().doing());}
        if(!seen.equals(bossSeen)){
            String at="";
            if(b!=null){var sb=new StringBuilder();for(var p:server.getPlayerManager().getPlayerList())sb.append(String.format(java.util.Locale.ROOT,", %s %.1f blocks (health %.1f)",p.getGameProfile().getName(),p.distanceTo(b),p.getHealth()));at=String.format(java.util.Locale.ROOT," at %.1f %.1f, health %.0f",b.getX(),b.getZ(),b.getHealth())+sb;}
            note(server,"boss "+seen+at);bossSeen=seen;
        }
        if(b!=null){
            if(Math.abs(b.getHealth()-bossHealth)>=.5f){note(server,String.format(java.util.Locale.ROOT,"boss health %.1f -> %.1f",bossHealth,b.getHealth()));bossHealth=b.getHealth();}
            bossFell=b.getPos();
            boolean calling=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.active(b);
            if(calling!=bossStopping){note(server,"boss's time stop "+(calling?"called":"over")+" at tick "+now+" (health "+b.getHealth()+")");bossStopping=calling;}
        }else bossHealth=-1;
        for(var p:server.getPlayerManager().getPlayerList()){
            String who=p.getGameProfile().getName();
            Float was=lastHealth.put(p.getUuid(),p.getHealth());
            if(was!=null&&Math.abs(was-p.getHealth())>.01f)note(server,String.format(java.util.Locale.ROOT,"%s health %.1f -> %.1f%s",who,was,p.getHealth(),b!=null&&p.getRecentDamageSource()!=null&&p.getRecentDamageSource().getAttacker()==b?" (the boss)":""));
            boolean stopped=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopped(p);
            if(stopped!=Boolean.TRUE.equals(bossStopped.put(p.getUuid(),stopped)))note(server,who+(stopped?" stopped in time":" moving again")+" at tick "+now);
            long m=ProgressManager.profile(p).money();Long before=money.put(p.getUuid(),m);
            if(before!=null&&before!=m)note(server,who+" money "+before+" -> "+m);
        }
        var boxes=w.getEntitiesByClass(dev.sangwoo.celestialfall.loot.FieldLootEntity.class,new net.minecraft.util.math.Box(-64,ground-16,-64,64,ground+16,64),e->e.loose());
        if(boxes.size()!=boxesSeen){
            var sb=new StringBuilder();for(var e:boxes)sb.append(String.format(java.util.Locale.ROOT," (%.2f, %.2f) %.2f from where he fell%s",e.getX(),e.getZ(),bossFell==null?-1:Math.hypot(e.getX()-bossFell.x,e.getZ()-bossFell.z),e.opened()?" opened":""));
            note(server,"boxes left by the boss: "+boxes.size()+sb);boxesSeen=boxes.size();
        }
        if(Files.exists(OUT.resolve("openbox"))){
            try{Files.delete(OUT.resolve("openbox"));}catch(Exception ignored){}
            var watcher=server.getPlayerManager().getPlayerList().stream().filter(q->!caster(q)).findFirst().orElse(null);
            if(watcher!=null&&!boxes.isEmpty()){
                var box=boxes.stream().min(java.util.Comparator.comparingDouble(e->e.squaredDistanceTo(watcher))).get();
                watcher.networkHandler.requestTeleport(box.getX()+1.2,box.getY(),box.getZ(),90,20);
                var items=new StringBuilder();int before=0;for(int i=0;i<watcher.getInventory().size();i++)before+=watcher.getInventory().getStack(i).getCount();
                boolean near=dev.sangwoo.celestialfall.loot.LootServer.near(watcher,box);boolean ok=near&&dev.sangwoo.celestialfall.loot.LootServer.claim(watcher,box);
                for(int i=0;i<watcher.getInventory().size();i++){var st=watcher.getInventory().getStack(i);if(!st.isEmpty())items.append(' ').append(st.getCount()).append('x').append(net.minecraft.registry.Registries.ITEM.getId(st.getItem()));}
                note(server,"watcher opens a box: near="+near+" claimed="+ok+" opened="+box.opened()+" inventory:"+items+" (a second claim: "+dev.sangwoo.celestialfall.loot.LootServer.claim(watcher,box)+")");
            }else note(server,"watcher opens a box: none to open");
        }
    }
    private final Map<UUID,Long> staggeredUntil=new HashMap<>(),strikeStarts=new HashMap<>();private final Map<UUID,String> slowSeen=new HashMap<>();
    private final Map<UUID,Integer> hurtStagger=new HashMap<>();private static java.lang.reflect.Method stateOf;
    /**
     * The basic attack's stagger (2.68.0) as the server keeps it, for both players: each stagger (until when, its speed
     * then, where the watcher is), each bare-hand strike started, each blow taken, each change of speed, and the
     * watcher stopped in time or not.
     */
    private void staggers(MinecraftServer server){
        long now=server.getOverworld().getTime();
        var caster=server.getPlayerManager().getPlayerList().stream().filter(MpServer::caster).findFirst().orElse(null);
        for(var p:server.getPlayerManager().getPlayerList()){
            String who=p.getGameProfile().getName();
            dev.sangwoo.celestialfall.combat.CombatState s;
            try{
                if(stateOf==null){stateOf=dev.sangwoo.celestialfall.combat.CombatManager.class.getDeclaredMethod("state",ServerPlayerEntity.class);stateOf.setAccessible(true);}
                s=(dev.sangwoo.celestialfall.combat.CombatState)stateOf.invoke(null,p);
            }catch(ReflectiveOperationException e){note(server,"ERROR "+e);return;}
            var speed=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);String sp=String.format(java.util.Locale.ROOT,"x%.2f",speed.getValue()/speed.getBaseValue());
            boolean on=s.staggered(now);Long was=staggeredUntil.get(p.getUuid());
            if(on&&(was==null||was!=s.staggerUntil)){
                note(server,String.format(java.util.Locale.ROOT,"%s staggered until %d (%d ticks on; speed attribute %s%s)",who,s.staggerUntil,s.staggerUntil-now,sp,caster==null||caster==p?"":", "+where(p,caster)));
                staggeredUntil.put(p.getUuid(),s.staggerUntil);
            }else if(!on&&was!=null){note(server,who+" free of its stagger");staggeredUntil.remove(p.getUuid());}
            if(s.attack>0&&!Long.valueOf(s.attackStart).equals(strikeStarts.put(p.getUuid(),s.attackStart)))note(server,String.format(java.util.Locale.ROOT,"%s's bare-hand strike %d started at %d",who,s.attack,s.attackStart));
            if(!sp.equals(slowSeen.put(p.getUuid(),sp)))note(server,who+" speed attribute "+sp);
            Integer before=hurtStagger.put(p.getUuid(),p.hurtTime);
            if(p.hurtTime>=9&&(before==null||p.hurtTime>before))note(server,String.format(java.util.Locale.ROOT,"%s struck (health %.1f of %.1f)",who,p.getHealth(),p.getMaxHealth()));
            if(!caster(p)){boolean stopped=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopped(p);
                if(stopped!=Boolean.TRUE.equals(timeStopped.put(p.getUuid(),stopped)))note(server,String.format(java.util.Locale.ROOT,"watcher %s at tick %d %s",stopped?"stopped in time":"not stopped (moving)",now,caster==null?"":where(p,caster)));}
            if(caster(p)){boolean calling=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.active(p);
                if(calling!=Boolean.TRUE.equals(timeStopping.put(p.getUuid(),calling)))note(server,"caster's time stop "+(calling?"called":"over")+" at tick "+now);}
        }
    }
    private final Map<UUID,Boolean> timeStopped=new HashMap<>(),timeStopping=new HashMap<>(),awakenings=new HashMap<>();
    private final Map<UUID,String> standStates=new HashMap<>();
    private final Map<UUID,Boolean> beaten=new HashMap<>(),lives=new HashMap<>();private final Map<UUID,Float> healths=new HashMap<>();
    private final Map<UUID,net.minecraft.util.math.Vec3d> from=new HashMap<>();private final Map<UUID,Boolean> guardUp=new HashMap<>(),disabled=new HashMap<>();
    private final Map<UUID,String> speeds=new HashMap<>();private final Map<UUID,Boolean> inhaled=new HashMap<>(),inhaling=new HashMap<>();private long heldAt;
    /** Where the watcher is from the caster: ahead along the way the caster faces, to its right, and how far. */
    private static String where(ServerPlayerEntity p,ServerPlayerEntity caster){
        var d=p.getPos().subtract(caster.getPos());var f=net.minecraft.util.math.Vec3d.fromPolar(0,caster.getYaw());
        return String.format(java.util.Locale.ROOT,"(%.2f blocks ahead of the caster, %.2f to its right, %.2f from it; caster inhaling %s)",d.x*f.x+d.z*f.z,-d.x*f.z+d.z*f.x,p.distanceTo(caster),
            dev.sangwoo.celestialfall.stand.StarPlatinumInhale.inhaling(caster));
    }
}
