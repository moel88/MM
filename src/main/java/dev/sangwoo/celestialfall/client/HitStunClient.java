package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.combat.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.*;
import net.minecraft.util.math.Vec3d;

/**
 * Short-lived synchronized hit reactions; map is cleared on every world/connection change. A basic attack's stagger
 * (2.68.0) is a flinch only: its lock and slow come with the combat state. A barrage's hold also holds the body where it
 * stands ({@code held}). Neither shows a notice (2.68.0, user request: "피격 경직 연속 타격 이거 뜨는거 없애줘").
 */
public final class HitStunClient {
    public record Reaction(UUID attacker,int entityId,long until,long hitAt,int strike,boolean held) {}
    private static final Map<UUID,Reaction> REACTIONS=new HashMap<>();
    private static Object world;
    private HitStunClient() {}
    public static void clear(){REACTIONS.clear();world=null;}
    public static void initialize() {
        HitStun.setClientCheck(HitStunClient::stunned);
        ClientPlayNetworking.registerGlobalReceiver(HitStun.PACKET,(mc,handler,buffer,response)->{
            var dimension=buffer.readIdentifier();UUID target=buffer.readUuid();int entityId=buffer.readInt();UUID attacker=buffer.readUuid();
            long until=buffer.readLong(),hitAt=buffer.readLong();int strike=buffer.readUnsignedByte();boolean held=!buffer.isReadable()||buffer.readBoolean();
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){REACTIONS.clear();world=mc.world;}
                if(until==0)REACTIONS.remove(target);
                else if(strike>=1&&strike<=3) {
                    REACTIONS.put(target,new Reaction(attacker,entityId,until,hitAt,strike,held));
                    Entity e=mc.world.getEntityById(entityId);if(held&&e instanceof LivingEntity living&&e.getUuid().equals(target))HitStun.freeze(living);
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{REACTIONS.clear();world=null;});
        ClientTickEvents.START_CLIENT_TICK.register(mc->{
            if(world!=mc.world){REACTIONS.clear();world=mc.world;}
            if(mc.world!=null)REACTIONS.values().removeIf(r->r.until<=mc.world.getTime());
            if(mc.player!=null&&stunned(mc.player)){HitStun.freeze(mc.player);mc.player.clearActiveItem();}
        });
    }
    public static Reaction reaction(LivingEntity e){var r=REACTIONS.get(e.getUuid());return r!=null&&r.entityId==e.getId()&&r.until>e.getWorld().getTime()&&e.isAlive()?r:null;}
    /** Whether a barrage holds {@code e} where it stands now (a stagger only flinches it here). */
    public static boolean stunned(LivingEntity e){if(!MapClient.enabled()||e.getWorld()!=world)return false;var r=reaction(e);return r!=null&&r.held();}
    public static boolean localStunned(){var p=MinecraftClient.getInstance().player;return p!=null&&stunned(p);}
    public static boolean aimCapturedTarget() {
        var mc=MinecraftClient.getInstance();if(!MapClient.enabled()||mc.player==null||mc.world==null||localStunned())return false;
        for(var entry:REACTIONS.entrySet()) {
            var r=entry.getValue();if(!r.attacker.equals(mc.player.getUuid())||r.until<=mc.world.getTime())continue;
            Entity target=mc.world.getEntityById(r.entityId);
            if(!(target instanceof LivingEntity living)||!target.getUuid().equals(entry.getKey())||!target.isAlive()||mc.player.squaredDistanceTo(target)>20.25||!mc.player.canSee(target))continue;
            Vec3d d=new Vec3d(target.getX(),target.getBodyY(.7),target.getZ()).subtract(mc.player.getEyePos());
            float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));mc.player.setYaw(yaw);mc.player.setHeadYaw(yaw);
            mc.player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalLength())));return true;
        }
        return false;
    }
}
