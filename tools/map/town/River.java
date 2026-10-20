package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The river: a path along the top of its west bank with one row of cherry trees (the town's only long row of them)
 * and benches under them, and two footbridges over to the east bank.
 */
final class River {
    private final Townscape t;private final Canvas c;private final Random rnd;
    River(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    /** The path's outer edge at z (west of the water). */
    static int pathX(int z){return (int)Math.floor(Plan.riverX(z)-Plan.riverHalf(z))-5;}

    void build(){
        int y=Plan.COAST;
        for(int z=-190;z<=Plan.RAIL_Z0-2;z++){
            int px=pathX(z);
            for(int x=px;x<=px+2;x++){
                if(c.ground(x,z)!=y||t.terrain.plat[Canvas.idx(x,z)]!=Terrain.NONE&&x<Plan.TOWN_X1)continue;
                c.set(x,y,z,Pal.hash(x,21,z)<0.8?s(Blocks.GRAVEL):s(Blocks.COARSE_DIRT));c.clear(x,y+1,z,x,y+4,z);
            }
            // the cherry row on the town side of the path, benches between
            int tx=px-2;
            if(z>=-120&&z<=24&&Math.floorMod(z,10)==3&&c.ground(tx,z)==y&&c.air(tx,y+1,z))t.nature.cherry(tx,y+1,z,5+rnd.nextInt(2));
            if(z>=-120&&z<=24&&Math.floorMod(z,10)==8&&c.air(px-1,y+1,z)&&c.ground(px-1,z)==y)c.set(px-1,y+1,z,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.WEST,false));
        }
        paddies();
        footbridge(Plan.ST_N[0]+2);
        footbridge(-118);
        c.wallSign(pathX(10)-1,Plan.COAST+1,10,Blocks.BIRCH_WALL_SIGN,Direction.WEST,DyeColor.BLACK,false,"なぎさ川","桜並木","Nagisa River");
        Town.log("river: "+c.placed);
    }

    /**
     * 田んぼ: rice paddies on the valley floor between the town and the river, north of the cherries: shallow water
     * with young rice standing in rows, earth ridges between the plots, a farm path along them.
     */
    private void paddies(){
        int y=Plan.COAST;
        for(int z=-214;z<=-126;z++){
            int x0=Plan.TERRACE_XE[0]+12,x1=pathX(z)-4;
            for(int x=x0;x<=x1;x++){
                if(c.ground(x,z)!=y||t.terrain.plat[Canvas.idx(x,z)]!=Terrain.NONE||t.roads.any(x,z)||!c.air(x,y+1,z))continue;
                boolean ridge=Math.floorMod(z,13)==0||Math.floorMod(x-x0,11)==0||x==x0||x==x1;
                if(ridge){c.set(x,y,z,s(Blocks.GRASS_BLOCK));if(rnd.nextInt(4)==0)c.set(x,y+1,z,s(Blocks.GRASS));continue;}
                // flooded rows between the planted ones: water over mud, the rice green on wet ground beside it
                if(Math.floorMod(x-x0,3)==1){c.set(x,y-1,z,s(Blocks.MUD));c.set(x,y,z,rnd.nextInt(3)==0?s(Blocks.SEAGRASS):s(Blocks.WATER));}
                else{c.set(x,y,z,Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7));c.set(x,y+1,z,Blocks.WHEAT.getDefaultState().with(CropBlock.AGE,3+rnd.nextInt(3)));}
            }
        }
    }
    /** A footbridge of concrete with railings, humped a little over the water. */
    private void footbridge(int z){
        int a=pathX(z)+2,b=(int)Math.ceil(Plan.riverX(z)+Plan.riverHalf(z))+3;
        int len=b-a;
        for(int x=a;x<=b;x++){
            double u=(x-a)/(double)len;
            int lv=(int)Math.round(2*(Plan.COAST+1.5*Math.sin(Math.PI*u)));
            int y=lv>>1;boolean half=(lv&1)==1;
            for(int dz=-1;dz<=1;dz++){
                int zz=z+dz;
                c.clear(x,y+1,zz,x,y+5,zz);
                c.set(x,y-1,zz,s(Blocks.LIGHT_GRAY_CONCRETE));
                c.set(x,y,zz,dz==0||!half?s(Blocks.SMOOTH_STONE):s(Blocks.SMOOTH_STONE));
                if(half)c.set(x,y+1,zz,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
            }
            for(int dz:new int[]{-2,2}){
                c.set(x,y,z+dz,s(Blocks.LIGHT_GRAY_CONCRETE));c.set(x,y+1,z+dz,s(Blocks.IRON_BARS));if(half)c.set(x,y+2,z+dz,s(Blocks.IRON_BARS));
            }
            if(Plan.river(x,z)&&Math.floorMod(x-a,8)==4)for(int dz=-1;dz<=1;dz+=2)for(int yy=c.ground(x,z+dz)+1;yy<y-1;yy++)c.set(x,yy,z+dz,s(Blocks.LIGHT_GRAY_CONCRETE));
        }
    }
}
