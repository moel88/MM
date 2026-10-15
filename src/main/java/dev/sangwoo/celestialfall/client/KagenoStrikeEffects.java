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
    private static void arc(EffectMesh m,Vec3d p,Vec3d x,Vec3d y,double radius,double start,double length,double width,float alpha,boolean light){
        for(int j=0;j<32;j++){
            double t=j/32.,q=(j+1)/32.,a=start+t*length,b=start+q*length;
            double w=width*Math.pow(Math.sin(Math.PI*(t+.015)),.6),w2=width*Math.pow(Math.sin(Math.PI*(q-.015)),.6);
            int layers=light?2:3;
            for(int k=0;k<layers;k++){
                double scale=light?(k==0?2.6:.72):(k==0?1.3:k==1?1:.52);
                int color=light?(k==0?0xb43cff:0xffdfff):(k==0?0x6500ba:k==1?0xe48bff:0xffffff);
                m.quad(point(p,x,y,a,radius-w*scale),point(p,x,y,b,radius-w2*scale),point(p,x,y,b,radius+w2*scale),point(p,x,y,a,radius+w*scale),color,alpha*(light?(k==0?.12f:.75f):1));
            }
        }
    }
    private static void swordArc(EffectMesh m,Vec3d p,Vec3d x,Vec3d y,double radius,double start,double length,double width,float alpha,boolean light){
        for(int i=0;i<40;i++){
            double t=i/40.,q=(i+1)/40.,a=start+t*length,b=start+q*length;
            double w=width*Math.pow(Math.sin(Math.PI*(t+.01)),.65),v=width*Math.pow(Math.sin(Math.PI*(q-.01)),.65);
            double[] scales=light?new double[]{2.4,.25}:new double[]{1.25,1,.55,.14};
            int[] colors=light?new int[]{0x8d00ff,0xffd4ff}:new int[]{0x120025,0x6c00d8,0xce3fff,0xffe8ff};
            for(int k=0;k<scales.length;k++){
                double n=scales[k];
                m.quad(point(p,x,y,a,radius-w*n),point(p,x,y,b,radius-v*n),point(p,x,y,b,radius+v*n),point(p,x,y,a,radius+w*n),colors[k],alpha*(light?(k==0?.12f:.55f):1));
            }
        }
    }
    /** Four distinct reference strokes, including the expanding third sweep and fourth impact. */
    static void basic(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float age,int combo,boolean light){
        if(age<2||age>=14)return;
        float life=age-2,alpha=KagenoMotion.smooth(life,0,.8f)*fade(life,4,12);
        double progress=KagenoMotion.smooth(life,0,5),radius=combo==3?2.3+2.6*KagenoMotion.smooth(life,2,10):2.35;
        Vec3d x=combo==4?UP:right.add(UP.multiply(combo==1?.48:combo==2?-.48:.02)).normalize();
        Vec3d y=combo==4?f.add(right.multiply(.30)).normalize():f;
        Vec3d center=origin.add(f.multiply(combo==4?.65:.2));
        double direction=combo==2?-1:1,head=-2.8+progress*5.4*direction;
        for(int j=0;j<(combo==3?3:2);j++){
            Vec3d p=center.add(0,j*.10,0);
            swordArc(m,p,x,y,radius-j*.16,head-j*.18,combo==3?5.5:3.2,.25-j*.045,alpha*(1-j*.23f),light);
            arc(m,p,x,y,radius+.22+j*.15,head+.2,combo==3?5.8:3.7,.014,alpha*.55f,light);
        }
        if(combo==4){
            float a=fade(life,4,12);Vec3d p=origin.add(f.multiply(1.65)).add(0,-.55,0);
            if(!light)for(int j=0;j<8;j++){
                Vec3d q=p.add(right.multiply(Math.sin(j*4.1)*.6)).add(0,Math.cos(j*2.7)*.4+.1,0);
                halo(m,q,right,UP,.48+.25*(j%3),0x100019,a*.72f);
            }
            flare(m,p,right,UP,.6,a,light,4);shards(m,p,right,UP,life,12,1.8,26,light,4);
            for(int j=0;j<3;j++)arc(m,p,right,UP, .65+j*.23,age*.12+j*2,2.1,.05,a,light);
        }
    }
    static void thrusts(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float age,boolean light){
        if(age<4||age>=50)return;
        float alpha=KagenoMotion.smooth(age,4,8)*fade(age,47,50);
        // Dense short thrust trails, all pointing forward. No long persistent laser fan.
        for(int i=0;i<176;i++){
            float life=age-(5+i*.25f);if(life<0||life>=2.7f)continue;
            double angle=i*2.39996,r=.25+.65*(i%7)/6.;
            Vec3d offset=right.multiply(Math.cos(angle)*r).add(0,Math.sin(angle)*r,0);
            Vec3d a=origin.add(offset.multiply(.4)).add(f.multiply(.05+life*.42));
            Vec3d b=origin.add(offset).add(f.multiply(1.35+1.65*KagenoMotion.smooth(life,0,.7f)));
            float opacity=alpha*fade(life,.4f,2.7f);
            slash(m,a,b,UP,.025+.012*(i%3),opacity,light);
            slash(m,a,b,right,.025,opacity*.65f,light);
        }
        // The source has a dense, broken white/violet pressure crown around the stab lane.
        // Several tilted, offset arcs create depth instead of a few large flat rings.
        for(int j=0;j<9;j++){
            Vec3d center=origin.add(f.multiply(.32+(j%3)*.24));
            Vec3d x=right.add(f.multiply(Math.sin(j*2.1)*.34)).normalize();
            Vec3d y=UP.add(f.multiply(Math.cos(j*1.7)*.32)).normalize();
            double radius=.58+j*.045+.06*Math.sin(age*1.8+j);
            float beat=(float)(.65+.35*Math.sin(age*1.5+j*2.2)*Math.sin(age*1.5+j*2.2));
            arc(m,center,x,y,radius,age*(j%2==0?1.2:-1.0)+j*1.7, .65+(j%3)*.35,.08+(j%2)*.025,alpha*beat,light);
        }
        // Short angular white forks interrupt the curved pressure crown, as in the footage.
        for(int j=0;j<16;j++){
            double angle=j*2.39996+Math.floor(age*1.6)*.43;
            Vec3d axis=right.multiply(Math.cos(angle)).add(UP.multiply(Math.sin(angle)));
            Vec3d normal=right.multiply(-Math.sin(angle)).add(UP.multiply(Math.cos(angle)));
            Vec3d a=origin.add(f.multiply(.9)).add(axis.multiply(.35));
            Vec3d b=a.add(axis.multiply(.45)).add(normal.multiply(.16*Math.sin(j*2.7)));
            Vec3d c=b.add(axis.multiply(.35+(j%3)*.18)).add(f.multiply(.25));
            slash(m,a,b,normal,.035,alpha*.9f,light);slash(m,b,c,normal,.025,alpha*.8f,light);
        }
        // Brief dark blade silhouettes emerge from the sword shoulder during retraction.
        for(int j=0;j<5;j++){
            float phase=(age-4+j*1.1f)%6,stretch=KagenoMotion.smooth(phase,0,1.2f)*(1-KagenoMotion.smooth(phase,2.2f,5.6f));
            Vec3d shoulder=origin.add(0,.25,0);
            Vec3d grip=shoulder.add(right.multiply((j-2)*.11)).add(0,(j-2)*.10,0).add(f.multiply(.3+stretch*.65));
            Vec3d tip=grip.add(f.multiply(1.35+stretch*.3));
            if(!light){m.line(shoulder,grip,.075,0x2a1936,alpha*.4f);m.line(grip,tip,.045,0x110619,alpha*.85f);}
            slash(m,grip,tip,UP,.025,alpha*.4f,light);
        }
        for(int j=0;j<29;j++){
            float life=age-(6+j*1.5f);if(life<0||life>=3.4)continue;
            double angle=j*2.39996;
            Vec3d p=origin.add(f.multiply(1.35+(j%3)*.24)).add(right.multiply(Math.cos(angle)*.68)).add(0,Math.sin(angle)*.68,0);
            float a=alpha*fade(life,.4f,3.4f);
            flare(m,p,right,UP,.38+(j%3)*.16,a,light,j);
            shards(m,p,right,UP,life,3.4f,1.5,8,light,j);
        }
        if(light)halo(m,origin.add(f.multiply(.8)),right,UP,1.5,0xa42dff,alpha*.18f);
    }
    static void rush(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float age,boolean light){
        if(age>=RUSH_START-1&&age<RUSH_FINISH+2){
            float alpha=KagenoMotion.smooth(age,RUSH_START-1,RUSH_START+1)*fade(age,RUSH_FINISH-1,RUSH_FINISH+2);
            Vec3d nose=origin.add(f.multiply(.65)).add(0,.65,0);
            // Overlapping side and overhead crescents enclose the low sliding stance.
            for(int j=0;j<7;j++){
                double tilt=(j-3)*.14;
                Vec3d axis=right.add(f.multiply(tilt)).normalize(),vertical=UP.add(f.multiply(.28+tilt)).normalize();
                arc(m,nose.subtract(f.multiply(j*.24)),axis,vertical,1.35+j*.055,-.18+age*.055+j*.32,3.7,.11+(j%3)*.018,alpha*(1-j*.07f),light);
            }
            for(int j=0;j<9;j++){
                double side=(j-4)*.22,height=.05+.18*(j%5);
                Vec3d head=nose.add(right.multiply(side)).add(0,height,0);
                Vec3d tail=head.subtract(f.multiply(2.2+(j%3)*.65+.4*Math.sin(age*.9+j)));
                slash(m,tail,head,UP,.055+(j%3)*.018,alpha*.95f,light);
            }
        }
        if(age>=RUSH_FINISH&&age<RUSH_FINISH+16){
            float life=age-RUSH_FINISH,alpha=fade(life,2,13);Vec3d hit=origin.add(f.multiply(1.2)).add(0,.68,0);
            swordArc(m,hit,right,f,3.2+KagenoMotion.smooth(life,0,5)*2,-2.6+life*.18,5.4,.22,alpha,light);
            slash(m,hit.subtract(right.multiply(5.8)),hit.add(right.multiply(6.4)),UP,.22,alpha,light);
            slash(m,hit.subtract(right.multiply(4.5)).add(0,.2,0),hit.add(right.multiply(4.9)).add(0,-.12,0),f,.16,alpha,light);
            flare(m,hit,right,UP,.8,fade(life,.4f,5),light,4);
            shards(m,hit,right,UP,life,12,3.4,20,light,5);
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
    static void stoppedCuts(EffectMesh m,Vec3d origin,Vec3d f,Vec3d right,float age,boolean light){
        Vec3d center=origin.add(f.multiply(.7)).add(0,1.1,0);
        if(age>=18&&age<36){
            for(int j=0;j<6;j++){
                float life=age-(18+j*3);if(life<0)continue;
                double angle=new double[]{.08,.34,-.23,.51,-.45,.14}[j];
                Vec3d axis=right.multiply(Math.cos(angle)).add(UP.multiply(Math.sin(angle))),normal=right.multiply(-Math.sin(angle)).add(UP.multiply(Math.cos(angle)));
                Vec3d p=center.add(0,(j%3-1)*.32,0).add(f.multiply((j%2)*.2));
                float alpha=KagenoMotion.smooth(life,0,.8f);
                // Frozen once introduced: no age-driven wobble during stopped time.
                slash(m,p.subtract(axis.multiply(4.9+j*.13)),p.add(axis.multiply(4.5+j*.22)),normal,.16+(j%3)*.055,alpha,light);
                for(int k=0;k<3;k++){
                    Vec3d q=p.add(normal.multiply((k-1)*.17));
                    slash(m,q.subtract(axis.multiply(3.6+k*.4)),q.add(axis.multiply(5.0-k*.5)),normal,.025,alpha*.8f,light);
                }
                flare(m,p,right,UP,.42,alpha,light,j);
            }
        }
        if(age>=36&&age<59){
            float life=age-36,a=fade(life,1.4f,10);
            slash(m,center.subtract(right.multiply(6)),center.add(right.multiply(6)),UP,.32,a,light);
            flare(m,center,right,UP,1.35,a,light,0);
            for(int j=0;j<7;j++){
                Vec3d p=center.add(right.multiply((j-3)*1.1)).add(0,.35*Math.sin(j*4.1),0);
                flare(m,p,right,UP,.48+.15*(j%3),fade(life,.3f,7+j%3),light,j);
            }
            shards(m,center,right,UP,life,23,4.7,42,light,3);
            if(light)halo(m,center,right,UP,2.5,0xc047ff,a*.55f);
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
