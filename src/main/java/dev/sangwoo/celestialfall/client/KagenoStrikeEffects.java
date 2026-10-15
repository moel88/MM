package dev.sangwoo.celestialfall.client;

import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** Reference E/R/H meshes. Solid silhouettes and additive emission are separate bounded batches. */
final class KagenoStrikeEffects {
    private static final Vec3d UP=new Vec3d(0,1,0);
    private KagenoStrikeEffects(){}
    private static float fade(float life,float hold,float end){return 1-KagenoMotion.smooth(life,hold,end);}
    private static Vec3d point(Vec3d o,Vec3d x,Vec3d y,double angle,double radius){return o.add(x.multiply(Math.cos(angle)*radius)).add(y.multiply(Math.sin(angle)*radius));}

    /** A pointed white blade with a violet silhouette; emission never changes the opaque shape. */
    private static void slash(EffectMesh m,Vec3d a,Vec3d b,Vec3d side,double width,float alpha,boolean light){
        if(alpha<=0)return;
        Vec3d mid=a.lerp(b,.46),n=side.normalize();
        if(light){
            diamond(m,a,b,mid,n,width*3.6,0x8300ff,alpha*.075f);
            diamond(m,a,b,mid,n,width*1.9,0xbb40ff,alpha*.18f);
            diamond(m,a,b,mid,n,width*.73,0xffdfff,alpha*.85f);
        }else{
            diamond(m,a,b,mid,n,width*1.18,0x390066,alpha*.92f);
            diamond(m,a,b,mid,n,width,0x8d24ff,alpha);
            diamond(m,a,b,mid,n,width*.72,0xf7b4ff,alpha);
            diamond(m,a,b,mid,n,width*.48,0xffffff,alpha);
        }
    }
    private static void diamond(EffectMesh m,Vec3d a,Vec3d b,Vec3d mid,Vec3d n,double width,int color,float alpha){
        m.quad(a,mid.add(n.multiply(width)),b,mid.subtract(n.multiply(width)),color,alpha);
    }
    private static void flare(EffectMesh m,Vec3d p,Vec3d x,Vec3d y,double radius,float alpha,boolean light,int seed){
        if(alpha<=0)return;
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4+seed*.53,length=radius*(i%2==0?1:.35)*(1+.15*Math.sin(seed+i*8.2));
            Vec3d axis=x.multiply(Math.cos(angle)).add(y.multiply(Math.sin(angle))),normal=x.multiply(-Math.sin(angle)).add(y.multiply(Math.cos(angle)));
            slash(m,p.subtract(axis.multiply(length*.18)),p.add(axis.multiply(length)),normal,radius*.09,alpha,light);
        }
        if(light)halo(m,p,x,y,radius*.5,0xe876ff,alpha*.45f);
    }
    /** Radial vertex-alpha falloff, no flat transparent disk. */
    private static void halo(EffectMesh m,Vec3d p,Vec3d x,Vec3d y,double radius,int color,float alpha){
        for(int i=0;i<24;i++){
            Vec3d a=point(p,x,y,i*Math.PI/12,radius),b=point(p,x,y,(i+1)*Math.PI/12,radius);
            m.vertex(p,color,alpha);m.vertex(a,color,0);m.vertex(b,color,0);m.vertex(b,color,0);
        }
    }
    static void landingSlide(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float age){
        if(age<56||age>76)return;
        Vec3d foot=origin.add(f.multiply(KagenoMotion.hTravel(age))).add(0,.045,0);
        float moving=age<63?1-KagenoMotion.smooth(age,61,64):KagenoMotion.smooth(age,65,67)*(1-KagenoMotion.smooth(age,71,74));
        for(int i=0;i<7;i++){
            Vec3d last=origin.add(f.multiply(KagenoMotion.hTravel(Math.max(56,age-i*.65f)))).add(right.multiply((i%3-1)*.14)).add(0,.035+i*.002,0);
            m.glowLine(last,foot,.018,0xbda7d6,moving*(1-i/7f)*.42f);
        }
        for(int i=0;i<9;i++){
            double phase=(age*.19+i*.618)%1;
            Vec3d dust=foot.add(right.multiply((i%2==0?-1:1)*(.18+phase*.7))).add(f.multiply((age<64?1:-1)*phase*.8));
            m.star(dust.add(0,phase*.22,0),.035*(1-phase),0xa5a2b1,moving*(float)(1-phase)*.6f);
        }
    }
    static void highest(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float age,boolean light){
        if(age<79||age>=111)return;
        // The slash field is behind the actor in the FINAL CAMERA plane, not
        // squeezed edge-on by the old forward-facing combat plane.
        f=f.multiply(.587).subtract(right.multiply(.810)).normalize();
        right=new Vec3d(f.z,0,-f.x);
        Vec3d behind=origin.subtract(f.multiply(2.4)).subtract(right.multiply(.8)).add(0,1.7,0);
        if(light&&age>=96){
            float a=KagenoMotion.smooth(age,96,98)*fade(age,100,111);
            halo(m,origin.subtract(f.multiply(3.5)).add(0,.6,0),right,UP.multiply(.45),3.2,0x7200ff,a*.75f);
        }
        for(int j=0;j<12;j++){
            float life=age-(79+j*1.6f);if(life<0||life>=6.4f)continue;
            float alpha=KagenoMotion.smooth(life,0,.35f)*fade(life,1.4f,5.5f);
            // The big cuts end as the sword lowers; the remaining server hits have small residual flashes.
            if(j>=6){
                Vec3d p=behind.add(right.multiply(Math.sin(j*4.17)*2.8)).add(0,Math.cos(j*2.73)*.9,0);
                flare(m,p,right,UP,.38,alpha*.75f,light,j);
                shards(m,p,right,UP,life,6.4f,1.5,8,light,j);
                continue;
            }
            // Two razor-like intersecting blades per hit; intentionally no circular crescents.
            for(int k=0;k<2;k++){
                int seed=j*2+k;double angle=seed*2.39996+.18;
                Vec3d axis=right.multiply(Math.cos(angle)).add(UP.multiply(Math.sin(angle))),normal=right.multiply(-Math.sin(angle)).add(UP.multiply(Math.cos(angle)));
                Vec3d p=behind.add(right.multiply(Math.sin(seed*4.17)*2.5)).add(0,Math.cos(seed*2.73)*1.05,0).add(f.multiply(-.15*(seed%3)));
                double length=2.5+(seed%4)*.55,width=.17+(seed%3)*.10;
                slash(m,p.subtract(axis.multiply(length)),p.add(axis.multiply(length*1.12)),normal,width,alpha,light);
                // Jagged secondary cuts and bright intersections are part of the source silhouette.
                Vec3d joint=p.add(axis.multiply(length*.3));
                Vec3d spur=axis.multiply(.45).add(normal.multiply(seed%2==0?.9:-.9)).normalize();
                slash(m,joint,joint.add(spur.multiply(1.2+(seed%3)*.3)),normal,width*.45,alpha,light);
                for(int thread=0;thread<3;thread++){
                    Vec3d offset=normal.multiply((thread-1)*.17);
                    slash(m,p.subtract(axis.multiply(length*1.3)).add(offset),p.add(axis.multiply(length*1.2)).add(offset),normal,.018,alpha*.7f,light);
                }
                flare(m,p,right,UP,.48+(seed%3)*.18,alpha,light,seed);
                shards(m,p,right,UP,life,6.4f,1.6,6,light,seed);
            }
        }
    }
    private static void shards(EffectMesh m,Vec3d p,Vec3d x,Vec3d y,float life,float duration,double radius,int count,boolean light,int seed){
        if(life<0||life>=duration)return;
        float t=life/duration,alpha=fade(life,duration*.24f,duration);
        for(int i=0;i<count;i++){
            double angle=i*2.39996+seed*.71,r=radius*(.55+.45*(.5+.5*Math.sin(i*7.4)))*Math.sqrt(t+.04);
            Vec3d axis=x.multiply(Math.cos(angle)).add(y.multiply(Math.sin(angle))),normal=x.multiply(-Math.sin(angle)).add(y.multiply(Math.cos(angle)));
            Vec3d tip=p.add(axis.multiply(r)).add(0,-t*t*.9,0),tail=tip.subtract(axis.multiply((.12+.6*(1-t))*(.5+.5*(i%3))));
            slash(m,tail,tip,normal,.055+.035*(i%3),alpha,light);
        }
    }
}
