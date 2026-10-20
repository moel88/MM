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
    /** Patchy value noise: repaired squares of asphalt, mossy corners of walls. */
    static double blot(int x,int z,double scale){
        int gx=Math.floorDiv(x,(int)scale),gz=Math.floorDiv(z,(int)scale);
        return hash(gx,7,gz);
    }
    /** Smooth value noise (0..1): the hash at grid points of the given spacing, blended between them. */
    static double smooth(int x,int z,double scale,int seed){
        double fx=x/scale,fz=z/scale;int gx=(int)Math.floor(fx),gz=(int)Math.floor(fz);
        double tx=fx-gx,tz=fz-gz;tx=tx*tx*(3-2*tx);tz=tz*tz*(3-2*tz);
        double a=hash(gx,seed,gz),b=hash(gx+1,seed,gz),c=hash(gx,seed,gz+1),d=hash(gx+1,seed,gz+1);
        return (a*(1-tx)+b*tx)*(1-tz)+(c*(1-tx)+d*tx)*tz;
    }
    /** Worn asphalt: dark grey, with lighter repaired patches and the odd rough spot. */
    static BlockState asphalt(int x,int z){
        double h=hash(x,1,z);
        if(blot(x,z,6)>0.88)return Blocks.GRAY_CONCRETE.getDefaultState();
        if(h<0.03)return Blocks.COBBLED_DEEPSLATE.getDefaultState();
        if(h<0.10)return Blocks.GRAY_CONCRETE.getDefaultState();
        return Blocks.GRAY_CONCRETE_POWDER.getDefaultState();
    }
    static BlockState asphaltSlab(int x,int z){
        return hash(x,1,z)<0.06?Canvas.slab(Blocks.COBBLED_DEEPSLATE_SLAB,false):Canvas.slab(Blocks.POLISHED_DEEPSLATE_SLAB,false);
    }
    static BlockState asphaltBase(){return Blocks.POLISHED_DEEPSLATE.getDefaultState();}
    static BlockState ramp(boolean slab){return slab?Canvas.slab(Blocks.POLISHED_DEEPSLATE_SLAB,false):Blocks.POLISHED_DEEPSLATE.getDefaultState();}
    /** Concrete paving of alleys, yards and the quay. */
    static BlockState concrete(int x,int z){
        double h=hash(x,2,z);
        if(h<0.06)return Blocks.ANDESITE.getDefaultState();
        if(h<0.12)return Blocks.STONE.getDefaultState();
        return Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
    }
    static BlockState gutter(int x,int z){return hash(x,3,z)<0.12?Blocks.STONE.getDefaultState():Blocks.SMOOTH_STONE.getDefaultState();}
    /** Sidewalk pavers in a two-tone pattern. */
    static BlockState pavers(int x,int z){
        return ((x>>1)+(z>>1)&1)==0?Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false):Canvas.slab(Blocks.POLISHED_ANDESITE_SLAB,false);
    }
    static BlockState paverBlock(int x,int z){
        return ((x>>1)+(z>>1)&1)==0?Blocks.SMOOTH_STONE.getDefaultState():Blocks.POLISHED_ANDESITE.getDefaultState();
    }
    /** The seaside promenade: red-brown and sand-coloured pavers. */
    static BlockState promenade(int x,int z){
        double h=hash(x,4,z);
        return h<0.55?Canvas.slab(Blocks.POLISHED_GRANITE_SLAB,false):h<0.85?Canvas.slab(Blocks.SMOOTH_SANDSTONE_SLAB,false):Canvas.slab(Blocks.GRANITE_SLAB,false);
    }
    static BlockState promenadeBlock(int x,int z){
        double h=hash(x,4,z);
        return h<0.55?Blocks.POLISHED_GRANITE.getDefaultState():h<0.85?Blocks.SMOOTH_SANDSTONE.getDefaultState():Blocks.GRANITE.getDefaultState();
    }
    /** Old block walls (ブロック塀): grey blocks, some darker or mossy with age. */
    static BlockState blockWall(int x,int y,int z){
        double h=hash(x,y,z);
        if(h<0.10)return Blocks.ANDESITE.getDefaultState();
        if(h<0.16)return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if(h<0.22)return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        return Blocks.STONE_BRICKS.getDefaultState();
    }
    /** Weathered concrete of sea walls and quays. */
    static BlockState seawall(int x,int y,int z){
        double h=hash(x,y,z);
        if(h<0.12)return Blocks.ANDESITE.getDefaultState();
        if(h<0.20)return Blocks.STONE.getDefaultState();
        if(h<0.26&&y<=Plan.SEA+1)return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        return Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
    }
    static Block pick(Random r,Block... options){return options[r.nextInt(options.length)];}
    static <T> T pick(Random r,T[] options){return options[r.nextInt(options.length)];}
}
