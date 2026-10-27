package dev.sangwoo.celestialfall.progression;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ProgressManager {
    public static final Identifier STATE=CelestialFall.id("progress_v2");
    private ProgressManager() {}
    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->joined(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->joined(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var p:server.getPlayerManager().getPlayerList())if(p.age%20==0)applyHealth(p);
        });
    }
    public static ProgressProfile profile(ServerPlayerEntity p){return ProgressState.get(p.getServer()).profile(p.getUuid());}
    /** Whether this player is awakened (2.72.0): Star Platinum's time stop is theirs (ProgressState.awakened). */
    public static boolean awakened(ServerPlayerEntity p){return ProgressState.get(p.getServer()).awakened(p.getUuid());}
    /** Awakens this player or takes it back (an operator's command for now, the awakening quest later); their client is told. */
    public static void awaken(ServerPlayerEntity p,boolean value){ProgressState.get(p.getServer()).awaken(p.getUuid(),value);sync(p);}
    public static void joined(ServerPlayerEntity p){
        applyHealth(p);dev.sangwoo.celestialfall.menu.MenuServer.observe(p);sync(p);
    }
    public static boolean canUseKageno(ServerPlayerEntity p){return profile(p).stand().equals("kageno");}
    public static void applyHealth(ServerPlayerEntity p){
        dev.sangwoo.celestialfall.stand.StandManager.refresh(p);
        var map=dev.sangwoo.celestialfall.map.MapState.get(p.getServer());
        boolean first=map.firstVisit(p.getUuid());
        double base=p.getAttributeBaseValue(EntityAttributes.GENERIC_MAX_HEALTH);
        if(dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())) {
            var hunger=new net.minecraft.nbt.NbtCompound();p.getHungerManager().writeNbt(hunger);
            map.remember(p.getUuid(),first&&base==200?20:base,hunger);
            setBaseHealth(p,StandStats.get(profile(p).stand()).health());
        } else {
            var back=map.forget(p.getUuid());
            if(back!=null){setBaseHealth(p,back.baseHealth());p.getHungerManager().readNbt(back.hunger());}
            else if(first&&base==200)setBaseHealth(p,20); // One-time legacy migration; never police normal-world HP after this.
        }
    }
    /** Preserve the health fraction across the first 20->200 migration and future stand changes. */
    public static void setBaseHealth(LivingEntity entity,double base) {
        var attribute=entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if(attribute==null||attribute.getBaseValue()==base)return;
        float health=entity.getHealth(),max=entity.getMaxHealth();
        attribute.setBaseValue(base);
        entity.setHealth(max>0?Math.min(entity.getMaxHealth(),health/max*entity.getMaxHealth()):0);
    }
    public static void set(ServerPlayerEntity p,ProgressProfile value){if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld()))return;dev.sangwoo.celestialfall.menu.MenuServer.observe(p);ProgressState.get(p.getServer()).set(p.getUuid(),value);dev.sangwoo.celestialfall.menu.MenuServer.observe(p);applyHealth(p);sync(p);}
    /** Entry point for future quests, bosses and progression rewards; clients cannot call this directly. */
    public static void grantLevels(ServerPlayerEntity p,int levels){if(levels>0)set(p,profile(p).addLevels(levels));}
    public static boolean canChange(ServerPlayerEntity p){return dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())&&p.isAlive()&&!p.isSpectator()&&!CombatManager.busy(p)&&!CombatManager.cinematic(p);}
    public static void sync(ServerPlayerEntity p) {
        if(!dev.sangwoo.celestialfall.map.MapGate.enabled(p.getWorld())||!ServerPlayNetworking.canSend(p,STATE))return;
        var value=profile(p);var b=PacketByteBufs.create();
        b.writeVarInt(value.level());b.writeVarInt(value.rebirths());b.writeVarInt(value.transcendence());b.writeString(value.stand(),32);
        b.writeLong(value.money());b.writeVarInt(value.xp());
        // (2.72.0: whether they are awakened, so their client knows whether H is a key for them)
        b.writeBoolean(awakened(p));
        ServerPlayNetworking.send(p,STATE,b);
    }
}
