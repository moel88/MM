package dev.sangwoo.celestialfall.client.vfx;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Refraction geometry for the effect layer: bands whose uv.y runs across them (0 inner edge, 1 outer edge).
 * The shader bends the world behind a band outward on its outer half and inward on its inner half; the
 * strength is in pixels at full strength 1.
 */
public final class FxDistort {
    private final BufferBuilder buffer;
    private final Matrix4f matrix;
    private int quads;
    FxDistort(BufferBuilder buffer,Matrix4f matrix){this.buffer=buffer;this.matrix=matrix;}
    private void point(Vec3d p,float u,float v,float strength){
        buffer.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).texture(u,v).color(255,255,255,Math.max(0,Math.min(255,Math.round(strength*255)))).next();
    }
    /** Arc band in the plane (u,v) (point = o + (u cos a + v sin a) r) from {@code startDeg} over {@code lengthDeg}. */
    public void arc(Vec3d o,Vec3d u,Vec3d v,double outer,double width,double startDeg,double lengthDeg,float strength){
        if(!(strength>0)||lengthDeg==0||!(width>0))return;
        double inner=Math.max(0,outer-width);int n=Math.max(8,(int)Math.ceil(Math.abs(lengthDeg)/4));
        for(int i=0;i<n;i++){
            double t0=i/(double)n,t1=(i+1)/(double)n,a0=Math.toRadians(startDeg+lengthDeg*t0),a1=Math.toRadians(startDeg+lengthDeg*t1);
            // Fade in and out along the arc so its ends do not cut the world with a straight edge.
            float s0=strength*(float)Math.sin(Math.PI*t0),s1=strength*(float)Math.sin(Math.PI*t1);
            Vec3d r0=u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))),r1=u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
            point(o.add(r0.multiply(inner)),(float)t0,0,s0);point(o.add(r1.multiply(inner)),(float)t1,0,s1);
            point(o.add(r1.multiply(outer)),(float)t1,1,s1);point(o.add(r0.multiply(outer)),(float)t0,1,s0);quads++;
        }
    }
    /** Whole ring of {@code radius} (its outer edge), {@code width} deep. */
    public void ring(Vec3d center,Vec3d u,Vec3d v,double radius,double width,float strength){
        if(!(strength>0))return;
        double inner=Math.max(0,radius-width);int n=96;
        for(int i=0;i<n;i++){
            double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;
            Vec3d r0=u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))),r1=u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
            point(center.add(r0.multiply(inner)),(float)(i/(double)n),0,strength);point(center.add(r1.multiply(inner)),(float)((i+1)/(double)n),0,strength);
            point(center.add(r1.multiply(radius)),(float)((i+1)/(double)n),1,strength);point(center.add(r0.multiply(radius)),(float)(i/(double)n),1,strength);quads++;
        }
    }
    /** Straight band from a to b, {@code width} wide along {@code side}. */
    public void band(Vec3d a,Vec3d b,Vec3d side,double width,float strength){
        if(!(strength>0))return;Vec3d s=side.normalize().multiply(width*.5);
        point(a.subtract(s),0,0,strength);point(b.subtract(s),1,0,strength);point(b.add(s),1,1,strength);point(a.add(s),0,1,strength);quads++;
    }
    int quads(){return quads;}
    BufferBuilder buffer(){return buffer;}
}
