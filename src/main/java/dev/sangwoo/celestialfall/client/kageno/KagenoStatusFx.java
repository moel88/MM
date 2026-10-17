package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
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
            OverdriveFx.circuits(m,mc,p,delta,now+delta,false);
            OverdriveFx.flames(m,mc,p,delta,now+delta,false);
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
    /** Additive light; {@code layered} on the effect layer, where the guard bubble and the overdrive's flames and circuits glow as well. */
    public static void additive(MinecraftClient mc,EffectMesh m,float delta,Vec3d camera,boolean layered){
        long now=mc.world.getTime();float time=(now%100000)+delta;
        for(var p:mc.world.getPlayers()){
            if(!StandClient.visible(p)||p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            float in=KagenoClips.guardSec(p.getUuid(),delta),out=KagenoClips.releaseSec(p.getUuid(),delta);
            GuardFx.glints(m,p.getLerpedPos(delta),in,out,time);
            if(layered)GuardFx.light(m,p.getLerpedPos(delta),camera,in,out,time);
        }
        for(var entry:StandClient.STATES.entrySet()){
            if(entry.getValue().until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
            if(p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            OverdriveFx.aura(m,mc,p,delta,now+delta);
            if(layered){OverdriveFx.circuits(m,mc,p,delta,now+delta,true);OverdriveFx.flames(m,mc,p,delta,now+delta,true);}
        }
    }
    /** A guard bubble or an overdrive shows near the camera (they are drawn on the effect layer then). */
    public static boolean showing(MinecraftClient mc,float delta,Vec3d camera){
        if(mc.world==null)return false;
        for(var p:mc.world.getPlayers()){
            if(!StandClient.visible(p)||p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            if(!Float.isNaN(KagenoClips.guardSec(p.getUuid(),delta)))return true;
            float out=KagenoClips.releaseSec(p.getUuid(),delta);if(!Float.isNaN(out)&&out<.4f)return true;
        }
        long now=mc.world.getTime();
        for(var entry:StandClient.STATES.entrySet()){
            if(entry.getValue().until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());
            if(p!=null&&StandClient.visible(p)&&p.getLerpedPos(delta).squaredDistanceTo(camera)<=64*64)return true;
        }
        return false;
    }
    /** Lights on the effect layer: the guard bubble's soft white-violet glow (a flare as it goes up, a flash as it bursts), the overdrive's activation and aura. */
    public static void frame(MinecraftClient mc,FxFrame f,float delta,Vec3d camera){
        for(var p:mc.world.getPlayers()){
            if(!StandClient.visible(p)||p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            float in=KagenoClips.guardSec(p.getUuid(),delta),out=KagenoClips.releaseSec(p.getUuid(),delta);
            // The bubble bursting as it drops.
            if(!Float.isNaN(out)&&out<.3f)f.light(p.getLerpedPos(delta).add(0,GuardFx.CENTER,0),0xe8e0ff,5,1.5f*FxLib.kick(out,.01f,.08f));
            float a=!Float.isNaN(in)?Ease.smooth(in,0,.05f):!Float.isNaN(out)?1-Ease.smooth(out,0,.15f):0;
            if(a<=0)continue;
            float rise=Float.isNaN(in)?0:1-Ease.smooth(in,.05f,.3f);
            f.light(p.getLerpedPos(delta).add(0,GuardFx.CENTER,0),0xddd4ff,3.6+1.5*rise,.5f*a+1.2f*rise);
        }
        long now=mc.world.getTime();
        for(var entry:StandClient.STATES.entrySet()){
            if(entry.getValue().until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
            if(p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            OverdriveFx.frame(mc,p,delta,camera,f);
        }
    }
    /** Refraction on the effect layer: the guard bubble bursting as it drops, the overdrive's activation shock. */
    public static void distort(MinecraftClient mc,FxDistort d,float delta,Vec3d camera){
        for(var p:mc.world.getPlayers()){
            if(!StandClient.visible(p)||p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            float out=KagenoClips.releaseSec(p.getUuid(),delta);if(Float.isNaN(out)||out>=.2f)continue;
            float u=Ease.unit(out,0,.2f);Vec3d o=p.getLerpedPos(delta).add(0,GuardFx.CENTER,0);
            d.ring(o,new Vec3d(1,0,0),new Vec3d(0,0,1),GuardFx.RADIUS*(1+.9*u),.5,.6f*(1-u));
        }
        long now=mc.world.getTime();
        for(var entry:StandClient.STATES.entrySet()){
            if(entry.getValue().until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
            if(p.getLerpedPos(delta).squaredDistanceTo(camera)>64*64)continue;
            OverdriveFx.distort(mc,p,delta,d);
        }
    }
}
