package dev.sangwoo.celestialfall.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomicRigTest {
    static class Capture implements VertexConsumer {
        final List<float[]> vertices=new ArrayList<>();float x,y,z;
        public VertexConsumer vertex(double x,double y,double z) { this.x=(float)x;this.y=(float)y;this.z=(float)z;return this; }
        public VertexConsumer color(int r,int g,int b,int a) { return this; }
        public VertexConsumer texture(float u,float v) { return this; }
        public VertexConsumer overlay(int a,int b) { return this; }
        public VertexConsumer light(int a,int b) { return this; }
        public VertexConsumer normal(float x,float y,float z) { return this; }
        public void next() { vertices.add(new float[]{x,y,z}); }
        public void fixedColor(int r,int g,int b,int a) {}
        public void unfixColor() {}
    }
    @Test void sharedRigBuildsFiniteGeometry() {
        var rig=AtomicActor.createRig();var face=AtomicActor.createFace();
        Capture body=new Capture(),head=new Capture();
        rig.render(new MatrixStack(),body,0,0);face.render(new MatrixStack(),head,0,0);
        assertTrue(body.vertices.size()>100);assertEquals(24,head.vertices.size());
        for(var mesh:List.of(body,head))for(float[] v:mesh.vertices)for(float c:v)assertTrue(Float.isFinite(c)&&Math.abs(c)<4);
    }
}
