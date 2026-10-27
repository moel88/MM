package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.HeavenTimeline;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeavenSceneMeshTest {
    @Test void bladeIsLongParallelAndHasGuardGripAndPommel() {
        assertTrue(HeavenTimeline.BLADE_HEIGHT/(2*HeavenTimeline.BLADE_HALF_WIDTH)>5);
        for(int y=140;y<=680;y++)assertTrue(HeavenTimeline.halfWidth(y)>=66 && HeavenTimeline.halfWidth(y)<=71);
        assertTrue(HeavenBlade.GUARD_HALF_WIDTH>2*HeavenTimeline.BLADE_HALF_WIDTH);
        double maxY=0,maxX=0;
        for(var face:HeavenBlade.FACES)for(Vec3d v:List.of(face.a(),face.b(),face.c(),face.d())) {
            assertTrue(Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z));
            assertTrue(v.y>=0);maxY=Math.max(maxY,v.y);maxX=Math.max(maxX,Math.abs(v.x));
        }
        assertEquals(920,maxY);assertEquals(156,maxX);
        for(int age=32;age<160;age+=4)for(var face:HeavenBlade.visibleFaces(age))
            for(var p:List.of(face.a(),face.b(),face.c(),face.d()))assertTrue(p.y<=HeavenBlade.exposedHeight(age)+1e-6);
        assertSame(HeavenBlade.FACES,HeavenBlade.visibleFaces(145));
        assertEquals(HeavenRenderer.point(Vec3d.ZERO,0,38,0,720,0).subtract(HeavenRenderer.point(Vec3d.ZERO,0,38,0,0,0)).length(),720,1e-6);
    }
    @Test void cloudBanksHaveDepthOpenCentreAndBoundedCachedGeometry() {
        int high=0,low=0;double minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY,minRadius=999;
        for(var layer:HeavenClouds.LAYERS) {
            high+=layer.high().size();low+=layer.low().size();
            for(var f:layer.high())for(var p:List.of(f.a(),f.b(),f.c(),f.d())) {
                assertTrue((f.color()>>16&255)<=(f.color()>>8&255) && (f.color()>>8&255)<=(f.color()&255),"Cloud lighting must not overflow blue into yellow");
                minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);minRadius=Math.min(minRadius,Math.hypot(p.x,p.y));
                assertTrue(p.length()<900);
            }
        }
        assertTrue(maxZ-minZ>80);assertTrue(minRadius>80 && minRadius<115);assertTrue(high<15000 && low<high);
    }
    @Test void camerasAndCloudTransformsStayFiniteAtAllCastRanges() {
        for(float yaw:new float[]{0,90,225})for(double distance:new double[]{12,64,128}) {
            Vec3d origin=new Vec3d(200,80,-300),target=origin.add(Vec3d.fromPolar(0,yaw).multiply(distance));
            for(float age=0;age<HeavenTimeline.CAMERA_END;age+=.5f) {
                var frame=HeavenScene.camera(origin,target,yaw,age,HeavenActor.fingerTip(origin,yaw,age));
                assertTrue(frame.position().distanceTo(frame.focus())>.01);
                assertTrue(Double.isFinite(frame.position().x+frame.position().y+frame.position().z));
                assertTrue(frame.position().y>=Math.min(origin.y,target.y));
                for(int i=0;i<3;i++)assertTrue(HeavenScene.cloudTransform(origin,target,yaw,age,i).isFinite());
            }
        }
    }
    @Test void boundedCameraFollowsOnlyPartOfTheFallAndKeepsContactContinuous() {
        Vec3d target=new Vec3d(0,0,80);
        var before=HeavenScene.camera(Vec3d.ZERO,target,0,99.999f,Vec3d.ZERO);
        var contact=HeavenScene.camera(Vec3d.ZERO,target,0,100,Vec3d.ZERO);
        assertTrue(before.position().distanceTo(contact.position())<.01);
        assertTrue(before.focus().distanceTo(contact.focus())<.01);
        var descent=HeavenScene.camera(Vec3d.ZERO,target,0,HeavenTimeline.DESCENT,Vec3d.ZERO);
        double previousFocus=descent.focus().y;
        for(float a=HeavenTimeline.DESCENT;a<HeavenTimeline.CAMERA_END;a+=.125f) {
            var frame=HeavenScene.camera(Vec3d.ZERO,target,0,a,Vec3d.ZERO);
            assertEquals(descent.position(),frame.position(),"No retreat, orbit or vertical camera travel");
            assertTrue(frame.position().distanceTo(target)<300,"Never fit the full sword by zooming out");
            assertTrue(frame.focus().y<=previousFocus+1e-6 && previousFocus-frame.focus().y<.55,"Smooth downward gaze");
            assertEquals(descent.fov(),frame.fov());assertEquals(0,frame.roll());
            previousFocus=frame.focus().y;
        }
        assertTrue(descent.focus().y-contact.focus().y>30 && descent.focus().y-contact.focus().y<80);
        for(int a=HeavenTimeline.IMPACT;a<HeavenTimeline.CAMERA_END;a++) {
            assertEquals(target,HeavenScene.effectFocus(Vec3d.ZERO,target,0,a));
            assertEquals(contact,HeavenScene.camera(Vec3d.ZERO,target,0,a,Vec3d.ZERO));
        }
    }
    private static Vec3d projected(HeavenScene.Frame camera,Vec3d point,double aspect) {
        Vec3d forward=camera.focus().subtract(camera.position()).normalize();
        Vec3d right=forward.crossProduct(new Vec3d(0,1,0)).normalize(),up=right.crossProduct(forward);
        Vec3d delta=point.subtract(camera.position());double depth=delta.dotProduct(forward);
        double scale=Math.tan(Math.toRadians(camera.fov()/2))*depth;
        return new Vec3d(delta.dotProduct(right)/(scale*aspect),delta.dotProduct(up)/scale,depth);
    }
    @Test void swordTipTravelsDownTheScreenWithGroundVisibleInsideTheLetterbox() {
        // Test screen-space behavior, not just finite coordinates or chosen camera constants.
        for(float yaw:new float[]{0,90,225})for(double range:new double[]{12,64,128})for(double aspect:new double[]{4./3,16./9,21./9}) {
            Vec3d origin=new Vec3d(200,80,-300),target=origin.add(Vec3d.fromPolar(0,yaw).multiply(range));
            double startY=0,lastY=2;
            for(float age=HeavenTimeline.DESCENT;age<=HeavenTimeline.IMPACT;age+=.25f) {
                var camera=HeavenScene.camera(origin,target,yaw,age,Vec3d.ZERO);
                var tip=projected(camera,HeavenScene.bladePoint(target,yaw,age,0,0,0),aspect);
                var ground=projected(camera,target,aspect);
                assertTrue(tip.z>0 && Math.abs(tip.x)<.85 && Math.abs(tip.y)<.85,"Falling tip remains visible");
                assertTrue(ground.z>0 && Math.abs(ground.x)<.85 && Math.abs(ground.y)<.85,"Ground remains inside the letterbox");
                assertTrue(tip.y<=lastY+1e-5,"Following the gaze must not freeze or reverse the visible fall");
                if(age==HeavenTimeline.DESCENT)startY=tip.y;
                lastY=tip.y;
            }
            assertTrue(startY-lastY>.7,"Tip must fall through more than 35% of screen height");
            var frame=HeavenScene.camera(origin,target,yaw,100,Vec3d.ZERO);
            assertTrue(projected(frame,HeavenScene.bladePoint(target,yaw,100,0,720,0),aspect).y>1,"Upper sword deliberately leaves the frame");
        }
    }
    @Test void wideTerrainSeedStaysInLoadedAreaWithoutMovingTheRealView() {
        var view=new net.minecraft.client.render.Camera();Vec3d before=view.getPos();
        Vec3d loaded=new Vec3d(20,85,-31);
        var seed=new HeavenTerrainCamera(view,loaded);
        assertEquals(loaded,seed.getPos());assertEquals(net.minecraft.util.math.BlockPos.ofFloored(loaded),seed.getBlockPos());
        assertEquals(before,view.getPos());assertEquals(view.getYaw(),seed.getYaw());assertEquals(view.getPitch(),seed.getPitch());
    }
    @Test void impactEmittersAndWaveClocksMatchContactAndFinalPressure() {
        Vec3d target=new Vec3d(500,80,-20);
        for(float yaw:new float[]{0,90,225})for(int age=100;age<=244;age++)for(int i=0;i<12;i++) {
            Vec3d a=HeavenImpact.emitter(target,yaw,age,i*Math.PI/6,0),b=HeavenImpact.emitter(target,yaw,age,i*Math.PI/6+Math.PI,0);
            assertEquals(target.y+.25,a.y,1e-8);assertTrue(a.distanceTo(target)<100);
            assertTrue(a.add(b).multiply(.5).distanceTo(target.add(0,.25,0))<1e-6);
        }
        for(int wave=0;wave<3;wave++)assertEquals(0,HeavenImpact.waveProgress(99.99f,wave));
        assertEquals(0,HeavenImpact.waveProgress(179.99f,2));assertTrue(HeavenImpact.waveProgress(190,2)>0);
        assertEquals(0,HeavenImpact.fade(99));assertEquals(0,HeavenImpact.fade(244));
        for(int stage=0;stage<4;stage++)assertTrue(HeavenImpact.burstAge(stage)>=HeavenTimeline.IMPACT && HeavenImpact.burstAge(stage)<=HeavenTimeline.EMBED_END);
    }
    private static List<Object> packed(List<HeavenBlade.Face> faces) {
        List<Object> out=new ArrayList<>();
        for(var f:faces)out.add(List.of(List.of(f.a().x,f.a().y,f.a().z),List.of(f.b().x,f.b().y,f.b().z),
            List.of(f.c().x,f.c().y,f.c().z),List.of(f.d().x,f.d().y,f.d().z),f.color()));
        return out;
    }
    private static net.minecraft.client.model.ModelPart part(String name) throws Exception {
        var f=HeavenActor.class.getDeclaredField(name);f.setAccessible(true);return (net.minecraft.client.model.ModelPart)f.get(null);
    }
    private static Object actor(float age,float yaw) throws Exception {
        HeavenActor.applyPose(age);
        var m=new net.minecraft.client.util.math.MatrixStack();m.translate(0,1.5,0);
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180-yaw));m.scale(-1,-1,1);
        var root=part("ROOT");var cloth=new AtomicRigTest.Capture();var face=new AtomicRigTest.Capture();var hand=new AtomicRigTest.Capture();
        if(age<HeavenTimeline.FACE) {
            m.push();root.getChild("body").rotate(m);root.getChild("body").getChild("rightArm").rotate(m);
            root.getChild("body").getChild("rightArm").getChild("forearm").render(m,cloth,0,0);m.pop();
        } else root.render(m,cloth,0,0);
        m.push();root.getChild("body").rotate(m);root.getChild("body").getChild("head").rotate(m);if(age>=HeavenTimeline.FACE)part("FACE").render(m,face,0,0);m.pop();
        m.push();HeavenActor.handTransform(m);part("HAND").render(m,hand,0,0);m.pop();
        return Map.of("cloth",cloth.vertices,"face",face.vertices,"hand",hand.vertices);
    }
    @Test void exportActualMeshesAndCameraShotsForVisualInspection() throws Exception {
        Vec3d origin=Vec3d.ZERO,target=new Vec3d(0,0,80);
        List<Object> snapshots=new ArrayList<>();
        for(float age:new float[]{10,24,42,62,70,80,90,100,130,170,210,240}) {
            var camera=HeavenScene.camera(origin,target,0,age,HeavenActor.fingerTip(origin,0,age));
            List<HeavenBlade.Face> blade=new ArrayList<>();List<Object> clouds=new ArrayList<>();
            for(var f:HeavenBlade.visibleFaces(age)) {
                List<Vec3d> p=new ArrayList<>();for(var v:List.of(f.a(),f.b(),f.c(),f.d()))p.add(HeavenRenderer.point(target,0,age,v.x,v.y,v.z));
                blade.add(new HeavenBlade.Face(p.get(0),p.get(1),p.get(2),p.get(3),f.color()));
            }
            if(HeavenTimeline.portal(age)>0)for(int i=0;i<3;i++) {
                var matrix=HeavenScene.cloudTransform(origin,target,0,age,i);
                for(var f:HeavenClouds.LAYERS.get(i).high()) {
                    List<Vec3d> p=new ArrayList<>();
                    for(var v:List.of(f.a(),f.b(),f.c(),f.d())) { var t=new Vector3f((float)v.x,(float)v.y,(float)v.z).mulPosition(matrix);p.add(new Vec3d(t.x,t.y,t.z)); }
                    var colors=HeavenClouds.LAYERS.get(i).highColors();
                    clouds.add(List.of(List.of(p.get(0).x,p.get(0).y,p.get(0).z),List.of(p.get(1).x,p.get(1).y,p.get(1).z),List.of(p.get(2).x,p.get(2).y,p.get(2).z),List.of(p.get(3).x,p.get(3).y,p.get(3).z),f.color(),List.of(colors.get(f.a()),colors.get(f.b()),colors.get(f.c()),colors.get(f.d()))));
                }
            }
            snapshots.add(Map.of("age",age,"camera",camera,"blade",age>=32?packed(blade):List.of(),"clouds",clouds,"actor",age<HeavenTimeline.POSE_END?actor(age,HeavenScene.actorYaw(origin,target,0,age)):Map.of("cloth",List.of(),"face",List.of(),"hand",List.of())));
        }
        Path out=Path.of("build/heaven-scene-check");Files.createDirectories(out);
        Files.writeString(out.resolve("scene.json"),new Gson().toJson(Map.of("local_blade",packed(HeavenBlade.FACES),"shots",snapshots)));
    }
}
