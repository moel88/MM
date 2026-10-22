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
    static final byte NONE=0,ROAD=1,GUTTER=2,WALK=3,EDGE=4,ZEBRA=5,ALLEY=6,PROM=7,PATH=8,RAMP=9,STOP=10,CROSS=11,CENTRE=12,GREEN=13;
    final byte[] map=new byte[Plan.W*Plan.D];
    /** For an edge line the side it lies along (a horizontal Direction's id); for a centre line, zebra or stop line its axis (0 x, 1 z). */
    final byte[] dir=new byte[Plan.W*Plan.D];
    private final Townscape t;private final Canvas c;
    Roads(Townscape t){this.t=t;c=t.c;}

    byte at(int x,int z){return Canvas.inPlan(x,z)?map[Canvas.idx(x,z)]:NONE;}
    boolean isRoad(int x,int z){byte b=at(x,z);return b==ROAD||b==EDGE||b==CENTRE||b==ZEBRA||b==GUTTER||b==RAMP||b==STOP||b==CROSS||b==GREEN;}
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
        // sidewalks first, the seaside promenade, then every road over them: where streets meet, the roads run on
        // across the other street's sidewalks and the corners keep theirs
        for(Plan.Street s:Plan.STREETS){
            if(s.walkLo()>0)markStreet(s,s.a0(),s.a0()+s.walkLo()-1,WALK);
            if(s.walkHi()>0)markStreet(s,s.a1()-s.walkHi()+1,s.a1(),WALK);
        }
        mark(Plan.HARBOR_X1+1,Plan.PROM_Z0,Plan.TOWN_X1,Plan.PROM_Z1,PROM);
        for(Plan.Street s:Plan.STREETS)markStreet(s,s.roadA0(),s.roadA1(),ROAD);
        // road paint: edge lines along every kerb, broken where streets join
        for(Plan.Street s:Plan.STREETS)for(int b=s.b0();b<=s.b1();b++){
            if(s.eastWest()){edge(b,s.roadA0(),Direction.NORTH);edge(b,s.roadA1(),Direction.SOUTH);}
            else{edge(s.roadA0(),b,Direction.WEST);edge(s.roadA1(),b,Direction.EAST);}
        }
        // dashed centre lines: the main street's through the middle of its middle lane, the eight-wide roads' between
        // their two lanes (a thin line along one side of the lanes' last blocks), not across junctions
        for(Plan.Street s:Plan.STREETS){
            int w=s.roadA1()-s.roadA0()+1;
            for(int b=s.b0();b<=s.b1();b++){
                if(Math.floorMod(b,10)>=5)continue;
                int a=s.roadA0()+(w-1)/2;
                int x=s.eastWest()?b:a,z=s.eastWest()?a:b;
                if(junction(s,x,z))continue;
                if(w%2==1)paint(x,z,CENTRE,s.eastWest()?0:1);
                else paint(x,z,EDGE,(s.eastWest()?Direction.SOUTH:Direction.EAST).getHorizontal());
            }
        }
        // zebra crossings across every arm of the grid's junctions, just outside them, and across the main street where
        // the promenade crosses it onto the bridge
        Plan.Street[] ns={Plan.WEST,Plan.MAIN_N,Plan.MAIN_S,Plan.EAST},ew={Plan.NORTH,Plan.MIDDLE,Plan.SOUTH,Plan.COASTAL};
        for(Plan.Street v:ns)for(Plan.Street h:ew){
            if(h.a0()>v.b1()||h.a1()<v.b0())continue;
            for(int x=v.roadA0();x<=v.roadA1();x++){
                for(int k=1;k<=3;k++){
                    if(h.a0()-k>=v.b0())paint(x,h.a0()-k,ZEBRA,1);
                    if(h.a1()+k<=v.b1())paint(x,h.a1()+k,ZEBRA,1);
                }
            }
            for(int z=h.roadA0();z<=h.roadA1();z++)for(int k=1;k<=3;k++){
                paint(v.a0()-k,z,ZEBRA,0);paint(v.a1()+k,z,ZEBRA,0);
            }
        }
        for(int x=Plan.MAIN_S.roadA0();x<=Plan.MAIN_S.roadA1();x++)for(int z=Plan.PROM_Z0+1;z<=Plan.PROM_Z1-1;z++)paint(x,z,ZEBRA,1);
        // gutters wherever the asphalt meets something that is not street
        byte[] copy=map.clone();
        for(int x=Plan.X0+1;x<Plan.X0+Plan.W-1;x++)for(int z=Plan.Z0+1;z<Plan.Z0+Plan.D-1;z++){
            int i=Canvas.idx(x,z);if(copy[i]!=ROAD)continue;
            for(var d:Direction.Type.HORIZONTAL){int nx=x+d.getOffsetX(),nz=z+d.getOffsetZ();if(copy[Canvas.idx(nx,nz)]==NONE){map[i]=GUTTER;break;}}
        }
    }
    /** Marks a band of a street, across from a to b, along its whole length. */
    private void markStreet(Plan.Street s,int a,int b,byte type){
        if(s.eastWest())mark(s.b0(),a,s.b1(),b,type);else mark(a,s.b0(),b,s.b1(),type);
    }
    /** Whether a road column of street s lies where another street's road crosses it. */
    private boolean junction(Plan.Street s,int x,int z){
        for(Plan.Street o:Plan.STREETS){
            if(o==s||o.eastWest()==s.eastWest())continue;
            int a=o.eastWest()?z:x;
            int b=o.eastWest()?x:z;
            if(a>=o.a0()-1&&a<=o.a1()+1&&b>=o.b0()&&b<=o.b1())return true;
        }
        return false;
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
                case GREEN->{full=TB.green(side);slab=TB.asphaltSlab();}
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
