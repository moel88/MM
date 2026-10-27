package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.block.enums.*;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.*;
import java.util.function.Consumer;

/**
 * The town while it is built: every block of the planned area from just above the sea floor up, held in memory (a
 * block state id per position) until {@link Chunks} writes it into the world as freshly generated chunks. Building in
 * memory makes tens of millions of blocks cheap, and the world then lights the chunks and shapes their fences, panes,
 * walls and stairs exactly as it does for chunks it generates itself. Also keeps the ground height of every column
 * and a 2D occupancy grid so lots never overlap.
 */
final class Canvas {
    /** The model covers y Y0..YMAX; below it the flat world's layers stay as they are. */
    static final int Y0=Plan.FLOOR+1,H=160,YMAX=Y0+H-1;
    static final char AIR=id(Blocks.AIR.getDefaultState()),WATER=id(Blocks.WATER.getDefaultState());
    private final char[] ids=new char[Plan.W*Plan.D*H];
    /** Block entity settings (sign text and the like) by position. */
    final Map<Long,Consumer<BlockEntity>> entities=new HashMap<>();
    long placed;
    /** The surface height of every column of the plan (the top solid block), filled by the terrain. */
    final int[] ground=new int[Plan.W*Plan.D];
    /** What owns a column (0 free, else a code), so later pieces keep clear of earlier ones. */
    final byte[] owner=new byte[Plan.W*Plan.D];

    Canvas(){
        // the flat ocean the world starts as: water up to the sea level, air above
        for(int c=0;c<Plan.W*Plan.D;c++){int b=c*H;for(int y=0;y<H;y++)ids[b+y]=Y0+y<=Plan.SEA?WATER:AIR;}
        Arrays.fill(ground,Plan.FLOOR);
    }
    static char id(BlockState s){int i=Block.getRawIdFromState(s);if(i<0||i>65535)throw new IllegalStateException("state id "+i);return (char)i;}
    static BlockState state(int id){return Block.getStateFromRawId(id);}

    static boolean inPlan(int x,int z){return x>=Plan.X0&&x<Plan.X0+Plan.W&&z>=Plan.Z0&&z<Plan.Z0+Plan.D;}
    static int idx(int x,int z){return (x-Plan.X0)*Plan.D+(z-Plan.Z0);}
    static boolean inModel(int x,int y,int z){return inPlan(x,z)&&y>=Y0&&y<=YMAX;}
    /** The raw state id at a position (callers check {@link #inModel}). */
    char raw(int x,int y,int z){return ids[idx(x,z)*H+(y-Y0)];}

    int ground(int x,int z){return inPlan(x,z)?ground[idx(x,z)]:Plan.FLOOR;}
    void ground(int x,int z,int y){if(inPlan(x,z))ground[idx(x,z)]=y;}
    int owner(int x,int z){return inPlan(x,z)?owner[idx(x,z)]:-1;}
    void own(int x,int z,int code){if(inPlan(x,z))owner[idx(x,z)]=(byte)code;}
    /** Whether a rectangle (inclusive) is free in the occupancy grid. */
    boolean free(int x0,int z0,int x1,int z1){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)if(owner(x,z)!=0)return false;
        return true;
    }
    void claim(int x0,int z0,int x1,int z1,int code){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)own(x,z,code);
    }

    void set(int x,int y,int z,BlockState s){
        if(!inModel(x,y,z))return;
        ids[idx(x,z)*H+(y-Y0)]=id(s);placed++;
    }
    void set(int x,int y,int z,Block b){set(x,y,z,b.getDefaultState());}
    BlockState get(int x,int y,int z){
        if(!inModel(x,y,z))return y<=Plan.SEA&&y>Plan.FLOOR?Blocks.WATER.getDefaultState():y<=Plan.FLOOR?Blocks.SAND.getDefaultState():Blocks.AIR.getDefaultState();
        return state(ids[idx(x,z)*H+(y-Y0)]);
    }
    boolean air(int x,int y,int z){return !inModel(x,y,z)?y>Plan.SEA:ids[idx(x,z)*H+(y-Y0)]==AIR;}
    boolean water(int x,int y,int z){return inModel(x,y,z)&&ids[idx(x,z)*H+(y-Y0)]==WATER;}
    /** Air or water: nothing solid. */
    boolean open(int x,int y,int z){if(!inModel(x,y,z))return true;char c=ids[idx(x,z)*H+(y-Y0)];return c==AIR||c==WATER;}
    void fill(int x0,int y0,int z0,int x1,int y1,int z1,BlockState s){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int y=Math.min(y0,y1);y<=Math.max(y0,y1);y++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)set(x,y,z,s);
    }
    void fill(int x0,int y0,int z0,int x1,int y1,int z1,Block b){fill(x0,y0,z0,x1,y1,z1,b.getDefaultState());}
    /** Air over a box (clearing room for a building or a road cut); below the sea level it is water again. */
    void clear(int x0,int y0,int z0,int x1,int y1,int z1){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int y=Math.min(y0,y1);y<=Math.max(y0,y1);y++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)set(x,y,z,Blocks.AIR.getDefaultState());
    }
    /** The highest non-air block of a column at or below {@code from}. */
    int top(int x,int z,int from){for(int y=Math.min(from,YMAX);y>=Y0;y--)if(!air(x,y,z))return y;return Plan.FLOOR;}

    // ---- shaped blocks ----
    static BlockState stairs(Block b,Direction facing,boolean top){
        return b.getDefaultState().with(StairsBlock.FACING,facing).with(StairsBlock.HALF,top?BlockHalf.TOP:BlockHalf.BOTTOM);
    }
    static BlockState slab(Block b,boolean top){return b.getDefaultState().with(SlabBlock.TYPE,top?SlabType.TOP:SlabType.BOTTOM);}
    static BlockState doubleSlab(Block b){return b.getDefaultState().with(SlabBlock.TYPE,SlabType.DOUBLE);}
    static BlockState axis(Block b,Direction.Axis a){return b.getDefaultState().with(Properties.AXIS,a);}
    static BlockState leaves(Block b){return b.getDefaultState().with(LeavesBlock.PERSISTENT,true);}
    static BlockState facing(Block b,Direction d){return b.getDefaultState().with(Properties.HORIZONTAL_FACING,d);}
    static BlockState trapdoor(Block b,Direction facing,boolean open,boolean top){
        return b.getDefaultState().with(TrapdoorBlock.FACING,facing).with(TrapdoorBlock.OPEN,open).with(TrapdoorBlock.HALF,top?BlockHalf.TOP:BlockHalf.BOTTOM);
    }
    static BlockState button(Block b,Direction facing,WallMountLocation face){return b.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(Properties.WALL_MOUNT_LOCATION,face);}
    static BlockState chain(Direction.Axis a){return Blocks.CHAIN.getDefaultState().with(Properties.AXIS,a);}
    static BlockState rod(Block b,Direction d){return b.getDefaultState().with(Properties.FACING,d);}
    void door(int x,int y,int z,Block b,Direction facing,boolean rightHinge){
        var s=b.getDefaultState().with(DoorBlock.FACING,facing).with(DoorBlock.HINGE,rightHinge?DoorHinge.RIGHT:DoorHinge.LEFT);
        set(x,y,z,s.with(DoorBlock.HALF,DoubleBlockHalf.LOWER));set(x,y+1,z,s.with(DoorBlock.HALF,DoubleBlockHalf.UPPER));
    }
    void tallPlant(int x,int y,int z,Block b){
        set(x,y,z,b.getDefaultState().with(TallPlantBlock.HALF,DoubleBlockHalf.LOWER));set(x,y+1,z,b.getDefaultState().with(TallPlantBlock.HALF,DoubleBlockHalf.UPPER));
    }
    /** A bed lying with its head toward {@code head}, foot at (x,z). */
    void bed(int x,int y,int z,Block b,Direction head){
        set(x,y,z,b.getDefaultState().with(BedBlock.FACING,head).with(BedBlock.PART,BedPart.FOOT));
        set(x+head.getOffsetX(),y,z+head.getOffsetZ(),b.getDefaultState().with(BedBlock.FACING,head).with(BedBlock.PART,BedPart.HEAD));
    }
    void hangingLantern(int x,int y,int z,boolean soul){set(x,y,z,(soul?Blocks.SOUL_LANTERN:Blocks.LANTERN).getDefaultState().with(LanternBlock.HANGING,true));}
    void lantern(int x,int y,int z){set(x,y,z,Blocks.LANTERN.getDefaultState());}
    /** A vine on the side of a block, hanging on the face toward {@code toWall}. */
    void vine(int x,int y,int z,Direction toWall){
        var p=switch(toWall){case NORTH->VineBlock.NORTH;case SOUTH->VineBlock.SOUTH;case EAST->VineBlock.EAST;default->VineBlock.WEST;};
        set(x,y,z,Blocks.VINE.getDefaultState().with(p,true));
    }
    /** Block entity settings for the block at a position (applied when the chunk is written). */
    void entity(int x,int y,int z,Consumer<BlockEntity> settings){if(inModel(x,y,z))entities.put(BlockPos.asLong(x,y,z),settings);}
    /** A sign on a wall, facing out toward {@code facing}, with up to four lines. */
    void wallSign(int x,int y,int z,Block sign,Direction facing,DyeColor color,boolean glow,String... lines){
        set(x,y,z,sign.getDefaultState().with(WallSignBlock.FACING,facing));
        text(x,y,z,color,glow,false,lines);
    }
    void standingSign(int x,int y,int z,Block sign,int rotation,DyeColor color,boolean glow,String... lines){
        set(x,y,z,sign.getDefaultState().with(SignBlock.ROTATION,rotation&15));
        text(x,y,z,color,glow,true,lines);
    }
    /** Sign text, written as the sign's saved data (the sign is not in a world yet, so its setters cannot be used). */
    private void text(int x,int y,int z,DyeColor color,boolean glow,boolean back,String... lines){
        var side=new NbtCompound();var messages=new NbtList();
        for(int i=0;i<4;i++)messages.add(NbtString.of(Text.Serializer.toJson(Text.literal(i<lines.length?lines[i]:""))));
        side.put("messages",messages);side.putString("color",color.getName());side.putBoolean("has_glowing_text",glow);
        var nbt=new NbtCompound();nbt.put("front_text",side);nbt.put("back_text",back?side.copy():blank());nbt.putBoolean("is_waxed",true);
        entity(x,y,z,be->{if(be instanceof SignBlockEntity)be.readNbt(nbt);});
    }
    private static NbtCompound blank(){
        var side=new NbtCompound();var messages=new NbtList();
        for(int i=0;i<4;i++)messages.add(NbtString.of(Text.Serializer.toJson(Text.empty())));
        side.put("messages",messages);side.putString("color","black");side.putBoolean("has_glowing_text",false);return side;
    }
}
