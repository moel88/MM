package dev.sangwoo.celestialfall.client.xchara;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A body drawn again as a flat silhouette of one colour (X Chara's pull, 2.127.0, and their delete slash, 2.133.0): drawn
 * through its own renderer, every layer asked of it the one colour (no texture, no light: the shape of whatever it is),
 * a little lighter where it faces up so its form still reads. No name is drawn over it meanwhile ({@link #drawing}).
 */
public final class FlatBody {
    private FlatBody(){}
    private static boolean drawing;
    /** Whether a body is being drawn again as a flat silhouette now (no name over it then: EntityRenderer.render). */
    public static boolean drawing(){return drawing;}
    /** Every layer asked of it is the one flat colour: a body drawn through it is its silhouette. */
    private static final class Flat implements VertexConsumerProvider,VertexConsumer {
        final VertexConsumer to;final int r,g,b,a;double x,y,z;float ny;
        Flat(VertexConsumer to,int rgb,float alpha){this.to=to;r=rgb>>16&255;g=rgb>>8&255;b=rgb&255;a=Math.max(0,Math.min(255,Math.round(alpha*255)));}
        @Override public VertexConsumer getBuffer(RenderLayer layer){return this;}
        @Override public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;ny=0;return this;}
        @Override public VertexConsumer color(int red,int green,int blue,int alpha){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer normal(float nx,float ny,float nz){this.ny=ny;return this;}
        @Override public void next(){
            float shade=.88f+.12f*MathHelper.clamp(ny,-1,1);
            to.vertex(x,y,z).color(Math.min(255,(int)(r*shade)),Math.min(255,(int)(g*shade)),Math.min(255,(int)(b*shade)),a).next();
        }
        @Override public void fixedColor(int red,int green,int blue,int alpha){}
        @Override public void unfixColor(){}
    }
    /**
     * Draws {@code e} as its flat silhouette of {@code rgb} with its feet at {@code at}, {@code swell} times its size
     * about its middle (so it covers the body), into {@code immediate}'s debug quads (the caller draws them).
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void draw(MinecraftClient mc,MatrixStack m,VertexConsumerProvider.Immediate immediate,Vec3d cam,LivingEntity e,Vec3d at,float delta,int rgb,float swell){draw(mc,m,immediate,cam,e,at,delta,rgb,swell,1);}
    /** The same, as solid as {@code alpha} (2.137.0, X Chara the boss's silhouettes behind it). */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void draw(MinecraftClient mc,MatrixStack m,VertexConsumerProvider.Immediate immediate,Vec3d cam,LivingEntity e,Vec3d at,float delta,int rgb,float swell,float alpha){
        var renderer=(EntityRenderer)mc.getEntityRenderDispatcher().getRenderer(e);
        double half=e.getHeight()*.5;
        m.push();
        m.translate(at.x-cam.x,at.y-cam.y+half,at.z-cam.z);m.scale(swell,swell,swell);m.translate(0,-half,0);
        drawing=true;
        try{renderer.render(e,MathHelper.lerp(delta,e.prevYaw,e.getYaw()),delta,m,new Flat(immediate.getBuffer(RenderLayer.getDebugQuads()),rgb,alpha),0xF000F0);}
        finally{drawing=false;m.pop();}
    }
}
