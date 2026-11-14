package dev.sangwoo.celestialfall.xchara;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The grounds X Chara's terrain delete marks (2.133.0, EraseRules), found from the blocks as they are by the server
 * (XCharaErase): where the middle of its user's screen points, under the ones near them, and at random places.
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
    /**
     * The point a look from {@code eye} along {@code look} (unit) of one standing at {@code from} aims at: where it meets
     * the world (a step out from a wall it meets, so the ground at its foot is found), or, meeting none (the sky), as far
     * out the way it goes, level, as it may reach (EraseRules.REACH).
     */
    public static Vec3d aimed(World world,Entity who,Vec3d from,Vec3d eye,Vec3d look){
        var hit=world.raycast(new RaycastContext(eye,eye.add(look.multiply(96)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,who));
        if(hit.getType()!=HitResult.Type.MISS){var n=hit.getSide();return hit.getPos().add(n.getOffsetX()*.35,0,n.getOffsetZ()*.35);}
        Vec3d flat=new Vec3d(look.x,0,look.z);
        return flat.lengthSquared()<1e-6?from:from.add(flat.normalize().multiply(EraseRules.REACH));
    }
    /**
     * The ground marked for the point {@code want} aimed at by one standing at {@code from}: that point brought within
     * EraseRules.REACH of them (level), on the ground found there from a little over the point (no higher than
     * EraseRules.FLOOR_OVER over their feet) down to EraseRules.FLOOR_UNDER under them; none there, at the height of
     * their feet.
     */
    public static Vec3d landing(World world,Vec3d from,Vec3d want){
        double[] xz=EraseRules.within(from.x,from.z,want.x,want.z);
        double wantY=Double.isFinite(want.y)?want.y:from.y,low=from.y-EraseRules.FLOOR_UNDER;
        double high=Math.min(Math.max(wantY,low)+1.5,from.y+EraseRules.FLOOR_OVER+1.5);
        double y=at(world,xz[0],xz[1],high,low);
        return new Vec3d(xz[0],Double.isNaN(y)?from.y:y,xz[1]);
    }
    /** The ground under one whose feet are at {@code feet} at (x, z): the top of the first block under them, no deeper than EraseRules.UNDER_THEM; NaN if none. */
    public static double under(BlockView world,double x,double z,double feet){return at(world,x,z,feet+.5,feet-EraseRules.UNDER_THEM);}
    /** The ground at a random place (x, z) near one whose feet are at {@code feet}: no higher than EraseRules.RANDOM_OVER over them, no deeper than EraseRules.RANDOM_UNDER under them; NaN if none. */
    public static double near(BlockView world,double x,double z,double feet){return at(world,x,z,feet+EraseRules.RANDOM_OVER,feet-EraseRules.RANDOM_UNDER);}
}
