package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
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
 * X Chara's terrain delete once its grounds are marked, drawn (2.133.0; EraseClient's casts, EraseZone's passes; the
 * server's XCharaErase, EraseRules). Everything of how it looks is mine but what the user asked for (a giant "!!!" over
 * the ground looked at and a "!!!" over each of the others, the ground rising as a cube ten blocks each way, glitching as
 * one and bursting, the ground back ten seconds on); everything is a function of the call's own clock (ticks since the
 * mark), of where its grounds are and of their seeds, the same on every client.
 * <ul>
 * <li>The "!!!" ({@link #marks}, G's own, SwordDance.bangs): over the ground looked at a giant one ({@link #GIANT}), over
 * the others one each (over the head of whom it is under, where they stood), each ground's ten by ten marked with
 * crawling dashes ({@link #square}) until it rises, EraseRules.WARN on.</li>
 * <li>As it breaks free ({@link #breaksFree}): a ring along the ground, a flash of its edges, pieces of the ground flung.</li>
 * <li>The cube ({@link #cube}, the blocks of that ground drawn as they are, rising with EraseRules.lift; where there is no
 * block, the nearest of its column's): glitching as one piece ({@link #tension}: a little as it rises, more and more
 * toward its burst): slices of it slipping aside ({@link #slice}), the whole of it shaking, for a frame now and then its
 * blocks gone to a dark void ({@link #flick}), its edges glowing purple and splitting red and cyan, bands of light running
 * up its sides, pixels of data flickering on it and, at the last, peeling off it.</li>
 * <li>Its burst ({@link #burst}): its blocks flung out in pieces ({@link #fragments}), pixels everywhere, purple spikes,
 * a white flash, rings in the air and along the ground.</li>
 * <li>Where it was ({@link #voidPlane}): a dark void on the ground edged in purple with a grid and flickering static,
 * from as it clears the ground until EraseRules.BACK, when a line of light sweeps over it and the ground is back.</li>
 * </ul>
 */
final class EraseFx {
    private EraseFx(){}
    static final int INK=GlitchFx.INK,DEEP=GlitchFx.DEEP,PURPLE=GlitchFx.PURPLE,GLOW=GlitchFx.GLOW,LILAC=GlitchFx.LILAC,WHITE=GlitchFx.WHITE,RED=GlitchFx.RED,CYAN=GlitchFx.CYAN,PINK=GlitchFx.PINK;
    static final int NEON=0xc45cff;
    /** The void where there is no ground: its purple edge and its dark. */
    static final int VOID_EDGE=0x34106a,VOID=0x0b0418;
    private static double h(int j,int seed,int salt){return GlitchFx.h(j,0,seed,salt);}
    /** Its moments (seconds after the mark): the cubes rise, they burst, the ground is back. */
    static final float RISE0=EraseRules.RISE_FROM/20f,BURST_S=EraseRules.BURST/20f,BACK_S=EraseRules.BACK/20f;
    /** How long it is drawn after the ground is back, and after it was cut short (seconds). */
    static final float AFTER=.6f,CUT_LIFE=.6f;
    /** Cut short: how fast what is left of it goes (ticks). */
    static final float CUT_FADE=5;
    /** How much of call {@code c} is left {@code age} ticks after the mark (cut short, it goes over CUT_FADE). */
    static float kept(EraseClient.Call c,float age){return c.cut==Float.MAX_VALUE?1:1-Ease.smooth(age,c.cut,c.cut+CUT_FADE);}

    // ───────────────────────────── the "!!!" ─────────────────────────────

    /**
     * The giant "!!!" over the ground looked at, this many times G's (the user's "거대한 !!!": some five blocks across, half
     * its ground), and how high over its ground's top its middle is (its foot four blocks up); a "!!!" over the others: how
     * high over its middle, or over the head of whom it is under (blocks).
     */
    static final double GIANT=6,GIANT_OVER=6,BANG_OVER=2.6,OVER_HEAD=.9;
    /** Where ground {@code g}'s "!!!" shows: over the head of whom it is under where they stood as it was marked (the server's word); else over its middle, the giant one higher. */
    static Vec3d bangOver(EraseClient.Ground g){
        if(g.head!=null)return g.head.add(0,OVER_HEAD,0);
        return g.middle().add(0,g.looked()?GIANT_OVER:BANG_OVER,0);
    }
    /** How much the "!!!" and the marked ground show {@code age} ticks after the mark: from it until the ground rises, gone over the few ticks round that. */
    static float marked(float age){return age<0?0:1-Ease.smooth(age,EraseRules.WARN-1,EraseRules.WARN+3);}
    /** The "!!!" over each ground and its ten by ten marked on the ground, {@code age} ticks after the mark. */
    static void marks(Fx c,EraseClient.Call call,EffectMesh m,boolean light,float age){
        float shown=marked(age)*kept(call,age);if(shown<=0)return;
        float sec=age/20f;
        for(var g:call.grounds){
            square(m,g.x0,g.z0,g.top,sec,shown*Ease.smooth(age,0,1.5f),light,c.camera,g.looked());
            SwordDance.bangs(m,c.camera,g.bang,age,shown,light,g.looked()?GIANT:1);
        }
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
    /** How long the ground breaking free flashes (seconds after it begins to rise). */
    static final float FREE=.7f;
    /** Ground {@code g} breaking free {@code sec} seconds after it begins to rise: a ring along the ground, a flash of its edges, pixels thrown up off it. */
    static void breaksFree(Fx c,EraseClient.Ground g,EffectMesh m,boolean light,float sec){
        if(sec<0||sec>=FREE)return;
        Vec3d at=g.middle();float near=GlitchFx.near(at.add(0,1,0),c.camera);
        if(light&&near>0)m.softDisc(at.add(0,.8,0),4.5,NEON,.3f*Ease.smooth(sec,0,.02f)*(1-Ease.smooth(sec,.08f,.6f))*near);
        float r=sec/.55f;
        if(r<1){double rad=6+7*Ease.out(sec,0,.55f);
            if(light)FxLib.ring(m,at.add(0,.08,0),rad,.7,NEON,.45f*(1-r));else FxLib.ring(m,at.add(0,.08,0),rad,.25,PURPLE,.8f*(1-r));}
        // its edges flash as it lets go of the ground
        square(m,g.x0,g.z0,g.top,sec,1-Ease.smooth(sec,.1f,.5f),light,c.camera,false);
        for(int k=0;k<6;k++){
            Vec3d p=new Vec3d(g.x0+1.5+7*h(k,g.seed,11),g.top+.2,g.z0+1.5+7*h(k,g.seed,12));
            GlitchFx.pixels(m,p,new Vec3d(0,1,0),.6,7,.08,10,g.seed+k*31,sec-.02f*k,light,c.camera);
        }
    }

    // ───────────────────────────── the cube ─────────────────────────────

    /** Its cube's faces as this client found them: each a face of one of its blocks on its outside, the block (null: none, a void), and which way. */
    static final class Faces {
        final int n,base;final byte[] dir,x,y,z;final BlockState[] state;
        Faces(int n,int base){this.n=n;this.base=base;dir=new byte[n];x=new byte[n];y=new byte[n];z=new byte[n];state=new BlockState[n];}
    }
    static final Direction[] DIRS={Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    /** The world y of the lowest blocks of ground {@code s}'s cube: its ground's top block and the nine under it. */
    static int base(EraseClient.Ground s){return (int)Math.ceil(s.top-1e-6)-EraseRules.SIDE;}
    /** The faces of ground {@code s}'s cube, found from {@code world} as it is (its outside only: what shows). */
    static Faces faces(EraseClient.Ground s,World world){
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
     * How hard it glitches {@code tr} ticks after the mark (0 to 1): a little as it rises out of the ground, a trace
     * as it waits, more and more over its last five seconds.
     */
    static float tension(float tr){
        float sec=tr/20f;
        float rising=.18f*Ease.smooth(sec,RISE0,RISE0+.3f)*(1-Ease.smooth(sec,RISE0+5.5f,RISE0+6.5f));
        float ramp=.06f+.94f*Ease.in(sec,BURST_S-5f,BURST_S);
        return Math.max(rising,ramp);
    }
    /** How far layer {@code layer} of it (from its bottom) slips aside {@code sec} seconds after the mark: in steps of a twelfth of a second, now and then a few layers together. */
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
    /** Where its least corner is drawn {@code tr} ticks after the mark (null: there is none now). */
    static Vec3d corner(EraseClient.Ground s,float tr,Vec3d jit){
        double lift=EraseRules.lift(tr);if(Double.isNaN(lift)||tr<EraseRules.RISE_FROM)return null;
        return new Vec3d(s.x0+jit.x,base(s)+lift+.003+jit.y,s.z0+jit.z);
    }
    /** Whether the cubes of call {@code c} are drawn now: risen and not burst, not cut short. */
    static boolean showsCube(EraseClient.Call c,float age,float tr){return tr>=EraseRules.RISE_FROM&&tr<EraseRules.BURST&&age<c.cut;}
    /** Its blocks (the solids): each face on its outside drawn as its block shows it there, its layers slipping as it glitches. */
    static void cube(FxSolids so,EraseClient.Ground s,float tr){
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
    static void cubeFx(Fx c,EraseClient.Ground s,EffectMesh m,boolean light,float tr){
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
    static void fragments(FxSolids so,EraseClient.Ground s,float tr){
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
    static void burst(Fx c,EraseClient.Ground s,EffectMesh m,boolean light,float t){
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
    /** How far over the void the sweep has come at {@code sec} seconds after the mark (0 to 1; behind it the ground is back). */
    static float swept(float sec){return (float)Math.max(0,Math.min(1,(sec-(BACK_S-SWEEP-.05f))/SWEEP));}
    /** Whether the void shows {@code sec} seconds after the mark: from as the cube clears the ground (its gap shows it) to the ground back, flickering before the sweep. */
    static boolean voidShows(float sec,float tr){
        if(sec<RISE0||sec>=BACK_S)return false;
        if(sec<BURST_S){double lift=EraseRules.lift(tr);if(!(lift>.06))return false;}
        if(sec>=BACK_S-RESTORE&&sec<BACK_S-SWEEP-.05f){int step=(int)Math.floor(sec*30);return step%3!=0;}
        return true;
    }
    /** Where it was ({@code tr} ticks after the mark): a dark void on the ground, edged in purple, a grid, flickering static, a band of light going over it, and at the last the sweep that draws the ground back. */
    static void voidPlane(Fx c,EraseClient.Ground s,EffectMesh m,boolean light,float tr){
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

    /** Whom ground {@code g}'s burst struck, as this client heard of it: a white star and a burst of pixels on them. */
    static void blasted(Fx c,EraseClient.Ground w,EffectMesh m,boolean light){
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

    // ───────────────────────────── the passes ─────────────────────────────

    private static EraseClient.Call of(Fx c){
        var call=EraseClient.of(c.cast);if(call==null)return null;
        if(c.age>=EraseRules.RISE_FROM-2)for(var g:call.grounds)if(g.faces==null)g.faces=faces(g,c.mc.world);
        return call;
    }
    /** Ticks since the mark, for the cubes: a tick behind the call's clock (as the bodies on them are drawn between their last place and this tick's). */
    static float cubeTicks(float age){return age-1;}
    /** Everything but the cubes' blocks, in one pass: the "!!!", each ground breaking free, the cubes' glitches, their bursts, where they were, whom they struck. */
    static void zone(Fx c,EffectMesh m,boolean light){
        var call=of(c);if(call==null)return;
        float age=c.age,tr=cubeTicks(age),fade=kept(call,age);
        boolean cut=age>=call.cut;
        marks(c,call,m,light,age);
        for(var g:call.grounds){
            if(!cut)breaksFree(c,g,m,light,(age-EraseRules.RISE_FROM)/20f);
            if(showsCube(call,age,tr))cubeFx(c,g,m,light,tr);
            if(!cut)burst(c,g,m,light,(tr-EraseRules.BURST)/20f);
            if(!cut)voidPlane(c,g,m,light,tr);
            // cut short: the cube glitches out where it was
            if(cut&&fade>0&&g.faces!=null){
                float t=(age-call.cut)/20f;double S=EraseRules.SIDE,lift=EraseRules.lift(cubeTicks(call.cut));
                if(!Double.isNaN(lift)&&cubeTicks(call.cut)>=EraseRules.RISE_FROM){
                    Vec3d mid=new Vec3d(g.x0+S*.5,base(g)+lift+S*.5,g.z0+S*.5);
                    for(int q=0;q<6;q++)GlitchFx.pixels(m,mid.add((h(q,g.seed,251)-.5)*S,(h(q,g.seed,252)-.5)*S,(h(q,g.seed,253)-.5)*S),new Vec3d(0,1,0),1,6,.1,14,g.seed+q*19,t,light,c.camera);
                }
            }
            blasted(c,g,m,light);
        }
    }
    /** The solids: pieces of each ground flung as it breaks free, the cubes' blocks, their bursts' pieces. */
    static void solids(Fx c,FxSolids so){
        var call=of(c);if(call==null)return;
        float age=c.age,sec=(age-EraseRules.RISE_FROM)/20f,tr=cubeTicks(age);
        for(var g:call.grounds){
            // pieces of the ground flung from its edges as it breaks free
            if(sec>=0&&sec<1.4f&&age<call.cut){
                BlockState st=so.ground(g.middle().add(0,.2,0));
                if(st!=null)for(int k=0;k<10;k++){
                    double an=Math.PI*2*(k+.5*h(k,g.seed,261))/10,out=2+3*h(k,g.seed,262);
                    Vec3d edge=g.middle().add(Math.cos(an)*EraseRules.SIDE*.5,.25,Math.sin(an)*EraseRules.SIDE*.5);
                    Vec3d v0=new Vec3d(Math.cos(an)*out,5+4*h(k,g.seed,263),Math.sin(an)*out);
                    Vec3d p=FxSolids.flight(edge,v0,g.top+.15,sec);
                    double size=(.22+.2*h(k,g.seed,264))*(1-Ease.smooth(sec,1f,1.4f));
                    float spin=(float)((260+380*h(k,g.seed,265))*Math.min(sec,1f));
                    so.chunk(st,p,size,spin,spin*.7f,spin*.4f);
                }
            }
            if(showsCube(call,age,tr))cube(so,g,tr);
            if(age<call.cut)fragments(so,g,tr);
        }
    }
    /** Its light on the world: the marked grounds pulsing, each breaking free, the cubes' glow (stronger as they near their burst), their bursts, the voids'. */
    static void frame(Fx c,FxFrame fr){
        var call=EraseClient.of(c.cast);if(call==null)return;
        float age=c.age,tr=cubeTicks(age),sec=(age-EraseRules.RISE_FROM)/20f;
        if(age>=call.cut)return;
        double S=EraseRules.SIDE;float mark=marked(age);
        for(var g:call.grounds){
            if(mark>0)fr.light(g.middle().add(0,1.5,0),0xff3050,g.looked()?12:8,.7f*mark*(float)(.7+.3*Math.sin(age*Math.PI/2)));
            if(sec>=0&&sec<.7f)fr.light(g.middle().add(0,1,0),0xb04cff,16,1.8f*(1-sec/.7f));
            if(showsCube(call,age,tr)){
                double lift=EraseRules.lift(tr);float tension=tension(tr);
                fr.light(new Vec3d(g.x0+S*.5,base(g)+lift+S*.5,g.z0+S*.5),0x8a2be2,16,(.5f+1.1f*tension)*(float)(.8+.2*Math.sin(tr/20f*(5+16*tension))));
            }
            float t=(tr-EraseRules.BURST)/20f;
            if(t>=0&&t<1f)fr.light(new Vec3d(g.x0+S*.5,base(g)+EraseRules.LIFT+S*.5,g.z0+S*.5),0xc45cff,32,3f*(1-t));
            if(voidShows(tr/20f,tr))fr.light(new Vec3d(g.x0+S*.5,g.top+.5,g.z0+S*.5),0x6a0cb8,9,.45f);
        }
    }
    /** How hard it shakes the camera (KagenoCamera weighs it by how near its nearest ground is): as its cubes break free and a rumble as they rise, hard as they burst. */
    static float shake(EraseClient.Call c,float sec){
        float age=sec*20,v=0;
        if(age>=c.cut)return 0;
        v+=.4f*KnifeCombo.bump(sec,RISE0,.1f);
        if(sec>=RISE0&&sec<RISE0+EraseRules.RISE_TICKS/20f)v+=.12f;
        v+=2.0f*KnifeCombo.bump(sec,BURST_S+.05f,.16f);
        v+=.2f*KnifeCombo.bump(sec,BACK_S,.1f);
        return v;
    }
}
