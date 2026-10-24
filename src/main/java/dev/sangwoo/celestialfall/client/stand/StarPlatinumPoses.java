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
 * a bone's pose in a frame: the stances weighed as the look says, its own movement (StarPlatinumMotion) on top. The
 * stances' turns are weighed as turns (2.54.0): each bone's whole turn in each stance (its own turn in the model and the
 * stance's on top) as a quaternion, the weighted ones added and normalised, so a blend goes the short way from one
 * stance to the next. Weighing the angles themselves went wrong for the punches' limbs, whose turns written as angles
 * can lie far apart for poses close together (a forearm twisted a quarter turn reads as nearly opposite angles).
 */
final class StarPlatinumPoses {
    private static final float[] REST=new float[6];
    /** Per stance: each bone's turn (degrees, as Blockbench writes it) and offset (units). */
    private static List<Map<String,float[]>> stances;
    /** Each bone's own turn in the model (degrees, Blockbench's way): a stance's turn is added to it. */
    private static Map<String,float[]> rests;
    /** Per stance: each bone's whole turn (its own and the stance's) as a quaternion {w, x, y, z}. */
    private static List<Map<String,double[]>> turns;
    private final Map<String,StarPlatinumMotion> motions=new HashMap<>();
    private final double[] q=new double[4],e=new double[3];

    /** The bone's turn x, y, z (degrees, Blockbench's way) and offset x, y, z (units) at {@code seconds} on the clock. */
    void pose(String bone,StarPlatinumLook look,double seconds,float[] out){
        Arrays.fill(out,0);
        var all=stances();
        int top=-1,count=0;float best=0;
        for(int s=0;s<look.weights.length&&s<all.size();s++){
            float w=look.weights[s];if(w==0)continue;
            float[] v=all.get(s).getOrDefault(bone,REST);for(int i=0;i<6;i++)out[i]+=w*v[i];
            if(Math.abs(w)>1e-6){count++;if(w>best){best=w;top=s;}}
        }
        if(count>1&&top>=0)blend(bone,look.weights,top,out);
        motions.computeIfAbsent(bone,StarPlatinumMotion::of).add(out,seconds,look);
    }
    /**
     * The blended turn, weighed as turns: the stances taken in, strongest first, each turned towards by its share of the
     * weight taken so far along the shortest way (so two stances blend at an even speed of turning), written back as
     * angles on the branch nearest the angles weighed directly (a blend that is mostly one stance reads as that stance's
     * angles), less the bone's own turn.
     */
    private void blend(String bone,float[] weights,int top,float[] out){
        var rest=rests().getOrDefault(bone,REST);var quats=turns();
        double[] own=null;
        double[] first=quats.get(top).get(bone);if(first==null)first=own=quat(rest[0],rest[1],rest[2],new double[4]);
        System.arraycopy(first,0,q,0,4);double taken=weights[top];
        for(int s=0;s<weights.length&&s<quats.size();s++){
            float w=weights[s];if(s==top||w<=0)continue;
            double[] t=quats.get(s).get(bone);if(t==null)t=own!=null?own:(own=quat(rest[0],rest[1],rest[2],new double[4]));
            taken+=w;slerp(q,t,w/taken);
        }
        euler(q,e);
        // the branch nearest the angles weighed directly (out holds them, less the bone's own turn)
        double[] near={out[0]+rest[0],out[1]+rest[1],out[2]+rest[2]};
        double[] other={e[0]+180,180-e[1],e[2]+180};
        double[] pick=distance(e,near)<=distance(other,near)?e:other;
        for(int i=0;i<3;i++)out[i]=(float)(near[i]+wrap(pick[i]-near[i])-rest[i]);
    }
    /** Turns {@code q} the share {@code k} of the shortest way to {@code to} (both unit quaternions). */
    static void slerp(double[] q,double[] to,double k){
        double dot=q[0]*to[0]+q[1]*to[1]+q[2]*to[2]+q[3]*to[3],sign=1;
        if(dot<0){dot=-dot;sign=-1;}
        double a,b;
        if(dot>.9995){a=1-k;b=k;}
        else{double th=Math.acos(dot),sn=Math.sin(th);a=Math.sin((1-k)*th)/sn;b=Math.sin(k*th)/sn;}
        double n=0;for(int i=0;i<4;i++){q[i]=a*q[i]+b*sign*to[i];n+=q[i]*q[i];}
        n=Math.sqrt(n);for(int i=0;i<4;i++)q[i]/=n;
    }
    static double wrap(double d){d%=360;if(d>180)d-=360;if(d<-180)d+=360;return d;}
    static double distance(double[] a,double[] b){double s=0;for(int i=0;i<3;i++){double d=wrap(a[i]-b[i]);s+=d*d;}return s;}
    /**
     * A turn written as Blockbench writes it (degrees x, y, z) as a quaternion: the model's turn is Rz(-z) Ry(y) Rx(-x),
     * as tools/stand/make_star_platinum.py and GeckoLib apply it.
     */
    static double[] quat(double x,double y,double z,double[] q){
        double a=Math.toRadians(-z)/2,b=Math.toRadians(y)/2,c=Math.toRadians(-x)/2;
        double ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b),cc=Math.cos(c),sc=Math.sin(c);
        // qz(a) qy(b) qx(c)
        q[0]=ca*cb*cc+sa*sb*sc;
        q[1]=ca*cb*sc-sa*sb*cc;
        q[2]=ca*sb*cc+sa*cb*sc;
        q[3]=sa*cb*cc-ca*sb*sc;
        return q;
    }
    /** The angles (degrees, Blockbench's way) of a quaternion's turn, the y one within ±90 (as the generator's euler_of). */
    static double[] euler(double[] q,double[] e){
        double w=q[0],x=q[1],y=q[2],z=q[3];
        double r00=1-2*(y*y+z*z),r10=2*(x*y+w*z),r20=2*(x*z-w*y),r21=2*(y*z+w*x),r22=1-2*(x*x+y*y);
        double b=Math.asin(Math.max(-1,Math.min(1,-r20))),a=Math.atan2(r10,r00),c=Math.atan2(r21,r22);
        e[0]=-Math.toDegrees(c);e[1]=Math.toDegrees(b);e[2]=-Math.toDegrees(a);
        return e;
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
    static Map<String,float[]> rests(){
        if(rests!=null)return rests;
        var map=new HashMap<String,float[]>();
        try(var in=StarPlatinumPoses.class.getResourceAsStream("/assets/celestialfall/geo/entity/star_platinum.geo.json")){
            var bones=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"model"),StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            for(var b:bones){
                var o=b.getAsJsonObject();if(!o.has("rotation"))continue;
                float[] v=new float[6];for(int i=0;i<3;i++)v[i]=o.getAsJsonArray("rotation").get(i).getAsFloat();
                map.put(o.get("name").getAsString(),v);
            }
        }catch(Exception ex){
            CelestialFall.LOGGER.error("Star Platinum's model could not be read",ex);
        }
        return rests=map;
    }
    static List<Map<String,double[]>> turns(){
        if(turns!=null)return turns;
        var list=new ArrayList<Map<String,double[]>>();
        for(var stance:stances()){
            var map=new HashMap<String,double[]>();
            for(var entry:stance.entrySet()){
                var rest=rests().getOrDefault(entry.getKey(),REST);var v=entry.getValue();
                map.put(entry.getKey(),quat(rest[0]+v[0],rest[1]+v[1],rest[2]+v[2],new double[4]));
            }
            list.add(map);
        }
        return turns=list;
    }
}
