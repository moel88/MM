package dev.sangwoo.celestialfall.client.samurai;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * What the samurai's G draws solid in the world (2.143.0), after everyone is drawn: the scabbard of its user's sword
 * (SamuraiKata.sheath: lit as the world lights it there), for every viewer; and, in the flat parts of its cutscene (its
 * two's own frames, where nobody is drawn as themselves), its two as flat shapes of one grey each (drawn again through
 * their own renderers, every layer the one colour, as X Chara's pull draws its silhouette: FlatBody), the disc behind the
 * one marked in the ring's two frames, and the two pale copies of them beside them in its second.
 */
final class SamuraiFlat {
    private SamuraiFlat(){}
    private static final Identifier WHITE=new Identifier("textures/block/white_concrete.png");
    private static boolean drawing;
    /** Whether a body is being drawn again as a flat shape now (no name over it then). */
    static boolean drawing(){return drawing;}

    static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();
        if(SamuraiClient.all().isEmpty()||mc.world==null||!(ctx.consumers() instanceof VertexConsumerProvider.Immediate immediate))return;
        float delta=ctx.tickDelta();Vec3d cam=ctx.camera().getPos();
        var mine=SamuraiClient.cinematic();
        var part=mine==null?null:SamuraiScene.part(mine.sec(delta));
        boolean flat=part!=null&&SamuraiScene.flat(part);
        // the scabbard (not on a flat frame: the shapes there are the bodies')
        if(!flat)for(var d:SamuraiClient.all()){
            float t=d.age(delta),sec=t/20f;
            if(t<0||sec>=SamuraiKata.LENGTH||!d.runs(t)||SamuraiClient.animated(d.user)==null)continue;
            var user=mc.world.getPlayerByUuid(d.user);if(user==null||user.isInvisible())continue;
            if(user==mc.getCameraEntity()&&!ctx.camera().isThirdPerson())continue;
            Vec3d feet=user.getLerpedPos(delta);var off=SamuraiClient.offset(user,delta);if(off!=null)feet=feet.add(off);
            if(feet.squaredDistanceTo(cam)>96*96)continue;
            var s=SamuraiKata.sheath(feet,d.facing(t),sec);
            int light=WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(s[0]));
            sheath(ctx.matrixStack(),immediate.getBuffer(RenderLayer.getEntitySolid(WHITE)),cam,s[0],s[1],light);
        }
        if(!flat)return;
        var d=mine;float sec=d.sec(delta);
        var buffer=immediate.getBuffer(RenderLayer.getDebugQuads());
        var m=ctx.matrixStack().peek().getPositionMatrix();
        Vec3d look=SamuraiScene.sideLook(d.f,d.side),right=SamuraiScene.sideRight(d.f,d.side),up=new Vec3d(0,1,0);
        float[] reel=SamuraiScene.reel(sec);
        Vec3d targetFeet=d.hold.add(0,reel[1],0),middle=d.hold.add(0,.9,0);
        // the ring's two frames: the disc behind the one marked
        if(part==SamuraiScene.Part.RING_WHITE||part==SamuraiScene.Part.RING_BLACK){
            boolean white=part==SamuraiScene.Part.RING_BLACK;
            disc(buffer,m,cam,middle.add(look.multiply(.9)),right,up,white?SamuraiScene.DISC_BLACK:SamuraiScene.DISC_WHITE,white?255:0);
        }
        boolean drew=false;
        for(int who=0;who<2;who++){
            boolean target=who==1;
            float grey=SamuraiScene.body(part,target);if(grey<0)continue;
            LivingEntity e=target?(mc.world.getEntityById(d.target) instanceof LivingEntity x&&x.getUuid().equals(d.targetId)?x:null):mc.world.getPlayerByUuid(d.user);
            if(e==null||e.isInvisible())continue;
            Vec3d at=target?targetFeet:SamuraiScene.userFeet(d.spot,d.hold,d.f,d.side,sec);
            int v=Math.round(grey*255);
            if(target&&part==SamuraiScene.Part.RING_BLACK){
                // the two pale copies beside them, a little behind
                for(int k=-1;k<=1;k+=2)body(mc,ctx.matrixStack(),immediate,cam,e,at.add(right.multiply(k*1.02)).add(look.multiply(.45)),delta,0xd4d4d4);
            }
            body(mc,ctx.matrixStack(),immediate,cam,e,at,delta,v<<16|v<<8|v);
            drew=true;
        }
        if(drew||part==SamuraiScene.Part.RING_WHITE||part==SamuraiScene.Part.RING_BLACK)immediate.draw(RenderLayer.getDebugQuads());
    }

    // ───────────────────────────── the flat shapes ─────────────────────────────

    /** Every layer asked of it is the one flat colour: a body drawn through it is its shape. */
    private static final class Flat implements VertexConsumerProvider,VertexConsumer {
        final VertexConsumer to;final int r,g,b;double x,y,z;
        Flat(VertexConsumer to,int rgb){this.to=to;r=rgb>>16&255;g=rgb>>8&255;b=rgb&255;}
        @Override public VertexConsumer getBuffer(RenderLayer layer){return this;}
        @Override public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        @Override public VertexConsumer color(int red,int green,int blue,int alpha){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer normal(float nx,float ny,float nz){return this;}
        @Override public void next(){to.vertex(x,y,z).color(r,g,b,255).next();}
        @Override public void fixedColor(int red,int green,int blue,int alpha){}
        @Override public void unfixColor(){}
    }
    /** Draws {@code e} as its flat shape of {@code rgb} with its feet at {@code at}, into {@code immediate}'s debug quads (the caller draws them). */
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void body(MinecraftClient mc,MatrixStack m,VertexConsumerProvider.Immediate immediate,Vec3d cam,LivingEntity e,Vec3d at,float delta,int rgb){
        var renderer=(EntityRenderer)mc.getEntityRenderDispatcher().getRenderer(e);
        m.push();
        m.translate(at.x-cam.x,at.y-cam.y,at.z-cam.z);
        drawing=true;
        try{renderer.render(e,MathHelper.lerp(delta,e.prevYaw,e.getYaw()),delta,m,new Flat(immediate.getBuffer(RenderLayer.getDebugQuads()),rgb),0xF000F0);}
        finally{drawing=false;m.pop();}
    }
    /** A flat disc of one grey facing the side shots, its middle at {@code c}. */
    private static void disc(VertexConsumer buffer,org.joml.Matrix4f m,Vec3d cam,Vec3d c,Vec3d u,Vec3d v,double radius,int grey){
        int n=64;
        for(int i=0;i<n;i+=2){
            Vec3d[] p={c,rim(c,u,v,radius,i,n),rim(c,u,v,radius,i+1,n),rim(c,u,v,radius,i+2,n)};
            for(Vec3d q:p)buffer.vertex(m,(float)(q.x-cam.x),(float)(q.y-cam.y),(float)(q.z-cam.z)).color(grey,grey,grey,255).next();
        }
    }
    private static Vec3d rim(Vec3d c,Vec3d u,Vec3d v,double r,int i,int n){double a=i*Math.PI*2/n;return c.add(u.multiply(Math.cos(a)*r)).add(v.multiply(Math.sin(a)*r));}

    // ───────────────────────────── the scabbard ─────────────────────────────

    /** The scabbard's colours: its lacquer, the cord's band a third of the way down, its cap. */
    static final int LACQUER=0x15171b,BAND=0x3a2e22,CAP=0x2a2d33;
    /**
     * The scabbard: a slim dark box from its mouth at {@code mouth} along {@code way}, a little curved band near the
     * mouth and a cap at its end, as wide across as deep it is thin (its wide faces to the sides of the way it hangs).
     */
    private static void sheath(MatrixStack matrices,VertexConsumer buffer,Vec3d cam,Vec3d mouth,Vec3d way,int light){
        Vec3d side=way.crossProduct(new Vec3d(0,1,0));if(side.lengthSquared()<1e-5)side=new Vec3d(1,0,0);side=side.normalize();
        Vec3d top=side.crossProduct(way).normalize();
        var entry=matrices.peek();
        Vec3d o=mouth.subtract(cam);
        double L=SamuraiKata.SHEATH,w=SamuraiKata.SHEATH_W,dp=SamuraiKata.SHEATH_D;
        box(entry,buffer,o,way,top,side,0,L*.9,w,dp,LACQUER,light);
        box(entry,buffer,o,way,top,side,L*.9,L,w*.86,dp*.9,CAP,light);
        box(entry,buffer,o,way,top,side,-.01,.05,w*1.18,dp*1.25,CAP,light);
        box(entry,buffer,o,way,top,side,L*.24,L*.29,w*1.12,dp*1.2,BAND,light);
    }
    /** A box from {@code a} to {@code b} along {@code way}, {@code hu} either way along {@code u} and {@code hv} along {@code v}. */
    private static void box(MatrixStack.Entry entry,VertexConsumer buffer,Vec3d o,Vec3d way,Vec3d u,Vec3d v,double a,double b,double hu,double hv,int rgb,int light){
        Vec3d[] c=new Vec3d[8];
        for(int i=0;i<8;i++)c[i]=o.add(way.multiply((i&4)==0?a:b)).add(u.multiply((i&1)==0?-hu:hu)).add(v.multiply((i&2)==0?-hv:hv));
        int[][] faces={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
        Vec3d[] normals={way.negate(),way,v.negate(),v,u.negate(),u};
        for(int f=0;f<6;f++){
            var n=new Vector3f((float)normals[f].x,(float)normals[f].y,(float)normals[f].z);
            for(int i:faces[f])buffer.vertex(entry.getPositionMatrix(),(float)c[i].x,(float)c[i].y,(float)c[i].z).color(rgb>>16&255,rgb>>8&255,rgb&255,255)
                .texture(.5f,.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(),n.x,n.y,n.z).next();
        }
    }
}
