package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * The giant sword of the empowered fourth as drawn (2.80.2, user request: "검 모델링 제대로 만들어 영상이랑 똑같은 검 모델로",
 * "검 더 커야 돼", the size chosen "키의 4배 (약 7블록)", and the outline the user drew round it on the frames of their videos):
 * a sword of blue light some 7 blocks long, not the sword in their hand. Its blade is broad and straight with a rounded end
 * and a dark groove down its middle; its guard flares out to a point either side, curls rising from it beside the blade,
 * an oval gem at its middle; then a long narrow grip and a round pommel (the video from above, the user's outline).
 * <p>Its shape is fixed here in its own frame: along it from the middle of its grip (+y toward its end), across its flat
 * (+x) and through its thickness (+z), blocks at full size. It is drawn in the effect layer: its faces in the blended pass,
 * only those facing the camera (so its back never shows through its front), light blue and a little see-through, lit
 * from above and brighter at their rims as glass is; its edges white-hot and its glow in the light. It can be heated
 * (white, as it shows beside them) and inked (dark, as the ink swallows it).
 */
final class SpiritSword {
    private SpiritSword(){}

    /** Along it from the middle of its grip (blocks at full size): the pommel's end, the grip's ends, the guard's points, the blade's base and its end. */
    static final double POMMEL=-1.25,GRIP_LOW=-.75,GRIP_HIGH=.75,GUARD_POINT=1.05,BLADE_BASE=1.55,TIP=5.75;
    /** Across it: the blade's half width and its flat's, the guard's points, the grip's radius. */
    static final double BLADE=.48,FLAT=.3,GUARD=1.07,GRIP=.175;
    /** Through it: the blade's flat and its edges, the guard's faces. */
    static final double FLAT_Z=.065,EDGE_Z=.015,GUARD_Z=.14;
    /** How long the rounded end of the blade is. */
    static final double END=.45;
    /** The groove down the blade: its half width and where it runs. */
    static final double GROOVE=.075,GROOVE_LOW=BLADE_BASE+.25,GROOVE_HIGH=TIP-.85;
    /** The gem at the guard's middle: where along it and its half sizes (across, along). */
    static final double GEM_AT=(GRIP_HIGH+BLADE_BASE)*.5,GEM_X=.17,GEM_Y=.2;
    /** The pommel's outline as it turns about its axis: (along, radius) from its end to the grip. */
    static final double[][] POMMEL_SHAPE={{POMMEL,0},{POMMEL+.04,.14},{POMMEL+.12,.26},{POMMEL+.24,.32},{POMMEL+.36,.29},{POMMEL+.45,.22},{GRIP_LOW,GRIP}};
    /** How long it is from the pommel's end to the blade's (blocks at full size: the user's 7). */
    static final double LENGTH=TIP-POMMEL;

    enum Role{FLAT,BEVEL,GROOVE,GUARD,GEM,GRIP,BAND,POMMEL}
    /** A face in its own frame: its corners (three or four), its outward normal, what it is. */
    record Face(Vec3d[] v,Vec3d n,Role role){}
    /** A line of it: an outline (white-hot) or an inner line (pale), in its own frame. */
    record Edge(Vec3d a,Vec3d b,boolean outline){}
    static final List<Face> FACES=new ArrayList<>();
    static final List<Edge> EDGES=new ArrayList<>();
    static{build();}

    /** The blade's half width at {@code y} along it (0 off the blade): straight, then rounding to its end. */
    static double half(double y){
        if(y<BLADE_BASE-1e-9||y>TIP+1e-9)return 0;
        double from=TIP-END;if(y<=from)return BLADE;
        double u=Math.min(1,(y-from)/END);return BLADE*Math.sqrt(Math.max(0,1-u*u));
    }
    /** Where along the blade its faces are cut (the rounded end in ten steps). */
    static double[] bladeCuts(){
        List<Double> ys=new ArrayList<>(List.of(BLADE_BASE,GROOVE_LOW,(GROOVE_LOW+GROOVE_HIGH)*.5,GROOVE_HIGH,TIP-END));
        for(int i=1;i<=10;i++)ys.add(TIP-END+END*(1-Math.cos(Math.PI*.5*i/10)));
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
    private static void build(){
        double[] ys=bladeCuts();
        // the blade: on either face a flat down its middle between two bevels falling to its edges
        for(int s=-1;s<=1;s+=2){
            Vec3d out=v(0,0,s);
            for(int i=0;i+1<ys.length;i++){
                double y0=ys[i],y1=ys[i+1],h0=half(y0),h1=half(y1),f0=h0*FLAT/BLADE,f1=h1*FLAT/BLADE;
                face(Role.BEVEL,v(-1,0,s*.3),v(-h0,y0,s*EDGE_Z),v(-f0,y0,s*FLAT_Z),v(-f1,y1,s*FLAT_Z),v(-h1,y1,s*EDGE_Z));
                face(Role.FLAT,out,v(-f0,y0,s*FLAT_Z),v(f0,y0,s*FLAT_Z),v(f1,y1,s*FLAT_Z),v(-f1,y1,s*FLAT_Z));
                face(Role.BEVEL,v(1,0,s*.3),v(f0,y0,s*FLAT_Z),v(h0,y0,s*EDGE_Z),v(h1,y1,s*EDGE_Z),v(f1,y1,s*FLAT_Z));
            }
            // the groove, its ends drawn in to points
            double z=s*(FLAT_Z+.003),lo=GROOVE_LOW,hi=GROOVE_HIGH,taper=.18;
            face(Role.GROOVE,out,v(0,lo,z),v(GROOVE,lo+taper,z),v(-GROOVE,lo+taper,z));
            face(Role.GROOVE,out,v(-GROOVE,lo+taper,z),v(GROOVE,lo+taper,z),v(GROOVE,hi-taper,z),v(-GROOVE,hi-taper,z));
            face(Role.GROOVE,out,v(-GROOVE,hi-taper,z),v(GROOVE,hi-taper,z),v(0,hi,z));
        }
        // the blade's outline (its edges and its rounded end) and the lines where its bevels meet its flat
        for(int i=0;i+1<ys.length;i++){
            double y0=ys[i],y1=ys[i+1],h0=half(y0),h1=half(y1);
            EDGES.add(new Edge(v(-h0,y0,0),v(-h1,y1,0),true));EDGES.add(new Edge(v(h0,y0,0),v(h1,y1,0),true));
            for(int s=-1;s<=1;s+=2){double f0=h0*FLAT/BLADE,f1=h1*FLAT/BLADE;EDGES.add(new Edge(v(-f0,y0,s*FLAT_Z),v(-f1,y1,s*FLAT_Z),false));EDGES.add(new Edge(v(f0,y0,s*FLAT_Z),v(f1,y1,s*FLAT_Z),false));}
        }
        // the guard: flaring out to a point either side, from the grip up to the blade's base, as thick as a slab
        Vec3d[] g={v(GRIP+.02,GRIP_HIGH,0),v(GUARD,GUARD_POINT,0),v(BLADE,BLADE_BASE,0),v(-BLADE,BLADE_BASE,0),v(-GUARD,GUARD_POINT,0),v(-GRIP-.02,GRIP_HIGH,0)};
        Vec3d mid=v(0,(GRIP_HIGH+BLADE_BASE)*.5,0);
        for(int s=-1;s<=1;s+=2){
            for(int i=0;i<g.length;i++){Vec3d a=g[i],b=g[(i+1)%g.length];face(Role.GUARD,v(0,0,s),mid.add(0,0,s*GUARD_Z),a.add(0,0,s*GUARD_Z),b.add(0,0,s*GUARD_Z));}
            for(int i=0;i<g.length;i++)EDGES.add(new Edge(g[i].add(0,0,s*GUARD_Z),g[(i+1)%g.length].add(0,0,s*GUARD_Z),true));
        }
        for(int i=0;i<g.length;i++){
            Vec3d a=g[i],b=g[(i+1)%g.length],d=b.subtract(a),out=v(d.y,-d.x,0);
            face(Role.GUARD,out,a.add(0,0,-GUARD_Z),b.add(0,0,-GUARD_Z),b.add(0,0,GUARD_Z),a.add(0,0,GUARD_Z));
        }
        // the curls rising from the guard beside the blade, cut into both its faces
        double[][] curl={{.5,1.5},{.66,1.44},{.79,1.33},{.78,1.21},{.67,1.15},{.58,1.2},{.6,1.29}};
        for(int s=-1;s<=1;s+=2)for(int side=-1;side<=1;side+=2)for(int i=0;i+1<curl.length;i++)
            EDGES.add(new Edge(v(side*curl[i][0],curl[i][1],s*(GUARD_Z+.004)),v(side*curl[i+1][0],curl[i+1][1],s*(GUARD_Z+.004)),false));
        // the oval gem at its middle, raised a little on both faces
        int n=14;
        for(int s=-1;s<=1;s+=2){
            Vec3d c=v(0,GEM_AT,s*(GUARD_Z+.035));
            for(int i=0;i<n;i++){
                double a0=2*Math.PI*i/n,a1=2*Math.PI*(i+1)/n;
                Vec3d p0=v(GEM_X*Math.cos(a0),GEM_AT+GEM_Y*Math.sin(a0),s*(GUARD_Z+.01)),p1=v(GEM_X*Math.cos(a1),GEM_AT+GEM_Y*Math.sin(a1),s*(GUARD_Z+.01));
                face(Role.GEM,v(0,0,s),c,p0,p1);
                EDGES.add(new Edge(p0,p1,false));
            }
        }
        // the grip: eight-sided, wound with four darker bands
        int sides=8;
        for(int i=0;i<sides;i++){
            double a0=2*Math.PI*(i+.5)/sides,a1=2*Math.PI*(i+1.5)/sides,am=(a0+a1)*.5;
            Vec3d out=v(Math.cos(am),0,Math.sin(am));
            face(Role.GRIP,out,v(GRIP*Math.cos(a0),GRIP_LOW,GRIP*Math.sin(a0)),v(GRIP*Math.cos(a1),GRIP_LOW,GRIP*Math.sin(a1)),v(GRIP*Math.cos(a1),GRIP_HIGH,GRIP*Math.sin(a1)),v(GRIP*Math.cos(a0),GRIP_HIGH,GRIP*Math.sin(a0)));
            for(int b=0;b<4;b++){
                double y0=GRIP_LOW+.2+b*(GRIP_HIGH-GRIP_LOW-.4)/3-.04,y1=y0+.08,r=GRIP+.014;
                face(Role.BAND,out,v(r*Math.cos(a0),y0,r*Math.sin(a0)),v(r*Math.cos(a1),y0,r*Math.sin(a1)),v(r*Math.cos(a1),y1,r*Math.sin(a1)),v(r*Math.cos(a0),y1,r*Math.sin(a0)));
            }
        }
        // the round pommel, turned about its axis
        int turns=12;
        for(int k=0;k+1<POMMEL_SHAPE.length;k++){
            double y0=POMMEL_SHAPE[k][0],r0=POMMEL_SHAPE[k][1],y1=POMMEL_SHAPE[k+1][0],r1=POMMEL_SHAPE[k+1][1];
            for(int i=0;i<turns;i++){
                double a0=2*Math.PI*i/turns,a1=2*Math.PI*(i+1)/turns,am=(a0+a1)*.5;
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
    /** Its light (the light pass): its outline white-hot with a blue glow round it, its inner lines pale, a halo along it. */
    static void glow(EffectMesh m,Vec3d grip,Vec3d dir,double scale,float alpha,float heat,float ink){
        float a=alpha*(1-.85f*ink);if(a<=.005f)return;
        Vec3d[] f=frame(dir);
        for(var e:EDGES){
            Vec3d p=world(grip,f,scale,e.a()),q=world(grip,f,scale,e.b());
            if(e.outline()){m.line(p,q,.036*scale,0xffffff,(.55f+.3f*heat)*a);m.softLine(p,q,.3*scale,0x58a0ff,(.14f+.14f*heat)*a);}
            else m.line(p,q,.022*scale,0xdff6ff,.34f*a);
        }
        Vec3d base=world(grip,f,scale,new Vec3d(0,BLADE_BASE,0)),end=world(grip,f,scale,new Vec3d(0,TIP,0)),butt=world(grip,f,scale,new Vec3d(0,POMMEL,0));
        m.softLine(base,end,2.2*scale,0x2a5cff,(.06f+.08f*heat)*a);m.softLine(butt,end,.9*scale,0x9fe8ff,(.04f+.12f*heat)*a);
        m.softDisc(world(grip,f,scale,new Vec3d(0,GEM_AT,0)),.45*scale,0xbfe2ff,.28f*a);
    }
    /** A point along its middle (blocks from its grip) in the world. */
    static Vec3d along(Vec3d grip,Vec3d dir,double scale,double y){return grip.add(dir.normalize().multiply(y*scale));}
}
