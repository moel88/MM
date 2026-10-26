package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.meteor.MeteorRegistry;
import dev.sangwoo.celestialfall.progression.ProgressManager;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import static dev.sangwoo.celestialfall.stand.StandArrowRules.*;

/**
 * The stand arrow on the server (2.70.0, StandArrowRules for the user's words). The arrow in the main hand is used (R
 * on the map: the item's use) by one on the map with no stand, out of combat and cutscenes, standing on the ground;
 * the server rolls what the arrow will do and tells nobody, holds them where they stand ({@link #holds}: they raise
 * the arrow and drive it into their chest, every client drawing it from the start it is told of) and at REVEAL makes
 * the arrow's choice: the arrow (still in the hand it was raised in) is spent, and either the stand is theirs and
 * comes out beside them, everyone on the map hearing of it, or they fall, dying at DEATH_AT of the arrow's damage
 * type, which nothing stops (as the character reset's).
 * <p>
 * Cut short before REVEAL (they died, left, went elsewhere, put the arrow away) nothing happens and the arrow is
 * still theirs: the choice is made only as it is shown, so nothing can be learnt from cutting it short. Once made it
 * stands: one the arrow rejects who leaves before falling dies as they leave.
 */
public final class StandArrow {
    public static final Identifier EVENT=CelestialFall.id("stand_arrow_event_v1");
    public static final RegistryKey<DamageType> DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("stand_arrow"));
    /** The events: the arrow raised, its choice shown (with what it chose), cut short. */
    public static final int START=0,REVEALED=1,CUT=2;
    /** Rolls nobody can predict from the world's own randomness. */
    private static final SecureRandom RANDOM=new SecureRandom();
    /** For the captures and live checks: what the next arrow does ({@link StandArrowRules#DIE} or a stand), null to roll. */
    public static volatile String rigged;
    /** A stab: when it began (the world's tick), the hotbar slot the arrow was raised from, what it will do, whether it has been shown. */
    static final class Stab {
        final long start;final int slot;final String outcome;boolean revealed;
        Stab(long start,int slot,String outcome){this.start=start;this.slot=slot;this.outcome=outcome;}
        long age(long now){return now-start;}
    }
    private static final Map<UUID,Stab> STABS=new HashMap<>();
    /** Set while one the arrow killed is dying: their death message goes to their own death screen, not round the chat (StandArrowDeathMixin). */
    public static final ThreadLocal<Boolean> QUIET=new ThreadLocal<>();
    public static boolean quiet(){return Boolean.TRUE.equals(QUIET.get());}
    private StandArrow(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(StandArrow::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->left(h.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->STABS.clear());
    }

    /** Whether {@code e} is held where they stand by an arrow in their chest. */
    public static boolean holds(LivingEntity e){
        if(e==null||e.getWorld().isClient)return false;
        var s=STABS.get(e.getUuid());if(s==null)return false;
        return !s.revealed||dies(s.outcome)||s.age(e.getWorld().getTime())<FREE;
    }
    /** Whether {@code p} has an arrow in their chest (raising it, taken in, or the choice being made): no other action. */
    public static boolean stabbing(LivingEntity p){return holds(p);}

    /**
     * The arrow used from {@code hand}: starts it, or tells the player (on the action bar) why not. Returns whether it
     * started.
     */
    public static boolean use(ServerPlayerEntity p,Hand hand){
        if(!MapGate.require(p))return false;
        if(STABS.containsKey(p.getUuid()))return false;
        if(hand!=Hand.MAIN_HAND||!p.getMainHandStack().isOf(MeteorRegistry.STAND_ARROW)){p.sendMessage(Text.literal("스탠드 화살은 주로 쓰는 손에 들고 써 주세요."),true);return false;}
        if(!canUse(ProgressManager.profile(p).stand())){p.sendMessage(Text.literal("이미 스탠드가 있어요. 로카카카로 스탠드를 초기화한 뒤 쓸 수 있어요."),true);return false;}
        if(!ProgressManager.canChange(p)||p.hasVehicle()||p.isSleeping()){p.sendMessage(Text.literal("전투나 컷씬이 끝난 뒤 쓸 수 있어요."),true);return false;}
        if(!standing(p)){p.sendMessage(Text.literal("땅에 선 채로 쓸 수 있어요."),true);return false;}
        String outcome=rigged!=null?rigged:roll(()->RANDOM.nextInt(ODDS));rigged=null;
        long now=p.getServerWorld().getTime();
        var s=new Stab(now,p.getInventory().selectedSlot,outcome);STABS.put(p.getUuid(),s);
        p.stopUsingItem();dev.sangwoo.celestialfall.stand.KagenoManager.freeze(p);
        send(p,s,START);
        CelestialFall.LOGGER.info("Stand arrow: {} ({}) drives it in; it will {}",p.getEntityName(),p.getUuid(),dies(outcome)?"kill them":"awaken "+outcome);
        return true;
    }

    /**
     * Whether {@code p} stands on something: their own word for it, or ground right under their feet (the word comes
     * with their movement: one teleported a moment ago may not have sent it yet).
     */
    static boolean standing(ServerPlayerEntity p){
        return p.isOnGround()||Math.abs(p.getVelocity().y)<.1&&!p.getWorld().isSpaceEmpty(p,p.getBoundingBox().offset(0,-.06,0));
    }
    private static void tick(MinecraftServer server){
        if(STABS.isEmpty())return;
        for(var it=STABS.entrySet().iterator();it.hasNext();){
            var e=it.next();var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null){it.remove();continue;}
            long age=s.age(p.getServerWorld().getTime());
            if(!s.revealed){
                // cut short: dead, gone elsewhere, off the map, the arrow no longer in the hand it was raised in, or
                // caught in another's cutscene or time stop (which hold them their own way)
                boolean holding=p.getInventory().selectedSlot==s.slot&&p.getMainHandStack().isOf(MeteorRegistry.STAND_ARROW);
                boolean caught=StarPlatinumBeatdown.involved(p)||StarPlatinumTimeStop.holds(p)||dev.sangwoo.celestialfall.AtomicManager.immobilized(p);
                if(!p.isAlive()||p.isSpectator()||!MapGate.enabled(p.getWorld())||!holding||caught||age<0||age>REVEAL+40){
                    it.remove();send(p,s,CUT);
                    CelestialFall.LOGGER.info("Stand arrow: {} was cut short at {} ticks (the arrow kept)",p.getEntityName(),age);
                    continue;
                }
                if(age<REVEAL)continue;
                reveal(p,s);
            }
            if(dies(s.outcome)){
                if(!p.isAlive()){it.remove();continue;}
                if(age>=DEATH_AT){it.remove();kill(p);}
            }else if(age>=FREE)it.remove();
        }
    }
    /** The arrow's choice made: the arrow spent, then the stand given (and out) or the fall begun. */
    private static void reveal(ServerPlayerEntity p,Stab s){
        s.revealed=true;
        var stack=p.getMainHandStack();stack.decrement(1);p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();
        // (no chat line: the user's "채팅은 아예 안 나오게", 2.71.0; the one stabbed has the words on their screen, StandArrowHud)
        if(!dies(s.outcome)){
            ProgressManager.set(p,ProgressManager.profile(p).withStand(s.outcome));
            if(StandManager.starPlatinum(p))((StandHolder)p).botand$setStandOut(true);
        }
        send(p,s,REVEALED);
        CelestialFall.LOGGER.info("Stand arrow: {} {}",p.getEntityName(),dies(s.outcome)?"was rejected and falls":"awakened "+s.outcome);
    }
    private static void kill(ServerPlayerEntity p){
        var source=new DamageSource(p.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE));
        p.damage(source,Float.MAX_VALUE);
        CelestialFall.LOGGER.info("Stand arrow: {} {}",p.getEntityName(),p.isAlive()?"survived its death (?)":"died");
    }
    /** Leaving: cut short before the choice (nothing happens); after a death was chosen, they die as they leave. */
    private static void left(ServerPlayerEntity p){
        var s=STABS.remove(p.getUuid());if(s==null)return;
        if(s.revealed&&dies(s.outcome)&&p.isAlive())kill(p);
    }

    /** To everyone within 128 blocks of the user (and the user): who (and their body's entity id), what happened, the stab's start and the world's tick now, what the arrow chose once shown. */
    private static void send(ServerPlayerEntity p,Stab s,int type){
        var world=p.getServerWorld();long now=world.getTime();
        for(var viewer:new ArrayList<>(world.getPlayers())){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeVarInt(p.getId());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);
            b.writeLong(s.start);b.writeLong(now);b.writeString(type==REVEALED?s.outcome:"",32);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
