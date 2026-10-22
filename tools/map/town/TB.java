package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

/**
 * Botand's own town blocks (dev.sangwoo.celestialfall.town.TownBlocks), looked up by name: the builder's server runs
 * the Botand jar, so the town is built of the photos' materials (fine and worn asphalt with thin paint, pavers,
 * split-face block walls, plaster, kawara, corrugated iron) and its street furniture.
 */
final class TB {
    private TB(){}
    static BlockState of(String name){
        var b=Registries.BLOCK.get(new Identifier("celestialfall",name));
        if(b==Blocks.AIR)throw new IllegalStateException("no town block "+name+": is the Botand jar on the builder's server?");
        return b.getDefaultState();
    }
    static final BlockState ASPHALT=of("asphalt"),WORN_ASPHALT=of("worn_asphalt"),WORN_CRACKED=of("worn_asphalt_cracked"),PATCHED=of("patched_asphalt");
    static final BlockState PAVING=of("paving"),BLOCK_WALL=of("block_wall"),STONE_PILLAR=of("stone_pillar"),PLASTER=of("plaster"),WEATHERED=of("weathered_concrete");
    static final BlockState CORRUGATED=of("corrugated_iron"),RUSTY=of("rusty_corrugated_iron");
    static final BlockState SLAT_FENCE=of("slat_fence"),CHAIN_LINK=of("chain_link_fence"),GATE=of("metal_gate"),GUARDRAIL=of("guardrail"),
        BALCONY=of("balcony_rail"),NET=of("net"),WINDOW=of("curtain_window");
    static final BlockState POLE=of("utility_pole"),SLEEVE=of("pole_sleeve"),PIPE=of("pipe_post");
    // 2.48.0: the photos' streets drawn by the mod: leaves on cards, sash windows, the houses' materials, furniture
    static final BlockState HEDGE=of("hedge"),CANOPY=of("canopy"),BLOSSOM=of("cherry_blossom");
    static final BlockState STUCCO_WHITE=of("stucco_white"),STUCCO_CREAM=of("stucco_cream"),STUCCO_PINK=of("stucco_pink"),STUCCO_GRAY=of("stucco_gray"),
        SIDING=of("siding"),TILE=of("tile_wall"),TRIM_DARK=of("trim_dark"),TRIM_WHITE=of("trim_white"),CONCRETE=of("concrete_smooth");
    static final BlockState BOARDS_DARK=of("wood_boards_dark"),BOARDS=of("wood_boards"),TIMBER=of("timber_dark");
    static final BlockState WALL_FOOT=of("block_wall_foot"),WALL_DARK=of("block_wall_dark"),SLAT_DARK=of("slat_fence_dark"),SLAT_LIGHT=of("slat_fence_light"),MIRROR_POST=of("mirror_post");
    /** Windows with sash frames, facing north (a frame turns them; Kit.windows faces them out of their wall). */
    static final BlockState WIN=of("window"),WIN_CURTAIN=of("window_curtain"),WIN_FROSTED=of("window_frosted"),WIN_WHITE=of("window_white"),WIN_WHITE_CURTAIN=of("window_white_curtain");
    static boolean isWindow(BlockState s){return s.getBlock() instanceof dev.sangwoo.celestialfall.town.TownBlocks.Window;}
    /** A window (or anything with a facing) facing out the given way. */
    static BlockState out(BlockState s,Direction d){return s.contains(Properties.HORIZONTAL_FACING)?s.with(Properties.HORIZONTAL_FACING,d):s;}
    /** Ivy on the wall behind it, its leaves facing out the given way. */
    static BlockState ivy(Direction out){return of("ivy").with(Properties.HORIZONTAL_FACING,out);}
    static BlockState branch(Direction.Axis a){return of("cherry_branch").with(Properties.AXIS,a);}
    /** A cherry's trunk, or a limb where it leaves the trunk: the round branch, thick. */
    static BlockState trunk(Direction.Axis a){return branch(a).with(dev.sangwoo.celestialfall.town.TownBlocks.Branch.THICK,true);}
    static BlockState darkCap(){return slab("block_wall_dark_slab",false);}
    /** The green walking strip with its white line along the given side. */
    static BlockState green(Direction line){return of("asphalt_green").with(Properties.HORIZONTAL_FACING,line);}
    static BlockState mirror(Direction front){return facing("traffic_mirror",front);}

    static BlockState slab(String name,boolean top){return of(name).with(Properties.SLAB_TYPE,top?SlabType.TOP:SlabType.BOTTOM);}
    static BlockState asphaltSlab(){return slab("asphalt_slab",false);}
    static BlockState pavingSlab(){return slab("paving_slab",false);}
    static BlockState wallCap(){return slab("block_wall_slab",false);}
    /** Road paint: an edge line along the given side of its block (the kerb's side), or along it on a half step. */
    static BlockState edgeLine(Direction side,boolean slab){
        var s=of(slab?"asphalt_edge_line_slab":"asphalt_edge_line").with(Properties.HORIZONTAL_FACING,side);
        return slab?s.with(Properties.SLAB_TYPE,SlabType.BOTTOM):s;
    }
    /** Road paint running along an axis: a centre line, a zebra bar, a stop line, the lot's faded lines. */
    static BlockState painted(String name,Direction.Axis along){return of(name).with(Properties.HORIZONTAL_AXIS,along);}
    /** Kawara or corrugated roofing with its channels running down the slope along the given axis. */
    static BlockState roof(String name,Direction.Axis slope){return of(name).with(Properties.HORIZONTAL_AXIS,slope);}
    static BlockState roofSlab(String name,Direction.Axis slope,boolean top){
        return of(name).with(Properties.HORIZONTAL_AXIS,slope).with(Properties.SLAB_TYPE,top?SlabType.TOP:SlabType.BOTTOM);
    }
    static BlockState crossarm(boolean pole,Direction.Axis along){return of(pole?"pole_crossarm":"pole_arm").with(Properties.HORIZONTAL_AXIS,along);}
    /** A thing with a front, facing the given way, its back to what it hangs on. */
    static BlockState facing(String name,Direction front){return of(name).with(Properties.HORIZONTAL_FACING,front);}
    static BlockState vending(Direction front,boolean upper){return vending(front,upper,false);}
    /** A drinks machine's half: red, or white (2.48.0). */
    static BlockState vending(Direction front,boolean upper,boolean white){
        return of(white?"vending_machine_white":"vending_machine").with(Properties.HORIZONTAL_FACING,front).with(Properties.DOUBLE_BLOCK_HALF,upper?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER);
    }
}
