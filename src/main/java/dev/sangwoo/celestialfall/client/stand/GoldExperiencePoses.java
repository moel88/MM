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
 * whose arithmetic of turns this uses), and its own movement ({@link GoldExperienceMotion}) comes on top. (2.156.0) Each
 * skin has its own model and stances ({@link GoldSkin}): one of these goes by one skin's.
 */
final class GoldExperiencePoses {
    private static final float[] REST=new float[6];
    /** Per skin, per stance: each bone's turn (degrees, as Blockbench writes it) and offset (units). */
    private static final Map<GoldSkin,List<Map<String,float[]>>> STANCES=new java.util.EnumMap<>(GoldSkin.class);
    /** Per skin: each bone's own turn in the model (degrees, Blockbench's way): a stance's turn is added to it. */
    private static final Map<GoldSkin,Map<String,float[]>> RESTS=new java.util.EnumMap<>(GoldSkin.class);
    private final GoldSkin skin;
    private final Map<String,GoldExperienceMotion> motions=new HashMap<>();
    private final double[] qa=new double[4],qb=new double[4],e=new double[3];
    /** Its own look's stances. */
    GoldExperiencePoses(){this(GoldSkin.ORIGINAL);}
    GoldExperiencePoses(GoldSkin skin){this.skin=skin;}

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
    /**
     * (2.146.0) The bone's pose for a stand's look: its stances weighed together (each turn taken the short way to the
     * next, in the order of the stances), and the bone's own movement on top, stilled as it stands curled up or straight.
     */
    void blend(String bone,float[] weights,double seconds,float[] out){
        var all=stances(skin);var rest=rests(skin).getOrDefault(bone,REST);
        float acc=0;int held=0;
        for(int i=0;i<weights.length&&i<all.size();i++){
            float w=weights[i];if(w<=1e-4f)continue;
            float[] a=all.get(i).getOrDefault(bone,REST);held++;
            if(acc==0){System.arraycopy(a,0,out,0,6);StarPlatinumPoses.quat(rest[0]+a[0],rest[1]+a[1],rest[2]+a[2],qa);acc=w;continue;}
            float k=w/(acc+w);
            for(int j=0;j<6;j++)out[j]+=(a[j]-out[j])*k;
            StarPlatinumPoses.quat(rest[0]+a[0],rest[1]+a[1],rest[2]+a[2],qb);StarPlatinumPoses.slerp(qa,qb,k);acc+=w;
        }
        if(held==0)System.arraycopy(all.get(GoldExperienceStance.IDLE).getOrDefault(bone,REST),0,out,0,6);
        else if(held>1){
            StarPlatinumPoses.euler(qa,e);
            double[] near={out[0]+rest[0],out[1]+rest[1],out[2]+rest[2]},other={e[0]+180,180-e[1],e[2]+180};
            double[] pick=StarPlatinumPoses.distance(e,near)<=StarPlatinumPoses.distance(other,near)?e:other;
            for(int i=0;i<3;i++)out[i]=(float)(near[i]+StarPlatinumPoses.wrap(pick[i]-near[i])-rest[i]);
        }
        // (2.152.0) down on its knee with its palm on the ground, it does not float either
        float calm=1-weights[GoldExperienceStance.STAND]-.75f*weights[GoldExperienceStance.SUMMON]-(weights.length>GoldExperienceStance.GROUND?weights[GoldExperienceStance.GROUND]:0);
        motions.computeIfAbsent(bone,GoldExperienceMotion::of).add(out,seconds,Math.max(0,Math.min(1,calm)));
    }
    /** The stances alone, without the bone's own movement. */
    void stance(String bone,int from,int to,float k,float[] out){
        var all=stances(skin);
        float[] a=all.get(Math.floorMod(from,all.size())).getOrDefault(bone,REST),b=all.get(Math.floorMod(to,all.size())).getOrDefault(bone,REST);
        if(from==to||k>=1){System.arraycopy(b,0,out,0,6);return;}
        if(k<=0){System.arraycopy(a,0,out,0,6);return;}
        for(int i=0;i<6;i++)out[i]=a[i]+(b[i]-a[i])*k;
        var rest=rests(skin).getOrDefault(bone,REST);
        StarPlatinumPoses.quat(rest[0]+a[0],rest[1]+a[1],rest[2]+a[2],qa);StarPlatinumPoses.quat(rest[0]+b[0],rest[1]+b[1],rest[2]+b[2],qb);
        StarPlatinumPoses.slerp(qa,qb,k);StarPlatinumPoses.euler(qa,e);
        // written as angles on the branch nearest the angles blended directly, less the bone's own turn
        double[] near={out[0]+rest[0],out[1]+rest[1],out[2]+rest[2]},other={e[0]+180,180-e[1],e[2]+180};
        double[] pick=StarPlatinumPoses.distance(e,near)<=StarPlatinumPoses.distance(other,near)?e:other;
        for(int i=0;i<3;i++)out[i]=(float)(near[i]+StarPlatinumPoses.wrap(pick[i]-near[i])-rest[i]);
    }
    /** A skin's stances (its own look's: {@link #stances()}). */
    static List<Map<String,float[]>> stances(){return stances(GoldSkin.ORIGINAL);}
    static List<Map<String,float[]>> stances(GoldSkin skin){
        var known=STANCES.get(skin);if(known!=null)return known;
        var list=new ArrayList<Map<String,float[]>>();
        try(var in=GoldExperiencePoses.class.getResourceAsStream(skin.poses)){
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
            CelestialFall.LOGGER.error("Gold Experience's poses ("+skin+") could not be read",ex);
            list.clear();for(int i=0;i<GoldExperienceStance.NAMES.size();i++)list.add(Map.of());
        }
        STANCES.put(skin,list);return list;
    }
    static Map<String,float[]> rests(GoldSkin skin){
        var known=RESTS.get(skin);if(known!=null)return known;
        var map=new HashMap<String,float[]>();
        try(var in=GoldExperiencePoses.class.getResourceAsStream(skin.geo())){
            var bones=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"model"),StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            for(var b:bones){
                var o=b.getAsJsonObject();if(!o.has("rotation"))continue;
                float[] v=new float[6];for(int i=0;i<3;i++)v[i]=o.getAsJsonArray("rotation").get(i).getAsFloat();
                map.put(o.get("name").getAsString(),v);
            }
        }catch(Exception ex){
            CelestialFall.LOGGER.error("Gold Experience's model ("+skin+") could not be read",ex);
        }
        RESTS.put(skin,map);return map;
    }
}
