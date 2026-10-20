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
    static BlockState vending(Direction front,boolean upper){
        return of("vending_machine").with(Properties.HORIZONTAL_FACING,front).with(Properties.DOUBLE_BLOCK_HALF,upper?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER);
    }
}
