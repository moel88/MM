package dev.sangwoo.botandtown;

/**
 * The hourglass island (map v3.0, "보탠드 모래시계 섬", the user's design: '모래시계처럼', a white rift where space and
 * time are strange at the waist, one giant world tree in it with glowing sky-blue leaves, and four zones of their own in
 * the two glasses). 400 x 400 blocks, the island symmetric, its zones each a little different in their ground.
 *
 * <pre>
 *            north (z -190)
 *   +---------------------------+
 *   |  TOWN (NW)  |  CAPITAL (NE)|   the upper glass, higher ground (y 72..76): the old Japanese town on terraces with
 *   |  shrine hill|  castle      |   its canal and the shrine on its hill (y 88); the fantasy capital stepping up to its
 *    \            |             /    castle on a plateau with cliffs (y 84)
 *      \_________ RIFT ________/     the waist: the white rift (y 70), the world tree in its middle, no roads in it
 *      /          |            \
 *   |  FOREST (SW)| CITY (SE)   |   the lower glass, lower ground: the forest, its lake and ruins over a sandy shore;
 *   |  lake, ruins| arena, cars |   Botand's old city as on the first map (its arena, car park and buildings), on low cliffs
 *   +---------------------------+
 *            south (z 190)
 * </pre>
 * A loop road runs round the middle of each glass and the axis road runs north and south through both, stopping where
 * the white begins.
 */
final class Plan {
    private Plan(){}
    /** The planned area (x X0..X0+W-1, z Z0..Z0+D-1); outside it the flat ocean of the world goes on. */
    static final int X0=-200,W=400,Z0=-200,D=400;
    /** Water surface (top water block) and the sea floor's base. */
    static final int SEA=62,FLOOR=50;
    /** The rift's ground and the old city's (top solid block); the upper glass stands a few blocks higher. */
    static final int BASE=70;

    // ---- the glass ----
    /** Half the waist's width, half the widest part's, how far the ends reach from the middle, the outer corners' radius. */
    static final int NECK=44,WIDE=188,END=190,CORNER=58;
    /** The rift: white within RIFT of the middle; round it the waist swells to SWELL. */
    static final int RIFT=54,SWELL=46;

    // ---- zones ----
    static final int TOWN=0,CAPITAL=1,FOREST=2,CITY=3,RIFT_ZONE=4;

    // ---- places ----
    /** The shrine on its hill at the town's far corner, its precinct's level. */
    static final int SHRINE_X=-150,SHRINE_Z=-148,SHRINE_Y=88,SHRINE_R=18;
    /** The castle on its plateau at the capital's far corner, the plateau's level and radius. */
    static final int CASTLE_X=150,CASTLE_Z=-154,CASTLE_Y=84,PLATEAU_R=27;
    /** The cathedral inside the capital's half of the loop, facing the rift. */
    static final int CATHEDRAL_X=62,CATHEDRAL_Z=-120;
    /** The forest's lake, and the ruins near the lower glass's far corner. */
    static final int LAKE_X=-68,LAKE_Z=118,LAKE_R=16,RUINS_X=-150,RUINS_Z=152;
    /** The town's canal inside the loop road: x as a function of z, its pool halfway, its half width (wider at the pool). */
    static double canalX(double z){return -70+8*Math.sin(z*.05);}
    static final int CANAL_Z0=-148,CANAL_Z1=-88,POOL_Z=-118;
    static double canalHalf(double z){double u=(z-POOL_Z)/7.0;return 2.6+3.6*Math.max(0,1-u*u);}

    // ---- roads ----
    /** The loop roads round each glass (ellipses centred on the axis) and the axis road; a road's half width with its sidewalks. */
    static final int LOOP_N_CZ=-118,LOOP_S_CZ=118,LOOP_RX=124,LOOP_RZ=54,ROAD_HALF=6,AXIS_END=172;
    /** Where the axis road begins on either side of the rift (it stops where the white begins). */
    static final int AXIS_START=RIFT+5;

    // ---- the rift ----
    /** The world tree's foot in the rift's middle; where players appear, looking at it; the two merchants between. */
    static final int TREE_X=0,TREE_Z=0,SPAWN_X=0,SPAWN_Z=46,MERCHANT_Z=39;
    /** The old city's arena (lifted from the first map), its middle: by the middle road, leaving the loop's east end for buildings. */
    static final int ARENA_X=42,ARENA_Z=118;
    /** The old city's car park (lifted from the first map), its north-west corner. */
    static final int PARKING_X=133,PARKING_Z=103;

    /** No far coast across the water: the open sea's surface out to the horizon. */
    static final float[][] BACKDROP={};
    static final float FAR_SEA=62.4f;

    static double clamp(double t){return Math.max(0,Math.min(1,t));}
    static double ss(double a,double b,double x){double t=clamp((x-a)/(b-a));return t*t*(3-2*t);}

    /** Half the glass's width at z: narrow at the waist, flaring out, drawn in a little toward the ends. */
    static double half(double z){double u=Math.abs(z)/END;return NECK+(WIDE-NECK)*ss(.06,.6,u)-22*ss(.78,1,u);}
    /** A small wander of the shore, the same on every build. */
    static double wobble(double x,double z){
        return 1.1*Math.sin(x*.025+1.3)*Math.cos(z*.028+4.1)+.7*Math.sin(x*.043+.2)*Math.cos(z*.048+2.6)+.45*Math.sin(x*.073+5.0)*Math.cos(z*.083+.9);
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
        return RIFT+2.6*Math.sin(a*7+0.7)+1.6*Math.sin(a*13+2.1)+0.9*Math.sin(a*23+4.4);
    }
    /** Whether a point lies in the white rift. */
    static boolean inRift(double x,double z){return Math.hypot(x,z)<riftEdge(x,z);}
    /** Which zone a point is in. */
    static int zone(double x,double z){
        if(inRift(x,z))return RIFT_ZONE;
        return z<0?(x<0?TOWN:CAPITAL):(x<0?FOREST:CITY);
    }
    /** The point on a loop road's centre line at angle a. */
    static double loopX(double a){return LOOP_RX*Math.cos(a);}
    static double loopZ(double cz,double a){return cz+LOOP_RZ*Math.sin(a);}
}
