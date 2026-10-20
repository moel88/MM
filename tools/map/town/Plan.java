package dev.sangwoo.botandtown;

/**
 * The town's layout: a Japanese seaside town facing the sea to the south (+z), on flat ground along the coast that
 * climbs northward in four terraces to a shrine under a wooded ridge; a river to the east with the landmark bridge
 * over its mouth, a fishing harbour to the west, a beach and a waterfront lot (the battle ground) on the coast.
 *
 * <pre>
 *  z -280..-240  ridge (forest), rising to about y 150 behind the shrine
 *  z -236..-218  shrine (y 96), its stone steps up from T4
 *  z -206..-177  terrace T4 (y 88)        ┐
 *  z -176..-147  terrace T3 (y 82)        │ houses, a street through each terrace, the main stairway up the middle
 *  z -146..-117  terrace T2 (y 76)        │
 *  z -116..-87   terrace T1 (y 70)        ┘
 *  z  -86..-81   street S4 (hill foot)          y 64 from here south
 *  z  -46..-41   street S3                      main street x -5..5 (shops), side streets every ~50 blocks
 *  z   -6..-1    street S2
 *  z   18..23    street S1 (station street)
 *  z   24..36    station square, station street shops
 *  z   37..45    railway (platform 37..39, track 42, fence 45)
 *  z   46..52    coastal road (asphalt, a dashed centre line at 49)
 *  z   53..55    seaside promenade; parapet at 56
 *  z   57+       sea: harbour (x -200..-70), beach (x -60..30), battle ground (x 38..128), river mouth (x ~140..190)
 * </pre>
 */
final class Plan {
    private Plan(){}
    /** The planned area (x X0..X0+W-1, z Z0..Z0+D-1); outside it the flat ocean of the world goes on. */
    static final int X0=-240,W=480,Z0=-280,D=480;
    /** Water surface (top water block), the low town's ground (top solid block), and the sea floor. */
    static final int SEA=62,GROUND=64,FLOOR=50;

    // east-west streets: south edge..north edge (z1 is the larger)
    static final int S1_Z0=18,S1_Z1=23,S2_Z0=-6,S2_Z1=-1,S3_Z0=-46,S3_Z1=-41,S4_Z0=-86,S4_Z1=-81;
    static final int RAIL_Z0=37,RAIL_Z1=45,TRACK_Z=42;
    static final int COAST_Z0=46,COAST_Z1=52,PROM_Z0=53,PROM_Z1=55,PARAPET_Z=56,SEAWARD_Z=57;
    /** Main street: asphalt x -3..3, sidewalks to ±5. */
    static final int MAIN_X0=-5,MAIN_X1=5;
    /** Side streets (x0..x1, 5 wide). */
    static final int[][] SIDE_STREETS={{-196,-192},{-152,-148},{-104,-100},{-56,-52},{44,48},{92,96},{128,132}};
    /** The low town's west and east ends. */
    static final int TOWN_X0=-204,TOWN_X1=136;

    /** Terraces: surface y, south edge z (the wall line) and north edge, before the curve. */
    static final int[] TERRACE_Y={70,76,82,88};
    static final int[] TERRACE_Z0={-87,-117,-147,-177};
    static final int[] TERRACE_Z1={-116,-146,-176,-206};
    static final int[] TERRACE_XW={-196,-192,-186,-178},TERRACE_XE={128,120,110,98};
    /** Each terrace's street (relative to its south edge, toward -z): 16..19 blocks in. */
    static final int TERRACE_STREET_IN=16,TERRACE_STREET_W=4;
    /** The shrine platform. */
    static final int SHRINE_Y=96,SHRINE_X0=-18,SHRINE_X1=18,SHRINE_Z0=-218,SHRINE_Z1=-236;
    /** The main stairway's corridor (x), the ramps (x0, x1, from level, to terrace) up the hill. */
    static final int STAIR_X0=-4,STAIR_X1=4;
    static final int[][] RAMPS={{-151,-145,-1,0},{103,109,0,1},{-137,-131,1,2},{71,77,2,3}};

    /** How far north (0..-5) the terraces bend at this x: the hill is not a ruler, and never crowds street S4. */
    static int bend(int x){
        // straight across the stairway and the ramps, so their flights meet each wall square on
        if(x>=STAIR_X0-2&&x<=STAIR_X1+2)x=0;
        for(int[] r:RAMPS)if(x>=r[0]-2&&x<=r[1]+2)x=(r[0]+r[1])/2;
        return (int)Math.round(-2.5-1.5*Math.sin(x/47.0)-1.0*Math.sin(x/19.0+1.3));
    }
    /** The river's centre line. */
    static double riverX(int z){return 165+6*Math.sin(z/45.0);}
    /** The river's half width (water), widening into the mouth. */
    static double riverHalf(int z){return 10+Math.max(0,z-40)*0.6;}

    // the coast
    static final int HARBOR_X0=-200,HARBOR_X1=-70,QUAY_Z1=68;
    static final int BEACH_X0=-60,BEACH_X1=30,BEACH_Z1=82;
    static final int LOT_X0=38,LOT_X1=128,LOT_Z1=118;
    /** The landmark bridge: deck height, ends, pylons (relative to the river at the coastal road). */
    static final int DECK_Y=72;

    /** Which terrace (0..3) a point lies on, or -1. */
    static int terrace(int x,int z){
        int b=bend(x);
        for(int i=0;i<4;i++)if(z<=TERRACE_Z0[i]+b&&z>=TERRACE_Z1[i]+b&&x>=TERRACE_XW[i]&&x<=TERRACE_XE[i])return i;
        return -1;
    }
    /** The z range (south edge, north edge) of terrace i's street at x. */
    static int terraceStreetZ0(int i,int x){return TERRACE_Z0[i]+bend(x)-TERRACE_STREET_IN;}
    static int terraceStreetZ1(int i,int x){return terraceStreetZ0(i,x)-TERRACE_STREET_W+1;}
}
