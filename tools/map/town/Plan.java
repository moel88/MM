package dev.sangwoo.botandtown;

/**
 * The town's layout on 400 x 400 blocks (map v2.0, 2.49.0): Botand's seaside town rebuilt for fighting in, simple and
 * wide like Botand's first map (the user: '원래 보탠드처럼 단순한데 넓직하고 예쁘기까지'). An island town of one flat
 * level (y 64) on a grid of broad streets, twelve blocks from kerb to kerb with their sidewalks; each block between
 * them holds one thing with plenty of open ground: the square before the town hall in the middle, the park west of it,
 * the school and its sports ground east of it, the shopping street and the houses north of them, the harbour, the pine
 * grove, the sea station and the open lot by the sea south of them. A wooded hill with the shrine on top closes the
 * north; to the south the landmark bridge crosses to a little island with a lighthouse.
 *
 * <pre>
 *  z -200..-152  the hill: woods, the shrine's square (y 82) at the top of a broad stone stairway
 *  z -150..-139  North Street                         x: -183..-175 west ring road, -70..-59 West Avenue,
 *  z -138..-66   row N: houses | shops | shops, supermarket | houses       -6..6 Main Street (rows N and S),
 *  z  -65..-54   Middle Street                             59..70 East Avenue, 175..183 east ring road
 *  z  -53..32    row M: park | town hall and its square | school and its sports ground
 *  z   33..44    South Street
 *  z   45..84    row S: harbour sheds | pine grove | sea station | houses and the gas station
 *  z   85..94    the coastal road, the promenade (95..100), the sea wall (101)
 *  z  101+       the sea: the harbour basin (west), the beach, the bridge to the island (z 158..199), the open lot
 *                (east, the battle ground)
 * </pre>
 */
final class Plan {
    private Plan(){}
    /** The planned area (x X0..X0+W-1, z Z0..Z0+D-1); outside it the flat ocean of the world goes on. */
    static final int X0=-200,W=400,Z0=-200,D=400;
    /** Water surface (top water block), the town's ground (top solid block, the same everywhere), and the sea floor. */
    static final int SEA=62,COAST=64,FLOOR=50;

    /**
     * A street: its span across (a0..a1, from the outer edge of one sidewalk to the other's), along (b0..b1), and the
     * width of its sidewalk on each side (the low side first: north of an east-west street, west of a north-south one).
     */
    record Street(String name,boolean eastWest,int a0,int a1,int b0,int b1,int walkLo,int walkHi){
        Street(String name,boolean eastWest,int a0,int a1,int b0,int b1,int walk){this(name,eastWest,a0,a1,b0,b1,walk,walk);}
        boolean contains(int x,int z){int a=eastWest?z:x,b=eastWest?x:z;return a>=a0&&a<=a1&&b>=b0&&b<=b1;}
        int roadA0(){return a0+walkLo;}
        int roadA1(){return a1-walkHi;}
    }
    /** The town platform's edges: the ring roads' outer kerbs, the hill's foot, the coast. */
    static final int TOWN_X0=-183,TOWN_X1=183,TOWN_Z0=-151;
    /** The coastal road and the promenade along the sea, the sea wall's line, the first block of sea. */
    static final int COAST_Z0=85,COAST_Z1=94,PROM_Z0=95,PROM_Z1=100,PARAPET_Z=101,SEAWARD_Z=102;

    static final Street NORTH=new Street("north",true,-150,-139,TOWN_X0,TOWN_X1,2);
    static final Street MIDDLE=new Street("middle",true,-65,-54,TOWN_X0,TOWN_X1,2);
    static final Street SOUTH=new Street("south",true,33,44,TOWN_X0,TOWN_X1,2);
    /** The coastal road: its sidewalk on the town side only, the promenade on the sea side. */
    static final Street COASTAL=new Street("coastal",true,COAST_Z0,COAST_Z1,TOWN_X0,TOWN_X1,2,0);
    /** The north-south streets run from North Street's road to the coastal road's, across their sidewalks. */
    static final int NS_Z0=-148,NS_Z1=COAST_Z0+1;
    static final Street WEST=new Street("west",false,-70,-59,NS_Z0,NS_Z1,2);
    static final Street EAST=new Street("east",false,59,70,NS_Z0,NS_Z1,2);
    /** Main Street: the shopping street from the shrine's stairway to the town hall, and on from the square to the bridge. */
    static final Street MAIN_N=new Street("main",false,-6,6,NS_Z0,-56,3);
    static final Street MAIN_S=new Street("main",false,-6,6,35,PROM_Z1,3);
    /** The ring roads round the island's west and east shores (one sidewalk, on the town side). */
    static final Street RING_W=new Street("ring-west",false,TOWN_X0,-175,NS_Z0,NS_Z1,0,2);
    static final Street RING_E=new Street("ring-east",false,175,TOWN_X1,NS_Z0,NS_Z1,2,0);
    static final Street[] STREETS={NORTH,MIDDLE,SOUTH,COASTAL,WEST,EAST,MAIN_N,MAIN_S,RING_W,RING_E};

    /** The rows between the east-west streets (north edge, south edge) and the columns between the north-south ones. */
    static final int[] ROW_N={-138,-66},ROW_M={-53,32},ROW_S={45,84};
    static final int[] COL_W={-174,-71},COL_C={-58,58},COL_E={71,174};

    /** The town hall's square: the town hall along its north side, the square itself south of it. */
    static final int HALL_Z1=-36,SQUARE_Z0=-34;
    /** Where players appear: the middle of the square, looking south down the main street to the bridge. */
    static final int SPAWN_X=0,SPAWN_Z=-4;

    // the sea front
    /** The harbour: its quay (along the coastal road's south side), the breakwaters closing the basin. */
    static final int HARBOR_X0=TOWN_X0,HARBOR_X1=-71,QUAY_Z1=112;
    /** The beach under the promenade, the stepped revetment down to it. */
    static final int BEACH_X0=-58,BEACH_X1=-14;
    /** The open lot by the sea (the battle ground), reclaimed land walled against the sea. */
    static final int LOT_X0=28,LOT_X1=150,LOT_Z0=PARAPET_Z,LOT_Z1=152;
    /** The landmark bridge: its deck (x), the ends of the span (z), the pylon, the island beyond it. */
    static final int BRIDGE_X0=-6,BRIDGE_X1=6,BRIDGE_Z0=PARAPET_Z,BRIDGE_Z1=160,PYLON_Z=130;
    static final int ISLAND_X=0,ISLAND_Z=181,ISLAND_RX=34,ISLAND_RZ=21;
    /** The shrine's square on top of the hill, and its stone stairway (x) up from North Street. */
    static final int SHRINE_Y=82,SHRINE_X0=-26,SHRINE_X1=26,SHRINE_Z0=-174,SHRINE_Z1=-193;
    static final int STAIR_X0=-5,STAIR_X1=5;
    /** No far coast across the water (2.48.0, the user asked for the mountains to go); the open sea's surface out to the horizon. */
    static final float[][] BACKDROP={};
    static final float FAR_SEA=62.4f;

    /** The street at a point, or null. */
    static Street street(int x,int z){for(Street s:STREETS)if(s.contains(x,z))return s;return null;}
    /** The island's shape: 1 inside its shore, falling to 0 at it. */
    static double island(int x,int z){
        double dx=(x-ISLAND_X)/(double)ISLAND_RX,dz=(z-ISLAND_Z)/(double)ISLAND_RZ;
        double wob=0.08*Math.sin(Math.atan2(dz,dx)*3+1.1)+0.05*Math.sin(Math.atan2(dz,dx)*7);
        return 1+wob-Math.sqrt(dx*dx+dz*dz);
    }
    static boolean in(int[] r,int x,int z){return x>=r[0]&&x<=r[2]&&z>=r[1]&&z<=r[3];}
}
