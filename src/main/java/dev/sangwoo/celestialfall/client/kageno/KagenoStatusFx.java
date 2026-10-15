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

/** Visuals driven by state rather than by a cast: F guard bubble and the Q overdrive aura. */
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
        long now=mc.world.getTime();
        for(var entry:StandClient.STATES.entrySet()){
            if(entry.getValue().until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
            if(p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            OverdriveFx.circuits(m,mc,p,delta,now+delta);
        }
    }
    /** Full-screen brightening while an overdrive switches on nearby. */
    public static float flash(MinecraftClient mc,float delta,Vec3d camera){
        long now=mc.world.getTime();float best=0;
        for(var entry:StandClient.STATES.entrySet()){
            if(entry.getValue().until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
            best=Math.max(best,OverdriveFx.flash(mc,p,delta,camera));
        }
        return best;
    }
    public static void additive(MinecraftClient mc,EffectMesh m,float delta,Vec3d camera){
        long now=mc.world.getTime();
        for(var entry:StandClient.STATES.entrySet()){
            if(entry.getValue().until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
            if(p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            OverdriveFx.aura(m,mc,p,delta,now+delta);
        }
    }
}
