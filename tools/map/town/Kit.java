package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.Random;

/**
 * Building parts shared by every building, in a frame's own coordinates (front toward bz=0): walls with a band at
 * each floor, rows of windows, low-pitched roofs in half blocks the way Japanese roofs sit, porches and name plates,
 * room lights, and the things around a house (cars, air conditioners, block walls, hedges, potted plants).
 */
final class Kit {
    private Kit(){}
    /** A roof covering: its slab and its full block (the ridge). */
    record Roof(Block slab,BlockState full){}
    static final Roof KAWARA=new Roof(Blocks.DEEPSLATE_TILE_SLAB,Blocks.DEEPSLATE_TILES.getDefaultState());
    static final Roof BLACK=new Roof(Blocks.POLISHED_BLACKSTONE_SLAB,Blocks.POLISHED_BLACKSTONE.getDefaultState());
    static final Roof BLUE=new Roof(Blocks.DARK_PRISMARINE_SLAB,Blocks.DARK_PRISMARINE.getDefaultState());
    static final Roof SLATE=new Roof(Blocks.STONE_BRICK_SLAB,Blocks.STONE_BRICKS.getDefaultState());
    static final Roof RED=new Roof(Blocks.BRICK_SLAB,Blocks.BRICKS.getDefaultState());
    static final Roof BROWN=new Roof(Blocks.MUD_BRICK_SLAB,Blocks.MUD_BRICKS.getDefaultState());
    static final Roof COPPER=new Roof(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB,Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState());
    static final Roof RUST=new Roof(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB,Blocks.WAXED_EXPOSED_CUT_COPPER.getDefaultState());
    static final Roof TEAL=new Roof(Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB,Blocks.WAXED_WEATHERED_CUT_COPPER.getDefaultState());
    static final int GABLE=0,HIP=1,SHED=2,FLAT=3;
    static BlockState s(Block b){return b.getDefaultState();}

    /**
     * A roof over the walls bx0..bx1 × bz0..bz1, its eaves at by=b sticking out one block (none for a flat roof).
     * Pitched roofs rise half a block per block. A gable roof's ridge runs along x if {@code alongX}; a shed roof
     * is highest over bz0 (the front) unless {@code alongX} is false, then over bz1. Gable and shed walls are filled
     * up to the roof with {@code wall}; the attic gets a ceiling of {@code ceiling}.
     */
    static void roof(Frame f,int bx0,int bz0,int bx1,int bz1,int b,int type,boolean alongX,Roof m,BlockState wall,BlockState ceiling){
        if(type==FLAT){
            for(int x=bx0;x<=bx1;x++)for(int z=bz0;z<=bz1;z++){
                boolean edge=x==bx0||x==bx1||z==bz0||z==bz1;
                f.set(x,b,z,edge?wall:s(Blocks.SMOOTH_STONE));
                if(edge)f.set(x,b+1,z,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
            }
            return;
        }
        int ox0=bx0-1,ox1=bx1+1,oz0=bz0-1,oz1=bz1+1,dmax=0;
        int[][] dd=new int[ox1-ox0+1][oz1-oz0+1];
        for(int x=ox0;x<=ox1;x++)for(int z=oz0;z<=oz1;z++){
            int dx=Math.min(x-ox0,ox1-x),dz=Math.min(z-oz0,oz1-z);
            int d=switch(type){case GABLE->alongX?dz:dx;case HIP->Math.min(dx,dz);default->alongX?oz1-z:z-oz0;};
            dd[x-ox0][z-oz0]=d;dmax=Math.max(dmax,d);
        }
        for(int x=ox0;x<=ox1;x++)for(int z=oz0;z<=oz1;z++){
            int d=dd[x-ox0][z-oz0],S=2*b+d,y=S>>1;boolean top=(S&1)==1;
            boolean inside=x>=bx0&&x<=bx1&&z>=bz0&&z<=bz1;
            if(inside){
                boolean end=switch(type){case GABLE->alongX?x==bx0||x==bx1:z==bz0||z==bz1;case SHED->x==bx0||x==bx1||(alongX?z==bz0:z==bz1);default->false;};
                for(int yy=b;yy<y;yy++)f.set(x,yy,z,end?wall:yy==b?ceiling:s(Blocks.AIR));
                if(end&&top)f.set(x,y,z,Canvas.slab(m.slab(),true));
            }
            if(type!=SHED&&d==dmax){
                // the ridge: a full row of the covering, standing proud of the slopes
                f.set(x,y,z,m.full());if(!top)continue;
                f.set(x,y+1,z,Canvas.slab(m.slab(),false));continue;
            }
            f.set(x,y,z,Canvas.slab(m.slab(),top));
        }
    }

    /** The walls, floors and band courses of a box of {@code stories} stories (four blocks each). */
    static void shell(Frame f,int bx0,int bz0,int bx1,int bz1,int stories,BlockState wall,BlockState corner,BlockState band,BlockState floor,BlockState base){
        for(int s=0;s<stories;s++){
            int fy=4*s;
            for(int x=bx0;x<=bx1;x++)for(int z=bz0;z<=bz1;z++){
                boolean edge=x==bx0||x==bx1||z==bz0||z==bz1,cornerCol=(x==bx0||x==bx1)&&(z==bz0||z==bz1);
                f.set(x,fy,z,edge&&s==0?base:edge?band:floor);
                for(int y=fy+1;y<=fy+3;y++)f.set(x,y,z,edge?(cornerCol?corner:wall):s(Blocks.AIR));
            }
        }
        // the floor above the top story (the roof sits on it)
        for(int x=bx0;x<=bx1;x++)for(int z=bz0;z<=bz1;z++)f.set(x,4*stories,z,x==bx0||x==bx1||z==bz0||z==bz1?band:floor);
    }

    /**
     * Windows along one wall of a box: front (bz0), back (bz1), left (bx0) or right (bx1). {@code skip} keeps the
     * windows off one position (the door). Panes are tall on living floors, small and frosted now and then.
     */
    static void windows(Frame f,Random r,int bx0,int bz0,int bx1,int bz1,char side,int story,BlockState pane,BlockState frosted,int skip,boolean tall){
        boolean alongX=side=='f'||side=='b';
        int a=alongX?bx0:bz0,e=alongX?bx1:bz1,fixed=side=='f'?bz0:side=='b'?bz1:side=='l'?bx0:bx1;
        int fy=4*story;
        int k=a+2;
        while(k<=e-2){
            int w=r.nextInt(3)==0?1:2;if(k+w-1>e-2)w=1;
            boolean clash=skip>=k-1&&skip<=k+w;
            if(!clash){
                boolean small=!tall||r.nextInt(4)==0;
                BlockState p=small&&r.nextBoolean()?frosted:pane;
                for(int i=0;i<w;i++){
                    int x=alongX?k+i:fixed,z=alongX?fixed:k+i;
                    f.set(x,fy+2,z,p);if(!small)f.set(x,fy+3,z,p);
                }
            }
            k+=w+1+r.nextInt(2);
        }
    }

    /** A front door with a little canopy over it, a lamp beside it and the family's name plate. */
    static void entrance(Frame f,int bx,int bz,Block door,Roof canopy,String name,boolean light){
        f.door(bx,1,bz,door,Direction.NORTH,false);
        for(int x=bx-1;x<=bx+1;x++)if(f.air(x,3,bz-1))f.set(x,3,bz-1,Canvas.slab(canopy.slab(),false));
        if(light)f.set(bx+1,2,bz-1,Canvas.rod(Blocks.END_ROD,Direction.NORTH));
        if(name!=null)f.wallSign(bx-1,2,bz-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLACK,false,"",name);
    }

    /** A light in the middle of a room: a lantern hanging from the ceiling, or a white tube light. */
    static void roomLight(Frame f,int bx,int fy,int bz,boolean warm){
        if(warm)f.hangingLantern(bx,fy+3,bz,false);
        else f.set(bx,fy+3,bz,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
    }

    /** An air conditioner's outdoor unit against a wall: a pale box with a fan grille. */
    static void airCon(Frame f,int bx,int by,int bz,Direction out){
        f.set(bx,by,bz,Blocks.WHITE_TERRACOTTA);
        int fx=bx+out.getOffsetX(),fz=bz+out.getOffsetZ();
        if(f.air(fx,by,fz))f.set(fx,by,fz,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,out,true,false));
    }

    /** A potted plant. */
    static BlockState pot(Random r){
        Block[] p={Blocks.POTTED_FERN,Blocks.POTTED_AZALEA_BUSH,Blocks.POTTED_FLOWERING_AZALEA_BUSH,Blocks.POTTED_RED_TULIP,Blocks.POTTED_POPPY,
            Blocks.POTTED_BAMBOO,Blocks.POTTED_CACTUS,Blocks.POTTED_DANDELION,Blocks.POTTED_OAK_SAPLING,Blocks.POTTED_SPRUCE_SAPLING,Blocks.POTTED_BLUE_ORCHID,
            Blocks.POTTED_CORNFLOWER,Blocks.POTTED_LILY_OF_THE_VALLEY,Blocks.POTTED_CHERRY_SAPLING};
        return s(p[r.nextInt(p.length)]);
    }

    /** Block wall (ブロック塀) along a lot line: concrete blocks capped with a slab, sometimes with a gap pattern. */
    static void blockWall(Frame f,int bx0,int bz0,int bx1,int bz1,int h){
        for(int x=Math.min(bx0,bx1);x<=Math.max(bx0,bx1);x++)for(int z=Math.min(bz0,bz1);z<=Math.max(bz0,bz1);z++){
            int g=f.ground(x,z);
            for(int y=g+1;y<h;y++)f.set(x,y,z,Pal.blockWall(f.wx(x,z),f.y0+y,f.wz(x,z)));
            f.set(x,Math.max(h,g+1),z,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
        }
    }
    static void hedge(Frame f,int bx0,int bz0,int bx1,int bz1,int h,Block leaves){
        for(int x=Math.min(bx0,bx1);x<=Math.max(bx0,bx1);x++)for(int z=Math.min(bz0,bz1);z<=Math.max(bz0,bz1);z++)
            for(int y=1;y<=h;y++)f.set(x,y,z,Canvas.leaves(leaves));
    }
    static void boardFence(Frame f,int bx0,int bz0,int bx1,int bz1,Block planks,Block slab){
        for(int x=Math.min(bx0,bx1);x<=Math.max(bx0,bx1);x++)for(int z=Math.min(bz0,bz1);z<=Math.max(bz0,bz1);z++){
            f.set(x,1,z,planks);f.set(x,2,z,Canvas.slab(slab,false));
        }
    }
    static void fence(Frame f,int bx0,int bz0,int bx1,int bz1,Block fence){
        for(int x=Math.min(bx0,bx1);x<=Math.max(bx0,bx1);x++)for(int z=Math.min(bz0,bz1);z<=Math.max(bz0,bz1);z++)f.set(x,1,z,fence);
    }

    /** Car colours: body slab and block, roof. */
    record Paint(Block slab,BlockState body){}
    static final Paint[] PAINTS={
        new Paint(Blocks.SMOOTH_QUARTZ_SLAB,s(Blocks.SMOOTH_QUARTZ)),new Paint(Blocks.SMOOTH_QUARTZ_SLAB,s(Blocks.SMOOTH_QUARTZ)),
        new Paint(Blocks.POLISHED_ANDESITE_SLAB,s(Blocks.POLISHED_ANDESITE)),new Paint(Blocks.POLISHED_BLACKSTONE_SLAB,s(Blocks.POLISHED_BLACKSTONE)),
        new Paint(Blocks.RED_NETHER_BRICK_SLAB,s(Blocks.RED_NETHER_BRICKS)),new Paint(Blocks.PRISMARINE_BRICK_SLAB,s(Blocks.PRISMARINE_BRICKS)),
        new Paint(Blocks.SMOOTH_SANDSTONE_SLAB,s(Blocks.SMOOTH_SANDSTONE)),new Paint(Blocks.BIRCH_SLAB,s(Blocks.BIRCH_PLANKS))};
    /** A small car (kei car) parked facing the street: low bonnet, a glass cabin, two blocks wide and four long. */
    static void car(Frame f,int bx,int bz,Random r){
        Paint p=PAINTS[r.nextInt(PAINTS.length)];
        boolean van=r.nextInt(3)==0;
        for(int x=bx;x<=bx+1;x++){
            int g=f.ground(x,bz);
            f.set(x,g+1,bz,Canvas.slab(p.slab(),false));
            for(int z=bz+1;z<=bz+3;z++)f.set(x,g+1,z,p.body());
            f.set(x,g+2,bz+1,s(Blocks.BLACK_STAINED_GLASS));
            f.set(x,g+2,bz+2,s(Blocks.GRAY_STAINED_GLASS));
            f.set(x,g+2,bz+3,van?s(Blocks.GRAY_STAINED_GLASS):Canvas.slab(p.slab(),false));
            if(van)f.set(x,g+3,bz+2,Canvas.slab(p.slab(),false));
        }
    }
    static BlockState button(Direction facing){return Canvas.button(Blocks.STONE_BUTTON,facing,WallMountLocation.WALL);}
}
