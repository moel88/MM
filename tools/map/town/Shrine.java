package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.block.enums.Attachment;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * ぼたんど神社 on the hill north of the town (map v2.0), up a broad flight of stone steps from the end of the shopping
 * street (three flights between stone walls, two landings, stone lanterns at the foot): a great red torii at the top,
 * a gravel precinct with a stone-paved approach between stone lanterns, the purification basin under its little roof,
 * the worship hall on a raised floor under a green copper roof with crossed finials, its straw rope and paper
 * streamers, the offering box and the bell; a great sacred tree bound with rope, and tall cedars all round.
 */
final class Shrine {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int Y=Plan.SHRINE_Y,X0=Plan.SHRINE_X0,X1=Plan.SHRINE_X1,ZS=Plan.SHRINE_Z0,ZN=Plan.SHRINE_Z1;
    Shrine(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        for(int x=X0;x<=X1;x++)for(int z=ZN;z<=ZS;z++){
            boolean path=Math.abs(x)<=2;
            c.set(x,Y,z,path?(((x+z)&1)==0?s(Blocks.STONE_BRICKS):s(Blocks.POLISHED_ANDESITE)):s(Blocks.GRAVEL));
            c.clear(x,Y+1,z,x,Y+14,z);c.ground(x,z,Y);
        }
        stairway();
        torii(0,ZS-1);
        for(int z=ZS-5;z>=ZN+14;z-=4)for(int side=-1;side<=1;side+=2)lantern(side*4,z);
        basin(-16,ZS-5);
        hall(-9,ZN+1,9,ZN+11);
        sacredTree(19,ZN+9);
        // cedars round the precinct
        for(int i=0;i<40;i++){
            int x=X0-8+rnd.nextInt(X1-X0+16),z=ZN-14+rnd.nextInt(ZS-ZN+18);
            if(x>=X0-1&&x<=X1+1&&z>=ZN-1&&z<=ZS+1)continue;
            if(Math.abs(x)<=6&&z>ZS)continue;
            int g=c.ground(x,z);
            if(g<Y-6||!c.get(x,g,z).isOf(Blocks.GRASS_BLOCK))continue;
            t.nature.cedar(x,g+1,z,13+rnd.nextInt(8));
        }
        t.places.put("shrine",new int[]{0,Y+1,ZS-3});
        Town.log("shrine: "+c.placed);
    }

    /**
     * The stone steps from North Street up the hill's face to the precinct: three flights of six between walls of old
     * masonry with a stone balustrade, a landing of two blocks between flights; a pair of stone lanterns at the foot.
     */
    private void stairway(){
        int x0=Plan.STAIR_X0+1,x1=Plan.STAIR_X1-1,z=Plan.TOWN_Z0-1,y=Plan.COAST;
        for(int flight=0;flight<3;flight++){
            for(int k=0;k<6;k++){y++;step(x0,x1,z,y,true);z--;}
            if(flight<2)for(int k=0;k<2;k++){step(x0,x1,z,y,false);z--;}
        }
        // from the last step to the precinct's edge, level
        for(;z>ZS;z--)step(x0,x1,z,Y,false);
        for(int side=-1;side<=1;side+=2){
            int lx=side*7,lz=Plan.TOWN_Z0;
            c.set(lx,Plan.COAST+1,lz,s(Blocks.STONE_BRICKS));c.set(lx,Plan.COAST+2,lz,s(Blocks.STONE_BRICK_WALL));c.set(lx,Plan.COAST+3,lz,s(Blocks.STONE_BRICK_WALL));
            c.set(lx,Plan.COAST+4,lz,s(Blocks.LANTERN));c.set(lx,Plan.COAST+5,lz,Canvas.slab(Blocks.STONE_BRICK_SLAB,false));
        }
        c.wallSign(Plan.STAIR_X1+2,Plan.COAST+1,Plan.TOWN_Z0,Blocks.DARK_OAK_WALL_SIGN,Direction.SOUTH,DyeColor.WHITE,false,"ぼたんど神社","参道","BOTAND SHRINE");
    }
    /** One row of the stairway at z: a step (or landing) at y, the side walls retaining the hill, a balustrade on them. */
    private void step(int x0,int x1,int z,int y,boolean stair){
        for(int x=x0;x<=x1;x++){
            c.fill(x,Plan.COAST-3,z,x,y-1,z,s(Blocks.STONE));
            c.set(x,y,z,stair?Canvas.stairs(Blocks.STONE_BRICK_STAIRS,Direction.NORTH,false):s(Blocks.STONE_BRICKS));
            c.clear(x,y+1,z,x,Y+10,z);c.ground(x,z,y);
        }
        for(int x:new int[]{x0-1,x1+1}){
            int top=Math.max(y,c.ground(x,z));
            for(int yy=Plan.COAST+1;yy<=top;yy++)c.set(x,yy,z,t.terrain.masonry(x,yy,z));
            c.set(x,top+1,z,s(Blocks.ANDESITE_WALL));
            c.clear(x,top+2,z,x,Y+10,z);
            c.ground(x,z,top);
        }
    }

    /** The great torii: vermilion pillars and tie beam, the black lintel with upturned ends, the name tablet. */
    void torii(int x,int z){
        int h=8;
        for(int side=-1;side<=1;side+=2){
            for(int y=Y+1;y<=Y+h;y++)c.set(x+side*3,y,z,s(Blocks.RED_CONCRETE));
            c.set(x+side*3,Y+1,z,s(Blocks.BLACK_CONCRETE));
        }
        for(int dx=-4;dx<=4;dx++)c.set(x+dx,Y+h-2,z,s(Blocks.RED_CONCRETE));
        for(int dx=-4;dx<=4;dx++)c.set(x+dx,Y+h,z,s(Blocks.RED_CONCRETE));
        for(int dx=-5;dx<=5;dx++)c.set(x+dx,Y+h+1,z,s(Blocks.BLACK_CONCRETE));
        c.set(x-6,Y+h+1,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,Direction.EAST,true));
        c.set(x+6,Y+h+1,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,Direction.WEST,true));
        c.set(x-6,Y+h+2,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));c.set(x+6,Y+h+2,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
        c.set(x,Y+h-1,z,s(Blocks.BLACK_CONCRETE));
        c.wallSign(x,Y+h-1,z+1,Blocks.DARK_OAK_WALL_SIGN,Direction.SOUTH,DyeColor.YELLOW,true,"","ぼたんど","神社");
    }
    /** 石灯籠: a stone lantern. */
    void lantern(int x,int z){
        c.set(x,Y+1,z,s(Blocks.STONE_BRICKS));c.set(x,Y+2,z,s(Blocks.STONE_BRICK_WALL));c.set(x,Y+3,z,s(Blocks.STONE_BRICK_WALL));
        c.set(x,Y+4,z,s(Blocks.LANTERN));c.set(x,Y+5,z,Canvas.slab(Blocks.STONE_BRICK_SLAB,false));
    }
    /** 手水舎: a stone basin of water under a small roof on four posts. */
    private void basin(int x,int z){
        c.set(x,Y+1,z,Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3,3));c.set(x+1,Y+1,z,Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3,3));
        for(int dx:new int[]{-1,2})for(int dz:new int[]{-1,1})for(int y=Y+1;y<=Y+3;y++)c.set(x+dx,y,z+dz,s(Blocks.DARK_OAK_FENCE));
        var f=new Frame(c,x-1,z-1,x+2,z+1,Direction.SOUTH,Y);
        Kit.roof(f,0,0,f.W-1,f.D-1,4,Kit.GABLE,true,Kit.COPPER,s(Blocks.AIR),s(Blocks.AIR));
    }

    /** 拝殿: the worship hall. */
    private void hall(int x0,int z0,int x1,int z1){
        var f=new Frame(c,x0,z0,x1,z1,Direction.SOUTH,Y+1);
        int W=f.W,D=f.D;
        BlockState post=Canvas.axis(Blocks.STRIPPED_DARK_OAK_LOG,Direction.Axis.Y),wall=s(Blocks.SPRUCE_PLANKS),white=s(Blocks.CALCITE);
        // a raised floor on a stone base, steps up at the front
        for(int x=-1;x<=W;x++)for(int z=-1;z<=D;z++)f.set(x,-1,z,s(Blocks.STONE_BRICKS));
        for(int x=-1;x<=W;x++)for(int z=-1;z<=D;z++)f.set(x,0,z,x<0||x>=W||z<0||z>=D?Canvas.slab(Blocks.SPRUCE_SLAB,true):s(Blocks.SPRUCE_PLANKS));
        for(int x=W/2-2;x<=W/2+2;x++)f.set(x,0,-2,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.SOUTH,false));
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){
            boolean edge=x==0||x==W-1||z==0||z==D-1;
            boolean p=(x%3==0||x==W-1)&&(z==0||z==D-1)||(z%3==0||z==D-1)&&(x==0||x==W-1);
            for(int y=1;y<=5;y++)f.set(x,y,z,!edge?s(Blocks.AIR):p?post:y>=4?white:wall);
        }
        // the front stands open between its posts
        for(int x=1;x<W-1;x++)if(x%3!=0)for(int y=1;y<=4;y++)f.set(x,y,0,s(Blocks.AIR));
        // the straw rope across the front with paper streamers, the bell, the offering box
        for(int x=1;x<W-1;x++)f.set(x,5,-1,Canvas.axis(Blocks.HAY_BLOCK,Direction.Axis.X));
        for(int x=2;x<W-1;x+=3)f.set(x,4,-1,s(Blocks.WHITE_WALL_BANNER).with(Properties.HORIZONTAL_FACING,Direction.NORTH));
        f.set(W/2,4,0,s(Blocks.BELL).with(Properties.ATTACHMENT,Attachment.CEILING).with(Properties.HORIZONTAL_FACING,Direction.NORTH));
        f.set(W/2,3,0,s(Blocks.RED_WOOL));
        f.set(W/2,1,-1,s(Blocks.BARREL));
        // inside: a dim sanctuary, lanterns, the mirror
        f.set(W/2,1,D-2,s(Blocks.GOLD_BLOCK));f.set(W/2,2,D-2,s(Blocks.GLASS));
        f.hangingLantern(2,5,D/2,false);f.hangingLantern(W-3,5,D/2,false);
        for(int x=1;x<W-1;x++)for(int z=1;z<D-1;z++)f.set(x,6,z,s(Blocks.SPRUCE_PLANKS));
        // the roof: green copper with a wide overhang, crossed finials at the gables, logs along the ridge
        Kit.roof(f,-1,-1,W,D,6,Kit.GABLE,true,Kit.COPPER,white,s(Blocks.SPRUCE_PLANKS));
        // the ridge sits at 6 + (its half-block rise)/2 above the floor: the roof's overhang reaches two past the walls
        int ridge=6+(D+3)/4+1;
        for(int x:new int[]{-2,W+1})for(int k=0;k<3;k++){
            f.set(x,ridge+k,D/2-k,s(Blocks.DARK_OAK_FENCE));f.set(x,ridge+k,D/2+k,s(Blocks.DARK_OAK_FENCE));
        }
        for(int x=1;x<W-1;x+=3)f.set(x,ridge,D/2,Canvas.axis(Blocks.DARK_OAK_LOG,Direction.Axis.Z));
    }
    /** 御神木: a huge old camphor bound with a straw rope. */
    private void sacredTree(int x,int z){
        int g=c.ground(x,z);
        for(int y=g+1;y<=g+9;y++)for(int dx=0;dx<=1;dx++)for(int dz=0;dz<=1;dz++)c.set(x+dx,y,z+dz,Canvas.axis(Blocks.DARK_OAK_LOG,Direction.Axis.Y));
        for(int dx=-1;dx<=2;dx++)for(int dz=-1;dz<=2;dz++)if((dx==-1||dx==2||dz==-1||dz==2)&&!(Math.abs(dx-0.5)>1&&Math.abs(dz-0.5)>1))c.set(x+dx,g+3,z+dz,Canvas.axis(Blocks.HAY_BLOCK,Direction.Axis.Y));
        t.nature.blob(x+1,g+12,z+1,7,4.5,7,TB.CANOPY);
        t.nature.blob(x-2,g+14,z+3,4,3,4,TB.CANOPY);
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2+0.4;
            for(int k=1;k<=4;k++)c.set(x+(int)Math.round(Math.cos(a)*k),g+9+k/2,z+(int)Math.round(Math.sin(a)*k),Canvas.axis(Blocks.DARK_OAK_LOG,Math.abs(Math.cos(a))>0.7?Direction.Axis.X:Direction.Axis.Z));
        }
    }
}
