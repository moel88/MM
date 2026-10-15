package dev.sangwoo.celestialfall.client;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Small batched geometry writer. No spawned armor stands and no per-pixel particles. */
final class EffectMesh {
    private final BufferBuilder buffer;
    private final Matrix4f matrix;
    private final Vec3d camera;
    private final Vec3d origin;
    private final double scale;
    EffectMesh(BufferBuilder buffer, Matrix4f matrix, Vec3d camera) {
        this(buffer,matrix,camera,Vec3d.ZERO,1);
    }
    private EffectMesh(BufferBuilder buffer,Matrix4f matrix,Vec3d camera,Vec3d origin,double scale) {
        this.buffer = buffer; this.matrix = matrix; this.camera = camera;this.origin=origin;this.scale=scale;
    }
    EffectMesh scaled(Vec3d origin,double scale) {
        return new EffectMesh(buffer,matrix,origin.add(camera.subtract(origin).multiply(1/scale)),origin,scale);
    }
    void vertex(Vec3d v, int rgb, float alpha) {
        if(scale!=1)v=origin.add(v.subtract(origin).multiply(scale));
        buffer.vertex(matrix, (float)v.x, (float)v.y, (float)v.z)
            .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, (int)(alpha * 255)))).next();
    }
    void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int rgb, float alpha) {
        vertex(a, rgb, alpha); vertex(b, rgb, alpha); vertex(c, rgb, alpha); vertex(d, rgb, alpha);
    }
    void triangle(Vec3d a, Vec3d b, Vec3d c, int rgb, float alpha) { quad(a,b,c,c,rgb,alpha); }
    void line(Vec3d a, Vec3d b, double width, int rgb, float alpha) {
        Vec3d direction = b.subtract(a);
        if (direction.lengthSquared() < 1e-10 || alpha <= 0) return;
        Vec3d side = direction.crossProduct(camera.subtract(a)).normalize().multiply(width * 0.5);
        if (side.lengthSquared() < 1e-12) side = new Vec3d(width * 0.5,0,0);
        quad(a.subtract(side), a.add(side), b.add(side), b.subtract(side), rgb, alpha);
    }
    void glowLine(Vec3d a, Vec3d b, double width, int rgb, float alpha) {
        line(a,b,width * 7,rgb,alpha * 0.07f);
        line(a,b,width * 3,rgb,alpha * 0.22f);
        line(a,b,width,rgb,alpha);
        line(a,b,width * 0.25,0xFFF6FF,alpha);
    }
    void star(Vec3d p, double radius, int rgb, float alpha) {
        Vec3d normal = camera.subtract(p).normalize();
        Vec3d right = normal.crossProduct(new Vec3d(0,1,0)).normalize();
        if (right.lengthSquared() < 0.1) right = new Vec3d(1,0,0);
        Vec3d up = right.crossProduct(normal).normalize();
        line(p.subtract(right.multiply(radius)),p.add(right.multiply(radius)),radius * 0.13,rgb,alpha);
        line(p.subtract(up.multiply(radius)),p.add(up.multiply(radius)),radius * 0.13,rgb,alpha);
        Vec3d r = right.multiply(radius * 0.17), u = up.multiply(radius * 0.17);
        quad(p.add(r),p.add(u),p.subtract(r),p.subtract(u),0xFFFFFF,alpha);
        r=right.multiply(radius * 0.45);u=up.multiply(radius * 0.45);
        quad(p.add(r),p.add(u),p.subtract(r),p.subtract(u),rgb,alpha * 0.16f);
    }
    void ring(Vec3d center, Vec3d u, Vec3d v, double radius, int segments, float fraction,
              double width, int rgb, float alpha, double rotation) {
        int n = (int)(segments * Math.min(1,fraction));
        for (int i=0;i<n;i++) {
            double a=rotation + 2*Math.PI*i/segments,b=rotation + 2*Math.PI*(i+1)/segments;
            Vec3d p=center.add(u.multiply(Math.cos(a)*radius)).add(v.multiply(Math.sin(a)*radius));
            Vec3d q=center.add(u.multiply(Math.cos(b)*radius)).add(v.multiply(Math.sin(b)*radius));
            line(p,q,width,rgb,alpha);
        }
    }
    void box(Vec3d base, double x0,double y0,double z0,double x1,double y1,double z1,int rgb,float alpha) {
        Vec3d a=base.add(x0,y0,z0),b=base.add(x1,y0,z0),c=base.add(x1,y1,z0),d=base.add(x0,y1,z0);
        Vec3d e=base.add(x0,y0,z1),f=base.add(x1,y0,z1),g=base.add(x1,y1,z1),h=base.add(x0,y1,z1);
        quad(a,b,c,d,rgb,alpha);quad(f,e,h,g,rgb,alpha);quad(e,a,d,h,rgb,alpha);
        quad(b,f,g,c,rgb,alpha);quad(d,c,g,h,rgb,alpha);quad(e,f,b,a,rgb,alpha);
    }
}
