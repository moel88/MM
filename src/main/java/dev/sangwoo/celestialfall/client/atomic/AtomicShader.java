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
    public static final int CIRCUIT=1,SKY=2,BEAM=3,FLARE=4,DISC=5,CURTAIN=6,ORB=7,STRIATED=8,PILLAR=9;
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
    }
    /** Starts a batch for one mode; uniforms are uploaded when the batch is drawn by {@link #end}. */
    static Batch begin(Matrix4f matrix,Vec3d camera,int mode,float sec,float seed,float p0,float p1,float p2,float p3) {
        RenderSystem.setShader(()->shader);
        shader.getUniform("Mode").set(mode);shader.getUniform("Age").set(sec);shader.getUniform("Seed").set(seed);
        shader.getUniform("Params").set(p0,p1,p2,p3);
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        return new Batch(b,matrix,camera);
    }
    static void end(Batch b) { BufferRenderer.drawWithGlobalProgram(b.buffer.end()); }
}
