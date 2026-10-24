package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.stand.StarPlatinumEntity;
import dev.sangwoo.celestialfall.stand.StarPlatinumStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Star Platinum's GeckoLib model (2.50.0). Each frame every bone takes its stance from the poses file that
 * tools/stand/make_star_platinum.py writes with the model (crossfaded when the stance changes) and its own movement on
 * top of it (StarPlatinumMotion).
 */
public final class StarPlatinumModel extends GeoModel<StarPlatinumEntity> {
    static final Identifier MODEL=CelestialFall.id("geo/entity/star_platinum.geo.json"),
        TEXTURE=CelestialFall.id("textures/entity/star_platinum.png"),
        ANIMATION=CelestialFall.id("animations/entity/star_platinum.animation.json");
    private static final float[] REST=new float[6];
    /** Per stance: each bone's turn (degrees, as Blockbench writes it) and offset (units). */
    private static List<Map<String,float[]>> stances;
    private final Map<String,StarPlatinumMotion> motions=new HashMap<>();
    private final float[] pose=new float[6];

    @Override public Identifier getModelResource(StarPlatinumEntity e){return MODEL;}
    @Override public Identifier getTextureResource(StarPlatinumEntity e){return TEXTURE;}
    @Override public Identifier getAnimationResource(StarPlatinumEntity e){return ANIMATION;}

    @Override public void setCustomAnimations(StarPlatinumEntity e,long id,AnimationState<StarPlatinumEntity> state){
        float delta=state.getPartialTick(),k=e.blend(delta);
        var all=stances();
        Map<String,float[]> from=all.get(e.previousStance()),to=all.get(e.stance());
        float wind=(e.previousStance()==StarPlatinumStance.HERO?1-k:0)+(e.stance()==StarPlatinumStance.HERO?k:0);
        double t=(e.age+delta)/20.0;
        for(CoreGeoBone bone:getAnimationProcessor().getRegisteredBones()){
            var rest=bone.getInitialSnapshot();if(rest==null)continue;
            String name=bone.getName();
            float[] a=from.getOrDefault(name,REST),b=to.getOrDefault(name,REST);
            for(int i=0;i<6;i++)pose[i]=a[i]+(b[i]-a[i])*k;
            motions.computeIfAbsent(name,StarPlatinumMotion::of).add(pose,t,wind);
            // the poses are written the way Blockbench writes a turn; GeckoLib keeps x and y turned the other way
            bone.setRotX(rest.getRotX()-(float)Math.toRadians(pose[0]));
            bone.setRotY(rest.getRotY()-(float)Math.toRadians(pose[1]));
            bone.setRotZ(rest.getRotZ()+(float)Math.toRadians(pose[2]));
            bone.setPosX(pose[3]);bone.setPosY(pose[4]);bone.setPosZ(pose[5]);
        }
    }

    private static List<Map<String,float[]>> stances(){
        if(stances!=null)return stances;
        var list=new ArrayList<Map<String,float[]>>();
        try(var in=StarPlatinumModel.class.getResourceAsStream("/assets/celestialfall/stands/star_platinum_poses.json")){
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
