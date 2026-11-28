package dev.sangwoo.celestialfall.client.engine;

import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.HitStunClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.combat.CombatState;
import dev.sangwoo.celestialfall.engine.Defense;
import dev.sangwoo.celestialfall.engine.EngineNet;
import java.util.ArrayDeque;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * The combat engine on the client (2.157.0): its own player's moves start the frame their key is pressed, not when the
 * server's answer comes back a round trip later.
 * <p>
 * The client runs a combat state of its own for its player ({@link #shadow}: the very class the server runs, on this
 * client's clock), and every press goes through it first: an attack it starts or keeps, a dash or an evasive it begins,
 * the guard as it goes. Each press goes to the server with a number and the world time this client showed
 * ({@link #send}); the server sends its state back with the number of the last press it went through and whether it went
 * through. A press it turned down is undone here (the punch stops, the dash stops). What others do to this player (a
 * blow's stagger, an incapacitation, a broken guard, the guard's pressure) is the server's alone: it is taken from each
 * state as it comes. This client's player is drawn by the prediction ({@link #merged}); everyone else as the server
 * tells.
 * <p>
 * A dash or evasive this client predicts it runs itself ({@link #tick}: its speed set each tick before the player
 * moves, as the server pushed it before), stopped by a wall or water; the server pushes none for it.
 */
public final class EngineClient {
    private EngineClient(){}
    /** The prediction of this client's player's combat state, on this client's clock. */
    private static final CombatState shadow=new CombatState();
    /** A press sent and not yet answered: its number, what it was, and whether it did anything here. */
    private record Pending(int seq,int action,boolean took){}
    private static final ArrayDeque<Pending> PENDING=new ArrayDeque<>();
    private static int seq;
    private static Object world;
    /** The dash or evasive this client runs: which (DASH, EVADE or none), since when (this client's ticks), the way, the way it faced. */
    private static int drive=-1;private static long driveStart;private static float driveYaw,driveForward,driveSide;

    public static void clear(){
        PENDING.clear();drive=-1;world=null;
        var fresh=new CombatState();
        shadow.attack=shadow.combo=0;shadow.attackStart=-1000;shadow.comboUntil=0;shadow.queued=false;shadow.guarding=false;
        shadow.dashStart=-1000;shadow.dashReady=0;shadow.evadeStart=-1000;shadow.evadeReady=0;shadow.lastRequest=-1000;
        shadow.staggerUntil=shadow.disabledUntil=shadow.brokenUntil=shadow.slowUntil=shadow.sealedUntil=0;shadow.pressure=0;
        shadow.guardUpAt=fresh.guardUpAt;shadow.perfectAt=fresh.perfectAt;
    }
    private static long now(MinecraftClient mc){return mc.world.getTime();}
    /** Sends a press: its number, the world time this client shows, the two ways pressed. */
    private static void send(int action,int a,int b,boolean took){
        if(!ClientPlayNetworking.canSend(EngineNet.INPUT))return;
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        seq=(seq+1)&0xffff;
        var buf=PacketByteBufs.create();EngineNet.writeInput(buf,action,seq,now(mc),a,b);
        ClientPlayNetworking.send(EngineNet.INPUT,buf);
        PENDING.addLast(new Pending(seq,action,took));
        while(PENDING.size()>64)PENDING.removeFirst();
    }
    private static void sound(SoundEvent sound,float volume,float pitch){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null)return;
        mc.world.playSound(mc.player,mc.player.getX(),mc.player.getY(),mc.player.getZ(),sound,SoundCategory.PLAYERS,volume,pitch);
    }

    // ───────────────────────────── presses ─────────────────────────────

    /** A press of the attack key with bare hands: a punch started or kept here at once, and sent. */
    public static void attack(){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        long now=now(mc);boolean kept=shadow.queued;
        boolean started=shadow.requestAttack(now);
        // the swing's sound as the punch starts (the server plays it for everyone else)
        if(started)sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,.23f,1.0f+(4-shadow.attack)*.13f);
        send(EngineNet.ATTACK,0,0,started||shadow.queued&&!kept);
    }
    /** The guard key held or let go (sent as it changes and every few ticks while held, as before). */
    public static void guard(boolean held,boolean send){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        boolean was=shadow.guarding;shadow.guard(held,now(mc));
        if(send)send(held?EngineNet.GUARD_UP:EngineNet.GUARD_DOWN,0,0,shadow.guarding!=was);
    }
    /** Whether the dash key now means the evasive: something holds this player and it is ready (as far as this client knows). */
    public static boolean evades(){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null)return false;
        long now=now(mc);
        return now>=shadow.evadeReady&&!shadow.evading(now)&&(shadow.locked(now)||HitStunClient.localStunned());
    }
    /** A press of the dash key: the evasive if something holds them ({@link #evades}), else the dash; begun here at once, and sent. */
    public static void dash(int forward,int side){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null)return;
        long now=now(mc);
        if(evades()){
            boolean took=shadow.evade(now,forward,side,HitStunClient.localStunned());
            if(took){
                shadow.evadeYaw=mc.player.getYaw();start(EngineNet.EVADE,shadow.evadeYaw,shadow.evadeForward,shadow.evadeSide);
                sound(SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE,.7f,1.35f);sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,.45f,1.55f);
                DefenseFx.evadedHere(mc.player);
            }
            send(EngineNet.EVADE,forward,side,took);
            return;
        }
        if(mc.player.isTouchingWater()){send(EngineNet.DASH,forward,side,false);return;}
        boolean took=shadow.dash(now,forward,side);
        if(took){
            shadow.dashYaw=mc.player.getYaw();start(EngineNet.DASH,shadow.dashYaw,shadow.dashForward,shadow.dashSide);
            sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,.35f,1.25f);
        }
        send(EngineNet.DASH,forward,side,took);
    }
    private static void start(int kind,float yaw,float forward,float side){
        var mc=MinecraftClient.getInstance();
        drive=kind;driveStart=now(mc);driveYaw=yaw;driveForward=forward;driveSide=side;
        if(mc.player!=null)mc.player.setSprinting(false);
    }

    // ───────────────────────────── each tick ─────────────────────────────

    /**
     * Before the player moves this tick: the prediction's own turns (an attack ends, a kept press starts the next), and
     * the dash or evasive this client runs, its speed set as the server set it.
     */
    public static void tick(MinecraftClient mc){
        if(mc.world==null||mc.player==null||!MapClient.enabled()){if(world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        long now=now(mc);
        shadow.tick(now);
        if(drive<0)return;
        ClientPlayerEntity p=mc.player;int age=(int)(now-driveStart);
        int length=drive==EngineNet.EVADE?Defense.EVADE_TICKS:CombatState.DASH_TICKS;
        if(age<0||age>=length||!p.isAlive()){drive=-1;return;}
        // a wall or water ends a dash (its cooldown from there, as the server has it); an evasive goes on against it
        if(drive==EngineNet.DASH&&(p.horizontalCollision||p.isTouchingWater())){shadow.stopDash(now);drive=-1;return;}
        double v=drive==EngineNet.EVADE?Defense.evadeSpeed(age):CombatState.dashSpeed(age);
        double yaw=Math.toRadians(driveYaw);
        Vec3d way=new Vec3d(-Math.sin(yaw)*driveForward+Math.cos(yaw)*driveSide,0,Math.cos(yaw)*driveForward+Math.sin(yaw)*driveSide);
        if(drive==EngineNet.EVADE&&p.horizontalCollision)return;
        p.setVelocity(way.multiply(v).add(0,p.getVelocity().y,0));
    }

    // ───────────────────────────── the server's word ─────────────────────────────

    /**
     * The server's state of this client's player came: what others did to them is taken as it is; a press it answered
     * is kept if it went through and undone here if not.
     */
    public static void server(CombatClient.Snapshot s){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        long now=now(mc);
        // what holds them, and the guard's wear: the server's alone
        shadow.staggerUntil=s.staggerUntil();shadow.disabledUntil=s.disabledUntil();shadow.disabledAt=s.disabledAt();
        shadow.brokenUntil=s.brokenUntil();shadow.brokenAt=s.brokenAt();shadow.sealedUntil=s.sealedUntil();shadow.pressure=s.pressure();
        shadow.combatUntil=Math.max(shadow.combatUntil,s.combatUntil());
        if(s.ackSeq()>=0){
            while(!PENDING.isEmpty()&&seqBefore(PENDING.peekFirst().seq(),s.ackSeq()))PENDING.removeFirst();
            var p=PENDING.peekFirst();
            if(p!=null&&p.seq()==s.ackSeq()){
                PENDING.removeFirst();
                if(p.took()&&!s.ackOk())undo(p.action(),s,now);
                else if(!p.took()&&s.ackOk())adopt(p.action(),s,now);
            }
        }
        // a blow cut off what they were doing (the server holds them): so does the prediction
        if(s.locked(now)&&!shadow.evading(now)){
            if(shadow.attack>0||shadow.dashing(now)){shadow.attack=shadow.combo=0;shadow.queued=false;if(shadow.dashing(now))shadow.stopDash(now);}
            if(drive==EngineNet.DASH)drive=-1;
        }
        // the cooldowns as the server has them once nothing of theirs is on its way
        if(PENDING.isEmpty()){
            if(!shadow.dashing(now))shadow.dashReady=Math.max(shadow.dashReady,s.dashReady());
            if(!shadow.evading(now))shadow.evadeReady=s.evadeReady();
        }
    }
    private static boolean seqBefore(int a,int b){return ((b-a)&0xffff)!=0&&((b-a)&0xffff)<0x8000;}
    /** A press that did something here and nothing on the server: undone. */
    private static void undo(int action,CombatClient.Snapshot s,long now){
        switch(action){
            case EngineNet.ATTACK->{shadow.attack=s.attack();shadow.attackStart=s.attackStart();shadow.combo=s.attack();shadow.queued=false;
                shadow.comboUntil=s.attack()>0?dev.sangwoo.celestialfall.engine.Moves.BARE.until(s.attack(),s.attackStart()):0;}
            case EngineNet.DASH->{shadow.dashStart=-1000;shadow.dashReady=s.dashReady();if(drive==EngineNet.DASH)drive=-1;}
            case EngineNet.EVADE->{shadow.evadeStart=-1000;shadow.evadeReady=s.evadeReady();if(drive==EngineNet.EVADE)drive=-1;}
            default->{}
        }
    }
    /** A press that did nothing here and something on the server (this client did not know enough): taken as the server has it. */
    private static void adopt(int action,CombatClient.Snapshot s,long now){
        switch(action){
            case EngineNet.ATTACK->{if(s.attack()>0){shadow.attack=shadow.combo=s.attack();shadow.attackStart=s.attackStart();shadow.hitDone=true;
                shadow.comboUntil=dev.sangwoo.celestialfall.engine.Moves.BARE.until(s.attack(),s.attackStart());}}
            case EngineNet.DASH->{shadow.dashStart=s.dashStart();shadow.dashForward=s.forward();shadow.dashSide=s.side();shadow.dashReady=s.dashReady();}
            case EngineNet.EVADE->{shadow.evadeStart=s.evadeStart();shadow.evadeForward=s.evadeForward();shadow.evadeSide=s.evadeSide();shadow.evadeReady=s.evadeReady();}
            default->{}
        }
    }

    /**
     * This client's player's state to draw and to judge its keys by: the server's, with what this player does
     * themselves (their attack, guard, dash and evasive) as predicted.
     */
    public static CombatClient.Snapshot merged(CombatClient.Snapshot s){
        if(!ClientPlayNetworking.canSend(EngineNet.INPUT))return s;
        boolean evading=shadow.evadeStart>s.evadeStart();
        return new CombatClient.Snapshot(s.serial(),shadow.attack,shadow.attackStart,shadow.guarding,s.pressure(),
            evading?Math.min(s.brokenUntil(),shadow.evadeStart):s.brokenUntil(),shadow.dashStart,Math.max(shadow.dashReady,0),shadow.dashForward,shadow.dashSide,Math.max(s.combatUntil(),shadow.combatUntil),
            s.brokenAt(),evading?Math.min(s.disabledUntil(),shadow.evadeStart):s.disabledUntil(),s.disabledAt(),evading?Math.min(s.staggerUntil(),shadow.evadeStart):s.staggerUntil(),s.sealedUntil(),
            shadow.guardUpAt,shadow.perfectAt,shadow.evadeStart,shadow.evadeReady,shadow.evadeForward,shadow.evadeSide,s.ackSeq(),s.ackOk());
    }
    /** The way of the evasive this client predicted: the way faced, forward and side (DefenseFx draws it from the press). */
    static float[] evadeWay(){return new float[]{shadow.evadeYaw,shadow.evadeForward,shadow.evadeSide};}
    /** Whether this client's player is under way with an evasive it predicted (a barrage's hold lets go of them here at once). */
    public static boolean evadingHere(){var mc=MinecraftClient.getInstance();return mc.world!=null&&shadow.evading(now(mc));}
    /** For the capture tools and the checks: the prediction and what is on its way. */
    public static String describe(){
        var mc=MinecraftClient.getInstance();long now=mc.world==null?0:now(mc);
        return String.format(java.util.Locale.ROOT,"attack %d@%d dash %d evade %d (ready %d) guard %b pending %d drive %d",
            shadow.attack,shadow.attackStart,shadow.dashStart,shadow.evadeStart,shadow.evadeReady,shadow.guarding,PENDING.size(),drive)+(shadow.locked(now)?" locked":"");
    }
}
