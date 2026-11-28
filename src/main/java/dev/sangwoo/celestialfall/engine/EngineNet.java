package dev.sangwoo.celestialfall.engine;

import dev.sangwoo.celestialfall.CelestialFall;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * The combat engine's few packets (2.157.0). One input from the client for every action everyone has, with its number
 * and the world time the client showed ({@link #INPUT}); one packet of what happened for everyone near ({@link #EVENT}:
 * a perfect guard, an evasive); and the debug view's shapes for whoever has it on ({@link #DEBUG}, EngineDebug). The
 * combat state itself still goes out as before (CombatManager.STATE), with the engine's fields at its end.
 */
public final class EngineNet {
    private EngineNet(){}
    public static final Identifier INPUT=CelestialFall.id("engine_input_v1"),EVENT=CelestialFall.id("engine_event_v1"),DEBUG=CelestialFall.id("engine_debug_v1");
    /** The input's actions. */
    public static final int ATTACK=0,GUARD_UP=1,GUARD_DOWN=2,DASH=3,EVADE=4;
    /** The input's size: its action, its number, the world time the client showed, the two ways pressed. */
    public static final int INPUT_BYTES=1+2+8+1+1;
    /** What an event tells. */
    public static final int PERFECT=1,EVADED=2;
    /** How far the news of an event goes (blocks). */
    static final double HEARD=128;

    /** Writes an input (for the client). */
    public static void writeInput(PacketByteBuf b,int action,int seq,long clientTick,int a,int c){
        b.writeByte(action);b.writeShort(seq&0xffff);b.writeLong(clientTick);b.writeByte(a);b.writeByte(c);
    }
    private static Set<ServerPlayerEntity> near(ServerWorld world,Vec3d at,LivingEntity... also){
        Set<ServerPlayerEntity> out=new HashSet<>();
        for(var p:world.getPlayers())if(p.squaredDistanceTo(at)<HEARD*HEARD)out.add(p);
        for(var e:also)if(e instanceof ServerPlayerEntity p&&p.getWorld()==world)out.add(p);
        return out;
    }
    private static void send(ServerWorld world,Vec3d at,PacketByteBuf b,LivingEntity... also){
        for(var p:near(world,at,also)){
            if(!ServerPlayNetworking.canSend(p,EVENT))continue;
            ServerPlayNetworking.send(p,EVENT,PacketByteBufs.copy(b));
        }
    }
    /**
     * A perfect guard: {@code guard} took a blow at {@code at} perfectly at {@code tick}, {@code striker} (null if
     * none known) turned back or not.
     */
    public static void perfect(ServerPlayerEntity guard,LivingEntity striker,boolean parried,Vec3d at,long tick){
        if(!(guard.getWorld() instanceof ServerWorld world))return;
        var b=PacketByteBufs.create();b.writeByte(PERFECT);b.writeIdentifier(world.getRegistryKey().getValue());
        b.writeVarInt(guard.getId());b.writeUuid(guard.getUuid());b.writeVarInt(striker==null?-1:striker.getId());b.writeBoolean(parried);
        b.writeDouble(at.x);b.writeDouble(at.y);b.writeDouble(at.z);b.writeLong(tick);
        send(world,at,b,guard,striker);
    }
    /** An evasive: {@code who} got out at {@code start} from {@code from}, facing {@code yaw}, going {@code forward}, {@code side}. */
    public static void evaded(ServerPlayerEntity who,long start,Vec3d from,float yaw,float forward,float side){
        if(!(who.getWorld() instanceof ServerWorld world))return;
        var b=PacketByteBufs.create();b.writeByte(EVADED);b.writeIdentifier(world.getRegistryKey().getValue());
        b.writeVarInt(who.getId());b.writeUuid(who.getUuid());b.writeLong(start);
        b.writeDouble(from.x);b.writeDouble(from.y);b.writeDouble(from.z);b.writeFloat(yaw);b.writeFloat(forward);b.writeFloat(side);
        send(world,from,b,who);
    }
}
