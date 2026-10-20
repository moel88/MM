package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;

/**
 * The street network, first drawn as a map of what each column is (asphalt, paint, gutter, sidewalk, path) and then
 * laid on the ground, so crossings, gutters, kerbs and road paint come out right wherever streets meet: gutters only
 * where a street meets something that is not street, sidewalks of pale pavers half a block above the road, thin edge
 * lines along a kerb that stop where another street joins. Streets that slope between the rows rise a block at a
 * time, the step taken in two halves, in the same asphalt (Botand's own town blocks) from end to end.
 */
final class Roads {
    static final byte NONE=0,ROAD=1,GUTTER=2,WALK=3,EDGE=4,ZEBRA=5,ALLEY=6,PROM=7,PATH=8,RAMP=9,STOP=10,CROSS=11,CENTRE=12;
    final byte[] map=new byte[Plan.W*Plan.D];
    /** For an edge line the side it lies along (a horizontal Direction's id); for a centre line, zebra or stop line its axis (0 x, 1 z). */
    final byte[] dir=new byte[Plan.W*Plan.D];
    private final Townscape t;private final Canvas c;
    Roads(Townscape t){this.t=t;c=t.c;}

    byte at(int x,int z){return Canvas.inPlan(x,z)?map[Canvas.idx(x,z)]:NONE;}
    boolean isRoad(int x,int z){byte b=at(x,z);return b==ROAD||b==EDGE||b==CENTRE||b==ZEBRA||b==GUTTER||b==RAMP||b==STOP||b==CROSS;}
    boolean any(int x,int z){return at(x,z)!=NONE;}
    void mark(int x0,int z0,int x1,int z1,byte type){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)if(Canvas.inPlan(x,z))map[Canvas.idx(x,z)]=type;
    }
    void markIfFree(int x0,int z0,int x1,int z1,byte type){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)if(Canvas.inPlan(x,z)&&map[Canvas.idx(x,z)]==NONE)map[Canvas.idx(x,z)]=type;
    }
    private void paint(int x,int z,byte type,int d){if(Canvas.inPlan(x,z)&&map[Canvas.idx(x,z)]==ROAD){map[Canvas.idx(x,z)]=type;dir[Canvas.idx(x,z)]=(byte)d;}}
    /** An edge line along the kerb side of a road column, where the column beyond it is not street. */
    private void edge(int x,int z,Direction kerb){
        byte beyond=at(x+kerb.getOffsetX(),z+kerb.getOffsetZ());
        if(beyond!=ROAD&&beyond!=CROSS&&beyond!=EDGE&&beyond!=CENTRE)paint(x,z,EDGE,kerb.getHorizontal());
    }

    void plan(){
        // the coastal road from the west tunnel to the east one
        mark(Plan.TUNNEL_W,Plan.COAST_Z0,Plan.TUNNEL_E,Plan.COAST_Z1,ROAD);
        // the seaside promenade from the harbour to the lot
        mark(Plan.HARBOR_X0,Plan.PROM_Z0,Plan.LOT_X0-2,Plan.PROM_Z1,PROM);
        // the east-west streets of the low town
        for(int k=0;k<4;k++)mark(Plan.TOWN_X0+4,Plan.ST_N[k],Plan.TOWN_X1-2,Plan.ST_S[k],ROAD);
        // alleys from S4 to S1
        for(int[] s:Plan.SIDE_STREETS)mark(s[0],Plan.ST_N[3],s[1],Plan.ST_S[0],ROAD);
        // the main street, from the hill foot to the coast and on across the promenade onto the bridge
        mark(-3,Plan.ST_N[3],3,Plan.PROM_Z1,ROAD);
        markIfFree(Plan.MAIN_X0,Plan.ST_S[3]+1,-4,Plan.RAIL_Z0-1,WALK);markIfFree(4,Plan.ST_S[3]+1,Plan.MAIN_X1,Plan.RAIL_Z0-1,WALK);
        // the photo street: two lanes from the hill foot across the railway to the coastal road, pavers either side
        mark(Plan.PHOTO_ROAD_X0,Plan.ST_N[3],Plan.PHOTO_ROAD_X1,Plan.COAST_Z0,ROAD);
        markIfFree(Plan.PHOTO_X0,Plan.ST_S[3]+1,Plan.PHOTO_ROAD_X0-1,Plan.RAIL_Z0-1,WALK);markIfFree(Plan.PHOTO_ROAD_X1+1,Plan.ST_S[3]+1,Plan.PHOTO_X1,Plan.RAIL_Z0-1,WALK);
        // terrace streets, each following its terrace's bend
        for(int i=0;i<Plan.TERRACE_Y.length;i++)for(int x=Plan.TERRACE_XW[i]+3;x<=Plan.TERRACE_XE[i]-3;x++)
            mark(x,Plan.terraceStreetZ1(i,x),x,Plan.terraceStreetZ0(i,x),ROAD);
        // the level crossings are laid by the railway
        for(int[] xs:Plan.CROSSINGS)mark(xs[0],Plan.RAIL_Z0,xs[1],Plan.RAIL_FENCE_Z,CROSS);
        // road paint: edge lines along the kerbs of the photo street, the main street, the streets of the low town
        // and the coastal road, broken where streets join
        for(int z=Plan.ST_N[3];z<=Plan.COAST_Z1;z++){
            edge(Plan.PHOTO_ROAD_X0,z,Direction.WEST);edge(Plan.PHOTO_ROAD_X1,z,Direction.EAST);
            edge(-3,z,Direction.WEST);edge(3,z,Direction.EAST);
        }
        for(int x=Plan.TUNNEL_W;x<=Plan.TUNNEL_E;x++){
            for(int k=0;k<4;k++){edge(x,Plan.ST_N[k],Direction.NORTH);edge(x,Plan.ST_S[k],Direction.SOUTH);}
            edge(x,Plan.COAST_Z0,Direction.NORTH);edge(x,Plan.COAST_Z1,Direction.SOUTH);
        }
        // dashed centre lines: the coastal road, the main street, the photo street (from a little past the photo's
        // zebra crossing; its centre lies between x -70 and -69, painted as an edge line on the east side of x -70)
        for(int x=Plan.TUNNEL_W;x<=Plan.TUNNEL_E;x++)if(Math.floorMod(x,10)<5)paint(x,Plan.CENTRE_Z,CENTRE,0);
        for(int z=Plan.ST_N[3];z<Plan.COAST_Z0;z++)if(Math.floorMod(z,10)<5)paint(0,z,CENTRE,1);
        for(int z=(int)Plan.PHOTO1_Z+12;z<Plan.RAIL_Z0;z++)if(Math.floorMod(z,10)<5)paint(-70,z,EDGE,Direction.EAST.getHorizontal());
        // zebra crossings: on the photo street just in front of photo 1's camera, across the main street south of each
        // cross street, across the coastal road west of the bridge and across the bridge's entrance for the promenade
        int zebra=(int)Math.floor(Plan.PHOTO1_Z)+6;
        for(int x=Plan.PHOTO_ROAD_X0;x<=Plan.PHOTO_ROAD_X1;x++)for(int z=zebra;z<=zebra+2;z++)paint(x,z,ZEBRA,1);
        for(int k=0;k<3;k++)for(int x=-3;x<=3;x++)for(int z=Plan.ST_S[k]+1;z<Plan.ST_S[k]+4;z++)paint(x,z,ZEBRA,1);
        for(int z=Plan.COAST_Z0;z<=Plan.COAST_Z1;z++)for(int x=-11;x<=-8;x++)paint(x,z,ZEBRA,0);
        for(int x=-3;x<=3;x++)for(int z=Plan.PROM_Z0;z<=Plan.PROM_Z1;z++)paint(x,z,ZEBRA,1);
        // stop lines before the level crossings, across the lanes coming down to them (traffic keeps left: the east lanes)
        for(int x=-69;x<=Plan.PHOTO_ROAD_X1;x++)paint(x,Plan.RAIL_Z0-2,STOP,0);
        for(int x=1;x<=3;x++)paint(x,Plan.RAIL_Z0-2,STOP,0);
        // gutters wherever the asphalt meets something that is not street
        byte[] copy=map.clone();
        for(int x=Plan.X0+1;x<Plan.X0+Plan.W-1;x++)for(int z=Plan.Z0+1;z<Plan.Z0+Plan.D-1;z++){
            int i=Canvas.idx(x,z);if(copy[i]!=ROAD)continue;
            if(z>=Plan.COAST_Z0&&z<=Plan.COAST_Z1&&(x<Plan.TOWN_X0||x>Plan.TOWN_X1))continue;
            for(var d:Direction.Type.HORIZONTAL){int nx=x+d.getOffsetX(),nz=z+d.getOffsetZ();if(copy[Canvas.idx(nx,nz)]==NONE){map[i]=GUTTER;break;}}
        }
    }

    /** The surface level of a planned column in half blocks: the ground's, a sidewalk half a block above it. */
    int level(int x,int z){
        int p=t.terrain.plat[Canvas.idx(x,z)];
        if(p==Terrain.NONE)p=2*c.ground(x,z);
        return at(x,z)==WALK||at(x,z)==PROM?p+1:p;
    }

    /** Lays the planned streets on the ground (ramps, stairways, crossings and bridges are laid by their own code). */
    void lay(){
        for(int x=Plan.X0;x<Plan.X0+Plan.W;x++)for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++){
            int i=Canvas.idx(x,z);
            byte b=map[i];if(b==NONE||b==RAMP||b==CROSS)continue;
            if(t.terrain.kind[i]!=Terrain.LAND)continue;
            int lv=level(x,z),y=lv>>1;boolean half=(lv&1)==1;
            Direction side=Direction.fromHorizontal(dir[i]);Direction.Axis axis=dir[i]==0?Direction.Axis.X:Direction.Axis.Z;
            BlockState full,slab;
            switch(b){
                case ROAD->{full=TB.ASPHALT;slab=TB.asphaltSlab();}
                case EDGE->{full=TB.edgeLine(side,false);slab=TB.edgeLine(side,true);}
                case CENTRE->{full=TB.painted("asphalt_center_line",axis);slab=TB.asphaltSlab();}
                case ZEBRA->{full=TB.painted("asphalt_zebra",axis);slab=TB.asphaltSlab();}
                case STOP->{full=TB.painted("asphalt_stop_line",axis);slab=TB.asphaltSlab();}
                case GUTTER->{full=Pal.gutter(x,z);slab=Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false);}
                case ALLEY->{full=Pal.concrete(x,z);slab=Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false);}
                case PATH->{full=((x+z)&1)==0?Blocks.STONE_BRICKS.getDefaultState():Blocks.POLISHED_ANDESITE.getDefaultState();slab=Canvas.slab(Blocks.STONE_BRICK_SLAB,false);}
                case WALK->{full=TB.PAVING;slab=TB.pavingSlab();}
                case PROM->{full=Pal.promenadeBlock(x,z);slab=Pal.promenade(x,z);}
                default->{full=Blocks.SMOOTH_STONE.getDefaultState();slab=Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false);}
            }
            // fill up to the surface from the ground below (sidewalks stand above the terrain)
            for(int yy=c.ground(x,z)+1;yy<y;yy++)c.set(x,yy,z,Blocks.STONE.getDefaultState());
            if(b==WALK||b==PROM){
                // sidewalk and promenade pavers are slabs on a full block, so their kerb stands half a block up
                if(half){c.set(x,y,z,Blocks.STONE.getDefaultState());c.set(x,y+1,z,slab);}else c.set(x,y,z,full);
            }else if(half){c.set(x,y,z,b==GUTTER||b==ALLEY||b==PATH?Blocks.STONE.getDefaultState():TB.ASPHALT);c.set(x,y+1,z,slab);}
            else c.set(x,y,z,full);
            int surface=half?y+1:y;
            c.ground(x,z,surface);
            for(int k=1;k<=6;k++)if(!c.air(x,surface+k,z))c.set(x,surface+k,z,Blocks.AIR.getDefaultState());
            if(half)continue;
            // the odd manhole cover, gutter grates every so often
            if(b==ROAD&&Pal.hash(x,9,z)<0.0025)c.set(x,y+1,z,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,false,false));
            if(b==GUTTER&&Math.floorMod(x*7+z*13,23)==0)c.set(x,y,z,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,false,true));
        }
    }
}
