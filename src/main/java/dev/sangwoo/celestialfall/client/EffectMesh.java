package dev.sangwoo.celestialfall.client;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Small batched geometry writer. No spawned armor stands and no per-pixel particles. */
public final class EffectMesh {
    private final BufferBuilder buffer;
    private final Matrix4f matrix;
    private final Vec3d camera;
    private final Vec3d origin;
    private final double scale;
    public EffectMesh(BufferBuilder buffer, Matrix4f matrix, Vec3d camera) {
        this(buffer,matrix,camera,Vec3d.ZERO,1);
    }
    private EffectMesh(BufferBuilder buffer,Matrix4f matrix,Vec3d camera,Vec3d origin,double scale) {
        this.buffer = buffer; this.matrix = matrix; this.camera = camera;this.origin=origin;this.scale=scale;
    }
    public EffectMesh scaled(Vec3d origin,double scale) {
        return new EffectMesh(buffer,matrix,origin.add(camera.subtract(origin).multiply(1/scale)),origin,scale);
    }
    public void vertex(Vec3d v, int rgb, float alpha) {
        if(scale!=1)v=origin.add(v.subtract(origin).multiply(scale));
        buffer.vertex(matrix, (float)v.x, (float)v.y, (float)v.z)
            .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, (int)(alpha * 255)))).next();
    }
    public void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int rgb, float alpha) {
        vertex(a, rgb, alpha); vertex(b, rgb, alpha); vertex(c, rgb, alpha); vertex(d, rgb, alpha);
    }
    public void triangle(Vec3d a, Vec3d b, Vec3d c, int rgb, float alpha) { quad(a,b,c,c,rgb,alpha); }
    public void line(Vec3d a, Vec3d b, double width, int rgb, float alpha) {
        Vec3d direction = b.subtract(a);
        if (direction.lengthSquared() < 1e-10 || alpha <= 0) return;
        Vec3d side = direction.crossProduct(camera.subtract(a)).normalize().multiply(width * 0.5);
        if (side.lengthSquared() < 1e-12) side = new Vec3d(width * 0.5,0,0);
        quad(a.subtract(side), a.add(side), b.add(side), b.subtract(side), rgb, alpha);
    }
    /** Camera-facing line that is opaque along its axis and fades to nothing at both edges. */
    public void softLine(Vec3d a, Vec3d b, double width, int rgb, float alpha) {
        Vec3d direction = b.subtract(a);
        if (direction.lengthSquared() < 1e-10 || alpha <= 0) return;
        Vec3d side = direction.crossProduct(camera.subtract(a)).normalize().multiply(width * 0.5);
        if (!Double.isFinite(side.x)) return;
        vertex(a,rgb,alpha);vertex(b,rgb,alpha);vertex(b.add(side),rgb,0);vertex(a.add(side),rgb,0);
        vertex(a.subtract(side),rgb,0);vertex(b.subtract(side),rgb,0);vertex(b,rgb,alpha);vertex(a,rgb,alpha);
    }
    /** Soft line whose width runs linearly from {@code wa} at a to {@code wb} at b. */
    public void softTaper(Vec3d a, Vec3d b, double wa, double wb, int rgb, float alpha) {
        Vec3d direction = b.subtract(a);
        if (direction.lengthSquared() < 1e-10 || alpha <= 0) return;
        Vec3d sa = direction.crossProduct(camera.subtract(a)).normalize(), sb = direction.crossProduct(camera.subtract(b)).normalize();
        if (!Double.isFinite(sa.x) || !Double.isFinite(sb.x)) return;
        Vec3d ha = sa.multiply(wa * 0.5), hb = sb.multiply(wb * 0.5);
        vertex(a,rgb,alpha);vertex(b,rgb,alpha);vertex(b.add(hb),rgb,0);vertex(a.add(ha),rgb,0);
        vertex(a.subtract(ha),rgb,0);vertex(b.subtract(hb),rgb,0);vertex(b,rgb,alpha);vertex(a,rgb,alpha);
    }
    public void glowLine(Vec3d a, Vec3d b, double width, int rgb, float alpha) {
        line(a,b,width * 7,rgb,alpha * 0.07f);
        line(a,b,width * 3,rgb,alpha * 0.22f);
        line(a,b,width,rgb,alpha);
        line(a,b,width * 0.25,0xFFF6FF,alpha);
    }
    public void star(Vec3d p, double radius, int rgb, float alpha) {
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
    public void ring(Vec3d center, Vec3d u, Vec3d v, double radius, int segments, float fraction,
              double width, int rgb, float alpha, double rotation) {
        int n = (int)(segments * Math.min(1,fraction));
        for (int i=0;i<n;i++) {
            double a=rotation + 2*Math.PI*i/segments,b=rotation + 2*Math.PI*(i+1)/segments;
            Vec3d p=center.add(u.multiply(Math.cos(a)*radius)).add(v.multiply(Math.sin(a)*radius));
            Vec3d q=center.add(u.multiply(Math.cos(b)*radius)).add(v.multiply(Math.sin(b)*radius));
            line(p,q,width,rgb,alpha);
        }
    }
    public Vec3d camera(){return camera;}
    /** Camera-facing blade stroke: sharp at both ends, widest at {@code peak} (0..1 along a→b). */
    public void needle(Vec3d a,Vec3d b,double width,double peak,int rgb,float alpha){
        Vec3d d=b.subtract(a);if(d.lengthSquared()<1e-10||alpha<=0)return;
        Vec3d mid=a.add(d.multiply(peak));
        Vec3d side=d.crossProduct(camera.subtract(mid)).normalize().multiply(width*.5);
        if(!Double.isFinite(side.x))return;
        quad(a,mid.add(side),b,mid.subtract(side),rgb,alpha);
    }
    /** The same stroke on a fixed plane: {@code side} (a unit vector across the stroke) instead of turning to the camera. */
    public void needle(Vec3d a,Vec3d b,Vec3d side,double width,double peak,int rgb,float alpha){
        if(alpha<=0||b.subtract(a).lengthSquared()<1e-10)return;
        Vec3d mid=a.add(b.subtract(a).multiply(peak)),half=side.multiply(width*.5);
        quad(a,mid.add(half),b,mid.subtract(half),rgb,alpha);
    }
    /** Camera-facing radial gradient: opaque centre fading to a transparent rim. */
    public void softDisc(Vec3d p,double radius,int rgb,float alpha){
        if(alpha<=0||radius<=0)return;
        Vec3d normal=camera.subtract(p).normalize(),u=normal.crossProduct(new Vec3d(0,1,0)).normalize();
        if(u.lengthSquared()<.1)u=new Vec3d(1,0,0);Vec3d v=u.crossProduct(normal).normalize();
        for(int i=0;i<20;i++){
            double a=i*Math.PI/10,b=(i+1)*Math.PI/10;
            Vec3d pa=p.add(u.multiply(Math.cos(a)*radius)).add(v.multiply(Math.sin(a)*radius)),pb=p.add(u.multiply(Math.cos(b)*radius)).add(v.multiply(Math.sin(b)*radius));
            vertex(p,rgb,alpha);vertex(pa,rgb,0);vertex(pb,rgb,0);vertex(p,rgb,alpha);
        }
    }
    /** Flat quad in an arbitrary plane with soft edges along {@code v}. */
    public void sheet(Vec3d center,Vec3d u,Vec3d v,int rgb,float alpha){
        vertex(center.subtract(u).subtract(v),rgb,0);vertex(center.add(u).subtract(v),rgb,0);vertex(center.add(u),rgb,alpha);vertex(center.subtract(u),rgb,alpha);
        vertex(center.subtract(u),rgb,alpha);vertex(center.add(u),rgb,alpha);vertex(center.add(u).add(v),rgb,0);vertex(center.subtract(u).add(v),rgb,0);
    }
    public void box(Vec3d base, double x0,double y0,double z0,double x1,double y1,double z1,int rgb,float alpha) {
        Vec3d a=base.add(x0,y0,z0),b=base.add(x1,y0,z0),c=base.add(x1,y1,z0),d=base.add(x0,y1,z0);
        Vec3d e=base.add(x0,y0,z1),f=base.add(x1,y0,z1),g=base.add(x1,y1,z1),h=base.add(x0,y1,z1);
        quad(a,b,c,d,rgb,alpha);quad(f,e,h,g,rgb,alpha);quad(e,a,d,h,rgb,alpha);
        quad(b,f,g,c,rgb,alpha);quad(d,c,g,h,rgb,alpha);quad(e,f,b,a,rgb,alpha);
    }
}
