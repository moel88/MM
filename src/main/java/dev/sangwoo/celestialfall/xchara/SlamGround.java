package dev.sangwoo.celestialfall.xchara;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Where X Chara's slam (SlamRules) can go in a world: how high its user rises where they stand, and the floor it comes
 * down on for a point aimed at. The server settles both (XCharaAttack); the user's own client finds the same floor
 * the same way, to mark where it would come down before the dive (client/xchara/OverwriteSlam).
 */
public final class SlamGround {
    private SlamGround(){}
    /** How high {@code who} rises from where they stand: SlamRules.RISE, less where something is over their head. */
    public static double headroom(World world,Entity who){
        Vec3d head=who.getPos().add(0,who.getHeight(),0);
        var hit=world.raycast(new RaycastContext(head,head.add(0,SlamRules.RISE+.5,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,who));
        return hit.getType()==HitResult.Type.MISS?SlamRules.RISE:Math.max(0,Math.min(SlamRules.RISE,hit.getPos().y-head.y-.3));
    }
    /** The first floor at (x, z) from {@code high} down to {@code low} that {@code who} can stand on (its top's height), or NaN. */
    public static double floor(World world,Entity who,double x,double z,double high,double low){
        var pos=new BlockPos.Mutable(MathHelper.floor(x),MathHelper.floor(high),MathHelper.floor(z));
        for(int y=pos.getY(),end=MathHelper.floor(low);y>=end;y--){
            pos.setY(y);
            var shape=world.getBlockState(pos).getCollisionShape(world,pos);if(shape.isEmpty())continue;
            double top=y+shape.getMax(Direction.Axis.Y);
            if(top>high+.01)continue;
            var box=who.getDimensions(EntityPose.STANDING).getBoxAt(x,top+.001,z).contract(1e-4,0,1e-4);
            if(world.isSpaceEmpty(who,box))return top;
        }
        return Double.NaN;
    }
    /**
     * Where a slam risen {@code rise} blocks over {@code from} comes down for the point {@code want} aimed at: that
     * point brought within the circle, on the first floor down from it (from no higher than they rose) that
     * {@code who} can stand on; none there, where they stood.
     */
    public static Vec3d landing(World world,Entity who,Vec3d from,double rise,Vec3d want){
        double top=from.y+rise;
        double[] xz=SlamRules.within(from.x,from.z,want.x,want.z,.5);
        double wantY=Double.isFinite(want.y)?want.y:from.y;
        double y=floor(world,who,xz[0],xz[1],Math.min(Math.max(wantY,from.y-SlamRules.FLOOR_UNDER)+1.5,top+.5),from.y-SlamRules.FLOOR_UNDER);
        return Double.isNaN(y)?from:new Vec3d(xz[0],y,xz[1]);
    }
    /**
     * The point a look from {@code eye} along {@code look} (unit) aims a slam begun at {@code from} at: where it meets
     * the world (a step out from a wall it meets, so the floor at its foot is found), or, meeting none (the sky), as
     * far out the way it goes, level, as the circle reaches.
     */
    public static Vec3d aimed(World world,Entity who,Vec3d from,Vec3d eye,Vec3d look){
        var hit=world.raycast(new RaycastContext(eye,eye.add(look.multiply(96)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,who));
        if(hit.getType()!=HitResult.Type.MISS){var n=hit.getSide();return hit.getPos().add(n.getOffsetX()*.35,0,n.getOffsetZ()*.35);}
        Vec3d flat=new Vec3d(look.x,0,look.z);
        return flat.lengthSquared()<1e-6?from:from.add(flat.normalize().multiply(SlamRules.RADIUS));
    }
}
