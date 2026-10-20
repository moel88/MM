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
 * where the mod's things stand on it (the two merchants, the field supply spots), its named places, the wires
 * hung between its poles (drawn by the mod as thin sagging lines, being too thin for blocks) and the far coast across
 * the water (mountains beyond the map, drawn by the mod behind everything, too far off for blocks). A world with one is a
 * Botand map like the delivered city (recognised by its bundled functions); the city keeps its own built-in
 * positions and fixes.
 *
 * <pre>
 * {"format":1,"id":"botand:seaside","name":"...","spawn":[0,65,29,0],
 *  "merchants":{"seller":{"pos":[x,y,z],"yaw":90},"buyer":{"pos":[x,y,z],"yaw":90}},
 *  "loot":[[x,y,z],...],"places":{"station":[x,y,z],...},
 *  "wires":[[x0,y0,z0,x1,y1,z1,sag],...],"backdrop":[[x,z,rx,rz,top,seed],...]}
 * </pre>
 */
public record MapDefinition(String id,String name,BlockPos spawn,Vec3d seller,float sellerYaw,Vec3d buyer,float buyerYaw,List<BlockPos> loot,Map<String,BlockPos> places,List<Wire> wires,List<Peak> backdrop) {
    public static final Identifier FILE=new Identifier("botand","map/definition.json");
    /** The most field supply spots a map may have. */
    public static final int MAX_LOOT=64;
    /** The most wires a map may have, the longest span and the deepest sag (blocks). */
    public static final int MAX_WIRES=4096;
    public static final float MAX_SPAN=64,MAX_SAG=8;
    /** A wire from one point to another, hanging {@code sag} blocks below the straight line at its middle. */
    public record Wire(float x0,float y0,float z0,float x1,float y1,float z1,float sag) {}
    /** The most mountains a backdrop may have, how far from the map's middle they may stand, their widest and highest (blocks). */
    public static final int MAX_PEAKS=64;
    public static final float MAX_REACH=30000,MAX_RADIUS=8000,MAX_TOP=1000;
    /**
     * A mountain beyond the map, out at sea: its foot an ellipse rx blocks across east to west and rz north to south
     * around x, z, rising from the water to y {@code top}; its seed shapes its ridges and shore.
     */
    public record Peak(float x,float z,float rx,float rz,float top,int seed) {}

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
        List<Wire> wires=new ArrayList<>();
        if(o.has("wires"))for(var e:array(o,"wires"))wires.add(wire(e));
        if(wires.size()>MAX_WIRES)throw new IllegalArgumentException("too many wires");
        List<Peak> backdrop=new ArrayList<>();
        if(o.has("backdrop"))for(var e:array(o,"backdrop"))backdrop.add(peak(e));
        if(backdrop.size()>MAX_PEAKS)throw new IllegalArgumentException("too many mountains");
        return new MapDefinition(id,name,spawn,(Vec3d)seller[0],(float)seller[1],(Vec3d)buyer[0],(float)buyer[1],List.copyOf(loot),Collections.unmodifiableMap(places),List.copyOf(wires),List.copyOf(backdrop));
    }
    private static Peak peak(JsonElement e){
        if(!e.isJsonArray()||e.getAsJsonArray().size()!=6)throw new IllegalArgumentException("a mountain needs x, z, rx, rz, top, seed");
        var a=e.getAsJsonArray();float[] v=new float[5];
        for(int i=0;i<5;i++){v[i]=a.get(i).getAsFloat();if(!Float.isFinite(v[i]))throw new IllegalArgumentException("a mountain is not finite");}
        if(Math.abs(v[0])>MAX_REACH||Math.abs(v[1])>MAX_REACH)throw new IllegalArgumentException("a mountain stands too far off");
        if(v[2]<=0||v[3]<=0||v[2]>MAX_RADIUS||v[3]>MAX_RADIUS)throw new IllegalArgumentException("a mountain is too wide");
        if(v[4]<=0||v[4]>MAX_TOP)throw new IllegalArgumentException("a mountain is too high");
        return new Peak(v[0],v[1],v[2],v[3],v[4],a.get(5).getAsInt());
    }
    private static Wire wire(JsonElement e){
        if(!e.isJsonArray()||e.getAsJsonArray().size()!=7)throw new IllegalArgumentException("a wire needs x0, y0, z0, x1, y1, z1, sag");
        var a=e.getAsJsonArray();float[] v=new float[7];
        for(int i=0;i<7;i++){v[i]=a.get(i).getAsFloat();if(!Float.isFinite(v[i]))throw new IllegalArgumentException("a wire is not finite");}
        double span=Math.sqrt((v[3]-v[0])*(v[3]-v[0])+(v[4]-v[1])*(v[4]-v[1])+(v[5]-v[2])*(v[5]-v[2]));
        if(span>MAX_SPAN)throw new IllegalArgumentException("a wire is longer than "+MAX_SPAN);
        if(v[6]<0||v[6]>MAX_SAG)throw new IllegalArgumentException("a wire sags too far");
        return new Wire(v[0],v[1],v[2],v[3],v[4],v[5],v[6]);
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
