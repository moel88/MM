package dev.sangwoo.celestialfall.client.meteor;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.util.*;

/**
 * A meteor's rock, made from its seed (2.49.0): an icosphere pushed in and out by noise, dented with shallow
 * thumbprints (a meteorite's regmaglypts), cut flat by a few planes and squashed into a low boulder that sits about a
 * third of its height in the ground, shaded flat facet by facet. Glowing seams run between its plates where a ridged
 * noise peaks. Three clusters of gold crystals grow out of its upper half, one for each fragment it holds; mining takes
 * the last one off first. Positions are in blocks from the middle of its foot on the ground.
 */
final class MeteorMesh {
    /** Floats per corner: x, y, z, u, v, nx, ny, nz, seam glow (0..1). Three corners a triangle. */
    static final int STRIDE=9;
    /** The rock's triangles. */
    final float[] rock;
    /** The crystal clusters, in the order they are mined from the last. */
    final Cluster[] clusters;
    /** What the rock breaks into when it is mined out or crumbles. */
    final Piece[] pieces;
    /** The height of the rock's middle above the ground, and its rough size. */
    final float centreY,radius;
    record Cluster(float[] tris,float x,float y,float z,float nx,float ny,float nz){}
    record Piece(float[] tris,float cx,float cy,float cz,float dx,float dy,float dz){}

    private static final Map<Integer,MeteorMesh> CACHE=new LinkedHashMap<>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Integer,MeteorMesh> e){return size()>24;}
    };
    static synchronized MeteorMesh of(int seed){return CACHE.computeIfAbsent(seed,MeteorMesh::new);}

    private MeteorMesh(int seed){
        var rng=new Random(seed*0x9E3779B97F4A7C15L+0x5DEECE66DL);
        // the icosphere, three times divided (642 corners, 1280 faces)
        List<double[]> v=new ArrayList<>();List<int[]> f=new ArrayList<>();
        double g=(1+Math.sqrt(5))/2;
        double[][] base={{-1,g,0},{1,g,0},{-1,-g,0},{1,-g,0},{0,-1,g},{0,1,g},{0,-1,-g},{0,1,-g},{g,0,-1},{g,0,1},{-g,0,-1},{-g,0,1}};
        for(var b:base)v.add(unit(b));
        int[][] ico={{0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},{1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},{3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},{4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}};
        f.addAll(Arrays.asList(ico));
        for(int level=0;level<3;level++){
            Map<Long,Integer> mid=new HashMap<>();List<int[]> next=new ArrayList<>();
            for(int[] t:f){
                int a=midpoint(v,mid,t[0],t[1]),b=midpoint(v,mid,t[1],t[2]),c=midpoint(v,mid,t[2],t[0]);
                next.add(new int[]{t[0],a,c});next.add(new int[]{t[1],b,a});next.add(new int[]{t[2],c,b});next.add(new int[]{a,b,c});
            }
            f=next;
        }
        // its shape: noise, thumbprints, flat cuts, squashed and turned
        double ox=rng.nextDouble()*100,oy=rng.nextDouble()*100,oz=rng.nextDouble()*100;
        double[][] dents=new double[9][];for(int i=0;i<dents.length;i++)dents[i]=randomDir(rng,-.3);
        double[][] cuts=new double[6][];double[] cutAt=new double[6];
        for(int i=0;i<cuts.length;i++){cuts[i]=randomDir(rng,-.2);cutAt[i]=.80+rng.nextDouble()*.1;}
        double ax=1.10+rng.nextDouble()*.18,ay=.78+rng.nextDouble()*.12,az=.92+rng.nextDouble()*.16;
        double yaw=rng.nextDouble()*Math.PI*2,cy=Math.cos(yaw),sy=Math.sin(yaw);
        centreY=(float)(ay*.46);radius=(float)Math.max(ax,az);
        float bury=-(float)(ay*.62)+centreY;
        double[][] p=new double[v.size()][];float[] seam=new float[v.size()];
        for(int i=0;i<v.size();i++){
            double[] d=v.get(i);
            double r=1+.17*(fbm(d[0]*1.7+ox,d[1]*1.7+oy,d[2]*1.7+oz)-.5)*2+.06*(fbm(d[0]*4.3+oz,d[1]*4.3+ox,d[2]*4.3+oy)-.5)*2;
            for(var dent:dents){double c=dot(d,dent);if(c>.94){double k=(c-.94)/.06;r-=.055*Math.sin(Math.PI*Math.min(1,k*1.4))*(1-k*.3);}}
            double[] q={d[0]*r,d[1]*r,d[2]*r};
            for(int c=0;c<cuts.length;c++){double s=dot(q,cuts[c])-cutAt[c];if(s>0){q[0]-=cuts[c][0]*s;q[1]-=cuts[c][1]*s;q[2]-=cuts[c][2]*s;}}
            double x=q[0]*ax,y=q[1]*ay,z=q[2]*az;
            p[i]=new double[]{x*cy-z*sy,Math.max(bury,y+centreY),x*sy+z*cy};
            // a seam between plates where the ridged noise peaks
            double n=fbm(d[0]*2.3+oy,d[1]*2.3+oz,d[2]*2.3+ox);double ridge=1-Math.abs(n-.5)*2;
            seam[i]=(float)smooth(.80,.96,ridge);
        }
        double[] uvOffset={rng.nextDouble(),rng.nextDouble()};
        float[] tris=new float[f.size()*3*STRIDE];int at=0;
        for(int[] t:f){
            double[] a=p[t[0]],b=p[t[1]],c=p[t[2]];
            double[] n=cross(sub(b,a),sub(c,a));double len=Math.sqrt(dot(n,n));if(len<1e-9)continue;
            n=new double[]{n[0]/len,n[1]/len,n[2]/len};
            double[] centroid={(a[0]+b[0]+c[0])/3,(a[1]+b[1]+c[1])/3-centreY,(a[2]+b[2]+c[2])/3};
            int[] order={t[0],t[1],t[2]};
            if(dot(n,centroid)<0){n=new double[]{-n[0],-n[1],-n[2]};order=new int[]{t[0],t[2],t[1]};}
            for(int k:order)at=corner(tris,at,p[k],n,seam[k],uvOffset);
        }
        rock=Arrays.copyOf(tris,at);
        clusters=new Cluster[3];
        double first=rng.nextDouble()*Math.PI*2;
        for(int k=0;k<3;k++){
            double az0=first+k*Math.PI*2/3+(rng.nextDouble()-.5)*.8,el=Math.toRadians(28+rng.nextDouble()*26);
            double[] dir={Math.cos(az0)*Math.cos(el),Math.sin(el),Math.sin(az0)*Math.cos(el)};
            clusters[k]=cluster(dir,rng);
        }
        pieces=pieces(rng);
    }

    /** One crystal cluster where a ray from the rock's middle along {@code dir} leaves its surface. */
    private Cluster cluster(double[] dir,Random rng){
        double[] o={0,centreY,0};double best=-1;double[] hitN={0,1,0};
        for(int i=0;i<rock.length;i+=3*STRIDE){
            double[] a={rock[i],rock[i+1],rock[i+2]},b={rock[i+STRIDE],rock[i+STRIDE+1],rock[i+STRIDE+2]},c={rock[i+2*STRIDE],rock[i+2*STRIDE+1],rock[i+2*STRIDE+2]};
            double tt=intersect(o,dir,a,b,c);
            if(tt>best){best=tt;hitN=new double[]{rock[i+5],rock[i+6],rock[i+7]};}
        }
        if(best<0)best=radius*.8;
        double[] base={o[0]+dir[0]*best,o[1]+dir[1]*best,o[2]+dir[2]*best};
        double[] up=unit(new double[]{hitN[0]*.55+dir[0]*.45,hitN[1]*.55+dir[1]*.45+.15,hitN[2]*.55+dir[2]*.45});
        List<Float> out=new ArrayList<>();
        double[][] shards={{.62,.115,0,0},{.44,.085,.42,.10},{.32,.068,-.5,.09}};
        double spin=rng.nextDouble()*Math.PI*2;
        for(double[] s:shards){
            double len=s[0]*(.9+rng.nextDouble()*.2),rad=s[1];
            double[] axis=tilt(up,s[2]!=0?Math.abs(s[2]):.12,spin+(s[2]<0?Math.PI:0)+rng.nextDouble()*.6);
            double[] side=perpendicular(up);double[] side2=cross(up,side);
            double off=s[3];double ang=spin+(s[2]<0?Math.PI:0);
            double[] foot={base[0]+(side[0]*Math.cos(ang)+side2[0]*Math.sin(ang))*off,base[1]+(side[1]*Math.cos(ang)+side2[1]*Math.sin(ang))*off,base[2]+(side[2]*Math.cos(ang)+side2[2]*Math.sin(ang))*off};
            prism(out,foot,axis,len,rad,rng.nextDouble()*Math.PI);
        }
        float[] tris=new float[out.size()];for(int i=0;i<tris.length;i++)tris[i]=out.get(i);
        return new Cluster(tris,(float)base[0],(float)base[1],(float)base[2],(float)up[0],(float)up[1],(float)up[2]);
    }

    /** A six-sided crystal: buried a little at its foot, straight sides to 70% of its length, then a point. */
    private static void prism(List<Float> out,double[] foot,double[] axis,double len,double rad,double turn){
        double[] s1=perpendicular(axis),s2=cross(axis,s1);
        double[][] lowRing=new double[6][],highRing=new double[6][];
        double[] bottom=add(foot,axis,-.22),shoulder=add(foot,axis,len*.7),tip=add(foot,axis,len);
        for(int i=0;i<6;i++){
            double a=turn+i*Math.PI/3;double[] r={s1[0]*Math.cos(a)+s2[0]*Math.sin(a),s1[1]*Math.cos(a)+s2[1]*Math.sin(a),s1[2]*Math.cos(a)+s2[2]*Math.sin(a)};
            lowRing[i]=add(bottom,r,rad);highRing[i]=add(shoulder,r,rad);
        }
        for(int i=0;i<6;i++){
            int j=(i+1)%6;
            face(out,lowRing[i],lowRing[j],highRing[j],0,1,1,1,1,0,axis,foot);
            face(out,lowRing[i],highRing[j],highRing[i],0,1,1,0,0,0,axis,foot);
            face(out,highRing[i],highRing[j],tip,0,.3,1,.3,.5,0,axis,foot);
        }
    }
    private static void face(List<Float> out,double[] a,double[] b,double[] c,double ua,double va,double ub,double vb,double uc,double vc,double[] axis,double[] foot){
        double[] n=cross(sub(b,a),sub(c,a));double len=Math.sqrt(dot(n,n));if(len<1e-12)return;n=new double[]{n[0]/len,n[1]/len,n[2]/len};
        double[] mid={(a[0]+b[0]+c[0])/3-foot[0],(a[1]+b[1]+c[1])/3-foot[1],(a[2]+b[2]+c[2])/3-foot[2]};
        double along=dot(mid,axis);double[] outward={mid[0]-axis[0]*along,mid[1]-axis[1]*along,mid[2]-axis[2]*along};
        if(dot(n,outward)+dot(n,axis)*(along>0?.01:0)<0){double[] t=b;b=c;c=t;double tu=ub,tv=vb;ub=uc;vb=vc;uc=tu;vc=tv;n=new double[]{-n[0],-n[1],-n[2]};}
        for(double[][] q:new double[][][]{{a,{ua,va}},{b,{ub,vb}},{c,{uc,vc}}}){
            out.add((float)q[0][0]);out.add((float)q[0][1]);out.add((float)q[0][2]);out.add((float)q[1][0]);out.add((float)q[1][1]);
            out.add((float)n[0]);out.add((float)n[1]);out.add((float)n[2]);out.add(0f);
        }
    }

    /** The rock split by seven directions (more of them upward), each triangle going with the nearest. */
    private Piece[] pieces(Random rng){
        int k=7;double[][] dirs=new double[k][];for(int i=0;i<k;i++)dirs[i]=randomDir(rng,.1);
        List<List<Float>> parts=new ArrayList<>();for(int i=0;i<k;i++)parts.add(new ArrayList<>());
        for(int i=0;i<rock.length;i+=3*STRIDE){
            double cx=(rock[i]+rock[i+STRIDE]+rock[i+2*STRIDE])/3,cy=(rock[i+1]+rock[i+STRIDE+1]+rock[i+2*STRIDE+1])/3-centreY,cz=(rock[i+2]+rock[i+STRIDE+2]+rock[i+2*STRIDE+2])/3;
            double[] d=unit(new double[]{cx,cy,cz});int best=0;double bestDot=-2;
            for(int j=0;j<k;j++){double c=dot(d,dirs[j]);if(c>bestDot){bestDot=c;best=j;}}
            for(int j=0;j<3*STRIDE;j++)parts.get(best).add(rock[i+j]);
        }
        List<Piece> out=new ArrayList<>();
        for(int j=0;j<k;j++){
            var part=parts.get(j);if(part.isEmpty())continue;
            float[] tris=new float[part.size()];double sx=0,sy=0,sz=0;int n=0;
            for(int i=0;i<tris.length;i++)tris[i]=part.get(i);
            for(int i=0;i<tris.length;i+=STRIDE){sx+=tris[i];sy+=tris[i+1];sz+=tris[i+2];n++;}
            out.add(new Piece(tris,(float)(sx/n),(float)(sy/n),(float)(sz/n),(float)dirs[j][0],(float)Math.max(.25,dirs[j][1]),(float)dirs[j][2]));
        }
        return out.toArray(new Piece[0]);
    }

    private int corner(float[] out,int at,double[] p,double[] n,float seam,double[] uvOffset){
        double ax=Math.abs(n[0]),ay=Math.abs(n[1]),az=Math.abs(n[2]);
        double u,w;
        if(ax>=ay&&ax>=az){u=p[2];w=p[1];}else if(ay>=az){u=p[0];w=p[2];}else{u=p[0];w=p[1];}
        out[at]=(float)p[0];out[at+1]=(float)p[1];out[at+2]=(float)p[2];
        out[at+3]=(float)(u*.25+uvOffset[0]);out[at+4]=(float)(w*.25+uvOffset[1]);
        out[at+5]=(float)n[0];out[at+6]=(float)n[1];out[at+7]=(float)n[2];out[at+8]=seam;
        return at+STRIDE;
    }

    /**
     * Writes triangles to an entity layer (each as a quad with its last corner twice). {@code seamGlow} > 0 colours each
     * corner by its seam (the seams pass); otherwise every corner is {@code r,g,b,a}.
     */
    static void emit(VertexConsumer vc,Matrix4f pos,Matrix3f normal,float[] tris,int from,int to,float r,float g,float b,float a,boolean seams,int light,int overlay){
        for(int i=from;i<to;i+=3*STRIDE){
            for(int k=0;k<4;k++){
                int c=i+Math.min(k,2)*STRIDE;
                float s=seams?tris[c+8]:1;
                // a colour packs into a byte a channel: anything over 1 would wrap round to nearly nothing
                vc.vertex(pos,tris[c],tris[c+1],tris[c+2]).color(unit(r*s),unit(g*s),unit(b*s),unit(a)).texture(tris[c+3],tris[c+4]).overlay(overlay).light(light).normal(normal,tris[c+5],tris[c+6],tris[c+7]).next();
            }
        }
    }
    static void emit(VertexConsumer vc,Matrix4f pos,Matrix3f normal,float[] tris,float r,float g,float b,float a,boolean seams,int light,int overlay){emit(vc,pos,normal,tris,0,tris.length,r,g,b,a,seams,light,overlay);}
    static float unit(float v){return v<0?0:v>1?1:v;}

    // ---- small vector helpers and value noise ----
    private static int midpoint(List<double[]> v,Map<Long,Integer> cache,int a,int b){
        long key=((long)Math.min(a,b)<<32)|Math.max(a,b);
        return cache.computeIfAbsent(key,k->{double[] p=v.get(a),q=v.get(b);v.add(unit(new double[]{p[0]+q[0],p[1]+q[1],p[2]+q[2]}));return v.size()-1;});
    }
    private static double[] randomDir(Random rng,double minY){
        while(true){double[] d={rng.nextGaussian(),rng.nextGaussian(),rng.nextGaussian()};d=unit(d);if(d[1]>=minY)return d;}
    }
    private static double[] tilt(double[] axis,double angle,double around){
        double[] s1=perpendicular(axis),s2=cross(axis,s1);
        double c=Math.cos(angle),s=Math.sin(angle);
        return unit(new double[]{axis[0]*c+(s1[0]*Math.cos(around)+s2[0]*Math.sin(around))*s,axis[1]*c+(s1[1]*Math.cos(around)+s2[1]*Math.sin(around))*s,axis[2]*c+(s1[2]*Math.cos(around)+s2[2]*Math.sin(around))*s});
    }
    private static double[] perpendicular(double[] a){return unit(Math.abs(a[1])<.9?cross(a,new double[]{0,1,0}):cross(a,new double[]{1,0,0}));}
    private static double intersect(double[] o,double[] d,double[] a,double[] b,double[] c){
        double[] e1=sub(b,a),e2=sub(c,a),h=cross(d,e2);double det=dot(e1,h);if(Math.abs(det)<1e-12)return -1;
        double inv=1/det;double[] s=sub(o,a);double u=dot(s,h)*inv;if(u<0||u>1)return -1;
        double[] q=cross(s,e1);double w=dot(d,q)*inv;if(w<0||u+w>1)return -1;
        double t=dot(e2,q)*inv;return t>0?t:-1;
    }
    private static double[] unit(double[] a){double l=Math.sqrt(dot(a,a));return new double[]{a[0]/l,a[1]/l,a[2]/l};}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double[] add(double[] a,double[] d,double k){return new double[]{a[0]+d[0]*k,a[1]+d[1]*k,a[2]+d[2]*k};}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double smooth(double e0,double e1,double x){double t=Math.max(0,Math.min(1,(x-e0)/(e1-e0)));return t*t*(3-2*t);}
    private static double hash(int x,int y,int z){long h=x*374761393L+y*668265263L+z*1274126177L;h=(h^(h>>>13))*1274126177L;return ((h^(h>>>16))&0xffffff)/(double)0xffffff;}
    private static double noise(double x,double y,double z){
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y),iz=(int)Math.floor(z);
        double fx=x-ix,fy=y-iy,fz=z-iz;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);fz=fz*fz*(3-2*fz);
        double a=lerp(hash(ix,iy,iz),hash(ix+1,iy,iz),fx),b=lerp(hash(ix,iy+1,iz),hash(ix+1,iy+1,iz),fx);
        double c=lerp(hash(ix,iy,iz+1),hash(ix+1,iy,iz+1),fx),d=lerp(hash(ix,iy+1,iz+1),hash(ix+1,iy+1,iz+1),fx);
        return lerp(lerp(a,b,fy),lerp(c,d,fy),fz);
    }
    private static double fbm(double x,double y,double z){return .55*noise(x,y,z)+.3*noise(x*2.03+5.1,y*2.03+1.7,z*2.03+9.3)+.15*noise(x*4.1+3.3,y*4.1+7.7,z*4.1+2.2);}
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
}
