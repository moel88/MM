package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.map.MapGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;

/**
 * Star Platinum's time stop, its H (2.67.0, user request: "이제 H시간 정지를 만들자 시간 정지 시간은 5초고 시간 정지를 하면
 * 당한 사람은 시전자도 멈춰보이고 전부 멈춰보이고 풀려야 다시 보여 시간정지 상태일때 적을 때리면 시간 정지 상태가 끝나야
 * 넣었던 데미지가 모두 들어가"; StarPlatinumRules for its timing and reach). With the stand out and its user free and the
 * time stop ready, H (stand_timestop_v1, nothing in it) calls it: the stand shouts "Star Platinum: The World!", its
 * user held where they stand (KagenoManager.frozen, as for a cast; a punch in flight stops short), and as the shout
 * ends (TS_WIND) time stops for TS_LENGTH ticks for every living thing within TS_RANGE blocks of its user but its user:
 * players, mobs, dummies. Whatever they were doing stops (their attack, guard, dash, their stand's punches, rush and
 * inhale, a skill they were casting) and they stand where they are (KagenoManager.frozen), doing nothing, while its
 * user moves and acts as they please.
 *
 * <p>Nothing that befalls the stopped comes to them while time is stopped: the harm dealt them (LivingCombatMixin: what
 * bypasses invulnerability, /kill or the void, still does), the knocks (takeKnockback) and throws (deferLaunch) and the
 * incapacitations (CombatManager.incapacitate) are kept, and the moment time moves again they all come at once: one blow
 * of all the harm dealt, the throws added up (no faster than FLY_CAP times the strongest), the knocks, the longest
 * incapacitation and the strongest slow; and the feedback of those blows for whoever dealt them (the damage number) waits
 * for it too ("시간 정지 상태가 끝나야 넣었던 데미지가 모두 들어가").
 *
 * <p>The call is cut short, before time stops, if its user is struck, stunned, incapacitated, put into a cutscene, stopped
 * by another's stopped time, dies or leaves, or the stand goes in; time moves again early only if its user dies or leaves.
 * Its cooldown runs from its end (2.44.0's rule). Passed by as it stops time: whoever is in a beatdown's cutscene, an
 * Atomic's (or one of the old items') cast, stopped in another's stopped time, or stopping time themselves (time
 * stoppers move in stopped time), and Kageno's users (its passive, from before Star Platinum: no other stand's time
 * stop holds them). Every viewer within 128 blocks (and whom it stopped) is told when it is called, whom
 * it stopped and when time moves again, and the harm each was owed then (stand_timestop_event_v1, sent before it comes to them); the clients do
 * the rest (client/stand/StandTimeStop): the stand's shout, the wave, the stopped world and, for whom it stopped, a still
 * screen and no sound but its own until time moves again.
 */
public final class StarPlatinumTimeStop {
    public static final Identifier INPUT=CelestialFall.id("stand_timestop_v1"),EVENT=CelestialFall.id("stand_timestop_event_v1");
    /** It is called (H); time stops (whom it stopped); time moves again, or its call was cut short. */
    public static final int CALL=0,STOP=1,END=2;
    /** At most this many presses a second are heard. */
    static final int RATE=20;
    /** The throws one stopped took, added up, go no faster than this many times the strongest of them. */
    static final double FLY_CAP=1.25;
    /** Its name to the stands that may be immune to another's time stop (KagenoManager.ignoresTimeStop). */
    public static final String SOURCE="star_platinum";

    /** What one it stopped is owed for when time moves again. */
    private static final class Debt {
        /** The harm dealt them (the last blow's source with an attacker carries it), and whether any of it was a cry's. */
        DamageSource source;float damage;boolean loud;
        /** The throws added up, the strongest of them (blocks a tick), and the knocks (takeKnockback's strength, x, z). */
        Vec3d fly=Vec3d.ZERO;double strongest;final List<double[]> knocks=new ArrayList<>();
        /** The longest incapacitation, and the strongest slow (and the longest at that strength); the longest stagger (2.68.0). */
        int disable,slowTicks,stagger;double slow;
        /** Each attacker's own feedback for their blows: the damage they dealt, the way their last blow went. */
        final Map<ServerPlayerEntity,float[]> shown=new LinkedHashMap<>();
    }
    /** A time stop: its user, when it was called, and once time stopped, where its user stood then and whom it stopped. */
    private static final class Stop {
        final ServerPlayerEntity user;final ServerWorld world;final long start;
        boolean stopped;Vec3d center;
        final Map<LivingEntity,Debt> held=new LinkedHashMap<>();
        Stop(ServerPlayerEntity user,long start){this.user=user;world=user.getServerWorld();this.start=start;}
        long stopsAt(){return start+TS_WIND;}
        long movesAt(){return start+TS_WIND+TS_LENGTH;}
    }
    private static final class Presses{int window,count;}
    private static final Map<UUID,Stop> BY_USER=new HashMap<>(),BY_HELD=new HashMap<>();
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static final Map<UUID,Presses> PRESSES=new HashMap<>();
    /** Set while what the stopped are owed comes to them: those blows go through. */
    private static boolean settling;
    private StarPlatinumTimeStop(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                // (2.72.0) the time stop is the awakened's (ProgressManager.awakened: an operator's command until the
                // awakening quest); to anyone else H is no key at all (their client sends nothing either)
                if(!dev.sangwoo.celestialfall.progression.ProgressManager.awakened(player))return;
                var c=PRESSES.computeIfAbsent(player.getUuid(),k->new Presses());int second=server.getTicks()/20;
                if(c.window!=second){c.window=second;c.count=0;}
                if(++c.count>RATE)return;
                request(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(StarPlatinumTimeStop::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var s=BY_USER.get(p.getUuid());
            if(s!=null)end(s,s.world.getTime(),!s.stopped);
            PRESSES.remove(p.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{BY_USER.clear();BY_HELD.clear();READY.clear();PRESSES.clear();settling=false;});
    }

    /** Whether {@code e} is held where it stands by a time stop: calling it (until time stops), or stopped in it. */
    public static boolean holds(LivingEntity e){
        if(e==null||e.getWorld().isClient)return false;
        if(BY_HELD.containsKey(e.getUuid()))return true;
        var s=BY_USER.get(e.getUuid());return s!=null&&!s.stopped;
    }
    /** Whether {@code e} is stopped in someone's stopped time now. */
    public static boolean stopped(LivingEntity e){return e!=null&&!e.getWorld().isClient&&BY_HELD.containsKey(e.getUuid());}
    /** Whether this player has stopped time and it has not moved again yet (they move in it). */
    public static boolean stopping(PlayerEntity p){var s=p==null||p.getWorld().isClient?null:BY_USER.get(p.getUuid());return s!=null&&s.stopped;}
    /** Whether this player is calling a time stop or stopping time now. */
    public static boolean active(PlayerEntity p){return p!=null&&!p.getWorld().isClient&&BY_USER.containsKey(p.getUuid());}
    /** The tick this player called the time stop going on now, for a boss to read (2.69.0, BossThreats; MIN_VALUE for none). */
    public static long calledAt(PlayerEntity p){var s=p==null||p.getWorld().isClient?null:BY_USER.get(p.getUuid());return s==null?Long.MIN_VALUE:s.start;}
    /** The tick this player's time stop is ready again (0 if they have not used it). */
    public static long ready(PlayerEntity p){return READY.getOrDefault(p.getUuid(),0L);}
    /** This player's time stop is ready at {@code ready} (2.73.0, LongCooldowns: begun again in full as they came back or reset; 0 for now). */
    public static void restore(ServerPlayerEntity p,long ready){if(ready>0)READY.put(p.getUuid(),ready);else READY.remove(p.getUuid());}

    /**
     * H: the time stop is called now if it may: the stand out, its user free (no punch's skill in flight, no rush, no
     * inhale; a punch in flight stops short), none of theirs going on, and it ready again. Returns whether it was.
     */
    public static boolean request(ServerPlayerEntity p){
        if(!StarPlatinumAttack.able(p)||BY_USER.containsKey(p.getUuid())||BY_HELD.containsKey(p.getUuid()))return false;
        long now=p.getWorld().getTime();
        if(now<ready(p)&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        // the heavy punch, the Star Finger and the beatdown's blow are not cut off, nor the rush's fists on their way back
        if(now<StarPlatinumAttack.skillOverAt(p)||now<StarPlatinumRush.freeAt(p))return false;
        StarPlatinumAttack.yieldTo(p);
        var s=new Stop(p,now);BY_USER.put(p.getUuid(),s);
        p.clearActiveItem();p.setSprinting(false);if(p.currentScreenHandler!=p.playerScreenHandler)p.closeHandledScreen();
        KagenoManager.freeze(p);
        send(s,CALL,now,false,List.of());
        return true;
    }
    /** Its user was struck (or stunned, incapacitated, put into a cutscene) while calling it: the call is cut short. */
    public static void struck(ServerPlayerEntity p){
        var s=BY_USER.get(p.getUuid());
        if(s!=null&&!s.stopped)end(s,s.world.getTime(),true);
    }

    private static void tick(MinecraftServer server){
        if(BY_USER.isEmpty())return;
        for(var s:new ArrayList<>(BY_USER.values())){
            var u=s.user;long now=s.world.getTime();
            boolean gone=u.isRemoved()||!u.isAlive()||u.getServerWorld()!=s.world||u.isSpectator()||!MapGate.enabled(s.world);
            if(!s.stopped){
                // the call: its user held where they stand, until time stops (cut short as they are struck, held or put in a cutscene)
                if(gone||!StarPlatinumAttack.punches(u)||HitStun.stunned(u)||CombatManager.incapacitated(u)||StarPlatinumBeatdown.involved(u)
                    ||BY_HELD.containsKey(u.getUuid())||u.isSleeping()||u.hasVehicle()){end(s,now,true);continue;}
                KagenoManager.freeze(u);
                if(now>=s.stopsAt())stop(s,now);
                continue;
            }
            if(gone){end(s,now,false);continue;}
            // whom it stopped stand where they are; one gone (dead, away, a spectator) is let go, what they were owed with them
            for(var it=s.held.keySet().iterator();it.hasNext();){
                var e=it.next();
                if(!e.isAlive()||e.isRemoved()||e.getWorld()!=s.world||e.isSpectator()){it.remove();BY_HELD.remove(e.getUuid(),s);continue;}
                KagenoManager.freeze(e);
            }
            if(now>=s.movesAt())end(s,now,false);
        }
    }
    /** Time stops: every living thing within TS_RANGE of its user but its user (and those it passes by) stops where it is. */
    private static void stop(Stop s,long now){
        s.stopped=true;s.center=s.user.getPos();
        double r2=TS_RANGE*TS_RANGE;
        for(var e:s.world.getEntitiesByClass(LivingEntity.class,new Box(s.center,s.center).expand(TS_RANGE),e->e!=s.user&&e.isAlive()&&!e.isSpectator())){
            if(e.squaredDistanceTo(s.center)>r2||passedBy(e))continue;
            s.held.put(e,new Debt());BY_HELD.put(e.getUuid(),s);
            // whatever they were doing stops where it is (they show it as it was, stopped: their clients draw them so)
            CombatManager.interrupt(e);
            if(e instanceof ServerPlayerEntity sp){
                StarPlatinumAttack.struck(sp);StarPlatinumRush.struck(sp);StarPlatinumInhale.struck(sp);KagenoManager.disable(sp);
                sp.clearActiveItem();sp.setSprinting(false);if(sp.currentScreenHandler!=sp.playerScreenHandler)sp.closeHandledScreen();
            }
            if(e instanceof MobEntity mob)mob.getNavigation().stop();
            KagenoManager.freeze(e);
            // a boss stopped in it is a boss struck (2.69.0)
            dev.sangwoo.celestialfall.boss.Bosses.provoke(e,s.user);
        }
        send(s,STOP,now,false,List.copyOf(s.held.keySet()));
    }
    /**
     * Whom a time stop passes by: those in a beatdown's cutscene or an Atomic's (or an old item's) cast, time stoppers
     * (stopped already, or stopping time), and Kageno's users (its passive: no other stand's time stop holds them,
     * KagenoManager.ignoresTimeStop).
     */
    private static boolean passedBy(LivingEntity e){
        if(StarPlatinumBeatdown.involved(e)||BY_HELD.containsKey(e.getUuid()))return true;
        if(e instanceof ServerPlayerEntity p){
            var own=BY_USER.get(p.getUuid());if(own!=null&&own.stopped)return true;
            if(KagenoManager.ignoresTimeStop(p,SOURCE))return true;
            return dev.sangwoo.celestialfall.AtomicManager.isCasting(p.getUuid())||dev.sangwoo.celestialfall.SpellManager.isCasting(p.getUuid())||dev.sangwoo.celestialfall.HeavenManager.isCasting(p.getUuid());
        }
        return false;
    }
    /**
     * Time moves again (or the call is cut short, {@code cut}): whom it stopped go free, and what they are owed comes to
     * them at once. Its cooldown runs from now.
     */
    private static void end(Stop s,long now,boolean cut){
        BY_USER.remove(s.user.getUuid());
        for(var e:s.held.keySet())BY_HELD.remove(e.getUuid(),s);
        long ready=dev.sangwoo.celestialfall.NoCooldownState.enabled(s.user)?now:now+dev.sangwoo.celestialfall.boss.Bosses.cooldown(s.user,dev.sangwoo.celestialfall.boss.BossRules.TIME_STOP,TS_COOLDOWN);
        READY.put(s.user.getUuid(),ready);
        // (kept with the world too, 2.73.0: begun again in full if its user comes back or resets while it runs)
        dev.sangwoo.celestialfall.LongCooldowns.note(s.world.getServer(),s.user.getUuid(),dev.sangwoo.celestialfall.LongCooldowns.Skill.TIME_STOP,ready);
        // the word that time moves goes out first: whom it stopped are let go (their screens, their sound) before what
        // they are owed comes to them, so their cries of pain are heard (sent after, they were hushed with the rest)
        List<LivingEntity> owed=new ArrayList<>();List<Float> harm=new ArrayList<>();
        for(var e:s.held.entrySet()){var d=e.getValue();if(d.damage>0||d.fly.lengthSquared()>0){owed.add(e.getKey());harm.add(d.damage);}}
        send(s,END,now,cut,owed,harm);
        for(var e:s.held.entrySet())settle(s,e.getKey(),e.getValue());
    }
    /**
     * What one it stopped is owed comes to them now: all the harm dealt them in one blow (quietly if every blow of it
     * was a quiet one), then the knocks, the throws added up and the incapacitation; then whoever dealt the harm sees it
     * (their damage number).
     */
    private static void settle(Stop s,LivingEntity e,Debt d){
        if(!e.isAlive()||e.isRemoved()||e.getWorld()!=s.world)return;
        boolean landed=false;
        settling=true;
        try{
            if(d.damage>0&&d.source!=null){
                e.timeUntilRegen=0;
                landed=d.loud?CombatManager.damageWithoutKnockback(e,d.source,d.damage):CombatManager.damageQuietly(e,d.source,d.damage);
            }
            if(e.isAlive()){
                for(var k:d.knocks)e.takeKnockback(k[0],k[1],k[2]);
                if(d.fly.lengthSquared()>0){
                    Vec3d v=d.fly;double cap=d.strongest*FLY_CAP;
                    if(v.length()>cap)v=v.normalize().multiply(cap);
                    HitStun.release(e.getUuid());e.setVelocity(v);
                }
                if(d.fly.lengthSquared()>0||!d.knocks.isEmpty())e.velocityModified=true;
                if(d.disable>0||d.slowTicks>0)CombatManager.incapacitate(e,d.disable,d.slowTicks,d.slow);
                if(d.stagger>0)CombatManager.stagger(e,e.getWorld().getTime()+d.stagger);
            }
        }finally{settling=false;}
        if(landed)for(var f:d.shown.entrySet()){
            var p=f.getKey();
            if(p.isRemoved()||!ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE))continue;
            var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(f.getValue()[0]);b.writeFloat(f.getValue()[1]);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
        }
    }

    // ---- what befalls the stopped waits for time to move again

    private static Debt debt(LivingEntity e){
        if(settling||e==null||e.getWorld().isClient)return null;
        var s=BY_HELD.get(e.getUuid());return s==null?null:s.held.get(e);
    }
    /**
     * Harm about to be dealt {@code e}: if it is stopped, it is kept for when time moves again (and counts as dealt: the
     * blow landed, its feedback shows) and returns true. What bypasses invulnerability (/kill, the void) and harm it is
     * immune to are not kept.
     */
    public static boolean defer(LivingEntity e,DamageSource source,float amount){
        var d=debt(e);
        if(d==null||!Float.isFinite(amount)||amount<=0||source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)||e.isInvulnerableTo(source))return false;
        d.damage+=amount;
        if(d.source==null||source.getAttacker()!=null)d.source=source;
        if(!CombatManager.quiet(e))d.loud=true;
        return true;
    }
    /** A knock about to come to {@code e} (takeKnockback): kept if it is stopped. */
    public static boolean deferKnockback(LivingEntity e,double strength,double x,double z){
        var d=debt(e);if(d==null)return false;
        d.knocks.add(new double[]{strength,x,z});
        return true;
    }
    /** A throw about to send {@code e} flying at {@code velocity} (blocks a tick): kept if it is stopped (added to its others). */
    public static boolean deferLaunch(LivingEntity e,Vec3d velocity){
        var d=debt(e);if(d==null)return false;
        d.fly=d.fly.add(velocity);d.strongest=Math.max(d.strongest,velocity.length());
        return true;
    }
    /** An incapacitation about to come to {@code e} (CombatManager.incapacitate): kept if it is stopped (the longest, the strongest slow). */
    public static boolean deferIncapacitate(LivingEntity e,int ticks,int slowTicks,double slow){
        var d=debt(e);if(d==null)return false;
        d.disable=Math.max(d.disable,ticks);
        if(slowTicks>0&&(slow>d.slow||slow==d.slow&&slowTicks>d.slowTicks)){d.slow=slow;d.slowTicks=slowTicks;}
        return true;
    }
    /** A basic attack's stagger about to come to {@code e} for {@code ticks} (CombatManager.stagger, 2.68.0): kept if it is stopped (the longest). */
    public static boolean deferStagger(LivingEntity e,int ticks){
        var d=debt(e);if(d==null)return false;
        d.stagger=Math.max(d.stagger,ticks);
        return true;
    }
    /**
     * {@code attacker}'s own feedback for a blow of {@code amount} on {@code e} (the damage number, its way {@code yaw}):
     * kept if {@code e} is stopped, to show as the harm comes to them. Returns whether it was kept.
     */
    public static boolean deferFeedback(ServerPlayerEntity attacker,LivingEntity e,float amount,float yaw){
        var d=debt(e);if(d==null)return false;
        var f=d.shown.computeIfAbsent(attacker,k->new float[2]);f[0]+=amount;f[1]=yaw;
        return true;
    }

    /** Tells every player near its user (and its user, and whom it stopped) of the event. */
    private static void send(Stop s,int type,long now,boolean cut,List<LivingEntity> who){send(s,type,now,cut,who,null);}
    private static void send(Stop s,int type,long now,boolean cut,List<LivingEntity> who,List<Float> harm){
        var u=s.user;
        for(var viewer:s.world.getPlayers()){
            if(viewer!=u&&viewer.squaredDistanceTo(u)>128*128&&!s.held.containsKey(viewer))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(u.getUuid());b.writeIdentifier(s.world.getRegistryKey().getValue());b.writeByte(type);
            b.writeLong(s.start);b.writeLong(now);b.writeBoolean(cut);b.writeLong(ready(u));
            vec(b,s.center==null?u.getPos():s.center);
            b.writeVarInt(who.size());
            for(int i=0;i<who.size();i++){b.writeInt(who.get(i).getId());b.writeFloat(harm==null?0:harm.get(i));}
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    private static void vec(PacketByteBuf b,Vec3d v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
}
