package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import java.util.Random;

/**
 * The ground. Natural land (the hill rising north behind the town to a wooded ridge, a headland on each side, the
 * river valley in the east, the island) shelves off into the sea, and flat platforms are cut into it for the town:
 * where a platform is cut into higher ground, or one platform stands above the next, the step is a stone retaining
 * wall; where it stands above natural ground, the fill slopes away as a grassy bank. Levels are kept in half blocks
 * (2y, plus one where a slab tops the column) so streets can slope evenly between the rows of houses.
 */
final class Terrain {
    static final byte SEA=1,LAND=2,RIVER=3,BEACH=4;
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

    /** The shore of the mainland: land at z up to this (the headlands' capes reach further south). */
    static double shoreZ(int x){
        if(x<-205){
            // the west headland's cape: a rounded point reaching south, its shore broken by coves
            double t=Math.min(1,(-205-x)/100.0);
            return 57+92*Math.pow(Math.sin(Math.PI*t),0.65)*(1-0.25*t)+5*Math.sin(x/9.0)+3*Math.sin(x/4.3+1);
        }
        if(x>205)return 60+4*Math.sin(x/7.0)-Math.max(0,x-262)*0.9;
        return Plan.SEAWARD_Z-1;
    }
    /** The plan's edge: natural ground falls away into the sea near it, along a coast that wanders in and out. */
    double edge(int x,int z){
        double d=Math.min(Math.min(x-Plan.X0,Plan.X0+Plan.W-1-x),z-Plan.Z0);
        d+=16*noise2(x*0.7+300,z*0.7-200)+6*noise(x*1.9,z*1.9);
        return Math.max(0,Math.min(1,(d-6)/52.0));
    }
    /** The natural height of the mainland (before platforms), or the island's. */
    double natural(int x,int z){
        if(z>150){double k=Plan.island(x,z);return k<=0?0:61.2+17*Math.pow(k,0.75)+1.5*noise2(x*1.7,z*1.7)*Math.min(1,k*3);}
        double t=-z,h;
        if(t<=60)h=64;
        else if(t<=86)h=64+(t-60)*0.31;
        else if(t<=206)h=72+(t-86)*0.2;
        else if(t<=240)h=96+(t-206)*0.4;
        else if(t<=272)h=109.6+(t-240)*1.25;
        else if(t<=300)h=149.6-(t-272)*0.3;
        else h=141-(t-300)*1.6;
        double amp=Math.max(0,Math.min(1,(t-70)/40))*4+(t>236?7:0);
        h+=amp*noise(x,z);
        // the headlands
        if(x<-200)h=Math.max(h,64+Math.min(52,(-200-x)*1.6)+4*noise2(x,z));
        if(x>200)h=Math.max(h,64+Math.min(56,(x-200)*1.5)+4*noise2(x+90,z));
        // the river valley: a flat floor along the river, its head climbing into the ridge
        double dv=Math.abs(x-Plan.riverX(z)),vf=z>=Plan.RIVER_HEAD_Z?64:64+(Plan.RIVER_HEAD_Z-z)*1.3;
        if(dv<44)h=Math.min(h,vf);
        else if(dv<130)h=Math.min(h,vf+(dv-44)*0.9+2*noise(x,z));
        // the headlands fall to the sea as cliffs
        double shore=shoreZ(x);
        if(x<-205||x>205)h=Math.min(h,62+(shore-z)*2.6);
        // and everything falls into the sea toward the plan's edge
        double e=edge(x,z);
        return Plan.FLOOR+(h-Plan.FLOOR)*(e*e*(3-2*e));
    }
    /** How deep the sea is here: shelving off the shore, a dredged basin in the harbour. */
    int seaFloor(int x,int z,double nat){
        if(x>=Plan.HARBOR_X0&&x<=Plan.HARBOR_X1+8&&z>Plan.QUAY_Z1&&z<=128)return 54+(int)Math.round(noise2(x*3,z*3));
        if(z>150&&nat>0)return (int)Math.min(61,Math.round(nat));
        double d=z-shoreZ(x);
        if(x>=Plan.BEACH_X0-6&&x<=Plan.BEACH_X1+6&&z<110)d=Math.max(0,d-6)*0.6;
        double k=Plan.island(x,z);
        double floor=61-d*0.32+0.8*noise(x*2,z*2);
        if(z>150)floor=Math.max(floor,52+k*30);
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
        if(z>=Plan.RAIL_Z0&&z<=Plan.PARAPET_Z&&x>=Plan.TUNNEL_W&&x<=Plan.TUNNEL_E&&!Plan.river(x,z))return 2*Plan.COAST;
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
            boolean river=Plan.river(x,z)&&p==NONE;
            boolean beach=x>=Plan.BEACH_X0&&x<=Plan.BEACH_X1&&z>=Plan.SEAWARD_Z&&beach(z)>=0&&p==NONE;
            boolean coast=z>shoreZ(x)&&(z<=150||Plan.island(x,z)<=0);
            boolean sea=p==NONE&&!river&&!beach&&(coast||nat[i]<62.5);
            kind[i]=river?RIVER:beach?BEACH:sea?SEA:LAND;
            F[i]=p!=NONE?p>>1:beach?beach(z):sea||river?seaFloor(x,z,nat[i]):(int)Math.round(nat[i]);
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
            if(kind[i]==SEA||kind[i]==RIVER){
                int floor=kind[i]==RIVER&&z<Plan.SEAWARD_Z?55:top;
                c.ground(x,z,floor);
                for(int y=Canvas.Y0;y<=floor;y++)c.set(x,y,z,y==floor&&rnd.nextInt(5)==0?Blocks.GRAVEL.getDefaultState():Blocks.SAND.getDefaultState());
                continue;
            }
            int low=lowestNeighbour(F,i);
            boolean riverBank=nearKind(i,RIVER,1);
            boolean wall=plat[i]!=NONE&&low<top-1||plat[i]==NONE&&low<top-2&&hasPlatformNeighbour(i)||riverBank;
            for(int y=Canvas.Y0;y<=top;y++){
                BlockState s;
                if(riverBank&&y>=55&&y<top)s=masonry(x,y,z);
                else if(wall&&y>low&&y<top)s=masonry(x,y,z);
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
    private int lvl(int[] F,int j){return kind[j]==SEA||kind[j]==RIVER?Plan.SEA-1:F[j];}
    private boolean hasPlatformNeighbour(int i){
        int x=i/Plan.D,z=i%Plan.D;
        return x>0&&plat[i-Plan.D]!=NONE||x<Plan.W-1&&plat[i+Plan.D]!=NONE||z>0&&plat[i-1]!=NONE||z<Plan.D-1&&plat[i+1]!=NONE;
    }
    private boolean nearKind(int i,byte k,int r){
        int x=i/Plan.D,z=i%Plan.D;
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            int xx=x+dx,zz=z+dz;if(xx<0||zz<0||xx>=Plan.W||zz>=Plan.D)continue;
            if(kind[xx*Plan.D+zz]==k)return true;
        }
        return false;
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
            // grass and moss cling to the ledges here and there
            double g=Pal.hash(x,5,z);
            if(g<0.10)return Blocks.GRASS_BLOCK.getDefaultState();
            if(g<0.14)return Blocks.MOSS_BLOCK.getDefaultState();
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
