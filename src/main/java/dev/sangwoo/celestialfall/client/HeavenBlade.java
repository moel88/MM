package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.HeavenTimeline;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/** Actual render mesh: a long double-edged sword, not an enlarged arrowhead. */
public final class HeavenBlade {
    public record Face(Vec3d a,Vec3d b,Vec3d c,Vec3d d,int color) {}
    public static final double[] LEVELS={0,24,58,100,138,240,400,580,680,720};
    public static final double TOTAL_HEIGHT=920,GUARD_HALF_WIDTH=156;
    public static final List<Face> FACES=build();
    private HeavenBlade() {}
    public static double exposedHeight(float age) {
        double through=300+(260-HeavenTimeline.tipHeight(age))/Math.cos(HeavenTimeline.TILT);
        return Math.min(TOTAL_HEIGHT,through+HeavenTimeline.ease(age,HeavenTimeline.IMPACT,HeavenTimeline.IMPACT+45)*TOTAL_HEIGHT);
    }
    /** The portion still above the cloud gate emerges as the sword descends. */
    public static List<Face> visibleFaces(float age) {
        double height=exposedHeight(age);if(height>=TOTAL_HEIGHT)return FACES;
        List<Face> out=new ArrayList<>();
        for(Face f:FACES) {
            var input=List.of(f.a(),f.b(),f.c(),f.d());
            if(input.stream().allMatch(v->v.y<=height)) { out.add(f);continue; }
            if(input.stream().allMatch(v->v.y>height))continue;
            List<Vec3d> clipped=new ArrayList<>();Vec3d last=f.d();
            for(Vec3d next:input) {
                if((last.y<=height)!=(next.y<=height))clipped.add(last.lerp(next,(height-last.y)/(next.y-last.y)));
                if(next.y<=height)clipped.add(next);last=next;
            }
            for(int i=1;i+1<clipped.size();i++)out.add(new Face(clipped.get(0),clipped.get(i),clipped.get(i+1),clipped.get(i+1),f.color()));
        }
        return out;
    }
    public static double faceDepth(double x,double y) {
        double w=HeavenTimeline.halfWidth(y);
        if(w<.001)return 0;
        double p=Math.min(1,Math.abs(x)/w);
        return Math.min(1,y/35)*(p<.8?14-5*p:50*(1-p));
    }
    private static Vec3d v(double x,double y,int side,double lift) {
        return new Vec3d(x,y,side*(faceDepth(x,y)+lift));
    }
    private static List<Face> build() {
        List<Face> faces=new ArrayList<>();
        for(int side:new int[]{-1,1})for(int half:new int[]{-1,1})for(int n=1;n<LEVELS.length;n++) {
            double y0=LEVELS[n-1],y1=LEVELS[n],w0=HeavenTimeline.halfWidth(y0),w1=HeavenTimeline.halfWidth(y1);
            double[] xs={0,.8,1};
            for(int strip=0;strip<2;strip++) {
                int color=strip==1?(half==1?0xFFE4A6:0xF3B360):(half==1?0xD48C53:0xB96841);
                faces.add(new Face(v(half*w0*xs[strip],y0,side,0),v(half*w0*xs[strip+1],y0,side,0),
                    v(half*w1*xs[strip+1],y1,side,0),v(half*w1*xs[strip],y1,side,0),color));
            }
        }
        for(int side:new int[]{-1,1})for(int half:new int[]{-1,1}) {
            // Long edge channels and centre fuller make the parallel blade legible even from far away.
            for(int i=2;i<LEVELS.length;i++) {
                double y0=LEVELS[i-1],y1=LEVELS[i];
                groove(faces,half*HeavenTimeline.halfWidth(y0)*.73,y0,half*HeavenTimeline.halfWidth(y1)*.73,y1,2.2,side);
            }
            groove(faces,half*5,76,half*5,702,2.5,side);
            // The lower engraving flows out of the point; the upper blade has repeated geometric inlays.
            for(int row=0;row<4;row++) {
                double base=110+row*143;
                double[][] p={{12,base},{12,base+53},{23,base+70},{23,base+107},{43,base+125},{47,base+110},{47,base+64},{35,base+49},{35,base+12}};
                for(int i=1;i<p.length;i++)groove(faces,half*p[i-1][0],p[i-1][1],half*p[i][0],p[i][1],3,side);
                groove(faces,half*28,base+23,half*28,base+65,1.6,side);
            }
            double[][] tip={{0,17},{17,47},{37,78},{46,109}};
            for(int i=1;i<tip.length;i++)groove(faces,half*tip[i-1][0],tip[i-1][1],half*tip[i][0],tip[i][1],2.3,side);
        }
        // Swept crossguard, blue inset, an unmistakable separate grip, and a faceted pommel.
        prism(faces,new double[][]{{-156,750},{-149,728},{-74,708},{-27,710},{0,720},{27,710},{74,708},{149,728},{156,750},{91,738},{26,735},{-26,735},{-91,738}},18,0xE9B865);
        prism(faces,new double[][]{{-65,719},{-16,721},{0,744},{16,721},{65,719},{30,743},{0,762},{-30,743}},20,0x466E8E);
        cylinder(faces,746,880,13,0x24465D);
        for(int i=0;i<9;i++)cylinder(faces,753+i*14,757+i*14,15,0xEAC889);
        prism(faces,new double[][]{{0,871},{-23,888},{-16,906},{0,920},{16,906},{23,888}},16,0xF4CF88);
        prism(faces,new double[][]{{0,884},{-10,894},{0,909},{10,894}},17,0x619FB1);
        return List.copyOf(faces);
    }
    private static void groove(List<Face> faces,double x0,double y0,double x1,double y1,double width,int side) {
        stroke(faces,x0,y0,x1,y1,width,side,2.0,0x87472F);
        stroke(faces,x0-.8,y0+.6,x1-.8,y1+.6,.65,side,2.8,0xFFD79A);
    }
    private static void stroke(List<Face> faces,double x0,double y0,double x1,double y1,double width,int side,double lift,int color) {
        double length=Math.hypot(x1-x0,y1-y0),dx=(y1-y0)/length*width/2,dy=-(x1-x0)/length*width/2;
        faces.add(new Face(v(x0-dx,y0-dy,side,lift),v(x0+dx,y0+dy,side,lift),v(x1+dx,y1+dy,side,lift),v(x1-dx,y1-dy,side,lift),color));
    }
    private static void prism(List<Face> faces,double[][] outline,double depth,int color) {
        double cx=0,cy=0;for(double[] p:outline){cx+=p[0];cy+=p[1];}cx/=outline.length;cy/=outline.length;
        for(int i=0;i<outline.length;i++) {
            double[] a=outline[i],b=outline[(i+1)%outline.length];
            for(int side:new int[]{-1,1}) {
                Vec3d p=new Vec3d(a[0],a[1],side*depth),q=new Vec3d(b[0],b[1],side*depth),centre=new Vec3d(cx,cy,side*(depth+3));
                faces.add(new Face(p,q,centre,centre,color));
            }
            faces.add(new Face(new Vec3d(a[0],a[1],-depth),new Vec3d(b[0],b[1],-depth),new Vec3d(b[0],b[1],depth),new Vec3d(a[0],a[1],depth),0xAE7B42));
        }
    }
    private static void cylinder(List<Face> faces,double y0,double y1,double radius,int color) {
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
            faces.add(new Face(new Vec3d(Math.cos(a)*radius,y0,Math.sin(a)*radius),new Vec3d(Math.cos(b)*radius,y0,Math.sin(b)*radius),
                new Vec3d(Math.cos(b)*radius,y1,Math.sin(b)*radius),new Vec3d(Math.cos(a)*radius,y1,Math.sin(a)*radius),color));
        }
    }
}
