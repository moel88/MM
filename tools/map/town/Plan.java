package dev.sangwoo.botandtown;

/**
 * The town's layout: a Japanese seaside town facing the sea to the south (+z). The low town climbs gently from the
 * coast (y 64) to the hill foot (y 72) in rows held by low stone walls; behind it four terraces climb to a shrine under
 * a wooded ridge. The main street runs straight down the middle from the hill's stairway, across the railway at a
 * level crossing, and on over the sea as the landmark bridge to a small island. A fishing harbour lies to the west
 * under a headland, a beach beside the bridge, the battle ground (an old waterfront lot) beside it to the east, and a
 * river to the east under the east headland. The whole is an island in the flat ocean of the world.
 *
 * <pre>
 *  z -352..-300  north coast
 *  z -300..-250  ridge (forest), up to about y 150
 *  z -236..-218  shrine (y 104), its stone steps up from T4
 *  z -206..-177  terrace T4 (y 96)        ┐
 *  z -176..-147  terrace T3 (y 90)        │ houses, a street through each terrace, the main stairway up the middle,
 *  z -146..-117  terrace T2 (y 84)        │ ramps for cars between them
 *  z -116..-87   terrace T1 (y 78)        ┘
 *  z  -86..-81   street S4 (y 72)
 *  z  -80..-47   two rows of houses (y 72 / 69)
 *  z  -46..-41   street S3 (y 69)                main street x -5..5 (shops), side streets every ~50 blocks,
 *  z  -40..-7    two rows (y 69 / 66)             sloping evenly between the streets
 *  z   -6..-1    street S2 (y 66)
 *  z    0..17    one row (y 64)
 *  z   18..23    street S1, the station street (y 64)
 *  z   24..36    station square and buildings along S1
 *  z   37..45    railway (platform 37..39, track 41)
 *  z   46..52    coastal road (a dashed centre line at 49)
 *  z   53..55    seaside promenade; parapet at 56
 *  z   57+       sea: harbour (x -212..-100), beach (x -92..-10), the bridge (x -6..6) to the island (z 207..267),
 *                battle ground (x 14..104), river mouth (x ~150..200)
 * </pre>
 */
final class Plan {
    private Plan(){}
    /** The planned area (x X0..X0+W-1, z Z0..Z0+D-1, whole chunks); outside it the flat ocean of the world goes on. */
    static final int X0=-336,W=640,Z0=-352,D=640;
    /** Water surface (top water block), the coast's ground (top solid block), and the sea floor. */
    static final int SEA=62,COAST=64,FLOOR=50;

    // the four east-west streets of the low town, S1 (station street) to S4 (hill foot): north edge, south edge, level
    static final int[] ST_N={18,-6,-46,-86},ST_S={23,-1,-41,-81},ST_Y={64,66,69,72};
    /** Rows of lots between the streets: north edge, south edge, level, whether the row fronts the street to its south. */
    static final int[][] ROWS={{24,36,64,0},{0,17,64,1},{-23,-7,66,1},{-40,-24,69,0},{-63,-47,69,1},{-80,-64,72,0}};
    /** The low town's west and east ends. */
    static final int TOWN_X0=-204,TOWN_X1=136;
    /** Main street: asphalt x -3..3, sidewalks to ±5. */
    static final int MAIN_X0=-5,MAIN_X1=5;
    /** Side streets (x0..x1, 5 wide) from S4 to S1; the one at x -104 crosses the railway to the coastal road. */
    static final int[][] SIDE_STREETS={{-196,-192},{-152,-148},{-104,-100},{-56,-52},{44,48},{92,96},{128,132}};
    static final int CROSSING_X0=-104,CROSSING_X1=-100;

    // the railway and the coast
    static final int RAIL_Z0=37,PLATFORM_Z1=39,TRACK_Z=41,RAIL_FENCE_Z=45;
    static final int COAST_Z0=46,COAST_Z1=52,CENTRE_Z=49,PROM_Z0=53,PROM_Z1=55,PARAPET_Z=56,SEAWARD_Z=57;
    /** The station: building and platform. */
    static final int STATION_X0=12,STATION_X1=34,PLATFORM_X0=8,PLATFORM_X1=58;
    /** Where the coastal road and the railway go into the headlands. */
    static final int TUNNEL_W=-222,TUNNEL_E=245;

    /** Terraces: surface y, south edge z (the wall line) and north edge, before the curve; west and east ends. */
    static final int[] TERRACE_Y={78,84,90,96};
    static final int[] TERRACE_Z0={-87,-117,-147,-177};
    static final int[] TERRACE_Z1={-116,-146,-176,-206};
    static final int[] TERRACE_XW={-196,-192,-186,-178},TERRACE_XE={128,120,110,98};
    /** Each terrace's street (relative to its south edge, toward -z): 16..19 blocks in. */
    static final int TERRACE_STREET_IN=16,TERRACE_STREET_W=4;
    /** The shrine platform. */
    static final int SHRINE_Y=104,SHRINE_X0=-20,SHRINE_X1=20,SHRINE_Z0=-218,SHRINE_Z1=-238;
    /** The main stairway's corridor (x); the ramps for cars (x0, x1, from level (-1 the hill foot), to terrace). */
    static final int STAIR_X0=-4,STAIR_X1=4;
    static final int[][] RAMPS={{-151,-145,-1,0},{103,109,0,1},{-137,-131,1,2},{71,77,2,3}};

    // the sea front
    static final int HARBOR_X0=-212,HARBOR_X1=-100,QUAY_Z1=68;
    static final int BEACH_X0=-92,BEACH_X1=-10;
    static final int LOT_X0=14,LOT_X1=104,LOT_Z1=118;
    /** The landmark bridge: its deck (x), the ends of the span (z), the pylon, the island beyond it. */
    static final int BRIDGE_X0=-6,BRIDGE_X1=6,BRIDGE_Z0=53,BRIDGE_Z1=208,PYLON_Z=131;
    static final int ISLAND_X=0,ISLAND_Z=238,ISLAND_RX=44,ISLAND_RZ=31;

    /** How far north (0..-5) the terraces bend at this x: the hill is not a ruler, and never crowds street S4. */
    static int bend(int x){
        // straight across the stairway and the ramps, so their flights meet each wall square on
        if(x>=STAIR_X0-2&&x<=STAIR_X1+2)x=0;
        for(int[] r:RAMPS)if(x>=r[0]-2&&x<=r[1]+2)x=(r[0]+r[1])/2;
        return (int)Math.round(-2.5-1.5*Math.sin(x/47.0)-1.0*Math.sin(x/19.0+1.3));
    }
    /** The river's centre line, its half width (water, widening into the mouth), and where it starts in the hills. */
    static double riverX(int z){return 170+6*Math.sin(z/45.0);}
    static double riverHalf(int z){return 11+Math.max(0,z-30)*0.4;}
    static final int RIVER_HEAD_Z=-226;
    static boolean river(int x,int z){return z>=RIVER_HEAD_Z&&Math.abs(x-riverX(z))<=riverHalf(z);}

    /** Which terrace (0..3) a point lies on, or -1. */
    static int terrace(int x,int z){
        int b=bend(x);
        for(int i=0;i<4;i++)if(z<=TERRACE_Z0[i]+b&&z>=TERRACE_Z1[i]+b&&x>=TERRACE_XW[i]&&x<=TERRACE_XE[i])return i;
        return -1;
    }
    /** The z range (south edge, north edge) of terrace i's street at x. */
    static int terraceStreetZ0(int i,int x){return TERRACE_Z0[i]+bend(x)-TERRACE_STREET_IN;}
    static int terraceStreetZ1(int i,int x){return terraceStreetZ0(i,x)-TERRACE_STREET_W+1;}

    /** Which east-west street (0..3) a z lies in, or -1. */
    static int street(int z){for(int k=0;k<4;k++)if(z>=ST_N[k]&&z<=ST_S[k])return k;return -1;}
    /** The row (index into ROWS) a z lies in, or -1. */
    static int row(int z){for(int r=0;r<ROWS.length;r++)if(z>=ROWS[r][0]&&z<=ROWS[r][1])return r;return -1;}
    /**
     * The level of a north-south street at z, in half blocks (2y, plus one where a slab tops it): level along each
     * east-west street, rising evenly between them; the coast's level south of S1, S4's north of it.
     */
    static int slopeHalf(int z){
        if(z>=ST_N[0])return 2*ST_Y[0];
        if(z<=ST_S[3])return 2*ST_Y[3];
        for(int k=0;k<3;k++){
            if(z>=ST_N[k]&&z<=ST_S[k])return 2*ST_Y[k];
            if(z<ST_N[k]&&z>ST_S[k+1]){
                double t=(ST_N[k]-z)/(double)(ST_N[k]-ST_S[k+1]);
                return (int)Math.round(2*(ST_Y[k]+t*(ST_Y[k+1]-ST_Y[k])));
            }
        }
        return 2*ST_Y[3];
    }
    /** Whether x lies in the main street's corridor or a side street's. */
    static boolean mainStreet(int x){return x>=MAIN_X0&&x<=MAIN_X1;}
    static int[] sideStreet(int x){for(int[] s:SIDE_STREETS)if(x>=s[0]&&x<=s[1])return s;return null;}
    /** The island's shape: 1 inside its shore, falling to 0 at it. */
    static double island(int x,int z){
        double dx=(x-ISLAND_X)/(double)ISLAND_RX,dz=(z-ISLAND_Z)/(double)ISLAND_RZ;
        double wob=0.08*Math.sin(Math.atan2(dz,dx)*3+1.1)+0.05*Math.sin(Math.atan2(dz,dx)*7);
        return 1+wob-Math.sqrt(dx*dx+dz*dz);
    }
}
