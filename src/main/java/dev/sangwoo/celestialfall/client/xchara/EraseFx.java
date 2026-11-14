package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import java.util.ArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * X Chara's delete slash once it is thrown, drawn (2.133.0; EraseClient's casts, EraseZone's passes; the server's
 * XCharaErase, EraseRules). Everything of how it looks is mine but what the user asked for (an upright slash thrown
 * once that stands in the ground, the ten by ten it hits deleted as one piece, a ten-block cube of it rising and bursting
 * in a glitch, the ground back ten seconds on); everything is a function of the slash's own clock (ticks since it was
 * thrown), of where it went and of its seed, the same on every client.
 * <ul>
 * <li>The slash ({@link #slash}, the glitch pass, KagenoFire.glitchRibbon style 2): a crescent in the plane the knife
 * came down in (their choice "내려찍는 면 그대로": seen from the side a crescent, from behind a thin upright blade; turned
 * {@link #ROLL} about its way so a little of its face shows behind it), its point leading, flying from the knife to the
 * ground with copies of itself behind; it drives into the ground ({@link #PLUNGE}) and stands there, its white edge
 * cooling, until it is eaten into pixels.</li>
 * <li>Where it lands ({@link #impact}): a flash, a ring along the ground, the ten by ten marked bright ({@link #square}),
 * pieces of the ground flung, pixels thrown up.</li>
 * <li>The cube ({@link #cube}, the blocks of that ground drawn as they are, rising with EraseRules.lift; where there is no
 * block, a dark void): glitching as one piece ({@link #tension}: a little as it rises, more and more toward its burst):
 * slices of it slipping aside ({@link #slice}), the whole of it shaking, for a frame now and then its blocks gone to a
 * dark void ({@link #flick}), its edges glowing purple and splitting red and cyan, bands of light running up its sides,
 * pixels of data flickering on it and, at the last, peeling off it.</li>
 * <li>Its burst ({@link #burst}): its blocks flung out in pieces ({@link #fragments}), pixels everywhere, purple spikes,
 * a white flash, rings in the air and along the ground.</li>
 * <li>Where it was ({@link #voidPlane}): a dark void on the ground edged in purple with a grid and flickering static,
 * from as it clears the ground until EraseRules.BACK, when a line of light sweeps over it and the ground is back.</li>
 * <li>Whom it deletes ({@link #victims}): a burst on them; while they are gone an outline of where they stand, blinking;
 * their pixels gathering back; torn bars across them as they are back.</li>
 * </ul>
 */
final class EraseFx {
    private EraseFx(){}
    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE,RED=GlitchFx.RED,CYAN=GlitchFx.CYAN,PINK=GlitchFx.PINK;
    static final int NEON=0xc45cff;
    /** The void where there is no ground: its purple edge and its dark. */
    static final int VOID_EDGE=0x34106a,VOID=0x0b0418;
    private static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}
    /** Its moments (seconds after it struck): the cube rises, it bursts, the ground is back. */
    static final float RISE0=EraseRules.RISE_FROM/20f,BURST_S=EraseRules.BURST/20f,BACK_S=EraseRules.BACK/20f;
    /** How long it is drawn after the ground is back, and after it was cut short (seconds). */
    static final float AFTER=.6f,CUT_LIFE=.6f;
    /** Cut short: how fast what is left of it goes (ticks). */
    static final float CUT_FADE=5;

    // ───────────────────────────── the slash ─────────────────────────────

    /**
     * The crescent: from its middle to a horn (blocks), how far its point leads its horns, how deep it is at its middle,
     * how far it is turned about its way (degrees), how far it drives into the ground and over how many ticks, how fast it
     * grows as it leaves the knife (ticks), how many points along it.
     */
    static final double HALF=4.8,BOW=2.4,THICK=1.6,ROLL=14,PLUNGE=1.6;
    static final float PLUNGE_T=1.5f,GROW_T=2f;
    static final int N=33;
    private static float[] along(){float[] a=new float[N];for(int i=0;i<N;i++)a[i]=i/(float)(N-1);return a;}
    private static final float[] ALONG=along();
    /** Its copies behind it as it flies: how many, how far behind each (ticks), how strongly each shows. */
    static final int COPIES=3;static final float COPY_LAG=.8f;static final float[] COPY_ALPHA={.42f,.24f,.12f};
    /** Standing in the ground: its white cooling over this long, eaten into pixels from when to when (seconds after it struck). */
    static final float COOL1=.25f,EAT0=.25f,EAT1=.6f;
    /**
     * Its frame from the knife {@code from} to the ground {@code to} (facing {@code yaw}, for a straight drop): its way
     * (unit), the upright of its plane (square to its way, upward, turned ROLL about the way) and the plane's side.
     */
    static Vec3d[] frame(Vec3d from,Vec3d to,float yaw){
        Vec3d d=to.subtract(from);Vec3d way=d.lengthSquared()<1e-6?new Vec3d(0,-1,0):d.normalize();
        Vec3d up=new Vec3d(0,1,0).subtract(way.multiply(way.y));
        if(up.lengthSquared()<1e-6)up=Vec3d.fromPolar(0,yaw);
        up=up.normalize();Vec3d side=way.crossProduct(up).normalize();
        double r=Math.toRadians(ROLL);
        Vec3d up2=up.multiply(Math.cos(r)).add(side.multiply(Math.sin(r))).normalize();
        return new Vec3d[]{way,up2,way.crossProduct(up2).normalize()};
    }
    /** How much of its way it has flown {@code age} ticks after it was thrown (0 to 1): a little faster as it goes. */
    static double flown(float age,int fly){
        if(age<=0)return 0;if(age>=fly)return 1;
        double u=age/fly;return .65*u+.35*u*u;
    }
    /** Where its point is {@code age} ticks after it was thrown: along its way from the knife, then driven into the ground. */
    static Vec3d tip(EraseClient.Strike s,float age){
        if(age<s.fly)return s.from.add(s.to.subtract(s.from).multiply(flown(age,s.fly)));
        return s.to.add(s.way.multiply(PLUNGE*Ease.out(age-s.fly,0,PLUNGE_T)));
    }
    /** How grown it is {@code age} ticks after it was thrown (it leaves the knife small). */
    static double grown(float age){return .3+.7*Ease.out(age,0,GROW_T);}
    /** Its edges with its point at {@code tip}, grown {@code g}: {inner, outer} (the outer its cutting edge, leading). */
    static Vec3d[][] crescent(EraseClient.Strike s,Vec3d tip,double g){
        Vec3d c=tip.subtract(s.way.multiply(BOW*g));Vec3d[] in=new Vec3d[N],out=new Vec3d[N];
        for(int i=0;i<N;i++){
            double u=-1+2.0*i/(N-1),w=1-u*u;
            out[i]=c.add(s.upright.multiply(HALF*g*u)).add(s.way.multiply(BOW*g*w));
            in[i]=c.add(s.upright.multiply(HALF*g*u*.97)).add(s.way.multiply(BOW*g*w-THICK*g*Math.pow(w,.7)));
        }
        return new Vec3d[][]{in,out};
    }
    /** How strongly what is left of it shows {@code age} ticks in (cut short: gone over CUT_FADE ticks). */
    static float kept(EraseClient.Strike s,float age){return s.cut==Float.MAX_VALUE?1:1-Ease.smooth(age,s.cut,s.cut+CUT_FADE);}
    /** The slash (the glitch pass): its copies behind it as it flies, and itself, flying and then standing in the ground until it is eaten. */
    static void slash(Fx c){
        var s=EraseClient.of(c.cast);if(s==null)return;
        float age=c.age,since=(age-s.fly)/20f,fade=kept(s,age);
        if(age<0||since>=EAT1+.02f||fade<=0)return;
        int seed=s.seed&15;
        float eat=Ease.smooth(since,EAT0,EAT1),cool=.6f*Ease.smooth(since,0,COOL1);
        if(age<s.fly+1)for(int k=1;k<=COPIES;k++){
            float a=age-COPY_LAG*k;if(a<0)continue;
            var cr=crescent(s,tip(s,Math.min(a,s.fly)),grown(a));
            KagenoFire.glitchRibbon(cr[0],cr[1],ALONG,N,2,(seed+k*5)&15,Math.min(1,.3f+.2f*k),.4f,COPY_ALPHA[k-1]*fade);
        }
        var cr=crescent(s,tip(s,age),grown(age));
        KagenoFire.glitchRibbon(cr[0],cr[1],ALONG,N,2,seed,eat,cool,fade);
    }

    // ───────────────────────────── lines ─────────────────────────────

    /** A line flat on the ground from {@code a} to {@code b}, {@code width} across. */
    static void flatLine(EffectMesh m,Vec3d a,Vec3d b,double width,int rgb,float alpha){
        if(alpha<=0)return;Vec3d d=b.subtract(a);double l=Math.hypot(d.x,d.z);if(l<1e-6)return;
        Vec3d out=new Vec3d(-d.z/l*width*.5,0,d.x/l*width*.5);
        m.quad(a.subtract(out),b.subtract(out),b.add(out),a.add(out),rgb,alpha);
    }
    /** A line from {@code a} to {@code b} facing the camera, {@code width} across. */
    static void line(EffectMesh m,Vec3d a,Vec3d b,double width,int rgb,float alpha,Vec3d cam){
        if(alpha<=0)return;Vec3d d=b.subtract(a),mid=a.add(b).multiply(.5);
        Vec3d out=d.crossProduct(cam.subtract(mid));if(out.lengthSquared()<1e-9)return;
        out=out.normalize().multiply(width*.5);
        m.quad(a.subtract(out),b.subtract(out),b.add(out),a.add(out),rgb,alpha);
    }

    // ───────────────────────────── the ground it hits ─────────────────────────────

    /**
     * The ten by ten blocks whose corner is ({@code x0}, {@code z0}) marked on the ground at {@code top}: crawling dashes
     * round it and solid corners (its user's marker as they aim also a cross at its middle), or their glow.
     */
    static void square(EffectMesh m,int x0,int z0,double top,float sec,float alpha,boolean light,Vec3d cam,boolean marker){
        if(alpha<=0)return;
        double y=top+.05,S=EraseRules.SIDE;
        Vec3d[] c={new Vec3d(x0,y,z0),new Vec3d(x0+S,y,z0),new Vec3d(x0+S,y,z0+S),new Vec3d(x0,y,z0+S)};
        double dash=.55,crawl=sec*1.6%(dash*2);
        for(int e=0;e<4;e++){
            Vec3d a=c[e],b=c[(e+1)%4],ab=b.subtract(a);double len=ab.length();Vec3d dir=ab.multiply(1/len);
            for(double t=-crawl;t<len;t+=dash*2){
                double t0=Math.max(0,t),t1=Math.min(len,t+dash);if(t1<=t0)continue;
                Vec3d p0=a.add(dir.multiply(t0)),p1=a.add(dir.multiply(t1));
                if(light)flatLine(m,p0,p1,.36,NEON,.35f*alpha);else flatLine(m,p0,p1,.11,PURPLE,.85f*alpha);
            }
            // the corner: an L of light at a
            Vec3d prev=c[(e+3)%4].subtract(a).normalize();
            for(Vec3d arm:new Vec3d[]{dir,prev}){
                Vec3d b2=a.add(arm.multiply(1.3));
                if(light)flatLine(m,a,b2,.5,NEON,.45f*alpha);else{flatLine(m,a,b2,.24,INK,.8f*alpha);flatLine(m,a,b2,.16,LILAC,.95f*alpha);}
            }
        }
        if(marker){
            Vec3d mid=new Vec3d(x0+S*.5,y,z0+S*.5);double k=.6+.15*Math.sin(sec*14);
            for(Vec3d arm:new Vec3d[]{new Vec3d(k,0,0),new Vec3d(0,0,k)}){
                if(light)flatLine(m,mid.subtract(arm),mid.add(arm),.3,NEON,.4f*alpha);else flatLine(m,mid.subtract(arm),mid.add(arm),.1,LILAC,.9f*alpha);
            }
        }
    }
    /** How long where it lands flashes (seconds after it struck). */
    static final float IMPACT=.7f;
    /** Where it lands, {@code sec} seconds after it struck: a flash, a ring along the ground, its ground marked bright until the cube rises, pixels thrown up. */
    static void impact(Fx c,EraseClient.Strike s,EffectMesh m,boolean light,float sec){
        if(sec<0||sec>=RISE0+.2f)return;
        Vec3d at=s.to;float near=GlitchFx.near(at.add(0,1,0),c.camera);
        if(light&&near>0&&sec<IMPACT){
            if(sec<.16f)m.star(at.add(0,.6,0),1.4,WHITE,.85f*Ease.smooth(sec,0,.015f)*(1-sec/.16f)*near);
            m.softDisc(at.add(0,.8,0),4.5,NEON,.3f*Ease.smooth(sec,0,.02f)*(1-Ease.smooth(sec,.08f,.6f))*near);
        }
        float r=sec/.55f;
        if(r<1){double rad=1+9*Ease.out(sec,0,.55f);
            if(light)FxLib.ring(m,at.add(0,.08,0),rad,.7,NEON,.45f*(1-r));else FxLib.ring(m,at.add(0,.08,0),rad,.25,PURPLE,.8f*(1-r));}
        // the ground marked bright, handing over to the cube as it rises
        float a=Ease.smooth(sec,0,.03f)*(1-Ease.smooth(sec,RISE0-.05f,RISE0+.15f));
        square(m,s.x0,s.z0,s.top,sec,a,light,c.camera,false);
        // pixels thrown up off its ground
        for(int k=0;k<6;k++){
            Vec3d p=new Vec3d(s.x0+1.5+7*h(k,s.seed,11),s.top+.2,s.z0+1.5+7*h(k,s.seed,12));
            GlitchFx.pixels(m,p,new Vec3d(0,1,0),.6,7,.08,10,s.seed+k*31,sec-.02f*k,light,c.camera);
        }
    }

    // ───────────────────────────── the cube ─────────────────────────────

    /** Its cube's faces as this client found them: each a face of one of its blocks on its outside, the block (null: none, a void), and which way. */
    static final class Faces {
        final int n,base;final byte[] dir,x,y,z;final BlockState[] state;
        Faces(int n,int base){this.n=n;this.base=base;dir=new byte[n];x=new byte[n];y=new byte[n];z=new byte[n];state=new BlockState[n];}
    }
    static final Direction[] DIRS={Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    /** The world y of the lowest blocks of strike {@code s}'s cube: its ground's top block and the nine under it. */
    static int base(EraseClient.Strike s){return (int)Math.ceil(s.top-1e-6)-EraseRules.SIDE;}
    /** The faces of strike {@code s}'s cube, found from {@code world} as it is (its outside only: what shows). */
    static Faces faces(EraseClient.Strike s,World world){
        int S=EraseRules.SIDE,base=base(s);
        var dirs=new ArrayList<int[]>();var states=new ArrayList<BlockState>();
        var pos=new BlockPos.Mutable();
        // the ground's blocks there; where there are none (air, water, a cave, under the world's floor), the nearest of its
        // column's, deeper first, or as ground goes (grass over dirt over stone): it rises as one solid piece of ground
        BlockState[] grid=null;
        if(world!=null){
            grid=new BlockState[S*S*S];
            for(int x=0;x<S;x++)for(int y=0;y<S;y++)for(int z=0;z<S;z++){pos.set(s.x0+x,base+y,s.z0+z);grid[(x*S+y)*S+z]=solid(world,pos);}
        }
        for(int x=0;x<S;x++)for(int y=0;y<S;y++)for(int z=0;z<S;z++){
            boolean edge=x==0||x==S-1||y==0||y==S-1||z==0||z==S-1;if(!edge)continue;
            BlockState st=grid==null?null:filled(grid,x,y,z);
            int[] which={y==S-1?0:-1,y==0?1:-1,z==0?2:-1,z==S-1?3:-1,x==0?4:-1,x==S-1?5:-1};
            for(int d:which)if(d>=0){dirs.add(new int[]{d,x,y,z});states.add(st);}
        }
        var f=new Faces(dirs.size(),base);
        for(int i=0;i<f.n;i++){var q=dirs.get(i);f.dir[i]=(byte)q[0];f.x[i]=(byte)q[1];f.y[i]=(byte)q[2];f.z[i]=(byte)q[3];f.state[i]=states.get(i);}
        return f;
    }
    /** The block at {@code pos} if it is a solid one drawn as a model (null: air, a liquid, a plant, a chest's sort). */
    static BlockState solid(World world,BlockPos pos){
        var b=world.getBlockState(pos);
        return b.isAir()||b.getRenderType()!=net.minecraft.block.BlockRenderType.MODEL||b.getCollisionShape(world,pos).isEmpty()?null:b;
    }
    /** Its block at ({@code x}, {@code y}, {@code z}) of its {@code grid}: the ground's own, or where it has none the nearest of its column's (deeper first), or grass over dirt over stone. */
    static BlockState filled(BlockState[] grid,int x,int y,int z){
        var near=nearest(grid,x,y,z);if(near!=null)return near;
        int depth=EraseRules.SIDE-1-y;
        return (depth==0?net.minecraft.block.Blocks.GRASS_BLOCK:depth<4?net.minecraft.block.Blocks.DIRT:net.minecraft.block.Blocks.STONE).getDefaultState();
    }
    /** What {@code grid} (SIDE³, x then y then z) has at ({@code x}, {@code y}, {@code z}), or the nearest its column has (deeper first at a tie), or null. */
    static <T> T nearest(T[] grid,int x,int y,int z){
        int S=EraseRules.SIDE;
        var own=grid[(x*S+y)*S+z];if(own!=null)return own;
        for(int d=1;d<S;d++){
            if(y-d>=0&&grid[(x*S+y-d)*S+z]!=null)return grid[(x*S+y-d)*S+z];
            if(y+d<S&&grid[(x*S+y+d)*S+z]!=null)return grid[(x*S+y+d)*S+z];
        }
        return null;
    }
    /**
     * How hard it glitches {@code tr} ticks after it struck (0 to 1): a little as it rises out of the ground, a trace
     * as it waits, more and more over its last five seconds.
     */
    static float tension(float tr){
        float sec=tr/20f;
        float rising=.18f*Ease.smooth(sec,RISE0,RISE0+.3f)*(1-Ease.smooth(sec,RISE0+5.5f,RISE0+6.5f));
        float ramp=.06f+.94f*Ease.in(sec,BURST_S-5f,BURST_S);
        return Math.max(rising,ramp);
    }
    /** How far layer {@code layer} of it (from its bottom) slips aside {@code sec} seconds after it struck: in steps of a twelfth of a second, now and then a few layers together. */
    static Vec3d slice(int layer,float sec,int seed,float tension){
        int step=(int)Math.floor(sec*12);
        if(h(step,seed,91)>.07+.4*tension)return Vec3d.ZERO;
        int from=(int)(h(step,seed,92)*EraseRules.SIDE),len=1+(int)(h(step,seed,93)*(1.5+4.5*tension));
        if(layer<from||layer>=from+len)return Vec3d.ZERO;
        double amount=(.12+.55*tension)*(h(step,seed,94)*2-1);
        return h(step,seed,95)<.5?new Vec3d(amount,0,0):new Vec3d(0,0,amount);
    }
    /** How the whole of it shakes (in twentieths of a second): only when it glitches hard. */
    static Vec3d jitter(float sec,int seed,float tension){
        if(tension<.45f)return Vec3d.ZERO;
        int step=(int)Math.floor(sec*20);double a=(.04+.16*(tension-.45)/.55);
        return new Vec3d((h(step,seed,96)*2-1)*a,(h(step,seed,97)*2-1)*a*.5,(h(step,seed,98)*2-1)*a);
    }
    /** Whether its blocks are gone to a void for this twentieth of a second (rarely; often near its burst). */
    static boolean flick(float sec,int seed,float tension){
        int step=(int)Math.floor(sec*20);
        return h(step,seed,99)<.006+.11*tension*tension;
    }
    /** Where its least corner is drawn {@code tr} ticks after it struck (null: there is none now). */
    static Vec3d corner(EraseClient.Strike s,float tr,Vec3d jit){
        double lift=EraseRules.lift(tr);if(Double.isNaN(lift)||tr<EraseRules.RISE_FROM)return null;
        return new Vec3d(s.x0+jit.x,base(s)+lift+.003+jit.y,s.z0+jit.z);
    }
    /** Whether the cube is drawn now: risen and not burst, not cut short. */
    static boolean showsCube(EraseClient.Strike s,float age,float tr){return tr>=EraseRules.RISE_FROM&&tr<EraseRules.BURST&&age<s.cut;}
    /** Its blocks (the solids): each face on its outside drawn as its block shows it there, its layers slipping as it glitches. */
    static void cube(FxSolids so,EraseClient.Strike s,float tr){
        var f=s.faces;if(f==null)return;
        float sec=tr/20f,tension=tension(tr);
        if(flick(sec,s.seed,tension))return;
        Vec3d jit=jitter(sec,s.seed,tension),o=corner(s,tr,jit);if(o==null)return;
        int S=EraseRules.SIDE;Vec3d[] sl=new Vec3d[S];for(int y=0;y<S;y++)sl[y]=slice(y,sec,s.seed,tension);
        for(int i=0;i<f.n;i++){
            if(f.state[i]==null)continue;
            Vec3d d=sl[f.y[i]];var dir=DIRS[f.dir[i]];
            double wx=o.x+f.x[i]+d.x,wy=o.y+f.y[i],wz=o.z+f.z[i]+d.z;
            var tintAt=new BlockPos(s.x0+f.x[i],f.base+f.y[i],s.z0+f.z[i]);
            var lit=BlockPos.ofFloored(wx+.5+dir.getOffsetX(),wy+.5+dir.getOffsetY(),wz+.5+dir.getOffsetZ());
            so.face(f.state[i],tintAt,lit,dir,wx,wy,wz);
        }
    }
    /** One face of a cell of the cube drawn flat in one colour (a void where there is no block, or all of it as it flicks), {@code inset} in from its edges. */
    private static void cellFace(EffectMesh m,Vec3d o,int dir,int x,int y,int z,int rgb,float alpha,double inset){
        double x0=o.x+x,y0=o.y+y,z0=o.z+z,a=inset,b=1-inset;
        Vec3d p0,p1,p2,p3;
        switch(dir){
            case 0->{double yy=y0+1.002;p0=new Vec3d(x0+a,yy,z0+a);p1=new Vec3d(x0+b,yy,z0+a);p2=new Vec3d(x0+b,yy,z0+b);p3=new Vec3d(x0+a,yy,z0+b);}
            case 1->{double yy=y0-.002;p0=new Vec3d(x0+a,yy,z0+a);p1=new Vec3d(x0+a,yy,z0+b);p2=new Vec3d(x0+b,yy,z0+b);p3=new Vec3d(x0+b,yy,z0+a);}
            case 2->{double zz=z0-.002;p0=new Vec3d(x0+a,y0+a,zz);p1=new Vec3d(x0+a,y0+b,zz);p2=new Vec3d(x0+b,y0+b,zz);p3=new Vec3d(x0+b,y0+a,zz);}
            case 3->{double zz=z0+1.002;p0=new Vec3d(x0+a,y0+a,zz);p1=new Vec3d(x0+b,y0+a,zz);p2=new Vec3d(x0+b,y0+b,zz);p3=new Vec3d(x0+a,y0+b,zz);}
            case 4->{double xx=x0-.002;p0=new Vec3d(xx,y0+a,z0+a);p1=new Vec3d(xx,y0+a,z0+b);p2=new Vec3d(xx,y0+b,z0+b);p3=new Vec3d(xx,y0+b,z0+a);}
            default->{double xx=x0+1.002;p0=new Vec3d(xx,y0+a,z0+a);p1=new Vec3d(xx,y0+b,z0+a);p2=new Vec3d(xx,y0+b,z0+b);p3=new Vec3d(xx,y0+a,z0+b);}
        }
        m.quad(p0,p1,p2,p3,rgb,alpha);
    }
    /** The corners of a box from {@code o}, {@code S} each way. */
    private static Vec3d[] box(Vec3d o,double S){
        return new Vec3d[]{o,o.add(S,0,0),o.add(S,0,S),o.add(0,0,S),o.add(0,S,0),o.add(S,S,0),o.add(S,S,S),o.add(0,S,S)};
    }
    private static final int[][] EDGES={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
    /**
     * What the effects' layer adds to the cube as it glitches: the voids where it has no block (or all of it as it
     * flicks), its edges glowing (and split red and cyan), bands of light running up its sides, pixels of data on it and,
     * near its burst, peeling off it; and the seam at the ground pixels pop from as it rises.
     */
    static void cubeFx(Fx c,EraseClient.Strike s,EffectMesh m,boolean light,float tr){
        var f=s.faces;if(f==null)return;
        float sec=tr/20f,tension=tension(tr);Vec3d cam=c.camera;
        Vec3d jit=jitter(sec,s.seed,tension),o=corner(s,tr,jit);if(o==null)return;
        double S=EraseRules.SIDE;boolean flicks=flick(sec,s.seed,tension);
        // the voids: where it has no block, or (as it flicks) all of it
        if(!light){
            for(int i=0;i<f.n;i++){
                if(f.state[i]!=null&&!flicks)continue;
                cellFace(m,o,f.dir[i],f.x[i],f.y[i],f.z[i],VOID_EDGE,.9f,0);
                cellFace(m,o,f.dir[i],f.x[i],f.y[i],f.z[i],VOID,.95f,.06);
            }
        }
        // its edges: glowing purple, pulsing faster as it nears its burst, split red and cyan as it glitches
        Vec3d[] k=box(o,S);float pulse=(float)(.75+.25*Math.sin(sec*(5+16*tension)));
        Vec3d mid=o.add(S*.5,S*.5,S*.5);var face=GlitchFx.facing(cam,mid);
        double split=.05+.22*tension;
        for(int[] e:EDGES){
            Vec3d a=k[e[0]],b=k[e[1]];
            if(light){
                line(m,a,b,.55+.4*tension,NEON,(.22f+.3f*tension)*pulse,cam);
                line(m,a,b,.12,WHITE,(.25f+.4f*tension)*pulse,cam);
                if(tension>.08f){
                    float sa=.12f+.3f*tension;
                    line(m,a.add(face[0].multiply(split)),b.add(face[0].multiply(split)),.1,RED,sa,cam);
                    line(m,a.subtract(face[0].multiply(split)),b.subtract(face[0].multiply(split)),.1,CYAN,sa,cam);
                }
            }else line(m,a,b,.09,PURPLE,.9f,cam);
        }
        // bands of light running up its four sides
        for(int b=0;b<3;b++){
            double yb=((sec*(1.1+1.8*tension)+b/3.0)%1.0)*S,hb=.16;
            Vec3d lo=o.add(0,yb,0);
            Vec3d[] ring={lo.add(-.012,0,-.012),lo.add(S+.012,0,-.012),lo.add(S+.012,0,S+.012),lo.add(-.012,0,S+.012)};
            for(int e=0;e<4;e++){
                Vec3d a=ring[e],c2=ring[(e+1)%4];
                if(light)m.quad(a,c2,c2.add(0,hb,0),a.add(0,hb,0),NEON,.16f+.2f*tension);
                else m.quad(a,c2,c2.add(0,hb,0),a.add(0,hb,0),LILAC,.22f+.25f*tension);
            }
        }
        // pixels of data flickering on its faces
        int count=10+(int)(40*tension),step=(int)Math.floor(sec*12);
        for(int j=0;j<count;j++){
            int i=Math.min(f.n-1,(int)(h(j+step*61,s.seed,201)*f.n));
            if(h(j+step*13,s.seed,202)>.7)continue;
            int rgb=switch((int)(h(j,s.seed,203)*5)){case 0->WHITE;case 1->INK;case 2->PINK;default->PURPLE;};
            double in=.15+.3*h(j+step*7,s.seed,204);
            if(light){if(rgb!=INK)cellFace(m,o,f.dir[i],f.x[i],f.y[i],f.z[i],rgb==WHITE?GLOW:rgb,.3f,in);}
            else cellFace(m,o,f.dir[i],f.x[i],f.y[i],f.z[i],rgb,.92f,in);
        }
        // near its burst, pixels peeling off its edges (each a little burst of its own, eight a second)
        if(tension>.3f){
            int n=(int)(18*tension),now=(int)Math.floor(sec*8);
            for(int j=0;j<n;j++)for(int q=0;q<3;q++){
                int born=now-q;float age=sec-born/8f;
                int[] e=EDGES[(int)(h(j+born*17,s.seed,211)*12)%12];
                Vec3d at=k[e[0]].lerp(k[e[1]],h(j+born*29,s.seed,213));
                GlitchFx.pixels(m,at,at.subtract(mid).normalize(),.6,4,.07,3,s.seed+j*97+born*13,age,light,cam);
            }
        }
        // the seam at the ground as it rises out of it: pixels popping
        if(tr<EraseRules.RISE_FROM+EraseRules.RISE_TICKS){
            int now=(int)Math.floor(tr);
            for(int q=0;q<4;q++){
                int t=now-q;if(t<EraseRules.RISE_FROM)continue;
                float age=(tr-t)/20f;
                for(int side=0;side<4;side++){
                    double u=h(t*4+side,s.seed,221)*S;
                    Vec3d at=switch(side){case 0->new Vec3d(s.x0+u,s.top+.1,s.z0);case 1->new Vec3d(s.x0+S,s.top+.1,s.z0+u);case 2->new Vec3d(s.x0+u,s.top+.1,s.z0+S);default->new Vec3d(s.x0,s.top+.1,s.z0+u);};
                    Vec3d out=switch(side){case 0->new Vec3d(0,.6,-1);case 1->new Vec3d(1,.6,0);case 2->new Vec3d(0,.6,1);default->new Vec3d(-1,.6,0);};
                    GlitchFx.pixels(m,at,out.normalize(),.5,4,.07,3,s.seed+t*41+side*7,age,light,cam);
                }
            }
        }
    }

    // ───────────────────────────── its burst ─────────────────────────────

    /** Its burst's pieces: how many, and how long they fly (seconds). */
    static final int FRAGS=70;static final float FRAG_LIFE=1.8f;
    /** Its blocks flung out in pieces as it bursts (the solids): each a piece of one of its blocks, out from its middle and up, falling to the ground. */
    static void fragments(FxSolids so,EraseClient.Strike s,float tr){
        float t=(tr-EraseRules.BURST)/20f;var f=s.faces;
        if(f==null||t<0||t>=FRAG_LIFE)return;
        int stride=Math.max(1,f.n/FRAGS);double L=EraseRules.LIFT,S=EraseRules.SIDE;
        Vec3d mid=new Vec3d(s.x0+S*.5,f.base+L+S*.5,s.z0+S*.5);
        for(int i=0,k=0;i<f.n&&k<FRAGS;i+=stride,k++){
            BlockState st=f.state[i];if(st==null)continue;
            Vec3d p0=new Vec3d(s.x0+f.x[i]+.5,f.base+f.y[i]+L+.5,s.z0+f.z[i]+.5);
            Vec3d out=p0.subtract(mid);out=out.lengthSquared()<1e-6?new Vec3d(0,1,0):out.normalize();
            Vec3d v0=out.multiply(4+6*h(k,s.seed,301)).add(0,2+4*h(k,s.seed,302),0);
            Vec3d p=FxSolids.flight(p0,v0,s.top+.3,t);
            double size=(.45+.4*h(k,s.seed,303))*(1-Ease.smooth(t,FRAG_LIFE-.5f,FRAG_LIFE));
            float spin=(float)((220+420*h(k,s.seed,304))*Math.min(t,1.3f));
            so.chunk(st,p,size,spin,spin*.7f,spin*.4f);
        }
    }
    /** How long its burst's light lasts (seconds). */
    static final float BURST_LIFE=1.2f;
    /** Its burst {@code t} seconds after: a white flash, purple spikes out every way, pixels everywhere, rings in the air and along the ground. */
    static void burst(Fx c,EraseClient.Strike s,EffectMesh m,boolean light,float t){
        if(t<0||t>=BURST_LIFE)return;
        double S=EraseRules.SIDE,L=EraseRules.LIFT;Vec3d mid=new Vec3d(s.x0+S*.5,base(s)+L+S*.5,s.z0+S*.5);Vec3d cam=c.camera;
        float near=GlitchFx.near(mid,cam);
        if(light&&near>0){
            if(t<.2f)m.star(mid,2.4,WHITE,.9f*Ease.smooth(t,0,.015f)*(1-t/.2f)*near);
            if(t<.14f)m.softDisc(mid,8,WHITE,.6f*(1-t/.14f)*near);
            m.softDisc(mid,14,NEON,.32f*Ease.smooth(t,0,.03f)*(1-Ease.smooth(t,.1f,.8f))*near);
        }
        // spikes out every way
        if(t<.5f)for(int j=0;j<26;j++){
            double yaw=Math.PI*2*h(j,s.seed,311),pitch=Math.asin(h(j,s.seed,312)*1.6-.6);
            Vec3d dir=new Vec3d(Math.cos(pitch)*Math.cos(yaw),Math.sin(pitch),Math.cos(pitch)*Math.sin(yaw));
            float life=.5f*(.6f+.4f*(float)h(j,s.seed,313));if(t>=life)continue;
            double len=(5+6*h(j,s.seed,314))*Ease.out(t,0,.08f);float fade=Ease.smooth(t,0,.02f)*(1-Ease.smooth(t,life*.35f,life))*near;
            Vec3d a0=mid.add(dir.multiply(1.5)),a1=mid.add(dir.multiply(1.5+len));int kind=j%3;
            if(light){if(kind==0)m.needle(a0,a1,.28,.22,WHITE,.85f*fade);else if(kind==1)m.needle(a0,a1,.5,.22,NEON,.22f*fade);}
            else if(kind!=0){m.needle(a0,a1,.4,.22,INK,.85f*fade);m.needle(a0,a1,.28,.22,kind==1?PURPLE:DEEP,.95f*fade);}
        }
        // pixels everywhere: from its corners and the middles of its faces
        Vec3d[] k=box(new Vec3d(s.x0,base(s)+L,s.z0),S);
        for(int q=0;q<8;q++)GlitchFx.pixels(m,k[q],k[q].subtract(mid).normalize(),.7,12,.12,22,s.seed+q*53,t,light,cam);
        Vec3d[] faces={mid.add(0,S*.5,0),mid.add(0,-S*.5,0),mid.add(S*.5,0,0),mid.add(-S*.5,0,0),mid.add(0,0,S*.5),mid.add(0,0,-S*.5)};
        for(int q=0;q<6;q++)GlitchFx.pixels(m,faces[q],faces[q].subtract(mid).normalize(),.6,10,.12,18,s.seed+q*71+500,t,light,cam);
        // rings: one round it in the air, one along the ground
        float r1=t/.6f;
        if(r1<1){double rad=6+12*Ease.out(t,0,.6f);
            if(light)FxLib.ring(m,mid,rad,1.2,NEON,.45f*(1-r1));else FxLib.ring(m,mid,rad,.35,PURPLE,.8f*(1-r1));}
        float r2=(t-.05f)/.9f;
        if(r2>0&&r2<1){double rad=4+18*Ease.out(t-.05f,0,.9f);Vec3d g=new Vec3d(mid.x,s.top+.08,mid.z);
            if(light)FxLib.ring(m,g,rad,1.0,NEON,.4f*(1-r2));else FxLib.ring(m,g,rad,.3,LILAC,.75f*(1-r2));}
    }

    // ───────────────────────────── where it was ─────────────────────────────

    /** The ground is drawn back over this long before EraseRules.BACK (seconds): flickering, then a line of light sweeping over it. */
    static final float RESTORE=.6f,SWEEP=.45f;
    /** How far over the void the sweep has come at {@code sec} seconds after it struck (0 to 1; behind it the ground is back). */
    static float swept(float sec){return (float)Math.max(0,Math.min(1,(sec-(BACK_S-SWEEP-.05f))/SWEEP));}
    /** Whether the void shows {@code sec} seconds after it struck: from as the cube clears the ground (its gap shows it) to the ground back, flickering before the sweep. */
    static boolean voidShows(float sec,float tr){
        if(sec<RISE0||sec>=BACK_S)return false;
        if(sec<BURST_S){double lift=EraseRules.lift(tr);if(!(lift>.06))return false;}
        if(sec>=BACK_S-RESTORE&&sec<BACK_S-SWEEP-.05f){int step=(int)Math.floor(sec*30);return step%3!=0;}
        return true;
    }
    /** Where it was ({@code tr} ticks after it struck): a dark void on the ground, edged in purple, a grid, flickering static, a band of light going over it, and at the last the sweep that draws the ground back. */
    static void voidPlane(Fx c,EraseClient.Strike s,EffectMesh m,boolean light,float tr){
        float sec=tr/20f;if(!voidShows(sec,tr))return;
        double S=EraseRules.SIDE,y=s.top+.02,from=s.x0+S*swept(sec);
        if(from>=s.x0+S-1e-3)return;
        double x1=s.x0+S,z0=s.z0,z1=s.z0+S;
        float in=Ease.smooth(sec,RISE0,RISE0+.3f);
        if(!light){
            m.quad(new Vec3d(from,y,z0),new Vec3d(x1,y,z0),new Vec3d(x1,y,z1),new Vec3d(from,y,z1),VOID,.94f*in);
            // the grid, every block
            for(int g=1;g<EraseRules.SIDE;g++){
                double gx=s.x0+g;if(gx>from)flatLine(m,new Vec3d(gx,y+.004,z0),new Vec3d(gx,y+.004,z1),.04,VOID_EDGE,.9f*in);
                flatLine(m,new Vec3d(from,y+.004,z0+g),new Vec3d(x1,y+.004,z0+g),.04,VOID_EDGE,.9f*in);
            }
            // static: squares of lilac, dark purple and black flickering on it
            int step=(int)Math.floor(sec*10);
            for(int j=0;j<28;j++){
                double px=s.x0+S*h(j+step*29,s.seed,231),pz=s.z0+S*h(j+step*31,s.seed,232),half=.12+.22*h(j,s.seed,233);
                if(px-half<from||px+half>x1||pz-half<z0||pz+half>z1||h(j+step*7,s.seed,234)>.65)continue;
                int rgb=j%3==0?LILAC:j%3==1?DEEP:INK;
                m.quad(new Vec3d(px-half,y+.006,pz-half),new Vec3d(px+half,y+.006,pz-half),new Vec3d(px+half,y+.006,pz+half),new Vec3d(px-half,y+.006,pz+half),rgb,.75f*in);
            }
        }
        // a band of light going over it, slowly
        double band=s.z0+((sec*.55)%1.0)*S;
        if(light)flatLine(m,new Vec3d(from,y+.008,band),new Vec3d(x1,y+.008,band),.5,NEON,.16f*in);
        else flatLine(m,new Vec3d(from,y+.008,band),new Vec3d(x1,y+.008,band),.14,PURPLE,.55f*in);
        // its edge (the part not yet swept)
        Vec3d[] c4={new Vec3d(from,y+.01,z0),new Vec3d(x1,y+.01,z0),new Vec3d(x1,y+.01,z1),new Vec3d(from,y+.01,z1)};
        for(int e=0;e<4;e++){
            if(light)flatLine(m,c4[e],c4[(e+1)%4],.5,NEON,.4f*in);else flatLine(m,c4[e],c4[(e+1)%4],.13,PURPLE,.95f*in);
        }
        // the sweep: a line of light drawing the ground back, pixels thrown off it
        float sw=swept(sec);
        if(sw>0&&sw<1){
            Vec3d a=new Vec3d(from,y+.03,z0),b=new Vec3d(from,y+.03,z1);
            if(light){flatLine(m,a,b,.9,WHITE,.6f);flatLine(m,a,b,2.2,NEON,.3f);}
            else flatLine(m,a,b,.2,LILAC,.95f);
            int now=(int)Math.floor(tr);
            for(int q=0;q<3;q++){
                int t=now-q;float age=(tr-t)/20f;
                for(int j=0;j<3;j++)GlitchFx.pixels(m,new Vec3d(from,y+.1,z0+S*h(t*3+j,s.seed,241)),new Vec3d(-.4,1,0).normalize(),.6,4,.07,3,s.seed+t*17+j,age,light,c.camera);
            }
        }
    }

    // ───────────────────────────── whom it deleted, whom the burst struck ─────────────────────────────

    /** The outline of where one deleted stands blinks this often (a second), with this many pixels of them in it. */
    static final float BLINK=16;static final int MOTES=7,GATHER=16;
    /** One deleted, {@code s} seconds after: a burst as it strikes, the outline while they are gone, their pixels gathering back, torn bars as they are back. */
    static void victims(Fx c,EraseClient.Strike w,EffectMesh m,boolean light){
        if(w.deleted.isEmpty()||c.mc.world==null)return;
        Vec3d cam=c.camera;float vanish=EraseClient.VANISH;Vec3d up=new Vec3d(0,1,0);
        for(var g:w.deleted.values()){
            float s=g.since(c.delta);if(s<0||s>=vanish+.3f)continue;
            if(!(c.mc.world.getEntityById(g.entity()) instanceof LivingEntity e))continue;
            boolean own=e==c.mc.getCameraEntity()&&!c.mc.gameRenderer.getCamera().isThirdPerson();
            Vec3d feet=e.getLerpedPos(c.delta),mid=feet.add(0,e.getHeight()*.55,0);
            int seed=g.entity()*7919+w.seed;
            float near=GlitchFx.near(mid,cam);
            var face=GlitchFx.facing(cam,mid);
            if(s<.35f&&!own){
                Vec3d at=mid.add(face[2].multiply(e.getWidth()*.5+.2));
                if(light){
                    if(s<.18f)m.star(at,1.2,WHITE,.9f*Ease.smooth(s,0,.02f)*(1-s/.18f)*near);
                    m.softDisc(at,2.2,NEON,.3f*(1-Ease.smooth(s,.05f,.35f))*near);
                }else for(int j=0;j<10;j++){
                    double an=Math.PI*2*(j+.6*h(j,seed,21))/10;Vec3d dir=face[0].multiply(Math.cos(an)).add(face[1].multiply(Math.sin(an)));
                    float life=.3f*(.6f+.4f*(float)h(j,seed,22));if(s>=life)continue;
                    double len=(.8+1.4*h(j,seed,23))*Ease.out(s,0,.06f);float fade=Ease.smooth(s,0,.02f)*(1-Ease.smooth(s,life*.35f,life))*near;
                    Vec3d a0=at.add(dir.multiply(.25)),a1=at.add(dir.multiply(.25+len));
                    m.needle(a0,a1,.16,.22,INK,.85f*fade);m.needle(a0,a1,.1,.22,j%2==0?PURPLE:DEEP,.95f*fade);
                }
                GlitchFx.pixels(m,mid,up,.85,6,.07,24,seed,s,light,cam);
            }
            if(s>=.06f&&s<vanish&&!own&&near>0){
                double hw=e.getWidth()*.5+.12,hh=e.getHeight()*.5+.08;Vec3d box=feet.add(0,e.getHeight()*.5,0);
                boolean on=((int)Math.floor(s*BLINK))%2==0;
                if(on)outline(m,box,face,hw,hh,light?GLOW:PURPLE,(light?.35f:.75f)*near,s);
                int step=(int)Math.floor(s*12);
                for(int j=0;j<MOTES;j++){
                    if(h(j+step*17,seed,31)>.6)continue;
                    Vec3d p=box.add(face[0].multiply((h(j+step*7,seed,32)-.5)*2*hw)).add(face[1].multiply((h(j+step*11,seed,33)-.5)*2*hh));
                    double half=.05+.05*h(j,seed,34);int rgb=j%3==0?LILAC:PURPLE;
                    if(light)GlitchFx.square(m,p,face,half*1.8,GLOW,.25f*near);
                    else GlitchFx.square(m,p,face,half,rgb,.9f*near);
                }
            }
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
    /** Whom the burst struck, as this client heard of it: a white star and a burst of pixels on them. */
    static void blasted(Fx c,EraseClient.Strike w,EffectMesh m,boolean light){
        if(w.blasted.isEmpty()||c.mc.world==null)return;
        for(var en:w.blasted.entrySet()){
            float s=(c.age-en.getValue())/20f;if(s<0||s>=.5f)continue;
            if(!(c.mc.world.getEntityById(en.getKey()) instanceof LivingEntity e))continue;
            boolean own=e==c.mc.getCameraEntity()&&!c.mc.gameRenderer.getCamera().isThirdPerson();if(own)continue;
            Vec3d mid=e.getLerpedPos(c.delta).add(0,e.getHeight()*.55,0);var face=GlitchFx.facing(c.camera,mid);
            float near=GlitchFx.near(mid,c.camera);Vec3d at=mid.add(face[2].multiply(e.getWidth()*.5+.2));
            if(light&&s<.2f)m.star(at,1.2,WHITE,.9f*(1-s/.2f)*near);
            GlitchFx.pixels(m,mid,new Vec3d(0,1,0),.9,7,.08,20,en.getKey()*131+w.seed,s,light,c.camera);
        }
    }
    /** A dashed outline of a box facing the camera: {@code hw} wide and {@code hh} high from its middle, its dashes crawling round it. */
    private static void outline(EffectMesh m,Vec3d mid,Vec3d[] face,double hw,double hh,int rgb,float alpha,float s){
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

    // ───────────────────────────── the passes ─────────────────────────────

    private static EraseClient.Strike of(Fx c){
        var s=EraseClient.of(c.cast);if(s==null)return null;
        if(s.faces==null&&c.age>=s.fly-1)s.faces=faces(s,c.mc.world);
        return s;
    }
    /** Ticks since it struck, for the cube: a tick behind its clock (as the bodies on it are drawn between their last place and this tick's). */
    static float cubeTicks(EraseClient.Strike s,float age){return age-s.fly-1;}
    /** Everything but the slash itself, in one pass: where it lands, the cube's glitches, its burst, where it was, whom it struck. */
    static void zone(Fx c,EffectMesh m,boolean light){
        var s=of(c);if(s==null)return;
        float age=c.age,sec=(age-s.fly)/20f,tr=cubeTicks(s,age),fade=kept(s,age);
        boolean cut=age>=s.cut;
        if(!cut)impact(c,s,m,light,sec);
        if(showsCube(s,age,tr))cubeFx(c,s,m,light,tr);
        if(!cut)burst(c,s,m,light,(tr-EraseRules.BURST)/20f);
        if(!cut)voidPlane(c,s,m,light,tr);
        // cut short: the cube glitches out where it was
        if(cut&&fade>0&&s.faces!=null){
            float t=(age-s.cut)/20f;double S=EraseRules.SIDE,lift=EraseRules.lift(cubeTicks(s,s.cut));
            if(!Double.isNaN(lift)&&cubeTicks(s,s.cut)>=EraseRules.RISE_FROM){
                Vec3d mid=new Vec3d(s.x0+S*.5,base(s)+lift+S*.5,s.z0+S*.5);
                for(int q=0;q<6;q++)GlitchFx.pixels(m,mid.add((h(q,s.seed,251)-.5)*S,(h(q,s.seed,252)-.5)*S,(h(q,s.seed,253)-.5)*S),new Vec3d(0,1,0),1,6,.1,14,s.seed+q*19,t,light,c.camera);
            }
        }
        victims(c,s,m,light);
        blasted(c,s,m,light);
    }
    /** The solids: pieces of the ground flung where it lands, the cube's blocks, its burst's pieces. */
    static void solids(Fx c,FxSolids so){
        var s=of(c);if(s==null)return;
        float age=c.age,sec=(age-s.fly)/20f,tr=cubeTicks(s,age);
        // pieces of the ground flung where it lands
        if(sec>=0&&sec<1.4f&&age<s.cut){
            BlockState st=so.ground(s.to.add(0,.2,0));
            if(st!=null)for(int k=0;k<10;k++){
                double an=Math.PI*2*(k+.5*h(k,s.seed,261))/10,out=2+3*h(k,s.seed,262);
                Vec3d v0=new Vec3d(Math.cos(an)*out,5+4*h(k,s.seed,263),Math.sin(an)*out);
                Vec3d p=FxSolids.flight(s.to.add(0,.25,0),v0,s.top+.15,sec);
                double size=(.22+.2*h(k,s.seed,264))*(1-Ease.smooth(sec,1f,1.4f));
                float spin=(float)((260+380*h(k,s.seed,265))*Math.min(sec,1f));
                so.chunk(st,p,size,spin,spin*.7f,spin*.4f);
            }
        }
        if(showsCube(s,age,tr))cube(so,s,tr);
        if(age<s.cut)fragments(so,s,tr);
    }
    /** Its light on the world: as it flies, where it lands, the cube's glow (stronger as it nears its burst), its burst, the void's; little afterglow as it flies. */
    static void frame(Fx c,FxFrame fr){
        var s=EraseClient.of(c.cast);if(s==null)return;
        float age=c.age,sec=(age-s.fly)/20f,tr=cubeTicks(s,age);
        if(age<s.fly+2)fr.afterglow(.05f);
        if(age>=s.cut)return;
        if(age<s.fly)fr.light(tip(s,age),0xa040ff,10,1.2f);
        if(sec>=0&&sec<.7f)fr.light(s.to.add(0,1,0),0xb04cff,18,2.4f*(1-sec/.7f));
        double S=EraseRules.SIDE;
        if(showsCube(s,age,tr)){
            double lift=EraseRules.lift(tr);float tension=tension(tr);
            fr.light(new Vec3d(s.x0+S*.5,base(s)+lift+S*.5,s.z0+S*.5),0x8a2be2,16,(.5f+1.1f*tension)*(float)(.8+.2*Math.sin(tr/20f*(5+16*tension))));
        }
        float t=(tr-EraseRules.BURST)/20f;
        if(t>=0&&t<1f)fr.light(new Vec3d(s.x0+S*.5,base(s)+EraseRules.LIFT+S*.5,s.z0+S*.5),0xc45cff,32,3f*(1-t));
        if(voidShows(tr/20f,tr))fr.light(new Vec3d(s.x0+S*.5,s.top+.5,s.z0+S*.5),0x6a0cb8,9,.45f);
    }
    /** How hard it shakes the camera (KagenoCamera weighs it by how near it is): as it lands, as its cube begins to rise and a rumble as it does, hard as it bursts. */
    static float shake(EraseClient.Strike s,float sec){
        float age=sec*20,since=sec-s.fly/20f,v=0;
        if(age>=s.cut)return 0;
        v+=1.4f*KnifeCombo.bump(since,0,.14f);
        v+=.3f*KnifeCombo.bump(since,RISE0,.1f);
        if(since>=RISE0&&since<RISE0+EraseRules.RISE_TICKS/20f)v+=.12f;
        v+=2.0f*KnifeCombo.bump(since,BURST_S+.05f,.16f);
        v+=.2f*KnifeCombo.bump(since,BACK_S,.1f);
        return v;
    }
}
