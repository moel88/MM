package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.xchara.EraseGround;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * X Chara's delete slash as it goes, drawn (2.133.0; EraseClient's casts, EraseZone's passes; the server's XCharaErase,
 * EraseRules). Everything of how it looks is mine; everything is a function of the slash's own clock (ticks since it
 * went), of where it went from and of its seed, the same on every client.
 * <ul>
 * <li>The blade ({@link #wave}, the glitch pass, KagenoFire.glitchRibbon styles 2 to 4): a giant crescent standing across
 * its way, {@link #HALF_DRAW} blocks to either side, its crown {@link #CROWN} over the ground, its horns in the ground,
 * bowed forward ({@link #BOW}: the crown leads), leaning a little into its way; it rises out of the ground as it goes and
 * runs along it at the server's speed (EraseRules.front). Its cutting edge is white within an ink line, its body purple
 * going deep, its inner edge broken into flickering pixels; under it down to the ground a see-through veil with light
 * running down it; behind it fainter copies of itself; at its foot a bright cut along the ground. At the end of its way
 * it breaks into pixels and is gone.</li>
 * <li>The ground ({@link #ground}): every block it comes over flashes, its top breaks into square pixels of its own
 * colour (and some purple, white and black) thrown up and falling, leaves a dark void on its top edged in purple, and
 * a second on is drawn back ({@link #void_}); pieces of it are flung and the blocks it comes over jolt up ({@link
 * #solids}: drawn only, the map is not changed).</li>
 * <li>Whom it deletes ({@link #victims}): a burst on them as it strikes; while they are gone (EraseClient hides them) an
 * outline of where they stand, blinking, with pixels of them in it; their pixels gathering back; torn bars across them
 * as they are back.</li>
 * </ul>
 */
final class EraseFx {
    private EraseFx(){}
    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE;
    static final int NEON=0xc45cff;
    /** The void a block's top is left as: its purple edge and its dark. */
    static final int VOID_EDGE=0x34106a,VOID=0x0b0418;
    private static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}

    // ───────────────────────────── the blade ─────────────────────────────

    /**
     * The blade's shape: how far to either side its horns reach (blocks: a little past the server's 8, so the edge of what
     * it strikes is under it), how far its crown leads its horns, how high its crown is over the ground and how deep its
     * blade is there, how far it leans into its way at its crown, how far its horns go into the ground, and how many points
     * across it is drawn with.
     */
    static final double HALF_DRAW=8.4,BOW=2.4,CROWN=6.8,THICK=2.4,LEAN=.7,SINK=.25;
    static final int SAMPLES=41;
    /** How long it takes to rise out of the ground (ticks). */
    static final float RISE=2.5f;
    /** The copies of it behind it: how many, how far behind each is (ticks of its going), how strongly each shows. */
    static final int COPIES=3;static final float COPY_LAG=.9f;static final float[] COPY_ALPHA={.42f,.24f,.12f};
    /** At the end of its way it is eaten (from when to when, ticks after it got there) and gone; cut short, over this many ticks. */
    static final float END0=.5f,END1=7f,FADE0=3f,FADE1=8f,CUT_TICKS=4;
    /** How far behind its foot its cut along the ground glows (blocks). */
    static final double CUT_BACK=1.8;

    /** The tick after it went that slash {@code w} is as far as it goes (or was cut short). */
    static float endAt(EraseClient.Wave w){return (float)Math.min((w.range-EraseRules.NEAR)/EraseRules.SPEED,w.cut);}
    /** Whether {@code w} was cut short before it got to the end of its way. */
    static boolean cutShort(EraseClient.Wave w){return w.cut<(w.range-EraseRules.NEAR)/EraseRules.SPEED;}
    /** How far ahead of where its user stood its front is {@code age} ticks after it went (blocks; NaN before): the server's, held where it was cut short. */
    static double frontAt(EraseClient.Wave w,float age){
        if(age<0)return Double.NaN;
        return EraseRules.front(Math.min(age,w.cut),w.range);
    }
    /** How grown it is {@code age} ticks after it went (0 to 1): it rises out of the ground over RISE ticks. */
    static double grown(float age){return age<=0?0:Ease.out(age,0,RISE);}
    /** How much of it is eaten {@code age} ticks after it went (0 to 1): at the end of its way, or as it is cut short. */
    static float eaten(EraseClient.Wave w,float age){
        float end=endAt(w);
        return cutShort(w)?Ease.smooth(age,end,end+CUT_TICKS):Ease.smooth(age,end+END0,end+END1);
    }
    /** How strongly it shows {@code age} ticks after it went (0 to 1): gone a little after it is eaten. */
    static float shown(EraseClient.Wave w,float age){
        float end=endAt(w);
        return cutShort(w)?1-Ease.smooth(age,end,end+CUT_TICKS+1):1-Ease.smooth(age,end+FADE0,end+FADE1);
    }
    /** How high over the feet its cutting edge is {@code u} of the way from its middle to a horn (-1 to 1), grown {@code g}. */
    static double top(double u,double g){return -SINK+g*CROWN*Math.pow(Math.max(0,1-u*u),.55);}
    /** How deep its blade is there. */
    static double thick(double u,double g){return .02+g*THICK*Math.pow(Math.max(0,1-u*u),.8);}
    /** A point of it with its front {@code front} ahead: {@code u} across (-1 one horn, 1 the other), {@code y} over its user's feet. */
    static Vec3d point(EraseClient.Wave w,double front,double u,double y){
        double fw=front+BOW*(.5-u*u)+LEAN*Math.max(0,y)/CROWN;
        return w.from.add(w.f.multiply(fw)).add(w.right.multiply(HALF_DRAW*u)).add(0,y,0);
    }
    private static float[] along(){float[] a=new float[SAMPLES];for(int i=0;i<SAMPLES;i++)a[i]=i/(float)(SAMPLES-1);return a;}
    private static final float[] ALONG=along();
    private static double u(int i){return -1+2.0*i/(SAMPLES-1);}
    /** The blade with its front {@code front} ahead, grown {@code g}: style 2. */
    private static void blade(EraseClient.Wave w,double front,double g,int seed,float eat,float cool,float alpha){
        Vec3d[] in=new Vec3d[SAMPLES],out=new Vec3d[SAMPLES];
        for(int i=0;i<SAMPLES;i++){double u=u(i),t=top(u,g);in[i]=point(w,front,u,t-thick(u,g));out[i]=point(w,front,u,t);}
        KagenoFire.glitchRibbon(in,out,ALONG,SAMPLES,2,seed,eat,cool,alpha);
    }
    /** The veil under it, from under the ground up to the blade: style 3. */
    private static void veil(EraseClient.Wave w,double front,double g,int seed,float eat,float cool,float alpha){
        Vec3d[] in=new Vec3d[SAMPLES],out=new Vec3d[SAMPLES];
        for(int i=0;i<SAMPLES;i++){double u=u(i),t=top(u,g)-thick(u,g)+.05;in[i]=point(w,front,u,EraseRules.LOW);out[i]=point(w,front,u,Math.max(EraseRules.LOW+.05,t));}
        KagenoFire.glitchRibbon(in,out,ALONG,SAMPLES,3,seed,eat,cool,alpha);
    }
    /** The cut along the ground at its foot, glowing behind it: style 4. */
    private static void groundCut(EraseClient.Wave w,double front,double back,int seed,float eat,float cool,float alpha){
        Vec3d[] in=new Vec3d[SAMPLES],out=new Vec3d[SAMPLES];
        for(int i=0;i<SAMPLES;i++){double u=u(i)*.97;in[i]=point(w,front-back,u,.04);out[i]=point(w,front,u,.04);}
        KagenoFire.glitchRibbon(in,out,ALONG,SAMPLES,4,seed,eat,cool,alpha);
    }
    /** The slash (the glitch pass): its veil, its copies behind it, its blade, its cut along the ground. */
    static void wave(Fx c){
        var w=EraseClient.of(c.cast);if(w==null)return;
        float age=c.age,fade=shown(w,age);if(fade<=0||age<0)return;
        double g=grown(age),front=frontAt(w,age);if(Double.isNaN(front))return;
        float eat=eaten(w,age),cool=Ease.smooth(age,endAt(w),endAt(w)+6);
        int seed=w.seed&15;
        veil(w,front,g,seed,eat,cool,.9f*fade);
        for(int k=1;k<=COPIES;k++){
            float a=age-COPY_LAG*k;if(a<0)continue;
            double fk=frontAt(w,a);if(Double.isNaN(fk)||front-fk<.2)continue;
            blade(w,fk,grown(a),(seed+k*5)&15,Math.min(1,eat+.25f+.2f*k),Math.max(cool,.4f),COPY_ALPHA[k-1]*fade);
        }
        blade(w,front,g,seed,eat,cool,fade);
        groundCut(w,front,CUT_BACK*g,seed,eat,cool,.95f*fade);
    }

    // ───────────────────────────── the ground ─────────────────────────────

    /** The ground it comes over: every block column's top within its way, as this client found it once. */
    static final class Field {
        final int n;final int[] bx,bz;final double[] top;final float[] pass;final int[] rgb;final BlockState[] state;
        Field(int n){this.n=n;bx=new int[n];bz=new int[n];top=new double[n];pass=new float[n];rgb=new int[n];state=new BlockState[n];}
        int size(){return n;}
    }
    /** When (ticks after it went) its front comes over a point {@code fw} ahead and {@code side} to the right, for a slash going {@code range} (Float.MAX_VALUE: never). */
    static float pass(double fw,double side,double range){
        double u=MathHelper.clamp(side/HALF_DRAW,-1,1),front=fw-BOW*(.5-u*u);
        if(front>range+.3)return Float.MAX_VALUE;
        return (float)Math.max(0,(front-EraseRules.NEAR)/EraseRules.SPEED);
    }
    /** The ground of slash {@code w}, found from {@code world} as it is: every block column within its way whose top is in its reach (as EraseGround finds it), with when its front comes over it. */
    static Field field(EraseClient.Wave w,World world){
        if(world==null)return new Field(0);
        double half=EraseRules.HALF+.5,far=w.range+BOW+.5;Vec3d o=w.from,f=w.f,r=w.right;
        double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,minZ=Double.MAX_VALUE,maxZ=-Double.MAX_VALUE;
        for(double a:new double[]{-.5,far})for(double s:new double[]{-half,half}){
            Vec3d p=o.add(f.multiply(a)).add(r.multiply(s));
            minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);
        }
        var cols=new ArrayList<double[]>();var states=new ArrayList<BlockState>();
        var pos=new BlockPos.Mutable();
        for(int x=MathHelper.floor(minX);x<=MathHelper.floor(maxX);x++)for(int z=MathHelper.floor(minZ);z<=MathHelper.floor(maxZ);z++){
            double dx=x+.5-o.x,dz=z+.5-o.z,fw=dx*f.x+dz*f.z,side=dx*r.x+dz*r.z;
            if(fw<-.5||Math.abs(side)>half)continue;
            float t=pass(fw,side,w.range);if(t==Float.MAX_VALUE)continue;
            double top=EraseGround.at(world,x+.5,z+.5,o.y+EraseRules.HIGH,o.y+EraseRules.LOW-1);
            if(Double.isNaN(top))continue;
            pos.set(x,MathHelper.floor(top-.5),z);
            BlockState st=world.getBlockState(pos);
            int rgb=st.getMapColor(world,pos).color;if(rgb==0)rgb=0x8a8a8a;
            cols.add(new double[]{x,z,top,t,rgb});states.add(st);
        }
        var fl=new Field(cols.size());
        for(int i=0;i<fl.n;i++){var q=cols.get(i);fl.bx[i]=(int)q[0];fl.bz[i]=(int)q[1];fl.top[i]=q[2];fl.pass[i]=(float)q[3];fl.rgb[i]=(int)q[4];fl.state[i]=states.get(i);}
        return fl;
    }
    private static EraseClient.Wave of(Fx c,boolean find){
        var w=EraseClient.of(c.cast);if(w==null)return null;
        if(w.field==null&&find)w.field=field(w,c.mc.world);
        return w;
    }
    /** How long a block it comes over shows what it did (seconds after its front came over it): its pixels are gone and its void drawn back by then. */
    static final float GROUND_LIFE=1.6f;
    /** How many pixels a block's top breaks into. */
    static final int PIX=5;
    /** A column's own number from its place (the same on every client). */
    static int cell(int x,int z){return x*73856093^z*19349663;}
    /**
     * Pixel {@code j} of a block's top {@code tau} seconds after its front came over it, thrown up and along its way
     * ({@code fx}, {@code fz}): {east, up, south of the top's middle, its half size, how strongly it shows}, or null while
     * it does not show. It keeps its size, rests on the ground if it falls back to it, and blinks out at its end.
     */
    static double[] pixel(int j,int seed,float tau,double fx,double fz){
        float born=.012f*j+.02f*(float)h(j,seed,1),age=tau-born,life=.45f+.4f*(float)h(j,seed,2);
        if(age<0||age>=life)return null;
        double ox=(h(j,seed,3)-.5)*.8,oz=(h(j,seed,4)-.5)*.8;
        double push=1.5+2.5*h(j,seed,10);
        double vy=3+5*h(j,seed,5),vx=(h(j,seed,6)-.5)*3+fx*push,vz=(h(j,seed,7)-.5)*3+fz*push;
        double drag=2.2,k=(1-Math.exp(-drag*age))/drag;
        double y=Math.max(.03,.08+vy*k-7*age*age);
        float u=age/life,alpha=u<.75f?1:((int)Math.floor(age*60+j)%2==0?1:0);
        return new double[]{ox+vx*k,y,oz+vz*k,.06+.07*h(j,seed,8),alpha};
    }
    /** A pixel's colour: mostly its block's own (a little lighter or darker), some purple, lilac, white and black. */
    static int pixelColour(int j,int seed,int block){
        double c=h(j,seed,9);
        if(c<.68){double k=.72+.42*h(j,seed,11);int r=(int)Math.min(255,(block>>16&255)*k),g=(int)Math.min(255,(block>>8&255)*k),b=(int)Math.min(255,(block&255)*k);return r<<16|g<<8|b;}
        return c<.8?PURPLE:c<.88?LILAC:c<.94?WHITE:INK;
    }
    /**
     * How much of a block's void is left {@code tau} seconds after its front came over it (0 to 1): coming in as its
     * pixels fly, held, then drawn back in from its edges to its middle (from {@code r0}, a quarter of a second).
     */
    static float void_(float tau,float r0){
        if(tau<0)return 0;
        return Ease.smooth(tau,.05f,.12f)*(1-Ease.smooth(tau,r0,r0+.25f));
    }
    /** When a block's void begins to be drawn back (seconds after its front came over it). */
    static float restore(int cell){return .95f+.3f*(float)GlitchFx.h(cell,0,7,2);}
    private static int pixelsDrawn,pixelsLast;
    /** For the capture tools: how many of its pixels were drawn the last frame. */
    static int pixelsLast(){return pixelsLast;}
    /** A flat square on a block's top: {@code half} from its middle to a side. */
    private static void flat(EffectMesh m,double x,double y,double z,double half,int rgb,float alpha){
        if(alpha<=0||half<=0)return;
        m.quad(new Vec3d(x-half,y,z-half),new Vec3d(x+half,y,z-half),new Vec3d(x+half,y,z+half),new Vec3d(x-half,y,z+half),rgb,alpha);
    }
    /** The ground it came over (one pass): the voids and the pixels, or the flash and the glow. */
    static void ground(Fx c,EraseClient.Wave w,EffectMesh m,boolean light){
        var fl=w.field;if(fl==null||fl.n==0)return;
        float age=c.age;Vec3d cam=c.camera;
        for(int i=0;i<fl.n;i++){
            float tau=(age-fl.pass[i])/20f;if(tau<0||tau>=GROUND_LIFE)continue;
            double x=fl.bx[i]+.5,z=fl.bz[i]+.5,y=fl.top[i];
            double dist2=(x-cam.x)*(x-cam.x)+(y-cam.y)*(y-cam.y)+(z-cam.z)*(z-cam.z);if(dist2>90*90)continue;
            int cl=cell(fl.bx[i],fl.bz[i]);
            float v=void_(tau,restore(cl));
            if(light){
                // the flash as it comes over it, a faint glow of the void, and a lilac one as it is drawn back
                if(tau<.08f)flat(m,x,y+.02,z,.5,WHITE,.5f*(1-tau/.08f));
                if(v>0)flat(m,x,y+.02,z,.5,NEON,.05f*v);
                float r0=restore(cl),back=Ease.smooth(tau,r0,r0+.08f)*(1-Ease.smooth(tau,r0+.12f,r0+.3f));
                if(back>0)flat(m,x,y+.022,z,.5*(1-.6*Ease.smooth(tau,r0,r0+.25f)),LILAC,.35f*back);
            }else if(v>0){
                // the void: drawn in from its edges to its middle as it comes back (its purple edge makes a grid of them)
                float shrink=1-Ease.smooth(tau,restore(cl),restore(cl)+.25f);
                flat(m,x,y+.013,z,.5*shrink,VOID_EDGE,.66f*v);
                flat(m,x,y+.016,z,.42*shrink,VOID,.58f*v);
            }
            if(tau>=.9f||dist2>64*64)continue;
            boolean outline=dist2<18*18;
            for(int j=0;j<PIX;j++){
                var p=pixel(j,cl,tau,w.f.x,w.f.z);if(p==null||p[4]<=0)continue;
                Vec3d at=new Vec3d(x+p[0],y+p[1],z+p[2]);float near=GlitchFx.near(at,cam);if(near<=0)continue;
                var face=GlitchFx.facing(cam,at);int rgb=pixelColour(j,cl,fl.rgb[i]);
                if(light){if(rgb==PURPLE||rgb==LILAC||rgb==WHITE)GlitchFx.square(m,at,face,p[3]*1.9,rgb==WHITE?GLOW:rgb,.25f*near);}
                else{
                    if(outline)GlitchFx.square(m,at,face,p[3]+.014,INK,.85f*near);
                    GlitchFx.square(m,at,face,p[3],rgb,near);
                    pixelsDrawn++;
                }
            }
        }
    }
    /** How many columns in five fling a piece of their block. */
    static boolean flings(int cell){return GlitchFx.h(cell,0,5,7)<.22;}
    /** One piece of block flung from a column {@code tau} seconds after its front came over it: {where, its size, how far it has spun (degrees)}, or null once it is gone. */
    static double[] chunk(Vec3d top,int cell,float tau,Vec3d f){
        if(tau<0||tau>=1.7f)return null;
        double size=(.22+.2*GlitchFx.h(cell,0,5,8))*(1-Ease.smooth(tau,1.3f,1.7f));
        double side=(GlitchFx.h(cell,0,5,9)-.5)*4;
        Vec3d v=f.multiply(2+2.5*GlitchFx.h(cell,0,5,10)).add(new Vec3d(-f.z,0,f.x).multiply(side)).add(0,5+4*GlitchFx.h(cell,0,5,11),0);
        Vec3d at=FxSolids.flight(top.add(0,size*.5,0),v,top.y+size*.5,tau);
        return new double[]{at.x,at.y,at.z,size,(260+420*GlitchFx.h(cell,0,5,12))*Math.min(tau,1.2)};
    }
    /** The ground itself as it comes over it: each block jolts up as it passes, and pieces of some are flung (drawn only: no block is changed). */
    static void solids(Fx c,FxSolids so){
        var w=of(c,true);if(w==null||w.field==null)return;var fl=w.field;
        float age=c.age;
        for(int i=0;i<fl.n;i++){
            float tau=(age-fl.pass[i])/20f;if(tau<0||tau>=1.7f)continue;
            int cl=cell(fl.bx[i],fl.bz[i]);
            var pos=new BlockPos(fl.bx[i],MathHelper.floor(fl.top[i]-.5),fl.bz[i]);
            if(tau<.16f){
                var st=so.surface(pos);
                if(st!=null)so.lifted(pos,st,.3*Math.sin(Math.PI*tau/.16f)*(.7+.3*GlitchFx.h(cl,0,5,13)),0,w.f);
            }
            if(flings(cl)){
                var q=chunk(new Vec3d(fl.bx[i]+.5,fl.top[i],fl.bz[i]+.5),cl,tau,w.f);
                if(q!=null&&q[3]>.02)so.chunk(fl.state[i],new Vec3d(q[0],q[1],q[2]),q[3],(float)q[4],(float)(q[4]*.7),(float)(q[4]*.4));
            }
        }
    }

    // ───────────────────────────── whom it deleted ─────────────────────────────

    /** The outline of where one deleted stands blinks this often (a second), with this many pixels of them in it. */
    static final float BLINK=16;static final int MOTES=7,GATHER=16;
    /** One deleted, {@code s} seconds after: a burst as it strikes, the outline while they are gone, their pixels gathering back, torn bars as they are back. */
    static void victims(Fx c,EraseClient.Wave w,EffectMesh m,boolean light){
        if(w.deleted.isEmpty()||c.mc.world==null)return;
        Vec3d cam=c.camera;float vanish=EraseClient.VANISH;
        for(var g:w.deleted.values()){
            float s=g.since(c.delta);if(s<0||s>=vanish+.3f)continue;
            if(!(c.mc.world.getEntityById(g.entity()) instanceof LivingEntity e))continue;
            boolean own=e==c.mc.getCameraEntity()&&!c.mc.gameRenderer.getCamera().isThirdPerson();
            Vec3d feet=e.getLerpedPos(c.delta),mid=feet.add(0,e.getHeight()*.55,0);
            int seed=g.entity()*7919+w.seed;
            float near=GlitchFx.near(mid,cam);
            var face=GlitchFx.facing(cam,mid);
            // the burst as it strikes
            if(s<.35f&&!own){
                Vec3d at=mid.add(face[2].multiply(e.getWidth()*.5+.2));
                if(light){
                    if(s<.18f)m.star(at,1.6,WHITE,.9f*Ease.smooth(s,0,.02f)*(1-s/.18f)*near);
                    m.softDisc(at,2.2,NEON,.3f*(1-Ease.smooth(s,.05f,.35f))*near);
                }else for(int j=0;j<10;j++){
                    double an=Math.PI*2*(j+.6*h(j,seed,21))/10;Vec3d dir=face[0].multiply(Math.cos(an)).add(face[1].multiply(Math.sin(an)));
                    float life=.3f*(.6f+.4f*(float)h(j,seed,22));if(s>=life)continue;
                    double len=(.8+1.4*h(j,seed,23))*Ease.out(s,0,.06f);float fade=Ease.smooth(s,0,.02f)*(1-Ease.smooth(s,life*.35f,life))*near;
                    Vec3d a0=at.add(dir.multiply(.25)),a1=at.add(dir.multiply(.25+len));
                    m.needle(a0,a1,.16,.22,INK,.85f*fade);m.needle(a0,a1,.1,.22,j%2==0?PURPLE:DEEP,.95f*fade);
                }
                GlitchFx.pixels(m,mid,w.f,.85,6,.07,24,seed,s,light,cam);
            }
            // gone: the outline of where they stand, blinking, with pixels of them in it
            if(s>=.06f&&s<vanish&&!own&&near>0){
                double hw=e.getWidth()*.5+.12,hh=e.getHeight()*.5+.08;Vec3d box=feet.add(0,e.getHeight()*.5,0);
                boolean on=((int)Math.floor(s*BLINK))%2==0;
                if(on)outline(m,box,face,hw,hh,light?GLOW:PURPLE,(light?.35f:.75f)*near,seed,s);
                int step=(int)Math.floor(s*12);
                for(int j=0;j<MOTES;j++){
                    if(h(j+step*17,seed,31)>.6)continue;
                    Vec3d p=box.add(face[0].multiply((h(j+step*7,seed,32)-.5)*2*hw)).add(face[1].multiply((h(j+step*11,seed,33)-.5)*2*hh));
                    double half=.05+.05*h(j,seed,34);int rgb=j%3==0?LILAC:PURPLE;
                    if(light)GlitchFx.square(m,p,face,half*1.8,GLOW,.25f*near);
                    else GlitchFx.square(m,p,face,half,rgb,.9f*near);
                }
            }
            // their pixels gathering back, the last of it
            float back0=vanish-.12f;
            if(s>=back0&&s<vanish&&!own&&near>0){
                float k=(s-back0)/.12f;
                for(int j=0;j<GATHER;j++){
                    double an=Math.PI*2*(j+h(j,seed,41))/GATHER;
                    Vec3d dir=face[0].multiply(Math.cos(an)).add(face[1].multiply(Math.sin(an)*1.4));
                    Vec3d p=mid.add(dir.multiply((1.5+.4*h(j,seed,42))*(1-k*k)));
                    double half=.06+.05*h(j,seed,43);int rgb=j%4==0?WHITE:j%2==0?LILAC:PURPLE;
                    if(light)GlitchFx.square(m,p,face,half*1.9,rgb==WHITE?GLOW:rgb,.3f*near);
                    else GlitchFx.square(m,p,face,half,rgb,near);
                }
            }
            // back: a lilac flash, torn bars across them
            if(s>=vanish&&!own){
                float b=s-vanish;
                if(light&&b<.14f)m.softDisc(mid,1.3+1.2*b/.14f,LILAC,.5f*(1-b/.14f)*near);
                float bs=b*2+1/30f;Vec3d front=face[2].multiply(e.getWidth()*.5+.14);
                for(int j=0;j<PullClient.DASHES;j++){
                    double[] q=PullClient.dash(j,bs,seed,e.getHeight(),e.getWidth());if(q==null)continue;
                    Vec3d p=feet.add(0,q[0]*e.getHeight(),0).add(face[0].multiply(q[1])).add(front);
                    if(light)GlitchFx.bar(m,p,face,q[2]*1.2,q[3]*2.6,GLOW,.22f*near);
                    else GlitchFx.bar(m,p,face,q[2],q[3],(int)q[4],near);
                }
            }
        }
    }
    /** A dashed outline of a box facing the camera: {@code hw} wide and {@code hh} high from its middle, its dashes crawling round it. */
    private static void outline(EffectMesh m,Vec3d mid,Vec3d[] face,double hw,double hh,int rgb,float alpha,int seed,float s){
        Vec3d r=face[0],up=face[1];
        Vec3d[] c={mid.subtract(r.multiply(hw)).subtract(up.multiply(hh)),mid.add(r.multiply(hw)).subtract(up.multiply(hh)),mid.add(r.multiply(hw)).add(up.multiply(hh)),mid.subtract(r.multiply(hw)).add(up.multiply(hh))};
        double wide=.035,dash=.16,crawl=s*1.2;
        for(int e=0;e<4;e++){
            Vec3d a=c[e],b=c[(e+1)%4],ab=b.subtract(a);double len=ab.length();if(len<1e-6)continue;Vec3d dir=ab.multiply(1/len);
            Vec3d out=dir.crossProduct(face[2]).normalize().multiply(wide);
            for(double t=-(crawl%(dash*2));t<len;t+=dash*2){
                double t0=Math.max(0,t),t1=Math.min(len,t+dash);if(t1<=t0)continue;
                Vec3d p0=a.add(dir.multiply(t0)),p1=a.add(dir.multiply(t1));
                m.quad(p0.subtract(out),p1.subtract(out),p1.add(out),p0.add(out),rgb,alpha);
            }
        }
    }

    // ───────────────────────────── round the blade ─────────────────────────────

    /** The light along it, streaks behind it, sparks where its horns plough the ground, and at the end of its way a shower of its pixels (and a flash on the wall that stopped it). */
    static void round(Fx c,EraseClient.Wave w,EffectMesh m,boolean light){
        float age=c.age,fade=shown(w,age);if(fade<=0||age<0)return;
        double g=grown(age),front=frontAt(w,age);if(Double.isNaN(front))return;
        float end=endAt(w);boolean going=age<end;
        Vec3d cam=c.camera;
        if(light){
            // a glow along its cutting edge
            for(int i=0;i<7;i++){double u=-.84+.28*i;m.softDisc(point(w,front,u,top(u,g)-.3),2.0+1.4*(1-u*u),NEON,.14f*fade*(float)g);}
            // streaks behind it, along the ground and up its face, as it goes
            if(going)for(int i=0;i<12;i++){
                double u=-.92+1.84*(i+.5*h(i,w.seed,51))/12,y=.3+top(u,g)*.85*h(i,w.seed,52);
                Vec3d a=point(w,front-.2,u,y),b=point(w,front-3.5-2.5*h(i,w.seed,53),u,y);
                m.softTaper(b,a,.02,.12,i%3==0?WHITE:LILAC,.35f*fade);
            }
        }
        // sparks where its horns go through the ground: a little burst every tick of its going
        int now=(int)Math.floor(age);
        for(int k=0;k<5;k++){
            int t=now-k;if(t<0||t>end)continue;
            double ft=frontAt(w,t);if(Double.isNaN(ft))continue;
            float s=(age-t)/20f;
            for(int side=-1;side<=1;side+=2){
                Vec3d base=point(w,ft,side*.94,.1);
                GlitchFx.pixels(m,base,new Vec3d(0,1,0).add(w.f.multiply(-.5)).normalize(),.6,6,.07,5,w.seed+t*37+side*11,s,light,cam);
            }
        }
        // the end of its way: a shower of its pixels off its cutting edge; and a flash on what stopped it
        if(!cutShort(w)){
            float s=(age-end)/20f;
            if(s>=0&&s<.6f){
                for(int i=0;i<9;i++){
                    double u=-.85+.2125*i;Vec3d at=point(w,front,u,top(u,g)*.7);
                    GlitchFx.pixels(m,at,w.f,.9,5,.08,10,w.seed+i*101,s,light,cam);
                }
                if(w.range<EraseRules.RANGE-.5){
                    Vec3d at=point(w,front,0,2.2);float near=GlitchFx.near(at,cam);
                    if(light){
                        if(s<.18f)m.star(at,3.2,WHITE,.85f*Ease.smooth(s,0,.02f)*(1-s/.18f)*near);
                        m.softDisc(at,4.5,NEON,.25f*(1-Ease.smooth(s,.05f,.5f))*near);
                    }
                    FxLib.ringPlane(m,at,w.right,new Vec3d(0,1,0),6*Ease.out(s,0,.4f),light?.8:.3,light?NEON:PURPLE,(light?.4f:.8f)*(1-s/.6f));
                }
            }
        }
    }

    // ───────────────────────────── the passes ─────────────────────────────

    /** Everything but the blade itself, in one pass: the ground, whom it deleted, what is round the blade. */
    static void zone(Fx c,EffectMesh m,boolean light){
        var w=of(c,true);if(w==null)return;
        if(!light){pixelsLast=pixelsDrawn;pixelsDrawn=0;}
        ground(c,w,m,light);
        round(c,w,m,light);
        victims(c,w,m,light);
    }
    /** Its light on the world; little afterglow as it goes (it would leave copies of itself behind it). */
    static void frame(Fx c,FxFrame fr){
        var w=EraseClient.of(c.cast);if(w==null)return;
        float age=c.age,fade=shown(w,age);
        double g=grown(age),front=frontAt(w,age);
        if(fade>0&&!Double.isNaN(front)){
            if(age<endAt(w)+2)fr.afterglow(.05f);
            fr.light(point(w,front,0,top(0,g)*.6),0xa040ff,16,1.5f*fade*(float)g);
            fr.light(point(w,front,0,.5),0x8a2be2,10,.7f*fade);
        }
        for(var gn:w.deleted.values()){
            float s=gn.since(c.delta);if(s<0||s>=EraseClient.VANISH+.3f)continue;
            if(!(c.mc.world.getEntityById(gn.entity()) instanceof LivingEntity e))continue;
            Vec3d mid=e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);
            if(s<.4f)fr.light(mid,0xb04cff,5,1.3f*(1-s/.4f));
            float b=s-EraseClient.VANISH;if(b>=0&&b<.3f)fr.light(mid,0xc890ff,4,.8f*(1-b/.3f));
        }
    }
    /** How hard it shakes the camera (KagenoCamera weighs it by how near its front is): a rumble as it goes, a knock as a wall stops it, and hard on one it deletes. */
    static float shake(EraseClient.Wave w,float sec){
        float age=sec*20,end=endAt(w),v=0;
        if(age<end+1)v+=.55f*(float)grown(age);
        if(!cutShort(w)&&w.range<EraseRules.RANGE-.5)v+=1.2f*dev.sangwoo.celestialfall.client.xchara.KnifeCombo.bump(sec,end/20f,.14f);
        var mc=net.minecraft.client.MinecraftClient.getInstance();
        if(mc.player!=null){var g=w.deleted.get(mc.player.getId());if(g!=null)v+=1.4f*dev.sangwoo.celestialfall.client.xchara.KnifeCombo.bump(sec,g.at()/20f,.14f);}
        return v;
    }
    /** For the tests: a column's pixels at {@code tau} seconds as {east, up, south, half, shown} each (null where one does not show). */
    static List<double[]> pixels(int cell,float tau,double fx,double fz){
        var out=new ArrayList<double[]>();for(int j=0;j<PIX;j++)out.add(pixel(j,cell,tau,fx,fz));return out;
    }
}
