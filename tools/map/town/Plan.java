package dev.sangwoo.botandtown;

/**
 * The town's layout on 400 x 400 blocks: a Japanese seaside town facing the sea to the south (+z), built so that two
 * places in it look like the two photos the user sent (tools/map/town/PHOTO_SCENES.md). The low town climbs gently from
 * the coast (y 64) to the hill foot (y 72) in rows held by low stone walls; two terraces climb behind it to a small
 * shrine under a wooded ridge. A two-lane street (photo 1) runs straight down from the hill foot, over the railway and
 * on to a guardrail above the sea, with a steep islet across the water at its end. The main street of
 * shops runs down the middle and on over the sea as the landmark bridge. A fishing harbour lies to the west, a beach
 * between the two streets, and the old lot by the sea (photo 2, the battle ground) to the east at the foot of a steep
 * hill whose west face is covered in houses. The whole is an island in the flat ocean of the world.
 *
 * <pre>
 *  z -200..-184  ridge (forest), falling to the sea at the map's edge
 *  z -183..-159  shrine (y 92), its stone steps up from T2
 *  z -146..-117  terrace T2 (y 84)        ┐ houses, a street through each terrace, the main stairway up the middle,
 *  z -116..-87   terrace T1 (y 78)        ┘ ramps for cars
 *  z  -86..-81   street S4 (y 72)
 *  z  -80..-47   two rows of houses (y 72 / 69)
 *  z  -46..-41   street S3 (y 69)                main street x -5..5 (shops), the photo street x -73..-66,
 *  z  -40..-7    two rows (y 69 / 66)             alleys between, all sloping evenly between the streets
 *  z   -6..-1    street S2 (y 66)
 *  z    0..17    one row (y 64)
 *  z   18..23    street S1, the station street (y 64)
 *  z   24..36    station square and buildings along S1
 *  z   37..45    railway (platform 37..39, track 41)
 *  z   46..52    coastal road (a dashed centre line at 49)
 *  z   53..55    seaside promenade; parapet at 56
 *  z   57+       sea: harbour (x -176..-100), beach (x -58..-12), the bridge (x -6..6) to the island (z 130..186),
 *                the islet (x -83..-49, z 135..165), the lot (x 20..100, z 53..118) under the east hill (x 101+),
 *                behind the lot's sea wall two strips of shore with old houses and a run-down block of flats
 * </pre>
 */
final class Plan {
    private Plan(){}
    /** The planned area (x X0..X0+W-1, z Z0..Z0+D-1); outside it the flat ocean of the world goes on. */
    static final int X0=-200,W=400,Z0=-200,D=400;
    /** Water surface (top water block), the coast's ground (top solid block), and the sea floor. */
    static final int SEA=62,COAST=64,FLOOR=50;

    // the four east-west streets of the low town, S1 (station street) to S4 (hill foot): north edge, south edge, level
    static final int[] ST_N={18,-6,-46,-86},ST_S={23,-1,-41,-81},ST_Y={64,66,69,72};
    /** Rows of lots between the streets: north edge, south edge, level, whether the row fronts the street to its south. */
    static final int[][] ROWS={{24,36,64,0},{0,17,64,1},{-23,-7,66,1},{-40,-24,69,0},{-63,-47,69,1},{-80,-64,72,0}};
    /** The low town's west and east ends (the east hill rises beyond). */
    static final int TOWN_X0=-176,TOWN_X1=100;
    /** Main street: asphalt x -3..3, sidewalks to ±5. */
    static final int MAIN_X0=-5,MAIN_X1=5;
    /**
     * The photo street (photo 1), as narrow as the photo's: a one-paver sidewalk x -73, a two-lane road x -72..-67
     * with an edge line along each kerb and a dashed centre line, a sidewalk x -66 with the poles on it. It runs from
     * S4 down across the railway to the coastal road.
     */
    static final int PHOTO_X0=-73,PHOTO_X1=-66,PHOTO_ROAD_X0=-72,PHOTO_ROAD_X1=-67;
    /** Alleys (x0..x1, 5 wide) from S4 to S1. */
    static final int[][] SIDE_STREETS={{-152,-148},{-114,-110},{-38,-34},{38,42},{72,76}};
    /** Level crossings over the railway: the photo street and the main street. */
    static final int[][] CROSSINGS={{PHOTO_ROAD_X0,PHOTO_ROAD_X1},{-3,3}};
    /**
     * Where photo 1 is taken from (measured from the photo): standing in the road's west lane two metres in from its
     * edge line between S4 and S3, turned eight degrees east of south and looking a little up, the zebra crossing
     * six to nine metres ahead...
     */
    static final double PHOTO1_X=-69.8,PHOTO1_Z=-70.5,PHOTO1_YAW=-8,PHOTO1_PITCH=-1.7;
    /** ...and photo 2: in the lot, eighteen metres short of its sea wall, the shed ahead on the left, looking south and down. */
    static final double PHOTO2_X=84.5,PHOTO2_Z=100.5,PHOTO2_YAW=0,PHOTO2_PITCH=6;

    // the railway and the coast
    static final int RAIL_Z0=37,PLATFORM_Z1=39,TRACK_Z=41,RAIL_FENCE_Z=45;
    static final int COAST_Z0=46,COAST_Z1=52,CENTRE_Z=49,PROM_Z0=53,PROM_Z1=55,PARAPET_Z=56,SEAWARD_Z=57;
    /** The station: building and platform. */
    static final int STATION_X0=12,STATION_X1=34,PLATFORM_X0=8,PLATFORM_X1=58;
    /** Where the coastal road and the railway go into the hills. */
    static final int TUNNEL_W=-186,TUNNEL_E=106;

    /** Terraces: surface y, south edge z (the wall line) and north edge, before the curve; west and east ends. */
    static final int[] TERRACE_Y={78,84};
    static final int[] TERRACE_Z0={-87,-117};
    static final int[] TERRACE_Z1={-116,-146};
    static final int[] TERRACE_XW={-170,-164},TERRACE_XE={98,92};
    /** Each terrace's street (relative to its south edge, toward -z): 16..19 blocks in. */
    static final int TERRACE_STREET_IN=16,TERRACE_STREET_W=4;
    /** The shrine platform. */
    static final int SHRINE_Y=92,SHRINE_X0=-16,SHRINE_X1=16,SHRINE_Z0=-159,SHRINE_Z1=-183;
    /** The main stairway's corridor (x); the ramps for cars (x0, x1, from level (-1 the hill foot), to terrace). */
    static final int STAIR_X0=-4,STAIR_X1=4;
    static final int[][] RAMPS={{-133,-127,-1,0},{54,60,0,1}};

    // the sea front
    static final int HARBOR_X0=-176,HARBOR_X1=-100,QUAY_Z1=68;
    static final int BEACH_X0=-58,BEACH_X1=-12;
    static final int LOT_X0=20,LOT_X1=100,LOT_Z0=PROM_Z0,LOT_Z1=118;
    /** Behind the lot's sea wall, a step down: the strips of shore with the old houses (east) and the block of flats (west). */
    static final int[] BACK_E={78,119,100,140},BACK_W={36,119,67,134};
    static final int BACK_Y=63;
    /** The landmark bridge: its deck (x), the ends of the span (z), the pylon, the island beyond it. */
    static final int BRIDGE_X0=-6,BRIDGE_X1=6,BRIDGE_Z0=53,BRIDGE_Z1=128,PYLON_Z=90;
    static final int ISLAND_X=0,ISLAND_Z=158,ISLAND_RX=34,ISLAND_RZ=28;
    /** The islet out at sea at the end of the photo street, the hill across the water in photo 1. */
    static final int ISLET_X=-66,ISLET_Z=150,ISLET_RX=17,ISLET_RZ=15;
    /** The foot of the east hill, whose west face carries the hillside houses above the lot. */
    static final int EAST_FOOT_X=101;

    /** How far north (0..-5) the terraces bend at this x: the hill is not a ruler, and never crowds street S4. */
    static int bend(int x){
        // straight across the stairway and the ramps, so their flights meet each wall square on
        if(x>=STAIR_X0-2&&x<=STAIR_X1+2)x=0;
        for(int[] r:RAMPS)if(x>=r[0]-2&&x<=r[1]+2)x=(r[0]+r[1])/2;
        return (int)Math.round(-2.5-1.5*Math.sin(x/47.0)-1.0*Math.sin(x/19.0+1.3));
    }

    /** Which terrace a point lies on, or -1. */
    static int terrace(int x,int z){
        int b=bend(x);
        for(int i=0;i<TERRACE_Y.length;i++)if(z<=TERRACE_Z0[i]+b&&z>=TERRACE_Z1[i]+b&&x>=TERRACE_XW[i]&&x<=TERRACE_XE[i])return i;
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
     * The level of a north-south street at z, in half blocks (2y, plus one where a slab tops it). The street rises in
     * whole blocks, each step taken in two halves: a single row of slabs just below each new level, so the slope reads
     * as one long road and not as bands of two pavings.
     */
    static int slopeHalf(int z){
        int here=slopeLevel(z),up=slopeLevel(z-1);
        return up>here?2*here+1:2*here;
    }
    /**
     * The whole-block level of a north-south street at z: along each east-west street, rising evenly between them;
     * the coast's level south of S1, S4's north of it.
     */
    static int slopeLevel(int z){
        if(z>=ST_N[0])return ST_Y[0];
        if(z<=ST_S[3])return ST_Y[3];
        for(int k=0;k<3;k++){
            if(z>=ST_N[k]&&z<=ST_S[k])return ST_Y[k];
            if(z<ST_N[k]&&z>ST_S[k+1]){
                double t=(ST_N[k]-z)/(double)(ST_N[k]-ST_S[k+1]);
                return (int)Math.round(ST_Y[k]+t*(ST_Y[k+1]-ST_Y[k]));
            }
        }
        return ST_Y[3];
    }
    /** Whether x lies in the main street's corridor, the photo street's or an alley's. */
    static boolean mainStreet(int x){return x>=MAIN_X0&&x<=MAIN_X1;}
    static boolean photoStreet(int x){return x>=PHOTO_X0&&x<=PHOTO_X1;}
    static int[] sideStreet(int x){for(int[] s:SIDE_STREETS)if(x>=s[0]&&x<=s[1])return s;return null;}
    /** The island's shape: 1 inside its shore, falling to 0 at it. */
    static double island(int x,int z){
        double dx=(x-ISLAND_X)/(double)ISLAND_RX,dz=(z-ISLAND_Z)/(double)ISLAND_RZ;
        double wob=0.08*Math.sin(Math.atan2(dz,dx)*3+1.1)+0.05*Math.sin(Math.atan2(dz,dx)*7);
        return 1+wob-Math.sqrt(dx*dx+dz*dz);
    }
    static double islet(int x,int z){
        double dx=(x-ISLET_X)/(double)ISLET_RX,dz=(z-ISLET_Z)/(double)ISLET_RZ;
        double wob=0.07*Math.sin(Math.atan2(dz,dx)*4+0.4)+0.04*Math.sin(Math.atan2(dz,dx)*9+2);
        return 1+wob-Math.sqrt(dx*dx+dz*dz);
    }
    static boolean in(int[] r,int x,int z){return x>=r[0]&&x<=r[2]&&z>=r[1]&&z<=r[3];}
}
