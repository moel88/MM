package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.AtomicTimeline;
import net.minecraft.util.math.Vec3d;

/** Shared runtime/review geometry: luminous purple core, soft halo and fast vertical advection. */
public final class AtomicColumnGeometry {
    @FunctionalInterface public interface Quads { void quad(Vec3d a,Vec3d b,Vec3d c,Vec3d d,int rgb,float alpha); }
    private AtomicColumnGeometry() {}
    private static Vec3d point(double r,double a,double y){return new Vec3d(Math.cos(a)*r,y,Math.sin(a)*r);}
    private static int mix(int a,int b,double f) {
        f=Math.max(0,Math.min(1,f));int rgb=0;
        for(int shift:new int[]{0,8,16})rgb|=(int)(((a>>shift)&255)*(1-f)+((b>>shift)&255)*f)<<shift;
        return rgb;
    }
    public static void body(Quads out,float age,double baseRadius,boolean low) {
        double r=AtomicTimeline.blastRadius(age,baseRadius);if(!(r>0))return;
        double h=AtomicTimeline.columnHeight(baseRadius),time=(age-AtomicTimeline.IMPACT)/20.;
        int cols=low?32:64;
        // Broad lilac highlights recover the old luminous volume without four white additive walls.
        for(int y=0;y<2;y++)for(int i=0;i<cols;i++) {
            double a=i*Math.PI*2/cols,b=(i+1)*Math.PI*2/cols,lo=h*y/2,hi=h*(y+1)/2;
            double broad=.5+.5*Math.sin(a*3+time*.35);
            double channel=Math.pow(Math.max(0,Math.sin(a*11-time*.8)),8);
            int color=mix(mix(0x3002ad,0x6505f5,broad*.8),0x831bff,channel*.65);
            out.quad(point(r,a,lo),point(r,b,lo),point(r,b,hi),point(r,a,hi),color,1);
        }
        for(int i=0;i<cols;i++) {
            double a=i*Math.PI*2/cols,b=(i+1)*Math.PI*2/cols;
            out.quad(new Vec3d(0,h,0),point(r,a,h),point(r,b,h),new Vec3d(0,h,0),0xA059EC,1);
        }
    }
    /** Ordinary-alpha outer haze. Keep this separate from the additive highlights. */
    public static void halo(Quads out,float age,double baseRadius,boolean low) {
        double r=AtomicTimeline.blastRadius(age,baseRadius);if(!(r>0))return;
        double h=AtomicTimeline.columnHeight(baseRadius);int cols=low?32:64;
        // Back-to-front radial shells have low opacity, preserving the purple body and scene.
        for(int layer=2;layer>=0;layer--) {
            double radius=r*(1.022+layer*.037);float alpha=layer==0?.18f:layer==1?.075f:.025f;
            for(int i=0;i<cols;i++) {
                double a=i*Math.PI*2/cols,b=(i+1)*Math.PI*2/cols;
                out.quad(point(radius,a,0),point(radius,b,0),point(radius,b,h),point(radius,a,h),0xB270FF,alpha);
            }
        }
    }
    private static void tapered(Quads out,Vec3d a,Vec3d b,double startWidth,double endWidth,int color,float alpha) {
        Vec3d radial=a.add(b).multiply(.5).multiply(1,0,1).normalize();
        Vec3d side=new Vec3d(-radial.z,0,radial.x);
        if(side.lengthSquared()<1e-14)side=new Vec3d(1,0,0);
        Vec3d lo=side.multiply(startWidth*.5),hi=side.multiply(endWidth*.5);
        out.quad(a.subtract(lo),a.add(lo),b.add(hi),b.subtract(hi),color,alpha);
    }
    private static void strip(Quads out,Vec3d a,Vec3d b,double width,int color,float alpha) {
        tapered(out,a,b,width,width,color,alpha);
    }
    private static void ring(Quads out,double r,double y,double width,int color,float alpha,int segments,double rotation,double arc) {
        for(int i=0;i<segments;i++) {
            double a=rotation+i*Math.PI*2*arc/segments,b=rotation+(i+1)*Math.PI*2*arc/segments;
            out.quad(point(Math.max(0,r-width/2),a,y),point(r+width/2,a,y),point(r+width/2,b,y),point(Math.max(0,r-width/2),b,y),color,alpha);
        }
    }
    /** Real translating packets, not flickering full-height static lines. Speeds are blocks/second. */
    public static void streams(Quads out,float age,double baseRadius,boolean low) {
        double r=AtomicTimeline.blastRadius(age,baseRadius);if(!(r>0))return;
        double h=AtomicTimeline.columnHeight(baseRadius),seconds=(age-AtomicTimeline.IMPACT)/20.;
        int lanes=low?12:32,packets=8,slices=low?3:4;
        for(int i=0;i<lanes;i++) {
            double az=i*2.399963,rad=r*(1.008+(i%4)*.004);
            double speed=145+(i%7)*15,length=15+(i%5)*5,width=r*(i%4==0?.044:.021);
            for(int k=0;k<packets;k++) {
                // Span h+length, so a packet exits fully at the top before re-entering at the floor.
                double head=(seconds*speed+(i*.173+k/(double)packets)*(h+length))%(h+length);
                for(int j=0;j<slices;j++) {
                    double u0=j/(double)slices,u1=(j+1.)/slices;
                    double lo=head-length+length*u0,hi=head-length+length*u1;
                    if(hi<=0||lo>=h)continue;
                    Vec3d a=point(rad,az,Math.max(0,lo)),b=point(rad,az,Math.min(h,hi));
                    // Narrow tail -> bright blunt leading edge makes the upward direction readable.
                    double w0=width*(.13+.87*u0),w1=width*(.13+.87*u1);
                    float alpha=(float)(.08+.65*u1*u1);
                    tapered(out,a,b,w0*3.4,w1*3.4,0xB060FF,alpha*.32f);
                    tapered(out,a,b,w0,w1,i%3==0?0xE4C8FF:0xD397FF,alpha);
                }
            }
        }
    }
    public static void energy(Quads out,float age,double baseRadius,boolean low) {
        double r=AtomicTimeline.blastRadius(age,baseRadius);if(!(r>0))return;
        double h=AtomicTimeline.columnHeight(baseRadius),t=age-AtomicTimeline.IMPACT;
        streams(out,age,baseRadius,low);
        // Thin electrical detail stays secondary to the much faster axial streams.
        int bolts=low?4:8,steps=low?16:32;
        for(int i=0;i<bolts;i++) {
            double az=i*Math.PI*2/bolts+t*.003;
            Vec3d prev=point(r*1.023,az,0);
            for(int j=1;j<=steps;j++) {
                double y=h*j/steps;
                double kink=.07*Math.sin(i*71+j*17-Math.floor(t)*2.4)+.05*Math.sin(y*.13-t*.9);
                Vec3d next=point(r*1.025,az+kink,y);
                strip(out,prev,next,r*.024,0x9845FF,.18f);
                strip(out,prev,next,r*.005,0xD89AFF,.65f);prev=next;
            }
        }
        int segments=low?32:64;
        ring(out,r*1.06,.12,r*.09,0xA249FF,.65f,segments,0,1);
        ring(out,r*1.13,.14,r*.012,0xE4B0FF,.90f,segments,0,1);
        for(int i=0;i<(low?2:4);i++) {
            double wave=((t*.045+i*.25)%1);
            ring(out,r*(1.12+wave*.45),.15+i*.018,r*.025,0xB26BFF,(float)(1-wave)*.48f,segments,0,1);
        }
        // Old upward travelling bands, now independent of column height and horizontal shrink.
        int bands=low?4:6;
        for(int i=0;i<bands;i++) {
            double y=(t*6.5+i*h/bands)%h;
            float envelope=(float)Math.min(1,Math.min(y/5,(h-y)/5));
            ring(out,r*1.026,y,r*.12,0xAC66FF,.24f*envelope,segments,t*.01+i,.88);
            ring(out,r*1.028,y,r*.025,0xE3BFFF,.78f*envelope,segments,t*.01+i,.88);
        }
    }
}
