package dev.sangwoo.botandtown;

/**
 * The hourglass island (map v3.0, "보탠드 모래시계 섬", the user's design: '모래시계처럼', a white rift where space and
 * time are strange at the waist, one giant world tree in it with glowing sky-blue leaves, and four zones of their own in
 * the two glasses). 1000 x 1000 blocks (the user's request: '1000×1000으로 시도해보고', after 2000 x 2000 proved too big),
 * the first 400 x 400 layout grown K times, the island symmetric, its zones each a little different in their ground.
 * Nothing stands higher than about y 190, under the clouds (y 192): the mountain's summit and its shrine, the world
 * tree's crown, the castle's great tower and the cyber city's towers.
 *
 * <pre>
 *            north (z -475)
 *   +---------------------------+
 *   |  MOUNTAIN   |  WALLS       |   the upper glass, higher ground: the old Japanese town with its canal and streets at
 *   |  TOWN (NW)  |  CAPITAL (NE)|   the foot of a great mountain, the shrine on its summit ('진짜 거대한 산 위에', the
 *    \            |   castle    /    island's landmark); the fantasy capital: a great castle inside high city walls
 *      \_________ RIFT ________/     the waist: the white rift, the world tree in its middle, no roads in it
 *      /          |            \
 *   |  FOREST (SW)| CYBER (SE)  |   the lower glass, lower ground: the forest, its lake and ruins over a sandy shore;
 *   |  lake, ruins| towers, neon|   the cyberpunk city (the user's photo: towers, neon, screens, wide avenues)
 *   +---------------------------+
 *            south (z 475)
 * </pre>
 * The axis road runs north and south through both glasses, stopping where the white begins (no loop roads: the user's
 * request, '거대한 동그라미 아스팔트 … 없애고'); each zone has streets of its own off it (Roads).
 */
final class Plan {
    private Plan(){}
    /** How many times the first layout (400 x 400) every length of the island has grown. */
    static final double K=2.5;
    /** A length of the first layout grown K times, in whole blocks. */
    static int k(double v){return (int)Math.round(v*K);}
    /** The planned area (x X0..X0+W-1, z Z0..Z0+D-1); outside it the flat ocean of the world goes on. */
    static final int X0=-500,W=1000,Z0=-500,D=1000;
    /** Water surface (top water block) and the sea floor's base. */
    static final int SEA=62,FLOOR=50;
    /** The rift's ground and the cyber city's (top solid block); the upper glass stands a few blocks higher. */
    static final int BASE=70;

    // ---- the glass ----
    /** Half the waist's width, half the widest part's, how far the ends reach from the middle, the outer corners' radius. */
    static final int NECK=k(44),WIDE=k(188),END=k(190),CORNER=k(58);
    /** The rift: white within RIFT of the middle; round it the waist swells to SWELL. */
    static final int RIFT=k(54),SWELL=k(46);

    // ---- zones ----
    static final int TOWN=0,CAPITAL=1,FOREST=2,CYBER=3,RIFT_ZONE=4;

    // ---- places ----
    /**
     * The great mountain at the town's far corner, the island's landmark: its summit's middle, the level of the summit
     * where the shrine stands (low enough that the shrine stays under the clouds), the summit's radius and the mountain's
     * foot (its west and north flanks fall to the sea); the snow begins about SNOW. The shrine's stairway climbs it from
     * the town to the south.
     */
    static final int MOUNT_X=-330,MOUNT_Z=-345,MOUNT_TOP=172,MOUNT_SUMMIT=24,MOUNT_R=145,MOUNT_FOOT=80;
    /** A height a fraction of the way up the mountain from its foot to its summit (the bands of its grass, rock and snow). */
    static int mountLevel(double f){return (int)Math.round(MOUNT_FOOT+(MOUNT_TOP-MOUNT_FOOT)*f);}
    static final int SNOW=mountLevel(0.76);
    static final int SHRINE_X=MOUNT_X,SHRINE_Z=MOUNT_Z,SHRINE_Y=MOUNT_TOP;
    /** The capital's walls: their middle and radius, and the angles of the gates (west to the axis road, south-west to the rift). */
    static final int WALL_X=240,WALL_Z=-305,WALL_R=155;
    static final double GATE_W=Math.PI,GATE_SW=Math.PI*0.80;
    /** The great castle on its plateau inside the walls, facing west down the main avenue: the plateau's level and radius, the castle's own (its octagon of curtain walls). */
    static final int CASTLE_X=311,CASTLE_Z=-305,CASTLE_Y=96,PLATEAU_R=56,CASTLE_R=42;
    /** The cathedral north of the main avenue, its front and square to the south. */
    static final int CATHEDRAL_X=165,CATHEDRAL_Z=-345;
    /** The forest's lake (its water's level), and the ruins near the lower glass's far corner. */
    static final int LAKE_X=-170,LAKE_Z=295,LAKE_R=38,LAKE_Y=66,RUINS_X=-375,RUINS_Z=380;
    /** The town's canal between the mountain and the axis road: x as a function of z, its pool halfway, its half width (wider at the pool). */
    static double canalX(double z){return -102+12*Math.sin(z*.022);}
    static final int CANAL_Z0=-425,CANAL_Z1=-215,POOL_Z=-320;
    static double canalHalf(double z){double u=(z-POOL_Z)/14.0;return 3.1+4.4*Math.max(0,1-u*u);}

    // ---- roads ----
    /** The axis road: its half width with its sidewalks and how far it runs; it begins on either side of the rift (where the white ends). */
    static final int ROAD_HALF=6,AXIS_END=k(172),AXIS_START=RIFT+20;
    /** The towns' own streets: their half width with the sidewalks, the capital's main avenue, and the cyber city's avenues. */
    static final int STREET_HALF=5,MAIN_HALF=7,AVENUE_HALF=10;

    // ---- the rift ----
    /** The world tree's foot in the rift's middle; where players appear, looking at it; the two merchants between. */
    static final int TREE_X=0,TREE_Z=0,SPAWN_X=0,SPAWN_Z=112,MERCHANT_Z=102;

    /** No far coast across the water: the open sea's surface out to the horizon. */
    static final float[][] BACKDROP={};
    static final float FAR_SEA=62.4f;

    static double clamp(double t){return Math.max(0,Math.min(1,t));}
    static double ss(double a,double b,double x){double t=clamp((x-a)/(b-a));return t*t*(3-2*t);}

    /** Half the glass's width at z: narrow at the waist, flaring out, drawn in a little toward the ends. */
    static double half(double z){double u=Math.abs(z)/END;return NECK+(WIDE-NECK)*ss(.06,.6,u)-22*K*ss(.78,1,u);}
    /** A wander of the shore, the same on every build: long bays and headlands, and a little roughness on them. */
    static double wobble(double x,double z){
        double X=x/K,Z=z/K;
        double big=1.1*Math.sin(X*.025+1.3)*Math.cos(Z*.028+4.1)+.7*Math.sin(X*.043+.2)*Math.cos(Z*.048+2.6)+.45*Math.sin(X*.073+5.0)*Math.cos(Z*.083+.9);
        double small=1.2*Math.sin(x*.031+2.2)*Math.cos(z*.027+.4)+.6*Math.sin(x*.067+4.4)*Math.cos(z*.071+1.9);
        return big*K+small;
    }
    /** How far a point lies outside the island's shore (negative inland). */
    static double shore(double x,double z){
        double side=Math.abs(x)-half(z),ends=Math.abs(z)-END;
        double cx=WIDE-CORNER,cz=END-CORNER;
        double corner=Math.abs(x)>cx&&Math.abs(z)>cz?Math.hypot(Math.abs(x)-cx,Math.abs(z)-cz)-CORNER:-1e9;
        double d=Math.max(Math.max(side,ends),corner)+wobble(x,z);
        return Math.min(d,Math.hypot(x,z)-SWELL);
    }
    /** How far the white reaches at a point's bearing: the rift's edge wavers. */
    static double riftEdge(double x,double z){
        double a=Math.atan2(z,x);
        return RIFT+K*(2.6*Math.sin(a*7+0.7)+1.6*Math.sin(a*13+2.1)+0.9*Math.sin(a*23+4.4))+2.5*Math.sin(a*61+1.3);
    }
    /** Whether a point lies in the white rift. */
    static boolean inRift(double x,double z){return Math.hypot(x,z)<riftEdge(x,z);}
    /** Which zone a point is in. */
    static int zone(double x,double z){
        if(inRift(x,z))return RIFT_ZONE;
        return z<0?(x<0?TOWN:CAPITAL):(x<0?FOREST:CYBER);
    }
    /** The capital's walls' radius at a bearing (a little uneven, as walls built along the ground). */
    static double wallR(double a){return WALL_R+4.5*Math.sin(a*3+0.4)+2.5*Math.sin(a*7+2.0);}
    /** How far a point lies inside the capital's walls (negative outside). */
    static double insideWalls(double x,double z){double a=Math.atan2(z-WALL_Z,x-WALL_X);return wallR(a)-Math.hypot(x-WALL_X,z-WALL_Z);}
    /** A point on the walls' line at a bearing. */
    static double[] wallAt(double a){double r=wallR(a);return new double[]{WALL_X+r*Math.cos(a),WALL_Z+r*Math.sin(a)};}
}
