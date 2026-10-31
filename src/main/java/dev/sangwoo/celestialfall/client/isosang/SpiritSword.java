package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * The giant sword of the empowered fourth as drawn (2.80.2, user request: "검 모델링 제대로 만들어 영상이랑 똑같은 검 모델로",
 * "검 더 커야 돼", and the outline the user drew round it on the frames of their videos; 2.80.3, user request: "검도 더
 * 날카롭게 만들어 주고", "검 크기도 2배 더 줄이고"): a sword of blue light some 3.5 blocks long (half the 7 of 2.80.2), not
 * the sword in their hand. Its blade is broad and straight, narrowing a little to a sharp point (2.80.2's was rounded), keen
 * bevels either side of a narrow flat with a dark groove down it; its guard sweeps out to a sharp point either side, its
 * points drawn back toward the grip, an oval gem at its middle; then a long narrow grip and a faceted pommel ending in a
 * point (the video from above, the user's outline; the video of the empowered fourth: a sharp blade of light).
 * <p>Its shape is fixed here in its own frame: along it from the middle of its grip (+y toward its point), across its flat
 * (+x) and through its thickness (+z), blocks at full size. It is drawn in the effect layer: its faces in the blended pass,
 * only those facing the camera (so its back never shows through its front), light blue and a little see-through, lit
 * from above and brighter at their rims as glass is; its edges white-hot and its glow in the light. It can be heated
 * (white, as it shows beside them) and inked (dark, as the ink swallows it).
 */
final class SpiritSword {
    private SpiritSword(){}

    /** Along it from the middle of its grip (blocks at full size): the pommel's point, the grip's ends, the blade's base and its point. */
    static final double POMMEL=-.625,GRIP_LOW=-.375,GRIP_HIGH=.375,BLADE_BASE=.775,TIP=2.875;
    /** Across it: the blade's half width at its base and its flat's, the guard's points, the grip's radius. */
    static final double BLADE=.24,FLAT=.1,GUARD=.535,GRIP=.0875;
    /** Through it: the blade's flat and its keen edges, the guard's faces. */
    static final double FLAT_Z=.034,EDGE_Z=.004,GUARD_Z=.07;
    /** The point: how long it is (from where the edges begin to close to the point) and how much the blade narrows before it. */
    static final double POINT=.62,NARROW=.15;
    /** Where the guard's points are along it: drawn back toward the grip, past the guard's foot. */
    static final double GUARD_POINT=GRIP_HIGH-.07;
    /** The groove down the blade: its half width and where it runs (ending short of the point). */
    static final double GROOVE=.034,GROOVE_LOW=BLADE_BASE+.12,GROOVE_HIGH=TIP-POINT-.08;
    /** The gem at the guard's middle: where along it and its half sizes (across, along). */
    static final double GEM_AT=(GRIP_HIGH+BLADE_BASE)*.5+.02,GEM_X=.075,GEM_Y=.095;
    /** The pommel's outline as it turns about its axis: (along, radius) from its point to the grip; faceted, eight sides. */
    static final double[][] POMMEL_SHAPE={{POMMEL,0},{POMMEL+.07,.07},{POMMEL+.15,.12},{POMMEL+.2,.13},{POMMEL+.23,.1},{GRIP_LOW,GRIP}};
    /** How long it is from the pommel's point to the blade's (blocks at full size: half the 7 of 2.80.2). */
    static final double LENGTH=TIP-POMMEL;

    enum Role{FLAT,BEVEL,GROOVE,GUARD,GEM,GRIP,BAND,POMMEL}
    /** A face in its own frame: its corners (three or four), its outward normal, what it is. */
    record Face(Vec3d[] v,Vec3d n,Role role){}
    /** A line of it: an outline (white-hot) or an inner line (pale), in its own frame. */
    record Edge(Vec3d a,Vec3d b,boolean outline){}
    static final List<Face> FACES=new ArrayList<>();
    static final List<Edge> EDGES=new ArrayList<>();

    /**
     * The blade's half width at {@code y} along it (0 off the blade): narrowing a little from its base to where the point
     * begins, then closing to a sharp point, full at first and keen at the end (its edges meet at an angle, not round).
     */
    static double half(double y){
        if(y<BLADE_BASE-1e-9||y>TIP+1e-9)return 0;
        double from=TIP-POINT,w=BLADE*(1-NARROW*Math.min(1,(y-BLADE_BASE)/(from-BLADE_BASE)));
        if(y<=from)return w;
        double u=Math.min(1,(y-from)/POINT),wp=BLADE*(1-NARROW);
        return Math.max(0,wp*(1-u)*(1+.35*u));
    }
    /** Where along the blade its faces are cut (the point in ten steps). */
    static double[] bladeCuts(){
        List<Double> ys=new ArrayList<>(List.of(BLADE_BASE,GROOVE_LOW,(GROOVE_LOW+GROOVE_HIGH)*.5,GROOVE_HIGH,TIP-POINT));
        for(int i=1;i<=10;i++)ys.add(TIP-POINT+POINT*i/10);
        return ys.stream().mapToDouble(Double::doubleValue).toArray();
    }
    private static Vec3d v(double x,double y,double z){return new Vec3d(x,y,z);}
    /** A face with its normal found from its corners, turned to {@code out}'s side. */
    private static void face(Role role,Vec3d out,Vec3d... v){
        Vec3d n=v[1].subtract(v[0]).crossProduct(v[v.length-1].subtract(v[0]));
        if(n.lengthSquared()<1e-14&&v.length>3)n=v[2].subtract(v[1]).crossProduct(v[3].subtract(v[1]));
        if(n.lengthSquared()<1e-14)n=out;
        n=n.normalize();if(n.dotProduct(out)<0)n=n.multiply(-1);
        FACES.add(new Face(v,n,role));
    }
    /** The guard's outline in its plane (x across, y along), going round: from the grip out to the right point and back. */
    static final double[][] GUARD_SHAPE={
        {GRIP+.01,GRIP_HIGH},{GUARD,GUARD_POINT},{.33,.5},{BLADE+.02,BLADE_BASE},
        {-BLADE-.02,BLADE_BASE},{-.33,.5},{-GUARD,GUARD_POINT},{-GRIP-.01,GRIP_HIGH}};
    // (built once every shape it needs is set)
    static{build();}
    private static void build(){
        double[] ys=bladeCuts();
        // the blade: on either face a narrow flat down its middle between two broad bevels falling to keen edges
        for(int s=-1;s<=1;s+=2){
            Vec3d out=v(0,0,s);
            for(int i=0;i+1<ys.length;i++){
                double y0=ys[i],y1=ys[i+1],h0=half(y0),h1=half(y1),f0=h0*FLAT/BLADE,f1=h1*FLAT/BLADE;
                face(Role.BEVEL,v(-1,0,s*.3),v(-h0,y0,s*EDGE_Z),v(-f0,y0,s*FLAT_Z),v(-f1,y1,s*FLAT_Z),v(-h1,y1,s*EDGE_Z));
                face(Role.FLAT,out,v(-f0,y0,s*FLAT_Z),v(f0,y0,s*FLAT_Z),v(f1,y1,s*FLAT_Z),v(-f1,y1,s*FLAT_Z));
                face(Role.BEVEL,v(1,0,s*.3),v(f0,y0,s*FLAT_Z),v(h0,y0,s*EDGE_Z),v(h1,y1,s*EDGE_Z),v(f1,y1,s*FLAT_Z));
            }
            // the groove, its ends drawn in to points
            double z=s*(FLAT_Z+.002),lo=GROOVE_LOW,hi=GROOVE_HIGH,taper=.1;
            face(Role.GROOVE,out,v(0,lo,z),v(GROOVE,lo+taper,z),v(-GROOVE,lo+taper,z));
            face(Role.GROOVE,out,v(-GROOVE,lo+taper,z),v(GROOVE,lo+taper,z),v(GROOVE,hi-taper,z),v(-GROOVE,hi-taper,z));
            face(Role.GROOVE,out,v(-GROOVE,hi-taper,z),v(GROOVE,hi-taper,z),v(0,hi,z));
        }
        // the blade's outline (its edges and its point) and the lines where its bevels meet its flat
        for(int i=0;i+1<ys.length;i++){
            double y0=ys[i],y1=ys[i+1],h0=half(y0),h1=half(y1);
            EDGES.add(new Edge(v(-h0,y0,0),v(-h1,y1,0),true));EDGES.add(new Edge(v(h0,y0,0),v(h1,y1,0),true));
            for(int s=-1;s<=1;s+=2){double f0=h0*FLAT/BLADE,f1=h1*FLAT/BLADE;EDGES.add(new Edge(v(-f0,y0,s*FLAT_Z),v(-f1,y1,s*FLAT_Z),false));EDGES.add(new Edge(v(f0,y0,s*FLAT_Z),v(f1,y1,s*FLAT_Z),false));}
        }
        // the guard: sweeping out to a sharp point either side, drawn back toward the grip, as thick as a slab
        Vec3d[] g=new Vec3d[GUARD_SHAPE.length];for(int i=0;i<g.length;i++)g[i]=v(GUARD_SHAPE[i][0],GUARD_SHAPE[i][1],0);
        Vec3d mid=v(0,(GRIP_HIGH+BLADE_BASE)*.5,0);
        for(int s=-1;s<=1;s+=2){
            for(int i=0;i<g.length;i++){Vec3d a=g[i],b=g[(i+1)%g.length];face(Role.GUARD,v(0,0,s),mid.add(0,0,s*GUARD_Z),a.add(0,0,s*GUARD_Z),b.add(0,0,s*GUARD_Z));}
            for(int i=0;i<g.length;i++)EDGES.add(new Edge(g[i].add(0,0,s*GUARD_Z),g[(i+1)%g.length].add(0,0,s*GUARD_Z),true));
        }
        for(int i=0;i<g.length;i++){
            Vec3d a=g[i],b=g[(i+1)%g.length],d=b.subtract(a),out=v(d.y,-d.x,0);
            // (round the outline the other way, its outward side: the outline goes clockwise seen from +z)
            if(out.dotProduct(a.add(b).multiply(.5).subtract(mid))<0)out=out.multiply(-1);
            face(Role.GUARD,out,a.add(0,0,-GUARD_Z),b.add(0,0,-GUARD_Z),b.add(0,0,GUARD_Z),a.add(0,0,GUARD_Z));
        }
        // a ridge down each wing to its point, cut into both faces
        for(int s=-1;s<=1;s+=2)for(int side=-1;side<=1;side+=2)
            EDGES.add(new Edge(v(side*.12,.52,s*(GUARD_Z+.002)),v(side*(GUARD-.04),GUARD_POINT+.012,s*(GUARD_Z+.002)),false));
        // the oval gem at its middle, raised a little on both faces
        int n=14;
        for(int s=-1;s<=1;s+=2){
            Vec3d c=v(0,GEM_AT,s*(GUARD_Z+.018));
            for(int i=0;i<n;i++){
                double a0=2*Math.PI*i/n,a1=2*Math.PI*(i+1)/n;
                Vec3d p0=v(GEM_X*Math.cos(a0),GEM_AT+GEM_Y*Math.sin(a0),s*(GUARD_Z+.005)),p1=v(GEM_X*Math.cos(a1),GEM_AT+GEM_Y*Math.sin(a1),s*(GUARD_Z+.005));
                face(Role.GEM,v(0,0,s),c,p0,p1);
                EDGES.add(new Edge(p0,p1,false));
            }
        }
        // the grip: eight-sided, wound with three darker bands
        int sides=8;
        for(int i=0;i<sides;i++){
            double a0=2*Math.PI*(i+.5)/sides,a1=2*Math.PI*(i+1.5)/sides,am=(a0+a1)*.5;
            Vec3d out=v(Math.cos(am),0,Math.sin(am));
            face(Role.GRIP,out,v(GRIP*Math.cos(a0),GRIP_LOW,GRIP*Math.sin(a0)),v(GRIP*Math.cos(a1),GRIP_LOW,GRIP*Math.sin(a1)),v(GRIP*Math.cos(a1),GRIP_HIGH,GRIP*Math.sin(a1)),v(GRIP*Math.cos(a0),GRIP_HIGH,GRIP*Math.sin(a0)));
            for(int b=0;b<3;b++){
                double y0=GRIP_LOW+.12+b*(GRIP_HIGH-GRIP_LOW-.24)/2-.02,y1=y0+.04,r=GRIP+.007;
                face(Role.BAND,out,v(r*Math.cos(a0),y0,r*Math.sin(a0)),v(r*Math.cos(a1),y0,r*Math.sin(a1)),v(r*Math.cos(a1),y1,r*Math.sin(a1)),v(r*Math.cos(a0),y1,r*Math.sin(a0)));
            }
        }
        // the pommel, faceted, turned about its axis to a point
        int turns=8;
        for(int k=0;k+1<POMMEL_SHAPE.length;k++){
            double y0=POMMEL_SHAPE[k][0],r0=POMMEL_SHAPE[k][1],y1=POMMEL_SHAPE[k+1][0],r1=POMMEL_SHAPE[k+1][1];
            for(int i=0;i<turns;i++){
                double a0=2*Math.PI*(i+.5)/turns,a1=2*Math.PI*(i+1.5)/turns,am=(a0+a1)*.5;
                Vec3d out=v(Math.cos(am)*(y1-y0),r0-r1,Math.sin(am)*(y1-y0));
                if(r0<1e-9)face(Role.POMMEL,out,v(0,y0,0),v(r1*Math.cos(a1),y1,r1*Math.sin(a1)),v(r1*Math.cos(a0),y1,r1*Math.sin(a0)));
                else face(Role.POMMEL,out,v(r0*Math.cos(a0),y0,r0*Math.sin(a0)),v(r0*Math.cos(a1),y0,r0*Math.sin(a1)),v(r1*Math.cos(a1),y1,r1*Math.sin(a1)),v(r1*Math.cos(a0),y1,r1*Math.sin(a0)));
            }
        }
    }

    // ───────────────────────────── drawing ─────────────────────────────

    static final Vec3d UP=new Vec3d(0,1,0);
    /** Its frame in the world, pointing along {@code dir}, flat (the guard's points level): across, along, through. */
    static Vec3d[] frame(Vec3d dir){
        Vec3d y=dir.normalize(),x=y.crossProduct(UP);
        if(x.lengthSquared()<1e-6)x=new Vec3d(1,0,0);
        x=x.normalize();Vec3d z=x.crossProduct(y).normalize();
        return new Vec3d[]{x,y,z};
    }
    static Vec3d world(Vec3d grip,Vec3d[] f,double scale,Vec3d p){return grip.add(f[0].multiply(p.x*scale)).add(f[1].multiply(p.y*scale)).add(f[2].multiply(p.z*scale));}
    private static int rgb(Role r){
        return switch(r){
            case FLAT->0x8ad6ff;case BEVEL->0xc4ecff;case GROOVE->0x1d47c8;case GUARD->0xd4f2ff;case GEM->0xf0fcff;case GRIP->0x4c9ee8;case BAND->0x2656c4;case POMMEL->0xc8eeff;
        };
    }
    private static float opacity(Role r){
        return switch(r){case FLAT->.8f;case BEVEL->.84f;case GROOVE->.92f;case GEM->.96f;default->.9f;};
    }
    static int mix(int a,int b,double t){
        t=Math.max(0,Math.min(1,t));
        int r=(int)Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=(int)Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t),bl=(int)Math.round((a&255)*(1-t)+(b&255)*t);
        return r<<16|g<<8|bl;
    }
    static int scale(int c,double k){
        int r=(int)Math.min(255,Math.round(((c>>16)&255)*k)),g=(int)Math.min(255,Math.round(((c>>8)&255)*k)),b=(int)Math.min(255,Math.round((c&255)*k));
        return r<<16|g<<8|b;
    }
    /** The light it is lit by: from above, a little from the side. */
    static final Vec3d LIGHT=new Vec3d(.35,1,.25).normalize();
    /**
     * Its body (the blended pass): every face toward the camera, lit from above and brightest at its rim, heated toward
     * white ({@code heat}, 0 to 1) and inked toward the dark ({@code ink}).
     */
    static void body(EffectMesh m,Vec3d camera,Vec3d grip,Vec3d dir,double scale,float alpha,float heat,float ink){
        if(alpha<=.005f)return;
        Vec3d[] f=frame(dir);
        for(var face:FACES){
            Vec3d n=f[0].multiply(face.n().x).add(f[1].multiply(face.n().y)).add(f[2].multiply(face.n().z));
            Vec3d[] w=new Vec3d[face.v().length];Vec3d c=Vec3d.ZERO;
            for(int i=0;i<w.length;i++){w[i]=world(grip,f,scale,face.v()[i]);c=c.add(w[i]);}
            c=c.multiply(1.0/w.length);
            Vec3d view=camera.subtract(c);double facing=n.dotProduct(view);
            if(facing<=0)continue;
            double lit=.72+.28*Math.max(0,n.dotProduct(LIGHT)),rim=1-Math.min(1,facing/Math.max(1e-6,view.length()));
            int col=scale(rgb(face.role()),lit);
            col=mix(col,0xffffff,.28*rim*rim+.72*heat);
            col=mix(col,0x0b1338,.82*ink);
            float a=alpha*opacity(face.role())*(1-.25f*ink);
            if(w.length==3)m.triangle(w[0],w[1],w[2],col,a);else m.quad(w[0],w[1],w[2],w[3],col,a);
        }
    }
    /**
     * Its light (the light pass): its outline white-hot with a blue glow round it, its inner lines pale, a halo along it
     * (2.80.3: the lines and the halo for a sword half the size, crisp so its point and its guard's points read sharp).
     */
    static void glow(EffectMesh m,Vec3d grip,Vec3d dir,double scale,float alpha,float heat,float ink){
        float a=alpha*(1-.85f*ink);if(a<=.005f)return;
        Vec3d[] f=frame(dir);
        for(var e:EDGES){
            Vec3d p=world(grip,f,scale,e.a()),q=world(grip,f,scale,e.b());
            if(e.outline()){m.line(p,q,.022*scale,0xffffff,(.6f+.3f*heat)*a);m.softLine(p,q,.17*scale,0x58a0ff,(.14f+.14f*heat)*a);}
            else m.line(p,q,.014*scale,0xdff6ff,.34f*a);
        }
        Vec3d base=world(grip,f,scale,new Vec3d(0,BLADE_BASE,0)),end=world(grip,f,scale,new Vec3d(0,TIP,0)),butt=world(grip,f,scale,new Vec3d(0,POMMEL,0));
        m.softLine(base,end,1.1*scale,0x2a5cff,(.06f+.08f*heat)*a);m.softLine(butt,end,.45*scale,0x9fe8ff,(.04f+.12f*heat)*a);
        m.softDisc(world(grip,f,scale,new Vec3d(0,GEM_AT,0)),.22*scale,0xbfe2ff,.28f*a);
        // a glint at its point, so the point reads sharp
        m.star(end,.32*scale,0xffffff,(.35f+.4f*heat)*a);
    }
    /** A point along its middle (blocks from its grip) in the world. */
    static Vec3d along(Vec3d grip,Vec3d dir,double scale,double y){return grip.add(dir.normalize().multiply(y*scale));}
}
