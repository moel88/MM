package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Q overdrive: body aura while active, and the owner-only shadow mark over marked targets. */
final class OverdriveFx {
    private OverdriveFx(){}
    static void aura(EffectMesh m,MinecraftClient mc,PlayerEntity p,float delta,float now){}
    static void mark(EffectMesh m,Vec3d p,float now){
        Vec3d cam=m.camera(),n=cam.subtract(p).normalize(),u=n.crossProduct(Fx.UP).normalize();if(u.lengthSquared()<.1)u=new Vec3d(1,0,0);Vec3d v=u.crossProduct(n).normalize();
        m.needle(p.subtract(u.multiply(.17)),p.add(u.multiply(.17)),.05,.5,0xffffff,.85f);
        m.needle(p.subtract(v.multiply(.17)),p.add(v.multiply(.17)),.05,.5,0xffffff,.85f);
    }
}
