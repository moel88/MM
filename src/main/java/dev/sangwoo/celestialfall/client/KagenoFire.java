package dev.sangwoo.celestialfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.*;

/** Analytic animated fire: scrolling turbulence, torn curls, hot cores and a marbled black pool. */
public final class KagenoFire {
    private static ShaderProgram shader;
    private static final Vec3d UP=new Vec3d(0,1,0),X=new Vec3d(1,0,0),Z=new Vec3d(0,0,1);
    private KagenoFire(){}
    public static void initialize(){CoreShaderRegistrationCallback.EVENT.register(c->c.register(CelestialFall.id("kageno_flame"),VertexFormats.POSITION_TEXTURE_COLOR,s->shader=s));}
    public static boolean ready(){return shader!=null;}
    private static BufferBuilder begin(float age,int mode){
        RenderSystem.setShader(()->shader);shader.getUniform("Age").set(age);shader.getUniform("Mode").set(mode);
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);return b;
    }
    private static void end(BufferBuilder b){BufferRenderer.drawWithGlobalProgram(b.end());}
    private static void point(BufferBuilder b,Matrix4f m,Vec3d p,float u,float v,int seed,float alpha){b.vertex(m,(float)p.x,(float)p.y,(float)p.z).texture(u,v).color(seed&255,255,255,Math.max(0,Math.min(255,(int)(alpha*255)))).next();}
    private static void quad(BufferBuilder b,Matrix4f m,Vec3d center,Vec3d u,Vec3d v,int seed,float alpha){
        point(b,m,center.subtract(u).subtract(v),0,0,seed,alpha);point(b,m,center.add(u).subtract(v),1,0,seed,alpha);
        point(b,m,center.add(u).add(v),1,1,seed,alpha);point(b,m,center.subtract(u).add(v),0,1,seed,alpha);
    }
    private record Flame(Vec3d root,double width,double height,int seed,float alpha){}
    public static void field(Matrix4f m,Vec3d camera,Vec3d center,float age,double radius,float alpha,boolean pool){
        if(shader==null||alpha<=0)return;
        if(pool){var b=begin(age,1);quad(b,m,center.add(0,.025,0),X.multiply(radius*1.14),Z.multiply(radius*1.14),0,alpha);end(b);}
        double strength=pool?3.4:3.2;
        double burst=1.0+.55*(1-KagenoMotion.smooth(age,0,10));
        int count=CelestialFall.CONFIG.visualQuality==0?8:12;var flames=new ArrayList<Flame>();
        float tail=1-KagenoMotion.smooth(age,74,98);
        for(int i=0;i<count;i++){
            double theta=i*2.39996,rad=radius*Math.sqrt((i+.4)/count)*.85;
            Vec3d p=center.add(Math.cos(theta)*rad,0,Math.sin(theta)*rad);
            double height=(.65+.35*(.5+.5*Math.sin(i*4.18+age*.13)))*strength*burst*(radius/4+.25);
            flames.add(new Flame(p,.65+radius*.23,height,i*79,alpha*tail));
        }
        // Detached curling tongues travel out of the blast, instead of repeating fixed cones.
        for(int i=0;i<(CelestialFall.CONFIG.visualQuality==0?3:5);i++){
            double life=(age*.024+i*.618)%1,angle=i*2.39996,rad=radius*(.6+life*.5);
            Vec3d p=center.add(Math.cos(angle)*rad,.2+life*1.1,Math.sin(angle)*rad);
            flames.add(new Flame(p,.25+radius*.09,(.19+radius*.08)*strength,i*61+41,alpha*tail*(float)Math.sin(life*Math.PI)*.85f));
        }
        flames.sort(Comparator.comparingDouble((Flame f)->f.root.squaredDistanceTo(camera)).reversed());
        var b=begin(age,5);
        for(var f:flames){Vec3d right=camera.subtract(f.root).crossProduct(UP).normalize();if(right.lengthSquared()<.1)right=X;
            quad(b,m,f.root.add(0,f.height*.5,0),right.multiply(f.width),UP.multiply(f.height*.5),f.seed,f.alpha);
        }end(b);
        if(age<6){
            Vec3d p=center.add(0,1.1,0),normal=camera.subtract(p).normalize(),u=normal.crossProduct(UP).normalize();if(u.lengthSquared()<.1)u=X;Vec3d v=u.crossProduct(normal).normalize();
            double size=radius*(.35+KagenoMotion.smooth(age,0,4)*.18);b=begin(age,3);quad(b,m,p,u.multiply(size),v.multiply(size),0,alpha*(1-KagenoMotion.smooth(age,1,6)));end(b);
        }
    }
    /** Ragged flame fragments on the rush's curved pressure shell, rather than a stationary field. */
    public static void rush(Matrix4f m,Vec3d camera,Vec3d origin,Vec3d forward,float age,float alpha){
        if(shader==null||alpha<=0)return;
        Vec3d right=new Vec3d(forward.z,0,-forward.x);var b=begin(age,5);
        int count=CelestialFall.CONFIG.visualQuality==0?7:12;
        for(int i=0;i<count;i++){
            double angle=i*2.39996+age*.19,r=1.25+.24*Math.sin(i*3.8+age*.21);
            Vec3d p=origin.add(forward.multiply(.5-(i%4)*.45)).add(right.multiply(Math.cos(angle)*r)).add(0,.85+Math.sin(angle)*r*.7,0);
            Vec3d view=camera.subtract(p).normalize(),u=view.crossProduct(UP).normalize();if(u.lengthSquared()<.1)u=right;
            quad(b,m,p,u.multiply(.38+(i%3)*.1),UP.multiply(.45),i*43,alpha*.86f);
        }end(b);
    }
    /** Soft world-space energy sheath with view-dependent edges, never billboard flames on the head. */
    public static void overdrive(Matrix4f m,Vec3d camera,Vec3d origin,float age,float alpha){
        if(shader==null)return;var b=begin(age,4);
        for(int i=0;i<32;i++){
            double a=i*Math.PI/16,d=(i+1)*Math.PI/16;
            Vec3d n=new Vec3d(Math.cos(a),0,Math.sin(a)),q=new Vec3d(Math.cos(d),0,Math.sin(d)),view=camera.subtract(origin).multiply(1,0,1).normalize();
            float edge=(float)Math.pow(1-Math.abs(n.dotProduct(view)),1.5),edge2=(float)Math.pow(1-Math.abs(q.dotProduct(view)),1.5);
            Vec3d p0=origin.add(n.multiply(.52)).add(0,.05,0),p1=origin.add(q.multiply(.52)).add(0,.05,0),p2=origin.add(q.multiply(.45)).add(0,2.05,0),p3=origin.add(n.multiply(.45)).add(0,2.05,0);
            point(b,m,p0,i/32f,0,0,alpha*(.12f+.88f*edge));point(b,m,p1,(i+1)/32f,0,0,alpha*(.12f+.88f*edge2));point(b,m,p2,(i+1)/32f,1,0,alpha*(.12f+.88f*edge2));point(b,m,p3,i/32f,1,0,alpha*(.12f+.88f*edge));
        }end(b);
    }
}
