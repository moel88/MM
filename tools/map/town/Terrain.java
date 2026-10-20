package dev.sangwoo.botandtown;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import java.util.Random;

/**
 * The ground: a hill rising north behind the town (gentle where the terraces are, steep at the ridge), headlands on
 * both sides, the river valley in the east, and the sea floor shelving off the coast. Flat platforms are cut into it
 * for the town (the coastal flat, four terraces, the shrine, the harbour quay and the battle ground): where a platform
 * is cut into higher ground the step is a stone retaining wall, where it stands above lower ground the fill slopes
 * away as a grassy bank.
 */
final class Terrain {
    static final byte SEA=1,LAND=2,RIVER=3,BEACH=4;
    private final Canvas c;private final Random rnd;
    final byte[] kind=new byte[Plan.W*Plan.D];
    /** Platform height of a column, or MIN_VALUE where the ground is natural. */
    final int[] platform=new int[Plan.W*Plan.D];
    private final double[] ph=new double[8];
    Terrain(Canvas c,long seed){this.c=c;rnd=new Random(seed);for(int i=0;i<ph.length;i++)ph[i]=rnd.nextDouble()*Math.PI*2;}

    double noise(double x,double z){
        return (Math.sin(x/37+ph[0])*Math.cos(z/41+ph[1])+0.6*Math.sin(x/17.3+z/23.1+ph[2])+0.35*Math.cos(x/9.7-z/11.3+ph[3])+0.2*Math.sin(x/5.1+ph[4])*Math.sin(z/4.7+ph[5]))/2.15;
    }
    /** The west headland's coastline (land at z up to this). */
    static double westCoast(int x){return 150-(x+240)*1.2;}
    static double eastCoast(int x){return 60+(x-196)*1.2;}

    boolean seaAt(int x,int z){
        if(Math.abs(x-Plan.riverX(z))<=Plan.riverHalf(z))return true;
        if(x<=-205)return z>westCoast(x);
        if(x>=196)return z>eastCoast(x);
        return z>=Plan.SEAWARD_Z;
    }
    int natural(int x,int z){
        int t=-z;
        double h=t<70?64:t<206?64+(t-70)*0.1875:89.5+(t-206)*0.85;
        if(t>80)h+=Math.min(4,(t-80)*0.05)*noise(x,z);
        if(x<=-196)h=Math.max(h,64+(-196-x)*1.4+3*noise(x*1.3,z*1.3));
        if(x>=190)h=Math.max(h,64+(x-190)*1.1+3*noise(x*1.2+50,z*1.2));
        // the headlands fall to the sea as cliffs
        if(x<=-205)h=Math.min(h,64+(westCoast(x)-z)*1.5);
        if(x>=196)h=Math.min(h,64+(eastCoast(x)-z)*1.5);
        // the river valley: a flat floor along the river, the hills rising on both sides
        double dv=Math.abs(x-Plan.riverX(z));
        if(dv<80)h=Math.min(h,64+Math.max(0,dv-26)*0.9+(dv>26?2*noise(x,z):0));
        return (int)Math.round(Math.max(h,64));
    }
    int seaFloor(int x,int z){
        double d=Math.abs(x-Plan.riverX(z))<=Plan.riverHalf(z)&&z<56?7:Math.min(z-56,40);
        if(x<=-205)d=Math.min(40,z-westCoast(x));
        if(x>=196)d=Math.min(40,z-eastCoast(x));
        return (int)Math.round(Math.max(Plan.FLOOR,60-d*0.3+0.8*noise(x*2,z*2)));
    }
    /** The flat platforms the town stands on. */
    int platformAt(int x,int z){
        if(x>=Plan.SHRINE_X0&&x<=Plan.SHRINE_X1&&z<=Plan.SHRINE_Z0&&z>=Plan.SHRINE_Z1)return Plan.SHRINE_Y;
        int t=Plan.terrace(x,z);if(t>=0)return Plan.TERRACE_Y[t];
        if(x>=Plan.TOWN_X0&&x<=Plan.TOWN_X1&&z>Plan.TERRACE_Z0[0]+Plan.bend(x)&&z<=Plan.PARAPET_Z)return Plan.GROUND;
        if(x>=Plan.HARBOR_X0&&x<=Plan.HARBOR_X1&&z>=Plan.SEAWARD_Z&&z<=Plan.QUAY_Z1)return Plan.GROUND;
        if(x>=Plan.LOT_X0&&x<=Plan.LOT_X1&&z>=Plan.SEAWARD_Z&&z<=Plan.LOT_Z1)return Plan.GROUND;
        // the coastal road goes on past the town both ways (to the tunnels), and the valley floor by the river
        if(z>=Plan.COAST_Z0-1&&z<=Plan.PARAPET_Z&&(x<Plan.TOWN_X0||x>Plan.TOWN_X1)&&!seaAt(x,z))return Plan.GROUND;
        return Integer.MIN_VALUE;
    }

    void build(){
        int n=Plan.W*Plan.D;int[] F=new int[n];
        for(int i=0;i<n;i++){
            int x=Plan.X0+i/Plan.D,z=Plan.Z0+i%Plan.D;
            int p=platformAt(x,z);platform[i]=p;
            boolean sea=seaAt(x,z)&&p==Integer.MIN_VALUE;
            boolean river=Math.abs(x-Plan.riverX(z))<=Plan.riverHalf(z);
            kind[i]=river?RIVER:sea?SEA:LAND;
            F[i]=p!=Integer.MIN_VALUE?p:sea?seaFloor(x,z):natural(x,z);
        }
        // banks: where a platform stands above the natural ground, the fill slopes away one block per block
        int[] E=new int[n];
        for(int i=0;i<n;i++)E[i]=platform[i]!=Integer.MIN_VALUE?platform[i]:Integer.MIN_VALUE;
        for(int pass=0;pass<14;pass++)for(int i=0;i<n;i++){
            if(platform[i]!=Integer.MIN_VALUE||kind[i]!=LAND)continue;
            int x=i/Plan.D,z=i%Plan.D,best=E[i];
            if(x>0)best=Math.max(best,E[i-Plan.D]-1);if(x<Plan.W-1)best=Math.max(best,E[i+Plan.D]-1);
            if(z>0)best=Math.max(best,E[i-1]-1);if(z<Plan.D-1)best=Math.max(best,E[i+1]-1);
            E[i]=best;
        }
        for(int i=0;i<n;i++)if(platform[i]==Integer.MIN_VALUE&&kind[i]==LAND&&E[i]>F[i])F[i]=E[i];
        // fill the columns
        for(int i=0;i<n;i++){
            int x=Plan.X0+i/Plan.D,z=Plan.Z0+i%Plan.D,top=F[i];
            c.ground(x,z,top);
            if(kind[i]==SEA||kind[i]==RIVER){
                int floor=kind[i]==RIVER&&z<Plan.SEAWARD_Z?55:top;
                if(kind[i]==RIVER)c.ground(x,z,floor);
                for(int y=Plan.FLOOR+1;y<=floor;y++)c.set(x,y,z,y==floor&&rnd.nextInt(5)==0?Blocks.GRAVEL.getDefaultState():Blocks.SAND.getDefaultState());
                if(kind[i]==RIVER)for(int y=floor+1;y<=Plan.SEA;y++)c.set(x,y,z,Blocks.WATER.getDefaultState());
                continue;
            }
            int low=lowestNeighbour(F,i);
            boolean wall=platform[i]!=Integer.MIN_VALUE&&low<top-1||platform[i]==Integer.MIN_VALUE&&low<top-2&&hasPlatformNeighbour(i);
            for(int y=Plan.FLOOR+1;y<=top;y++){
                BlockState s;
                if(wall&&y>low&&y<top)s=masonry(x,y,z);
                else if(y==top)s=surface(x,z,top);
                else if(y>=top-3)s=Blocks.DIRT.getDefaultState();
                else s=Blocks.STONE.getDefaultState();
                c.set(x,y,z,s);
            }
            // above a natural hill nothing is left of the sea; the platforms below sea level were already water
            for(int y=top+1;y<=Plan.SEA;y++)c.set(x,y,z,Blocks.AIR.getDefaultState());
        }
    }
    private int lowestNeighbour(int[] F,int i){
        int x=i/Plan.D,z=i%Plan.D,low=F[i];
        if(x>0)low=Math.min(low,F[i-Plan.D]);if(x<Plan.W-1)low=Math.min(low,F[i+Plan.D]);
        if(z>0)low=Math.min(low,F[i-1]);if(z<Plan.D-1)low=Math.min(low,F[i+1]);
        return low;
    }
    private boolean hasPlatformNeighbour(int i){
        int x=i/Plan.D,z=i%Plan.D;
        return x>0&&platform[i-Plan.D]!=Integer.MIN_VALUE||x<Plan.W-1&&platform[i+Plan.D]!=Integer.MIN_VALUE||z>0&&platform[i-1]!=Integer.MIN_VALUE||z<Plan.D-1&&platform[i+1]!=Integer.MIN_VALUE;
    }
    /** A retaining wall's stones: weathered masonry, with moss low down and on the shady side. */
    BlockState masonry(int x,int y,int z){
        int r=rnd.nextInt(100);
        if(r<46)return Blocks.STONE_BRICKS.getDefaultState();
        if(r<64)return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if(r<74)return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        if(r<86)return Blocks.ANDESITE.getDefaultState();
        if(r<94)return Blocks.COBBLESTONE.getDefaultState();
        return Blocks.MOSSY_COBBLESTONE.getDefaultState();
    }
    private BlockState surface(int x,int z,int top){
        if(kind[Canvas.idx(x,z)]!=LAND)return Blocks.SAND.getDefaultState();
        // cliffs and steep hillsides show their rock
        if(top>78&&platform[Canvas.idx(x,z)]==Integer.MIN_VALUE&&steep(x,z,top))return rnd.nextBoolean()?Blocks.STONE.getDefaultState():Blocks.ANDESITE.getDefaultState();
        if(top<=Plan.SEA+2&&near(x,z))return Blocks.SAND.getDefaultState();
        return rnd.nextInt(40)==0?Blocks.COARSE_DIRT.getDefaultState():Blocks.GRASS_BLOCK.getDefaultState();
    }
    private boolean steep(int x,int z,int top){
        return Math.abs(c.ground(x+1,z)-top)>2&&c.ground(x+1,z)!=Integer.MIN_VALUE||Math.abs(c.ground(x,z+1)-top)>2&&c.ground(x,z+1)!=Integer.MIN_VALUE;
    }
    private boolean near(int x,int z){
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(Canvas.inPlan(x+dx,z+dz)&&kind[Canvas.idx(x+dx,z+dz)]==SEA)return true;
        return false;
    }
}
