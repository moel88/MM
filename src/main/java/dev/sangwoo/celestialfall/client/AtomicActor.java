package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Articulated stand-in for the casting player: separate elbows, knees and coat panels. */
final class AtomicActor {
    private static final Identifier CLOTH=new Identifier(CelestialFall.ID,"textures/entity/atomic_costume.png");
    private static final Identifier BLADE=new Identifier(CelestialFall.ID,"textures/entity/atomic_blade.png");
    private static final ModelPart ROOT=createRig(),FACE=createFace(),HOOD=createHood(),SWORD=createSword();
    private static VertexConsumerProvider.Immediate buffer;
    private static VertexConsumerProvider.Immediate buffer() {
        if(buffer==null)buffer=VertexConsumerProvider.immediate(new BufferBuilder(8192));return buffer;
    }
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
    private static ModelPart createHood() {
        ModelData d=new ModelData();var r=d.getRoot();
        r.addChild("top",cube(16,16,-4.7f,-9,-4.8f,9.4f,1.2f,9.4f),ModelTransform.NONE);
        r.addChild("left",cube(16,16,3.9f,-8,-4.8f,1.1f,8.8f,9.4f),ModelTransform.NONE);
        r.addChild("right",cube(16,16,-5,-8,-4.8f,1.1f,8.8f,9.4f),ModelTransform.NONE);
        r.addChild("back",cube(16,16,-4,-8,3.9f,8,8,1),ModelTransform.NONE);
        r.addChild("collar",cube(16,16,-4.8f,-1.6f,-4.9f,9.6f,2,1.2f),ModelTransform.NONE);
        r.addChild("peak",cube(16,16,-3.7f,-10,-3.2f,7.4f,2,6.4f),ModelTransform.NONE);
        return TexturedModelData.of(d,64,64).createModel();
    }
    private static ModelPart createSword() {
        ModelData d=new ModelData();var r=d.getRoot();
        r.addChild("grip",cube(0,0,-.6f,-2,-.6f,1.2f,5,1.2f),ModelTransform.NONE);
        r.addChild("guard",cube(0,0,-3,2,-.65f,6,1.1f,1.3f),ModelTransform.NONE);
        r.addChild("blade",cube(0,0,-.65f,3,-.22f,1.3f,21,.44f),ModelTransform.NONE);
        r.addChild("tip",cube(0,0,-.28f,24,-.18f,.56f,3,.36f),ModelTransform.NONE);
        r.addChild("edge",cube(32,0,.58f,3,-.25f,.14f,21,.5f),ModelTransform.NONE);
        return TexturedModelData.of(d,64,64).createModel();
    }
    private static float rad(float a) { return a*(float)Math.PI/180; }
    static void applyPose(float age,boolean costume) {
        var p=dev.sangwoo.celestialfall.combat.AtomicMotion.sample(age);
        var b=ROOT.getChild("body");
        var torso=new org.joml.Quaternionf().rotationZYX(rad(p.joint[1][2]),rad(p.joint[1][1]),rad(p.joint[1][0]));
        var hip=new org.joml.Vector3f(0,12,0).rotate(torso);
        b.pivotX=p.shift-hip.x;b.pivotY=12+p.bob-hip.y;b.pivotZ=p.lean-hip.z;
        rotate(b,p.joint[1]);rotate(b.getChild("head"),p.joint[0]);
        for(int i=0;i<2;i++) {
            String side=i==0?"right":"left";
            var arm=b.getChild(side+"Arm");rotate(arm,p.joint[2+i]);
            arm.getChild("forearm").pitch=rad(p.elbow[i]);
            var leg=ROOT.getChild(side+"Leg");rotate(leg,p.joint[4+i]);
            leg.pivotX=(i==0?-2:2)+p.shift;leg.pivotY=12+p.bob;leg.pivotZ=p.lean;
            leg.getChild("shin").pitch=rad(p.knee[i]);
            var cape=b.getChild(side+"Cape");cape.visible=costume;float sign=i==0?-1:1;
            float spread=dev.sangwoo.celestialfall.combat.AtomicMotion.capeSpread(age);
            cape.roll=rad(-sign*(spread+2*(float)Math.sin(age*.09+sign)));
            cape.pitch=rad(-9-spread*.45f);cape.yaw=rad(sign*3);
            cape.getChild("tail").roll=rad(sign*(5+3*(float)Math.sin(age*.12-1)));
            cape.getChild("tail").pitch=rad(-8-3*(float)Math.sin(age*.1));
            cape.getChild("tail").yScale=.32f; // Hem ends at the ankle instead of passing through the floor.
        }
    }
    private static void rotate(ModelPart part,float[] r) { part.pitch=rad(r[0]);part.yaw=rad(r[1]);part.roll=rad(r[2]); }
    record Blade(net.minecraft.util.math.Vec3d grip,net.minecraft.util.math.Vec3d tip) {}
    static Blade blade(net.minecraft.util.math.Vec3d origin,float yaw,float age) {
        applyPose(age,true);var body=ROOT.getChild("body");var arm=body.getChild("rightArm");
        MatrixStack m=new MatrixStack();m.translate(origin.x,origin.y+1.5,origin.z);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-yaw));m.scale(-1,-1,1);
        body.rotate(m);arm.rotate(m);arm.getChild("forearm").rotate(m);m.translate(0,5.5/16.,0);
        var matrix=m.peek().getPositionMatrix();var h=new org.joml.Vector3f(0,3/16f,0).mulPosition(matrix);
        var t=new org.joml.Vector3f(0,27/16f,0).mulPosition(matrix);
        return new Blade(new net.minecraft.util.math.Vec3d(h.x,h.y,h.z),new net.minecraft.util.math.Vec3d(t.x,t.y,t.z));
    }
    static void renderSword(MatrixStack m,VertexConsumer consumer,int light,int overlay){m.push();m.translate(-.5,-5/16.,-.5);KagenoSword.renderMesh(m,consumer,light,overlay);m.pop();}
    static void render(WorldRenderContext context,AtomicClient.Cast s) {
        float age=s.age(context.tickDelta());if(!AtomicTimeline.posed(age))return;
        var mc=MinecraftClient.getInstance();
        if(mc.player!=null && s.caster.equals(mc.player.getUuid()) && !context.camera().isThirdPerson())return;
        var entity=mc.world.getPlayerByUuid(s.caster);
        if(!(entity instanceof AbstractClientPlayerEntity player) || !player.isAlive())return;
        boolean costume=CelestialFall.CONFIG.atomicCostume&&!StandClient.visible(player);
        applyPose(age,costume);
        var b=ROOT.getChild("body");var head=b.getChild("head");var right=b.getChild("rightArm");
        MatrixStack m=context.matrixStack();var camera=context.camera().getPos();
        m.push();m.translate(s.origin.x-camera.x,s.origin.y-camera.y+1.5,s.origin.z-camera.z);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-s.yaw));m.scale(-1,-1,1);
        int light=LightmapTextureManager.MAX_LIGHT_COORDINATE;
        ROOT.render(m,buffer().getBuffer(RenderLayer.getEntityCutoutNoCull(costume?CLOTH:player.getSkinTexture())),light,OverlayTexture.DEFAULT_UV);
        m.push();b.rotate(m);head.rotate(m);
        FACE.render(m,buffer().getBuffer(RenderLayer.getEntityCutoutNoCull(player.getSkinTexture())),light,OverlayTexture.DEFAULT_UV);
        if(costume)HOOD.render(m,buffer().getBuffer(RenderLayer.getEntityCutoutNoCull(CLOTH)),light,OverlayTexture.DEFAULT_UV);
        m.pop();
        m.push();b.rotate(m);right.rotate(m);right.getChild("forearm").rotate(m);m.translate(0,5.5/16.0,0);
        renderSword(m,buffer().getBuffer(RenderLayer.getEntityCutoutNoCull(KagenoSword.TEXTURE)),light,OverlayTexture.DEFAULT_UV);m.pop();
        buffer().draw();m.pop();
    }
}
