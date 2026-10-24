package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.stand.StarPlatinum;
import dev.sangwoo.celestialfall.stand.StarPlatinumEntity;
import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import net.minecraft.world.World;

/**
 * The body a client draws for a player's Star Platinum while it is out (2.51.0): never added to the world, so nothing
 * collides with it, aims at it or saves it; StandPresence places it beside its user each frame and gives it its look.
 */
final class StandPuppet extends StarPlatinumEntity {
    /** Ids no server entity has (GeckoLib keeps each body's animation by its id). */
    private static int next=-4_000_000;
    final StarPlatinumLook look=new StarPlatinumLook();
    StandPuppet(World world){super(StarPlatinum.TYPE,world);setId(next--);}
    @Override public StarPlatinumLook look(float delta){return look;}
    @Override public boolean canHit(){return false;}
    /** Puts it where it is drawn this frame, facing {@code yaw} (no motion between ticks: it is placed every frame). */
    void place(net.minecraft.util.math.Vec3d where,float yaw){
        // setPosition moves its box too (the frustum test and the light go by it)
        setPosition(where.x,where.y,where.z);prevX=lastRenderX=where.x;prevY=lastRenderY=where.y;prevZ=lastRenderZ=where.z;
        setYaw(yaw);prevYaw=yaw;setBodyYaw(yaw);prevBodyYaw=yaw;setHeadYaw(yaw);prevHeadYaw=yaw;setPitch(0);prevPitch=0;
    }
}
