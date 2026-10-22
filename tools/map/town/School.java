package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * ぼたんど小学校, east of the square (map v2.0): the long three-storey school building with its clock over the
 * entrance and the gym beside it along the block's north side, both looking south over the sports ground: a wide
 * field of packed earth with a running track painted on it, a goal at each end, the horizontal bars, the sandpit and
 * the flagpole, shade trees along the south edge; a knee-high wall and the school gate on South Street.
 */
final class School {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int Y=Plan.COAST,X0=Plan.COL_E[0],X1=Plan.COL_E[1],Z0=Plan.ROW_M[0],Z1=Plan.ROW_M[1];
    /** The sports ground (x, z inclusive) and its track's middle. */
    static final int GX0=X0+2,GX1=X1-2,GZ0=-26,GZ1=Z1-3,TX=122,TZ=2;
    School(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        for(int x=X0;x<=X1;x++)for(int z=Z0;z<=Z1;z++){c.set(x,Y,z,s(Blocks.GRASS_BLOCK));c.clear(x,Y+1,z,x,Y+12,z);c.ground(x,z,Y);}
        // the forecourt in front of the buildings, concrete
        for(int x=X0+2;x<=X1-2;x++)for(int z=Z0+2;z<GZ0;z++)c.set(x,Y,z,Pal.concrete(x,z));
        ground();
        main(new Frame(c,100,Z0+2,X1-3,-40,Direction.SOUTH,Y));
        gym(new Frame(c,X0+3,Z0+2,96,-34,Direction.SOUTH,Y));
        goal(GX0+2,TZ,Direction.EAST);goal(GX1-2,TZ,Direction.WEST);
        bars(GX0+8,GZ1-1);
        sandpit(GX1-22,GZ1-5);
        flagpole(TX+28,-36);
        edges();
        t.places.put("school",new int[]{TX,Y+1,TZ});
        Town.log("school: "+c.placed);
    }

    /** The sports ground: packed earth with a little grit, the track's white lines. */
    private void ground(){
        for(int x=GX0;x<=GX1;x++)for(int z=GZ0;z<=GZ1;z++){
            double h=Pal.hash(x,81,z);
            c.set(x,Y,z,h<0.06?s(Blocks.COARSE_DIRT):s(Blocks.PACKED_MUD));
        }
        // the track: two lanes round a stadium of 36 blocks' straights and 14 blocks' bends, its lines in white
        for(int x=GX0;x<=GX1;x++)for(int z=GZ0;z<=GZ1;z++){
            double px=Math.max(TX-18,Math.min(TX+18,x)),d=Math.hypot(x-px,z-TZ);
            boolean line=Math.abs(d-14)<0.5||Math.abs(d-16.5)<0.5||Math.abs(d-19)<0.5;
            boolean start=x==TX-2&&d>14&&d<19&&z>TZ;
            if(line||start)c.set(x,Y,z,s(Blocks.WHITE_CONCRETE));
        }
    }

    /**
     * The school building: three storeys of cream walls, classroom windows all along the south, the corridor's along
     * the north, the middle bay carried up a storey higher with the clock and the school's name, the entrance hall
     * under a canopy below it.
     */
    private void main(Frame f){
        int W=f.W,D=f.D,m=W/2;
        Kit.shell(f,0,0,W-1,D-1,3,TB.STUCCO_CREAM,TB.CONCRETE,TB.TRIM_WHITE,s(Blocks.SMOOTH_STONE),TB.CONCRETE);
        for(int s=0;s<3;s++){
            int fy=4*s;
            for(int x=1;x<W-1;x++){
                if(x%8==0||Math.abs(x-m)<=3)continue;
                f.set(x,fy+2,0,TB.WIN_WHITE);f.set(x,fy+3,0,TB.WIN_WHITE);
                if(x%2==1)f.set(x,fy+2,D-1,TB.out(TB.WIN_WHITE,Direction.SOUTH));
            }
            for(int z=2;z<D-2;z+=3){f.set(0,fy+2,z,TB.out(TB.WIN_WHITE,Direction.WEST));f.set(W-1,fy+2,z,TB.out(TB.WIN_WHITE,Direction.EAST));}
            for(int x=4;x<W-4;x+=8)Kit.roomLight(f,x,fy,D/2,false);
        }
        Kit.roof(f,0,0,W-1,D-1,12,Kit.FLAT,true,Kit.SLATE,TB.STUCCO_CREAM,s(Blocks.SMOOTH_STONE));
        // the middle bay with the clock
        for(int x=m-3;x<=m+3;x++)for(int z=0;z<=3;z++)for(int y=5;y<=16;y++){
            boolean face=z==0||z==3||x==m-3||x==m+3;
            f.set(x,y,z,face?(y>=15?TB.TRIM_WHITE:TB.CONCRETE):y==16?s(Blocks.SMOOTH_STONE):s(Blocks.AIR));
        }
        for(int x=m-3;x<=m+3;x++)f.set(x,17,0,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
        for(int x=m-1;x<=m+1;x++)for(int y=12;y<=14;y++)f.set(x,y,-1,s(Blocks.WHITE_CONCRETE));
        f.set(m,13,-2,s(Blocks.BLACK_CONCRETE));f.set(m,14,-2,Canvas.rod(Blocks.END_ROD,Direction.UP));f.set(m+1,13,-2,Canvas.rod(Blocks.END_ROD,Direction.EAST));
        f.wallSign(m,8,-1,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"ぼたんど町立","ぼたんど小学校","BOTAND SCHOOL");
        // the entrance hall: open doors, the shoe lockers inside, a flat canopy
        for(int x=m-2;x<=m+2;x++){f.set(x,1,0,s(Blocks.AIR));f.set(x,2,0,s(Blocks.AIR));f.set(x,3,0,TB.WIN_WHITE);}
        for(int x=m-4;x<=m+4;x++)for(int z=-3;z<=-1;z++)f.set(x,4,z,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));
        for(int x:new int[]{m-4,m+4})for(int y=1;y<=3;y++)f.set(x,y,-3,s(Blocks.QUARTZ_PILLAR));
        for(int x=m-4;x<=m+4;x++)if(Math.abs(x-m)>1)f.set(x,1,3,s(Blocks.CHISELED_BOOKSHELF));
        f.set(m-5,2,-1,Kit.pot(rnd));
        Kit.airCon(f,W,1,D-2,Direction.EAST);Kit.airCon(f,-1,1,D-2,Direction.WEST);
    }

    /** The gym: a tall hall of pale panels under a low-pitched green roof, a band of high windows, its doors to the ground. */
    private void gym(Frame f){
        int W=f.W,D=f.D,m=W/2;
        Kit.shell(f,0,0,W-1,D-1,2,TB.STUCCO_GRAY,TB.CONCRETE,TB.STUCCO_GRAY,s(Blocks.OAK_PLANKS),TB.CONCRETE);
        // one hall the full height: no floor between the storeys
        for(int x=1;x<W-1;x++)for(int z=1;z<D-1;z++)f.set(x,4,z,s(Blocks.AIR));
        for(int x=2;x<W-2;x++){if(x%3==0)continue;f.set(x,6,0,TB.WIN_FROSTED);f.set(x,6,D-1,TB.out(TB.WIN_FROSTED,Direction.SOUTH));}
        for(int z=2;z<D-2;z++){if(z%3==0)continue;f.set(0,6,z,TB.out(TB.WIN_FROSTED,Direction.WEST));f.set(W-1,6,z,TB.out(TB.WIN_FROSTED,Direction.EAST));}
        for(int x=m-2;x<=m+2;x++){f.set(x,1,0,s(Blocks.AIR));f.set(x,2,0,s(Blocks.AIR));f.set(x,3,0,TB.TRIM_WHITE);}
        f.wallSign(m,3,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLACK,false,"","体育館","GYM");
        for(int x=3;x<W-3;x+=5)f.hangingLantern(x,7,D/2,false);
        Kit.roof(f,0,0,W-1,D-1,8,Kit.GABLE,true,Kit.TEAL,TB.STUCCO_GRAY,s(Blocks.SPRUCE_PLANKS));
    }

    /** A small goal, its mouth toward {@code mouth}: white posts and bar, the net behind. */
    private void goal(int x,int zc,Direction mouth){
        int back=-mouth.getOffsetX();
        for(int z=zc-3;z<=zc+3;z++){
            boolean post=z==zc-3||z==zc+3;
            if(post){c.set(x,Y+1,z,TB.PIPE);c.set(x,Y+2,z,TB.PIPE);}
            c.set(x,Y+3,z,s(Blocks.WHITE_CONCRETE));
            c.set(x+back,Y+1,z,TB.NET);c.set(x+back,Y+2,z,TB.NET);
        }
    }
    /** Three horizontal bars of different heights between iron posts. */
    private void bars(int x0,int z){
        int[] h={2,3,3};
        for(int i=0;i<3;i++){
            int x=x0+i*3;
            for(int y=Y+1;y<=Y+h[i];y++){c.set(x,y,z,s(Blocks.IRON_BARS));c.set(x+3,y,z,s(Blocks.IRON_BARS));}
            for(int k=1;k<=2;k++)c.set(x+k,Y+h[i],z,s(Blocks.IRON_BARS));
        }
    }
    /** The long jump's sandpit, its run-up marked in white. */
    private void sandpit(int x0,int z0){
        for(int x=x0;x<x0+7;x++)for(int z=z0;z<z0+4;z++)c.set(x,Y,z,s(Blocks.SAND));
        for(int x=x0-12;x<x0;x++){c.set(x,Y,z0,s(Blocks.WHITE_CONCRETE));c.set(x,Y,z0+3,s(Blocks.WHITE_CONCRETE));}
    }
    private void flagpole(int x,int z){
        for(int y=Y+1;y<=Y+11;y++)c.set(x,y,z,s(Blocks.IRON_BARS));
        c.set(x,Y+12,z,s(Blocks.GOLD_BLOCK));
        c.set(x,Y+10,z+1,s(Blocks.WHITE_WALL_BANNER).with(Properties.HORIZONTAL_FACING,Direction.SOUTH));
        c.set(x,Y+1,z+1,s(Blocks.STONE_BRICKS));
    }

    /**
     * The school's edges: a knee-high wall along South Street with the gate (two stone posts, the school's name) in
     * the middle, low hedges along the avenue and the ring road, shade trees along the ground's south edge and two
     * cherry trees at the gate.
     */
    private void edges(){
        int gate0=TX-5,gate1=TX+5;
        for(int x=X0+2;x<=X1-2;x++){
            if(x>=gate0&&x<=gate1)continue;
            c.set(x,Y+1,Z1-1,TB.CONCRETE);
        }
        for(int x:new int[]{gate0-1,gate1+1}){c.set(x,Y+1,Z1-1,TB.STONE_PILLAR);c.set(x,Y+2,Z1-1,TB.STONE_PILLAR);}
        c.wallSign(gate0-1,Y+2,Z1,Blocks.DARK_OAK_WALL_SIGN,Direction.SOUTH,DyeColor.WHITE,false,"ぼたんど町立","ぼたんど","小学校");
        for(int z=Z0+4;z<=Z1-2;z++){
            if(Math.abs(z-TZ)<=3)continue;
            if(c.air(X0+1,Y+1,z))c.set(X0+1,Y+1,z,TB.HEDGE);
            if(c.air(X1-1,Y+1,z))c.set(X1-1,Y+1,z,TB.HEDGE);
        }
        for(int x=GX0+6;x<=GX1-6;x+=16)if(Math.abs(x-TX)>9)t.nature.townTree(x,Y+1,Z1-2,5+rnd.nextInt(2));
        for(int x:new int[]{gate0-4,gate1+4})t.nature.cherry(x,Y+1,Z1-3,6,1.1);
    }
}
