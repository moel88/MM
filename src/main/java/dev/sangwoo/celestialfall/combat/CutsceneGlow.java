package dev.sangwoo.celestialfall.combat;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.map.MapGate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A skill with a cutscene glows first (2.109.0, user request: "빛나는 메커니즘을 바꾸자 몸이 하늘색으로 빛나고 빛이
 * 끝나고 스킬이 시전되게 바꾸자 카게노 H 스타플래티나 비트다운 이소상 G"): Kageno's H, Star Platinum's beatdown (G: the
 * glow, then its opening punch) and Lee So-sang's G. Its key pressed, and the skill free to be cast then (the same checks
 * as its cast), its user glows sky blue {@link #TICKS} (the glow CutsceneFlash draws, 0.5 s) and only then is it cast,
 * as it would have been at the key: the way its user faces as the glow ends.
 * <p>
 * While it glows (the user's answers: "80% 둔화 상태로 걸을 수 있음 + 빛나는 순간에 때리면 그 스킬 끊기고 다시 쿨타임
 * 기다려야함", "끊기고 쿨타임도 다시 흐름"): its user may only walk, at a fifth of their speed ({@link #SLOW}, as Kageno E's
 * thrusts; no sprint, no jump, nothing else: CombatManager.busy and actionLocked count it). A blow landing on them then
 * ({@link #struck}: LivingEntity.damage let it through, from someone or something thrown) cuts it short, and the skill's
 * cooldown runs in full from then; so does anything else that stops them (a stagger, an incapacitation, a hold, stopped
 * time, a cutscene of another's), their death, or their being no more the skill's (another stand, the stand put away,
 * off the map). Before 2.109.0 the glow came as the skill was cast (2.64.0 to 2.108.0).
 * <p>
 * Every player near (and the user) is told of it ("cutscene_glow_v1": whose, what happened, which skill), so every
 * client draws the same glow from when it begins, and its fading out if it is cut short.
 */
public final class CutsceneGlow {
    public static final Identifier EVENT=new Identifier(CelestialFall.ID,"cutscene_glow_v1");
    /** How long its user glows before the skill is cast (ticks): 0.5 s, the glow's whole length (CutsceneFlash.DURATION). */
    public static final int TICKS=10;
    /** How much slower its user walks while they glow (the user's 80%, as Kageno E's thrusts, KagenoRules.E_SLOW). */
    public static final double SLOW=.8;
    /** The slow walk's speed modifier (the client keeps its lens still while it is on, StandClient.skillSlowed). */
    public static final UUID SLOW_ID=UUID.fromString("c5e1f7a2-3b4d-4e8f-9a61-7d2b0c9e4f13");
    /** The skills that glow first. */
    public static final int KAGENO_H=0,BEATDOWN=1,ISOSANG_G=2;
    /** What the clients are told: a glow begins, is cut short, or is over and its skill cast. */
    public static final int BEGIN=0,CUT=1,CAST=2;
    /** How far its news goes (as the skills' own). */
    static final double VIEW=128;
    private record Glow(ServerPlayerEntity player,int skill,long start,ServerWorld world){}
    /** The glows on now, by whose (the server's alone: a client's player is never one of them, CombatManager asks on both sides). */
    private static final Map<UUID,Glow> GLOWS=new ConcurrentHashMap<>();
    private CutsceneGlow(){}

    public static void initialize(){
        // a glow ends as a key would have been pressed: before the tick's worlds, where the presses come
        ServerTickEvents.START_SERVER_TICK.register(CutsceneGlow::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->s.execute(()->drop(h.player)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->GLOWS.clear());
    }

    /** Whether the skill whose glow began at {@code start} is cast at {@code now}: the glow over. */
    public static boolean over(long start,long now){return now-start>=TICKS;}
    /** Whether this player glows now, their skill not yet cast. */
    public static boolean glowing(PlayerEntity p){var g=p==null?null:GLOWS.get(p.getUuid());return g!=null&&g.player==p;}
    /** Which skill this player glows for (-1 for none). */
    public static int skill(PlayerEntity p){var g=p==null?null:GLOWS.get(p.getUuid());return g!=null&&g.player==p?g.skill:-1;}
    /** When this player's glow began (Long.MIN_VALUE for none). */
    public static long startedAt(PlayerEntity p){var g=p==null?null:GLOWS.get(p.getUuid());return g!=null&&g.player==p?g.start:Long.MIN_VALUE;}

    /**
     * {@code p}'s key for {@code skill} was pressed and the skill may be cast (its own checks passed): they glow now, and
     * it is cast once the glow is over. Returns whether the glow began.
     */
    public static boolean begin(ServerPlayerEntity p,int skill){
        if(glowing(p)||!MapGate.enabled(p.getWorld()))return false;
        GLOWS.put(p.getUuid(),new Glow(p,skill,p.getWorld().getTime(),p.getServerWorld()));
        slow(p,true);p.setSprinting(false);p.clearActiveItem();
        tell(p,BEGIN,skill);
        return true;
    }
    /**
     * A blow landed on {@code target} (LivingEntity.damage let it through): if they glow, their skill is cut short. Only a
     * blow: harm from someone, or from something thrown (an arrow, a fireball); none from a fall, a fire or a poison, nor
     * their own.
     */
    public static void struck(LivingEntity target,DamageSource source){
        if(!(target instanceof ServerPlayerEntity p)||!glowing(p))return;
        if(source.getAttacker()==null&&source.getSource()==null||source.getAttacker()==p)return;
        cut(GLOWS.get(p.getUuid()));
    }

    private static void tick(MinecraftServer server){
        if(GLOWS.isEmpty())return;
        for(var g:new ArrayList<>(GLOWS.values())){
            var p=g.player;
            if(stopped(g)){cut(g);continue;}
            if(!over(g.start,p.getWorld().getTime()))continue;
            GLOWS.remove(p.getUuid());slow(p,false);
            if(cast(p,g.skill))tell(p,CAST,g.skill);
            else{cool(p,g.skill);tell(p,CUT,g.skill);}
        }
    }
    /**
     * Whether something stops the glowing one (so their skill is cut short): death, another world, the map off, no more
     * the skill's, or anything else that holds them (a stagger, an incapacitation, a broken guard, a rush's hold, stopped
     * time, another's cutscene, a hold of Kageno's).
     */
    static boolean stopped(Glow g){
        var p=g.player;
        if(!p.isAlive()||p.isRemoved()||p.getServerWorld()!=g.world||!MapGate.enabled(p.getWorld())||p.isSpectator()||p.isSleeping()||p.hasVehicle())return true;
        if(!still(p,g.skill))return true;
        return dev.sangwoo.celestialfall.stand.KagenoManager.frozen(p)||CombatManager.incapacitated(p)||HitStun.stunned(p)||CombatManager.cinematic(p)
            ||dev.sangwoo.celestialfall.stand.StarPlatinumRush.rushing(p)||dev.sangwoo.celestialfall.stand.StarPlatinumInhale.inhaling(p);
    }
    /** Whether {@code p} still has the skill they glow for. */
    private static boolean still(ServerPlayerEntity p,int skill){
        return switch(skill){
            case KAGENO_H->dev.sangwoo.celestialfall.progression.ProgressManager.canUseKageno(p);
            case BEATDOWN->dev.sangwoo.celestialfall.stand.StarPlatinumAttack.punches(p);
            case ISOSANG_G->dev.sangwoo.celestialfall.isosang.IsosangAttack.attacks(p);
            default->false;
        };
    }
    /** The glow is over: its skill is cast (whether it was). */
    private static boolean cast(ServerPlayerEntity p,int skill){
        return switch(skill){
            case KAGENO_H->dev.sangwoo.celestialfall.stand.KagenoManager.glowed(p);
            case BEATDOWN->dev.sangwoo.celestialfall.stand.StarPlatinumAttack.beatdownGlowed(p);
            case ISOSANG_G->dev.sangwoo.celestialfall.isosang.IsosangAttack.ultimateGlowed(p);
            default->false;
        };
    }
    /** The glow was cut short (or its skill could not be cast as it ended): the skill's cooldown runs in full from now. */
    private static void cool(ServerPlayerEntity p,int skill){
        switch(skill){
            case KAGENO_H->dev.sangwoo.celestialfall.stand.KagenoManager.glowCut(p);
            case BEATDOWN->dev.sangwoo.celestialfall.stand.StarPlatinumAttack.beatdownGlowCut(p);
            case ISOSANG_G->dev.sangwoo.celestialfall.isosang.IsosangAttack.ultimateGlowCut(p);
            default->{}
        }
    }
    private static void cut(Glow g){
        if(g==null||GLOWS.get(g.player.getUuid())!=g)return;
        var p=g.player;GLOWS.remove(p.getUuid());slow(p,false);
        // (a body gone from the world, a new one in its place, keeps nothing of the old one's skills)
        if(!p.isRemoved())cool(p,g.skill);
        tell(p,CUT,g.skill);
    }
    /** Gone (left the game): its glow goes with it, no cooldown run (nothing of it is kept). */
    private static void drop(ServerPlayerEntity p){
        var g=GLOWS.get(p.getUuid());if(g==null||g.player!=p)return;
        GLOWS.remove(p.getUuid());slow(p,false);tell(p,CUT,g.skill);
    }
    private static void slow(ServerPlayerEntity p,boolean on){
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
        if(!on){attr.removeModifier(SLOW_ID);return;}
        if(attr.getModifier(SLOW_ID)==null)attr.addTemporaryModifier(new EntityAttributeModifier(SLOW_ID,"Cutscene skill glow slow walk",-SLOW,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    /** Tells every player near {@code p} (and them) of their glow: whose, what happened, which skill. */
    private static void tell(ServerPlayerEntity p,int event,int skill){
        var world=p.getServerWorld();
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>VIEW*VIEW)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();b.writeUuid(p.getUuid());b.writeByte(event);b.writeByte(skill);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
