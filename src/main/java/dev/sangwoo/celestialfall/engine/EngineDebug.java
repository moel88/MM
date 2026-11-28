package dev.sangwoo.celestialfall.engine;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The combat engine's tools (2.157.0, OP): {@code /botand engine debug [on|off]} shows its holder what the engine
 * judges near them, the shapes of the blows and the boxes they were judged against, where the targets stood as the
 * striker saw them (wound back, Rewind) beside where they stand, with the frame data of the moves (EngineDebugClient);
 * {@code /botand engine lag <targets> <ms>} puts that much more round trip on a player's connection (half each way,
 * EngineLag), to try the lag compensation and the client's prediction with a far server on a near one (0: none).
 */
public final class EngineDebug {
    private EngineDebug(){}
    /** What a debug packet shows. */
    public static final int RAY=1;
    private static final Set<UUID> VIEWERS=ConcurrentHashMap.newKeySet();
    /** How far from a viewer what the engine judges is shown (blocks). */
    static final double SHOWN=64;

    public static boolean on(ServerPlayerEntity p){return VIEWERS.contains(p.getUuid());}
    public static void leave(UUID player){VIEWERS.remove(player);}
    public static void clear(){VIEWERS.clear();EngineLag.clear();}

    public static LiteralArgumentBuilder<ServerCommandSource> commands(){
        return CommandManager.literal("engine").requires(s->s.hasPermissionLevel(2))
            .then(CommandManager.literal("debug")
                .executes(c->debug(c.getSource(),!on(c.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("on").executes(c->debug(c.getSource(),true)))
                .then(CommandManager.literal("off").executes(c->debug(c.getSource(),false))))
            .then(CommandManager.literal("lag")
                .then(CommandManager.argument("targets",EntityArgumentType.players())
                    .then(CommandManager.argument("ms",IntegerArgumentType.integer(0,1000))
                        .executes(c->lag(c.getSource(),EntityArgumentType.getPlayers(c,"targets"),IntegerArgumentType.getInteger(c,"ms"))))));
    }
    private static int debug(ServerCommandSource source,boolean on)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=source.getPlayerOrThrow();
        if(on)VIEWERS.add(p.getUuid());else VIEWERS.remove(p.getUuid());
        source.sendFeedback(()->Text.translatable(on?"message.botand.engine_debug_on":"message.botand.engine_debug_off"),false);
        return on?1:0;
    }
    private static int lag(ServerCommandSource source,Collection<ServerPlayerEntity> players,int ms){
        for(var p:players)EngineLag.set(p.getUuid(),ms);
        source.sendFeedback(()->Text.translatable("message.botand.engine_lag",players.size(),ms),false);
        return players.size();
    }

    /** Whoever has the debug view on near {@code at}. */
    private static Set<ServerPlayerEntity> viewers(ServerWorld world,Vec3d at){
        Set<ServerPlayerEntity> out=new HashSet<>();
        if(VIEWERS.isEmpty())return out;
        for(var p:world.getPlayers())if(VIEWERS.contains(p.getUuid())&&p.squaredDistanceTo(at)<SHOWN*SHOWN&&ServerPlayNetworking.canSend(p,EngineNet.DEBUG))out.add(p);
        return out;
    }
    /**
     * A ray judged: {@code attacker}'s move {@code move} (its number), hit {@code hit}, at {@code tick}, its targets wound
     * back {@code seen} ticks, from {@code eye} to {@code end} meeting boxes grown by {@code pad}; {@code struck} the one
     * it met (null if none), {@code near} those judged, each with where it stood as the striker saw it and where it
     * stands.
     */
    public static void ray(ServerPlayerEntity attacker,int move,int hit,long tick,int seen,Vec3d eye,Vec3d end,double pad,LivingEntity struck,List<LivingEntity> near){
        if(VIEWERS.isEmpty()||!(attacker.getWorld() instanceof ServerWorld world))return;
        var to=viewers(world,eye);if(to.isEmpty())return;
        var b=PacketByteBufs.create();b.writeByte(RAY);b.writeIdentifier(world.getRegistryKey().getValue());
        b.writeVarInt(attacker.getId());b.writeVarInt(move);b.writeVarInt(hit);b.writeLong(tick);b.writeVarInt(seen);
        b.writeDouble(eye.x);b.writeDouble(eye.y);b.writeDouble(eye.z);b.writeDouble(end.x);b.writeDouble(end.y);b.writeDouble(end.z);b.writeDouble(pad);
        b.writeVarInt(struck==null?-1:struck.getId());
        int n=Math.min(near.size(),16);b.writeVarInt(n);
        for(int i=0;i<n;i++){
            var e=near.get(i);b.writeVarInt(e.getId());
            box(b,Rewind.boxAt(e,tick-seen));box(b,e.getBoundingBox());
        }
        for(var p:to)ServerPlayNetworking.send(p,EngineNet.DEBUG,PacketByteBufs.copy(b));
    }
    private static void box(net.minecraft.network.PacketByteBuf b,Box x){b.writeDouble(x.minX);b.writeDouble(x.minY);b.writeDouble(x.minZ);b.writeDouble(x.maxX);b.writeDouble(x.maxY);b.writeDouble(x.maxZ);}
    /** For the checks: who has the debug view on and the simulated round trips. */
    public static String describe(){return "debug="+VIEWERS.size()+" lag="+EngineLag.describe();}
    static Map<UUID,Integer> lags(){return EngineLag.all();}
}
