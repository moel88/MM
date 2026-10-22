package dev.sangwoo.celestialfall.town;

import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.property.BooleanProperty;
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
 * Every block drops itself. 2.48.0 adds what the photos' streets are made of and a block model file could not draw:
 * leaves as clusters on cards (hedges, crowns, blossom, ivy, a cherry's limbs), windows with real sash frames that
 * join into one, the houses' stucco, siding, tiles and trim, dark block walls and their grimy feet, the green
 * walking strip, drinks machines with a display behind glass, air conditioners with a fan guard, traffic mirrors.
 * 2.49.0 adds the world tree of the hourglass island's rift: neon sky-blue leaves that give light, silver bark, the
 * rift's white ground and its glowing cracks.
 */
public final class TownBlocks {
    private TownBlocks() {}
    /** Every block in registration order, with the item that places it. */
    public static final Map<String,Block> ALL=new LinkedHashMap<>();
    public static final List<Block> CUTOUT=new ArrayList<>(),CUTOUT_MIPPED=new ArrayList<>(),TRANSLUCENT=new ArrayList<>();

    private static FabricBlockSettings stone(){return FabricBlockSettings.copyOf(Blocks.STONE);}
    private static FabricBlockSettings metal(){return FabricBlockSettings.copyOf(Blocks.IRON_BARS);}
    private static FabricBlockSettings wood(){return FabricBlockSettings.copyOf(Blocks.SPRUCE_PLANKS);}
    private static FabricBlockSettings thin(){return FabricBlockSettings.copyOf(Blocks.IRON_BARS).nonOpaque();}
    private static FabricBlockSettings sheet(){return FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f,6f);}
    /** Leaves that neither decay nor tick: soft, see-through to light, burnable, nothing spawns on them. */
    private static FabricBlockSettings leaves(MapColor colour){
        return FabricBlockSettings.create().mapColor(colour).strength(0.2f).sounds(BlockSoundGroup.GRASS).nonOpaque().allowsSpawning((s,w,p,t)->false)
            .suffocates((s,w,p)->false).blockVision((s,w,p)->false).burnable().pistonBehavior(PistonBehavior.DESTROY).solidBlock((s,w,p)->false);
    }
    private static FabricBlockSettings glass(){return FabricBlockSettings.copyOf(Blocks.GLASS);}

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
    // leaves as the photos show them (2.48.0): clusters on cards, drawn in code (client/town/FoliageModel)
    public static final Block HEDGE=cutoutMipped(add("hedge",new Foliage(leaves(MapColor.DARK_GREEN))));
    public static final Block CANOPY=cutoutMipped(add("canopy",new Foliage(leaves(MapColor.DARK_GREEN))));
    public static final Block CHERRY_BLOSSOM=cutoutMipped(add("cherry_blossom",new Foliage(leaves(MapColor.PINK))));
    public static final Block IVY=cutoutMipped(add("ivy",new Ivy(leaves(MapColor.DARK_GREEN).noCollision())));
    public static final Block CHERRY_BRANCH=add("cherry_branch",new Branch(FabricBlockSettings.copyOf(Blocks.CHERRY_LOG).nonOpaque()));
    public static final Block PINE_NEEDLES=cutoutMipped(add("pine_needles",new Foliage(leaves(MapColor.DARK_GREEN))));
    // windows with real sash frames (2.48.0, client/town/WindowModel): dark or white, the glass clear, curtained or frosted
    public static final Map<Window,String[]> WINDOWS=new LinkedHashMap<>();
    public static final Window WINDOW=window("window","window_frame_dark","window_glass",null);
    public static final Window WINDOW_CURTAIN=window("window_curtain","window_frame_dark","window_glass","window_curtain");
    public static final Window WINDOW_FROSTED=window("window_frosted","window_frame_dark","window_frosted",null);
    public static final Window WINDOW_WHITE=window("window_white","window_frame_white","window_glass",null);
    public static final Window WINDOW_WHITE_CURTAIN=window("window_white_curtain","window_frame_white","window_glass","window_curtain");
    // the houses' materials (2.48.0): stucco in the photos' colours, lap siding, tiles, the trim boards, smooth concrete,
    // the grimy foot of a block wall and the dark block walls
    public static final Block STUCCO_WHITE=add("stucco_white",new Block(stone()));
    public static final Block STUCCO_CREAM=add("stucco_cream",new Block(stone()));
    public static final Block STUCCO_PINK=add("stucco_pink",new Block(stone()));
    public static final Block STUCCO_GRAY=add("stucco_gray",new Block(stone()));
    public static final Block SIDING=add("siding",new Block(stone()));
    public static final Block TILE_WALL=add("tile_wall",new Block(stone()));
    public static final Block TRIM_DARK=add("trim_dark",new Block(stone()));
    public static final Block TRIM_WHITE=add("trim_white",new Block(stone()));
    public static final Block CONCRETE_SMOOTH=add("concrete_smooth",new Block(stone()));
    public static final Block BLOCK_WALL_FOOT=add("block_wall_foot",new Block(stone()));
    public static final Block BLOCK_WALL_DARK=add("block_wall_dark",new Block(stone()));
    public static final Block BLOCK_WALL_DARK_SLAB=add("block_wall_dark_slab",new SlabBlock(stone()));
    public static final Block ASPHALT_GREEN=add("asphalt_green",new Facing(stone()));
    public static final Block SLAT_FENCE_DARK=cutout(add("slat_fence_dark",new Pane(wood().nonOpaque())));
    public static final Block SLAT_FENCE_LIGHT=cutout(add("slat_fence_light",new Pane(wood().nonOpaque())));
    public static final Block WOOD_BOARDS_DARK=add("wood_boards_dark",new Block(wood()));
    public static final Block WOOD_BOARDS=add("wood_boards",new Block(wood()));
    public static final Block TIMBER_DARK=add("timber_dark",new Block(wood()));
    // more of the street's furniture (2.48.0, client/town/FurnitureModel): the white drinks machine, the traffic mirror on its post
    public static final Block VENDING_MACHINE_WHITE=add("vending_machine_white",new VendingMachine(metal().luminance(s->s.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER?11:6)));
    public static final Block MIRROR_POST=add("mirror_post",new Post(7.1,8.9,metal()));
    public static final Block TRAFFIC_MIRROR=add("traffic_mirror",new Shaped(metal(),Block.createCuboidShape(1,2,5.5,15,16,8.5)));
    // the world tree in the rift of the hourglass island (2.49.0, map v3.0): its leaves glowing a neon sky blue on cards
    // (client/town/FoliageModel), its silver bark (some of it threaded with light), the rift's white ground and its cracks of light
    public static final Block WORLD_TREE_LEAVES=cutoutMipped(add("world_tree_leaves",new GlowFoliage(leaves(MapColor.LIGHT_BLUE).luminance(15))));
    public static final Block WORLD_TREE_BARK=add("world_tree_bark",new PillarBlock(FabricBlockSettings.copyOf(Blocks.BIRCH_LOG).mapColor(MapColor.WHITE_GRAY)));
    public static final Block WORLD_TREE_BARK_GLOW=add("world_tree_bark_glow",new PillarBlock(FabricBlockSettings.copyOf(Blocks.BIRCH_LOG).mapColor(MapColor.WHITE_GRAY).luminance(10)));
    public static final Block RIFT_GROUND=add("rift_ground",new Block(stone().mapColor(MapColor.WHITE)));
    public static final Block RIFT_CRACK=add("rift_crack",new Crack(stone().mapColor(MapColor.LIGHT_BLUE).luminance(12)));
    // the fantasy capital's blue roofs (2.49.0): fish-scale slates as a block, a slab, stairs for the towers' cones,
    // and a roof at its true pitch
    public static final Block BLUE_SHINGLES=add("blue_shingles",new Block(stone().mapColor(MapColor.BLUE)));
    public static final Block BLUE_SHINGLES_SLAB=add("blue_shingles_slab",new SlabBlock(stone().mapColor(MapColor.BLUE)));
    public static final Block BLUE_SHINGLES_STAIRS=add("blue_shingles_stairs",new StairsBlock(BLUE_SHINGLES.getDefaultState(),stone().mapColor(MapColor.BLUE)));
    public static final RoofBlock ROOF_NAVY=roof("roof_navy",0.5f,2f/16,stone(),"town/blue_shingles","town/blue_shingles","town/plaster_0","town/blue_shingles");
    // the cyber city of the hourglass island (2.49.0, map v3.0, the user's photo): neon tubes, glass curtain walls (lit
    // ones glow from inside), dark metal cladding, LED walls playing their loops (animated textures), wet asphalt and dark paving
    public static final Block NEON_PINK=add("neon_pink",new Block(stone().mapColor(MapColor.PINK).luminance(15)));
    public static final Block NEON_PURPLE=add("neon_purple",new Block(stone().mapColor(MapColor.PURPLE).luminance(15)));
    public static final Block NEON_CYAN=add("neon_cyan",new Block(stone().mapColor(MapColor.CYAN).luminance(15)));
    public static final Block GLASS_CURTAIN=add("glass_curtain",new Block(stone().mapColor(MapColor.BLACK)));
    public static final Block GLASS_CURTAIN_LIT=add("glass_curtain_lit",new Block(stone().mapColor(MapColor.YELLOW).luminance(9)));
    public static final Block CYBER_PANEL=add("cyber_panel",new Block(stone().mapColor(MapColor.GRAY)));
    public static final Block LED_SCREEN_PINK=add("led_screen_pink",new Block(stone().mapColor(MapColor.PINK).luminance(13)));
    public static final Block LED_SCREEN_CYAN=add("led_screen_cyan",new Block(stone().mapColor(MapColor.CYAN).luminance(13)));
    public static final Block LED_SCREEN_MIX=add("led_screen_mix",new Block(stone().mapColor(MapColor.MAGENTA).luminance(13)));
    public static final Block WET_ASPHALT=add("wet_asphalt",new Block(stone().mapColor(MapColor.BLACK)));
    public static final Block CYBER_PAVING=add("cyber_paving",new Block(stone().mapColor(MapColor.GRAY)));

    private static <B extends Block> B add(String name,B block){ALL.put(name,block);return block;}
    /** A roof block and its textures: names under the town's own textures, or minecraft: blocks' (block/ is added). */
    private static RoofBlock roof(String name,float pitch,float thickness,FabricBlockSettings settings,String... look){
        var b=add(name,new RoofBlock(pitch,thickness,settings));
        String[] ids=new String[look.length];
        for(int i=0;i<look.length;i++)ids[i]=look[i].startsWith("minecraft:")?"minecraft:block/"+look[i].substring(10):CelestialFall.ID+":block/"+look[i];
        ROOFS.put(b,ids);
        return b;
    }
    /** A window block and its textures: the frame, the glass, and the curtain behind it or none. */
    private static Window window(String name,String frame,String glass,String curtain){
        var b=add(name,new Window(glass()));
        WINDOWS.put(b,new String[]{frame,glass,curtain});
        TRANSLUCENT.add(b);
        return b;
    }
    private static Block cutout(Block b){CUTOUT.add(b);return b;}
    private static Block cutoutMipped(Block b){CUTOUT_MIPPED.add(b);return b;}
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

    /**
     * Leaves as clusters on cards (2.48.0, client/town/FoliageModel): hedges, trees' crowns, cherry blossom. A full
     * block that neither decays nor ticks; the face between two of them is not drawn (the cards fill it).
     */
    public static class Foliage extends Block {
        public Foliage(Settings s){super(s);}
        @Override public boolean isSideInvisible(BlockState s,BlockState from,Direction d){return from.isOf(this);}
    }
    /**
     * The world tree's leaves (2.49.0): leaves that give light and let all the daylight through, so the white ground
     * under the great crown is not dappled with the shade of its uneven layers.
     */
    public static class GlowFoliage extends Foliage {
        public GlowFoliage(Settings s){super(s);}
        @Override public boolean isTransparent(BlockState s,BlockView w,BlockPos p){return true;}
    }
    /** Ivy on a wall (2.48.0): cards of leaves lying against the wall behind, which it falls with; nothing to walk into. */
    public static class Ivy extends HorizontalFacingBlock {
        private final VoxelShape[] shapes=new VoxelShape[4];
        public Ivy(Settings s){
            super(s);setDefaultState(getDefaultState().with(FACING,Direction.NORTH));
            for(var d:Direction.Type.HORIZONTAL)shapes[d.getHorizontal()]=turn(new Box(0,0,10/16.0,1,1,1),d);
        }
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return shapes[s.get(FACING).getHorizontal()];}
        @Override public BlockState getPlacementState(ItemPlacementContext c){
            var side=c.getSide();
            var s=getDefaultState().with(FACING,side.getAxis().isHorizontal()?side:c.getHorizontalPlayerFacing().getOpposite());
            return s.canPlaceAt(c.getWorld(),c.getBlockPos())?s:null;
        }
        @Override public boolean canPlaceAt(BlockState s,WorldView w,BlockPos p){
            var back=p.offset(s.get(FACING).getOpposite());
            return w.getBlockState(back).isSideSolidFullSquare(w,back,s.get(FACING));
        }
        @Override public BlockState getStateForNeighborUpdate(BlockState s,Direction d,BlockState n,WorldAccess w,BlockPos p,BlockPos np){
            return d==s.get(FACING).getOpposite()&&!canPlaceAt(s,w,p)?Blocks.AIR.getDefaultState():s;
        }
    }
    /**
     * A cherry's limb along an axis (2.48.0): a round branch five sixteenths across, or where {@code thick} the trunk
     * and the limbs where they leave it, eleven sixteenths across; drawn in code.
     */
    public static class Branch extends PillarBlock {
        public static final BooleanProperty THICK=BooleanProperty.of("thick");
        private static final VoxelShape X=Block.createCuboidShape(0,5.5,5.5,16,10.5,10.5),Y=Block.createCuboidShape(5.5,0,5.5,10.5,16,10.5),Z=Block.createCuboidShape(5.5,5.5,0,10.5,10.5,16);
        private static final VoxelShape TX=Block.createCuboidShape(0,2.5,2.5,16,13.5,13.5),TY=Block.createCuboidShape(2.5,0,2.5,13.5,16,13.5),TZ=Block.createCuboidShape(2.5,2.5,0,13.5,13.5,16);
        public Branch(Settings s){super(s);setDefaultState(getDefaultState().with(THICK,false));}
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){super.appendProperties(b);b.add(THICK);}
        @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
            boolean t=s.get(THICK);
            return switch(s.get(AXIS)){case X->t?TX:X;case Y->t?TY:Y;case Z->t?TZ:Z;};
        }
    }
    /**
     * A crack of light in the rift's ground (2.49.0): a full block whose glowing seam runs from its middle to the middle
     * of each side where another crack lies, so a line of them, however it turns, is one crack; with one neighbour it
     * is the crack's tip.
     */
    public static class Crack extends Block {
        private static final BooleanProperty[] SIDES={Properties.NORTH,Properties.EAST,Properties.SOUTH,Properties.WEST};
        public Crack(Settings s){super(s);var d=getDefaultState();for(var p:SIDES)d=d.with(p,false);setDefaultState(d);}
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(SIDES);}
        /** The side's property: Properties.NORTH for north, and so on. */
        public static BooleanProperty side(Direction d){
            return switch(d){
                case NORTH->Properties.NORTH; case EAST->Properties.EAST; case SOUTH->Properties.SOUTH; case WEST->Properties.WEST;
                default->throw new IllegalArgumentException("not a side: "+d);
            };
        }
        /** The state with its seams joined to the cracks round it. */
        public static BlockState joined(BlockState s,BlockView w,BlockPos p){
            for(var d:Direction.Type.HORIZONTAL)s=s.with(side(d),w.getBlockState(p.offset(d)).getBlock() instanceof Crack);
            return s;
        }
        @Override public BlockState getPlacementState(ItemPlacementContext c){return joined(getDefaultState(),c.getWorld(),c.getBlockPos());}
        @Override public BlockState getStateForNeighborUpdate(BlockState s,Direction d,BlockState n,WorldAccess w,BlockPos p,BlockPos np){
            return d.getAxis().isHorizontal()?s.with(side(d),n.getBlock() instanceof Crack):s;
        }
        @Override public BlockState rotate(BlockState s,BlockRotation r){
            var out=s;for(var d:Direction.Type.HORIZONTAL)out=out.with(side(r.rotate(d)),s.get(side(d)));
            return out;
        }
        @Override public BlockState mirror(BlockState s,BlockMirror m){
            var out=s;for(var d:Direction.Type.HORIZONTAL)out=out.with(side(m.apply(d)),s.get(side(d)));
            return out;
        }
    }
    /**
     * A window with a real sash frame (2.48.0, client/town/WindowModel), facing out of its wall. Sashes beside, above
     * and below it that face the same way join it into one window: {@code left} and {@code right} are seen from
     * outside. Light passes; the glass is see-through.
     */
    public static class Window extends HorizontalFacingBlock {
        public static final BooleanProperty UP=Properties.UP,DOWN=Properties.DOWN,LEFT=BooleanProperty.of("left"),RIGHT=BooleanProperty.of("right");
        public Window(Settings s){super(s);setDefaultState(getDefaultState().with(FACING,Direction.NORTH).with(UP,false).with(DOWN,false).with(LEFT,false).with(RIGHT,false));}
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING,UP,DOWN,LEFT,RIGHT);}
        /** The way to the sash on this one's left seen from outside (the model's west side turned to the facing). */
        public static Direction left(Direction facing){return facing.rotateYCounterclockwise();}
        private static boolean joins(BlockState s,BlockState n){return n.getBlock() instanceof Window&&n.get(FACING)==s.get(FACING);}
        /** The state with its joins read from the sashes round it. */
        public static BlockState joined(BlockState s,BlockView w,BlockPos p){
            var f=s.get(FACING);
            return s.with(UP,joins(s,w.getBlockState(p.up()))).with(DOWN,joins(s,w.getBlockState(p.down())))
                .with(LEFT,joins(s,w.getBlockState(p.offset(left(f))))).with(RIGHT,joins(s,w.getBlockState(p.offset(left(f).getOpposite()))));
        }
        @Override public BlockState getPlacementState(ItemPlacementContext c){return joined(getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite()),c.getWorld(),c.getBlockPos());}
        @Override public BlockState getStateForNeighborUpdate(BlockState s,Direction d,BlockState n,WorldAccess w,BlockPos p,BlockPos np){
            var f=s.get(FACING);
            if(d==Direction.UP)return s.with(UP,joins(s,n));
            if(d==Direction.DOWN)return s.with(DOWN,joins(s,n));
            if(d==left(f))return s.with(LEFT,joins(s,n));
            if(d==left(f).getOpposite())return s.with(RIGHT,joins(s,n));
            return s;
        }
        @Override public boolean isSideInvisible(BlockState s,BlockState from,Direction d){return from.getBlock() instanceof Window;}
        @Override public float getAmbientOcclusionLightLevel(BlockState s,BlockView w,BlockPos p){return 1;}
    }
}
