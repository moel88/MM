package dev.sangwoo.celestialfall.client.atomic;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The G world shader ({@code atomic_fx}) and a quad writer for it. Every mode writes
 * premultiplied colour, so one blend function serves both the additive glows (alpha 0) and the
 * alpha-blended ground wash and blast curtains.
 */
public final class AtomicShader {
    public static final int CIRCUIT=1,SKY=2,BEAM=3,FLARE=4,DISC=5,CURTAIN=6,ORB=7,STRIATED=8,PILLAR=9,
        DOME=10,SHOCK=11,COLUMN=12,PUFF=13,SKYRING=14,FLASH=15,TOWER=16,GROUND=17;
    static final Vec3d UP=new Vec3d(0,1,0);
    private static ShaderProgram shader;
    private AtomicShader() {}
    public static void initialize() {
        CoreShaderRegistrationCallback.EVENT.register(c->c.register(CelestialFall.id("atomic_fx"),VertexFormats.POSITION_TEXTURE_COLOR,s->shader=s));
    }
    public static boolean ready() { return shader!=null; }
    /** Premultiplied blending; depth tested against the world, never written. */
    static void premultiplied() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);RenderSystem.disableCull();
    }

    public static final class Batch {
        private final BufferBuilder buffer;
        private final Matrix4f matrix;
        final Vec3d camera;
        private Batch(BufferBuilder buffer,Matrix4f matrix,Vec3d camera) { this.buffer=buffer;this.matrix=matrix;this.camera=camera; }
        public void point(Vec3d p,float u,float v,int rgb,float alpha) {
            buffer.vertex(matrix,(float)(p.x-camera.x),(float)(p.y-camera.y),(float)(p.z-camera.z)).texture(u,v)
                .color((rgb>>16)&255,(rgb>>8)&255,rgb&255,Math.max(0,Math.min(255,(int)(alpha*255)))).next();
        }
        /** Quad a-b-c-d with uv (0,0) (1,0) (1,1) (0,1). */
        public void quad(Vec3d a,Vec3d b,Vec3d c,Vec3d d,int rgb,float alpha) {
            point(a,0,0,rgb,alpha);point(b,1,0,rgb,alpha);point(c,1,1,rgb,alpha);point(d,0,1,rgb,alpha);
        }
        /** Quad with explicit uv per corner (used for the world-space circuit coordinates). */
        public void quad(Vec3d a,Vec3d b,Vec3d c,Vec3d d,float[] u,float[] v,int rgb,float alpha) {
            point(a,u[0],v[0],rgb,alpha);point(b,u[1],v[1],rgb,alpha);point(c,u[2],v[2],rgb,alpha);point(d,u[3],v[3],rgb,alpha);
        }
        /** Vertical strip from {@code base} up {@code height}, turned toward the camera; uv.x across, uv.y up. */
        public void upright(Vec3d base,double height,double width,int rgb,float alpha) {
            if(alpha<=0||height<=0)return;
            Vec3d side=camera.subtract(base).crossProduct(UP);
            if(side.lengthSquared()<1e-6)side=new Vec3d(1,0,0);
            side=side.normalize().multiply(width*.5);Vec3d top=base.add(0,height,0);
            quad(base.subtract(side),base.add(side),top.add(side),top.subtract(side),rgb,alpha);
        }
        /** Strip from a to b turned toward the camera around its own axis. */
        public void strip(Vec3d a,Vec3d b,double width,int rgb,float alpha) {
            if(alpha<=0)return;Vec3d axis=b.subtract(a);
            Vec3d side=axis.crossProduct(camera.subtract(a.add(b).multiply(.5)));
            if(side.lengthSquared()<1e-8)return;
            side=side.normalize().multiply(width*.5);
            quad(a.subtract(side),a.add(side),b.add(side),b.subtract(side),rgb,alpha);
        }
        /** Camera-facing square of half-size {@code r}. */
        public void facing(Vec3d p,double r,int rgb,float alpha) {
            if(alpha<=0)return;Vec3d n=camera.subtract(p).normalize(),u=n.crossProduct(UP);
            if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(n).normalize();
            u=u.multiply(r);v=v.multiply(r);
            quad(p.subtract(u).subtract(v),p.add(u).subtract(v),p.add(u).add(v),p.subtract(u).add(v),rgb,alpha);
        }
        /** Horizontal square of half-size {@code r} centred on p. */
        public void flat(Vec3d p,double r,int rgb,float alpha) {
            if(alpha<=0)return;
            quad(p.add(-r,0,-r),p.add(r,0,-r),p.add(r,0,r),p.add(-r,0,r),rgb,alpha);
        }
        /** Camera-facing square whose uv.x runs k..k+1, so a puff shader can pick its own noise per quad. */
        public void facingSeeded(Vec3d p,double r,int rgb,float alpha,int k) {
            if(alpha<=0)return;Vec3d n=camera.subtract(p).normalize(),u=n.crossProduct(UP);
            if(u.lengthSquared()<1e-4)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(n).normalize();
            u=u.multiply(r);v=v.multiply(r);
            point(p.subtract(u).subtract(v),k,0,rgb,alpha);point(p.add(u).subtract(v),k+1,0,rgb,alpha);
            point(p.add(u).add(v),k+1,1,rgb,alpha);point(p.subtract(u).add(v),k,1,rgb,alpha);
        }
        /** Lat-long shell of radius r around c from elevation el0 to el1 (radians); uv.x azimuth, uv.y elevation fraction. */
        public void dome(Vec3d c,double r,int nAz,int nEl,double el0,double el1,int rgb,float alpha) {
            if(alpha<=0||r<=0)return;
            for(int j=0;j<nEl;j++){
                double e0=el0+(el1-el0)*j/nEl,e1=el0+(el1-el0)*(j+1)/nEl;
                float v0=(float)j/nEl,v1=(float)(j+1)/nEl;
                double c0=Math.cos(e0)*r,s0=Math.sin(e0)*r,c1=Math.cos(e1)*r,s1=Math.sin(e1)*r;
                for(int i=0;i<nAz;i++){
                    double a0=i*Math.PI*2/nAz,a1=(i+1)*Math.PI*2/nAz;float u0=(float)i/nAz,u1=(float)(i+1)/nAz;
                    point(c.add(Math.cos(a0)*c0,s0,Math.sin(a0)*c0),u0,v0,rgb,alpha);point(c.add(Math.cos(a1)*c0,s0,Math.sin(a1)*c0),u1,v0,rgb,alpha);
                    point(c.add(Math.cos(a1)*c1,s1,Math.sin(a1)*c1),u1,v1,rgb,alpha);point(c.add(Math.cos(a0)*c1,s1,Math.sin(a0)*c1),u0,v1,rgb,alpha);
                }
            }
        }
        /** Cylinder shell around the vertical axis through c from y0 to y1 with radius {@code radius.apply(v)} at height fraction v; uv.x around, uv.y up. */
        public void cylinder(Vec3d c,double y0,double y1,java.util.function.DoubleUnaryOperator radius,int nAz,int nY,int rgb,float alpha) {
            if(alpha<=0)return;
            for(int j=0;j<nY;j++){
                double v0=(double)j/nY,v1=(double)(j+1)/nY,r0=radius.applyAsDouble(v0),r1=radius.applyAsDouble(v1);
                double ya=y0+(y1-y0)*v0,yb=y0+(y1-y0)*v1;
                for(int i=0;i<nAz;i++){
                    double a0=i*Math.PI*2/nAz,a1=(i+1)*Math.PI*2/nAz;float u0=(float)i/nAz,u1=(float)(i+1)/nAz;
                    point(new Vec3d(c.x+Math.cos(a0)*r0,ya,c.z+Math.sin(a0)*r0),u0,(float)v0,rgb,alpha);point(new Vec3d(c.x+Math.cos(a1)*r0,ya,c.z+Math.sin(a1)*r0),u1,(float)v0,rgb,alpha);
                    point(new Vec3d(c.x+Math.cos(a1)*r1,yb,c.z+Math.sin(a1)*r1),u1,(float)v1,rgb,alpha);point(new Vec3d(c.x+Math.cos(a0)*r1,yb,c.z+Math.sin(a0)*r1),u0,(float)v1,rgb,alpha);
                }
            }
        }
        /** Horizontal ring at c.y between radii r0 and r1; uv.x around, uv.y 0 at r0 and 1 at r1. */
        public void annulus(Vec3d c,double r0,double r1,int n,int rgb,float alpha) {
            if(alpha<=0||r1<=0)return;r0=Math.max(0,r0);
            for(int i=0;i<n;i++){
                double a0=i*Math.PI*2/n,a1=(i+1)*Math.PI*2/n;float u0=(float)i/n,u1=(float)(i+1)/n;
                point(c.add(Math.cos(a0)*r0,0,Math.sin(a0)*r0),u0,0,rgb,alpha);point(c.add(Math.cos(a1)*r0,0,Math.sin(a1)*r0),u1,0,rgb,alpha);
                point(c.add(Math.cos(a1)*r1,0,Math.sin(a1)*r1),u1,1,rgb,alpha);point(c.add(Math.cos(a0)*r1,0,Math.sin(a0)*r1),u0,1,rgb,alpha);
            }
        }
    }
    /** Starts a batch for one mode; uniforms are uploaded when the batch is drawn by {@link #end}. */
    static Batch begin(Matrix4f matrix,Vec3d camera,int mode,float sec,float seed,float p0,float p1,float p2,float p3) {
        return beginAt(camera,matrix,camera,mode,sec,seed,p0,p1,p2,p3);
    }
    /** Like {@link #begin} with a shell centre (world position) for the modes that shade by the offset from it. */
    static Batch beginAt(Vec3d center,Matrix4f matrix,Vec3d camera,int mode,float sec,float seed,float p0,float p1,float p2,float p3) {
        RenderSystem.setShader(()->shader);
        shader.getUniform("Mode").set(mode);shader.getUniform("Age").set(sec);shader.getUniform("Seed").set(seed);
        shader.getUniform("Params").set(p0,p1,p2,p3);
        shader.getUniform("Center").set((float)(center.x-camera.x),(float)(center.y-camera.y),(float)(center.z-camera.z),0f);
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        return new Batch(b,matrix,camera);
    }
    static void end(Batch b) { BufferRenderer.drawWithGlobalProgram(b.buffer.end()); }
}
