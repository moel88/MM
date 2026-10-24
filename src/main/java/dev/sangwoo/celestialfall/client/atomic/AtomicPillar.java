package dev.sangwoo.celestialfall.client.atomic;

import dev.sangwoo.celestialfall.CelestialFall;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.AtomicClient;
import dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm;
import dev.sangwoo.celestialfall.client.vfx.Vfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * G's light pillar as seen from outside the blast, drawn on the VFX layer: one straight GPU cylinder
 * of exactly the damage radius whose material scrolls layered noise (rising wave bands, white flow
 * lines of several widths and speeds, surges) over light violet, thousands of white energy streaks
 * racing up its surface layer (moved on the GPU), a glowing floor, the ignition wave, the leading edge
 * tearing up the ground as it opens ({@link AtomicEdge}), debris and sparks caught in the near surface,
 * heat shimmer where the pillar stood, then dust and embers.
 * The pillar and its streaks never reach past the damage radius; only light and air do.
 *
 * <p>Viewers in the damage column (the hit view) keep the interior renderer of {@link AtomicWorld}
 * and the screen grade exactly as before; nothing here runs for them.
 */
public final class AtomicPillar {
    private AtomicPillar(){}

    /** False until the VFX layer is up (or if it failed): the previous renderer and grade are used instead. */
    public static boolean available(){return Vfx.ready();}

    /**
     * The hit view: the camera is in the damage column as far as it has opened (sec: real seconds of the cast), or the
     * local player is and the camera is their own. Someone the opening pillar has not reached yet still sees it from outside.
     */
    public static boolean interior(AtomicClient.Cast c,Vec3d cam,float sec){
        double R=c.radius;float at=AtomicStage.visual(sec);
        if(AtomicPillarShape.inside(at,cam.x-c.origin.x,cam.y-c.origin.y,cam.z-c.origin.z,R))return true;
        var p=MinecraftClient.getInstance().player;
        return p!=null&&AtomicPillarShape.inside(at,p.getX()-c.origin.x,p.getY()-c.origin.y,p.getZ()-c.origin.z,R)
            &&p.getEyePos().squaredDistanceTo(cam)<AtomicPillarShape.OWN_CAMERA*AtomicPillarShape.OWN_CAMERA;
    }

    static void render(AtomicScene s,float real,Matrix4f rot,Vec3d cam,ClientWorld w){
        double R=28*s.scale;
        Vec3d o=new Vec3d(s.origin.x,s.groundY(w,s.origin.x,s.origin.z),s.origin.z);
        int quality=CelestialFall.CONFIG.visualQuality;
        // The pillar's own clock runs slowed through the first instant of the ignition; sky, shock and clouds keep real time.
        float sec=AtomicStage.visual(real);
        double r=AtomicPillarShape.radius(sec,R),y0=AtomicPillarShape.base(o.y),y1=AtomicPillarShape.top(sec,o.y,R);
        float flow=AtomicPillarShape.flowTime(sec);
        boolean standing=r>.05;
        boolean inside=standing&&Math.hypot(cam.x-o.x,cam.z-o.z)<r&&cam.y>y0&&cam.y<y1;
        float ripple=AtomicPillarShape.ignitionLevel(sec),haze=standing&&r<R*.995?1:0,column=AtomicPillarShape.heatColumn(sec);
        float shock=AtomicStage.shockLevel(real,R);
        boolean refract=quality>1&&Vfx.refractionSupported()&&(ripple>0||haze>0||column>0||shock>0);
        double clouds=cloudHeight(w,o,R,y1);
        // Its light pours into the view of everyone outside who sees it, and close to it the screen is overwhelmed
        // (Veil post), pulsing with the surges.
        float over=AtomicStage.overwhelm(real,Math.hypot(cam.x-o.x,cam.z-o.z),R),shine=AtomicStage.radiance(real,cam.distanceTo(o),R);
        if(over>0||shine>0)screen(over,shine,real,rot,cam,o,r,y1);
        Vfx.begin();
        try{
            ground(sec,rot,cam,o,R,r);
            pool(real,rot,cam,o,R,AtomicPillarShape.column(sec,R));
            // Behind the pillar first, then the pillar covers it, then everything in front of it.
            rings(real,rot,cam,o,R,clouds,-1);
            if(standing)motes(s,sec,rot,cam,o,R,r,y1,quality,-1);
            if(standing)glow(s,sec,rot,cam,o,R,r,y0,y1);
            wave(sec,rot,cam,o,R,r,-1);
            // The leading edge behind the pillar's axis (its body covers what it hides).
            AtomicEdge.far(s,sec,rot,cam,o,R,r,quality);
            if(standing){
                pillar(s,sec,flow,rot,cam,o,R,r,y0,y1,inside);
                streaks(sec,flow,rot,cam,o,R,r,y0,y1,inside,quality);
            }
            if(refract){
                Vfx.captureScene();
                if(ripple>0)ripple(sec,rot,cam,o,R,r);
                if(haze>0)groundHaze(sec,rot,cam,o,R,r);
                if(column>0)heatColumn(s,sec,flow,rot,cam,o,R,column);
            }
            if(shock>0)shock(real,rot,cam,o,R,shock,refract);
            rings(real,rot,cam,o,R,clouds,1);
            if(standing)motes(s,sec,rot,cam,o,R,r,y1,quality,1);
            wave(sec,rot,cam,o,R,r,1);
            AtomicEdge.near(s,sec,rot,cam,o,R,r,quality);
            if(quality>0)debris(s,sec,rot,cam,o,R,r,quality);
            sparks(s,sec,rot,cam,o,R,r,quality);
            dust(s,sec,rot,cam,o,R);
            embers(s,sec,rot,cam,o,R,quality);
            flares(sec,rot,cam,o,R,r,y0,y1);
        }finally{Vfx.end();}
    }

    /**
     * Offers the Veil screen pass: the pillar's axis at eye height and its half width on screen (the overwhelm's
     * centre), and its axis from the ground to its top with the middle of the visible part (where the light comes from).
     */
    private static void screen(float strength,float shine,float real,Matrix4f rot,Vec3d cam,Vec3d o,double r,double y1){
        var proj=RenderSystem.getProjectionMatrix();
        var view=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(rot);
        var p=new Vector4f((float)(o.x-cam.x),(float)(Math.max(o.y,Math.min(y1,cam.y))-cam.y),(float)(o.z-cam.z),1);
        new Matrix4f(proj).mul(view).transform(p);
        float x=.5f,y=.5f,facing=0,half=0;
        if(p.w>.05f){
            float nx=p.x/p.w,ny=p.y/p.w;
            // Kept near the frame: a centre far off screen would smear the whole view.
            x=Math.max(-.25f,Math.min(1.25f,nx*.5f+.5f));y=Math.max(-.25f,Math.min(1.25f,ny*.5f+.5f));
            facing=1-AtomicLook.smooth(Math.abs(nx),1.1f,2.2f);
            half=(float)(r*proj.m00()/p.w*.5);
        }
        var a=view.transform(new Vector4f((float)(o.x-cam.x),(float)(o.y-cam.y),(float)(o.z-cam.z),1));
        var b=view.transform(new Vector4f((float)(o.x-cam.x),(float)(y1-cam.y),(float)(o.z-cam.z),1));
        AtomicOverwhelm.offer(strength,shine,AtomicStage.surge(real),x,y,facing,half,screenAxis(a,b,proj,Math.max(half,.02f)));
    }
    /**
     * The pillar's axis on screen from view-space points a (ground) and b (top): {ax, ay, bx, by, lx, ly, on} in 0..1
     * screen coordinates (y up). The segment is cut where it passes behind the camera; (lx, ly) is the middle of the
     * part within the screen widened by {@code margin}, and on is 1 when there is such a part. Null when the whole
     * axis is behind the camera.
     */
    static float[] screenAxis(Vector4f a,Vector4f b,Matrix4f proj,float margin){
        final float near=-.1f;
        if(a.z>near&&b.z>near)return null;
        Vector4f pa=a.z>near?cut(a,b,near):a,pb=b.z>near?cut(b,a,near):b;
        float[] sa=project(pa,proj),sb=project(pb,proj);
        float dx=sb[0]-sa[0],dy=sb[1]-sa[1],t0=0,t1=1;
        float[][] edges={{-dx,sa[0]+margin},{dx,1+margin-sa[0]},{-dy,sa[1]+margin},{dy,1+margin-sa[1]}};
        boolean on=true;
        for(float[] e:edges){
            if(e[0]==0){if(e[1]<0)on=false;continue;}
            float t=e[1]/e[0];
            if(e[0]<0)t0=Math.max(t0,t);else t1=Math.min(t1,t);
        }
        if(t0>t1)on=false;
        float mid=on?(t0+t1)*.5f:.5f;
        return new float[]{sa[0],sa[1],sb[0],sb[1],sa[0]+dx*mid,sa[1]+dy*mid,on?1:0};
    }
    private static Vector4f cut(Vector4f from,Vector4f to,float z){
        float t=(z-from.z)/(to.z-from.z);
        return new Vector4f(from.x+(to.x-from.x)*t,from.y+(to.y-from.y)*t,z,1);
    }
    private static float[] project(Vector4f v,Matrix4f proj){
        var c=proj.transform(new Vector4f(v.x,v.y,v.z,1));
        return new float[]{c.x/c.w*.5f+.5f,c.y/c.w*.5f+.5f};
    }
    /**
     * Violet light the pillar throws on the ground around it, out to 4.5 R (light, not the blast), brightest at the wall
     * as far as it has opened ({@code opened}, blocks).
     */
    private static void pool(float real,Matrix4f rot,Vec3d cam,Vec3d o,double R,double opened){
        float level=AtomicStage.sky(real);if(level<=.01f)return;
        Vfx.material(Vfx.POOL,real,4.5f,.55f*level,(float)Math.max(.05,Math.min(1,opened/R)),0,0,0,0,0);
        Vfx.disc(rot,cam,o.x,o.y+.09,o.z,4.5*R);
    }
    /** Height of the cloud ring: the world's cloud layer, kept between 2.5 R above the ground and the pillar's top. */
    private static double cloudHeight(ClientWorld w,Vec3d o,double R,double top){
        float h=w.getDimensionEffects().getCloudsHeight();
        double y=Float.isNaN(h)?o.y+5*R:h+1;
        return Math.max(o.y+2.5*R,Math.min(y,o.y+8*R));
    }
    /** Light thrown into the air around the pillar: a soft violet-white glow past its silhouette that carries on above its top. */
    private static void glow(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,double y0,double y1){
        float level=AtomicStage.sky(sec)*.9f+.1f*AtomicLook.smooth(sec,AtomicLook.IMPACT,AtomicLook.PINK);
        level*=AtomicPillarShape.opacity(sec);if(level<=.01f)return;
        double half=r+1.5*R,top=y1+1.8*R;
        var b=Vfx.sprites(rot,cam,Vfx.GLOW,sec,(float)(r/half),1.0f*level,(float)((y1-y0)/(top-y0)),0);
        b.streak(new Vec3d(o.x,y0,o.z),new Vec3d(o.x,top,o.z),half*2,0xffffff,1);
        Vfx.draw(b,false);
    }
    /**
     * The air shock: a dome racing out from the blast at 5.5 R per second, bending the scene where the view grazes it,
     * long after the pillar's own radius (it is air, not the blast).
     */
    private static void shock(float real,Matrix4f rot,Vec3d cam,Vec3d o,double R,float level,boolean refract){
        double radius=AtomicStage.shockRadius(real,R);if(radius<1)return;
        // Only its low band, rolling over the ground: a pressure wave, not a shell over the world.
        Vfx.material(Vfx.SHOCK,real,level,.05f*level,refract?1:0,.33f,0,0,0,0);
        Vfx.dome(rot,cam,o.x,o.y-1,o.z,radius);
    }
    /** The cloud ring pushed out at cloud height and the condensation ring around the pillar's middle; far (pass -1) or near (+1) half. */
    private static void rings(float real,Matrix4f rot,Vec3d cam,Vec3d o,double R,double clouds,int pass){
        double[] d=toCamera(cam,o);
        double outer=AtomicStage.cloudRing(real)*R;float level=AtomicStage.cloudRingLevel(real);
        if(level>.01f&&outer>1.3*R){
            double inner=Math.max(1.02*R,outer*.55);
            Vfx.material(Vfx.CLOUD,real,(float)(inner/outer),.72f*level,1.3f,0,pass,(float)d[0],(float)d[1],0);
            Vfx.disc(rot,cam,o.x,clouds,o.z,outer);
        }
        outer=AtomicStage.condensationRing(real)*R;level=AtomicStage.condensationLevel(real);
        if(level>.01f){
            double inner=Math.max(1.02*R,outer*.78);
            Vfx.material(Vfx.CLOUD,real,(float)(inner/outer),.65f*level,1.2f,1,pass,(float)d[0],(float)d[1],0);
            Vfx.disc(rot,cam,o.x,o.y+2.2*R,o.z,outer);
        }
    }

    /** Unit vector from the cast toward the camera in the ground plane. */
    static double[] toCamera(Vec3d cam,Vec3d o){
        double dx=cam.x-o.x,dz=cam.z-o.z,l=Math.hypot(dx,dz);
        return l<1e-6?new double[]{0,0}:new double[]{dx/l,dz/l};
    }
    /**
     * Blocks of the pillar between its near surface and p along the line of sight (0 when the camera
     * is inside it or p is outside it): how deep in the light something sits.
     */
    static double depthInside(Vec3d cam,Vec3d o,double r,Vec3d p){
        double cx=cam.x-o.x,cz=cam.z-o.z,px=p.x-o.x,pz=p.z-o.z;
        if(cx*cx+cz*cz<=r*r||px*px+pz*pz>=r*r)return 0;
        double dx=px-cx,dz=pz-cz,a=dx*dx+dz*dz,b=2*(cx*dx+cz*dz),c=cx*cx+cz*cz-r*r,disc=b*b-4*a*c;
        if(a<1e-9||disc<0)return 0;
        double tin=(-b-Math.sqrt(disc))/(2*a);
        return Math.max(0,1-tin)*p.distanceTo(cam);
    }

    /**
     * The floor inside the radius lit magenta, rays and waves running outward, a faint spill at the edge: as far as the
     * pillar has opened, and the whole radius from then on (through the collapse and after the beam).
     */
    private static void ground(float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r){
        float g=AtomicPillarShape.groundGlow(sec);if(g<=.005f)return;
        Vfx.material(Vfx.GROUND,sec,0,g,0,0,0,0,0,0);
        Vfx.disc(rot,cam,o.x,o.y+.06,o.z,Math.max(.5,Math.min(R,AtomicPillarShape.column(sec,R)))*1.1);
    }
    /** The ignition wave on the ground, far half (pass -1, before the pillar) or near half (pass +1, after it). */
    private static void wave(float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int pass){
        float level=AtomicPillarShape.ignitionLevel(sec);double front=AtomicPillarShape.ignitionFront(sec,R);
        if(level<=.005f||front<.5)return;
        double[] d=toCamera(cam,o);
        Vfx.material(Vfx.RING,sec,.22f,.8f,level,0,pass,(float)d[0],(float)d[1],(float)Math.min(1,r/front));
        Vfx.disc(rot,cam,o.x,o.y+.1,o.z,front);
    }
    /** The ground behind the ignition wave ripples (near half; the far half is behind the pillar). */
    private static void ripple(float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r){
        double front=AtomicPillarShape.ignitionFront(sec,R);if(front<.5)return;
        double[] d=toCamera(cam,o);
        Vfx.material(Vfx.RING_REFRACT,sec,.5f,0,AtomicPillarShape.ignitionLevel(sec),0,1,(float)d[0],(float)d[1],(float)Math.min(1,r/front));
        Vfx.disc(rot,cam,o.x,o.y+.12,o.z,front);
    }
    /** Heat shimmer over the floor between the pillar and the edge while it opens and closes. */
    private static void groundHaze(float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r){
        double[] d=toCamera(cam,o);
        float strength=AtomicLook.smooth(sec,AtomicLook.IMPACT,AtomicLook.PINK)*(float)Math.min(1,(R-r)/(R*.08));
        Vfx.material(Vfx.HAZE,sec,(float)(r/R),strength,0,0,1,(float)d[0],(float)d[1],0);
        Vfx.disc(rot,cam,o.x,o.y+.15,o.z,R);
    }
    /** The pillar: opaque body, the fastest streaks added over it, and its top seen from above. */
    private static void pillar(AtomicScene s,float sec,float flow,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,double y0,double y1,boolean inside){
        float white=AtomicPillarShape.whiteOut(sec),heat=AtomicPillarShape.heat(sec),a=AtomicPillarShape.opacity(sec);
        float offset=(s.seed%97)/97f,h=(float)(y1-y0),front=(float)AtomicStage.surgeFront(sec,R),glow=AtomicStage.surgeGlow(sec);
        Vfx.material(Vfx.BODY,flow,(float)r,h,(float)R,white,1,heat,a,offset);
        Vfx.surge(front,glow);
        Vfx.cylinder(rot,cam,o.x,y0,o.z,r,h,inside);
        Vfx.material(Vfx.SKIN,flow,(float)r,h,(float)R,white,1,heat,a,offset);
        Vfx.surge(front,glow);
        Vfx.cylinder(rot,cam,o.x,y0,o.z,r,h,inside);
        if(cam.y>y1){
            Vfx.material(Vfx.CAP,flow,(float)r,h,(float)R,white,1,heat,a,offset);
            Vfx.disc(rot,cam,o.x,y1,o.z,r);
        }
    }
    /**
     * Thousands of white energy streaks racing up the pillar's surface layer at 8 to 15 R/s (bolts the fastest),
     * flaring where a surge passes; the vertex shader moves them, nothing is rebuilt per frame. A share of them
     * at lower quality settings.
     */
    private static void streaks(float sec,float flow,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,double y0,double y1,boolean inside,int quality){
        float level=AtomicPillarShape.streaks(sec);if(level<=.01f)return;
        float share=quality>1?1:quality>0?.6f:.35f;
        Vfx.material(Vfx.STREAK,flow,(float)r,(float)(y1-y0),(float)R,level,1,share,inside?1:0,0);
        Vfx.surge((float)AtomicStage.surgeFront(sec,R),AtomicStage.surgeGlow(sec));
        Vfx.streaks(rot,cam,o.x,y0,o.z);
    }
    /** Shimmer where the pillar stood: the scene behind the space it left wavers and rises. */
    private static void heatColumn(AtomicScene s,float sec,float flow,Matrix4f rot,Vec3d cam,Vec3d o,double R,float level){
        double h=R*3;boolean inside=Math.hypot(cam.x-o.x,cam.z-o.z)<R&&cam.y>o.y-2&&cam.y<o.y+h;
        Vfx.material(Vfx.HEAT_COLUMN,flow,(float)R,(float)h,(float)R,0,1,0,level,(s.seed%97)/97f);
        Vfx.cylinder(rot,cam,o.x,o.y-2,o.z,R,h,inside);
    }
    /** Chunks of ground lifted up the pillar; only those near its surface show through the light. */
    private static void debris(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int quality){
        float t0=AtomicLook.PINK;
        if(sec<t0||sec>=t0+6.5f||r<2)return;
        record Piece(AtomicScene.Chunk d,Vec3d p,float t,float alpha){}
        List<Piece> pieces=new ArrayList<>();
        int n=quality>1?s.debris.length:s.debris.length/2;
        double deep=Math.max(4,R*.25);
        for(int k=0;k<n;k++){
            var d=s.debris[k];float t=sec-t0-d.delay()*3;if(t<0||t>=4.6f)continue;
            // Just under the surface (the whole chunk stays inside the radius), deeper ones fainter.
            double size=Math.max(d.sx(),Math.max(d.sy(),d.sz()))*R;
            double rr=r-size*(1.75+.9*d.r0()/1.1);if(rr<=0)continue;
            double y=o.y+d.rise()*R*.9*t-R*.04*t*t;
            Vec3d p=new Vec3d(o.x+Math.cos(d.angle())*rr,y,o.z+Math.sin(d.angle())*rr);
            // Solid dark silhouettes near the surface; deeper in the light they fade out instead of turning to grey ghosts.
            float a=.95f*(1-AtomicLook.smooth(t,3.4f,4.6f))*(1-AtomicLook.smooth((float)depthInside(cam,o,r,p),(float)deep*.6f,(float)deep))*AtomicWorld.near(cam,p,1.5,4);
            if(a>.3f)pieces.add(new Piece(d,p,t,a));
        }
        if(pieces.isEmpty())return;
        pieces.sort(Comparator.comparingDouble((Piece q)->-q.p().squaredDistanceTo(cam)));
        var b=Vfx.sprites(rot,cam,Vfx.SOLID,sec,0,0,0,0);
        int k=0;
        for(var q:pieces){
            var d=q.d();double ang=d.spin()*q.t();
            Vec3d u=AtomicWorld.turn(new Vec3d(1,0,0),d.axis(),ang).multiply(d.sx()*R),v=AtomicWorld.turn(new Vec3d(0,1,0),d.axis(),ang).multiply(d.sy()*R),
                n2=AtomicWorld.turn(new Vec3d(0,0,1),d.axis(),ang).multiply(d.sz()*R);
            Vec3d light=new Vec3d(o.x,q.p().y,o.z).subtract(q.p()).normalize();
            float u0=(k++)*3.7f;
            face(b,q.p(),u,v,n2,light,q.alpha(),u0);face(b,q.p(),u.multiply(-1),n2,v,light,q.alpha(),u0+.3f);
            face(b,q.p(),v,n2,u,light,q.alpha(),u0+.6f);face(b,q.p(),v.multiply(-1),u,n2,light,q.alpha(),u0+.9f);
            face(b,q.p(),n2,u,v,light,q.alpha(),u0+1.2f);face(b,q.p(),n2.multiply(-1),v,u,light,q.alpha(),u0+1.5f);
        }
        Vfx.draw(b,true);
    }
    /** One box face, counter-clockwise from outside: dark where it faces away from the light, pink where it faces it. */
    static void face(Vfx.Sprites b,Vec3d p,Vec3d n,Vec3d a,Vec3d c,Vec3d light,float alpha,float u0){
        double lit=Math.max(0,n.normalize().dotProduct(light));
        int rgb=AtomicLook.lerpColor(0x160c2a,0xd6b8ff,(float)(.06+.94*lit*lit));
        Vec3d m=p.add(n);
        b.quad(m.subtract(a).subtract(c),m.add(a).subtract(c),m.add(a).add(c),m.subtract(a).add(c),u0,rgb,alpha);
    }
    /** Sparks racing up just under the surface, seen where the light is thin (the violet edges, the smoke ring). */
    private static void sparks(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,int quality){
        if(sec<AtomicLook.PINK||sec>=AtomicLook.BEAM_END||r<2)return;
        int n=quality>1?96:quality>0?56:28;double deep=Math.max(2,R*.08);
        var b=Vfx.sprites(rot,cam,Vfx.SPARK,sec,0,0,0,0);
        for(int k=0;k<n;k++){
            double life=.55+.5*AtomicWorld.h(k*1.7+s.seed);
            double cyc=(sec-AtomicLook.PINK)/life+AtomicWorld.h(k*3.1);int c=(int)Math.floor(cyc);double u=cyc-c;
            double ang=AtomicWorld.h(c*5.3+k*2.9+s.seed)*Math.PI*2,rr=r*(.9+.08*AtomicWorld.h(c*7.1+k));
            double y=o.y+R*(.02+.25*AtomicWorld.h(c*2.3+k*1.3))+u*R*(1.4+1.6*AtomicWorld.h(k*4.4));
            Vec3d p=new Vec3d(o.x+Math.cos(ang)*rr,y,o.z+Math.sin(ang)*rr);
            float a=(float)Math.sin(Math.PI*u)*(1-(float)Math.min(1,depthInside(cam,o,r,p)/deep))*AtomicWorld.near(cam,p,1,3);
            if(a<=.02f)continue;
            b.streak(p.add(0,-R*(.1+.12*AtomicWorld.h(k*9.2)),0),p,R*.01+.1,k%3==0?0xffe8ff:0xd2b0ff,a);
        }
        Vfx.draw(b,false);
    }
    /**
     * Motes of light streaming up around the pillar, just outside its surface (out to 1.6 times its radius: light, not
     * the blast), twinkling. Those on the far half go before the pillar (it hides the ones behind it), the near half after.
     */
    private static void motes(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,double y1,int quality,int pass){
        float level=AtomicStage.standing(sec)*AtomicPillarShape.opacity(sec)*AtomicLook.smooth((float)(r/R),.25f,.6f);
        if(level<=.01f)return;
        int n=quality>1?160:quality>0?100:50;
        double[] d=toCamera(cam,o);
        var b=Vfx.sprites(rot,cam,Vfx.SPARK,sec,0,0,0,0);
        for(int k=0;k<n;k++){
            double life=1.6+1.4*AtomicWorld.h(k*2.3+s.seed);
            double cyc=sec/life+AtomicWorld.h(k*1.9);int c=(int)Math.floor(cyc);double u=cyc-c;
            double ang=AtomicWorld.h(c*4.1+k*3.7+s.seed)*Math.PI*2,rr=r*(1.04+.56*Math.pow(AtomicWorld.h(c*6.3+k),1.5));
            double px=Math.cos(ang)*rr,pz=Math.sin(ang)*rr;
            if((px*d[0]+pz*d[1]>0)!=(pass>0))continue;
            double y=o.y+R*(.05+.3*AtomicWorld.h(c*2.9+k*1.1))+u*(y1-o.y)*(.35+.4*AtomicWorld.h(k*5.1));
            Vec3d p=new Vec3d(o.x+px,Math.min(y,y1),o.z+pz);
            float tw=.55f+.45f*(float)Math.sin(sec*(6+5*AtomicWorld.h(k*7.7))+k);
            float a=level*(float)Math.sin(Math.PI*u)*tw*AtomicWorld.near(cam,p,1.5,4);
            if(a<=.02f)continue;
            b.billboard(p,R*.012+.12,k%4==0?0xffffff:0xe2d0ff,a);
        }
        Vfx.draw(b,false);
    }
    /** Dust rising out of the crater once the pillar is gone, inside the radius. */
    private static void dust(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R){
        float start=AtomicLook.BEAM_END-.3f;
        if(sec<start||sec>=AtomicWorld.BLAST_END)return;
        float t=sec-start,in=AtomicLook.smooth(sec,start,AtomicLook.BEAM_END+.6f),out=1-AtomicLook.smooth(sec,24.5f,AtomicWorld.BLAST_END);
        record Puff(Vec3d p,double size,float seed,float alpha){}
        List<Puff> puffs=new ArrayList<>();
        int k=0;
        for(var p:s.dust){
            double size=Math.min(R*.95,R*(.26+.24*p.size())*(1+.08*t));
            double rho=Math.max(0,Math.min(R*(.08+.35*p.size())+R*.02*t,R-size)),ang=p.angle()+.12*t;
            double y=o.y+R*(.1+p.size()*.4)+R*.2*t*(.6+.7*p.size());
            Vec3d q=new Vec3d(o.x+Math.cos(ang)*rho,y,o.z+Math.sin(ang)*rho);
            puffs.add(new Puff(q,size,(k++)/(float)s.dust.length,in*out*p.alpha()*AtomicWorld.near(cam,q,2,7)));
        }
        puffs.sort(Comparator.comparingDouble((Puff q)->-q.p().squaredDistanceTo(cam)));
        var b=Vfx.sprites(rot,cam,Vfx.SMOKE,sec,.42f,1,0,0);
        for(var q:puffs)b.billboard(q.p(),q.size(),((int)(q.seed()*255))<<16,q.alpha());
        Vfx.draw(b,false);
    }
    /** Embers drifting up out of the crater after the beam, inside the radius. */
    private static void embers(AtomicScene s,float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,int quality){
        float start=AtomicLook.BEAM_END-.2f;
        if(sec<start||sec>=AtomicWorld.BLAST_END)return;
        float t=sec-start,in=AtomicLook.smooth(sec,start,start+.8f),out=1-AtomicLook.smooth(sec,25.5f,AtomicWorld.BLAST_END);
        int n=quality>1?s.embers.length:quality>0?s.embers.length/2:s.embers.length/4;
        var b=Vfx.sprites(rot,cam,Vfx.SPARK,sec,0,0,0,0);
        for(int k=0;k<n;k++){
            var e=s.embers[k];float cyc=t/e.life()+e.phase();int c=(int)Math.floor(cyc);float u=cyc-c;
            double a=e.angle()+AtomicWorld.h(c*3.1+k)*1.2,rad=e.radius()*R*.7*(.6+.6*AtomicWorld.h(c*5.7+k));
            double x=Math.cos(a)*rad+e.vx()*R*u*e.life(),z=Math.sin(a)*rad+e.vz()*R*u*e.life(),hz=Math.hypot(x,z),size=e.size()*R;
            if(hz>R-size){double k2=Math.max(0,R-size)/hz;x*=k2;z*=k2;}
            Vec3d p=o.add(x,R*.05+e.vy()*R*u*e.life(),z);
            float tw=(float)Math.pow(Math.max(0,Math.sin(sec*(5+AtomicWorld.h(k*6.1)*7)+k)),2);
            b.billboard(p,size,0xe6c4ff,in*out*(float)Math.sqrt(Math.max(0,Math.sin(Math.PI*u)))*(.5f+.5f*tw)*AtomicWorld.near(cam,p,1,3));
        }
        Vfx.draw(b,false);
    }
    /**
     * Star flares: the ignition at the caster's feet, the top of the pillar shining like a star while it stands (bursting
     * as each surge arrives), the pulse when the pillar starts to close and the spark where it vanishes.
     */
    private static void flares(float sec,Matrix4f rot,Vec3d cam,Vec3d o,double R,double r,double y0,double y1){
        var b=Vfx.sprites(rot,cam,Vfx.FLARE,sec,0,0,0,0);
        float crown=AtomicStage.standing(sec)*AtomicPillarShape.opacity(sec)*AtomicLook.smooth((float)(r/R),.25f,.6f);
        if(crown>.01f){
            double front=AtomicStage.surgeFront(sec,R);
            float burst=front<0?0:(float)Math.exp(-Math.abs(front-(y1-y0))/(1.2*R))*AtomicStage.surgeGlow(sec)*2;
            Vec3d p=new Vec3d(o.x,y1,o.z);
            b.billboard(p,R*(1.15+.45*burst),0xffffff,crown*(.5f+.45f*Math.min(1,burst))*AtomicWorld.near(cam,p,2,6));
        }
        if(sec>=AtomicLook.IMPACT&&sec<6.5f){
            float grow=AtomicWorld.out(sec,AtomicLook.IMPACT,AtomicLook.PINK+.15f);
            Vec3d p=o.add(0,R*.3,0);
            b.billboard(p,R*(.3+.7*grow),0xffffff,(1-AtomicLook.smooth(sec,AtomicLook.PINK+.1f,6.5f))*AtomicWorld.near(cam,p,2,6));
        }
        if(sec>=AtomicLook.COLLAPSE_FLAT&&sec<21.8f){
            float u=AtomicLook.unit(sec,AtomicLook.COLLAPSE_FLAT,21.8f);Vec3d p=o.add(0,R*.3,0);
            b.billboard(p,R*(.5+.5*u),0xffffff,(1-u)*(1-u)*AtomicWorld.near(cam,p,2,6));
        }
        float end=AtomicLook.smooth(sec,AtomicLook.BEAM_END-.15f,AtomicLook.BEAM_END)*(1-AtomicLook.smooth(sec,AtomicLook.BEAM_END,AtomicLook.BEAM_END+.4f));
        if(end>0){Vec3d p=o.add(0,R*.15,0);b.billboard(p,R*.45,0xffffff,end*AtomicWorld.near(cam,p,2,6));}
        Vfx.draw(b,false);
    }
}
