package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import dev.sangwoo.celestialfall.town.RoofBlock;
import dev.sangwoo.celestialfall.town.RoofShape;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.Random;

/**
 * Building parts shared by every building, in a frame's own coordinates (front toward bz=0): walls with a band at
 * each floor, rows of windows, roofs at their true pitch (Botand's roof blocks), porches and name plates,
 * room lights, and the things around a house (cars, air conditioners, block walls, hedges, potted plants).
 */
final class Kit {
    private Kit(){}
    /**
     * A roof covering: its slab and its full block (for flat roofs' parapets and small canopies), and the roof block
     * that lays it at its true pitch (Botand's RoofBlock, 2.47.0).
     */
    record Roof(Block slab,BlockState full,Block patch){
        float pitch(){return ((RoofBlock)patch).pitch;}
    }
    /** Kawara (Botand's own tiles, their channels laid down the slope), the photos' dark grey roofs. */
    static final Roof KAWARA=new Roof(TB.of("kawara_slab").getBlock(),TB.of("kawara"),TB.of("roof_kawara").getBlock());
    /** Corrugated iron sheet roofing, at a low pitch: sheds, the old houses behind the lot. */
    static final Roof SHEET=new Roof(TB.of("corrugated_roof_slab").getBlock(),TB.of("corrugated_roof"),TB.of("roof_sheet").getBlock());
    static final Roof BLACK=new Roof(Blocks.POLISHED_BLACKSTONE_SLAB,Blocks.POLISHED_BLACKSTONE.getDefaultState(),TB.of("roof_black").getBlock());
    static final Roof BLUE=new Roof(Blocks.DARK_PRISMARINE_SLAB,Blocks.DARK_PRISMARINE.getDefaultState(),TB.of("roof_blue").getBlock());
    static final Roof SLATE=new Roof(Blocks.STONE_BRICK_SLAB,Blocks.STONE_BRICKS.getDefaultState(),TB.of("roof_slate").getBlock());
    static final Roof RED=new Roof(Blocks.BRICK_SLAB,Blocks.BRICKS.getDefaultState(),TB.of("roof_red").getBlock());
    static final Roof BROWN=new Roof(Blocks.MUD_BRICK_SLAB,Blocks.MUD_BRICKS.getDefaultState(),TB.of("roof_brown").getBlock());
    static final Roof COPPER=new Roof(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB,Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState(),TB.of("roof_copper").getBlock());
    static final Roof RUST=new Roof(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB,Blocks.WAXED_EXPOSED_CUT_COPPER.getDefaultState(),TB.of("roof_rust").getBlock());
    static final Roof TEAL=new Roof(Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB,Blocks.WAXED_WEATHERED_CUT_COPPER.getDefaultState(),TB.of("roof_teal").getBlock());
    static final int GABLE=0,HIP=1,SHED=2,FLAT=3;
    static BlockState s(Block b){return b.getDefaultState();}
    /** A roofing state laid along an axis (the town's tiles and sheets run down the slope); others as they are. */
    static BlockState along(BlockState s,Direction.Axis a){return s.contains(Properties.HORIZONTAL_AXIS)?s.with(Properties.HORIZONTAL_AXIS,a):s;}

    /**
     * A roof over the walls bx0..bx1 × bz0..bz1, its eaves at by=b sticking out one block (none for a flat roof),
     * laid at the covering's true pitch: half a block per block for tiles, a quarter for sheet. A gable roof's ridge
     * runs along x if {@code alongX}; a shed roof is highest over bz0 (the front) unless {@code alongX} is false, then
     * over bz1. Each cell gets the roof block whose shape is the roof's over it (a slope, a hip, a ridge, the end of a
     * ridge, a peak), filled down to its floor over the walls; gable and shed walls are built up under the roof with
     * {@code wall}; the attic gets a ceiling of {@code ceiling}; a ridge on a cell's edge gets its cap.
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
        int ox0=bx0-1,ox1=bx1+1,oz0=bz0-1,oz1=bz1+1;
        float p=m.pitch();
        // the roof's distance up from its eaves at any point, in blocks of run (the outer edges of the eaves are 0)
        java.util.function.DoubleBinaryOperator run=(x,z)->{
            double dx=Math.min(x-ox0,ox1+1-x),dz=Math.min(z-oz0,oz1+1-z);
            return switch(type){case GABLE->alongX?dz:dx;case HIP->Math.min(dx,dz);default->alongX?oz1+1-z:z-oz0;};
        };
        double top=b+p*switch(type){case GABLE->(alongX?oz1+1-oz0:ox1+1-ox0)/2.0;case HIP->Math.min(ox1+1-ox0,oz1+1-oz0)/2.0;default->alongX?oz1+1-oz0:ox1+1-ox0;};
        for(int x=ox0;x<=ox1;x++)for(int z=oz0;z<=oz1;z++){
            double[][] h=new double[3][3];double lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;
            for(int i=0;i<3;i++)for(int j=0;j<3;j++){h[i][j]=b+p*run.applyAsDouble(x+i*0.5,z+j*0.5);lo=Math.min(lo,h[i][j]);hi=Math.max(hi,h[i][j]);}
            int y=(int)Math.floor(lo+1e-6),base=(int)Math.round((lo-y)*4);
            if(base==4){y++;base=0;}
            var shape=RoofCells.match(h,lo,p);
            boolean inside=x>=bx0&&x<=bx1&&z>=bz0&&z<=bz1;
            if(inside){
                boolean end=switch(type){case GABLE->alongX?x==bx0||x==bx1:z==bz0||z==bz1;case SHED->x==bx0||x==bx1||(alongX?z==bz0:z==bz1);default->false;};
                for(int yy=b;yy<y;yy++)f.set(x,yy,z,end?wall:yy==b?ceiling:s(Blocks.AIR));
            }
            // a ridge along a cell's edge: the two slopes meeting there each carry half its cap
            boolean cap=type!=SHED&&shape.shape()==RoofShape.SLOPE&&Math.abs(hi-top)<1e-6;
            f.set(x,y,z,m.patch().getDefaultState().with(RoofBlock.SHAPE,shape.shape()).with(RoofBlock.FACING,shape.facing())
                .with(RoofBlock.BASE,base).with(RoofBlock.FILLED,inside).with(RoofBlock.CAP,cap));
        }
    }

    /**
     * A lean-to canopy (a porch roof, a tiled eave over a window row) out from a front wall at bz = wall + 1 toward the
     * street (-bz), across bx0..bx1: it meets the wall at by + 1 and falls outward at the covering's pitch for
     * {@code depth} blocks, laid with the roof blocks like the roofs (only into air).
     */
    static void canopy(Frame f,int bx0,int bx1,int wall,int depth,int by,Roof m){
        float p=m.pitch();
        for(int r=0;r<depth;r++){
            double lo=by+1-(r+1)*p;
            int y=(int)Math.floor(lo+1e-6),base=(int)Math.round((lo-y)*4),bz=wall-r;
            for(int x=bx0;x<=bx1;x++)if(f.air(x,y,bz))
                f.set(x,y,bz,m.patch().getDefaultState().with(RoofBlock.SHAPE,RoofShape.SLOPE).with(RoofBlock.FACING,Direction.NORTH).with(RoofBlock.BASE,base));
        }
    }

    /** One roof cell falling toward {@code falls} from {@code base} quarters up its block (only into air). */
    static void cell(Frame f,int bx,int by,int bz,RoofShape shape,Direction falls,int base,Roof m){
        if(f.air(bx,by,bz))f.set(bx,by,bz,m.patch().getDefaultState().with(RoofBlock.SHAPE,shape).with(RoofBlock.FACING,falls).with(RoofBlock.BASE,base));
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
                // a window block faces out of its wall (2.48.0)
                Direction out=switch(side){case 'f'->Direction.NORTH;case 'b'->Direction.SOUTH;case 'l'->Direction.WEST;default->Direction.EAST;};
                BlockState p=TB.out(small&&r.nextBoolean()?frosted:pane,out);
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
        canopy(f,bx-1,bx+1,bz-1,1,3,canopy);
        if(light)f.set(bx+1,2,bz-1,Canvas.rod(Blocks.END_ROD,Direction.NORTH));
        if(name!=null)f.wallSign(bx-1,2,bz-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLACK,false,"",name);
    }

    /** A light in the middle of a room: a lantern hanging from the ceiling, or a white tube light. */
    static void roomLight(Frame f,int bx,int fy,int bz,boolean warm){
        if(warm)f.hangingLantern(bx,fy+3,bz,false);
        else f.set(bx,fy+3,bz,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
    }

    /** An air conditioner's outdoor unit against a wall, its fan toward {@code out}. */
    static void airCon(Frame f,int bx,int by,int bz,Direction out){
        if(f.air(bx,by,bz))f.set(bx,by,bz,TB.facing("air_conditioner",out));
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
            for(int y=g+1;y<h;y++)f.set(x,y,z,y==g+1?TB.WALL_FOOT:Pal.blockWall(f.wx(x,z),f.y0+y,f.wz(x,z)));
            f.set(x,Math.max(h,g+1),z,TB.wallCap());
        }
    }
    /** A clipped hedge (Botand's leaves on cards, 2.48.0), h blocks high. */
    static void hedge(Frame f,int bx0,int bz0,int bx1,int bz1,int h,Block leaves){
        for(int x=Math.min(bx0,bx1);x<=Math.max(bx0,bx1);x++)for(int z=Math.min(bz0,bz1);z<=Math.max(bz0,bz1);z++)
            for(int y=1;y<=h;y++)f.set(x,y,z,TB.HEDGE);
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
