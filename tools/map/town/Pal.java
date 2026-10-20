package dev.sangwoo.botandtown;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import java.util.Random;

/** Block palettes, chosen to read well in plain Minecraft and better still under shaders. */
final class Pal {
    private Pal(){}
    /** A cheap stable hash of a position, 0..1. */
    static double hash(int x,int y,int z){
        long h=x*341873128712L+y*132897987541L+z*6971L;h^=h>>>13;h*=0x5bd1e995L;h^=h>>>15;
        return (h&0xffffff)/(double)0x1000000;
    }
    static double blot(int x,int z,double scale){
        // patchy value noise: repaired squares of asphalt, mossy corners of walls
        int gx=Math.floorDiv(x,(int)scale),gz=Math.floorDiv(z,(int)scale);
        return hash(gx,7,gz);
    }
    /** Worn asphalt: grey with darker repaired patches and the odd crack. */
    static BlockState asphalt(int x,int z){
        double h=hash(x,1,z);
        if(blot(x,z,7)>0.86)return h<0.7?Blocks.POLISHED_DEEPSLATE.getDefaultState():Blocks.DEEPSLATE_TILES.getDefaultState();
        if(h<0.035)return Blocks.COBBLED_DEEPSLATE.getDefaultState();
        if(h<0.09)return Blocks.TUFF.getDefaultState();
        if(h<0.16)return Blocks.GRAY_CONCRETE_POWDER.getDefaultState();
        return Blocks.GRAY_CONCRETE.getDefaultState();
    }
    static BlockState ramp(boolean slab){return slab?Canvas.slab(Blocks.POLISHED_DEEPSLATE_SLAB,false):Blocks.POLISHED_DEEPSLATE.getDefaultState();}
    /** Concrete paving of alleys, yards and the quay. */
    static BlockState concrete(int x,int z){
        double h=hash(x,2,z);
        if(h<0.06)return Blocks.ANDESITE.getDefaultState();
        if(h<0.12)return Blocks.STONE.getDefaultState();
        return Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
    }
    static BlockState gutter(int x,int z){return hash(x,3,z)<0.12?Blocks.STONE.getDefaultState():Blocks.SMOOTH_STONE.getDefaultState();}
    /** Sidewalk pavers, laid as half slabs so the kerb stands half a block above the road. */
    static BlockState pavers(int x,int z){
        return ((x>>1)+(z>>1)&1)==0?Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false):Canvas.slab(Blocks.POLISHED_ANDESITE_SLAB,false);
    }
    /** The seaside promenade: red-brown and sand-coloured pavers. */
    static BlockState promenade(int x,int z){
        double h=hash(x,4,z);
        return h<0.55?Canvas.slab(Blocks.POLISHED_GRANITE_SLAB,false):h<0.85?Canvas.slab(Blocks.SMOOTH_SANDSTONE_SLAB,false):Canvas.slab(Blocks.GRANITE_SLAB,false);
    }
    /** Old block walls (ブロック塀): grey blocks, some darker or mossy with age. */
    static BlockState blockWall(int x,int y,int z){
        double h=hash(x,y,z);
        if(h<0.10)return Blocks.ANDESITE.getDefaultState();
        if(h<0.16)return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if(h<0.22)return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        return Blocks.STONE_BRICKS.getDefaultState();
    }
    static Block pick(Random r,Block... options){return options[r.nextInt(options.length)];}
}
