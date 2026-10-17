package dev.sangwoo.celestialfall.client.atomic;

import dev.sangwoo.celestialfall.client.vfx.Vfx;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The opening pillar's leading edge, on the VFX layer, for viewers outside the damage column: a white-hot contact
 * line where the wall meets the ground with violet light, jagged rays and cracks thrown ahead of it, a sheet of light
 * rising from the wall's foot, chunks of ground and a rolling wall of dust shoved outward, sparks spraying from the
 * foot and arcs crawling up the wall; when the pillar reaches its full radius a shock ring and a burst of dust slam
 * outward. Everything follows the pillar's own clock and radius (sec here is that clock), so it moves with the edge
 * the server's damage column follows. The far half of the pillar's surroundings is drawn before the pillar so its
 * opaque body covers it, the near half after it.
 */
final class AtomicEdge {
    private AtomicEdge(){}

    /** Blocks the light, rays and cracks reach past the wall. */
    static final double REACH=9;
    /** Each of the debris lanes launches a chunk about this often (seconds) while the wall pushes; a chunk lives up to LIFE. */
    static final float SPAWN=.3f,LIFE=1.7f;

    /** Everything behind the pillar's axis as seen from the camera (pass -1), drawn before the pillar. */
    static void far(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int quality){
        draw(s,sec,rot,cam,o,R,r,quality,-1);
    }
    /** The sheet on the wall and everything on the camera's side (pass +1), drawn after the pillar. */
    static void near(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int quality){
        float edge=AtomicPillarShape.edge(sec,R);
        if(edge>.01f&&r>.3)sheet(sec,rot,cam,o,R,r,edge);
        draw(s,sec,rot,cam,o,R,r,quality,1);
        if(edge>.01f&&r>.3)arcs(s,sec,rot,cam,o,R,r,quality,edge);
    }
    private static void draw(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int quality,int pass){
        float edge=AtomicPillarShape.edge(sec,R);
        if(edge>.01f&&r>.3)ground(sec,rot,cam,o,r,edge,pass);
        slamRing(sec,rot,cam,o,R,r,pass);
        if(r>.3){
            dust(s,sec,rot,cam,o,R,r,quality,pass);
            debris(s,sec,rot,cam,o,R,r,quality,pass);
            if(edge>.01f)sparks(s,sec,rot,cam,o,R,r,quality,pass,edge);
        }
    }
    /** True when a point at angle {@code ang} around the axis lies in this pass's half (+1 the camera's side). */
    private static boolean inPass(double ang,double[] toCam,int pass){return (Math.cos(ang)*toCam[0]+Math.sin(ang)*toCam[1]>0)==(pass>0);}

    /** The contact line, the light, the rays and the cracks on the ground just past the wall. */
    private static void ground(float sec,Matrix4f rot,Vec3d cam,Vec3d o,double r,float level,int pass){
        double outer=r+REACH;double[] d=AtomicPillar.toCamera(cam,o);
        Vfx.material(Vfx.EDGE,sec,(float)(r/outer),Math.min(1.6f,level),(float)outer,.4f+.6f*Math.min(1,level),pass,(float)d[0],(float)d[1],0);
        Vfx.disc(rot,cam,o.x,o.y+.11,o.z,outer);
    }
    /** A sheet of light just outside the wall, from under the ground to about a third of R up. */
    private static void sheet(float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,float level){
        double rad=r+.45,h=3+.3*R;
        boolean inside=Math.hypot(cam.x-o.x,cam.z-o.z)<rad&&cam.y>o.y-.5&&cam.y<o.y-.5+h;
        Vfx.material(Vfx.EDGE_WALL,sec,Math.min(1.6f,level*1.25f),(float)h,0,0,0,0,0,0);
        Vfx.cylinder(rot,cam,o.x,o.y-.5,o.z,rad,h,inside);
    }
    /** The ground shock the slam sends out past the full radius (the ignition wave's look). */
    private static void slamRing(float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int pass){
        float level=AtomicPillarShape.slam(sec,R);double front=AtomicPillarShape.slamFront(sec,R);
        if(level<=.01f||front<R)return;
        double[] d=AtomicPillar.toCamera(cam,o);
        Vfx.material(Vfx.RING,sec,(float)Math.min(.3,5/front),.9f,Math.min(1.5f,level*1.6f),0,pass,(float)d[0],(float)d[1],(float)Math.min(1,r/front));
        Vfx.disc(rot,cam,o.x,o.y+.13,o.z,front);
    }

    /**
     * When (real seconds of the cast) the crater ring starting at radius rho erupts for viewers outside: as the opening
     * wall reaches it, or past the full radius as the slam's ground shock (30 blocks a second) does.
     */
    static float slabRise(double rho,double R){
        float open=AtomicPillarShape.open(R);
        if(rho>=R)return open+(float)((rho-R)/30);
        float lo=AtomicLook.IMPACT,hi=open;
        for(int i=0;i<24;i++){float mid=(lo+hi)/2;if(AtomicPillarShape.radius(AtomicStage.visual(mid),R)>=rho)hi=mid;else lo=mid;}
        return hi;
    }
    /**
     * Where a debris chunk launched from a lane is at sec (pillar clock), or null when it is not in the air: lanes around
     * the axis launch every {@link #SPAWN} seconds (skipping some) while the wall pushes, each chunk leaving the wall at
     * the radius it had then, thrown outward and up harder the harder the wall pushed, falling under gravity and
     * settling for a third of a second after it lands. Returns {x, y, z offsets from the base centre, seconds since
     * launch, landing time, size factor 0..1}.
     */
    static double[] chunk(float seed,int lane,int lanes,int launchIndex,float sec,double R){
        double k=seed*.0137+lane*7.31+launchIndex*3.17;
        float launch=AtomicLook.IMPACT+.15f+launchIndex*SPAWN+(float)(AtomicWorld.h(k)*SPAWN);
        if(sec<launch||launch>AtomicPillarShape.open(R)||AtomicWorld.h(k+1.3)>.8)return null;
        float push=AtomicPillarShape.push(launch,R);if(push<.08f)return null;
        double t=sec-launch,vo=(3+6*AtomicWorld.h(k+3.3))*push,vu=(6+8*AtomicWorld.h(k+4.7))*push,g=22,land=2*vu/g;
        if(t>land+.35)return null;
        double ang=(lane+AtomicWorld.h(k+2.1))*Math.PI*2/lanes,rho=AtomicPillarShape.radius(launch,R)+.25+vo*Math.min(t,land);
        double y=t<land?vu*t-.5*g*t*t:-.45*(t-land)/.35;
        return new double[]{Math.cos(ang)*rho,y,Math.sin(ang)*rho,t,land,AtomicWorld.h(k+5.9)};
    }
    /** Chunks of ground torn up at the wall and flung outward, tumbling; those the wall catches up with are swallowed. */
    private static void debris(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int quality,int pass){
        if(sec<AtomicLook.IMPACT||sec>AtomicPillarShape.open(R)+LIFE)return;
        int lanes=quality>1?96:quality>0?60:30;
        int last=(int)Math.floor((sec-AtomicLook.IMPACT-.15f)/SPAWN),first=Math.max(0,last-(int)Math.ceil(LIFE/SPAWN));
        double[] toCam=AtomicPillar.toCamera(cam,o);
        record Piece(Vec3d p,double size,float spin,Vec3d axis,float alpha){}
        List<Piece> pieces=new ArrayList<>();
        for(int lane=0;lane<lanes;lane++)for(int j=first;j<=last;j++){
            double[] c=chunk(s.seed,lane,lanes,j,sec,R);if(c==null)continue;
            double rho=Math.hypot(c[0],c[2]);if(rho<r+.15)continue;
            if(!inPass(Math.atan2(c[2],c[0]),toCam,pass))continue;
            Vec3d p=o.add(c[0],c[1]+.2,c[2]);
            float alpha=(1-AtomicLook.smooth((float)c[3],(float)c[4],(float)c[4]+.35f))*AtomicWorld.near(cam,p,1,2.5);
            if(alpha<=.05f)continue;
            double h=AtomicWorld.h(c[5]*91+lane);
            pieces.add(new Piece(p,.2+.35*c[5],(float)((4+6*h)*c[3]),new Vec3d(h-.5,.6,AtomicWorld.h(h*13)-.5).normalize(),alpha));
        }
        if(pieces.isEmpty())return;
        pieces.sort(Comparator.comparingDouble((Piece q)->-q.p().squaredDistanceTo(cam)));
        var b=Vfx.sprites(rot,cam,Vfx.SOLID,sec,0,0,0,0);
        int k=0;
        for(var q:pieces){
            Vec3d u=AtomicWorld.turn(new Vec3d(1,0,0),q.axis(),q.spin()).multiply(q.size()),v=AtomicWorld.turn(new Vec3d(0,1,0),q.axis(),q.spin()).multiply(q.size()*.8),
                n=AtomicWorld.turn(new Vec3d(0,0,1),q.axis(),q.spin()).multiply(q.size()*.9);
            Vec3d light=new Vec3d(o.x,q.p().y,o.z).subtract(q.p()).normalize();
            float u0=(k++)*3.7f;
            AtomicPillar.face(b,q.p(),u,v,n,light,q.alpha(),u0);AtomicPillar.face(b,q.p(),u.multiply(-1),n,v,light,q.alpha(),u0+.3f);
            AtomicPillar.face(b,q.p(),v,n,u,light,q.alpha(),u0+.6f);AtomicPillar.face(b,q.p(),v.multiply(-1),u,n,light,q.alpha(),u0+.9f);
            AtomicPillar.face(b,q.p(),n,u,v,light,q.alpha(),u0+1.2f);AtomicPillar.face(b,q.p(),n.multiply(-1),v,u,light,q.alpha(),u0+1.5f);
        }
        Vfx.draw(b,true);
    }
    /**
     * A rolling wall of dust shoved along just ahead of the edge while it pushes; when the pillar slams into its full
     * radius the dust is blown out another dozen blocks and settles.
     */
    private static void dust(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int quality,int pass){
        float push=AtomicPillarShape.push(sec,R),after=sec-AtomicPillarShape.open(R);
        float level=after<0?AtomicLook.smooth(push,.05f,.5f):(1-AtomicLook.smooth(after,.2f,2.2f));
        level*=1-AtomicLook.smooth(sec,AtomicLook.COLLAPSE-.3f,AtomicLook.COLLAPSE);
        if(level<=.02f||sec<AtomicLook.IMPACT)return;
        int n=quality>1?96:quality>0?64:32;
        double[] toCam=AtomicPillar.toCamera(cam,o);
        double blown=after<0?0:12*(1-Math.exp(-after/.55));
        record Puff(Vec3d p,double size,float seed,float alpha){}
        List<Puff> puffs=new ArrayList<>();
        for(int k=0;k<n;k++){
            double h1=AtomicWorld.h(k*1.37+s.seed),h2=AtomicWorld.h(k*2.91+s.seed),h3=AtomicWorld.h(k*5.17+s.seed);
            double ang=(k+h1)*Math.PI*2/n+.04*Math.sin(sec*1.3+k);
            if(!inPass(ang,toCam,pass))continue;
            double rho=r+1.5+3*h2+blown*(.7+.5*h3)+.6*Math.sin(sec*2.1+k*1.7);
            double y=o.y+.6+2.2*h3+(after>0?1.2*after:0);
            Vec3d p=new Vec3d(o.x+Math.cos(ang)*rho,y,o.z+Math.sin(ang)*rho);
            double size=(2+3*h1)*(after>0?1+.35*Math.min(after,2):.75+.25*push);
            float alpha=level*(.55f+.3f*(float)h2)*AtomicWorld.near(cam,p,1.5,5);
            if(alpha>.02f)puffs.add(new Puff(p,size,(float)h3,alpha));
        }
        if(puffs.isEmpty())return;
        puffs.sort(Comparator.comparingDouble((Puff q)->-q.p().squaredDistanceTo(cam)));
        var b=Vfx.sprites(rot,cam,Vfx.SMOKE,sec,.8f,.38f,0,0);
        for(var q:puffs)b.billboard(q.p(),q.size(),((int)(q.seed()*255))<<16,q.alpha());
        Vfx.draw(b,false);
    }
    /** Sparks spraying out from the wall's foot, stretched along their flight. */
    private static void sparks(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int quality,int pass,float level){
        int n=quality>1?110:quality>0?70:34;
        double[] toCam=AtomicPillar.toCamera(cam,o);
        var b=Vfx.sprites(rot,cam,Vfx.SPARK,sec,0,0,0,0);
        for(int k=0;k<n;k++){
            double life=.32+.3*AtomicWorld.h(k*1.9+s.seed);
            double cyc=(sec-AtomicLook.IMPACT)/life+AtomicWorld.h(k*3.3);int c=(int)Math.floor(cyc);double u=cyc-c,t=u*life;
            double ang=AtomicWorld.h(c*5.1+k*2.3+s.seed)*Math.PI*2;
            if(!inPass(ang,toCam,pass))continue;
            double vo=8+8*AtomicWorld.h(c*3.7+k),vu=2.5+6*AtomicWorld.h(c*6.1+k*1.3),t0=Math.max(0,t-.045);
            Vec3d a=pos(o,ang,r+.15+vo*t0,.25+vu*t0-9*t0*t0),p=pos(o,ang,r+.15+vo*t,.25+vu*t-9*t*t);
            float alpha=Math.min(1,level)*(float)(1-u)*AtomicWorld.near(cam,p,1,3);
            if(alpha<=.02f)continue;
            b.streak(a,p,.07+.06*AtomicWorld.h(k*8.3),k%3==0?0xffffff:0xe0c8ff,alpha);
        }
        Vfx.draw(b,false);
    }
    private static Vec3d pos(Vec3d o,double ang,double rho,double y){return new Vec3d(o.x+Math.cos(ang)*rho,o.y+y,o.z+Math.sin(ang)*rho);}
    /** Arcs of light crawling up the wall's foot on the side facing the camera, flickering on and off. */
    private static void arcs(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int quality,float level){
        int n=quality>1?9:quality>0?6:3;
        double facing=Math.atan2(cam.z-o.z,cam.x-o.x),rad=r+.2;
        var b=Vfx.sprites(rot,cam,Vfx.SPARK,sec,0,0,0,0);
        for(int k=0;k<n;k++){
            long flick=(long)Math.floor(sec*14+k*.37);
            if(AtomicWorld.h(flick*1.7+k*9.1+s.seed)>.55)continue;
            double base=facing+(AtomicWorld.h(flick*2.3+k*5.7)-.5)*Math.PI*.85,height=2+(3+.08*R)*AtomicWorld.h(flick*3.1+k);
            int seg=6;Vec3d prev=pos(o,base,rad,-.2);
            for(int i=1;i<=seg;i++){
                double f=i/(double)seg,wob=(AtomicWorld.h(flick*4.7+k*3.3+i*1.9)-.5)*1.6/Math.max(rad,1);
                Vec3d next=pos(o,base+wob,rad,f*height);
                float alpha=Math.min(1,level)*(float)(1-.6*f)*AtomicWorld.near(cam,next,1,3);
                b.streak(prev,next,.09,0xf4e8ff,alpha);prev=next;
            }
        }
        Vfx.draw(b,false);
    }
}
