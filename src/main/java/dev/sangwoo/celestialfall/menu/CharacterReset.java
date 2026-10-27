package dev.sangwoo.celestialfall.menu;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.map.MapGate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "캐릭터 재설정하기" from the pause menu (2.45.0, user request: the character dies at once, as Roblox's reset does). The
 * client asks with an empty packet; on the Botand map a living player who is not spectating dies of the reset damage
 * type, which nothing stops (armour, resistance, a shield, the guard, invulnerability, a totem), the way /kill kills.
 * The death message says the player reset. One reset a second, so a double click or a held Enter kills once. A long
 * cooldown still running (Atomic, the time stop, the beatdown) begins again in full as they come back to life, as after
 * any death (2.74.0, LongCooldowns; 2.73.0 began them again as the reset killed).
 */
public final class CharacterReset {
    public static final Identifier REQUEST=CelestialFall.id("character_reset_v1");
    public static final RegistryKey<DamageType> DAMAGE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,CelestialFall.id("reset"));
    private static final Map<UUID,Integer> LAST=new HashMap<>();
    private CharacterReset(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(REQUEST,(s,p,h,b,r)->{if(b.readableBytes()!=0)return;s.execute(()->reset(p));});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->LAST.remove(h.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->LAST.clear());
    }
    /** Whether this player may reset now. */
    public static boolean allowed(ServerPlayerEntity p){return MapGate.enabled(p.getWorld())&&p.isAlive()&&!p.isSpectator()&&!p.isRemoved();}
    /** The one-a-second rule, apart from Minecraft for tests. */
    static final class Gate{
        /** Server ticks between two resets of one player. */
        static final int GAP=20;
        /** Whether a reset {@code now} (server ticks) comes soon enough after the last one ({@code last}, or null) to be dropped. */
        static boolean tooSoon(Integer last,int now){return last!=null&&now-last>=0&&now-last<GAP;}
        private Gate(){}
    }

    static void reset(ServerPlayerEntity p){
        if(!allowed(p))return;
        int now=p.getServer().getTicks();
        if(Gate.tooSoon(LAST.get(p.getUuid()),now))return;
        LAST.put(p.getUuid(),now);
        var source=new DamageSource(p.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE));
        p.damage(source,Float.MAX_VALUE);
        CelestialFall.LOGGER.info("Character reset: {} ({})",p.getEntityName(),p.isAlive()?"survived":"died");
    }
}
