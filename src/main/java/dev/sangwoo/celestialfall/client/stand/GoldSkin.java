package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.stand.GoldExperienceStance;
import dev.sangwoo.celestialfall.stand.StandHolder;
import dev.sangwoo.celestialfall.stand.StandSkins;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

/**
 * How Gold Experience is drawn in each of its skins (2.156.0, StandSkins): its own look, the original, the box-built
 * figure (tools/stand/make_gold_experience.py: 2.145.0 to 2.154.1, and again since), and "고퀄리티" (hq), the model the
 * user sent (tools/stand/import_gold_experience_hq.py and make_gold_experience_hq.py: its body from 2.155.0 to 2.155.1).
 * Each has its model, texture and stances, where its fists and shoulders are in the stances that strike (its generator
 * writes them), its size, where it leans as it follows, its shadow, the heights of its face, crown and chest that the
 * beatdown's camera looks at, and how far in front of its user its punches stand at most. The stand and its skills go
 * by the skin of whoever's stand it is: its rules, reach and timing are the stand's.
 */
public enum GoldSkin {
    /**
     * The box-built figure: 40.49 units tall drawn at .789, 2.0 blocks (as tall as Star Platinum to the top of its head,
     * 2.146.1 to 2.146.2), its waist at 23 units; the beatdown's camera on its face at 1.72, the top of its head at 1.95,
     * its chest at 1.35; its overdrive and life-giving punches stand at most 2.1 blocks in front.
     */
    ORIGINAL("gold_experience",null,.789f,23f,.45f,1.72,1.95,1.35,.14,2.1f),
    /**
     * The model the user sent: 40.49 units tall drawn at .884, 2.24 blocks (2.155.1, the user's choice, "B 통째로 1.12배":
     * as big as the box-built figure looks), its waist at its hips, 26 units; the beatdown's camera on its face at 2.02,
     * the top of its head at 2.21, its chest at 1.66, the eye its last blow comes into a little further back (its arms are
     * shorter for its size, so it stands nearer); its punches stand at most 2.2 blocks in front for the same reason.
     */
    HQ("gold_experience_hq","stands/gold_experience_hq_mesh.bin",.884f,26f,.5f,2.02,2.21,1.66,.26,2.2f);

    /** The model's height to the top of its helmet (units): both are built to it. */
    public static final float HEIGHT=40.49f;
    /** Its model, texture and stances, and (the model the user sent) its body, which GoldExperienceMesh skins. */
    public final Identifier model,texture;final String poses,fists,mesh;
    /** Its size (the model's units to blocks, sixteen units to a block at 1), and the height it leans about as it follows (blocks above its feet). */
    public final float scale,waist;
    /** Its shadow's radius (blocks). */
    final float shadow;
    /** The beatdown's camera: its face, the top of its head and its chest (blocks up from its feet), and how far behind the eyes of the one struck the lens its last blow comes into sits. */
    final double face,crown,chest,lens;
    /** How far in front of its user it stands at most for the overdrive punch and giving life's punch (blocks). */
    final float far;
    private float[][][] fistsAt,shouldersAt;

    GoldSkin(String name,String mesh,float scale,float waist,float shadow,double face,double crown,double chest,double lens,float far){
        // (the mod's id as a constant: the mod's own class, with all it registers, is not loaded to name a file)
        model=new Identifier(CelestialFall.ID,"geo/entity/"+name+".geo.json");texture=new Identifier(CelestialFall.ID,"textures/entity/"+name+".png");
        poses="/assets/celestialfall/stands/"+name+"_poses.json";fists="/assets/celestialfall/stands/"+name+"_fists.json";this.mesh=mesh;
        this.scale=scale;this.waist=waist/16*scale;this.shadow=shadow;this.face=face;this.crown=crown;this.chest=chest;this.lens=lens;this.far=far;
    }
    /** Where its fists' knuckles are in each stance (blocks ahead, to its right and up from its feet, at its size 1; [stance][side]). */
    public float[][][] fists(){if(fistsAt==null)fistsAt=points("");return fistsAt;}
    /** Where its shoulders' joints are in each stance (the same way). */
    public float[][][] shoulders(){if(shouldersAt==null)shouldersAt=points("_shoulder");return shouldersAt;}
    /** Its number among Gold Experience's skins (StandSkins): what the server tells every client in sight. */
    public int number(){return this==ORIGINAL?0:Math.max(0,StandSkins.number("gold_experience",StandSkins.GOLD_HQ));}
    /** Its height drawn (blocks). */
    public float height(){return HEIGHT/16*scale;}
    /** The path of its geo.json (for reading its bones' own turns). */
    String geo(){return "/assets/"+model.getNamespace()+"/"+model.getPath();}

    /** The skin numbered {@code number} among Gold Experience's (StandSkins): its own look for 0 or any number it has not. */
    public static GoldSkin of(int number){return StandSkins.GOLD_HQ.equals(StandSkins.skin("gold_experience",number))?HQ:ORIGINAL;}
    /** The skin a player's Gold Experience is drawn in, as the server says (their stand's tracked skin); its own look for anyone else. */
    public static GoldSkin of(Entity user){return user instanceof StandHolder holder?of(holder.botand$standSkin()):ORIGINAL;}
    /** The same, for a player known by id (its own look if they are not in this client's world). */
    public static GoldSkin of(UUID user){
        var mc=MinecraftClient.getInstance();
        return mc==null||mc.world==null||user==null?ORIGINAL:of(mc.world.getPlayerByUuid(user));
    }

    /** The knuckles in the stances that strike, or (with {@code "_shoulder"}) the shoulders' joints, from the file its generator writes with its stances. */
    private float[][][] points(String what){
        var names=GoldExperienceStance.NAMES;float[][][] out=new float[names.size()][2][3];float unit=scale/16f;
        try(var in=GoldSkin.class.getResourceAsStream(fists)){
            var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"fists"),StandardCharsets.UTF_8)).getAsJsonObject();
            for(int s=0;s<names.size();s++){
                var stance=root.getAsJsonObject(names.get(s));if(stance==null)continue;
                for(int side=0;side<2;side++){
                    var p=stance.getAsJsonArray((side==0?"left":"right")+what);if(p==null)continue;
                    // model units (front -z, its right -x, up +y) to blocks ahead, to its right and up
                    out[s][side][0]=-p.get(2).getAsFloat()*unit;out[s][side][1]=-p.get(0).getAsFloat()*unit;out[s][side][2]=p.get(1).getAsFloat()*unit;
                }
            }
        }catch(Exception e){
            CelestialFall.LOGGER.error("Gold Experience's fists ("+name()+") could not be read",e);
        }
        return out;
    }
}
