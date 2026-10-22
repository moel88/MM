package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * The ground (map v2.0). The town stands on one flat platform (y 64) from the foot of the hill to the sea wall, with the
 * harbour's quay and the open lot by the sea reaching out into the water at the same level; past the ring roads the
 * land falls in a short grassy bank to rocks and the sea. North of the town a wooded hill rises to the shrine's square
 * (a platform cut into it) and falls behind it to the sea in cliffs; the island beyond the bridge is a green hill of
 * its own. Where a platform is cut into higher ground the step is a stone retaining wall; where it stands above the
 * natural ground the fill slopes away as a grassy bank. Levels are kept in half blocks (2y, plus one where a slab
 * tops the column).
 */
final class Terrain {
    static final byte SEA=1,LAND=2,BEACH=4;
    static final int NONE=Integer.MIN_VALUE;
    private final Canvas c;private final Random rnd;private final Layout layout;
    final byte[] kind=new byte[Plan.W*Plan.D];
    /** Platform level of a column in half blocks, or NONE where the ground is natural. */
    final int[] plat=new int[Plan.W*Plan.D];
    private final double[] ph=new double[12];
    Terrain(Canvas c,Layout layout,long seed){this.c=c;this.layout=layout;rnd=new Random(seed);for(int i=0;i<ph.length;i++)ph[i]=rnd.nextDouble()*Math.PI*2;}

    double noise(double x,double z){
        return (Math.sin(x/37+ph[0])*Math.cos(z/41+ph[1])+0.6*Math.sin(x/17.3+z/23.1+ph[2])+0.35*Math.cos(x/9.7-z/11.3+ph[3])+0.2*Math.sin(x/5.1+ph[4])*Math.sin(z/4.7+ph[5]))/2.15;
    }
    double noise2(double x,double z){return (Math.sin(x/29+ph[6])*Math.cos(z/31+ph[7])+0.5*Math.sin(x/13.1-z/15.7+ph[8])+0.25*Math.cos(x/6.3+z/7.1+ph[9]))/1.75;}

    /** How far in from the plan's west, east and north edges a point lies (0 at the sea, 1 well inland), the coast wandering. */
    double edge(int x,int z){
        double d=Math.min(Math.min(x-Plan.X0,Plan.X0+Plan.W-1-x),z-Plan.Z0+2);
        d+=3*noise2(x*0.9+300,z*0.9-200)+1.5*noise(x*2.3,z*2.3);
        return Math.max(0,Math.min(1,(d-3)/10.0));
    }
    /** The natural height of the land (before platforms): the town's level, the hill to the north, the island. */
    double natural(int x,int z){
        // the island beyond the bridge: a green hill standing out of the sea
        if(z>Plan.BRIDGE_Z1-12&&Math.abs(x-Plan.ISLAND_X)<Plan.ISLAND_RX+14){
            double k=Plan.island(x,z);
            if(k>0)return 61+18*Math.pow(k,0.9)+2*noise2(x*1.7,z*1.7)*Math.min(1,k*3);
        }
        // south of the sea wall the sea (the quay, the lot and the beach are laid on it)
        if(z>Plan.PARAPET_Z)return 0;
        double h=Plan.COAST;
        int t=Plan.TOWN_Z0-z;
        if(t>0){
            // the hill: a gentle rise from the street, its face up to the shrine's level, a ridge behind
            double u=Math.min(1,Math.max(0,(t-2)/22.0)),face=u*u*(3-2*u);
            h=Plan.COAST+0.4*Math.min(t,2)+18*face+Math.max(0,t-24)*0.45;
            h+=Math.min(1,t/10.0)*(3*noise(x,z)+1.5*noise2(x*1.3,z*1.3));
        }
        // the shores: past the ring roads and behind the hill the land falls to rocks and the sea
        double e=edge(x,z);
        return Plan.FLOOR+(h-Plan.FLOOR)*(e*e*(3-2*e));
    }
    /** The beach: dry sand at the water's level under the sea wall, shelving into the shallows. */
    static int beach(int z){int d=z-Plan.SEAWARD_Z;return d<10?62:d<14?61:d<18?60:d<22?59:-1;}
    static boolean beachX(int x){return x>=Plan.BEACH_X0&&x<=Plan.BEACH_X1;}

    /** The platform level of a column, in half blocks, or NONE. */
    int platformAt(int x,int z){
        int own=layout.level[Canvas.idx(x,z)];if(own!=NONE)return own;
        if(x>=Plan.SHRINE_X0&&x<=Plan.SHRINE_X1&&z<=Plan.SHRINE_Z0&&z>=Plan.SHRINE_Z1)return 2*Plan.SHRINE_Y;
        if(x>=Plan.TOWN_X0&&x<=Plan.TOWN_X1&&z>=Plan.TOWN_Z0&&z<=Plan.PARAPET_Z)return 2*Plan.COAST;
        if(x>=Plan.HARBOR_X0&&x<=Plan.HARBOR_X1&&z>=Plan.SEAWARD_Z&&z<=Plan.QUAY_Z1)return 2*Plan.COAST;
        if(x>=Plan.LOT_X0&&x<=Plan.LOT_X1&&z>=Plan.SEAWARD_Z&&z<=Plan.LOT_Z1)return 2*Plan.COAST;
        // the island's square where the bridge lands
        if(z>=Plan.BRIDGE_Z1-1&&z<=Plan.BRIDGE_Z1+12&&Math.abs(x)<=14)return 2*66;
        return NONE;
    }

    void build(){
        int n=Plan.W*Plan.D;int[] F=new int[n];double[] nat=new double[n];
        for(int i=0;i<n;i++){
            int x=Plan.X0+i/Plan.D,z=Plan.Z0+i%Plan.D;
            int p=platformAt(x,z);plat[i]=p;
            nat[i]=natural(x,z);
            boolean beach=beachX(x)&&z>=Plan.SEAWARD_Z&&beach(z)>=0&&p==NONE;
            boolean sea=p==NONE&&!beach&&nat[i]<62.5;
            kind[i]=beach?BEACH:sea?SEA:LAND;
            F[i]=p!=NONE?p>>1:beach?beach(z):(int)Math.round(nat[i]);
        }
        // the sea floor: shelving off every shore (a third of a block down per block out), a dredged basin in the harbour,
        // the shallows off the beach
        int[] dist=shoreDistance();
        for(int i=0;i<n;i++){
            if(kind[i]!=SEA)continue;
            int x=Plan.X0+i/Plan.D,z=Plan.Z0+i%Plan.D;
            double d=dist[i];
            if(x>=Plan.BEACH_X0-6&&x<=Plan.BEACH_X1+6&&z<Plan.SEAWARD_Z+40)d=Math.max(0,d-6)*0.6;
            double floor=61-d*0.33+0.8*noise(x*2,z*2);
            if(nat[i]>0&&Plan.island(x,z)>0)floor=Math.max(floor,nat[i]);
            if(x>=Plan.X0+4&&x<=-66&&z>Plan.QUAY_Z1&&z<=166)floor=Math.min(floor,54+noise2(x*3,z*3));
            F[i]=(int)Math.round(Math.max(Plan.FLOOR,Math.min(61,floor)));
        }
        // banks: where a platform stands above the natural ground, the fill slopes away one block per block
        int[] E=new int[n];
        for(int i=0;i<n;i++)E[i]=plat[i]!=NONE?plat[i]>>1:-1000;
        for(int pass=0;pass<16;pass++)for(int i=0;i<n;i++){
            if(plat[i]!=NONE||kind[i]!=LAND)continue;
            int x=i/Plan.D,z=i%Plan.D,best=E[i];
            if(x>0)best=Math.max(best,E[i-Plan.D]-1);if(x<Plan.W-1)best=Math.max(best,E[i+Plan.D]-1);
            if(z>0)best=Math.max(best,E[i-1]-1);if(z<Plan.D-1)best=Math.max(best,E[i+1]-1);
            E[i]=best;
        }
        for(int i=0;i<n;i++)if(plat[i]==NONE&&kind[i]==LAND&&E[i]>F[i])F[i]=E[i];
        // fill the columns
        for(int i=0;i<n;i++){
            int x=Plan.X0+i/Plan.D,z=Plan.Z0+i%Plan.D,top=F[i];
            c.ground(x,z,top);
            if(kind[i]==SEA){
                for(int y=Canvas.Y0;y<=top;y++)c.set(x,y,z,y==top&&rnd.nextInt(5)==0?Blocks.GRAVEL.getDefaultState():Blocks.SAND.getDefaultState());
                continue;
            }
            int low=lowestNeighbour(F,i);
            boolean wall=plat[i]!=NONE&&low<top-1||plat[i]==NONE&&low<top-2&&hasPlatformNeighbour(i);
            for(int y=Canvas.Y0;y<=top;y++){
                BlockState s;
                if(wall&&y>low&&y<top)s=masonry(x,y,z);
                else if(y==top)s=surface(x,z,top,i,F);
                else if(y>=top-3)s=kind[i]==BEACH||kind[i]==LAND&&top<=Plan.SEA+1?Blocks.SAND.getDefaultState():plat[i]==NONE&&slope(F,i,top)>=2?rock(x,y,z):Blocks.DIRT.getDefaultState();
                else s=Blocks.STONE.getDefaultState();
                c.set(x,y,z,s);
            }
        }
    }
    /** How far each sea column lies from the nearest land, in blocks (4-way steps), up to 60. */
    private int[] shoreDistance(){
        int n=Plan.W*Plan.D;int[] d=new int[n];
        ArrayDeque<Integer> q=new ArrayDeque<>();
        for(int i=0;i<n;i++){if(kind[i]!=SEA){d[i]=0;q.add(i);}else d[i]=60;}
        while(!q.isEmpty()){
            int i=q.poll(),x=i/Plan.D,z=i%Plan.D;
            if(d[i]>=60)continue;
            int[] nb={x>0?i-Plan.D:-1,x<Plan.W-1?i+Plan.D:-1,z>0?i-1:-1,z<Plan.D-1?i+1:-1};
            for(int j:nb)if(j>=0&&d[j]>d[i]+1){d[j]=d[i]+1;q.add(j);}
        }
        return d;
    }
    private int lowestNeighbour(int[] F,int i){
        int x=i/Plan.D,z=i%Plan.D,low=F[i];
        if(x>0)low=Math.min(low,lvl(F,i-Plan.D));if(x<Plan.W-1)low=Math.min(low,lvl(F,i+Plan.D));
        if(z>0)low=Math.min(low,lvl(F,i-1));if(z<Plan.D-1)low=Math.min(low,lvl(F,i+1));
        return low;
    }
    private int lvl(int[] F,int j){return kind[j]==SEA?Plan.SEA-1:F[j];}
    private boolean hasPlatformNeighbour(int i){
        int x=i/Plan.D,z=i%Plan.D;
        return x>0&&plat[i-Plan.D]!=NONE||x<Plan.W-1&&plat[i+Plan.D]!=NONE||z>0&&plat[i-1]!=NONE||z<Plan.D-1&&plat[i+1]!=NONE;
    }
    /** A retaining wall's stones: weathered masonry, some of it mossy or cracked. */
    BlockState masonry(int x,int y,int z){
        double r=Pal.hash(x,y,z)*100;
        if(r<46)return Blocks.STONE_BRICKS.getDefaultState();
        if(r<62)return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if(r<72)return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        if(r<86)return Blocks.ANDESITE.getDefaultState();
        if(r<94)return Blocks.COBBLESTONE.getDefaultState();
        return Blocks.MOSSY_COBBLESTONE.getDefaultState();
    }
    private BlockState surface(int x,int z,int top,int i,int[] F){
        if(kind[i]==BEACH)return Blocks.SAND.getDefaultState();
        // cliffs and steep hillsides show their rock, the shore its sand and stones
        if(plat[i]==NONE&&steep(F,i,top)){
            double g=Pal.hash(x,5,z),green=top<Plan.SEA+8?0.10:0.45;
            if(g<green)return Blocks.GRASS_BLOCK.getDefaultState();
            if(g<green+0.06)return Blocks.MOSS_BLOCK.getDefaultState();
            return rock(x,top,z);
        }
        if(plat[i]==NONE&&top<=Plan.SEA+1)return Pal.hash(x,6,z)<0.7?Blocks.SAND.getDefaultState():Blocks.GRAVEL.getDefaultState();
        return Pal.hash(x,7,z)<0.025?Blocks.COARSE_DIRT.getDefaultState():Blocks.GRASS_BLOCK.getDefaultState();
    }
    /** The greatest drop from a column to its neighbours. */
    private int slope(int[] F,int i,int top){
        int x=i/Plan.D,z=i%Plan.D,d=0;
        for(int[] o:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            int xx=x+o[0],zz=z+o[1];if(xx<0||zz<0||xx>=Plan.W||zz>=Plan.D)continue;
            d=Math.max(d,top-lvl(F,xx*Plan.D+zz));
        }
        return d;
    }
    /**
     * Bare rock of the cliffs and the shore: beds of stone, andesite and tuff a few blocks thick that wander up and
     * down across the face, broken and grown over in places.
     */
    BlockState rock(int x,int y,int z){
        double h=Pal.hash(x,y,z);
        if(h<0.05)return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        if(h<0.10)return Blocks.COBBLESTONE.getDefaultState();
        int bed=Math.floorMod((int)Math.floor((y+Pal.smooth(x,z,29,31)*7)/2.5),6);
        return switch(bed){
            case 0,3->h<0.78?Blocks.STONE.getDefaultState():Blocks.ANDESITE.getDefaultState();
            case 1,4->h<0.72?Blocks.ANDESITE.getDefaultState():Blocks.STONE.getDefaultState();
            default->h<0.70?Blocks.TUFF.getDefaultState():Blocks.STONE.getDefaultState();
        };
    }
    private boolean steep(int[] F,int i,int top){
        int x=i/Plan.D,z=i%Plan.D;
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            int xx=x+d[0],zz=z+d[1];if(xx<0||zz<0||xx>=Plan.W||zz>=Plan.D)continue;
            if(Math.abs(lvl(F,xx*Plan.D+zz)-top)>2)return true;
        }
        return false;
    }
}
