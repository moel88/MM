package dev.sangwoo.celestialfall.client;

import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Continuous billowing banks and tapered spiral wakes, cached once in two quality levels. */
public final class HeavenClouds {
    public record Layer(List<HeavenBlade.Face> high,List<HeavenBlade.Face> low,double speed,
                        Map<Vec3d,Integer> highColors,Map<Vec3d,Integer> lowColors) {}
    public static final List<Layer> LAYERS=build();
    private HeavenClouds() {}
    private static List<Layer> build() {
        List<Layer> layers=new ArrayList<>();
        for(int layer=0;layer<3;layer++) {
            var high=mesh(layer,128,20);var low=mesh(layer,64,8);
            layers.add(new Layer(high,low,.0036-layer*.0009,smoothColors(high),smoothColors(low)));
        }
        return List.copyOf(layers);
    }
    private static Map<Vec3d,Integer> smoothColors(List<HeavenBlade.Face> faces) {
        Map<Vec3d,int[]> sums=new HashMap<>();
        for(var f:faces)for(var v:List.of(f.a(),f.b(),f.c(),f.d())) {
            var sum=sums.computeIfAbsent(v,k->new int[4]);sum[0]+=f.color()>>16&255;sum[1]+=f.color()>>8&255;sum[2]+=f.color()&255;sum[3]++;
        }
        Map<Vec3d,Integer> colors=new HashMap<>();sums.forEach((v,s)->colors.put(v,((s[0]/s[3])<<16)|((s[1]/s[3])<<8)|(s[2]/s[3])));
        return Map.copyOf(colors);
    }
    private static List<HeavenBlade.Face> mesh(int layer,int segments,int sides) {
        List<HeavenBlade.Face> faces=new ArrayList<>();
        if(layer<2) {
            for(int i=0;i<segments;i++)for(int j=0;j<sides;j++) {
                double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
                double u=j*Math.PI*2/sides,v=(j+1)*Math.PI*2/sides;
                faces.add(new HeavenBlade.Face(bank(layer,a,u),bank(layer,b,u),bank(layer,b,v),bank(layer,a,v),
                    tint(layer,(a+b)/2,(u+v)/2)));
            }
        } else {
            // Irregular long connected wakes, all following the same circulation.
            for(int arm=0;arm<5;arm++)for(int i=0;i<segments*5/8;i++)for(int j=0;j<sides;j++) {
                double t=i/(segments*.625),tt=(i+1)/(segments*.625),u=j*Math.PI*2/sides,v=(j+1)*Math.PI*2/sides;
                faces.add(new HeavenBlade.Face(wake(arm,t,u),wake(arm,tt,u),wake(arm,tt,v),wake(arm,t,v),
                    tint(2,arm*1.27+t*2.4,(u+v)/2)));
            }
        }
        if(layer==0)for(int arm=0;arm<11;arm++)for(int i=0;i<segments/4;i++) {
            double t=i/(segments/4.0),tt=(i+1)/(segments/4.0);
            faces.add(new HeavenBlade.Face(wisp(arm,t,-1),wisp(arm,tt,-1),wisp(arm,tt,1),wisp(arm,t,1),
                arm%3==0?0xD9EAF7:0xC4E0F2));
        }
        return List.copyOf(faces);
    }
    private static double wave(double a) {
        return Math.sin(a*5+.6)*.52+Math.sin(a*9-1.4)*.28+Math.sin(a*17+.3)*.20;
    }
    private static Vec3d bank(int layer,double a,double u) {
        double billow=wave(a+layer*.37);
        double centre=(layer==0?154:260)+(layer==0?5:16)*billow;
        double width=(layer==0?48:83)*(1+.12*Math.sin(a*7)+.08*Math.sin(a*13));
        double detail=(Math.sin(a*11+2*Math.sin(u))+.45*Math.sin(a*21-u*3)+.25*Math.sin(a*37+u*5))/1.7;
        double r=centre+Math.cos(u)*width+detail*5;
        double z=(layer==0?27:-18)+Math.sin(u)*(layer==0?34:64)*(1+.17*billow)+billow*9;
        z+=detail*(layer==0?13:24)*(.7+.3*Math.sin(u));
        return new Vec3d(Math.cos(a)*r,Math.sin(a)*r,z);
    }
    private static Vec3d wisp(int arm,double t,double side) {
        double a=arm*Math.PI*2/11+Math.sin(arm*6.7)*.23+t*(.7+(arm%4)*.12);
        double r=108+t*130+side*(4+arm%5*2)*Math.sin(Math.PI*t);
        return new Vec3d(Math.cos(a)*r,Math.sin(a)*r,64+wave(a)*12-t*14);
    }
    private static Vec3d wake(int arm,double t,double u) {
        double a=arm*Math.PI*2/5+Math.sin(arm*2.3)*.13+t*(2.1+arm*.08);
        double r=270+420*t+wave(a)*12;
        double envelope=Math.pow(Math.sin(Math.PI*t),.65);
        double width=(35+65*t)*envelope;
        r+=Math.cos(u)*width;
        double z=-36+Math.sin(u)*width*.55+Math.sin(a*3)*18-18*t;
        return new Vec3d(Math.cos(a)*r,Math.sin(a)*r,z);
    }
    private static int tint(int layer,double a,double u) {
        int base=layer==0?0xDFEFFA:layer==1?0xA0BED8:0x728BA8;
        // Continuous lighting avoids the repeated hard facets of separate ellipsoids.
        double light=.79+.16*Math.sin(u)+.025*Math.cos(a-1.2)+.035*Math.sin(a*11+2*Math.sin(u))+.025*Math.sin(a*23-u*3);
        light=Math.max(.45,Math.min(1,light));
        return ((int)((base>>16&255)*light)<<16)|((int)((base>>8&255)*light)<<8)|(int)((base&255)*light);
    }
}
