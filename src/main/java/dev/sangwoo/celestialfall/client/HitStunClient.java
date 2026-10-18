package dev.sangwoo.celestialfall.client;
import dev.sangwoo.celestialfall.combat.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/** Short-lived synchronized hit reactions; map is cleared on every world/connection change. */
public final class HitStunClient {
    public record Reaction(UUID attacker,int entityId,long until,long hitAt,int strike) {}
    private static final Map<UUID,Reaction> REACTIONS=new HashMap<>();
    private static Object world;
    private HitStunClient() {}
    public static void clear(){REACTIONS.clear();world=null;}
    public static void initialize() {
        HitStun.setClientCheck(HitStunClient::stunned);
        ClientPlayNetworking.registerGlobalReceiver(HitStun.PACKET,(mc,handler,buffer,response)->{
            var dimension=buffer.readIdentifier();UUID target=buffer.readUuid();int entityId=buffer.readInt();UUID attacker=buffer.readUuid();
            long until=buffer.readLong(),hitAt=buffer.readLong();int strike=buffer.readUnsignedByte();
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){REACTIONS.clear();world=mc.world;}
                if(until==0)REACTIONS.remove(target);
                else if(strike>=1&&strike<=3) {
                    REACTIONS.put(target,new Reaction(attacker,entityId,until,hitAt,strike));
                    Entity e=mc.world.getEntityById(entityId);if(e instanceof LivingEntity living&&e.getUuid().equals(target))HitStun.freeze(living);
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{REACTIONS.clear();world=null;});
        ClientTickEvents.START_CLIENT_TICK.register(mc->{
            if(world!=mc.world){REACTIONS.clear();world=mc.world;}
            if(mc.world!=null)REACTIONS.values().removeIf(r->r.until<=mc.world.getTime());
            if(mc.player!=null&&stunned(mc.player)){HitStun.freeze(mc.player);mc.player.clearActiveItem();}
        });
        HudRenderCallback.EVENT.register((draw,delta)->{
            var mc=MinecraftClient.getInstance();if(mc.player==null||!stunned(mc.player)||mc.options.hudHidden||CinematicDirector.isActive())return;
            int x=draw.getScaledWindowWidth()/2,y=draw.getScaledWindowHeight()-98;
            draw.fill(x-75,y-3,x+75,y+12,0xd01c1420);
            draw.drawCenteredTextWithShadow(mc.textRenderer,Text.translatable("hud.botand.hitstun"),x,y,0xffffab9d);
        });
    }
    public static Reaction reaction(LivingEntity e){var r=REACTIONS.get(e.getUuid());return r!=null&&r.entityId==e.getId()&&r.until>e.getWorld().getTime()&&e.isAlive()?r:null;}
    public static boolean stunned(LivingEntity e){return MapClient.enabled()&&e.getWorld()==world&&reaction(e)!=null;}
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
