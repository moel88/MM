package dev.sangwoo.celestialfall.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A roof laid at its true pitch (2.47.0), where blocks could only climb in half steps: a plate of tiles or sheet whose
 * top runs straight from the ridge down to the eaves. Each block is one cell of a roof, and says which part of the roof
 * lies over it: one slope, a hip (two slopes meeting across the cell's diagonal), a ridge across its middle, the end of
 * a ridge, or the peak of a pyramid; which way that faces; how high in the block its lowest edge sits (in quarters);
 * whether the space under the plate down to the block's floor is filled (over the walls, closing the attic and the
 * gable ends); and whether a ridge cap runs along its top edge. Every shape is the lowest of up to four planes rising
 * at the block's pitch from the cell's sides, so neighbouring cells always meet edge to edge. The look is drawn in code
 * (client/town/RoofModel); the collision follows the plate in small steps, so it can be walked up.
 */
public class RoofBlock extends Block {
    public enum Shape implements StringIdentifiable {
        SLOPE,HIP,RIDGE,RIDGE_END,PEAK;
        @Override public String asString(){return name().toLowerCase(java.util.Locale.ROOT);}
    }
    public static final EnumProperty<Shape> SHAPE=EnumProperty.of("shape",Shape.class);
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    public static final IntProperty BASE=IntProperty.of("base",0,3);
    public static final BooleanProperty FILLED=BooleanProperty.of("filled"),CAP=BooleanProperty.of("cap");
    /** Rise per block of run, and the plate's thickness straight down (blocks). */
    public final float pitch,thickness;
    private final Map<BlockState,VoxelShape> shapes=new IdentityHashMap<>();

    public RoofBlock(float pitch,float thickness,Settings s){
        super(s.nonOpaque());
        this.pitch=pitch;this.thickness=thickness;
        setDefaultState(getDefaultState().with(SHAPE,Shape.SLOPE).with(FACING,Direction.NORTH).with(BASE,0).with(FILLED,false).with(CAP,false));
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(SHAPE,FACING,BASE,FILLED,CAP);}

    /** Placed by hand: one slope falling toward the player. */
    @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite());}

    @Override public BlockState rotate(BlockState s,BlockRotation r){return s.with(FACING,r.rotate(s.get(FACING)));}
    /** A mirror turns a hip's corner the other way round; every other shape mirrors as its facing does. */
    @Override public BlockState mirror(BlockState s,BlockMirror m){
        var f=s.get(FACING);
        return s.with(FACING,s.get(SHAPE)==Shape.HIP?m.apply(f.rotateYClockwise()):m.apply(f));
    }

    /** The height of the roof's top above the block's floor (blocks) at u (toward east) and v (toward south), 0..1. */
    public double height(BlockState s,double u,double v){return height(s.get(SHAPE),s.get(FACING),s.get(BASE),pitch,u,v);}
    /**
     * The roof's top over a cell: the lowest of the planes that make its shape, each rising at the pitch from one side
     * of the cell. A shape facing north has its slope falling toward north; a hip facing north falls toward north and
     * east (the north-east corner of a roof); a ridge facing north runs east-west; the end of a ridge facing north is
     * where a ridge running north-south ends in the slope falling north. Other facings turn the shape clockwise.
     */
    public static double height(Shape shape,Direction facing,int base,float pitch,double u,double v){
        double cu,cv;
        switch(facing){
            case EAST->{cu=v;cv=1-u;}
            case SOUTH->{cu=1-u;cv=1-v;}
            case WEST->{cu=1-v;cv=u;}
            default->{cu=u;cv=v;}
        }
        double b=base/4.0;
        double n=b+pitch*cv,s=b+pitch*(1-cv),e=b+pitch*(1-cu),w=b+pitch*cu;
        return switch(shape){
            case SLOPE->n;
            case HIP->Math.min(n,e);
            case RIDGE->Math.min(n,s);
            case RIDGE_END->Math.min(n,Math.min(e,w));
            case PEAK->Math.min(Math.min(n,s),Math.min(e,w));
        };
    }

    /** The plate in steps of a quarter block each way, from its underside (or the floor where filled) up to its top. */
    @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
        return shapes.computeIfAbsent(s,this::shapeOf);
    }
    private VoxelShape shapeOf(BlockState s){
        VoxelShape out=VoxelShapes.empty();
        boolean filled=s.get(FILLED);
        for(int i=0;i<4;i++)for(int j=0;j<4;j++){
            double lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;
            for(int a=0;a<=2;a++)for(int b=0;b<=2;b++){double h=height(s,(i+a/2.0)/4,(j+b/2.0)/4);lo=Math.min(lo,h);hi=Math.max(hi,h);}
            double top=Math.min(1.5,hi),bottom=filled?0:Math.max(0,lo-thickness);
            if(top-bottom<1/64.0)continue;
            out=VoxelShapes.union(out,VoxelShapes.cuboid(i/4.0,bottom,j/4.0,(i+1)/4.0,top,(j+1)/4.0));
        }
        return out.isEmpty()?VoxelShapes.cuboid(0,0,0,1,1/16.0,1):out.simplify();
    }
}
