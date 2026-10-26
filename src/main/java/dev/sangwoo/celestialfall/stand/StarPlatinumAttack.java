package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.combat.Stagger;
import dev.sangwoo.celestialfall.map.MapGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;

/**
 * Star Platinum's punches, the attack of its user while it is out (2.54.0; StarPlatinumRules for the timing). The
 * attack key on the user's client asks for a punch (stand_attack_v1, no payload; a vanilla attack is taken the same
 * way) and the server decides: the next punch of the combo starts now, or when it may if that is near (a press held
 * or made a little early is kept), or not at all while the attack rests. Each punch goes the way its user faces as it
 * starts; at its contact it strikes everyone in its way in front of its user (StarPlatinumRules.lane), holds them for
 * the next punch (HitStun.standHold) and the fourth throws them along it. Every viewer is told when a punch starts
 * (with how far ahead the nearest one in its way stands, for where the stand goes to strike), whom it struck, and when
 * the attack rests or stops short (stand_attack_event_v1); the clients draw the stand's punches and play the shouts.
 * The user can walk and run while the stand punches. A punch stops short when its user is struck, sent back, or can no
 * longer have the stand out; the attack then rests as after a broken combo.
 * <p>
 * R is its heavy punch (2.59.0, user request: "이번에는 R 강펀치 맞으면 날아가고 가드 브레이커야"; stand_heavy_v1, no
 * payload): a fifth punch out of the combo (StarPlatinumRules.HEAVY), told to the clients like the others. It takes over
 * from a punch in flight (that one stops short, and the heavy punch starts once the stand is back in its guard from
 * it, STOP_RETURN ticks on), breaks the guard of whoever it strikes and lands all the same, sends
 * them flying along it, and is ready again HEAVY_COOLDOWN ticks after it is over. Every event tells the clients when it
 * is ready again and whose guard it broke. Since 2.60.0 whoever's guard it broke does not fly: they stay where they
 * stand, incapacitated while their guard is broken (user request: "R로 가드 부수면 날아가는 대신 2초 무력화 됨").
 * <p>
 * T is its Star Finger (2.60.0, user request: "이번에는 스타 핑거 손가락 2개를 엄청 늘려서 5블록까지 적을 찌를 수 있음";
 * stand_finger_v1, no payload): a sixth punch out of the combo (StarPlatinumRules.FINGER), taken like the heavy punch
 * (from a punch in flight, after the rush), that strikes whoever stands first in its long, narrow way, slows them and
 * incapacitates them (CombatManager.incapacitate), and is ready again FINGER_COOLDOWN ticks after it is over. Every
 * event tells the clients when it is ready again too.
 * <p>
 * G is its beatdown (2.64.0, user request: "이제 G 비트다운을 만들어야해 이건 컷씬이 나와야하고"; stand_beatdown_v1, no
 * payload): a seventh punch out of the combo (StarPlatinumRules.BEATDOWN), taken like the heavy punch, whose blow lands
 * on whoever stands first in its way: their guard up, it breaks it (and nothing more); none up, it begins the cutscene
 * (StarPlatinumBeatdown). It is ready again BEATDOWN_COOLDOWN ticks after its blow is over, or, if it began the
 * cutscene, after that is. Every event tells the clients when it is ready again too.
 */
public final class StarPlatinumAttack {
    public static final Identifier INPUT=CelestialFall.id("stand_attack_v1"),EVENT=CelestialFall.id("stand_attack_event_v1"),HEAVY_INPUT=CelestialFall.id("stand_heavy_v1"),
        FINGER_INPUT=CelestialFall.id("stand_finger_v1"),BEATDOWN_INPUT=CelestialFall.id("stand_beatdown_v1");
    /** A punch starts; it lands (whom it struck); the attack rests until it is ready again; the punch in flight stops short. */
    public static final int PUNCH=0,LANDED=1,RESTS=2,STOPPED=3;
    /** At most this many presses a second are heard. */
    static final int RATE=40;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** The punch in flight or the last one (1 to 4, 0 for none), when it began and the way it goes (degrees). */
        int combo;long start=-1000;float yaw;
        /** A press kept for the next punch; the punch in flight has landed. */
        boolean queued,landed;
        /** The tick the attack is ready again after a rest (0 for none), and the tick a punch in flight last stopped short. */
        long ready,stoppedAt=-1000;
        /** The tick the heavy punch is ready again (2.59.0), and an R kept for when the rush's fists are back in the guard. */
        long heavyReady;boolean heavyQueued;
        /** The same for the Star Finger (2.60.0) and its T. */
        long fingerReady;boolean fingerQueued;
        /** The same for the beatdown (2.64.0) and its G. */
        long beatdownReady;boolean beatdownQueued;
        int window,presses,heavyWindow,heavyPresses,fingerWindow,fingerPresses,beatdownWindow,beatdownPresses;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private StarPlatinumAttack(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                if(++s.presses>RATE)return;
                request(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(HEAVY_INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.heavyWindow!=second){s.heavyWindow=second;s.heavyPresses=0;}
                if(++s.heavyPresses>RATE)return;
                heavy(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(FINGER_INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.fingerWindow!=second){s.fingerWindow=second;s.fingerPresses=0;}
                if(++s.fingerPresses>RATE)return;
                finger(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(BEATDOWN_INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.beatdownWindow!=second){s.beatdownWindow=second;s.beatdownPresses=0;}
                if(++s.beatdownPresses>RATE)return;
                beatdown(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(StarPlatinumAttack::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->STATES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    /** Whether this player's attack is Star Platinum's punches: its user with it out, alive, on the map. */
    public static boolean punches(PlayerEntity p){
        return p!=null&&MapGate.enabled(p.getWorld())&&StandManager.starPlatinum(p)&&((StandHolder)p).botand$isStandOut()&&p.isAlive()&&!p.isSpectator();
    }
    /** Free to start a punch (or the inhale, 2.61.0): nothing else holds the user (a blow, a guard, a dash, a skill, a cutscene). */
    public static boolean able(ServerPlayerEntity p){return punches(p)&&!p.isSleeping()&&!p.hasVehicle()&&!CombatManager.busy(p)&&!CombatManager.cinematic(p);}
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){
            s=new State(p);STATES.put(p.getUuid(),s);
            // (2.73.0) the beatdown's cooldown is kept with the world (LongCooldowns): a new state, after the player left
            // and came back or went to another world, has it still
            if(p.getServer()!=null&&!(p instanceof dev.sangwoo.celestialfall.boss.BossPlayer))
                s.beatdownReady=dev.sangwoo.celestialfall.LongCooldowns.get(p.getServer()).ready(p.getUuid(),dev.sangwoo.celestialfall.LongCooldowns.Skill.BEATDOWN);
        }
        return s;
    }

    /** The user asks for a punch. Returns whether one starts now or is kept for when it may. */
    public static boolean request(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(now<s.ready)return false;
        // (the heavy punch too: the next punch may start once it is over)
        if(s.combo>=1&&s.combo!=4&&now<nextAt(s.combo,s.start)){
            if(nextAt(s.combo,s.start)-now<=BUFFER)s.queued=true;
            return s.queued;
        }
        // the rush just over: its fists are going back to the guard, and the punch starts once they are there (2.57.0)
        long free=StarPlatinumRush.freeAt(p);
        if(now<free){if(free-now<=BUFFER)s.queued=true;return s.queued;}
        begin(p,s,now);
        return true;
    }
    /**
     * R: the heavy punch starts now if it may (2.59.0): the stand out and its user free, it ready again and none in
     * flight. A punch in flight stops short and the heavy punch takes over from it; just after the rush it starts once
     * the rush's fists are back in the guard. Returns whether it starts (now or then).
     */
    public static boolean heavy(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        // (one of the stand's skills in flight, the heavy punch or the Star Finger: no other starts over it)
        if(skill(s.combo)&&now<s.start+LENGTH[s.combo])return false;
        if(now<s.heavyReady&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(now<StarPlatinumRush.freeAt(p)){s.heavyQueued=true;return true;}
        beginSkill(p,s,now,HEAVY);
        return true;
    }
    /**
     * T: the Star Finger starts now if it may (2.60.0), as the heavy punch does: the stand out and its user free, it
     * ready again and neither it nor the heavy punch in flight; a punch in flight stops short and it takes over, and
     * just after the rush it starts once the rush's fists are back in the guard. Returns whether it starts (now or then).
     */
    public static boolean finger(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(skill(s.combo)&&now<s.start+LENGTH[s.combo])return false;
        if(now<s.fingerReady&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(now<StarPlatinumRush.freeAt(p)){s.fingerQueued=true;return true;}
        beginSkill(p,s,now,FINGER);
        return true;
    }
    /**
     * G: the beatdown starts now if it may (2.64.0), as the heavy punch does: the stand out and its user free, it ready
     * again and no other of the stand's skills in flight; a punch in flight stops short and its blow takes over, and
     * just after the rush it starts once the rush's fists are back in the guard. Returns whether it starts (now or then).
     */
    public static boolean beatdown(ServerPlayerEntity p){
        // (not in its user's own stopped time, 2.67.0: whom it would strike stand stopped, and its cutscene is not of stopped time)
        if(!able(p)||StarPlatinumTimeStop.stopping(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        if(skill(s.combo)&&now<s.start+LENGTH[s.combo])return false;
        if(now<s.beatdownReady&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        if(now<StarPlatinumRush.freeAt(p)){s.beatdownQueued=true;return true;}
        beginSkill(p,s,now,BEATDOWN);
        return true;
    }
    /** The beatdown's cutscene is over (or cut short) at {@code end}: it is ready again a cooldown from then. */
    static void beatdownOver(ServerPlayerEntity p,long end){
        var s=state(p);
        s.beatdownReady=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?end:beatdownReady(p,end);kept(p,s);
        send(p,s,RESTS,null,null);
    }
    /** This player's beatdown is ready at {@code ready} (2.73.0, LongCooldowns: begun again in full as they come back, come back to life or are given a stand). */
    public static void restoreBeatdown(ServerPlayerEntity p,long ready){var s=STATES.get(p.getUuid());if(s!=null)s.beatdownReady=ready;}
    /** The beatdown's cooldown as just set, kept with the world (2.73.0, LongCooldowns). */
    private static void kept(ServerPlayerEntity p,State s){
        dev.sangwoo.celestialfall.LongCooldowns.note(p.getServer(),p.getUuid(),dev.sangwoo.celestialfall.LongCooldowns.Skill.BEATDOWN,s.beatdownReady);
    }
    /**
     * What this player's stand is punching now, as a boss reads it (2.69.0, BossThreats): the punch (1 to 7, 0 for none),
     * when it began (it may be a few ticks ahead, as a stopped punch turns back to the guard), the way it goes, whether it
     * has landed, and the tick the attack is ready again after a rest.
     */
    public record Punch(int combo,long start,float yaw,boolean landed,long ready){}
    /** This player's punch now (null if they have thrown none). */
    public static Punch punch(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?null:new Punch(s.combo,s.start,s.yaw,s.landed,s.ready);}
    /** The tick this player's beatdown is ready again (2.64.0; 0 if they have not used it). */
    public static long beatdownReady(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.beatdownReady;}
    /** When this player's beatdown is ready again once it ended at {@code end}: a boss's may be its own (2.69.0, BossKind.cooldown). */
    private static long beatdownReady(ServerPlayerEntity p,long end){return end+dev.sangwoo.celestialfall.boss.Bosses.cooldown(p,dev.sangwoo.celestialfall.boss.BossRules.BEATDOWN,BEATDOWN_COOLDOWN);}
    /** The tick this player's heavy punch is ready again (0 if they have not thrown one). */
    public static long heavyReady(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.heavyReady;}
    /** The tick this player's Star Finger is ready again (2.60.0; 0 if they have not used it). */
    public static long fingerReady(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.fingerReady;}
    /**
     * The tick this player's heavy punch or Star Finger in flight is over (0 if neither is): the rush waits for it
     * (StarPlatinumRush).
     */
    public static long skillOverAt(PlayerEntity p){var s=STATES.get(p.getUuid());return s==null||!skill(s.combo)?0:nextAt(s.combo,s.start);}
    private static void beginSkill(ServerPlayerEntity p,State s,long now,int skill){
        // a punch in flight (or a combo still open) stops short: the heavy punch or the Star Finger starts once the
        // stand has turned back to its guard from it (StarPlatinumRules.STOP_RETURN, as the rush does), so the clients'
        // stand goes from its guard into the coil or the aim
        s.heavyQueued=s.fingerQueued=s.beatdownQueued=false;stop(p,s,now);
        s.combo=skill;s.start=Math.max(now,s.stoppedAt+STOP_RETURN);s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=s.landed=s.heavyQueued=s.fingerQueued=s.beatdownQueued=false;
        // its cooldown runs once it is over (none for a player the cooldowns are off for, /botand nocooldown: ready from
        // now, so the cooldown list shows nothing for it, as for the rush)
        long end=s.start+LENGTH[skill];boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(skill==HEAVY)s.heavyReady=free?now:StarPlatinumRules.heavyReady(end);
        else if(skill==FINGER)s.fingerReady=free?now:StarPlatinumRules.fingerReady(end);
        // (the beatdown's from the end of its blow; if the blow begins the cutscene, from the cutscene's end instead)
        else{s.beatdownReady=free?now:beatdownReady(p,end);kept(p,s);}
        send(p,s,PUNCH,null,null);
    }
    /** The user was struck: whom the punches hold go, and the punch in flight stops short. */
    public static void struck(ServerPlayerEntity p){
        HitStun.releaseStand(p.getUuid());
        var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());
    }
    /** The rush starts (2.57.0): the punch in flight, if one is, stops short (and the combo starts again after its rest). */
    public static void yieldTo(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());}
    /** The tick the stand is back in its guard after the punch it last stopped short (StarPlatinumRules.STOP_RETURN; 0 if none has stopped). */
    public static long guardAt(ServerPlayerEntity p){var s=STATES.get(p.getUuid());return s==null?0:s.stoppedAt+STOP_RETURN;}
    /** The combo broke (the next punch is late): the attack rests and starts again from the first punch. */
    private static void settle(ServerPlayerEntity p,State s,long now){
        long rest=broken(s.combo,s.start,now);
        if(rest>=0){s.combo=0;s.queued=false;s.ready=Math.max(s.ready,rest);send(p,s,RESTS,null,null);}
        if(s.combo==4&&now>=s.ready)s.combo=0;
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        s.combo=next(s.combo);s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=s.landed=false;
        if(s.combo==4)s.ready=rested(now);
        send(p,s,PUNCH,null,null);
    }
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            // its beatdown's cutscene holds the user (2.64.0): its blow has landed, and nothing more comes of the punches until it is over
            if(StarPlatinumBeatdown.casting(p))continue;
            if(!punches(p)||HitStun.stunned(p)||CombatManager.actionLocked(p)){stop(p,s,now);continue;}
            settle(p,s,now);
            if(s.queued&&now>=nextAt(s.combo,s.start)&&now>=StarPlatinumRush.freeAt(p)){if(able(p))begin(p,s,now);else s.queued=false;}
            if(s.heavyQueued&&now>=StarPlatinumRush.freeAt(p)){s.heavyQueued=false;if(able(p)&&(now>=s.heavyReady||dev.sangwoo.celestialfall.NoCooldownState.enabled(p)))beginSkill(p,s,now,HEAVY);}
            if(s.fingerQueued&&now>=StarPlatinumRush.freeAt(p)){s.fingerQueued=false;if(able(p)&&(now>=s.fingerReady||dev.sangwoo.celestialfall.NoCooldownState.enabled(p)))beginSkill(p,s,now,FINGER);}
            if(s.beatdownQueued&&now>=StarPlatinumRush.freeAt(p)){s.beatdownQueued=false;if(able(p)&&(now>=s.beatdownReady||dev.sangwoo.celestialfall.NoCooldownState.enabled(p)))beginSkill(p,s,now,BEATDOWN);}
            if(s.combo>=1&&!s.landed&&now>=s.start+CONTACT[s.combo])strike(p,s,now);
        }
    }
    /** The punch in flight stops short (or the combo is cut off between punches): the attack rests from now. */
    private static void stop(ServerPlayerEntity p,State s,long now){
        boolean flying=s.combo>=1&&now-s.start<LENGTH[s.combo],open=s.combo>=1&&s.combo<4&&broken(s.combo,s.start,now)<0;
        if(!flying&&!open&&!s.queued&&!s.heavyQueued&&!s.fingerQueued&&!s.beatdownQueued)return;
        if(flying)s.stoppedAt=now;
        // a heavy punch or a Star Finger stopped short: its cooldown runs from now (2.44.0's rule for a move cut off)
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(flying&&s.combo==HEAVY)s.heavyReady=free?now:StarPlatinumRules.heavyReady(now);
        if(flying&&s.combo==FINGER)s.fingerReady=free?now:StarPlatinumRules.fingerReady(now);
        // (a beatdown whose blow began the cutscene is not stopped here: the cutscene holds its user, StarPlatinumBeatdown)
        if(flying&&s.combo==BEATDOWN&&!StarPlatinumBeatdown.casting(p)){s.beatdownReady=free?now:beatdownReady(p,now);kept(p,s);}
        s.queued=s.heavyQueued=s.fingerQueued=s.beatdownQueued=false;s.ready=Math.max(s.ready,now+REST);s.combo=0;
        send(p,s,STOPPED,null,null);
    }
    /**
     * The punch lands: everyone in its way in front of its user, in sight, is struck. The first three hold whom they
     * strike for the next punch; the fourth cannot be guarded and throws them along it; the heavy punch (2.59.0) breaks
     * the guard of whoever holds one up and throws them further (since 2.60.0 not those whose guard it broke: they stay,
     * incapacitated while it is broken). The Star Finger (2.60.0) strikes only the first one in its way, the nearest, and
     * incapacitates them if it lands (a guard takes it).
     */
    private static void strike(ServerPlayerEntity p,State s,long now){
        s.landed=true;
        var world=p.getServerWorld();int combo=s.combo;boolean big=combo==4||combo==HEAVY,heavy=combo==HEAVY,finger=combo==FINGER,beat=combo==BEATDOWN;
        Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        var source=world.getDamageSources().playerAttack(p);
        List<LivingEntity> struck=new ArrayList<>(),broke=new ArrayList<>();
        List<LivingEntity> way=new ArrayList<>();
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(Math.max(REACH_BIG,FINGER_REACH)+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            if(!lane(combo,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            // (the beatdown's blow passes by whoever is in a cutscene already, its own or another's)
            // (nor by whoever stands stopped in a time stop, 2.67.0)
            if(beat&&(StarPlatinumBeatdown.involved(e)||StarPlatinumTimeStop.stopped(e)||e instanceof ServerPlayerEntity sp&&CombatManager.cinematic(sp)))continue;
            way.add(e);
        }
        // the Star Finger's fingers end in the first body they meet: the nearest one in their way (to its near side); the
        // beatdown's blow lands on the first one too
        if((finger||beat)&&way.size()>1){
            LivingEntity first=null;double best=Double.MAX_VALUE;
            for(var e:way){double near=e.getPos().subtract(at).dotProduct(f)-e.getWidth()*.5;if(near<best){best=near;first=e;}}
            way=List.of(first);
        }
        if(beat){
            // the beatdown's blow (2.64.0): a guard up breaks (and nothing more comes of it); none up, the cutscene begins
            for(var e:way){
                if(CombatManager.breakGuard(e,HEAVY_BREAK))broke.add(e);
                else if(!e.isInvulnerableTo(source)&&StarPlatinumBeatdown.begin(p,e,s.start,s.yaw)){
                    struck.add(e);p.onAttacking(e);
                    world.playSound(null,e.getX(),e.getY(),e.getZ(),SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,SoundCategory.PLAYERS,.9f,.8f);
                }
            }
            send(p,s,LANDED,struck,broke);
            return;
        }
        for(var e:way){
            // the heavy punch breaks a guard held up against it, and lands all the same
            boolean broken=heavy&&CombatManager.breakGuard(e,HEAVY_BREAK);
            if(broken)broke.add(e);
            if(big)CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,DAMAGE[combo]))continue;
            struck.add(e);
            if(finger){
                // slowed and incapacitated where it stands (2.60.0): no hold, no throw
                CombatManager.incapacitate(e,FINGER_DISABLE,FINGER_SLOW_TICKS,FINGER_SLOW);
            }else if(broken){
                // its guard broken (2.60.0): it stays where it stands, incapacitated while the guard is broken (CombatManager)
                HitStun.release(e.getUuid());
            }else if(big){
                HitStun.release(e.getUuid());
                // the fourth punch staggers them a moment as it throws them (2.68.0, the basic attack's Stagger)
                if(!heavy)HitStun.stagger(p,e,combo,Stagger.stand(combo,s.start,now));
                Vec3d fly=f.multiply(heavy?FLY_HEAVY:FLY).add(0,heavy?FLY_HEAVY_UP:FLY_UP,0);
                // one stopped in a time stop (2.67.0) is thrown as time moves again, with whatever else came to them
                if(!StarPlatinumTimeStop.deferLaunch(e,fly)){
                    e.setVelocity(fly);e.velocityModified=true;
                    // the heavy punch tells a struck player's client of it once, through the tracker (velocityModified):
                    // the finisher sends it here as well, a tick before the tracker does, and a client that moves between
                    // the two is thrown twice (2.56.0's check: 11 or 20 blocks)
                    if(!heavy&&e instanceof ServerPlayerEntity other)other.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(other));
                }
            }else HitStun.stagger(p,e,combo,Stagger.stand(combo,s.start,now));
            // (until 2.68.0 the first three held them where they stood until the next punch had time to land: they are
            // staggered now, a tenth of their speed and nothing else for as long, Stagger)
            // the user's own hit feedback (the damage number and the blood), as Kageno's (on one stopped in a time stop,
            // as time moves again with all that came to them, 2.67.0)
            if(!StarPlatinumTimeStop.deferFeedback(p,e,DAMAGE[combo],s.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(DAMAGE[combo]);b.writeFloat(s.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);}
        }
        // (the heavy punch's and the Star Finger's own sounds carry their blows: StandAttack plays them on every client)
        if(!struck.isEmpty()){
            p.onAttacking(struck.get(0));
            if(!heavy&&!finger)world.playSound(null,at.x,at.y,at.z,big?SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK:SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,SoundCategory.PLAYERS,big?.8f:.55f,big?.7f:.9f);
        }
        send(p,s,LANDED,struck,broke);
    }
    /**
     * Tells every player near the user (and the user, and whoever was struck) of the event; since 2.59.0 every event
     * also carries when the heavy punch is ready again and, as it lands, whose guard it broke, since 2.60.0 when the
     * Star Finger is ready again, and since 2.64.0 when the beatdown is.
     */
    private static void send(ServerPlayerEntity p,State s,int type,List<LivingEntity> struck,List<LivingEntity> broke){
        var world=p.getServerWorld();long now=world.getTime();
        float aim=type==PUNCH?aim(p,s):Float.NaN;
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128&&(struck==null||!struck.contains(viewer)))continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);b.writeByte(s.combo);
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(s.yaw);b.writeFloat(aim);b.writeLong(s.ready);
            int n=struck==null?0:struck.size();b.writeVarInt(n);
            for(int i=0;i<n;i++){var e=struck.get(i);b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY());b.writeDouble(e.getZ());}
            b.writeLong(s.heavyReady);
            int m=broke==null?0:broke.size();b.writeVarInt(m);
            for(int i=0;i<m;i++)b.writeInt(broke.get(i).getId());
            b.writeLong(s.fingerReady);
            // 2.64.0: when the beatdown is ready again
            b.writeLong(s.beatdownReady);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    /**
     * How far ahead (blocks along the punch) the nearest one in its way stands, to the near side of their body, or NaN:
     * the stand goes as far out in front of its user as it takes for its fist to meet them.
     */
    private static float aim(ServerPlayerEntity p,State s){
        var world=p.getServerWorld();Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        double best=Double.NaN;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(Math.max(REACH_BIG,FINGER_REACH)+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);double ahead=d.dotProduct(f),half=e.getWidth()*.5;
            if(!lane(s.combo,ahead,d.dotProduct(right),d.y,half)||!p.canSee(e))continue;
            if(!(ahead-half>=best))best=ahead-half;
        }
        return (float)best;
    }
}
