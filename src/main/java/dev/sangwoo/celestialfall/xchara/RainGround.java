package dev.sangwoo.celestialfall.xchara;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;

/**
 * The ground a knife of X Chara's rain comes down on (2.132.5): the server (whether a knife meets a body before it) and
 * every client (where to draw it land) find it the same way, from the same blocks.
 */
public final class RainGround {
    private RainGround(){}
    /**
     * The ground under a knife whose point comes out of the sky at {@code top} over (x, z): the top of the first block
     * with something to stand on from there down to {@code bottom}; NaN if there is none (over a deeper drop) or if its
     * point would come out inside it (the rain is over a roof or a hill higher than its sky: no knife comes there).
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
