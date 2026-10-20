package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The houses stacked up the east hill's west face (the green hillside over the lot in photo 2, and above the low
 * town's east end): narrow steps cut into the slope one above the other, each held by a stone wall, a path along its
 * front edge and a row of small two-storey houses facing west over the town and the sea; steep stairs climb from
 * step to step between the houses, and poles along the paths carry the wires.
 */
final class Hillside {
    /** The steps: west edge x, east edge x, level y (each eight blocks above the one below). */
    static final int[][] STEPS={{101,109,70},{110,118,78},{119,127,86},{128,136,94}};
    /**
     * Where the steps run along z: above the low town, and behind the lot's shed (photo 2: the houses climb the hill
     * beyond the shed, 30 to 90 blocks off; beside the lot the hill's foot is left to trees and the cherry).
     */
    static final int[][] SPANS={{-40,30},{126,172}};
    /** How deep a house is (along x), behind the two-block path. */
    static final int PATH=2;

    private final Townscape t;private final Canvas c;private final Random rnd;
    int houses;
    Hillside(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    /** The level of a step at a column, in half blocks, or {@link Terrain#NONE}. */
    static int level(int x,int z){
        for(int[] s:STEPS){
            if(x<s[0]||x>s[1])continue;
            for(int[] r:SPANS)if(z>=r[0]&&z<=Math.min(r[1],end(s)))return 2*s[2];
        }
        return Terrain.NONE;
    }
    /** How far south a step runs: short of where the hill falls into the sea below it. */
    static int end(int[] s){return (int)Math.floor(Terrain.shoreZ(s[0]))-7;}

    void build(){
        for(int i=0;i<STEPS.length;i++){
            int[] s=STEPS[i];
            for(int[] r:SPANS){
                int z0=r[0],z1=Math.min(r[1],end(s));
                if(z1-z0<8)continue;
                row(i,z0,z1);
            }
        }
        lotStairs();
        Town.log("hillside: "+houses+" houses");
    }

    /**
     * One step's row: the path along its edge, plots along it (every few a flight of stairs up to the next step
     * instead of a house), a pole every eighteen blocks or so on the path's outer edge, and a pipe rail along that edge
     * where the wall drops to the step below.
     */
    private void row(int i,int z0,int z1){
        int[] s=STEPS[i];int x0=s[0],x1=s[1],y=s[2];
        for(int z=z0;z<=z1;z++)for(int x=x0;x<x0+PATH;x++){c.set(x,y,z,Pal.concrete(x,z));c.clear(x,y+1,z,x,y+6,z);}
        int z=z0+1,k=rnd.nextInt(3);
        boolean top=i==STEPS.length-1;
        while(z<=z1-5){
            k++;
            if(!top&&k%3==0&&z+2<=z1){stairs(i,z);z+=4;continue;}
            int w=Math.min(z1-z,6+rnd.nextInt(4));
            if(w<5)break;
            // a garden now and then between the houses, so the hillside is green between its roofs as in photo 2
            if(rnd.nextInt(3)==0)garden(x0+PATH,z,x1,z+w-1,y);else house(x0+PATH,z,x1,z+w-1,y);
            z+=w+1;
        }
        List<int[]> poles=new ArrayList<>();
        for(int pz=z0+3;pz<=z1-2;pz+=17+rnd.nextInt(4)){
            int[] p=t.street.pole(x0,pz,Direction.EAST,poles.size()%2==0?1:0,rnd.nextInt(3)==0);
            if(p!=null)poles.add(p);
        }
        t.street.addRun(poles);
        for(int zz=z0;zz<=z1;zz++)if(c.ground(x0-1,zz)<y-1&&c.air(x0,y+1,zz))c.set(x0,y+1,zz,Math.floorMod(zz,3)==0?TB.PIPE:TB.GUARDRAIL);
        // ivy hanging down the step's wall in streaks and shrubs at its foot, so the hill reads green between the
        // houses as in photo 2 rather than as walls of stone
        int fx=x0-1;
        for(int zz=z0;zz<=z1;zz++){
            int g=c.ground(fx,zz);
            if(g>=y-1)continue;
            if(Pal.hash(fx,77,zz)<0.7){
                int len=2+(int)(Pal.hash(fx,78,zz)*(y-1-g));
                for(int yy=y-1;yy>=Math.max(g+1,y-len);yy--)if(c.air(fx,yy,zz))c.set(fx,yy,zz,Blocks.VINE.getDefaultState().with(VineBlock.EAST,true));
            }
            if(Pal.hash(fx,79,zz)<0.5&&c.air(fx,g+1,zz))c.set(fx,g+1,zz,Canvas.leaves(Pal.hash(fx,80,zz)<0.5?Blocks.AZALEA_LEAVES:Blocks.OAK_LEAVES));
        }
    }

    /** A stair up the lowest step's wall from the shore strip behind the lot, along the wall's face, onto the step's path. */
    private void lotStairs(){
        int y=STEPS[0][2],x0=STEPS[0][0],from=Plan.BACK_Y,z0=SPANS[1][0]-(y-from);
        for(int k=0;k<y-from;k++){
            int z=z0+k,sy=from+1+k;
            for(int x=x0-2;x<=x0-1;x++){
                c.fill(x,from+1,z,x,sy-1,z,Blocks.STONE.getDefaultState());
                c.set(x,sy,z,Canvas.stairs(Blocks.POLISHED_ANDESITE_STAIRS,Direction.SOUTH,false));
                c.clear(x,sy+1,z,x,sy+4,z);
            }
        }
    }
    /** A garden plot on a step: grass, a tree (a camphor, a cedar, now and then a cherry), shrubs and flowers. */
    private void garden(int x0,int z0,int x1,int z1,int y){
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){c.set(x,y,z,s(Blocks.GRASS_BLOCK));c.clear(x,y+1,z,x,y+9,z);}
        int tx=(x0+x1)/2,tz=(z0+z1)/2;
        switch(rnd.nextInt(5)){
            case 0->t.nature.cherry(tx,y+1,tz,5);
            case 1->t.nature.cedar(tx,y+1,tz,7+rnd.nextInt(3));
            default->t.nature.broadleaf(tx,y+1,tz,4+rnd.nextInt(2));
        }
        for(int i=0;i<4;i++){int x=x0+rnd.nextInt(x1-x0+1),z=z0+rnd.nextInt(z1-z0+1);if(c.air(x,y+1,z))c.set(x,y+1,z,Canvas.leaves(rnd.nextBoolean()?Blocks.AZALEA_LEAVES:Blocks.OAK_LEAVES));}
    }

    /** A flight of stairs, two wide, from this step's path straight up the next step's wall, walled on both sides. */
    private void stairs(int i,int z){
        int[] s=STEPS[i];int x0=s[0]+PATH,y=s[2];
        for(int k=0;k<=s[1]-x0;k++){
            int x=x0+k,sy=y+1+k;
            for(int zz=z;zz<=z+1;zz++){
                c.fill(x,y+1,zz,x,sy-1,zz,Blocks.STONE.getDefaultState());
                c.set(x,sy,zz,Canvas.stairs(Blocks.POLISHED_ANDESITE_STAIRS,Direction.EAST,false));
                c.clear(x,sy+1,zz,x,sy+4,zz);
            }
            for(int zz:new int[]{z-1,z+2}){
                c.fill(x,y+1,zz,x,sy,zz,TB.BLOCK_WALL);
                if(c.air(x,sy+1,zz))c.set(x,sy+1,zz,TB.wallCap());
            }
        }
    }

    /**
     * A small house on the hill: two storeys of plaster or weathered siding under a kawara or sheet roof, its front
     * (west) with a door and windows looking out over the town, an air conditioner on the wall, a balcony on some.
     */
    private void house(int x0,int z0,int x1,int z1,int y){
        var f=new Frame(c,x0,z0,x1,z1,Direction.WEST,y);
        int W=f.W,D=f.D;
        if(W<5||D<5)return;
        BlockState[] walls={TB.PLASTER,TB.PLASTER,TB.WEATHERED,s(Blocks.SMOOTH_SANDSTONE),s(Blocks.CALCITE),s(Blocks.WHITE_TERRACOTTA),s(Blocks.LIGHT_GRAY_TERRACOTTA)};
        BlockState wall=walls[rnd.nextInt(walls.length)],floor=s(Blocks.SPRUCE_PLANKS);
        BlockState band=rnd.nextBoolean()?s(Blocks.STRIPPED_DARK_OAK_WOOD):wall;
        int hd=Math.min(D-1,6);
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){f.set(x,0,z,s(Blocks.GRASS_BLOCK));f.clear(x,1,z,x,9,z);}
        Kit.shell(f,0,1,W-1,hd,2,wall,wall,band,floor,s(Blocks.STONE_BRICKS));
        BlockState pane=rnd.nextInt(3)==0?TB.WINDOW:s(Blocks.GLASS_PANE),frost=s(Blocks.WHITE_STAINED_GLASS_PANE);
        int door=1+rnd.nextInt(Math.max(1,W-3));
        for(int st=0;st<2;st++){
            Kit.windows(f,rnd,0,1,W-1,hd,'f',st,pane,frost,st==0?door:-99,st>0);
            Kit.windows(f,rnd,0,1,W-1,hd,'l',st,frost,pane,-99,false);
            Kit.windows(f,rnd,0,1,W-1,hd,'r',st,frost,pane,-99,false);
        }
        f.door(door,1,1,Pal.pick(rnd,Blocks.SPRUCE_DOOR,Blocks.DARK_OAK_DOOR,Blocks.BIRCH_DOOR),Direction.NORTH,false);
        Kit.canopy(f,door,door,0,1,3,Kit.KAWARA);
        // a balcony across the upper floor's front on some, the bedding out over its rail
        if(rnd.nextInt(3)==0)for(int x=0;x<W;x++){f.set(x,4,0,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));if(f.air(x,5,0))f.set(x,5,0,TB.BALCONY);}
        Kit.roof(f,0,1,W-1,hd,8,rnd.nextInt(3)==0?Kit.HIP:Kit.GABLE,true,rnd.nextInt(4)==0?Kit.SHEET:Kit.KAWARA,wall,floor);
        Kit.airCon(f,W-1,5,0,Direction.NORTH);
        if(rnd.nextBoolean())Kit.airCon(f,0,1,0,Direction.NORTH);
        if(rnd.nextInt(3)>0)Kit.roomLight(f,W/2,0,hd/2,true);
        if(rnd.nextBoolean())f.set(Math.min(W-1,door+1),1,0,Kit.pot(rnd));
        f.wallSign(Math.max(0,door-1),2,0,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLACK,false,"",t.houses.name());
        houses++;
    }
}
