package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;

/**
 * The street network, first drawn as a map of what each column is (asphalt, gutter, sidewalk, paint, path) and then
 * laid on the ground, so crossings, gutters and kerbs come out right wherever streets meet: gutters only where a
 * street meets something that is not street, sidewalks half a block above the road. Streets that slope between the
 * rows rise in half blocks, slabs on the half steps, so they read as smooth asphalt from end to end.
 */
final class Roads {
    static final byte NONE=0,ROAD=1,GUTTER=2,WALK=3,LINE=4,ZEBRA=5,ALLEY=6,PROM=7,PATH=8,RAMP=9,STOP=10,CROSS=11;
    final byte[] map=new byte[Plan.W*Plan.D];
    private final Townscape t;private final Canvas c;
    Roads(Townscape t){this.t=t;c=t.c;}

    byte at(int x,int z){return Canvas.inPlan(x,z)?map[Canvas.idx(x,z)]:NONE;}
    boolean isRoad(int x,int z){byte b=at(x,z);return b==ROAD||b==LINE||b==ZEBRA||b==GUTTER||b==RAMP||b==STOP||b==CROSS;}
    boolean any(int x,int z){return at(x,z)!=NONE;}
    void mark(int x0,int z0,int x1,int z1,byte type){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)if(Canvas.inPlan(x,z))map[Canvas.idx(x,z)]=type;
    }
    void markIfFree(int x0,int z0,int x1,int z1,byte type){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)if(Canvas.inPlan(x,z)&&map[Canvas.idx(x,z)]==NONE)map[Canvas.idx(x,z)]=type;
    }

    void plan(){
        // the coastal road from the west tunnel to the east one (the river bridge carries it over the river)
        mark(Plan.TUNNEL_W,Plan.COAST_Z0,Plan.TUNNEL_E,Plan.COAST_Z1,ROAD);
        for(int x=Plan.TUNNEL_W;x<=Plan.TUNNEL_E;x++)if(Math.floorMod(x,8)<4)mark(x,Plan.CENTRE_Z,x,Plan.CENTRE_Z,LINE);
        // the seaside promenade from the harbour to the river
        mark(Plan.HARBOR_X0,Plan.PROM_Z0,Plan.TOWN_X1+18,Plan.PROM_Z1,PROM);
        // the east-west streets of the low town
        for(int k=0;k<4;k++)mark(Plan.TOWN_X0+4,Plan.ST_N[k],Plan.TOWN_X1-2,Plan.ST_S[k],ROAD);
        // side streets: S4 to S1, one of them across the railway to the coastal road
        for(int[] s:Plan.SIDE_STREETS)mark(s[0],Plan.ST_N[3],s[1],s[0]==Plan.CROSSING_X0?Plan.COAST_Z0:Plan.ST_S[0],ROAD);
        // the main street: asphalt with a dashed centre line and sidewalks, from the hill foot to the coast and on
        // across the promenade onto the bridge
        mark(-3,Plan.ST_N[3],3,Plan.PROM_Z1,ROAD);
        for(int z=Plan.ST_N[3];z<Plan.COAST_Z0;z++)if(Math.floorMod(z,8)<4)mark(0,z,0,z,LINE);
        markIfFree(Plan.MAIN_X0,Plan.ST_S[3]+1,-4,Plan.RAIL_Z0-1,WALK);markIfFree(4,Plan.ST_S[3]+1,Plan.MAIN_X1,Plan.RAIL_Z0-1,WALK);
        // zebra crossings across the main street south of each cross street, across the coastal road west of the
        // bridge, and across the bridge's entrance for the promenade
        for(int k=0;k<3;k++){int z0=Plan.ST_S[k]+1;for(int x=-3;x<=3;x++)for(int z=z0;z<z0+3;z++)mark(x,z,x,z,(x&1)!=0?ZEBRA:ROAD);}
        for(int z=Plan.COAST_Z0;z<=Plan.COAST_Z1;z++)for(int x=-11;x<=-8;x++)mark(x,z,x,z,(z&1)==0?ZEBRA:ROAD);
        for(int x=-3;x<=3;x++)for(int z=Plan.PROM_Z0;z<=Plan.PROM_Z1;z++)mark(x,z,x,z,(x&1)!=0?ZEBRA:ROAD);
        // stop lines where the side streets and the main street meet the coastal road
        mark(-3,Plan.COAST_Z0-1,0,Plan.COAST_Z0-1,STOP);
        // terrace streets, each following its terrace's bend
        for(int i=0;i<4;i++)for(int x=Plan.TERRACE_XW[i]+3;x<=Plan.TERRACE_XE[i]-3;x++)
            mark(x,Plan.terraceStreetZ1(i,x),x,Plan.terraceStreetZ0(i,x),ROAD);
        // the level crossings are laid by the railway
        for(int[] xs:new int[][]{{Plan.MAIN_X0,Plan.MAIN_X1},{Plan.CROSSING_X0,Plan.CROSSING_X1}})mark(xs[0],Plan.RAIL_Z0,xs[1],Plan.RAIL_FENCE_Z,CROSS);
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
        return at(x,z)==WALK?p+1:p;
    }

    /** Lays the planned streets on the ground (ramps, stairways, crossings and bridges are laid by their own code). */
    void lay(){
        for(int x=Plan.X0;x<Plan.X0+Plan.W;x++)for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++){
            byte b=map[Canvas.idx(x,z)];if(b==NONE||b==RAMP||b==CROSS)continue;
            if(t.terrain.kind[Canvas.idx(x,z)]!=Terrain.LAND)continue;
            int lv=level(x,z),y=lv>>1;boolean half=(lv&1)==1;
            BlockState full,slab;
            switch(b){
                case ROAD->{full=Pal.asphalt(x,z);slab=Pal.asphaltSlab(x,z);}
                case LINE,ZEBRA,STOP->{full=Blocks.WHITE_CONCRETE.getDefaultState();slab=Canvas.slab(Blocks.SMOOTH_QUARTZ_SLAB,false);}
                case GUTTER->{full=Pal.gutter(x,z);slab=Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false);}
                case ALLEY->{full=Pal.concrete(x,z);slab=Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false);}
                case PATH->{full=((x+z)&1)==0?Blocks.STONE_BRICKS.getDefaultState():Blocks.POLISHED_ANDESITE.getDefaultState();slab=Canvas.slab(Blocks.STONE_BRICK_SLAB,false);}
                case WALK->{full=Pal.paverBlock(x,z);slab=Pal.pavers(x,z);}
                case PROM->{full=Pal.promenadeBlock(x,z);slab=Pal.promenade(x,z);}
                default->{full=Blocks.SMOOTH_STONE.getDefaultState();slab=Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false);}
            }
            // fill up to the surface from the ground below (sidewalks stand above the terrain)
            for(int yy=c.ground(x,z)+1;yy<y;yy++)c.set(x,yy,z,Blocks.STONE.getDefaultState());
            if(b==WALK||b==PROM){
                // sidewalk and promenade pavers are slabs on a full block, so their kerb stands half a block up
                if(half){c.set(x,y,z,Blocks.STONE.getDefaultState());c.set(x,y+1,z,slab);}else c.set(x,y,z,full);
            }else if(half){c.set(x,y,z,Pal.asphaltBase());c.set(x,y+1,z,slab);}
            else c.set(x,y,z,full);
            int surface=half?y+1:y;
            c.ground(x,z,surface);
            for(int k=1;k<=6;k++)if(!c.air(x,surface+k,z))c.set(x,surface+k,z,Blocks.AIR.getDefaultState());
            if(half)continue;
            // the odd manhole cover on the bigger roads, gutter grates every so often
            if(b==ROAD&&Pal.hash(x,9,z)<0.0025)c.set(x,y+1,z,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,false,false));
            if(b==GUTTER&&Math.floorMod(x*7+z*13,23)==0)c.set(x,y,z,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,false,true));
        }
    }
}
