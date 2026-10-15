package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.AtomicTimeline;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicRigTest {
    private static ModelPart part(String name) throws Exception {
        var f=AtomicActor.class.getDeclaredField(name);f.setAccessible(true);return (ModelPart)f.get(null);
    }
    static class Capture implements VertexConsumer {
        final List<float[]> vertices=new ArrayList<>();float x,y,z,u,v,nx,ny,nz;
        public VertexConsumer vertex(double x,double y,double z) { this.x=(float)x;this.y=(float)y;this.z=(float)z;return this; }
        public VertexConsumer color(int r,int g,int b,int a) { return this; }
        public VertexConsumer texture(float u,float v) { this.u=u;this.v=v;return this; }
        public VertexConsumer overlay(int a,int b) { return this; }
        public VertexConsumer light(int a,int b) { return this; }
        public VertexConsumer normal(float x,float y,float z) { nx=x;ny=y;nz=z;return this; }
        public void next() { vertices.add(new float[]{x,y,z,u,v,nx,ny,nz}); }
        public void fixedColor(int r,int g,int b,int a) {}
        public void unfixColor() {}
    }
    @Test void actualBakedRigAndSwordProduceFiniteAttachedGeometry() throws Exception {
        var root=part("ROOT");var body=root.getChild("body");var arm=body.getChild("rightArm");
        List<Map<String,Object>> snapshots=new ArrayList<>();
        for(float age:new float[]{0,74,93,110,AtomicTimeline.RECOVER}) {
            AtomicActor.applyPose(age,true);MatrixStack m=new MatrixStack();m.translate(0,1.5,0);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));m.scale(-1,-1,1);
            Capture cloth=new Capture(),face=new Capture(),sword=new Capture();
            root.render(m,cloth,0,0);
            m.push();body.rotate(m);body.getChild("head").rotate(m);part("FACE").render(m,face,0,0);part("HOOD").render(m,cloth,0,0);m.pop();
            m.push();body.rotate(m);arm.rotate(m);arm.getChild("forearm").rotate(m);m.translate(0,5.5/16.,0);AtomicActor.renderSword(m,sword,0,0);m.pop();
            assertTrue(sword.vertices.size()>100 && cloth.vertices.size()>300);
            for(Capture mesh:List.of(cloth,face,sword))for(float[] v:mesh.vertices) {
                for(float component:v)assertTrue(Float.isFinite(component));
                assertTrue(Math.abs(v[0])<4 && Math.abs(v[1])<5 && Math.abs(v[2])<4,"Unbounded model vertex");
            }
            if(age==74)assertTrue(sword.vertices.stream().mapToDouble(v->v[1]).max().orElseThrow()>face.vertices.stream().mapToDouble(v->v[1]).max().orElseThrow()+.6,
                "Raised blade must extend above the head");
            // After the slash, the blade extends outward and down, leaving the face clear.
            if(age==110) {
                assertTrue(sword.vertices.stream().mapToDouble(v->v[0]).min().orElseThrow() < -1.5);
                assertTrue(sword.vertices.stream().mapToDouble(v->v[1]).max().orElseThrow() < 1.4);
            }
            snapshots.add(Map.of("age",age,"cloth",cloth.vertices,"face",face.vertices,"sword",sword.vertices));
        }
        Path out=Path.of("build/rig-check");Files.createDirectories(out);Files.writeString(out.resolve("mesh.json"),new Gson().toJson(snapshots));
    }
}
