package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.stand.StarPlatinumEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Star Platinum's GeckoLib model (2.50.0). Each frame every bone takes the pose its body's look asks for (the figure's
 * stance, crossfaded when it changes; a stand's coming out, floating and going back, StandTimeline) from the poses file
 * that tools/stand/make_star_platinum.py writes with the model, and its own movement on top (StarPlatinumMotion). For an
 * afterimage of the rush's flying arms (2.57.0) every bone but the arms' is hidden (their children still follow them).
 */
public final class StarPlatinumModel extends GeoModel<StarPlatinumEntity> {
    static final Identifier MODEL=CelestialFall.id("geo/entity/star_platinum.geo.json"),
        TEXTURE=CelestialFall.id("textures/entity/star_platinum.png"),
        ANIMATION=CelestialFall.id("animations/entity/star_platinum.animation.json");
    private final StarPlatinumPoses poses=new StarPlatinumPoses();
    private final float[] pose=new float[6];
    /** The arms' bones, all that an afterimage of the rush's flying arms shows. */
    static final java.util.Set<String> ARMS=java.util.Set.of("left_arm","left_forearm","left_hand","right_arm","right_forearm","right_hand");

    @Override public Identifier getModelResource(StarPlatinumEntity e){return MODEL;}
    @Override public Identifier getTextureResource(StarPlatinumEntity e){return TEXTURE;}
    @Override public Identifier getAnimationResource(StarPlatinumEntity e){return ANIMATION;}

    @Override public void setCustomAnimations(StarPlatinumEntity e,long id,AnimationState<StarPlatinumEntity> state){
        float delta=state.getPartialTick();
        var look=e.look(delta);
        double t=(e.age+delta)/20.0;
        for(CoreGeoBone bone:getAnimationProcessor().getRegisteredBones()){
            var rest=bone.getInitialSnapshot();if(rest==null)continue;
            // set every frame: the bones are the model's, shared by every body it draws; a hidden bone hides only its own
            // cubes (GeckoLib's setHidden hides the children too: the arms hang from the chest)
            bone.setHidden(look.armsOnly&&!ARMS.contains(bone.getName()));bone.setChildrenHidden(false);
            poses.pose(bone.getName(),look,t,pose);
            // the poses are written the way Blockbench writes a turn; GeckoLib keeps x and y turned the other way
            bone.setRotX(rest.getRotX()-(float)Math.toRadians(pose[0]));
            bone.setRotY(rest.getRotY()-(float)Math.toRadians(pose[1]));
            bone.setRotZ(rest.getRotZ()+(float)Math.toRadians(pose[2]));
            bone.setPosX(pose[3]);bone.setPosY(pose[4]);bone.setPosZ(pose[5]);
        }
    }
}
