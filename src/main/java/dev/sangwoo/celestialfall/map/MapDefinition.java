package dev.sangwoo.celestialfall.map;

import com.google.gson.*;
import dev.sangwoo.celestialfall.CelestialFall;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A Botand map that describes itself: data/botand/map/definition.json in its world's data pack names the map and
 * where the mod's things stand on it (the two merchants, the field supply spots) and its named places. A world with
 * one is a Botand map like the delivered city (recognised by its bundled functions); the city keeps its own built-in
 * positions and fixes.
 *
 * <pre>
 * {"format":1,"id":"botand:seaside","name":"...","spawn":[0,65,29,0],
 *  "merchants":{"seller":{"pos":[x,y,z],"yaw":90},"buyer":{"pos":[x,y,z],"yaw":90}},
 *  "loot":[[x,y,z],...],"places":{"station":[x,y,z],...}}
 * </pre>
 */
public record MapDefinition(String id,String name,BlockPos spawn,Vec3d seller,float sellerYaw,Vec3d buyer,float buyerYaw,List<BlockPos> loot,Map<String,BlockPos> places) {
    public static final Identifier FILE=new Identifier("botand","map/definition.json");
    /** The most field supply spots a map may have. */
    public static final int MAX_LOOT=64;

    /** The definition in the server's data packs, if there is one and it reads cleanly. */
    public static Optional<MapDefinition> load(MinecraftServer server){
        var resource=server.getResourceManager().getResource(FILE);
        if(resource.isEmpty())return Optional.empty();
        try(var in=new InputStreamReader(resource.get().getInputStream(),StandardCharsets.UTF_8)){
            return Optional.of(parse(JsonParser.parseReader(in)));
        }catch(Exception e){
            CelestialFall.LOGGER.warn("Ignoring Botand map definition {}: {}",FILE,e.getMessage());
            return Optional.empty();
        }
    }

    /** Reads a definition; anything missing or malformed is an IllegalArgumentException. */
    public static MapDefinition parse(JsonElement json){
        if(!json.isJsonObject())throw new IllegalArgumentException("not an object");
        var o=json.getAsJsonObject();
        if(!o.has("format")||o.get("format").getAsInt()!=1)throw new IllegalArgumentException("unknown format");
        String id=string(o,"id"),name=string(o,"name");
        var spawnArr=array(o,"spawn");
        BlockPos spawn=block(spawnArr);
        var merchants=o.has("merchants")&&o.get("merchants").isJsonObject()?o.getAsJsonObject("merchants"):null;
        if(merchants==null)throw new IllegalArgumentException("no merchants");
        Object[] seller=merchant(merchants,"seller"),buyer=merchant(merchants,"buyer");
        List<BlockPos> loot=new ArrayList<>();
        for(var e:array(o,"loot"))loot.add(block(e.getAsJsonArray()));
        if(loot.size()>MAX_LOOT)throw new IllegalArgumentException("too many loot spots");
        if(new HashSet<>(loot).size()!=loot.size())throw new IllegalArgumentException("duplicate loot spots");
        Map<String,BlockPos> places=new LinkedHashMap<>();
        if(o.has("places")&&o.get("places").isJsonObject())for(var e:o.getAsJsonObject("places").entrySet())places.put(e.getKey(),block(e.getValue().getAsJsonArray()));
        return new MapDefinition(id,name,spawn,(Vec3d)seller[0],(float)seller[1],(Vec3d)buyer[0],(float)buyer[1],List.copyOf(loot),Collections.unmodifiableMap(places));
    }
    private static Object[] merchant(JsonObject merchants,String key){
        if(!merchants.has(key)||!merchants.get(key).isJsonObject())throw new IllegalArgumentException("no "+key);
        var m=merchants.getAsJsonObject(key);
        var p=array(m,"pos");if(p.size()!=3)throw new IllegalArgumentException(key+" needs x, y, z");
        double x=p.get(0).getAsDouble(),y=p.get(1).getAsDouble(),z=p.get(2).getAsDouble();
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException(key+" is not finite");
        float yaw=m.has("yaw")?m.get("yaw").getAsFloat():0f;
        return new Object[]{new Vec3d(x,y,z),yaw};
    }
    private static String string(JsonObject o,String key){
        if(!o.has(key)||!o.get(key).isJsonPrimitive())throw new IllegalArgumentException("no "+key);
        return o.get(key).getAsString();
    }
    private static JsonArray array(JsonObject o,String key){
        if(!o.has(key)||!o.get(key).isJsonArray())throw new IllegalArgumentException("no "+key);
        return o.getAsJsonArray(key);
    }
    private static BlockPos block(JsonArray a){
        if(a.size()<3)throw new IllegalArgumentException("a position needs x, y, z");
        return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
    }
}
