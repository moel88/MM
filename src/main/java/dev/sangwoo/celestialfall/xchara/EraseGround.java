package dev.sangwoo.celestialfall.xchara;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;

/**
 * The ground under X Chara's delete slash (2.133.0; first the rain of knives', 2.132.5): where the clients draw the
 * blocks it comes over break into pixels, found from the blocks as they are.
 */
public final class EraseGround {
    private EraseGround(){}
    /**
     * The ground at (x, z) looked for from {@code top} down: the top of the first block with something to stand on from
     * there down to {@code bottom}; NaN if there is none (over a deeper drop) or if it is over {@code top} (inside a wall
     * or a hill higher than that).
     */
    public static double at(BlockView world,double x,double z,double top,double bottom){
        int hi=MathHelper.floor(top),lo=MathHelper.floor(bottom);
        var pos=new BlockPos.Mutable(MathHelper.floor(x),hi,MathHelper.floor(z));
        for(int by=hi;by>=lo;by--){
            pos.setY(by);
            var shape=world.getBlockState(pos).getCollisionShape(world,pos);
            if(shape.isEmpty())continue;
            double t=by+shape.getMax(Direction.Axis.Y);
            return t>top?Double.NaN:t;
        }
        return Double.NaN;
    }
}
