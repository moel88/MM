package dev.sangwoo.botandtown;

import dev.sangwoo.celestialfall.town.TownBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * The rift at the island's waist (map v3.0, the user's design: '시공간이 이상한 흰색 구역', '세계수 하나, 네온으로 빛나는
 * 하늘색 잎사귀', '장식으로 둬'). No square and no paving: white ground broken by cracks of sky-blue light running out
 * from the world tree in its middle. The tree is for looking at, not for climbing: a silver trunk threaded with veins of
 * light on roots arching out of the ground, five limbs spreading up into a crown of neon sky-blue leaves some seventy
 * blocks across that lights the rift by night, strands of leaves hanging under it. Pieces of the ground float in the air
 * round it, some upside down with their moss and roots hanging under them, and blocks of white hover a little over the
 * ground. Players appear on the white south of the tree looking at it, the two merchants' stalls between them and it.
 * The roads of both glasses stop where the white begins.
 */
final class Rift {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int Y=Plan.BASE,TX=Plan.TREE_X,TZ=Plan.TREE_Z;
    static final BlockState BARK=TB.of("world_tree_bark"),GLOW=TB.of("world_tree_bark_glow"),LEAVES=TB.of("world_tree_leaves"),
        GROUND=TB.of("rift_ground"),CRACK=TB.of("rift_crack");
    /** The trunk's height above the ground before it parts into its limbs. */
    static final int TRUNK_H=34;
    int leaves,bark,cracks;
    Rift(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        trunk();
        roots();
        crown();
        cracks();
        fragments();
        hovering();
        stall(-10,true);stall(7,false);
        t.places.put("rift",new int[]{Plan.SPAWN_X,Y+1,Plan.SPAWN_Z});
        t.places.put("tree",new int[]{TX+16,Y+1,TZ+4});
        Town.log("rift: tree of "+bark+" bark and "+leaves+" leaves, "+cracks+" crack blocks, "+c.placed);
    }

    /** The trunk's radius h blocks above the ground (flaring at its foot), and its middle's gentle wander. */
    static double trunkR(double h){return 4.3+3.1*Math.exp(-Math.max(0,h)/4.5);}
    static double driftX(double h){return TX+0.5+1.3*Math.sin(h/15.0);}
    static double driftZ(double h){return TZ+0.5+0.9*Math.sin(h/12.0+1.0);}

    /** Whether the tree may grow through a block: air, plants, the ground's top at the foot. */
    private boolean free(int x,int y,int z){
        BlockState s=c.get(x,y,z);
        return s.isAir()||s.getBlock() instanceof net.minecraft.block.PlantBlock||s.isOf(LEAVES.getBlock())||y<=Y&&!s.isOf(BARK.getBlock())&&!s.isOf(GLOW.getBlock());
    }
    private void wood(int x,int y,int z,Direction.Axis a,boolean glow){
        if(!free(x,y,z))return;
        c.set(x,y,z,(glow?GLOW:BARK).with(Properties.AXIS,a));bark++;
        if(y>c.ground(x,z)&&y<=Y+8)c.ground(x,z,y);
    }

    /**
     * The trunk: round with buttress ridges that twist slowly up it, flaring at the foot (its base sunk two blocks into
     * the ground), three veins of light spiralling up its face and a few flecks of light between them.
     */
    private void trunk(){
        for(int h=-2;h<=TRUNK_H+2;h++){
            double r0=trunkR(h)*(h>TRUNK_H-4?1-0.08*(h-TRUNK_H+4):1),mx=driftX(h),mz=driftZ(h);
            int R=(int)Math.ceil(r0*1.2)+1;
            for(int x=(int)Math.floor(mx)-R;x<=mx+R;x++)for(int z=(int)Math.floor(mz)-R;z<=mz+R;z++){
                double px=x+0.5-mx,pz=z+0.5-mz,d=Math.hypot(px,pz),a=Math.atan2(pz,px);
                double rr=r0*(1+0.10*Math.sin(5*a+h*0.09)+0.05*Math.sin(11*a-h*0.13));
                if(d>rr)continue;
                boolean face=d>rr-1.4;
                boolean vein=face&&(Math.sin(3*a+h*0.11)>0.985||Pal.hash(x,h,z)<0.015);
                wood(x,Y+h,z,Direction.Axis.Y,vein);
            }
        }
    }

    /** A limb along a curve from a through ctrl to b, tapering from r0 to r1, its bark here and there veined with light. */
    private void limb(double[] a,double[] ctrl,double[] b,double r0,double r1){
        double len=dist(a,ctrl)+dist(ctrl,b);int n=Math.max(2,(int)(len*3));
        for(int k=0;k<=n;k++){
            double s=k/(double)n,u=1-s;
            double x=u*u*a[0]+2*u*s*ctrl[0]+s*s*b[0],y=u*u*a[1]+2*u*s*ctrl[1]+s*s*b[1],z=u*u*a[2]+2*u*s*ctrl[2]+s*s*b[2];
            double tx=u*(ctrl[0]-a[0])+s*(b[0]-ctrl[0]),ty=u*(ctrl[1]-a[1])+s*(b[1]-ctrl[1]),tz=u*(ctrl[2]-a[2])+s*(b[2]-ctrl[2]);
            Direction.Axis ax=Math.abs(ty)>=Math.max(Math.abs(tx),Math.abs(tz))?Direction.Axis.Y:Math.abs(tx)>=Math.abs(tz)?Direction.Axis.X:Direction.Axis.Z;
            ball(x,y,z,r0+(r1-r0)*s,ax);
        }
    }
    private void ball(double x,double y,double z,double r,Direction.Axis ax){
        if(r<0.8){int bx=(int)Math.floor(x),by=(int)Math.floor(y),bz=(int)Math.floor(z);wood(bx,by,bz,ax,Pal.hash(bx,by,bz)<0.05);return;}
        int R=(int)Math.ceil(r);
        for(int dx=-R;dx<=R;dx++)for(int dy=-R;dy<=R;dy++)for(int dz=-R;dz<=R;dz++){
            int bx=(int)Math.floor(x)+dx,by=(int)Math.floor(y)+dy,bz=(int)Math.floor(z)+dz;
            double ex=bx+0.5-x,ey=by+0.5-y,ez=bz+0.5-z;
            if(ex*ex+ey*ey+ez*ez>r*r)continue;
            wood(bx,by,bz,ax,Pal.hash(bx,by,bz)<0.05);
        }
    }
    private static double dist(double[] a,double[] b){return Math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1])+(a[2]-b[2])*(a[2]-b[2]));}

    /**
     * Roots arching out of the ground round the trunk's foot and back into it: thick at the trunk, a block or two
     * over the white, wandering a little; low enough to step over at their ends, cover nearer the trunk.
     */
    private void roots(){
        int n=9;
        for(int i=0;i<n;i++){
            double a=i*2*Math.PI/n+(rnd.nextDouble()-0.5)*0.35,len=11+rnd.nextDouble()*10,wig=rnd.nextDouble()*6;
            int steps=(int)(len*3);
            for(int k=0;k<=steps;k++){
                double s=k/(double)steps,ang=a+0.22*Math.sin(s*4+wig);
                double d=3.6+len*s,x=TX+0.5+Math.cos(ang)*d,z=TZ+0.5+Math.sin(ang)*d;
                double y=Y+0.8+3.2*(1-s)*(1-s)+1.4*Math.sin(Math.PI*s)*(1-s)-1.6*s;
                double r=2.1*(1-s)+0.75;
                Direction.Axis ax=Math.abs(Math.cos(ang))>Math.abs(Math.sin(ang))?Direction.Axis.X:Direction.Axis.Z;
                int R=(int)Math.ceil(r);
                for(int dx=-R;dx<=R;dx++)for(int dy=-R;dy<=R;dy++)for(int dz=-R;dz<=R;dz++){
                    int bx=(int)Math.floor(x)+dx,by=(int)Math.floor(y)+dy,bz=(int)Math.floor(z)+dz;
                    double ex=bx+0.5-x,ey=by+0.5-y,ez=bz+0.5-z;
                    if(ex*ex+ey*ey+ez*ez>r*r||by<Y)continue;
                    wood(bx,by,bz,ax,Pal.hash(bx,by,bz)<0.04);
                }
            }
        }
    }

    /**
     * The limbs and the crown: five limbs leave the trunk's top and climb outward, each parting into two or three
     * branches; clumps of the glowing leaves gather round the branches' ends and fill out a dome some seventy blocks
     * across, from forty to seventy blocks up. Only a shell two blocks deep is leaves (the cards show through to the
     * shell behind, so the crown looks as full), and strands of leaves hang under it.
     */
    private void crown(){
        List<double[]> clumps=new ArrayList<>();
        double topX=driftX(TRUNK_H),topZ=driftZ(TRUNK_H);
        int limbs=5;
        for(int i=0;i<limbs;i++){
            double a=i*2*Math.PI/limbs+0.5+(rnd.nextDouble()-0.5)*0.4,L=22+rnd.nextDouble()*7;
            double[] from={topX+Math.cos(a)*1.5,Y+TRUNK_H-3-(i%2)*4,topZ+Math.sin(a)*1.5};
            double[] ctrl={topX+Math.cos(a)*L*0.35,Y+TRUNK_H+9,topZ+Math.sin(a)*L*0.35};
            double[] to={topX+Math.cos(a)*L,Y+48+rnd.nextDouble()*6,topZ+Math.sin(a)*L};
            limb(from,ctrl,to,2.9,1.3);
            clumps.add(new double[]{to[0],to[1]+3,to[2],9,5.5});
            for(int j=0;j<3;j++){
                if(j==2&&rnd.nextBoolean())continue;
                double s=0.5+0.15*j+rnd.nextDouble()*0.1,u=1-s;
                double[] p={u*u*from[0]+2*u*s*ctrl[0]+s*s*to[0],u*u*from[1]+2*u*s*ctrl[1]+s*s*to[1],u*u*from[2]+2*u*s*ctrl[2]+s*s*to[2]};
                double b=a+(j%2==0?1:-1)*(0.45+rnd.nextDouble()*0.25),bl=L*0.55+rnd.nextDouble()*8,bd=Math.hypot(p[0]-TX,p[2]-TZ)+bl;
                bd=Math.min(bd,36);
                double[] end={TX+Math.cos(b)*bd,Y+54+rnd.nextDouble()*7,TZ+Math.sin(b)*bd};
                double[] mid={(p[0]+end[0])/2,Math.max(p[1],end[1])+2,(p[2]+end[2])/2};
                limb(p,mid,end,1.5,0.6);
                clumps.add(new double[]{end[0],end[1]+2,end[2],7.5,5});
            }
        }
        // the dome between and over the branches
        for(int k=0;k<46;k++){
            double a=rnd.nextDouble()*2*Math.PI,q=Math.sqrt(rnd.nextDouble());
            double d=q*33,h=Y+50+16*Math.sqrt(Math.max(0,1-q*q))*0.95+rnd.nextDouble()*3;
            clumps.add(new double[]{TX+Math.cos(a)*d,h,TZ+Math.sin(a)*d,6+rnd.nextDouble()*3,4+rnd.nextDouble()*1.5});
        }
        // the crown as a solid, then only its outer two blocks kept
        int R=48,y0=Y+36,y1=Y+78,W=2*R+1,H=y1-y0+1;
        boolean[] in=new boolean[W*W*H];
        for(double[] k:clumps){
            for(int x=(int)Math.floor(k[0]-k[3]);x<=Math.ceil(k[0]+k[3]);x++)for(int y=(int)Math.floor(k[1]-k[4]);y<=Math.ceil(k[1]+k[4]);y++)
                for(int z=(int)Math.floor(k[2]-k[3]);z<=Math.ceil(k[2]+k[3]);z++){
                    if(Math.abs(x-TX)>R||Math.abs(z-TZ)>R||y<y0||y>y1)continue;
                    double dx=(x+0.5-k[0])/k[3],dy=(y+0.5-k[1])/k[4],dz=(z+0.5-k[2])/k[3],d=dx*dx+dy*dy+dz*dz;
                    if(d>1||d>0.7&&Pal.hash(x,y,z)<0.4)continue;
                    in[((x-TX+R)*W+(z-TZ+R))*H+(y-y0)]=true;
                }
        }
        int[] depth=new int[in.length];Arrays.fill(depth,99);
        ArrayDeque<Integer> q=new ArrayDeque<>();
        for(int i=0;i<in.length;i++)if(!in[i]){depth[i]=0;q.add(i);}
        int[][] nb={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while(!q.isEmpty()){
            int i=q.poll(),y=i%H,z=(i/H)%W,x=i/(H*W);
            if(depth[i]>=2)continue;
            for(int[] o:nb){
                int xx=x+o[0],yy=y+o[1],zz=z+o[2];
                if(xx<0||yy<0||zz<0||xx>=W||yy>=H||zz>=W)continue;
                int j=(xx*W+zz)*H+yy;
                if(depth[j]>depth[i]+1){depth[j]=depth[i]+1;q.add(j);}
            }
        }
        for(int i=0;i<in.length;i++){
            if(!in[i]||depth[i]>2)continue;
            int y=i%H+y0,z=(i/H)%W+TZ-R,x=i/(H*W)+TX-R;
            if(c.air(x,y,z)){c.set(x,y,z,LEAVES);leaves++;}
        }
        // strands hanging under the crown
        for(int k=0;k<90;k++){
            double a=rnd.nextDouble()*2*Math.PI,d=8+Math.sqrt(rnd.nextDouble())*28;
            int x=(int)Math.floor(TX+Math.cos(a)*d),z=(int)Math.floor(TZ+Math.sin(a)*d);
            int low=-1;
            for(int y=y0;y<=y1;y++)if(c.get(x,y,z).isOf(LEAVES.getBlock())){low=y;break;}
            if(low<0)continue;
            int len=2+rnd.nextInt(7);
            for(int y=low-1;y>=low-len&&y>Y+12;y--){if(!c.air(x,y,z))break;c.set(x,y,z,LEAVES);leaves++;}
        }
    }

    /**
     * Cracks of light in the white: fourteen run out from the roots' feet to near the rift's edge, wandering and
     * forking, square-stepped so each block's seam joins its neighbours' (Botand's crack block).
     */
    private void cracks(){
        boolean[] on=new boolean[Plan.W*Plan.D];
        int n=14;
        for(int i=0;i<n;i++){
            double a=i*2*Math.PI/n+(rnd.nextDouble()-0.5)*0.3;
            walk(on,TX+Math.cos(a)*9,TZ+Math.sin(a)*9,a,70,true);
        }
        for(int x=Plan.X0;x<Plan.X0+Plan.W;x++)for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++){
            int i=Canvas.idx(x,z);
            if(!on[i])continue;
            int g=c.ground(x,z);
            if(g!=Y||!c.air(x,g+1,z)||!(c.get(x,g,z).isOf(GROUND.getBlock())||c.get(x,g,z).isOf(Blocks.CALCITE)))continue;
            BlockState s=CRACK;
            for(var d:Direction.Type.HORIZONTAL){
                int nx=x+d.getOffsetX(),nz=z+d.getOffsetZ();
                s=s.with(TownBlocks.Crack.side(d),Canvas.inPlan(nx,nz)&&on[Canvas.idx(nx,nz)]&&c.ground(nx,nz)==Y&&c.air(nx,Y+1,nz));
            }
            c.set(x,g,z,s);cracks++;
        }
    }
    /** One crack: a wandering walk outward, its cells joined square (a diagonal step goes through a side cell), forking now and then. */
    private void walk(boolean[] on,double x,double z,double a,int steps,boolean fork){
        int px=(int)Math.floor(x),pz=(int)Math.floor(z);
        for(int k=0;k<steps;k++){
            a+=rnd.nextGaussian()*0.28;
            x+=Math.cos(a);z+=Math.sin(a);
            int nx=(int)Math.floor(x),nz=(int)Math.floor(z);
            if(!Plan.inRift(nx,nz)||Math.hypot(nx,nz)>Plan.riftEdge(nx,nz)-2)return;
            if(nx!=px&&nz!=pz)mark(on,nx,pz);
            mark(on,nx,nz);px=nx;pz=nz;
            if(fork&&k>4&&rnd.nextDouble()<0.07)walk(on,x,z,a+(rnd.nextBoolean()?1:-1)*(0.6+rnd.nextDouble()*0.4),6+rnd.nextInt(10),false);
        }
    }
    private static void mark(boolean[] on,int x,int z){if(Canvas.inPlan(x,z))on[Canvas.idx(x,z)]=true;}

    /**
     * Pieces of the ground hanging in the air round the tree: a flat face of white (or grass) on a rough cone of white
     * rock; one in four turned upside down, its face of moss underneath with roots and glowing berries hanging from it.
     */
    private void fragments(){
        int made=0;
        for(int k=0;k<40&&made<15;k++){
            double a=rnd.nextDouble()*2*Math.PI,r=24+rnd.nextDouble()*40;
            double fx=TX+Math.cos(a)*r,fz=TZ+Math.sin(a)*r;
            int fy=r<40?Y+14+rnd.nextInt(14):Y+16+rnd.nextInt(30);
            int size=2+rnd.nextInt(5);
            if(!clearAround(fx,fy,fz,size+4))continue;
            fragment(fx,fy,fz,size,made%4==1);made++;
        }
        Town.log("rift: "+made+" floating fragments");
    }
    private boolean clearAround(double fx,int fy,double fz,int r){
        for(int x=(int)fx-r;x<=fx+r;x+=2)for(int z=(int)fz-r;z<=fz+r;z+=2)for(int y=fy-r-4;y<=fy+r;y+=2)if(!c.air(x,y,z))return false;
        return true;
    }
    private void fragment(double fx,int fy,double fz,int size,boolean flipped){
        int depth=(int)Math.round(size*1.5)+2;
        boolean grassy=rnd.nextInt(3)==0;
        for(int x=(int)Math.floor(fx)-size-2;x<=fx+size+2;x++)for(int z=(int)Math.floor(fz)-size-2;z<=fz+size+2;z++){
            double d=Math.hypot(x+0.5-fx,z+0.5-fz)/(size*(0.8+0.4*Pal.smooth(x,z,3,51)));
            if(d>1)continue;
            int deep=(int)Math.round(depth*Math.pow(1-d*d,0.8)*(0.7+0.6*Pal.hash(x,3,z)));
            for(int k=0;k<=deep;k++){
                int y=flipped?fy+k:fy-k;
                BlockState s;
                if(k==0)s=flipped?Blocks.MOSS_BLOCK.getDefaultState():grassy?Blocks.GRASS_BLOCK.getDefaultState():GROUND;
                else if(k==1&&(flipped||grassy))s=Blocks.DIRT.getDefaultState();
                else s=t.terrain.white(x,y,z,Pal.hash(x,y,z)<0.6);
                c.set(x,y,z,s);
            }
            if(flipped){
                double h=Pal.hash(x,9,z);
                if(h<0.22)c.set(x,fy-1,z,Blocks.HANGING_ROOTS.getDefaultState());
                else if(h<0.32){
                    int len=1+(int)(Pal.hash(x,11,z)*3);
                    for(int j=1;j<=len;j++)c.set(x,fy-j,z,(j==len?Blocks.CAVE_VINES:Blocks.CAVE_VINES_PLANT).getDefaultState().with(Properties.BERRIES,Pal.hash(x,j,z)<0.6));
                }
            }else if(grassy&&Pal.hash(x,13,z)<0.3)c.set(x,fy+1,z,Blocks.GRASS.getDefaultState());
        }
    }

    /** Blocks of the white hanging still a little over the ground in loose clusters near the rift's edge. */
    private void hovering(){
        int made=0;
        for(int k=0;k<30&&made<8;k++){
            double a=rnd.nextDouble()*2*Math.PI,r=26+rnd.nextDouble()*20;
            int cx=(int)Math.floor(TX+Math.cos(a)*r),cz=(int)Math.floor(TZ+Math.sin(a)*r);
            if(Math.hypot(cx-Plan.SPAWN_X,cz-Plan.SPAWN_Z)<12||!Plan.inRift(cx,cz)||Math.abs(cx)<8&&cz>0)continue;
            for(int j=0;j<4+rnd.nextInt(4);j++){
                int x=cx+rnd.nextInt(7)-3,z=cz+rnd.nextInt(7)-3,y=Y+2+rnd.nextInt(4);
                if(c.air(x,y,z)&&c.air(x,y-1,z)&&c.air(x,y+1,z))c.set(x,y,z,rnd.nextInt(4)==0?Blocks.CALCITE.getDefaultState():GROUND);
            }
            made++;
        }
    }

    /**
     * A merchant's stall on the white between the spawn and the tree, its counter toward the players coming from the
     * spawn (south): white posts, a counter of calcite, an awning striped sky blue and white, a lantern; the merchant
     * stands behind the counter facing south.
     */
    private void stall(int x0,boolean seller){
        int z0=Plan.MERCHANT_Z,y=Y;
        for(int x=x0-1;x<=x0+4;x++)for(int z=z0-2;z<=z0+2;z++){c.clear(x,y+1,z,x,y+5,z);if(c.get(x,y,z).isOf(CRACK.getBlock()))c.set(x,y,z,GROUND);}
        for(int x=x0;x<=x0+3;x++){
            c.set(x,y+1,z0+1,x==x0+1?Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,true):Blocks.CALCITE.getDefaultState());
            for(int z=z0-1;z<=z0+1;z++)c.set(x,y+4,z,((x+z)&1)==0?Blocks.LIGHT_BLUE_WOOL.getDefaultState():Blocks.WHITE_WOOL.getDefaultState());
        }
        for(int x:new int[]{x0,x0+3})for(int z:new int[]{z0-1,z0+1})for(int yy=y+1;yy<=y+3;yy++)if(!(z==z0+1&&yy==y+1))c.set(x,yy,z,Blocks.QUARTZ_PILLAR.getDefaultState());
        c.hangingLantern(x0+2,y+3,z0,false);
        c.wallSign(x0+1,y+4,z0+2,Blocks.BIRCH_WALL_SIGN,Direction.SOUTH,DyeColor.BLUE,true,"",seller?"아이템 판매":"아이템 매입",seller?"로카":"카카");
        t.merchants.add(new int[]{x0+1,y+1,z0,0});
    }
}
