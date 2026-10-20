package dev.sangwoo.botandtown;

import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * Cuts the town into plots before the ground is shaped: every row of the low town between its side streets, the shop
 * plots along the sloping main street (each levelled to the sidewalk at its door), the plots on both sides of each
 * terrace street, and the sites kept for the station and the convenience store. Plots facing the main street get
 * their own ground level, which the terrain then cuts or fills to.
 */
final class Layout {
    final List<Lot> lots=new ArrayList<>();
    /** Ground levels set by plots, in half blocks, or {@link Terrain#NONE}. */
    final int[] level=new int[Plan.W*Plan.D];
    private final Random rnd;
    /** How deep the shop plots along the main street are. */
    static final int SHOP_D=13;
    /** The north-south corridors of the low town (side streets and the main street), west to east. */
    static final int[][] CORRIDORS;
    static{
        List<int[]> l=new ArrayList<>(List.of(Plan.SIDE_STREETS));
        l.add(new int[]{Plan.MAIN_X0,Plan.MAIN_X1});
        l.sort(Comparator.comparingInt(a->a[0]));
        CORRIDORS=l.toArray(new int[0][]);
    }
    Layout(Random rnd){this.rnd=rnd;Arrays.fill(level,Terrain.NONE);}

    void plan(){
        lowTown();
        terraces();
        Town.log("plots: "+lots.size());
    }

    /** The blocks of x between the corridors (west end to east end of the low town). */
    static List<int[]> xBlocks(){
        List<int[]> out=new ArrayList<>();
        int x=Plan.TOWN_X0;
        for(int[] c:CORRIDORS){if(c[0]-1>=x)out.add(new int[]{x,c[0]-1});x=c[1]+1;}
        if(x<=Plan.TOWN_X1)out.add(new int[]{x,Plan.TOWN_X1});
        return out;
    }

    private void lowTown(){
        for(int r=0;r<Plan.ROWS.length;r++){
            int zN=Plan.ROWS[r][0],zS=Plan.ROWS[r][1],y=Plan.ROWS[r][2];
            Direction front=Plan.ROWS[r][3]==1?Direction.SOUTH:Direction.NORTH;
            for(int[] b:xBlocks()){
                int xa=b[0],xb=b[1];
                if(xb-xa+1<9){lots.add(new Lot(xa,zN,xb,zS,y,front,"strip"));continue;}
                // the station's block, south of the station street east of the main street
                if(r==0&&xa==Plan.MAIN_X1+1){lots.add(new Lot(xa,zN,xb,zS,y,front,"station"));continue;}
                // shop plots along the main street, levelled to the sidewalk at their doors
                if(xb==Plan.MAIN_X0-1){shopStrip(xb-SHOP_D+1,xb,zN,zS,Direction.EAST);xb-=SHOP_D;}
                if(xa==Plan.MAIN_X1+1){shopStrip(xa,xa+SHOP_D-1,zN,zS,Direction.WEST);xa+=SHOP_D;}
                // the convenience store, on the station street just east of the main street
                if(r==1&&b[0]==Plan.MAIN_X1+1){lots.add(new Lot(xa,zN,xb,zS,y,front,"konbini"));continue;}
                String zone=r<=1&&xa>=-60&&xb<=60?"station-street":"row";
                cut(xa,xb,9,14,(x0,x1)->lots.add(new Lot(x0,zN,x1,zS,y,front,zone)));
            }
        }
    }
    private void shopStrip(int x0,int x1,int zN,int zS,Direction front){
        int z=zN;
        while(z<=zS){
            int w=Math.min(zS-z+1,8+rnd.nextInt(4));
            if(zS-(z+w-1)<7)w=zS-z+1;
            int zc=z+w/2,lv=Plan.slopeHalf(zc)+1,y=(lv+1)>>1;
            var lot=new Lot(x0,z,x1,z+w-1,y,front,"main");lots.add(lot);
            for(int x=x0;x<=x1;x++)for(int zz=z;zz<z+w;zz++)level[Canvas.idx(x,zz)]=2*y;
            z+=w;
        }
    }
    interface Cut{void lot(int a,int b);}
    /** Cuts [a..b] into pieces min..max long (the last takes what is left, or joins the one before). */
    void cut(int a,int b,int min,int max,Cut out){
        int x=a;
        while(x<=b){
            int w=min+rnd.nextInt(max-min+1);
            if(b-(x+w-1)<min)w=b-x+1;
            out.lot(x,x+w-1);x+=w;
        }
    }

    private void terraces(){
        for(int i=0;i<4;i++){
            int y=Plan.TERRACE_Y[i];
            // stretches of the terrace clear of the stairway and of the ramps cut into its rows
            List<int[]> spans=new ArrayList<>();spans.add(new int[]{Plan.TERRACE_XW[i]+3,Plan.TERRACE_XE[i]-3});
            spans=minus(spans,Plan.STAIR_X0-3,Plan.STAIR_X1+3);
            List<int[]> south=new ArrayList<>(spans),north=new ArrayList<>(spans);
            for(int[] r:Plan.RAMPS){
                if(r[3]==i)south=minus(south,r[0]-2,r[1]+2);
                if(r[2]==i)north=minus(north,r[0]-2,r[1]+2);
            }
            final int ti=i;
            for(int[] s:south)cut(s[0],s[1],10,15,(x0,x1)->{
                int zN=Integer.MIN_VALUE,zS=Integer.MAX_VALUE;
                for(int x=x0;x<=x1;x++){zN=Math.max(zN,Plan.terraceStreetZ0(ti,x)+1);zS=Math.min(zS,Plan.TERRACE_Z0[ti]+Plan.bend(x)-1);}
                if(zS-zN>=7)lots.add(new Lot(x0,zN,x1,zS,y,Direction.NORTH,"terrace"));
            });
            for(int[] s:north)cut(s[0],s[1],8,13,(x0,x1)->{
                int zN=Integer.MIN_VALUE,zS=Integer.MAX_VALUE;
                for(int x=x0;x<=x1;x++){zN=Math.max(zN,Plan.TERRACE_Z1[ti]+Plan.bend(x)+1);zS=Math.min(zS,Plan.terraceStreetZ1(ti,x)-1);}
                if(zS-zN>=6)lots.add(new Lot(x0,zN,x1,zS,y,Direction.SOUTH,"terrace-back"));
            });
        }
    }
    /** Intervals minus [a..b]. */
    static List<int[]> minus(List<int[]> in,int a,int b){
        List<int[]> out=new ArrayList<>();
        for(int[] s:in){
            if(b<s[0]||a>s[1]){out.add(s);continue;}
            if(a-1>=s[0])out.add(new int[]{s[0],a-1});
            if(b+1<=s[1])out.add(new int[]{b+1,s[1]});
        }
        return out;
    }
    Lot at(int x,int z){for(Lot l:lots)if(l.contains(x,z))return l;return null;}
}
