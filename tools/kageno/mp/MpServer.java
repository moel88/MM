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
        // (2.73.0, MP_ONLY=cooldowns: the caster keeps its cooldowns, and coming back keeps the stand it had; 2.74.3 the same for MP_ONLY=fresh)
        if(caster(p)){if(!COOLDOWNS_ONLY&&!FRESH_ONLY||casterJoins++==0)ProgressManager.set(p,new ProgressProfile(1,0,0,ARROW_ONLY?"none":"kageno",10,0));
            NoCooldownState.get(p.getServer()).set(p.getUuid(),!TIMESTOP_ONLY&&!COOLDOWNS_ONLY&&!SWAP_ONLY&&!FRESH_ONLY&&!ISOSANG_ONLY);p.getServer().getPlayerManager().addToOperators(p.getGameProfile());}
        // (2.107.0, MP_ONLY=death) one who joins dead waits where it fell: its five seconds count from now
        if(DEATH_ONLY&&!p.isAlive()){deadAt.put(p.getUuid(),p.getServer().getOverworld().getTime());note(p.getServer(),String.format(java.util.Locale.ROOT,"%s joined dead at tick %d at %.2f %.2f %.2f",p.getGameProfile().getName(),p.getServer().getOverworld().getTime(),p.getX(),p.getY(),p.getZ()));}
        else home(p);
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
        called(server);
        if(COOLDOWNS_ONLY||SWAP_ONLY||FRESH_ONLY)cooldowns(server);
        if(STAGGER_ONLY||ISOSANG_ONLY)staggers(server);
        if(ISOSANG_ONLY)isosang(server);
        if(RESIST_ONLY)resist(server);
        if(BOSS_ONLY&&!bossPlaced)placeBoss(server);
        if(BOSS_ONLY)boss(server);
        if(ARROW_ONLY||COOLDOWNS_ONLY||SWAP_ONLY||FRESH_ONLY)arrow(server);
        if(DEATH_ONLY)deaths(server);
        if(GLOW_ONLY)glows(server);
        for(var p:server.getPlayerManager().getPlayerList()){
            var last=logged.get(p.getUuid());
            if(last==null||last.squaredDistanceTo(p.getPos())>1){logged.put(p.getUuid(),p.getPos());note(server,String.format(java.util.Locale.ROOT,"pos %s %.2f %.2f %.2f",p.getGameProfile().getName(),p.getX(),p.getY(),p.getZ()));}
            // (the beatdown's own check, 2.64.0: its damage is the point, so nobody is healed but by the script's HEAL)
            if(p.isAlive()&&p.getHealth()<p.getMaxHealth()&&!BEATDOWN_ONLY&&!TIMESTOP_ONLY&&!RESIST_ONLY&&!ARROW_ONLY&&!ISOSANG_ONLY&&(!BOSS_ONLY||caster(p)))p.setHealth(p.getMaxHealth());
        }
        idle=!busy;
    }
    private final Set<UUID> struck=new HashSet<>();private net.minecraft.entity.mob.HuskEntity behind;
    /** The glows as the server keeps them (2.109.0): whose, since when, for which skill. */
    private final Map<UUID,long[]> glowsSeen=new HashMap<>();
    /**
     * Every player's glow before a skill with a cutscene (2.109.0, MP_ONLY=glow, CutsceneGlow): when it begins (their
     * speed against their base), every blow on them meanwhile, and when it ends: cast (the skill begun) or cut short,
     * and the skill ready again how long after.
     */
    private void glows(MinecraftServer server){
        long now=server.getOverworld().getTime();
        for(var p:server.getPlayerManager().getPlayerList()){
            int skill=dev.sangwoo.celestialfall.combat.CutsceneGlow.skill(p);var was=glowsSeen.get(p.getUuid());
            String name=p.getGameProfile().getName();
            if(skill>=0&&was==null){
                glowsSeen.put(p.getUuid(),new long[]{now,skill});var speed=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                note(server,String.format(java.util.Locale.ROOT,"%s glows for %s at tick %d (speed x%.2f)",name,glowName(skill),now,speed.getValue()/speed.getBaseValue()));
                hurtNow(p);
            }else if(skill>=0){
                if(hurtNow(p))note(server,String.format(java.util.Locale.ROOT,"%s struck as it glows at tick %d (%d ticks in), health %.1f",name,now,now-was[0],p.getHealth()));
            }else if(was!=null){
                glowsSeen.remove(p.getUuid());int k=(int)was[1];
                boolean cast=switch(k){
                    case dev.sangwoo.celestialfall.combat.CutsceneGlow.KAGENO_H->KagenoManager.casts().stream().anyMatch(c->c.player==p&&c.skill==KagenoRules.H&&c.start>=was[0]);
                    case dev.sangwoo.celestialfall.combat.CutsceneGlow.BEATDOWN->{var pu=dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punch(p);yield pu!=null&&pu.combo()==dev.sangwoo.celestialfall.stand.StarPlatinumRules.BEATDOWN&&pu.start()>=was[0];}
                    case dev.sangwoo.celestialfall.combat.CutsceneGlow.ISOSANG_G->dev.sangwoo.celestialfall.isosang.IsosangAttack.casting(p);
                    default->false;};
                long ready=switch(k){
                    case dev.sangwoo.celestialfall.combat.CutsceneGlow.KAGENO_H->KagenoManager.ready(p,KagenoRules.H);
                    case dev.sangwoo.celestialfall.combat.CutsceneGlow.BEATDOWN->dev.sangwoo.celestialfall.stand.StarPlatinumAttack.beatdownReady(p);
                    case dev.sangwoo.celestialfall.combat.CutsceneGlow.ISOSANG_G->dev.sangwoo.celestialfall.isosang.IsosangAttack.gReady(p);
                    default->0L;};
                note(server,String.format(java.util.Locale.ROOT,"%s's glow for %s over at tick %d, %d ticks after it began: %s; ready again in %.1f s%s",name,glowName(k),now,now-was[0],cast?"CAST":"CUT SHORT",
                    Math.max(0,ready-now)/20.0,dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?" (cooldowns off)":""));
            }
        }
    }
    private static String glowName(int skill){return switch(skill){case 0->"Kageno's H";case 1->"the beatdown";case 2->"Lee So-sang's G";default->"?";};}
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
        // (2.77.0: and in Lee So-sang's script, where it is sent 8 blocks away for the first three hits to strike no one)
        boolean standOut=server.getPlayerManager().getPlayerList().stream().anyMatch(q->caster(q)&&dev.sangwoo.celestialfall.stand.StandSummon.out(q));
        for(var p:server.getPlayerManager().getPlayerList()){boolean c=caster(p);if(!c&&(standOut||ISOSANG_ONLY))continue;
            // (2.82.0: nor a player on Lee So-sang's sword formation, held up on its middle sword)
            if(p.isOnGround()&&!dev.sangwoo.celestialfall.stand.StandSummon.out(p)&&!thrownLately(p,server)&&dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(p)==null&&p.squaredDistanceTo(.5,ground,c?.5:3)>1.2*1.2)home(p);}
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
            if(before!=null&&Math.abs(before-p.getHealth())>.01f&&(in||BEATDOWN_ONLY||TIMESTOP_ONLY||RESIST_ONLY))note(server,String.format(java.util.Locale.ROOT,"watcher health %.1f -> %.1f at tick %d%s",before,p.getHealth(),now,in?" (in the cutscene)":""));
            // 2.67.0: the watcher stopped in the caster's stopped time as the server keeps it, and the caster's time stop
            boolean stopped=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopped(p);
            if(stopped!=Boolean.TRUE.equals(timeStopped.put(p.getUuid(),stopped)))note(server,String.format(java.util.Locale.ROOT,"watcher %s at tick %d, health %.1f %s",stopped?"stopped in time":"moving again",now,p.getHealth(),where(p,caster)));
            boolean calling=dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.active(caster);
            if(calling!=Boolean.TRUE.equals(timeStopping.put(caster.getUuid(),calling)))note(server,"caster's time stop "+(calling?"called":"over")+" at tick "+now+", ready again at "+dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.ready(caster));
            boolean alive=p.isAlive();
            if(alive!=!Boolean.FALSE.equals(lives.put(p.getUuid(),alive)))note(server,"watcher "+(alive?"alive again":"died")+" at tick "+now+" "+where(p,caster));
        }
    }
    /**
     * The caster's awakening as the server keeps it (2.72.0: the time stop is the awakened's), and its stand, out or in
     * and whether at its call: every tick, its stand out or not (from punched, which runs only while it is out, its
     * going back was never seen).
     */
    private void called(MinecraftServer server){
        var caster=server.getPlayerManager().getPlayerList().stream().filter(MpServer::caster).findFirst().orElse(null);
        if(caster==null)return;
        long now=server.getOverworld().getTime();
        boolean awake=ProgressManager.awakened(caster);
        if(awake!=Boolean.TRUE.equals(awakenings.put(caster.getUuid(),awake)))note(server,"caster "+(awake?"awakened":"not awakened")+" at tick "+now);
        var ch=(dev.sangwoo.celestialfall.stand.StandHolder)caster;
        String standNow=ch.botand$getStand()+" "+(ch.botand$isStandOut()?"out":"in")+(ch.botand$standCalled()?" (called)":" (not called)");
        if(!standNow.equals(standStates.put(caster.getUuid(),standNow)))note(server,"caster's stand "+standNow+" at tick "+now);
    }
    private int casterJoins;private String cooldownsLogged="";private Boolean casterAlive;
    /** 2.75.0 (MP_ONLY=resist): the watcher's state in the caster's stopped time last logged, where it was last tick, the caster's health. */
    private String resistLogged="";private net.minecraft.util.math.Vec3d resistLast;private float casterHealth=-1;private long resistSince=-1,stoppedSince=-1;
    /** A blow of 4 on the watcher from the caster through the game's own blow path (as the capture tool's HURT), and the watcher's health right after. */
    private void strike(MinecraftServer server,ServerPlayerEntity w,ServerPlayerEntity caster,String when){
        float before=w.getHealth();boolean ok=dev.sangwoo.celestialfall.combat.CombatManager.damageWithoutKnockback(w,w.getDamageSources().playerAttack(caster),4);
        note(server,String.format(java.util.Locale.ROOT,"a blow of 4 on the watcher %s at tick %d: %s, health %.1f -> %.1f",when,server.getOverworld().getTime(),ok?"landed":"refused",before,w.getHealth()));
    }
    /**
     * Star Platinum's users resisting another's stopped time (2.75.0, MP_ONLY=resist): whenever it changes, the watcher
     * resisting the caster's time stop, stopped in it, or neither (where, and its health), its own time stop called, its
     * inhale on, its beatdown's blow begun (none may while it resists); while the caster's time stop is on, every tick
     * the watcher moved (how far); and every change of the caster's health (the watcher's jab lands at once).
     */
    private void resist(MinecraftServer server){
        var caster=server.getPlayerManager().getPlayerList().stream().filter(MpServer::caster).findFirst().orElse(null);
        var w=server.getPlayerManager().getPlayerList().stream().filter(q->!caster(q)).findFirst().orElse(null);
        if(caster==null||w==null)return;
        long now=server.getOverworld().getTime();
        var punch=dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punch(w);
        String state=(dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.resisting(w)?"resisting the caster's stopped time":dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopped(w)?"stopped in time":"free")
            +(dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.active(w)?", calling its own time stop":"")
            +(dev.sangwoo.celestialfall.stand.StarPlatinumInhale.inhaling(w)?", inhaling":"")
            +(punch!=null&&punch.combo()==dev.sangwoo.celestialfall.stand.StarPlatinumRules.BEATDOWN&&now<punch.start()+dev.sangwoo.celestialfall.stand.StarPlatinumRules.LENGTH[dev.sangwoo.celestialfall.stand.StarPlatinumRules.BEATDOWN]?", its beatdown's blow":"");
        if(!state.equals(resistLogged)){note(server,String.format(java.util.Locale.ROOT,"watcher %s at tick %d (at %.2f %.2f, health %.1f)",state,now,w.getX(),w.getZ(),w.getHealth()));resistLogged=state;
            if(state.startsWith("resisting"))resistSince=now;if(state.startsWith("stopped"))stoppedSince=now;}
        // the server's own checks, its clients' lag aside (a slow software-rendered client can be 50 ticks behind): 5 ticks
        // into resisting, its own time stop, inhale and beatdown asked for straight (all refused while it resists); 8 ticks
        // in, a blow of 4 through the game's own blow path (it lands at once); 10 ticks after it stopped, another (kept
        // until time moves, then dealt)
        if(resistSince>=0&&now==resistSince+5){
            note(server,String.format(java.util.Locale.ROOT,"asked straight while resisting: its own time stop %s, its inhale %s, its beatdown %s",
                dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.request(w)?"STARTED":"refused",dev.sangwoo.celestialfall.stand.StarPlatinumInhale.start(w)?"STARTED":"refused",
                dev.sangwoo.celestialfall.stand.StarPlatinumAttack.beatdown(w)?"STARTED":"refused"));
            dev.sangwoo.celestialfall.stand.StarPlatinumInhale.release(w);
        }
        if(resistSince>=0&&now==resistSince+8)strike(server,w,caster,"while it resists");
        if(stoppedSince>=0&&now==stoppedSince+10)strike(server,w,caster,"stopped two seconds in");
        if(dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.stopping(caster)&&resistLast!=null){
            double step=w.getPos().distanceTo(resistLast);
            if(step>1e-4)note(server,String.format(java.util.Locale.ROOT,"watcher moved %.3f blocks at tick %d (%s)",step,now,state));
        }
        resistLast=w.getPos();
        if(casterHealth>=0&&Math.abs(casterHealth-caster.getHealth())>.01f)note(server,String.format(java.util.Locale.ROOT,"caster health %.1f -> %.1f at tick %d",casterHealth,caster.getHealth(),now));
        casterHealth=caster.getHealth();
    }
    /**
     * The long cooldowns (2.73.0, MP_ONLY=cooldowns): whenever any changes, the caster's as kept with the world
     * (LongCooldowns) and as each skill keeps its own (the Atomic's with its sword's cooldown, the time stop's, the
     * beatdown's), as the tick each is ready again and the seconds from now; and the caster dying and coming back.
     */
    private void cooldowns(MinecraftServer server){
        var caster=server.getPlayerManager().getPlayerList().stream().filter(MpServer::caster).findFirst().orElse(null);
        if(caster==null)return;
        long now=server.getOverworld().getTime();
        if(!Boolean.valueOf(caster.isAlive()).equals(casterAlive)){casterAlive=caster.isAlive();note(server,"caster "+(casterAlive?"alive":"dead")+" at tick "+now);}
        var ledger=dev.sangwoo.celestialfall.LongCooldowns.get(server);var id=caster.getUuid();
        long[] kept={ledger.ready(id,dev.sangwoo.celestialfall.LongCooldowns.Skill.ATOMIC),ledger.ready(id,dev.sangwoo.celestialfall.LongCooldowns.Skill.TIME_STOP),ledger.ready(id,dev.sangwoo.celestialfall.LongCooldowns.Skill.BEATDOWN)};
        long[] own={AtomicManager.ready(id),StarPlatinumTimeStop.ready(caster),StarPlatinumAttack.beatdownReady(caster)};
        // 2.74.2 (MP_ONLY=swap): the short ones too, as Star Platinum's punches (their rest, the heavy punch, the Star
        // Finger), its rush and its inhale keep them, and as Kageno keeps its E's; and the stand of the caster's profile
        var punch=StarPlatinumAttack.punch(caster);
        long[] quick={punch==null?0:punch.ready(),StarPlatinumAttack.heavyReady(caster),StarPlatinumAttack.fingerReady(caster),
            dev.sangwoo.celestialfall.stand.StarPlatinumRush.ready(caster),dev.sangwoo.celestialfall.stand.StarPlatinumInhale.ready(caster),kagenoReady(caster,KagenoRules.E)};
        String stand=ProgressManager.profile(caster).stand();
        // (a cooldown over counts as none, so the line is logged as one starts or goes, not as one runs out)
        long[] live=new long[quick.length];for(int i=0;i<quick.length;i++)live[i]=quick[i]>now?quick[i]:0;
        String key=Arrays.toString(kept)+Arrays.toString(own)+caster.getItemCooldownManager().isCoolingDown(dev.sangwoo.celestialfall.CelestialFall.ATOMIC_SWORD)+(SWAP_ONLY?Arrays.toString(live)+stand:"");
        if(key.equals(cooldownsLogged))return;cooldownsLogged=key;
        String[] names={"atomic","time stop","beatdown"};var line=new StringBuilder("caster's long cooldowns at tick "+now+":");
        for(int i=0;i<3;i++)line.append(String.format(java.util.Locale.ROOT," %s kept %s, its own %s;",names[i],at(kept[i],now),at(own[i],now)));
        line.append(String.format(java.util.Locale.ROOT," sword cooling %.3f",caster.getItemCooldownManager().getCooldownProgress(dev.sangwoo.celestialfall.CelestialFall.ATOMIC_SWORD,0)));
        if(SWAP_ONLY){
            String[] quickNames={"punches' rest","heavy punch","Star Finger","rush","inhale","Kageno E"};
            line.append("; stand ").append(stand).append("; short ones:");
            for(int i=0;i<quick.length;i++)line.append(String.format(java.util.Locale.ROOT," %s %s;",quickNames[i],at(quick[i],now)));
        }
        note(server,line.toString());
    }
    /** The tick Kageno's skill is ready again for this player (0 if it keeps no state for them), read from KagenoManager's states. */
    private static long kagenoReady(ServerPlayerEntity p,int skill){
        try{
            var f=KagenoManager.class.getDeclaredField("STATES");f.setAccessible(true);var state=((Map<?,?>)f.get(null)).get(p);if(state==null)return 0;
            var r=state.getClass().getDeclaredField("ready");r.setAccessible(true);return ((long[])r.get(state))[skill];
        }catch(ReflectiveOperationException e){return -1;}
    }
    private static String at(long ready,long now){return ready<=0?"none":ready<=now?"over ("+ready+")":String.format(java.util.Locale.ROOT,"%d (%.1f s)",ready,(ready-now)/20.);}
    /** Only the script of the long cooldowns begun in full whatever they were (2.74.3, MP_ONLY=fresh). */
    static final boolean FRESH_ONLY="fresh".equals(System.getProperty("botand.mp.only"));
    /** Only the script of another stand's cooldowns gone (2.74.2, MP_ONLY=swap). */
    static final boolean SWAP_ONLY="swap".equals(System.getProperty("botand.mp.only"));
    /** Only the long cooldowns' script (2.73.0, MP_ONLY=cooldowns). */
    static final boolean COOLDOWNS_ONLY="cooldowns".equals(System.getProperty("botand.mp.only"));
    /** Only the beatdown's script (2.64.0, MP_ONLY=beatdown). */
    static final boolean BEATDOWN_ONLY="beatdown".equals(System.getProperty("botand.mp.only"));
    /** (2.109.0) a skill with a cutscene glows first: only its check (MpClient.GLOW_SCRIPT). */
    static final boolean GLOW_ONLY="glow".equals(System.getProperty("botand.mp.only"));
    /** Only the time stop's script (2.67.0, MP_ONLY=timestop). */
    static final boolean TIMESTOP_ONLY="timestop".equals(System.getProperty("botand.mp.only"));
    /** Only the script of Star Platinum's users resisting another's stopped time for two seconds (2.75.0, MP_ONLY=resist). */
    static final boolean RESIST_ONLY="resist".equals(System.getProperty("botand.mp.only"));
    /** Only the stagger's script (2.68.0, MP_ONLY=stagger). */
    static final boolean STAGGER_ONLY="stagger".equals(System.getProperty("botand.mp.only"));
    /** Only the boss's script (2.69.0, MP_ONLY=boss). */
    static final boolean BOSS_ONLY="boss".equals(System.getProperty("botand.mp.only"));
    /** Only the script of coming back to life by itself (2.107.0, MP_ONLY=death). */
    static final boolean DEATH_ONLY="death".equals(System.getProperty("botand.mp.only"));
    /** (2.107.0) The server tick each player was found dead (or joined dead), and the hotbar slot each was last seen with. */
    private final Map<UUID,Long> deadAt=new HashMap<>();private final Map<UUID,Integer> slots=new HashMap<>();
    /**
     * Coming back to life by itself (2.107.0, MP_ONLY=death) as the server keeps it: each player's death (when, where),
     * every second while dead whether its body is still in the world (Minecraft took the dead out a second after they
     * died) and where it lies, its coming back (when, how long after it died or joined dead, where), and every change of
     * its hotbar slot (alive or dead: none may come while dead).
     */
    private void deaths(MinecraftServer server){
        long now=server.getOverworld().getTime();
        for(var p:server.getPlayerManager().getPlayerList()){
            String who=p.getGameProfile().getName();int slot=p.getInventory().selectedSlot;
            Integer was=slots.put(p.getUuid(),slot);
            if(was!=null&&was!=slot)note(server,String.format(java.util.Locale.ROOT,"%s slot %d -> %d at tick %d (%s)",who,was,slot,now,p.isAlive()?"alive":"dead"));
            Long since=deadAt.get(p.getUuid());
            if(!p.isAlive()&&since==null){deadAt.put(p.getUuid(),now);note(server,String.format(java.util.Locale.ROOT,"%s died at tick %d at %.2f %.2f %.2f",who,now,p.getX(),p.getY(),p.getZ()));}
            else if(!p.isAlive()&&(now-since)%20==0)note(server,String.format(java.util.Locale.ROOT,"%s dead %d ticks: at %.2f %.2f %.2f, body in the world %s (removed %s), death time %d",who,now-since,p.getX(),p.getY(),p.getZ(),p.getServerWorld().getPlayers().contains(p),p.isRemoved(),p.deathTime));
            else if(p.isAlive()&&since!=null){deadAt.remove(p.getUuid());note(server,String.format(java.util.Locale.ROOT,"%s alive again at tick %d, %d ticks after it died (or joined dead), at %.2f %.2f %.2f",who,now,now-since,p.getX(),p.getY(),p.getZ()));}
        }
    }
    /** Only Lee So-sang's basic attack's script (2.77.0, MP_ONLY=isosang). */
    static final boolean ISOSANG_ONLY="isosang".equals(System.getProperty("botand.mp.only"))||"isosange".equals(System.getProperty("botand.mp.only"))||"isosangr".equals(System.getProperty("botand.mp.only"))||"isosangh".equals(System.getProperty("botand.mp.only"))||"isosangg".equals(System.getProperty("botand.mp.only"))||"isosangz".equals(System.getProperty("botand.mp.only"))||"isosangdodge".equals(System.getProperty("botand.mp.only"))||"isosangvolley".equals(System.getProperty("botand.mp.only"))||"isosangt".equals(System.getProperty("botand.mp.only"))||"isosangy".equals(System.getProperty("botand.mp.only"))||"isosanghh".equals(System.getProperty("botand.mp.only"));
    private String hitSeen="";private int blowsSeen;private float watcherHealth=-1;private net.minecraft.util.math.Vec3d watcherAt;private long watchUntil;
    private String eSeen="";private net.minecraft.util.math.Vec3d thrustFrom,casterFrom;private long thrustAt,giantSeen=-1;
    /**
     * Lee So-sang's basic attack (2.77.0) as the server keeps it: the caster's hits (each one, its blows as they land, its
     * rest), the watcher's health (each blow), its guard, and where it is: every move of more than a hundredth of a block
     * while a hit is in flight or for a second after one (a blow throws no one: there should be none).
     */
    private float dodgeHealth=-1;private String dodgeSeen="";private int onSeen=-1;
    /** (2.95.0) The caster's H basic attack last logged: its start, its blows, when it is ready again. */
    private long volleySeen=-1,vReadySeen=-1;private int volleyBlows;
    /** (2.96.0) The caster's T last logged: its start, its blows, when it is ready again. */
    private long lungeSeen=-1,tReadySeen=-1;private int lungeBlows;
    /** (2.97.0) The caster's Y last logged: its start, its blows, when it is ready again; whether the watcher was caged, and where it stood a tick ago. */
    private long cageSeen=-1,yReadySeen=-1;private int cageBlows;private boolean cagedSeen;private net.minecraft.util.math.Vec3d cageWatcherAt;
    /** (2.101.0) The caster's H again last logged: its start and its blows. */
    private long finaleSeen=-1;private int finaleBlows;
    private long sweepSeen=-1,rReadySeen=-1,formSeen=-1,hReadySeen=-1,gSeen=-1,gReadySeen=-1,zFrom=-1;private dev.sangwoo.celestialfall.isosang.IsosangRules.ZState zSeen;private net.minecraft.util.math.Vec3d gFrom;private float gHealth;private boolean formOver;private double formFloor;
    private void isosang(MinecraftServer server){
        long now=server.getOverworld().getTime();
        var caster=server.getPlayerManager().getPlayerList().stream().filter(MpServer::caster).findFirst().orElse(null);
        var watcher=server.getPlayerManager().getPlayerList().stream().filter(q->!caster(q)).findFirst().orElse(null);
        if(caster==null||watcher==null)return;
        var h=dev.sangwoo.celestialfall.isosang.IsosangAttack.hit(caster);
        String hit=h==null||h.combo()<1?"":h.combo()+"@"+h.start();
        if(!hit.equals(hitSeen)){
            if(!hit.isEmpty())note(server,String.format(java.util.Locale.ROOT,"caster's hit %d began at %d (way %.1f; ready again at %d) %s",h.combo(),h.start(),h.yaw(),dev.sangwoo.celestialfall.isosang.IsosangAttack.ready(caster),where(watcher,caster)));
            else note(server,"caster's attack: no hit (combo over or broken; ready again at "+dev.sangwoo.celestialfall.isosang.IsosangAttack.ready(caster)+")");
            hitSeen=hit;blowsSeen=0;watcherAt=watcher.getPos();
        }
        if(h!=null&&h.combo()>=1){
            if(h.blows()>blowsSeen){note(server,"caster's hit "+h.combo()+" blow "+h.blows()+" of "+dev.sangwoo.celestialfall.isosang.IsosangRules.blows(h.combo())+" landed at "+now);blowsSeen=h.blows();}
            watchUntil=now+20;
        }
        // (2.101.0) H again: its start (where she stands, its way, the watcher ahead and aside, the formation over or not,
        // H's ready tick), each blow as it lands, and every 10 ticks to 40 how high over where she stands the caster is
        var fi=dev.sangwoo.celestialfall.isosang.IsosangAttack.finaleNow(caster);
        if(fi!=null){
            var f=net.minecraft.util.math.Vec3d.fromPolar(0,fi.yaw());var d=watcher.getPos().subtract(fi.spot());
            if(fi.start()!=finaleSeen){finaleSeen=fi.start();finaleBlows=0;
                note(server,String.format(java.util.Locale.ROOT,"caster's H again began at %d: she stands %.2f from the caster, way %.1f, the watcher %.2f ahead and %.2f aside of her; formation %s; H ready again at %d",
                    fi.start(),Math.hypot(caster.getX()-fi.spot().x,caster.getZ()-fi.spot().z),fi.yaw(),d.dotProduct(f),d.dotProduct(new net.minecraft.util.math.Vec3d(-f.z,0,f.x)),
                    dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(caster)==null?"over":"standing",dev.sangwoo.celestialfall.isosang.IsosangAttack.hReady(caster)));}
            if(fi.blows()>finaleBlows){finaleBlows=fi.blows();note(server,"caster's H again blow "+fi.blows()+" of "+dev.sangwoo.celestialfall.isosang.IsosangRules.blows(dev.sangwoo.celestialfall.isosang.IsosangRules.FINALE)+" landed at "+now+" ("+(now-fi.start())+" in)");}
            long in=now-fi.start();
            if(in%10==0&&in<=40)note(server,String.format(java.util.Locale.ROOT,"caster's H again +%d: %.2f over where she stands, %.2f off it, fall distance %.2f",in,caster.getY()-fi.spot().y,Math.hypot(caster.getX()-fi.spot().x,caster.getZ()-fi.spot().z),caster.fallDistance));
        }
        if(Math.abs(watcher.getHealth()-watcherHealth)>.01f){note(server,String.format(java.util.Locale.ROOT,"watcher health %.1f -> %.1f at %d (guard %s)",watcherHealth,watcher.getHealth(),now,dev.sangwoo.celestialfall.combat.CombatManager.guarding(watcher)));watcherHealth=watcher.getHealth();}
        // (2.80.0) the caster's E, and the empowered fourth: its step back and its throw of the watcher (that one moves)
        String e=dev.sangwoo.celestialfall.isosang.IsosangAttack.charged(caster)+"/"+dev.sangwoo.celestialfall.isosang.IsosangAttack.eReady(caster);
        if(!e.equals(eSeen)){eSeen=e;note(server,"caster's E: "+(dev.sangwoo.celestialfall.isosang.IsosangAttack.charged(caster)?"empowered":"not empowered")+", ready again at "+dev.sangwoo.celestialfall.isosang.IsosangAttack.eReady(caster)+" (now "+now+")");}
        boolean thrust=h!=null&&h.combo()==dev.sangwoo.celestialfall.isosang.IsosangRules.THRUST;
        if(thrust&&thrustFrom==null){thrustFrom=watcher.getPos();casterFrom=caster.getPos();thrustAt=now;}
        if(thrustFrom!=null&&now-thrustAt<=60){
            if(now-thrustAt==8||now-thrustAt==60)note(server,String.format(java.util.Locale.ROOT,"empowered fourth +%d: caster stepped %.2f blocks; watcher thrown %.2f blocks (horizontally %.2f) to %s",now-thrustAt,caster.getPos().distanceTo(casterFrom),watcher.getPos().distanceTo(thrustFrom),watcher.getPos().subtract(thrustFrom).horizontalLength(),where(watcher,caster)));
            watcherAt=watcher.getPos();
        }else if(!thrust)thrustFrom=null;
        // (2.80.1) the giant sword thrown: from where (behind where the hit began), which way, how far it flies
        var gf=dev.sangwoo.celestialfall.isosang.IsosangAttack.giant(caster);
        if(gf!=null&&gf.thrown()!=giantSeen&&casterFrom!=null&&h!=null){
            giantSeen=gf.thrown();
            note(server,String.format(java.util.Locale.ROOT,"giant sword thrown at %d (%d into the hit) from %.2f blocks behind where the hit began (caster now %.2f back), way %.1f, its point to stop %.2f blocks on",
                gf.thrown(),gf.thrown()-h.start(),gf.from().distanceTo(casterFrom),caster.getPos().distanceTo(casterFrom),gf.yaw(),gf.most()));
        }
        // (2.81.0) the caster's R: where its sweep stands against the caster, which way, when R is ready again; the watcher
        // must not move while it sweeps (it throws no one)
        var r=dev.sangwoo.celestialfall.isosang.IsosangAttack.sweeping(caster);
        if(r!=null&&r.start()!=sweepSeen){
            sweepSeen=r.start();var f=net.minecraft.util.math.Vec3d.fromPolar(0,r.yaw());var d=r.at().subtract(caster.getPos());
            note(server,String.format(java.util.Locale.ROOT,"caster's R began at %d: it stands %.2f ahead (%.2f to the side, %.2f up) of the caster, way %.1f, the watcher %.2f ahead; ready again at %d",
                r.start(),d.dotProduct(f),d.dotProduct(new net.minecraft.util.math.Vec3d(-f.z,0,f.x)),d.y,r.yaw(),watcher.getPos().subtract(caster.getPos()).dotProduct(f),dev.sangwoo.celestialfall.isosang.IsosangAttack.rReady(caster)));
            watcherAt=watcher.getPos();
        }
        if(r!=null)watchUntil=Math.max(watchUntil,now+20);
        long rr=dev.sangwoo.celestialfall.isosang.IsosangAttack.rReady(caster);if(rr!=rReadySeen){rReadySeen=rr;note(server,"caster's R ready again at "+rr+" (now "+now+")");}
        // (2.82.0) the caster's H: its formation as it begins (its middle against the caster), every second the caster's
        // height over its middle and how far off it level, and when it is over; when H is ready again
        var form=dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(caster);
        if(form!=null){
            if(form.start()!=formSeen){formSeen=form.start();var d=form.centre().subtract(caster.getPos());
                note(server,String.format(java.util.Locale.ROOT,"caster's H began at %d: its middle %.2f %.2f %.2f from the caster; the watcher %.2f off it",form.start(),d.x,d.y,d.z,watcher.getPos().distanceTo(form.centre())));}
            if((now-form.start())%20==0||(now-form.start())>=24&&(now-form.start())<=40&&(now-form.start())%2==0){var d=caster.getPos().subtract(form.centre());var off=caster.getPos().subtract(form.holds());
                // (2.82.3: and how far off where it holds them level; 2.82.4: where they stood, on its outer ring, its middle ahead)
                note(server,String.format(java.util.Locale.ROOT,"caster's formation +%d: the caster %.2f over its middle, %.2f off it level, %.2f off where it holds them (%.2f from the middle) (on ground %s, fall %.2f)",
                    now-form.start(),d.y,Math.hypot(d.x,d.z),Math.hypot(off.x,off.z),Math.hypot(form.holds().x-form.centre().x,form.holds().z-form.centre().z),caster.isOnGround(),caster.fallDistance));}
            watchUntil=Math.max(watchUntil,now+20);
        }else if(formSeen>=0&&!formOver){formOver=true;note(server,"caster's formation over at "+now+String.format(java.util.Locale.ROOT," (the caster %.2f over the ground it began on)",caster.getY()-formFloor));}
        if(form!=null){formOver=false;formFloor=form.centre().y;}
        long hr=dev.sangwoo.celestialfall.isosang.IsosangAttack.hReady(caster);if(hr!=hReadySeen){hReadySeen=hr;note(server,"caster's H ready again at "+hr+" (now "+now+")");}
        // (2.93.0) the caster's Z: its state as it changes (its window's first move, how many, when it is ready again); and
        // every tick for 16 ticks from each move, how far off where the formation holds them the caster is, how high over its
        // middle, and how far from the middle that is (the sword they fly to)
        // (2.94.0) H's dodge: the caster's health as it changes, its formation's dodge (used or not) and the sword it stands
        // on as they change (a move of either kind: the per-tick lines below follow it as they follow Z's)
        if(Math.abs(caster.getHealth()-dodgeHealth)>.01f){note(server,String.format(java.util.Locale.ROOT,"caster health %.1f -> %.1f at %d",dodgeHealth,caster.getHealth(),now));dodgeHealth=caster.getHealth();}
        int on=dev.sangwoo.celestialfall.isosang.IsosangAttack.standsOn(caster);boolean dodged=dev.sangwoo.celestialfall.isosang.IsosangAttack.dodged(caster);String dg=dodged+"/"+on;
        if(!dg.equals(dodgeSeen)){if(onSeen>=0&&on>=0&&on!=onSeen)zFrom=now;onSeen=on;dodgeSeen=dg;
            note(server,String.format(java.util.Locale.ROOT,"caster's H dodge %s, on sword %d at %d",dodged?"used":"unused",on,now));}
        var z=dev.sangwoo.celestialfall.isosang.IsosangAttack.zNow(caster);
        if(!z.equals(zSeen)){if(zSeen!=null&&z.uses()>0&&(z.uses()!=zSeen.uses()||z.first()!=zSeen.first()))zFrom=now;zSeen=z;
            note(server,String.format(java.util.Locale.ROOT,"caster's Z: first %d, used %d, ready again at %d (now %d)",z.first(),z.uses(),z.ready(),now));}
        if(form!=null&&zFrom>=0&&now-zFrom<=16){var off=caster.getPos().subtract(form.holds());
            note(server,String.format(java.util.Locale.ROOT,"caster's Z +%d: %.2f off where it holds them level, %.2f over its middle, held %.2f from the middle (on ground %s)",
                now-zFrom,Math.hypot(off.x,off.z),caster.getY()-form.centre().y,Math.hypot(form.holds().x-form.centre().x,form.holds().z-form.centre().z),caster.isOnGround()));}
        // (2.83.0) the caster's G: as it begins, where it strikes against where the caster stands and the watcher; every
        // ten ticks while it holds the caster, how far the caster has moved from where it stood and its health
        var g=dev.sangwoo.celestialfall.isosang.IsosangAttack.ultimateNow(caster);
        if(g!=null&&g.start()!=gSeen){gSeen=g.start();gFrom=caster.getPos();gHealth=caster.getHealth();var f=net.minecraft.util.math.Vec3d.fromPolar(0,caster.getYaw());var d=g.strike().subtract(g.at());
            note(server,String.format(java.util.Locale.ROOT,"caster's G began at %d: it strikes %.2f ahead (%.2f across, %.2f up) of the caster; the watcher %.2f from the caster",g.start(),d.dotProduct(f),d.dotProduct(new net.minecraft.util.math.Vec3d(-f.z,0,f.x)),d.y,watcher.getPos().distanceTo(caster.getPos())));}
        if(gSeen>=0&&gFrom!=null&&now-gSeen<=110&&(now-gSeen)%10==0)
            note(server,String.format(java.util.Locale.ROOT,"caster's G +%d: held %s, moved %.3f blocks, health %.1f (was %.1f)",now-gSeen,dev.sangwoo.celestialfall.isosang.IsosangAttack.casting(caster),caster.getPos().distanceTo(gFrom),caster.getHealth(),gHealth));
        long gr=dev.sangwoo.celestialfall.isosang.IsosangAttack.gReady(caster);if(gr!=gReadySeen){gReadySeen=gr;note(server,"caster's G ready again at "+gr+" (now "+now+")");}
        // (2.95.0) the caster's H basic attack (회풍만검): each volley as it begins (whom it comes down on and where, against
        // the watcher's chest), its blows as they land (the watcher's health beside them), when it is ready again; the
        // watcher must not move while it strikes (no knockback)
        var vo=dev.sangwoo.celestialfall.isosang.IsosangAttack.volleyNow(caster);
        if(vo!=null&&vo.start()!=volleySeen){volleySeen=vo.start();volleyBlows=0;watcherAt=watcher.getPos();
            note(server,String.format(java.util.Locale.ROOT,"caster's volley began at %d on %s at %.2f %.2f %.2f (%.2f off the watcher's chest, the watcher %.2f from the caster); ready again at %d",
                vo.start(),vo.target()<0?"no one":vo.target()==watcher.getId()?"the watcher":"entity "+vo.target(),vo.at().x,vo.at().y,vo.at().z,vo.at().distanceTo(watcher.getPos().add(0,watcher.getHeight()*.55,0)),
                watcher.distanceTo(caster),dev.sangwoo.celestialfall.isosang.IsosangAttack.vReady(caster)));
            // (2.95.2) its reach is the formation and 3 blocks more: how far out (level) from the formation's middle where
            // they come down and the watcher are, and how high over its ground
            var fo=dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(caster);
            if(fo!=null)note(server,String.format(java.util.Locale.ROOT,"  from the formation's middle: where they come down %.2f out, %.2f up; the watcher %.2f out, %.2f up (the reach %.0f out)",
                Math.hypot(vo.at().x-fo.centre().x,vo.at().z-fo.centre().z),vo.at().y-fo.centre().y,Math.hypot(watcher.getX()-fo.centre().x,watcher.getZ()-fo.centre().z),watcher.getY()-fo.centre().y,
                dev.sangwoo.celestialfall.isosang.IsosangRules.FORMATION_RADIUS+dev.sangwoo.celestialfall.isosang.IsosangRules.VOLLEY_BEYOND));}
        if(vo!=null){if(vo.blows()>volleyBlows){note(server,"caster's volley blow "+vo.blows()+" of 6 at "+now+" (its tick "+(now-vo.start())+")");volleyBlows=vo.blows();}watchUntil=Math.max(watchUntil,now+20);}
        long vr=dev.sangwoo.celestialfall.isosang.IsosangAttack.vReady(caster);if(vr!=vReadySeen){vReadySeen=vr;note(server,"caster's volley ready again at "+vr+" (now "+now+")");}
        // (2.97.0) the caster's Y: as it begins (on whom, its ring against the watcher's feet), its blows as they land, as it
        // is set (where, against the watcher), whether the watcher is caged (incapacitated) as that changes, and every tick
        // through it the watcher's step (it walks: four fifths shorter while caged); when it is ready again
        var ca=dev.sangwoo.celestialfall.isosang.IsosangAttack.cageNow(caster);
        if(ca!=null&&ca.start()!=cageSeen){cageSeen=ca.start();cageBlows=0;cageWatcherAt=watcher.getPos();
            note(server,String.format(java.util.Locale.ROOT,"caster's Y began at %d on %s, its ring at %.2f %.2f %.2f (%.2f from the watcher's feet); ready again at %d",
                ca.start(),ca.target()==watcher.getId()?"the watcher":ca.target()<0?"no one":"entity "+ca.target(),ca.at().x,ca.at().y,ca.at().z,Math.hypot(ca.at().x-watcher.getX(),ca.at().z-watcher.getZ()),dev.sangwoo.celestialfall.isosang.IsosangAttack.yReady(caster)));}
        if(ca!=null&&ca.blows()>cageBlows){cageBlows=ca.blows();note(server,String.format(java.util.Locale.ROOT,"caster's Y blow %d of %d landed at %d (+%d), its ring %.2f from the watcher's feet",ca.blows(),dev.sangwoo.celestialfall.isosang.IsosangRules.blows(dev.sangwoo.celestialfall.isosang.IsosangRules.CAGE),now,now-ca.start(),Math.hypot(ca.at().x-watcher.getX(),ca.at().z-watcher.getZ())));}
        boolean cagedNow=dev.sangwoo.celestialfall.combat.CombatManager.incapacitated(watcher);
        if(cagedNow!=cagedSeen){cagedSeen=cagedNow;note(server,"watcher "+(cagedNow?"caged (incapacitated)":"free")+" at "+now+(cageSeen>=0?" (+"+(now-cageSeen)+")":""));}
        if(cageSeen>=0&&now-cageSeen<=45&&cageWatcherAt!=null){double step=Math.hypot(watcher.getX()-cageWatcherAt.x,watcher.getZ()-cageWatcherAt.z);
            if(step>1e-4)note(server,String.format(java.util.Locale.ROOT,"watcher stepped %.3f blocks at +%d",step,now-cageSeen));cageWatcherAt=watcher.getPos();}
        long yr=dev.sangwoo.celestialfall.isosang.IsosangAttack.yReady(caster);if(yr!=yReadySeen){yReadySeen=yr;note(server,"caster's Y ready again at "+yr+" (now "+now+")");}
        // (2.96.0) the caster's T: as it begins (whom its blows come down on and where, against the watcher's chest; where the
        // caster lands against the watcher; the swords it flies from and to, and (2.96.1) whether that one is on the other
        // side of the formation's middle from the one it began on, of how many there), its blows as they land, every tick through it how far off where the formation holds them the
        // caster is and how high over its middle; when it is ready again. The watcher must not move (no knockback)
        var lu=dev.sangwoo.celestialfall.isosang.IsosangAttack.lungeNow(caster);
        if(lu!=null&&lu.start()!=lungeSeen){lungeSeen=lu.start();lungeBlows=0;watcherAt=watcher.getPos();
            var places=dev.sangwoo.celestialfall.isosang.IsosangAttack.formationPlaces(caster);
            var goal=lu.target()==watcher.getId()?watcher.getPos():lu.at();
            var mid=dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(caster);
            double turn=Double.NaN;int across=0;
            if(places!=null&&mid!=null&&lu.from()>=0&&lu.to()>=0){
                double fx=places[lu.from()].x-mid.centre().x,fz=places[lu.from()].z-mid.centre().z;
                for(int k=0;k<places.length;k++)if(k!=lu.from()&&(places[k].x-mid.centre().x)*fx+(places[k].z-mid.centre().z)*fz<0)across++;
                double tx=places[lu.to()].x-mid.centre().x,tz=places[lu.to()].z-mid.centre().z;
                turn=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,(fx*tx+fz*tz)/(Math.hypot(fx,fz)*Math.hypot(tx,tz))))));
            }
            // (2.96.2) where it lands against the watcher: how far in front of it on the way the caster came, and to its side (right +)
            double front=Double.NaN,side=Double.NaN;
            if(places!=null&&lu.from()>=0){
                double wx=watcher.getX()-places[lu.from()].x,wz=watcher.getZ()-places[lu.from()].z,wl=Math.hypot(wx,wz);
                if(wl>1e-3){wx/=wl;wz/=wl;double dx=lu.past().x-watcher.getX(),dz=lu.past().z-watcher.getZ();front=-(dx*wx+dz*wz);side=dx*-wz+dz*wx;}
            }
            note(server,String.format(java.util.Locale.ROOT,"caster's T began at %d on %s at %.2f %.2f %.2f (%.2f off the watcher's chest, the watcher %.2f from the caster); it lands %.2f from the watcher's feet (%.2f in front of it, %.2f to its side, level), flies from sword %d to sword %d (%.2f from where it comes down; %.1f deg round the formation's middle from the first, %s, one of %d on the other side); ready again at %d",
                lu.start(),lu.target()<0?"no one":lu.target()==watcher.getId()?"the watcher":"entity "+lu.target(),lu.at().x,lu.at().y,lu.at().z,lu.at().distanceTo(watcher.getPos().add(0,watcher.getHeight()*.55,0)),watcher.distanceTo(caster),
                lu.past().distanceTo(watcher.getPos()),front,side,lu.from(),lu.to(),places==null||lu.to()<0?-1:Math.hypot(places[lu.to()].x-goal.x,places[lu.to()].z-goal.z),
                turn,turn>90?"on the other side":"NOT on the other side",across,
                dev.sangwoo.celestialfall.isosang.IsosangAttack.tReady(caster)));}
        if(lu!=null){
            if(lu.blows()>lungeBlows){note(server,"caster's T blow "+lu.blows()+" of "+dev.sangwoo.celestialfall.isosang.IsosangRules.blows(dev.sangwoo.celestialfall.isosang.IsosangRules.LUNGE)+" at "+now+" (its tick "+(now-lu.start())+")");lungeBlows=lu.blows();}
            var fo=dev.sangwoo.celestialfall.isosang.IsosangAttack.formationNow(caster);
            if(fo!=null){var off=caster.getPos().subtract(fo.holds());
                note(server,String.format(java.util.Locale.ROOT,"caster's T +%d: %.2f off where it holds them level, %.2f over its middle (on ground %s, fall %.2f)",now-lu.start(),Math.hypot(off.x,off.z),caster.getY()-fo.centre().y,caster.isOnGround(),caster.fallDistance));}
            watchUntil=Math.max(watchUntil,now+20);
        }
        long tr=dev.sangwoo.celestialfall.isosang.IsosangAttack.tReady(caster);if(tr!=tReadySeen){tReadySeen=tr;note(server,"caster's T ready again at "+tr+" (now "+now+")");}
        if(now<=watchUntil&&watcherAt!=null&&watcher.getPos().distanceTo(watcherAt)>.01){
            note(server,String.format(java.util.Locale.ROOT,"ERROR? watcher moved %.3f blocks at %d (velocity %.3f %.3f %.3f) %s",watcher.getPos().distanceTo(watcherAt),now,watcher.getVelocity().x,watcher.getVelocity().y,watcher.getVelocity().z,where(watcher,caster)));
            watcherAt=watcher.getPos();
        }
        if(now>watchUntil)watcherAt=watcher.getPos();
    }
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
