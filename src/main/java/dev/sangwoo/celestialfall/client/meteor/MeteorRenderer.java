package dev.sangwoo.celestialfall.client.meteor;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.meteor.MeteorEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * A meteor's rock on the ground (2.49.0): the rock itself (lit by the world), the glow of its seams and cracks (white
 * hot as it lands, cooling to a dull red over a minute or two and never quite out), the gold crystals still in it and
 * the scorch round it. Shaken for a moment when a pickaxe strikes it.
 */
public final class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    static final Identifier ROCK=tex("rock"),ROCK_GLOW=tex("rock_glow"),SEAM=tex("rock_seam"),NODULE=tex("nodule"),NODULE_GLOW=tex("nodule_glow"),SCORCH=tex("scorch"),SCORCH_GLOW=tex("scorch_glow");
    private static final float SCORCH_RADIUS=3.6f;
    private static Identifier tex(String name){return CelestialFall.id("textures/entity/meteor/"+name+".png");}

    public MeteorRenderer(EntityRendererFactory.Context ctx){super(ctx);shadowRadius=0;}
    @Override public Identifier getTexture(MeteorEntity rock){return ROCK;}
    /** The scorch reaches well past the rock's box: keep drawing while any of it is in view. */
    @Override public boolean shouldRender(MeteorEntity rock,Frustum frustum,double x,double y,double z){
        return rock.shouldRender(x,y,z)&&frustum.isVisible(rock.getBoundingBox().expand(SCORCH_RADIUS,.5,SCORCH_RADIUS));
    }
    @Override public void render(MeteorEntity rock,float yaw,float delta,MatrixStack ms,VertexConsumerProvider consumers,int light){
        // until it lands on this client, the fall draws it
        if(MeteorFx.falling(rock.slot(),rock.cycle(),delta))return;
        double age=MeteorClient.clock(delta)-MeteorFx.landedAt(rock,delta);
        drawScorch(ms,consumers,rock.seed(),age,light,1);
        drawRock(ms,consumers,MeteorMesh.of(rock.seed()),rock.fragments(),age,MeteorFx.sinceHit(rock,delta),light,1);
        super.render(rock,yaw,delta,ms,consumers,light);
    }

    /** How hot the rock still is {@code age} ticks after landing (1 while it falls): from white heat to a lasting dull glow. */
    static double heat(double age){return age<0?1:.12+.88*Math.exp(-age/500);}
    /** The colour of something glowing this hot (0 cold red to 1 white). */
    static float[] ember(double h){
        double[][] k={{0,.32,.035,.01},{.3,.78,.14,.02},{.6,1,.44,.08},{.85,1,.74,.3},{1,1,.95,.74}};
        h=Math.max(0,Math.min(1,h));
        for(int i=1;i<k.length;i++)if(h<=k[i][0]){double t=(h-k[i-1][0])/(k[i][0]-k[i-1][0]);return new float[]{(float)(k[i-1][1]+(k[i][1]-k[i-1][1])*t),(float)(k[i-1][2]+(k[i][2]-k[i-1][2])*t),(float)(k[i-1][3]+(k[i][3]-k[i-1][3])*t)};}
        return new float[]{1,.95f,.74f};
    }

    /**
     * The rock with {@code fragments} crystal clusters, {@code age} ticks after landing (below 0 while it falls: white
     * hot all over), {@code sinceHit} ticks after a pickaxe last struck it.
     */
    static void drawRock(MatrixStack ms,VertexConsumerProvider consumers,MeteorMesh mesh,int fragments,double age,double sinceHit,int light,float alpha){
        ms.push();
        if(sinceHit<12){double k=Math.exp(-sinceHit/3)*Math.sin(sinceHit*2.7)*.05;ms.translate(k,0,-k*.6);}
        var e=ms.peek();Matrix4f pos=e.getPositionMatrix();Matrix3f nrm=e.getNormalMatrix();
        int overlay=OverlayTexture.DEFAULT_UV;
        MeteorMesh.emit(consumers.getBuffer(RenderLayer.getEntitySolid(ROCK)),pos,nrm,mesh.rock,1,1,1,1,false,light,overlay);
        double h=heat(age);float[] c=ember(h*.94);
        // the seams between its plates, brightest; the fine cracks; and while it is fresh the whole surface aglow, a
        // crust darkening over it within a few seconds of landing
        float s=(float)(.2+.85*h);
        MeteorMesh.emit(consumers.getBuffer(RenderLayer.getEyes(SEAM)),pos,nrm,mesh.rock,c[0]*s,c[1]*s,c[2]*s,1,true,light,overlay);
        float[] cc=ember(h*.86);float k=(float)(h*.75);
        MeteorMesh.emit(consumers.getBuffer(RenderLayer.getEyes(ROCK_GLOW)),pos,nrm,mesh.rock,cc[0]*k,cc[1]*k,cc[2]*k,1,false,light,overlay);
        double surface=age<0?1:Math.exp(-age/30);
        if(surface>.01){
            float[] sc=ember(.42+.5*surface);float g=(float)(surface*.75);
            MeteorMesh.emit(consumers.getBuffer(RenderLayer.getEyes(SEAM)),pos,nrm,mesh.rock,sc[0]*g,sc[1]*g,sc[2]*g,1,false,light,overlay);
        }
        drawClusters(ms,consumers,mesh,fragments,age,light,1,sinceHit);
        ms.pop();
    }
    static void drawClusters(MatrixStack ms,VertexConsumerProvider consumers,MeteorMesh mesh,int count,double age,int light,float alpha){drawClusters(ms,consumers,mesh,count,age,light,alpha,1e9);}
    /** The gold crystals still in it (the one being mined, the last, shudders when struck), with their soft inner glow. */
    static void drawClusters(MatrixStack ms,VertexConsumerProvider consumers,MeteorMesh mesh,int count,double age,int light,float alpha,double sinceHit){
        int overlay=OverlayTexture.DEFAULT_UV;
        for(int k=0;k<Math.min(count,mesh.clusters.length);k++){
            var cl=mesh.clusters[k];
            ms.push();
            if(k==count-1&&sinceHit<10){
                float bump=(float)(1+.12*Math.exp(-sinceHit/2.5)*Math.cos(sinceHit*2.2));
                ms.translate(cl.x(),cl.y(),cl.z());ms.scale(bump,bump,bump);ms.translate(-cl.x(),-cl.y(),-cl.z());
            }
            var e=ms.peek();
            MeteorMesh.emit(consumers.getBuffer(RenderLayer.getEntitySolid(NODULE)),e.getPositionMatrix(),e.getNormalMatrix(),cl.tris(),alpha,alpha,alpha,1,false,light,overlay);
            float pulse=(float)((.34+.14*Math.sin(age*.07+k*2.1))*alpha);
            MeteorMesh.emit(consumers.getBuffer(RenderLayer.getEyes(NODULE_GLOW)),e.getPositionMatrix(),e.getNormalMatrix(),cl.tris(),pulse,pulse*.74f,pulse*.3f,1,false,light,overlay);
            ms.pop();
        }
    }
    /** One of the pieces it breaks into, drawn from inside too (its broken faces open), still glowing as hot as it was. */
    static void drawPiece(MatrixStack ms,VertexConsumerProvider consumers,float[] tris,double age,int light){
        var e=ms.peek();int overlay=OverlayTexture.DEFAULT_UV;
        MeteorMesh.emit(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(ROCK)),e.getPositionMatrix(),e.getNormalMatrix(),tris,1,1,1,1,false,light,overlay);
        double h=heat(age);float[] c=ember(h);float s=(float)(h*1.1);
        MeteorMesh.emit(consumers.getBuffer(RenderLayer.getEyes(SEAM)),e.getPositionMatrix(),e.getNormalMatrix(),tris,c[0]*s,c[1]*s,c[2]*s,1,true,light,overlay);
    }
    /** The scorch on the ground round it, its embers glowing while it is hot ({@code alpha} fades it out). */
    static void drawScorch(MatrixStack ms,VertexConsumerProvider consumers,int seed,double age,int light,float alpha){
        if(alpha<=.004)return;
        ms.push();ms.translate(0,.03,0);ms.multiply(RotationAxis.POSITIVE_Y.rotation(seed*.37f));
        var e=ms.peek();
        flat(consumers.getBuffer(RenderLayer.getEntityTranslucent(SCORCH)),e,SCORCH_RADIUS,1,1,1,alpha,light);
        double h=heat(Math.max(0,age));float[] c=ember(h);float k=(float)(h*1.1*alpha);
        flat(consumers.getBuffer(RenderLayer.getEyes(SCORCH_GLOW)),e,SCORCH_RADIUS,c[0]*k,c[1]*k,c[2]*k,1,light);
        ms.pop();
    }
    private static void flat(VertexConsumer vc,MatrixStack.Entry e,float r,float red,float green,float blue,float alpha,int light){
        Matrix4f m=e.getPositionMatrix();Matrix3f n=e.getNormalMatrix();int o=OverlayTexture.DEFAULT_UV;
        red=MeteorMesh.unit(red);green=MeteorMesh.unit(green);blue=MeteorMesh.unit(blue);alpha=MeteorMesh.unit(alpha);
        vc.vertex(m,-r,0,-r).color(red,green,blue,alpha).texture(0,0).overlay(o).light(light).normal(n,0,1,0).next();
        vc.vertex(m,-r,0,r).color(red,green,blue,alpha).texture(0,1).overlay(o).light(light).normal(n,0,1,0).next();
        vc.vertex(m,r,0,r).color(red,green,blue,alpha).texture(1,1).overlay(o).light(light).normal(n,0,1,0).next();
        vc.vertex(m,r,0,-r).color(red,green,blue,alpha).texture(1,0).overlay(o).light(light).normal(n,0,1,0).next();
    }
}
