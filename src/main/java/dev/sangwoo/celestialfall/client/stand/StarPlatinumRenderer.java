package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinum;
import dev.sangwoo.celestialfall.stand.StarPlatinumEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws Star Platinum with its GeckoLib model (2.50.0): the figure, and a stand beside its user (2.51.0) at the size,
 * solidity and colour of its look; while it fades in or out, and for its afterimages, see-through. A stand's aura is
 * drawn round it on the screen (StandAuraLayer, 2.53.0). Where that cannot be drawn (a shader pack, or post-processing
 * off) the stand glows as in 2.52.0 (user request: "스타플래티나를 조금 빛나게하고"): the same body drawn twice more over it,
 * a little larger each time, in light that adds to what is behind it and is not shaded by the world, a faint pale
 * violet blue sheen on the body and a soft rim round its outline, breathing with it and flaring as it bursts out.
 */
public final class StarPlatinumRenderer extends GeoEntityRenderer<StarPlatinumEntity> {
    /**
     * The model stands 41 units (2.6 blocks) to the top of its head; at this size it stands about half a head over a
     * player (2.52.0, user request: "사이즈도 조금만 줄이고": 0.8 until 2.51.0).
     */
    public static final float SCALE=.72f;
    /** The glow's colour: a pale violet blue, like the body's own. */
    static final float AURA_R=.55f,AURA_G=.64f,AURA_B=1f;
    /** The glow's shells: how much larger than the body each is drawn, and how bright. */
    static final float[][] SHELLS={{1.02f,.1f},{1.05f,.08f}};
    /** The height (blocks above its feet) of the middle the shells grow from: its waist. */
    static final float WAIST=24f/16*SCALE;
    /** The brightness of the shell being drawn, or -1 while the body itself is. */
    private float shell=-1;
    /**
     * The Star Finger's fingers as last drawn on each user's stand this frame (2.60.0): where they leave the fist, where
     * their tips are, and the frame they were drawn in; for their light (StandPunchFx). Only the body itself tells
     * ({@link #record}), not its afterimages.
     */
    public record Finger(net.minecraft.util.math.Vec3d root,net.minecraft.util.math.Vec3d tip,long frame){}
    private static final java.util.Map<java.util.UUID,Finger> FINGERS=new java.util.HashMap<>();
    private static java.util.UUID recording;
    private static long frames;
    /** The natural length of the fingers (model units, tools/stand/make_star_platinum.py FINGER_LEN), stretched from there. */
    static final float FINGER_LEN=3f;
    /** A new frame: the fingers drawn before it are no longer where they are (StandPresence, before it draws any stand). */
    public static void frame(){frames++;}
    /** The body about to be drawn is {@code owner}'s stand itself (null: an afterimage, or none): its fingers are told. */
    public static void record(java.util.UUID owner){recording=owner;}
    /** Where {@code owner}'s stand's fingers were drawn this frame (null if they were not). */
    public static Finger finger(java.util.UUID owner){var f=FINGERS.get(owner);return f==null||f.frame!=frames?null:f;}
    public StarPlatinumRenderer(EntityRendererFactory.Context context){super(context,new StarPlatinumModel());withScale(SCALE);shadowRadius=.45f;}
    public static void initialize(){EntityRendererRegistry.register(StarPlatinum.TYPE,StarPlatinumRenderer::new);}
    @Override public void render(StarPlatinumEntity e,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light){
        var look=e.look(delta);
        // drawn after this: the shadow, as dark as the body is solid
        shadowOpacity=look.alpha*look.shadow;
        matrices.push();matrices.scale(look.scale,look.scale,look.scale);
        super.render(e,yaw,delta,matrices,buffers,light);
        if(look.glow>0&&!StandAuraLayer.available()){
            // the shells keep the body's pose: GeckoLib does not pose the same body twice in a frame
            float breath=1+.12f*(float)Math.sin(2*Math.PI*(e.age+delta)/20/StarPlatinumMotion.BREATH);
            for(float[] s:SHELLS){
                shell=look.glow*s[1]*breath;
                matrices.push();matrices.translate(0,WAIST,0);matrices.scale(s[0],s[0],s[0]);matrices.translate(0,-WAIST,0);
                super.render(e,yaw,delta,matrices,buffers,light);
                matrices.pop();
            }
            shell=-1;
        }
        matrices.pop();
    }
    /**
     * An afterimage of the rush's arms (2.58.0) draws each arm, from its shoulder down, as strongly as its look shows it:
     * the arm striking out, not the one drawn back, so the afterimages are a storm of fists rather than a wall of arms.
     */
    @Override public void renderRecursively(MatrixStack matrices,StarPlatinumEntity e,GeoBone bone,RenderLayer layer,VertexConsumerProvider buffers,net.minecraft.client.render.VertexConsumer buffer,boolean reRender,float delta,int light,int overlay,float red,float green,float blue,float alpha){
        String name=bone.getName();
        if(name.equals("left_arm")||name.equals("right_arm")){var look=e.look(delta);if(look.armsOnly)alpha*=name.equals("left_arm")?look.showL:look.showR;}
        if(name.equals("right_finger")&&!bone.isHidden())aim(matrices,bone,e.look(delta));
        super.renderRecursively(matrices,e,bone,layer,buffers,buffer,reRender,delta,light,overlay,red,green,blue,alpha);
    }
    /**
     * The Star Finger's fingers (2.60.0), turned and stretched from where they leave the fist to where they go: the hand
     * as it is drawn now (every bone above it, and the view's turn under them, is on the stack) gives where they
     * leave the fist and which way is which; held out straight along the hand while they are open and not stretched,
     * turned to their point as they stretch out to it (all the way as they are out), their length from theirs to the
     * point and a little thicker.
     */
    private static void aim(MatrixStack matrices,GeoBone bone,dev.sangwoo.celestialfall.stand.StarPlatinumLook look){
        float open=Math.max(0,Math.min(1,look.fingerOpen)),ext=look.hasTip?Math.max(0,Math.min(1,look.fingerExt)):0;
        bone.setRotX(0);bone.setRotY(0);bone.setRotZ(0);bone.setScaleX(1);bone.setScaleZ(1);bone.setScaleY(Math.max(.05f,open));
        var m=new org.joml.Matrix4f(matrices.peek().getPositionMatrix());
        var root=m.transformPosition(new org.joml.Vector3f(bone.getPivotX()/16f-bone.getPosX()/16f,(bone.getPivotY()+bone.getPosY())/16f,(bone.getPivotZ()+bone.getPosZ())/16f));
        // the hand's turn and size here: how long a finger of the model is in the world
        var hand=new org.joml.Matrix3f();m.get3x3(hand);
        float unit=hand.transform(new org.joml.Vector3f(0,1,0)).length();if(unit<1e-6f)return;
        float full=FINGER_LEN/16f*unit,length=full*open;
        var d=new org.joml.Vector3f(0,-1,0);
        var cam=net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        if(ext>0){
            // the point, in the view's frame (the stack's base turns the world to the view), and the way to it in the
            // hand's own frame
            var view=new org.joml.Matrix3f(com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix()).invert();
            var to=view.transform(new org.joml.Vector3f((float)(look.fingerX-cam.x),(float)(look.fingerY-cam.y),(float)(look.fingerZ-cam.z))).sub(root);
            float distance=to.length();
            if(distance>1e-4f){
                var way=new org.joml.Matrix3f(hand).invert().transform(to).normalize();
                // turned to it as they stretch out (at once: all the way a third of the way out)
                float w=Math.min(1,ext*3);w=w*w*(3-2*w);
                d.lerp(way,w);if(d.lengthSquared()<1e-8f)d.set(way);d.normalize();
                // a turn about x then y sends (0, -1, 0) along d: (-sin a sin b, -cos a, -sin a cos b)
                float a=(float)Math.acos(Math.max(-1,Math.min(1,-d.y))),b=(float)Math.atan2(-d.x,-d.z);
                bone.setRotX(a);bone.setRotY(a<1e-4f?0:b);
                length+=(distance-length)*ext;
                bone.setScaleY(Math.max(.05f,length/full));
                float thick=1+.25f*ext;bone.setScaleX(thick);bone.setScaleZ(thick);
            }
        }
        if(recording!=null){
            // where they are in the world: back from the view's frame
            var back=com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix();
            var r=back.transform(new org.joml.Vector3f(root));
            var at=new net.minecraft.util.math.Vec3d(cam.x+r.x,cam.y+r.y,cam.z+r.z);
            var tv=back.transform(hand.transform(new org.joml.Vector3f(d)).normalize().mul(length));
            FINGERS.put(recording,new Finger(at,at.add(tv.x,tv.y,tv.z),frames));
        }
    }

    @Override public RenderLayer getRenderType(StarPlatinumEntity e,Identifier texture,VertexConsumerProvider buffers,float delta){
        if(shell>=0)return RenderLayer.getEyes(texture);
        return e.look(delta).alpha<.999f?RenderLayer.getEntityTranslucent(texture):super.getRenderType(e,texture,buffers,delta);
    }
    @Override public Color getRenderColor(StarPlatinumEntity e,float delta,int light){
        if(shell>=0)return Color.ofRGBA(AURA_R*shell,AURA_G*shell,AURA_B*shell,1);
        var look=e.look(delta);return Color.ofRGBA(look.red,look.green,look.blue,look.alpha);
    }
}
