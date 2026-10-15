package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.HeavenTimeline;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeavenRigTest {
    private static ModelPart part(String name) throws Exception {
        var f=HeavenActor.class.getDeclaredField(name);f.setAccessible(true);return (ModelPart)f.get(null);
    }
    @Test void actualTwoFingerRigHasAnAttachedHandInFrontOfFace() throws Exception {
        List<Map<String,Object>> snapshots=new ArrayList<>();
        for(float age:new float[]{0,12,20,28,55,56}) {
            HeavenActor.applyPose(age);var root=part("ROOT");var b=root.getChild("body");
            var m=new MatrixStack();m.translate(0,1.5,0);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));m.scale(-1,-1,1);
            var cloth=new AtomicRigTest.Capture();var face=new AtomicRigTest.Capture();var hand=new AtomicRigTest.Capture();
            root.render(m,cloth,0,0);
            m.push();b.rotate(m);b.getChild("head").rotate(m);part("FACE").render(m,face,0,0);m.pop();
            m.push();HeavenActor.handTransform(m);part("HAND").render(m,hand,0,0);m.pop();
            assertTrue(part("HAND").hasChild("index") && part("HAND").hasChild("middle"));
            for(var mesh:List.of(cloth,face,hand))for(float[] v:mesh.vertices)for(float f:v)assertTrue(Float.isFinite(f));
            Vec3d tip=HeavenActor.fingerTip(Vec3d.ZERO,0,age);
            if(age==20) {
                assertTrue(tip.y>1.4 && tip.y<2,"Finger height: "+tip);
                assertTrue(tip.z>.25 && tip.z<.9,"Finger must be in front of the face: "+tip);
                assertTrue(Math.abs(tip.x)<.3,"Seal must sit close to the face centre: "+tip);
            }
            snapshots.add(Map.of("age",age,"cloth",cloth.vertices,"face",face.vertices,"hand",hand.vertices,"tip",List.of(tip.x,tip.y,tip.z)));
        }
        Path out=Path.of("build/heaven-check");Files.createDirectories(out);Files.writeString(out.resolve("rig.json"),new Gson().toJson(snapshots));
    }
    @Test void actualBladeTransformEmbedsThroughAFixedSurfaceIntersection() {
        for(float yaw:new float[]{0,45,180,270})for(float age:new float[]{32,62,99,100,124,160,180,244,339}) {
            Vec3d target=new Vec3d(150,80,-25);
            Vec3d tip=HeavenRenderer.point(target,yaw,age,0,0,0);
            assertEquals(target.y+HeavenTimeline.tipHeight(age),tip.y,1e-7);
            if(age>=HeavenTimeline.IMPACT) {
                double localY=-HeavenTimeline.tipHeight(age)/Math.cos(HeavenTimeline.TILT);
                assertTrue(HeavenRenderer.point(target,yaw,age,0,localY,0).distanceTo(target)<1e-6,"Ground intersection must stay on VFX / damage anchor");
            }
            for(double y=0;y<=720;y+=5)for(int sign:new int[]{-1,1}) {
                Vec3d p=HeavenRenderer.point(target,yaw,age,sign*HeavenTimeline.halfWidth(y),y,8);
                assertTrue(Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z));
                assertTrue(p.y>=target.y-100);assertTrue(p.distanceTo(target)<1300);
            }
        }
    }
}
