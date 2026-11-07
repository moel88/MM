package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * The swords of H's basic attack, 회풍만검, as drawn (2.95.1, user request: "검이 하늘색이여야 하고 지금 검 모델링이 너무 안
 * 좋아 영상이랑 똑같이 만들어봐 6검을"): swords of sky-blue light of their own, not the sword in her hand (2.95.0 drew that
 * one see-through, its dark navy blade and gold guard showing). As the user's video has them (read at five times its size,
 * and the sword its interface shows beside her health, the same sword): a long straight blade, its edges parallel, closing
 * to a sharp point; a small cross-guard whose arms end in sharp points swept a little toward the point; a short grip,
 * narrower than the blade; and a pointed pommel, a diamond. Some 1.6 blocks long, the blade about a twentieth of that
 * across, the hilt (pommel to guard) about a fifth.
 * <p>Its shape is fixed here in its own frame: along it from the middle of its grip (+y toward its point), across its flat
 * (+x) and through its thickness (+z), blocks as drawn. Drawn as SpiritSword is, in the effect layer: its faces in the
 * blended pass, only those toward the camera, pale sky blue and a little see-through, lit from above and brighter at
 * their rims; its outline white and a sky-blue glow round it in the light pass. Its flat faces the way it is told (the
 * swords' fan behind her shows its broad side to her back, where the camera is: SwordVolley).
 */
final class VolleyBlade {
    private VolleyBlade(){}

    /** How long it is (blocks, pommel to point). */
    static final double LENGTH=1.6;
    /** Along it from the middle of its grip: the pommel's point and its widest, the grip's foot (it runs up into the guard), the guard's foot and top, the blade's base (in the guard) and its point. */
    static final double POMMEL=-.208,POMMEL_WIDE=-.152,GRIP_LOW=-.112,GUARD_LOW=.096,GUARD_HIGH=.176,BLADE_BASE=.16,TIP=LENGTH+POMMEL;
    /** Across it: the blade's half width, the guard's points, the grip's and the pommel's half widths. */
    static final double BLADE=.048,GUARD=.13,GRIP=.027,POMMEL_HALF=.042;
    /** Through it: the blade's ridge and its edges, the guard's faces. */
    static final double RIDGE_Z=.013,EDGE_Z=.003,GUARD_Z=.022;
    /** The point: how long it is, from where the edges begin to close. */
    static final double POINT=.15;
    /** Where the guard's points are along it: swept a little toward the blade's point (the video's sword, its interface's). */
    static final double GUARD_POINT=.196;

    enum Role{BLADE,GUARD,GRIP,POMMEL}
    /** A face in its own frame: its corners (three or four), its outward normal, what it is. */
    record Face(Vec3d[] v,Vec3d n,Role role){}
    /** A line of it: its outline or an inner line (the ridge), in its own frame. */
    record Edge(Vec3d a,Vec3d b,boolean outline){}
    static final List<Face> FACES=new ArrayList<>();
    static final List<Edge> EDGES=new ArrayList<>();

    /** The blade's half width at {@code y} along it (0 off the blade): parallel edges, then closing to a sharp point (meeting at an angle). */
    static double half(double y){
        if(y<BLADE_BASE-1e-9||y>TIP+1e-9)return 0;
        double from=TIP-POINT;if(y<=from)return BLADE;
        double u=Math.min(1,(y-from)/POINT);
        return Math.max(0,BLADE*(1-u)*(1+.3*u));
    }
    /** Where along the blade its faces are cut (the point in eight steps). */
    static double[] bladeCuts(){
        List<Double> ys=new ArrayList<>(List.of(BLADE_BASE,(BLADE_BASE+TIP-POINT)*.5,TIP-POINT));
        for(int i=1;i<=8;i++)ys.add(TIP-POINT+POINT*i/8);
        return ys.stream().mapToDouble(Double::doubleValue).toArray();
    }
    private static Vec3d v(double x,double y,double z){return new Vec3d(x,y,z);}
    private static void face(Role role,Vec3d out,Vec3d... v){
        Vec3d n=v[1].subtract(v[0]).crossProduct(v[v.length-1].subtract(v[0]));
        if(n.lengthSquared()<1e-16&&v.length>3)n=v[2].subtract(v[1]).crossProduct(v[3].subtract(v[1]));
        if(n.lengthSquared()<1e-16)n=out;
        n=n.normalize();if(n.dotProduct(out)<0)n=n.multiply(-1);
        FACES.add(new Face(v,n,role));
    }
    /** The guard's outline in its plane (x across, y along), going round: its right half from the grip out to its point and back to the blade, then its left. */
    static final double[][] GUARD_SHAPE={
        {GRIP+.004,GUARD_LOW},{.075,GUARD_LOW+.012},{GUARD,GUARD_POINT},{BLADE+.016,GUARD_HIGH},
        {-BLADE-.016,GUARD_HIGH},{-GUARD,GUARD_POINT},{-.075,GUARD_LOW+.012},{-GRIP-.004,GUARD_LOW}};
    static{build();}
    private static void build(){
        double[] ys=bladeCuts();
        // the blade: on either face two bevels rising from its keen edges to a ridge down its middle
        for(int s=-1;s<=1;s+=2){
            for(int i=0;i+1<ys.length;i++){
                double y0=ys[i],y1=ys[i+1],h0=half(y0),h1=half(y1);
                face(Role.BLADE,v(-.4,0,s),v(-h0,y0,s*EDGE_Z),v(0,y0,s*RIDGE_Z),v(0,y1,s*RIDGE_Z),v(-h1,y1,s*EDGE_Z));
                face(Role.BLADE,v(.4,0,s),v(0,y0,s*RIDGE_Z),v(h0,y0,s*EDGE_Z),v(h1,y1,s*EDGE_Z),v(0,y1,s*RIDGE_Z));
            }
            // its base, closed under the guard
            face(Role.BLADE,v(0,-1,0),v(-BLADE,BLADE_BASE,s*EDGE_Z),v(0,BLADE_BASE,s*RIDGE_Z),v(BLADE,BLADE_BASE,s*EDGE_Z));
        }
        // its edges, thin as they are (seen edge on it is a line of light, not nothing)
        for(int i=0;i+1<ys.length;i++){
            double y0=ys[i],y1=ys[i+1],h0=half(y0),h1=half(y1);
            for(int side=-1;side<=1;side+=2)
                face(Role.BLADE,v(side,0,0),v(side*h0,y0,-EDGE_Z),v(side*h0,y0,EDGE_Z),v(side*h1,y1,EDGE_Z),v(side*h1,y1,-EDGE_Z));
        }
        // its outline and its ridge
        for(int i=0;i+1<ys.length;i++){
            double y0=ys[i],y1=ys[i+1],h0=half(y0),h1=half(y1);
            EDGES.add(new Edge(v(-h0,y0,0),v(-h1,y1,0),true));EDGES.add(new Edge(v(h0,y0,0),v(h1,y1,0),true));
        }
        for(int s=-1;s<=1;s+=2)EDGES.add(new Edge(v(0,GUARD_HIGH,s*RIDGE_Z),v(0,TIP-POINT*.5,s*RIDGE_Z*.5),false));
        // the guard: a slab whose arms end in sharp points, swept a little toward the blade's point
        Vec3d[] g=new Vec3d[GUARD_SHAPE.length];for(int i=0;i<g.length;i++)g[i]=v(GUARD_SHAPE[i][0],GUARD_SHAPE[i][1],0);
        Vec3d mid=v(0,(GUARD_LOW+GUARD_HIGH)*.5,0);
        for(int s=-1;s<=1;s+=2){
            for(int i=0;i<g.length;i++){Vec3d a=g[i],b=g[(i+1)%g.length];face(Role.GUARD,v(0,0,s),mid.add(0,0,s*GUARD_Z),a.add(0,0,s*GUARD_Z),b.add(0,0,s*GUARD_Z));}
            for(int i=0;i<g.length;i++)EDGES.add(new Edge(g[i].add(0,0,s*GUARD_Z),g[(i+1)%g.length].add(0,0,s*GUARD_Z),true));
        }
        for(int i=0;i<g.length;i++){
            Vec3d a=g[i],b=g[(i+1)%g.length],d=b.subtract(a),out=v(d.y,-d.x,0);
            if(out.dotProduct(a.add(b).multiply(.5).subtract(mid))<0)out=out.multiply(-1);
            face(Role.GUARD,out,a.add(0,0,-GUARD_Z),b.add(0,0,-GUARD_Z),b.add(0,0,GUARD_Z),a.add(0,0,GUARD_Z));
        }
        // the grip: four-sided, a diamond across (its corners on the flat and through it)
        double[][] corner={{GRIP,0},{0,GRIP*.8},{-GRIP,0},{0,-GRIP*.8}};
        for(int i=0;i<4;i++){
            double[] p=corner[i],q=corner[(i+1)%4];Vec3d out=v(p[0]+q[0],0,p[1]+q[1]);
            face(Role.GRIP,out,v(p[0],GRIP_LOW,p[1]),v(q[0],GRIP_LOW,q[1]),v(q[0],GUARD_LOW,q[1]),v(p[0],GUARD_LOW,p[1]));
        }
        // the pommel: a diamond turned about its axis, widest a little below the grip, coming to a point
        double[][] shape={{POMMEL,0},{POMMEL_WIDE,POMMEL_HALF},{GRIP_LOW,GRIP}};
        for(int k=0;k+1<shape.length;k++){
            double y0=shape[k][0],r0=shape[k][1],y1=shape[k+1][0],r1=shape[k+1][1];
            for(int i=0;i<4;i++){
                double[] p=corner[i],q=corner[(i+1)%4];
                double px0=p[0]/GRIP*r0,pz0=p[1]/GRIP*r0,qx0=q[0]/GRIP*r0,qz0=q[1]/GRIP*r0,px1=p[0]/GRIP*r1,pz1=p[1]/GRIP*r1,qx1=q[0]/GRIP*r1,qz1=q[1]/GRIP*r1;
                Vec3d out=v(p[0]+q[0],0,p[1]+q[1]).normalize().multiply(y1-y0).add(0,r0-r1,0);
                if(r0<1e-9)face(Role.POMMEL,out,v(0,y0,0),v(qx1,y1,qz1),v(px1,y1,pz1));
                else face(Role.POMMEL,out,v(px0,y0,pz0),v(qx0,y0,qz0),v(qx1,y1,qz1),v(px1,y1,pz1));
            }
            EDGES.add(new Edge(v(r0,y0,0),v(r1,y1,0),true));EDGES.add(new Edge(v(-r0,y0,0),v(-r1,y1,0),true));
        }
        EDGES.add(new Edge(v(GRIP,GRIP_LOW,0),v(GRIP,GUARD_LOW,0),true));EDGES.add(new Edge(v(-GRIP,GRIP_LOW,0),v(-GRIP,GUARD_LOW,0),true));
    }

    // ───────────────────────────── drawing ─────────────────────────────

    static final Vec3d UP=new Vec3d(0,1,0);
    /**
     * Its frame in the world, pointing along {@code dir}, its flat facing as near {@code flat} as it can (across, along,
     * through); straight up if {@code flat} lies along it.
     */
    static Vec3d[] frame(Vec3d dir,Vec3d flat){
        Vec3d y=dir.normalize(),z=flat.subtract(y.multiply(flat.dotProduct(y)));
        if(z.lengthSquared()<1e-6)z=UP.subtract(y.multiply(y.y));
        if(z.lengthSquared()<1e-6)z=new Vec3d(1,0,0).subtract(y.multiply(y.x));
        z=z.normalize();Vec3d x=y.crossProduct(z).normalize();
        return new Vec3d[]{x,y,z};
    }
    static Vec3d world(Vec3d grip,Vec3d[] f,double scale,Vec3d p){return grip.add(f[0].multiply(p.x*scale)).add(f[1].multiply(p.y*scale)).add(f[2].multiply(p.z*scale));}
    /** Its colours: sky blue, the blade palest (white at its rims), the grip deeper. */
    static int rgb(Role r){return switch(r){case BLADE->0xc2ecff;case GUARD->0xccf2ff;case GRIP->0x7cc4f2;case POMMEL->0xc4ecff;};}
    private static float opacity(Role r){return switch(r){case BLADE->.88f;case GRIP->.92f;default->.9f;};}
    static final Vec3d LIGHT=new Vec3d(.35,1,.25).normalize();
    /** Its body (the blended pass): every face toward the camera, sky blue, lit from above and brightest at its rim. */
    static void body(EffectMesh m,Vec3d camera,Vec3d grip,Vec3d dir,Vec3d flat,double scale,float alpha){
        if(alpha<=.005f||scale<=1e-4)return;
        Vec3d[] f=frame(dir,flat);
        for(var face:FACES){
            Vec3d n=f[0].multiply(face.n().x).add(f[1].multiply(face.n().y)).add(f[2].multiply(face.n().z));
            Vec3d[] w=new Vec3d[face.v().length];Vec3d c=Vec3d.ZERO;
            for(int i=0;i<w.length;i++){w[i]=world(grip,f,scale,face.v()[i]);c=c.add(w[i]);}
            c=c.multiply(1.0/w.length);
            Vec3d view=camera.subtract(c);double facing=n.dotProduct(view);
            if(facing<=0)continue;
            double lit=.8+.2*Math.max(0,n.dotProduct(LIGHT)),rim=1-Math.min(1,facing/Math.max(1e-6,view.length()));
            int col=SpiritSword.mix(SpiritSword.scale(rgb(face.role()),lit),0xffffff,.15+.4*rim*rim);
            float a=alpha*opacity(face.role());
            if(w.length==3)m.triangle(w[0],w[1],w[2],col,a);else m.quad(w[0],w[1],w[2],w[3],col,a);
        }
    }
    /**
     * Its light (the light pass): its outline white with a sky-blue glow round it, its ridge pale, and a sky-blue halo along
     * the whole of it (the video's swords glow round their shape; the shape still shows through it).
     */
    static void glow(EffectMesh m,Vec3d grip,Vec3d dir,Vec3d flat,double scale,float alpha){
        if(alpha<=.005f||scale<=1e-4)return;
        Vec3d[] f=frame(dir,flat);
        for(var e:EDGES){
            Vec3d p=world(grip,f,scale,e.a()),q=world(grip,f,scale,e.b());
            if(e.outline()){m.line(p,q,.014*scale,0xeefbff,.75f*alpha);m.softLine(p,q,.05*scale,0x8fd6ff,.22f*alpha);}
            else m.line(p,q,.01*scale,0xe4f8ff,.45f*alpha);
        }
        Vec3d butt=world(grip,f,scale,new Vec3d(0,POMMEL,0)),base=world(grip,f,scale,new Vec3d(0,BLADE_BASE,0)),end=world(grip,f,scale,new Vec3d(0,TIP,0));
        m.softLine(butt,end,.26*scale,0x4aa8ff,.1f*alpha);
        m.softLine(base,end,.1*scale,0xa8e2ff,.2f*alpha);
        // (the video's blades are filled with light, palest down their middle: a bright core along the blade, the guard lit)
        Vec3d near=world(grip,f,scale,new Vec3d(0,TIP-POINT*.4,0));
        m.softLine(base,near,.06*scale,0xe2f8ff,.5f*alpha);
        Vec3d gl=world(grip,f,scale,new Vec3d(-GUARD*.8,GUARD_POINT,0)),gr=world(grip,f,scale,new Vec3d(GUARD*.8,GUARD_POINT,0));
        m.softLine(gl,gr,.05*scale,0xe2f8ff,.4f*alpha);
        m.softDisc(world(grip,f,scale,new Vec3d(0,(GUARD_LOW+GUARD_HIGH)*.5,0)),.09*scale,0xc4ecff,.2f*alpha);
        m.softDisc(end,.06*scale,0xf2fdff,.3f*alpha);
    }
}
