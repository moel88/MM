package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.stand.GoldExperienceEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Gold Experience's GeckoLib model (2.145.0). Each frame every bone takes the pose the figure's stance asks for
 * (crossfaded when it changes) from the poses file that tools/stand/make_gold_experience.py writes with the model, and
 * its own movement on top ({@link GoldExperienceMotion}).
 */
public final class GoldExperienceModel extends GeoModel<GoldExperienceEntity> {
    static final Identifier MODEL=CelestialFall.id("geo/entity/gold_experience.geo.json"),
        TEXTURE=CelestialFall.id("textures/entity/gold_experience.png"),
        ANIMATION=CelestialFall.id("animations/entity/gold_experience.animation.json");
    private final GoldExperiencePoses poses=new GoldExperiencePoses();
    private final float[] pose=new float[6];
    /** The arms' bones: all an afterimage of the rush's arms shows (2.147.0). */
    private static final java.util.Set<String> ARMS=java.util.Set.of("left_arm","left_forearm","left_hand","left_fingers","right_arm","right_forearm","right_hand","right_fingers");
    /**
     * For trying a build before it goes into the generator (the filming tool's "goldfit=BULK/HEAD"): the body drawn
     * BULK times as wide and deep, the head HEAD times its size. Null but while a trial is filmed.
     */
    static float[] tuned;

    @Override public Identifier getModelResource(GoldExperienceEntity e){return MODEL;}
    @Override public Identifier getTextureResource(GoldExperienceEntity e){return TEXTURE;}
    @Override public Identifier getAnimationResource(GoldExperienceEntity e){return ANIMATION;}

    @Override public void setCustomAnimations(GoldExperienceEntity e,long id,AnimationState<GoldExperienceEntity> state){
        float delta=state.getPartialTick(),k=e.blend(delta);
        double t=(e.age+delta)/20.0;
        for(CoreGeoBone bone:getAnimationProcessor().getRegisteredBones()){
            var rest=bone.getInitialSnapshot();if(rest==null)continue;
            var look=e.look(delta);
            // set every frame: the bones are the model's, shared by every body it draws; a hidden bone hides only its own
            // cubes (GeckoLib's setHidden hides the children too: the arms hang from the chest)
            String name=bone.getName();
            bone.setHidden(look!=null&&look.armsOnly&&!ARMS.contains(name));bone.setChildrenHidden(false);
            // (2.146.0) a stand beside its user goes by its look; the figure by its stance
            if(look!=null)poses.blend(bone.getName(),look.weights,t,pose);else poses.pose(bone.getName(),e.previousStance(),e.stance(),k,t,pose);
            // the poses are written the way Blockbench writes a turn; GeckoLib keeps x and y turned the other way
            bone.setRotX(rest.getRotX()-(float)Math.toRadians(pose[0]));
            bone.setRotY(rest.getRotY()-(float)Math.toRadians(pose[1]));
            bone.setRotZ(rest.getRotZ()+(float)Math.toRadians(pose[2]));
            bone.setPosX(pose[3]);bone.setPosY(pose[4]);bone.setPosZ(pose[5]);
            float[] fit=tuned;
            // (2.147.0) the rush's arms, bigger from the shoulder (set every frame too: the next body may have its own size)
            if(name.equals("left_arm")){float a=look==null?1:look.armL;bone.setScaleX(a);bone.setScaleY(a);bone.setScaleZ(a);}
            else if(name.equals("right_arm")){float a=look==null?1:look.armR;bone.setScaleX(a);bone.setScaleY(a);bone.setScaleZ(a);}
            if("root".equals(bone.getName())){float w=fit==null?1:fit[0];bone.setScaleX(w);bone.setScaleY(1);bone.setScaleZ(w);}
            else if("head".equals(bone.getName())){float w=fit==null?1:fit[1]/fit[0],h=fit==null?1:fit[1];bone.setScaleX(w);bone.setScaleY(h);bone.setScaleZ(w);}
        }
    }
}
