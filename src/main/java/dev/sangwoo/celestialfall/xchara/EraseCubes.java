package dev.sangwoo.celestialfall.xchara;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/**
 * The cubes of ground X Chara's terrain delete lifts (2.133.0, EraseRules), as something to stand on: no block (the map is
 * not changed), but every body moving near one, on the server and on each client, meets it as it meets a block (their
 * moves are kept out of it and they stand on its top: EraseCubeCollisionMixin), and one standing on it is carried up as
 * it rises ({@link #carry}: EraseCarryMixin; the server carries everything but players, each client its own player).
 * The server knows its cubes from its calls (XCharaErase), each client from what it was told (EraseClient), each on
 * its own clock: both of them ticks after the grounds were marked, a whole number for the tick being run.
 */
public final class EraseCubes {
    private EraseCubes(){}
    /** One cube as one side knows it now: its ground's corner, its ground's top, and ticks since it was marked (this tick). */
    public record Cube(int x0,int z0,double top,long t){
        /** Its top {@code tt} ticks after it was marked (NaN once it has burst). */
        public double topAt(double tt){double l=EraseRules.lift(tt);return Double.isNaN(l)?Double.NaN:top+l;}
        /** Its top now. */
        public double top(){return topAt(t);}
        /** Its shape now, or null once it has burst. */
        public Box box(){double y=top();return Double.isNaN(y)?null:new Box(x0,y-EraseRules.SIDE,z0,x0+EraseRules.SIDE,y,z0+EraseRules.SIDE);}
        /** Whether a box is over its ground. */
        public boolean over(Box b){return EraseRules.over(x0,z0,b.minX,b.maxX,b.minZ,b.maxZ);}
    }
    /** How one side tells its cubes: whether it has any at all (cheap), and those in a world now. */
    public interface Side {boolean any();void cubes(World world,List<Cube> out);}
    private static Side client;
    /** The client's (EraseClient, as it starts). */
    public static void client(Side side){client=side;}
    private static Side side(World world){return world.isClient?client:XCharaErase.SIDE;}
    /** Whether there is any cube on {@code world}'s side at all (the bodies moving everywhere ask this first). */
    public static boolean any(World world){var s=side(world);return s!=null&&s.any();}
    /** The cubes in {@code world} now. */
    public static List<Cube> cubes(World world){
        var out=new ArrayList<Cube>();var s=side(world);
        if(s!=null&&s.any())s.cubes(world,out);
        return out;
    }
    /** {@code list} with the shapes of the cubes near {@code near} added (a new list if any are: the one given may be fixed). */
    public static List<VoxelShape> with(World world,Box near,List<VoxelShape> list){
        if(!any(world))return list;
        List<VoxelShape> out=null;
        for(var c:cubes(world)){
            var b=c.box();if(b==null||!b.intersects(near))continue;
            if(out==null)out=new ArrayList<>(list);
            out.add(VoxelShapes.cuboid(b));
        }
        return out==null?list:out;
    }
    /**
     * How far up a cube rising under {@code e} lifts it this tick (blocks; 0: none): one over a cube's ground whose feet
     * are on its top as it was a tick ago (or between that and its top now: it rises into their feet) goes up to its top
     * now. The server carries everything but players (their clients move them), each client its own player.
     */
    public static double carry(LivingEntity e){
        var world=e.getWorld();if(!any(world))return 0;
        if(world.isClient){if(!(e instanceof PlayerEntity p)||!p.isMainPlayer())return 0;}
        else if(e instanceof PlayerEntity)return 0;
        var box=e.getBoundingBox();double feet=e.getY(),up=0;
        for(var c:cubes(world)){
            if(!c.over(box))continue;
            double prev=c.topAt(c.t()-1),now=c.top();
            if(Double.isNaN(prev)||Double.isNaN(now)||!(now>prev))continue;
            if(feet>=prev-EraseRules.STANDING&&feet<now)up=Math.max(up,now-feet);
        }
        return up;
    }
    /** The top of the cube {@code e} is on now (over its ground, its feet on it or a jump up from it), or NaN. */
    public static double on(LivingEntity e){
        var world=e.getWorld();if(!any(world))return Double.NaN;
        var box=e.getBoundingBox();double feet=e.getY();
        for(var c:cubes(world)){
            if(!c.over(box))continue;
            double top=c.top();
            if(!Double.isNaN(top)&&EraseRules.on(feet,top))return top;
        }
        return Double.NaN;
    }
}
