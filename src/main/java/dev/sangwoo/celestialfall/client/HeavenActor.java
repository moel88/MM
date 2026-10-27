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
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** A two-finger seal, blue/white sleeves and flowing coat, with the player's own face. */
final class HeavenActor {
    private static final Identifier CLOTH=new Identifier("celestialfall","textures/entity/heaven_costume.png");
    private static final Identifier GLOVE=new Identifier("celestialfall","textures/entity/heaven_glove.png");
    private static final ModelPart ROOT=AtomicActor.createRig(),FACE=AtomicActor.createFace(),HAND=createHand();
    private static VertexConsumerProvider.Immediate buffer;
    private HeavenActor() {}
    private static ModelPart createHand() {
        ModelData d=new ModelData();var r=d.getRoot();
        r.addChild("palm",ModelPartBuilder.create().uv(0,0).cuboid(-1.6f,-1,-1.6f,3.2f,2.5f,3.2f),ModelTransform.NONE);
        r.addChild("index",ModelPartBuilder.create().uv(0,0).cuboid(-1.35f,1.4f,-1.2f,1,4.3f,1.1f),ModelTransform.NONE);
        r.addChild("middle",ModelPartBuilder.create().uv(0,0).cuboid(-.15f,1.4f,-1.2f,1,4.6f,1.1f),ModelTransform.NONE);
        r.addChild("curled",ModelPartBuilder.create().uv(0,0).cuboid(.7f,.5f,-.6f,1.2f,2,2.2f),ModelTransform.NONE);
        r.addChild("thumb",ModelPartBuilder.create().uv(0,0).cuboid(-2,-.4f,-.2f,1,2.4f,1.4f),ModelTransform.NONE);
        return TexturedModelData.of(d,16,16).createModel();
    }
    private static float rad(float x) { return x*(float)Math.PI/180; }
    static void applyPose(float age) {
        var p=HeavenTimeline.pose(age);var b=ROOT.getChild("body");b.yaw=rad(p.bodyYaw());b.getChild("head").pitch=rad(p.headPitch());
        var a=b.getChild("rightArm");a.pitch=rad(p.rightPitch());a.roll=rad(p.rightRoll());a.getChild("forearm").pitch=rad(p.elbow());a.getChild("forearm").roll=rad(70*HeavenTimeline.ease(age,0,16)*p.cape());
        var l=b.getChild("leftArm");l.pitch=rad(p.leftPitch());l.roll=rad(p.leftRoll());l.getChild("forearm").pitch=rad(-15*p.cape());
        ROOT.getChild("rightLeg").pitch=rad(3*p.cape());ROOT.getChild("leftLeg").pitch=rad(-3*p.cape());
        for(int side:new int[]{-1,1}) {
            var cape=b.getChild(side<0?"rightCape":"leftCape");cape.visible=true;
            cape.roll=rad(-side*(18+8*(float)Math.sin(age*.11))*p.cape());cape.pitch=rad(-25*p.cape());
            cape.getChild("tail").pitch=rad(-15-14*(float)Math.sin(age*.15+side));
        }
    }
    private static void rootTransform(MatrixStack m,Vec3d origin,float yaw) {
        m.translate(origin.x,origin.y+1.5,origin.z);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-yaw));m.scale(-1,-1,1);
    }
    static void handTransform(MatrixStack m) {
        var b=ROOT.getChild("body");b.rotate(m);var a=b.getChild("rightArm");a.rotate(m);a.getChild("forearm").rotate(m);m.translate(0,5.5/16.,0);
    }
    static Vec3d fingerTip(Vec3d origin,float yaw,float age) {
        applyPose(age);MatrixStack m=new MatrixStack();rootTransform(m,origin,yaw);handTransform(m);
        Vector3f p=new Vector3f(-.25f/16,6f/16,-.7f/16).mulPosition(m.peek().getPositionMatrix());
        return new Vec3d(p.x,p.y,p.z);
    }
    static HeavenScene.Frame handFrame(Vec3d origin,float yaw,float age) {
        applyPose(age);var m=new MatrixStack();rootTransform(m,origin,yaw);handTransform(m);
        var matrix=m.peek().getPositionMatrix();
        var centre=new Vector3f(-.25f/16,3.0f/16,-.7f/16).mulPosition(matrix);
        var normal=new Vector3f(0,0,-1).mulDirection(matrix).normalize();
        Vec3d focus=new Vec3d(centre.x,centre.y,centre.z),n=new Vec3d(normal.x,normal.y,normal.z);
        Vec3d position=focus.add(n.multiply(.92)),direction=n.negate();
        Vec3d right=direction.crossProduct(new Vec3d(0,1,0)).normalize();
        if(right.lengthSquared()<.01)right=new Vec3d(1,0,0);
        Vec3d up=right.crossProduct(direction).normalize(),axis=fingerTip(origin,yaw,age).subtract(focus).normalize();
        double projected=Math.toDegrees(Math.atan2(axis.dotProduct(up),axis.dotProduct(right)));
        double desired=165-HeavenTimeline.ease(age,0,12)*105;
        return new HeavenScene.Frame(position,focus,42,net.minecraft.util.math.MathHelper.wrapDegrees((float)(desired-projected)));
    }
    static void render(WorldRenderContext c,HeavenClient.Cast s) {
        float age=s.age(c.tickDelta());if(!HeavenTimeline.posed(age))return;
        var mc=MinecraftClient.getInstance();var entity=mc.world.getPlayerByUuid(s.caster);
        if(!(entity instanceof AbstractClientPlayerEntity player) || !player.isAlive())return;
        if(mc.player!=null && s.caster.equals(mc.player.getUuid()) && !c.camera().isThirdPerson())return;
        if(buffer==null)buffer=VertexConsumerProvider.immediate(new BufferBuilder(8192));
        applyPose(age);var m=c.matrixStack();m.push();rootTransform(m,s.origin.subtract(c.camera().getPos()),HeavenScene.actorYaw(s.origin,s.target,s.yaw,age));
        int light=LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay=OverlayTexture.DEFAULT_UV;
        if(age<HeavenTimeline.FACE && HeavenClient.cinematic()==s) {
            m.push();ROOT.getChild("body").rotate(m);ROOT.getChild("body").getChild("rightArm").rotate(m);
            ROOT.getChild("body").getChild("rightArm").getChild("forearm").render(m,buffer.getBuffer(RenderLayer.getEntityCutoutNoCull(CLOTH)),light,overlay);m.pop();
        } else ROOT.render(m,buffer.getBuffer(RenderLayer.getEntityCutoutNoCull(CLOTH)),light,overlay);
        m.push();ROOT.getChild("body").rotate(m);ROOT.getChild("body").getChild("head").rotate(m);
        if(age>=HeavenTimeline.FACE || HeavenClient.cinematic()!=s)FACE.render(m,buffer.getBuffer(RenderLayer.getEntityCutoutNoCull(player.getSkinTexture())),light,overlay);m.pop();
        m.push();handTransform(m);HAND.render(m,buffer.getBuffer(RenderLayer.getEntityCutoutNoCull(GLOVE)),light,overlay);m.pop();
        buffer.draw();m.pop();
    }
}
