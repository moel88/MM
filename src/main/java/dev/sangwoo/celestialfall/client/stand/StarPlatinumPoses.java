package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import dev.sangwoo.celestialfall.stand.StarPlatinumStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Star Platinum's stances as the poses file gives them (tools/stand/make_star_platinum.py writes it with the model), and
 * a bone's pose in a frame: the stances weighed as the look says, its own movement (StarPlatinumMotion) on top.
 */
final class StarPlatinumPoses {
    private static final float[] REST=new float[6];
    /** Per stance: each bone's turn (degrees, as Blockbench writes it) and offset (units). */
    private static List<Map<String,float[]>> stances;
    private final Map<String,StarPlatinumMotion> motions=new HashMap<>();

    /** The bone's turn x, y, z (degrees, Blockbench's way) and offset x, y, z (units) at {@code seconds} on the clock. */
    void pose(String bone,StarPlatinumLook look,double seconds,float[] out){
        Arrays.fill(out,0);
        var all=stances();
        for(int s=0;s<look.weights.length&&s<all.size();s++){
            float w=look.weights[s];if(w==0)continue;
            float[] v=all.get(s).getOrDefault(bone,REST);for(int i=0;i<6;i++)out[i]+=w*v[i];
        }
        motions.computeIfAbsent(bone,StarPlatinumMotion::of).add(out,seconds,look);
    }

    static List<Map<String,float[]>> stances(){
        if(stances!=null)return stances;
        var list=new ArrayList<Map<String,float[]>>();
        try(var in=StarPlatinumPoses.class.getResourceAsStream("/assets/celestialfall/stands/star_platinum_poses.json")){
            var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"poses"),StandardCharsets.UTF_8)).getAsJsonObject();
            for(String stance:StarPlatinumStance.NAMES){
                var map=new HashMap<String,float[]>();var bones=root.getAsJsonObject(stance);
                if(bones!=null)for(var entry:bones.entrySet()){
                    var o=entry.getValue().getAsJsonObject();float[] v=new float[6];
                    if(o.has("rot"))for(int i=0;i<3;i++)v[i]=o.getAsJsonArray("rot").get(i).getAsFloat();
                    if(o.has("pos"))for(int i=0;i<3;i++)v[3+i]=o.getAsJsonArray("pos").get(i).getAsFloat();
                    map.put(entry.getKey(),v);
                }
                list.add(map);
            }
        }catch(Exception ex){
            CelestialFall.LOGGER.error("Star Platinum's poses could not be read",ex);
            list.clear();for(int i=0;i<StarPlatinumStance.NAMES.size();i++)list.add(Map.of());
        }
        return stances=list;
    }
}
