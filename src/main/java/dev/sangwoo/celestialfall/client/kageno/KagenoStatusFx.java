package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.List;

/** Visuals driven by state rather than by a cast: F guard bubble, Q overdrive aura, shadow marks. */
public final class KagenoStatusFx {
    private KagenoStatusFx(){}
    public static Fx context(MinecraftClient mc,StandClient.Cast cast,float delta,Vec3d pos,Vec3d camera,Matrix4f matrix,MatrixStack matrices,List<LivingEntity> targets){
        return new Fx(mc,cast,delta,pos,camera,matrix,matrices,targets);
    }
    public static void alpha(MinecraftClient mc,EffectMesh m,float delta,Vec3d camera){
        float time=(mc.world.getTime()%100000)+delta;
        for(var p:mc.world.getPlayers()){
            if(!StandClient.visible(p))continue;
            float in=KagenoClips.guardSec(p.getUuid(),delta),out=KagenoClips.releaseSec(p.getUuid(),delta);
            // Full size and opacity from the first guard frame; a short fade when it drops.
            float alpha=!Float.isNaN(in)?1:!Float.isNaN(out)?1-Ease.smooth(out,0,.15f):0;
            GuardFx.bubble(m,p.getLerpedPos(delta),camera,alpha,time);
        }
    }
    public static void additive(MinecraftClient mc,EffectMesh m,float delta,Vec3d camera){
        long now=mc.world.getTime();
        for(var entry:StandClient.STATES.entrySet()){
            if(entry.getValue().until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
            if(p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            OverdriveFx.aura(m,mc,p,delta,now+delta);
        }
    }
    public static void marks(MinecraftClient mc,EffectMesh m,float delta,Vec3d camera){
        if(mc.player==null)return;var local=StandClient.STATES.get(mc.player.getUuid());if(local==null)return;long now=mc.world.getTime();
        for(var mark:local.marks())if(mark.until()>now){var e=mc.world.getEntityById(mark.entity());if(e!=null&&e.getUuid().equals(mark.id())&&e.isAlive())OverdriveFx.mark(m,e.getLerpedPos(delta).add(0,e.getHeight()+.35,0),now+delta);}
    }
}
