package dev.sangwoo.celestialfall.client.stand;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/**
 * The fly's model (2.151.0, T: GoldFlyEntity): a housefly of a few boxes, its skin from tools/stand/make_gold_fly.py
 * (textures/entity/gold_fly.png, 32 square). Model units (sixteenths of a block before its renderer's scale), the
 * entity models' way: y down, its head toward -z. Its middle is the thorax's.
 * <ul>
 * <li>The thorax, the head before it (its face grey) with a big dark-red eye bulging out of each side, the abdomen
 * behind it tipped a little down; six thin black legs under the thorax, splayed out, the hind pair back.</li>
 * <li>Two clear wings from the top of the thorax, swept back (drawn apart, see-through, by its renderer, which beats
 * them).</li>
 * </ul>
 */
final class GoldFlyModel {
    final ModelPart root,thorax,rightWing,leftWing;
    /** The legs: {x, z, splay (degrees, out), rake (degrees, back)} for each. */
    private static final float[][] LEGS={{-1.1f,-1.0f,38,-22},{1.1f,-1.0f,-38,-22},{-1.2f,0f,44,6},{1.2f,0f,-44,6},{-1.1f,1.0f,40,34},{1.1f,1.0f,-40,34}};
    GoldFlyModel(){
        var data=new ModelData();var r=data.getRoot();
        var body=r.addChild("thorax",ModelPartBuilder.create().uv(0,0).cuboid(-1.5f,-1.5f,-1.5f,3,3,3),ModelTransform.NONE);
        body.addChild("head",ModelPartBuilder.create().uv(0,6).cuboid(-1,-1,-2,2,2,2).uv(8,6).cuboid(-1.65f,-1.1f,-1.9f,1,2,2)
            .uv(8,6).mirrored().cuboid(.65f,-1.1f,-1.9f,1,2,2),ModelTransform.pivot(0,-.2f,-1.5f));
        body.addChild("abdomen",ModelPartBuilder.create().uv(0,10).cuboid(-1.5f,-1,0,3,2,4),ModelTransform.of(0,.2f,1.4f,(float)Math.toRadians(-12),0,0));
        body.addChild("right_wing",ModelPartBuilder.create().uv(0,16).cuboid(-5,0,0,5,0,3),ModelTransform.pivot(-.5f,-1.55f,-.7f));
        body.addChild("left_wing",ModelPartBuilder.create().uv(0,16).mirrored().cuboid(0,0,0,5,0,3),ModelTransform.pivot(.5f,-1.55f,-.7f));
        for(int i=0;i<LEGS.length;i++){
            float[] l=LEGS[i];
            body.addChild("leg"+i,ModelPartBuilder.create().uv(24,0).cuboid(-.5f,0,-.5f,1,3,1,new Dilation(-.33f)),
                ModelTransform.of(l[0],1.2f,l[1],(float)Math.toRadians(l[3]),0,(float)Math.toRadians(l[2])));
        }
        root=TexturedModelData.of(data,32,32).createModel();
        thorax=root.getChild("thorax");rightWing=thorax.getChild("right_wing");leftWing=thorax.getChild("left_wing");
    }
    /**
     * Its wings {@code beat} (radians: up from level) and swept back {@code sweep} (radians), mirror images of each other.
     */
    void wings(float beat,float sweep){
        // (the right wing runs out along -x: turning it +z lifts its tip, -y; the left one's the other way)
        rightWing.roll=beat;rightWing.yaw=sweep;
        leftWing.roll=-beat;leftWing.yaw=-sweep;
    }
}
