package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * The sea front (map v2.0): the concrete sea wall under the promenade with a white pipe guardrail along its kerb, the
 * broad stepped revetment down to the beach (階段護岸), wave-breaking tetrapods heaped along the walls, the beach itself
 * (a rowboat drawn up, driftwood, beach umbrellas, sea grass in the shallows), benches facing the sea, and the white
 * guardrail along the ring roads' outer edge where the land falls to the rocks.
 */
final class Coast {
    private final Townscape t;private final Canvas c;private final Random rnd;
    int tetrapods;
    Coast(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    boolean lotX(int x){return x>=Plan.LOT_X0&&x<=Plan.LOT_X1;}
    boolean harborX(int x){return x>=Plan.HARBOR_X0&&x<=Plan.HARBOR_X1;}
    boolean bridgeX(int x){return x>=Plan.BRIDGE_X0-1&&x<=Plan.BRIDGE_X1+1;}

    void build(){
        int z=Plan.PARAPET_Z;
        for(int x=Plan.HARBOR_X1+1;x<=Plan.TOWN_X1;x++){
            // the sea wall: concrete from the sea floor up under the promenade's edge
            if(lotX(x)||bridgeX(x))continue;
            for(int y=Canvas.Y0;y<=Plan.COAST;y++)c.set(x,y,z,Pal.seawall(x,y,z));
            if(Terrain.beachX(x)){steps(x);continue;}
            // a low concrete kerb and the guardrail on it, a white pipe post every few metres
            c.set(x,Plan.COAST+1,z,Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
            c.set(x,Plan.COAST+2,z,Math.floorMod(x,3)==0?TB.PIPE:TB.GUARDRAIL);
            c.ground(x,z,Plan.COAST+1);
            if(c.water(x,Plan.SEA,z+1))heap(x,z+1,3);
        }
        // the ends of the beach: short walls down to the sand
        for(int x:new int[]{Plan.BEACH_X0-1,Plan.BEACH_X1+1})for(int zz=Plan.SEAWARD_Z;zz<=Plan.SEAWARD_Z+4;zz++)
            for(int y=Canvas.Y0;y<=Plan.COAST;y++)c.set(x,y,zz,Pal.seawall(x,y,zz));
        // the guardrail along the ring roads' outer edges, on the bank just outside the road
        for(int zz=Plan.NS_Z0;zz<=Plan.COAST_Z1;zz++)for(int x:new int[]{Plan.TOWN_X0-1,Plan.TOWN_X1+1}){
            int g=c.ground(x,zz);
            if(g<Plan.COAST-1||!c.air(x,g+1,zz))continue;
            c.set(x,g+1,zz,Math.floorMod(zz,4)==0?TB.PIPE:TB.GUARDRAIL);
        }
        benches();
        beach();
        Town.log("coast: "+tetrapods+" tetrapods");
    }

    /** The stepped revetment: wide concrete steps from the promenade down to the sand. */
    private void steps(int x){
        int z=Plan.PARAPET_Z;
        c.set(x,Plan.COAST,z,Canvas.stairs(Blocks.POLISHED_ANDESITE_STAIRS,Direction.NORTH,false));
        c.set(x,Plan.COAST-1,z,Pal.seawall(x,Plan.COAST-1,z));
        c.set(x,Plan.COAST-1,z+1,Canvas.stairs(Blocks.POLISHED_ANDESITE_STAIRS,Direction.NORTH,false));
        for(int y=Canvas.Y0;y<Plan.COAST-1;y++)c.set(x,y,z+1,Pal.seawall(x,y,z+1));
        c.clear(x,Plan.COAST,z+1,x,Plan.COAST+3,z+1);
        c.ground(x,z,Plan.COAST);c.ground(x,z+1,Plan.COAST-1);
    }

    /** A heap of tetrapods in front of a wall, {@code deep} blocks out to sea. */
    void heap(int x,int z,int deep){
        if(rnd.nextInt(2)!=0)return;
        for(int k=0;k<deep;k++){
            int zz=z+k+rnd.nextInt(2);
            int floor=c.ground(x,zz);
            int top=Plan.SEA+1-k;
            for(int y=floor+1;y<=top;y+=2)tetrapod(x+rnd.nextInt(3)-1,y,zz);
        }
    }
    /** One tetrapod: a heart with three or four legs reaching out. */
    void tetrapod(int x,int y,int z){
        BlockState[] m={Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(),Blocks.SMOOTH_STONE.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.ANDESITE.getDefaultState()};
        BlockState s=m[rnd.nextInt(m.length)];
        put(x,y,z,s);
        int[][] legs={{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
        for(int[] l:legs)if(rnd.nextInt(3)>0)put(x+l[0],y+l[1],z+l[2],rnd.nextInt(6)==0?Blocks.MOSSY_COBBLESTONE.getDefaultState():s);
        tetrapods++;
    }
    private void put(int x,int y,int z,BlockState s){if(c.open(x,y,z)&&y<=Plan.SEA+2)c.set(x,y,z,s);}

    /** Benches along the promenade facing the sea, a vending machine pair near the beach steps. */
    private void benches(){
        for(int x=Plan.HARBOR_X1+10;x<Plan.TOWN_X1-4;x+=13){
            if(bridgeX(x)||bridgeX(x+1)||Math.floorMod(x+4,22)<3)continue;
            int z=Plan.PROM_Z1,g=c.ground(x,z);
            if(!c.air(x,g+1,z)||!c.air(x+1,g+1,z))continue;
            c.set(x,g+1,z,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.NORTH,false));c.set(x+1,g+1,z,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.NORTH,false));
        }
        t.street.vending(Plan.BEACH_X0+8,Plan.PROM_Z0,Direction.EAST,2);
    }

    /** The beach: a rowboat drawn up on the sand, driftwood, two beach umbrellas, sea grass and the odd rock in the shallows. */
    private void beach(){
        for(int x=Plan.BEACH_X0;x<=Plan.BEACH_X1;x++)for(int z=Plan.SEAWARD_Z+2;z<Plan.SEAWARD_Z+30;z++){
            int g=c.ground(x,z);
            if(c.water(x,g+1,z)){
                if(rnd.nextInt(9)==0)c.set(x,g+1,z,Blocks.SEAGRASS.getDefaultState());
                else if(rnd.nextInt(90)==0)c.set(x,g+1,z,Blocks.STONE.getDefaultState());
            }else if(c.air(x,g+1,z)&&rnd.nextInt(140)==0)c.set(x,g+1,z,Canvas.axis(Blocks.STRIPPED_OAK_LOG,rnd.nextBoolean()?Direction.Axis.X:Direction.Axis.Z));
        }
        // a small wooden rowboat on the sand, its bow to the sea
        int bx=Plan.BEACH_X0+14,bz=Plan.SEAWARD_Z+4,g=c.ground(bx,bz);
        for(int z=bz;z<bz+5;z++)for(int x=bx;x<bx+2;x++){
            boolean end=z==bz||z==bz+4;
            c.set(x,g+1,z,end?Canvas.slab(Blocks.SPRUCE_SLAB,false):Blocks.SPRUCE_PLANKS.getDefaultState());
        }
        c.set(bx,g+1,bz+2,Canvas.slab(Blocks.SPRUCE_SLAB,false));c.set(bx+1,g+1,bz+2,Canvas.slab(Blocks.SPRUCE_SLAB,false));
        // beach umbrellas: a pole and a canopy of white and sky-blue cloth
        for(int[] u:new int[][]{{Plan.BEACH_X0+28,Plan.SEAWARD_Z+3},{Plan.BEACH_X1-6,Plan.SEAWARD_Z+5}}){
            int ux=u[0],uz=u[1],ug=c.ground(ux,uz);
            for(int y=ug+1;y<=ug+3;y++)c.set(ux,y,uz,Blocks.BIRCH_FENCE.getDefaultState());
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)c.set(ux+dx,ug+4,uz+dz,((dx+dz)&1)==0?Blocks.WHITE_WOOL.getDefaultState():Blocks.LIGHT_BLUE_WOOL.getDefaultState());
        }
        c.wallSign(Plan.BEACH_X0-3,Plan.COAST+1,Plan.PARAPET_Z-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLUE,false,"ぼたんど海岸","遊泳期間 7月〜8月","監視員のいない時は","泳がないでください");
        t.places.put("beach",new int[]{(Plan.BEACH_X0+Plan.BEACH_X1)/2,Plan.SEA+1,Plan.SEAWARD_Z+5});
    }
}
