package dev.sangwoo.celestialfall.engine;

import java.util.OptionalDouble;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The shape a move strikes with (2.157.0, the combat engine). Each is given in its attacker's frame (their feet, the way
 * they face) and tested against a target's box, the box being where the target stood as the attacker saw it (Rewind).
 * <ul>
 * <li>{@link Ray}: a line from the eye along the look, {@code reach} long, meeting boxes grown by {@code pad} (bare hands:
 * the first box it meets, a wall stops it).</li>
 * <li>{@link Lane}: a box ahead of the feet, {@code fwdMin} to {@code fwdMax} forward, {@code half} to either side,
 * {@code low} to {@code high} up (the stands' punches' lanes).</li>
 * <li>{@link Sphere}: a ball {@code forward} ahead of and {@code up} above the feet.</li>
 * </ul>
 * A shape that moves within a tick (its attacker dashing) is tested at several points of the tick ({@link #steps}), so
 * a fast one does not pass a target between two ticks.
 */
public sealed interface Hitbox permits Hitbox.Ray, Hitbox.Lane, Hitbox.Sphere {
    record Ray(double reach,double pad) implements Hitbox {}
    record Lane(double fwdMin,double fwdMax,double half,double low,double high) implements Hitbox {}
    record Sphere(double forward,double up,double radius) implements Hitbox {}

    /** Where a ray from {@code eye} to {@code end} first meets {@code box} grown by its pad: the distance from the eye, or none. */
    static OptionalDouble along(Ray r,Vec3d eye,Vec3d end,Box box){
        var hit=box.expand(r.pad()).raycast(eye,end);
        return hit.isPresent()?OptionalDouble.of(eye.distanceTo(hit.get())):OptionalDouble.empty();
    }
    /** The forward and side axes of an attacker facing {@code yaw} (Minecraft's degrees). */
    static Vec3d forward(float yaw){double r=Math.toRadians(yaw);return new Vec3d(-Math.sin(r),0,Math.cos(r));}
    static Vec3d side(float yaw){double r=Math.toRadians(yaw);return new Vec3d(Math.cos(r),0,Math.sin(r));}
    /**
     * Whether a lane of an attacker standing at {@code feet} facing {@code yaw} meets {@code box}: the box's middle ahead
     * of and beside the feet within the lane, its own half width added to either side and to its reach, and some of its
     * height within the lane's.
     */
    static boolean meets(Lane l,Vec3d feet,float yaw,Box box){
        Vec3d c=box.getCenter().subtract(feet);double half=Math.max(box.getXLength(),box.getZLength())*.5;
        double f=c.dotProduct(forward(yaw)),s=c.dotProduct(side(yaw));
        double low=box.minY-feet.y,high=box.maxY-feet.y;
        return f>=l.fwdMin()-half&&f<=l.fwdMax()+half&&Math.abs(s)<=l.half()+half&&high>=l.low()&&low<=l.high();
    }
    /** Whether a ball of an attacker standing at {@code feet} facing {@code yaw} meets {@code box} (its nearest point within the ball). */
    static boolean meets(Sphere s,Vec3d feet,float yaw,Box box){
        Vec3d c=feet.add(forward(yaw).multiply(s.forward())).add(0,s.up(),0);
        double x=Math.max(box.minX,Math.min(c.x,box.maxX)),y=Math.max(box.minY,Math.min(c.y,box.maxY)),z=Math.max(box.minZ,Math.min(c.z,box.maxZ));
        return c.squaredDistanceTo(x,y,z)<=s.radius()*s.radius();
    }
    /**
     * The points of a tick (0 its start, 1 its end) a shape moving {@code moved} blocks within it is tested at: often
     * enough that it never moves more than {@code most} between two (at least the end; at most 8).
     */
    static double[] steps(double moved,double most){
        int n=(int)Math.max(1,Math.min(8,Math.ceil(moved/Math.max(1e-3,most))));
        double[] t=new double[n];for(int i=0;i<n;i++)t[i]=(i+1)/(double)n;
        return t;
    }
}
