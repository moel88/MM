package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The two blocks between South Street and the sea either side of the main street (map v2.0): the pine grove (松原)
 * west of it, black pines standing well apart on grass and sand with paths, benches and the coast's stone; and the sea
 * station (海の駅) east of it, a market hall of the town's fish and vegetables under a big tiled roof, its paved
 * forecourt toward the main street and its car park toward the sea.
 */
final class Waterfront {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int Y=Plan.COAST,Z0=Plan.ROW_S[0],Z1=Plan.ROW_S[1];
    /** The pine grove (x) west of the main street, the sea station's block east of it. */
    static final int GX0=Plan.COL_C[0],GX1=Plan.MAIN_S.a0()-1,SX0=Plan.MAIN_S.a1()+1,SX1=Plan.COL_C[1];
    Waterfront(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        grove();
        station();
        Town.log("waterfront: "+c.placed);
    }

    /** The pine grove: grass and drifts of sand, black pines on a loose grid, two paths crossing it, benches, the stone. */
    private void grove(){
        for(int x=GX0;x<=GX1;x++)for(int z=Z0;z<=Z1;z++){
            boolean sand=Pal.smooth(x,z,6,91)<0.38;
            c.set(x,Y,z,sand?s(Blocks.SAND):s(Blocks.GRASS_BLOCK));c.clear(x,Y+1,z,x,Y+10,z);c.ground(x,z,Y);
        }
        // a path from South Street down to the sea, and one across
        int px=(GX0+GX1)/2,pz=(Z0+Z1)/2;
        for(int z=Z0;z<=Z1;z++)for(int dx=-1;dx<=1;dx++)c.set(px+dx,Y,z,Pal.hash(px+dx,92,z)<0.2?s(Blocks.COARSE_DIRT):s(Blocks.DIRT_PATH));
        for(int x=GX0;x<=GX1;x++)for(int dz=-1;dz<=1;dz++)c.set(x,Y,pz+dz,Pal.hash(x,93,pz+dz)<0.2?s(Blocks.COARSE_DIRT):s(Blocks.DIRT_PATH));
        // the pines, on a loose grid eight apart, kept off the paths
        for(int x=GX0+5;x<=GX1-4;x+=8)for(int z=Z0+5;z<=Z1-4;z+=8){
            int tx=x+rnd.nextInt(5)-2,tz=z+rnd.nextInt(5)-2;
            if(Math.abs(tx-px)<=3||Math.abs(tz-pz)<=3)continue;
            t.nature.pine(tx,Y+1,tz,6+rnd.nextInt(4));
        }
        // benches along the paths, the coast's stone where they cross, lamps
        for(int z=Z0+6;z<=Z1-6;z+=10)if(Math.abs(z-pz)>3)for(int side=-1;side<=1;side+=2){
            int x=px+side*3;
            if(c.air(x,Y+1,z)&&c.air(x,Y+1,z+1))for(int k=0;k<=1;k++)c.set(x,Y+1,z+k,Canvas.stairs(Blocks.SPRUCE_STAIRS,side<0?Direction.WEST:Direction.EAST,false));
        }
        c.set(px+3,Y+1,pz+3,s(Blocks.MOSSY_STONE_BRICKS));c.set(px+3,Y+2,pz+3,s(Blocks.STONE_BRICK_WALL));
        c.wallSign(px+3,Y+1,pz+2,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,false,"","ぼたんど松原","BOTAND PINES");
        for(int[] p:new int[][]{{px-3,Z0+3},{px+3,Z1-3},{GX0+3,pz-3},{GX1-3,pz+3}}){
            if(!c.air(p[0],Y+1,p[1]))continue;
            for(int y=Y+1;y<=Y+3;y++)c.set(p[0],y,p[1],s(Blocks.DARK_OAK_FENCE));
            c.set(p[0],Y+4,p[1],s(Blocks.LANTERN));c.set(p[0],Y+5,p[1],Canvas.slab(Blocks.DARK_OAK_SLAB,false));
        }
        t.places.put("pines",new int[]{px,Y+1,pz});
    }

    /**
     * 海の駅 ぼたんど: one tall storey of dark timber and glass under a wide tiled hip roof, its front open to the paved
     * forecourt with the market's stalls of fish, vegetables and dried goods inside, noren at the doors, the name on a
     * board; the car park toward the coastal road, a bus stop.
     */
    private void station(){
        int fz1=Z1-17;
        for(int x=SX0;x<=SX1;x++)for(int z=Z0;z<=Z1;z++){
            c.set(x,Y,z,z>fz1?Pal.asphalt(x,z):TB.PAVING);c.clear(x,Y+1,z,x,Y+12,z);c.ground(x,z,Y);
        }
        var f=new Frame(c,SX1-25,Z0+2,SX1-2,fz1-2,Direction.WEST,Y);
        int W=f.W,D=f.D,m=W/2;
        Kit.shell(f,0,0,W-1,D-1,1,TB.BOARDS_DARK,TB.TIMBER,TB.TIMBER,s(Blocks.SMOOTH_STONE),TB.CONCRETE);
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){
            boolean edge=x==0||x==W-1||z==0||z==D-1;
            f.set(x,4,z,edge?TB.TIMBER:s(Blocks.AIR));f.set(x,5,z,edge?TB.PLASTER:s(Blocks.AIR));
        }
        // the front: glass between the posts, three wide doorways with noren
        for(int x=1;x<W-1;x++){
            if(x%4==0)continue;
            boolean door=Math.abs(x-m)<=1||Math.abs(x-4)<=1||Math.abs(x-(W-5))<=1;
            for(int y=1;y<=3;y++)f.set(x,y,0,door&&y<=2?s(Blocks.AIR):TB.WIN);
            if(door&&x%4==2)f.set(x,3,-1,s(Blocks.BLUE_WALL_BANNER).with(Properties.HORIZONTAL_FACING,Direction.NORTH));
        }
        for(int z=2;z<D-2;z+=3){f.set(0,2,z,TB.out(TB.WIN,Direction.WEST));f.set(W-1,2,z,TB.out(TB.WIN,Direction.EAST));}
        f.wallSign(m,5,-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"","海の駅 ぼたんど","SEA STATION");
        Kit.roof(f,0,0,W-1,D-1,6,Kit.HIP,true,Kit.KAWARA,TB.PLASTER,s(Blocks.SPRUCE_PLANKS));
        // the stalls inside: ice and fish, vegetables, dried goods; lanterns overhead
        for(int z=3;z<D-2;z+=4)for(int x=3;x<W-3;x++){
            if(x%6==0)continue;
            f.set(x,1,z,Canvas.slab(Blocks.SPRUCE_SLAB,true));
            int kind=(z/4)%3;
            if(rnd.nextInt(3)>0)f.set(x,2,z,kind==0?(rnd.nextBoolean()?s(Blocks.PACKED_ICE):s(Blocks.DRIED_KELP_BLOCK)):kind==1?Pal.pick(rnd,Blocks.MELON,Blocks.PUMPKIN,Blocks.MOSS_BLOCK).getDefaultState():s(Blocks.BARREL));
        }
        for(int x=3;x<W-3;x+=5)for(int z=2;z<D-2;z+=5)f.hangingLantern(x,5,z,false);
        // the forecourt: planters with a pine each, benches, vending machines by the door
        for(int z:new int[]{Z0+4,fz1-3}){
            int x=SX0+8;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)c.set(x+dx,Y+1,z+dz,dx==0&&dz==0?s(Blocks.GRASS_BLOCK):s(Blocks.STONE_BRICKS));
            t.nature.pine(x,Y+2,z,5);
        }
        for(int z=Z0+10;z<=fz1-8;z+=6)for(int k=0;k<=1;k++)c.set(SX0+3,Y+1,z+k,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.WEST,false));
        t.street.vending(f.wx(W-2,-1),f.wz(W-2,-1),f.dir(Direction.EAST),2);
        // the car park: bays along the north and south of its aisle, a few cars
        for(int x=SX0+3;x<SX1-2;x+=3){
            for(int z=fz1+2;z<=fz1+6;z++)c.set(x,Y,z,s(Blocks.WHITE_CONCRETE));
            for(int z=Z1-4;z<=Z1;z++)c.set(x,Y,z,s(Blocks.WHITE_CONCRETE));
            if(rnd.nextInt(4)==0)Kit.car(new Frame(c,x+1,fz1+2,x+2,fz1+6,Direction.SOUTH,Y),0,0,rnd);
        }
        t.street.busStop(SX0+8,Plan.COASTAL.a0(),Direction.SOUTH,"海の駅前");
        t.places.put("sea_station",new int[]{f.wx(m,-3),Y+1,f.wz(m,-3)});
    }
}
