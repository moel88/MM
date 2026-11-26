package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.stand.GoldExperienceStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Gold Experience's stances as the poses file gives them (tools/stand/make_gold_experience.py writes it with the model),
 * and a bone's pose in a frame (2.145.0): going over from one stance to the next its whole turn (its own in the model and
 * the stance's on top) goes the short way from the one to the other, as Star Platinum's do ({@link StarPlatinumPoses},
 * whose arithmetic of turns this uses), and its own movement ({@link GoldExperienceMotion}) comes on top.
 */
final class GoldExperiencePoses {
    private static final float[] REST=new float[6];
    /** Per stance: each bone's turn (degrees, as Blockbench writes it) and offset (units). */
    private static List<Map<String,float[]>> stances;
    /** Each bone's own turn in the model (degrees, Blockbench's way): a stance's turn is added to it. */
    private static Map<String,float[]> rests;
    private final Map<String,GoldExperienceMotion> motions=new HashMap<>();
    private final double[] qa=new double[4],qb=new double[4],e=new double[3];

    /**
     * The bone's turn x, y, z (degrees, Blockbench's way) and offset x, y, z (units) at {@code seconds} on the clock,
     * the share {@code k} of the way over from the stance {@code from} to the stance {@code to}.
     */
    void pose(String bone,int from,int to,float k,double seconds,float[] out){
        stance(bone,from,to,k,out);
        // standing to be looked at it does not float
        float calm=1-(to==GoldExperienceStance.STAND?k:0)-(from==GoldExperienceStance.STAND?1-k:0);
        motions.computeIfAbsent(bone,GoldExperienceMotion::of).add(out,seconds,Math.max(0,calm));
    }
    /** The stances alone, without the bone's own movement. */
    void stance(String bone,int from,int to,float k,float[] out){
        var all=stances();
        float[] a=all.get(Math.floorMod(from,all.size())).getOrDefault(bone,REST),b=all.get(Math.floorMod(to,all.size())).getOrDefault(bone,REST);
        if(from==to||k>=1){System.arraycopy(b,0,out,0,6);return;}
        if(k<=0){System.arraycopy(a,0,out,0,6);return;}
        for(int i=0;i<6;i++)out[i]=a[i]+(b[i]-a[i])*k;
        var rest=rests().getOrDefault(bone,REST);
        StarPlatinumPoses.quat(rest[0]+a[0],rest[1]+a[1],rest[2]+a[2],qa);StarPlatinumPoses.quat(rest[0]+b[0],rest[1]+b[1],rest[2]+b[2],qb);
        StarPlatinumPoses.slerp(qa,qb,k);StarPlatinumPoses.euler(qa,e);
        // written as angles on the branch nearest the angles blended directly, less the bone's own turn
        double[] near={out[0]+rest[0],out[1]+rest[1],out[2]+rest[2]},other={e[0]+180,180-e[1],e[2]+180};
        double[] pick=StarPlatinumPoses.distance(e,near)<=StarPlatinumPoses.distance(other,near)?e:other;
        for(int i=0;i<3;i++)out[i]=(float)(near[i]+StarPlatinumPoses.wrap(pick[i]-near[i])-rest[i]);
    }
    static List<Map<String,float[]>> stances(){
        if(stances!=null)return stances;
        var list=new ArrayList<Map<String,float[]>>();
        try(var in=GoldExperiencePoses.class.getResourceAsStream("/assets/celestialfall/stands/gold_experience_poses.json")){
            var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"poses"),StandardCharsets.UTF_8)).getAsJsonObject();
            for(String stance:GoldExperienceStance.NAMES){
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
            CelestialFall.LOGGER.error("Gold Experience's poses could not be read",ex);
            list.clear();for(int i=0;i<GoldExperienceStance.NAMES.size();i++)list.add(Map.of());
        }
        return stances=list;
    }
    static Map<String,float[]> rests(){
        if(rests!=null)return rests;
        var map=new HashMap<String,float[]>();
        try(var in=GoldExperiencePoses.class.getResourceAsStream("/assets/celestialfall/geo/entity/gold_experience.geo.json")){
            var bones=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"model"),StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            for(var b:bones){
                var o=b.getAsJsonObject();if(!o.has("rotation"))continue;
                float[] v=new float[6];for(int i=0;i<3;i++)v[i]=o.getAsJsonArray("rotation").get(i).getAsFloat();
                map.put(o.get("name").getAsString(),v);
            }
        }catch(Exception ex){
            CelestialFall.LOGGER.error("Gold Experience's model could not be read",ex);
        }
        return rests=map;
    }
}
