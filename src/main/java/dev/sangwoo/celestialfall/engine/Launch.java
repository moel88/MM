package dev.sangwoo.celestialfall.engine;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Throws as the combat engine makes them (2.157.0): the velocity is set once and told once. A struck player moves
 * themselves (their client), so the server tells their client of a throw: through the tracker ({@code velocityModified},
 * at the end of the tick, to them and to everyone who sees them). Several throws sent it a second time at once besides,
 * and a client that moved between the two packets was thrown twice (2.56.0's check: 11 or 20 blocks for one throw;
 * CLAUDE.md's known problem of Star Platinum's finisher). One stopped in Star Platinum's stopped time is thrown as time
 * moves again, with whatever else came to them.
 */
public final class Launch {
    private Launch(){}
    /** Throws {@code target} with {@code velocity} (blocks a tick). */
    public static void at(LivingEntity target,Vec3d velocity){
        if(dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop.deferLaunch(target,velocity))return;
        target.setVelocity(velocity);target.velocityModified=true;
    }
    /**
     * Throws {@code target} as Minecraft's knockback does ({@code LivingEntity.takeKnockback}: half its speed kept and
     * {@code strength} away along {@code x}, {@code z} taken off, up a little if it stands on the ground); stopped in
     * time, it comes as time moves again (the knockback's own deferral).
     */
    public static void knock(LivingEntity target,double strength,double x,double z){
        target.takeKnockback(strength,x,z);target.velocityModified=true;
    }
}
