package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.GoldExperience;
import dev.sangwoo.celestialfall.stand.GoldExperienceEntity;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import net.minecraft.world.World;

/**
 * The body a client draws for a player's Gold Experience while it is out (2.146.0), as {@link StandPuppet} is Star
 * Platinum's: never added to the world, so nothing collides with it, aims at it or saves it; GoldPresence places it
 * beside its user each frame and gives it its look.
 */
final class GoldPuppet extends GoldExperienceEntity {
    /** Ids no server entity has (GeckoLib keeps each body's animation by its id), apart from Star Platinum's puppets'. */
    private static int next=-6_000_000;
    final GoldExperienceLook look=new GoldExperienceLook();
    GoldPuppet(World world){super(GoldExperience.TYPE,world);setId(next--);}
    @Override public GoldExperienceLook look(float delta){return look;}
    @Override public boolean canHit(){return false;}
    /** Puts it where it is drawn this frame, facing {@code yaw} (no motion between ticks: it is placed every frame). */
    void place(net.minecraft.util.math.Vec3d where,float yaw){
        setPosition(where.x,where.y,where.z);prevX=lastRenderX=where.x;prevY=lastRenderY=where.y;prevZ=lastRenderZ=where.z;
        setYaw(yaw);prevYaw=yaw;setBodyYaw(yaw);prevBodyYaw=yaw;setHeadYaw(yaw);prevHeadYaw=yaw;setPitch(0);prevPitch=0;
    }
}
