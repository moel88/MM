package dev.sangwoo.celestialfall.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * A boss's way to where it goes (2.69.0): straight there when nothing stands in the way, else along a path found the
 * way a mob finds its own (a stand-in mob that is never in the world plans it with the game's own pathfinding), a step
 * at a time.
 */
final class BossPath {
    private ZombieEntity planner;
    private Path path;private BlockPos goal;private long planned=-1000;
    /** The point to head for now on the way from {@code boss} to {@code to}. */
    Vec3d next(BossPlayer boss,Vec3d to,long now){
        var world=boss.getServerWorld();
        if(clear(world,boss,to)){path=null;return to;}
        var at=BlockPos.ofFloored(to);
        if(path==null||goal==null||goal.getSquaredDistance(at)>4||now-planned>20||path.isFinished()){
            if(planner==null||planner.getWorld()!=world)planner=new ZombieEntity(EntityType.ZOMBIE,world);
            planner.refreshPositionAndAngles(boss.getX(),boss.getY(),boss.getZ(),0,0);planner.setOnGround(true);
            path=planner.getNavigation().findPathTo(at,1);goal=at;planned=now;
            if(path==null)return to;
        }
        // the next node not yet reached
        while(!path.isFinished()){
            var node=path.getNode(path.getCurrentNodeIndex());
            double dx=node.x+.5-boss.getX(),dz=node.z+.5-boss.getZ();
            if(dx*dx+dz*dz>.45*.45)return new Vec3d(node.x+.5,node.y,node.z+.5);
            path.next();
        }
        return to;
    }
    void clear(){path=null;goal=null;}
    /** Whether nothing stands between the boss and {@code to} at its knees and at its eyes, and it is about level. */
    static boolean clear(ServerWorld world,BossPlayer boss,Vec3d to){
        if(Math.abs(to.y-boss.getY())>1.2)return false;
        for(double h:new double[]{.6,1.6}){
            var from=boss.getPos().add(0,h,0);var end=to.add(0,h,0);
            if(world.raycast(new RaycastContext(from,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,boss)).getType()!=HitResult.Type.MISS)return false;
        }
        return true;
    }
}
