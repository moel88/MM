package dev.sangwoo.celestialfall.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.*;
import dev.sangwoo.celestialfall.combat.MotionPose;

/** Skin-compatible segmented limbs, with matching jacket/sleeve/pants geometry. */
public final class MotionRig {
    private MotionRig() {}
    public static ModelPart player(boolean slim) {
        ModelData data=PlayerEntityModel.getTexturedModelData(Dilation.NONE,slim);var r=data.getRoot();
        limbs(r,Dilation.NONE,slim,false);
        segment(r,"right_sleeve",40,32,-5,2,slim?3:4,slim?-2:-3,-2,4,"elbow",new Dilation(.25f),false);
        segment(r,"left_sleeve",48,48,5,2,slim?3:4,-1,-2,4,"elbow",new Dilation(.25f),false);
        segment(r,"right_pants",0,32,-1.9f,12,4,-2,0,6,"knee",new Dilation(.25f),false);
        segment(r,"left_pants",0,48,1.9f,12,4,-2,0,6,"knee",new Dilation(.25f),false);
        var root=TexturedModelData.of(data,64,64).createModel();fixCaps(root,64,true);return root;
    }
    public static ModelPart armor(Dilation dilation) {
        ModelData data=BipedEntityModel.getModelData(dilation,0);limbs(data.getRoot(),dilation,false,true);
        var root=TexturedModelData.of(data,64,32).createModel();fixCaps(root,32,false);return root;
    }
    private static void fixCaps(ModelPart root,int textureHeight,boolean overlays) {
        java.util.List<String> parts=new java.util.ArrayList<>(java.util.List.of("right_arm","left_arm","right_leg","left_leg"));
        if(overlays)parts.addAll(java.util.List.of("right_sleeve","left_sleeve","right_pants","left_pants"));
        for(String name:parts) {
            var part=root.getChild(name);var lower=part.getChild(part.hasChild("elbow")?"elbow":"knee");
            lower.forEachCuboid(new net.minecraft.client.util.math.MatrixStack(),(entry,path,index,cube)->{
                for(var face:cube.sides)if(face.direction.y>0)for(int i=0;i<face.vertices.length;i++) {
                    var vertex=face.vertices[i];face.vertices[i]=vertex.remap(vertex.u,vertex.v-6f/textureHeight);
                }
                else if(face.direction.y<0) {
                    // The exposed elbow/knee cap samples the cut-line skin rather than shoulder/boot pixels.
                    float u=face.vertices[0].u+.5f/64,v=face.vertices[0].v+4.5f/textureHeight;
                    for(int i=0;i<face.vertices.length;i++)face.vertices[i]=face.vertices[i].remap(u,v);
                }
            });
        }
    }
    private static void limbs(ModelPartData r,Dilation d,boolean slim,boolean armor) {
        segment(r,"right_arm",40,16,-5,2,slim?3:4,slim?-2:-3,-2,4,"elbow",d,false);
        segment(r,"left_arm",armor?40:32,armor?16:48,5,2,slim?3:4,-1,-2,4,"elbow",d,armor);
        segment(r,"right_leg",0,16,-1.9f,12,4,-2,0,6,"knee",d,false);
        segment(r,"left_leg",armor?0:16,armor?16:48,1.9f,12,4,-2,0,6,"knee",d,armor);
    }
    private static void segment(ModelPartData root,String name,int u,int v,float px,float py,int width,float x,float y,float hinge,String child,Dilation d,boolean mirror) {
        var top=root.addChild(name,ModelPartBuilder.create().uv(u,v).mirrored(mirror).cuboid(x,y,-2,width,6,4,d),ModelTransform.pivot(px,py,0));
        top.addChild(child,ModelPartBuilder.create().uv(u,v+6).mirrored(mirror).cuboid(x,0,-2,width,6,4,d),ModelTransform.pivot(0,hinge,0));
    }
    private static void rotate(ModelPart p,float[] r) {p.pitch=rad(r[0]);p.yaw=rad(r[1]);p.roll=rad(r[2]);}
    public static float rad(float value) {return value*(float)Math.PI/180;}
    public static void apply(BipedEntityModel<?> model,MotionPose pose,float headYaw,float headPitch) {
        ModelPart[] parts={model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg};
        for(int i=0;i<6;i++)rotate(parts[i],pose.joint[i]);
        model.head.yaw+=rad(headYaw);model.head.pitch+=rad(headPitch);
        var torso=new org.joml.Quaternionf().rotationZYX(model.body.roll,model.body.yaw,model.body.pitch);
        var hip=new org.joml.Vector3f(0,12,0).rotate(torso);
        model.body.pivotY=12+pose.bob-hip.y;model.body.pivotX=pose.shift-hip.x;model.body.pivotZ=pose.lean-hip.z;
        // Anchor the waist to the pelvis; the neck and both shoulder sockets follow the same rigid torso.
        for(int i=0;i<2;i++) {
            ModelPart arm=i==0?model.rightArm:model.leftArm;float x=i==0?-5:5;
            var socket=new org.joml.Vector3f(x,2,0).rotate(torso);
            arm.pivotX=model.body.pivotX+socket.x;arm.pivotZ=model.body.pivotZ+socket.z;
            arm.pivotY=model.body.pivotY+socket.y;
            var q=new org.joml.Quaternionf(torso).mul(new org.joml.Quaternionf().rotationZYX(arm.roll,arm.yaw,arm.pitch));
            // Explicit Rz*Ry*Rx decomposition preserves authored elbow/shoulder contact angles.
            arm.pitch=(float)Math.atan2(2*(q.w*q.x+q.y*q.z),1-2*(q.x*q.x+q.y*q.y));
            arm.yaw=(float)Math.asin(Math.max(-1,Math.min(1,2*(q.w*q.y-q.z*q.x))));
            arm.roll=(float)Math.atan2(2*(q.w*q.z+q.x*q.y),1-2*(q.y*q.y+q.z*q.z));
            arm.getChild("elbow").pitch=rad(pose.elbow[i]);
            ModelPart leg=i==0?model.rightLeg:model.leftLeg;
            leg.pivotY=12+pose.bob;leg.pivotX=(i==0?-1.9f:1.9f)+pose.shift;leg.pivotZ=pose.lean;
            leg.getChild("knee").pitch=rad(pose.knee[i]);
        }
        model.head.pivotX=model.body.pivotX;model.head.pivotY=model.body.pivotY;model.head.pivotZ=model.body.pivotZ;
        model.hat.copyTransform(model.head);
    }
    public static void reset(BipedEntityModel<?> m) {
        for(var part:new ModelPart[]{m.head,m.hat,m.body,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg})part.resetTransform();
        for(var part:new ModelPart[]{m.rightArm,m.leftArm})part.getChild("elbow").resetTransform();
        for(var part:new ModelPart[]{m.rightLeg,m.leftLeg})part.getChild("knee").resetTransform();
    }
}
