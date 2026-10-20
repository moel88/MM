package dev.sangwoo.celestialfall.town;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import java.util.*;

/**
 * The seaside town's own blocks (2.46.0): the materials of the two photos the map follows (fresh and worn asphalt with
 * road paint, pavers, concrete block walls, rough stone pillars, plaster, kawara, corrugated iron, weathered concrete)
 * and its street furniture (poles with sleeves, crossarms and transformers, street lamps, vending machines, air
 * conditioners, letterboxes, slat and chain-link fences, gates, guardrails, balcony rails, curtained windows, a net),
 * and roofs laid at their true pitch (2.47.0, RoofBlock, drawn in code by client/town/RoofModel). Their 64x textures and
 * models are drawn by tools/map/blocks/make_blocks.py from the same list of names; vanilla blocks are not touched.
 * Every block drops itself.
 */
public final class TownBlocks {
    private TownBlocks() {}
    /** Every block in registration order, with the item that places it. */
    public static final Map<String,Block> ALL=new LinkedHashMap<>();
    public static final List<Block> CUTOUT=new ArrayList<>(),TRANSLUCENT=new ArrayList<>();

    private static FabricBlockSettings stone(){return FabricBlockSettings.copyOf(Blocks.STONE);}
    private static FabricBlockSettings metal(){return FabricBlockSettings.copyOf(Blocks.IRON_BARS);}
    private static FabricBlockSettings wood(){return FabricBlockSettings.copyOf(Blocks.SPRUCE_PLANKS);}
    private static FabricBlockSettings thin(){return FabricBlockSettings.copyOf(Blocks.IRON_BARS).nonOpaque();}
    private static FabricBlockSettings sheet(){return FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f,6f);}

    // the ground: asphalt and its paint, the worn asphalt of the lot by the sea, pavers
    public static final Block ASPHALT=add("asphalt",new Block(stone()));
    public static final Block ASPHALT_SLAB=add("asphalt_slab",new SlabBlock(stone()));
    public static final Block ASPHALT_EDGE_LINE=add("asphalt_edge_line",new Facing(stone()));
    public static final Block ASPHALT_EDGE_LINE_SLAB=add("asphalt_edge_line_slab",new FacingSlab(stone()));
    public static final Block ASPHALT_CENTER_LINE=add("asphalt_center_line",new Axis(stone()));
    public static final Block ASPHALT_ZEBRA=add("asphalt_zebra",new Axis(stone()));
    public static final Block ASPHALT_STOP_LINE=add("asphalt_stop_line",new Axis(stone()));
    public static final Block WORN_ASPHALT=add("worn_asphalt",new Block(stone()));
    public static final Block WORN_ASPHALT_CRACKED=add("worn_asphalt_cracked",new Block(stone()));
    public static final Block PATCHED_ASPHALT=add("patched_asphalt",new Block(stone()));
    public static final Block WORN_ASPHALT_LINE=add("worn_asphalt_line",new Axis(stone()));
    public static final Block PAVING=add("paving",new Block(stone()));
    public static final Block PAVING_SLAB=add("paving_slab",new SlabBlock(stone()));
    // walls and buildings
    public static final Block BLOCK_WALL=add("block_wall",new Block(stone()));
    public static final Block BLOCK_WALL_SLAB=add("block_wall_slab",new SlabBlock(stone()));
    public static final Block STONE_PILLAR=add("stone_pillar",new Block(stone()));
    public static final Block PLASTER=add("plaster",new Block(stone()));
    public static final Block WEATHERED_CONCRETE=add("weathered_concrete",new Block(stone()));
    public static final Block KAWARA=add("kawara",new Axis(stone()));
    public static final Block KAWARA_SLAB=add("kawara_slab",new AxisSlab(stone()));
    public static final Block CORRUGATED_IRON=add("corrugated_iron",new Block(sheet()));
    public static final Block RUSTY_CORRUGATED_IRON=add("rusty_corrugated_iron",new Block(sheet()));
    public static final Block CORRUGATED_ROOF=add("corrugated_roof",new Axis(sheet()));
    public static final Block CORRUGATED_ROOF_SLAB=add("corrugated_roof_slab",new AxisSlab(sheet()));
    // thin panels that join their neighbours like glass panes
    public static final Block SLAT_FENCE=cutout(add("slat_fence",new Pane(wood().nonOpaque())));
    public static final Block CHAIN_LINK_FENCE=cutout(add("chain_link_fence",new Pane(thin())));
    public static final Block METAL_GATE=cutout(add("metal_gate",new Pane(thin())));
    public static final Block GUARDRAIL=cutout(add("guardrail",new Pane(thin())));
    public static final Block BALCONY_RAIL=cutout(add("balcony_rail",new Pane(thin())));
    public static final Block NET=cutout(add("net",new Pane(FabricBlockSettings.copyOf(Blocks.WHITE_WOOL).nonOpaque().noCollision())));
    public static final Block CURTAIN_WINDOW=translucent(add("curtain_window",new Pane(FabricBlockSettings.copyOf(Blocks.GLASS_PANE))));
    // the poles and what hangs on them, the street's furniture; a shape is given for the thing facing north, its back
    // against whatever it hangs on to the south
    public static final Block UTILITY_POLE=add("utility_pole",new Post(3.8,12.2,stone()));
    public static final Block POLE_SLEEVE=add("pole_sleeve",new Post(3.3,12.7,stone()));
    public static final Block PIPE_POST=add("pipe_post",new Post(7,9,metal()));
    public static final Block POLE_CROSSARM=add("pole_crossarm",new Crossarm(true,metal()));
    public static final Block POLE_ARM=add("pole_arm",new Crossarm(false,metal().noCollision()));
    public static final Block POLE_TRANSFORMER=add("pole_transformer",new Shaped(metal(),Block.createCuboidShape(3,1,6,13,15,16)));
    public static final Block STREET_LAMP_ARM=add("street_lamp_arm",new Shaped(metal().noCollision(),Block.createCuboidShape(7,10,0,9,13,16)));
    public static final Block STREET_LAMP=add("street_lamp",new Shaped(metal().noCollision().luminance(15),Block.createCuboidShape(4,8,2,12,13,16)));
    public static final Block AIR_CONDITIONER=add("air_conditioner",new Shaped(metal(),Block.createCuboidShape(1,0,5,15,10,16)));
    public static final Block LETTERBOX=add("letterbox",new Shaped(metal(),Block.createCuboidShape(4,5,12,12,11,16)));
    public static final Block FALLEN_PETALS=cutout(add("fallen_petals",new Litter(FabricBlockSettings.copyOf(Blocks.PINK_PETALS).noCollision().nonOpaque())));
    public static final Block VENDING_MACHINE=add("vending_machine",new VendingMachine(metal().luminance(s->s.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER?11:6)));
    // roofs at their true pitch (2.47.0): Botand's kawara and corrugated sheet, and the town's painted, slate and copper
    // roofs; each names its top, its edge, its underside (and the wall closing the attic) and its ridge cap
    public static final Map<RoofBlock,String[]> ROOFS=new LinkedHashMap<>();
    public static final RoofBlock ROOF_KAWARA=roof("roof_kawara",0.5f,3/16f,stone(),"town/kawara","town/kawara_side","town/plaster_0","town/kawara_ridge");
    public static final RoofBlock ROOF_SHEET=roof("roof_sheet",0.25f,1.5f/16,sheet(),"town/corrugated_roof","town/corrugated_roof","town/corrugated_iron_0","town/roof_flashing");
    public static final RoofBlock ROOF_BLACK=roof("roof_black",0.5f,2.5f/16,stone(),"minecraft:polished_blackstone","minecraft:polished_blackstone","town/plaster_0","minecraft:polished_blackstone_bricks");
    public static final RoofBlock ROOF_BLUE=roof("roof_blue",0.5f,2f/16,stone(),"minecraft:dark_prismarine","minecraft:dark_prismarine","town/plaster_1","minecraft:dark_prismarine");
    public static final RoofBlock ROOF_SLATE=roof("roof_slate",0.5f,2.5f/16,stone(),"minecraft:stone_bricks","minecraft:stone_bricks","town/plaster_2","minecraft:stone_bricks");
    public static final RoofBlock ROOF_RED=roof("roof_red",0.5f,2f/16,stone(),"minecraft:bricks","minecraft:bricks","town/plaster_0","minecraft:bricks");
    public static final RoofBlock ROOF_BROWN=roof("roof_brown",0.5f,2f/16,stone(),"minecraft:mud_bricks","minecraft:mud_bricks","town/plaster_1","minecraft:mud_bricks");
    public static final RoofBlock ROOF_COPPER=roof("roof_copper",0.5f,1.5f/16,sheet(),"minecraft:oxidized_cut_copper","minecraft:oxidized_cut_copper","town/plaster_0","minecraft:oxidized_copper");
    public static final RoofBlock ROOF_RUST=roof("roof_rust",0.5f,1.5f/16,sheet(),"minecraft:exposed_cut_copper","minecraft:exposed_cut_copper","town/plaster_2","minecraft:exposed_copper");
    public static final RoofBlock ROOF_TEAL=roof("roof_teal",0.5f,1.5f/16,sheet(),"minecraft:weathered_cut_copper","minecraft:weathered_cut_copper","town/plaster_1","minecraft:weathered_copper");

    private static <B extends Block> B add(String name,B block){ALL.put(name,block);return block;}
    /** A roof block and its textures: names under the town's own textures, or minecraft: blocks' (block/ is added). */
    private static RoofBlock roof(String name,float pitch,float thickness,FabricBlockSettings settings,String... look){
        var b=add(name,new RoofBlock(pitch,thickness,settings));
        String[] ids=new String[look.length];
        for(int i=0;i<look.length;i++)ids[i]=look[i].startsWith("minecraft:")?"minecraft:block/"+look[i].substring(10):CelestialFall.ID+":block/"+look[i];
        ROOFS.put(b,ids);
        return b;
    }
    private static Block cutout(Block b){CUTOUT.add(b);return b;}
    private static Block translucent(Block b){TRANSLUCENT.add(b);return b;}

    public static void initialize(){
        for(var e:ALL.entrySet()){
            Registry.register(Registries.BLOCK,CelestialFall.id(e.getKey()),e.getValue());
            Item item=e.getValue() instanceof VendingMachine?new TallBlockItem(e.getValue(),new Item.Settings()):new BlockItem(e.getValue(),new Item.Settings());
            Registry.register(Registries.ITEM,CelestialFall.id(e.getKey()),item);
        }
        Registry.register(Registries.ITEM_GROUP,CelestialFall.id("town"),FabricItemGroup.builder().icon(()->new ItemStack(VENDING_MACHINE))
            .displayName(Text.translatable("itemGroup.celestialfall.town")).entries((context,entries)->{for(var b:ALL.values())entries.add(b);}).build());
    }

    /** Rotates a box given for a block facing north to the given facing. */
    static VoxelShape turn(Box box,Direction facing){
        double x0=box.minX*16,z0=box.minZ*16,x1=box.maxX*16,z1=box.maxZ*16,y0=box.minY*16,y1=box.maxY*16;
        return switch(facing){
            case SOUTH->Block.createCuboidShape(16-x1,y0,16-z1,16-x0,y1,16-z0);
            case EAST->Block.createCuboidShape(16-z1,y0,x0,16-z0,y1,x1);
            case WEST->Block.createCuboidShape(z0,y0,16-x1,z1,y1,16-x0);
            default->Block.createCuboidShape(x0,y0,z0,x1,y1,z1);
        };
    }

    /** A full block with a horizontal facing: road paint along one side. */
    public static class Facing extends HorizontalFacingBlock {
        public Facing(Settings s){super(s);setDefaultState(getDefaultState().with(FACING,Direction.NORTH));}
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getHorizontalPlayerFacing());}
    }
    /** A full block lying along a horizontal axis: centre lines, zebra bars, roof tiles running down the slope. */
    public static class Axis extends Block {
        public Axis(Settings s){super(s);setDefaultState(getDefaultState().with(Properties.HORIZONTAL_AXIS,Direction.Axis.X));}
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(Properties.HORIZONTAL_AXIS);}
        @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(Properties.HORIZONTAL_AXIS,c.getHorizontalPlayerFacing().getAxis());}
        @Override public BlockState rotate(BlockState s,BlockRotation r){return turnAxis(s,r);}
    }
    /** A quarter turn swaps a horizontal axis; a half turn keeps it. */
    static BlockState turnAxis(BlockState s,BlockRotation r){
        if(r!=BlockRotation.CLOCKWISE_90&&r!=BlockRotation.COUNTERCLOCKWISE_90)return s;
        return s.with(Properties.HORIZONTAL_AXIS,s.get(Properties.HORIZONTAL_AXIS)==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X);
    }
    /** A slab lying along a horizontal axis: a roof's half steps keep their tiles running down the slope. */
    public static class AxisSlab extends SlabBlock {
        public AxisSlab(Settings s){super(s);setDefaultState(getDefaultState().with(Properties.HORIZONTAL_AXIS,Direction.Axis.X));}
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){super.appendProperties(b);b.add(Properties.HORIZONTAL_AXIS);}
        @Override public BlockState getPlacementState(ItemPlacementContext c){var s=super.getPlacementState(c);return s==null?null:s.with(Properties.HORIZONTAL_AXIS,c.getHorizontalPlayerFacing().getAxis());}
        @Override public BlockState rotate(BlockState s,BlockRotation r){return turnAxis(s,r);}
    }
    /** A slab with a horizontal facing: an edge line on a street's half step. */
    public static class FacingSlab extends SlabBlock {
        public FacingSlab(Settings s){super(s);setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH));}
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){super.appendProperties(b);b.add(Properties.HORIZONTAL_FACING);}
        @Override public BlockState getPlacementState(ItemPlacementContext c){var s=super.getPlacementState(c);return s==null?null:s.with(Properties.HORIZONTAL_FACING,c.getHorizontalPlayerFacing());}
        @Override public BlockState rotate(BlockState s,BlockRotation r){return s.with(Properties.HORIZONTAL_FACING,r.rotate(s.get(Properties.HORIZONTAL_FACING)));}
        @Override public BlockState mirror(BlockState s,BlockMirror m){return s.rotate(m.getRotation(s.get(Properties.HORIZONTAL_FACING)));}
    }
    /** A thin panel that joins its neighbours like a glass pane, and the posts it is hung between. */
    public static class Pane extends PaneBlock {
        public Pane(Settings s){super(s);}
        private static BlockState posts(BlockState s,WorldAccess w,BlockPos p){
            for(var d:Direction.Type.HORIZONTAL)if(w.getBlockState(p.offset(d)).getBlock() instanceof Post)s=s.with(FACING_PROPERTIES.get(d),true);
            return s;
        }
        @Override public BlockState getPlacementState(ItemPlacementContext c){var s=super.getPlacementState(c);return s==null?null:posts(s,c.getWorld(),c.getBlockPos());}
        @Override public BlockState getStateForNeighborUpdate(BlockState s,Direction d,BlockState n,WorldAccess w,BlockPos p,BlockPos np){
            var out=super.getStateForNeighborUpdate(s,d,n,w,p,np);
            return d.getAxis().isHorizontal()&&n.getBlock() instanceof Post?out.with(FACING_PROPERTIES.get(d),true):out;
        }
    }
    /** A round post standing in the middle of its block: concrete poles, their sleeves, thin pipes (drawn round by client/town/PoleModel). */
    public static class Post extends Block {
        private final VoxelShape shape;
        public Post(double from,double to,Settings s){super(s.nonOpaque());shape=Block.createCuboidShape(from,0,from,to,16,to);}
        @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return shape;}
    }
    /** A pole's crossarm along a horizontal axis: through the pole (with it) or reaching out from it. */
    public static class Crossarm extends Axis {
        private final VoxelShape x,z;
        public Crossarm(boolean pole,Settings s){
            super(s.nonOpaque());
            VoxelShape px=Block.createCuboidShape(0,11,7,16,13.5,9),pz=Block.createCuboidShape(7,11,0,9,13.5,16),post=Block.createCuboidShape(3.8,0,3.8,12.2,16,12.2);
            x=pole?VoxelShapes.union(px,post):px;z=pole?VoxelShapes.union(pz,post):pz;
        }
        @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return s.get(Properties.HORIZONTAL_AXIS)==Direction.Axis.X?x:z;}
    }
    /** A small thing with a front, its shape given facing north and turned: transformers, lamps, air conditioners. */
    public static class Shaped extends HorizontalFacingBlock {
        private final VoxelShape[] shapes=new VoxelShape[4];
        public Shaped(Settings s,VoxelShape north){
            super(s.nonOpaque());setDefaultState(getDefaultState().with(FACING,Direction.NORTH));
            for(var d:Direction.Type.HORIZONTAL)shapes[d.getHorizontal()]=turn(north.getBoundingBox(),d);
        }
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite());}
        @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return shapes[s.get(FACING).getHorizontal()];}
    }
    /**
     * Litter lying flat on whatever is under it: cherry petals on the sidewalk and the road (2.47.0; the game's pink
     * petals grow only on soil). No collision; it goes when what holds it goes.
     */
    public static class Litter extends Block {
        private static final VoxelShape SHAPE=Block.createCuboidShape(0,0,0,16,0.5,16);
        public Litter(Settings s){super(s);}
        @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return SHAPE;}
        @Override public boolean canPlaceAt(BlockState s,WorldView w,BlockPos p){return w.getBlockState(p.down()).isSideSolidFullSquare(w,p.down(),Direction.UP);}
        @Override public BlockState getStateForNeighborUpdate(BlockState s,Direction d,BlockState n,WorldAccess w,BlockPos p,BlockPos np){
            return d==Direction.DOWN&&!canPlaceAt(s,w,p)?Blocks.AIR.getDefaultState():s;
        }
    }
    /** A drinks vending machine two blocks tall, its display lit; placing one sets both halves, breaking one clears both. */
    public static class VendingMachine extends HorizontalFacingBlock {
        public VendingMachine(Settings s){super(s.nonOpaque());setDefaultState(getDefaultState().with(FACING,Direction.NORTH).with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER));}
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING,Properties.DOUBLE_BLOCK_HALF);}
        @Override public BlockState getPlacementState(ItemPlacementContext c){
            var pos=c.getBlockPos();
            return pos.getY()<c.getWorld().getTopY()-1&&c.getWorld().getBlockState(pos.up()).canReplace(c)?getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite()):null;
        }
        @Override public void onPlaced(World w,BlockPos p,BlockState s,LivingEntity placer,ItemStack stack){w.setBlockState(p.up(),s.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER),Block.NOTIFY_ALL);}
        @Override public BlockState getStateForNeighborUpdate(BlockState s,Direction d,BlockState n,WorldAccess w,BlockPos p,BlockPos np){
            var half=s.get(Properties.DOUBLE_BLOCK_HALF);
            if(d.getAxis()==Direction.Axis.Y&&(half==DoubleBlockHalf.LOWER)==(d==Direction.UP))
                return n.isOf(this)&&n.get(Properties.DOUBLE_BLOCK_HALF)!=half?s:Blocks.AIR.getDefaultState();
            return s;
        }
        @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
            var box=s.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?new Box(0,0,2/16.0,1,1,1):new Box(0,0,2/16.0,1,13/16.0,1);
            return turn(box,s.get(FACING));
        }
    }
}
