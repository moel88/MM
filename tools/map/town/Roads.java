package dev.sangwoo.botandtown;

import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

/**
 * The street network, first drawn as a map of what each column is (asphalt, gutter, sidewalk, paint, path) and then
 * laid on the ground, so crossings, gutters and kerbs come out right wherever streets meet: gutters only where a
 * street meets something that is not street, kerbs where the road meets a sidewalk.
 */
final class Roads {
    static final byte NONE=0,ROAD=1,GUTTER=2,WALK=3,LINE=4,ZEBRA=5,ALLEY=6,PROM=7,PATH=8,RAMP=9;
    final byte[] map=new byte[Plan.W*Plan.D];
    private final Townscape t;private final Canvas c;
    Roads(Townscape t){this.t=t;c=t.c;}

    byte at(int x,int z){return Canvas.inPlan(x,z)?map[Canvas.idx(x,z)]:NONE;}
    boolean isRoad(int x,int z){byte b=at(x,z);return b==ROAD||b==LINE||b==ZEBRA||b==GUTTER||b==RAMP;}
    boolean any(int x,int z){return at(x,z)!=NONE;}
    void mark(int x0,int z0,int x1,int z1,byte type){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)if(Canvas.inPlan(x,z))map[Canvas.idx(x,z)]=type;
    }
    void markIfFree(int x0,int z0,int x1,int z1,byte type){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)if(Canvas.inPlan(x,z)&&map[Canvas.idx(x,z)]==NONE)map[Canvas.idx(x,z)]=type;
    }

    void plan(){
        // the coastal road from the west tunnel to the bridge and on to the east tunnel, the promenade beside it
        mark(-232,Plan.COAST_Z0,236,Plan.COAST_Z1,ROAD);
        for(int x=-232;x<=236;x++)if(Math.floorMod(x,6)<3)mark(x,49,x,49,LINE);
        mark(Plan.TOWN_X0,Plan.PROM_Z0,Plan.TOWN_X1,Plan.PROM_Z1,PROM);
        // the east-west streets of the low town
        for(int[] s:new int[][]{{Plan.S1_Z0,Plan.S1_Z1},{Plan.S2_Z0,Plan.S2_Z1},{Plan.S3_Z0,Plan.S3_Z1},{Plan.S4_Z0,Plan.S4_Z1}})
            mark(Plan.TOWN_X0+4,s[0],Plan.TOWN_X1-2,s[1],ROAD);
        // side streets: S4 to S1, two of them across the railway to the coastal road
        for(int[] s:Plan.SIDE_STREETS){
            boolean crossing=s[0]==-104;
            mark(s[0],Plan.S4_Z0,s[1],crossing?Plan.COAST_Z0:Plan.S1_Z1,ROAD);
        }
        // the main street: asphalt with a dashed centre line, sidewalks both sides, from the coast to the hill foot
        mark(-3,Plan.S4_Z0,3,Plan.COAST_Z0,ROAD);
        for(int z=Plan.S4_Z0;z<Plan.COAST_Z0;z++)if(Math.floorMod(z,6)<3)mark(0,z,0,z,LINE);
        markIfFree(Plan.MAIN_X0,Plan.S4_Z1+1,-4,Plan.RAIL_Z0-1,WALK);markIfFree(4,Plan.S4_Z1+1,Plan.MAIN_X1,Plan.RAIL_Z0-1,WALK);
        // crossings: across the main street south of S1, S2, S3; across the coastal road to the beach
        for(int z0:new int[]{Plan.S1_Z1+1,Plan.S2_Z1+1,Plan.S3_Z1+1})for(int x=-3;x<=3;x++)for(int z=z0;z<z0+3;z++)if((x&1)!=0)mark(x,z,x,z,ZEBRA);else mark(x,z,x,z,ROAD);
        for(int z=Plan.COAST_Z0;z<=Plan.COAST_Z1;z++)for(int x=-3;x<=3;x++)mark(x,z,x,z,(z&1)==0?ZEBRA:ROAD);
        // terrace streets, each following its terrace's bend
        for(int i=0;i<4;i++)for(int x=Plan.TERRACE_XW[i]+3;x<=Plan.TERRACE_XE[i]-3;x++)
            mark(x,Plan.terraceStreetZ1(i,x),x,Plan.terraceStreetZ0(i,x),ROAD);
        // gutters wherever the asphalt meets something that is not street
        byte[] copy=map.clone();
        for(int x=Plan.X0+1;x<Plan.X0+Plan.W-1;x++)for(int z=Plan.Z0+1;z<Plan.Z0+Plan.D-1;z++){
            int i=Canvas.idx(x,z);if(copy[i]!=ROAD)continue;
            if(z>=Plan.COAST_Z0&&z<=Plan.COAST_Z1&&(x<Plan.TOWN_X0||x>Plan.TOWN_X1))continue;
            for(var d:Direction.Type.HORIZONTAL){int nx=x+d.getOffsetX(),nz=z+d.getOffsetZ();if(copy[Canvas.idx(nx,nz)]==NONE){map[i]=GUTTER;break;}}
        }
    }

    /** Lays the planned streets on the ground (the ramps and stairways are laid by their own code). */
    void lay(){
        for(int x=Plan.X0;x<Plan.X0+Plan.W;x++)for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++){
            byte b=map[Canvas.idx(x,z)];if(b==NONE||b==RAMP)continue;
            int y=c.ground(x,z);if(y<Plan.SEA)continue;
            BlockState top=switch(b){
                case ROAD->Pal.asphalt(x,z);
                case LINE,ZEBRA->Blocks.WHITE_CONCRETE.getDefaultState();
                case GUTTER->Pal.gutter(x,z);
                case ALLEY->Pal.concrete(x,z);
                case PATH->((x+z)&1)==0?Blocks.STONE_BRICKS.getDefaultState():Blocks.POLISHED_ANDESITE.getDefaultState();
                default->Blocks.SMOOTH_STONE.getDefaultState();
            };
            c.set(x,y,z,top);
            for(int k=1;k<=5;k++)if(!c.air(x,y+k,z))c.set(x,y+k,z,Blocks.AIR.getDefaultState());
            if(b==WALK)c.set(x,y+1,z,Pal.pavers(x,z));
            if(b==PROM)c.set(x,y+1,z,Pal.promenade(x,z));
            // the odd manhole cover on the bigger roads
            if(b==ROAD&&Pal.hash(x,9,z)<0.0025)c.set(x,y+1,z,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,false,false));
            // gutter grates every so often
            if(b==GUTTER&&Math.floorMod(x*7+z*13,23)==0)c.set(x,y,z,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,false,true));
        }
    }
}
