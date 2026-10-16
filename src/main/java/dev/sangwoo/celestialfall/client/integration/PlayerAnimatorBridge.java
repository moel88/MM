package dev.sangwoo.celestialfall.client.integration;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

/** Adapts the articulated rig to Player Animator without replacing elbows, knees or root motion.
 * The rig supplies layer 0; authored Player Animator clips go in layer 1000. Root movement
 * remains owned by the skill. No client animation changes server movement or damage.
 */
public final class PlayerAnimatorBridge {
    public static final Identifier BASE=CelestialFall.id("articulated_base");
    public static final Identifier CLIP=CelestialFall.id("authored_clip");
    private static final String[] NAMES={"head","torso","rightArm","leftArm","rightLeg","leftLeg"};
    private static long sampledFrames;
    private PlayerAnimatorBridge(){}
    public static void initialize(){
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(BASE,0,p->new RigLayer());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(CLIP,1000,p->new ModifierLayer<IAnimation>());
    }
    @SuppressWarnings("unchecked")
    public static ModifierLayer<IAnimation> clips(AbstractClientPlayerEntity player){
        return (ModifierLayer<IAnimation>)PlayerAnimationAccess.getPlayerAssociatedData(player).get(CLIP);
    }
    public static long sampledFrames(){return sampledFrames;}
    public static void apply(AbstractClientPlayerEntity player,MotionPlayerModel model,float delta){
        var data=PlayerAnimationAccess.getPlayerAssociatedData(player);
        if(!(data.get(BASE) instanceof RigLayer rig))return;
        ModelPart[] parts={model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg};
        rig.capture(parts);
        var stack=PlayerAnimationAccess.getPlayerAnimLayer(player);
        stack.setupAnim(delta);
        for(int i=0;i<parts.length;i++){
            var part=parts[i];
            var r=stack.get3DTransform(NAMES[i],TransformType.ROTATION,delta,rig.rotation[i]);
            var p=stack.get3DTransform(NAMES[i],TransformType.POSITION,delta,rig.position[i]);
            part.pitch=r.getX();part.yaw=r.getY();part.roll=r.getZ();
            part.pivotX=p.getX();part.pivotY=p.getY();part.pivotZ=p.getZ();
        }
        sampledFrames++;
    }
    /** Only six standard bones; the segmented child joints and world root are not applied twice. */
    private static final class RigLayer implements IAnimation {
        private final Vec3f[] rotation=new Vec3f[6],position=new Vec3f[6];
        private boolean ready;
        void capture(ModelPart[] parts){
            for(int i=0;i<6;i++){
                var p=parts[i];rotation[i]=new Vec3f(p.pitch,p.yaw,p.roll);
                position[i]=new Vec3f(p.pivotX,p.pivotY,p.pivotZ);
            }
            ready=true;
        }
        public boolean isActive(){return ready&&MapClient.enabled();}
        public void setupAnim(float delta){}
        public Vec3f get3DTransform(String name,TransformType type,float delta,Vec3f fallback){
            for(int i=0;i<6;i++)if(NAMES[i].equals(name))return switch(type){
                case ROTATION->rotation[i];case POSITION->position[i];default->fallback;
            };
            return fallback;
        }
    }
}
