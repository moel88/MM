package dev.sangwoo.celestialfall.client;

import net.minecraft.client.model.*;

/** Articulated rig (separate elbows, knees and coat panels) used by Heaven's stand-in actor. */
final class AtomicActor {
    private AtomicActor() {}
    private static ModelPartBuilder cube(int u,int v,float x,float y,float z,float w,float h,float d) {
        return ModelPartBuilder.create().uv(u,v).cuboid(x,y,z,w,h,d);
    }
    static ModelPart createRig() {
        ModelData d=new ModelData();var r=d.getRoot();
        var b=r.addChild("body",cube(16,16,-4,0,-2,8,12,4),ModelTransform.NONE);
        b.addChild("head",ModelPartBuilder.create(),ModelTransform.NONE);
        for(int side:new int[]{-1,1}) {
            String name=side<0?"right":"left";
            var a=b.addChild(name+"Arm",cube(side<0?40:32,side<0?16:48,-1.6f,-1,-2,3.2f,7,4),ModelTransform.pivot(side*5,2,0));
            a.addChild("forearm",cube(side<0?40:32,side<0?22:54,-1.6f,0,-2,3.2f,6,4),ModelTransform.pivot(0,6,0));
            var leg=r.addChild(name+"Leg",cube(side<0?0:16,side<0?16:48,-2,0,-2,4,6,4),ModelTransform.pivot(side*2,12,0));
            leg.addChild("shin",cube(side<0?0:16,side<0?22:54,-2,0,-2,4,6,4),ModelTransform.pivot(0,6,0));
            var cape=b.addChild(name+"Cape",cube(16,16,-2,0,0,4,11,1),ModelTransform.pivot(side*3,10,2));
            cape.addChild("tail",cube(16,20,-2,0,0,4,11,1),ModelTransform.pivot(0,10,0));
        }
        return TexturedModelData.of(d,64,64).createModel();
    }
    static ModelPart createFace() {
        ModelData d=new ModelData();d.getRoot().addChild("face",cube(0,0,-4,-8,-4,8,8,8),ModelTransform.NONE);
        return TexturedModelData.of(d,64,64).createModel();
    }
}
