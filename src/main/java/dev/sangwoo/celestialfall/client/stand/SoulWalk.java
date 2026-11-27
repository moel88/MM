package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import net.minecraft.util.math.Vec3d;

/**
 * How a soul out of its body walks (2.148.0, Gold Experience's overdrive: "그 상태에서는 속도가 2배"): the game's own
 * walking for a body on foot (LivingEntity.travel: the keys push it, the ground's friction holds it back, it jumps and
 * falls), at SOUL_SPEED times a body's speed. It is the struck player's own client's alone (SoulClient): nothing of it
 * is on the server. Pure numbers; what is in its way is asked of {@link Ground}.
 */
public final class SoulWalk {
    private SoulWalk(){}
    /** What stands in its way: how much of a move of {@code want} from {@code feet} it can make. */
    public interface Ground {Vec3d move(Vec3d feet,Vec3d want);}
    /** The game's numbers for a body on foot: its speed, its push in the air, the ground's friction and the air's, gravity, drag, a jump, and what running adds. */
    static final double SPEED=.1,AIR=.02,FRICTION=.6*.91,DRAG=.91,GRAVITY=.08,FALL=.98,JUMP=.42,RUN=1.3,RUN_JUMP=.2;
    /** Where it is, how fast it goes (blocks a tick) and whether it stands on something. */
    public record Step(Vec3d feet,Vec3d velocity,boolean ground){}
    /** How fast a soul goes at a steady walk (blocks a tick): SOUL_SPEED times a body's. */
    public static double pace(boolean run){return SPEED*GoldExperienceRules.SOUL_SPEED*(run?RUN:1)*.98/(1-FRICTION);}
    /** The push of the keys ({@code forward} and {@code left}, each -1 to 1) for one facing {@code yaw} degrees, {@code push} strong. */
    static Vec3d push(float forward,float left,float yaw,double push){
        double len=forward*forward+left*left;if(len<1e-7)return Vec3d.ZERO;
        double k=(len>1?1/Math.sqrt(len):1)*push*.98,r=Math.toRadians(yaw),sin=Math.sin(r),cos=Math.cos(r);
        return new Vec3d((left*cos-forward*sin)*k,0,(forward*cos+left*sin)*k);
    }
    /**
     * One tick: the keys push it the way {@code yaw} faces, it jumps if asked and it stands on something, it moves as
     * far as {@code ground} lets it, and friction and gravity take their share for the next.
     */
    public static Step step(Step at,float forward,float left,boolean jump,boolean run,float yaw,Ground ground){
        double fast=GoldExperienceRules.SOUL_SPEED*(run?RUN:1);
        Vec3d v=at.velocity.add(push(forward,left,yaw,(at.ground?SPEED:AIR)*fast));
        // standing, gravity presses it on what it stands on (so it knows it still stands there, and steps down a slope's blocks)
        if(at.ground&&v.y>-GRAVITY*FALL)v=new Vec3d(v.x,-GRAVITY*FALL,v.z);
        if(jump&&at.ground){
            v=new Vec3d(v.x,JUMP,v.z);
            if(run&&forward>0){double r=Math.toRadians(yaw);v=v.add(-Math.sin(r)*RUN_JUMP,0,Math.cos(r)*RUN_JUMP);}
        }
        Vec3d moved=ground.move(at.feet,v);
        boolean landed=v.y<0&&moved.y>v.y+1e-9;
        double vx=Math.abs(moved.x-v.x)>1e-9?0:v.x,vz=Math.abs(moved.z-v.z)>1e-9?0:v.z,vy=Math.abs(moved.y-v.y)>1e-9?0:v.y;
        double f=at.ground?FRICTION:DRAG;
        return new Step(at.feet.add(moved),new Vec3d(vx*f,(vy-GRAVITY)*FALL,vz*f),landed);
    }
}
