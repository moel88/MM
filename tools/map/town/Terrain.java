package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import java.util.Random;

/**
 * The ground. Natural land (the hill rising north behind the town to a wooded ridge, a small headland in the west,
 * the steep east hill running on south as a long headland, the island) shelves off into the sea, and flat platforms
 * are cut into it for the town:
 * where a platform is cut into higher ground, or one platform stands above the next, the step is a stone retaining
 * wall; where it stands above natural ground, the fill slopes away as a grassy bank. Levels are kept in half blocks
 * (2y, plus one where a slab tops the column) so streets can slope evenly between the rows of houses.
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

    /** The shore of the mainland: land at z up to this (the headlands reach further south). */
    static double shoreZ(int x){
        if(x<-178){
            // the west headland's cape, a rounded point broken by coves
            double t=Math.min(1,(-178-x)/22.0);
            return 57+30*Math.pow(Math.sin(Math.PI*Math.min(1,t*0.8+0.2)),0.65)+4*Math.sin(x/5.0)+2*Math.sin(x/2.7+1);
        }
        // the east hill runs on south into the sea as a long headland
        if(x>Plan.EAST_FOOT_X)return 57+Math.min(135,(x-Plan.EAST_FOOT_X)*2.2)+5*Math.sin(x/9.0)+3*Math.sin(x/4.1+2);
        return Plan.SEAWARD_Z-1;
    }
    /** The plan's edge (west, north and east): natural ground falls away into the sea near it, the coast wandering. */
    double edge(int x,int z){
        double d=Math.min(Math.min(x-Plan.X0,Plan.X0+Plan.W-1-x),z-Plan.Z0);
        d+=8*noise2(x*0.7+300,z*0.7-200)+3*noise(x*1.9,z*1.9);
        return Math.max(0,Math.min(1,(d-2)/12.0));
    }
    /** The natural height of the mainland (before platforms), or the island's. */
    double natural(int x,int z){
        // the island: a steep green hill standing out of the sea beyond the bridge
        if(z>Plan.BRIDGE_Z1-8&&Math.abs(x-Plan.ISLAND_X)<Plan.ISLAND_RX+12){
            double k=Plan.island(x,z);
            if(k>0)return 61+42*Math.pow(k,0.85)+2*noise2(x*1.7,z*1.7)*Math.min(1,k*3);
            if(z>Plan.SEAWARD_Z+20)return 0;
        }
        double t=-z,h;
        if(t<=60)h=64;
        else if(t<=86)h=64+(t-60)*0.31;
        else if(t<=146)h=72+(t-86)*0.2;
        else if(t<=166)h=84+(t-146)*0.45;
        else h=93+(t-166)*0.9;
        double amp=Math.max(0,Math.min(1,(t-70)/40))*4+(t>160?6:0);
        h+=amp*noise(x,z);
        // the west headland, and the steep east hill rising from the lot and the town's east end
        if(x<-178)h=Math.max(h,64+Math.min(40,(-178-x)*1.8)+4*noise2(x,z));
        if(x>=Plan.EAST_FOOT_X)h=Math.max(h,64+Math.min(66,(x-Plan.EAST_FOOT_X+1)*1.05)+5*noise2(x+90,z));
        // the headlands fall to the sea as cliffs
        double shore=shoreZ(x);
        if(x<-178||x>Plan.EAST_FOOT_X)h=Math.min(h,62+(shore-z)*2.2);
        // and everything falls into the sea toward the plan's edge
        double e=edge(x,z);
        return Plan.FLOOR+(h-Plan.FLOOR)*(e*e*(3-2*e));
    }
    /** How deep the sea is here: shelving off the shore, a dredged basin in the harbour. */
    int seaFloor(int x,int z,double nat){
        if(x>=Plan.HARBOR_X0&&x<=Plan.HARBOR_X1+8&&z>Plan.QUAY_Z1&&z<=118)return 54+(int)Math.round(noise2(x*3,z*3));
        double k=Plan.island(x,z);
        if(k>0&&nat>0)return (int)Math.min(61,Math.round(nat));
        double d=z-shoreZ(x);
        if(x>=Plan.BEACH_X0-6&&x<=Plan.BEACH_X1+6&&z<100)d=Math.max(0,d-6)*0.6;
        double floor=61-d*0.32+0.8*noise(x*2,z*2);
        if(k>-0.6)floor=Math.max(floor,52+(k+0.6)*14);
        return (int)Math.round(Math.max(Plan.FLOOR,Math.min(61,floor)));
    }
    /** The beach: dry sand at the water's level under the sea wall, shelving into the shallows. */
    static int beach(int z){int d=z-Plan.SEAWARD_Z;return d<10?62:d<14?61:d<18?60:d<22?59:-1;}

    /** The platform level of a column, in half blocks, or NONE. */
    int platformAt(int x,int z){
        int own=layout.level[Canvas.idx(x,z)];if(own!=NONE)return own;
        if(x>=Plan.SHRINE_X0&&x<=Plan.SHRINE_X1&&z<=Plan.SHRINE_Z0&&z>=Plan.SHRINE_Z1)return 2*Plan.SHRINE_Y;
        int t=Plan.terrace(x,z);if(t>=0)return 2*Plan.TERRACE_Y[t];
        if(x>=Plan.TOWN_X0&&x<=Plan.TOWN_X1&&z>Plan.TERRACE_Z0[0]+Plan.bend(x)&&z<=Plan.PARAPET_Z){
            if(z>Plan.ST_S[0])return 2*Plan.COAST;
            if(Plan.mainStreet(x)||Plan.sideStreet(x)!=null)return Plan.slopeHalf(z);
            int k=Plan.street(z);if(k>=0)return 2*Plan.ST_Y[k];
            int r=Plan.row(z);if(r>=0)return 2*Plan.ROWS[r][2];
            return z<Plan.ST_N[3]?2*Plan.ST_Y[3]:2*Plan.COAST;
        }
        if(x>=Plan.HARBOR_X0&&x<=Plan.HARBOR_X1&&z>=Plan.SEAWARD_Z&&z<=Plan.QUAY_Z1)return 2*Plan.COAST;
        if(x>=Plan.LOT_X0&&x<=Plan.LOT_X1&&z>=Plan.SEAWARD_Z&&z<=Plan.LOT_Z1)return 2*Plan.COAST;
        // the railway, the coastal road and the promenade go on past the town both ways, to the tunnels
        if(z>=Plan.RAIL_Z0&&z<=Plan.PARAPET_Z&&x>=Plan.TUNNEL_W&&x<=Plan.TUNNEL_E)return 2*Plan.COAST;
        // the little terraces of houses up the east hill's face
        int hill=Hillside.level(x,z);if(hill!=NONE)return hill;
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
            boolean beach=x>=Plan.BEACH_X0&&x<=Plan.BEACH_X1&&z>=Plan.SEAWARD_Z&&beach(z)>=0&&p==NONE;
            boolean coast=z>shoreZ(x)&&Plan.island(x,z)<=0;
            boolean sea=p==NONE&&!beach&&(coast||nat[i]<62.5);
            kind[i]=beach?BEACH:sea?SEA:LAND;
            F[i]=p!=NONE?p>>1:beach?beach(z):sea?seaFloor(x,z,nat[i]):(int)Math.round(nat[i]);
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
                int floor=top;
                c.ground(x,z,floor);
                for(int y=Canvas.Y0;y<=floor;y++)c.set(x,y,z,y==floor&&rnd.nextInt(5)==0?Blocks.GRAVEL.getDefaultState():Blocks.SAND.getDefaultState());
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
            // above the beach's wet sand the sea stays; the half block on top of sloping streets is laid by the streets
        }
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
            // the foot of a sea cliff is bare rock; higher up grass and moss take hold on half the ledges, and trees on some
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
     * Bare rock of the hillsides, where a slope shows its side: beds of stone, andesite and tuff a few blocks thick
     * that wander up and down across the face, broken and grown over in places.
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
