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
    private static float hot;
    /** Extra light (above white) on the hottest streaks of the R slashes, for the effect layer's glow pass; 0 off. */
    public static void hot(float amount){hot=amount;}
    private static BufferBuilder begin(float age,int mode){
        RenderSystem.setShader(()->shader);shader.getUniform("Age").set(age);shader.getUniform("Mode").set(mode);
        var h=shader.getUniform("Hot");if(h!=null)h.set(hot);
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
        if(pool){var b=begin(age,1);quad(b,m,center.add(0,.025,0),X.multiply(radius*.95),Z.multiply(radius*.82),0,alpha);end(b);}
        if(pool&&age>=80)return;
        double strength=pool?3.4:3.2;
        double burst=1.0+.55*(1-dev.sangwoo.celestialfall.client.kageno.Ease.smooth(age,0,10));
        int count=CelestialFall.CONFIG.visualQuality==0?8:12;var flames=new ArrayList<Flame>();
        float tail=pool?1-dev.sangwoo.celestialfall.client.kageno.Ease.smooth(age,62,78):1-dev.sangwoo.celestialfall.client.kageno.Ease.smooth(age,74,98);
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
            double size=radius*(.35+dev.sangwoo.celestialfall.client.kageno.Ease.smooth(age,0,4)*.18);b=begin(age,3);quad(b,m,p,u.multiply(size),v.multiply(size),0,alpha*(1-dev.sangwoo.celestialfall.client.kageno.Ease.smooth(age,1,6)));end(b);
        }
    }
    /** Camera-facing squares in one shader mode (3 glow, 7 smoke, 9 blast cloud, 10 dark orb), drawn back to front. */
    public static void billboards(Matrix4f m,Vec3d camera,int mode,List<Vec3d> pts,List<Double> sizes,List<Float> alphas,float age){
        if(shader==null||pts.isEmpty())return;
        Integer[] order=new Integer[pts.size()];for(int i=0;i<order.length;i++)order[i]=i;
        Arrays.sort(order,Comparator.comparingDouble((Integer i)->pts.get(i).squaredDistanceTo(camera)).reversed());
        var b=begin(age,mode);
        for(int i:order){float a=alphas.get(i);if(a<=0)continue;Vec3d p=pts.get(i);Vec3d n=camera.subtract(p).normalize(),u=n.crossProduct(UP).normalize();if(u.lengthSquared()<.1)u=X;Vec3d v=u.crossProduct(n).normalize();double s=sizes.get(i);
            quad(b,m,p,u.multiply(s),v.multiply(s),i*37+11,a);}
        end(b);
    }
    /**
     * Fire cells (shader mode 13), drawn back to front, each on three upright planes 60° apart whose turn comes from the
     * cell's seed: like the game's own fire block they stay put in the world instead of turning to follow the camera,
     * and from any side at least one of them faces the viewer. Each cell is {size, alpha, heat, wisp, seed}: heat runs
     * from black smoke (0) through blue and violet to magenta and white-hot pink (1), wisp from a round billow (0) to a
     * curling flame (1).
     */
    public static void cells(Matrix4f m,Vec3d camera,List<Vec3d> pts,List<float[]> cells,float age){cells(m,camera,pts,cells,age,13);}
    /**
     * Dust puffs on the same fixed planes as the fire cells (shader mode 14): each cell is {size, alpha, violet cast,
     * thinning, seed}.
     */
    public static void dust(Matrix4f m,Vec3d camera,List<Vec3d> pts,List<float[]> cells,float age){cells(m,camera,pts,cells,age,14);}
    private static void cells(Matrix4f m,Vec3d camera,List<Vec3d> pts,List<float[]> cells,float age,int mode){
        if(shader==null||pts.isEmpty())return;
        Integer[] order=new Integer[pts.size()];for(int i=0;i<order.length;i++)order[i]=i;
        Arrays.sort(order,Comparator.comparingDouble((Integer i)->pts.get(i).squaredDistanceTo(camera)).reversed());
        var b=begin(age,mode);float[][] uv={{0,0},{1,0},{1,1},{0,1}};
        for(int i:order){
            float[] c=cells.get(i);if(c[1]<=0||c[0]<=0)continue;Vec3d p=pts.get(i);
            int seed=(int)c[4],heat=Math.round(Math.max(0,Math.min(1,c[2]))*255),wisp=Math.round(Math.max(0,Math.min(1,c[3]))*255),a=Math.max(0,Math.min(255,(int)(c[1]*255)));
            // The same shape on every plane: seen from any side their union reads as one puff of flame (at least 87% of its width).
            Vec3d v=UP.multiply(c[0]);
            for(Vec3d plane:cellPlanes(seed)){
                Vec3d u=plane.multiply(c[0]);
                Vec3d[] corner={p.subtract(u).subtract(v),p.add(u).subtract(v),p.add(u).add(v),p.subtract(u).add(v)};
                for(int q=0;q<4;q++)b.vertex(m,(float)corner[q].x,(float)corner[q].y,(float)corner[q].z).texture(uv[q][0],uv[q][1]).color(seed&255,heat,wisp,a).next();
            }
        }
        end(b);
    }
    /** Horizontal directions of a fire cell's three upright planes: 60° apart and turned by its seed alone, never by the camera. */
    public static Vec3d[] cellPlanes(int seed){
        double turn=((seed*2654435761L)&0xffff)/65536.0*Math.PI/3;
        Vec3d[] out=new Vec3d[3];
        for(int k=0;k<3;k++){double a=turn+k*Math.PI/3;out[k]=new Vec3d(Math.cos(a),0,Math.sin(a));}
        return out;
    }
    /** T/T+ use curling 3D flame surfaces; other modes retain their sprite treatment. */
    public static void tongues(Matrix4f m,Vec3d camera,int mode,List<Vec3d> roots,List<double[]> sizes,List<Float> alphas,float age){
        if(shader==null||roots.isEmpty())return;
        if(mode==11){volumes(m,camera,roots,sizes,alphas,age);return;}
        Integer[] order=new Integer[roots.size()];for(int i=0;i<order.length;i++)order[i]=i;
        Arrays.sort(order,Comparator.comparingDouble((Integer i)->roots.get(i).squaredDistanceTo(camera)).reversed());
        var b=begin(age,mode);
        for(int i:order){float a=alphas.get(i);if(a<=0)continue;Vec3d r=roots.get(i);double w=sizes.get(i)[0],h=sizes.get(i)[1];
            Vec3d right=camera.subtract(r).crossProduct(UP).normalize();if(right.lengthSquared()<.1)right=X;
            quad(b,m,r.add(0,h*.5,0),right.multiply(w),UP.multiply(h*.5),i*53+7,a);}
        end(b);
    }
    private static Vec3d flameVertex(Vec3d root,double width,double height,double y,double angle,double time,int seed){
        double phase=time*.105+seed*.73;
        double bend=width*y*y;
        double cx=Math.sin(phase+y*7.1)*bend*.72,cz=Math.cos(phase*.83+y*6.3)*bend*.62;
        // Rounded, rolling lobes instead of a straight cone. The tip still curls with the flow.
        double round=Math.pow(Math.max(0,Math.sin(Math.PI*y)),.52);
        double profile=(.25*Math.pow(1-y,2)+round*(1-.24*y))*(.85+.15*Math.sin(y*15-phase));
        double lobe=1+.21*Math.sin(angle*3+y*10-phase)+.08*Math.cos(angle*5-y*13+phase);
        double r=width*.64*profile*lobe;
        return root.add(cx+Math.cos(angle)*r,height*y,cz+Math.sin(angle)*r);
    }
    private static void volumes(Matrix4f m,Vec3d camera,List<Vec3d> roots,List<double[]> sizes,List<Float> alphas,float age){
        Integer[] order=new Integer[roots.size()];for(int i=0;i<order.length;i++)order[i]=i;
        Arrays.sort(order,Comparator.comparingDouble((Integer i)->roots.get(i).squaredDistanceTo(camera)).reversed());
        int sides=CelestialFall.CONFIG.visualQuality==0?12:24,rings=CelestialFall.CONFIG.visualQuality==0?12:22;
        // Draw only the outside, with depth writes: two sides of a translucent shell otherwise
        // paint over each other in strips and expose the quad triangulation during camera moves.
        RenderSystem.enableCull();RenderSystem.depthMask(true);
        var b=begin(age,12);
        for(int i:order){
            float alpha=alphas.get(i);if(alpha<=0)continue;
            Vec3d root=roots.get(i);double width=sizes.get(i)[0],height=sizes.get(i)[1];int seed=i*53+7;
            double horizontal=Math.hypot(camera.x-root.x,camera.z-root.z);
            // A camera inside the fire should see through its nearest plume, not hit a solid wall.
            alpha*=.22f+.78f*dev.sangwoo.celestialfall.client.kageno.Ease.smooth((float)horizontal,(float)(width*.55),(float)(width*1.8));
            for(int j=0;j<rings;j++)for(int k=0;k<sides;k++){
                double y0=j/(double)rings,y1=(j+1)/(double)rings,u0=k/(double)sides,u1=(k+1)/(double)sides;
                point(b,m,flameVertex(root,width,height,y0,u0*Math.PI*2,age,seed),(float)u0,(float)y0,seed,alpha);
                point(b,m,flameVertex(root,width,height,y1,u0*Math.PI*2,age,seed),(float)u0,(float)y1,seed,alpha);
                point(b,m,flameVertex(root,width,height,y1,u1*Math.PI*2,age,seed),(float)u1,(float)y1,seed,alpha);
                point(b,m,flameVertex(root,width,height,y0,u1*Math.PI*2,age,seed),(float)u1,(float)y0,seed,alpha);
            }
        }
        end(b);RenderSystem.depthMask(false);RenderSystem.disableCull();
    }
    /** Flat ground decal in mode 1 (black ink pool with violet veins). */
    public static void pool(Matrix4f m,Vec3d center,Vec3d u,Vec3d v,float age,float alpha){
        if(shader==null||alpha<=0)return;var b=begin(age,1);quad(b,m,center,u,v,0,alpha);end(b);
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
    // ── Brush strokes (shader mode 6) and smoke (mode 7) used by the rebuilt Kageno skills ──
    private static BufferBuilder strokes;private static Matrix4f strokeMatrix;
    public static boolean beginStrokes(Matrix4f m,float age){if(shader==null)return false;strokes=begin(age,6);strokeMatrix=m;return true;}
    public static void endStrokes(){if(strokes!=null){end(strokes);strokes=null;}}
    /** Circular band in the plane (u,v): {@code start..start+length} radians, head at the end. */
    public static void stroke(Vec3d o,Vec3d u,Vec3d v,double radius,double start,double length,double width,int seed,float alpha){
        if(strokes==null||alpha<=0||length==0)return;int n=36;
        for(int i=0;i<n;i++){
            double t0=i/(double)n,t1=(i+1)/(double)n,a0=start+length*t0,a1=start+length*t1;
            Vec3d r0=u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))),r1=u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
            point(strokes,strokeMatrix,o.add(r0.multiply(radius-width*.5)),(float)t0,0,seed,alpha);
            point(strokes,strokeMatrix,o.add(r1.multiply(radius-width*.5)),(float)t1,0,seed,alpha);
            point(strokes,strokeMatrix,o.add(r1.multiply(radius+width*.5)),(float)t1,1,seed,alpha);
            point(strokes,strokeMatrix,o.add(r0.multiply(radius+width*.5)),(float)t0,1,seed,alpha);
        }
    }
    // ── Brush crescents (shader mode 8): styles 0 M1 hit 1, 1 M1 hit 2, 2 spin ring, 3 E finisher, 4 white-lavender ──
    private static BufferBuilder crescents;private static Matrix4f crescentMatrix;
    /** Alpha multiplier for the second, additive glow pass of the crescents. */
    public static float crescentGain=1;
    public static boolean beginCrescents(Matrix4f m,float age){if(shader==null)return false;crescents=begin(age,8);crescentMatrix=m;return true;}
    public static void endCrescents(){if(crescents!=null){end(crescents);crescents=null;}}
    private static void cpoint(Vec3d p,float u,float v,int code,float erase,float taper,float alpha){
        crescents.vertex(crescentMatrix,(float)p.x,(float)p.y,(float)p.z).texture(u,v)
            .color(code&255,Math.max(0,Math.min(255,Math.round(erase*255))),Math.max(0,Math.min(255,Math.round(taper*255))),Math.max(0,Math.min(255,(int)(alpha*crescentGain*255)))).next();
    }
    /**
     * Brush crescent in the plane (u,v) (point = o + (u cos a + v sin a) r) from {@code startDeg}
     * sweeping {@code lengthDeg}; the band's outer edge sits at {@code outer}, its widest part is
     * {@code width} deep. {@code erase} is the fraction already eaten from the start end and
     * {@code taper} 0 = widest at the start, .5 = even, 1 = widest at the end.
     */
    public static void crescent(Vec3d o,Vec3d u,Vec3d v,double outer,double width,double startDeg,double lengthDeg,int style,int seed,float erase,float taper,float alpha){
        crescent(o,u,v,outer,width,startDeg,lengthDeg,style,seed,erase,taper,alpha,0);
    }
    /** As above; {@code bevel} drops the inner edge out of the plane (fraction of the width) so a band seen edge-on still reads as a thick stroke. */
    public static void crescent(Vec3d o,Vec3d u,Vec3d v,double outer,double width,double startDeg,double lengthDeg,int style,int seed,float erase,float taper,float alpha,double bevel){
        if(crescents==null||alpha<=0||erase>=1)return;int n=48,code=(style&15)*16+(seed&15);
        double inner=Math.max(0,outer-width);Vec3d drop=u.crossProduct(v).normalize().multiply(-bevel*width);
        for(int i=0;i<n;i++){
            double t0=i/(double)n,t1=(i+1)/(double)n,a0=Math.toRadians(startDeg+lengthDeg*t0),a1=Math.toRadians(startDeg+lengthDeg*t1);
            Vec3d r0=u.multiply(Math.cos(a0)).add(v.multiply(Math.sin(a0))),r1=u.multiply(Math.cos(a1)).add(v.multiply(Math.sin(a1)));
            cpoint(o.add(r0.multiply(inner)).add(drop),(float)t0,0,code,erase,taper,alpha);cpoint(o.add(r1.multiply(inner)).add(drop),(float)t1,0,code,erase,taper,alpha);
            cpoint(o.add(r1.multiply(outer)),(float)t1,1,code,erase,taper,alpha);cpoint(o.add(r0.multiply(outer)),(float)t0,1,code,erase,taper,alpha);
        }
    }
    /**
     * A brush band along any swept path (a sword's own swing): {@code inner[i]}..{@code outer[i]} across it, {@code along[i]}
     * from 0 at the tail to 1 at the head along it ({@code n} points), drawn like a crescent of {@code style}.
     */
    public static void ribbon(Vec3d[] inner,Vec3d[] outer,float[] along,int n,int style,int seed,float erase,float taper,float alpha){
        if(crescents==null||alpha<=0||erase>=1||n<2)return;int code=(style&15)*16+(seed&15);
        for(int i=0;i+1<n;i++){
            cpoint(inner[i],along[i],0,code,erase,taper,alpha);cpoint(inner[i+1],along[i+1],0,code,erase,taper,alpha);
            cpoint(outer[i+1],along[i+1],1,code,erase,taper,alpha);cpoint(outer[i],along[i],1,code,erase,taper,alpha);
        }
    }
    /**
     * Ink ribbons (2.77.0, Lee So-sang's slashes): the shader's mode 16, drawn in a pass of their own after the crescents.
     * {@link #inkRibbon} lays a band along a swept path as {@link #ribbon} does.
     */
    private static BufferBuilder inks;private static Matrix4f inkMatrix;
    public static boolean beginInk(Matrix4f m,float age){if(shader==null)return false;inks=begin(age,16);inkMatrix=m;return true;}
    public static void endInk(){if(inks!=null){end(inks);inks=null;}}
    private static void ipoint(Vec3d p,float u,float v,int code,float erase,float ink,float alpha){
        inks.vertex(inkMatrix,(float)p.x,(float)p.y,(float)p.z).texture(u,v)
            .color(code&255,Math.max(0,Math.min(255,Math.round(erase*255))),Math.max(0,Math.min(255,Math.round(ink*255))),Math.max(0,Math.min(255,(int)(alpha*crescentGain*255)))).next();
    }
    /**
     * A band of blue light outlined in ink along a swept path: {@code inner[i]}..{@code outer[i]} across it (the outer
     * edge the cutting edge), {@code along[i]} from 0 at the tail to 1 at the head ({@code n} points). {@code style}: 0 a
     * cut (widest at its head), 1 a ring (even), 2 a beam (pointed at both ends), 3 the finisher's (thicker); {@code erase}
     * the share eaten from the tail, {@code ink} how far it has turned to ink (0 light, 1 ink breaking up).
     */
    public static void inkRibbon(Vec3d[] inner,Vec3d[] outer,float[] along,int n,int style,int seed,float erase,float ink,float alpha){
        if(inks==null||alpha<=0||erase>=1||n<2)return;int code=(style&15)*16+(seed&15);
        for(int i=0;i+1<n;i++){
            ipoint(inner[i],along[i],0,code,erase,ink,alpha);ipoint(inner[i+1],along[i+1],0,code,erase,ink,alpha);
            ipoint(outer[i+1],along[i+1],1,code,erase,ink,alpha);ipoint(outer[i],along[i],1,code,erase,ink,alpha);
        }
    }
    /** Straight band a→b, width measured along {@code side}. */
    public static void streak(Vec3d a,Vec3d b,Vec3d side,double width,int seed,float alpha){
        if(strokes==null||alpha<=0)return;Vec3d s=side.normalize().multiply(width*.5);
        point(strokes,strokeMatrix,a.subtract(s),0,0,seed,alpha);point(strokes,strokeMatrix,b.subtract(s),1,0,seed,alpha);
        point(strokes,strokeMatrix,b.add(s),1,1,seed,alpha);point(strokes,strokeMatrix,a.add(s),0,1,seed,alpha);
    }
    public static void smoke(Matrix4f m,Vec3d camera,java.util.List<Vec3d> points,java.util.List<Double> sizes,float age,float alpha){
        if(shader==null||alpha<=0||points.isEmpty())return;var b=begin(age,7);
        for(int i=0;i<points.size();i++){Vec3d p=points.get(i);Vec3d n=camera.subtract(p).normalize(),u=n.crossProduct(UP).normalize();if(u.lengthSquared()<.1)u=X;Vec3d v=u.crossProduct(n).normalize();double s=sizes.get(i);
            quad(b,m,p,u.multiply(s),v.multiply(s),i*37+11,alpha);}
        end(b);
    }
}
